package ai.byak.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Dark = darkColorScheme(
    primary = Color(0xFFB8A8FF), secondary = Color(0xFF78D8C5), tertiary = Color(0xFFFFB878),
    background = Color(0xFF181714), surface = Color(0xFF211F1B), surfaceVariant = Color(0xFF2C2924),
    primaryContainer = Color(0xFF352F57), onPrimary = Color(0xFF241B55),
    onBackground = Color(0xFFF7F1E8), onSurface = Color(0xFFF7F1E8), onSurfaceVariant = Color(0xFFD8D0C5), outline = Color(0xFF746E66)
)
private val Light = lightColorScheme(
    primary = Color(0xFF6555C7), secondary = Color(0xFF13766A), tertiary = Color(0xFFB7642A),
    background = Color(0xFFFAF7F2), surface = Color(0xFFFFFCF8), surfaceVariant = Color(0xFFF1ECE5),
    primaryContainer = Color(0xFFE9E3FF), onPrimary = Color.White,
    onBackground = Color(0xFF282521), onSurface = Color(0xFF282521), onSurfaceVariant = Color(0xFF625D56), outline = Color(0xFF8A837A)
)

@Composable fun ByakTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) Dark else Light, typography = Typography(), content = content)
}
