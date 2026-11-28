package com.tools.app.music.engine

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class Quality(val arg: String, private val labelRes: Int, private val summaryRes: Int) {
    Best("0", com.tools.app.R.string.music_q_best, com.tools.app.R.string.music_q_best_summary),
    K320("320K", com.tools.app.R.string.music_q_320, com.tools.app.R.string.music_q_320_summary),
    K192("192K", com.tools.app.R.string.music_q_192, com.tools.app.R.string.music_q_192_summary),
    K128("128K", com.tools.app.R.string.music_q_128, com.tools.app.R.string.music_q_128_summary),
    ;
    val label get() = com.tools.app.ui.t(labelRes)
    val summary get() = com.tools.app.ui.t(summaryRes)
}

data class Options(
    val quality: Quality = Quality.Best,
    val squareCovers: Boolean = true,
    val numberTracks: Boolean = true,
    val subfolder: Boolean = true,
    val skipExisting: Boolean = true,
)

class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val _flow = MutableStateFlow(load())
    val flow: StateFlow<Options> = _flow.asStateFlow()
    val current get() = _flow.value

    fun update(transform: (Options) -> Options) {
        val next = transform(_flow.value)
        _flow.value = next
        prefs.edit()
            .putString("quality", next.quality.name)
            .putBoolean("squareCovers", next.squareCovers)
            .putBoolean("numberTracks", next.numberTracks)
            .putBoolean("subfolder", next.subfolder)
            .putBoolean("skipExisting", next.skipExisting)
            .apply()
    }

    private fun load(): Options {
        val d = Options()
        return Options(
            quality = runCatching { Quality.valueOf(prefs.getString("quality", d.quality.name)!!) }.getOrDefault(d.quality),
            squareCovers = prefs.getBoolean("squareCovers", d.squareCovers),
            numberTracks = prefs.getBoolean("numberTracks", d.numberTracks),
            subfolder = prefs.getBoolean("subfolder", d.subfolder),
            skipExisting = prefs.getBoolean("skipExisting", d.skipExisting),
        )
    }
}
