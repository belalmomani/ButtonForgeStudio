package studio.buttonforge

import studio.buttonforge.geometry.hittest.HitTester
import studio.buttonforge.geometry.math.Vec2
import studio.buttonforge.geometry.ops.ArcLengthDistribution
import studio.buttonforge.geometry.ops.BooleanOp
import studio.buttonforge.geometry.ops.BooleanOptions
import studio.buttonforge.geometry.ops.BooleanResult
import studio.buttonforge.geometry.path.AnchorId
import studio.buttonforge.geometry.path.CompoundPath
import studio.buttonforge.geometry.path.withInsertedAnchors
import studio.buttonforge.geometry.primitives.Primitives
import studio.buttonforge.geometryandroid.SkiaBooleanOps

/**
 * A self-check for the boolean operations, run on the device. For each case and operation it asks the platform for the
 * result, rebuilds editable anchors from it, then compares the rebuilt shape with the expected inside/outside answer on
 * a grid of points (skipping points within 1.5 units of an input outline, where "inside" is ambiguous).
 */
object BooleanLab {
    private class Case(val name: String, val a: CompoundPath, val b: CompoundPath)

    fun run(): List<String> {
        var next = 1L
        val ids: () -> AnchorId = { AnchorId(next++) }
        fun circle(x: Double, y: Double, r: Double) = Primitives.circle(Vec2(x, y), r, ids)
        fun rect(x: Double, y: Double, w: Double, h: Double) = Primitives.rectangle(Vec2(x, y), w, h, ids)
        fun heavyCircle(x: Double, y: Double, r: Double, points: Int): CompoundPath {
            val c = circle(x, y, r).contours.single()
            val extra = ArcLengthDistribution.fillTo(c, points)
            return CompoundPath(listOf(c.withInsertedAnchors(extra, ids)))
        }

        val cases = listOf(
            Case("دائرة + نجمة", circle(0.0, 0.0, 100.0), Primitives.star(Vec2(60.0, 0.0), 110.0, 45.0, 5, ids)),
            Case("مستطيل ودائرة داخله", rect(0.0, 0.0, 300.0, 200.0), circle(0.0, 0.0, 60.0)),
            Case("دائرتان متقاطعتان", circle(-40.0, 0.0, 100.0), circle(40.0, 0.0, 100.0)),
            Case("دائرتان متماستان", circle(-100.0, 0.0, 100.0), circle(100.0, 0.0, 100.0)),
            Case("مستطيلان بحافة مشتركة", rect(-100.0, 0.0, 200.0, 100.0), rect(100.0, 0.0, 200.0, 100.0)),
            Case("شكلان متطابقان", circle(0.0, 0.0, 80.0), circle(0.0, 0.0, 80.0)),
            Case("نجمة ومستطيل", Primitives.star(Vec2.ZERO, 120.0, 50.0, 6, ids), rect(30.0, 10.0, 160.0, 60.0)),
            Case("دائرتان بـ500 نقطة", heavyCircle(-30.0, 0.0, 100.0, 500), heavyCircle(30.0, 0.0, 100.0, 500)),
        )
        val ops = BooleanOp.values()
        val ops2 = SkiaBooleanOps()
        val lines = ArrayList<String>()
        lines += "الحالة | العملية | النتيجة | محيطات/نقاط | تطابق | م.ث"
        var worst = 100.0
        var failures = 0
        for (case in cases) {
            for (op in ops) {
                val start = System.nanoTime()
                val result = try {
                    ops2.apply(op, case.a, case.b, BooleanOptions(tolerance = 0.05, nextId = ids))
                } catch (e: Throwable) {
                    BooleanResult.Failure(studio.buttonforge.geometry.ops.BooleanFailure.Internal, e.toString())
                }
                val ms = (System.nanoTime() - start) / 1_000_000
                when (result) {
                    is BooleanResult.Failure -> {
                        failures++
                        lines += "${case.name} | ${op.name} | فشل ${result.reason} ${result.message} | - | - | $ms"
                    }
                    is BooleanResult.Success -> {
                        val contours = result.path.contours
                        val anchors = contours.sumOf { it.anchors.size }
                        val agreement = agreement(op, case, result.path)
                        worst = minOf(worst, agreement)
                        val empty = if (contours.isEmpty()) " (فارغ)" else ""
                        lines += "${case.name} | ${op.name} | نجح$empty | ${contours.size}/$anchors | ${"%.1f".format(agreement)}% | $ms"
                    }
                }
            }
        }
        lines += ""
        lines += "عدد الإخفاقات: $failures | أسوأ تطابق: ${"%.1f".format(worst)}%"
        return lines
    }

    private fun agreement(op: BooleanOp, case: Case, result: CompoundPath): Double {
        val all = listOf(case.a, case.b)
        val xs = all.flatMap { p -> p.contours.flatMap { c -> c.anchors.map { it.position.x } } }
        val ys = all.flatMap { p -> p.contours.flatMap { c -> c.anchors.map { it.position.y } } }
        if (xs.isEmpty()) return 100.0
        val minX = xs.min() - 10.0
        val maxX = xs.max() + 10.0
        val minY = ys.min() - 10.0
        val maxY = ys.max() + 10.0
        var considered = 0
        var same = 0
        val steps = 80
        for (i in 0..steps) {
            for (j in 0..steps) {
                val p = Vec2(minX + (maxX - minX) * i / steps, minY + (maxY - minY) * j / steps)
                if (HitTester.nearestSegment(case.a, p, 1.5) != null || HitTester.nearestSegment(case.b, p, 1.5) != null) continue
                val ca = HitTester.contains(case.a, p)
                val cb = HitTester.contains(case.b, p)
                val expected = when (op) {
                    BooleanOp.Union -> ca || cb
                    BooleanOp.Subtract -> ca && !cb
                    BooleanOp.Intersect -> ca && cb
                    BooleanOp.Exclude -> ca != cb
                }
                considered++
                if (HitTester.contains(result, p) == expected) same++
            }
        }
        return if (considered == 0) 100.0 else 100.0 * same / considered
    }
}
