package studio.buttonforge.storage

import studio.buttonforge.document.IdAllocator
import studio.buttonforge.document.SampleDocuments
import studio.buttonforge.document.kitchenSink
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AnchorIdStabilityTest {
    private fun anchorIds(doc: studio.buttonforge.document.Document): List<Long> {
        fun walk(nodes: List<studio.buttonforge.document.layers.LayerNode>): List<Long> = nodes.flatMap { n ->
            when (n) {
                is studio.buttonforge.document.layers.ShapeLayer ->
                    n.shape.geometry.contours.flatMap { c -> c.anchors.map { it.id.value } }
                is studio.buttonforge.document.layers.GroupLayer -> walk(n.children)
            }
        }
        return walk(doc.layers)
    }

    @Test
    fun idsAndCountersAreIdenticalAfterRoundTripAndNewIdsAreFresh() {
        val original = SampleDocuments.kitchenSink()
        val result = ProjectReader().read(ProjectWriter().toBytes(original))
        assertIs<ReadResult.Success>(result)
        assertEquals(anchorIds(original), anchorIds(result.document))
        assertEquals(original.counters, result.document.counters)
        val fresh = IdAllocator(result.document.counters).nextAnchorId().value
        assertTrue(fresh > anchorIds(result.document).max())
    }
}
