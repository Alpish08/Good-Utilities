package com.tools.app.music

import com.tools.app.ui.OneUiIcons
import android.Manifest
import android.content.ClipboardManager
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.core.content.ContextCompat
import coil3.compose.AsyncImage
import com.tools.app.music.MusicDownloader
import com.tools.app.music.engine.AppState
import com.tools.app.music.engine.EngineState
import com.tools.app.music.engine.Phase
import com.tools.app.music.engine.Playlist
import com.tools.app.music.engine.Status
import com.tools.app.music.engine.Track
import com.tools.app.music.engine.TrackState
import com.tools.app.ui.*
import com.tools.app.ui.theme.OneUi

@Composable
fun MusicScreen(listState: LazyListState, onBack: () -> Unit, onOpenSettings: () -> Unit) {
    androidx.compose.runtime.LaunchedEffect(Unit) { MusicDownloader.prepareEngine() }
    val s by MusicDownloader.state.collectAsState()
    val context = LocalContext.current
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        MusicDownloader.startDownload(context)
    }
    val download = {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        else MusicDownloader.startDownload(context)
    }

    OneUiScaffold(
        title = "Music Downloader",
        onBack = onBack,
        listState = listState,
        actions = { com.tools.app.ui.OverflowMenu(listOf("Settings" to onOpenSettings)) },
        bottomBar = bottomBar(s, onDownload = download, onRetry = { MusicDownloader.retryFailed(context) }),
    ) {
        item(key = "link") { LinkCard(s) }
        item(key = "status") {
            Text(subtitle(s), fontSize = 14.sp, color = OneUi.colors.textSecondary, modifier = Modifier.padding(horizontal = 28.dp, vertical = 4.dp))
        }

        val error = s.error
        if (error != null) item(key = "error") {
            Text(
                error, color = OneUi.colors.error, fontSize = 14.sp,
                modifier = Modifier.padding(horizontal = 28.dp, vertical = 6.dp),
            )
        }

        when (val e = s.engine) {
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

        val playlist = s.playlist
        when {
            s.phase == Phase.Fetching -> card("fetching") {
                ListRow(
                    "Reading link…",
                    summary = s.link,
                    leading = { CircularProgressIndicator(Modifier.size(28.dp), color = OneUi.colors.accent, strokeWidth = 3.dp) },
                )
            }

            playlist == null -> {
                sectionLabel("How it works")
                cardList(HOW_IT_WORKS, key = { it.first }, dividerStart = 74.dp) { (title, summary, icon) ->
                    ListRow(title, summary = summary, leading = { IconBadge(icon) })
                }
            }

            else -> {
                item(key = "playlist") { PlaylistCard(playlist, s) }
                if (s.phase == Phase.Finished) item(key = "summary") { SummaryCard(s, playlist) }

                val allSelected = s.selected.size == playlist.tracks.size
                // A single song is already shown in full by the card above.
                if (playlist.tracks.size > 1) sectionLabel("Songs", key = "songs-label", trailing = if (s.phase == Phase.Ready && playlist.tracks.size > 1) {
                    {
                        Text(
                            if (allSelected) "Deselect all" else "Select all",
                            color = OneUi.colors.accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { MusicDownloader.selectAll(!allSelected) }.padding(8.dp),
                        )
                    }
                } else null)
                if (playlist.tracks.size > 1) cardList(playlist.tracks, key = { it.id }, dividerStart = 86.dp) { track ->
                    TrackRow(track, s)
                }
            }
        }
    }
}

private val HOW_IT_WORKS = listOf(
    Triple("Share from YouTube", "Tap Share → Music Downloader on any song, video or playlist, or paste its link above.", OneUiIcons.Share),
    Triple("MP3 with album art", "Square cover, title, artist, album and track number are embedded.", OneUiIcons.MusicNote),
    Triple("Saved to Good Utilities/Music", "Playlists get their own folder. Ready in Samsung Music or any player.", OneUiIcons.Folder),
)

