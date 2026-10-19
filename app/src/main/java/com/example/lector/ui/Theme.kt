package com.example.lector.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Inter en todos los textos de la app (como musicfy).
val LectorFont: FontFamily = com.example.lector.Inter

object LectorColors {
    val Bg = Color.Black
    val Card = Color(0xFF121212)
    val CardHigh = Color(0xFF1E1E20)
    val Glass = Color(0xE61A1A1C)
    val GlassBorder = Color(0x14FFFFFF)
    // Borde de cristal: más claro arriba y casi invisible abajo (como la luz sobre un vidrio).
    val GlassBorderBrush = androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color(0x33FFFFFF), Color(0x08FFFFFF)))
    val Text = Color.White
    val Muted = Color(0xFF9A9AA0)
    val PillFill = Color(0x33FFFFFF)
}

object LectorType {
    val Display = TextStyle(fontFamily = LectorFont, fontWeight = FontWeight.Bold, fontSize = 34.sp, letterSpacing = (-0.5).sp)
    val Hero = TextStyle(fontFamily = LectorFont, fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 30.sp, letterSpacing = (-0.8).sp)
    val Section = TextStyle(fontFamily = LectorFont, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, letterSpacing = (-0.3).sp)
    val Body = TextStyle(fontFamily = LectorFont, fontWeight = FontWeight.Medium, fontSize = 14.sp, letterSpacing = (-0.2).sp)
    val Label = TextStyle(fontFamily = LectorFont, fontWeight = FontWeight.Normal, fontSize = 12.sp)
    val Mono = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp)
}
