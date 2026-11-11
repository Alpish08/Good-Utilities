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
) {
    val healthPercent: Int? get() = estimatedMah?.let { (it * 100f / designMah).toInt().coerceIn(1, 100) } // rounded down, like Samsung
    val watts: Float? get() = currentMa?.let { abs(it) / 1000f * voltageV }
}

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

    // --- History: one reading per day, kept in the app's own files.

    data class Point(val day: Long, val mah: Int, val cycles: Int?)

    private fun file(context: Context) = File(context.filesDir, "battery_history.csv")

    fun history(context: Context): List<Point> = runCatching {
        file(context).readLines().mapNotNull { l ->
            val p = l.split(',')
            if (p.size < 2) null else Point(p[0].toLong(), p[1].toInt(), p.getOrNull(2)?.toIntOrNull())
        }
    }.getOrDefault(emptyList())

    /** Records today's estimate (once a day, only from a reliable reading). */
    fun record(context: Context, s: BatterySnapshot) {
        val mah = s.estimatedMah ?: return
        if (s.level < 50) return
        val today = System.currentTimeMillis() / 86_400_000L
        val all = history(context)
        if (all.lastOrNull()?.day == today) return
        file(context).appendText("$today,$mah,${s.cycles ?: ""}\n")
    }
}
