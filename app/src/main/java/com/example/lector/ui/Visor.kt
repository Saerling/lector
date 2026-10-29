package com.example.lector.ui

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.provider.MediaStore
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.lector.Almacen
import com.example.lector.EscalaPagina
import com.example.lector.InvertirZonas
import com.example.lector.ModoVisor
import com.example.lector.Orientacion
import com.example.lector.Teclas
import com.example.lector.Tinte
import com.example.lector.ZonasToque
import com.example.lector.colorFondo
import com.example.lector.colorTinte
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt

/** Un capítulo que el visor sabe abrir. */
class CapVisor(val clave: String, val nombre: String, val origen: OrigenPaginas)

/** Lo que el visor puede hacer además de leer (depende de si es un cómic local o un manga de una fuente). */
class AccionesVisor(
    /** Dirección web del capítulo (null = no tiene). */
    val urlWeb: ((Int) -> String?)? = null,
    val estaMarcado: ((Int) -> Boolean)? = null,
    val alternarMarca: ((Int) -> Unit)? = null,
    /** Usar una página como portada de la serie (solo si está en la biblioteca). */
    val usarComoPortada: (suspend (ByteArray) -> Unit)? = null,
    val esLeido: ((Int) -> Boolean)? = null,
    /** Cabeceras de la fuente para abrir el capítulo en el WebView (Cloudflare, captchas). */
    val cabecerasWeb: Map<String, String> = emptyMap(),
)

private data class RefPagina(val indice: Int, val mitad: Int)

private sealed interface ItemVisor {
    // [c]: índice del capítulo al que pertenece (en la tira continua conviven varios; -1 = el actual).
    data class Pagina(val ref: RefPagina, val c: Int = -1) : ItemVisor
    data class Transicion(val siguiente: Boolean, val c: Int = -1) : ItemVisor
}

private enum class AccionToque { SIGUIENTE, ANTERIOR, MENU }

private val ModoVisor.paginado: Boolean
    get() = this == ModoVisor.DERECHA_IZQUIERDA || this == ModoVisor.IZQUIERDA_DERECHA || this == ModoVisor.VERTICAL

fun nombreModoVisor(m: ModoVisor) = when (m) {
    ModoVisor.DERECHA_IZQUIERDA -> "Paginado, de derecha a izquierda (manga)"
    ModoVisor.IZQUIERDA_DERECHA -> "Paginado, de izquierda a derecha (cómic)"
    ModoVisor.VERTICAL -> "Paginado vertical"
    ModoVisor.WEBTOON -> "Webtoon (tira continua)"
    ModoVisor.CONTINUO -> "Vertical continuo (con separación)"
}

/**
 * Zonas de toque de Mihon/Komikku. Las coordenadas son relativas (0..1). En los modos de derecha a izquierda la
 * pantalla se invierte en horizontal, igual que en Komikku.
 */
private fun zonaDe(fx0: Float, fy0: Float, zonas: ZonasToque, invertir: InvertirZonas, modo: ModoVisor): AccionToque {
    if (zonas == ZonasToque.DESACTIVADO) return AccionToque.MENU
    val rtl = modo == ModoVisor.DERECHA_IZQUIERDA
    val invH = (invertir == InvertirZonas.HORIZONTAL || invertir == InvertirZonas.AMBOS) xor rtl
    val invV = invertir == InvertirZonas.VERTICAL || invertir == InvertirZonas.AMBOS
    val x = if (invH) 1f - fx0 else fx0
    val y = if (invV) 1f - fy0 else fy0
    fun en(l: Float, t: Float, r: Float, b: Float) = x in l..r && y in t..b
    val t = 1f / 3f
    return when (zonas) {
        ZonasToque.AUTOMATICO, ZonasToque.L -> when {
            en(0f, t, t, 2 * t) || en(0f, 0f, 1f, t) -> AccionToque.ANTERIOR
            en(2 * t, t, 1f, 2 * t) || en(0f, 2 * t, 1f, 1f) -> AccionToque.SIGUIENTE
            else -> AccionToque.MENU
        }
        ZonasToque.KINDLE -> when {
            en(t, t, 1f, 1f) -> AccionToque.SIGUIENTE
            en(0f, t, t, 1f) -> AccionToque.ANTERIOR
            else -> AccionToque.MENU
        }
        ZonasToque.BORDES -> when {
            en(0f, 0f, t, 1f) || en(2 * t, 0f, 1f, 1f) -> AccionToque.SIGUIENTE
            en(t, 2 * t, 2 * t, 1f) -> AccionToque.ANTERIOR
            else -> AccionToque.MENU
        }
        ZonasToque.IZQUIERDA_DERECHA -> when {
            en(0f, 0f, t, 1f) -> AccionToque.ANTERIOR
            en(2 * t, 0f, 1f, 1f) -> AccionToque.SIGUIENTE
            else -> AccionToque.MENU
        }
        ZonasToque.DESACTIVADO -> AccionToque.MENU
    }
}

