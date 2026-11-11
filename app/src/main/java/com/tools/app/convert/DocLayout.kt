package com.tools.app.convert

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.LeadingMarginSpan
import android.text.style.StyleSpan
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipException
import java.util.zip.ZipFile

// ---- Document model -------------------------------------------------------------------------

internal data class Run(val text: String, val bold: Boolean = false, val italic: Boolean = false)

internal sealed interface Block
/** heading: 0 body, 1..6 headings, 10 title. bullet: -1 none, else nesting level. align: 0 start, 1 center, 2 end. */
internal class Para(
    val runs: List<Run>, val heading: Int = 0, val bullet: Int = -1, val align: Int = 0,
    val mono: Boolean = false, val gap: Float = 6f,
) : Block
internal class Table(val rows: List<List<String>>) : Block
internal data object PageBreak : Block

// ---- DOCX parsing ---------------------------------------------------------------------------

internal object Docx {
    class Invalid : Exception("This isn't a valid .docx file")

    fun parse(file: File): List<Block> {
        try {
            ZipFile(file).use { zip ->
                val doc = zip.getEntry("word/document.xml") ?: throw Invalid()
                val styles = zip.getEntry("word/styles.xml")?.let { e -> zip.getInputStream(e).use(::readStyles) } ?: emptyMap()
                return zip.getInputStream(doc).use { parseBody(it, styles) }
            }
        } catch (e: ZipException) {
            throw Invalid()
        }
    }

    private fun readStyles(input: InputStream): Map<String, String> {
        val p = Xml.newPullParser().apply { setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false); setInput(input, null) }
        val map = HashMap<String, String>()
        var id: String? = null
        var ev = p.eventType
        while (ev != XmlPullParser.END_DOCUMENT) {
            if (ev == XmlPullParser.START_TAG) when (p.name) {
                "w:style" -> id = p.getAttributeValue(null, "w:styleId")
                "w:name" -> id?.let { map[it] = p.getAttributeValue(null, "w:val") ?: "" }
            } else if (ev == XmlPullParser.END_TAG && p.name == "w:style") id = null
            ev = p.next()
        }
        return map
    }

    private fun headingLevel(styleId: String?, styles: Map<String, String>): Int {
        val name = (styles[styleId] ?: styleId ?: "").lowercase()
        return when {
            name == "title" -> 10
            name.startsWith("heading") -> Regex("\\d").find(name)?.value?.toInt()?.coerceIn(1, 6) ?: 2
            name == "subtitle" -> 3
            else -> 0
        }
    }

    private fun parseBody(input: InputStream, styles: Map<String, String>): List<Block> {
        val p = Xml.newPullParser().apply { setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false); setInput(input, null) }
        val blocks = ArrayList<Block>()
        var tblDepth = 0
        var rows = ArrayList<List<String>>(); var row = ArrayList<String>(); var cell = StringBuilder()
        var runs = ArrayList<Run>(); var style: String? = null
        var bullet = -1; var align = 0; var inPPr = false
        var bold = false; var italic = false; var pageBreak = false
        var skipDepth = -1

        fun addText(t: String) { if (t.isNotEmpty()) runs.add(Run(t, bold, italic)) }
        fun flag(): Boolean { val v = p.getAttributeValue(null, "w:val"); return v == null || (v != "0" && v != "false" && v != "off") }

        var ev = p.eventType
        while (ev != XmlPullParser.END_DOCUMENT) {
            if (skipDepth >= 0) {
                if (ev == XmlPullParser.END_TAG && p.depth == skipDepth) skipDepth = -1
                ev = p.next(); continue
            }
            if (ev == XmlPullParser.START_TAG) {
                when (p.name) {
                    // Pictures, drawings and text boxes are not converted.
                    "mc:Fallback", "w:drawing", "w:pict", "w:txbxContent", "w:object" -> skipDepth = p.depth
                    "w:tbl" -> { tblDepth++; if (tblDepth == 1) rows = ArrayList() }
                    "w:tr" -> if (tblDepth == 1) row = ArrayList()
                    "w:tc" -> if (tblDepth == 1) cell = StringBuilder()
                    "w:p" -> { runs = ArrayList(); style = null; bullet = -1; align = 0; pageBreak = false }
                    "w:pPr" -> inPPr = true
                    "w:pStyle" -> style = p.getAttributeValue(null, "w:val")
                    "w:ilvl" -> if (inPPr) bullet = (p.getAttributeValue(null, "w:val")?.toIntOrNull() ?: 0).coerceIn(0, 5)
                    "w:numId" -> if (inPPr && p.getAttributeValue(null, "w:val") != "0") bullet = maxOf(bullet, 0)
                    "w:jc" -> if (inPPr) align = when (p.getAttributeValue(null, "w:val")) { "center" -> 1; "right", "end" -> 2; else -> 0 }
                    "w:r" -> { bold = false; italic = false }
                    "w:b" -> if (!inPPr) bold = flag()
                    "w:i" -> if (!inPPr) italic = flag()
                    "w:t" -> addText(p.nextText())
                    "w:tab" -> if (!inPPr) addText("\t")
                    "w:br" -> if (p.getAttributeValue(null, "w:type") == "page") pageBreak = true else addText("\n")
                    "w:cr" -> addText("\n")
                }
            } else if (ev == XmlPullParser.END_TAG) {
                when (p.name) {
                    "w:pPr" -> inPPr = false
                    "w:p" -> {
                        val text = runs.joinToString("") { it.text }
                        if (tblDepth > 0) {
                            if (text.isNotEmpty()) { if (cell.isNotEmpty()) cell.append('\n'); cell.append(text) }
                        } else {
                            blocks.add(Para(runs, headingLevel(style, styles), bullet, align))
                            if (pageBreak) blocks.add(PageBreak)
                        }
                    }
                    "w:tc" -> if (tblDepth == 1) row.add(cell.toString().trim())
                    "w:tr" -> if (tblDepth == 1) rows.add(row)
                    "w:tbl" -> { if (tblDepth == 1) blocks.add(Table(rows)); tblDepth-- }
                }
            }
            ev = p.next()
        }
        return blocks
    }

    fun toText(blocks: List<Block>): String = buildString {
        for (b in blocks) when (b) {
            is Para -> {
                if (b.bullet >= 0) append("  ".repeat(b.bullet)).append("• ")
                append(b.runs.joinToString("") { it.text })
                append('\n')
                if (b.heading in 1..10 && b.heading != 0) append('\n')
            }
            is Table -> { b.rows.forEach { r -> appendLine(r.joinToString("\t") { it.replace('\n', ' ') }) }; append('\n') }
            PageBreak -> append('\n')
        }
    }.trim() + "\n"
}

