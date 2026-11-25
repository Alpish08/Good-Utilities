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
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.outlined.CreditCard
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

/** ID-1 cards (driver's licences, most IDs): 85.6 × 54 mm. */
private const val CardRatio = 85.6f / 54f
private val CardShape = RoundedCornerShape(16.dp)

private sealed interface Page {
    data object List : Page
    data class Open(val id: String) : Page
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

    when (val p = page) {
        Page.List -> CardList(cards, onBack, onOpen = { page = Page.Open(it) }, onAdd = { page = Page.Add })
        is Page.Open -> {
            BackHandler { page = Page.List }
            val card = cards.firstOrNull { it.id == p.id }
            if (card == null) page = Page.List else CardView(card, onBack = { page = Page.List })
        }
        Page.Add -> {
            val done = { IdStore.pendingShare.value = null; page = Page.List }
            BackHandler { done() }
            AddCard(pending, onDone = done)
        }
    }
}

// ---------------------------------------------------------------- list: the Wallet stack

@Composable
private fun CardList(cards: List<IdCard>, onBack: () -> Unit, onOpen: (String) -> Unit, onAdd: () -> Unit) {
    // Samsung Wallet's two views, switched from a bottom tab bar: Quick access (one card at a time, swipe between them)
    // and All (the stack).
    var tab by rememberSaveable { mutableStateOf(0) }
    val tabs = remember {
        listOf(
            com.tools.app.ui.PillTab("Quick access", Icons.Outlined.CreditCard, Icons.Rounded.CreditCard),
            com.tools.app.ui.PillTab("All", Icons.Outlined.Apps, Icons.Rounded.Apps),
        )
    }
    OneUiScaffold(
        title = "ID cards",
        subtitle = when (cards.size) { 0 -> null; 1 -> "1 card"; else -> "${cards.size} cards" },
        onBack = onBack,
        actions = { ToolbarIcon(OneUiIcons.Add, "Add card", onAdd) },
        bottomBar = if (cards.size > 1) ({ com.tools.app.ui.TabPill(tabs, tab, { tab = it }) }) else null,
        fadeUnderBottomBar = false,
    ) {
        if (cards.isEmpty()) {
            description("Keep a copy of your IDs' front and back: crop the photos in Gallery first, then add them here. They're locked with your fingerprint and encrypted on this phone.")
            card("add-first") { ListRow("Add an ID card", summary = "Pick the front (and back) from your photos", onClick = onAdd) }
            return@OneUiScaffold
        }
        if (tab == 0 || cards.size == 1) item("carousel") { QuickAccess(cards, onOpen) }
        else item("stack") { WalletStack(cards, onOpen) }
    }
}

/**
 * Samsung Wallet's Quick access: one card at a time, its neighbours peeking in at the sides; swipe between them. Under
 * each card hangs a slightly narrower panel with its name, like Wallet's balance strip.
 */
@Composable
private fun QuickAccess(cards: List<IdCard>, onOpen: (String) -> Unit) {
    val c = OneUi.colors
    val pager = androidx.compose.foundation.pager.rememberPagerState { cards.size }
    androidx.compose.foundation.pager.HorizontalPager(
        pager,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 36.dp),
        pageSpacing = 14.dp,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 12.dp),
    ) { i ->
        val card = cards[i]
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CardFace(
                card, back = false,
                modifier = Modifier.fillMaxWidth().aspectRatio(CardRatio)
                    .shadow(10.dp, CardShape, ambientColor = Color(0x88000000), spotColor = Color(0xAA000000))
                    .clip(CardShape).clickable { onOpen(card.id) },
                crop = true,
            )
            Box(
                Modifier.fillMaxWidth().padding(horizontal = 10.dp)
                    .clip(RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp)).background(c.card)
                    .clickable { onOpen(card.id) }.padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(card.title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = c.textSecondary, maxLines = 1)
            }
        }
    }
}

/** Samsung Wallet's "All" stack: each card shows its top strip, the last one in full. */
@Composable
private fun WalletStack(cards: List<IdCard>, onOpen: (String) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
        val cardH = maxWidth / CardRatio
        val strip = 70.dp
        Box(Modifier.fillMaxWidth().height(strip * (cards.size - 1) + cardH)) {
            cards.forEachIndexed { i, card ->
                CardFace(
                    card, back = false,
                    modifier = Modifier.offset(y = strip * i).fillMaxWidth().aspectRatio(CardRatio)
                        .shadow(if (i == 0) 0.dp else 10.dp, CardShape, ambientColor = Color(0x88000000), spotColor = Color(0xAA000000))
                        .clip(CardShape).clickable { onOpen(card.id) },
                    crop = true,
                )
            }
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
            alignment = Alignment.TopCenter,
            modifier = Modifier.matchParentSizeCompat(),
        )
    }
}

@Composable
private fun Modifier.matchParentSizeCompat(): Modifier = this.then(Modifier.fillMaxWidth().aspectRatio(CardRatio))

// ---------------------------------------------------------------- one card: front and back

