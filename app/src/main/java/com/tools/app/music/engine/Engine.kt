package com.tools.app.music.engine

import com.tools.app.R
import com.tools.app.ui.t
import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import org.json.JSONObject
import java.io.File

/** Thin wrapper around yt-dlp (+ FFmpeg) bundled by youtubedl-android. All calls block. */
object Engine {
    const val PROCESS_FETCH = "spool-fetch"
    const val PROCESS_DOWNLOAD = "spool-download"
    fun processId(track: Track) = "$PROCESS_DOWNLOAD-${track.id}"

    private val UNAVAILABLE = setOf("[Private video]", "[Deleted video]", "[Unavailable video]")

    fun fetchPlaylist(url: String): Playlist {
        val request = YoutubeDLRequest(url).apply {
            addOption("--flat-playlist")
            addOption("--dump-single-json")
            addOption("--no-warnings")
        }
        val out = YoutubeDL.getInstance().execute(request, PROCESS_FETCH).out
        val root = JSONObject(out.substring(out.indexOf('{')))
        val entries = root.optJSONArray("entries")

        if (entries == null) {
            // A single video link: treat it as a one-song "playlist".
            val track = parseEntry(root, 1) ?: error(t(R.string.music_couldn_t_read_that_video))
            return Playlist(track.id, track.title, track.artist, track.thumbnail, listOf(track))
        }
        val tracks = (0 until entries.length())
            .mapNotNull { i -> entries.optJSONObject(i)?.let { parseEntry(it, i + 1) } }
            .filterNot { it.title in UNAVAILABLE }
        if (tracks.isEmpty()) error(t(R.string.music_this_playlist_is_empty_or_private))

        val thumbs = root.optJSONArray("thumbnails")
        val cover = thumbs?.optJSONObject(thumbs.length() - 1)?.str("url") ?: tracks.first().thumbnail
        return Playlist(
            id = root.str("id") ?: "playlist",
            title = root.str("title") ?: t(R.string.music_youtube_playlist),
            uploader = (root.str("uploader") ?: root.str("channel") ?: "").removeSuffix(" - Topic"),
            thumbnail = cover,
            tracks = tracks,
        )
    }

    private fun parseEntry(e: JSONObject, index: Int): Track? {
        val id = e.str("id") ?: return null
        return Track(
            id = id,
            index = index,
            title = e.str("title") ?: id,
            artist = (e.str("channel") ?: e.str("uploader") ?: "").removeSuffix(" - Topic"),
            durationSec = if (e.isNull("duration")) null else e.optDouble("duration").toInt(),
            thumbnail = "https://i.ytimg.com/vi/$id/mqdefault.jpg",
        )
    }

