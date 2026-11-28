package com.tools.app.compress

import com.tools.app.R
import com.tools.app.ui.t
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
import com.tools.app.ui.ToolSplit
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
    val mode = jobs.mode.intValue
    val isVideo = mode == 0
    val isPhoto = mode == 2
    val video by jobs.video
    val audio by jobs.audio
    val photos by jobs.photos
    val vWork by jobs.videoWork.collectAsState()
    val aWork by jobs.audioWork.collectAsState()
    val pWork by jobs.photoWork.collectAsState()
    val work = when (mode) { 0 -> vWork; 1 -> aWork; else -> pWork }
    val running = work is CWork.Running
    val loading by jobs.loading
    val pickError by jobs.pickError
    var pickingAudio by remember { mutableStateOf(false) }

    val videoPicker = rememberLauncherForActivityResult(com.tools.app.ui.GalleryPick()) { it?.let { u -> jobs.pickVideo(context, u) } }
    val photoPicker = rememberLauncherForActivityResult(com.tools.app.ui.GalleryPickMultiple()) { jobs.pickPhotos(context, it) }
    val choose = {
        when (mode) {
            0 -> videoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
            2 -> photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            else -> pickingAudio = true
        }
    }
    if (pickingAudio) AudioPicker(
        multiple = false,
        onPicked = { pickingAudio = false; it.firstOrNull()?.let { u -> jobs.pickAudio(context, u) } },
        onDismiss = { pickingAudio = false },
    )

    val hasFile = when (mode) { 0 -> video != null; 1 -> audio != null; else -> photos != null }
    val chooseLabel = t(when (mode) { 0 -> R.string.compress_choose_video; 1 -> R.string.compress_choose_audio; else -> R.string.compress_choose_photos })

    ToolPage(
        t(R.string.home_compressor), onBack,
        actions = { if (hasFile && !running) ToolbarIcon(if (mode == 1) OneUiIcons.Music else OneUiIcons.Gallery, chooseLabel, choose) },
    ) {
        ToolSplit(0.385f, top = { m ->
        // Top ~40%: the picked file.
        Column(
            m.padding(horizontal = 28.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when (mode) {
                0 -> video?.let { VideoHeader(it) }
                1 -> audio?.let { AudioHeader(it) }
                else -> photos?.let { PhotoHeader(it) }
            }
            if (!hasFile) {
                Text(
                    when (mode) { 0 -> t(R.string.compress_shrink_a_video); 1 -> t(R.string.compress_shrink_an_audio_file); else -> t(R.string.compress_shrink_photos) },
                    fontSize = 20.sp, fontWeight = FontWeight.Bold, color = c.text,
                )
                Text(
                    when (mode) {
                        0 -> t(R.string.compress_re_encode_with_the_phone_s_hardware_en)
                        1 -> t(R.string.compress_convert_to_mp3_aac_or_opus_at_a_lower)
                        else -> t(R.string.compress_smaller_photos_for_sites_with_upload_l)
                    },
                    fontSize = 14.sp, color = c.textSecondary, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp),
                )
                PillButton(
                    if (loading) t(R.string.audio_opening) else chooseLabel, onClick = choose, enabled = !loading,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
            pickError?.let { Text(it, fontSize = 14.sp, color = c.error, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp)) }
        }
        }, bottom = { m ->
        // Bottom ~60% (a little more, so the video options fit without scrolling): mode + options + action.
        Column(
            m
                .padding(horizontal = CardMargin, vertical = 6.dp)
                .fillMaxWidth()
                .clip(OneUi.CardShape)
                .background(c.card)
                .padding(18.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(Modifier.weight(1f)) {
                SegmentedControl(listOf(t(R.string.video_video), t(R.string.convert_audio), t(R.string.compress_photo)), jobs.mode.intValue, { if (!running) jobs.mode.intValue = it })
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    when (mode) {
                        0 -> VideoOptions(video, !running)
                        1 -> AudioOptions(audio, !running)
                        else -> PhotoOptions(photos, !running)
                    }
                }
            }
            Column {
                Status(work, onCancel = when (mode) { 0 -> jobs::cancelVideo; 1 -> jobs::cancelAudio; else -> jobs::cancelPhotos })
                PillButton(
                    if (running) t(R.string.compress_compressing) else t(R.string.compress_compress),
                    enabled = hasFile && !running,
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    onClick = { when (mode) { 0 -> jobs.startVideo(context); 1 -> jobs.startAudio(context); else -> jobs.startPhotos(context) } },
                )
            }
        }
        })
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
        t(R.string.compress_kbps, formatDuration((a.clip.durationMs / 1000).toInt()), (a.bitrate / 1000.0).roundToInt(), a.clip.ext.uppercase()),
        fontSize = 14.sp, color = c.textSecondary,
    )
}

