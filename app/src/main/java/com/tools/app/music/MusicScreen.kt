package com.tools.app.music

import com.tools.app.ui.tp
import com.tools.app.R
import com.tools.app.ui.t
import androidx.compose.foundation.layout.widthIn
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
        title = t(R.string.home_music_downloader),
        onBack = onBack,
        listState = listState,
        actions = { com.tools.app.ui.OverflowMenu(listOf(t(R.string.music_settings) to onOpenSettings, com.tools.app.ui.tipsItem(MUSIC_TIPS))) },
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
                    t(R.string.music_preparing_download_engine),
                    summary = t(R.string.music_unpacking_yt_dlp_and_ffmpeg_only_happe),
                    trailing = { com.tools.app.ui.OneUiSpinner(Modifier.size(24.dp), color = OneUi.colors.accent, strokeWidth = 2.5.dp) },
                )
            }
            is EngineState.Failed -> card("engine") {
                ListRow(t(R.string.music_download_engine_failed_to_start), summary = e.message, summaryColor = OneUi.colors.error)
            }
            else -> Unit
        }

        val playlist = s.playlist
        when {
            s.phase == Phase.Fetching -> card("fetching") {
                ListRow(
                    t(R.string.music_reading_link),
                    summary = s.link,
                    leading = { com.tools.app.ui.OneUiSpinner(Modifier.size(28.dp), color = OneUi.colors.accent, strokeWidth = 3.dp) },
                )
            }

            playlist == null -> Unit

            else -> {
                item(key = "playlist") { PlaylistCard(playlist, s) }
                if (s.phase == Phase.Finished) item(key = "summary") { SummaryCard(s, playlist) }

                val allSelected = s.selected.size == playlist.tracks.size
                // A single song is already shown in full by the card above.
                if (playlist.tracks.size > 1) sectionLabel(t(R.string.music_songs), key = "songs-label", trailing = if (s.phase == Phase.Ready && playlist.tracks.size > 1) {
                    {
                        Text(
                            if (allSelected) t(R.string.music_deselect_all) else t(R.string.music_select_all),
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

private val MUSIC_TIPS = listOf(
    com.tools.app.ui.Tip(t(R.string.music_share_from_youtube), t(R.string.music_tap_share_music_downloader_on_any_song)),
    com.tools.app.ui.Tip(t(R.string.music_mp3_with_album_art), t(R.string.music_square_cover_title_artist_album_and_tr)),
    com.tools.app.ui.Tip(t(R.string.music_saved_to_good_utilities_music), t(R.string.music_playlists_get_their_own_folder_ready_i)),
)

private fun subtitle(s: AppState): String = when (s.phase) {
    Phase.Idle -> t(R.string.home_youtube_songs_and_playlists_to_mp3)
    Phase.Fetching -> t(R.string.music_reading_link)
    Phase.Ready -> s.playlist?.let { if (it.tracks.size == 1) t(R.string.music_ready_to_download) else t(R.string.music_of_songs_selected, s.selected.size, it.tracks.size) } ?: ""
    Phase.Downloading -> t(R.string.music_downloading_of, minOf(s.finishedCount + 1, s.totalQueued), s.totalQueued)
    Phase.Finished -> s.count(Status.Done).let { n -> tp(R.plurals.music_songs_saved, n) }
}

@Composable
private fun LinkCard(s: AppState) {
    val c = OneUi.colors
    val context = LocalContext.current
    val focus = LocalFocusManager.current
    val busy = s.phase == Phase.Downloading
    RoundCard {
        Column(Modifier.padding(start = 20.dp, end = 14.dp, top = 18.dp, bottom = 6.dp)) {
            Text(t(R.string.music_song_or_playlist_link), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = c.text)
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
                            if (s.link.isEmpty()) Text(t(R.string.music_paste_a_youtube_link), fontSize = 16.sp, color = c.textSecondary.copy(alpha = 0.7f))
                            inner()
                        }
                    },
                )
                if (s.link.isNotEmpty() && !busy) ToolbarIcon(OneUiIcons.Close, t(R.string.about_clear)) { MusicDownloader.reset() }
                else if (!busy) ToolbarIcon(OneUiIcons.ContentPaste, t(R.string.music_paste)) {
                    val clip = context.getSystemService(ClipboardManager::class.java).primaryClip
                    val text = clip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()
                    if (!text.isNullOrBlank()) { focus.clearFocus(); MusicDownloader.fetch(text) }
                }
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
                    if (n == 1) t(R.string.music_song, playlist.tracks[0].durationSec?.let(::formatDuration) ?: "").removeSuffix(" · ")
                    else t(R.string.music_songs_2, n, formatTotal(playlist.totalSeconds)),
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
                s.totalQueued == 1 && s.count(Status.Done) == 1 -> t(R.string.music_song_saved)
                s.totalQueued == 1 && skipped == 1 -> t(R.string.music_already_downloaded)
                s.totalQueued == 1 -> t(R.string.music_couldn_t_download_this_song)
                else -> t(R.string.music_saved_of_songs, s.count(Status.Done), s.totalQueued)
            },
            summary = buildString {
                append(t(R.string.music_good_utilities_music))
                if (MusicDownloader.settings.current.subfolder && playlist.tracks.size > 1) append("/").append(playlist.title)
                if (skipped > 0 && s.totalQueued > 1) append(t(R.string.music_n_already_downloaded, skipped))
                if (failed > 0) append(if (s.totalQueued == 1) "\n" + (s.trackStates.values.firstOrNull()?.error ?: t(R.string.music_tap_retry_or_update_yt_dlp_in_settings)) else t(R.string.music_n_failed_tap_retry_or_update_yt_dlp_in, failed))
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
        st?.status == Status.Failed -> st.error ?: t(R.string.music_failed)
        st?.status == Status.Skipped -> t(R.string.music_already_downloaded)
        st?.status == Status.Converting -> t(R.string.music_converting_to_mp3)
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
        Status.Queued -> Icon(OneUiIcons.Schedule, t(R.string.music_waiting), tint = c.textSecondary.copy(alpha = 0.6f), modifier = m)
        Status.Downloading -> com.tools.app.ui.OneUiSpinner(
            progress = { st.progress }, modifier = m, color = c.accent, trackColor = c.divider, strokeWidth = 3.dp,
        )
        Status.Converting -> com.tools.app.ui.OneUiSpinner(m, color = c.accent, trackColor = c.divider, strokeWidth = 3.dp)
        Status.Done -> Icon(OneUiIcons.CheckCircle, t(R.string.music_saved), tint = c.accent, modifier = m)
        Status.Skipped -> Icon(OneUiIcons.CheckCircle, t(R.string.music_already_downloaded), tint = c.textSecondary, modifier = m)
        Status.Failed -> Icon(OneUiIcons.ErrorOutline, t(R.string.music_failed), tint = c.error, modifier = m)
    }
}

private fun bottomBar(s: AppState, onDownload: () -> Unit, onRetry: () -> Unit): (@Composable () -> Unit)? = when (s.phase) {
    Phase.Ready -> {
        {
            val n = s.selected.size
            PillButton(
                if (n == 1) t(R.string.music_download_song) else t(R.string.music_download_songs, n),
                onClick = onDownload, enabled = n > 0 && s.engine is EngineState.Ready,
                icon = OneUiIcons.Download, modifier = Modifier.fillMaxWidth(),
            )
        }
    }
    Phase.Downloading -> {
        { PillButton(t(R.string.ui_cancel), onClick = MusicDownloader::cancel, tonal = true, modifier = Modifier.fillMaxWidth()) }
    }
    Phase.Finished -> {
        {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (s.count(Status.Failed) > 0) PillButton(if (s.totalQueued == 1) t(R.string.music_retry) else t(R.string.music_retry_failed), onClick = onRetry, tonal = true, modifier = Modifier.weight(1f))
                PillButton(t(R.string.music_new_link), onClick = MusicDownloader::reset, modifier = Modifier.weight(1f))
            }
        }
    }
    else -> null
}
