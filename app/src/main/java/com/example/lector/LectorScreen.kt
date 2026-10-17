package com.example.lector

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

// Zoom máximo con pellizco y zoom que da el doble toque.
private const val ZOOM_MAXIMO = 5f
private const val ZOOM_DOBLE_TOQUE = 2.5f

@Composable
fun LectorScreen(comic: Comic, almacen: Almacen, alCerrar: () -> Unit) {
    val archivo = remember(comic.id) { almacen.archivoDe(comic.id) }
    var paginas by remember(comic.id) { mutableStateOf<List<String>?>(null) }
    var rtl by remember { mutableStateOf(almacen.derechaAIzquierda) }

    LaunchedEffect(comic.id) {
        paginas = withContext(Dispatchers.IO) { Cbz.paginas(archivo) }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        val lista = paginas
        if (lista == null) {
            CircularProgressIndicator(Modifier.align(Alignment.Center))
        } else {
            val estado = rememberPagerState(
                initialPage = comic.ultimaPagina.coerceIn(0, lista.size - 1),
            ) { lista.size }

            LaunchedEffect(estado.currentPage) {
                almacen.guardarProgreso(comic.id, estado.currentPage)
            }

            HorizontalPager(
                state = estado,
                reverseLayout = rtl,
                modifier = Modifier.fillMaxSize(),
            ) { indice ->
                Pagina(archivo, lista[indice])
            }

            Row(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color(0xCC000000))
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "${estado.currentPage + 1} / ${lista.size}",
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.onBackground,
                )
                TextButton(onClick = {
                    rtl = !rtl
                    almacen.derechaAIzquierda = rtl
                }) { Text(if (rtl) "Manga ←" else "Cómic →") }
                TextButton(onClick = alCerrar) { Text("Cerrar") }
            }
        }
    }
}

@Composable
private fun Pagina(archivo: File, nombre: String) {
    var imagen by remember(nombre) { mutableStateOf<ImageBitmap?>(null) }
    var escala by remember(nombre) { mutableFloatStateOf(1f) }
    var desplazamiento by remember(nombre) { mutableStateOf(Offset.Zero) }

    LaunchedEffect(nombre) {
        // 3000 px: da margen para ampliar sin que se vea borroso.
        imagen = withContext(Dispatchers.IO) {
            Cbz.decodificar(archivo, nombre, 3000)?.asImageBitmap()
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            // Doble toque: acerca hasta el punto tocado o vuelve al tamaño normal.
            .pointerInput(nombre) {
                detectTapGestures(onDoubleTap = { toque ->
                    if (escala > 1f) {
                        escala = 1f
                        desplazamiento = Offset.Zero
                    } else {
                        val centro = Offset(size.width / 2f, size.height / 2f)
                        escala = ZOOM_DOBLE_TOQUE
                        desplazamiento = limitar(
                            (centro - toque) * (escala - 1f),
                            escala,
                            size,
                        )
                    }
                })
            }
            // Pellizco y arrastre. Solo "consume" los gestos con dos dedos o con
            // zoom activo; así, con un dedo y sin zoom, el pager sigue pasando de página.
            .pointerInput(nombre) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val evento = awaitPointerEvent()
                        val dedos = evento.changes.count { it.pressed }
                        if (dedos >= 2 || escala > 1f) {
                            val zoom = evento.calculateZoom()
                            val arrastre = evento.calculatePan()
                            val centro = Offset(size.width / 2f, size.height / 2f)
                            val foco = evento.calculateCentroid() - centro
                            val nueva = (escala * zoom).coerceIn(1f, ZOOM_MAXIMO)
                            val z = nueva / escala
                            desplazamiento = limitar(
                                desplazamiento * z + foco * (1f - z) + arrastre,
                                nueva,
                                size,
                            )
                            escala = nueva
                            evento.changes.forEach {
                                if (it.positionChanged()) it.consume()
                            }
                        }
                    } while (evento.changes.any { it.pressed })
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        val img = imagen
        if (img == null) {
            CircularProgressIndicator()
        } else {
            Image(
                bitmap = img,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = escala
                        scaleY = escala
                        translationX = desplazamiento.x
                        translationY = desplazamiento.y
                    },
            )
        }
    }
}

// Evita que la imagen se arrastre más allá de sus bordes al ampliarla.
private fun limitar(desplazamiento: Offset, escala: Float, tamano: IntSize): Offset {
    val maxX = tamano.width * (escala - 1f) / 2f
    val maxY = tamano.height * (escala - 1f) / 2f
    return Offset(
        desplazamiento.x.coerceIn(-maxX, maxX),
        desplazamiento.y.coerceIn(-maxY, maxY),
    )
}
