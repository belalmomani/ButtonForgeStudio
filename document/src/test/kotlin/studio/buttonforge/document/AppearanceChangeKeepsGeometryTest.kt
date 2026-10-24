package studio.buttonforge.document

import studio.buttonforge.document.appearance.Appearance
import studio.buttonforge.document.appearance.Color
import studio.buttonforge.document.appearance.Effect
import studio.buttonforge.document.appearance.Fill
import studio.buttonforge.document.appearance.Paint
import studio.buttonforge.document.layers.LayerId
import studio.buttonforge.document.layers.ShapeLayer
import studio.buttonforge.document.layers.findLayer
import studio.buttonforge.document.layers.updateShape
import studio.buttonforge.geometry.math.Vec2
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertSame

class AppearanceChangeKeepsGeometryTest {
    private fun shapeOf(doc: Document) = (doc.findLayer(LayerId(1)) as ShapeLayer).shape

    @Test
    fun changingFillsAndEffectsLeavesGeometryUntouched() {
        val before = SampleDocuments.circleButton()
        val after = before.updateShape(LayerId(1)) { shape ->
            shape.copy(
                appearance = Appearance(
                    fills = listOf(Fill(Paint.Solid(Color.White))),
                    effects = listOf(Effect.DropShadow(Vec2(2.0, 4.0), 8.0, 0.0, Color.Black)),
                    opacity = 0.5,
                ),
            )
        }
        assertSame(shapeOf(before).geometry, shapeOf(after).geometry)
        assertNotEquals(shapeOf(before).appearance, shapeOf(after).appearance)
        assertEquals(shapeOf(before).transform, shapeOf(after).transform)
    }
}
