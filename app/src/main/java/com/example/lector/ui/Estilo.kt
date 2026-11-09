package com.example.lector.ui

import androidx.compose.ui.composed
import androidx.compose.ui.unit.isSpecified
import androidx.compose.runtime.remember
import com.example.lector.ui.mochy.RoundedHighlight
import com.example.lector.ui.mochy.TopToaster
import com.example.lector.ui.mochy.pressBounce
import com.example.lector.ui.mochy.press3D as mochyPress3D

import androidx.compose.animation.core.keyframes
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.shadow
import androidx.compose.animation.core.Spring
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.node.DelegatingNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.SuspendingPointerInputModifierNode
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.unit.Constraints
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/*
 * Kit de diseño inspirado en la app musicfy: pulsación con rebote, inclinación 3D, resalte suave de filas,
 * esquinas continuas, esqueletos con ola, avisos que caen desde arriba y números que ruedan.
 * Todo está escrito para Lector (Compose 1.7), sin depender de nada más que de Compose.
 */

/** Curva "enfática": arranca rápido y se asienta despacio. La usan las entradas y los cambios de pantalla. */
val EnfasisEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

// ───────────────────────── Esquinas continuas ─────────────────────────

/**
 * Rectángulo cuyas esquinas son un cuarto de superelipse en vez de un cuarto de círculo (el "squircle" de los
 * iconos de las apps): la curva entra en el borde sin cambio brusco y se lee como una sola forma suave.
 */
@Immutable
data class SuaveShape(
    val radio: Dp,
    val exponente: Float = 4f,
    /** Personalización → Estilo → Esquinas. Forma parte de la igualdad, así que cambiarla rehace los recortes. */
    val estilo: com.example.lector.ui.mochy.EstiloApp.Esquinas = com.example.lector.ui.mochy.EstiloApp.esquinas,
) : Shape {
    // Mochy: la superelipse de SmoothCornerShape (ui/mochy/SmoothCornerShape.kt), la de siempre.
    // Continuas: ContinuousCornerShape de BitChord (ui/bitchord/ContinuousCornerShape.kt), la esquina de Apple/Figma
    // (cúbica + arco; las cápsulas conservan su extremo semicircular). [exponente] se traduce en el suavizado.
    private val forma: Shape = when (estilo) {
        com.example.lector.ui.mochy.EstiloApp.Esquinas.MOCHY -> com.example.lector.ui.mochy.SmoothCornerShape(radio, exponente)
        com.example.lector.ui.mochy.EstiloApp.Esquinas.CONTINUAS -> com.example.lector.ui.bitchord.ContinuousCornerShape(
            androidx.compose.foundation.shape.CornerSize(radio), androidx.compose.foundation.shape.CornerSize(radio),
            androidx.compose.foundation.shape.CornerSize(radio), androidx.compose.foundation.shape.CornerSize(radio),
            smoothing = ((exponente - 2f) / 2f * com.example.lector.ui.bitchord.GLASS_CORNER_SMOOTHING).coerceIn(0f, 1f),
        )
    }
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        forma.createOutline(size, layoutDirection, density)
}

// ───────────────────────── Pulsación ─────────────────────────
//
// Las tres pulsaciones (rebote, inclinación 3D y resalte de fila) son un único Modifier.Node: antes eran
// funciones @Composable / composed {} con remember + LaunchedEffect POR TARJETA, lo que en cuadrículas de cientos de
// portadas suponía mucho trabajo en cada recomposición. Ahora no crean estado de composición.

private enum class TipoPulsacion { REBOTE, INCLINAR, RESALTE }

private data class PulsacionElement(
    val tipo: TipoPulsacion,
    val escala: Float,
    val inclinacion: Float,
    val radio: Dp,
    val margen: Dp,
) : ModifierNodeElement<PulsacionNode>() {
    override fun create() = PulsacionNode(tipo, escala, inclinacion, radio, margen)
    override fun update(node: PulsacionNode) {
        node.tipo = tipo; node.escala = escala; node.inclinacion = inclinacion; node.radio = radio; node.margen = margen
        node.invalidateDraw()
    }
}

