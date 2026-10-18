package com.example.lector

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.lector.ui.ImagenRemota
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Calendar
import java.util.Date

// Lo último que has leído, agrupado por día. Tocar abre el cómic; mantener pulsado lo quita
// del historial (sin borrar tu progreso).
@Composable
fun HistorialScreen(
    comics: List<Comic>,
    almacen: Almacen,
    alAbrir: (Comic) -> Unit,
    alCambiar: () -> Unit,
    modifier: Modifier = Modifier,
    alVolver: (() -> Unit)? = null,
    // Lo que se lee online (mangas de fuentes): tocar una fila abre ese manga en su capítulo.
    alAbrirOnline: (EntradaHistorial) -> Unit = {},
    // Cabeceras HTTP (Referer...) para cargar las portadas de cada fuente.
    cabecerasDe: (Long) -> Map<String, String> = { emptyMap() },
) {
    // Cómics locales (los mangas en línea de la biblioteca salen por el historial online, no aquí).
    val locales = comics.filter { it.leido > 0 && !it.enLinea }
        .map { ItemHistorial.Local(it) }

    // Un manga online cuya categoría está oculta tampoco sale aquí.
    val idsVisibles = comics.map { it.id }.toSet()
    val ocultosEnLinea = remember(comics) {
        almacen.cargar().filter { it.enLinea && it.id !in idsVisibles }
            .map { it.fuenteId to it.urlManga }.toSet()
    }
    val enLinea = almacen.historialLinea
        .filter { (it.fuenteId to it.urlManga) !in ocultosEnLinea }
        .map { ItemHistorial.EnLinea(it) }

    val entradas = (locales + enLinea).sortedByDescending { it.tiempo }
    val grupos = entradas.groupBy { inicioDelDia(it.tiempo) }

    var quitar by remember { mutableStateOf<ItemHistorial?>(null) }
    var confirmarTodo by remember { mutableStateOf(false) }

    val formatoDia = remember { DateFormat.getDateInstance(DateFormat.LONG) }
    val formatoHora = remember { DateFormat.getTimeInstance(DateFormat.SHORT) }
    val hoy = inicioDelDia(System.currentTimeMillis())
    val ayer = inicioDelDia(hoy - 1)

    Column(modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (alVolver != null) {
                TextButton(onClick = alVolver) { Text("‹ Volver") }
            }
            Text(
                "Historial",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            if (entradas.isNotEmpty()) {
                TextButton(onClick = { confirmarTodo = true }) { Text("Borrar todo") }
            }
        }

        if (entradas.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "Aún no has leído nada",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
                grupos.forEach { (dia, lista) ->
                    item(key = "dia_$dia") {
                        Text(
                            when (dia) {
                                hoy -> "Hoy"
                                ayer -> "Ayer"
                                else -> formatoDia.format(Date(dia))
                            },
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 4.dp),
                        )
                    }
                    items(lista, key = { it.clave }) { item ->
                        when (item) {
                            is ItemHistorial.Local -> FilaHistorial(
                                comic = item.comic,
                                almacen = almacen,
                                hora = formatoHora.format(Date(item.tiempo)),
                                alAbrir = { alAbrir(item.comic) },
                                alMantener = { quitar = item },
                            )
                            is ItemHistorial.EnLinea -> FilaHistorialEnLinea(
                                e = item.entrada,
                                hora = formatoHora.format(Date(item.tiempo)),
                                cabecerasDe = cabecerasDe,
                                alAbrir = { alAbrirOnline(item.entrada) },
                                alMantener = { quitar = item },
                            )
                        }
                    }
                }
            }
        }
    }

    quitar?.let { item ->
        AlertDialog(
            onDismissRequest = { quitar = null },
            title = { Text("Quitar del historial") },
            text = {
                Text(
                    "«${item.titulo}» dejará de aparecer aquí. " +
                        "Tu progreso de lectura no se borra.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    when (item) {
                        is ItemHistorial.Local -> almacen.quitarDelHistorial(item.comic.id)
                        is ItemHistorial.EnLinea ->
                            almacen.quitarHistorialLinea(item.entrada.fuenteId, item.entrada.urlManga)
                    }
                    quitar = null
                    alCambiar()
                }) { Text("Quitar") }
            },
            dismissButton = {
                TextButton(onClick = { quitar = null }) { Text("Cancelar") }
            },
        )
    }

    if (confirmarTodo) {
        AlertDialog(
            onDismissRequest = { confirmarTodo = false },
            title = { Text("Borrar el historial") },
            text = { Text("Se vaciará el historial. Tu progreso de lectura no se borra.") },
            confirmButton = {
                TextButton(onClick = {
                    almacen.limpiarHistorial()
                    confirmarTodo = false
                    alCambiar()
                }) { Text("Borrar") }
            },
            dismissButton = {
                TextButton(onClick = { confirmarTodo = false }) { Text("Cancelar") }
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FilaHistorial(
    comic: Comic,
    almacen: Almacen,
    hora: String,
    alAbrir: () -> Unit,
    alMantener: () -> Unit,
) {
    val portada = rememberPortada(almacen, comic.id)
    Row(
        Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = alAbrir, onLongClick = alMantener)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .width(48.dp)
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            if (portada != null) {
                Image(
                    bitmap = portada,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                comic.titulo,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            Text(
                "${comic.etiquetaProgreso()} · $hora",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

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

    class EnLinea(val entrada: EntradaHistorial) : ItemHistorial() {
        override val tiempo get() = entrada.leido
        override val clave get() = "o_${entrada.fuenteId}_${entrada.urlManga}"
        override val titulo get() = entrada.titulo
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FilaHistorialEnLinea(
    e: EntradaHistorial,
    hora: String,
    cabecerasDe: (Long) -> Map<String, String>,
    alAbrir: () -> Unit,
    alMantener: () -> Unit,
) {
    // Las cabeceras se piden fuera del hilo principal (puede cargar la extensión la primera vez).
    val cabeceras by produceState<Map<String, String>?>(null, e.fuenteId) {
        value = withContext(Dispatchers.IO) { runCatching { cabecerasDe(e.fuenteId) }.getOrDefault(emptyMap()) }
    }
    Row(
        Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = alAbrir, onLongClick = alMantener)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .width(48.dp)
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            val c = cabeceras
            if (c != null) ImagenRemota(e.portadaUrl, Modifier.fillMaxSize(), c)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                e.titulo,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            val cap = e.capNombre.ifBlank { "Capítulo" }
            val pagina = if (e.total > 0) " · Pág. ${e.pagina + 1} de ${e.total}" else ""
            Text(
                "$cap$pagina · $hora",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// Medianoche (hora local) del día al que pertenece el instante dado.
private fun inicioDelDia(milisegundos: Long): Long {
    val c = Calendar.getInstance()
    c.timeInMillis = milisegundos
    c.set(Calendar.HOUR_OF_DAY, 0)
    c.set(Calendar.MINUTE, 0)
    c.set(Calendar.SECOND, 0)
    c.set(Calendar.MILLISECOND, 0)
    return c.timeInMillis
}
