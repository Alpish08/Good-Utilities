package com.tools.app.japanese

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Small settings for the Japanese reader (shared by the main page and the reader activity). */
object JpPrefs {
    private var prefs: android.content.SharedPreferences? = null
    var romaji by mutableStateOf(false)
    var wifiOnly by mutableStateOf(true)
    var variant by mutableStateOf(DictVariant.Full)

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences("japanese", Context.MODE_PRIVATE)
        prefs = p
        romaji = p.getBoolean("romaji", false)
        wifiOnly = p.getBoolean("wifiOnly", true)
        variant = runCatching { DictVariant.valueOf(p.getString("variant", null) ?: "Full") }.getOrDefault(DictVariant.Full)
    }

    fun changeRomaji(v: Boolean) { romaji = v; prefs?.edit()?.putBoolean("romaji", v)?.apply() }
    fun changeWifiOnly(v: Boolean) { wifiOnly = v; prefs?.edit()?.putBoolean("wifiOnly", v)?.apply() }
    fun changeVariant(v: DictVariant) { variant = v; prefs?.edit()?.putString("variant", v.name)?.apply() }
}

data class SavedWord(
    val id: Long, val key: String, val word: String, val reading: String, val meaning: String,
    val pos: String, val added: Long, val box: Int,
) {
    val segments get() = Kana.rubySegments(word, if (word.any(Kana::isKanji)) reading else "")
}

/** "My words": starred words, in SQLite. All access is off the main thread. */
object WordStore {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var helper: Helper? = null
    private val _words = MutableStateFlow<List<SavedWord>>(emptyList())
    val words: StateFlow<List<SavedWord>> = _words.asStateFlow()

    private class Helper(context: Context) : SQLiteOpenHelper(context, "japanese_words.db", null, 1) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE words (id INTEGER PRIMARY KEY AUTOINCREMENT, key TEXT NOT NULL UNIQUE, word TEXT NOT NULL, " +
                    "reading TEXT NOT NULL, meaning TEXT NOT NULL, pos TEXT NOT NULL, added INTEGER NOT NULL, box INTEGER NOT NULL DEFAULT 0)"
            )
        }
        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    }

    fun init(context: Context) {
        if (helper != null) return
        helper = Helper(context.applicationContext)
        scope.launch { reload() }
    }

    private fun reload() {
        val h = helper ?: return
        val out = ArrayList<SavedWord>()
        h.readableDatabase.rawQuery("SELECT id, key, word, reading, meaning, pos, added, box FROM words ORDER BY added DESC", null).use {
            while (it.moveToNext()) out += SavedWord(it.getLong(0), it.getString(1), it.getString(2), it.getString(3), it.getString(4), it.getString(5), it.getLong(6), it.getInt(7))
        }
        _words.value = out
    }

    fun add(key: String, word: String, reading: String, meaning: String, pos: String) {
        val h = helper ?: return
        scope.launch {
            val v = ContentValues().apply {
                put("key", key); put("word", word); put("reading", reading); put("meaning", meaning); put("pos", pos)
                put("added", System.currentTimeMillis()); put("box", 0)
            }
            h.writableDatabase.insertWithOnConflict("words", null, v, SQLiteDatabase.CONFLICT_IGNORE)
            reload()
        }
    }

    fun remove(key: String) {
        val h = helper ?: return
        scope.launch { h.writableDatabase.delete("words", "key = ?", arrayOf(key)); reload() }
    }

    fun setBox(key: String, box: Int) {
        val h = helper ?: return
        scope.launch {
            h.writableDatabase.update("words", ContentValues().apply { put("box", box) }, "key = ?", arrayOf(key))
            reload()
        }
    }
}
