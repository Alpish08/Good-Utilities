package com.tools.app.audio

import com.tools.app.ui.theme.IconColors
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.Icons
import android.media.MediaPlayer
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tools.app.ui.CardMargin
import com.tools.app.ui.EmptyState
import com.tools.app.ui.JobStatus
import com.tools.app.ui.OneUiIcons
import com.tools.app.ui.OneUiRangeSlider
import com.tools.app.ui.PillButton
import com.tools.app.ui.ToolPage
import com.tools.app.ui.ToolSplit
import com.tools.app.ui.ToolbarIcon
import com.tools.app.ui.theme.OneUi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun TrimScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val c = OneUi.colors
    val clip by AudioJobs.trimClip
    var start by AudioJobs.trimStart
    var end by AudioJobs.trimEnd
    val work by AudioJobs.trim.collectAsState()
    var loading by remember { mutableStateOf(false) }
    var playing by remember { mutableStateOf(false) }
    var playhead by remember { mutableFloatStateOf(-1f) }

    val player = remember(clip) { clip?.let { runCatching { MediaPlayer().apply { setDataSource(it.file.absolutePath); prepare() } }.getOrNull() } }
    DisposableEffect(player) { onDispose { player?.release(); playing = false; playhead = -1f } }

    var picking by remember { mutableStateOf(false) }
    fun open(uri: android.net.Uri) {
        loading = true
        AudioJobs.resetTrim()
        scope.launch {
            runCatching { Ffmpeg.import(context, uri, "trim${System.nanoTime()}") }
                .onSuccess { picked -> AudioJobs.trimClip.value?.file?.delete(); AudioJobs.trimClip.value = picked; start = 0; end = picked.durationMs }
                .onFailure { Toast.makeText(context, it.message ?: "Couldn't open that file", Toast.LENGTH_SHORT).show() }
            loading = false
        }
    }
    if (picking) AudioPicker(multiple = false, onPicked = { picking = false; it.firstOrNull()?.let(::open) }, onDismiss = { picking = false })
    val choose = { picking = true }
    val running = work is Work.Running

    // Preview: plays the selection and stops at its end.
    LaunchedEffect(playing, player) {
        val p = player ?: return@LaunchedEffect
        if (!playing) { if (p.isPlaying) p.pause(); return@LaunchedEffect }
        p.seekTo(start, MediaPlayer.SEEK_CLOSEST)
        p.start()
        while (playing) {
            val pos = p.currentPosition.toLong()
            playhead = pos / clip!!.durationMs.toFloat()
            if (pos >= end || !p.isPlaying) { playing = false; break }
            delay(50)
        }
        if (p.isPlaying) p.pause()
        playhead = -1f
    }

    ToolPage("Audio trimmer", onBack, expandable = clip != null, actions = { if (clip != null && !running) ToolbarIcon(OneUiIcons.Music, "Choose audio", choose) }) {
        val cl = clip
        if (cl == null) {
            EmptyState(
                Icons.Rounded.ContentCut, IconColors.Purple, "Trim a song",
                "Cut the start and end off any audio file. Nothing is re-encoded, so quality stays exactly the same.",
                if (loading) "Opening…" else "Choose audio", choose,
            )
            return@ToolPage
        }
        val total = cl.durationMs.toFloat()

        ToolSplit(0.4f, top = { m ->
        // Top 40%: what you are cutting.
        Column(
            m.padding(horizontal = 28.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(cl.name, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = c.text, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
            Text(
                clock(end - start), fontSize = 44.sp, fontWeight = FontWeight.Bold, color = c.accent,
                modifier = Modifier.padding(top = 10.dp),
            )
            Text("of ${clock(cl.durationMs)}", fontSize = 15.sp, color = c.textSecondary)
        }
        }, bottom = { m ->
        // Bottom 60%: the controls card.
        Column(
            m
                .padding(horizontal = CardMargin, vertical = 6.dp)
                .fillMaxWidth()
                .clip(OneUi.CardShape)
                .background(c.card)
                .padding(18.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Row(Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        Text("Start", fontSize = 13.sp, color = c.textSecondary)
                        Text(clock(start), fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = c.text)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("End", fontSize = 13.sp, color = c.textSecondary)
                        Text(clock(end), fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = c.text)
                    }
                }
                OneUiRangeSlider(
                    start / total, end / total,
                    onChange = { s, e ->
                        if (running) return@OneUiRangeSlider
                        playing = false
                        start = (s * total).toLong(); end = (e * total).toLong()
                        AudioJobs.resetTrim()
                    },
                    minGap = (500f / total).coerceAtMost(0.5f),
                    marker = playhead.takeIf { it >= 0f },
                    modifier = Modifier.padding(top = 10.dp),
                )
                Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Nudge("−0.1 s") { start = (start - 100).coerceAtLeast(0) }
                    Nudge("+0.1 s") { start = (start + 100).coerceAtMost(end - 500) }
                    Box(Modifier.weight(1f))
                    Nudge("−0.1 s") { end = (end - 100).coerceAtLeast(start + 500) }
                    Nudge("+0.1 s") { end = (end + 100).coerceAtMost(cl.durationMs) }
                }
                Box(Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) {
                    Box(
                        Modifier.size(56.dp).clip(CircleShape).background(c.tonal)
                            .then(Modifier.clickableNoRipple(player != null) { playing = !playing }),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(if (playing) OneUiIcons.Pause else OneUiIcons.PlayArrow, if (playing) "Pause" else "Play selection", tint = c.text, modifier = Modifier.size(30.dp))
                    }
                }
            }
            Column {
                JobStatus(
                    running = (work as? Work.Running)?.progress,
                    done = (work as? Work.Done)?.fileName,
                    error = (work as? Work.Failed)?.message,
                    onCancel = AudioJobs::cancelTrim,
                )
                PillButton(
                    if (running) "Saving…" else "Save trimmed copy",
                    enabled = !running && (start > 0 || end < cl.durationMs),
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    onClick = { playing = false; AudioJobs.startTrim(context) },
                )
            }
        }
        })
    }
}

@Composable
private fun Nudge(label: String, onClick: () -> Unit) {
    Text(
        label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = OneUi.colors.accent,
        modifier = Modifier.clip(CircleShape).clickableNoRipple(true, onClick).padding(horizontal = 8.dp, vertical = 6.dp),
    )
}

private fun Modifier.clickableNoRipple(enabled: Boolean, onClick: () -> Unit) =
    this.clickable(enabled = enabled, onClick = onClick)

private fun clock(ms: Long): String {
    val tenths = (ms / 100) % 10
    val s = ms / 1000
    return if (s >= 3600) "%d:%02d:%02d.%d".format(s / 3600, (s % 3600) / 60, s % 60, tenths)
    else "%d:%02d.%d".format(s / 60, s % 60, tenths)
}
