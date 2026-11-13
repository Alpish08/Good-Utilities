package com.tools.app.video

import android.content.ClipboardManager
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.tools.app.Storage
import com.tools.app.music.MusicDownloader
import com.tools.app.music.engine.EngineState
import com.tools.app.ui.*
import com.tools.app.ui.theme.OneUi
import java.util.Locale

@Composable
fun VideoDownloaderScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        VideoDownloader.attach(context)
        MusicDownloader.prepareEngine()
    }
    val s by VideoDownloader.state.collectAsState()
    val engine by MusicDownloader.state.collectAsState()
    val busy = s.phase == VPhase.Downloading

    OneUiScaffold(
        title = "Video downloader",
        onBack = onBack,
        bottomBar = bottomBar(s, engine.engine is EngineState.Ready),
    ) {
        item(key = "link") { LinkCard(s) }

        val error = s.error
        if (error != null && !s.noAccess) item(key = "error") {
            Text(error, color = OneUi.colors.error, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(horizontal = 28.dp, vertical = 6.dp))
        }
        if (s.noAccess) card("noaccess") {
            ListRow(
                "Storage access needed",
                summary = Storage.NoAccess().message,
                summaryColor = OneUi.colors.error,
            )
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                val c = LocalContext.current
                PillButton("Open settings", onClick = { c.startActivity(Storage.accessIntent(c)) }, height = 46.dp, modifier = Modifier.fillMaxWidth())
            }
        }

        when (val e = engine.engine) {
            EngineState.Preparing -> card("engine") {
                ListRow(
                    "Preparing download engine",
                    summary = "Unpacking yt-dlp and FFmpeg. Only happens on first launch.",
                    trailing = { CircularProgressIndicator(Modifier.size(24.dp), color = OneUi.colors.accent, strokeWidth = 2.5.dp) },
                )
            }
            is EngineState.Failed -> card("engine") {
                ListRow("Download engine failed to start", summary = e.message, summaryColor = OneUi.colors.error)
            }
            else -> Unit
        }

        val info = s.info
        when {
            s.phase == VPhase.Fetching -> card("fetching") {
                ListRow(
                    "Reading link…",
                    summary = s.link,
                    leading = { CircularProgressIndicator(Modifier.size(28.dp), color = OneUi.colors.accent, strokeWidth = 3.dp) },
                )
            }

            info == null -> {
                sectionLabel("Supported")
                cardList(SUPPORTED, key = { it.first }) { (title, summary, icon) ->
                    ListRow(title, summary = summary)
                }
            }

            else -> {
                item(key = "preview") { PreviewCard(info) }
                when (s.phase) {
                    VPhase.Downloading -> item(key = "progress") { ProgressCard(s) }
                    VPhase.Finished -> item(key = "finished") { FinishedCard(s) }
                    else -> Unit
                }
                if (info.multi && info.items.size > 1 && s.phase != VPhase.Finished) {
                    val allSelected = s.selected.size == info.items.size
                    sectionLabel("Videos", key = "videos-label", trailing = if (s.phase == VPhase.Ready) {
                        {
                            Text(
                                if (allSelected) "Deselect all" else "Select all",
                                color = OneUi.colors.accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { VideoDownloader.selectAll(!allSelected) }.padding(8.dp),
                            )
                        }
                    } else null)
                    cardList(info.items, key = { "item-${it.index}" }, dividerStart = 86.dp) { ItemRow(it, s) }
                }
                if (s.phase == VPhase.Ready) optionCards(s, info)
            }
        }
    }
}

private val SUPPORTED = listOf(
    Triple("Supported sites", "YouTube, TikTok, Instagram, X, Reddit, Facebook, Vimeo and more", OneUiIcons.Globe),
    Triple("Share from any app", "Tap Share → Video downloader on a video or post, or paste its link above.", OneUiIcons.Share),
    Triple("Saved to ${Storage.label(Storage.Folder.Videos)}", "Ready in Samsung Gallery and any player.", OneUiIcons.Folder),
)

