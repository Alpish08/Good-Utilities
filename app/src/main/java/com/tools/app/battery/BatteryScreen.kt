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
fun BatteryScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val c = OneUi.colors
    var now by remember { mutableStateOf(BatteryHealth.read(context)) }
    var history by remember { mutableStateOf(BatteryHealth.history(context)) }
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) { BatteryHealth.record(context, now) }
        history = BatteryHealth.history(context)
        while (true) { delay(1000); now = BatteryHealth.read(context) }
    }
    val n = NumberFormat.getIntegerInstance()

    OneUiScaffold(title = "Battery", onBack = onBack) {
        card(key = "hero") {
            Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                HealthRing(now.healthPercent)
                Column(Modifier.padding(start = 22.dp)) {
                    Text("Estimated health", fontSize = 15.sp, color = c.textSecondary)
                    Text(
                        now.estimatedMah?.let { "≈ ${n.format(it)} mAh" } ?: "Charge above 20% to estimate",
                        fontSize = 20.sp, fontWeight = FontWeight.Bold, color = c.text, modifier = Modifier.padding(top = 2.dp),
                    )
                    Text("of ${n.format(now.designMah)} mAh when new", fontSize = 14.sp, color = c.textSecondary)
                    now.cycles?.let { Text("$it charge cycles", fontSize = 14.sp, color = c.textSecondary, modifier = Modifier.padding(top = 6.dp)) }
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
                now.cycles?.let { "Charge cycles" to "$it" },
                "Condition" to now.health,
                "Capacity when new" to "${n.format(now.designMah)} mAh typical · ${n.format(now.ratedMah)} mAh rated",
                "Type" to now.technology,
            ),
            key = { "bat-" + it.first },
        ) { (label, value) -> ListRow(label, summary = value) }

        sectionLabel("Capacity over time")
        if (history.size >= 2) {
            card(key = "history") {
                val first = history.first(); val last = history.last()
                Column(Modifier.padding(20.dp)) {
                    Text(
                        "${n.format(last.mah - first.mah)} mAh since ${java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM).format(java.util.Date(first.day * 86_400_000L))}",
                        fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = c.text,
                    )
                    Sparkline(history.map { it.mah.toFloat() }, now.designMah.toFloat(), Modifier.padding(top = 14.dp).fillMaxWidth().height(90.dp))
                }
            }
        } else description(
            "Good Utilities saves one reading a day when you open it with the battery above 50%. A chart of your battery's capacity appears here after a couple of days.",
            key = "history-empty",
        )
        description(
            "Health is estimated from the fuel gauge (charge stored ÷ level), so it moves by a percent or two between readings. It is most accurate above 50%. Keeping your charge limit at 80–85% slows battery wear.",
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

@Composable
private fun Sparkline(values: List<Float>, design: Float, modifier: Modifier) {
    val c = OneUi.colors
    Canvas(modifier) {
        val lo = (values.min() - design * 0.02f).coerceAtMost(design * 0.9f)
        val hi = maxOf(values.max(), design)
        fun y(v: Float) = size.height - (v - lo) / (hi - lo) * size.height
        // Dashed line at the rated capacity.
        val dy = y(design)
        var x = 0f
        while (x < size.width) { drawLine(c.divider, Offset(x, dy), Offset(x + 8.dp.toPx(), dy), 1.dp.toPx()); x += 14.dp.toPx() }
        val path = Path()
        values.forEachIndexed { i, v ->
            val px = if (values.size == 1) 0f else i * size.width / (values.size - 1)
            if (i == 0) path.moveTo(px, y(v)) else path.lineTo(px, y(v))
        }
        drawPath(path, c.accent, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
        val lastX = size.width; val lastY = y(values.last())
        drawCircle(c.accent, 5.dp.toPx(), Offset(lastX, lastY))
    }
}

