package com.example.lector.ui

import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import com.example.lector.ui.mochy.library.GeneratedGroupHeader
import com.example.lector.ui.mochy.library.HomeTrackRow
import com.example.lector.ui.mochy.library.LibraryColors
import com.example.lector.ui.mochy.library.LibraryEmptyState
import com.example.lector.ui.mochy.library.LibraryIconButton
import com.example.lector.ui.mochy.library.LibraryRowCoverCorner
import com.example.lector.ui.mochy.library.LibraryRowCoverSize
import com.example.lector.ui.mochy.library.LibraryTitleBlock
import com.example.lector.ui.mochy.library.SearchHorizontalPadding
import kotlinx.coroutines.launch
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.lector.ActualizacionCap
import com.example.lector.Actualizador
import com.example.lector.Almacen
import com.example.lector.Comic
import com.example.lector.Descargas
import com.example.lector.ext.CapituloFuente
import com.example.lector.ext.ExtManager
import java.text.DateFormat
import java.util.Calendar
import java.util.Date

private fun inicioDelDia(ms: Long): Long = Calendar.getInstance().apply {
    timeInMillis = ms
    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}.timeInMillis

fun etiquetaDia(inicio: Long): String {
    val hoy = inicioDelDia(System.currentTimeMillis())
    val dia = 24L * 60 * 60 * 1000
    return when {
        inicio >= hoy -> "Hoy"
        inicio >= hoy - dia -> "Ayer"
        inicio >= hoy - 6 * dia -> "Hace ${(hoy - inicio + dia - 1) / dia} días"
        else -> DateFormat.getDateInstance(DateFormat.LONG).format(Date(inicio))
    }
}

fun hace(ms: Long): String {
    if (ms == 0L) return "nunca"
    val min = (System.currentTimeMillis() - ms) / 60_000
    return when {
        min < 1 -> "hace un momento"
        min < 60 -> "hace $min min"
        min < 60 * 24 -> "hace ${min / 60} h"
        else -> "hace ${min / (60 * 24)} d"
    }
}

private fun clave(a: ActualizacionCap) = "${a.fuenteId}|${a.capUrl}"

