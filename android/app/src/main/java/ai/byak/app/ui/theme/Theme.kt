package ai.byak.app.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

val Obsidian = Color(0xFF05060A)
val ObsidianElevated = Color(0xFF0E1019)
val NeonPurple = Color(0xFF9D4DFF)
val NeonPurpleSoft = Color(0xFFC266FF)
val QuantumBlue = Color(0xFF4D6FFF)
val CyberTeal = Color(0xFF58F6FF)
val PremiumGold = Color(0xFFFFCF66)
val Glass = Color(0x14FFFFFF)
val GlassBorder = Color(0x33588ADE)
val Ink = Color(0xFFF6F7FF)
val MutedInk = Color(0xFF9DA8BD)

private val DarkColors = darkColorScheme(
    primary = NeonPurpleSoft,
    onPrimary = Color(0xFF24003D),
    primaryContainer = Color(0xFF2A1645),
    onPrimaryContainer = Color(0xFFF1E4FF),
    secondary = CyberTeal,
    onSecondary = Color(0xFF002E33),
    secondaryContainer = Color(0xFF07383F),
    onSecondaryContainer = Color(0xFFC4FAFF),
    tertiary = PremiumGold,
    onTertiary = Color(0xFF302700),
    background = Obsidian,
    onBackground = Ink,
    surface = ObsidianElevated,
    onSurface = Ink,
    surfaceVariant = Color(0xFF171923),
    onSurfaceVariant = MutedInk,
    outline = Color(0xFF65718B),
    outlineVariant = GlassBorder,
    error = Color(0xFFFFB4AB),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF7130C7),
    secondary = Color(0xFF006A74),
    tertiary = Color(0xFF735E00),
    background = Color(0xFFF8F7FF),
    surface = Color(0xFFFFFFFF),
)

private val ByakShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(30.dp),
)

@Composable
fun ByakTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography(),
        shapes = ByakShapes,
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
