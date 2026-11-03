// Copiado de Mochy Music (github.com/realidkroo/Mochy-Music, GPL-3.0): app/src/main/kotlin/com/mochyy/mochymusic/ui/component/HomeTopGlow.kt
// (Shiori: las portadas salen de Portadas/Almacen en vez de Coil, y el tono se saca sin androidx.palette:
// el mayor grupo de color real de una copia de 24 px, igual de apagado que en Mochy)

package com.example.lector.ui.mochy.home

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.util.LruCache
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.core.graphics.ColorUtils
import com.example.lector.Almacen
import com.example.lector.Portadas
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private const val GlowPeakAlpha = 0.9f
// how far each corner's glow reaches sideways, so the two meet and blend across the middle
private const val GlowReachFraction = 0.95f
private const val GlowStopCount = 12
private val GlowStopPositions = FloatArray(GlowStopCount + 1) { it / GlowStopCount.toFloat() }

// the soft wash at the top of home: the first cover tints the left corner, the second the right
@Composable
fun HomeTopGlow(
    coverIds: List<String>,
    almacen: Almacen,
    depth: Dp,
    visibility: () -> Float,
    modifier: Modifier = Modifier,
) {
    // seeded from the cache so coming back to home shows the glow straight away instead of fading it in again
    val colors = remember { mutableStateOf(cachedGlowPair(coverIds)) }
    LaunchedEffect(coverIds) { colors.value = loadGlowPair(almacen, coverIds) }

    // starts from clear, so the wash fades in once the covers are sampled and blends over when they change
    val left = animateColorAsState(colors.value?.first ?: Color.Transparent, tween(700), label = "homeGlowLeft")
    val right = animateColorAsState(colors.value?.second ?: Color.Transparent, tween(700), label = "homeGlowRight")
    val leftPaint = remember { Paint().apply { isDither = true } }
    val rightPaint = remember { Paint().apply { isDither = true } }

    Spacer(
        modifier = modifier
            // the scroll fade only moves this layer's alpha, so scrolling never redraws the wash
            .graphicsLayer {
                alpha = visibility().coerceIn(0f, 1f)
                compositingStrategy = CompositingStrategy.ModulateAlpha
            }
            .drawWithCache {
                val leftColor = left.value
                val rightColor = right.value
                val width = size.width
                val height = depth.toPx()
                if (width <= 0f || height <= 0f || (leftColor.alpha == 0f && rightColor.alpha == 0f)) {
                    return@drawWithCache onDrawBehind {}
                }
                leftPaint.shader = cornerGlow(leftColor, width * 0.08f, width, height)
                rightPaint.shader = cornerGlow(rightColor, width * 0.92f, width, height)
                // drawn past this spacer's own bottom when the section under it is short, so the fade never gets cut off
                onDrawBehind {
                    drawIntoCanvas { canvas ->
                        canvas.nativeCanvas.drawRect(0f, 0f, width, height, leftPaint)
                        canvas.nativeCanvas.drawRect(0f, 0f, width, height, rightPaint)
                    }
                }
            }
    )
}

// an oval glow hanging from the top edge, eased out to nothing by the bottom so there's no line where it ends
private fun cornerGlow(color: Color, centerX: Float, width: Float, height: Float): Shader {
    val rgb = color.copy(alpha = 1f).toArgb()
    val stops = IntArray(GlowStopCount + 1) { i ->
        val remaining = 1f - GlowStopPositions[i]
        val falloff = remaining * remaining * (3f - 2f * remaining)
        ColorUtils.setAlphaComponent(rgb, (color.alpha * GlowPeakAlpha * falloff * 255f).roundToInt())
    }
    return RadialGradient(centerX, 0f, height, stops, GlowStopPositions, Shader.TileMode.CLAMP).apply {
        setLocalMatrix(Matrix().apply { setScale(width * GlowReachFraction / height, 1f, centerX, 0f) })
    }
}

private fun glowPair(left: Color?, right: Color?): Pair<Color, Color>? = when {
    left != null -> left to (right ?: left)
    right != null -> right to right
    else -> null
}

private fun cachedGlowPair(ids: List<String>): Pair<Color, Color>? =
    glowPair(ids.getOrNull(0)?.let(HomeGlowColor::cached), ids.getOrNull(1)?.let(HomeGlowColor::cached))

private suspend fun loadGlowPair(almacen: Almacen, ids: List<String>): Pair<Color, Color>? =
    glowPair(ids.getOrNull(0)?.let { HomeGlowColor.load(almacen, it) }, ids.getOrNull(1)?.let { HomeGlowColor.load(almacen, it) })

// one dim colour per cover, sampled off the main thread from a tiny copy and kept so a cover is never sampled twice
private object HomeGlowColor {
    private val cache = LruCache<String, Int>(48)

    fun cached(id: String): Color? = cache.get(id)?.let { Color(it) }

    suspend fun load(almacen: Almacen, id: String): Color? {
        cached(id)?.let { return it }
        return try {
            val image = Portadas.enMemoria(id) ?: Portadas.cargar(almacen.portadaDe(id), id) ?: return null
            val argb = withContext(Dispatchers.Default) { glowTone(image.asAndroidBitmap()) } ?: return null
            cache.put(id, argb)
            Color(argb)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    // the biggest patch of real colour on the cover (its main grey if it has none), kept dim so white text stays easy to read on it
    private fun glowTone(source: Bitmap): Int? {
        val small = Bitmap.createScaledBitmap(source, 24, 24, true)
        val pixels = IntArray(24 * 24)
        small.getPixels(pixels, 0, 24, 0, 0, 24, 24)
        if (small !== source) small.recycle()
        // Shiori: 12 grupos de tono en lugar de las muestras de Palette
        val hsl = FloatArray(3)
        val count = IntArray(12)
        val sums = Array(12) { FloatArray(3) }
        var all = 0
        val allSum = FloatArray(3)
        for (p in pixels) {
            ColorUtils.colorToHSL(p, hsl)
            all++
            allSum[0] += hsl[0]; allSum[1] += hsl[1]; allSum[2] += hsl[2]
            if (hsl[1] >= 0.2f && hsl[2] in 0.08f..0.9f) {
                val b = ((hsl[0] / 30f).toInt()).coerceIn(0, 11)
                count[b]++
                sums[b][0] += hsl[0]; sums[b][1] += hsl[1]; sums[b][2] += hsl[2]
            }
        }
        if (all == 0) return null
        val best = count.indices.maxByOrNull { count[it] } ?: return null
        val pick = if (count[best] >= all / 6) {
            floatArrayOf(sums[best][0] / count[best], sums[best][1] / count[best], sums[best][2] / count[best])
        } else {
            floatArrayOf(allSum[0] / all, allSum[1] / all, allSum[2] / all)
        }
        pick[1] = pick[1].coerceAtMost(0.6f)
        pick[2] = pick[2].coerceIn(0.2f, 0.32f)
        return ColorUtils.HSLToColor(pick)
    }
}
