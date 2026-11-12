package com.tools.app.japanese

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.JsonReader
import android.util.JsonToken
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FilterInputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

data class Sense(val pos: List<String>, val glosses: List<String>)

data class Entry(val kanji: List<String>, val kana: List<String>, val common: Boolean, val senses: List<Sense>)

/** Offline JMdict (EDRDG, CC BY-SA 4.0) in a compact SQLite file: forms → senses. */
object Jmdict {
    private const val FILE = "jmdict.db"
    private var db: SQLiteDatabase? = null

    fun file(context: Context) = File(context.filesDir, FILE)
    fun installed(context: Context) = file(context).length() > 0

    @Synchronized
    private fun open(context: Context): SQLiteDatabase? {
        db?.let { if (it.isOpen) return it }
        val f = file(context)
        if (f.length() == 0L) return null
        return runCatching { SQLiteDatabase.openDatabase(f.path, null, SQLiteDatabase.OPEN_READONLY) }.getOrNull().also { db = it }
    }

    @Synchronized
    fun close() { db?.close(); db = null }

    /** Number of entries and file size in bytes. */
    fun stats(context: Context): Pair<Int, Long>? {
        val d = open(context) ?: return null
        val n = d.rawQuery("SELECT COUNT(*) FROM entry", null).use { if (it.moveToFirst()) it.getInt(0) else 0 }
        return n to file(context).length()
    }

    /** Best entry for a word: by base form first, then the surface as written. Blocking: call off the main thread. */
    fun lookup(context: Context, word: Word): Entry? {
        val d = open(context) ?: return null
        for (form in listOf(word.base, word.surface).distinct()) {
            val e = lookupForm(d, form, word, if (form == word.surface || word.base == word.surface) word.reading else "")
            if (e != null) return e
        }
        // Kana-only words: the reading may be the dictionary form.
        if (word.reading.isNotEmpty() && word.reading != word.base && word.reading != word.surface && word.posGroup != PosGroup.Particle) {
            lookupForm(d, word.reading, word, "")?.let { return it }
        }
        return null
    }

    private fun lookupForm(d: SQLiteDatabase, form: String, word: Word, reading: String): Entry? {
        val ids = ArrayList<Long>()
        d.rawQuery("SELECT entry FROM form WHERE form = ? LIMIT 25", arrayOf(form)).use { while (it.moveToNext()) ids += it.getLong(0) }
        if (ids.isEmpty()) return null
        var best: Entry? = null
        var bestScore = Int.MIN_VALUE
        for (id in ids.distinct()) {
            val e = load(d, id) ?: continue
            var score = 0
            if (e.common) score += 2
            if (reading.isNotEmpty() && reading in e.kana) score += 4
            if (e.senses.any { s -> s.pos.any { posMatches(word.posGroup, it) } }) score += 5
            if (score > bestScore) { best = e; bestScore = score }
        }
        return best
    }

    private fun posMatches(g: PosGroup, tag: String) = when (g) {
        PosGroup.Noun -> tag == "n" || tag.startsWith("n-")
        PosGroup.Verb -> tag.startsWith("v")
        PosGroup.Adjective -> tag.startsWith("adj") || tag == "adv-to" || tag == "n"
        PosGroup.Adverb -> tag.startsWith("adv")
        PosGroup.Particle -> tag == "prt"
        PosGroup.Auxiliary -> tag.startsWith("aux")
        PosGroup.Conjunction -> tag == "conj"
        PosGroup.Pronoun -> tag == "pn"
        PosGroup.Interjection -> tag == "int"
        PosGroup.Prefix -> tag == "pref"
        PosGroup.Suffix -> tag == "suf" || tag.startsWith("n-suf")
        else -> false
    }

