package com.tools.app.japanese

import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tools.app.ui.Images
import com.tools.app.ui.OneUiIcons
import com.tools.app.ui.PillButton
import com.tools.app.ui.SegmentedControl
import com.tools.app.ui.ThinProgress
import com.tools.app.ui.theme.OneUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

private sealed interface OcrState {
    data object Reading : OcrState
    data object Done : OcrState
    class Failed(val message: String) : OcrState
}

private val Scrim = Color(0x99000000)

@Composable
fun ReaderRoot(source: ReaderSource, onClose: () -> Unit) {
    val ctx = LocalContext.current
    var bitmap by remember(source.key) { mutableStateOf<Bitmap?>(null) }
    var ocr by remember(source.key) { mutableStateOf<OcrResult?>(null) }
    var state by remember(source.key) { mutableStateOf<OcrState>(OcrState.Reading) }
    var selected by remember(source.key) { mutableStateOf<Int?>(null) }
    var wholeBlock by remember(source.key) { mutableStateOf(false) }
    var showAll by remember(source.key) { mutableStateOf(false) }

    LaunchedEffect(source.key) {
        launch { runCatching { Nlp.warmUp() } }
        try {
            val bmp = withContext(Dispatchers.IO) { Images.load(ctx, source.uri, 3400) }
            bitmap = bmp
            ocr = withContext(Dispatchers.Default) { Ocr.recognize(bmp) }
            state = OcrState.Done
        } catch (t: Throwable) {
            if (t is kotlinx.coroutines.CancellationException) throw t
            state = OcrState.Failed(t.message ?: "Couldn't read this image")
        }
    }

    BackHandler(showAll || selected != null) { if (showAll) showAll = false else selected = null }

    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
        val density = androidx.compose.ui.platform.LocalDensity.current
        val screenH = with(density) { maxHeight.toPx() }
        val halfPx = screenH * 0.5f
        val sheet = remember { Animatable(halfPx) }
        val sheetOpen = selected != null
        val sheetPx = if (sheetOpen) sheet.value else 0f

        val bmp = bitmap
        if (bmp != null) {
            ImageLayer(
                bmp, ocr, selected, wholeBlock, sheetPx,
                onSelect = { selected = it },
            )
        }

        // Top controls
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(44.dp).clip(CircleShape).background(Scrim).clickable(onClick = onClose), contentAlignment = Alignment.Center) {
                Icon(OneUiIcons.Close, "Close", tint = Color.White, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.weight(1f))
            ScrimChip(if (JpPrefs.romaji) "Romaji on" else "Romaji", JpPrefs.romaji) { JpPrefs.changeRomaji(!JpPrefs.romaji) }
            Spacer(Modifier.width(8.dp))
            if (ocr?.lines?.isNotEmpty() == true) ScrimChip("All text", false) { showAll = true }
        }

        // Status
        val st = state
        Box(Modifier.fillMaxWidth().statusBarsPadding().padding(top = 64.dp), contentAlignment = Alignment.TopCenter) {
            when {
                st is OcrState.Reading -> StatusPill { CircularProgressIndicator(Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp); Spacer(Modifier.width(10.dp)); Text("Reading text…", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
                st is OcrState.Failed -> StatusPill { Text(st.message, color = Color.White, fontSize = 14.sp) }
                ocr?.lines?.isEmpty() == true -> StatusPill { Text("No Japanese text found", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
                selected == null -> StatusPill { Text("Tap a line of text", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
            }
        }

        val o = ocr
        val sel = selected
        if (o != null && sel != null && sel in o.lines.indices) {
            val line = o.lines[sel]
            val blockCount = o.blockLines(line.block).size
            SelectionSheet(
                sheet = sheet, halfPx = halfPx, fullPx = screenH - with(density) { 44.dp.toPx() },
                text = if (wholeBlock && blockCount > 1) o.blockText(line.block) else line.text,
                canWholeBlock = blockCount > 1, wholeBlock = wholeBlock, onWholeBlock = { wholeBlock = it },
                onDismiss = { selected = null },
            )
        }

        if (showAll && o != null) AllText(o, onPick = { selected = it; showAll = false }, onClose = { showAll = false })
    }
}

@Composable
private fun ScrimChip(text: String, on: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.height(40.dp).clip(CircleShape).background(if (on) OneUi.colors.accent else Scrim).clickable(onClick = onClick).padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1) }
}

@Composable
private fun StatusPill(content: @Composable () -> Unit) {
    Row(Modifier.clip(CircleShape).background(Scrim).padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) { content() }
}

// --- Image with pinch-zoom, pan and tappable lines ---------------------------------------------------

@Composable
private fun ImageLayer(
    bitmap: Bitmap, ocr: OcrResult?, selected: Int?, wholeBlock: Boolean, sheetPx: Float,
    onSelect: (Int?) -> Unit,
) {
    val image = remember(bitmap) { bitmap.asImageBitmap() }
    val accent = OneUi.colors.accent
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = androidx.compose.ui.platform.LocalDensity.current
        val cw = constraints.maxWidth.toFloat()
        val ch = constraints.maxHeight.toFloat()
        val fit = min(cw / bitmap.width, ch / bitmap.height)
        val left = (cw - bitmap.width * fit) / 2f
        val top = (ch - bitmap.height * fit) / 2f
        var scale by remember { mutableFloatStateOf(1f) }
        var tx by remember { mutableFloatStateOf(0f) }
        var ty by remember { mutableFloatStateOf(0f) }
        val sheetNow by rememberUpdatedStateCompat(sheetPx)

        fun clampT(s: Float) {
            val mx = (s - 1f) * cw / 2f
            val my = (s - 1f) * ch / 2f
            tx = tx.coerceIn(-mx - 40f, mx + 40f)
            ty = ty.coerceIn(-my - sheetNow - 40f, my + 40f)
        }

        // Bring the chosen line above the sheet.
        LaunchedEffect(selected) {
            val o = ocr ?: return@LaunchedEffect
            val l = selected?.let { o.lines.getOrNull(it) } ?: return@LaunchedEffect
            val qy = top + l.box.centerY() * fit
            val screenY = ch / 2f + scale * (qy - ch / 2f) + ty
            val visibleBottom = ch - max(sheetNow, ch * 0.5f)
            if (screenY > visibleBottom - 60f || screenY < 140f) {
                val want = max(visibleBottom, 300f) / 2f + 60f
                val from = ty
                animate(0f, 1f, animationSpec = tween(280)) { v, _ -> ty = from + (want - screenY) * v; clampT(scale) }
            }
        }

        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        val ns = (scale * zoom).coerceIn(1f, 8f)
                        val cx = cw / 2f; val cy = ch / 2f
                        val k = ns / scale
                        tx = centroid.x - cx - k * (centroid.x - cx - tx) + pan.x
                        ty = centroid.y - cy - k * (centroid.y - cy - ty) + pan.y
                        scale = ns
                        clampT(ns)
                    }
                }
                .pointerInput(ocr) {
                    detectTapGestures(
                        onDoubleTap = { p ->
                            val ns = if (scale > 1.5f) 1f else 2.5f
                            val cx = cw / 2f; val cy = ch / 2f
                            val k = ns / scale
                            tx = p.x - cx - k * (p.x - cx - tx); ty = p.y - cy - k * (p.y - cy - ty)
                            scale = ns; clampT(ns)
                        },
                        onTap = { p ->
                            val o = ocr ?: return@detectTapGestures
                            // screen → image pixels
                            val qx = cw / 2f + (p.x - cw / 2f - tx) / scale
                            val qy = ch / 2f + (p.y - ch / 2f - ty) / scale
                            val ix = (qx - left) / fit
                            val iy = (qy - top) / fit
                            val slop = 10.dp.toPx() / (fit * scale)
                            var best = -1
                            var bestArea = Float.MAX_VALUE
                            o.lines.forEachIndexed { i, l ->
                                val b = l.box
                                if (ix >= b.left - slop && ix <= b.right + slop && iy >= b.top - slop && iy <= b.bottom + slop) {
                                    val a = b.width().toFloat() * b.height()
                                    if (a < bestArea) { best = i; bestArea = a }
                                }
                            }
                            onSelect(if (best >= 0) best else null)
                        },
                    )
                }
                .graphicsLayer { scaleX = scale; scaleY = scale; translationX = tx; translationY = ty },
        ) {
            Image(
                image, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit,
                colorFilter = ColorFilter.tint(Color(0x52000000), BlendMode.SrcOver),
            )
            Canvas(Modifier.fillMaxSize()) {
                val o = ocr ?: return@Canvas
                val selBlock = selected?.let { o.lines.getOrNull(it)?.block }
                o.lines.forEachIndexed { i, l ->
                    val isSel = i == selected || (wholeBlock && selBlock != null && l.block == selBlock)
                    val pad = 4f * fit
                    val tl = Offset(left + l.box.left * fit - pad, top + l.box.top * fit - pad)
                    val sz = Size(l.box.width() * fit + pad * 2, l.box.height() * fit + pad * 2)
                    val r = CornerRadius(7.dp.toPx())
                    if (isSel) {
                        drawRoundRect(accent.copy(alpha = 0.38f), tl, sz, r)
                        drawRoundRect(accent, tl, sz, r, style = Stroke(2.dp.toPx() / scale))
                    } else {
                        drawRoundRect(Color.White.copy(alpha = 0.17f), tl, sz, r)
                        drawRoundRect(Color.White.copy(alpha = 0.5f), tl, sz, r, style = Stroke(1.dp.toPx() / scale))
                    }
                }
            }
        }
    }
}

