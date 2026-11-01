package studio.buttonforge.export

import studio.buttonforge.document.Document
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.roundToInt

/** Turns a design into PNG bytes at a given pixel size, with a transparent background. Implemented by the app. */
interface Rasterizer {
    fun png(doc: Document, widthPx: Int, heightPx: Int): ByteArray
}

class ExportFile(val path: String, val bytes: ByteArray) {
    val text: String get() = bytes.toString(Charsets.UTF_8)
}

class ExportBundle(val files: List<ExportFile>, val report: ExportReport) {
    fun file(path: String): ExportFile? = files.firstOrNull { it.path == path }

    fun firstWithSuffix(suffix: String): ExportFile? = files.firstOrNull { it.path.endsWith(suffix) }

    /** The whole bundle as a ZIP archive. */
    fun zip(): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            for (f in files) {
                zip.putNextEntry(ZipEntry(f.path))
                zip.write(f.bytes)
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }
}

object Exporter {
    private val ANDROID_DENSITIES = listOf("mdpi" to 1.0, "hdpi" to 1.5, "xhdpi" to 2.0, "xxhdpi" to 3.0, "xxxhdpi" to 4.0)
    private val WEB_SCALES = listOf("" to 1.0, "@2x" to 2.0, "@3x" to 3.0)

    /**
     * Builds every output at once: Android (VectorDrawable XML, Compose code, PNG per density), web (SVG, HTML button, PNG at
     * 1x/2x/3x). PNGs are produced only when a [rasterizer] is given. The report says what was kept, approximated or dropped.
     */
    fun build(doc: Document, rasterizer: Rasterizer? = null, name: String = doc.name): ExportBundle {
        val report = ReportBuilder()
        val items = Flattener.flatten(doc, report)
        report.kept = items.size
        if (items.isEmpty()) {
            report.add(ReportItem("الكل", "-", "لا يوجد أي شكل مرئي للتصدير", Fidelity.Dropped, "الملفات ستكون فارغة"))
        }
        val res = Names.androidResource(name)
        val pascal = Names.pascal(name)
        val web = Names.web(name)
        val files = ArrayList<ExportFile>()
        fun text(path: String, content: String) {
            files += ExportFile(path, content.toByteArray(Charsets.UTF_8))
        }

        text("android/res/drawable/$res.xml", AndroidVectorWriter.write(doc, items, report))
        text("android/compose/$pascal.kt", ComposeWriter.write(doc, items, report, pascal))
        text("web/$web.svg", SvgWriter.write(doc, items, report, name))
        text("web/$web.html", WebWriter.write(doc, items, report, name, web))

        if (rasterizer != null) {
            for (item in items.distinctBy { it.layer }) {
                if (item.hasEffects) report.add(ReportItem("PNG", item.layer, "الظلال والتوهج والتمويه", Fidelity.Dropped, "المحرر لا يرسمها بعد"))
                if (item.blend != studio.buttonforge.document.appearance.BlendMode.Normal) {
                    report.add(ReportItem("PNG", item.layer, "نمط المزج", Fidelity.Dropped, "المحرر لا يرسمه بعد"))
                }
            }
            for ((suffix, factor) in ANDROID_DENSITIES) {
                files += ExportFile("android/res/drawable-$suffix/$res.png", rasterizer.png(doc, px(doc.canvas.width, factor), px(doc.canvas.height, factor)))
            }
            for ((suffix, factor) in WEB_SCALES) {
                files += ExportFile("web/$web$suffix.png", rasterizer.png(doc, px(doc.canvas.width, factor), px(doc.canvas.height, factor)))
            }
        }
        text("android/README.txt", androidReadme(res, pascal, doc, rasterizer != null))
        text("web/README.txt", webReadme(web, rasterizer != null))
        return ExportBundle(files, report.build())
    }

    private fun px(size: Double, factor: Double) = (size * factor).roundToInt().coerceAtLeast(1)

    private fun androidReadme(res: String, pascal: String, doc: Document, withPng: Boolean) = buildString {
        appendLine("Button Forge Studio: ملفات أندرويد")
        appendLine()
        appendLine("1) VectorDrawable (الأفضل: بلا فقد في الجودة)")
        appendLine("   انسخ res/drawable/$res.xml إلى app/src/main/res/drawable/ في مشروعك.")
        appendLine("   كخلفية:   android:background=\"@drawable/$res\"")
        appendLine("   كصورة:    <ImageView android:src=\"@drawable/$res\" .../>")
        appendLine("   الأبعاد الأصلية ${Format.num(doc.canvas.width)} x ${Format.num(doc.canvas.height)} dp.")
        appendLine()
        appendLine("2) Jetpack Compose")
        appendLine("   انسخ compose/$pascal.kt إلى مشروعك وأضف سطر package في أعلاه، ثم:")
        appendLine("   Image(imageVector = $pascal, contentDescription = null)")
        appendLine("   أو Modifier.clickable { } للضغط.")
        if (withPng) {
            appendLine()
            appendLine("3) صور PNG (شفافة) بخمس كثافات: res/drawable-mdpi ... drawable-xxxhdpi")
            appendLine("   تُستخدم إن كنت لا تريد VectorDrawable. لا تضعها مع XML بالاسم نفسه في المجلد نفسه.")
        }
        appendLine()
        appendLine("ما لا يدعمه أندرويد من التصميم مذكور في تقرير التصدير (الحذف أو التقريب).")
    }

    private fun webReadme(web: String, withPng: Boolean) = buildString {
        appendLine("Button Forge Studio: ملفات الويب")
        appendLine()
        appendLine("$web.svg   مسارات متجهة حقيقية (تُفتح في أي متصفح أو برنامج رسم).")
        appendLine("$web.html  زر جاهز: SVG مضمّن + CSS للمرور والضغط والتعطيل والتركيز. غيّر النص داخل <span class=\"bf-label\">.")
        if (withPng) appendLine("$web.png و$web@2x.png و$web@3x.png  صور شفافة لشاشات الكثافة العالية.")
        appendLine()
        appendLine("ما لا يدعمه SVG من التصميم مذكور في تقرير التصدير.")
    }
}