private class PulsacionNode(
    var tipo: TipoPulsacion,
    var escala: Float,
    var inclinacion: Float,
    var radio: Dp,
    var margen: Dp,
) : DelegatingNode(), DrawModifierNode {
    private val t = Animatable(0f)
    // Living Flow (ColorOS 17): «la luz como respuesta». Un brillo suave nace bajo el dedo, lo sigue mientras se
    // mantiene y se apaga despacio al soltar.
    private val luz = Animatable(0f)
    private var punto by androidx.compose.runtime.mutableStateOf(Offset.Zero)
    private var normal = Offset.Zero
    private var ancho = 0

    init {
        delegate(SuspendingPointerInputModifierNode { detectar() })
    }

    // Observa el dedo sin consumir nada (así se combina con clickable y con el scroll de las listas).
    private suspend fun PointerInputScope.detectar() {
        awaitPointerEventScope {
            while (true) {
                val abajo = awaitPointerEvent(PointerEventPass.Initial).changes
                    .firstOrNull { it.pressed && !it.previousPressed } ?: continue
                val caja = size
                if (caja.width > 0 && caja.height > 0) {
                    val p = abajo.position
                    normal = Offset(
                        ((p.x / caja.width) * 2f - 1f).coerceIn(-1f, 1f),
                        ((p.y / caja.height) * 2f - 1f).coerceIn(-1f, 1f),
                    )
                    ancho = caja.width
                }
                val holgura = viewConfiguration.touchSlop * 1.5f
                punto = abajo.position
                pulsar(true)
                while (true) {
                    val ev = awaitPointerEvent(PointerEventPass.Initial)
                    val c = ev.changes.firstOrNull { it.id == abajo.id } ?: break
                    if (c.pressed) punto = c.position
                    // Si el dedo se arrastra (scroll), se suelta la pulsación.
                    if (!c.pressed || (c.position - abajo.position).getDistance() > holgura) break
                }
                pulsar(false)
                while (true) {
                    val ev = awaitPointerEvent(PointerEventPass.Initial)
                    if (ev.changes.none { it.pressed }) break
                }
            }
        }
    }

    private fun pulsar(abajo: Boolean) {
        coroutineScope.launch {
            if (abajo) luz.animateTo(1f, tween(120, easing = EnfasisEasing))
            else luz.animateTo(0f, tween(520, easing = EnfasisEasing))
        }
    }

    override fun ContentDrawScope.draw() {
        drawContent()
        val l = if (Aspecto.luzTactil) luz.value else 0f
        if (l > 0f) {
            // Luz radial recortada a la forma del elemento (píldora, portada o fila), sin salirse a los vecinos.
            val menor = minOf(size.width, size.height)
            val r = (maxOf(size.width, size.height) * 0.6f).coerceIn(40.dp.toPx(), 170.dp.toPx())
            val esquina = when (tipo) {
                TipoPulsacion.REBOTE -> menor / 2f
                TipoPulsacion.INCLINAR -> 14.dp.toPx()
                TipoPulsacion.RESALTE -> radio.toPx()
            }
            val mx = if (tipo == TipoPulsacion.RESALTE) margen.toPx() else 0f
            drawRoundRect(
                Brush.radialGradient(
                    0f to LectorColors.Text.copy(alpha = 0.20f * l),
                    0.45f to LectorColors.Text.copy(alpha = 0.07f * l),
                    1f to Color.Transparent,
                    center = punto, radius = r,
                ),
                topLeft = Offset(mx, 0f), size = Size(size.width - mx * 2f, size.height), cornerRadius = CornerRadius(esquina),
            )
        }
    }
}

/*
 * Las pulsaciones delegan en el código de Mochy (ui/mochy/TouchFeedback.kt y Press3D.kt): pressBounce para el
 * rebote, RoundedHighlight como indicación (caja redondeada en vez de la onda cuadrada) y press3D para la
 * inclinación. El nodo de arriba solo añade la «luz táctil» de Shiori cuando está activada en Aspecto.
 */
private fun Modifier.luzTactil(tipo: TipoPulsacion, radio: Dp, margen: Dp): Modifier =
    if (Aspecto.luzTactil) this.then(PulsacionElement(tipo, 1f, 0f, radio, margen)) else this

