package studio.buttonforge.export

import studio.buttonforge.document.Document
import studio.buttonforge.document.appearance.BlendMode
import studio.buttonforge.document.appearance.Paint
import studio.buttonforge.document.appearance.StrokeCap
import studio.buttonforge.document.appearance.StrokeJoin
import studio.buttonforge.document.layers.GroupLayer
import studio.buttonforge.document.layers.LayerNode
import studio.buttonforge.document.layers.ShapeLayer
import studio.buttonforge.document.shape.Transform
import studio.buttonforge.geometry.math.Vec2
import studio.buttonforge.geometry.path.CompoundPath
import studio.buttonforge.geometry.path.FillRule
import kotlin.math.abs

internal sealed interface PathCmd {
    data class MoveTo(val x: Double, val y: Double) : PathCmd
    data class CurveTo(val x1: Double, val y1: Double, val x2: Double, val y2: Double, val x: Double, val y: Double) : PathCmd
    object Close : PathCmd
}

internal data class FlatStop(val offset: Double, val argb: Int)

internal sealed interface FlatPaint {
    data class Solid(val argb: Int) : FlatPaint
    data class Linear(val x1: Double, val y1: Double, val x2: Double, val y2: Double, val stops: List<FlatStop>) : FlatPaint
    data class Radial(val cx: Double, val cy: Double, val radius: Double, val stops: List<FlatStop>) : FlatPaint
    data class Sweep(val cx: Double, val cy: Double, val stops: List<FlatStop>) : FlatPaint
}

internal data class StrokeInfo(
    val width: Double,
    val cap: StrokeCap,
    val join: StrokeJoin,
    val miter: Double,
    val dash: List<Double>,
)

/**
 * One filled or stroked outline in canvas coordinates (shape transforms already applied). [stroke] is null for a fill.
 * [alpha] is the fill/stroke opacity times the shape opacity; the colour's own alpha stays in the paint.
 */
internal data class DrawItem(
    val layer: String,
    val commands: List<PathCmd>,
    val evenOdd: Boolean,
    val paint: FlatPaint,
    val alpha: Double,
    val stroke: StrokeInfo?,
    val blend: BlendMode,
    val hasEffects: Boolean,
) {
    val pathData: String by lazy { PathData.string(commands) }
}

internal object PathData {
    fun commands(g: CompoundPath, t: Transform): List<PathCmd> {
        val out = ArrayList<PathCmd>()
        for (c in g.contours) {
            if (c.anchors.size < 2) continue
            val segments = c.segments()
            val start = t.toWorld(segments[0].p0)
            out += PathCmd.MoveTo(start.x, start.y)
            for (s in segments) {
                val a = t.toWorld(s.p1)
                val b = t.toWorld(s.p2)
                val e = t.toWorld(s.p3)
                out += PathCmd.CurveTo(a.x, a.y, b.x, b.y, e.x, e.y)
            }
            if (c.closed) out += PathCmd.Close
        }
        return out
    }

    fun string(commands: List<PathCmd>): String {
        val n = { d: Double -> Format.num(d) }
        val sb = StringBuilder()
        for (c in commands) {
            when (c) {
                is PathCmd.MoveTo -> sb.append('M').append(n(c.x)).append(' ').append(n(c.y))
                is PathCmd.CurveTo -> sb.append('C').append(n(c.x1)).append(' ').append(n(c.y1)).append(' ')
                    .append(n(c.x2)).append(' ').append(n(c.y2)).append(' ').append(n(c.x)).append(' ').append(n(c.y))
                PathCmd.Close -> sb.append('Z')
            }
        }
        return sb.toString()
    }
}

internal object Flattener {
    fun flatten(doc: Document, report: ReportBuilder): List<DrawItem> {
        val out = ArrayList<DrawItem>()
        fun walk(nodes: List<LayerNode>) {
            for (n in nodes) {
                if (!n.visible) continue
                when (n) {
                    is GroupLayer -> walk(n.children)
                    is ShapeLayer -> shape(n, out, report)
                }
            }
        }
        walk(doc.layers)
        return out
    }

    private fun shape(layer: ShapeLayer, out: MutableList<DrawItem>, report: ReportBuilder) {
        val t = layer.shape.transform
        val commands = PathData.commands(layer.shape.geometry, t)
        if (commands.isEmpty()) return
        val sx = abs(t.scale.x)
        val sy = abs(t.scale.y)
        val average = (sx + sy) / 2.0
        val uniform = abs(sx - sy) < 1e-9
        val a = layer.shape.appearance
        val evenOdd = layer.shape.geometry.fillRule == FillRule.EvenOdd

        fun world(p: Vec2) = t.toWorld(p)
        fun stops(list: List<studio.buttonforge.document.appearance.GradientStop>) = list.map { FlatStop(it.offset, it.color.argb) }
        fun paint(p: Paint): FlatPaint = when (p) {
            is Paint.Solid -> FlatPaint.Solid(p.color.argb)
            is Paint.LinearGradient -> {
                val s = world(p.start)
                val e = world(p.end)
                FlatPaint.Linear(s.x, s.y, e.x, e.y, stops(p.stops))
            }
            is Paint.RadialGradient -> {
                val c = world(p.center)
                if (!uniform) {
                    report.add(ReportItem("الكل", layer.name, "تدرج دائري على شكل بتكبير غير متساوٍ", Fidelity.Approximated, "يصير نصف القطر متوسط المقياسين"))
                }
                FlatPaint.Radial(c.x, c.y, p.radius * average, stops(p.stops))
            }
            is Paint.SweepGradient -> {
                val c = world(p.center)
                FlatPaint.Sweep(c.x, c.y, stops(p.stops))
            }
        }

        for (fill in a.fills) {
            if (!fill.enabled) continue
            out += DrawItem(layer.name, commands, evenOdd, paint(fill.paint), fill.opacity * a.opacity, null, a.blendMode, a.effects.isNotEmpty())
        }
        for (stroke in a.strokes) {
            if (!stroke.enabled || stroke.width <= 0.0) continue
            if (!uniform) {
                report.add(ReportItem("الكل", layer.name, "سماكة الحد على شكل بتكبير غير متساوٍ", Fidelity.Approximated, "تُستخدم سماكة متوسطة"))
            }
            val info = StrokeInfo(stroke.width * average, stroke.cap, stroke.join, stroke.miterLimit, stroke.dash)
            out += DrawItem(layer.name, commands, evenOdd, paint(stroke.paint), stroke.opacity * a.opacity, info, a.blendMode, a.effects.isNotEmpty())
        }
    }
}
