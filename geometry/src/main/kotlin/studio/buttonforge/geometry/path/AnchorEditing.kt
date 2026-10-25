package studio.buttonforge.geometry.path

import studio.buttonforge.geometry.math.Vec2

enum class HandleSide { In, Out }

/**
 * Moves one handle and keeps the other consistent with the anchor's corner type:
 * Sharp leaves the other handle alone, Smooth keeps its length but points it opposite, Symmetric mirrors it exactly.
 */
fun Anchor.withHandle(side: HandleSide, handle: Vec2): Anchor {
    val moved = if (side == HandleSide.In) copy(handleIn = handle) else copy(handleOut = handle)
    val other = if (side == HandleSide.In) handleOut else handleIn
    val opposite = when (corner) {
        CornerType.Sharp -> return moved
        CornerType.Symmetric -> handle * -1.0
        CornerType.Smooth -> {
            val otherLength = other.length()
            val length = handle.length()
            if (otherLength == 0.0 || length == 0.0) other else handle * (-otherLength / length)
        }
    }
    // adding 0.0 turns -0.0 into 0.0, so an unchanged handle compares equal to its original
    val clean = Vec2(opposite.x + 0.0, opposite.y + 0.0)
    return if (side == HandleSide.In) moved.copy(handleOut = clean) else moved.copy(handleIn = clean)
}

private fun unit(v: Vec2): Vec2 {
    val l = v.length()
    return if (l == 0.0) v else Vec2(v.x / l, v.y / l)
}

/**
 * Changes the corner type of one anchor. Sharp only relabels it. Smooth and Symmetric line the handles up (and, for
 * Symmetric, equalise them), so the curve around the anchor changes. An anchor without handles gets them from its
 * neighbours (a sixth of the span between them), the same rule as [studio.buttonforge.geometry.ops.CurveRefit.interpolate].
 */
fun Contour.withCornerType(index: Int, type: CornerType): Contour {
    require(index in anchors.indices) { "No anchor at index $index" }
    val a = anchors[index]
    if (a.corner == type) return this
    val n = anchors.size
    val hasIn = closed || index > 0
    val hasOut = closed || index < n - 1
    val prev = if (closed) anchors[(index - 1 + n) % n] else anchors[maxOf(index - 1, 0)]
    val next = if (closed) anchors[(index + 1) % n] else anchors[minOf(index + 1, n - 1)]
    val auto = (next.position - prev.position) * (1.0 / 6.0)
    val updated = when (type) {
        CornerType.Sharp -> a.copy(corner = CornerType.Sharp)
        CornerType.Smooth, CornerType.Symmetric -> {
            val master = when {
                hasOut && a.handleOut != Vec2.ZERO -> a.handleOut
                hasIn && a.handleIn != Vec2.ZERO -> a.handleIn * -1.0
                else -> auto
            }
            val dir = unit(master)
            val outLength = if (a.handleOut.length() > 0.0) a.handleOut.length() else auto.length()
            val inLength = if (a.handleIn.length() > 0.0) a.handleIn.length() else auto.length()
            val (lengthOut, lengthIn) = if (type == CornerType.Symmetric) {
                val both = listOf(outLength, inLength).filterIndexed { i, _ -> if (i == 0) hasOut else hasIn }
                val common = if (both.isEmpty()) 0.0 else both.average()
                common to common
            } else {
                outLength to inLength
            }
            a.copy(
                handleOut = if (hasOut) dir * lengthOut else Vec2.ZERO,
                handleIn = if (hasIn) dir * -lengthIn else Vec2.ZERO,
                corner = type,
            )
        }
    }
    val list = anchors.toMutableList()
    list[index] = updated
    return copy(anchors = list)
}
