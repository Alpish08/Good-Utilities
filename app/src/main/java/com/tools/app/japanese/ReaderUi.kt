package com.tools.app.japanese

import androidx.compose.material.icons.rounded.GTranslate
import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tools.app.ui.CardMargin
import com.tools.app.ui.Images
import com.tools.app.ui.ListRow
import com.tools.app.ui.OneUiIcons
import com.tools.app.ui.OneUiScaffold
import com.tools.app.ui.OverflowMenu
import com.tools.app.ui.PillButton
import com.tools.app.ui.SegmentedControl
import com.tools.app.ui.ThinProgress
import com.tools.app.ui.card
import com.tools.app.ui.cardList
import com.tools.app.ui.description
import com.tools.app.ui.sectionLabel
import com.tools.app.ui.theme.OneUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.min

private sealed interface OcrState {
    data object Reading : OcrState
    data object Done : OcrState
    class Failed(val message: String) : OcrState
}

private sealed interface TrState {
    data object Loading : TrState
    class Done(val text: String) : TrState
    class Failed(val message: String) : TrState
}

private class WordInfo(val idx: Int, val word: Word, val entry: Entry?, val alts: List<Entry> = emptyList())

/** One row of the Words card list: a word, or the collapsed group of particles. */
private class WordItem(val key: String, val info: WordInfo?, val particles: List<WordInfo>?)

