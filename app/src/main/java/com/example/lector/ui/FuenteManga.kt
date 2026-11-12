package com.example.lector.ui

import androidx.compose.foundation.combinedClickable
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
import com.example.lector.Comic
import com.example.lector.CacheFichas
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
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.luminance
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
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
import com.example.lector.ui.mochy.ChapterSwipeAction
import com.example.lector.ui.mochy.ChapterSwipeBox
import com.example.lector.ui.mochy.EstadoCapituloSwipe
import com.example.lector.ui.mochy.GlassState
import com.example.lector.ui.mochy.glassRoot
import com.example.lector.ui.mochy.rememberRevealSeenState
import com.example.lector.ui.mochy.revealOnAppear
import com.example.lector.ui.mochy.detail.*
import androidx.compose.ui.graphics.RectangleShape
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

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
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
    // Abrir otro manga (de otra fuente) en esta misma ficha: lo usa la pantalla «Fuente no disponible».
    alAbrirOtro: (FuenteUI, MangaFuente) -> Unit = { _, _ -> },
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
    var refrescando by remember { mutableStateOf(false) }
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
    var abriendoRec by remember { mutableStateOf<Int?>(null) }
    var otrosSeguimientos by remember { mutableStateOf(false) }
    var combinando by remember { mutableStateOf(false) }
    // Menú ⋮ de la ficha (como Komikku) y lo que abre.
    var menuFicha by remember { mutableStateOf(false) }
    var editandoNotas by remember { mutableStateOf(false) }
    var limpiar by remember { mutableStateOf(false) }
    var ajustesFuente by remember { mutableStateOf(false) }
    val clNotas = "${fuenteId}|${manga.url}"
    var notas by remember(clNotas) { mutableStateOf(ctx.getSharedPreferences("notas", android.content.Context.MODE_PRIVATE).getString(clNotas, "").orEmpty()) }
    var parecidos by remember { mutableStateOf<List<MangaFuente>?>(null) }
    // Vista previa de páginas (como TachiyomiSY/Komikku): miniaturas del primer capítulo, al pedirlas.
    var vistaPrevia by remember { mutableStateOf<List<androidx.compose.ui.graphics.ImageBitmap?>?>(null) }
    var cargandoVista by remember { mutableStateOf(false) }

    LaunchedEffect(fuenteId, manga.url, recarga) {
        if (f == null || motor == null) return@LaunchedEffect
        // Como Mihon: la primera vez se trae de la fuente y queda guardada; después se abre lo guardado al instante y
        // solo se vuelve a pedir al deslizar hacia abajo (o con la actualización de la biblioteca).
        var cargada: FichaManga? = if (recarga == 0) withContext(Dispatchers.IO) {
            CacheFichas.leer(f.id, manga.url)?.also { g ->
                // Lo leído/marcado antes de ir por manga: se pasa a los capítulos de la ficha guardada (una vez).
                if (ext.faltaMigrar(f.id, manga.url)) runCatching {
                    ext.migrarMangaSiHace(f.id, manga.url, g.capitulos.map { it.url } + almacen.capitulosConocidos(f.id, manga.url))
                }
            }
        } else null
        if (cargada != null) {
            ficha = cargada; error = null; cargando = false
            android.util.Log.i("LectorPrueba", "ficha «${manga.titulo}» desde el móvil (${cargada.capitulos.size} caps)")
        } else {
            android.util.Log.i("LectorPrueba", "ficha «${manga.titulo}» desde la fuente")
            val antes = ficha
            if (antes == null) cargando = true
            error = null
            val r = runCatching { motor.ficha(f, manga) }
            r.onSuccess { nueva ->
                ficha = nueva; cargada = nueva
                if (antes != null) {
                    val viejas = antes.capitulos.mapTo(HashSet()) { it.url }
                    val nuevos = nueva.capitulos.count { it.url !in viejas }
                    Avisos.mostrar(if (nuevos > 0) "$nuevos capítulos nuevos" else "Ficha actualizada: sin capítulos nuevos")
                }
            }.onFailure {
                // Salir de la ficha mientras carga cancela la petición: no es un error que haya que mostrar.
                if (it is kotlinx.coroutines.CancellationException) throw it
                val m = it.message ?: "No se pudo cargar la ficha"
                if (antes == null) error = m else Avisos.mostrar("No se pudo actualizar: $m")
            }
            cargando = false
        }
        refrescando = false
        val cargada2 = cargada ?: return@LaunchedEffect
        val cargadaF: FichaManga = cargada2
        // Si está en la biblioteca: progreso, datos (autor, estado, géneros…) y capítulos nuevos.
        if (almacen.comicEnLinea(f.id, manga.url) != null) {
            almacen.actualizarProgresoEnLinea(
                f.id, manga.url, cargadaF.capitulos.count { ext.esLeido(f.id, manga.url, it.url) }, cargadaF.capitulos.size,
            )
            almacen.actualizarInfoEnLinea(
                f.id, manga.url, cargadaF.detalle.autor, cargadaF.detalle.estado, cargadaF.detalle.generos,
                cargadaF.detalle.descripcion, cargadaF.detalle.portadaUrl, cargadaF.capitulos.maxOfOrNull { it.fecha } ?: 0L,
            )
            almacen.registrarCapitulos(
                f.id, manga.url, cargadaF.detalle.titulo.ifBlank { manga.titulo },
                cargadaF.detalle.portadaUrl ?: manga.portadaUrl, cargadaF.capitulos,
            )
        }
        // Venimos del historial: abre directamente el capítulo donde lo dejaste.
        if (reanudarCap != null) {
            alConsumirReanudar()
            val i = cargadaF.capitulos.indexOfFirst { it.url == reanudarCap.first }
            if (i >= 0) onLeer(cargadaF.capitulos, i, reanudarCap.second)
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

    // Parecidos de la fuente: los relacionados de la extensión o, si no da, una búsqueda por su primer género.
    LaunchedEffect(fi != null) {
        if (fi == null || parecidos != null || f == null || motor == null) return@LaunchedEffect
        val rel = runCatching { motor.relacionados(f, manga) }.getOrDefault(emptyList())
        parecidos = rel.ifEmpty {
            val g = fi.detalle.generos.firstOrNull()
            if (g == null) emptyList()
            else runCatching { motor.buscar(f, g, 1).mangas.filter { it.url != manga.url }.distinctBy { it.url }.take(15) }.getOrDefault(emptyList())
        }
    }

    // Recomendaciones (AniList) del título.
    LaunchedEffect(tituloFicha, fi != null) {
        if (fi != null && recomendaciones == null) {
            recomendaciones = withContext(Dispatchers.IO) {
                runCatching { Anilist.recomendaciones(tituloFicha).distinctBy { it.id } }
                    .onSuccess { android.util.Log.i("LectorPrueba", "recomendaciones de «$tituloFicha»: ${it.size}") }
                    .onFailure { android.util.Log.w("LectorPrueba", "recomendaciones de «$tituloFicha»: error ${it.message}") }
                    .getOrDefault(emptyList())
            }
        }
    }

    // Estética de la ficha: el fondo y el acento toman el tono de la portada (si "Tema según la portada" está activo).
    var paleta by remember { mutableStateOf<TemaPortada?>(null) }
    val conTema = almacen.colorPortada
    val acento = (if (conTema) paleta?.acento else null) ?: MaterialTheme.colorScheme.primary
    val fondoBase = LectorColors.Bg
    val fondoEstado = animateColorAsState(
        (if (conTema && TemaActual.oscuro) paleta?.fondo else null) ?: fondoBase, tween(800), label = "fondoFicha",
    )

    // Corazón "En biblioteca": añade (descargando la portada) o quita el manga.
    fun anadirABiblioteca() {
        if (f == null || motor == null || guardando) return
        guardando = true
        scope.launch {
            val titulo = fi?.detalle?.titulo ?: manga.titulo
            val urlPortada = fi?.detalle?.portadaUrl ?: manga.portadaUrl
            val portada = withContext(Dispatchers.IO) {
                urlPortada?.let {
                    runCatching { Imagenes.cargar(it, motor.cabeceras(f), 400) }.getOrNull()?.asAndroidBitmap()
                }
            }
            val leidos = fi?.capitulos?.count { ext.esLeido(f.id, manga.url, it.url) } ?: 0
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
            Tacto.confirmar()
            Avisos.mostrar("Añadido a la biblioteca")
            // Como en Komikku: al añadir, pregunta las categorías (si hay).
            if (almacen.categorias().isNotEmpty()) dialogoCats = true
        }
    }

    fun alternarBiblioteca() {
        if (f == null || motor == null || guardando) return
        if (enBiblioteca != null) {
            almacen.quitarEnLinea(f.id, manga.url)
            alRecargar()
            Avisos.mostrar("Quitado de la biblioteca")
            return
        }
        // ¿Ya está en la biblioteca desde otra fuente? Entonces se pregunta antes (ver, migrar o añadir igualmente).
        Duplicados.comprobar(almacen, f, manga, fi?.detalle?.titulo ?: manga.titulo) { anadirABiblioteca() }
    }

    if (f == null || motor == null) {
        // En vez de un aviso sin salida: por qué falta, cómo arreglarlo y el mismo título en tus otras fuentes.
        FuenteNoDisponible(ext, almacen, fuenteId, manga, enBiblioteca, alRecargar, onBack, alAbrirOtro)
        return
    }

    BackHandler(enabled = seleccion.isNotEmpty()) { seleccion = emptySet() }

    // Recomendación tocada: se busca en ESTA fuente y se abre directamente la que coincide (encima de esta ficha,
    // así «atrás» vuelve aquí). Si la fuente no la tiene, un aviso ofrece buscarla en todas.
    fun abrirRecomendacion(r: ResultadoAnilist) {
        if (abriendoRec != null) return
        abriendoRec = r.id
        scope.launch {
            val hallado = runCatching { buscarEnFuente(motor, f, r.titulos.ifEmpty { listOf(r.titulo) }) }.getOrNull()
            abriendoRec = null
            android.util.Log.i("LectorPrueba", "recomendación «${r.titulo}» en ${f.nombre}: ${hallado?.titulo ?: "no está"}")
            if (hallado != null) alAbrirOtro(f, hallado)
            else Avisos.mostrar("«${r.titulo}» no está en ${f.nombre}", "Buscar en todas") { alBuscarGlobal(r.titulo) }
        }
    }
    LaunchedEffect(manga.url) { android.util.Log.i("LectorPrueba", "ficha abierta: ${f.nombre} · ${manga.titulo}") }
    // Solo en la prueba del emulador: abre la primera recomendación en cuanto llegan.
    LaunchedEffect(recomendaciones) {
        val recs = recomendaciones
        if (PruebaAuto.abrirRecomendacion && !recs.isNullOrEmpty()) {
            PruebaAuto.abrirRecomendacion = false
            kotlinx.coroutines.delay(1500)
            // La primera que la fuente tenga (antes solo se probaba la primera y, si no estaba, la prueba no decía nada).
            for (r in recs.take(8)) {
                val hallado = runCatching { buscarEnFuente(motor, f, r.titulos.ifEmpty { listOf(r.titulo) }) }.getOrNull()
                android.util.Log.i("LectorPrueba", "recomendación «${r.titulo}» en ${f.nombre}: ${hallado?.titulo ?: "no está"}")
                if (hallado != null) { alAbrirOtro(f, hallado); break }
            }
        }
    }

    // ---- Lista de capítulos con filtros y orden ----
    val leidosAhora = ext.leidos
    val marcadosAhora = ext.marcados
    val versionDesc = Descargas.version.value + almacen.version
    val ocultos = ext.gruposOcultos(f.id, manga.url)
    // Filtro y orden de capítulos propios de este manga (no afecta a los demás).
    var filtroCaps by remember(f?.id, manga.url) { mutableStateOf(f?.let { ext.filtroCapsDe(it.id, manga.url) } ?: ext.filtroCapsPredeterminado()) }
    fun cambiarFiltroCaps(nuevo: com.example.lector.ext.ExtManager.FiltroCaps) { filtroCaps = nuevo; f?.let { ext.guardarFiltroCaps(it.id, manga.url, nuevo) } }
    val visibles = remember(fi, leidosAhora, marcadosAhora, versionDesc, filtroCaps, ocultos) {
        val base = fi?.capitulos?.withIndex()?.toList().orEmpty()
        val filtrados = base.filter { iv ->
            (iv.value.scanlator == null || iv.value.scanlator !in ocultos) &&
            filtroCaps.noLeidos.deja(!ext.esLeido(f.id, manga.url, iv.value.url)) &&
                filtroCaps.descargados.deja(almacen.estaDescargado(f.id, manga.url, iv.value.url)) &&
                filtroCaps.marcados.deja(ext.esMarcado(f.id, manga.url, iv.value.url))
        }
        val desc = filtroCaps.descendente
        when (filtroCaps.orden) {
            1 -> if (desc) filtrados.sortedByDescending { it.value.numero } else filtrados.sortedBy { it.value.numero }
            2 -> if (desc) filtrados.sortedByDescending { it.value.fecha } else filtrados.sortedBy { it.value.fecha }
            else -> if (desc) filtrados else filtrados.asReversed()
        }
    }
    val filtrosCap = listOf(filtroCaps.noLeidos, filtroCaps.descargados, filtroCaps.marcados).count { it != com.example.lector.Tri.IGNORAR } + (if (ocultos.isNotEmpty()) 1 else 0)

    // Cuánto se ha desplazado la cabecera (se lee al dibujar, sin recomponer).
    // En tablet la cabecera va en su propia columna: el parallax y la barra siguen a esa lista.
    val dosColumnas = LocalConfiguration.current.screenWidthDp >= 840
    val listaInfo = rememberLazyListState()
    val listaCab = if (dosColumnas) listaInfo else lista
    // Mochy: la banda de la portada (60 % del alto) marca cuándo se pliega la barra; el cristal y las entradas.
    val altoPortada = playlistCoverHeight()
    val plegado = rememberPlaylistCollapse(listaCab, altoPortada)
    val morph: () -> Float = { plegado.morph.value }
    val glass = remember { GlassState() }
    val revelados = rememberRevealSeenState()
    // La mayoría de fuentes listan el capítulo más nuevo primero: el primero a leer es el último sin leer.
    // Al leer se respetan los filtros y el tipo de orden de la lista (como Mihon/Komikku): el lector recibe solo esos
    // capítulos, en orden de lectura (del primero al último), aunque la lista se vea de más nuevo a más viejo.
    // Se pasa con la convención de onLeer: lo más nuevo primero.
    val paraLeer = remember(visibles, filtroCaps) {
        (if (filtroCaps.descendente) visibles else visibles.asReversed()).map { it.value }
    }
    fun leerCap(c: CapituloFuente, pagina: Int) {
        val todos = fi?.capitulos ?: return
        val i = paraLeer.indexOfFirst { it.url == c.url }
        if (i >= 0 && paraLeer.size > 1) onLeer(paraLeer, i, pagina)
        else onLeer(todos, todos.indexOfFirst { it.url == c.url }.coerceAtLeast(0), pagina)
    }
    val sig = remember(fi, leidosAhora) {
        fi?.let { x -> (x.capitulos.lastIndex downTo 0).firstOrNull { !ext.esLeido(f.id, manga.url, x.capitulos[it].url) } ?: 0 } ?: 0
    }
    val paginaSig = fi?.capitulos?.getOrNull(sig)?.let { ext.paginaDe(f.id, manga.url, it.url) } ?: 0
    val hayLeidos = remember(fi, leidosAhora) { fi?.capitulos?.any { ext.esLeido(f.id, manga.url, it.url) } == true }
    val urlWeb = remember(f, manga) { motor.urlManga(f, manga) ?: urlManga(f, manga) }
    val abrirNavegador: () -> Unit = {
        if (urlWeb == null) Avisos.mostrar("Esta fuente no da la dirección web") else webUrl = urlWeb
    }
    fun progreso() {
        val caps = fi?.capitulos ?: return
        almacen.actualizarProgresoEnLinea(f.id, manga.url, caps.count { ext.esLeido(f.id, manga.url, it.url) }, caps.size)
    }
    val enSeleccion = seleccion.isNotEmpty()

    // La portada de la ficha (PlaylistDetailScaffold de Mochy): nítida a sangre detrás de la lista, la copia pequeña
    // para el desenfoque antes de Android 12 y la miniatura que sale del botón atrás al plegar la barra.
    val portadaUrl = fi?.detalle?.portadaUrl ?: manga.portadaUrl
    val cabecerasImg = motor.cabeceras(f)
    val comicLocal = comic?.takeIf { it.portadaPropia }
    val portadaNitida: @Composable (Modifier) -> Unit = { m ->
        if (comicLocal != null) ComicCover(comicLocal, almacen, m, RectangleShape)
        else ImagenRemota(portadaUrl, m, cabecerasImg, maxLado = 900, fuenteId = f.id)
    }
    val portadaSuave: (@Composable (Modifier) -> Unit)? = if (comicLocal != null) null else { m ->
        ImagenRemota(portadaUrl, m, cabecerasImg, maxLado = 160, fuenteId = f.id)
    }
    val portadaMini: @Composable (Modifier) -> Unit = { m ->
        if (comicLocal != null) ComicCover(comicLocal, almacen, m, RectangleShape)
        else ImagenRemota(portadaUrl, m, cabecerasImg, maxLado = 160, fuenteId = f.id)
    }
    // El tono de la portada (si «Tema según la portada» está activo), de la copia pequeña ya en memoria.
    LaunchedEffect(portadaUrl, conTema) {
        val u = portadaUrl ?: return@LaunchedEffect
        Imagenes.cargar(u, cabecerasImg, 160, f.id)?.let { if (conTema) paleta = temaDe(it) }
    }

    Box(Modifier.fillMaxSize().drawBehind { drawRect(fondoEstado.value) }) {
        // Deslizar hacia abajo: vuelve a pedir la ficha y los capítulos a la fuente (lo normal es usar lo guardado).
        androidx.compose.material3.pulltorefresh.PullToRefreshBox(
            isRefreshing = refrescando,
            onRefresh = { Tacto.umbral(); refrescando = true; recarga++ },
            modifier = Modifier.fillMaxSize(),
        ) {
        // Contenido de la ficha: [info] = cabecera y tarjetas; [caps] = lista de capítulos. En tablet (≥ 840 dp) van
        // en dos columnas, como MangaScreenLargeImpl de Komikku; en el móvil, una sola lista.
        val contenido: androidx.compose.foundation.lazy.LazyListScope.(Boolean, Boolean) -> Unit = { info, caps ->
            if (info) item(key = "cabecera", contentType = "cabecera") {
                val capSig = fi?.capitulos?.getOrNull(sig)
                val textoLeer = when {
                    capSig == null -> "Leer"
                    hayLeidos -> numeroTexto(capSig.numero).takeIf { it.isNotEmpty() }?.let { "Continuar cap. $it" } ?: "Continuar"
                    else -> "Empezar a leer"
                }
                // La línea del artista de Mochy = el autor (o la fuente si no hay autor); los datos, debajo.
                val autores = autor?.split(',', ';')?.map { it.trim() }?.filter { it.isNotBlank() }.orEmpty()
                val creadores = autores.ifEmpty { listOf(f.nombre) }.map { CreatorUi(it) }
                val stats = listOfNotNull(
                    estadoPub?.let { estadoTexto(it) },
                    f.nombre.takeIf { autores.isNotEmpty() },
                    fi?.let { "${it.capitulos.size} capítulos" },
                ).joinToString(" · ")
                PlaylistHeader(
                    title = tituloFicha, creators = creadores, stats = stats,
                    playLabel = textoLeer, playEnabled = fi != null && fi.capitulos.isNotEmpty(),
                    onPlayClick = { fi?.capitulos?.getOrNull(sig)?.let { leerCap(it, paginaSig) } },
                    description = descripcion?.takeIf { it.isNotBlank() },
                    onDescriptionLongClick = { descExpandida = true },
                    onCoverClick = { verPortada = true },
                    onTitleClick = { alBuscarGlobal(tituloFicha) },
                    onTitleLongClick = { copiarTexto(ctx, tituloFicha) },
                ) {
                    CircleAction(
                        if (enBiblioteca != null) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        if (enBiblioteca != null) "Quitar de la biblioteca" else "Añadir a la biblioteca",
                        onClick = { alternarBiblioteca() }, tint = if (enBiblioteca != null) acento else null,
                    )
                    if (enBiblioteca != null) {
                        Spacer(Modifier.width(4.dp))
                        CircleAction(Icons.Rounded.List, "Categorías", onClick = { dialogoCats = true })
                    }
                    Spacer(Modifier.width(4.dp))
                    CircleAction(Icons.Rounded.ArrowForward, "Abrir en WebView", onClick = abrirNavegador)
                    if (fi != null && fi.capitulos.isNotEmpty()) {
                        Spacer(Modifier.width(4.dp))
                        // Menú de descargas en lote (como Komikku): siguiente, siguientes N, no leídos o todos.
                        Box {
                            CircleAction(Iconos.Descargar, "Descargar capítulos", onClick = { menuDescarga = true })
                            MenuShiori(expanded = menuDescarga, onDismissRequest = { menuDescarga = false }) {
                                // La lista llega con lo más nuevo primero: "siguientes" = desde el primero sin leer hacia los nuevos.
                                val opciones = listOf(
                                    "Siguiente capítulo" to fi.capitulos.slice((sig downTo maxOf(sig, 0)).toList()),
                                    "Siguientes 5" to fi.capitulos.slice((sig downTo maxOf(sig - 4, 0)).toList()),
                                    "Siguientes 10" to fi.capitulos.slice((sig downTo maxOf(sig - 9, 0)).toList()),
                                    "Siguientes 25" to fi.capitulos.slice((sig downTo maxOf(sig - 24, 0)).toList()),
                                    "No leídos" to fi.capitulos.filter { !ext.esLeido(f.id, manga.url, it.url) }.asReversed(),
                                    "Marcados" to fi.capitulos.filter { ext.esMarcado(f.id, manga.url, it.url) }.asReversed(),
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
                    }
                }
            }
            if (fi != null) {
                if (info) {
                if (generos.isNotEmpty()) {
                    item(key = "generos", contentType = "generos") {
                        Row(
                            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = PlaylistInset, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) { generos.forEach { g -> PildoraGenero(g) { alGenero(g) } } }
                    }
                }
                // Último capítulo: tarjeta ancha con portada pequeña y botón de acento.
                val ultimo = fi.capitulos.firstOrNull()
                if (ultimo != null) item(key = "ultimo", contentType = "ultimo") {
                    val leerUltimo = { leerCap(fi.capitulos[0], ext.paginaDe(f.id, manga.url, fi.capitulos[0].url)) }
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)
                            .revealOnAppear("ultimo", revelados, enabled = !Mov.reducido)
                            .press3D(4f, 0.98f).pressable(leerUltimo)
                            .clip(SuaveShape(22.dp)).background(LectorColors.Card).padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ImagenRemota(
                            fi.detalle.portadaUrl ?: manga.portadaUrl,
                            Modifier.width(39.dp).aspectRatio(2f / 3f).clip(formaPortada(0.6f)), motor.cabeceras(f), fuenteId = f.id,
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("ÚLTIMO CAPÍTULO", style = ReceiptSmallStyle.copy(fontWeight = FontWeight.Bold, fontSize = 9.5.sp), color = acento, maxLines = 1)
                            Spacer(Modifier.height(3.dp))
                            Text(nombreCapitulo(ultimo), style = TrackTitleStyle, color = LectorColors.Text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(
                                listOfNotNull(fechaTexto(ultimo.fecha).takeIf { it.isNotEmpty() }, ultimo.scanlator?.takeIf { it.isNotBlank() })
                                    .joinToString(" · ").ifEmpty { "${fi.capitulos.size} capítulos" },
                                style = TrackSubtitleStyle, color = LectorColors.Text.copy(alpha = 0.55f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Box(Modifier.size(36.dp).clip(CircleShape).background(acento), Alignment.Center) {
                            Icon(Icons.Rounded.PlayArrow, "Leer último capítulo", tint = tintaSobre(acento), modifier = Modifier.size(20.dp))
                        }
                    }
                }
                // Acciones: seguimiento, editar, migrar, compartir y buscar en otras fuentes.
                item {
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = PlaylistInset - 8.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ConAcento(acento) {
                            if (comic != null) {
                                val vinculado = remember(almacen.version) { almacen.seguimientoDe(comic.id) != null }
                                ChipSel(if (vinculado) "AniList ✓" else "Seguimiento", vinculado) { seguimiento = true }
                                ChipSel("MAL · Kitsu · MU", false) { otrosSeguimientos = true }
                                ChipSel("Editar información", editado) { editarInfo = true }
                                ChipSel("Migrar", false) { migrando = true }
                            }
                            if (urlWeb != null) ChipSel("Compartir", false) { compartirTexto(ctx, "$tituloFicha\n$urlWeb") }
                            ChipSel("Buscar en otras fuentes", false) { alBuscarGlobal(tituloFicha) }
                            if (enBiblioteca != null) ChipSel("Combinar", false) { combinando = true }
                        }
                    }
                }
                if (notas.isNotBlank()) item {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp).clip(SuaveShape(14.dp))
                        .background(LectorColors.Card).pressable { editandoNotas = true }.padding(14.dp)) {
                        Text("Notas", style = SectionTitleStyle.copy(fontSize = 13.sp), color = acento)
                        Spacer(Modifier.height(2.dp))
                        Text(notas, style = TrackTitleStyle.copy(fontWeight = FontWeight.Medium), color = LectorColors.Text.copy(alpha = 0.86f))
                    }
                }
                // Entrada fusionada: chips con cada fuente unida (tocar = verla; mantener = separarla).
                item {
                    @Suppress("UNUSED_VARIABLE") val fv = ext.fusionVersion
                    val esta = "${f.id}|${manga.url}"
                    val principal = ext.principalDe(esta) ?: esta
                    val grupo = listOf(principal) + ext.unidas(principal)
                    if (grupo.size > 1) {
                        Column(Modifier.padding(top = 12.dp)) {
                            PlaylistSectionTitle("Fuentes combinadas", Modifier.padding(bottom = 4.dp))
                            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = PlaylistInset - 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                grupo.forEach { k ->
                                    val fid = k.substringBefore('|').toLongOrNull() ?: return@forEach
                                    val url = k.substringAfter('|')
                                    val fu = ext.fuentes.firstOrNull { it.id == fid }
                                    ChipSel(fu?.nombre ?: "Fuente no instalada", k == esta, onLongClick = {
                                        if (k != principal) { ext.separar(principal, k); Avisos.mostrar("Separada: vuelve a la biblioteca") }
                                    }) {
                                        val c = almacen.comicEnLinea(fid, url)
                                        if (k != esta && fu != null) alAbrirOtro(fu, MangaFuente(url, c?.titulo ?: tituloFicha, c?.portadaUrl))
                                    }
                                }
                            }
                        }
                    }
                }
                // Vista previa de las primeras páginas del primer capítulo (se cargan al pedirlas: no gasta datos sin querer).
                if (fi.capitulos.isNotEmpty()) item {
                    Column(Modifier.padding(top = 28.dp)) {
                        PlaylistSectionTitle("Vista previa", Modifier.padding(bottom = 8.dp), trailing = {
                            if (vistaPrevia == null && !cargandoVista) PillButton("Ver páginas", Icons.Rounded.ArrowForward, {
                                cargandoVista = true
                                scope.launch {
                                    val primero = fi.capitulos.last()
                                    val origen = OrigenFuente(almacen, motor, f, CacheFichas.conMemo(f.id, manga.url, primero), manga.url)
                                    vistaPrevia = withContext(Dispatchers.IO) {
                                        val n = runCatching { origen.total() }.getOrDefault(0).coerceAtMost(8)
                                        (0 until n).map { i -> runCatching { miniatura(origen.bytes(i)) }.getOrNull() }
                                    }
                                    cargandoVista = false
                                }
                            })
                        })
                        val vp = vistaPrevia
                        when {
                            cargandoVista -> BonesPortadas()
                            vp != null && vp.isEmpty() -> Text("No se pudieron cargar las páginas.", Modifier.padding(horizontal = PlaylistInset), style = PlaylistStatsStyle.copy(fontSize = 12.sp), color = LectorColors.Muted)
                            vp != null -> LazyRow(contentPadding = PaddingValues(horizontal = PlaylistInset), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                items(vp.size) { i ->
                                    Box(
                                        Modifier.width(92.dp).aspectRatio(2f / 3f).clip(formaPortada(0.75f)).background(LectorColors.CardHigh)
                                            .press3D().pressable { onLeer(fi.capitulos, fi.capitulos.lastIndex, i) },
                                        Alignment.Center,
                                    ) {
                                        vp[i]?.let { androidx.compose.foundation.Image(it, "Página ${i + 1}", Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop) }
                                            ?: LText("${i + 1}", LectorType.Label, color = LectorColors.Muted)
                                    }
                                }
                            }
                        }
                    }
                }
                // Parecidos de la propia fuente: SIEMPRE hay un apartado (lo que dé la extensión o, si no, su mismo género).
                item {
                    val lista = parecidos
                    Column(Modifier.padding(top = 28.dp)) {
                        PlaylistSectionTitle("Parecidos en ${f.nombre}", Modifier.padding(bottom = 8.dp))
                        when {
                            lista == null -> BonesPortadas()
                            lista.isEmpty() -> Text("Esta fuente no da parecidos para este manga.", Modifier.padding(horizontal = PlaylistInset), style = PlaylistStatsStyle.copy(fontSize = 12.sp), color = LectorColors.Muted)
                            else -> LazyRow(contentPadding = PaddingValues(horizontal = PlaylistInset), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                items(lista, key = { it.url }) { m ->
                                    Column(Modifier.width(96.dp).press3D().pressable { alAbrirOtro(f, m) }) {
                                        ImagenRemota(m.portadaUrl, Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(formaPortada(0.75f)), motor.cabeceras(f), fuenteId = f.id)
                                        Spacer(Modifier.height(6.dp))
                                        Text(m.titulo, style = TrackSubtitleStyle.copy(fontWeight = FontWeight.SemiBold), color = LectorColors.Text.copy(alpha = 0.86f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                        }
                    }
                }
                // Recomendaciones (AniList): tocar una la busca en la fuente de esta ficha y abre su ficha.
                val recs = recomendaciones
                if (!recs.isNullOrEmpty()) item(key = "recs") {
                    Column(Modifier.padding(top = 28.dp)) {
                        PlaylistSectionTitle("Recomendaciones", Modifier.padding(bottom = 8.dp))
                        LazyRow(contentPadding = PaddingValues(horizontal = PlaylistInset), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(recs, key = { it.id }) { r ->
                                Column(Modifier.width(96.dp).press3D().pressable { abrirRecomendacion(r) }) {
                                    Box {
                                        ImagenRemota(r.portada, Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(formaPortada(0.75f)))
                                        if (abriendoRec == r.id) Box(
                                            Modifier.matchParentSize().clip(SuaveShape(12.dp)).background(Color.Black.copy(alpha = 0.5f)),
                                            Alignment.Center,
                                        ) { IndicadorCarga(Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp) }
                                    }
                                    Spacer(Modifier.height(6.dp))
                                    Text(r.titulo, style = TrackSubtitleStyle.copy(fontWeight = FontWeight.SemiBold), color = LectorColors.Text.copy(alpha = 0.86f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
                }
                if (caps) {
                item(key = "cabCaps", contentType = "cabCaps") {
                    Column(Modifier.fillMaxWidth().padding(top = 26.dp, bottom = 4.dp)) {
                        PlaylistSectionTitle("Capítulos", trailing = {
                            Odometro(
                                if (visibles.size != fi.capitulos.size) "${visibles.size}/${fi.capitulos.size}" else "${fi.capitulos.size}",
                                PlaylistStatsStyle.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold), Modifier, LectorColors.Text.copy(alpha = 0.62f),
                            )
                        })
                        // El «añadidos últimamente ⌄» de Mochy: el orden actual; tocarlo abre Filtro / Ordenar / Mostrar.
                        PlaylistSortControl(
                            nombreOrdenCap(filtroCaps.orden) + (if (filtrosCap > 0) " · $filtrosCap ${if (filtrosCap == 1) "filtro" else "filtros"}" else ""),
                            filtroCaps.descendente, { hojaCaps = true }, Modifier.padding(start = PlaylistInset - 2.dp),
                        )
                    }
                }
                if (visibles.isEmpty()) item {
                    Text("Ningún capítulo coincide con los filtros.", Modifier.padding(horizontal = PlaylistInset, vertical = 18.dp), style = TrackTitleStyle, color = LectorColors.Muted)
                }
                // La clave es el índice original del capítulo (único aunque la fuente repita direcciones).
                itemsIndexedAnimados(visibles, key = { _, iv -> iv.index }, contentType = { _, _ -> 1 }) { pos, iv ->
                    val i = iv.index
                    val c = iv.value
                    val clave = Descargas.clave(f, manga.url, c)
                    val paginaGuardada = ext.paginaDe(f.id, manga.url, c.url)
                    val fraccion = Descargas.progreso[clave] ?: if (Descargas.enCola(clave)) 0f else null
                    val estado: EstadoDescarga = when {
                        fraccion != null -> EstadoDescarga.Descargando(fraccion)
                        versionDesc >= 0 && almacen.estaDescargado(f.id, manga.url, c.url) -> EstadoDescarga.Listo
                        else -> EstadoDescarga.Libre
                    }
                    val leido = leidosAhora.let { ext.esLeido(f.id, manga.url, c.url) }
                    val marcado = ext.esMarcado(f.id, manga.url, c.url)
                    val seleccionado = c.url in seleccion
                    // Deslizar la fila (los gestos de Mochy): cada lado hace lo elegido en Personalización → Gestos.
                    ChapterSwipeBox(
                        estado = EstadoCapituloSwipe(
                            leido = leido, marcado = marcado,
                            descargado = estado is EstadoDescarga.Listo, descargando = estado is EstadoDescarga.Descargando,
                        ),
                        onAction = { accion ->
                            when (accion) {
                                ChapterSwipeAction.NONE -> Unit
                                ChapterSwipeAction.MARK_READ -> {
                                    if (leido) ext.desmarcarLeido(f.id, manga.url, c.url) else ext.marcarLeido(f.id, manga.url, c.url)
                                    progreso()
                                }
                                ChapterSwipeAction.BOOKMARK -> ext.alternarMarcado(f.id, manga.url, c.url)
                                ChapterSwipeAction.DOWNLOAD -> when (estado) {
                                    is EstadoDescarga.Libre -> Descargas.encolar(ctx, f, manga.url, tituloFicha, listOf(c))
                                    is EstadoDescarga.Descargando -> Descargas.cancelar(ctx, clave)
                                    is EstadoDescarga.Listo -> {
                                        almacen.borrarDescarga(f.id, manga.url, c.url)
                                        Descargas.version.value++
                                    }
                                }
                                ChapterSwipeAction.MARK_PREVIOUS_READ -> {
                                    val todos = fi?.capitulos.orEmpty()
                                    val idx = todos.indexOfFirst { it.url == c.url }
                                    if (idx >= 0) ext.marcarLeidos(f.id, manga.url, todos.drop(idx + 1).map { it.url })
                                    progreso()
                                }
                            }
                        },
                        modifier = Modifier.revealOnAppear(
                            key = "cap_$i", seenState = revelados, enabled = !Mov.reducido, delayMillis = minOf(pos, 8) * 28,
                        ),
                        enabled = !enSeleccion,
                    ) {
                        // La fila de canción de Mochy: punto de acento a la izquierda = sin leer; leído = texto apagado.
                        val alternar = { seleccion = if (seleccionado) seleccion - c.url else seleccion + c.url }
                        PlaylistTrackRow(
                            badge = numeroTexto(c.numero).ifEmpty { "#" },
                            title = if (ext.capVerNumero && c.numero >= 0) "Capítulo ${numeroTexto(c.numero)}" else nombreCapitulo(c),
                            subtitle = listOfNotNull(
                                if (paginaGuardada > 0 && leido) "Pág: ${paginaGuardada + 1}" else null,
                                fechaTexto(c.fecha).takeIf { it.isNotEmpty() }, c.scanlator,
                            ).joinToString(" · "),
                            read = leido, accent = acento,
                            progressLabel = if (paginaGuardada > 0 && !leido) "Pág. ${paginaGuardada + 1}" else null,
                            leading = if (marcado) { { Icon(Iconos.Marca, "Marcado", tint = ColorMarca, modifier = Modifier.size(14.dp)) } } else null,
                            selected = seleccionado,
                            onClick = { if (enSeleccion) alternar() else leerCap(c, paginaGuardada) },
                            onLongClick = { Tacto.tic(); alternar() },
                            trailing = {
                                if (enSeleccion) PlaylistSelectTick(checked = seleccionado)
                                else ConAcento(acento) {
                                    BotonDescarga(estado, modifier = Modifier.clip(CircleShape).background(acento.copy(alpha = if (estado is EstadoDescarga.Libre) 0.1f else 0.18f)), onClick = {
                                        when (estado) {
                                            is EstadoDescarga.Libre -> Descargas.encolar(ctx, f, manga.url, tituloFicha, listOf(c))
                                            is EstadoDescarga.Descargando -> Descargas.cancelar(ctx, clave)
                                            is EstadoDescarga.Listo -> quitarDescarga = c
                                        }
                                    })
                                }
                            },
                        )
                    }
                }
                // El pie de «recibo» de Mochy: N CAPÍTULOS · FIN · N LEÍDOS.
                if (visibles.isNotEmpty()) item(key = "finCaps", contentType = "finCaps") {
                    val nLeidos = remember(visibles, leidosAhora) { visibles.count { ext.esLeido(f.id, manga.url, it.value.url) } }
                    PlaylistEndOfLine(
                        start = "${visibles.size} ${if (visibles.size == 1) "CAPÍTULO" else "CAPÍTULOS"}",
                        end = "$nLeidos ${if (nLeidos == 1) "LEÍDO" else "LEÍDOS"}",
                    )
                }
                }
            }
            if (caps) item {
                when {
                    cargando && fi == null -> PlaylistTrackBones()
                    cargando -> Box(Modifier.fillMaxWidth().padding(24.dp), Alignment.Center) { IndicadorCarga(Modifier.size(28.dp)) }
                    error != null -> Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(error!!, style = PlaylistStatsStyle.copy(fontSize = 12.sp), color = LectorColors.Error, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            PillButton("Reintentar", Icons.Rounded.Refresh, { recarga++ })
                            PillButton("Abrir en WebView", Icons.Rounded.ArrowForward, abrirNavegador)
                        }
                    }
                }
            }
        }
        val relleno = PaddingValues(bottom = if (enSeleccion) 130.dp else 60.dp)
        // El cristal de la barra de arriba toma la portada y la lista juntas (glassRoot de Mochy).
        Box(Modifier.fillMaxSize().glassRoot(glass, isActive = { plegado.morph.value > 0.01f || enSeleccion })) {
            if (dosColumnas) Row(Modifier.fillMaxSize()) {
                Box(Modifier.weight(0.45f).fillMaxHeight()) {
                    PlaylistCoverBackdrop(portadaNitida, portadaSuave, altoPortada, { plegado.scrollProgress.value }, fondoEstado)
                    LazyColumn(Modifier.fillMaxSize(), state = listaInfo, contentPadding = relleno) { contenido(true, false) }
                }
                LazyColumn(
                    Modifier.weight(0.55f).fillMaxHeight().statusBarsPadding(), state = lista,
                    contentPadding = PaddingValues(top = 56.dp, bottom = if (enSeleccion) 130.dp else 60.dp),
                ) { contenido(false, true) }
            } else {
                PlaylistCoverBackdrop(portadaNitida, portadaSuave, altoPortada, { plegado.scrollProgress.value }, fondoEstado)
                LazyColumn(Modifier.fillMaxSize(), state = lista, contentPadding = relleno) { contenido(true, true) }
            }
        }
        // Barra de desplazamiento rápido (como Komikku en la lista de capítulos).
        BarraDesplazamiento(lista)
        }

        // La barra de arriba de Mochy: atrás con la portada pequeña al plegarse; eligiendo, la barra de elegir.
        val modo = if (enSeleccion) PlaylistModeBar.Select(
            count = seleccion.size,
            allSelected = visibles.isNotEmpty() && visibles.all { it.value.url in seleccion },
            onToggleAll = {
                val todos = visibles.map { it.value.url }.toSet()
                seleccion = if (seleccion.containsAll(todos)) emptySet() else todos
            },
            onMore = { seleccion = visibles.map { it.value.url }.toSet() - seleccion },
            onClose = { seleccion = emptySet() },
            moreIcon = Icons.Rounded.Refresh, moreDescription = "Invertir selección",
        ) else null
        PlaylistTopBar(
            glassState = glass, morph = morph, miniCover = portadaMini, background = fondoEstado,
            onBackClick = onBack, onBackLongClick = {},
            onMiniCoverClick = { scope.launch { listaCab.animateScrollToItem(0) } },
            mode = modo,
        ) {
            if (fi != null && fi.capitulos.isNotEmpty()) {
                // Al bajar, el «Leer» de la cabecera sale de pantalla: aparece aquí con la barra plegada.
                BarCircleButton(
                    Icons.Rounded.PlayArrow, if (hayLeidos) "Reanudar" else "Leer", morph, filled = true,
                    modifier = Modifier.graphicsLayer { alpha = morph().coerceIn(0f, 1f) },
                    onClick = { if (morph() > 0.5f) fi.capitulos.getOrNull(sig)?.let { leerCap(it, paginaSig) } },
                )
                Spacer(Modifier.width(8.dp))
            }
            BarCircleButton(Icons.Rounded.ArrowForward, "Abrir en el navegador", morph, onClick = abrirNavegador)
            Spacer(Modifier.width(8.dp))
            Box {
                BarCircleButton(Icons.Rounded.MoreVert, "Más", morph, onClick = { menuFicha = true })
                MenuShiori(expanded = menuFicha, onDismissRequest = { menuFicha = false }) {
                    @Composable fun op(t: String, accion: () -> Unit) = DropdownMenuItem(text = { Text(t) }, onClick = { menuFicha = false; accion() })
                    op("Actualizar") { refrescando = true; recarga++ }
                    if (enBiblioteca != null) {
                        op("Editar categorías") { dialogoCats = true }
                        op("Migrar") { migrando = true }
                    }
                    urlWeb?.let { u -> op("Compartir") { compartirTexto(ctx, "$tituloFicha\n$u") } }
                    op("Notas") { editandoNotas = true }
                    if (enBiblioteca != null) {
                        op("Unir") { combinando = true }
                        op("Editar información") { editarInfo = true }
                    }
                    op("Ver recomendaciones") {
                        scope.launch {
                            if (recomendaciones.isNullOrEmpty()) { Avisos.mostrar(if (recomendaciones == null) "Buscando recomendaciones…" else "No hay recomendaciones"); return@launch }
                            repeat(30) {
                                listaCab.layoutInfo.visibleItemsInfo.firstOrNull { it.key == "recs" }?.let { listaCab.animateScrollToItem(it.index); return@launch }
                                listaCab.scrollBy(900f)
                            }
                        }
                    }
                    op("Abrir carpeta") { abrirCarpeta(ctx, com.example.lector.CarpetaApp.descargas(ctx)) }
                    op("Limpiar manga") { limpiar = true }
                    if (ext.configurables(f.pkg).any { it.id == f.id }) op("Ajustes de la fuente") { ajustesFuente = true }
                }
            }
        }

        if (enSeleccion) {
            val elegidos = fi?.capitulos?.filter { it.url in seleccion }.orEmpty()
            BarraAcciones(
                visible = true, modifier = Modifier.align(Alignment.BottomCenter),
                acciones = buildList {
                    add(AccionBarra(Icons.Rounded.Check, "Leído") { ext.marcarLeidos(f.id, manga.url, seleccion); progreso(); seleccion = emptySet() })
                    add(AccionBarra(Iconos.OjoTachado, "No leído") { ext.desmarcarLeidos(f.id, manga.url, seleccion); progreso(); seleccion = emptySet() })
                    if (elegidos.size == 1) add(AccionBarra(Icons.Rounded.KeyboardArrowDown, "Anteriores leídos") {
                        val caps = fi?.capitulos.orEmpty()
                        val idx = caps.indexOfFirst { it.url == elegidos[0].url }
                        if (idx >= 0) ext.marcarLeidos(f.id, manga.url, caps.drop(idx + 1).map { it.url })
                        progreso(); seleccion = emptySet()
                    })
                    val todosMarcados = elegidos.isNotEmpty() && elegidos.all { ext.esMarcado(f.id, manga.url, it.url) }
                    add(AccionBarra(if (todosMarcados) Iconos.MarcaBorde else Iconos.Marca, if (todosMarcados) "Desmarcar" else "Marcar") {
                        ext.marcarVarios(f.id, manga.url, seleccion, !todosMarcados); seleccion = emptySet()
                    })
                    add(AccionBarra(Iconos.Descargar, "Descargar") {
                        Descargas.encolar(ctx, f, manga.url, tituloFicha, elegidos.asReversed()); seleccion = emptySet()
                    })
                    if (elegidos.any { almacen.estaDescargado(f.id, manga.url, it.url) }) add(AccionBarra(Icons.Rounded.Delete, "Borrar descarga") {
                        elegidos.forEach { if (almacen.estaDescargado(f.id, manga.url, it.url)) almacen.borrarDescarga(f.id, manga.url, it.url) }
                        Descargas.version.value++
                        seleccion = emptySet()
                    })
                },
            )
        }
        // WebView del sitio, encima de la ficha.
        CapaEncima(webUrl) { u ->
            Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { } }) {
                WebViewScreen(u, tituloFicha, motor.cabeceras(f)) { webUrl = null; recarga++ }
            }
        }

        // Sinopsis completa.
        // Información completa (como el panel desplegado de Komikku): texto seleccionable, título y autor que se copian
        // al mantenerlos, géneros que buscan en la fuente.
        if (descExpandida) {
            HojaLector({ descExpandida = false }) {
                Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 24.dp)) {
                    androidx.compose.foundation.text.selection.SelectionContainer {
                        Column {
                            Text(tituloFicha, style = LectorType.Section.copy(fontSize = 22.sp, fontWeight = FontWeight.Bold), color = LectorColors.Text)
                            val au = autor
                            if (!au.isNullOrBlank()) Text(au, style = LectorType.Label, color = LectorColors.Muted, modifier = Modifier.padding(top = 4.dp))
                            Text(f?.nombre.orEmpty(), style = LectorType.Label, color = LectorColors.Muted, modifier = Modifier.padding(top = 2.dp))
                            Spacer(Modifier.height(14.dp))
                            Text(descripcion?.takeIf { it.isNotBlank() } ?: "Sin descripción.", style = LectorType.Body, color = LectorColors.Text)
                        }
                    }
                    if (generos.isNotEmpty()) {
                        Spacer(Modifier.height(16.dp))
                        @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
                        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            generos.forEach { g ->
                                Text(
                                    g, style = LectorType.Label, color = LectorColors.Text,
                                    modifier = Modifier.clip(CircleShape).background(LectorColors.PillFill)
                                        .combinedClickable(onClick = { descExpandida = false; alGenero(g) }, onLongClick = { copiarTexto(ctx, g) })
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { copiarTexto(ctx, descripcion.orEmpty()) }) { Text("Copiar descripción") }
                        TextButton(onClick = { descExpandida = false; alBuscarGlobal(tituloFicha) }) { Text("Buscar en todas") }
                    }
                }
            }
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
        CapaEncima(editandoCats) {
            BackHandler(editandoCats) { editandoCats = false; dialogoCats = true }
            Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { } }) {
                EditarCategoriasScreen(almacen) { editandoCats = false; dialogoCats = true }
            }
        }

        // Ajustes de la fuente, encima de la ficha.
        CapaEncima(ajustesFuente) {
            val cfg = remember { ext.configurables(f.pkg).firstOrNull { it.id == f.id } }
            if (cfg != null) Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { } }) {
                BackHandler(ajustesFuente) { ajustesFuente = false }
                AjustesFuenteScreen(ext, f.pkg, cfg, f.nombre) { ajustesFuente = false; recarga++ }
            }
        }
        // Migrar a otra fuente: pantalla encima de la ficha.
        val comicMigrar = enBiblioteca
        CapaEncima(if (migrando) comicMigrar else null) { comicMigrar ->
            BackHandler(migrando) { migrando = false }
            Box(Modifier.fillMaxSize().background(LectorColors.Bg).pointerInput(Unit) { detectTapGestures { } }) {
                MigrarScreen(
                    ext, almacen, comicMigrar, f, fi?.capitulos,
                    alHecho = { d -> migrando = false; alRecargar(); Avisos.mostrar("Migrado a ${d.nombre}"); onBack() },
                    onBack = { migrando = false },
                )
            }
        }

        quitarDescarga?.let { c ->
            DialogoAlerta(
                onDismissRequest = { quitarDescarga = null },
                title = { Text("¿Quitar la descarga?") },
                text = { Text("Se borrará el archivo descargado de «${nombreCapitulo(c)}». Podrás seguir leyéndolo online.") },
                confirmButton = {
                    TextButton(onClick = {
                        almacen.borrarDescarga(f.id, manga.url, c.url)
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
                        FilaTri("No leídos", filtroCaps.noLeidos) { cambiarFiltroCaps(filtroCaps.copy(noLeidos = it)) }
                        FilaTri("Descargados", filtroCaps.descargados) { cambiarFiltroCaps(filtroCaps.copy(descargados = it)) }
                        FilaTri("Marcados", filtroCaps.marcados) { cambiarFiltroCaps(filtroCaps.copy(marcados = it)) }
                        // Grupos de traducción (como Komikku): toca uno para ocultar sus capítulos en este manga.
                        val grupos = fi?.capitulos?.mapNotNull { it.scanlator }?.distinct()?.sorted().orEmpty()
                        if (grupos.size > 1) {
                            TituloSeccion("Grupos de traducción")
                            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                grupos.forEach { g -> ChipSel(g, g !in ocultos) { ext.alternarGrupo(f.id, manga.url, g) } }
                            }
                            LText("Encendido = se muestra. Apaga un grupo para ocultar sus capítulos.", LectorType.Label, Modifier.padding(horizontal = 24.dp, vertical = 6.dp), color = LectorColors.Muted)
                        }
                    }
                    1 -> Column {
                        (0..2).forEach { o ->
                            FilaOrden(nombreOrdenCap(o), filtroCaps.orden == o, !filtroCaps.descendente) {
                                cambiarFiltroCaps(if (filtroCaps.orden == o) filtroCaps.copy(descendente = !filtroCaps.descendente) else filtroCaps.copy(orden = o))
                            }
                        }
                        // Como Komikku: el filtro y el orden son de este manga; esto los copia a los que no tengan propio.
                        Spacer(Modifier.height(8.dp))
                        Box(Modifier.padding(horizontal = 20.dp)) {
                            BotonExpresivo("Usar como predeterminado", Icons.Rounded.Check, {
                                ext.filtroCapsComoPredeterminado(filtroCaps); Avisos.mostrar("Filtro y orden predeterminados guardados")
                            }, estilo = EstiloBoton.TONAL)
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
    if (otrosSeguimientos && comic != null) DialogoServicios(comic.id, tituloFicha) { otrosSeguimientos = false }
    // Combinar con otra entrada de la biblioteca (de otra fuente): primero las de título parecido.
    if (combinando && f != null) {
        val esta = "${f.id}|${manga.url}"
        val principal = ext.principalDe(esta) ?: esta
        var q by remember { mutableStateOf("") }
        val clave = tituloComparable(tituloFicha)
        val candidatas = remember(almacen.version, q) {
            almacen.cargar().filter { it.enLinea && "${it.fuenteId}|${it.urlManga}" != principal && "${it.fuenteId}|${it.urlManga}" !in ext.unidas(principal) }
                .filter { q.isBlank() || it.titulo.contains(q, true) }
                .sortedByDescending { tituloComparable(it.titulo).let { t -> t == clave || t.contains(clave) || clave.contains(t) } }
                .take(40)
        }
        DialogoShiori("Combinar «$tituloFicha» con…", { combinando = false }) {
            Box(Modifier.padding(horizontal = 10.dp, vertical = 4.dp)) { CampoBusqueda(q, { q = it }, "Buscar en tu biblioteca") }
            candidatas.forEach { c ->
                val nombre = ext.fuentes.firstOrNull { it.id == c.fuenteId }?.nombre ?: "otra fuente"
                OpcionPildora("${c.titulo} · $nombre", false) {
                    ext.fusionar(principal, "${c.fuenteId}|${c.urlManga}")
                    Tacto.confirmar(); Avisos.mostrar("Combinadas: en la biblioteca queda una sola entrada")
                    combinando = false
                }
            }
        }
    }

    if (editandoNotas) {
        var t by remember { mutableStateOf(notas) }
        DialogoAlerta(
            onDismissRequest = { editandoNotas = false },
            title = { Text("Notas") },
            text = { CampoTextoShiori(t, { t = it }, Modifier.fillMaxWidth().height(180.dp), placeholder = { Text("Lo que quieras recordar de este manga") }) },
            confirmButton = { TextButton(onClick = {
                notas = t.trim(); editandoNotas = false
                ctx.getSharedPreferences("notas", android.content.Context.MODE_PRIVATE).edit().putString(clNotas, notas).apply()
            }) { Text("Guardar") } },
            dismissButton = { TextButton(onClick = { editandoNotas = false }) { Text("Cancelar") } },
        )
    }
    if (limpiar) DialogoAlerta(
        onDismissRequest = { limpiar = false },
        title = { Text("Limpiar manga") },
        text = { Text("Se borran sus capítulos descargados, los leídos y la página por la que ibas. Sigue en tu biblioteca.") },
        confirmButton = { TextButton(onClick = {
            limpiar = false
            val caps = ficha?.capitulos.orEmpty()
            caps.forEach { if (almacen.estaDescargado(fuenteId, manga.url, it.url)) almacen.borrarDescarga(fuenteId, manga.url, it.url) }
            ext.desmarcarLeidos(fuenteId, manga.url, caps.map { it.url })
            Descargas.version.value++
            Avisos.mostrar("Manga limpiado")
        }) { Text("Limpiar") } },
        dismissButton = { TextButton(onClick = { limpiar = false }) { Text("Cancelar") } },
    )
    if (editarInfo && comic != null) DialogoEditarInfo(
        comic = comic, almacen = almacen,
        alRestablecer = { almacen.actualizarComic(comic.id) { it.copy(editado = false, portadaPropia = false) }; recarga++ },
        alCerrar = { editarInfo = false; alRecargar() },
    )

    if (verPortada) VisorPortada(
        fuenteId = f.id, url = fi?.detalle?.portadaUrl ?: manga.portadaUrl, cabeceras = motor.cabeceras(f), titulo = tituloFicha,
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
    DialogoAlerta(
        onDismissRequest = alCerrar,
        title = { Text("Editar información") },
        text = {
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CampoTextoShiori(titulo, { titulo = it }, label = { Text("Título") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                CampoTextoShiori(autor, { autor = it }, label = { Text("Autor") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                CampoTextoShiori(generos, { generos = it }, label = { Text("Géneros (separados por comas)") }, modifier = Modifier.fillMaxWidth())
                CampoTextoShiori(descripcion, { descripcion = it }, label = { Text("Descripción") }, minLines = 3, modifier = Modifier.fillMaxWidth())
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
    fuenteId: Long? = null,
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
        else url?.let { Imagenes.bytes(it, cabeceras, fuenteId) }
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onCerrar,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(Modifier.fillMaxSize().background(Color(0xF0000000)).pointerInput(Unit) { detectTapGestures { onCerrar() } }) {
            if (local != null) Image(local, null, Modifier.fillMaxSize().padding(24.dp), contentScale = ContentScale.Fit)
            else ImagenRemota(url, Modifier.fillMaxSize().padding(24.dp), cabeceras, escala = ContentScale.Fit, maxLado = 1400, fuenteId = fuenteId)
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


private val TintaBtn = Color(0xFF0D0D18)
private val ColorMarca = Color(0xFF2DBE6C)

/** Tinta legible sobre un color de acento (los acentos de la portada pueden ser claros u oscuros). */
private fun tintaSobre(c: Color): Color = if (c.luminance() > 0.55f) TintaBtn else Color.White

/** Cambia el color de acento (MaterialTheme.primary) de lo que lleva dentro: botones de descarga, chips... */
@Composable
private fun ConAcento(acento: Color, content: @Composable () -> Unit) {
    val base = MaterialTheme.colorScheme
    val esquema = if (acento == base.primary) base else base.copy(primary = acento, onPrimary = tintaSobre(acento))
    MaterialTheme(colorScheme = esquema, content = content)
}

/** Género como píldora (las de Mochy, tinta del tema al 10 %): tocarlo busca ese género en la fuente. */
@Composable
private fun PildoraGenero(texto: String, onClick: () -> Unit) {
    Box(
        Modifier.pressable(onClick).height(28.dp).clip(CircleShape).background(LectorColors.Text.copy(alpha = 0.1f))
            .padding(horizontal = 12.dp),
        Alignment.Center,
    ) { Text(texto, style = PlaylistStatsStyle.copy(fontSize = 12.sp), color = LectorColors.Text.copy(alpha = 0.86f), maxLines = 1) }
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
    // Como «Saltar capítulos duplicados» de Komikku: de los capítulos con el mismo número (varios grupos de traducción)
    // queda el que se abre o, si no, el del mismo grupo; así la lectura continua no repite el capítulo.
    val (capsLeer, indiceLeer) = remember(capitulos, indiceInicial, alm.saltarDuplicados, alm.saltarLeidos) {
        val actual = capitulos.getOrNull(indiceInicial)
        if ((!alm.saltarDuplicados && !alm.saltarLeidos) || actual == null) capitulos to indiceInicial
        else {
            // «Saltar capítulos leídos» de Mihon: al pasar de capítulo no se entra en los ya leídos.
            val base = if (!alm.saltarLeidos) capitulos else capitulos.filter { it === actual || !ext.esLeido(f.id, manga?.url.orEmpty(), it.url) }
            if (!alm.saltarDuplicados) return@remember base to base.indexOf(actual).coerceAtLeast(0)
            val elegido = HashMap<Float, CapituloFuente>()
            base.forEach { c ->
                if (c.numero < 0f) return@forEach
                val ya = elegido[c.numero]
                if (ya == null || c === actual || (ya !== actual && ya.scanlator != actual.scanlator && c.scanlator == actual.scanlator)) elegido[c.numero] = c
            }
            val lista = base.filter { it.numero < 0f || elegido[it.numero] === it }
            lista to lista.indexOf(actual).coerceAtLeast(0)
        }
    }
    val enOrden = remember(capsLeer) { capsLeer.asReversed() }
    val caps = remember(enOrden, f.id) {
        enOrden.map { c -> CapVisor("${f.id}|${manga?.url}|${c.url}", nombreCapitulo(c), OrigenFuente(alm, motor, f, c, manga?.url.orEmpty())) }
    }
    val inicio = (capsLeer.lastIndex - indiceLeer).coerceIn(0, caps.lastIndex)
    val comic = remember(manga, alm.version) { manga?.let { alm.comicEnLinea(f.id, it.url) } }
    val urlM = manga?.url ?: comic?.urlManga.orEmpty()
    // Capítulos terminados en esta sesión: si está activado «borrar al leer», se borran al salir o al pasar al siguiente.
    val terminados = remember { mutableSetOf<String>() }
    fun borrarLeidos(salvo: String?) {
        if (!alm.borrarAlLeer) return
        // Como Mihon: los capítulos con marcador no se borran salvo que se permita en Ajustes → Descargas.
        terminados.filter { it != salvo && (alm.borrarMarcados || !ext.esMarcado(f.id, urlM, it)) }.forEach { url ->
            if (alm.estaDescargado(f.id, urlM, url)) alm.borrarDescarga(f.id, urlM, url)
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
            ext.guardarPagina(f.id, urlM, c.url, pagina) // también la última: al reabrirlo sigue ahí
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
                val siguientes = enOrden.drop(i + 1).take(adelante).filter { !alm.estaDescargado(f.id, urlM, it.url) }
                if (siguientes.isNotEmpty()) Descargas.encolar(ctx, f, manga?.url ?: comic.urlManga.orEmpty(), comic.titulo, siguientes, avisar = false)
            }
        },
        alTerminarCapitulo = { i ->
            val c = enOrden[i]
            ext.marcarLeido(f.id, urlM, c.url)
            // Con los duplicados saltados, las otras versiones del mismo capítulo también quedan leídas.
            if (alm.saltarDuplicados && c.numero >= 0f) {
                ext.marcarLeidos(f.id, urlM, capitulos.filter { it.numero == c.numero && it.url != c.url }.map { it.url })
            }
            terminados += c.url
            if (manga != null) {
                alm.actualizarProgresoEnLinea(f.id, manga.url, capitulos.count { ext.esLeido(f.id, urlM, it.url) }, capitulos.size)
            }
            // Seguimiento (AniList): sube el número del capítulo terminado.
            val cid = comic?.id
            // MyAnimeList, Kitsu y MangaUpdates: cada uno vinculado recibe el capítulo terminado.
            if (cid != null) {
                val n2 = if (c.numero > 0) c.numero.toInt() else capitulos.count { ext.esLeido(f.id, urlM, it.url) }
                scope.launch { com.example.lector.Seguidores.avanzar(ctx, cid, n2) }
            }
            if (cid != null && alm.seguimientoDe(cid) != null) {
                val numero = if (c.numero > 0) c.numero.toInt() else capitulos.count { ext.esLeido(f.id, urlM, it.url) }
                scope.launch { withContext(Dispatchers.IO) { alm.avanzarSeguimiento(cid, numero) } }
            }
        },
        acciones = AccionesVisor(
            urlWeb = { urlCapitulo(f, enOrden[it]) },
            estaMarcado = { ext.esMarcado(f.id, urlM, enOrden[it].url) },
            alternarMarca = { ext.alternarMarcado(f.id, urlM, enOrden[it].url) },
            usarComoPortada = usarComoPortada,
            esLeido = { ext.esLeido(f.id, urlM, enOrden[it].url) },
            cabecerasWeb = motor.cabeceras(f),
        ),
        onBack = onBack,
    )
}

/**
 * Busca un manga en la fuente [f] probando sus títulos por orden y devuelve el mejor resultado: uno con exactamente
 * alguno de esos títulos (sin contar mayúsculas ni signos) o, si no hay, el primero que salga con el título principal.
 */
private suspend fun buscarEnFuente(motor: com.example.lector.ext.MotorFuentes?, f: FuenteUI, titulos: List<String>): MangaFuente? {
    if (motor == null) return null
    val claves = titulos.map { tituloComparable(it) }.toSet()
    var primero: MangaFuente? = null
    for (t in titulos.take(4)) {
        val res = runCatching { motor.buscar(f, t, 1).mangas }.getOrElse { if (it is kotlinx.coroutines.CancellationException) throw it else emptyList() }
        res.firstOrNull { tituloComparable(it.titulo) in claves }?.let { return it }
        if (primero == null) primero = res.firstOrNull()
    }
    return primero
}

/** Interruptores de las pruebas automáticas del emulador (solo se activan en compilaciones depurables). */
object PruebaAuto {
    @Volatile var abrirRecomendacion = false
    @Volatile var probarCapitulo = false
}

/** Miniatura ligera (unos 300 px de ancho) de una página, para la vista previa. */
private fun miniatura(bytes: ByteArray): androidx.compose.ui.graphics.ImageBitmap? {
    val o = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
    android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
    var m = 1
    while (o.outWidth / (m * 2) >= 300 || o.outHeight / m > 2000) m *= 2
    val d = android.graphics.BitmapFactory.Options().apply { inSampleSize = m }
    return android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, d)?.asImageBitmap()
}

/** Abre una carpeta en el gestor de archivos (si se puede); si no, enseña su ruta. */
internal fun abrirCarpeta(ctx: android.content.Context, dir: java.io.File) {
    val base = android.os.Environment.getExternalStorageDirectory().path
    val ok = dir.path.startsWith(base) && runCatching {
        val rel = dir.path.removePrefix(base).trim('/')
        val uri = android.net.Uri.parse("content://com.android.externalstorage.documents/document/" + android.net.Uri.encode("primary:$rel"))
        ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW).setDataAndType(uri, "vnd.android.document/directory")
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION))
    }.isSuccess
    if (!ok) Avisos.mostrar("Carpeta: ${dir.path}")
}
