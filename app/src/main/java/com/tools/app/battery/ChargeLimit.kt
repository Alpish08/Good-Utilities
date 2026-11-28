package com.tools.app.battery

import com.tools.app.R
import com.tools.app.ui.t
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings

/**
 * Drives Samsung's own "Battery protection › Maximum" with an arbitrary stop level.
 * The values live in Settings.Global, which needs WRITE_SECURE_SETTINGS (granted once over adb).
 *
 * Keys observed on One UI 8.5:
 *   protect_battery                     mode (4 = Basic; Maximum assumed to be 1)
 *   battery_protection_threshold        stop charging at %
 *   battery_protection_recharge_level   resume charging below %
 */
object ChargeLimit {
    private const val KEY_MODE = "protect_battery"
    private const val KEY_THRESHOLD = "battery_protection_threshold"
    private const val KEY_RECHARGE = "battery_protection_recharge_level"
    const val MODE_MAXIMUM = 1
    const val MIN_LIMIT = 20

    const val GRANT_COMMAND = "adb shell pm grant com.tools.app android.permission.WRITE_SECURE_SETTINGS"

    data class SamsungState(val mode: Int, val threshold: Int, val recharge: Int) {
        val modeName get() = when (mode) {
            0 -> t(R.string.ui_off); 1 -> t(R.string.charge_maximum); 2 -> t(R.string.charge_adaptive); 4 -> t(R.string.charge_basic); else -> t(R.string.charge_mode, mode)
        }
    }

    fun canWrite(context: Context) =
        context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED

    fun read(context: Context): SamsungState {
        val cr = context.contentResolver
        return SamsungState(
            Settings.Global.getInt(cr, KEY_MODE, -1),
            Settings.Global.getInt(cr, KEY_THRESHOLD, -1),
            Settings.Global.getInt(cr, KEY_RECHARGE, -1),
        )
    }

    private fun prefs(context: Context) = context.getSharedPreferences("chargelimit", Context.MODE_PRIVATE)

    /** The custom limit currently applied by Tools, or null when Samsung's own setting is in charge. */
    fun activeLimit(context: Context): Int? = prefs(context).getInt("limit", -1).takeIf { it > 0 }
    fun lastLimit(context: Context): Int = prefs(context).getInt("lastLimit", 80)

    fun apply(context: Context, limit: Int) {
        val p = prefs(context)
        if (!p.contains("backupMode")) {
            val s = read(context)
            p.edit().putInt("backupMode", s.mode).putInt("backupThreshold", s.threshold).putInt("backupRecharge", s.recharge).apply()
        }
        val cr = context.contentResolver
        Settings.Global.putInt(cr, KEY_THRESHOLD, limit)
        Settings.Global.putInt(cr, KEY_RECHARGE, limit)
        Settings.Global.putInt(cr, KEY_MODE, MODE_MAXIMUM)
        p.edit().putInt("limit", limit).putInt("lastLimit", limit).apply()
        BypassTileService.refresh(context)
    }

    /** Puts Samsung's battery protection back exactly how it was before Tools changed it (unless the user has since changed it in Settings). */
    fun restore(context: Context) {
        val p = prefs(context)
        val cr = context.contentResolver
        val applied = p.getInt("limit", -1)
        val now = read(context)
        // Roll back each value that is still the one Tools wrote; anything changed since (in Samsung's Battery
        // settings, or by a routine switching the mode) is your choice and stays. Checking them one by one matters:
        // a mode change alone leaves the stop / resume levels at the bypass level (e.g. Basic resuming at 46%).
        if (p.contains("backupMode") && applied > 0) {
            if (now.threshold == applied) p.getInt("backupThreshold", -1).takeIf { it >= 0 }?.let { Settings.Global.putInt(cr, KEY_THRESHOLD, it) }
            if (now.recharge == applied) p.getInt("backupRecharge", -1).takeIf { it >= 0 }?.let { Settings.Global.putInt(cr, KEY_RECHARGE, it) }
            if (now.mode == MODE_MAXIMUM) p.getInt("backupMode", -1).takeIf { it >= 0 }?.let { Settings.Global.putInt(cr, KEY_MODE, it) }
        }
        p.edit().remove("backupMode").remove("backupThreshold").remove("backupRecharge").remove("limit").apply()
        BypassTileService.refresh(context)
    }
}
