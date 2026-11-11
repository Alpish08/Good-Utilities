package com.tools.app.random

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.tools.app.ui.CardMargin
import com.tools.app.ui.Chip
import com.tools.app.ui.OneUiSwitch
import com.tools.app.ui.PillButton
import com.tools.app.ui.SegmentedControl
import com.tools.app.ui.ToolPage
import com.tools.app.ui.theme.OneUi
import com.tools.app.ui.theme.OneUiEasing
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.NumberFormat
import kotlin.math.abs
import kotlin.math.sqrt

private const val NumberRollMs = 600
private const val MaxAbs = 999_999_999_999L
private val SideOptions = listOf(4, 6, 8, 10, 12, 20)

private fun View.tick() { performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) }
private fun View.confirm() {
    performHapticFeedback(if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.CONTEXT_CLICK)
}

private fun fmt(n: Long): String = NumberFormat.getIntegerInstance().format(n)
private fun parse(s: String): Long? = s.toLongOrNull()?.takeIf { abs(it) <= MaxAbs }

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RandomScreen(onBack: () -> Unit) {
    val c = OneUi.colors
    val view = LocalView.current
    val context = LocalContext.current
    val focus = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val st = RandomState

    var rolling by remember { mutableStateOf(false) }
    var rollId by remember { mutableIntStateOf(0) }
    var job by remember { mutableStateOf<Job?>(null) }
    var liveNumbers by remember { mutableStateOf<List<Long>>(emptyList()) }
    var liveTotal by remember { mutableIntStateOf(0) }
    val pop = remember { Animatable(1f) }

    // ---- validation (number mode)
    val lo0 = parse(st.minText); val hi0 = parse(st.maxText)
    val lo = if (lo0 != null && hi0 != null) minOf(lo0, hi0) else null
    val hi = if (lo0 != null && hi0 != null) maxOf(lo0, hi0) else null
    val rangeSize = if (lo != null && hi != null) hi - lo + 1 else 0L
    val error: String? = when {
        st.mode != 0 -> null
        lo == null -> if (st.minText.isEmpty() || st.maxText.isEmpty() || st.minText == "-" || st.maxText == "-") "Enter a minimum and a maximum" else "Numbers up to 12 digits only"
        st.noRepeats && st.count > rangeSize -> "That range only has ${fmt(rangeSize)} number${if (rangeSize == 1L) "" else "s"}"
        else -> null
    }

    suspend fun finish() {
        view.confirm()
        pop.snapTo(1f)
        pop.animateTo(1.08f, tween(110, easing = OneUiEasing))
        pop.animateTo(1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium))
    }

    fun generate() {
        if (rolling || error != null) return
        focus.clearFocus()
        val a = lo!!; val b = hi!!
        if (parse(st.minText)!! > parse(st.maxText)!!) { st.minText = a.toString(); st.maxText = b.toString() }
        val result = Rng.numbers(a, b, st.count, st.noRepeats)
        st.numbers = result
        rolling = true
        job = scope.launch {
            val t0 = System.nanoTime()
            while (true) {
                val p = ((System.nanoTime() - t0) / 1_000_000f / NumberRollMs).coerceAtMost(1f)
                if (p >= 1f) break
                liveNumbers = List(result.size) { Rng.between(a, b) }
                view.tick()
                delay((32 + 110 * p * p).toLong())
            }
            rolling = false
            st.numbers = result
            RandomState.push(st.numberHistory, HistoryEntry(result.joinToString(", ") { fmt(it) }, result.joinToString(", ")))
            finish()
        }
    }

    fun rollDice() {
        if (rolling) return
        focus.clearFocus()
        val n = st.diceCount; val sides = st.sides
        val values = List(n) { Rng.between(1, sides.toLong()).toInt() }
        st.dice = values; st.diceSides = sides
        rollId += 1
        rolling = true
        job = scope.launch {
            val total = DiceStaggerMs * (n - 1) + DiceRollMs
            val t0 = System.nanoTime()
            while (true) {
                val el = (System.nanoTime() - t0) / 1_000_000L
                if (el >= total - 60) break
                liveTotal = List(n) { Rng.between(1, sides.toLong()).toInt() }.sum()
                delay((70 + 90 * (el.toFloat() / total)).toLong())
            }
            delay((total - (System.nanoTime() - t0) / 1_000_000L).coerceAtLeast(0))
            rolling = false
            val sum = values.sum()
            RandomState.push(
                st.diceHistory,
                HistoryEntry("$sum · ${n}d$sides", "$sum (${values.joinToString(" + ")}) with ${n}d$sides"),
            )
            finish()
        }
    }

    // Shake to roll: sensor only registered while the screen is resumed and in dice mode.
    val rollNow by rememberUpdatedState(::rollDice)
    if (st.mode == 1) ShakeEffect { rollNow() }

    ToolPage("Random", onBack) {
        // ---------- Top 40%
        Column(Modifier.weight(0.4f).fillMaxWidth()) {
            Box(
                Modifier.weight(1f).fillMaxWidth()
                    .then(
                        if (st.mode == 1) Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { rollDice() } else Modifier,
                    )
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (st.mode == 0) NumberResult(
                    values = if (rolling) liveNumbers else st.numbers,
                    modifier = Modifier.graphicsLayer { scaleX = pop.value; scaleY = pop.value },
                ) else DiceResult(rolling, rollId, liveTotal, pop.value, view)
            }
            HistoryRow(if (st.mode == 0) st.numberHistory else st.diceHistory)
            Spacer(Modifier.height(8.dp))
        }

        // ---------- Bottom 60%
        Column(
            Modifier
                .weight(0.6f)
                .padding(horizontal = CardMargin, vertical = 6.dp)
                .fillMaxWidth()
                .clip(OneUi.CardShape)
                .background(c.card)
                .imePadding()
                .padding(18.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                SegmentedControl(listOf("Number", "Dice"), st.mode, { if (!rolling) st.mode = it })
                Spacer(Modifier.height(16.dp))
                if (st.mode == 0) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        NumberField("Min", st.minText, { st.minText = it }, Modifier.weight(1f), error != null && lo == null && parse(st.minText) == null)
                        NumberField("Max", st.maxText, { st.maxText = it }, Modifier.weight(1f), error != null && lo == null && parse(st.maxText) == null)
                    }
                    SettingRow("How many") { Stepper(st.count, 1, 10) { st.count = it } }
                    Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("No repeats", fontSize = 15.5.sp, fontWeight = FontWeight.SemiBold, color = c.text, modifier = Modifier.weight(1f))
                        OneUiSwitch(st.noRepeats) { st.noRepeats = it }
                    }
                    if (error != null) Text(error, fontSize = 13.sp, color = c.error, modifier = Modifier.padding(top = 2.dp))
                } else {
                    SettingRow("Dice") { Stepper(st.diceCount, 1, 6) { st.diceCount = it } }
                    Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        SideOptions.forEach { s ->
                            Chip("d$s", st.sides == s, { st.sides = s }, Modifier.weight(1f), horizontalPadding = 2.dp)
                        }
                    }
                    Text("Tap the dice or shake your phone to roll", fontSize = 13.sp, color = c.textSecondary, modifier = Modifier.padding(top = 12.dp))
                }
            }
            PillButton(
                if (st.mode == 0) "Generate" else "Roll",
                onClick = { if (st.mode == 0) generate() else rollDice() },
                enabled = !rolling && error == null,
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            )
        }
    }
}

