package studio.buttonforge

import android.graphics.Bitmap
import android.graphics.Canvas
import studio.buttonforge.document.Document
import studio.buttonforge.export.Rasterizer
import studio.buttonforge.renderer.SceneRenderer
import java.io.ByteArrayOutputStream

/** Renders the design with the same code the editor uses, on a transparent background, and encodes it as PNG. */
class AndroidRasterizer : Rasterizer {
    override fun png(doc: Document, widthPx: Int, heightPx: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        try {
            val scale = widthPx / doc.canvas.width.toFloat()
            SceneRenderer.draw(Canvas(bitmap), doc, scale, 0f, 0f, background = false)
            val out = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            return out.toByteArray()
        } finally {
            bitmap.recycle()
        }
    }
}
