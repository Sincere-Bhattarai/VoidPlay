package com.voidcorp.voidplay.ui.theme

import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.voidcorp.voidplay.presentation.viewmodel.ColorSchemePair
import com.google.android.material.color.utilities.DynamicScheme
import com.google.android.material.color.utilities.Hct
import com.google.android.material.color.utilities.MathUtils
import com.google.android.material.color.utilities.QuantizerCelebi
import com.google.android.material.color.utilities.SchemeExpressive
import com.google.android.material.color.utilities.SchemeFruitSalad
import com.google.android.material.color.utilities.SchemeMonochrome
import com.google.android.material.color.utilities.SchemeNeutral
import com.google.android.material.color.utilities.SchemeTonalSpot
import com.google.android.material.color.utilities.SchemeVibrant
import com.voidcorp.voidplay.data.preferences.AlbumArtPaletteStyle
import androidx.core.graphics.scale
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt

data class ColorScoringConfig(
    val targetChroma: Double = 48.0,
    val weightProportion: Double = 0.7,
    val weightChromaAbove: Double = 0.3,
    val weightChromaBelow: Double = 0.1,
    val cutoffChroma: Double = 5.0,
    val cutoffExcitedProportion: Double = 0.01,
    val maxColorCount: Int = 4,
    val maxHueDifference: Int = 90,
    val minHueDifference: Int = 15
)

data class ColorExtractionConfig(
    val downscaleMaxDimension: Int = 128,
    val quantizerMaxColors: Int = 128,
    val scoring: ColorScoringConfig = ColorScoringConfig()
)

private data class ScoredHct(
    val hct: Hct,
    val score: Double
)

private val extractedColorCache = LruCache<Int, Color>(32)
private const val GRAYSCALE_CHROMA_THRESHOLD = 10.0
private const val NEUTRAL_PIXEL_CHROMA_THRESHOLD = 8.0
private const val HIGH_CHROMA_THRESHOLD = 18.0
private const val REQUIRED_NEUTRAL_POPULATION = 0.92
private const val MAX_HIGH_CHROMA_POPULATION = 0.03
private const val MAX_WEIGHTED_CHROMA_FOR_NEUTRAL = 9.0

fun clearExtractedColorCache() {
    extractedColorCache.evictAll()
}

fun extractSeedColor(
    bitmap: Bitmap,
    config: ColorExtractionConfig = ColorExtractionConfig()
): Color {
    val cacheKey = 31 * bitmap.hashCode() + config.hashCode()
    extractedColorCache.get(cacheKey)?.let { return it }

    val workingBitmap = resizeForExtraction(bitmap, config.downscaleMaxDimension)

    val seedColor = runCatching {
        val pixels = IntArray(workingBitmap.width * workingBitmap.height)
        workingBitmap.getPixels(
            pixels,
            0,
            workingBitmap.width,
            0,
            0,
            workingBitmap.width,
            workingBitmap.height
        )

        val fallbackArgb = averageColorArgb(pixels)
        val quantized = QuantizerCelebi.quantize(pixels, config.quantizerMaxColors)
        val mostlyNeutralArtwork = isMostlyNeutralArtwork(quantized)

        if (mostlyNeutralArtwork) {
            val avgHct = Hct.fromInt(fallbackArgb)
            val neutralSeed = Hct.from(avgHct.hue, 0.0, avgHct.tone).toInt()
            return@runCatching Color(neutralSeed)
        }

        val rankedSeeds = scoreQuantizedColors(
            colorsToPopulation = quantized,
            scoring = config.scoring,
            fallbackColorArgb = fallbackArgb
        )

        Color(rankedSeeds.firstOrNull() ?: fallbackArgb)
    }.getOrElse { DarkColorScheme.primary }

    extractedColorCache.put(cacheKey, seedColor)
    if (workingBitmap !== bitmap) {
        workingBitmap.recycle()
    }
    return seedColor
}

fun generateColorSchemeFromSeed(
    seedColor: Color,
    paletteStyle: AlbumArtPaletteStyle = AlbumArtPaletteStyle.default
): ColorSchemePair {
    return runCatching {
        val sourceHct = Hct.fromInt(seedColor.toArgb())
        val shouldForceNeutral = sourceHct.chroma < GRAYSCALE_CHROMA_THRESHOLD

        val lightScheme = createDynamicScheme(
            sourceHct = sourceHct,
            paletteStyle = paletteStyle,
            isDark = false,
            forceNeutral = shouldForceNeutral
        ).toComposeColorScheme()
        val darkScheme = createDynamicScheme(
            sourceHct = sourceHct,
            paletteStyle = paletteStyle,
            isDark = true,
            forceNeutral = shouldForceNeutral
        ).toComposeColorScheme()
        ColorSchemePair(lightScheme, darkScheme)
    }.getOrElse {
        ColorSchemePair(LightColorScheme, DarkColorScheme)
    }
}

