package com.tools.app.compress

import androidx.compose.foundation.layout.Spacer
import com.tools.app.ui.ColorIcon
import com.tools.app.ui.theme.IconColors
import androidx.compose.material.icons.rounded.Compress
import androidx.compose.material.icons.Icons
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tools.app.Storage
import com.tools.app.audio.AudioPicker
import com.tools.app.ui.CardMargin
import com.tools.app.ui.Chip
import com.tools.app.ui.OneUiIcons
import com.tools.app.ui.OneUiSwitch
import com.tools.app.ui.PillButton
import com.tools.app.ui.SegmentedControl
import com.tools.app.ui.ThinProgress
import com.tools.app.ui.ToolPage
import com.tools.app.ui.ToolbarIcon
import com.tools.app.ui.formatDuration
import com.tools.app.ui.theme.OneUi
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun CompressorScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val c = OneUi.colors
    val jobs = CompressJobs
    val isVideo = jobs.mode.intValue == 0
    val video by jobs.video
    val audio by jobs.audio
    val vWork by jobs.videoWork.collectAsState()
    val aWork by jobs.audioWork.collectAsState()
    val work = if (isVideo) vWork else aWork
    val running = work is CWork.Running
    val loading by jobs.loading
    val pickError by jobs.pickError
    var pickingAudio by remember { mutableStateOf(false) }

    val videoPicker = rememberLauncherForActivityResult(com.tools.app.ui.GalleryPick()) { it?.let { u -> jobs.pickVideo(context, u) } }
    val choose = {
        if (isVideo) videoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
        else pickingAudio = true
    }
    if (pickingAudio) AudioPicker(
        multiple = false,
        onPicked = { pickingAudio = false; it.firstOrNull()?.let { u -> jobs.pickAudio(context, u) } },
        onDismiss = { pickingAudio = false },
    )

    val hasFile = if (isVideo) video != null else audio != null

    ToolPage(
        "Compressor", onBack,
        actions = { if (hasFile && !running) ToolbarIcon(if (isVideo) OneUiIcons.Gallery else OneUiIcons.Music, if (isVideo) "Choose video" else "Choose audio", choose) },
    ) {
        // Top 40%: the picked file.
        Column(
            Modifier.weight(0.4f).fillMaxWidth().padding(horizontal = 28.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (isVideo) video?.let { VideoHeader(it) } else audio?.let { AudioHeader(it) }
            if (!hasFile) {
                ColorIcon(Icons.Rounded.Compress, IconColors.Blue, 84.dp, glyph = 0.5f)
                Spacer(Modifier.height(20.dp))
                Text(
                    if (isVideo) "Shrink a video" else "Shrink an audio file", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = c.text,
                )
                Text(
                    if (isVideo) "Re-encode with the phone's hardware encoder to make big videos much smaller." else "Convert to MP3, AAC or Opus at a lower bitrate.",
                    fontSize = 14.sp, color = c.textSecondary, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp),
                )
                PillButton(
                    if (loading) "Opening…" else if (isVideo) "Choose video" else "Choose audio", onClick = choose, enabled = !loading,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
            pickError?.let { Text(it, fontSize = 14.sp, color = c.error, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp)) }
        }

        // Bottom 60%: mode + options + action.
        Column(
            Modifier
                .weight(0.6f)
                .padding(horizontal = CardMargin, vertical = 6.dp)
                .fillMaxWidth()
                .clip(OneUi.CardShape)
                .background(c.card)
                .padding(18.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(Modifier.weight(1f)) {
                SegmentedControl(listOf("Video", "Audio"), jobs.mode.intValue, { if (!running) jobs.mode.intValue = it })
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (isVideo) VideoOptions(video, !running) else AudioOptions(audio, !running)
                }
            }
            Column {
                Status(work, onCancel = if (isVideo) jobs::cancelVideo else jobs::cancelAudio)
                PillButton(
                    if (running) "Compressing…" else "Compress",
                    enabled = hasFile && !running,
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    onClick = { if (isVideo) jobs.startVideo(context) else jobs.startAudio(context) },
                )
            }
        }
    }
}

