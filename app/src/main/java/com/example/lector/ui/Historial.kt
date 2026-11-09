package com.example.lector.ui

import kotlinx.coroutines.launch
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.example.lector.progresoDe
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.lector.Almacen
import com.example.lector.Comic
import com.example.lector.EntradaHistorial
import java.text.DateFormat
import java.util.Calendar
import java.util.Date
import com.example.lector.ui.mochy.library.GeneratedGroupHeader
import com.example.lector.ui.mochy.library.HomeTrackRow
import com.example.lector.ui.mochy.library.LibraryColors
import com.example.lector.ui.mochy.library.LibraryEmptyState
import com.example.lector.ui.mochy.library.LibraryIconButton
import com.example.lector.ui.mochy.library.LibraryRowCoverCorner
import com.example.lector.ui.mochy.library.LibraryRowCoverSize
import com.example.lector.ui.mochy.library.LibraryTitleBlock
import com.example.lector.ui.mochy.library.RowSmallAction
import com.example.lector.ui.mochy.library.SearchHorizontalPadding
import com.example.lector.ui.mochy.library.TrackSubtitleStyle

// Una fila del historial: un cómic local o un manga leído online.
private sealed class ItemHistorial {
    abstract val tiempo: Long
    abstract val clave: String
    abstract val titulo: String

    class Local(val comic: Comic) : ItemHistorial() {
        override val tiempo get() = comic.leido
        override val clave get() = "c_${comic.id}"
        override val titulo get() = comic.titulo
    }

    class EnLinea(val entrada: EntradaHistorial, val comic: Comic?) : ItemHistorial() {
        override val tiempo get() = entrada.leido
        override val clave get() = "o_${entrada.fuenteId}_${entrada.urlManga}"
        override val titulo get() = comic?.titulo ?: entrada.titulo
    }
}

private fun inicioDelDiaH(ms: Long): Long = Calendar.getInstance().apply {
    timeInMillis = ms
    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}.timeInMillis

