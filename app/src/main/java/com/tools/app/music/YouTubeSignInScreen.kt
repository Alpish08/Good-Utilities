package com.tools.app.music

import com.tools.app.R
import com.tools.app.ui.t
import android.annotation.SuppressLint
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.tools.app.music.engine.YouTubeAccount
import com.tools.app.ui.CardMargin
import com.tools.app.ui.ToolPage

/** Google's own sign-in page; we never see the password, only the resulting YouTube cookies. */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun YouTubeSignInScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    ToolPage(t(R.string.music_sign_in_to_youtube), onBack = onDone) {
        AndroidView(
            modifier = Modifier.fillMaxSize().padding(horizontal = CardMargin),
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    // Google refuses sign-in from user agents marked as embedded WebViews.
                    settings.userAgentString = settings.userAgentString.replace("; wv", "")
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView, url: String) {
                            if (url.contains("youtube.com") && YouTubeAccount.hasSession()) {
                                val ok = YouTubeAccount.export(context)
                                Toast.makeText(context, if (ok) t(R.string.music_signed_in_to_youtube) else t(R.string.music_sign_in_incomplete_try_again), Toast.LENGTH_SHORT).show()
                                if (ok) onDone()
                            }
                        }
                    }
                    loadUrl("https://accounts.google.com/ServiceLogin?service=youtube&continue=https%3A%2F%2Fmusic.youtube.com%2F")
                }
            },
        )
    }
}