/** The reader page: the picture in a card on top, then the selected line, its translation and its words. */
@Composable
fun ReaderRoot(source: ReaderSource, onClose: () -> Unit) {
    val ctx = LocalContext.current
    var bitmap by remember(source.key) { mutableStateOf<Bitmap?>(null) }
    var ocr by remember(source.key) { mutableStateOf<OcrResult?>(null) }
    var state by remember(source.key) { mutableStateOf<OcrState>(OcrState.Reading) }
    var selected by remember(source.key) { mutableStateOf<Int?>(null) }
    var wholeBlock by remember(source.key) { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }

    LaunchedEffect(source.key) {
        launch { runCatching { Nlp.warmUp() } }
        try {
            val bmp = withContext(Dispatchers.IO) { Images.load(ctx, source.uri, 3400) }
            bitmap = bmp
            ocr = withContext(Dispatchers.Default) { Ocr.recognize(bmp, source.topInset) }
            state = OcrState.Done
        } catch (t: Throwable) {
            if (t is kotlinx.coroutines.CancellationException) throw t
            state = OcrState.Failed(t.message ?: "Couldn't read this image")
        }
    }

    // What is selected, as text.
    val o = ocr
    val sel = selected?.takeIf { o != null && it in o.lines.indices }
    val line = if (o != null && sel != null) o.lines[sel] else null
    val blockCount = if (o != null && line != null) o.blockLines(line.block).size else 0
    val text = if (o != null && line != null) (if (wholeBlock && blockCount > 1) o.blockText(line.block) else line.text) else null

    val dictVersion by DictJobs.version.collectAsState()
    val dictState by DictJobs.state.collectAsState()
    val installed = remember(dictVersion, dictState) { Jmdict.installed(ctx) }
    val saved by WordStore.words.collectAsState()
    val savedKeys = remember(saved) { saved.map { it.key }.toSet() }
    var words by remember(text) { mutableStateOf<List<Word>?>(null) }
    var infos by remember(text) { mutableStateOf<List<WordInfo>?>(null) }

    LaunchedEffect(text, installed) {
        val t = text ?: return@LaunchedEffect
        val w = Nlp.split(t, if (installed) ctx else null)
        words = w
        infos = w.mapIndexed { i, x -> WordInfo(i, x, null) }
        if (installed) infos = withContext(Dispatchers.IO) {
            w.mapIndexed { i, x ->
                val e = if (x.isWord) Jmdict.lookup(ctx, x) else null
                WordInfo(i, x, e, if (e != null) Jmdict.alternatives(ctx, x, e) else emptyList())
            }
        }
    }
    val romajis = remember(words) { words?.let { Kana.romajiPerWord(it) } }

    // Translation
    val model by Translate.state.collectAsState()
    var tr by remember(text) { mutableStateOf<TrState>(TrState.Loading) }
    LaunchedEffect(Unit) { Translate.refresh() }
    LaunchedEffect(model, text != null) { if (model is ModelState.Missing && text != null) Translate.download(JpPrefs.wifiOnly) }
    LaunchedEffect(text, model is ModelState.Ready) {
        val t = text ?: return@LaunchedEffect
        if (model !is ModelState.Ready) return@LaunchedEffect
        tr = TrState.Loading
        tr = try { TrState.Done(Translate.translate(t)) } catch (e: Throwable) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            TrState.Failed(e.message ?: "Translation failed")
        }
    }

    if (showSettings) {
        BackHandler { showSettings = false }
        JapaneseReaderScreen(onBack = { showSettings = false })
        return
    }

    val menu = listOf<Pair<String, () -> Unit>>(
        (if (JpPrefs.romaji) "Hide romaji" else "Show romaji") to { JpPrefs.changeRomaji(!JpPrefs.romaji) },
        "Copy all text" to {
            val all = ocr?.lines?.joinToString("\n") { it.text }.orEmpty()
            if (all.isNotEmpty()) {
                ctx.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Japanese text", all))
                Toast.makeText(ctx, "Copied", Toast.LENGTH_SHORT).show()
            }
        },
        "Dictionary" to { showSettings = true },
    )

    OneUiScaffold(title = "Japanese reader", onBack = onClose, expandedTitle = false, actions = { OverflowMenu(menu) }) {
        item(key = "image") {
            val bmp = bitmap
            if (bmp != null) ImageCard(bmp, ocr, sel, wholeBlock) { selected = it }
            else Box(
                Modifier.padding(horizontal = CardMargin, vertical = 10.dp).fillMaxWidth().height(180.dp)
                    .clip(OneUi.CardShape).background(OneUi.colors.card),
                contentAlignment = Alignment.Center,
            ) { com.tools.app.ui.OneUiSpinner(Modifier.size(24.dp), strokeWidth = 2.dp) }
        }

        val st = state
        when {
            st is OcrState.Reading -> card("status") {
                ListRow("Reading text…", leading = { com.tools.app.ui.OneUiSpinner(Modifier.size(22.dp), strokeWidth = 2.dp) })
            }
            st is OcrState.Failed -> card("status") { ListRow("Couldn't read this image", summary = st.message, summaryColor = OneUi.colors.error) }
            o != null && o.lines.isEmpty() -> description("No Japanese text found in this picture.", key = "status")
            sel == null -> description("Tap a line in the picture", key = "status")
        }

        if (sel != null && text != null) {
            if (blockCount > 1) item(key = "scope") {
                Box(Modifier.padding(horizontal = CardMargin + 6.dp, vertical = 6.dp)) {
                    SegmentedControl(listOf("This line", "Whole bubble"), if (wholeBlock) 1 else 0, { wholeBlock = it == 1 })
                }
            }
            // Words first (what you look up most), then the sentence translation.
            val list = infos
            sectionLabel("Words")
            if (list == null) card("wordsLoading") { Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { com.tools.app.ui.OneUiSpinner(Modifier.size(22.dp), strokeWidth = 2.dp) } }
            else {
                val rows = buildList {
                    list.filter { it.word.isWord && !it.word.isParticle }.forEach { add(WordItem("w${it.idx}", it, null)) }
                    val p = list.filter { it.word.isWord && it.word.isParticle }
                    if (p.isNotEmpty()) add(WordItem("particles", null, p))
                }
                if (rows.isEmpty()) description("No words in this line.", key = "noWords")
                else cardList(rows, key = { it.key }) { item ->
                    if (item.info != null) WordRow(item.info, installed, savedKeys, romajis?.getOrNull(item.info.idx).orEmpty(), dim = false)
                    else ParticleGroup(item.particles.orEmpty(), installed, savedKeys, romajis)
                }
            }
            sectionLabel("English")
            card("english") {
                Box(Modifier.padding(18.dp)) { TranslationBody(model, tr) }
                // Google Translate's own floating card: online translation, pronunciation audio, alternatives.
                if (GoogleTranslate.installed(LocalContext.current)) {
                    val ctx = LocalContext.current
                    com.tools.app.ui.CardDivider()
                    ListRow(
                        "Google Translate",
                        summary = "Listen, see alternatives, and get the online translation",
                        onClick = { GoogleTranslate.open(ctx, text) },
                    )
                }
            }
            if (!installed) item(key = "dictHint") { DictionaryHint() }

        }

        if (o != null && o.lines.isNotEmpty()) {
            sectionLabel("All text" + if (o.vertical) " (vertical, right to left)" else "")
            cardList(o.lines.indices.toList(), key = { "line-$it" }) { i ->
                ListRow(o.lines[i].text, onClick = { selected = i; wholeBlock = false })
            }
        }
    }
}

