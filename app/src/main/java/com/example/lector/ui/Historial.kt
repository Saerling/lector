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
    val entradas = remember(comics, almacen.historialLinea, porClave, consulta) {
        val locales = comics.filter { it.leido > 0 && !it.enLinea }.map { ItemHistorial.Local(it) }
        // Un manga de una categoría oculta tampoco sale aquí.
        val enLinea = almacen.historialLinea.mapNotNull { e ->
            val c = porClave["${e.fuenteId}|${e.urlManga}"]
            if (c != null && c.id !in idsVisibles) null else ItemHistorial.EnLinea(e, c)
        }
        val q = consulta.trim()
        (locales + enLinea).filter { q.isEmpty() || it.titulo.contains(q, ignoreCase = true) }.sortedByDescending { it.tiempo }
    }
    val grupos = remember(entradas) { entradas.groupBy { inicioDelDiaH(it.tiempo) } }
    val formatoHora = remember { DateFormat.getTimeInstance(DateFormat.SHORT) }

    val acento = MaterialTheme.colorScheme.primary
    Column(modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 22.dp, end = 14.dp, top = 16.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                LText("Historial", LectorType.Display.copy(fontSize = 40.sp, lineHeight = 44.sp), maxLines = 1)
                Spacer(Modifier.height(4.dp))
                Row(
                    Modifier.clip(CircleShape).background(acento.copy(alpha = 0.18f)).padding(horizontal = 10.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Odometro("${entradas.size}", LectorType.Label.copy(fontWeight = FontWeight.Bold), color = acento)
                    LText(if (entradas.size == 1) " lectura" else " lecturas", LectorType.Label, color = acento, maxLines = 1)
                }
            }
            BotonRedondoHist(if (buscando) Icons.Rounded.Close else Icons.Rounded.Search, "Buscar", buscando) {
                buscando = !buscando; if (!buscando) consulta = ""
            }
            if (entradas.isNotEmpty()) {
                Spacer(Modifier.width(8.dp))
                BotonRedondoHist(Icons.Rounded.Delete, "Borrar el historial", false) { confirmarTodo = true }
            }
        }
        if (almacen.incognito) {
            Row(
                Modifier.padding(horizontal = 20.dp, vertical = 6.dp).clip(CircleShape)
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
            Box(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) { CampoBusqueda(consulta, { consulta = it }, "Buscar en el historial") }
        }

        if (entradas.isEmpty()) {
            if (consulta.isBlank()) VacioHistorial(Icons.Rounded.DateRange, "Aún no has leído nada", "Lo que leas aparecerá aquí, ordenado por día, para retomarlo con un toque.")
            else VacioHistorial(Icons.Rounded.Search, "Sin resultados", "Nada coincide con «$consulta».")
        } else {
            LazyColumn(contentPadding = PaddingValues(bottom = 190.dp)) {
                var base = 0
                grupos.forEach { (dia, lista) ->
                    val inicio = base
                    base += lista.size + 1
                    stickyHeader(key = "dia_$dia", contentType = 0) {
                        CabeceraDiaHist(etiquetaDia(dia), lista.size, Modifier.entrada(escalonHist(inicio)))
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

@Composable
private fun BotonRedondoHist(icono: ImageVector, desc: String, activo: Boolean, onClick: () -> Unit) {
    val acento = MaterialTheme.colorScheme.primary
    val fondo by animateColorAsState(if (activo) acento.copy(alpha = 0.22f) else LectorColors.PillFill, Muelles.efectos(), label = "fondoHist")
    val tinta by animateColorAsState(if (activo) acento else LectorColors.Text, Muelles.efectos(), label = "tintaHist")
    Box(Modifier.size(44.dp).pressableCon(0.88f, onClick).clip(CircleShape).background(fondo), Alignment.Center) {
        Icon(icono, desc, tint = tinta, modifier = Modifier.size(22.dp))
    }
}

/** Cabecera de día grande y pegajosa (tapa las filas que pasan por debajo con el fondo de la pantalla). */
@Composable
private fun CabeceraDiaHist(texto: String, cuantos: Int, modifier: Modifier) {
    Row(
        modifier.fillMaxWidth().background(LectorColors.Bg).padding(start = 22.dp, end = 22.dp, top = 18.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LText(texto, LectorType.Hero.copy(fontSize = 22.sp, lineHeight = 26.sp), Modifier.weight(1f, fill = false), maxLines = 1)
        Spacer(Modifier.width(10.dp))
        Box(Modifier.clip(CircleShape).background(LectorColors.PillFill).padding(horizontal = 9.dp, vertical = 2.dp)) {
            LText("$cuantos", LectorType.Label.copy(fontWeight = FontWeight.Bold), color = LectorColors.Muted, maxLines = 1)
        }
    }
}

@Composable
private fun VacioHistorial(icono: ImageVector, titulo: String, texto: String) {
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
        }
    }
}

@Composable
private fun FilaHistorial(item: ItemHistorial, almacen: Almacen, hora: String, alAbrir: () -> Unit, alQuitar: () -> Unit, modifier: Modifier = Modifier) {
    val acento = MaterialTheme.colorScheme.primary
    val formaPortada = SuaveShape(14.dp)
    Row(
        modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
            .pressableCon(0.97f, alQuitar, alAbrir)
            .clip(SuaveShape(22.dp)).background(LectorColors.Card)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val portada = Modifier.width(56.dp).aspectRatio(2f / 3f)
        when (item) {
            is ItemHistorial.Local -> ComicCover(item.comic, almacen, portada, formaPortada)
            is ItemHistorial.EnLinea -> {
                // Tocar la portada abre el manga (su ficha), no el capítulo.
                val portada = portada.clip(formaPortada).clickable { abrirMangaHist(item.entrada) }
                if (item.comic != null) ComicCover(item.comic, almacen, portada, formaPortada)
                else ImagenRemota(item.entrada.portadaUrl, portada.clip(formaPortada), rememberCabeceras(item.entrada.fuenteId), fuenteId = item.entrada.fuenteId)
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            LText(item.titulo, LectorType.Section.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold), maxLines = 2)
            Spacer(Modifier.height(3.dp))
            val (principal, progreso) = when (item) {
                is ItemHistorial.Local -> item.comic.etiquetaProgreso() to progresoDe(item.comic)
                is ItemHistorial.EnLinea -> {
                    val e = item.entrada
                    val cap = e.capNombre.trim().trimEnd(':').trim().ifBlank { "Capítulo" }
                    val pag = if (e.total > 0) " · Pág. ${e.pagina + 1}/${e.total}" else ""
                    "$cap$pag" to (if (e.total > 0) (e.pagina + 1).toFloat() / e.total else 0f)
                }
            }
            LText(principal, LectorType.Body, color = acento, maxLines = 1)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (progreso > 0f) {
                    Box(Modifier.width(56.dp).height(4.dp).clip(CircleShape).background(LectorColors.PillFill)) {
                        Box(Modifier.fillMaxWidth(progreso.coerceIn(0f, 1f)).fillMaxHeight().clip(CircleShape).background(acento))
                    }
                    Spacer(Modifier.width(8.dp))
                }
                LText(hora, LectorType.Label, color = LectorColors.Muted, maxLines = 1)
            }
        }
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier.size(42.dp).pressableCon(0.85f, alQuitar).clip(CircleShape).background(LectorColors.PillFill),
            Alignment.Center,
        ) {
            Icon(Icons.Rounded.Delete, "Quitar del historial", tint = LectorColors.Muted, modifier = Modifier.size(20.dp))
        }
    }
}
