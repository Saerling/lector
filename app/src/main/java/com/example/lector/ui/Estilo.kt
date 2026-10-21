package com.example.lector.ui

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
data class SuaveShape(val radio: Dp, val exponente: Float = 4f) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val r = with(density) { radio.toPx() }.coerceAtMost(minOf(size.width, size.height) / 2f)
        if (r <= 0f) return Outline.Rectangle(Rect(0f, 0f, size.width, size.height))
        return Outline.Generic(Path().apply { rectanguloSuave(size.width, size.height, r, exponente.toDouble()) })
    }
}

private const val PasosEsquina = 12

private fun Path.rectanguloSuave(w: Float, h: Float, r: Float, n: Double) {
    val potencia = 2.0 / n
    fun along(t: Double): Float = (r * abs(cos(t)).pow(potencia)).toFloat()
    fun across(t: Double): Float = (r * abs(sin(t)).pow(potencia)).toFloat()
    moveTo(r, 0f)
    lineTo(w - r, 0f)
    for (i in 1..PasosEsquina) { val t = PI / 2 * i / PasosEsquina; lineTo(w - r + across(t), r - along(t)) }
    lineTo(w, h - r)
    for (i in 1..PasosEsquina) { val t = PI / 2 * i / PasosEsquina; lineTo(w - r + along(t), h - r + across(t)) }
    lineTo(r, h)
    for (i in 1..PasosEsquina) { val t = PI / 2 * i / PasosEsquina; lineTo(r - across(t), h - r + along(t)) }
    lineTo(0f, r)
    for (i in 1..PasosEsquina) { val t = PI / 2 * i / PasosEsquina; lineTo(r - along(t), r - across(t)) }
    close()
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
) : DelegatingNode(), LayoutModifierNode, DrawModifierNode {
    private val t = Animatable(0f)
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
                pulsar(true)
                while (true) {
                    val ev = awaitPointerEvent(PointerEventPass.Initial)
                    val c = ev.changes.firstOrNull { it.id == abajo.id } ?: break
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
            when (tipo) {
                TipoPulsacion.REBOTE ->
                    if (abajo) t.animateTo(1f, spring(dampingRatio = 1f, stiffness = 1600f))
                    else {
                        // Un toque rápido suelta antes de verse el aplastado: se enseña primero.
                        if (t.value < 0.6f) t.animateTo(1f, tween(70))
                        t.animateTo(0f, spring(dampingRatio = 0.4f, stiffness = 520f))
                    }
                TipoPulsacion.INCLINAR -> t.animateTo(if (abajo) 1f else 0f, spring(dampingRatio = 1f, stiffness = 420f))
                TipoPulsacion.RESALTE ->
                    if (abajo) t.animateTo(1f, tween(90))
                    else {
                        if (t.value < 1f) t.animateTo(1f, tween(70))
                        t.animateTo(0f, tween(340))
                    }
            }
        }
    }

    override fun MeasureScope.measure(measurable: Measurable, constraints: Constraints): MeasureResult {
        val p = measurable.measure(constraints)
        return layout(p.width, p.height) {
            if (tipo == TipoPulsacion.RESALTE) p.place(0, 0)
            else p.placeWithLayer(0, 0) {
                val v = t.value
                if (v != 0f) {
                    val s = 1f - (1f - escala) * v
                    scaleX = s
                    scaleY = s
                    if (tipo == TipoPulsacion.INCLINAR) {
                        rotationX = -normal.y * inclinacion * v
                        rotationY = normal.x * inclinacion * v
                        cameraDistance = (ancho.takeIf { it > 0 }?.toFloat() ?: 1000f) * 3f
                        transformOrigin = TransformOrigin.Center
                    }
                }
            }
        }
    }

    override fun ContentDrawScope.draw() {
        if (tipo == TipoPulsacion.RESALTE) {
            val f = t.value.coerceIn(0f, 1f)
            if (f > 0f) {
                val mx = margen.toPx()
                drawRoundRect(
                    LectorColors.Text.copy(alpha = 0.12f * f),
                    topLeft = Offset(mx, 0f),
                    size = Size(size.width - mx * 2f, size.height),
                    cornerRadius = CornerRadius(radio.toPx()),
                )
            }
        }
        drawContent()
    }
}

/** Botón / tarjeta tocable con rebote (sin ondas cuadradas). Con [onLongClick], también mantener pulsado. */
fun Modifier.pressable(onClick: () -> Unit): Modifier = pressableCon(0.95f, null, onClick)

fun Modifier.pressable(onLongClick: (() -> Unit)?, onClick: () -> Unit): Modifier = pressableCon(0.95f, onLongClick, onClick)

fun Modifier.pressableCon(escalaPulsada: Float, onClick: () -> Unit): Modifier = pressableCon(escalaPulsada, null, onClick)

