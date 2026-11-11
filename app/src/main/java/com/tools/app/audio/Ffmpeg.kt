package com.tools.app.audio

import android.content.ContentValues
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume

/**
 * Runs the FFmpeg 7.1 build that already ships with Music downloader (youtubedl-android), so the
 * audio tools add nothing to the APK. ffmpeg-kit, which was suggested, was retired in 2025 and its
 * binaries were pulled from Maven Central.
 */
object Ffmpeg {
    class Failed(message: String) : Exception(message)

    /** One audio file picked by the user, copied into the cache so FFmpeg can read it by path. */
    data class Clip(val uri: Uri, val name: String, val file: File, val durationMs: Long, val ext: String)

    private val lock = Any()
    @Volatile private var ready = false

    private fun prepare(context: Context) = synchronized(lock) {
        if (ready) return
        val app = context.applicationContext
        // Both are no-ops after the first unpack (shared with Music downloader).
        YoutubeDL.getInstance().init(app)
        FFmpeg.getInstance().init(app)
        ready = true
    }

    /**
     * Runs ffmpeg with [args]. [totalMs] is the expected output length, used to turn FFmpeg's
     * progress reports into 0..1 for [onProgress]. Cancelling the coroutine kills the process.
     */
    suspend fun run(context: Context, args: List<String>, totalMs: Long, onProgress: (Float) -> Unit) = withContext(Dispatchers.IO) {
        prepare(context)
        val packages = File(context.noBackupFilesDir, "youtubedl-android/packages")
        val binary = File(context.applicationInfo.nativeLibraryDir, "libffmpeg.so")
        val cmd = listOf(binary.absolutePath, "-hide_banner", "-nostdin", "-loglevel", "error", "-progress", "pipe:1", "-nostats") + args
        val process = ProcessBuilder(cmd).apply {
            environment()["LD_LIBRARY_PATH"] = listOf("python/usr/lib", "ffmpeg/usr/lib").joinToString(":") { File(packages, it).absolutePath }
            environment()["TMPDIR"] = context.cacheDir.absolutePath
        }.start()

        val errors = StringBuilder()
        val errThread = Thread { process.errorStream.bufferedReader().forEachLine { if (errors.length < 4000) errors.appendLine(it) } }.apply { start() }
        val code = suspendCancellableCoroutine { cont ->
            cont.invokeOnCancellation { process.destroy() }
            Thread {
                process.inputStream.bufferedReader().forEachLine { line ->
                    if (line.startsWith("out_time_us=") && totalMs > 0) {
                        line.substringAfter('=').toLongOrNull()?.let { onProgress((it / 1000f / totalMs).coerceIn(0f, 1f)) }
                    }
                }
                cont.resume(process.waitFor())
            }.start()
        }
        errThread.join(500)
        currentCoroutineContext().ensureActive()
        if (code != 0) throw Failed(errors.lines().lastOrNull { it.isNotBlank() }?.take(160) ?: "FFmpeg stopped with code $code")
        onProgress(1f)
    }

    /** Copies a picked file into the cache and reads its name and length. */
    suspend fun import(context: Context, uri: Uri, slot: String): Clip = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        } ?: "Audio"
        val ext = name.substringAfterLast('.', "mp3").lowercase().takeIf { it.length in 2..5 } ?: "mp3"
        val dir = File(context.cacheDir, "audio").apply { mkdirs() }
        val file = File(dir, "$slot.$ext")
        resolver.openInputStream(uri)!!.use { input -> file.outputStream().use { input.copyTo(it) } }
        val duration = MediaMetadataRetriever().run {
            try { setDataSource(file.absolutePath); extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L }
            finally { release() }
        }
        if (duration <= 0) throw Failed("That file doesn't look like audio")
        Clip(uri, name.substringBeforeLast('.'), file, duration, ext)
    }

    fun scratch(context: Context, name: String) = File(File(context.cacheDir, "audio").apply { mkdirs() }, name)

    /** Moves a finished file into Good Utilities/Audio (scanned, so it shows up in Samsung Music). Returns its name. */
    suspend fun publish(context: Context, file: File, displayName: String): String = withContext(Dispatchers.IO) {
        com.tools.app.Storage.saveFile(context, com.tools.app.Storage.Folder.Audio, null, "${displayName.trim().ifEmpty { "Audio" }}.${file.extension}", file)
            .also { file.delete() }
    }

    fun clearCache(context: Context) { File(context.cacheDir, "audio").deleteRecursively() }

    fun seconds(ms: Long) = "%.3f".format(java.util.Locale.US, ms / 1000.0)
}
