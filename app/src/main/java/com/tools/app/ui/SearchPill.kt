package com.tools.app.ui

import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import dev.chrisbanes.haze.blur.hazeBlur
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
/** Popups' text boxes and buttons: see-through, outline only (true), or filled with [pillColor] (false). */
const val PopupFieldsClear = true

/** The fill of a text box or button inside a popup (transparent while [PopupFieldsClear]). */
fun popupFieldFill(dark: Boolean) = if (PopupFieldsClear) Color.Transparent else pillColor(dark)

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
                // Same frosted look as the short resting pill (and the tab pill): see-through grey over a live blur.
                .shadow(
                    12.dp, CircleShape,
                    ambientColor = if (c.isDark) Color(0x99000000) else Color(0x33000000),
                    spotColor = if (c.isDark) Color(0xCC000000) else Color(0x40000000),
                )
                .clip(CircleShape)
                .then(
                    LocalHazeState.current?.let { haze ->
                        Modifier.hazeBlur(
                            dev.chrisbanes.haze.HazeInput.Sources(haze),
                            dev.chrisbanes.haze.blur.HazeBlurStyle { blurRadius(22.dp); noiseFactor(0f); backgroundColor(c.background) },
                        )
                    } ?: Modifier,
                )
                .border(0.5.dp, if (c.isDark) Color.White.copy(alpha = 0.05f) else Color.Black.copy(alpha = 0.05f), CircleShape)
                .drawBehind { drawRect(searchPillBase(c.isDark)) }
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
 * One UI 8.5 bottom navigation: a floating 58 dp pill (Gallery measured: 203 px = 58 dp, its capsule 50 dp, 4 dp in). With labels (Gallery, Phone, Samsung Health, measured):
 * a 22 dp icon over a 10 sp label, tabs 63 dp apart (Phone uses 72; five tabs need it tighter), the selected tab on a 70 × 48 dp capsule (7 dp wider than the slot). Icon-only (Clock):
 * tabs 56 dp apart on a 60 × 46 dp capsule. The selected tab gets a filled icon.
 */
/** Set by Home: the list the tab pill blurs behind itself. */
val LocalHazeState = androidx.compose.runtime.staticCompositionLocalOf<dev.chrisbanes.haze.HazeState?> { null }

