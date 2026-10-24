package studio.buttonforge.document.appearance

import studio.buttonforge.geometry.math.Vec2

enum class StrokeCap { Butt, Round, Square }
enum class StrokeJoin { Miter, Round, Bevel }

enum class BlendMode { Normal, Multiply, Screen, Overlay, Darken, Lighten }

data class Fill(val paint: Paint, val opacity: Double = 1.0, val enabled: Boolean = true) {
    init { require(opacity in 0.0..1.0) { "Fill opacity must be in [0,1]" } }
}

data class Stroke(
    val paint: Paint,
    val width: Double = 1.0,
    val cap: StrokeCap = StrokeCap.Butt,
    val join: StrokeJoin = StrokeJoin.Miter,
    val miterLimit: Double = 4.0,
    val dash: List<Double> = emptyList(),
    val opacity: Double = 1.0,
    val enabled: Boolean = true,
) {
    init {
        require(width >= 0.0 && width.isFinite()) { "Stroke width must be finite and >= 0" }
        require(miterLimit >= 1.0) { "Miter limit must be >= 1" }
        require(dash.all { it >= 0.0 && it.isFinite() }) { "Dash values must be finite and >= 0" }
        require(opacity in 0.0..1.0) { "Stroke opacity must be in [0,1]" }
    }
}

sealed interface Effect {
    data class DropShadow(val offset: Vec2, val blur: Double, val spread: Double, val color: Color) : Effect {
        init { require(blur >= 0.0) { "Blur must be >= 0" } }
    }

    data class InnerShadow(val offset: Vec2, val blur: Double, val spread: Double, val color: Color) : Effect {
        init { require(blur >= 0.0) { "Blur must be >= 0" } }
    }

    data class LayerBlur(val radius: Double) : Effect {
        init { require(radius >= 0.0) { "Radius must be >= 0" } }
    }

    data class Glow(val radius: Double, val color: Color, val intensity: Double = 1.0) : Effect {
        init {
            require(radius >= 0.0) { "Radius must be >= 0" }
            require(intensity >= 0.0) { "Intensity must be >= 0" }
        }
    }
}

/** Everything about how a shape looks. Never contains geometry. */
data class Appearance(
    val fills: List<Fill> = emptyList(),
    val strokes: List<Stroke> = emptyList(),
    val effects: List<Effect> = emptyList(),
    val opacity: Double = 1.0,
    val blendMode: BlendMode = BlendMode.Normal,
) {
    init { require(opacity in 0.0..1.0) { "Opacity must be in [0,1]" } }
}
