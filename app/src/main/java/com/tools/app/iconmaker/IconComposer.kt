package com.tools.app.iconmaker

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Image steps of the Icon maker: tidy the logo, then place it on the pack's empty tile. */
object IconComposer {

    /** Makes the logo's flat background transparent: only colour connected to the edges goes, so white inside a logo stays. */
    fun removeFlatBackground(src: Bitmap, tolerance: Float = 38f): Bitmap {
        val w = src.width; val h = src.height
        val px = IntArray(w * h); src.getPixels(px, 0, w, 0, 0, w, h)
        val corners = intArrayOf(px[0], px[w - 1], px[(h - 1) * w], px[h * w - 1])
        val bg = corners.groupBy { it or 0xFF000000.toInt() }.maxBy { it.value.size }.key
        val br = (bg shr 16) and 0xFF; val bgG = (bg shr 8) and 0xFF; val bb = bg and 0xFF
        fun dist(c: Int): Float {
            if ((c ushr 24) < 16) return 0f
            val dr = ((c shr 16) and 0xFF) - br; val dg = ((c shr 8) and 0xFF) - bgG; val db = (c and 0xFF) - bb
            return sqrt((dr * dr + dg * dg + db * db).toFloat())
        }
        val soft = tolerance * 1.8f
        val seen = BooleanArray(w * h)
        val queue = IntArray(w * h); var head = 0; var tail = 0
        fun push(i: Int) { if (!seen[i] && dist(px[i]) < soft) { seen[i] = true; queue[tail++] = i } }
        for (x in 0 until w) { push(x); push((h - 1) * w + x) }
        for (y in 0 until h) { push(y * w); push(y * w + w - 1) }
        while (head < tail) {
            val i = queue[head++]
            val d = dist(px[i])
            val a = if (d <= tolerance) 0f else (d - tolerance) / (soft - tolerance)
            val oldA = px[i] ushr 24
            px[i] = (px[i] and 0x00FFFFFF) or (((oldA * a).roundToInt().coerceIn(0, 255)) shl 24)
            if (d > tolerance) continue // soft edge: don't spread past it
            val x = i % w; val y = i / w
            if (x > 0) push(i - 1); if (x < w - 1) push(i + 1); if (y > 0) push(i - w); if (y < h - 1) push(i + w)
        }
        return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
    }

    /** Turns every visible pixel white (keeps the shape and its soft edges), for dark one-colour logos. */
    fun makeWhite(src: Bitmap): Bitmap {
        val w = src.width; val h = src.height
        val px = IntArray(w * h); src.getPixels(px, 0, w, 0, 0, w, h)
        for (i in px.indices) px[i] = (px[i] and 0xFF000000.toInt()) or 0x00FFFFFF
        return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
    }

    /** Rounds the image into a circle. A square logo is kept whole; anything else is centred on its shorter side. */
    fun circle(src: Bitmap): Bitmap {
        val s = min(src.width, src.height)
        val out = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
        val shader = BitmapShader(src, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
            setLocalMatrix(android.graphics.Matrix().apply { setTranslate(-(src.width - s) / 2f, -(src.height - s) / 2f) })
        }
        Canvas(out).drawCircle(s / 2f, s / 2f, s / 2f, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.shader = shader })
        return out
    }

    /** Trims fully transparent edges. */
    fun trim(b: Bitmap): Bitmap = com.tools.app.cutout.Cutout.cropToSubject(b)

    /** The tile's solid body (ignores soft shadows), so the logo is centred on what you see. */
    private fun bodyBounds(base: Bitmap): RectF {
        val w = base.width; val h = base.height
        val px = IntArray(w * h); base.getPixels(px, 0, w, 0, 0, w, h)
        var l = w; var t = h; var r = -1; var b = -1
        for (y in 0 until h) for (x in 0 until w) if (px[y * w + x] ushr 24 >= 200) {
            if (x < l) l = x; if (x > r) r = x; if (y < t) t = y; if (y > b) b = y
        }
        return if (r < l) RectF(0f, 0f, w.toFloat(), h.toFloat()) else RectF(l.toFloat(), t.toFloat(), r + 1f, b + 1f)
    }

    /** Halves the logo until it is close to the target, then one filtered step: much smoother than one big jump. */
    private fun scaleTo(src: Bitmap, tw: Int, th: Int): Bitmap {
        var b = src
        while (b.width / 2 >= tw && b.height / 2 >= th) b = Bitmap.createScaledBitmap(b, b.width / 2, b.height / 2, true)
        return if (b.width == tw && b.height == th) b else Bitmap.createScaledBitmap(b, max(1, tw), max(1, th), true)
    }

    /**
     * Places [logo] on [base]: fitted inside a square box [size] × the icon's shorter side (keeping its shape), centred
     * on the tile's body, moved by [dx] / [dy] percent of the icon size.
     */
    fun compose(base: Bitmap, logo: Bitmap, size: Float, dx: Int, dy: Int): Bitmap {
        val out = base.copy(Bitmap.Config.ARGB_8888, true)
        val body = bodyBounds(base)
        val box = size * min(base.width, base.height)
        val scale = min(box / logo.width, box / logo.height)
        val lw = (logo.width * scale).roundToInt().coerceAtLeast(1)
        val lh = (logo.height * scale).roundToInt().coerceAtLeast(1)
        val cx = body.centerX() + dx * base.width / 100f
        val cy = body.centerY() + dy * base.height / 100f
        Canvas(out).drawBitmap(scaleTo(logo, lw, lh), cx - lw / 2f, cy - lh / 2f, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        return out
    }
}
