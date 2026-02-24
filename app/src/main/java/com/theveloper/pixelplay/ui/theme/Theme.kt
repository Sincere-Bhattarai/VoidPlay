package com.theveloper.voidplay.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.theveloper.voidplay.presentation.viewmodel.ColorSchemePair
import androidx.core.graphics.ColorUtils
import androidx.compose.ui.unit.dp

val LocalVoidPlayDarkTheme = staticCompositionLocalOf { false }

val DarkColorScheme = darkColorScheme(
    primary = VoidPlayPurplePrimary,
    secondary = VoidPlayPink,
    tertiary = VoidPlayOrange,
    background = VoidPlayPurpleDark,
    surface = VoidPlaySurface,
    onPrimary = VoidPlayWhite,
    onSecondary = VoidPlayWhite,
    onTertiary = VoidPlayWhite,
    onBackground = VoidPlayWhite,
    onSurface = VoidPlayLightPurple, // Texto sobre superficies
    error = Color(0xFFFF5252),
    onError = VoidPlayWhite
)

val LightColorScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = VoidPlayWhite,
    primaryContainer = LightPrimaryContainer,
    onPrimaryContainer = LightOnPrimaryContainer,
    secondary = VoidPlayPink,
    onSecondary = VoidPlayWhite,
    secondaryContainer = VoidPlayPink.copy(alpha = 0.15f),
    onSecondaryContainer = VoidPlayPink.copy(alpha = 0.85f),
    tertiary = VoidPlayOrange,
    onTertiary = VoidPlayBlack,
    background = LightBackground,
    onBackground = LightOnSurface,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    outline = LightOutline,
    outlineVariant = LightOutline.copy(alpha = 0.6f),
    surfaceTint = LightPrimary,
    error = Color(0xFFD32F2F),
    onError = VoidPlayWhite
)

@Composable
fun VoidPlayTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    colorSchemePairOverride: ColorSchemePair? = null,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val finalColorScheme = when {
        colorSchemePairOverride == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            // Tema dinámico del sistema como prioridad si no hay override
            try {
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            } catch (e: Exception) {
                // Fallback a los defaults si dynamic colors falla (raro, pero posible en algunos dispositivos)
                if (darkTheme) DarkColorScheme else LightColorScheme
            }
        }
        colorSchemePairOverride != null -> {
            // Usar el esquema del álbum si se proporciona
            if (darkTheme) colorSchemePairOverride.dark else colorSchemePairOverride.light
        }
        // Fallback final a los defaults si no hay override ni dynamic colors aplicables
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            val statusBarElevation = if (darkTheme) 4.dp else 12.dp
            val elevatedSurface = finalColorScheme.surfaceColorAtElevation(statusBarElevation)
            val statusBarColor = Color(ColorUtils.blendARGB(finalColorScheme.background.toArgb(), elevatedSurface.toArgb(), 0.35f))
            window.statusBarColor = statusBarColor.toArgb()
            val isLightStatusBar = ColorUtils.calculateLuminance(statusBarColor.toArgb()) > 0.55
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = isLightStatusBar
        }
    }

    CompositionLocalProvider(LocalVoidPlayDarkTheme provides darkTheme) {
        MaterialTheme(
            colorScheme = finalColorScheme,
            typography = Typography,
            shapes = Shapes,
            content = content
        )
    }
}