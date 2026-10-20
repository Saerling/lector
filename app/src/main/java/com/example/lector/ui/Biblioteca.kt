package com.example.lector.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.lector.Actualizador
import com.example.lector.Agrupar
import com.example.lector.Almacen
import com.example.lector.Comic
import com.example.lector.ConsultaBiblioteca
import com.example.lector.Descargas
import com.example.lector.Orden
import com.example.lector.Vista
import com.example.lector.estadoPublicacion
import com.example.lector.ext.ExtManager
import com.example.lector.ext.MangaFuente
import com.example.lector.ext.Motor
import com.example.lector.ext.bandera
import com.example.lector.nombreVista
import com.example.lector.progresoDe
import kotlinx.coroutines.launch

/** Una pestaña de la biblioteca: una categoría (o una fuente / un estado, según la agrupación). */
private class GrupoBiblioteca(val clave: String, val nombre: String, val comics: List<Comic>, val categoriaId: String?)

private fun nombreOrden(o: Orden) = when (o) {
    Orden.TITULO -> "Alfabético"
    Orden.ULTIMO_LEIDO -> "Leído por última vez"
    Orden.PROGRESO -> "Progreso"
    Orden.ANADIDO -> "Fecha de añadido"
    Orden.SIN_LEER -> "Capítulos sin leer"
    Orden.TOTAL_CAPITULOS -> "Total de capítulos"
    Orden.ULTIMO_CAPITULO -> "Último capítulo"
    Orden.ULTIMA_REVISION -> "Última revisión"
    Orden.ALEATORIO -> "Aleatorio"
}

private fun nombreAgrupar(a: Agrupar) = when (a) {
    Agrupar.CATEGORIA -> "Categoría"
    Agrupar.FUENTE -> "Fuente"
    Agrupar.ESTADO -> "Estado"
    Agrupar.NINGUNO -> "Sin agrupar"
}