@Composable
private fun VideoOptions(v: VideoSource?, enabled: Boolean) {
    val jobs = CompressJobs
    val c = OneUi.colors
    val short = v?.let { minOf(it.width, it.height) } ?: Int.MAX_VALUE
    OptionLabel(t(R.string.convert_resolution))
    ChipRow {
        ResolutionOptions.filter { (h, _) -> h == 0 || h < short }.forEach { (h, label) ->
            Chip(label, jobs.resolution.intValue == h, { if (enabled) { jobs.resolution.intValue = h; jobs.resetDone() } }, Modifier.weight(1f), horizontalPadding = 4.dp)
        }
    }
    OptionLabel(t(R.string.video_quality))
    ChipRow {
        QualityLabels.forEachIndexed { i, label ->
            Chip(label, jobs.quality.intValue == i, { if (enabled) { jobs.quality.intValue = i; jobs.resetDone() } }, Modifier.weight(1f), horizontalPadding = 4.dp)
        }
    }
    OptionLabel(t(R.string.compress_codec))
    ChipRow {
        Chip(t(R.string.compress_h_264_compatible), !jobs.hevc.value, { if (enabled) { jobs.hevc.value = false; jobs.resetDone() } }, Modifier.weight(1f), horizontalPadding = 4.dp)
        if (CompressJobs.hevcSupported) Chip(t(R.string.compress_hevc_smaller), jobs.hevc.value, { if (enabled) { jobs.hevc.value = true; jobs.resetDone() } }, Modifier.weight(1f), horizontalPadding = 4.dp)
    }
    SwitchRow(t(R.string.compress_remove_audio), jobs.removeAudio.value) { if (enabled) { jobs.removeAudio.value = it; jobs.resetDone() } }
    if (v != null) Estimate(jobs.estimateVideoBytes(v), v.sizeBytes)
    else Text(t(R.string.compress_choose_a_video_to_see_the_estimated_si), fontSize = 13.sp, color = c.textSecondary)
}

@Composable
private fun PhotoHeader(p: PhotoSource) {
    val c = OneUi.colors
    val first = p.items.first()
    Box(Modifier.height(130.dp).aspectRatio((first.width.toFloat() / first.height.coerceAtLeast(1)).coerceIn(0.6f, 1.9f)).clip(RoundedCornerShape(16.dp)).background(c.tonal)) {
        p.thumb?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
    }
    Text(
        if (p.items.size == 1) first.name else t(R.string.compress_photos, p.items.size),
        fontSize = 17.sp, fontWeight = FontWeight.Bold, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 10.dp),
    )
    Text(
        (if (p.items.size == 1) "${first.width}×${first.height} · " else "") + size(p.totalBytes),
        fontSize = 13.sp, color = c.textSecondary, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 2.dp),
    )
}

@Composable
private fun PhotoOptions(p: PhotoSource?, enabled: Boolean) {
    val jobs = CompressJobs
    val c = OneUi.colors
    OptionLabel(t(R.string.compress_longest_side_px))
    ChipRow {
        PhotoSizes.forEach { (px, label) ->
            Chip(label, jobs.photoSide.intValue == px, { if (enabled) { jobs.photoSide.intValue = px; jobs.resetDone() } }, Modifier.weight(1f), horizontalPadding = 4.dp)
        }
    }
    OptionLabel(t(R.string.video_quality))
    ChipRow {
        QualityLabels.forEachIndexed { i, label ->
            Chip(label, jobs.photoQuality.intValue == i, { if (enabled) { jobs.photoQuality.intValue = i; jobs.resetDone() } }, Modifier.weight(1f), horizontalPadding = 4.dp)
        }
    }
    OptionLabel(t(R.string.compress_max_file_size))
    ChipRow {
        PhotoLimits.forEach { (kb, label) ->
            Chip(label, jobs.photoLimit.intValue == kb, { if (enabled) { jobs.photoLimit.intValue = kb; jobs.resetDone() } }, Modifier.weight(1f), horizontalPadding = 4.dp)
        }
    }
    Text(
        if (p == null) t(R.string.compress_choose_photos_to_compress_saved_as_jpe)
        else t(R.string.compress_saved_as_jpeg_to_location_and_camera_d, Storage.label(Storage.Folder.Pictures, "Compressed")),
        fontSize = 13.sp, color = c.textSecondary,
    )
}

