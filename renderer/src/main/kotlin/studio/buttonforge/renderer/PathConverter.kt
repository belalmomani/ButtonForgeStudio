package studio.buttonforge.renderer

import android.graphics.Path
import studio.buttonforge.geometry.path.CompoundPath
import studio.buttonforge.geometry.path.FillRule

object PathConverter {
    fun toPath(geometry: CompoundPath): Path {
        val path = Path()
        path.fillType = if (geometry.fillRule == FillRule.EvenOdd) Path.FillType.EVEN_ODD else Path.FillType.WINDING
        for (contour in geometry.contours) {
            if (contour.anchors.size < 2) continue
            val first = contour.anchors.first().position
            path.moveTo(first.x.toFloat(), first.y.toFloat())
            for (segment in contour.segments()) {
                path.cubicTo(
                    segment.p1.x.toFloat(), segment.p1.y.toFloat(),
                    segment.p2.x.toFloat(), segment.p2.y.toFloat(),
                    segment.p3.x.toFloat(), segment.p3.y.toFloat(),
                )
            }
            if (contour.closed) path.close()
        }
        return path
    }
}