/** Botón / tarjeta tocable con rebote (sin ondas cuadradas). Con [onLongClick], también mantener pulsado. */
fun Modifier.pressable(onClick: () -> Unit): Modifier = pressableCon(0.95f, null, onClick)

fun Modifier.pressable(onLongClick: (() -> Unit)?, onClick: () -> Unit): Modifier = pressableCon(0.95f, onLongClick, onClick)

fun Modifier.pressableCon(escalaPulsada: Float, onClick: () -> Unit): Modifier = pressableCon(escalaPulsada, null, onClick)

@OptIn(ExperimentalFoundationApi::class)
fun Modifier.pressableCon(escalaPulsada: Float, onLongClick: (() -> Unit)?, onClick: () -> Unit): Modifier = composed {
    val fuente = remember { MutableInteractionSource() }
    val base = this.pressBounce(fuente, escalaPulsada).luzTactil(TipoPulsacion.REBOTE, 0.dp, 0.dp)
    if (onLongClick == null) base.clickable(interactionSource = fuente, indication = null, onClick = onClick)
    else base.combinedClickable(interactionSource = fuente, indication = null, onLongClick = onLongClick, onClick = onClick)
}

/**
 * Fila tocable: al pulsar aparece detrás una caja redondeada suave (en lugar de la onda cuadrada) que se desvanece
 * al soltar. Se dibuja por debajo del contenido y queda [margenX] hacia dentro, así no se corta en los bordes.
 */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.resaltable(radio: Dp = 22.dp, margenX: Dp = 12.dp, onLongClick: (() -> Unit)? = null, onClick: () -> Unit): Modifier = composed {
    val fuente = remember { MutableInteractionSource() }
    // RoundedHighlight de Mochy: un margen negativo la deja dentro de la fila.
    val resalte = RoundedHighlight(cornerRadius = radio, spillX = -margenX, color = LectorColors.Text.copy(alpha = 0.12f))
    val base = this.luzTactil(TipoPulsacion.RESALTE, radio, margenX)
    if (onLongClick == null) base.clickable(interactionSource = fuente, indication = resalte, onClick = onClick)
    else base.combinedClickable(interactionSource = fuente, indication = resalte, onLongClick = onLongClick, onClick = onClick)
}

/**
 * Inclina el elemento hacia donde se pulsa (como una tarjeta física) y lo encoge un poco. No consume el toque,
 * así que se puede combinar con clickable. Con el movimiento reducido solo encoge, sin inclinarse.
 */
fun Modifier.press3D(inclinacionMax: Float = 7f, escalaPulsada: Float = 0.97f): Modifier = composed {
    this.mochyPress3D(
        maxTilt = if (MovimientoReducido.activo) 0f else inclinacionMax,
        pressedScale = escalaPulsada,
    ).luzTactil(TipoPulsacion.INCLINAR, 0.dp, 0.dp)
}

// ───────────────────────── Esqueletos de carga ─────────────────────────

private val ColorHueso = Color(0xFFD9D9D9).copy(alpha = 0.22f)

/** Contenedor de esqueletos: el BonesHost de Mochy (ola de opacidad); sin animaciones, quietos. */
@Composable
fun BonesHost(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    if (MovimientoReducido.activo) Column(modifier, content = content)
    else com.example.lector.ui.mochy.BonesHost(modifier, content)
}

@Composable
fun Bone(ancho: Dp, alto: Dp, modifier: Modifier = Modifier, forma: Shape = SuaveShape(20.dp)) {
    com.example.lector.ui.mochy.Bone(ancho, alto, modifier, forma)
}

/** Esqueleto de la lista de capítulos (mismas medidas que las filas reales). */
@Composable
fun BonesCapitulos(filas: Int = 7) {
    BonesHost(Modifier.fillMaxWidth()) {
        repeat(filas) {
            Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Bone(64.dp, 64.dp)
                Spacer(Modifier.width(16.dp))
                Column {
                    Bone(170.dp, 16.dp, forma = SuaveShape(8.dp))
                    Spacer(Modifier.height(8.dp))
                    Bone(110.dp, 12.dp, forma = SuaveShape(6.dp))
                }
            }
        }
    }
}

