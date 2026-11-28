package com.tools.app.reader

import com.tools.app.R
import com.tools.app.ui.t
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tools.app.ui.OneUiIcons
import com.tools.app.ui.OverflowMenu
import com.tools.app.ui.ToolbarIcon
import com.tools.app.ui.theme.OneUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.withContext

internal enum class TKind { Para, Heading, Bullet, Quote, Code, Rule, Gap }

internal class TBlock(val kind: TKind, val level: Int, val text: AnnotatedString)

private val inline = Regex("\\*\\*(.+?)\\*\\*|__(.+?)__|\\*(.+?)\\*|`([^`]+)`|\\[([^\\]]+)]\\(([^)]*)\\)")

private fun styled(s: String): AnnotatedString = buildAnnotatedString {
    var last = 0
    for (m in inline.findAll(s)) {
        if (m.range.first > last) append(s.substring(last, m.range.first))
        val g = m.groupValues
        when {
            g[1].isNotEmpty() -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(g[1]) }
            g[2].isNotEmpty() -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(g[2]) }
            g[3].isNotEmpty() -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(g[3]) }
            g[4].isNotEmpty() -> withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = Color(0x33888888))) { append(g[4]) }
            else -> withStyle(SpanStyle(color = Color(0xFF3679FD))) { append(g[5]) }
        }
        last = m.range.last + 1
    }
    if (last < s.length) append(s.substring(last))
}

internal fun parseText(raw: String, markdown: Boolean): List<TBlock> {
    val lines = raw.replace("\r\n", "\n").replace('\r', '\n').split('\n')
    val out = ArrayList<TBlock>()
    if (!markdown) {
        var blank = false
        for (l in lines) {
            if (l.isBlank()) { if (!blank) out.add(TBlock(TKind.Gap, 0, AnnotatedString(""))); blank = true }
            else { out.add(TBlock(TKind.Para, 0, AnnotatedString(l.replace("\t", "    ")))); blank = false }
        }
        return out
    }
    val para = ArrayList<String>()
    fun flush() { if (para.isNotEmpty()) out.add(TBlock(TKind.Para, 0, styled(para.joinToString(" ")))); para.clear() }
    val heading = Regex("^(#{1,6})\\s+(.*)$")
    val bullet = Regex("^(\\s*)([-*+]|\\d+[.)])\\s+(.*)$")
    var i = 0
    while (i < lines.size) {
        val line = lines[i].trimEnd()
        val t = line.trimStart()
        when {
            t.startsWith("```") -> {
                flush()
                val code = ArrayList<String>()
                i++
                while (i < lines.size && !lines[i].trimStart().startsWith("```")) { code.add(lines[i].replace("\t", "    ")); i++ }
                out.add(TBlock(TKind.Code, 0, AnnotatedString(code.joinToString("\n"))))
            }
            line.isBlank() -> { flush(); if (out.lastOrNull()?.kind != TKind.Gap) out.add(TBlock(TKind.Gap, 0, AnnotatedString(""))) }
            heading.matches(line) -> { flush(); val m = heading.find(line)!!; out.add(TBlock(TKind.Heading, m.groupValues[1].length, styled(m.groupValues[2].trim()))) }
            Regex("^\\s*([-*_])(\\s*\\1){2,}\\s*$").matches(line) -> { flush(); out.add(TBlock(TKind.Rule, 0, AnnotatedString(""))) }
            t.startsWith(">") -> { flush(); out.add(TBlock(TKind.Quote, 0, styled(t.removePrefix(">").trim()))) }
            bullet.matches(line) -> {
                flush()
                val m = bullet.find(line)!!
                val marker = if (m.groupValues[2][0].isDigit()) m.groupValues[2] else "•"
                out.add(TBlock(TKind.Bullet, m.groupValues[1].length / 2, buildAnnotatedString { append("$marker  "); append(styled(m.groupValues[3])) }))
            }
            else -> para.add(t)
        }
        i++
    }
    flush()
    return out
}

