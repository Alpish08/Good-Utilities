package com.tools.app.random

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.security.SecureRandom

/** One remembered result: [label] is shown in the history chip, [copy] goes to the clipboard. */
data class HistoryEntry(val label: String, val copy: String)

/** Remembers settings, last result and history while the app process lives. */
object RandomState {
    var mode by mutableIntStateOf(0)
    var minText by mutableStateOf("1")
    var maxText by mutableStateOf("100")
    var count by mutableIntStateOf(1)
    var noRepeats by mutableStateOf(false)
    var numbers by mutableStateOf<List<Long>>(emptyList())

    var diceCount by mutableIntStateOf(2)
    var sides by mutableIntStateOf(6)
    var diceSides by mutableIntStateOf(6)
    var dice by mutableStateOf<List<Int>>(emptyList())

    val numberHistory = mutableStateListOf<HistoryEntry>()
    val diceHistory = mutableStateListOf<HistoryEntry>()

    fun push(list: MutableList<HistoryEntry>, e: HistoryEntry) {
        list.add(0, e)
        while (list.size > 10) list.removeAt(list.size - 1)
    }
}

object Rng {
    private val rng = SecureRandom()

    /** Unbiased value in [0, bound). */
    fun below(bound: Long): Long {
        val limit = Long.MAX_VALUE - (Long.MAX_VALUE % bound)
        var r: Long
        do { r = rng.nextLong() and Long.MAX_VALUE } while (r >= limit)
        return r % bound
    }

    fun between(lo: Long, hi: Long): Long = lo + below(hi - lo + 1)

    fun numbers(lo: Long, hi: Long, count: Int, unique: Boolean): List<Long> {
        if (!unique) return List(count) { between(lo, hi) }
        val out = LinkedHashSet<Long>()
        while (out.size < count) out.add(between(lo, hi))
        return out.toList()
    }
}
