package com.tools.app.home

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.tools.app.R
import com.tools.app.ui.ListRow
import com.tools.app.ui.fadingEdges
import com.tools.app.ui.OneUiIcons
import com.tools.app.ui.ToolPage
import com.tools.app.ui.cardList
import com.tools.app.ui.sectionLabel
import com.tools.app.ui.theme.OneUi

private data class Credit(val name: String, val role: String, val url: String, val image: Int? = null, val imageBg: Color = Color.Transparent)

/** Samsung-style "About" page: app identity in the top 40%, details in cards below. */
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val c = OneUi.colors
    val icon = remember { context.packageManager.getApplicationIcon(context.packageName).toBitmap(256, 256).asImageBitmap() }
    val version = remember { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
    val open = { url: String -> context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }

    val developers = listOf(
        Credit("Alpish08", "Developer · GitHub", "https://github.com/Alpish08", R.drawable.avatar_alpish08),
        Credit("Claude", "Developer · AI by Anthropic", "https://claude.ai", R.drawable.ic_claude, Color(0xFFF5F4EE)),
    )
    val libraries = listOf(
        Credit("yt-dlp", "Music downloader", "https://github.com/yt-dlp/yt-dlp"),
        Credit("FFmpeg", "Audio conversion, merging and trimming", "https://ffmpeg.org"),
        Credit("Google ML Kit", "Background remover", "https://developers.google.com/ml-kit/vision/subject-segmentation"),
        Credit("ARSCLib", "APK merging", "https://github.com/REAndroid/ARSCLib"),
        Credit("ZXing", "QR codes", "https://github.com/zxing/zxing"),
        Credit("JMdict (EDRDG), CC BY-SA 4.0", "Japanese dictionary", "https://www.edrdg.org/jmdict/j_jmdict.html"),
        Credit("jmdict-simplified", "JMdict as JSON", "https://github.com/scriptin/jmdict-simplified"),
        Credit("Kuromoji", "Japanese word splitting (IPADIC)", "https://github.com/atilika/kuromoji"),
        Credit("ML Kit", "Japanese text recognition and translation", "https://developers.google.com/ml-kit"),
    )

    ToolPage("", onBack) {
        BoxWithConstraints(Modifier.weight(1f)) {
            val header = maxHeight * 0.4f
            val list = androidx.compose.foundation.lazy.rememberLazyListState()
            LazyColumn(state = list, modifier = Modifier.fadingEdges(list)) {
                item(key = "header") {
                    Column(
                        Modifier.fillMaxWidth().height(header).padding(horizontal = 24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Image(icon, null, Modifier.size(88.dp))
                        Text(context.getString(R.string.app_name), fontSize = 30.sp, fontWeight = FontWeight.Bold, color = c.text, modifier = Modifier.padding(top = 16.dp))
                        Text("Version $version", fontSize = 15.sp, color = c.textSecondary, modifier = Modifier.padding(top = 6.dp))
                        Text(
                            "A personal toolbox for One UI.", fontSize = 14.sp, color = c.textSecondary,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
                sectionLabel("Developers")
                cardList(developers, key = { "dev-" + it.name }, dividerStart = 72.dp) { d ->
                    ListRow(
                        d.name, summary = d.role,
                        leading = { Avatar(d.image!!, d.imageBg) },
                        trailing = { Icon(OneUiIcons.OpenExternal, null, tint = c.textSecondary, modifier = Modifier.size(20.dp)) },
                        onClick = { open(d.url) },
                    )
                }
                sectionLabel("Open source")
                cardList(libraries, key = { "lib-" + it.name }) { l ->
                    ListRow(l.name, summary = l.role, onClick = { open(l.url) })
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

/** Round profile picture; logos sit inset on a soft background so they read like an avatar. */
@Composable
private fun Avatar(image: Int, background: Color) {
    val logo = background != Color.Transparent
    Box(
        Modifier.size(40.dp).clip(CircleShape).background(background),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            androidx.compose.ui.res.painterResource(image), null,
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            modifier = if (logo) Modifier.size(24.dp) else Modifier.size(40.dp),
        )
    }
}
