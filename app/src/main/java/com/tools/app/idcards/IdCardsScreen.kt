package com.tools.app.idcards

import android.app.Activity
import android.graphics.Bitmap
import android.net.Uri
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tools.app.ui.ListRow
import com.tools.app.ui.OneUiDialog
import com.tools.app.ui.OneUiIcons
import com.tools.app.ui.OneUiScaffold
import com.tools.app.ui.OverflowMenu
import com.tools.app.ui.PillButton
import com.tools.app.ui.ToolbarIcon
import com.tools.app.ui.card
import com.tools.app.ui.description
import com.tools.app.ui.theme.OneUi
import com.tools.app.ui.theme.SamsungFont
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Samsung Wallet's card, measured on the S24 Ultra: 336 × 209 dp (1175 × 731 px), corners ~12 dp. */
private const val CardRatio = 1175f / 731f
private val CardShape = RoundedCornerShape(12.dp)

private sealed interface Page {
    data object List : Page
    data object Add : Page
}

/**
 * ID cards: your IDs' front and back, Samsung Wallet style. Opening it always asks for your fingerprint or PIN (done by
 * the caller, like App lock), and it closes again when the screen turns off or you leave the app.
 */
@Composable
fun IdCardsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    remember { IdStore.load(context) }
    val cards by IdStore.cards.collectAsState()
    val pending by IdStore.pendingShare.collectAsState()
    var page by remember { mutableStateOf<Page>(if (pending != null) Page.Add else Page.List) }

    // No screenshots or recents preview while open (IdStore.SECURE; off while testing).
    val window = (context as? Activity)?.window
    DisposableEffect(Unit) {
        if (IdStore.SECURE) window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { if (IdStore.SECURE) window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }

    when (page) {
        Page.List -> CardList(cards, onBack, onAdd = { page = Page.Add })
        Page.Add -> {
            val done = { IdStore.pendingShare.value = null; page = Page.List }
            BackHandler { done() }
            AddCard(pending, onDone = done)
        }
    }
}

// ---------------------------------------------------------------- the cards (Wallet's Quick access)

/**
 * Samsung Wallet's Quick access: one card at a time, its neighbours peeking in at the sides; swipe sideways between
 * them (with Wallet's own haptic tick), swipe down (or tap) to turn one over. The ⋮ menu acts on the card in view.
 */
