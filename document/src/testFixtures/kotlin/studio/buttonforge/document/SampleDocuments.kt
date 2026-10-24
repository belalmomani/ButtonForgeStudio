package studio.buttonforge.document

import studio.buttonforge.document.appearance.Appearance
import studio.buttonforge.document.appearance.Color
import studio.buttonforge.document.appearance.Fill
import studio.buttonforge.document.appearance.Paint
import studio.buttonforge.document.layers.LayerId
import studio.buttonforge.document.layers.ShapeLayer
import studio.buttonforge.document.shape.Shape
import studio.buttonforge.geometry.IdSource
import studio.buttonforge.geometry.TestShapes
import studio.buttonforge.geometry.path.CompoundPath

object SampleDocuments {
    fun empty(): Document = Document(name = "Empty", canvas = CanvasSize(512.0, 512.0))

    fun circleButton(): Document {
        val contour = TestShapes.circle(100.0, IdSource(1))
        val nextAnchor = contour.anchors.maxOf { it.id.value } + 1
        val appearance = Appearance(fills = listOf(Fill(Paint.Solid(Color(0xFF3366FF.toInt())))))
        val layer = ShapeLayer(LayerId(1), "Circle", shape = Shape(CompoundPath(listOf(contour)), appearance))
        return empty().copy(
            name = "Circle button",
            layers = listOf(layer),
            counters = IdCounters(nextAnchor = nextAnchor, nextLayer = 2),
        )
    }
}
