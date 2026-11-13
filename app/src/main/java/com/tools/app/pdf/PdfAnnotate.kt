package com.tools.app.pdf

import android.graphics.Bitmap
import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tools.app.ui.CardMargin
import com.tools.app.ui.OneUiDialog
import com.tools.app.ui.OneUiIcons
import com.tools.app.ui.PillButton
import com.tools.app.ui.SegmentedControl
import com.tools.app.ui.ToolPage
import com.tools.app.ui.ToolbarIcon
import com.tools.app.ui.theme.OneUi

/** Plenty of colours: neutrals, then the rainbow, then earthy tones. */
private val PenColors = listOf(
    0xFF111111, 0xFF5F6368, 0xFFB0B3B8, 0xFFFFFFFF,
    0xFFE5392F, 0xFFF06292, 0xFFF08C1A, 0xFFFFC21A,
    0xFFFFEB3B, 0xFF8BC34A, 0xFF2E9E4F, 0xFF009688,
    0xFF00B8D4, 0xFF2457E6, 0xFF3F51B5, 0xFF8E44AD,
    0xFFBA68C8, 0xFF795548, 0xFFD7A86E, 0xFF263238,
).map { it.toInt() }
private val PenWidths = listOf(0.0025f, 0.005f, 0.009f, 0.015f, 0.025f)
private val TextSizes = listOf(0.025f, 0.035f, 0.045f, 0.06f, 0.08f)
/** Highlighter: wider and see-through. */
private const val MarkerWidth = 3.2f
private const val MarkerAlpha = 0x66

private enum class Tool(val label: String) { Pen("Pen"), Marker("Marker"), Eraser("Eraser"), Text("Text"), Sign("Sign") }

private fun norm(d: Int) = ((d % 360) + 360) % 360

/** S Pen in use: once seen, fingers stop drawing (palm rejection) and pan/zoom instead. Kept for the session. */
private var penSeen by mutableStateOf(false)

