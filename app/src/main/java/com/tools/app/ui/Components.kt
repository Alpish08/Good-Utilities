package com.tools.app.ui

import androidx.compose.ui.layout.layout
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.gestures.draggable
import androidx.compose.animation.core.animate
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.draw.blur
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tools.app.ui.theme.OneUi
import androidx.compose.runtime.SideEffect
import com.tools.app.ui.theme.OneUiEasing
import com.tools.app.ui.theme.pageBackground
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.collectIsPressedAsState
import kotlinx.coroutines.flow.filter
import kotlin.math.roundToInt

private val ToolbarHeight = 64.dp

/**
 * One UI 8.5: once the list scrolls under the pinned toolbar buttons, each gets a round chip behind it.
 * 0 = no chip (nothing underneath), 1 = full chip. Read lazily so scrolling doesn't recompose.
 */
private val LocalToolbarChip = androidx.compose.runtime.compositionLocalOf<() -> Float> { { 0f } }

/** The round chip behind a toolbar button, shown while content scrolls under it. */
private fun Modifier.toolbarChip(chip: () -> Float, color: Color, radius: Dp): Modifier = drawBehind {
    val a = chip()
    if (a > 0f) drawCircle(color, radius = radius.toPx(), alpha = a)
}

/** #222222, as opaque as the search pill. */
private fun chipColor(dark: Boolean) = if (dark) Color(0xFF222222) else Color(0xFFE6E6EB)
val CardMargin = 10.dp
/** How far lists fade out at the top and bottom edges. */
val FadeLength = 38.dp
private val RowPadding = 18.dp
/** Gap between card groups (each card contributes half). */
private val CardGap = 20.dp
private val DividerInset = 16.dp

/**
 * One UI page with a list body.
 * Pages get One UI's tall expanded app bar: a large title centred in the top third that shrinks
 * and glides into the toolbar as you scroll, snapping fully open or closed (like Settings). Sub
 * pages keep the back chevron in the toolbar. [expandedTitle] = false gives the compact bar only.
 * Lists fade out under the toolbar and above the bottom edge.
 */