/**
 * Visor de Lector (como el de Komikku). El MISMO para cómics locales, capítulos descargados y lectura online.
 *  - Modos: paginado ← / →, paginado vertical, webtoon y vertical continuo.
 *  - Zonas de toque (L, Kindle, bordes, izquierda/derecha) con inversión, teclas de volumen, autodesplazamiento.
 *  - Zoom con pellizco y doble toque (también en webtoon), recorte de bordes, división de páginas dobles,
 *    relleno lateral, transiciones entre capítulos, precarga, filtros de color y brillo.
 *  - Mantener pulsada una página: compartir, guardar o usarla como portada.
 *
 * [capitulos] van en orden de lectura (el 0 es el primero). [claveSerie] guarda el modo propio de la serie.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun Visor(
    almacen: Almacen,
    titulo: String,
    capitulos: List<CapVisor>,
    inicial: Int,
    paginaInicial: Int,
    claveSerie: String?,
    // Modo propuesto si la serie no tiene uno propio (p. ej. webtoon detectado por la forma de las páginas).
    modoSugerido: ModoVisor? = null,
    alPagina: (cap: Int, pagina: Int, total: Int) -> Unit,
    alTerminarCapitulo: (cap: Int) -> Unit,
    acciones: AccionesVisor = AccionesVisor(),
    onBack: () -> Unit,
) {
    if (capitulos.isEmpty()) {
        Box(Modifier.fillMaxSize().background(Color.Black), Alignment.Center) {
            LText("No se puede abrir el capítulo.", LectorType.Body, color = Color(0xFF9A9AA0))
        }
        return
    }
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val densidad = LocalDensity.current
    val conf = LocalConfiguration.current

    var ci by rememberSaveable { mutableIntStateOf(inicial.coerceIn(0, capitulos.lastIndex)) }
    if (ci > capitulos.lastIndex) ci = capitulos.lastIndex
    val cap = capitulos[ci]
    var modo by remember { mutableStateOf(claveSerie?.let { almacen.modoVisorDe(it) } ?: modoSugerido ?: almacen.modoVisor) }
    val paginado = modo.paginado

    var total by remember(cap.clave) { mutableStateOf<Int?>(null) }
    var error by remember(cap.clave) { mutableStateOf<String?>(null) }
    var recarga by remember(cap.clave) { mutableIntStateOf(0) }
    // Al volver al capítulo anterior se empieza por su última página.
    var irAlFinal by remember { mutableStateOf(false) }
    var primeraCarga by remember { mutableStateOf(true) }

    var barras by remember { mutableStateOf(false) }
    var hoja by remember { mutableStateOf(false) }
    var dialogoCaps by remember { mutableStateOf(false) }
    var auto by remember { mutableStateOf(false) }
    var webCap by remember { mutableStateOf<String?>(null) }
    var menuPagina by remember { mutableStateOf<Int?>(null) }
    var tocando by remember { mutableStateOf(false) }

    val refs = remember(cap.clave) { mutableStateListOf<RefPagina>() }
    // Tira continua (como Komikku): páginas de cada capítulo ya conocido, para encadenar anterior, actual y siguiente.
    val totales = remember { mutableStateMapOf<String, Int>() }
    // El capítulo cambió porque se llegó a él desplazando la tira: no hay que recolocar nada.
    var porScroll by remember { mutableStateOf(false) }
    LaunchedEffect(cap.clave, recarga) {
        total = null; error = null; refs.clear()
        runCatching { cap.origen.total() }
            .onSuccess { n ->
                if (n <= 0) error = "El capítulo no tiene páginas"
                else { refs.addAll((0 until n).map { RefPagina(it, 0) }); total = n; totales[cap.clave] = n }
            }
            .onFailure { if (it is CancellationException) throw it; error = it.message ?: "No se pudieron cargar las páginas" }
    }

    val hayAnterior = ci > 0
    val haySiguiente = ci < capitulos.lastIndex
    LaunchedEffect(ci, paginado) {
        if (paginado) return@LaunchedEffect
        for (c in listOf(ci + 1, ci - 1)) {
            val cv = capitulos.getOrNull(c) ?: continue
            if (totales[cv.clave] == null) runCatching { cv.origen.total() }.getOrNull()?.takeIf { it > 0 }?.let { totales[cv.clave] = it }
        }
    }
    val items by remember(hayAnterior, refs, paginado, ci) {
        derivedStateOf {
            if (!paginado) buildList<ItemVisor> {
                // Anterior, actual y siguiente en la misma lista; al pasar de uno a otro las claves no cambian y la
                // tira no salta (LazyColumn conserva la posición por clave).
                val rango = (ci - 1..ci + 1).filter { it in capitulos.indices && totales[capitulos[it].clave] != null }
                if (rango.isEmpty()) return@buildList
                add(ItemVisor.Transicion(false, rango.first()))
                rango.forEach { c ->
                    repeat(totales[capitulos[c].clave]!!) { add(ItemVisor.Pagina(RefPagina(it, 0), c)) }
                    add(ItemVisor.Transicion(true, c))
                }
            } else buildList<ItemVisor> {
                if (hayAnterior) add(ItemVisor.Transicion(false))
                refs.forEach { add(ItemVisor.Pagina(it)) }
                add(ItemVisor.Transicion(true))
            }
        }
    }
    val desfase = if (hayAnterior) 1 else 0

    val pager = rememberPagerState { items.size }
    val lista = rememberLazyListState()

    // Tamaño al que se decodifican las páginas: ancho de pantalla (con margen para el zoom), con tope.
    val anchoPx = with(densidad) { conf.screenWidthDp.dp.roundToPx() }
    val altoPx = with(densidad) { conf.screenHeightDp.dp.roundToPx() }
    val anchoDecodificar = remember(anchoPx, altoPx, paginado) {
        (if (paginado) (maxOf(anchoPx, altoPx) * 1.3f) else (anchoPx * 1.5f)).roundToInt().coerceIn(720, 2560)
    }
    val recortar = if (paginado) almacen.recortarBordes else almacen.recortarWebtoon
    val dividir = paginado && almacen.dividirDobles

    // Al cargar un capítulo: salta a la página que toca (la guardada, la última si se vuelve atrás, o la primera).
    LaunchedEffect(cap.clave, total) {
        val n = total ?: return@LaunchedEffect
        val pagina = when {
            irAlFinal -> n - 1
            primeraCarga -> paginaInicial.coerceIn(0, n - 1)
            else -> 0
        }
        irAlFinal = false
        primeraCarga = false
        if (porScroll) { porScroll = false; return@LaunchedEffect }
        if (modo.paginado) {
            val idx = desfase + refs.indexOfFirst { it.indice == pagina }.coerceAtLeast(0)
            runCatching { pager.scrollToPage(idx) }
        } else {
            val idx = items.indexOfFirst { it is ItemVisor.Pagina && it.c == ci && it.ref.indice == pagina }
            if (idx >= 0) runCatching { lista.scrollToItem(idx) }
        }
    }

    // Elemento actual (página o transición).
    var itemActual by remember { mutableIntStateOf(0) }
    LaunchedEffect(modo, cap.clave) {
        if (modo.paginado) {
            snapshotFlow { pager.settledPage }.collect { itemActual = it }
        } else {
            // En la tira, la página "actual" es la que ocupa el centro de la pantalla.
            snapshotFlow {
                val info = lista.layoutInfo
                val centro = (info.viewportStartOffset + info.viewportEndOffset) / 2
                info.visibleItemsInfo.firstOrNull { it.offset <= centro && it.offset + it.size > centro }?.index
                    ?: lista.firstVisibleItemIndex
            }.distinctUntilChanged().collect { i ->
                itemActual = i
                // Al entrar en las páginas de otro capítulo de la tira, ese pasa a ser el actual (sin recolocar).
                val c = when (val it = items.getOrNull(i)) { is ItemVisor.Pagina -> it.c; else -> -1 }
                if (c >= 0 && c != ci) { porScroll = true; ci = c }
            }
        }
    }
    val actual = items.getOrNull(itemActual)
    val paginaReal = when (actual) {
        is ItemVisor.Pagina -> actual.ref.indice
        is ItemVisor.Transicion -> if (actual.siguiente) (total ?: 1) - 1 else 0
        null -> 0
    }

    fun irCapitulo(nuevo: Int, alFinal: Boolean) {
        if (nuevo !in capitulos.indices || nuevo == ci) return
        irAlFinal = alFinal
        ci = nuevo
    }

    // Progreso: se anota la página (con una pequeña espera) y el capítulo se da por leído en la última.
    val terminados = remember { mutableSetOf<Int>() }
    LaunchedEffect(cap.clave, paginaReal, total) {
        val n = total ?: return@LaunchedEffect
        delay(450)
        alPagina(ci, paginaReal.coerceIn(0, n - 1), n)
        if (paginaReal >= n - 1 && terminados.add(ci)) alTerminarCapitulo(ci)
    }

    // Lectura continua (como Komikku): al llegar a la página de transición se pasa solo al capítulo siguiente, y al
    // volver a la del principio, al anterior (por su última página). Se ve un instante el «Siguiente: …».
    LaunchedEffect(actual, total) {
        val a = actual
        if (total == null || a !is ItemVisor.Transicion || !paginado) return@LaunchedEffect
        delay(120) // sin parada: se ve solo un destello del «Siguiente: …»
        if (a.siguiente && haySiguiente) irCapitulo(ci + 1, false)
        else if (!a.siguiente && hayAnterior) irCapitulo(ci - 1, true)
    }
    // Webtoon: al entrar en la transición final, sigue con el siguiente capítulo (como la tira infinita de Komikku).
    // (La tira ya no salta de capítulo: los encadena en la misma lista.)

    // El capítulo COMPLETO se descarga al abrirlo (a disco, sin decodificar: casi no gasta memoria), 3 a la vez.
    LaunchedEffect(cap.clave, total) {
        val n = total ?: return@LaunchedEffect
        val origen = cap.origen as? OrigenFuente ?: return@LaunchedEffect
        if (!origen.enRed) return@LaunchedEffect
        val cupo = kotlinx.coroutines.sync.Semaphore(3)
        kotlinx.coroutines.coroutineScope {
            for (p in 0 until n) launch(Dispatchers.IO) { cupo.acquire(); try { runCatching { origen.bytes(p) } } finally { cupo.release() } }
        }
    }

    // Precarga de las páginas siguientes (y del total del capítulo siguiente al acercarse al final).
    LaunchedEffect(cap.clave, paginaReal, total, anchoDecodificar, recortar) {
        val n = total ?: return@LaunchedEffect
        val cuantas = almacen.precargaPaginas.coerceIn(1, 10)
        for (k in 1..cuantas) {
            val p = paginaReal + k
            if (p >= n) break
            try {
                CacheVisor.obtener(cap.origen, p, 0, anchoDecodificar, recortar)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            }
        }
        if (paginaReal >= n - 3 && haySiguiente) runCatching { capitulos[ci + 1].origen.total() }
    }

    // ---- Navegación ----
    fun siguiente() {
        scope.launch {
            if (paginado) {
                val a = items.getOrNull(pager.currentPage)
                if (a is ItemVisor.Transicion && a.siguiente) { if (haySiguiente) irCapitulo(ci + 1, false); return@launch }
                val destino = (pager.currentPage + 1).coerceAtMost(items.lastIndex)
                if (almacen.animarPaginas) pager.animateScrollToPage(destino) else pager.scrollToPage(destino)
            } else {
                val alto = (lista.layoutInfo.viewportEndOffset - lista.layoutInfo.viewportStartOffset).toFloat()
                if (almacen.animarPaginas) lista.animateScrollBy(alto * 0.75f) else lista.scrollBy(alto * 0.75f)
            }
        }
    }

    fun anterior() {
        scope.launch {
            if (paginado) {
                val a = items.getOrNull(pager.currentPage)
                if (a is ItemVisor.Transicion && !a.siguiente) { irCapitulo(ci - 1, true); return@launch }
                val destino = (pager.currentPage - 1).coerceAtLeast(0)
                if (almacen.animarPaginas) pager.animateScrollToPage(destino) else pager.scrollToPage(destino)
            } else {
                val alto = (lista.layoutInfo.viewportEndOffset - lista.layoutInfo.viewportStartOffset).toFloat()
                if (almacen.animarPaginas) lista.animateScrollBy(-alto * 0.75f) else lista.scrollBy(-alto * 0.75f)
            }
        }
    }

    fun toque(pos: Offset, tam: IntSize) {
        if (barras) { barras = false; return }
        if (tam.width <= 0 || tam.height <= 0) { barras = true; return }
        when (zonaDe(pos.x / tam.width, pos.y / tam.height, almacen.zonasVisor, almacen.invertirZonas, modo)) {
            AccionToque.SIGUIENTE -> siguiente()
            AccionToque.ANTERIOR -> anterior()
            AccionToque.MENU -> barras = true
        }
    }

    fun irAPagina(p: Int) {
        // En la tira hay varios capítulos en la lista: se busca la página DEL CAPÍTULO ACTUAL (antes caía en el anterior).
        val idx = if (paginado) desfase + refs.indexOfFirst { it.indice == p }.coerceAtLeast(0)
            else items.indexOfFirst { it is ItemVisor.Pagina && it.c == ci && it.ref.indice == p }
        if (idx < 0) return
        scope.launch { if (paginado) pager.scrollToPage(idx) else lista.scrollToItem(idx) }
    }

    // Páginas dobles: al descubrir que una página es apaisada se parte en dos (el orden depende del sentido).
    fun dividirPagina(indice: Int) {
        val pos = refs.indexOfFirst { it.indice == indice && it.mitad == 0 }
        if (pos < 0) return
        val rtl = modo == ModoVisor.DERECHA_IZQUIERDA
        val primeraDerecha = rtl xor almacen.invertirDivision
        val orden = if (primeraDerecha) listOf(RefPagina(indice, 2), RefPagina(indice, 1)) else listOf(RefPagina(indice, 1), RefPagina(indice, 2))
        val antes = pager.currentPage
        refs.removeAt(pos)
        refs.addAll(pos, orden)
        // Si la página partida estaba antes de la actual, todo se corre una posición: se compensa.
        if (desfase + pos < antes) scope.launch { pager.scrollToPage(antes + 1) }
    }

    // ---- Teclas de volumen y autodesplazamiento ----
    TeclasVolumen(activas = almacen.teclasVolumen)
    LaunchedEffect(modo, total) {
        Teclas.eventos.collect { d ->
            val delta = if (almacen.invertirVolumen) -d else d
            if (delta > 0) siguiente() else anterior()
        }
    }
    LaunchedEffect(auto, barras, modo, total, tocando) {
        if (!auto || barras || total == null || tocando) return@LaunchedEffect
        if (paginado) {
            while (true) {
                delay(((11f - almacen.velocidadAuto.coerceIn(1f, 10f)) * 1000f).toLong())
                val a = items.getOrNull(pager.currentPage)
                if (a is ItemVisor.Transicion && a.siguiente) { auto = false; break }
                siguiente()
            }
        } else {
            val pxSeg = with(densidad) { (20f + almacen.velocidadAuto.coerceIn(1f, 10f) * 22f).dp.toPx() }
            var antes = 0L
            while (true) {
                val ahora = withFrameNanos { it }
                if (antes != 0L) {
                    val dt = (ahora - antes) / 1_000_000_000f
                    val movido = lista.scrollBy(pxSeg * dt.coerceAtMost(0.05f))
                    if (movido == 0f && !lista.canScrollForward) { auto = false; break }
                }
                antes = ahora
            }
        }
    }

    // ---- Pantalla ----
    PantallaCompleta(oculta = almacen.pantallaCompleta && !barras)
    PantallaEncendida(activa = almacen.mantenerEncendida)
    AjustesPantalla(almacen)
    val vista = LocalView.current
    // Tiempo de lectura (estadísticas).
    DisposableEffect(Unit) {
        val inicio = SystemClock.elapsedRealtime()
        onDispose { almacen.sumarTiempoLectura(SystemClock.elapsedRealtime() - inicio) }
    }
    BackHandler(enabled = barras || hoja) { if (hoja) hoja = false else barras = false }

    val fondo = colorFondo(almacen.fondoLector)
    val filtro = remember(almacen.escalaGrises, almacen.invertirColores) {
        construirFiltro(almacen.escalaGrises, almacen.invertirColores)
    }

    Box(
        Modifier.fillMaxSize().background(fondo)
            // Saber si hay un dedo en la pantalla (pausa el autodesplazamiento), sin consumir nada.
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val ev = awaitPointerEvent(PointerEventPass.Initial)
                        val abajo = ev.changes.any { it.pressed }
                        if (abajo != tocando) tocando = abajo
                    }
                }
            }
    ) {
        val n = total
        when {
            n == null && (paginado || items.isEmpty() || error != null) -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                if (error == null) CircularProgressIndicator(Modifier.size(30.dp), color = Color.White)
                else Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                    Text(error!!, color = Color(0xFFE57373), textAlign = TextAlign.Center, style = LectorType.Body)
                    Spacer(Modifier.height(14.dp))
                    BotonOscuro("Reintentar", Icons.Rounded.Refresh) { recarga++ }
                    Spacer(Modifier.height(8.dp))
                    BotonOscuro("Volver", Icons.Rounded.ArrowBack) { onBack() }
                }
            }
            paginado -> {
                val contenido: @Composable (Int) -> Unit = { i ->
                    when (val it = items.getOrNull(i)) {
                        is ItemVisor.Pagina -> PaginaPaginada(
                            origen = cap.origen, ref = it.ref, anchoMax = anchoDecodificar, recortar = recortar,
                            escala = almacen.escalaPagina, filtro = filtro, dobleToque = almacen.dobleToqueZoom,
                            detectarAncha = dividir, onAncha = { dividirPagina(it.ref.indice) },
                            onToque = ::toque, onLargo = { menuPagina = it.ref.indice },
                        )
                        is ItemVisor.Transicion -> Transicion(
                            siguiente = it.siguiente,
                            actual = cap.nombre,
                            otro = if (it.siguiente) capitulos.getOrNull(ci + 1)?.nombre else capitulos.getOrNull(ci - 1)?.nombre,
                            modo = modo,
                            onIr = { if (it.siguiente) irCapitulo(ci + 1, false) else irCapitulo(ci - 1, true) },
                            onToque = ::toque,
                        )
                        null -> Box(Modifier.fillMaxSize())
                    }
                }
                if (modo == ModoVisor.VERTICAL) {
                    VerticalPager(
                        state = pager, modifier = Modifier.fillMaxSize(), beyondViewportPageCount = 1,
                        key = { i -> claveItem(items.getOrNull(i), i) },
                    ) { i -> contenido(i) }
                } else {
                    HorizontalPager(
                        state = pager, modifier = Modifier.fillMaxSize(), beyondViewportPageCount = 1,
                        reverseLayout = modo == ModoVisor.DERECHA_IZQUIERDA,
                        key = { i -> claveItem(items.getOrNull(i), i) },
                    ) { i -> contenido(i) }
                }
            }
            else -> Tira(
                items = items, lista = lista, cap = cap, capitulos = capitulos, ci = ci,
                anchoMax = anchoDecodificar, recortar = recortar, filtro = filtro, fondo = fondo,
                relleno = (conf.screenWidthDp * almacen.rellenoLateral.coerceIn(0, 25) / 100f).dp,
                separacion = if (modo == ModoVisor.CONTINUO) 14.dp else 0.dp,
                dobleToque = almacen.dobleToqueZoom,
                onToque = ::toque,
                onLargo = { menuPagina = it },
                onIr = { sig -> if (sig) irCapitulo(ci + 1, false) else irCapitulo(ci - 1, true) },
            )
        }

        // Capa de color (luz cálida, sepia…) sobre las páginas; no intercepta toques.
        if (almacen.tinte != Tinte.NINGUNO) {
            Box(Modifier.fillMaxSize().background(colorTinte(almacen.tinte).copy(alpha = almacen.intensidadTinte)))
        }

        // Número de página cuando el menú está oculto.
        if (!barras && n != null && almacen.mostrarNumeroPagina) {
            Text(
                "${paginaReal + 1} / $n",
                style = LectorType.Label, color = Color.White,
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 6.dp)
                    .clip(CircleShape).background(Color(0x99000000)).padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }
        if (auto && !barras) {
            Text(
                "Autodesplazamiento",
                style = LectorType.Label, color = Color.White,
                modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 6.dp)
                    .clip(CircleShape).background(Color(0x99000000)).padding(horizontal = 12.dp, vertical = 4.dp)
                    .clickable { auto = false },
            )
        }

        // ── Barra superior ──
        AnimatedVisibility(
            visible = barras, modifier = Modifier.align(Alignment.TopCenter),
            enter = fadeIn(tween(200)) + slideInVertically(tween(280)) { -it },
            exit = fadeOut(tween(150)) + slideOutVertically(tween(220)) { -it },
        ) {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp)
                    .clip(SuaveShape(28.dp)).background(ColorBarra)
                    .border(1.dp, BordeBarra, SuaveShape(28.dp))
                    .padding(horizontal = 6.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BotonRedondo(Icons.Rounded.ArrowBack, "Atrás") { onBack() }
                Spacer(Modifier.width(6.dp))
                Column(Modifier.weight(1f)) {
                    Text(titulo, style = LectorType.Section.copy(fontSize = 18.sp), color = TintaBarra, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(cap.nombre, style = LectorType.Label, color = SubBarra, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                acciones.alternarMarca?.let { alternar ->
                    val marcado = acciones.estaMarcado?.invoke(ci) == true
                    BotonRedondo(if (marcado) Iconos.Marca else Iconos.MarcaBorde, if (marcado) "Quitar marca" else "Marcar capítulo") { alternar(ci) }
                }
                acciones.urlWeb?.let { url ->
                    BotonRedondo(Icons.Rounded.Share, "Compartir enlace") {
                        val u = url(ci)
                        if (u == null) Avisos.mostrar("Este capítulo no tiene dirección web")
                        else compartirTexto(ctx, "$titulo - ${cap.nombre}\n$u")
                    }
                }
            }
        }

        // ── Barra inferior: avance + acciones ──
        AnimatedVisibility(
            visible = barras, modifier = Modifier.align(Alignment.BottomCenter),
            enter = fadeIn(tween(200)) + slideInVertically(tween(280)) { it },
            exit = fadeOut(tween(150)) + slideOutVertically(tween(220)) { it },
        ) {
            Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
                val rtl = modo == ModoVisor.DERECHA_IZQUIERDA
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)
                        .graphicsLayer { if (rtl) scaleX = -1f },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val girar = Modifier.graphicsLayer { if (rtl) scaleX = -1f }
                    BotonCapitulo(Icons.Rounded.KeyboardArrowLeft, "Capítulo anterior", hayAnterior, girar) { irCapitulo(ci - 1, false) }
                    Spacer(Modifier.width(8.dp))
                    Row(
                        Modifier.weight(1f).clip(CircleShape).background(ColorBarra).padding(horizontal = 14.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val tot = n ?: 0
                        Text("${if (tot > 0) paginaReal + 1 else 0}", style = LectorType.Label, color = TintaBarra, modifier = girar.widthIn(min = 24.dp), textAlign = TextAlign.Center)
                        if (tot > 1) {
                            var arrastre by remember { mutableStateOf<Float?>(null) }
                            Slider(
                                value = arrastre ?: paginaReal.toFloat(),
                                onValueChange = { arrastre = it; irAPagina(it.roundToInt()) },
                                onValueChangeFinished = { arrastre = null },
                                valueRange = 0f..(tot - 1).toFloat(),
                                colors = SliderDefaults.colors(
                                    thumbColor = TintaBarra,
                                    activeTrackColor = MaterialTheme.colorScheme.primary,
                                    inactiveTrackColor = Color(0x33FFFFFF),
                                ),
                                modifier = Modifier.weight(1f).padding(horizontal = 6.dp),
                            )
                        } else Spacer(Modifier.weight(1f))
                        Text("$tot", style = LectorType.Label, color = TintaBarra, modifier = girar.widthIn(min = 24.dp), textAlign = TextAlign.Center)
                    }
                    Spacer(Modifier.width(8.dp))
                    BotonCapitulo(Icons.Rounded.KeyboardArrowRight, "Capítulo siguiente", haySiguiente, girar) { irCapitulo(ci + 1, false) }
                }
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)
                        .clip(SuaveShape(28.dp)).background(ColorBarra)
                        .border(1.dp, BordeBarra, SuaveShape(28.dp)).padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    if (capitulos.size > 1) AccionVisor(Icons.Rounded.List, "Capítulos") { dialogoCaps = true }
                    // WebView del capítulo que estás leyendo (en lugar de la reproducción automática).
                    if (acciones.urlWeb != null) AccionVisor(Icons.Rounded.ArrowForward, "WebView") {
                        val u = acciones.urlWeb.invoke(ci)
                        if (u == null) Avisos.mostrar("Este capítulo no tiene dirección web") else webCap = u
                    }
                    AccionVisor(Icons.Rounded.Refresh, "Recargar") { CacheVisor.vaciar(); recarga++ }
                    AccionVisor(Icons.Rounded.Build, "Modo") { hoja = true }
                    AccionVisor(Icons.Rounded.Settings, "Ajustes") { hoja = true }
                }
            }
        }
    }

    // Al volver del WebView se recargan las páginas (por si se pasó un captcha o Cloudflare).
    webCap?.let { u -> WebViewScreen(u, titulo, acciones.cabecerasWeb) { webCap = null; CacheVisor.vaciar(); recarga++ } }

    if (hoja) {
        HojaLector({ hoja = false }) {
            Column(Modifier.fillMaxWidth().heightIn(max = (conf.screenHeightDp * 0.75f).dp).verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
                run {
                    AjustesVisorContenido(
                        almacen = almacen,
                        modoActual = modo,
                        paraSerie = claveSerie != null,
                        alElegirModo = { m, soloSerie ->
                            modo = m
                            if (soloSerie && claveSerie != null) almacen.guardarModoVisor(claveSerie, m)
                            else { almacen.modoVisor = m; if (claveSerie != null) almacen.guardarModoVisor(claveSerie, null) }
                        },
                    )
                }
            }
        }
    }

    if (dialogoCaps) {
        AlertDialog(
            onDismissRequest = { dialogoCaps = false },
            title = { Text("Capítulos") },
            text = {
                val estado = rememberLazyListState(initialFirstVisibleItemIndex = (ci - 3).coerceAtLeast(0))
                LazyColumn(Modifier.heightIn(max = 440.dp), state = estado) {
                    itemsIndexed(capitulos, key = { _, c -> c.clave }) { i, c ->
                        val leido = acciones.esLeido?.invoke(i) == true
                        Text(
                            c.nombre,
                            Modifier.fillMaxWidth().clickable { irCapitulo(i, false); dialogoCaps = false }.padding(vertical = 10.dp),
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                            fontWeight = if (i == ci) FontWeight.Bold else FontWeight.Normal,
                            color = if (i == ci) MaterialTheme.colorScheme.primary
                            else if (leido) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { dialogoCaps = false }) { Text("Cerrar") } },
        )
    }

    menuPagina?.let { p ->
        MenuPagina(
            ctx = ctx, origen = cap.origen, pagina = p, titulo = "$titulo - ${cap.nombre}",
            usarComoPortada = acciones.usarComoPortada,
            onCerrar = { menuPagina = null },
        )
    }
}

private fun claveItem(i: ItemVisor?, pos: Int): Any = when (i) {
    is ItemVisor.Pagina -> "p${i.c}_${i.ref.indice}_${i.ref.mitad}"
    is ItemVisor.Transicion -> if (i.siguiente) "t_sig${i.c}" else "t_ant${i.c}"
    null -> "x$pos"
}

// Las barras del lector siguen el tema (claro/oscuro) y el estilo de superficie de Apariencia, como el resto de la app.
private val ColorBarra: Color
    get() = when (Aspecto.superficie) {
        Superficie.SOLIDO -> LectorColors.Cristal
        Superficie.LIQUIDO -> LectorColors.Cristal.copy(alpha = 0.62f)
        Superficie.CRISTAL -> LectorColors.Cristal.copy(alpha = 0.92f)
    }
private val BordeBarra: Color
    get() = when {
        Aspecto.superficie == Superficie.LIQUIDO -> Color.White.copy(alpha = if (TemaActual.oscuro) 0.33f else 0.8f)
        else -> LectorColors.Linea
    }
private val TintaBarra: Color get() = LectorColors.Text
private val SubBarra: Color get() = LectorColors.Muted

@Composable
private fun BotonRedondo(icono: androidx.compose.ui.graphics.vector.ImageVector, desc: String, onClick: () -> Unit) {
    Box(Modifier.size(44.dp).pressable(onClick).clip(CircleShape), Alignment.Center) {
        Icon(icono, desc, tint = TintaBarra)
    }
}

@Composable
private fun BotonOscuro(texto: String, icono: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Row(
        Modifier.pressable(onClick).clip(CircleShape).background(Color(0x33FFFFFF)).padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icono, null, tint = TintaBarra, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(texto, color = TintaBarra, style = LectorType.Body)
    }
}

@Composable
private fun BotonCapitulo(
    icono: androidx.compose.ui.graphics.vector.ImageVector, desc: String, activo: Boolean, extra: Modifier, onClick: () -> Unit,
) {
    Box(
        Modifier.size(50.dp).then(if (activo) Modifier.pressable(onClick) else Modifier)
            .clip(CircleShape).background(ColorBarra),
        Alignment.Center,
    ) { Icon(icono, desc, tint = if (activo) TintaBarra else Color(0x669A9AA0), modifier = extra) }
}

@Composable
private fun AccionVisor(icono: androidx.compose.ui.graphics.vector.ImageVector, texto: String, onClick: () -> Unit) {
    Column(Modifier.pressable(onClick).padding(horizontal = 8.dp, vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icono, texto, tint = TintaBarra)
        Text(texto, style = LectorType.Label.copy(fontSize = 11.sp), maxLines = 1, color = SubBarra)
    }
}

// ───────────────────── Páginas en los modos paginados ─────────────────────

@Composable
private fun PaginaPaginada(
    origen: OrigenPaginas, ref: RefPagina, anchoMax: Int, recortar: Boolean, escala: EscalaPagina,
    filtro: ColorFilter?, dobleToque: Boolean, detectarAncha: Boolean,
    onAncha: () -> Unit, onToque: (Offset, IntSize) -> Unit, onLargo: () -> Unit,
) {
    val clave = CacheVisor.clave(origen, ref.indice, ref.mitad, anchoMax, recortar)
    var pag by remember(clave) { mutableStateOf(CacheVisor.enMemoria(clave)) }
    var fallo by remember(clave) { mutableStateOf<String?>(null) }
    var intento by remember(clave) { mutableIntStateOf(0) }
    LaunchedEffect(clave, intento) {
        if (pag == null) {
            fallo = null
            try {
                pag = CacheVisor.obtener(origen, ref.indice, ref.mitad, anchoMax, recortar)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                fallo = e.message ?: "Error al cargar la página"
            }
        }
        val p = pag
        if (p != null && detectarAncha && ref.mitad == 0 && p.ancha) onAncha()
    }

    val p = pag
    when {
        p == null -> Box(
            Modifier.fillMaxSize().pointerInput(clave, fallo) {
                detectTapGestures(onTap = { if (fallo != null) intento++ else onToque(it, size) }, onLongPress = { onLargo() })
            },
            Alignment.Center,
        ) {
            if (fallo == null) Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(Modifier.size(28.dp), color = Color.White.copy(alpha = 0.8f))
                Spacer(Modifier.height(10.dp))
                Text("${ref.indice + 1}", color = Color(0x99FFFFFF), style = LectorType.Label)
            }
            else Text(
                "Página ${ref.indice + 1}: $fallo\nToca para reintentar",
                color = Color(0xFFE57373), style = LectorType.Label, textAlign = TextAlign.Center,
                modifier = Modifier.padding(24.dp),
            )
        }
        p.partes.size == 1 && escala != EscalaPagina.AJUSTAR_ANCHO ->
            ZoomPagina(p, escala, filtro, dobleToque, onToque, onLargo)
        else -> {
            // Ajustar al ancho (o página muy alta): se desplaza dentro de la página.
            Box(
                Modifier.fillMaxSize().pointerInput(clave) {
                    detectTapGestures(onTap = { onToque(it, size) }, onLongPress = { onLargo() })
                }
            ) {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.Center) {
                    p.partes.forEach { Image(it, null, Modifier.fillMaxWidth(), contentScale = ContentScale.FillWidth, colorFilter = filtro) }
                }
            }
        }
    }
}

private const val ZOOM_MAXIMO = 5f
private const val ZOOM_DOBLE_TOQUE = 2.5f

/** Página con zoom (pellizco y doble toque). Con zoom, al llegar al borde se deja pasar el gesto al paginador. */
@Composable
private fun ZoomPagina(
    p: PaginaLista, escalaModo: EscalaPagina, filtro: ColorFilter?, dobleToque: Boolean,
    onToque: (Offset, IntSize) -> Unit, onLargo: () -> Unit,
) {
    var escala by remember(p) { mutableFloatStateOf(1f) }
    var desplazamiento by remember(p) { mutableStateOf(Offset.Zero) }
    val contentScale = when (escalaModo) {
        EscalaPagina.AJUSTAR -> ContentScale.Fit
        EscalaPagina.AJUSTAR_ALTO -> ContentScale.FillHeight
        EscalaPagina.ORIGINAL -> ContentScale.None
        EscalaPagina.AJUSTAR_ANCHO -> ContentScale.FillWidth
    }
    Box(
        Modifier.fillMaxSize()
            .pointerInput(p, dobleToque) {
                detectTapGestures(
                    onTap = { onToque(it, size) },
                    onLongPress = { onLargo() },
                    onDoubleTap = if (!dobleToque) null else { t ->
                        if (escala > 1f) {
                            escala = 1f; desplazamiento = Offset.Zero
                        } else {
                            val centro = Offset(size.width / 2f, size.height / 2f)
                            escala = ZOOM_DOBLE_TOQUE
                            desplazamiento = limitar((centro - t) * (escala - 1f), escala, size)
                        }
                    },
                )
            }
            // Pellizco y arrastre. Con un dedo y sin zoom no se consume nada: el paginador pasa de página.
            .pointerInput(p) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val ev = awaitPointerEvent()
                        val dedos = ev.changes.count { it.pressed }
                        if (dedos >= 2 || escala > 1f) {
                            val zoom = ev.calculateZoom()
                            val pan = ev.calculatePan()
                            val centro = Offset(size.width / 2f, size.height / 2f)
                            val foco = ev.calculateCentroid() - centro
                            val nueva = (escala * zoom).coerceIn(1f, ZOOM_MAXIMO)
                            val z = nueva / escala
                            val deseado = desplazamiento * z + foco * (1f - z) + pan
                            val limitado = limitar(deseado, nueva, size)
                            // Un dedo, con zoom, empujando contra el borde: se deja pasar para cambiar de página.
                            val contraBorde = dedos == 1 && abs(pan.x) > abs(pan.y) && abs(limitado.x - desplazamiento.x) < 0.5f && zoom == 1f
                            desplazamiento = limitado
                            escala = nueva
                            if (!contraBorde) ev.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    } while (ev.changes.any { it.pressed })
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Image(
            bitmap = p.partes[0], contentDescription = null, contentScale = contentScale, colorFilter = filtro,
            modifier = Modifier.fillMaxSize().graphicsLayer {
                scaleX = escala; scaleY = escala
                translationX = desplazamiento.x; translationY = desplazamiento.y
            },
        )
    }
}