/** [selected] -1 = no tab chosen (Home shows every tool); tapping the selected tab again clears it. */
@Composable
fun TabPill(
    tabs: List<PillTab>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier, labels: Boolean = TabPillLabels,
    /** false: only the tabs are drawn (Home's search morph draws the pill itself while it changes shape). */
    chrome: Boolean = true,
    /** The tabs (icons, labels, capsule) fade with this while the pill morphs into the search bar. */
    contentAlpha: Float = 1f,
    /** …and rise this far (px) with it, riding the bar up as the keyboard opens. */
    contentLift: Float = 0f,
) {
    val c = OneUi.colors
    // Labelled tabs a little closer than Phone's 72 dp, so five fit in a shorter pill.
    val slot = if (labels) 63.dp else 56.dp
    // 7.5 dp: the selected capsule then clears the pill's ends by 4 dp, the same as above and below it ((56 − 48) / 2).
    val rowPad = if (labels) 7.5.dp else 8.dp
    val capsuleW = if (labels) 70.dp else 60.dp
    val capsuleH = if (labels) 50.dp else 46.dp
    // The capsule is lit from its centre (a soft glow).
    // Neutral grey like Clock's bar (~#232323 at the edge, #262626 at the centre with the glow below).
    // A little more see-through than before, so the blur shows (Clock's neutral grey on top of it).
    // Dark: Clock's base grey (#292929), see-through enough for the blur to show.
    val pillBase = if (c.isDark) Color(0xFF292929).copy(alpha = 0.66f) else Color.White.copy(alpha = 0.55f)
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
    // Where the pill and each tab really are (window x), so the capsule lines up with its tab to the pixel.
    var pillX by remember { mutableStateOf(0f) }
    val slotCentres = remember { androidx.compose.runtime.mutableStateMapOf<Int, Float>() }

    // 8 dp above the screen edge (Clock 8, My Files 16).
    Box(modifier.fillMaxWidth().offset(y = 4.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .onGloballyPositioned { pillX = it.positionInRoot().x }
                .height(if (labels) 58.dp else 56.dp)
                .then(
                    if (!chrome) Modifier else Modifier
                        // A soft drop shadow, and the tools scrolling underneath blurred through the pill.
                        .shadow(
                            12.dp, CircleShape,
                            ambientColor = if (c.isDark) Color(0x99000000) else Color(0x33000000),
                            spotColor = if (c.isDark) Color(0xCC000000) else Color(0x40000000),
                        )
                        .clip(CircleShape)
                        .then(
                            LocalHazeState.current?.let { haze ->
                                Modifier.hazeBlur(
                                    dev.chrisbanes.haze.HazeInput.Sources(haze),
                                    dev.chrisbanes.haze.blur.HazeBlurStyle { blurRadius(22.dp); noiseFactor(0f); backgroundColor(c.background) },
                                )
                            } ?: Modifier,
                        )
                        // A very faint hairline, as some One UI apps have (Clock doesn't). Toggled by TabPillOutline.
                        .then(if (TabPillOutline) Modifier.border(0.5.dp, if (c.isDark) Color.White.copy(alpha = 0.05f) else Color.Black.copy(alpha = 0.05f), CircleShape) else Modifier)
                        .drawBehind {
                            drawRect(pillBase)
                            drawRect(androidx.compose.ui.graphics.Brush.radialGradient(listOf(pillGlow, Color.Transparent), center = center, radius = size.width * 0.6f))
                        },
                )
                .graphicsLayer { alpha = contentAlpha; translationY = -contentLift },
            contentAlignment = Alignment.CenterStart,
        ) {
            // The selected (or pressed) capsule, centred on its slot (a little wider than the slot).
            Box(
                Modifier
                    // Centred on the tab as actually laid out: each slot is rounded to whole pixels (63 dp = 220.5 px), so
                    // "slot × index" drifted up to 4 px left by the last tab.
                    .offset {
                        val i = shown.coerceAtLeast(0)
                        val centre = slotCentres[i]?.minus(pillX) ?: (rowPad + slot * i + slot / 2).toPx()
                        androidx.compose.ui.unit.IntOffset(kotlin.math.round(centre - capsuleW.toPx() / 2).toInt(), 0)
                    }
                    .graphicsLayer { alpha = if (shown < 0) 0f else 1f }
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
                            .size(width = slot, height = if (labels) 58.dp else 56.dp)
                            .onGloballyPositioned { slotCentres[i] = it.positionInRoot().x + it.size.width / 2f }
                            // Fading out into the search bar: the tabs no longer take taps.
                            .then(if (contentAlpha < 1f) Modifier else Modifier.pointerInput(i) {
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
                            })
                            .semantics { contentDescription = tab.label },
                        contentAlignment = if (labels) Alignment.TopCenter else Alignment.Center,
                    ) {
                        val tint = if (on) iconOn else iconOff
                        if (!labels) Icon(if (on) tab.selectedIcon else tab.icon, null, tint = tint, modifier = Modifier.size(24.dp))
                        else {
                            Icon(if (on) tab.selectedIcon else tab.icon, null, tint = tint, modifier = Modifier.padding(top = 10.35.dp).size(22.3.dp)) // 7% under 24 dp, same centre
                            androidx.compose.material3.Text(
                                tab.label,
                                color = tint,
                                fontFamily = com.tools.app.ui.theme.SamsungFont,
                                fontSize = 10.sp,
                                letterSpacing = 0.sp,
                                fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium,
                                maxLines = 1,
                                // Icon and label sit 2 dp lower than before so the gap above the icon matches the one under
                                // the label (measured: 38 px over, 52 px under, in the 58 dp pill).
                                modifier = Modifier.paddingFrom(androidx.compose.ui.layout.FirstBaseline, before = 45.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * My Files' search: a short frosted "Search" pill resting at the bottom of the page. Tapping it morphs that same pill
 * into the full-width search bar, rising with the keyboard as it opens (and back down into the pill as it closes).
 * Closing the keyboard with nothing typed ends the search.
 *
 * Measured on the S24 Ultra: the resting pill is 240 × 52 dp, centred 4 dp lower than the bar host's bottom edge; the open
 * bar is full width (16 dp margins), 53 dp tall, sitting on the keyboard. The morph runs between exactly those two.
 */
@Composable
fun PageSearch(
    searching: Boolean,
    query: String,
    onQueryChange: (String) -> Unit,
    onOpen: () -> Unit,
    onClose: () -> Unit,
    label: String = "Search",
) = SearchMorph(searching, query, onQueryChange, onOpen, onClose, label)

/** How far a [SearchMorph] has opened (0 = resting shape, 1 = search bar), for what's drawn around it. */
class SearchMorphProgress {
    var value by androidx.compose.runtime.mutableFloatStateOf(0f)
    /** How far (px) the bar has risen with the keyboard, so what fades out of it can ride along. */
    var lift by androidx.compose.runtime.mutableFloatStateOf(0f)
}

/**
 * The search bar that grows out of a resting pill: [PageSearch]'s short "Search" pill, or Home's tab pill (then
 * [restWidth]/[restHeight]/[restOffset] are the tab pill's, [hiddenAtRest] leaves the resting pill to [TabPill], and
 * [fadeContent] fades the search field in while the tabs fade out).
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun SearchMorph(
    searching: Boolean,
    query: String,
    onQueryChange: (String) -> Unit,
    onOpen: () -> Unit,
    onClose: () -> Unit,
    label: String = "Search",
    restWidth: androidx.compose.ui.unit.Dp = 240.dp,
    restHeight: androidx.compose.ui.unit.Dp = 52.dp,
    restOffset: androidx.compose.ui.unit.Dp = 4.dp,
    hiddenAtRest: Boolean = false,
    fadeContent: Boolean = false,
    progress: SearchMorphProgress? = null,
) {
    val c = OneUi.colors
    val density = androidx.compose.ui.platform.LocalDensity.current
    val focus = remember { FocusRequester() }
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current

    // Where the keyboard will end up (Samsung Keyboard reports its final height at once; its own slide runs ahead of
    // the live insets, so the bar heads straight for that height on its own curve rather than tracking the insets).
    val nav = WindowInsets.navigationBars.getBottom(density)
    val target = (WindowInsets.imeAnimationTarget.getBottom(density) - nav).coerceAtLeast(0)
    val keyboardUp = target > 0

    // The keyboard's height arrives ~150 ms after the tap; the pill waits for it so it widens and rises together
    // (or opens anyway after a moment, e.g. with a hardware keyboard).
    var waited by remember { mutableStateOf(false) }
    LaunchedEffect(searching) { waited = false; if (searching) { kotlinx.coroutines.delay(450); waited = true } }
    val open = searching && (keyboardUp || waited)

    // 0 = resting pill, 1 = open bar. Width, height and offset follow it; the lift follows the keyboard.
    val morph by androidx.compose.animation.core.animateFloatAsState(
        if (open) 1f else 0f, tween(if (open) 280 else 260, easing = OneUiEasing), label = "searchMorph",
    )
    val rise by androidx.compose.animation.core.animateFloatAsState(
        if (searching && keyboardUp) target.toFloat() else 0f, tween(if (keyboardUp) 260 else 240, easing = OneUiEasing), label = "searchLift",
    )
    // Never below the keyboard's current top edge: on the way down the keyboard slides slower than our curve, and the
    // bar used to slip behind it.
    val imeNow = (WindowInsets.ime.getBottom(density) - nav).coerceAtLeast(0)
    val lift = maxOf(rise, imeNow.toFloat())
    androidx.compose.runtime.SideEffect { progress?.value = morph; progress?.lift = lift }
    if (hiddenAtRest && !searching && morph == 0f) return

    // Opening: focus the field so the keyboard comes up. Closing: drop focus so it goes down with the pill.
    LaunchedEffect(searching) {
        if (searching) { runCatching { focus.requestFocus() }; keyboard?.show() } else focusManager.clearFocus()
    }
    // The first back press only hides the keyboard; with nothing typed, that ends the search.
    var hadIme by remember { mutableStateOf(false) }
    val latestQuery by androidx.compose.runtime.rememberUpdatedState(query)
    LaunchedEffect(keyboardUp, searching) {
        if (!searching) { hadIme = false; return@LaunchedEffect }
        if (keyboardUp) hadIme = true
        else if (hadIme) { hadIme = false; if (latestQuery.isEmpty()) onClose() }
    }

    val style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.5.sp, lineHeight = 21.sp, fontWeight = FontWeight.Bold, color = c.text)
    val hint = if (c.isDark) Color(0xFFA3A3A7) else Color(0xFF8A8A8F)
    androidx.compose.foundation.layout.BoxWithConstraints(
        Modifier.fillMaxWidth().padding(bottom = with(density) { lift.toDp() }),
        contentAlignment = Alignment.Center,
    ) {
        val width = androidx.compose.ui.unit.lerp(restWidth, maxWidth, morph)
        Row(
            Modifier
                .offset(y = androidx.compose.ui.unit.lerp(restOffset, (-6).dp, morph))
                .width(width)
                .height(androidx.compose.ui.unit.lerp(restHeight, 53.dp, morph))
                .shadow(
                    12.dp, CircleShape,
                    ambientColor = if (c.isDark) Color(0x99000000) else Color(0x33000000),
                    spotColor = if (c.isDark) Color(0xCC000000) else Color(0x40000000),
                )
                .clip(CircleShape)
                .then(
                    LocalHazeState.current?.let { haze ->
                        Modifier.hazeBlur(
                            dev.chrisbanes.haze.HazeInput.Sources(haze),
                            dev.chrisbanes.haze.blur.HazeBlurStyle { blurRadius(22.dp); noiseFactor(0f); backgroundColor(c.background) },
                        )
                    } ?: Modifier,
                )
                .border(0.5.dp, if (c.isDark) Color.White.copy(alpha = 0.05f) else Color.Black.copy(alpha = 0.05f), CircleShape)
                .drawBehind { drawRect(searchPillBase(c.isDark)) }
                .clickable(interactionSource = null, indication = if (searching) null else androidx.compose.foundation.LocalIndication.current) {
                    if (searching) runCatching { focus.requestFocus() } else onOpen()
                }
                .semantics { contentDescription = label }
                .padding(start = 17.dp, end = 8.dp)
                // Growing out of the tab pill, the search field fades in as the tabs fade out.
                .graphicsLayer { alpha = if (fadeContent) ((morph - 0.4f) / 0.6f).coerceIn(0f, 1f) else 1f },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(OneUiIcons.Search, null, tint = hint, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(6.dp))
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (query.isEmpty()) Text(label, style = style.copy(color = hint), maxLines = 1)
                BasicTextField(
                    query, onQueryChange,
                    enabled = searching,
                    singleLine = true,
                    textStyle = style,
                    cursorBrush = SolidColor(c.accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
            }
            if (searching && query.isNotEmpty()) Box(Modifier.size(40.dp).clip(CircleShape).clickable { onQueryChange("") }, contentAlignment = Alignment.Center) {
                Icon(OneUiIcons.Close, "Clear", tint = c.text, modifier = Modifier.size(22.dp))
            } else Spacer(Modifier.width(16.dp))
        }
    }
}

@Composable
private fun SearchFade(visible: Boolean, content: @Composable () -> Unit) {
    val alpha by androidx.compose.animation.core.animateFloatAsState(
        if (visible) 1f else 0f,
        if (visible) tween(220, delayMillis = 60) else tween(150),
        label = "searchFade",
    )
    if (!visible && alpha == 0f) return
    Box(Modifier.graphicsLayer { this.alpha = alpha; compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.ModulateAlpha }) { content() }
}

/** The short resting pill (My Files: ~245 × 52 dp, search icon and grey label). Frosted like the tab pill. */
@Composable
fun SearchPillButton(label: String, onClick: () -> Unit) {
    val c = OneUi.colors
    val base = searchPillBase(c.isDark)
    val hint = if (c.isDark) Color(0xFFA3A3A7) else Color(0xFF8A8A8F)
    Box(Modifier.fillMaxWidth().offset(y = 4.dp), contentAlignment = Alignment.Center) {
        Row(
            Modifier
                .width(240.dp)
                .height(52.dp)
                .shadow(
                    12.dp, CircleShape,
                    ambientColor = if (c.isDark) Color(0x99000000) else Color(0x33000000),
                    spotColor = if (c.isDark) Color(0xCC000000) else Color(0x40000000),
                )
                .clip(CircleShape)
                .then(
                    LocalHazeState.current?.let { haze ->
                        Modifier.hazeBlur(
                            dev.chrisbanes.haze.HazeInput.Sources(haze),
                            dev.chrisbanes.haze.blur.HazeBlurStyle { blurRadius(22.dp); noiseFactor(0f); backgroundColor(c.background) },
                        )
                    } ?: Modifier,
                )
                .border(0.5.dp, if (c.isDark) Color.White.copy(alpha = 0.05f) else Color.Black.copy(alpha = 0.05f), CircleShape)
                .drawBehind { drawRect(base) }
                .clickable(onClick = onClick)
                .semantics { contentDescription = label }
                // Laid out like the full search bar (icon and label from the left), just shorter.
                .padding(start = 17.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(OneUiIcons.Search, null, tint = hint, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(6.dp))
            Text(label, fontSize = 16.5.sp, fontWeight = FontWeight.Bold, color = hint, maxLines = 1)
        }
    }
}

/** The search pills' see-through grey (the tab pill's base), laid over the live blur. */
fun searchPillBase(dark: Boolean) = if (dark) Color(0xFF292929).copy(alpha = 0.66f) else Color.White.copy(alpha = 0.55f)
