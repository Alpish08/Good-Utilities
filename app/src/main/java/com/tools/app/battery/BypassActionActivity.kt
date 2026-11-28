package com.tools.app.battery

import com.tools.app.R
import com.tools.app.ui.t
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import com.tools.app.MainActivity

/**
 * Headless entry points for Modes and Routines, shortcuts and other automation apps:
 *   com.tools.app/.battery.BypassOn      hold the battery at its current level
 *   com.tools.app/.battery.BypassOff     restore Samsung's previous Battery protection
 *   com.tools.app/.battery.BypassToggle
 */
class BypassActionActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val started = android.os.SystemClock.elapsedRealtime()
        val name = intent.component?.className.orEmpty()
        val action = intent.action.orEmpty()
        val mode = when {
            name.endsWith("BypassOn") || action.endsWith("BYPASS_ON") -> 1
            name.endsWith("BypassOff") || action.endsWith("BYPASS_OFF") -> 0
            else -> if (ChargeLimit.activeLimit(this) != null) 0 else 1
        }
        if (!ChargeLimit.canWrite(this)) {
            startActivity(Intent(this, MainActivity::class.java).setAction(MainActivity.ACTION_CHARGE_LIMIT).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } else if (mode == 1) {
            val level = BypassTileService.currentLevel(this).coerceIn(ChargeLimit.MIN_LIMIT, 100)
            ChargeLimit.apply(this, level)
            toastOn(this, level)
        } else {
            ChargeLimit.restore(this)
            toastOff(this)
        }
        // Routines launches this in a way that drops whatever app was open and shows the Home screen (with "return to
        // Home", or through a shortcut). App lock's service knows which app that was and reopens it if Home took over.
        sendBroadcast(
            Intent(com.tools.app.applock.AppLockService.ACTION_RETURN).setPackage(packageName)
                .putExtra(com.tools.app.applock.AppLockService.EXTRA_AT, started),
        )
        finish()
    }

    companion object {
        fun toastOn(context: Context, level: Int) =
            Toast.makeText(context, t(R.string.charge_bypass_charging_on_holding_at, level), Toast.LENGTH_SHORT).show()

        fun toastOff(context: Context) =
            Toast.makeText(context, t(R.string.charge_bypass_charging_off), Toast.LENGTH_SHORT).show()
    }
}
