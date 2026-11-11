package com.tools.app.print

import com.tools.app.ui.theme.IconColors
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Print
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tools.app.ui.CardDivider
import com.tools.app.ui.CardMargin
import com.tools.app.ui.ColorIcon
import com.tools.app.ui.EmptyState
import com.tools.app.ui.ListRow
import com.tools.app.ui.OneUiDialog
import com.tools.app.ui.OneUiIcons
import com.tools.app.ui.OneUiScaffold
import com.tools.app.ui.OneUiSwitch
import com.tools.app.ui.PillButton
import com.tools.app.ui.RadioDot
import com.tools.app.ui.RoundCheck
import com.tools.app.ui.SegmentedControl
import com.tools.app.ui.ThinProgress
import com.tools.app.ui.ToolPage
import com.tools.app.ui.ToolbarIcon
import com.tools.app.ui.card
import com.tools.app.ui.sectionLabel
import com.tools.app.ui.theme.OneUi
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
fun PrintScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val c = OneUi.colors
    LaunchedEffect(Unit) { PrintSession.init(context) }

    val doc by PrintSession.doc.collectAsState()
    val selected by PrintSession.selected.collectAsState()
    val opts by PrintSession.options.collectAsState()
    val printer by PrintSession.printer.collectAsState()
    val job by PrintSession.job.collectAsState()
    val found by PrinterDiscovery.printers.collectAsState()
    var picker by remember { mutableStateOf(false) }

    // Discovery runs while the page is open; the remembered printer is also contacted directly.
    DisposableEffect(Unit) {
        PrinterDiscovery.start(context)
        onDispose { PrinterDiscovery.stop() }
    }
    LaunchedEffect(found, printer.address) {
        if (printer.address == null) found.firstOrNull()?.let { PrintSession.selectPrinter(it) }
        else PrintSession.onDiscovered(found)
    }
    LaunchedEffect(printer.address) {
        while (true) {
            kotlinx.coroutines.delay(8000)
            if (!PrintSession.job.value.active) PrintSession.refreshPrinter()
        }
    }

    val pickFiles = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { PrintSession.add(context, it) }
    val pickPhotos = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { PrintSession.add(context, it) }
    val chooseFiles = { pickFiles.launch(arrayOf("application/pdf", "image/*")) }
    val choosePhotos = { pickPhotos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }

    if (doc.pages.isEmpty()) {
        ToolPage("Print", onBack) {
            if (doc.loading) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = c.accent) }
            } else {
                Box(Modifier.weight(1f)) {
                    EmptyState(
                        Icons.Rounded.Print, IconColors.Blue, "Print a document or photo",
                        (doc.error?.let { "$it\n\n" } ?: "") +
                            "Choose PDFs or images here. You can also share files from any app and choose \"Print\".",
                        "Choose files", onAction = chooseFiles,
                    )
                }
                PillButton(
                    "Choose photos", onClick = choosePhotos, tonal = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 16.dp),
                )
            }
        }
        return
    }

    val caps = printer.caps
    val papers = PrintModels.papersFor(caps)
    val paper = papers.firstOrNull { it.id == opts.media } ?: PrintModels.defaultPaper(caps, papers)
    val mono = !(opts.color && (caps?.supportsColor ?: true))
    val nSel = selected.size

    OneUiScaffold(
        title = "Print", onBack = onBack, expandedTitle = false,
        actions = {
            ToolbarIcon(OneUiIcons.Gallery, "Add photos", choosePhotos)
            ToolbarIcon(OneUiIcons.Add, "Add files", chooseFiles)
        },
        bottomBar = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (job.phase != JobPhase.Idle) JobCard(job)
                val canPrint = caps != null && nSel > 0 && !job.active
                PillButton(
                    when {
                        job.active -> "Printing…"
                        printer.address == null -> "Choose a printer"
                        nSel > 1 -> "Print $nSel pages"
                        else -> "Print"
                    },
                    onClick = { if (printer.address == null) picker = true else PrintSession.print() },
                    enabled = if (printer.address == null) true else canPrint,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
    ) {
        item(key = "preview") { PreviewPager(doc, selected, opts, paper, mono) }

        sectionLabel("Printer")
        card("printer") { PrinterCard(printer, onClick = { picker = true }) }

        if (doc.files.size > 1 || doc.files.firstOrNull()?.isPdf == false) {
            sectionLabel("Files")
            card("files") {
                doc.files.forEachIndexed { i, f ->
                    if (i > 0) CardDivider()
                    ListRow(
                        f.name, summary = if (f.isPdf) "${f.pages} ${if (f.pages == 1) "page" else "pages"}" else "Image",
                        trailing = {
                            Box(Modifier.size(36.dp).clip(CircleShape).clickable { PrintSession.removeFile(i) }, contentAlignment = Alignment.Center) {
                                Icon(OneUiIcons.Close, "Remove", tint = c.textSecondary, modifier = Modifier.size(20.dp))
                            }
                        },
                    )
                }
            }
        }

        sectionLabel("Options")
        card("options") { Options(doc, selected, opts, caps, papers, paper) }
        item(key = "end") { Spacer(Modifier.height(12.dp)) }
    }

    if (picker) PrinterPicker(found, printer, onClose = { picker = false })
}

