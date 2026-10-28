package studio.buttonforge

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.AndroidViewModel
import studio.buttonforge.geometryandroid.SkiaBooleanOps
import studio.buttonforge.document.CanvasSize
import studio.buttonforge.document.Document
import studio.buttonforge.editorui.EditorHost
import studio.buttonforge.storage.ProjectFiles
import studio.buttonforge.storage.ReadError
import studio.buttonforge.storage.ReadResult
import studio.buttonforge.storage.SaveException
import java.io.File

/**
 * Phase 2 keeps one project in the app's private storage. "Save" writes project.bfs. Unsaved changes are written to a
 * separate recovery file when the app goes to the background, and restored at the next start. All writes are atomic
 * (temp file, then rename).
 */
class EditorViewModel(app: Application) : AndroidViewModel(app) {
    private val file = File(app.filesDir, "project.bfs")
    private val recovery = File(app.filesDir, "project.recovery.bfs")
    private val files = ProjectFiles()

    val host = EditorHost(blank(), SkiaBooleanOps())
    var message: String? by mutableStateOf(null)
        private set

    /** Output of the boolean self-check, or null when no dialog should be shown. */
    var labReport: List<String>? by mutableStateOf(null)
        private set
    var labRunning: Boolean by mutableStateOf(false)
        private set

    /** Runs the boolean self-check off the main thread (the 500-point cases can take a while). */
    fun runBooleanLab() {
        if (labRunning) return
        labRunning = true
        Thread {
            val report = try {
                BooleanLab.run()
            } catch (e: Throwable) {
                listOf("فشل الاختبار: $e")
            }
            Handler(Looper.getMainLooper()).post {
                labReport = report
                labRunning = false
            }
        }.start()
    }

    fun closeLabReport() {
        labReport = null
    }

    /** What is on disk (saved or recovered), to tell whether there is anything new to keep. */
    private var persisted: Document? = null

    init {
        if (recovery.exists()) {
            if (!load(recovery, "تم استرجاع آخر تعديلات غير محفوظة")) loadSaved()
        } else {
            loadSaved()
        }
    }

    private fun loadSaved() {
        if (file.exists()) load(file, null)
    }

    fun save() {
        message = try {
            files.save(file.toPath(), host.session.document)
            persisted = host.session.document
            recovery.delete()
            "تم الحفظ"
        } catch (e: SaveException) {
            "تعذّر الحفظ: ${e.message}"
        } catch (e: IllegalArgumentException) {
            "المستند غير صالح للحفظ: ${e.message}"
        }
    }

    /** Called when the app leaves the foreground. Writes the recovery file only if something changed. */
    fun keepUnsavedWork() {
        val doc = host.session.document
        if (doc == persisted) return
        try {
            files.save(recovery.toPath(), doc)
            persisted = doc
        } catch (e: SaveException) {
            // nothing more to do here: the user can still save by hand
        } catch (e: IllegalArgumentException) {
        }
    }

    fun open() {
        if (!file.exists()) {
            message = "لا يوجد مشروع محفوظ"
            return
        }
        load(file, "تم فتح المشروع")
        recovery.delete()
    }

    fun newProject() {
        host.replaceDocument(blank())
        message = null
    }

    private fun load(from: File, success: String?): Boolean =
        when (val result = files.open(from.toPath())) {
            is ReadResult.Success -> {
                host.replaceDocument(result.document)
                persisted = result.document
                message = success
                true
            }
            is ReadResult.Failure -> {
                message = describe(result.error)
                false
            }
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
