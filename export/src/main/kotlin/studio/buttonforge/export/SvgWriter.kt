package studio.buttonforge.export

import studio.buttonforge.document.Document
import studio.buttonforge.document.appearance.BlendMode
import studio.buttonforge.document.appearance.StrokeCap
import studio.buttonforge.document.appearance.StrokeJoin

/** Real vector paths (never an embedded picture). Gradients become SVG gradients; unsupported features are reported. */
internal object SvgWriter {
    private const val TARGET = "SVG"

    fun write(doc: Document, items: List<DrawItem>, report: ReportBuilder, title: String, idPrefix: String = "g", withXmlHeader: Boolean = true): String {
        val defs = StringBuilder()
        val body = StringBuilder()
        var gradients = 0
        val w = Format.num(doc.canvas.width)
        val h = Format.num(doc.canvas.height)

        fun stopsXml(stops: List<FlatStop>) = stops.joinToString("") {
            "<stop offset=\"${Format.num(it.offset)}\" stop-color=\"${Format.rgb(it.argb)}\"" +
                (if (Format.alpha(it.argb) < 1.0) " stop-opacity=\"${Format.num(Format.alpha(it.argb))}\"" else "") + "/>"
        }

        /** Returns the value of the fill/stroke attribute and the extra opacity carried by the colour itself. */
        fun paintValue(item: DrawItem): Pair<String, Double> = when (val p = item.paint) {
            is FlatPaint.Solid -> Format.rgb(p.argb) to Format.alpha(p.argb)
            is FlatPaint.Linear -> {
                val id = "$idPrefix${++gradients}"
                defs.append("<linearGradient id=\"$id\" gradientUnits=\"userSpaceOnUse\" x1=\"${Format.num(p.x1)}\" y1=\"${Format.num(p.y1)}\" x2=\"${Format.num(p.x2)}\" y2=\"${Format.num(p.y2)}\">${stopsXml(p.stops)}</linearGradient>\n")
                "url(#$id)" to 1.0
            }
            is FlatPaint.Radial -> {
                val id = "$idPrefix${++gradients}"
                defs.append("<radialGradient id=\"$id\" gradientUnits=\"userSpaceOnUse\" cx=\"${Format.num(p.cx)}\" cy=\"${Format.num(p.cy)}\" r=\"${Format.num(p.radius)}\">${stopsXml(p.stops)}</radialGradient>\n")
                "url(#$id)" to 1.0
            }
            is FlatPaint.Sweep -> {
                report.add(ReportItem(TARGET, item.layer, "التدرج المخروطي", Fidelity.Approximated, "غير موجود في SVG؛ يُستبدل بلون متوسط"))
                val argb = averageColor(p.stops)
                Format.rgb(argb) to Format.alpha(argb)
            }
        }

        for (item in items) {
            if (item.hasEffects) {
                report.add(ReportItem(TARGET, item.layer, "الظلال والتوهج والتمويه", Fidelity.Dropped, "لا تُكتب في هذه النسخة"))
            }
            val blend = blendName(item.blend)
            if (blend != null) {
                report.add(ReportItem(TARGET, item.layer, "نمط المزج", Fidelity.Approximated, "يعمل في المتصفحات عبر mix-blend-mode ولا تدعمه كل البرامج"))
            }
            val (value, colorAlpha) = paintValue(item)
            val opacity = item.alpha * colorAlpha
            val s = item.stroke
            val attrs = StringBuilder()
            if (s == null) {
                attrs.append(" fill=\"$value\"")
                if (opacity < 1.0) attrs.append(" fill-opacity=\"${Format.num(opacity)}\"")
                if (item.evenOdd) attrs.append(" fill-rule=\"evenodd\"")
            } else {
                attrs.append(" fill=\"none\" stroke=\"$value\" stroke-width=\"${Format.num(s.width)}\"")
                if (opacity < 1.0) attrs.append(" stroke-opacity=\"${Format.num(opacity)}\"")
                if (s.cap != StrokeCap.Butt) attrs.append(" stroke-linecap=\"${s.cap.name.lowercase()}\"")
                if (s.join != StrokeJoin.Miter) attrs.append(" stroke-linejoin=\"${s.join.name.lowercase()}\"")
                if (s.miter != 4.0) attrs.append(" stroke-miterlimit=\"${Format.num(s.miter)}\"")
                if (s.dash.isNotEmpty() && s.dash.sum() > 0.0) {
                    attrs.append(" stroke-dasharray=\"${s.dash.joinToString(" ") { Format.num(it) }}\"")
                }
            }
            if (blend != null) attrs.append(" style=\"mix-blend-mode:$blend\"")
            body.append("<path d=\"${item.pathData}\"$attrs/>\n")
        }

        val sb = StringBuilder()
        if (withXmlHeader) sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        sb.append("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"$w\" height=\"$h\" viewBox=\"0 0 $w $h\">\n")
        sb.append("<title>${Format.escape(title)}</title>\n")
        if (defs.isNotEmpty()) sb.append("<defs>\n").append(defs).append("</defs>\n")
        sb.append(body)
        sb.append("</svg>\n")
        return sb.toString()
    }

    private fun blendName(mode: BlendMode): String? = when (mode) {
        BlendMode.Normal -> null
        else -> mode.name.lowercase()
    }

    fun averageColor(stops: List<FlatStop>): Int {
        if (stops.isEmpty()) return 0
        var a = 0
        var r = 0
        var g = 0
        var b = 0
        for (s in stops) {
            a += s.argb ushr 24
            r += (s.argb shr 16) and 0xFF
            g += (s.argb shr 8) and 0xFF
            b += s.argb and 0xFF
        }
        val n = stops.size
        return ((a / n) shl 24) or ((r / n) shl 16) or ((g / n) shl 8) or (b / n)
    }
}
