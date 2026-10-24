package studio.buttonforge.geometry.primitives

import studio.buttonforge.geometry.math.Vec2
import studio.buttonforge.geometry.path.Anchor
import studio.buttonforge.geometry.path.AnchorId
import studio.buttonforge.geometry.path.CompoundPath
import studio.buttonforge.geometry.path.Contour
import studio.buttonforge.geometry.path.CornerType
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Ready-made shapes. Each returns ordinary editable anchors; nothing about the generator is remembered. */
object Primitives {
    private const val KAPPA = 0.5522847498307936

    /** Four cubic arcs (largest radial error about 0.03% of the radius). */
    fun circle(center: Vec2, radius: Double, nextId: () -> AnchorId): CompoundPath {
        require(radius > 0.0 && radius.isFinite()) { "radius must be positive" }
        val k = radius * KAPPA
        val (cx, cy) = center
        val anchors = listOf(
            Anchor(nextId(), Vec2(cx + radius, cy), Vec2(0.0, -k), Vec2(0.0, k), CornerType.Smooth),
            Anchor(nextId(), Vec2(cx, cy + radius), Vec2(k, 0.0), Vec2(-k, 0.0), CornerType.Smooth),
            Anchor(nextId(), Vec2(cx - radius, cy), Vec2(0.0, k), Vec2(0.0, -k), CornerType.Smooth),
            Anchor(nextId(), Vec2(cx, cy - radius), Vec2(-k, 0.0), Vec2(k, 0.0), CornerType.Smooth),
        )
        return CompoundPath(listOf(Contour(anchors, closed = true)))
    }

    /** Four sharp corners, clockwise from the top-left (y grows downward). */
    fun rectangle(center: Vec2, width: Double, height: Double, nextId: () -> AnchorId): CompoundPath {
        require(width > 0.0 && height > 0.0 && width.isFinite() && height.isFinite()) { "size must be positive" }
        val l = center.x - width / 2.0
        val r = center.x + width / 2.0
        val t = center.y - height / 2.0
        val b = center.y + height / 2.0
        val anchors = listOf(
            Anchor(nextId(), Vec2(l, t)),
            Anchor(nextId(), Vec2(r, t)),
            Anchor(nextId(), Vec2(r, b)),
            Anchor(nextId(), Vec2(l, b)),
        )
        return CompoundPath(listOf(Contour(anchors, closed = true)))
    }

    /** [points] outer tips and as many inner corners, starting from the top tip. 2 * points sharp anchors. */
    fun star(center: Vec2, outerRadius: Double, innerRadius: Double, points: Int, nextId: () -> AnchorId): CompoundPath {
        require(points >= 3) { "a star needs at least 3 points" }
        require(innerRadius > 0.0 && outerRadius > innerRadius) { "need 0 < innerRadius < outerRadius" }
        val anchors = List(points * 2) { i ->
            val angle = -PI / 2.0 + i * PI / points
            val r = if (i % 2 == 0) outerRadius else innerRadius
            Anchor(nextId(), Vec2(center.x + r * cos(angle), center.y + r * sin(angle)))
        }
        return CompoundPath(listOf(Contour(anchors, closed = true)))
    }
}
