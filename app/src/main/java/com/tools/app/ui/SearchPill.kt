package com.tools.app.ui

import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material3.MaterialTheme
import com.tools.app.ui.theme.OneUiEasing
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tools.app.ui.theme.OneUi

/** My Files' search pill: #313131 with a hairline #3A3A3D edge (sampled from One UI 8.5). */
fun pillColor(dark: Boolean) = if (dark) Color(0xFF313131) else Color.White
private fun pillEdge(dark: Boolean) = if (dark) Color(0xFF3A3A3D) else Color(0x14000000)

/**
 * One UI 8.5 floating search pill, matched to My Files: 245 × 53 dp, "Search" in 18 sp bold grey, a mic on
 * the right. Tapping it stretches it to the full width for typing, and it rides up above the keyboard.
 * Closing the keyboard with nothing typed shrinks it back.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun SearchPill(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String = "Search",
    focus: FocusRequester = remember { FocusRequester() },
    onFocusChange: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val c = OneUi.colors
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    var focused by remember { mutableStateOf(false) }
    // The first back press only hides the keyboard (the field keeps focus). Treat that as leaving search.
    val imeVisible = WindowInsets.isImeVisible
    var hadIme by remember { mutableStateOf(false) }
    LaunchedEffect(imeVisible) {
        if (imeVisible) hadIme = true
        else if (hadIme) { hadIme = false; focusManager.clearFocus() }
    }
    val open = focused || query.isNotEmpty()
    // 0 = the short pill, 1 = stretched across the screen.
    val t by animateFloatAsState(if (open) 1f else 0f, tween(320, easing = OneUiEasing), label = "searchPill")
    // SamsungOne, same size and weight as My Files' "Search".
    val style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.5.sp, lineHeight = 21.sp, fontWeight = FontWeight.Bold, color = c.text)
    val hint = if (c.isDark) Color(0xFFA3A3A7) else Color(0xFF8A8A8F)

    val speech = remember {
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
    }
    val canSpeak = remember { speech.resolveActivity(context.packageManager) != null }
    val listen = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let(onQueryChange)
    }

    BoxWithConstraints(
        modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.ime.exclude(WindowInsets.navigationBars)),
        contentAlignment = Alignment.Center,
    ) {
        val short = 245.dp.coerceAtMost(maxWidth)
        Row(
            Modifier
                .offset(y = (-6).dp) // same height above the bottom edge as My Files
                .width(short + (maxWidth - short) * t)
                .height(53.dp)
                .shadow(if (c.isDark) 0.dp else 10.dp, CircleShape, ambientColor = Color(0x22000000), spotColor = Color(0x22000000))
                .clip(CircleShape)
                .background(pillColor(c.isDark))
                .border(0.6.dp, pillEdge(c.isDark), CircleShape)
                .clickable(interactionSource = null, indication = null) { focus.requestFocus() }
                .padding(start = 17.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(OneUiIcons.Search, null, tint = hint, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(6.dp))
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (query.isEmpty()) Text(placeholder, style = style.copy(color = hint), maxLines = 1)
                BasicTextField(
                    query, onQueryChange,
                    singleLine = true,
                    textStyle = style,
                    cursorBrush = SolidColor(c.accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus).onFocusChanged { focused = it.isFocused; onFocusChange(it.isFocused) },
                )
            }
            when {
                query.isNotEmpty() -> Box(Modifier.size(40.dp).clip(CircleShape).clickable { onQueryChange("") }, contentAlignment = Alignment.Center) {
                    Icon(OneUiIcons.Close, "Clear", tint = c.text, modifier = Modifier.size(22.dp))
                }
                canSpeak -> Box(Modifier.size(40.dp).clip(CircleShape).clickable { runCatching { listen.launch(speech) } }, contentAlignment = Alignment.Center) {
                    Icon(OneUiIcons.Mic, "Voice search", tint = c.text, modifier = Modifier.size(24.dp))
                }
                else -> Spacer(Modifier.width(16.dp))
            }
        }
    }
}
