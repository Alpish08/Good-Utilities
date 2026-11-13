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
 * Runs in its own small ":lock" process. Watches which app comes to the foreground (window-state
 * events) and gates locked apps. It also gets taps, but only looks at taps on the Home screen, and
 * only at the tapped icon's name, so a locked app's cover can start fading in at the tap.
 */
class AppLockService : AccessibilityService() {
    private var keyboards: Set<String> = emptySet()
    /** The app in the foreground as far as we know (the last non-overlay window that opened). */
    private var lastApp: String? = null
    /** Home screen apps (launchers); taps there may be on a locked app's icon. */
    private var launchers: Set<String> = emptySet()
    /** Locked apps' names, longest first ("Samsung Notes" before "Notes"). */
    private var lockedLabels: List<Pair<String, String>> = emptyList()
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

    private val captureReceiver by lazy { com.tools.app.japanese.JapaneseCapture.Receiver(this) }

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
        // "Read Japanese" tile: screenshot on request (explicit, non-exported broadcast from this app only).
        ContextCompat.registerReceiver(this, captureReceiver, IntentFilter(com.tools.app.japanese.JapaneseCapture.ACTION_CAPTURE), ContextCompat.RECEIVER_NOT_EXPORTED)
        keyboards = getSystemService(InputMethodManager::class.java).inputMethodList.map { it.packageName }.toSet()
        launchers = packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)
            .map { it.activityInfo.packageName }.toSet() - packageName
        scope.launch {
            AppLock.config.map { it.locked }.distinctUntilChanged().collect { locked ->
                lockedLabels = locked.mapNotNull { pkg ->
                    runCatching { packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString() to pkg }.getOrNull()
                }.sortedByDescending { it.first.length }
            }
        }
        // Battery: with nothing to lock, ask Android not to deliver any events at all.
        scope.launch {
            AppLock.config.map { it.enabled && it.locked.isNotEmpty() }.distinctUntilChanged().collect { needed ->
                serviceInfo = serviceInfo.apply {
                    eventTypes = if (needed) AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or AccessibilityEvent.TYPE_VIEW_CLICKED else 0
                }
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) { onTap(event); return }
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        // Overlays that sit on top of an app shouldn't count as leaving it.
        if (pkg == packageName || pkg in IGNORED || pkg in keyboards) return
        lastApp = pkg
        tapped?.let { t ->
            // The tapped app is opening (its cover is already fading in), or something else came up instead.
            if (pkg == t || pkg !in launchers) { tapped = null; main.removeCallbacks(tapTimeout) }
            if (pkg != t && pkg !in launchers && AppLock.gating == null) hideShield(fade = true)
        }
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

    /** App whose Home screen icon was just tapped and whose cover is fading in, until its window shows up. */
    private var tapped: String? = null
    private val tapTimeout = Runnable { // the tap didn't open it after all
        tapped = null
        if (AppLock.gating == null) hideShield(fade = true)
    }

    /**
     * A tap on the Home screen: if it was a locked app's icon, start fading its cover in right away,
     * over the launcher's opening animation, instead of waiting for the app's window to appear.
     */
    private fun onTap(event: AccessibilityEvent) {
        if (event.packageName?.toString() !in launchers || AppLock.gating != null || tapped != null) return
        // Icons are named like "WhatsApp" or "WhatsApp, 23 notifications".
        val name = (event.contentDescription ?: event.text.firstOrNull())?.toString()?.trim() ?: return
        val pkg = lockedLabels.firstOrNull { (label, _) -> name == label || name.startsWith("$label,") }?.second ?: return
        if (!AppLock.wouldLock(pkg)) return
        tapped = pkg
        showShield(pkg, fadeIn = true, fromTap = true)
        main.removeCallbacks(tapTimeout)
        main.postDelayed(tapTimeout, 1500)
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
    private var shieldPkg: String? = null
    /** "Not recognised" line on the cover while it hides Samsung's sheet (null when not prompting). */
    private var promptStatus: android.widget.TextView? = null
    private val clearStatus = Runnable { promptStatus?.text = "" }

    /**
     * Keeps the cover up over Samsung's fingerprint sheet. The sheet's card and dim are hidden under
     * it; Samsung draws the fingerprint icon in its own window above ours, so only the icon shows.
     * Not touchable: taps reach the sheet, and "Use PIN" sits right over Samsung's own button.
     */
    fun coverPrompt(pkg: String) {
        showShield(pkg, fadeIn = false)
        // It must outlast the sheet however long it's up; LockActivity removes it when it closes
        // (and an overlay can't outlive this process). It isn't touchable, so it can't trap you.
        promptHeld = true
        main.removeCallbacks(shieldTimeout)
        val root = shield as? android.widget.FrameLayout ?: return
        if (promptStatus == null) promptStatus = LockCard.addPromptHints(root)
    }

    fun promptFeedback(text: String) {
        val v = promptStatus ?: return
        v.text = text
        v.setTextColor(android.graphics.Color.rgb(255, 120, 120))
        main.removeCallbacks(clearStatus)
        main.postDelayed(clearStatus, 1600)
    }
    private val main = Handler(Looper.getMainLooper())
    /** While it hides Samsung's sheet the cover stays until the lock screen ends it (no timeout). */
    private var promptHeld = false
    private val shieldTimeout = Runnable { if (!promptHeld) hideShield(fade = true) }

    fun showShield(pkg: String, fadeIn: Boolean, fromTap: Boolean = false) {
        if (shield != null && shieldPkg != pkg) hideShield()
        main.removeCallbacks(shieldTimeout)
        main.postDelayed(shieldTimeout, 4000)
        // Already up (e.g. fading in since the tap): let its fade finish; an app on top needs it at once.
        shield?.let { if (!fadeIn) { it.animate().cancel(); it.alpha = 1f }; return }
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
        // From a tap it fades in from nothing over the launcher; otherwise the app is already showing, so start nearly opaque.
        if (fadeIn) v.alpha = if (fromTap) 0f else 0.8f
        runCatching { getSystemService(WindowManager::class.java).addView(v, lp); shield = v; shieldPkg = pkg }.onFailure { return }
        if (fadeIn) v.animate().alpha(1f).setDuration(if (fromTap) LockCard.TAP_FADE_IN_MS else LockCard.FADE_IN_MS)
            .setInterpolator(DecelerateInterpolator()).start()
    }

    fun hideShield(fade: Boolean = false) {
        main.removeCallbacks(shieldTimeout)
        val v = shield ?: return
        shield = null
        shieldPkg = null
        promptStatus = null
        promptHeld = false
        main.removeCallbacks(clearStatus)
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
        main.removeCallbacks(tapTimeout)
        hideShield()
        if (instance === this) instance = null
        runCatching { unregisterReceiver(receiver) }
        runCatching { unregisterReceiver(captureReceiver) }
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
