package studio.buttonforge.document.appearance

import studio.buttonforge.geometry.math.Vec2

data class GradientStop(val offset: Double, val color: Color) {
    init {
        require(offset in 0.0..1.0) { "Gradient stop offset must be in [0,1]" }
    }
}

private fun validateStops(stops: List<GradientStop>) {
    require(stops.size >= 2) { "A gradient needs at least 2 stops" }
    require(stops.zipWithNext().all { (a, b) -> a.offset <= b.offset }) { "Gradient stops must be ordered by offset" }
}

sealed interface Paint {
    data class Solid(val color: Color) : Paint

    data class LinearGradient(val start: Vec2, val end: Vec2, val stops: List<GradientStop>) : Paint {
        init { validateStops(stops) }
    }

    data class RadialGradient(val center: Vec2, val radius: Double, val stops: List<GradientStop>) : Paint {
        init {
            require(radius > 0.0) { "Radius must be positive" }
            validateStops(stops)
        }
    }

    data class SweepGradient(val center: Vec2, val stops: List<GradientStop>) : Paint {
        init { validateStops(stops) }
    }
}
