package com.example.lector.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.lector.Almacen
import com.example.lector.Categoria
import com.example.lector.Comic

/** Páginas que se abren encima de las pestañas (con animación de deslizamiento). */
enum class Ruta {
    Categorias, Estadisticas, Datos, Ajustes, Apariencia, AjBiblioteca, AjSeguimiento,
    // Explorar / extensiones (paso 3)
    ListadosExt, ExtDetalle, FuenteDetalle
}

fun Ruta.padre(): Ruta? = when (this) {
    Ruta.Apariencia, Ruta.AjBiblioteca, Ruta.AjSeguimiento -> Ruta.Ajustes
    else -> null
}

/** Pestaña "Más": todo lo que no cabe en la barra, igual que en Komikku. */
@Composable
fun MasScreen(onRuta: (Ruta) -> Unit, onAdd: () -> Unit, onAddFolder: () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().padding(bottom = 110.dp)
    ) {
        Box(Modifier.fillMaxWidth().padding(vertical = 28.dp), Alignment.Center) {
            Box(
                Modifier.size(88.dp).clip(RoundedCornerShape(28.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
                Alignment.Center
            ) { LText("L", LectorType.Display.copy(fontSize = 44.sp), color = MaterialTheme.colorScheme.primary) }
        }
        Divisor()
        FilaAjuste(Icons.Rounded.Add, "Añadir cómic", "Archivo .cbz de tu móvil", onAdd)
        FilaAjuste(Icons.Rounded.Add, "Añadir carpeta", "Una carpeta de imágenes (o con capítulos)", onAddFolder)
        Divisor()
        FilaAjuste(Icons.Rounded.Menu, "Categorías", null) { onRuta(Ruta.Categorias) }
        FilaAjuste(Icons.Rounded.Info, "Estadísticas", null) { onRuta(Ruta.Estadisticas) }
        FilaAjuste(Icons.Rounded.Share, "Datos y almacenamiento", null) { onRuta(Ruta.Datos) }
        Divisor()
        FilaAjuste(Icons.Rounded.Settings, "Ajustes", null) { onRuta(Ruta.Ajustes) }
    }
}

@Composable
fun EstadisticasScreen(comics: List<Comic>, onBack: () -> Unit) {
    val leidas = comics.filter { it.leido > 0 }.sumOf { it.ultimaPagina + 1 }
    PaginaAjustes("Estadísticas", onBack) {
        Row(Modifier.padding(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard("${comics.size}", "Cómics", Modifier.weight(1f))
            StatCard("${comics.count { it.terminado() }}", "Terminados", Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.padding(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard("${comics.count { it.enCurso() }}", "En curso", Modifier.weight(1f))
            StatCard("${comics.count { it.leido == 0L }}", "Sin leer", Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
        StatCard("$leidas", "Páginas leídas", Modifier.padding(horizontal = 24.dp).fillMaxWidth())
    }
}

@Composable
fun CategoriasScreen(almacen: Almacen, onBack: () -> Unit) {
    var cats by remember { mutableStateOf(almacen.categorias()) }
    var nueva by remember { mutableStateOf(false) }
    var renombrar by remember { mutableStateOf<Categoria?>(null) }
    var borrar by remember { mutableStateOf<Categoria?>(null) }
    fun refrescar() { cats = almacen.categorias() }

    PaginaAjustes("Categorías", onBack) {
        if (cats.isEmpty()) {
            LText("Todavía no tienes categorías.", LectorType.Body, Modifier.padding(24.dp), color = LectorColors.Muted)
        }
        cats.forEach { c ->
            Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 8.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    LText(c.nombre, LectorType.Section.copy(fontSize = 17.sp), maxLines = 1)
                    LText(if (c.oculta) "Oculta en Inicio y Buscar" else "Visible", LectorType.Label, color = LectorColors.Muted)
                }
                Switch(checked = c.oculta, onCheckedChange = { almacen.alternarOculta(c.id); refrescar() })
                IconButton(onClick = { renombrar = c }) { Icon(Icons.Rounded.Edit, "Renombrar", tint = LectorColors.Muted) }
                IconButton(onClick = { borrar = c }) { Icon(Icons.Rounded.Delete, "Borrar", tint = LectorColors.Muted) }
            }
        }
        Spacer(Modifier.height(16.dp))
        Box(Modifier.padding(horizontal = 24.dp)) { PillButton("Nueva categoría", Icons.Rounded.Add, { nueva = true }) }
    }

    if (nueva) DialogoTexto("Nueva categoría", "", { almacen.crearCategoria(it); refrescar(); nueva = false }, { nueva = false })
    renombrar?.let { c ->
        DialogoTexto("Renombrar", c.nombre, { almacen.renombrarCategoria(c.id, it); refrescar(); renombrar = null }, { renombrar = null })
    }
    borrar?.let { c ->
        AlertDialog(
            onDismissRequest = { borrar = null },
            title = { Text("¿Borrar «${c.nombre}»?") },
            text = { Text("Los cómics no se borran, solo pierden esta etiqueta.") },
            confirmButton = { TextButton(onClick = { almacen.eliminarCategoria(c.id); refrescar(); borrar = null }) { Text("Borrar") } },
            dismissButton = { TextButton(onClick = { borrar = null }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun DialogoTexto(titulo: String, inicial: String, alAceptar: (String) -> Unit, alCancelar: () -> Unit) {
    var texto by remember { mutableStateOf(inicial) }
    AlertDialog(
        onDismissRequest = alCancelar,
        title = { Text(titulo) },
        text = { OutlinedTextField(value = texto, onValueChange = { texto = it }, singleLine = true, label = { Text("Nombre") }) },
        confirmButton = { TextButton(onClick = { alAceptar(texto) }) { Text("Aceptar") } },
        dismissButton = { TextButton(onClick = alCancelar) { Text("Cancelar") } },
    )
}