    private fun load(d: SQLiteDatabase, id: Long): Entry? {
        var kanji = emptyList<String>(); var kana = emptyList<String>(); var common = false
        d.rawQuery("SELECT kanji, kana, common FROM entry WHERE id = ?", arrayOf(id.toString())).use {
            if (!it.moveToFirst()) return null
            kanji = it.getString(0).split('|').filter(String::isNotEmpty)
            kana = it.getString(1).split('|').filter(String::isNotEmpty)
            common = it.getInt(2) == 1
        }
        val senses = ArrayList<Sense>()
        d.rawQuery("SELECT pos, gloss FROM sense WHERE entry = ? ORDER BY rowid", arrayOf(id.toString())).use {
            while (it.moveToNext()) senses += Sense(it.getString(0).split('|').filter(String::isNotEmpty), it.getString(1).split('|').filter(String::isNotEmpty))
        }
        return Entry(kanji, kana, common, senses)
    }

    /** Short plain-English names for JMdict part-of-speech codes. */
    fun posName(code: String): String = when {
        code == "n" -> "noun"
        code == "pn" -> "pronoun"
        code == "prt" -> "particle"
        code == "adv" || code == "adv-to" -> "adverb"
        code == "adj-i" || code == "adj-ix" -> "i-adjective"
        code == "adj-na" -> "na-adjective"
        code == "adj-no" -> "no-adjective"
        code == "adj-t" || code == "adj-f" || code == "adj-pn" -> "adjective"
        code == "conj" -> "conjunction"
        code == "int" -> "interjection"
        code == "exp" -> "expression"
        code == "ctr" -> "counter"
        code == "pref" -> "prefix"
        code == "suf" -> "suffix"
        code == "num" -> "numeric"
        code == "cop" -> "copula"
        code == "unc" -> ""
        code.startsWith("aux") -> "auxiliary"
        code == "vs" || code == "vs-i" || code == "vs-s" -> "suru verb"
        code == "v1" || code == "v1-s" -> "ichidan verb"
        code.startsWith("v5") -> "godan verb"
        code == "vk" -> "kuru verb"
        code.startsWith("vi") -> "intransitive verb"
        code.startsWith("vt") -> "transitive verb"
        code.startsWith("v") -> "verb"
        code.startsWith("n-") -> "noun"
        else -> code
    }

    fun posLabel(codes: List<String>): String = codes.map(::posName).filter { it.isNotEmpty() }.distinct().joinToString(", ")

    // --- building the database -----------------------------------------------------------------------

    /** Streams the .json.zip and writes the compact DB. [onBytes] reports compressed bytes consumed. */
    internal fun build(context: Context, zip: InputStream, onBytes: () -> Long, onProgress: (Long) -> Unit, active: () -> Unit) {
        close()
        val tmp = File(context.filesDir, "$FILE.tmp")
        tmp.delete()
        val d = SQLiteDatabase.openOrCreateDatabase(tmp, null)
        try {
            // journal_mode returns a row, so it has to go through rawQuery (execSQL refuses it).
            d.rawQuery("PRAGMA journal_mode = OFF", null).use { it.moveToFirst() }
            d.execSQL("PRAGMA synchronous = OFF")
            d.execSQL("CREATE TABLE entry (id INTEGER PRIMARY KEY, kanji TEXT NOT NULL, kana TEXT NOT NULL, common INTEGER NOT NULL)")
            d.execSQL("CREATE TABLE form (form TEXT NOT NULL, entry INTEGER NOT NULL)")
            d.execSQL("CREATE TABLE sense (entry INTEGER NOT NULL, pos TEXT NOT NULL, gloss TEXT NOT NULL)")
            d.beginTransaction()
            val insEntry = d.compileStatement("INSERT INTO entry VALUES (?,?,?,?)")
            val insForm = d.compileStatement("INSERT INTO form VALUES (?,?)")
            val insSense = d.compileStatement("INSERT INTO sense VALUES (?,?,?)")
            val z = ZipInputStream(zip)
            var found = false
            var n = 0
            while (true) {
                val ze = z.nextEntry ?: break
                if (!ze.name.endsWith(".json")) continue
                found = true
                JsonReader(InputStreamReader(z, Charsets.UTF_8).buffered(1 shl 16)).use { r ->
                    r.beginObject()
                    while (r.hasNext()) {
                        if (r.nextName() == "words") {
                            r.beginArray()
                            while (r.hasNext()) {
                                readWord(r, insEntry, insForm, insSense)
                                if (++n % 2000 == 0) { active(); onProgress(onBytes()) }
                            }
                            r.endArray()
                        } else r.skipValue()
                    }
                    r.endObject()
                }
                break
            }
            if (!found) error("The download did not contain a dictionary file")
            d.execSQL("CREATE INDEX form_idx ON form(form)")
            d.execSQL("CREATE INDEX sense_idx ON sense(entry)")
            d.setTransactionSuccessful()
            d.endTransaction()
            d.execSQL("VACUUM")
            d.close()
            val dest = file(context)
            dest.delete()
            if (!tmp.renameTo(dest)) error("Could not save the dictionary")
        } catch (t: Throwable) {
            runCatching { if (d.inTransaction()) d.endTransaction() }
            runCatching { d.close() }
            tmp.delete()
            throw t
        }
    }

