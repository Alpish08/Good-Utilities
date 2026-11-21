package com.tools.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.SeekableTransitionState
import com.tools.app.ui.theme.drawGlow
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.scaleOut
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.tools.app.applock.AppLockScreen
import com.tools.app.battery.ChargeLimitScreen
import com.tools.app.blur.BlurScreen
import com.tools.app.color.ColorPickerScreen
import com.tools.app.home.HomeScreen
import com.tools.app.music.MusicDownloader
import com.tools.app.music.MusicScreen
import com.tools.app.music.MusicSettingsScreen
import com.tools.app.ui.theme.OneUiTheme

enum class Route { Home, Battery, PdfEditor, Converter, Compressor, Random, VideoDownloader, Reader, QrCode, Print, Japanese, GuideAppLock, GuideBattery, About, AppLockPin, PrivateDns, Music, MusicSettings, YouTubeSignIn, ColorPicker, Blur, AppLock, ChargeLimit, AudioMerge, AudioTrim, BackgroundRemover, ApkExtractor, IconMaker, CardBalance }

class MainActivity : androidx.fragment.app.FragmentActivity() {
    /** Back stack, e.g. "Home,Music". Kept as a string so it survives process recreation. */
    private var stack by mutableStateOf("Home")
    /** Tab the Tabs widget asked for, applied once Home is composed (-1 = none). */
    private var pendingTab by mutableStateOf(-1)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        savedInstanceState?.getString("stack")?.let { stack = withoutAppLock(it) }
        // App lock's settings ask for your fingerprint each time; turning the screen off closes them.
        androidx.core.content.ContextCompat.registerReceiver(
            this, screenOff, android.content.IntentFilter(Intent.ACTION_SCREEN_OFF), androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        if (savedInstanceState == null) handleShare(intent)
        setContent {
            OneUiTheme {
                val routes = stack.split(',').map(Route::valueOf)
                val home = remember { com.tools.app.home.HomeState() }
                LaunchedEffect(pendingTab) { if (pendingTab >= 0) { home.tab = pendingTab; pendingTab = -1 } }
                val musicList = rememberLazyListState()
                // Predictive back: the back swipe scrubs the same page transition, so the page
                // follows your finger and the previous one peeks in from the left. Let go to finish,
                // or swipe back the other way to cancel and it settles back into place.
                val nav = remember { SeekableTransitionState(routes) }
                // True while a back swipe is being dragged; once you let go, the LaunchedEffect below finishes the slide.
                var backGesture by remember { mutableStateOf(false) }
                // Also re-checked when a back swipe ends, so a tap made during its animation still opens its page.
                LaunchedEffect(routes, backGesture) {
                    if (!backGesture && (nav.currentState != routes || nav.targetState != routes)) nav.animateTo(routes)
                }
                val scope = rememberCoroutineScope()
                PredictiveBackHandler(enabled = routes.size > 1) { gesture ->
                    // The back stack is the single source of truth. A back swipe that starts while a page is
                    // still sliding in first lands that page, so seeking always starts from what the stack says.
                    val current = stack.split(',').map(Route::valueOf)
                    val previous = current.dropLast(1)
                    var committed = false
                    backGesture = true
                    try {
                        if (nav.currentState != current || nav.targetState != current) nav.snapTo(current)
                        // Undo the motion curve while seeking so the page moves 1:1 with the finger.
                        gesture.collect { e -> nav.seekTo(unease(e.progress * 0.85f), targetState = previous) }
                        committed = true
                        // Hand the rest of the slide to the LaunchedEffect above (it continues from where the finger
                        // let go), so a page tapped while it is still sliding interrupts it and opens normally.
                        backGesture = false
                        stack = previous.joinToString(",") { it.name }
                    } catch (e: kotlin.coroutines.cancellation.CancellationException) {
                        // Cancelled (or interrupted): show exactly what the stack holds, never an older page.
                        val show = if (committed) previous else current
                        scope.launch { nav.snapTo(show) }
                        throw e
                    } finally {
                        backGesture = false
                    }
                }

                // Behind every page: the glow of the page you're heading to. A page fading or shrinking away
                // during a transition then blends into it instead of showing a dark gap.
                val c = com.tools.app.ui.theme.OneUi.colors
                androidx.compose.foundation.layout.Box(
                    androidx.compose.ui.Modifier.fillMaxSize().drawBehind {
                        drawRect(c.background)
                        if (com.tools.app.ui.theme.Glow.enabled) glowFor(nav.targetState.last())?.let { drawGlow(it, c.isDark) }
                    },
                ) {
                rememberTransition(nav, label = "nav").AnimatedContent(
                    transitionSpec = {
                        // One UI navigation: the new page slides in from the right over the old one,
                        // which drifts a little left and dims. Back does the exact reverse (L → R),
                        // with the leaving page kept on top.
                        val forward = targetState.size >= initialState.size
                        val dur = 570 // 50% slower than the original 380 ms
                        val move = tween<androidx.compose.ui.unit.IntOffset>(dur, easing = com.tools.app.ui.theme.OneUiEasing)
                        val fade = tween<Float>(dur, easing = com.tools.app.ui.theme.OneUiEasing)
                        if (forward) {
                            // The new page slides in solid (no fade-in), so the old page never shows through it.
                            slideInHorizontally(move) { it }
                                .togetherWith(slideOutHorizontally(move) { -it / 4 } + fadeOut(fade, targetAlpha = 0.4f))
                                .apply { targetContentZIndex = 1f }
                        } else {
                            (slideInHorizontally(move) { -it / 4 } + fadeIn(fade, initialAlpha = 0.4f))
                                .togetherWith(
                                    slideOutHorizontally(move) { it } +
                                        scaleOut(fade, targetScale = 0.94f) +
                                        fadeOut(tween(dur / 2, delayMillis = dur / 2)),
                                )
                                .apply { targetContentZIndex = -1f }
                        }
                    },
                    contentKey = { it.joinToString() },
                ) { s ->
                    // Only the page that is (or is becoming) the top of the stack may navigate. Taps on a page
                    // that is sliding away used to push pages you never saw, which back then walked through.
                    val here = s.joinToString(",") { it.name }
                    fun push(r: Route) { if (stack == here && s.last() != r) stack = "$here,${r.name}" }
                    fun pop() { if (stack == here && s.size > 1) stack = here.substringBeforeLast(',') }
                    androidx.compose.runtime.CompositionLocalProvider(com.tools.app.ui.theme.LocalGlowStyle provides glowFor(s.last())) {
                    when (s.last()) {
                        Route.Home -> HomeScreen(home, open = { r ->
                            if (r == Route.AppLock) com.tools.app.applock.confirmIdentity(this@MainActivity, "Open App lock") { push(r) }
                            else push(r)
                        })
                        Route.Music -> MusicScreen(musicList, onBack = ::pop, onOpenSettings = { push(Route.MusicSettings) })
                        Route.MusicSettings -> MusicSettingsScreen(onBack = ::pop, onSignIn = { push(Route.YouTubeSignIn) })
                        Route.YouTubeSignIn -> com.tools.app.music.YouTubeSignInScreen(onDone = ::pop)
                        Route.ColorPicker -> ColorPickerScreen(onBack = ::pop)
                        Route.Blur -> BlurScreen(onBack = ::pop)
                        Route.AppLock -> AppLockScreen(onBack = ::pop, onSetPin = { push(Route.AppLockPin) }, onGuide = { push(Route.GuideAppLock) })
                        Route.AppLockPin -> com.tools.app.applock.PinSetupScreen(onDone = ::pop)
                        Route.ChargeLimit -> ChargeLimitScreen(onBack = ::pop)
                        Route.PrivateDns -> com.tools.app.dns.PrivateDnsScreen(onBack = ::pop)
                        Route.About -> com.tools.app.home.AboutScreen(onBack = ::pop)
                        Route.Battery -> com.tools.app.battery.BatteryScreen(onBack = ::pop, onGuide = { push(Route.GuideBattery) })
                        Route.GuideAppLock -> com.tools.app.home.GuideScreen(com.tools.app.home.GuideKind.AppLock, onBack = ::pop)
                        Route.GuideBattery -> com.tools.app.home.GuideScreen(com.tools.app.home.GuideKind.Battery, onBack = ::pop)
                        Route.PdfEditor -> com.tools.app.pdf.PdfEditorScreen(onBack = ::pop)
                        Route.Converter -> com.tools.app.convert.ConverterScreen(onBack = ::pop)
                        Route.Compressor -> com.tools.app.compress.CompressorScreen(onBack = ::pop)
                        Route.Random -> com.tools.app.random.RandomScreen(onBack = ::pop)
                        Route.VideoDownloader -> com.tools.app.video.VideoDownloaderScreen(onBack = ::pop)
                        Route.Reader -> com.tools.app.reader.ReaderHomeScreen(onBack = ::pop)
                        Route.QrCode -> com.tools.app.qr.QrScreen(onBack = ::pop)
                        Route.Japanese -> com.tools.app.japanese.JapaneseReaderScreen(onBack = ::pop)
                        Route.Print -> com.tools.app.print.PrintScreen(onBack = ::pop)
                        Route.AudioMerge -> com.tools.app.audio.MergeScreen(onBack = ::pop)
                        Route.AudioTrim -> com.tools.app.audio.TrimScreen(onBack = ::pop)
                        Route.BackgroundRemover -> com.tools.app.cutout.BackgroundRemoverScreen(onBack = ::pop)
                        Route.IconMaker -> com.tools.app.iconmaker.IconMakerScreen(onBack = ::pop)
                        Route.CardBalance -> com.tools.app.card.CardBalanceScreen(onBack = ::pop)
                        Route.ApkExtractor -> com.tools.app.apk.ApkScreen(onBack = ::pop)
                    }
                    }
                }
                }
            }
        }
    }