@Composable
private fun LinkCard(s: VState) {
    val c = OneUi.colors
    val context = LocalContext.current
    val focus = LocalFocusManager.current
    val busy = s.phase == VPhase.Downloading
    RoundCard {
        Column(Modifier.padding(start = 20.dp, end = 14.dp, top = 18.dp, bottom = 16.dp)) {
            Text("Video link", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = c.accent)
            Row(verticalAlignment = Alignment.CenterVertically) {
                BasicTextField(
                    value = s.link,
                    onValueChange = VideoDownloader::setLink,
                    enabled = !busy,
                    singleLine = true,
                    textStyle = TextStyle(fontFamily = com.tools.app.ui.theme.SamsungFont, fontSize = 16.sp, color = c.text),
                    cursorBrush = SolidColor(c.accent),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { focus.clearFocus(); VideoDownloader.fetch(s.link) }),
                    modifier = Modifier.weight(1f).padding(vertical = 12.dp),
                    decorationBox = { inner ->
                        Box {
                            if (s.link.isEmpty()) Text("Paste a link", fontSize = 16.sp, color = c.textSecondary.copy(alpha = 0.7f))
                            inner()
                        }
                    },
                )
                if (s.link.isNotEmpty() && !busy) ToolbarIcon(OneUiIcons.Close, "Clear") { VideoDownloader.reset() }
            }
            Row(Modifier.fillMaxWidth().padding(top = 6.dp, end = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PillButton(
                    "Paste", tonal = true, icon = OneUiIcons.ContentPaste, height = 46.dp, enabled = !busy,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        val clip = context.getSystemService(ClipboardManager::class.java).primaryClip
                        val text = clip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()
                        if (!text.isNullOrBlank()) { focus.clearFocus(); VideoDownloader.fetch(text) }
                    },
                )
                PillButton(
                    if (s.phase == VPhase.Fetching) "Fetching…" else "Fetch", height = 46.dp,
                    enabled = s.link.isNotBlank() && s.phase != VPhase.Fetching && !busy,
                    modifier = Modifier.weight(1f),
                    onClick = { focus.clearFocus(); VideoDownloader.fetch(s.link) },
                )
            }
        }
    }
}

@Composable
private fun PreviewCard(info: VInfo) {
    val c = OneUi.colors
    RoundCard {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = info.thumbnail, contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.size(width = 124.dp, height = 78.dp).clip(RoundedCornerShape(16.dp)).background(c.divider),
            )
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(info.title, fontSize = 18.sp, lineHeight = 23.sp, fontWeight = FontWeight.Bold, color = c.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (info.uploader.isNotBlank()) Text(info.uploader, fontSize = 14.sp, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
                val meta = listOfNotNull(
                    info.site.ifBlank { null },
                    if (info.multi && info.items.size > 1) "${info.items.size} videos" else null,
                    info.durationSec?.takeIf { it > 0 }?.let(::formatDuration),
                ).joinToString(" · ")
                if (meta.isNotEmpty()) Text(meta, fontSize = 14.sp, color = c.textSecondary, modifier = Modifier.padding(top = 2.dp))
            }
        }
    }
}

@Composable
private fun ItemRow(item: VItem, s: VState) {
    val c = OneUi.colors
    val selecting = s.phase == VPhase.Ready
    ListRow(
        title = item.title,
        summary = listOfNotNull(item.uploader.ifBlank { null }, item.durationSec?.takeIf { it > 0 }?.let(::formatDuration)).joinToString(" · ").ifBlank { null },
        dimmed = selecting && item.index !in s.selected,
        onClick = if (selecting) ({ VideoDownloader.toggle(item.index) }) else null,
        leading = {
            AsyncImage(
                model = item.thumbnail, contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.size(52.dp).clip(RoundedCornerShape(12.dp)).background(c.divider),
            )
        },
        trailing = { if (selecting) RoundCheck(item.index in s.selected) },
    )
}

private fun androidx.compose.foundation.lazy.LazyListScope.optionCards(s: VState, info: VInfo) {
    sectionLabel("Type")
    card("type") {
        Box(Modifier.padding(14.dp)) {
            SegmentedControl(listOf("Video", "Audio only"), selected = if (s.audioOnly) 1 else 0, onSelect = { VideoDownloader.setAudioOnly(it == 1) })
        }
    }
    if (s.audioOnly) {
        sectionLabel("Format")
        card("audioFormat") {
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for ((id, label) in listOf("m4a" to "M4A", "mp3" to "MP3", "opus" to "Opus")) {
                    Chip(label, s.audioFormat == id, { VideoDownloader.setAudioFormat(id) })
                }
            }
        }
        description("Cover art is embedded in M4A and MP3 files. Saved to ${Storage.label(Storage.Folder.Audio)}.")
    } else {
        val heights = info.heights
        if (heights.isNotEmpty()) {
            sectionLabel("Quality")
            val rows = listOf(0) + heights.map { it.height }
            cardList(rows, key = { "q-$it" }, dividerStart = 16.dp) { h ->
                val fmt = heights.firstOrNull { it.height == h } ?: heights.first()
                val selected = info.items.filter { it.index in s.selected }
                val sizes = selected.map { item ->
                    val f = item.formats.filter { it.height == fmt.height }.maxByOrNull { it.size ?: 0L }
                    f?.size?.let { it + if (f.hasAudio) 0L else (item.audioSize ?: 0L) }
                }
                val size = if (sizes.isNotEmpty() && sizes.all { it != null }) "~" + formatSize(sizes.sumOf { it!! }) else null
                val fps = if (fmt.fps >= 50) "${fmt.fps} fps" else null
                ListRow(
                    title = if (h == 0) "Best available" else "${fmt.shortSide}p",
                    summary = (if (h == 0) listOfNotNull("Up to ${fmt.shortSide}p", size, fps) else listOfNotNull(size, fps)).joinToString(" · ").ifBlank { null },
                    onClick = { VideoDownloader.setQuality(h) },
                    trailing = { RadioDot(s.quality == h) },
                )
            }
        }
        card("compatible") {
            ListRow(
                "Compatible format",
                summary = if (s.compatible) "H.264 + AAC in MP4. Plays everywhere, including Samsung Gallery."
                else "Best quality in any codec (VP9, AV1). Some players can't open it.",
                onClick = { VideoDownloader.setCompatible(!s.compatible) },
                trailing = { OneUiSwitch(s.compatible) },
            )
        }
    }
}

