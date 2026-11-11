package com.tools.app.video

import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import com.tools.app.Storage
import com.tools.app.music.MusicDownloader
import com.tools.app.music.engine.EngineState
import com.tools.app.music.engine.YouTubeAccount
import com.tools.app.music.engine.friendlyMessage
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.coroutines.resume

enum class VPhase { Idle, Fetching, Ready, Downloading, Finished }

/** One downloadable video (a link has one, a carousel / multi-video post has several). */
data class VItem(
    val index: Int,               // 1-based, passed to --playlist-items for multi-video posts
    val title: String,
    val uploader: String,
    val durationSec: Int?,
    val thumbnail: String?,
    val formats: List<VFormat>,
    val audioSize: Long?,
)

data class VFormat(val height: Int, val shortSide: Int, val fps: Int, val size: Long?, val hasAudio: Boolean)

data class VInfo(
    val url: String,
    val title: String,
    val uploader: String,
    val site: String,
    val thumbnail: String?,
    val items: List<VItem>,
    val multi: Boolean,           // true when yt-dlp returned entries (selection rows + --playlist-items)
) {
    val durationSec: Int? get() = items.mapNotNull { it.durationSec }.takeIf { it.isNotEmpty() }?.sum()
    /** Distinct heights actually offered, tallest first. */
    val heights: List<VFormat>
        get() = items.flatMap { it.formats }.groupBy { it.height }
            .map { (h, list) -> VFormat(h, list.maxOf { it.shortSide }, list.maxOf { it.fps }, null, true) }
            .sortedByDescending { it.height }
}

data class SavedFile(val name: String, val uri: Uri?, val mime: String)

data class VState(
    val link: String = "",
    val phase: VPhase = VPhase.Idle,
    val info: VInfo? = null,
    val selected: Set<Int> = emptySet(),
    val audioOnly: Boolean = false,
    val quality: Int = 0,                 // height, 0 = best available
    val compatible: Boolean = true,
    val audioFormat: String = "m4a",      // m4a | mp3 | opus
    val error: String? = null,
    val noAccess: Boolean = false,
    val progress: Float = 0f,
    val status: String = "",
    val speed: String = "",
    val current: Int = 0,                 // 1-based position of the video being downloaded
    val total: Int = 0,
    val saved: List<SavedFile> = emptyList(),
    val savedLabel: String = "",
    val failed: Int = 0,
)

/** App-wide state for the Video downloader. Lives as long as the process so a download survives leaving the page. */
object VideoDownloader {
    private const val PROCESS_FETCH = "video-fetch"
    private const val PROCESS_DOWNLOAD = "video-download"

    private val _state = MutableStateFlow(VState())
    val state: StateFlow<VState> = _state.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val context = CompletableDeferred<Context>()
    private var fetchJob: Job? = null
    private var downloadJob: Job? = null
    @Volatile private var cancelled = false

    /** The screen hands over a context (shares can arrive before it exists). */
    fun attach(c: Context) { if (!context.isCompleted) context.complete(c.applicationContext) }

    fun setLink(text: String) = _state.update { it.copy(link = text, error = null) }

