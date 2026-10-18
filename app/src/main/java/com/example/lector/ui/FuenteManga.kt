package com.example.lector.ui

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.example.lector.EntradaHistorial
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
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

/**
 * Descargas de capítulos (.cbz → biblioteca). Viven fuera de las pantallas: si sales de la ficha la descarga
 * sigue, y al volver ves su progreso. Tocar otra vez el botón de un capítulo que se está descargando la CANCELA.
 */
object GestorDescargas {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val trabajos = HashMap<String, Job>()

    /** "fuenteId|urlCapítulo" → fracción descargada (0f = preparando). Las pantallas lo leen y se repintan. */
    val progreso = mutableStateMapOf<String, Float>()

    /** Sube cada vez que termina o se borra una descarga, para que las pantallas relean `almacen.descargas()`. */
    val version = mutableStateOf(0)

    fun clave(f: FuenteUI, c: CapituloFuente) = "${f.id}|${c.url}"

    fun iniciar(
        ctx: Context, almacen: Almacen, motor: MotorFuentes, f: FuenteUI, c: CapituloFuente,
        tituloManga: String, alTerminar: () -> Unit,
    ) {
        val clave = clave(f, c)
        if (trabajos.containsKey(clave)) return
        val app = ctx.applicationContext
        progreso[clave] = 0f
        trabajos[clave] = scope.launch {
            val yo = coroutineContext[Job]
            val archivo = File(app.cacheDir, "cap_${System.nanoTime()}.cbz")
            try {
                motor.descargarCbz(f, c, archivo) { h, t -> if (t > 0) progreso[clave] = h.toFloat() / t }
                val titulo = "$tituloManga - ${c.nombre.ifBlank { numeroTexto(c.numero) }}"
                    .replace(Regex("[\\\\/:*?\"<>|]"), "_")
                val comic = withContext(Dispatchers.IO) { almacen.importarArchivo(archivo, titulo) }
                if (comic != null) {
                    almacen.registrarDescarga(f.id, c.url, comic.id)
                    Toast.makeText(app, "Añadido a la biblioteca", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(app, "No se pudo descargar: archivo vacío", Toast.LENGTH_LONG).show()
                }
            } catch (e: CancellationException) {
                throw e // el aviso de "cancelada" lo da cancelar()
            } catch (e: Exception) {
                Toast.makeText(app, "No se pudo descargar: ${e.message ?: "error desconocido"}", Toast.LENGTH_LONG).show()
            } finally {
                archivo.delete()
                // Solo limpia si nadie ha cancelado y reiniciado mientras tanto.
                if (trabajos[clave] === yo) {
                    trabajos.remove(clave)
                    progreso.remove(clave)
                }
                version.value++
                alTerminar()
            }
        }
    }

    fun cancelar(ctx: Context, clave: String) {
        val t = trabajos.remove(clave) ?: return
        progreso.remove(clave) // el botón vuelve a "descargar" al instante
        t.cancel()
        Toast.makeText(ctx.applicationContext, "Descarga cancelada", Toast.LENGTH_SHORT).show()
    }
}

private fun urlManga(f: FuenteUI, m: MangaFuente): String? =
    if (m.url.startsWith("http")) m.url
    else if (f.baseUrl.isBlank()) null
    else f.baseUrl.trimEnd('/') + "/" + m.url.trimStart('/')

@Composable
private fun AccionFicha(
    icono: androidx.compose.ui.graphics.vector.ImageVector, texto: String, activa: Boolean,
    acento: Color, onClick: () -> Unit,
) {
    Column(
        Modifier.pressable(onClick).padding(horizontal = 14.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icono, texto, tint = if (activa) acento else LectorColors.Muted, modifier = Modifier.size(26.dp))
        Spacer(Modifier.height(4.dp))
        LText(
            texto, LectorType.Label.copy(fontSize = 12.sp),
            color = if (activa) acento else LectorColors.Muted, maxLines = 1,
        )
    }
}

// ───────────────────── Ficha del manga (online) ─────────────────────

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

    // Biblioteca y categorías
    var enBiblioteca by remember { mutableStateOf(almacen.comicEnLinea(fuenteId, manga.url)) }
    var guardando by remember { mutableStateOf(false) }
    var dialogoCats by remember { mutableStateOf(false) }
    var editandoCats by remember { mutableStateOf(false) }
    // Descargas ya hechas ("fuenteId|capítulo" → id del cómic) y capítulo cuya descarga se quiere borrar.
    val hechas = remember(GestorDescargas.version.value) { almacen.descargas() }
    var quitarDescarga by remember { mutableStateOf<CapituloFuente?>(null) }

