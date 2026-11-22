package com.tools.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.DeviceFontFamilyName
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Immutable
data class OneUiColors(
    val background: Color,
    val card: Color,
    val text: Color,
    val textSecondary: Color,
    val accent: Color,
    val onAccent: Color,
    val accentContainer: Color,
    val tonal: Color,
    val divider: Color,
    val switchOff: Color,
    val error: Color,
    val sliderTrack: Color,
    /** Pop-up menus and dialogs. */
    val popup: Color,
    val success: Color,
    val isDark: Boolean,
)

/** Samsung's primary blue. */
val SamsungBlue = Color(0xFF3679FD)

private val Light = OneUiColors(
    background = Color(0xFFF4F4F7), card = Color.White.copy(alpha = 0.86f),
    text = Color(0xFF111114), textSecondary = Color(0xFF79797D),
    accent = SamsungBlue, onAccent = Color.White, accentContainer = Color(0xFFE1EAFF),
    tonal = Color(0xFFE7E7EA), divider = Color(0x14000008), switchOff = Color(0xFFC4C4C8),
    error = Color(0xFFE5383B), sliderTrack = Color(0xFFD3D3D6), success = Color(0xFF3BB54A), isDark = false,
    popup = Color.White,
)

// Sampled from One UI 8.5 system apps (Settings, Modes and Routines): Samsung's dark grays are
// slightly cool (a touch of blue), never neutral or warm.
private val Dark = OneUiColors(
    // Cards are slightly see-through so the page glow shows faintly; over plain black they look the same as before.
    background = Color(0xFF010102), card = Color(0xFF1D1D21).copy(alpha = 0.8f),
    text = Color(0xFFFAFAFC), textSecondary = Color(0xFF9B9BA0),
    accent = SamsungBlue, onAccent = Color.White, accentContainer = Color(0xFF16264A),
    tonal = Color(0xFF2A2A2D), divider = Color(0x1AFFFFFF), switchOff = Color(0xFF5A5A5E),
    error = Color(0xFFFF5A5F), sliderTrack = Color(0xFF5F5F61), success = Color(0xFF4CD964), isDark = true,
    popup = Color(0xFF262626),
)

/** Icon-circle colors in the style of Samsung Settings' top-level list. */
object IconColors {
    val Red = Color(0xFFF0544C)
    val Orange = Color(0xFFF5822A)
    val Green = Color(0xFF4CB848)
    val Teal = Color(0xFF22B0A8)
    val Blue = SamsungBlue
    val Purple = Color(0xFF7B5CF5)
    val Pink = Color(0xFFEC4F86)
    val Gray = Color(0xFF8A8A90)
}

/** Samsung's system UI font family ("sec" = SamsungOne or the font chosen in Settings). */
val SamsungFont: FontFamily = FontFamily(
    listOf(FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold, FontWeight.ExtraBold, FontWeight.Black).map {
        Font(DeviceFontFamilyName("sec"), weight = it)
    }
)

private val SamsungTypography = Typography().let { t ->
    fun TextStyle.sec() = copy(fontFamily = SamsungFont)
    Typography(
        displayLarge = t.displayLarge.sec(), displayMedium = t.displayMedium.sec(), displaySmall = t.displaySmall.sec(),
        headlineLarge = t.headlineLarge.sec(), headlineMedium = t.headlineMedium.sec(), headlineSmall = t.headlineSmall.sec(),
        titleLarge = t.titleLarge.sec(), titleMedium = t.titleMedium.sec(), titleSmall = t.titleSmall.sec(),
        bodyLarge = t.bodyLarge.sec(), bodyMedium = t.bodyMedium.sec(), bodySmall = t.bodySmall.sec(),
        labelLarge = t.labelLarge.sec(), labelMedium = t.labelMedium.sec(), labelSmall = t.labelSmall.sec(),
    )
}

private val LocalOneUi = staticCompositionLocalOf { Light }

object OneUi {
    val colors: OneUiColors
        @Composable @ReadOnlyComposable get() = LocalOneUi.current
    val CardRadius = 26.dp
    val CardShape = RoundedCornerShape(CardRadius)
}

/** One UI's light colours, e.g. for a page shown light while the phone is in dark mode. */
val OneUiLightColors: OneUiColors get() = Light

/** Draws [content] in One UI's light colours, whatever the system theme. */
@Composable
fun OneUiLight(content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalOneUi provides Light,
        LocalContentColor provides Light.text,
        androidx.compose.foundation.LocalIndication provides PressHighlight.Light,
        content = content,
    )
}

@Composable
fun OneUiTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val ctx = androidx.compose.ui.platform.LocalContext.current
    androidx.compose.runtime.remember { Glow.load(ctx) }
    val c = if (dark) Dark else Light
    val surface = c.card.compositeOver(c.background)
    val scheme = if (dark) darkColorScheme(primary = c.accent, background = c.background, surface = surface, onSurface = c.text)
    else lightColorScheme(primary = c.accent, background = c.background, surface = surface, onSurface = c.text)
    // System font: on Galaxy devices this resolves to SamsungOne.
    MaterialTheme(colorScheme = scheme, typography = SamsungTypography) {
        CompositionLocalProvider(
            LocalOneUi provides c,
            LocalContentColor provides c.text,
            androidx.compose.foundation.LocalIndication provides if (dark) PressHighlight.Dark else PressHighlight.Light,
            content = content,
        )
    }
}

/**
 * One UI's touch feedback: the pressed area softly fills with a translucent tint and fades back,
 * instead of Material's expanding ripple. Quick taps still show the full highlight briefly.
 */
class PressHighlight private constructor(private val color: Color) : androidx.compose.foundation.IndicationNodeFactory {
    override fun create(interactionSource: androidx.compose.foundation.interaction.InteractionSource): androidx.compose.ui.node.DelegatableNode =
        Node(interactionSource, color)

    override fun equals(other: Any?) = other is PressHighlight && other.color == color
    override fun hashCode() = color.hashCode()

    private class Node(
        private val source: androidx.compose.foundation.interaction.InteractionSource,
        private val color: Color,
    ) : androidx.compose.ui.Modifier.Node(), androidx.compose.ui.node.DrawModifierNode {
        private val alpha = androidx.compose.animation.core.Animatable(0f)

        override fun onAttach() {
            coroutineScope.launch {
                var pressJob: kotlinx.coroutines.Job? = null
                source.interactions.collect { i ->
                    when (i) {
                        is androidx.compose.foundation.interaction.PressInteraction.Press ->
                            pressJob = launch { alpha.animateTo(1f, androidx.compose.animation.core.tween(110)) }
                        is androidx.compose.foundation.interaction.PressInteraction.Release,
                        is androidx.compose.foundation.interaction.PressInteraction.Cancel -> launch {
                            pressJob?.join()
                            alpha.animateTo(0f, androidx.compose.animation.core.tween(320))
                        }
                    }
                }
            }
        }

        override fun androidx.compose.ui.graphics.drawscope.ContentDrawScope.draw() {
            drawContent()
            if (alpha.value > 0f) drawRect(color, alpha = alpha.value)
        }
    }

    companion object {
        val Light = PressHighlight(Color(0x14000000))
        val Dark = PressHighlight(Color(0x1FFFFFFF))
    }
}

/** Samsung's standard motion curve (fast start, long soft settle). */
val OneUiEasing = androidx.compose.animation.core.CubicBezierEasing(0.22f, 0.25f, 0f, 1f)
