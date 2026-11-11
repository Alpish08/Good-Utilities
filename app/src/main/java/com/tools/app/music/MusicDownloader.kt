package com.tools.app.music

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.tools.app.music.engine.AppState
import com.tools.app.music.engine.Engine
import com.tools.app.music.engine.EngineState
import com.tools.app.music.engine.Phase
import com.tools.app.music.engine.Settings
import com.tools.app.music.engine.Status
import com.tools.app.music.engine.TrackState
import com.tools.app.music.engine.friendlyMessage
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** App-wide state + actions. Lives as long as the process; the UI and the service observe it. */
object MusicDownloader {
    private val _state = MutableStateFlow(AppState())
    val state: StateFlow<AppState> = _state.asStateFlow()

    lateinit var settings: Settings
        private set
    private lateinit var app: Context
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val engineReady = CompletableDeferred<Unit>()
    private var fetchJob: Job? = null
    private var downloadJob: Job? = null
    @Volatile private var cancelled = false
    private const val PARALLEL = 3
    /** yt-dlp process ids of the songs downloading right now. */
    private val active = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    private val engineStarted = java.util.concurrent.atomic.AtomicBoolean(false)

    /** Cheap: only remembers the context. The App lock service keeps this process alive, so nothing heavy happens here. */
    fun init(context: Context) {
        app = context.applicationContext
        settings = Settings(app)
    }

    /** Unpacks/initialises Python + yt-dlp + FFmpeg on first use of Music Downloader. */
    fun prepareEngine() {
        if (!engineStarted.compareAndSet(false, true)) return
        scope.launch {
            try {
                YoutubeDL.getInstance().init(app)
                FFmpeg.getInstance().init(app)
                autoUpdate()
                _state.update { it.copy(engine = EngineState.Ready(YoutubeDL.getInstance().version(app))) }
                engineReady.complete(Unit)
            } catch (e: Exception) {
                _state.update { it.copy(engine = EngineState.Failed(e.friendlyMessage())) }
            }
        }
    }

    fun setLink(text: String) = _state.update { it.copy(link = text, error = null) }