/**
 * Pestaña "Actualizaciones" (como la de Komikku): capítulos nuevos de los mangas de tu biblioteca, por día y
 * agrupados por manga. Tocar abre el capítulo; mantener pulsado selecciona (marcar leído, descargar, quitar).
 * Deslizar hacia abajo busca capítulos nuevos. Si hubo mangas que no se pudieron revisar, se ven sus errores.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ActualizacionesScreen(
    almacen: Almacen,
    ext: ExtManager,
    alAbrir: (ActualizacionCap) -> Unit,
    alAbrirManga: (ActualizacionCap) -> Unit = {},
    modifier: Modifier = Modifier,
    alBuscarGlobal: (String) -> Unit = {},
) {
    val ctx = LocalContext.current
    val lista = almacen.actualizaciones
    abrirMangaAct = alAbrirManga
    var seleccion by remember { mutableStateOf(emptySet<String>()) }
    var abiertos by remember { mutableStateOf(emptySet<String>()) }
    var menu by remember { mutableStateOf(false) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var confirmarLimpiar by remember { mutableStateOf(false) }
    var verErrores by remember { mutableStateOf(false) }

    // Sin revisión automática: al abrir la pestaña, si hace más de 6 horas, se revisa sola.
    LaunchedEffect(Unit) {
        if (almacen.intervaloActualizacion == 0 &&
            System.currentTimeMillis() - almacen.ultimaActualizacion > Actualizador.INTERVALO_MS
        ) Actualizador.iniciar(ctx, avisar = false)
    }
    BackHandler(enabled = seleccion.isNotEmpty()) { seleccion = emptySet() }

    val grupos = remember(lista) {
        lista.sortedByDescending { it.detectado }
            .groupBy { inicioDelDia(it.detectado) }
            .map { (dia, filas) -> dia to filas.groupBy { "${it.fuenteId}|${it.urlManga}" }.values.toList() }
    }
    val comics: Map<String, Comic> = remember(almacen.version) {
        almacen.cargar().filter { it.enLinea }.associateBy { "${it.fuenteId}|${it.urlManga}" }
    }
    val leidos = ext.leidos

    fun alternar(a: ActualizacionCap) {
        val k = clave(a)
        seleccion = if (k in seleccion) seleccion - k else seleccion + k
    }

    fun seleccionadas() = lista.filter { clave(it) in seleccion }

    fun recalcularProgreso(filas: List<ActualizacionCap>) {
        filas.map { it.fuenteId to it.urlManga }.distinct().forEach { (f, url) ->
            val conocidos = almacen.capitulosConocidos(f, url)
            if (conocidos.isNotEmpty()) almacen.actualizarProgresoEnLinea(f, url, conocidos.count { ext.esLeido(f, it) }, conocidos.size)
        }
    }

    Box(modifier.fillMaxSize()) {
        val acento = MaterialTheme.colorScheme.primary
        Column(Modifier.fillMaxSize()) {
            if (seleccion.isNotEmpty()) {
                CabeceraSeleccion(
                    seleccion.size, onCerrar = { seleccion = emptySet() },
                    onTodo = { seleccion = lista.map { clave(it) }.toSet() },
                    onInvertir = { seleccion = lista.map { clave(it) }.toSet() - seleccion },
                )
            } else {
                Row(
                    Modifier.fillMaxWidth().padding(start = SearchHorizontalPadding, end = 16.dp, top = 14.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LibraryTitleBlock(
                        "Actualizaciones",
                        if (Actualizador.corriendo) {
                            if (Actualizador.total > 0) "Revisando ${Actualizador.hechas} de ${Actualizador.total}…" else "Preparando la revisión…"
                        } else "Última revisión: ${hace(almacen.ultimaActualizacion)}",
                        Modifier.weight(1f),
                    )
                    Box(
                        Modifier.size(42.dp).clip(CircleShape).background(acento.copy(alpha = 0.22f))
                            .clickable { if (Actualizador.corriendo) Actualizador.cancelar(ctx) else Actualizador.iniciar(ctx) },
                        Alignment.Center,
                    ) {
                        if (Actualizador.corriendo) IndicadorCarga(Modifier.size(22.dp), strokeWidth = 2.5.dp)
                        else Icon(Icons.Rounded.Refresh, "Buscar capítulos nuevos", tint = acento, modifier = Modifier.size(21.dp))
                    }
                    Spacer(Modifier.width(8.dp))
                    Box {
                        LibraryIconButton(Icons.Rounded.MoreVert, "Más", false) { menu = true }
                        MenuShiori(expanded = menu, onDismissRequest = { menu = false }) {
                            if (almacen.erroresActualizacion.isNotEmpty()) {
                                DropdownMenuItem(
                                    text = { Text("Errores de la última revisión (${almacen.erroresActualizacion.size})") },
                                    onClick = { menu = false; verErrores = true },
                                )
                            }
                            if (lista.isNotEmpty()) {
                                DropdownMenuItem(text = { Text("Borrar la lista") }, onClick = { menu = false; confirmarLimpiar = true })
                            }
                            DropdownMenuItem(
                                text = { Text(if (Actualizador.corriendo) "Cancelar la revisión" else "Buscar capítulos nuevos") },
                                onClick = { menu = false; if (Actualizador.corriendo) Actualizador.cancelar(ctx) else Actualizador.iniciar(ctx) },
                            )
                        }
                    }
                }
                if (almacen.erroresActualizacion.isNotEmpty() && !Actualizador.corriendo) {
                    Row(
                        Modifier.padding(horizontal = SearchHorizontalPadding, vertical = 6.dp).clip(CircleShape)
                            .background(LectorColors.Error.copy(alpha = 0.16f)).pressable { verErrores = true }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.Warning, null, tint = LectorColors.Error, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        LText("${almacen.erroresActualizacion.size} no se pudieron revisar · ver", LectorType.Label, color = LectorColors.Error)
                    }
                }
            }

            PullToRefreshBox(
                isRefreshing = Actualizador.corriendo,
                onRefresh = { if (!Actualizador.corriendo) Actualizador.iniciar(ctx) },
                modifier = Modifier.weight(1f),
            ) {
                if (lista.isEmpty()) {
                    VacioActualizaciones(
                        "Todo al día",
                        "Aquí saldrán los capítulos nuevos de los mangas que añadas a tu biblioteca desde Explorar. " +
                            if (almacen.intervaloActualizacion > 0) "Se revisan solos cada ${almacen.intervaloActualizacion} h; desliza hacia abajo para revisar ya."
                            else "Desliza hacia abajo para revisar.",
                        enMarcha = Actualizador.corriendo,
                    ) { Actualizador.iniciar(ctx) }
                } else {
                    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 120.dp)) {
                        var base = 0
                        grupos.forEach { (dia, mangas) ->
                            val inicioDia = base
                            base += 1
                            stickyHeader(key = "d_$dia", contentType = 0) {
                                mangas.sumOf { it.size }.let { n ->
                                    GeneratedGroupHeader(etiquetaDia(dia), Modifier.entrada(escalonAct(inicioDia)), count = if (n == 1) "1 nuevo" else "$n nuevos")
                                }
                            }
                            mangas.forEach { filas ->
                                val primera = filas.first()
                                val kGrupo = "$dia|${primera.fuenteId}|${primera.urlManga}"
                                val comic = comics["${primera.fuenteId}|${primera.urlManga}"]
                                val pos = base
                                base += 1
                                if (filas.size == 1) {
                                    item(key = "c_${clave(primera)}", contentType = 1) {
                                        FilaActualizacion(
                                            primera, comic, almacen, ext, leidos, clave(primera) in seleccion, seleccion.isNotEmpty(),
                                            onClick = { if (seleccion.isNotEmpty()) alternar(primera) else alAbrir(primera) },
                                            onLargo = { alternar(primera) },
                                            modifier = Modifier.entrada(escalonAct(pos)),
                                            alCambiarLeido = { recalcularProgreso(listOf(primera)) },
                                        )
                                    }
                                } else {
                                    val abierto = kGrupo in abiertos
                                    item(key = "g_$kGrupo", contentType = 2) {
                                        CabeceraGrupo(
                                            primera, comic, almacen, filas.size, filas.count { ext.esLeido(it.fuenteId, it.capUrl) }, abierto,
                                            seleccionado = filas.all { clave(it) in seleccion },
                                            onClick = {
                                                if (seleccion.isNotEmpty()) {
                                                    val ks = filas.map { clave(it) }.toSet()
                                                    seleccion = if (ks.all { it in seleccion }) seleccion - ks else seleccion + ks
                                                } else abiertos = if (abierto) abiertos - kGrupo else abiertos + kGrupo
                                            },
                                            onLargo = { seleccion = seleccion + filas.map { clave(it) } },
                                            modifier = Modifier.entrada(escalonAct(pos)),
                                        )
                                    }
                                    if (abierto) itemsAnimados(filas, key = { "c_${clave(it)}" }, contentType = { 1 }) { a ->
                                        FilaActualizacion(
                                            a, comic, almacen, ext, leidos, clave(a) in seleccion, seleccion.isNotEmpty(),
                                            onClick = { if (seleccion.isNotEmpty()) alternar(a) else alAbrir(a) },
                                            onLargo = { alternar(a) }, sangria = true,
                                            alCambiarLeido = { recalcularProgreso(listOf(a)) },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        BarraAcciones(
            visible = seleccion.isNotEmpty(),
            modifier = Modifier.align(Alignment.BottomCenter),
            acciones = listOf(
                AccionBarra(Icons.Rounded.Check, "Leído") {
                    val sel = seleccionadas()
                    sel.groupBy { it.fuenteId }.forEach { (f, filas) -> ext.marcarLeidos(f, filas.map { it.capUrl }) }
                    recalcularProgreso(sel)
                    seleccion = emptySet()
                },
                AccionBarra(Iconos.OjoTachado, "No leído") {
                    val sel = seleccionadas()
                    sel.groupBy { it.fuenteId }.forEach { (f, filas) -> ext.desmarcarLeidos(f, filas.map { it.capUrl }) }
                    recalcularProgreso(sel)
                    seleccion = emptySet()
                },
                AccionBarra(Iconos.Descargar, "Descargar") {
                    var n = 0
                    seleccionadas().groupBy { it.fuenteId to it.urlManga }.forEach { (k, filas) ->
                        val f = ext.fuentes.firstOrNull { it.id == k.first } ?: return@forEach
                        n += Descargas.encolar(
                            ctx, f, k.second, filas.first().titulo,
                            filas.map { com.example.lector.CacheFichas.conMemo(it.fuenteId, it.urlManga, CapituloFuente(it.capUrl, it.capNombre, it.capFecha, it.scanlator, it.capNumero)) }.asReversed(),
                            avisar = false,
                        )
                    }
                    Avisos.mostrar(if (n == 0) "Ya estaban descargados" else "$n capítulo(s) en la cola de descargas")
                    seleccion = emptySet()
                },
                AccionBarra(Icons.Rounded.Delete, "Quitar") {
                    almacen.quitarActualizaciones(seleccion)
                    seleccion = emptySet()
                },
            ),
        )
    }

    if (confirmarLimpiar) {
        DialogoAlerta(
            onDismissRequest = { confirmarLimpiar = false },
            title = { Text("¿Borrar las actualizaciones?") },
            text = { Text("Solo se vacía esta lista. Tu biblioteca y tu progreso no cambian.") },
            confirmButton = { TextButton(onClick = { almacen.limpiarActualizaciones(); confirmarLimpiar = false }) { Text("Borrar") } },
            dismissButton = { TextButton(onClick = { confirmarLimpiar = false }) { Text("Cancelar") } },
        )
    }

    if (verErrores) {
        DialogoAlerta(
            onDismissRequest = { verErrores = false },
            title = { Text("No se pudieron revisar") },
            text = {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    almacen.erroresActualizacion.groupBy { it.motivo }.forEach { (motivo, errs) ->
                        Text(motivo, color = MaterialTheme.colorScheme.error, style = LectorType.Body)
                        // Por fuente: si la extensión está en algún repositorio, se dice cuál instalar; tocar un título
                        // lo busca en las fuentes instaladas (búsqueda global). Nada corre en segundo plano.
                        errs.groupBy { it.fuente }.forEach { (fuente, deFuente) ->
                            val id = Regex("\\d{5,}").find(fuente)?.value?.toLongOrNull()
                            val enRepo = id?.let { i -> ext.disponibles.firstOrNull { d -> d.fuentes.any { it.id == i } } }
                            val fi = enRepo?.fuentes?.firstOrNull { it.id == id }
                            Text(
                                when {
                                    enRepo != null -> "${fi?.nombre ?: enRepo.nombre} · instala «${enRepo.nombre}» (${enRepo.repoSigla})" + (fi?.baseUrl?.takeIf { it.isNotBlank() }?.let { "\n$it" } ?: "")
                                    else -> "$fuente · no está en tus repositorios"
                                },
                                style = LectorType.Label.copy(fontWeight = FontWeight.Bold),
                                color = if (enRepo != null) MaterialTheme.colorScheme.primary else LectorColors.Muted,
                                modifier = Modifier.padding(start = 8.dp, top = 6.dp),
                            )
                            deFuente.forEach { e ->
                                Text(
                                    "• ${e.titulo}  ⌕", style = LectorType.Label,
                                    modifier = Modifier.fillMaxWidth().clickable { verErrores = false; alBuscarGlobal(e.titulo) }.padding(start = 8.dp, top = 3.dp, bottom = 3.dp),
                                )
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                    }
                }
            },
            confirmButton = { TextButton(onClick = { verErrores = false }) { Text("Cerrar") } },
            dismissButton = { TextButton(onClick = { almacen.guardarErrores(emptyList()); verErrores = false }) { Text("Olvidar") } },
        )
    }
}

private var abrirMangaAct: (ActualizacionCap) -> Unit = {}

/** Solo la primera pantalla aparece escalonada; lo que llega luego al hacer scroll aparece sin retraso. */
private fun escalonAct(i: Int): Int = if (i < 10) i else 0

