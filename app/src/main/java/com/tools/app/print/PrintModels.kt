package com.tools.app.print

import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Pure helpers and models for the print tool (no Android dependencies, JVM-testable). */

class PrintFile(val name: String, val path: String, val isPdf: Boolean, val pages: Int)

/** One printable page. [w]/[h] are the content size (points for PDFs, pixels for images). */
class PrintPage(val fileIndex: Int, val index: Int, val w: Float, val h: Float, val isPdf: Boolean)

data class PrintOptions(
    val copies: Int = 1,
    /** 0 = off, 1 = long edge, 2 = short edge. */
    val duplex: Int = 0,
    val color: Boolean = true,
    /** 0 = auto, 1 = portrait, 2 = landscape. */
    val orientation: Int = 0,
    /** 0 = draft, 1 = normal, 2 = high. */
    val quality: Int = 1,
    val media: String? = null,
    val fit: Boolean = true,
)

/** A paper size: IPP media name, display label and size in points. */
data class Paper(val id: String, val label: String, val wPt: Float, val hPt: Float)

object PrintModels {
    private val SIZE = Regex("""_(\d+(?:\.\d+)?)x(\d+(?:\.\d+)?)(mm|in)$""")
    private val NAMES = mapOf(
        "iso_a3" to "A3", "iso_a4" to "A4", "iso_a5" to "A5", "iso_a6" to "A6", "iso_b4" to "B4 (ISO)", "iso_b5" to "B5 (ISO)",
        "jis_b4" to "B4 (JIS)", "jis_b5" to "B5 (JIS)", "na_letter" to "Letter", "na_legal" to "Legal", "na_executive" to "Executive",
        "na_ledger" to "Tabloid", "na_invoice" to "Statement", "na_foolscap" to "Folio", "oe_photo-l" to "Photo L",
    )

    /** Parses names like iso_a4_210x297mm / na_letter_8.5x11in. Custom ranges and unknown formats give null. */
    fun parseMedia(id: String): Paper? {
        if (id.startsWith("custom_") || id.contains("_min_") || id.contains("_max_") || id.contains("-borderless")) return null
        val m = SIZE.find(id) ?: return null
        val a = m.groupValues[1].toFloatOrNull() ?: return null
        val b = m.groupValues[2].toFloatOrNull() ?: return null
        val mm = m.groupValues[3] == "mm"
        val wPt = if (mm) a / 25.4f * 72f else a * 72f
        val hPt = if (mm) b / 25.4f * 72f else b * 72f
        val base = id.substring(0, m.range.first)
        val label = NAMES[base] ?: run {
            val wTxt = trim(a); val hTxt = trim(b)
            "$wTxt × $hTxt ${if (mm) "mm" else "in"}"
        }
        return Paper(id, label, wPt, hPt)
    }

    private fun trim(f: Float): String = if (f == f.roundToInt().toFloat()) f.roundToInt().toString() else String.format(Locale.US, "%.1f", f)

    fun defaultPapers(): List<Paper> = listOfNotNull(parseMedia("iso_a4_210x297mm"), parseMedia("na_letter_8.5x11in"))

    fun papersFor(caps: PrinterCaps?): List<Paper> {
        val list = caps?.mediaSupported.orEmpty().mapNotNull { parseMedia(it) }.distinctBy { it.label }
        return list.ifEmpty { defaultPapers() }
    }

    fun defaultPaper(caps: PrinterCaps?, papers: List<Paper>, locale: Locale = Locale.getDefault()): Paper {
        caps?.mediaDefault?.let { d -> papers.firstOrNull { it.id == d } }?.let { return it }
        val letter = locale.country in setOf("US", "CA", "MX", "PH")
        return papers.firstOrNull { it.id.startsWith(if (letter) "na_letter" else "iso_a4") } ?: papers.first()
    }

    // ---- page ranges --------------------------------------------------------------------------------------------

