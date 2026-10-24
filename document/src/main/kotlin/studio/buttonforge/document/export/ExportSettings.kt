package studio.buttonforge.document.export

enum class ExportTarget { Svg, Png, AndroidVectorDrawable, Unity, Unreal, Web }

data class ExportSettings(
    val targets: Set<ExportTarget> = emptySet(),
    val pngScales: List<Double> = listOf(1.0),
    val transparentBackground: Boolean = true,
) {
    init { require(pngScales.all { it > 0.0 && it.isFinite() }) { "PNG scales must be positive" } }
}
