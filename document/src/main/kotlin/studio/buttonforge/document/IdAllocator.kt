package studio.buttonforge.document

import studio.buttonforge.document.layers.LayerId
import studio.buttonforge.geometry.path.AnchorId

/** Next unused ids. Saved with the document so ids are never reused, even after deletion and reopening. */
data class IdCounters(val nextAnchor: Long = 1, val nextLayer: Long = 1)

class IdAllocator(start: IdCounters = IdCounters()) {
    private var nextAnchor = start.nextAnchor
    private var nextLayer = start.nextLayer

    fun nextAnchorId(): AnchorId = AnchorId(nextAnchor++)
    fun nextLayerId(): LayerId = LayerId(nextLayer++)
    fun counters(): IdCounters = IdCounters(nextAnchor, nextLayer)
}