@OptIn(ExperimentalFoundationApi::class)
fun Modifier.pressableCon(escalaPulsada: Float, onLongClick: (() -> Unit)?, onClick: () -> Unit): Modifier {
    val base = this.then(PulsacionElement(TipoPulsacion.REBOTE, escalaPulsada, 0f, 0.dp, 0.dp))
    return if (onLongClick == null) base.clickable(interactionSource = null, indication = null, onClick = onClick)
    else base.combinedClickable(interactionSource = null, indication = null, onLongClick = onLongClick, onClick = onClick)
}

/**
 * Fila tocable: al pulsar aparece detrás una caja redondeada suave (en lugar de la onda cuadrada) que se desvanece
 * al soltar. Se dibuja por debajo del contenido y queda [margenX] hacia dentro, así no se corta en los bordes.
 */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.resaltable(radio: Dp = 22.dp, margenX: Dp = 12.dp, onLongClick: (() -> Unit)? = null, onClick: () -> Unit): Modifier {
    val base = this.then(PulsacionElement(TipoPulsacion.RESALTE, 1f, 0f, radio, margenX))
    return if (onLongClick == null) base.clickable(interactionSource = null, indication = null, onClick = onClick)
    else base.combinedClickable(interactionSource = null, indication = null, onLongClick = onLongClick, onClick = onClick)
}

/**
 * Inclina el elemento hacia donde se pulsa (como una tarjeta física) y lo encoge un poco. No consume el toque,
 * así que se puede combinar con clickable. Si el dedo se arrastra (scroll), la inclinación se suelta.
 */
fun Modifier.press3D(inclinacionMax: Float = 7f, escalaPulsada: Float = 0.97f): Modifier =
    this.then(PulsacionElement(TipoPulsacion.INCLINAR, escalaPulsada, inclinacionMax, 0.dp, 0.dp))

// ───────────────────────── Esqueletos de carga ─────────────────────────

private val ColorHueso = Color(0xFFD9D9D9).copy(alpha = 0.22f)

/** Contenedor de esqueletos: una ola suave de opacidad baja por ellos (se lee solo al dibujar, no recompone). */
@Composable
fun BonesHost(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val ola = rememberInfiniteTransition(label = "bones")
    val fase = ola.animateFloat(
        0f, 1f, infiniteRepeatable(tween(2000, easing = LinearEasing)), label = "fase",
    )
    Column(
        modifier
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                val h = size.height
                val banda = h * 0.8f + 1f
                val centro = -banda + fase.value * (h + banda * 2f)
                drawRect(
                    brush = Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.45f),
                        0.5f to Color.Black,
                        1f to Color.Black.copy(alpha = 0.45f),
                        startY = centro - banda,
                        endY = centro + banda,
                    ),
                    blendMode = BlendMode.DstIn,
                )
            },
        content = content,
    )
}

@Composable
fun Bone(ancho: Dp, alto: Dp, modifier: Modifier = Modifier, forma: Shape = SuaveShape(20.dp)) {
    Box(modifier.size(ancho, alto).background(ColorHueso, forma))
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

    fun mostrar(texto: String) { _actual.value = Aviso(contador.incrementAndGet(), texto) }

    /** Aviso con un botón (p. ej. «Buscar en todas»): tocarlo lo ejecuta y cierra el aviso. */
    fun mostrar(texto: String, accion: String, alAccion: () -> Unit) {
        _actual.value = Aviso(contador.incrementAndGet(), texto, accion, alAccion)
    }

    fun ocultar(id: Long) { _actual.update { if (it?.id == id) null else it } }
}

/** Curva de los avisos (salida rápida, llegada suave). */
private val AvisoEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

/** Un aviso «de error» (para pintar la cruz roja en vez del icono). */
private fun esError(t: String): Boolean {
    val x = t.lowercase()
    return x.startsWith("no se pudo") || x.startsWith("error") || x.contains("falló") || x.startsWith("no hay")
}

/**
 * Avisos como los de musicfy: una tarjeta oscura de 30 dp de radio que cae desde arriba con un muelle, con un
 * cuadrado claro con icono a la izquierda y un visto a la derecha. Se arrastra hacia arriba para cerrarla y,
 * mientras se sujeta, no se cierra sola. Se pone una sola vez, encima de toda la app.
 */
@Composable
fun AvisosHost(modifier: Modifier = Modifier) {
    val aviso by Avisos.actual.collectAsState()
    Box(modifier.fillMaxWidth().statusBarsPadding().padding(top = 8.dp, start = 14.dp, end = 14.dp), Alignment.TopCenter) {
        AnimatedContent(
            targetState = aviso,
            contentKey = { it?.id },
            contentAlignment = Alignment.TopCenter,
            transitionSpec = {
                (slideInVertically(spring(dampingRatio = 0.74f, stiffness = Spring.StiffnessMedium)) { -it - 40 } +
                    fadeIn(tween(160)) + scaleIn(tween(320, easing = AvisoEasing), initialScale = 0.92f))
                    .togetherWith(slideOutVertically(tween(260, easing = AvisoEasing)) { -it - 40 } + fadeOut(tween(200)))
                    .using(SizeTransform(clip = false) { _, _ -> tween(1) })
            },
            label = "avisos",
        ) { a -> if (a != null) TarjetaAviso(a) else Spacer(Modifier.height(0.dp)) }
    }
}

