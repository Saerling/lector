package com.example.lector.ui

import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.example.lector.ui.mochy.AppTypography
import com.example.lector.ui.mochy.GlassState
import com.example.lector.ui.mochy.glassRoot
import com.example.lector.ui.mochy.search.*
import android.content.Context
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.example.lector.ext.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

private val Rojo get() = LectorColors.Error

/** Pestaña "Explorar", como en Komikku: Fuentes · Novedades · Extensiones · Migrar. */
@Composable
fun ExplorarScreen(
    ext: ExtManager,
    onRuta: (Ruta) -> Unit,
    onFuente: (FuenteUI) -> Unit,
    onExt: (ExtInstalada) -> Unit,
    onManga: (FuenteUI, MangaFuente) -> Unit,
    almacen: com.example.lector.Almacen? = null,
    onMigrar: (com.example.lector.Comic) -> Unit = {},
    onBuscarGlobal: () -> Unit = {},
) {
    var sub by rememberSaveable { mutableStateOf(0) }
    var reordenar by rememberSaveable { mutableStateOf(false) }
    var dialogoNovedades by remember { mutableStateOf(false) }
    var consulta by rememberSaveable { mutableStateOf("") }
    var buscando by rememberSaveable { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var dialogoIdiomas by remember { mutableStateOf(false) }
    var porConfiar by remember { mutableStateOf<ExtInstalada?>(null) }
    val acento = MaterialTheme.colorScheme.primary

    // Como la pestaña Buscar de Mochy: título grande que se pliega con desenfoque, campo de búsqueda en píldora y
    // cristal progresivo cuando el contenido pasa por debajo. Las sub-pestañas son las píldoras de categorías.
    val glassState = remember { GlassState() }
    val (plegado, collapse) = rememberNestedCollapseProgress(sub)
    val progressProvider = remember(collapse) { { collapse.value } }
    val focusManager = LocalFocusManager.current
    val filtra = sub == 0 || sub == 2
    var campo by remember { mutableStateOf(TextFieldValue(consulta, TextRange(consulta.length))) }
    LaunchedEffect(consulta) { if (campo.text != consulta) campo = TextFieldValue(consulta, TextRange(consulta.length)) }
    val alto = searchTopBarHeight(withTitle = true, extra = 50.dp, titleBlockHeight = LibraryTitleBlockHeight)

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier.fillMaxSize()
                // recorded while scrolling too, so the bar's blur never drops out mid-scroll
                .glassRoot(glassState, isActive = { progressProvider() > 0.01f })
                .nestedScroll(plegado)
        ) {
            CompositionLocalProvider(LocalArriba provides alto) {
            AnimatedContent(
                targetState = sub,
                transitionSpec = {
                    if (Mov.reducido) fadeIn(tween(120)) togetherWith fadeOut(tween(100))
                    else {
                        val dir = if (targetState > initialState) 1 else -1
                        (slideInHorizontally(Muelles.espacial()) { it / 5 * dir } + fadeIn(Muelles.efectosLento())) togetherWith
                            (slideOutHorizontally(Muelles.espacial()) { -it / 5 * dir } + fadeOut(tween(140)))
                    }
                },
                label = "subExplorar",
            ) { pestana ->
                when (pestana) {
                    0 -> FuentesTab(ext, consulta, { consulta = it }, onFuente) { sub = 2 }
                    1 -> NovedadesTab(ext, reordenar, onManga, onFuente) { dialogoNovedades = true }
                    2 -> ExtensionesTab(
                        ext, consulta, false, { consulta = it }, onExt,
                        onConfiar = { porConfiar = it }, onRepos = { onRuta(Ruta.ListadosExt) }
                    )
                    else -> MigrarTab(ext, almacen, onMigrar)
                }
            }
            }
        }

        val nFuentes = ext.fuentes.size
        val nExt = ext.instaladas.size
        val pendientes = remember(ext.instaladas) { ext.instaladas.count { it.actualizacion != null } }
        val resumen = remember(nFuentes, nExt, pendientes) {
            "$nFuentes fuentes · $nExt extensiones" + if (pendientes > 0) " · $pendientes por actualizar" else ""
        }
        SearchGlassTopBar(
            glassState = glassState,
            progressProvider = progressProvider,
            pureBlack = TemaActual.puro,
            title = "Explorar",
            subtitle = resumen,
            titleBlockHeight = LibraryTitleBlockHeight,
            trailing = if (sub == 3) null else {
                {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (sub == 1) {
                            if (reordenar) {
                                BotonCabecera(Icons.Rounded.Check, "Terminar", activo = true) { reordenar = false }
                            } else {
                                BotonCabecera(Icons.Rounded.Add, "Añadir fuente") { dialogoNovedades = true }
                                BotonCabecera(Icons.Rounded.Menu, "Ordenar") { reordenar = true }
                            }
                        }
                        if (sub == 0) BotonCabecera(Icons.Rounded.Search, "Búsqueda global") { onBuscarGlobal() }
                        if (sub == 0 || sub == 2) {
                            val on = ext.mostrarNsfw
                            val fondo by animateColorAsState(if (on) SearchColors.Primary else SearchColors.TileHigh, Muelles.efectos(), label = "nsfw")
                            val color by animateColorAsState(if (on) SearchColors.page(false) else SearchColors.Secondary, Muelles.efectos(), label = "nsfwTinta")
                            Box(
                                Modifier.height(AvatarSize).pressable { Tacto.tic(); ext.alternarNsfw() }.clip(CircleShape)
                                    .background(fondo).padding(horizontal = 10.dp),
                                Alignment.Center
                            ) { LText("18+", LectorType.Body.copy(fontWeight = FontWeight.Bold, fontSize = 13.sp), color = color) }
                            Box {
                                BotonCabecera(Icons.Rounded.MoreVert, "Más") { menu = true }
                                MenuShiori(expanded = menu, onDismissRequest = { menu = false }) {
                                    DropdownMenuItem(text = { Text("Búsqueda global") }, onClick = { menu = false; onBuscarGlobal() })
                                    DropdownMenuItem(text = { Text("Listados de extensiones") }, onClick = { menu = false; onRuta(Ruta.ListadosExt) })
                                    DropdownMenuItem(text = { Text("Idiomas") }, onClick = { menu = false; dialogoIdiomas = true })
                                    DropdownMenuItem(text = { Text("Actualizar lista") }, onClick = { menu = false; ext.actualizarLista() })
                                }
                            }
                        }
                    }
                }
            },
            below = { PestanasExplorar(sub) { i -> sub = i; consulta = ""; buscando = false; reordenar = false } },
        ) {
            // En Fuentes y Extensiones el campo filtra la lista; en Novedades y Migrar abre la búsqueda global.
            SearchField(
                value = if (filtra) campo else TextFieldValue(""),
                onValueChange = { campo = it; consulta = it.text; buscando = it.text.isNotEmpty() },
                onSearch = { focusManager.clearFocus() },
                placeholder = when (sub) { 0 -> "Buscar fuentes"; 2 -> "Buscar extensiones"; else -> "Buscar en todas las fuentes" },
                enabled = filtra,
                readOnlyClick = if (filtra) null else onBuscarGlobal,
                leading = { SearchFieldGlyph(Icons.Rounded.Search, null) },
                trailing = if (filtra && campo.text.isNotEmpty()) {
                    { SearchFieldGlyph(Icons.Rounded.Close, "Borrar") { campo = TextFieldValue(""); consulta = ""; buscando = false } }
                } else {
                    null
                },
            )
        }
    }

    if (dialogoNovedades) DialogoNovedades(ext) { dialogoNovedades = false }

    porConfiar?.let { e ->
        DialogoAlerta(
            onDismissRequest = { porConfiar = null },
            title = { Text("¿Confiar en «${e.nombre}»?") },
            text = {
                Text("Una extensión es código de terceros que se ejecuta con los permisos de esta app. Confía solo en las que vengan de un listado que conozcas.")
            },
            confirmButton = { TextButton(onClick = { ext.confiar(e); porConfiar = null }) { Text("Confiar") } },
            dismissButton = { TextButton(onClick = { porConfiar = null }) { Text("Cancelar") } },
        )
    }

    if (dialogoIdiomas) {
        val todos = (ext.disponibles.map { it.lang } + ext.instaladas.map { it.lang } + ext.idiomas + "all")
            .filter { it.isNotBlank() }.distinct().sortedBy { nombreIdioma(it) }
        DialogoAlerta(
            onDismissRequest = { dialogoIdiomas = false },
            title = { Text("Idiomas") },
            text = {
                Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState())) {
                    todos.forEach { l ->
                        Row(
                            Modifier.fillMaxWidth().clickable { ext.alternarIdioma(l) }.padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CasillaShiori(checked = l in ext.idiomas, onCheckedChange = null)
                            Spacer(Modifier.width(12.dp))
                            Text("${bandera(l)}  ${nombreIdioma(l)}")
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { dialogoIdiomas = false }) { Text("Listo") } },
            dismissButton = { TextButton(onClick = { ext.activarTodosIdiomas() }) { Text("Todos") } },
        )
    }
}

