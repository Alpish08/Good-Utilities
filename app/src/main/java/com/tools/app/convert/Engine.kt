package com.tools.app.convert

import com.tools.app.R
import com.tools.app.ui.t
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.media.MediaMetadataRetriever
import android.os.Build
import android.os.ParcelFileDescriptor
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tools.app.Storage
import com.tools.app.audio.Ffmpeg
import com.tools.app.ui.Images
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.coroutineContext
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

internal object Engine {
    private fun work(context: Context) = File(context.cacheDir, "convert").apply { mkdirs() }
    fun clearCache(context: Context) { File(context.cacheDir, "convert").deleteRecursively() }

    fun friendly(t: Throwable): String {
        val msg = t.message.orEmpty()
        return when {
            t is Storage.NoAccess -> msg
            t is Docx.Invalid -> msg
            t is InvalidPasswordException || t is SecurityException -> t(R.string.convert_this_pdf_is_password_protected_remove)
            t is OutOfMemoryError -> t(R.string.convert_not_enough_memory_for_a_file_this_larg)
            t is Ffmpeg.Failed -> when {
                "matches no streams" in msg || "does not contain any stream" in msg -> t(R.string.convert_this_file_has_no_audio_track)
                "doesn't look like audio" in msg || "Invalid data" in msg -> t(R.string.convert_couldn_t_read_this_file_it_may_be_dama)
                else -> t(R.string.convert_conversion_failed, msg)
            }
            t is java.io.IOException && "ENOSPC" in msg -> t(R.string.convert_not_enough_free_storage_space)
            t is android.graphics.ImageDecoder.DecodeException -> t(R.string.convert_couldn_t_read_this_image_it_may_be_dam)
            else -> msg.ifBlank { t(R.string.convert_conversion_failed_2) }
        }
    }

    private fun mkOutput(fmt: Fmt, sub: String?, names: List<String>) =
        Output(names.map { File(Storage.dir(fmt.folder, sub), it) }, Storage.label(fmt.folder, sub), fmt.mime)

    private fun copyToCache(context: Context, src: Source): File {
        val f = File(work(context), "in-${src.id}.${src.ext.ifEmpty { "bin" }}")
        val input = context.contentResolver.openInputStream(src.uri) ?: error(t(R.string.convert_couldn_t_open, src.name))
        input.use { i -> f.outputStream().use { i.copyTo(it) } }
        return f
    }

    suspend fun convert(context: Context, src: Source, fmt: Fmt, opts: Opts, progress: (Float) -> Unit): Output =
        withContext(Dispatchers.IO) {
            when (src.kind) {
                Kind.Image -> if (fmt == Fmt.Pdf) imagesToPdf(context, listOf(src), progress) else imageToImage(context, src, fmt, opts, progress)
                Kind.Pdf -> if (fmt == Fmt.Txt) pdfToText(context, src, progress) else pdfToImages(context, src, fmt, opts, progress)
                Kind.Docx -> docToOutput(context, src, fmt, progress)
                Kind.Text -> docToOutput(context, src, fmt, progress)
                Kind.Audio -> audio(context, src, fmt, opts, progress)
                Kind.Video -> video(context, src, fmt, opts, progress)
            }
        }

    // ---- Images ------------------------------------------------------------------------------

    private fun compress(bmp: Bitmap, fmt: Fmt, quality: Int, out: java.io.OutputStream) {
        val format = when (fmt) {
            Fmt.Jpg -> Bitmap.CompressFormat.JPEG
            Fmt.Png -> Bitmap.CompressFormat.PNG
            else -> if (Build.VERSION.SDK_INT >= 30) {
                if (quality >= 100) Bitmap.CompressFormat.WEBP_LOSSLESS else Bitmap.CompressFormat.WEBP_LOSSY
            } else @Suppress("DEPRECATION") Bitmap.CompressFormat.WEBP
        }
        if (!bmp.compress(format, quality.coerceIn(1, 100), out)) error(t(R.string.convert_couldn_t_encode_the_image, fmt.label))
    }

    private fun onWhite(bmp: Bitmap): Bitmap {
        if (!bmp.hasAlpha()) return bmp
        val flat = Bitmap.createBitmap(bmp.width, bmp.height, Bitmap.Config.ARGB_8888)
        Canvas(flat).apply { drawColor(Color.WHITE); drawBitmap(bmp, 0f, 0f, null) }
        bmp.recycle()
        return flat
    }

