package com.tools.app.blur

import android.graphics.Bitmap
import kotlin.math.max
import kotlin.math.roundToInt

/** Gaussian-like blur: downscale, three box-blur passes, and let the GPU upscale smoothly. */
object BoxBlur {
    /** [strength] 0..1. Returns a small blurred bitmap meant to be drawn stretched over the original. */
    fun blur(src: Bitmap, strength: Float): Bitmap? {
        if (strength <= 0.01f) return null
        val radiusFull = strength * max(src.width, src.height) * 0.035f
        val factor = max(1f, radiusFull / 10f)
        val w = (src.width / factor).roundToInt().coerceAtLeast(1)
        val h = (src.height / factor).roundToInt().coerceAtLeast(1)
        val small = Bitmap.createScaledBitmap(src, w, h, true)
        val r = (radiusFull / factor).roundToInt().coerceIn(1, 30)
        val px = IntArray(w * h)
        small.getPixels(px, 0, w, 0, 0, w, h)
        val tmp = IntArray(w * h)
        repeat(3) {
            pass(px, tmp, w, h, r, horizontal = true)
            pass(tmp, px, w, h, r, horizontal = false)
        }
        return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
    }

    /** Blurs a small software bitmap in place-sized copy with radius [r] (3 box passes ≈ Gaussian). */
    fun blurSmall(small: Bitmap, r: Int): Bitmap {
        val w = small.width; val h = small.height
        val px = IntArray(w * h)
        small.getPixels(px, 0, w, 0, 0, w, h)
        val tmp = IntArray(w * h)
        repeat(3) { pass(px, tmp, w, h, r, horizontal = true); pass(tmp, px, w, h, r, horizontal = false) }
        return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
    }

    private fun pass(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int, horizontal: Boolean) {
        val lines = if (horizontal) h else w
        val len = if (horizontal) w else h
        val step = if (horizontal) 1 else w
        val div = 2 * r + 1
        for (line in 0 until lines) {
            val base = if (horizontal) line * w else line
            var sa = 0; var sr = 0; var sg = 0; var sb = 0
            for (i in -r..r) {
                val c = src[base + i.coerceIn(0, len - 1) * step]
                sa += c ushr 24; sr += (c shr 16) and 0xff; sg += (c shr 8) and 0xff; sb += c and 0xff
            }
            for (i in 0 until len) {
                dst[base + i * step] = ((sa / div) shl 24) or ((sr / div) shl 16) or ((sg / div) shl 8) or (sb / div)
                val add = src[base + (i + r + 1).coerceAtMost(len - 1) * step]
                val rem = src[base + (i - r).coerceAtLeast(0) * step]
                sa += (add ushr 24) - (rem ushr 24)
                sr += ((add shr 16) and 0xff) - ((rem shr 16) and 0xff)
                sg += ((add shr 8) and 0xff) - ((rem shr 8) and 0xff)
                sb += (add and 0xff) - (rem and 0xff)
            }
        }
    }
}
