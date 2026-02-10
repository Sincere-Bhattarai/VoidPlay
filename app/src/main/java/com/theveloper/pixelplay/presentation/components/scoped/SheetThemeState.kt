package com.theveloper.pixelplay.presentation.components.scoped

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.util.lerp
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.preferences.ThemePreference
import com.theveloper.pixelplay.presentation.viewmodel.ColorSchemePair

internal data class SheetThemeState(
    val albumColorScheme: ColorScheme,
    val miniPlayerScheme: ColorScheme,
    val isPreparingPlayback: Boolean,
    val miniReadyAlpha: Float,
    val miniAppearScale: Float,
    val playerAreaBackground: Color,
    val effectivePlayerAreaElevation: Dp,
    val miniAlpha: Float
)

@Composable
internal fun rememberSheetThemeState(
    activePlayerSchemePair: ColorSchemePair?,
    isDarkTheme: Boolean,
    playerThemePreference: String,
    currentSong: Song?,
    themedAlbumArtUri: String?,
    preparingSongId: String?,
    playerContentExpansionFraction: Animatable<Float, AnimationVector1D>,
    systemColorScheme: ColorScheme
): SheetThemeState {
    val isAlbumArtTheme = playerThemePreference == ThemePreference.ALBUM_ART
    val hasAlbumArt = currentSong?.albumArtUriString != null
    val needsAlbumScheme = isAlbumArtTheme && hasAlbumArt

    val activePlayerScheme = remember(activePlayerSchemePair, isDarkTheme) {
        activePlayerSchemePair?.let { if (isDarkTheme) it.dark else it.light }
    }
    val currentSongActiveScheme = remember(
        activePlayerScheme,
        currentSong?.albumArtUriString,
        themedAlbumArtUri
    ) {
        if (
            activePlayerScheme != null &&
            !currentSong?.albumArtUriString.isNullOrBlank() &&
            currentSong?.albumArtUriString == themedAlbumArtUri
        ) {
            activePlayerScheme
        } else {
            null
        }
    }

    var lastAlbumScheme by remember { mutableStateOf<ColorScheme?>(null) }
    var lastAlbumSchemeSongId by remember { mutableStateOf<String?>(null) }
    // When song changes, keep lastAlbumScheme as cross-song fallback
    // to prevent flicker to system color while new color loads.
    // Only update the tracked song ID so the new scheme replaces it once ready.
    LaunchedEffect(currentSong?.id) {
        if (currentSong?.id != lastAlbumSchemeSongId) {
            lastAlbumSchemeSongId = currentSong?.id
        }
    }
    LaunchedEffect(currentSongActiveScheme, currentSong?.id) {
        val currentSongId = currentSong?.id
        if (currentSongId != null && currentSongActiveScheme != null) {
            lastAlbumScheme = currentSongActiveScheme
            lastAlbumSchemeSongId = currentSongId
        }
    }

    val sameSongLastAlbumScheme = remember(currentSong?.id, lastAlbumSchemeSongId, lastAlbumScheme) {
        if (currentSong?.id != null && currentSong?.id == lastAlbumSchemeSongId) {
            lastAlbumScheme
        } else {
            null
        }
    }
    val isPreparingPlayback = remember(preparingSongId, currentSong?.id) {
        preparingSongId != null && preparingSongId == currentSong?.id
    }

    // Capture nullable var for smart-cast
    val lastAlbumSchemeSnapshot = lastAlbumScheme

    // Use lastAlbumScheme (previous song's color) as fallback while new color loads
    val rawAlbumColorScheme = if (isAlbumArtTheme) {
        currentSongActiveScheme ?: lastAlbumSchemeSnapshot ?: systemColorScheme
    } else {
        systemColorScheme
    }

    val rawMiniPlayerScheme = when {
        !needsAlbumScheme -> systemColorScheme
        currentSongActiveScheme != null -> currentSongActiveScheme
        lastAlbumSchemeSnapshot != null -> lastAlbumSchemeSnapshot
        else -> systemColorScheme
    }

    // Animate color transitions for smooth cross-song color changes
    val colorAnimSpec = spring<Color>(stiffness = Spring.StiffnessLow)
    val albumColorScheme = animateColorScheme(rawAlbumColorScheme, colorAnimSpec)
    val miniPlayerScheme = animateColorScheme(rawMiniPlayerScheme, colorAnimSpec)
    val miniAppearProgress = remember { Animatable(0f) }
    LaunchedEffect(currentSong?.id) {
        if (currentSong == null) {
            miniAppearProgress.snapTo(0f)
        } else if (miniAppearProgress.value < 1f) {
            miniAppearProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing)
            )
        }
    }

    val miniReadyAlpha = miniAppearProgress.value
    val miniAppearScale = lerp(0.985f, 1f, miniAppearProgress.value)
    val playerAreaBackground = miniPlayerScheme.primaryContainer

    val t = rememberExpansionTransition(playerContentExpansionFraction.value)
    val playerAreaElevation by t.animateDp(label = "elev") { expansion ->
        lerp(2.dp, 12.dp, expansion)
    }
    val effectivePlayerAreaElevation = lerp(0.dp, playerAreaElevation, miniReadyAlpha)
    val miniAlpha by t.animateFloat(label = "miniAlpha") { expansion ->
        (1f - expansion * 2f).coerceIn(0f, 1f)
    }

    return SheetThemeState(
        albumColorScheme = albumColorScheme,
        miniPlayerScheme = miniPlayerScheme,
        isPreparingPlayback = isPreparingPlayback,
        miniReadyAlpha = miniReadyAlpha,
        miniAppearScale = miniAppearScale,
        playerAreaBackground = playerAreaBackground,
        effectivePlayerAreaElevation = effectivePlayerAreaElevation,
        miniAlpha = miniAlpha
    )
}

