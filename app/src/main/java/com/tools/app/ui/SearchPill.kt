package com.tools.app.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.LaunchedEffect

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
import androidx.compose.foundation.layout.imeAnimationTarget
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
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import com.tools.app.ui.theme.OneUiEasing
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tools.app.ui.theme.OneUi

/** My Files' search pill: #313131 with a hairline #3A3A3D edge (sampled from One UI 8.5). */
fun pillColor(dark: Boolean) = if (dark) Color(0xFF313131) else Color.White
private fun pillEdge(dark: Boolean) = if (dark) Color(0xFF3A3A3D) else Color(0x14000000)

/**
 * One UI 8.5 search bar, opened from the toolbar's search button: a full-width rounded bar at the bottom
 * (My Files' style), focused straight away and riding above the keyboard. Closing the keyboard with
 * nothing typed closes it ([onClose]).
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
    placeholder: String = "Search",
    modifier: Modifier = Modifier,
) {
    val c = OneUi.colors
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    // The first back press only hides the keyboard; with nothing typed, that ends the search. It ends as soon
    // as the keyboard starts closing, so the bar fades out where it is instead of dropping first.
    val keyboardOpening = WindowInsets.imeAnimationTarget.getBottom(androidx.compose.ui.platform.LocalDensity.current) > 0
    var hadIme by remember { mutableStateOf(false) }
    val latestQuery by androidx.compose.runtime.rememberUpdatedState(query)
    LaunchedEffect(keyboardOpening) {
        if (keyboardOpening) hadIme = true
        else if (hadIme) { hadIme = false; if (latestQuery.isEmpty()) onClose() }
    }
    val style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.5.sp, lineHeight = 21.sp, fontWeight = FontWeight.Bold, color = c.text)
    val hint = if (c.isDark) Color(0xFFA3A3A7) else Color(0xFF8A8A8F)
    // Where the bar sits. Samsung Keyboard slides faster than the insets report, so following the keyboard
    // let it cover the bar. Instead the bar goes straight to where the keyboard will end up, and while the
    // keyboard slides away it stays put (above it) and only drops once the keyboard is gone.
    val density = androidx.compose.ui.platform.LocalDensity.current
    val nav = WindowInsets.navigationBars.getBottom(density)
    val target = (WindowInsets.imeAnimationTarget.getBottom(density) - nav).coerceAtLeast(0)
    val current = (WindowInsets.ime.getBottom(density) - nav).coerceAtLeast(0)
    var held by remember { mutableStateOf(0) }
    if (target > 0) held = target
    val lift = when {
        target > 0 -> target
        current > 0 -> held
        else -> 0
    }
    // Hidden until the keyboard says where it will end up, so the bar never flashes at the bottom first
    // (shown anyway after a moment, e.g. with a hardware keyboard).
    var placed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(450); placed = true }
    if (target > 0) placed = true
    Box(
        modifier.fillMaxWidth().padding(bottom = with(density) { lift.toDp() })
            .graphicsLayer { alpha = if (placed) 1f else 0f; compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.ModulateAlpha },
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier
                .offset(y = (-6).dp)
                .fillMaxWidth()
                .height(53.dp)
                .shadow(if (c.isDark) 0.dp else 10.dp, CircleShape, ambientColor = Color(0x22000000), spotColor = Color(0x22000000))
                .clip(CircleShape)
                .background(pillColor(c.isDark))
                .border(0.6.dp, pillEdge(c.isDark), CircleShape)
                .clickable(interactionSource = null, indication = null) { focus.requestFocus() }
                .padding(start = 17.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(OneUiIcons.Search, null, tint = hint, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(6.dp))
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (query.isEmpty()) Text(placeholder, style = style.copy(color = hint), maxLines = 1)
                BasicTextField(
                    query, onQueryChange,
                    singleLine = true,
                    textStyle = style,
                    cursorBrush = SolidColor(c.accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
            }
            if (query.isNotEmpty()) Box(Modifier.size(40.dp).clip(CircleShape).clickable { onQueryChange("") }, contentAlignment = Alignment.Center) {
                Icon(OneUiIcons.Close, "Clear", tint = c.text, modifier = Modifier.size(22.dp))
            } else Spacer(Modifier.width(16.dp))
        }
    }
}

/** One tab of a [TabPill]: outline icon, and the filled one shown while selected. */
class PillTab(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector, val selectedIcon: androidx.compose.ui.graphics.vector.ImageVector)

/**
 * One UI 8.5 icon-only bottom navigation (Clock): a floating 56 dp pill of icons 56 dp apart; the selected tab
 * sits on a lighter 64 × 48 dp capsule that glides between tabs, with a filled icon.
 */
@Composable
fun TabPill(tabs: List<PillTab>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val c = OneUi.colors
    val slot = 56.dp
    val x by androidx.compose.animation.core.animateDpAsState(slot * selected, tween(320, easing = OneUiEasing), label = "tab")
    Box(modifier.fillMaxWidth().offset(y = 4.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .height(56.dp)
                .shadow(if (c.isDark) 0.dp else 10.dp, CircleShape, ambientColor = Color(0x22000000), spotColor = Color(0x22000000))
                .clip(CircleShape)
                // 40% see-through, like Clock's bar.
                .background(if (c.isDark) Color(0xFF262A2C).copy(alpha = 0.6f) else Color.White.copy(alpha = 0.6f))
                // Faint hairline and highlight, to sit quietly on the see-through pill.
                .border(0.6.dp, if (c.isDark) Color.White.copy(alpha = 0.06f) else Color.Black.copy(alpha = 0.05f), CircleShape)
                .padding(horizontal = 4.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            // The selected capsule: 64 dp wide, so it reaches 4 dp past the slot on each side of the end tabs.
            Box(
                Modifier
                    .offset(x = x)
                    .size(width = 64.dp, height = 48.dp)
                    .clip(CircleShape)
                    .background(if (c.isDark) Color.White.copy(alpha = 0.085f) else Color.Black.copy(alpha = 0.05f)),
            )
            Row(Modifier.padding(horizontal = 4.dp)) {
                tabs.forEachIndexed { i, tab ->
                    val on = i == selected
                    Box(
                        Modifier
                            .size(width = slot, height = 56.dp)
                            .clickable(interactionSource = null, indication = null) { onSelect(i) }
                            .semantics { contentDescription = tab.label },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            if (on) tab.selectedIcon else tab.icon, null,
                            tint = if (on) c.text else c.text.copy(alpha = 0.85f),
                            modifier = Modifier.size(25.dp),
                        )
                    }
                }
            }
        }
    }
}
