package com.example.lector.ui

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.OutlinedTextField
import com.example.lector.Comic
import com.example.lector.Anilist
import com.example.lector.ResultadoAnilist
import com.example.lector.rememberPortada
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.animation.animateColorAsState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.SliderDefaults
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.lector.Almacen
import com.example.lector.ext.*
import com.example.lector.Cbz
import com.example.lector.EntradaHistorial
import com.example.lector.Descargas
import com.example.lector.Tinte
import com.example.lector.colorFondo
import com.example.lector.colorTinte
import androidx.compose.ui.graphics.ColorFilter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipFile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

private fun estadoTexto(e: Int) = when (e) {
    1 -> "En emisión"
    2 -> "Completado"
    3 -> "Licenciado"
    4 -> "Publicación finalizada"
    5 -> "Cancelado"
    6 -> "En pausa"
    else -> "Estado desconocido"
}

private fun fechaTexto(ms: Long): String =
    if (ms <= 0) "" else SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(ms))

private fun numeroTexto(n: Float): String =
    if (n < 0) "" else if (n % 1f == 0f) n.toInt().toString() else n.toString()

private fun urlManga(f: FuenteUI, m: MangaFuente): String? =
    if (m.url.startsWith("http")) m.url
    else if (f.baseUrl.isBlank()) null
    else f.baseUrl.trimEnd('/') + "/" + m.url.trimStart('/')

// ───────────────────── Ficha del manga (online) ─────────────────────

