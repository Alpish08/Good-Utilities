package com.tools.app.battery

import com.tools.app.ui.t
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.BatteryManager
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.tools.app.MainActivity
import com.tools.app.R

/**
 * Quick panel toggle: holds the battery at its current level so a plugged-in phone runs
 * straight from the charger. Turning it off restores Samsung's previous Battery protection.
 */
class BypassTileService : TileService() {

    override fun onStartListening() = render()

    override fun onClick() {
        if (!ChargeLimit.canWrite(this)) {
            openApp()
            return
        }
        if (ChargeLimit.activeLimit(this) != null) {
            ChargeLimit.restore(this)
            BypassActionActivity.toastOff(this)
        } else {
            val level = currentLevel(this).coerceIn(ChargeLimit.MIN_LIMIT, 100)
            ChargeLimit.apply(this, level)
            BypassActionActivity.toastOn(this, level)
        }
        render()
    }

    private fun render() {
        val tile = qsTile ?: return
        val limit = ChargeLimit.activeLimit(this)
        tile.icon = Icon.createWithResource(this, R.drawable.ic_qs_bypass)
        tile.label = t(R.string.charge_bypass_charging)
        tile.state = when {
            !ChargeLimit.canWrite(this) -> Tile.STATE_UNAVAILABLE
            limit != null -> Tile.STATE_ACTIVE
            else -> Tile.STATE_INACTIVE
        }
        tile.subtitle = null // the label alone fits; state shows as on/off
        tile.updateTile()
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java)
            .setAction(MainActivity.ACTION_CHARGE_LIMIT)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE))
        } else {
            @Suppress("DEPRECATION") startActivityAndCollapse(intent)
        }
    }

    companion object {
        fun currentLevel(context: Context) =
            context.getSystemService(BatteryManager::class.java).getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)

        /** Ask the Quick panel to redraw the tile after the app changed the limit. */
        fun refresh(context: Context) =
            requestListeningState(context, ComponentName(context, BypassTileService::class.java))
    }
}
