package com.example.lector.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * «Polvo»: un elemento que se quita se deshace en partículas que se lleva el viento (de izquierda a derecha), y uno
 * que se añade se rearma desde ellas. Se captura la imagen del elemento (GraphicsLayer) y se dibuja troceada en una
 * capa encima de toda la app, así el elemento real puede desaparecer al instante. Solo trabaja mientras hay efecto.
 */
object Polvo {
    internal class Registro(val capa: GraphicsLayer, var rect: Rect)
    internal class Efecto(val img: ImageBitmap, val rect: Rect, val inverso: Boolean, val semilla: Int, val inicio: Long)

    internal val registros = HashMap<String, Registro>()
    internal val efectos = mutableStateListOf<Efecto>()
    internal val ocultos = mutableStateMapOf<String, Boolean>()
    const val DURACION = 1100L

    private suspend fun capturar(id: String): Pair<ImageBitmap, Rect>? {
        val r = registros[id] ?: return null
        if (r.rect.width < 2f || r.rect.height < 2f) return null
        return runCatching { r.capa.toImageBitmap() }.getOrNull()?.let { it to r.rect }
    }

    /** Deshace el elemento [id] (antes de quitarlo de la lista). */
    suspend fun desintegrar(id: String) {
        if (MovimientoReducido.activo) return
        val (img, rect) = capturar(id) ?: return
        Tacto.tic()
        if (efectos.size >= 12) return // muchos a la vez (quitar en bloque): basta con los primeros
        efectos += Efecto(img, rect, false, id.hashCode(), System.currentTimeMillis())
    }

    /** Rearma el elemento [id]: se oculta, las partículas llegan y vuelve a verse. */
    suspend fun reconstruir(id: String) {
        if (MovimientoReducido.activo) return
        val (img, rect) = capturar(id) ?: return
        ocultos[id] = true
        try {
            efectos += Efecto(img, rect, true, id.hashCode(), System.currentTimeMillis())
            kotlinx.coroutines.delay(DURACION)
        } finally { ocultos.remove(id) } // aunque se salga antes de tiempo, el elemento vuelve a verse
    }
}

/** Marca un elemento para poder deshacerlo o rearmarlo con [Polvo]. */
@Composable
fun Modifier.polvo(id: String): Modifier {
    val capa = rememberGraphicsLayer()
    DisposableEffect(id) { onDispose { if (Polvo.registros[id]?.capa === capa) Polvo.registros.remove(id) } }
    return this
        .graphicsLayer { alpha = if (Polvo.ocultos.containsKey(id)) 0f else 1f }
        .onGloballyPositioned { c ->
            val r = Polvo.registros[id]
            if (r != null && r.capa === capa) r.rect = c.boundsInRoot() else Polvo.registros[id] = Polvo.Registro(capa, c.boundsInRoot())
        }
        .drawWithContent {
            capa.record { this@drawWithContent.drawContent() }
            drawLayer(capa)
        }
}

/** Capa de partículas: va encima de toda la app (en LectorApp). */
@Composable
fun CapaPolvo() {
    if (Polvo.efectos.isEmpty()) return
    var ahora by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (Polvo.efectos.isNotEmpty()) {
            withFrameMillis { }
            ahora = System.currentTimeMillis()
            Polvo.efectos.removeAll { ahora - it.inicio > Polvo.DURACION }
        }
    }
    Canvas(Modifier.fillMaxSize()) {
        val viento = 90.dp.toPx()
        val subida = 70.dp.toPx()
        for (e in Polvo.efectos) {
            if (ahora - e.inicio > Polvo.DURACION) continue
            val img = e.img
            val p0 = ((ahora - e.inicio).toFloat() / Polvo.DURACION).coerceIn(0f, 1f)
            val p = if (e.inverso) 1f - p0 else p0
            // Trozos de ~1/34 del ancho (mín. 6 px): bastante finos para parecer polvo, pocos para ir fluido.
            val lado = maxOf(6, img.width / 34, kotlin.math.sqrt(img.width * img.height / 1400f).toInt() + 1) // ≤ ~1400 trozos
            val cols = (img.width + lado - 1) / lado
            val filas = (img.height + lado - 1) / lado
            val ex = e.rect.width / img.width
            val ey = e.rect.height / img.height
            for (fy in 0 until filas) for (fx in 0 until cols) {
                val h = ((fx * 73856093) xor (fy * 19349663) xor e.semilla) and 0x7fffffff
                val a1 = (h % 1000) / 1000f
                val a2 = ((h / 1000) % 1000) / 1000f
                // Barrido: empiezan los de la izquierda; cada trozo vuela en su propio tiempo.
                val retraso = fx.toFloat() / cols * 0.45f + a1 * 0.12f
                val t = ((p - retraso) / 0.5f).coerceIn(0f, 1f)
                if (t >= 1f) continue
                val suave = t * t * (3f - 2f * t)
                val x = fx * lado
                val y = fy * lado
                val w = minOf(lado, img.width - x)
                val hh = minOf(lado, img.height - y)
                val dx = suave * (viento * (0.5f + a2)) + (a1 - 0.5f) * 24f * suave
                val dy = -suave * subida * (0.3f + a1)
                val esc = 1f - 0.6f * suave
                drawImage(
                    img,
                    srcOffset = IntOffset(x, y), srcSize = IntSize(w, hh),
                    dstOffset = IntOffset((e.rect.left + x * ex + dx).roundToInt(), (e.rect.top + y * ey + dy).roundToInt()),
                    dstSize = IntSize((w * ex * esc).roundToInt().coerceAtLeast(1), (hh * ey * esc).roundToInt().coerceAtLeast(1)),
                    alpha = (1f - suave).coerceIn(0f, 1f),
                )
            }
        }
    }
}
