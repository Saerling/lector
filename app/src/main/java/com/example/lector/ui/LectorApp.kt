package com.example.lector.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.lector.Almacen
import com.example.lector.BibliotecaScreen
import com.example.lector.Comic
import com.example.lector.HistorialScreen

/**
 * Pantalla principal con el diseño nuevo.
 *  - Pestañas: Inicio, Buscar, Biblioteca, Historial, Más.
 *  - Encima de las pestañas se abren (con animación) la página de un cómic y las páginas de Más/Ajustes.
 * Tu BibliotecaScreen e HistorialScreen siguen intactos.
 */
@Composable
fun Inicio(
    tab: LectorTab,
    onTab: (LectorTab) -> Unit,
    comics: List<Comic>,
    almacen: Almacen,
    alAnadir: () -> Unit,
    alAnadirCarpeta: () -> Unit,
    alAbrir: (Comic) -> Unit,
    alEliminar: (Comic) -> Unit,
    alRecargar: () -> Unit,
    pestanaBiblioteca: Int,
    alCambiarPestanaBiblioteca: (Int) -> Unit,
) {
    // Los cómics de categorías ocultas no salen en Inicio, Buscar ni Historial.
    // Solo se ven en Biblioteca, manteniendo pulsado su título.
    val visibles = almacen.sinOcultos(comics)
    val ultimo = visibles.firstOrNull { it.id == almacen.ultimoAbierto }

    var ruta by rememberSaveable { mutableStateOf<Ruta?>(null) }
    var detalleId by rememberSaveable { mutableStateOf<String?>(null) }
    val hayCapa = ruta != null || detalleId != null

    BackHandler(enabled = ruta != null && detalleId == null) { ruta = ruta?.padre() }
    BackHandler(enabled = detalleId != null) { detalleId = null }

    val abrirDetalle: (Comic) -> Unit = { detalleId = it.id }
    // Clave de la capa que está encima ("" = ninguna). Se usa como estado de la animación.
    val capa = when {
        detalleId != null -> "d:$detalleId"
        ruta != null -> ruta!!.name
        else -> ""
    }

    Box(Modifier.fillMaxSize().background(LectorColors.Bg)) {
        Crossfade(tab, animationSpec = tween(250), label = "tabs") { t ->
            when (t) {
                LectorTab.Home -> HomeScreen(visibles, almacen, onOpen = abrirDetalle, onRead = alAbrir, onAdd = alAnadir)
                LectorTab.Search -> SearchScreen(visibles, almacen, abrirDetalle)
                LectorTab.Library -> Box(Modifier.padding(bottom = 76.dp)) {
                    BibliotecaScreen(
                        comics = comics,
                        almacen = almacen,
                        alAnadir = alAnadir,
                        alAnadirCarpeta = alAnadirCarpeta,
                        alAbrir = abrirDetalle,
                        alEliminar = alEliminar,
                        alRecargar = alRecargar,
                        pestana = pestanaBiblioteca,
                        alCambiarPestana = alCambiarPestanaBiblioteca,
                        conNavegacion = false,
                    )
                }
                LectorTab.History -> Box(Modifier.statusBarsPadding().padding(bottom = 76.dp)) {
                    HistorialScreen(
                        comics = visibles, almacen = almacen,
                        alAbrir = alAbrir, alCambiar = alRecargar,
                    )
                }
                LectorTab.More -> MasScreen(
                    onRuta = { ruta = it },
                    onAdd = alAnadir,
                    onAddFolder = alAnadirCarpeta,
                )
            }
        }

        // Páginas que se deslizan encima (detalle de cómic, Más → Categorías, Ajustes, etc.).
        AnimatedContent(
            targetState = capa, label = "capa",
            transitionSpec = {
                ContentTransform(
                    slideInHorizontally(tween(320)) { it / 4 } + fadeIn(tween(320)),
                    slideOutHorizontally(tween(260)) { -it / 5 } + fadeOut(tween(220)),
                )
            }
        ) { clave ->
            when {
                clave.isEmpty() -> Box(Modifier.fillMaxSize())
                clave.startsWith("d:") -> {
                    val c = comics.firstOrNull { it.id == clave.drop(2) }
                    if (c != null) {
                        DetalleScreen(
                            comic = c, almacen = almacen,
                            onBack = { detalleId = null },
                            onRead = { alAbrir(c) },
                            onChanged = alRecargar,
                        )
                    } else {
                        Box(Modifier.fillMaxSize().background(LectorColors.Bg))
                    }
                }
                else -> when (Ruta.valueOf(clave)) {
                    Ruta.Categorias -> CategoriasScreen(almacen) { ruta = null }
                    Ruta.Estadisticas -> EstadisticasScreen(visibles) { ruta = null }
                    Ruta.Datos -> DatosScreen(almacen, alRecargar) { ruta = null }
                    Ruta.Ajustes -> AjustesScreen(almacen, { ruta = null }) { ruta = it }
                    Ruta.Apariencia -> AparienciaScreen(almacen) { ruta = Ruta.Ajustes }
                    Ruta.AjBiblioteca -> AjBibliotecaScreen(almacen, { ruta = Ruta.Ajustes }) { ruta = it }
                    Ruta.AjSeguimiento -> AjSeguimientoScreen(almacen) { ruta = Ruta.Ajustes }
                }
            }
        }

        // Mini lector + barra flotante: se esconden hacia abajo cuando hay una página encima.
        AnimatedVisibility(
            visible = !hayCapa,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(tween(300)) { it } + fadeIn(tween(300)),
            exit = slideOutVertically(tween(250)) { it } + fadeOut(tween(200)),
        ) {
            Column(
                Modifier.navigationBarsPadding().padding(horizontal = 16.dp).padding(bottom = 8.dp)
            ) {
                if (ultimo != null && tab != LectorTab.Library) {
                    MiniReader(ultimo, almacen, onClick = { alAbrir(ultimo) })
                    Spacer(Modifier.height(8.dp))
                }
                FloatingNavBar(tab, onSelect = onTab)
            }
        }
    }
}
