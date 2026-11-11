package com.tools.app.home

import androidx.compose.material.icons.automirrored.rounded.QueueMusic

import androidx.compose.material.icons.rounded.*
import androidx.compose.material.icons.automirrored.rounded.MenuBook

import androidx.compose.material.icons.Icons

import com.tools.app.ui.OneUiIcons
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
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
}

@Composable
fun HomeScreen(state: HomeState, open: (Route) -> Unit) {
    val context = LocalContext.current
    val lock by AppLock.config.collectAsState()
    val music by MusicDownloader.state.collectAsState()
    val limit = ChargeLimit.activeLimit(context)
    // Battery health for the Misc tab; also saves today's reading for the history chart.
    var battery by remember { mutableStateOf<com.tools.app.battery.BatterySnapshot?>(null) }
    LaunchedEffect(Unit) {
        battery = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            com.tools.app.battery.BatteryHealth.read(context).also { com.tools.app.battery.BatteryHealth.record(context, it) }
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
            Tool(Route.Music, "Music Downloader", musicSummary, Icons.Rounded.MusicNote, IconColors.Red),
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
                battery?.let { b -> b.healthPercent?.let { "Health ≈ $it%" + (b.cycles?.let { c -> " · $c cycles" } ?: "") } } ?: "Health, temperature and charging speed",
                Icons.Rounded.BatteryStd, IconColors.Green,
            ),
            Tool(Route.ApkExtractor, "APK extractor", "Save apps as APK, merging split bundles", Icons.Rounded.Android, IconColors.Blue),
            Tool(Route.QrCode, "QR code", "Turn a link or any text into a QR code", Icons.Rounded.QrCode2, IconColors.Teal),
            Tool(Route.Random, "Random", "Numbers in any range, or roll some dice", Icons.Rounded.Casino, IconColors.Purple),
        ),
    )

    OneUiScaffold(
        title = "Good Utilities",
        subtitle = "${groups.sumOf { it.second.size }} tools",
        listState = state.list,
        actions = { OverflowMenu(listOf("About" to { open(Route.About) })) },
    ) {
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
        for ((label, tools) in groups) {
            sectionLabel(label)
            cardList(tools, key = { it.route.name }, dividerStart = IconRowDivider) { t ->
                ListRow(t.title, summary = t.summary, leading = { ColorIcon(t.icon, t.color) }, onClick = { open(t.route) })
            }
        }
    }
}
