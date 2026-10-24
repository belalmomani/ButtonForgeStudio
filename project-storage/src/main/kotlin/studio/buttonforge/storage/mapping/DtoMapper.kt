package studio.buttonforge.storage.mapping

import studio.buttonforge.document.CanvasSize
import studio.buttonforge.document.Document
import studio.buttonforge.document.IdCounters
import studio.buttonforge.document.appearance.Appearance
import studio.buttonforge.document.appearance.BlendMode
import studio.buttonforge.document.appearance.Color
import studio.buttonforge.document.appearance.Effect
import studio.buttonforge.document.appearance.Fill
import studio.buttonforge.document.appearance.GradientStop
import studio.buttonforge.document.appearance.Paint
import studio.buttonforge.document.appearance.Stroke
import studio.buttonforge.document.appearance.StrokeCap
import studio.buttonforge.document.appearance.StrokeJoin
import studio.buttonforge.document.export.ExportSettings
import studio.buttonforge.document.export.ExportTarget
import studio.buttonforge.document.layers.GroupLayer
import studio.buttonforge.document.layers.LayerId
import studio.buttonforge.document.layers.LayerNode
import studio.buttonforge.document.layers.ShapeLayer
import studio.buttonforge.document.shape.Shape
import studio.buttonforge.document.shape.Transform
import studio.buttonforge.document.states.ButtonState
import studio.buttonforge.document.states.StateOverride
import studio.buttonforge.document.states.StateSet
import studio.buttonforge.geometry.math.Vec2
import studio.buttonforge.geometry.path.Anchor
import studio.buttonforge.geometry.path.AnchorId
import studio.buttonforge.geometry.path.CompoundPath
import studio.buttonforge.geometry.path.Contour
import studio.buttonforge.geometry.path.CornerType
import studio.buttonforge.geometry.path.FillRule
import studio.buttonforge.storage.dto.*

/** The file contains something the format does not allow (unknown name, duplicate entry). */
internal class FormatProblem(message: String) : RuntimeException(message)

private inline fun <reified E : Enum<E>> parseEnum(value: String, what: String): E =
    try {
        enumValueOf<E>(value)
    } catch (e: IllegalArgumentException) {
        throw FormatProblem("Unknown $what '$value'")
    }

// ---- geometry ----
private fun Vec2.toDto() = Vec2Dto(x, y)
private fun Vec2Dto.toModel() = Vec2(x, y)

private fun Anchor.toDto() = AnchorDto(id.value, position.toDto(), handleIn.toDto(), handleOut.toDto(), corner.name)
private fun AnchorDto.toModel() = Anchor(
    AnchorId(id), position.toModel(), handleIn.toModel(), handleOut.toModel(),
    parseEnum<CornerType>(corner, "corner type"),
)

private fun Contour.toDto() = ContourDto(anchors.map { it.toDto() }, closed)
private fun ContourDto.toModel() = Contour(anchors.map { it.toModel() }, closed)

private fun CompoundPath.toDto() = PathDto(contours.map { it.toDto() }, fillRule.name)
private fun PathDto.toModel() = CompoundPath(contours.map { it.toModel() }, parseEnum<FillRule>(fillRule, "fill rule"))

// ---- appearance ----
private fun GradientStop.toDto() = GradientStopDto(offset, color.argb)
private fun GradientStopDto.toModel() = GradientStop(offset, Color(color))

private fun Paint.toDto(): PaintDto = when (this) {
    is Paint.Solid -> SolidPaintDto(color.argb)
    is Paint.LinearGradient -> LinearPaintDto(start.toDto(), end.toDto(), stops.map { it.toDto() })
    is Paint.RadialGradient -> RadialPaintDto(center.toDto(), radius, stops.map { it.toDto() })
    is Paint.SweepGradient -> SweepPaintDto(center.toDto(), stops.map { it.toDto() })
}

private fun PaintDto.toModel(): Paint = when (this) {
    is SolidPaintDto -> Paint.Solid(Color(color))
    is LinearPaintDto -> Paint.LinearGradient(start.toModel(), end.toModel(), stops.map { it.toModel() })
    is RadialPaintDto -> Paint.RadialGradient(center.toModel(), radius, stops.map { it.toModel() })
    is SweepPaintDto -> Paint.SweepGradient(center.toModel(), stops.map { it.toModel() })
}

private fun Fill.toDto() = FillDto(paint.toDto(), opacity, enabled)
private fun FillDto.toModel() = Fill(paint.toModel(), opacity, enabled)