    /** "1-3, 5" -> {0,1,2,4} (0-based), clipped to [count]. Returns null when the text is not a valid range list. */
    fun parseRanges(text: String, count: Int): Set<Int>? {
        val t = text.trim()
        if (t.isEmpty()) return null
        val out = sortedSetOf<Int>()
        for (part in t.split(',', ';', ' ').map { it.trim() }.filter { it.isNotEmpty() }) {
            val bits = part.split('-', '–')
            when (bits.size) {
                1 -> { val n = bits[0].toIntOrNull() ?: return null; if (n < 1) return null; if (n <= count) out += n - 1 }
                2 -> {
                    val a = if (bits[0].isEmpty()) 1 else bits[0].toIntOrNull() ?: return null
                    val b = if (bits[1].isEmpty()) count else bits[1].toIntOrNull() ?: return null
                    if (a < 1 || b < a) return null
                    for (n in a..min(b, count)) out += n - 1
                }
                else -> return null
            }
        }
        return if (out.isEmpty()) null else out
    }

    /** {0,1,2,4} -> "1-3, 5". */
    fun formatRanges(pages: Set<Int>): String {
        val s = pages.sorted()
        if (s.isEmpty()) return ""
        val parts = ArrayList<String>()
        var start = s[0]; var prev = s[0]
        for (i in 1..s.size) {
            val cur = s.getOrNull(i)
            if (cur != null && cur == prev + 1) { prev = cur; continue }
            parts += if (start == prev) "${start + 1}" else "${start + 1}-${prev + 1}"
            if (cur != null) { start = cur; prev = cur }
        }
        return parts.joinToString(", ")
    }

    /** IPP page-ranges (1-based inclusive) for a sorted selection. */
    fun toIppRanges(pages: Set<Int>): List<IntRange> {
        val s = pages.sorted()
        val out = ArrayList<IntRange>()
        var start = s.firstOrNull() ?: return out; var prev = start
        for (i in 1..s.size) {
            val cur = s.getOrNull(i)
            if (cur != null && cur == prev + 1) { prev = cur; continue }
            out += (start + 1)..(prev + 1)
            if (cur != null) { start = cur; prev = cur }
        }
        return out
    }

    // ---- layout -------------------------------------------------------------------------------------------------

    /** Placement of content on a sheet. All in sheet units (points). */
    data class Placement(val scale: Float, val x: Float, val y: Float, val w: Float, val h: Float)

    /**
     * Where the content (size [cw] x [ch] in its own units) goes on a sheet of [sw] x [sh] with [margin] on every side.
     * With [fit] the content is scaled to the printable area; otherwise it keeps [natural] sheet units per content unit
     * (centered, clipped by the sheet).
     */
    fun place(sw: Float, sh: Float, margin: Float, cw: Float, ch: Float, fit: Boolean, natural: Float): Placement {
        val aw = max(1f, sw - 2 * margin); val ah = max(1f, sh - 2 * margin)
        val sc = if (fit) min(aw / cw, ah / ch) else natural
        val w = cw * sc; val h = ch * sc
        return Placement(sc, (sw - w) / 2f, if (fit) (sh - h) / 2f else (sh - h) / 2f, w, h)
    }

    /** Sheet orientation for one page given the user's setting. */
    fun landscape(orientation: Int, page: PrintPage): Boolean = when (orientation) {
        1 -> false; 2 -> true; else -> page.w > page.h
    }

    const val MARGIN_PT = 14f
    /** Images without a DPI: assume this many pixels per inch when "Fit to page" is off. */
    const val IMAGE_DPI = 200f
    fun naturalScale(page: PrintPage) = if (page.isPdf) 1f else 72f / IMAGE_DPI

    // ---- printer status -------------------------------------------------------------------------------------------

    enum class Tone { Ok, Busy, Warn, Error }

