package com.tools.app.battery

import com.tools.app.R
import com.tools.app.ui.t
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tools.app.ui.ListRow
import com.tools.app.ui.OneUiScaffold
import com.tools.app.ui.card
import com.tools.app.ui.cardList
import com.tools.app.ui.description
import com.tools.app.ui.sectionLabel
import com.tools.app.ui.theme.OneUi
import com.tools.app.ui.theme.OneUiEasing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import kotlin.math.abs

@Composable
fun BatteryScreen(onBack: () -> Unit, onGuide: () -> Unit = {}) {
    val context = LocalContext.current
    val c = OneUi.colors
    var now by remember { mutableStateOf(BatteryHealth.read(context)) }
    var samsung by remember { mutableStateOf<SamsungBattery?>(null) }
    LaunchedEffect(Unit) {
        // Re-read each tick so a sync from the Mac shows up while the page is open (prefs are in memory).
        while (true) {
            samsung = BatteryHealth.samsung(context)
            now = BatteryHealth.read(context).copy(samsung = samsung)
            delay(1000)
        }
    }
    val dateFmt = java.time.format.DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.MEDIUM)
    val n = NumberFormat.getIntegerInstance()

    OneUiScaffold(title = t(R.string.home_battery_info), onBack = onBack, actions = { com.tools.app.ui.OverflowMenu(listOf(t(R.string.lock_setup_guide) to onGuide, com.tools.app.ui.tipsItem(listOf(
        if (now.samsung == null) com.tools.app.ui.Tip(
            t(R.string.battery_estimated_health),
            t(R.string.battery_health_is_estimated_from_the_fuel_gaug) +
                t(R.string.battery_for_samsung_s_exact_health_and_lifetim),
        ) else com.tools.app.ui.Tip(
            t(R.string.battery_samsung_s_figures),
            t(R.string.battery_health_and_cycles_are_samsung_s_own_it, n.format(now.designMah)) +
                t(R.string.battery_as_of_the_last_sync_run_sync_battery_s),
        ),
    )))) }) {
        card(key = "hero") {
            Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                HealthRing(now.healthPercent)
                Column(Modifier.padding(start = 22.dp)) {
                    Text(if (now.healthIsSamsung) t(R.string.battery_battery_health) else t(R.string.battery_estimated_health), fontSize = 15.sp, color = c.textSecondary)
                    val mah = if (now.healthIsSamsung) now.healthPercent?.let { now.designMah * it / 100 } else now.estimatedMah
                    Text(
                        mah?.let { "${if (now.healthIsSamsung) "" else "≈ "}${n.format(it)} mAh" } ?: t(R.string.battery_charge_above_20_to_estimate),
                        fontSize = 20.sp, fontWeight = FontWeight.Bold, color = c.text, modifier = Modifier.padding(top = 2.dp),
                    )
                    Text(t(R.string.battery_of_mah_when_new, n.format(now.designMah)), fontSize = 14.sp, color = c.textSecondary)
                    now.totalCycles?.let { Text(t(R.string.battery_charge_cycles, n.format(it)), fontSize = 14.sp, color = c.textSecondary, modifier = Modifier.padding(top = 6.dp)) }
                }
            }
        }

        sectionLabel(t(R.string.battery_right_now))
        val status = when {
            now.charging -> t(R.string.charge_charging) + (now.plugged?.let { " · $it" } ?: "")
            now.plugged != null -> t(R.string.battery_plugged_in_not_charging, now.plugged)
            else -> t(R.string.charge_on_battery)
        }
        val currentText = now.currentMa?.let { ma ->
            val sign = if (ma > 0) "+" else if (ma < 0) "−" else ""
            "$sign${n.format(abs(ma))} mA" + (now.watts?.takeIf { it >= 0.05f }?.let { " · %.1f W".format(it) } ?: "")
        } ?: t(R.string.battery_not_reported)
        val tempColor = when {
            now.temperatureC >= 42f -> c.error
            now.temperatureC >= 38f -> com.tools.app.ui.theme.IconColors.Orange
            else -> null
        }
        cardList(
            listOf(
                Triple(t(R.string.battery_level), "${now.level}%", null),
                Triple(t(R.string.battery_status), status, null),
                Triple(t(R.string.battery_current), currentText, null),
                Triple(t(R.string.battery_temperature), "%.1f °C".format(now.temperatureC) + if (tempColor != null) t(R.string.battery_warm) else "", tempColor),
                Triple(t(R.string.battery_voltage), "%.2f V".format(now.voltageV), null),
            ),
            key = { "now-" + it.first },
        ) { (label, value, color) -> ListRow(label, summary = value, summaryColor = color) }

        sectionLabel(t(R.string.battery_battery))
        cardList(
            listOfNotNull(
                now.totalCycles?.let { t(R.string.battery_charge_cycles_2) to n.format(it) },
                now.samsung?.let { t(R.string.battery_samsung_figures) to t(R.string.battery_synced) + dateFmt.format(java.time.Instant.ofEpochMilli(it.syncedAt).atZone(java.time.ZoneId.systemDefault()).toLocalDate()) },
                now.estimatedPercent?.takeIf { now.healthIsSamsung }?.let { t(R.string.battery_today_s_estimate) to "≈ $it%" + if (now.samsung?.offset != null) t(R.string.battery_adjusted_to_samsung_s) else "" },
                now.samsung?.firstUse?.let { t(R.string.battery_first_used) to dateFmt.format(it) },
                now.samsung?.made?.let { t(R.string.battery_battery_made) to dateFmt.format(it) },
                t(R.string.battery_condition) to now.health,
                t(R.string.battery_capacity_when_new) to t(R.string.battery_mah_typical_mah_rated, n.format(now.designMah), n.format(now.ratedMah)),
                t(R.string.video_type) to now.technology,
            ),
            key = { "bat-" + it.first },
        ) { (label, value) -> ListRow(label, summary = value) }

    }
}

@Composable
private fun HealthRing(percent: Int?) {
    val c = OneUi.colors
    val p by animateFloatAsState((percent ?: 0) / 100f, tween(900, easing = OneUiEasing), label = "health")
    val color = when {
        percent == null -> c.textSecondary
        percent >= 85 -> c.success
        percent >= 75 -> com.tools.app.ui.theme.IconColors.Orange
        else -> c.error
    }
    Box(Modifier.size(104.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(104.dp)) {
            val w = 10.dp.toPx()
            val inset = w / 2
            val arc = Size(size.width - w, size.height - w)
            drawArc(c.sliderTrack, 135f, 270f, false, Offset(inset, inset), arc, style = Stroke(w, cap = StrokeCap.Round))
            if (p > 0f) drawArc(color, 135f, 270f * p, false, Offset(inset, inset), arc, style = Stroke(w, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(percent?.let { "$it%" } ?: "–", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = c.text)
        }
    }
}