@Composable
private fun CardView(card: IdCard, onBack: () -> Unit) {
    val context = LocalContext.current
    val c = OneUi.colors
    var showBack by rememberSaveable(card.id) { mutableStateOf(false) }
    val turn by animateFloatAsState(if (showBack && card.hasBack) 180f else 0f, tween(420, easing = com.tools.app.ui.theme.OneUiEasing), label = "flip")
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var replacingBack by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        IdStore.picking = false
        if (uri != null) scope.launch {
            val bytes = withContext(Dispatchers.IO) { read(context, uri) } ?: return@launch
            withContext(Dispatchers.IO) { IdStore.replace(context, card.id, replacingBack, bytes) }
            if (replacingBack) showBack = true
        }
    }
    fun pick(back: Boolean) {
        replacingBack = back; IdStore.picking = true
        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    OneUiScaffold(
        title = card.title, onBack = onBack,
        // Wallet's card page: a compact top bar ("‹ Name ⋮"), the card just under it.
        expandedTitle = false,
        actions = {
            OverflowMenu(buildList {
                add("Rename" to { renaming = true })
                add("Replace front" to { pick(false) })
                add((if (card.hasBack) "Replace back" else "Add back") to { pick(true) })
                if (card.hasBack) add("Remove back" to { IdStore.removeBack(context, card.id); showBack = false })
                add("Delete" to { deleting = true })
            })
        },
    ) {
        item("card") {
            // Tap or swipe to turn it over.
            Box(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)
                    .pointerInput(card.hasBack) {
                        var dx = 0f
                        detectHorizontalDragGestures(onDragStart = { dx = 0f }, onDragEnd = { if (card.hasBack && kotlin.math.abs(dx) > 60f) showBack = !showBack }) { _, d -> dx += d }
                    },
            ) {
                val face = if (turn > 90f) true else false
                CardFace(
                    card, back = face,
                    modifier = Modifier.fillMaxWidth().aspectRatio(CardRatio)
                        .graphicsLayer {
                            rotationY = if (face) turn - 180f else turn
                            cameraDistance = 14f * density
                        }
                        .shadow(10.dp, CardShape, ambientColor = Color(0x88000000), spotColor = Color(0xAA000000))
                        .clip(CardShape)
                        .clickable(enabled = card.hasBack, interactionSource = null, indication = null) { showBack = !showBack },
                )
            }
        }
        item("side") {
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center) {
                if (card.hasBack) {
                    listOf("Front" to false, "Back" to true).forEach { (label, b) ->
                        val on = showBack == b
                        Text(
                            label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                            color = if (on) c.text else c.textSecondary,
                            modifier = Modifier.clip(CircleShape).background(if (on) c.tonal else Color.Transparent)
                                .clickable { showBack = b }.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                } else Text("No back added · ⋮ › Add back", fontSize = 13.5.sp, color = c.textSecondary)
            }
        }
        card("info") {
            ListRow("Added", summary = java.text.DateFormat.getDateInstance(java.text.DateFormat.LONG).format(java.util.Date(card.added)))
            com.tools.app.ui.CardDivider()
            ListRow("Stored", summary = "Encrypted on this phone · never in Gallery or backups")
        }
    }

    if (renaming) {
        var name by remember { mutableStateOf(card.title) }
        OneUiDialog(
            "Rename", onDismiss = { renaming = false },
            buttons = listOf("Cancel" to { renaming = false }, "Save" to { IdStore.rename(context, card.id, name.trim()); renaming = false }),
        ) { NameField(name, { name = it.take(40) }, popup = true) }
    }
    if (deleting) OneUiDialog(
        "Delete ${card.title}?", onDismiss = { deleting = false },
        buttons = listOf("Cancel" to { deleting = false }, "Delete" to { deleting = false; IdStore.delete(context, card.id); onBack() }),
    ) {
        Text("Both photos are removed from this phone. Your Gallery copies, if any, stay where they are.", fontSize = 15.sp, color = c.textSecondary)
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

    OneUiScaffold(title = "Add ID card", onBack = onDone) {
        description("Crop and straighten the photos in Gallery first (Edit › crop finds the card's edges), then pick them here.")
        card("name") {
            Box(Modifier.padding(16.dp)) { NameField(name, { name = it.take(40) }) }
        }
        item("front") { PhotoSlot("Front", front, onPick = { pick(false) }) }
        item("back") { PhotoSlot("Back (optional)", back, onPick = { pick(true) }, onClear = back?.let { { back = null } }) }
        item("save") {
            Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), contentAlignment = Alignment.CenterEnd) {
                PillButton(
                    if (saving) "Saving…" else "Save",
                    enabled = front != null && name.isNotBlank() && !saving,
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
private fun PhotoSlot(label: String, image: ByteArray?, onPick: () -> Unit, onClear: (() -> Unit)? = null) {
    val c = OneUi.colors
    val bmp = remember(image) { image?.let { runCatching { android.graphics.BitmapFactory.decodeByteArray(it, 0, it.size) }.getOrNull() } }
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = c.textSecondary, modifier = Modifier.weight(1f))
            if (onClear != null) Text(
                "Remove", fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = c.accent,
                modifier = Modifier.clip(CircleShape).clickable(onClick = onClear).padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
        Box(
            Modifier.fillMaxWidth().aspectRatio(CardRatio).clip(CardShape)
                .then(if (bmp == null) Modifier.background(c.card).border(1.dp, c.divider, CardShape) else Modifier)
                .clickable(onClick = onPick),
            contentAlignment = Alignment.Center,
        ) {
            if (bmp != null) Image(bmp.asImageBitmap(), label, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().aspectRatio(CardRatio))
            else Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Rounded.AddPhotoAlternate, null, tint = c.textSecondary, modifier = Modifier.size(34.dp))
                Text("Choose from photos", fontSize = 14.sp, color = c.textSecondary, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp))
            }
        }
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