// ---- preview ---------------------------------------------------------------------------------------------------

@Composable
private fun PreviewPager(doc: DocState, selected: Set<Int>, opts: PrintOptions, paper: Paper, mono: Boolean) {
    val c = OneUi.colors
    val pages = doc.pages
    val state = rememberPagerState { pages.size }
    Column(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        HorizontalPager(
            state, Modifier.fillMaxWidth().height(380.dp),
            contentPadding = PaddingValues(horizontal = 64.dp), pageSpacing = 14.dp,
            key = { "${it}-${pages.size}" },
        ) { i ->
            val pg = pages[i]
            val land = PrintModels.landscape(opts.orientation, pg)
            val sw = if (land) max(paper.wPt, paper.hPt) else min(paper.wPt, paper.hPt)
            val sh = if (land) min(paper.wPt, paper.hPt) else max(paper.wPt, paper.hPt)
            Box(Modifier.fillMaxSize().padding(vertical = 14.dp), contentAlignment = Alignment.Center) {
                Sheet(i, pg, sw, sh, opts, mono, i in selected, Modifier.aspectRatio(sw / sh))
            }
        }
        val cur = state.currentPage.coerceIn(0, max(0, pages.size - 1))
        Row(
            Modifier.clip(RoundedCornerShape(22.dp)).clickable { PrintSession.togglePage(cur) }.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RoundCheck(cur in selected)
            Spacer(Modifier.width(10.dp))
            Text(
                "Page ${cur + 1} of ${pages.size}", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = c.text,
            )
            if (selected.size != pages.size) Text("  ·  ${selected.size} selected", fontSize = 14.sp, color = c.textSecondary)
        }
    }
}

@Composable
private fun Sheet(index: Int, pg: PrintPage, sw: Float, sh: Float, opts: PrintOptions, mono: Boolean, included: Boolean, modifier: Modifier) {
    val context = LocalContext.current
    val c = OneUi.colors
    val bmp by produceState<android.graphics.Bitmap?>(null, index, pg) { value = PrintSession.preview(context, index, 720) }
    val gray = remember { ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }) }
    Box(
        modifier
            .alpha(if (included) 1f else 0.5f)
            .shadow(10.dp, RoundedCornerShape(3.dp), ambientColor = Color.Black.copy(alpha = 0.35f), spotColor = Color.Black.copy(alpha = 0.35f))
            .background(Color.White, RoundedCornerShape(3.dp))
            .clip(RoundedCornerShape(3.dp)),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val b = bmp
            val pl = PrintModels.place(sw, sh, PrintModels.MARGIN_PT, pg.w, pg.h, opts.fit, PrintModels.naturalScale(pg))
            val k = size.width / sw
            if (b != null) {
                drawImage(
                    b.asImageBitmap(),
                    dstOffset = IntOffset((pl.x * k).roundToInt(), (pl.y * k).roundToInt()),
                    dstSize = IntSize((pl.w * k).roundToInt().coerceAtLeast(1), (pl.h * k).roundToInt().coerceAtLeast(1)),
                    colorFilter = if (mono) gray else null,
                )
            }
        }
        if (bmp == null) CircularProgressIndicator(Modifier.align(Alignment.Center).size(26.dp), color = c.accent, strokeWidth = 2.5.dp)
        Box(
            Modifier.align(Alignment.TopEnd).padding(4.dp).size(40.dp).clip(CircleShape).clickable { PrintSession.togglePage(index) },
            contentAlignment = Alignment.Center,
        ) { RoundCheck(included) }
    }
}

// ---- printer card -----------------------------------------------------------------------------------------------

