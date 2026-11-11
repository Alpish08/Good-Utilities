package com.tools.app

import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import android.provider.Settings
import java.io.File
import java.io.OutputStream

/**
 * Everything Good Utilities creates lives in Internal Storage/Good Utilities/<folder>.
 * A folder at the top of storage needs "All files access", which you switch on once in Settings.
 * Saved files are handed to the media scanner so they show up in Samsung Music and Gallery.
 */
object Storage {
    const val ROOT = "Good Utilities"

    enum class Folder(val path: String) {
        Music("Music"),       // Music downloader (one subfolder per playlist)
        Audio("Audio"),       // Audio merger and trimmer
        Pictures("Pictures"), // Image blur, Background remover, QR codes
        APKs("APKs"),         // APK extractor
        Documents("Documents"), // PDF editor, converted documents
        Videos("Videos"),     // Compressed / converted video
    }

    class NoAccess : Exception("Allow \"All files access\" for Good Utilities to save into Internal Storage/$ROOT")

    fun hasAccess() = Environment.isExternalStorageManager()

    /** The settings page where you allow All files access for this app. */
    fun accessIntent(context: Context) =
        Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Human-readable location, e.g. "Good Utilities/Music/Fallen". */
    fun label(folder: Folder, sub: String? = null) = listOfNotNull(ROOT, folder.path, sub?.takeIf { it.isNotBlank() }).joinToString("/")

    fun dir(folder: Folder, sub: String? = null) = File(Environment.getExternalStorageDirectory(), label(folder, sub))

    fun exists(folder: Folder, sub: String?, name: String) = File(dir(folder, sub), name).exists()

    /**
     * Writes a new file named [name] (made unique with " (1)", " (2)"… if taken) and returns the
     * name it was saved under. The file only appears once it is complete.
     */
    fun save(context: Context, folder: Folder, sub: String?, name: String, write: (OutputStream) -> Unit): String {
        if (!hasAccess()) throw NoAccess()
        val dir = dir(folder, sub).apply { mkdirs() }
        val target = unique(dir, safe(name))
        val part = File(dir, ".${target.name}.part")
        try {
            part.outputStream().buffered().use(write)
            if (!part.renameTo(target)) error("Couldn't save ${target.name}")
        } catch (e: Exception) {
            part.delete(); throw e
        }
        MediaScannerConnection.scanFile(context.applicationContext, arrayOf(target.absolutePath), null, null)
        return target.name
    }

    fun saveFile(context: Context, folder: Folder, sub: String?, name: String, source: File): String =
        save(context, folder, sub, name) { out -> source.inputStream().use { it.copyTo(out) } }

    fun safe(name: String) = name.replace(Regex("[\\\\/:*?\"<>|]"), " ").replace(Regex("\\s+"), " ").trim().ifEmpty { "file" }

    private fun unique(dir: File, name: String): File {
        var f = File(dir, name)
        if (!f.exists()) return f
        val base = name.substringBeforeLast('.'); val ext = name.substringAfterLast('.', "")
        var i = 1
        while (f.exists()) f = File(dir, "$base ($i)" + if (ext.isNotEmpty()) ".$ext" else "").also { i++ }
        return f
    }
}
