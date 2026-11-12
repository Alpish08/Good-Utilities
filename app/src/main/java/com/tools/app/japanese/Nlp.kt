package com.tools.app.japanese

import com.atilika.kuromoji.ipadic.Tokenizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** One word of a sentence, as split by Kuromoji. */
data class Word(
    val surface: String,
    val base: String,
    /** Reading in hiragana ("" when the word is plain kana / unknown). */
    val reading: String,
    /** How it is pronounced in hiragana (は → わ); used for romaji. */
    val pronunciation: String,
    /** Plain English part of speech ("noun", "verb", "particle"…). */
    val pos: String,
    val posGroup: PosGroup,
    /** Furigana pieces (okurigana peeled off), e.g. 食べ → [食 / た][べ]. */
    val segments: List<Seg>,
) {
    val isParticle get() = posGroup == PosGroup.Particle
    val isSymbol get() = posGroup == PosGroup.Symbol
    /** Has hiragana, katakana or kanji. Latin words, digits and symbols are shown in the sentence only. */
    val japanese get() = Kana.hasJapanese(surface)
    /** Worth a dictionary entry of its own. */
    val isWord get() = japanese && !isSymbol
}

data class Seg(val text: String, val ruby: String?)

enum class PosGroup { Noun, Verb, Adjective, Adverb, Particle, Auxiliary, Conjunction, Pronoun, Interjection, Prefix, Suffix, Symbol, Other }

object Nlp {
    @Volatile private var tokenizer: Tokenizer? = null
    private val lock = Mutex()

    /** Loads the IPADIC dictionary once, on a background thread (takes a second or two). */
    suspend fun warmUp() {
        if (tokenizer != null) return
        lock.withLock {
            if (tokenizer == null) withContext(Dispatchers.Default) { tokenizer = Tokenizer() }
        }
    }

    val ready get() = tokenizer != null

    /** Splits [text]. With [dictionary] (when JMdict is installed) suspicious runs of small pieces are merged into real words. */
    suspend fun split(text: String, dictionary: android.content.Context? = null): List<Word> {
        val raw = rawSplit(text)
        return if (dictionary != null) withContext(Dispatchers.IO) { resegment(dictionary, raw) } else raw
    }

    private fun suspicious(w: Word): Boolean {
        if (!w.japanese) return false
        val last = w.surface.last()
        if (last == 'っ' || last == 'ッ') return true
        if (w.surface.length == 1 && Kana.isKana(w.surface) && !w.isParticle) return true
        return w.posGroup == PosGroup.Suffix || w.posGroup == PosGroup.Other
    }

    private fun resegment(ctx: android.content.Context, words: List<Word>): List<Word> {
        val out = ArrayList<Word>()
        var i = 0
        while (i < words.size) {
            var merged: Word? = null
            var used = 1
            if (words[i].isWord && !words[i].isParticle || suspicious(words[i])) {
                for (len in minOf(4, words.size - i) downTo 2) {
                    val piece = words.subList(i, i + len)
                    if (piece.any { !it.isWord }) continue
                    val surface = piece.joinToString("") { it.surface }
                    if (surface.length > 8) continue
                    if (piece.none(::suspicious)) continue
                    if (piece.first().isParticle) continue // particle + word is not a word
                    val entry = Jmdict.exact(ctx, surface) ?: continue
                    merged = mergedWord(piece, surface, entry)
                    used = len
                    break
                }
            }
            if (merged != null) { out += merged; i += used } else { out += words[i]; i++ }
        }
        return out
    }