@Composable
private fun VacioActualizaciones(titulo: String, texto: String, enMarcha: Boolean, onRevisar: () -> Unit) {
    Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        LibraryEmptyState(
            Modifier.entrada(0).padding(top = 24.dp, bottom = 160.dp), texto, titulo,
            if (enMarcha) "Revisando…" else "Buscar ahora", onRevisar.takeIf { !enMarcha } ?: {},
        )
    }
}

@Composable
private fun PortadaActualizacion(a: ActualizacionCap, comic: Comic?, almacen: Almacen) {
    // Tocar la portada abre el manga (su ficha), no el capítulo.
    val forma = SuaveShape(LibraryRowCoverCorner)
    Box(Modifier.size(LibraryRowCoverSize).clip(forma).background(LibraryColors.TileHigh).clickable { abrirMangaAct(a) }) {
        if (comic != null) ComicCover(comic, almacen, Modifier.fillMaxSize(), forma)
        else ImagenRemota(a.portadaUrl, Modifier.fillMaxSize(), rememberCabeceras(a.fuenteId), fuenteId = a.fuenteId)
    }
}

/** Selección con muelle: la fila se tiñe como la luz de pulsación de Mochy, pero fija. */
@Composable
private fun seleccionAct(seleccionado: Boolean): Float {
    val s by animateFloatAsState(if (seleccionado) 1f else 0f, if (Mov.reducido) snap() else Muelles.espacial(), label = "selAct")
    return s
}

