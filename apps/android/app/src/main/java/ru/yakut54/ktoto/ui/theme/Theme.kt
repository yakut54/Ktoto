package ru.yakut54.ktoto.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import androidx.core.view.WindowCompat

private val LightColors = lightColorScheme(
    primary              = Blue40,
    onPrimary            = Color.White,
    primaryContainer     = Blue90,
    onPrimaryContainer   = Blue10,
    secondary            = Slate50,
    onSecondary          = Color.White,
    secondaryContainer   = Slate90,
    onSecondaryContainer = Slate30,
    error                = Red40,
    onError              = Color.White,
    background           = Color(0xFFF8F9FF),
    onBackground         = Color(0xFF191C20),
    surface              = Color.White,
    onSurface            = Color(0xFF191C20),
    surfaceVariant       = Color(0xFFDFE2EB),
    onSurfaceVariant     = Color(0xFF43474E),
    outline              = Color(0xFF73777F),
)

private val DarkColors = darkColorScheme(
    primary              = Blue80,
    onPrimary            = Blue20,
    primaryContainer     = Color(0xFF004799),
    onPrimaryContainer   = Blue90,
    secondary            = Slate80,
    onSecondary          = Slate30,
    secondaryContainer   = Color(0xFF3E4758),
    onSecondaryContainer = Slate90,
    error                = Red80,
    background           = Color(0xFF111318),
    onBackground         = Color(0xFFE2E2E9),
    surface              = Color(0xFF191C20),
    onSurface            = Color(0xFFE2E2E9),
    surfaceVariant       = Color(0xFF43474E),
    onSurfaceVariant     = Color(0xFFC3C7CF),
    outline              = Color(0xFF8D9199),
)

@Composable
fun KtotoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    fontScale: Float = 1.0f,
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }
    val density = LocalDensity.current
    val scaledDensity = Density(density.density, fontScale)
    CompositionLocalProvider(LocalDensity provides scaledDensity) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content,
        )
    }
}
