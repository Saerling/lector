package com.example.lector

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Qué cómics mostrar según cuánto los hayas leído.
enum class Filtro { TODOS, SIN_LEER, EN_CURSO, TERMINADOS }

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun BibliotecaScreen(
    comics: List<Comic>,
    almacen: Almacen,
    alAnadir: () -> Unit,
    alAnadirCarpeta: () -> Unit,
    alAbrir: (Comic) -> Unit,
    alEliminar: (Comic) -> Unit,
    alRecargar: () -> Unit,
    // 0 = biblioteca, 1 = historial (la guarda la actividad para no perderla al leer).
    pestana: Int = 0,
    alCambiarPestana: (Int) -> Unit = {},
    // Con false se oculta la barra Biblioteca/Historial (la app ya tiene su propia barra)
    // y el historial se abre desde el menú ⋮.
    conNavegacion: Boolean = true,
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
    var dialogoVista by remember { mutableStateOf(false) }
    var dialogoLector by remember { mutableStateOf(false) }
    var dialogoAnilist by remember { mutableStateOf(false) }
    var comicSeguimiento by remember { mutableStateOf<Comic?>(null) }
    var dialogoAnadir by remember { mutableStateOf(false) }
    var dialogoCopia by remember { mutableStateOf(false) }
    var resultadoCopia by remember { mutableStateOf<String?>(null) }

    val contexto = LocalContext.current
    val ambito = rememberCoroutineScope()

    fun recargar() {
        categorias = almacen.categorias()
        asignaciones = almacen.asignaciones()
    }

    // Guardar la copia de seguridad en un archivo que eliges.
    val crearCopia = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) {
            resultadoCopia = try {
                val salida = contexto.contentResolver.openOutputStream(uri)
                    ?: throw IllegalStateException("Sin salida")
                salida.use { it.write(almacen.crearCopia().toByteArray(Charsets.UTF_8)) }
                "Copia guardada."
            } catch (e: Exception) {
                "No se pudo guardar la copia."
            }
        }
    }

    // Restaurar desde un archivo de copia.
    val restaurar = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            resultadoCopia = try {
                val entrada = contexto.contentResolver.openInputStream(uri)
                    ?: throw IllegalStateException("Sin entrada")
                val texto = entrada.use { it.readBytes().toString(Charsets.UTF_8) }
                val r = almacen.restaurarCopia(texto)
                recargar()
                orden = almacen.orden
                ascendente = almacen.ordenAscendente
                alRecargar()
                "Copia restaurada: ${r.actualizados} cómics actualizados" +
                    if (r.pendientes > 0) {
                        " y ${r.pendientes} pendientes. Su progreso y categorías se aplicarán " +
                            "cuando añadas esos archivos."
                    } else {
                        "."
                    }
            } catch (e: Exception) {
                "El archivo no es una copia válida."
            }
        }
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
    // En el historial, "atrás" vuelve a la biblioteca.
    BackHandler(enabled = pestana == 1 && !modoSeleccion) { alCambiarPestana(0) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            if (!modoSeleccion && pestana == 0) {
                FloatingActionButton(
                    onClick = { dialogoAnadir = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ) {
                    Text("+", style = MaterialTheme.typography.headlineMedium)
                }
            }
        },
        bottomBar = {
            if (!modoSeleccion && conNavegacion) {
                NavigationBar {
                    NavigationBarItem(
                        selected = pestana == 0,
                        onClick = { alCambiarPestana(0) },
                        icon = { Text("▦") },
                        label = { Text("Biblioteca") },
                    )
                    NavigationBarItem(
                        selected = pestana == 1,
                        onClick = { alCambiarPestana(1) },
                        icon = { Text("◷") },
                        label = { Text("Historial") },
                    )
                }
            }
        },
    ) { padding ->
        if (pestana == 1) {
            // Los cómics de categorías ocultas tampoco aparecen en el historial.
            val volverABiblioteca: () -> Unit = { alCambiarPestana(0) }
            HistorialScreen(
                comics = visibles,
                almacen = almacen,
                alAbrir = alAbrir,
                alCambiar = alRecargar,
                modifier = Modifier.padding(padding),
                alVolver = if (conNavegacion) null else volverABiblioteca,
            )
        } else Column(Modifier.padding(padding)) {
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
                    if (seleccionados.size == 1) {
                        TextButton(
                            onClick = { comicSeguimiento = seleccionados.first() },
                            contentPadding = PaddingValues(horizontal = 8.dp),
                        ) { Text("Seguimiento…") }
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
                            if (!conNavegacion) {
                                DropdownMenuItem(
                                    text = { Text("Historial") },
                                    onClick = {
                                        menuAbierto = false
                                        alCambiarPestana(1)
                                    },
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Vista de la biblioteca") },
                                onClick = {
                                    menuAbierto = false
                                    dialogoVista = true
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Apariencia") },
                                onClick = {
                                    menuAbierto = false
                                    dialogoApariencia = true
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Lector") },
                                onClick = {
                                    menuAbierto = false
                                    dialogoLector = true
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Seguimiento (AniList)") },
                                onClick = {
                                    menuAbierto = false
                                    dialogoAnilist = true
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Copia de seguridad") },
                                onClick = {
                                    menuAbierto = false
                                    dialogoCopia = true
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
            } else if (almacen.vista == Vista.LISTA) {
                LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
                    items(lista, key = { it.id }) { comic ->
                        FilaLista(
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
            } else {
                val comoda = almacen.vista == Vista.CUADRICULA_COMODA
                LazyVerticalGrid(
                    columns = if (almacen.columnas == 0) {
                        GridCells.Adaptive(130.dp)
                    } else {
                        GridCells.Fixed(almacen.columnas)
                    },
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(if (comoda) 12.dp else 8.dp),
                    verticalArrangement = Arrangement.spacedBy(if (comoda) 16.dp else 8.dp),
                ) {
                    items(lista, key = { it.id }) { comic ->
                        TarjetaPortada(
                            comic = comic,
                            almacen = almacen,
                            vista = almacen.vista,
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

    // ---- Vista de la biblioteca ----
    if (dialogoVista) {
        AlertDialog(
            onDismissRequest = { dialogoVista = false },
            title = { Text("Vista de la biblioteca") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text("Modo", fontWeight = FontWeight.Bold)
                    Vista.entries.forEach { v ->
                        FilaOpcion(etiqueta = nombreVista(v), marcada = almacen.vista == v) {
                            almacen.elegirVista(v)
                        }
                    }
                    if (almacen.vista != Vista.LISTA) {
                        Spacer(Modifier.height(12.dp))
                        Text("Columnas", fontWeight = FontWeight.Bold)
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            listOf(0, 2, 3, 4, 5).forEach { n ->
                                FilterChip(
                                    selected = almacen.columnas == n,
                                    onClick = { almacen.elegirColumnas(n) },
                                    label = { Text(if (n == 0) "Auto" else n.toString()) },
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    FilaInterruptor(
                        titulo = "Mostrar la página actual",
                        detalle = "«Pág. X de Y» junto a cada cómic.",
                        activado = almacen.mostrarPagina,
                        alCambiar = { almacen.elegirMostrarPagina(it) },
                    )
                    Spacer(Modifier.height(12.dp))
                    FilaInterruptor(
                        titulo = "Insignia de progreso",
                        detalle = "Porcentaje sobre la portada, o ✓ si está terminado.",
                        activado = almacen.insigniaProgreso,
                        alCambiar = { almacen.elegirInsigniaProgreso(it) },
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { dialogoVista = false }) { Text("Listo") }
            },
        )
    }

    // ---- Ajustes del lector ----
    if (dialogoLector) {
        AlertDialog(
            onDismissRequest = { dialogoLector = false },
            title = { Text("Lector") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    FilaInterruptor(
                        titulo = "Tocar los bordes para pasar de página",
                        detalle = "Izquierda y derecha cambian de página; el centro muestra " +
                            "u oculta la barra.",
                        activado = almacen.zonasToque,
                        alCambiar = { almacen.elegirZonasToque(it) },
                    )
                    Spacer(Modifier.height(12.dp))
                    FilaInterruptor(
                        titulo = "Teclas de volumen",
                        detalle = "Bajar el volumen avanza y subirlo retrocede.",
                        activado = almacen.teclasVolumen,
                        alCambiar = { almacen.elegirTeclasVolumen(it) },
                    )
                    Spacer(Modifier.height(12.dp))
                    FilaInterruptor(
                        titulo = "Mantener la pantalla encendida",
                        detalle = null,
                        activado = almacen.mantenerEncendida,
                        alCambiar = { almacen.elegirMantenerEncendida(it) },
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { dialogoLector = false }) { Text("Listo") }
            },
        )
    }

    // ---- Cuenta de AniList ----
    if (dialogoAnilist) {
        var clientId by remember { mutableStateOf(almacen.anilistClientId) }
        AlertDialog(
            onDismissRequest = { dialogoAnilist = false },
            title = { Text("Seguimiento (AniList)") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    if (almacen.anilistToken != null) {
                        Text("Conectado como ${almacen.anilistUsuario ?: "…"}")
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Vincula un cómic a su serie manteniéndolo pulsado y eligiendo " +
                                "«Seguimiento…». Al llegar a su última página, se sube el progreso.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        TextButton(onClick = { almacen.cerrarSesionAnilist() }) {
                            Text("Cerrar sesión")
                        }
                    } else {
                        Text(
                            "1. En anilist.co → Settings → Developer, crea una aplicación " +
                                "con la URL de redirección  lector://anilist\n" +
                                "2. Pega aquí su Client ID (un número) y pulsa «Iniciar sesión».",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = clientId,
                            onValueChange = { clientId = it.filter { c -> c.isDigit() } },
                            singleLine = true,
                            label = { Text("Client ID") },
                        )
                        TextButton(
                            enabled = clientId.isNotEmpty(),
                            onClick = {
                                almacen.guardarClientId(clientId)
                                contexto.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(Anilist.urlDeLogin(clientId))),
                                )
                            },
                        ) { Text("Iniciar sesión") }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { dialogoAnilist = false }) { Text("Cerrar") }
            },
        )
    }

    // ---- Vincular un cómic con una serie de AniList ----
    comicSeguimiento?.let { comic ->
        val vinculoInicial = remember(comic.id) { almacen.seguimientoDe(comic.id) }
        var elegido by remember(comic.id) {
            mutableStateOf(vinculoInicial?.let { it.mediaId to it.titulo })
        }
        var porVolumenes by remember(comic.id) { mutableStateOf(vinculoInicial?.volumenes ?: true) }
        var numero by remember(comic.id) {
            mutableStateOf((vinculoInicial?.numero ?: numeroDeTitulo(comic.titulo))?.toString() ?: "")
        }
        var busqueda by remember(comic.id) { mutableStateOf(comic.titulo) }
        var resultados by remember(comic.id) { mutableStateOf(emptyList<ResultadoAnilist>()) }
        var cargandoBusqueda by remember(comic.id) { mutableStateOf(false) }
        var errorBusqueda by remember(comic.id) { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { comicSeguimiento = null },
            title = { Text("Seguimiento") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        comic.titulo,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(8.dp))
                    val serie = elegido
                    if (serie != null) {
                        Text("Serie en AniList", fontWeight = FontWeight.Bold)
                        Text(serie.second)
                        TextButton(onClick = { elegido = null }) { Text("Cambiar serie") }
                        Spacer(Modifier.height(8.dp))
                        Text("Este archivo es un…", fontWeight = FontWeight.Bold)
                        FilaOpcion(etiqueta = "Volumen", marcada = porVolumenes) {
                            porVolumenes = true
                        }
                        FilaOpcion(etiqueta = "Capítulo", marcada = !porVolumenes) {
                            porVolumenes = false
                        }
                        OutlinedTextField(
                            value = numero,
                            onValueChange = { numero = it.filter { c -> c.isDigit() }.take(5) },
                            singleLine = true,
                            label = { Text("Número") },
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Al terminarlo, AniList marcará hasta ese " +
                                (if (porVolumenes) "volumen." else "capítulo."),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (almacen.anilistToken == null) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Aún no has iniciado sesión: ⋮ → Seguimiento (AniList).",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    } else {
                        OutlinedTextField(
                            value = busqueda,
                            onValueChange = { busqueda = it },
                            singleLine = true,
                            label = { Text("Buscar en AniList") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Button(
                            enabled = busqueda.isNotBlank() && !cargandoBusqueda,
                            onClick = {
                                ambito.launch {
                                    cargandoBusqueda = true
                                    errorBusqueda = null
                                    val respuesta = withContext(Dispatchers.IO) {
                                        runCatching { Anilist.buscar(busqueda) }
                                    }
                                    respuesta
                                        .onSuccess { resultados = it }
                                        .onFailure {
                                            errorBusqueda = "No se pudo buscar. Revisa tu conexión."
                                        }
                                    cargandoBusqueda = false
                                }
                            },
                        ) { Text(if (cargandoBusqueda) "Buscando…" else "Buscar") }
                        errorBusqueda?.let {
                            Text(it, color = MaterialTheme.colorScheme.error)
                        }
                        resultados.forEach { r ->
                            Text(
                                r.titulo + (r.formato?.let { " · $it" } ?: ""),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { elegido = r.id to r.titulo }
                                    .padding(vertical = 8.dp),
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = elegido != null && numero.isNotEmpty(),
                    onClick = {
                        val serie = elegido
                        val n = numero.toIntOrNull()
                        if (serie != null && n != null) {
                            almacen.guardarSeguimiento(
                                comic.id,
                                Seguimiento(serie.first, serie.second, porVolumenes, n),
                            )
                            // Si ya lo habías terminado, sube el progreso ahora.
                            if (comic.ultimaPagina >= comic.paginas - 1) {
                                almacen.marcarSincronizacion(comic.id, true)
                                ambito.launch {
                                    withContext(Dispatchers.IO) { almacen.sincronizarAnilist(comic.id) }
                                }
                            }
                            comicSeguimiento = null
                        }
                    },
                ) { Text("Guardar") }
            },
            dismissButton = {
                Row {
                    if (vinculoInicial != null) {
                        TextButton(onClick = {
                            almacen.guardarSeguimiento(comic.id, null)
                            comicSeguimiento = null
                        }) { Text("Desvincular") }
                    }
                    TextButton(onClick = { comicSeguimiento = null }) { Text("Cancelar") }
                }
            },
        )
    }

    // ---- Añadir: archivo o carpeta ----
    if (dialogoAnadir) {
        AlertDialog(
            onDismissRequest = { dialogoAnadir = false },
            title = { Text("Añadir") },
            text = {
                Column {
                    TextButton(onClick = {
                        dialogoAnadir = false
                        alAnadir()
                    }) { Text("Archivo .cbz") }
                    TextButton(onClick = {
                        dialogoAnadir = false
                        alAnadirCarpeta()
                    }) { Text("Carpeta de imágenes") }
                    Text(
                        "En una carpeta, las imágenes se ordenan por nombre. Si tiene " +
                            "subcarpetas (por ejemplo, una por capítulo), se leen todas seguidas.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { dialogoAnadir = false }) { Text("Cancelar") }
            },
        )
    }

    // ---- Copia de seguridad ----
    if (dialogoCopia) {
        AlertDialog(
            onDismissRequest = { dialogoCopia = false },
            title = { Text("Copia de seguridad") },
            text = {
                Column {
                    Text(
                        "La copia guarda tu progreso, categorías, modos de lectura y ajustes. " +
                            "No incluye los archivos de los cómics.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = {
                        dialogoCopia = false
                        val fecha = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                        crearCopia.launch("lector-copia-$fecha.json")
                    }) { Text("Crear copia") }
                    TextButton(onClick = {
                        dialogoCopia = false
                        restaurar.launch(arrayOf("*/*"))
                    }) { Text("Restaurar copia") }
                }
            },
            confirmButton = {
                TextButton(onClick = { dialogoCopia = false }) { Text("Cerrar") }
            },
        )
    }

    resultadoCopia?.let { mensaje ->
        AlertDialog(
            onDismissRequest = { resultadoCopia = null },
            title = { Text("Copia de seguridad") },
            text = { Text(mensaje) },
            confirmButton = {
                TextButton(onClick = { resultadoCopia = null }) { Text("Aceptar") }
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

@Composable
private fun FilaInterruptor(
    titulo: String,
    detalle: String?,
    activado: Boolean,
    alCambiar: (Boolean) -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(titulo)
            if (detalle != null) {
                Text(
                    detalle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Switch(checked = activado, onCheckedChange = alCambiar)
    }
}

// Intenta adivinar el número de volumen o capítulo a partir del título ("Vol. 3", "Cap 12"...).
// Es solo una sugerencia: se puede corregir a mano.
private fun numeroDeTitulo(titulo: String): Int? {
    val conPrefijo = Regex("(?i)(?:vol(?:umen|ume)?|cap(?:[ií]tulo)?|ch(?:apter)?|[vc#])[ .]*0*(\\d+)")
        .find(titulo)
    if (conPrefijo != null) return conPrefijo.groupValues[1].toIntOrNull()
    return Regex("(\\d+)").findAll(titulo).lastOrNull()?.groupValues?.get(1)?.toIntOrNull()
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
fun estadoDe(c: Comic): Filtro = when {
    c.ultimaPagina == 0 -> Filtro.SIN_LEER
    c.ultimaPagina >= c.paginas - 1 -> Filtro.TERMINADOS
    else -> Filtro.EN_CURSO
}

fun progresoDe(c: Comic): Float =
    if (c.paginas > 1) c.ultimaPagina.toFloat() / (c.paginas - 1) else 0f