@Composable
private fun CardList(cards: List<IdCard>, onBack: () -> Unit, onAdd: () -> Unit) {
    val context = LocalContext.current
    val c = OneUi.colors
    val view = androidx.compose.ui.platform.LocalView.current
    val pager = androidx.compose.foundation.pager.rememberPagerState { cards.size }
    val current = cards.getOrNull(pager.currentPage.coerceAtMost(cards.lastIndex))
    // Each card's turn, in half-turns of 180°: swiping down adds one, swiping up takes one away, so it always turns
    // the way you swipe. Odd = showing the back.
    var turns by remember { mutableStateOf(mapOf<String, Int>()) }
    fun showingBack(id: String) = Math.floorMod(turns[id] ?: 0, 2) == 1
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var replacingBack by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        IdStore.picking = false
        val card = current
        if (uri != null && card != null) scope.launch {
            val bytes = withContext(Dispatchers.IO) { read(context, uri) } ?: return@launch
            withContext(Dispatchers.IO) { IdStore.replace(context, card.id, replacingBack, bytes) }
            if (showingBack(card.id) != replacingBack) turns = turns + (card.id to (turns[card.id] ?: 0) + 1)
        }
    }
    fun pick(back: Boolean) {
        replacingBack = back; IdStore.picking = true
        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    // Samsung Wallet's card-switch haptic (SemHaptic type 50025, measured from Wallet: a short, sharp tick).
    var lastPage by remember { mutableStateOf(-1) }
    androidx.compose.runtime.LaunchedEffect(pager) {
        androidx.compose.runtime.snapshotFlow { pager.currentPage }.collect { p ->
            if (lastPage >= 0 && p != lastPage) {
                if (!view.performHapticFeedback(50025)) view.performHapticFeedback(android.view.HapticFeedbackConstants.SEGMENT_FREQUENT_TICK)
            }
            lastPage = p
        }
    }

    OneUiScaffold(
        title = "Virtual cards",
        subtitle = when (cards.size) { 0 -> null; 1 -> "1 card"; else -> "${cards.size} cards" },
        onBack = onBack,
        actions = {
            ToolbarIcon(OneUiIcons.Add, "Add card", onAdd)
            val card = current
            val tips = com.tools.app.ui.tipsItem(ID_TIPS)
            if (card == null) OverflowMenu(listOf(tips))
            else OverflowMenu(buildList {
                add("Rename" to { renaming = true })
                add("Replace front" to { pick(false) })
                add((if (card.hasBack) "Replace back" else "Add back") to { pick(true) })
                if (card.hasBack) add("Remove back" to { IdStore.removeBack(context, card.id); turns = turns - card.id })
                add("Delete" to { deleting = true })
                add(tips)
            })
        },
    ) {
        if (cards.isEmpty()) {
            card("add-first") { ListRow("Add a card", summary = "Pick the front (and back) from your photos", onClick = onAdd) }
            return@OneUiScaffold
        }
        item("carousel") {
            androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
            // Wallet: the card 38 dp in from each edge, its neighbours peeking in. In landscape that card would be
            // taller than the screen, so there it's sized to fit the height (with the dots and name under it).
            val cardW = landscapeCardWidth()?.coerceAtMost(maxWidth - 76.dp) ?: (maxWidth - 76.dp)
            androidx.compose.foundation.pager.HorizontalPager(
                pager,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = (maxWidth - cardW) / 2),
                pageSpacing = 14.dp,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 12.dp),
            ) { i ->
                val card = cards[i]
                FlipCard(card, turns[card.id] ?: 0, onSwipe = { down ->
                    if (card.hasBack) turns = turns + (card.id to (turns[card.id] ?: 0) + if (down) 1 else -1)
                })
            }
            }
        }
        // Under the cards: One UI's page indicator (the current card a capsule), then its name and side.
        if (current != null) item("label") {
            Column(Modifier.fillMaxWidth().padding(top = 2.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                if (cards.size > 1) PageCapsules(cards.size, pager.currentPage)
                Text(current.title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = c.text, maxLines = 1, modifier = Modifier.padding(top = 14.dp))
                Text(
                    if (!current.hasBack) "Front only" else if (showingBack(current.id)) "Back" else "Front",
                    fontSize = 13.5.sp, color = c.textSecondary, modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }

    val card = current
    if (renaming && card != null) {
        var name by remember { mutableStateOf(card.title) }
        OneUiDialog(
            "Rename", onDismiss = { renaming = false },
            buttons = listOf("Cancel" to { renaming = false }, "Save" to { IdStore.rename(context, card.id, name.trim()); renaming = false }),
        ) { NameField(name, { name = it.take(40) }, popup = true) }
    }
    if (deleting && card != null) OneUiDialog(
        "Delete ${card.title}?", onDismiss = { deleting = false },
        buttons = listOf("Cancel" to { deleting = false }, "Delete" to { deleting = false; IdStore.delete(context, card.id) }),
    ) {
        Text("Both photos are removed from this phone. Your Gallery copies, if any, stay where they are.", fontSize = 15.sp, color = c.textSecondary)
    }
}

/** A card that turns over the way you swipe: down and its top comes towards you, up and its bottom does. */
@Composable
private fun FlipCard(card: IdCard, turns: Int, onSwipe: (down: Boolean) -> Unit) {
    val c = OneUi.colors
    val angle by animateFloatAsState(turns * 180f, tween(440, easing = com.tools.app.ui.theme.OneUiEasing), label = "flip")
    // The nearest half-turn decides which face is visible; the face is drawn relative to it so it never shows mirrored.
    val half = kotlin.math.round(angle / 180f).toInt()
    val back = Math.floorMod(half, 2) == 1 && card.hasBack
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        CardFace(
            card, back = back, crop = true,
            modifier = Modifier.fillMaxWidth().aspectRatio(CardRatio)
                .pointerInput(card.id) {
                    var dy = 0f
                    detectVerticalDragGestures(onDragStart = { dy = 0f }, onDragEnd = { if (kotlin.math.abs(dy) > 50f) onSwipe(dy > 0f) }) { _, d -> dy += d }
                }
                .graphicsLayer {
                    rotationX = -(angle - half * 180f)
                    cameraDistance = 14f * density
                }
                .shadow(10.dp, CardShape, ambientColor = Color(0x88000000), spotColor = Color(0xAA000000))
                .clip(CardShape)
                .border(1.dp, com.tools.app.ui.pillEdge(c.isDark), CardShape),
        )
    }
}

/** One UI's page indicator: dots, the current page a capsule that grows into place. */
@Composable
private fun PageCapsules(count: Int, current: Int) {
    val c = OneUi.colors
    Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(count) { i ->
            val on = i == current
            val w by androidx.compose.animation.core.animateDpAsState(if (on) 18.dp else 6.dp, tween(260, easing = com.tools.app.ui.theme.OneUiEasing), label = "dot")
            Box(Modifier.size(width = w, height = 6.dp).clip(CircleShape).background(if (on) c.text else c.text.copy(alpha = 0.3f)))
        }
    }
}