// --- The picture: a rounded card with pinch zoom, pan and tappable lines ----------------------------------

@Composable
private fun ImageCard(bitmap: Bitmap, ocr: OcrResult?, selected: Int?, wholeBlock: Boolean, onSelect: (Int?) -> Unit) {
    val c = OneUi.colors
    val image = remember(bitmap) { bitmap.asImageBitmap() }
    val density = LocalDensity.current
    val screenH = with(density) { LocalConfiguration.current.screenHeightDp.dp.toPx() }
    BoxWithConstraints(Modifier.padding(horizontal = CardMargin, vertical = 10.dp).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        // The card takes the picture's own shape (no cropping): full width, or for tall screenshots narrower,
        // so it is never taller than 75% of the screen.
        val fit = min(constraints.maxWidth.toFloat() / bitmap.width, screenH * 0.75f / bitmap.height)
        val cw = bitmap.width * fit
        val dispH = bitmap.height * fit
        val boxH = dispH
        var scale by remember(bitmap) { mutableFloatStateOf(1f) }
        var tx by remember(bitmap) { mutableFloatStateOf(0f) }
        var ty by remember(bitmap) { mutableFloatStateOf(0f) }
        val accent = c.accent

        fun minTy(s: Float) = min(0f, boxH - s * dispH)

        // Bring the chosen line into view.
        LaunchedEffect(selected) {
            val l = selected?.let { ocr?.lines?.getOrNull(it) } ?: return@LaunchedEffect
            val sy = scale * l.box.centerY() * fit + ty
            if (sy < 24f || sy > boxH - 24f) {
                val from = ty
                val want = (boxH / 2f - scale * l.box.centerY() * fit).coerceIn(minTy(scale), 0f)
                animate(0f, 1f, animationSpec = tween(250)) { v, _ -> ty = from + (want - from) * v }
            }
        }

        Box(
            Modifier
                .width(with(density) { cw.toDp() })
                .height(with(density) { boxH.toDp() })
                .clip(OneUi.CardShape)
                .background(c.card)
                .pointerInput(cw, boxH) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        var moved = 0f
                        var active = false
                        do {
                            val ev = awaitPointerEvent()
                            val zoom = ev.calculateZoom(); val pan = ev.calculatePan(); val cen = ev.calculateCentroid()
                            moved += pan.getDistance()
                            if (!active && (ev.changes.count { it.pressed } > 1 || moved > viewConfiguration.touchSlop)) active = true
                            if (active && cen != Offset.Unspecified) {
                                val ns = (scale * zoom).coerceIn(1f, 6f)
                                val k = ns / scale
                                val ntx = (cen.x - k * (cen.x - tx) + pan.x).coerceIn(min(0f, cw - ns * cw), 0f)
                                val nty = (cen.y - k * (cen.y - ty) + pan.y).coerceIn(minTy(ns), 0f)
                                // Only take the gesture when it moves the picture; otherwise the page scrolls.
                                if (ns != scale || ntx != tx || nty != ty) {
                                    scale = ns; tx = ntx; ty = nty
                                    ev.changes.forEach { if (it.positionChanged()) it.consume() }
                                }
                            }
                        } while (ev.changes.any { it.pressed })
                    }
                }
                .pointerInput(ocr, cw) {
                    detectTapGestures(onTap = { p ->
                        val o = ocr ?: return@detectTapGestures
                        val ix = (p.x - tx) / scale / fit
                        val iy = (p.y - ty) / scale / fit
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
                    })
                },
        ) {
            Box(
                Modifier
                    .requiredSize(with(density) { cw.toDp() }, with(density) { dispH.toDp() })
                    .graphicsLayer { transformOrigin = TransformOrigin(0f, 0f); scaleX = scale; scaleY = scale; translationX = tx; translationY = ty },
            ) {
                Image(image, null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
                Canvas(Modifier.fillMaxSize()) {
                    val o = ocr ?: return@Canvas
                    val selBlock = selected?.let { o.lines.getOrNull(it)?.block }
                    o.lines.forEachIndexed { i, l ->
                        val isSel = i == selected || (wholeBlock && selBlock != null && l.block == selBlock)
                        val pad = 3.dp.toPx() / scale
                        val tl = Offset(l.box.left * fit - pad, l.box.top * fit - pad)
                        val sz = Size(l.box.width() * fit + pad * 2, l.box.height() * fit + pad * 2)
                        val r = CornerRadius(6.dp.toPx() / scale)
                        if (isSel) {
                            drawRoundRect(accent.copy(alpha = 0.35f), tl, sz, r)
                            drawRoundRect(accent, tl, sz, r, style = Stroke(2.dp.toPx() / scale))
                        } else {
                            drawRoundRect(accent.copy(alpha = 0.12f), tl, sz, r)
                            drawRoundRect(accent.copy(alpha = 0.55f), tl, sz, r, style = Stroke(1.dp.toPx() / scale))
                        }
                    }
                }
            }
        }
    }
}

