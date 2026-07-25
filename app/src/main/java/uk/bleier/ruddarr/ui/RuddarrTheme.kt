package uk.bleier.ruddarr.ui

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private val Orchid = Color(0xFFD5B7FF)
private val OrchidDeep = Color(0xFF6F3F8B)
private val Coral = Color(0xFFFF8E94)
private val Lime = Color(0xFFD8FF93)
private val Ink = Color(0xFF17101D)
private val Night = Color(0xFF140D1A)
private val NightSurface = Color(0xFF25182E)
private val LightSurface = Color(0xFFFFF8FF)

private val LightScheme = lightColorScheme(
    primary = Color(0xFF683A86),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFF0DFFF),
    onPrimaryContainer = Color(0xFF2B043F),
    secondary = Color(0xFF9B354D),
    onSecondary = Color.White,
    tertiary = Color(0xFF496800),
    onTertiary = Color.White,
    surface = LightSurface,
    surfaceVariant = Color(0xFFF0E3F2),
    onSurface = Ink,
    onSurfaceVariant = Color(0xFF554B59),
)

private val DarkScheme = darkColorScheme(
    primary = Orchid,
    onPrimary = Color(0xFF30113F),
    primaryContainer = OrchidDeep,
    onPrimaryContainer = Color(0xFFFFE9FF),
    secondary = Coral,
    onSecondary = Color(0xFF5B1224),
    secondaryContainer = Color(0xFF873D4F),
    onSecondaryContainer = Color(0xFFFFE9E9),
    tertiary = Lime,
    onTertiary = Color(0xFF233A00),
    tertiaryContainer = Color(0xFF466000),
    onTertiaryContainer = Color(0xFFE0FFAB),
    surface = Night,
    surfaceVariant = NightSurface,
    onSurface = Color(0xFFFFF5FF),
    onSurfaceVariant = Color(0xFFE2D2E5),
    outline = Color(0xFFAD97B0),
    outlineVariant = Color(0xFF59465E),
)

@Composable
fun RuddarrTheme(
    darkTheme: Boolean,
    useDynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colors: ColorScheme = when {
        useDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> DarkScheme
        else -> LightScheme
    }
    MaterialExpressiveTheme(
        colorScheme = colors,
        motionScheme = MotionScheme.expressive(),
        content = content,
    )
}

@Composable
fun RuddarrBackdrop(content: @Composable () -> Unit) {
    val colors = androidx.compose.material3.MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        colors.surface,
                        colors.surfaceVariant.copy(alpha = 0.74f),
                        colors.surface,
                    ),
                ),
            ),
        content = {
            CompositionLocalProvider(LocalContentColor provides colors.onSurface) {
                content()
            }
        },
    )
}

val GlassContentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
