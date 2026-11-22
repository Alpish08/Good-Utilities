package com.tools.app.briefs

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.sun.mail.imap.IMAPFolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import java.util.Properties
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.mail.AuthenticationFailedException
import javax.mail.FetchProfile
import javax.mail.Flags
import javax.mail.Folder
import javax.mail.Message
import javax.mail.Multipart
import javax.mail.Part
import javax.mail.Session
import javax.mail.Store
import javax.mail.UIDFolder
import javax.mail.internet.InternetAddress

/** The Gmail account Briefs reads: address, app password (encrypted with a Keystore key) and the label. */
data class Account(val email: String, val password: String, val label: String)

object BriefsAccount {
    private const val KEY = "briefs_login"

    fun get(c: Context): Account? = runCatching {
        val p = prefs(c)
        val email = p.getString("email", null) ?: return null
        val sealed = p.getString("password", null) ?: return null
        Account(email, decrypt(sealed), p.getString("label", null) ?: "Reading")
    }.getOrNull()

    fun save(c: Context, a: Account) {
        prefs(c).edit().putString("email", a.email).putString("password", encrypt(a.password)).putString("label", a.label).apply()
    }

    fun clear(c: Context) {
        prefs(c).edit().clear().apply()
        runCatching { KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(KEY) }
    }

    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences("briefs_account", Context.MODE_PRIVATE)

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(KEY, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(
                KeyGenParameterSpec.Builder(KEY, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build(),
            )
        }.generateKey()
    }

    private fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val out = cipher.iv + cipher.doFinal(plain.toByteArray())
        return Base64.encodeToString(out, Base64.NO_WRAP)
    }

    private fun decrypt(sealed: String): String {
        val raw = Base64.decode(sealed, Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, raw, 0, 12)) }
        return String(cipher.doFinal(raw, 12, raw.size - 12))
    }
}

/** One synced email. Its body is kept as HTML in briefs/<uid>.html. */
data class Brief(
    val uid: Long, val subject: String, val sender: String, val address: String,
    val date: Long, val seen: Boolean, val words: Int, val preview: String,
) {
    val minutes get() = (words / 230).coerceAtLeast(1)
}

/**
 * Briefs: the emails under one Gmail label, fetched over IMAP only while the app is open (no background work,
 * no notifications). Syncing peeks, so nothing changes in Gmail; opening a brief marks it read there.
 */
object Briefs {
    /** How many of the label's newest emails are kept. */
    private const val KEEP = 80

    val list = MutableStateFlow<List<Brief>>(emptyList())
    val syncing = MutableStateFlow(false)
    val error = MutableStateFlow<String?>(null)
    @Volatile var lastSync = 0L
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var loaded = false
    /** Bumped when the cache is cleared, so a sync that started before that can't write its stale list back. */
    @Volatile private var generation = 0
    /** A sync asked for while one was running: run again right after it. */
    @Volatile private var again = false

    private fun dir(c: Context) = File(c.filesDir, "briefs").apply { mkdirs() }
    private fun index(c: Context) = File(dir(c), "index.json")
    fun body(c: Context, uid: Long): String = runCatching { File(dir(c), "$uid.html").readText() }.getOrDefault("")

