package com.tools.app.japanese

import android.content.Context
import android.content.Intent
import android.graphics.Color as AColor
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.tools.app.ui.theme.OneUiTheme
import java.io.File

/**
 * Full-screen Japanese reader. Opened with a screenshot path (the tile), a picked / photographed image,
 * or a shared image (Share → Read Japanese). Without an image it shows the Japanese reader page itself
 * (used by the tile when App lock's accessibility service isn't running).
 */
class JapaneseReaderActivity : ComponentActivity() {
    private var source by mutableStateOf<ReaderSource?>(null)
    private var noService by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(AColor.TRANSPARENT, AColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(AColor.TRANSPARENT, AColor.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        JpPrefs.init(this)
        WordStore.init(this)
        take(intent)
        setContent {
            OneUiTheme {
                val s = source
                if (s != null) ReaderRoot(s, onClose = { finish() })
                else JapaneseReaderScreen(
                    onBack = { finish() },
                    notice = if (noService) "App lock's accessibility service isn't running, so the tile can't capture the screen. " +
                        "Turn it on in Settings > Accessibility (run ~/enable-applock.sh after reinstalling), then try the tile again. " +
                        "You can still choose an image or take a photo below." else null,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        take(intent)
    }

    private fun take(i: Intent?) {
        noService = i?.getBooleanExtra(EXTRA_NO_SERVICE, false) == true
        val path = i?.getStringExtra(EXTRA_PATH)
        source = when {
            path != null -> {
                val f = File(path)
                ReaderSource(Uri.fromFile(f), "$path@${i.getLongExtra(EXTRA_STAMP, f.lastModified())}")
            }
            i?.action == Intent.ACTION_SEND -> @Suppress("DEPRECATION") i.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)?.let { ReaderSource(it, it.toString() + System.nanoTime()) }
            i?.data != null -> ReaderSource(i.data!!, i.data.toString() + System.nanoTime())
            else -> null
        }
    }

    companion object {
        const val EXTRA_PATH = "path"
        const val EXTRA_STAMP = "stamp"
        const val EXTRA_NO_SERVICE = "no_service"

        fun open(context: Context, uri: Uri) {
            context.startActivity(
                Intent(context, JapaneseReaderActivity::class.java).setData(uri)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            )
        }

        fun openFile(context: Context, file: File) {
            context.startActivity(
                Intent(context, JapaneseReaderActivity::class.java)
                    .putExtra(EXTRA_PATH, file.path).putExtra(EXTRA_STAMP, System.nanoTime())
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            )
        }
    }
}

class ReaderSource(val uri: Uri, val key: String)
