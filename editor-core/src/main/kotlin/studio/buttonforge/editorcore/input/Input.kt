package studio.buttonforge.editorcore.input

import studio.buttonforge.geometry.math.Vec2

/** Supplied by the UI layer. Keeps this module free of Android types. */
interface ViewportTransform {
    /** Screen pixels per world unit. */
    val scale: Double
    fun toWorld(screen: Vec2): Vec2
}

enum class PointerType { Down, Move, Up, Cancel }

data class PointerEvent(val type: PointerType, val screen: Vec2)