@Composable
private fun <T> rememberUpdatedStateCompat(v: T) = androidx.compose.runtime.rememberUpdatedState(v)

// --- Bottom sheet -----------------------------------------------------------------------------------------

private sealed interface TrState {
    data object Loading : TrState
    class Done(val text: String) : TrState
    class Failed(val message: String) : TrState
}

private class WordInfo(val word: Word, val entry: Entry?)

@Composable
private fun SelectionSheet(
    sheet: Animatable<Float, androidx.compose.animation.core.AnimationVector1D>,
    halfPx: Float, fullPx: Float,
    text: String,
    canWholeBlock: Boolean, wholeBlock: Boolean, onWholeBlock: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val c = OneUi.colors
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val density = androidx.compose.ui.platform.LocalDensity.current
    val dictVersion by DictJobs.version.collectAsState()
    val dictState by DictJobs.state.collectAsState()
    val installed = remember(dictVersion, dictState) { Jmdict.installed(ctx) }
    val saved by WordStore.words.collectAsState()
    val savedKeys = remember(saved) { saved.map { it.key }.toSet() }
    var words by remember(text) { mutableStateOf<List<Word>?>(null) }
    var infos by remember(text, installed) { mutableStateOf<List<WordInfo>?>(null) }

    LaunchedEffect(text) { words = Nlp.split(text) }
    LaunchedEffect(words, installed) {
        val w = words ?: return@LaunchedEffect
        infos = w.map { WordInfo(it, null) }
        if (installed) infos = withContext(Dispatchers.IO) {
            w.map { WordInfo(it, if (it.isSymbol || !Kana.hasJapanese(it.surface)) null else Jmdict.lookup(ctx, it)) }
        }
    }

    // Translation
    val model by Translate.state.collectAsState()
    var tr by remember(text) { mutableStateOf<TrState>(TrState.Loading) }
    LaunchedEffect(Unit) { Translate.refresh() }
    LaunchedEffect(model) { if (model is ModelState.Missing) Translate.download(JpPrefs.wifiOnly) }
    LaunchedEffect(text, model is ModelState.Ready) {
        if (model !is ModelState.Ready) return@LaunchedEffect
        tr = TrState.Loading
        tr = try { TrState.Done(Translate.translate(text)) } catch (t: Throwable) {
            if (t is kotlinx.coroutines.CancellationException) throw t
            TrState.Failed(t.message ?: "Translation failed")
        }
    }

    val heightDp = with(density) { sheet.value.toDp() }
    LaunchedEffect(Unit) { if (sheet.value < halfPx * 0.6f) sheet.snapTo(halfPx) }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        Column(
            Modifier
                .fillMaxWidth()
                .height(heightDp)
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .background(c.card),
        ) {
            // Drag handle
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(34.dp)
                    .draggable(
                        orientation = Orientation.Vertical,
                        state = rememberDraggableState { d -> scope.launch { sheet.snapTo((sheet.value - d).coerceIn(halfPx * 0.55f, fullPx)) } },
                        onDragStopped = { v ->
                            val cur = sheet.value
                            when {
                                cur < halfPx * 0.8f || (v > 1800f && cur < halfPx) -> onDismiss()
                                v < -700f -> sheet.animateTo(fullPx, tween(260))
                                v > 700f -> sheet.animateTo(halfPx, tween(260))
                                cur > (halfPx + fullPx) / 2f -> sheet.animateTo(fullPx, tween(260))
                                else -> sheet.animateTo(halfPx, tween(260))
                            }
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) { Box(Modifier.width(40.dp).height(5.dp).clip(CircleShape).background(c.textSecondary.copy(alpha = 0.5f))) }

            LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp)) {
                item(key = "sentence") {
                    Column {
                        if (canWholeBlock) {
                            SegmentedControl(listOf("This line", "Whole bubble"), if (wholeBlock) 1 else 0, { onWholeBlock(it == 1) }, Modifier.padding(bottom = 14.dp))
                        }
                        val w = words
                        if (w == null) Text(text, fontSize = 22.sp, color = c.text)
                        else RubySentence(w, 24.sp, JpPrefs.romaji)
                    }
                }
                item(key = "translation") {
                    Column(Modifier.padding(top = 16.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(c.tonal.copy(alpha = if (c.isDark) 0.6f else 0.8f)).padding(16.dp)) {
                        Text("English", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = c.textSecondary)
                        Spacer(Modifier.height(6.dp))
                        TranslationBody(model, tr)
                    }
                }
                if (!installed) item(key = "dict") { DictionaryHint() }
                item(key = "wordsLabel") {
                    Text("Words", fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = c.textSecondary, modifier = Modifier.padding(start = 8.dp, top = 20.dp, bottom = 8.dp))
                }
                val list = infos
                if (list == null) item(key = "wl") { Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp) } }
                else {
                    val content = list.filter { !it.word.isSymbol && !it.word.isParticle }
                    val particles = list.filter { it.word.isParticle }
                    itemsIndexed(content, key = { i, it -> "w$i-${it.word.surface}" }) { _, info ->
                        WordRow(info, installed, savedKeys, dim = false)
                    }
                    if (particles.isNotEmpty()) item(key = "particles") { ParticleGroup(particles, installed, savedKeys) }
                }
            }
        }
    }
}

