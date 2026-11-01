package studio.buttonforge.export

import kotlin.math.roundToLong

internal object Format {
    /** At most 3 decimals, no trailing zeros, never "-0". */
    fun num(d: Double): String {
        val r = (d * 1000.0).roundToLong() / 1000.0
        val s = if (r == r.toLong().toDouble()) r.toLong().toString() else r.toString()
        return if (s == "-0") "0" else s
    }

    fun floatLiteral(d: Double): String = num(d) + "f"

    fun argb(argb: Int): String = "#%08X".format(argb)
    fun rgb(argb: Int): String = "#%06X".format(argb and 0xFFFFFF)
    fun alpha(argb: Int): Double = (argb ushr 24) / 255.0

    fun escape(text: String): String =
        text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
}

/** File and identifier names derived from the project name. Anything unusable falls back to "button". */
object Names {
    private fun words(raw: String): List<String> =
        raw.split(Regex("[^A-Za-z0-9]+")).filter { it.isNotEmpty() }

    /** lower_snake_case, valid as an Android resource name. */
    fun androidResource(raw: String): String {
        val s = words(raw).joinToString("_") { it.lowercase() }
        return when {
            s.isEmpty() -> "button"
            s[0].isDigit() -> "button_$s"
            else -> s
        }
    }

    /** PascalCase, valid as a Kotlin identifier. */
    fun pascal(raw: String): String {
        val s = words(raw).joinToString("") { it.lowercase().replaceFirstChar { c -> c.uppercase() } }
        return when {
            s.isEmpty() -> "Button"
            s[0].isDigit() -> "Button$s"
            else -> s
        }
    }

    /** kebab-case for web files and CSS classes. */
    fun web(raw: String): String = androidResource(raw).replace('_', '-')
}
