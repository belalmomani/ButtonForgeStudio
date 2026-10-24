package studio.buttonforge.integration

import org.junit.Assume
import studio.buttonforge.document.CanvasSize
import studio.buttonforge.document.Document
import studio.buttonforge.document.appearance.Appearance
import studio.buttonforge.editorcore.EditorSession
import studio.buttonforge.geometry.hittest.HitTester
import studio.buttonforge.geometry.math.Vec2
import studio.buttonforge.geometry.path.AnchorId
import studio.buttonforge.geometry.primitives.Primitives
import studio.buttonforge.storage.ProjectReader
import studio.buttonforge.storage.ProjectWriter
import kotlin.test.Test

/**
 * Opt-in timing of the core operations (`gradle :integration-tests:perfTest`). These numbers come from the machine
 * running the test, not from a phone: they show how cost grows with points and layers, and which operations to
 * watch on the device. They decide nothing on their own (see docs/performance.md).
 */
class PerfBaselineTest {
    private fun session(points: Int, layers: Int): EditorSession {
        val s = EditorSession(Document("perf", CanvasSize(1000.0, 1000.0)))
        repeat(layers) { i ->
            val id = s.addShapeLayer("L$i", Appearance()) { next -> Primitives.circle(Vec2(500.0, 500.0), 400.0 - i, next) }
            s.addPointsToCount(id, 0, points)
        }
        return s
    }

    private inline fun millisPer(iterations: Int, warmup: Int = 5, block: (Int) -> Unit): Double {
        repeat(warmup) { block(it) }
        val start = System.nanoTime()
        repeat(iterations) { block(it) }
        return (System.nanoTime() - start) / 1e6 / iterations
    }

    @Test
    fun printBaseline() {
        Assume.assumeTrue("set -Dbfs.perf to run", System.getProperty("bfs.perf") != null)
        println("PERF java=${System.getProperty("java.version")} cpus=${Runtime.getRuntime().availableProcessors()}")
        println("PERF points layers | hitAnchor hitSegment dragEvent(1) dragEvent(50) undo+redo(100) save open | bytes")
        for (layers in listOf(1, 10, 50)) {
            for (points in listOf(100, 500, 1000)) {
                val s = session(points, layers)
                val top = s.state.selectedLayer!!
                val geometry = (s.document.layers.last() as studio.buttonforge.document.layers.ShapeLayer).shape.geometry
                val anchors = geometry.contours.single().anchors

                val hitAnchor = millisPer(200) { i ->
                    HitTester.nearestAnchor(geometry, Vec2(500.0 + i % 50, 100.0 + i % 70), 24.0)
                }
                val hitSegment = millisPer(200) { i ->
                    HitTester.nearestSegment(geometry, Vec2(500.0 + i % 50, 100.0 + i % 70), 24.0)
                }

                s.beginAnchorDrag(top, setOf(anchors[0].id))
                val drag1 = millisPer(300) { i -> s.updateAnchorDrag(Vec2(i * 0.01, 0.0)) }
                s.cancelAnchorDrag()

                s.beginAnchorDrag(top, anchors.take(50).map { it.id }.toSet())
                val drag50 = millisPer(300) { i -> s.updateAnchorDrag(Vec2(i * 0.01, 0.0)) }
                s.cancelAnchorDrag()

                val history = EditorSession(s.document)
                repeat(100) { i ->
                    history.beginAnchorDrag(top, setOf(AnchorId(anchors[0].id.value)))
                    history.updateAnchorDrag(Vec2(1.0 + i, 0.0))
                    history.commitAnchorDrag()
                }
                val undoRedo = millisPer(1, warmup = 0) {
                    repeat(100) { history.undo() }
                    repeat(100) { history.redo() }
                }

                var bytes = ByteArray(0)
                val save = millisPer(5) { bytes = ProjectWriter().toBytes(s.document) }
                val open = millisPer(5) { ProjectReader().read(bytes) }

                println(
                    "PERF %6d %6d | %9.3f %10.3f %12.3f %13.3f %16.3f %5.1f %5.1f | %d".format(
                        points, layers, hitAnchor, hitSegment, drag1, drag50, undoRedo, save, open, bytes.size,
                    ),
                )
            }
        }
        println("PERF units: milliseconds per call (undo+redo: total for 200 steps)")
    }
}
