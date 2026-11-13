package com.tools.app.applock

import android.content.Intent
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import androidx.activity.OnBackPressedCallback
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.ui.platform.ComposeView
import androidx.core.view.WindowCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.tools.app.ui.theme.OneUiTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Lock screen over a locked app: the tinted lock card (identical to the instant cover).
 * Once Android gives this window focus, the cover is removed and unlocking starts.
 *
 * Fingerprint always goes through Samsung's sheet (One UI forces it for any app fingerprint check
 * on under-display sensors). With a Tools PIN set, the sheet offers "Use Tools PIN", which opens
 * Tools' own PIN pad on the card, without the dim; lockouts land there too.
 */
class LockActivity : FragmentActivity() {
    private var target = ""
    private var label = ""
    private var prompting = false
    private var handedOff = false
    private var hasPin = false
    private var unlocking = false
    private var retryOnFocus = false
    /** Opened from a locked shortcut: unlock first, then launch the app. */
    private var launchAfter = false
    /** Going Home after a cancel: the cover stays a moment while Android animates the app away. */
    private var leaving = false
    private var prompt: BiometricPrompt? = null
    private val fallback = Runnable { handOff() }
    private val ui = CustomLockState()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        load(intent)
        if (isFinishing) return
        buildContent()
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = goHome()
        })
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Brought back over the app that covered it: keep the current state (e.g. a PIN in progress).
        if (intent.getBooleanExtra(EXTRA_REFRONT, false) && intent.getStringExtra(EXTRA_PACKAGE) == target) {
            if (hasWindowFocus()) AppLockService.instance?.hideShield()
            return
        }
        load(intent)
        if (isFinishing) return
        buildContent()
        handedOff = false
    }

    private fun buildContent() {
        hasPin = PinStore.isSet(this)
        val card = LockCard.build(this, target)
        if (!hasPin) { setContentView(card); return }
        ui.pinLength = PinStore.length(this)
        ui.fingerprintAvailable = fingerprintReady()
        ui.pinMode = !ui.fingerprintAvailable
        ui.message = "Enter your Tools PIN"
        val compose = ComposeView(this).apply {
            setContent {
                OneUiTheme {
                    CustomLockUi(
                        state = ui,
                        onDigit = ::onDigit,
                        onDelete = { if (!ui.busy) ui.entered = ui.entered.dropLast(1) },
                        onUseFingerprint = { ui.entered = ""; ui.error = false; ui.pinMode = false; prompt() },
                    )
                }
            }
        }
        setContentView(FrameLayout(this).apply {
            addView(card, FrameLayout.LayoutParams(-1, -1))
            addView(compose, FrameLayout.LayoutParams(-1, -1))
        })
    }

    private fun fingerprintReady() =
        BiometricManager.from(this).canAuthenticate(BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS

    override fun onResume() {
        super.onResume()
        AppLock.lockOnTop = true
        current = java.lang.ref.WeakReference(this)
        if (!handedOff) { window.decorView.postDelayed(fallback, 1500); return } // safety net if focus never arrives
        if (!hasPin || !ui.pinMode) prompt()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && retryOnFocus) { retryOnFocus = false; if (!hasPin || !ui.pinMode) prompt() }
        if (hasFocus && !handedOff) handOff()
        else if (hasFocus && !prompting && !unlocking && !leaving) AppLockService.instance?.hideShield() // back on top after the app covered us
    }

    /** Our card is really on top: drop the identical cover, then start unlocking. */
    private fun handOff() {
        if (handedOff) return
        handedOff = true
        window.decorView.removeCallbacks(fallback)
        // Focus means our identical card is on screen. The fingerprint sheet keeps the cover over it;
        // the PIN pad (in this window) needs it gone.
        ui.revealed = true
        if (!hasPin || !ui.pinMode) prompt() else AppLockService.instance?.hideShield()
    }

    private fun load(intent: Intent) {
        target = intent.getStringExtra(EXTRA_PACKAGE).orEmpty()
        launchAfter = intent.getBooleanExtra(EXTRA_LAUNCH, false)
        // Shortcut for an app that isn't locked (any more) or is still unlocked: just open it.
        if (launchAfter && !AppLock.needsLock(target)) {
            LockedShortcut.launch(this, target)
            finish()
            @Suppress("DEPRECATION") overridePendingTransition(0, 0)
            return
        }
        label = runCatching {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(target, 0)).toString()
        }.getOrDefault("app")
    }

    // --- Samsung's fingerprint sheet

    private fun prompt() {
        if (prompting || unlocking || target.isEmpty() || isFinishing) return
        if (!fingerprintReady()) { // no fingerprint enrolled
            if (hasPin) showPin("Enter your Tools PIN", error = false) else credentialPrompt()
            return
        }
        prompting = true
        // Only Samsung's fingerprint icon shows: the cover stays over its card.
        AppLockService.instance?.coverPrompt(target)
        val info = BiometricPrompt.PromptInfo.Builder().setTitle("Unlock $label")
            .setAllowedAuthenticators(BIOMETRIC_STRONG).setNegativeButtonText("Use PIN")
        prompt = BiometricPrompt(this, mainExecutor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                prompting = false
                unlock()
            }

            override fun onAuthenticationFailed() {
                AppLockService.instance?.promptFeedback("Fingerprint not recognised")
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                prompting = false
                // Samsung cancels a sheet requested while One UI is still animating the app open
                // (we don't have focus yet). That isn't the user backing out: ask again on focus.
                if (errorCode == BiometricPrompt.ERROR_CANCELED && !hasWindowFocus()) { retryOnFocus = true; return }
                when (errorCode) {
                    // "Use PIN": Tools' own pad, or the phone's PIN screen.
                    BiometricPrompt.ERROR_NEGATIVE_BUTTON ->
                        if (hasPin) showPin("Enter your Tools PIN", error = false) else credentialPrompt()
                    BiometricPrompt.ERROR_LOCKOUT, BiometricPrompt.ERROR_LOCKOUT_PERMANENT ->
                        if (hasPin) showPin("Too many attempts. Enter your Tools PIN", error = true) else credentialPrompt()
                    else -> goHome() // Back / swipe away
                }
            }
        }).also { it.authenticate(info.build()) }
    }

    /** No Tools PIN: Samsung's own PIN screen for your phone PIN (the cover must not hide it). */
    private fun credentialPrompt() {
        if (prompting || unlocking || isFinishing) return
        prompting = true
        AppLockService.instance?.hideShield()
        val info = BiometricPrompt.PromptInfo.Builder().setTitle("Unlock $label").setAllowedAuthenticators(DEVICE_CREDENTIAL)
        prompt = BiometricPrompt(this, mainExecutor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                prompting = false
                unlock()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                prompting = false
                goHome()
            }
        }).also { it.authenticate(info.build()) }
    }

    private fun showPin(message: String, error: Boolean) {
        // The PIN pad lives in this window; by now it is long on screen, so the cover can go.
        AppLockService.instance?.hideShield()
        ui.message = message
        ui.error = error
        ui.entered = ""
        ui.pinMode = true
    }

    // --- Tools PIN pad

    private fun onDigit(d: Char) {
        if (ui.busy || ui.entered.length >= PinStore.MAX) return
        val wait = PinStore.blockedForMs()
        if (wait > 0) { ui.error = true; ui.message = "Try again in ${(wait + 999) / 1000} s"; ui.shake++; return }
        ui.error = false
        ui.entered += d
        if (ui.entered.length < ui.pinLength) return
        val attempt = ui.entered
        ui.busy = true
        lifecycleScope.launch {
            val ok = withContext(Dispatchers.Default) { PinStore.verify(this@LockActivity, attempt) }
            PinStore.onResult(ok)
            if (ok) { unlock(); return@launch }
            ui.busy = false
            ui.error = true
            ui.shake++
            window.decorView.performHapticFeedback(HapticFeedbackConstants.REJECT)
            delay(120)
            ui.entered = ""
            val wait = PinStore.blockedForMs()
            ui.message = if (wait > 0) "Too many attempts. Try again in ${(wait + 999) / 1000} s" else "Wrong PIN. Try again"
        }
    }

    // --- Shared

    private fun unlock() {
        if (unlocking) return
        unlocking = true
        AppLock.gating = null
        AppLock.markUnlocked(target)
        window.decorView.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        if (launchAfter) {
            // Straight into the app with its own opening animation.
            LockedShortcut.launch(this, target)
            finish()
            return
        }
        AppLockService.instance?.hideShield(fade = true)
        window.decorView.animate().alpha(0f).setDuration(LockCard.FADE_OUT_MS)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction {
                finish()
                @Suppress("DEPRECATION") overridePendingTransition(0, 0)
            }.start()
    }

    private fun goHome() {
        if (isFinishing) return
        if (launchAfter) { // came from the Home screen; just close
            prompt?.cancelAuthentication()
            finish()
            return
        }
        prompt?.cancelAuthentication()
        leaving = true
        AppLock.gating = null
        AppLock.onCancelled(target)
        AppLockService.instance?.coverWhileGoingHome(target)
        startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
        @Suppress("DEPRECATION") overridePendingTransition(0, 0)
    }

    override fun onPause() {
        super.onPause()
        AppLock.lockOnTop = false
    }

    /** Closes this lock without going Home (the user already left for another app). */
    private fun dismissNow() {
        if (isFinishing) return
        prompt?.cancelAuthentication()
        AppLockService.instance?.hideShield()
        finish()
        @Suppress("DEPRECATION") overridePendingTransition(0, 0)
    }

    override fun onDestroy() {
        // Closed any other way while the sheet was up: don't leave the cover behind.
        if (!unlocking && !leaving) AppLockService.instance?.hideShield(fade = true)
        if (AppLock.gating == target && !unlocking) AppLock.gating = null
        super.onDestroy()
    }

    companion object {
        const val EXTRA_PACKAGE = "package"
        const val EXTRA_REFRONT = "refront"
        const val EXTRA_LAUNCH = "launch"
        private var current: java.lang.ref.WeakReference<LockActivity>? = null

        fun dismissQuietly() { current?.get()?.dismissNow() }
    }
}
