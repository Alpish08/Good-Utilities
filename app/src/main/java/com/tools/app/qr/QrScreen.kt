package com.tools.app.qr

import android.content.ClipboardManager
import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tools.app.ui.OneUiIcons
import com.tools.app.ui.OneUiScaffold
import com.tools.app.ui.PillButton
import com.tools.app.ui.RoundCard
import com.tools.app.ui.ToolbarIcon
import com.tools.app.ui.description
import com.tools.app.ui.theme.OneUi
import com.tools.app.ui.theme.SamsungFont
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** In-app version of the "QR code" share target: type or paste a link or text, then save or share the code. */
@Composable
fun QrScreen(onBack: () -> Unit) {
    val c = OneUi.colors
    val context = LocalContext.current
    val focus = LocalFocusManager.current
    var text by rememberSaveable { mutableStateOf("") }
    val input = text.trim()

    // Re-encoded shortly after typing stops; null while empty, Result.failure when too long.
    val qr by produceState<Result<Bitmap>?>(null, input) {
        if (input.isEmpty()) { value = null; return@produceState }
        delay(150)
        value = withContext(Dispatchers.Default) { runCatching { QrCode.encode(input, 1024) } }
    }
    val bitmap = qr?.getOrNull()
    val image = remember(bitmap) { bitmap?.asImageBitmap() }

    OneUiScaffold(
        title = "QR code",
        onBack = onBack,
        bottomBar = if (bitmap != null) ({
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PillButton("Save", tonal = true, modifier = Modifier.weight(1f), onClick = {
                    val r = runCatching { com.tools.app.ui.Images.saveJpeg(context, QrCode.padded(bitmap), "qr_${System.currentTimeMillis()}") }
                    Toast.makeText(context, if (r.isSuccess) "Saved to ${com.tools.app.ui.Images.FOLDER}" else r.exceptionOrNull()?.message ?: "Couldn't save", Toast.LENGTH_SHORT).show()
                })
                PillButton("Share", icon = OneUiIcons.Share, modifier = Modifier.weight(1f), onClick = { QrCode.share(context, QrCode.padded(bitmap)) })
            }
        }) else null,
    ) {
        item(key = "input") {
            RoundCard {
                Column(Modifier.padding(start = 20.dp, end = 14.dp, top = 18.dp, bottom = 16.dp)) {
                    Text("Link or text", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = c.accent)
                    Row(verticalAlignment = Alignment.Top) {
                        BasicTextField(
                            value = text,
                            onValueChange = { text = it },
                            textStyle = TextStyle(fontFamily = SamsungFont, fontSize = 16.sp, color = c.text),
                            cursorBrush = SolidColor(c.accent),
                            maxLines = 6,
                            modifier = Modifier.weight(1f).heightIn(min = 24.dp).padding(vertical = 12.dp),
                            decorationBox = { inner ->
                                Box {
                                    if (text.isEmpty()) Text("Type or paste anything", fontSize = 16.sp, color = c.textSecondary.copy(alpha = 0.7f))
                                    inner()
                                }
                            },
                        )
                        if (text.isNotEmpty()) ToolbarIcon(OneUiIcons.Close, "Clear") { text = "" }
                    }
                    PillButton(
                        "Paste", tonal = true, icon = OneUiIcons.ContentPaste, height = 46.dp,
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp, end = 6.dp),
                        onClick = {
                            val clip = context.getSystemService(ClipboardManager::class.java).primaryClip
                            val t = clip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()
                            if (!t.isNullOrBlank()) { focus.clearFocus(); text = t.trim() }
                        },
                    )
                }
            }
        }
        item(key = "qr") {
            AnimatedVisibility(
                visible = image != null,
                enter = fadeIn(tween(220)) + scaleIn(tween(320, easing = com.tools.app.ui.theme.OneUiEasing), initialScale = 0.92f),
                exit = fadeOut(tween(150)),
            ) {
                RoundCard {
                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        Box(
                            Modifier.fillMaxWidth(0.82f).aspectRatio(1f).clip(RoundedCornerShape(22.dp))
                                .background(Color.White).padding(16.dp),
                        ) { image?.let { Image(it, "QR code", Modifier.fillMaxSize(), filterQuality = FilterQuality.None) } }
                    }
                }
            }
        }
        if (qr?.isFailure == true) description("This is too long for a QR code. Try a shorter link or less text.", key = "too-long")
        else if (input.isEmpty()) description("Tip: you can also share a link or text from any app and choose \"QR code\".", key = "tip")
    }
}
