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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
) {
    val entradas = comics.filter { it.leido > 0 }.sortedByDescending { it.leido }
    val grupos = entradas.groupBy { inicioDelDia(it.leido) }

    var quitar by remember { mutableStateOf<Comic?>(null) }
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
                    items(lista, key = { it.id }) { comic ->
                        FilaHistorial(
                            comic = comic,
                            almacen = almacen,
                            hora = formatoHora.format(Date(comic.leido)),
                            alAbrir = { alAbrir(comic) },
                            alMantener = { quitar = comic },
                        )
                    }
                }
            }
        }
    }

    quitar?.let { comic ->
        AlertDialog(
            onDismissRequest = { quitar = null },
            title = { Text("Quitar del historial") },
            text = {
                Text(
                    "«${comic.titulo}» dejará de aparecer aquí. " +
                        "Tu progreso de lectura no se borra.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    almacen.quitarDelHistorial(comic.id)
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
                "Pág. ${comic.ultimaPagina + 1} de ${comic.paginas} · $hora",
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
