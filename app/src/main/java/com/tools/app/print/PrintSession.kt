package com.tools.app.print

import com.tools.app.R
import com.tools.app.ui.t
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.LruCache
import com.tools.app.ui.Images
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

data class DocState(
    val files: List<PrintFile> = emptyList(),
    val pages: List<PrintPage> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
)

data class PrinterState(
    val address: PrinterAddress? = null,
    val name: String = "",
    val model: String = "",
    val uuid: String? = null,
    val caps: PrinterCaps? = null,
    val loading: Boolean = false,
    val error: String? = null,
)

enum class JobPhase { Idle, Preparing, Sending, Printing, Done, Failed }

data class JobState(val phase: JobPhase = JobPhase.Idle, val progress: Float = 0f, val text: String = "") {
    val active get() = phase == JobPhase.Preparing || phase == JobPhase.Sending || phase == JobPhase.Printing
}

sealed interface AddState {
    data object Idle : AddState
    data object Trying : AddState
    data class Failed(val message: String) : AddState
}

/**
 * The loaded files, chosen printer, options and the running job. A singleton with its own scope so leaving the page
 * doesn't stop a print.
 */
object PrintSession {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var app: Context

    private val _doc = MutableStateFlow(DocState())
    val doc: StateFlow<DocState> = _doc
    private val _selected = MutableStateFlow<Set<Int>>(emptySet())
    val selected: StateFlow<Set<Int>> = _selected
    private val _options = MutableStateFlow(PrintOptions())
    val options: StateFlow<PrintOptions> = _options
    private val _printer = MutableStateFlow(PrinterState())
    val printer: StateFlow<PrinterState> = _printer
    private val _job = MutableStateFlow(JobState())
    val job: StateFlow<JobState> = _job
    private val _add = MutableStateFlow<AddState>(AddState.Idle)
    val addState: StateFlow<AddState> = _add

    private var inited = false
    private var loadJob: Job? = null
    private var printJob: Job? = null
    private var probeJob: Job? = null
    @Volatile private var currentJobId: Int? = null
    @Volatile private var currentAddress: PrinterAddress? = null
    private var fileCounter = 0

    // ---- setup ------------------------------------------------------------------------------------------------------

    private fun prefs() = app.getSharedPreferences("print", Context.MODE_PRIVATE)

    @Synchronized
    fun init(context: Context) {
        if (inited) return
        app = context.applicationContext
        inited = true
        val p = prefs()
        val host = p.getString("host", null)
        if (host != null) {
            val addr = PrinterAddress(host, p.getInt("port", 631), p.getString("path", "ipp/print") ?: "ipp/print", p.getBoolean("tls", false))
            _printer.value = PrinterState(addr, p.getString("name", "") ?: "", p.getString("model", "") ?: "", p.getString("uuid", null))
            refreshPrinter()
        }
    }

    /** Called by MainActivity: files from the share sheet or the reader. Replaces what was loaded before. */
    fun open(context: Context, uris: List<Uri>) {
        init(context)
        clear()
        add(context, uris)
    }

    fun clear() {
        loadJob?.cancel()
        loadJob = null
        _doc.value = DocState()
        _selected.value = emptySet()
        previews.evictAll()
        if (!_job.value.active) _job.value = JobState()
        // Chained before any later import (a fire-and-forget delete could wipe a file that is being copied in).
        val prev = loadJob
        loadJob = scope.launch(Dispatchers.IO) { prev?.join(); File(app.cacheDir, "print/docs").deleteRecursively() }
    }