    private class Form(val text: String, val common: Boolean)

    private fun readForms(r: JsonReader): List<Form> {
        val out = ArrayList<Form>()
        r.beginArray()
        while (r.hasNext()) {
            var text = ""; var common = false
            r.beginObject()
            while (r.hasNext()) when (r.nextName()) {
                "text" -> text = r.nextString()
                "common" -> common = r.nextBoolean()
                else -> r.skipValue()
            }
            r.endObject()
            if (text.isNotEmpty()) out += Form(text, common)
        }
        r.endArray()
        return out
    }

    private fun readStrings(r: JsonReader): List<String> {
        val out = ArrayList<String>()
        r.beginArray(); while (r.hasNext()) out += r.nextString(); r.endArray()
        return out
    }

    private fun readWord(r: JsonReader, insEntry: android.database.sqlite.SQLiteStatement, insForm: android.database.sqlite.SQLiteStatement, insSense: android.database.sqlite.SQLiteStatement) {
        var id = 0L
        var kanji = emptyList<Form>(); var kana = emptyList<Form>()
        val senses = ArrayList<Pair<List<String>, List<String>>>()
        r.beginObject()
        while (r.hasNext()) when (r.nextName()) {
            "id" -> id = r.nextString().toLong()
            "kanji" -> kanji = readForms(r)
            "kana" -> kana = readForms(r)
            "sense" -> {
                var lastPos = emptyList<String>()
                r.beginArray()
                while (r.hasNext()) {
                    var pos = emptyList<String>(); val gl = ArrayList<String>()
                    r.beginObject()
                    while (r.hasNext()) when (r.nextName()) {
                        "partOfSpeech" -> pos = readStrings(r)
                        "gloss" -> {
                            r.beginArray()
                            while (r.hasNext()) {
                                r.beginObject()
                                while (r.hasNext()) if (r.nextName() == "text") gl += r.nextString() else r.skipValue()
                                r.endObject()
                            }
                            r.endArray()
                        }
                        else -> r.skipValue()
                    }
                    r.endObject()
                    if (pos.isEmpty()) pos = lastPos else lastPos = pos
                    if (gl.isNotEmpty()) senses += pos to gl
                }
                r.endArray()
            }
            else -> r.skipValue()
        }
        r.endObject()
        if (id == 0L || senses.isEmpty()) return
        insEntry.bindLong(1, id)
        insEntry.bindString(2, kanji.joinToString("|") { it.text })
        insEntry.bindString(3, kana.joinToString("|") { it.text })
        insEntry.bindLong(4, if (kanji.any { it.common } || kana.any { it.common }) 1 else 0)
        insEntry.executeInsert()
        for (f in (kanji + kana).map { it.text }.distinct()) {
            insForm.bindString(1, f); insForm.bindLong(2, id); insForm.executeInsert()
        }
        for ((pos, gl) in senses) {
            insSense.bindLong(1, id); insSense.bindString(2, pos.joinToString("|")); insSense.bindString(3, gl.joinToString("|")); insSense.executeInsert()
        }
    }
}