// ---- Plain text / Markdown ------------------------------------------------------------------

internal object PlainText {
    fun decode(file: File): String {
        val bytes = file.readBytes()
        val start = if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) 3 else 0
        return String(bytes, start, bytes.size - start, Charsets.UTF_8)
    }

    fun plain(text: String): List<Block> =
        text.replace("\r\n", "\n").replace('\r', '\n').split('\n').map { Para(listOf(Run(it.replace("\t", "    "))), mono = false, gap = 0f) }

    private val inline = Regex("\\*\\*(.+?)\\*\\*|\\*(.+?)\\*|`([^`]+)`|\\[([^\\]]+)]\\(([^)]*)\\)")

    private fun inlineRuns(s: String): List<Run> {
        val out = ArrayList<Run>()
        var last = 0
        for (m in inline.findAll(s)) {
            if (m.range.first > last) out.add(Run(s.substring(last, m.range.first)))
            val g = m.groupValues
            out.add(when {
                g[1].isNotEmpty() -> Run(g[1], bold = true)
                g[2].isNotEmpty() -> Run(g[2], italic = true)
                g[3].isNotEmpty() -> Run(g[3])
                else -> Run(g[4])
            })
            last = m.range.last + 1
        }
        if (last < s.length) out.add(Run(s.substring(last)))
        return out
    }

    fun markdown(text: String): List<Block> {
        val blocks = ArrayList<Block>()
        val buf = ArrayList<String>()
        fun flush() {
            if (buf.isNotEmpty()) blocks.add(Para(inlineRuns(buf.joinToString(" ")))); buf.clear()
        }
        var fenced = false
        for (raw in text.replace("\r\n", "\n").replace('\r', '\n').split('\n')) {
            val line = raw.trimEnd()
            if (line.trimStart().startsWith("```")) { flush(); fenced = !fenced; continue }
            if (fenced) { blocks.add(Para(listOf(Run(line.replace("\t", "    "))), mono = true, gap = 0f)); continue }
            val heading = Regex("^(#{1,6})\\s+(.*)$").find(line)
            val bullet = Regex("^(\\s*)[-*+]\\s+(.*)$").find(line)
            when {
                line.isBlank() -> flush()
                heading != null -> { flush(); blocks.add(Para(inlineRuns(heading.groupValues[2].trimEnd('#', ' ')), heading = heading.groupValues[1].length)) }
                bullet != null -> { flush(); blocks.add(Para(inlineRuns(bullet.groupValues[2]), bullet = (bullet.groupValues[1].length / 2).coerceAtMost(4), gap = 3f)) }
                Regex("^\\s*([-*_]\\s*){3,}$").matches(line) -> flush()
                else -> buf.add(line.trim().removePrefix("> "))
            }
        }
        flush()
        return blocks
    }
}

// ---- A4 layout with PdfDocument + StaticLayout ----------------------------------------------

internal class PdfBuilder {
    private val doc = PdfDocument()
    private var page: PdfDocument.Page? = null
    private var canvas: Canvas? = null
    private var y = 0f
    private var pageNo = 0