@Composable
fun OneUiScaffold(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    bottomBar: (@Composable () -> Unit)? = null,
    listState: LazyListState = rememberLazyListState(),
    expandedTitle: Boolean = true,
    /** Extra room at the end of the list (e.g. for a floating tab bar the list scrolls under). */
    bottomInset: Dp = 0.dp,
    content: LazyListScope.() -> Unit,
) {
    val c = OneUi.colors
    val density = LocalDensity.current
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    // Every page can show the big title; expandedTitle = false only means it starts collapsed
    // (pull down at the top to expand it, like Samsung apps, for one-handed use).
    val expanded = true

    BoxWithConstraints(Modifier.fillMaxSize().pageBackground()) {
        val headerHeight = (maxHeight * 0.36f).coerceAtLeast(190.dp)
        val rangePx = with(density) { (headerHeight - ToolbarHeight).toPx() }
        val statusTopPx = with(density) { statusTop.toPx() }
        val fadePx = with(density) { FadeLength.toPx() }
        SideEffect { headerRanges[listState] = rangePx.roundToInt() }
        if (!expandedTitle) remember(listState) {
            if (listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0)
                listState.requestScrollToItem(0, rangePx.roundToInt())
        }

        /** How far the expanded header has scrolled away, 0..rangePx. */
        val headerScroll by remember(rangePx, expanded) {
            derivedStateOf {
                when {
                    !expanded -> rangePx
                    listState.firstVisibleItemIndex > 0 -> rangePx
                    else -> listState.firstVisibleItemScrollOffset.toFloat().coerceAtMost(rangePx)
                }
            }
        }
        // One UI 8.5: after the big title has folded away, the small title keeps scrolling up with the list
        // (instead of pinning) and the buttons stay, each on a round chip. How far past that point we are:
        val pastPx by remember(rangePx) {
            derivedStateOf {
                if (listState.firstVisibleItemIndex > 0) Float.MAX_VALUE
                else (listState.firstVisibleItemScrollOffset - rangePx).coerceAtLeast(0f)
            }
        }
        val chipFadePx = with(density) { 24.dp.toPx() }
        val chip = { (pastPx / chipFadePx).coerceIn(0f, 1f) }
        // Snap the header fully open or fully collapsed, but never fight the user at the end of a short list.
        if (expanded) LaunchedEffect(listState, rangePx) {
            // Also when the list changes under a still header (e.g. search results replacing a long list).
            snapshotFlow {
                val off = listState.firstVisibleItemScrollOffset
                !listState.isScrollInProgress && listState.firstVisibleItemIndex == 0 && off > 0 && off < rangePx
            }.collect { mid ->
                if (!mid) return@collect
                kotlinx.coroutines.delay(120) // let the list and its bottom filler settle first
                val off = listState.firstVisibleItemScrollOffset
                if (!listState.isScrollInProgress && listState.firstVisibleItemIndex == 0 && off > 0 && off < rangePx && listState.canScrollForward) {
                    listState.animateScrollToItem(0, if (off > rangePx / 2) rangePx.roundToInt() else 0)
                }
            }
        }
        // 0..1 strength of the top / bottom fades (only when there is content to fade).
        val topFade by remember(rangePx, expanded) {
            derivedStateOf {
                val past = if (listState.firstVisibleItemIndex > 0) Float.MAX_VALUE
                else listState.firstVisibleItemScrollOffset - (if (expanded) rangePx else 0f)
                (past / fadePx).coerceIn(0f, 1f)
            }
        }
        val bottomFade by animateFloatAsState(if (listState.canScrollForward) 1f else 0f, label = "bottomFade")

        var bottomBarPx by remember { mutableIntStateOf(0) }
        val bottomPad = (if (bottomBar != null) with(density) { bottomBarPx.toDp() } else navBottom) + bottomInset
        // Short lists get invisible bottom room so the header can always collapse all the way.
        val viewportPx = constraints.maxHeight.toFloat()
        var fillerPx by remember { mutableFloatStateOf(0f) }
        // When the list's contents change (e.g. search results → full list), drop the old filler first, so a long
        // list can't keep the short list's extra room and scroll past its end.
        LaunchedEffect(listState) {
            snapshotFlow { listState.layoutInfo.totalItemsCount }.collect { fillerPx = 0f }
        }
        if (expanded) LaunchedEffect(listState, rangePx, viewportPx) {
            snapshotFlow {
                val info = listState.layoutInfo
                val last = info.visibleItemsInfo.lastOrNull()
                if (listState.firstVisibleItemIndex == 0 && last != null && last.index == info.totalItemsCount - 1)
                    (last.offset + last.size + listState.firstVisibleItemScrollOffset).toFloat()
                else null
            }.collect { contentPx ->
                if (contentPx != null) {
                    val basePad = with(density) { (bottomPad + 20.dp).toPx() }
                    fillerPx = (viewportPx + rangePx - contentPx - basePad).coerceAtLeast(0f)
                }
            }
        }
        // Fade at the screen edge, not above the inset: the list keeps going under a floating bar.
        val bottomCutPx = with(density) { (bottomPad - bottomInset).toPx() }
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                .drawWithContent {
                    drawContent()
                    val h = size.height
                    fun f(y: Float) = (y / h).coerceIn(0f, 1f)
                    val solid = Color.Black
                    drawRect(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            // The list runs right up under the status bar now, fading out just below it.
                            f(statusTopPx) to solid.copy(alpha = 1f - topFade),
                            f(statusTopPx + fadePx * 1.6f) to solid,
                            // Under a floating bar (search pill etc.) the list keeps going, like Reminder and My Files:
                            // rows stay visible beside and below the pill, fading out slowly toward the bottom edge.
                            *(if (bottomBar != null) arrayOf(
                                f(h - bottomCutPx - fadePx * 3.5f) to solid,
                                f(h - bottomCutPx - fadePx * 1.5f) to solid.copy(alpha = 0.85f),
                                f(h - bottomCutPx) to solid.copy(alpha = 0.6f),
                                f(h - bottomCutPx * 0.5f) to solid.copy(alpha = 0.3f),
                                1f to solid.copy(alpha = 0f),
                            ) else arrayOf(
                                f(h - bottomCutPx - fadePx * 1.4f) to solid,
                                f(h - bottomCutPx) to solid.copy(alpha = 1f - bottomFade),
                                1f to solid.copy(alpha = 1f - bottomFade),
                            )),
                        ),
                        blendMode = BlendMode.DstIn,
                    )
                },
            contentPadding = PaddingValues(
                top = if (expanded) 0.dp else statusTop + ToolbarHeight,
                bottom = bottomPad + 20.dp + with(density) { fillerPx.toDp() },
            ),
        ) {
            if (expanded) item(key = "__header") {
                Spacer(Modifier.fillMaxWidth().height(statusTop + headerHeight))
            }
            content()
        }

        // Like Samsung apps: while the title is expanded, the back / action buttons sit at the bottom of the
        // header, just above the first card, and ride up with the list until they pin to the top.
        androidx.compose.runtime.CompositionLocalProvider(LocalToolbarChip provides chip) {
            Toolbar(
                title = if (expanded) "" else title, onBack = onBack, actions = actions,
                modifier = if (expanded) Modifier.graphicsLayer { translationY = rangePx - headerScroll } else Modifier,
            )
        }

        val widthPx = constraints.maxWidth.toFloat()
        val toolbarHalfPx = with(density) { ToolbarHeight.toPx() } / 2f
        // The collapsed title scrolls away with the list, fading before it reaches the status bar.
        Box(
            Modifier.fillMaxSize().graphicsLayer {
                val p = pastPx.coerceAtMost(toolbarHalfPx * 4f)
                translationY = -p
                alpha = (1f - p / toolbarHalfPx).coerceIn(0f, 1f)
            },
        ) {
            ExpandingTitle(
                title, subtitle, hasBack = onBack != null, widthPx = widthPx,
                t = { headerScroll / rangePx },
                // Expanded: centred in the space above the buttons. Collapsed: on the toolbar's centre line.
                centerY = { lerpF(statusTopPx + rangePx / 2f + toolbarHalfPx * 0.35f, statusTopPx + toolbarHalfPx, headerScroll / rangePx) },
            )
        }

        if (bottomBar != null) BottomBarHost(Modifier.align(Alignment.BottomCenter).onSizeChanged { bottomBarPx = it.height }, bottomBar)
    }
}

private val headerRanges = java.util.WeakHashMap<LazyListState, Int>()

/** Scrolls a [OneUiScaffold] list to the point where its big title has just folded into the toolbar. */
suspend fun LazyListState.collapseHeader() {
    val range = headerRanges[this] ?: return
    animateScrollToItem(0, range)
}

/**
 * One UI page whose body is not a list (image editors etc.). It opens with the compact toolbar; drag the
 * toolbar down (or pull past the top of scrolling content) to expand the big title for one-handed use.
 * It snaps fully open or closed.
 */
