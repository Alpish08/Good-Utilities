package com.tools.app.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorPath
import kotlin.math.roundToInt

/**
 * Draws one of the app's Compose icons ([ImageVector]) into a white bitmap for a home-screen widget, where Compose
 * can't run. The widget tints it (white keeps the icon's own transparency), so dark and light mode both work.
 */
object VectorBitmap {
    fun render(icon: ImageVector, sizePx: Int): Bitmap {
        val b = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(b)
        canvas.scale(sizePx / icon.viewportWidth, sizePx / icon.viewportHeight)
        draw(canvas, icon.root)
        return b
    }

    private fun draw(canvas: Canvas, group: VectorGroup) {
        canvas.save()
        canvas.translate(group.translationX + group.pivotX, group.translationY + group.pivotY)
        canvas.rotate(group.rotation)
        canvas.scale(group.scaleX, group.scaleY)
        canvas.translate(-group.pivotX, -group.pivotY)
        for (node in group) when (node) {
            is VectorGroup -> draw(canvas, node)
            is VectorPath -> {
                val path = PathParser().addPathNodes(node.pathData).toPath().asAndroidPath()
                path.fillType = if (node.pathFillType == PathFillType.EvenOdd) android.graphics.Path.FillType.EVEN_ODD else android.graphics.Path.FillType.WINDING
                (node.fill as? SolidColor)?.let { fill ->
                    canvas.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        style = Paint.Style.FILL
                        color = android.graphics.Color.WHITE
                        alpha = (255 * fill.value.alpha * node.fillAlpha).roundToInt()
                    })
                }
                (node.stroke as? SolidColor)?.let { stroke ->
                    canvas.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        style = Paint.Style.STROKE
                        color = android.graphics.Color.WHITE
                        alpha = (255 * stroke.value.alpha * node.strokeAlpha).roundToInt()
                        strokeWidth = node.strokeLineWidth
                        strokeMiter = node.strokeLineMiter
                        strokeCap = when (node.strokeLineCap) { StrokeCap.Round -> Paint.Cap.ROUND; StrokeCap.Square -> Paint.Cap.SQUARE; else -> Paint.Cap.BUTT }
                        strokeJoin = when (node.strokeLineJoin) { StrokeJoin.Round -> Paint.Join.ROUND; StrokeJoin.Bevel -> Paint.Join.BEVEL; else -> Paint.Join.MITER }
                    })
                }
            }
        }
        canvas.restore()
    }
}