@Composable
private fun PrinterCard(st: PrinterState, onClick: () -> Unit) {
    val c = OneUi.colors
    val caps = st.caps
    val status = caps?.let { PrintModels.status(it) }
    val toners = caps?.let { PrintModels.tonerMarkers(it) }.orEmpty()
    ListRow(
        title = if (st.address == null) "Choose a printer" else st.model.ifBlank { st.name.ifBlank { st.address.host } },
        summary = when {
            st.address == null -> "Searching your Wi-Fi network…"
            status != null -> status.first + (if (st.error != null) " · can't refresh" else "")
            st.error != null -> st.error
            else -> "Connecting…"
        },
        summaryColor = when {
            st.address != null && status == null && st.error != null -> c.error
            status?.second == PrintModels.Tone.Error -> c.error
            status?.second == PrintModels.Tone.Warn -> OneUi.colors.accent
            status?.second == PrintModels.Tone.Ok -> c.success
            else -> null
        },
        leading = { ColorIcon(Icons.Rounded.Print, if (st.error != null && caps == null) c.textSecondary else c.accent, size = 40.dp) },
        trailing = { Text("Change", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = c.accent) },
        onClick = onClick,
    )
    if (toners.isNotEmpty()) {
        CardDivider()
        Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            toners.forEach { m ->
                val col = m.color?.let { Color(0xFF000000.toInt() or it) }
                val bar = if (col == null || (col.red + col.green + col.blue) / 3f < 0.25f) c.text else col
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        m.name.ifBlank { "Toner" }, fontSize = 13.sp, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(0.42f),
                    )
                    ThinProgress(m.level / 100f, Modifier.weight(0.45f), color = if (m.level <= 10) c.error else bar, height = 6.dp)
                    Text("${m.level}%", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = c.text, textAlign = TextAlign.End, modifier = Modifier.weight(0.13f))
                }
            }
        }
    }
}

// ---- options ----------------------------------------------------------------------------------------------------

