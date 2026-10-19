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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
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

/**
 * Descargas de capítulos (.cbz → biblioteca). Viven fuera de las pantallas: si sales de la ficha la descarga
 * sigue, y al volver ves su progreso. Tocar otra vez el botón de un capítulo que se está descargando la CANCELA.
 */
object GestorDescargas {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val trabajos = HashMap<String, Job>()

    /** "fuenteId|urlCapítulo" → fracción descargada (0f = preparando). Las pantallas lo leen y se repintan. */
    val progreso = mutableStateMapOf<String, Float>()

    /** Sube cada vez que termina o se borra una descarga, para que las pantallas vuelvan a mirar cuáles hay. */
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
                withContext(Dispatchers.IO) { almacen.guardarDescarga(archivo, f.id, c.url) }
                Toast.makeText(app, "Capítulo descargado", Toast.LENGTH_SHORT).show()
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
    var webUrl by remember { mutableStateOf<String?>(null) }

    // Biblioteca y categorías
    var enBiblioteca by remember { mutableStateOf(almacen.comicEnLinea(fuenteId, manga.url)) }
    var guardando by remember { mutableStateOf(false) }
    var dialogoCats by remember { mutableStateOf(false) }
    var editandoCats by remember { mutableStateOf(false) }
    // Capítulo cuya descarga se quiere borrar.
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
        // Si está en la biblioteca, anota los capítulos nuevos (pestaña Actualizaciones).
        if (almacen.comicEnLinea(f.id, manga.url) != null) {
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
    // Estética de la ficha: el fondo y el acento toman el tono de la portada (si "Tema según la portada" está activo).
    var paleta by remember { mutableStateOf<TemaPortada?>(null) }
    var portadaLista by remember { mutableStateOf(false) }
    var masAntiguos by rememberSaveable { mutableStateOf(false) }
    val conTema = almacen.colorPortada
    val acento = (if (conTema) paleta?.acento else null) ?: MaterialTheme.colorScheme.primary
    val fondoAnim by animateColorAsState(
        (if (conTema) paleta?.fondo else null) ?: Color.Black, tween(800), label = "fondoFicha",
    )
    val orden = remember(fi, masAntiguos) {
        fi?.capitulos?.withIndex()?.toList()?.let { if (masAntiguos) it.asReversed() else it } ?: emptyList()
    }

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
                val c = almacen.anadirEnLinea(f.id, manga.url, titulo, urlPortada, portada, fi?.capitulos?.size ?: 0, leidos)
                // Los capítulos que hay ahora son la base: solo lo que salga después contará como novedad.
                fi?.let { almacen.registrarCapitulos(f.id, manga.url, titulo, urlPortada, it.capitulos) }
                c
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
    val tituloFicha = fi?.detalle?.titulo ?: manga.titulo
    // La mayoría de fuentes listan el capítulo más nuevo primero: el primero a leer es el último sin leer.
    val sig = fi?.let { x -> (x.capitulos.lastIndex downTo 0).firstOrNull { !ext.esLeido(f.id, x.capitulos[it].url) } ?: 0 } ?: 0
    val paginaSig = fi?.capitulos?.getOrNull(sig)?.let { ext.paginaDe(f.id, it.url) } ?: 0
    val hayLeidos = fi?.capitulos?.any { ext.esLeido(f.id, it.url) } == true
    // WebView dentro de la app (como Komikku): la dirección la da la propia fuente; si no, se arma con su baseUrl.
    val abrirNavegador: () -> Unit = {
        val u = motor.urlManga(f, manga) ?: urlManga(f, manga)
        if (u == null) Toast.makeText(ctx, "Esta fuente no da la dirección web", Toast.LENGTH_SHORT).show()
        else webUrl = u
    }

    Box(Modifier.fillMaxSize().drawBehind { drawRect(fondoAnim) }) {
        LazyColumn(Modifier.fillMaxSize(), state = lista, contentPadding = PaddingValues(bottom = 60.dp)) {
            item {
                val lineas = listOfNotNull(
                    fi?.detalle?.autor,
                    fi?.let { estadoTexto(it.detalle.estado) },
                    f.nombre,
                    fi?.let { "${it.capitulos.size} capítulos" },
                )
                CabeceraFicha(
                    portada = fi?.detalle?.portadaUrl ?: manga.portadaUrl, cabeceras = motor.cabeceras(f),
                    titulo = tituloFicha, lineas = lineas, acento = acento, fondo = fondoAnim,
                    scroll = scrollPx, portadaLista = portadaLista,
                    onCargada = { portadaLista = true; if (conTema) paleta = temaDe(it) },
                    enBiblioteca = enBiblioteca != null,
                    textoPildora = if (enBiblioteca != null) "Categorías" else "Añadir a biblioteca",
                    onLeer = { fi?.let { onLeer(it.capitulos, sig, paginaSig) } },
                    onCorazon = { alternarBiblioteca() },
                    onPildora = { if (enBiblioteca != null) dialogoCats = true else alternarBiblioteca() },
                )
            }
            if (fi != null) {
                // Tarjetas deslizables: último capítulo y sinopsis.
                item {
                    val ultimo = fi.capitulos.firstOrNull()
                    val sinopsis = fi.detalle.descripcion?.takeIf { it.isNotBlank() }
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
                                            Modifier.size(64.dp).clip(RoundedCornerShape(16.dp)), motor.cabeceras(f),
                                        )
                                        Spacer(Modifier.width(14.dp))
                                        Column(Modifier.weight(1f)) {
                                            LText(
                                                ultimo.nombre.ifBlank { "Capítulo ${numeroTexto(ultimo.numero)}" },
                                                LectorType.Section.copy(fontSize = 18.sp), maxLines = 2,
                                            )
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
                if (fi.detalle.generos.isNotEmpty()) {
                    item {
                        Row(
                            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) { fi.detalle.generos.forEach { ChipBorde(it, acento) } }
                    }
                }
                item {
                    Row(
                        Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 22.dp, bottom = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        LText("Capítulos", LectorType.Display.copy(fontSize = 30.sp, letterSpacing = (-1).sp))
                        Spacer(Modifier.width(12.dp))
                        LText("${fi.capitulos.size}", LectorType.Body, Modifier.weight(1f), color = LectorColors.Muted)
                        ConAcento(acento) {
                            ChipSel(if (masAntiguos) "Más antiguos" else "Más nuevos", true) { masAntiguos = !masAntiguos }
                        }
                    }
                }
                itemsIndexed(orden) { pos, iv ->
                    val i = iv.index
                    val c = iv.value
                    val clave = GestorDescargas.clave(f, c)
                    val paginaGuardada = ext.paginaDe(f.id, c.url)
                    val fraccion = GestorDescargas.progreso[clave]
                    val version = GestorDescargas.version.value // al cambiar, se vuelve a mirar el disco
                    val estado: EstadoDescarga = when {
                        fraccion != null -> EstadoDescarga.Descargando(fraccion)
                        version >= 0 && almacen.estaDescargado(f.id, c.url) -> EstadoDescarga.Listo
                        else -> EstadoDescarga.Libre
                    }
                    val leido = ext.esLeido(f.id, c.url)
                    val numero = numeroTexto(c.numero).ifEmpty { "#" }
                    val marcado = ext.esMarcado(f.id, c.url)
                    // Deslizar → leído / no leído · deslizar ← marcado / no marcado.
                    val accionLeido = AccionDeslizar(
                        if (leido) Iconos.OjoTachado else Icons.Rounded.Check,
                        if (leido) "Marcar como no leído" else "Marcar como leído",
                        acento,
                    ) {
                        if (leido) ext.desmarcarLeido(f.id, c.url) else ext.marcarLeido(f.id, c.url)
                        almacen.actualizarProgresoEnLinea(
                            f.id, manga.url, fi.capitulos.count { ext.esLeido(f.id, it.url) }, fi.capitulos.size,
                        )
                    }
                    val accionMarca = AccionDeslizar(
                        if (marcado) Iconos.MarcaBorde else Iconos.Marca,
                        if (marcado) "Quitar marca" else "Marcar capítulo",
                        ColorMarca,
                    ) { ext.alternarMarcado(f.id, c.url) }
                    FilaDeslizable(accionLeido, accionMarca, Modifier.entrada(pos)) {
                    Row(
                        Modifier.fillMaxWidth().pressable { onLeer(fi.capitulos, i, paginaGuardada) }
                            .padding(start = 24.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier.size(64.dp).clip(RoundedCornerShape(20.dp))
                                .background(acento.copy(alpha = if (leido) 0.10f else 0.24f)),
                            Alignment.Center,
                        ) {
                            LText(
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
                                    c.nombre.ifBlank { "Capítulo ${numeroTexto(c.numero)}" },
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
                        // Descargar el capítulo como .cbz y meterlo en la biblioteca.
                        // Mientras descarga, volver a tocar el botón cancela la descarga.
                        ConAcento(acento) {
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
                    Modifier.size(44.dp).pressable(abrirNavegador).clip(CircleShape).background(Color(0x66000000)),
                    Alignment.Center,
                ) { Icon(Icons.Rounded.ArrowForward, "Abrir en el navegador", tint = LectorColors.Text) }
            },
        )

        // WebView del sitio, encima de la ficha.
        webUrl?.let { u ->
            Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { } }) {
                WebViewScreen(u, tituloFicha) { webUrl = null }
            }
        }

        // Sinopsis completa.
        if (descExpandida) {
            AlertDialog(
                onDismissRequest = { descExpandida = false },
                title = { Text("Sinopsis") },
                text = { Column(Modifier.verticalScroll(rememberScrollState())) { Text(fi?.detalle?.descripcion.orEmpty()) } },
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

        quitarDescarga?.let { c ->
            AlertDialog(
                onDismissRequest = { quitarDescarga = null },
                title = { Text("¿Quitar la descarga?") },
                text = { Text("Se borrará el archivo descargado de «${c.nombre.ifBlank { "Capítulo ${numeroTexto(c.numero)}" }}». Podrás seguir leyéndolo online.") },
                confirmButton = {
                    TextButton(onClick = {
                        almacen.borrarDescarga(f.id, c.url)
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
    val forma = RoundedCornerShape(32.dp)
    Column(
        modifier.then(if (onClick != null) Modifier.pressable(onClick) else Modifier)
            .clip(forma).background(Color.White.copy(alpha = 0.06f))
            .border(1.5.dp, Color.White.copy(alpha = 0.16f), forma),
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
    portada: String?, cabeceras: Map<String, String>, titulo: String, lineas: List<String>,
    acento: Color, fondo: Color, scroll: () -> Float, portadaLista: Boolean,
    onCargada: (ImageBitmap) -> Unit,
    enBiblioteca: Boolean, textoPildora: String,
    onLeer: () -> Unit, onCorazon: () -> Unit, onPildora: () -> Unit,
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
            ImagenRemota(portada, Modifier.size(96.dp).clip(CircleShape), cabeceras)
            Spacer(Modifier.height(14.dp))
            LText(
                titulo, LectorType.Display.copy(fontSize = 44.sp, lineHeight = 46.sp, letterSpacing = (-1.5).sp),
                maxLines = 3,
            )
            Spacer(Modifier.height(6.dp))
            lineas.chunked(2).forEach { par ->
                LText(par.joinToString(" · "), LectorType.Body, color = Color(0xCCFFFFFF), maxLines = 1)
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

// Si el capítulo está descargado se lee del archivo; si no, de la red (misma pantalla, mismo progreso).
private suspend fun paginasDeCap(almacen: Almacen?, motor: MotorFuentes, f: FuenteUI, cap: CapituloFuente): Int {
    val local = almacen?.archivoDescarga(f.id, cap.url)
    if (local != null && local.exists()) {
        val n = runCatching { withContext(Dispatchers.IO) { Cbz.paginas(local).size } }.getOrDefault(0)
        if (n > 0) return n
    }
    return motor.cantidadPaginas(f, cap)
}

private suspend fun bytesDePagina(almacen: Almacen?, motor: MotorFuentes, f: FuenteUI, cap: CapituloFuente, i: Int): ByteArray {
    val local = almacen?.archivoDescarga(f.id, cap.url)
    if (local != null && local.exists()) {
        val bytes = runCatching {
            withContext(Dispatchers.IO) {
                val nombre = Cbz.paginas(local).getOrNull(i) ?: error("Página $i fuera de rango")
                ZipFile(local).use { z -> z.getInputStream(z.getEntry(nombre)).use { it.readBytes() } }
            }
        }.getOrNull()
        if (bytes != null) return bytes
    }
    return motor.imagenPagina(f, cap, i)
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
        runCatching { paginasDeCap(almacen, motor, f, cap) }
            .onSuccess { paginas = it }
            .onFailure { error = it.message ?: "No se pudieron cargar las páginas" }
    }
    // Al cambiar de capítulo (o terminar de cargar) se vuelve a la primera página;
    // solo la primera vez, si venimos del historial, se salta a la página guardada.
    var paginaInicialUsada by remember { mutableStateOf(false) }
    LaunchedEffect(cap.url, total) {
        if (total != null && total > 0) {
            val destino = (if (!paginaInicialUsada && indice == indiceInicial) paginaInicial else ext.paginaDe(f.id, cap.url))
                .coerceIn(0, total - 1)
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
        if (total == null || total <= 0) return@LaunchedEffect
        delay(600)
        // Guarda por qué página vas en este capítulo (si ya lo terminaste, se marca leído y no hace falta).
        if (actual >= total - 1) ext.marcarLeido(f.id, cap.url) else ext.guardarPagina(f.id, cap.url, actual)
        if (almacen == null || manga == null) return@LaunchedEffect
        almacen.registrarHistorialLinea(
            EntradaHistorial(
                fuenteId = f.id, urlManga = manga.url,
                titulo = titulo.ifBlank { manga.titulo },
                portadaUrl = manga.portadaUrl,
                capUrl = cap.url,
                capNombre = cap.nombre.trim().trimEnd(':').trim().ifBlank { "Capítulo ${numeroTexto(cap.numero)}" },
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
                items(total) { i -> PaginaOnline(f, cap, i, motor, almacen, ajustar = false) }
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
                ) { i -> PaginaOnline(f, cap, i, motor, almacen, ajustar = true) }
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
private fun PaginaOnline(f: FuenteUI, cap: CapituloFuente, i: Int, motor: MotorFuentes, almacen: Almacen?, ajustar: Boolean) {
    val clave = "${f.id}|${cap.url}|$i"
    var img by remember(clave) { mutableStateOf(CachePaginas.cache.get(clave)) }
    var fallo by remember(clave) { mutableStateOf<String?>(null) }
    var intento by remember(clave) { mutableStateOf(0) }

    LaunchedEffect(clave, intento) {
        if (img != null) return@LaunchedEffect
        fallo = null
        runCatching { decodificar(bytesDePagina(almacen, motor, f, cap, i)) ?: error("Imagen no válida") }
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