// Evita que la imagen se arrastre más allá de sus bordes al ampliarla.
private fun limitar(desplazamiento: Offset, escala: Float, tamano: IntSize): Offset {
    val maxX = tamano.width * (escala - 1f) / 2f
    val maxY = tamano.height * (escala - 1f) / 2f
    return Offset(desplazamiento.x.coerceIn(-maxX, maxX), desplazamiento.y.coerceIn(-maxY, maxY))
}

/** Página de transición entre capítulos (como la de Komikku). Arrastrar otra vez hacia delante cambia de capítulo. */
@Composable
private fun Transicion(
    siguiente: Boolean, actual: String, otro: String?, modo: ModoVisor,
    onIr: () -> Unit, onToque: (Offset, IntSize) -> Unit,
) {
    val umbral = with(LocalDensity.current) { 60.dp.toPx() }
    Box(
        Modifier.fillMaxSize()
            .pointerInput(siguiente, otro) { detectTapGestures(onTap = { onToque(it, size) }) }
            .pointerInput(siguiente, otro, modo) {
                if (otro == null) return@pointerInput
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    var acumulado = Offset.Zero
                    do {
                        val ev = awaitPointerEvent()
                        val c = ev.changes.firstOrNull() ?: break
                        acumulado += c.position - c.previousPosition
                        // Hacia delante: ← en cómic, → en manga, ↑ en vertical (al revés en la transición anterior).
                        val avance = when (modo) {
                            ModoVisor.DERECHA_IZQUIERDA -> acumulado.x
                            ModoVisor.VERTICAL -> -acumulado.y
                            else -> -acumulado.x
                        }
                        val haciaDondeToca = if (siguiente) avance else -avance
                        if (haciaDondeToca > 0) c.consume() // en el sentido contrario lo maneja el paginador
                        if (haciaDondeToca > umbral) { onIr(); break }
                    } while (ev.changes.any { it.pressed })
                }
            },
        Alignment.Center,
    ) {
        Column(Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if (siguiente) "Terminado" else "Actual", color = Color(0xFF9A9AA0), style = LectorType.Label)
            Text(actual, color = Color.White, style = LectorType.Section.copy(fontSize = 18.sp), textAlign = TextAlign.Center)
            Spacer(Modifier.height(26.dp))
            if (otro != null) {
                Text(if (siguiente) "Siguiente" else "Anterior", color = Color(0xFF9A9AA0), style = LectorType.Label)
                Text(otro, color = Color.White, style = LectorType.Section.copy(fontSize = 18.sp), textAlign = TextAlign.Center)
                Spacer(Modifier.height(22.dp))
                BotonOscuro(if (siguiente) "Ir al siguiente" else "Ir al anterior", if (siguiente) Icons.Rounded.ArrowForward else Icons.Rounded.ArrowBack, onIr)
                Spacer(Modifier.height(10.dp))
                Text("o desliza otra vez", color = Color(0x809A9AA0), style = LectorType.Label)
            } else {
                Text(if (siguiente) "No hay más capítulos" else "Es el primer capítulo", color = Color.White, style = LectorType.Body)
            }
        }
    }
}