private fun Stroke.toDto() = StrokeDto(paint.toDto(), width, cap.name, join.name, miterLimit, dash, opacity, enabled)
private fun StrokeDto.toModel() = Stroke(
    paint.toModel(), width, parseEnum<StrokeCap>(cap, "stroke cap"), parseEnum<StrokeJoin>(join, "stroke join"),
    miterLimit, dash, opacity, enabled,
)

private fun Effect.toDto(): EffectDto = when (this) {
    is Effect.DropShadow -> DropShadowDto(offset.toDto(), blur, spread, color.argb)
    is Effect.InnerShadow -> InnerShadowDto(offset.toDto(), blur, spread, color.argb)
    is Effect.LayerBlur -> LayerBlurDto(radius)
    is Effect.Glow -> GlowDto(radius, color.argb, intensity)
}

private fun EffectDto.toModel(): Effect = when (this) {
    is DropShadowDto -> Effect.DropShadow(offset.toModel(), blur, spread, Color(color))
    is InnerShadowDto -> Effect.InnerShadow(offset.toModel(), blur, spread, Color(color))
    is LayerBlurDto -> Effect.LayerBlur(radius)
    is GlowDto -> Effect.Glow(radius, Color(color), intensity)
}

private fun Appearance.toDto() = AppearanceDto(
    fills.map { it.toDto() }, strokes.map { it.toDto() }, effects.map { it.toDto() }, opacity, blendMode.name,
)

private fun AppearanceDto.toModel() = Appearance(
    fills.map { it.toModel() }, strokes.map { it.toModel() }, effects.map { it.toModel() }, opacity,
    parseEnum<BlendMode>(blendMode, "blend mode"),
)

// ---- shape and layers ----
private fun Transform.toDto() = TransformDto(translation.toDto(), rotationRadians, scale.toDto())
private fun TransformDto.toModel() = Transform(translation.toModel(), rotationRadians, scale.toModel())

private fun Shape.toDto() = ShapeDto(geometry.toDto(), appearance.toDto(), transform.toDto())
private fun ShapeDto.toModel() = Shape(geometry.toModel(), appearance.toModel(), transform.toModel())

private fun LayerNode.toDto(): LayerDto = when (this) {
    is ShapeLayer -> ShapeLayerDto(id.value, name, visible, locked, shape.toDto())
    is GroupLayer -> GroupLayerDto(id.value, name, visible, locked, children.map { it.toDto() })
}

private fun LayerDto.toModel(): LayerNode = when (this) {
    is ShapeLayerDto -> ShapeLayer(LayerId(id), name, visible, locked, shape.toModel())
    is GroupLayerDto -> GroupLayer(LayerId(id), name, visible, locked, children.map { it.toModel() })
}

// ---- states, export, document ----
private fun StateSet.toDtos(): List<StateEntryDto> =
    overrides.entries.sortedBy { it.key.ordinal }.flatMap { (state, perLayer) ->
        perLayer.entries.sortedBy { it.key.value }.map { (id, o) ->
            StateEntryDto(state.name, id.value, o.appearance?.toDto(), o.transform?.toDto())
        }
    }

private fun List<StateEntryDto>.toStateSet(): StateSet {
    val map = LinkedHashMap<ButtonState, LinkedHashMap<LayerId, StateOverride>>()
    for (e in this) {
        val state = parseEnum<ButtonState>(e.state, "button state")
        val perLayer = map.getOrPut(state) { LinkedHashMap() }
        val previous = perLayer.put(LayerId(e.layerId), StateOverride(e.appearance?.toModel(), e.transform?.toModel()))
        if (previous != null) throw FormatProblem("Duplicate state override for layer ${e.layerId}")
    }
    return StateSet(map)
}

private fun ExportSettings.toDto() = ExportDto(targets.map { it.name }.sorted(), pngScales, transparentBackground)
private fun ExportDto.toModel() = ExportSettings(
    targets.map { parseEnum<ExportTarget>(it, "export target") }.toSet(), pngScales, transparentBackground,
)

internal fun Document.toDto() = DocumentDto(
    name = name,
    canvas = CanvasDto(canvas.width, canvas.height),
    layers = layers.map { it.toDto() },
    states = states.toDtos(),
    export = export.toDto(),
    counters = CountersDto(counters.nextAnchor, counters.nextLayer),
)

internal fun DocumentDto.toModel() = Document(
    name = name,
    canvas = CanvasSize(canvas.width, canvas.height),
    layers = layers.map { it.toModel() },
    states = states.toStateSet(),
    export = export.toModel(),
    counters = IdCounters(counters.nextAnchor, counters.nextLayer),
)
