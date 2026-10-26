package com.example.lector.ui

import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.geometry.Offset
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.Canvas
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.lector.R
import kotlinx.coroutines.delay

/** Tamaño y sitio del icono fijo de la esquina (arriba a la izquierda, en Inicio). */
val LogoEsquina: Dp = 56.dp
val LogoEsquinaInicio: Dp = 14.dp
val LogoEsquinaArriba: Dp = 8.dp

/** Radio del horizonte de sucesos dentro del icono (en unidades del lienzo de 108 del vector). */
private const val HORIZONTE = 17.6f / 108f

/**
 * El icono de la app (la capa frontal del icono adaptable: el agujero negro). Con [conFondo] lleva debajo su disco
 * negro, para que el agujero se vea igual sobre el tema claro; sin él, el horizonte es transparente.
 */
@Composable
fun LogoApp(modifier: Modifier = Modifier, conFondo: Boolean = true) {
    Box(modifier) {
        if (conFondo) Canvas(Modifier.matchParentSize()) { drawCircle(Color.Black, radius = size.minDimension * 0.33f) }
        Image(painterResource(R.drawable.ic_launcher_foreground), contentDescription = "Shiori", modifier = Modifier.matchParentSize())
    }
}

/** Empieza despacio y acelera: la sensación de caer dentro. */
private val Caida = CubicBezierEasing(0.55f, 0f, 0.8f, 0.2f)

/**
 * Animación de arranque. El primer fotograma es idéntico a la pantalla de inicio del sistema (Android 12+: fondo negro
 * y el mismo icono, del mismo tamaño y en el mismo sitio), así que el paso de una a otra no se nota. Después el
 * agujero negro «inspira» un instante y se abre: el icono crece acelerando y por dentro del horizonte aparece la
 * app, como si se cayera dentro. El halo y el disco se apagan mientras pasan de largo.
 */
@Composable
fun ArranqueOverlay(alTerminar: () -> Unit) {
    val densidad = LocalDensity.current
    val inspirar = remember { Animatable(1f) }
    val caer = remember { Animatable(0f) }

    val desvanecer = remember { Animatable(0f) }
    BoxWithConstraints(Modifier.fillMaxSize().graphicsLayer {
        alpha = 1f - desvanecer.value
        val e = 1f + 0.12f * desvanecer.value
        scaleX = e; scaleY = e
    }) {
        val ancho = constraints.maxWidth.toFloat()
        val alto = constraints.maxHeight.toFloat()
        val lado = with(densidad) { 288.dp.toPx() }
        val radioHorizonte = lado * HORIZONTE
        // Escala con la que el horizonte cubre la pantalla entera (desde el centro hasta la esquina más lejana).
        val escalaFinal = (kotlin.math.hypot(ancho, alto) / 2f) / radioHorizonte * 1.04f

        LaunchedEffect(Unit) {
            // Con «Quitar animaciones» o «Ninguna»: directamente a la app.
            if (MovimientoReducido.activo || Aspecto.arranque == AnimArranque.NINGUNA) { ArranqueRevelado.valor = 1f; alTerminar(); return@LaunchedEffect }
            if (Aspecto.arranque == AnimArranque.RAPIDA) {
                // Rápida: el logo crece un poco y todo se funde en 280 ms.
                desvanecer.animateTo(1f, tween(280, easing = EnfasisEasing)) { ArranqueRevelado.valor = value }
                alTerminar(); return@LaunchedEffect
            }
            delay(40)
            inspirar.animateTo(0.95f, tween(200, easing = EnfasisEasing))
            caer.animateTo(1f, tween(560, easing = Caida)) { ArranqueRevelado.valor = value }
            alTerminar()
        }

        val escala: () -> Float = { inspirar.value * (1f + (escalaFinal - 1f) * caer.value) }

        // Negro con un agujero (transparente) del tamaño del horizonte: a través de él se ve la app.
        Canvas(
            Modifier.fillMaxSize()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .pointerInput(Unit) { detectTapGestures { } }, // mientras dura, nada de toques a la app de debajo
        ) {
            drawRect(Color.Black)
            if (caer.value > 0f) drawCircle(Color.Transparent, radius = radioHorizonte * escala(), center = Offset(ancho / 2f, alto / 2f), blendMode = BlendMode.Clear)
        }
        LogoApp(
            Modifier.size(288.dp).align(Alignment.Center).graphicsLayer {
                val e = escala()
                scaleX = e
                scaleY = e
                // La luz se apaga según pasa de largo (ya casi nada cuando el icono es 5 veces mayor).
                alpha = (1f - (e - 1.6f) / 3.4f).coerceIn(0f, 1f)
            },
            conFondo = false,
        )
    }
}

/**
 * Cuánto se ha abierto el agujero (0 → 1). La app de debajo lo usa para «emerger»: empieza un poco más grande y se
 * asienta mientras se revela, así el paso del agujero a la app tiene profundidad en vez de ser un corte.
 */
object ArranqueRevelado {
    var valor by androidx.compose.runtime.mutableFloatStateOf(0f)
    var repetir by androidx.compose.runtime.mutableIntStateOf(0)
}
