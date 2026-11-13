package com.tools.app.battery

import androidx.compose.material.icons.rounded.Bolt

import androidx.compose.material.icons.Icons

import com.tools.app.ui.OneUiIcons
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tools.app.ui.Chip
import com.tools.app.ui.ListRow
import com.tools.app.ui.MasterSwitchCard
import com.tools.app.ui.OneUiScaffold
import com.tools.app.ui.OneUiSlider
import com.tools.app.ui.PillButton
import com.tools.app.ui.RoundCard
import com.tools.app.ui.card
import com.tools.app.ui.description
import com.tools.app.ui.sectionLabel
import com.tools.app.ui.theme.OneUi
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt

private data class BatteryInfo(val level: Int = 0, val status: Int = 0, val plugged: Boolean = false, val tempC: Float = 0f)

@Composable
fun ChargeLimitScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val c = OneUi.colors
    var battery by remember { mutableStateOf(BatteryInfo()) }
    var currentMa by remember { mutableIntStateOf(0) }
    var canWrite by remember { mutableStateOf(ChargeLimit.canWrite(context)) }
    var samsung by remember { mutableStateOf(ChargeLimit.read(context)) }
    var active by remember { mutableStateOf(ChargeLimit.activeLimit(context)) }
    var limit by remember { mutableFloatStateOf((ChargeLimit.activeLimit(context) ?: ChargeLimit.lastLimit(context)).toFloat()) }
    var error by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) {
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(ctx: Context, i: Intent) {
                battery = BatteryInfo(
                    level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, 0) * 100 / i.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1),
                    status = i.getIntExtra(BatteryManager.EXTRA_STATUS, 0),
                    plugged = i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0,
                    tempC = i.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10f,
                )
            }
        }
        context.registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        onDispose { context.unregisterReceiver(receiver) }
    }
    LaunchedEffect(Unit) {
        val bm = context.getSystemService(BatteryManager::class.java)
        while (true) {
            val raw = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
            currentMa = if (abs(raw) > 20_000) raw / 1000 else raw // some kernels report µA
            canWrite = ChargeLimit.canWrite(context)
            samsung = ChargeLimit.read(context)
            delay(2000)
        }
    }

    fun apply(value: Int) = runCatching { ChargeLimit.apply(context, value); active = value; samsung = ChargeLimit.read(context) }
        .onFailure { error = it.message }

    val charging = battery.status == BatteryManager.BATTERY_STATUS_CHARGING
    val bypass = battery.plugged && !charging && abs(currentMa) < 150

    OneUiScaffold(title = "Charge limit", onBack = onBack) {
        item("status") {
            RoundCard {
                Column(Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (charging) Icon(OneUiIcons.Bolt, null, tint = c.success, modifier = Modifier.size(34.dp))
                        Text("${battery.level}%", fontSize = 40.sp, fontWeight = FontWeight.Bold, color = if (charging) c.success else c.text)
                        Box(Modifier.weight(1f))
                        Text(
                            "${if (currentMa > 0) "+" else ""}${"%,d".format(currentMa)} mA\n${"%.1f".format(battery.tempC)}°C",
                            fontSize = 14.sp, color = c.textSecondary, lineHeight = 18.sp, textAlign = androidx.compose.ui.text.style.TextAlign.End,
                        )
                    }
                    LevelBar(battery.level, active, charging, Modifier.padding(top = 14.dp))
                    Text(
                        when {
                            bypass -> "Bypass charging · running on the charger, battery idle"
                            charging -> "Charging"
                            battery.plugged -> "Plugged in · not charging"
                            else -> "On battery"
                        },
                        fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                        color = if (bypass) c.accent else c.textSecondary, modifier = Modifier.padding(top = 10.dp),
                    )
                }
            }
        }

        if (!canWrite) {
            sectionLabel("One-time setup")
            card("grant") {
                Column(Modifier.padding(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 16.dp)) {
                    Text("Samsung protects this setting. Connect your phone to a computer with USB debugging on and run:", fontSize = 15.sp, color = c.text, lineHeight = 21.sp)
                    Text(
                        ChargeLimit.GRANT_COMMAND, fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = c.text,
                        modifier = Modifier.padding(vertical = 12.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.tonal).padding(14.dp),
                    )
                    PillButton("Copy command", tonal = true, height = 44.dp, onClick = {
                        context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("adb", ChargeLimit.GRANT_COMMAND))
                    })
                }
            }
        }

        item("master") {
            MasterSwitchCard(
                checked = active != null, enabled = canWrite,
                onCheckedChange = { on ->
                    if (on) apply(limit.roundToInt())
                    else { ChargeLimit.restore(context); active = null; samsung = ChargeLimit.read(context) }
                },
            )
        }
        description("Stops charging at any level you choose, using Samsung's own Battery protection. While plugged in at the limit, the phone runs straight from the charger: ideal for gaming, DeX or long navigation sessions.")

        sectionLabel("Stop charging at")
        card("limit") {
            Column(Modifier.padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 18.dp)) {
                Text("${limit.roundToInt()}%", fontSize = 34.sp, fontWeight = FontWeight.Bold, color = if (canWrite) c.accent else c.textSecondary)
                OneUiSlider(
                    value = limit, onValueChange = { limit = it.roundToInt().toFloat() }, valueRange = ChargeLimit.MIN_LIMIT.toFloat()..100f,
                    modifier = Modifier.padding(top = 6.dp),
                    onValueChangeFinished = { if (active != null && canWrite) apply(limit.roundToInt()) },
                )
                Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val presets = listOf(battery.level to "Now ${battery.level}%", 60 to "60%", 80 to "80%", 90 to "90%")
                    presets.forEach { (v, label) ->
                        Chip(label, selected = limit.roundToInt() == v && (label.startsWith("Now") || v != battery.level), onClick = {
                            limit = v.toFloat()
                            if (active != null && canWrite) apply(v)
                        })
                    }
                }
                Text(
                    "\"Now\" holds the battery where it is, so the charger powers the phone directly.",
                    fontSize = 13.sp, color = c.textSecondary, modifier = Modifier.padding(top = 10.dp),
                )
            }
        }

        if (android.os.Build.VERSION.SDK_INT >= 33) card("tile") {
            ListRow(
                "Add Bypass charging to Quick panel",
                summary = "One tap holds the battery at its current level. Touch and hold the tile to open this page.",
                onClick = {
                    context.getSystemService(android.app.StatusBarManager::class.java).requestAddTileService(
                        android.content.ComponentName(context, BypassTileService::class.java),
                        "Bypass charging",
                        android.graphics.drawable.Icon.createWithResource(context, com.tools.app.R.drawable.ic_qs_bypass),
                        context.mainExecutor,
                    ) { }
                },
            )
        }

        sectionLabel("Samsung setting right now")
        card("samsung") {
            ListRow(
                "Battery protection: ${samsung.modeName}",
                summary = "Stops at ${samsung.threshold}% · resumes below ${samsung.recharge}%" +
                    if (samsung.mode != 1 && active != null) "\nSamsung changed it. Tap to apply your limit again" else "",
                summaryColor = if (samsung.mode != 1 && active != null) c.error else null,
                onClick = if (active != null && canWrite) ({ apply(limit.roundToInt()) }) else null,
            )
        }
        error?.let { e -> description("Couldn't change the setting: $e", key = "error") }
        description("Turning this off restores your previous Battery protection setting. If you open Samsung's Battery protection page and change it there, it may overwrite the custom limit.")
    }
}

@Composable
private fun LevelBar(level: Int, limit: Int?, charging: Boolean, modifier: Modifier = Modifier) {
    val c = OneUi.colors
    Box(modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(7.dp)).background(c.tonal)) {
        Box(Modifier.fillMaxWidth(level / 100f).height(14.dp).background(if (charging) c.success else c.accent))
        if (limit != null) {
            Row(Modifier.fillMaxWidth()) {
                Box(Modifier.weight(limit.coerceIn(1, 99).toFloat()))
                Box(Modifier.width(3.dp).height(14.dp).background(if (c.isDark) Color.White else Color.Black))
                Box(Modifier.weight((100 - limit).coerceIn(1, 99).toFloat()))
            }
        }
    }
}
