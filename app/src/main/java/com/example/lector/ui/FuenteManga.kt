package com.example.lector.ui

import android.graphics.BitmapFactory
import android.util.LruCache
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
                            LText(c.nombre.ifBlank { "Capítulo ${numeroTexto(c.numero)}" }, LectorType.Body, maxLines = 2)
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

@Composable
fun LeerOnlineScreen(
    ext: ExtManager,
    fuenteId: Long,
    capitulos: List<CapituloFuente>,
    indiceInicial: Int,
    onBack: () -> Unit,
) {
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

    var paginas by remember(cap.url) { mutableStateOf<Int?>(null) }
    var error by remember(cap.url) { mutableStateOf<String?>(null) }
    var recarga by remember(cap.url) { mutableStateOf(0) }

    LaunchedEffect(cap.url, recarga) {
        paginas = null; error = null
        runCatching { motor.cantidadPaginas(f, cap) }
            .onSuccess { paginas = it }
            .onFailure { error = it.message ?: "No se pudieron cargar las páginas" }
    }

    // Al cambiar de capítulo se vuelve arriba (la lista se recrea por la clave).
    key(cap.url) {
        LazyColumn(Modifier.fillMaxSize().background(Color.Black)) {
            item {
                Row(
                    Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(44.dp).pressable(onBack).clip(CircleShape).background(LectorColors.PillFill), Alignment.Center) {
                        Icon(Icons.Rounded.ArrowBack, "Atrás", tint = LectorColors.Text)
                    }
                    Spacer(Modifier.width(14.dp))
                    LText(cap.nombre.ifBlank { "Capítulo ${numeroTexto(cap.numero)}" }, LectorType.Label, maxLines = 2)
                }
            }
            val total = paginas
            if (total != null) {
                items(total) { i -> PaginaOnline(f, cap, i, motor) }
                item {
                    Row(
                        Modifier.fillMaxWidth().navigationBarsPadding().padding(24.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                    ) {
                        // Las fuentes suelen listar lo más nuevo primero: "siguiente" = índice anterior.
                        if (indice < capitulos.lastIndex) PillButton("Anterior", Icons.Rounded.ArrowBack, { indice += 1 })
                        if (indice > 0) PillButton("Siguiente", Icons.Rounded.PlayArrow, { indice -= 1 })
                    }
                }
            } else {
                item {
                    Box(Modifier.fillMaxWidth().padding(48.dp), Alignment.Center) {
                        if (error == null) CircularProgressIndicator(Modifier.size(28.dp))
                        else Column(horizontalAlignment = Alignment.CenterHorizontally) {
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

@Composable
private fun PaginaOnline(f: FuenteUI, cap: CapituloFuente, i: Int, motor: MotorFuentes) {
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
        Image(b, null, Modifier.fillMaxWidth(), contentScale = ContentScale.FillWidth)
    } else {
        Box(
            Modifier.fillMaxWidth().height(480.dp).background(Color(0xFF0C0C0C)).pressable { if (fallo != null) intento++ },
            Alignment.Center,
        ) {
            if (fallo == null) CircularProgressIndicator(Modifier.size(24.dp))
            else LText("${i + 1}: $fallo\nToca para reintentar", LectorType.Label, Modifier.padding(24.dp), color = Color(0xFFE57373))
        }
    }
}