    LaunchedEffect(fuenteId, manga.url, recarga) {
        if (f == null || motor == null) return@LaunchedEffect
        cargando = true; error = null
        val r = runCatching { motor.ficha(f, manga) }
        r.onSuccess { ficha = it }
            .onFailure { error = it.message ?: "No se pudo cargar la ficha" }
        cargando = false
        val cargada = r.getOrNull() ?: return@LaunchedEffect
        // Si está en la biblioteca, deja al día "capítulos leídos / totales".
        almacen.actualizarProgresoEnLinea(
            f.id, manga.url, cargada.capitulos.count { ext.esLeido(f.id, it.url) }, cargada.capitulos.size,
        )
        // Venimos del historial: abre directamente el capítulo donde lo dejaste.
        if (reanudarCap != null) {
            alConsumirReanudar()
            val i = cargada.capitulos.indexOfFirst { it.url == reanudarCap.first }
            if (i >= 0) onLeer(cargada.capitulos, i, reanudarCap.second)
        }
    }

    val lista = rememberLazyListState()
    val fi = ficha
    val acento = MaterialTheme.colorScheme.primary

    // Corazón "En biblioteca": añade (descargando la portada) o quita el manga.
    fun alternarBiblioteca() {
        if (f == null || motor == null || guardando) return
        if (enBiblioteca != null) {
            almacen.quitarEnLinea(f.id, manga.url)
            enBiblioteca = null
            alRecargar()
            Toast.makeText(ctx, "Quitado de la biblioteca", Toast.LENGTH_SHORT).show()
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
            val comic = withContext(Dispatchers.IO) {
                almacen.anadirEnLinea(f.id, manga.url, titulo, urlPortada, portada, fi?.capitulos?.size ?: 0, leidos)
            }
            enBiblioteca = comic
            guardando = false
            alRecargar()
            Toast.makeText(ctx, "Añadido a la biblioteca", Toast.LENGTH_SHORT).show()
            // Como en Komikku: al añadir, pregunta las categorías (si hay).
            if (almacen.categorias().isNotEmpty()) dialogoCats = true
        }
    }