/** One side of a card, decrypted in memory for display. */
@Composable
private fun CardFace(card: IdCard, back: Boolean, modifier: Modifier = Modifier, crop: Boolean = false) {
    val context = LocalContext.current
    val c = OneUi.colors
    val bmp by produceState<Bitmap?>(null, card.id, back, card.hasBack) {
        value = withContext(Dispatchers.IO) { IdStore.image(context, card.id, back) }
    }
    Box(modifier.background(c.card), contentAlignment = Alignment.Center) {
        val b = bmp
        if (b != null) Image(
            b.asImageBitmap(), card.title,
            contentScale = if (crop) ContentScale.Crop else ContentScale.Fit,
            modifier = Modifier.fillMaxWidth().aspectRatio(CardRatio),
        )
    }
}

// ---------------------------------------------------------------- add

@Composable
private fun AddCard(shared: ByteArray?, onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var name by rememberSaveable { mutableStateOf("") }
    var front by remember { mutableStateOf(shared) }
    var back by remember { mutableStateOf<ByteArray?>(null) }
    var pickingBack by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var side by rememberSaveable { mutableStateOf(0) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        IdStore.picking = false
        if (uri != null) scope.launch {
            val bytes = withContext(Dispatchers.IO) { read(context, uri) } ?: return@launch
            if (pickingBack) back = bytes else front = bytes
        }
    }
    fun pick(isBack: Boolean) {
        pickingBack = isBack; IdStore.picking = true
        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    OneUiScaffold(title = "Add card", onBack = onDone, actions = { com.tools.app.ui.TipsMenu(ID_TIPS) }) {
        // Front | Back, like the Compressor's Video | Audio | Photo, over one photo slot for the chosen side.
        item("side") {
            Box(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                com.tools.app.ui.SegmentedControl(listOf("Front", "Back"), if (side == 0) 0 else 1, { side = it })
            }
        }
        item("photo") {
            if (side == 0) PhotoSlot(null, front, onPick = { pick(false) })
            else PhotoSlot(null, back, onPick = { pick(true) }, onClear = back?.let { { back = null } }, hint = "Optional")
        }
        // The name goes under the photo, centred, just where the cards page shows it; Save sits beside it on the
        // line, so it's in reach without scrolling.
        item("name") {
            Box(Modifier.fillMaxWidth()) {
                CardNameField(name, { name = it.take(40) })
                PillButton(
                    if (saving) "Saving…" else "Save",
                    enabled = front != null && name.isNotBlank() && !saving,
                    modifier = Modifier.align(Alignment.TopEnd).padding(top = 20.dp, end = 20.dp),
                    onClick = {
                        val f = front ?: return@PillButton
                        saving = true
                        scope.launch {
                            withContext(Dispatchers.IO) { IdStore.add(context, name.trim(), f, back) }
                            onDone()
                        }
                    },
                )
            }
        }
    }
}

/** A picked photo (or the place to pick one), at card proportions. */
@Composable
private fun PhotoSlot(label: String?, image: ByteArray?, onPick: () -> Unit, onClear: (() -> Unit)? = null, hint: String? = null) {
    val c = OneUi.colors
    val bmp = remember(image) { image?.let { runCatching { android.graphics.BitmapFactory.decodeByteArray(it, 0, it.size) }.getOrNull() } }
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
        if (label != null || onClear != null) Row(Modifier.fillMaxWidth().padding(start = 4.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label.orEmpty(), fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = c.textSecondary, modifier = Modifier.weight(1f))
            if (onClear != null) Text(
                "Remove", fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = c.accent,
                modifier = Modifier.clip(CircleShape).clickable(onClick = onClear).padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
        Box(
            Modifier.align(Alignment.CenterHorizontally).then(landscapeCardWidth()?.let { Modifier.widthIn(max = it) } ?: Modifier)
                .fillMaxWidth().aspectRatio(CardRatio).clip(CardShape)
                .then(if (bmp == null) Modifier.background(c.card).border(1.dp, c.divider, CardShape) else Modifier)
                .clickable(onClick = onPick),
            contentAlignment = Alignment.Center,
        ) {
            if (bmp != null) Image(bmp.asImageBitmap(), label ?: "Photo", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().aspectRatio(CardRatio))
            else Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(OneUiIcons.AddPhoto, null, tint = c.textSecondary, modifier = Modifier.size(34.dp))
                Text("Choose from photos", fontSize = 14.sp, color = c.textSecondary, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp))
                if (hint != null) Text(hint, fontSize = 12.5.sp, color = c.textSecondary.copy(alpha = 0.7f), modifier = Modifier.padding(top = 2.dp))
            }
        }
    }
}

