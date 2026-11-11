package com.tools.app.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AColor
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.util.LruCache
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.util.Matrix
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tools.app.Storage
import com.tools.app.ui.Images
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Marks drawn on a page. Points are normalised (0..1) in the page's *base* orientation (its own rotation, no edits). */
sealed interface Annot {
    /** [width] is a fraction of the base page width. */
    data class Stroke(val pts: List<Offset>, val color: Int, val width: Float) : Annot
    /** [at] is the baseline start; [size] a fraction of the base page width; [rot] the edit rotation it was typed under. */
    data class Label(val at: Offset, val text: String, val color: Int, val size: Float, val rot: Int) : Annot
}

class PdfSource(val id: Int, val file: File, val pageCount: Int) {
    private val lock = Mutex()
    private var pfd: ParcelFileDescriptor? = null
    private var renderer: PdfRenderer? = null

    /** Renders page [index] at [width] px wide on a white background; null if the page can't be rendered. */
    suspend fun render(index: Int, width: Int): Bitmap? = lock.withLock {
        withContext(Dispatchers.IO) {
            try {
                val r = renderer ?: run {
                    val p = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                    pfd = p
                    PdfRenderer(p).also { renderer = it }
                }
                r.openPage(index).use { page ->
                    val h = (width.toFloat() * page.height / page.width).toInt().coerceIn(1, width * 3)
                    val bmp = Bitmap.createBitmap(width, h, Bitmap.Config.ARGB_8888)
                    bmp.eraseColor(AColor.WHITE)
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bmp
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                null
            }
        }
    }

    suspend fun close() = lock.withLock {
        runCatching { renderer?.close() }; runCatching { pfd?.close() }
        renderer = null; pfd = null
    }
}

data class PageItem(
    val id: Long,
    val src: PdfSource,
    val index: Int,
    /** Clockwise degrees added by the user (unbounded so the thumbnail animates the short way). */
    val rot: Int = 0,
    val annots: List<Annot> = emptyList(),
)

object Thumbs {
    const val WIDTH = 300
    private val cache = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }
    private fun key(s: PdfSource, i: Int) = "${s.id}:$i"
    fun get(s: PdfSource, i: Int): Bitmap? = cache.get(key(s, i))
    suspend fun load(s: PdfSource, i: Int): Bitmap? =
        get(s, i) ?: s.render(i, WIDTH)?.also { cache.put(key(s, i), it) }
    fun clear() = cache.evictAll()
}

class PasswordPrompt(val name: String, val wrong: Boolean, val result: CompletableDeferred<String?>)
class Notice(val text: String, val error: Boolean, val needsAccess: Boolean = false)

sealed interface ExportState {
    data object Idle : ExportState
    data class Running(val progress: Float, val label: String) : ExportState
    class Done(val message: String, val share: File?) : ExportState { var consumed = false }
    data class Failed(val message: String, val needsAccess: Boolean = false) : ExportState
}

object PdfSession {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var nextSource = 1
    private var nextPage = 1L
    private var fileCounter = 0

    var name by mutableStateOf<String?>(null); private set
    val pages = mutableStateListOf<PageItem>()
    var selected by mutableStateOf<Set<Long>>(emptySet()); private set
    var busy by mutableStateOf<String?>(null); private set
    var notice by mutableStateOf<Notice?>(null)
    var passwordPrompt by mutableStateOf<PasswordPrompt?>(null); private set
    /** Password the saved copy will be encrypted with (null = none). */
    var newPassword by mutableStateOf<String?>(null)
    var hadPassword by mutableStateOf(false); private set
    /** Signature kept for this session: strokes normalised to its own box, and the box's width / height. */
    var signature by mutableStateOf<List<List<Offset>>?>(null)
    var signatureAspect by mutableStateOf(2f)
    val export = MutableStateFlow<ExportState>(ExportState.Idle)
    private var exportJob: Job? = null

    private fun dir(ctx: Context) = File(ctx.cacheDir, "pdfedit").apply { mkdirs() }
    private fun newFile(ctx: Context, prefix: String) = File(dir(ctx), "${prefix}_${++fileCounter}.pdf")

