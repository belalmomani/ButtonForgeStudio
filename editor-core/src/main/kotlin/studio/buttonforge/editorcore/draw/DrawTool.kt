package studio.buttonforge.editorcore.draw

import studio.buttonforge.geometry.math.Vec2

enum class DrawTool { None, Freehand, Pen }

/** One pen click. A drag after the click pulls [handleOut]; no drag means a sharp corner. */
data class PenPoint(val position: Vec2, val handleOut: Vec2 = Vec2.ZERO)
