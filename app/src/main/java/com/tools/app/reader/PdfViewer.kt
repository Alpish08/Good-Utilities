package com.tools.app.reader

import dev.chrisbanes.haze.hazeSource
import android.content.Intent
import android.graphics.RectF
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.gestures.stopScroll
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.tools.app.ui.OneUiDialog
import com.tools.app.ui.OverflowMenu
import com.tools.app.ui.OneUiIcons
import com.tools.app.ui.ToolbarIcon
import com.tools.app.ui.theme.OneUi
import com.tools.app.ui.theme.OneUiEasing
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

private const val MAX_ZOOM = 5f

private val InvertFilter = ColorFilter.colorMatrix(
    ColorMatrix(floatArrayOf(-1f, 0f, 0f, 0f, 255f, 0f, -1f, 0f, 0f, 255f, 0f, 0f, -1f, 0f, 255f, 0f, 0f, 0f, 1f, 0f)),
)

private class Tile(val img: ImageBitmap, val left: Int, val top: Int, val w: Int, val h: Int)
private class Jump(val page: Int, val y: Float? = null)

private class Hit(val page: Int, val rects: List<RectF>)

private class SearchState {
    var active by mutableStateOf(false)
    var query by mutableStateOf("")
    var hits by mutableStateOf<List<Hit>>(emptyList())
    var current by mutableIntStateOf(-1)
    var running by mutableStateOf(false)
    val byPage by derivedStateOf { hits.withIndex().groupBy({ it.value.page }, { it.index to it.value.rects }) }
}

private class ZoomState(z: Float) {
    var z by mutableFloatStateOf(z)
    var tx by mutableFloatStateOf(0f)
}

/** Where we are, for saving. */
private class Where {
    var page by mutableIntStateOf(0)       // page under the middle of the screen (indicator)
    var first by mutableIntStateOf(0)      // first visible page (restore)
    var frac by mutableFloatStateOf(0f)
}

private enum class Dlg { GoTo, Contents, Info }