@Composable
private fun AudioOptions(a: AudioSource?, enabled: Boolean) {
    val jobs = CompressJobs
    val c = OneUi.colors
    OptionLabel(t(R.string.video_format))
    ChipRow {
        AudioFormat.entries.forEach { f ->
            Chip(f.label, jobs.format.value == f, {
                if (enabled) { jobs.format.value = f; jobs.kbps.intValue = f.defaultKbps; jobs.resetDone() }
            }, Modifier.weight(1f), horizontalPadding = 4.dp)
        }
    }
    OptionLabel(t(R.string.compress_bitrate))
    ChipRow {
        AudioBitrates.forEach { k -> Chip("$k", jobs.kbps.intValue == k, { if (enabled) { jobs.kbps.intValue = k; jobs.resetDone() } }, Modifier.weight(1f), horizontalPadding = 4.dp) }
        Text("kbps", fontSize = 13.sp, color = c.textSecondary, modifier = Modifier.align(Alignment.CenterVertically))
    }
    SwitchRow(t(R.string.compress_mono), jobs.mono.value) { if (enabled) { jobs.mono.value = it; jobs.resetDone() } }
    SwitchRow(if (jobs.format.value == AudioFormat.Opus) t(R.string.compress_keep_tags) else t(R.string.compress_keep_tags_and_cover_art), jobs.keepMeta.value) { if (enabled) { jobs.keepMeta.value = it; jobs.resetDone() } }
    if (a != null) Estimate(jobs.estimateAudioBytes(a), a.sizeBytes)
    else Text(t(R.string.compress_choose_a_file_to_see_the_estimated_siz), fontSize = 13.sp, color = c.textSecondary)
}

@Composable
private fun Estimate(est: Long, original: Long) {
    val c = OneUi.colors
    val bigger = original > 0 && est >= original
    Text(
        if (original > 0 && !bigger) t(R.string.compress_estimated_size_about_smaller, size(est), ((1 - est.toDouble() / original) * 100).roundToInt())
        else if (bigger) t(R.string.compress_estimated_size_that_isn_t_smaller_than, size(est))
        else t(R.string.compress_estimated_size, size(est)),
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
                    (work.note ?: t(R.string.compress_compressing)) + " ${(work.progress * 100).roundToInt()}%", fontSize = 14.sp, color = c.textSecondary,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    t(R.string.ui_cancel), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = c.accent,
                    modifier = Modifier.clip(CircleShape).clickable(onClick = onCancel).padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
            ThinProgress(work.progress, Modifier.padding(top = 8.dp))
        }
        is CWork.Done -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                val saved = if (work.before > 0) ((1 - work.after.toDouble() / work.before) * 100).roundToInt() else 0
                Text(
                    "${size(work.before)} → ${size(work.after)}" + if (saved > 0) t(R.string.compress_saved, saved) else "",
                    fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = c.success,
                )
                Text(t(R.string.ui_saved_to, Storage.label(work.folder, work.sub), work.fileName), fontSize = 12.sp, color = c.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            val uris = work.shareUris.ifEmpty { listOfNotNull(work.shareUri) }
            if (uris.isNotEmpty()) {
                Text(
                    t(R.string.video_share), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = c.accent,
                    modifier = Modifier.clip(CircleShape).clickable {
                        val send = if (uris.size == 1) Intent(Intent.ACTION_SEND).setType(work.mime).putExtra(Intent.EXTRA_STREAM, uris.first())
                        else Intent(Intent.ACTION_SEND_MULTIPLE).setType(work.mime).putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
                        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
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