// --- Translation, dictionary hint ------------------------------------------------------------------------------

@Composable
private fun TranslationBody(model: ModelState, tr: TrState) {
    val c = OneUi.colors
    when {
        model is ModelState.Downloading -> Column {
            Text(if (model.wifiOnly) "Downloading the translation model (about 30 MB), waiting for Wi-Fi if needed…" else "Downloading the translation model (about 30 MB)…", fontSize = 14.sp, color = c.text)
            Spacer(Modifier.height(10.dp))
            // ML Kit gives no byte progress, so this is indeterminate.
            com.tools.app.ui.ThinProgressIndeterminate(color = c.accent)
            if (model.wifiOnly) {
                Text("Use mobile data instead", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = c.accent, modifier = Modifier.clickable { Translate.download(false) }.padding(top = 10.dp, bottom = 4.dp))
            }
        }
        model is ModelState.Failed -> Column {
            Text("Couldn't download the model: ${model.message}", fontSize = 14.sp, color = c.error)
            Text("Try again", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = c.accent, modifier = Modifier.clickable { Translate.download(JpPrefs.wifiOnly) }.padding(vertical = 8.dp))
        }
        model !is ModelState.Ready || tr is TrState.Loading -> Row(verticalAlignment = Alignment.CenterVertically) {
            com.tools.app.ui.OneUiSpinner(Modifier.size(18.dp), strokeWidth = 2.dp); Spacer(Modifier.width(10.dp))
            Text(if (model is ModelState.Ready) "Translating…" else "Preparing…", color = c.textSecondary, fontSize = 14.sp)
        }
        tr is TrState.Failed -> Text(tr.message, fontSize = 14.sp, color = c.error)
        tr is TrState.Done -> Text(tr.text, fontSize = 16.sp, lineHeight = 23.sp, color = c.text)
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
    Column(Modifier.padding(horizontal = CardMargin, vertical = 10.dp).fillMaxWidth().clip(OneUi.CardShape).background(c.card).padding(18.dp)) {
        Text("Meanings need the offline dictionary", fontSize = 15.5.sp, fontWeight = FontWeight.SemiBold, color = c.text)
        Spacer(Modifier.height(10.dp))
        when (val s = st) {
            is DictState.Working -> { ThinProgress(s.progress); Spacer(Modifier.height(6.dp)); Text("${(s.progress * 100).toInt()}%  ${s.stage}", fontSize = 13.sp, color = c.textSecondary) }
            DictState.Checking -> Text("Checking the latest release…", fontSize = 13.sp, color = c.textSecondary)
            else -> {
                if (s is DictState.Failed) Text(s.message, fontSize = 13.sp, color = c.error, modifier = Modifier.padding(bottom = 8.dp))
                PillButton("Download dictionary (~${formatMb(size ?: if (JpPrefs.variant == DictVariant.Full) 11_500_000L else 1_450_000L)})", { DictJobs.start(ctx, JpPrefs.variant) }, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

fun formatMb(bytes: Long): String = if (bytes < 1_000_000) "${bytes / 1000} KB" else "${(bytes / 1_000_000.0).let { if (it < 10) "%.1f".format(it) else it.toInt().toString() }} MB"

// --- Word rows ----------------------------------------------------------------------------------------------------

@Composable
private fun ParticleGroup(particles: List<WordInfo>, installed: Boolean, savedKeys: Set<String>, romajis: List<String>?) {
    val c = OneUi.colors
    var open by remember { mutableStateOf(false) }
    Column {
        ListRow(
            "Particles (${particles.size})", summary = particles.joinToString("  ") { it.word.surface }.take(60),
            trailing = { Icon(if (open) OneUiIcons.ChevronUp else OneUiIcons.ChevronDown, null, tint = c.textSecondary, modifier = Modifier.size(22.dp)) },
            onClick = { open = !open }, dimmed = true,
        )
        if (open) particles.forEach { WordRow(it, installed, savedKeys, romajis?.getOrNull(it.idx).orEmpty(), dim = true) }
    }
}

@Composable
private fun WordRow(info: WordInfo, installed: Boolean, savedKeys: Set<String>, romaji: String, dim: Boolean) {
    val c = OneUi.colors
    val ctx = LocalContext.current
    val w = info.word
    var expanded by remember { mutableStateOf(false) }
    val e = info.entry
    val key = w.base
    val isSaved = key in savedKeys
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

    Row(
        Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
            .padding(start = 18.dp, end = 6.dp, top = 12.dp, bottom = 12.dp)
            .graphicsLayer { alpha = if (dim) 0.55f else 1f },
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f)) {
            RubyWord(w.segments, 26.sp)
            Text(if (inflected) "${w.pos} · from ${w.base}" else w.pos, fontSize = 13.sp, color = c.textSecondary, modifier = Modifier.padding(top = 2.dp))
            val reading = w.reading
            val showReading = reading.isNotEmpty() && reading != w.surface
            if (showReading || (JpPrefs.romaji && romaji.isNotEmpty())) {
                Row(Modifier.padding(top = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (showReading) Text(reading, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = c.accent)
                    if (JpPrefs.romaji && romaji.isNotEmpty()) {
                        if (showReading) Spacer(Modifier.width(10.dp))
                        Text(romaji, fontSize = 14.sp, color = c.textSecondary)
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            when {
                e == null && !installed -> Text("Download the dictionary to see meanings", fontSize = 13.sp, color = c.textSecondary)
                e == null -> Text("No dictionary entry", fontSize = 13.sp, color = c.textSecondary)
                !expanded -> Column {
                    e.senses.take(3).forEachIndexed { i, s ->
                        Text("${i + 1}  " + s.glosses.take(3).joinToString("; "), fontSize = 14.sp, lineHeight = 19.sp, color = c.text)
                    }
                    if (e.senses.size > 3) Text("+${e.senses.size - 3} more, tap to expand", fontSize = 13.sp, color = c.textSecondary, modifier = Modifier.padding(top = 2.dp))
                    // Same kana, different word: show the other likely ones (がっき → 学期 or 楽器).
                    if (info.alts.isNotEmpty()) Text(
                        "Or: " + info.alts.joinToString(" · ") { a -> a.kanji.first() + "  " + (a.senses.firstOrNull()?.glosses?.firstOrNull() ?: "") },
                        fontSize = 13.5.sp, lineHeight = 18.sp, color = c.textSecondary, modifier = Modifier.padding(top = 6.dp),
                    )
                }
                else -> Column {
                    var lastPos = ""
                    e.senses.forEachIndexed { i, s ->
                        val p = Jmdict.posLabel(s.pos)
                        if (p != lastPos) { lastPos = p; if (p.isNotEmpty()) Text(p, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = c.textSecondary, modifier = Modifier.padding(top = if (i == 0) 0.dp else 8.dp, bottom = 2.dp)) }
                        Text("${i + 1}  " + s.glosses.joinToString("; "), fontSize = 14.sp, lineHeight = 19.sp, color = c.text, modifier = Modifier.padding(vertical = 1.dp))
                    }
                    val forms = (e.kanji + e.kana).filter { it != w.surface }
                    if (forms.isNotEmpty()) Text("Also written: " + forms.take(8).joinToString("、"), fontSize = 13.sp, color = c.textSecondary, modifier = Modifier.padding(top = 8.dp))
                    if (GoogleTranslate.installed(ctx)) Text(
                        "Hear it in Google Translate", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = c.accent,
                        modifier = Modifier.padding(top = 10.dp).clip(RoundedCornerShape(8.dp)).clickable { GoogleTranslate.open(ctx, w.base) }.padding(vertical = 4.dp),
                    )
                }
            }
        }
        Box(
            Modifier.size(48.dp).clip(CircleShape).clickable {
                if (isSaved) WordStore.remove(key)
                else WordStore.add(key, w.base, dictReading, meaningText, e?.senses?.firstOrNull()?.pos?.let(Jmdict::posLabel) ?: w.pos)
            },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (isSaved) Icons.Filled.Star else Icons.Outlined.StarBorder,
                if (isSaved) "Remove from My words" else "Save to My words",
                tint = if (isSaved) c.accent else c.textSecondary, modifier = Modifier.size(24.dp),
            )
        }
    }
}
