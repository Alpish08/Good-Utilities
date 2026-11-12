package com.tools.app.battery

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt

/** One reading of the battery, all from public Android APIs (no root, no extra permission). */
data class BatterySnapshot(
    val level: Int,
    val charging: Boolean,
    val plugged: String?,
    val temperatureC: Float,
    val voltageV: Float,
    val currentMa: Int?,       // + charging, − draining
    val cycles: Int?,
    val health: String,
    val technology: String,
    val estimatedMah: Int?,    // current full capacity, estimated
    val designMah: Int,       // typical capacity when new
    val ratedMah: Int,        // minimum (rated) capacity from the power profile
    val samsung: SamsungBattery? = null, // Samsung's own figures, when Good Utilities may read them
) {
    /** The fuel-gauge estimate (charge stored ÷ level); runs a couple of points high. */
    val estimatedPercent: Int? get() = estimatedMah?.let { (it * 100f / designMah).toInt().coerceIn(1, 100) }
    /** Samsung's battery health (ASOC) when available, otherwise the estimate. */
    val healthPercent: Int? get() = samsung?.asoc ?: estimatedPercent
    /** True when [healthPercent] is Samsung's own measurement rather than an estimate. */
    val healthIsSamsung: Boolean get() = samsung?.asoc != null
    val watts: Float? get() = currentMa?.let { abs(it) / 1000f * voltageV }
    /** Samsung's lifetime cycle count when available, else Android's (Samsung reports 0 there). */
    val totalCycles: Int? get() = samsung?.cycles ?: cycles
}

/**
 * Samsung's battery service figures, from `dumpsys battery`:
 * mSavedBatteryAsoc (health: learned full capacity as % of design), mSavedBatteryUsage (lifetime usage
 * in hundredths of a cycle), battery FirstUseDate and LLB MAN (manufacture date).
 */
data class SamsungBattery(val asoc: Int?, val cycles: Int?, val firstUse: java.time.LocalDate?, val made: java.time.LocalDate?)

object BatteryHealth {
    /**
     * Estimates today's full capacity from the fuel gauge: charge counter (µAh in the battery now)
     * divided by the level. It is most accurate above ~50%, so the history only keeps those readings.
     */
    fun read(context: Context): BatterySnapshot {
        val bm = context.getSystemService(BatteryManager::class.java)
        val i: Intent? = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = i?.let { it.getIntExtra(BatteryManager.EXTRA_LEVEL, 0) * 100 / it.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1) } ?: 0
        val status = i?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val plug = i?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        val counterUah = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
        // Samsung reports current_now in mA, most other phones in µA.
        val rawCurrent = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        val current = when {
            rawCurrent == Int.MIN_VALUE -> null
            abs(rawCurrent) > 20_000 -> rawCurrent / 1000
            else -> rawCurrent
        }
        // Samsung reports 0 here and keeps the real count private, so 0 means "not reported".
        val cycles = i?.getIntExtra("android.os.extra.CYCLE_COUNT", -1)?.takeIf { it > 0 }
        return BatterySnapshot(
            level = level,
            charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL,
            plugged = when (plug) {
                BatteryManager.BATTERY_PLUGGED_AC -> "Charger"
                BatteryManager.BATTERY_PLUGGED_USB -> "USB"
                BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless"
                BatteryManager.BATTERY_PLUGGED_DOCK -> "Dock"
                else -> null
            },
            temperatureC = (i?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10f,
            voltageV = (i?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0) / 1000f,
            currentMa = current,
            cycles = cycles,
            health = when (i?.getIntExtra(BatteryManager.EXTRA_HEALTH, 0)) {
                BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
                BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheating"
                BatteryManager.BATTERY_HEALTH_COLD -> "Too cold"
                BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"
                BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over voltage"
                else -> "Unknown"
            },
            technology = i?.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY).orEmpty().ifBlank { "Li-ion" },
            estimatedMah = if (counterUah > 0 && level >= 20) (counterUah / 1000f / (level / 100f)).roundToInt() else null,
            designMah = typicalCapacity(context),
            ratedMah = designCapacity(context),
        )
    }

    /**
     * Typical capacity when new. Phones list a lower *rated* (minimum) figure in their power profile
     * (4,855 mAh on an S24 Ultra, sold as 5,000 mAh), so health is measured against the typical one.
     */
    fun typicalCapacity(context: Context): Int {
        val rated = designCapacity(context)
        return when {
            android.os.Build.MODEL.startsWith("SM-S928") -> 5000 // Galaxy S24 Ultra
            else -> (rated / 0.97f / 50f).roundToInt() * 50     // typical ≈ rated / 0.97
        }
    }

    /** The battery's rated (minimum) capacity from the system power profile. */
    fun designCapacity(context: Context): Int = runCatching {
        val cls = Class.forName("com.android.internal.os.PowerProfile")
        val profile = cls.getConstructor(Context::class.java).newInstance(context)
        (cls.getMethod("getBatteryCapacity").invoke(profile) as Double).roundToInt()
    }.getOrNull()?.takeIf { it in 1000..20000 } ?: 5000

    // --- Samsung's own figures

    const val DUMP = "android.permission.DUMP"

    /**
     * Reading Samsung's battery report needs the DUMP permission, which only adb can grant
     * (`adb shell pm grant com.tools.app android.permission.DUMP`); enable-applock.sh does it.
     */
    fun canReadSamsung(context: Context) = context.checkSelfPermission(DUMP) == android.content.pm.PackageManager.PERMISSION_GRANTED

    @Volatile private var cached: Pair<Long, SamsungBattery?>? = null

    /** Blocking (runs dumpsys); call off the main thread. Cached for a few minutes, the values change slowly. */
    fun samsung(context: Context): SamsungBattery? {
        if (!canReadSamsung(context)) return null
        cached?.let { (t, v) -> if (System.currentTimeMillis() - t < 5 * 60_000L) return v }
        val v = runCatching {
            val p = ProcessBuilder("dumpsys", "battery").redirectErrorStream(true).start()
            val text = p.inputStream.bufferedReader().use { it.readText() }
            p.waitFor()
            parseSamsung(text)
        }.getOrNull()
        cached = System.currentTimeMillis() to v
        return v
    }

    fun parseSamsung(text: String): SamsungBattery? {
        fun field(name: String) = Regex("""$name:\s*\[?\s*([A-Za-z0-9]+)""").find(text)?.groupValues?.get(1)
        val asoc = field("mSavedBatteryAsoc")?.toIntOrNull()?.takeIf { it in 1..120 }?.coerceAtMost(100)
        val usage = field("mSavedBatteryUsage")?.toIntOrNull()?.takeIf { it >= 0 }
        fun date(s: String?) = s?.takeIf { it.length == 8 && it.all(Char::isDigit) }?.let {
            runCatching { java.time.LocalDate.parse(it, java.time.format.DateTimeFormatter.BASIC_ISO_DATE) }.getOrNull()
        }
        val firstUse = date(field("battery FirstUseDate"))
        val made = date(field("LLB MAN"))
        if (asoc == null && usage == null) return null
        return SamsungBattery(asoc, usage?.let { it / 100 }, firstUse, made)
    }
}