@Composable
private fun TarjetaAviso(a: Aviso) {
    val scope = rememberCoroutineScope()
    val subida = remember(a.id) { Animatable(0f) }
    var sujeto by remember(a.id) { mutableStateOf(false) }
    LaunchedEffect(a.id, sujeto) {
        if (sujeto) return@LaunchedEffect
        delay(if (a.accion != null) 5000L else if (a.texto.length > 40) 3500L else 2400L)
        Avisos.ocultar(a.id)
    }
    val error = remember(a.texto) { esError(a.texto) }
    val forma = RoundedCornerShape(30.dp)
    val claro = Color(0xFFD9D9D9)
    Row(
        Modifier.widthIn(max = 480.dp).fillMaxWidth()
            .graphicsLayer { translationY = subida.value }
            .shadow(14.dp, forma, clip = false, ambientColor = Color.Black, spotColor = Color.Black)
            .clip(forma).background(LectorColors.Aviso)
            .pointerInput(a.id) {
                detectVerticalDragGestures(
                    onDragStart = { sujeto = true },
                    onDragEnd = {
                        sujeto = false
                        if (subida.value < -28.dp.toPx()) Avisos.ocultar(a.id)
                        else scope.launch { subida.animateTo(0f, spring(dampingRatio = 0.7f)) }
                    },
                    onDragCancel = { sujeto = false; scope.launch { subida.animateTo(0f, spring(dampingRatio = 0.7f)) } },
                ) { _, dy -> scope.launch { subida.snapTo((subida.value + dy).coerceAtMost(0f)) } }
            }
            .defaultMinSize(minHeight = 68.dp)
            .padding(start = 12.dp, end = 18.dp, top = 11.dp, bottom = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (error) {
            Box(Modifier.padding(start = 4.dp).size(30.dp).clip(CircleShape).background(Color(0xFFEE5A52)), Alignment.Center) {
                Icon(Icons.Rounded.Close, null, tint = Color.Black, modifier = Modifier.size(18.dp))
            }
        } else {
            Box(Modifier.size(46.dp).clip(RoundedCornerShape(13.dp)).background(claro), Alignment.Center) {
                Icon(Icons.Rounded.Info, null, tint = Color(0xFF232323), modifier = Modifier.size(24.dp))
            }
        }
        Spacer(Modifier.width(14.dp))
        LText(
            a.texto, LectorType.Body.copy(fontSize = 17.sp, lineHeight = 21.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.6).sp),
            Modifier.weight(1f), maxLines = 3,
        )
        if (a.accion != null) {
            Box(
                Modifier.padding(start = 12.dp).clip(CircleShape).background(claro)
                    .pressable { Avisos.ocultar(a.id); a.alAccion?.invoke() }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            ) { LText(a.accion, LectorType.Label.copy(fontWeight = FontWeight.Bold), color = Color(0xFF232323), maxLines = 1) }
        } else if (!error) Box(Modifier.padding(start = 12.dp).size(26.dp).clip(CircleShape).background(claro), Alignment.Center) {
            Icon(Icons.Rounded.Check, null, tint = Color(0xFF232323), modifier = Modifier.size(16.dp))
        }
    }
}

// ───────────────────────── Números que ruedan ─────────────────────────

/** Texto numérico cuyos dígitos ruedan hacia arriba o abajo al cambiar (como un cuentakilómetros). */
@Composable
fun Odometro(texto: String, estilo: TextStyle, modifier: Modifier = Modifier, color: Color = LectorColors.Text) {
    Row(modifier) {
        texto.forEachIndexed { i, c ->
            // Clave contando desde el final: así las unidades siguen siendo las mismas al pasar de 9 a 10.
            key(texto.length - i) {
                AnimatedContent(
                    targetState = c,
                    modifier = Modifier.clipToBounds(),
                    transitionSpec = {
                        val sube = targetState > initialState
                        (slideInVertically(tween(380, easing = EnfasisEasing)) { if (sube) it else -it } + fadeIn(tween(300)))
                            .togetherWith(
                                slideOutVertically(tween(380, easing = EnfasisEasing)) { if (sube) -it else it } + fadeOut(tween(200))
                            ).using(SizeTransform(clip = false))
                    },
                    label = "odometro",
                ) { ch -> androidx.compose.material3.Text(ch.toString(), style = estilo, color = color, maxLines = 1) }
            }
        }
    }
}
