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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.layout.fillMaxSize
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
 * Botón con el aspecto de los controles de Mochy (ui/component/ButtonStyles.kt): píldora redonda del todo; el
 * relleno usa el acento (como la píldora de reproducir) y el tonal el blanco al 15 % de sus botones redondos. Al
 * pulsarlo se encoge con el muelle de esa píldora (amortiguación 0,6, rigidez 700) y vuelve con un rebote suave.
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
    val escala by animateFloatAsState(if (pulsado) 0.92f else 1f, spring(dampingRatio = 0.6f, stiffness = 700f), label = "boton")
    val acento = MaterialTheme.colorScheme.primary
    val contenedor = if (TemaActual.oscuro) Color.White.copy(alpha = 0.15f) else Color.Black.copy(alpha = 0.07f)
    val (fondo, tinta) = when (estilo) {
        EstiloBoton.RELLENO -> acento to MaterialTheme.colorScheme.onPrimary
        EstiloBoton.TONAL -> contenedor to LectorColors.Text
        EstiloBoton.CONTORNO, EstiloBoton.TEXTO -> Color.Transparent to (if (estilo == EstiloBoton.TEXTO) acento else LectorColors.Text)
    }
    val forma = RoundedCornerShape(50)
    Row(
        modifier.heightIn(min = alto)
            .graphicsLayer { alpha = if (habilitado) 1f else 0.38f; scaleX = escala; scaleY = escala }
            .clip(forma).background(fondo)
            .then(if (estilo == EstiloBoton.CONTORNO) Modifier.border(1.dp, LectorColors.Linea, forma) else Modifier)
            .clickable(fuente, null, enabled = habilitado) { Tacto.tic(); onClick() }
            .padding(horizontal = if (texto == null) 13.dp else 22.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icono != null) Icon(icono, null, tint = tinta, modifier = Modifier.size(22.dp))
        if (icono != null && texto != null) Spacer(Modifier.width(8.dp))
        if (texto != null) LText(
            texto, MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
            color = tinta, maxLines = 1,
        )
    }
}

/**
 * Control segmentado (como el de iOS, con los grises de las hojas de Mochy): una pista redonda con una píldora clara
 * que se desliza con un muelle hasta la opción elegida. La posición se lee al dibujar (no recompone al moverse).
 */
@Composable
fun GrupoBotones(opciones: List<String>, elegida: Int, onElegir: (Int) -> Unit, modifier: Modifier = Modifier) {
    if (opciones.isEmpty()) return
    val pos = remember { Animatable(elegida.toFloat()) }
    androidx.compose.runtime.LaunchedEffect(elegida) {
        if (Mov.reducido) pos.snapTo(elegida.toFloat())
        else pos.animateTo(elegida.toFloat(), spring(dampingRatio = 0.82f, stiffness = 520f))
    }
    val pista = com.example.lector.ui.mochy.sheet.MenuRowSurface
    val pildora = if (TemaActual.oscuro) Color.White.copy(alpha = 0.16f) else Color.White
    val n = opciones.size
    Box(
        modifier.fillMaxWidth().height(40.dp).clip(RoundedCornerShape(50)).background(pista)
            .drawBehind {
                val m = 3.dp.toPx()
                val w = (size.width - m * 2) / n
                val x = m + w * pos.value.coerceIn(0f, (n - 1).toFloat())
                drawRoundRect(
                    pildora, Offset(x, m), androidx.compose.ui.geometry.Size(w, size.height - m * 2),
                    androidx.compose.ui.geometry.CornerRadius((size.height - m * 2) / 2f),
                )
            }
            .padding(3.dp),
    ) {
        Row(Modifier.fillMaxSize()) {
            opciones.forEachIndexed { i, t ->
                val sel = i == elegida
                val tinta by animateColorAsState(if (sel) LectorColors.Text else LectorColors.Muted, Muelles.efectos(), label = "tinta")
                Box(
                    Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(50))
                        .clickable(remember { MutableInteractionSource() }, null) { if (!sel) { Tacto.tic(); onElegir(i) } },
                    Alignment.Center,
                ) {
                    LText(
                        t, MaterialTheme.typography.labelLarge.copy(
                            fontSize = 14.sp,
                            fontWeight = if (sel) androidx.compose.ui.text.font.FontWeight.SemiBold else androidx.compose.ui.text.font.FontWeight.Medium,
                        ),
                        color = tinta, maxLines = 1,
                    )
                }
            }
        }
    }
}

/**
 * Indicador de carga al estilo de iOS: doce radios redondeados que se van apagando en círculo (giro a saltos de
 * 1/12 de vuelta cada ~83 ms). Con «Quitar animaciones» se queda quieto.
 */
