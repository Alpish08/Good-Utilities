package com.tools.app.japanese

import android.graphics.Bitmap
import android.graphics.Rect
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import kotlinx.coroutines.tasks.await

class OcrElement(val text: String, val box: Rect)

class OcrLine(
    val text: String,
    val box: Rect,
    val vertical: Boolean,
    /** Index of the ML Kit block (≈ a speech bubble or paragraph) this line belongs to. */
    val block: Int,
    val elements: List<OcrElement>,
)

/** Lines in reading order. */
class OcrResult(val lines: List<OcrLine>, val vertical: Boolean) {
    fun blockLines(block: Int) = lines.filter { it.block == block }

    /** Text of every line in [block], joined the way Japanese is written (no spaces between kana/kanji). */
    fun blockText(block: Int) = join(blockLines(block).map { it.text })

    companion object {
        fun join(parts: List<String>): String = buildString {
            for (p in parts) {
                if (isNotEmpty() && last().code < 0x3000 && p.firstOrNull()?.let { it.code < 0x3000 } == true) append(' ')
                append(p)
            }
        }
    }
}

/** ML Kit's bundled on-device Japanese recognizer. Horizontal text works best; vertical columns are supported but less accurate. */
object Ocr {
    private val recognizer by lazy { TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build()) }

    /** [topInset]: height in px of the status bar in a screenshot; lines inside it are ignored. */
    suspend fun recognize(bitmap: Bitmap, topInset: Int = 0): OcrResult {
        val result = recognizer.process(InputImage.fromBitmap(bitmap, 0)).await()
        val blocks = ArrayList<List<OcrLine>>()
        result.textBlocks.forEachIndexed { bi, b ->
            val lines = b.lines.mapNotNull { l ->
                val box = l.boundingBox ?: return@mapNotNull null
                if (l.text.isBlank() || !Kana.hasJapanese(l.text)) return@mapNotNull null
                if (topInset > 0 && box.centerY() < topInset) return@mapNotNull null
                val vertical = box.height() > box.width() * 1.3f && l.text.length >= 2
                OcrLine(
                    l.text.trim(), box, vertical, bi,
                    l.elements.mapNotNull { e -> e.boundingBox?.let { OcrElement(e.text, it) } },
                )
            }
            if (lines.isNotEmpty()) blocks += lines
        }
        // Re-number blocks that were dropped.
        val numbered = blocks.mapIndexed { i, ls -> ls.map { OcrLine(it.text, it.box, it.vertical, i, it.elements) } }
        val all = numbered.flatten()
        val verticalChars = all.filter { it.vertical }.sumOf { it.text.length }
        val verticalPage = verticalChars > all.sumOf { it.text.length } / 2
        val ordered = if (verticalPage) {
            // Manga: columns read right to left, bubbles right to left then top to bottom.
            numbered.sortedWith(compareByDescending<List<OcrLine>> { b -> b.maxOf { it.box.right } }.thenBy { b -> b.minOf { it.box.top } })
                .flatMap { b -> if (b.count { it.vertical } * 2 > b.size) b.sortedByDescending { it.box.centerX() } else b.sortedBy { it.box.top } }
        } else numbered.flatten()
        return OcrResult(ordered, verticalPage)
    }
}
