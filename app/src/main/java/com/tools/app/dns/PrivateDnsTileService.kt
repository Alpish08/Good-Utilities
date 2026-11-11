package com.tools.app.dns

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.tools.app.MainActivity
import com.tools.app.R

/** Quick panel: Private DNS Custom (on) / Automatic (off). */
class PrivateDnsTileService : TileService() {
    override fun onStartListening() = render()

    override fun onClick() {
        if (!PrivateDns.canWrite(this) || PrivateDns.host(this).isBlank()) { openApp(); return }
        PrivateDns.setCustom(this, !PrivateDns.isCustom(this))
        render()
    }

    private fun render() {
        val tile = qsTile ?: return
        val custom = PrivateDns.isCustom(this)
        tile.icon = Icon.createWithResource(this, R.drawable.ic_qs_dns)
        tile.label = "DNS"
        tile.state = when {
            !PrivateDns.canWrite(this) -> Tile.STATE_UNAVAILABLE
            custom -> Tile.STATE_ACTIVE
            else -> Tile.STATE_INACTIVE
        }
        tile.subtitle = when {
            !PrivateDns.canWrite(this) -> "Setup needed"
            custom -> PrivateDns.host(this)
            else -> "Automatic"
        }
        tile.updateTile()
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java).setAction(MainActivity.ACTION_PRIVATE_DNS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= 34) startActivityAndCollapse(PendingIntent.getActivity(this, 1, intent, PendingIntent.FLAG_IMMUTABLE))
        else @Suppress("DEPRECATION") startActivityAndCollapse(intent)
    }
}
