package studio.buttonforge.geometry

import studio.buttonforge.geometry.math.Vec2
import studio.buttonforge.geometry.path.Anchor
import studio.buttonforge.geometry.path.AnchorId
import studio.buttonforge.geometry.path.Contour
import studio.buttonforge.geometry.path.CornerType
import kotlin.random.Random

class IdSource(start: Long = 1000) {
    private var next = start
    fun nextId() = AnchorId(next++)
}

object TestShapes {
    private const val KAPPA = 0.5522847498307936

    /** Circle as 4 cubic arcs (max radial error about 0.03% of the radius). */
    fun circle(radius: Double = 100.0, ids: IdSource = IdSource(1)): Contour {
        val k = radius * KAPPA
        return Contour(
            listOf(
                Anchor(ids.nextId(), Vec2(radius, 0.0), Vec2(0.0, -k), Vec2(0.0, k), CornerType.Smooth),
                Anchor(ids.nextId(), Vec2(0.0, radius), Vec2(k, 0.0), Vec2(-k, 0.0), CornerType.Smooth),
                Anchor(ids.nextId(), Vec2(-radius, 0.0), Vec2(0.0, k), Vec2(0.0, -k), CornerType.Smooth),
                Anchor(ids.nextId(), Vec2(0.0, -radius), Vec2(-k, 0.0), Vec2(k, 0.0), CornerType.Smooth),
            ),
            closed = true,
        )
    }

    fun square(side: Double = 100.0, ids: IdSource = IdSource(1)): Contour = Contour(
        listOf(
            Anchor(ids.nextId(), Vec2(0.0, 0.0)),
            Anchor(ids.nextId(), Vec2(side, 0.0)),
            Anchor(ids.nextId(), Vec2(side, side)),
            Anchor(ids.nextId(), Vec2(0.0, side)),
        ),
        closed = true,
    )

    /** Random closed contour with curved segments; same seed gives the same shape. */
    fun random(seed: Long, anchors: Int = 8, scale: Double = 100.0, ids: IdSource = IdSource(1)): Contour {
        val rnd = Random(seed)
        val list = List(anchors) { i ->
            val angle = 2.0 * Math.PI * i / anchors
            val r = scale * (0.6 + 0.4 * rnd.nextDouble())
            Anchor(
                ids.nextId(),
                Vec2(r * Math.cos(angle), r * Math.sin(angle)),
                Vec2((rnd.nextDouble() - 0.5) * scale * 0.4, (rnd.nextDouble() - 0.5) * scale * 0.4),
                Vec2((rnd.nextDouble() - 0.5) * scale * 0.4, (rnd.nextDouble() - 0.5) * scale * 0.4),
                CornerType.Sharp,
            )
        }
        return Contour(list, closed = true)
    }
}
