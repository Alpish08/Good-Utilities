package com.tools.app.idcards

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** One stored ID: its name and whether it has a back. The images live encrypted in idcards/<id>_front|back. */
data class IdCard(val id: String, val title: String, val hasBack: Boolean, val added: Long)

/**
 * ID cards: photos of your IDs (driver's license, Social Security card…), front and back. Everything (the list of
 * names and every image) is encrypted with an AES key kept in the Android Keystore (hardware-backed), in the app's
 * private storage only: never in Gallery, Downloads or backups. Decrypted images exist only in memory, on screen.
 */
object IdStore {
    /** Screenshots, screen recording and the recents preview blocked while Virtual cards is open. */
    const val SECURE = true

    private const val KEY = "idcards_key"
    private const val MAX_SIDE = 2400

    val cards = MutableStateFlow<List<IdCard>>(emptyList())

    /** An image shared to "Add to ID cards", held in memory until ID cards is unlocked and it's saved or dropped. */
    val pendingShare = MutableStateFlow<ByteArray?>(null)

    /** True while the photo picker is open, so leaving for it doesn't lock ID cards. */
    @Volatile var picking = false

    private var loaded = false

    private fun dir(c: Context) = File(c.filesDir, "idcards").apply { mkdirs() }
    private fun index(c: Context) = File(dir(c), "index.enc")
    private fun file(c: Context, id: String, back: Boolean) = File(dir(c), "${id}_${if (back) "back" else "front"}.enc")

    @Synchronized
    fun load(c: Context) {
        if (loaded) return
        loaded = true
        cards.value = runCatching {
            val a = JSONArray(String(decrypt(index(c).readBytes())))
            (0 until a.length()).map { i -> a.getJSONObject(i).run { IdCard(getString("id"), getString("t"), getBoolean("b"), getLong("a")) } }
        }.getOrDefault(emptyList())
    }

    private fun save(c: Context, list: List<IdCard>) {
        val a = JSONArray()
        list.forEach { a.put(JSONObject().put("id", it.id).put("t", it.title).put("b", it.hasBack).put("a", it.added)) }
        index(c).writeBytes(encrypt(a.toString().toByteArray()))
        cards.value = list
    }

    fun add(c: Context, title: String, front: ByteArray, back: ByteArray?) {
        val id = UUID.randomUUID().toString()
        file(c, id, false).writeBytes(encrypt(normalise(front)))
        if (back != null) file(c, id, true).writeBytes(encrypt(normalise(back)))
        save(c, cards.value + IdCard(id, title.ifBlank { "ID card" }, back != null, System.currentTimeMillis()))
    }

    fun rename(c: Context, id: String, title: String) =
        save(c, cards.value.map { if (it.id == id) it.copy(title = title.ifBlank { it.title }) else it })

    fun replace(c: Context, id: String, back: Boolean, image: ByteArray) {
        file(c, id, back).writeBytes(encrypt(normalise(image)))
        if (back) save(c, cards.value.map { if (it.id == id) it.copy(hasBack = true) else it })
    }

    fun removeBack(c: Context, id: String) {
        file(c, id, true).delete()
        save(c, cards.value.map { if (it.id == id) it.copy(hasBack = false) else it })
    }

    fun delete(c: Context, id: String) {
        file(c, id, false).delete(); file(c, id, true).delete()
        save(c, cards.value.filter { it.id != id })
    }

    /** Moves a card to a new place in the stack. */
    fun move(c: Context, from: Int, to: Int) {
        val l = cards.value.toMutableList()
        if (from !in l.indices || to !in l.indices) return
        l.add(to, l.removeAt(from)); save(c, l)
    }

    /** The decrypted image, decoded for display (null if missing). */
    fun image(c: Context, id: String, back: Boolean): Bitmap? = runCatching {
        val bytes = decrypt(file(c, id, back).readBytes())
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }.getOrNull()

    /** Re-encodes a picked photo: upright (EXIF), at most [MAX_SIDE] px, JPEG, with the photo's metadata dropped. */
    private fun normalise(raw: ByteArray): ByteArray {
        var bmp = BitmapFactory.decodeByteArray(raw, 0, raw.size) ?: return raw
        val rotation = runCatching {
            androidx.exifinterface.media.ExifInterface(raw.inputStream()).rotationDegrees
        }.getOrDefault(0)
        if (rotation != 0) bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, android.graphics.Matrix().apply { postRotate(rotation.toFloat()) }, true)
        val long = maxOf(bmp.width, bmp.height)
        if (long > MAX_SIDE) {
            val s = MAX_SIDE.toFloat() / long
            bmp = Bitmap.createScaledBitmap(bmp, (bmp.width * s).toInt(), (bmp.height * s).toInt(), true)
        }
        return ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.JPEG, 92, it) }.toByteArray()
    }

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(KEY, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(
                KeyGenParameterSpec.Builder(KEY, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build(),
            )
        }.generateKey()
    }

    private fun encrypt(plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        return cipher.iv + cipher.doFinal(plain)
    }

    private fun decrypt(sealed: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, sealed, 0, 12)) }
        return cipher.doFinal(sealed, 12, sealed.size - 12)
    }
}
