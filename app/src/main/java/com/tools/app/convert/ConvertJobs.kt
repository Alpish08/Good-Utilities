package com.tools.app.convert

import com.tools.app.ui.tp
import com.tools.app.R
import com.tools.app.ui.t
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import com.tools.app.Storage
import com.tools.app.audio.Ffmpeg
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.cancellation.CancellationException

enum class Kind(private val labelRes: Int) {
    Image(R.string.convert_kind_image), Pdf(R.string.convert_kind_pdf), Docx(R.string.convert_kind_docx), Text(R.string.convert_kind_text), Audio(R.string.convert_kind_audio), Video(R.string.convert_kind_video),
    ;
    val label get() = t(labelRes)
}

enum class Fmt(val ext: String, val label: String, val mime: String, val folder: Storage.Folder) {
    Jpg("jpg", "JPG", "image/jpeg", Storage.Folder.Pictures),
    Png("png", "PNG", "image/png", Storage.Folder.Pictures),
    Webp("webp", "WebP", "image/webp", Storage.Folder.Pictures),
    Pdf("pdf", "PDF", "application/pdf", Storage.Folder.Documents),
    Txt("txt", "TXT", "text/plain", Storage.Folder.Documents),
    Mp3("mp3", "MP3", "audio/mpeg", Storage.Folder.Audio),
    Wav("wav", "WAV", "audio/wav", Storage.Folder.Audio),
    M4a("m4a", "M4A (AAC)", "audio/mp4", Storage.Folder.Audio),
    Flac("flac", "FLAC", "audio/flac", Storage.Folder.Audio),
    Ogg("ogg", "OGG (Vorbis)", "audio/ogg", Storage.Folder.Audio),
    Opus("opus", "OPUS", "audio/ogg", Storage.Folder.Audio),
    Mp4("mp4", "MP4", "video/mp4", Storage.Folder.Videos),
    WebM("webm", "WebM", "video/webm", Storage.Folder.Videos),
    Gif("gif", "GIF", "image/gif", Storage.Folder.Videos);

    val lossyAudio get() = this == Mp3 || this == M4a || this == Ogg || this == Opus
    val lossyImage get() = this == Jpg || this == Webp
}

data class Choice(val fmt: Fmt, val title: String, val summary: String)

data class Source(val id: Int, val uri: Uri, val name: String, val size: Long, val kind: Kind, val ext: String) {
    val base get() = name.substringBeforeLast('.', name)
}

/** What a finished conversion produced and where it was saved. */
data class Output(val files: List<File>, val folderLabel: String, val mime: String)

sealed interface ItemState {
    data object Queued : ItemState
    data class Running(val progress: Float, val step: String = t(R.string.convert_converting)) : ItemState
    data class Done(val output: Output) : ItemState
    data class Failed(val message: String) : ItemState
    data object Cancelled : ItemState
}

data class Item(val id: Int, val title: String, val summary: String, val kind: Kind, val state: ItemState)

data class Opts(val imageQuality: Int, val audioQuality: Int, val videoHeight: Int, val gifWidth: Int)

