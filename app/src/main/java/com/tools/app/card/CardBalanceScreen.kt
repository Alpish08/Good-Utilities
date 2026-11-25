package com.tools.app.card

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tools.app.ui.ListRow
import com.tools.app.ui.OneUiDialog
import com.tools.app.ui.OneUiScaffold
import com.tools.app.ui.OverflowMenu
import com.tools.app.ui.PillButton
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material3.Icon
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import com.tools.app.ui.RadioDot
import com.tools.app.ui.card
import com.tools.app.ui.collapseHeader
import com.tools.app.ui.cardList
import com.tools.app.ui.description
import com.tools.app.ui.sectionLabel
import com.tools.app.ui.theme.OneUi
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private enum class Dialog { Switch, Add, Rename, Start, Delete }

/** Card balance: manage expenses and track payments. Choose − or +, type an amount, tap Log. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun CardBalanceScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val c = OneUi.colors
    remember { CardStore.load(context) }
    val state by CardStore.state.collectAsState()
    val card = state.current

    var amount by remember { mutableStateOf("") }
    var spend by remember { mutableStateOf(true) }
    var note by remember { mutableStateOf("") }
    var dialog by remember { mutableStateOf<Dialog?>(null) }
    var editing by remember { mutableStateOf<Entry?>(null) }
    var deleted by remember { mutableStateOf<List<Entry>>(emptyList()) }
    // Multi-select (long-press a payment): My Files / Reminder style, with Delete in a floating action bar.
    var selecting by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    androidx.activity.compose.BackHandler(enabled = selecting) { selecting = false; selected = emptySet() }
    val amountFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val windowInfo = androidx.compose.ui.platform.LocalWindowInfo.current
    val imeShown = androidx.compose.runtime.rememberUpdatedState(androidx.compose.foundation.layout.WindowInsets.isImeVisible)

    // From the widget's − / + buttons: that mode, with the amount box ready to type in. Collected in one long-lived
    // effect (keying an effect on the request would cancel it the moment the request is cleared).
    val hasCard = card != null
    LaunchedEffect(hasCard) {
        if (!hasCard) return@LaunchedEffect
        CardStore.pendingMode.collect { mode ->
            if (mode == null) return@collect
            spend = mode
            // The window and the list item take a moment to be ready (longer on a cold start): keep asking until
            // the keyboard is really up, for up to 2 s.
            for (attempt in 0 until 20) {
                delay(100)
                if (!windowInfo.isWindowFocused) continue
                runCatching { amountFocus.requestFocus() }
                keyboard?.show()
                if (imeShown.value) break
            }
            CardStore.pendingMode.value = null
        }
    }
    LaunchedEffect(deleted) { if (deleted.isNotEmpty()) { delay(6000); deleted = emptyList() } }

    fun logIt() {
        val cents = CardStore.parse(amount) ?: return
        if (cents == 0L) return
        CardStore.log(context, if (spend) -cents else cents, note)
        amount = ""; note = ""
    }

    // The keyboard (amount or note) folds the big title away, so the entry row and history stay in view above it.
    val list = androidx.compose.foundation.lazy.rememberLazyListState()
    LaunchedEffect(imeShown.value) {
        if (imeShown.value && list.firstVisibleItemIndex == 0) list.collapseHeader()
    }

    val allIds = card?.entries?.map { it.id }.orEmpty()
    OneUiScaffold(
        title = "Card balance", onBack = onBack, listState = list,
        selection = if (selecting) com.tools.app.ui.Selection(
            count = selected.size,
            allSelected = allIds.isNotEmpty() && selected.size == allIds.size,
            onToggleAll = { selected = if (selected.size == allIds.size) emptySet() else allIds.toSet() },
            onCancel = { selecting = false; selected = emptySet() },
        ) else null,
        bottomBar = if (selecting) ({
            com.tools.app.ui.SelectionActions(
                listOf(
                    Triple(androidx.compose.material.icons.Icons.Outlined.Delete, "Delete") {
                        val gone = card?.entries?.filter { it.id in selected }.orEmpty()
                        gone.forEach { CardStore.deleteEntry(context, it.id) }
                        deleted = gone; selecting = false; selected = emptySet()
                    },
                ),
                enabled = selected.isNotEmpty(),
            )
        }) else null,
        actions = {
            if (card != null) OverflowMenu(
                buildList {
                    if (state.cards.size > 1) add("Switch card" to { dialog = Dialog.Switch })
                    add("Add card" to { dialog = Dialog.Add })
                    if (!card.primary && state.primary?.id != card.id) add("Make primary" to { CardStore.makePrimary(context) })
                    add("Rename card" to { dialog = Dialog.Rename })
                    add("Edit starting balance" to { dialog = Dialog.Start })
                    add("Delete card" to { dialog = Dialog.Delete })
                },
            )
        },
    ) {
        if (card == null) {
            card("setup") { SetupCard(onCreate = { name, start -> CardStore.addCard(context, name, start) }) }
            description("Manage your spending: log purchases and deposits, see your balance, and look back through your payment history. Everything stays on this phone.")
            return@OneUiScaffold
        }

        card("balance") {
            // The switcher keeps its size; its right gap matches the space above and below it (measured from the card).
            val density = androidx.compose.ui.platform.LocalDensity.current
            var rowH by remember { mutableStateOf(0.dp) }
            val sideGap = ((rowH - 76.dp) / 2).coerceAtLeast(12.dp)
            Row(
                Modifier.onSizeChanged { rowH = with(density) { it.height.toDp() } }.padding(start = 22.dp, end = sideGap, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                    Text(CardStore.format(card.balanceCents), fontSize = 40.sp, fontWeight = FontWeight.Bold, color = c.text, letterSpacing = 0.sp, maxLines = 1)
                    Text(card.name, fontSize = 15.sp, color = c.textSecondary, modifier = Modifier.padding(top = 2.dp), maxLines = 1)
                }
                // Quick switcher: the other cards are one tap away (or adding the second one).
                val many = state.cards.size > 1
                Column(
                    Modifier.padding(start = 10.dp).size(width = 84.dp, height = 76.dp).clip(RoundedCornerShape(20.dp)).background(c.tonal)
                        .clickable { dialog = if (many) Dialog.Switch else Dialog.Add },
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(if (many) androidx.compose.material.icons.Icons.Rounded.CreditCard else com.tools.app.ui.OneUiIcons.Add, null, tint = c.text, modifier = Modifier.size(24.dp))
                    Text(if (many) "Cards" else "Add card", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = c.text, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }

        card("entry") {
            Column(Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ModeButton("−", selected = spend) { spend = true }
                    ModeButton("+", selected = !spend, modifier = Modifier.padding(start = 6.dp)) { spend = false }
                    MoneyField(
                        amount, { if (CardStore.acceptable(it)) amount = it },
                        Modifier.padding(start = 10.dp).weight(1f),
                        onDone = ::logIt, focus = amountFocus,
                    )
                    PillButton(
                        "Log", onClick = ::logIt,
                        enabled = (CardStore.parse(amount) ?: 0L) > 0L,
                        modifier = Modifier.padding(start = 10.dp),
                    )
                }
                // The gap above the note matches the card's 14 dp bottom edge (the card grows to fit).
                NoteField(note, { note = it.take(120) }, Modifier.padding(top = 14.dp).fillMaxWidth(), onDone = ::logIt)
            }
        }

        if (deleted.isNotEmpty()) {
            val gone = deleted
            card("undo") {
                Row(Modifier.padding(start = 20.dp, end = 8.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (gone.size == 1) "Entry deleted" else "${gone.size} entries deleted", fontSize = 15.sp, color = c.text, modifier = Modifier.weight(1f))
                    Text(
                        "Undo", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = c.accent,
                        modifier = Modifier.clip(CircleShape).clickable { gone.forEach { CardStore.restoreEntry(context, it) }; deleted = emptyList() }.padding(horizontal = 14.dp, vertical = 10.dp),
                    )
                }
            }
        }

        // History, newest first, grouped by day, each with the balance after it.
        var running = card.startCents
        val after = card.entries.sortedBy { it.time }.associate { e -> running += e.cents; e.id to running }
        val days = card.entries.sortedByDescending { it.time }.groupBy { dayKey(it.time) }
        if (days.isEmpty()) description("Your purchases and deposits show up here.", key = "empty-history")
        days.forEach { (day, entries) ->
            sectionLabel(dayLabel(entries.first().time), key = "day-$day")
            cardList(entries, key = { it.id }) { e ->
                ListRow(
                    e.note.ifBlank { if (e.cents < 0) "Purchase" else "Added" },
                    summary = timeText(e.time) + " · Balance " + CardStore.format(after[e.id] ?: 0),
                    trailing = {
                        Text(
                            CardStore.format(e.cents, sign = true),
                            fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                            // Deposits green, expenses a warm red-orange (with their minus sign).
                            color = if (e.cents > 0) c.success else if (c.isDark) Color(0xFFFF7A59) else Color(0xFFE0502E),
                        )
                    },
                    leading = if (selecting) ({ com.tools.app.ui.RoundCheck(e.id in selected, Modifier.size(22.dp)) }) else null,
                    onClick = {
                        if (selecting) selected = if (e.id in selected) selected - e.id else selected + e.id
                        else editing = e
                    },
                    onLongClick = { if (!selecting) { selecting = true; selected = setOf(e.id) } },
                )
            }
        }
        item("bottom") { Box(Modifier.height(24.dp)) }
    }

    val cur = card
    when (dialog) {
        Dialog.Switch -> OneUiDialog("Switch card", { dialog = null }, buttons = listOf("Cancel" to { dialog = null })) {
            state.cards.forEach { k ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { CardStore.switchTo(context, k.id); dialog = null }
                        .padding(horizontal = 6.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioDot(k.id == cur?.id)
                    Column(Modifier.padding(start = 14.dp).weight(1f)) {
                        Text(k.name, fontSize = 16.sp, color = c.text)
                        if (k.id == state.primary?.id) Text("Primary", fontSize = 13.sp, color = c.textSecondary)
                    }
                    Text(CardStore.format(k.balanceCents), fontSize = 15.sp, color = c.textSecondary)
                }
            }
        }
        Dialog.Add -> {
            var name by remember { mutableStateOf("") }
            var start by remember { mutableStateOf("") }
            var primary by remember { mutableStateOf(false) }
            OneUiDialog("Add card", { dialog = null }, buttons = listOf("Cancel" to { dialog = null }, "Add" to {
                CardStore.addCard(context, name, CardStore.parse(start) ?: 0L, primary); dialog = null
            })) {
                com.tools.app.ui.SegmentedControl(listOf("Primary", "Secondary"), if (primary) 0 else 1, { primary = it == 0 }, popup = true)
                Text(
                    if (primary) "Shown on Home and in the widget" else "Open it from Switch card",
                    fontSize = 13.sp, color = c.textSecondary, modifier = Modifier.padding(top = 8.dp, bottom = 14.dp),
                )
                PlainField(name, { name = it.take(40) }, "Card name", Modifier.fillMaxWidth(), capitalize = true, popup = true)
                Text("Your starting balance", fontSize = 14.sp, color = c.textSecondary, modifier = Modifier.padding(top = 14.dp, bottom = 8.dp))
                MoneyField(start, { if (CardStore.acceptable(it)) start = it }, Modifier.fillMaxWidth(), popup = true)
            }
        }
        Dialog.Rename -> {
            var name by remember { mutableStateOf(cur?.name.orEmpty()) }
            OneUiDialog("Rename card", { dialog = null }, buttons = listOf("Cancel" to { dialog = null }, "Save" to { CardStore.rename(context, name); dialog = null })) {
                PlainField(name, { name = it.take(40) }, "Card name", Modifier.fillMaxWidth(), capitalize = true, popup = true)
            }
        }
        Dialog.Start -> {
            var start by remember { mutableStateOf(cur?.let { centsText(it.startCents) }.orEmpty()) }
            OneUiDialog("Starting balance", { dialog = null }, buttons = listOf("Cancel" to { dialog = null }, "Save" to {
                CardStore.parse(start)?.let { CardStore.setStart(context, it) }; dialog = null
            })) {
                Text("What was yours on the card before the first entry.", fontSize = 14.sp, color = c.textSecondary, modifier = Modifier.padding(bottom = 10.dp))
                MoneyField(start, { if (CardStore.acceptable(it)) start = it }, Modifier.fillMaxWidth(), popup = true)
            }
        }
        Dialog.Delete -> OneUiDialog(
            "Delete ${cur?.name}?", { dialog = null },
            buttons = listOf("Cancel" to { dialog = null }, "Delete" to { CardStore.deleteCurrent(context); dialog = null }),
        ) {
            Text("Its balance and payment history will be removed. This can't be undone.", fontSize = 15.sp, color = c.text)
        }
        null -> Unit
    }

    editing?.let { e ->
        var amt by remember(e.id) { mutableStateOf(centsText(kotlin.math.abs(e.cents))) }
        var minus by remember(e.id) { mutableStateOf(e.cents < 0) }
        var txt by remember(e.id) { mutableStateOf(e.note) }
        OneUiDialog(
            "Edit entry", { editing = null },
            buttons = listOf(
                "Delete" to { CardStore.deleteEntry(context, e.id); deleted = listOf(e); editing = null },
                "Cancel" to { editing = null },
                "Save" to {
                    val cents = CardStore.parse(amt)
                    if (cents != null && cents > 0) CardStore.editEntry(context, e.copy(cents = if (minus) -cents else cents, note = txt.trim()))
                    editing = null
                },
            ),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ModeButton("−", selected = minus, popup = true) { minus = true }
                ModeButton("+", selected = !minus, modifier = Modifier.padding(start = 8.dp), popup = true) { minus = false }
                MoneyField(amt, { if (CardStore.acceptable(it)) amt = it }, Modifier.padding(start = 12.dp).weight(1f), popup = true)
            }
            PlainField(txt, { txt = it.take(60) }, "Note (optional)", Modifier.padding(top = 10.dp).fillMaxWidth(), popup = true)
            Text(dayLabel(e.time) + ", " + timeText(e.time), fontSize = 13.sp, color = c.textSecondary, modifier = Modifier.padding(top = 10.dp))
        }
    }
}

/** First visit: name the card and say how much of it is yours. */
@Composable
private fun SetupCard(onCreate: (String, Long) -> Unit) {
    val c = OneUi.colors
    var name by remember { mutableStateOf("") }
    var start by remember { mutableStateOf("") }
    Column(Modifier.padding(18.dp)) {
        Text("Set up a card", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = c.text)
        PlainField(name, { name = it.take(40) }, "Card name (e.g. Debit card)", Modifier.padding(top = 14.dp).fillMaxWidth(), capitalize = true)
        Text("How much of it is yours right now", fontSize = 14.sp, color = c.textSecondary, modifier = Modifier.padding(top = 14.dp, bottom = 8.dp))
        MoneyField(start, { if (CardStore.acceptable(it)) start = it }, Modifier.fillMaxWidth())
        PillButton(
            "Create", onClick = { onCreate(name.ifBlank { "Debit card" }, CardStore.parse(start) ?: 0L) },
            modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
        )
    }
}

