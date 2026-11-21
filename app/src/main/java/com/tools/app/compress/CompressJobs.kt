package com.tools.app.compress

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaCodecList
import android.media.MediaMetadataRetriever
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import androidx.annotation.OptIn
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import com.tools.app.Storage
import com.tools.app.audio.Ffmpeg
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.resume
import kotlin.math.roundToInt
import kotlin.math.sqrt

sealed interface CWork {
    data object Idle : CWork
    data class Running(val progress: Float, val note: String? = null) : CWork
    data class Done(
        val fileName: String, val folder: Storage.Folder, val before: Long, val after: Long, val shareUri: Uri?, val mime: String,
        /** Several files (photos): shared together. */
        val shareUris: List<Uri> = emptyList(), val sub: String? = null,
    ) : CWork
    data class Failed(val message: String) : CWork
}

data class VideoSource(
    val uri: Uri, val name: String, val durationMs: Long, val width: Int, val height: Int,
    val sizeBytes: Long, val bitrate: Long, val fps: Float, val hasAudio: Boolean, val thumb: Bitmap?,
)

data class AudioSource(val clip: Ffmpeg.Clip, val sizeBytes: Long) {
    val bitrate get() = if (clip.durationMs > 0) sizeBytes * 8000 / clip.durationMs else 0L
}

enum class AudioFormat(val label: String, val ext: String, val defaultKbps: Int) {
    Mp3("MP3", "mp3", 128), Aac("AAC (M4A)", "m4a", 128), Opus("Opus", "opus", 96),
}

val ResolutionOptions = listOf(0 to "Original", 1080 to "1080p", 720 to "720p", 480 to "480p")
val QualityLabels = listOf("High", "Balanced", "Small")
val AudioBitrates = listOf(192, 128, 96, 64)

/** Compression jobs and the picked files/options. Outside the screen so leaving the page doesn't cancel a run. */
@OptIn(UnstableApi::class)
object CompressJobs {
    // Transformer must be used from a looper thread; main is the simplest one.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val main = Handler(Looper.getMainLooper())

    val mode = mutableIntStateOf(0) // 0 video, 1 audio, 2 photo
    val loading = mutableStateOf(false)
    val pickError = mutableStateOf<String?>(null)

    // --- Video
    val video = mutableStateOf<VideoSource?>(null)
    val resolution = mutableIntStateOf(0)   // target short side, 0 = original
    val quality = mutableIntStateOf(1)      // 0 high, 1 balanced, 2 small
    val hevcSupported: Boolean by lazy { hevcEncoderAvailable() }
    val hevc = mutableStateOf(hevcSupported)
    val removeAudio = mutableStateOf(false)
    private val _videoWork = MutableStateFlow<CWork>(CWork.Idle)
    val videoWork: StateFlow<CWork> = _videoWork.asStateFlow()
    private var videoJob: Job? = null
    @Volatile private var transformer: Transformer? = null

    // --- Audio
    val audio = mutableStateOf<AudioSource?>(null)
    val format = mutableStateOf(AudioFormat.Opus)
    val kbps = mutableIntStateOf(96)
    val mono = mutableStateOf(false)
    val keepMeta = mutableStateOf(true)
    private val _audioWork = MutableStateFlow<CWork>(CWork.Idle)
    val audioWork: StateFlow<CWork> = _audioWork.asStateFlow()
    private var audioJob: Job? = null

    private fun hevcEncoderAvailable(): Boolean = runCatching {
        MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.any { it.isEncoder && it.supportedTypes.any { t -> t.equals(MimeTypes.VIDEO_H265, true) } }
    }.getOrDefault(false)

    // --- Photo
    val photos = mutableStateOf<PhotoSource?>(null)
    val photoSide = mutableIntStateOf(2048)
    val photoQuality = mutableIntStateOf(1)
    val photoLimit = mutableIntStateOf(0)
    private val _photoWork = MutableStateFlow<CWork>(CWork.Idle)
    val photoWork: StateFlow<CWork> = _photoWork.asStateFlow()
    private var photoJob: Job? = null

