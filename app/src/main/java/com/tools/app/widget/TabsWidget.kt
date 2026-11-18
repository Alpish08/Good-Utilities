package com.tools.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.SizeF
import android.widget.RemoteViews
import com.tools.app.MainActivity
import com.tools.app.R
import com.tools.app.ui.OneUiIcons
import kotlin.math.roundToInt

/**
 * "Tabs": the app's bottom tab pill as a 4×1 home-screen widget. Each tab opens Good Utilities on that tab.
 * Wide enough, the tabs sit 66 dp apart like in the app; narrower, they share the space evenly.
 */
class TabsWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { manager.updateAppWidget(it, views(context)) }
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) {
        manager.updateAppWidget(id, views(context))
    }

    private fun views(context: Context): RemoteViews {
        val regular = build(context, R.layout.widget_tabs)
        val compact = build(context, R.layout.widget_tabs_compact)
        // 5 tabs × 66 dp + the pill's 7 dp padding on each side.
        return RemoteViews(mapOf(SizeF(10f, 40f) to compact, SizeF(344f, 40f) to regular))
    }

    private fun build(context: Context, layout: Int): RemoteViews {
        val v = RemoteViews(context.packageName, layout)
        val px = (22.3f * context.resources.displayMetrics.density).roundToInt()
        TABS.forEachIndexed { i, (label, icon) ->
            v.setImageViewBitmap(ICONS[i], VectorBitmap.render(icon(), px))
            v.setTextViewText(LABELS[i], label)
            val open = Intent(context, MainActivity::class.java)
                .setAction(MainActivity.ACTION_OPEN_TAB)
                .putExtra(MainActivity.EXTRA_TAB, i)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            v.setOnClickPendingIntent(
                CELLS[i],
                PendingIntent.getActivity(context, 100 + i, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT),
            )
        }
        return v
    }

    private companion object {
        val TABS = listOf(
            "Media" to { OneUiIcons.TabMedia }, "Files" to { OneUiIcons.TabFiles }, "Images" to { OneUiIcons.TabImages },
            "Device" to { OneUiIcons.TabDevice }, "Misc" to { OneUiIcons.TabMisc },
        )
        val CELLS = intArrayOf(R.id.tab0, R.id.tab1, R.id.tab2, R.id.tab3, R.id.tab4)
        val ICONS = intArrayOf(R.id.icon0, R.id.icon1, R.id.icon2, R.id.icon3, R.id.icon4)
        val LABELS = intArrayOf(R.id.label0, R.id.label1, R.id.label2, R.id.label3, R.id.label4)
    }
}