@Composable
fun ToolPage(
    title: String,
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = OneUi.colors
    val density = LocalDensity.current
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    BoxWithConstraints(Modifier.fillMaxSize().pageBackground()) {
        val rangePx = with(density) { ((maxHeight * 0.36f).coerceAtLeast(190.dp) - ToolbarHeight).toPx() }
        val statusTopPx = with(density) { statusTop.toPx() }
        val toolbarHalfPx = with(density) { ToolbarHeight.toPx() } / 2f
        var extra by remember { mutableFloatStateOf(0f) } // 0 = collapsed … rangePx = expanded
        val scope = rememberCoroutineScope()
        fun settle(velocity: Float) {
            val target = when {
                velocity > 900f -> rangePx
                velocity < -900f -> 0f
                else -> if (extra > rangePx / 2f) rangePx else 0f
            }
            scope.launch { animate(extra, target, animationSpec = tween(300, easing = OneUiEasing)) { v, _ -> extra = v } }
        }
        // A page without a title (About) has nothing to expand.
        val expandable = title.isNotEmpty()
        val nested = remember(rangePx) {
            object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
                override fun onPreScroll(available: Offset, source: androidx.compose.ui.input.nestedscroll.NestedScrollSource): Offset {
                    // Scrolling content up first folds the header away.
                    if (available.y < 0f && extra > 0f) { val used = maxOf(available.y, -extra); extra += used; return Offset(0f, used) }
                    return Offset.Zero
                }
                override fun onPostScroll(consumed: Offset, available: Offset, source: androidx.compose.ui.input.nestedscroll.NestedScrollSource): Offset {
                    // Pulling past the top of the content opens it.
                    if (expandable && available.y > 0f && source == androidx.compose.ui.input.nestedscroll.NestedScrollSource.UserInput) {
                        val used = minOf(available.y, rangePx - extra); extra += used; return Offset(0f, used)
                    }
                    return Offset.Zero
                }
                override suspend fun onPreFling(available: androidx.compose.ui.unit.Velocity): androidx.compose.ui.unit.Velocity {
                    if (extra > 0.5f && extra < rangePx - 0.5f) { settle(available.y); return available }
                    return androidx.compose.ui.unit.Velocity.Zero
                }
            }
        }
        val drag = androidx.compose.foundation.gestures.rememberDraggableState { d -> if (expandable) extra = (extra + d).coerceIn(0f, rangePx) }
        // A drag anywhere on the page that nothing inside claims (sliders, canvases and scroll areas
        // consume their own) pulls the header open or closed.
        Column(Modifier.fillMaxSize().draggable(drag, androidx.compose.foundation.gestures.Orientation.Vertical, onDragStopped = { v -> settle(v) })) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(statusTop + ToolbarHeight + with(density) { extra.toDp() }),
            ) {
                Toolbar("", onBack, actions, Modifier.align(Alignment.BottomStart))
            }
            // While expanded, the page keeps its normal height and slides down (running off the bottom)
            // instead of being squeezed into the space that's left.
            Column(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clipToBounds()
                    .layout { m, cs ->
                        val h = cs.maxHeight + extra.roundToInt()
                        val p = m.measure(cs.copy(minHeight = h, maxHeight = h))
                        layout(cs.maxWidth, cs.maxHeight) { p.place(0, 0) }
                    }
                    .navigationBarsPadding()
                    .nestedScroll(nested),
                content = content,
            )
        }
        ExpandingTitle(
            title, null, hasBack = true, widthPx = constraints.maxWidth.toFloat(),
            t = { 1f - extra / rangePx },
            centerY = { lerpF(statusTopPx + rangePx / 2f + toolbarHalfPx * 0.35f, statusTopPx + toolbarHalfPx, 1f - extra / rangePx) },
        )
    }
}

private fun lerpF(a: Float, b: Float, t: Float) = a + (b - a) * t

/**
 * The page title that travels from the centre of the expanded header ([t] = 0) to the toolbar's
 * top-left ([t] = 1), shrinking as it goes. Long titles shrink to fit.
 */
@Composable
private fun ExpandingTitle(title: String, subtitle: String?, hasBack: Boolean, widthPx: Float, t: () -> Float, centerY: () -> Float) {
    val c = OneUi.colors
    val density = LocalDensity.current
    val subtitleGapPx = with(density) { 18.dp.toPx() }
    var titleH by remember { mutableIntStateOf(0) }
    val lift = { if (subtitle != null) subtitleGapPx * (1f - t()) else 0f }
    MovingTitle(title, hasBack, widthPx, t, { centerY() - lift() }) { titleH = it }
    if (subtitle != null) Text(
        subtitle, fontSize = 15.sp, color = c.textSecondary, maxLines = 1,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = (1f - t() * 3f).coerceIn(0f, 1f)
                translationY = centerY() + titleH / 2f - subtitleGapPx + 6.dp.toPx()
            },
    )
}

@Composable
private fun MovingTitle(
    text: String, hasBack: Boolean, widthPx: Float, t: () -> Float, centerY: () -> Float, onHeight: (Int) -> Unit = {},
) {
    val c = OneUi.colors
    val density = LocalDensity.current
    // Collapsed, the title sits where the toolbar title would: after the back arrow if there is one.
    val startPx = with(density) { (if (hasBack) 58.dp else 26.dp).toPx() }
    val collapsedScale = 21f / 35f
    var size by remember { mutableStateOf(IntSize.Zero) }
    Text(
        text, fontSize = 35.sp, lineHeight = 41.sp, fontWeight = FontWeight.ExtraBold, color = c.text,
        maxLines = 1,
        modifier = Modifier
            .wrapContentSize(unbounded = true, align = Alignment.TopStart)
            .onSizeChanged { size = it; onHeight(it.height) }
            .graphicsLayer {
                val tt = t()
                val w = size.width.coerceAtLeast(1).toFloat()
                val fitExpanded = ((widthPx - 48.dp.toPx()) / w).coerceAtMost(1f)
                val fitCollapsed = ((widthPx - startPx - 64.dp.toPx()) / w).coerceAtMost(collapsedScale)
                val scale = lerpF(fitExpanded, fitCollapsed, tt)
                val cx = lerpF(widthPx / 2f, startPx + size.width * fitCollapsed / 2f, tt)
                scaleX = scale; scaleY = scale
                translationX = cx - size.width / 2f
                translationY = centerY() - size.height / 2f
            },
    )
}

