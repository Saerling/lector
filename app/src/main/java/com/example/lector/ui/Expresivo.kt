package com.example.lector.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Base de diseño nueva: movimiento de Mochy + componentes de Material 3 Expressive (formas que cambian al pulsar,
 * grupos de botones conectados, indicador de carga que se transforma, slider grueso con asa).
 * Los muelles son los del «MotionScheme.expressive» de Material: espacial (posición, tamaño, forma) con algo de
 * rebote y efectos (color, opacidad) sin rebote.
 */
object Muelles {
    fun <T> espacialRapido(): SpringSpec<T> = spring(0.6f, 800f)
    fun <T> espacial(): SpringSpec<T> = spring(0.8f, 380f)
    fun <T> espacialLento(): SpringSpec<T> = spring(0.8f, 200f)
    fun <T> efectos(): SpringSpec<T> = spring(1f, 1600f)
    fun <T> efectosLento(): SpringSpec<T> = spring(1f, 800f)
}

enum class EstiloBoton { RELLENO, TONAL, CONTORNO, TEXTO }

/**
 * Botón Expressive: redondo del todo en reposo y, al pulsarlo, sus esquinas se cuadran (de 50 % a ~30 %) con un
 * muelle rápido, como los botones de Android 16. El relleno usa el acento; el tonal, el gris de la app.
 */
@Composable
fun BotonExpresivo(
    texto: String?,
    icono: ImageVector?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    estilo: EstiloBoton = EstiloBoton.TONAL,
    alto: Dp = 48.dp,
    habilitado: Boolean = true,
) {
    val fuente = remember { MutableInteractionSource() }
    val pulsado by fuente.collectIsPressedAsState()
    val radio by animateFloatAsState(if (pulsado) 0.3f else 0.5f, Muelles.espacialRapido(), label = "forma")
    val acento = MaterialTheme.colorScheme.primary
    val (fondo, tinta) = when (estilo) {
        EstiloBoton.RELLENO -> acento to MaterialTheme.colorScheme.onPrimary
        EstiloBoton.TONAL -> LectorColors.PillFill to LectorColors.Text
        EstiloBoton.CONTORNO, EstiloBoton.TEXTO -> Color.Transparent to (if (estilo == EstiloBoton.TEXTO) acento else LectorColors.Text)
    }
    val forma = RoundedCornerShape(percent = (radio * 100).roundToInt())
    Row(
        modifier.heightIn(min = alto).graphicsLayer { alpha = if (habilitado) 1f else 0.4f }
            .clip(forma).background(fondo)
            .then(if (estilo == EstiloBoton.CONTORNO) Modifier.border(1.dp, LectorColors.Linea, forma) else Modifier)
            .clickable(fuente, null, enabled = habilitado) { Tacto.tic(); onClick() }
            .padding(horizontal = if (texto == null) 12.dp else 22.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icono != null) Icon(icono, null, tint = tinta, modifier = Modifier.size(20.dp))
        if (icono != null && texto != null) Spacer(Modifier.width(8.dp))
        if (texto != null) LText(texto, LectorType.Body.copy(fontSize = 15.sp), color = tinta, maxLines = 1)
    }
}

/**
 * Grupo de botones conectados (ButtonGroup de Expressive): segmentos separados por 4 dp, con las esquinas de
 * dentro pequeñas; el elegido se vuelve una píldora completa y crece un poco, con muelle.
 */
