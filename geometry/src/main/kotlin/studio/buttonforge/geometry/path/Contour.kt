package studio.buttonforge.geometry.path

import studio.buttonforge.geometry.bezier.CubicBezier

data class Contour(val anchors: List<Anchor>, val closed: Boolean) {
    init {
        require(anchors.map { it.id }.toSet().size == anchors.size) { "Duplicate anchor ids" }
    }

    val segmentCount: Int
        get() = when {
            anchors.size < 2 -> 0
            closed -> anchors.size
            else -> anchors.size - 1
        }

    fun segment(i: Int): CubicBezier {
        require(i in 0 until segmentCount) { "Segment $i out of range" }
        val a = anchors[i]
        val b = anchors[(i + 1) % anchors.size]
        return CubicBezier(
            a.position,
            a.position + a.handleOut,
            b.position + b.handleIn,
            b.position,
        )
    }

    fun segments(): List<CubicBezier> = List(segmentCount) { segment(it) }
}
