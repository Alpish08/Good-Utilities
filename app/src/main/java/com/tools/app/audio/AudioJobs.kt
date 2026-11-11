package com.tools.app.audio

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

sealed interface Work {
    data object Idle : Work
    data class Running(val progress: Float) : Work
    data class Done(val fileName: String) : Work
    data class Failed(val message: String) : Work
}

/**
 * Audio merger and trimmer jobs. They live outside the screens so a job keeps going (and its
 * progress is still there) when you leave the page and come back.
 */
object AudioJobs {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // --- Merger
    val clips = mutableStateListOf<Ffmpeg.Clip>()
    val crossfade = mutableStateOf(3f)
    private val _merge = MutableStateFlow<Work>(Work.Idle)
    val merge: StateFlow<Work> = _merge.asStateFlow()
    private var mergeJob: Job? = null

    /** Crossfade actually used: never longer than half of the shortest track. */
    fun effectiveFade(): Float {
        val shortest = clips.minOfOrNull { it.durationMs } ?: return 0f
        return crossfade.value.coerceAtMost((shortest / 2000f - 0.1f).coerceAtLeast(0f))
    }

    fun mergedLengthMs(): Long =
        (clips.sumOf { it.durationMs } - (effectiveFade() * 1000).toLong() * (clips.size - 1).coerceAtLeast(0)).coerceAtLeast(0)

    fun startMerge(context: Context) {
        if (clips.size < 2 || _merge.value is Work.Running) return
        val app = context.applicationContext
        val list = clips.toList()
        val fade = effectiveFade()
        val total = mergedLengthMs()
        mergeJob = scope.launch {
            _merge.value = Work.Running(0f)
            _merge.value = try {
                val out = Ffmpeg.scratch(app, "merged.mp3")
                val args = mutableListOf("-y")
                list.forEach { args += listOf("-i", it.file.absolutePath) }
                // Same format for every input, then chain them: [a0][a1] → x1, [x1][a2] → x2 …
                val graph = buildString {
                    list.indices.forEach { append("[$it:a]aformat=sample_fmts=fltp:sample_rates=44100:channel_layouts=stereo[a$it];") }
                    if (fade > 0f) {
                        var prev = "a0"
                        for (i in 1 until list.size) {
                            val outLabel = if (i == list.lastIndex) "out" else "x$i"
                            append("[$prev][a$i]acrossfade=d=${"%.2f".format(java.util.Locale.US, fade)}:c1=tri:c2=tri[$outLabel]")
                            if (i != list.lastIndex) append(';')
                            prev = outLabel
                        }
                    } else {
                        list.indices.forEach { append("[a$it]") }
                        append("concat=n=${list.size}:v=0:a=1[out]")
                    }
                }
                args += listOf("-filter_complex", graph, "-map", "[out]")
                // Keep the first song's cover art when it is an MP3 with one.
                if (list.first().ext == "mp3") args += listOf("-map", "0:v?", "-c:v", "copy", "-disposition:v", "attached_pic")
                val title = list.first().name + " (merged)"
                args += listOf("-c:a", "libmp3lame", "-q:a", "0", "-id3v2_version", "3", "-metadata", "title=$title", out.absolutePath)
                Ffmpeg.run(app, args, total) { _merge.value = Work.Running(it) }
                Work.Done(Ffmpeg.publish(app, out, title))
            } catch (e: CancellationException) {
                Work.Idle
            } catch (e: Exception) {
                Work.Failed(e.message ?: "Merging failed")
            }
        }
    }

    fun cancelMerge() { mergeJob?.cancel(); _merge.value = Work.Idle }
    fun resetMerge() { if (_merge.value !is Work.Running) _merge.value = Work.Idle }

    // --- Trimmer
    val trimClip = mutableStateOf<Ffmpeg.Clip?>(null)
    val trimStart = mutableStateOf(0L)
    val trimEnd = mutableStateOf(0L)
    private val _trim = MutableStateFlow<Work>(Work.Idle)
    val trim: StateFlow<Work> = _trim.asStateFlow()
    private var trimJob: Job? = null

    fun startTrim(context: Context) {
        val clip = trimClip.value ?: return
        if (_trim.value is Work.Running) return
        val app = context.applicationContext
        val start = trimStart.value; val end = trimEnd.value
        trimJob = scope.launch {
            _trim.value = Work.Running(0f)
            _trim.value = try {
                val out = Ffmpeg.scratch(app, "trimmed.${clip.ext}")
                val title = clip.name + " (trimmed)"
                // Stream copy: no re-encoding, so it is instant and lossless.
                val args = listOf(
                    "-y", "-ss", Ffmpeg.seconds(start), "-i", clip.file.absolutePath,
                    "-t", Ffmpeg.seconds(end - start),
                    "-map", "0:a", "-map", "0:v?", "-c", "copy", "-map_metadata", "0",
                    "-metadata", "title=$title", out.absolutePath,
                )
                Ffmpeg.run(app, args, end - start) { _trim.value = Work.Running(it) }
                Work.Done(Ffmpeg.publish(app, out, title))
            } catch (e: CancellationException) {
                Work.Idle
            } catch (e: Exception) {
                Work.Failed(e.message ?: "Trimming failed")
            }
        }
    }

    fun cancelTrim() { trimJob?.cancel(); _trim.value = Work.Idle }
    fun resetTrim() { if (_trim.value !is Work.Running) _trim.value = Work.Idle }
}