    if (f == null || motor == null) {
        Column(Modifier.fillMaxSize().background(LectorColors.Bg).statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).pressable(onBack).clip(CircleShape).background(LectorColors.PillFill), Alignment.Center) {
                    Icon(Icons.Rounded.ArrowBack, "Atrás", tint = LectorColors.Text)
                }
            }
            LText("La fuente no está disponible.", LectorType.Body, Modifier.padding(24.dp), color = LectorColors.Muted)
        }
        return
    }

    // Cuánto se ha desplazado la cabecera (se lee al dibujar, sin recomponer).
    val scrollPx: () -> Float = { if (lista.firstVisibleItemIndex == 0) lista.firstVisibleItemScrollOffset.toFloat() else 99999f }

    Box(Modifier.fillMaxSize().background(LectorColors.Bg)) {
        LazyColumn(Modifier.fillMaxSize(), state = lista, contentPadding = PaddingValues(bottom = 130.dp)) {
            item {
                val lineas = listOfNotNull(
                    fi?.detalle?.autor,
                    fi?.detalle?.let { d -> d.artista?.takeIf { it != d.autor } },
                    fi?.let { estadoTexto(it.detalle.estado) },
                    f.nombre,
                )
                CabeceraFicha(
                    fi?.detalle?.portadaUrl ?: manga.portadaUrl, motor.cabeceras(f),
                    fi?.detalle?.titulo ?: manga.titulo, lineas, acento, scrollPx,
                )
            }
            item {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    val en = enBiblioteca != null
                    AccionFicha(
                        if (en) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        if (en) "En biblioteca" else "Añadir a biblioteca", en, acento,
                    ) { alternarBiblioteca() }
                    if (en) AccionFicha(Icons.Rounded.List, "Categorías", false, acento) { dialogoCats = true }
                    AccionFicha(Icons.Rounded.ArrowForward, "Navegador", false, acento) {
                        val u = urlManga(f, manga)
                        if (u == null) Toast.makeText(ctx, "Esta fuente no da la dirección web", Toast.LENGTH_SHORT).show()
                        else runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    }
                }
            }
            if (fi != null) {
                fi.detalle.descripcion?.let { d ->
                    item {
                        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp)) {
                            LText(
                                d, LectorType.Body, Modifier.pressable { descExpandida = !descExpandida },
                                color = LectorColors.Muted, maxLines = if (descExpandida) Int.MAX_VALUE else 4,
                            )
                        }
                    }
                }
                if (fi.detalle.generos.isNotEmpty()) {
                    item {
                        Row(
                            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) { fi.detalle.generos.forEach { ChipBorde(it, acento) } }
                    }
                }
                item {
                    LText(
                        "${fi.capitulos.size} capítulos", LectorType.Label,
                        Modifier.padding(horizontal = 24.dp, vertical = 10.dp),
                    )
                }
                itemsIndexed(fi.capitulos) { i, c ->
                    val clave = GestorDescargas.clave(f, c)
                    val fraccion = GestorDescargas.progreso[clave]
                    val estado: EstadoDescarga = when {
                        fraccion != null -> EstadoDescarga.Descargando(fraccion)
                        clave in hechas -> EstadoDescarga.Listo
                        else -> EstadoDescarga.Libre
                    }
                    Row(
                        Modifier.fillMaxWidth().entrada(i).pressable { onLeer(fi.capitulos, i, 0) }
                            .padding(start = 24.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                            LText(
                                c.nombre.ifBlank { "Capítulo ${numeroTexto(c.numero)}" }, LectorType.Body, maxLines = 2,
                                color = if (ext.esLeido(f.id, c.url)) LectorColors.Muted else LectorColors.Text,
                            )
                            val sub = listOfNotNull(
                                fechaTexto(c.fecha).takeIf { it.isNotEmpty() }, c.scanlator,
                            ).joinToString(" · ")
                            if (sub.isNotEmpty()) LText(sub, LectorType.Label, color = LectorColors.Muted, maxLines = 1)
                        }
                        Spacer(Modifier.width(6.dp))
                        // Descargar el capítulo como .cbz y meterlo en la biblioteca.
                        // Mientras descarga, volver a tocar el botón cancela la descarga.
                        BotonDescarga(estado, onClick = {
                            when (estado) {
                                is EstadoDescarga.Libre -> GestorDescargas.iniciar(
                                    ctx, almacen, motor, f, c, fi.detalle.titulo, alRecargar,
                                )
                                is EstadoDescarga.Descargando -> GestorDescargas.cancelar(ctx, clave)
                                is EstadoDescarga.Listo -> quitarDescarga = c
                            }
                        })
                    }
                }
            }
            item {
                Box(Modifier.fillMaxWidth().padding(24.dp), Alignment.Center) {
                    when {
                        cargando -> CircularProgressIndicator(Modifier.size(28.dp))
                        error != null -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            LText(error!!, LectorType.Label, color = Color(0xFFE57373))
                            Spacer(Modifier.height(10.dp))
                            PillButton("Reintentar", Icons.Rounded.Refresh, { recarga++ })
                        }
                    }
                }
            }
        }

        BarraSuperior(scrollPx, fi?.detalle?.titulo ?: manga.titulo, onBack)

        if (fi != null && fi.capitulos.isNotEmpty()) {
            // La mayoría de fuentes listan el capítulo más nuevo primero: el primero a leer es el último.
            val sig = (fi.capitulos.lastIndex downTo 0).firstOrNull { !ext.esLeido(f.id, fi.capitulos[it].url) } ?: 0
            val hayLeidos = fi.capitulos.any { ext.esLeido(f.id, it.url) }
            BotonFlotante(
                if (hayLeidos) "Reanudar" else "Empezar a leer", acento,
                { onLeer(fi.capitulos, sig, 0) }, Modifier.align(Alignment.BottomEnd),
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

        quitarDescarga?.let { c ->
            AlertDialog(
                onDismissRequest = { quitarDescarga = null },
                title = { Text("¿Quitar la descarga?") },
                text = { Text("Se borrará «${c.nombre.ifBlank { "Capítulo ${numeroTexto(c.numero)}" }}» de tu biblioteca.") },
                confirmButton = {
                    TextButton(onClick = {
                        hechas[GestorDescargas.clave(f, c)]?.let { almacen.eliminar(it) }
                        almacen.olvidarDescarga(f.id, c.url)
                        GestorDescargas.version.value++
                        quitarDescarga = null
                        alRecargar()
                    }) { Text("Quitar") }
                },
                dismissButton = { TextButton(onClick = { quitarDescarga = null }) { Text("Cancelar") } },
            )
        }
    }
}