private fun subtitle(s: AppState): String = when (s.phase) {
    Phase.Idle -> "YouTube songs and playlists to MP3"
    Phase.Fetching -> "Reading link…"
    Phase.Ready -> s.playlist?.let { if (it.tracks.size == 1) "Ready to download" else "${s.selected.size} of ${it.tracks.size} songs selected" } ?: ""
    Phase.Downloading -> "Downloading ${minOf(s.finishedCount + 1, s.totalQueued)} of ${s.totalQueued}"
    Phase.Finished -> s.count(Status.Done).let { n -> "$n ${if (n == 1) "song" else "songs"} saved to Music" }
}

@Composable
private fun LinkCard(s: AppState) {
    val c = OneUi.colors
    val context = LocalContext.current
    val focus = LocalFocusManager.current
    val busy = s.phase == Phase.Downloading
    RoundCard {
        Column(Modifier.padding(start = 20.dp, end = 14.dp, top = 18.dp, bottom = 16.dp)) {
            Text("Song or playlist link", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = c.accent)
            Row(verticalAlignment = Alignment.CenterVertically) {
                BasicTextField(
                    value = s.link,
                    onValueChange = MusicDownloader::setLink,
                    enabled = !busy,
                    singleLine = true,
                    textStyle = TextStyle(fontFamily = com.tools.app.ui.theme.SamsungFont, fontSize = 16.sp, color = c.text),
                    cursorBrush = SolidColor(c.accent),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { focus.clearFocus(); MusicDownloader.fetch(s.link) }),
                    modifier = Modifier.weight(1f).padding(vertical = 12.dp),
                    decorationBox = { inner ->
                        Box {
                            if (s.link.isEmpty()) Text("Paste a YouTube link", fontSize = 16.sp, color = c.textSecondary.copy(alpha = 0.7f))
                            inner()
                        }
                    },
                )
                if (s.link.isNotEmpty() && !busy) ToolbarIcon(OneUiIcons.Close, "Clear") { MusicDownloader.reset() }
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 6.dp, end = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PillButton(
                    "Paste", tonal = true, icon = OneUiIcons.ContentPaste, height = 46.dp, enabled = !busy,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        val clip = context.getSystemService(ClipboardManager::class.java).primaryClip
                        val text = clip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()
                        if (!text.isNullOrBlank()) { focus.clearFocus(); MusicDownloader.fetch(text) }
                    },
                )
                PillButton(
                    if (s.phase == Phase.Fetching) "Fetching…" else "Fetch", height = 46.dp,
                    enabled = s.link.isNotBlank() && s.phase != Phase.Fetching && !busy,
                    modifier = Modifier.weight(1f),
                    onClick = { focus.clearFocus(); MusicDownloader.fetch(s.link) },
                )
            }
        }
    }
}