/** Which "Convert to" choices make sense for a set of picked files. */
fun choicesFor(kind: Kind, exts: Set<String>): List<Choice> {
    val list = when (kind) {
        Kind.Image -> listOf(
            Choice(Fmt.Jpg, "JPG", t(R.string.convert_small_photos_transparency_becomes_whit)),
            Choice(Fmt.Png, "PNG", t(R.string.convert_lossless_keeps_transparency)),
            Choice(Fmt.Webp, "WebP", t(R.string.convert_small_files_supports_transparency)),
            Choice(Fmt.Pdf, "PDF", t(R.string.convert_one_page_per_image)),
        )
        Kind.Pdf -> listOf(
            Choice(Fmt.Jpg, t(R.string.convert_jpg_images), t(R.string.convert_one_picture_per_page_2x_resolution)),
            Choice(Fmt.Png, t(R.string.convert_png_images), t(R.string.convert_one_picture_per_page_2x_resolution)),
            Choice(Fmt.Txt, t(R.string.convert_text_txt), t(R.string.convert_selectable_text_only_no_layout)),
        )
        Kind.Docx -> listOf(
            Choice(Fmt.Pdf, "PDF", t(R.string.convert_text_and_basic_formatting_on_a4_pages)),
            Choice(Fmt.Txt, t(R.string.convert_text_txt), t(R.string.reader_plain_text)),
        )
        Kind.Text -> listOf(Choice(Fmt.Pdf, "PDF", t(R.string.convert_a4_pages_with_a_readable_font)))
        Kind.Audio -> listOf(
            Choice(Fmt.Mp3, "MP3", t(R.string.convert_plays_everywhere_lossy)),
            Choice(Fmt.M4a, "M4A (AAC)", t(R.string.convert_better_quality_than_mp3_at_the_same_si)),
            Choice(Fmt.Opus, "OPUS", t(R.string.convert_very_efficient_lossy)),
            Choice(Fmt.Ogg, "OGG (Vorbis)", t(R.string.convert_open_format_lossy)),
            Choice(Fmt.Flac, "FLAC", t(R.string.convert_lossless_larger_files)),
            Choice(Fmt.Wav, "WAV", t(R.string.convert_uncompressed_very_large_files)),
        ).filter { !(exts.size == 1 && exts.first() == it.fmt.ext) }
        Kind.Video -> listOf(
            Choice(Fmt.Mp4, "MP4", t(R.string.convert_h_264_aac_plays_everywhere)),
            Choice(Fmt.WebM, "WebM", t(R.string.convert_vp9_opus_much_slower_to_convert)),
            Choice(Fmt.Gif, "GIF", t(R.string.convert_animated_picture_first_30_seconds_no_s)),
            Choice(Fmt.Mp3, t(R.string.convert_audio_only_mp3), t(R.string.convert_extract_the_sound_track)),
            Choice(Fmt.M4a, t(R.string.convert_audio_only_m4a), t(R.string.convert_extract_the_sound_track_as_aac)),
        ).filter { !(exts.size == 1 && exts.first() == it.fmt.ext) }
    }
    return list
}

private val imageExt = setOf("jpg", "jpeg", "png", "webp", "heic", "heif", "gif", "bmp")
private val audioExt = setOf("mp3", "wav", "m4a", "aac", "flac", "ogg", "oga", "opus", "amr", "alac")
private val videoExt = setOf("mp4", "mkv", "mov", "webm", "3gp", "avi", "m4v")

/** Result of looking at a picked file: its kind, or why it can't be converted. */
fun detect(name: String, mime: String?): Pair<Kind?, String> {
    val ext = name.substringAfterLast('.', "").lowercase()
    val kind = when {
        ext in imageExt -> Kind.Image
        ext == "pdf" -> Kind.Pdf
        ext == "docx" -> Kind.Docx
        ext == "txt" || ext == "md" || ext == "markdown" -> Kind.Text
        ext in audioExt -> Kind.Audio
        ext in videoExt -> Kind.Video
        mime == null -> null
        mime.startsWith("image/") -> Kind.Image
        mime == "application/pdf" -> Kind.Pdf
        mime == "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> Kind.Docx
        mime == "text/plain" || mime == "text/markdown" -> Kind.Text
        mime.startsWith("audio/") -> Kind.Audio
        mime.startsWith("video/") -> Kind.Video
        else -> null
    }
    return kind to ext
}

/**
 * Conversion state lives here, outside the screen, so a running batch keeps going (and its
 * progress and results are still there) when you leave the page and come back.
 */
object ConvertJobs {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val ids = AtomicInteger()

    // Setup
    val sources = mutableStateListOf<Source>()
    val target = mutableStateOf<Fmt?>(null)
    val notice = mutableStateOf<String?>(null)
    val imageQuality = mutableStateOf(90f)
    val audioQuality = mutableStateOf(75f)
    val videoHeight = mutableStateOf(0)
    val gifWidth = mutableStateOf(480)
    val combinePdf = mutableStateOf(true)

    // Run
    val items = mutableStateOf<List<Item>>(emptyList())
    val running = mutableStateOf(false)
    private var job: Job? = null

