package com.tools.app.japanese

import com.tools.app.R
import com.tools.app.ui.t
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

sealed interface ModelState {
    data object Unknown : ModelState
    data object Missing : ModelState
    /** [waitingForWifi] is true while a Wi-Fi-only download hasn't started yet. */
    class Downloading(val wifiOnly: Boolean) : ModelState
    data object Ready : ModelState
    class Failed(val message: String) : ModelState
}

/** ML Kit on-device ja → en translation. The model (about 30 MB) downloads on first use. */
object Translate {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val translator by lazy {
        Translation.getClient(TranslatorOptions.Builder().setSourceLanguage(TranslateLanguage.JAPANESE).setTargetLanguage(TranslateLanguage.ENGLISH).build())
    }
    private val model get() = TranslateRemoteModel.Builder(TranslateLanguage.JAPANESE).build()
    private val _state = MutableStateFlow<ModelState>(ModelState.Unknown)
    val state: StateFlow<ModelState> = _state.asStateFlow()
    private var job: Job? = null
    private val cache = object : LinkedHashMap<String, String>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?) = size > 200
    }

    /** Checks whether the model is already on the phone. */
    fun refresh() {
        if (job?.isActive == true) return
        scope.launch {
            val have = runCatching { RemoteModelManager.getInstance().isModelDownloaded(model).await() }.getOrDefault(false)
            if (job?.isActive != true) _state.value = if (have) ModelState.Ready else ModelState.Missing
        }
    }

    fun download(wifiOnly: Boolean) {
        job?.cancel()
        _state.value = ModelState.Downloading(wifiOnly)
        job = scope.launch {
            try {
                val cond = DownloadConditions.Builder().apply { if (wifiOnly) requireWifi() }.build()
                translator.downloadModelIfNeeded(cond).await()
                _state.value = ModelState.Ready
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (t: Throwable) {
                _state.value = ModelState.Failed(t.message ?: t(R.string.jp_download_failed_2))
            }
        }
    }

    fun delete() {
        scope.launch {
            job?.cancel()
            runCatching { RemoteModelManager.getInstance().deleteDownloadedModel(model).await() }
            cache.clear()
            _state.value = ModelState.Missing
        }
    }

    suspend fun translate(text: String): String {
        synchronized(cache) { cache[text] }?.let { return it }
        val r = translator.translate(text).await()
        synchronized(cache) { cache[text] = r }
        return r
    }
}