    /** Friendly status from printer-state and printer-state-reasons. */
    fun status(caps: PrinterCaps): Pair<String, Tone> {
        val r = caps.stateReasons.map { it.removeSuffix("-error").removeSuffix("-warning").removeSuffix("-report") }.toSet()
        fun has(vararg k: String) = k.any { it in r }
        return when {
            has("media-jam", "paper-jam") -> "Paper jam" to Tone.Error
            has("media-empty", "media-needed", "input-tray-missing") -> "Out of paper" to Tone.Error
            has("toner-empty", "marker-supply-empty") -> "Out of toner" to Tone.Error
            has("cover-open", "door-open", "interlock-open") -> "Cover open" to Tone.Error
            has("offline", "shutdown") -> "Offline" to Tone.Error
            has("output-area-full", "output-tray-missing") -> "Output tray full" to Tone.Error
            caps.state == Ipp.PRINTER_STOPPED -> "Stopped" to Tone.Error
            !caps.acceptingJobs -> "Not accepting jobs" to Tone.Error
            caps.state == Ipp.PRINTER_PROCESSING -> "Printing" to Tone.Busy
            has("toner-low", "marker-supply-low") -> "Toner low" to Tone.Warn
            has("media-low") -> "Paper low" to Tone.Warn
            has("opc-life-over", "opc-near-eol", "drum-life-over", "drum-near-eol") -> "Ready · replace drum soon" to Tone.Warn
            has("sleep", "standby") -> "Ready (sleeping)" to Tone.Ok
            else -> "Ready" to Tone.Ok
        }
    }

    /** Only supplies people care about: toner / ink, not drums or waste containers. */
    fun tonerMarkers(caps: PrinterCaps): List<Marker> {
        val withLevel = caps.markers.filter { it.level >= 0 }
        val supplies = withLevel.filter {
            val t = (it.type + " " + it.name).lowercase()
            (t.contains("toner") || t.contains("ink")) && !t.contains("waste") && !t.contains("drum")
        }
        return supplies.ifEmpty { withLevel.filter { !(it.type + it.name).lowercase().let { t -> t.contains("waste") || t.contains("drum") } } }
    }

    // ---- raster choices -----------------------------------------------------------------------------------------

    enum class RasterKind { Pwg, Urf }

    /** Picks the raster resolution (dpi) for a quality level: draft lowest, normal nearest 300, high highest. */
    fun pickDpi(available: List<Int>, quality: Int): Int {
        val list = available.filter { it > 0 }.distinct().sorted()
        if (list.isEmpty()) return 300
        return when (quality) {
            0 -> list.first()
            2 -> list.last()
            else -> list.minByOrNull { kotlin.math.abs(it - 300) + if (it < 300) 1 else 0 } ?: 300
        }
    }

    fun pwgDpis(caps: PrinterCaps): List<Int> = caps.pwgResolutions.map { max(it.dpiX, it.dpiY) }
        .ifEmpty { caps.urfResolutions }.ifEmpty { listOf(300) }

    fun urfDpis(caps: PrinterCaps): List<Int> = caps.urfResolutions.ifEmpty { pwgDpis(caps) }

    fun friendlyIppError(status: Int, message: String?): String = when (status) {
        0x0400 -> "The printer didn't understand the request."
        0x0401, 0x0402 -> "The printer needs a login or doesn't allow this phone."
        0x0404 -> "The printer couldn't find the print queue."
        0x0405 -> "The printer is busy. Try again in a moment."
        0x0406 -> "The printer doesn't know this job any more."
        0x0409 -> "The document is too large for the printer."
        0x040A, 0x040B -> "The printer doesn't accept this kind of document."
        0x040C -> "The printer rejected some print settings."
        0x0503 -> "The printer doesn't support this IPP version."
        0x0504 -> "The printer is not accepting jobs right now."
        0x0505 -> "The printer is busy. Try again in a moment."
        0x0507 -> "The printer is not accepting jobs right now."
        else -> message?.takeIf { it.isNotBlank() } ?: "The printer reported an error (0x${status.toString(16)})."
    }
}
