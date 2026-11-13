package com.tools.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material3.MaterialTheme
import com.tools.app.ui.theme.OneUiEasing
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tools.app.ui.theme.OneUi

/** The floating pill colour used by One UI 8.5 bottom bars (search, tabs, "Add reminder"). */
fun pillColor(dark: Boolean) = if (dark) Color(0xFF2A2A2E).copy(alpha = 0.96f) else Color.White.copy(alpha = 0.97f)

/**
 * One UI 8.5 floating search pill (My Files): a short rounded bar at the bottom of the page. Tapping it
 * stretches it to the full width for typing, and it rides up above the keyboard.
 */
@Composable
fun SearchPill(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    focus: FocusRequester = remember { FocusRequester() },
    onFocusChange: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val c = OneUi.colors
    var focused by remember { mutableStateOf(false) }
    val open = focused || query.isNotEmpty()
    // 0 = the short pill, 1 = stretched across the screen.
    val t by animateFloatAsState(if (open) 1f else 0f, tween(320, easing = OneUiEasing), label = "searchPill")
    // Same font as the rest of the app (SamsungOne), so typed text isn't tighter than the labels.
    val style = MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = c.text)
    BoxWithConstraints(
        modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.ime.exclude(WindowInsets.navigationBars)),
        contentAlignment = Alignment.Center,
    ) {
        val short = 230.dp.coerceAtMost(maxWidth)
        Row(
            Modifier
                .width(short + (maxWidth - short) * t)
                .height(52.dp)
                .shadow(if (c.isDark) 0.dp else 10.dp, CircleShape, ambientColor = Color(0x22000000), spotColor = Color(0x22000000))
                .clip(CircleShape)
                .background(pillColor(c.isDark))
                .clickable(interactionSource = null, indication = null) { focus.requestFocus() }
                .padding(start = 20.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(OneUiIcons.Search, null, tint = c.textSecondary, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(12.dp))
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (query.isEmpty()) Text(placeholder, style = style.copy(color = c.textSecondary), maxLines = 1)
                BasicTextField(
                    query, onQueryChange,
                    singleLine = true,
                    textStyle = style,
                    cursorBrush = SolidColor(c.accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus).onFocusChanged { focused = it.isFocused; onFocusChange(it.isFocused) },
                )
            }
            if (query.isNotEmpty()) {
                Box(Modifier.size(42.dp).clip(CircleShape).clickable { onQueryChange("") }, contentAlignment = Alignment.Center) {
                    Icon(OneUiIcons.Close, "Clear", tint = c.text, modifier = Modifier.size(22.dp))
                }
            } else Spacer(Modifier.width(14.dp))
        }
    }
}
