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
import androidx.compose.ui.graphics.asComposeRenderEffect
import com.example.lector.ui.mochy.setAgslUniform

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

// ───── Liquid glass (estilo Apple) ─────
// Sobre el fondo ya desenfocado, un shader AGSL (Android 13+) hace de lente: cerca del borde la imagen se curva hacia
// dentro (refracción, con una pizca de aberración cromática), la saturación sube y un brillo especular recorre el
// canto, más fuerte arriba a la izquierda (donde «da» la luz) y con un rebote tenue abajo a la derecha. Usa las
// utilidades de AGSL de Mochy (ui/mochy/AgslShader.kt). En Android 12 queda el desenfoque y los brillos dibujados;
// en 8–11, el tinte.
private const val LIQUID_GLASS_SKSL = """
uniform shader content;
uniform float2 size;
uniform float inset;
uniform float radius;
uniform float band;
uniform float refract;
uniform float sat;
uniform float rim;

float sdRound(float2 p, float2 c, float2 hs, float r) {
    float2 q = abs(p - c) - (hs - r);
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
}

half4 main(float2 p) {
    float2 lo = float2(inset);
    float2 hi = size - float2(inset);
    float2 c = (lo + hi) * 0.5;
    float2 hs = max((hi - lo) * 0.5, float2(1.0));
    float r = min(radius, min(hs.x, hs.y));
    float d = sdRound(p, c, hs, r);
    // Normal hacia fuera (gradiente de la distancia).
    float e = 1.0;
    float2 n = float2(sdRound(p + float2(e, 0.0), c, hs, r) - sdRound(p - float2(e, 0.0), c, hs, r),
                      sdRound(p + float2(0.0, e), c, hs, r) - sdRound(p - float2(0.0, e), c, hs, r));
    float nl = length(n);
    n = nl > 0.0001 ? n / nl : float2(0.0);
    // 0 en el borde, 1 ya dentro de la banda: la lente solo curva el canto.
    float t = clamp(-d / band, 0.0, 1.0);
    float k = (1.0 - t) * (1.0 - t);
    float2 off = n * k * refract;
    half4 col = content.eval(p + off);
    half4 cr = content.eval(p + off * 1.12);
    half4 cb = content.eval(p + off * 0.88);
    col.r = cr.r;
    col.b = cb.b;
    // Más saturación (el «vibrancy» del cristal de Apple).
    float l = dot(float3(col.rgb), float3(0.2126, 0.7152, 0.0722));
    col.rgb = half3(mix(float3(l), float3(col.rgb), sat));
    // Brillo especular en el canto: luz arriba a la izquierda y un reflejo menor abajo a la derecha.
    float canto = 1.0 - smoothstep(0.0, band * 0.22, -d);
    float2 luz = normalize(float2(-0.55, -0.85));
    float dl = dot(n, luz);
    float spec = max(dl, 0.0) + 0.45 * max(-dl, 0.0);
    float brillo = rim * canto * (0.25 + 0.75 * spec) * step(d, 0.0);
    col.rgb = col.rgb + half3(brillo) * col.a;
    return col;
}
"""

@androidx.annotation.RequiresApi(33)
private class LenteLiquida {
    val shader: Any = com.example.lector.ui.mochy.createAgslShader(LIQUID_GLASS_SKSL)

