package com.tools.app.print

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tools.app.ui.Images
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.coroutines.coroutineContext
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Everything that turns the user's pages into bytes the printer understands. All blocking; call from a worker thread. */
object PrintRender {

    /** One open PdfRenderer per source file during a job. */
    private class Sources(val files: List<PrintFile>) : AutoCloseable {
        private val renderers = HashMap<Int, Pair<PdfRenderer, ParcelFileDescriptor>>()
        fun renderer(i: Int): PdfRenderer = renderers.getOrPut(i) {
            val fd = ParcelFileDescriptor.open(File(files[i].path), ParcelFileDescriptor.MODE_READ_ONLY)
            PdfRenderer(fd) to fd
        }.first
        override fun close() { renderers.values.forEach { (r, fd) -> runCatching { r.close() }; runCatching { fd.close() } }; renderers.clear() }
    }

    // ---- preview ----------------------------------------------------------------------------------------------------

    private val previewLock = Any()

    /** Renders the content of [page] (without sheet and margins) so its width is about [targetW] px. */
    fun previewBitmap(context: Context, files: List<PrintFile>, page: PrintPage, targetW: Int): Bitmap? = try {
        val f = files[page.fileIndex]
        if (page.isPdf) synchronized(previewLock) {
            val fd = ParcelFileDescriptor.open(File(f.path), ParcelFileDescriptor.MODE_READ_ONLY)
            val r = PdfRenderer(fd)
            try {
                val p = r.openPage(page.index)
                try {
                    val w = targetW.coerceIn(100, 1600)
                    val h = (w * p.height.toFloat() / p.width).roundToInt().coerceAtLeast(1)
                    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    bmp.eraseColor(Color.WHITE)
                    p.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bmp
                } finally { p.close() }
            } finally { runCatching { r.close() }; runCatching { fd.close() } }
        } else Images.load(context, Uri.fromFile(File(f.path)), targetW.coerceIn(100, 1600))
    } catch (_: Throwable) { null }

    // ---- page content drawing (shared by raster / PDF building) ---------------------------------------------------------

    /** Loads the bitmap of an image page for printing, at most [maxSide] px on its long side. */
    private fun loadImage(context: Context, f: PrintFile, maxSide: Int): Bitmap = Images.load(context, Uri.fromFile(File(f.path)), maxSide)

    // ---- PDF assembly -----------------------------------------------------------------------------------------------

    /**
     * Builds one PDF with [pages] in order (images fitted to [paper] with margins), for printers that take application/pdf
     * when the user's selection is not simply "this one PDF, all pages".
     */
    suspend fun buildPdf(
        context: Context, files: List<PrintFile>, pages: List<PrintPage>, paper: Paper, opts: PrintOptions,
        out: File, progress: (Float) -> Unit,
    ) {
        val anyPdf = pages.any { it.isPdf }
        if (!anyPdf) {
            val doc = PdfDocument()
            try {
                pages.forEachIndexed { i, pg ->
                    coroutineContext.ensureActive()
                    drawImagePage(context, doc, files[pg.fileIndex], pg, paper, opts, i)
                    progress((i + 1f) / pages.size)
                }
                BufferedOutputStream(FileOutputStream(out)).use { doc.writeTo(it) }
            } finally { doc.close() }
            return
        }
        PDFBoxResourceLoader.init(context.applicationContext)
        val result = PDDocument()
        val open = ArrayList<PDDocument>()
        val temps = ArrayList<File>()
        try {
            val srcs = HashMap<Int, PDDocument>()
            pages.forEachIndexed { i, pg ->
                coroutineContext.ensureActive()
                if (pg.isPdf) {
                    val src = srcs.getOrPut(pg.fileIndex) { PDDocument.load(File(files[pg.fileIndex].path)).also { open += it } }
                    result.importPage(src.getPage(pg.index))
                } else {
                    val tmp = File(out.parentFile, "img-$i.pdf").also { temps += it }
                    val d = PdfDocument()
                    try {
                        drawImagePage(context, d, files[pg.fileIndex], pg, paper, opts, 0)
                        FileOutputStream(tmp).use { d.writeTo(it) }
                    } finally { d.close() }
                    val src = PDDocument.load(tmp).also { open += it }
                    result.importPage(src.getPage(0))
                }
                progress((i + 1f) / pages.size * 0.9f)
            }
            result.save(out)
            progress(1f)
        } finally {
            runCatching { result.close() }
            open.forEach { runCatching { it.close() } }
            temps.forEach { it.delete() }
        }
    }

