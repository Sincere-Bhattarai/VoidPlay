package com.voidcorp.voidplay.presentation.screens.search.components

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import com.voidcorp.voidplay.R
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.FontVariation
import kotlin.math.abs

// FALTA: genre_variable.ttf (Variable Font)
// Por favor, coloca el archivo de fuente variable (ej. RobotoFlex-VariableFont_GRAD,XTRA,YOPQ,YTAS,YTDE,YTFI,YTLC,YTUC,opsz,slnt,wdth,wght.ttf)
// en la carpeta: app/src/main/res/font/
// Y renómbralo a: genre_variable.ttf

object GenreTypography {

    /**
     * Generates a deterministic random TextStyle for a given Genre ID using variable font settings.
     * Note: This requires a variable font resource to be present in res/font/genre_variable.ttf
     */
@OptIn(ExperimentalTextApi::class)
    fun getGenreStyle(genreId: String, genreName: String): TextStyle {
        val hash = abs(genreId.hashCode())
        val seed = hash % 100
        
        // Analyze Text Length for smart adjustments
        val length = genreName.length
        val wordCount = genreName.split(" ").size
        val isLongText = length > 10 || wordCount > 2
        val isVeryLongText = length > 16

        // Strategy Distribution:
        // 0-14: Original (Simple Bold) -> 15%
        // 15-24: Monospace -> 10%
        // 25-64: Roboto Flex (Wild Variable) -> 40%
        // 65-99: Google Sans Flex (Rounded/Expressive) -> 35%
        
        return when {
            seed < 15 -> { // Original
                 TextStyle(
                    fontFamily = FontFamily.Default, 
                    fontWeight = FontWeight.Bold,
                    fontSize = if (isVeryLongText) 16.sp else if (isLongText) 18.sp else 20.sp
                )
            }
            seed < 25 -> { // Monospace
                TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = if (isVeryLongText) 15.sp else if (isLongText) 17.sp else 19.sp,
                    letterSpacing = (-0.5).sp // Tighter look for mono
                )
            }
            seed < 65 -> { // Roboto Flex (Wild)
                // Distinctive "Wild" look using multiple axes
                
                // Weight: 300 to 900
                val weightVal = 300 + (hash % 600) 
                
                // Width: 70 to 120
                // Smart Adjustment: Condense width for long text
                val baseWidth = 70f + (hash % 50) 
                val widthVal = if (isVeryLongText) baseWidth * 0.85f else if (isLongText) baseWidth * 0.9f else baseWidth
                
                // Slant: Mix of Upright (0), Slight Slant (-5), and Full Slant (-10)
                val slantBucket = hash % 3
                val slantVal = when (slantBucket) {
                    0 -> 0f
                    1 -> -5f
                    else -> -10f
                }
                
                // New Axes for texture and personality:
                val gradeVal = -200 + (hash % 350)
                val xtraVal = 400f + (hash % 200)
                val yopqVal = 40f + (hash % 60)
                val ytlcVal = 450f + (hash % 100)

                val family = FontFamily(
                    Font(
                        resId = R.font.genre_variable,
                        variationSettings = FontVariation.Settings(
                            FontVariation.weight(weightVal),
                            FontVariation.width(widthVal),
                            FontVariation.slant(slantVal),
                            FontVariation.grade(gradeVal),
                            FontVariation.Setting("XTRA", xtraVal),
                            FontVariation.Setting("YOPQ", yopqVal),
                            FontVariation.Setting("YTLC", ytlcVal)
                        )
                    )
                )

                // Geometric Transform: "Crazy" scaleX
                // Range: 0.9 to 1.3
                // Smart Adjustment: Tame scaleX for long text to avoid horizontal overflow
                val baseScaleX = 0.9f + ((hash % 5) / 10f)
                val scaleXVal = if (isVeryLongText) 0.95f else if (isLongText) 1.0f else baseScaleX

                val baseFontSize = (22 + (hash % 10))
                val finalFontSize = if (isVeryLongText) (baseFontSize * 0.75f) else if (isLongText) (baseFontSize * 0.85f) else baseFontSize.toFloat()

                TextStyle(
                    fontFamily = family,
                    fontWeight = FontWeight(weightVal), 
                    fontSize = finalFontSize.sp,
                    letterSpacing = if (widthVal > 100) (-0.5).sp else 0.sp, 
                    textGeometricTransform = androidx.compose.ui.text.style.TextGeometricTransform(scaleX = scaleXVal)
                )
            }
            else -> { // Google Sans Flex (Rounded/Expressive)
                 val weightVal = 300 + (hash % 500) 
                 val family = FontFamily(
                    Font(
                        resId = R.font.gflex_variable,
                        variationSettings = FontVariation.Settings(
                            FontVariation.weight(weightVal)
                        )
                    )
                 )
                 
                 val baseScaleX = 0.9f + ((hash % 5) / 10f) 
                 val scaleXVal = if (isVeryLongText) 0.95f else if (isLongText) 1.0f else baseScaleX
                 
                 val baseFontSize = (20 + (hash % 8))
                 val finalFontSize = if (isVeryLongText) (baseFontSize * 0.8f) else if (isLongText) (baseFontSize * 0.9f) else baseFontSize.toFloat()

                 TextStyle(
                    fontFamily = family,
                    fontWeight = FontWeight(weightVal),
                    fontSize = finalFontSize.sp,
                    textGeometricTransform = androidx.compose.ui.text.style.TextGeometricTransform(scaleX = scaleXVal)
                )
            }
        }
    }
}
