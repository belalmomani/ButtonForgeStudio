package studio.buttonforge

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
    EditorScreen(
        host = vm.host,
        onSave = vm::save,
        onOpen = vm::open,
        onNew = vm::newProject,
        message = vm.message,
        extraActions = {
            OutlinedButton(onClick = vm::runBooleanLab, enabled = !vm.labRunning) {
                Text(if (vm.labRunning) "...يعمل" else "اختبار البولينية")
            }
        },
    )
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
