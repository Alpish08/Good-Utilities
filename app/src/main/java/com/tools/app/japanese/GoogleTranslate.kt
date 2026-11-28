package com.tools.app.japanese

import com.tools.app.R
import com.tools.app.ui.t
import android.content.Context
import android.content.Intent
import android.widget.Toast

/**
 * Google Translate's "Tap to Translate" card, which floats over the reader. Translate has no API that hands
 * text back, but this card gives its online translation, pronunciation audio and alternatives in place.
 */
object GoogleTranslate {
    private const val PKG = "com.google.android.apps.translate"

    private fun intent(text: String) = Intent(Intent.ACTION_PROCESS_TEXT)
        .setPackage(PKG)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_PROCESS_TEXT, text)
        .putExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, true)

    fun installed(context: Context) = intent("").resolveActivity(context.packageManager) != null

    fun open(context: Context, text: String) {
        runCatching { context.startActivity(intent(text)) }
            .onFailure { Toast.makeText(context, t(R.string.jp_couldn_t_open_google_translate), Toast.LENGTH_SHORT).show() }
    }
}
