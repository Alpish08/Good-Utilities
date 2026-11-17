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
import kotlinx.coroutines.launch
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.paddingFrom
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tools.app.ui.theme.OneUi

/** My Files' search pill: #313131 with a hairline #3A3A3D edge (sampled from One UI 8.5). */
fun pillColor(dark: Boolean) = if (dark) Color(0xFF313131) else Color.White
fun pillEdge(dark: Boolean) = if (dark) Color(0xFF3A3A3D) else Color(0x14000000)

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

/** The tab pill's faint hairline outline (false hides it). */
const val TabPillOutline = true

/** Labels under the tab icons (Gallery / Phone / Samsung Health style); false = icon-only like Clock. */
const val TabPillLabels = true

/**
 * One UI 8.5 bottom navigation: a floating 56 dp pill. With labels (Gallery, Phone, Samsung Health, measured):
 * a 22 dp icon over a 10 sp label, tabs 66 dp apart (Phone uses 72; five tabs need it tighter), the selected tab on a 70 × 48 dp capsule. Icon-only (Clock):
 * tabs 56 dp apart on a 60 × 46 dp capsule. The selected tab gets a filled icon.
 */
@Composable
fun TabPill(tabs: List<PillTab>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier, labels: Boolean = TabPillLabels) {
    val c = OneUi.colors
    // Labelled tabs a little closer than Phone's 72 dp, so five fit in a shorter pill.
    val slot = if (labels) 66.dp else 56.dp
    val rowPad = if (labels) 7.dp else 8.dp
    val capsuleW = if (labels) 70.dp else 60.dp
    val capsuleH = if (labels) 48.dp else 46.dp
    // The capsule is lit from its centre (a soft glow).
    // Neutral grey like Clock's bar (~#232323 at the edge, #262626 at the centre with the glow below).
    val pillBase = if (c.isDark) Color(0xFF292929).copy(alpha = 0.86f) else Color.White.copy(alpha = 0.88f)
    val pillGlow = if (c.isDark) Color.White.copy(alpha = 0.015f) else Color.White.copy(alpha = 0.3f)
    val restCentre = if (c.isDark) 0.15f else 0.045f
    val restEdge = if (c.isDark) 0.105f else 0.03f
    val pressCentre = if (c.isDark) 0.315f else 0.10f
    val pressEdge = if (c.isDark) 0.22f else 0.07f
    val tintBase = if (c.isDark) Color.White else Color.Black
    val iconOn = if (c.isDark) Color(0xFFFCFCFF) else Color(0xFF111114)
    val iconOff = if (c.isDark) Color(0xFFD9D9DD) else Color(0xFF4A4A4F)

    // Clock's press, measured frame by frame: the capsule jumps to the touched tab at once (no slide), brightens
    // strongly and presses in ~4% over 0.2 s while held, then eases back to rest over ~0.35 s on release. A quick tap
    // flashes bright (peak ~85 ms) and fades.
    var pressedTab by remember { mutableStateOf<Int?>(null) }
    val glow = remember { androidx.compose.animation.core.Animatable(0f) }
    val scale = remember { androidx.compose.animation.core.Animatable(1f) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val shown = pressedTab ?: selected

    // 10 dp above the screen edge (Clock 8, My Files 16).
    Box(modifier.fillMaxWidth().offset(y = 2.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .height(56.dp)
                .shadow(if (c.isDark) 0.dp else 10.dp, CircleShape, ambientColor = Color(0x22000000), spotColor = Color(0x22000000))
                .clip(CircleShape)
                // A very faint hairline, as some One UI apps have (Clock doesn't). Toggled by TabPillOutline.
                .then(if (TabPillOutline) Modifier.border(0.5.dp, if (c.isDark) Color.White.copy(alpha = 0.05f) else Color.Black.copy(alpha = 0.05f), CircleShape) else Modifier)
                .drawBehind {
                    drawRect(pillBase)
                    drawRect(androidx.compose.ui.graphics.Brush.radialGradient(listOf(pillGlow, Color.Transparent), center = center, radius = size.width * 0.6f))
                },
            contentAlignment = Alignment.CenterStart,
        ) {
            // The selected (or pressed) capsule, centred on its slot (a little wider than the slot).
            Box(
                Modifier
                    .offset(x = rowPad + slot * shown - (capsuleW - slot) / 2)
                    .size(width = capsuleW, height = capsuleH)
                    .graphicsLayer { scaleX = scale.value; scaleY = scale.value }
                    .clip(CircleShape)
                    .drawBehind {
                        val g = glow.value
                        val centreA = restCentre + (pressCentre - restCentre) * g
                        val edgeA = restEdge + (pressEdge - restEdge) * g
                        drawRect(
                            androidx.compose.ui.graphics.Brush.radialGradient(
                                listOf(tintBase.copy(alpha = centreA), tintBase.copy(alpha = edgeA)), center = center, radius = size.width * 0.55f,
                            ),
                        )
                    },
            )
            Row(Modifier.padding(horizontal = rowPad)) {
                tabs.forEachIndexed { i, tab ->
                    val on = i == shown
                    Box(
                        Modifier
                            .size(width = slot, height = 56.dp)
                            .pointerInput(i) {
                                detectTapGestures(onPress = {
                                    pressedTab = i
                                    scope.launch { glow.animateTo(1f, tween(200, easing = OneUiEasing)) }
                                    scope.launch { scale.animateTo(0.96f, tween(200, easing = OneUiEasing)) }
                                    val released = tryAwaitRelease()
                                    if (released) onSelect(i)
                                    pressedTab = null
                                    scope.launch {
                                        // A quick tap still flashes before fading, like Clock's.
                                        if (glow.value < 0.9f) glow.animateTo(1f, tween(85))
                                        glow.animateTo(0f, tween(380, easing = OneUiEasing))
                                    }
                                    scope.launch { scale.animateTo(1f, tween(350, easing = OneUiEasing)) }
                                })
                            }
                            .semantics { contentDescription = tab.label },
                        contentAlignment = if (labels) Alignment.TopCenter else Alignment.Center,
                    ) {
                        val tint = if (on) iconOn else iconOff
                        if (!labels) Icon(if (on) tab.selectedIcon else tab.icon, null, tint = tint, modifier = Modifier.size(24.dp))
                        else {
                            Icon(if (on) tab.selectedIcon else tab.icon, null, tint = tint, modifier = Modifier.padding(top = 8.35.dp).size(22.3.dp)) // 7% under 24 dp, same centre
                            androidx.compose.material3.Text(
                                tab.label,
                                color = tint,
                                fontFamily = com.tools.app.ui.theme.SamsungFont,
                                fontSize = 10.sp,
                                letterSpacing = 0.sp,
                                fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium,
                                maxLines = 1,
                                // Baseline 43 dp below the pill's top, as in Phone / Modes and Routines.
                                modifier = Modifier.paddingFrom(androidx.compose.ui.layout.FirstBaseline, before = 43.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
