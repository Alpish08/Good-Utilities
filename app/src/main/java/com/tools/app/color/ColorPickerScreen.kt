package com.tools.app.color

import com.tools.app.ui.theme.IconColors
import androidx.compose.material.icons.rounded.Colorize
import androidx.compose.material.icons.Icons
import com.tools.app.ui.OneUiIcons
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette
import com.tools.app.ui.fadingEdges
import com.tools.app.ui.roundedImage
import androidx.compose.ui.graphics.graphicsLayer
import com.tools.app.ui.CardMargin
import com.tools.app.ui.EmptyState
import com.tools.app.ui.PillButton
import com.tools.app.ui.ToolPage
import com.tools.app.ui.ToolbarIcon
import com.tools.app.ui.theme.OneUi
import com.tools.app.ui.Images
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.min
import kotlin.math.roundToInt

/** Image kept across navigation so leaving the tool and coming back doesn't lose it. */
private object PickerState {
    var bitmap: Bitmap? = null
    var image: ImageBitmap? = null
    var point: Offset? = null
    var color: Int? = null
}

@Composable
fun ColorPickerScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val c = OneUi.colors
    var bitmap by remember { mutableStateOf(PickerState.bitmap) }
    var image by remember { mutableStateOf(PickerState.image) }
    var point by remember { mutableStateOf(PickerState.point) }
    var picked by remember { mutableStateOf(PickerState.color) }
    var dragging by remember { mutableStateOf(false) }
    var palette by remember { mutableStateOf<List<Int>>(emptyList()) }
    var saved by remember { mutableStateOf(SavedColors.load(context)) }

    val chooser = rememberLauncherForActivityResult(com.tools.app.ui.GalleryPick()) { uri ->
        if (uri != null) scope.launch {
            val bmp = withContext(Dispatchers.IO) { runCatching { Images.load(context, uri, 2400) }.getOrNull() } ?: return@launch
            bitmap = bmp; image = bmp.asImageBitmap()
            point = Offset(bmp.width / 2f, bmp.height / 2f)
            picked = bmp.getPixel(bmp.width / 2, bmp.height / 2)
            PickerState.bitmap = bitmap; PickerState.image = image; PickerState.point = point; PickerState.color = picked
        }
    }
    val choose = { chooser.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }

    LaunchedEffect(bitmap) {
        val bmp = bitmap ?: return@LaunchedEffect
        palette = withContext(Dispatchers.Default) {
            val p = Palette.from(bmp).maximumColorCount(24).generate()
            listOfNotNull(p.vibrantSwatch, p.lightVibrantSwatch, p.darkVibrantSwatch, p.mutedSwatch, p.lightMutedSwatch, p.darkMutedSwatch)
                .plus(p.swatches.sortedByDescending { it.population })
                .map { it.rgb }.distinct().take(10)
        }
    }

    ToolPage("Color picker", onBack, actions = { if (bitmap != null) ToolbarIcon(com.tools.app.ui.OneUiIcons.Gallery, "Choose image", choose) }) {
        val bmp = bitmap; val img = image
        if (bmp == null || img == null) {
            EmptyState(Icons.Rounded.Colorize, IconColors.Pink, "Pick colors from any image", "Drag over the photo to sample a pixel, with a magnifier for precision.", "Choose image", choose)
            return@ToolPage
        }

        // Image + crosshair + loupe
        val appear = com.tools.app.ui.rememberFadeIn(img)
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = CardMargin, vertical = 4.dp)
                .graphicsLayer { alpha = appear },
        ) {
            Canvas(
                Modifier
                    .fillMaxSize()
                    .pointerInput(bmp) {
                        fun sample(pos: Offset) {
                            val r = fitRect(Size(bmp.width.toFloat(), bmp.height.toFloat()), size.width.toFloat(), size.height.toFloat())
                            val x = ((pos.x - r.left) / r.width * bmp.width).coerceIn(0f, bmp.width - 1f)
                            val y = ((pos.y - r.top) / r.height * bmp.height).coerceIn(0f, bmp.height - 1f)
                            point = Offset(x, y); picked = bmp.getPixel(x.toInt(), y.toInt())
                            PickerState.point = point; PickerState.color = picked
                        }
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            dragging = true; sample(down.position)
                            drag(down.id) { it.consume(); sample(it.position) }
                            dragging = false
                        }
                    },
            ) {
                val r = fitRect(Size(bmp.width.toFloat(), bmp.height.toFloat()), size.width, size.height)
                clipPath(roundedImage(r)) {
                    drawImage(img, dstOffset = IntOffset(r.left.roundToInt(), r.top.roundToInt()), dstSize = IntSize(r.width.roundToInt(), r.height.roundToInt()), filterQuality = FilterQuality.Medium)
                }
                val p = point ?: return@Canvas
                val center = Offset(r.left + p.x / bmp.width * r.width, r.top + p.y / bmp.height * r.height)
                val col = Color(picked ?: 0)
                drawCircle(Color.White, 16.dp.toPx(), center, style = Stroke(3.dp.toPx()))
                drawCircle(Color.Black.copy(alpha = 0.35f), 17.5.dp.toPx(), center, style = Stroke(1.dp.toPx()))
                drawCircle(col, 11.dp.toPx(), center)

                if (dragging) {
                    val loupeR = 62.dp.toPx()
                    val lc = Offset(center.x.coerceIn(loupeR, size.width - loupeR), (center.y - loupeR - 40.dp.toPx()).let { if (it < loupeR) center.y + loupeR + 40.dp.toPx() else it })
                    val span = 11 // source pixels across
                    val sx = (p.x.toInt() - span / 2).coerceIn(0, (bmp.width - span).coerceAtLeast(0))
                    val sy = (p.y.toInt() - span / 2).coerceIn(0, (bmp.height - span).coerceAtLeast(0))
                    val clip = Path().apply { addOval(Rect(lc, loupeR)) }
                    clipPath(clip) {
                        drawImage(
                            img, srcOffset = IntOffset(sx, sy), srcSize = IntSize(min(span, bmp.width), min(span, bmp.height)),
                            dstOffset = IntOffset((lc.x - loupeR).roundToInt(), (lc.y - loupeR).roundToInt()),
                            dstSize = IntSize((loupeR * 2).roundToInt(), (loupeR * 2).roundToInt()), filterQuality = FilterQuality.None,
                        )
                        val cell = loupeR * 2 / span
                        drawRect(Color.White, Offset(lc.x - cell / 2, lc.y - cell / 2), Size(cell, cell), style = Stroke(2.dp.toPx()))
                    }
                    drawCircle(Color.White, loupeR, lc, style = Stroke(4.dp.toPx()))
                    drawCircle(col, loupeR + 6.dp.toPx(), lc, style = Stroke(6.dp.toPx()))
                }
            }
        }

        // Color details
        val argb = picked ?: 0
        Column(
            Modifier
                .padding(horizontal = CardMargin, vertical = 6.dp)
                .fillMaxWidth()
                .clip(OneUi.CardShape)
                .background(c.card)
                .padding(18.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(68.dp).clip(RoundedCornerShape(20.dp)).background(Color(argb)).border(1.dp, c.divider, RoundedCornerShape(20.dp)))
                Column(Modifier.padding(start = 16.dp).weight(1f)) {
                    ValueLine(hex(argb), big = true) { copy(context, hex(argb)) }
                    ValueLine(rgb(argb)) { copy(context, rgb(argb)) }
                    ValueLine(hsl(argb)) { copy(context, hsl(argb)) }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PillButton("Copy HEX", onClick = { copy(context, hex(argb)) }, tonal = true, height = 44.dp, modifier = Modifier.weight(1f))
                PillButton("Save color", onClick = { saved = SavedColors.add(context, argb) }, height = 44.dp, modifier = Modifier.weight(1f))
            }
            if (palette.isNotEmpty()) Swatches("From this image", palette) { picked = it; PickerState.color = it }
            if (saved.isNotEmpty()) Swatches("Saved · hold to remove", saved, onLongClick = { saved = SavedColors.remove(context, it) }) { picked = it; PickerState.color = it }
        }
    }
}