    private fun drawImagePage(context: Context, doc: PdfDocument, f: PrintFile, pg: PrintPage, paper: Paper, opts: PrintOptions, number: Int) {
        val land = PrintModels.landscape(opts.orientation, pg)
        val sw = if (land) max(paper.wPt, paper.hPt) else min(paper.wPt, paper.hPt)
        val sh = if (land) min(paper.wPt, paper.hPt) else max(paper.wPt, paper.hPt)
        val info = PdfDocument.PageInfo.Builder(sw.roundToInt(), sh.roundToInt(), number + 1).create()
        val page = doc.startPage(info)
        val bmp = loadImage(context, f, 3600)
        val pl = PrintModels.place(sw, sh, PrintModels.MARGIN_PT, pg.w, pg.h, opts.fit, PrintModels.naturalScale(pg))
        val m = Matrix()
        m.postScale(pg.w / bmp.width, pg.h / bmp.height)
        m.postScale(pl.scale, pl.scale)
        m.postTranslate(pl.x, pl.y)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        if (!opts.color) paint.colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) })
        page.canvas.drawColor(Color.WHITE)
        page.canvas.drawBitmap(bmp, m, paint)
        doc.finishPage(page)
        bmp.recycle()
    }

    // ---- raster -------------------------------------------------------------------------------------------------------

    class RasterSpec(
        val kind: PrintModels.RasterKind,
        val dpi: Int,
        val gray: Boolean,
        val duplex: Boolean,
        val tumble: Boolean,
        val quality: Int,
        /** pwg-raster-document-sheet-back: normal / flipped / rotated / manual-tumble (null = normal). */
        val sheetBack: String?,
        /** Repeat the whole page list this many times (printers that can't do copies for us). */
        val repeat: Int,
        val pageSizeName: String,
        val iosCopies: Int = 1,
    )

    /**
     * Renders [pages] page by page (in bands, so memory stays low) and writes a PWG raster or URF stream to [out].
     * Returns the number of rendered sheets (pages).
     */
    suspend fun buildRaster(
        context: Context, files: List<PrintFile>, pages: List<PrintPage>, paper: Paper, opts: PrintOptions, spec: RasterSpec,
        out: File, progress: (Float) -> Unit,
    ): Int {
        // Page sequence: selected pages x repeat, plus a blank back side if a copy would end on a front side in duplex.
        val seq = ArrayList<PrintPage?>()
        repeat(spec.repeat.coerceAtLeast(1)) {
            seq += pages
            if (spec.duplex && pages.size % 2 == 1 && spec.repeat > 1) seq += null
        }
        val portraitW = min(paper.wPt, paper.hPt); val portraitH = max(paper.wPt, paper.hPt)
        val devW = (portraitW / 72f * spec.dpi).roundToInt()
        val devH = (portraitH / 72f * spec.dpi).roundToInt()
        val bandH = max(16, min(devH, (12_000_000 / (devW * 4)).coerceAtLeast(16)))
        val band = Bitmap.createBitmap(devW, bandH, Bitmap.Config.ARGB_8888)
        val srcs = Sources(files)
        val os = BufferedOutputStream(FileOutputStream(out), 256 * 1024)
        try {
            if (spec.kind == PrintModels.RasterKind.Pwg) os.write(Raster.pwgFileHeader())
            else os.write(Raster.urfFileHeader(seq.size))
            val bpp = if (spec.gray) 1 else 3
            val row = ByteArray(devW * bpp)
            val px = IntArray(devW * bandH)
            seq.forEachIndexed { n, pg ->
                coroutineContext.ensureActive()
                val back = spec.duplex && n % 2 == 1
                var flipX = false; var flipY = false
                if (back) when (spec.sheetBack) {
                    "rotated" -> { flipX = true; flipY = true }
                    "flipped" -> if (spec.tumble) flipX = true else flipY = true
                    "manual-tumble" -> if (spec.tumble) { flipX = true; flipY = true }
                }
                val page = Raster.Page(
                    width = devW, height = devH, dpi = spec.dpi, gray = spec.gray, duplex = spec.duplex, tumble = spec.tumble,
                    quality = if (spec.quality == 0) 3 else if (spec.quality == 2) 5 else 4, copies = 1, totalPages = seq.size,
                    pageSizeNamePwg = spec.pageSizeName,
                    crossFeedTransform = if (flipX) -1 else 1, feedTransform = if (flipY) -1 else 1,
                )
                // The sheet already contains the flip, so the header stays "normal" for printers that expect pre-transformed data.
                val header = if (spec.kind == PrintModels.RasterKind.Pwg) Raster.pwgPageHeader(page) else Raster.urfPageHeader(page)
                os.write(header)
                val lw = Raster.LineWriter(os, bpp, devW)

                // Page content setup
                var pdfPage: PdfRenderer.Page? = null
                var image: Bitmap? = null
                var matrix = Matrix()
                if (pg != null) {
                    val land = PrintModels.landscape(opts.orientation, pg)
                    val sw = if (land) portraitH else portraitW
                    val sh = if (land) portraitW else portraitH
                    val pl = PrintModels.place(sw, sh, PrintModels.MARGIN_PT, pg.w, pg.h, opts.fit, PrintModels.naturalScale(pg))
                    val k = spec.dpi / 72f
                    if (pg.isPdf) pdfPage = srcs.renderer(pg.fileIndex).openPage(pg.index)
                    else {
                        image = loadImage(context, files[pg.fileIndex], min(max(devW, devH), 5000))
                        matrix.postScale(pg.w / image.width, pg.h / image.height)
                    }
                    matrix.postScale(pl.scale, pl.scale)
                    matrix.postTranslate(pl.x, pl.y)
                    matrix.postScale(k, k)
                    if (land) { matrix.postRotate(90f); matrix.postTranslate(devW.toFloat(), 0f) }
                    if (flipX) matrix.postScale(-1f, 1f, devW / 2f, 0f)
                    if (flipY) matrix.postScale(1f, -1f, 0f, devH / 2f)
                }
                try {
                    var top = 0
                    while (top < devH) {
                        coroutineContext.ensureActive()
                        val rows = min(bandH, devH - top)
                        band.eraseColor(Color.WHITE)
                        if (pg != null) {
                            val m = Matrix(matrix).apply { postTranslate(0f, -top.toFloat()) }
                            if (pdfPage != null) pdfPage.render(band, null, m, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                            else {
                                val c = Canvas(band)
                                c.drawBitmap(image!!, m, Paint(Paint.FILTER_BITMAP_FLAG))
                            }
                        }
                        band.getPixels(px, 0, devW, 0, 0, devW, rows)
                        for (y in 0 until rows) {
                            val o = y * devW
                            if (spec.gray) {
                                for (x in 0 until devW) {
                                    val p = px[o + x]
                                    row[x] = ((((p shr 16) and 255) * 77 + ((p shr 8) and 255) * 151 + (p and 255) * 28) shr 8).toByte()
                                }
                            } else {
                                var j = 0
                                for (x in 0 until devW) {
                                    val p = px[o + x]
                                    row[j++] = (p shr 16).toByte(); row[j++] = (p shr 8).toByte(); row[j++] = p.toByte()
                                }
                            }
                            lw.addLine(row)
                        }
                        top += rows
                    }
                    lw.finish()
                } finally {
                    pdfPage?.close(); image?.recycle()
                }
                progress((n + 1f) / seq.size)
            }
            os.flush()
        } finally {
            os.close(); band.recycle(); srcs.close()
        }
        return seq.size
    }

    // ---- JPEG (one job per page) ----------------------------------------------------------------------------------------

    /** Renders a single page fitted onto the paper at [dpi] and writes it as a JPEG. */
    suspend fun buildJpeg(context: Context, files: List<PrintFile>, pg: PrintPage, paper: Paper, opts: PrintOptions, dpi: Int, out: File) {
        coroutineContext.ensureActive()
        val land = PrintModels.landscape(opts.orientation, pg)
        val sw = if (land) max(paper.wPt, paper.hPt) else min(paper.wPt, paper.hPt)
        val sh = if (land) min(paper.wPt, paper.hPt) else max(paper.wPt, paper.hPt)
        val devW = (sw / 72f * dpi).roundToInt(); val devH = (sh / 72f * dpi).roundToInt()
        val bmp = Bitmap.createBitmap(devW, devH, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(Color.WHITE)
        val pl = PrintModels.place(sw, sh, PrintModels.MARGIN_PT, pg.w, pg.h, opts.fit, PrintModels.naturalScale(pg))
        val k = dpi / 72f
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        if (!opts.color) paint.colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) })
        val srcs = Sources(files)
        try {
            if (pg.isPdf) {
                val p = srcs.renderer(pg.fileIndex).openPage(pg.index)
                try {
                    val m = Matrix().apply { postScale(pl.scale * k, pl.scale * k); postTranslate(pl.x * k, pl.y * k) }
                    p.render(bmp, null, m, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                } finally { p.close() }
            } else {
                val img = loadImage(context, files[pg.fileIndex], min(max(devW, devH), 5000))
                val m = Matrix().apply {
                    postScale(pg.w / img.width, pg.h / img.height)
                    postScale(pl.scale * k, pl.scale * k); postTranslate(pl.x * k, pl.y * k)
                }
                Canvas(bmp).drawBitmap(img, m, paint)
                img.recycle()
            }
        } finally { srcs.close() }
        FileOutputStream(out).use { bmp.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        bmp.recycle()
    }
}
