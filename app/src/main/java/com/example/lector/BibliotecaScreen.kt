package com.example.lector

import android.graphics.BitmapFactory
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

// Qué cómics mostrar según cuánto los hayas leído.
enum class Filtro { TODOS, SIN_LEER, EN_CURSO, TERMINADOS }

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun BibliotecaScreen(
    comics: List<Comic>,
    almacen: Almacen,
    alAnadir: () -> Unit,
    alAbrir: (Comic) -> Unit,
    alEliminar: (Comic) -> Unit,
) {
    var categorias by remember { mutableStateOf(almacen.categorias()) }
    var asignaciones by remember { mutableStateOf(almacen.asignaciones()) }
    var orden by remember { mutableStateOf(almacen.orden) }
    var ascendente by remember { mutableStateOf(almacen.ordenAscendente) }
    var filtro by remember { mutableStateOf(Filtro.TODOS) }
    var categoriaSel by remember { mutableStateOf<String?>(null) }
    var consulta by remember { mutableStateOf("") }
    var buscando by remember { mutableStateOf(false) }
    var mostrarOcultas by remember { mutableStateOf(false) }
    var menuAbierto by remember { mutableStateOf(false) }

    var porEliminar by remember { mutableStateOf<Comic?>(null) }
    var opcionesDe by remember { mutableStateOf<Comic?>(null) }
    var categoriasDe by remember { mutableStateOf<Comic?>(null) }
    var dialogoOrden by remember { mutableStateOf(false) }
    var dialogoCategorias by remember { mutableStateOf(false) }
    var categoriaNueva by remember { mutableStateOf(false) }
    var renombrando by remember { mutableStateOf<Categoria?>(null) }

    fun recargar() {
        categorias = almacen.categorias()
        asignaciones = almacen.asignaciones()
    }

    // ---- Qué se ve ----
    val idsOcultas = categorias.filter { it.oculta }.map { it.id }.toSet()
    val categoriasVisibles = categorias.filter { mostrarOcultas || !it.oculta }
    val activa = categoriaSel?.takeIf { id -> categoriasVisibles.any { it.id == id } }

    // Un cómic con alguna categoría oculta se esconde en todas partes (también en "Continuar").
    val visibles = comics.filter { c ->
        mostrarOcultas || asignaciones[c.id].orEmpty().none { it in idsOcultas }
    }

    val filtrados = visibles
        .filter { c -> activa == null || activa in asignaciones[c.id].orEmpty() }
        .filter { c -> filtro == Filtro.TODOS || estadoDe(c) == filtro }
        .filter { c -> consulta.isBlank() || c.titulo.contains(consulta.trim(), ignoreCase = true) }

    val ordenados = when (orden) {
        Orden.TITULO -> filtrados.sortedWith(
            Comparator { a, b ->
                Cbz.compararNatural(a.titulo.lowercase(), b.titulo.lowercase())
            },
        )
        Orden.ULTIMO_LEIDO -> filtrados.sortedBy { it.leido }
        Orden.PROGRESO -> filtrados.sortedBy { progresoDe(it) }
        Orden.ANADIDO -> filtrados
    }
    val lista = if (ascendente) ordenados else ordenados.reversed()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            FloatingActionButton(
                onClick = alAnadir,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Text("+", style = MaterialTheme.typography.headlineMedium)
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Mantener pulsado el título muestra u oculta las categorías ocultas.
                Column(
                    Modifier
                        .weight(1f)
                        .combinedClickable(
                            onClick = {},
                            onLongClick = { mostrarOcultas = !mostrarOcultas },
                        ),
                ) {
                    Text(
                        "Biblioteca",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    if (mostrarOcultas) {
                        Text(
                            "Ocultas visibles",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                TextButton(onClick = {
                    buscando = !buscando
                    if (!buscando) consulta = ""
                }) { Text(if (buscando) "Cerrar" else "Buscar") }
                Box {
                    TextButton(onClick = { menuAbierto = true }) { Text("⋮") }
                    DropdownMenu(
                        expanded = menuAbierto,
                        onDismissRequest = { menuAbierto = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("Ordenar y filtrar") },
                            onClick = {
                                menuAbierto = false
                                dialogoOrden = true
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Categorías") },
                            onClick = {
                                menuAbierto = false
                                dialogoCategorias = true
                            },
                        )
                    }
                }
            }

            if (buscando) {
                OutlinedTextField(
                    value = consulta,
                    onValueChange = { consulta = it },
                    singleLine = true,
                    placeholder = { Text("Buscar por título") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                )
                Spacer(Modifier.height(8.dp))
            }

            // Botón para seguir donde lo dejaste (solo si el último cómic no está terminado).
            val continuar = visibles.firstOrNull {
                it.id == almacen.ultimoAbierto && it.ultimaPagina < it.paginas - 1
            }
            continuar?.let { c ->
                Button(
                    onClick = { alAbrir(c) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                ) {
                    Text(
                        "Continuar: ${c.titulo} · pág. ${c.ultimaPagina + 1}",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            if (categoriasVisibles.isNotEmpty()) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item {
                        FilterChip(
                            selected = activa == null,
                            onClick = { categoriaSel = null },
                            label = { Text("Todos") },
                        )
                    }
                    items(categoriasVisibles, key = { it.id }) { cat ->
                        FilterChip(
                            selected = activa == cat.id,
                            onClick = { categoriaSel = cat.id },
                            label = { Text(cat.nombre + if (cat.oculta) " •" else "") },
                        )
                    }
                }
            }

            if (visibles.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "Toca + para añadir un archivo .cbz",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else if (lista.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "Sin resultados",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(130.dp),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    items(lista, key = { it.id }) { comic ->
                        Tarjeta(
                            comic = comic,
                            almacen = almacen,
                            alAbrir = { alAbrir(comic) },
                            alMantener = { opcionesDe = comic },
                        )
                    }
                }
            }
        }
    }

    // ---- Opciones de un cómic (pulsación larga) ----
    opcionesDe?.let { comic ->
        AlertDialog(
            onDismissRequest = { opcionesDe = null },
            title = { Text(comic.titulo, maxLines = 2, overflow = TextOverflow.Ellipsis) },
            text = {
                Column {
                    TextButton(onClick = {
                        categoriasDe = comic
                        opcionesDe = null
                    }) { Text("Categorías…") }
                    TextButton(onClick = {
                        porEliminar = comic
                        opcionesDe = null
                    }) { Text("Eliminar") }
                }
            },
            confirmButton = {
                TextButton(onClick = { opcionesDe = null }) { Text("Cerrar") }
            },
        )
    }

    // ---- Elegir las categorías de un cómic ----
    categoriasDe?.let { comic ->
        var marcadas by remember(comic.id) { mutableStateOf(asignaciones[comic.id].orEmpty()) }
        AlertDialog(
            onDismissRequest = { categoriasDe = null },
            title = { Text("Categorías") },
            text = {
                if (categorias.isEmpty()) {
                    Text("Aún no hay categorías. Créalas desde el menú ⋮ → Categorías.")
                } else {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        categorias.forEach { cat ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        marcadas = if (cat.id in marcadas) {
                                            marcadas - cat.id
                                        } else {
                                            marcadas + cat.id
                                        }
                                    },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(checked = cat.id in marcadas, onCheckedChange = null)
                                Spacer(Modifier.width(8.dp))
                                Text(cat.nombre + if (cat.oculta) " (oculta)" else "")
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    almacen.asignarCategorias(comic.id, marcadas)
                    recargar()
                    categoriasDe = null
                }) { Text("Guardar") }
            },
            dismissButton = {
                TextButton(onClick = { categoriasDe = null }) { Text("Cancelar") }
            },
        )
    }

    // ---- Eliminar un cómic ----
    porEliminar?.let { comic ->
        AlertDialog(
            onDismissRequest = { porEliminar = null },
            title = { Text("Eliminar") },
            text = { Text("¿Quitar «${comic.titulo}» de la biblioteca?") },
            confirmButton = {
                TextButton(onClick = {
                    alEliminar(comic)
                    porEliminar = null
                    recargar()
                }) { Text("Eliminar") }
            },
            dismissButton = {
                TextButton(onClick = { porEliminar = null }) { Text("Cancelar") }
            },
        )
    }

    // ---- Ordenar y filtrar ----
    if (dialogoOrden) {
        AlertDialog(
            onDismissRequest = { dialogoOrden = false },
            title = { Text("Ordenar y filtrar") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text("Ordenar por", fontWeight = FontWeight.Bold)
                    Orden.entries.forEach { o ->
                        FilaOpcion(etiqueta = nombreOrden(o), marcada = orden == o) {
                            orden = o
                            almacen.orden = o
                        }
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Orden ascendente", modifier = Modifier.weight(1f))
                        Switch(
                            checked = ascendente,
                            onCheckedChange = {
                                ascendente = it
                                almacen.ordenAscendente = it
                            },
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("Mostrar", fontWeight = FontWeight.Bold)
                    Filtro.entries.forEach { f ->
                        FilaOpcion(etiqueta = nombreFiltro(f), marcada = filtro == f) {
                            filtro = f
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { dialogoOrden = false }) { Text("Listo") }
            },
        )
    }

    // ---- Gestionar categorías ----
    if (dialogoCategorias) {
        AlertDialog(
            onDismissRequest = { dialogoCategorias = false },
            title = { Text("Categorías") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        "Las categorías ocultas esconden sus cómics. Para verlas, " +
                            "mantén pulsado el título «Biblioteca».",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    if (categorias.isEmpty()) {
                        Text("Todavía no tienes categorías. Pulsa «Nueva categoría».")
                    }
                    categorias.forEach { cat ->
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    cat.nombre,
                                    modifier = Modifier.weight(1f),
                                    fontWeight = FontWeight.Medium,
                                )
                                Text("Oculta", style = MaterialTheme.typography.bodySmall)
                                Spacer(Modifier.width(8.dp))
                                Switch(
                                    checked = cat.oculta,
                                    onCheckedChange = {
                                        almacen.alternarOculta(cat.id)
                                        recargar()
                                    },
                                )
                            }
                            Row {
                                TextButton(onClick = { renombrando = cat }) { Text("Renombrar") }
                                TextButton(onClick = {
                                    almacen.eliminarCategoria(cat.id)
                                    recargar()
                                }) { Text("Borrar") }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { categoriaNueva = true }) { Text("Nueva categoría") }
            },
            dismissButton = {
                TextButton(onClick = { dialogoCategorias = false }) { Text("Cerrar") }
            },
        )
    }

    if (categoriaNueva) {
        DialogoNombre(
            titulo = "Nueva categoría",
            inicial = "",
            alAceptar = {
                almacen.crearCategoria(it)
                recargar()
                categoriaNueva = false
            },
            alCancelar = { categoriaNueva = false },
        )
    }

    renombrando?.let { cat ->
        DialogoNombre(
            titulo = "Renombrar categoría",
            inicial = cat.nombre,
            alAceptar = {
                almacen.renombrarCategoria(cat.id, it)
                recargar()
                renombrando = null
            },
            alCancelar = { renombrando = null },
        )
    }
}

@Composable
private fun DialogoNombre(
    titulo: String,
    inicial: String,
    alAceptar: (String) -> Unit,
    alCancelar: () -> Unit,
) {
    var texto by remember { mutableStateOf(inicial) }
    AlertDialog(
        onDismissRequest = alCancelar,
        title = { Text(titulo) },
        text = {
            OutlinedTextField(
                value = texto,
                onValueChange = { texto = it },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = { alAceptar(texto) }) { Text("Aceptar") }
        },
        dismissButton = {
            TextButton(onClick = alCancelar) { Text("Cancelar") }
        },
    )
}

@Composable
private fun FilaOpcion(etiqueta: String, marcada: Boolean, alElegir: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { alElegir() },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = marcada, onClick = null)
        Spacer(Modifier.width(8.dp))
        Text(etiqueta)
    }
}

private fun nombreOrden(o: Orden): String = when (o) {
    Orden.TITULO -> "Título"
    Orden.ULTIMO_LEIDO -> "Último leído"
    Orden.PROGRESO -> "Progreso"
    Orden.ANADIDO -> "Fecha de añadido"
}

private fun nombreFiltro(f: Filtro): String = when (f) {
    Filtro.TODOS -> "Todos"
    Filtro.SIN_LEER -> "Sin leer"
    Filtro.EN_CURSO -> "En curso"
    Filtro.TERMINADOS -> "Terminados"
}

// Sin leer = en la primera página; terminado = en la última; el resto, en curso.
private fun estadoDe(c: Comic): Filtro = when {
    c.ultimaPagina == 0 -> Filtro.SIN_LEER
    c.ultimaPagina >= c.paginas - 1 -> Filtro.TERMINADOS
    else -> Filtro.EN_CURSO
}

private fun progresoDe(c: Comic): Float =
    if (c.paginas > 1) c.ultimaPagina.toFloat() / (c.paginas - 1) else 0f

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Tarjeta(
    comic: Comic,
    almacen: Almacen,
    alAbrir: () -> Unit,
    alMantener: () -> Unit,
) {
    val portada = remember(comic.id) {
        BitmapFactory.decodeFile(almacen.portadaDe(comic.id).path)?.asImageBitmap()
    }
    Column(Modifier.combinedClickable(onClick = alAbrir, onLongClick = alMantener)) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(12.dp))
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
        Spacer(Modifier.height(6.dp))
        Text(
            comic.titulo,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
        )
        Text(
            "Pág. ${comic.ultimaPagina + 1} de ${comic.paginas}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
