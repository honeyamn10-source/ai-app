package ai.byak.app.ui.theme

import android.os.Build
import ai.byak.app.data.ThemeMode
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val Dark = darkColorScheme(
    primary = Color(0xFF8B7CFF), secondary = Color(0xFF43D9B8), tertiary = Color(0xFFFFB45E),
    background = Color(0xFF0A0B10), surface = Color(0xFF12141C), surfaceVariant = Color(0xFF1A1D28),
    onPrimary = Color.White, onBackground = Color(0xFFF5F3FF), onSurface = Color(0xFFF5F3FF), outline = Color(0xFF474B5F)
)
private val Light = lightColorScheme(
    primary = Color(0xFF5848D8), secondary = Color(0xFF007B68), tertiary = Color(0xFF8A4F00),
    background = Color(0xFFF8F7FC), surface = Color.White, surfaceVariant = Color(0xFFEFEDF7),
    onBackground = Color(0xFF171620), onSurface = Color(0xFF171620)
)

val dynamicColorSupported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

@Composable fun ByakTheme(mode: ThemeMode = ThemeMode.System, dynamicColor: Boolean = false, content: @Composable () -> Unit) {
    val dark = when (mode) { ThemeMode.System -> isSystemInDarkTheme(); ThemeMode.Light -> false; ThemeMode.Dark -> true }
    val context = LocalContext.current
    val scheme = when {
        dynamicColor && dynamicColorSupported -> if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> Dark
        else -> Light
    }
    MaterialTheme(colorScheme = scheme, typography = Typography(), content = content)
}