    private fun imageToImage(context: Context, src: Source, fmt: Fmt, opts: Opts, progress: (Float) -> Unit): Output {
        progress(0.1f)
        // ImageDecoder applies the EXIF orientation and returns the first frame of a GIF.
        var bmp = Images.load(context, src.uri, 6000)
        if (fmt == Fmt.Jpg) bmp = onWhite(bmp)
        progress(0.6f)
        val name = Storage.save(context, fmt.folder, null, "${src.base}.${fmt.ext}") { compress(bmp, fmt, opts.imageQuality, it) }
        bmp.recycle()
        return mkOutput(fmt, null, listOf(name))
    }

    suspend fun imagesToPdf(context: Context, list: List<Source>, progress: (Float) -> Unit): Output = withContext(Dispatchers.IO) {
        val doc = PdfDocument()
        try {
            for ((i, s) in list.withIndex()) {
                coroutineContext.ensureActive()
                val bmp = Images.load(context, s.uri, 2400)
                val landscape = bmp.width > bmp.height
                val pw = if (landscape) PdfBuilder.H else PdfBuilder.W
                val ph = if (landscape) PdfBuilder.W else PdfBuilder.H
                val page = doc.startPage(PdfDocument.PageInfo.Builder(pw, ph, i + 1).create())
                val c = page.canvas
                c.drawColor(Color.WHITE)
                val margin = 24f
                val scale = min((pw - 2 * margin) / bmp.width, (ph - 2 * margin) / bmp.height)
                val w = bmp.width * scale; val h = bmp.height * scale
                val left = (pw - w) / 2f; val top = (ph - h) / 2f
                c.drawBitmap(bmp, null, RectF(left, top, left + w, top + h), Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
                doc.finishPage(page)
                bmp.recycle()
                progress((i + 1) / list.size.toFloat() * 0.9f)
            }
            val base = if (list.size == 1) list[0].base else "${list[0].base} + ${list.size - 1} more"
            val name = Storage.save(context, Fmt.Pdf.folder, null, "$base.pdf") { doc.writeTo(it) }
            mkOutput(Fmt.Pdf, null, listOf(name))
        } finally {
            doc.close()
        }
    }

    // ---- PDF ---------------------------------------------------------------------------------

    private suspend fun pdfToImages(context: Context, src: Source, fmt: Fmt, opts: Opts, progress: (Float) -> Unit): Output {
        val file = copyToCache(context, src)
        val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        val names = ArrayList<String>()
        var sub: String? = null
        try {
            PdfRenderer(pfd).use { renderer ->
                val count = renderer.pageCount
                if (count == 0) error(t(R.string.convert_this_pdf_has_no_pages))
                sub = if (count > 1) Storage.safe(src.base) else null
                val pad = count.toString().length
                for (i in 0 until count) {
                    coroutineContext.ensureActive()
                    renderer.openPage(i).use { page ->
                        // 2x the page's size in points (about 144 dpi), capped so huge pages fit in memory.
                        val scale = min(2f, 4200f / max(page.width, page.height))
                        val bmp = Bitmap.createBitmap((page.width * scale).roundToInt().coerceAtLeast(1), (page.height * scale).roundToInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
                        bmp.eraseColor(Color.WHITE)
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        val fileName = if (count > 1) "${src.base} - page ${(i + 1).toString().padStart(pad, '0')}.${fmt.ext}" else "${src.base}.${fmt.ext}"
                        names += Storage.save(context, fmt.folder, sub, fileName) { compress(bmp, fmt, opts.imageQuality, it) }
                        bmp.recycle()
                    }
                    progress((i + 1) / count.toFloat())
                }
            }
        } finally {
            pfd.close(); file.delete()
        }
        return mkOutput(fmt, sub, names)
    }

    private fun pdfToText(context: Context, src: Source, progress: (Float) -> Unit): Output {
        PDFBoxResourceLoader.init(context.applicationContext)
        val file = copyToCache(context, src)
        try {
            PDDocument.load(file).use { pdf ->
                val pages = pdf.numberOfPages
                val stripper = PDFTextStripper()
                val text = StringBuilder()
                for (i in 1..pages) {
                    stripper.startPage = i; stripper.endPage = i
                    text.append(stripper.getText(pdf).trim()).append("\n\n")
                    progress(i / pages.toFloat() * 0.95f)
                }
                if (text.isBlank()) error(t(R.string.convert_no_selectable_text_found_this_pdf_may))
                val name = Storage.save(context, Fmt.Txt.folder, null, "${src.base}.txt") { it.write(text.toString().trim().plus("\n").toByteArray()) }
                return mkOutput(Fmt.Txt, null, listOf(name))
            }
        } finally {
            file.delete()
        }
    }

    // ---- DOCX / TXT / MD ---------------------------------------------------------------------

    private fun docToOutput(context: Context, src: Source, fmt: Fmt, progress: (Float) -> Unit): Output {
        val file = copyToCache(context, src)
        try {
            progress(0.05f)
            val blocks = if (src.kind == Kind.Docx) Docx.parse(file) else {
                val text = PlainText.decode(file)
                if (src.ext == "txt") PlainText.plain(text) else PlainText.markdown(text)
            }
            if (fmt == Fmt.Txt) {
                val text = Docx.toText(blocks)
                val name = Storage.save(context, fmt.folder, null, "${src.base}.txt") { it.write(text.toByteArray()) }
                return mkOutput(fmt, null, listOf(name))
            }
            val name = Storage.save(context, fmt.folder, null, "${src.base}.pdf") { out ->
                PdfBuilder().render(blocks, out) { progress(0.1f + it * 0.85f) }
            }
            return mkOutput(fmt, null, listOf(name))
        } finally {
            file.delete()
        }
    }

    // ---- Audio -------------------------------------------------------------------------------

    private fun audioCodec(fmt: Fmt, q: Int): List<String> = when (fmt) {
        Fmt.Mp3 -> listOf("-c:a", "libmp3lame", "-q:a", ((100 - q) * 9 / 100f).roundToInt().coerceIn(0, 9).toString(), "-id3v2_version", "3")
        Fmt.M4a -> listOf("-c:a", "aac", "-b:a", "${(64 + q * 1.7).roundToInt()}k", "-movflags", "+faststart")
        Fmt.Opus -> listOf("-c:a", "libopus", "-b:a", "${(48 + q * 1.07).roundToInt()}k", "-vbr", "on")
        Fmt.Ogg -> listOf("-c:a", "libvorbis", "-q:a", "%.1f".format(Locale.US, (q / 10f).coerceIn(1f, 10f)))
        Fmt.Flac -> listOf("-c:a", "flac")
        else -> listOf("-c:a", "pcm_s16le")
    }

    private suspend fun audio(context: Context, src: Source, fmt: Fmt, opts: Opts, progress: (Float) -> Unit): Output {
        progress(0.02f)
        val clip = Ffmpeg.import(context, src.uri, "cv-${src.id}")
        val out = Ffmpeg.scratch(context, "cv-${src.id}-out.${fmt.ext}")
        try {
            val coverOk = src.kind == Kind.Audio && (fmt == Fmt.Mp3 || fmt == Fmt.M4a || fmt == Fmt.Flac)
            suspend fun go(cover: Boolean) {
                val args = mutableListOf("-y", "-i", clip.file.absolutePath, "-map", "0:a:0")
                if (cover) args += listOf("-map", "0:v:0?", "-c:v", "copy", "-disposition:v", "attached_pic") else args += "-vn"
                args += listOf("-map_metadata", "0")
                args += audioCodec(fmt, opts.audioQuality)
                args += out.absolutePath
                Ffmpeg.run(context, args, clip.durationMs) { progress(0.05f + it * 0.9f) }
            }
            if (coverOk) {
                // Cover art is a bonus: if copying it fails, convert the sound alone.
                try { go(true) } catch (e: Ffmpeg.Failed) { out.delete(); go(false) }
            } else go(false)
            progress(0.97f)
            val name = Storage.saveFile(context, fmt.folder, null, "${src.base}.${fmt.ext}", out)
            return mkOutput(fmt, null, listOf(name))
        } finally {
            clip.file.delete(); out.delete()
        }
    }

    // ---- Video -------------------------------------------------------------------------------

    private class VideoInfo(val width: Int, val height: Int, val bitrate: Int)

    private fun probe(file: File): VideoInfo {
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(file.absolutePath)
            fun n(k: Int) = r.extractMetadata(k)?.toIntOrNull() ?: 0
            val rot = n(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
            val w = n(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH); val h = n(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
            if (rot == 90 || rot == 270) VideoInfo(h, w, n(MediaMetadataRetriever.METADATA_KEY_BITRATE)) else VideoInfo(w, h, n(MediaMetadataRetriever.METADATA_KEY_BITRATE))
        } catch (e: Exception) {
            VideoInfo(0, 0, 0)
        } finally {
            r.release()
        }
    }

    private suspend fun video(context: Context, src: Source, fmt: Fmt, opts: Opts, progress: (Float) -> Unit): Output {
        if (fmt == Fmt.Mp3 || fmt == Fmt.M4a) return audio(context, src, fmt, opts, progress)
        progress(0.02f)
        val clip = Ffmpeg.import(context, src.uri, "cv-${src.id}")
        val out = Ffmpeg.scratch(context, "cv-${src.id}-out.${fmt.ext}")
        try {
            val info = probe(clip.file)
            val input = clip.file.absolutePath
            val onProgress: (Float) -> Unit = { progress(0.05f + it * 0.9f) }
            when (fmt) {
                Fmt.Gif -> {
                    val w = opts.gifWidth
                    val graph = "fps=12,scale='min($w,iw)':-1:flags=lanczos,split[s0][s1];[s0]palettegen=stats_mode=diff[p];[s1][p]paletteuse=dither=bayer:bayer_scale=5"
                    val args = listOf("-y", "-t", "30", "-i", input, "-an", "-vf", graph, "-loop", "0", out.absolutePath)
                    Ffmpeg.run(context, args, min(clip.durationMs, 30_000L), onProgress)
                }
                Fmt.WebM -> {
                    val args = listOf(
                        "-y", "-i", input, "-map", "0:v:0", "-map", "0:a:0?", "-vf", scaleFilter(opts.videoHeight, info),
                        "-c:v", "libvpx-vp9", "-b:v", "0", "-crf", "33", "-row-mt", "1", "-deadline", "good", "-cpu-used", "4",
                        "-c:a", "libopus", "-b:a", "128k", "-map_metadata", "0", out.absolutePath,
                    )
                    Ffmpeg.run(context, args, clip.durationMs, onProgress)
                }
                else -> { // MP4: hardware encoder first, software x264 if it isn't available or fails
                    val outH = if (opts.videoHeight > 0 && info.height > opts.videoHeight) opts.videoHeight else info.height
                    val outW = if (info.height > 0) (info.width.toLong() * outH / info.height).toInt() else 1920
                    var bps = (max(outW, 2) * max(outH, 2) * 30L * 0.09).toLong().coerceIn(1_000_000L, 20_000_000L)
                    if (info.bitrate > 0) bps = min(bps, info.bitrate.toLong())
                    val head = listOf("-y", "-i", input, "-map", "0:v:0", "-map", "0:a:0?", "-vf", scaleFilter(opts.videoHeight, info))
                    val tail = listOf("-c:a", "aac", "-b:a", "192k", "-movflags", "+faststart", "-map_metadata", "0", out.absolutePath)
                    try {
                        Ffmpeg.run(context, head + listOf("-c:v", "h264_mediacodec", "-b:v", bps.toString()) + tail, clip.durationMs, onProgress)
                    } catch (e: Ffmpeg.Failed) {
                        out.delete()
                        progress(0.05f)
                        Ffmpeg.run(context, head + listOf("-c:v", "libx264", "-preset", "veryfast", "-crf", "23") + tail, clip.durationMs, onProgress)
                    }
                }
            }
            progress(0.97f)
            val name = Storage.saveFile(context, fmt.folder, null, "${src.base}.${fmt.ext}", out)
            return mkOutput(fmt, null, listOf(name))
        } finally {
            clip.file.delete(); out.delete()
        }
    }

    /** Even-sized frames in yuv420p, optionally capped to [height] (never upscaled). */
    private fun scaleFilter(height: Int, info: VideoInfo) =
        if (height > 0 && (info.height == 0 || info.height > height)) "scale=-2:$height,format=yuv420p"
        else "scale=trunc(iw/2)*2:trunc(ih/2)*2,format=yuv420p"
}
