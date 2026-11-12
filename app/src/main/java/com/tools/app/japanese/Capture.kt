package com.tools.app.japanese

import android.accessibilityservice.AccessibilityService
import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.view.Display
import android.widget.Toast
import com.tools.app.R
import com.tools.app.applock.AppLockService
import java.io.File
import java.util.concurrent.Executors

/** Screen capture for the "Read Japanese" tile. The shot is taken by App lock's accessibility service (":lock" process). */
object JapaneseCapture {
    /** Explicit, non-exported broadcast from the tile to the service. */
    const val ACTION_CAPTURE = "com.tools.app.japanese.CAPTURE"

    fun shotFile(context: Context) = File(context.cacheDir, "japanese/shot.png").also { it.parentFile?.mkdirs() }

    /** Is App lock's accessibility service switched on in Settings? */
    fun serviceEnabled(context: Context): Boolean {
        val list = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        val cn = ComponentName(context, AppLockService::class.java)
        return list.split(':').any { it == cn.flattenToString() || it == cn.flattenToShortString() }
    }

    private val worker by lazy { Executors.newSingleThreadExecutor() }

    /** Runs inside the accessibility service: close the shade, wait for it to slide away, screenshot, open the reader. */
    fun start(service: AccessibilityService) {
        if (Build.VERSION.SDK_INT < 30) {
            toast(service, "Screen capture needs Android 11 or newer")
            return
        }
        val main = Handler(Looper.getMainLooper())
        service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE)
        main.postDelayed({ shoot(service) }, 350)
    }

    private fun shoot(service: AccessibilityService) {
        if (Build.VERSION.SDK_INT < 30) return
        service.takeScreenshot(Display.DEFAULT_DISPLAY, worker, object : AccessibilityService.TakeScreenshotCallback {
            override fun onSuccess(result: AccessibilityService.ScreenshotResult) {
                val buffer = result.hardwareBuffer
                try {
                    val hw = Bitmap.wrapHardwareBuffer(buffer, result.colorSpace) ?: error("no bitmap")
                    val soft = hw.copy(Bitmap.Config.ARGB_8888, false)
                    hw.recycle()
                    val f = shotFile(service)
                    f.outputStream().buffered().use { soft.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    soft.recycle()
                    service.startActivity(
                        Intent(service, JapaneseReaderActivity::class.java)
                            .putExtra(JapaneseReaderActivity.EXTRA_PATH, f.path)
                            .putExtra(JapaneseReaderActivity.EXTRA_STAMP, System.currentTimeMillis())
                            .putExtra(JapaneseReaderActivity.EXTRA_TOP_INSET, statusBarHeight(service))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    )
                } catch (t: Throwable) {
                    toast(service, "Couldn't capture the screen")
                } finally {
                    buffer.close()
                }
            }

            override fun onFailure(errorCode: Int) {
                toast(
                    service,
                    if (errorCode == AccessibilityService.ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT) "Too fast, try again in a second"
                    else if (errorCode == AccessibilityService.ERROR_TAKE_SCREENSHOT_SECURE_WINDOW) "This screen doesn't allow screenshots"
                    else "Couldn't capture the screen",
                )
            }
        })
    }

    private fun statusBarHeight(context: Context): Int {
        val id = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) context.resources.getDimensionPixelSize(id) else 0
    }

    private fun toast(context: Context, text: String) {
        Handler(Looper.getMainLooper()).post { Toast.makeText(context, text, Toast.LENGTH_SHORT).show() }
    }

    /** Registered by the service. Answers right away (result OK) so the tile knows someone is listening. */
    class Receiver(private val service: AccessibilityService) : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != ACTION_CAPTURE) return
            if (isOrderedBroadcast) resultCode = Activity.RESULT_OK
            start(service)
        }
    }
}

/** Quick Settings tile "Read Japanese". */
class JapaneseTileService : TileService() {
    override fun onStartListening() {
        qsTile?.apply {
            state = Tile.STATE_INACTIVE
            label = getString(R.string.read_japanese)
            subtitle = if (JapaneseCapture.serviceEnabled(this@JapaneseTileService)) null else "Setup needed"
            updateTile()
        }
    }

    override fun onClick() {
        if (isLocked && isSecure) unlockAndRun { go() } else go()
    }

    private fun go() {
        val intent = Intent(JapaneseCapture.ACTION_CAPTURE).setPackage(packageName)
        // Ordered, so the service can answer; no answer means the accessibility service isn't running.
        sendOrderedBroadcast(intent, null, object : BroadcastReceiver() {
            override fun onReceive(context: Context, i: Intent) {
                if (resultCode != Activity.RESULT_OK) openSetupScreen()
            }
        }, null, Activity.RESULT_CANCELED, null, null)
    }

    @android.annotation.SuppressLint("StartActivityAndCollapseDeprecated")
    private fun openSetupScreen() {
        val intent = Intent(this, JapaneseReaderActivity::class.java)
            .putExtra(JapaneseReaderActivity.EXTRA_NO_SERVICE, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        if (Build.VERSION.SDK_INT >= 34) startActivityAndCollapse(PendingIntent.getActivity(this, 7, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        else @Suppress("DEPRECATION") startActivityAndCollapse(intent)
    }
}