/** − or +: a round toggle; the selected one is filled. */
@Composable
private fun ModeButton(sign: String, selected: Boolean, modifier: Modifier = Modifier, popup: Boolean = false, onClick: () -> Unit) {
    val c = OneUi.colors
    Box(
        modifier.size(44.dp).clip(CircleShape)
            .then(if (selected) Modifier.background(c.accent) else Modifier.fieldBackground(popup))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(sign, fontSize = 24.sp, fontWeight = FontWeight.SemiBold, color = if (selected) Color.White else c.text, letterSpacing = 0.sp)
    }
}

/** Amount box with a fixed "$" on the right. Only digits and one dot, at most 2 decimals. */
@Composable
private fun MoneyField(
    value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier, onDone: (() -> Unit)? = null,
    focus: FocusRequester = remember { FocusRequester() }, popup: Boolean = false,
) {
    val c = OneUi.colors
    Row(
        modifier.height(44.dp).clip(CircleShape).fieldBackground(popup)
            .clickable(interactionSource = null, indication = null) { focus.requestFocus() }
            .padding(start = 16.dp, end = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("$", fontSize = 19.sp, fontWeight = FontWeight.SemiBold, color = c.textSecondary, modifier = Modifier.padding(end = 6.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text("0.00", fontSize = 19.sp, color = c.textSecondary)
            BasicTextField(
                value, onChange, singleLine = true,
                textStyle = TextStyle(fontFamily = com.tools.app.ui.theme.SamsungFont, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, color = c.text),
                cursorBrush = SolidColor(c.accent),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onDone?.invoke() }),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        }
    }
}

@Composable
private fun PlainField(
    value: String, onChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier,
    capitalize: Boolean = false, onDone: (() -> Unit)? = null, size: TextUnit = 16.sp, popup: Boolean = false,
) {
    val c = OneUi.colors
    val focus = remember { FocusRequester() }
    Box(
        modifier.height(46.dp).clip(CircleShape).fieldBackground(popup)
            .clickable(interactionSource = null, indication = null) { focus.requestFocus() }
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (value.isEmpty()) Text(placeholder, fontSize = size, color = c.textSecondary)
        BasicTextField(
            value, onChange, singleLine = true,
            textStyle = TextStyle(fontFamily = com.tools.app.ui.theme.SamsungFont, fontSize = size, color = c.text),
            cursorBrush = SolidColor(c.accent),
            keyboardOptions = KeyboardOptions(
                capitalization = if (capitalize) KeyboardCapitalization.Words else KeyboardCapitalization.Sentences,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { onDone?.invoke() }),
            modifier = Modifier.fillMaxWidth().focusRequester(focus),
        )
    }
}

/** The entry card's note: a roomy rounded box (a few lines), placeholder at the top. */
@Composable
private fun NoteField(value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier, onDone: () -> Unit) {
    val c = OneUi.colors
    val focus = remember { FocusRequester() }
    Box(
        modifier.heightIn(min = 92.dp).clip(RoundedCornerShape(22.dp)).fieldBackground(false)
            .clickable(interactionSource = null, indication = null) { focus.requestFocus() }
            .padding(horizontal = 18.dp, vertical = 13.dp),
        contentAlignment = Alignment.TopStart,
    ) {
        if (value.isEmpty()) Text("Note (optional)", fontSize = 16.sp, color = c.textSecondary)
        BasicTextField(
            value, onChange, maxLines = 4,
            textStyle = TextStyle(fontFamily = com.tools.app.ui.theme.SamsungFont, fontSize = 16.sp, color = c.text),
            cursorBrush = SolidColor(c.accent),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onDone() }),
            modifier = Modifier.fillMaxWidth().focusRequester(focus),
        )
    }
}

