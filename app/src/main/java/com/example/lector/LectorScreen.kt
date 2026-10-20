package com.example.lector

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.example.lector.ui.AccionesVisor
import com.example.lector.ui.Avisos
import com.example.lector.ui.CapVisor
import com.example.lector.ui.OrigenArchivo
import com.example.lector.ui.Visor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Lector de un cómic local (.cbz o carpeta importada). Usa el mismo visor que la lectura online: modos,
 * zoom, zonas de toque, filtros, autodesplazamiento… Si el cómic no tiene modo propio y parece un webtoon
 * (páginas muy altas), se abre en tira vertical.
 */
@Composable
fun LectorScreen(comic: Comic, almacen: Almacen, alCerrar: () -> Unit) {
    val scope = rememberCoroutineScope()
    val archivo = remember(comic.id) { almacen.archivoDe(comic.id) }
    val caps = remember(comic.id) { listOf(CapVisor(comic.id, comic.titulo, OrigenArchivo(archivo))) }
    val propio = remember(comic.id) { almacen.modoVisorDe(comic.id) }
    var sugerido by remember(comic.id) { mutableStateOf<ModoVisor?>(null) }
    var listo by remember(comic.id) { mutableStateOf(propio != null) }

    LaunchedEffect(comic.id) {
        if (!listo) {
            val web = withContext(Dispatchers.IO) {
                runCatching { Cbz.esWebtoon(archivo, Cbz.paginas(archivo)) }.getOrDefault(false)
            }
            if (web) sugerido = ModoVisor.WEBTOON
            listo = true
        }
    }
    if (!listo) {
        Box(Modifier.fillMaxSize().background(Color.Black), Alignment.Center) { CircularProgressIndicator() }
        return
    }

    Visor(
        almacen = almacen,
        titulo = comic.titulo,
        capitulos = caps,
        inicial = 0,
        paginaInicial = comic.ultimaPagina,
        claveSerie = comic.id,
        modoSugerido = sugerido,
        alPagina = { _, pagina, _ -> almacen.guardarProgreso(comic.id, pagina) },
        alTerminarCapitulo = {
            // Al llegar a la última página, sube el progreso a AniList (si está vinculado). Sin conexión, queda pendiente.
            if (almacen.seguimientoDe(comic.id) != null) {
                almacen.marcarSincronizacion(comic.id, true)
                scope.launch {
                    val subido = withContext(Dispatchers.IO) { almacen.sincronizarAnilist(comic.id) }
                    Avisos.mostrar(if (subido) "AniList actualizado" else "AniList: se reintentará más tarde")
                }
            }
        },
        acciones = AccionesVisor(
            usarComoPortada = { bytes -> withContext(Dispatchers.IO) { almacen.cambiarPortada(comic.id, bytes) } },
        ),
        onBack = alCerrar,
    )
}
