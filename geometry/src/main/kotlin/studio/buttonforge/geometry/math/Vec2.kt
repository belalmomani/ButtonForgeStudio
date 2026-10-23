package studio.buttonforge.geometry.math

import kotlin.math.hypot

data class Vec2(val x: Double, val y: Double) {
    operator fun plus(o: Vec2) = Vec2(x + o.x, y + o.y)
    operator fun minus(o: Vec2) = Vec2(x - o.x, y - o.y)
    operator fun times(k: Double) = Vec2(x * k, y * k)
    fun length(): Double = hypot(x, y)
    fun distanceTo(o: Vec2): Double = hypot(x - o.x, y - o.y)
    val isFinite: Boolean get() = x.isFinite() && y.isFinite()

    companion object {
        val ZERO = Vec2(0.0, 0.0)
    }
}

fun lerp(a: Vec2, b: Vec2, t: Double): Vec2 =
    Vec2(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)
