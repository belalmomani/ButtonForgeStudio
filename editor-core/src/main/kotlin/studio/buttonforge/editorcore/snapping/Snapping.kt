package studio.buttonforge.editorcore.snapping

import studio.buttonforge.geometry.math.Vec2
import kotlin.math.round

/** What the user switched on. [gridSize] is in world units; [thresholdPx] is how close (on screen) counts as "near" a point. */
data class SnapSettings(
    val gridSize: Double? = null,
    val snapToPoints: Boolean = false,
    val thresholdPx: Double = 12.0,
) {
    init {
        require(gridSize == null || (gridSize > 0.0 && gridSize.isFinite())) { "gridSize must be positive" }
        require(thresholdPx > 0.0) { "thresholdPx must be positive" }
    }
}

/** Settings turned into world units for one gesture: [anchorTolerance] 0 means "do not snap to points". */
data class SnapContext(val gridSize: Double?, val anchorTolerance: Double) {
    val active: Boolean get() = gridSize != null || anchorTolerance > 0.0
}

object Snapper {
    fun snapToGrid(p: Vec2, grid: Double): Vec2 = Vec2(round(p.x / grid) * grid + 0.0, round(p.y / grid) * grid + 0.0)

    /** A nearby point (within [tolerance]) wins; otherwise the grid; otherwise [p] unchanged. */
    fun snap(p: Vec2, grid: Double?, points: List<Vec2>, tolerance: Double): Vec2 {
        if (tolerance > 0.0) {
            var best: Vec2? = null
            var bestDistance = tolerance
            for (q in points) {
                val d = q.distanceTo(p)
                if (d <= bestDistance) {
                    bestDistance = d
                    best = q
                }
            }
            if (best != null) return best
        }
        return if (grid != null) snapToGrid(p, grid) else p
    }
}
