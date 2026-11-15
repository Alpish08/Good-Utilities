package com.tools.app

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Keeps the last few crashes in a private file, because the phone drops a release app's logcat
 * lines. Shared from About → Crash log.
 */
object CrashLog {
    private const val MAX_CHARS = 60_000

    private fun file(c: Context) = File(c.applicationContext.filesDir, "crash-log.txt")

    /** Called once per process from [ToolsApp]; records the crash, then lets Android handle it as before. */
    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, e ->
            runCatching { record(app, thread, e) }
            previous?.uncaughtException(thread, e)
        }
    }

    private fun record(c: Context, thread: Thread, e: Throwable) {
        val version = runCatching { c.packageManager.getPackageInfo(c.packageName, 0).let { "${it.versionName} (${it.longVersionCode})" } }.getOrDefault("?")
        val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val entry = buildString {
            append("=== $time · Good Utilities $version · Android ${Build.VERSION.RELEASE} (${Build.MODEL})\n")
            append("Process ${Application.getProcessName()} · thread ${thread.name}\n")
            append(e.stackTraceToString())
            append("\n")
        }
        val f = file(c)
        val old = if (f.exists()) f.readText() else ""
        f.writeText((entry + old).take(MAX_CHARS)) // newest first
    }

    fun exists(c: Context) = file(c).let { it.exists() && it.length() > 0 }

    /** When the newest crash happened, like "Nov 15, 14:02", or null. */
    fun lastTime(c: Context): String? {
        if (!exists(c)) return null
        val raw = file(c).useLines { it.firstOrNull() }?.removePrefix("=== ")?.substringBefore(" · ") ?: return null
        return runCatching {
            val d = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).parse(raw)!!
            SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(d)
        }.getOrDefault(raw)
    }

    fun count(c: Context): Int = if (!exists(c)) 0 else file(c).useLines { l -> l.count { it.startsWith("=== ") } }

    fun share(c: Context) {
        val out = File(c.cacheDir, "Good Utilities crash log.txt")
        file(c).copyTo(out, overwrite = true)
        val uri = FileProvider.getUriForFile(c, "com.tools.app.files", out)
        val send = Intent(Intent.ACTION_SEND).setType("text/plain")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_SUBJECT, "Good Utilities crash log")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        c.startActivity(Intent.createChooser(send, "Share crash log").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun clear(c: Context) { file(c).delete() }
}

private typealias Application = android.app.Application
