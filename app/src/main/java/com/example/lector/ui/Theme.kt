package com.example.lector.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// TODO: pon aquí tu Inter si quieres, p. ej. FontFamily(Font(R.font.inter_regular), Font(R.font.inter_bold, FontWeight.Bold))
val LectorFont: FontFamily = FontFamily.Default

object LectorColors {
    val Bg = Color.Black
    val Card = Color(0xFF121212)
    val CardHigh = Color(0xFF1E1E20)
    val Glass = Color(0xE61A1A1C)
    val GlassBorder = Color(0x14FFFFFF)
    val Text = Color.White
    val Muted = Color(0xFF9A9AA0)
    val PillFill = Color(0x33FFFFFF)
}

object LectorType {
    val Display = TextStyle(fontFamily = LectorFont, fontWeight = FontWeight.Bold, fontSize = 34.sp, letterSpacing = (-0.5).sp)
    val Hero = TextStyle(fontFamily = LectorFont, fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 30.sp)
    val Section = TextStyle(fontFamily = LectorFont, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
    val Body = TextStyle(fontFamily = LectorFont, fontWeight = FontWeight.Medium, fontSize = 14.sp)
    val Label = TextStyle(fontFamily = LectorFont, fontWeight = FontWeight.Normal, fontSize = 12.sp)
    val Mono = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp)
}
