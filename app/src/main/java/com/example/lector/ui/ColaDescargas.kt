package com.example.lector.ui

import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.lector.Almacen
import com.example.lector.Descargas

/** Cola de descargas (como la de Komikku): progreso, pausar / reanudar, mover al principio, cancelar y reintentar. */
@Composable
fun ColaDescargasScreen(almacen: Almacen, onBack: () -> Unit) {
    val ctx = LocalContext.current
    LaunchedEffect(Unit) { Descargas.cargar(ctx) }
    var menu by remember { mutableStateOf(false) }
    var confirmar by remember { mutableStateOf(false) }
    val cola = Descargas.cola.toList()
    val espacio = remember(Descargas.version.value) {
        Formatter.formatShortFileSize(ctx, almacen.bytesDescargas()) + " en ${almacen.totalDescargas()} capítulos"
    }

    Column(Modifier.fillMaxSize().background(LectorColors.Bg).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            BotonAtras(onBack)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                LText("Descargas", LectorType.Display.copy(fontSize = 28.sp), maxLines = 1)
                LText(
                    when {
                        cola.isEmpty() -> "Descargado: $espacio"
                        Descargas.pausado -> "En pausa · ${cola.size} en cola"
                        else -> "${cola.size} en cola · ${Descargas.progreso.size} descargando"
                    },
                    LectorType.Label, color = LectorColors.Muted, maxLines = 1,
                )
            }
            if (cola.isNotEmpty()) {
                BotonRedondoTema(
                    if (Descargas.pausado) Icons.Rounded.PlayArrow else Iconos.Detener,
                    if (Descargas.pausado) "Reanudar" else "Pausar",
                    { if (Descargas.pausado) Descargas.reanudar(ctx) else Descargas.pausar(ctx) },
                )
                Spacer(Modifier.width(6.dp))
                Box {
                    BotonRedondoTema(Icons.Rounded.MoreVert, "Más", { menu = true })
                    MenuShiori(expanded = menu, onDismissRequest = { menu = false }) {
                        if (Descargas.errores.isNotEmpty()) {
                            DropdownMenuItem(text = { Text("Reintentar las fallidas") }, onClick = { menu = false; Descargas.reintentar(ctx) })
                        }
                        DropdownMenuItem(text = { Text("Cancelar todas") }, onClick = { menu = false; confirmar = true })
                    }
                }
            }
        }

        if (cola.isEmpty()) {
            MensajeVacio("No hay descargas pendientes. Descarga capítulos desde la ficha de un manga (o en lote desde la biblioteca).")
        } else {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 40.dp)) {
                cola.groupBy { it.fuenteId to it.mangaUrl }.forEach { (_, tareas) ->
                    item(key = "m_${tareas.first().clave}") {
                        LText(
                            tareas.first().mangaTitulo.ifBlank { "Manga" } + " (${tareas.size})", LectorType.Section,
                            Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 4.dp), maxLines = 1,
                        )
                    }
                    items(tareas, key = { it.clave }) { t ->
                        val fraccion = Descargas.progreso[t.clave]
                        val error = Descargas.errores[t.clave]
                        Row(
                            Modifier.fillMaxWidth().padding(start = 24.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                LText(t.capNombre.ifBlank { "Capítulo" }, LectorType.Body, maxLines = 1)
                                when {
                                    error != null -> LText("Error: $error", LectorType.Label, color = LectorColors.Error, maxLines = 2)
                                    fraccion != null -> {
                                        Spacer(Modifier.height(6.dp))
                                        if (fraccion <= 0f) LinearProgressIndicator(Modifier.fillMaxWidth().clip(CircleShape))
                                        else LinearProgressIndicator(progress = { fraccion }, modifier = Modifier.fillMaxWidth().clip(CircleShape))
                                    }
                                    else -> LText(if (Descargas.pausado) "En pausa" else "En espera", LectorType.Label, color = LectorColors.Muted)
                                }
                            }
                            if (error != null) {
                                Box(Modifier.size(40.dp).pressable { Descargas.reintentar(ctx, t.clave) }, Alignment.Center) {
                                    Icon(Icons.Rounded.Refresh, "Reintentar", tint = LectorColors.Text)
                                }
                            } else if (fraccion == null) {
                                Box(Modifier.size(40.dp).pressable { Descargas.mover(ctx, t.clave, true) }, Alignment.Center) {
                                    Icon(Icons.Rounded.KeyboardArrowUp, "Mover al principio", tint = LectorColors.Text)
                                }
                            }
                            Box(Modifier.size(40.dp).pressable { Descargas.cancelar(ctx, t.clave) }, Alignment.Center) {
                                Icon(Icons.Rounded.Close, "Cancelar", tint = LectorColors.Text)
                            }
                        }
                    }
                }
                item {
                    LText(
                        "Descargado: $espacio" + if (almacen.descSoloWifi) " · Solo con Wi‑Fi" else "",
                        LectorType.Label, Modifier.padding(24.dp), color = LectorColors.Muted,
                    )
                }
            }
        }
    }

    if (confirmar) {
        DialogoAlerta(
            onDismissRequest = { confirmar = false },
            title = { Text("¿Cancelar todas las descargas?") },
            text = { Text("Se vacía la cola. Lo que ya está descargado se queda.") },
            confirmButton = { TextButton(onClick = { Descargas.cancelarTodo(ctx); confirmar = false }) { Text("Cancelar todas") } },
            dismissButton = { TextButton(onClick = { confirmar = false }) { Text("Volver") } },
        )
    }
}
