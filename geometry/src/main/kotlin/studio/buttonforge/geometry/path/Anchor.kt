package studio.buttonforge.geometry.path

import studio.buttonforge.geometry.math.Vec2

/** Stable identifier. Allocated by the document; never reused after deletion. */
@JvmInline
value class AnchorId(val value: Long)

enum class CornerType { Sharp, Smooth, Symmetric }

/** Handles are offsets relative to [position]. Zero handles on both sides mean a straight corner. */
data class Anchor(
    val id: AnchorId,
    val position: Vec2,
    val handleIn: Vec2 = Vec2.ZERO,
    val handleOut: Vec2 = Vec2.ZERO,
    val corner: CornerType = CornerType.Sharp,
) {
    init {
        require(position.isFinite && handleIn.isFinite && handleOut.isFinite) { "Anchor ${id.value} has non-finite values" }
    }
}
