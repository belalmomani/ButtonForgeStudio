package studio.buttonforge.geometry.ops

import studio.buttonforge.geometry.bezier.ArcLength
import studio.buttonforge.geometry.path.Contour
import studio.buttonforge.geometry.path.SegmentParam

/**
 * Operation 2: positions evenly spaced by arc length. Read-only: returns positions, never edits the contour.
 * Closed contour: [count] points, spacing L/count, the first at the start. Open contour: spacing L/(count-1), includes both ends.
 * Returns an empty list for contours with (near) zero length.
 */
object ArcLengthDistribution {
    private const val MIN_LENGTH = 1e-12

    fun distribute(contour: Contour, count: Int): List<SegmentParam> {
        require(count >= 1) { "count must be >= 1" }
        if (!contour.closed) require(count >= 2) { "open contour needs count >= 2" }
        val segments = contour.segments()
        if (segments.isEmpty()) return emptyList()
        val lengths = segments.map { ArcLength.length(it) }
        val total = lengths.sum()
        if (total < MIN_LENGTH) return emptyList()
        val step = if (contour.closed) total / count else total / (count - 1)

        val result = ArrayList<SegmentParam>(count)
        var segIdx = 0
        var segStart = 0.0
        for (k in 0 until count) {
            val s = k * step
            while (segIdx < segments.lastIndex && s > segStart + lengths[segIdx]) {
                segStart += lengths[segIdx]
                segIdx++
            }
            val local = (s - segStart).coerceIn(0.0, lengths[segIdx])
            val t = ArcLength.parameterAtLength(segments[segIdx], local, lengths[segIdx])
            result += SegmentParam(segIdx, t)
        }
        return result
    }

    /**
     * Positions of the extra anchors needed to bring the contour to exactly [targetCount] anchors, spaced as
     * evenly as possible by arc length. Existing anchors are never duplicated. Read-only, like [distribute].
     * Returns an empty list when nothing is needed or the contour has no length.
     */
    fun fillTo(contour: Contour, targetCount: Int): List<SegmentParam> {
        require(targetCount >= contour.anchors.size) { "targetCount ${targetCount} is below the current ${contour.anchors.size} anchors" }
        val missing = targetCount - contour.anchors.size
        val segments = contour.segments()
        if (missing == 0 || segments.isEmpty()) return emptyList()
        val lengths = segments.map { ArcLength.length(it) }
        if (lengths.sum() < MIN_LENGTH) return emptyList()

        // Give each new point to the segment whose pieces are currently longest.
        val extra = IntArray(segments.size)
        repeat(missing) {
            var best = 0
            for (i in segments.indices) {
                if (lengths[i] / (extra[i] + 1) > lengths[best] / (extra[best] + 1)) best = i
            }
            extra[best]++
        }
        val result = ArrayList<SegmentParam>(missing)
        for (i in segments.indices) {
            for (j in 1..extra[i]) {
                val s = j * lengths[i] / (extra[i] + 1)
                result += SegmentParam(i, ArcLength.parameterAtLength(segments[i], s, lengths[i]))
            }
        }
        return result
    }
}
