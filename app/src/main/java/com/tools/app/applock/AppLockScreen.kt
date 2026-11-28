package com.tools.app.applock

import com.tools.app.R
import com.tools.app.ui.t
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
import kotlinx.coroutines.launch
import com.tools.app.ui.collapseHeader

private class AppEntry(val pkg: String, val label: String, val icon: ImageBitmap)

@Composable
fun AppLockScreen(onBack: () -> Unit, onSetPin: () -> Unit = {}, onGuide: () -> Unit = {}) {
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

    // Search opens from the toolbar as Home's floating bottom pill; while it's open only the apps are listed.
    var searching by rememberSaveable { mutableStateOf(false) }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val closeSearch: () -> Unit = {
        query = ""; searching = false; focusManager.clearFocus()
        scope.launch { delay(60); listState.collapseHeader() }
    }
    androidx.activity.compose.BackHandler(enabled = searching) { closeSearch() }

    OneUiScaffold(
        title = t(R.string.home_app_lock), onBack = onBack, listState = listState,
        actions = {
            com.tools.app.ui.OverflowMenu(listOf(t(R.string.lock_setup_guide) to onGuide, com.tools.app.ui.tipsItem(listOf(com.tools.app.ui.Tip(t(R.string.lock_private_by_design), t(R.string.lock_locked_apps_ask_for_your_fingerprint_f))))))
        },
        bottomBar = { com.tools.app.ui.PageSearch(searching, query, { query = it }, onOpen = { searching = true }, onClose = closeSearch) },
    ) {
        if (!searching) {
        item("master") {
            MasterSwitchCard(active, onCheckedChange = { on ->
                if (on && !serviceOn) openAccessibility()
                AppLock.setEnabled(on)
            })
        }

        if (config.enabled && !serviceOn) card("setup") {
            val blocked = AppLock.isToggleOnInSettings(context)
            ListRow(
                if (blocked) t(R.string.lock_android_blocked_app_lock) else t(R.string.lock_allow_app_lock_in_accessibility),
                summary = if (blocked)
                    t(R.string.lock_apps_installed_outside_the_play_store)
                else t(R.string.lock_settings_accessibility_installed_apps),
                summaryColor = c.accent,
                onClick = {
                    if (blocked) context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:" + context.packageName))
                    ) else openAccessibility()
                },
            )
        }

        sectionLabel(t(R.string.lock_unlock_screen))
        card("unlock-method") {
            ListRow(
                if (pinSet) t(R.string.lock_change_tools_pin) else t(R.string.lock_set_tools_pin),
                summary = if (pinSet) t(R.string.lock_on_use_tools_pin_on_the_fingerprint_sh)
                else t(R.string.lock_off_locked_apps_use_samsung_s_prompt_w),
                summaryColor = if (pinSet) c.accent else null,
                onClick = onSetPin,
            )
            if (pinSet) com.tools.app.ui.CardDivider()
            if (pinSet) ListRow(t(R.string.lock_remove_tools_pin), summary = t(R.string.lock_go_back_to_samsung_s_prompt), onClick = {
                PinStore.clear(context); pinSet = false
            })
        }

        sectionLabel(t(R.string.lock_lock_again))
        cardList(Relock.entries, key = { it.name }) { r ->
            ListRow(r.label, leading = { RadioDot(config.relock == r) }, onClick = { AppLock.setRelock(r) })
        }

        }
        sectionLabel(t(R.string.lock_apps), key = "apps-label", trailing = {
            Text(t(R.string.lock_locked, config.locked.size), fontSize = 14.sp, color = c.textSecondary)
        })
        val list = apps
        if (list == null) item("loading") {
            Box(Modifier.padding(32.dp).size(32.dp)) { com.tools.app.ui.OneUiSpinner(color = c.accent) }
        } else {
            val shown = list
                .filter { query.isBlank() || it.label.contains(query, ignoreCase = true) }
                .sortedByDescending { it.pkg in lockedAtOpen }
            cardList(shown, key = { it.pkg }, dividerStart = 70.dp) { app ->
                val locked = app.pkg in config.locked
                ListRow(
                    app.label,
                    summary = if (locked) t(R.string.lock_locked_2) else null,
                    leading = { Image(app.icon, null, Modifier.size(36.dp)) },
                    trailing = { OneUiSwitch(locked) },
                    onClick = { AppLock.setLocked(app.pkg, !locked) },
                )
            }
        }
    }
}


/** Asks for your fingerprint or phone PIN (Samsung's sheet) before opening App lock's settings. */
fun confirmIdentity(activity: androidx.fragment.app.FragmentActivity, title: String, onOk: () -> Unit) {
    val kinds = androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK or androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
    // No screen lock set up at all: there is nothing to confirm with.
    if (androidx.biometric.BiometricManager.from(activity).canAuthenticate(kinds) != androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS) { onOk(); return }
    androidx.biometric.BiometricPrompt(activity, activity.mainExecutor, object : androidx.biometric.BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: androidx.biometric.BiometricPrompt.AuthenticationResult) = onOk()
    }).authenticate(
        androidx.biometric.BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(t(R.string.lock_confirm_it_s_you))
            .setAllowedAuthenticators(kinds)
            .build()
    )
}
