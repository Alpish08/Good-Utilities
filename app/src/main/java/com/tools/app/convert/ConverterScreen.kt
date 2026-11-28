package com.tools.app.convert

import com.tools.app.ui.tp
import com.tools.app.R
import com.tools.app.ui.t
import androidx.compose.material.icons.rounded.SwapHoriz
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.text.format.Formatter
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.tools.app.Storage
import com.tools.app.ui.CardDivider
import com.tools.app.ui.Chip
import com.tools.app.ui.ColorIcon
import com.tools.app.ui.ListRow
import com.tools.app.ui.OneUiIcons
import com.tools.app.ui.OneUiScaffold
import com.tools.app.ui.OneUiSlider
import com.tools.app.ui.OneUiSwitch
import com.tools.app.ui.PillButton
import com.tools.app.ui.RadioDot
import com.tools.app.ui.ThinProgress
import com.tools.app.ui.card
import com.tools.app.ui.cardList
import com.tools.app.ui.description
import com.tools.app.ui.sectionLabel
import com.tools.app.ui.theme.IconColors
import com.tools.app.ui.theme.OneUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

private fun Kind.icon(): ImageVector = when (this) {
    Kind.Image -> OneUiIcons.Gallery
    Kind.Audio -> OneUiIcons.MusicNote
    Kind.Video -> OneUiIcons.PlayArrow
    Kind.Pdf -> Icons.Outlined.PictureAsPdf
    Kind.Docx, Kind.Text -> Icons.Outlined.Description
}

private fun Kind.color() = when (this) {
    Kind.Image -> IconColors.Green
    Kind.Audio -> IconColors.Pink
    Kind.Video -> IconColors.Purple
    Kind.Pdf -> IconColors.Red
    Kind.Docx -> IconColors.Blue
    Kind.Text -> IconColors.Teal
}

private fun Context.lifecycleOwner(): LifecycleOwner? {
    var c: Context? = this
    while (c is ContextWrapper) { if (c is LifecycleOwner) return c; c = c.baseContext }
    return null
}