@Composable
fun IndicadorCarga(
    modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary, @Suppress("UNUSED_PARAMETER") strokeWidth: Dp = 0.dp,
    // Qué está haciendo la app: elige la animación del thinking orb (Personalización → Estilo).
    estado: com.example.lector.ui.orbes.EstadoOrbe = com.example.lector.ui.orbes.EstadoOrbe.BREATHING,
) {
    if (com.example.lector.ui.mochy.EstiloApp.orbes) {
        com.example.lector.ui.orbes.OrbePensante(
            estado, modifier.size(28.dp),
            // Sobre una capa oscura (color blanco) o con el tema oscuro, la tinta se invierte: los puntos cercanos brillan.
            oscuro = color == Color.White || TemaActual.oscuro,
            tinte = if (com.example.lector.ui.mochy.EstiloApp.orbesAcento) color else null,
            quieto = Mov.reducido,
        )
        return
    }
    val t = rememberInfiniteTransition(label = "carga")
    val giro by t.animateFloat(0f, 12f, infiniteRepeatable(tween(1000, easing = LinearEasing)), label = "giro")
    val quieto = Mov.reducido
    Canvas(modifier.size(28.dp)) {
        val paso = if (quieto) 0 else giro.toInt() % 12
        val r = size.minDimension / 2f
        val c = Offset(size.width / 2, size.height / 2)
        val grosor = r * 0.2f
        for (k in 0 until 12) {
            // El radio más reciente es el más opaco; los de detrás se apagan hasta el 20 %.
            val edad = (paso - k + 12) % 12
            val a = (1f - edad / 12f).coerceAtLeast(0.2f)
            val ang = (k / 12f) * 2f * PI.toFloat() - PI.toFloat() / 2f
            val dx = cos(ang); val dy = sin(ang)
            drawLine(
                color.copy(alpha = color.alpha * a),
                Offset(c.x + dx * r * 0.45f, c.y + dy * r * 0.45f), Offset(c.x + dx * (r - grosor / 2), c.y + dy * (r - grosor / 2)),
                strokeWidth = grosor, cap = androidx.compose.ui.graphics.StrokeCap.Round,
            )
        }
    }
}

/**
 * Deslizador: el LineSlider de Mochy (ui/player/menu/LineSlider.kt, pista de 6 dp redonda del todo, toque en 28 dp
 * de alto) con el gesto de iOS: mientras se arrastra la pista engorda a 10 dp con un muelle. [pasos] = 0 para continuo.
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
    // Los gestos viven más que cada recomposición: siempre llaman a la versión actual de los callbacks.
    val cambio by androidx.compose.runtime.rememberUpdatedState(onCambio)
    val fin by androidx.compose.runtime.rememberUpdatedState(alTerminar)
    val grosor by animateDpAsState(if (arrastrando) 10.dp else 6.dp, spring(dampingRatio = 0.7f, stiffness = 600f), label = "pista")
    val span = (rango.endInclusive - rango.start).takeIf { it > 0f } ?: 1f
    fun aValor(x: Float): Float {
        var v = rango.start + (x / ancho).coerceIn(0f, 1f) * span
        if (pasos > 0) { val paso = span / (pasos + 1); v = rango.start + ((v - rango.start) / paso).roundToInt() * paso }
        return v.coerceIn(rango)
    }
    val frac = ((valor - rango.start) / span).coerceIn(0f, 1f)
    Box(
        modifier.fillMaxWidth().height(36.dp).onSizeChanged { ancho = it.width.coerceAtLeast(1) }
            .pointerInput(rango, pasos) { detectTapGestures { cambio(aValor(it.x)); fin?.invoke() } }
            .pointerInput(rango, pasos) {
                detectHorizontalDragGestures(
                    onDragStart = { arrastrando = true; cambio(aValor(it.x)) },
                    onDragEnd = { arrastrando = false; fin?.invoke() },
                    onDragCancel = { arrastrando = false; fin?.invoke() },
                ) { ch, _ -> cambio(aValor(ch.position.x)) }
            },
        Alignment.CenterStart,
    ) {
        Canvas(Modifier.fillMaxWidth().height(36.dp)) {
            val h = grosor.toPx(); val y = (size.height - h) / 2f
            val r = androidx.compose.ui.geometry.CornerRadius(h / 2f)
            drawRoundRect(colorPista, Offset(0f, y), androidx.compose.ui.geometry.Size(size.width, h), r)
            val w = frac * size.width
            if (w > 0f) drawRoundRect(colorActivo, Offset(0f, y), androidx.compose.ui.geometry.Size(w.coerceAtLeast(h), h), r)
            // Marcas de los pasos (si los hay y caben), como las de iOS.
            if (pasos in 1..20) {
                for (k in 1..pasos) {
                    val x = size.width * k / (pasos + 1f)
                    drawCircle((if (x <= w) Color.Black else LectorColors.Muted).copy(alpha = 0.35f), h * 0.18f, Offset(x, size.height / 2f))
                }
            }
        }
    }
}

/**
 * Título grande que nunca se corta: usa [maximo] si cabe en una línea y, si no, el tamaño más grande que quepa (hasta
 * [minimo]). Se mide una vez por texto y ancho, no en cada fotograma.
 */
@Composable
fun TituloAjustable(
    texto: String,
    modifier: Modifier = Modifier,
    maximo: androidx.compose.ui.unit.TextUnit = 40.sp,
    minimo: androidx.compose.ui.unit.TextUnit = 24.sp,
) {
    val medidor = androidx.compose.ui.text.rememberTextMeasurer()
    androidx.compose.foundation.layout.BoxWithConstraints(modifier) {
        val ancho = constraints.maxWidth
        val tam = remember(texto, ancho, maximo, minimo) {
            var t = maximo.value
            while (t > minimo.value) {
                val r = medidor.measure(texto, LectorType.Display.copy(fontSize = t.sp, lineHeight = (t * 1.1f).sp), maxLines = 1, softWrap = false)
                if (r.size.width <= ancho) break
                t -= 1f
            }
            t
        }
        LText(texto, LectorType.Display.copy(fontSize = tam.sp, lineHeight = (tam * 1.1f).sp), maxLines = 1)
    }
}
