package com.example.lector.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.lector.textoSobre
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

/** Una acción que sale al deslizar una fila: icono, texto, color y qué hacer al soltar. */
class AccionDeslizar(
    val icono: ImageVector,
    val etiqueta: String,
    val color: Color,
    val alDisparar: () -> Unit,
)

/**
 * Fila deslizable al estilo de la cola de musicfy:
 *  - Al arrastrar, la fila se convierte en una píldora de vidrio y por debajo asoma un círculo con el icono.
 *  - Al pasar el umbral el círculo se estira, se tiñe del color de la acción, enseña su texto y vibra.
 *  - Al soltar (pasado el umbral) se ejecuta la acción y la fila vuelve con un rebote.
 *
 * [inicio] sale al deslizar hacia la derecha; [fin], hacia la izquierda. Si es null, ese lado no se desliza.
 */
@Composable
fun FilaDeslizable(
    inicio: AccionDeslizar?,
    fin: AccionDeslizar?,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val umbral = with(density) { 110.dp.toPx() }
    val maximo = with(density) { 260.dp.toPx() }

    var dx by remember { mutableStateOf(0f) }
    val inicioAct by rememberUpdatedState(inicio)
    val finAct by rememberUpdatedState(fin)
    // Las acciones que se enseñan se fijan al empezar el gesto: así el texto no cambia mientras la fila vuelve.
    var verInicio by remember { mutableStateOf(inicio) }
    var verFin by remember { mutableStateOf(fin) }

    val armadoInicio by remember { derivedStateOf { dx >= umbral } }
    val armadoFin by remember { derivedStateOf { -dx >= umbral } }
    val armado = armadoInicio || armadoFin
    LaunchedEffect(armado) {
        if (armado) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    val estado = rememberDraggableState { d ->
        val resistencia = if (abs(dx) > umbral) 0.45f else 1f
        dx = (dx + d * resistencia).coerceIn(
            if (finAct == null) 0f else -maximo,
            if (inicioAct == null) 0f else maximo,
        )
    }

    Box(
        modifier.draggable(
            state = estado,
            orientation = Orientation.Horizontal,
            onDragStarted = { verInicio = inicioAct; verFin = finAct },
            onDragStopped = {
                val accion = when {
                    dx >= umbral -> verInicio
                    -dx >= umbral -> verFin
                    else -> null
                }
                accion?.let { it.alDisparar() }
                animate(dx, 0f, animationSpec = spring(dampingRatio = 0.72f, stiffness = 260f)) { v, _ -> dx = v }
            },
        )
    ) {
        verInicio?.let { PildoraDeslizar(it, armadoInicio, { dx.coerceAtLeast(0f) }, Modifier.align(Alignment.CenterStart)) }
        verFin?.let { PildoraDeslizar(it, armadoFin, { (-dx).coerceAtLeast(0f) }, Modifier.align(Alignment.CenterEnd)) }

        Box(
            Modifier
                .graphicsLayer { translationX = dx }
                .drawBehind {
                    // La fila se vuelve una píldora de vidrio mientras se arrastra.
                    val a = (abs(dx) / 40.dp.toPx()).coerceIn(0f, 1f)
                    if (a > 0f) {
                        val margen = 8.dp.toPx()
                        drawRoundRect(
                            Color.White.copy(alpha = 0.10f * a),
                            topLeft = Offset(margen, 0f),
                            size = Size(size.width - margen * 2, size.height),
                            cornerRadius = CornerRadius(size.height / 2f),
                        )
                    }
                }
        ) { content() }
    }
}

@Composable
private fun PildoraDeslizar(
    accion: AccionDeslizar,
    armada: Boolean,
    revelado: () -> Float,
    modifier: Modifier,
) {
    val expande = animateFloatAsState(
        if (armada) 1f else 0f, spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMedium), label = "expandePildora",
    )
    val fondo by animateColorAsState(if (armada) accion.color else Color(0x40FFFFFF), tween(180), label = "fondoPildora")
    val tinta by animateColorAsState(
        if (armada) textoSobre(accion.color) else Color(0xE6FFFFFF), tween(180), label = "tintaPildora",
    )
    val natural = 64.dp + (accion.etiqueta.length * 9).dp

    Box(
        modifier
            .padding(horizontal = 16.dp)
            .layout { medible, _ ->
                val disponible = (revelado() - 28.dp.toPx()).coerceAtLeast(0f)
                val circulo = min(56.dp.toPx(), disponible)
                val pleno = min(natural.toPx(), disponible)
                val ancho = (circulo + (pleno - circulo) * expande.value).roundToInt().coerceAtLeast(0)
                val alto = 56.dp.roundToPx()
                val p = medible.measure(Constraints.fixed(ancho, alto))
                layout(ancho, alto) { p.place(0, 0) }
            }
            .graphicsLayer { alpha = (revelado() / 24.dp.toPx()).coerceIn(0f, 1f) }
            .clip(CircleShape)
            .background(fondo)
    ) {
        Row(
            Modifier.wrapContentWidth(Alignment.Start, unbounded = true).height(56.dp).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(accion.icono, null, tint = tinta, modifier = Modifier.size(24.dp))
            LText(
                accion.etiqueta, LectorType.Section.copy(fontSize = 15.sp),
                Modifier.padding(start = 10.dp).graphicsLayer { alpha = expande.value },
                color = tinta, maxLines = 1,
            )
        }
    }
}
