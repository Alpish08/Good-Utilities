package com.tools.app.applock

import android.content.Context
import android.os.SystemClock
import android.util.AtomicFile
import android.util.Base64
import java.io.File
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * The Tools PIN: stored only as a salted PBKDF2 hash (never the PIN itself) in app-private
 * storage, readable by both the app and the ":lock" process.
 */
object PinStore {
    const val MIN = 4
    const val MAX = 8
    private const val ITERATIONS = 60_000
    private const val MAX_FAILURES = 5
    private const val COOLDOWN_MS = 30_000L

    private fun file(c: Context) = AtomicFile(File(c.filesDir, "applock_pin"))

    private fun read(c: Context): Triple<Int, ByteArray, ByteArray>? = runCatching {
        val p = String(file(c).readFully()).trim().split(':')
        Triple(p[0].toInt(), Base64.decode(p[1], Base64.NO_WRAP), Base64.decode(p[2], Base64.NO_WRAP))
    }.getOrNull()

    fun isSet(c: Context) = read(c) != null
    fun length(c: Context) = read(c)?.first ?: 0

    fun set(c: Context, pin: String) {
        require(pin.length in MIN..MAX && pin.all { it.isDigit() })
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val line = "${pin.length}:${Base64.encodeToString(salt, Base64.NO_WRAP)}:${Base64.encodeToString(derive(pin, salt), Base64.NO_WRAP)}"
        val f = file(c); val out = f.startWrite()
        try { out.write(line.toByteArray()); f.finishWrite(out) } catch (e: Exception) { f.failWrite(out); throw e }
    }

    fun clear(c: Context) = file(c).delete()

    /** Slow on purpose (~tens of ms); call off the main thread. */
    fun verify(c: Context, pin: String): Boolean {
        val (len, salt, hash) = read(c) ?: return false
        if (pin.length != len) return false
        return MessageDigest.isEqual(derive(pin, salt), hash)
    }

    private fun derive(pin: String, salt: ByteArray): ByteArray =
        SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(PBEKeySpec(pin.toCharArray(), salt, ITERATIONS, 256)).encoded

    // --- Wrong-PIN throttling (per lock process).
    private var failures = 0
    private var blockedUntil = 0L

    fun blockedForMs(): Long = (blockedUntil - SystemClock.elapsedRealtime()).coerceAtLeast(0)

    fun onResult(ok: Boolean) {
        if (ok) { failures = 0; blockedUntil = 0; return }
        failures++
        if (failures >= MAX_FAILURES) { failures = 0; blockedUntil = SystemClock.elapsedRealtime() + COOLDOWN_MS }
    }
}