    fun efecto(ancho: Float, alto: Float, margen: Float, densidad: Float, desenfoque: Float): androidx.compose.ui.graphics.RenderEffect {
        val hsMin = (minOf(ancho, alto) - margen * 2f) / 2f
        val s = shader
        s.setAgslUniform("size", ancho, alto)
        s.setAgslUniform("inset", margen)
        s.setAgslUniform("radius", minOf(hsMin, 34f * densidad))
        s.setAgslUniform("band", minOf(hsMin * 0.9f, 18f * densidad).coerceAtLeast(1f))
        s.setAgslUniform("refract", 9f * densidad)
        s.setAgslUniform("sat", if (TemaActual.oscuro) 1.45f else 1.25f)
        s.setAgslUniform("rim", if (TemaActual.oscuro) 0.22f else 0.35f)
        val lente = com.example.lector.ui.mochy.agslRenderEffect(s, "content")
        val blur = android.graphics.RenderEffect.createBlurEffect(desenfoque, desenfoque, android.graphics.Shader.TileMode.CLAMP)
        return android.graphics.RenderEffect.createChainEffect(lente, blur).asComposeRenderEffect()
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
    // Estilo elegido en Apariencia: cristal (por defecto), liquid glass (más transparente, más desenfoque y un brillo
    // de superficie) o sólido (sin desenfoque).
    val estilo = Aspecto.superficie
    val desenfoca = hayDesenfoque && estilo != Superficie.SOLIDO
    val radioReal = if (estilo == Superficie.LIQUIDO) radio * 1.3f else radio
    val tinteReal = when (estilo) {
        Superficie.SOLIDO -> LectorColors.Cristal
        Superficie.LIQUIDO -> tinte.copy(alpha = tinte.alpha * 0.55f)
        Superficie.CRISTAL -> tinte
    }
    val margen = if (desenfoca) ceil(radioReal).toInt() else 0
    // Liquid glass con lente (Android 13+); se crea una vez por pieza.
    val lente = remember(estilo == Superficie.LIQUIDO && desenfoca) {
        if (estilo == Superficie.LIQUIDO && desenfoca && Build.VERSION.SDK_INT >= 33) LenteLiquida() else null
    }
    Box(modifier.clipToBounds()) {
        Box(
            Modifier.fillMaxSize()
                .then(if (margen > 0) Modifier.sangrado(margen) else Modifier)
                .onGloballyPositioned { pos.valor = it.positionInWindow() }
                .graphicsLayer {
                    if (desenfoca && radioReal > 0.5f) {
                        renderEffect = if (lente != null && Build.VERSION.SDK_INT >= 33) {
                            lente.efecto(size.width, size.height, margen.toFloat(), density, radioReal)
                        } else BlurEffect(radioReal, radioReal, TileMode.Clamp)
                    }
                    clip = true
                }
                .drawWithContent {
                    if (base != null) drawRect(base)
                    if (desenfoca && estado != null) dibujarFondo(estado, pos.valor)
                    drawRect(tinteReal)
                },
        )
        // Liquid glass: brillo de superficie arriba y un reflejo tenue abajo, como una gota de cristal.
        if (estilo == Superficie.LIQUIDO) Box(
            Modifier.fillMaxSize().drawWithContent {
                val f = if (lente != null) 0.6f else 1f
                drawRect(
                    Brush.verticalGradient(
                        0f to Color.White.copy(alpha = (if (TemaActual.oscuro) 0.13f else 0.35f) * f),
                        0.45f to Color.Transparent,
                        1f to Color.White.copy(alpha = (if (TemaActual.oscuro) 0.05f else 0.12f) * f),
                    ),
                )
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
    if (Aspecto.superficie == Superficie.LIQUIDO) {
        // Borde de luz: más brillante arriba (donde «da» la luz) y casi nada abajo.
        drawRoundRect(
            Brush.linearGradient(
                0f to Color.White.copy(alpha = if (TemaActual.oscuro) 0.42f else 0.85f),
                0.32f to Color.White.copy(alpha = 0.08f),
                0.68f to Color.White.copy(alpha = 0.04f),
                1f to Color.White.copy(alpha = if (TemaActual.oscuro) 0.22f else 0.5f),
                start = Offset.Zero, end = Offset(size.width, size.height),
            ),
            topLeft = Offset(m, m), size = Size(size.width - ancho, size.height - ancho),
            cornerRadius = CornerRadius((radioEsquina - m).coerceAtLeast(0f)), style = Stroke(ancho * 1.25f), alpha = alpha,
        )
        return
    }
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