@Composable
fun PdfAnnotateScreen(
    item: PageItem, pageNumber: Int, pageCount: Int,
    onCancel: () -> Unit, onDone: (List<Annot>) -> Unit,
    /** Saves this page and opens the one [delta] away (previous / next). */
    onGo: (List<Annot>, Int) -> Unit = { _, _ -> },
) {
    val c = OneUi.colors
    val d = norm(item.rot)
    val annots = remember { mutableStateListOf<Annot>().apply { addAll(item.annots) } }
    val undo = remember { mutableStateListOf<List<Annot>>() }
    val redo = remember { mutableStateListOf<List<Annot>>() }
    val current = remember { mutableStateListOf<Offset>() }
    var tool by remember { mutableStateOf(Tool.Pen) }
    var color by remember { mutableIntStateOf(PenColors[0]) }
    var sizeIdx by remember { mutableIntStateOf(1) }
    var erasingNow by remember { mutableStateOf(false) }
    var canvas by remember { mutableStateOf(IntSize.Zero) }
    var canvasPos by remember { mutableStateOf(Offset.Zero) }
    var askText by remember { mutableStateOf<Offset?>(null) }
    var padOpen by remember { mutableStateOf(false) }
    // Zoom (pinch with two fingers): canvas point p shows at canvasPos + p·z + t.
    var z by remember { mutableFloatStateOf(1f) }
    var t by remember { mutableStateOf(Offset.Zero) }

    val bitmap by produceState<Bitmap?>(null, item.id, d) {
        val b = item.src.render(item.index, 2000)
        value = if (b != null && d != 0) {
            val m = android.graphics.Matrix().apply { postRotate(d.toFloat()) }
            Bitmap.createBitmap(b, 0, 0, b.width, b.height, m, true)
        } else b
    }

    fun snapshot() { undo += annots.toList(); if (undo.size > 100) undo.removeAt(0); redo.clear() }
    fun toBase(px: Offset): Offset = rotNorm(Offset(px.x / canvas.width, px.y / canvas.height), 360 - d)
    fun baseWpx() = (if (d % 180 != 0) canvas.height else canvas.width).toFloat()
    fun vis(p: Offset) = rotNorm(p, d).let { Offset(it.x * canvas.width, it.y * canvas.height) }
    fun strokeColor() = if (tool == Tool.Marker) (color and 0x00FFFFFF) or (MarkerAlpha shl 24) else color
    fun strokeWidth() = PenWidths[sizeIdx] * if (tool == Tool.Marker) MarkerWidth else 1f

    fun commitStroke() {
        if (current.isNotEmpty()) { snapshot(); annots += Annot.Stroke(current.map(::toBase), strokeColor(), strokeWidth()) }
        current.clear()
    }

    /** Removes every mark within reach of [at] (canvas pixels). Returns whether anything went. */
    fun eraseAt(at: Offset, snapshotFirst: Boolean): Boolean {
        val bw = baseWpx()
        val reach = (PenWidths[sizeIdx] * bw * 2.5f).coerceAtLeast(14f)
        val hit = annots.filter { a ->
            when (a) {
                is Annot.Stroke -> {
                    val r = reach + a.width * bw / 2
                    val pts = a.pts.map(::vis)
                    pts.indices.any { i ->
                        val p = pts[i]; val q = if (i + 1 < pts.size) pts[i + 1] else p
                        distToSegment(at, p, q) <= r
                    }
                }
                is Annot.Label -> (vis(a.at) - at).getDistance() <= reach + a.size * bw
            }
        }
        if (hit.isEmpty()) return false
        if (snapshotFirst) snapshot()
        annots.removeAll(hit.toSet())
        return true
    }

    fun placeSignature(at: Offset) {
        val sig = PdfSession.signature ?: return
        val wPx = canvas.width * (if (d % 180 != 0) 0.3f else 0.4f).coerceAtMost(0.5f)
        val hPx = wPx / PdfSession.signatureAspect
        val x0 = (at.x - wPx / 2).coerceIn(0f, (canvas.width - wPx).coerceAtLeast(0f))
        val y0 = (at.y - hPx / 2).coerceIn(0f, (canvas.height - hPx).coerceAtLeast(0f))
        snapshot()
        for (s in sig) if (s.isNotEmpty()) annots += Annot.Stroke(s.map { toBase(Offset(x0 + it.x * wPx, y0 + it.y * hPx)) }, color, 0.006f)
    }

    fun clampT(v: Offset, zz: Float): Offset {
        if (zz <= 1f) return Offset.Zero
        return Offset(v.x.coerceIn(canvas.width - canvas.width * zz, 0f), v.y.coerceIn(canvas.height - canvas.height * zz, 0f))
    }
    /** Keeps the canvas point under [prev] under [now] while the zoom becomes [nz] (positions in the outer box). */
    fun transformBy(prev: Offset, now: Offset, nz0: Float) {
        val nz = nz0.coerceIn(1f, 6f)
        val p = (prev - canvasPos - t) / z
        t = clampT(now - canvasPos - p * nz, nz)
        z = nz
    }

    ToolPage(
        "Edit page $pageNumber", onCancel,
        actions = {
            if (pageCount > 1) {
                if (pageNumber > 1) ToolbarIcon(OneUiIcons.ChevronUp, "Previous page") { commitStroke(); onGo(annots.toList(), -1) }
                if (pageNumber < pageCount) ToolbarIcon(OneUiIcons.ChevronDown, "Next page") { commitStroke(); onGo(annots.toList(), 1) }
            }
        },
    ) {
        Box(
            Modifier
                .weight(1f).fillMaxWidth().clipToBounds().padding(12.dp)
                // Two fingers pinch-zoom and pan; with the S Pen in use, one finger pans too.
                .pointerInput(canvas) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        var pinching = false
                        do {
                            val ev = awaitPointerEvent(PointerEventPass.Initial)
                            val fingers = ev.changes.filter { it.pressed && it.type != PointerType.Stylus }
                            if (fingers.size >= 2) {
                                if (!pinching) current.clear()
                                pinching = true
                                val zc = ev.calculateZoom()
                                val prev = ev.calculateCentroid(useCurrent = false)
                                val now = ev.calculateCentroid()
                                if (prev.isSpecified && now.isSpecified) transformBy(prev, now, z * zc)
                                ev.changes.forEach { if (it.positionChanged()) it.consume() }
                            } else if (!pinching && penSeen && fingers.size == 1 && ev.changes.none { it.type == PointerType.Stylus && it.pressed }) {
                                val ch = fingers[0]
                                val dd = ch.position - ch.previousPosition
                                if (dd != Offset.Zero && z > 1f) { t = clampT(t + dd, z); ch.consume() }
                            }
                        } while (ev.changes.any { it.pressed })
                        if (z < 1.04f) { z = 1f; t = Offset.Zero }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            val bmp = bitmap
            if (bmp == null) Text("Loading page…", color = c.textSecondary, fontSize = 15.sp)
            else {
                val img = remember(bmp) { bmp.asImageBitmap() }
                val paint = remember { Paint(Paint.ANTI_ALIAS_FLAG) }
                val drawing = tool == Tool.Pen || tool == Tool.Marker || tool == Tool.Eraser
                Canvas(
                    Modifier
                        .aspectRatio(bmp.width.toFloat() / bmp.height)
                        .onPlaced { canvasPos = it.positionInParent() }
                        .graphicsLayer {
                            transformOrigin = TransformOrigin(0f, 0f)
                            scaleX = z; scaleY = z
                            translationX = t.x; translationY = t.y
                        }
                        .clip(RoundedCornerShape(6.dp))
                        .onSizeChanged { canvas = it }
                        // Pen, marker and eraser: start right at the touch (no drag slop), so the S Pen feels immediate.
                        .pointerInput(drawing, tool, color, sizeIdx, canvas, d) {
                            if (!drawing) return@pointerInput
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                val pen = down.type == PointerType.Stylus
                                if (pen) penSeen = true
                                if (penSeen && !pen) return@awaitEachGesture // palm or finger while using the S Pen
                                // The S Pen's side button erases while held.
                                var erasing = tool == Tool.Eraser || (pen && currentEvent.buttons.isSecondaryPressed)
                                var erased = false
                                erasingNow = erasing
                                current.clear()
                                if (erasing) erased = eraseAt(down.position, true) else current += down.position
                                down.consume()
                                while (true) {
                                    val ev = awaitPointerEvent()
                                    if (ev.changes.count { it.pressed && it.type != PointerType.Stylus } >= 2) { current.clear(); break } // pinch
                                    // Follow whichever pointer is still down (the S Pen can change id between hover and touch).
                                    val ch = ev.changes.firstOrNull { it.pressed && (it.id == down.id || it.type == down.type) } ?: break
                                    if (pen && !erasing && ev.buttons.isSecondaryPressed) { commitStroke(); erasing = true; erasingNow = true }
                                    val pts = ch.historical.map { it.position } + ch.position
                                    for (p in pts) {
                                        if (erasing) { if (eraseAt(p, !erased)) erased = true } else current += p
                                    }
                                    ch.consume()
                                }
                                erasingNow = false
                                commitStroke()
                            }
                        }
                        .pointerInput(tool, color, sizeIdx, canvas, d) {
                            if (drawing) return@pointerInput
                            detectTapGestures { p ->
                                when (tool) {
                                    Tool.Text -> askText = p
                                    else -> if (PdfSession.signature == null) padOpen = true else placeSignature(p)
                                }
                            }
                        },
                ) {
                    drawImage(img, dstSize = IntSize(size.width.toInt(), size.height.toInt()))
                    val bw = if (d % 180 != 0) size.height else size.width
                    fun visD(p: Offset) = rotNorm(p, d).let { Offset(it.x * size.width, it.y * size.height) }
                    fun drawStroke(pts: List<Offset>, col: Int, wPx: Float) {
                        if (pts.isEmpty()) return
                        val path = Path()
                        path.moveTo(pts[0].x, pts[0].y)
                        if (pts.size == 1) path.lineTo(pts[0].x + 0.1f, pts[0].y)
                        // Smooth: quadratic curves through the midpoints of the sampled points.
                        for (i in 1 until pts.size) {
                            val a = pts[i - 1]; val b = pts[i]
                            path.quadraticTo(a.x, a.y, (a.x + b.x) / 2, (a.y + b.y) / 2)
                        }
                        if (pts.size > 1) path.lineTo(pts.last().x, pts.last().y)
                        drawPath(path, Color(col), style = Stroke(wPx.coerceAtLeast(1f), cap = StrokeCap.Round, join = StrokeJoin.Round))
                    }
                    for (a in annots) when (a) {
                        is Annot.Stroke -> drawStroke(a.pts.map(::visD), a.color, a.width * bw)
                        is Annot.Label -> {
                            val at = visD(a.at)
                            paint.color = a.color; paint.textSize = a.size * bw
                            val nc = drawContext.canvas.nativeCanvas
                            nc.save(); nc.rotate((d - a.rot).toFloat(), at.x, at.y)
                            a.text.lines().forEachIndexed { k, l -> nc.drawText(l, at.x, at.y + k * paint.textSize * 1.2f, paint) }
                            nc.restore()
                        }
                    }
                    if (!erasingNow) drawStroke(current.toList(), strokeColor(), strokeWidth() * bw)
                }
            }
        }

        Column(
            Modifier.padding(horizontal = CardMargin, vertical = 6.dp).fillMaxWidth().clip(OneUi.CardShape).background(c.card).padding(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(Modifier.padding(horizontal = 16.dp)) {
                SegmentedControl(Tool.entries.map { it.label }, tool.ordinal, { tool = Tool.entries[it] })
            }
            if (tool != Tool.Eraser) Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                PenColors.forEach { col ->
                    val sel = col == color
                    Box(
                        Modifier.size(32.dp).clip(CircleShape).background(Color(col))
                            .border(1.dp, c.textSecondary.copy(alpha = 0.35f), CircleShape)
                            .then(if (sel) Modifier.border(3.dp, c.card, CircleShape).border(5.dp, c.accent, CircleShape) else Modifier)
                            .clickable { color = col },
                    )
                }
            }
            if (tool != Tool.Sign) {
                Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        when (tool) { Tool.Text -> "Text size"; Tool.Eraser -> "Eraser size"; else -> "Brush size" },
                        fontSize = 15.sp, color = c.textSecondary, modifier = Modifier.weight(1f),
                    )
                    PenWidths.indices.forEach { i ->
                        val sel = i == sizeIdx
                        Box(
                            Modifier.size(40.dp).clip(CircleShape).background(if (sel) c.accentContainer else Color.Transparent).clickable { sizeIdx = i },
                            contentAlignment = Alignment.Center,
                        ) {
                            val dot = (4 + i * 4).dp
                            Box(Modifier.size(dot).clip(CircleShape).background(if (sel) c.accent else c.text.copy(alpha = 0.75f)))
                        }
                    }
                }
            } else {
                Text(
                    if (PdfSession.signature == null) "Tap the page to draw your signature, then tap again to place it."
                    else "Tap the page to place your signature.",
                    fontSize = 14.sp, color = c.textSecondary, modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PillButton("Undo", {
                    if (undo.isNotEmpty()) { redo += annots.toList(); val prev = undo.removeAt(undo.lastIndex); annots.clear(); annots.addAll(prev) }
                }, Modifier.weight(1f), tonal = true, enabled = undo.isNotEmpty())
                PillButton("Redo", {
                    if (redo.isNotEmpty()) { undo += annots.toList(); val next = redo.removeAt(redo.lastIndex); annots.clear(); annots.addAll(next) }
                }, Modifier.weight(1f), tonal = true, enabled = redo.isNotEmpty())
                if (tool == Tool.Sign) PillButton("Redraw", { padOpen = true }, Modifier.weight(1f), tonal = true)
                PillButton("Done", { commitStroke(); onDone(annots.toList()) }, Modifier.weight(1f))
            }
        }
    }

    askText?.let { at ->
        var text by remember { mutableStateOf("") }
        OneUiDialog(
            "Add text", onDismiss = { askText = null },
            buttons = listOf("Cancel" to { askText = null }, "Add" to {
                if (text.isNotBlank()) {
                    val sz = TextSizes[sizeIdx]
                    val baseline = Offset(at.x, at.y + sz * baseWpx() * 0.85f)
                    snapshot()
                    annots += Annot.Label(toBase(baseline), text, color, sz, d)
                }
                askText = null
            }),
        ) { DialogField(text, { text = it }, "Type something", singleLine = false) }
    }

    if (padOpen) SignaturePadDialog(onDismiss = { padOpen = false }, onSaved = { padOpen = false })
}