@Composable
private fun TranslationBody(model: ModelState, tr: TrState) {
    val c = OneUi.colors
    when {
        model is ModelState.Downloading -> Column {
            Text(if (model.wifiOnly) "Downloading the translation model (about 30 MB), waiting for Wi-Fi if needed…" else "Downloading the translation model (about 30 MB)…", fontSize = 14.sp, color = c.text)
            Spacer(Modifier.height(10.dp))
            // ML Kit gives no byte progress, so this is indeterminate.
            androidx.compose.material3.LinearProgressIndicator(Modifier.fillMaxWidth().clip(CircleShape), color = c.accent, trackColor = c.sliderTrack)
            if (model.wifiOnly) {
                Spacer(Modifier.height(10.dp))
                Text("Use mobile data instead", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = c.accent, modifier = Modifier.clickable { Translate.download(false) }.padding(vertical = 4.dp))
            }
        }
        model is ModelState.Failed -> Column {
            Text("Couldn't download the model: ${model.message}", fontSize = 14.sp, color = c.error)
            Text("Try again", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = c.accent, modifier = Modifier.clickable { Translate.download(JpPrefs.wifiOnly) }.padding(vertical = 8.dp))
        }
        model !is ModelState.Ready -> Row(verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp); Spacer(Modifier.width(10.dp)); Text("Preparing…", color = c.textSecondary, fontSize = 14.sp) }
        tr is TrState.Loading -> Row(verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp); Spacer(Modifier.width(10.dp)); Text("Translating…", color = c.textSecondary, fontSize = 14.sp) }
        tr is TrState.Failed -> Text(tr.message, fontSize = 14.sp, color = c.error)
        tr is TrState.Done -> Text(tr.text, fontSize = 18.sp, lineHeight = 25.sp, color = c.text)
    }
}