// ───────────────────── Tira vertical (webtoon / continuo) ─────────────────────

@Composable
private fun Tira(
    items: List<ItemVisor>, lista: androidx.compose.foundation.lazy.LazyListState,
    cap: CapVisor, capitulos: List<CapVisor>, ci: Int,
    anchoMax: Int, recortar: Boolean, filtro: ColorFilter?, fondo: Color, relleno: Dp, separacion: Dp,
    dobleToque: Boolean,
    onToque: (Offset, IntSize) -> Unit, onLargo: (Int) -> Unit, onIr: (Boolean) -> Unit,
) {
    // Zoom de toda la tira (pellizco o doble toque) con desplazamiento lateral.
    var escala by remember { mutableFloatStateOf(1f) }
    var panX by remember { mutableFloatStateOf(0f) }
    var ancho by remember { mutableIntStateOf(1) }
    Box(
        Modifier.fillMaxSize()
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    do {
                        val ev = awaitPointerEvent(PointerEventPass.Initial)
                        val dedos = ev.changes.count { it.pressed }
                        if (dedos >= 2) {
                            escala = (escala * ev.calculateZoom()).coerceIn(1f, 3f)
                        }
                        if (escala > 1f) {
                            val max = ancho * (escala - 1f) / 2f
                            panX = (panX + ev.calculatePan().x).coerceIn(-max, max)
                        } else panX = 0f
                    } while (ev.changes.any { it.pressed })
                }
            }
            .pointerInput(dobleToque, items.size) {
                detectTapGestures(
                    onTap = { onToque(it, size) },
                    onDoubleTap = if (!dobleToque) null else { t ->
                        if (escala > 1f) { escala = 1f; panX = 0f } else {
                            escala = 2f
                            panX = ((size.width / 2f - t.x) * (escala - 1f)).coerceIn(-size.width / 2f, size.width / 2f)
                        }
                    },
                    onLongPress = { pos ->
                        // ¿Qué página hay bajo el dedo?
                        val y = pos.y.roundToInt()
                        val info = lista.layoutInfo.visibleItemsInfo.firstOrNull { y >= it.offset && y < it.offset + it.size }
                        (items.getOrNull(info?.index ?: -1) as? ItemVisor.Pagina)?.let { onLargo(it.ref.indice) }
                    },
                )
            }
    ) {
        LazyColumn(
            state = lista,
            modifier = Modifier.fillMaxSize()
                .onSizeChanged { ancho = it.width.coerceAtLeast(1) }
                .graphicsLayer {
                    scaleX = escala; scaleY = escala; translationX = panX
                    transformOrigin = TransformOrigin(0.5f, 0.5f)
                },
            contentPadding = PaddingValues(horizontal = relleno),
            verticalArrangement = Arrangement.spacedBy(separacion),
        ) {
            items(items.size, key = { claveItem(items.getOrNull(it), it) }) { i ->
                when (val it = items[i]) {
                    is ItemVisor.Pagina -> PaginaTira((capitulos.getOrNull(it.c) ?: cap).origen, it.ref.indice, anchoMax, recortar, filtro, fondo)
                    is ItemVisor.Transicion -> {
                        val este = capitulos.getOrNull(it.c) ?: cap
                        val k = if (it.c >= 0) it.c else ci
                        val otro = if (it.siguiente) capitulos.getOrNull(k + 1)?.nombre else capitulos.getOrNull(k - 1)?.nombre
                        Column(
                            Modifier.fillMaxWidth().padding(vertical = 48.dp, horizontal = 24.dp).navigationBarsPadding(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(if (it.siguiente) "Finalizado: ${este.nombre}" else "Actual: ${este.nombre}", color = Color(0xFF9A9AA0), style = LectorType.Label, textAlign = TextAlign.Center)
                            Spacer(Modifier.height(12.dp))
                            if (otro != null) {
                                Text(if (it.siguiente) "Siguiente: $otro" else "Anterior: $otro", color = Color.White, style = LectorType.Section, textAlign = TextAlign.Center)
                            } else {
                                Text(if (it.siguiente) "No hay más capítulos" else "Es el primer capítulo", color = Color.White, style = LectorType.Body)
                            }
                            if (it.siguiente && otro == null) Spacer(Modifier.height(160.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PaginaTira(origen: OrigenPaginas, indice: Int, anchoMax: Int, recortar: Boolean, filtro: ColorFilter?, fondo: Color) {
    val clave = CacheVisor.clave(origen, indice, 0, anchoMax, recortar)
    var pag by remember(clave) { mutableStateOf(CacheVisor.enMemoria(clave)) }
    var fallo by remember(clave) { mutableStateOf<String?>(null) }
    var intento by remember(clave) { mutableIntStateOf(0) }
    LaunchedEffect(clave, intento) {
        if (pag != null) return@LaunchedEffect
        fallo = null
        try {
            pag = CacheVisor.obtener(origen, indice, 0, anchoMax, recortar)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            fallo = e.message ?: "Error al cargar la página"
        }
    }
    val p = pag
    if (p != null) {
        Column(Modifier.fillMaxWidth()) {
            p.partes.forEach { Image(it, null, Modifier.fillMaxWidth(), contentScale = ContentScale.FillWidth, colorFilter = filtro) }
        }
    } else {
        Box(
            Modifier.fillMaxWidth().aspectRatio(1f / CacheVisor.proporcion).background(fondo)
                .then(if (fallo != null) Modifier.clickable { intento++ } else Modifier),
            Alignment.Center,
        ) {
            if (fallo == null) CircularProgressIndicator(Modifier.size(26.dp), color = Color.White.copy(alpha = 0.7f))
            else Text("${indice + 1}: $fallo\nToca para reintentar", color = Color(0xFFE57373), style = LectorType.Label, textAlign = TextAlign.Center, modifier = Modifier.padding(24.dp))
        }
    }
}

// ───────────────────── Menú de una página (mantener pulsado) ─────────────────────

@Composable
private fun MenuPagina(
    ctx: Context, origen: OrigenPaginas, pagina: Int, titulo: String,
    usarComoPortada: (suspend (ByteArray) -> Unit)?, onCerrar: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var trabajando by remember { mutableStateOf(false) }
    fun conBytes(accion: suspend (ByteArray) -> Unit) {
        if (trabajando) return
        trabajando = true
        scope.launch {
            val bytes = runCatching { origen.bytes(pagina) }.getOrNull()
            if (bytes == null) Avisos.mostrar("No se pudo leer la página")
            else runCatching { accion(bytes) }.onFailure { Avisos.mostrar(it.message ?: "No se pudo completar") }
            trabajando = false
            onCerrar()
        }
    }
    AlertDialog(
        onDismissRequest = onCerrar,
        title = { Text("Página ${pagina + 1}") },
        text = {
            Column {
                if (trabajando) CircularProgressIndicator(Modifier.size(24.dp))
                else {
                    TextButton(onClick = { conBytes { compartirImagen(ctx, it, titulo) } }) { Text("Compartir página") }
                    TextButton(onClick = { conBytes { Avisos.mostrar(guardarImagen(ctx, it, titulo, pagina)) } }) { Text("Guardar en el teléfono") }
                    if (usarComoPortada != null) {
                        TextButton(onClick = { conBytes { usarComoPortada(it); Avisos.mostrar("Portada cambiada") } }) { Text("Usar como portada") }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onCerrar) { Text("Cerrar") } },
    )
}

private fun extensionImagen(b: ByteArray): String = when {
    b.size > 4 && b[0] == 0x89.toByte() && b[1] == 'P'.code.toByte() -> "png"
    b.size > 12 && b[0] == 'R'.code.toByte() && b[8] == 'W'.code.toByte() -> "webp"
    b.size > 3 && b[0] == 'G'.code.toByte() && b[1] == 'I'.code.toByte() -> "gif"
    else -> "jpg"
}

private fun mimeDe(ext: String) = when (ext) { "png" -> "image/png"; "webp" -> "image/webp"; "gif" -> "image/gif"; else -> "image/jpeg" }

/** Comparte una imagen con otras apps (vía FileProvider). */
suspend fun compartirImagen(ctx: Context, bytes: ByteArray, texto: String) {
    val ext = extensionImagen(bytes)
    val archivo = withContext(Dispatchers.IO) {
        File(File(ctx.cacheDir, "compartir").apply { mkdirs() }, "pagina.$ext").apply { writeBytes(bytes) }
    }
    val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.archivos", archivo)
    val envio = Intent(Intent.ACTION_SEND).setType(mimeDe(ext))
        .putExtra(Intent.EXTRA_STREAM, uri).putExtra(Intent.EXTRA_TEXT, texto)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    ctx.startActivity(Intent.createChooser(envio, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

fun compartirTexto(ctx: Context, texto: String) {
    val envio = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, texto)
    runCatching { ctx.startActivity(Intent.createChooser(envio, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

/** Guarda una imagen en Imágenes/Lector (Android 10+) o en la carpeta de la app. Devuelve el aviso para el usuario. */
suspend fun guardarImagen(ctx: Context, bytes: ByteArray, titulo: String, pagina: Int): String = withContext(Dispatchers.IO) {
    val ext = extensionImagen(bytes)
    val nombre = (titulo.replace(Regex("[^\\p{L}\\p{N} ._-]"), "").take(60).trim().ifBlank { "pagina" }) + " - ${pagina + 1}.$ext"
    if (Build.VERSION.SDK_INT >= 29) {
        val valores = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, nombre)
            put(MediaStore.Images.Media.MIME_TYPE, mimeDe(ext))
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Lector")
        }
        val uri: Uri = ctx.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, valores)
            ?: error("No se pudo crear la imagen")
        ctx.contentResolver.openOutputStream(uri)?.use { it.write(bytes) } ?: error("No se pudo escribir la imagen")
        "Guardada en Imágenes/Lector"
    } else {
        val dir = File(ctx.getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES), "Lector").apply { mkdirs() }
        File(dir, nombre).writeBytes(bytes)
        "Guardada en ${dir.path}"
    }
}

// ───────────────────── Pantalla: barras del sistema, brillo, orientación, filtros ─────────────────────

// Oculta las barras del sistema (estado y navegación) mientras `oculta` sea true. Al salir, las vuelve a mostrar.
@Composable
fun PantallaCompleta(oculta: Boolean) {
    val vista = LocalView.current
    DisposableEffect(oculta) {
        val ventana = (vista.context as? Activity)?.window
        val control: WindowInsetsControllerCompat? =
            if (ventana != null) WindowCompat.getInsetsController(ventana, vista) else null
        if (oculta) {
            control?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            control?.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            control?.show(WindowInsetsCompat.Type.systemBars())
        }
        onDispose { control?.show(WindowInsetsCompat.Type.systemBars()) }
    }
}

// Mantiene la pantalla encendida mientras lees.
@Composable
fun PantallaEncendida(activa: Boolean) {
    val vista = LocalView.current
    DisposableEffect(activa) {
        vista.keepScreenOn = activa
        onDispose { vista.keepScreenOn = false }
    }
}

// Avisa a la actividad de que debe interceptar las teclas de volumen.
@Composable
fun TeclasVolumen(activas: Boolean) {
    DisposableEffect(activas) {
        Teclas.activo = activas
        onDispose { Teclas.activo = false }
    }
}

// Matriz de color para escala de grises y/o colores invertidos (null si no hay ninguno).
fun construirFiltro(grises: Boolean, invertir: Boolean): ColorFilter? {
    if (!grises && !invertir) return null
    val matriz = ColorMatrix()
    if (grises) matriz.setToSaturation(0f)
    if (invertir) {
        matriz *= ColorMatrix(
            floatArrayOf(
                -1f, 0f, 0f, 0f, 255f,
                0f, -1f, 0f, 0f, 255f,
                0f, 0f, -1f, 0f, 255f,
                0f, 0f, 0f, 1f, 0f,
            ),
        )
    }
    return ColorFilter.colorMatrix(matriz)
}

// Orientación y brillo propios mientras el visor está abierto. Al salir se restauran.
@Composable
fun AjustesPantalla(almacen: Almacen) {
    val vista = LocalView.current
    val actividad = vista.context as? Activity

    DisposableEffect(almacen.orientacion) {
        actividad?.requestedOrientation = when (almacen.orientacion) {
            Orientacion.LIBRE -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            Orientacion.VERTICAL -> ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT
            Orientacion.HORIZONTAL -> ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE
        }
        onDispose { actividad?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
    }

    DisposableEffect(almacen.brilloPersonalizado, almacen.brillo) {
        val ventana = actividad?.window
        if (ventana != null) {
            val atributos = ventana.attributes
            atributos.screenBrightness = if (almacen.brilloPersonalizado) almacen.brillo.coerceIn(0.01f, 1f)
            else WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            ventana.attributes = atributos
        }
        onDispose {
            if (ventana != null) {
                val atributos = ventana.attributes
                atributos.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                ventana.attributes = atributos
            }
        }
    }
}
