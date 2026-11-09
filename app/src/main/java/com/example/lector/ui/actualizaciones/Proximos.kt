// Copiado de Komikku (https://github.com/komikku-app/komikku, Apache-2.0): app/src/main/java/mihon/feature/upcoming/
// (UpcomingScreenModel.kt, UpcomingScreenContent.kt, components/calendar/Calendar.kt, CalendarDay.kt, CalendarHeader.kt,
// CalendarIndicator.kt) y domain/.../FetchInterval.kt (calculateInterval), adaptado a Shiori.
package com.example.lector.ui.actualizaciones

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.lector.Almacen
import com.example.lector.CacheFichas
import com.example.lector.Comic
import com.example.lector.ui.ComicCover
import com.example.lector.ui.LText
import com.example.lector.ui.LectorColors
import com.example.lector.ui.LectorType
import com.example.lector.ui.SuaveShape
import com.example.lector.ui.mochy.library.GeneratedGroupHeader
import com.example.lector.ui.mochy.library.HomeTrackRow
import com.example.lector.ui.mochy.library.LibraryIconButton
import com.example.lector.ui.mochy.library.LibraryRowCoverCorner
import com.example.lector.ui.mochy.library.LibraryRowCoverSize
import com.example.lector.ui.mochy.library.LibraryTitleBlock
import com.example.lector.ui.mochy.library.SearchHorizontalPadding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.time.temporal.WeekFields
import java.util.Locale

/** Un manga de la biblioteca con su próxima actualización esperada. */
data class MangaProximo(val comic: Comic, val fecha: LocalDate, val intervalo: Int, val ultima: LocalDate)

private const val MAX_INTERVALO = 28

/** FetchInterval.calculateInterval de Mihon/Komikku: mediana de los días entre las últimas fechas distintas. */
internal fun calcularIntervalo(fechasSubida: List<Long>, fechasDeteccion: List<Long>, zona: ZoneId): Int {
    val ventana = if (fechasSubida.size <= 8) 3 else 10
    fun dias(ms: List<Long>) = ms.asSequence().filter { it > 0L }.sortedDescending()
        .map { Instant.ofEpochMilli(it).atZone(zona).toLocalDate() }.distinct().take(ventana).toList()
    fun mediana(d: List<LocalDate>): Int {
        val rangos = d.windowed(2).map { x -> ChronoUnit.DAYS.between(x[1], x[0]) }.sorted()
        return rangos[(rangos.size - 1) / 2].toInt()
    }
    val subida = dias(fechasSubida)
    val deteccion = dias(fechasDeteccion)
    val intervalo = when {
        subida.size >= 3 -> mediana(subida)
        deteccion.size >= 3 -> mediana(deteccion)
        else -> 7
    }
    return intervalo.coerceIn(1, MAX_INTERVALO)
}

/**
 * Como GetUpcomingManga: mangas en emisión (o de estado desconocido, que en Shiori es lo habitual) con su próxima fecha
 * esperada = última novedad + intervalo. Si esa fecha ya pasó, se avanza de intervalo en intervalo hasta hoy; los que
 * llevan mucho sin novedades (más de 4 intervalos y 2 meses) se dejan fuera.
 */
fun calcularProximos(almacen: Almacen): List<MangaProximo> {
    val zona = ZoneId.systemDefault()
    val hoy = LocalDate.now(zona)
    val detectados = almacen.actualizaciones.groupBy { "${it.fuenteId}|${it.urlManga}" }
    return almacen.cargar().asSequence()
        .filter { it.enLinea && it.estado in setOf(0, 1, 4) }
        .mapNotNull { c ->
            val url = c.urlManga ?: return@mapNotNull null
            val caps = runCatching { CacheFichas.leer(c.fuenteId, url)?.capitulos }.getOrNull().orEmpty()
            val subidas = caps.map { it.fecha }
            val dets = detectados["${c.fuenteId}|$url"].orEmpty().map { it.detectado }
            val ultimaMs = (subidas + dets + c.ultimoCapitulo).filter { it > 0L }.maxOrNull() ?: return@mapNotNull null
            val intervalo = calcularIntervalo(subidas, dets, zona)
            val ultima = Instant.ofEpochMilli(ultimaMs).atZone(zona).toLocalDate()
            val atraso = ChronoUnit.DAYS.between(ultima, hoy)
            if (atraso > maxOf(4L * intervalo, 60L)) return@mapNotNull null
            var proxima = ultima.plusDays(intervalo.toLong())
            while (proxima.isBefore(hoy)) proxima = proxima.plusDays(intervalo.toLong())
            MangaProximo(c, proxima, intervalo, ultima)
        }
        .sortedWith(compareBy({ it.fecha }, { it.comic.titulo.lowercase() }))
        .toList()
}

