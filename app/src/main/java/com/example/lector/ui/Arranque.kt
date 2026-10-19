package com.example.lector.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
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
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Tamaño y sitio del icono fijo de la esquina (arriba a la izquierda, en Inicio). */
val LogoEsquina: Dp = 56.dp
val LogoEsquinaInicio: Dp = 14.dp
val LogoEsquinaArriba: Dp = 8.dp

/** El icono de la app (la capa frontal del icono adaptable). */
@Composable
fun LogoApp(modifier: Modifier = Modifier) {
    Image(painterResource(R.drawable.ic_launcher_foreground), contentDescription = "Shiori", modifier = modifier)
}

/**
 * Animación de arranque (como la de musicfy): pantalla negra con el icono grande en el centro; tras un instante el
 * icono se encoge y vuela a la esquina de arriba a la izquierda mientras la negrura se desvanece y aparece la app.
 */
@Composable
fun ArranqueOverlay(alTerminar: () -> Unit) {
    val densidad = LocalDensity.current
    val entrada = remember { Animatable(0f) }
    val mover = remember { Animatable(0f) }
    val fondo = remember { Animatable(1f) }
    val brillo = remember { Animatable(1f) }

    LaunchedEffect(Unit) {
        entrada.animateTo(1f, tween(280, easing = EnfasisEasing))
        delay(380)
        coroutineScope {
            launch { mover.animateTo(1f, tween(780, easing = EnfasisEasing)) }
            launch { delay(120); fondo.animateTo(0f, tween(620)) }
            launch { delay(680); brillo.animateTo(0f, tween(140)) }
        }
        alTerminar()
    }

    val arribaPx = WindowInsets.statusBars.getTop(densidad).toFloat()
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val ancho = constraints.maxWidth.toFloat()
        val alto = constraints.maxHeight.toFloat()
        val grande = with(densidad) { 288.dp.toPx() }
        val chico = with(densidad) { LogoEsquina.toPx() }
        val destinoX = with(densidad) { LogoEsquinaInicio.toPx() } + chico / 2f
        val destinoY = arribaPx + with(densidad) { LogoEsquinaArriba.toPx() } + chico / 2f

        // Mientras dura, el fondo negro se come los toques (la app de debajo aún no debe responder).
        Box(
            Modifier.fillMaxSize()
                .graphicsLayer { alpha = fondo.value }
                .background(Color.Black)
                .pointerInput(Unit) { detectTapGestures { } },
        )
        LogoApp(
            Modifier.size(288.dp).align(Alignment.Center).graphicsLayer {
                val m = mover.value
                val escala = (0.9f + 0.1f * entrada.value) * (1f + (chico / grande - 1f) * m)
                scaleX = escala
                scaleY = escala
                translationX = (destinoX - ancho / 2f) * m
                translationY = (destinoY - alto / 2f) * m
                alpha = entrada.value * brillo.value
            },
        )
    }
}
