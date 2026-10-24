package studio.buttonforge.document.appearance

/** 32-bit ARGB, same layout as android.graphics.Color. */
@JvmInline
value class Color(val argb: Int) {
    companion object {
        val Transparent = Color(0)
        val Black = Color(0xFF000000.toInt())
        val White = Color(0xFFFFFFFF.toInt())
    }
}
