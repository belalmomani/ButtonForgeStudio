package studio.buttonforge.document

import studio.buttonforge.document.appearance.Appearance
import studio.buttonforge.document.appearance.BlendMode
import studio.buttonforge.document.appearance.Color
import studio.buttonforge.document.appearance.Effect
import studio.buttonforge.document.appearance.Fill
import studio.buttonforge.document.appearance.GradientStop
import studio.buttonforge.document.appearance.Paint
import studio.buttonforge.document.appearance.Stroke
import studio.buttonforge.document.appearance.StrokeCap
import studio.buttonforge.document.appearance.StrokeJoin
import studio.buttonforge.document.export.ExportSettings
import studio.buttonforge.document.export.ExportTarget
import studio.buttonforge.document.layers.GroupLayer
import studio.buttonforge.document.layers.LayerId
import studio.buttonforge.document.layers.ShapeLayer
import studio.buttonforge.document.shape.Shape
import studio.buttonforge.document.shape.Transform
import studio.buttonforge.document.states.ButtonState
import studio.buttonforge.document.states.StateOverride
import studio.buttonforge.document.states.StateSet
import studio.buttonforge.geometry.IdSource
import studio.buttonforge.geometry.TestShapes
import studio.buttonforge.geometry.math.Vec2
import studio.buttonforge.geometry.ops.ArcLengthDistribution
import studio.buttonforge.geometry.path.Anchor
import studio.buttonforge.geometry.path.AnchorId
import studio.buttonforge.geometry.path.CompoundPath
import studio.buttonforge.geometry.path.Contour
import studio.buttonforge.geometry.path.CornerType
import studio.buttonforge.geometry.path.FillRule
import studio.buttonforge.geometry.path.withInsertedAnchors

/** A document where every field has a non-default value. Used to prove nothing is lost on save and reopen. */
fun SampleDocuments.kitchenSink(): Document {
    val circle = TestShapes.circle(100.0, IdSource(1)) // anchor ids 1..4
    val alloc = IdAllocator(IdCounters(nextAnchor = 5, nextLayer = 1))
    val params = ArcLengthDistribution.distribute(circle, 100).filter { it.t > 1e-9 && it.t < 1 - 1e-9 }
    val outer = circle.withInsertedAnchors(params, alloc::nextAnchorId) // about 100 anchors
    val hole = TestShapes.circle(40.0, IdSource(5000))
    val square = TestShapes.square(60.0, IdSource(6000))
    val open = Contour(
        listOf(
            Anchor(AnchorId(7000), Vec2(0.0, 0.0), Vec2.ZERO, Vec2(10.0, 5.0), CornerType.Symmetric),
            Anchor(AnchorId(7001), Vec2(50.0, 20.0), Vec2(-10.0, -5.0), Vec2.ZERO, CornerType.Smooth),
        ),
        closed = false,
    )
    val stops = listOf(GradientStop(0.0, Color(0xFFFF0000.toInt())), GradientStop(1.0, Color(0xFF0000FF.toInt())))

    val bodyAppearance = Appearance(
        fills = listOf(
            Fill(Paint.Solid(Color(0xFF3366FF.toInt()))),
            Fill(Paint.LinearGradient(Vec2(0.0, 0.0), Vec2(10.5, 20.25), stops), opacity = 0.5),
            Fill(Paint.RadialGradient(Vec2(1.0, 2.0), 33.5, stops), enabled = false),
            Fill(Paint.SweepGradient(Vec2(-3.0, 4.0), stops), opacity = 0.25),
        ),
        strokes = listOf(
            Stroke(
                Paint.Solid(Color.Black), width = 3.5, cap = StrokeCap.Round, join = StrokeJoin.Bevel,
                miterLimit = 2.0, dash = listOf(4.0, 2.0), opacity = 0.8,
            ),
        ),
        effects = listOf(
            Effect.DropShadow(Vec2(2.0, 4.0), 7.25, 1.5, Color(0x80000000.toInt())),
            Effect.InnerShadow(Vec2(-1.0, -2.0), 3.0, 0.5, Color(0x80FFFFFF.toInt())),
            Effect.LayerBlur(2.5),
            Effect.Glow(6.0, Color(0xFFFFFF00.toInt()), 1.5),
        ),
        opacity = 0.9,
        blendMode = BlendMode.Multiply,
    )

    val body = ShapeLayer(
        LayerId(2), "Body",
        shape = Shape(
            CompoundPath(listOf(outer, hole), FillRule.EvenOdd),
            bodyAppearance,
            Transform(Vec2(10.5, -3.25), 0.5, Vec2(1.5, 0.75)),
        ),
    )
    val squareLayer = ShapeLayer(
        LayerId(3), "Square",
        shape = Shape(CompoundPath(listOf(square)), Appearance(fills = listOf(Fill(Paint.Solid(Color.White))))),
    )
    val curve = ShapeLayer(
        LayerId(4), "Curve", visible = false, locked = true,
        shape = Shape(
            CompoundPath(listOf(open)),
            Appearance(strokes = listOf(Stroke(Paint.Solid(Color.Black), width = 2.0))),
        ),
    )
    val states = StateSet(
        mapOf(
            ButtonState.Pressed to mapOf(
                LayerId(2) to StateOverride(Appearance(opacity = 0.7), Transform(Vec2(0.0, 2.0), 0.0, Vec2(0.95, 0.95))),
            ),
            ButtonState.Disabled to mapOf(
                LayerId(3) to StateOverride(
                    appearance = Appearance(fills = listOf(Fill(Paint.Solid(Color(0xFF888888.toInt()))))),
                ),
            ),
        ),
    )
    return Document(
        name = "زر تجريبي — kitchen sink",
        canvas = CanvasSize(640.5, 480.25),
        layers = listOf(GroupLayer(LayerId(1), "Button", children = listOf(body, squareLayer)), curve),
        states = states,
        export = ExportSettings(setOf(ExportTarget.Svg, ExportTarget.Png, ExportTarget.Unity), listOf(1.0, 2.0, 3.0), false),
        counters = IdCounters(nextAnchor = 7002, nextLayer = 5),
    )
}