    /** Copies the picked files into the cache and reads their pages. */
    fun add(context: Context, uris: List<Uri>) {
        init(context)
        if (uris.isEmpty()) return
        val startFiles = _doc.value
        _doc.update { it.copy(loading = true, error = null) }
        val prev = loadJob
        loadJob = scope.launch(Dispatchers.IO) {
            prev?.join()
            val files = startFiles.files.toMutableList()
            val pages = _doc.value.pages.toMutableList()
            var error: String? = null
            for (uri in uris) {
                try {
                    val f = importOne(uri)
                    val idx = files.size
                    files += f.first
                    pages += f.second.map { PrintPage(idx, it.index, it.w, it.h, it.isPdf) }
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) { error = e.message ?: t(R.string.print_couldn_t_open_that_file) }
            }
            val firstNew = _doc.value.pages.size
            _doc.update { DocState(files, pages, false, error) }
            // New pages are selected; previously chosen ones stay as they were.
            _selected.update { it + (firstNew until pages.size) }
        }
    }

    private class PageDim(val index: Int, val w: Float, val h: Float, val isPdf: Boolean)

    private fun importOne(uri: Uri): Pair<PrintFile, List<PageDim>> {
        var name = "Document"
        try {
            app.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0)?.let { n -> name = n }
            }
        } catch (_: Exception) { }
        // file:// URIs have no display name to query.
        if (name == "Document") uri.lastPathSegment?.takeIf { it.isNotBlank() }?.let { name = it }
        val dir = File(app.cacheDir, "print/docs").apply { mkdirs() }
        val dest = File(dir, "${fileCounter++}-" + name.replace(Regex("[^A-Za-z0-9._ -]"), "_").takeLast(80))
        val input = app.contentResolver.openInputStream(uri) ?: throw IllegalStateException(t(R.string.print_couldn_t_open, name))
        input.use { i -> dest.outputStream().use { o -> i.copyTo(o) } }
        val isPdf = dest.inputStream().use { s -> val b = ByteArray(1024); val n = s.read(b); String(b, 0, maxOf(n, 0), Charsets.ISO_8859_1).contains("%PDF-") }
        if (isPdf) {
            val fd = ParcelFileDescriptor.open(dest, ParcelFileDescriptor.MODE_READ_ONLY)
            val r = try { PdfRenderer(fd) } catch (e: SecurityException) { fd.close(); dest.delete(); throw IllegalStateException(t(R.string.print_is_password_protected, name)) }
            catch (e: Exception) { fd.close(); dest.delete(); throw IllegalStateException(t(R.string.print_couldn_t_read, name)) }
            try {
                val dims = (0 until r.pageCount).map { i ->
                    val p = r.openPage(i)
                    try { PageDim(i, p.width.toFloat(), p.height.toFloat(), true) } finally { p.close() }
                }
                if (dims.isEmpty()) throw IllegalStateException(t(R.string.print_has_no_pages, name))
                return PrintFile(name, dest.absolutePath, true, dims.size) to dims
            } finally { runCatching { r.close() }; runCatching { fd.close() } }
        }
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(dest.absolutePath, o)
        var w = o.outWidth; var h = o.outHeight
        if (w <= 0 || h <= 0) {
            val bmp = try { Images.load(app, Uri.fromFile(dest), 1600) } catch (e: Exception) { dest.delete(); throw IllegalStateException(t(R.string.print_isn_t_a_pdf_or_an_image_i_can_print, name)) }
            w = bmp.width; h = bmp.height
        } else {
            val rot = try { android.media.ExifInterface(dest.absolutePath).getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, 1) } catch (_: Exception) { 1 }
            if (rot in 5..8) { val t = w; w = h; h = t }
        }
        return PrintFile(name, dest.absolutePath, false, 1) to listOf(PageDim(0, w.toFloat(), h.toFloat(), false))
    }

    // ---- selection & options ----------------------------------------------------------------------------------------

    fun togglePage(i: Int) = _selected.update { if (i in it) it - i else it + i }
    fun selectAll() { _selected.value = _doc.value.pages.indices.toSet() }
    fun selectRange(text: String): Boolean {
        val s = PrintModels.parseRanges(text, _doc.value.pages.size) ?: return false
        _selected.value = s
        return true
    }
    fun removeFile(index: Int) {
        // Rebuilds the document without one source file; page selection resets to "all".
        val d = _doc.value
        val files = d.files.filterIndexed { i, _ -> i != index }
        val pages = d.pages.filter { it.fileIndex != index }.map { if (it.fileIndex > index) PrintPage(it.fileIndex - 1, it.index, it.w, it.h, it.isPdf) else it }
        File(d.files[index].path).delete()
        previews.evictAll()
        _doc.value = d.copy(files = files, pages = pages)
        _selected.value = pages.indices.toSet()
    }
    fun setOptions(f: (PrintOptions) -> PrintOptions) = _options.update(f)

    // ---- printer ----------------------------------------------------------------------------------------------------

    fun selectPrinter(found: FoundPrinter) = select(found.address, found.name, found.model.ifBlank { found.name }, found.uuid)

    private fun select(addr: PrinterAddress, name: String, model: String, uuid: String? = null, caps: PrinterCaps? = null) {
        _printer.value = PrinterState(addr, name, model, uuid, caps)
        prefs().edit().putString("host", addr.host).putInt("port", addr.port).putString("path", addr.path).putBoolean("tls", addr.tls)
            .putString("name", name).putString("model", model).putString("uuid", uuid).apply()
        _options.update { it.copy(media = null) }
        refreshPrinter()
    }

    /** Re-reads the printer's attributes (status, toner, capabilities). Safe to call often. */
    fun refreshPrinter() {
        val st = _printer.value
        val addr = st.address ?: return
        if (_printer.value.loading) return
        _printer.update { it.copy(loading = true) }
        scope.launch(Dispatchers.IO) {
            try {
                val caps = IppClient.getPrinterAttributes(app, addr)
                _printer.update {
                    if (it.address != addr) it else it.copy(
                        caps = caps, loading = false, error = null,
                        model = caps.makeModel.ifBlank { it.model }, name = it.name.ifBlank { caps.name },
                    )
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                _printer.update { if (it.address != addr) it else it.copy(loading = false, error = friendly(e)) }
            }
        }
    }

    /** If the remembered printer can't be reached but mDNS finds the same device at another address, follow it. */
    fun onDiscovered(list: List<FoundPrinter>) {
        val st = _printer.value
        if (st.address == null) return
        if (st.error == null || st.loading) return
        val same = list.firstOrNull { (st.uuid != null && it.uuid == st.uuid) || (st.uuid == null && it.name == st.name && it.address != st.address) }
        if (same != null && same.address != st.address) select(same.address, same.name, st.model.ifBlank { same.model }, same.uuid)
    }

    /** "Add printer by IP address": tries a few common queue paths. */
    fun addByIp(input: String) {
        probeJob?.cancel()
        _add.value = AddState.Trying
        probeJob = scope.launch(Dispatchers.IO) {
            var text = input.trim()
            val tls = text.startsWith("ipps://", true) || text.startsWith("https://", true)
            text = text.substringAfter("://")
            var path: String? = null
            if (text.contains('/')) { path = text.substringAfter('/').trim('/'); text = text.substringBefore('/') }
            var port = if (tls) 631 else 631
            var host = text
            if (!host.startsWith("[") && host.count { it == ':' } == 1) { port = host.substringAfter(':').toIntOrNull() ?: 631; host = host.substringBefore(':') }
            if (host.isBlank()) { _add.value = AddState.Failed(t(R.string.print_enter_the_printer_s_ip_address_like_19)); return@launch }
            val paths = (listOfNotNull(path?.takeIf { it.isNotEmpty() }) + listOf("ipp/print", "ipp", "ipp/printer", "printers/ipp", "printers/printer", "")).distinct()
            var lastErr: Exception? = null
            for (p in paths) {
                val addr = PrinterAddress(host, port, p, tls)
                try {
                    val caps = IppClient.getPrinterAttributes(app, addr)
                    select(addr, caps.name.ifBlank { caps.makeModel }.ifBlank { host }, caps.makeModel.ifBlank { host }, null, caps)
                    _add.value = AddState.Idle
                    return@launch
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) {
                    lastErr = e
                    if (e is ConnectException || e is SocketTimeoutException || e is UnknownHostException || e is NoRouteToHostException) break
                }
            }
            _add.value = AddState.Failed(
                if (lastErr != null && lastErr !is IppException) friendly(lastErr)
                else t(R.string.print_found_something_at_but_it_didn_t_answe, host),
            )
        }
    }
    fun resetAdd() { probeJob?.cancel(); _add.value = AddState.Idle }

    private fun friendly(e: Throwable): String = when (e) {
        is SocketTimeoutException, is ConnectException, is NoRouteToHostException, is UnknownHostException ->
            t(R.string.print_can_t_reach_the_printer_check_that_it)
        is IppException -> e.message ?: t(R.string.print_the_printer_reported_an_error)
        is java.io.IOException -> t(R.string.print_the_connection_to_the_printer_was_inte)
        else -> e.message ?: t(R.string.print_something_went_wrong)
    }

    // ---- previews -----------------------------------------------------------------------------------------------------

    private val previews = object : LruCache<String, Bitmap>(40 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    suspend fun preview(context: Context, pageIndex: Int, width: Int): Bitmap? {
        val d = _doc.value
        val page = d.pages.getOrNull(pageIndex) ?: return null
        val key = "${page.fileIndex}:${page.index}:${width / 64}"
        previews.get(key)?.let { return it }
        return withContext(Dispatchers.IO) {
            PrintRender.previewBitmap(context.applicationContext, d.files, page, width)?.also { previews.put(key, it) }
        }
    }

    // ---- printing -----------------------------------------------------------------------------------------------------

    fun dismissJob() { if (!_job.value.active) _job.value = JobState() }

    fun cancel() {
        val id = currentJobId
        val addr = currentAddress
        val job = printJob
        if (id != null && addr != null) {
            _job.update { it.copy(text = t(R.string.print_cancelling)) }
            scope.launch(Dispatchers.IO) {
                try {
                    IppClient.call(
                        app, addr, Ipp.OP_CANCEL_JOB,
                        listOf(Ipp.operationGroup(addr.ippUri, jobIdAttr(id), Ipp.Attribute(Ipp.T_NAME, "requesting-user-name", Ipp.name("Good Utilities")))),
                        readTimeout = 10000,
                    )
                } catch (_: Exception) {}
                job?.cancel()
                _job.value = JobState(JobPhase.Failed, 0f, t(R.string.print_print_cancelled))
                refreshPrinter()
            }
        } else {
            job?.cancel()
            _job.value = JobState(JobPhase.Failed, 0f, t(R.string.print_print_cancelled))
        }
    }

    private fun jobIdAttr(id: Int) = Ipp.Attribute(Ipp.T_INTEGER, "job-id", Ipp.int(id))

    fun print() {
        if (_job.value.active) return
        currentJobId = null
        _job.value = JobState(JobPhase.Preparing, 0f, t(R.string.music_preparing))
        printJob = scope.launch(Dispatchers.IO) {
            try {
                runPrint()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _job.value = JobState(JobPhase.Failed, 0f, friendly(e).let { if (e is OutOfMemoryError) t(R.string.print_not_enough_memory_to_prepare_this_docu) else it })
            } finally {
                File(app.cacheDir, "print/job").deleteRecursively()
                currentJobId = null
            }
        }
    }

    private suspend fun runPrint() {
        val d = _doc.value
        val st = _printer.value
        val addr = st.address ?: throw IppException(t(R.string.print_choose_a_printer_first))
        val caps = st.caps ?: throw IppException(st.error ?: t(R.string.print_waiting_for_the_printer_s_details))
        currentAddress = addr
        val opts = _options.value
        val sel = _selected.value.sorted()
        if (sel.isEmpty()) throw IppException(t(R.string.print_select_at_least_one_page))
        val pages = sel.map { d.pages[it] }
        val papers = PrintModels.papersFor(caps)
        val paper = papers.firstOrNull { it.id == opts.media } ?: PrintModels.defaultPaper(caps, papers)
        val dir = File(app.cacheDir, "print/job").apply { deleteRecursively(); mkdirs() }
        val ctx = app

        val duplex = opts.duplex > 0 && caps.duplexSupported
        val colorWanted = opts.color && caps.supportsColor
        val ippCopies = opts.copies > 1 && caps.copiesKnown && caps.copiesMax >= opts.copies

        // Which path?
        val rasterKind = when {
            caps.canPdf -> null
            caps.canPwg -> PrintModels.RasterKind.Pwg
            caps.canUrf -> PrintModels.RasterKind.Urf
            else -> null
        }
        val jpeg = !caps.canPdf && rasterKind == null && caps.canJpeg
        if (!caps.canPdf && rasterKind == null && !jpeg) {
            throw IppException(t(R.string.print_this_printer_only_accepts_documents_th))
        }

        val jobAttrs = ArrayList<Ipp.Attribute>()
        fun attr(tag: Int, name: String, v: Ipp.Value) { jobAttrs += Ipp.Attribute(tag, name, v) }
        if (ippCopies || (caps.canPdf && opts.copies > 1)) attr(Ipp.T_INTEGER, "copies", Ipp.int(opts.copies))
        if (caps.duplexSupported) attr(Ipp.T_KEYWORD, "sides", Ipp.keyword(if (!duplex) "one-sided" else if (opts.duplex == 2) "two-sided-short-edge" else "two-sided-long-edge"))
        if (caps.mediaSupported.isEmpty() || caps.mediaSupported.contains(paper.id)) attr(Ipp.T_KEYWORD, "media", Ipp.keyword(paper.id))
        if (caps.colorModes.isNotEmpty()) {
            val mode = if (colorWanted) "color" else if ("monochrome" in caps.colorModes) "monochrome" else null
            if (mode != null && mode in caps.colorModes) attr(Ipp.T_KEYWORD, "print-color-mode", Ipp.keyword(mode))
        }
        val q = when (opts.quality) { 0 -> 3; 2 -> 5; else -> 4 }
        if (q in caps.qualities) attr(Ipp.T_ENUM, "print-quality", Ipp.enum(q))

        val files = ArrayList<File>()
        var format = "application/pdf"
        var pageRanges: List<IntRange>? = null
        var sheets = pages.size
        val progress = { p: Float -> _job.update { JobState(JobPhase.Preparing, p, t(R.string.music_preparing)) } }
        val sendProgress = { p: Float -> _job.update { JobState(JobPhase.Sending, p, t(R.string.print_sending, (p * 100).toInt())) } }

        if (caps.canPdf) {
            val single = d.files.size == 1 && d.files[0].isPdf
            if (single && sel.size == d.pages.size) files += File(d.files[0].path)
            else if (single && caps.pageRanges) { files += File(d.files[0].path); pageRanges = PrintModels.toIppRanges(_selected.value) }
            else { val f = File(dir, "job.pdf"); PrintRender.buildPdf(ctx, d.files, pages, paper, opts, f, progress); files += f }
            if (opts.orientation != 0) attr(Ipp.T_ENUM, "orientation-requested", Ipp.enum(if (opts.orientation == 2) 4 else 3))
            if (caps.printScaling.isNotEmpty()) {
                val want = if (opts.fit) "fit" else "none"
                if (want in caps.printScaling) attr(Ipp.T_KEYWORD, "print-scaling", Ipp.keyword(want))
            }
        } else if (rasterKind != null) {
            val pwg = rasterKind == PrintModels.RasterKind.Pwg
            format = if (pwg) "image/pwg-raster" else "image/urf"
            val grayFinal = if (pwg) !colorWanted && (caps.pwgTypes.isEmpty() || caps.pwgTypes.any { it.startsWith("sgray") })
            else !colorWanted && (caps.urf.isEmpty() || caps.urf.any { it.startsWith("W8") })
            val dpi = PrintModels.pickDpi(if (pwg) PrintModels.pwgDpis(caps) else PrintModels.urfDpis(caps), opts.quality)
            val spec = PrintRender.RasterSpec(
                kind = rasterKind, dpi = dpi, gray = grayFinal, duplex = duplex, tumble = opts.duplex == 2, quality = opts.quality,
                sheetBack = caps.pwgSheetBack, repeat = if (opts.copies > 1 && !ippCopies) opts.copies else 1, pageSizeName = paper.id,
            )
            val f = File(dir, "job.ras")
            sheets = PrintRender.buildRaster(ctx, d.files, pages, paper, opts, spec, f, progress)
            files += f
        } else {
            format = "image/jpeg"
            val dpi = PrintModels.pickDpi(caps.resolutions.map { it.dpiX }, opts.quality).coerceIn(150, 300)
            val total = pages.size * (if (opts.copies > 1 && !ippCopies) opts.copies else 1)
            var n = 0
            repeat(if (opts.copies > 1 && !ippCopies) opts.copies else 1) {
                pages.forEach { pg ->
                    val f = File(dir, "p${n++}.jpg")
                    PrintRender.buildJpeg(ctx, d.files, pg, paper, opts, dpi, f)
                    files += f
                    progress(n.toFloat() / total)
                }
            }
        }
        if (pageRanges != null) jobAttrs += Ipp.Attribute(Ipp.T_RANGE, "page-ranges", pageRanges.map { Ipp.range(it.first, it.last) })

        // Send (one request per file; only the JPEG path has more than one)
        val job = currentCoroutineContext()[Job]
        var lastJobId = 0
        files.forEachIndexed { i, f ->
            currentCoroutineContext().ensureActive()
            val groups = listOf(
                Ipp.operationGroup(
                    addr.ippUri,
                    Ipp.Attribute(Ipp.T_NAME, "requesting-user-name", Ipp.name("Good Utilities")),
                    Ipp.Attribute(Ipp.T_NAME, "job-name", Ipp.name(d.files.firstOrNull()?.name?.take(60) ?: "Document")),
                    Ipp.Attribute(Ipp.T_BOOLEAN, "ipp-attribute-fidelity", Ipp.bool(false)),
                    Ipp.Attribute(Ipp.T_MIME, "document-format", Ipp.mime(format)),
                ),
                Ipp.Group(Ipp.TAG_JOB, jobAttrs.toList()),
            )
            _job.value = JobState(JobPhase.Sending, 0f, t(R.string.print_sending_2))
            val resp = IppClient.call(
                ctx, addr, Ipp.OP_PRINT_JOB, groups, f, preferred = caps.ippMajor to caps.ippMinor, readTimeout = 180_000,
            ) { p ->
                if (job != null && !job.isActive) throw CancellationException("cancelled")
                sendProgress((i + p) / files.size)
            }
            if (!Ipp.isSuccess(resp.code)) throw IppException(PrintModels.friendlyIppError(resp.code, resp.statusMessage), resp.code)
            val id = resp.job?.int("job-id") ?: 0
            lastJobId = id
            if (id > 0) currentJobId = id
            // A multi-file (JPEG) print waits for each job to finish before sending the next, so pages stay in order.
            if (id > 0 && i < files.size - 1) pollJob(addr, id, caps, sheets)
        }
        if (lastJobId > 0) pollJob(addr, lastJobId, caps, sheets)
        else _job.value = JobState(JobPhase.Done, 1f, t(R.string.print_sent_to_the_printer))
        refreshPrinter()
    }

    /** Polls Get-Job-Attributes until the job completes, fails or is cancelled. */
    private suspend fun pollJob(addr: PrinterAddress, id: Int, caps: PrinterCaps, sheets: Int) {
        var fails = 0
        var sawProcessing = false
        val started = System.currentTimeMillis()
        _job.value = JobState(JobPhase.Printing, 0f, t(R.string.print_waiting_for_the_printer))
        while (System.currentTimeMillis() - started < 20 * 60_000L) {
            currentCoroutineContext().ensureActive()
            delay(1200)
            val resp = try {
                IppClient.call(
                    app, addr, Ipp.OP_GET_JOB_ATTRIBUTES,
                    listOf(
                        Ipp.operationGroup(
                            addr.ippUri, jobIdAttr(id),
                            Ipp.Attribute(Ipp.T_NAME, "requesting-user-name", Ipp.name("Good Utilities")),
                            Ipp.Attribute(
                                Ipp.T_KEYWORD, "requested-attributes",
                                listOf("job-state", "job-state-reasons", "job-impressions-completed", "job-media-sheets-completed").map { Ipp.keyword(it) },
                            ),
                        ),
                    ),
                    preferred = caps.ippMajor to caps.ippMinor, readTimeout = 8000,
                ).also { fails = 0 }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                // Many printers drop the connection while rendering; give up politely after a few misses.
                if (++fails >= 5) {
                    if (sawProcessing) { _job.value = JobState(JobPhase.Done, 1f, t(R.string.print_sent_to_the_printer)); return }
                    throw e
                }
                continue
            }
            if (resp.code == Ipp.STATUS_CLIENT_NOT_FOUND) {
                // Finished jobs are often forgotten right away.
                _job.value = JobState(JobPhase.Done, 1f, if (sawProcessing) t(R.string.print_printed) else t(R.string.print_sent_to_the_printer))
                return
            }
            val j = resp.job ?: continue
            val state = j.int("job-state") ?: continue
            val reasons = j.strings("job-state-reasons")
            val done = j.int("job-media-sheets-completed") ?: j.int("job-impressions-completed") ?: 0
            when (state) {
                Ipp.JOB_COMPLETED -> { _job.value = JobState(JobPhase.Done, 1f, t(R.string.print_printed)); return }
                Ipp.JOB_CANCELED -> { _job.value = JobState(JobPhase.Failed, 0f, t(R.string.print_print_cancelled)); return }
                Ipp.JOB_ABORTED -> {
                    _job.value = JobState(JobPhase.Failed, 0f, t(R.string.print_the_printer_couldn_t_finish_the_job) + (reasonText(reasons)?.let { ": $it" } ?: "."))
                    return
                }
                Ipp.JOB_PROCESSING, Ipp.JOB_STOPPED -> {
                    sawProcessing = true
                    val stuck = if (state == Ipp.JOB_STOPPED) reasonText(reasons) ?: reasonText(_printer.value.caps?.stateReasons.orEmpty()) else null
                    _job.value = JobState(
                        JobPhase.Printing, if (sheets > 0 && done > 0) (done.toFloat() / sheets).coerceIn(0f, 1f) else 0f,
                        stuck ?: if (done > 0 && sheets > 0) t(R.string.print_printing_of, done, sheets) else t(R.string.print_printing),
                    )
                }
                else -> _job.value = JobState(JobPhase.Printing, 0f, reasonText(reasons) ?: t(R.string.print_waiting_for_the_printer))
            }
        }
        _job.value = JobState(JobPhase.Done, 1f, t(R.string.print_sent_to_the_printer))
    }

    private fun reasonText(reasons: List<String>): String? {
        val r = reasons.map { it.removeSuffix("-error").removeSuffix("-warning").removeSuffix("-report") }
        return when {
            r.any { it == "media-jam" } -> t(R.string.print_paper_jam)
            r.any { it == "media-empty" || it == "media-needed" } -> t(R.string.print_out_of_paper)
            r.any { it == "toner-empty" } -> t(R.string.print_out_of_toner)
            r.any { it == "cover-open" || it == "door-open" } -> t(R.string.print_cover_open)
            r.any { it == "job-incoming" } -> t(R.string.print_receiving_data)
            else -> null
        }?.replaceFirstChar { it.uppercase() }
    }
}
