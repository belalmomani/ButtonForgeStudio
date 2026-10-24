package studio.buttonforge.renderer

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.SweepGradient
import studio.buttonforge.document.Document
import studio.buttonforge.document.appearance.StrokeCap
import studio.buttonforge.document.appearance.StrokeJoin
import studio.buttonforge.document.layers.GroupLayer
import studio.buttonforge.document.layers.LayerNode
import studio.buttonforge.document.layers.ShapeLayer
import studio.buttonforge.document.shape.Shape
import studio.buttonforge.document.appearance.GradientStop
import studio.buttonforge.document.appearance.Paint as ModelPaint
import kotlin.math.roundToInt

/**
 * Draws a document with plain android.graphics calls. Phase 1 draws fills (solid and gradients) and strokes;
 * shadows, blur, glow and blend modes are not drawn yet. Opacity is applied per fill and stroke.
 */
object SceneRenderer {
    fun draw(canvas: Canvas, doc: Document, scale: Float, offsetX: Float, offsetY: Float) {
        canvas.save()
        canvas.translate(offsetX, offsetY)
        canvas.scale(scale, scale)
        val background = Paint().apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        canvas.drawRect(0f, 0f, doc.canvas.width.toFloat(), doc.canvas.height.toFloat(), background)
        drawNodes(canvas, doc.layers)
        canvas.restore()
    }

    private fun drawNodes(canvas: Canvas, nodes: List<LayerNode>) {
        for (node in nodes) {
            if (!node.visible) continue
            when (node) {
                is ShapeLayer -> drawShape(canvas, node.shape)
                is GroupLayer -> drawNodes(canvas, node.children)
            }
        }
    }

    private fun drawShape(canvas: Canvas, shape: Shape) {
        val path = PathConverter.toPath(shape.geometry)
        val t = shape.transform
        canvas.save()
        canvas.translate(t.translation.x.toFloat(), t.translation.y.toFloat())
        canvas.rotate(Math.toDegrees(t.rotationRadians).toFloat())
        canvas.scale(t.scale.x.toFloat(), t.scale.y.toFloat())

        val shapeOpacity = shape.appearance.opacity
        for (fill in shape.appearance.fills) {
            if (!fill.enabled) continue
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            p.style = Paint.Style.FILL
            applyPaint(p, fill.paint, fill.opacity * shapeOpacity)
            canvas.drawPath(path, p)
        }
        for (stroke in shape.appearance.strokes) {
            if (!stroke.enabled || stroke.width <= 0.0) continue
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            p.style = Paint.Style.STROKE
            p.strokeWidth = stroke.width.toFloat()
            p.strokeMiter = stroke.miterLimit.toFloat()
            p.strokeCap = when (stroke.cap) {
                StrokeCap.Butt -> Paint.Cap.BUTT
                StrokeCap.Round -> Paint.Cap.ROUND
                StrokeCap.Square -> Paint.Cap.SQUARE
            }
            p.strokeJoin = when (stroke.join) {
                StrokeJoin.Miter -> Paint.Join.MITER
                StrokeJoin.Round -> Paint.Join.ROUND
                StrokeJoin.Bevel -> Paint.Join.BEVEL
            }
            if (stroke.dash.isNotEmpty() && stroke.dash.sum() > 0.0) {
                val intervals = if (stroke.dash.size % 2 == 0) stroke.dash else stroke.dash + stroke.dash
                p.pathEffect = DashPathEffect(FloatArray(intervals.size) { intervals[it].toFloat() }, 0f)
            }
            applyPaint(p, stroke.paint, stroke.opacity * shapeOpacity)
            canvas.drawPath(path, p)
        }
        canvas.restore()
    }

    private fun applyPaint(p: Paint, paint: ModelPaint, opacity: Double) {
        var baseAlpha = 255
        when (paint) {
            is ModelPaint.Solid -> {
                p.color = paint.color.argb
                baseAlpha = Color.alpha(paint.color.argb)
            }
            is ModelPaint.LinearGradient -> p.shader = LinearGradient(
                paint.start.x.toFloat(), paint.start.y.toFloat(), paint.end.x.toFloat(), paint.end.y.toFloat(),
                colors(paint.stops), positions(paint.stops), Shader.TileMode.CLAMP,
            )
            is ModelPaint.RadialGradient -> p.shader = RadialGradient(
                paint.center.x.toFloat(), paint.center.y.toFloat(), paint.radius.toFloat(),
                colors(paint.stops), positions(paint.stops), Shader.TileMode.CLAMP,
            )
            is ModelPaint.SweepGradient -> p.shader = SweepGradient(
                paint.center.x.toFloat(), paint.center.y.toFloat(), colors(paint.stops), positions(paint.stops),
            )
        }
        p.alpha = (baseAlpha * opacity).roundToInt().coerceIn(0, 255)
    }

    private fun colors(stops: List<GradientStop>) = IntArray(stops.size) { stops[it].color.argb }
    private fun positions(stops: List<GradientStop>) = FloatArray(stops.size) { stops[it].offset.toFloat() }
}