    fun fetch(raw: String) {
        if (_state.value.phase == Phase.Downloading) return
        val url = Engine.extractUrl(raw)
        if (url == null) {
            _state.update { it.copy(link = raw, error = "That doesn't look like a YouTube link") }
            return
        }
        prepareEngine()
        fetchJob?.cancel()
        YoutubeDL.getInstance().destroyProcessById(Engine.PROCESS_FETCH)
        _state.update {
            it.copy(link = url, phase = Phase.Fetching, playlist = null, error = null, trackStates = emptyMap())
        }
        fetchJob = scope.launch {
            try {
                engineReady.await()
                val playlist = Engine.fetchPlaylist(url)
                _state.update {
                    it.copy(phase = Phase.Ready, playlist = playlist, selected = playlist.tracks.map { t -> t.id }.toSet())
                }
            } catch (e: YoutubeDL.CanceledException) {
                // superseded by a newer fetch
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(phase = Phase.Idle, error = e.friendlyMessage()) }
            }
        }
    }

    fun toggle(id: String) = _state.update {
        if (it.phase != Phase.Ready) it
        else it.copy(selected = if (id in it.selected) it.selected - id else it.selected + id)
    }

    fun selectAll(all: Boolean) = _state.update {
        it.copy(selected = if (all) it.playlist?.tracks?.map { t -> t.id }?.toSet().orEmpty() else emptySet())
    }

    fun reset() {
        if (_state.value.phase == Phase.Downloading) return
        _state.update { it.copy(link = "", phase = Phase.Idle, playlist = null, selected = emptySet(), trackStates = emptyMap(), error = null) }
    }

    fun retryFailed(context: Context) {
        val failed = _state.value.trackStates.filterValues { it.status == Status.Failed }.keys
        _state.update { it.copy(phase = Phase.Ready, selected = failed) }
        startDownload(context)
    }

    fun startDownload(context: Context) {
        val s = _state.value
        val playlist = s.playlist ?: return
        if (s.phase != Phase.Ready) return
        val tracks = playlist.tracks.filter { it.id in s.selected }
        if (tracks.isEmpty()) return

        cancelled = false
        _state.update { st ->
            st.copy(phase = Phase.Downloading, error = null, trackStates = tracks.associate { it.id to TrackState() })
        }
        ContextCompat.startForegroundService(context, Intent(context, DownloadService::class.java))

        downloadJob = scope.launch {
            val options = settings.current
            java.io.File(app.cacheDir, "work").deleteRecursively() // leftovers from an earlier run
            // A few songs at once: each is mostly network wait + a short FFmpeg step, so this is
            // close to 3x faster without hammering YouTube (which throttles bursts).
            val next = java.util.concurrent.atomic.AtomicInteger(0)
            kotlinx.coroutines.coroutineScope {
                repeat(PARALLEL) {
                    launch {
                        while (!cancelled) {
                            val track = tracks.getOrNull(next.getAndIncrement()) ?: break
                            active += Engine.processId(track)
                            updateTrack(track.id) { it.copy(status = Status.Downloading) }
                            try {
                                val saved = Engine.downloadTrack(app, playlist, track, options) { p, status ->
                                    updateTrack(track.id) { it.copy(progress = p, status = status) }
                                }
                                updateTrack(track.id) { it.copy(status = if (saved) Status.Done else Status.Skipped, progress = 1f) }
                            } catch (e: YoutubeDL.CanceledException) {
                                break
                            } catch (e: Exception) {
                                if (cancelled) break
                                updateTrack(track.id) { it.copy(status = Status.Failed, error = e.friendlyMessage()) }
                            } finally {
                                active -= Engine.processId(track)
                            }
                        }
                    }
                }
            }
            _state.update { st ->
                if (cancelled) st.copy(
                    phase = Phase.Ready,
                    trackStates = st.trackStates.filterValues { it.status.finished },
                ) else st.copy(phase = Phase.Finished)
            }
        }
    }

    fun cancel() {
        if (_state.value.phase != Phase.Downloading) return
        cancelled = true
        active.toList().forEach { YoutubeDL.getInstance().destroyProcessById(it) }
    }

    /** YouTube breaks old yt-dlp builds (HTTP 403), so refresh it at most once a day before use. */
    private fun autoUpdate() {
        val prefs = app.getSharedPreferences("music", Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (now - prefs.getLong("ytdlpChecked", 0) < 24 * 3600_000L) return
        _state.update { it.copy(engineMessage = "Updating yt-dlp…") }
        runCatching { YoutubeDL.getInstance().updateYoutubeDL(app, YoutubeDL.UpdateChannel._STABLE) }
            .onSuccess { prefs.edit().putLong("ytdlpChecked", now).apply() }
        _state.update { it.copy(engineMessage = null) }
    }

    fun updateEngine() {
        if (_state.value.engine !is EngineState.Ready) return
        _state.update { it.copy(engine = EngineState.Updating, engineMessage = null) }
        scope.launch {
            val message = try {
                val status = YoutubeDL.getInstance().updateYoutubeDL(app, YoutubeDL.UpdateChannel._STABLE)
                if (status == YoutubeDL.UpdateStatus.ALREADY_UP_TO_DATE) "Already up to date" else "Updated"
            } catch (e: Exception) {
                "Update failed: ${e.friendlyMessage()}"
            }
            _state.update {
                it.copy(engine = EngineState.Ready(YoutubeDL.getInstance().version(app)), engineMessage = message)
            }
        }
    }

    private fun updateTrack(id: String, transform: (TrackState) -> TrackState) = _state.update { s ->
        s.copy(trackStates = s.trackStates + (id to transform(s.trackStates[id] ?: TrackState())))
    }
}
