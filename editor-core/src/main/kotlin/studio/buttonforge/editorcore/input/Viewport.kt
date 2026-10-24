package studio.buttonforge.editorcore.input

import studio.buttonforge.geometry.math.Vec2
import kotlin.math.min

/** Pan and zoom. Immutable; changing the view never touches the document. */
data class Viewport(
    override val scale: Double = 1.0,
    val offsetX: Double = 0.0,
    val offsetY: Double = 0.0,
) : ViewportTransform {
    init {
        require(scale in MIN_SCALE..MAX_SCALE) { "scale $scale outside $MIN_SCALE..$MAX_SCALE" }
    }

    override fun toWorld(screen: Vec2): Vec2 = Vec2((screen.x - offsetX) / scale, (screen.y - offsetY) / scale)

    fun toScreen(world: Vec2): Vec2 = Vec2(world.x * scale + offsetX, world.y * scale + offsetY)

    fun panned(dx: Double, dy: Double): Viewport = copy(offsetX = offsetX + dx, offsetY = offsetY + dy)

    /** Zooms by [factor] keeping the world point under screen position ([cx], [cy]) fixed. Clamped to the limits. */
    fun zoomedAbout(cx: Double, cy: Double, factor: Double): Viewport {
        if (factor <= 0.0 || !factor.isFinite()) return this
        val newScale = (scale * factor).coerceIn(MIN_SCALE, MAX_SCALE)
        val k = newScale / scale
        return Viewport(newScale, cx - (cx - offsetX) * k, cy - (cy - offsetY) * k)
    }

    companion object {
        const val MIN_SCALE = 0.05
        const val MAX_SCALE = 64.0

        /** Centres a canvas of [contentWidth] x [contentHeight] inside a view of [viewWidth] x [viewHeight]. */
        fun fit(contentWidth: Double, contentHeight: Double, viewWidth: Double, viewHeight: Double, margin: Double = 0.9): Viewport {
            val s = (min(viewWidth / contentWidth, viewHeight / contentHeight) * margin).coerceIn(MIN_SCALE, MAX_SCALE)
            return Viewport(s, (viewWidth - contentWidth * s) / 2.0, (viewHeight - contentHeight * s) / 2.0)
        }
    }
}
