package com.tools.app.ui.theme

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow

/**
 * The soft glow behind pages in Samsung's newest apps (Clock, Reminder, Samsung Health): a few large,
 * very blurred colour pools on the page background. Each part of the app has its own colours.
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

/** One colour pool: centre and radius as fractions of the screen width / height. */
class Pool(val dark: Color, val light: Color, val x: Float, val y: Float, val r: Float)

/** A page's glow: its colour pools, back to front. */
enum class GlowStyle(vararg val pools: Pool) {
    /** Home: night-sky navy and violet with a little teal (Clock / Timer). */
    Home(
        Pool(Color(0xFF1A2C5E).copy(alpha = 0.55f), Color(0xFFB8CBFF).copy(alpha = 0.55f), 0.2f, 0.02f, 1.05f),
        Pool(Color(0xFF3B2A73).copy(alpha = 0.42f), Color(0xFFD9C8FF).copy(alpha = 0.45f), 1.0f, 0.48f, 0.85f),
        Pool(Color(0xFF123F4A).copy(alpha = 0.32f), Color(0xFFBFE8EC).copy(alpha = 0.4f), 0.0f, 0.9f, 0.8f),
    ),
    /** Media: warm rose and amber (Samsung Health / Music). */
    Media(
        Pool(Color(0xFF5A1E3C).copy(alpha = 0.5f), Color(0xFFFFC6D6).copy(alpha = 0.5f), 0.95f, 0.0f, 1.0f),
        Pool(Color(0xFF5C3312).copy(alpha = 0.42f), Color(0xFFFFDDB8).copy(alpha = 0.5f), 0.05f, 0.55f, 0.85f),
        Pool(Color(0xFF2E1A4F).copy(alpha = 0.3f), Color(0xFFE5D6FF).copy(alpha = 0.35f), 0.9f, 0.95f, 0.8f),
    ),
    /** Files: deep ocean blue and cyan (My Files). */
    Files(
        Pool(Color(0xFF0E3557).copy(alpha = 0.6f), Color(0xFFB5DAFF).copy(alpha = 0.55f), 0.85f, 0.02f, 1.05f),
        Pool(Color(0xFF13305E).copy(alpha = 0.42f), Color(0xFFC4CFFF).copy(alpha = 0.45f), 0.0f, 0.5f, 0.85f),
        Pool(Color(0xFF0D4448).copy(alpha = 0.32f), Color(0xFFBDEDE6).copy(alpha = 0.4f), 0.95f, 0.92f, 0.8f),
    ),
    /** Images: magenta and violet (Gallery). */
    Images(
        Pool(Color(0xFF4E1C5E).copy(alpha = 0.55f), Color(0xFFF0C8FF).copy(alpha = 0.5f), 0.15f, 0.02f, 1.0f),
        Pool(Color(0xFF5E1D45).copy(alpha = 0.4f), Color(0xFFFFC8E4).copy(alpha = 0.45f), 1.0f, 0.5f, 0.85f),
        Pool(Color(0xFF1E2A66).copy(alpha = 0.3f), Color(0xFFC8D2FF).copy(alpha = 0.38f), 0.05f, 0.95f, 0.8f),
    ),
    /** Device: green and teal (Device care). */
    Device(
        Pool(Color(0xFF0F4535).copy(alpha = 0.55f), Color(0xFFBFF0D8).copy(alpha = 0.5f), 0.2f, 0.0f, 1.05f),
        Pool(Color(0xFF103A58).copy(alpha = 0.4f), Color(0xFFBEE0FF).copy(alpha = 0.42f), 1.0f, 0.5f, 0.85f),
        Pool(Color(0xFF29451A).copy(alpha = 0.28f), Color(0xFFDDF2C2).copy(alpha = 0.38f), 0.0f, 0.95f, 0.8f),
    ),
    /** Miscellaneous: amber dusk and purple (Reminder). */
    Misc(
        Pool(Color(0xFF4F3B10).copy(alpha = 0.48f), Color(0xFFFFE6A8).copy(alpha = 0.5f), 0.85f, 0.0f, 1.0f),
        Pool(Color(0xFF34226B).copy(alpha = 0.45f), Color(0xFFDCCBFF).copy(alpha = 0.45f), 0.0f, 0.45f, 0.9f),
        Pool(Color(0xFF4A1F33).copy(alpha = 0.28f), Color(0xFFFFCFDD).copy(alpha = 0.36f), 1.0f, 0.95f, 0.8f),
    ),
}

/** The glow of the page being composed (set per page by the navigator). */
val LocalGlowStyle = staticCompositionLocalOf { GlowStyle.Home }

/**
 * Page background: the plain One UI background plus the page's glow. The glow is pinned to the screen,
 * not the page, so it doesn't slide during page transitions; only its colours change as the new page
 * slides over.
 */
fun Modifier.pageBackground(): Modifier = composed {
    val c = OneUi.colors
    val style = LocalGlowStyle.current
    val on = Glow.enabled
    val origin = remember { mutableFloatStateOf(0f) }
    val originY = remember { mutableFloatStateOf(0f) }
    this
        .onGloballyPositioned { val p = it.positionInWindow(); origin.floatValue = p.x; originY.floatValue = p.y }
        .drawBehind {
            drawRect(c.background)
            if (on) translate(-origin.floatValue, -originY.floatValue) { drawGlow(style, c.isDark) }
        }
}

/** The glow on its own (behind all pages), for the screen behind a page that is fading or shrinking away. */
fun DrawScope.drawGlow(style: GlowStyle, dark: Boolean, alpha: Float = 1f) {
    val w = size.width
    val h = size.height
    for (p in style.pools) {
        val base = if (dark) p.dark else p.light
        val color = base.copy(alpha = base.alpha * alpha)
        val center = Offset(w * p.x, h * p.y)
        val r = w * p.r
        drawCircle(
            Brush.radialGradient(
                0f to color, 0.45f to color.copy(alpha = color.alpha * 0.55f), 1f to Color.Transparent,
                center = center, radius = r,
            ),
            radius = r, center = center,
        )
    }
}