private fun nombreOrdenCap(o: Int) = when (o) { 1 -> "Número de capítulo"; 2 -> "Fecha de subida"; else -> "Orden de la fuente" }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FichaMangaScreen(
    ext: ExtManager,
    fuenteId: Long,
    manga: MangaFuente,
    almacen: Almacen,
    alRecargar: () -> Unit,
    onBack: () -> Unit,
    // (capítulos, índice del capítulo, página por la que seguir)
    onLeer: (List<CapituloFuente>, Int, Int) -> Unit,
    // Si viene del historial: capítulo y página que hay que abrir en cuanto cargue la ficha.
    reanudarCap: Pair<String, Int>? = null,
    alConsumirReanudar: () -> Unit = {},
    // Tocar un género de la ficha: busca ese género en la fuente (como «searchGenre» de Komikku).
    alGenero: (String) -> Unit = {},
    // Buscar este título en todas las fuentes (búsqueda global).
    alBuscarGlobal: (String) -> Unit = {},
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val f = ext.fuentes.firstOrNull { it.id == fuenteId }
    val motor = Motor.actual

    var ficha by remember { mutableStateOf<FichaManga?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var cargando by remember { mutableStateOf(true) }
    var descExpandida by remember { mutableStateOf(false) }
    var recarga by remember { mutableStateOf(0) }
    var webUrl by remember { mutableStateOf<String?>(null) }

    // Biblioteca y categorías (se recalcula sola cuando cambia la biblioteca).
    val enBiblioteca: Comic? = remember(almacen.version, fuenteId, manga.url) { almacen.comicEnLinea(fuenteId, manga.url) }
    var guardando by remember { mutableStateOf(false) }
    var dialogoCats by remember { mutableStateOf(false) }
    var editandoCats by remember { mutableStateOf(false) }
    var quitarDescarga by remember { mutableStateOf<CapituloFuente?>(null) }
    var menuDescarga by remember { mutableStateOf(false) }
    var migrando by remember { mutableStateOf(false) }
    var hojaCaps by remember { mutableStateOf(false) }
    var pestanaHoja by remember { mutableIntStateOf(0) }
    var seleccion by remember { mutableStateOf(emptySet<String>()) }
    var verPortada by remember { mutableStateOf(false) }
    var editarInfo by remember { mutableStateOf(false) }
    var seguimiento by remember { mutableStateOf(false) }
    var recomendaciones by remember { mutableStateOf<List<ResultadoAnilist>?>(null) }

    LaunchedEffect(fuenteId, manga.url, recarga) {
        if (f == null || motor == null) return@LaunchedEffect
        cargando = true; error = null
        val r = runCatching { motor.ficha(f, manga) }
        r.onSuccess { ficha = it }
            .onFailure { error = it.message ?: "No se pudo cargar la ficha" }
        cargando = false
        val cargada = r.getOrNull() ?: return@LaunchedEffect
        // Si está en la biblioteca: progreso, datos (autor, estado, géneros…) y capítulos nuevos.
        if (almacen.comicEnLinea(f.id, manga.url) != null) {
            almacen.actualizarProgresoEnLinea(
                f.id, manga.url, cargada.capitulos.count { ext.esLeido(f.id, it.url) }, cargada.capitulos.size,
            )
            almacen.actualizarInfoEnLinea(
                f.id, manga.url, cargada.detalle.autor, cargada.detalle.estado, cargada.detalle.generos,
                cargada.detalle.descripcion, cargada.detalle.portadaUrl, cargada.capitulos.maxOfOrNull { it.fecha } ?: 0L,
            )
            almacen.registrarCapitulos(
                f.id, manga.url, cargada.detalle.titulo.ifBlank { manga.titulo },
                cargada.detalle.portadaUrl ?: manga.portadaUrl, cargada.capitulos,
            )
        }
        // Venimos del historial: abre directamente el capítulo donde lo dejaste.
        if (reanudarCap != null) {
            alConsumirReanudar()
            val i = cargada.capitulos.indexOfFirst { it.url == reanudarCap.first }
            if (i >= 0) onLeer(cargada.capitulos, i, reanudarCap.second)
        }
    }

    val lista = rememberLazyListState()
    val fi = ficha
    val comic = enBiblioteca
    val editado = comic?.editado == true
    // Lo que se enseña: si el usuario editó la información, manda la suya.
    val tituloFicha = if (editado) comic!!.titulo else fi?.detalle?.titulo ?: manga.titulo
    val autor = if (editado) comic!!.autor else fi?.detalle?.autor
    val estadoPub = if (editado && comic!!.estado != 0) comic.estado else fi?.detalle?.estado
    val descripcion = if (editado) comic!!.descripcion else fi?.detalle?.descripcion
    val generos = if (editado) comic!!.generos else fi?.detalle?.generos.orEmpty()

    // Recomendaciones (AniList) del título.
    LaunchedEffect(tituloFicha, fi != null) {
        if (fi != null && recomendaciones == null) {
            recomendaciones = withContext(Dispatchers.IO) { runCatching { Anilist.recomendaciones(tituloFicha) }.getOrDefault(emptyList()) }
        }
    }

    // Estética de la ficha: el fondo y el acento toman el tono de la portada (si "Tema según la portada" está activo).
    var paleta by remember { mutableStateOf<TemaPortada?>(null) }
    var portadaLista by remember { mutableStateOf(false) }
    val conTema = almacen.colorPortada
    val acento = (if (conTema) paleta?.acento else null) ?: MaterialTheme.colorScheme.primary
    val fondoBase = LectorColors.Bg
    val fondoAnim by animateColorAsState(
        (if (conTema && TemaActual.oscuro) paleta?.fondo else null) ?: fondoBase, tween(800), label = "fondoFicha",
    )

    // Corazón "En biblioteca": añade (descargando la portada) o quita el manga.
    fun alternarBiblioteca() {
        if (f == null || motor == null || guardando) return
        if (enBiblioteca != null) {
            almacen.quitarEnLinea(f.id, manga.url)
            alRecargar()
            Avisos.mostrar("Quitado de la biblioteca")
            return
        }
        guardando = true
        scope.launch {
            val titulo = fi?.detalle?.titulo ?: manga.titulo
            val urlPortada = fi?.detalle?.portadaUrl ?: manga.portadaUrl
            val portada = withContext(Dispatchers.IO) {
                urlPortada?.let {
                    runCatching { Imagenes.cargar(it, motor.cabeceras(f), 400) }.getOrNull()?.asAndroidBitmap()
                }
            }
            val leidos = fi?.capitulos?.count { ext.esLeido(f.id, it.url) } ?: 0
            withContext(Dispatchers.IO) {
                almacen.anadirEnLinea(
                    f.id, manga.url, titulo, urlPortada, portada, fi?.capitulos?.size ?: 0, leidos,
                    autor = fi?.detalle?.autor, estado = fi?.detalle?.estado ?: 0, generos = fi?.detalle?.generos.orEmpty(),
                    descripcion = fi?.detalle?.descripcion,
                )
                // Los capítulos que hay ahora son la base: solo lo que salga después contará como novedad.
                fi?.let {
                    almacen.registrarCapitulos(f.id, manga.url, titulo, urlPortada, it.capitulos)
                    almacen.actualizarInfoEnLinea(f.id, manga.url, null, 0, emptyList(), null, null, it.capitulos.maxOfOrNull { c -> c.fecha } ?: 0L)
                }
            }
            guardando = false
            alRecargar()
            Avisos.mostrar("Añadido a la biblioteca")
            // Como en Komikku: al añadir, pregunta las categorías (si hay).
            if (almacen.categorias().isNotEmpty()) dialogoCats = true
        }
    }

    if (f == null || motor == null) {
        Column(Modifier.fillMaxSize().background(LectorColors.Bg).statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                BotonRedondoTema(Icons.Rounded.ArrowBack, "Atrás", onBack)
            }
            LText("La fuente no está disponible (¿se desinstaló la extensión o dejó de ser de confianza?).", LectorType.Body, Modifier.padding(24.dp), color = LectorColors.Muted)
        }
        return
    }

    BackHandler(enabled = seleccion.isNotEmpty()) { seleccion = emptySet() }

    // ---- Lista de capítulos con filtros y orden ----
    val leidosAhora = ext.leidos
    val marcadosAhora = ext.marcados
    val versionDesc = Descargas.version.value + almacen.version
    val visibles = remember(fi, leidosAhora, marcadosAhora, versionDesc, ext.capNoLeidos, ext.capDescargados, ext.capMarcados, ext.capOrden, ext.capDescendente) {
        val base = fi?.capitulos?.withIndex()?.toList().orEmpty()
        val filtrados = base.filter { iv ->
            ext.capNoLeidos.deja(!ext.esLeido(f.id, iv.value.url)) &&
                ext.capDescargados.deja(almacen.estaDescargado(f.id, iv.value.url)) &&
                ext.capMarcados.deja(ext.esMarcado(f.id, iv.value.url))
        }
        val desc = ext.capDescendente
        when (ext.capOrden) {
            1 -> if (desc) filtrados.sortedByDescending { it.value.numero } else filtrados.sortedBy { it.value.numero }
            2 -> if (desc) filtrados.sortedByDescending { it.value.fecha } else filtrados.sortedBy { it.value.fecha }
            else -> if (desc) filtrados else filtrados.asReversed()
        }
    }
    val filtrosCap = listOf(ext.capNoLeidos, ext.capDescargados, ext.capMarcados).count { it != com.example.lector.Tri.IGNORAR }

    // Cuánto se ha desplazado la cabecera (se lee al dibujar, sin recomponer).
    val scrollPx: () -> Float = { if (lista.firstVisibleItemIndex == 0) lista.firstVisibleItemScrollOffset.toFloat() else 99999f }
    // La mayoría de fuentes listan el capítulo más nuevo primero: el primero a leer es el último sin leer.
    val sig = remember(fi, leidosAhora) {
        fi?.let { x -> (x.capitulos.lastIndex downTo 0).firstOrNull { !ext.esLeido(f.id, x.capitulos[it].url) } ?: 0 } ?: 0
    }
    val paginaSig = fi?.capitulos?.getOrNull(sig)?.let { ext.paginaDe(f.id, it.url) } ?: 0
    val hayLeidos = remember(fi, leidosAhora) { fi?.capitulos?.any { ext.esLeido(f.id, it.url) } == true }
    val urlWeb = remember(f, manga) { motor.urlManga(f, manga) ?: urlManga(f, manga) }
    val abrirNavegador: () -> Unit = {
        if (urlWeb == null) Avisos.mostrar("Esta fuente no da la dirección web") else webUrl = urlWeb
    }
    fun progreso() {
        val caps = fi?.capitulos ?: return
        almacen.actualizarProgresoEnLinea(f.id, manga.url, caps.count { ext.esLeido(f.id, it.url) }, caps.size)
    }
    val enSeleccion = seleccion.isNotEmpty()

    Box(Modifier.fillMaxSize().drawBehind { drawRect(fondoAnim) }) {
        LazyColumn(Modifier.fillMaxSize(), state = lista, contentPadding = PaddingValues(bottom = if (enSeleccion) 130.dp else 60.dp)) {
            item {
                val lineas = listOfNotNull(
                    autor,
                    estadoPub?.let { estadoTexto(it) },
                    f.nombre,
                    fi?.let { "${it.capitulos.size} capítulos" },
                )
                CabeceraFicha(
                    portada = fi?.detalle?.portadaUrl ?: manga.portadaUrl, cabeceras = motor.cabeceras(f),
                    comicLocal = comic?.takeIf { it.portadaPropia }, almacen = almacen,
                    titulo = tituloFicha, lineas = lineas, acento = acento, fondo = fondoAnim,
                    scroll = scrollPx, portadaLista = portadaLista,
                    onCargada = { portadaLista = true; if (conTema) paleta = temaDe(it) },
                    enBiblioteca = enBiblioteca != null,
                    textoPildora = if (enBiblioteca != null) "Categorías" else "Añadir a biblioteca",
                    onLeer = { fi?.let { onLeer(it.capitulos, sig, paginaSig) } },
                    onCorazon = { alternarBiblioteca() },
                    onPildora = { if (enBiblioteca != null) dialogoCats = true else alternarBiblioteca() },
                    onPortada = { verPortada = true },
                )
            }
            if (fi != null) {
                // Tarjetas deslizables: último capítulo y sinopsis.
                item {
                    val ultimo = fi.capitulos.firstOrNull()
                    val sinopsis = descripcion?.takeIf { it.isNotBlank() }
                    if (ultimo != null || sinopsis != null) {
                        val ancho = (LocalConfiguration.current.screenWidthDp * 0.82f).dp
                        LazyRow(
                            Modifier.padding(top = 4.dp),
                            contentPadding = PaddingValues(horizontal = 24.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            if (ultimo != null) item {
                                TarjetaVidrio(Modifier.width(ancho).entrada(0), { onLeer(fi.capitulos, 0, ext.paginaDe(f.id, fi.capitulos[0].url)) }) {
                                    Row(Modifier.padding(start = 22.dp, end = 22.dp, top = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                                        LText("Último capítulo", LectorType.Section.copy(fontSize = 18.sp), Modifier.weight(1f), maxLines = 1)
                                        LText(fechaTexto(ultimo.fecha), LectorType.Label, color = LectorColors.Muted, maxLines = 1)
                                    }
                                    Row(Modifier.padding(22.dp), verticalAlignment = Alignment.CenterVertically) {
                                        ImagenRemota(
                                            fi.detalle.portadaUrl ?: manga.portadaUrl,
                                            Modifier.size(64.dp).clip(SuaveShape(16.dp)), motor.cabeceras(f),
                                        )
                                        Spacer(Modifier.width(14.dp))
                                        Column(Modifier.weight(1f)) {
                                            LText(nombreCapitulo(ultimo), LectorType.Section.copy(fontSize = 18.sp), maxLines = 2)
                                            LText(
                                                ultimo.scanlator?.takeIf { it.isNotBlank() } ?: "${fi.capitulos.size} capítulos",
                                                LectorType.Label, color = LectorColors.Muted, maxLines = 1,
                                            )
                                        }
                                        Spacer(Modifier.width(10.dp))
                                        CirculoClaro(Icons.Rounded.PlayArrow, "Leer último capítulo", ClaroBtn, 48.dp) { onLeer(fi.capitulos, 0, ext.paginaDe(f.id, fi.capitulos[0].url)) }
                                    }
                                }
                            }
                            if (sinopsis != null) item {
                                TarjetaVidrio(Modifier.width(ancho).entrada(1), { descExpandida = true }) {
                                    Column(Modifier.padding(22.dp)) {
                                        LText("Sinopsis", LectorType.Section.copy(fontSize = 18.sp), maxLines = 1)
                                        Spacer(Modifier.height(10.dp))
                                        LText(sinopsis, LectorType.Body, color = LectorColors.Muted, maxLines = 4)
                                    }
                                }
                            }
                        }
                    }
                }
                if (generos.isNotEmpty()) {
                    item {
                        Row(
                            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) { generos.forEach { g -> Box(Modifier.pressable { alGenero(g) }) { ChipBorde(g, acento) } } }
                    }
                }
                // Acciones: seguimiento, editar, migrar, compartir y buscar en otras fuentes.
                item {
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ConAcento(acento) {
                            if (comic != null) {
                                val vinculado = remember(almacen.version) { almacen.seguimientoDe(comic.id) != null }
                                ChipSel(if (vinculado) "AniList ✓" else "Seguimiento", vinculado) { seguimiento = true }
                                ChipSel("Editar información", editado) { editarInfo = true }
                                ChipSel("Migrar", false) { migrando = true }
                            }
                            if (urlWeb != null) ChipSel("Compartir", false) { compartirTexto(ctx, "$tituloFicha\n$urlWeb") }
                            ChipSel("Buscar en otras fuentes", false) { alBuscarGlobal(tituloFicha) }
                        }
                    }
                }
                // Recomendaciones (AniList): tocar una busca ese título en todas tus fuentes.
                val recs = recomendaciones
                if (!recs.isNullOrEmpty()) item {
                    Column(Modifier.padding(top = 16.dp)) {
                        LText("Recomendaciones", LectorType.Section.copy(fontSize = 18.sp), Modifier.padding(horizontal = 24.dp))
                        Spacer(Modifier.height(8.dp))
                        LazyRow(contentPadding = PaddingValues(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(recs, key = { it.id }) { r ->
                                Column(Modifier.width(96.dp).press3D().pressable { alBuscarGlobal(r.titulo) }) {
                                    ImagenRemota(r.portada, Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(SuaveShape(12.dp)))
                                    Spacer(Modifier.height(6.dp))
                                    LText(r.titulo, LectorType.Label, maxLines = 2)
                                }
                            }
                        }
                    }
                }
                item {
                    Row(
                        Modifier.fillMaxWidth().padding(start = 24.dp, end = 16.dp, top = 22.dp, bottom = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        LText("Capítulos", LectorType.Display.copy(fontSize = 30.sp, letterSpacing = (-1).sp))
                        Spacer(Modifier.width(12.dp))
                        Odometro(
                            if (visibles.size != fi.capitulos.size) "${visibles.size}/${fi.capitulos.size}" else "${fi.capitulos.size}",
                            LectorType.Body, Modifier.weight(1f), LectorColors.Muted,
                        )
                        // Menú de descargas en lote (como Komikku): siguiente, siguientes N, no leídos o todos.
                        Box {
                            BotonRedondoTema(Iconos.Descargar, "Descargar capítulos", { menuDescarga = true })
                            DropdownMenu(expanded = menuDescarga, onDismissRequest = { menuDescarga = false }) {
                                // La lista llega con lo más nuevo primero: "siguientes" = desde el primero sin leer hacia los nuevos.
                                val opciones = listOf(
                                    "Siguiente capítulo" to fi.capitulos.slice((sig downTo maxOf(sig, 0)).toList()),
                                    "Siguientes 5" to fi.capitulos.slice((sig downTo maxOf(sig - 4, 0)).toList()),
                                    "Siguientes 10" to fi.capitulos.slice((sig downTo maxOf(sig - 9, 0)).toList()),
                                    "Siguientes 25" to fi.capitulos.slice((sig downTo maxOf(sig - 24, 0)).toList()),
                                    "No leídos" to fi.capitulos.filter { !ext.esLeido(f.id, it.url) }.asReversed(),
                                    "Marcados" to fi.capitulos.filter { ext.esMarcado(f.id, it.url) }.asReversed(),
                                    "Todos" to fi.capitulos.asReversed(),
                                )
                                opciones.forEach { (texto, caps) ->
                                    DropdownMenuItem(
                                        text = { Text(texto) },
                                        onClick = { menuDescarga = false; Descargas.encolar(ctx, f, manga.url, tituloFicha, caps) },
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                        Box(Modifier.size(44.dp).pressable { hojaCaps = true }.clip(CircleShape).background(if (filtrosCap > 0) acento.copy(alpha = 0.25f) else LectorColors.PillFill), Alignment.Center) {
                            Icon(Icons.Rounded.List, "Filtrar y ordenar capítulos", tint = if (filtrosCap > 0) acento else LectorColors.Text)
                        }
                    }
                }
                if (visibles.isEmpty()) item {
                    LText("Ningún capítulo coincide con los filtros.", LectorType.Body, Modifier.padding(24.dp), color = LectorColors.Muted)
                }
                // La clave es el índice original del capítulo (único aunque la fuente repita direcciones).
                itemsIndexed(visibles, key = { _, iv -> iv.index }) { pos, iv ->
                    val i = iv.index
                    val c = iv.value
                    val clave = Descargas.clave(f, c)
                    val paginaGuardada = ext.paginaDe(f.id, c.url)
                    val fraccion = Descargas.progreso[clave] ?: if (Descargas.enCola(clave)) 0f else null
                    val estado: EstadoDescarga = when {
                        fraccion != null -> EstadoDescarga.Descargando(fraccion)
                        versionDesc >= 0 && almacen.estaDescargado(f.id, c.url) -> EstadoDescarga.Listo
                        else -> EstadoDescarga.Libre
                    }
                    val leido = leidosAhora.let { ext.esLeido(f.id, c.url) }
                    val marcado = ext.esMarcado(f.id, c.url)
                    val seleccionado = c.url in seleccion
                    val numero = numeroTexto(c.numero).ifEmpty { "#" }
                    // Deslizar → leído / no leído · deslizar ← marcado / no marcado.
                    val accionLeido = AccionDeslizar(
                        if (leido) Iconos.OjoTachado else Icons.Rounded.Check,
                        if (leido) "Marcar como no leído" else "Marcar como leído",
                        acento,
                    ) {
                        if (leido) ext.desmarcarLeido(f.id, c.url) else ext.marcarLeido(f.id, c.url)
                        progreso()
                    }
                    val accionMarca = AccionDeslizar(
                        if (marcado) Iconos.MarcaBorde else Iconos.Marca,
                        if (marcado) "Quitar marca" else "Marcar capítulo",
                        ColorMarca,
                    ) { ext.alternarMarcado(f.id, c.url) }
                    FilaDeslizable(if (enSeleccion) null else accionLeido, if (enSeleccion) null else accionMarca, Modifier.entrada(pos)) {
                        Row(
                            Modifier.fillMaxWidth()
                                .background(if (seleccionado) acento.copy(alpha = 0.16f) else Color.Transparent)
                                .resaltable(
                                    onLongClick = { seleccion = if (seleccionado) seleccion - c.url else seleccion + c.url },
                                ) {
                                    if (enSeleccion) seleccion = if (seleccionado) seleccion - c.url else seleccion + c.url
                                    else onLeer(fi.capitulos, i, paginaGuardada)
                                }
                                .padding(start = 24.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                Modifier.size(64.dp).clip(SuaveShape(20.dp))
                                    .background(acento.copy(alpha = if (seleccionado) 0.5f else if (leido) 0.10f else 0.24f)),
                                Alignment.Center,
                            ) {
                                if (seleccionado) Icon(Icons.Rounded.Check, null, tint = LectorColors.Text)
                                else LText(
                                    numero, LectorType.Display.copy(fontSize = if (numero.length > 3) 15.sp else 21.sp),
                                    color = if (leido) LectorColors.Muted else LectorColors.Text, maxLines = 1,
                                )
                            }
                            Spacer(Modifier.width(16.dp))
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (marcado) {
                                        Icon(Iconos.Marca, "Marcado", tint = ColorMarca, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                    }
                                    LText(
                                        if (ext.capVerNumero && c.numero >= 0) "Capítulo ${numeroTexto(c.numero)}" else nombreCapitulo(c),
                                        LectorType.Section.copy(fontSize = 17.sp), Modifier.weight(1f, fill = false), maxLines = 2,
                                        color = if (leido) LectorColors.Muted else LectorColors.Text,
                                    )
                                }
                                val sub = listOfNotNull(
                                    if (paginaGuardada > 0) "Pág: ${paginaGuardada + 1}" else null,
                                    fechaTexto(c.fecha).takeIf { it.isNotEmpty() }, c.scanlator,
                                ).joinToString(" · ")
                                if (sub.isNotEmpty()) LText(
                                    sub, LectorType.Label, maxLines = 1,
                                    color = if (paginaGuardada > 0) acento else LectorColors.Muted,
                                )
                            }
                            Spacer(Modifier.width(6.dp))
                            if (!enSeleccion) ConAcento(acento) {
                                BotonDescarga(estado, onClick = {
                                    when (estado) {
                                        is EstadoDescarga.Libre -> Descargas.encolar(ctx, f, manga.url, tituloFicha, listOf(c))
                                        is EstadoDescarga.Descargando -> Descargas.cancelar(ctx, clave)
                                        is EstadoDescarga.Listo -> quitarDescarga = c
                                    }
                                })
                            }
                        }
                    }
                }
            }
            item {
                Box(Modifier.fillMaxWidth().padding(24.dp), Alignment.Center) {
                    when {
                        cargando -> if (fi == null) BonesCapitulos() else CircularProgressIndicator(Modifier.size(28.dp))
                        error != null -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            LText(error!!, LectorType.Label, color = LectorColors.Error)
                            Spacer(Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                PillButton("Reintentar", Icons.Rounded.Refresh, { recarga++ })
                                PillButton("Abrir en WebView", Icons.Rounded.ArrowForward, abrirNavegador)
                            }
                        }
                    }
                }
            }
        }

        if (enSeleccion) {
            val elegidos = fi?.capitulos?.filter { it.url in seleccion }.orEmpty()
            Box(Modifier.align(Alignment.TopCenter).fillMaxWidth().background(LectorColors.Bg).statusBarsPadding()) {
                CabeceraSeleccion(
                    seleccion.size, onCerrar = { seleccion = emptySet() },
                    onTodo = { seleccion = visibles.map { it.value.url }.toSet() },
                    onInvertir = { seleccion = visibles.map { it.value.url }.toSet() - seleccion },
                )
            }
            BarraAcciones(
                visible = true, modifier = Modifier.align(Alignment.BottomCenter),
                acciones = buildList {
                    add(AccionBarra(Icons.Rounded.Check, "Leído") { ext.marcarLeidos(f.id, seleccion); progreso(); seleccion = emptySet() })
                    add(AccionBarra(Iconos.OjoTachado, "No leído") { ext.desmarcarLeidos(f.id, seleccion); progreso(); seleccion = emptySet() })
                    if (elegidos.size == 1) add(AccionBarra(Icons.Rounded.KeyboardArrowDown, "Anteriores leídos") {
                        val caps = fi?.capitulos.orEmpty()
                        val idx = caps.indexOfFirst { it.url == elegidos[0].url }
                        if (idx >= 0) ext.marcarLeidos(f.id, caps.drop(idx + 1).map { it.url })
                        progreso(); seleccion = emptySet()
                    })
                    val todosMarcados = elegidos.isNotEmpty() && elegidos.all { ext.esMarcado(f.id, it.url) }
                    add(AccionBarra(if (todosMarcados) Iconos.MarcaBorde else Iconos.Marca, if (todosMarcados) "Desmarcar" else "Marcar") {
                        ext.marcarVarios(f.id, seleccion, !todosMarcados); seleccion = emptySet()
                    })
                    add(AccionBarra(Iconos.Descargar, "Descargar") {
                        Descargas.encolar(ctx, f, manga.url, tituloFicha, elegidos.asReversed()); seleccion = emptySet()
                    })
                    if (elegidos.any { almacen.estaDescargado(f.id, it.url) }) add(AccionBarra(Icons.Rounded.Delete, "Borrar descarga") {
                        elegidos.forEach { if (almacen.estaDescargado(f.id, it.url)) almacen.borrarDescarga(f.id, it.url) }
                        Descargas.version.value++
                        seleccion = emptySet()
                    })
                },
            )
        } else {
            BarraSuperior(
                scrollPx, tituloFicha, onBack, fondo = fondoAnim,
                trailing = {
                    if (fi != null && fi.capitulos.isNotEmpty()) {
                        // Al bajar, el "reanudar" de la cabecera sale de pantalla: aparece aquí.
                        Box(
                            Modifier.size(44.dp)
                                .graphicsLayer { alpha = ((scrollPx() - 560f) / 160f).coerceIn(0f, 1f) }
                                .pressable { if (scrollPx() > 560f) onLeer(fi.capitulos, sig, paginaSig) }
                                .clip(CircleShape).background(acento),
                            Alignment.Center,
                        ) { Icon(Icons.Rounded.PlayArrow, if (hayLeidos) "Reanudar" else "Leer", tint = TintaBtn) }
                        Spacer(Modifier.width(10.dp))
                    }
                    Box(
                        Modifier.size(44.dp).pressable(abrirNavegador).clip(CircleShape).background(LectorColors.SobreImagen),
                        Alignment.Center,
                    ) { Icon(Icons.Rounded.ArrowForward, "Abrir en el navegador", tint = LectorColors.Text) }
                },
            )
        }

        // WebView del sitio, encima de la ficha.
        webUrl?.let { u ->
            Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { } }) {
                WebViewScreen(u, tituloFicha) { webUrl = null; recarga++ }
            }
        }

        // Sinopsis completa.
        if (descExpandida) {
            AlertDialog(
                onDismissRequest = { descExpandida = false },
                title = { Text("Sinopsis") },
                text = { Column(Modifier.verticalScroll(rememberScrollState())) { Text(descripcion.orEmpty()) } },
                confirmButton = { TextButton(onClick = { descExpandida = false }) { Text("Cerrar") } },
            )
        }

        // Asignar categorías (captura de Komikku): casillas + Editar / Cancelar / Aceptar.
        if (dialogoCats) {
            val cats = remember { almacen.categorias() }
            val comicActual = enBiblioteca
            DialogoAsignarCategorias(
                categorias = cats,
                seleccionInicial = comicActual?.let { almacen.asignaciones()[it.id] }.orEmpty(),
                alEditar = { dialogoCats = false; editandoCats = true },
                alCancelar = { dialogoCats = false },
                alAceptar = { sel ->
                    comicActual?.let { almacen.asignarCategorias(it.id, sel) }
                    dialogoCats = false
                    alRecargar()
                },
            )
        }

        // «Editar» abre la pantalla de editar categorías encima de la ficha; al volver reaparece el diálogo.
        if (editandoCats) {
            BackHandler { editandoCats = false; dialogoCats = true }
            Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { } }) {
                EditarCategoriasScreen(almacen) { editandoCats = false; dialogoCats = true }
            }
        }

        // Migrar a otra fuente: pantalla encima de la ficha.
        val comicMigrar = enBiblioteca
        if (migrando && comicMigrar != null) {
            BackHandler { migrando = false }
            Box(Modifier.fillMaxSize().background(LectorColors.Bg).pointerInput(Unit) { detectTapGestures { } }) {
                MigrarScreen(
                    ext, almacen, comicMigrar, f, fi?.capitulos,
                    alHecho = { d -> migrando = false; alRecargar(); Avisos.mostrar("Migrado a ${d.nombre}"); onBack() },
                    onBack = { migrando = false },
                )
            }
        }

        quitarDescarga?.let { c ->
            AlertDialog(
                onDismissRequest = { quitarDescarga = null },
                title = { Text("¿Quitar la descarga?") },
                text = { Text("Se borrará el archivo descargado de «${nombreCapitulo(c)}». Podrás seguir leyéndolo online.") },
                confirmButton = {
                    TextButton(onClick = {
                        almacen.borrarDescarga(f.id, c.url)
                        Descargas.version.value++
                        quitarDescarga = null
                        alRecargar()
                    }) { Text("Quitar") }
                },
                dismissButton = { TextButton(onClick = { quitarDescarga = null }) { Text("Cancelar") } },
            )
        }
    }

    // ---- Hoja de los capítulos: Filtro / Ordenar / Mostrar ----
    if (hojaCaps) {
        HojaLector({ hojaCaps = false }) {
            PestanasPildora(listOf("Filtro", "Ordenar", "Mostrar"), pestanaHoja, { pestanaHoja = it })
            Column(Modifier.fillMaxWidth().padding(bottom = 28.dp)) {
                when (pestanaHoja) {
                    0 -> {
                        FilaTri("No leídos", ext.capNoLeidos) { ext.capNoLeidos = it }
                        FilaTri("Descargados", ext.capDescargados) { ext.capDescargados = it }
                        FilaTri("Marcados", ext.capMarcados) { ext.capMarcados = it }
                    }
                    1 -> (0..2).forEach { o ->
                        FilaOrden(nombreOrdenCap(o), ext.capOrden == o, !ext.capDescendente) {
                            if (ext.capOrden == o) ext.capDescendente = !ext.capDescendente else ext.capOrden = o
                        }
                    }
                    else -> {
                        FilaRadio("Nombre que da la fuente", !ext.capVerNumero) { ext.capVerNumero = false }
                        FilaRadio("Número de capítulo", ext.capVerNumero) { ext.capVerNumero = true }
                    }
                }
            }
        }
    }

    if (seguimiento && comic != null) DialogoSeguimientoComic(comic, almacen) { seguimiento = false }

    if (editarInfo && comic != null) DialogoEditarInfo(
        comic = comic, almacen = almacen,
        alRestablecer = { almacen.actualizarComic(comic.id) { it.copy(editado = false, portadaPropia = false) }; recarga++ },
        alCerrar = { editarInfo = false; alRecargar() },
    )

    if (verPortada) VisorPortada(
        url = fi?.detalle?.portadaUrl ?: manga.portadaUrl, cabeceras = motor.cabeceras(f), titulo = tituloFicha,
        comic = comic, almacen = almacen, onCerrar = { verPortada = false },
    )
}

/** «Editar información» (Komikku): título, autor, descripción, géneros, estado y portada propia. */
@Composable
private fun DialogoEditarInfo(comic: Comic, almacen: Almacen, alRestablecer: () -> Unit, alCerrar: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var titulo by remember { mutableStateOf(comic.titulo) }
    var autor by remember { mutableStateOf(comic.autor.orEmpty()) }
    var descripcion by remember { mutableStateOf(comic.descripcion.orEmpty()) }
    var generos by remember { mutableStateOf(comic.generos.joinToString(", ")) }
    var estado by remember { mutableIntStateOf(comic.estado) }
    val elegirImagen = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching { ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()?.let { almacen.cambiarPortada(comic.id, it) } == true
            }
            Avisos.mostrar(if (ok) "Portada cambiada" else "No se pudo usar esa imagen")
        }
    }
    AlertDialog(
        onDismissRequest = alCerrar,
        title = { Text("Editar información") },
        text = {
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(titulo, { titulo = it }, label = { Text("Título") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(autor, { autor = it }, label = { Text("Autor") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(generos, { generos = it }, label = { Text("Géneros (separados por comas)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(descripcion, { descripcion = it }, label = { Text("Descripción") }, minLines = 3, modifier = Modifier.fillMaxWidth())
                Text("Estado", style = MaterialTheme.typography.labelLarge)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(0, 1, 2, 4, 5, 6).forEach { e -> ChipSel(if (e == 0) "Desconocido" else estadoTexto(e), estado == e) { estado = e } }
                }
                TextButton(onClick = { elegirImagen.launch("image/*") }) { Text("Cambiar la portada…") }
                if (comic.editado || comic.portadaPropia) TextButton(onClick = { alRestablecer(); alCerrar() }) { Text("Volver a los datos de la fuente") }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                almacen.editarInfo(comic.id, titulo, autor, descripcion, generos.split(',').map { it.trim() }.filter { it.isNotEmpty() }, estado)
                alCerrar()
            }) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = alCerrar) { Text("Cancelar") } },
    )
}

/** La portada a pantalla completa, con compartir, guardar y (si está en la biblioteca) cambiarla. */
@Composable
private fun VisorPortada(
    url: String?, cabeceras: Map<String, String>, titulo: String, comic: Comic?, almacen: Almacen, onCerrar: () -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val local = comic?.takeIf { it.portadaPropia }?.let { rememberPortada(almacen, it.id) }
    val elegirImagen = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null && comic != null) scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching { ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()?.let { almacen.cambiarPortada(comic.id, it) } == true
            }
            Avisos.mostrar(if (ok) "Portada cambiada" else "No se pudo usar esa imagen")
        }
    }
    suspend fun bytes(): ByteArray? =
        if (comic?.portadaPropia == true) withContext(Dispatchers.IO) { runCatching { almacen.portadaDe(comic.id).readBytes() }.getOrNull() }
        else url?.let { Imagenes.bytes(it, cabeceras) }
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onCerrar,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(Modifier.fillMaxSize().background(Color(0xF0000000)).pointerInput(Unit) { detectTapGestures { onCerrar() } }) {
            if (local != null) Image(local, null, Modifier.fillMaxSize().padding(24.dp), contentScale = ContentScale.Fit)
            else ImagenRemota(url, Modifier.fillMaxSize().padding(24.dp), cabeceras, escala = ContentScale.Fit, maxLado = 1400)
            Row(
                Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(24.dp).clip(CircleShape)
                    .background(Color(0xCC1A1A1C)).padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                TextButton(onClick = { scope.launch { bytes()?.let { compartirImagen(ctx, it, titulo) } ?: Avisos.mostrar("No se pudo leer la portada") } }) { Text("Compartir", color = Color.White) }
                TextButton(onClick = { scope.launch { bytes()?.let { Avisos.mostrar(guardarImagen(ctx, it, titulo, 0)) } ?: Avisos.mostrar("No se pudo leer la portada") } }) { Text("Guardar", color = Color.White) }
                if (comic != null) TextButton(onClick = { elegirImagen.launch("image/*") }) { Text("Cambiar", color = Color.White) }
            }
        }
    }
}


