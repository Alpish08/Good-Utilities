package com.tools.app.qr

import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tools.app.ui.PillButton
import com.tools.app.ui.theme.OneUi
import com.tools.app.ui.theme.OneUiTheme

/** Share target: any text or link → a minimal One UI bottom sheet with its QR code. */
class QrActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val text = (intent.getStringExtra(Intent.EXTRA_TEXT) ?: intent.getStringExtra(Intent.EXTRA_SUBJECT)).orEmpty().trim()
        if (text.isEmpty()) { finish(); return }
        setContent {
            OneUiTheme {
                // Encoded off the main thread, once per text; recompositions reuse it.
                var tooLong by remember { mutableStateOf(false) }
                val qr by androidx.compose.runtime.produceState<Bitmap?>(null, text) {
                    value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                        runCatching { QrCode.encode(text, 1024) }.getOrNull()
                    }
                    tooLong = value == null
                }
                val qrImage = remember(qr) { qr?.asImageBitmap() }
                val c = OneUi.colors
                var shown by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) { shown = true }
                Box(
                    Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f))
                        .clickable(remember { MutableInteractionSource() }, null) { finish() },
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    AnimatedVisibility(shown, enter = slideInVertically(tween(280)) { it / 3 } + fadeIn(tween(200))) {
                        Column(
                            Modifier
                                .padding(horizontal = 10.dp, vertical = 10.dp)
                                .navigationBarsPadding()
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(30.dp))
                                .background(c.card)
                                .clickable(remember { MutableInteractionSource() }, null) { }
                                .padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Box(Modifier.width(36.dp).height(4.dp).clip(CircleShape).background(c.divider))
                            Text("QR code", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = c.text, modifier = Modifier.padding(top = 14.dp))
                            if (!tooLong) Box(
                                Modifier.padding(top = 16.dp).fillMaxWidth(0.78f).aspectRatio(1f)
                                    .clip(RoundedCornerShape(22.dp)).background(Color.White).padding(14.dp),
                            ) { qrImage?.let { Image(it, "QR code", Modifier.fillMaxSize(), filterQuality = FilterQuality.None) } }
                            else Text("This text is too long for a QR code.", color = c.error, modifier = Modifier.padding(24.dp))
                            Text(
                                text, fontSize = 14.sp, color = c.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center, modifier = Modifier.padding(top = 14.dp),
                            )
                            qr?.let { qr -> Row(Modifier.fillMaxWidth().padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                PillButton("Save", tonal = true, modifier = Modifier.weight(1f), onClick = {
                                    val r = runCatching { com.tools.app.ui.Images.saveJpeg(this@QrActivity, QrCode.padded(qr), "qr_${System.currentTimeMillis()}") }
                                    Toast.makeText(this@QrActivity, if (r.isSuccess) "Saved to ${com.tools.app.ui.Images.FOLDER}" else r.exceptionOrNull()?.message ?: "Couldn't save", Toast.LENGTH_SHORT).show()
                                })
                                PillButton("Share", modifier = Modifier.weight(1f), onClick = { QrCode.share(this@QrActivity, QrCode.padded(qr)) })
                            } }
                        }
                    }
                }
            }
        }
    }
}
