package studio.buttonforge.editorcore

import studio.buttonforge.document.IdCounters
import studio.buttonforge.document.layers.LayerId
import studio.buttonforge.geometry.path.Contour

/**
 * A proposed new contour, shown to the user before it is applied. [maxError] is the measured distance between
 * the old and the new outline, in the shape's own units.
 */
data class ContourReplacement(
    val layerId: LayerId,
    val contourIndex: Int,
    val before: Contour,
    val after: Contour,
    val maxError: Double,
    val countersBefore: IdCounters,
    val countersAfter: IdCounters,
)
