package studio.buttonforge.document

import studio.buttonforge.document.appearance.Color
import studio.buttonforge.document.appearance.Fill
import studio.buttonforge.document.appearance.GradientStop
import studio.buttonforge.document.appearance.Paint
import studio.buttonforge.document.appearance.Stroke
import studio.buttonforge.geometry.math.Vec2
import kotlin.test.Test
import kotlin.test.assertFailsWith

class PaintValidationTest {
    private val a = GradientStop(0.0, Color.Black)
    private val b = GradientStop(1.0, Color.White)

    @Test
    fun validGradientIsAccepted() {
        Paint.LinearGradient(Vec2(0.0, 0.0), Vec2(1.0, 1.0), listOf(a, b))
        Paint.RadialGradient(Vec2.ZERO, 5.0, listOf(a, GradientStop(0.5, Color.White), b))
    }

    @Test
    fun invalidGradientsAreRejected() {
        assertFailsWith<IllegalArgumentException> { Paint.LinearGradient(Vec2.ZERO, Vec2(1.0, 0.0), listOf(a)) }
        assertFailsWith<IllegalArgumentException> { Paint.LinearGradient(Vec2.ZERO, Vec2(1.0, 0.0), listOf(b, a)) }
        assertFailsWith<IllegalArgumentException> { Paint.RadialGradient(Vec2.ZERO, 0.0, listOf(a, b)) }
        assertFailsWith<IllegalArgumentException> { GradientStop(1.5, Color.Black) }
    }

    @Test
    fun invalidFillAndStrokeValuesAreRejected() {
        assertFailsWith<IllegalArgumentException> { Fill(Paint.Solid(Color.Black), opacity = 2.0) }
        assertFailsWith<IllegalArgumentException> { Stroke(Paint.Solid(Color.Black), width = -1.0) }
        assertFailsWith<IllegalArgumentException> { Stroke(Paint.Solid(Color.Black), dash = listOf(-1.0)) }
    }
}