@Composable
fun ConverterScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val c = OneUi.colors
    val jobs = ConvertJobs
    val picker = rememberLauncherForActivityResult(com.tools.app.ui.FilesPickMultiple()) { uris ->
        if (uris.isNotEmpty()) jobs.add(context, uris)
    }
    // Re-check All files access when coming back from Settings.
    var tick by remember { mutableIntStateOf(0) }
    DisposableEffect(context) {
        val owner = context.lifecycleOwner()
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) tick++ }
        owner?.lifecycle?.addObserver(obs)
        onDispose { owner?.lifecycle?.removeObserver(obs) }
    }
    @Suppress("UNUSED_EXPRESSION") tick
    val hasAccess = Storage.hasAccess()

    val items = jobs.items.value
    val running = jobs.running.value
    val inRun = items.isNotEmpty()

    OneUiScaffold(
        title = t(R.string.home_file_converter),
        onBack = onBack,
        actions = { com.tools.app.ui.TipsMenu(CONVERT_TIPS) },
        bottomBar = {
            when {
                running -> PillButton(t(R.string.ui_cancel), onClick = { jobs.cancel() }, tonal = true, modifier = Modifier.fillMaxWidth())
                inRun -> PillButton(t(R.string.convert_convert_more_files), onClick = { jobs.reset() }, modifier = Modifier.fillMaxWidth())
                jobs.sources.isEmpty() -> PillButton(t(R.string.convert_choose_files), onClick = { picker.launch(arrayOf("*/*")) }, icon = OneUiIcons.Add, modifier = Modifier.fillMaxWidth())
                else -> {
                    val n = jobs.sources.size
                    PillButton(
                        if (n == 1) t(R.string.convert_convert) else t(R.string.convert_convert_files, n),
                        onClick = { jobs.start(context) },
                        enabled = jobs.target.value != null && hasAccess,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
    ) {
        if (inRun) runPage(items, running) else setupPage(hasAccess) { picker.launch(arrayOf("*/*")) }
    }
}

// ---- Setup ----------------------------------------------------------------------------------

private fun androidx.compose.foundation.lazy.LazyListScope.setupPage(hasAccess: Boolean, pick: () -> Unit) {
    val jobs = ConvertJobs
    val sources = jobs.sources.toList()
    val target = jobs.target.value
    val kind = jobs.kind

    if (!hasAccess) {
        card("access") {
            val context = LocalContext.current
            ListRow(
                t(R.string.convert_allow_access_to_storage), summary = t(R.string.convert_converted_files_are_saved_in_internal, Storage.ROOT),
                onClick = { context.startActivity(Storage.accessIntent(context)) },
            )
        }
    }

    jobs.notice.value?.let { msg ->
        item(key = "notice") {
            Text(msg, fontSize = 14.sp, lineHeight = 20.sp, color = OneUi.colors.error, modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp))
        }
    }

    if (sources.isEmpty()) {
        sectionLabel(t(R.string.home_files))
        card("choose") {
            ListRow(
                t(R.string.convert_choose_files), summary = t(R.string.convert_pick_one_or_more_files_of_the_same_typ),
                onClick = pick,
            )
        }
        return
    }

    sectionLabel(tp(R.plurals.convert_n_files_kind, sources.size, sources.size, kind?.label.orEmpty()))
    cardList(sources, key = { "src-${it.id}" }) { s ->
        val context = LocalContext.current
        ListRow(
            s.name,
            summary = (if (s.ext.isEmpty()) s.kind.label else t(R.string.convert_ext_kind, s.ext.uppercase(), s.kind.label.lowercase())) +
                if (s.size > 0) " · ${Formatter.formatShortFileSize(context, s.size)}" else "",
            trailing = {
                Box(Modifier.size(36.dp).clip(CircleShape).clickable { jobs.remove(s) }, contentAlignment = Alignment.Center) {
                    Icon(OneUiIcons.Close, t(R.string.audio_remove), tint = OneUi.colors.textSecondary, modifier = Modifier.size(20.dp))
                }
            },
        )
    }
    card("add") { ListRow(t(R.string.convert_add_more_files), onClick = pick) }

    val choices = jobs.choices
    sectionLabel(t(R.string.convert_convert_to))
    card("choices") {
        choices.forEachIndexed { i, ch ->
            ListRow(ch.title, summary = ch.summary, trailing = { RadioDot(ch.fmt == target) }, onClick = { jobs.target.value = ch.fmt })
            if (i != choices.lastIndex) CardDivider()
        }
    }

    if (target != null) {
        val showQuality = (target.lossyImage && (kind == Kind.Image || kind == Kind.Pdf)) || (target.lossyAudio && (kind == Kind.Audio || kind == Kind.Video))
        val showResolution = kind == Kind.Video && (target == Fmt.Mp4 || target == Fmt.WebM)
        val showGif = target == Fmt.Gif
        val showCombine = kind == Kind.Image && target == Fmt.Pdf && sources.size > 1
        if (showQuality || showResolution || showGif || showCombine) {
            sectionLabel(t(R.string.convert_options))
            card("options") {
                val c = OneUi.colors
                if (showQuality) {
                    val imageQ = kind == Kind.Image || kind == Kind.Pdf
                    val state = if (imageQ) jobs.imageQuality else jobs.audioQuality
                    Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(t(R.string.video_quality), fontSize = 15.5.sp, fontWeight = FontWeight.SemiBold, color = c.text, modifier = Modifier.weight(1f))
                            Text(qualityLabel(target, state.value.roundToInt(), imageQ), fontSize = 13.sp, color = c.accent)
                        }
                        OneUiSlider(
                            state.value, { state.value = it.roundToInt().toFloat() }, valueRange = 10f..100f,
                            modifier = Modifier.padding(top = 6.dp).fillMaxWidth(),
                        )
                    }
                }
                if (showQuality && (showResolution || showGif || showCombine)) CardDivider()
                if (showResolution) {
                    Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
                        Text(t(R.string.convert_resolution), fontSize = 15.5.sp, fontWeight = FontWeight.SemiBold, color = c.text)
                        Row(Modifier.padding(top = 10.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(0 to t(R.string.convert_original), 1080 to "1080p", 720 to "720p", 480 to "480p").forEach { (h, label) ->
                                Chip(label, jobs.videoHeight.value == h, { jobs.videoHeight.value = h })
                            }
                        }
                        Text(t(R.string.convert_smaller_sizes_are_never_enlarged), fontSize = 13.sp, color = c.textSecondary, modifier = Modifier.padding(top = 8.dp))
                    }
                }
                if (showGif) {
                    Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
                        Text(t(R.string.convert_gif_width), fontSize = 15.5.sp, fontWeight = FontWeight.SemiBold, color = c.text)
                        Row(Modifier.padding(top = 10.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(320, 480, 640).forEach { w -> Chip("$w px", jobs.gifWidth.value == w, { jobs.gifWidth.value = w }) }
                        }
                        Text(t(R.string.convert_12_frames_per_second), fontSize = 13.sp, color = c.textSecondary, modifier = Modifier.padding(top = 8.dp))
                    }
                }
                if (showCombine) {
                    ListRow(
                        t(R.string.convert_combine_into_one_pdf), summary = t(R.string.convert_one_page_per_image_in_the_order_shown),
                        trailing = { OneUiSwitch(jobs.combinePdf.value) },
                        onClick = { jobs.combinePdf.value = !jobs.combinePdf.value },
                    )
                }
            }
        }
        note(kind, target, sources)?.let { description(it, key = "note") }
    }
}

