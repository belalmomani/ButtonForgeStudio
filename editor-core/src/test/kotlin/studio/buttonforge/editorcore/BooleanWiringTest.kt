package studio.buttonforge.editorcore

import studio.buttonforge.document.CanvasSize
import studio.buttonforge.document.Document
import studio.buttonforge.document.DocumentInvariants
import studio.buttonforge.document.appearance.Appearance
import studio.buttonforge.document.appearance.Color
import studio.buttonforge.document.appearance.Fill
import studio.buttonforge.document.appearance.Paint
import studio.buttonforge.document.layers.LayerId
import studio.buttonforge.document.layers.ShapeLayer
import studio.buttonforge.document.layers.findLayer
import studio.buttonforge.document.layers.updateShape
import studio.buttonforge.document.shape.Transform
import studio.buttonforge.geometry.math.Vec2
import studio.buttonforge.geometry.ops.BooleanFailure
import studio.buttonforge.geometry.ops.BooleanOp
import studio.buttonforge.geometry.ops.BooleanOps
import studio.buttonforge.geometry.ops.BooleanOptions
import studio.buttonforge.geometry.ops.BooleanResult
import studio.buttonforge.geometry.path.CompoundPath
import studio.buttonforge.geometry.primitives.Primitives
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BooleanWiringTest {
    private class FakeOps(val respond: (BooleanOp, CompoundPath, CompoundPath, BooleanOptions) -> BooleanResult) : BooleanOps {
        val calls = ArrayList<Triple<BooleanOp, CompoundPath, CompoundPath>>()
        override fun apply(op: BooleanOp, a: CompoundPath, b: CompoundPath, options: BooleanOptions): BooleanResult {
            calls += Triple(op, a, b)
            return respond(op, a, b, options)
        }
    }

    private val look = Appearance(fills = listOf(Fill(Paint.Solid(Color(0xFFFF0000.toInt())))))

    private fun twoShapes(ops: BooleanOps?): Triple<EditorSession, LayerId, LayerId> {
        val s = EditorSession(Document("d", CanvasSize(1000.0, 1000.0)))
        s.booleanOps = ops
        val below = s.addShapeLayer("Below", look) { next -> Primitives.circle(Vec2.ZERO, 100.0, next) }
        val top = s.addShapeLayer("Top", Appearance()) { next -> Primitives.circle(Vec2(30.0, 0.0), 50.0, next) }
        return Triple(s, below, top)
    }

    private fun success(options: BooleanOptions) =
        BooleanResult.Success(Primitives.rectangle(Vec2.ZERO, 10.0, 10.0, options.nextId!!))

    @Test
    fun aSuccessfulOperationReplacesBothShapesWithOneAndIsOneUndoStep() {
        val ops = FakeOps { _, _, _, o -> success(o) }
        val (s, below, top) = twoShapes(ops)
        val before = s.document
        assertNull(s.booleanWithBelow(top, BooleanOp.Union))

        assertEquals(1, s.document.layers.size)
        val result = s.document.findLayer(below) as ShapeLayer
        assertEquals(look, result.shape.appearance, "the lower shape keeps its look")
        assertEquals(4, result.shape.geometry.contours.single().anchors.size)
        assertEquals(below, s.state.selectedLayer)
        assertEquals(emptyList(), DocumentInvariants.check(s.document))

        val counters = s.document.counters
        assertTrue(s.undo())
        assertEquals(before.layers, s.document.layers)
        assertEquals(counters, s.document.counters, "ids are not handed out again")
        assertTrue(s.redo())
        assertEquals(1, s.document.layers.size)
    }

    @Test
    fun theOperandsArePassedInTheLowerShapesCoordinatesAndInTheRightOrder() {
        val ops = FakeOps { _, _, _, o -> success(o) }
        val (s, below, top) = twoShapes(ops)
        s.updateLayers("move") {
            it.updateShape(below) { sh -> sh.copy(transform = Transform(translation = Vec2(200.0, 0.0))) }
                .updateShape(top) { sh -> sh.copy(transform = Transform(translation = Vec2(260.0, 0.0))) }
        }
        val belowGeometry = (s.document.findLayer(below) as ShapeLayer).shape.geometry
        s.booleanWithBelow(top, BooleanOp.Subtract)

        val (op, a, b) = ops.calls.single()
        assertEquals(BooleanOp.Subtract, op)
        assertEquals(belowGeometry, a, "first operand is the lower shape")
        // the top circle is centred at local (30, 0) plus its offset (260) = world (290, 0); the lower shape sits at 200
        val xs = b.contours.single().anchors.map { it.position.x }
        assertEquals(290.0 - 200.0, (xs.min() + xs.max()) / 2, 1e-9)
    }

    @Test
    fun aFailureOrAnEmptyResultChangesNothing() {
        val failing = FakeOps { _, _, _, _ -> BooleanResult.Failure(BooleanFailure.Degenerate, "tangent edges") }
        val (s, _, top) = twoShapes(failing)
        val before = s.document
        val message = s.booleanWithBelow(top, BooleanOp.Intersect)
        assertNotNull(message)
        assertTrue(message.contains("Degenerate") && message.contains("tangent edges"), message)
        assertEquals(before, s.document)
        assertEquals(2, s.history.undoSize)

        val empty = FakeOps { _, _, _, _ -> BooleanResult.Success(CompoundPath(emptyList())) }
        s.booleanOps = empty
        assertNotNull(s.booleanWithBelow(top, BooleanOp.Intersect))
        assertEquals(before, s.document)
    }

    @Test
    fun missingOperationsOrMissingNeighbourGiveAMessage() {
        val (noOps, _, top) = twoShapes(null)
        assertNotNull(noOps.booleanWithBelow(top, BooleanOp.Union))

        val ops = FakeOps { _, _, _, o -> success(o) }
        val (s, below, _) = twoShapes(ops)
        assertNotNull(s.booleanWithBelow(below, BooleanOp.Union), "nothing below the bottom layer")
        assertNotNull(s.booleanWithBelow(LayerId(999), BooleanOp.Union))
        assertTrue(ops.calls.isEmpty())
    }
}