@Composable
private fun Toolbar(
    title: String,
    onBack: (() -> Unit)?,
    actions: @Composable RowScope.() -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = OneUi.colors
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    Row(
        modifier
            .fillMaxWidth()
            .padding(top = statusTop)
            .height(ToolbarHeight)
            .padding(start = 4.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            Box(
                Modifier.size(52.dp).toolbarChip(LocalToolbarChip.current, chipColor(c.isDark), 23.dp)
                    .clip(CircleShape).clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(OneUiIcons.ArrowBack, "Navigate up", tint = c.text, modifier = Modifier.size(24.dp))
            }
        } else Spacer(Modifier.width(20.dp))
        Text(
            title,
            fontSize = 21.sp, fontWeight = FontWeight.Bold, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(start = 2.dp),
        )
        // One UI 8.5 (Reminder): the action buttons share one capsule chip once content scrolls under them.
        val chip = LocalToolbarChip.current
        val chipCol = chipColor(c.isDark)
        Row(
            Modifier
                .height(48.dp)
                .drawBehind {
                    val a = chip()
                    if (a > 0f) drawRoundRect(chipCol, cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2f), alpha = a)
                }
                .padding(horizontal = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            androidx.compose.runtime.CompositionLocalProvider(LocalToolbarChip provides { 0f }, LocalToolbarSlot provides 38.dp) {
                actions()
            }
        }
    }
}

/** Width of a toolbar button: narrower inside the shared actions capsule. */
private val LocalToolbarSlot = androidx.compose.runtime.compositionLocalOf { 48.dp }

@Composable
fun BottomBarHost(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val c = OneUi.colors
    Box(
        modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 22.dp, bottom = 12.dp),
    ) { content() }
}

@Composable
fun RoundCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .padding(horizontal = CardMargin, vertical = CardGap / 2)
            .fillMaxWidth()
            .clip(OneUi.CardShape)
            .background(OneUi.colors.card),
        content = content,
    )
}

fun LazyListScope.card(key: Any, content: @Composable ColumnScope.() -> Unit) =
    item(key = key) { RoundCard(content = content) }

fun LazyListScope.sectionLabel(text: String, key: Any = "label-$text", trailing: (@Composable () -> Unit)? = null) =
    item(key = key) {
        Row(
            Modifier.fillMaxWidth().padding(start = 24.dp, end = 20.dp, top = 14.dp, bottom = 0.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = OneUi.colors.textSecondary, modifier = Modifier.weight(1f))
            trailing?.invoke()
        }
    }

/** Explanatory text placed between cards, as Samsung does under its master switches. */
fun LazyListScope.description(text: String, key: Any = "desc-${text.hashCode()}") = item(key = key) {
    Text(
        text, fontSize = 14.sp, lineHeight = 20.sp, color = OneUi.colors.textSecondary,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp),
    )
}

/** A list of rows rendered as one rounded card with inset dividers: the One UI grouped list. */
fun <T> LazyListScope.cardList(
    items: List<T>,
    key: (T) -> Any,
    dividerStart: Dp = DividerInset,
    row: @Composable (item: T) -> Unit,
) = itemsIndexed(items, key = { _, it -> key(it) }) { i, item ->
    val first = i == 0
    val last = i == items.lastIndex
    val r = OneUi.CardRadius
    val shape = RoundedCornerShape(
        topStart = if (first) r else 0.dp, topEnd = if (first) r else 0.dp,
        bottomStart = if (last) r else 0.dp, bottomEnd = if (last) r else 0.dp,
    )
    Column(
        Modifier
            .padding(start = CardMargin, end = CardMargin, top = if (first) CardGap / 2 else 0.dp, bottom = if (last) CardGap / 2 else 0.dp)
            .clip(shape)
            .background(OneUi.colors.card),
    ) {
        row(item)
        if (!last) HorizontalDivider(Modifier.padding(start = dividerStart, end = DividerInset), 1.dp, OneUi.colors.divider)
    }
}

/** Samsung's row divider, for rows placed by hand inside one [card] (cardList adds these itself). */
@Composable
fun CardDivider(start: Dp = 16.dp) =
    HorizontalDivider(Modifier.padding(start = start, end = 16.dp), 1.dp, OneUi.colors.divider)

/** Divider start for rows that have a 28dp icon circle. */
val IconRowDivider = 62.dp

@Composable
fun ListRow(
    title: String,
    modifier: Modifier = Modifier,
    summary: String? = null,
    summaryColor: Color? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    dimmed: Boolean = false,
) {
    val c = OneUi.colors
    Row(
        modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(enabled = enabled, onClick = onClick) else Modifier)
            .heightIn(min = 56.dp)
            .padding(horizontal = RowPadding, vertical = 15.dp)
            .graphicsLayer { alpha = if (dimmed || !enabled) 0.45f else 1f },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(16.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.5.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold, color = c.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (summary != null) {
                Text(
                    summary, fontSize = 13.sp, lineHeight = 17.sp, color = summaryColor ?: c.textSecondary,
                    maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(12.dp))
            trailing()
        }
    }
}

/** Filled circle with a white glyph, as in Samsung Settings' main list. */
@Composable
fun ColorIcon(icon: ImageVector, color: Color, size: Dp = 28.dp, glyph: Float = 0.62f) {
    Box(Modifier.size(size).background(color, CircleShape), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(size * glyph))
    }
}

@Composable
fun IconBadge(icon: ImageVector) {
    val c = OneUi.colors
    Box(Modifier.size(40.dp).background(c.accentContainer, CircleShape), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = c.accent, modifier = Modifier.size(22.dp))
    }
}

/** The "On / Off" card at the top of Samsung feature pages (e.g. Battery protection). */
@Composable
fun MasterSwitchCard(checked: Boolean, onCheckedChange: (Boolean) -> Unit, enabled: Boolean = true, onText: String = "On", offText: String = "Off") {
    val c = OneUi.colors
    Row(
        Modifier
            .padding(horizontal = CardMargin, vertical = CardGap / 2)
            .fillMaxWidth()
            .clip(OneUi.CardShape)
            .background(c.card)
            .toggleable(checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
            .heightIn(min = 68.dp)
            .padding(start = RowPadding, end = 22.dp, top = 14.dp, bottom = 14.dp)
            .graphicsLayer { alpha = if (enabled) 1f else 0.45f },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            if (checked) onText else offText,
            fontSize = 18.sp, fontWeight = FontWeight.Bold,
            color = if (checked) c.accent else c.text, modifier = Modifier.weight(1f),
        )
        OneUiSwitch(checked)
    }
}

