package studio.buttonforge.export

import studio.buttonforge.document.Document

/** A self-contained HTML button: the SVG inline, a text slot on top, and CSS for hover, press, focus and disabled. */
internal object WebWriter {
    fun write(doc: Document, items: List<DrawItem>, report: ReportBuilder, title: String, cssName: String): String {
        val svg = SvgWriter.write(doc, items, report, title, idPrefix = "$cssName-g", withXmlHeader = false).trim()
        val width = Format.num(doc.canvas.width)
        val c = "bf-$cssName"
        val label = Format.escape(title)
        return buildString {
            appendLine("<!DOCTYPE html>")
            appendLine("<html lang=\"ar\" dir=\"rtl\">")
            appendLine("<head>")
            appendLine("<meta charset=\"utf-8\">")
            appendLine("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">")
            appendLine("<title>$label</title>")
            appendLine("<style>")
            appendLine("  .$c {")
            appendLine("    position: relative; display: inline-block; padding: 0; border: 0; background: none;")
            appendLine("    cursor: pointer; width: ${width}px; max-width: 100%; line-height: 0;")
            appendLine("    transition: transform .08s ease, filter .12s ease;")
            appendLine("  }")
            appendLine("  .$c svg { width: 100%; height: auto; display: block; }")
            appendLine("  .$c .bf-label {")
            appendLine("    position: absolute; inset: 0; display: flex; align-items: center; justify-content: center;")
            appendLine("    font: 600 18px/1 system-ui, sans-serif; color: #fff; pointer-events: none;")
            appendLine("  }")
            appendLine("  .$c:hover:not(:disabled) { filter: brightness(1.08); }")
            appendLine("  .$c:active:not(:disabled) { transform: scale(.97); }")
            appendLine("  .$c:focus-visible { outline: 3px solid #1565c0; outline-offset: 3px; }")
            appendLine("  .$c:disabled { filter: grayscale(1) opacity(.55); cursor: not-allowed; }")
            appendLine("</style>")
            appendLine("</head>")
            appendLine("<body>")
            appendLine("<button class=\"$c\" type=\"button\" aria-label=\"$label\">")
            appendLine(svg)
            appendLine("<span class=\"bf-label\">Button</span>")
            appendLine("</button>")
            appendLine("</body>")
            appendLine("</html>")
        }
    }
}
