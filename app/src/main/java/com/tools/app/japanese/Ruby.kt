package com.tools.app.japanese

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import com.tools.app.ui.theme.OneUi

/**
 * Ruby layout: each piece of kanji is a column with its small hiragana reading centred above it.
 * Plain kana pieces get an empty slot of the same height, so every baseline lines up.
 */
@Composable
fun RubyWord(
    segments: List<Seg>,
    size: TextUnit,
    modifier: Modifier = Modifier,
    color: Color = OneUi.colors.text,
    rubyColor: Color = OneUi.colors.textSecondary,
    showRuby: Boolean = true,
    weight: FontWeight = FontWeight.Medium,
) {
    val rubySize = (size.value * 0.46f).coerceAtLeast(9f).sp
    Row(modifier, verticalAlignment = Alignment.Bottom) {
        for (s in segments) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // Always laid out (even when blank) so words with and without furigana share a baseline.
                Text(
                    if (showRuby) s.ruby ?: " " else " ", fontSize = rubySize, lineHeight = (rubySize.value * 1.25f).sp,
                    color = rubyColor, maxLines = 1, softWrap = false, textAlign = TextAlign.Center,
                )
                Text(s.text, fontSize = size, lineHeight = (size.value * 1.3f).sp, color = color, fontWeight = weight, softWrap = false)
            }
        }
    }
}

/** A whole sentence with furigana, wrapping at word boundaries; optional romaji under each word. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RubySentence(
    words: List<Word>,
    size: TextUnit,
    romaji: Boolean,
    modifier: Modifier = Modifier,
    color: Color = OneUi.colors.text,
) {
    FlowRow(modifier, horizontalArrangement = Arrangement.Start, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        for (w in words) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                RubyWord(w.segments, size, color = if (w.isParticle) color.copy(alpha = 0.78f) else color)
                if (romaji) {
                    val r = if (w.isSymbol || w.pronunciation.isEmpty() || !Kana.hasJapanese(w.surface)) " " else Kana.toRomaji(romajiSource(w))
                    Text(r, fontSize = (size.value * 0.42f).coerceAtLeast(9f).sp, color = OneUi.colors.textSecondary, maxLines = 1, softWrap = false)
                }
            }
        }
    }
}

/** Particles are pronounced differently from how they are written (は → wa, を → o, へ → e). */
fun romajiSource(w: Word): String = when {
    w.pos.contains("particle") && w.surface == "は" -> "わ"
    w.pos.contains("particle") && w.surface == "を" -> "お"
    w.pos.contains("particle") && w.surface == "へ" -> "え"
    else -> w.pronunciation
}
