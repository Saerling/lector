package com.example.lector.ui

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
import androidx.compose.ui.composed
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

/**
 * Se aplasta bajo el dedo y vuelve con un pequeño rebote. Un toque rápido avisa de pulsar y soltar casi a la vez,
 * así que al soltar se hace primero el aplastado si todavía no se había visto.
 */
@Composable
fun Modifier.rebote(fuente: InteractionSource, escalaPulsada: Float = 0.94f): Modifier {
    val escala = remember { Animatable(1f) }
    LaunchedEffect(fuente, escalaPulsada) {
        var pulsados = 0
        fuente.interactions.collect { accion ->
            when (accion) {
                is PressInteraction.Press -> {
                    pulsados++
                    launch { escala.animateTo(escalaPulsada, spring(dampingRatio = 1f, stiffness = 1600f)) }
                }
                is PressInteraction.Release, is PressInteraction.Cancel -> {
                    pulsados = (pulsados - 1).coerceAtLeast(0)
                    if (pulsados == 0) {
                        launch {
                            if (escala.value > escalaPulsada + 0.03f) escala.animateTo(escalaPulsada, tween(70))
                            escala.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = 520f))
                        }
                    }
                }
                else -> {}
            }
        }
    }
    return graphicsLayer { scaleX = escala.value; scaleY = escala.value }
}

/** Botón / tarjeta tocable con rebote (sin ondas cuadradas). */
@Composable
fun Modifier.pressable(onClick: () -> Unit): Modifier = pressableCon(0.95f, onClick)

@Composable
fun Modifier.pressableCon(escalaPulsada: Float, onClick: () -> Unit): Modifier {
    val fuente = remember { MutableInteractionSource() }
    return this
        .rebote(fuente, escalaPulsada)
        .clickable(interactionSource = fuente, indication = null, onClick = onClick)
}

/**
 * Fila tocable: al pulsar aparece detrás una caja redondeada suave (en lugar de la onda cuadrada) que se desvanece
 * al soltar. Se dibuja por debajo del contenido y queda [margenX] hacia dentro, así no se corta en los bordes.
 */
@Composable
fun Modifier.resaltable(radio: Dp = 22.dp, margenX: Dp = 12.dp, onClick: () -> Unit): Modifier {
    val fuente = remember { MutableInteractionSource() }
    val fuerza = remember { Animatable(0f) }
    LaunchedEffect(fuente) {
        val pulsaciones = ArrayList<PressInteraction.Press>()
        fuente.interactions.collect { accion ->
            when (accion) {
                is PressInteraction.Press -> pulsaciones.add(accion)
                is PressInteraction.Release -> pulsaciones.remove(accion.press)
                is PressInteraction.Cancel -> pulsaciones.remove(accion.press)
                else -> return@collect
            }
            if (pulsaciones.isNotEmpty()) {
                launch { fuerza.animateTo(1f, tween(90)) }
            } else {
                launch {
                    // un toque rápido suelta antes de que la caja se haya visto: se enseña un instante
                    if (fuerza.value < 1f) fuerza.animateTo(1f, tween(70))
                    fuerza.animateTo(0f, tween(340))
                }
            }
        }
    }
    return this
        .drawBehind {
            val f = fuerza.value
            if (f > 0f) {
                val mx = margenX.toPx()
                drawRoundRect(
                    Color.White.copy(alpha = 0.12f * f),
                    topLeft = Offset(mx, 0f),
                    size = Size(size.width - mx * 2f, size.height),
                    cornerRadius = CornerRadius(radio.toPx()),
                )
            }
        }
        .clickable(interactionSource = fuente, indication = null, onClick = onClick)
}

/**
 * Inclina el elemento hacia donde se pulsa (como una tarjeta física) y lo encoge un poco. No consume el toque,
 * así que se puede combinar con clickable. Si el dedo se arrastra (scroll), la inclinación se suelta.
 */
