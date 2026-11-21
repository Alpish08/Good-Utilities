package com.tools.app.card

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.tools.app.MainActivity
import com.tools.app.R

/**
 * Card balance widget (2×4): the last-used card's balance and name, its three newest entries, and − / + buttons that
 * open Card balance in that mode with the amount box ready (widgets can't hold a text box themselves).
 */
class CardWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val v = views(context)
        ids.forEach { manager.updateAppWidget(it, v) }
    }

    private fun views(context: Context): RemoteViews {
        val v = RemoteViews(context.packageName, R.layout.widget_card)
        val card = CardStore.load(context).current
        v.setTextViewText(R.id.balance, card?.let { CardStore.format(it.balanceCents) } ?: "Card balance")
        v.setTextViewText(R.id.name, card?.name ?: "Tap to set up a card")
        val recent = card?.entries?.sortedByDescending { it.time }?.take(3).orEmpty()
        val rows = intArrayOf(R.id.row0, R.id.row1, R.id.row2)
        val notes = intArrayOf(R.id.note0, R.id.note1, R.id.note2)
        val amounts = intArrayOf(R.id.amount0, R.id.amount1, R.id.amount2)
        rows.forEachIndexed { i, row ->
            val e = recent.getOrNull(i)
            v.setViewVisibility(row, if (e == null) View.GONE else View.VISIBLE)
            if (e != null) {
                v.setTextViewText(notes[i], e.note.ifBlank { if (e.cents < 0) "Purchase" else "Added" })
                v.setTextViewText(amounts[i], CardStore.format(e.cents, sign = true))
                v.setTextColor(amounts[i], context.getColor(if (e.cents > 0) R.color.widget_card_plus else R.color.widget_card_text))
            }
        }
        v.setViewVisibility(R.id.empty, if (card != null && recent.isEmpty()) View.VISIBLE else View.GONE)
        v.setViewVisibility(R.id.minus, if (card == null) View.GONE else View.VISIBLE)
        v.setViewVisibility(R.id.plus, if (card == null) View.GONE else View.VISIBLE)
        v.setOnClickPendingIntent(R.id.body, open(context, 0, null))
        v.setOnClickPendingIntent(R.id.minus, open(context, 1, "minus"))
        v.setOnClickPendingIntent(R.id.plus, open(context, 2, "plus"))
        return v
    }

    private fun open(context: Context, code: Int, mode: String?): PendingIntent {
        val i = Intent(context, MainActivity::class.java).setAction(MainActivity.ACTION_CARD)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        if (mode != null) i.putExtra(MainActivity.EXTRA_MODE, mode)
        return PendingIntent.getActivity(context, 200 + code, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }
}
