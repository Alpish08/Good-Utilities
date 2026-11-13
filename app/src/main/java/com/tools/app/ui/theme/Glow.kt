package com.tools.app.ui.theme

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope

/**
 * The soft glow behind pages in Samsung's newest apps (Clock, Reminder, Samsung Health): a few large,
 * very blurred colour pools on the page background. It stays put while the list scrolls over it.
 */
object Glow {
    var enabled by mutableStateOf(true)
        private set
    private var loaded = false

    fun load(context: Context) {
        if (loaded) return
        loaded = true
        enabled = prefs(context).getBoolean("glow", true)
    }

    fun set(context: Context, on: Boolean) {
        enabled = on
        prefs(context).edit().putBoolean("glow", on).apply()
    }

    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences("ui", Context.MODE_PRIVATE)
}

/** Page background: plain One UI background, plus the glow when it's switched on. */
fun Modifier.pageBackground(): Modifier = composed {
    val c = OneUi.colors
    val on = Glow.enabled
    drawBehind {
        drawRect(c.background)
        if (on) drawGlow(c.isDark)
    }
}

private fun DrawScope.drawGlow(dark: Boolean) {
    val w = size.width
    val h = size.height
    fun pool(color: Color, x: Float, y: Float, r: Float) = drawCircle(
        Brush.radialGradient(
            0f to color, 0.45f to color.copy(alpha = color.alpha * 0.55f), 1f to Color.Transparent,
            center = Offset(x, y), radius = r,
        ),
        radius = r, center = Offset(x, y),
    )
    if (dark) {
        // Deep navy at the top, violet on the right, a faint teal low on the left (Clock / Timer).
        pool(Color(0xFF1A2C5E).copy(alpha = 0.55f), w * 0.2f, h * 0.02f, w * 1.05f)
        pool(Color(0xFF3B2A73).copy(alpha = 0.42f), w * 1.0f, h * 0.48f, w * 0.85f)
        pool(Color(0xFF123F4A).copy(alpha = 0.32f), w * 0.0f, h * 0.9f, w * 0.8f)
    } else {
        pool(Color(0xFFB8CBFF).copy(alpha = 0.55f), w * 0.2f, h * 0.02f, w * 1.05f)
        pool(Color(0xFFD9C8FF).copy(alpha = 0.45f), w * 1.0f, h * 0.48f, w * 0.85f)
        pool(Color(0xFFBFE8EC).copy(alpha = 0.4f), w * 0.0f, h * 0.9f, w * 0.8f)
    }
}
