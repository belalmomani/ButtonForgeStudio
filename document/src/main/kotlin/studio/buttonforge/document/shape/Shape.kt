package studio.buttonforge.document.shape

import studio.buttonforge.document.appearance.Appearance
import studio.buttonforge.geometry.math.Vec2
import studio.buttonforge.geometry.path.CompoundPath

data class Transform(
    val translation: Vec2 = Vec2.ZERO,
    val rotationRadians: Double = 0.0,
    val scale: Vec2 = Vec2(1.0, 1.0),
) {
    init {
        require(translation.isFinite && scale.isFinite && rotationRadians.isFinite()) { "Transform must be finite" }
    }
}

/** Geometry and appearance live in separate fields; editing one never touches the other. */
data class Shape(
    val geometry: CompoundPath,
    val appearance: Appearance = Appearance(),
    val transform: Transform = Transform(),
)
