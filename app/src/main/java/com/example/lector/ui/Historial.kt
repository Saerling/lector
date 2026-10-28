package com.example.lector.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.lector.Almacen
import com.example.lector.Comic
import com.example.lector.EntradaHistorial
import java.text.DateFormat
import java.util.Calendar
import java.util.Date

// Una fila del historial: un cómic local o un manga leído online.
private sealed class ItemHistorial {
    abstract val tiempo: Long
    abstract val clave: String
    abstract val titulo: String

    class Local(val comic: Comic) : ItemHistorial() {
        override val tiempo get() = comic.leido
        override val clave get() = "c_${comic.id}"
        override val titulo get() = comic.titulo
    }

    class EnLinea(val entrada: EntradaHistorial, val comic: Comic?) : ItemHistorial() {
        override val tiempo get() = entrada.leido
        override val clave get() = "o_${entrada.fuenteId}_${entrada.urlManga}"
        override val titulo get() = comic?.titulo ?: entrada.titulo
    }
}

private fun inicioDelDiaH(ms: Long): Long = Calendar.getInstance().apply {
    timeInMillis = ms
    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}.timeInMillis

/**
 * Historial (como el de Komikku): lo último que has leído, por día; un manga ocupa una fila con el capítulo y la
 * página por los que vas. Tocar = seguir leyendo; el botón de la papelera (o mantener pulsado) lo quita del historial.
 * Hay buscador y aviso del modo incógnito.
 */
@Composable
fun HistorialScreen(
    comics: List<Comic>,
    almacen: Almacen,
    alAbrir: (Comic) -> Unit,
    alAbrirOnline: (EntradaHistorial) -> Unit,
    modifier: Modifier = Modifier,
    alAbrirManga: (EntradaHistorial) -> Unit = {},
) {
    abrirMangaHist = alAbrirManga
    var consulta by remember { mutableStateOf("") }
    var buscando by remember { mutableStateOf(false) }
    var quitar by remember { mutableStateOf<ItemHistorial?>(null) }
    var confirmarTodo by remember { mutableStateOf(false) }

    val idsVisibles = remember(comics) { comics.map { it.id }.toSet() }
    val porClave = remember(almacen.version) { almacen.cargar().filter { it.enLinea }.associateBy { "${it.fuenteId}|${it.urlManga}" } }
    val entradas = remember(comics, almacen.historialLinea, porClave, consulta) {
        val locales = comics.filter { it.leido > 0 && !it.enLinea }.map { ItemHistorial.Local(it) }
        // Un manga de una categoría oculta tampoco sale aquí.
        val enLinea = almacen.historialLinea.mapNotNull { e ->
            val c = porClave["${e.fuenteId}|${e.urlManga}"]
            if (c != null && c.id !in idsVisibles) null else ItemHistorial.EnLinea(e, c)
        }
        val q = consulta.trim()
        (locales + enLinea).filter { q.isEmpty() || it.titulo.contains(q, ignoreCase = true) }.sortedByDescending { it.tiempo }
    }
    val grupos = remember(entradas) { entradas.groupBy { inicioDelDiaH(it.tiempo) } }
    val formatoHora = remember { DateFormat.getTimeInstance(DateFormat.SHORT) }

    Column(modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 24.dp, end = 8.dp, top = 12.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LText("Historial", LectorType.Display, Modifier.weight(1f), maxLines = 1)
            Box(Modifier.size(44.dp).pressable { buscando = !buscando; if (!buscando) consulta = "" }, Alignment.Center) {
                Icon(if (buscando) Icons.Rounded.Close else Icons.Rounded.Search, "Buscar", tint = LectorColors.Text)
            }
            if (entradas.isNotEmpty()) Box(Modifier.size(44.dp).pressable { confirmarTodo = true }, Alignment.Center) {
                Icon(Icons.Rounded.Delete, "Borrar el historial", tint = LectorColors.Text)
            }
        }
        if (almacen.incognito) {
            Row(
                Modifier.padding(horizontal = 24.dp, vertical = 4.dp).clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)).pressable { almacen.incognito = false }
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Lock, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                LText("Modo incógnito: no se anota nada · tocar para salir", LectorType.Label, color = MaterialTheme.colorScheme.primary)
            }
        }
        if (buscando) Box(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) { CampoBusqueda(consulta, { consulta = it }, "Buscar en el historial") }

        if (entradas.isEmpty()) {
            MensajeVacio(if (consulta.isBlank()) "Aún no has leído nada." else "Nada coincide con «$consulta».")
        } else {
            LazyColumn(contentPadding = PaddingValues(bottom = 190.dp)) {
                grupos.forEach { (dia, lista) ->
                    item(key = "dia_$dia") {
                        LText(
                            etiquetaDia(dia), LectorType.Section.copy(fontSize = 15.sp),
                            Modifier.padding(start = 24.dp, top = 18.dp, end = 24.dp, bottom = 6.dp), color = LectorColors.Muted,
                        )
                    }
                    items(lista, key = { it.clave }) { item ->
                        FilaHistorial(item, almacen, formatoHora.format(Date(item.tiempo)),
                            alAbrir = {
                                when (item) {
                                    is ItemHistorial.Local -> alAbrir(item.comic)
                                    is ItemHistorial.EnLinea -> alAbrirOnline(item.entrada)
                                }
                            },
                            alQuitar = { quitar = item },
                        )
                    }
                }
            }
        }
    }

    quitar?.let { item ->
        AlertDialog(
            onDismissRequest = { quitar = null },
            title = { Text("Quitar del historial") },
            text = { Text("«${item.titulo}» dejará de aparecer aquí. Tu progreso de lectura no se borra.") },
            confirmButton = {
                TextButton(onClick = {
                    when (item) {
                        is ItemHistorial.Local -> almacen.quitarDelHistorial(item.comic.id)
                        is ItemHistorial.EnLinea -> almacen.quitarHistorialLinea(item.entrada.fuenteId, item.entrada.urlManga)
                    }
                    quitar = null
                }) { Text("Quitar") }
            },
            dismissButton = { TextButton(onClick = { quitar = null }) { Text("Cancelar") } },
        )
    }

    if (confirmarTodo) {
        AlertDialog(
            onDismissRequest = { confirmarTodo = false },
            title = { Text("Borrar el historial") },
            text = { Text("Se vaciará el historial. Tu progreso de lectura no se borra.") },
            confirmButton = { TextButton(onClick = { almacen.limpiarHistorial(); confirmarTodo = false }) { Text("Borrar") } },
            dismissButton = { TextButton(onClick = { confirmarTodo = false }) { Text("Cancelar") } },
        )
    }
}

