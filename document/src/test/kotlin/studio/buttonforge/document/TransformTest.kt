package studio.buttonforge.document

import studio.buttonforge.document.shape.Transform
import studio.buttonforge.geometry.math.Vec2
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TransformTest {
    @Test
    fun worldToLocalInvertsLocalToWorld() {
        val rnd = Random(5)
        repeat(100) {
            val t = Transform(
                Vec2(rnd.nextDouble(-50.0, 50.0), rnd.nextDouble(-50.0, 50.0)),
                rnd.nextDouble(-3.0, 3.0),
                Vec2(rnd.nextDouble(0.2, 3.0), rnd.nextDouble(0.2, 3.0)),
            )
            val p = Vec2(rnd.nextDouble(-100.0, 100.0), rnd.nextDouble(-100.0, 100.0))
            val back = t.toLocal(t.toWorld(p))
            assertTrue(back != null && back.distanceTo(p) < 1e-9)
        }
    }

    @Test
    fun identityTransformIsExact() {
        val v = Vec2(0.123456789, -9.87654321)
        assertEquals(v, Transform().vectorToLocal(v))
    }

    @Test
    fun zeroScaleCannotBeInverted() {
        val t = Transform(scale = Vec2(0.0, 1.0))
        assertNull(t.toLocal(Vec2(1.0, 1.0)))
        assertNull(t.vectorToLocal(Vec2(1.0, 1.0)))
    }

    @Test
    fun vectorsRoundTripAndIgnoreTranslation() {
        val t = Transform(Vec2(500.0, -300.0), 0.7, Vec2(2.0, 0.5))
        val v = Vec2(3.0, -4.0)
        val back = t.vectorToLocal(t.vectorToWorld(v))
        assertTrue(back != null && back.distanceTo(v) < 1e-9)
        assertEquals(t.toWorld(Vec2(1.0, 1.0)) - t.toWorld(Vec2.ZERO), t.vectorToWorld(Vec2(1.0, 1.0)))
    }
}
