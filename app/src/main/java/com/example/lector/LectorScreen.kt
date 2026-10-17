package com.example.lector

import android.app.Activity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
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
    var modo by remember(comic.id) { mutableStateOf(Modo.MANGA) }
    var paginaActual by remember(comic.id) { mutableIntStateOf(comic.ultimaPagina) }
    var barraVisible by remember { mutableStateOf(true) }

    // Oculta o muestra las barras del sistema junto con nuestra barra inferior.
    PantallaCompleta(oculta = !barraVisible)

    LaunchedEffect(comic.id) {
        val lista = withContext(Dispatchers.IO) { Cbz.paginas(archivo) }
        // Prioridad: modo elegido a mano > webtoon detectado > último modo paginado.
        modo = almacen.modoDe(comic.id) ?: withContext(Dispatchers.IO) {
            if (Cbz.esWebtoon(archivo, lista)) Modo.VERTICAL else almacen.modoPorDefecto()
        }
        paginas = lista
    }

    // El color de acento puede tomarse de la portada del cómic.
    TemaPortada(almacen.portadaDe(comic.id), almacen.colorPortada) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            val lista = paginas
            if (lista == null) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            } else {
                val inicial = paginaActual.coerceIn(0, lista.size - 1)

                LaunchedEffect(paginaActual) {
                    almacen.guardarProgreso(comic.id, paginaActual)
                }

                val alTocar = { barraVisible = !barraVisible }

                if (modo == Modo.VERTICAL) {
                    LectorVertical(
                        archivo = archivo,
                        lista = lista,
                        inicial = inicial,
                        alCambiarPagina = { paginaActual = it },
                        alTocar = alTocar,
                    )
                } else {
                    LectorPaginado(
                        archivo = archivo,
                        lista = lista,
                        inicial = inicial,
                        rtl = modo == Modo.MANGA,
                        alCambiarPagina = { paginaActual = it },
                        alTocar = alTocar,
                    )
                }

                if (barraVisible) {
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
                            "${paginaActual + 1} / ${lista.size}",
                            modifier = Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        TextButton(onClick = {
                            val nuevo = when (modo) {
                                Modo.MANGA -> Modo.COMIC
                                Modo.COMIC -> Modo.VERTICAL
                                Modo.VERTICAL -> Modo.MANGA
                            }
                            modo = nuevo
                            almacen.guardarModo(comic.id, nuevo)
                            if (nuevo != Modo.VERTICAL) {
                                almacen.derechaAIzquierda = nuevo == Modo.MANGA
                            }
                        }) {
                            Text(
                                when (modo) {
                                    Modo.MANGA -> "Manga ←"
                                    Modo.COMIC -> "Cómic →"
                                    Modo.VERTICAL -> "Vertical ↓"
                                },
                            )
                        }
                        TextButton(onClick = alCerrar) { Text("Cerrar") }
                    }
                }
            }
        }
    }
}

// Modo paginado: una página por pantalla, con zoom.
@Composable
private fun LectorPaginado(
    archivo: File,
    lista: List<String>,
    inicial: Int,
    rtl: Boolean,
    alCambiarPagina: (Int) -> Unit,
    alTocar: () -> Unit,
) {
    val estado = rememberPagerState(initialPage = inicial) { lista.size }

    LaunchedEffect(estado.currentPage) {
        alCambiarPagina(estado.currentPage)
    }

    HorizontalPager(
        state = estado,
        reverseLayout = rtl,
        modifier = Modifier.fillMaxSize(),
    ) { indice ->
        Pagina(archivo, lista[indice], alTocar)
    }
}

// Modo vertical (webtoon): todas las páginas una debajo de otra, sin huecos.
@Composable
private fun LectorVertical(
    archivo: File,
    lista: List<String>,
    inicial: Int,
    alCambiarPagina: (Int) -> Unit,
    alTocar: () -> Unit,
) {
    val estado = rememberLazyListState(initialFirstVisibleItemIndex = inicial)

    LaunchedEffect(estado) {
        snapshotFlow { estado.firstVisibleItemIndex }.collect { alCambiarPagina(it) }
    }

    LazyColumn(
        state = estado,
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(onTap = { alTocar() })
            },
    ) {
        items(lista.size, key = { it }) { indice ->
            PaginaVertical(archivo, lista[indice])
        }
    }
}

@Composable
private fun PaginaVertical(archivo: File, nombre: String) {
    var tiras by remember(nombre) { mutableStateOf<List<ImageBitmap>?>(null) }

    LaunchedEffect(nombre) {
        tiras = withContext(Dispatchers.IO) {
            Cbz.decodificarTiras(archivo, nombre, 1440, 2048).map { it.asImageBitmap() }
        }
    }

    val lista = tiras
    if (lista == null) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(400.dp),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator()
        }
    } else {
        Column {
            lista.forEach { tira ->
                Image(
                    bitmap = tira,
                    contentDescription = null,
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun Pagina(archivo: File, nombre: String, alTocar: () -> Unit) {
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
            // Un toque: muestra u oculta la barra. Doble toque: acerca hasta el punto
            // tocado o vuelve al tamaño normal.
            .pointerInput(nombre) {
                detectTapGestures(
                    onTap = { alTocar() },
                    onDoubleTap = { toque ->
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
                    },
                )
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

// Oculta las barras del sistema (estado y navegación) mientras `oculta` sea true.
// Al salir del lector, las vuelve a mostrar.
@Composable
private fun PantallaCompleta(oculta: Boolean) {
    val vista = LocalView.current
    DisposableEffect(oculta) {
        val ventana = (vista.context as? Activity)?.window
        val control: WindowInsetsControllerCompat? =
            if (ventana != null) WindowCompat.getInsetsController(ventana, vista) else null
        if (oculta) {
            control?.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            control?.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            control?.show(WindowInsetsCompat.Type.systemBars())
        }
        onDispose {
            control?.show(WindowInsetsCompat.Type.systemBars())
        }
    }
}