    /** Only the Home page has the glow; tool pages keep the plain One UI background. */
    private fun glowFor(r: Route): com.tools.app.ui.theme.GlowStyle? =
        if (r == Route.Home) com.tools.app.ui.theme.Glow.style else null

    companion object {
        const val ACTION_CHARGE_LIMIT = "com.tools.app.CHARGE_LIMIT"
        /** From the Tabs widget: open Home on tab [EXTRA_TAB]. */
        const val ACTION_OPEN_TAB = "com.tools.app.OPEN_TAB"
        const val EXTRA_TAB = "tab"
        /** From the Card balance widget: open Card balance, optionally ready to log ([EXTRA_MODE] "minus" / "plus"). */
        const val ACTION_CARD = "com.tools.app.CARD_BALANCE"
        const val EXTRA_MODE = "mode"
        const val ACTION_PRIVATE_DNS = "com.tools.app.PRIVATE_DNS"
    }

    private val screenOff = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context, intent: Intent) { stack = withoutAppLock(stack) }
    }

    /** The stack with App lock (and the pages opened from it) closed. */
    private fun withoutAppLock(s: String) = s.split(',').takeWhile { it != Route.AppLock.name }.joinToString(",")

    override fun onDestroy() {
        runCatching { unregisterReceiver(screenOff) }
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("stack", stack)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShare(intent)
    }

    private fun handleShare(intent: Intent?) {
        if (intent?.action == ACTION_CARD) {
            stack = "Home,CardBalance"
            when (intent.getStringExtra(EXTRA_MODE)) {
                "minus" -> com.tools.app.card.CardStore.pendingMode.value = true
                "plus" -> com.tools.app.card.CardStore.pendingMode.value = false
            }
            return
        }
        if (intent?.action == ACTION_OPEN_TAB) {
            stack = "Home"
            pendingTab = intent.getIntExtra(EXTRA_TAB, 0).coerceIn(0, 4)
            return
        }
        if (intent?.action == ACTION_PRIVATE_DNS) { stack = "Home,PrivateDns"; return }
        if (intent?.action == "android.service.quicksettings.action.QS_TILE_PREFERENCES") {
            @Suppress("DEPRECATION")
            val tile = intent.getParcelableExtra<android.content.ComponentName>(Intent.EXTRA_COMPONENT_NAME)
            stack = if (tile?.className?.contains("Dns") == true) "Home,PrivateDns" else "Home,ChargeLimit"
            return
        }
        if (intent?.getStringExtra("route") == "PdfEditor") { stack = "Home,PdfEditor"; return }
        // Print: from the document reader (route extra + data) or the "Print" share target (alias of this activity).
        val printShare = intent?.component?.className?.endsWith(".PrintShare") == true
        if (intent?.getStringExtra("route") == "Print" || printShare) {
            @Suppress("DEPRECATION")
            val uris = buildList {
                intent?.data?.let(::add)
                intent?.getParcelableExtra<android.net.Uri>(Intent.EXTRA_STREAM)?.let(::add)
                intent?.getParcelableArrayListExtra<android.net.Uri>(Intent.EXTRA_STREAM)?.let(::addAll)
            }.distinct()
            if (uris.isNotEmpty()) com.tools.app.print.PrintSession.open(this, uris)
            stack = "Home,Print"
            return
        }
        if (intent?.action == ACTION_CHARGE_LIMIT) {
            stack = "Home,ChargeLimit"
            return
        }
        if (intent?.action != Intent.ACTION_SEND) return
        val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return
        // "Video downloader" in the share sheet is an alias of this activity.
        if (intent.component?.className?.endsWith(".VideoShare") == true) {
            stack = "Home,VideoDownloader"
            com.tools.app.video.VideoDownloader.fetch(text)
            return
        }
        stack = "Home,Music"
        MusicDownloader.fetch(text)
    }
}

/** Inverse of [com.tools.app.ui.theme.OneUiEasing]: the animation fraction that puts the page at [p]. */
private fun unease(p: Float): Float {
    var lo = 0f; var hi = 1f
    repeat(20) {
        val mid = (lo + hi) / 2
        if (com.tools.app.ui.theme.OneUiEasing.transform(mid) < p) lo = mid else hi = mid
    }
    return (lo + hi) / 2
}
