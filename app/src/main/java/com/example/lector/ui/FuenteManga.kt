package com.example.lector.ui

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.lector.Almacen
import com.example.lector.ext.*
import kotlinx.coroutines.Dispatchers
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

// ───────────────────── Ficha del manga (online) ─────────────────────

@Composable
fun FichaMangaScreen(
    ext: ExtManager,
    fuenteId: Long,
    manga: MangaFuente,
    almacen: Almacen,
    alRecargar: () -> Unit,
    onBack: () -> Unit,
    onLeer: (List<CapituloFuente>, Int) -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val f = ext.fuentes.firstOrNull { it.id == fuenteId }
    val motor = Motor.actual

    var ficha by remember { mutableStateOf<FichaManga?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var cargando by remember { mutableStateOf(true) }
    var descargando by remember { mutableStateOf<Map<String, String>>(emptyMap()) } // url capítulo -> texto de progreso
    var descExpandida by remember { mutableStateOf(false) }
    var recarga by remember { mutableStateOf(0) }

    LaunchedEffect(fuenteId, manga.url, recarga) {
        if (f == null || motor == null) return@LaunchedEffect
        cargando = true; error = null
        runCatching { motor.ficha(f, manga) }
            .onSuccess { ficha = it }
            .onFailure { error = it.message ?: "No se pudo cargar la ficha" }
        cargando = false
    }

    Column(Modifier.fillMaxSize().background(LectorColors.Bg).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).pressable(onBack).clip(CircleShape).background(LectorColors.PillFill), Alignment.Center) {
                Icon(Icons.Rounded.ArrowBack, "Atrás", tint = LectorColors.Text)
            }
            Spacer(Modifier.width(14.dp))
            LText(ficha?.detalle?.titulo ?: manga.titulo, LectorType.Display.copy(fontSize = 22.sp), maxLines = 1)
        }

        if (f == null || motor == null) {
            LText("La fuente no está disponible.", LectorType.Body, Modifier.padding(24.dp), color = LectorColors.Muted)
            return@Column
        }

        val fi = ficha
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 40.dp)) {
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)) {
                    ImagenRemota(
                        fi?.detalle?.portadaUrl ?: manga.portadaUrl,
                        Modifier.width(120.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(14.dp)),
                        motor.cabeceras(f),
                    )
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        LText(fi?.detalle?.titulo ?: manga.titulo, LectorType.Display.copy(fontSize = 20.sp), maxLines = 4)
                        Spacer(Modifier.height(6.dp))
                        fi?.detalle?.autor?.let { LText(it, LectorType.Label, color = LectorColors.Muted, maxLines = 2) }
                        fi?.detalle?.let { d ->
                            d.artista?.takeIf { it != d.autor }
                                ?.let { LText(it, LectorType.Label, color = LectorColors.Muted, maxLines = 2) }
                        }
                        fi?.let { LText(estadoTexto(it.detalle.estado), LectorType.Label, color = LectorColors.Muted) }
                        LText(f.nombre, LectorType.Label, color = LectorColors.Muted, maxLines = 1)
                    }
                }
            }
            if (fi != null) {
                item {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)) {
                        if (fi.capitulos.isNotEmpty()) {
                            // La mayoría de fuentes listan el capítulo más nuevo primero: el primero a leer es el último.
                            PillButton("Empezar a leer", Icons.Rounded.PlayArrow, {
                                onLeer(fi.capitulos, fi.capitulos.lastIndex)
                            })
                        }
                    }
                }
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
                        LText(
                            fi.detalle.generos.joinToString(" · "), LectorType.Label,
                            Modifier.padding(horizontal = 24.dp, vertical = 8.dp), color = LectorColors.Muted,
                        )
                    }
                }
                item {
                    LText(
                        "${fi.capitulos.size} capítulos", LectorType.Label,
                        Modifier.padding(horizontal = 24.dp, vertical = 10.dp),
                    )
                }
                itemsIndexed(fi.capitulos) { i, c ->
                    val prog = descargando[c.url]
                    Row(
                        Modifier.fillMaxWidth().pressable { onLeer(fi.capitulos, i) }
                            .padding(horizontal = 24.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            LText(
                                c.nombre.ifBlank { "Capítulo ${numeroTexto(c.numero)}" }, LectorType.Body, maxLines = 2,
                                color = if (ext.esLeido(f.id, c.url)) LectorColors.Muted else LectorColors.Text,
                            )
                            val sub = listOfNotNull(
                                fechaTexto(c.fecha).takeIf { it.isNotEmpty() }, c.scanlator,
                            ).joinToString(" · ")
                            if (sub.isNotEmpty()) LText(sub, LectorType.Label, color = LectorColors.Muted, maxLines = 1)
                        }
                        Spacer(Modifier.width(10.dp))
                        if (prog != null) {
                            LText(prog, LectorType.Label, color = LectorColors.Muted)
                        } else {
                            // Descargar el capítulo como .cbz y meterlo en la biblioteca.
                            Box(
                                Modifier.pressable {
                                    scope.launch {
                                        descargando = descargando + (c.url to "0%")
                                        val archivo = File(ctx.cacheDir, "cap_${System.nanoTime()}.cbz")
                                        val resultado = runCatching {
                                            motor.descargarCbz(f, c, archivo) { h, t ->
                                                descargando = descargando + (c.url to "${h * 100 / t}%")
                                            }
                                            val titulo = "${fi.detalle.titulo} - ${c.nombre.ifBlank { numeroTexto(c.numero) }}"
                                                .replace(Regex("[\\\\/:*?\"<>|]"), "_")
                                            withContext(Dispatchers.IO) { almacen.importarArchivo(archivo, titulo) }
                                        }
                                        archivo.delete()
                                        descargando = descargando - c.url
                                        val ok = resultado.getOrNull() != null
                                        if (ok) alRecargar()
                                        Toast.makeText(
                                            ctx,
                                            if (ok) "Añadido a la biblioteca" else
                                                "No se pudo descargar: ${resultado.exceptionOrNull()?.message ?: "archivo vacío"}",
                                            Toast.LENGTH_LONG,
                                        ).show()
                                    }
                                }.clip(CircleShape).background(LectorColors.PillFill).padding(horizontal = 12.dp, vertical = 6.dp),
                                Alignment.Center,
                            ) { LText("CBZ", LectorType.Label) }
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
    // Al cambiar de capítulo (o terminar de cargar) se vuelve a la primera página.
    LaunchedEffect(cap.url, total) {
        if (total != null && total > 0) {
            runCatching { listState.scrollToItem(0) }
            runCatching { pagerState.scrollToPage(0) }
        }
    }
    // Página actual según el modo.
    LaunchedEffect(modo, total) {
        if (total == null || total == 0) return@LaunchedEffect
        if (modo == 0) snapshotFlow { listState.firstVisibleItemIndex }.collect { actual = it.coerceIn(0, total - 1) }
        else snapshotFlow { pagerState.currentPage }.collect { actual = it.coerceIn(0, total - 1) }
    }
    // Capítulo leído al llegar a la última página.
    LaunchedEffect(actual, total) {
        if (total != null && total > 0 && actual >= total - 1) ext.marcarLeido(f.id, cap.url)
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
            enter = fadeIn() + slideInVertically { -it }, exit = fadeOut() + slideOutVertically { -it },
        ) {
            Row(
                Modifier.fillMaxWidth().background(LectorColors.Glass).statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
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
            enter = fadeIn() + slideInVertically { it }, exit = fadeOut() + slideOutVertically { it },
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
                                steps = if (n in 3..80) n - 2 else 0,
                                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                            )
                        } else Spacer(Modifier.weight(1f))
                        LText("$n", LectorType.Label, Modifier.widthIn(min = 22.dp), maxLines = 1)
                    }
                    Spacer(Modifier.width(8.dp))
                    BotonCapitulo(Icons.Rounded.KeyboardArrowRight, "Capítulo siguiente", haySiguiente) { indice -= 1 }
                }
                Row(
                    Modifier.fillMaxWidth().background(LectorColors.Glass).padding(vertical = 8.dp),
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