/** Cabecera de la ficha: portada difuminada y teñida, parallax y entrada suave. */
@Composable
private fun CabeceraFicha(
    portada: String?, cabeceras: Map<String, String>, titulo: String,
    lineas: List<String>, acento: Color, scroll: () -> Float,
) {
    var entro by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entro = true }
    val t by animateFloatAsState(if (entro) 1f else 0f, tween(550), label = "entradaFicha")
    Box(Modifier.fillMaxWidth().height(420.dp)) {
        ImagenRemota(
            portada,
            Modifier.fillMaxSize().graphicsLayer { translationY = scroll() * 0.5f }.blur(32.dp).alpha(0.6f),
            cabeceras,
        )
        Box(Modifier.fillMaxSize().background(acento.copy(alpha = 0.18f)))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.35f to Color.Transparent, 1f to Color.Black)))
        Row(
            Modifier.align(Alignment.BottomStart).padding(24.dp)
                .graphicsLayer { alpha = t; translationY = (1f - t) * 60f },
            verticalAlignment = Alignment.Bottom,
        ) {
            ImagenRemota(portada, Modifier.width(124.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(20.dp)), cabeceras)
            Spacer(Modifier.width(18.dp))
            Column(Modifier.weight(1f)) {
                LText(titulo, LectorType.Hero, maxLines = 3)
                Spacer(Modifier.height(6.dp))
                lineas.forEach { LText(it, LectorType.Body, color = LectorColors.Muted, maxLines = 2) }
            }
        }
    }
}

// ───────────────────── Leer online (tira vertical) ─────────────────────

private object CachePaginas {
    // Límite por bytes (48 MB), no por número de páginas: algunas fuentes dan tiras muy altas.
    val cache = object : LruCache<String, ImageBitmap>(48 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap): Int = value.width * value.height * 4
    }
}

private suspend fun decodificar(bytes: ByteArray, anchoMax: Int = 1440): ImageBitmap? = withContext(Dispatchers.Default) {
    val medidas = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, medidas)
    var muestra = 1
    while (medidas.outWidth / (muestra * 2) >= anchoMax ||
        (medidas.outWidth / muestra).toLong() * (medidas.outHeight / muestra) > 12_000_000L
    ) muestra *= 2
    val op = BitmapFactory.Options().apply { inSampleSize = muestra }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, op)?.asImageBitmap()
}

private fun urlCapitulo(f: FuenteUI, c: CapituloFuente): String? =
    if (c.url.startsWith("http")) c.url
    else if (f.baseUrl.isBlank()) null
    else f.baseUrl.trimEnd('/') + "/" + c.url.trimStart('/')

