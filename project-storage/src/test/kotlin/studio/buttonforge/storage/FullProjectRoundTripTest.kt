package studio.buttonforge.storage

import studio.buttonforge.document.DocumentInvariants
import studio.buttonforge.document.SampleDocuments
import studio.buttonforge.document.kitchenSink
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class FullProjectRoundTripTest {
    @Test
    fun kitchenSinkSurvivesSaveAndReopen() {
        val original = SampleDocuments.kitchenSink()
        assertEquals(emptyList(), DocumentInvariants.check(original))
        val result = ProjectReader().read(ProjectWriter().toBytes(original))
        assertIs<ReadResult.Success>(result)
        assertEquals(original, result.document)
        assertNull(result.migratedFrom)
    }

    @Test
    fun simpleDocumentSurvives() {
        val original = SampleDocuments.circleButton()
        val result = ProjectReader().read(ProjectWriter().toBytes(original))
        assertIs<ReadResult.Success>(result)
        assertEquals(original, result.document)
    }

    @Test
    fun outputIsDeterministic() {
        val doc = SampleDocuments.kitchenSink()
        assertContentEquals(ProjectWriter().toBytes(doc), ProjectWriter().toBytes(doc))
    }

    @Test
    fun kitchenSinkHasAboutOneHundredAnchorsInTheBody() {
        val body = SampleDocuments.kitchenSink().layers.first().let { it as studio.buttonforge.document.layers.GroupLayer }
            .children.first() as studio.buttonforge.document.layers.ShapeLayer
        val count = body.shape.geometry.contours.first().anchors.size
        assertEquals(true, count in 96..104, "anchors=$count")
    }
}
