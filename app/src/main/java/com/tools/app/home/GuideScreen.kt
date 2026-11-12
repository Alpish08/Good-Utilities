package com.tools.app.home

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tools.app.ui.OneUiIcons
import com.tools.app.ui.OneUiScaffold
import com.tools.app.ui.RoundCard
import com.tools.app.ui.description
import com.tools.app.ui.sectionLabel
import com.tools.app.ui.theme.OneUi

/** A step of a setup guide; [command] is shown in a copyable box. */
private class Step(val title: String, val text: String, val command: String? = null)

enum class GuideKind { AppLock, Battery }

/**
 * Setup guide for the features Android only lets a computer switch on (over adb): App lock's accessibility
 * service and Samsung's battery figures. Opened from ⋮ › Setup guide on those pages.
 */
@Composable
fun GuideScreen(kind: GuideKind, onBack: () -> Unit) {
    val before = listOf(
        Step(
            "Turn on USB debugging",
            "Settings › About phone › Software information › tap Build number 7 times. Then Settings › Developer options › USB debugging. Connect the phone to your computer and allow it.",
        ),
        Step(
            "Install adb on your Mac (once)",
            "Homebrew installs Google's platform tools. Check that the phone shows up as \"device\".",
            "brew install android-platform-tools\nadb devices",
        ),
    )
    val (title, intro, steps, note) = when (kind) {
        GuideKind.AppLock -> Quad(
            "App lock setup",
            "App lock needs its accessibility service, and Android 16 blocks that for apps installed outside the Play Store. It also switches the service off every time the app is reinstalled or updated over adb, so run this after each update.",
            listOf(
                Step(
                    "Run the App lock script",
                    "If you have it on your Mac, this does everything below in one go.",
                    "~/enable-applock.sh",
                ),
                Step(
                    "Or run the commands yourself",
                    "Allows restricted settings for Good Utilities, then turns its accessibility service on.",
                    "adb shell appops set com.tools.app ACCESS_RESTRICTED_SETTINGS allow\n" +
                        "adb shell settings put secure enabled_accessibility_services com.tools.app/com.tools.app.applock.AppLockService\n" +
                        "adb shell settings put secure accessibility_enabled 1",
                ),
            ),
            "The second command replaces your list of enabled accessibility services; if you use others, turn them back on in Settings › Accessibility. Without a computer: App info › ⋮ › Allow restricted settings, then Settings › Accessibility › Installed apps › Good Utilities app lock.",
        )
        GuideKind.Battery -> Quad(
            "Battery setup",
            "Samsung keeps its battery health (ASOC) and lifetime cycle count in a system service apps can't read, so they come over adb from your computer. Health changes slowly; syncing every few weeks is plenty.",
            listOf(
                Step(
                    "Run the sync script",
                    "Reads Samsung's battery report and sends health, cycles, first-use and manufacture dates to Good Utilities. It's in the Good Utilities repository.",
                    "~/sync-battery.sh",
                ),
                Step(
                    "Or see the figures yourself",
                    "mSavedBatteryAsoc is health in %. mSavedBatteryUsage ÷ 100 is the cycle count.",
                    "adb shell dumpsys battery | grep -E \"Asoc|Usage|FirstUseDate|LLB MAN\"",
                ),
                Step(
                    "…and send them by hand",
                    "Replace 97 and 75808 with your own Asoc and Usage values.",
                    "adb shell am broadcast -f 32 -a com.tools.app.SAMSUNG_BATTERY -n com.tools.app/.battery.SamsungBatterySync --ei asoc 97 --ei usage 75808",
                ),
            ),
            "Only adb (or the system) can send these figures; other apps can't. Until the first sync, the Battery page shows its own estimate, which reads a couple of points high on Galaxy phones.",
        )
    }

    OneUiScaffold(title = title, onBack = onBack) {
        description(intro, key = "intro")
        sectionLabel("Before you start", key = "before")
        before.forEachIndexed { i, s -> item(key = "b$i") { StepCard(s) } }
        sectionLabel("Each time", key = "each")
        steps.forEachIndexed { i, s -> item(key = "s$i") { StepCard(s) } }
        description(note, key = "note")
    }
}

private data class Quad(val a: String, val b: String, val c: List<Step>, val d: String)

@Composable
private fun StepCard(step: Step) {
    val c = OneUi.colors
    val context = LocalContext.current
    RoundCard {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = if (step.command != null) 14.dp else 18.dp)) {
            Text(step.title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = c.text)
            Text(step.text, fontSize = 14.sp, lineHeight = 19.sp, color = c.textSecondary, modifier = Modifier.padding(top = 3.dp))
            step.command?.let { cmd ->
                Row(
                    Modifier.padding(top = 12.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.tonal),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        cmd, fontFamily = FontFamily.Monospace, fontSize = 12.5.sp, lineHeight = 18.sp, color = c.text, softWrap = false,
                        modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()).padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 8.dp),
                    )
                    Box(
                        Modifier.padding(end = 6.dp).size(40.dp).clip(CircleShape).clickable {
                            context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Command", cmd))
                            Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                        },
                        contentAlignment = Alignment.Center,
                    ) { Icon(OneUiIcons.Copy, "Copy", tint = c.textSecondary, modifier = Modifier.size(20.dp)) }
                }
            }
        }
    }
}