private fun distToSegment(p: Offset, a: Offset, b: Offset): Float {
    val ab = b - a
    val len2 = ab.x * ab.x + ab.y * ab.y
    if (len2 == 0f) return (p - a).getDistance()
    val tt = (((p.x - a.x) * ab.x + (p.y - a.y) * ab.y) / len2).coerceIn(0f, 1f)
    return (p - (a + ab * tt)).getDistance()
}

@Composable
internal fun DialogField(
    value: String, onChange: (String) -> Unit, hint: String,
    password: Boolean = false, singleLine: Boolean = true,
) {
    val c = OneUi.colors
    androidx.compose.foundation.text.BasicTextField(
        value, onChange,
        textStyle = androidx.compose.ui.text.TextStyle(color = c.text, fontSize = 16.sp),
        cursorBrush = androidx.compose.ui.graphics.SolidColor(c.accent),
        singleLine = singleLine,
        visualTransformation = if (password) androidx.compose.ui.text.input.PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
            keyboardType = if (password) androidx.compose.ui.text.input.KeyboardType.Password else androidx.compose.ui.text.input.KeyboardType.Text,
        ),
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.tonal).padding(14.dp),
        decorationBox = { inner ->
            Box { if (value.isEmpty()) Text(hint, color = c.textSecondary, fontSize = 16.sp); inner() }
        },
    )
}