    fun pickPhotos(context: Context, uris: List<Uri>) {
        if (uris.isEmpty() || _photoWork.value is CWork.Running) return
        val app = context.applicationContext
        loading.value = true; pickError.value = null
        scope.launch {
            try {
                val items = withContext(Dispatchers.IO) { uris.map { PhotoCompress.read(app, it) } }
                val thumb = withContext(Dispatchers.IO) { PhotoCompress.thumb(app, items.first().uri) }
                photos.value = PhotoSource(items, thumb); _photoWork.value = CWork.Idle
            } catch (e: Exception) {
                pickError.value = e.message ?: "Couldn't open those photos"
            }
            loading.value = false
        }
    }

    fun startPhotos(context: Context) {
        val p = photos.value ?: return
        if (_photoWork.value is CWork.Running) return
        val app = context.applicationContext
        val side = photoSide.intValue; val q = PhotoQualities[photoQuality.intValue]; val limit = photoLimit.intValue
        photoJob = scope.launch {
            _photoWork.value = CWork.Running(0f)
            _photoWork.value = try {
                var after = 0L
                val names = ArrayList<String>(); val uris = ArrayList<Uri>()
                p.items.forEachIndexed { i, item ->
                    val bytes = withContext(Dispatchers.Default) { PhotoCompress.encode(app, item, side, q, limit) }
                    val saved = withContext(Dispatchers.IO) {
                        Storage.save(app, Storage.Folder.Pictures, "Compressed", "${item.name}.jpg") { it.write(bytes) }
                    }
                    after += bytes.size; names += saved
                    scan(app, java.io.File(Storage.dir(Storage.Folder.Pictures, "Compressed"), saved))?.let { uris += it }
                    _photoWork.value = CWork.Running((i + 1f) / p.items.size, "Compressing ${i + 1} of ${p.items.size}…")
                }
                CWork.Done(
                    if (names.size == 1) names.first() else "${names.size} photos", Storage.Folder.Pictures, p.totalBytes, after,
                    uris.singleOrNull(), "image/jpeg", shareUris = uris, sub = "Compressed",
                )
            } catch (e: CancellationException) {
                CWork.Idle
            } catch (e: OutOfMemoryError) {
                CWork.Failed("Not enough memory for a photo this large. Choose a smaller size.")
            } catch (e: Exception) {
                CWork.Failed(friendly(e))
            }
        }
    }

    fun cancelPhotos() { photoJob?.cancel(); _photoWork.value = CWork.Idle }

    // ---------- Estimates

    /** Output size after the "never upscale" rule: [target] is the wanted short side, 0 = keep. */
    fun outputDims(v: VideoSource, target: Int): Pair<Int, Int> {
        val short = minOf(v.width, v.height)
        if (target == 0 || target >= short) return v.width to v.height
        val s = target.toFloat() / short
        return even(v.width * s) to even(v.height * s)
    }

    private fun even(f: Float) = ((f / 2f).roundToInt() * 2).coerceAtLeast(2)

    /** Target video bitrate, bits per second. 1080p30 H.264 ≈ 8 / 5 / 3 Mbps, scaled by pixels and fps; HEVC needs ~30% less. */
    fun videoBitrate(v: VideoSource, target: Int, q: Int, useHevc: Boolean): Int {
        val (w, h) = outputDims(v, target)
        val base = doubleArrayOf(8e6, 5e6, 3e6)[q]
        val pixels = w.toDouble() * h / (1920.0 * 1080.0)
        val fps = (v.fps / 30.0).coerceIn(0.8, 2.0)
        var bps = base * pixels * fps * (if (useHevc) 0.7 else 1.0)
        // Small clips need proportionally more bits per pixel, large 4K clips fewer.
        bps *= if (pixels < 1) sqrt(1 / pixels).coerceAtMost(1.35) else 1.0
        // Never go above what the source already has.
        if (v.bitrate > 0) bps = minOf(bps, v.bitrate * 0.9)
        return bps.coerceAtLeast(250_000.0).toInt()
    }

