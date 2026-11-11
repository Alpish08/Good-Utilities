package com.tools.app.reader

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException

enum class Kind(val mime: String) {
    Pdf("application/pdf"),
    Docx("application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
    Text("text/plain"),
    Markdown("text/markdown"),
}

val SupportedMimes = arrayOf(Kind.Pdf.mime, Kind.Docx.mime, "text/plain", "text/markdown", "text/x-markdown")

/** A document the reader was asked to show. [key] is stable for the same URI and size. */
class DocInfo(val uri: Uri, val name: String, val size: Long, val kind: Kind) {
    val key: String = (uri.toString() + "|" + size).hashCode().toUInt().toString(16)

    companion object {
        /** Blocking: reads the display name / size and sniffs the file type. */
        fun resolve(context: Context, uri: Uri): DocInfo {
            var name: String? = null
            var size = -1L
            if (uri.scheme == "file") {
                val f = File(uri.path ?: "")
                name = f.name; size = f.length()
            } else runCatching {
                context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use {
                    if (it.moveToFirst()) {
                        name = it.getString(0)
                        if (!it.isNull(1)) size = it.getLong(1)
                    }
                }
            }
            val n = name ?: uri.lastPathSegment?.substringAfterLast('/') ?: "Document"
            return DocInfo(uri, n, size, kindOf(context, uri, n))
        }

        private fun kindOf(context: Context, uri: Uri, name: String): Kind {
            val ext = name.substringAfterLast('.', "").lowercase()
            when (ext) {
                "pdf" -> return Kind.Pdf
                "docx" -> return Kind.Docx
                "md", "markdown" -> return Kind.Markdown
                "txt" -> return Kind.Text
            }
            when (runCatching { context.contentResolver.getType(uri) }.getOrNull()) {
                Kind.Pdf.mime -> return Kind.Pdf
                Kind.Docx.mime -> return Kind.Docx
                "text/markdown", "text/x-markdown" -> return Kind.Markdown
            }
            val head = ByteArray(5)
            val n = try {
                (if (uri.scheme == "file") File(uri.path ?: "").inputStream() else context.contentResolver.openInputStream(uri))?.use { it.read(head) } ?: 0
            } catch (e: IOException) { 0 } catch (e: SecurityException) { 0 }
            if (n >= 5 && String(head, Charsets.ISO_8859_1) == "%PDF-") return Kind.Pdf
            if (n >= 2 && head[0] == 'P'.code.toByte() && head[1] == 'K'.code.toByte()) return Kind.Docx
            return Kind.Text
        }
    }
}

class Recent(
    val uri: String, val name: String, val size: Long, val kind: Kind,
    val pages: Int, val page: Int, val opened: Long,
)

/** Where you were in a document. */
class Position(val page: Int, val frac: Float, val zoom: Float)

/** Recents, last positions and the viewer's remembered settings, all in SharedPreferences. */
object ReaderStore {
    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences("reader", Context.MODE_PRIVATE)
    private const val MAX_RECENTS = 40

    fun recents(c: Context): List<Recent> {
        val raw = prefs(c).getString("recents", null) ?: return emptyList()
        return runCatching {
            val a = JSONArray(raw)
            (0 until a.length()).map {
                val o = a.getJSONObject(it)
                Recent(
                    o.getString("u"), o.getString("n"), o.optLong("s", -1),
                    runCatching { Kind.valueOf(o.optString("k", "Pdf")) }.getOrDefault(Kind.Pdf),
                    o.optInt("c", 0), o.optInt("p", 0), o.optLong("t", 0),
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun writeRecents(c: Context, list: List<Recent>) {
        val a = JSONArray()
        list.forEach {
            a.put(JSONObject().put("u", it.uri).put("n", it.name).put("s", it.size).put("k", it.kind.name)
                .put("c", it.pages).put("p", it.page).put("t", it.opened))
        }
        prefs(c).edit().putString("recents", a.toString()).apply()
    }

    @Synchronized
    fun touch(c: Context, info: DocInfo, pages: Int, page: Int) {
        val now = System.currentTimeMillis()
        val rest = recents(c).filterNot { it.uri == info.uri.toString() }
        writeRecents(c, (listOf(Recent(info.uri.toString(), info.name, info.size, info.kind, pages, page, now)) + rest).take(MAX_RECENTS))
    }

    @Synchronized
    fun remove(c: Context, uri: String) {
        writeRecents(c, recents(c).filterNot { it.uri == uri })
        runCatching { c.contentResolver.releasePersistableUriPermission(Uri.parse(uri), Intent_FLAG_READ) }
    }

    private const val Intent_FLAG_READ = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION

    fun position(c: Context, key: String): Position? {
        val s = prefs(c).getString("pos_$key", null) ?: return null
        val p = s.split(',')
        return runCatching { Position(p[0].toInt(), p[1].toFloat(), p[2].toFloat()) }.getOrNull()
    }

    @Synchronized
    fun savePosition(c: Context, info: DocInfo, pages: Int, pos: Position) {
        prefs(c).edit().putString("pos_${info.key}", "${pos.page},${pos.frac},${pos.zoom}").apply()
        val list = recents(c)
        val i = list.indexOfFirst { it.uri == info.uri.toString() }
        if (i >= 0) writeRecents(c, list.toMutableList().also { it[i] = Recent(it[i].uri, it[i].name, it[i].size, it[i].kind, pages, pos.page, it[i].opened) })
    }

    // ---- settings ----
    class Settings(private val c: Context) {
        private val p = prefs(c)
        var night by mutableStateOf(p.getBoolean("night", false)); private set
        var keepOn by mutableStateOf(p.getBoolean("keepOn", false)); private set
        var continuous by mutableStateOf(p.getBoolean("continuous", true)); private set
        var fontSize by mutableFloatStateOf(p.getFloat("fontSize", 17f)); private set
        fun changeNight(v: Boolean) { night = v; p.edit().putBoolean("night", v).apply() }
        fun changeKeepOn(v: Boolean) { keepOn = v; p.edit().putBoolean("keepOn", v).apply() }
        fun changeContinuous(v: Boolean) { continuous = v; p.edit().putBoolean("continuous", v).apply() }
        fun changeFontSize(v: Float) { fontSize = v.coerceIn(12f, 32f); p.edit().putFloat("fontSize", fontSize).apply() }
    }

    fun cacheDir(c: Context): File = File(c.cacheDir, "reader").apply { mkdirs() }

    /** Drops cached DOCX conversions and copies that have not been touched for two weeks. */
    fun trimCache(c: Context) {
        val limit = System.currentTimeMillis() - 14L * 24 * 3600 * 1000
        cacheDir(c).listFiles()?.forEach { if (it.lastModified() < limit) it.delete() }
    }
}