@Composable
private fun CabeceraGrupo(
    a: ActualizacionCap, comic: Comic?, almacen: Almacen, cuantos: Int, leidos: Int, abierto: Boolean, seleccionado: Boolean,
    onClick: () -> Unit, onLargo: () -> Unit, modifier: Modifier = Modifier,
) {
    val acento = MaterialTheme.colorScheme.primary
    val haptic = LocalHapticFeedback.current
    val giro by animateFloatAsState(if (abierto) 90f else 0f, if (Mov.reducido) snap() else Muelles.espacial(), label = "giroGrupo")
    val todoLeido = leidos == cuantos
    HomeTrackRow(
        title = comic?.titulo ?: a.titulo,
        subtitle = "$cuantos capítulos" + if (leidos > 0) " • $leidos leídos" else "",
        subtitleColor = if (todoLeido) null else acento,
        dimmed = todoLeido,
        onClick = onClick,
        onLongClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onLargo() },
        selected = seleccionAct(seleccionado),
        modifier = modifier.fillMaxWidth().padding(horizontal = SearchHorizontalPadding, vertical = 3.dp),
        cover = { PortadaActualizacion(a, comic, almacen) },
        trailing = {
            Box(Modifier.size(38.dp), Alignment.Center) {
                Box(
                    Modifier.size(28.dp).background(if (abierto) acento.copy(alpha = 0.22f) else LibraryColors.Primary.copy(alpha = 0.14f), CircleShape),
                    Alignment.Center,
                ) {
                    Icon(
                        Icons.Rounded.KeyboardArrowRight, if (abierto) "Plegar" else "Desplegar",
                        tint = if (abierto) acento else LibraryColors.Primary.copy(alpha = 0.85f),
                        modifier = Modifier.size(19.dp).graphicsLayer { rotationZ = giro },
                    )
                }
            }
        },
    )
}