private var abrirMangaHist: (EntradaHistorial) -> Unit = {}

@Composable
private fun FilaHistorial(item: ItemHistorial, almacen: Almacen, hora: String, alAbrir: () -> Unit, alQuitar: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().resaltable(onLongClick = alQuitar, onClick = alAbrir).padding(start = 24.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val portada = Modifier.width(52.dp).aspectRatio(2f / 3f)
        when (item) {
            is ItemHistorial.Local -> ComicCover(item.comic, almacen, portada, SuaveShape(10.dp))
            is ItemHistorial.EnLinea -> {
                // Tocar la portada abre el manga (su ficha), no el capítulo.
                val portada = portada.clip(SuaveShape(10.dp)).clickable { abrirMangaHist(item.entrada) }
                if (item.comic != null) ComicCover(item.comic, almacen, portada, SuaveShape(10.dp))
                else ImagenRemota(item.entrada.portadaUrl, portada.clip(SuaveShape(10.dp)), rememberCabeceras(item.entrada.fuenteId), fuenteId = item.entrada.fuenteId)
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            LText(item.titulo, LectorType.Section.copy(fontSize = 16.sp), maxLines = 2)
            val detalle = when (item) {
                is ItemHistorial.Local -> "${item.comic.etiquetaProgreso()} · $hora"
                is ItemHistorial.EnLinea -> {
                    val e = item.entrada
                    val cap = e.capNombre.trim().trimEnd(':').trim().ifBlank { "Capítulo" }
                    val pag = if (e.total > 0) " · Pág. ${e.pagina + 1}/${e.total}" else ""
                    "$cap$pag · $hora"
                }
            }
            LText(detalle, LectorType.Label, color = LectorColors.Muted, maxLines = 1)
        }
        Box(Modifier.size(44.dp).pressable(alQuitar), Alignment.Center) {
            Icon(Icons.Rounded.Delete, "Quitar del historial", tint = LectorColors.Muted, modifier = Modifier.size(20.dp))
        }
    }
}
