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
    /** One UI's gear: six rounded teeth with curved valleys between them. */
    private fun PathBuilder.gear(outer: Float = 9.6f, root: Float = 6.7f, topDeg: Float = 12f, baseDeg: Float = 20f) {
        fun pt(deg: Double, r: Float) = Pair(12f + r * cos(Math.toRadians(deg)).toFloat(), 12f + r * sin(Math.toRadians(deg)).toFloat())
        for (i in 0 until 6) {
            val a = i * 60.0 - 90.0
            val b0 = pt(a - baseDeg, root); val o0 = pt(a - topDeg, outer); val o1 = pt(a + topDeg, outer); val b1 = pt(a + baseDeg, root)
            if (i == 0) moveTo(b0.first, b0.second) else lineTo(b0.first, b0.second)
            lineTo(o0.first, o0.second); lineTo(o1.first, o1.second); lineTo(b1.first, b1.second)
            val nb = pt(a + 60.0 - baseDeg, root)
            arcTo(root, root, 0f, false, true, nb.first, nb.second)
        }
        close()
    }

    val Settings: ImageVector by lazy {
        icon("Settings") {
            solid(width = 1.4f, evenOdd = true) { gear(); circle(12f, 12f, 3.4f) }
        }
    }

    /** Outline gear, with a ring in the middle. */
    val SettingsOutline: ImageVector by lazy {
        icon("SettingsOutline") {
            line(1.4f) { gear(9.5f) }
            line(1.4f) { circle(12f, 12f, 2.7f) }
        }
    }

    /** Copy: two overlapping rounded sheets. */
    val Copy: ImageVector by lazy {
        icon("Copy") {
            line(1.8f) { roundRect(8.5f, 8.5f, 19.5f, 19.5f, 3f) }
            line(1.8f) { moveTo(15.5f, 8.5f); verticalLineTo(7f); arcTo(3f, 3f, 0f, false, false, 12.5f, 4f); horizontalLineTo(7f); arcTo(3f, 3f, 0f, false, false, 4f, 7f); verticalLineTo(12.5f); arcTo(3f, 3f, 0f, false, false, 7f, 15.5f); horizontalLineTo(8.5f) }
        }
    }

    /** Gallery / photo: rounded frame, hills and a sun. */
    /** Same picture as the Images tab. */
    val Gallery: ImageVector get() = TabImages

    /** "More options": three vertical dots. */
    val More: ImageVector by lazy {
        icon("More") {
            fill { circle(12f, 5f, 1.9f) }
            fill { circle(12f, 12f, 1.9f) }
            fill { circle(12f, 19f, 1.9f) }
        }
    }

    /** Opens in a browser: box with an arrow leaving its corner. */
    val OpenExternal: ImageVector by lazy {
        icon("OpenExternal") {
            line(1.8f) { moveTo(19.5f, 13.5f); lineTo(19.5f, 17f); arcTo(3f, 3f, 0f, false, true, 16.5f, 20f); lineTo(7f, 20f); arcTo(3f, 3f, 0f, false, true, 4f, 17f); lineTo(4f, 7.5f); arcTo(3f, 3f, 0f, false, true, 7f, 4.5f); lineTo(10.5f, 4.5f) }
            line(1.8f) { moveTo(14f, 4f); lineTo(20f, 4f); lineTo(20f, 10f) }
            line(1.8f) { moveTo(19.6f, 4.4f); lineTo(11.5f, 12.5f) }
        }
    }

    /** Music file: a beamed note. */
    val Music: ImageVector by lazy {
        icon("Music") {
            line(1.8f) { moveTo(9f, 17.5f); lineTo(9f, 5.8f); lineTo(19f, 3.8f); lineTo(19f, 15.5f) }
            line(1.8f) { moveTo(9f, 9.3f); lineTo(19f, 7.3f) }
            line(1.8f) { circle(6.6f, 17.5f, 2.4f) }
            line(1.8f) { circle(16.6f, 15.5f, 2.4f) }
        }
    }

    // ---- Generated line icons (24dp grid, 1.6dp strokes) ----

    val Search: ImageVector by lazy {
        icon("Search") {
            line(1.8f) { circle(10.5f, 10.5f, 6.5f) }
            line(1.8f) { moveTo(15.4f, 15.4f); lineTo(20f, 20f) }
        }
    }

    // ---- Home tabs: outline (unselected) and filled (selected), One UI style ----

    private fun PathBuilder.roundRect(l: Float, t: Float, r: Float, b: Float, rad: Float) {
        moveTo(l + rad, t); lineTo(r - rad, t); arcTo(rad, rad, 0f, false, true, r, t + rad)
        lineTo(r, b - rad); arcTo(rad, rad, 0f, false, true, r - rad, b)
        lineTo(l + rad, b); arcTo(rad, rad, 0f, false, true, l, b - rad)
        lineTo(l, t + rad); arcTo(rad, rad, 0f, false, true, l + rad, t); close()
    }

    private fun PathBuilder.folderShape() {
        moveTo(3.2f, 6.9f); arcTo(2.6f, 2.6f, 0f, false, true, 5.8f, 4.3f); lineTo(9.1f, 4.3f)
        arcTo(1.8f, 1.8f, 0f, false, true, 10.4f, 4.9f); lineTo(12.1f, 6.8f); lineTo(18.2f, 6.8f)
        arcTo(2.6f, 2.6f, 0f, false, true, 20.8f, 9.4f); lineTo(20.8f, 17.1f)
        arcTo(2.6f, 2.6f, 0f, false, true, 18.2f, 19.7f); lineTo(5.8f, 19.7f)
        arcTo(2.6f, 2.6f, 0f, false, true, 3.2f, 17.1f); close()
    }

    // Shapes after One UI 8.5's system icons: 1.8 strokes, generous corner radii, solid "on" variants.
    private fun PathBuilder.noteFlag() { moveTo(11.7f, 16.6f); lineTo(11.7f, 4.4f); curveTo(14.6f, 4.6f, 16.6f, 6.4f, 16.6f, 9.3f) }

    val TabMedia: ImageVector by lazy {
        icon("TabMedia") {
            line(1.62f) { circle(8.6f, 16.9f, 3.1f) }
            line(1.62f) { noteFlag() }
        }
    }
    val TabMediaOn: ImageVector by lazy {
        icon("TabMediaOn") {
            fill { circle(8.6f, 16.9f, 4.0f) }
            line(1.62f) { noteFlag() }
        }
    }
    val TabFiles: ImageVector by lazy { icon("TabFiles") { line(1.62f) { folderShape() } } }
    val TabFilesOn: ImageVector by lazy { icon("TabFilesOn") { solid(1.62f) { folderShape() } } }
    // Picture, traced from One UI's gallery icon: frame, sun, two peaks.
    val TabImages: ImageVector by lazy {
        icon("TabImages") {
            line(1.62f) { roundRect(3.74f, 3.74f, 20.26f, 20.26f, 2.99f) }
            line(1.62f) { circle(8.58f, 8.72f, 1.89f) }
            line(1.62f) { moveTo(3.81f, 16.59f); lineTo(9.06f, 13.62f); lineTo(11.82f, 15.42f); lineTo(16.23f, 11.82f); lineTo(20.01f, 15.24f) }
        }
    }
    val TabImagesOn: ImageVector by lazy {
        icon("TabImagesOn") {
            fill(evenOdd = true) {
                roundRect(3.72f, 3.72f, 20.28f, 20.28f, 3.91f)
                circle(8.58f, 8.76f, 1.44f)
                moveTo(5.18f, 15.55f); lineTo(8.91f, 12.96f); lineTo(11.50f, 14.75f); lineTo(15.72f, 10.79f); lineTo(18.48f, 13.29f)
                lineTo(18.48f, 16.86f); arcTo(1.30f, 1.30f, 0f, false, true, 17.18f, 18.16f); lineTo(6.48f, 18.16f)
                arcTo(1.30f, 1.30f, 0f, false, true, 5.18f, 16.86f); close()
            }
        }
    }
    val TabDevice: ImageVector by lazy {
        icon("TabDevice") {
            line(1.62f) { roundRect(6.6f, 3.4f, 17.4f, 20.6f, 3f) }
            line(1.62f) { moveTo(10.8f, 17.5f); lineTo(13.2f, 17.5f) }
        }
    }
    val TabDeviceOn: ImageVector by lazy {
        icon("TabDeviceOn") {
            fill(evenOdd = true) { roundRect(5.9f, 2.7f, 18.1f, 21.3f, 3.6f); roundRect(10.1f, 16.8f, 13.9f, 18.3f, 0.75f) }
        }
    }
    private fun PathBuilder.diamond(cx: Float, cy: Float, r: Float) {
        moveTo(cx, cy - r); lineTo(cx + r, cy); lineTo(cx, cy + r); lineTo(cx - r, cy); close()
    }
    // Shapes, traced from One UI's icon: circle, rounded square, triangle, diamond.
    private fun PathBuilder.shapeTriangle() { moveTo(6.87f, 14.1f); lineTo(10.4f, 20.4f); lineTo(3.35f, 20.4f); close() }
    val TabMisc: ImageVector by lazy {
        icon("TabMisc") {
            line(1.62f) { circle(6.87f, 6.69f, 3.88f) }
            line(1.62f) { roundRect(13.27f, 3.0f, 20.47f, 10.2f, 1.7f) }
            line(1.62f) { shapeTriangle() }
            line(1.62f) { diamond(16.96f, 17.04f, 3.7f) }
        }
    }
    val TabMiscOn: ImageVector by lazy {
        icon("TabMiscOn") {
            solid(1.62f) { circle(6.87f, 6.69f, 3.88f) }
            solid(1.62f) { roundRect(13.27f, 3.0f, 20.47f, 10.2f, 1.7f) }
            solid(1.62f) { shapeTriangle() }
            solid(1.62f) { diamond(16.96f, 17.04f, 3.7f) }
        }
    }

    /** One UI's mic (My Files search): solid capsule, cradle, stem and a base line. */
    val Mic: ImageVector by lazy {
        icon("Mic") {
            fill { roundRect(9.2f, 3f, 14.8f, 13.4f, 2.8f) }
            line(1.8f) { moveTo(5.8f, 11f); arcTo(6.2f, 6.2f, 0f, false, false, 18.2f, 11f) }
            line(1.8f) { moveTo(12f, 17.2f); verticalLineTo(20.5f) }
        }
    }

    val Close: ImageVector by lazy {
        icon("Close") {
            line(1.8f) { moveTo(6.5f, 6.5f); lineTo(17.5f, 17.5f); moveTo(17.5f, 6.5f); lineTo(6.5f, 17.5f) }
        }
    }

    val Check: ImageVector by lazy {
        icon("Check") {
            line(1.8f) { moveTo(5f, 12.5f); lineTo(10f, 17.5f); lineTo(19f, 7f) }
        }
    }

    val CheckCircle: ImageVector by lazy {
        icon("CheckCircle") {
            line(1.8f) { circle(12f, 12f, 9f) }
            line(1.8f) { moveTo(7.9f, 12.4f); lineTo(10.8f, 15.3f); lineTo(16.3f, 9.2f) }
        }
    }

    val Add: ImageVector by lazy {
        icon("Add") {
            line(1.8f) { moveTo(12f, 5f); verticalLineTo(19f); moveTo(5f, 12f); horizontalLineTo(19f) }
        }
    }

    val Share: ImageVector by lazy {
        icon("Share") {
            fill { circle(6.5f, 12f, 2.4f) }
            fill { circle(17.5f, 6f, 2.4f) }
            fill { circle(17.5f, 18f, 2.4f) }
            line(1.8f) { moveTo(6.5f, 12f); lineTo(17.5f, 6f); moveTo(6.5f, 12f); lineTo(17.5f, 18f) }
        }
    }

    val Download: ImageVector by lazy {
        icon("Download") {
            line(1.8f) { moveTo(4.5f, 14f); verticalLineTo(16.5f); arcTo(3f, 3f, 0f, false, false, 7.5f, 19.5f); horizontalLineTo(16.5f); arcTo(3f, 3f, 0f, false, false, 19.5f, 16.5f); verticalLineTo(14f) }
            line(1.8f) { moveTo(12f, 4f); verticalLineTo(15f) }
            line(1.8f) { moveTo(7.8f, 10.8f); lineTo(12f, 15f); lineTo(16.2f, 10.8f) }
        }
    }

    val Schedule: ImageVector by lazy {
        icon("Schedule") {
            line(1.8f) { circle(12f, 12f, 9f) }
            line(1.8f) { moveTo(12f, 6.8f); verticalLineTo(12f); lineTo(15.5f, 14.2f) }
        }
    }

    val PlayArrow: ImageVector by lazy {
        icon("PlayArrow") {
            solid(1.8f) { moveTo(8.2f, 6f); verticalLineTo(18f); lineTo(18.4f, 12f); close() }
        }
    }

    val Pause: ImageVector by lazy {
        icon("Pause") {
            line(3f) { moveTo(8.6f, 6.8f); verticalLineTo(17.2f); moveTo(15.4f, 6.8f); verticalLineTo(17.2f) }
        }
    }

    val ChevronUp: ImageVector by lazy {
        icon("ChevronUp") {
            line(1.8f) { moveTo(6f, 14.5f); lineTo(12f, 8.5f); lineTo(18f, 14.5f) }
        }
    }

    val ChevronDown: ImageVector by lazy {
        icon("ChevronDown") {
            line(1.8f) { moveTo(6f, 9.5f); lineTo(12f, 15.5f); lineTo(18f, 9.5f) }
        }
    }

    val ArrowBack: ImageVector by lazy {
        icon("ArrowBack") {
            line(1.8f) { moveTo(15f, 5f); lineTo(8f, 12f); lineTo(15f, 19f) }
        }
    }

    val Backspace: ImageVector by lazy {
        icon("Backspace") {
            line(1.8f) { moveTo(9f, 5.5f); horizontalLineTo(18.5f); arcTo(2.5f, 2.5f, 0f, false, true, 21f, 8f); verticalLineTo(16f); arcTo(2.5f, 2.5f, 0f, false, true, 18.5f, 18.5f); horizontalLineTo(9f); lineTo(3.5f, 12f); close() }
            line(1.8f) { moveTo(12.9f, 9.9f); lineTo(17.1f, 14.1f); moveTo(17.1f, 9.9f); lineTo(12.9f, 14.1f) }
        }
    }

    /** One UI's fingerprint, traced from Samsung's icon: outer loop curling into the middle, three inner ridges. */
    val Fingerprint: ImageVector by lazy {
        icon("Fingerprint") {
            line(1.0f) { moveTo(2.74f, 17.03f); curveTo(2.69f, 16.62f, 2.47f, 15.36f, 2.40f, 14.57f); curveTo(2.33f, 13.78f, 2.32f, 13.00f, 2.34f, 12.29f); curveTo(2.36f, 11.57f, 2.41f, 10.95f, 2.51f, 10.29f); curveTo(2.62f, 9.62f, 2.76f, 8.90f, 2.97f, 8.29f); curveTo(3.18f, 7.67f, 3.43f, 7.14f, 3.77f, 6.57f); curveTo(4.11f, 6.00f, 4.56f, 5.35f, 5.03f, 4.86f); curveTo(5.50f, 4.36f, 6.03f, 3.95f, 6.57f, 3.60f); curveTo(7.11f, 3.25f, 7.67f, 2.95f, 8.29f, 2.74f); curveTo(8.90f, 2.53f, 9.62f, 2.41f, 10.29f, 2.34f); curveTo(10.95f, 2.28f, 11.62f, 2.27f, 12.29f, 2.34f); curveTo(12.95f, 2.42f, 13.67f, 2.59f, 14.29f, 2.80f); curveTo(14.90f, 3.01f, 15.43f, 3.28f, 16.00f, 3.60f); curveTo(16.57f, 3.92f, 17.19f, 4.30f, 17.71f, 4.74f); curveTo(18.24f, 5.19f, 18.74f, 5.79f, 19.14f, 6.29f); curveTo(19.54f, 6.78f, 19.82f, 7.19f, 20.11f, 7.71f); curveTo(20.41f, 8.24f, 20.70f, 8.76f, 20.91f, 9.43f); curveTo(21.13f, 10.10f, 21.36f, 10.95f, 21.43f, 11.71f); curveTo(21.50f, 12.48f, 21.46f, 13.35f, 21.31f, 14.00f); curveTo(21.17f, 14.65f, 20.93f, 15.20f, 20.57f, 15.60f); curveTo(20.21f, 16.00f, 19.67f, 16.27f, 19.14f, 16.40f); curveTo(18.62f, 16.53f, 17.90f, 16.55f, 17.43f, 16.40f); curveTo(16.96f, 16.25f, 16.62f, 15.89f, 16.34f, 15.49f); curveTo(16.07f, 15.09f, 15.90f, 14.53f, 15.77f, 14.00f); curveTo(15.64f, 13.47f, 15.62f, 12.81f, 15.54f, 12.29f); curveTo(15.47f, 11.76f, 15.48f, 11.28f, 15.31f, 10.86f); curveTo(15.15f, 10.44f, 14.89f, 10.07f, 14.57f, 9.77f); curveTo(14.26f, 9.48f, 13.88f, 9.24f, 13.43f, 9.09f); curveTo(12.98f, 8.93f, 12.39f, 8.84f, 11.89f, 8.86f); curveTo(11.38f, 8.88f, 10.80f, 9.01f, 10.40f, 9.20f); curveTo(10.00f, 9.39f, 9.71f, 9.63f, 9.49f, 10.00f); curveTo(9.26f, 10.37f, 9.12f, 10.86f, 9.03f, 11.43f); curveTo(8.93f, 12.00f, 8.90f, 12.76f, 8.91f, 13.43f); curveTo(8.92f, 14.10f, 8.99f, 14.81f, 9.09f, 15.43f); curveTo(9.18f, 16.05f, 9.30f, 16.62f, 9.49f, 17.14f); curveTo(9.67f, 17.67f, 9.90f, 18.11f, 10.17f, 18.57f); curveTo(10.45f, 19.03f, 10.79f, 19.49f, 11.14f, 19.89f); curveTo(11.50f, 20.29f, 11.86f, 20.64f, 12.29f, 20.97f); curveTo(12.71f, 21.30f, 13.48f, 21.73f, 13.71f, 21.89f) }
            line(1.0f) { moveTo(6.63f, 8.40f); curveTo(6.72f, 8.23f, 6.95f, 7.70f, 7.20f, 7.37f); curveTo(7.45f, 7.05f, 7.74f, 6.71f, 8.11f, 6.46f); curveTo(8.49f, 6.20f, 8.92f, 5.97f, 9.43f, 5.83f); curveTo(9.93f, 5.69f, 10.57f, 5.62f, 11.14f, 5.60f); curveTo(11.71f, 5.58f, 12.29f, 5.60f, 12.86f, 5.71f); curveTo(13.43f, 5.83f, 14.05f, 6.05f, 14.57f, 6.29f); curveTo(15.10f, 6.52f, 15.59f, 6.81f, 16.00f, 7.14f); curveTo(16.41f, 7.48f, 16.74f, 7.86f, 17.03f, 8.29f); curveTo(17.31f, 8.71f, 17.52f, 9.19f, 17.71f, 9.71f); curveTo(17.90f, 10.24f, 18.06f, 10.90f, 18.17f, 11.43f); curveTo(18.29f, 11.95f, 18.36f, 12.41f, 18.40f, 12.86f); curveTo(18.44f, 13.30f, 18.40f, 13.90f, 18.40f, 14.11f) }
            line(1.0f) { moveTo(5.49f, 10.69f); curveTo(5.48f, 10.95f, 5.44f, 11.73f, 5.43f, 12.29f); curveTo(5.42f, 12.84f, 5.41f, 13.43f, 5.43f, 14.00f); curveTo(5.45f, 14.57f, 5.47f, 15.19f, 5.54f, 15.71f); curveTo(5.62f, 16.24f, 5.74f, 16.67f, 5.89f, 17.14f); curveTo(6.03f, 17.62f, 6.20f, 18.10f, 6.40f, 18.57f); curveTo(6.60f, 19.04f, 6.84f, 19.51f, 7.09f, 19.94f); curveTo(7.33f, 20.37f, 7.75f, 20.94f, 7.89f, 21.14f) }
            line(1.0f) { moveTo(11.77f, 12.06f); curveTo(11.78f, 12.33f, 11.76f, 13.15f, 11.83f, 13.71f); curveTo(11.90f, 14.28f, 12.01f, 14.90f, 12.17f, 15.43f); curveTo(12.33f, 15.95f, 12.51f, 16.40f, 12.80f, 16.86f); curveTo(13.09f, 17.31f, 13.45f, 17.81f, 13.89f, 18.17f); curveTo(14.32f, 18.53f, 14.84f, 18.81f, 15.43f, 19.03f); curveTo(16.02f, 19.25f, 17.10f, 19.41f, 17.43f, 19.49f) }
        }
    }

    val Lock: ImageVector by lazy {
        icon("Lock") {
            line(1.8f) { roundRect(5f, 10.5f, 19f, 20.5f, 3.4f) }
            line(1.8f) { moveTo(8.3f, 10.5f); verticalLineTo(8f); arcTo(3.7f, 3.7f, 0f, false, true, 15.7f, 8f); verticalLineTo(10.5f) }
            fill { circle(12f, 15.5f, 1.3f) }
        }
    }

    val ContentPaste: ImageVector by lazy {
        icon("ContentPaste") {
            line(1.8f) { moveTo(8.6f, 4.8f); horizontalLineTo(8f); arcTo(3.2f, 3.2f, 0f, false, false, 4.8f, 8f); verticalLineTo(17.6f); arcTo(3.2f, 3.2f, 0f, false, false, 8f, 20.8f); horizontalLineTo(16f); arcTo(3.2f, 3.2f, 0f, false, false, 19.2f, 17.6f); verticalLineTo(8f); arcTo(3.2f, 3.2f, 0f, false, false, 16f, 4.8f); horizontalLineTo(15.4f) }
            line(1.8f) { roundRect(8.6f, 2.9f, 15.4f, 6.5f, 1.6f) }
            line(1.8f) { moveTo(8.6f, 12f); horizontalLineTo(15.4f); moveTo(8.6f, 15.8f); horizontalLineTo(13f) }
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
            line(1.8f) { folderShape() }
        }
    }

    val ErrorOutline: ImageVector by lazy {
        icon("ErrorOutline") {
            line(1.8f) { circle(12f, 12f, 9f) }
            line(1.8f) { moveTo(12f, 7.4f); verticalLineTo(13f) }
            fill { circle(12f, 16.6f, 1.2f) }
        }
    }

    val Bolt: ImageVector by lazy {
        icon("Bolt") {
            line(1.8f) { moveTo(13f, 3.2f); lineTo(5.8f, 13.4f); horizontalLineTo(11.4f); lineTo(10.6f, 20.8f); lineTo(18.2f, 10.4f); horizontalLineTo(12.6f); close() }
        }
    }

    val Globe: ImageVector by lazy {
        icon("Globe") {
            line(1.8f) { circle(12f, 12f, 9f) }
            line(1.8f) { moveTo(3f, 12f); horizontalLineTo(21f) }
            line(1.8f) { moveTo(12f, 3f); curveTo(8.2f, 6.4f, 8.2f, 17.6f, 12f, 21f); moveTo(12f, 3f); curveTo(15.8f, 6.4f, 15.8f, 17.6f, 12f, 21f) }
        }
    }

    val MusicNote: ImageVector by lazy {
        icon("MusicNote") {
            line(1.8f) { circle(8f, 17.4f, 2.8f) }
            line(1.8f) { moveTo(10.8f, 17.2f); verticalLineTo(4.8f); curveTo(13.4f, 6f, 17f, 6.6f, 17.8f, 10.2f) }
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