@Composable
private fun PlaylistCard(playlist: Playlist, s: AppState) {
    val c = OneUi.colors
    RoundCard {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = playlist.thumbnail, contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.size(92.dp).clip(RoundedCornerShape(20.dp)).background(c.divider),
            )
            Spacer(Modifier.width(18.dp))
            Column(Modifier.weight(1f)) {
                Text(playlist.title, fontSize = 20.sp, lineHeight = 25.sp, fontWeight = FontWeight.Bold, color = c.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (playlist.uploader.isNotBlank()) Text(playlist.uploader, fontSize = 14.sp, color = c.textSecondary, maxLines = 1, modifier = Modifier.padding(top = 2.dp))
                val n = playlist.tracks.size
                Text(
                    if (n == 1) "Song · ${playlist.tracks[0].durationSec?.let(::formatDuration) ?: ""}".removeSuffix(" · ")
                    else "$n songs · ${formatTotal(playlist.totalSeconds)}",
                    fontSize = 14.sp, color = c.textSecondary, modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        if (s.phase == Phase.Downloading) ThinProgress(s.overallProgress, Modifier.padding(start = 20.dp, end = 20.dp, bottom = 20.dp))
    }
}

@Composable
private fun SummaryCard(s: AppState, playlist: Playlist) {
    val c = OneUi.colors
    val failed = s.count(Status.Failed)
    val skipped = s.count(Status.Skipped)
    RoundCard {
        ListRow(
            when {
                s.totalQueued == 1 && s.count(Status.Done) == 1 -> "Song saved"
                s.totalQueued == 1 && skipped == 1 -> "Already downloaded"
                s.totalQueued == 1 -> "Couldn't download this song"
                else -> "Saved ${s.count(Status.Done)} of ${s.totalQueued} songs"
            },
            summary = buildString {
                append("Good Utilities/Music")
                if (MusicDownloader.settings.current.subfolder && playlist.tracks.size > 1) append("/").append(playlist.title)
                if (skipped > 0 && s.totalQueued > 1) append("\n$skipped already downloaded")
                if (failed > 0) append(if (s.totalQueued == 1) "\n" + (s.trackStates.values.firstOrNull()?.error ?: "Tap Retry or update yt-dlp in Settings") else "\n$failed failed. Tap Retry or update yt-dlp in Settings")
            },
            leading = {
                Icon(
                    if (failed > 0) OneUiIcons.ErrorOutline else OneUiIcons.CheckCircle, null,
                    tint = if (failed > 0) c.error else c.accent, modifier = Modifier.size(32.dp),
                )
            },
        )
    }
}

@Composable
private fun TrackRow(track: Track, s: AppState) {
    val c = OneUi.colors
    val st = s.trackStates[track.id]
    val selecting = s.phase == Phase.Ready
    val summary = when {
        st?.status == Status.Failed -> st.error ?: "Failed"
        st?.status == Status.Skipped -> "Already downloaded"
        st?.status == Status.Converting -> "Converting to MP3…"
        else -> listOfNotNull(track.artist.ifBlank { null }, track.durationSec?.let(::formatDuration)).joinToString(" · ")
    }
    ListRow(
        title = track.title,
        summary = summary,
        summaryColor = if (st?.status == Status.Failed) c.error else null,
        dimmed = (selecting && track.id !in s.selected) || (!selecting && st == null),
        onClick = if (selecting) ({ MusicDownloader.toggle(track.id) }) else null,
        leading = {
            AsyncImage(
                model = track.thumbnail, contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.size(52.dp).clip(RoundedCornerShape(12.dp)).background(c.divider),
            )
        },
        trailing = {
            if (selecting) RoundCheck(track.id in s.selected)
            else if (st != null) StatusIndicator(st)
        },
    )
}

@Composable
private fun StatusIndicator(st: TrackState) {
    val c = OneUi.colors
    val m = Modifier.size(26.dp)
    when (st.status) {
        Status.Queued -> Icon(OneUiIcons.Schedule, "Waiting", tint = c.textSecondary.copy(alpha = 0.6f), modifier = m)
        Status.Downloading -> CircularProgressIndicator(
            progress = { st.progress }, modifier = m, color = c.accent, trackColor = c.divider, strokeWidth = 3.dp,
        )
        Status.Converting -> CircularProgressIndicator(m, color = c.accent, trackColor = c.divider, strokeWidth = 3.dp)
        Status.Done -> Icon(OneUiIcons.CheckCircle, "Saved", tint = c.accent, modifier = m)
        Status.Skipped -> Icon(OneUiIcons.CheckCircle, "Already downloaded", tint = c.textSecondary, modifier = m)
        Status.Failed -> Icon(OneUiIcons.ErrorOutline, "Failed", tint = c.error, modifier = m)
    }
}

private fun bottomBar(s: AppState, onDownload: () -> Unit, onRetry: () -> Unit): (@Composable () -> Unit)? = when (s.phase) {
    Phase.Ready -> {
        {
            val n = s.selected.size
            PillButton(
                if (n == 1) "Download song" else "Download $n songs",
                onClick = onDownload, enabled = n > 0 && s.engine is EngineState.Ready,
                icon = OneUiIcons.Download, modifier = Modifier.fillMaxWidth(),
            )
        }
    }
    Phase.Downloading -> {
        { PillButton("Cancel", onClick = MusicDownloader::cancel, tonal = true, modifier = Modifier.fillMaxWidth()) }
    }
    Phase.Finished -> {
        {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (s.count(Status.Failed) > 0) PillButton(if (s.totalQueued == 1) "Retry" else "Retry failed", onClick = onRetry, tonal = true, modifier = Modifier.weight(1f))
                PillButton("New link", onClick = MusicDownloader::reset, modifier = Modifier.weight(1f))
            }
        }
    }
    else -> null
}
