package com.tools.app.widget

import com.tools.app.ui.t
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Bundle
import android.util.SizeF
import android.util.TypedValue
import android.widget.RemoteViews
import com.tools.app.MainActivity
import com.tools.app.R
import com.tools.app.ui.OneUiIcons

/**
 * "Tabs": the app's bottom tab pill as a 4×1 home-screen widget; each tab opens Good Utilities on that tab.
 *
 * The pill is drawn as one image with the app's exact proportions (5 tabs × 63 dp + 7 dp padding, 56 dp tall, 22.3 dp
 * icons, 10 sp labels) and scaled to fill the space the Home screen gives the widget. One UI draws widgets smaller
 * than apps (hsResizeRatio), so a fixed-size layout looked shrunken; filling the width brings it back to about the
 * app's own size. Five transparent tap areas sit on top.
 */
class TabsWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { manager.updateAppWidget(it, views(context, manager.getAppWidgetOptions(it))) }
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) {
        manager.updateAppWidget(id, views(context, options))
    }

    private fun views(context: Context, options: Bundle?): RemoteViews {
        val v = RemoteViews(context.packageName, R.layout.widget_tabs)
        val (w, h) = pillSize(options)
        v.setViewLayoutWidth(R.id.pill, w, TypedValue.COMPLEX_UNIT_DIP)
        v.setViewLayoutHeight(R.id.pill, h, TypedValue.COMPLEX_UNIT_DIP)
        v.setImageViewBitmap(R.id.pill_dark, draw(dark = true))
        v.setImageViewBitmap(R.id.pill_light, draw(dark = false))
        CELLS.forEachIndexed { i, cell ->
            val open = Intent(context, MainActivity::class.java)
                .setAction(MainActivity.ACTION_OPEN_TAB)
                .putExtra(MainActivity.EXTRA_TAB, i)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            v.setOnClickPendingIntent(cell, PendingIntent.getActivity(context, 100 + i, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        }
        return v
    }

    /** The largest pill with the app's proportions that fits the widget, leaving a little room at the sides. */
    @Suppress("DEPRECATION")
    private fun pillSize(o: Bundle?): Pair<Float, Float> {
        val size = o?.getParcelableArrayList<SizeF>(AppWidgetManager.OPTION_APPWIDGET_SIZES)?.firstOrNull()
        val width = size?.width ?: o?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)?.toFloat()?.takeIf { it > 0 } ?: W
        val height = size?.height ?: o?.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)?.toFloat()?.takeIf { it > 0 } ?: H
        var w = width - 16f
        var h = w * H / W
        if (h > height - 8f) { h = height - 8f; w = h * W / H }
        return w to h
    }

    /** The pill in the app's own measurements, at [K] px per dp. */
    private fun draw(dark: Boolean): Bitmap {
        val b = Bitmap.createBitmap((W * K).toInt(), (H * K).toInt(), Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val fill = if (dark) 0xDB292929.toInt() else 0xE0FFFFFF.toInt()
        val edge = if (dark) 0x0DFFFFFF else 0x0D000000
        val ink = if (dark) 0xFFD9D9DD.toInt() else 0xFF4A4A4F.toInt()
        val r = RectF(0.25f * K, 0.25f * K, W * K - 0.25f * K, H * K - 0.25f * K)
        c.drawRoundRect(r, r.height() / 2, r.height() / 2, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = fill })
        c.drawRoundRect(r, r.height() / 2, r.height() / 2, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 0.5f * K; color = edge
        })
        val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { colorFilter = PorterDuffColorFilter(ink, PorterDuff.Mode.SRC_IN) }
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ink; textSize = 10f * K; textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.create("sec", Typeface.NORMAL), 500, false)
        }
        val iconPx = (22.3f * K).toInt()
        TABS.forEachIndexed { i, (label, icon) ->
            val cx = (7f + 63f * i + 31.5f) * K
            c.drawBitmap(VectorBitmap.render(icon(), iconPx), cx - iconPx / 2f, 8.35f * K, iconPaint)
            c.drawText(label, cx, 43f * K, text)
        }
        return b
    }

    private companion object {
        const val W = 7f + 63f * 5 + 7f
        const val H = 56f
        const val K = 4f
        val TABS get() = listOf(
            t(R.string.home_media) to { OneUiIcons.TabMedia }, t(R.string.home_files) to { OneUiIcons.TabFiles }, t(R.string.home_images) to { OneUiIcons.TabImages },
            t(R.string.home_device) to { OneUiIcons.TabDevice }, t(R.string.home_misc) to { OneUiIcons.TabMisc },
        )
        val CELLS = intArrayOf(R.id.tab0, R.id.tab1, R.id.tab2, R.id.tab3, R.id.tab4)
    }
}
