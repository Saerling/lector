package com.example.lector.ui

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.math.cos
import kotlin.math.sin

/** Cómo se ven las superficies flotantes (barra, mini lector, avisos, cabeceras). */
enum class Superficie(val nombre: String) { CRISTAL("Cristal"), LIQUIDO("Liquid glass"), SOLIDO("Sólido") }

/** Lo que hay detrás de las pestañas principales (y que el cristal desenfoca). */
enum class FondoApp(val nombre: String) { NEGRO("Liso"), AURORA("Aurora"), COLOR("Aurora de color") }

/** Animación al abrir la app. */
enum class AnimArranque(val nombre: String) { AGUJERO("Agujero negro"), RAPIDA("Rápida"), NINGUNA("Ninguna") }

/** Todas las opciones de aspecto en las que hemos trabajado, con sus valores guardados. */
object Aspecto {
    private var prefs: SharedPreferences? = null

    var superficie by mutableStateOf(Superficie.CRISTAL); private set
    var fondo by mutableStateOf(FondoApp.NEGRO); private set
    var arranque by mutableStateOf(AnimArranque.AGUJERO); private set
    var luzTactil by mutableStateOf(true); private set

    fun iniciar(ctx: Context) {
        val p = ctx.getSharedPreferences("aspecto", Context.MODE_PRIVATE).also { prefs = it }
        superficie = runCatching { Superficie.valueOf(p.getString("superficie", null)!!) }.getOrDefault(Superficie.CRISTAL)
        fondo = runCatching { FondoApp.valueOf(p.getString("fondo", null)!!) }.getOrDefault(FondoApp.NEGRO)
        arranque = runCatching { AnimArranque.valueOf(p.getString("arranque", null)!!) }.getOrDefault(AnimArranque.AGUJERO)
        luzTactil = p.getBoolean("luz", true)
    }

    fun elegir(s: Superficie) { superficie = s; prefs?.edit()?.putString("superficie", s.name)?.apply() }
    fun elegir(f: FondoApp) { fondo = f; prefs?.edit()?.putString("fondo", f.name)?.apply() }
    fun elegir(a: AnimArranque) { arranque = a; prefs?.edit()?.putString("arranque", a.name)?.apply() }
    fun elegirLuz(v: Boolean) { luzTactil = v; prefs?.edit()?.putBoolean("luz", v)?.apply() }
}

/**
 * Fondo vivo (pseudo liquid glass): manchas de luz muy suaves que derivan despacio detrás de las pestañas; el cristal
 * de la barra las desenfoca y refracta. Se mueve a 15 fps (no hace falta más para algo tan lento) y se queda quieto
 * con «Quitar animaciones».
 */
@Composable
fun FondoVivo(modo: FondoApp, modifier: Modifier = Modifier, fijo: Boolean = false, acento: Color = Color(0xFF8A6BFF)) {
    if (modo == FondoApp.NEGRO) return
    val t = remember { mutableFloatStateOf(0.3f) }
    LaunchedEffect(fijo) {
        if (fijo || MovimientoReducido.activo) return@LaunchedEffect
        // Va con el reloj de fotogramas: con la app en segundo plano o la pantalla apagada se detiene (no gasta batería).
        while (true) { androidx.compose.runtime.withFrameMillis { }; kotlinx.coroutines.delay(66); t.floatValue += 0.066f / 40f }
    }
    val oscuro = TemaActual.oscuro
    Canvas(modifier) { dibujarAurora(modo, t.floatValue, oscuro, acento) }
}

fun DrawScope.dibujarAurora(modo: FondoApp, t: Float, oscuro: Boolean, acento: Color) {
    val base = if (oscuro) Color.White else Color.Black
    val colores = when (modo) {
        FondoApp.COLOR -> listOf(acento, Color(0xFFFF7A45), Color(0xFF3D7BFF))
        else -> listOf(base, base, base)
    }
    val fuerza = when (modo) { FondoApp.COLOR -> if (oscuro) 0.30f else 0.22f; else -> if (oscuro) 0.09f else 0.06f }
    val w = size.width; val h = size.height
    val a = (t * 2f * Math.PI).toFloat()
    val centros = listOf(
        Offset(w * (0.25f + 0.15f * sin(a)), h * (0.22f + 0.08f * cos(a * 0.7f))),
        Offset(w * (0.80f + 0.12f * cos(a * 0.8f)), h * (0.48f + 0.10f * sin(a * 0.9f))),
        Offset(w * (0.35f + 0.18f * cos(a * 1.1f)), h * (0.85f + 0.06f * sin(a))),
    )
    centros.forEachIndexed { i, c ->
        val r = maxOf(w, h) * (0.55f - i * 0.07f)
        drawCircle(Brush.radialGradient(listOf(colores[i].copy(alpha = fuerza), Color.Transparent), c, r), r, c)
    }
}
