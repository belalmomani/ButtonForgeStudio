package studio.buttonforge.document.states

import studio.buttonforge.document.appearance.Appearance
import studio.buttonforge.document.layers.LayerId
import studio.buttonforge.document.shape.Transform

enum class ButtonState { Normal, Pressed, Hovered, Focused, Disabled }

/** Per-layer differences for one state. Geometry is shared across states (decision D4). */
data class StateOverride(val appearance: Appearance? = null, val transform: Transform? = null)

data class StateSet(val overrides: Map<ButtonState, Map<LayerId, StateOverride>> = emptyMap()) {
    fun withoutLayers(ids: Set<LayerId>): StateSet = StateSet(
        overrides
            .mapValues { (_, perLayer) -> perLayer.filterKeys { it !in ids } }
            .filterValues { it.isNotEmpty() },
    )
}
