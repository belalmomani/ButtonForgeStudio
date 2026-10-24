package studio.buttonforge

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import studio.buttonforge.document.CanvasSize
import studio.buttonforge.document.Document
import studio.buttonforge.editorui.EditorHost
import studio.buttonforge.storage.ProjectFiles
import studio.buttonforge.storage.ReadError
import studio.buttonforge.storage.ReadResult
import studio.buttonforge.storage.SaveException
import java.io.File

/** Phase 1 keeps a single project in the app's private storage. Saves are atomic (temp file, then rename). */
class EditorViewModel(app: Application) : AndroidViewModel(app) {
    private val file = File(app.filesDir, "project.bfs")
    private val files = ProjectFiles()

    val host = EditorHost(blank())
    var message: String? by mutableStateOf(null)
        private set

    init {
        if (file.exists()) open()
    }

    fun save() {
        message = try {
            files.save(file.toPath(), host.session.document)
            "تم الحفظ"
        } catch (e: SaveException) {
            "تعذّر الحفظ: ${e.message}"
        } catch (e: IllegalArgumentException) {
            "المستند غير صالح للحفظ: ${e.message}"
        }
    }

    fun open() {
        if (!file.exists()) {
            message = "لا يوجد مشروع محفوظ"
            return
        }
        when (val result = files.open(file.toPath())) {
            is ReadResult.Success -> {
                host.replaceDocument(result.document)
                message = "تم فتح المشروع"
            }
            is ReadResult.Failure -> message = describe(result.error)
        }
    }

    fun newProject() {
        host.replaceDocument(blank())
        message = null
    }

    private fun describe(error: ReadError): String = when (error) {
        is ReadError.NotAProject -> "الملف ليس مشروعًا صالحًا"
        is ReadError.MissingEntry -> "الملف ناقص: ${error.name}"
        is ReadError.Corrupt -> "الملف تالف: ${error.detail}"
        is ReadError.UnsupportedFutureVersion -> "المشروع أحدث من هذا الإصدار (${error.found} > ${error.supported})"
        is ReadError.MissingMigration -> "لا توجد ترقية للإصدار ${error.from}"
        is ReadError.Invalid -> "المشروع غير صالح: ${error.problems.firstOrNull().orEmpty()}"
        is ReadError.Io -> "تعذّرت القراءة: ${error.detail}"
    }

    private companion object {
        fun blank() = Document(name = "بدون عنوان", canvas = CanvasSize(512.0, 512.0))
    }
}