/**
 * Animates all key properties of a [ColorScheme] for smooth color transitions.
 */
@Composable
private fun animateColorScheme(
    target: ColorScheme,
    animationSpec: androidx.compose.animation.core.AnimationSpec<Color>
): ColorScheme {
    return target.copy(
        primary = animateColorAsState(target.primary, animationSpec, label = "primary").value,
        onPrimary = animateColorAsState(target.onPrimary, animationSpec, label = "onPrimary").value,
        primaryContainer = animateColorAsState(target.primaryContainer, animationSpec, label = "primaryContainer").value,
        onPrimaryContainer = animateColorAsState(target.onPrimaryContainer, animationSpec, label = "onPrimaryContainer").value,
        secondary = animateColorAsState(target.secondary, animationSpec, label = "secondary").value,
        onSecondary = animateColorAsState(target.onSecondary, animationSpec, label = "onSecondary").value,
        secondaryContainer = animateColorAsState(target.secondaryContainer, animationSpec, label = "secondaryContainer").value,
        onSecondaryContainer = animateColorAsState(target.onSecondaryContainer, animationSpec, label = "onSecondaryContainer").value,
        tertiary = animateColorAsState(target.tertiary, animationSpec, label = "tertiary").value,
        onTertiary = animateColorAsState(target.onTertiary, animationSpec, label = "onTertiary").value,
        tertiaryContainer = animateColorAsState(target.tertiaryContainer, animationSpec, label = "tertiaryContainer").value,
        onTertiaryContainer = animateColorAsState(target.onTertiaryContainer, animationSpec, label = "onTertiaryContainer").value,
        surface = animateColorAsState(target.surface, animationSpec, label = "surface").value,
        onSurface = animateColorAsState(target.onSurface, animationSpec, label = "onSurface").value,
        surfaceVariant = animateColorAsState(target.surfaceVariant, animationSpec, label = "surfaceVariant").value,
        onSurfaceVariant = animateColorAsState(target.onSurfaceVariant, animationSpec, label = "onSurfaceVariant").value,
        background = animateColorAsState(target.background, animationSpec, label = "background").value,
        onBackground = animateColorAsState(target.onBackground, animationSpec, label = "onBackground").value,
        inverseSurface = animateColorAsState(target.inverseSurface, animationSpec, label = "inverseSurface").value,
        inverseOnSurface = animateColorAsState(target.inverseOnSurface, animationSpec, label = "inverseOnSurface").value,
        inversePrimary = animateColorAsState(target.inversePrimary, animationSpec, label = "inversePrimary").value,
        surfaceContainerLowest = animateColorAsState(target.surfaceContainerLowest, animationSpec, label = "surfaceContainerLowest").value,
        surfaceContainerLow = animateColorAsState(target.surfaceContainerLow, animationSpec, label = "surfaceContainerLow").value,
        surfaceContainer = animateColorAsState(target.surfaceContainer, animationSpec, label = "surfaceContainer").value,
        surfaceContainerHigh = animateColorAsState(target.surfaceContainerHigh, animationSpec, label = "surfaceContainerHigh").value,
        surfaceContainerHighest = animateColorAsState(target.surfaceContainerHighest, animationSpec, label = "surfaceContainerHighest").value,
        outline = animateColorAsState(target.outline, animationSpec, label = "outline").value,
        outlineVariant = animateColorAsState(target.outlineVariant, animationSpec, label = "outlineVariant").value,
        surfaceTint = animateColorAsState(target.surfaceTint, animationSpec, label = "surfaceTint").value,
        error = animateColorAsState(target.error, animationSpec, label = "error").value,
        onError = animateColorAsState(target.onError, animationSpec, label = "onError").value,
        errorContainer = animateColorAsState(target.errorContainer, animationSpec, label = "errorContainer").value,
        onErrorContainer = animateColorAsState(target.onErrorContainer, animationSpec, label = "onErrorContainer").value,
        scrim = animateColorAsState(target.scrim, animationSpec, label = "scrim").value,
        surfaceBright = animateColorAsState(target.surfaceBright, animationSpec, label = "surfaceBright").value,
        surfaceDim = animateColorAsState(target.surfaceDim, animationSpec, label = "surfaceDim").value,
    )
}