private fun resizeForExtraction(bitmap: Bitmap, maxDimension: Int): Bitmap {
    if (maxDimension <= 0) return bitmap
    if (bitmap.width <= maxDimension && bitmap.height <= maxDimension) return bitmap
    val scale = maxDimension.toFloat() / max(bitmap.width, bitmap.height).toFloat()
    return bitmap.scale(
        width = (bitmap.width * scale).roundToInt().coerceAtLeast(1),
        height = (bitmap.height * scale).roundToInt().coerceAtLeast(1)
    )
}

private fun scoreQuantizedColors(
    colorsToPopulation: Map<Int, Int>,
    scoring: ColorScoringConfig,
    fallbackColorArgb: Int
): List<Int> {
    if (colorsToPopulation.isEmpty()) return listOf(fallbackColorArgb)

    val colorsHct = ArrayList<Hct>(colorsToPopulation.size)
    val huePopulation = IntArray(360)
    var populationSum = 0.0

    for ((argb, population) in colorsToPopulation) {
        if (population <= 0) continue
        val hct = Hct.fromInt(argb)
        colorsHct.add(hct)
        val hue = MathUtils.sanitizeDegreesInt(floor(hct.hue).toInt())
        huePopulation[hue] += population
        populationSum += population.toDouble()
    }

    if (populationSum <= 0.0) return listOf(fallbackColorArgb)

    val hueExcitedProportions = DoubleArray(360)
    for (hue in 0 until 360) {
        val proportion = huePopulation[hue] / populationSum
        for (neighbor in hue - 14..hue + 15) {
            val wrappedHue = MathUtils.sanitizeDegreesInt(neighbor)
            hueExcitedProportions[wrappedHue] += proportion
        }
    }

    val scoredColors = ArrayList<ScoredHct>(colorsHct.size)
    for (hct in colorsHct) {
        val hue = MathUtils.sanitizeDegreesInt(hct.hue.roundToInt())
        val excitedProportion = hueExcitedProportions[hue]
        if (hct.chroma < scoring.cutoffChroma || excitedProportion <= scoring.cutoffExcitedProportion) {
            continue
        }

        val proportionScore = excitedProportion * 100.0 * scoring.weightProportion
        val chromaWeight =
            if (hct.chroma < scoring.targetChroma) scoring.weightChromaBelow else scoring.weightChromaAbove
        val chromaScore = (hct.chroma - scoring.targetChroma) * chromaWeight
        scoredColors.add(ScoredHct(hct, proportionScore + chromaScore))
    }

    if (scoredColors.isEmpty()) return listOf(fallbackColorArgb)
    scoredColors.sortByDescending { it.score }

    val maxHueDifference = scoring.maxHueDifference.coerceAtLeast(scoring.minHueDifference)
    val minHueDifference = scoring.minHueDifference.coerceAtLeast(1)
    val desiredColorCount = scoring.maxColorCount.coerceAtLeast(1)
    val chosen = mutableListOf<Hct>()

    for (differenceDegrees in maxHueDifference downTo minHueDifference) {
        chosen.clear()
        for (candidate in scoredColors) {
            val isDuplicateHue = chosen.any {
                MathUtils.differenceDegrees(candidate.hct.hue, it.hue) < differenceDegrees.toDouble()
            }
            if (!isDuplicateHue) {
                chosen.add(candidate.hct)
            }
            if (chosen.size >= desiredColorCount) break
        }
        if (chosen.size >= desiredColorCount) break
    }

    if (chosen.isEmpty()) return listOf(fallbackColorArgb)
    return chosen.map { it.toInt() }
}

private fun createDynamicScheme(
    sourceHct: Hct,
    paletteStyle: AlbumArtPaletteStyle,
    isDark: Boolean,
    forceNeutral: Boolean
): DynamicScheme {
    if (forceNeutral && paletteStyle != AlbumArtPaletteStyle.MONOCHROME) {
        return SchemeNeutral(sourceHct, isDark, 0.0)
    }

    return when (paletteStyle) {
        AlbumArtPaletteStyle.TONAL_SPOT -> SchemeTonalSpot(sourceHct, isDark, 0.0)
        AlbumArtPaletteStyle.VIBRANT -> SchemeVibrant(sourceHct, isDark, 0.0)
        AlbumArtPaletteStyle.EXPRESSIVE -> SchemeExpressive(sourceHct, isDark, 0.0)
        AlbumArtPaletteStyle.FRUIT_SALAD -> SchemeFruitSalad(sourceHct, isDark, 0.0)
        AlbumArtPaletteStyle.MONOCHROME -> SchemeMonochrome(sourceHct, isDark, 0.0)
    }
}

