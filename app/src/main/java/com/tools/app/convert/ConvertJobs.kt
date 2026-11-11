package com.tools.app.convert

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

enum class Kind(val label: String) {
    Image("Image"), Pdf("PDF"), Docx("Word document"), Text("Text file"), Audio("Audio"), Video("Video"),
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
    data class Running(val progress: Float, val step: String = "Converting") : ItemState
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
            Choice(Fmt.Jpg, "JPG", "Small photos. Transparency becomes white"),
            Choice(Fmt.Png, "PNG", "Lossless, keeps transparency"),
            Choice(Fmt.Webp, "WebP", "Small files, supports transparency"),
            Choice(Fmt.Pdf, "PDF", "One page per image"),
        )
        Kind.Pdf -> listOf(
            Choice(Fmt.Jpg, "JPG images", "One picture per page, 2x resolution"),
            Choice(Fmt.Png, "PNG images", "One picture per page, 2x resolution"),
            Choice(Fmt.Txt, "Text (TXT)", "Selectable text only, no layout"),
        )
        Kind.Docx -> listOf(
            Choice(Fmt.Pdf, "PDF", "Text and basic formatting on A4 pages"),
            Choice(Fmt.Txt, "Text (TXT)", "Plain text"),
        )
        Kind.Text -> listOf(Choice(Fmt.Pdf, "PDF", "A4 pages with a readable font"))
        Kind.Audio -> listOf(
            Choice(Fmt.Mp3, "MP3", "Plays everywhere, lossy"),
            Choice(Fmt.M4a, "M4A (AAC)", "Better quality than MP3 at the same size"),
            Choice(Fmt.Opus, "OPUS", "Very efficient, lossy"),
            Choice(Fmt.Ogg, "OGG (Vorbis)", "Open format, lossy"),
            Choice(Fmt.Flac, "FLAC", "Lossless, larger files"),
            Choice(Fmt.Wav, "WAV", "Uncompressed, very large files"),
        ).filter { !(exts.size == 1 && exts.first() == it.fmt.ext) }
        Kind.Video -> listOf(
            Choice(Fmt.Mp4, "MP4", "H.264 + AAC, plays everywhere"),
            Choice(Fmt.WebM, "WebM", "VP9 + Opus. Much slower to convert"),
            Choice(Fmt.Gif, "GIF", "Animated picture, first 30 seconds, no sound"),
            Choice(Fmt.Mp3, "Audio only · MP3", "Extract the sound track"),
            Choice(Fmt.M4a, "Audio only · M4A", "Extract the sound track as AAC"),
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
                kind == null && ext == "doc" -> problems += "$name: old .doc files aren't supported. Save it as .docx first."
                kind == null -> problems += "$name: can't convert ${if (ext.isEmpty()) "this type of file" else ".$ext files"}."
                sources.isNotEmpty() && sources.first().kind != kind -> otherType++
                else -> sources.add(Source(ids.incrementAndGet(), uri, name, size, kind, ext))
            }
        }
        if (otherType > 0) problems += "$otherType file${if (otherType > 1) "s" else ""} skipped: convert one type of file at a time (${sources.first().kind.label.lowercase()} files here)."
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
            listOf(Item(0, "${list.first().base} + ${list.size - 1} more.pdf", "${list.size} images into one PDF", Kind.Image, ItemState.Queued))
        } else list.mapIndexed { i, s -> Item(i, s.name, "${s.kind.label} to ${fmt.label}", s.kind, ItemState.Queued) }
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
