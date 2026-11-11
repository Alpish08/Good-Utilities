package com.tools.app.cutout

import com.tools.app.ui.theme.IconColors
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.Icons
import android.content.Context
import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.common.moduleinstall.ModuleInstall
import com.google.android.gms.common.moduleinstall.ModuleInstallRequest
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import com.tools.app.ui.CardMargin
import com.tools.app.ui.EmptyState
import com.tools.app.ui.Images
import com.tools.app.ui.roundedImage
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import com.tools.app.ui.OneUiIcons
import com.tools.app.ui.PillButton
import com.tools.app.ui.SegmentedControl
import com.tools.app.ui.ThinProgress
import com.tools.app.ui.ToolPage
import com.tools.app.ui.ToolbarIcon
import com.tools.app.ui.theme.OneUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Cuts the subject out of a photo with ML Kit subject segmentation. The model is "unbundled": it
 * lives in Google Play services and is downloaded on first use, so it adds almost nothing to the APK.
 */
object Cutout {
    private val segmenter by lazy {
        SubjectSegmentation.getClient(SubjectSegmenterOptions.Builder().enableForegroundBitmap().build())
    }

    /** Makes sure Play services has the model; reports download progress 0..1. */
    suspend fun ensureModel(context: Context, onProgress: (Float) -> Unit) {
        val client = ModuleInstall.getClient(context)
        if (client.areModulesAvailable(segmenter).await().areModulesAvailable()) return
        val request = ModuleInstallRequest.newBuilder()
            .addApi(segmenter)
            .setListener { update ->
                update.progressInfo?.let { p -> if (p.totalBytesToDownload > 0) onProgress(p.bytesDownloaded.toFloat() / p.totalBytesToDownload) }
            }
            .build()
        client.installModules(request).await()
        // installModules returns once the request is accepted; wait until the module is usable.
        repeat(120) {
            if (client.areModulesAvailable(segmenter).await().areModulesAvailable()) return
            kotlinx.coroutines.delay(500)
        }
    }

    /** Foreground on a transparent background, same size as [bitmap]. Runs off the main thread. */
    suspend fun cut(bitmap: Bitmap): Bitmap = withContext(Dispatchers.Default) {
        val result = segmenter.process(InputImage.fromBitmap(bitmap, 0)).await()
        result.foregroundBitmap ?: error("No subject found in this photo")
    }

    /** Trims transparent edges so the PNG is only as big as the subject. */
    fun cropToSubject(b: Bitmap): Bitmap {
        val w = b.width; val h = b.height
        val px = IntArray(w * h); b.getPixels(px, 0, w, 0, 0, w, h)
        var l = w; var t = h; var r = -1; var btm = -1
        for (y in 0 until h) for (x in 0 until w) if (px[y * w + x] ushr 24 > 8) {
            if (x < l) l = x; if (x > r) r = x; if (y < t) t = y; if (y > btm) btm = y
        }
        if (r < l || btm < t) return b
        return Bitmap.createBitmap(b, l, t, r - l + 1, btm - t + 1)
    }
}

@Composable
fun BackgroundRemoverScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val c = OneUi.colors
    var original by remember { mutableStateOf<Bitmap?>(null) }
    var cutout by remember { mutableStateOf<Bitmap?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var progress by remember { mutableStateOf<Float?>(null) }
    var showOriginal by remember { mutableStateOf(false) }
    var crop by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }

    val chooser = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            cutout = null; showOriginal = false; status = "Opening…"
            val bmp = withContext(Dispatchers.IO) { runCatching { Images.load(context, uri, 2048) }.getOrNull() }
            if (bmp == null) { status = "Couldn't open that image"; return@launch }
            original = bmp
            try {
                status = "Getting the model ready…"
                Cutout.ensureModel(context) { progress = it; status = "Downloading the model… ${(it * 100).roundToInt()}%" }
                progress = null
                status = "Finding the subject…"
                cutout = Cutout.cut(bmp)
                status = null
            } catch (e: Exception) {
                progress = null
                status = e.message ?: "Couldn't remove the background"
            }
        }
    }
    val choose = { chooser.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }

    ToolPage("Background remover", onBack, actions = { if (original != null) ToolbarIcon(OneUiIcons.Gallery, "Choose image", choose) }) {
        val src = original
        if (src == null) {
            EmptyState(Icons.Rounded.AutoFixHigh, IconColors.Teal, "Remove a background", "Pick a photo and the person, pet or object in it is cut out as a transparent PNG.", "Choose image", choose)
            return@ToolPage
        }
        val shown = if (showOriginal || cutout == null) src else cutout!!
        val image = remember(shown) { shown.asImageBitmap() }

        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = CardMargin, vertical = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            val appear = com.tools.app.ui.rememberFadeIn(image)
            Canvas(Modifier.fillMaxSize().graphicsLayer { alpha = appear }) {
                val scale = min(size.width / shown.width, size.height / shown.height)
                val w = shown.width * scale; val h = shown.height * scale
                val left = (size.width - w) / 2; val top = (size.height - h) / 2
                clipPath(roundedImage(androidx.compose.ui.geometry.Rect(left, top, left + w, top + h))) {
                if (shown !== src) {
                    // Checkerboard behind transparent pixels, like Samsung's photo editor.
                    val cell = 12.dp.toPx()
                    val light = if (c.isDark) Color(0xFF3A3A3D) else Color(0xFFFFFFFF)
                    val dark = if (c.isDark) Color(0xFF2A2A2D) else Color(0xFFE6E6EA)
                    var y = top; var row = 0
                    while (y < top + h) {
                        var x = left; var col = 0
                        while (x < left + w) {
                            drawRect(if ((row + col) % 2 == 0) light else dark, Offset(x, y), Size(min(cell, left + w - x), min(cell, top + h - y)))
                            x += cell; col++
                        }
                        y += cell; row++
                    }
                }
                drawImage(
                    image, dstOffset = IntOffset(left.roundToInt(), top.roundToInt()),
                    dstSize = IntSize(w.roundToInt(), h.roundToInt()), filterQuality = FilterQuality.Medium,
                )
                }
            }
            status?.let {
                Column(
                    Modifier.clip(OneUi.CardShape).background(c.background.copy(alpha = 0.92f)).padding(horizontal = 22.dp, vertical = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(it, fontSize = 15.sp, color = c.text)
                    progress?.let { p -> ThinProgress(p, Modifier.padding(top = 8.dp).fillMaxWidth(0.6f)) }
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
            SegmentedControl(listOf("Cut out", "Original"), if (showOriginal) 1 else 0, { showOriginal = it == 1 })
            SegmentedControl(
                listOf("Crop to subject", "Keep size"), if (crop) 0 else 1, { crop = it == 0 },
                modifier = Modifier.padding(top = 10.dp),
            )
            PillButton(
                if (saving) "Saving…" else "Save PNG",
                enabled = cutout != null && !saving,
                modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                onClick = {
                    val out = cutout ?: return@PillButton
                    saving = true
                    scope.launch {
                        val ok = withContext(Dispatchers.Default) {
                            runCatching {
                                Images.savePng(context, if (crop) Cutout.cropToSubject(out) else out, "cutout_${System.currentTimeMillis()}")
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
