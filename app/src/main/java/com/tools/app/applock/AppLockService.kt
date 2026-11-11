package com.tools.app.applock

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Runs in its own small ":lock" process. Watches which app comes to the foreground
 * (window-state events only; it never reads screen content) and gates locked apps.
 */
class AppLockService : AccessibilityService() {
    private var keyboards: Set<String> = emptySet()
    /** The app in the foreground as far as we know (the last non-overlay window that opened). */
    private var lastApp: String? = null
    private val scope = MainScope()

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> AppLock.onScreenOff()
                // Unlocking the phone shows the app you were in again without any "app opened"
                // event, so check it here: the screen-off above already ended its unlock.
                Intent.ACTION_USER_PRESENT -> lastApp?.let { pkg ->
                    if (AppLock.gating == null && AppLock.needsLock(pkg)) gate(pkg)
                }
                AppLock.ACTION_RELOAD -> AppLock.reload()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    override fun onServiceConnected() {
        ContextCompat.registerReceiver(
            this, receiver,
            IntentFilter(Intent.ACTION_SCREEN_OFF).apply { addAction(Intent.ACTION_USER_PRESENT) },
            ContextCompat.RECEIVER_EXPORTED,
        )
        ContextCompat.registerReceiver(this, receiver, IntentFilter(AppLock.ACTION_RELOAD), ContextCompat.RECEIVER_NOT_EXPORTED)
        keyboards = getSystemService(InputMethodManager::class.java).inputMethodList.map { it.packageName }.toSet()
        // Battery: with nothing to lock, ask Android not to deliver any events at all.
        scope.launch {
            AppLock.config.map { it.enabled && it.locked.isNotEmpty() }.distinctUntilChanged().collect { needed ->
                serviceInfo = serviceInfo.apply {
                    eventTypes = if (needed) AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED else 0
                }
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        // Overlays that sit on top of an app shouldn't count as leaving it.
        if (pkg == packageName || pkg in IGNORED || pkg in keyboards) return
        lastApp = pkg
        val gating = AppLock.gating
        if (gating != null) {
            if (pkg == gating) {
                // The locked app opened another window (e.g. Ring's splash screen starting its
                // main screen) and it landed on top of the lock. Cover it at once and put the
                // lock screen back in front.
                if (!AppLock.lockOnTop) {
                    showShield(pkg, fadeIn = false)
                    startLock(pkg, refront = true)
                }
                return
            }
            // Another app came up after the lock opened: you left the lock screen (home swipe,
            // Recents…). That counts as backing out, so the lock can't go stale.
            if (!AppLock.gateStarting()) {
                AppLock.gating = null
                LockActivity.dismissQuietly()
            }
        }
        AppLock.onForeground(pkg)
        if (!AppLock.needsLock(pkg)) return
        if (AppLock.recentlyCancelled(pkg)) { // the app reopened itself right after you backed out
            coverWhileGoingHome(pkg)
            startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        }
        gate(pkg)
    }

    /**
     * The instant cover goes up the moment the app's window appears; LockActivity (same card)
     * takes over once Android gives it focus, then shows Samsung's biometric prompt.
     */
    private fun gate(pkg: String) {
        AppLock.gating = pkg
        showShield(pkg, fadeIn = true)
        startLock(pkg, refront = false)
    }

    private fun startLock(pkg: String, refront: Boolean) {
        startActivity(
            Intent(this, LockActivity::class.java)
                .putExtra(LockActivity.EXTRA_PACKAGE, pkg)
                .putExtra(LockActivity.EXTRA_REFRONT, refront)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
        )
    }

    // --- Instant cover: an accessibility overlay appears in milliseconds (an activity takes
    // hundreds). It can't stay up during the prompt (it would sit above Samsung's sheet), so
    // LockActivity removes it once it has focus. Not touchable and auto-removed after 4 s,
    // so it can never trap you.
    private var shield: View? = null
    private val main = Handler(Looper.getMainLooper())
    private val shieldTimeout = Runnable { hideShield(fade = true) }

    fun showShield(pkg: String, fadeIn: Boolean) {
        main.removeCallbacks(shieldTimeout)
        main.postDelayed(shieldTimeout, 4000)
        shield?.let { it.animate().cancel(); it.alpha = 1f; return }
        val v = LockCard.build(this, pkg)
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            if (Build.VERSION.SDK_INT >= 30) fitInsetsTypes = 0
            windowAnimations = 0
        }
        if (fadeIn) v.alpha = 0.8f
        runCatching { getSystemService(WindowManager::class.java).addView(v, lp); shield = v }.onFailure { return }
        if (fadeIn) v.animate().alpha(1f).setDuration(LockCard.FADE_IN_MS).setInterpolator(DecelerateInterpolator()).start()
    }

    fun hideShield(fade: Boolean = false) {
        main.removeCallbacks(shieldTimeout)
        val v = shield ?: return
        shield = null
        val remove = { runCatching { getSystemService(WindowManager::class.java).removeViewImmediate(v) }; Unit }
        if (fade) v.animate().alpha(0f).setDuration(LockCard.FADE_OUT_MS).setInterpolator(DecelerateInterpolator())
            .withEndAction { remove() }.start()
        else remove()
    }

    /** Cancelled: keep the app covered while Android animates it closing, then fade to Home. */
    fun coverWhileGoingHome(pkg: String) {
        showShield(pkg, fadeIn = false)
        main.postDelayed({ hideShield(fade = true) }, 450)
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        hideShield()
        if (instance === this) instance = null
        runCatching { unregisterReceiver(receiver) }
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        /** Running instance (same ":lock" process as LockActivity). */
        @Volatile var instance: AppLockService? = null

        private val IGNORED = setOf(
            "android",
            "com.android.systemui",
            "com.samsung.android.app.cocktailbarservice", // Edge panels
            "com.samsung.android.biometrics.app.setting", // biometric prompt UI
            "com.android.settings.intelligence",
        )
    }
}
