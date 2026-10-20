package com.example.lector.ui

import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.Constraints
import kotlin.math.ceil

/*
 * Cristal esmerilado (el aspecto de la barra de navegación y del mini reproductor de musicfy):
 * lo que hay DEBAJO se ve desenfocado a través de la pieza, con un tinte oscuro encima, un grano
 * finísimo para que el degradado no haga bandas y una línea blanca de 2 px al 5 % por dentro del borde.
 *
 * Cómo funciona: [raizCristal] graba lo que dibuja la app en una capa (GraphicsLayer) además de
 * pintarlo; cada pieza de cristal vuelve a dibujar esa capa, desplazada a su posición, con un
 * desenfoque (RenderEffect, Android 12+). En Android 8–11 no hay desenfoque: se usa un tinte casi opaco.
 *
 * Importante: una pieza de cristal NO puede estar dentro de la raíz cuya capa dibuja (se dibujaría a
 * sí misma). La barra, el mini lector y los avisos van fuera; una cabecera de cristal usa una raíz
 * propia sobre su contenido desplazable.
 */

@Stable
class EstadoCristal {
    internal var capa by mutableStateOf<GraphicsLayer?>(null)
    internal var origen = Offset.Zero
}

/** El desenfoque solo existe desde Android 12. */
val hayDesenfoque: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

/** Graba lo que dibuja este elemento para que las piezas de cristal lo vean desenfocado. */
@Composable
fun Modifier.raizCristal(estado: EstadoCristal): Modifier {
    val capa = rememberGraphicsLayer()
    if (estado.capa !== capa) estado.capa = capa
    if (!hayDesenfoque) return this
    return this
        .onGloballyPositioned { estado.origen = it.positionInWindow() }
        .drawWithContent {
            capa.record { this@drawWithContent.drawContent() }
            drawLayer(capa)
        }
}

/** Grano: un mosaico de 64×64 píxeles medio claros, medio oscuros, repetido. Se crea una vez. */
private val GranoBrush: ShaderBrush by lazy {
    val r = java.util.Random(0x6A55)
    val n = 64
    val pixeles = IntArray(n * n) {
        val a = r.nextInt(256) shl 24
        if (r.nextBoolean()) a or 0xFFFFFF else a
    }
    val tile = android.graphics.Bitmap.createBitmap(pixeles, n, n, android.graphics.Bitmap.Config.ARGB_8888)
    ShaderBrush(ImageShader(tile.asImageBitmap(), TileMode.Repeated, TileMode.Repeated))
}

/** Dibuja la capa de la raíz desplazada a la posición de este elemento. */
private fun DrawScope.dibujarFondo(estado: EstadoCristal, posicion: Offset) {
    val capa = estado.capa ?: return
    translate(left = -(posicion.x - estado.origen.x), top = -(posicion.y - estado.origen.y)) { drawLayer(capa) }
}

/** Posición en ventana guardada sin provocar recomposiciones (solo se lee al dibujar). */
private class Posicion { var valor = Offset.Zero }

/** Lo coloca [margen] px más grande por cada lado, centrado (para desenfocar con lo que hay más allá del borde). */
private fun Modifier.sangrado(margen: Int): Modifier = layout { medible, restricciones ->
    val w = if (restricciones.hasBoundedWidth) restricciones.maxWidth else restricciones.minWidth
    val h = if (restricciones.hasBoundedHeight) restricciones.maxHeight else restricciones.minHeight
    val p = medible.measure(Constraints.fixed(w + margen * 2, h + margen * 2))
    layout(w, h) { p.place(-margen, -margen) }
}

/**
 * Fondo de cristal: lo de debajo desenfocado + [tinte] + grano. Se pone como primer hijo de la pieza
 * (que debe recortarse a su forma). [radio] en píxeles.
 */
@Composable
fun FondoCristal(
    estado: EstadoCristal?,
    modifier: Modifier = Modifier,
    tinte: Color = LectorColors.Cristal.copy(alpha = if (hayDesenfoque) 0.78f else 0.96f),
    base: Color? = LectorColors.Cristal,
    radio: Float = 48f,
    grano: Boolean = true,
) {
    val pos = remember { Posicion() }
    val margen = if (hayDesenfoque) ceil(radio).toInt() else 0
    Box(modifier.clipToBounds()) {
        Box(
            Modifier.fillMaxSize()
                .then(if (margen > 0) Modifier.sangrado(margen) else Modifier)
                .onGloballyPositioned { pos.valor = it.positionInWindow() }
                .graphicsLayer {
                    if (hayDesenfoque && radio > 0.5f) renderEffect = BlurEffect(radio, radio, TileMode.Clamp)
                    clip = true
                }
                .drawWithContent {
                    if (base != null) drawRect(base)
                    if (hayDesenfoque && estado != null) dibujarFondo(estado, pos.valor)
                    drawRect(tinte)
                },
        )
        if (grano) Box(Modifier.fillMaxSize().drawWithContent { drawRect(GranoBrush, alpha = 0.035f) })
    }
}

/**
 * Cristal que se desvanece hacia abajo (cabeceras): el desenfoque solo se ve arriba y se funde con la página.
 * [radio] se lee al dibujar, así se puede animar sin recomponer.
 */
@Composable
fun CristalProgresivo(
    estado: EstadoCristal,
    radio: () -> Float,
    base: Color,
    modifier: Modifier = Modifier,
) {
    val pos = remember { Posicion() }
    val mascara = remember { Brush.verticalGradient(0f to Color.Black, 0.55f to Color.Black, 1f to Color.Transparent) }
    Box(
        modifier
            .clipToBounds()
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                drawRect(mascara, blendMode = BlendMode.DstIn)
            },
    ) {
        Box(
            Modifier.fillMaxSize()
                .onGloballyPositioned { pos.valor = it.positionInWindow() }
                .graphicsLayer {
                    val r = radio()
                    renderEffect = if (hayDesenfoque && r > 0.5f) BlurEffect(r, r, TileMode.Clamp) else null
                    clip = true
                }
                .drawWithContent {
                    drawRect(base)
                    if (hayDesenfoque) dibujarFondo(estado, pos.valor)
                    drawRect(base.copy(alpha = if (hayDesenfoque) 0.55f else 0.9f))
                },
        )
    }
}

/** La línea finísima del borde del cristal (2 px reales), por dentro para que el recorte no la corte. */
fun DrawScope.lineaCristal(radioEsquina: Float, alpha: Float = 1f) {
    val ancho = 2f
    val m = ancho / 2f
    drawRoundRect(
        color = if (TemaActual.oscuro) Color.White.copy(alpha = 0.05f) else Color.Black.copy(alpha = 0.08f),
        topLeft = Offset(m, m),
        size = Size(size.width - ancho, size.height - ancho),
        cornerRadius = CornerRadius((radioEsquina - m).coerceAtLeast(0f)),
        style = Stroke(ancho),
        alpha = alpha,
    )
}

/** Estado de cristal de toda la app (lo que hay bajo la barra flotante). Lo crea Inicio. */
val CristalApp = EstadoCristal()