    /**
     * Downloads one track as MP3 with embedded cover + tags and copies it into Music/.
     * Returns false when skipped because the file already exists.
     */
    fun downloadTrack(
        context: Context,
        playlist: Playlist,
        track: Track,
        options: Options,
        onProgress: (progress: Float, status: Status) -> Unit,
    ): Boolean {
        val digits = maxOf(2, playlist.tracks.maxOf { it.index }.toString().length)
        val baseName = buildString {
            if (options.numberTracks && playlist.tracks.size > 1) append(track.index.toString().padStart(digits, '0')).append(" - ")
            append(track.title)
        }.safeFileName()
        val fileName = "$baseName.mp3"
        // Good Utilities/Music[/<playlist>]; songs from older versions in Music/YouTube still count as existing.
        val sub = if (options.subfolder && playlist.tracks.size > 1) playlist.title.safeFileName() else null
        val legacyPath = Environment.DIRECTORY_MUSIC + "/YouTube" + (sub?.let { "/$it" } ?: "")
        if (options.skipExisting && (com.tools.app.Storage.exists(com.tools.app.Storage.Folder.Music, sub, fileName) ||
                MusicStore.exists(context, legacyPath, fileName))) return false
        if (!com.tools.app.Storage.hasAccess()) throw com.tools.app.Storage.NoAccess()

        // Own folder per track: several tracks download at the same time.
        val work = File(context.cacheDir, "work/${track.id}").apply { deleteRecursively(); mkdirs() }
        fun request(withAccount: Boolean) = YoutubeDLRequest(track.url).apply {
            addOption("--no-playlist")
            addOption("-f", "bestaudio/best")
            addOption("-x")
            addOption("--audio-format", "mp3")
            addOption("--audio-quality", options.quality.arg)
            addOption("--embed-metadata")
            addOption("--embed-thumbnail")
            addOption("--convert-thumbnails", "jpg")
            if (options.squareCovers) {
                // Center-crop the 16:9 YouTube thumbnail into a square album cover.
                addOption("--ppa", "ThumbnailsConvertor:-qmin 1 -q:v 1 -vf crop=\"'if(gt(ih,iw),iw,ih)':'if(gt(iw,ih),ih,iw)'\"")
            }
            // "Artist - Topic" channels → "Artist"
            addCommands(listOf("--replace-in-metadata", "uploader,channel", " - Topic$", ""))
            if (playlist.tracks.size > 1) {
                addOption("--parse-metadata", literalTag(playlist.title, "meta_album"))
                addOption("--parse-metadata", literalTag(track.index.toString(), "meta_track"))
                if (playlist.uploader.isNotBlank()) addOption("--parse-metadata", literalTag(playlist.uploader, "meta_album_artist"))
            }
            addOption("--no-mtime")
            addOption("--cache-dir", File(context.cacheDir, "yt-dlp").absolutePath)
            addOption("-o", File(work, "track.%(ext)s").absolutePath)
            if (withAccount) addOption("--cookies", YouTubeAccount.cookieFile(context).absolutePath)
        }

        var last = -1f
        fun run(withAccount: Boolean) = YoutubeDL.getInstance().execute(request(withAccount), processId(track)) { percent, _, line ->
            val converting = POSTPROCESS.any { line.contains(it) }
            val p = (percent / 100f).coerceIn(0f, 1f)
            if (converting) onProgress(1f, Status.Converting)
            else if (p - last >= 0.01f || p == 1f) { last = p; onProgress(p, Status.Downloading) }
        }
        if (YouTubeAccount.isSignedIn(context)) {
            // Premium account first (better audio, fewer blocks); fall back to anonymous if YouTube rejects it.
            try { run(true) } catch (e: YoutubeDL.CanceledException) { throw e } catch (e: Exception) {
                work.listFiles()?.forEach { it.delete() }; last = -1f; run(false)
            }
        } else run(false)

        val mp3 = File(work, "track.mp3")
        if (!mp3.exists()) error(t(R.string.music_yt_dlp_finished_but_produced_no_mp3))
        onProgress(1f, Status.Converting)
        com.tools.app.Storage.saveFile(context, com.tools.app.Storage.Folder.Music, sub, fileName, mp3)
        work.deleteRecursively()
        return true
    }

    private val POSTPROCESS = listOf("[ExtractAudio]", "[ThumbnailsConvertor]", "[EmbedThumbnail]", "[Metadata]")

    /**
     * yt-dlp's --parse-metadata takes "[WHEN:]FROM:TO" where FROM is a field name or template.
     * A "@@" prefix guarantees FROM is never mistaken for a field name; the TO regex strips it again.
     */
    private fun literalTag(value: String, field: String): String {
        val escaped = value.replace("%", "%%").replace(":", "\\:")
        return "pre_process:@@$escaped:(?s)@@(?P<$field>.+)"
    }

    private fun String.safeFileName() =
        replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_").trim().trimEnd('.').take(120).ifBlank { "track" }

    fun extractUrl(raw: String): String? {
        Regex("https?://\\S+").find(raw)?.let { return it.value }
        val t = raw.trim()
        return if (t.contains("youtu") && !t.contains(' ')) "https://$t" else null
    }
}

private fun JSONObject.str(key: String): String? =
    if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }

fun Throwable.friendlyMessage(): String {
    val text = message ?: javaClass.simpleName
    val line = text.lines().lastOrNull { it.startsWith("ERROR:") }
        ?: text.lines().firstOrNull { it.isNotBlank() } ?: text
    val clean = line.removePrefix("ERROR: ").replace(Regex("^\\[[^]]+] [\\w-]+: "), "").take(240)
    return if ("403" in clean) t(R.string.music_youtube_refused_the_download_http_403) else clean
}

/** Writes into the shared Music collection — no storage permission needed on Android 10+. */
object MusicStore {
    private val collection get() = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

    fun exists(context: Context, relativePath: String, name: String): Boolean =
        context.contentResolver.query(
            collection,
            arrayOf(MediaStore.MediaColumns._ID),
            "${MediaStore.MediaColumns.RELATIVE_PATH}=? AND ${MediaStore.MediaColumns.DISPLAY_NAME}=?",
            arrayOf("$relativePath/", name),
            null,
        )?.use { it.count > 0 } ?: false

    fun save(context: Context, file: File, relativePath: String, name: String) {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "audio/mpeg")
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(collection, values) ?: error(t(R.string.music_couldn_t_create_the_file_in_music))
        try {
            resolver.openOutputStream(uri)!!.use { out -> file.inputStream().use { it.copyTo(out) } }
            resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
    }
}