private fun qualityLabel(fmt: Fmt, q: Int, image: Boolean): String = when {
    image -> if (fmt == Fmt.Webp && q >= 100) t(R.string.convert_lossless, q) else "$q%"
    fmt == Fmt.Mp3 -> "$q% · VBR"
    fmt == Fmt.M4a -> "$q% · ${(64 + q * 1.7).roundToInt()} kbps"
    fmt == Fmt.Opus -> "$q% · ${(48 + q * 1.07).roundToInt()} kbps"
    else -> "$q%"
}

/** Honest limits for the chosen conversion. */
private fun note(kind: Kind?, fmt: Fmt, sources: List<Source>): String? = when {
    kind == Kind.Docx && fmt == Fmt.Pdf ->
        t(R.string.convert_this_is_a_text_and_basic_formatting_co)
    kind == Kind.Docx -> t(R.string.convert_pictures_and_complex_layout_are_left_o)
    kind == Kind.Text && fmt == Fmt.Pdf -> t(R.string.convert_markdown_is_lightly_formatted_headings)
    kind == Kind.Pdf && fmt == Fmt.Txt -> t(R.string.convert_only_selectable_text_is_extracted_scan)
    kind == Kind.Pdf -> t(R.string.convert_each_page_becomes_a_picture_at_about_1)
    kind == Kind.Image && fmt == Fmt.Jpg -> t(R.string.convert_jpg_has_no_transparency_so_transparent)
    kind == Kind.Image && fmt == Fmt.Pdf -> t(R.string.convert_images_are_fitted_onto_a4_pages_and_re)
    kind == Kind.Image -> t(R.string.convert_gifs_convert_their_first_frame_only_ph)
    kind == Kind.Video && fmt == Fmt.WebM -> t(R.string.convert_webm_vp9_is_slow_to_encode_often_sever)
    kind == Kind.Video && fmt == Fmt.Mp4 -> t(R.string.convert_uses_the_phone_s_hardware_encoder_when)
    kind == Kind.Video && fmt == Fmt.Gif -> t(R.string.convert_gifs_are_large_and_limited_to_the_firs)
    kind == Kind.Video -> t(R.string.convert_extracts_the_sound_track_videos_withou)
    fmt == Fmt.Wav || fmt == Fmt.Flac -> t(R.string.convert_lossless_output_is_much_larger_than_mp)
    kind == Kind.Audio && fmt.lossyAudio && sources.any { it.ext in setOf("flac", "wav", "alac") } ->
        t(R.string.convert_converting_lossless_audio_to_a_lossy_f)
    kind == Kind.Audio -> t(R.string.convert_tags_and_cover_art_are_kept_where_the)
    else -> null
}

// ---- Running / results ----------------------------------------------------------------------

private fun androidx.compose.foundation.lazy.LazyListScope.runPage(items: List<Item>, running: Boolean) {
    fun progressOf(i: Item) = when (val s = i.state) {
        is ItemState.Running -> s.progress
        is ItemState.Queued -> 0f
        else -> 1f
    }
    val overall = items.sumOf { progressOf(it).toDouble() }.toFloat() / items.size
    val done = items.count { it.state is ItemState.Done }
    val failed = items.count { it.state is ItemState.Failed }
    val cancelled = items.count { it.state is ItemState.Cancelled }

    card("overall") {
        val c = OneUi.colors
        Column(Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
            val title = when {
                running -> t(R.string.convert_converting_of, (done + failed + 1).coerceAtMost(items.size), items.size)
                cancelled > 0 -> t(R.string.convert_cancelled)
                failed == 0 -> t(R.string.convert_all_done)
                done == 0 -> t(R.string.convert_nothing_could_be_converted)
                else -> t(R.string.convert_finished_with_problems)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = c.text, modifier = Modifier.weight(1f))
                if (running) Text("${(overall * 100).roundToInt()}%", fontSize = 14.sp, color = c.accent)
            }
            if (running) ThinProgress(overall, Modifier.padding(top = 10.dp))
            else Text(
                buildString {
                    append(t(R.string.convert_converted, done))
                    if (failed > 0) append(t(R.string.music_failed_2, failed))
                    if (cancelled > 0) append(t(R.string.convert_not_done, cancelled))
                },
                fontSize = 13.sp, color = c.textSecondary, modifier = Modifier.padding(top = 2.dp),
            )
            if (running) Text(t(R.string.convert_you_can_leave_this_page_conversion_con), fontSize = 13.sp, color = c.textSecondary, modifier = Modifier.padding(top = 10.dp))
        }
    }

    sectionLabel(if (items.size == 1) t(R.string.convert_file) else t(R.string.home_files))
    cardList(items, key = { "item-${it.id}" }) { item -> ResultRow(item) }
}