/**
 * Lector online con los controles de Komikku: barra superior (título y capítulo), barra de avance con
 * número de página y botones de capítulo anterior/siguiente, y una fila de acciones. Un toque en el
 * centro muestra u oculta las barras. Modos: tira vertical, paginado → y paginado ← (manga).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LeerOnlineScreen(
    ext: ExtManager,
    fuenteId: Long,
    titulo: String,
    capitulos: List<CapituloFuente>,
    indiceInicial: Int,
    // Para el historial y el progreso de la biblioteca (null = no se anota nada).
    almacen: Almacen? = null,
    manga: MangaFuente? = null,
    // Página por la que seguir en el primer capítulo (al reanudar desde el historial).
    paginaInicial: Int = 0,
    onBack: () -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val f = ext.fuentes.firstOrNull { it.id == fuenteId }
    val motor = Motor.actual
    var indice by remember { mutableStateOf(indiceInicial.coerceIn(0, (capitulos.size - 1).coerceAtLeast(0))) }
    val cap = capitulos.getOrNull(indice)

    if (f == null || motor == null || cap == null) {
        Box(Modifier.fillMaxSize().background(Color.Black), Alignment.Center) {
            LText("No se puede abrir el capítulo.", LectorType.Body, color = LectorColors.Muted)
        }
        return
    }

    val modo = ext.modoLector // 0 tira vertical · 1 paginado → · 2 paginado ← (manga)
    var barras by remember { mutableStateOf(true) }
    var dialogoCaps by remember { mutableStateOf(false) }
    var dialogoAjustes by remember { mutableStateOf(false) }
    var paginas by remember(cap.url) { mutableStateOf<Int?>(null) }
    var error by remember(cap.url) { mutableStateOf<String?>(null) }
    var recarga by remember(cap.url) { mutableStateOf(0) }
    var actual by remember(cap.url) { mutableStateOf(0) }

    val listState = rememberLazyListState()
    val pagerState = rememberPagerState { paginas ?: 0 }
    val total = paginas

    LaunchedEffect(cap.url, recarga) {
        paginas = null; error = null; actual = 0
        runCatching { motor.cantidadPaginas(f, cap) }
            .onSuccess { paginas = it }
            .onFailure { error = it.message ?: "No se pudieron cargar las páginas" }
    }
    // Al cambiar de capítulo (o terminar de cargar) se vuelve a la primera página;
    // solo la primera vez, si venimos del historial, se salta a la página guardada.
    var paginaInicialUsada by remember { mutableStateOf(false) }
    LaunchedEffect(cap.url, total) {
        if (total != null && total > 0) {
            val destino = if (!paginaInicialUsada && indice == indiceInicial) paginaInicial.coerceIn(0, total - 1) else 0
            paginaInicialUsada = true
            runCatching { listState.scrollToItem(destino) }
            runCatching { pagerState.scrollToPage(destino) }
        }
    }
    // Página actual según el modo.
    LaunchedEffect(modo, total) {
        if (total == null || total == 0) return@LaunchedEffect
        if (modo == 0) snapshotFlow { listState.firstVisibleItemIndex }.collect { actual = it.coerceIn(0, total - 1) }
        else snapshotFlow { pagerState.currentPage }.collect { actual = it.coerceIn(0, total - 1) }
    }
    // Capítulo leído al llegar a la última página (y se actualiza "leídos / totales" en la biblioteca).
    LaunchedEffect(actual, total) {
        if (total != null && total > 0 && actual >= total - 1) {
            ext.marcarLeido(f.id, cap.url)
            if (almacen != null && manga != null) {
                almacen.actualizarProgresoEnLinea(
                    f.id, manga.url, capitulos.count { ext.esLeido(f.id, it.url) }, capitulos.size,
                )
            }
        }
    }
    // Historial: anota el capítulo y la página que estás leyendo (con una pequeña espera para no
    // escribir en cada fotograma del desplazamiento).
    LaunchedEffect(cap.url, actual, total) {
        if (almacen == null || manga == null || total == null || total <= 0) return@LaunchedEffect
        delay(600)
        almacen.registrarHistorialLinea(
            EntradaHistorial(
                fuenteId = f.id, urlManga = manga.url,
                titulo = titulo.ifBlank { manga.titulo },
                portadaUrl = manga.portadaUrl,
                capUrl = cap.url,
                capNombre = cap.nombre.ifBlank { "Capítulo ${numeroTexto(cap.numero)}" },
                pagina = actual.coerceIn(0, total - 1), total = total,
                leido = System.currentTimeMillis(),
            ),
        )
    }

    fun irA(p: Int) {
        scope.launch {
            if (modo == 0) listState.scrollToItem(p) else pagerState.scrollToPage(p)
        }
    }
    // "Siguiente" = índice menor: las fuentes suelen listar lo más nuevo primero.
    val haySiguiente = indice > 0
    val hayAnterior = indice < capitulos.lastIndex

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        when {
            total == null -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                if (error == null) CircularProgressIndicator(Modifier.size(28.dp))
                else Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                    LText(error!!, LectorType.Label, color = Color(0xFFE57373))
                    Spacer(Modifier.height(10.dp))
                    PillButton("Reintentar", Icons.Rounded.Refresh, { recarga++ })
                }
            }
            modo == 0 -> LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { barras = !barras } },
            ) {
                items(total) { i -> PaginaOnline(f, cap, i, motor, ajustar = false) }
                item {
                    Row(
                        Modifier.fillMaxWidth().navigationBarsPadding().padding(24.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                    ) {
                        if (hayAnterior) PillButton("Anterior", Icons.Rounded.ArrowBack, { indice += 1 })
                        if (haySiguiente) PillButton("Siguiente", Icons.Rounded.ArrowForward, { indice -= 1 })
                    }
                }
            }
            else -> Box(
                Modifier.fillMaxSize().pointerInput(modo, total) {
                    detectTapGestures { o ->
                        val tercio = size.width / 3f
                        val adelante = if (modo == 2) o.x < tercio else o.x > 2 * tercio
                        val atras = if (modo == 2) o.x > 2 * tercio else o.x < tercio
                        when {
                            adelante -> scope.launch { pagerState.animateScrollToPage((pagerState.currentPage + 1).coerceAtMost(total - 1)) }
                            atras -> scope.launch { pagerState.animateScrollToPage((pagerState.currentPage - 1).coerceAtLeast(0)) }
                            else -> barras = !barras
                        }
                    }
                },
            ) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    reverseLayout = modo == 2,
                    beyondViewportPageCount = 1,
                ) { i -> PaginaOnline(f, cap, i, motor, ajustar = true) }
            }
        }

        // Número de página cuando las barras están ocultas.
        if (!barras && total != null && total > 0) {
            LText(
                "${actual + 1} / $total", LectorType.Label,
                Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 6.dp)
                    .clip(CircleShape).background(Color(0x99000000)).padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }

        // ── Barra superior ──
        AnimatedVisibility(
            visible = barras, modifier = Modifier.align(Alignment.TopCenter),
            enter = fadeIn(tween(220)) + slideInVertically(tween(300)) { -it }, exit = fadeOut(tween(160)) + slideOutVertically(tween(240)) { -it },
        ) {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp)
                    .clip(RoundedCornerShape(28.dp)).background(LectorColors.Glass)
                    .border(1.dp, LectorColors.GlassBorder, RoundedCornerShape(28.dp))
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(44.dp).pressable(onBack).clip(CircleShape), Alignment.Center) {
                    Icon(Icons.Rounded.ArrowBack, "Atrás", tint = LectorColors.Text)
                }
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    LText(titulo.ifBlank { f.nombre }, LectorType.Section.copy(fontSize = 19.sp), maxLines = 1)
                    LText(
                        cap.nombre.ifBlank { "Capítulo ${numeroTexto(cap.numero)}" }, LectorType.Label,
                        color = LectorColors.Muted, maxLines = 1,
                    )
                }
            }
        }

        // ── Barra inferior: avance + acciones ──
        AnimatedVisibility(
            visible = barras, modifier = Modifier.align(Alignment.BottomCenter),
            enter = fadeIn(tween(220)) + slideInVertically(tween(300)) { it }, exit = fadeOut(tween(160)) + slideOutVertically(tween(240)) { it },
        ) {
            Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BotonCapitulo(Icons.Rounded.KeyboardArrowLeft, "Capítulo anterior", hayAnterior) { indice += 1 }
                    Spacer(Modifier.width(8.dp))
                    Row(
                        Modifier.weight(1f).clip(CircleShape).background(LectorColors.Glass).padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val n = total ?: 0
                        LText("${if (n > 0) actual + 1 else 0}", LectorType.Label, Modifier.widthIn(min = 22.dp))
                        if (n > 1) {
                            Slider(
                                value = actual.toFloat(),
                                onValueChange = { actual = it.roundToInt(); irA(actual) },
                                valueRange = 0f..(n - 1).toFloat(),
                                steps = 0,
                                colors = SliderDefaults.colors(
                                    thumbColor = LectorColors.Text,
                                    activeTrackColor = MaterialTheme.colorScheme.primary,
                                    inactiveTrackColor = LectorColors.PillFill,
                                ),
                                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                            )
                        } else Spacer(Modifier.weight(1f))
                        LText("$n", LectorType.Label, Modifier.widthIn(min = 22.dp), maxLines = 1)
                    }
                    Spacer(Modifier.width(8.dp))
                    BotonCapitulo(Icons.Rounded.KeyboardArrowRight, "Capítulo siguiente", haySiguiente) { indice -= 1 }
                }
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)
                        .clip(RoundedCornerShape(28.dp)).background(LectorColors.Glass)
                        .border(1.dp, LectorColors.GlassBorder, RoundedCornerShape(28.dp)).padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    AccionLector(Icons.Rounded.List, "Capítulos") { dialogoCaps = true }
                    AccionLector(Icons.Rounded.ArrowForward, "Navegador") {
                        val u = urlCapitulo(f, cap)
                        if (u == null) Toast.makeText(ctx, "Esta fuente no da la dirección web", Toast.LENGTH_SHORT).show()
                        else runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    }
                    AccionLector(Icons.Rounded.Share, "Compartir") {
                        val u = urlCapitulo(f, cap) ?: "$titulo - ${cap.nombre}"
                        val envio = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, u)
                        runCatching { ctx.startActivity(Intent.createChooser(envio, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    }
                    AccionLector(Icons.Rounded.Refresh, "Recargar") { recarga++ }
                    AccionLector(Icons.Rounded.Settings, "Ajustes") { dialogoAjustes = true }
                }
            }
        }
    }

    if (dialogoCaps) {
        AlertDialog(
            onDismissRequest = { dialogoCaps = false },
            title = { Text("Capítulos") },
            text = {
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    itemsIndexed(capitulos) { i, c ->
                        val leido = ext.esLeido(f.id, c.url)
                        Row(
                            Modifier.fillMaxWidth().clickable { indice = i; dialogoCaps = false }.padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                c.nombre.ifBlank { "Capítulo ${numeroTexto(c.numero)}" },
                                Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                                fontWeight = if (i == indice) FontWeight.Bold else FontWeight.Normal,
                                color = if (i == indice) MaterialTheme.colorScheme.primary
                                else if (leido) LectorColors.Muted else LectorColors.Text,
                            )
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { dialogoCaps = false }) { Text("Cerrar") } },
        )
    }

    if (dialogoAjustes) {
        AlertDialog(
            onDismissRequest = { dialogoAjustes = false },
            title = { Text("Modo de lectura") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ChipSel("Tira vertical (webtoon)", modo == 0) { ext.cambiarModoLector(0) }
                    ChipSel("Paginado, de izquierda a derecha", modo == 1) { ext.cambiarModoLector(1) }
                    ChipSel("Paginado, de derecha a izquierda (manga)", modo == 2) { ext.cambiarModoLector(2) }
                }
            },
            confirmButton = { TextButton(onClick = { dialogoAjustes = false }) { Text("Aceptar") } },
        )
    }
}

@Composable
private fun BotonCapitulo(icono: androidx.compose.ui.graphics.vector.ImageVector, desc: String, activo: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(52.dp).then(if (activo) Modifier.pressable(onClick) else Modifier)
            .clip(CircleShape).background(LectorColors.Glass),
        Alignment.Center,
    ) { Icon(icono, desc, tint = if (activo) LectorColors.Text else LectorColors.Muted.copy(alpha = 0.4f)) }
}

@Composable
private fun AccionLector(icono: androidx.compose.ui.graphics.vector.ImageVector, texto: String, onClick: () -> Unit) {
    Column(Modifier.pressable(onClick).padding(horizontal = 10.dp, vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icono, texto, tint = LectorColors.Text)
        LText(texto, LectorType.Label.copy(fontSize = 11.sp), maxLines = 1, color = LectorColors.Muted)
    }
}

@Composable
private fun PaginaOnline(f: FuenteUI, cap: CapituloFuente, i: Int, motor: MotorFuentes, ajustar: Boolean) {
    val clave = "${f.id}|${cap.url}|$i"
    var img by remember(clave) { mutableStateOf(CachePaginas.cache.get(clave)) }
    var fallo by remember(clave) { mutableStateOf<String?>(null) }
    var intento by remember(clave) { mutableStateOf(0) }

    LaunchedEffect(clave, intento) {
        if (img != null) return@LaunchedEffect
        fallo = null
        runCatching { decodificar(motor.imagenPagina(f, cap, i)) ?: error("Imagen no válida") }
            .onSuccess { img = it; CachePaginas.cache.put(clave, it) }
            .onFailure { fallo = it.message ?: "Error al cargar la página" }
    }

    val b = img
    if (b != null) {
        if (ajustar) Image(b, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        else Image(b, null, Modifier.fillMaxWidth(), contentScale = ContentScale.FillWidth)
    } else {
        Box(
            (if (ajustar) Modifier.fillMaxSize() else Modifier.fillMaxWidth().height(480.dp))
                .background(Color(0xFF0C0C0C)).pressable { if (fallo != null) intento++ },
            Alignment.Center,
        ) {
            if (fallo == null) CircularProgressIndicator(Modifier.size(24.dp))
            else LText("${i + 1}: $fallo\nToca para reintentar", LectorType.Label, Modifier.padding(24.dp), color = Color(0xFFE57373))
        }
    }
}
