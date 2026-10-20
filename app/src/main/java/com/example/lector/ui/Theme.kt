package com.example.lector.ui

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Inter en todos los textos de la app (como musicfy).
val LectorFont: FontFamily = com.example.lector.Inter

/**
 * Tema actual (claro / oscuro / negro puro). Es estado de Compose: al cambiarlo, todo lo que lee [LectorColors]
 * se vuelve a pintar. Lo fija la actividad al arrancar y cada vez que cambia el ajuste o el modo del sistema.
 */
@Stable
object TemaActual {
    var oscuro by mutableStateOf(true)
    var puro by mutableStateOf(true)
}

/** Colores de la app. Son propiedades calculadas: siguen al tema sin tener que pasar nada por parámetros. */
object LectorColors {
    private val o get() = TemaActual.oscuro
    private val p get() = TemaActual.puro

    val Bg: Color get() = if (!o) Color(0xFFF6F6F8) else if (p) Color.Black else Color(0xFF111114)
    val Card: Color get() = if (!o) Color(0xFFFFFFFF) else if (p) Color(0xFF121212) else Color(0xFF1C1C21)
    val CardHigh: Color get() = if (!o) Color(0xFFE9E9EE) else if (p) Color(0xFF1E1E20) else Color(0xFF26262C)
    val Glass: Color get() = if (!o) Color(0xEBFFFFFF) else Color(0xE61A1A1C)
    val GlassBorder: Color get() = if (!o) Color(0x14000000) else Color(0x14FFFFFF)
    // Borde de cristal: más claro arriba y casi invisible abajo (como la luz sobre un vidrio).
    val GlassBorderBrush: Brush get() = if (!o) Brush.verticalGradient(listOf(Color(0x22000000), Color(0x0A000000)))
        else Brush.verticalGradient(listOf(Color(0x33FFFFFF), Color(0x08FFFFFF)))
    val Text: Color get() = if (!o) Color(0xFF111114) else Color.White
    val Muted: Color get() = if (!o) Color(0xFF6B6B73) else Color(0xFF9A9AA0)
    val PillFill: Color get() = if (!o) Color(0x16000000) else Color(0x33FFFFFF)
    /** Línea divisoria fina. */
    val Linea: Color get() = if (!o) Color(0x1A000000) else Color(0x22FFFFFF)
    /** Velo para resaltar una fila al pulsar. */
    val Resalte: Color get() = if (!o) Color(0x14000000) else Color(0x1FFFFFFF)
    /** Fondo de los botones redondos que van encima de imágenes (portada, cabeceras). */
    val SobreImagen: Color get() = if (!o) Color(0x99FFFFFF) else Color(0x66000000)
    val Error: Color = Color(0xFFE57373)
}

object LectorType {
    val Display = TextStyle(fontFamily = LectorFont, fontWeight = FontWeight.Bold, fontSize = 34.sp, letterSpacing = (-0.5).sp)
    val Hero = TextStyle(fontFamily = LectorFont, fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 30.sp, letterSpacing = (-0.8).sp)
    val Section = TextStyle(fontFamily = LectorFont, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, letterSpacing = (-0.3).sp)
    val Body = TextStyle(fontFamily = LectorFont, fontWeight = FontWeight.Medium, fontSize = 14.sp, letterSpacing = (-0.2).sp)
    val Label = TextStyle(fontFamily = LectorFont, fontWeight = FontWeight.Normal, fontSize = 12.sp)
    val Mono = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp)
}