/**
 * Historial (como el de Komikku): lo último que has leído, por día; un manga ocupa una fila con el capítulo y la
 * página por los que vas. Tocar = seguir leyendo; el botón de la papelera (o mantener pulsado) lo quita del historial.
 * Hay buscador y aviso del modo incógnito.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HistorialScreen(
    comics: List<Comic>,
    almacen: Almacen,
    alAbrir: (Comic) -> Unit,
    alAbrirOnline: (EntradaHistorial) -> Unit,
    modifier: Modifier = Modifier,
    alAbrirManga: (EntradaHistorial) -> Unit = {},
) {
    abrirMangaHist = alAbrirManga
    var consulta by remember { mutableStateOf("") }
    var buscando by remember { mutableStateOf(false) }
    var quitar by remember { mutableStateOf<ItemHistorial?>(null) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var confirmarTodo by remember { mutableStateOf(false) }

    val idsVisibles = remember(comics) { comics.map { it.id }.toSet() }
    val porClave = remember(almacen.version) { almacen.cargar().filter { it.enLinea }.associateBy { "${it.fuenteId}|${it.urlManga}" } }
    val entradas = remember(comics, almacen.historialLinea, porClave, consulta, com.example.lector.ModoContenido.modo) {
        val locales = comics.filter { it.leido > 0 && !it.enLinea }.map { ItemHistorial.Local(it) }
        // Un manga de una categoría oculta tampoco sale aquí.
        val enLinea = almacen.historialLinea.mapNotNull { e ->
            val c = porClave["${e.fuenteId}|${e.urlManga}"]
            if (c != null && c.id !in idsVisibles) null
            else if (!com.example.lector.ModoContenido.permite(e.fuenteId, c?.generos)) null
            else ItemHistorial.EnLinea(e, c)
        }
        val q = consulta.trim()
        (locales + enLinea).filter { q.isEmpty() || it.titulo.contains(q, ignoreCase = true) }.sortedByDescending { it.tiempo }
    }
    val grupos = remember(entradas) { entradas.groupBy { inicioDelDiaH(it.tiempo) } }
    val formatoHora = remember { DateFormat.getTimeInstance(DateFormat.SHORT) }

    val acento = MaterialTheme.colorScheme.primary
    Column(modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = SearchHorizontalPadding, end = 16.dp, top = 14.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LibraryTitleBlock("Historial", null, Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val estiloSub = TrackSubtitleStyle.copy(fontSize = 12.sp)
                    Odometro("${entradas.size}", estiloSub, color = LibraryColors.Secondary)
                    LText(if (entradas.size == 1) " lectura" else " lecturas", estiloSub, color = LibraryColors.Secondary, maxLines = 1)
                }
            }
            LibraryIconButton(if (buscando) Icons.Rounded.Close else Icons.Rounded.Search, "Buscar", buscando) {
                buscando = !buscando; if (!buscando) consulta = ""
            }
            if (entradas.isNotEmpty()) {
                Spacer(Modifier.width(8.dp))
                LibraryIconButton(Icons.Rounded.Delete, "Borrar el historial", false) { confirmarTodo = true }
            }
        }
        if (almacen.incognito) {
            Row(
                Modifier.padding(horizontal = SearchHorizontalPadding, vertical = 6.dp).clip(CircleShape)
                    .background(acento).pressable { almacen.incognito = false }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Lock, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                LText("Modo incógnito: no se anota nada · tocar para salir", LectorType.Label, color = MaterialTheme.colorScheme.onPrimary)
            }
        }
        AnimatedVisibility(
            visible = buscando,
            enter = if (Mov.reducido) fadeIn() else expandVertically(Muelles.espacial()) + fadeIn(Muelles.efectos()),
            exit = if (Mov.reducido) fadeOut() else shrinkVertically(Muelles.espacialRapido()) + fadeOut(Muelles.efectos()),
        ) {
            Box(Modifier.padding(horizontal = SearchHorizontalPadding - 8.dp, vertical = 6.dp)) { CampoBusqueda(consulta, { consulta = it }, "Buscar en el historial") }
        }

        if (entradas.isEmpty()) {
            if (consulta.isBlank()) VacioHistorial("Aún no has leído nada", "Lo que leas aparecerá aquí, ordenado por día, para retomarlo con un toque.")
            else VacioHistorial("Sin resultados", "Nada coincide con «$consulta».")
        } else {
            LazyColumn(contentPadding = PaddingValues(bottom = 190.dp)) {
                var base = 0
                grupos.forEach { (dia, lista) ->
                    val inicio = base
                    base += lista.size + 1
                    stickyHeader(key = "dia_$dia", contentType = 0) {
                        GeneratedGroupHeader(etiquetaDia(dia), Modifier.entrada(escalonHist(inicio)), count = "${lista.size}")
                    }
                    itemsIndexedAnimados(lista, key = { _, it -> it.clave }, contentType = { _, _ -> 1 }) { i, item ->
                        FilaHistorial(item, almacen, formatoHora.format(Date(item.tiempo)),
                            alAbrir = {
                                when (item) {
                                    is ItemHistorial.Local -> alAbrir(item.comic)
                                    is ItemHistorial.EnLinea -> alAbrirOnline(item.entrada)
                                }
                            },
                            alQuitar = { quitar = item },
                            modifier = Modifier.entrada(escalonHist(inicio + 1 + i)),
                        )
                    }
                }
            }
        }
    }

    quitar?.let { item ->
        DialogoAlerta(
            onDismissRequest = { quitar = null },
            title = { Text("Quitar del historial") },
            text = { Text("«${item.titulo}» dejará de aparecer aquí. Tu progreso de lectura no se borra.") },
            confirmButton = {
                TextButton(onClick = {
                    when (item) {
                        is ItemHistorial.Local -> almacen.quitarDelHistorial(item.comic.id)
                        is ItemHistorial.EnLinea -> almacen.quitarHistorialLinea(item.entrada.fuenteId, item.entrada.urlManga)
                    }
                    quitar = null
                }) { Text("Quitar") }
            },
            dismissButton = { TextButton(onClick = { quitar = null }) { Text("Cancelar") } },
        )
    }

    if (confirmarTodo) {
        DialogoAlerta(
            onDismissRequest = { confirmarTodo = false },
            title = { Text("Borrar el historial") },
            text = { Text("Se vaciará el historial. Tu progreso de lectura no se borra.") },
            confirmButton = { TextButton(onClick = { almacen.limpiarHistorial(); confirmarTodo = false }) { Text("Borrar") } },
            dismissButton = { TextButton(onClick = { confirmarTodo = false }) { Text("Cancelar") } },
        )
    }
}

private var abrirMangaHist: (EntradaHistorial) -> Unit = {}

/** Solo la primera pantalla aparece escalonada; lo que llega luego al hacer scroll aparece sin retraso. */
private fun escalonHist(i: Int): Int = if (i < 10) i else 0

/** LibraryEmptyState de Mochy. */
@Composable
private fun VacioHistorial(titulo: String, texto: String) {
    Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        LibraryEmptyState(Modifier.entrada(0).padding(top = 24.dp, bottom = 160.dp), texto, titulo)
    }
}

