package com.tools.app.battery

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

    OneUiScaffold(title = "Battery info", onBack = onBack, actions = { com.tools.app.ui.OverflowMenu(listOf("Setup guide" to onGuide)) }) {
        card(key = "hero") {
            Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                HealthRing(now.healthPercent)
                Column(Modifier.padding(start = 22.dp)) {
                    Text(if (now.healthIsSamsung) "Battery health" else "Estimated health", fontSize = 15.sp, color = c.textSecondary)
                    val mah = if (now.healthIsSamsung) now.healthPercent?.let { now.designMah * it / 100 } else now.estimatedMah
                    Text(
                        mah?.let { "${if (now.healthIsSamsung) "" else "≈ "}${n.format(it)} mAh" } ?: "Charge above 20% to estimate",
                        fontSize = 20.sp, fontWeight = FontWeight.Bold, color = c.text, modifier = Modifier.padding(top = 2.dp),
                    )
                    Text("of ${n.format(now.designMah)} mAh when new", fontSize = 14.sp, color = c.textSecondary)
                    now.totalCycles?.let { Text("${n.format(it)} charge cycles", fontSize = 14.sp, color = c.textSecondary, modifier = Modifier.padding(top = 6.dp)) }
                }
            }
        }

        sectionLabel("Right now")
        val status = when {
            now.charging -> "Charging" + (now.plugged?.let { " · $it" } ?: "")
            now.plugged != null -> "Plugged in, not charging (${now.plugged})"
            else -> "On battery"
        }
        val currentText = now.currentMa?.let { ma ->
            val sign = if (ma > 0) "+" else if (ma < 0) "−" else ""
            "$sign${n.format(abs(ma))} mA" + (now.watts?.takeIf { it >= 0.05f }?.let { " · %.1f W".format(it) } ?: "")
        } ?: "Not reported"
        val tempColor = when {
            now.temperatureC >= 42f -> c.error
            now.temperatureC >= 38f -> com.tools.app.ui.theme.IconColors.Orange
            else -> null
        }
        cardList(
            listOf(
                Triple("Level", "${now.level}%", null),
                Triple("Status", status, null),
                Triple("Current", currentText, null),
                Triple("Temperature", "%.1f °C".format(now.temperatureC) + if (tempColor != null) " · warm" else "", tempColor),
                Triple("Voltage", "%.2f V".format(now.voltageV), null),
            ),
            key = { "now-" + it.first },
        ) { (label, value, color) -> ListRow(label, summary = value, summaryColor = color) }

        sectionLabel("Battery")
        cardList(
            listOfNotNull(
                now.totalCycles?.let { "Charge cycles" to n.format(it) },
                now.samsung?.let { "Samsung figures" to "Synced " + dateFmt.format(java.time.Instant.ofEpochMilli(it.syncedAt).atZone(java.time.ZoneId.systemDefault()).toLocalDate()) },
                now.estimatedPercent?.takeIf { now.healthIsSamsung }?.let { "Today's estimate" to "≈ $it%" + if (now.samsung?.offset != null) " (adjusted to Samsung's)" else "" },
                now.samsung?.firstUse?.let { "First used" to dateFmt.format(it) },
                now.samsung?.made?.let { "Battery made" to dateFmt.format(it) },
                "Condition" to now.health,
                "Capacity when new" to "${n.format(now.designMah)} mAh typical · ${n.format(now.ratedMah)} mAh rated",
                "Type" to now.technology,
            ),
            key = { "bat-" + it.first },
        ) { (label, value) -> ListRow(label, summary = value) }

        if (now.samsung == null) description(
            "Health is estimated from the fuel gauge (charge stored ÷ level), which reads a couple of points high on Galaxy phones. " +
                "For Samsung's exact health and lifetime cycle count, connect your phone to your Mac and run sync-battery.sh.",
            key = "about",
        ) else description(
            "Health and cycles are Samsung's own (its fuel gauge's learned capacity as a share of the ${n.format(now.designMah)} mAh design), " +
                "as of the last sync. Run sync-battery.sh again every few weeks to refresh them. Cycles include any previous owner's.",
            key = "about",
        )
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
