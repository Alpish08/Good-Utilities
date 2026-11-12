package com.tools.app.blur

import com.tools.app.ui.theme.IconColors
import androidx.compose.material.icons.rounded.BlurOn
import androidx.compose.material.icons.Icons
import com.tools.app.ui.OneUiIcons
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas as ComposeCanvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tools.app.ui.CardMargin
import com.tools.app.ui.EmptyState
import com.tools.app.ui.Images
import com.tools.app.ui.roundedImage
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import com.tools.app.ui.OneUiSlider
import com.tools.app.ui.PillButton
import com.tools.app.ui.SegmentedControl
import com.tools.app.ui.ToolPage
import com.tools.app.ui.ToolbarIcon
import com.tools.app.ui.theme.OneUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.min
import kotlin.math.roundToInt

/** A brush stroke in image pixel coordinates. */
private class BrushStroke(val points: List<Offset>, val width: Float)

@Composable
fun BlurScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val c = OneUi.colors
    var original by remember { mutableStateOf<Bitmap?>(null) }
    var originalImage by remember { mutableStateOf<ImageBitmap?>(null) }
    var blurred by remember { mutableStateOf<Bitmap?>(null) }
    var blurredImage by remember { mutableStateOf<ImageBitmap?>(null) }
    var mode by remember { mutableIntStateOf(0) } // 0 whole, 1 brush
    var strength by remember { mutableFloatStateOf(0.5f) }
    var brush by remember { mutableFloatStateOf(0.4f) }
    val strokes = remember { mutableStateListOf<BrushStroke>() }
    var current by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var saving by remember { mutableStateOf(false) }

    val chooser = rememberLauncherForActivityResult(com.tools.app.ui.GalleryPick()) { uri ->
        if (uri != null) scope.launch {
            val bmp = withContext(Dispatchers.IO) { runCatching { Images.load(context, uri, 4096) }.getOrNull() } ?: return@launch
            strokes.clear(); original = bmp; originalImage = bmp.asImageBitmap()
        }
    }
    val choose = { chooser.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }

    LaunchedEffect(original, strength) {
        val src = original ?: return@LaunchedEffect
        delay(60) // debounce slider drags
        val b = withContext(Dispatchers.Default) { BoxBlur.blur(src, strength) }
        blurred = b; blurredImage = b?.asImageBitmap()
    }

    ToolPage("Image blur", onBack, actions = { if (original != null) ToolbarIcon(com.tools.app.ui.OneUiIcons.Gallery, "Choose image", choose) }) {
        val src = original; val img = originalImage
        if (src == null || img == null) {
            EmptyState(Icons.Rounded.BlurOn, IconColors.Purple, "Blur a photo", "Blur the whole image, or paint over faces, plates and details to hide them.", "Choose image", choose)
            return@ToolPage
        }
        val brushPx = brush * 0.12f * maxOf(src.width, src.height) + 8f
        val appear = com.tools.app.ui.rememberFadeIn(img)

        ComposeCanvas(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = CardMargin, vertical = 4.dp)
                .graphicsLayer { alpha = appear }
                .pointerInput(src, mode, brushPx) {
                    if (mode != 1) return@pointerInput
                    fun toImage(p: Offset): Offset {
                        val r = fitRect(src, size.width.toFloat(), size.height.toFloat())
                        return Offset((p.x - r.left) / r.width * src.width, (p.y - r.top) / r.height * src.height)
                    }
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        current = listOf(toImage(down.position))
                        drag(down.id) { it.consume(); current = current + toImage(it.position) }
                        strokes.add(BrushStroke(current, brushPx)); current = emptyList()
                    }
                },
        ) {
            val r = fitRect(src, size.width, size.height)
            clipPath(roundedImage(r)) {
            val dstOff = IntOffset(r.left.roundToInt(), r.top.roundToInt())
            val dstSize = IntSize(r.width.roundToInt(), r.height.roundToInt())
            drawImage(img, dstOffset = dstOff, dstSize = dstSize, filterQuality = FilterQuality.Medium)
            val bi = blurredImage ?: return@ComposeCanvas
            val scale = r.width / src.width
            if (mode == 0) {
                drawImage(bi, srcSize = IntSize(bi.width, bi.height), dstOffset = dstOff, dstSize = dstSize, filterQuality = FilterQuality.Medium)
            } else {
                drawIntoCanvas { canvas ->
                    canvas.saveLayer(r, androidx.compose.ui.graphics.Paint())
                    val all = strokes.map { it.points to it.width } + listOf(current to brushPx)
                    for ((pts, width) in all) {
                        if (pts.isEmpty()) continue
                        val path = Path().apply {
                            moveTo(r.left + pts[0].x * scale, r.top + pts[0].y * scale)
                            pts.drop(1).forEach { lineTo(r.left + it.x * scale, r.top + it.y * scale) }
                            if (pts.size == 1) lineTo(r.left + pts[0].x * scale + 0.1f, r.top + pts[0].y * scale)
                        }
                        drawPath(path, Color.Black, style = Stroke(width * scale, cap = StrokeCap.Round, join = StrokeJoin.Round))
                    }
                    drawImage(bi, srcSize = IntSize(bi.width, bi.height), dstOffset = dstOff, dstSize = dstSize, filterQuality = FilterQuality.Medium, blendMode = BlendMode.SrcIn)
                    canvas.restore()
                }
            }
        }
            }

        Column(
            Modifier
                .padding(horizontal = CardMargin, vertical = 6.dp)
                .fillMaxWidth()
                .clip(OneUi.CardShape)
                .background(c.card)
                .padding(18.dp),
        ) {
            SegmentedControl(listOf("Whole image", "Brush"), mode, { mode = it })
            Label("Strength", "${(strength * 100).roundToInt()}")
            OneUiSlider(strength, { strength = it })
            if (mode == 1) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Label("Brush size", "", Modifier.weight(1f))
                    TextAction("Undo", strokes.isNotEmpty()) { strokes.removeAt(strokes.lastIndex) }
                    TextAction("Clear", strokes.isNotEmpty()) { strokes.clear() }
                }
                OneUiSlider(brush, { brush = it })
                if (strokes.isEmpty()) Text("Paint over the parts you want to hide.", fontSize = 13.sp, color = c.textSecondary, modifier = Modifier.padding(top = 4.dp))
            }
            PillButton(
                if (saving) "Saving…" else "Save image",
                enabled = !saving && blurred != null && (mode == 0 || strokes.isNotEmpty()),
                modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                onClick = {
                    val b = blurred ?: return@PillButton
                    saving = true
                    val snapshot = strokes.toList(); val m = mode
                    scope.launch {
                        val ok = withContext(Dispatchers.Default) {
                            runCatching {
                                val out = render(src, b, m, snapshot)
                                Images.saveJpeg(context, out, "blur_${System.currentTimeMillis()}")
                            }.isSuccess
                        }
                        saving = false
                        Toast.makeText(context, if (ok) "Saved to ${Images.FOLDER}" else (com.tools.app.Storage.NoAccess().message.takeIf { !com.tools.app.Storage.hasAccess() } ?: "Couldn't save the image"), Toast.LENGTH_SHORT).show()
                    }
                },
            )
        }
    }
}

