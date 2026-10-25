package studio.buttonforge.geometry

import studio.buttonforge.geometry.hittest.HitTester
import studio.buttonforge.geometry.math.Vec2
import studio.buttonforge.geometry.path.CompoundPath
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HitTesterTest {
    private val circle = CompoundPath(listOf(TestShapes.circle(100.0, IdSource(1))))

    @Test
    fun anchorHitRespectsTolerance() {
        assertNull(HitTester.nearestAnchor(circle, Vec2(105.0, 0.0), 4.0))
        val hit = HitTester.nearestAnchor(circle, Vec2(105.0, 0.0), 6.0)
        assertNotNull(hit)
        assertEquals(1L, hit.id.value)
    }

    @Test
    fun nearestAnchorWinsAndTiesGoToTheLowerId() {
        val equidistant = HitTester.nearestAnchor(circle, Vec2(70.0, 70.0), 200.0)
        assertNotNull(equidistant)
        assertEquals(1L, equidistant.id.value) // (100,0) and (0,100) are equally far from (70,70)
    }

    @Test
    fun segmentHitFindsTheRightSegmentAndParameter() {
        val hit = HitTester.nearestSegment(circle, Vec2(70.7107, 70.7107), 1.0)
        assertNotNull(hit)
        assertEquals(0, hit.segmentIndex)
        assertTrue(abs(hit.t - 0.5) < 0.01, "t=${hit.t}")
        assertTrue(hit.distance < 0.5)
    }

    @Test
    fun farPointsHitNothing() {
        assertNull(HitTester.nearestSegment(circle, Vec2(0.0, 0.0), 10.0))
        assertNull(HitTester.nearestAnchor(circle, Vec2(0.0, 0.0), 10.0))
    }

    @Test
    fun containsFollowsTheShape() {
        assertTrue(HitTester.contains(circle, Vec2(0.0, 0.0)))
        assertTrue(HitTester.contains(circle, Vec2(60.0, 60.0)))
        assertTrue(!HitTester.contains(circle, Vec2(120.0, 0.0)))
        assertTrue(!HitTester.contains(circle, Vec2(75.0, 75.0))) // outside: distance 106
        val square = CompoundPath(listOf(TestShapes.square(100.0, IdSource(1))))
        assertTrue(HitTester.contains(square, Vec2(50.0, 50.0)))
        assertTrue(!HitTester.contains(square, Vec2(150.0, 50.0)))
        assertTrue(!HitTester.contains(square, Vec2(-1.0, 50.0)))
    }

    @Test
    fun fillRuleDecidesWhetherASameDirectionHoleIsFilled() {
        val outer = TestShapes.circle(100.0, IdSource(1))
        val inner = TestShapes.circle(40.0, IdSource(100))
        val nonZero = CompoundPath(listOf(outer, inner), studio.buttonforge.geometry.path.FillRule.NonZero)
        val evenOdd = CompoundPath(listOf(outer, inner), studio.buttonforge.geometry.path.FillRule.EvenOdd)
        assertTrue(HitTester.contains(nonZero, Vec2(0.0, 0.0)))
        assertTrue(!HitTester.contains(evenOdd, Vec2(0.0, 0.0)))
        assertTrue(HitTester.contains(evenOdd, Vec2(70.0, 0.0)))
    }
}