    private fun mergedWord(piece: List<Word>, surface: String, entry: Entry): Word {
        val reading = if (Kana.isKana(surface)) Kana.toHiragana(surface) else piece.joinToString("") { it.reading.ifEmpty { Kana.toHiragana(it.surface) } }
        val pron = piece.joinToString("") { it.pronunciation }
        val code = entry.senses.firstOrNull()?.pos?.firstOrNull() ?: "n"
        val group = when {
            code.startsWith("v") -> PosGroup.Verb
            code.startsWith("adj") -> PosGroup.Adjective
            code.startsWith("adv") -> PosGroup.Adverb
            code == "pn" -> PosGroup.Pronoun
            code == "conj" -> PosGroup.Conjunction
            code == "int" -> PosGroup.Interjection
            else -> PosGroup.Noun
        }
        val label = Jmdict.posName(code).ifEmpty { "noun" }
        return Word(surface, surface, reading, pron, label, group, Kana.rubySegments(surface, if (surface.any(Kana::isKanji)) reading else ""))
    }

    private suspend fun rawSplit(text: String): List<Word> {
        warmUp()
        return withContext(Dispatchers.Default) {
            val t = tokenizer ?: return@withContext emptyList()
            t.tokenize(text).mapNotNull { tok ->
                val surface = tok.surface
                if (surface.isBlank()) return@mapNotNull null
                val l1 = tok.partOfSpeechLevel1
                val l2 = tok.partOfSpeechLevel2
                val (group, label) = classify(l1, l2)
                val base = tok.baseForm.takeIf { it != "*" && it.isNotBlank() } ?: surface
                val reading = tok.reading.takeIf { it != "*" }?.let(Kana::toHiragana) ?: ""
                val pron = tok.pronunciation.takeIf { it != "*" }?.let(Kana::toHiragana) ?: reading
                // Kuromoji echoes the surface as the "reading" of plain kana; no furigana needed then.
                val useReading = if (surface.any(Kana::isKanji)) reading else ""
                Word(
                    surface, base, if (reading.isNotEmpty()) reading else if (Kana.isKana(surface)) Kana.toHiragana(surface) else "",
                    pron.ifEmpty { Kana.toHiragana(surface) }, label, group, Kana.rubySegments(surface, useReading),
                )
            }
        }
    }

    private fun classify(l1: String, l2: String): Pair<PosGroup, String> = when (l1) {
        "名詞" -> when (l2) {
            "代名詞" -> PosGroup.Pronoun to "pronoun"
            "固有名詞" -> PosGroup.Noun to "proper noun"
            "数" -> PosGroup.Noun to "number"
            "接尾" -> PosGroup.Suffix to "suffix"
            "形容動詞語幹" -> PosGroup.Adjective to "na-adjective"
            "非自立" -> PosGroup.Noun to "dependent noun"
            "副詞可能" -> PosGroup.Noun to "noun (adverbial)"
            else -> PosGroup.Noun to "noun"
        }
        "動詞" -> if (l2 == "非自立") PosGroup.Verb to "helper verb" else if (l2 == "接尾") PosGroup.Suffix to "verb suffix" else PosGroup.Verb to "verb"
        "形容詞" -> PosGroup.Adjective to "i-adjective"
        "副詞" -> PosGroup.Adverb to "adverb"
        "助詞" -> PosGroup.Particle to when (l2) {
            "格助詞" -> "case particle"
            "係助詞" -> "topic particle"
            "接続助詞" -> "conjunctive particle"
            "終助詞" -> "sentence-ending particle"
            "副助詞" -> "adverbial particle"
            else -> "particle"
        }
        "助動詞" -> PosGroup.Auxiliary to "auxiliary verb"
        "接続詞" -> PosGroup.Conjunction to "conjunction"
        "連体詞" -> PosGroup.Adjective to "pre-noun adjective"
        "感動詞" -> PosGroup.Interjection to "interjection"
        "接頭詞" -> PosGroup.Prefix to "prefix"
        "記号" -> PosGroup.Symbol to "symbol"
        "フィラー" -> PosGroup.Interjection to "filler"
        else -> PosGroup.Other to "other"
    }
}