/** Sub-pestañas como las categorías de la búsqueda de Mochy: píldoras, la elegida en blanco. */
@Composable
private fun PestanasExplorar(elegida: Int, onElegir: (Int) -> Unit) {
    val titulos = remember { listOf("Fuentes", "Novedades", "Extensiones", "Migrar") }
    SearchCategoryRow(titulos, elegida, { i -> if (i != elegida) { Tacto.tic(); onElegir(i) } })
}

/** Alto de la barra de cristal de Explorar: el contenido de cada sub-pestaña empieza debajo y pasa por detrás. */
private val LocalArriba = compositionLocalOf { 0.dp }

// ───────────────────────── Fuentes ─────────────────────────

@Composable
private fun FuentesTab(
    ext: ExtManager, consulta: String, onConsulta: (String) -> Unit,
    onFuente: (FuenteUI) -> Unit, irExtensiones: () -> Unit,
) {
    val todas = ext.fuentes
    val q = consulta.trim()
    // Con cientos de fuentes: los filtros y agrupaciones solo se rehacen cuando cambia lo que los afecta.
    val visibles = remember(todas, ext.mostrarNsfw, ext.idiomas, q) {
        todas.filter {
            (ext.mostrarNsfw || !it.nsfw) && it.lang in ext.idiomas &&
                (q.isEmpty() || it.nombre.contains(q, ignoreCase = true))
        }
    }
    val fijadas = ext.fijadas
    val ancladas = remember(visibles, fijadas) { visibles.filter { it.id.toString() in fijadas } }
    val recientes = ext.recientes
    val usadas = remember(visibles, recientes) {
        val porId = visibles.associateBy { it.id.toString() }
        recientes.mapNotNull { porId[it] }.take(1)
    }
    val resto = remember(visibles, fijadas) {
        visibles.filterNot { it.id.toString() in fijadas }
            .groupBy { it.lang }.toSortedMap(compareBy<String>({ nombreIdioma(it) }, { it }))
    }
    val abajo = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Column(Modifier.fillMaxSize()) {
        if (todas.isEmpty()) {
            Vacio(
                "Todavía no hay fuentes. Instala una extensión, pulsa «Confiar» y sus fuentes aparecerán aquí.",
                "Ir a Extensiones", irExtensiones, Icons.Rounded.Build,
            )
        } else if (visibles.isEmpty()) {
            Vacio("Ninguna fuente coincide con «$q».", icono = Icons.Rounded.Search)
        } else {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = LocalArriba.current, bottom = abajo + 190.dp)) {
                if (usadas.isNotEmpty()) {
                    item(key = "t_rec", contentType = "titulo") { SearchSectionHeader("Seguir explorando", ruleAbove = false) }
                    items(usadas, key = { "r${it.id}" }, contentType = { "reciente" }) { f ->
                        TarjetaReciente(f, f.id.toString() in fijadas, { ext.alternarFijada(f.id) }) { onFuente(f) }
                    }
                }
                // a row's own rule already closes the previous section
                if (ancladas.isNotEmpty()) {
                    item(key = "t_anc", contentType = "titulo") {
                        SearchSectionHeader("Ancladas", ruleAbove = usadas.isNotEmpty(), trailing = { SearchCount(ancladas.size) })
                    }
                    items(ancladas, key = { "a${it.id}" }, contentType = { "fuente" }) { FilaFuente(it, true, ext, onFuente) }
                }
                var primera = usadas.isEmpty() && ancladas.isEmpty()
                resto.forEach { (lang, lista) ->
                    val regla = !primera
                    primera = false
                    item(key = "t_$lang", contentType = "titulo") {
                        SearchSectionHeader(
                            nombreIdioma(lang), ruleAbove = false,
                            modifier = Modifier.padding(top = if (regla) 22.dp else 0.dp),
                            trailing = { SearchCount(lista.size) },
                        )
                    }
                    items(lista, key = { "f${it.id}" }, contentType = { "fuente" }) { FilaFuente(it, false, ext, onFuente) }
                }
            }
        }
    }
}

/** La última fuente usada: la tarjeta de «Top Results» de Mochy, con su icono grande y el botón blanco para entrar. */
@Composable
private fun TarjetaReciente(f: FuenteUI, fijada: Boolean, onFijar: () -> Unit, onAbrir: () -> Unit) {
    val detalle = remember(f.lang, f.nsfw) {
        listOfNotNull("Usada hace poco", "${bandera(f.lang)} ${nombreIdioma(f.lang)}", "+18".takeIf { f.nsfw }).joinToString("  •  ")
    }
    TopResultCard(
        artwork = { IconoExt(f.pkg, null, Modifier.size(72.dp)) },
        title = f.nombre,
        detail = detalle,
        onClick = onAbrir,
        actionIcon = Icons.Rounded.ArrowForward,
        actionDescription = "Abrir",
        extra = { BotonEstrella(fijada, onFijar) },
        modifier = Modifier.padding(bottom = 22.dp),
    )
}

@Composable
private fun FilaFuente(f: FuenteUI, fijada: Boolean, ext: ExtManager, onFuente: (FuenteUI) -> Unit) {
    val subtitulo = remember(f.lang, f.nsfw) {
        listOfNotNull("${bandera(f.lang)} ${nombreIdioma(f.lang)}", "+18".takeIf { f.nsfw }).joinToString("  •  ")
    }
    ResultRow(
        artwork = { IconoExt(f.pkg, null, Modifier.size(46.dp)) },
        title = f.nombre,
        subtitle = subtitulo,
        onClick = { onFuente(f) },
        trailing = { BotonEstrella(fijada) { ext.alternarFijada(f.id) } },
    )
}

/** Anclar: el glifo redondo de 32 dp de las filas de Mochy; la estrella se enciende (con un pequeño salto) al anclar. */
@Composable
private fun BotonEstrella(fijada: Boolean, onClick: () -> Unit) {
    val acento = MaterialTheme.colorScheme.primary
    val tinta by animateColorAsState(if (fijada) acento else SearchColors.Secondary.copy(alpha = 0.55f), Muelles.efectos(), label = "estrellaTinta")
    val escala by animateFloatAsState(
        if (fijada) 1f else 0.86f, if (Mov.reducido) tween(100) else spring(0.45f, 600f), label = "estrellaEscala"
    )
    SearchRowGlyph(
        Icons.Rounded.Star, if (fijada) "Desanclar" else "Anclar", { Tacto.tic(); onClick() },
        Modifier.graphicsLayer { scaleX = escala; scaleY = escala }, tint = tinta,
    )
}

// ───────────────────────── Novedades ─────────────────────────

/**
 * Lo cargado en Novedades: se pide una vez por sesión de la app y se reutiliza al cambiar de pestaña o de pantalla.
 * Vuelve a pedirse al deslizar hacia abajo ([recargar]) o al abrir la app de nuevo ([olvidar], desde MainActivity).
 */
/** Búsqueda que se aplicará al abrir la extensión desde un chip de Novedades. */
object BusquedaPendiente {
    var consulta: String? = null
    var guardada: ExtManager.BusquedaGuardada? = null
    fun tomar(): String? = consulta.also { consulta = null }
}