@OptIn(FlowPreview::class)
@Composable
internal fun TextViewer(
    info: DocInfo, blocks: List<TBlock>, settings: ReaderStore.Settings, start: Position?,
    onPosition: (Position) -> Unit, onBack: () -> Unit,
) {
    val c = OneUi.colors
    val ctx = LocalContext.current
    val view = LocalView.current
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val list = rememberLazyListState(initialFirstVisibleItemIndex = (start?.page ?: 0).coerceIn(0, (blocks.size - 1).coerceAtLeast(0)))
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var hits by remember { mutableStateOf<List<Pair<Int, Int>>>(emptyList()) }   // block, start offset
    var current by remember { mutableIntStateOf(-1) }
    var infoOpen by remember { mutableStateOf(false) }

    DisposableEffect(settings.keepOn) { view.keepScreenOn = settings.keepOn; onDispose { view.keepScreenOn = false } }
    androidx.activity.compose.BackHandler(searching) { searching = false }
    LaunchedEffect(list) { snapshotFlow { list.firstVisibleItemIndex }.debounce(600).collect { onPosition(Position(it, 0f, 1f)) } }
    DisposableEffect(Unit) { onDispose { onPosition(Position(list.firstVisibleItemIndex, 0f, 1f)) } }

    LaunchedEffect(searching, query) {
        if (!searching || query.isBlank()) { hits = emptyList(); current = -1; return@LaunchedEffect }
        delay(250)
        val q = query.trim().lowercase()
        val found = withContext(Dispatchers.Default) {
            val r = ArrayList<Pair<Int, Int>>()
            blocks.forEachIndexed { bi, b ->
                val t = b.text.text.lowercase()
                var at = t.indexOf(q)
                while (at >= 0 && r.size < 20000) { r.add(bi to at); at = t.indexOf(q, at + q.length) }
            }
            r
        }
        hits = found
        current = if (found.isEmpty()) -1 else 0
        if (found.isNotEmpty()) list.scrollToItem(found[0].first)
    }
    fun step(d: Int) {
        if (hits.isEmpty()) return
        current = (current + d + hits.size) % hits.size
    }
    LaunchedEffect(current) { hits.getOrNull(current)?.let { list.animateScrollToItem(it.first) } }
    val byBlock by remember(hits) { derivedStateOf { hits.withIndex().groupBy({ it.value.first }, { it.index to it.value.second }) } }

    Column(Modifier.fillMaxSize().background(c.background)) {
        if (searching) {
            SearchBar(
                query, { query = it },
                status = when { query.isBlank() -> ""; hits.isEmpty() -> t(R.string.reader_no_results); else -> t(R.string.reader_of, current + 1, hits.size) },
                onPrev = { step(-1) }, onNext = { step(1) }, onClose = { searching = false }, alpha = 1f,
            )
        } else {
            ReaderBar(info.name, onBack, alpha = 1f) {
                ToolbarIcon(OneUiIcons.Search, t(R.string.home_search)) { searching = true }
                OverflowMenu(listOf(
                    t(R.string.reader_increase_text_size) to { settings.changeFontSize(settings.fontSize + 2f) },
                    t(R.string.reader_decrease_text_size) to { settings.changeFontSize(settings.fontSize - 2f) },
                    (if (settings.keepOn) t(R.string.reader_allow_screen_to_turn_off) else t(R.string.reader_keep_screen_on)) to { settings.changeKeepOn(!settings.keepOn) },
                    t(R.string.video_share) to { shareDocument(ctx, info) },
                    t(R.string.reader_file_info) to { infoOpen = true },
                ))
            }
        }
        SelectionContainer(Modifier.weight(1f)) {
            LazyColumn(Modifier.fillMaxSize(), state = list, contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = navBottom + 32.dp)) {
                items(blocks.size) { bi ->
                    val b = blocks[bi]
                    val fs = settings.fontSize
                    val marks = byBlock[bi]
                    val shown = if (marks == null) b.text else buildAnnotatedString {
                        append(b.text)
                        for ((hi, s) in marks) addStyle(
                            SpanStyle(background = if (hi == current) Color(0xFFFF8A00) else Color(0xFFFFE600), color = Color.Black),
                            s, (s + query.trim().length).coerceAtMost(b.text.length),
                        )
                    }
                    when (b.kind) {
                        TKind.Gap -> Box(Modifier.height((fs * 0.7f).dp))
                        TKind.Rule -> HorizontalDivider(Modifier.padding(vertical = 14.dp), 1.dp, c.divider)
                        TKind.Heading -> {
                            val scale = when (b.level) { 1 -> 1.7f; 2 -> 1.45f; 3 -> 1.25f; else -> 1.1f }
                            Text(shown, fontSize = (fs * scale).sp, lineHeight = (fs * scale * 1.3f).sp, fontWeight = FontWeight.Bold, color = c.text, modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))
                        }
                        TKind.Bullet -> Text(shown, fontSize = fs.sp, lineHeight = (fs * 1.55f).sp, color = c.text, modifier = Modifier.padding(start = (12 + b.level * 18).dp, top = 2.dp, bottom = 2.dp))
                        TKind.Quote -> Text(shown, fontSize = fs.sp, lineHeight = (fs * 1.55f).sp, color = c.textSecondary, fontStyle = FontStyle.Italic, modifier = Modifier.padding(start = 14.dp, top = 2.dp, bottom = 2.dp))
                        TKind.Code -> Text(
                            shown, fontSize = (fs * 0.88f).sp, lineHeight = (fs * 1.4f).sp, fontFamily = FontFamily.Monospace, color = c.text,
                            modifier = Modifier.padding(vertical = 6.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.card).padding(14.dp),
                        )
                        TKind.Para -> Text(shown, fontSize = fs.sp, lineHeight = (fs * 1.55f).sp, color = c.text, modifier = Modifier.padding(vertical = 1.dp))
                    }
                }
            }
        }
    }
    if (infoOpen) InfoDialog(info, null, null) { infoOpen = false }
}