@Composable
private fun VideoHeader(v: VideoSource) {
    val c = OneUi.colors
    val ratio = (v.width.toFloat() / v.height).coerceIn(0.6f, 1.9f)
    Box(Modifier.height(130.dp).aspectRatio(ratio).clip(RoundedCornerShape(16.dp)).background(c.tonal)) {
        v.thumb?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
    }
    Text(v.name, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 10.dp))
    Text(
        listOf(formatDuration((v.durationMs / 1000).toInt()), "${v.width}×${v.height}", size(v.sizeBytes), mbps(v.bitrate)).joinToString(" · "),
        fontSize = 13.sp, color = c.textSecondary, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 2.dp),
    )
}

private fun Modifier.fillMaxSize() = this.fillMaxWidth().height(130.dp)

@Composable
private fun AudioHeader(a: AudioSource) {
    val c = OneUi.colors
    Text(a.clip.name, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = c.text, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
    Text(size(a.sizeBytes), fontSize = 40.sp, fontWeight = FontWeight.Bold, color = c.accent, modifier = Modifier.padding(top = 8.dp))
    Text(
        "${formatDuration((a.clip.durationMs / 1000).toInt())} · ${(a.bitrate / 1000.0).roundToInt()} kbps · ${a.clip.ext.uppercase()}",
        fontSize = 14.sp, color = c.textSecondary,
    )
}

@Composable
private fun VideoOptions(v: VideoSource?, enabled: Boolean) {
    val jobs = CompressJobs
    val c = OneUi.colors
    val short = v?.let { minOf(it.width, it.height) } ?: Int.MAX_VALUE
    OptionLabel("Resolution")
    ChipRow {
        ResolutionOptions.filter { (h, _) -> h == 0 || h < short }.forEach { (h, label) ->
            Chip(label, jobs.resolution.intValue == h, { if (enabled) { jobs.resolution.intValue = h; jobs.resetDone() } }, Modifier.weight(1f), horizontalPadding = 4.dp)
        }
    }
    OptionLabel("Quality")
    ChipRow {
        QualityLabels.forEachIndexed { i, label ->
            Chip(label, jobs.quality.intValue == i, { if (enabled) { jobs.quality.intValue = i; jobs.resetDone() } }, Modifier.weight(1f), horizontalPadding = 4.dp)
        }
    }
    OptionLabel("Codec")
    ChipRow {
        Chip("H.264 · compatible", !jobs.hevc.value, { if (enabled) { jobs.hevc.value = false; jobs.resetDone() } }, Modifier.weight(1f), horizontalPadding = 4.dp)
        if (CompressJobs.hevcSupported) Chip("HEVC · smaller", jobs.hevc.value, { if (enabled) { jobs.hevc.value = true; jobs.resetDone() } }, Modifier.weight(1f), horizontalPadding = 4.dp)
    }
    SwitchRow("Remove audio", jobs.removeAudio.value) { if (enabled) { jobs.removeAudio.value = it; jobs.resetDone() } }
    if (v != null) Estimate(jobs.estimateVideoBytes(v), v.sizeBytes)
    else Text("Choose a video to see the estimated size.", fontSize = 13.sp, color = c.textSecondary)
}

@Composable
private fun AudioOptions(a: AudioSource?, enabled: Boolean) {
    val jobs = CompressJobs
    val c = OneUi.colors
    OptionLabel("Format")
    ChipRow {
        AudioFormat.entries.forEach { f ->
            Chip(f.label, jobs.format.value == f, {
                if (enabled) { jobs.format.value = f; jobs.kbps.intValue = f.defaultKbps; jobs.resetDone() }
            }, Modifier.weight(1f), horizontalPadding = 4.dp)
        }
    }
    OptionLabel("Bitrate")
    ChipRow {
        AudioBitrates.forEach { k -> Chip("$k", jobs.kbps.intValue == k, { if (enabled) { jobs.kbps.intValue = k; jobs.resetDone() } }, Modifier.weight(1f), horizontalPadding = 4.dp) }
        Text("kbps", fontSize = 13.sp, color = c.textSecondary, modifier = Modifier.align(Alignment.CenterVertically))
    }
    SwitchRow("Mono", jobs.mono.value) { if (enabled) { jobs.mono.value = it; jobs.resetDone() } }
    SwitchRow(if (jobs.format.value == AudioFormat.Opus) "Keep tags" else "Keep tags and cover art", jobs.keepMeta.value) { if (enabled) { jobs.keepMeta.value = it; jobs.resetDone() } }
    if (a != null) Estimate(jobs.estimateAudioBytes(a), a.sizeBytes)
    else Text("Choose a file to see the estimated size.", fontSize = 13.sp, color = c.textSecondary)
}

@Composable
private fun Estimate(est: Long, original: Long) {
    val c = OneUi.colors
    val bigger = original > 0 && est >= original
    Text(
        if (original > 0 && !bigger) "Estimated size: ≈ ${size(est)} (about ${((1 - est.toDouble() / original) * 100).roundToInt()}% smaller)"
        else if (bigger) "Estimated size: ≈ ${size(est)}. That isn't smaller than the original; pick a lower setting."
        else "Estimated size: ≈ ${size(est)}",
        fontSize = 13.sp, color = if (bigger) c.error else c.textSecondary,
    )
}

@Composable
private fun OptionLabel(text: String) = Text(text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = OneUi.colors.textSecondary, modifier = Modifier.padding(bottom = 0.dp))

@Composable
private fun ChipRow(content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) =
    Row(Modifier.fillMaxWidth().padding(top = 0.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), content = content)

@Composable
private fun SwitchRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onChange(!checked) }.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = OneUi.colors.text, modifier = Modifier.weight(1f))
        OneUiSwitch(checked, onCheckedChange = onChange)
    }
}

