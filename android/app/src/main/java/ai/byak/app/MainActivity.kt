package ai.byak.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import ai.byak.app.data.ThemeMode
import ai.byak.app.ui.ByakApp
import ai.byak.app.ui.theme.ByakTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as ByakApplication
        setContent {
            val mode by app.settings.themeMode.collectAsState(initial = ThemeMode.System)
            val dynamic by app.settings.dynamicColor.collectAsState(initial = false)
            ByakTheme(mode, dynamic) { ByakApp(app) }
        }
    }
}