    // ---------- selection / page operations ----------
    fun toggle(id: Long) { selected = if (id in selected) selected - id else selected + id }
    fun selectAll() { selected = if (selected.size == pages.size) emptySet() else pages.map { it.id }.toSet() }
    fun clearSelection() { selected = emptySet() }

    fun rotate(delta: Int) {
        for (i in pages.indices) if (pages[i].id in selected) pages[i] = pages[i].copy(rot = pages[i].rot + delta)
    }

    fun deleteSelected() {
        pages.removeAll { it.id in selected }
        selected = emptySet()
        if (pages.isEmpty()) close()
    }

    fun moveSelected(earlier: Boolean) {
        if (earlier) {
            for (i in 1 until pages.size) if (pages[i].id in selected && pages[i - 1].id !in selected) pages.add(i - 1, pages.removeAt(i))
        } else {
            for (i in pages.size - 2 downTo 0) if (pages[i].id in selected && pages[i + 1].id !in selected) pages.add(i + 1, pages.removeAt(i))
        }
    }

    fun setAnnots(id: Long, annots: List<Annot>) {
        val i = pages.indexOfFirst { it.id == id }
        if (i >= 0) pages[i] = pages[i].copy(annots = annots)
    }

    fun close() {
        val old = pages.map { it.src }.distinct()
        pages.clear(); selected = emptySet(); name = null; newPassword = null; hadPassword = false
        notice = null; export.value = ExportState.Idle
        Thumbs.clear()
        scope.launch { old.forEach { it.close() } }
    }

    // ---------- opening ----------
    fun open(context: Context, uris: List<Uri>, append: Boolean) {
        if (uris.isEmpty() || busy != null) return
        val app = context.applicationContext
        scope.launch {
            busy = "Opening…"; notice = null
            try {
                var replace = !append
                for (uri in uris) if (importPdf(app, uri, replace)) replace = false
            } finally { busy = null }
        }
    }

    private suspend fun importPdf(app: Context, uri: Uri, replace: Boolean): Boolean {
        PDFBoxResourceLoader.init(app)
        val display = displayName(app, uri) ?: "document.pdf"
        val raw = newFile(app, "in")
        try {
            withContext(Dispatchers.IO) {
                val input = app.contentResolver.openInputStream(uri) ?: throw IOException("no stream")
                input.use { i -> raw.outputStream().use { o -> i.copyTo(o) } }
            }
        } catch (e: SecurityException) {
            notice = Notice("Can't access \"$display\". Pick it again from the file picker.", true); return false
        } catch (e: Exception) {
            notice = Notice("Couldn't read \"$display\".", true); return false
        }
        var password = ""
        var usable = raw
        var count = 0
        var wasEncrypted = false
        while (true) {
            try {
                withContext(Dispatchers.IO) {
                    PDDocument.load(raw, password, MemoryUsageSetting.setupTempFileOnly()).use { doc ->
                        count = doc.numberOfPages
                        if (doc.isEncrypted) {
                            wasEncrypted = true
                            val plain = newFile(app, "dec")
                            doc.setAllSecurityToBeRemoved(true)
                            doc.save(plain)
                            usable = plain
                        }
                    }
                }
                break
            } catch (e: InvalidPasswordException) {
                password = askPassword(display, password.isNotEmpty()) ?: run { raw.delete(); return false }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                raw.delete()
                notice = Notice("\"$display\" doesn't look like a valid PDF, or it's damaged.", true); return false
            }
        }
        if (count == 0) { notice = Notice("\"$display\" has no pages.", true); return false }
        if (replace) { close() }
        val src = PdfSource(nextSource++, usable, count)
        if (name == null) name = display
        if (wasEncrypted) hadPassword = true
        pages.addAll((0 until count).map { PageItem(nextPage++, src, it) })
        return true
    }

    private suspend fun askPassword(name: String, wrong: Boolean): String? {
        val d = CompletableDeferred<String?>()
        passwordPrompt = PasswordPrompt(name, wrong, d)
        return try { d.await() } finally { passwordPrompt = null }
    }

    fun answerPassword(value: String?) { passwordPrompt?.result?.complete(value) }

