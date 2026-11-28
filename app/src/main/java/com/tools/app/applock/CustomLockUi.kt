package com.tools.app.applock

import com.tools.app.R
import com.tools.app.ui.t
import com.tools.app.ui.OneUiIcons
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tools.app.ui.theme.SamsungFont

/** UI state for Tools' own lock screen, driven by LockActivity. */
class CustomLockState {
    var revealed by mutableStateOf(false)
    var pinMode by mutableStateOf(false)
    var fingerprintAvailable by mutableStateOf(true)
    var message by mutableStateOf(t(R.string.lock_enter_your_tools_pin))
    var error by mutableStateOf(false)
    var entered by mutableStateOf("")
    var pinLength by mutableIntStateOf(0)
    var shake by mutableIntStateOf(0)
    var busy by mutableStateOf(false)
}

private val White = Color.White
private val Red = Color(0xFFFF6B6B)

/** Drawn over the lock card: the Tools PIN pad (no dim). Empty while Samsung's fingerprint sheet is up. */
@Composable
fun CustomLockUi(
    state: CustomLockState,
    onDigit: (Char) -> Unit,
    onDelete: () -> Unit,
    onUseFingerprint: () -> Unit,
) {
    AnimatedVisibility(state.revealed, enter = fadeIn(tween(280)), exit = fadeOut(tween(150))) {
        AnimatedContent(
            targetState = state.pinMode,
            transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) },
            label = "lockMode",
        ) { pin ->
            // Fingerprint: Samsung's sheet is on screen over the bare card.
            if (pin) PinPanel(state, onDigit, onDelete, onUseFingerprint) else Box(Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun PinPanel(state: CustomLockState, onDigit: (Char) -> Unit, onDelete: () -> Unit, onUseFingerprint: () -> Unit) {
    Column(
        Modifier.fillMaxSize().navigationBarsPadding().padding(bottom = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.weight(1f))
        Text(
            state.message, fontFamily = SamsungFont, fontSize = 15.sp, fontWeight = FontWeight.Medium,
            color = if (state.error) Red else White.copy(alpha = 0.9f), textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp),
        )
        Box(Modifier.padding(top = 18.dp, bottom = 34.dp)) {
            PinDots(state.entered.length, state.pinLength, state.shake, White)
        }
        PinKeypad(
            color = White, onDigit = onDigit, onDelete = onDelete, enabled = !state.busy,
            leftKey = if (state.fingerprintAvailable) ({
                Box(
                    Modifier.size(64.dp).clip(CircleShape).clickable(onClick = onUseFingerprint),
                    contentAlignment = Alignment.Center,
                ) { Icon(OneUiIcons.Fingerprint, t(R.string.lock_use_fingerprint), tint = White, modifier = Modifier.size(30.dp)) }
            }) else null,
        )
    }
}