/** Esqueleto de una fila horizontal de portadas. */
@Composable
fun BonesPortadas(cantidad: Int = 3) {
    BonesHost(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 24.dp), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)) {
            repeat(cantidad) {
                Column {
                    Bone(124.dp, 186.dp, forma = SuaveShape(12.dp))
                    Spacer(Modifier.height(8.dp))
                    Bone(90.dp, 12.dp, forma = SuaveShape(6.dp))
                }
            }
        }
    }
}

/** Esqueleto de una cuadrícula de portadas (catálogo de una fuente mientras llega la primera página). */
@Composable
fun BonesRejilla(filas: Int = 3, columnas: Int = 3) {
    BonesHost(Modifier.fillMaxWidth()) {
        repeat(filas) {
            Row(Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)) {
                repeat(columnas) {
                    Column(Modifier.weight(1f)) {
                        Box(Modifier.fillMaxWidth().then(Modifier.aspectRatio(2f / 3f)).background(ColorHueso, SuaveShape(14.dp)))
                        Spacer(Modifier.height(8.dp))
                        Bone(80.dp, 12.dp, forma = SuaveShape(6.dp))
                    }
                }
            }
        }
    }
}

// ───────────────────────── Avisos (en lugar de Toast) ─────────────────────────

@Immutable
data class Aviso(val id: Long, val texto: String, val accion: String? = null, val alAccion: (() -> Unit)? = null)

/**
 * Avisos como una píldora oscura que cae desde arriba. Se pueden lanzar desde cualquier sitio (también desde hilos
 * de fondo); uno nuevo sustituye al que se ve, y deslizar hacia arriba lo quita antes de tiempo.
 */
object Avisos {
    private val _actual = MutableStateFlow<Aviso?>(null)
    val actual: StateFlow<Aviso?> = _actual.asStateFlow()
    private val contador = AtomicLong(0)

    // Se dibujan con TopToaster / TopToastHost de Mochy (ui/mochy/TopToast.kt).
    fun mostrar(texto: String) {
        if (esError(texto)) {
            TopToaster.error(texto)
            Tacto.rechazar()
        } else {
            TopToaster.show(texto, iconRes = Icons.Rounded.Info)
        }
        _actual.value = Aviso(TopToaster.current.value?.id ?: contador.incrementAndGet(), texto)
    }

    /** Aviso con un botón (p. ej. «Buscar en todas»): tocarlo lo ejecuta y cierra el aviso. */
    fun mostrar(texto: String, accion: String, alAccion: () -> Unit) {
        TopToaster.show(texto, iconRes = Icons.Rounded.Info, actionLabel = accion, onAction = alAccion)
        _actual.value = Aviso(TopToaster.current.value?.id ?: contador.incrementAndGet(), texto, accion, alAccion)
    }

    fun ocultar(id: Long) {
        TopToaster.dismiss(id)
        _actual.update { if (it?.id == id) null else it }
    }
}

/** Curva de los avisos (salida rápida, llegada suave). */
private val AvisoEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

/** Un aviso «de error» (para pintar la cruz roja en vez del icono). */
private fun esError(t: String): Boolean {
    val x = t.lowercase()
    return x.startsWith("no se pudo") || x.startsWith("error") || x.contains("falló") || x.startsWith("no hay")
}

/** Avisos: el TopToastHost de Mochy, una sola vez encima de toda la app. */
@Composable
fun AvisosHost(modifier: Modifier = Modifier) {
    com.example.lector.ui.mochy.TopToastHost(modifier)
}

// ───────────────────────── Números que ruedan ─────────────────────────

/** Texto numérico cuyos dígitos ruedan al cambiar: el OdometerNumber de Mochy (ui/mochy/OdometerText.kt). */
@Composable
fun Odometro(texto: String, estilo: TextStyle, modifier: Modifier = Modifier, color: Color = LectorColors.Text) {
    val tam = if (estilo.fontSize.isSpecified) estilo.fontSize else LectorType.Body.fontSize
    com.example.lector.ui.mochy.OdometerNumber(
        value = texto,
        fontSize = tam,
        fontWeight = estilo.fontWeight ?: FontWeight.Bold,
        fontFamily = estilo.fontFamily ?: LectorFont,
        color = color,
        letterSpacing = if (estilo.letterSpacing.isSpecified) estilo.letterSpacing else 0.sp,
        modifier = modifier,
    )
}