@Composable
fun OneUiSwitch(checked: Boolean, modifier: Modifier = Modifier, onCheckedChange: ((Boolean) -> Unit)? = null) {
    val c = OneUi.colors
    // Samsung: 32 x 20 dp track, 16 dp white thumb inset 2 dp.
    val pos = animateFloatAsState(if (checked) 1f else 0f, tween(240, easing = OneUiEasing), label = "switch")
    val track by animateColorAsState(if (checked) c.accent else c.switchOff, tween(200), label = "track")
    Box(
        modifier
            .size(width = 32.dp, height = 20.dp)
            .clip(CircleShape)
            .background(track)
            .then(
                if (onCheckedChange != null) Modifier.toggleable(checked, role = Role.Switch, onValueChange = onCheckedChange)
                else Modifier
            )
            .padding(2.dp),
    ) {
        Box(
            Modifier
                .offset { IntOffset((12.dp.toPx() * pos.value).roundToInt(), 0) }
                .size(16.dp)
                .background(Color.White, CircleShape),
        )
    }
}

@Composable
fun RoundCheck(checked: Boolean, modifier: Modifier = Modifier) {
    val c = OneUi.colors
    val bg by animateColorAsState(if (checked) c.accent else Color.Transparent, tween(180), label = "check")
    Box(
        modifier
            .size(20.dp)
            .clip(CircleShape)
            .background(bg)
            .then(if (!checked) Modifier.border(1.3.dp, c.textSecondary.copy(alpha = 0.7f), CircleShape) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) Icon(OneUiIcons.Check, null, tint = c.onAccent, modifier = Modifier.size(14.dp))
    }
}

@Composable
fun RadioDot(selected: Boolean) {
    val c = OneUi.colors
    val ring by animateColorAsState(if (selected) c.accent else c.textSecondary.copy(alpha = 0.7f), tween(180), label = "radioRing")
    Box(
        Modifier.size(20.dp).border(if (selected) 1.6.dp else 1.3.dp, ring, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Box(Modifier.size(11.dp).background(c.accent, CircleShape))
    }
}

@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tonal: Boolean = false,
    icon: ImageVector? = null,
    height: Dp = 52.dp,
) {
    val c = OneUi.colors
    val bg by animateColorAsState(if (tonal) c.tonal else c.accent, label = "pillBg")
    val fg = if (tonal) c.text else c.onAccent
    val source = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, tween(if (pressed) 90 else 260, easing = OneUiEasing), label = "pillScale")
    Row(
        modifier
            .height(height)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(CircleShape)
            .background(bg)
            .clickable(source, LocalIndication.current, enabled = enabled, onClick = onClick)
            .graphicsLayer { alpha = if (enabled) 1f else 0.4f }
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = fg, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, fontSize = 15.5.sp, fontWeight = FontWeight.SemiBold, color = fg, maxLines = 1)
    }
}

/** Small rounded chip, like Samsung's date / filter pills. */
@Composable
fun Chip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, horizontalPadding: Dp = 16.dp) {
    val c = OneUi.colors
    val bg by animateColorAsState(if (selected) c.accent else c.tonal, tween(220), label = "chip")
    Box(
        modifier
            .height(36.dp)
            .clip(CircleShape)
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = horizontalPadding),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = if (selected) c.onAccent else c.text, maxLines = 1, softWrap = false)
    }
}

@Composable
fun ToolbarIcon(icon: ImageVector, description: String, onClick: () -> Unit) {
    Box(
        Modifier.size(width = LocalToolbarSlot.current, height = 48.dp).toolbarChip(LocalToolbarChip.current, chipColor(OneUi.colors.isDark), 23.dp)
            .clip(CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, description, tint = OneUi.colors.text, modifier = Modifier.size(24.dp))
    }
}

@Composable
fun ThinProgress(progress: Float, modifier: Modifier = Modifier, color: Color = OneUi.colors.accent, height: Dp = 4.dp) {
    val c = OneUi.colors
    val p by animateFloatAsState(progress, label = "progress")
    Box(modifier.fillMaxWidth().height(height).clip(CircleShape).background(c.sliderTrack)) {
        Box(Modifier.fillMaxWidth(p.coerceIn(0f, 1f)).fillMaxHeight().clip(CircleShape).background(color))
    }
}

private val SliderIdleTrack = 14.dp
private val SliderHeldTrack = 22.dp
private val SliderThumb = 22.dp
private val SliderRing = 2.dp
private val SliderTouchHeight = 40.dp

/** Draws the morphing One UI 8 track: [fillStart]..[fillEnd] (px) accent fill clipped to the rounded track. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSeekTrack(
    trackH: Float, trackColor: Color, fillColor: Color, fillStart: Float, fillEnd: Float,
) {
    val top = (size.height - trackH) / 2f
    val shape = androidx.compose.ui.graphics.Path().apply {
        addRoundRect(
            androidx.compose.ui.geometry.RoundRect(
                androidx.compose.ui.geometry.Rect(0f, top, size.width, top + trackH),
                androidx.compose.ui.geometry.CornerRadius(trackH / 2f),
            )
        )
    }
    drawPath(shape, trackColor)
    if (fillEnd > fillStart) clipPath(shape) {
        drawRect(fillColor, androidx.compose.ui.geometry.Offset(fillStart, top), androidx.compose.ui.geometry.Size(fillEnd - fillStart, trackH))
    }
}

/** Ring thumb: page-background fill with an accent ring; [scale] 0 collapses it away. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSeekThumb(cx: Float, scale: Float, fill: Color, ring: Color) {
    if (scale <= 0.01f) return
    val r = SliderThumb.toPx() / 2f * scale
    val w = SliderRing.toPx() * scale
    drawCircle(fill, r, androidx.compose.ui.geometry.Offset(cx, size.height / 2f))
    drawCircle(ring, r - w / 2f, androidx.compose.ui.geometry.Offset(cx, size.height / 2f), style = androidx.compose.ui.graphics.drawscope.Stroke(w))
}

/**
 * One UI 8 morphing seek bar. Idle: 14 dp track with a ringed 22 dp thumb. While touched the thumb
 * collapses and the track thickens to 22 dp, so the fill reads as a solid level bar.
 */
