package com.tools.app.random

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import com.tools.app.ui.theme.OneUi
import com.tools.app.ui.theme.OneUiEasing
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

const val DiceStaggerMs = 70L
const val DiceRollMs = 720

private class PolyShape(val n: Int, val rotation: Float, val radiusFrac: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Generic(roundedPoly(size, n, rotation, size.minDimension * radiusFrac))
}

private fun roundedPoly(size: Size, n: Int, rotationDeg: Float, corner: Float): Path {
    val cx = size.width / 2
    // A triangle drawn in the circle would sit high and look small, so it fills the box instead
    // and is centred by its bounding box (it is r * 1.5 tall).
    val r = if (n == 3) minOf(size.width / 1.732f, size.height / 1.5f) else size.minDimension / 2
    val cy = if (n == 3) size.height / 2 + r / 4 else size.height / 2
    val v = List(n) {
        val a = Math.toRadians((rotationDeg + it * 360f / n).toDouble())
        Offset(cx + r * cos(a).toFloat(), cy + r * sin(a).toFloat())
    }
    val path = Path()
    for (i in 0 until n) {
        val p = v[(i + n - 1) % n]; val c = v[i]; val q = v[(i + 1) % n]
        fun toward(o: Offset): Offset {
            val d = hypot(o.x - c.x, o.y - c.y)
            val t = (corner / d).coerceAtMost(0.45f)
            return Offset(c.x + (o.x - c.x) * t, c.y + (o.y - c.y) * t)
        }
        val a = toward(p); val b = toward(q)
        if (i == 0) path.moveTo(a.x, a.y) else path.lineTo(a.x, a.y)
        path.quadraticTo(c.x, c.y, b.x, b.y)
    }
    path.close()
    return path
}

private fun dieShape(sides: Int): Shape = when (sides) {
    4 -> PolyShape(3, -90f, 0.11f)
    6 -> RoundedCornerShape(22)
    8 -> PolyShape(4, -90f, 0.14f)
    10 -> PolyShape(5, -90f, 0.14f)
    12 -> PolyShape(10, -90f, 0.12f)
    else -> PolyShape(6, -90f, 0.14f)
}

private val Pips = mapOf(
    1 to listOf(4), 2 to listOf(0, 8), 3 to listOf(0, 4, 8), 4 to listOf(0, 2, 6, 8),
    5 to listOf(0, 2, 4, 6, 8), 6 to listOf(0, 2, 3, 5, 6, 8),
)

/** Static die face. */
@Composable
fun DieFace(sides: Int, value: Int, size: Dp, modifier: Modifier = Modifier) {
    val c = OneUi.colors
    val shape = dieShape(sides)
    val face = if (c.isDark) Color(0xFF2B2B30) else Color.White
    Box(
        modifier
            .size(size)
            .shadow(if (c.isDark) 0.dp else 7.dp, shape, ambientColor = Color(0x33000000), spotColor = Color(0x44000000))
            .graphicsLayer { clip = false }
            .border(if (c.isDark) 1.dp else 0.dp, c.divider, shape)
            .background(face, shape),
        contentAlignment = Alignment.Center,
    ) {
        if (sides == 6) {
            val pip = c.accent
            Canvas(Modifier.size(size)) {
                val s = this.size.minDimension
                val pos = listOf(0.28f, 0.5f, 0.72f)
                Pips[value.coerceIn(1, 6)]!!.forEach { idx ->
                    drawCircle(pip, s * 0.085f, Offset(pos[idx % 3] * s, pos[idx / 3] * s))
                }
            }
        } else {
            val dy = if (sides == 4) size * 0.13f else 0.dp
            Text(
                "$value", color = c.accent, fontWeight = FontWeight.Bold,
                fontSize = (size.value * if (value >= 10) 0.36f else if (sides == 4) 0.36f else 0.42f).sp,
                modifier = Modifier.offset(y = dy),
            )
        }
    }
}

/**
 * A die that tumbles when [rollId] changes (0 = no roll yet), settling on [value].
 * [index] staggers the start.
 */
@Composable
fun TumblingDie(sides: Int, value: Int, size: Dp, rollId: Int, index: Int, tick: () -> Unit) {
    val rotation = remember { Animatable(0f) }
    val lift = remember { Animatable(0f) }
    val sy = remember { Animatable(1f) }
    val sx = remember { Animatable(1f) }
    var rolling by remember { mutableStateOf(false) }
    var shown by remember { mutableIntStateOf(value) }
    val px = with(LocalDensity.current) { size.toPx() }
    val max = sides

    LaunchedEffect(rollId) {
        if (rollId == 0) return@LaunchedEffect
        delay(index * DiceStaggerMs)
        rolling = true
        val dir = if ((index + rollId) % 2 == 0) 1f else -1f
        coroutineScope {
            launch { rotation.snapTo(0f); rotation.animateTo(dir * (540f + 90f * (index % 3)), tween(DiceRollMs, easing = OneUiEasing)); rotation.snapTo(0f) }
            launch {
                lift.snapTo(0f)
                lift.animateTo(0f, keyframes {
                    durationMillis = DiceRollMs
                    0f at 0; (-0.5f * px) at 200 using FastOutSlowInEasing; 0f at 430 using FastOutSlowInEasing
                    (-0.14f * px) at 560 using FastOutSlowInEasing; 0f at DiceRollMs using FastOutSlowInEasing
                })
            }
            launch {
                sy.animateTo(1f, keyframes { durationMillis = DiceRollMs; 1f at 0; 1.06f at 200; 0.86f at 430; 1.03f at 560; 1f at DiceRollMs })
            }
            launch {
                sx.animateTo(1f, keyframes { durationMillis = DiceRollMs; 1f at 0; 0.96f at 200; 1.12f at 430; 0.99f at 560; 1f at DiceRollMs })
            }
            launch {
                val t0 = System.nanoTime()
                while (true) {
                    val p = ((System.nanoTime() - t0) / 1_000_000f / (DiceRollMs - 80)).coerceAtMost(1f)
                    if (p >= 1f) break
                    shown = Rng.between(1, max.toLong()).toInt()
                    tick()
                    delay((40 + 110 * p * p).toLong())
                }
            }
        }
        rolling = false
    }

    DieFace(
        sides, if (rolling) shown else value, size,
        Modifier.graphicsLayer {
            rotationZ = rotation.value
            translationY = lift.value
            scaleX = sx.value; scaleY = sy.value
        },
    )
}