/**
 * Biblioteca (como la de Komikku/TachiyomiSY):
 *  - pestañas por categoría (o agrupada por fuente o por estado) con su número de entradas;
 *  - filtros de tres estados, orden y opciones de vista en una hoja (Filtro / Ordenar / Mostrar);
 *  - insignias de capítulos sin leer, descargados, idioma y «Local», y botón de continuar sobre la portada;
 *  - búsqueda con operadores ("frase", -excluir, fuente:, autor:, genero:);
 *  - mantener pulsado = selección múltiple: categorías, marcar leído / no leído, descargar, migrar, borrar;
 *  - deslizar hacia abajo busca capítulos nuevos de la pestaña.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun BibliotecaScreen(
    comics: List<Comic>,
    almacen: Almacen,
    ext: ExtManager,
    alAbrir: (Comic) -> Unit,
    alLeer: (Comic) -> Unit,
    alAnadir: () -> Unit,
    alAnadirCarpeta: () -> Unit,
    alEditarCategorias: () -> Unit,
    alMigrar: (Comic) -> Unit,
    alEliminar: (Comic) -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var consulta by rememberSaveable { mutableStateOf("") }
    var buscando by rememberSaveable { mutableStateOf(false) }
    var mostrarOcultas by remember { mutableStateOf(false) }
    var seleccion by remember { mutableStateOf(emptySet<String>()) }
    var hoja by remember { mutableStateOf(false) }
    var pestanaHoja by remember { mutableIntStateOf(0) }
    var menu by remember { mutableStateOf(false) }
    var dialogoCategorias by remember { mutableStateOf(false) }
    var confirmarBorrado by remember { mutableStateOf(false) }
    var menuDescarga by remember { mutableStateOf(false) }
    var seguimiento by remember { mutableStateOf<Comic?>(null) }

    val version = almacen.version
    val categorias = remember(version) { almacen.categorias() }
    val asignaciones = remember(version) { almacen.asignaciones() }
    val conSeguimiento = remember(version) { almacen.idsConSeguimiento() }
    val descargas = remember(version, Descargas.version.value) { almacen.conteoDescargas() }
    val fuentes = ext.fuentes
    fun nombreFuente(c: Comic) = if (!c.enLinea) "Local" else fuentes.firstOrNull { it.id == c.fuenteId }?.nombre ?: "Fuente ${c.fuenteId}"
    fun descargasDe(c: Comic) = if (!c.enLinea) 0 else descargas["${c.fuenteId}|${c.urlManga}"] ?: 0

    // ---- Qué se ve ----
    val ocultas = categorias.filter { it.oculta }.map { it.id }.toSet()
    val visibles = remember(comics, asignaciones, ocultas, mostrarOcultas) {
        comics.filter { c -> mostrarOcultas || asignaciones[c.id].orEmpty().none { it in ocultas } }
    }
    val q = remember(consulta) { ConsultaBiblioteca(consulta) }
    val filtrados = remember(
        visibles, q, version, descargas, almacen.fDescargados, almacen.fSinLeer, almacen.fEmpezados,
        almacen.fTerminados, almacen.fSeguimiento, almacen.fLocales, fuentes,
    ) {
        visibles.filter { c ->
            (q.vacia || q.coincide(c, nombreFuente(c))) &&
                almacen.fDescargados.deja(!c.enLinea || descargasDe(c) > 0) &&
                almacen.fSinLeer.deja(c.sinLeer > 0) &&
                almacen.fEmpezados.deja(c.ultimaPagina > 0) &&
                almacen.fTerminados.deja(c.terminado()) &&
                almacen.fSeguimiento.deja(c.id in conSeguimiento) &&
                almacen.fLocales.deja(!c.enLinea)
        }
    }
    val orden = almacen.orden
    val asc = almacen.ordenAscendente
    val semilla = remember { System.currentTimeMillis() }
    fun ordenar(l: List<Comic>): List<Comic> {
        val r = when (orden) {
            Orden.TITULO -> {
                val claves = HashMap<String, String>(l.size)
                l.forEach { claves[it.id] = it.titulo.lowercase() }
                l.sortedWith { a, b -> com.example.lector.Cbz.compararNatural(claves.getValue(a.id), claves.getValue(b.id)) }
            }
            Orden.ULTIMO_LEIDO -> l.sortedBy { it.leido }
            Orden.PROGRESO -> l.sortedBy { progresoDe(it) }
            Orden.ANADIDO -> l.sortedBy { it.anadido }
            Orden.SIN_LEER -> l.sortedBy { it.sinLeer }
            Orden.TOTAL_CAPITULOS -> l.sortedBy { it.paginas }
            Orden.ULTIMO_CAPITULO -> l.sortedBy { it.ultimoCapitulo }
            Orden.ULTIMA_REVISION -> l.sortedBy { it.ultimaRevision }
            Orden.ALEATORIO -> l.shuffled(java.util.Random(semilla))
        }
        return if (asc) r else r.asReversed()
    }

    val agrupar = almacen.agrupar
    val grupos: List<GrupoBiblioteca> = remember(filtrados, agrupar, categorias, asignaciones, orden, asc, mostrarOcultas, fuentes) {
        when (agrupar) {
            Agrupar.CATEGORIA -> {
                val visiblesCats = categorias.filter { mostrarOcultas || !it.oculta }
                val idsCats = visiblesCats.map { it.id }.toSet()
                val sinCategoria = filtrados.filter { c -> asignaciones[c.id].orEmpty().none { it in idsCats } }
                buildList {
                    if (sinCategoria.isNotEmpty() || visiblesCats.isEmpty()) add(GrupoBiblioteca("", "Predeterminada", ordenar(sinCategoria), null))
                    visiblesCats.forEach { cat ->
                        add(GrupoBiblioteca(cat.id, cat.nombre + if (cat.oculta) " •" else "", ordenar(filtrados.filter { cat.id in asignaciones[it.id].orEmpty() }), cat.id))
                    }
                }
            }
            Agrupar.FUENTE -> filtrados.groupBy { if (it.enLinea) it.fuenteId else 0L }.map { (id, l) ->
                GrupoBiblioteca("f$id", if (id == 0L) "Local" else fuentes.firstOrNull { it.id == id }?.nombre ?: "Fuente $id", ordenar(l), null)
            }.sortedBy { it.nombre.lowercase() }
            Agrupar.ESTADO -> filtrados.groupBy { if (it.enLinea) estadoPublicacion(it.estado) else "Local" }.map { (n, l) ->
                GrupoBiblioteca("e$n", n, ordenar(l), null)
            }.sortedBy { it.nombre }
            Agrupar.NINGUNO -> listOf(GrupoBiblioteca("todo", "Biblioteca", ordenar(filtrados), null))
        }.ifEmpty { listOf(GrupoBiblioteca("", "Predeterminada", emptyList(), null)) }
    }

    val pager = rememberPagerState { grupos.size }
    val grupoActual = grupos.getOrNull(pager.currentPage.coerceAtMost(grupos.lastIndex)) ?: grupos.first()
    val seleccionados = remember(seleccion, comics) { comics.filter { it.id in seleccion } }
    val enSeleccion = seleccion.isNotEmpty()
    BackHandler(enabled = enSeleccion) { seleccion = emptySet() }
    BackHandler(enabled = !enSeleccion && buscando) { buscando = false; consulta = "" }

    fun alternar(c: Comic) { seleccion = if (c.id in seleccion) seleccion - c.id else seleccion + c.id }

    val filtrosActivos = listOf(almacen.fDescargados, almacen.fSinLeer, almacen.fEmpezados, almacen.fTerminados, almacen.fSeguimiento, almacen.fLocales)
        .count { it != com.example.lector.Tri.IGNORAR }

    // ---- Acciones de la selección ----
    fun marcar(leido: Boolean) {
        var sinDatos = 0
        seleccionados.forEach { c ->
            if (c.enLinea) {
                val url = c.urlManga.orEmpty()
                val conocidos = almacen.capitulosConocidos(c.fuenteId, url)
                if (conocidos.isEmpty()) { sinDatos++; return@forEach }
                if (leido) ext.marcarLeidos(c.fuenteId, conocidos) else ext.desmarcarLeidos(c.fuenteId, conocidos)
                almacen.actualizarProgresoEnLinea(c.fuenteId, url, if (leido) conocidos.size else 0, conocidos.size)
            } else {
                almacen.actualizarComic(c.id) { it.copy(ultimaPagina = if (leido) (it.paginas - 1).coerceAtLeast(0) else 0) }
            }
        }
        if (sinDatos > 0) Avisos.mostrar("$sinDatos manga(s) aún no tienen la lista de capítulos: ábrelos o busca actualizaciones")
        seleccion = emptySet()
    }

    fun descargar(soloNoLeidos: Boolean, maximo: Int?) {
        val motor = Motor.actual ?: return
        val enLinea = seleccionados.filter { it.enLinea }
        if (enLinea.isEmpty()) { Avisos.mostrar("Los cómics locales ya están en el teléfono"); return }
        seleccion = emptySet()
        Avisos.mostrar("Preparando ${enLinea.size} manga(s)…")
        scope.launch {
            var total = 0
            enLinea.forEach { c ->
                val f = ext.fuentes.firstOrNull { it.id == c.fuenteId } ?: return@forEach
                runCatching { motor.ficha(f, MangaFuente(c.urlManga.orEmpty(), c.titulo, c.portadaUrl)) }.getOrNull()?.let { ficha ->
                    var caps = ficha.capitulos.asReversed().filter { !soloNoLeidos || !ext.esLeido(f.id, it.url) }
                    if (maximo != null) caps = caps.take(maximo)
                    total += Descargas.encolar(ctx, f, c.urlManga.orEmpty(), c.titulo, caps, avisar = false)
                }
            }
            Avisos.mostrar(if (total == 0) "No había capítulos que descargar" else "$total capítulo(s) en la cola de descargas")
        }
    }

    val anchoPantalla = LocalConfiguration.current.screenWidthDp

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            // ---- Cabecera ----
            if (enSeleccion) {
                CabeceraSeleccion(
                    seleccion.size, onCerrar = { seleccion = emptySet() },
                    onTodo = { seleccion = seleccion + grupoActual.comics.map { it.id } },
                    onInvertir = { seleccion = grupoActual.comics.map { it.id }.toSet() - seleccion },
                )
            } else {
                Row(
                    Modifier.fillMaxWidth().padding(start = 24.dp, end = 8.dp, top = 12.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f).pressable(onLongClick = { mostrarOcultas = !mostrarOcultas }) {}) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            LText("Biblioteca", LectorType.Display, maxLines = 1)
                            Spacer(Modifier.width(8.dp))
                            LText("${filtrados.size}", LectorType.Body, Modifier.padding(bottom = 6.dp), color = LectorColors.Muted)
                        }
                        if (mostrarOcultas) LText("Categorías ocultas visibles", LectorType.Label, color = MaterialTheme.colorScheme.primary)
                    }
                    Box(Modifier.size(44.dp).pressable { buscando = !buscando; if (!buscando) consulta = "" }, Alignment.Center) {
                        Icon(if (buscando) Icons.Rounded.Close else Icons.Rounded.Search, "Buscar", tint = LectorColors.Text)
                    }
                    Box(Modifier.size(44.dp).pressable { hoja = true }, Alignment.Center) {
                        Icon(Icons.Rounded.List, "Filtrar y ordenar", tint = if (filtrosActivos > 0) MaterialTheme.colorScheme.primary else LectorColors.Text)
                    }
                    Box {
                        Box(Modifier.size(44.dp).pressable { menu = true }, Alignment.Center) {
                            Icon(Icons.Rounded.MoreVert, "Más", tint = LectorColors.Text)
                        }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("Buscar capítulos nuevos") }, onClick = { menu = false; Actualizador.iniciar(ctx) })
                            if (grupoActual.categoriaId != null) {
                                DropdownMenuItem(
                                    text = { Text("Actualizar «${grupoActual.nombre}»") },
                                    onClick = { menu = false; Actualizador.iniciar(ctx, grupoActual.categoriaId) },
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Abrir una entrada al azar") },
                                onClick = {
                                    menu = false
                                    grupoActual.comics.randomOrNull()?.let(alAbrir) ?: Avisos.mostrar("No hay nada en esta pestaña")
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(if (mostrarOcultas) "Esconder categorías ocultas" else "Mostrar categorías ocultas") },
                                onClick = { menu = false; mostrarOcultas = !mostrarOcultas },
                            )
                            DropdownMenuItem(text = { Text("Editar categorías") }, onClick = { menu = false; alEditarCategorias() })
                            DropdownMenuItem(text = { Text("Añadir cómic (.cbz)") }, onClick = { menu = false; alAnadir() })
                            DropdownMenuItem(text = { Text("Añadir carpeta de imágenes") }, onClick = { menu = false; alAnadirCarpeta() })
                        }
                    }
                }
            }
            if (buscando && !enSeleccion) {
                Box(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                    CampoBusqueda(consulta, { consulta = it }, "Título, autor:, genero:, fuente:, -excluir")
                }
            }

            // ---- Pestañas ----
            if (grupos.size > 1) {
                val estadoTabs = rememberLazyListState()
                LaunchedEffect(pager.currentPage) { runCatching { estadoTabs.animateScrollToItem(pager.currentPage) } }
                LazyRow(
                    state = estadoTabs,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    itemsIndexed(grupos, key = { _, g -> g.clave }) { i, g ->
                        val sel = i == pager.currentPage
                        val acento = MaterialTheme.colorScheme.primary
                        Row(
                            Modifier.pressable { scope.launch { pager.animateScrollToPage(i) } }.clip(CircleShape)
                                .background(if (sel) acento.copy(alpha = 0.22f) else LectorColors.Card)
                                .border(1.dp, if (sel) acento else LectorColors.Linea, CircleShape)
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            LText(g.nombre, LectorType.Body, color = if (sel) acento else LectorColors.Text, maxLines = 1)
                            if (almacen.conteoCategorias) {
                                Spacer(Modifier.width(6.dp))
                                LText("${g.comics.size}", LectorType.Label, color = if (sel) acento else LectorColors.Muted)
                            }
                        }
                    }
                }
            } else Spacer(Modifier.height(6.dp))

            // ---- Contenido ----
            PullToRefreshBox(
                isRefreshing = Actualizador.corriendo,
                onRefresh = { if (!Actualizador.corriendo) Actualizador.iniciar(ctx, grupoActual.categoriaId) },
                modifier = Modifier.weight(1f),
            ) {
                if (comics.isEmpty()) {
                    MensajeVacio("Tu biblioteca está vacía. Añade cómics .cbz o mangas desde Explorar (corazón «En biblioteca»).", "Añadir cómic", alAnadir)
                } else HorizontalPager(state = pager, modifier = Modifier.fillMaxSize(), key = { grupos.getOrNull(it)?.clave ?: it }) { pagina ->
                    val g = grupos.getOrNull(pagina) ?: return@HorizontalPager
                    if (g.comics.isEmpty()) {
                        MensajeVacio(if (!q.vacia || filtrosActivos > 0) "Nada coincide con la búsqueda o los filtros." else "Esta categoría está vacía.")
                    } else if (almacen.vista == Vista.LISTA) {
                        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 4.dp, bottom = 190.dp)) {
                            items(g.comics, key = { it.id }) { c ->
                                FilaBiblioteca(
                                    c, almacen, c.id in seleccion, descargasDe(c), if (c.enLinea) fuentes.firstOrNull { it.id == c.fuenteId }?.lang else null,
                                    onClick = { if (enSeleccion) alternar(c) else alAbrir(c) },
                                    onLargo = { alternar(c) }, onContinuar = { alLeer(c) },
                                )
                            }
                        }
                    } else {
                        val columnas = almacen.columnas
                        LazyVerticalGrid(
                            columns = if (columnas == 0) GridCells.Adaptive(if (anchoPantalla > 600) 150.dp else 112.dp) else GridCells.Fixed(columnas),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 190.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(if (almacen.vista == Vista.CUADRICULA_COMODA) 14.dp else 10.dp),
                        ) {
                            items(g.comics, key = { it.id }) { c ->
                                TarjetaBiblioteca(
                                    c, almacen, almacen.vista, c.id in seleccion, descargasDe(c),
                                    if (c.enLinea) fuentes.firstOrNull { it.id == c.fuenteId }?.lang else null,
                                    onClick = { if (enSeleccion) alternar(c) else alAbrir(c) },
                                    onLargo = { alternar(c) }, onContinuar = { alLeer(c) },
                                )
                            }
                        }
                    }
                }
            }
        }

        // ---- Barra de acciones de la selección ----
        val unoEnLinea = seleccionados.singleOrNull()?.takeIf { it.enLinea }
        val unoLocal = seleccionados.singleOrNull()?.takeIf { !it.enLinea }
        Box(Modifier.align(Alignment.BottomCenter)) {
            BarraAcciones(
                visible = enSeleccion,
                acciones = buildList {
                    add(AccionBarra(Icons.Rounded.Menu, "Categorías") { dialogoCategorias = true })
                    add(AccionBarra(Icons.Rounded.Check, "Leído") { marcar(true) })
                    add(AccionBarra(Iconos.OjoTachado, "No leído") { marcar(false) })
                    if (seleccionados.any { it.enLinea }) add(AccionBarra(Iconos.Descargar, "Descargar") { menuDescarga = true })
                    if (unoEnLinea != null) add(AccionBarra(Icons.Rounded.Refresh, "Migrar") { seleccion = emptySet(); alMigrar(unoEnLinea) })
                    if (unoLocal != null) add(AccionBarra(Icons.Rounded.Star, "Seguimiento") { seguimiento = unoLocal })
                    add(AccionBarra(Icons.Rounded.Delete, "Quitar") { confirmarBorrado = true })
                },
            )
            DropdownMenu(expanded = menuDescarga, onDismissRequest = { menuDescarga = false }) {
                DropdownMenuItem(text = { Text("Siguiente capítulo sin leer") }, onClick = { menuDescarga = false; descargar(true, 1) })
                DropdownMenuItem(text = { Text("Siguientes 5 sin leer") }, onClick = { menuDescarga = false; descargar(true, 5) })
                DropdownMenuItem(text = { Text("Todos los no leídos") }, onClick = { menuDescarga = false; descargar(true, null) })
                DropdownMenuItem(text = { Text("Todos") }, onClick = { menuDescarga = false; descargar(false, null) })
            }
        }
    }

    // ---- Hoja: Filtro / Ordenar / Mostrar ----
    if (hoja) {
        HojaLector({ hoja = false }) {
            PestanasPildora(listOf("Filtro", "Ordenar", "Mostrar"), pestanaHoja, { pestanaHoja = it })
            Column(Modifier.fillMaxWidth().heightIn(max = 520.dp).verticalScroll(rememberScrollState()).padding(bottom = 28.dp)) {
                when (pestanaHoja) {
                    0 -> {
                        FilaTri("Descargados", almacen.fDescargados) { almacen.fDescargados = it }
                        FilaTri("Sin leer", almacen.fSinLeer) { almacen.fSinLeer = it }
                        FilaTri("Empezados", almacen.fEmpezados) { almacen.fEmpezados = it }
                        FilaTri("Terminados", almacen.fTerminados) { almacen.fTerminados = it }
                        FilaTri("Con seguimiento (AniList)", almacen.fSeguimiento) { almacen.fSeguimiento = it }
                        FilaTri("Cómics locales", almacen.fLocales) { almacen.fLocales = it }
                        LText(
                            "Toca una vez para incluir (✓), otra para excluir (✕) y otra para ignorar.",
                            LectorType.Label, Modifier.padding(horizontal = 24.dp, vertical = 10.dp), color = LectorColors.Muted,
                        )
                    }
                    1 -> Orden.entries.forEach { o ->
                        FilaOrden(nombreOrden(o), orden == o, asc) {
                            if (orden == o) almacen.ordenAscendente = !asc else almacen.orden = o
                        }
                    }
                    else -> {
                        TituloSeccion("Vista")
                        Vista.entries.forEach { v -> FilaRadio(nombreVista(v), almacen.vista == v) { almacen.elegirVista(v) } }
                        if (almacen.vista != Vista.LISTA) {
                            TituloSeccion("Columnas")
                            FilaChips(listOf("Auto", "2", "3", "4", "5", "6"), listOf(0, 2, 3, 4, 5, 6).indexOf(almacen.columnas).coerceAtLeast(0)) {
                                almacen.elegirColumnas(listOf(0, 2, 3, 4, 5, 6)[it])
                            }
                        }
                        TituloSeccion("Agrupar en pestañas por")
                        FilaChips(Agrupar.entries.map { nombreAgrupar(it) }, agrupar.ordinal) { almacen.agrupar = Agrupar.entries[it] }
                        TituloSeccion("Insignias")
                        FilaSwitch("Capítulos sin leer", null, almacen.insigniaSinLeer) { almacen.insigniaSinLeer = it }
                        FilaSwitch("Capítulos descargados", null, almacen.insigniaDescargas) { almacen.insigniaDescargas = it }
                        FilaSwitch("Idioma de la fuente", null, almacen.insigniaIdioma) { almacen.insigniaIdioma = it }
                        FilaSwitch("Cómic local", null, almacen.insigniaLocal) { almacen.insigniaLocal = it }
                        FilaSwitch("Porcentaje de progreso", null, almacen.insigniaProgreso) { almacen.elegirInsigniaProgreso(it) }
                        TituloSeccion("Otros")
                        FilaSwitch("Botón de continuar leyendo", "Sobre la portada", almacen.botonContinuar) { almacen.botonContinuar = it }
                        FilaSwitch("Número de entradas en las pestañas", null, almacen.conteoCategorias) { almacen.conteoCategorias = it }
                        FilaSwitch("Mostrar el progreso", "«Pág. X de Y» o «Leídos X de Y»", almacen.mostrarPagina) { almacen.elegirMostrarPagina(it) }
                    }
                }
            }
        }
    }

    // ---- Categorías de los seleccionados (todos / algunos / ninguno) ----
    if (dialogoCategorias) {
        val ids = seleccionados.map { it.id }
        val inicial = remember {
            categorias.associate { cat ->
                val cuantos = ids.count { cat.id in asignaciones[it].orEmpty() }
                cat.id to when {
                    ids.isNotEmpty() && cuantos == ids.size -> ToggleableState.On
                    cuantos == 0 -> ToggleableState.Off
                    else -> ToggleableState.Indeterminate
                }
            }
        }
        var estados by remember { mutableStateOf(inicial) }
        AlertDialog(
            onDismissRequest = { dialogoCategorias = false },
            title = { Text("Categorías") },
            text = {
                Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
                    if (categorias.isEmpty()) Text("Aún no hay categorías. Pulsa «Editar» para crearlas.")
                    categorias.forEach { cat ->
                        val e = estados[cat.id] ?: ToggleableState.Off
                        Row(
                            Modifier.fillMaxWidth().pressable {
                                estados = estados + (cat.id to if (e == ToggleableState.On) ToggleableState.Off else ToggleableState.On)
                            }.padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TriStateCheckbox(state = e, onClick = null)
                            Spacer(Modifier.width(8.dp))
                            Text(cat.nombre + if (cat.oculta) " (oculta)" else "")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val agregar = estados.filter { it.value == ToggleableState.On }.keys
                    val quitar = estados.filter { it.value == ToggleableState.Off }.keys
                    almacen.modificarCategorias(ids.toSet(), agregar, quitar)
                    seleccion = emptySet()
                    dialogoCategorias = false
                }) { Text("Guardar") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { dialogoCategorias = false; alEditarCategorias() }) { Text("Editar") }
                    TextButton(onClick = { dialogoCategorias = false }) { Text("Cancelar") }
                }
            },
        )
    }

    // ---- Quitar de la biblioteca ----
    if (confirmarBorrado) {
        var tambienDescargas by remember { mutableStateOf(false) }
        val unico = seleccionados.singleOrNull()
        AlertDialog(
            onDismissRequest = { confirmarBorrado = false },
            title = { Text("Quitar de la biblioteca") },
            text = {
                Column {
                    Text(if (unico != null) "¿Quitar «${unico.titulo}»?" else "¿Quitar ${seleccionados.size} entradas?")
                    if (seleccionados.any { !it.enLinea }) {
                        Spacer(Modifier.height(6.dp))
                        Text("Los cómics locales se borran de la app (el archivo original de tu teléfono no se toca).", style = MaterialTheme.typography.bodySmall)
                    }
                    if (seleccionados.any { it.enLinea }) Row(
                        Modifier.fillMaxWidth().pressable { tambienDescargas = !tambienDescargas }.padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = tambienDescargas, onCheckedChange = { tambienDescargas = it })
                        Text("Borrar también los capítulos descargados")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    seleccionados.forEach { c ->
                        if (tambienDescargas && c.enLinea) almacen.borrarDescargasDe(c.fuenteId, c.urlManga.orEmpty())
                        alEliminar(c)
                    }
                    seleccion = emptySet()
                    confirmarBorrado = false
                }) { Text("Quitar") }
            },
            dismissButton = { TextButton(onClick = { confirmarBorrado = false }) { Text("Cancelar") } },
        )
    }

    seguimiento?.let { c -> DialogoSeguimientoComic(c, almacen) { seguimiento = null; seleccion = emptySet() } }
}

// ───────────────────── Tarjetas ─────────────────────

@Composable
private fun Insignias(c: Comic, almacen: Almacen, descargas: Int, modifier: Modifier) {
    val acento = MaterialTheme.colorScheme.primary
    Row(modifier.clip(SuaveShape(8.dp)), verticalAlignment = Alignment.CenterVertically) {
        if (!c.enLinea && almacen.insigniaLocal) InsigniaTexto("Local", Color(0xFF2E7D6B), Color.White)
        if (c.enLinea && almacen.insigniaDescargas && descargas > 0) InsigniaTexto("$descargas", Color(0xFF3D7BD9), Color.White)
        if (c.enLinea && almacen.insigniaSinLeer && c.sinLeer > 0) InsigniaTexto("${c.sinLeer}", acento, com.example.lector.textoSobre(acento))
        if (almacen.insigniaProgreso && !c.enLinea) {
            val p = (progresoDe(c) * 100).toInt()
            if (c.terminado()) InsigniaTexto("✓", acento, com.example.lector.textoSobre(acento))
            else if (p in 1..99) InsigniaTexto("$p%", acento, com.example.lector.textoSobre(acento))
        }
    }
}

@Composable
private fun InsigniaTexto(texto: String, fondo: Color, tinta: Color) {
    Box(Modifier.background(fondo).padding(horizontal = 6.dp, vertical = 2.dp)) {
        LText(texto, LectorType.Label.copy(fontSize = 11.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold), color = tinta, maxLines = 1)
    }
}

@Composable
private fun BotonContinuar(onClick: () -> Unit, modifier: Modifier) {
    Box(
        modifier.size(30.dp).pressable(onClick).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.92f)),
        Alignment.Center,
    ) { Icon(Icons.Rounded.PlayArrow, "Continuar leyendo", tint = com.example.lector.textoSobre(MaterialTheme.colorScheme.primary), modifier = Modifier.size(20.dp)) }
}

@Composable
private fun TarjetaBiblioteca(
    c: Comic, almacen: Almacen, vista: Vista, seleccionado: Boolean, descargas: Int, lang: String?,
    onClick: () -> Unit, onLargo: () -> Unit, onContinuar: () -> Unit,
) {
    val acento = MaterialTheme.colorScheme.primary
    val forma = SuaveShape(14.dp)
    Column(Modifier.press3D(5f, 0.97f).pressable(onLongClick = onLargo, onClick = onClick)) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(forma)
                .then(if (seleccionado) Modifier.border(3.dp, acento, forma) else Modifier),
        ) {
            ComicCover(c, almacen, Modifier.fillMaxSize(), forma)
            if (seleccionado) Box(Modifier.fillMaxSize().background(acento.copy(alpha = 0.25f)))
            Insignias(c, almacen, descargas, Modifier.align(Alignment.TopStart).padding(6.dp))
            if (lang != null && almacen.insigniaIdioma) {
                LText(bandera(lang), LectorType.Body, Modifier.align(Alignment.TopEnd).padding(6.dp))
            }
            if (vista == Vista.CUADRICULA_COMPACTA) {
                Column(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xE6000000))))
                        .padding(start = 8.dp, end = 8.dp, top = 28.dp, bottom = 8.dp),
                ) {
                    LText(c.titulo, LectorType.Body.copy(fontSize = 13.sp), color = Color.White, maxLines = 2)
                    if (almacen.mostrarPagina) LText(c.etiquetaProgreso(), LectorType.Label.copy(fontSize = 11.sp), color = Color(0xCCFFFFFF), maxLines = 1)
                }
            }
            val puedeSeguir = almacen.botonContinuar && !seleccionado && !c.terminado() && (c.enLinea && c.paginas > 0 || !c.enLinea)
            if (puedeSeguir) {
                BotonContinuar(onContinuar, Modifier.align(if (vista == Vista.CUADRICULA_COMPACTA) Alignment.TopEnd else Alignment.BottomEnd).padding(6.dp).padding(top = if (vista == Vista.CUADRICULA_COMPACTA && lang != null && almacen.insigniaIdioma) 26.dp else 0.dp))
            }
            if (seleccionado) {
                Box(
                    Modifier.align(Alignment.Center).size(34.dp).clip(CircleShape).background(acento),
                    Alignment.Center,
                ) { Icon(Icons.Rounded.Check, null, tint = com.example.lector.textoSobre(acento)) }
            }
        }
        if (vista == Vista.CUADRICULA_COMODA) {
            Spacer(Modifier.height(6.dp))
            LText(c.titulo, LectorType.Body.copy(fontSize = 13.sp), maxLines = 2)
            if (almacen.mostrarPagina) LText(c.etiquetaProgreso(), LectorType.Label.copy(fontSize = 11.sp), color = LectorColors.Muted, maxLines = 1)
        }
    }
}

@Composable
private fun FilaBiblioteca(
    c: Comic, almacen: Almacen, seleccionado: Boolean, descargas: Int, lang: String?,
    onClick: () -> Unit, onLargo: () -> Unit, onContinuar: () -> Unit,
) {
    val acento = MaterialTheme.colorScheme.primary
    Row(
        Modifier.fillMaxWidth()
            .background(if (seleccionado) acento.copy(alpha = 0.16f) else Color.Transparent)
            .resaltable(onLongClick = onLargo, onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ComicCover(c, almacen, Modifier.width(54.dp).aspectRatio(2f / 3f), SuaveShape(10.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            LText(c.titulo, LectorType.Section.copy(fontSize = 16.sp), maxLines = 2)
            val linea = listOfNotNull(
                if (lang != null && almacen.insigniaIdioma) bandera(lang) else null,
                c.autor, if (almacen.mostrarPagina) c.etiquetaProgreso() else null,
            ).joinToString(" · ")
            if (linea.isNotEmpty()) LText(linea, LectorType.Label, color = LectorColors.Muted, maxLines = 1)
            Spacer(Modifier.height(6.dp))
            ProgressLine(progresoDe(c))
        }
        Spacer(Modifier.width(10.dp))
        Insignias(c, almacen, descargas, Modifier)
        if (almacen.botonContinuar && !seleccionado && !c.terminado()) {
            Spacer(Modifier.width(8.dp))
            BotonContinuar(onContinuar, Modifier)
        }
    }
}
