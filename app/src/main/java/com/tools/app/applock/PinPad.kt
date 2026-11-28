package com.tools.app.applock

import com.tools.app.R
import com.tools.app.ui.t
import com.tools.app.ui.OneUiIcons
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tools.app.ui.theme.SamsungFont
import kotlin.math.roundToInt

/** PIN dots; [slots] = 0 means "unknown length": show only the entered digits. Shakes when [shake] changes. */
@Composable
fun PinDots(entered: Int, slots: Int, shake: Int, color: Color) {
    val dx = remember { Animatable(0f) }
    LaunchedEffect(shake) {
        if (shake > 0) dx.animateTo(0f, keyframes {
            durationMillis = 360
            -18f at 45; 16f at 105; -12f at 165; 8f at 225; -4f at 285
        })
    }
    val count = if (slots > 0) slots else maxOf(entered, 1)
    Row(
        Modifier.height(20.dp).offset { IntOffset(dx.value.dp.roundToPx(), 0) },
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) { i ->
            val filled = i < entered
            Box(
                Modifier.size(12.dp).clip(CircleShape)
                    .then(if (filled) Modifier.background(color) else Modifier.border(1.5.dp, color.copy(alpha = 0.55f), CircleShape)),
            )
        }
    }
}

private val LETTERS = mapOf('2' to "ABC", '3' to "DEF", '4' to "GHI", '5' to "JKL", '6' to "MNO", '7' to "PQRS", '8' to "TUV", '9' to "WXYZ")

/** One UI-style number pad. [leftKey] fills the empty bottom-left slot (e.g. fingerprint). */
@Composable
fun PinKeypad(
    color: Color,
    onDigit: (Char) -> Unit,
    onDelete: () -> Unit,
    enabled: Boolean = true,
    leftKey: (@Composable () -> Unit)? = null,
) {
    val rows = listOf("123", "456", "789")
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        rows.forEach { r ->
            Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) { r.forEach { d -> DigitKey(d, color, enabled) { onDigit(d) } } }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(28.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(80.dp), contentAlignment = Alignment.Center) { leftKey?.invoke() }
            DigitKey('0', color, enabled) { onDigit('0') }
            Box(
                Modifier.size(80.dp).clip(CircleShape).clickable(enabled = enabled, onClick = onDelete),
                contentAlignment = Alignment.Center,
            ) { Icon(OneUiIcons.Backspace, t(R.string.pdf_delete), tint = color, modifier = Modifier.size(26.dp)) }
        }
    }
}

@Composable
private fun DigitKey(d: Char, color: Color, enabled: Boolean, onClick: () -> Unit) {
    Column(
        Modifier.size(80.dp).clip(CircleShape).clickable(enabled = enabled, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(d.toString(), fontFamily = SamsungFont, fontSize = 32.sp, fontWeight = FontWeight.Medium, color = color, lineHeight = 34.sp)
        // "1" keeps an empty letters line so its row lines up; "0" has none so it centres with ⌫.
        if (d != '0') Text(LETTERS[d] ?: " ", fontFamily = SamsungFont, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = color.copy(alpha = 0.55f), letterSpacing = 1.sp)
    }
}