@Composable
private fun SignaturePadDialog(onDismiss: () -> Unit, onSaved: () -> Unit) {
    val c = OneUi.colors
    val strokes = remember { mutableStateListOf<List<Offset>>() }
    val cur = remember { mutableStateListOf<Offset>() }
    var size by remember { mutableStateOf(IntSize.Zero) }
    OneUiDialog(
        "Your signature", onDismiss,
        buttons = listOf(
            "Clear" to { strokes.clear(); cur.clear() },
            "Cancel" to onDismiss,
            "Use" to {
                if (strokes.isNotEmpty() && size.width > 0) {
                    PdfSession.signature = strokes.map { s -> s.map { Offset((it.x / size.width).coerceIn(0f, 1f), (it.y / size.height).coerceIn(0f, 1f)) } }
                    PdfSession.signatureAspect = size.width.toFloat() / size.height
                    onSaved()
                }
            },
        ),
    ) {
        Canvas(
            Modifier.fillMaxWidth().height(160.dp).clip(RoundedCornerShape(18.dp)).background(Color.White)
                .onSizeChanged { size = it }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { cur.clear(); cur += it },
                        onDrag = { ch, _ -> ch.consume(); cur += ch.position },
                        onDragEnd = { if (cur.isNotEmpty()) strokes += cur.toList(); cur.clear() },
                        onDragCancel = { if (cur.isNotEmpty()) strokes += cur.toList(); cur.clear() },
                    )
                },
        ) {
            val line = size.height * 0.78f
            drawLine(Color(0xFFBBBBBB), Offset(24f, line), Offset(this.size.width - 24f, line), 2f)
            fun draw(pts: List<Offset>) {
                if (pts.isEmpty()) return
                val p = Path(); p.moveTo(pts[0].x, pts[0].y)
                if (pts.size == 1) p.lineTo(pts[0].x + 0.1f, pts[0].y)
                for (i in 1 until pts.size) p.lineTo(pts[i].x, pts[i].y)
                drawPath(p, Color(0xFF111111), style = Stroke(5f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            strokes.forEach { draw(it) }
            draw(cur.toList())
        }
        Text("Sign with your finger. It's kept until you leave the app.", fontSize = 13.sp, color = c.textSecondary, modifier = Modifier.padding(top = 8.dp))
    }
}