private val ClaroBtn = Color(0x9EFFFFFF)
private val TintaBtn = Color(0xFF0D0D18)
private val ColorMarca = Color(0xFF2DBE6C)

/** Cambia el color de acento (MaterialTheme.primary) de lo que lleva dentro: botones de descarga, chips... */
@Composable
private fun ConAcento(acento: Color, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = MaterialTheme.colorScheme.copy(primary = acento), content = content)
}

/** Tarjeta de vidrio: relleno translúcido y borde fino, como las de "Latest release / About". */
@Composable
private fun TarjetaVidrio(modifier: Modifier, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val forma = SuaveShape(32.dp)
    Column(
        modifier.press3D(5f, 0.98f).then(if (onClick != null) Modifier.pressable(onClick) else Modifier)
            .clip(forma).background(LectorColors.Text.copy(alpha = 0.06f))
            .border(1.5.dp, LectorColors.Text.copy(alpha = 0.16f), forma),
        content = content,
    )
}

@Composable
private fun CirculoClaro(icono: ImageVector, desc: String, fondo: Color, tam: androidx.compose.ui.unit.Dp = 56.dp, onClick: () -> Unit) {
    Box(Modifier.size(tam).pressable(onClick).clip(CircleShape).background(fondo), Alignment.Center) {
        Icon(icono, desc, tint = TintaBtn, modifier = Modifier.size(tam * 0.5f))
    }
}