@Composable
private fun ValueLine(text: String, big: Boolean = false, onClick: () -> Unit) {
    Text(
        text,
        fontSize = if (big) 22.sp else 14.sp,
        fontWeight = if (big) FontWeight.Bold else FontWeight.Normal,
        color = if (big) OneUi.colors.text else OneUi.colors.textSecondary,
        modifier = Modifier.clip(RoundedCornerShape(6.dp)).combinedClickable(onClick = onClick).padding(vertical = 1.dp),
    )
}

@Composable
private fun Swatches(label: String, colors: List<Int>, onLongClick: ((Int) -> Unit)? = null, onClick: (Int) -> Unit) {
    val c = OneUi.colors
    Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = c.textSecondary, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
    val row = androidx.compose.foundation.lazy.rememberLazyListState()
    LazyRow(state = row, modifier = Modifier.fadingEdges(row, horizontal = true, length = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(colors) { col ->
            Box(
                Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(Color(col))
                    .border(1.dp, c.divider, CircleShape)
                    .combinedClickable(onClick = { onClick(col) }, onLongClick = onLongClick?.let { { it(col) } }),
            )
        }
    }
}

private fun fitRect(img: Size, w: Float, h: Float): Rect {
    val scale = min(w / img.width, h / img.height)
    val dw = img.width * scale; val dh = img.height * scale
    return Rect(Offset((w - dw) / 2, (h - dh) / 2), Size(dw, dh))
}

private fun hex(c: Int) = "#%06X".format(c and 0xFFFFFF)
private fun rgb(c: Int) = "RGB ${(c shr 16) and 0xff}, ${(c shr 8) and 0xff}, ${c and 0xff}"
private fun hsl(c: Int): String {
    val out = FloatArray(3); ColorUtils.colorToHSL(c, out)
    return "HSL ${out[0].roundToInt()}°, ${(out[1] * 100).roundToInt()}%, ${(out[2] * 100).roundToInt()}%"
}

private fun copy(context: Context, text: String) {
    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("color", text))
    if (Build.VERSION.SDK_INT < 33) Toast.makeText(context, "Copied $text", Toast.LENGTH_SHORT).show()
}

private object SavedColors {
    private fun prefs(c: Context) = c.getSharedPreferences("colors", Context.MODE_PRIVATE)
    fun load(c: Context): List<Int> = prefs(c).getString("saved", "")!!.split(',').mapNotNull { it.toIntOrNull() }
    private fun store(c: Context, list: List<Int>) = list.also { prefs(c).edit().putString("saved", it.joinToString(",")).apply() }
    fun add(c: Context, color: Int) = store(c, (listOf(color) + load(c)).distinct().take(30))
    fun remove(c: Context, color: Int) = store(c, load(c) - color)
}