/** Counts the bytes read, so the download can report progress while it is being parsed. */
private class CountingStream(input: InputStream) : FilterInputStream(input) {
    @Volatile var count = 0L
    override fun read(): Int = super.read().also { if (it >= 0) count++ }
    override fun read(b: ByteArray, off: Int, len: Int): Int = super.read(b, off, len).also { if (it > 0) count += it }
}

enum class DictVariant(val label: String) { Full("Everything"), Common("Common words") }

class DictAsset(val name: String, val size: Long, val url: String)

sealed interface DictState {
    data object Idle : DictState
    data object Checking : DictState
    class Working(val progress: Float, val stage: String) : DictState
    class Failed(val message: String) : DictState
}

/** Download + import of JMdict. A singleton so leaving the page doesn't cancel it. */
object DictJobs {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private val _state = MutableStateFlow<DictState>(DictState.Idle)
    val state: StateFlow<DictState> = _state.asStateFlow()
    /** Bumped whenever the DB was installed or removed, so UI re-reads the status. */
    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> = _version.asStateFlow()
    private val _assets = MutableStateFlow<Map<DictVariant, DictAsset>>(emptyMap())
    val assets: StateFlow<Map<DictVariant, DictAsset>> = _assets.asStateFlow()

    private const val API = "https://api.github.com/repos/scriptin/jmdict-simplified/releases/latest"

    private fun connect(url: String): HttpURLConnection = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 15000; readTimeout = 30000; instanceFollowRedirects = true
        setRequestProperty("User-Agent", "GoodUtilities")
        setRequestProperty("Accept", "application/vnd.github+json")
    }

    /** Looks up the latest release (small JSON) so the card can say how big the download is. */
    fun refreshInfo() {
        if (_assets.value.isNotEmpty()) return
        scope.launch { runCatching { fetchAssets() } }
    }

    private fun fetchAssets(): Map<DictVariant, DictAsset> {
        val c = connect(API)
        try {
            if (c.responseCode != 200) error("GitHub answered ${c.responseCode}")
            val json = JSONObject(c.inputStream.bufferedReader().readText())
            val arr = json.getJSONArray("assets")
            val map = HashMap<DictVariant, DictAsset>()
            for (i in 0 until arr.length()) {
                val a = arr.getJSONObject(i)
                val name = a.getString("name")
                val asset = DictAsset(name, a.getLong("size"), a.getString("browser_download_url"))
                if (Regex("^jmdict-eng-common-.*\\.json\\.zip$").matches(name)) map[DictVariant.Common] = asset
                else if (Regex("^jmdict-eng-\\d.*\\.json\\.zip$").matches(name)) map[DictVariant.Full] = asset
            }
            if (map.isEmpty()) error("No English dictionary in the latest release")
            _assets.value = map
            return map
        } finally { c.disconnect() }
    }

    fun start(context: Context, variant: DictVariant) {
        if (job?.isActive == true) return
        val app = context.applicationContext
        _state.value = DictState.Checking
        job = scope.launch {
            try {
                val asset = (_assets.value.takeIf { it.isNotEmpty() } ?: fetchAssets())[variant]
                    ?: error("This dictionary isn't in the latest release")
                val c = connect(asset.url)
                try {
                    if (c.responseCode != 200) error("Download failed (${c.responseCode})")
                    val total = (c.contentLengthLong.takeIf { it > 0 } ?: asset.size).toFloat()
                    val counting = CountingStream(c.inputStream.buffered(1 shl 16))
                    _state.value = DictState.Working(0f, "Downloading and preparing…")
                    Jmdict.build(app, counting, { counting.count }, { b ->
                        _state.value = DictState.Working((b / total).coerceIn(0f, 0.99f), "Downloading and preparing…")
                    }, { ensureActive() })
                } finally { c.disconnect() }
                _state.value = DictState.Idle
                _version.value++
            } catch (e: CancellationException) {
                _state.value = DictState.Idle
            } catch (t: Throwable) {
                _state.value = DictState.Failed(t.message ?: t.javaClass.simpleName)
            }
        }
    }

    fun cancel() { job?.cancel() }

    fun delete(context: Context) {
        scope.launch {
            Jmdict.close()
            Jmdict.file(context).delete()
            _version.value++
        }
    }
}