object Kana {
    fun isKanji(c: Char) = c in '一'..'鿿' || c in '㐀'..'䶿' || c in '豈'..'﫿' || c == '々' || c == '〆' || c == 'ヶ'
    fun isHiragana(c: Char) = c in 'ぁ'..'ゟ'
    fun isKatakana(c: Char) = c in '゠'..'ヿ'
    fun isKana(s: String) = s.isNotEmpty() && s.all { isHiragana(it) || isKatakana(it) || it == 'ー' }
    /** Hiragana, katakana or CJK ideographs. A lone ー or ・ doesn't count. */
    fun hasJapanese(s: String) = s.any { isKanji(it) || isHiragana(it) || (isKatakana(it) && it != '\u30FB' && it != '\u30FC') }

    fun toHiragana(s: String): String = buildString(s.length) {
        for (c in s) append(if (c in 'ァ'..'ヶ') (c.code - 0x60).toChar() else c)
    }

    /** Splits a word into furigana pieces: okurigana at the start/end are left bare, only the kanji core gets a reading. */
    fun rubySegments(surface: String, readingHira: String): List<Seg> {
        if (readingHira.isEmpty() || surface.none(::isKanji)) return listOf(Seg(surface, null))
        var s = surface
        var r = readingHira
        val head = StringBuilder()
        val tail = StringBuilder()
        while (s.length > 1 && !isKanji(s.first()) && r.length > 1 && toHiragana(s.first().toString()) == r.first().toString()) {
            head.append(s.first()); s = s.substring(1); r = r.substring(1)
        }
        while (s.length > 1 && !isKanji(s.last()) && r.length > 1 && toHiragana(s.last().toString()) == r.last().toString()) {
            tail.insert(0, s.last()); s = s.dropLast(1); r = r.dropLast(1)
        }
        return buildList {
            if (head.isNotEmpty()) add(Seg(head.toString(), null))
            add(Seg(s, r.ifEmpty { null }))
            if (tail.isNotEmpty()) add(Seg(tail.toString(), null))
        }
    }

    // --- Hepburn romaji --------------------------------------------------------------------------
    private val mono = mapOf(
        'あ' to "a", 'い' to "i", 'う' to "u", 'え' to "e", 'お' to "o",
        'か' to "ka", 'き' to "ki", 'く' to "ku", 'け' to "ke", 'こ' to "ko",
        'さ' to "sa", 'し' to "shi", 'す' to "su", 'せ' to "se", 'そ' to "so",
        'た' to "ta", 'ち' to "chi", 'つ' to "tsu", 'て' to "te", 'と' to "to",
        'な' to "na", 'に' to "ni", 'ぬ' to "nu", 'ね' to "ne", 'の' to "no",
        'は' to "ha", 'ひ' to "hi", 'ふ' to "fu", 'へ' to "he", 'ほ' to "ho",
        'ま' to "ma", 'み' to "mi", 'む' to "mu", 'め' to "me", 'も' to "mo",
        'や' to "ya", 'ゆ' to "yu", 'よ' to "yo",
        'ら' to "ra", 'り' to "ri", 'る' to "ru", 'れ' to "re", 'ろ' to "ro",
        'わ' to "wa", 'ゐ' to "i", 'ゑ' to "e", 'を' to "o", 'ん' to "n",
        'が' to "ga", 'ぎ' to "gi", 'ぐ' to "gu", 'げ' to "ge", 'ご' to "go",
        'ざ' to "za", 'じ' to "ji", 'ず' to "zu", 'ぜ' to "ze", 'ぞ' to "zo",
        'だ' to "da", 'ぢ' to "ji", 'づ' to "zu", 'で' to "de", 'ど' to "do",
        'ば' to "ba", 'び' to "bi", 'ぶ' to "bu", 'べ' to "be", 'ぼ' to "bo",
        'ぱ' to "pa", 'ぴ' to "pi", 'ぷ' to "pu", 'ぺ' to "pe", 'ぽ' to "po",
        'ぁ' to "a", 'ぃ' to "i", 'ぅ' to "u", 'ぇ' to "e", 'ぉ' to "o", 'ゔ' to "vu",
        'ゃ' to "ya", 'ゅ' to "yu", 'ょ' to "yo", 'ゎ' to "wa",
    )
    private val digraph = mapOf(
        "きゃ" to "kya", "きゅ" to "kyu", "きょ" to "kyo", "ぎゃ" to "gya", "ぎゅ" to "gyu", "ぎょ" to "gyo",
        "しゃ" to "sha", "しゅ" to "shu", "しょ" to "sho", "じゃ" to "ja", "じゅ" to "ju", "じょ" to "jo",
        "ちゃ" to "cha", "ちゅ" to "chu", "ちょ" to "cho", "ぢゃ" to "ja", "ぢゅ" to "ju", "ぢょ" to "jo",
        "にゃ" to "nya", "にゅ" to "nyu", "にょ" to "nyo", "ひゃ" to "hya", "ひゅ" to "hyu", "ひょ" to "hyo",
        "びゃ" to "bya", "びゅ" to "byu", "びょ" to "byo", "ぴゃ" to "pya", "ぴゅ" to "pyu", "ぴょ" to "pyo",
        "みゃ" to "mya", "みゅ" to "myu", "みょ" to "myo", "りゃ" to "rya", "りゅ" to "ryu", "りょ" to "ryo",
        "ふぁ" to "fa", "ふぃ" to "fi", "ふぇ" to "fe", "ふぉ" to "fo", "うぃ" to "wi", "うぇ" to "we", "うぉ" to "wo",
        "てぃ" to "ti", "でぃ" to "di", "しぇ" to "she", "ちぇ" to "che", "じぇ" to "je", "ゔぁ" to "va", "ゔぃ" to "vi", "ゔぇ" to "ve", "ゔぉ" to "vo",
    )

