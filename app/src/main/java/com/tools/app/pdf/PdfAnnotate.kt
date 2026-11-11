package com.tools.app.pdf

import android.graphics.Bitmap
import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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

private val PenColors = listOf(0xFF111111.toInt(), 0xFF2457E6.toInt(), 0xFFE5392F.toInt(), 0xFF2E9E4F.toInt(), 0xFFF08C1A.toInt())
private val PenWidths = listOf(0.004f, 0.009f, 0.018f)
private val TextSizes = listOf(0.03f, 0.045f, 0.07f)

private fun norm(d: Int) = ((d % 360) + 360) % 360

@Composable
fun PdfAnnotateScreen(item: PageItem, pageNumber: Int, onCancel: () -> Unit, onDone: (List<Annot>) -> Unit) {
    val c = OneUi.colors
    val d = norm(item.rot)
    val annots = remember { mutableStateListOf<Annot>().apply { addAll(item.annots) } }
    val current = remember { mutableStateListOf<Offset>() }
    var tool by remember { mutableStateOf(0) }
    var color by remember { mutableStateOf(PenColors[0]) }
    var sizeIdx by remember { mutableStateOf(1) }
    var canvas by remember { mutableStateOf(IntSize.Zero) }
    var askText by remember { mutableStateOf<Offset?>(null) }
    var padOpen by remember { mutableStateOf(false) }

    val bitmap by produceState<Bitmap?>(null, item.id, d) {
        val b = item.src.render(item.index, 1500)
        value = if (b != null && d != 0) {
            val m = android.graphics.Matrix().apply { postRotate(d.toFloat()) }
            Bitmap.createBitmap(b, 0, 0, b.width, b.height, m, true)
        } else b
    }

    fun toBase(px: Offset): Offset = rotNorm(Offset(px.x / canvas.width, px.y / canvas.height), 360 - d)
    fun baseWpx() = (if (d % 180 != 0) canvas.height else canvas.width).toFloat()

    fun commitStroke() {
        if (current.isNotEmpty()) annots += Annot.Stroke(current.map(::toBase), color, PenWidths[sizeIdx])
        current.clear()
    }

    fun placeSignature(at: Offset) {
        val sig = PdfSession.signature ?: return
        val wPx = canvas.width * (if (d % 180 != 0) 0.3f else 0.4f).coerceAtMost(0.5f)
        val hPx = wPx / PdfSession.signatureAspect
        val x0 = (at.x - wPx / 2).coerceIn(0f, (canvas.width - wPx).coerceAtLeast(0f))
        val y0 = (at.y - hPx / 2).coerceIn(0f, (canvas.height - hPx).coerceAtLeast(0f))
        for (s in sig) if (s.isNotEmpty()) annots += Annot.Stroke(s.map { toBase(Offset(x0 + it.x * wPx, y0 + it.y * hPx)) }, color, 0.006f)
    }

    ToolPage(
        "Edit page $pageNumber", onCancel,
        actions = { ToolbarIcon(OneUiIcons.Check, "Done") { onDone(annots.toList()) } },
    ) {
        Box(Modifier.weight(1f).fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
            val bmp = bitmap
            if (bmp == null) Text("Loading page…", color = c.textSecondary, fontSize = 15.sp)
            else {
                val img = remember(bmp) { bmp.asImageBitmap() }
                val paint = remember { Paint(Paint.ANTI_ALIAS_FLAG) }
                Canvas(
                    Modifier
                        .aspectRatio(bmp.width.toFloat() / bmp.height)
                        .clip(RoundedCornerShape(6.dp))
                        .onSizeChanged { canvas = it }
                        .pointerInput(tool, color, sizeIdx, canvas, d) {
                            if (tool == 0) detectDragGestures(
                                onDragStart = { current.clear(); current += it },
                                onDrag = { ch, _ -> ch.consume(); current += ch.position },
                                onDragEnd = { commitStroke() },
                                onDragCancel = { commitStroke() },
                            )
                        }
                        .pointerInput(tool, color, sizeIdx, canvas, d) {
                            detectTapGestures { p ->
                                when (tool) {
                                    0 -> annots += Annot.Stroke(listOf(toBase(p)), color, PenWidths[sizeIdx])
                                    1 -> askText = p
                                    else -> if (PdfSession.signature == null) padOpen = true else placeSignature(p)
                                }
                            }
                        },
                ) {
                    drawImage(img, dstSize = IntSize(size.width.toInt(), size.height.toInt()))
                    val bw = if (d % 180 != 0) size.height else size.width
                    fun vis(p: Offset) = rotNorm(p, d).let { Offset(it.x * size.width, it.y * size.height) }
                    fun drawStroke(pts: List<Offset>, col: Int, wPx: Float) {
                        if (pts.isEmpty()) return
                        val path = Path()
                        path.moveTo(pts[0].x, pts[0].y)
                        if (pts.size == 1) path.lineTo(pts[0].x + 0.1f, pts[0].y)
                        for (i in 1 until pts.size) path.lineTo(pts[i].x, pts[i].y)
                        drawPath(path, Color(col), style = Stroke(wPx.coerceAtLeast(1f), cap = StrokeCap.Round, join = StrokeJoin.Round))
                    }
                    for (a in annots) when (a) {
                        is Annot.Stroke -> drawStroke(a.pts.map(::vis), a.color, a.width * bw)
                        is Annot.Label -> {
                            val at = vis(a.at)
                            paint.color = a.color; paint.textSize = a.size * bw
                            val nc = drawContext.canvas.nativeCanvas
                            nc.save(); nc.rotate((d - a.rot).toFloat(), at.x, at.y)
                            a.text.lines().forEachIndexed { k, l -> nc.drawText(l, at.x, at.y + k * paint.textSize * 1.2f, paint) }
                            nc.restore()
                        }
                    }
                    drawStroke(current.toList(), color, PenWidths[sizeIdx] * bw)
                }
            }
        }

        Column(
            Modifier.padding(horizontal = CardMargin, vertical = 6.dp).fillMaxWidth().clip(OneUi.CardShape).background(c.card).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SegmentedControl(listOf("Draw", "Text", "Signature"), tool, { tool = it })
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PenColors.forEach { col ->
                    val sel = col == color
                    Box(
                        Modifier.size(30.dp).clip(CircleShape).background(Color(col))
                            .then(if (sel) Modifier.border(3.dp, c.card, CircleShape).border(5.dp, c.accent, CircleShape) else Modifier)
                            .clickable { color = col },
                    )
                }
            }
            if (tool != 2) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (tool == 0) "Pen width" else "Text size", fontSize = 15.sp, color = c.textSecondary, modifier = Modifier.padding(end = 14.dp))
                    SegmentedControl(listOf("S", "M", "L"), sizeIdx, { sizeIdx = it }, Modifier.weight(1f))
                }
            } else {
                Text(
                    if (PdfSession.signature == null) "Tap the page to draw your signature, then tap again to place it."
                    else "Tap the page to place your signature.",
                    fontSize = 14.sp, color = c.textSecondary,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PillButton("Undo", { if (annots.isNotEmpty()) annots.removeAt(annots.lastIndex) }, Modifier.weight(1f), tonal = true, enabled = annots.isNotEmpty())
                if (tool == 2) PillButton("Redraw", { padOpen = true }, Modifier.weight(1f), tonal = true)
                PillButton("Done", { onDone(annots.toList()) }, Modifier.weight(1f))
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
                    annots += Annot.Label(toBase(baseline), text, color, sz, d)
                }
                askText = null
            }),
        ) { DialogField(text, { text = it }, "Type something", singleLine = false) }
    }

    if (padOpen) SignaturePadDialog(onDismiss = { padOpen = false }, onSaved = { padOpen = false })
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
