package studio.buttonforge.geometry

import studio.buttonforge.geometry.math.Vec2
import studio.buttonforge.geometry.path.Anchor
import studio.buttonforge.geometry.path.AnchorId
import studio.buttonforge.geometry.path.Contour
import studio.buttonforge.geometry.path.CornerType
import studio.buttonforge.geometry.path.HandleSide
import studio.buttonforge.geometry.path.withCornerType
import studio.buttonforge.geometry.path.withHandle
import studio.buttonforge.geometry.primitives.Primitives
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class AnchorEditingTest {
    private fun anchor(type: CornerType, hi: Vec2, ho: Vec2) =
        Anchor(AnchorId(1), Vec2(10.0, 10.0), hi, ho, type)

    private fun cross(a: Vec2, b: Vec2) = a.x * b.y - a.y * b.x
    private fun dot(a: Vec2, b: Vec2) = a.x * b.x + a.y * b.y

    @Test
    fun sharpAnchorsMoveOneHandleOnly() {
        val a = anchor(CornerType.Sharp, Vec2(-5.0, 0.0), Vec2(5.0, 0.0)).withHandle(HandleSide.Out, Vec2(0.0, 9.0))
        assertEquals(Vec2(0.0, 9.0), a.handleOut)
        assertEquals(Vec2(-5.0, 0.0), a.handleIn)
    }

    @Test
    fun smoothAnchorsKeepTheOtherHandleLengthButPointItOpposite() {
        val a = anchor(CornerType.Smooth, Vec2(-3.0, 0.0), Vec2(3.0, 0.0)).withHandle(HandleSide.Out, Vec2(0.0, 8.0))
        assertEquals(3.0, a.handleIn.length(), 1e-12)
        assertTrue(abs(cross(a.handleIn, a.handleOut)) < 1e-9, "handles stay on one line")
        assertTrue(dot(a.handleIn, a.handleOut) < 0.0, "handles point opposite ways")
        assertEquals(Vec2(0.0, 8.0), a.handleOut)
    }

    @Test
    fun symmetricAnchorsMirrorTheOtherHandle() {
        val a = anchor(CornerType.Symmetric, Vec2(-3.0, 0.0), Vec2(3.0, 0.0)).withHandle(HandleSide.In, Vec2(-4.0, 2.0))
        assertEquals(Vec2(4.0, -2.0), a.handleOut)
    }

    @Test
    fun smoothWithAZeroHandleLeavesTheOtherAlone() {
        val a = anchor(CornerType.Smooth, Vec2.ZERO, Vec2(3.0, 0.0)).withHandle(HandleSide.Out, Vec2(0.0, 5.0))
        assertEquals(Vec2.ZERO, a.handleIn)
    }

    @Test
    fun makingAStarCornerSmoothGivesItAlignedHandlesAndDoesNotMoveTheAnchor() {
        val star = Primitives.star(Vec2.ZERO, 100.0, 40.0, 5, IdSource()::nextId).contours.single()
        val smooth = star.withCornerType(0, CornerType.Smooth)
        val a = smooth.anchors[0]
        assertEquals(star.anchors[0].position, a.position)
        assertEquals(CornerType.Smooth, a.corner)
        assertTrue(a.handleOut.length() > 0.0 && a.handleIn.length() > 0.0)
        assertTrue(abs(cross(a.handleIn, a.handleOut)) < 1e-9)
        assertTrue(dot(a.handleIn, a.handleOut) < 0.0)
        assertEquals(star.anchors.drop(1), smooth.anchors.drop(1), "other anchors are untouched")
    }

    @Test
    fun symmetricMakesBothHandlesEqualLength() {
        val c = Contour(
            listOf(
                Anchor(AnchorId(1), Vec2(0.0, 0.0), Vec2(-2.0, 0.0), Vec2(6.0, 0.0), CornerType.Smooth),
                Anchor(AnchorId(2), Vec2(50.0, 0.0)),
                Anchor(AnchorId(3), Vec2(50.0, 50.0)),
            ),
            closed = true,
        )
        val a = c.withCornerType(0, CornerType.Symmetric).anchors[0]
        assertEquals(a.handleIn.length(), a.handleOut.length(), 1e-12)
        assertEquals(4.0, a.handleOut.length(), 1e-12)
    }

    @Test
    fun sharpOnlyRelabelsAndTheEndsOfAnOpenContourKeepOneHandle() {
        val open = Contour(
            listOf(
                Anchor(AnchorId(1), Vec2(0.0, 0.0)),
                Anchor(AnchorId(2), Vec2(50.0, 0.0)),
                Anchor(AnchorId(3), Vec2(100.0, 40.0)),
            ),
            closed = false,
        )
        val smoothEnd = open.withCornerType(0, CornerType.Smooth).anchors[0]
        assertEquals(Vec2.ZERO, smoothEnd.handleIn)
        assertTrue(smoothEnd.handleOut.length() > 0.0)

        val circle = TestShapes.circle(100.0)
        val sharp = circle.withCornerType(1, CornerType.Sharp)
        assertEquals(circle.anchors[1].handleIn, sharp.anchors[1].handleIn)
        assertEquals(circle.anchors[1].handleOut, sharp.anchors[1].handleOut)
        assertEquals(CornerType.Sharp, sharp.anchors[1].corner)
    }

    @Test
    fun sameTypeReturnsTheSameContour() {
        val c = TestShapes.circle(100.0)
        assertSame(c, c.withCornerType(0, CornerType.Smooth))
    }
}
