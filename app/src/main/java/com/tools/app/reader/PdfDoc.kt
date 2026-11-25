package com.tools.app.reader

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Point
import android.graphics.RectF
import android.graphics.pdf.LoadParams
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.ext.SdkExtensions
import android.util.LruCache
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.interactive.action.PDActionGoTo
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.destination.PDNamedDestination
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageDestination
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineNode
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.Executors

/** Something the viewer can explain to the user instead of crashing. */
class ReaderError(val title: String, val summary: String) : Exception(summary)
class PasswordCancelled : Exception()

class OutlineEntry(val title: String, val page: Int, val level: Int)
class PdfMeta(val title: String?, val author: String?)
class Selection(val page: Int, val text: String, val rects: List<RectF>)

/** Page.searchText / selectContent need Android 15 with the S extension 13 (always true on Android 16). */
val advancedPdf: Boolean by lazy {
    Build.VERSION.SDK_INT >= 35 && runCatching { SdkExtensions.getExtensionVersion(Build.VERSION_CODES.S) >= 13 }.getOrDefault(false)
}

/**
 * An open PDF. PdfRenderer is not thread safe, so every renderer call runs on [dispatcher], a single
 * thread. Cancelled requests that are still queued are skipped, which keeps fast flings cheap.
 */
class PdfDoc private constructor(
    private val renderer: PdfRenderer,
    private val pfd: ParcelFileDescriptor,
    val info: DocInfo,
    private val file: File?,
    private val contentUri: Uri,
    private val appContext: Context,
    private val boxPassword: String?,
    val pageCount: Int,
    first: FloatArray,
) {
    private val thread = Executors.newSingleThreadExecutor { Thread(it, "pdf-render").apply { priority = Thread.NORM_PRIORITY - 1 } }
    val dispatcher: CoroutineDispatcher = thread.asCoroutineDispatcher()
    @Volatile private var closed = false
    private val id = info.key + (file?.length() ?: 0)

    /** Interleaved width, height in PDF points; pages not yet measured use page 1's size. */
    var sizes by mutableStateOf(first)
        private set

    fun pageW(i: Int) = sizes.let { if (2 * i + 1 < it.size) it[2 * i] else it[0] }
    fun pageH(i: Int) = sizes.let { if (2 * i + 1 < it.size) it[2 * i + 1] else it[1] }

    /** File for tools that need a path (PDF editor); null for pure content: sources. */
    val localUri: Uri get() = contentUri

    private fun readSizes() {
        // Measured in small chunks on the render thread so that page renders interleave.
        val all = FloatArray(pageCount * 2)
        all[0] = sizes[0]; all[1] = sizes[1]
        var i = 1
        fun chunk() {
            if (closed || i >= pageCount) return
            thread.execute {
                if (closed) return@execute
                val end = minOf(pageCount, i + 24)
                for (p in i until end) {
                    runCatching { renderer.openPage(p).use { all[2 * p] = it.width.toFloat(); all[2 * p + 1] = it.height.toFloat() } }
                    if (all[2 * p] == 0f) { all[2 * p] = all[0]; all[2 * p + 1] = all[1] }
                }
                i = end
                if (i % 96 == 0 || i >= pageCount) sizes = all.copyOf()
                chunk()
            }
        }
        chunk()
    }

    fun cached(i: Int, w: Int): Bitmap? = cache.get("$id:$i:$w")

    /** Renders the whole page into a w x h bitmap (white background). */
    suspend fun page(i: Int, w: Int, h: Int): Bitmap? {
        cache.get("$id:$i:$w")?.let { return it }
        return withContext(dispatcher) {
            cache.get("$id:$i:$w")?.let { return@withContext it }
            ensureActive()
            if (closed) return@withContext null
            runCatching {
                val bmp = Bitmap.createBitmap(w.coerceAtLeast(1), h.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
                bmp.eraseColor(Color.WHITE)
                renderer.openPage(i).use { it.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY) }
                cache.put("$id:$i:$w", bmp)
                bmp
            }.getOrNull()
        }
    }

    /** Renders the region (left, top, w, h) of page [i], given in pixels at [pxPerPoint] device pixels per PDF point. */
    suspend fun tile(i: Int, pxPerPoint: Float, left: Int, top: Int, w: Int, h: Int): Bitmap? = withContext(dispatcher) {
        ensureActive()
        if (closed) return@withContext null
        runCatching {
            val bmp = Bitmap.createBitmap(w.coerceAtLeast(1), h.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
            bmp.eraseColor(Color.WHITE)
            val m = Matrix().apply { setScale(pxPerPoint, pxPerPoint); postTranslate(-left.toFloat(), -top.toFloat()) }
            renderer.openPage(i).use { it.render(bmp, null, m, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY) }
            bmp
        }.getOrNull()
    }

    // ---- text: search / selection (Android 15+) ----

    /** Match rectangles (PDF points) per hit on page [i]; only with [advancedPdf]. */
    suspend fun searchNative(i: Int, query: String): List<List<RectF>> = withContext(dispatcher) {
        ensureActive()
        if (closed) emptyList() else runCatching {
            renderer.openPage(i).use { p -> p.searchText(query).map { it.bounds.toList() } }
        }.getOrDefault(emptyList())
    }

    suspend fun selectWord(i: Int, x: Int, y: Int): Selection? = withContext(dispatcher) {
        if (closed) return@withContext null
        runCatching {
            renderer.openPage(i).use { p ->
                val b = android.graphics.pdf.models.selection.SelectionBoundary(Point(x, y))
                val s = p.selectContent(b, b) ?: return@use null
                val text = s.selectedTextContents.joinToString(" ") { it.text }.trim()
                if (text.isEmpty()) null else Selection(i, text, s.selectedTextContents.flatMap { it.bounds })
            }
        }.getOrNull()
    }

    suspend fun allText(i: Int): Selection = withContext(dispatcher) {
        runCatching {
            renderer.openPage(i).use { p ->
                val c = p.textContents
                Selection(i, c.joinToString("\n") { it.text }, c.flatMap { it.bounds })
            }
        }.getOrDefault(Selection(i, "", emptyList()))
    }

    // ---- PdfBox (outline, metadata, search / text below Android 15) ----

    private val boxLock = Mutex()
    private var box: PDDocument? = null

    private fun openStream(): InputStream =
        file?.let { FileInputStream(it) } ?: appContext.contentResolver.openInputStream(contentUri) ?: throw IOException("no stream")

    /** A fresh stream of the PDF bytes (printing, copying). */
    fun stream(): InputStream = openStream()

    private suspend fun <T> withBox(block: (PDDocument) -> T): T = withContext(Dispatchers.IO) {
        boxLock.withLock {
            if (closed) throw IOException("closed")
            val d = box ?: run {
                PDFBoxResourceLoader.init(appContext)
                openStream().use { PDDocument.load(it, boxPassword ?: "", MemoryUsageSetting.setupTempFileOnly()) }.also { box = it }
            }
            block(d)
        }
    }

    suspend fun pageText(i: Int): String =
        if (advancedPdf) allText(i).text
        else runCatching {
            withBox { d -> PDFTextStripper().apply { startPage = i + 1; endPage = i + 1 }.getText(d) }
        }.getOrDefault("")

    suspend fun meta(): PdfMeta = runCatching {
        withBox { d -> d.documentInformation.let { PdfMeta(it.title?.takeIf(String::isNotBlank), it.author?.takeIf(String::isNotBlank)) } }
    }.getOrDefault(PdfMeta(null, null))

    suspend fun outline(): List<OutlineEntry> = runCatching {
        withBox { d ->
            val root = d.documentCatalog.documentOutline ?: return@withBox emptyList()
            val out = ArrayList<OutlineEntry>()
            fun pageOf(item: PDOutlineItem): Int {
                var dest = runCatching { item.destination }.getOrNull()
                if (dest == null) {
                    val action = runCatching { item.action }.getOrNull()
                    if (action is PDActionGoTo) dest = runCatching { action.destination }.getOrNull()
                }
                if (dest is PDNamedDestination) dest = runCatching { d.documentCatalog.findNamedDestinationPage(dest as PDNamedDestination) }.getOrNull()
                if (dest !is PDPageDestination) return -1
                val pg = dest.page
                return if (pg != null) d.pages.indexOf(pg) else dest.pageNumber
            }
            fun walk(node: PDOutlineNode, level: Int) {
                var it: PDOutlineItem? = node.firstChild
                var guard = 0
                while (it != null && out.size < 5000 && guard++ < 5000) {
                    val title = it.title?.trim().orEmpty()
                    if (title.isNotEmpty()) out.add(OutlineEntry(title, pageOf(it), level))
                    walk(it, level + 1)
                    it = it.nextSibling
                }
            }
            walk(root, 0)
            out.filter { it.page in 0 until pageCount }
        }
    }.getOrDefault(emptyList())

    /** Occurrences of [q] on page [i] (page-level only, no rectangles) using PdfBox text. */
    suspend fun searchText(i: Int, q: String): Int {
        val t = pageText(i)
        if (t.isEmpty()) return 0
        var n = 0
        var at = t.indexOf(q, 0, ignoreCase = true)
        while (at >= 0) { n++; at = t.indexOf(q, at + q.length, ignoreCase = true) }
        return n
    }

    fun close() {
        if (closed) return
        closed = true
        thread.execute { runCatching { renderer.close() }; runCatching { pfd.close() } }
        thread.shutdown()
        runCatching { box?.close() }
    }

    companion object {
        private val cache = object : LruCache<String, Bitmap>(
            (Runtime.getRuntime().maxMemory() / 5).toInt().coerceIn(48 shl 20, 192 shl 20),
        ) {
            override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
        }

        /**
         * Opens [source] (a file: or content: URI of PDF bytes). [ask] is called when a password is needed
         * (wrong = the previous one was rejected); return null to give up.
         */
        suspend fun open(ctx: Context, source: Uri, info: DocInfo, ask: suspend (wrong: Boolean) -> String?): PdfDoc =
            withContext(Dispatchers.IO) {
                val app = ctx.applicationContext
                PDFBoxResourceLoader.init(app)
                var file: File? = if (source.scheme == "file") File(source.path ?: "") else null
                val dir = ReaderStore.cacheDir(app)

                fun openPfd(): ParcelFileDescriptor =
                    if (file != null) ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                    else app.contentResolver.openFileDescriptor(source, "r") ?: throw IOException("no descriptor")

                fun copyLocal(): File {
                    val out = File(dir, "copy_${info.key}.pdf")
                    val input = app.contentResolver.openInputStream(source) ?: throw IOException("no stream")
                    input.use { i -> out.outputStream().use { o -> i.copyTo(o) } }
                    return out
                }

                fun corrupt() = ReaderError("Can't open this file", "\"${info.name}\" looks damaged or isn't a valid PDF.")

                var password: String? = null
                var renderer: PdfRenderer? = null
                var pfd: ParcelFileDescriptor? = null
                var attempts = 0
                var decrypted = false
                while (renderer == null) {
                    currentCoroutineContext().ensureActive()
                    if (++attempts > 50) throw corrupt()
                    val fd = try {
                        openPfd()
                    } catch (e: SecurityException) {
                        throw ReaderError("No permission", "One Reader isn't allowed to open this file any more. Pick it again from the file list.")
                    } catch (e: FileNotFoundException) {
                        throw ReaderError("File not found", "\"${info.name}\" was moved or deleted.")
                    } catch (e: IOException) {
                        if (file == null) {
                            try { file = copyLocal() } catch (e2: SecurityException) {
                                throw ReaderError("No permission", "One Reader isn't allowed to open this file any more. Pick it again from the file list.")
                            } catch (e2: IOException) { throw ReaderError("Can't read this file", "\"${info.name}\" couldn't be read.") }
                            continue
                        }
                        throw corrupt()
                    }
                    try {
                        renderer = if (password != null && advancedPdf)
                            PdfRenderer(fd, LoadParams.Builder().setPassword(password).build())
                        else PdfRenderer(fd)
                        pfd = fd
                    } catch (e: SecurityException) {
                        runCatching { fd.close() }
                        if (advancedPdf) {
                            password = ask(password != null) ?: throw PasswordCancelled()
                        } else {
                            // Below Android 15 PdfRenderer can't open encrypted files: decrypt a copy with PdfBox.
                            var pw = ask(false) ?: throw PasswordCancelled()
                            val plain = File(dir, "dec_${info.key}.pdf")
                            while (true) {
                                try {
                                    val stream = file?.let { FileInputStream(it) } ?: app.contentResolver.openInputStream(source) ?: throw IOException("no stream")
                                    stream.use { s ->
                                        PDDocument.load(s, pw, MemoryUsageSetting.setupTempFileOnly()).use { d ->
                                            d.isAllSecurityToBeRemoved = true
                                            d.save(plain)
                                        }
                                    }
                                    break
                                } catch (w: com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException) {
                                    pw = ask(true) ?: throw PasswordCancelled()
                                } catch (t: IOException) { throw corrupt() }
                            }
                            file = plain; decrypted = true
                        }
                    } catch (e: IOException) {
                        runCatching { fd.close() }
                        if (file == null) { file = runCatching { copyLocal() }.getOrElse { throw corrupt() } } else throw corrupt()
                    } catch (e: RuntimeException) {
                        runCatching { fd.close() }
                        throw corrupt()
                    }
                }
                val r = renderer!!
                val count = r.pageCount
                if (count <= 0) { r.close(); throw ReaderError("Empty document", "\"${info.name}\" has no pages.") }
                val first = runCatching { r.openPage(0).use { floatArrayOf(it.width.toFloat(), it.height.toFloat()) } }.getOrElse {
                    r.close(); throw corrupt()
                }
                val uri = file?.let { Uri.fromFile(it) } ?: source
                PdfDoc(r, pfd!!, info, file, uri, app, if (decrypted) null else password, count, first)
                    .also { it.readSizes() }
            }
    }
}
