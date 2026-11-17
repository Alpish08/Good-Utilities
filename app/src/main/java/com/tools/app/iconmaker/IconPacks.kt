package com.tools.app.iconmaker

import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.DisplayMetrics
import org.xmlpull.v1.XmlPullParser

/** An installed icon pack (any app that offers itself to launchers as an icon theme). */
data class IconPack(val pkg: String, val label: String)

/**
 * Reads installed icon packs the way launchers do: packs announce themselves with well-known intent actions,
 * list their icons in res/xml/drawable.xml (or assets/drawable.xml), and ship them as ordinary drawables.
 */
object IconPacks {
    private val ACTIONS = listOf(
        "org.adw.launcher.THEMES", "com.novalauncher.THEME", "com.teslacoilsw.launcher.THEME",
        "com.gau.go.launcherex.theme", "org.adw.launcher.icons.ACTION_PICK_ICON", "com.anddoes.launcher.THEME",
    )

    fun installed(context: Context): List<IconPack> {
        val pm = context.packageManager
        return ACTIONS.flatMap { runCatching { pm.queryIntentActivities(Intent(it), 0) }.getOrDefault(emptyList()) }
            .map { it.activityInfo.packageName }
            .distinct()
            .mapNotNull { pkg ->
                runCatching { IconPack(pkg, pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()) }.getOrNull()
            }
            .sortedBy { it.label.lowercase() }
    }

    private fun res(context: Context, pkg: String): Resources = context.packageManager.getResourcesForApplication(pkg)

    /** Every icon the pack lists, in its own order (duplicates removed). */
    fun drawableNames(context: Context, pkg: String): List<String> {
        val r = runCatching { res(context, pkg) }.getOrNull() ?: return emptyList()
        val out = LinkedHashSet<String>()
        fun read(p: XmlPullParser) {
            while (p.next() != XmlPullParser.END_DOCUMENT) {
                if (p.eventType == XmlPullParser.START_TAG && p.name == "item") {
                    p.getAttributeValue(null, "drawable")?.takeIf { it.isNotBlank() }?.let { out += it }
                }
            }
        }
        val id = r.getIdentifier("drawable", "xml", pkg)
        if (id != 0) runCatching { r.getXml(id).use { read(it) } }
        if (out.isEmpty()) runCatching {
            r.assets.open("drawable.xml").use { s ->
                val p = android.util.Xml.newPullParser(); p.setInput(s, null); read(p)
            }
        }
        return out.toList()
    }

    /** The pack's empty folder tile, if it has one under a usual name. */
    fun defaultBase(context: Context, pkg: String): String? {
        val r = runCatching { res(context, pkg) }.getOrNull() ?: return null
        return listOf("folder", "ic_folder", "folder_background", "iconback", "iconback1", "icon_back")
            .firstOrNull { r.getIdentifier(it, "drawable", pkg) != 0 }
    }

    /** Renders an icon at the pack's own (highest) resolution, at least [minSize] px. */
    fun load(context: Context, pkg: String, name: String, minSize: Int = 0): Bitmap? = runCatching {
        val r = res(context, pkg)
        val id = r.getIdentifier(name, "drawable", pkg).takeIf { it != 0 } ?: r.getIdentifier(name, "mipmap", pkg)
        if (id == 0) return null
        val d = r.getDrawableForDensity(id, DisplayMetrics.DENSITY_XXXHIGH, null) ?: return null
        var w = d.intrinsicWidth.takeIf { it > 0 } ?: 432
        var h = d.intrinsicHeight.takeIf { it > 0 } ?: 432
        if (minSize > 0 && maxOf(w, h) < minSize) { val s = minSize.toFloat() / maxOf(w, h); w = (w * s).toInt(); h = (h * s).toInt() }
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        d.setBounds(0, 0, w, h); d.draw(Canvas(b))
        b
    }.getOrNull()
}
