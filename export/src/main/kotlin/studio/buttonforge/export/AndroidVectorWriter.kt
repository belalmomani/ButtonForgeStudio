package studio.buttonforge.export

import studio.buttonforge.document.Document
import studio.buttonforge.document.appearance.BlendMode
import studio.buttonforge.document.appearance.StrokeCap
import studio.buttonforge.document.appearance.StrokeJoin

/** Android Studio's own vector format (res/drawable/*.xml). Dash patterns, blend modes and effects cannot be expressed. */
internal object AndroidVectorWriter {
    private const val TARGET = "VectorDrawable"

    fun write(doc: Document, items: List<DrawItem>, report: ReportBuilder): String {
        val w = Format.num(doc.canvas.width)
        val h = Format.num(doc.canvas.height)
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n")
        sb.append("<vector xmlns:android=\"http://schemas.android.com/apk/res/android\"\n")
        sb.append("    xmlns:aapt=\"http://schemas.android.com/aapt\"\n")
        sb.append("    android:width=\"${w}dp\"\n    android:height=\"${h}dp\"\n")
        sb.append("    android:viewportWidth=\"$w\"\n    android:viewportHeight=\"$h\">\n")

        var usedEvenOdd = false
        var usedGradient = false
        for (item in items) {
            if (item.hasEffects) {
                report.add(ReportItem(TARGET, item.layer, "الظلال والتوهج والتمويه", Fidelity.Dropped, "VectorDrawable لا يدعمها"))
            }
            if (item.blend != BlendMode.Normal) {
                report.add(ReportItem(TARGET, item.layer, "نمط المزج", Fidelity.Dropped, "VectorDrawable لا يدعمه"))
            }
            val s = item.stroke
            if (s != null && s.dash.isNotEmpty() && s.dash.sum() > 0.0) {
                report.add(ReportItem(TARGET, item.layer, "الحد المنقط", Fidelity.Dropped, "يصير الحد متصلًا"))
            }
            val side = if (s == null) "fill" else "stroke"
            val attrs = ArrayList<String>()
            attrs += "android:pathData=\"${item.pathData}\""
            val gradient = gradientXml(item.paint)
            when (val p = item.paint) {
                is FlatPaint.Solid -> attrs += "android:${side}Color=\"${Format.argb(p.argb)}\""
                else -> usedGradient = true
            }
            if (item.alpha < 1.0) attrs += "android:${side}Alpha=\"${Format.num(item.alpha)}\""
            if (s == null) {
                if (item.evenOdd) {
                    attrs += "android:fillType=\"evenOdd\""
                    usedEvenOdd = true
                }
            } else {
                attrs += "android:strokeWidth=\"${Format.num(s.width)}\""
                if (s.cap != StrokeCap.Butt) attrs += "android:strokeLineCap=\"${s.cap.name.lowercase()}\""
                if (s.join != StrokeJoin.Miter) attrs += "android:strokeLineJoin=\"${s.join.name.lowercase()}\""
                if (s.miter != 4.0) attrs += "android:strokeMiterLimit=\"${Format.num(s.miter)}\""
            }
            if (gradient == null) {
                sb.append("    <path\n").append(attrs.joinToString("\n") { "        $it" }).append("/>\n")
            } else {
                sb.append("    <path\n").append(attrs.joinToString("\n") { "        $it" }).append(">\n")
                sb.append("        <aapt:attr name=\"android:${side}Color\">\n").append(gradient).append("        </aapt:attr>\n")
                sb.append("    </path>\n")
            }
        }
        sb.append("</vector>\n")
        if (usedEvenOdd) {
            report.add(ReportItem(TARGET, "-", "قاعدة التعبئة الزوجية (ثقوب)", Fidelity.Approximated, "تتطلب minSdk 24 فأعلى"))
        }
        if (usedGradient) {
            report.add(ReportItem(TARGET, "-", "التدرجات", Fidelity.Approximated, "تتطلب API 24 أو VectorDrawableCompat"))
        }
        return sb.toString()
    }

    private fun gradientXml(p: FlatPaint): String? {
        fun items(stops: List<FlatStop>) = stops.joinToString("") {
            "            <item android:offset=\"${Format.num(it.offset)}\" android:color=\"${Format.argb(it.argb)}\"/>\n"
        }
        return when (p) {
            is FlatPaint.Solid -> null
            is FlatPaint.Linear ->
                "            <gradient android:type=\"linear\" android:startX=\"${Format.num(p.x1)}\" android:startY=\"${Format.num(p.y1)}\" " +
                    "android:endX=\"${Format.num(p.x2)}\" android:endY=\"${Format.num(p.y2)}\">\n" + items(p.stops) + "            </gradient>\n"
            is FlatPaint.Radial ->
                "            <gradient android:type=\"radial\" android:centerX=\"${Format.num(p.cx)}\" android:centerY=\"${Format.num(p.cy)}\" " +
                    "android:gradientRadius=\"${Format.num(p.radius)}\">\n" + items(p.stops) + "            </gradient>\n"
            is FlatPaint.Sweep ->
                "            <gradient android:type=\"sweep\" android:centerX=\"${Format.num(p.cx)}\" android:centerY=\"${Format.num(p.cy)}\">\n" +
                    items(p.stops) + "            </gradient>\n"
        }
    }
}
