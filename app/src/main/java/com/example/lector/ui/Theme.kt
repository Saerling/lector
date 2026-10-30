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
import androidx.compose.ui.unit.em

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

/**
 * Colores de la app. Son propiedades calculadas: siguen al tema sin tener que pasar nada por parámetros.
 * En oscuro son los grises de musicfy: negro de fondo, campos #141416, hojas #0B0B0C, botones #2C2C2E.
 */
object LectorColors {
    private val o get() = TemaActual.oscuro
    private val p get() = TemaActual.puro

    val Bg: Color get() = if (!o) Color(0xFFF6F6F8) else if (p) Color.Black else Color(0xFF0E0E10)
    /** Campos, filas de ajustes y tarjetas (el «Field» de musicfy). */
    val Card: Color get() = if (!o) Color(0xFFFFFFFF) else Color(0xFF141416)
    /** Lo mismo pero un escalón más claro (campo con foco, mosaicos). */
    val CardHigh: Color get() = if (!o) Color(0xFFE9E9EE) else Color(0xFF1E1E21)
    /** Fondo de las hojas inferiores y de los menús. */
    val Hoja: Color get() = if (!o) Color(0xFFF2F2F5) else Color(0xFF0B0B0C)
    /** Filas sobre una hoja: un escalón más claras que la hoja. */
    val HojaFila: Color get() = if (!o) Color(0xFFFFFFFF) else Color(0xFF161619)
    /** Tinte del cristal de la barra y el mini lector (#131313 al 78 % sobre el fondo desenfocado). */
    val Cristal: Color get() = if (!o) Color(0xFFF4F4F6) else Color(0xFF131313)
    val Glass: Color get() = if (!o) Color(0xEBFFFFFF) else Color(0xE6131313)
    val GlassBorder: Color get() = if (!o) Color(0x14000000) else Color(0x0DFFFFFF)
    // Borde de cristal: una línea finísima (blanco al 5 % en oscuro), como el de musicfy.
    val GlassBorderBrush: Brush get() = if (!o) Brush.verticalGradient(listOf(Color(0x1F000000), Color(0x0F000000)))
        else Brush.verticalGradient(listOf(Color(0x14FFFFFF), Color(0x0DFFFFFF)))
    val Text: Color get() = if (!o) Color(0xFF111114) else Color.White
    val Muted: Color get() = if (!o) Color(0xFF6B6B73) else Color(0xFF9A9AA0)
    /** Texto de pista dentro de un campo vacío. */
    val Pista: Color get() = if (!o) Color(0xFF8A8A92) else Color(0xFF77777D)
    /** Botones redondos y píldoras (el gris #2C2C2E de musicfy). */
    val PillFill: Color get() = if (!o) Color(0x16000000) else Color(0xFF2C2C2E)
    /** Línea divisoria fina. */
    val Linea: Color get() = if (!o) Color(0x1A000000) else Color(0xFF232326)
    /** Velo para resaltar una fila al pulsar (blanco al 12 %). */
    val Resalte: Color get() = if (!o) Color(0x14000000) else Color(0x1FFFFFFF)
    /** Píldora iluminada de la pestaña activa (blanco al 10 %). */
    val Iluminada: Color get() = if (!o) Color(0x14000000) else Color(0x1AFFFFFF)
    /** Fondo de los botones redondos que van encima de imágenes (portada, cabeceras). */
    val SobreImagen: Color get() = if (!o) Color(0x99FFFFFF) else Color(0x66000000)
    /** Círculo del icono de una fila de ajustes (claro, con el glifo oscuro). */
    val IconoFila: Color get() = if (!o) Color(0xFF1C1C1E) else Color(0xFFE5E5EA)
    val IconoFilaGlifo: Color get() = if (!o) Color.White else Color(0xFF1C1C1E)
    val Aviso: Color get() = if (!o) Color(0xFFFFFFFF) else Color(0xFF1E1E1E)
    val Error: Color = Color(0xFFE57373)
}

/** Tipografía: Inter SemiBold con el interletrado cerrado de musicfy; los títulos grandes en negrita. */
object LectorType {
    private val cerrado = (-0.05).em
    val Display = TextStyle(fontFamily = LectorFont, fontWeight = FontWeight.ExtraBold, fontSize = 36.sp, lineHeight = 40.sp, letterSpacing = (-0.04).em)
    val Hero = TextStyle(fontFamily = LectorFont, fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 30.sp, letterSpacing = (-0.03).em)
    val Section = TextStyle(fontFamily = LectorFont, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp, letterSpacing = cerrado)
    val Body = TextStyle(fontFamily = LectorFont, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = cerrado)
    val Label = TextStyle(fontFamily = LectorFont, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = cerrado)
    val Mono = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp)
}
