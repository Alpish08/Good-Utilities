package com.tools.app.print

import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

/**
 * PWG Raster (PWG 5102.4) and Apple Raster (URF) encoders. Pure Kotlin so they can be unit tested on the JVM.
 *
 * Both formats share the same line compression: every line is preceded by a "repeat this line N more times" byte,
 * followed by pixel-wise PackBits: control 0..127 = repeat the next pixel n+1 times, control 129..255 = copy
 * 257-n literal pixels (2..128).
 */
object Raster {
    const val PWG_HEADER_SIZE = 1796
    const val URF_HEADER_SIZE = 32

    /** Everything the page header needs; [colorSpace] is "sgray" or "srgb". */
    class Page(
        val width: Int,
        val height: Int,
        val dpi: Int,
        val gray: Boolean,
        val duplex: Boolean = false,
        val tumble: Boolean = false,
        /** IPP print-quality enum: 3 draft, 4 normal, 5 high (0 = default). */
        val quality: Int = 0,
        val copies: Int = 1,
        val totalPages: Int = 0,
        val crossFeedTransform: Int = 1,
        val feedTransform: Int = 1,
        val pageSizeNamePwg: String = "",
        val mediaType: String = "",
    ) {
        val bytesPerPixel get() = if (gray) 1 else 3
        val bytesPerLine get() = width * bytesPerPixel
        val whiteByte: Byte = 0xFF.toByte()
    }

    fun pwgFileHeader(): ByteArray = "RaS2".toByteArray(StandardCharsets.US_ASCII)

    fun urfFileHeader(pageCount: Int): ByteArray =
        ByteBuffer.allocate(12).put("UNIRAST".toByteArray(StandardCharsets.US_ASCII)).put(0).putInt(pageCount).array()

    /** The 1796-byte PWG raster page header (big-endian), laid out like cups_page_header2_t with PWG assignments. */
    fun pwgPageHeader(p: Page): ByteArray {
        val b = ByteBuffer.allocate(PWG_HEADER_SIZE) // zero filled, big endian by default
        fun str(off: Int, s: String) { val d = s.toByteArray(StandardCharsets.US_ASCII); b.position(off); b.put(d, 0, minOf(d.size, 63)) }
        fun int(off: Int, v: Int) { b.putInt(off, v) }
        str(0, "PwgRaster")
        str(128, p.mediaType)
        int(272, if (p.duplex) 1 else 0)              // Duplex
        int(276, p.dpi); int(280, p.dpi)               // HWResolution
        int(340, p.copies)                             // NumCopies
        int(344, 0)                                    // Orientation
        // PageSize in points
        int(352, Math.round(p.width * 72f / p.dpi)); int(356, Math.round(p.height * 72f / p.dpi))
        int(368, if (p.tumble) 1 else 0)               // Tumble
        int(372, p.width); int(376, p.height)
        int(384, 8)                                    // BitsPerColor
        int(388, 8 * p.bytesPerPixel)                  // BitsPerPixel
        int(392, p.bytesPerLine)
        int(396, 0)                                    // ColorOrder: chunky
        int(400, if (p.gray) 18 else 19)               // ColorSpace: sGray / sRGB
        int(420, p.bytesPerPixel)                      // NumColors
        int(452, p.totalPages)                         // TotalPageCount
        int(456, p.crossFeedTransform)
        int(460, p.feedTransform)
        int(464, 0); int(468, 0); int(472, p.width); int(476, p.height) // ImageBox
        int(480, 0xFFFFFF)                             // AlternatePrimary
        int(484, p.quality)                            // PrintQuality
        str(1732, p.pageSizeNamePwg)
        return b.array()
    }

    /** 32-byte URF page header. */
    fun urfPageHeader(p: Page): ByteArray {
        val b = ByteBuffer.allocate(URF_HEADER_SIZE)
        b.put(0, (8 * p.bytesPerPixel).toByte())
        b.put(1, (if (p.gray) 0 else 1).toByte())                       // colorspace: sGray 0 / sRGB 1
        b.put(2, (if (!p.duplex) 1 else if (p.tumble) 3 else 2).toByte()) // 1 one-sided, 2 long edge, 3 short edge
        b.put(3, p.quality.toByte())
        b.putInt(8, p.width); b.putInt(12, p.height); b.putInt(16, p.dpi)
        return b.array()
    }

    /**
     * Streams compressed lines into [out]; call [addLine] [Page.height] times per page, then [finish].
     * Lines are accepted top to bottom in the final orientation (transforms are applied by the renderer).
     */
    class LineWriter(private val out: OutputStream, private val bpp: Int, private val width: Int) {
        private var prev: ByteArray? = null
        private var repeats = 0
        private val packed = ByteArrayOutputStream(width * bpp + 64)
        private var prevPacked: ByteArray? = null

        fun addLine(line: ByteArray) {
            val p = prev
            if (p != null && repeats < 255 && p.contentEquals(line)) { repeats++; return }
            flush()
            prev = line.copyOf()
        }

        private fun flush() {
            val p = prev ?: return
            out.write(repeats)
            out.write(pack(p, bpp, width))
            repeats = 0; prev = null
        }

        fun finish() { flush() }
    }

    /** Pixel-wise PackBits for one line. */
    fun pack(line: ByteArray, bpp: Int, width: Int): ByteArray {
        val o = ByteArrayOutputStream(line.size / 2 + 16)
        fun eq(a: Int, b: Int): Boolean {
            val x = a * bpp; val y = b * bpp
            for (k in 0 until bpp) if (line[x + k] != line[y + k]) return false
            return true
        }
        var i = 0
        while (i < width) {
            var run = 1
            while (i + run < width && run < 128 && eq(i, i + run)) run++
            if (run > 1) {
                o.write(run - 1); o.write(line, i * bpp, bpp); i += run
            } else {
                val start = i
                var count = 1
                i++
                while (i < width && count < 128) {
                    if (i + 1 < width && eq(i, i + 1)) break
                    count++; i++
                }
                if (count == 1) { o.write(0); o.write(line, start * bpp, bpp) }
                else { o.write(257 - count); o.write(line, start * bpp, count * bpp) }
            }
        }
        return o.toByteArray()
    }

    /** Inverse of [pack] + line repeats for one page; used by tests. Returns raw pixel rows concatenated. */
    fun unpackPage(data: ByteArray, offset: Int, bpp: Int, width: Int, height: Int): Pair<ByteArray, Int> {
        val out = ByteArrayOutputStream(width * bpp * height)
        var pos = offset
        var rows = 0
        while (rows < height) {
            val rep = (data[pos++].toInt() and 0xFF) + 1
            val line = ByteArrayOutputStream(width * bpp)
            var px = 0
            while (px < width) {
                val c = data[pos++].toInt()
                if (c in 0..127) {
                    val n = c + 1
                    repeat(n) { line.write(data, pos, bpp) }
                    pos += bpp; px += n
                } else {
                    val n = 257 - (c and 0xFF)
                    line.write(data, pos, n * bpp); pos += n * bpp; px += n
                }
            }
            val bytes = line.toByteArray()
            repeat(rep) { out.write(bytes) }
            rows += rep
        }
        return out.toByteArray() to pos
    }
}
