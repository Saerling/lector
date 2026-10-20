package com.example.lector.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.lector.Almacen
import com.example.lector.ext.ExtManager
import com.example.lector.ext.FuenteUI
import com.example.lector.ext.MangaFuente
import com.example.lector.ext.Motor
import com.example.lector.ext.bandera
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

private sealed interface EstadoBusqueda {
    object Cargando : EstadoBusqueda
    data class Hecho(val mangas: List<MangaFuente>) : EstadoBusqueda
    data class Fallo(val motivo: String) : EstadoBusqueda
}

/**
 * Búsqueda global (como la de Komikku): busca el mismo texto en todas tus fuentes a la vez (o solo en las ancladas)
 * y enseña una fila de resultados por fuente. Mantener pulsada una portada la añade a la biblioteca.
 */
@Composable
fun BusquedaGlobalScreen(
    ext: ExtManager,
    almacen: Almacen,
    consultaInicial: String,
    onManga: (FuenteUI, MangaFuente) -> Unit,
    onBack: () -> Unit,
) {
    val ctx = LocalContext.current
    val motor = Motor.actual
    var consulta by rememberSaveable { mutableStateOf(consultaInicial) }
    var enviada by rememberSaveable { mutableStateOf(consultaInicial) }
    var soloConResultados by remember { mutableStateOf(false) }
    val resultados = remember { mutableStateMapOf<Long, EstadoBusqueda>() }
    val fuentes = remember(ext.fuentes, almacen.globalSoloAncladas, ext.idiomas, ext.mostrarNsfw) {
        ext.fuentes.filter {
            (ext.mostrarNsfw || !it.nsfw) && it.lang in ext.idiomas &&
                (!almacen.globalSoloAncladas || it.id.toString() in ext.fijadas)
        }.sortedWith(compareBy({ it.id.toString() !in ext.fijadas }, { it.nombre.lowercase() }))
    }
    val enBiblioteca = remember(almacen.version) { almacen.clavesEnLinea() }

    LaunchedEffect(enviada, fuentes) {
        resultados.clear()
        if (enviada.isBlank() || motor == null) return@LaunchedEffect
        val limite = Semaphore(5)
        fuentes.forEach { f -> resultados[f.id] = EstadoBusqueda.Cargando }
        fuentes.forEach { f ->
            launch {
                limite.withPermit {
                    resultados[f.id] = try {
                        EstadoBusqueda.Hecho(motor.buscar(f, enviada.trim(), 1).mangas)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        EstadoBusqueda.Fallo(e.message ?: "Error")
                    }
                }
            }
        }
    }

    Column(Modifier.fillMaxSize().background(LectorColors.Bg).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            BotonRedondoTema(Icons.Rounded.ArrowBack, "Atrás", onBack)
            Spacer(Modifier.width(12.dp))
            Box(Modifier.weight(1f)) { CampoBusqueda(consulta, { consulta = it }, "Buscar en todas las fuentes") }
            Spacer(Modifier.width(8.dp))
            BotonRedondoTema(Icons.Rounded.Search, "Buscar", { enviada = consulta.trim() })
        }
        Row(Modifier.padding(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChipSel("Con resultados", soloConResultados) { soloConResultados = !soloConResultados }
            ChipSel(if (almacen.globalSoloAncladas) "Solo ancladas" else "Todas las fuentes", almacen.globalSoloAncladas) {
                almacen.globalSoloAncladas = !almacen.globalSoloAncladas
            }
        }
        val terminadas = resultados.values.count { it !is EstadoBusqueda.Cargando }
        if (enviada.isNotBlank() && terminadas < fuentes.size) {
            LText("Buscando… $terminadas de ${fuentes.size}", LectorType.Label, Modifier.padding(horizontal = 24.dp, vertical = 6.dp), color = LectorColors.Muted)
        }
        when {
            motor == null -> MensajeVacio("El motor de fuentes no está disponible.")
            fuentes.isEmpty() -> MensajeVacio("No hay fuentes. Instala una extensión y pulsa «Confiar» (o desactiva «Solo ancladas»).")
            enviada.isBlank() -> MensajeVacio("Escribe un título y pulsa buscar.")
            else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 40.dp)) {
                items(fuentes, key = { it.id }) { f ->
                    val e = resultados[f.id] ?: EstadoBusqueda.Cargando
                    if (soloConResultados && (e !is EstadoBusqueda.Hecho || e.mangas.isEmpty())) return@items
                    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        Row(Modifier.padding(horizontal = 24.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            LText(f.nombre, LectorType.Section.copy(fontSize = 17.sp), Modifier.weight(1f), maxLines = 1)
                            LText(bandera(f.lang), LectorType.Body)
                        }
                        when (e) {
                            EstadoBusqueda.Cargando -> Box(Modifier.fillMaxWidth().height(60.dp), Alignment.Center) { CircularProgressIndicator(Modifier.size(22.dp)) }
                            is EstadoBusqueda.Fallo -> LText(e.motivo, LectorType.Label, Modifier.padding(horizontal = 24.dp), color = LectorColors.Error, maxLines = 2)
                            is EstadoBusqueda.Hecho -> if (e.mangas.isEmpty()) {
                                LText("Sin resultados", LectorType.Label, Modifier.padding(horizontal = 24.dp), color = LectorColors.Muted)
                            } else LazyRow(contentPadding = PaddingValues(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                items(e.mangas, key = { it.url }) { m ->
                                    val esta = "${f.id}|${m.url}" in enBiblioteca
                                    Column(
                                        Modifier.width(112.dp).press3D().pressable(onLongClick = {
                                            alternarEnBiblioteca(ctx, almacen, f, m)
                                        }) { onManga(f, m) },
                                    ) {
                                        Box {
                                            ImagenRemota(m.portadaUrl, Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(SuaveShape(12.dp)), motor.cabeceras(f))
                                            if (esta) EnBiblioteca(Modifier.align(Alignment.TopStart).padding(6.dp))
                                        }
                                        Spacer(Modifier.height(6.dp))
                                        LText(m.titulo, LectorType.Label, maxLines = 2)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Insignia «En biblioteca» sobre una portada de Explorar. */
@Composable
fun EnBiblioteca(modifier: Modifier = Modifier) {
    Box(
        modifier.clip(SuaveShape(8.dp)).background(androidx.compose.material3.MaterialTheme.colorScheme.primary)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        LText("En biblioteca", LectorType.Label.copy(fontSize = 10.sp), color = com.example.lector.textoSobre(androidx.compose.material3.MaterialTheme.colorScheme.primary), maxLines = 1)
    }
}

/** Mantener pulsada una portada en Explorar: la añade a la biblioteca (o la quita si ya estaba), como en Komikku. */
fun alternarEnBiblioteca(ctx: android.content.Context, almacen: Almacen, f: FuenteUI, m: MangaFuente) {
    val ya = almacen.comicEnLinea(f.id, m.url)
    if (ya != null) {
        almacen.eliminar(ya.id)
        Avisos.mostrar("Quitado de la biblioteca")
    } else {
        almacen.anadirEnLinea(f.id, m.url, m.titulo, m.portadaUrl, null, 0, 0)
        Avisos.mostrar("Añadido a la biblioteca")
    }
}
