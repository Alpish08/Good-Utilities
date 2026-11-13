package com.tools.app.home

import androidx.compose.material3.Text
import androidx.compose.ui.graphics.graphicsLayer
import com.tools.app.ui.theme.OneUi
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material.icons.automirrored.rounded.QueueMusic

import androidx.compose.material.icons.rounded.*
import androidx.compose.material.icons.automirrored.rounded.MenuBook

import androidx.compose.material.icons.Icons

import com.tools.app.ui.OneUiIcons
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import com.tools.app.ui.collapseHeader
import kotlinx.coroutines.launch
import androidx.compose.animation.togetherWith
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import com.tools.app.ui.card
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import com.tools.app.Route
import com.tools.app.applock.AppLock
import com.tools.app.battery.ChargeLimit
import com.tools.app.music.MusicDownloader
import com.tools.app.music.engine.Phase
import com.tools.app.ui.ColorIcon
import com.tools.app.ui.IconRowDivider
import com.tools.app.ui.ListRow
import com.tools.app.ui.OneUiScaffold
import com.tools.app.ui.OverflowMenu
import com.tools.app.ui.cardList
import com.tools.app.ui.sectionLabel
import com.tools.app.ui.theme.IconColors

private data class Tool(val route: Route, val title: String, val summary: String, val icon: ImageVector, val color: Color)

/** Home's scroll position, kept by MainActivity so it survives opening a tool. */
class HomeState {
    val list = LazyListState()
    /** Selected bottom tab (Media, Files, Images, Device, Miscellaneous). */
    var tab by androidx.compose.runtime.mutableIntStateOf(0)
}

