// Copiado de Komikku (https://github.com/komikku-app/komikku, Apache-2.0): app/src/main/java/mihon/feature/upcoming/
// (UpcomingScreenModel.kt, UpcomingScreenContent.kt, components/calendar/Calendar.kt, CalendarDay.kt, CalendarHeader.kt,
// CalendarIndicator.kt) y domain/.../FetchInterval.kt (calculateInterval), adaptado a Shiori. Aspecto del calendario al
// estilo de Mochy Music (github.com/realidkroo/Mochy-Music, GPL-3.0) y BitChord (GPL-3.0): cabecera grande, meses con
// paginador y días con la portada del manga previsto.
package com.example.lector.ui.actualizaciones

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import com.example.lector.ui.Tacto
import com.example.lector.ui.mochy.library.HomeTrackRow
import com.example.lector.ui.mochy.library.LibraryIconButton
import com.example.lector.ui.mochy.library.LibraryRowCoverCorner
import com.example.lector.ui.mochy.library.LibraryRowCoverSize
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

private const val PAGINAS_MES = 14 // el mes pasado, el actual y doce más
private const val PAGINA_HOY = 1
private const val DIAS_SEMANA = 7
private const val FILAS_MES = 6

/**
 * Pantalla «Próximos» (UpcomingScreen de Komikku): cabecera grande con el mes, calendario que se pasa deslizando, días
 * con la portada del manga previsto y, debajo, la lista del día elegido (o de todas las fechas).
 * El cálculo se hace fuera del hilo principal y se guarda en [EstadoActualizaciones] según la versión de la biblioteca;
 * el mes, el día y el desplazamiento también, para volver igual tras abrir un manga.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ProximosScreen(almacen: Almacen, onVolver: () -> Unit, onAbrir: (Comic) -> Unit, modifier: Modifier = Modifier) {
    BackHandler(onBack = onVolver)
    val est = EstadoActualizaciones
    val version = almacen.version
    LaunchedEffect(version) {
        if (est.proximos == null || est.versionProximos != version) {
            val r = withContext(Dispatchers.IO) { runCatching { calcularProximos(almacen) }.getOrDefault(emptyList()) }
            est.proximos = r
            est.versionProximos = version
        }
    }
    val items = est.proximos
    val porDia = remember(items) { items.orEmpty().groupBy { it.fecha } }
    val hoy = remember { LocalDate.now() }
    val base = remember { YearMonth.from(hoy).minusMonths(PAGINA_HOY.toLong()) }
    val pager = rememberPagerState(initialPage = est.paginaMes ?: PAGINA_HOY) { PAGINAS_MES }
    val estadoLista = rememberLazyListState(est.listaIndice, est.listaDesplazamiento)
    val scope = rememberCoroutineScope()
    LaunchedEffect(pager.settledPage) { est.paginaMes = pager.settledPage }
    DisposableEffect(Unit) {
        onDispose {
            est.listaIndice = estadoLista.firstVisibleItemIndex
            est.listaDesplazamiento = estadoLista.firstVisibleItemScrollOffset
        }
    }
    val seleccion = est.diaSeleccionado
    val formatoDia = remember { DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", Locale.getDefault()) }
    val primerDia = remember { WeekFields.of(Locale.getDefault()).firstDayOfWeek.value }
    val diasSemana = remember { (0 until DIAS_SEMANA).map { DayOfWeek.of((primerDia - 1 + it) % DIAS_SEMANA + 1) } }

    Column(modifier.fillMaxSize().background(LectorColors.Bg)) {
        Row(
            Modifier.fillMaxWidth().padding(start = SearchHorizontalPadding, end = SearchHorizontalPadding, top = 14.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LibraryIconButton(Icons.Rounded.ArrowBack, "Volver", false, onVolver)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                LText("Próximos", LectorType.Section.copy(fontWeight = FontWeight.Bold))
                LText(
                    items?.let { if (it.isEmpty()) "Sin fechas previstas" else "${it.size} mangas con fecha prevista" } ?: "Calculando…",
                    LectorType.Label, color = LectorColors.Muted,
                )
            }
            val enHoy = pager.currentPage == PAGINA_HOY && seleccion == null
            Pastilla("Hoy", activa = !enHoy) {
                est.diaSeleccionado = null
                scope.launch { pager.animateScrollToPage(PAGINA_HOY) }
            }
        }

        LazyColumn(Modifier.fillMaxSize(), state = estadoLista, contentPadding = PaddingValues(bottom = 120.dp)) {
            item(key = "mes", contentType = "mes") {
                CabeceraMes(
                    base.plusMonths(pager.currentPage.toLong()),
                    puedeAtras = pager.currentPage > 0, puedeAdelante = pager.currentPage < PAGINAS_MES - 1,
                    onAnterior = { scope.launch { pager.animateScrollToPage(pager.currentPage - 1) } },
                    onSiguiente = { scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } },
                )
            }
            item(key = "semana", contentType = "semana") {
                Row(Modifier.fillMaxWidth().padding(horizontal = SearchHorizontalPadding - 4.dp, vertical = 6.dp)) {
                    diasSemana.forEach {
                        LText(
                            it.getDisplayName(TextStyle.SHORT, Locale.getDefault()).trimEnd('.').uppercase(),
                            LectorType.Label.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.8.sp, textAlign = TextAlign.Center),
                            Modifier.weight(1f), color = LectorColors.Muted,
                        )
                    }
                }
            }
            item(key = "calendario", contentType = "calendario") {
                HorizontalPager(
                    pager, Modifier.fillMaxWidth(), beyondViewportPageCount = 1, key = { it },
                    contentPadding = PaddingValues(horizontal = SearchHorizontalPadding - 4.dp), pageSpacing = 8.dp,
                    verticalAlignment = Alignment.Top,
                ) { pagina ->
                    MesCalendario(base.plusMonths(pagina.toLong()), diasSemana, porDia, hoy, seleccion, almacen) { d ->
                        Tacto.alternar(true)
                        est.diaSeleccionado = if (seleccion == d) null else d
                    }
                }
            }
            item(key = "titulo_lista", contentType = "titulo") {
                Row(
                    Modifier.fillMaxWidth().padding(start = SearchHorizontalPadding, end = SearchHorizontalPadding, top = 22.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AnimatedContent(
                        seleccion, Modifier.weight(1f),
                        transitionSpec = { fadeIn(tween(180)).togetherWith(fadeOut(tween(120))) }, label = "tituloLista",
                    ) { d ->
                        Column {
                            LText(
                                d?.let { formatoDia.format(it).replaceFirstChar { c -> c.titlecase() } } ?: "Todas las fechas",
                                LectorType.Section.copy(fontWeight = FontWeight.Bold, fontSize = 20.sp),
                            )
                            val n = if (d != null) porDia[d]?.size ?: 0 else items?.size ?: 0
                            LText(
                                (d?.let { relativo(hoy, it) + " · " } ?: "") + if (n == 1) "1 manga" else "$n mangas",
                                LectorType.Label, color = LectorColors.Muted,
                            )
                        }
                    }
                    if (seleccion != null) Pastilla("Ver todos", activa = true) { est.diaSeleccionado = null }
                }
            }
            if (items?.isEmpty() == true) item(key = "vacio") {
                LText(
                    "Aquí salen los mangas de tu biblioteca en emisión con la fecha en que se espera su próximo capítulo, " +
                        "calculada con las fechas de sus últimos capítulos.",
                    LectorType.Body, Modifier.padding(SearchHorizontalPadding, 12.dp), color = LectorColors.Muted,
                )
            }
            if (seleccion != null) {
                val delDia = porDia[seleccion].orEmpty()
                if (delDia.isEmpty() && items != null) item(key = "dia_vacio") {
                    LText(
                        "No se espera nada este día.", LectorType.Body,
                        Modifier.animateItem().padding(horizontal = SearchHorizontalPadding, vertical = 12.dp), color = LectorColors.Muted,
                    )
                }
                items(delDia, key = { "m_${it.fecha}_${it.comic.id}" }, contentType = { "manga" }) { p ->
                    FilaProximo(p, hoy, almacen, Modifier.animateItem()) { onAbrir(p.comic) }
                }
            } else {
                porDia.forEach { (dia, lista) ->
                    item(key = "h_$dia", contentType = "cabecera") {
                        Row(
                            Modifier.animateItem().fillMaxWidth().clickable {
                                est.diaSeleccionado = dia
                                val pag = ChronoUnit.MONTHS.between(base, YearMonth.from(dia)).toInt()
                                if (pag in 0 until PAGINAS_MES) scope.launch { pager.animateScrollToPage(pag) }
                            }.padding(horizontal = SearchHorizontalPadding, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            LText(
                                formatoDia.format(dia).replaceFirstChar { it.titlecase() },
                                LectorType.Label.copy(fontWeight = FontWeight.SemiBold, fontSize = 13.sp), Modifier.weight(1f),
                            )
                            LText(relativo(hoy, dia), LectorType.Label, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    items(lista, key = { "m_${dia}_${it.comic.id}" }, contentType = { "manga" }) { p ->
                        FilaProximo(p, hoy, almacen, Modifier.animateItem()) { onAbrir(p.comic) }
                    }
                }
            }
        }
    }
}

private fun relativo(hoy: LocalDate, d: LocalDate): String = when (val n = ChronoUnit.DAYS.between(hoy, d)) {
    0L -> "Hoy"
    1L -> "Mañana"
    -1L -> "Ayer"
    else -> if (n > 0) "En $n días" else "Hace ${-n} días"
}

@Composable
private fun Pastilla(texto: String, activa: Boolean, onClick: () -> Unit) {
    val fondo by animateColorAsState(if (activa) LectorColors.CardHigh else LectorColors.Card, label = "pastilla")
    val color by animateColorAsState(if (activa) LectorColors.Text else LectorColors.Muted, label = "pastillaTexto")
    Box(
        Modifier.clip(CircleShape).background(fondo).clickable(enabled = activa, onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp),
    ) { LText(texto, LectorType.Label.copy(fontWeight = FontWeight.SemiBold), color = color) }
}

@Composable
private fun FilaProximo(p: MangaProximo, hoy: LocalDate, almacen: Almacen, modifier: Modifier, onClick: () -> Unit) {
    val zona = remember { ZoneId.systemDefault() }
    HomeTrackRow(
        title = p.comic.titulo,
        subtitle = "Cada ${p.intervalo} ${if (p.intervalo == 1) "día" else "días"} · último " +
            com.example.lector.ui.etiquetaDia(p.ultima.atStartOfDay(zona).toInstant().toEpochMilli()).lowercase(),
        onClick = onClick,
        onLongClick = null,
        modifier = modifier.fillMaxWidth().padding(horizontal = SearchHorizontalPadding, vertical = 3.dp),
        cover = { ComicCover(p.comic, almacen, Modifier.size(LibraryRowCoverSize), SuaveShape(LibraryRowCoverCorner)) },
        trailing = {
            val hoyEs = p.fecha == hoy
            Box(
                Modifier.clip(CircleShape)
                    .background(if (hoyEs) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else LectorColors.CardHigh)
                    .padding(horizontal = 10.dp, vertical = 5.dp),
            ) {
                LText(
                    relativo(hoy, p.fecha), LectorType.Label.copy(fontWeight = FontWeight.SemiBold),
                    color = if (hoyEs) MaterialTheme.colorScheme.primary else LectorColors.Muted,
                )
            }
        },
    )
}

// ---- Calendario ----

@Composable
private fun CabeceraMes(
    mes: YearMonth, puedeAtras: Boolean, puedeAdelante: Boolean, onAnterior: () -> Unit, onSiguiente: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(start = SearchHorizontalPadding, end = SearchHorizontalPadding, top = 18.dp, bottom = 8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        AnimatedContent(
            targetState = mes, modifier = Modifier.weight(1f),
            transitionSpec = {
                val adelante = targetState > initialState
                (slideInHorizontally(spring(stiffness = Spring.StiffnessMediumLow)) { w -> if (adelante) w / 3 else -w / 3 } + fadeIn(tween(220)))
                    .togetherWith(slideOutHorizontally(tween(180)) { w -> if (adelante) -w / 3 else w / 3 } + fadeOut(tween(150)))
                    .using(SizeTransform(clip = false))
            },
            label = "cambioMes",
        ) { m ->
            Column {
                LText(
                    m.year.toString(), LectorType.Label.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 1.5.sp),
                    color = MaterialTheme.colorScheme.primary,
                )
                LText(
                    m.month.getDisplayName(TextStyle.FULL_STANDALONE, Locale.getDefault()).replaceFirstChar { it.titlecase() },
                    LectorType.Hero.copy(fontSize = 40.sp, lineHeight = 44.sp, fontWeight = FontWeight.Black, letterSpacing = (-1).sp),
                    maxLines = 1,
                )
            }
        }
        Row(Modifier.padding(bottom = 4.dp)) {
            Box(Modifier.graphicsLayer { alpha = if (puedeAtras) 1f else 0.35f }) {
                LibraryIconButton(Icons.Rounded.KeyboardArrowLeft, "Mes anterior", false) { if (puedeAtras) onAnterior() }
            }
            Spacer(Modifier.width(8.dp))
            Box(Modifier.graphicsLayer { alpha = if (puedeAdelante) 1f else 0.35f }) {
                LibraryIconButton(Icons.Rounded.KeyboardArrowRight, "Mes siguiente", false) { if (puedeAdelante) onSiguiente() }
            }
        }
    }
}

/** Un mes: siempre seis filas, para que todas las páginas midan lo mismo y el paginador no salte. */
@Composable
private fun MesCalendario(
    mes: YearMonth, diasSemana: List<DayOfWeek>, porDia: Map<LocalDate, List<MangaProximo>>, hoy: LocalDate,
    seleccion: LocalDate?, almacen: Almacen, onDia: (LocalDate) -> Unit,
) {
    val celdas = remember(mes, diasSemana) {
        val vacios = diasSemana.indexOf(mes.atDay(1).dayOfWeek)
        val dias: List<LocalDate?> = List(vacios) { null } + (1..mes.lengthOfMonth()).map { mes.atDay(it) }
        dias + List(FILAS_MES * DIAS_SEMANA - dias.size) { null }
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        celdas.chunked(DIAS_SEMANA).forEach { semana ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                semana.forEach { d ->
                    Box(Modifier.weight(1f).aspectRatio(0.74f)) {
                        if (d != null) DiaCalendario(d, porDia[d].orEmpty(), hoy, d == seleccion, almacen) { onDia(d) }
                    }
                }
            }
        }
    }
}