@OptIn(FlowPreview::class)
@Composable
internal fun PdfViewer(
    doc: PdfDoc,
    settings: ReaderStore.Settings,
    start: Position?,
    onPosition: (Position) -> Unit,
    onBack: () -> Unit,
) {
    val ctx = LocalContext.current
    val c = OneUi.colors
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val view = LocalView.current
    val count = doc.pageCount

    var bars by remember { mutableStateOf(true) }
    val where = remember { Where().also { it.page = (start?.page ?: 0).coerceIn(0, count - 1); it.first = it.page; it.frac = start?.frac ?: 0f } }
    val zoom = remember { ZoomState((start?.zoom ?: 1f).coerceIn(1f, MAX_ZOOM)) }
    var jump by remember { mutableStateOf<Jump?>(null) }
    val search = remember { SearchState() }
    var dialog by remember { mutableStateOf<Dlg?>(null) }
    var outline by remember { mutableStateOf<List<OutlineEntry>>(emptyList()) }
    var meta by remember { mutableStateOf<PdfMeta?>(null) }
    var selection by remember { mutableStateOf<Selection?>(null) }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = where.first)

    LaunchedEffect(doc) { outline = doc.outline() }

    // ---- remember position ----
    LaunchedEffect(doc) {
        snapshotFlow { Triple(where.first, where.frac, zoom.z) }.debounce(600).collect { (p, f, z) -> onPosition(Position(p, f, z)) }
    }
    DisposableEffect(doc) { onDispose { onPosition(Position(where.first, where.frac, zoom.z)) } }

    // ---- window: keep screen on, immersive ----
    DisposableEffect(settings.keepOn) { view.keepScreenOn = settings.keepOn; onDispose { view.keepScreenOn = false } }
    val window = ctx.findActivity()?.window
    LaunchedEffect(bars, search.active) {
        if (window != null) WindowCompat.getInsetsController(window, view).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (bars || search.active) show(WindowInsetsCompat.Type.systemBars()) else hide(WindowInsetsCompat.Type.systemBars())
        }
    }
    DisposableEffect(Unit) {
        onDispose { if (window != null) WindowCompat.getInsetsController(window, view).show(WindowInsetsCompat.Type.systemBars()) }
    }

    BackHandler(search.active) { search.active = false }
    BackHandler(selection != null) { selection = null }

    // ---- search ----
    LaunchedEffect(search.active, search.query) {
        if (!search.active || search.query.isBlank()) { search.hits = emptyList(); search.current = -1; search.running = false; return@LaunchedEffect }
        delay(350)
        val q = search.query.trim()
        search.hits = emptyList(); search.current = -1; search.running = true
        try {
            // Start from the page you are on so the first result is close by, then wrap around.
            val order = (0 until count).map { (where.page + it) % count }
            val acc = ArrayList<Hit>()
            for (p in order) {
                ensureActive()
                if (advancedPdf) {
                    val found = doc.searchNative(p, q)
                    if (found.isNotEmpty()) found.forEach { acc.add(Hit(p, it)) }
                } else {
                    repeat(doc.searchText(p, q)) { acc.add(Hit(p, emptyList())) }
                }
                if (acc.size != search.hits.size) {
                    search.hits = acc.toList()
                    if (search.current < 0) { search.current = 0; jump = Jump(acc[0].page, acc[0].rects.firstOrNull()?.top) }
                }
            }
        } finally { search.running = false }
    }
    fun step(d: Int) {
        val n = search.hits.size
        if (n == 0) return
        search.current = (search.current + d + n) % n
        val h = search.hits[search.current]
        jump = Jump(h.page, h.rects.firstOrNull()?.top)
    }

    val landscape = androidx.compose.ui.platform.LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    BoxWithConstraints(Modifier.fillMaxSize().background(c.background)) {
        val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        val topPad = statusTop + BarHeight
        val bottomPad = navBottom + 28.dp

        var scrolling by remember { mutableStateOf(false) }
        var dragging by remember { mutableStateOf(false) }
        var pill by remember { mutableStateOf(false) }
        LaunchedEffect(scrolling, dragging) {
            if (scrolling || dragging) pill = true else { delay(1100); pill = false }
        }

        val toggleBars = { selection = null; bars = !bars }
        var pressY by remember { mutableStateOf(0f) }
        // The pages, captured so the top bar can blur them.
        val haze = remember { dev.chrisbanes.haze.HazeState() }
        Box(Modifier.fillMaxSize().hazeSource(haze)) {
        if (settings.continuous) {
            ContinuousPages(
                doc, zoom, listState, where, settings.night, search, selection, jump, topPad, bottomPad,
                onScrolling = { scrolling = it }, onTap = toggleBars,
                onLongPress = { p, x, y, sy ->
                    pressY = sy
                    scope.launch {
                        val s = doc.selectWord(p, x.roundToInt(), y.roundToInt())
                        if (s != null) selection = s
                    }
                },
            )
        } else {
            SinglePages(doc, where, settings.night, search, jump, topPad, bottomPad, onScrolling = { scrolling = it }, onTap = toggleBars)
        }
        }

        // ---- top bar ----
        AnimatedVisibility(
            visible = bars || search.active,
            modifier = Modifier.align(Alignment.TopStart),
            enter = fadeIn(tween(220, easing = OneUiEasing)) + slideInVertically(tween(300, easing = OneUiEasing)) { -it / 2 },
            exit = fadeOut(tween(180, easing = OneUiEasing)) + slideOutVertically(tween(260, easing = OneUiEasing)) { -it / 2 },
        ) {
            if (search.active) {
                SearchBar(
                    search.query, { search.query = it },
                    status = when {
                        search.query.isBlank() -> ""
                        search.hits.isEmpty() -> if (search.running) "…" else "No results"
                        else -> "${search.current + 1} of ${search.hits.size}" + if (search.running) "+" else ""
                    },
                    onPrev = { step(-1) }, onNext = { step(1) }, onClose = { search.active = false },
                )
            } else {
                ReaderBar(doc.info.name, onBack, haze = haze) {
                    ToolbarIcon(OneUiIcons.Search, "Search") { search.active = true; bars = true }
                    val items = buildList<Pair<String, () -> Unit>> {
                        add("Go to page" to { dialog = Dlg.GoTo })
                        if (outline.isNotEmpty()) add("Contents" to { dialog = Dlg.Contents })
                        add((if (settings.night) "Turn off night mode" else "Night mode") to { settings.changeNight(!settings.night) })
                        add((if (settings.keepOn) "Allow screen to turn off" else "Keep screen on") to { settings.changeKeepOn(!settings.keepOn) })
                        add((if (settings.continuous) "Single page" else "Continuous scroll") to { settings.changeContinuous(!settings.continuous) })
                        add((if (landscape) "Rotate to portrait" else "Rotate to landscape") to {
                            // Only this screen: the phone's own auto-rotate setting is left alone.
                            ctx.findActivity()?.requestedOrientation =
                                if (landscape) android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                                else android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                        })
                        if (!advancedPdf) add("Copy page text" to {
                            scope.launch {
                                val t = doc.pageText(where.page).trim()
                                if (t.isEmpty()) Toast.makeText(ctx, "No text on this page", Toast.LENGTH_SHORT).show() else copyToClipboard(ctx, doc.info.name, t)
                            }
                        })
                        add("Share" to { shareDocument(ctx, doc.info) })
                        add("Print" to {
                            // Good Utilities' own One UI print tool instead of the system print screen.
                            ctx.startActivity(
                                Intent(ctx, com.tools.app.MainActivity::class.java).putExtra("route", "Print")
                                    .setData(contentUri(ctx, doc.localUri))
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION),
                            )
                        })
                        add("Edit in PDF editor" to {
                            com.tools.app.pdf.PdfSession.open(ctx, listOf(doc.localUri), false)
                            runCatching {
                                ctx.startActivity(Intent(ctx, com.tools.app.MainActivity::class.java).putExtra("route", "PdfEditor").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            }
                            Toast.makeText(ctx, "Loaded in PDF editor", Toast.LENGTH_SHORT).show()
                        })
                        add("File info" to { dialog = Dlg.Info; scope.launch { if (meta == null) meta = doc.meta() } })
                    }
                    OverflowMenu(items)
                }
            }
        }

        // ---- selection popup ----
        AnimatedVisibility(
            visible = selection != null,
            // Just above the pressed word, or below it when that would hide under the toolbar.
            modifier = Modifier.align(Alignment.TopCenter).offset {
                val above = pressY - 96.dp.toPx()
                IntOffset(0, (if (above > (topPad + 8.dp).toPx()) above else pressY + 40.dp.toPx()).roundToInt())
            },
            enter = fadeIn(tween(160)) + scaleIn(tween(240, easing = OneUiEasing), 0.9f),
            exit = fadeOut(tween(120)) + scaleOut(tween(160), 0.95f),
        ) {
            val s = remember(selection) { selection } ?: return@AnimatedVisibility
            Row(Modifier.clip(RoundedCornerShape(22.dp)).background(c.popup).padding(3.5.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Copy", fontSize = 14.75.sp, fontWeight = FontWeight.Medium, color = c.text,
                    modifier = Modifier.clip(CircleShape).clickable { copyToClipboard(ctx, doc.info.name, s.text); selection = null }.padding(horizontal = 16.dp, vertical = 10.5.dp))
                Box(Modifier.width(1.dp).height(16.dp).background(c.divider))
                Text("Select all on page", fontSize = 14.75.sp, fontWeight = FontWeight.Medium, color = c.text,
                    modifier = Modifier.clip(CircleShape).clickable { scope.launch { selection = doc.allText(s.page).takeIf { it.text.isNotBlank() } ?: s } }.padding(horizontal = 16.dp, vertical = 10.5.dp))
            }
        }

        // ---- page pill ----
        AnimatedVisibility(
            visible = pill,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = navBottom + 20.dp),
            enter = fadeIn(tween(160)) + scaleIn(tween(240, easing = OneUiEasing), 0.85f),
            exit = fadeOut(tween(260)) + scaleOut(tween(260), 0.9f),
        ) {
            Text(
                "${where.page + 1} / $count", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = c.text,
                modifier = Modifier.clip(CircleShape)
                    .then(blurBehind(haze, c.background, com.tools.app.ui.MenuBlurRadius))
                    .background(com.tools.app.ui.menuTint(c.popup, c.isDark)).padding(horizontal = 13.dp, vertical = 6.dp),
            )
        }

        // ---- fast scroll ----
        if (settings.continuous && count > 1) {
            FastScroller(
                listState, count, zoom, with(density) { topPad.toPx() }, with(density) { (maxHeight - navBottom - 16.dp).toPx() },
                show = (scrolling || dragging) && !search.active, onDragging = { dragging = it },
            )
        }

        // ---- dialogs ----
        when (dialog) {
            Dlg.GoTo -> {
                var text by remember { mutableStateOf("") }
                val go = {
                    text.toIntOrNull()?.let { n -> jump = Jump((n - 1).coerceIn(0, count - 1)); dialog = null }
                    Unit
                }
                OneUiDialog("Go to page", { dialog = null }, listOf("Cancel" to { dialog = null }, "Go" to go)) {
                    OneUiField(text, { text = it.filter(Char::isDigit).take(6) }, "Page 1 – $count", androidx.compose.ui.text.input.KeyboardType.Number, onDone = go)
                }
            }
            Dlg.Contents -> OneUiDialog("Contents", { dialog = null }, listOf("Close" to { dialog = null })) {
                androidx.compose.foundation.lazy.LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(outline.size) { i ->
                        val e = outline[i]
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { jump = Jump(e.page); dialog = null }
                                .padding(start = (8 + e.level * 16).dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(e.title, Modifier.weight(1f), fontSize = 15.5.sp, color = c.text, fontWeight = if (e.level == 0) FontWeight.SemiBold else FontWeight.Normal, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            Text("${e.page + 1}", Modifier.padding(start = 12.dp), fontSize = 14.sp, color = c.textSecondary)
                        }
                    }
                }
            }
            Dlg.Info -> InfoDialog(doc.info, count, meta) { dialog = null }
            null -> {}
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Continuous scroll with pinch zoom
// ---------------------------------------------------------------------------------------------

@OptIn(FlowPreview::class)
@Composable
private fun ContinuousPages(
    doc: PdfDoc, zs: ZoomState, listState: LazyListState, where: Where, night: Boolean,
    search: SearchState, selection: Selection?, jump: Jump?,
    topPad: androidx.compose.ui.unit.Dp, bottomPad: androidx.compose.ui.unit.Dp,
    onScrolling: (Boolean) -> Unit, onTap: () -> Unit, onLongPress: (page: Int, x: Float, y: Float, screenY: Float) -> Unit,
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wPx = constraints.maxWidth
        val hPx = constraints.maxHeight
        val tiles = remember { mutableStateMapOf<Int, Tile>() }
        var pinchActive by remember { mutableStateOf(false) }

        fun clampTx(tx: Float, z: Float) = if (z <= 1f) 0f else tx.coerceIn(-(z - 1f) * wPx, 0f)

        /** Keeps the content point that was under [prev] under [now] while the zoom becomes [newZoom]. */
        fun transformBy(prev: Offset, now: Offset, newZoom: Float) {
            val old = zs.z
            val nz = newZoom.coerceIn(1f, MAX_ZOOM)
            val lx = (prev.x - zs.tx) / old
            zs.tx = clampTx(now.x - lx * nz, nz)
            listState.dispatchRawDelta(prev.y / old - now.y / nz)
            zs.z = nz
        }

        // restore zoom centring and scroll offset within the first page
        LaunchedEffect(Unit) {
            if (zs.z > 1f) zs.tx = clampTx(-(zs.z - 1f) * wPx / 2f, zs.z)
            if (where.frac > 0f) {
                val h = wPx * doc.pageH(where.first) / doc.pageW(where.first)
                listState.scrollToItem(where.first, (where.frac * h).toInt())
            }
        }

        LaunchedEffect(jump) {
            val j = jump ?: return@LaunchedEffect
            val ph = wPx * doc.pageH(j.page) / doc.pageW(j.page)
            val off = if (j.y != null) (j.y / doc.pageH(j.page) * ph - hPx / zs.z / 3f).toInt().coerceIn(0, max(0, ph.toInt() - 1)) else 0
            listState.scrollToItem(j.page, off)
        }

        LaunchedEffect(listState) {
            snapshotFlow { listState.isScrollInProgress }.collect { onScrolling(it) }
        }
        LaunchedEffect(listState) {
            snapshotFlow {
                val info = listState.layoutInfo
                val mid = info.viewportSize.height / 2 + info.viewportStartOffset
                info.visibleItemsInfo.firstOrNull { it.offset + it.size > mid }?.index ?: listState.firstVisibleItemIndex
            }.collect { where.page = it }
        }
        LaunchedEffect(listState) {
            snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }.collect { (i, o) ->
                val size = listState.layoutInfo.visibleItemsInfo.firstOrNull()?.size ?: 0
                where.first = i
                where.frac = if (size > 0) (o / size.toFloat()).coerceIn(0f, 0.98f) else 0f
            }
        }

        // Sharp text: once zoom / scroll settle, render the visible part of each visible page at full zoom.
        LaunchedEffect(doc, wPx, hPx) {
            snapshotFlow { listOf(zs.z, zs.tx, listState.firstVisibleItemIndex.toFloat(), listState.firstVisibleItemScrollOffset.toFloat()) }
                .debounce(170)
                .collectLatest {
                    val z = zs.z
                    if (z < 1.15f) { tiles.clear(); return@collectLatest }
                    val tx = zs.tx
                    val info = listState.layoutInfo
                    val vpH = info.viewportSize.height
                    val x0 = (-tx / z).coerceIn(0f, wPx.toFloat())
                    val x1 = ((wPx - tx) / z).coerceIn(0f, wPx.toFloat())
                    val visible = HashSet<Int>()
                    for (item in info.visibleItemsInfo) {
                        val y = item.offset - info.viewportStartOffset // item top in the list's own layout
                        val top = max(0, -y)
                        val bottom = min(item.size, vpH - y)
                        if (bottom - top < 8 || x1 - x0 < 8f) continue
                        visible.add(item.index)
                        val l = max(0f, x0 - (x1 - x0) * 0.05f).toInt()
                        val r = min(wPx.toFloat(), x1 + (x1 - x0) * 0.05f).let { ceil(it).toInt() }
                        val t = max(0, top - ((bottom - top) * 0.05f).toInt())
                        val b = min(item.size, bottom + ((bottom - top) * 0.05f).toInt() + 1)
                        val rw = r - l; val rh = b - t
                        if (rw <= 0 || rh <= 0) continue
                        var f = 1f
                        val px = rw * z * rh * z
                        if (px > 14_000_000f) f = sqrt(14_000_000f / px)
                        val sc = z * f
                        val bmp = doc.tile(
                            item.index, wPx * sc / doc.pageW(item.index),
                            (l * sc).roundToInt(), (t * sc).roundToInt(), (rw * sc).roundToInt(), (rh * sc).roundToInt(),
                        ) ?: continue
                        ensureActive()
                        tiles[item.index] = Tile(bmp.asImageBitmap(), l, t, rw, rh)
                    }
                    tiles.keys.retainAll(visible)
                }
        }

        Box(
            Modifier
                .fillMaxSize()
                .clipToBounds()
                // pinch zoom + horizontal pan, observed before the list so it can claim two-finger gestures
                .pointerInput(wPx) {
                    val slop = viewConfiguration.touchSlop
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        var pinching = false
                        var panning = false
                        var tx = 0f; var ty = 0f
                        do {
                            val ev = awaitPointerEvent(PointerEventPass.Initial)
                            val down = ev.changes.count { it.pressed }
                            if (down >= 2) {
                                if (!pinching) { pinchActive = true; scope.launch { listState.stopScroll() } }
                                pinching = true
                                val zc = ev.calculateZoom()
                                val prev = ev.calculateCentroid(useCurrent = false)
                                val now = ev.calculateCentroid()
                                if (prev.isSpecified && now.isSpecified) transformBy(prev, now, zs.z * zc)
                                ev.changes.forEach { if (it.positionChanged()) it.consume() }
                            } else if (!pinching && down == 1 && zs.z > 1f) {
                                val ch = ev.changes.first { it.pressed }
                                val dx = ch.position.x - ch.previousPosition.x
                                val dy = ch.position.y - ch.previousPosition.y
                                tx += dx; ty += dy
                                if (!panning && abs(tx) > slop && abs(ty) < 2f * abs(tx)) panning = true
                                if (panning) zs.tx = clampTx(zs.tx + dx, zs.z)
                            }
                        } while (ev.changes.any { it.pressed })
                        pinchActive = false
                        if (zs.z < 1.03f && zs.z != 1f) transformBy(Offset(wPx / 2f, hPx / 2f), Offset(wPx / 2f, hPx / 2f), 1f)
                    }
                }
                .pointerInput(wPx, hPx) {
                    detectTapGestures(
                        onTap = { onTap() },
                        onDoubleTap = { off ->
                            scope.launch {
                                val from = zs.z
                                val to = if (from > 1.3f) 1f else 2.5f
                                Animatable(0f).animateTo(1f, tween(300, easing = OneUiEasing)) {
                                    transformBy(off, off, from + (to - from) * value)
                                }
                            }
                        },
                        onLongPress = { off ->
                            if (!advancedPdf) return@detectTapGestures
                            val lx = (off.x - zs.tx) / zs.z
                            val ly = off.y / zs.z
                            // Item offsets don't include the top content padding (viewportStartOffset is minus that padding).
                            val info = listState.layoutInfo
                            val item = info.visibleItemsInfo.firstOrNull {
                                val y = it.offset - info.viewportStartOffset
                                ly >= y && ly < y + it.size
                            } ?: return@detectTapGestures
                            val top = item.offset - info.viewportStartOffset
                            onLongPress(item.index, lx / wPx * doc.pageW(item.index), (ly - top) / item.size * doc.pageH(item.index), off.y)
                        },
                    )
                },
        ) {
            Box(
                Modifier
                    .layout { m, c ->
                        val p = m.measure(Constraints.fixed(c.maxWidth, (c.maxHeight / zs.z).roundToInt().coerceAtLeast(1)))
                        layout(c.maxWidth, c.maxHeight) { p.place(0, 0) }
                    }
                    .graphicsLayer {
                        transformOrigin = TransformOrigin(0f, 0f)
                        scaleX = zs.z; scaleY = zs.z
                        translationX = zs.tx
                    },
            ) {
                LazyColumn(
                    state = listState,
                    // Off while pinching, so the first finger's drag can't turn into a fling that throws the page.
                    userScrollEnabled = !pinchActive,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = topPad, bottom = bottomPad),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(doc.pageCount, key = { it }) { i ->
                        val pw = doc.pageW(i); val ph = doc.pageH(i)
                        val hh = (wPx * ph / pw).roundToInt().coerceAtLeast(1)
                        PageCanvas(
                            doc, i, wPx, hh, night, tiles[i], search.byPage[i], search.current,
                            selection?.takeIf { it.page == i },
                            Modifier.fillMaxWidth().height(with(density) { hh.toDp() }),
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Single page (horizontal pager)
// ---------------------------------------------------------------------------------------------

@OptIn(FlowPreview::class)
@Composable
private fun SinglePages(
    doc: PdfDoc, where: Where, night: Boolean, search: SearchState, jump: Jump?,
    topPad: androidx.compose.ui.unit.Dp, bottomPad: androidx.compose.ui.unit.Dp,
    onScrolling: (Boolean) -> Unit, onTap: () -> Unit,
) {
    val pager = rememberPagerState(initialPage = where.first.coerceIn(0, doc.pageCount - 1)) { doc.pageCount }
    val density = LocalDensity.current
    // Swiping to the next page pauses while the current one is zoomed in (one finger pans it instead).
    var zoomed by remember { mutableStateOf(false) }
    LaunchedEffect(jump) { jump?.let { pager.scrollToPage(it.page) } }
    LaunchedEffect(pager) { snapshotFlow { pager.currentPage }.collect { where.page = it; where.first = it; where.frac = 0f } }
    LaunchedEffect(pager) { snapshotFlow { pager.isScrollInProgress }.collect { onScrolling(it) } }
    HorizontalPager(
        pager, Modifier.fillMaxSize(),
        pageSpacing = 12.dp, beyondViewportPageCount = 1, userScrollEnabled = !zoomed,
    ) { i ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(top = topPad, bottom = bottomPad), contentAlignment = Alignment.Center) {
            val ar = doc.pageW(i) / doc.pageH(i)
            val fitWidth = maxWidth / maxHeight < ar
            val w = if (fitWidth) maxWidth else maxHeight * ar
            val wPx = with(density) { w.roundToPx() }
            val hPx = (wPx / ar).roundToInt()
            val bw = constraints.maxWidth.toFloat()
            val bh = constraints.maxHeight.toFloat()
            // The page's rectangle inside this box before zooming.
            val px0 = (bw - wPx) / 2f
            val py0 = (bh - hPx) / 2f
            val zs = remember(i) { ZoomState(1f) }
            var ty by remember(i) { mutableFloatStateOf(0f) }
            var tile by remember(i) { mutableStateOf<Tile?>(null) }
            val scope = rememberCoroutineScope()

            /** Centres the page while it's smaller than the screen, otherwise keeps its edges on screen. */
            fun clampAxis(t: Float, z: Float, start: Float, len: Float, box: Float): Float {
                val scaled = len * z
                return if (scaled <= box) (box - scaled) / 2f - start * z
                else t.coerceIn(box - (start + len) * z, -start * z)
            }
            fun set(z: Float, tx: Float, tyNew: Float) {
                zs.z = z
                zs.tx = clampAxis(tx, z, px0, wPx.toFloat(), bw)
                ty = clampAxis(tyNew, z, py0, hPx.toFloat(), bh)
            }
            /** Keeps the page point under [prev] under [now] while the zoom becomes [newZoom]. */
            fun transformBy(prev: Offset, now: Offset, newZoom: Float) {
                val old = zs.z
                val nz = newZoom.coerceIn(1f, MAX_ZOOM)
                val lx = (prev.x - zs.tx) / old
                val ly = (prev.y - ty) / old
                set(nz, now.x - lx * nz, now.y - ly * nz)
            }

            // Leaving the page resets its zoom.
            LaunchedEffect(pager, i) {
                snapshotFlow { pager.currentPage }.collect { if (it != i && zs.z != 1f) { set(1f, 0f, 0f); tile = null } }
            }
            LaunchedEffect(pager, i) {
                snapshotFlow { pager.currentPage == i && zs.z > 1.01f }.collect { if (pager.currentPage == i) zoomed = it }
            }
            // Sharp text: once the zoom settles, render the visible part of the page at full zoom.
            LaunchedEffect(doc, i, wPx) {
                snapshotFlow { Triple(zs.z, zs.tx, ty) }.debounce(170).collectLatest { (z, tx, tyv) ->
                    if (z < 1.15f) { tile = null; return@collectLatest }
                    // Visible screen area in page-canvas pixels.
                    val l0 = ((0f - tx) / z - px0).coerceIn(0f, wPx.toFloat())
                    val r0 = ((bw - tx) / z - px0).coerceIn(0f, wPx.toFloat())
                    val t0 = ((0f - tyv) / z - py0).coerceIn(0f, hPx.toFloat())
                    val b0 = ((bh - tyv) / z - py0).coerceIn(0f, hPx.toFloat())
                    if (r0 - l0 < 8f || b0 - t0 < 8f) return@collectLatest
                    val l = l0.toInt(); val t = t0.toInt()
                    val rw = ceil(r0).toInt() - l; val rh = ceil(b0).toInt() - t
                    var f = 1f
                    val px = rw * z * rh * z
                    if (px > 14_000_000f) f = sqrt(14_000_000f / px)
                    val sc = z * f
                    val bmp = doc.tile(i, wPx * sc / doc.pageW(i), (l * sc).roundToInt(), (t * sc).roundToInt(), (rw * sc).roundToInt(), (rh * sc).roundToInt())
                        ?: return@collectLatest
                    ensureActive()
                    tile = Tile(bmp.asImageBitmap(), l, t, rw, rh)
                }
            }

            Box(
                Modifier
                    .fillMaxSize()
                    // Pinch zoom, and one-finger pan while zoomed; seen before the pager so it can't turn pages meanwhile.
                    .pointerInput(wPx, hPx) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                            var pinching = false
                            do {
                                val ev = awaitPointerEvent(PointerEventPass.Initial)
                                val down = ev.changes.count { it.pressed }
                                if (down >= 2) {
                                    pinching = true
                                    val zc = ev.calculateZoom()
                                    val prev = ev.calculateCentroid(useCurrent = false)
                                    val now = ev.calculateCentroid()
                                    if (prev.isSpecified && now.isSpecified) transformBy(prev, now, zs.z * zc)
                                    ev.changes.forEach { if (it.positionChanged()) it.consume() }
                                } else if (!pinching && down == 1 && zs.z > 1.01f) {
                                    val ch = ev.changes.first { it.pressed }
                                    val d = ch.position - ch.previousPosition
                                    if (d != Offset.Zero) { set(zs.z, zs.tx + d.x, ty + d.y); ch.consume() }
                                }
                            } while (ev.changes.any { it.pressed })
                            if (zs.z < 1.03f && zs.z != 1f) set(1f, 0f, 0f)
                        }
                    }
                    .pointerInput(wPx, hPx) {
                        detectTapGestures(
                            onTap = { onTap() },
                            onDoubleTap = { off ->
                                scope.launch {
                                    val from = zs.z
                                    val to = if (from > 1.3f) 1f else 2.5f
                                    Animatable(0f).animateTo(1f, tween(300, easing = OneUiEasing)) {
                                        transformBy(off, off, from + (to - from) * value)
                                    }
                                }
                            },
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                PageCanvas(
                    doc, i, wPx, hPx, night, tile, search.byPage[i], search.current, null,
                    Modifier
                        .size(w, with(density) { hPx.toDp() })
                        .graphicsLayer {
                            // Scale about the box's top-left: move the page so its own corner sits at (px0, py0)·z + t.
                            transformOrigin = TransformOrigin(0f, 0f)
                            scaleX = zs.z; scaleY = zs.z
                            translationX = zs.tx + px0 * zs.z - px0
                            translationY = ty + py0 * zs.z - py0
                        },
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------

@Composable
private fun PageCanvas(
    doc: PdfDoc, index: Int, wPx: Int, hPx: Int, night: Boolean, tile: Tile?,
    hits: List<Pair<Int, List<RectF>>>?, current: Int, selection: Selection?, modifier: Modifier,
) {
    val c = OneUi.colors
    val bmp by produceState(doc.cached(index, wPx), doc, index, wPx) { if (value == null) value = doc.page(index, wPx, hPx) }
    val img = remember(bmp) { bmp?.asImageBitmap() }
    val pw = doc.pageW(index)
    val accent = c.accent
    Canvas(modifier) {
        drawRect(if (night) Color(0xFF1A1A1A) else Color.White)
        img?.let { drawImage(it, dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()), colorFilter = if (night) InvertFilter else null) }
        tile?.let { drawImage(it.img, dstOffset = IntOffset(it.left, it.top), dstSize = IntSize(it.w, it.h), colorFilter = if (night) InvertFilter else null) }
        val sx = size.width / pw
        if (hits != null) for ((hi, rects) in hits) {
            val col = if (hi == current) Color(0x99FF8A00) else Color(0x66FFE600)
            for (r in rects) drawRect(col, Offset(r.left * sx, r.top * sx), Size(r.width() * sx, r.height() * sx))
        }
        if (selection != null) for (r in selection.rects) drawRect(accent.copy(alpha = 0.35f), Offset(r.left * sx, r.top * sx), Size(r.width() * sx, r.height() * sx))
    }
}

@Composable
private fun FastScroller(
    listState: LazyListState, count: Int, zs: ZoomState, trackTop: Float, trackBottom: Float,
    show: Boolean, onDragging: (Boolean) -> Unit,
) {
    val c = OneUi.colors
    val scope = rememberCoroutineScope()
    val thumbH = with(LocalDensity.current) { 56.dp.toPx() }
    var dragFrac by remember { mutableStateOf<Float?>(null) }
    val maxPos by remember {
        derivedStateOf {
            val i = listState.layoutInfo.visibleItemsInfo.firstOrNull()
            val vp = listState.layoutInfo.viewportSize.height
            if (i == null) 1f else (count - vp / (i.size + 6f)).coerceAtLeast(1f)
        }
    }
    val frac by remember {
        derivedStateOf {
            val i = listState.layoutInfo.visibleItemsInfo.firstOrNull()
            if (i == null) 0f else ((listState.firstVisibleItemIndex + listState.firstVisibleItemScrollOffset / i.size.toFloat()) / maxPos).coerceIn(0f, 1f)
        }
    }
    val shown = show || dragFrac != null
    AnimatedVisibility(shown, enter = fadeIn(tween(150)), exit = fadeOut(tween(300))) {
        val f = dragFrac ?: frac
        Box(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset { IntOffset(0, (trackTop + f * (trackBottom - trackTop - thumbH)).roundToInt()) }
                    .size(width = 34.dp, height = 56.dp)
                    .pointerInput(count) {
                        detectVerticalDragGestures(
                            onDragStart = { dragFrac = frac; onDragging(true) },
                            onDragEnd = { dragFrac = null; onDragging(false) },
                            onDragCancel = { dragFrac = null; onDragging(false) },
                        ) { change, dy ->
                            change.consume()
                            val nf = ((dragFrac ?: 0f) + dy / (trackBottom - trackTop - thumbH)).coerceIn(0f, 1f)
                            dragFrac = nf
                            val pos = nf * maxPos
                            val idx = pos.toInt().coerceIn(0, count - 1)
                            val size = listState.layoutInfo.visibleItemsInfo.firstOrNull()?.size ?: 0
                            scope.launch { listState.scrollToItem(idx, ((pos - idx) * size).toInt()) }
                        }
                    },
                contentAlignment = Alignment.CenterEnd,
            ) {
                Box(
                    Modifier.padding(end = 4.dp).size(width = if (dragFrac != null) 8.dp else 5.dp, height = 56.dp)
                        .clip(CircleShape).background(if (dragFrac != null) c.accent else Color(0xAA8E8E93)),
                )
            }
        }
    }
}