object NovedadesGuardadas {
    // id de la fuente → (generación en la que se cargó, mangas)
    val datos = androidx.compose.runtime.mutableStateMapOf<Long, Pair<Int, List<MangaFuente>>>()
    // id de la fuente → generación en la que falló
    val fallos = androidx.compose.runtime.mutableStateMapOf<Long, Int>()
    var generacion by mutableStateOf(0); private set

    fun recargar() { generacion++ }
    fun olvidar() { datos.clear(); fallos.clear(); generacion++ }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun NovedadesTab(
    ext: ExtManager,
    reordenar: Boolean,
    onManga: (FuenteUI, MangaFuente) -> Unit,
    onFuente: (FuenteUI) -> Unit,
    onAnadir: () -> Unit,
) {
    val motor = Motor.actual
    val todas = remember(ext.novedades, ext.fuentes) {
        val porId = ext.fuentes.associateBy { it.id }
        ext.novedades.mapNotNull { porId[it] }
    }
    val lista = remember(todas, ext.mostrarNsfw) { todas.filter { ext.mostrarNsfw || !it.nsfw } }
    when {
        todas.isEmpty() -> Vacio("Aquí salen las novedades de las fuentes que elijas.", "Añadir fuentes", onAnadir, Icons.Rounded.Notifications)
        motor == null -> Vacio("El motor de fuentes no está disponible.", icono = Icons.Rounded.Warning)
        reordenar -> OrdenarNovedades(ext)
        else -> {
            var refrescando by remember { mutableStateOf(false) }
            val gen = NovedadesGuardadas.generacion
            val pendientes = lista.any { NovedadesGuardadas.datos[it.id]?.first != gen && NovedadesGuardadas.fallos[it.id] != gen }
            LaunchedEffect(pendientes) { if (!pendientes) refrescando = false }
            // Deslizar hacia abajo vuelve a pedir las novedades de todas las fuentes (lo de antes se ve mientras tanto).
            val arriba = LocalArriba.current
            val estadoTirar = androidx.compose.material3.pulltorefresh.rememberPullToRefreshState()
            androidx.compose.material3.pulltorefresh.PullToRefreshBox(
                isRefreshing = refrescando && pendientes,
                onRefresh = { Tacto.umbral(); refrescando = true; NovedadesGuardadas.recargar() },
                modifier = Modifier.fillMaxSize(),
                state = estadoTirar,
                indicator = {
                    androidx.compose.material3.pulltorefresh.PullToRefreshDefaults.Indicator(
                        state = estadoTirar, isRefreshing = refrescando && pendientes,
                        modifier = Modifier.align(Alignment.TopCenter).padding(top = arriba),
                        containerColor = SearchColors.TileHigh, color = SearchColors.Primary,
                    )
                },
            ) {
                // Lo que ya está en tu biblioteca se marca (portada apagada + insignia), como en Komikku.
                val ctxB = androidx.compose.ui.platform.LocalContext.current
                val almB = remember { com.example.lector.Almacen.obtener(ctxB) }
                val enBiblio = remember(almB.version) { almB.clavesEnLinea() }
                // Cada fuente y, justo debajo, las búsquedas guardadas suyas que pusiste en Novedades.
                val filas = remember(lista, ext.novedadesBusq, ext.busquedasVersion) {
                    val porFuente = ext.novedadesBusq.mapNotNull { k ->
                        val id = k.substringBefore('\t').toLongOrNull() ?: return@mapNotNull null
                        val b = ext.busquedas(id).firstOrNull { it.nombre == k.substringAfter('\t') } ?: return@mapNotNull null
                        id to b
                    }.groupBy({ it.first }, { it.second })
                    val conFuente = ext.fuentes.associateBy { it.id }
                    lista.flatMap { f -> listOf(f to null) + porFuente[f.id].orEmpty().map { f to it } } +
                        porFuente.filterKeys { id -> lista.none { it.id == id } }.flatMap { (id, bs) -> conFuente[id]?.let { f -> bs.map { f to it } }.orEmpty() }
                }
                val abajo = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = arriba, bottom = abajo + 190.dp)) {
                    items(filas, key = { (f, b) -> if (b == null) "f${f.id}" else "b${f.id}|${b.nombre}" }, contentType = { "novedad" }) { (f, b) ->
                        NovedadesFuente(f, motor, onManga, onFuente, b, ext.busquedas(f.id).takeIf { b == null }.orEmpty(), enBiblio)
                    }
                }
            }
        }
    }
}

