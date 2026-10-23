package studio.buttonforge.geometry.path

enum class FillRule { NonZero, EvenOdd }

/** Several contours (holes are contours too) plus the fill rule that interprets them. */
data class CompoundPath(val contours: List<Contour>, val fillRule: FillRule = FillRule.NonZero)
