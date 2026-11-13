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
    // The outline picture with its frame filled in: same outer edge, corners, sun and hills.
    val TabImagesOn: ImageVector by lazy {
        icon("TabImagesOn") {
            fill(evenOdd = true) {
                roundRect(2.93f, 2.93f, 21.07f, 21.07f, 3.8f)
                circle(8.58f, 8.72f, 1.9f)
                moveTo(4.55f, 17.12f); lineTo(9.06f, 14.57f); lineTo(11.82f, 16.37f); lineTo(16.23f, 12.77f); lineTo(19.45f, 15.68f)
                lineTo(19.45f, 17.27f); arcTo(2.18f, 2.18f, 0f, false, true, 17.27f, 19.45f); lineTo(6.73f, 19.45f)
                arcTo(2.18f, 2.18f, 0f, false, true, 4.55f, 17.27f); close()
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
    // Apps: three rounded squares and a diamond (One UI's "apps & more").
    val TabMisc: ImageVector by lazy {
        icon("TabMisc") {
            line(1.62f) { roundRect(3.8f, 3.8f, 10.4f, 10.4f, 2.4f) }
            line(1.62f) { roundRect(3.8f, 13.6f, 10.4f, 20.2f, 2.4f) }
            line(1.62f) { roundRect(13.6f, 13.6f, 20.2f, 20.2f, 2.4f) }
            line(1.62f) { diamond(16.9f, 7.1f, 4.0f) }
        }
    }
    // Filled, each shape 0.4 smaller so the solid shapes keep clear gaps between them.
    val TabMiscOn: ImageVector by lazy {
        icon("TabMiscOn") {
            solid(1.62f) { roundRect(4.0f, 4.0f, 10.2f, 10.2f, 2.2f) }
            solid(1.62f) { roundRect(4.0f, 13.8f, 10.2f, 20.0f, 2.2f) }
            solid(1.62f) { roundRect(13.8f, 13.8f, 20.0f, 20.0f, 2.2f) }
            solid(1.62f) { diamond(16.9f, 7.1f, 3.8f) }
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
    /** Samsung's filled fingerprint glyph, traced from the reference image (five ridges). */
    val Fingerprint: ImageVector by lazy {
        icon("Fingerprint") {
            fill { moveTo(11.82f, 2.02f); lineTo(10.84f, 2.15f); lineTo(9.67f, 2.44f); lineTo(8.59f, 2.88f); lineTo(7.91f, 3.25f); lineTo(7.6f, 3.54f); lineTo(7.46f, 3.83f); lineTo(7.4f, 4.27f); lineTo(7.51f, 4.66f); lineTo(7.71f, 4.96f); lineTo(8.01f, 5.18f); lineTo(8.25f, 5.27f); lineTo(8.59f, 5.29f); lineTo(8.93f, 5.21f); lineTo(10.25f, 4.61f); lineTo(11.08f, 4.38f); lineTo(12.5f, 4.24f); lineTo(13.77f, 4.35f); lineTo(14.94f, 4.69f); lineTo(16.1f, 5.25f); lineTo(16.96f, 5.88f); lineTo(17.73f, 6.66f); lineTo(18.36f, 7.54f); lineTo(18.85f, 8.52f); lineTo(19.2f, 9.64f); lineTo(19.33f, 10.62f); lineTo(19.32f, 12.14f); lineTo(19.2f, 13.8f); lineTo(18.9f, 15.8f); lineTo(18.39f, 17.9f); lineTo(18.39f, 18.44f); lineTo(18.5f, 18.73f); lineTo(18.64f, 18.92f); lineTo(18.99f, 19.19f); lineTo(19.24f, 19.28f); lineTo(19.53f, 19.3f); lineTo(20.0f, 19.17f); lineTo(20.17f, 19.07f); lineTo(20.42f, 18.78f); lineTo(20.71f, 17.95f); lineTo(21.14f, 15.99f); lineTo(21.49f, 13.31f); lineTo(21.58f, 11.11f); lineTo(21.49f, 9.79f); lineTo(21.2f, 8.47f); lineTo(20.77f, 7.3f); lineTo(20.03f, 5.98f); lineTo(19.35f, 5.1f); lineTo(18.16f, 3.97f); lineTo(16.85f, 3.1f); lineTo(15.67f, 2.57f); lineTo(14.45f, 2.21f); lineTo(13.38f, 2.03f); close() }
            fill { moveTo(5.56f, 5.98f); lineTo(5.08f, 6.11f); lineTo(4.64f, 6.52f); lineTo(4.08f, 7.64f); lineTo(3.66f, 8.96f); lineTo(3.46f, 10.18f); lineTo(3.38f, 11.84f); lineTo(3.28f, 12.62f); lineTo(3.05f, 13.6f); lineTo(2.45f, 15.16f); lineTo(2.42f, 15.36f); lineTo(2.48f, 15.8f); lineTo(2.68f, 16.14f); lineTo(2.88f, 16.34f); lineTo(3.27f, 16.51f); lineTo(3.56f, 16.54f); lineTo(3.95f, 16.46f); lineTo(4.25f, 16.29f); lineTo(4.43f, 16.09f); lineTo(4.63f, 15.75f); lineTo(5.06f, 14.67f); lineTo(5.39f, 13.5f); lineTo(5.59f, 12.28f); lineTo(5.66f, 10.62f); lineTo(5.8f, 9.64f); lineTo(6.0f, 8.91f); lineTo(6.66f, 7.45f); lineTo(6.72f, 7.15f); lineTo(6.67f, 6.76f); lineTo(6.37f, 6.27f); lineTo(6.0f, 6.06f); close() }
            fill { moveTo(12.45f, 5.98f); lineTo(11.57f, 6.06f); lineTo(10.99f, 6.21f); lineTo(10.25f, 6.49f); lineTo(9.57f, 6.9f); lineTo(9.03f, 7.33f); lineTo(8.56f, 7.84f); lineTo(8.23f, 8.28f); lineTo(7.79f, 9.11f); lineTo(7.6f, 9.64f); lineTo(7.46f, 10.28f); lineTo(7.29f, 12.82f); lineTo(6.95f, 14.48f); lineTo(6.74f, 15.16f); lineTo(6.3f, 16.29f); lineTo(5.3f, 18.14f); lineTo(5.22f, 18.48f); lineTo(5.25f, 18.87f); lineTo(5.41f, 19.22f); lineTo(5.71f, 19.52f); lineTo(5.91f, 19.62f); lineTo(6.15f, 19.68f); lineTo(6.54f, 19.68f); lineTo(6.83f, 19.58f); lineTo(7.08f, 19.41f); lineTo(7.25f, 19.22f); lineTo(7.78f, 18.34f); lineTo(8.23f, 17.46f); lineTo(8.66f, 16.43f); lineTo(9.0f, 15.46f); lineTo(9.33f, 14.14f); lineTo(9.54f, 12.87f); lineTo(9.65f, 10.77f); lineTo(9.81f, 10.08f); lineTo(10.05f, 9.6f); lineTo(10.47f, 9.06f); lineTo(10.89f, 8.71f); lineTo(11.43f, 8.42f); lineTo(11.91f, 8.27f); lineTo(12.4f, 8.22f); lineTo(13.09f, 8.27f); lineTo(13.87f, 8.56f); lineTo(14.52f, 9.06f); lineTo(14.85f, 9.45f); lineTo(15.06f, 9.79f); lineTo(15.25f, 10.28f); lineTo(15.34f, 10.77f); lineTo(15.31f, 12.48f); lineTo(15.16f, 13.99f); lineTo(15.17f, 14.24f); lineTo(15.28f, 14.58f); lineTo(15.48f, 14.84f); lineTo(15.72f, 15.04f); lineTo(16.26f, 15.19f); lineTo(16.6f, 15.14f); lineTo(16.85f, 15.03f); lineTo(17.1f, 14.82f); lineTo(17.3f, 14.53f); lineTo(17.4f, 14.19f); lineTo(17.54f, 12.77f); lineTo(17.6f, 10.91f); lineTo(17.54f, 10.28f); lineTo(17.4f, 9.64f); lineTo(16.97f, 8.62f); lineTo(16.52f, 7.94f); lineTo(16.06f, 7.43f); lineTo(15.51f, 6.96f); lineTo(15.09f, 6.68f); lineTo(14.55f, 6.4f); lineTo(13.87f, 6.17f); lineTo(13.04f, 6.01f); close() }
            fill { moveTo(12.3f, 9.98f); lineTo(11.86f, 10.16f); lineTo(11.55f, 10.47f); lineTo(11.46f, 10.67f); lineTo(11.39f, 10.91f); lineTo(11.34f, 12.18f); lineTo(11.22f, 13.41f); lineTo(11.02f, 14.58f); lineTo(10.71f, 15.8f); lineTo(10.38f, 16.82f); lineTo(9.92f, 17.95f); lineTo(8.67f, 20.34f); lineTo(8.66f, 20.83f); lineTo(8.82f, 21.22f); lineTo(9.13f, 21.53f); lineTo(9.52f, 21.7f); lineTo(9.91f, 21.73f); lineTo(10.4f, 21.52f); lineTo(10.7f, 21.22f); lineTo(11.09f, 20.58f); lineTo(11.87f, 19.07f); lineTo(12.38f, 17.85f); lineTo(12.83f, 16.53f); lineTo(13.16f, 15.26f); lineTo(13.36f, 14.24f); lineTo(13.53f, 12.92f); lineTo(13.6f, 11.89f); lineTo(13.6f, 10.87f); lineTo(13.44f, 10.47f); lineTo(13.13f, 10.16f); lineTo(12.79f, 10.0f); close() }
            fill { moveTo(15.53f, 15.94f); lineTo(15.04f, 16.12f); lineTo(14.69f, 16.48f); lineTo(13.98f, 18.53f); lineTo(13.08f, 20.63f); lineTo(13.07f, 21.07f); lineTo(13.18f, 21.41f); lineTo(13.48f, 21.76f); lineTo(13.87f, 21.96f); lineTo(14.16f, 22.0f); lineTo(14.55f, 21.93f); lineTo(14.84f, 21.77f); lineTo(15.1f, 21.51f); lineTo(15.99f, 19.56f); lineTo(16.72f, 17.41f); lineTo(16.77f, 16.92f); lineTo(16.67f, 16.58f); lineTo(16.51f, 16.34f); lineTo(16.11f, 16.02f); close() }
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
