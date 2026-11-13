package com.tools.app.applock

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.drawable.toBitmap
import androidx.palette.graphics.Palette
import com.tools.app.R

/**
 * The lock card shown behind Samsung's biometric sheet: an opaque, very dark gradient tinted with
 * the app's icon colour (from the top), with the icon, name and "Locked" in the upper third (clear of the sheet).
 * The instant cover and LockActivity build the same card, so the hand-off between them is invisible.
 */
object LockCard {
    const val FADE_IN_MS = 150L
    /** Fade-in when the cover starts at the tap on a Home screen icon (over the launcher's opening animation). */
    const val TAP_FADE_IN_MS = 220L
    const val FADE_OUT_MS = 170L

    private val tints = HashMap<String, Int>()

    /**
     * Centre of Samsung's sheet button ("Use PIN") above the screen's bottom edge. Its card is
     * anchored to the bottom, so the button stays put whatever the title; measured on One UI 8.5.
     */
    private const val SHEET_BUTTON_DP = 303f

    /**
     * While the cover hides Samsung's sheet: "Use PIN" drawn right over Samsung's own (now hidden)
     * button, so a tap there still reaches it, plus a line for "not recognised". Returns that line.
     */
    fun addPromptHints(root: FrameLayout): TextView {
        val context = root.context
        val dp = context.resources.displayMetrics.density
        val sec = Typeface.create("sec", Typeface.NORMAL)
        val button = TextView(context).apply {
            text = "Use PIN"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            typeface = Typeface.create(sec, 600, false)
            gravity = Gravity.CENTER
        }
        root.addView(button, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, (26 * dp).toInt()).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            bottomMargin = ((SHEET_BUTTON_DP - 13) * dp).toInt()
        })
        val status = TextView(context).apply {
            setTextColor(Color.argb(170, 255, 255, 255))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            typeface = Typeface.create(sec, 500, false)
            gravity = Gravity.CENTER
        }
        root.addView(status, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.BOTTOM
            bottomMargin = ((SHEET_BUTTON_DP + 40) * dp).toInt()
        })
        return status
    }

    /** Dark tint derived from the app icon (cached; ~1 ms the first time). */
    private fun tint(context: Context, pkg: String): Int = tints.getOrPut(pkg) {
        runCatching {
            val bmp = context.packageManager.getApplicationIcon(pkg).toBitmap(64, 64)
            val p = Palette.from(bmp).generate()
            val base = (p.vibrantSwatch ?: p.dominantSwatch)?.rgb ?: Color.DKGRAY
            ColorUtils.blendARGB(base, Color.BLACK, 0.78f)
        }.getOrDefault(Color.rgb(24, 24, 24))
    }

    fun build(context: Context, pkg: String): View {
        val pm = context.packageManager
        val info = runCatching { pm.getApplicationInfo(pkg, 0) }.getOrNull()
        val dp = context.resources.displayMetrics.density
        val screenH = context.resources.displayMetrics.heightPixels
        val sec = Typeface.create("sec", Typeface.NORMAL)

        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        column.addView(ImageView(context).apply {
            info?.let { setImageDrawable(pm.getApplicationIcon(it)) }
        }, LinearLayout.LayoutParams((80 * dp).toInt(), (80 * dp).toInt()))
        column.addView(TextView(context).apply {
            text = info?.let { pm.getApplicationLabel(it) } ?: ""
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f)
            typeface = Typeface.create(sec, 700, false)
            maxLines = 1; ellipsize = TextUtils.TruncateAt.END
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = (20 * dp).toInt()
        })
        column.addView(TextView(context).apply {
            text = "Locked"
            setTextColor(Color.argb(170, 255, 255, 255))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            typeface = Typeface.create(sec, 500, false)
            setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_lock_small, 0, 0, 0)
            compoundDrawableTintList = android.content.res.ColorStateList.valueOf(Color.argb(170, 255, 255, 255))
            compoundDrawablePadding = (6 * dp).toInt()
            gravity = Gravity.CENTER_VERTICAL
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = (6 * dp).toInt()
        })

        val tint = tint(context, pkg)
        return FrameLayout(context).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM, // colour at the top, black at the bottom
                intArrayOf(tint, ColorUtils.blendARGB(tint, Color.BLACK, 0.55f), Color.BLACK),
            )
            addView(column, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.TOP
                topMargin = (screenH * 0.2f).toInt()
            })
        }
    }
}
