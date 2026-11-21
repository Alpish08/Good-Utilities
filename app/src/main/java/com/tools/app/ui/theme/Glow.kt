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
 * very blurred colour pools on the page background. Only the Home page has one; tool pages stay plain.
 */
object Glow {
    var enabled by mutableStateOf(true)
        private set

    /** The Home page's chosen glow. */
    var style by mutableStateOf(GlowStyle.Midnight)
        private set

    fun choose(context: Context, s: GlowStyle) {
        style = s
        prefs(context).edit().putString("homeGlow", s.name).apply()
    }
    private var loaded = false

    fun load(context: Context) {
        if (loaded) return
        loaded = true
        // The on/off switch was removed; clear any saved "off" so the Home glow always shows.
        prefs(context).edit().remove("glow").apply()
        enabled = true
        // Older versions saved styles that are gone now (Mist became Midnight); anything unknown falls back to it.
        style = runCatching { GlowStyle.valueOf(prefs(context).getString("homeGlow", null) ?: "") }.getOrDefault(GlowStyle.Midnight)
    }

    fun set(context: Context, on: Boolean) {
        enabled = on
        prefs(context).edit().putBoolean("glow", on).apply()
    }

    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences("ui", Context.MODE_PRIVATE)
}

/** One colour pool: centre and radius as fractions of the screen width / height. */
class Pool(val dark: Color, val light: Color, val x: Float, val y: Float, val r: Float)

/**
 * The Home page's background colours: all the same soft wash at the top of the page (the former "Mist"),
 * each in its own colour. Pick one from Home's menu → Background.
 */
enum class GlowStyle(val label: String, vararg val pools: Pool) {
    /** Deep indigo (the original Mist). */
    Midnight("Midnight", wash(0xFF1C2B4A, 0xFFCAD6F2)),
    /** Violet, like dusk turning to night. */
    Amethyst("Amethyst", wash(0xFF2E2152, 0xFFDCCCF7)),
    /** Blue-green, clear shallow water. */
    Lagoon("Lagoon", wash(0xFF123F45, 0xFFC2E8E6)),
    /** Soft green. */
    Moss("Moss", wash(0xFF1E3A22, 0xFFCFE8C9)),
    /** Warm amber, a fire burning low. */
    Ember("Ember", wash(0xFF4A2A12, 0xFFF7D6B8, 0.45f)),
    /** Dusty rose. */
    Rosewood("Rosewood", wash(0xFF4A1D33, 0xFFF5C9D8, 0.45f)),
    /** No colour, just a lighter grey at the top. */
    Graphite("Graphite", wash(0xFF2C2C30, 0xFFD9D9DE)),
    ;

    /** The colour shown on the picker's swatch. */
    val swatch get() = pools.first()
}

/** The one wash every background shares: centred at the top, wider than the screen. */
private fun wash(dark: Long, light: Long, alpha: Float = 0.5f) =
    Pool(Color(dark).copy(alpha = alpha), Color(light).copy(alpha = alpha), 0.5f, 0.0f, 1.2f)

/** The glow of the page being composed (set per page by the navigator); null = no glow (tool pages). */
val LocalGlowStyle = staticCompositionLocalOf<GlowStyle?> { null }

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
            if (on && style != null) translate(-origin.floatValue, -originY.floatValue) { drawGlow(style, c.isDark) }
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
