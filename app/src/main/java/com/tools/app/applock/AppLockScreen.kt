package com.tools.app.applock

import com.tools.app.ui.OneUiIcons
import androidx.compose.foundation.layout.width

import androidx.compose.foundation.layout.Spacer

import androidx.compose.ui.draw.clip

import androidx.compose.foundation.clickable


import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.tools.app.ui.ListRow
import com.tools.app.ui.MasterSwitchCard
import com.tools.app.ui.OneUiScaffold
import com.tools.app.ui.OneUiSwitch
import com.tools.app.ui.RadioDot
import com.tools.app.ui.RoundCard
import com.tools.app.ui.card
import com.tools.app.ui.cardList
import com.tools.app.ui.description
import com.tools.app.ui.sectionLabel
import com.tools.app.ui.theme.OneUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private class AppEntry(val pkg: String, val label: String, val icon: ImageBitmap)

@Composable
fun AppLockScreen(onBack: () -> Unit, onSetPin: () -> Unit = {}) {
    val context = LocalContext.current
    val c = OneUi.colors
    val config by AppLock.config.collectAsState()
    // Locked apps are grouped at the top only when the page opens; toggling never reorders the list.
    val lockedAtOpen = remember { AppLock.config.value.locked }
    var serviceOn by remember { mutableStateOf(AppLock.isServiceEnabled(context)) }
    var apps by remember { mutableStateOf<List<AppEntry>?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var pinSet by remember { mutableStateOf(PinStore.isSet(context)) }
    LaunchedEffect(Unit) { while (true) { pinSet = PinStore.isSet(context); delay(800) } }

    // The accessibility toggle lives in system Settings; re-check while this page is open.
    LaunchedEffect(Unit) {
        while (true) { serviceOn = AppLock.isServiceEnabled(context); delay(1000) }
    }
    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.IO) {
            val pm = context.packageManager
            pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
                .distinctBy { it.activityInfo.packageName }
                .filter { it.activityInfo.packageName != context.packageName }
                .map { AppEntry(it.activityInfo.packageName, it.loadLabel(pm).toString(), it.loadIcon(pm).toBitmap(112, 112).asImageBitmap()) }
                .sortedBy { it.label.lowercase() }
        }
    }
    val openAccessibility = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
    val active = config.enabled && serviceOn

    OneUiScaffold(title = "App lock", onBack = onBack) {
        item("master") {
            MasterSwitchCard(active, onCheckedChange = { on ->
                if (on && !serviceOn) openAccessibility()
                AppLock.setEnabled(on)
            })
        }
        description("Locked apps ask for your fingerprint, face or PIN when you open them. Good Utilities only sees which app is on screen, never what's in it, and nothing leaves your phone.")

        if (config.enabled && !serviceOn) card("setup") {
            val blocked = AppLock.isToggleOnInSettings(context)
            ListRow(
                if (blocked) "Android blocked App lock" else "Allow App lock in Accessibility",
                summary = if (blocked)
                    "Apps installed outside the Play Store need one extra step: open App info › ⋮ › Allow restricted settings, then turn Good Utilities app lock off and on again."
                else "Settings › Accessibility › Installed apps › Good Utilities app lock › On",
                summaryColor = c.accent,
                onClick = {
                    if (blocked) context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:" + context.packageName))
                    ) else openAccessibility()
                },
            )
        }

        sectionLabel("Unlock screen")
        card("unlock-method") {
            val activity = context as? androidx.fragment.app.FragmentActivity
            fun confirm(title: String, then: () -> Unit) {
                if (activity == null) { then(); return }
                confirmIdentity(activity, title, then)
            }
            ListRow(
                if (pinSet) "Change Tools PIN" else "Set Tools PIN",
                summary = if (pinSet) "On · \"Use Tools PIN\" on the fingerprint sheet opens Tools' PIN pad, without the dim"
                else "Off · locked apps use Samsung's prompt with your phone PIN",
                summaryColor = if (pinSet) c.accent else null,
                onClick = { confirm(if (pinSet) "Change Tools PIN" else "Set Tools PIN", onSetPin) },
            )
            if (pinSet) com.tools.app.ui.CardDivider()
            if (pinSet) ListRow("Remove Tools PIN", summary = "Go back to Samsung's prompt", onClick = {
                confirm("Remove Tools PIN") { PinStore.clear(context); pinSet = false }
            })
        }

        sectionLabel("Lock again")
        cardList(Relock.entries, key = { it.name }) { r ->
            ListRow(r.label, leading = { RadioDot(config.relock == r) }, onClick = { AppLock.setRelock(r) })
        }

        sectionLabel("Apps", key = "apps-label", trailing = {
            Text("${config.locked.size} locked", fontSize = 14.sp, color = c.textSecondary)
        })
        item("search") {
            RoundCard {
                Row(Modifier.padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(OneUiIcons.Search, null, tint = c.textSecondary, modifier = Modifier.size(22.dp))
                    Box(Modifier.padding(start = 12.dp).weight(1f)) {
                        if (query.isEmpty()) Text("Search apps", fontSize = 16.sp, color = c.textSecondary)
                        BasicTextField(query, { query = it }, singleLine = true, textStyle = TextStyle(fontFamily = com.tools.app.ui.theme.SamsungFont, fontSize = 16.sp, color = c.text), cursorBrush = SolidColor(c.accent))
                    }
                }
            }
        }
        val list = apps
        if (list == null) item("loading") {
            Box(Modifier.padding(32.dp).size(32.dp)) { CircularProgressIndicator(color = c.accent) }
        } else {
            val shown = list
                .filter { query.isBlank() || it.label.contains(query, ignoreCase = true) }
                .sortedByDescending { it.pkg in lockedAtOpen }
            cardList(shown, key = { it.pkg }, dividerStart = 70.dp) { app ->
                val locked = app.pkg in config.locked
                ListRow(
                    app.label,
                    summary = if (locked) "Locked" else null,
                    leading = { Image(app.icon, null, Modifier.size(36.dp)) },
                    trailing = { OneUiSwitch(locked) },
                    onClick = { AppLock.setLocked(app.pkg, !locked) },
                )
            }
        }
    }
}


/** Asks for your existing fingerprint or phone PIN (Samsung prompt) before sensitive changes. */
fun confirmIdentity(activity: androidx.fragment.app.FragmentActivity, title: String, onOk: () -> Unit) {
    androidx.biometric.BiometricPrompt(activity, activity.mainExecutor, object : androidx.biometric.BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: androidx.biometric.BiometricPrompt.AuthenticationResult) = onOk()
    }).authenticate(
        androidx.biometric.BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle("Confirm it's you")
            .setAllowedAuthenticators(androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK or androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL)
            .build()
    )
}
