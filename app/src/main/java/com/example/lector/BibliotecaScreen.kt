package com.example.lector

import android.graphics.BitmapFactory
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.material3.TriStateCheckbox
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
import androidx.compose.ui.state.ToggleableState
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

    // Selección múltiple: ids de los cómics marcados.
    var seleccion by remember { mutableStateOf(emptySet<String>()) }
    var dialogoCategoriasSel by remember { mutableStateOf(false) }
    var confirmarBorrado by remember { mutableStateOf(false) }

    var dialogoOrden by remember { mutableStateOf(false) }
    var dialogoCategorias by remember { mutableStateOf(false) }
    var dialogoApariencia by remember { mutableStateOf(false) }
    var categoriaNueva by remember { mutableStateOf(false) }
    var renombrando by remember { mutableStateOf<Categoria?>(null) }

    fun recargar() {
        categorias = almacen.categorias()
        asignaciones = almacen.asignaciones()
    }

    fun alternar(id: String) {
        seleccion = if (id in seleccion) seleccion - id else seleccion + id
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

    // Solo cuentan los seleccionados que se ven ahora mismo.
    val seleccionados = lista.filter { it.id in seleccion }
    val modoSeleccion = seleccionados.isNotEmpty()

    // El botón "atrás" cierra la selección en vez de salir.
    BackHandler(enabled = modoSeleccion) { seleccion = emptySet() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            if (!modoSeleccion) {
                FloatingActionButton(
                    onClick = alAnadir,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ) {
                    Text("+", style = MaterialTheme.typography.headlineMedium)
                }
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            if (modoSeleccion) {
                // ---- Barra de selección ----
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (seleccionados.size == 1) {
                                "1 seleccionado"
                            } else {
                                "${seleccionados.size} seleccionados"
                            },
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { seleccion = emptySet() }) { Text("Cancelar") }
                    }
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val margen = PaddingValues(horizontal = 8.dp)
                        TextButton(
                            onClick = { seleccion = lista.map { it.id }.toSet() },
                            contentPadding = margen,
                        ) { Text("Todo") }
                        TextButton(
                            onClick = {
                                seleccion = lista.map { it.id }.toSet() -
                                    seleccionados.map { it.id }.toSet()
                            },
                            contentPadding = margen,
                        ) { Text("Invertir") }
                        Spacer(Modifier.weight(1f))
                        TextButton(
                            onClick = { dialogoCategoriasSel = true },
                            contentPadding = margen,
                        ) { Text("Categorías") }
                        TextButton(
                            onClick = { confirmarBorrado = true },
                            contentPadding = margen,
                        ) { Text("Eliminar") }
                    }
                }
            } else {
                // ---- Cabecera normal ----
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
                            DropdownMenuItem(
                                text = { Text("Apariencia") },
                                onClick = {
                                    menuAbierto = false
                                    dialogoApariencia = true
                                },
                            )
                        }
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
            if (!modoSeleccion) {
                continuar?.let { c ->
                    // El botón toma el color de la portada si esa opción está activada.
                    TemaPortada(almacen.portadaDe(c.id), almacen.colorPortada) {
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
                            seleccionado = comic.id in seleccion,
                            // Con la selección activa, tocar marca o desmarca.
                            alAbrir = {
                                if (modoSeleccion) alternar(comic.id) else alAbrir(comic)
                            },
                            alMantener = { alternar(comic.id) },
                        )
                    }
                }
            }
        }
    }

    // ---- Categorías de los cómics seleccionados ----
    if (dialogoCategoriasSel) {
        val ids = seleccionados.map { it.id }
        // Para cada categoría: la tienen todos (On), ninguno (Off) o solo algunos (Indeterminate).
        val inicial = categorias.associate { cat ->
            val cuantos = ids.count { cat.id in asignaciones[it].orEmpty() }
            val estado = when {
                ids.isNotEmpty() && cuantos == ids.size -> ToggleableState.On
                cuantos == 0 -> ToggleableState.Off
                else -> ToggleableState.Indeterminate
            }
            cat.id to estado
        }
        var estados by remember { mutableStateOf(inicial) }
        AlertDialog(
            onDismissRequest = { dialogoCategoriasSel = false },
            title = { Text("Categorías") },
            text = {
                if (categorias.isEmpty()) {
                    Text("Aún no hay categorías. Créalas desde el menú ⋮ → Categorías.")
                } else {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        categorias.forEach { cat ->
                            val estado = estados[cat.id] ?: ToggleableState.Off
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        val nuevo = if (estado == ToggleableState.On) {
                                            ToggleableState.Off
                                        } else {
                                            ToggleableState.On
                                        }
                                        estados = estados + (cat.id to nuevo)
                                    },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                TriStateCheckbox(state = estado, onClick = null)
                                Spacer(Modifier.width(8.dp))
                                Text(cat.nombre + if (cat.oculta) " (oculta)" else "")
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val agregar = estados.filter { it.value == ToggleableState.On }.keys
                    val quitar = estados.filter { it.value == ToggleableState.Off }.keys
                    almacen.modificarCategorias(ids.toSet(), agregar, quitar)
                    recargar()
                    seleccion = emptySet()
                    dialogoCategoriasSel = false
                }) { Text("Guardar") }
            },
            dismissButton = {
                TextButton(onClick = { dialogoCategoriasSel = false }) { Text("Cancelar") }
            },
        )
    }

    // ---- Eliminar los cómics seleccionados ----
    if (confirmarBorrado) {
        val unico = seleccionados.singleOrNull()
        AlertDialog(
            onDismissRequest = { confirmarBorrado = false },
            title = { Text("Eliminar") },
            text = {
                Text(
                    if (unico != null) {
                        "¿Quitar «${unico.titulo}» de la biblioteca?"
                    } else {
                        "¿Quitar ${seleccionados.size} cómics de la biblioteca?"
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    seleccionados.forEach { alEliminar(it) }
                    seleccion = emptySet()
                    confirmarBorrado = false
                    recargar()
                }) { Text("Eliminar") }
            },
            dismissButton = {
                TextButton(onClick = { confirmarBorrado = false }) { Text("Cancelar") }
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

    // ---- Apariencia: paletas y color según la portada ----
    if (dialogoApariencia) {
        AlertDialog(
            onDismissRequest = { dialogoApariencia = false },
            title = { Text("Apariencia") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text("Color de acento", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))

                    val opciones = Paletas.fijas.map { it.nombre to it.color } +
                        if (Build.VERSION.SDK_INT >= 31) {
                            listOf(Paletas.SISTEMA to colorDePaleta(Paletas.SISTEMA))
                        } else {
                            emptyList()
                        }

                    opciones.chunked(4).forEach { fila ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            fila.forEach { (nombre, color) ->
                                Column(
                                    Modifier
                                        .width(60.dp)
                                        .clickable { almacen.elegirPaleta(nombre) },
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Box(
                                        Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(color)
                                            .then(
                                                if (almacen.paleta == nombre) {
                                                    Modifier.border(
                                                        3.dp,
                                                        MaterialTheme.colorScheme.onBackground,
                                                        CircleShape,
                                                    )
                                                } else {
                                                    Modifier
                                                },
                                            ),
                                    )
                                    Text(
                                        nombre,
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1,
                                    )
                                }
                            }
                        }
                    }
                    if (Build.VERSION.SDK_INT >= 31) {
                        Text(
                            "«Sistema» usa los colores de tu fondo de pantalla.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    Spacer(Modifier.height(16.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Color según la portada")
                            Text(
                                "El lector y el botón Continuar toman el color de la portada.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = almacen.colorPortada,
                            onCheckedChange = { almacen.elegirColorPortada(it) },
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { dialogoApariencia = false }) { Text("Listo") }
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
    seleccionado: Boolean,
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
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .then(
                    if (seleccionado) {
                        Modifier.border(
                            3.dp,
                            MaterialTheme.colorScheme.primary,
                            RoundedCornerShape(12.dp),
                        )
                    } else {
                        Modifier
                    },
                ),
        ) {
            if (portada != null) {
                Image(
                    bitmap = portada,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            if (seleccionado) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "✓",
                        color = MaterialTheme.colorScheme.onPrimary,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
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
