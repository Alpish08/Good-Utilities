package com.tools.app.battery

import com.tools.app.R
import com.tools.app.ui.t
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
    /** The raw fuel-gauge estimate (charge stored ÷ level); on Samsung it runs a couple of points high. */
    val rawPercent: Float? get() = estimatedMah?.let { it * 100f / designMah }
    /** The estimate, corrected by the gap seen between it and Samsung's figure at the last sync. */
    val estimatedPercent: Int? get() = rawPercent?.let { (it + (samsung?.offset ?: 0f)).toInt().coerceIn(1, 100) }
    /** Samsung's battery health (ASOC) when available, otherwise the estimate. */
    val healthPercent: Int? get() = samsung?.asoc ?: estimatedPercent
    /** True when [healthPercent] is Samsung's own measurement rather than an estimate. */
    val healthIsSamsung: Boolean get() = samsung?.asoc != null
    val watts: Float? get() = currentMa?.let { abs(it) / 1000f * voltageV }
    /** Samsung's lifetime cycle count when available, else Android's (Samsung reports 0 there). */
    val totalCycles: Int? get() = samsung?.cycles ?: cycles
}

/**
 * Samsung's battery service figures (`dumpsys battery`), which apps can't read themselves: Android blocks the
 * battery service for regular apps. sync-battery.sh reads them over adb and hands them to [SamsungBatterySync].
 * asoc = mSavedBatteryAsoc (health: learned full capacity as % of design), cycles = mSavedBatteryUsage / 100,
 * firstUse = battery FirstUseDate, made = LLB MAN. [offset] = Samsung's health minus the raw estimate at sync time.
 */
data class SamsungBattery(
    val asoc: Int?, val cycles: Int?, val firstUse: java.time.LocalDate?, val made: java.time.LocalDate?,
    val syncedAt: Long, val offset: Float?,
)

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
                BatteryManager.BATTERY_PLUGGED_AC -> t(R.string.battery_charger)
                BatteryManager.BATTERY_PLUGGED_USB -> "USB"
                BatteryManager.BATTERY_PLUGGED_WIRELESS -> t(R.string.battery_wireless)
                BatteryManager.BATTERY_PLUGGED_DOCK -> t(R.string.battery_dock)
                else -> null
            },
            temperatureC = (i?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10f,
            voltageV = (i?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0) / 1000f,
            currentMa = current,
            cycles = cycles,
            health = when (i?.getIntExtra(BatteryManager.EXTRA_HEALTH, 0)) {
                BatteryManager.BATTERY_HEALTH_GOOD -> t(R.string.battery_good)
                BatteryManager.BATTERY_HEALTH_OVERHEAT -> t(R.string.battery_overheating)
                BatteryManager.BATTERY_HEALTH_COLD -> t(R.string.battery_too_cold)
                BatteryManager.BATTERY_HEALTH_DEAD -> t(R.string.battery_dead)
                BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> t(R.string.battery_over_voltage)
                else -> t(R.string.battery_unknown)
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

    // --- Samsung's own figures, synced from a Mac (sync-battery.sh)

    private fun prefs(context: Context) = context.getSharedPreferences("samsung_battery", Context.MODE_PRIVATE)

    /** The last synced figures, or null if never synced. */
    fun samsung(context: Context): SamsungBattery? {
        val p = prefs(context)
        if (!p.contains("syncedAt")) return null
        fun date(k: String) = p.getString(k, null)?.let { runCatching { java.time.LocalDate.parse(it) }.getOrNull() }
        return SamsungBattery(
            asoc = p.getInt("asoc", -1).takeIf { it > 0 },
            cycles = p.getInt("cycles", -1).takeIf { it >= 0 },
            firstUse = date("firstUse"), made = date("made"),
            syncedAt = p.getLong("syncedAt", 0L),
            offset = if (p.contains("offset")) p.getFloat("offset", 0f) else null,
        )
    }

    /** Saves synced figures; the estimate's correction is measured now, against the live fuel gauge. */
    fun saveSamsung(context: Context, asoc: Int, usage: Int?, firstUse: String?, made: String?) {
        fun date(s: String?) = s?.takeIf { it.length == 8 && it.all(Char::isDigit) }?.let {
            runCatching { java.time.LocalDate.parse(it, java.time.format.DateTimeFormatter.BASIC_ISO_DATE) }.getOrNull()
        }
        val raw = read(context).rawPercent
        prefs(context).edit().apply {
            putInt("asoc", asoc.coerceIn(1, 100))
            if (usage != null && usage >= 0) putInt("cycles", usage / 100) else remove("cycles")
            date(firstUse)?.let { putString("firstUse", it.toString()) }
            date(made)?.let { putString("made", it.toString()) }
            putLong("syncedAt", System.currentTimeMillis())
            // Only trust the gap from a reading the fuel gauge is good at (above 50%).
            if (raw != null && read(context).level >= 50) putFloat("offset", (asoc - raw).coerceIn(-10f, 10f))
        }.apply()
    }
}