/** Fields on the page use the tonal fill; in popups they take the Home search bar's colour and hairline, which
 *  sit better on the popup's grey. */
@Composable
private fun Modifier.fieldBackground(popup: Boolean): Modifier {
    val c = OneUi.colors
    return if (popup) background(com.tools.app.ui.popupFieldFill(c.isDark)).border(0.6.dp, com.tools.app.ui.pillEdge(c.isDark), CircleShape)
    else background(c.tonal)
}

private fun centsText(cents: Long) = String.format(Locale.US, "%d.%02d", cents / 100, cents % 100)

private fun dayKey(t: Long): Int { val c = Calendar.getInstance().apply { timeInMillis = t }; return c.get(Calendar.YEAR) * 1000 + c.get(Calendar.DAY_OF_YEAR) }

private fun dayLabel(t: Long): String {
    val today = dayKey(System.currentTimeMillis())
    val yesterday = dayKey(System.currentTimeMillis() - 86_400_000L)
    return when (dayKey(t)) {
        today -> "Today"
        yesterday -> "Yesterday"
        else -> SimpleDateFormat(
            if (Calendar.getInstance().apply { timeInMillis = t }.get(Calendar.YEAR) == Calendar.getInstance().get(Calendar.YEAR)) "EEE, MMM d" else "MMM d, yyyy",
            Locale.getDefault(),
        ).format(Date(t))
    }
}

private fun timeText(t: Long): String = java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT, Locale.getDefault()).format(Date(t))
