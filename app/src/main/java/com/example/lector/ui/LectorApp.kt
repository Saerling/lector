package com.example.lector.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.lector.Almacen
import com.example.lector.BibliotecaScreen
import com.example.lector.Comic

/**
 * Pantalla principal con el diseño nuevo. Mantiene tu BibliotecaScreen como pestaña "Biblioteca"
 * (con su propio menú ⋮: orden, categorías, historial, apariencia, copia de seguridad...).
 * La pestaña elegida se guarda fuera (en MainActivity) para no perderla al abrir/cerrar un cómic.
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
    // Los cómics de categorías ocultas no salen en Inicio, Buscar ni Perfil.
    // Solo se ven en Biblioteca, manteniendo pulsado su título.
    val visibles = almacen.sinOcultos(comics)
    val ultimo = visibles.firstOrNull { it.id == almacen.ultimoAbierto }

    Box(Modifier.fillMaxSize().background(LectorColors.Bg)) {
        Crossfade(tab, animationSpec = tween(250), label = "tabs") { t ->
            when (t) {
                LectorTab.Home -> HomeScreen(visibles, almacen, alAbrir, alAnadir)
                LectorTab.Search -> SearchScreen(visibles, almacen, alAbrir)
                LectorTab.Library -> Box(Modifier.padding(bottom = 76.dp)) {
                    BibliotecaScreen(
                        comics = comics,
                        almacen = almacen,
                        alAnadir = alAnadir,
                        alAnadirCarpeta = alAnadirCarpeta,
                        alAbrir = alAbrir,
                        alEliminar = alEliminar,
                        alRecargar = alRecargar,
                        pestana = pestanaBiblioteca,
                        alCambiarPestana = alCambiarPestanaBiblioteca,
                        conNavegacion = false,
                    )
                }
                LectorTab.Profile -> ProfileScreen(visibles, alAnadir, alAnadirCarpeta)
            }
        }
        Column(
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding()
                .padding(horizontal = 16.dp).padding(bottom = 8.dp)
        ) {
            if (ultimo != null && tab != LectorTab.Library) {
                MiniReader(ultimo, almacen, onClick = { alAbrir(ultimo) })
                Spacer(Modifier.height(8.dp))
            }
            FloatingNavBar(tab, onSelect = onTab)
        }
    }
}