    private val sans = Typeface.create("sans-serif", Typeface.NORMAL)
    private val footerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 9f; color = Color.rgb(130, 130, 130); typeface = sans; textAlign = Paint.Align.CENTER
    }
    private val border = Paint().apply { style = Paint.Style.STROKE; strokeWidth = 0.7f; color = Color.rgb(170, 170, 170) }

    private val bottom get() = H - MB

    private fun newPage() {
        finishPage()
        pageNo++
        page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, pageNo).create())
        canvas = page!!.canvas
        y = MT
    }

    private fun finishPage() {
        val p = page ?: return
        canvas!!.drawText(pageNo.toString(), W / 2f, H - 30f, footerPaint)
        doc.finishPage(p)
        page = null
    }

    private fun paint(size: Float, mono: Boolean) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size; color = Color.BLACK; typeface = if (mono) Typeface.MONOSPACE else sans
    }

    private fun layout(text: CharSequence, paint: TextPaint, width: Int, align: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL) =
        StaticLayout.Builder.obtain(text, 0, text.length, paint, width).setAlignment(align).setLineSpacing(0f, 1.18f).setIncludePad(false).build()

    /** Draws [l] at x, flowing its lines onto as many pages as needed. */
    private fun flow(l: StaticLayout, x: Float) {
        var line = 0
        while (line < l.lineCount) {
            val avail = bottom - y
            val top = l.getLineTop(line)
            var end = line
            while (end < l.lineCount && l.getLineBottom(end) - top <= avail) end++
            if (end == line) {
                if (y > MT + 1f) { newPage(); continue }
                end = line + 1
            }
            val h = (l.getLineBottom(end - 1) - top).toFloat()
            val c = canvas!!
            c.save()
            c.clipRect(x, y, x + l.width, y + h)
            c.translate(x, y - top)
            l.draw(c)
            c.restore()
            y += h
            line = end
            if (line < l.lineCount) newPage()
        }
    }

    private fun para(b: Para) {
        val size = when (b.heading) { 10 -> 24f; 1 -> 20f; 2 -> 16.5f; 3 -> 14f; 0 -> if (b.mono) 9.5f else 11f; else -> 12.5f }
        val p = paint(size, b.mono)
        val sb = SpannableStringBuilder()
        var first = 0f; var rest = 0f
        if (b.bullet >= 0) {
            first = 10f + b.bullet * 18f
            sb.append("•  ")
            rest = first + p.measureText("•  ")
        }
        for (r in b.runs) {
            if (r.text.isEmpty()) continue
            val s = sb.length
            sb.append(r.text)
            val e = sb.length
            val boldNow = r.bold || b.heading > 0
            if (boldNow && r.italic) sb.setSpan(StyleSpan(Typeface.BOLD_ITALIC), s, e, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            else if (boldNow) sb.setSpan(StyleSpan(Typeface.BOLD), s, e, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            else if (r.italic) sb.setSpan(StyleSpan(Typeface.ITALIC), s, e, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        if (b.runs.all { it.text.isEmpty() }) {
            if (page == null) newPage()
            if (y + size * 1.18f > bottom) newPage()
            y += size * 1.18f + b.gap
            return
        }
        if (b.bullet >= 0) sb.setSpan(LeadingMarginSpan.Standard(first.toInt(), rest.toInt()), 0, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        val align = when (b.align) { 1 -> Layout.Alignment.ALIGN_CENTER; 2 -> Layout.Alignment.ALIGN_OPPOSITE; else -> Layout.Alignment.ALIGN_NORMAL }
        val l = layout(sb, p, CONTENT_W.toInt(), align)
        if (page == null) newPage()
        if (b.heading > 0) {
            if (y > MT + 1f) y += size * 0.6f
            // Keep a heading with the first lines that follow it.
            if (y + l.height + 40f > bottom && y > MT + 1f) newPage()
        }
        flow(l, MX)
        y += b.gap + if (b.heading > 0) 2f else 0f
    }

    private fun table(t: Table) {
        val cols = t.rows.maxOfOrNull { it.size } ?: return
        if (cols == 0) return
        if (page == null) newPage()
        val colW = CONTENT_W / cols
        val pad = 5f
        val p = paint(10f, false)
        for (row in t.rows) {
            val layouts = (0 until cols).map { layout(row.getOrElse(it) { "" }, p, (colW - 2 * pad).toInt().coerceAtLeast(10)) }
            val h = (layouts.maxOf { it.height } + 2 * pad).coerceAtMost(bottom - MT)
            if (y + h > bottom) newPage()
            val c = canvas!!
            for ((i, l) in layouts.withIndex()) {
                val x = MX + i * colW
                c.drawRect(x, y, x + colW, y + h, border)
                c.save()
                c.clipRect(x, y, x + colW, y + h)
                c.translate(x + pad, y + pad)
                l.draw(c)
                c.restore()
            }
            y += h
        }
        y += 10f
    }

    fun render(blocks: List<Block>, out: OutputStream, progress: (Float) -> Unit) {
        newPage()
        for ((i, b) in blocks.withIndex()) {
            when (b) {
                is Para -> para(b)
                is Table -> table(b)
                PageBreak -> if (y > MT + 1f) newPage()
            }
            if (i % 20 == 0) progress(i / blocks.size.toFloat())
        }
        finishPage()
        doc.writeTo(out)
        doc.close()
    }

    companion object {
        const val W = 595
        const val H = 842
        const val MX = 60f
        const val MT = 64f
        const val MB = 64f
        const val CONTENT_W = W - 2 * MX
    }
}