    @Synchronized
    fun load(c: Context) {
        if (loaded) return
        loaded = true
        list.value = runCatching {
            val o = JSONObject(index(c).readText())
            lastSync = o.optLong("synced")
            val a = o.getJSONArray("items")
            (0 until a.length()).map { i ->
                a.getJSONObject(i).run {
                    Brief(getLong("u"), getString("s"), getString("f"), optString("a"), getLong("d"), getBoolean("r"), getInt("w"), optString("p"))
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun save(c: Context, items: List<Brief>, validity: Long = prefs(c).getLong("validity", -1)) {
        val a = JSONArray()
        items.forEach {
            a.put(JSONObject().put("u", it.uid).put("s", it.subject).put("f", it.sender).put("a", it.address)
                .put("d", it.date).put("r", it.seen).put("w", it.words).put("p", it.preview))
        }
        index(c).writeText(JSONObject().put("synced", lastSync).put("items", a).toString())
        prefs(c).edit().putLong("validity", validity).apply()
    }

    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences("briefs", Context.MODE_PRIVATE)

    /** Signs in once to check the account (and that the label exists) before it's saved. */
    suspend fun test(a: Account): String? = withContext(Dispatchers.IO) {
        runCatching {
            connect(a).use { store ->
                val f = store.getFolder(a.label)
                if (!f.exists()) throw NoLabel(a.label)
            }
            null
        }.getOrElse { message(it) }
    }

    fun sync(c: Context) {
        val ctx = c.applicationContext
        if (syncing.value) { again = true; return }
        val account = BriefsAccount.get(ctx) ?: return
        syncing.value = true
        scope.launch {
            try {
                pull(ctx, account, generation)
                error.value = null
            } catch (e: Throwable) {
                error.value = message(e)
            } finally {
                syncing.value = false
                if (again) { again = false; sync(ctx) }
            }
        }
    }

    /** Marks a brief read here and in Gmail (the only change Briefs ever makes to the mailbox). */
    fun markRead(c: Context, uid: Long) {
        val ctx = c.applicationContext
        val b = list.value.firstOrNull { it.uid == uid } ?: return
        if (b.seen) return
        list.value = list.value.map { if (it.uid == uid) it.copy(seen = true) else it }
        save(ctx, list.value)
        val account = BriefsAccount.get(ctx) ?: return
        scope.launch {
            runCatching {
                connect(account).use { store ->
                    val f = store.getFolder(account.label) as IMAPFolder
                    f.open(Folder.READ_WRITE)
                    try { f.getMessageByUID(uid)?.setFlag(Flags.Flag.SEEN, true) } finally { f.close(false) }
                }
            }
        }
    }

    /** Forgets the synced emails (e.g. after switching labels); the account stays. */
    fun clearCache(c: Context) {
        generation++
        dir(c).deleteRecursively()
        prefs(c).edit().clear().apply()
        list.value = emptyList()
        error.value = null
        lastSync = 0
    }

    fun signOut(c: Context) {
        generation++
        BriefsAccount.clear(c)
        dir(c).deleteRecursively()
        prefs(c).edit().clear().apply()
        list.value = emptyList()
        error.value = null
        lastSync = 0
    }

    private fun pull(c: Context, account: Account, gen: Int) {
        connect(account).use { store ->
            val f = store.getFolder(account.label) as IMAPFolder
            if (!f.exists()) throw NoLabel(account.label)
            f.open(Folder.READ_ONLY)
            try {
                // UIDs are only meaningful within one UIDVALIDITY; if Gmail resets it, start over.
                val validity = f.uidValidity
                val known = if (validity == prefs(c).getLong("validity", -1)) list.value.associateBy { it.uid } else emptyMap()
                val n = f.messageCount
                val msgs = if (n == 0) emptyArray() else f.getMessages(maxOf(1, n - KEEP + 1), n)
                f.fetch(msgs, FetchProfile().apply {
                    add(UIDFolder.FetchProfileItem.UID); add(FetchProfile.Item.ENVELOPE); add(FetchProfile.Item.FLAGS)
                })
                val items = msgs.mapNotNull { m ->
                    val uid = f.getUID(m)
                    val seen = m.isSet(Flags.Flag.SEEN)
                    // A brief whose saved copy went missing is downloaded again.
                    known[uid]?.takeIf { File(dir(c), "$uid.html").exists() }?.copy(seen = seen)
                        ?: runCatching { fetch(c, m, uid, seen) }.getOrNull()
                }.sortedByDescending { it.date }
                if (gen != generation) return   // cleared (label changed, signed out) while this ran
                // Emails that lost the label (or fell off the end) go from the phone too.
                val keep = items.map { "${it.uid}.html" }.toSet() + "index.json"
                dir(c).listFiles()?.forEach { if (it.name !in keep) it.delete() }
                lastSync = System.currentTimeMillis()
                list.value = items
                save(c, items, validity)
            } finally {
                f.close(false)
            }
        }
    }

    private fun fetch(c: Context, m: Message, uid: Long, seen: Boolean): Brief {
        val html = bodyOf(m) ?: "<p>(This email has no readable text.)</p>"
        File(dir(c), "$uid.html").writeText(html)
        val text = BriefHtml.plainText(html)
        val from = m.from?.firstOrNull() as? InternetAddress
        return Brief(
            uid = uid,
            subject = m.subject?.trim().orEmpty().ifEmpty { "(No subject)" },
            sender = from?.personal?.trim()?.takeIf { it.isNotEmpty() } ?: from?.address.orEmpty(),
            address = from?.address.orEmpty(),
            date = (m.sentDate ?: m.receivedDate)?.time ?: 0L,
            seen = seen,
            words = text.split(Regex("\\s+")).count { it.isNotBlank() },
            preview = text.take(160),
        )
    }

    /** The email's HTML part, or its plain text wrapped as paragraphs. */
    private fun bodyOf(p: Part): String? {
        if (p.isMimeType("text/html")) return p.content as? String
        if (p.isMimeType("multipart/alternative")) {
            val mp = p.content as Multipart
            val parts = (0 until mp.count).map { mp.getBodyPart(it) }
            parts.firstOrNull { it.isMimeType("text/html") }?.let { return it.content as? String }
            parts.firstNotNullOfOrNull { bodyOf(it) }?.let { return it }
        }
        if (p.isMimeType("multipart/*")) {
            val mp = p.content as Multipart
            for (i in 0 until mp.count) {
                val part = mp.getBodyPart(i)
                if (Part.ATTACHMENT.equals(part.disposition, ignoreCase = true)) continue
                bodyOf(part)?.let { return it }
            }
        }
        if (p.isMimeType("text/plain")) {
            val t = p.content as? String ?: return null
            return t.split(Regex("\\n\\s*\\n")).joinToString("") { "<p>" + org.jsoup.nodes.Entities.escape(it.trim()).replace("\n", "<br>") + "</p>" }
        }
        return null
    }

    private fun connect(a: Account): Store {
        val props = Properties().apply {
            put("mail.store.protocol", "imaps")
            // Fetching a body must not mark it read; only opening a brief does.
            put("mail.imaps.peek", "true")
            put("mail.imaps.connectiontimeout", "15000")
            put("mail.imaps.timeout", "30000")
        }
        return Session.getInstance(props).getStore("imaps").apply { connect("imap.gmail.com", 993, a.email, a.password) }
    }

    private class NoLabel(val label: String) : Exception()

    private fun message(e: Throwable): String = when {
        e is NoLabel -> "Gmail has no label called \"${e.label}\". Check the spelling (labels inside others are written Parent/Label)."
        e is AuthenticationFailedException -> "Gmail didn't accept the address or app password. App passwords are 16 letters, made in your Google Account › Security."
        e is java.net.UnknownHostException || e.cause is java.net.UnknownHostException -> "No internet connection."
        else -> e.message ?: e.javaClass.simpleName
    }
}
