package com.example.lector.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.lector.Almacen
import com.example.lector.Comic
import com.example.lector.ext.ExtManager
import com.example.lector.ext.Fase
import com.example.lector.ext.FuenteUI
import com.example.lector.ext.MangaFuente
import kotlinx.coroutines.launch

/**
 * Lo que se ve al abrir un manga cuya fuente ya no está (extensión desinstalada, sin confianza o que no carga):
 * dice por qué falta y cómo arreglarlo, y busca el mismo título en todas tus otras fuentes, con una fila de
 * resultados por fuente (como en Novedades). Si el manga está en tu biblioteca, se puede migrar al que encuentres
 * conservando categorías y capítulos leídos; si no, se abre sin más.
 */
@Composable
fun FuenteNoDisponible(
    ext: ExtManager,
    almacen: Almacen,
    fuenteId: Long,
    manga: MangaFuente,
    enBiblioteca: Comic?,
    alRecargar: () -> Unit,
    onBack: () -> Unit,
    onAbrir: (FuenteUI, MangaFuente) -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val titulo = enBiblioteca?.titulo ?: manga.titulo
    var consulta by rememberSaveable(manga.url) { mutableStateOf(titulo) }
    var enviada by rememberSaveable(manga.url) { mutableStateOf(titulo) }
    var soloConResultados by rememberSaveable { mutableStateOf(true) }
    var elegido by remember { mutableStateOf<Pair<FuenteUI, MangaFuente>?>(null) }
    var migrando by remember { mutableStateOf(false) }

    // Se busca en todas las fuentes de tus idiomas (no solo en las ancladas): lo que importa es encontrarlo.
    val fuentes = remember(ext.fuentes, ext.idiomas, ext.mostrarNsfw, fuenteId) {
        ext.fuentes.filter { it.id != fuenteId && (ext.mostrarNsfw || !it.nsfw) && it.lang in ext.idiomas }
            .sortedWith(compareBy({ it.id.toString() !in ext.fijadas }, { it.nombre.lowercase() }))
    }
    val resultados = rememberResultadosGlobales(fuentes, enviada)
    val claves = remember(almacen.version) { almacen.clavesEnLinea() }
    val (instalada, disponible) = ext.extensionDeFuente(fuenteId)

    Column(Modifier.fillMaxSize().background(LectorColors.Bg).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            BotonRedondoTema(Icons.Rounded.ArrowBack, "Atrás", onBack)
            Spacer(Modifier.width(16.dp))
            LText("Fuente no disponible", LectorType.Section, Modifier.weight(1f), maxLines = 1)
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 40.dp)) {
            // ---- El manga y por qué falta su fuente ----
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)) {
                    val portada = Modifier.width(88.dp).aspectRatio(2f / 3f)
                    if (enBiblioteca != null) ComicCover(enBiblioteca, almacen, portada, SuaveShape(14.dp))
                    else ImagenRemota(manga.portadaUrl, portada.clip(SuaveShape(14.dp)), emptyMap(), fuenteId = fuenteId)
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        LText(titulo, LectorType.Section, maxLines = 3)
                        Spacer(Modifier.height(6.dp))
                        val motivo = when {
                            instalada != null && !instalada.confiable ->
                                "La extensión «${instalada.nombre}» está instalada, pero no es de confianza."
                            instalada?.errorCarga != null ->
                                "La extensión «${instalada.nombre}» no se pudo cargar: ${instalada.errorCarga}"
                            disponible != null -> "Su extensión, «${disponible.nombre}», no está instalada. Está en tus listados."
                            else -> "Su extensión no está instalada y no aparece en tus listados de extensiones."
                        }
                        LText(motivo, LectorType.Body, color = LectorColors.Muted)
                        Spacer(Modifier.height(10.dp))
                        val pr = (instalada?.pkg ?: disponible?.pkg)?.let { ext.progreso[it] }
                        when {
                            pr != null && pr.fase != Fase.ERROR -> LinearProgressIndicator(Modifier.fillMaxWidth().clip(SuaveShape(4.dp)))
                            pr?.fase == Fase.ERROR -> LText(pr.error ?: "No se pudo instalar", LectorType.Label, color = LectorColors.Error, maxLines = 3)
                            instalada != null && !instalada.confiable ->
                                PillButton("Confiar", Icons.Rounded.Check, { ext.confiar(instalada) })
                            instalada?.actualizacion != null ->
                                PillButton("Actualizar", Icons.Rounded.Refresh, { instalada.actualizacion?.let { ext.instalar(it) } })
                            instalada == null && disponible != null ->
                                PillButton("Instalar", Icons.Rounded.Add, { ext.instalar(disponible) })
                        }
                    }
                }
            }

            // ---- Búsqueda en las demás fuentes ----
            item {
                Column(Modifier.padding(top = 12.dp)) {
                    TituloSeccion("Buscarlo en otras fuentes")
                    Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) { CampoBusqueda(consulta, { consulta = it }, "Título") }
                        Spacer(Modifier.width(8.dp))
                        BotonRedondoTema(Icons.Rounded.Search, "Buscar", { enviada = consulta.trim() })
                    }
                    Row(Modifier.padding(horizontal = 24.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ChipSel("Solo con resultados", soloConResultados) { soloConResultados = !soloConResultados }
                    }
                    val hechas = resultados.values.count { it !is EstadoBusqueda.Cargando }
                    val conAlgo = resultados.values.count { it is EstadoBusqueda.Hecho && it.mangas.isNotEmpty() }
                    val estado = when {
                        fuentes.isEmpty() -> "No tienes otras fuentes. Instala una extensión en Explorar → Extensiones."
                        enviada.isBlank() -> "Escribe un título y pulsa buscar."
                        hechas < fuentes.size -> "Buscando en tus fuentes… $hechas de ${fuentes.size}" +
                            (if (conAlgo > 0) " · aparece en $conAlgo" else "")
                        conAlgo == 0 -> "No apareció en ninguna de tus ${fuentes.size} fuentes. Prueba con otro título " +
                            "(el nombre en inglés, en japonés o sin subtítulo)."
                        else -> "Aparece en $conAlgo de ${fuentes.size} fuentes." +
                            (if (enBiblioteca != null) " Toca uno para migrar tu progreso o abrirlo." else "")
                    }
                    Row(Modifier.padding(horizontal = 24.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (enviada.isNotBlank() && hechas < fuentes.size) {
                            CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(10.dp))
                        }
                        LText(estado, LectorType.Label, color = LectorColors.Muted)
                    }
                }
            }
            if (enviada.isNotBlank()) {
                items(fuentes, key = { it.id }) { f ->
                    val e = resultados[f.id] ?: EstadoBusqueda.Cargando
                    if (soloConResultados && (e !is EstadoBusqueda.Hecho || e.mangas.isEmpty())) return@items
                    FilaResultados(
                        f, e, claves, destacar = titulo,
                        onManga = { m -> if (enBiblioteca != null) elegido = f to m else onAbrir(f, m) },
                        onLargo = { m -> alternarEnBiblioteca(ctx, almacen, f, m) },
                    )
                }
            }
        }
    }

    // Con el manga en la biblioteca: migrar (lo normal) o solo abrir el encontrado.
    val sel = elegido
    if (sel != null && enBiblioteca != null) {
        val (f, m) = sel
        AlertDialog(
            onDismissRequest = { if (!migrando) elegido = null },
            title = { Text("«${m.titulo}» en ${f.nombre}") },
            text = {
                Column {
                    Text(
                        "Migrar: sustituye en tu biblioteca a «${enBiblioteca.titulo}», con sus categorías y portada, y marca " +
                            "como leídos los mismos capítulos (por número). Las descargas viejas no se mueven.",
                    )
                    if (migrando) {
                        Spacer(Modifier.height(12.dp))
                        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = !migrando, onClick = {
                    migrando = true
                    scope.launch {
                        val r = runCatching { migrarComic(ext, almacen, enBiblioteca, fuenteId, null, f, m) }
                        migrando = false
                        elegido = null
                        r.onSuccess {
                            alRecargar()
                            Tacto.confirmar()
                            Avisos.mostrar("Migrado a ${f.nombre}")
                            onAbrir(f, m)
                        }.onFailure { Avisos.mostrar(it.message ?: "No se pudo migrar") }
                    }
                }) { Text("Migrar aquí") }
            },
            dismissButton = {
                Row {
                    TextButton(enabled = !migrando, onClick = { elegido = null; onAbrir(f, m) }) { Text("Solo abrir") }
                    TextButton(enabled = !migrando, onClick = { elegido = null }) { Text("Cancelar") }
                }
            },
        )
    }
}