    /** Called when a link is shared to "Video Downloader" or the user taps Fetch / Paste. */
    fun fetch(raw: String) {
        if (_state.value.phase == VPhase.Downloading) return
        val url = extractUrl(raw)
        if (url == null) {
            _state.update { it.copy(link = raw, error = "Couldn't find a link in that text", phase = VPhase.Idle, info = null) }
            return
        }
        MusicDownloader.prepareEngine()
        fetchJob?.cancel()
        runCatching { YoutubeDL.getInstance().destroyProcessById(PROCESS_FETCH) }
        _state.update {
            it.copy(link = url, phase = VPhase.Fetching, info = null, selected = emptySet(), error = null,
                noAccess = false, saved = emptyList(), failed = 0)
        }
        fetchJob = scope.launch {
            try {
                val ctx = context.await()
                awaitEngine()
                val info = readInfo(ctx, url)
                val heights = info.heights
                val keys = heights.map { it.height to it.shortSide }
                // Default: best up to 1080p (judged by the short side so portrait clips count right).
                val quality = keys.filter { it.second <= 1080 }.maxByOrNull { it.second }?.first ?: 0
                _state.update {
                    it.copy(phase = VPhase.Ready, info = info, selected = info.items.map { i -> i.index }.toSet(), quality = quality)
                }
            } catch (e: YoutubeDL.CanceledException) {
                // superseded by a newer fetch
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(phase = VPhase.Idle, error = errorText(e)) }
            }
        }
    }

    fun toggle(index: Int) = _state.update {
        if (it.phase != VPhase.Ready) it else it.copy(selected = if (index in it.selected) it.selected - index else it.selected + index)
    }

    fun selectAll(all: Boolean) = _state.update { it.copy(selected = if (all) it.info?.items?.map { i -> i.index }?.toSet().orEmpty() else emptySet()) }
    fun setAudioOnly(v: Boolean) = _state.update { it.copy(audioOnly = v) }
    fun setQuality(h: Int) = _state.update { it.copy(quality = h) }
    fun setCompatible(v: Boolean) = _state.update { it.copy(compatible = v) }
    fun setAudioFormat(f: String) = _state.update { it.copy(audioFormat = f) }

    fun reset() {
        if (_state.value.phase == VPhase.Downloading) return
        fetchJob?.cancel()
        runCatching { YoutubeDL.getInstance().destroyProcessById(PROCESS_FETCH) }
        _state.update { VState() }
    }

    fun cancel() {
        if (_state.value.phase != VPhase.Downloading) return
        cancelled = true
        runCatching { YoutubeDL.getInstance().destroyProcessById(PROCESS_DOWNLOAD) }
    }

    fun download() {
        val s = _state.value
        val info = s.info ?: return
        if (s.phase != VPhase.Ready) return
        val items = info.items.filter { it.index in s.selected }
        if (items.isEmpty()) return
        val folder = if (s.audioOnly) Storage.Folder.Audio else Storage.Folder.Videos
        if (!Storage.hasAccess()) {
            _state.update { it.copy(noAccess = true, error = Storage.NoAccess().message) }
            return
        }
        cancelled = false
        _state.update {
            it.copy(phase = VPhase.Downloading, error = null, noAccess = false, progress = 0f, status = "Starting…", speed = "",
                current = 1, total = items.size, saved = emptyList(), failed = 0, savedLabel = Storage.label(folder))
        }
        downloadJob = scope.launch {
            val ctx = context.await()
            val root = File(ctx.cacheDir, "video-work").apply { deleteRecursively(); mkdirs() }
            val saved = mutableListOf<SavedFile>()
            var failed = 0
            var lastError: Throwable? = null
            try {
                awaitEngine()
                for ((n, item) in items.withIndex()) {
                    if (cancelled) break
                    _state.update { it.copy(current = n + 1, progress = n / items.size.toFloat(), status = "Downloading…", speed = "") }
                    val work = File(root, "v${item.index}").apply { mkdirs() }
                    try {
                        val file = downloadOne(ctx, s, info, item, work) { p, status, speed ->
                            _state.update { it.copy(progress = (n + p) / items.size, status = status, speed = speed) }
                        }
                        _state.update { it.copy(status = "Saving…", speed = "") }
                        val name = fileName(item.title, file.extension)
                        val savedName = Storage.saveFile(ctx, folder, null, name, file)
                        saved += SavedFile(savedName, scan(ctx, Storage.dir(folder).resolve(savedName)), mimeOf(file.extension))
                    } catch (e: YoutubeDL.CanceledException) {
                        break
                    } catch (e: Storage.NoAccess) {
                        throw e
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        if (cancelled) break
                        failed++; lastError = e
                    } finally {
                        work.deleteRecursively()
                    }
                }
                _state.update { st ->
                    when {
                        cancelled -> st.copy(phase = VPhase.Ready, error = null)
                        saved.isEmpty() -> st.copy(phase = VPhase.Ready, error = lastError?.let(::errorText) ?: "Nothing was downloaded")
                        else -> st.copy(phase = VPhase.Finished, saved = saved, failed = failed, progress = 1f,
                            error = if (failed > 0) lastError?.let(::errorText) else null)
                    }
                }
            } catch (e: Storage.NoAccess) {
                _state.update { it.copy(phase = VPhase.Ready, noAccess = true, error = e.message) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(phase = VPhase.Ready, error = errorText(e)) }
            } finally {
                root.deleteRecursively()
            }
        }
    }

    // ---------------------------------------------------------------- engine

    private suspend fun awaitEngine() {
        val engine = MusicDownloader.state.first { it.engine is EngineState.Ready || it.engine is EngineState.Failed }.engine
        if (engine is EngineState.Failed) error("The download engine failed to start: ${engine.message}")
    }

    private fun isYouTube(url: String) = Regex("(^|//|\\.)(youtube\\.com|youtu\\.be|youtube-nocookie\\.com)").containsMatchIn(url)

    private fun needsLogin(e: Throwable): Boolean {
        val m = (e.message ?: "").lowercase()
        return listOf("sign in", "log in", "login", "confirm your age", "age-restricted", "not a bot", "cookies").any { it in m }
    }

    /** Runs [build] anonymously; for YouTube, retries with the signed-in account's cookies on sign-in / age errors. */
    private fun <T> withAccount(ctx: Context, url: String, run: (cookies: File?) -> T, cleanup: () -> Unit = {}): T =
        try {
            run(null)
        } catch (e: YoutubeDL.CanceledException) {
            throw e
        } catch (e: Exception) {
            if (isYouTube(url) && YouTubeAccount.isSignedIn(ctx) && needsLogin(e)) {
                cleanup(); run(YouTubeAccount.cookieFile(ctx))
            } else throw e
        }

    private fun readInfo(ctx: Context, url: String): VInfo {
        val out = withAccount(ctx, url, { cookies ->
            val request = YoutubeDLRequest(url).apply {
                addOption("--dump-single-json")
                addOption("--no-playlist")
                addOption("--no-warnings")
                addOption("--cache-dir", File(ctx.cacheDir, "yt-dlp").absolutePath)
                if (cookies != null) addOption("--cookies", cookies.absolutePath)
            }
            YoutubeDL.getInstance().execute(request, PROCESS_FETCH).out
        })
        val start = out.indexOf('{')
        if (start < 0) error("Couldn't read that link")
        val root = JSONObject(out.substring(start))
        val entries = root.optJSONArray("entries")
        val site = siteName(root.str("extractor_key") ?: root.str("extractor") ?: "")
        val rootThumb = root.thumb()
        val rootTitle = root.str("title") ?: root.str("id") ?: "Video"
        val rootUploader = root.str("uploader") ?: root.str("channel") ?: ""

        if (entries == null) {
            val item = parseItem(root, 1)
            return VInfo(url, rootTitle, rootUploader, site, rootThumb, listOf(item), multi = false)
        }
        val items = (0 until entries.length()).mapNotNull { i -> entries.optJSONObject(i)?.let { parseItem(it, i + 1, rootTitle) } }
        if (items.isEmpty()) error("There is no video in this link")
        return VInfo(url, rootTitle, rootUploader, site, rootThumb ?: items.firstNotNullOfOrNull { it.thumbnail }, items, multi = true)
    }

    private fun parseItem(o: JSONObject, index: Int, fallbackTitle: String = "Video"): VItem {
        val duration = if (o.isNull("duration")) null else o.optDouble("duration").takeIf { !it.isNaN() }?.toInt()
        val formats = mutableListOf<VFormat>()
        var audioSize: Long? = null
        val arr: JSONArray? = o.optJSONArray("formats")
        if (arr != null) for (i in 0 until arr.length()) {
            val f = arr.optJSONObject(i) ?: continue
            val vcodec = f.str("vcodec"); val acodec = f.str("acodec")
            val h = f.optInt("height", 0); val w = f.optInt("width", 0)
            val tbr = if (f.isNull("tbr")) null else f.optDouble("tbr").takeIf { !it.isNaN() }
            val size = (if (f.isNull("filesize")) null else f.optLong("filesize"))
                ?: (if (f.isNull("filesize_approx")) null else f.optLong("filesize_approx"))
                ?: if (tbr != null && duration != null) (tbr * 125.0 * duration).toLong() else null
            val isVideo = vcodec != "none" && h > 0
            if (isVideo) {
                val fps = if (f.isNull("fps")) 0 else f.optDouble("fps").takeIf { !it.isNaN() }?.toInt() ?: 0
                formats += VFormat(h, if (w > 0) minOf(w, h) else h, fps, size?.takeIf { it > 0 }, acodec != "none")
            } else if (vcodec == "none" && acodec != null && acodec != "none" && size != null) {
                audioSize = maxOf(audioSize ?: 0L, size)
            }
        }
        return VItem(
            index = index,
            title = o.str("title") ?: fallbackTitle,
            uploader = o.str("uploader") ?: o.str("channel") ?: "",
            durationSec = duration,
            thumbnail = o.thumb(),
            formats = formats,
            audioSize = audioSize,
        )
    }

    private fun siteName(key: String) = when (key.lowercase().removeSuffix("ie")) {
        "youtube" -> "YouTube"
        "twitter", "twitterbroadcast" -> "X"
        "tiktok" -> "TikTok"
        "instagram" -> "Instagram"
        "reddit" -> "Reddit"
        "facebook" -> "Facebook"
        "vimeo" -> "Vimeo"
        "generic" -> ""
        else -> key.replace(Regex("(?<=[a-z])(?=[A-Z])"), " ")
    }

    private fun downloadOne(
        ctx: Context, s: VState, info: VInfo, item: VItem, work: File,
        onProgress: (Float, String, String) -> Unit,
    ): File {
        val h = s.quality
        val cap = if (h > 0) "[height<=$h]" else ""
        val format = if (s.audioOnly) {
            if (s.audioFormat == "m4a") "ba[ext=m4a]/ba/b" else "ba/b"
        } else if (s.compatible) {
            "bv*$cap[vcodec^=avc1]+ba[ext=m4a]/bv*$cap[vcodec^=avc1]+ba/bv*$cap+ba/b$cap/b"
        } else {
            "bv*$cap+ba/b$cap/b"
        }

        fun request(cookies: File?) = YoutubeDLRequest(info.url).apply {
            if (info.multi) addOption("--playlist-items", item.index.toString()) else addOption("--no-playlist")
            addOption("--no-warnings")
            addOption("-f", format)
            if (s.audioOnly) {
                addOption("-x")
                addOption("--audio-format", s.audioFormat)
                addOption("--audio-quality", "0")
                addOption("--embed-metadata")
                if (s.audioFormat != "opus") {
                    addOption("--embed-thumbnail")
                    addOption("--convert-thumbnails", "jpg")
                }
            } else {
                addOption("--merge-output-format", if (s.compatible) "mp4" else "mp4/mkv")
            }
            addOption("-N", "4")
            addOption("--no-mtime")
            addOption("--cache-dir", File(ctx.cacheDir, "yt-dlp").absolutePath)
            addOption("-o", File(work, "video.%(ext)s").absolutePath)
            if (cookies != null) addOption("--cookies", cookies.absolutePath)
        }

        var stage = 0; var streams = if (s.audioOnly) 1 else 2; var lastP = 0f; var last = -1f
        fun runOnce(cookies: File?) {
            stage = 0; lastP = 0f; last = -1f
            YoutubeDL.getInstance().execute(request(cookies), PROCESS_DOWNLOAD) { percent, _, line ->
                Regex("Downloading (\\d+) format").find(line)?.let { streams = it.groupValues[1].toInt().coerceAtLeast(1) }
                if (POST.any { line.contains(it) }) { onProgress(0.97f, "Processing…", ""); return@execute }
                if (percent < 0f) return@execute
                val p = (percent / 100f).coerceIn(0f, 1f)
                if (p < lastP - 0.2f) stage++
                lastP = p
                val total = maxOf(streams, stage + 1)
                val overall = ((stage + p) / total).coerceAtMost(0.96f)
                if (overall - last >= 0.005f) {
                    last = overall
                    val speed = Regex("at\\s+(\\S+/s)").find(line)?.groupValues?.get(1)
                    val eta = Regex("ETA\\s+(\\S+)").find(line)?.groupValues?.get(1)
                    val text = listOfNotNull(speed, eta?.let { "$it left" }).joinToString(" · ")
                    onProgress(overall, "Downloading…", text)
                }
            }
        }
        withAccount(ctx, info.url, { c -> runOnce(c) }, cleanup = { work.listFiles()?.forEach { it.delete() } })

        val exts = setOf("mp4", "mkv", "webm", "mov", "m4v", "m4a", "mp3", "opus", "ogg", "aac", "flac", "wav", "3gp", "avi", "flv")
        return work.listFiles()?.filter { it.isFile && it.extension.lowercase() in exts }?.maxByOrNull { it.length() }
            ?: error("The download finished but produced no file")
    }

    private val POST = listOf("[Merger]", "[ExtractAudio]", "[VideoConvertor]", "[EmbedThumbnail]", "[Metadata]", "[Fixup", "[ThumbnailsConvertor]")

    // ---------------------------------------------------------------- helpers

    private suspend fun scan(ctx: Context, file: File): Uri? = withTimeoutOrNull(4000) {
        suspendCancellableCoroutine { cont ->
            MediaScannerConnection.scanFile(ctx, arrayOf(file.absolutePath), null) { _, uri -> if (cont.isActive) cont.resume(uri) }
        }
    }

    private fun fileName(title: String, ext: String): String {
        val base = title.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), " ").replace(Regex("\\s+"), " ").trim().trimEnd('.').take(120).trim()
        return base.ifBlank { "video" } + "." + ext.ifBlank { "mp4" }
    }

    private fun mimeOf(ext: String) = when (ext.lowercase()) {
        "mp4", "m4v" -> "video/mp4"
        "mkv" -> "video/x-matroska"
        "webm" -> "video/webm"
        "mov" -> "video/quicktime"
        "m4a" -> "audio/mp4"
        "mp3" -> "audio/mpeg"
        "opus", "ogg" -> "audio/ogg"
        else -> "*/*"
    }

    fun extractUrl(raw: String): String? {
        Regex("https?://[^\\s<>\"]+").find(raw)?.let { return it.value.trimEnd('.', ',', ')', ']', ';', '!', '?') }
        val t = raw.trim()
        return if (!t.contains(' ') && Regex("^[\\w-]+(\\.[\\w-]+)+(/\\S*)?$").matches(t)) "https://$t" else null
    }

    private fun errorText(e: Throwable): String {
        val raw = e.message ?: ""
        val m = raw.lowercase()
        return when {
            e is Storage.NoAccess -> e.message ?: "Storage access is needed"
            "private video" in m || "video is private" in m || "this video is private" in m -> "This video is private."
            "unsupported url" in m -> "This link isn't supported. Open the page with the video itself and share that link."
            "no video could be found" in m || "no video formats" in m || "there is no video" in m -> "No video was found in this link."
            "login" in m || "log in" in m || "sign in" in m || "rate-limit" in m || "empty media response" in m || "cookies" in m ||
                "authentication" in m || "restricted video" in m || "protected" in m ->
                "This post needs you to be signed in. Instagram and X sometimes require it, and signing in isn't supported yet."
            "unable to download" in m || "urlopen" in m || "timed out" in m || "name or service" in m || "unreachable" in m ||
                "connection" in m || "network" in m || "resolve" in m -> "Couldn't reach the site. Check your connection and try again."
            "video unavailable" in m || "has been removed" in m || "not available" in m -> "This video isn't available."
            "403" in m -> "The site refused the download (HTTP 403). Update yt-dlp in Music Downloader settings and try again."
            else -> e.friendlyMessage()
        }
    }
}

private fun JSONObject.str(key: String): String? = if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() && it != "null" }

private fun JSONObject.thumb(): String? {
    str("thumbnail")?.let { return it }
    val t = optJSONArray("thumbnails") ?: return null
    return (t.length() - 1 downTo 0).firstNotNullOfOrNull { t.optJSONObject(it)?.str("url") }
}