@Composable
fun HomeScreen(state: HomeState, open: (Route) -> Unit) {
    val context = LocalContext.current
    val lock by AppLock.config.collectAsState()
    val music by MusicDownloader.state.collectAsState()
    val limit = ChargeLimit.activeLimit(context)
    // Battery health for the Miscellaneous row (Samsung's figures when Good Utilities may read them).
    var battery by remember { mutableStateOf<com.tools.app.battery.BatterySnapshot?>(null) }
    LaunchedEffect(Unit) {
        battery = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            com.tools.app.battery.BatteryHealth.read(context).copy(samsung = com.tools.app.battery.BatteryHealth.samsung(context))
        }
    }
    // Re-checked every time you come back (e.g. from the All files access page in Settings).
    var access by remember { mutableStateOf(com.tools.app.Storage.hasAccess()) }
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, e ->
            if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) access = com.tools.app.Storage.hasAccess()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    val musicSummary = when (music.phase) {
        Phase.Downloading -> "Downloading ${minOf(music.finishedCount + 1, music.totalQueued)} of ${music.totalQueued}…"
        else -> "YouTube songs and playlists to MP3"
    }
    val groups = listOf(
        "Media" to listOf(
            Tool(Route.Music, "Music downloader", musicSummary, Icons.Rounded.MusicNote, IconColors.Red),
            Tool(Route.VideoDownloader, "Video downloader", "YouTube, TikTok, Instagram, X and more", Icons.Rounded.VideoLibrary, IconColors.Blue),
            Tool(Route.AudioMerge, "Audio merger", "Join songs into one MP3 with a crossfade", Icons.AutoMirrored.Rounded.QueueMusic, IconColors.Orange),
            Tool(Route.AudioTrim, "Audio trimmer", "Cut a song without losing quality", Icons.Rounded.ContentCut, IconColors.Purple),
        ),
        "Files" to listOf(
            Tool(Route.Reader, "Document reader", "PDFs, Word files and text, One UI style", Icons.AutoMirrored.Rounded.MenuBook, IconColors.Orange),
            Tool(Route.PdfEditor, "PDF editor", "Merge, split, reorder, sign and annotate PDFs", Icons.Rounded.PictureAsPdf, IconColors.Red),
            Tool(Route.Converter, "File converter", "Images, documents, audio and video", Icons.Rounded.SwapHoriz, IconColors.Teal),
            Tool(Route.Print, "Print", "Print PDFs, photos and documents", Icons.Rounded.Print, IconColors.Blue),
            Tool(Route.Compressor, "Compressor", "Shrink videos and audio files", Icons.Rounded.Compress, IconColors.Blue),
        ),
        "Images" to listOf(
            Tool(Route.ColorPicker, "Color picker", "Sample any pixel, get HEX, RGB and palette", Icons.Rounded.Colorize, IconColors.Pink),
            Tool(Route.Blur, "Image blur", "Blur a whole photo or brush over details", Icons.Rounded.BlurOn, IconColors.Purple),
            Tool(Route.BackgroundRemover, "Background remover", "Cut out people, pets and objects as PNG", Icons.Rounded.AutoFixHigh, IconColors.Teal),
        ),
        "Device" to listOf(
            Tool(
                Route.AppLock, "App lock",
                if (lock.enabled && lock.locked.isNotEmpty()) "On · ${lock.locked.size} ${if (lock.locked.size == 1) "app" else "apps"} locked" else "Lock apps with fingerprint, face or PIN",
                Icons.Rounded.Lock, IconColors.Blue,
            ),
            Tool(
                Route.PrivateDns, "Private DNS",
                if (com.tools.app.dns.PrivateDns.isCustom(context)) "Custom · ${com.tools.app.dns.PrivateDns.host(context)}" else "Automatic",
                Icons.Rounded.Language, IconColors.Teal,
            ),
            Tool(
                Route.ChargeLimit, "Charge limit",
                limit?.let { "Stops charging at $it%" } ?: "Custom battery protection and bypass charging",
                Icons.Rounded.BatteryChargingFull, IconColors.Green,
            ),
        ),
        "Miscellaneous" to listOf(
            Tool(
                Route.Battery, "Battery",
                battery?.let { b -> b.healthPercent?.let { "Health ${if (b.healthIsSamsung) "" else "≈ "}$it%" + (b.totalCycles?.let { c -> " · $c cycles" } ?: "") } } ?: "Health, temperature and charging speed",
                Icons.Rounded.BatteryStd, IconColors.Green,
            ),
            Tool(Route.ApkExtractor, "APK extractor", "Save apps as APK, merging split bundles", Icons.Rounded.Android, IconColors.Blue),
            Tool(Route.Japanese, "Japanese reader", "Readings and meanings for Japanese on your screen", Icons.Rounded.Translate, IconColors.Red),
            Tool(Route.QrCode, "QR code", "Turn a link or any text into a QR code", Icons.Rounded.QrCode2, IconColors.Teal),
            Tool(Route.Random, "Random", "Numbers in any range, or roll some dice", Icons.Rounded.Casino, IconColors.Purple),
        ),
    )

    // Search opens from the toolbar's search button as a full-width bar at the bottom.
    var query by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    fun closeSearch() {
        query = ""; searching = false; focusManager.clearFocus()
        // Back to the tab's list with the title folded, not scrolled past it.
        scope.launch { kotlinx.coroutines.delay(60); state.list.collapseHeader() }
    }
    androidx.activity.compose.BackHandler(enabled = searching) { closeSearch() }
    val q = query.trim()
    val matches = if (q.isEmpty()) emptyList() else groups.flatMap { it.second }.filter {
        it.title.contains(q, ignoreCase = true) || it.summary.contains(q, ignoreCase = true)
    }
    // Opening search folds the big title away so the results have room above the keyboard.
    // Re-checked as results change: a short results list first jumps back to the top, then gets room to fold again.
    LaunchedEffect(searching, q) {
        if (!searching) return@LaunchedEffect
        kotlinx.coroutines.delay(60)
        if (state.list.firstVisibleItemIndex == 0) state.list.collapseHeader()
    }

    // Clock-style icon tabs: one category at a time (One UI line icons; filled while selected).
    val tabs = listOf(
        com.tools.app.ui.PillTab("Media", com.tools.app.ui.OneUiIcons.TabMedia, com.tools.app.ui.OneUiIcons.TabMediaOn),
        com.tools.app.ui.PillTab("Files", com.tools.app.ui.OneUiIcons.TabFiles, com.tools.app.ui.OneUiIcons.TabFilesOn),
        com.tools.app.ui.PillTab("Images", com.tools.app.ui.OneUiIcons.TabImages, com.tools.app.ui.OneUiIcons.TabImagesOn),
        com.tools.app.ui.PillTab("Device", com.tools.app.ui.OneUiIcons.TabDevice, com.tools.app.ui.OneUiIcons.TabDeviceOn),
        com.tools.app.ui.PillTab("Miscellaneous", com.tools.app.ui.OneUiIcons.TabMisc, com.tools.app.ui.OneUiIcons.TabMiscOn),
    )
    var choosingGlow by remember { mutableStateOf(false) }
    if (choosingGlow) GlowPicker(onDismiss = { choosingGlow = false })

    OneUiScaffold(
        title = "Good Utilities",
        subtitle = "${groups.sumOf { it.second.size }} tools",
        listState = state.list,
        // Home keeps the classic pinned toolbar; floating buttons are for sub-pages.
        floatingToolbar = false,
        actions = {
            com.tools.app.ui.ToolbarIcon(com.tools.app.ui.OneUiIcons.Search, "Search") { searching = true }
            OverflowMenu(listOf("Background" to { choosingGlow = true }, "About" to { open(Route.About) }))
        },
        bottomBar = {
            // The two bars cross-fade in place. Faded per draw (not through an offscreen layer), so the bars,
            // which sit a few dp outside their own bounds, are never cropped mid-fade.
            androidx.compose.foundation.layout.Box(contentAlignment = androidx.compose.ui.Alignment.BottomCenter) {
                FadeSlot(!searching) { com.tools.app.ui.TabPill(tabs, state.tab, { state.tab = it }) }
                FadeSlot(searching) { com.tools.app.ui.SearchBar(query, { query = it }, onClose = ::closeSearch) }
            }
        },
    ) {
        if (searching && q.isNotEmpty()) {
            sectionLabel(if (matches.isEmpty()) "No tools found" else "Results", key = "search-label")
            if (matches.isNotEmpty()) cardList(matches, key = { "found-" + it.route.name }, dividerStart = IconRowDivider) { t ->
                ListRow(t.title, summary = t.summary, leading = { ColorIcon(t.icon, t.color) }, onClick = { closeSearch(); open(t.route) })
            }
            return@OneUiScaffold
        }
        if (!access) {
            card(key = "storage-access") {
                ListRow(
                    "Allow All files access",
                    summary = "Needed to save everything to Internal Storage/${com.tools.app.Storage.ROOT}.",
                    leading = { ColorIcon(Icons.Rounded.Folder, IconColors.Orange) },
                    trailing = {
                        com.tools.app.ui.PillButton("Allow", onClick = { context.startActivity(com.tools.app.Storage.accessIntent(context)) }, height = 38.dp)
                    },
                    onClick = { context.startActivity(com.tools.app.Storage.accessIntent(context)) },
                )
            }
        }
        // The selected tab's tools (switching tabs swaps them instantly, like Samsung's apps).
        item(key = "tab-content") {
            run {
                val tab = state.tab
                val (label, tools) = groups[tab]
                androidx.compose.foundation.layout.Column {
                    Text(
                        label, fontSize = 13.5.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = OneUi.colors.textSecondary,
                        modifier = Modifier.padding(start = 24.dp, end = 20.dp, top = 14.dp),
                    )
                    com.tools.app.ui.RoundCard {
                        tools.forEachIndexed { i, t ->
                            ListRow(t.title, summary = t.summary, leading = { ColorIcon(t.icon, t.color) }, onClick = { open(t.route) })
                            if (i != tools.lastIndex) com.tools.app.ui.CardDivider(IconRowDivider)
                        }
                    }
                }
            }
        }
    }
}

