package com.tools.app.ui

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import kotlin.math.max
import kotlin.math.min

object Images {
    /** Decodes with EXIF rotation applied, as a software bitmap no larger than [maxSide]. */
    fun load(context: Context, uri: Uri, maxSide: Int): Bitmap {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val w = info.size.width; val h = info.size.height
            val scale = min(1f, maxSide.toFloat() / max(w, h))
            if (scale < 1f) decoder.setTargetSize((w * scale).toInt().coerceAtLeast(1), (h * scale).toInt().coerceAtLeast(1))
        }.let { if (it.config != Bitmap.Config.ARGB_8888) it.copy(Bitmap.Config.ARGB_8888, false) else it }
    }

    fun saveJpeg(context: Context, bitmap: Bitmap, name: String) = save(context, bitmap, name, png = false)

    /** Lossless, keeps transparency. */
    fun savePng(context: Context, bitmap: Bitmap, name: String) = save(context, bitmap, name, png = true)

    private fun save(context: Context, bitmap: Bitmap, name: String, png: Boolean) {
        com.tools.app.Storage.save(context, com.tools.app.Storage.Folder.Pictures, null, if (png) "$name.png" else "$name.jpg") {
            bitmap.compress(if (png) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG, 95, it)
        }
    }

    /** Where pictures are saved, for messages. */
    val FOLDER = com.tools.app.Storage.label(com.tools.app.Storage.Folder.Pictures)
}