/** Pantalla «Próximos» (UpcomingScreen de Komikku): calendario con puntos por día y lista agrupada por fecha. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ProximosScreen(almacen: Almacen, onVolver: () -> Unit, onAbrir: (Comic) -> Unit, modifier: Modifier = Modifier) {
    BackHandler(onBack = onVolver)
    var items by remember { mutableStateOf<List<MangaProximo>?>(null) }
    LaunchedEffect(almacen.version) { items = withContext(Dispatchers.IO) { calcularProximos(almacen) } }
    var mes by remember { mutableStateOf(YearMonth.now()) }
    val estadoLista = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val porDia = remember(items) { items.orEmpty().groupBy { it.fecha } }
    val eventos = remember(porDia) { porDia.mapValues { it.value.size } }
    // Índice de cada cabecera en la lista (getHeaderIndexes); el calendario ocupa el índice 0.
    val indices = remember(porDia) {
        var i = 1
        porDia.entries.associate { (d, l) -> (d to i).also { i += 1 + l.size } }
    }
    val formatoDia = remember { DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", Locale.getDefault()) }

    Column(modifier.fillMaxSize().background(LectorColors.Bg)) {
        Row(
            Modifier.fillMaxWidth().padding(start = SearchHorizontalPadding, end = 16.dp, top = 14.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LibraryIconButton(Icons.Rounded.ArrowBack, "Volver", false, onVolver)
            Spacer(Modifier.width(12.dp))
            LibraryTitleBlock(
                "Próximos",
                items?.let { if (it.isEmpty()) "Sin fechas previstas" else "${it.size} mangas con fecha prevista" } ?: "Calculando…",
                Modifier.weight(1f),
            )
        }
        LazyColumn(Modifier.fillMaxSize(), state = estadoLista, contentPadding = PaddingValues(bottom = 120.dp)) {
            item(key = "calendario") {
                Calendario(
                    mes, eventos, { mes = it },
                    onDia = { d -> indices[d]?.let { scope.launch { estadoLista.animateScrollToItem(it) } } },
                    modifier = Modifier.padding(horizontal = SearchHorizontalPadding),
                )
            }
            if (items?.isEmpty() == true) item(key = "vacio") {
                LText(
                    "Aquí salen los mangas de tu biblioteca en emisión con la fecha en que se espera su próximo capítulo, " +
                        "calculada con las fechas de sus últimos capítulos.",
                    LectorType.Body, Modifier.padding(SearchHorizontalPadding, 24.dp), color = LectorColors.Muted,
                )
            }
            porDia.forEach { (dia, lista) ->
                stickyHeader(key = "h_$dia") {
                    GeneratedGroupHeader(
                        formatoDia.format(dia).replaceFirstChar { it.titlecase() },
                        count = if (lista.size == 1) "1 manga" else "${lista.size} mangas",
                    )
                }
                items(lista, key = { "m_${dia}_${it.comic.id}" }) { p ->
                    HomeTrackRow(
                        title = p.comic.titulo,
                        subtitle = "Cada ${p.intervalo} ${if (p.intervalo == 1) "día" else "días"} · último ${com.example.lector.ui.etiquetaDia(p.ultima.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()).lowercase()}",
                        onClick = { onAbrir(p.comic) },
                        onLongClick = null,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = SearchHorizontalPadding, vertical = 3.dp),
                        cover = { ComicCover(p.comic, almacen, Modifier.size(LibraryRowCoverSize), SuaveShape(LibraryRowCoverCorner)) },
                    )
                }
            }
        }
    }
}

// ---- Calendario (components/calendar de Komikku) ----

private const val DIAS_SEMANA = 7
private const val MAX_EVENTOS = 3

@Composable
private fun Calendario(
    mes: YearMonth, eventos: Map<LocalDate, Int>, alCambiarMes: (YearMonth) -> Unit, onDia: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        CabeceraCalendario(mes, { alCambiarMes(mes.minusMonths(1)) }, { alCambiarMes(mes.plusMonths(1)) }, Modifier.fillMaxWidth().padding(vertical = 6.dp))
        val primerDia = WeekFields.of(Locale.getDefault()).firstDayOfWeek.value
        val diasSemana = remember { (0 until DIAS_SEMANA).map { DayOfWeek.of((primerDia - 1 + it) % DIAS_SEMANA + 1) } }
        val vacios = diasSemana.indexOf(mes.atDay(1).dayOfWeek)
        val celdas: List<LocalDate?> = List(vacios) { null } + (1..mes.lengthOfMonth()).map { mes.atDay(it) }
        Row(Modifier.fillMaxWidth()) {
            diasSemana.forEach {
                LText(
                    it.getDisplayName(TextStyle.NARROW, Locale.getDefault()), LectorType.Body.copy(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, textAlign = TextAlign.Center),
                    Modifier.weight(1f),
                )
            }
        }
        celdas.chunked(DIAS_SEMANA).forEach { semana ->
            Row(Modifier.fillMaxWidth()) {
                (0 until DIAS_SEMANA).forEach { i ->
                    Box(Modifier.weight(1f).padding(vertical = 2.dp), Alignment.Center) {
                        semana.getOrNull(i)?.let { d -> DiaCalendario(d, eventos[d] ?: 0) { onDia(d) } }
                    }
                }
            }
        }
    }
}

@Composable
private fun DiaCalendario(fecha: LocalDate, eventos: Int, onClick: () -> Unit) {
    val hoy = remember { LocalDate.now() }
    val acento = MaterialTheme.colorScheme.primary
    Box(
        Modifier
            .then(if (hoy == fecha) Modifier.border(BorderStroke(1.dp, LectorColors.Text), CircleShape) else Modifier)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .circulo(),
        contentAlignment = Alignment.Center,
    ) {
        LText(
            fecha.dayOfMonth.toString(),
            LectorType.Body.copy(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center),
            Modifier.padding(10.dp),
            color = if (fecha.isBefore(hoy)) LectorColors.Text.copy(alpha = 0.38f) else LectorColors.Text,
        )
        if (eventos > 0) {
            LText(eventos.toString(), LectorType.Label.copy(fontSize = 8.sp), Modifier.offset(x = 12.dp, y = (-10).dp), color = acento)
        }
        Row(Modifier.offset(y = 12.dp)) {
            repeat(eventos.coerceAtMost(MAX_EVENTOS)) { Indicador(it, 56.dp, acento) }
        }
    }
}

@Composable
private fun Indicador(indice: Int, tam: Dp, color: Color) {
    Box(Modifier.padding(horizontal = 1.dp).clip(CircleShape).background(color.copy(alpha = (indice + 1) * 0.3f)).size(tam / 12))
}

private fun Modifier.circulo() = layout { medible, restricciones ->
    val p = medible.measure(restricciones)
    val d = maxOf(p.height, p.width)
    layout(d, d) { p.placeRelative((d - p.width) / 2, (d - p.height) / 2) }
}

@Composable
private fun CabeceraCalendario(mes: YearMonth, onAnterior: () -> Unit, onSiguiente: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        AnimatedContent(
            targetState = mes,
            transitionSpec = {
                val adelante = targetState > initialState
                (slideInVertically(tween(200)) { h -> if (adelante) h else -h } + fadeIn(tween(200)))
                    .togetherWith(slideOutVertically(tween(200)) { h -> if (adelante) -h else h } + fadeOut(tween(200)))
                    .using(SizeTransform(clip = false))
            },
            label = "cambioMes",
        ) { m ->
            LText(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault()).format(m).replaceFirstChar { it.titlecase() }, LectorType.Hero)
        }
        Row {
            LibraryIconButton(Icons.Rounded.KeyboardArrowLeft, "Mes anterior", false, onAnterior)
            Spacer(Modifier.width(8.dp))
            LibraryIconButton(Icons.Rounded.KeyboardArrowRight, "Mes siguiente", false, onSiguiente)
        }
    }
}
