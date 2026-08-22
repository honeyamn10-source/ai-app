package ai.byak.app.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

val Obsidian = Color(0xFF0B0C10)
val ObsidianElevated = Color(0xFF12131A)
val NeonPurple = Color(0xFF8A2BE2)
val NeonPurpleSoft = Color(0xFFB68CFF)
val CyberTeal = Color(0xFF00E5FF)
val PremiumGold = Color(0xFFFFD700)
val Glass = Color(0x1AFFFFFF)
val GlassBorder = Color(0x26FFFFFF)
val Ink = Color(0xFFF6F1FA)
val MutedInk = Color(0xFFBEB7C7)

private val DarkColors = darkColorScheme(
    primary = NeonPurpleSoft,
    onPrimary = Color(0xFF21003A),
    primaryContainer = Color(0xFF331352),
    onPrimaryContainer = Color(0xFFEBD9FF),
    secondary = CyberTeal,
    onSecondary = Color(0xFF002F35),
    secondaryContainer = Color(0xFF003E46),
    onSecondaryContainer = Color(0xFF9CF5FF),
    tertiary = PremiumGold,
    onTertiary = Color(0xFF352D00),
    background = Obsidian,
    onBackground = Ink,
    surface = ObsidianElevated,
    onSurface = Ink,
    surfaceVariant = Color(0xFF1A1B24),
    onSurfaceVariant = MutedInk,
    outline = Color(0xFF706A78),
    outlineVariant = GlassBorder,
    error = Color(0xFFFFB4AB),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF6B16B0),
    secondary = Color(0xFF006874),
    tertiary = Color(0xFF6C5E00),
    background = Color(0xFFFDF8FF),
    surface = Color(0xFFFFF9FF),
)

@Composable
fun ByakTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography(),
        content = content,
    )
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    val colors = CardDefaults.cardColors(containerColor = Glass)
    val border = BorderStroke(1.dp, GlassBorder)
    if (onClick == null) {
        Card(modifier = modifier, shape = RoundedCornerShape(24.dp), colors = colors, border = border, content = content)
    } else {
        Card(onClick = onClick, modifier = modifier, shape = RoundedCornerShape(24.dp), colors = colors, border = border, content = content)
    }
}