@Composable
fun OneUiSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    onValueChangeFinished: () -> Unit = {},
) {
    val c = OneUi.colors
    var widthPx by remember { mutableIntStateOf(1) }
    val onChange by rememberUpdatedState(onValueChange)
    val onFinish by rememberUpdatedState(onValueChangeFinished)
    val span = valueRange.endInclusive - valueRange.start
    val fraction = ((value - valueRange.start) / span).coerceIn(0f, 1f)
    var held by remember { mutableStateOf(false) }
    val morph = animateFloatAsState(if (held) 1f else 0f, tween(200, easing = OneUiEasing), label = "morph")
    Box(
        modifier
            .fillMaxWidth()
            .height(SliderTouchHeight)
            .onSizeChanged { widthPx = it.width }
            .pointerInput(valueRange) {
                val thumbPx = SliderThumb.toPx()
                fun toValue(x: Float) =
                    valueRange.start + ((x - thumbPx / 2) / (widthPx - thumbPx)).coerceIn(0f, 1f) * span
                awaitEachGesture {
                    val down = awaitFirstDown()
                    held = true
                    onChange(toValue(down.position.x))
                    horizontalDrag(down.id) { change ->
                        change.consume()
                        onChange(toValue(change.position.x))
                    }
                    held = false
                    onFinish()
                }
            }
            .drawBehind {
                val m = morph.value
                val trackH = (SliderIdleTrack.toPx() + (SliderHeldTrack - SliderIdleTrack).toPx() * m)
                val half = SliderThumb.toPx() / 2f
                val cx = half + (size.width - 2 * half) * fraction
                drawSeekTrack(trackH, c.sliderTrack, c.accent, 0f, cx)
                drawSeekThumb(cx, 1f - m, if (c.isDark) c.background else Color.White, c.accent)
            },
    )
}