    /** Hepburn romaji for hiragana (katakana is converted first). Long vowels are doubled (ou, uu) as people usually type them. */
    fun toRomaji(kana: String): String = romajiWithStarts(kana).first

    /**
     * Romaji of [text] plus, for each input character, where its romaji starts. A small っ doubles the
     * consonant of whatever follows it, and ゃゅょ join the previous kana, so slicing the result by
     * word boundaries keeps がっ|き → "ga" + "kki".
     */
    fun romajiWithStarts(kana: String): Pair<String, IntArray> {
        val s = toHiragana(kana)
        val out = StringBuilder()
        val starts = IntArray(s.length + 1)
        var i = 0
        var geminate = false
        while (i < s.length) {
            val c = s[i]
            starts[i] = out.length
            if (c == 'っ') { geminate = true; i++; continue }
            var r: String? = null
            var used = 1
            if (i + 1 < s.length) digraph[s.substring(i, i + 2)]?.let { r = it; used = 2 }
            if (r == null) {
                r = when {
                    c == 'ー' -> out.lastOrNull()?.takeIf { it in "aiueo" }?.toString() ?: "-"
                    else -> mono[c] ?: c.toString()
                }
            }
            val romaji = r!!
            if (geminate && romaji.isNotEmpty() && romaji[0].isLetter()) {
                out.append(if (romaji.startsWith("ch")) "t" else romaji[0].toString())
            }
            geminate = false
            // ん before a vowel or y gets an apostrophe (shin'ichi).
            if (c == 'ん' && i + 1 < s.length && s[i + 1] in "あいうえおやゆよ") out.append("n'") else out.append(romaji)
            if (used == 2) starts[i + 1] = out.length
            i += used
        }
        starts[s.length] = out.length
        return out.toString() to starts
    }

    /** Romaji for each word of a sentence, computed across word boundaries. Words that aren't Japanese get "". */
    fun romajiPerWord(words: List<Word>): List<String> {
        val sb = StringBuilder()
        val bounds = ArrayList<IntRange?>()
        for (w in words) {
            if (w.isWord) {
                val src = romajiSource(w)
                bounds += sb.length until sb.length + src.length
                sb.append(src)
            } else {
                bounds += null
                sb.append(' ') // breaks any pending っ / ん
            }
        }
        val (full, starts) = romajiWithStarts(sb.toString())
        return bounds.map { r -> if (r == null || r.isEmpty()) "" else full.substring(starts[r.first], starts[r.last + 1]) }
    }
}