@Composable
private fun PildoraClara(texto: String, icono: ImageVector, onClick: () -> Unit) {
    Row(
        Modifier.height(56.dp).pressable(onClick).clip(CircleShape).background(ClaroBtn).padding(horizontal = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icono, null, tint = TintaBtn, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(8.dp))
        LText(texto, LectorType.Section.copy(fontSize = 17.sp), color = TintaBtn, maxLines = 1)
    }
}

/**
 * Cabecera de la ficha, como la página de artista de musicfy: portada a pantalla completa que se funde con el
 * fondo, avatar redondo, título enorme y fila de botones. La portada aparece con un fundido y hace parallax.
 */
@Composable
private fun CabeceraFicha(
    portada: String?, cabeceras: Map<String, String>, comicLocal: com.example.lector.Comic?, almacen: Almacen,
    titulo: String, lineas: List<String>,
    acento: Color, fondo: Color, scroll: () -> Float, portadaLista: Boolean,
    onCargada: (ImageBitmap) -> Unit,
    enBiblioteca: Boolean, textoPildora: String,
    onLeer: () -> Unit, onCorazon: () -> Unit, onPildora: () -> Unit, onPortada: () -> Unit = {},
) {
    var entro by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entro = true }
    val t by animateFloatAsState(if (entro) 1f else 0f, tween(600), label = "entradaFicha")
    val aparece by animateFloatAsState(if (portadaLista) 1f else 0f, tween(700), label = "portadaFicha")
    // clipToBounds: con el parallax la portada se corría hacia abajo y asomaba por debajo de la cabecera.
    Box(Modifier.fillMaxWidth().height(560.dp).clipToBounds()) {
        ImagenRemota(
            portada,
            Modifier.fillMaxSize().graphicsLayer {
                translationY = scroll() * 0.5f
                alpha = aparece * (1f - (scroll() / 1100f).coerceIn(0f, 1f) * 0.8f)
            },
            cabeceras, onCargada,
        )
        // Oscurece un poco arriba (barra de estado) y funde la portada con el fondo por abajo.
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color(0x66000000), 0.22f to Color.Transparent)))
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(0.38f to Color.Transparent, 0.80f to fondo.copy(alpha = 0.88f), 1f to fondo)
            )
        )
        Column(
            Modifier.align(Alignment.BottomStart).padding(start = 24.dp, end = 24.dp, bottom = 20.dp)
                .graphicsLayer { alpha = t; translationY = (1f - t) * 60f },
        ) {
            val avatar = Modifier.size(96.dp).pressable(onPortada).clip(CircleShape)
            if (comicLocal != null) ComicCover(comicLocal, almacen, avatar, CircleShape)
            else ImagenRemota(portada, avatar, cabeceras)
            Spacer(Modifier.height(14.dp))
            LText(
                titulo, LectorType.Display.copy(fontSize = 44.sp, lineHeight = 46.sp, letterSpacing = (-1.5).sp),
                maxLines = 3,
            )
            Spacer(Modifier.height(6.dp))
            lineas.chunked(2).forEach { par ->
                LText(par.joinToString(" · "), LectorType.Body, color = LectorColors.Text.copy(alpha = 0.8f), maxLines = 1)
            }
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                CirculoClaro(Icons.Rounded.PlayArrow, "Leer", ClaroBtn, onClick = onLeer)
                CirculoClaro(
                    if (enBiblioteca) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    if (enBiblioteca) "Quitar de la biblioteca" else "Añadir a la biblioteca",
                    if (enBiblioteca) acento else ClaroBtn, onClick = onCorazon,
                )
                PildoraClara(textoPildora, if (enBiblioteca) Icons.Rounded.List else Icons.Rounded.Add, onPildora)
            }
        }
    }
}

