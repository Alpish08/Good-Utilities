package com.tools.app.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Line icons drawn in One UI's style: 24dp grid, thin even strokes, round caps and soft corners.
 * Original drawings (not Samsung's files), so they tint like any other icon.
 */
object OneUiIcons {
    private fun icon(name: String, block: ImageVector.Builder.() -> Unit) =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply(block).build()

    private fun ImageVector.Builder.line(width: Float = 1.6f, block: PathBuilder.() -> Unit) = path(
        stroke = SolidColor(Color.Black), strokeLineWidth = width,
        strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round, pathBuilder = block,
    )

    private fun ImageVector.Builder.fill(evenOdd: Boolean = false, block: PathBuilder.() -> Unit) = path(
        fill = SolidColor(Color.Black),
        pathFillType = if (evenOdd) PathFillType.EvenOdd else PathFillType.NonZero, pathBuilder = block,
    )

    /** Filled shape with a thin round-joined outline so corners stay soft at 16-18 dp. */
    private fun ImageVector.Builder.solid(width: Float = 1f, evenOdd: Boolean = false, block: PathBuilder.() -> Unit) = path(
        fill = SolidColor(Color.Black), stroke = SolidColor(Color.Black), strokeLineWidth = width,
        strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
        pathFillType = if (evenOdd) PathFillType.EvenOdd else PathFillType.NonZero, pathBuilder = block,
    )

    private fun PathBuilder.circle(cx: Float, cy: Float, r: Float) {
        moveTo(cx - r, cy)
        arcTo(r, r, 0f, true, true, cx + r, cy)
        arcTo(r, r, 0f, true, true, cx - r, cy)
        close()
    }

    /** Settings: One UI's quick-panel gear, solid with six soft teeth and a round hole. */
    val Settings: ImageVector by lazy {
        icon("Settings") {
            // Drawn with a thin round-joined outline (solid) so the tooth corners come out rounded.
            solid(width = 1.2f, evenOdd = true) {
                val teeth = 6
                val outer = 9.0f; val root = 7.1f
                val step = 2 * PI / teeth
                for (i in 0 until teeth) {
                    val a = i * step - PI / 2
                    // valley → rise → flat top → fall (the tooth is wider at its base)
                    val pts = listOf(
                        root to a - step * 0.5, root to a - step * 0.30,
                        outer to a - step * 0.19, outer to a + step * 0.19,
                        root to a + step * 0.30,
                    )
                    pts.forEachIndexed { j, (r, ang) ->
                        val x = (12 + r * cos(ang)).toFloat(); val y = (12 + r * sin(ang)).toFloat()
                        if (i == 0 && j == 0) moveTo(x, y) else lineTo(x, y)
                    }
                }
                close()
                circle(12f, 12f, 4.3f)
            }
        }
    }

    /** Gallery / photo: rounded frame, hills and a sun. */
    val Gallery: ImageVector by lazy {
        icon("Gallery") {
            line {
                moveTo(7f, 3.5f); lineTo(17f, 3.5f)
                arcTo(3.5f, 3.5f, 0f, false, true, 20.5f, 7f); lineTo(20.5f, 17f)
                arcTo(3.5f, 3.5f, 0f, false, true, 17f, 20.5f); lineTo(7f, 20.5f)
                arcTo(3.5f, 3.5f, 0f, false, true, 3.5f, 17f); lineTo(3.5f, 7f)
                arcTo(3.5f, 3.5f, 0f, false, true, 7f, 3.5f); close()
            }
            line { moveTo(4f, 17.2f); lineTo(9f, 12.2f); lineTo(13.2f, 16.4f); lineTo(15.4f, 14.2f); lineTo(20f, 18.8f) }
            line { circle(15.6f, 8.6f, 1.7f) }
        }
    }

    /** "More options": three vertical dots. */
    val More: ImageVector by lazy {
        icon("More") {
            fill { circle(12f, 5.2f, 1.75f) }
            fill { circle(12f, 12f, 1.75f) }
            fill { circle(12f, 18.8f, 1.75f) }
        }
    }

    /** Opens in a browser: box with an arrow leaving its corner. */
    val OpenExternal: ImageVector by lazy {
        icon("OpenExternal") {
            line { moveTo(19.5f, 13.5f); lineTo(19.5f, 17f); arcTo(3f, 3f, 0f, false, true, 16.5f, 20f); lineTo(7f, 20f)
                arcTo(3f, 3f, 0f, false, true, 4f, 17f); lineTo(4f, 7.5f); arcTo(3f, 3f, 0f, false, true, 7f, 4.5f); lineTo(10.5f, 4.5f) }
            line { moveTo(14f, 4f); lineTo(20f, 4f); lineTo(20f, 10f) }
            line { moveTo(19.6f, 4.4f); lineTo(11.5f, 12.5f) }
        }
    }

    /** Music file: a beamed note. */
    val Music: ImageVector by lazy {
        icon("Music") {
            line { moveTo(9f, 17.5f); lineTo(9f, 5.8f); lineTo(19f, 3.8f); lineTo(19f, 15.5f) }
            line { moveTo(9f, 9.3f); lineTo(19f, 7.3f) }
            line { circle(6.6f, 17.5f, 2.4f) }
            line { circle(16.6f, 15.5f, 2.4f) }
        }
    }

    // ---- Generated line icons (24dp grid, 1.6dp strokes) ----

    val Search: ImageVector by lazy {
        icon("Search") {
            line(1.6f) { moveTo(3.9f, 10.5f); arcTo(6.6f, 6.6f, 0f, true, true, 17.1f, 10.5f); arcTo(6.6f, 6.6f, 0f, true, true, 3.9f, 10.5f); close() }
            line(1.6f) { moveTo(15.4f, 15.4f); lineTo(20f, 20f) }
        }
    }

    val Close: ImageVector by lazy {
        icon("Close") {
            line(1.6f) { moveTo(6f, 6f); lineTo(18f, 18f); moveTo(18f, 6f); lineTo(6f, 18f) }
        }
    }

    val Check: ImageVector by lazy {
        icon("Check") {
            line(1.6f) { moveTo(5f, 12.8f); lineTo(9.8f, 17.6f); lineTo(19f, 7.2f) }
        }
    }

    val CheckCircle: ImageVector by lazy {
        icon("CheckCircle") {
            line(1.6f) { moveTo(2.8f, 12f); arcTo(9.2f, 9.2f, 0f, true, true, 21.2f, 12f); arcTo(9.2f, 9.2f, 0f, true, true, 2.8f, 12f); close() }
            line(1.6f) { moveTo(7.9f, 12.4f); lineTo(10.8f, 15.3f); lineTo(16.3f, 9.2f) }
        }
    }

    val Add: ImageVector by lazy {
        icon("Add") {
            line(1.6f) { moveTo(12f, 5f); verticalLineTo(19f); moveTo(5f, 12f); horizontalLineTo(19f) }
        }
    }

    val Share: ImageVector by lazy {
        icon("Share") {
            line(1.6f) { moveTo(4f, 12f); arcTo(2.5f, 2.5f, 0f, true, true, 9f, 12f); arcTo(2.5f, 2.5f, 0f, true, true, 4f, 12f); close(); moveTo(15f, 6f); arcTo(2.5f, 2.5f, 0f, true, true, 20f, 6f); arcTo(2.5f, 2.5f, 0f, true, true, 15f, 6f); close(); moveTo(15f, 18f); arcTo(2.5f, 2.5f, 0f, true, true, 20f, 18f); arcTo(2.5f, 2.5f, 0f, true, true, 15f, 18f); close() }
            line(1.6f) { moveTo(8.7f, 10.8f); lineTo(15.3f, 7.2f); moveTo(8.7f, 13.2f); lineTo(15.3f, 16.8f) }
        }
    }

    val Download: ImageVector by lazy {
        icon("Download") {
            line(1.6f) { moveTo(4.5f, 14f); verticalLineTo(16.5f); arcTo(3f, 3f, 0f, false, false, 7.5f, 19.5f); horizontalLineTo(16.5f); arcTo(3f, 3f, 0f, false, false, 19.5f, 16.5f); verticalLineTo(14f) }
            line(1.6f) { moveTo(12f, 4f); verticalLineTo(15f) }
            line(1.6f) { moveTo(7.8f, 10.8f); lineTo(12f, 15f); lineTo(16.2f, 10.8f) }
        }
    }

    val Schedule: ImageVector by lazy {
        icon("Schedule") {
            line(1.6f) { moveTo(2.8f, 12f); arcTo(9.2f, 9.2f, 0f, true, true, 21.2f, 12f); arcTo(9.2f, 9.2f, 0f, true, true, 2.8f, 12f); close() }
            line(1.6f) { moveTo(12f, 6.8f); verticalLineTo(12f); lineTo(15.5f, 14.2f) }
        }
    }

    val PlayArrow: ImageVector by lazy {
        icon("PlayArrow") {
            line(1.6f) { moveTo(8.2f, 5.8f); verticalLineTo(18.2f); lineTo(18.6f, 12f); close() }
        }
    }

    val Pause: ImageVector by lazy {
        icon("Pause") {
            line(2.4f) { moveTo(8.6f, 6.2f); verticalLineTo(17.8f); moveTo(15.4f, 6.2f); verticalLineTo(17.8f) }
        }
    }

    val ChevronUp: ImageVector by lazy {
        icon("ChevronUp") {
            line(1.6f) { moveTo(6.5f, 14.5f); lineTo(12f, 9f); lineTo(17.5f, 14.5f) }
        }
    }

    val ChevronDown: ImageVector by lazy {
        icon("ChevronDown") {
            line(1.6f) { moveTo(6.5f, 9.5f); lineTo(12f, 15f); lineTo(17.5f, 9.5f) }
        }
    }

    val ArrowBack: ImageVector by lazy {
        icon("ArrowBack") {
            line(1.6f) { moveTo(14.5f, 4.8f); lineTo(7.3f, 12f); lineTo(14.5f, 19.2f) }
        }
    }

    val Backspace: ImageVector by lazy {
        icon("Backspace") {
            line(1.6f) { moveTo(9f, 5.5f); horizontalLineTo(18.5f); arcTo(2.5f, 2.5f, 0f, false, true, 21f, 8f); verticalLineTo(16f); arcTo(2.5f, 2.5f, 0f, false, true, 18.5f, 18.5f); horizontalLineTo(9f); lineTo(3.5f, 12f); close() }
            line(1.6f) { moveTo(12.9f, 9.9f); lineTo(17.1f, 14.1f); moveTo(17.1f, 9.9f); lineTo(12.9f, 14.1f) }
        }
    }

    val Fingerprint: ImageVector by lazy {
        icon("Fingerprint") {
            line(1.6f) { moveTo(4.6f, 15.5f); verticalLineTo(11.5f); arcTo(7.4f, 7.4f, 0f, false, true, 19.4f, 11.5f); verticalLineTo(12.5f) }
            line(1.6f) { moveTo(7.6f, 20f); curveTo(7.2f, 18.2f, 7.4f, 16f, 7.4f, 13f); verticalLineTo(11.5f); arcTo(4.6f, 4.6f, 0f, false, true, 16.6f, 11.5f); verticalLineTo(14f); curveTo(16.6f, 16.5f, 16.2f, 18.4f, 15.4f, 20f) }
            line(1.6f) { moveTo(10.2f, 15f); verticalLineTo(11.5f); arcTo(1.8f, 1.8f, 0f, false, true, 13.8f, 11.5f); verticalLineTo(14.5f); curveTo(13.8f, 16.5f, 13.3f, 18f, 12.6f, 19.4f) }
        }
    }

    val Lock: ImageVector by lazy {
        icon("Lock") {
            line(1.6f) { moveTo(8f, 10.5f); horizontalLineTo(16f); arcTo(3f, 3f, 0f, false, true, 19f, 13.5f); verticalLineTo(17.5f); arcTo(3f, 3f, 0f, false, true, 16f, 20.5f); horizontalLineTo(8f); arcTo(3f, 3f, 0f, false, true, 5f, 17.5f); verticalLineTo(13.5f); arcTo(3f, 3f, 0f, false, true, 8f, 10.5f); close() }
            line(1.6f) { moveTo(8.5f, 10.5f); verticalLineTo(8f); arcTo(3.5f, 3.5f, 0f, false, true, 15.5f, 8f); verticalLineTo(10.5f) }
            fill { moveTo(10.85f, 15.5f); arcTo(1.15f, 1.15f, 0f, true, true, 13.15f, 15.5f); arcTo(1.15f, 1.15f, 0f, true, true, 10.85f, 15.5f); close() }
        }
    }

    val ContentPaste: ImageVector by lazy {
        icon("ContentPaste") {
            line(1.6f) { moveTo(8.6f, 4.6f); horizontalLineTo(8f); arcTo(3.2f, 3.2f, 0f, false, false, 4.8f, 7.8f); verticalLineTo(17.6f); arcTo(3.2f, 3.2f, 0f, false, false, 8f, 20.8f); horizontalLineTo(16f); arcTo(3.2f, 3.2f, 0f, false, false, 19.2f, 17.6f); verticalLineTo(7.8f); arcTo(3.2f, 3.2f, 0f, false, false, 16f, 4.6f); horizontalLineTo(15.4f) }
            line(1.6f) { moveTo(10.1f, 2.9f); horizontalLineTo(13.9f); arcTo(1.5f, 1.5f, 0f, false, true, 15.4f, 4.4f); verticalLineTo(4.8f); arcTo(1.5f, 1.5f, 0f, false, true, 13.9f, 6.3f); horizontalLineTo(10.1f); arcTo(1.5f, 1.5f, 0f, false, true, 8.6f, 4.8f); verticalLineTo(4.4f); arcTo(1.5f, 1.5f, 0f, false, true, 10.1f, 2.9f); close() }
            line(1.6f) { moveTo(8.6f, 11.5f); horizontalLineTo(15.4f); moveTo(8.6f, 15.5f); horizontalLineTo(13f) }
        }
    }

    val ContentCut: ImageVector by lazy {
        icon("ContentCut") {
            line(1.6f) { moveTo(3.8f, 17.8f); arcTo(2.7f, 2.7f, 0f, true, true, 9.2f, 17.8f); arcTo(2.7f, 2.7f, 0f, true, true, 3.8f, 17.8f); close(); moveTo(14.8f, 17.8f); arcTo(2.7f, 2.7f, 0f, true, true, 20.2f, 17.8f); arcTo(2.7f, 2.7f, 0f, true, true, 14.8f, 17.8f); close() }
            line(1.6f) { moveTo(8.2f, 15.7f); lineTo(17.5f, 4f); moveTo(15.8f, 15.7f); lineTo(6.5f, 4f) }
        }
    }

    val CallMerge: ImageVector by lazy {
        icon("CallMerge") {
            line(1.6f) { moveTo(6f, 4.5f); verticalLineTo(6f); curveTo(6f, 10.5f, 12f, 10f, 12f, 14.5f); moveTo(18f, 4.5f); verticalLineTo(6f); curveTo(18f, 10.5f, 12f, 10f, 12f, 14.5f); moveTo(12f, 10.4f); verticalLineTo(20f) }
            line(1.6f) { moveTo(8.8f, 16.8f); lineTo(12f, 20f); lineTo(15.2f, 16.8f) }
        }
    }

    val Folder: ImageVector by lazy {
        icon("Folder") {
            line(1.6f) { moveTo(3.5f, 17f); verticalLineTo(7f); arcTo(2.5f, 2.5f, 0f, false, true, 6f, 4.5f); horizontalLineTo(9.6f); curveTo(10.3f, 4.5f, 10.9f, 4.8f, 11.3f, 5.3f); lineTo(12.4f, 6.7f); curveTo(12.8f, 7.2f, 13.4f, 7.5f, 14f, 7.5f); horizontalLineTo(18f); arcTo(2.5f, 2.5f, 0f, false, true, 20.5f, 10f); verticalLineTo(17f); arcTo(3f, 3f, 0f, false, true, 17.5f, 20f); horizontalLineTo(6.5f); arcTo(3f, 3f, 0f, false, true, 3.5f, 17f); close() }
        }
    }

    val ErrorOutline: ImageVector by lazy {
        icon("ErrorOutline") {
            line(1.6f) { moveTo(2.8f, 12f); arcTo(9.2f, 9.2f, 0f, true, true, 21.2f, 12f); arcTo(9.2f, 9.2f, 0f, true, true, 2.8f, 12f); close() }
            line(1.6f) { moveTo(12f, 7.2f); verticalLineTo(13f) }
            fill { moveTo(10.9f, 16.4f); arcTo(1.1f, 1.1f, 0f, true, true, 13.1f, 16.4f); arcTo(1.1f, 1.1f, 0f, true, true, 10.9f, 16.4f); close() }
        }
    }

    val Bolt: ImageVector by lazy {
        icon("Bolt") {
            line(1.6f) { moveTo(13f, 3.2f); lineTo(5.8f, 13.4f); horizontalLineTo(11.4f); lineTo(10.6f, 20.8f); lineTo(18.2f, 10.4f); horizontalLineTo(12.6f); close() }
        }
    }

    val Globe: ImageVector by lazy {
        icon("Globe") {
            line(1.6f) { moveTo(2.8f, 12f); arcTo(9.2f, 9.2f, 0f, true, true, 21.2f, 12f); arcTo(9.2f, 9.2f, 0f, true, true, 2.8f, 12f); close() }
            line(1.6f) { moveTo(2.8f, 12f); horizontalLineTo(21.2f) }
            line(1.6f) { moveTo(12f, 2.8f); curveTo(8.4f, 6f, 8.4f, 18f, 12f, 21.2f); moveTo(12f, 2.8f); curveTo(15.6f, 6f, 15.6f, 18f, 12f, 21.2f) }
        }
    }

    val MusicNote: ImageVector by lazy {
        icon("MusicNote") {
            line(1.6f) { moveTo(5.1f, 17.4f); arcTo(2.7f, 2.7f, 0f, true, true, 10.5f, 17.4f); arcTo(2.7f, 2.7f, 0f, true, true, 5.1f, 17.4f); close() }
            line(1.6f) { moveTo(10.5f, 17.2f); verticalLineTo(5f); curveTo(13f, 6.2f, 16.5f, 6.8f, 17.5f, 10f) }
        }
    }

    val QueueMusic: ImageVector by lazy {
        icon("QueueMusic") {
            line(1.6f) { moveTo(3.8f, 6.5f); horizontalLineTo(14f); moveTo(3.8f, 11f); horizontalLineTo(14f); moveTo(3.8f, 15.5f); horizontalLineTo(9.5f) }
            line(1.6f) { moveTo(13.6f, 17.6f); arcTo(2.4f, 2.4f, 0f, true, true, 18.4f, 17.6f); arcTo(2.4f, 2.4f, 0f, true, true, 13.6f, 17.6f); close() }
            line(1.6f) { moveTo(18.4f, 17.6f); verticalLineTo(7.8f); curveTo(19.5f, 8.8f, 20.4f, 9.6f, 20.5f, 11.4f) }
        }
    }

    /** Eyedropper: rounded squeeze bulb, a collar, then a thin glass tube to a fine tip (45°). */
    val Colorize: ImageVector by lazy {
        icon("Colorize") {
            line(1.6f) { moveTo(13.17f, 8.57f); lineTo(16.87f, 4.87f); arcTo(1.6f, 1.6f, 0f, false, true, 19.13f, 7.13f); lineTo(15.43f, 10.83f) }
            line(1.6f) { moveTo(11.9f, 8.9f); lineTo(15.1f, 12.1f) }
            line(1.6f) { moveTo(12.7f, 10.1f); lineTo(5.6f, 17.2f); lineTo(4.6f, 19.4f); lineTo(6.8f, 18.4f); lineTo(13.9f, 11.3f) }
        }
    }

    val BlurOn: ImageVector by lazy {
        icon("BlurOn") {
            fill { moveTo(10.65f, 12f); arcTo(1.35f, 1.35f, 0f, true, true, 13.35f, 12f); arcTo(1.35f, 1.35f, 0f, true, true, 10.65f, 12f); close(); moveTo(10.65f, 7f); arcTo(1.35f, 1.35f, 0f, true, true, 13.35f, 7f); arcTo(1.35f, 1.35f, 0f, true, true, 10.65f, 7f); close(); moveTo(10.65f, 17f); arcTo(1.35f, 1.35f, 0f, true, true, 13.35f, 17f); arcTo(1.35f, 1.35f, 0f, true, true, 10.65f, 17f); close(); moveTo(6.32f, 9.5f); arcTo(1.35f, 1.35f, 0f, true, true, 9.02f, 9.5f); arcTo(1.35f, 1.35f, 0f, true, true, 6.32f, 9.5f); close(); moveTo(6.32f, 14.5f); arcTo(1.35f, 1.35f, 0f, true, true, 9.02f, 14.5f); arcTo(1.35f, 1.35f, 0f, true, true, 6.32f, 14.5f); close(); moveTo(14.98f, 9.5f); arcTo(1.35f, 1.35f, 0f, true, true, 17.68f, 9.5f); arcTo(1.35f, 1.35f, 0f, true, true, 14.98f, 9.5f); close(); moveTo(14.98f, 14.5f); arcTo(1.35f, 1.35f, 0f, true, true, 17.68f, 14.5f); arcTo(1.35f, 1.35f, 0f, true, true, 14.98f, 14.5f); close() }
            fill { moveTo(11f, 3.6f); arcTo(1f, 1f, 0f, true, true, 13f, 3.6f); arcTo(1f, 1f, 0f, true, true, 11f, 3.6f); close(); moveTo(11f, 20.4f); arcTo(1f, 1f, 0f, true, true, 13f, 20.4f); arcTo(1f, 1f, 0f, true, true, 11f, 20.4f); close(); moveTo(3.7f, 7.8f); arcTo(1f, 1f, 0f, true, true, 5.7f, 7.8f); arcTo(1f, 1f, 0f, true, true, 3.7f, 7.8f); close(); moveTo(3.7f, 16.2f); arcTo(1f, 1f, 0f, true, true, 5.7f, 16.2f); arcTo(1f, 1f, 0f, true, true, 3.7f, 16.2f); close(); moveTo(18.3f, 7.8f); arcTo(1f, 1f, 0f, true, true, 20.3f, 7.8f); arcTo(1f, 1f, 0f, true, true, 18.3f, 7.8f); close(); moveTo(18.3f, 16.2f); arcTo(1f, 1f, 0f, true, true, 20.3f, 16.2f); arcTo(1f, 1f, 0f, true, true, 18.3f, 16.2f); close() }
        }
    }

    val AutoFixHigh: ImageVector by lazy {
        icon("AutoFixHigh") {
            line(1.6f) { moveTo(4.5f, 19.5f); lineTo(13.2f, 10.8f) }
            line(1.6f) { moveTo(17f, 2.8f); curveTo(17.3f, 5.32f, 19.1f, 6.7f, 21.2f, 7f); curveTo(19.1f, 7.3f, 17.3f, 8.68f, 17f, 11.2f); curveTo(16.7f, 8.68f, 14.9f, 7.3f, 12.8f, 7f); curveTo(14.9f, 6.7f, 16.7f, 5.32f, 17f, 2.8f); close() }
            line(1.6f) { moveTo(18f, 15.5f); verticalLineTo(19.5f); moveTo(16f, 17.5f); horizontalLineTo(20f) }
        }
    }

    val BatteryStd: ImageVector by lazy {
        icon("BatteryStd") {
            line(1.6f) { moveTo(5.8f, 7.5f); horizontalLineTo(16.2f); arcTo(2.8f, 2.8f, 0f, false, true, 19f, 10.3f); verticalLineTo(13.7f); arcTo(2.8f, 2.8f, 0f, false, true, 16.2f, 16.5f); horizontalLineTo(5.8f); arcTo(2.8f, 2.8f, 0f, false, true, 3f, 13.7f); verticalLineTo(10.3f); arcTo(2.8f, 2.8f, 0f, false, true, 5.8f, 7.5f); close() }
            line(1.6f) { moveTo(21.2f, 10.6f); verticalLineTo(13.4f) }
            fill { moveTo(6.6f, 9.8f); horizontalLineTo(13f); arcTo(1.2f, 1.2f, 0f, false, true, 14.2f, 11f); verticalLineTo(13f); arcTo(1.2f, 1.2f, 0f, false, true, 13f, 14.2f); horizontalLineTo(6.6f); arcTo(1.2f, 1.2f, 0f, false, true, 5.4f, 13f); verticalLineTo(11f); arcTo(1.2f, 1.2f, 0f, false, true, 6.6f, 9.8f); close() }
        }
    }

    val BatteryChargingFull: ImageVector by lazy {
        icon("BatteryChargingFull") {
            line(1.6f) { moveTo(5.8f, 7.5f); horizontalLineTo(16.2f); arcTo(2.8f, 2.8f, 0f, false, true, 19f, 10.3f); verticalLineTo(13.7f); arcTo(2.8f, 2.8f, 0f, false, true, 16.2f, 16.5f); horizontalLineTo(5.8f); arcTo(2.8f, 2.8f, 0f, false, true, 3f, 13.7f); verticalLineTo(10.3f); arcTo(2.8f, 2.8f, 0f, false, true, 5.8f, 7.5f); close() }
            line(1.6f) { moveTo(21.2f, 10.6f); verticalLineTo(13.4f) }
            solid(1f) { moveTo(12.2f, 8.2f); lineTo(8.4f, 12.6f); horizontalLineTo(10.9f); lineTo(9.8f, 15.9f); lineTo(13.6f, 11.4f); horizontalLineTo(11.1f); close() }
        }
    }

    val AppPackage: ImageVector by lazy {
        icon("AppPackage") {
            line(1.6f) { moveTo(12f, 3f); lineTo(20f, 7.5f); verticalLineTo(16.5f); lineTo(12f, 21f); lineTo(4f, 16.5f); verticalLineTo(7.5f); close() }
            line(1.6f) { moveTo(4f, 7.5f); lineTo(12f, 12f); lineTo(20f, 7.5f); moveTo(12f, 12f); verticalLineTo(21f) }
        }
    }

    val Info: ImageVector by lazy {
        icon("Info") {
            line(1.6f) { moveTo(2.8f, 12f); arcTo(9.2f, 9.2f, 0f, true, true, 21.2f, 12f); arcTo(9.2f, 9.2f, 0f, true, true, 2.8f, 12f); close() }
            line(1.6f) { moveTo(12f, 11f); verticalLineTo(16.6f) }
            fill { moveTo(10.9f, 7.8f); arcTo(1.1f, 1.1f, 0f, true, true, 13.1f, 7.8f); arcTo(1.1f, 1.1f, 0f, true, true, 10.9f, 7.8f); close() }
        }
    }

    val Refresh: ImageVector by lazy {
        icon("Refresh") {
            line(1.6f) { moveTo(20f, 12f); arcTo(8f, 8f, 0f, true, true, 17.66f, 6.34f) }
            line(1.6f) { moveTo(17.7f, 3f); verticalLineTo(6.5f); horizontalLineTo(14.2f) }
        }
    }

    val Image: ImageVector get() = Gallery

    // ---- Filled glyphs for coloured-circle list icons (draw ~22-24dp inside a 28-30dp circle) ----

    val MusicFilled: ImageVector by lazy {
        icon("MusicFilled") {
            solid(1f) { moveTo(5.2f, 17.2f); arcTo(3f, 3f, 0f, true, true, 11.2f, 17.2f); arcTo(3f, 3f, 0f, true, true, 5.2f, 17.2f); close() }
            solid(1f) { moveTo(10.7f, 4.5f); horizontalLineTo(11.9f); verticalLineTo(17.2f); horizontalLineTo(10.7f); close() }
            solid(1f) { moveTo(11.9f, 4.5f); curveTo(14f, 6.5f, 19f, 6.5f, 19.5f, 11.5f); curveTo(17.5f, 9.5f, 14.5f, 9.3f, 11.9f, 9.3f); close() }
        }
    }

    val PlaylistFilled: ImageVector by lazy {
        icon("PlaylistFilled") {
            solid(1f) { moveTo(4.8f, 5.8f); horizontalLineTo(13.2f); arcTo(0.8f, 0.8f, 0f, false, true, 14f, 6.6f); verticalLineTo(6.6f); arcTo(0.8f, 0.8f, 0f, false, true, 13.2f, 7.4f); horizontalLineTo(4.8f); arcTo(0.8f, 0.8f, 0f, false, true, 4f, 6.6f); verticalLineTo(6.6f); arcTo(0.8f, 0.8f, 0f, false, true, 4.8f, 5.8f); close() }
            solid(1f) { moveTo(4.8f, 10.8f); horizontalLineTo(13.2f); arcTo(0.8f, 0.8f, 0f, false, true, 14f, 11.6f); verticalLineTo(11.6f); arcTo(0.8f, 0.8f, 0f, false, true, 13.2f, 12.4f); horizontalLineTo(4.8f); arcTo(0.8f, 0.8f, 0f, false, true, 4f, 11.6f); verticalLineTo(11.6f); arcTo(0.8f, 0.8f, 0f, false, true, 4.8f, 10.8f); close() }
            solid(1f) { moveTo(4.8f, 15.8f); horizontalLineTo(9.2f); arcTo(0.8f, 0.8f, 0f, false, true, 10f, 16.6f); verticalLineTo(16.6f); arcTo(0.8f, 0.8f, 0f, false, true, 9.2f, 17.4f); horizontalLineTo(4.8f); arcTo(0.8f, 0.8f, 0f, false, true, 4f, 16.6f); verticalLineTo(16.6f); arcTo(0.8f, 0.8f, 0f, false, true, 4.8f, 15.8f); close() }
            solid(1f) { moveTo(13.4f, 17.6f); arcTo(2.6f, 2.6f, 0f, true, true, 18.6f, 17.6f); arcTo(2.6f, 2.6f, 0f, true, true, 13.4f, 17.6f); close() }
            solid(1f) { moveTo(17.8f, 6f); horizontalLineTo(19.2f); verticalLineTo(17.6f); horizontalLineTo(17.8f); close() }
            solid(1f) { moveTo(19.2f, 6f); curveTo(20.5f, 7f, 21f, 8f, 21f, 9.8f); curveTo(20f, 9.2f, 19.6f, 9f, 19.2f, 9f); close() }
        }
    }

    val CutFilled: ImageVector by lazy {
        icon("CutFilled") {
            line(2.2f) { moveTo(8.3f, 15.5f); lineTo(17.4f, 4.4f); moveTo(15.7f, 15.5f); lineTo(6.6f, 4.4f) }
            fill(true) { moveTo(3.1f, 17.8f); arcTo(3.4f, 3.4f, 0f, true, true, 9.9f, 17.8f); arcTo(3.4f, 3.4f, 0f, true, true, 3.1f, 17.8f); close(); moveTo(4.9f, 17.8f); arcTo(1.6f, 1.6f, 0f, true, true, 8.1f, 17.8f); arcTo(1.6f, 1.6f, 0f, true, true, 4.9f, 17.8f); close(); moveTo(14.1f, 17.8f); arcTo(3.4f, 3.4f, 0f, true, true, 20.9f, 17.8f); arcTo(3.4f, 3.4f, 0f, true, true, 14.1f, 17.8f); close(); moveTo(15.9f, 17.8f); arcTo(1.6f, 1.6f, 0f, true, true, 19.1f, 17.8f); arcTo(1.6f, 1.6f, 0f, true, true, 15.9f, 17.8f); close() }
        }
    }

    val ColorizeFilled: ImageVector by lazy {
        icon("ColorizeFilled") {
            solid(0.8f) { moveTo(16.95f, 4.79f); lineTo(19.21f, 7.05f); lineTo(15.96f, 10.3f); lineTo(13.7f, 8.04f); close() }
            solid(0.8f) { moveTo(12.28f, 7.19f); lineTo(16.81f, 11.72f); lineTo(15.82f, 12.71f); lineTo(11.29f, 8.18f); close() }
            solid(0.8f) { moveTo(12.14f, 9.6f); lineTo(14.4f, 11.86f); lineTo(10.3f, 15.96f); lineTo(6.48f, 18.36f); lineTo(5.64f, 17.52f); lineTo(8.04f, 13.7f); close() }
        }
    }

    val BlurFilled: ImageVector by lazy {
        icon("BlurFilled") {
            fill { moveTo(10.3f, 12f); arcTo(1.7f, 1.7f, 0f, true, true, 13.7f, 12f); arcTo(1.7f, 1.7f, 0f, true, true, 10.3f, 12f); close(); moveTo(10.3f, 7f); arcTo(1.7f, 1.7f, 0f, true, true, 13.7f, 7f); arcTo(1.7f, 1.7f, 0f, true, true, 10.3f, 7f); close(); moveTo(10.3f, 17f); arcTo(1.7f, 1.7f, 0f, true, true, 13.7f, 17f); arcTo(1.7f, 1.7f, 0f, true, true, 10.3f, 17f); close(); moveTo(5.97f, 9.5f); arcTo(1.7f, 1.7f, 0f, true, true, 9.37f, 9.5f); arcTo(1.7f, 1.7f, 0f, true, true, 5.97f, 9.5f); close(); moveTo(5.97f, 14.5f); arcTo(1.7f, 1.7f, 0f, true, true, 9.37f, 14.5f); arcTo(1.7f, 1.7f, 0f, true, true, 5.97f, 14.5f); close(); moveTo(14.63f, 9.5f); arcTo(1.7f, 1.7f, 0f, true, true, 18.03f, 9.5f); arcTo(1.7f, 1.7f, 0f, true, true, 14.63f, 9.5f); close(); moveTo(14.63f, 14.5f); arcTo(1.7f, 1.7f, 0f, true, true, 18.03f, 14.5f); arcTo(1.7f, 1.7f, 0f, true, true, 14.63f, 14.5f); close() }
            fill { moveTo(10.65f, 3.6f); arcTo(1.35f, 1.35f, 0f, true, true, 13.35f, 3.6f); arcTo(1.35f, 1.35f, 0f, true, true, 10.65f, 3.6f); close(); moveTo(10.65f, 20.4f); arcTo(1.35f, 1.35f, 0f, true, true, 13.35f, 20.4f); arcTo(1.35f, 1.35f, 0f, true, true, 10.65f, 20.4f); close(); moveTo(3.35f, 7.8f); arcTo(1.35f, 1.35f, 0f, true, true, 6.05f, 7.8f); arcTo(1.35f, 1.35f, 0f, true, true, 3.35f, 7.8f); close(); moveTo(3.35f, 16.2f); arcTo(1.35f, 1.35f, 0f, true, true, 6.05f, 16.2f); arcTo(1.35f, 1.35f, 0f, true, true, 3.35f, 16.2f); close(); moveTo(17.95f, 7.8f); arcTo(1.35f, 1.35f, 0f, true, true, 20.65f, 7.8f); arcTo(1.35f, 1.35f, 0f, true, true, 17.95f, 7.8f); close(); moveTo(17.95f, 16.2f); arcTo(1.35f, 1.35f, 0f, true, true, 20.65f, 16.2f); arcTo(1.35f, 1.35f, 0f, true, true, 17.95f, 16.2f); close() }
        }
    }

    val MagicFilled: ImageVector by lazy {
        icon("MagicFilled") {
            line(3f) { moveTo(5.2f, 18.8f); lineTo(12.6f, 11.4f) }
            solid(0.6f) { moveTo(16.2f, 3.4f); curveTo(16.5f, 6.04f, 18.4f, 7.5f, 20.6f, 7.8f); curveTo(18.4f, 8.1f, 16.5f, 9.56f, 16.2f, 12.2f); curveTo(15.9f, 9.56f, 14f, 8.1f, 11.8f, 7.8f); curveTo(14f, 7.5f, 15.9f, 6.04f, 16.2f, 3.4f); close() }
            fill { moveTo(18f, 15.1f); curveTo(18.3f, 16.6f, 19.25f, 17.3f, 20.5f, 17.6f); curveTo(19.25f, 17.9f, 18.3f, 18.6f, 18f, 20.1f); curveTo(17.7f, 18.6f, 16.75f, 17.9f, 15.5f, 17.6f); curveTo(16.75f, 17.3f, 17.7f, 16.6f, 18f, 15.1f); close() }
        }
    }

    val LockFilled: ImageVector by lazy {
        icon("LockFilled") {
            line(2.2f) { moveTo(8f, 10f); verticalLineTo(7.6f); arcTo(4f, 4f, 0f, false, true, 16f, 7.6f); verticalLineTo(10f) }
            fill(true) { moveTo(7.4f, 10.2f); horizontalLineTo(16.6f); arcTo(2.8f, 2.8f, 0f, false, true, 19.4f, 13f); verticalLineTo(17.6f); arcTo(2.8f, 2.8f, 0f, false, true, 16.6f, 20.4f); horizontalLineTo(7.4f); arcTo(2.8f, 2.8f, 0f, false, true, 4.6f, 17.6f); verticalLineTo(13f); arcTo(2.8f, 2.8f, 0f, false, true, 7.4f, 10.2f); close(); moveTo(10.2f, 14.3f); arcTo(1.8f, 1.8f, 0f, true, true, 13.8f, 14.3f); arcTo(1.8f, 1.8f, 0f, true, true, 10.2f, 14.3f); close(); moveTo(11.1f, 14.8f); horizontalLineTo(12.9f); verticalLineTo(17.8f); horizontalLineTo(11.1f); close() }
        }
    }

    val GlobeFilled: ImageVector by lazy {
        icon("GlobeFilled") {
            line(2f) { moveTo(3.2f, 12f); arcTo(8.8f, 8.8f, 0f, true, true, 20.8f, 12f); arcTo(8.8f, 8.8f, 0f, true, true, 3.2f, 12f); close() }
            line(1.8f) { moveTo(3.2f, 12f); horizontalLineTo(20.8f) }
            line(1.8f) { moveTo(12f, 3.2f); curveTo(8.6f, 6.4f, 8.6f, 17.6f, 12f, 20.8f); moveTo(12f, 3.2f); curveTo(15.4f, 6.4f, 15.4f, 17.6f, 12f, 20.8f) }
        }
    }

    val BatteryChargingFilled: ImageVector by lazy {
        icon("BatteryChargingFilled") {
            fill(true) { moveTo(5.6f, 6.6f); horizontalLineTo(16.2f); arcTo(3.2f, 3.2f, 0f, false, true, 19.4f, 9.8f); verticalLineTo(14.2f); arcTo(3.2f, 3.2f, 0f, false, true, 16.2f, 17.4f); horizontalLineTo(5.6f); arcTo(3.2f, 3.2f, 0f, false, true, 2.4f, 14.2f); verticalLineTo(9.8f); arcTo(3.2f, 3.2f, 0f, false, true, 5.6f, 6.6f); close(); moveTo(11.6f, 8.2f); lineTo(6.8f, 12.9f); horizontalLineTo(9.9f); lineTo(8.8f, 15.9f); lineTo(13.8f, 11.1f); horizontalLineTo(10.7f); close() }
            fill { moveTo(20.9f, 10f); horizontalLineTo(20.9f); arcTo(0.9f, 0.9f, 0f, false, true, 21.8f, 10.9f); verticalLineTo(13.1f); arcTo(0.9f, 0.9f, 0f, false, true, 20.9f, 14f); horizontalLineTo(20.9f); arcTo(0.9f, 0.9f, 0f, false, true, 20f, 13.1f); verticalLineTo(10.9f); arcTo(0.9f, 0.9f, 0f, false, true, 20.9f, 10f); close() }
        }
    }

    val BatteryFilled: ImageVector by lazy {
        icon("BatteryFilled") {
            fill(true) { moveTo(5.6f, 6.6f); horizontalLineTo(16.2f); arcTo(3.2f, 3.2f, 0f, false, true, 19.4f, 9.8f); verticalLineTo(14.2f); arcTo(3.2f, 3.2f, 0f, false, true, 16.2f, 17.4f); horizontalLineTo(5.6f); arcTo(3.2f, 3.2f, 0f, false, true, 2.4f, 14.2f); verticalLineTo(9.8f); arcTo(3.2f, 3.2f, 0f, false, true, 5.6f, 6.6f); close(); moveTo(6.2f, 8.6f); horizontalLineTo(15.6f); arcTo(1.8f, 1.8f, 0f, false, true, 17.4f, 10.4f); verticalLineTo(14.4f); arcTo(1.8f, 1.8f, 0f, false, true, 15.6f, 16.2f); horizontalLineTo(6.2f); arcTo(1.8f, 1.8f, 0f, false, true, 4.4f, 14.4f); verticalLineTo(10.4f); arcTo(1.8f, 1.8f, 0f, false, true, 6.2f, 8.6f); close() }
            fill { moveTo(7.1f, 10.2f); horizontalLineTo(11.9f); arcTo(0.9f, 0.9f, 0f, false, true, 12.8f, 11.1f); verticalLineTo(12.9f); arcTo(0.9f, 0.9f, 0f, false, true, 11.9f, 13.8f); horizontalLineTo(7.1f); arcTo(0.9f, 0.9f, 0f, false, true, 6.2f, 12.9f); verticalLineTo(11.1f); arcTo(0.9f, 0.9f, 0f, false, true, 7.1f, 10.2f); close() }
            fill { moveTo(20.9f, 10f); horizontalLineTo(20.9f); arcTo(0.9f, 0.9f, 0f, false, true, 21.8f, 10.9f); verticalLineTo(13.1f); arcTo(0.9f, 0.9f, 0f, false, true, 20.9f, 14f); horizontalLineTo(20.9f); arcTo(0.9f, 0.9f, 0f, false, true, 20f, 13.1f); verticalLineTo(10.9f); arcTo(0.9f, 0.9f, 0f, false, true, 20.9f, 10f); close() }
        }
    }

    val PackageFilled: ImageVector by lazy {
        icon("PackageFilled") {
            solid(1f) { moveTo(12f, 3.6f); lineTo(19.4f, 7.7f); lineTo(12f, 11.7f); lineTo(4.6f, 7.7f); close() }
            solid(1f) { moveTo(4.5f, 9.2f); lineTo(11.5f, 13f); verticalLineTo(20.2f); lineTo(4.5f, 16.4f); close() }
            solid(1f) { moveTo(19.5f, 9.2f); lineTo(12.5f, 13f); verticalLineTo(20.2f); lineTo(19.5f, 16.4f); close() }
        }
    }
}
