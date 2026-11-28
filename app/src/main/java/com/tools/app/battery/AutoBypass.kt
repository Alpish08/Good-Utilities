package com.tools.app.battery

import com.tools.app.R
import com.tools.app.ui.t
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import java.io.File
import java.util.Calendar

/**
 * Auto bypass when hot: while plugged in, turns bypass charging on once the battery reaches [onAt] °C and back off
 * when it has cooled to [offAt] °C (after at least [MIN_HOLD_MS]) or the phone is unplugged. Optionally skipped at
 * night (12 AM – 7:30 AM), when the Night routine's slow charging and 80% cap are in charge.
 *
 * Only undoes what it started: bypass you turned on yourself is left alone, and if you turn off a bypass it started,
 * it waits until the next time you plug in.
 *
 * Battery updates come from App lock's always-running service (only running apps receive them), forwarded to
 * [Receiver] so every change to the charge limit happens in this (the main) process.
 */
object AutoBypass {
    const val ACTION_BATTERY = "com.tools.app.battery.AUTO_BYPASS_BATTERY"
    const val EXTRA_PLUGGED = "plugged"
    const val EXTRA_TEMP = "temp" // tenths of °C

    val ON_CHOICES = listOf(38, 40, 42, 44)
    val OFF_CHOICES = listOf(34, 36, 38, 40)
    const val DEFAULT_ON = 40
    const val DEFAULT_OFF = 36
    private const val MIN_HOLD_MS = 10 * 60_000L
    const val NIGHT_LABEL = "12 AM – 7:30 AM"

    private fun prefs(c: Context) = c.getSharedPreferences("autobypass", Context.MODE_PRIVATE)

    /** Tells App lock's service (another process) whether to forward battery updates at all. */
    fun flagFile(c: Context) = File(c.applicationContext.filesDir, "auto-bypass-on")

    fun enabled(c: Context) = prefs(c).getBoolean("enabled", false)
    fun onAt(c: Context) = prefs(c).getInt("onAt", DEFAULT_ON)
    fun offAt(c: Context) = prefs(c).getInt("offAt", DEFAULT_OFF)
    fun skipNight(c: Context) = prefs(c).getBoolean("skipNight", true)

    fun setEnabled(c: Context, on: Boolean) {
        prefs(c).edit().putBoolean("enabled", on).apply()
        val f = flagFile(c)
        if (on) runCatching { f.writeText("1") } else f.delete()
        if (!on && prefs(c).getBoolean("active", false)) stop(c, toast = true)
    }

    fun setOnAt(c: Context, v: Int) {
        val e = prefs(c).edit().putInt("onAt", v)
        if (offAt(c) >= v) e.putInt("offAt", OFF_CHOICES.last { it < v })
        e.apply()
    }

    fun setOffAt(c: Context, v: Int) { if (v < onAt(c)) prefs(c).edit().putInt("offAt", v).apply() }
    fun setSkipNight(c: Context, v: Boolean) = prefs(c).edit().putBoolean("skipNight", v).apply()

    /** Whether the bypass running now was started by this feature. */
    fun startedIt(c: Context) = prefs(c).getBoolean("active", false) && ChargeLimit.activeLimit(c) != null

    fun isNight(now: Calendar = Calendar.getInstance()): Boolean {
        val m = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        return m < 7 * 60 + 30
    }

    /** One battery update: decides whether to turn bypass on or off. */
    fun onBattery(c: Context, plugged: Boolean, tempTenths: Int) {
        val p = prefs(c)
        val active = p.getBoolean("active", false)
        val bypassOn = ChargeLimit.activeLimit(c) != null
        if (!plugged) {
            p.edit().putBoolean("suppressed", false).apply()
            if (active) stop(c, toast = false)
            return
        }
        if (!enabled(c) || !ChargeLimit.canWrite(c)) return
        if (active && !bypassOn) { // you turned off the bypass it started: leave it until the next plug-in
            p.edit().putBoolean("active", false).putBoolean("suppressed", true).apply()
            return
        }
        val night = skipNight(c) && isNight()
        if (active) {
            val held = System.currentTimeMillis() - p.getLong("since", 0) >= MIN_HOLD_MS
            when {
                night -> stop(c, toast = true)
                held && tempTenths <= offAt(c) * 10 -> stop(c, toast = true, cooledTo = tempTenths)
            }
            return
        }
        if (night || bypassOn || p.getBoolean("suppressed", false)) return
        if (tempTenths >= onAt(c) * 10) {
            val level = BypassTileService.currentLevel(c).coerceIn(ChargeLimit.MIN_LIMIT, 100)
            ChargeLimit.apply(c, level)
            p.edit().putBoolean("active", true).putLong("since", System.currentTimeMillis()).apply()
            Toast.makeText(c, t(R.string.charge_bypass_charging_on_battery_at, deg(tempTenths)), Toast.LENGTH_SHORT).show()
        }
    }

    private fun stop(c: Context, toast: Boolean, cooledTo: Int? = null) {
        prefs(c).edit().putBoolean("active", false).apply()
        if (ChargeLimit.activeLimit(c) != null) ChargeLimit.restore(c)
        if (toast) Toast.makeText(
            c, if (cooledTo != null) t(R.string.charge_bypass_charging_off_battery_cooled_to, deg(cooledTo)) else t(R.string.charge_bypass_charging_off),
            Toast.LENGTH_SHORT,
        ).show()
    }

    private fun deg(t: Int) = "%.1f°C".format(t / 10f)

    /** Battery updates forwarded from App lock's service. */
    class Receiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != ACTION_BATTERY) return
            onBattery(context, intent.getBooleanExtra(EXTRA_PLUGGED, false), intent.getIntExtra(EXTRA_TEMP, 0))
        }
    }
}