@Composable
fun GrupoBotones(opciones: List<String>, elegida: Int, onElegir: (Int) -> Unit, modifier: Modifier = Modifier) {
    val acento = MaterialTheme.colorScheme.primary
    Row(modifier.fillMaxWidth().height(44.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        opciones.forEachIndexed { i, t ->
            val sel = i == elegida
            val fuente = remember { MutableInteractionSource() }
            val pulsado by fuente.collectIsPressedAsState()
            val dentro by animateDpAsState(if (sel || pulsado) 22.dp else 8.dp, Muelles.espacialRapido(), label = "dentro")
            val peso by animateFloatAsState(if (sel) 1.15f else if (pulsado) 1.05f else 1f, Muelles.espacial(), label = "peso")
            val fondo by animateColorAsState(if (sel) acento else LectorColors.PillFill, Muelles.efectos(), label = "fondo")
            val tinta by animateColorAsState(if (sel) MaterialTheme.colorScheme.onPrimary else LectorColors.Text, Muelles.efectos(), label = "tinta")
            val ext = 22.dp
            val forma = RoundedCornerShape(
                topStart = if (i == 0) ext else dentro, bottomStart = if (i == 0) ext else dentro,
                topEnd = if (i == opciones.lastIndex) ext else dentro, bottomEnd = if (i == opciones.lastIndex) ext else dentro,
            )
            Box(
                Modifier.weight(peso).fillMaxHeight().clip(forma).background(fondo)
                    .clickable(fuente, null) { Tacto.tic(); onElegir(i) },
                Alignment.Center,
            ) { LText(t, LectorType.Body, color = tinta, maxLines = 1) }
        }
    }
}

/**
 * Indicador de carga Expressive: una forma de pétalos que gira y va cambiando de figura (de 4 a 9 lóbulos y
 * de redonda a estrellada). Sustituye al círculo giratorio; respeta «Quitar animaciones».
 */
@Composable
fun IndicadorCarga(modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary, @Suppress("UNUSED_PARAMETER") strokeWidth: Dp = 0.dp) {
    val t = rememberInfiniteTransition(label = "carga")
    val giro by t.animateFloat(0f, 360f, infiniteRepeatable(tween(2600, easing = LinearEasing)), label = "giro")
    val fase by t.animateFloat(0f, 6f, infiniteRepeatable(tween(6000, easing = LinearEasing)), label = "fase")
    val quieto = Mov.reducido
    Canvas(modifier.size(36.dp)) {
        val figuras = intArrayOf(4, 9, 5, 7, 3, 6)
        val i = fase.toInt() % figuras.size
        val f = fase - fase.toInt()
        // Paso suave de una figura a la siguiente en la segunda mitad de cada tramo.
        val m = ((f - 0.5f) / 0.5f).coerceIn(0f, 1f).let { it * it * (3 - 2 * it) }
        val n1 = figuras[i]; val n2 = figuras[(i + 1) % figuras.size]
        val r = size.minDimension / 2f
        val c = Offset(size.width / 2, size.height / 2)
        val p = Path()
        val pasos = 96
        for (k in 0..pasos) {
            val a = (k.toFloat() / pasos) * 2f * PI.toFloat()
            val g = if (quieto) 0f else giro * PI.toFloat() / 180f
            val l1 = 0.82f + 0.18f * cos(n1 * a)
            val l2 = 0.82f + 0.18f * cos(n2 * a)
            val rr = r * (l1 + (l2 - l1) * m)
            val x = c.x + rr * cos(a + g); val y = c.y + rr * sin(a + g)
            if (k == 0) p.moveTo(x, y) else p.lineTo(x, y)
        }
        p.close()
        drawPath(p, color)
    }
}

/**
 * Slider Expressive: pista gruesa de 16 dp, parte activa con el acento, y un asa vertical (4 × 40 dp) separada de la
 * pista por un hueco; al arrastrar, el asa se estrecha. [pasos] = 0 para continuo.
 */
@Composable
fun DeslizadorExpresivo(
    valor: Float,
    rango: ClosedFloatingPointRange<Float>,
    onCambio: (Float) -> Unit,
    modifier: Modifier = Modifier,
    pasos: Int = 0,
    alTerminar: (() -> Unit)? = null,
    colorActivo: Color = MaterialTheme.colorScheme.primary,
    colorPista: Color = LectorColors.PillFill,
) {
    var ancho by remember { mutableIntStateOf(1) }
    var arrastrando by remember { mutableStateOf(false) }
    val asa by animateDpAsState(if (arrastrando) 2.dp else 4.dp, Muelles.espacialRapido(), label = "asa")
    val span = (rango.endInclusive - rango.start).takeIf { it > 0f } ?: 1f
    fun aValor(x: Float): Float {
        var v = rango.start + (x / ancho).coerceIn(0f, 1f) * span
        if (pasos > 0) { val paso = span / (pasos + 1); v = rango.start + ((v - rango.start) / paso).roundToInt() * paso }
        return v.coerceIn(rango)
    }
    val frac = ((valor - rango.start) / span).coerceIn(0f, 1f)
    Box(
        modifier.fillMaxWidth().height(44.dp).onSizeChanged { ancho = it.width.coerceAtLeast(1) }
            .pointerInput(rango, pasos) { detectTapGestures { onCambio(aValor(it.x)); alTerminar?.invoke() } }
            .pointerInput(rango, pasos) {
                detectHorizontalDragGestures(
                    onDragStart = { arrastrando = true; onCambio(aValor(it.x)) },
                    onDragEnd = { arrastrando = false; alTerminar?.invoke() },
                    onDragCancel = { arrastrando = false; alTerminar?.invoke() },
                ) { ch, _ -> onCambio(aValor(ch.position.x)) }
            },
        Alignment.CenterStart,
    ) {
        Canvas(Modifier.fillMaxWidth().height(44.dp)) {
            val h = 16.dp.toPx(); val y = (size.height - h) / 2f
            val x = frac * size.width
            val hueco = 6.dp.toPx()
            val r = androidx.compose.ui.geometry.CornerRadius(h / 2f)
            val ri = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx())
            // Parte activa (esquina interior pequeña) y resto de la pista, con el hueco del asa entre medias.
            if (x - hueco > 0f) drawRoundRect(colorActivo, Offset(0f, y), androidx.compose.ui.geometry.Size((x - hueco).coerceAtLeast(h / 2f), h), r)
            if (x + hueco < size.width) drawRoundRect(colorPista, Offset(x + hueco, y), androidx.compose.ui.geometry.Size(size.width - x - hueco, h), r)
            if (x - hueco > h / 2f) drawRoundRect(colorActivo, Offset(x - hueco - h / 2f, y), androidx.compose.ui.geometry.Size(h / 2f, h), ri)
            val aw = asa.toPx()
            drawRoundRect(colorActivo, Offset(x - aw / 2f, (size.height - 40.dp.toPx()) / 2f), androidx.compose.ui.geometry.Size(aw, 40.dp.toPx()), androidx.compose.ui.geometry.CornerRadius(aw / 2f))
        }
    }
}