/** The card's name, typed under its photo: centred and bold like the cards page, over a short hairline. */
@Composable
private fun CardNameField(value: String, onChange: (String) -> Unit) {
    val c = OneUi.colors
    Column(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.fillMaxWidth(0.5f), contentAlignment = Alignment.Center) {
            if (value.isEmpty()) Text("Card name", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = c.textSecondary)
            BasicTextField(
                value, onChange, singleLine = true,
                textStyle = TextStyle(fontFamily = SamsungFont, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = c.text, textAlign = TextAlign.Center),
                cursorBrush = SolidColor(c.accent),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            )
        }
        Box(Modifier.width(150.dp).height(1.dp).background(com.tools.app.ui.pillEdge(c.isDark)))
        Text("e.g. Driver's license", fontSize = 13.sp, color = c.textSecondary, modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun NameField(value: String, onChange: (String) -> Unit, popup: Boolean = false) {
    val c = OneUi.colors
    Box(
        Modifier.fillMaxWidth().height(48.dp).clip(CircleShape)
            .then(
                if (popup) Modifier.background(com.tools.app.ui.popupFieldFill(c.isDark)).border(0.6.dp, com.tools.app.ui.pillEdge(c.isDark), CircleShape)
                else Modifier.background(c.tonal),
            )
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (value.isEmpty()) Text("Name (e.g. Driver's license)", fontSize = 16.sp, color = c.textSecondary)
        BasicTextField(
            value, onChange, singleLine = true,
            textStyle = TextStyle(fontFamily = SamsungFont, fontSize = 16.sp, color = c.text),
            cursorBrush = SolidColor(c.accent),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private fun read(context: android.content.Context, uri: Uri): ByteArray? =
    runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()

/** Virtual cards' explanations, in ⋮ › Tips on the cards and Add card pages. */
private val ID_TIPS = listOf(
    com.tools.app.ui.Tip("Your IDs, front and back", "Keep a copy of your IDs here. They're locked with your fingerprint and encrypted on this phone, and screenshots are blocked."),
    com.tools.app.ui.Tip("Crop in Gallery first", "Crop and straighten the photos in Gallery (Edit › crop finds the card's edges), then pick them here, or share a photo to \"Add to Virtual cards\"."),
    com.tools.app.ui.Tip("Turn a card over", "Swipe down or up on a card to see its back."),
)

/**
 * In landscape (a short screen) the widest card that still fits the height: the screen less the toolbar, the pager's
 * padding and the dots and name under the card. null in portrait, where cards keep Wallet's width.
 */
@Composable
private fun landscapeCardWidth(): androidx.compose.ui.unit.Dp? {
    val h = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp.dp
    if (h >= com.tools.app.ui.CompactHeight) return null
    return ((h - 64.dp - 20.dp - 80.dp) * CardRatio).coerceAtLeast(200.dp)
}
