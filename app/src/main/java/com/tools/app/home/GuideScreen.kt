package com.tools.app.home

import com.tools.app.R
import com.tools.app.ui.t
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
            t(R.string.guide_turn_on_usb_debugging),
            t(R.string.guide_settings_about_phone_software_informat),
        ),
        Step(
            t(R.string.guide_install_adb_on_your_mac_once),
            t(R.string.guide_homebrew_installs_google_s_platform_to),
            "brew install android-platform-tools\nadb devices",
        ),
    )
    val (title, intro, steps, note) = when (kind) {
        GuideKind.AppLock -> Quad(
            t(R.string.guide_app_lock_setup),
            t(R.string.guide_app_lock_needs_its_accessibility_servi),
            listOf(
                Step(
                    t(R.string.guide_run_the_app_lock_script),
                    t(R.string.guide_if_you_have_it_on_your_mac_this_does_e),
                    "~/enable-applock.sh",
                ),
                Step(
                    t(R.string.guide_or_run_the_commands_yourself),
                    t(R.string.guide_allows_restricted_settings_for_good_ut),
                    "adb shell appops set com.tools.app ACCESS_RESTRICTED_SETTINGS allow\n" +
                        "adb shell settings put secure enabled_accessibility_services com.tools.app/com.tools.app.applock.AppLockService\n" +
                        "adb shell settings put secure accessibility_enabled 1",
                ),
            ),
            t(R.string.guide_the_second_command_replaces_your_list),
        )
        GuideKind.Battery -> Quad(
            t(R.string.guide_battery_setup),
            t(R.string.guide_samsung_keeps_its_battery_health_asoc),
            listOf(
                Step(
                    t(R.string.guide_run_the_sync_script),
                    t(R.string.guide_reads_samsung_s_battery_report_and_sen),
                    "~/sync-battery.sh",
                ),
                Step(
                    t(R.string.guide_or_see_the_figures_yourself),
                    t(R.string.guide_msavedbatteryasoc_is_health_in_msavedb),
                    "adb shell dumpsys battery | grep -E \"Asoc|Usage|FirstUseDate|LLB MAN\"",
                ),
                Step(
                    t(R.string.guide_and_send_them_by_hand),
                    t(R.string.guide_replace_97_and_75808_with_your_own_aso),
                    "adb shell am broadcast -f 32 -a com.tools.app.SAMSUNG_BATTERY -n com.tools.app/.battery.SamsungBatterySync --ei asoc 97 --ei usage 75808",
                ),
            ),
            t(R.string.guide_only_adb_or_the_system_can_send_these),
        )
    }

    OneUiScaffold(title = title, onBack = onBack) {
        description(intro, key = "intro")
        sectionLabel(t(R.string.guide_before_you_start), key = "before")
        before.forEachIndexed { i, s -> item(key = "b$i") { StepCard(s) } }
        sectionLabel(t(R.string.guide_each_time), key = "each")
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
                            context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText(t(R.string.guide_command), cmd))
                            Toast.makeText(context, t(R.string.reader_copied), Toast.LENGTH_SHORT).show()
                        },
                        contentAlignment = Alignment.Center,
                    ) { Icon(OneUiIcons.Copy, t(R.string.reader_copy), tint = c.textSecondary, modifier = Modifier.size(20.dp)) }
                }
            }
        }
    }
}
