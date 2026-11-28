package com.tools.app.applock

import com.tools.app.R
import com.tools.app.ui.t
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tools.app.ui.PillButton
import com.tools.app.ui.ToolPage
import com.tools.app.ui.theme.OneUi

/** Set or change the Tools PIN: enter, then confirm. */
@Composable
fun PinSetupScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val c = OneUi.colors
    var first by remember { mutableStateOf<String?>(null) }
    var entered by remember { mutableStateOf("") }
    var shake by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf(false) }
    val confirming = first != null

    fun save(pin: String) {
        PinStore.set(context, pin)
        Toast.makeText(context, t(R.string.lock_tools_pin_set), Toast.LENGTH_SHORT).show()
        onDone()
    }

    ToolPage(if (PinStore.isSet(context)) t(R.string.lock_change_tools_pin) else t(R.string.lock_set_tools_pin), onBack = onDone) {
        Column(Modifier.fillMaxWidth().weight(1f).padding(bottom = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.weight(1f))
            Text(
                when {
                    error -> t(R.string.lock_pins_didn_t_match_start_again)
                    confirming -> t(R.string.lock_confirm_your_pin)
                    else -> t(R.string.lock_enter_a_digit_pin, PinStore.MIN, PinStore.MAX)
                },
                fontSize = 20.sp, fontWeight = FontWeight.Bold, color = if (error) c.error else c.text, textAlign = TextAlign.Center,
            )
            Text(
                t(R.string.lock_used_when_your_fingerprint_isn_t_recog),
                fontSize = 14.sp, color = c.textSecondary, textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 40.dp, vertical = 8.dp),
            )
            Box(Modifier.padding(top = 18.dp, bottom = 26.dp)) {
                PinDots(entered.length, if (confirming) first!!.length else 0, shake, c.text)
            }
            PinKeypad(
                color = c.text,
                onDigit = { d ->
                    if (entered.length >= PinStore.MAX) return@PinKeypad
                    error = false
                    entered += d
                    val f = first
                    if (f != null && entered.length == f.length) {
                        if (entered == f) save(entered)
                        else { error = true; shake++; first = null; entered = "" }
                    }
                },
                onDelete = { entered = entered.dropLast(1) },
            )
            Box(Modifier.height(72.dp).padding(top = 14.dp), contentAlignment = Alignment.Center) {
                if (!confirming) PillButton(
                    t(R.string.lock_continue), enabled = entered.length >= PinStore.MIN, height = 50.dp,
                    modifier = Modifier.padding(horizontal = 24.dp).fillMaxWidth(),
                    onClick = { first = entered; entered = "" },
                )
            }
        }
    }
}
