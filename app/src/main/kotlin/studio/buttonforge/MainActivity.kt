package studio.buttonforge

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import android.net.Uri
import androidx.lifecycle.viewmodel.compose.viewModel
import studio.buttonforge.editorui.EditorScreen

class MainActivity : ComponentActivity() {
    private val editor: EditorViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize().systemBarsPadding()) {
                    Workspace()
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        editor.keepUnsavedWork()
    }
}

@Composable
private fun Workspace() {
    val vm: EditorViewModel = viewModel()
    val saver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri: Uri? ->
        if (uri != null) vm.writeExport(uri)
    }
    val clipboard = LocalClipboardManager.current

    EditorScreen(
        host = vm.host,
        onSave = vm::save,
        onOpen = vm::open,
        onNew = vm::newProject,
        message = vm.message,
        extraActions = {
            Button(onClick = vm::prepareExport, enabled = !vm.exportRunning) {
                Text(if (vm.exportRunning) "...يُجهَّز" else "تصدير")
            }
            OutlinedButton(onClick = vm::runBooleanLab, enabled = !vm.labRunning) {
                Text(if (vm.labRunning) "...يعمل" else "اختبار البولينية")
            }
        },
    )

    vm.exportBundle?.let { bundle ->
        fun copy(label: String, matches: (String) -> Boolean) {
            val file = bundle.files.firstOrNull { matches(it.path) }
            if (file == null) {
                vm.showMessage("لا يوجد ملف $label")
            } else {
                clipboard.setText(AnnotatedString(file.text))
                vm.showMessage("تم نسخ $label")
            }
        }
        AlertDialog(
            onDismissRequest = vm::closeExport,
            confirmButton = { TextButton(onClick = { saver.launch(vm.exportFileName()) }) { Text("حفظ كملف ZIP") } },
            dismissButton = { TextButton(onClick = vm::closeExport) { Text("إغلاق") } },
            title = { Text("تصدير للمبرمجين") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text("راجع ما سيُحفظ أو يُقرَّب أو يُحذف قبل التصدير:")
                    bundle.report.lines().forEach { Text(it) }
                    Text("")
                    Text("نسخ سريع إلى الحافظة:")
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(onClick = { copy("XML أندرويد") { it.startsWith("android/res/drawable/") && it.endsWith(".xml") } }) { Text("XML أندرويد") }
                        OutlinedButton(onClick = { copy("كود Compose") { it.endsWith(".kt") } }) { Text("كود Compose") }
                        OutlinedButton(onClick = { copy("SVG") { it.endsWith(".svg") } }) { Text("SVG") }
                        OutlinedButton(onClick = { copy("HTML") { it.endsWith(".html") } }) { Text("زر HTML") }
                    }
                    Text("")
                    Text("الملف المضغوط يحوي: أندرويد (VectorDrawable وCompose وPNG بخمس كثافات) والويب (SVG وزر HTML وPNG).")
                }
            },
        )
    }

    vm.labReport?.let { lines ->
        AlertDialog(
            onDismissRequest = vm::closeLabReport,
            confirmButton = { TextButton(onClick = vm::closeLabReport) { Text("إغلاق") } },
            title = { Text("نتيجة اختبار البولينية") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    lines.forEach { Text(it) }
                }
            },
        )
    }
}
