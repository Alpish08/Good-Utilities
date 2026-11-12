package com.tools.app.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.widget.RemoteViews
import com.tools.app.R

/**
 * "Spacer": a fully transparent home-screen widget with no tap action, any size from 1×1 (default 4×1).
 * It only reserves empty space, so swipes on it still reach the home screen.
 */
class SpacerWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val views = RemoteViews(context.packageName, R.layout.widget_spacer)
        ids.forEach { manager.updateAppWidget(it, views) }
    }
}
