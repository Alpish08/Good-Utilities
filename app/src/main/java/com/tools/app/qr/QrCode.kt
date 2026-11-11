package com.tools.app.qr

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.core.content.FileProvider
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.io.File

/** QR encoding shared by the share-sheet sheet and the in-app generator. */
object QrCode {
    /** Throws when the text is too long for a QR code. */
    fun encode(text: String, size: Int): Bitmap {
        val m = QRCodeWriter().encode(
            text, BarcodeFormat.QR_CODE, 0, 0,
            mapOf(EncodeHintType.MARGIN to 0, EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M, EncodeHintType.CHARACTER_SET to "UTF-8"),
        )
        val px = IntArray(m.width * m.height) { i -> if (m.get(i % m.width, i / m.width)) 0xFF000000.toInt() else -1 }
        val small = Bitmap.createBitmap(px, m.width, m.height, Bitmap.Config.ARGB_8888)
        return Bitmap.createScaledBitmap(small, size, size, false)
    }

    /** White quiet zone around the code for saving/sharing. */
    fun padded(qr: Bitmap): Bitmap {
        val pad = qr.width / 10
        return Bitmap.createBitmap(qr.width + pad * 2, qr.height + pad * 2, Bitmap.Config.ARGB_8888).also {
            val cv = android.graphics.Canvas(it); cv.drawColor(android.graphics.Color.WHITE); cv.drawBitmap(qr, pad.toFloat(), pad.toFloat(), null)
        }
    }

    fun share(context: Context, bmp: Bitmap) {
        val file = File(context.cacheDir, "qr/qr.png").apply { parentFile?.mkdirs() }
        file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        context.startActivity(Intent.createChooser(
            Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), null,
        ))
    }
}