@Composable
private fun Label(text: String, value: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = OneUi.colors.text)
        Text(value, fontSize = 15.sp, color = OneUi.colors.textSecondary)
    }
}

@Composable
private fun TextAction(text: String, enabled: Boolean, onClick: () -> Unit) {
    Text(
        text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
        color = if (enabled) OneUi.colors.accent else OneUi.colors.textSecondary.copy(alpha = 0.5f),
        modifier = Modifier.padding(top = 12.dp).clip(RoundedCornerShape(8.dp)).clickable(enabled = enabled, onClick = onClick).padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

private fun fitRect(img: Bitmap, w: Float, h: Float): Rect {
    val scale = min(w / img.width, h / img.height)
    val dw = img.width * scale; val dh = img.height * scale
    return Rect(Offset((w - dw) / 2, (h - dh) / 2), Size(dw, dh))
}

/** Full-resolution export matching the preview. */
private fun render(src: Bitmap, blurred: Bitmap, mode: Int, strokes: List<BrushStroke>): Bitmap {
    val out = src.copy(Bitmap.Config.ARGB_8888, true)
    val canvas = Canvas(out)
    val dst = android.graphics.Rect(0, 0, src.width, src.height)
    val filter = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    if (mode == 0) {
        canvas.drawBitmap(blurred, null, dst, filter)
    } else {
        val layer = canvas.saveLayer(null, null)
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND; color = android.graphics.Color.BLACK
        }
        for (s in strokes) {
            stroke.strokeWidth = s.width
            val path = android.graphics.Path().apply {
                moveTo(s.points[0].x, s.points[0].y)
                s.points.drop(1).forEach { lineTo(it.x, it.y) }
                if (s.points.size == 1) lineTo(s.points[0].x + 0.1f, s.points[0].y)
            }
            canvas.drawPath(path, stroke)
        }
        canvas.drawBitmap(blurred, null, dst, Paint(filter).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN) })
        canvas.restoreToCount(layer)
    }
    return out
}
