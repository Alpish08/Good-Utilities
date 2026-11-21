package com.tools.app.card

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale
import java.util.UUID

/** One logged amount: [cents] is negative for money spent, positive for money added. */
data class Entry(val id: String, val cents: Long, val note: String, val time: Long)

data class Card(val id: String, val name: String, val startCents: Long, val entries: List<Entry>, val primary: Boolean = false) {
    val balanceCents get() = startCents + entries.sumOf { it.cents }
}

data class Cards(val cards: List<Card> = emptyList(), val currentId: String? = null) {
    val current get() = cards.firstOrNull { it.id == currentId } ?: cards.firstOrNull()
    /** The card shown on Home and in the widget: the one marked primary (older data: the first card). */
    val primary get() = cards.firstOrNull { it.primary } ?: cards.firstOrNull()
}

/**
 * Card balance: expenses and payments per card, in exact cents (no floating-point drift). Kept in a small private
 * JSON file; every change also refreshes the widget.
 */
object CardStore {
    private val _state = MutableStateFlow(Cards())
    val state: StateFlow<Cards> = _state
    private var loaded = false

    /** Set by the widget's − / + buttons: the mode Card balance opens in (with the keyboard up). */
    val pendingMode = MutableStateFlow<Boolean?>(null)

    private fun file(c: Context) = File(c.applicationContext.filesDir, "cards.json")

    @Synchronized
    fun load(context: Context): Cards {
        if (loaded) return _state.value
        loaded = true
        val f = file(context)
        _state.value = if (!f.exists()) Cards() else runCatching { parse(JSONObject(f.readText())) }.getOrDefault(Cards())
        return _state.value
    }

    private fun parse(o: JSONObject): Cards {
        val cards = o.optJSONArray("cards") ?: JSONArray()
        return Cards(
            (0 until cards.length()).map { i ->
                val c = cards.getJSONObject(i)
                val es = c.optJSONArray("entries") ?: JSONArray()
                Card(
                    c.getString("id"), c.getString("name"), c.getLong("start"),
                    (0 until es.length()).map { j ->
                        val e = es.getJSONObject(j)
                        Entry(e.getString("id"), e.getLong("cents"), e.optString("note"), e.getLong("time"))
                    },
                    c.optBoolean("primary", false),
                )
            },
            o.optString("current").ifBlank { null },
        )
    }

    private fun save(context: Context, s: Cards) {
        _state.value = s
        val o = JSONObject().put("current", s.currentId ?: "").put("cards", JSONArray().apply {
            s.cards.forEach { c ->
                put(JSONObject().put("id", c.id).put("name", c.name).put("start", c.startCents).put("primary", c.primary).put("entries", JSONArray().apply {
                    c.entries.forEach { e -> put(JSONObject().put("id", e.id).put("cents", e.cents).put("note", e.note).put("time", e.time)) }
                }))
            }
        })
        val f = file(context)
        val tmp = File(f.parentFile, f.name + ".tmp")
        tmp.writeText(o.toString())
        tmp.renameTo(f)
        refreshWidgets(context)
    }

    private fun update(context: Context, f: (Cards) -> Cards) = synchronized(this) { save(context, f(load(context))) }

    private fun mapCurrent(context: Context, f: (Card) -> Card) = update(context) { s ->
        val cur = s.current ?: return@update s
        s.copy(cards = s.cards.map { if (it.id == cur.id) f(it) else it }, currentId = cur.id)
    }

    /** Adds a card and switches to it. The first card is always primary; a new primary takes over from the old one. */
    fun addCard(context: Context, name: String, startCents: Long, primary: Boolean = true) = update(context) { s ->
        val isPrimary = primary || s.cards.isEmpty()
        val card = Card(UUID.randomUUID().toString(), name.trim().ifBlank { "Card" }, startCents, emptyList(), isPrimary)
        val others = if (isPrimary) s.cards.map { it.copy(primary = false) } else s.cards
        s.copy(cards = others + card, currentId = card.id)
    }

    fun makePrimary(context: Context) = update(context) { s ->
        val cur = s.current ?: return@update s
        s.copy(cards = s.cards.map { it.copy(primary = it.id == cur.id) }, currentId = cur.id)
    }

    fun switchTo(context: Context, id: String) = update(context) { it.copy(currentId = id) }
    fun rename(context: Context, name: String) = mapCurrent(context) { it.copy(name = name.trim().ifBlank { it.name }) }
    fun setStart(context: Context, cents: Long) = mapCurrent(context) { it.copy(startCents = cents) }

    fun deleteCurrent(context: Context) = update(context) { s ->
        val cur = s.current ?: return@update s
        val rest = s.cards.filter { it.id != cur.id }
        s.copy(cards = rest, currentId = rest.firstOrNull()?.id)
    }

    fun log(context: Context, cents: Long, note: String) = mapCurrent(context) {
        it.copy(entries = it.entries + Entry(UUID.randomUUID().toString(), cents, note.trim(), System.currentTimeMillis()))
    }

    fun editEntry(context: Context, e: Entry) = mapCurrent(context) { c -> c.copy(entries = c.entries.map { if (it.id == e.id) e else it }) }
    fun deleteEntry(context: Context, id: String) = mapCurrent(context) { c -> c.copy(entries = c.entries.filter { it.id != id }) }

    /** Puts a deleted entry back where it was (by time). */
    fun restoreEntry(context: Context, e: Entry) = mapCurrent(context) { c -> c.copy(entries = (c.entries + e).sortedBy { it.time }) }

    fun refreshWidgets(context: Context) {
        val m = AppWidgetManager.getInstance(context)
        val ids = m.getAppWidgetIds(ComponentName(context, CardWidget::class.java))
        if (ids.isEmpty()) return
        context.sendBroadcast(
            Intent(context, CardWidget::class.java).setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids),
        )
    }

    // ---- money text ---------------------------------------------------------------------------------------------

    /** 123456 → "$1,234.56"; negative → "−$1,234.56". */
    fun format(cents: Long, sign: Boolean = false): String {
        val abs = kotlin.math.abs(cents)
        val body = "$" + String.format(Locale.US, "%,d.%02d", abs / 100, abs % 100)
        return when {
            cents < 0 -> "−$body"
            sign && cents > 0 -> "+$body"
            else -> body
        }
    }

    /** "12.4" → 1240; null if it isn't a positive amount with at most 2 decimals. */
    fun parse(text: String): Long? {
        val t = text.trim().replace(",", "")
        if (!Regex("""\d{0,7}(\.\d{0,2})?""").matches(t) || t.isEmpty() || t == ".") return null
        val whole = t.substringBefore('.').ifEmpty { "0" }.toLong()
        val frac = t.substringAfter('.', "").padEnd(2, '0').take(2).ifEmpty { "00" }.toLong()
        return whole * 100 + frac
    }

    /** Typing filter for the amount box: digits and one dot, at most 2 decimals. */
    fun acceptable(text: String) = Regex("""\d{0,7}(\.\d{0,2})?""").matches(text)
}