@Composable
private fun Status(work: CWork, onCancel: () -> Unit) {
    val c = OneUi.colors
    val context = LocalContext.current
    when (work) {
        is CWork.Running -> Column(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    (work.note ?: "Compressing…") + " ${(work.progress * 100).roundToInt()}%", fontSize = 14.sp, color = c.textSecondary,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "Cancel", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = c.accent,
                    modifier = Modifier.clip(CircleShape).clickable(onClick = onCancel).padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
            ThinProgress(work.progress, Modifier.padding(top = 8.dp))
        }
        is CWork.Done -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                val saved = if (work.before > 0) ((1 - work.after.toDouble() / work.before) * 100).roundToInt() else 0
                Text(
                    "${size(work.before)} → ${size(work.after)}" + if (saved > 0) " · saved $saved%" else "",
                    fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = c.success,
                )
                Text("Saved to ${Storage.label(work.folder)} · ${work.fileName}", fontSize = 12.sp, color = c.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            work.shareUri?.let { uri ->
                Text(
                    "Share", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = c.accent,
                    modifier = Modifier.clip(CircleShape).clickable {
                        val send = Intent(Intent.ACTION_SEND).setType(work.mime).putExtra(Intent.EXTRA_STREAM, uri)
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }.padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
        is CWork.Failed -> Text(work.message, fontSize = 14.sp, color = c.error)
        CWork.Idle -> {}
    }
}

private fun size(bytes: Long): String = when {
    bytes <= 0 -> "—"
    bytes >= 1_000_000_000 -> String.format(Locale.US, "%.2f GB", bytes / 1e9)
    bytes >= 1_000_000 -> String.format(Locale.US, "%.1f MB", bytes / 1e6)
    else -> "${(bytes / 1000.0).roundToInt()} KB"
}

private fun mbps(bps: Long): String = if (bps <= 0) "—" else String.format(Locale.US, "%.1f Mbps", bps / 1e6)