    private fun displayName(ctx: Context, uri: Uri): String? = runCatching {
        ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        }
    }.getOrNull()

    // ---------- images -> pages ----------
    fun addImages(context: Context, uris: List<Uri>, fitA4: Boolean) {
        if (uris.isEmpty() || busy != null) return
        val app = context.applicationContext
        scope.launch {
            busy = "Adding images…"; notice = null
            try {
                PDFBoxResourceLoader.init(app)
                val out = newFile(app, "img")
                var added = 0
                withContext(Dispatchers.IO) {
                    PDDocument().use { doc ->
                        for (u in uris) {
                            try {
                                val bmp = Images.load(app, u, if (fitA4) 2480 else 3000)
                                val flat = Bitmap.createBitmap(bmp.width, bmp.height, Bitmap.Config.ARGB_8888)
                                Canvas(flat).apply { drawColor(AColor.WHITE); drawBitmap(bmp, 0f, 0f, null) }
                                bmp.recycle()
                                val img = JPEGFactory.createFromImage(doc, flat, 0.88f)
                                val iw = flat.width.toFloat(); val ih = flat.height.toFloat()
                                flat.recycle()
                                val box = if (fitA4) (if (iw > ih) PDRectangle(PDRectangle.A4.height, PDRectangle.A4.width) else PDRectangle.A4)
                                else { val s = min(1f, 2000f / maxOf(iw, ih)); PDRectangle(iw * s, ih * s) }
                                val page = PDPage(box); doc.addPage(page)
                                val s = min(box.width / iw, box.height / ih)
                                val w = iw * s; val h = ih * s
                                PDPageContentStream(doc, page).use { it.drawImage(img, (box.width - w) / 2, (box.height - h) / 2, w, h) }
                                added++
                            } catch (e: CancellationException) { throw e } catch (e: Throwable) { /* skip unreadable image */ }
                        }
                        if (added > 0) doc.save(out)
                    }
                }
                if (added == 0) { notice = Notice("Couldn't read those images.", true); return@launch }
                val src = PdfSource(nextSource++, out, added)
                if (name == null) name = "Images.pdf"
                pages.addAll((0 until added).map { PageItem(nextPage++, src, it) })
                if (added < uris.size) notice = Notice("${uris.size - added} image(s) couldn't be read.", true)
            } catch (e: Throwable) {
                notice = Notice("Couldn't add the images.", true)
            } finally { busy = null }
        }
    }

    // ---------- export ----------
    fun cancelExport() { exportJob?.cancel(); export.value = ExportState.Idle }

    fun export(context: Context, items: List<PageItem>, suffix: String, share: Boolean) {
        if (items.isEmpty() || export.value is ExportState.Running) return
        val app = context.applicationContext
        val base = (name ?: "document").removeSuffix(".pdf").removeSuffix(".PDF")
        val fileName = Storage.safe("$base ($suffix).pdf")
        val password = newPassword
        exportJob = scope.launch {
            export.value = ExportState.Running(0f, if (share) "Preparing…" else "Saving…")
            val tmp = newFile(app, "out")
            try {
                withContext(Dispatchers.IO) {
                    PDFBoxResourceLoader.init(app)
                    buildPdf(items, password, tmp) { export.value = ExportState.Running(it, if (share) "Preparing…" else "Saving…") }
                }
                if (share) {
                    val shareDir = File(app.cacheDir, "qr").apply { mkdirs() }
                    val f = File(shareDir, fileName)
                    withContext(Dispatchers.IO) { tmp.copyTo(f, overwrite = true); tmp.delete() }
                    export.value = ExportState.Done("Ready to share", f)
                } else {
                    val saved = withContext(Dispatchers.IO) { Storage.saveFile(app, Storage.Folder.Documents, null, fileName, tmp).also { tmp.delete() } }
                    export.value = ExportState.Done("Saved to ${Storage.label(Storage.Folder.Documents)} · $saved", null)
                }
            } catch (e: CancellationException) {
                tmp.delete(); throw e
            } catch (e: Storage.NoAccess) {
                tmp.delete(); export.value = ExportState.Failed(e.message ?: "No access to storage", true)
            } catch (e: Throwable) {
                tmp.delete()
                export.value = ExportState.Failed("Couldn't write the PDF. ${if (e is OutOfMemoryError) "The file is too large for this device." else "Try again with fewer pages."}")
            }
        }
    }

    private fun buildPdf(items: List<PageItem>, password: String?, out: File, progress: (Float) -> Unit) {
        val opened = HashMap<Int, PDDocument>()
        try {
            PDDocument().use { target ->
                items.forEachIndexed { n, it ->
                    val srcDoc = opened.getOrPut(it.src.id) { PDDocument.load(it.src.file, MemoryUsageSetting.setupTempFileOnly()) }
                    val srcPage = srcDoc.getPage(it.index)
                    val page = target.importPage(srcPage)
                    page.mediaBox = srcPage.mediaBox
                    page.cropBox = srcPage.cropBox
                    val base = norm(srcPage.rotation)
                    page.rotation = base
                    if (it.annots.isNotEmpty()) stamp(target, page, it.annots)
                    page.rotation = norm(base + it.rot)
                    progress((n + 1f) / items.size * 0.95f)
                }
                if (!password.isNullOrEmpty()) {
                    val policy = StandardProtectionPolicy(password, password, AccessPermission())
                    policy.encryptionKeyLength = 256
                    policy.setPreferAES(true)
                    target.protect(policy)
                }
                target.save(out)
            }
        } finally {
            opened.values.forEach { runCatching { it.close() } }
        }
        progress(1f)
    }

    private fun norm(deg: Int) = ((deg % 360) + 360) % 360

    private fun stamp(doc: PDDocument, page: PDPage, annots: List<Annot>) {
        val box = page.cropBox
        val w = box.width; val h = box.height
        val ri = norm(page.rotation)
        val baseW = if (ri % 180 != 0) h else w
        fun map(p: Offset): Pair<Float, Float> {
            val a: Float; val b: Float
            when (ri) {
                90 -> { a = p.y; b = 1f - p.x }
                180 -> { a = 1f - p.x; b = 1f - p.y }
                270 -> { a = 1f - p.y; b = p.x }
                else -> { a = p.x; b = p.y }
            }
            return box.lowerLeftX + a * w to box.lowerLeftY + (1f - b) * h
        }
        val font = PDType1Font.HELVETICA
        PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
            for (a in annots) when (a) {
                is Annot.Stroke -> {
                    if (a.pts.isEmpty()) continue
                    cs.setStrokingColor(AColor.red(a.color), AColor.green(a.color), AColor.blue(a.color))
                    cs.setLineWidth((a.width * baseW).coerceAtLeast(0.3f))
                    cs.setLineCapStyle(1); cs.setLineJoinStyle(1)
                    val first = map(a.pts[0])
                    cs.moveTo(first.first, first.second)
                    if (a.pts.size == 1) cs.lineTo(first.first + 0.05f, first.second)
                    for (i in 1 until a.pts.size) { val q = map(a.pts[i]); cs.lineTo(q.first, q.second) }
                    cs.stroke()
                }
                is Annot.Label -> {
                    val size = (a.size * baseW).coerceAtLeast(4f)
                    val theta = Math.toRadians(((ri + a.rot) % 360 + 360) % 360.0)
                    val (x, y) = map(a.at)
                    cs.setNonStrokingColor(AColor.red(a.color), AColor.green(a.color), AColor.blue(a.color))
                    a.text.lines().forEachIndexed { k, raw ->
                        val line = raw.map { ch -> if (runCatching { font.getStringWidth(ch.toString()) }.isSuccess) ch else '?' }.joinToString("")
                        if (line.isBlank()) return@forEachIndexed
                        val lh = size * 1.2f * k
                        val lx = x + (lh * sin(theta)).toFloat(); val ly = y - (lh * cos(theta)).toFloat()
                        cs.beginText()
                        cs.setFont(font, size)
                        cs.setTextMatrix(Matrix.getRotateInstance(theta, lx, ly))
                        cs.showText(line)
                        cs.endText()
                    }
                }
            }
        }
    }
}

/** Rotates a normalised point from the base orientation into a view rotated clockwise by [deg]. */
fun rotNorm(p: Offset, deg: Int): Offset = when (((deg % 360) + 360) % 360) {
    90 -> Offset(1f - p.y, p.x)
    180 -> Offset(1f - p.x, 1f - p.y)
    270 -> Offset(p.y, 1f - p.x)
    else -> p
}
