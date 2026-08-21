package ai.byak.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import ai.byak.app.ui.ByakApp
import ai.byak.app.ui.theme.ByakTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as ByakApplication
        setContent { ByakTheme { ByakApp(app.api, app.billing) } }
    }
}
