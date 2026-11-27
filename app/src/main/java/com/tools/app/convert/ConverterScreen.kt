package com.tools.app.convert

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
        title = "File converter",
        onBack = onBack,
        actions = { com.tools.app.ui.TipsMenu(CONVERT_TIPS) },
        bottomBar = {
            when {
                running -> PillButton("Cancel", onClick = { jobs.cancel() }, tonal = true, modifier = Modifier.fillMaxWidth())
                inRun -> PillButton("Convert more files", onClick = { jobs.reset() }, modifier = Modifier.fillMaxWidth())
                jobs.sources.isEmpty() -> PillButton("Choose files", onClick = { picker.launch(arrayOf("*/*")) }, icon = OneUiIcons.Add, modifier = Modifier.fillMaxWidth())
                else -> {
                    val n = jobs.sources.size
                    PillButton(
                        if (n == 1) "Convert" else "Convert $n files",
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
                "Allow access to storage", summary = "Converted files are saved in Internal Storage/${Storage.ROOT}. Tap to allow All files access.",
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
        sectionLabel("Files")
        card("choose") {
            ListRow(
                "Choose files", summary = "Pick one or more files of the same type",
                onClick = pick,
            )
        }
        return
    }

    sectionLabel("${sources.size} ${if (sources.size == 1) "file" else "files"} · ${kind?.label.orEmpty()}")
    cardList(sources, key = { "src-${it.id}" }) { s ->
        val context = LocalContext.current
        ListRow(
            s.name,
            summary = (if (s.ext.isEmpty()) s.kind.label else "${s.ext.uppercase()} ${s.kind.label.lowercase()}") +
                if (s.size > 0) " · ${Formatter.formatShortFileSize(context, s.size)}" else "",
            trailing = {
                Box(Modifier.size(36.dp).clip(CircleShape).clickable { jobs.remove(s) }, contentAlignment = Alignment.Center) {
                    Icon(OneUiIcons.Close, "Remove", tint = OneUi.colors.textSecondary, modifier = Modifier.size(20.dp))
                }
            },
        )
    }
    card("add") { ListRow("Add more files", onClick = pick) }

    val choices = jobs.choices
    sectionLabel("Convert to")
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
            sectionLabel("Options")
            card("options") {
                val c = OneUi.colors
                if (showQuality) {
                    val imageQ = kind == Kind.Image || kind == Kind.Pdf
                    val state = if (imageQ) jobs.imageQuality else jobs.audioQuality
                    Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Quality", fontSize = 15.5.sp, fontWeight = FontWeight.SemiBold, color = c.text, modifier = Modifier.weight(1f))
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
                        Text("Resolution", fontSize = 15.5.sp, fontWeight = FontWeight.SemiBold, color = c.text)
                        Row(Modifier.padding(top = 10.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(0 to "Original", 1080 to "1080p", 720 to "720p", 480 to "480p").forEach { (h, label) ->
                                Chip(label, jobs.videoHeight.value == h, { jobs.videoHeight.value = h })
                            }
                        }
                        Text("Smaller sizes are never enlarged", fontSize = 13.sp, color = c.textSecondary, modifier = Modifier.padding(top = 8.dp))
                    }
                }
                if (showGif) {
                    Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
                        Text("GIF width", fontSize = 15.5.sp, fontWeight = FontWeight.SemiBold, color = c.text)
                        Row(Modifier.padding(top = 10.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(320, 480, 640).forEach { w -> Chip("$w px", jobs.gifWidth.value == w, { jobs.gifWidth.value = w }) }
                        }
                        Text("12 frames per second", fontSize = 13.sp, color = c.textSecondary, modifier = Modifier.padding(top = 8.dp))
                    }
                }
                if (showCombine) {
                    ListRow(
                        "Combine into one PDF", summary = "One page per image, in the order shown",
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
    image -> if (fmt == Fmt.Webp && q >= 100) "$q% · lossless" else "$q%"
    fmt == Fmt.Mp3 -> "$q% · VBR"
    fmt == Fmt.M4a -> "$q% · ${(64 + q * 1.7).roundToInt()} kbps"
    fmt == Fmt.Opus -> "$q% · ${(48 + q * 1.07).roundToInt()} kbps"
    else -> "$q%"
}

/** Honest limits for the chosen conversion. */
private fun note(kind: Kind?, fmt: Fmt, sources: List<Source>): String? = when {
    kind == Kind.Docx && fmt == Fmt.Pdf ->
        "This is a text-and-basic-formatting conversion: paragraphs, headings, bold and italic, simple bullets and tables. Pictures, text boxes, headers, footers and complex layout are left out."
    kind == Kind.Docx -> "Pictures and complex layout are left out. Tables become tab-separated rows."
    kind == Kind.Text && fmt == Fmt.Pdf -> "Markdown is lightly formatted (headings, lists, bold, italic). Everything else is plain text on A4 pages."
    kind == Kind.Pdf && fmt == Fmt.Txt -> "Only selectable text is extracted. Scanned PDFs have none. Layout, tables and pictures are not kept."
    kind == Kind.Pdf -> "Each page becomes a picture at about 144 dpi. Files with several pages are saved in their own folder."
    kind == Kind.Image && fmt == Fmt.Jpg -> "JPG has no transparency, so transparent areas turn white. GIFs convert their first frame only."
    kind == Kind.Image && fmt == Fmt.Pdf -> "Images are fitted onto A4 pages and reduced to about 200 dpi."
    kind == Kind.Image -> "GIFs convert their first frame only. Photo metadata other than orientation is not kept."
    kind == Kind.Video && fmt == Fmt.WebM -> "WebM (VP9) is slow to encode, often several times longer than the video itself. Keep the screen on and expect your phone to get warm."
    kind == Kind.Video && fmt == Fmt.Mp4 -> "Uses the phone's hardware encoder when it works, otherwise a slower software encoder."
    kind == Kind.Video && fmt == Fmt.Gif -> "GIFs are large and limited to the first 30 seconds, without sound."
    kind == Kind.Video -> "Extracts the sound track. Videos without sound can't be converted this way."
    fmt == Fmt.Wav || fmt == Fmt.Flac -> "Lossless output is much larger than MP3 and can't restore quality lost in the original file."
    kind == Kind.Audio && fmt.lossyAudio && sources.any { it.ext in setOf("flac", "wav", "alac") } ->
        "Converting lossless audio to a lossy format makes the file smaller but permanently loses some quality. Tags and cover art are kept where the format allows."
    kind == Kind.Audio -> "Tags and cover art are kept where the format allows (not in OGG, OPUS or WAV)."
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
                running -> "Converting ${(done + failed + 1).coerceAtMost(items.size)} of ${items.size}"
                cancelled > 0 -> "Cancelled"
                failed == 0 -> "All done"
                done == 0 -> "Nothing could be converted"
                else -> "Finished with problems"
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = c.text, modifier = Modifier.weight(1f))
                if (running) Text("${(overall * 100).roundToInt()}%", fontSize = 14.sp, color = c.accent)
            }
            if (running) ThinProgress(overall, Modifier.padding(top = 10.dp))
            else Text(
                buildString {
                    append("$done converted")
                    if (failed > 0) append(" · $failed failed")
                    if (cancelled > 0) append(" · $cancelled not done")
                },
                fontSize = 13.sp, color = c.textSecondary, modifier = Modifier.padding(top = 2.dp),
            )
            if (running) Text("You can leave this page. Conversion continues in the background.", fontSize = 13.sp, color = c.textSecondary, modifier = Modifier.padding(top = 10.dp))
        }
    }

    sectionLabel(if (items.size == 1) "File" else "Files")
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
                is ItemState.Queued -> "Waiting"
                is ItemState.Running -> "${state.step}… ${(state.progress * 100).roundToInt()}%"
                is ItemState.Done -> "Saved to ${state.output.folderLabel}" + if (state.output.files.size > 1) " · ${state.output.files.size} files" else ""
                is ItemState.Failed -> state.message
                is ItemState.Cancelled -> "Cancelled"
            },
            summaryColor = when (state) {
                is ItemState.Done -> c.success
                is ItemState.Failed -> c.error
                else -> null
            },
            trailing = if (state is ItemState.Done) ({
                Row {
                    RoundAction(OneUiIcons.Share, "Share") { scope.launch { share(context, state.output) } }
                    RoundAction(OneUiIcons.OpenExternal, "Open") { scope.launch { open(context, state.output) } }
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
    if (uris.isEmpty()) { Toast.makeText(context, "The saved file is no longer there", Toast.LENGTH_SHORT).show(); return }
    val intent = if (uris.size == 1) Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris[0])
    else Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
    intent.setType(output.mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(intent, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

private suspend fun open(context: Context, output: Output) {
    val uris = runCatching { shareable(context, output.copy(files = output.files.take(1))) }.getOrDefault(emptyList())
    if (uris.isEmpty()) { Toast.makeText(context, "The saved file is no longer there", Toast.LENGTH_SHORT).show(); return }
    val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uris[0], output.mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    try { context.startActivity(intent) } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, "No app can open this file. Try Share instead", Toast.LENGTH_SHORT).show()
    }
}

/** What can be converted, for the ⋮ › Tips popup. */
private val CONVERT_TIPS = listOf(
    com.tools.app.ui.Tip("Images", "JPG, PNG, WebP, HEIC, GIF, BMP to JPG, PNG, WebP or PDF."),
    com.tools.app.ui.Tip("PDF", "To JPG or PNG pages, or text."),
    com.tools.app.ui.Tip("Word (.docx)", "To PDF or text. Text and basic formatting only."),
    com.tools.app.ui.Tip("TXT and Markdown", "To PDF."),
    com.tools.app.ui.Tip("Audio", "MP3, WAV, M4A, FLAC, OGG, OPUS, AMR to MP3, WAV, M4A, FLAC, OGG or OPUS."),
    com.tools.app.ui.Tip("Video", "MP4, MKV, MOV, WebM, 3GP, AVI to MP4, WebM, GIF, or just the audio."),
)