@Composable
private fun FilaActualizacion(
    a: ActualizacionCap, comic: Comic?, almacen: Almacen, ext: ExtManager, leidos: Int,
    seleccionado: Boolean, enSeleccion: Boolean,
    onClick: () -> Unit, onLargo: () -> Unit, sangria: Boolean = false, modifier: Modifier = Modifier,
    alCambiarLeido: () -> Unit = {},
) {
    val ctx = LocalContext.current
    val acento = MaterialTheme.colorScheme.primary
    val haptic = LocalHapticFeedback.current
    // [leidos] solo sirve para repintar cuando cambia algún «leído».
    val leido = leidos.let { ext.esLeido(a.fuenteId, a.capUrl) }
    val nombre = a.capNombre.trim().trimEnd(':').trim().ifBlank { "Capítulo" }
    val k = clave(a)
    val fraccion = Descargas.progreso[k] ?: if (Descargas.enCola(k)) 0f else null
    val version = Descargas.version.value
    val estado: EstadoDescarga = when {
        fraccion != null -> EstadoDescarga.Descargando(fraccion)
        version >= 0 && almacen.estaDescargado(a.fuenteId, a.capUrl) -> EstadoDescarga.Listo
        else -> EstadoDescarga.Libre
    }
    val hora = remember(a.detectado) { DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(a.detectado)) }

    val descargar = {
        val f = ext.fuentes.firstOrNull { it.id == a.fuenteId }
        when {
            estado is EstadoDescarga.Descargando -> Descargas.cancelar(ctx, k)
            estado is EstadoDescarga.Listo -> { almacen.borrarDescarga(a.fuenteId, a.capUrl); Descargas.version.value++ }
            f == null -> Avisos.mostrar("Esa fuente no está disponible ahora")
            else -> Descargas.encolar(ctx, f, a.urlManga, a.titulo, listOf(com.example.lector.CacheFichas.conMemo(a.fuenteId, a.urlManga, CapituloFuente(a.capUrl, a.capNombre, a.capFecha, a.scanlator, a.capNumero))))
        }
    }
    com.example.lector.ui.mochy.ChapterSwipeBox(
        estado = com.example.lector.ui.mochy.EstadoCapituloSwipe(
            leido = leido, marcado = ext.esMarcado(a.fuenteId, a.capUrl),
            descargado = estado is EstadoDescarga.Listo, descargando = estado is EstadoDescarga.Descargando,
        ),
        enabled = !enSeleccion,
        onAction = { acc ->
            when (acc) {
                com.example.lector.ui.mochy.ChapterSwipeAction.MARK_READ -> {
                    if (leido) ext.desmarcarLeido(a.fuenteId, a.capUrl) else ext.marcarLeido(a.fuenteId, a.capUrl); alCambiarLeido()
                }
                com.example.lector.ui.mochy.ChapterSwipeAction.MARK_PREVIOUS_READ -> { ext.marcarLeido(a.fuenteId, a.capUrl); alCambiarLeido() }
                com.example.lector.ui.mochy.ChapterSwipeAction.BOOKMARK -> ext.alternarMarcado(a.fuenteId, a.capUrl)
                com.example.lector.ui.mochy.ChapterSwipeAction.DOWNLOAD -> descargar()
                else -> Unit
            }
        },
    ) {
    HomeTrackRow(
        title = if (sangria) nombre else comic?.titulo ?: a.titulo,
        subtitle = if (sangria) hora else "$nombre • $hora",
        subtitleColor = if (!sangria && !leido) acento else null,
        dimmed = leido,
        onClick = onClick,
        onLongClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onLargo() },
        selected = seleccionAct(seleccionado),
        modifier = modifier.fillMaxWidth()
            .padding(start = if (sangria) SearchHorizontalPadding + 30.dp else SearchHorizontalPadding, end = SearchHorizontalPadding, top = 3.dp, bottom = 3.dp),
        cover = {
            if (!sangria) PortadaActualizacion(a, comic, almacen)
            else Box(Modifier.padding(start = 4.dp).size(8.dp).clip(CircleShape).background(if (leido) LectorColors.PillFill else acento))
        },
        trailing = {
            if (!enSeleccion) Box(Modifier.size(38.dp), Alignment.Center) {
                BotonDescarga(estado, onClick = {
                    if (estado is EstadoDescarga.Listo) Avisos.mostrar("Ya está descargado") else descargar()
                })
            }
        },
    )
    }
}