@Composable
private fun NovedadesFuente(
    f: FuenteUI, motor: MotorFuentes,
    onManga: (FuenteUI, MangaFuente) -> Unit, onFuente: (FuenteUI) -> Unit,
    busqueda: ExtManager.BusquedaGuardada? = null,
    guardadas: List<ExtManager.BusquedaGuardada> = emptyList(),
    enBiblioteca: Set<String> = emptySet(),
) {
    val gen = NovedadesGuardadas.generacion
    // Clave propia para cada búsqueda guardada (comparte la caché de la sesión con las fuentes).
    val clave = if (busqueda == null) f.id else f.id * 31 + busqueda.nombre.hashCode()
    val estado = NovedadesGuardadas.datos[clave]?.second
    var fallo by remember(f.id) { mutableStateOf<String?>(null) }
    var intento by remember(f.id) { mutableStateOf(0) }
    LaunchedEffect(clave, intento, gen) {
        // Ya cargadas en esta sesión (y sin pedir recargar): no se vuelven a pedir.
        if (NovedadesGuardadas.datos[clave]?.first == gen) return@LaunchedEffect
        fallo = null
        // Si la fuente no tiene "recientes", se usan los populares.
        runCatching { (if (busqueda != null) motor.buscar(
            f, busqueda.consulta, 1,
            busqueda.filtros?.let { t -> runCatching { motor.filtros(f) }.getOrNull()?.let { FiltrosTexto.aplicar(it, t) } },
        ) else runCatching { motor.recientes(f, 1) }.getOrElse { motor.populares(f, 1) }).mangas.distinctBy { it.url }.take(15) }
            .onSuccess { NovedadesGuardadas.datos[clave] = gen to it; NovedadesGuardadas.fallos.remove(clave) }
            .onFailure {
                if (it is kotlinx.coroutines.CancellationException) throw it
                NovedadesGuardadas.fallos[clave] = gen
                // Si ya había algo cargado, se sigue enseñando; el fallo solo se ve si no hay nada.
                if (estado == null) fallo = it.message ?: "No se pudieron cargar las novedades"
                else Avisos.mostrar("${f.nombre}: no se pudieron actualizar las novedades")
            }
    }
    val acento = MaterialTheme.colorScheme.primary
    val cabeceras = remember(f.id) { motor.cabeceras(f) }
    // Cada fuente es una sección como las de la búsqueda de Mochy: cabecera tocable (con la píldora de resalte),
    // las búsquedas guardadas como píldoras de categoría y el carrusel de portadas; una regla la cierra.
    Column(Modifier.fillMaxWidth().padding(bottom = 18.dp)) {
        Row(
            Modifier.fillMaxWidth()
                .clickable(interactionSource = null, indication = ResultRowHighlight) { onFuente(f) }
                .padding(horizontal = SearchHorizontalPadding, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconoExt(f.pkg, null, Modifier.size(36.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    f.nombre, style = AppTypography.titleLarge.copy(fontSize = 18.sp, fontWeight = FontWeight.Bold),
                    color = SearchColors.Primary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (busqueda != null) "Búsqueda: ${busqueda.nombre}" else nombreIdioma(f.lang),
                    style = AppTypography.bodySmall.copy(fontSize = 12.sp),
                    color = if (busqueda != null) acento else SearchColors.Secondary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(Icons.Rounded.KeyboardArrowRight, "Abrir", tint = SearchColors.Secondary, modifier = Modifier.size(22.dp))
        }
        // Las búsquedas guardadas de esta extensión, como al entrar en ella: tocar una la abre ya buscada.
        if (guardadas.isNotEmpty()) LazyRow(
            contentPadding = PaddingValues(start = SearchHorizontalPadding, end = SearchHorizontalPadding, top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(guardadas, key = { it.nombre }) { b -> SearchCategoryChip(b.nombre, false) { Tacto.tic(); BusquedaPendiente.guardada = b; onFuente(f) } }
        }
        Spacer(Modifier.height(12.dp))
        val lst = estado
        when {
            lst == null && fallo != null -> Row(
                Modifier.padding(horizontal = SearchHorizontalPadding).fillMaxWidth().clip(RoundedCornerShape(16.dp))
                    .background(SearchColors.Tile).searchCardBorder(16.dp)
                    .padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Warning, null, tint = Rojo, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    fallo!!, style = AppTypography.bodySmall.copy(fontSize = 12.sp), modifier = Modifier.weight(1f),
                    color = Rojo, maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                SearchRowGlyph(Icons.Rounded.Refresh, "Reintentar", { intento++ })
            }
            lst == null -> BonesCarrusel()
            lst.isEmpty() -> Text(
                "Nada nuevo por aquí", style = AppTypography.bodySmall.copy(fontSize = 12.sp),
                modifier = Modifier.padding(horizontal = SearchHorizontalPadding), color = SearchColors.Secondary,
            )
            else -> LazyRow(contentPadding = PaddingValues(horizontal = SearchHorizontalPadding), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(lst, key = { it.url }) { m ->
                    Column(Modifier.width(118.dp).press3D().pressable { onManga(f, m) }) {
                        val esta = "${f.id}|${m.url}" in enBiblioteca
                        Box {
                            ImagenRemota(
                                m.portadaUrl,
                                Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(FormaPortada).background(SearchColors.TileHigh)
                                    .graphicsLayer { alpha = if (esta) 0.38f else 1f },
                                cabeceras, fuenteId = f.id
                            )
                            if (esta) EnBiblioteca(Modifier.align(Alignment.TopStart).padding(6.dp))
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            m.titulo, style = AppTypography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 17.sp, fontWeight = FontWeight.Medium),
                            color = SearchColors.Primary, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        SearchRule()
    }
}

/** Modo "ordenar": arrastra el icono ≡ para mover una fuente, o quítala con la papelera. */
@Composable
private fun OrdenarNovedades(ext: ExtManager) {
    val orden = remember { mutableStateListOf<Long>().apply { addAll(ext.novedades) } }
    var arrastrando by remember { mutableStateOf<Long?>(null) }
    var desplaz by remember { mutableStateOf(0f) }
    val paso = with(LocalDensity.current) { (72 + 12).dp.toPx() }
    val acento = MaterialTheme.colorScheme.primary

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(top = LocalArriba.current)
            .padding(horizontal = SearchHorizontalPadding, vertical = 4.dp).padding(bottom = 190.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Arrastra para cambiar el orden", style = AppTypography.bodySmall.copy(fontSize = 12.sp), color = SearchColors.Secondary)
        orden.toList().forEach { id ->
            val f = ext.fuentes.firstOrNull { it.id == id }
            key(id) {
                val levantada = arrastrando == id
                val escala by animateFloatAsState(if (levantada && !Mov.reducido) 1.04f else 1f, Muelles.espacialRapido(), label = "levantar")
                val fondo by animateColorAsState(if (levantada) SearchColors.TileHigh else SearchColors.Tile, Muelles.efectos(), label = "fondoOrden")
                Row(
                    Modifier.fillMaxWidth().height(72.dp)
                        .zIndex(if (levantada) 1f else 0f)
                        .offset { IntOffset(0, if (levantada) desplaz.roundToInt() else 0) }
                        .graphicsLayer { scaleX = escala; scaleY = escala }
                        .clip(RoundedCornerShape(16.dp)).background(fondo).searchCardBorder(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.padding(start = 12.dp).size(AvatarSize).clip(CircleShape).background(SearchColors.TileHigh).pointerInput(id) {
                            detectDragGestures(
                                onDragStart = { arrastrando = id; desplaz = 0f },
                                onDragEnd = { arrastrando = null; desplaz = 0f; ext.fijarNovedades(orden.toList()) },
                                onDragCancel = { arrastrando = null; desplaz = 0f; ext.fijarNovedades(orden.toList()) },
                            ) { change, d ->
                                change.consume()
                                desplaz += d.y
                                val i = orden.indexOf(id)
                                if (desplaz > paso / 2 && i < orden.lastIndex) {
                                    orden.add(i + 1, orden.removeAt(i)); desplaz -= paso
                                } else if (desplaz < -paso / 2 && i > 0) {
                                    orden.add(i - 1, orden.removeAt(i)); desplaz += paso
                                }
                            }
                        },
                        Alignment.Center,
                    ) { Icon(Icons.Rounded.Menu, "Arrastrar", tint = SearchColors.Secondary, modifier = Modifier.size(18.dp)) }
                    if (f != null) {
                        Spacer(Modifier.width(10.dp))
                        IconoExt(f.pkg, null, Modifier.size(40.dp))
                    }
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(
                            f?.nombre ?: "Fuente no disponible", style = AppTypography.bodyLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
                            color = SearchColors.Primary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        if (f != null) Text(
                            nombreIdioma(f.lang), style = AppTypography.bodySmall.copy(fontSize = 12.sp),
                            color = SearchColors.Secondary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    SearchRowGlyph(Icons.Rounded.Delete, "Quitar", { orden.remove(id); ext.fijarNovedades(orden.toList()) }, Modifier.padding(end = 12.dp))
                }
            }
        }
    }
}

/** Diálogo "Novedades": marca las fuentes que quieres ver en la pestaña. */
@Composable
private fun DialogoNovedades(ext: ExtManager, onCerrar: () -> Unit) {
    var q by remember { mutableStateOf("") }
    val todas = remember(ext.fuentes, ext.mostrarNsfw, q) {
        ext.fuentes
            .filter { (ext.mostrarNsfw || !it.nsfw) && (q.isBlank() || it.nombre.contains(q.trim(), ignoreCase = true)) }
            .sortedWith(compareBy({ it.nombre.lowercase() }, { it.lang }))
    }
    DialogoAlerta(
        onDismissRequest = onCerrar,
        title = { Text("Novedades") },
        text = {
            Column {
                CampoBusqueda(q, { q = it }, "Buscar…")
                Spacer(Modifier.height(8.dp))
                if (todas.isEmpty()) {
                    Text("No hay fuentes. Instala una extensión y pulsa «Confiar».")
                } else {
                    LazyColumn(Modifier.heightIn(max = 400.dp)) {
                        items(todas) { f ->
                            Row(
                                Modifier.fillMaxWidth().clickable { ext.alternarNovedad(f.id) }.padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                CasillaShiori(checked = f.id in ext.novedades, onCheckedChange = { ext.alternarNovedad(f.id) })
                                IconoExt(f.pkg, null, Modifier.size(40.dp))
                                Spacer(Modifier.width(12.dp))
                                Text("${f.nombre} (${bandera(f.lang)})", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            // Sus búsquedas guardadas también pueden ir a Novedades, cada una como su propia fila.
                            ext.busquedas(f.id).forEach { b ->
                                val k = "${f.id}\t${b.nombre}"
                                Row(
                                    Modifier.fillMaxWidth().clickable { ext.alternarNovedadBusqueda(f.id, b.nombre) }.padding(start = 40.dp, top = 2.dp, bottom = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    CasillaShiori(checked = k in ext.novedadesBusq, onCheckedChange = { ext.alternarNovedadBusqueda(f.id, b.nombre) })
                                    Text("Búsqueda: ${b.nombre}", maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onCerrar) { Text("Aceptar") } },
    )
}

// ───────────────────────── Extensiones ─────────────────────────

@Composable
private fun ExtensionesTab(
    ext: ExtManager, consulta: String, buscando: Boolean, onConsulta: (String) -> Unit,
    onDetalle: (ExtInstalada) -> Unit, onConfiar: (ExtInstalada) -> Unit, onRepos: () -> Unit,
) {
    val q = consulta.trim()
    val instaladas = ext.instaladas
    val inst = remember(instaladas, q, ext.mostrarNsfw) {
        instaladas.filter { (q.isEmpty() || it.nombre.contains(q, ignoreCase = true)) && (ext.mostrarNsfw || !it.nsfw) }
    }
    val pendientes = remember(inst) { inst.filter { it.actualizacion != null } }
    val alDia = remember(inst) { inst.filter { it.actualizacion == null } }
    val disp = remember(ext.disponibles, instaladas, q, ext.mostrarNsfw, ext.idiomas) {
        val pkgsInstalados = instaladas.mapTo(HashSet()) { it.pkg }
        ext.disponibles
            .filter { it.pkg !in pkgsInstalados && (q.isEmpty() || it.nombre.contains(q, ignoreCase = true)) && (ext.mostrarNsfw || !it.nsfw) && it.lang in ext.idiomas }
            .sortedBy { it.nombre.lowercase() }
            .groupBy { it.lang }.toSortedMap(compareBy<String>({ nombreIdioma(it) }, { it }))
    }
    val acento = MaterialTheme.colorScheme.primary

    // «Confiar en todas»: las instaladas que aún no son de confianza (con una sola confirmación).
    val sinConfianza = remember(instaladas) { instaladas.filter { !it.confiable } }
    var confirmarTodas by remember { mutableStateOf(false) }
    if (confirmarTodas) {
        DialogoAlerta(
            onDismissRequest = { confirmarTodas = false },
            title = { Text("¿Confiar en ${sinConfianza.size} extensiones?") },
            text = {
                Text(
                    "Una extensión de confianza puede ejecutar su código dentro de la app. Hazlo solo si las instalaste " +
                        "tú desde listados que conoces.\n\n" + sinConfianza.take(12).joinToString("\n") { "• " + it.nombre } +
                        if (sinConfianza.size > 12) "\n… y ${sinConfianza.size - 12} más" else ""
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    sinConfianza.forEach { ext.confiar(it) }
                    confirmarTodas = false
                    Avisos.mostrar("${sinConfianza.size} extensiones de confianza")
                }) { Text("Confiar en todas") }
            },
            dismissButton = { TextButton(onClick = { confirmarTodas = false }) { Text("Cancelar") } },
        )
    }

    val abajo = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Column(Modifier.fillMaxSize()) {
        if (buscando) Box(Modifier.padding(horizontal = SearchHorizontalPadding, vertical = 8.dp)) { CampoBusqueda(consulta, onConsulta, "Buscar extensiones") }
        val estadoLista = androidx.compose.foundation.lazy.rememberLazyListState()
        Box(Modifier.weight(1f)) {
        LazyColumn(state = estadoLista, contentPadding = PaddingValues(top = LocalArriba.current, bottom = abajo + 190.dp)) {
            if (ext.cargando) item(key = "cargando", contentType = "Aviso") {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = SearchHorizontalPadding).padding(bottom = 14.dp).height(2.dp).clip(CircleShape),
                    color = SearchColors.Primary, trackColor = SearchColors.Divider,
                )
            }
            if (sinConfianza.isNotEmpty()) {
                item(key = "confianza", contentType = "Aviso") {
                    // Tarjeta de Mochy: cuántas faltan por confiar y el botón blanco para hacerlo de golpe.
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = SearchHorizontalPadding).padding(bottom = 12.dp)
                            .clip(RoundedCornerShape(16.dp)).background(SearchColors.Tile).searchCardBorder(16.dp).padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(AvatarSize).clip(CircleShape).background(SearchColors.TileHigh), Alignment.Center) {
                            Icon(Icons.Rounded.Lock, null, tint = SearchColors.Secondary, modifier = Modifier.size(18.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                "${sinConfianza.size} sin confianza", style = AppTypography.bodyLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
                                color = SearchColors.Primary, maxLines = 1,
                            )
                            Text(
                                "Sus fuentes no salen hasta confiar", style = AppTypography.bodySmall.copy(fontSize = 12.sp),
                                color = SearchColors.Secondary, maxLines = 2,
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        PildoraAccion("Confiar", Icons.Rounded.Check, relleno = true) { confirmarTodas = true }
                    }
                }
            }
            if (ext.repos.none { it.activo }) {
                item(key = "sinRepos", contentType = "Aviso") { Aviso("No tienes ningún listado de extensiones activo.", "Añadir listado", onRepos) }
            }
            if (ext.errores.isNotEmpty()) {
                item(key = "errores", contentType = "Aviso") {
                    Column(
                        Modifier.fillMaxWidth().padding(horizontal = SearchHorizontalPadding).padding(bottom = 12.dp)
                            .clip(RoundedCornerShape(16.dp)).background(SearchColors.Tile).searchCardBorder(16.dp).padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        ext.errores.forEach { Text(it, style = AppTypography.bodySmall.copy(fontSize = 12.sp), color = Rojo) }
                    }
                }
            }
            // contentType = nombre de la sección: es lo que enseña la burbuja de la barra de desplazamiento.
            // a row's own rule already closes the previous section
            var regla = true
            if (pendientes.isNotEmpty()) {
                item(contentType = "Actualizaciones") {
                    SearchSectionHeader(
                        "Actualizaciones", ruleAbove = false,
                        trailing = {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                SearchCount(pendientes.size)
                                if (pendientes.size > 1) {
                                    Spacer(Modifier.weight(1f))
                                    PildoraAccion("Todas", Icons.Rounded.Refresh, relleno = true) { Tacto.confirmar(); ext.actualizarTodas() }
                                }
                            }
                        },
                    )
                }
                items(pendientes, contentType = { "Actualizaciones" }) { FilaInstalada(it, ext, onDetalle, onConfiar) }
                regla = false
            }
            if (alDia.isNotEmpty()) {
                val arriba = if (regla) 0.dp else 22.dp
                item(contentType = "Instalado") {
                    SearchSectionHeader("Instalado", ruleAbove = false, modifier = Modifier.padding(top = arriba), trailing = { SearchCount(alDia.size) })
                }
                items(alDia, contentType = { "Instalado" }) { FilaInstalada(it, ext, onDetalle, onConfiar) }
                regla = false
            }
            disp.forEach { (lang, lista) ->
                val n = nombreIdioma(lang)
                val arriba = if (regla) 0.dp else 22.dp
                regla = false
                item(contentType = n) {
                    SearchSectionHeader(n, ruleAbove = false, modifier = Modifier.padding(top = arriba), trailing = { SearchCount(lista.size) })
                }
                items(lista, contentType = { n }) { FilaDisponible(it, ext) }
            }
        }
        BarraDesplazamiento(estadoLista)
        }
    }
}

@Composable
private fun FilaInstalada(e: ExtInstalada, ext: ExtManager, onDetalle: (ExtInstalada) -> Unit, onConfiar: (ExtInstalada) -> Unit) {
    val acento = MaterialTheme.colorScheme.primary
    val subtitulo = remember(e.lang, e.confiable, e.nsfw, e.versionName, e.actualizacion, e.repoSigla) {
        listOfNotNull(
            if (!e.confiable) "Sin confianza" else "${bandera(e.lang)} ${nombreIdioma(e.lang)}",
            "+18".takeIf { e.nsfw },
            e.versionName + (e.actualizacion?.let { " → ${it.versionName}" } ?: "") + (e.repoSigla?.let { " @$it" } ?: ""),
        ).joinToString("  •  ")
    }
    ResultRow(
        artwork = {
            Box {
                IconoExt(e.pkg, null, Modifier.size(46.dp))
                // Insignia de actualización: un círculo de acento con la flecha, en la esquina del icono.
                if (e.actualizacion != null) Box(
                    Modifier.align(Alignment.TopEnd).offset(x = 6.dp, y = (-6).dp).size(20.dp)
                        .clip(CircleShape).background(SearchColors.page(false)).padding(2.dp).clip(CircleShape).background(acento),
                    Alignment.Center,
                ) { Icon(Icons.Rounded.KeyboardArrowUp, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(14.dp)) }
            }
        },
        title = e.nombre,
        subtitle = subtitulo,
        subtitleColor = when {
            !e.confiable -> Rojo
            e.actualizacion != null -> acento
            else -> SearchColors.Secondary
        },
        onClick = { onDetalle(e) },
        trailing = {
            val pr = ext.progreso[e.pkg]
            when {
                pr != null -> EstadoProgreso(pr) { ext.limpiarError(e.pkg) }
                !e.confiable -> PildoraAccion("Confiar", null) { onConfiar(e) }
                e.actualizacion != null -> PildoraAccion("Actualizar", Icons.Rounded.Refresh, relleno = true) { e.actualizacion?.let { ext.instalar(it) } }
                else -> SearchRowGlyph(Icons.Rounded.Settings, "Ajustes", { onDetalle(e) })
            }
        },
    )
}

@Composable
private fun FilaDisponible(d: ExtDisponible, ext: ExtManager) {
    val subtitulo = remember(d.lang, d.nsfw, d.versionName, d.repoSigla) {
        listOfNotNull("${bandera(d.lang)} ${nombreIdioma(d.lang)}", "+18".takeIf { d.nsfw }, "${d.versionName} @${d.repoSigla}").joinToString("  •  ")
    }
    ResultRow(
        artwork = { IconoExt(d.pkg, d.iconoUrl, Modifier.size(46.dp)) },
        title = d.nombre,
        subtitle = subtitulo,
        onClick = null,
        trailing = {
            val pr = ext.progreso[d.pkg]
            when {
                pr != null -> EstadoProgreso(pr) { ext.limpiarError(d.pkg) }
                d.apkUrl.isBlank() -> Text("No disponible", style = AppTypography.bodySmall.copy(fontSize = 12.sp), color = SearchColors.Secondary)
                else -> PildoraAccion("Instalar", Icons.Rounded.Add) { Tacto.tic(); ext.instalar(d) }
            }
        },
    )
}

/** Botón en píldora de 34 dp, como las categorías de la búsqueda de Mochy: blanco si [relleno], gris si no. */
@Composable
private fun PildoraAccion(texto: String, icono: ImageVector?, relleno: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier.height(34.dp).pressable(onClick).clip(RoundedCornerShape(50))
            .background(if (relleno) SearchColors.Primary else SearchColors.TileHigh).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val tinta = if (relleno) SearchColors.page(false) else SearchColors.Primary
        if (icono != null) {
            Icon(icono, null, tint = tinta, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(texto, style = AppTypography.labelLarge.copy(fontSize = 14.sp, fontWeight = FontWeight.Medium), color = tinta, maxLines = 1)
    }
}

/** Progreso de una instalación: píldora que se llena con muelle; error en rojo y tocable. */
@Composable
private fun EstadoProgreso(pr: Progreso, alReintentar: () -> Unit) {
    when (pr.fase) {
        Fase.DESCARGANDO -> {
            val fr by animateFloatAsState(
                (pr.porcentaje / 100f).coerceIn(0.04f, 1f), if (Mov.reducido) tween(100) else Muelles.espacial(), label = "descarga"
            )
            val relleno = SearchColors.Primary.copy(alpha = 0.28f)
            Box(
                Modifier.width(78.dp).height(34.dp).clip(CircleShape).background(SearchColors.TileHigh)
                    .drawBehind { drawRoundRect(relleno, size = Size(size.width * fr, size.height), cornerRadius = CornerRadius(size.height / 2f)) },
                Alignment.Center,
            ) { Text("${pr.porcentaje}%", style = AppTypography.labelLarge.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold), color = SearchColors.Primary) }
        }
        Fase.INSTALANDO -> Row(
            Modifier.height(34.dp).clip(CircleShape).background(SearchColors.TileHigh).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SearchLoadingDots(Modifier.padding(end = 2.dp))
        }
        Fase.ERROR -> Row(
            Modifier.height(34.dp).pressable(alReintentar).clip(CircleShape).background(Rojo.copy(alpha = 0.14f)).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Refresh, null, tint = Rojo, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text("Error", style = AppTypography.labelLarge.copy(fontSize = 14.sp, fontWeight = FontWeight.Medium), color = Rojo)
        }
    }
}

// ───────────────────────── Migrar ─────────────────────────

@Composable
private fun MigrarTab(ext: ExtManager, almacen: com.example.lector.Almacen?, onMigrar: (com.example.lector.Comic) -> Unit) {
    // Como en Komikku: tus series agrupadas por fuente; elige una para pasarla a otra fuente.
    val series = remember(almacen?.version) { almacen?.cargar().orEmpty().filter { it.enLinea } }
    if (series.isEmpty()) {
        Vacio("Aquí puedes mover tus series de una fuente a otra. Añade a la biblioteca algún manga de una fuente.", icono = Icons.Rounded.Share)
        return
    }
    val grupos = remember(series, ext.fuentes) {
        val porId = ext.fuentes.associateBy { it.id }
        series.groupBy { c -> porId[c.fuenteId]?.nombre ?: "Fuente no instalada" }
            .toSortedMap(compareBy { it.lowercase() })
    }
    val abajo = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    androidx.compose.foundation.lazy.LazyColumn(contentPadding = PaddingValues(top = LocalArriba.current, bottom = abajo + 190.dp)) {
        item(key = "intro") {
            Text(
                "Elige una serie para buscarla en otra fuente y pasar tu progreso.",
                style = AppTypography.bodySmall.copy(fontSize = 12.sp), color = SearchColors.Secondary,
                modifier = Modifier.padding(horizontal = SearchHorizontalPadding).padding(bottom = 18.dp),
            )
        }
        var primera = true
        grupos.forEach { (nombre, lista) ->
            val arriba = if (primera) 0.dp else 22.dp
            primera = false
            item(key = "g_$nombre") {
                SearchSectionHeader(nombre, ruleAbove = false, modifier = Modifier.padding(top = arriba), trailing = { SearchCount(lista.size) })
            }
            items(lista.size, key = { lista[it].id }) { i ->
                val c = lista[i]
                ResultRow(
                    artwork = {
                        // la miniatura de 46 dp de alto de las filas de Mochy, en proporción de portada
                        ComicCover(c, almacen!!, Modifier.height(46.dp).aspectRatio(2f / 3f), RoundedCornerShape(6.dp))
                    },
                    title = c.titulo,
                    // Lo guardado (de la copia o de la biblioteca); al tocar se ve su ficha guardada y desde ahí se migra.
                    subtitle = if (c.paginas > 0) "${c.paginas} capítulos · ${c.ultimaPagina} leídos" else "Ver lo guardado y migrar",
                    onClick = { onMigrar(c) },
                    trailing = { SearchRowGlyph(Icons.Rounded.ArrowForward, "Migrar", { onMigrar(c) }) },
                )
            }
        }
    }
}

// ───────────────────────── Piezas comunes ─────────────────────────

/** Botón redondo de la cabecera de Explorar (el hueco del avatar de Mochy); con [activo] se vuelve blanco. */
@Composable
private fun BotonCabecera(icono: ImageVector, desc: String, activo: Boolean = false, onClick: () -> Unit) {
    val fondo by animateColorAsState(if (activo) SearchColors.Primary else SearchColors.TileHigh, Muelles.efectos(), label = "botonCab")
    val tinta by animateColorAsState(if (activo) SearchColors.page(false) else SearchColors.Secondary, Muelles.efectos(), label = "tintaCab")
    Box(Modifier.size(AvatarSize).pressable { Tacto.tic(); onClick() }.clip(CircleShape).background(fondo), Alignment.Center) {
        Icon(icono, desc, tint = tinta, modifier = Modifier.size(AvatarSize * 0.5f))
    }
}

/** Tarjeta de fila de Explorar (fuentes, extensiones): superficie Card, esquinas suaves y se aplasta al pulsar. */
@Composable
internal fun TarjetaExplorar(onClick: (() -> Unit)?, content: @Composable RowScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
            .then(if (onClick != null) Modifier.pressableCon(0.97f, onClick) else Modifier)
            .clip(SuaveShape(22.dp)).background(LectorColors.Card)
            .padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/** Píldora pequeña de datos (idioma, versión, +18). */
@Composable
internal fun PildoraExplorar(texto: String, color: Color = LectorColors.Muted, fondo: Color = LectorColors.PillFill) {
    Box(Modifier.clip(CircleShape).background(fondo).padding(horizontal = 9.dp, vertical = 3.dp)) {
        LText(texto, LectorType.Label.copy(fontSize = 11.sp, lineHeight = 14.sp), color = color, maxLines = 1)
    }
}

/** Título de sección grande (20 sp negrita) con su recuento en una píldora (de acento si [acentuado]). */
@Composable
internal fun SeccionExplorar(texto: String, cantidad: Int? = null, acentuado: Boolean = false) {
    val acento = MaterialTheme.colorScheme.primary
    Row(
        Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 22.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LText(texto, LectorType.Section.copy(fontSize = 20.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold), Modifier.weight(1f, fill = false), maxLines = 1)
        if (cantidad != null) {
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier.clip(CircleShape).background(if (acentuado) acento else LectorColors.PillFill)
                    .padding(horizontal = 9.dp, vertical = 2.dp),
            ) {
                LText(
                    "$cantidad", LectorType.Label.copy(fontWeight = FontWeight.Bold),
                    color = if (acentuado) MaterialTheme.colorScheme.onPrimary else LectorColors.Muted, maxLines = 1,
                )
            }
        }
    }
}

/**
 * Chip expresivo de Explorar: la esquina se redondea con muelle y se llena de acento al elegirlo
 * (el resto, tonal). Con [onLongClick] admite mantener pulsado.
 */
@Composable
internal fun ChipExplorar(
    texto: String, seleccionado: Boolean, icono: ImageVector? = null,
    onLongClick: (() -> Unit)? = null, onClick: () -> Unit,
) {
    val acento = MaterialTheme.colorScheme.primary
    val radio by animateDpAsState(
        if (seleccionado) 20.dp else 12.dp, if (Mov.reducido) tween(100) else Muelles.espacialRapido(), label = "radioChipExp",
    )
    val fondo by animateColorAsState(if (seleccionado) acento else LectorColors.Card, Muelles.efectos(), label = "fondoChipExp")
    val tinta by animateColorAsState(
        if (seleccionado) MaterialTheme.colorScheme.onPrimary else LectorColors.Text, Muelles.efectos(), label = "tintaChipExp"
    )
    Row(
        Modifier.pressable(onLongClick) { Tacto.tic(); onClick() }.clip(RoundedCornerShape(radio))
            .background(fondo).height(38.dp).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val ic = if (seleccionado) Icons.Rounded.Check else icono
        if (ic != null) {
            Icon(ic, null, tint = tinta, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(6.dp))
        }
        LText(texto, LectorType.Body, color = tinta, maxLines = 1)
    }
}

/** Esqueleto de un carrusel de portadas dentro de una tarjeta (con la ola). */
@Composable
internal fun BonesCarrusel(ancho: androidx.compose.ui.unit.Dp = 118.dp) {
    BonesHost(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            repeat(4) {
                Column {
                    Bone(ancho, ancho * 1.5f, forma = SuaveShape(16.dp))
                    Spacer(Modifier.height(8.dp))
                    Bone(ancho * 0.7f, 12.dp, forma = SuaveShape(6.dp))
                }
            }
        }
    }
}

@Composable
internal fun CampoBusqueda(valor: String, alCambiar: (String) -> Unit, pista: String) {
    // El SearchField de Mochy (mochy/search/SearchDesign.kt): píldora de 48 dp, Field (FieldFocused con el foco),
    // lupa y borrar de 20 dp a los lados y pista gris de 15 sp. Se queda con texto plano para no cambiar quién lo usa.
    var foco by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    BasicTextField(
        value = valor, onValueChange = alCambiar, singleLine = true,
        textStyle = AppTypography.bodyMedium.copy(color = SearchColors.Primary, fontSize = 15.sp),
        cursorBrush = SolidColor(SearchColors.Primary),
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
        keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { focusManager.clearFocus() }),
        modifier = Modifier.onFocusChanged { foco = it.isFocused },
        decorationBox = { inner ->
            Row(
                Modifier.fillMaxWidth().height(SearchFieldHeight).clip(RoundedCornerShape(50))
                    .background(if (foco) SearchColors.FieldFocused else SearchColors.Field).padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SearchFieldGlyph(Icons.Rounded.Search, null)
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (valor.isEmpty()) Text(
                        pista, style = AppTypography.bodyMedium.copy(fontSize = 15.sp), color = SearchColors.Placeholder,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    inner()
                }
                if (valor.isNotEmpty()) {
                    Spacer(Modifier.width(10.dp))
                    SearchFieldGlyph(Icons.Rounded.Close, "Borrar") { alCambiar("") }
                }
            }
        }
    )
}

/** Vacío: el texto gris de «No results» de Mochy, con un icono en un círculo y una píldora blanca de acción. */
@Composable
private fun Vacio(texto: String, accion: String? = null, onAccion: (() -> Unit)? = null, icono: ImageVector = Icons.Rounded.Search) {
    Column(
        Modifier.fillMaxSize().padding(top = LocalArriba.current).padding(horizontal = 40.dp).padding(bottom = 120.dp),
        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.size(72.dp).clip(CircleShape).background(SearchColors.TileHigh), Alignment.Center) {
            Icon(icono, null, tint = SearchColors.Secondary, modifier = Modifier.size(32.dp))
        }
        Spacer(Modifier.height(18.dp))
        Text(texto, color = SearchColors.Secondary, style = AppTypography.bodyLarge.copy(fontSize = 15.sp, lineHeight = 21.sp), textAlign = TextAlign.Center)
        if (accion != null && onAccion != null) {
            Spacer(Modifier.height(18.dp))
            PildoraAccion(accion, Icons.Rounded.KeyboardArrowRight, relleno = true, onClick = onAccion)
        }
    }
}

@Composable
private fun Aviso(texto: String, accion: String, onAccion: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = SearchHorizontalPadding).padding(bottom = 12.dp)
            .clip(RoundedCornerShape(16.dp)).background(SearchColors.Tile).searchCardBorder(16.dp).padding(16.dp)
    ) {
        Icon(Icons.Rounded.Info, null, tint = SearchColors.Secondary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(10.dp))
        Text(texto, style = AppTypography.bodyLarge.copy(fontSize = 15.sp), color = SearchColors.Primary)
        Spacer(Modifier.height(14.dp))
        PildoraAccion(accion, Icons.Rounded.Add, relleno = true, onClick = onAccion)
    }
}

/** Icono de una extensión: el de la app instalada o, si no, el que publica el listado. */
@Composable
fun IconoExt(pkg: String, url: String?, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val local = remember(pkg) {
        runCatching { ctx.packageManager.getApplicationIcon(pkg).toBitmap(128, 128).asImageBitmap() }.getOrNull()
    }
    val forma = SuaveShape(12.dp)
    if (local != null) Image(local, null, modifier.clip(forma), contentScale = ContentScale.Crop)
    else ImagenRemota(url, modifier.clip(forma))
}

/**
 * Cargador de imágenes sin librerías.
 *  - Memoria: límite por BYTES (no por número de imágenes), así las cuadrículas largas no vuelven a descargar portadas.
 *  - Disco: guarda los bytes originales en cacheDir/imagenes (máx. ~120 MB); al reabrir la app las portadas salen al instante.
 *  - Una sola petición por URL aunque varias pantallas la pidan a la vez, y máximo 6 descargas simultáneas.
 */
object Imagenes {
    private val memoria = object : LruCache<String, ImageBitmap>(
        (Runtime.getRuntime().maxMemory() / 8L).coerceIn(16L * 1024 * 1024, 96L * 1024 * 1024).toInt()
    ) {
        override fun sizeOf(key: String, value: ImageBitmap): Int = value.width * value.height * 4
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val enVuelo = HashMap<String, Deferred<ImageBitmap?>>()
    private val limite = Semaphore(6)
    @Volatile private var disco: File? = null

    private const val MAX_DISCO = 120L * 1024 * 1024

    /** Prepara la caché en disco. Se puede llamar muchas veces. */
    fun iniciar(ctx: Context) {
        if (disco != null) return
        val carpeta = File(ctx.applicationContext.cacheDir, "imagenes").apply { mkdirs() }
        disco = carpeta
        scope.launch { runCatching { recortarDisco(carpeta) } }
    }

    private fun recortarDisco(carpeta: File) {
        val archivos = carpeta.listFiles()?.filter { it.isFile } ?: return
        var total = archivos.sumOf { it.length() }
        if (total <= MAX_DISCO) return
        for (a in archivos.sortedBy { it.lastModified() }) {
            if (total <= MAX_DISCO * 3 / 4) break
            total -= a.length()
            a.delete()
        }
    }

    /** Bytes originales de una imagen (de la caché en disco o de la red), para compartirla o guardarla. */
    suspend fun bytes(url: String, cabeceras: Map<String, String> = emptyMap(), fuenteId: Long? = null): ByteArray? = kotlinx.coroutines.withContext(Dispatchers.IO) {
        archivoDe(url)?.takeIf { it.exists() && it.length() > 0 }?.let { f -> return@withContext runCatching { f.readBytes() }.getOrNull() }
        descargar(url, cabeceras, fuenteId)?.let { return@withContext it }
        runCatching {
            val c = URL(url).openConnection() as HttpURLConnection
            try {
                c.connectTimeout = 15_000; c.readTimeout = 30_000
                c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) Lector/1.0")
                cabeceras.forEach { (k, v) -> c.setRequestProperty(k, v) }
                if (c.responseCode !in 200..299) null else c.inputStream.use { it.readBytes() }
            } finally { c.disconnect() }
        }.getOrNull()
    }

    /** Bytes que ocupa la caché de imágenes en disco. */
    fun tamanoDisco(): Long = disco?.listFiles()?.sumOf { it.length() } ?: 0L

    /** Vacía la caché de imágenes (memoria y disco). */
    fun vaciar() {
        memoria.evictAll()
        disco?.listFiles()?.forEach { it.delete() }
    }

    /** Devuelve la imagen si ya está en memoria (sin esperar), para pintarla en el primer fotograma. */
    // Tamaño (lado mayor pedido) con el que se decodificó cada imagen guardada: la pequeña no sirve para la grande.
    private val pedidoDe = java.util.concurrent.ConcurrentHashMap<String, Int>()
    private fun valeParaTam(url: String, maxLado: Int) = (pedidoDe[url] ?: 0) >= maxLado * 0.8f
    fun enMemoria(url: String, maxLado: Int = 0): ImageBitmap? = memoria.get(url)?.takeIf { maxLado == 0 || valeParaTam(url, maxLado) }
    private fun guardar(url: String, img: ImageBitmap, maxLado: Int) {
        if ((pedidoDe[url] ?: 0) > maxLado && memoria.get(url) != null) return // no sustituir una grande por una pequeña
        pedidoDe[url] = maxLado; memoria.put(url, img)
    }

    suspend fun cargar(url: String, cabeceras: Map<String, String> = emptyMap(), maxLado: Int = 512, fuenteId: Long? = null): ImageBitmap? {
        // Lo guardado vale si es al menos casi tan grande como lo pedido (si no, la portada a pantalla completa salía borrosa).
        enMemoria(url, maxLado)?.let { return it }
        // Si la portada sale de la pantalla antes de cargar, su carga se cancela y deja el turno a las visibles.
        return tareas.esperar("$url@$maxLado") { limite.withPermit { obtener(url, cabeceras, maxLado, fuenteId) } }
    }
    private val tareas by lazy { TareasCompartidas<ImageBitmap?>(scope) }

    private fun archivoDe(url: String): File? {
        val carpeta = disco ?: return null
        val h = java.security.MessageDigest.getInstance("SHA-1")
            .digest(url.toByteArray()).joinToString("") { "%02x".format(it) }
        return File(carpeta, h)
    }

    /** Bytes de una imagen de internet: cliente de la fuente → cliente general → conexión simple. */
    private suspend fun descargar(url: String, cabeceras: Map<String, String>, fuenteId: Long?): ByteArray? {
        com.example.lector.ext.datosIncrustados(url)?.let { return it } // portada incrustada («data:…»)
        val motor = com.example.lector.ext.Motor.actual
        if (fuenteId != null && fuenteId != 0L && motor != null) {
            runCatching { motor.bytesImagen(fuenteId, url) }.getOrNull()?.takeIf { it.isNotEmpty() }?.let { return it }
        }
        motor?.clienteGeneral()?.let { cliente ->
            runCatching {
                val b = okhttp3.Request.Builder().url(url)
                cabeceras.forEach { (k, v) -> b.header(k, v) }
                cliente.newCall(b.build()).execute().use { r -> if (r.isSuccessful) r.body.bytes() else null }
            }.getOrNull()?.takeIf { it.isNotEmpty() }?.let { return it }
        }
        return runCatching {
            val c = URL(url).openConnection() as HttpURLConnection
            try {
                c.connectTimeout = 15_000
                c.readTimeout = 30_000
                c.setRequestProperty("User-Agent", eu.kanade.tachiyomi.network.NetworkHelper.DEFAULT_USER_AGENT)
                cabeceras.forEach { (k, v) -> c.setRequestProperty(k, v) }
                if (c.responseCode !in 200..299) null else c.inputStream.use { it.readBytes() }
            } finally {
                c.disconnect()
            }
        }.getOrNull()
    }

    private suspend fun obtener(url: String, cabeceras: Map<String, String>, maxLado: Int, fuenteId: Long?): ImageBitmap? {
        val archivo = archivoDe(url)
        // 1) Disco.
        if (archivo != null && archivo.exists() && archivo.length() > 0) {
            val bytes = runCatching { archivo.readBytes() }.getOrNull()
            val img = bytes?.let { decodificar(it, maxLado) }
            if (img != null) {
                archivo.setLastModified(System.currentTimeMillis())
                guardar(url, img, maxLado)
                return img
            }
            archivo.delete() // archivo dañado
        }
        // 2) Red: con el cliente de la fuente si se sabe cuál es; si no, con el cliente general (cookies + Cloudflare).
        val bytes = descargar(url, cabeceras, fuenteId) ?: return null
        val img = decodificar(bytes, maxLado) ?: return null
        if (archivo != null) {
            runCatching {
                val tmp = File(archivo.path + ".tmp")
                tmp.writeBytes(bytes)
                if (!tmp.renameTo(archivo)) tmp.delete()
            }
        }
        guardar(url, img, maxLado)
        return img
    }

    private fun decodificar(bytes: ByteArray, maxLado: Int): ImageBitmap? = runCatching {
        val medidas = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, medidas)
        if (medidas.outWidth <= 0) return@runCatching null
        var muestra = 1
        while (maxOf(medidas.outWidth, medidas.outHeight) / (muestra * 2) >= maxLado) muestra *= 2
        val opciones = BitmapFactory.Options().apply { inSampleSize = muestra }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opciones)?.asImageBitmap()
    }.getOrNull()
}

@Composable
fun ImagenRemota(
    url: String?, modifier: Modifier = Modifier, cabeceras: Map<String, String> = emptyMap(),
    onCargada: ((ImageBitmap) -> Unit)? = null,
    escala: ContentScale = ContentScale.Crop,
    maxLado: Int = 512,
    // Fuente de la que viene la imagen: se descarga con su cliente (cookies, Cloudflare, Referer…).
    fuenteId: Long? = null,
) {
    val ctx = LocalContext.current
    remember { Imagenes.iniciar(ctx) }
    // Si ya estaba en memoria se pinta en el primer fotograma, sin parpadeo ni fundido al hacer scroll.
    var img by remember(url) { mutableStateOf(url?.takeIf { it.isNotBlank() }?.let { Imagenes.enMemoria(it, maxLado) }) }
    LaunchedEffect(url, cabeceras, fuenteId) {
        if (!url.isNullOrBlank()) {
            val cargada = img ?: Imagenes.cargar(url, cabeceras, maxLado, fuenteId)
            img = cargada
            if (cargada != null) onCargada?.invoke(cargada)
        }
    }
    // La portada nueva aparece con un fundido; la que ya estaba en caché empieza directamente en 1f.
    val opacidad by animateFloatAsState(if (img != null) 1f else 0f, tween(300), label = "portadaFundido")
    Box(modifier.background(LectorColors.CardHigh)) {
        val i = img
        if (i != null) Image(
            bitmap = i, contentDescription = null, contentScale = escala,
            modifier = Modifier.fillMaxSize().graphicsLayer { alpha = opacidad },
        )
    }
}
