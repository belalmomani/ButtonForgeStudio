package studio.buttonforge.geometry.path

import studio.buttonforge.geometry.bezier.CubicBezier
import studio.buttonforge.geometry.math.Vec2

/** A position on a contour: segment index and curve parameter. Produced by distribution, consumed by subdivision. */
data class SegmentParam(val segmentIndex: Int, val t: Double)

/**
 * Operation 1 applied to the anchor model: inserts anchors without changing the shape.
 * Neighbouring anchors keep their ids; new anchors get ids from [nextId].
 * A Symmetric neighbour becomes Smooth because its handle lengths no longer match.
 */
fun Contour.withInsertedAnchors(params: List<SegmentParam>, nextId: () -> AnchorId): Contour {
    if (params.isEmpty()) return this
    require(params.all { it.segmentIndex in 0 until segmentCount && it.t > 0.0 && it.t < 1.0 }) {
        "Invalid segment parameter"
    }
    val bySegment = params.groupBy { it.segmentIndex }.mapValues { (_, v) -> v.map { it.t }.sorted() }
    val out = ArrayList<Anchor>(anchors.size + params.size)
    // Mutable working copies so the end anchor's handleIn of a split segment can be updated.
    val working = anchors.toMutableList()
    val inserted = HashMap<Int, List<Anchor>>()

    for ((segIdx, ts) in bySegment) {
        val startIdx = segIdx
        val endIdx = (segIdx + 1) % anchors.size
        var curve: CubicBezier = segment(segIdx)
        var prevT = 0.0
        var firstHandleOut: Vec2? = null
        var lastHandleIn: Vec2? = null
        val newAnchors = ArrayList<Anchor>()
        var carryOut: Vec2? = null // handleOut to give to the anchor created at the previous split
        for (t in ts) {
            val local = (t - prevT) / (1.0 - prevT)
            val (left, right) = curve.split(local)
            // 'left' ends at the new anchor.
            val handleIn = left.p2 - left.p3
            val handleOut = right.p1 - right.p0
            if (newAnchors.isEmpty()) {
                firstHandleOut = left.p1 - left.p0
            } else {
                // previous new anchor's handleOut is the first control of 'left'
                val prev = newAnchors.last()
                newAnchors[newAnchors.lastIndex] = prev.copy(handleOut = left.p1 - left.p0)
            }
            newAnchors += Anchor(nextId(), left.p3, handleIn, handleOut, CornerType.Smooth)
            curve = right
            prevT = t
            carryOut = handleOut
        }
        lastHandleIn = curve.p2 - curve.p3
        val startAnchor = working[startIdx]
        working[startIdx] = startAnchor.copy(
            handleOut = firstHandleOut!!,
            corner = if (startAnchor.corner == CornerType.Symmetric) CornerType.Smooth else startAnchor.corner,
        )
        val endAnchor = working[endIdx]
        working[endIdx] = endAnchor.copy(
            handleIn = lastHandleIn,
            corner = if (endAnchor.corner == CornerType.Symmetric) CornerType.Smooth else endAnchor.corner,
        )
        inserted[segIdx] = newAnchors
    }

    for (i in working.indices) {
        out += working[i]
        inserted[i]?.let { out += it }
    }
    return Contour(out, closed)
}
