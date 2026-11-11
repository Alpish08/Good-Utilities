package com.tools.app.music.engine

data class Track(
    val id: String,
    val index: Int,          // 1-based position in the playlist
    val title: String,
    val artist: String,
    val durationSec: Int?,
    val thumbnail: String,
) {
    val url get() = "https://www.youtube.com/watch?v=$id"
}

data class Playlist(
    val id: String,
    val title: String,
    val uploader: String,
    val thumbnail: String?,
    val tracks: List<Track>,
) {
    val totalSeconds get() = tracks.sumOf { it.durationSec ?: 0 }
}

enum class Phase { Idle, Fetching, Ready, Downloading, Finished }

enum class Status { Queued, Downloading, Converting, Done, Skipped, Failed;
    val finished get() = this == Done || this == Skipped || this == Failed
}

data class TrackState(
    val status: Status = Status.Queued,
    val progress: Float = 0f,   // 0..1
    val error: String? = null,
)

sealed interface EngineState {
    data object Preparing : EngineState
    data object Updating : EngineState
    data class Ready(val version: String?) : EngineState
    data class Failed(val message: String) : EngineState
}

data class AppState(
    val link: String = "",
    val phase: Phase = Phase.Idle,
    val playlist: Playlist? = null,
    val selected: Set<String> = emptySet(),
    val trackStates: Map<String, TrackState> = emptyMap(),
    val error: String? = null,
    val engine: EngineState = EngineState.Preparing,
    val engineMessage: String? = null,
) {
    val totalQueued get() = trackStates.size
    val finishedCount get() = trackStates.values.count { it.status.finished }
    fun count(status: Status) = trackStates.values.count { it.status == status }

    /** 0..1 progress across the whole batch. */
    val overallProgress: Float
        get() {
            if (trackStates.isEmpty()) return 0f
            val partial = trackStates.values.sumOf {
                (if (it.status.finished) 1f else if (it.status == Status.Converting) 0.95f else it.progress * 0.9f).toDouble()
            }
            return (partial / trackStates.size).toFloat()
        }
}