@Composable
private fun DiaCalendario(
    fecha: LocalDate, mangas: List<MangaProximo>, hoy: LocalDate, elegido: Boolean, almacen: Almacen, onClick: () -> Unit,
) {
    val acento = MaterialTheme.colorScheme.primary
    val escala by animateFloatAsState(
        if (elegido) 1.08f else 1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "escalaDia",
    )
    val borde by animateColorAsState(
        when {
            elegido -> acento
            fecha == hoy -> acento.copy(alpha = 0.55f)
            else -> Color.Transparent
        },
        label = "bordeDia",
    )
    val forma = RoundedCornerShape(14.dp)
    val pasado = fecha.isBefore(hoy)
    val conPortada = mangas.isNotEmpty()
    Box(
        Modifier.fillMaxSize()
            .graphicsLayer { scaleX = escala; scaleY = escala }
            .clip(forma)
            .background(
                when {
                    conPortada -> LectorColors.CardHigh
                    elegido -> acento.copy(alpha = 0.16f)
                    pasado -> Color.Transparent
                    else -> LectorColors.Card
                },
            )
            .border(if (elegido) 2.dp else 1.5.dp, borde, forma)
            .clickable(onClick = onClick),
    ) {
        if (conPortada) {
            ComicCover(mangas.first().comic, almacen, Modifier.fillMaxSize(), RoundedCornerShape(0.dp))
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.55f), 0.45f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.35f)),
                ),
            )
        }
        LText(
            fecha.dayOfMonth.toString(),
            LectorType.Body.copy(fontSize = 14.sp, fontWeight = if (fecha == hoy || elegido) FontWeight.Black else FontWeight.SemiBold),
            Modifier.align(if (conPortada) Alignment.TopStart else Alignment.Center).padding(if (conPortada) 6.dp else 0.dp),
            color = when {
                conPortada -> Color.White
                fecha == hoy || elegido -> acento
                pasado -> LectorColors.Text.copy(alpha = 0.3f)
                else -> LectorColors.Text
            },
        )
        if (mangas.size > 1) {
            Box(
                Modifier.align(Alignment.BottomEnd).padding(4.dp).clip(CircleShape).background(acento)
                    .padding(horizontal = 5.dp, vertical = 1.dp),
            ) {
                LText("+${mangas.size - 1}", LectorType.Label.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold), color = com.example.lector.textoSobre(acento))
            }
        }
        if (fecha == hoy && !conPortada) {
            Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp).size(4.dp).clip(CircleShape).background(acento))
        }
    }
}
