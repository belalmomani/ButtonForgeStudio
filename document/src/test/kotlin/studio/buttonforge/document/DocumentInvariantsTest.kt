package studio.buttonforge.document

import studio.buttonforge.document.layers.LayerId
import studio.buttonforge.document.layers.ShapeLayer
import studio.buttonforge.document.layers.addLayer
import studio.buttonforge.document.shape.Shape
import studio.buttonforge.document.states.ButtonState
import studio.buttonforge.document.states.StateOverride
import studio.buttonforge.document.states.StateSet
import studio.buttonforge.geometry.IdSource
import studio.buttonforge.geometry.TestShapes
import studio.buttonforge.geometry.path.CompoundPath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DocumentInvariantsTest {
    @Test
    fun sampleDocumentIsValid() {
        assertEquals(emptyList(), DocumentInvariants.check(SampleDocuments.circleButton()))
    }

    @Test
    fun duplicateAnchorIdsAcrossShapesAreReported() {
        val doc = SampleDocuments.circleButton()
        val clash = ShapeLayer(
            LayerId(2), "Clash",
            shape = Shape(CompoundPath(listOf(TestShapes.square(50.0, IdSource(1))))), // ids 1..4 again
        )
        val problems = DocumentInvariants.check(doc.addLayer(clash).copy(counters = IdCounters(100, 100)))
        assertTrue(problems.any { it.startsWith("Duplicate anchor id") }, problems.toString())
    }

    @Test
    fun idsAtOrAboveCountersAreReported() {
        val doc = SampleDocuments.circleButton().copy(counters = IdCounters(nextAnchor = 2, nextLayer = 1))
        val problems = DocumentInvariants.check(doc)
        assertTrue(problems.any { it.contains("Anchor id") }, problems.toString())
        assertTrue(problems.any { it.contains("Layer id") }, problems.toString())
    }

    @Test
    fun overrideForMissingLayerIsReported() {
        val doc = SampleDocuments.circleButton().copy(
            states = StateSet(mapOf(ButtonState.Pressed to mapOf(LayerId(99) to StateOverride()))),
        )
        assertTrue(DocumentInvariants.check(doc).any { it.contains("missing layer 99") })
    }
}
