package com.tools.app.applock

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.AdaptiveIconDrawable
import android.widget.Toast
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.graphics.drawable.toBitmap

/**
 * Locked shortcut: a Home screen icon for a locked app that opens Good Utilities' lock first and
 * only launches the app after you unlock. Android gives no way to step in before a normal app
 * icon launches its app, so this is how the lock can come first. The accessibility lock still
 * covers every other way in (Recents, notifications, the app drawer).
 */
object LockedShortcut {
    const val ACTION = "com.tools.app.LAUNCH_LOCKED"

    fun pin(context: Context, pkg: String, label: String) {
        if (!ShortcutManagerCompat.isRequestPinShortcutSupported(context)) {
            Toast.makeText(context, "Your launcher doesn't support shortcuts", Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent(context, LockActivity::class.java)
            .setAction(ACTION)
            .putExtra(LockActivity.EXTRA_PACKAGE, pkg)
            .putExtra(LockActivity.EXTRA_LAUNCH, true)
        val info = ShortcutInfoCompat.Builder(context, "locked-$pkg")
            .setShortLabel(label)
            .setIcon(icon(context, pkg))
            .setIntent(intent)
            .build()
        ShortcutManagerCompat.requestPinShortcut(context, info, null)
    }

    /** The app's own icon, kept adaptive so the launcher shapes it like every other icon. */
    private fun icon(context: Context, pkg: String): IconCompat {
        val d = context.packageManager.getApplicationIcon(pkg)
        if (d is AdaptiveIconDrawable) {
            val size = 432
            val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            for (layer in listOfNotNull(d.background, d.foreground)) {
                layer.setBounds(0, 0, size, size)
                layer.draw(canvas)
            }
            return IconCompat.createWithAdaptiveBitmap(bmp)
        }
        return IconCompat.createWithBitmap(d.toBitmap(192, 192))
    }

    /** Opens [pkg] the normal way (its launcher activity, as if its own icon was tapped). */
    fun launch(context: Context, pkg: String): Boolean {
        val i = context.packageManager.getLaunchIntentForPackage(pkg) ?: return false
        context.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED))
        return true
    }
}