/** Home → Background: the glow choices, applied live behind this dialog. */
@Composable
private fun GlowPicker(onDismiss: () -> Unit) {
    val context = LocalContext.current
    com.tools.app.ui.OneUiDialog("Background", onDismiss, buttons = listOf("Done" to onDismiss)) {
        com.tools.app.ui.theme.GlowStyle.entries.forEach { g ->
            androidx.compose.foundation.layout.Row(
                Modifier.fillMaxWidth().clip(androidx.compose.foundation.shape.RoundedCornerShape(14.dp))
                    .clickable { com.tools.app.ui.theme.Glow.choose(context, g) }
                    .padding(horizontal = 6.dp, vertical = 11.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                com.tools.app.ui.RadioDot(com.tools.app.ui.theme.Glow.style == g)
                Text(g.label, fontSize = 16.sp, color = OneUi.colors.text, modifier = Modifier.padding(start = 14.dp))
            }
        }
    }
}

/** Shows [content] while [visible], fading in and out without clipping it to its bounds. */
@Composable
private fun FadeSlot(visible: Boolean, content: @Composable () -> Unit) {
    val alpha by androidx.compose.animation.core.animateFloatAsState(
        if (visible) 1f else 0f,
        if (visible) androidx.compose.animation.core.tween(220, delayMillis = 60) else androidx.compose.animation.core.tween(150),
        label = "fadeSlot",
    )
    if (!visible && alpha == 0f) return
    androidx.compose.foundation.layout.Box(
        Modifier.graphicsLayer {
            this.alpha = alpha
            compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.ModulateAlpha
        },
    ) { content() }
}