    fun estimateVideoBytes(v: VideoSource): Long {
        val vb = videoBitrate(v, resolution.intValue, quality.intValue, hevc.value)
        val ab = if (removeAudio.value || !v.hasAudio) 0 else 128_000
        return ((vb + ab) / 8.0 * v.durationMs / 1000.0).toLong()
    }

    fun estimateAudioBytes(a: AudioSource): Long = kbps.intValue * 1000L / 8 * a.clip.durationMs / 1000

    // ---------- Loading

    fun pickVideo(context: Context, uri: Uri) {
        if (_videoWork.value is CWork.Running) return
        val app = context.applicationContext
        loading.value = true; pickError.value = null
        scope.launch {
            try {
                val v = withContext(Dispatchers.IO) { readVideo(app, uri) }
                video.value = v; _videoWork.value = CWork.Idle
                // Drop a resolution that would now upscale.
                if (resolution.intValue >= minOf(v.width, v.height)) resolution.intValue = 0
            } catch (e: Exception) {
                pickError.value = e.message ?: "Couldn't open that video"
            }
            loading.value = false
        }
    }

    fun pickAudio(context: Context, uri: Uri) {
        if (_audioWork.value is CWork.Running) return
        val app = context.applicationContext
        loading.value = true; pickError.value = null
        scope.launch {
            try {
                val a = withContext(Dispatchers.IO) { Ffmpeg.import(app, uri, "compress${System.nanoTime()}").let { AudioSource(it, it.file.length()) } }
                audio.value?.clip?.file?.delete()
                audio.value = a; _audioWork.value = CWork.Idle
            } catch (e: Exception) {
                pickError.value = e.message ?: "Couldn't open that file"
            }
            loading.value = false
        }
    }

