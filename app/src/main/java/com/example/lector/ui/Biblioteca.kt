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
            val acentoCab = MaterialTheme.colorScheme.primary
            if (enSeleccion) {
                CabeceraSeleccion(
                    seleccion.size, onCerrar = { seleccion = emptySet() },
                    onTodo = { seleccion = seleccion + grupoActual.comics.map { it.id } },
                    onInvertir = { seleccion = grupoActual.comics.map { it.id }.toSet() - seleccion },
                )
            } else {
                Row(
                    Modifier.fillMaxWidth().padding(start = 22.dp, end = 14.dp, top = 16.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f).pressable(onLongClick = { mostrarOcultas = !mostrarOcultas }) {}) {
                        TituloAjustable("Biblioteca")
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Row(
                                Modifier.clip(CircleShape).background(acentoCab.copy(alpha = 0.18f)).padding(horizontal = 10.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Odometro("${filtrados.size}", LectorType.Label.copy(fontWeight = FontWeight.Bold), color = acentoCab)
                                LText(if (filtrados.size == 1) " entrada" else " entradas", LectorType.Label, color = acentoCab, maxLines = 1)
                            }
                            if (mostrarOcultas) {
                                Spacer(Modifier.width(8.dp))
                                LText("Ocultas visibles", LectorType.Label, color = LectorColors.Muted, maxLines = 1)
                            }
                        }
                    }
                    BotonCabeceraBib(if (buscando) Icons.Rounded.Close else Icons.Rounded.Search, "Buscar", buscando) {
                        buscando = !buscando; if (!buscando) consulta = ""
                    }
                    Spacer(Modifier.width(8.dp))
                    Box {
                        BotonCabeceraBib(Icons.Rounded.List, "Filtrar y ordenar", filtrosActivos > 0) { hoja = true }
                        if (filtrosActivos > 0) Box(
                            Modifier.align(Alignment.TopEnd).size(18.dp).clip(CircleShape).background(acentoCab),
                            Alignment.Center,
                        ) { LText("$filtrosActivos", LectorType.Label.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onPrimary) }
                    }
                    Spacer(Modifier.width(8.dp))
                    Box {
                        BotonCabeceraBib(Icons.Rounded.MoreVert, "Más", false) { menu = true }
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
                Box(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                    CampoBusqueda(consulta, { consulta = it }, "Título, autor:, genero:, fuente:, -excluir")
                }
            }

            // ---- Pestañas: píldoras con un indicador de acento que se estira y se desliza con el pager ----
            if (grupos.size > 1) {
                PestanasBiblioteca(grupos, pager, almacen.conteoCategorias) { i ->
                    scope.launch {
                        if (Mov.reducido) pager.scrollToPage(i) else pager.animateScrollToPage(i, animationSpec = Muelles.espacialLento())
                    }
                }
            } else Spacer(Modifier.height(8.dp))

            // ---- Contenido ----
            PullToRefreshBox(
                isRefreshing = Actualizador.corriendo,
                onRefresh = { if (!Actualizador.corriendo) Actualizador.iniciar(ctx, grupoActual.categoriaId) },
                modifier = Modifier.weight(1f),
            ) {
                if (comics.isEmpty()) {
                    VacioBiblioteca(Icons.Rounded.Favorite, "Tu biblioteca está vacía", "Añade cómics .cbz o mangas desde Explorar (corazón «En biblioteca»).", "Añadir cómic", alAnadir)
                } else HorizontalPager(state = pager, modifier = Modifier.fillMaxSize(), key = { grupos.getOrNull(it)?.clave ?: it }) { pagina ->
                    val g = grupos.getOrNull(pagina) ?: return@HorizontalPager
                    if (g.comics.isEmpty()) {
                        if (!q.vacia || filtrosActivos > 0) VacioBiblioteca(Icons.Rounded.Search, "Sin resultados", "Nada coincide con la búsqueda o los filtros.")
                        else VacioBiblioteca(Icons.Rounded.Star, "Categoría vacía", "Mantén pulsada una entrada de otra pestaña y usa «Categorías» para traerla aquí.")
                    } else if (almacen.vista == Vista.LISTA) {
                        val estadoLista = androidx.compose.foundation.lazy.rememberLazyListState()
                        Box(Modifier.fillMaxSize()) {
                        LazyColumn(Modifier.fillMaxSize(), state = estadoLista, contentPadding = PaddingValues(top = 6.dp, bottom = 190.dp)) {
                            itemsAnimados(g.comics, key = { it.id }) { c ->
                                FilaBiblioteca(
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
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 190.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(if (almacen.vista == Vista.CUADRICULA_COMODA) 12.dp else 12.dp),
                        ) {
                            itemsAnimados(g.comics, key = { it.id }) { c ->
                                TarjetaBiblioteca(
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

// ───────────────────── Cabecera y pestañas ─────────────────────

/** Botón redondo de la cabecera: tonal en reposo, de acento suave cuando su función está activa. */
@Composable
private fun BotonCabeceraBib(icono: ImageVector, desc: String, activo: Boolean, onClick: () -> Unit) {
    val acento = MaterialTheme.colorScheme.primary
    val fondo by animateColorAsState(if (activo) acento.copy(alpha = 0.22f) else LectorColors.PillFill, Muelles.efectos(), label = "fondoCab")
    val tinta by animateColorAsState(if (activo) acento else LectorColors.Text, Muelles.efectos(), label = "tintaCab")
    Box(Modifier.size(44.dp).pressableCon(0.88f, onClick).clip(CircleShape).background(fondo), Alignment.Center) {
        Icon(icono, desc, tint = tinta, modifier = Modifier.size(22.dp))
    }
}

private fun suaveBib(t: Float): Float = t * t * (3f - 2f * t)
private fun lerpBib(a: Float, b: Float, t: Float): Float = a + (b - a) * t

/**
 * Pestañas de categoría como píldoras. Debajo de todas hay un carril gris; el indicador de acento lleno se dibuja
 * siguiendo la posición del pager (página + fracción): el borde que va delante se adelanta y el de detrás le sigue,
 * así el indicador se estira y se recoge como una gota. Solo se repinta (no se recompone) al arrastrar.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PestanasBiblioteca(grupos: List<GrupoBiblioteca>, pager: PagerState, conteo: Boolean, onElegir: (Int) -> Unit) {
    val acento = MaterialTheme.colorScheme.primary
    val sobreAcento = MaterialTheme.colorScheme.onPrimary
    val carril = LectorColors.Card
    val n = grupos.size
    val scroll = rememberScrollState()
    // [izquierda, ancho] de cada pestaña, en px dentro de la fila.
    val bordes = remember(n) { mutableStateListOf(*Array(n * 2) { 0f }) }
    var anchoVista by remember { mutableIntStateOf(0) }
    val margen = with(LocalDensity.current) { 20.dp.toPx() }
    val actual = pager.currentPage.coerceIn(0, n - 1)

    LaunchedEffect(actual, anchoVista, bordes.getOrNull(actual * 2 + 1)) {
        val w = bordes.getOrNull(actual * 2 + 1) ?: return@LaunchedEffect
        if (w <= 0f || anchoVista == 0) return@LaunchedEffect
        val centro = margen + bordes[actual * 2] + w / 2f
        val destino = (centro - anchoVista / 2f).roundToInt().coerceIn(0, scroll.maxValue)
        runCatching { if (Mov.reducido) scroll.scrollTo(destino) else scroll.animateScrollTo(destino, Muelles.espacial()) }
    }

    Row(
        Modifier.fillMaxWidth().onSizeChanged { anchoVista = it.width }
            .horizontalScroll(scroll)
            .padding(horizontal = 20.dp, vertical = 10.dp)
            .drawBehind {
                if (n == 0) return@drawBehind
                val alto = size.height
                val radio = CornerRadius(alto / 2f)
                for (i in 0 until n) {
                    val w = bordes[i * 2 + 1]
                    if (w > 0f) drawRoundRect(carril, Offset(bordes[i * 2], 0f), Size(w, alto), radio)
                }
                val pos = (pager.currentPage + pager.currentPageOffsetFraction).coerceIn(0f, (n - 1).toFloat())
                val i0 = floor(pos).toInt().coerceIn(0, n - 1)
                val i1 = (i0 + 1).coerceAtMost(n - 1)
                val t = pos - i0
                val l0 = bordes[i0 * 2]; val r0 = l0 + bordes[i0 * 2 + 1]
                val l1 = bordes[i1 * 2]; val r1 = l1 + bordes[i1 * 2 + 1]
                val tDelante: Float
                val tDetras: Float
                if (Mov.reducido) { tDelante = t; tDetras = t } else {
                    tDelante = suaveBib((t * 1.6f).coerceIn(0f, 1f))
                    tDetras = suaveBib(((t - 0.375f) * 1.6f).coerceIn(0f, 1f))
                }
                val izq = lerpBib(l0, l1, tDetras)
                val der = lerpBib(r0, r1, tDelante)
                if (der > izq) drawRoundRect(acento, Offset(izq, 0f), Size(der - izq, alto), radio)
            },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        grupos.forEachIndexed { i, g ->
            val sel = i == actual
            val tinta by animateColorAsState(if (sel) sobreAcento else LectorColors.Text, Muelles.efectos(), label = "tintaPestana")
            Row(
                Modifier
                    .onPlaced { c ->
                        val x = c.positionInParent().x; val w = c.size.width.toFloat()
                        if (i * 2 + 1 < bordes.size) {
                            if (bordes[i * 2] != x) bordes[i * 2] = x
                            if (bordes[i * 2 + 1] != w) bordes[i * 2 + 1] = w
                        }
                    }
                    .pressableCon(0.92f) { onElegir(i) }
                    .heightIn(min = 40.dp)
                    .padding(horizontal = 18.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LText(g.nombre, LectorType.Body.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold), color = tinta, maxLines = 1)
                if (conteo) {
                    Spacer(Modifier.width(7.dp))
                    Box(
                        Modifier.clip(CircleShape).background(if (sel) sobreAcento.copy(alpha = 0.2f) else LectorColors.PillFill)
                            .padding(horizontal = 7.dp, vertical = 1.dp),
                    ) { LText("${g.comics.size}", LectorType.Label.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold), color = tinta, maxLines = 1) }
                }
            }
        }
    }
}

/** Estado vacío: icono grande en un círculo tonal con halo, título, texto y (si hay) una acción. */
@Composable
private fun VacioBiblioteca(icono: ImageVector, titulo: String, texto: String, accion: String? = null, onAccion: (() -> Unit)? = null) {
    val acento = MaterialTheme.colorScheme.primary
    Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 36.dp).padding(top = 72.dp, bottom = 180.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.entrada(0).size(148.dp).clip(CircleShape).background(acento.copy(alpha = 0.07f)), Alignment.Center) {
                Box(Modifier.size(108.dp).clip(CircleShape).background(acento.copy(alpha = 0.18f)), Alignment.Center) {
                    Icon(icono, null, tint = acento, modifier = Modifier.size(50.dp))
                }
            }
            Spacer(Modifier.height(24.dp))
            Text(titulo, Modifier.entrada(1), color = LectorColors.Text, style = LectorType.Hero, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text(texto, Modifier.entrada(2), color = LectorColors.Muted, style = LectorType.Body, textAlign = TextAlign.Center)
            if (accion != null && onAccion != null) {
                Spacer(Modifier.height(24.dp))
                BotonExpresivo(accion, Icons.Rounded.Add, onAccion, Modifier.entrada(3), EstiloBoton.RELLENO, alto = 52.dp)
            }
        }
    }
}

// ───────────────────── Tarjetas ─────────────────────

@Composable
private fun Insignias(c: Comic, almacen: Almacen, descargas: Int, modifier: Modifier) {
    val acento = MaterialTheme.colorScheme.primary
    val sobre = com.example.lector.textoSobre(acento)
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        if (!c.enLinea && almacen.insigniaLocal) InsigniaTexto("Local", Color(0xE62E7D6B), Color.White)
        if (c.enLinea && almacen.insigniaDescargas && descargas > 0) InsigniaTexto("↓ $descargas", Color(0xD9101012), Color.White)
        if (c.enLinea && almacen.insigniaSinLeer && c.sinLeer > 0) InsigniaTexto("${c.sinLeer}", acento, sobre)
        if (almacen.insigniaProgreso && !c.enLinea) {
            val p = (progresoDe(c) * 100).toInt()
            if (c.terminado()) InsigniaTexto("✓", acento, sobre)
            else if (p in 1..99) InsigniaTexto("$p%", acento, sobre)
        }
    }
}

@Composable
private fun InsigniaTexto(texto: String, fondo: Color, tinta: Color) {
    Box(Modifier.clip(CircleShape).background(fondo).padding(horizontal = 8.dp, vertical = 3.dp), Alignment.Center) {
        LText(texto, LectorType.Label.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold), color = tinta, maxLines = 1)
    }
}

@Composable
private fun BotonContinuar(onClick: () -> Unit, modifier: Modifier, tonal: Boolean = false) {
    val acento = MaterialTheme.colorScheme.primary
    Box(
        modifier.size(if (tonal) 42.dp else 34.dp).pressableCon(0.85f, onClick).clip(CircleShape)
            .background(if (tonal) acento.copy(alpha = 0.18f) else acento),
        Alignment.Center,
    ) {
        Icon(
            Icons.Rounded.PlayArrow, "Continuar leyendo",
            tint = if (tonal) acento else com.example.lector.textoSobre(acento), modifier = Modifier.size(22.dp),
        )
    }
}

/** Barra fina de progreso de acento pegada a la base de la portada. */
@Composable
private fun BarraProgresoPortada(p: Float, modifier: Modifier) {
    val acento = MaterialTheme.colorScheme.primary
    Box(modifier.fillMaxWidth().height(4.dp).background(Color(0x66000000))) {
        Box(Modifier.fillMaxWidth(p.coerceIn(0f, 1f)).fillMaxHeight().background(acento))
    }
}

@Composable
private fun TarjetaBiblioteca(
    c: Comic, almacen: Almacen, vista: Vista, seleccionado: Boolean, descargas: Int, lang: String?,
    onClick: () -> Unit, onLargo: () -> Unit, onContinuar: () -> Unit,
) {
    val acento = MaterialTheme.colorScheme.primary
    // Selección con muelle: la portada redondea más sus esquinas, se encoge un poco y aparece el borde de acento.
    val s by animateFloatAsState(if (seleccionado) 1f else 0f, if (Mov.reducido) snap() else Muelles.espacial(), label = "selTarjeta")
    val forma = SuaveShape(18.dp + 10.dp * s)
    val comoda = vista == Vista.CUADRICULA_COMODA
    val compacta = vista == Vista.CUADRICULA_COMPACTA
    val progreso = progresoDe(c)
    val fondoComoda = LectorColors.Card
    Column(
        Modifier.origenTransicion().press3D(5f, 0.97f).pressable(onLongClick = onLargo, onClick = onClick)
            .graphicsLayer { val e = 1f - 0.06f * s; scaleX = e; scaleY = e }
            .then(
                if (comoda) Modifier.clip(SuaveShape(24.dp))
                    .background(if (s > 0f) lerp(fondoComoda, acento.copy(alpha = 0.3f).compositeOver(fondoComoda), s) else fondoComoda)
                    .padding(6.dp)
                else Modifier,
            ),
    ) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(forma)
                .then(if (s > 0.01f) Modifier.border((3.5f * s).dp, acento, forma) else Modifier),
        ) {
            ComicCover(c, almacen, Modifier.fillMaxSize(), forma)
            if (s > 0.01f) Box(Modifier.fillMaxSize().background(acento.copy(alpha = 0.28f * s)))
            Insignias(c, almacen, descargas, Modifier.align(Alignment.TopStart).padding(7.dp))
            if (lang != null && almacen.insigniaIdioma) {
                Box(
                    Modifier.align(Alignment.TopEnd).padding(6.dp).size(26.dp).clip(CircleShape).background(Color(0x8C000000)),
                    Alignment.Center,
                ) { LText(bandera(lang), LectorType.Label.copy(fontSize = 13.sp)) }
            }
            if (compacta) {
                Column(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xF0000000))))
                        .padding(start = 10.dp, end = 10.dp, top = 34.dp, bottom = 12.dp),
                ) {
                    LText(c.titulo, LectorType.Body.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold), color = Color.White, maxLines = 2)
                    if (almacen.mostrarPagina) LText(c.etiquetaProgreso(), LectorType.Label.copy(fontSize = 11.sp), color = Color(0xCCFFFFFF), maxLines = 1)
                }
            }
            if (progreso > 0f && progreso < 1f) BarraProgresoPortada(progreso, Modifier.align(Alignment.BottomCenter))
            val puedeSeguir = almacen.botonContinuar && !seleccionado && !c.terminado() && (c.enLinea && c.paginas > 0 || !c.enLinea)
            if (puedeSeguir) {
                BotonContinuar(
                    onContinuar,
                    Modifier.align(if (compacta) Alignment.TopEnd else Alignment.BottomEnd).padding(8.dp)
                        .padding(top = if (compacta && lang != null && almacen.insigniaIdioma) 28.dp else 0.dp, bottom = if (!compacta) 4.dp else 0.dp),
                )
            }
            if (s > 0.01f) {
                Box(
                    Modifier.align(Alignment.Center).size(42.dp).graphicsLayer { scaleX = s; scaleY = s; alpha = s.coerceIn(0f, 1f) }
                        .clip(CircleShape).background(acento),
                    Alignment.Center,
                ) { Icon(Icons.Rounded.Check, null, tint = com.example.lector.textoSobre(acento), modifier = Modifier.size(24.dp)) }
            }
        }
        if (comoda) {
            Spacer(Modifier.height(8.dp))
            Text(
                c.titulo, Modifier.padding(horizontal = 4.dp), color = LectorColors.Text,
                style = LectorType.Body.copy(fontSize = 13.sp, lineHeight = 17.sp, fontWeight = FontWeight.Bold),
                maxLines = 2, minLines = 2, overflow = TextOverflow.Ellipsis,
            )
            if (almacen.mostrarPagina) LText(
                c.etiquetaProgreso(), LectorType.Label.copy(fontSize = 11.sp), Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                color = LectorColors.Muted, maxLines = 1,
            )
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun FilaBiblioteca(
    c: Comic, almacen: Almacen, seleccionado: Boolean, descargas: Int, lang: String?,
    onClick: () -> Unit, onLargo: () -> Unit, onContinuar: () -> Unit,
) {
    val acento = MaterialTheme.colorScheme.primary
    val s by animateFloatAsState(if (seleccionado) 1f else 0f, if (Mov.reducido) snap() else Muelles.espacial(), label = "selFila")
    val forma = SuaveShape(22.dp + 6.dp * s)
    val fondo = LectorColors.Card
    val progreso = progresoDe(c)
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
            .pressableCon(0.97f, onLargo, onClick)
            .clip(forma)
            .background(if (s > 0f) lerp(fondo, acento.copy(alpha = 0.22f).compositeOver(fondo), s) else fondo)
            .then(if (s > 0.01f) Modifier.border((2f * s).dp, acento, forma) else Modifier)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            ComicCover(c, almacen, Modifier.width(56.dp).aspectRatio(2f / 3f), SuaveShape(14.dp))
            if (s > 0.01f) Box(
                Modifier.matchParentSize().clip(SuaveShape(14.dp)).background(acento.copy(alpha = 0.55f * s)),
                Alignment.Center,
            ) { Icon(Icons.Rounded.Check, null, tint = com.example.lector.textoSobre(acento), modifier = Modifier.graphicsLayer { scaleX = s; scaleY = s }) }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            LText(c.titulo, LectorType.Section.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold), maxLines = 2)
            val linea = listOfNotNull(
                if (lang != null && almacen.insigniaIdioma) bandera(lang) else null,
                c.autor, if (almacen.mostrarPagina) c.etiquetaProgreso() else null,
            ).joinToString(" · ")
            if (linea.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
                LText(linea, LectorType.Label, color = LectorColors.Muted, maxLines = 1)
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f).height(5.dp).clip(CircleShape).background(LectorColors.PillFill)) {
                    Box(Modifier.fillMaxWidth(progreso.coerceIn(0f, 1f)).fillMaxHeight().clip(CircleShape).background(acento))
                }
                Spacer(Modifier.width(8.dp))
                Insignias(c, almacen, descargas, Modifier)
            }
        }
        if (almacen.botonContinuar && !seleccionado && !c.terminado()) {
            Spacer(Modifier.width(10.dp))
            BotonContinuar(onContinuar, Modifier, tonal = true)
        }
    }
}