    val kind: Kind? get() = sources.firstOrNull()?.kind
    val choices: List<Choice> get() = kind?.let { choicesFor(it, sources.map { s -> s.ext }.toSet()) } ?: emptyList()

    fun add(context: Context, uris: List<Uri>) {
        if (running.value) return
        val resolver = context.contentResolver
        val problems = mutableListOf<String>()
        var otherType = 0
        for (uri in uris) {
            if (sources.any { it.uri == uri }) continue
            var name = "File"; var size = 0L
            runCatching {
                resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use {
                    if (it.moveToFirst()) {
                        it.getString(0)?.let { n -> name = n }
                        if (!it.isNull(1)) size = it.getLong(1)
                    }
                }
            }
            val (kind, ext) = detect(name, resolver.getType(uri))
            when {
                kind == null && ext == "doc" -> problems += t(R.string.convert_old_doc_files_aren_t_supported_save_it, name)
                kind == null -> problems += if (ext.isEmpty()) t(R.string.convert_cant_convert_type, name) else t(R.string.convert_cant_convert_ext, name, ext)
                sources.isNotEmpty() && sources.first().kind != kind -> otherType++
                else -> sources.add(Source(ids.incrementAndGet(), uri, name, size, kind, ext))
            }
        }
        if (otherType > 0) problems += tp(R.plurals.convert_skipped_other_type, otherType, otherType, sources.first().kind.label.lowercase())
        notice.value = problems.takeIf { it.isNotEmpty() }?.joinToString("\n")
        syncTarget()
    }

    fun remove(source: Source) {
        if (running.value) return
        sources.remove(source)
        notice.value = null
        syncTarget()
    }

    private fun syncTarget() {
        val options = choices
        if (options.none { it.fmt == target.value }) target.value = options.firstOrNull()?.fmt
    }

    /** Back to the file picker after a finished batch. */
    fun reset() {
        if (running.value) return
        items.value = emptyList()
        sources.clear()
        notice.value = null
        target.value = null
    }

    fun cancel() { job?.cancel() }

    private fun set(index: Int, state: ItemState) {
        items.value = items.value.toMutableList().also { if (index in it.indices) it[index] = it[index].copy(state = state) }
    }

    fun start(context: Context) {
        val fmt = target.value ?: return
        if (running.value || sources.isEmpty()) return
        val app = context.applicationContext
        val list = sources.toList()
        val opts = Opts(imageQuality.value.toInt(), audioQuality.value.toInt(), videoHeight.value, gifWidth.value)
        val combine = fmt == Fmt.Pdf && list.first().kind == Kind.Image && list.size > 1 && combinePdf.value
        items.value = if (combine) {
            listOf(Item(0, "${list.first().base} + ${list.size - 1} more.pdf", t(R.string.convert_images_into_one_pdf, list.size), Kind.Image, ItemState.Queued))
        } else list.mapIndexed { i, s -> Item(i, s.name, t(R.string.convert_x_to_y, s.kind.label, fmt.label), s.kind, ItemState.Queued) }
        running.value = true
        job = scope.launch {
            try {
                if (combine) {
                    set(0, ItemState.Running(0f))
                    try {
                        set(0, ItemState.Done(Engine.imagesToPdf(app, list) { set(0, ItemState.Running(it)) }))
                    } catch (e: CancellationException) { throw e } catch (t: Throwable) { set(0, ItemState.Failed(Engine.friendly(t))) }
                } else {
                    for ((i, s) in list.withIndex()) {
                        set(i, ItemState.Running(0f))
                        try {
                            set(i, ItemState.Done(Engine.convert(app, s, fmt, opts) { p -> set(i, ItemState.Running(p)) }))
                        } catch (e: CancellationException) { throw e } catch (t: Throwable) { set(i, ItemState.Failed(Engine.friendly(t))) }
                    }
                }
            } catch (e: CancellationException) {
                items.value = items.value.map { if (it.state is ItemState.Running || it.state is ItemState.Queued) it.copy(state = ItemState.Cancelled) else it }
            } finally {
                running.value = false
                Engine.clearCache(app)
            }
        }
    }
}
