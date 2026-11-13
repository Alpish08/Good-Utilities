package com.tools.app.dns

import androidx.compose.material.icons.rounded.Language

import androidx.compose.material.icons.Icons

import com.tools.app.ui.OneUiIcons
import android.content.ComponentName
import android.graphics.drawable.Icon
import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tools.app.R
import com.tools.app.ui.ColorIcon
import com.tools.app.ui.ListRow
import com.tools.app.ui.MasterSwitchCard
import com.tools.app.ui.OneUiScaffold
import com.tools.app.ui.PillButton
import com.tools.app.ui.RadioDot
import com.tools.app.ui.card
import com.tools.app.ui.cardList
import com.tools.app.ui.description
import com.tools.app.ui.sectionLabel
import com.tools.app.ui.theme.IconColors
import com.tools.app.ui.theme.OneUi
import com.tools.app.ui.theme.SamsungFont
import kotlinx.coroutines.delay

@Composable
fun PrivateDnsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val focus = LocalFocusManager.current
    val c = OneUi.colors
    var canWrite by remember { mutableStateOf(PrivateDns.canWrite(context)) }
    var custom by remember { mutableStateOf(PrivateDns.isCustom(context)) }
    var host by remember { mutableStateOf(PrivateDns.host(context)) }
    var draft by remember { mutableStateOf(PrivateDns.host(context)) }

    // Stay in sync with the Quick panel tile and Samsung Settings: react the moment either changes
    // the setting (a ContentObserver, instead of polling).
    androidx.compose.runtime.DisposableEffect(Unit) {
        fun sync() {
            canWrite = PrivateDns.canWrite(context)
            custom = PrivateDns.isCustom(context)
            val h = PrivateDns.host(context)
            if (h != host) { host = h; draft = h }
        }
        val observer = PrivateDns.observe(context) { sync() }
        sync()
        onDispose { context.contentResolver.unregisterContentObserver(observer) }
    }
    fun saveHost(h: String) {
        if (h.isBlank()) return
        PrivateDns.setHost(context, h); host = h.trim(); draft = host
    }

    OneUiScaffold(title = "Private DNS", onBack = onBack) {
        item("master") {
            MasterSwitchCard(
                checked = custom, enabled = canWrite && host.isNotBlank(),
                onText = "Custom", offText = "Automatic",
                onCheckedChange = { PrivateDns.setCustom(context, it); custom = it },
            )
        }
        description("Custom sends all DNS lookups to the provider below over an encrypted connection, so it works on Wi-Fi and mobile data, and can block ads system-wide. Automatic uses your network's DNS.")

        if (!canWrite) card("grant") {
            ListRow("One-time setup needed", summary = "Run the adb grant command shown on the Charge limit page. The same permission covers both.", summaryColor = c.accent)
        }

        sectionLabel("Provider hostname")
        card("host") {
            Column(Modifier.padding(start = 18.dp, end = 14.dp, top = 14.dp, bottom = 14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f).padding(vertical = 10.dp)) {
                        if (draft.isEmpty()) Text("dns.example.com", fontSize = 16.sp, color = c.textSecondary)
                        BasicTextField(
                            draft, { draft = it }, singleLine = true, enabled = canWrite,
                            textStyle = TextStyle(fontFamily = SamsungFont, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = c.text),
                            cursorBrush = SolidColor(c.accent),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { focus.clearFocus(); saveHost(draft) }),
                        )
                    }
                    if (draft.trim() != host && draft.isNotBlank()) PillButton("Save", onClick = { focus.clearFocus(); saveHost(draft) }, height = 40.dp)
                }
            }
        }
        sectionLabel("Presets")
        cardList(PrivateDns.PRESETS, key = { it.first }) { (h, label) ->
            ListRow(h, summary = label, leading = { RadioDot(host == h) }, enabled = canWrite, onClick = { saveHost(h) })
        }

        if (Build.VERSION.SDK_INT >= 33) {
            sectionLabel("Quick panel")
            card("tile") {
                ListRow(
                    "Add Private DNS to Quick panel",
                    summary = "Tap to switch between Custom and Automatic. Touch and hold to open this page.",
                    onClick = {
                        context.getSystemService(android.app.StatusBarManager::class.java).requestAddTileService(
                            ComponentName(context, PrivateDnsTileService::class.java), "DNS",
                            Icon.createWithResource(context, R.drawable.ic_qs_dns), context.mainExecutor,
                        ) { }
                    },
                )
            }
        }
    }
}

