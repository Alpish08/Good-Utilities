package com.tools.app.music.engine

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class Quality(val arg: String, val label: String, val summary: String) {
    Best("0", "Best", "Variable bitrate, around 245 kbps"),
    K320("320K", "320 kbps", "Constant bitrate, largest files"),
    K192("192K", "192 kbps", "Good balance of size and quality"),
    K128("128K", "128 kbps", "Smallest files"),
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
