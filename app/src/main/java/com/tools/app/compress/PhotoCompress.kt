package com.tools.app.compress

import com.tools.app.R
import com.tools.app.ui.t
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.net.Uri
import android.provider.OpenableColumns
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

/** Photos picked for the Compressor's Photo tab. */
data class PhotoSource(val items: List<PhotoItem>, val thumb: Bitmap?) {
    val totalBytes get() = items.sumOf { it.sizeBytes }
}

data class PhotoItem(val uri: Uri, val name: String, val sizeBytes: Long, val width: Int, val height: Int)

/** Longest side choices (0 = keep) and JPEG qualities for High / Balanced / Small. */
val PhotoSizes get() = listOf(0 to t(R.string.convert_original), 3000 to "3000", 2048 to "2048", 1280 to "1280")
val PhotoQualities = listOf(90, 80, 65)
/** "Max file size" choices in KB (0 = no limit), for sites with upload limits. */
val PhotoLimits get() = listOf(0 to t(R.string.compress_no_limit), 2000 to "2 MB", 1000 to "1 MB", 500 to "500 KB")

/**
 * Re-encodes photos as JPEG: scaled so the longest side is at most the chosen size, at the chosen quality, and, with a
 * file size limit, stepped down (quality first, then dimensions) until it fits. Re-encoding also drops location and
 * camera details. Transparent images get a white background.
 */
object PhotoCompress {
    fun read(context: Context, uri: Uri): PhotoItem {
        var name = "Photo"; var size = 0L
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use {
            if (it.moveToFirst()) { it.getString(0)?.let { n -> name = n }; size = it.getLong(1) }
        }
        var w = 0; var h = 0
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { d, info, _ ->
            w = info.size.width; h = info.size.height
            d.setTargetSize(1, 1) // only the size is needed
        }
        return PhotoItem(uri, name.substringBeforeLast('.'), size, w, h)
    }

    fun thumb(context: Context, uri: Uri): Bitmap? = runCatching { com.tools.app.ui.Images.load(context, uri, 480) }.getOrNull()

    /** Decodes [item] with its longest side at most [maxSide] (0 = full size), on white. */
    private fun decode(context: Context, item: PhotoItem, maxSide: Int): Bitmap {
        val b = ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, item.uri)) { d, info, _ ->
            d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val long = max(info.size.width, info.size.height)
            if (maxSide in 1 until long) {
                val s = maxSide.toFloat() / long
                d.setTargetSize((info.size.width * s).roundToInt().coerceAtLeast(1), (info.size.height * s).roundToInt().coerceAtLeast(1))
            }
        }
        if (!b.hasAlpha()) return b
        val out = Bitmap.createBitmap(b.width, b.height, Bitmap.Config.ARGB_8888)
        Canvas(out).apply { drawColor(Color.WHITE); drawBitmap(b, 0f, 0f, null) }
        return out
    }

    private fun jpeg(b: Bitmap, q: Int): ByteArray = ByteArrayOutputStream().use { b.compress(Bitmap.CompressFormat.JPEG, q, it); it.toByteArray() }

    /** The JPEG bytes for one photo. */
    fun encode(context: Context, item: PhotoItem, maxSide: Int, quality: Int, limitKb: Int): ByteArray {
        var side = maxSide
        var bmp = decode(context, item, side)
        val limit = limitKb * 1000L
        var bytes = jpeg(bmp, quality)
        if (limit <= 0 || bytes.size <= limit) return bytes
        while (true) {
            // Highest quality (down to 50) that fits at this size.
            var lo = 50; var hi = quality - 1; var best: ByteArray? = null
            while (lo <= hi) {
                val mid = (lo + hi) / 2
                val b = jpeg(bmp, mid)
                if (b.size <= limit) { best = b; lo = mid + 1 } else hi = mid - 1
            }
            if (best != null) return best
            // Still too big at quality 50: shrink by 20% and try again.
            val long = max(bmp.width, bmp.height)
            if (long <= 320) return jpeg(bmp, 50)
            side = (long * 0.8f).roundToInt()
            bmp = decode(context, item, side)
            bytes = jpeg(bmp, quality)
            if (bytes.size <= limit) return bytes
        }
    }
}
