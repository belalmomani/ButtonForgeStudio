package studio.buttonforge

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import studio.buttonforge.editorui.EditorScreen

class MainActivity : ComponentActivity() {
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
    )
}
