package com.tools.app.music.engine

import android.content.Context
import android.webkit.CookieManager
import java.io.File

/**
 * Optional YouTube (Premium) sign-in. The user signs in on Google's own page in a WebView;
 * we only export the resulting youtube.com cookies to a private Netscape cookies.txt for yt-dlp.
 * Nothing is sent anywhere except to YouTube itself.
 */
object YouTubeAccount {
    private val DOMAINS = listOf("https://www.youtube.com", "https://music.youtube.com", "https://accounts.google.com")

    fun cookieFile(context: Context) = File(context.filesDir, "youtube_cookies.txt")
    fun isSignedIn(context: Context) = cookieFile(context).length() > 0

    /** True once Google has issued YouTube session cookies. */
    fun hasSession(): Boolean =
        CookieManager.getInstance().getCookie("https://www.youtube.com")?.contains("SAPISID=") == true

    fun export(context: Context): Boolean {
        val cm = CookieManager.getInstance()
        cm.flush()
        val expiry = System.currentTimeMillis() / 1000 + 180L * 24 * 3600
        val lines = mutableListOf("# Netscape HTTP Cookie File")
        for (url in DOMAINS) {
            val host = url.removePrefix("https://")
            val domain = "." + host.substringAfter("www.").let { if (it.startsWith("music.")) it.removePrefix("music.") else it }
            cm.getCookie(url)?.split(";")?.map { it.trim() }?.filter { '=' in it }?.forEach { c ->
                val name = c.substringBefore('='); val value = c.substringAfter('=')
                lines += listOf(domain, "TRUE", "/", "TRUE", expiry.toString(), name, value).joinToString("\t")
            }
        }
        val unique = lines.distinctBy { it.split('\t').let { p -> if (p.size > 5) p[0] + p[5] else it } }
        if (unique.none { "SAPISID" in it }) return false
        cookieFile(context).writeText(unique.joinToString("\n") + "\n")
        return true
    }

    fun signOut(context: Context) {
        cookieFile(context).delete()
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
    }
}
