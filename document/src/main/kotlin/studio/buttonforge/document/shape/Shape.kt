package studio.buttonforge.document.shape

import studio.buttonforge.document.appearance.Appearance
import studio.buttonforge.geometry.math.Vec2
import studio.buttonforge.geometry.path.CompoundPath
import kotlin.math.cos
import kotlin.math.sin

data class Transform(
    val translation: Vec2 = Vec2.ZERO,
    val rotationRadians: Double = 0.0,
    val scale: Vec2 = Vec2(1.0, 1.0),
) {
    init {
        require(translation.isFinite && scale.isFinite && rotationRadians.isFinite()) { "Transform must be finite" }
    }

    /** Local to world: scale, then rotate, then translate. */
    fun toWorld(p: Vec2): Vec2 {
        val sx = p.x * scale.x
        val sy = p.y * scale.y
        val c = cos(rotationRadians)
        val s = sin(rotationRadians)
        return Vec2(sx * c - sy * s + translation.x, sx * s + sy * c + translation.y)
    }

    /** Converts a displacement (no translation) from local to world space. */
    fun vectorToWorld(v: Vec2): Vec2 {
        val sx = v.x * scale.x
        val sy = v.y * scale.y
        val c = cos(rotationRadians)
        val s = sin(rotationRadians)
        return Vec2(sx * c - sy * s, sx * s + sy * c)
    }

    /** World to local, or null when the transform cannot be inverted (a zero scale). */
    fun toLocal(p: Vec2): Vec2? = vectorToLocal(p - translation)

    /** Converts a displacement (no translation) from world to local space. Null when not invertible. */
    fun vectorToLocal(v: Vec2): Vec2? {
        if (scale.x == 0.0 || scale.y == 0.0) return null
        val c = cos(rotationRadians)
        val s = sin(rotationRadians)
        return Vec2((v.x * c + v.y * s) / scale.x, (-v.x * s + v.y * c) / scale.y)
    }
}

/** Geometry and appearance live in separate fields; editing one never touches the other. */
data class Shape(
    val geometry: CompoundPath,
    val appearance: Appearance = Appearance(),
    val transform: Transform = Transform(),
)
