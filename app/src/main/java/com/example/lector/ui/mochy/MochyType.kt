// Copiado de Mochy Music (github.com/realidkroo/Mochy-Music, GPL-3.0): app/src/main/kotlin/com/mochyy/mochymusic/ui/theme/Type.kt
// Type.kt

package com.example.lector.ui.mochy

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

// Shiori ya trae Inter: la misma familia que Mochy, cargada en com.example.lector.ui.LectorFont
val InterFontFamily: FontFamily = com.example.lector.ui.LectorFont

private val GlobalFontWeight = FontWeight.SemiBold
private val GlobalLetterSpacing = (-0.05).em

val AppTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = GlobalFontWeight,
        fontSize = 57.sp,
        lineHeight = 64.sp,
        letterSpacing = GlobalLetterSpacing
    ),
    displayMedium = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = GlobalFontWeight,
        fontSize = 45.sp,
        lineHeight = 52.sp,
        letterSpacing = GlobalLetterSpacing
    ),
    displaySmall = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = GlobalFontWeight,
        fontSize = 36.sp,
        lineHeight = 44.sp,
        letterSpacing = GlobalLetterSpacing
    ),
    headlineLarge = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = GlobalFontWeight,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = GlobalLetterSpacing
    ),
    headlineMedium = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = GlobalFontWeight,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = GlobalLetterSpacing
    ),
    headlineSmall = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = GlobalFontWeight,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = GlobalLetterSpacing
    ),
    titleLarge = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = GlobalFontWeight,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = GlobalLetterSpacing
    ),
    titleMedium = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = GlobalFontWeight,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = GlobalLetterSpacing
    ),
    titleSmall = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = GlobalFontWeight,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = GlobalLetterSpacing
    ),
    bodyLarge = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = GlobalFontWeight,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = GlobalLetterSpacing
    ),
    bodyMedium = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = GlobalFontWeight,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = GlobalLetterSpacing
    ),
    bodySmall = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = GlobalFontWeight,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = GlobalLetterSpacing
    ),
    labelLarge = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = GlobalFontWeight,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = GlobalLetterSpacing
    ),
    labelMedium = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = GlobalFontWeight,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = GlobalLetterSpacing
    ),
    labelSmall = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = GlobalFontWeight,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = GlobalLetterSpacing
    )
)