// ============================================================ results

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NumberResult(values: List<Long>, modifier: Modifier) {
    val c = OneUi.colors
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when {
            values.isEmpty() -> Text("Tap Generate", fontSize = 18.sp, color = c.textSecondary)
            values.size == 1 -> {
                val s = fmt(values[0])
                val size = when { s.length <= 4 -> 96; s.length <= 6 -> 80; s.length <= 9 -> 60; else -> 44 }
                RollText(s, size.sp, c.text)
            }
            else -> {
                val size = when { values.size <= 3 -> 34; values.size <= 6 -> 28; else -> 24 }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                ) {
                    values.forEach { v ->
                        Box(
                            Modifier.clip(RoundedCornerShape(18.dp)).background(c.card).padding(horizontal = 16.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center,
                        ) { RollText(fmt(v), size.sp, c.text) }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DiceResult(rolling: Boolean, rollId: Int, liveTotal: Int, pop: Float, view: View) {
    val c = OneUi.colors
    val st = RandomState
    val n = st.diceCount; val sides = st.sides
    val has = st.dice.size == n && st.diceSides == sides
    val dieSize: Dp = when { n <= 2 -> 84.dp; n <= 3 -> 76.dp; else -> 60.dp }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.padding(vertical = 14.dp),
        ) {
            repeat(n) { i ->
                TumblingDie(
                    sides, if (has) st.dice[i] else 1, dieSize,
                    // A die only tumbles when its own roll matches the current set.
                    if (has) rollId else 0, i, tick = { view.tick() },
                )
            }
        }
        Box(Modifier.height(64.dp).graphicsLayer { scaleX = pop; scaleY = pop }, contentAlignment = Alignment.Center) {
            when {
                rolling -> RollText(liveTotal.toString(), 52.sp, c.textSecondary)
                has -> RollText(st.dice.sum().toString(), 56.sp, c.text)
                else -> Text("Total", fontSize = 18.sp, color = c.textSecondary)
            }
        }
    }
}

/** Digits that slot-machine vertically when they change. */
@OptIn(ExperimentalAnimationApi::class)
@Composable
private fun RollText(text: String, size: androidx.compose.ui.unit.TextUnit, color: androidx.compose.ui.graphics.Color) {
    Row(horizontalArrangement = Arrangement.Center) {
        text.forEachIndexed { i, ch ->
            AnimatedContent(
                ch,
                transitionSpec = {
                    (slideInVertically(tween(110)) { -it / 2 } + fadeIn(tween(90))) togetherWith
                        (slideOutVertically(tween(110)) { it / 2 } + fadeOut(tween(70)))
                },
                label = "digit$i",
            ) { d -> Text(d.toString(), fontSize = size, fontWeight = FontWeight.Bold, color = color, maxLines = 1, softWrap = false) }
        }
    }
}

@Composable
private fun HistoryRow(items: List<HistoryEntry>) {
    val c = OneUi.colors
    val context = LocalContext.current
    if (items.isEmpty()) { Spacer(Modifier.height(36.dp)); return }
    LazyRow(
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = CardMargin + 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        items(items) { e ->
            Box(
                Modifier.height(36.dp).clip(CircleShape).background(c.card)
                    .clickable {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("Random result", e.copy))
                        if (Build.VERSION.SDK_INT < 33) Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                    }
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(e.label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}


// ============================================================ controls

@Composable
private fun SettingRow(label: String, trailing: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().height(52.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 15.5.sp, fontWeight = FontWeight.SemiBold, color = OneUi.colors.text, modifier = Modifier.weight(1f))
        trailing()
    }
}

@Composable
private fun NumberField(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier, invalid: Boolean) {
    val c = OneUi.colors
    val focus = LocalFocusManager.current
    Column(modifier) {
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (invalid) c.error else c.textSecondary, modifier = Modifier.padding(start = 14.dp, bottom = 4.dp))
        Row(
            Modifier.fillMaxWidth().height(50.dp).clip(RoundedCornerShape(18.dp)).background(c.tonal).padding(start = 16.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicTextField(
                value = value,
                onValueChange = { raw ->
                    val t = raw.filterIndexed { i, ch -> ch.isDigit() || (i == 0 && ch == '-') }
                    onChange(t.take(if (t.startsWith("-")) 13 else 12))
                },
                singleLine = true,
                textStyle = TextStyle(fontFamily = com.tools.app.ui.theme.SamsungFont, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = c.text),
                cursorBrush = SolidColor(c.accent),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
                modifier = Modifier.weight(1f),
            )
            // Some number pads have no minus key, so offer a sign toggle.
            Box(
                Modifier.size(38.dp).clip(CircleShape).clickable { onChange(if (value.startsWith("-")) value.drop(1) else "-$value") },
                contentAlignment = Alignment.Center,
            ) { Text("±", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = c.accent) }
        }
    }
}

@Composable
private fun Stepper(value: Int, min: Int, max: Int, onChange: (Int) -> Unit) {
    val c = OneUi.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        StepButton(false, value > min) { onChange(value - 1) }
        Text(value.toString(), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = c.text, textAlign = TextAlign.Center, modifier = Modifier.width(36.dp))
        StepButton(true, value < max) { onChange(value + 1) }
    }
}

@Composable
private fun StepButton(plus: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val c = OneUi.colors
    Box(
        Modifier.size(30.dp).clip(CircleShape).background(c.tonal).clickable(enabled = enabled, onClick = onClick)
            .graphicsLayer { alpha = if (enabled) 1f else 0.35f },
        contentAlignment = Alignment.Center,
    ) {
        val col = c.text
        Canvas(Modifier.size(12.dp)) {
            val w = 1.6.dp.toPx(); val m = size.width / 2
            drawLine(col, Offset(0f, m), Offset(size.width, m), w, StrokeCap.Round)
            if (plus) drawLine(col, Offset(m, 0f), Offset(m, size.height), w, StrokeCap.Round)
        }
    }
}

// ============================================================ shake

@Composable
private fun ShakeEffect(onShake: () -> Unit) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val current by rememberUpdatedState(onShake)
    DisposableEffect(owner) {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val accel = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        var last = 0L
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                val g = sqrt(e.values[0] * e.values[0] + e.values[1] * e.values[1] + e.values[2] * e.values[2]) / SensorManager.GRAVITY_EARTH
                val now = System.currentTimeMillis()
                if (g > 2.7f && now - last > 1200) { last = now; current() }
            }
            override fun onAccuracyChanged(s: Sensor?, a: Int) {}
        }
        var on = false
        fun start() { if (!on && accel != null) { sm.registerListener(listener, accel, SensorManager.SENSOR_DELAY_UI); on = true } }
        fun stop() { if (on) { sm.unregisterListener(listener); on = false } }
        val obs = LifecycleEventObserver { _, ev ->
            if (ev == Lifecycle.Event.ON_RESUME) start() else if (ev == Lifecycle.Event.ON_PAUSE) stop()
        }
        owner.lifecycle.addObserver(obs)
        if (owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) start()
        onDispose { owner.lifecycle.removeObserver(obs); stop() }
    }
}
