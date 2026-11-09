package com.example.lector.ui

import androidx.compose.runtime.snapshotFlow
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import kotlin.math.floor
import kotlin.math.roundToInt
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
import com.example.lector.ui.mochy.library.HomeCardSpacing
import com.example.lector.ui.mochy.library.LibraryColors
import com.example.lector.ui.mochy.library.LibraryEmptyState
import com.example.lector.ui.mochy.library.LibraryGridTile
import com.example.lector.ui.mochy.library.LibraryIconButton
import com.example.lector.ui.mochy.library.LibraryListRow
import com.example.lector.ui.mochy.library.LibraryTabChips
import com.example.lector.ui.mochy.library.LibraryTitleBlock
import com.example.lector.ui.mochy.library.SearchHorizontalPadding
import com.example.lector.ui.mochy.library.TrackSubtitleStyle

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
    // Idioma por fuente, calculado una vez (antes se buscaba en toda la lista por cada manga en cada repintado).
    val idiomaDe = remember(fuentes) { fuentes.associate { it.id to it.lang } }
    fun nombreFuente(c: Comic) = if (!c.enLinea) "Local" else fuentes.firstOrNull { it.id == c.fuenteId }?.nombre ?: "Fuente ${c.fuenteId}"
    fun descargasDe(c: Comic) = if (!c.enLinea) 0 else descargas["${c.fuenteId}|${c.urlManga}"] ?: 0

    // ---- Qué se ve ----
    val ocultas = categorias.filter { it.oculta }.map { it.id }.toSet()
    val fusionadas = remember(ext.fusionVersion) { ext.fusionadas() }
    val visibles = remember(comics, asignaciones, ocultas, mostrarOcultas, fusionadas) {
        comics.filter { c -> (mostrarOcultas || asignaciones[c.id].orEmpty().none { it in ocultas }) && "${c.fuenteId}|${c.urlManga}" !in fusionadas }
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

    // Se vuelve a la pestaña en la que estabas (también al salir de la biblioteca o cerrar la app).
    val inicial = remember { grupos.indexOfFirst { it.clave == almacen.ultimaPestanaBiblioteca }.coerceAtLeast(0) }
    val pager = rememberPagerState(initialPage = inicial) { grupos.size }
    val gruposActuales by rememberUpdatedState(grupos)
    LaunchedEffect(pager) {
        snapshotFlow { pager.settledPage }.collect { p -> gruposActuales.getOrNull(p)?.let { almacen.ultimaPestanaBiblioteca = it.clave } }
    }
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
                if (leido) ext.marcarLeidos(c.fuenteId, url, conocidos) else ext.desmarcarLeidos(c.fuenteId, url, conocidos)
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
                    var caps = ficha.capitulos.asReversed().filter { !soloNoLeidos || !ext.esLeido(f.id, c.urlManga.orEmpty(), it.url) }
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
            val acentoCab = MaterialTheme.colorScheme.primary
            if (enSeleccion) {
                CabeceraSeleccion(
                    seleccion.size, onCerrar = { seleccion = emptySet() },
                    onTodo = { seleccion = seleccion + grupoActual.comics.map { it.id } },
                    onInvertir = { seleccion = grupoActual.comics.map { it.id }.toSet() - seleccion },
                )
            } else {
                Row(
                    Modifier.fillMaxWidth().padding(start = SearchHorizontalPadding, end = 16.dp, top = 14.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LibraryTitleBlock(
                        "Biblioteca", null,
                        Modifier.weight(1f).pressable(onLongClick = { mostrarOcultas = !mostrarOcultas }) {},
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val estiloSub = TrackSubtitleStyle.copy(fontSize = 12.sp)
                            Odometro("${filtrados.size}", estiloSub, color = LibraryColors.Secondary)
                            LText(if (filtrados.size == 1) " entrada" else " entradas", estiloSub, color = LibraryColors.Secondary, maxLines = 1)
                            if (mostrarOcultas) LText(" · ocultas visibles", estiloSub, color = acentoCab, maxLines = 1)
                        }
                    }
                    LibraryIconButton(if (buscando) Icons.Rounded.Close else Icons.Rounded.Search, "Buscar", buscando) {
                        buscando = !buscando; if (!buscando) consulta = ""
                    }
                    Spacer(Modifier.width(8.dp))
                    Box {
                        LibraryIconButton(Icons.Rounded.List, "Filtrar y ordenar", filtrosActivos > 0) { hoja = true }
                        if (filtrosActivos > 0) Box(
                            Modifier.align(Alignment.TopEnd).size(18.dp).clip(CircleShape).background(acentoCab),
                            Alignment.Center,
                        ) { LText("$filtrosActivos", LectorType.Label.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onPrimary) }
                    }
                    Spacer(Modifier.width(8.dp))
                    Box {
                        LibraryIconButton(Icons.Rounded.MoreVert, "Más", false) { menu = true }
                        MenuShiori(expanded = menu, onDismissRequest = { menu = false }) {
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
            AnimatedVisibility(
                visible = buscando && !enSeleccion,
                enter = if (Mov.reducido) fadeIn() else expandVertically(Muelles.espacial()) + fadeIn(Muelles.efectos()),
                exit = if (Mov.reducido) fadeOut() else shrinkVertically(Muelles.espacialRapido()) + fadeOut(Muelles.efectos()),
            ) {
                Box(Modifier.padding(horizontal = SearchHorizontalPadding - 8.dp, vertical = 6.dp)) {
                    CampoBusqueda(consulta, { consulta = it }, "Título, autor:, genero:, fuente:, -excluir")
                }
            }

            // ---- Pestañas: píldoras con un indicador de acento que se estira y se desliza con el pager ----
            if (grupos.size > 1) {
                val etiquetas = remember(grupos) { grupos.map { it.nombre } }
                val cuentas = remember(grupos) { grupos.map { it.comics.size } }
                LibraryTabChips(
                    etiquetas, if (almacen.conteoCategorias) cuentas else null,
                    pager.currentPage, modifier = Modifier.padding(top = 2.dp, bottom = 12.dp),
                    onSelect = { i ->
                        scope.launch {
                            if (Mov.reducido) pager.scrollToPage(i) else pager.animateScrollToPage(i, animationSpec = Muelles.espacialLento())
                        }
                    },
                )
            } else Spacer(Modifier.height(8.dp))

            // ---- Contenido ----
            PullToRefreshBox(
                isRefreshing = Actualizador.corriendo,
                onRefresh = { if (!Actualizador.corriendo) Actualizador.iniciar(ctx, grupoActual.categoriaId) },
                modifier = Modifier.weight(1f),
            ) {
                if (comics.isEmpty()) {
                    VacioBiblioteca("Tu biblioteca está vacía", "Añade cómics .cbz o mangas desde Explorar (corazón «En biblioteca»).", "Añadir cómic", alAnadir)
                } else HorizontalPager(state = pager, modifier = Modifier.fillMaxSize(), key = { grupos.getOrNull(it)?.clave ?: it }) { pagina ->
                    val g = grupos.getOrNull(pagina) ?: return@HorizontalPager
                    if (g.comics.isEmpty()) {
                        if (!q.vacia || filtrosActivos > 0) VacioBiblioteca("Sin resultados", "Nada coincide con la búsqueda o los filtros.")
                        else VacioBiblioteca("Categoría vacía", "Mantén pulsada una entrada de otra pestaña y usa «Categorías» para traerla aquí.")
                    } else if (almacen.vista == Vista.LISTA) {
                        val estadoLista = androidx.compose.foundation.lazy.rememberLazyListState()
                        Box(Modifier.fillMaxSize()) {
                        LazyColumn(Modifier.fillMaxSize(), state = estadoLista, contentPadding = PaddingValues(top = 2.dp, bottom = 190.dp)) {
                            itemsAnimados(g.comics, key = { it.id }) { c ->
                                LibraryListRow(
                                    c, almacen, c.id in seleccion, descargasDe(c), if (c.enLinea) idiomaDe[c.fuenteId] else null,
                                    onClick = { if (enSeleccion) alternar(c) else alAbrir(c) },
                                    onLargo = { alternar(c) }, onContinuar = { alLeer(c) },
                                )
                            }
                        }
                        BarraDesplazamiento(estadoLista)
                        }
                    } else {
                        val columnas = almacen.columnas
                        val estadoRejilla = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
                        Box(Modifier.fillMaxSize()) {
                        LazyVerticalGrid(
                            state = estadoRejilla,
                            columns = if (columnas == 0) GridCells.Adaptive(if (anchoPantalla > 600) 150.dp else 112.dp) else GridCells.Fixed(columnas),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(start = SearchHorizontalPadding, end = SearchHorizontalPadding, top = 4.dp, bottom = 190.dp),
                            horizontalArrangement = Arrangement.spacedBy(HomeCardSpacing),
                            verticalArrangement = Arrangement.spacedBy(if (almacen.vista == Vista.CUADRICULA_COMODA) 18.dp else HomeCardSpacing),
                        ) {
                            itemsAnimados(g.comics, key = { it.id }) { c ->
                                LibraryGridTile(
                                    c, almacen, almacen.vista, c.id in seleccion, descargasDe(c),
                                    if (c.enLinea) idiomaDe[c.fuenteId] else null,
                                    onClick = { if (enSeleccion) alternar(c) else alAbrir(c) },
                                    onLargo = { alternar(c) }, onContinuar = { alLeer(c) },
                                )
                            }
                        }
                        BarraDesplazamiento(estadoRejilla)
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
            MenuShiori(expanded = menuDescarga, onDismissRequest = { menuDescarga = false }) {
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
        DialogoAlerta(
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
        DialogoAlerta(
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
                        CasillaShiori(checked = tambienDescargas, onCheckedChange = { tambienDescargas = it })
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

// ───────────────────── Estado vacío ─────────────────────

/** LibraryEmptyState de Mochy: texto centrado y, si hay, la píldora de acción. */
@Composable
private fun VacioBiblioteca(titulo: String, texto: String, accion: String? = null, onAccion: (() -> Unit)? = null) {
    Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        LibraryEmptyState(Modifier.entrada(0).padding(top = 24.dp, bottom = 160.dp), texto, titulo, accion, onAccion)
    }
}
