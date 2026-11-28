package com.tools.app.battery

import com.tools.app.R
import com.tools.app.ui.t
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
    var autoEnabled by remember { mutableStateOf(AutoBypass.enabled(context)) }
    var autoOn by remember { mutableIntStateOf(AutoBypass.onAt(context)) }
    var autoOff by remember { mutableIntStateOf(AutoBypass.offAt(context)) }
    var autoNight by remember { mutableStateOf(AutoBypass.skipNight(context)) }

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

    OneUiScaffold(title = t(R.string.home_charge_limit), onBack = onBack, actions = { com.tools.app.ui.TipsMenu(listOf(
        com.tools.app.ui.Tip(t(R.string.charge_how_it_works), t(R.string.charge_stops_charging_at_any_level_you_choose)),
        com.tools.app.ui.Tip(t(R.string.charge_the_now_chip), t(R.string.charge_now_holds_the_battery_where_it_is_so_t)),
        com.tools.app.ui.Tip(t(R.string.charge_auto_bypass_when_hot), t(R.string.charge_uses_app_lock_s_background_service_to)),
        com.tools.app.ui.Tip(t(R.string.charge_turning_it_off), t(R.string.charge_turning_this_off_restores_your_previou)),
    )) }) {
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
                            bypass -> t(R.string.charge_bypass_charging_running_on_the_charger)
                            charging -> t(R.string.charge_charging)
                            battery.plugged -> t(R.string.charge_plugged_in_not_charging)
                            else -> t(R.string.charge_on_battery)
                        },
                        fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                        color = if (bypass) c.accent else c.textSecondary, modifier = Modifier.padding(top = 10.dp),
                    )
                }
            }
        }

        if (!canWrite) {
            sectionLabel(t(R.string.charge_one_time_setup))
            card("grant") {
                Column(Modifier.padding(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 16.dp)) {
                    Text(t(R.string.charge_samsung_protects_this_setting_connect), fontSize = 15.sp, color = c.text, lineHeight = 21.sp)
                    Text(
                        ChargeLimit.GRANT_COMMAND, fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = c.text,
                        modifier = Modifier.padding(vertical = 12.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.tonal).padding(14.dp),
                    )
                    PillButton(t(R.string.charge_copy_command), tonal = true, height = 44.dp, onClick = {
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

        sectionLabel(t(R.string.charge_stop_charging_at))
        card("limit") {
            Column(Modifier.padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 18.dp)) {
                Text("${limit.roundToInt()}%", fontSize = 34.sp, fontWeight = FontWeight.Bold, color = if (canWrite) c.accent else c.textSecondary)
                OneUiSlider(
                    value = limit, onValueChange = { limit = it.roundToInt().toFloat() }, valueRange = ChargeLimit.MIN_LIMIT.toFloat()..100f,
                    modifier = Modifier.padding(top = 6.dp),
                    onValueChangeFinished = { if (active != null && canWrite) apply(limit.roundToInt()) },
                )
                Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val nowLabel = t(R.string.charge_now_level, "${battery.level}%")
                    val presets = listOf(battery.level to nowLabel, 60 to "60%", 80 to "80%", 90 to "90%")
                    presets.forEach { (v, label) ->
                        Chip(label, selected = limit.roundToInt() == v && (label == nowLabel || v != battery.level), onClick = {
                            limit = v.toFloat()
                            if (active != null && canWrite) apply(v)
                        })
                    }
                }
            }
        }

        if (android.os.Build.VERSION.SDK_INT >= 33) card("tile") {
            ListRow(
                t(R.string.charge_add_bypass_charging_to_quick_panel),
                summary = t(R.string.charge_one_tap_holds_the_battery_at_its_curre),
                onClick = {
                    context.getSystemService(android.app.StatusBarManager::class.java).requestAddTileService(
                        android.content.ComponentName(context, BypassTileService::class.java),
                        t(R.string.charge_bypass_charging),
                        android.graphics.drawable.Icon.createWithResource(context, com.tools.app.R.drawable.ic_qs_bypass),
                        context.mainExecutor,
                    ) { }
                },
            )
        }

        sectionLabel(t(R.string.charge_auto_bypass_when_hot))
        card("auto") {
            Column {
                ListRow(
                    t(R.string.charge_auto_bypass_when_hot),
                    summary = t(R.string.charge_while_plugged_in_run_from_the_charger, autoOn) +
                        t(R.string.charge_charging_resumes_when_it_cools_to_c_or, autoOff),
                    trailing = { com.tools.app.ui.OneUiSwitch(autoEnabled) },
                    onClick = { autoEnabled = !autoEnabled; AutoBypass.setEnabled(context, autoEnabled) },
                    enabled = canWrite,
                )
                if (autoEnabled) {
                    TempChips(t(R.string.charge_turn_on_at), AutoBypass.ON_CHOICES, autoOn) {
                        AutoBypass.setOnAt(context, it); autoOn = AutoBypass.onAt(context); autoOff = AutoBypass.offAt(context)
                    }
                    TempChips(t(R.string.charge_turn_off_when_cooled_to), AutoBypass.OFF_CHOICES.filter { it < autoOn }, autoOff) {
                        AutoBypass.setOffAt(context, it); autoOff = AutoBypass.offAt(context)
                    }
                    ListRow(
                        t(R.string.charge_skip_at_night),
                        summary = t(R.string.charge_leaves_to_your_night_routine_s_slow_ch, AutoBypass.NIGHT_LABEL),
                        trailing = { com.tools.app.ui.OneUiSwitch(autoNight) },
                        onClick = { autoNight = !autoNight; AutoBypass.setSkipNight(context, autoNight) },
                    )
                }
            }
        }

        sectionLabel(t(R.string.charge_samsung_setting_right_now))
        card("samsung") {
            ListRow(
                t(R.string.charge_battery_protection, samsung.modeName),
                summary = t(R.string.charge_stops_at_resumes_below, samsung.threshold, samsung.recharge) +
                    if (samsung.mode != 1 && active != null) t(R.string.charge_nsamsung_changed_it_tap_to_apply_your) else "",
                summaryColor = if (samsung.mode != 1 && active != null) c.error else null,
                onClick = if (active != null && canWrite) ({ apply(limit.roundToInt()) }) else null,
            )
        }
        error?.let { e -> description(t(R.string.charge_couldn_t_change_the_setting, e), key = "error") }
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

/** A row of temperature chips (°C). */
@Composable
private fun TempChips(title: String, choices: List<Int>, selected: Int, onPick: (Int) -> Unit) {
    val c = OneUi.colors
    Column(Modifier.padding(start = 18.dp, end = 18.dp, top = 6.dp, bottom = 12.dp)) {
        Text(title, fontSize = 15.sp, color = c.text)
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            choices.forEach { t -> Chip("$t°C", selected = t == selected, onClick = { onPick(t) }) }
        }
    }
}