    private fun readVideo(context: Context, uri: Uri): VideoSource {
        var name = "Video"; var size = 0L
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use {
            if (it.moveToFirst()) {
                it.getString(0)?.let { n -> name = n.substringBeforeLast('.') }
                // Google's photo picker reports a bare media id instead of the file name.
                if (name.all { ch -> ch.isDigit() }) name = "Video " + java.text.SimpleDateFormat("yyyy-MM-dd HH.mm", java.util.Locale.US).format(java.util.Date())
                if (!it.isNull(1)) size = it.getLong(1)
            }
        }
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(context, uri)
            if (r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO) != "yes") throw IllegalStateException("That file doesn't look like a video")
            fun i(k: Int) = r.extractMetadata(k)?.toIntOrNull() ?: 0
            var w = i(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH); var h = i(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
            val rot = i(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
            if (rot == 90 || rot == 270) { val t = w; w = h; h = t }
            val dur = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            if (w <= 0 || h <= 0 || dur <= 0) throw IllegalStateException("That video can't be read")
            if (size <= 0) size = runCatching { context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } }.getOrNull() ?: 0L
            val bitrate = if (size > 0) size * 8000 / dur else i(MediaMetadataRetriever.METADATA_KEY_BITRATE).toLong()
            var fps = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)?.toFloatOrNull() ?: 0f
            if (fps <= 0f) {
                val frames = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT)?.toLongOrNull() ?: 0L
                if (frames > 0) fps = frames * 1000f / dur
            }
            if (fps !in 5f..240f) fps = 30f
            val thumb = runCatching {
                r.getScaledFrameAtTime(minOf(1_000_000L, dur * 500), MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 720, 720)
            }.getOrNull()
            return VideoSource(uri, name, dur, w, h, size, bitrate, fps, r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) == "yes", thumb)
        } finally { runCatching { r.release() } }
    }

    // ---------- Video job

    fun startVideo(context: Context) {
        val v = video.value ?: return
        if (_videoWork.value is CWork.Running) return
        val app = context.applicationContext
        val target = resolution.intValue.takeIf { it < minOf(v.width, v.height) } ?: 0
        val q = quality.intValue; val noAudio = removeAudio.value
        val wantHevc = hevc.value
        val out = File(File(app.cacheDir, "compress").apply { mkdirs() }, "video.mp4")
        videoJob = scope.launch {
            _videoWork.value = CWork.Running(0f)
            _videoWork.value = try {
                try {
                    runTransformer(app, v, target, q, wantHevc, noAudio, out, null)
                } catch (e: ExportException) {
                    if (!wantHevc) throw e
                    // The HEVC encoder gave up: try again with H.264.
                    out.delete()
                    runTransformer(app, v, target, q, false, noAudio, out, "Retrying with H.264…")
                }
                val title = "${v.name} (compressed)"
                val after = out.length()
                val saved = withContext(Dispatchers.IO) { Storage.saveFile(app, Storage.Folder.Videos, null, "$title.mp4", out) }
                val uri = scan(app, File(Storage.dir(Storage.Folder.Videos), saved))
                CWork.Done(saved, Storage.Folder.Videos, v.sizeBytes, after, uri, "video/mp4")
            } catch (e: CancellationException) {
                CWork.Idle
            } catch (e: Exception) {
                CWork.Failed(friendly(e))
            } finally {
                out.delete()
            }
        }
    }

    private suspend fun runTransformer(
        app: Context, v: VideoSource, target: Int, q: Int, useHevc: Boolean, noAudio: Boolean, out: File, note: String?,
    ) {
        out.delete()
        val done = CompletableDeferred<Unit>()
        var holder: Transformer? = null
        val bitrate = videoBitrate(v, target, q, useHevc)
        _videoWork.value = CWork.Running(0f, note)
        try {
            val t = Transformer.Builder(app)
                .setVideoMimeType(if (useHevc) MimeTypes.VIDEO_H265 else MimeTypes.VIDEO_H264)
                .setEncoderFactory(
                    DefaultEncoderFactory.Builder(app)
                        .setRequestedVideoEncoderSettings(VideoEncoderSettings.Builder().setBitrate(bitrate).build())
                        .build(),
                )
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) { done.complete(Unit) }
                    override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                        done.completeExceptionally(exportException)
                    }
                })
                .build()
            holder = t; transformer = t
            val effects = if (target == 0) Effects.EMPTY else Effects(emptyList(), listOf(Presentation.createForHeight(presentationHeight(v, target))))
            val item = EditedMediaItem.Builder(MediaItem.fromUri(v.uri)).setRemoveAudio(noAudio).setEffects(effects).build()
            t.start(item, out.absolutePath)

            val progress = ProgressHolder()
            while (!done.isCompleted) {
                if (t.getProgress(progress) == Transformer.PROGRESS_STATE_AVAILABLE) {
                    _videoWork.value = CWork.Running(progress.progress / 100f, note)
                }
                delay(300)
            }
            done.await()
        } catch (e: CancellationException) {
            // Cancel on the looper the transformer lives on.
            holder?.let { main.post { it.cancel() } }
            throw e
        } finally { transformer = null }
    }

    /** Height for [Presentation.createForHeight]: it is the displayed height, so portrait clips scale their long side. */
    private fun presentationHeight(v: VideoSource, target: Int): Int =
        if (v.height <= v.width) target else even(v.height * target.toFloat() / v.width)

    fun cancelVideo() { videoJob?.cancel(); _videoWork.value = CWork.Idle }

    // ---------- Audio job

    fun startAudio(context: Context) {
        val a = audio.value ?: return
        if (_audioWork.value is CWork.Running) return
        val app = context.applicationContext
        val fmt = format.value; val rate = kbps.intValue; val isMono = mono.value; val meta = keepMeta.value
        audioJob = scope.launch {
            _audioWork.value = CWork.Running(0f)
            val out = Ffmpeg.scratch(app, "compressed.${fmt.ext}")
            _audioWork.value = try {
                val title = "${a.clip.name} (compressed)"
                val cover = meta && fmt != AudioFormat.Opus
                try {
                    runFfmpeg(app, a, fmt, rate, isMono, meta, cover, out)
                } catch (e: Ffmpeg.Failed) {
                    if (!cover) throw e
                    out.delete()
                    runFfmpeg(app, a, fmt, rate, isMono, meta, false, out) // cover art in an odd format: retry without it
                }
                val after = out.length()
                val saved = Ffmpeg.publish(app, out, title)
                val uri = scan(app, File(Storage.dir(Storage.Folder.Audio), saved))
                CWork.Done(saved, Storage.Folder.Audio, a.sizeBytes, after, uri, "audio/*")
            } catch (e: CancellationException) {
                CWork.Idle
            } catch (e: Exception) {
                CWork.Failed(friendly(e))
            } finally { out.delete() }
        }
    }

    private suspend fun runFfmpeg(app: Context, a: AudioSource, fmt: AudioFormat, rate: Int, mono: Boolean, meta: Boolean, cover: Boolean, out: File) {
        val args = mutableListOf("-y", "-i", a.clip.file.absolutePath, "-map", "0:a:0")
        if (cover) args += listOf("-map", "0:v:0?", "-c:v", "copy", "-disposition:v", "attached_pic") else args += "-vn"
        args += listOf("-map_metadata", if (meta) "0" else "-1")
        when (fmt) {
            AudioFormat.Mp3 -> args += listOf("-c:a", "libmp3lame", "-b:a", "${rate}k", "-id3v2_version", "3")
            AudioFormat.Aac -> args += listOf("-c:a", "aac", "-b:a", "${rate}k", "-movflags", "+faststart")
            AudioFormat.Opus -> args += listOf("-c:a", "libopus", "-b:a", "${rate}k", "-vbr", "on", "-ar", "48000")
        }
        if (mono) args += listOf("-ac", "1")
        args += out.absolutePath
        Ffmpeg.run(app, args, a.clip.durationMs) { _audioWork.value = CWork.Running(it) }
    }

    fun cancelAudio() { audioJob?.cancel(); _audioWork.value = CWork.Idle }

    fun resetDone() {
        if (_videoWork.value !is CWork.Running) _videoWork.value = CWork.Idle
        if (_audioWork.value !is CWork.Running) _audioWork.value = CWork.Idle
        if (_photoWork.value !is CWork.Running) _photoWork.value = CWork.Idle
    }

    // ---------- Helpers

    private suspend fun scan(app: Context, file: File): Uri? = withTimeoutOrNull(3000) {
        suspendCancellableCoroutine { c -> MediaScannerConnection.scanFile(app, arrayOf(file.absolutePath), null) { _, uri -> if (c.isActive) c.resume(uri) } }
    }

    private fun friendly(e: Throwable): String = when (e) {
        is Storage.NoAccess -> e.message ?: "Allow All files access to save"
        is ExportException -> when (e.errorCode) {
            ExportException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED, ExportException.ERROR_CODE_DECODER_INIT_FAILED ->
                "This video's format isn't supported on this phone."
            ExportException.ERROR_CODE_ENCODER_INIT_FAILED, ExportException.ERROR_CODE_ENCODING_FORMAT_UNSUPPORTED ->
                "This phone couldn't start the encoder. Try H.264 or a different resolution."
            ExportException.ERROR_CODE_IO_FILE_NOT_FOUND, ExportException.ERROR_CODE_IO_NO_PERMISSION ->
                "Couldn't read the video any more. Pick it again."
            else -> "Compression failed (${e.errorCodeName.removePrefix("ERROR_CODE_").lowercase().replace('_', ' ')}). Try H.264."
        }
        is Ffmpeg.Failed -> "Couldn't compress this audio: ${e.message}"
        else -> e.message ?: "Compression failed"
    }
}
