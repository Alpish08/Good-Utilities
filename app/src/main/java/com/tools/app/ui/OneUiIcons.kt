package com.tools.app.ui

import androidx.compose.ui.graphics.vector.group
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

    /** Add a photo: the Images tab's filled picture, a little smaller, with a "+" at its bottom-right corner. */
    val AddPhoto: ImageVector by lazy {
        icon("AddPhoto") {
            // TabImagesOn at 72 %, its top-left at (2, 2): it ends at ~15 dp, clear of the "+".
            group(scaleX = 0.72f, scaleY = 0.72f, translationX = -0.1f, translationY = -0.1f) {
                fill(evenOdd = true) {
                    roundRect(2.93f, 2.93f, 21.07f, 21.07f, 3.8f)
                    circle(8.58f, 8.72f, 1.9f)
                    moveTo(4.55f, 17.12f); lineTo(9.06f, 14.57f); lineTo(11.82f, 16.37f); lineTo(16.23f, 12.77f); lineTo(19.45f, 15.68f)
                    lineTo(19.45f, 17.27f); arcTo(2.18f, 2.18f, 0f, false, true, 17.27f, 19.45f); lineTo(6.73f, 19.45f)
                    arcTo(2.18f, 2.18f, 0f, false, true, 4.55f, 17.27f); close()
                }
            }
            line(TabStroke) { moveTo(18.4f, 14.6f); lineTo(18.4f, 21.8f) }
            line(TabStroke) { moveTo(14.8f, 18.2f); lineTo(22f, 18.2f) }
        }
    }

    /** "More options": three vertical dots. */
    val More: ImageVector by lazy {
        // Matched to Samsung Reminder's ⋮ on the S24 Ultra: 11 px dots 21.5 px apart (r 1.6, 6.15 apart at 24 dp).
        icon("More") {
            fill { circle(12f, 5.85f, 1.6f) }
            fill { circle(12f, 12f, 1.6f) }
            fill { circle(12f, 18.15f, 1.6f) }
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

    /** Line weight of the tab bar's outline icons (was 1.62, ~5.3 px; 1.5 is a touch thinner). */
    private const val TabStroke = 1.5f

    /** Battery info: Material's rounded battery with an "i" cut out (like the bolt in Charge limit's icon). */
    val BatteryInfo: ImageVector by lazy {
        ImageVector.Builder("BatteryInfo", 24.dp, 24.dp, 24f, 24f).addPath(
            pathData = androidx.compose.ui.graphics.vector.addPathNodes(
                "M15.67,4H14V3c0-0.55-0.45-1-1-1h-2c-0.55,0-1,0.45-1,1v1H8.33C7.6,4 7,4.6 7,5.33v15.33C7,21.4 7.6,22 8.34,22h7.33" +
                    "c0.74,0 1.34-0.6 1.34-1.33V5.33C17,4.6 16.4,4 15.67,4z" +
                    "M12,7.35a1.25,1.25 0 1,0 0.001,0z" +
                    "M10.95,10.9h2.1v7.6h-2.1z",
            ),
            fill = androidx.compose.ui.graphics.SolidColor(androidx.compose.ui.graphics.Color.Black),
            pathFillType = androidx.compose.ui.graphics.PathFillType.EvenOdd,
        ).build()
    }

    val TabMedia: ImageVector by lazy {
        icon("TabMedia") {
            line(TabStroke) { circle(8.6f, 16.9f, 3.1f) }
            line(TabStroke) { noteFlag() }
        }
    }
    val TabMediaOn: ImageVector by lazy {
        icon("TabMediaOn") {
            fill { circle(8.6f, 16.9f, 4.0f) }
            line(TabStroke) { noteFlag() }
        }
    }
    val TabFiles: ImageVector by lazy { icon("TabFiles") { line(TabStroke) { folderShape() } } }
    val TabFilesOn: ImageVector by lazy { icon("TabFilesOn") { solid(1.62f) { folderShape() } } }
    // Picture, traced from One UI's gallery icon: frame, sun, two peaks.
    val TabImages: ImageVector by lazy {
        icon("TabImages") {
            line(TabStroke) { roundRect(3.74f, 3.74f, 20.26f, 20.26f, 2.99f) }
            line(TabStroke) { circle(8.58f, 8.72f, 1.89f) }
            line(TabStroke) { moveTo(3.81f, 16.59f); lineTo(9.06f, 13.62f); lineTo(11.82f, 15.42f); lineTo(16.23f, 11.82f); lineTo(20.01f, 15.24f) }
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
            line(TabStroke) { roundRect(6.6f, 3.4f, 17.4f, 20.6f, 3f) }
            line(TabStroke) { moveTo(10.8f, 17.5f); lineTo(13.2f, 17.5f) }
        }
    }
    val TabDeviceOn: ImageVector by lazy {
        icon("TabDeviceOn") {
            fill(evenOdd = true) { roundRect(5.9f, 2.7f, 18.1f, 21.3f, 3.6f); roundRect(10.05f, 16.75f, 13.95f, 18.25f, 0.75f) }
        }
    }
    private fun PathBuilder.diamond(cx: Float, cy: Float, r: Float) {
        moveTo(cx, cy - r); lineTo(cx + r, cy); lineTo(cx, cy + r); lineTo(cx - r, cy); close()
    }
    // Apps: three rounded squares and a diamond (One UI's "apps & more").
    val TabMisc: ImageVector by lazy {
        icon("TabMisc") {
            line(TabStroke) { roundRect(3.8f, 3.8f, 10.4f, 10.4f, 2.4f) }
            line(TabStroke) { roundRect(3.8f, 13.6f, 10.4f, 20.2f, 2.4f) }
            line(TabStroke) { roundRect(13.6f, 13.6f, 20.2f, 20.2f, 2.4f) }
            line(TabStroke) { diamond(16.9f, 7.1f, 4.0f) }
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
    /** Samsung's filled fingerprint glyph, traced from the reference image (five ridges), each ridge ~2 px thinner on screen. */
    val Fingerprint: ImageVector by lazy {
        icon("Fingerprint") {
            fill { moveTo(11.82f, 2.26f); lineTo(11.43f, 2.27f); lineTo(10.2f, 2.51f); lineTo(9.18f, 2.86f); lineTo(8.45f, 3.2f); lineTo(8.01f, 3.44f); lineTo(7.81f, 3.64f); lineTo(7.62f, 4.13f); lineTo(7.66f, 4.47f); lineTo(7.76f, 4.66f); lineTo(8.01f, 4.91f); lineTo(8.35f, 5.05f); lineTo(8.69f, 5.05f); lineTo(9.67f, 4.59f); lineTo(10.84f, 4.19f); lineTo(11.86f, 4.03f); lineTo(13.18f, 4.03f); lineTo(13.57f, 4.08f); lineTo(14.84f, 4.39f); lineTo(16.02f, 4.93f); lineTo(17.04f, 5.64f); lineTo(17.92f, 6.52f); lineTo(18.56f, 7.4f); lineTo(19.07f, 8.42f); lineTo(19.41f, 9.55f); lineTo(19.58f, 10.72f); lineTo(19.53f, 12.67f); lineTo(19.34f, 14.63f); lineTo(18.98f, 16.58f); lineTo(18.6f, 18.0f); lineTo(18.6f, 18.39f); lineTo(18.8f, 18.78f); lineTo(18.99f, 18.92f); lineTo(19.34f, 19.07f); lineTo(19.78f, 19.02f); lineTo(20.07f, 18.87f); lineTo(20.22f, 18.68f); lineTo(20.51f, 17.85f); lineTo(20.95f, 15.9f); lineTo(21.29f, 13.26f); lineTo(21.29f, 12.67f); lineTo(21.34f, 12.53f); lineTo(21.34f, 10.28f); lineTo(21.29f, 9.84f); lineTo(20.95f, 8.37f); lineTo(20.61f, 7.5f); lineTo(19.92f, 6.23f); lineTo(19.19f, 5.25f); lineTo(18.07f, 4.18f); lineTo(16.75f, 3.3f); lineTo(15.67f, 2.81f); lineTo(14.4f, 2.42f); lineTo(13.09f, 2.22f); lineTo(11.91f, 2.22f); close() }
            fill { moveTo(5.56f, 6.22f); lineTo(5.42f, 6.23f); lineTo(5.08f, 6.37f); lineTo(4.83f, 6.62f); lineTo(4.25f, 7.84f); lineTo(3.86f, 9.06f); lineTo(3.66f, 10.23f); lineTo(3.61f, 11.99f); lineTo(3.51f, 12.67f); lineTo(3.3f, 13.6f); lineTo(2.63f, 15.36f); lineTo(2.68f, 15.7f); lineTo(2.88f, 16.04f); lineTo(3.17f, 16.24f); lineTo(3.51f, 16.34f); lineTo(4.0f, 16.19f); lineTo(4.15f, 16.09f); lineTo(4.3f, 15.9f); lineTo(4.64f, 15.16f); lineTo(4.98f, 14.19f); lineTo(5.32f, 12.72f); lineTo(5.42f, 11.84f); lineTo(5.42f, 10.62f); lineTo(5.54f, 9.74f); lineTo(5.73f, 8.96f); lineTo(5.93f, 8.42f); lineTo(6.4f, 7.5f); lineTo(6.49f, 7.2f); lineTo(6.49f, 6.96f); lineTo(6.25f, 6.47f); lineTo(5.96f, 6.27f); close() }
            fill { moveTo(12.45f, 6.22f); lineTo(11.96f, 6.23f); lineTo(11.23f, 6.37f); lineTo(10.64f, 6.57f); lineTo(9.96f, 6.91f); lineTo(9.23f, 7.45f); lineTo(8.93f, 7.74f); lineTo(8.54f, 8.23f); lineTo(8.15f, 8.86f); lineTo(7.81f, 9.74f); lineTo(7.66f, 10.38f); lineTo(7.57f, 12.48f); lineTo(7.42f, 13.45f); lineTo(7.16f, 14.63f); lineTo(6.52f, 16.38f); lineTo(5.47f, 18.34f); lineTo(5.42f, 18.58f); lineTo(5.47f, 18.83f); lineTo(5.61f, 19.12f); lineTo(5.81f, 19.31f); lineTo(6.0f, 19.41f); lineTo(6.49f, 19.46f); lineTo(6.83f, 19.31f); lineTo(7.08f, 19.07f); lineTo(7.57f, 18.24f); lineTo(8.15f, 17.07f); lineTo(8.84f, 15.21f); lineTo(9.13f, 14.04f); lineTo(9.37f, 12.38f); lineTo(9.42f, 10.72f); lineTo(9.59f, 9.99f); lineTo(9.83f, 9.5f); lineTo(10.11f, 9.11f); lineTo(10.74f, 8.52f); lineTo(11.33f, 8.2f); lineTo(11.82f, 8.05f); lineTo(12.3f, 7.98f); lineTo(13.18f, 8.05f); lineTo(13.67f, 8.2f); lineTo(14.11f, 8.42f); lineTo(14.45f, 8.67f); lineTo(14.84f, 9.06f); lineTo(15.14f, 9.45f); lineTo(15.41f, 9.99f); lineTo(15.58f, 10.72f); lineTo(15.58f, 12.04f); lineTo(15.38f, 13.94f); lineTo(15.38f, 14.19f); lineTo(15.48f, 14.48f); lineTo(15.58f, 14.63f); lineTo(15.82f, 14.82f); lineTo(16.02f, 14.92f); lineTo(16.36f, 14.97f); lineTo(16.55f, 14.92f); lineTo(16.89f, 14.72f); lineTo(17.14f, 14.33f); lineTo(17.33f, 12.77f); lineTo(17.38f, 10.82f); lineTo(17.19f, 9.74f); lineTo(16.8f, 8.77f); lineTo(16.06f, 7.74f); lineTo(15.53f, 7.25f); lineTo(15.04f, 6.91f); lineTo(14.36f, 6.57f); lineTo(13.77f, 6.37f); lineTo(13.04f, 6.23f); close() }
            fill { moveTo(12.3f, 10.22f); lineTo(12.01f, 10.33f); lineTo(11.77f, 10.57f); lineTo(11.62f, 10.91f); lineTo(11.57f, 12.38f); lineTo(11.43f, 13.65f); lineTo(11.16f, 15.02f); lineTo(10.77f, 16.43f); lineTo(9.94f, 18.48f); lineTo(9.08f, 20.0f); lineTo(8.89f, 20.44f); lineTo(8.89f, 20.78f); lineTo(9.03f, 21.12f); lineTo(9.37f, 21.41f); lineTo(9.67f, 21.51f); lineTo(9.86f, 21.51f); lineTo(10.3f, 21.32f); lineTo(10.5f, 21.12f); lineTo(11.13f, 20.05f); lineTo(11.72f, 18.87f); lineTo(12.21f, 17.7f); lineTo(12.65f, 16.38f); lineTo(12.89f, 15.46f); lineTo(13.09f, 14.53f); lineTo(13.33f, 12.82f); lineTo(13.38f, 10.91f); lineTo(13.23f, 10.57f); lineTo(12.99f, 10.33f); lineTo(12.6f, 10.18f); close() }
            fill { moveTo(15.53f, 16.18f); lineTo(15.28f, 16.24f); lineTo(15.14f, 16.34f); lineTo(14.84f, 16.68f); lineTo(14.14f, 18.78f); lineTo(13.28f, 20.73f); lineTo(13.28f, 21.02f); lineTo(13.43f, 21.37f); lineTo(13.53f, 21.51f); lineTo(13.96f, 21.76f); lineTo(14.36f, 21.76f); lineTo(14.5f, 21.71f); lineTo(14.7f, 21.61f); lineTo(14.89f, 21.41f); lineTo(15.77f, 19.51f); lineTo(16.55f, 17.17f); lineTo(16.5f, 16.77f); lineTo(16.26f, 16.38f); lineTo(15.87f, 16.19f); lineTo(15.63f, 16.14f); close() }
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