@Composable
private fun ProgressCard(s: VState) {
    val c = OneUi.colors
    RoundCard {
        Column(Modifier.padding(20.dp)) {
            Text(
                if (s.total > 1) "Downloading ${s.current} of ${s.total}" else s.status.removeSuffix("…").ifBlank { "Downloading" } + "…",
                fontSize = 15.5.sp, fontWeight = FontWeight.SemiBold, color = c.text,
            )
            ThinProgress(s.progress, Modifier.padding(top = 12.dp))
            Row(Modifier.fillMaxWidth().padding(top = 10.dp)) {
                Text(
                    listOfNotNull(if (s.total > 1) s.status.removeSuffix("…") else null, s.speed.ifBlank { null }).joinToString(" · "),
                    fontSize = 13.sp, color = c.textSecondary, modifier = Modifier.weight(1f),
                )
                Text("${(s.progress * 100).toInt()}%", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = c.textSecondary)
            }
        }
    }
}

@Composable
private fun FinishedCard(s: VState) {
    val c = OneUi.colors
    val context = LocalContext.current
    RoundCard {
        ListRow(
            "Saved to ${s.savedLabel}",
            summary = (s.saved.map { it.name } + listOfNotNull(if (s.failed > 0) "${s.failed} failed" + (s.error?.let { ": $it" } ?: "") else null)).joinToString("\n"),
            leading = { Icon(OneUiIcons.CheckCircle, null, tint = c.accent, modifier = Modifier.size(32.dp)) },
        )
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val uris = s.saved.mapNotNull { it.uri }
            if (s.saved.size == 1 && uris.size == 1) PillButton("Open", tonal = true, icon = OneUiIcons.PlayArrow, height = 46.dp, modifier = Modifier.weight(1f), onClick = {
                val f = s.saved[0]
                val intent = Intent(Intent.ACTION_VIEW).setDataAndType(f.uri, f.mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                runCatching { context.startActivity(intent) }.onFailure { Toast.makeText(context, "No app can open this file", Toast.LENGTH_SHORT).show() }
            })
            if (uris.isNotEmpty()) PillButton("Share", tonal = true, icon = OneUiIcons.Share, height = 46.dp, modifier = Modifier.weight(1f), onClick = {
                val mime = s.saved.map { it.mime.substringBefore('/') }.distinct().singleOrNull()?.let { "$it/*" } ?: "*/*"
                val intent = if (uris.size == 1) Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris[0])
                else Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
                intent.setType(if (uris.size == 1) s.saved[0].mime else mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                runCatching { context.startActivity(Intent.createChooser(intent, null)) }
            })
        }
    }
}

private fun bottomBar(s: VState, engineReady: Boolean): (@Composable () -> Unit)? = when (s.phase) {
    VPhase.Ready -> {
        {
            val n = s.selected.size
            PillButton(
                if (n > 1) "Download $n videos" else "Download",
                onClick = VideoDownloader::download, enabled = n > 0 && engineReady,
                icon = OneUiIcons.Download, modifier = Modifier.fillMaxWidth(),
            )
        }
    }
    VPhase.Downloading -> {
        { PillButton("Cancel", onClick = VideoDownloader::cancel, tonal = true, modifier = Modifier.fillMaxWidth()) }
    }
    VPhase.Finished -> {
        { PillButton("New link", onClick = VideoDownloader::reset, modifier = Modifier.fillMaxWidth()) }
    }
    else -> null
}

private fun formatSize(bytes: Long): String {
    val mb = bytes / 1048576.0
    return if (mb >= 1000) String.format(Locale.US, "%.1f GB", mb / 1024) else String.format(Locale.US, "%.0f MB", mb.coerceAtLeast(1.0))
}