// ───────────────────── Leer online ─────────────────────

private fun urlCapitulo(f: FuenteUI, c: CapituloFuente): String? =
    if (c.url.startsWith("http")) c.url
    else if (f.baseUrl.isBlank()) null
    else f.baseUrl.trimEnd('/') + "/" + c.url.trimStart('/')

fun nombreCapitulo(c: CapituloFuente): String =
    c.nombre.trim().trimEnd(':').trim().ifBlank { "Capítulo ${numeroTexto(c.numero)}" }

/**
 * Lectura online de un manga de una fuente con el visor común. Si el capítulo está descargado se lee del archivo.
 * Anota historial, página por la que vas, capítulos leídos, progreso de la biblioteca y seguimiento de AniList;
 * borra los capítulos leídos (si se pidió) y descarga por adelantado los siguientes.
 * [capitulos] vienen como los da la fuente (lo más nuevo primero); el visor los recibe en orden de lectura.
 */
@Composable
fun LeerOnlineScreen(
    ext: ExtManager,
    fuenteId: Long,
    titulo: String,
    capitulos: List<CapituloFuente>,
    indiceInicial: Int,
    almacen: Almacen? = null,
    manga: MangaFuente? = null,
    paginaInicial: Int = 0,
    onBack: () -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val f = ext.fuentes.firstOrNull { it.id == fuenteId }
    val motor = Motor.actual
    if (f == null || motor == null || capitulos.isEmpty()) {
        Box(Modifier.fillMaxSize().background(Color.Black), Alignment.Center) {
            LText("No se puede abrir el capítulo.", LectorType.Body, color = Color(0xFF9A9AA0))
        }
        return
    }
    val alm = almacen ?: remember { Almacen.obtener(ctx) }
    val enOrden = remember(capitulos) { capitulos.asReversed() }
    val caps = remember(enOrden, f.id) {
        enOrden.map { c -> CapVisor("${f.id}|${c.url}", nombreCapitulo(c), OrigenFuente(alm, motor, f, c)) }
    }
    val inicio = (capitulos.lastIndex - indiceInicial).coerceIn(0, caps.lastIndex)
    val comic = remember(manga, alm.version) { manga?.let { alm.comicEnLinea(f.id, it.url) } }
    // Capítulos terminados en esta sesión: si está activado «borrar al leer», se borran al salir o al pasar al siguiente.
    val terminados = remember { mutableSetOf<String>() }
    fun borrarLeidos(salvo: String?) {
        if (!alm.borrarAlLeer) return
        terminados.filter { it != salvo }.forEach { url ->
            if (alm.estaDescargado(f.id, url)) alm.borrarDescarga(f.id, url)
            terminados.remove(url)
        }
    }
    DisposableEffect(Unit) { onDispose { borrarLeidos(null) } }
    val usarComoPortada: (suspend (ByteArray) -> Unit)? = comic?.let { c ->
        { b -> withContext(Dispatchers.IO) { alm.cambiarPortada(c.id, b) } }
    }

    Visor(
        almacen = alm,
        titulo = titulo.ifBlank { manga?.titulo ?: f.nombre },
        capitulos = caps,
        inicial = inicio,
        paginaInicial = paginaInicial,
        claveSerie = comic?.id ?: manga?.let { "${f.id}|${it.url}" },
        alPagina = { i, pagina, total ->
            val c = enOrden[i]
            borrarLeidos(c.url)
            if (pagina < total - 1) ext.guardarPagina(f.id, c.url, pagina)
            if (manga != null) {
                alm.registrarHistorialLinea(
                    EntradaHistorial(
                        fuenteId = f.id, urlManga = manga.url, titulo = titulo.ifBlank { manga.titulo },
                        portadaUrl = manga.portadaUrl, capUrl = c.url, capNombre = nombreCapitulo(c),
                        pagina = pagina, total = total, leido = System.currentTimeMillis(),
                    ),
                )
            }
            // Descargar por adelantado los capítulos siguientes (Ajustes → Descargas).
            val adelante = alm.descargarAdelante
            if (adelante > 0 && comic != null) {
                val siguientes = enOrden.drop(i + 1).take(adelante).filter { !alm.estaDescargado(f.id, it.url) }
                if (siguientes.isNotEmpty()) Descargas.encolar(ctx, f, manga?.url ?: comic.urlManga.orEmpty(), comic.titulo, siguientes, avisar = false)
            }
        },
        alTerminarCapitulo = { i ->
            val c = enOrden[i]
            ext.marcarLeido(f.id, c.url)
            terminados += c.url
            if (manga != null) {
                alm.actualizarProgresoEnLinea(f.id, manga.url, capitulos.count { ext.esLeido(f.id, it.url) }, capitulos.size)
            }
            // Seguimiento (AniList): sube el número del capítulo terminado.
            val cid = comic?.id
            if (cid != null && alm.seguimientoDe(cid) != null) {
                val numero = if (c.numero > 0) c.numero.toInt() else capitulos.count { ext.esLeido(f.id, it.url) }
                scope.launch { withContext(Dispatchers.IO) { alm.avanzarSeguimiento(cid, numero) } }
            }
        },
        acciones = AccionesVisor(
            urlWeb = { urlCapitulo(f, enOrden[it]) },
            estaMarcado = { ext.esMarcado(f.id, enOrden[it].url) },
            alternarMarca = { ext.alternarMarcado(f.id, enOrden[it].url) },
            usarComoPortada = usarComoPortada,
            esLeido = { ext.esLeido(f.id, enOrden[it].url) },
        ),
        onBack = onBack,
    )
}
