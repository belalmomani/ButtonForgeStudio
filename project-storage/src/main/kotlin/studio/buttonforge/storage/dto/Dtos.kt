package studio.buttonforge.storage.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/*
 * File format v1. These classes are the file schema: they are independent of the in-memory model so the
 * model can evolve without silently changing what is written. No default values on purpose: every field is
 * always written, and a missing field means a damaged or foreign file.
 */

@Serializable data class ManifestDto(val formatVersion: Int, val writtenBy: String)

@Serializable data class Vec2Dto(val x: Double, val y: Double)

@Serializable
data class AnchorDto(
    val id: Long,
    val position: Vec2Dto,
    val handleIn: Vec2Dto,
    val handleOut: Vec2Dto,
    val corner: String,
)

@Serializable data class ContourDto(val anchors: List<AnchorDto>, val closed: Boolean)

@Serializable data class PathDto(val contours: List<ContourDto>, val fillRule: String)

@Serializable data class GradientStopDto(val offset: Double, val color: Int)

@Serializable
sealed interface PaintDto

@Serializable @SerialName("solid")
data class SolidPaintDto(val color: Int) : PaintDto

@Serializable @SerialName("linear")
data class LinearPaintDto(val start: Vec2Dto, val end: Vec2Dto, val stops: List<GradientStopDto>) : PaintDto

@Serializable @SerialName("radial")
data class RadialPaintDto(val center: Vec2Dto, val radius: Double, val stops: List<GradientStopDto>) : PaintDto

@Serializable @SerialName("sweep")
data class SweepPaintDto(val center: Vec2Dto, val stops: List<GradientStopDto>) : PaintDto

@Serializable data class FillDto(val paint: PaintDto, val opacity: Double, val enabled: Boolean)

@Serializable
data class StrokeDto(
    val paint: PaintDto,
    val width: Double,
    val cap: String,
    val join: String,
    val miterLimit: Double,
    val dash: List<Double>,
    val opacity: Double,
    val enabled: Boolean,
)

@Serializable
sealed interface EffectDto

@Serializable @SerialName("dropShadow")
data class DropShadowDto(val offset: Vec2Dto, val blur: Double, val spread: Double, val color: Int) : EffectDto

@Serializable @SerialName("innerShadow")
data class InnerShadowDto(val offset: Vec2Dto, val blur: Double, val spread: Double, val color: Int) : EffectDto

@Serializable @SerialName("layerBlur")
data class LayerBlurDto(val radius: Double) : EffectDto

@Serializable @SerialName("glow")
data class GlowDto(val radius: Double, val color: Int, val intensity: Double) : EffectDto

@Serializable
data class AppearanceDto(
    val fills: List<FillDto>,
    val strokes: List<StrokeDto>,
    val effects: List<EffectDto>,
    val opacity: Double,
    val blendMode: String,
)

@Serializable data class TransformDto(val translation: Vec2Dto, val rotationRadians: Double, val scale: Vec2Dto)

@Serializable data class ShapeDto(val geometry: PathDto, val appearance: AppearanceDto, val transform: TransformDto)

@Serializable
sealed interface LayerDto

@Serializable @SerialName("shape")
data class ShapeLayerDto(
    val id: Long,
    val name: String,
    val visible: Boolean,
    val locked: Boolean,
    val shape: ShapeDto,
) : LayerDto

@Serializable @SerialName("group")
data class GroupLayerDto(
    val id: Long,
    val name: String,
    val visible: Boolean,
    val locked: Boolean,
    val children: List<LayerDto>,
) : LayerDto

@Serializable
data class StateEntryDto(
    val state: String,
    val layerId: Long,
    val appearance: AppearanceDto?,
    val transform: TransformDto?,
)

@Serializable
data class ExportDto(val targets: List<String>, val pngScales: List<Double>, val transparentBackground: Boolean)

@Serializable data class CanvasDto(val width: Double, val height: Double)

@Serializable data class CountersDto(val nextAnchor: Long, val nextLayer: Long)

@Serializable
data class DocumentDto(
    val name: String,
    val canvas: CanvasDto,
    val layers: List<LayerDto>,
    val states: List<StateEntryDto>,
    val export: ExportDto,
    val counters: CountersDto,
)