@Composable
private fun ResultRow(item: Item) {
    val c = OneUi.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state = item.state
    Column {
        ListRow(
            item.title,
            summary = when (state) {
                is ItemState.Queued -> t(R.string.music_waiting)
                is ItemState.Running -> "${state.step}… ${(state.progress * 100).roundToInt()}%"
                is ItemState.Done -> t(R.string.video_saved_to, state.output.folderLabel) + if (state.output.files.size > 1) t(R.string.convert_files, state.output.files.size) else ""
                is ItemState.Failed -> state.message
                is ItemState.Cancelled -> t(R.string.convert_cancelled)
            },
            summaryColor = when (state) {
                is ItemState.Done -> c.success
                is ItemState.Failed -> c.error
                else -> null
            },
            trailing = if (state is ItemState.Done) ({
                Row {
                    RoundAction(OneUiIcons.Share, t(R.string.video_share)) { scope.launch { share(context, state.output) } }
                    RoundAction(OneUiIcons.OpenExternal, t(R.string.video_open)) { scope.launch { open(context, state.output) } }
                }
            }) else null,
        )
        if (state is ItemState.Running) ThinProgress(state.progress, Modifier.padding(start = 62.dp, end = 18.dp, bottom = 14.dp))
    }
}

@Composable
private fun RoundAction(icon: ImageVector, description: String, onClick: () -> Unit) {
    Box(Modifier.size(40.dp).clip(CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, description, tint = OneUi.colors.accent, modifier = Modifier.size(22.dp))
    }
}

/** The app's FileProvider only exposes the cache, so saved files are handed out as short-lived copies. */
private suspend fun shareable(context: Context, output: Output): List<android.net.Uri> = withContext(Dispatchers.IO) {
    val dir = File(context.cacheDir, "qr/convert").apply { deleteRecursively(); mkdirs() }
    output.files.filter { it.exists() }.map { f ->
        val copy = File(dir, f.name)
        f.copyTo(copy, overwrite = true)
        FileProvider.getUriForFile(context, "${context.packageName}.files", copy)
    }
}

private suspend fun share(context: Context, output: Output) {
    val uris = runCatching { shareable(context, output) }.getOrDefault(emptyList())
    if (uris.isEmpty()) { Toast.makeText(context, t(R.string.convert_the_saved_file_is_no_longer_there), Toast.LENGTH_SHORT).show(); return }
    val intent = if (uris.size == 1) Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris[0])
    else Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
    intent.setType(output.mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(intent, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

private suspend fun open(context: Context, output: Output) {
    val uris = runCatching { shareable(context, output.copy(files = output.files.take(1))) }.getOrDefault(emptyList())
    if (uris.isEmpty()) { Toast.makeText(context, t(R.string.convert_the_saved_file_is_no_longer_there), Toast.LENGTH_SHORT).show(); return }
    val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uris[0], output.mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    try { context.startActivity(intent) } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, t(R.string.convert_no_app_can_open_this_file_try_share_in), Toast.LENGTH_SHORT).show()
    }
}

/** What can be converted, for the ⋮ › Tips popup. */
private val CONVERT_TIPS = listOf(
    com.tools.app.ui.Tip(t(R.string.home_images), t(R.string.convert_jpg_png_webp_heic_gif_bmp_to_jpg_png_w)),
    com.tools.app.ui.Tip("PDF", t(R.string.convert_to_jpg_or_png_pages_or_text)),
    com.tools.app.ui.Tip(t(R.string.convert_word_docx), t(R.string.convert_to_pdf_or_text_text_and_basic_formatti)),
    com.tools.app.ui.Tip(t(R.string.convert_txt_and_markdown), t(R.string.convert_to_pdf)),
    com.tools.app.ui.Tip(t(R.string.convert_audio), t(R.string.convert_mp3_wav_m4a_flac_ogg_opus_amr_to_mp3_w)),
    com.tools.app.ui.Tip(t(R.string.video_video), t(R.string.convert_mp4_mkv_mov_webm_3gp_avi_to_mp4_webm_g)),
)