fun Modifier.press3D(inclinacionMax: Float = 7f, escalaPulsada: Float = 0.97f): Modifier = composed {
    val ambito = rememberCoroutineScope()
    val pulso = remember { Animatable(0f) }
    var normal by remember { mutableStateOf(Offset.Zero) }
    var medidas by remember { mutableStateOf(IntSize.Zero) }
    val muelle = remember { spring<Float>(dampingRatio = 1f, stiffness = 420f) }

    this
        .pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val abajo = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.pressed } ?: continue
                    val caja = this.size
                    if (caja.width > 0 && caja.height > 0) {
                        val p = abajo.position
                        normal = Offset(
                            ((p.x / caja.width) * 2f - 1f).coerceIn(-1f, 1f),
                            ((p.y / caja.height) * 2f - 1f).coerceIn(-1f, 1f),
                        )
                        medidas = caja
                    }
                    val holgura = viewConfiguration.touchSlop * 1.5f
                    ambito.launch { pulso.animateTo(1f, muelle) }
                    while (true) {
                        val ev = awaitPointerEvent(PointerEventPass.Initial)
                        val c = ev.changes.firstOrNull()
                        if (c == null || !c.pressed || (c.position - abajo.position).getDistance() > holgura) break
                    }
                    ambito.launch { pulso.animateTo(0f, muelle) }
                    // espera a que se suelte del todo antes de volver a escuchar
                    while (true) {
                        val ev = awaitPointerEvent(PointerEventPass.Initial)
                        if (ev.changes.none { it.pressed }) break
                    }
                }
            }
        }
        .graphicsLayer {
            val t = pulso.value
            if (t > 0f) {
                val s = 1f - (1f - escalaPulsada) * t
                scaleX = s
                scaleY = s
                rotationX = -normal.y * inclinacionMax * t
                rotationY = normal.x * inclinacionMax * t
                cameraDistance = (medidas.width.takeIf { it > 0 }?.toFloat() ?: 1000f) * 3f
                transformOrigin = TransformOrigin.Center
            }
        }
}

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
data class Aviso(val id: Long, val texto: String)

/**
 * Avisos como una píldora oscura que cae desde arriba. Se pueden lanzar desde cualquier sitio (también desde hilos
 * de fondo); uno nuevo sustituye al que se ve, y deslizar hacia arriba lo quita antes de tiempo.
 */
object Avisos {
    private val _actual = MutableStateFlow<Aviso?>(null)
    val actual: StateFlow<Aviso?> = _actual.asStateFlow()
    private val contador = AtomicLong(0)

    fun mostrar(texto: String) { _actual.value = Aviso(contador.incrementAndGet(), texto) }

    fun ocultar(id: Long) { _actual.update { if (it?.id == id) null else it } }
}

/** Se pone una sola vez, encima de toda la app. */
@Composable
fun AvisosHost(modifier: Modifier = Modifier) {
    val aviso by Avisos.actual.collectAsState()
    LaunchedEffect(aviso?.id) {
        val a = aviso ?: return@LaunchedEffect
        delay(if (a.texto.length > 40) 3500L else 2400L)
        Avisos.ocultar(a.id)
    }
    Box(modifier.fillMaxWidth().statusBarsPadding().padding(top = 12.dp, start = 24.dp, end = 24.dp), Alignment.TopCenter) {
        AnimatedContent(
            targetState = aviso,
            transitionSpec = {
                (fadeIn(tween(260, easing = EnfasisEasing)) +
                    slideInVertically(tween(380, easing = EnfasisEasing)) { -it } +
                    scaleIn(tween(380, easing = EnfasisEasing), initialScale = 0.9f))
                    .togetherWith(
                        fadeOut(tween(180)) + slideOutVertically(tween(260, easing = EnfasisEasing)) { -it }
                    ).using(SizeTransform(clip = false))
            },
            label = "avisos",
        ) { a ->
            if (a == null) {
                Spacer(Modifier.height(0.dp))
            } else {
                val forma = SuaveShape(26.dp)
                Box(
                    Modifier.widthIn(max = 380.dp).clip(forma).background(Color(0xF01E1E20))
                        .border(1.dp, LectorColors.GlassBorderBrush, forma)
                        .pointerInput(a.id) {
                            detectVerticalDragGestures { _, dy -> if (dy < -6f) Avisos.ocultar(a.id) }
                        }
                        .padding(horizontal = 22.dp, vertical = 14.dp),
                ) { LText(a.texto, LectorType.Body, maxLines = 3) }
            }
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