private fun averageColorArgb(pixels: IntArray): Int {
    if (pixels.isEmpty()) return DarkColorScheme.primary.toArgb()

    var totalRed = 0L
    var totalGreen = 0L
    var totalBlue = 0L

    for (argb in pixels) {
        totalRed += (argb ushr 16) and 0xFF
        totalGreen += (argb ushr 8) and 0xFF
        totalBlue += argb and 0xFF
    }

    val size = pixels.size.toLong()
    val r = (totalRed / size).toInt().coerceIn(0, 255)
    val g = (totalGreen / size).toInt().coerceIn(0, 255)
    val b = (totalBlue / size).toInt().coerceIn(0, 255)
    return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
}

private fun isMostlyNeutralArtwork(colorsToPopulation: Map<Int, Int>): Boolean {
    if (colorsToPopulation.isEmpty()) return false

    var totalPopulation = 0.0
    var neutralPopulation = 0.0
    var highChromaPopulation = 0.0
    var weightedChroma = 0.0

    for ((argb, populationInt) in colorsToPopulation) {
        if (populationInt <= 0) continue
        val population = populationInt.toDouble()
        val chroma = Hct.fromInt(argb).chroma

        totalPopulation += population
        weightedChroma += chroma * population

        if (chroma <= NEUTRAL_PIXEL_CHROMA_THRESHOLD) {
            neutralPopulation += population
        }
        if (chroma >= HIGH_CHROMA_THRESHOLD) {
            highChromaPopulation += population
        }
    }

    if (totalPopulation <= 0.0) return false

    val neutralRatio = neutralPopulation / totalPopulation
    val highChromaRatio = highChromaPopulation / totalPopulation
    val meanChroma = weightedChroma / totalPopulation

    return neutralRatio >= REQUIRED_NEUTRAL_POPULATION &&
        highChromaRatio <= MAX_HIGH_CHROMA_POPULATION &&
        meanChroma <= MAX_WEIGHTED_CHROMA_FOR_NEUTRAL
}

private fun DynamicScheme.toComposeColorScheme(): ColorScheme {
    return ColorScheme(
        primary = Color(getPrimary()),
        onPrimary = Color(getOnPrimary()),
        primaryContainer = Color(getPrimaryContainer()),
        onPrimaryContainer = Color(getOnPrimaryContainer()),
        inversePrimary = Color(getInversePrimary()),
        secondary = Color(getSecondary()),
        onSecondary = Color(getOnSecondary()),
        secondaryContainer = Color(getSecondaryContainer()),
        onSecondaryContainer = Color(getOnSecondaryContainer()),
        tertiary = Color(getTertiary()),
        onTertiary = Color(getOnTertiary()),
        tertiaryContainer = Color(getTertiaryContainer()),
        onTertiaryContainer = Color(getOnTertiaryContainer()),
        background = Color(getBackground()),
        onBackground = Color(getOnBackground()),
        surface = Color(getSurface()),
        onSurface = Color(getOnSurface()),
        surfaceVariant = Color(getSurfaceVariant()),
        onSurfaceVariant = Color(getOnSurfaceVariant()),
        surfaceTint = Color(getSurfaceTint()),
        inverseSurface = Color(getInverseSurface()),
        inverseOnSurface = Color(getInverseOnSurface()),
        error = Color(getError()),
        onError = Color(getOnError()),
        errorContainer = Color(getErrorContainer()),
        onErrorContainer = Color(getOnErrorContainer()),
        outline = Color(getOutline()),
        outlineVariant = Color(getOutlineVariant()),
        scrim = Color(getScrim()),
        surfaceBright = Color(getSurfaceBright()),
        surfaceDim = Color(getSurfaceDim()),
        surfaceContainer = Color(getSurfaceContainer()),
        surfaceContainerHigh = Color(getSurfaceContainerHigh()),
        surfaceContainerHighest = Color(getSurfaceContainerHighest()),
        surfaceContainerLow = Color(getSurfaceContainerLow()),
        surfaceContainerLowest = Color(getSurfaceContainerLowest()),
        primaryFixed = Color(getPrimaryFixed()),
        primaryFixedDim = Color(getPrimaryFixedDim()),
        onPrimaryFixed = Color(getOnPrimaryFixed()),
        onPrimaryFixedVariant = Color(getOnPrimaryFixedVariant()),
        secondaryFixed = Color(getSecondaryFixed()),
        secondaryFixedDim = Color(getSecondaryFixedDim()),
        onSecondaryFixed = Color(getOnSecondaryFixed()),
        onSecondaryFixedVariant = Color(getOnSecondaryFixedVariant()),
        tertiaryFixed = Color(getTertiaryFixed()),
        tertiaryFixedDim = Color(getTertiaryFixedDim()),
        onTertiaryFixed = Color(getOnTertiaryFixed()),
        onTertiaryFixedVariant = Color(getOnTertiaryFixedVariant())
    )
}