@Composable
private fun DictionaryHint() {
    val c = OneUi.colors
    val ctx = LocalContext.current
    val st by DictJobs.state.collectAsState()
    val assets by DictJobs.assets.collectAsState()
    LaunchedEffect(Unit) { DictJobs.refreshInfo() }
    val size = assets[JpPrefs.variant]?.size
    Column(Modifier.padding(top = 12.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(c.accentContainer.copy(alpha = if (c.isDark) 0.18f else 1f)).padding(16.dp)) {
        Text("Meanings need the offline dictionary", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = c.text)
        Spacer(Modifier.height(10.dp))
        when (val s = st) {
            is DictState.Working -> { ThinProgress(s.progress); Spacer(Modifier.height(6.dp)); Text("${(s.progress * 100).toInt()}%  ${s.stage}", fontSize = 13.sp, color = c.textSecondary) }
            DictState.Checking -> Text("Checking the latest release…", fontSize = 13.sp, color = c.textSecondary)
            else -> {
                if (s is DictState.Failed) Text(s.message, fontSize = 13.sp, color = c.error, modifier = Modifier.padding(bottom = 8.dp))
                PillButton("Download dictionary (~${formatMb(size ?: if (JpPrefs.variant == DictVariant.Full) 11_500_000L else 1_450_000L)})", { DictJobs.start(ctx, JpPrefs.variant) }, height = 44.dp)
            }
        }
    }
}

fun formatMb(bytes: Long): String = if (bytes < 1_000_000) "${bytes / 1000} KB" else "${(bytes / 1_000_000.0).let { if (it < 10) "%.1f".format(it) else it.toInt().toString() }} MB"

@Composable
private fun ParticleGroup(particles: List<WordInfo>, installed: Boolean, savedKeys: Set<String>) {
    val c = OneUi.colors
    var open by remember { mutableStateOf(false) }
    Column(Modifier.padding(top = 8.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(c.tonal.copy(alpha = 0.35f))) {
        Row(Modifier.fillMaxWidth().clickable { open = !open }.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Particles (${particles.size})", fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold, color = c.textSecondary)
                Text(particles.joinToString("  ") { it.word.surface }.take(60), fontSize = 16.sp, color = c.textSecondary.copy(alpha = 0.8f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(if (open) OneUiIcons.ChevronUp else OneUiIcons.ChevronDown, null, tint = c.textSecondary, modifier = Modifier.size(22.dp))
        }
        if (open) particles.forEach { WordRow(it, installed, savedKeys, dim = true, plain = true) }
    }
}

@Composable
private fun WordRow(info: WordInfo, installed: Boolean, savedKeys: Set<String>, dim: Boolean, plain: Boolean = false) {
    val c = OneUi.colors
    val w = info.word
    var expanded by remember { mutableStateOf(false) }
    val e = info.entry
    val key = w.base
    val isSaved = key in savedKeys
    val alpha = if (dim) 0.62f else 1f
    val inflected = w.base != w.surface
    // Reading of the dictionary form, for saving.
    val dictReading = remember(info) {
        when {
            !w.base.any(Kana::isKanji) -> Kana.toHiragana(w.base)
            e != null && e.kana.isNotEmpty() -> e.kana.firstOrNull { !inflected && it == w.reading } ?: e.kana.first()
            !inflected -> w.reading
            else -> ""
        }
    }
    val meaningText = e?.senses?.take(3)?.joinToString(" · ") { it.glosses.take(2).joinToString("; ") } ?: ""

    Column(
        Modifier
            .then(if (plain) Modifier else Modifier.padding(bottom = 8.dp).clip(RoundedCornerShape(18.dp)).background(c.tonal.copy(alpha = if (c.isDark) 0.6f else 0.8f)))
            .fillMaxWidth()
            .clickable { expanded = !expanded }
            .padding(start = 16.dp, end = 6.dp, top = 12.dp, bottom = 12.dp)
            .graphicsLayer { this.alpha = alpha },
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                RubyWord(w.segments, 30.sp)
                Spacer(Modifier.height(2.dp))
                val bits = buildList {
                    add(w.pos)
                    if (inflected) add("from ${w.base}")
                }
                Text(bits.joinToString(" · "), fontSize = 12.5.sp, color = c.textSecondary)
            }
            Box(
                Modifier.size(44.dp).clip(CircleShape).clickable {
                    if (isSaved) WordStore.remove(key)
                    else WordStore.add(key, w.base, dictReading, meaningText, e?.senses?.firstOrNull()?.pos?.let(Jmdict::posLabel) ?: w.pos)
                },
                contentAlignment = Alignment.Center,
            ) {
                Icon(if (isSaved) Icons.Filled.Star else Icons.Outlined.StarBorder, if (isSaved) "Remove from My words" else "Save to My words", tint = if (isSaved) Color(0xFFFFB400) else c.textSecondary, modifier = Modifier.size(26.dp))
            }
        }
        val reading = w.reading
        if (reading.isNotEmpty() && reading != w.surface || JpPrefs.romaji && Kana.hasJapanese(w.surface)) {
            Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                if (reading.isNotEmpty() && reading != w.surface) Text(reading, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = c.accent)
                if (JpPrefs.romaji && Kana.hasJapanese(w.surface)) {
                    if (reading.isNotEmpty() && reading != w.surface) Spacer(Modifier.width(10.dp))
                    Text(Kana.toRomaji(romajiSource(w)), fontSize = 14.sp, color = c.textSecondary)
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        when {
            e == null && !installed -> Text("Download the dictionary to see meanings", fontSize = 13.5.sp, color = c.textSecondary)
            e == null -> Text("No dictionary entry", fontSize = 13.5.sp, color = c.textSecondary)
            !expanded -> Column(Modifier.padding(end = 10.dp)) {
                e.senses.take(3).forEachIndexed { i, s ->
                    Text("${i + 1}  " + s.glosses.take(3).joinToString("; "), fontSize = 14.5.sp, lineHeight = 20.sp, color = c.text)
                }
                if (e.senses.size > 3) Text("+${e.senses.size - 3} more, tap to expand", fontSize = 12.5.sp, color = c.textSecondary, modifier = Modifier.padding(top = 2.dp))
            }
            else -> Column(Modifier.padding(end = 10.dp)) {
                var lastPos = ""
                e.senses.forEachIndexed { i, s ->
                    val p = Jmdict.posLabel(s.pos)
                    if (p != lastPos) { lastPos = p; if (p.isNotEmpty()) Text(p, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = c.textSecondary, modifier = Modifier.padding(top = if (i == 0) 0.dp else 8.dp, bottom = 2.dp)) }
                    Text("${i + 1}  " + s.glosses.joinToString("; "), fontSize = 14.5.sp, lineHeight = 20.sp, color = c.text, modifier = Modifier.padding(vertical = 1.dp))
                }
                val forms = (e.kanji + e.kana).filter { it != w.surface }
                if (forms.isNotEmpty()) Text("Also written: " + forms.take(8).joinToString("、"), fontSize = 12.5.sp, color = c.textSecondary, modifier = Modifier.padding(top = 8.dp))
            }
        }
    }
}

// --- All text ----------------------------------------------------------------------------------------------

@Composable
private fun AllText(ocr: OcrResult, onPick: (Int) -> Unit, onClose: () -> Unit) {
    val c = OneUi.colors
    val ctx = LocalContext.current
    val list = rememberLazyListState()
    Box(Modifier.fillMaxSize().background(Color(0x66000000)).clickable(onClick = onClose, indication = null, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() })) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().padding(top = 56.dp)
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)).background(c.card)
                .clickable(indication = null, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }) {},
        ) {
            Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp, top = 18.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("All text", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = c.text)
                    Text("${ocr.lines.size} lines" + if (ocr.vertical) " · vertical text, read right to left" else "", fontSize = 13.sp, color = c.textSecondary)
                }
                Text(
                    "Copy", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = c.accent,
                    modifier = Modifier.clip(CircleShape).clickable {
                        val cm = ctx.getSystemService(ClipboardManager::class.java)
                        cm.setPrimaryClip(ClipData.newPlainText("Japanese text", ocr.lines.joinToString("\n") { it.text }))
                    }.padding(horizontal = 14.dp, vertical = 10.dp),
                )
                Box(Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onClose), contentAlignment = Alignment.Center) {
                    Icon(OneUiIcons.Close, "Close", tint = c.text, modifier = Modifier.size(22.dp))
                }
            }
            LazyColumn(Modifier.weight(1f), state = list, contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp)) {
                itemsIndexed(ocr.lines, key = { i, _ -> i }) { i, l ->
                    Column(Modifier.fillMaxWidth().clickable { onPick(i) }.padding(horizontal = 24.dp, vertical = 14.dp)) {
                        Text(l.text, fontSize = 19.sp, lineHeight = 26.sp, color = c.text)
                    }
                    if (i < ocr.lines.lastIndex) Box(Modifier.padding(horizontal = 24.dp).fillMaxWidth().height(1.dp).background(c.divider))
                }
                item { Spacer(Modifier.navigationBarsPadding()) }
            }
        }
    }
}