/** Two- or three-way pill switcher; the selection pill glides between options like One UI's tabs. */
@Composable
fun SegmentedControl(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val c = OneUi.colors
    BoxWithConstraints(modifier.fillMaxWidth().height(42.dp).clip(CircleShape).background(c.tonal).padding(3.dp)) {
        val segment = maxWidth / options.size
        val x by androidx.compose.animation.core.animateDpAsState(segment * selected, tween(320, easing = OneUiEasing), label = "segment")
        Box(
            Modifier.offset(x = x).width(segment).fillMaxHeight()
                .shadow(if (c.isDark) 0.dp else 1.dp, CircleShape)
                .background(if (c.isDark) Color(0xFF49494B) else Color.White, CircleShape),
        )
        Row(Modifier.fillMaxSize()) {
            options.forEachIndexed { i, label ->
                val sel = i == selected
                // No press highlight of its own: the sliding pill is the only highlight (two looked doubled).
                // A pressed label just dims, like Samsung's segmented buttons.
                val press = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                val pressed by press.collectIsPressedAsState()
                val dim by androidx.compose.animation.core.animateFloatAsState(if (pressed) 0.55f else 1f, tween(120), label = "press")
                Box(
                    Modifier.weight(1f).fillMaxHeight().clickable(press, indication = null) { onSelect(i) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label, fontSize = 14.5.sp, fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Medium,
                        color = if (sel) c.text else c.textSecondary, modifier = Modifier.graphicsLayer { alpha = dim },
                    )
                }
            }
        }
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, summary: String, action: String, onAction: () -> Unit, modifier: Modifier = Modifier) =
    EmptyState(icon, null, title, summary, action, onAction, modifier)

/** Empty state headed by the tool's own Home icon ([color] = its Home circle color). */
@Composable
fun EmptyState(
    icon: ImageVector, color: Color?, title: String, summary: String, action: String, onAction: () -> Unit,
    modifier: Modifier = Modifier,
    /** An optional second choice, shown as a matching tonal button beside the main one. */
    secondary: Pair<String, () -> Unit>? = null,
) {
    val c = OneUi.colors
    Column(modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        // Tool pages don't repeat the Home screen's coloured tool icons; only plain icons (errors) show here.
        if (color == null) Box(Modifier.size(84.dp).background(c.accentContainer, CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = c.accent, modifier = Modifier.size(40.dp))
        }
        Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = c.text, modifier = Modifier.padding(top = 20.dp), textAlign = TextAlign.Center)
        Text(summary, fontSize = 15.sp, color = c.textSecondary, modifier = Modifier.padding(top = 6.dp, bottom = 24.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        if (secondary == null) PillButton(action, onClick = onAction)
        else Column(Modifier.fillMaxWidth(0.72f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            PillButton(action, onClick = onAction, modifier = Modifier.fillMaxWidth())
            PillButton(secondary.first, onClick = secondary.second, tonal = true, modifier = Modifier.fillMaxWidth())
        }
    }
}

fun formatDuration(seconds: Int): String {
    val h = seconds / 3600; val m = (seconds % 3600) / 60; val s = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

fun formatTotal(seconds: Int): String {
    val h = seconds / 3600; val m = (seconds % 3600) / 60
    return if (h > 0) "$h h $m min" else if (m > 0) "$m min" else "$seconds sec"
}

/**
 * One UI "More options" button. The menu is a frosted card that grows from the top-right corner;
 * each item is its own rounded pill. Samsung keeps cross-window blur to itself, so the frost comes
 * from a snapshot of the screen taken as the menu opens, blurred under a translucent tint (the page
 * behind can't change while the menu is open, so it looks identical to a live blur).
 */
@Composable
fun OverflowMenu(items: List<Pair<String, () -> Unit>>) {
    val c = OneUi.colors
    var open by remember { mutableStateOf(false) }
    var backdrop by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    var anchor by remember { mutableStateOf(androidx.compose.ui.geometry.Rect.Zero) }
    val view = LocalView.current
    Box(Modifier.onGloballyPositioned { anchor = it.boundsInWindow() }) {
        ToolbarIcon(OneUiIcons.More, "More options") {
            val window = view.context.findActivityWindow()
            if (window == null || view.width == 0 || android.os.Build.VERSION.SDK_INT < 31) { backdrop = null; open = true; return@ToolbarIcon }
            val bmp = android.graphics.Bitmap.createBitmap(view.width, view.height, android.graphics.Bitmap.Config.ARGB_8888)
            android.view.PixelCopy.request(window, bmp, { r ->
                backdrop = if (r == android.view.PixelCopy.SUCCESS) bmp.asImageBitmap() else null
                open = true
            }, android.os.Handler(android.os.Looper.getMainLooper()))
        }
    }
    if (!open) return
    val density = LocalDensity.current
    val shape = RoundedCornerShape(24.dp)
    androidx.compose.ui.window.Dialog(
        onDismissRequest = { open = false; backdrop = null },
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        val window = (LocalView.current.parent as? androidx.compose.ui.window.DialogWindowProvider)?.window
        SideEffect {
            window ?: return@SideEffect
            window.setGravity(android.view.Gravity.TOP or android.view.Gravity.END)
            window.setLayout(android.view.ViewGroup.LayoutParams.WRAP_CONTENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT)
            window.attributes = window.attributes.apply {
                // anchor is in the activity window, which is edge to edge (screen coordinates); 10dp is shadow room.
                x = (view.width - anchor.right).toInt() - with(density) { 4.dp.roundToPx() }
                y = anchor.top.toInt() - with(density) { 6.dp.roundToPx() }
                flags = flags or android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                dimAmount = 0f
            }
            window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
            window.setWindowAnimations(com.tools.app.R.style.OneUiMenuAnimation)
        }
        var onScreen by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
        val snap = backdrop
        Box(
            Modifier
                .padding(10.dp) // room for the shadow
                .shadow(12.dp, shape, ambientColor = Color.Black.copy(alpha = 0.3f))
                .clip(shape)
                .onGloballyPositioned { onScreen = it.positionOnScreen() },
        ) {
            if (snap != null) {
                // The page behind this card, blurred; the card's own screen position lines the snapshot up.
                androidx.compose.foundation.Canvas(
                    Modifier.matchParentSize().blur(28.dp, androidx.compose.ui.draw.BlurredEdgeTreatment.Rectangle),
                ) {
                    val origin = view.locationOnScreen()
                    drawImage(snap, topLeft = androidx.compose.ui.geometry.Offset(origin.x - onScreen.x, origin.y - onScreen.y))
                }
            }
            Column(
                Modifier
                    .background(if (snap != null) c.popup.copy(alpha = if (c.isDark) 0.74f else 0.8f) else c.popup)
                    .padding(5.dp)
                    .widthIn(min = 150.dp)
                    .width(androidx.compose.foundation.layout.IntrinsicSize.Max),
            ) {
                for ((label, action) in items) {
                    Text(
                        label, fontSize = 15.5.sp, fontWeight = FontWeight.Medium, color = c.text,
                        maxLines = 1,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 51.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .clickable { open = false; backdrop = null; action() }
                            .padding(horizontal = 22.dp)
                            .wrapContentHeight(Alignment.CenterVertically),
                    )
                }
            }
        }
    }
}

private fun android.content.Context.findActivityWindow(): android.view.Window? {
    var c: android.content.Context? = this
    while (c is android.content.ContextWrapper) { if (c is android.app.Activity) return c.window; c = c.baseContext }
    return null
}

private fun android.view.View.locationOnScreen(): androidx.compose.ui.geometry.Offset {
    val a = IntArray(2); getLocationOnScreen(a); return androidx.compose.ui.geometry.Offset(a[0].toFloat(), a[1].toFloat())
}

/**
 * One UI range seek bar with two ringed thumbs. Values are fractions 0..1; [marker] draws a thin
 * playhead. The thumb nearest to the touch is the one that moves (and collapses while held).
 */
@Composable
fun OneUiRangeSlider(
    start: Float,
    end: Float,
    onChange: (start: Float, end: Float) -> Unit,
    modifier: Modifier = Modifier,
    minGap: Float = 0.01f,
    marker: Float? = null,
    onChangeFinished: () -> Unit = {},
) {
    val c = OneUi.colors
    var widthPx by remember { mutableIntStateOf(1) }
    val latestStart by rememberUpdatedState(start)
    val latestEnd by rememberUpdatedState(end)
    val change by rememberUpdatedState(onChange)
    val finish by rememberUpdatedState(onChangeFinished)
    var active by remember { mutableIntStateOf(-1) }
    val morph = animateFloatAsState(if (active >= 0) 1f else 0f, tween(200, easing = OneUiEasing), label = "morph")
    val t0 = animateFloatAsState(if (active == 0) 0f else 1f, tween(200, easing = OneUiEasing), label = "t0")
    val t1 = animateFloatAsState(if (active == 1) 0f else 1f, tween(200, easing = OneUiEasing), label = "t1")
    Box(
        modifier
            .fillMaxWidth()
            .height(SliderTouchHeight)
            .onSizeChanged { widthPx = it.width }
            .pointerInput(Unit) {
                val thumbPx = SliderThumb.toPx()
                fun toFraction(x: Float) = ((x - thumbPx / 2) / (widthPx - thumbPx)).coerceIn(0f, 1f)
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val f0 = toFraction(down.position.x)
                    val moveStart = kotlin.math.abs(f0 - latestStart) < kotlin.math.abs(f0 - latestEnd) ||
                        (latestStart == latestEnd && f0 < latestStart)
                    fun apply(f: Float) {
                        if (moveStart) change(f.coerceAtMost(latestEnd - minGap).coerceAtLeast(0f), latestEnd)
                        else change(latestStart, f.coerceAtLeast(latestStart + minGap).coerceAtMost(1f))
                    }
                    active = if (moveStart) 0 else 1
                    apply(f0)
                    horizontalDrag(down.id) { it.consume(); apply(toFraction(it.position.x)) }
                    active = -1
                    finish()
                }
            }
            .drawBehind {
                val m = morph.value
                val trackH = SliderIdleTrack.toPx() + (SliderHeldTrack - SliderIdleTrack).toPx() * m
                val half = SliderThumb.toPx() / 2f
                val x = { f: Float -> half + (size.width - 2 * half) * f }
                drawSeekTrack(trackH, c.sliderTrack, c.accent, x(start), x(end))
                if (marker != null) {
                    val mw = 2.dp.toPx()
                    drawRoundRect(
                        c.text,
                        androidx.compose.ui.geometry.Offset(x(marker) - mw / 2f, (size.height - 24.dp.toPx()) / 2f),
                        androidx.compose.ui.geometry.Size(mw, 24.dp.toPx()),
                        androidx.compose.ui.geometry.CornerRadius(mw / 2f),
                    )
                }
                drawSeekThumb(x(start), t0.value, if (c.isDark) c.background else Color.White, c.accent)
                drawSeekThumb(x(end), t1.value, if (c.isDark) c.background else Color.White, c.accent)
            },
    )
}

/** Progress / result strip used by tools that write files. */
@Composable
fun JobStatus(running: Float?, done: String?, error: String?, onCancel: () -> Unit, modifier: Modifier = Modifier) {
    val c = OneUi.colors
    Column(modifier.fillMaxWidth()) {
        when {
            running != null -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Working… ${(running * 100).roundToInt()}%", fontSize = 14.sp, color = c.textSecondary, modifier = Modifier.weight(1f))
                    Text(
                        "Cancel", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = c.accent,
                        modifier = Modifier.clip(CircleShape).clickable(onClick = onCancel).padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
                ThinProgress(running, Modifier.padding(top = 8.dp))
            }
            done != null -> Text("Saved to ${com.tools.app.Storage.label(com.tools.app.Storage.Folder.Audio)} · $done", fontSize = 14.sp, color = c.success)
            error != null -> Text(error, fontSize = 14.sp, color = c.error)
        }
    }
}

/**
 * One UI dialog: a floating rounded card near the bottom of the screen, bold title, and text
 * buttons along the bottom edge (no filled buttons), as in Samsung's system dialogs.
 */
@Composable
fun OneUiDialog(
    title: String,
    onDismiss: () -> Unit,
    buttons: List<Pair<String, () -> Unit>>,
    dismissible: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = OneUi.colors
    androidx.compose.ui.window.Dialog(
        onDismissRequest = { if (dismissible) onDismiss() },
        properties = androidx.compose.ui.window.DialogProperties(
            usePlatformDefaultWidth = false, dismissOnBackPress = dismissible, dismissOnClickOutside = dismissible,
        ),
    ) {
        val shown = remember { androidx.compose.animation.core.MutableTransitionState(false).apply { targetState = true } }
        Box(Modifier.fillMaxSize().navigationBarsPadding().padding(12.dp), contentAlignment = Alignment.BottomCenter) {
            androidx.compose.animation.AnimatedVisibility(
                shown,
                enter = androidx.compose.animation.slideInVertically(tween(340, easing = OneUiEasing)) { it / 3 } +
                    androidx.compose.animation.fadeIn(tween(200)) +
                    androidx.compose.animation.scaleIn(tween(340, easing = OneUiEasing), initialScale = 0.94f),
            ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .background(c.popup)
                    .padding(top = 24.dp, bottom = 10.dp),
            ) {
                Text(title, fontSize = 19.sp, fontWeight = FontWeight.Bold, color = c.text, modifier = Modifier.padding(horizontal = 24.dp))
                Column(Modifier.padding(horizontal = 24.dp).padding(top = 12.dp), content = content)
                if (buttons.isNotEmpty()) Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    buttons.forEachIndexed { i, (label, action) ->
                        if (i > 0) Box(Modifier.width(1.dp).height(18.dp).background(c.divider))
                        Box(
                            Modifier.weight(1f).height(46.dp).clip(CircleShape).clickable(onClick = action),
                            contentAlignment = Alignment.Center,
                        ) { Text(label, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = c.accent) }
                    }
                }
            }
            }
        }
    }
}

/** Rounded clip for a photo drawn at [r] on a canvas (One UI editors round image corners). */
fun androidx.compose.ui.graphics.drawscope.DrawScope.roundedImage(r: androidx.compose.ui.geometry.Rect): androidx.compose.ui.graphics.Path =
    androidx.compose.ui.graphics.Path().apply {
        addRoundRect(androidx.compose.ui.geometry.RoundRect(r, androidx.compose.ui.geometry.CornerRadius(16.dp.toPx())))
    }

/** 0 → 1 fade whenever [key] changes (a newly loaded image eases in instead of popping). */
@Composable
fun rememberFadeIn(key: Any?): Float {
    val a = remember(key) { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(key) { a.animateTo(1f, tween(380, easing = OneUiEasing)) }
    return a.value
}



/**
 * One UI's soft fade at the edges of a scrolling list, for lists outside [OneUiScaffold]. Each edge
 * only fades while there is more content beyond it, and eases in and out as you scroll.
 */
fun Modifier.fadingEdges(state: LazyListState, horizontal: Boolean = false, length: Dp = FadeLength): Modifier = composed {
    val start by animateFloatAsState(if (state.canScrollBackward) 1f else 0f, tween(200), label = "fadeStart")
    val end by animateFloatAsState(if (state.canScrollForward) 1f else 0f, tween(200), label = "fadeEnd")
    this
        .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
        .drawWithContent {
            drawContent()
            val total = if (horizontal) size.width else size.height
            if (total <= 0f) return@drawWithContent
            val f = (length.toPx() / total).coerceAtMost(0.4f)
            val stops = arrayOf(
                0f to Color.Black.copy(alpha = 1f - start),
                f to Color.Black,
                1f - f to Color.Black,
                1f to Color.Black.copy(alpha = 1f - end),
            )
            drawRect(
                if (horizontal) Brush.horizontalGradient(*stops) else Brush.verticalGradient(*stops),
                blendMode = BlendMode.DstIn,
            )
        }
}
