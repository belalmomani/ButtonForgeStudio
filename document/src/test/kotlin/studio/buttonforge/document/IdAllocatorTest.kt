package studio.buttonforge.document

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IdAllocatorTest {
    @Test
    fun idsIncreaseAndAreNeverReusedAfterReload() {
        val a = IdAllocator()
        val first = List(5) { a.nextAnchorId().value }
        assertEquals(listOf(1L, 2L, 3L, 4L, 5L), first)

        val reloaded = IdAllocator(a.counters())
        assertTrue(reloaded.nextAnchorId().value > first.max())
        assertEquals(1L, reloaded.nextLayerId().value)
        assertEquals(2L, reloaded.nextLayerId().value)
    }
}