@Composable
private fun Options(doc: DocState, selected: Set<Int>, opts: PrintOptions, caps: PrinterCaps?, papers: List<Paper>, paper: Paper) {
    val c = OneUi.colors
    var paperDialog by remember { mutableStateOf(false) }
    val maxCopies = if (caps?.copiesKnown == true) caps.copiesMax.coerceIn(1, 99) else 99

    // Copies
    ListRow(
        "Copies",
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StepButton("−", opts.copies > 1) { PrintSession.setOptions { it.copy(copies = (it.copies - 1).coerceAtLeast(1)) } }
                Text("${opts.copies}", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = c.text, textAlign = TextAlign.Center, modifier = Modifier.width(44.dp))
                StepButton("+", opts.copies < maxCopies) { PrintSession.setOptions { it.copy(copies = (it.copies + 1).coerceAtMost(maxCopies)) } }
            }
        },
    )
    CardDivider()

    // Pages
    PagesRow(doc, selected)
    CardDivider()

    // Paper
    ListRow("Paper size", summary = paper.label, summaryColor = c.accent, onClick = { paperDialog = true })

    if (caps == null || caps.duplexSupported) {
        CardDivider()
        SegRow("Two-sided", listOf("Off", "Long edge", "Short edge"), opts.duplex) { v -> PrintSession.setOptions { it.copy(duplex = v) } }
    }
    if (caps == null || caps.supportsColor) {
        CardDivider()
        SegRow("Color", listOf("Color", "Black & white"), if (opts.color) 0 else 1) { v -> PrintSession.setOptions { it.copy(color = v == 0) } }
    }
    CardDivider()
    SegRow("Orientation", listOf("Auto", "Portrait", "Landscape"), opts.orientation) { v -> PrintSession.setOptions { it.copy(orientation = v) } }
    if (caps != null && (caps.qualities.size >= 2 || (!caps.canPdf && caps.known))) {
        CardDivider()
        SegRow("Quality", listOf("Draft", "Normal", "High"), opts.quality) { v -> PrintSession.setOptions { it.copy(quality = v) } }
    }
    CardDivider()
    ListRow(
        "Fit to page",
        trailing = { OneUiSwitch(opts.fit) { v -> PrintSession.setOptions { it.copy(fit = v) } } },
        onClick = { PrintSession.setOptions { it.copy(fit = !it.fit) } },
    )

    if (paperDialog) {
        OneUiDialog("Paper size", { paperDialog = false }, listOf("Close" to { paperDialog = false })) {
            Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState())) {
                papers.forEach { p ->
                    val ready = caps?.mediaReady?.contains(p.id) == true
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                            .clickable { PrintSession.setOptions { it.copy(media = p.id) }; paperDialog = false }
                            .padding(vertical = 12.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioDot(p.id == paper.id)
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(p.label, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = c.text)
                            if (ready) Text("Loaded in the printer", fontSize = 13.sp, color = c.textSecondary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StepButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    val c = OneUi.colors
    Box(
        Modifier.size(38.dp).clip(CircleShape).background(c.tonal).alpha(if (enabled) 1f else 0.4f).clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(label, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = c.text) }
}

@Composable
private fun SegRow(title: String, options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Column(Modifier.padding(horizontal = 18.dp, vertical = 12.dp)) {
        Text(title, fontSize = 15.5.sp, fontWeight = FontWeight.SemiBold, color = OneUi.colors.text, modifier = Modifier.padding(bottom = 10.dp))
        SegmentedControl(options, selected, onSelect)
    }
}

@Composable
private fun PagesRow(doc: DocState, selected: Set<Int>) {
    val c = OneUi.colors
    val total = doc.pages.size
    val all = selected.size == total
    var rangeMode by remember { mutableStateOf(!all) }
    var text by remember { mutableStateOf(PrintModels.formatRanges(selected)) }
    var focused by remember { mutableStateOf(false) }
    val valid = PrintModels.parseRanges(text, total) != null
    // Checking / unchecking pages in the preview moves us to "Range" and keeps the text in sync.
    LaunchedEffect(selected) {
        if (!all) rangeMode = true
        if (!focused || PrintModels.parseRanges(text, total) == selected) {
            if (!focused) text = PrintModels.formatRanges(selected)
        }
    }
    Column(Modifier.padding(horizontal = 18.dp, vertical = 12.dp)) {
        Text("Pages", fontSize = 15.5.sp, fontWeight = FontWeight.SemiBold, color = c.text, modifier = Modifier.padding(bottom = 10.dp))
        SegmentedControl(listOf("All ($total)", "Range"), if (rangeMode) 1 else 0, { i ->
            if (i == 0) { rangeMode = false; PrintSession.selectAll() } else { rangeMode = true; if (all) text = "1-$total" }
        })
        if (rangeMode) {
            Spacer(Modifier.height(10.dp))
            OneUiField(
                text, { t -> text = t; PrintSession.selectRange(t) }, "e.g. 1-3, 5",
                error = text.isNotBlank() && !valid, modifier = Modifier.onFocusChanged { focused = it.isFocused },
            )
            if (text.isNotBlank() && !valid) Text("Use page numbers and ranges, like 1-3, 5", fontSize = 12.5.sp, color = c.error, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

@Composable
private fun OneUiField(
    value: String, onChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier,
    error: Boolean = false, keyboardType: KeyboardType = KeyboardType.Text,
) {
    val c = OneUi.colors
    Box(
        modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.tonal)
            .then(if (error) Modifier.border(1.5.dp, c.error, RoundedCornerShape(16.dp)) else Modifier)
            .padding(horizontal = 16.dp, vertical = 13.dp),
    ) {
        if (value.isEmpty()) Text(placeholder, fontSize = 16.sp, color = c.textSecondary)
        BasicTextField(
            value, onChange, singleLine = true,
            textStyle = TextStyle(color = c.text, fontSize = 16.sp), cursorBrush = SolidColor(c.accent),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

// ---- job card -----------------------------------------------------------------------------------------------------

@Composable
private fun JobCard(job: JobState) {
    val c = OneUi.colors
    Column(
        Modifier.fillMaxWidth().clip(OneUi.CardShape).background(c.card).padding(horizontal = 18.dp, vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            when (job.phase) {
                JobPhase.Done -> Icon(OneUiIcons.CheckCircle, null, tint = c.success, modifier = Modifier.size(22.dp).padding(end = 0.dp))
                JobPhase.Failed -> Icon(OneUiIcons.ErrorOutline, null, tint = c.error, modifier = Modifier.size(22.dp))
                else -> CircularProgressIndicator(Modifier.size(20.dp), color = c.accent, strokeWidth = 2.5.dp)
            }
            Spacer(Modifier.width(12.dp))
            Text(
                job.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                color = when (job.phase) { JobPhase.Done -> c.success; JobPhase.Failed -> c.error; else -> c.text },
                modifier = Modifier.weight(1f), maxLines = 3, overflow = TextOverflow.Ellipsis,
            )
            val label = if (job.active) "Cancel" else "OK"
            Text(
                label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = c.accent,
                modifier = Modifier.clip(CircleShape).clickable { if (job.active) PrintSession.cancel() else PrintSession.dismissJob() }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
        if (job.active && (job.phase != JobPhase.Printing || job.progress > 0f)) ThinProgress(job.progress, Modifier.padding(top = 10.dp))
    }
}

// ---- printer picker -------------------------------------------------------------------------------------------------

@Composable
private fun PrinterPicker(found: List<FoundPrinter>, current: PrinterState, onClose: () -> Unit) {
    val c = OneUi.colors
    val scanning by PrinterDiscovery.scanning.collectAsState()
    val add by PrintSession.addState.collectAsState()
    var adding by remember { mutableStateOf(false) }
    if (adding) {
        AddByIp(add, onClose = { adding = false; PrintSession.resetAdd() }, onAdded = { PrintSession.resetAdd(); onClose() })
        return
    }
    val context = LocalContext.current
    OneUiDialog("Printers", onClose, listOf("Close" to onClose)) {
        Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
            current.address?.let { a ->
                if (found.none { it.address == a }) PrinterRow(
                    current.model.ifBlank { current.name.ifBlank { a.host } }, a.host + (if (current.error != null) " · not reachable" else ""), true,
                ) { onClose() }
            }
            found.forEach { p ->
                val sel = p.address == current.address || (current.uuid != null && p.uuid == current.uuid)
                PrinterRow(
                    p.model.ifBlank { p.name }, listOf(p.name.takeIf { p.model.isNotBlank() && it != p.model }, p.address.host).filterNotNull().joinToString(" · "), sel,
                ) { PrintSession.selectPrinter(p); onClose() }
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                if (scanning) {
                    CircularProgressIndicator(Modifier.size(18.dp), color = c.accent, strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(if (found.isEmpty()) "Looking for printers…" else "Still looking…", fontSize = 14.sp, color = c.textSecondary)
                } else {
                    Text(
                        if (found.isEmpty() && current.address == null) "No printers found. Make sure the printer is on and on this Wi-Fi." else "Search again",
                        fontSize = 14.sp, color = if (found.isEmpty()) c.textSecondary else c.accent,
                        modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable { PrinterDiscovery.start(context) }.padding(vertical = 2.dp),
                    )
                }
            }
            Text(
                "Add printer by IP address", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = c.accent,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable { adding = true }.padding(vertical = 14.dp, horizontal = 4.dp),
            )
        }
    }
}

@Composable
private fun PrinterRow(title: String, summary: String, selected: Boolean, onClick: () -> Unit) {
    val c = OneUi.colors
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioDot(selected)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (summary.isNotBlank()) Text(summary, fontSize = 13.sp, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun AddByIp(state: AddState, onClose: () -> Unit, onAdded: () -> Unit) {
    val c = OneUi.colors
    var ip by remember { mutableStateOf("") }
    val printer by PrintSession.printer.collectAsState()
    val start = remember { printer.address }
    // Once the probe succeeds the printer changes; close the dialog.
    LaunchedEffect(printer.address, state) { if (state is AddState.Idle && printer.address != start && printer.address != null) onAdded() }
    OneUiDialog(
        "Add printer", onClose,
        listOf("Cancel" to onClose, (if (state is AddState.Trying) "Connecting…" else "Add") to { if (state !is AddState.Trying) PrintSession.addByIp(ip) }),
    ) {
        Text("Enter the IP address shown on the printer's network settings page.", fontSize = 14.sp, color = c.textSecondary, modifier = Modifier.padding(bottom = 12.dp))
        OneUiField(ip, { ip = it }, "192.168.1.50", error = state is AddState.Failed, keyboardType = KeyboardType.Uri)
        if (state is AddState.Failed) Text(state.message, fontSize = 13.sp, color = c.error, modifier = Modifier.padding(top = 8.dp))
        if (state is AddState.Trying) Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(16.dp), color = c.accent, strokeWidth = 2.dp)
            Spacer(Modifier.width(10.dp))
            Text("Contacting the printer…", fontSize = 13.sp, color = c.textSecondary)
        }
        Spacer(Modifier.height(6.dp))
    }
}
