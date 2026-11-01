package studio.buttonforge.export

import org.w3c.dom.Element
import studio.buttonforge.document.CanvasSize
import studio.buttonforge.document.Document
import studio.buttonforge.document.appearance.Appearance
import studio.buttonforge.document.appearance.BlendMode
import studio.buttonforge.document.appearance.Color
import studio.buttonforge.document.appearance.Effect
import studio.buttonforge.document.appearance.Fill
import studio.buttonforge.document.appearance.GradientStop
import studio.buttonforge.document.appearance.Paint
import studio.buttonforge.document.appearance.Stroke
import studio.buttonforge.document.appearance.StrokeCap
import studio.buttonforge.document.layers.GroupLayer
import studio.buttonforge.document.layers.LayerId
import studio.buttonforge.document.layers.ShapeLayer
import studio.buttonforge.document.shape.Shape
import studio.buttonforge.document.shape.Transform
import studio.buttonforge.geometry.IdSource
import studio.buttonforge.geometry.math.Vec2
import studio.buttonforge.geometry.path.CompoundPath
import studio.buttonforge.geometry.path.FillRule
import studio.buttonforge.geometry.primitives.Primitives
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ExportTest {
    private val blue = Color(0xFF3366FF.toInt())
    private val ids = IdSource()
    private val android = "http://schemas.android.com/apk/res/android"

    private fun square() = Primitives.rectangle(Vec2(5.0, 5.0), 10.0, 10.0, ids::nextId)

    private fun layer(
        id: Long,
        look: Appearance,
        geometry: CompoundPath = square(),
        transform: Transform = Transform(),
        visible: Boolean = true,
    ) = ShapeLayer(LayerId(id), "L$id", visible = visible, shape = Shape(geometry, look, transform))

    private fun doc(vararg layers: ShapeLayer, name: String = "My Button") =
        Document(name, CanvasSize(512.0, 512.0), layers.toList())

    private val solid = Appearance(fills = listOf(Fill(Paint.Solid(blue))))

    private fun xml(text: String, namespaces: Boolean = false): Element {
        val f = DocumentBuilderFactory.newInstance()
        f.isNamespaceAware = namespaces
        return f.newDocumentBuilder().parse(ByteArrayInputStream(text.toByteArray(Charsets.UTF_8))).documentElement
    }

    private fun bundle(d: Document, r: Rasterizer? = null) = Exporter.build(d, r)
    private fun svg(b: ExportBundle) = b.firstWithSuffix(".svg")!!.text
    private fun vector(b: ExportBundle) = b.firstWithSuffix("res/drawable/my_button.xml")!!.text
    private fun compose(b: ExportBundle) = b.firstWithSuffix("MyButton.kt")!!.text

    // ---------------------------------------------------------------- paths

    @Test
    fun pathDataIsExactForASquare() {
        val s = svg(bundle(doc(layer(1, solid))))
        assertTrue(s.contains("d=\"M0 0C0 0 10 0 10 0C10 0 10 10 10 10C10 10 0 10 0 10C0 10 0 0 0 0Z\""), s)
    }

    @Test
    fun shapeTransformsAreBakedIntoTheCoordinatesAndStrokeWidthScales() {
        val look = Appearance(strokes = listOf(Stroke(Paint.Solid(blue), width = 4.0)))
        val s = svg(bundle(doc(layer(1, look, transform = Transform(Vec2(100.0, 0.0), 0.0, Vec2(2.0, 2.0))))))
        assertTrue(s.contains("d=\"M100 0C"), s)
        assertTrue(s.contains("120 20"), s)
        assertTrue(s.contains("stroke-width=\"8\""), s)
    }

    @Test
    fun anUnevenScaleIsReportedAsAnApproximation() {
        val look = Appearance(strokes = listOf(Stroke(Paint.Solid(blue), width = 4.0)))
        val r = bundle(doc(layer(1, look, transform = Transform(scale = Vec2(2.0, 1.0))))).report
        assertTrue(r.approximated.any { it.feature.contains("سماكة") }, r.lines().toString())
    }

    // ---------------------------------------------------------------- SVG

    @Test
    fun svgIsWellFormedUsesRealPathsAndSkipsHiddenLayers() {
        val s = svg(bundle(doc(layer(1, solid), layer(2, solid, visible = false))))
        val root = xml(s)
        assertEquals("svg", root.tagName)
        assertEquals("512", root.getAttribute("width"))
        assertEquals(1, root.getElementsByTagName("path").length)
        assertFalse(s.contains("<image"), "never an embedded picture")
        assertTrue(s.contains("fill=\"#3366FF\""))
        assertFalse(s.contains("fill-opacity"))
    }

    @Test
    fun hiddenGroupsHideTheirChildren() {
        val group = GroupLayer(LayerId(9), "G", visible = false, children = listOf(layer(1, solid)))
        val b = bundle(Document("x", CanvasSize(10.0, 10.0), listOf(group)))
        assertEquals(0, xml(svg(b)).getElementsByTagName("path").length)
    }

    @Test
    fun opacityFillRuleStrokeAndDashAreWritten() {
        val hole = CompoundPath(
            listOf(square().contours.single(), Primitives.circle(Vec2(5.0, 5.0), 2.0, ids::nextId).contours.single()),
            FillRule.EvenOdd,
        )
        val look = Appearance(
            fills = listOf(Fill(Paint.Solid(Color(0x80FF0000.toInt())), opacity = 0.5)),
            strokes = listOf(Stroke(Paint.Solid(Color.Black), width = 2.0, cap = StrokeCap.Round, dash = listOf(4.0, 2.0))),
            opacity = 0.5,
        )
        val s = svg(bundle(doc(layer(1, look, hole))))
        assertTrue(s.contains("fill-rule=\"evenodd\""))
        assertTrue(s.contains("fill-opacity=\"0.125\""), s) // 0.5 * 0.5 * (128/255)
        assertTrue(s.contains("stroke-linecap=\"round\""))
        assertTrue(s.contains("stroke-dasharray=\"4 2\""))
        assertEquals(2, xml(s).getElementsByTagName("path").length)
    }

    @Test
    fun gradientsBecomeSvgGradientsAndSweepIsApproximated() {
        val stops = listOf(GradientStop(0.0, Color(0xFFFF0000.toInt())), GradientStop(1.0, Color(0xFF0000FF.toInt())))
        val linear = Appearance(fills = listOf(Fill(Paint.LinearGradient(Vec2(0.0, 0.0), Vec2(10.0, 0.0), stops))))
        val s = svg(bundle(doc(layer(1, linear))))
        assertTrue(s.contains("<linearGradient id=\"g1\""), s)
        assertTrue(s.contains("fill=\"url(#g1)\""))
        assertEquals(2, xml(s).getElementsByTagName("stop").length)

        val sweep = Appearance(fills = listOf(Fill(Paint.SweepGradient(Vec2(5.0, 5.0), stops))))
        val b = bundle(doc(layer(1, sweep)))
        assertTrue(b.report.approximated.any { it.target == "SVG" && it.feature.contains("المخروطي") })
        assertFalse(svg(b).contains("Gradient"))
    }

    // ---------------------------------------------------------------- Android

    @Test
    fun vectorDrawableHasTheExpectedAttributes() {
        val look = Appearance(
            fills = listOf(Fill(Paint.Solid(blue), opacity = 0.5)),
            strokes = listOf(Stroke(Paint.Solid(Color.Black), width = 3.0, cap = StrokeCap.Round)),
        )
        val root = xml(vector(bundle(doc(layer(1, look)))), namespaces = true)
        assertEquals("vector", root.tagName)
        assertEquals("512dp", root.getAttributeNS(android, "width"))
        assertEquals("512", root.getAttributeNS(android, "viewportWidth"))
        val paths = root.getElementsByTagName("path")
        assertEquals(2, paths.length)
        val fill = paths.item(0) as Element
        assertEquals("#FF3366FF", fill.getAttributeNS(android, "fillColor"))
        assertEquals("0.5", fill.getAttributeNS(android, "fillAlpha"))
        val stroke = paths.item(1) as Element
        assertEquals("#FF000000", stroke.getAttributeNS(android, "strokeColor"))
        assertEquals("3", stroke.getAttributeNS(android, "strokeWidth"))
        assertEquals("round", stroke.getAttributeNS(android, "strokeLineCap"))
    }

    @Test
    fun vectorDrawableGradientsUseTheAaptAttribute() {
        val stops = listOf(GradientStop(0.0, Color(0xFFFF0000.toInt())), GradientStop(1.0, Color(0xFF0000FF.toInt())))
        val look = Appearance(
            fills = listOf(
                Fill(Paint.RadialGradient(Vec2(5.0, 5.0), 5.0, stops)),
                Fill(Paint.SweepGradient(Vec2(5.0, 5.0), stops)),
            ),
        )
        val text = vector(bundle(doc(layer(1, look))))
        assertTrue(text.contains("<aapt:attr name=\"android:fillColor\">"), text)
        assertTrue(text.contains("android:type=\"radial\"") && text.contains("android:type=\"sweep\""))
        assertEquals(4, xml(text, namespaces = true).getElementsByTagName("item").length)
    }

    @Test
    fun whatAndroidCannotDrawIsDroppedAndReported() {
        val look = Appearance(
            fills = listOf(Fill(Paint.Solid(blue))),
            strokes = listOf(Stroke(Paint.Solid(Color.Black), width = 2.0, dash = listOf(3.0, 3.0))),
            effects = listOf(Effect.DropShadow(Vec2(2.0, 2.0), 4.0, 0.0, Color.Black)),
            blendMode = BlendMode.Multiply,
        )
        val b = bundle(doc(layer(1, look)))
        val dropped = b.report.dropped.filter { it.target == "VectorDrawable" }.map { it.feature }
        assertTrue(dropped.any { it.contains("الحد المنقط") }, dropped.toString())
        assertTrue(dropped.any { it.contains("الظلال") })
        assertTrue(dropped.any { it.contains("نمط المزج") })
        assertFalse(vector(b).contains("dash"))
        assertTrue(b.report.dropped.any { it.target == "Compose" })
        assertTrue(b.report.approximated.any { it.target == "SVG" && it.feature.contains("المزج") })
        assertTrue(svg(b).contains("mix-blend-mode:multiply"))
    }

    @Test
    fun evenOddNeedsApi24AndSaysSo() {
        val hole = CompoundPath(listOf(square().contours.single(), square().contours.single()), FillRule.EvenOdd)
        val b = bundle(doc(layer(1, solid, hole)))
        assertTrue(vector(b).contains("android:fillType=\"evenOdd\""))
        assertTrue(b.report.approximated.any { it.target == "VectorDrawable" && it.note.contains("24") })
    }

    @Test
    fun composeCodeBuildsAnImageVector() {
        val look = Appearance(
            fills = listOf(Fill(Paint.Solid(blue))),
            strokes = listOf(Stroke(Paint.Solid(Color.Black), width = 2.0)),
        )
        val code = compose(bundle(doc(layer(1, look))))
        assertTrue(code.contains("val MyButton: ImageVector = ImageVector.Builder("), code)
        assertTrue(code.contains("moveTo(0f, 0f)"))
        assertTrue(code.contains("curveTo(0f, 0f, 10f, 0f, 10f, 0f)"))
        assertTrue(code.contains("close()"))
        assertTrue(code.contains("fill = SolidColor(Color(0xFF3366FF))"))
        assertTrue(code.contains("stroke = SolidColor(Color(0xFF000000))"))
        assertTrue(code.contains("strokeLineWidth = 2f"))
        assertTrue(code.contains("}.build()"))
        assertEquals(code.count { it == '{' }, code.count { it == '}' }, "braces are balanced")
    }

    @Test
    fun composeGradientsAndFillRule() {
        val stops = listOf(GradientStop(0.0, Color(0xFFFF0000.toInt())), GradientStop(1.0, Color(0xFF0000FF.toInt())))
        val look = Appearance(fills = listOf(Fill(Paint.LinearGradient(Vec2(0.0, 0.0), Vec2(10.0, 10.0), stops))))
        val hole = CompoundPath(listOf(square().contours.single(), square().contours.single()), FillRule.EvenOdd)
        val code = compose(bundle(doc(layer(1, look, hole))))
        assertTrue(code.contains("Brush.linearGradient(0f to Color(0xFFFF0000), 1f to Color(0xFF0000FF), start = Offset(0f, 0f), end = Offset(10f, 10f))"), code)
        assertTrue(code.contains("PathFillType.EvenOdd"))
    }

    // ---------------------------------------------------------------- web

    @Test
    fun htmlButtonEmbedsTheSvgWithPrefixedGradientIdsAndStateStyles() {
        val stops = listOf(GradientStop(0.0, Color(0xFFFF0000.toInt())), GradientStop(1.0, Color(0xFF0000FF.toInt())))
        val look = Appearance(fills = listOf(Fill(Paint.LinearGradient(Vec2(0.0, 0.0), Vec2(10.0, 0.0), stops))))
        val html = bundle(doc(layer(1, look))).firstWithSuffix(".html")!!.text
        assertTrue(html.contains("<button class=\"bf-my-button\""))
        assertTrue(html.contains("<svg") && html.contains("</svg>"))
        assertTrue(html.contains("id=\"my-button-g1\"") && html.contains("url(#my-button-g1)"))
        assertTrue(html.contains(":disabled") && html.contains(":hover") && html.contains(":active") && html.contains(":focus-visible"))
        assertTrue(html.contains("bf-label"))
        assertFalse(html.contains("<?xml"))
    }

    // ---------------------------------------------------------------- names

    @Test
    fun namesAreValidIdentifiers() {
        assertEquals("my_button", Names.androidResource("My Button!"))
        assertEquals("button", Names.androidResource("بدون عنوان"))
        assertEquals("button_3d_btn", Names.androidResource("3d btn"))
        assertEquals("MyButton2", Names.pascal("my button-2"))
        assertEquals("Button", Names.pascal(""))
        assertEquals("Button3d", Names.pascal("3d"))
        assertEquals("my-button", Names.web("My Button"))
    }

    // ---------------------------------------------------------------- bundle

    private class FakeRasterizer : Rasterizer {
        val sizes = ArrayList<Pair<Int, Int>>()
        override fun png(doc: Document, widthPx: Int, heightPx: Int): ByteArray {
            sizes += widthPx to heightPx
            return byteArrayOf(1, 2, 3)
        }
    }

    @Test
    fun theBundleHasEveryFileAndPngsAtTheRightSizes() {
        val r = FakeRasterizer()
        val b = bundle(doc(layer(1, solid)), r)
        val paths = b.files.map { it.path }.toSet()
        for (expected in listOf(
            "android/res/drawable/my_button.xml", "android/compose/MyButton.kt", "android/README.txt",
            "web/my-button.svg", "web/my-button.html", "web/README.txt",
            "android/res/drawable-mdpi/my_button.png", "android/res/drawable-xxxhdpi/my_button.png",
            "web/my-button.png", "web/my-button@2x.png", "web/my-button@3x.png",
        )) {
            assertTrue(expected in paths, "missing $expected in $paths")
        }
        assertEquals(listOf(512, 768, 1024, 1536, 2048, 512, 1024, 1536), r.sizes.map { it.first })
        assertEquals(r.sizes.map { it.first }, r.sizes.map { it.second })
    }

    @Test
    fun withoutARasterizerThereAreNoPngs() {
        val b = bundle(doc(layer(1, solid)))
        assertTrue(b.files.none { it.path.endsWith(".png") })
    }

    @Test
    fun theZipHoldsTheSameFiles() {
        val b = bundle(doc(layer(1, solid)), FakeRasterizer())
        val read = HashMap<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(b.zip())).use { zin ->
            while (true) {
                val e = zin.nextEntry ?: break
                read[e.name] = zin.readBytes()
            }
        }
        assertEquals(b.files.map { it.path }.toSet(), read.keys)
        for (f in b.files) assertTrue(f.bytes.contentEquals(read.getValue(f.path)), f.path)
    }

    @Test
    fun pngReportsWhatTheEditorDoesNotDrawYet() {
        val look = Appearance(
            fills = listOf(Fill(Paint.Solid(blue))),
            effects = listOf(Effect.LayerBlur(3.0)),
        )
        val b = bundle(doc(layer(1, look)), FakeRasterizer())
        assertTrue(b.report.dropped.any { it.target == "PNG" })
    }

    @Test
    fun anEmptyDocumentStillExportsAndSaysSo() {
        val b = bundle(Document("x", CanvasSize(100.0, 100.0)))
        assertTrue(b.report.dropped.any { it.feature.contains("لا يوجد أي شكل") })
        assertNotNull(b.file("web/x.svg"))
        assertEquals(0, xml(b.file("web/x.svg")!!.text).getElementsByTagName("path").length)
    }

    @Test
    fun theReportListsEverythingInPlainLines() {
        val look = Appearance(fills = listOf(Fill(Paint.Solid(blue))), effects = listOf(Effect.LayerBlur(3.0)))
        val lines = bundle(doc(layer(1, look))).report.lines()
        assertTrue(lines.first().startsWith("عدد عناصر الرسم: 1"))
        assertTrue(lines.any { it.contains("[VectorDrawable]") && it.contains("يُحذف") })
        assertEquals(listOf("عدد عناصر الرسم: 1", "لا يوجد ما يُفقد أو يُقرّب."), bundle(doc(layer(1, solid))).report.lines().filter { !it.startsWith("[") })
    }
}