/** PlaylistTrackRow / HomeTrackRow de Mochy: portada de 54dp, dos líneas y la papelera como botón «more». */
@Composable
private fun FilaHistorial(item: ItemHistorial, almacen: Almacen, hora: String, alAbrir: () -> Unit, alQuitar: () -> Unit, modifier: Modifier = Modifier) {
    val acento = MaterialTheme.colorScheme.primary
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val formaPortada = SuaveShape(LibraryRowCoverCorner)
    val (principal, progreso) = when (item) {
        is ItemHistorial.Local -> item.comic.etiquetaProgreso() to progresoDe(item.comic)
        is ItemHistorial.EnLinea -> {
            val e = item.entrada
            val cap = e.capNombre.trim().trimEnd(':').trim().ifBlank { "Capítulo" }
            val pag = if (e.total > 0) " · Pág. ${e.pagina + 1}/${e.total}" else ""
            "$cap$pag" to (if (e.total > 0) (e.pagina + 1).toFloat() / e.total else 0f)
        }
    }
    val e = (item as? ItemHistorial.EnLinea)?.entrada
    if (e == null) FilaHistorialBase(item, almacen, hora, principal, progreso, acento, haptic, formaPortada, alAbrir, alQuitar, modifier)
    else {
        val ctx = androidx.compose.ui.platform.LocalContext.current
        val ext = remember { com.example.lector.ext.ExtManager.obtener(ctx) }
        val leido = ext.leidos.let { ext.esLeido(e.fuenteId, e.capUrl) }
        val descargado = com.example.lector.Descargas.version.value >= 0 && almacen.estaDescargado(e.fuenteId, e.urlManga, e.capUrl)
        com.example.lector.ui.mochy.ChapterSwipeBox(
            estado = com.example.lector.ui.mochy.EstadoCapituloSwipe(leido, ext.esMarcado(e.fuenteId, e.capUrl), descargado),
            onAction = { acc ->
                when (acc) {
                    com.example.lector.ui.mochy.ChapterSwipeAction.MARK_READ ->
                        if (leido) ext.desmarcarLeido(e.fuenteId, e.capUrl) else ext.marcarLeido(e.fuenteId, e.capUrl)
                    com.example.lector.ui.mochy.ChapterSwipeAction.MARK_PREVIOUS_READ -> ext.marcarLeido(e.fuenteId, e.capUrl)
                    com.example.lector.ui.mochy.ChapterSwipeAction.BOOKMARK -> ext.alternarMarcado(e.fuenteId, e.capUrl)
                    com.example.lector.ui.mochy.ChapterSwipeAction.DOWNLOAD -> {
                        val f = ext.fuentes.firstOrNull { it.id == e.fuenteId }
                        when {
                            descargado -> { almacen.borrarDescarga(e.fuenteId, e.urlManga, e.capUrl); com.example.lector.Descargas.version.value++ }
                            f == null -> Avisos.mostrar("Esa fuente no está disponible ahora")
                            else -> com.example.lector.Descargas.encolar(ctx, f, e.urlManga, e.titulo, listOf(com.example.lector.CacheFichas.conMemo(e.fuenteId, e.urlManga, com.example.lector.ext.CapituloFuente(e.capUrl, e.capNombre, 0L, null, -1f))))
                        }
                    }
                    else -> Unit
                }
            },
        ) { FilaHistorialBase(item, almacen, hora, principal, progreso, acento, haptic, formaPortada, alAbrir, alQuitar, modifier) }
    }
}

@Composable
private fun FilaHistorialBase(
    item: ItemHistorial, almacen: Almacen, hora: String, principal: String, progreso: Float, acento: androidx.compose.ui.graphics.Color,
    haptic: androidx.compose.ui.hapticfeedback.HapticFeedback, formaPortada: androidx.compose.ui.graphics.Shape,
    alAbrir: () -> Unit, alQuitar: () -> Unit, modifier: Modifier,
) {
    HomeTrackRow(
        title = item.titulo,
        subtitle = "$principal • $hora",
        onClick = alAbrir,
        onLongClick = { haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress); alQuitar() },
        modifier = modifier.fillMaxWidth().padding(horizontal = SearchHorizontalPadding, vertical = 3.dp),
        cover = {
            Box(Modifier.size(LibraryRowCoverSize).clip(formaPortada).background(LibraryColors.TileHigh)) {
                val portada = Modifier.fillMaxSize()
                when (item) {
                    is ItemHistorial.Local -> ComicCover(item.comic, almacen, portada, formaPortada)
                    is ItemHistorial.EnLinea -> {
                        // Tocar la portada abre el manga (su ficha), no el capítulo.
                        val p = portada.clickable { abrirMangaHist(item.entrada) }
                        if (item.comic != null) ComicCover(item.comic, almacen, p, formaPortada)
                        else ImagenRemota(item.entrada.portadaUrl, p, rememberCabeceras(item.entrada.fuenteId), fuenteId = item.entrada.fuenteId)
                    }
                }
                if (progreso > 0f && progreso < 1f) {
                    Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(3.dp).background(androidx.compose.ui.graphics.Color(0x66000000))) {
                        Box(Modifier.fillMaxWidth(progreso.coerceIn(0f, 1f)).fillMaxHeight().background(acento))
                    }
                }
            }
        },
        trailing = { RowSmallAction(Icons.Rounded.Delete, "Quitar del historial", alQuitar) },
    )
}
