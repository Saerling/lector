package com.example.lector.ui

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.example.lector.ext.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

private val Rojo = Color(0xFFE57373)

/** Pestaña "Explorar", como en Komikku: Fuentes · Novedades · Extensiones · Migrar. */
@Composable
fun ExplorarScreen(
    ext: ExtManager,
    onRuta: (Ruta) -> Unit,
    onFuente: (FuenteUI) -> Unit,
    onExt: (ExtInstalada) -> Unit,
) {
    var sub by rememberSaveable { mutableStateOf(0) }
    var consulta by rememberSaveable { mutableStateOf("") }
    var buscando by rememberSaveable { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var dialogoIdiomas by remember { mutableStateOf(false) }
    var porConfiar by remember { mutableStateOf<ExtInstalada?>(null) }
    val acento = MaterialTheme.colorScheme.primary

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 24.dp, end = 8.dp, top = 12.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LText("Explorar", LectorType.Display, Modifier.weight(1f))
            if (sub == 2) {
                IconButton(onClick = { buscando = !buscando; if (!buscando) consulta = "" }) {
                    Icon(Icons.Rounded.Search, "Buscar", tint = LectorColors.Text)
                }
            }
            if (sub == 0 || sub == 2) {
                val forma = RoundedCornerShape(8.dp)
                val color = if (ext.mostrarNsfw) acento else LectorColors.Muted
                Box(
                    Modifier.padding(horizontal = 8.dp).size(30.dp).pressable { ext.alternarNsfw() }
                        .clip(forma).border(2.dp, color, forma),
                    Alignment.Center
                ) { LText("18", LectorType.Body.copy(fontWeight = FontWeight.Bold, fontSize = 13.sp), color = color) }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "Más", tint = LectorColors.Text) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Listados de extensiones") }, onClick = { menu = false; onRuta(Ruta.ListadosExt) })
                        DropdownMenuItem(text = { Text("Idiomas") }, onClick = { menu = false; dialogoIdiomas = true })
                        DropdownMenuItem(text = { Text("Actualizar lista") }, onClick = { menu = false; ext.actualizarLista() })
                    }
                }
            }
        }

        // Pestañas con subrayado.
        Row(Modifier.fillMaxWidth()) {
            listOf("Fuentes", "Novedades", "Extensiones", "Migrar").forEachIndexed { i, titulo ->
                val sel = i == sub
                Column(
                    Modifier.weight(1f).clickable { sub = i; consulta = ""; buscando = false },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    LText(
                        titulo, LectorType.Body.copy(fontSize = 15.sp), Modifier.padding(vertical = 14.dp),
                        color = if (sel) acento else LectorColors.Text, maxLines = 1
                    )
                    Box(
                        Modifier.fillMaxWidth(0.7f).height(3.dp).clip(CircleShape)
                            .background(if (sel) acento else Color.Transparent)
                    )
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0x22FFFFFF)))

        Box(Modifier.weight(1f)) {
            when (sub) {
                0 -> FuentesTab(ext, consulta, { consulta = it }, onFuente) { sub = 2 }
                1 -> NovedadesTab(ext, onFuente)
                2 -> ExtensionesTab(
                    ext, consulta, buscando, { consulta = it }, onExt,
                    onConfiar = { porConfiar = it }, onRepos = { onRuta(Ruta.ListadosExt) }
                )
                else -> MigrarTab()
            }
        }
    }

    porConfiar?.let { e ->
        AlertDialog(
            onDismissRequest = { porConfiar = null },
            title = { Text("¿Confiar en «${e.nombre}»?") },
            text = {
                Text("Una extensión es código de terceros que se ejecuta con los permisos de esta app. Confía solo en las que vengan de un listado que conozcas.")
            },
            confirmButton = { TextButton(onClick = { ext.confiar(e); porConfiar = null }) { Text("Confiar") } },
            dismissButton = { TextButton(onClick = { porConfiar = null }) { Text("Cancelar") } },
        )
    }

    if (dialogoIdiomas) {
        val todos = (ext.disponibles.map { it.lang } + ext.instaladas.map { it.lang } + ext.idiomas + "all")
            .filter { it.isNotBlank() }.distinct().sortedBy { nombreIdioma(it) }
        AlertDialog(
            onDismissRequest = { dialogoIdiomas = false },
            title = { Text("Idiomas") },
            text = {
                Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState())) {
                    todos.forEach { l ->
                        Row(
                            Modifier.fillMaxWidth().clickable { ext.alternarIdioma(l) }.padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(checked = l in ext.idiomas, onCheckedChange = null)
                            Spacer(Modifier.width(12.dp))
                            Text("${bandera(l)}  ${nombreIdioma(l)}")
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { dialogoIdiomas = false }) { Text("Listo") } },
        )
    }
}

// ───────────────────────── Fuentes ─────────────────────────

@Composable
private fun FuentesTab(
    ext: ExtManager, consulta: String, onConsulta: (String) -> Unit,
    onFuente: (FuenteUI) -> Unit, irExtensiones: () -> Unit,
) {
    val todas = ext.fuentes
    val visibles = todas.filter {
        (ext.mostrarNsfw || !it.nsfw) && it.lang in ext.idiomas &&
            (consulta.isBlank() || it.nombre.contains(consulta.trim(), ignoreCase = true))
    }
    val ancladas = visibles.filter { it.id.toString() in ext.fijadas }
    val usadas = ext.recientes.mapNotNull { r -> visibles.firstOrNull { it.id.toString() == r } }.take(1)
    val resto = visibles.filterNot { it.id.toString() in ext.fijadas }
        .groupBy { it.lang }.toSortedMap(compareBy<String> { nombreIdioma(it) })

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.padding(horizontal = 24.dp, vertical = 12.dp)) { CampoBusqueda(consulta, onConsulta, "Buscar fuentes") }
        if (todas.isEmpty()) {
            Vacio(
                "Todavía no hay fuentes. Instala una extensión, pulsa «Confiar» y sus fuentes aparecerán aquí.",
                "Ir a Extensiones", irExtensiones
            )
        } else {
            LazyColumn(contentPadding = PaddingValues(bottom = 190.dp)) {
                if (usadas.isNotEmpty()) {
                    item { TituloSeccion("Usado recientemente") }
                    items(usadas) { FilaFuente(it, ext, onFuente) }
                }
                if (ancladas.isNotEmpty()) {
                    item { TituloSeccion("Ancladas") }
                    items(ancladas) { FilaFuente(it, ext, onFuente) }
                }
                resto.forEach { (lang, lista) ->
                    item { TituloSeccion(nombreIdioma(lang)) }
                    items(lista) { FilaFuente(it, ext, onFuente) }
                }
            }
        }
    }
}

@Composable
private fun FilaFuente(f: FuenteUI, ext: ExtManager, onFuente: (FuenteUI) -> Unit) {
    val fijada = f.id.toString() in ext.fijadas
    Row(
        Modifier.fillMaxWidth().clickable { onFuente(f) }.padding(start = 24.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconoExt(f.pkg, null, Modifier.size(48.dp))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            LText(f.nombre, LectorType.Section.copy(fontSize = 17.sp), maxLines = 1)
            Subtitulo(listOf("${bandera(f.lang)} ${nombreIdioma(f.lang)}"), f.nsfw)
        }
        IconButton(onClick = { ext.alternarFijada(f.id) }) {
            Icon(
                Icons.Rounded.Star, if (fijada) "Desanclar" else "Anclar",
                tint = if (fijada) MaterialTheme.colorScheme.primary else LectorColors.Muted
            )
        }
    }
}

// ───────────────────────── Novedades ─────────────────────────

@Composable
private fun NovedadesTab(ext: ExtManager, onFuente: (FuenteUI) -> Unit) {
    val motor = Motor.actual
    val fijas = ext.fuentes.filter { it.id.toString() in ext.fijadas && (ext.mostrarNsfw || !it.nsfw) }
    when {
        fijas.isEmpty() -> Vacio("Ancla fuentes con la estrella (pestaña Fuentes) para ver aquí sus novedades.")
        motor == null -> Vacio("Las novedades necesitan el motor de fuentes, que se conecta en el siguiente paso del proyecto (mira HANDOFF.md).")
        else -> LazyColumn(contentPadding = PaddingValues(bottom = 190.dp)) {
            items(fijas) { NovedadesFuente(it, motor, onFuente) }
        }
    }
}

@Composable
private fun NovedadesFuente(f: FuenteUI, motor: MotorFuentes, onFuente: (FuenteUI) -> Unit) {
    var estado by remember(f.id) { mutableStateOf<List<MangaFuente>?>(null) }
    var fallo by remember(f.id) { mutableStateOf(false) }
    LaunchedEffect(f.id) {
        runCatching { motor.recientes(f, 1).mangas.take(12) }
            .onSuccess { estado = it }
            .onFailure { fallo = true }
    }
    Column(Modifier.padding(bottom = 12.dp)) {
        Row(
            Modifier.fillMaxWidth().clickable { onFuente(f) }.padding(horizontal = 24.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                LText(f.nombre, LectorType.Section.copy(fontSize = 22.sp), maxLines = 1)
                LText(nombreIdioma(f.lang), LectorType.Body.copy(fontSize = 15.sp), color = LectorColors.Muted)
            }
            Icon(Icons.Rounded.KeyboardArrowRight, null, tint = LectorColors.Text, modifier = Modifier.size(32.dp))
        }
        val lista = estado
        when {
            fallo -> LText("No se pudieron cargar las novedades", LectorType.Label, Modifier.padding(horizontal = 24.dp), color = Rojo)
            lista == null -> Box(Modifier.fillMaxWidth().height(120.dp), Alignment.Center) { CircularProgressIndicator(Modifier.size(24.dp)) }
            else -> LazyRow(contentPadding = PaddingValues(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(lista) { m ->
                    Column(Modifier.width(124.dp).pressable { onFuente(f) }) {
                        ImagenRemota(
                            m.portadaUrl,
                            Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(RoundedCornerShape(12.dp)),
                            motor.cabeceras(f)
                        )
                        Spacer(Modifier.height(6.dp))
                        LText(m.titulo, LectorType.Body, maxLines = 3)
                    }
                }
            }
        }
    }
}

// ───────────────────────── Extensiones ─────────────────────────

@Composable
private fun ExtensionesTab(
    ext: ExtManager, consulta: String, buscando: Boolean, onConsulta: (String) -> Unit,
    onDetalle: (ExtInstalada) -> Unit, onConfiar: (ExtInstalada) -> Unit, onRepos: () -> Unit,
) {
    val q = consulta.trim()
    fun coincide(n: String) = q.isEmpty() || n.contains(q, ignoreCase = true)
    val inst = ext.instaladas.filter { coincide(it.nombre) && (ext.mostrarNsfw || !it.nsfw) }
    val pendientes = inst.filter { it.actualizacion != null }
    val alDia = inst.filter { it.actualizacion == null }
    val pkgsInstalados = ext.instaladas.map { it.pkg }.toSet()
    val disp = ext.disponibles
        .filter { it.pkg !in pkgsInstalados && coincide(it.nombre) && (ext.mostrarNsfw || !it.nsfw) && it.lang in ext.idiomas }
        .sortedBy { it.nombre.lowercase() }
        .groupBy { it.lang }.toSortedMap(compareBy<String> { nombreIdioma(it) })

    Column(Modifier.fillMaxSize()) {
        if (buscando) Box(Modifier.padding(horizontal = 24.dp, vertical = 12.dp)) { CampoBusqueda(consulta, onConsulta, "Buscar extensiones") }
        if (ext.cargando) LinearProgressIndicator(Modifier.fillMaxWidth())
        LazyColumn(contentPadding = PaddingValues(bottom = 190.dp)) {
            if (ext.repos.none { it.activo }) {
                item { Aviso("No tienes ningún listado de extensiones activo.", "Añadir listado", onRepos) }
            }
            if (ext.errores.isNotEmpty()) {
                item {
                    Column(Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
                        ext.errores.forEach { LText(it, LectorType.Label, color = Rojo) }
                    }
                }
            }
            if (pendientes.isNotEmpty()) {
                item { TituloSeccion("Actualizaciones pendientes (${pendientes.size})") }
                items(pendientes) { FilaInstalada(it, ext, onDetalle, onConfiar) }
            }
            if (alDia.isNotEmpty()) {
                item { TituloSeccion("Instalado") }
                items(alDia) { FilaInstalada(it, ext, onDetalle, onConfiar) }
            }
            disp.forEach { (lang, lista) ->
                item { TituloSeccion(nombreIdioma(lang)) }
                items(lista) { FilaDisponible(it, ext) }
            }
        }
    }
}

@Composable
private fun FilaInstalada(e: ExtInstalada, ext: ExtManager, onDetalle: (ExtInstalada) -> Unit, onConfiar: (ExtInstalada) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onDetalle(e) }.padding(start = 24.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconoExt(e.pkg, null, Modifier.size(48.dp))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            LText(e.nombre, LectorType.Section.copy(fontSize = 17.sp), maxLines = 1)
            Subtitulo(
                listOf("${bandera(e.lang)} ${nombreIdioma(e.lang)}", e.versionName + (e.repoSigla?.let { " @$it" } ?: "")),
                e.nsfw
            )
            if (!e.confiable) LText("Sin confianza", LectorType.Label, color = Rojo)
        }
        val pr = ext.progreso[e.pkg]
        when {
            pr != null -> EstadoProgreso(pr) { ext.limpiarError(e.pkg) }
            !e.confiable -> Pildora("Confiar") { onConfiar(e) }
            e.actualizacion != null -> Pildora("Actualizar") { e.actualizacion?.let { ext.instalar(it) } }
        }
        IconButton(onClick = { onDetalle(e) }) { Icon(Icons.Rounded.Settings, "Ajustes", tint = LectorColors.Text) }
    }
}

@Composable
private fun FilaDisponible(d: ExtDisponible, ext: ExtManager) {
    Row(
        Modifier.fillMaxWidth().padding(start = 24.dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconoExt(d.pkg, d.iconoUrl, Modifier.size(48.dp))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            LText(d.nombre, LectorType.Section.copy(fontSize = 17.sp), maxLines = 1)
            Subtitulo(listOf("${bandera(d.lang)} ${nombreIdioma(d.lang)}", "${d.versionName} @${d.repoSigla}"), d.nsfw)
        }
        val pr = ext.progreso[d.pkg]
        when {
            pr != null -> EstadoProgreso(pr) { ext.limpiarError(d.pkg) }
            d.apkUrl.isBlank() -> LText("No disponible", LectorType.Label, color = LectorColors.Muted)
            else -> Pildora("Instalar") { ext.instalar(d) }
        }
    }
}

@Composable
private fun EstadoProgreso(pr: Progreso, alReintentar: () -> Unit) {
    when (pr.fase) {
        Fase.DESCARGANDO -> LText("Descargando ${pr.porcentaje}%", LectorType.Label, color = LectorColors.Muted)
        Fase.INSTALANDO -> LText("Instalando…", LectorType.Label, color = LectorColors.Muted)
        Fase.ERROR -> LText(
            "Error · tocar", LectorType.Label, Modifier.clickable(onClick = alReintentar).padding(8.dp), color = Rojo
        )
    }
}

// ───────────────────────── Migrar ─────────────────────────

@Composable
private fun MigrarTab() {
    Vacio("Aquí podrás mover tus series de una fuente a otra. Todavía no tienes series vinculadas a una fuente.")
}

// ───────────────────────── Piezas comunes ─────────────────────────

@Composable
private fun Pildora(texto: String, onClick: () -> Unit) {
    val acento = MaterialTheme.colorScheme.primary
    Box(
        Modifier.pressable(onClick).clip(CircleShape).background(acento.copy(alpha = 0.22f))
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) { LText(texto, LectorType.Body, color = acento) }
}

@Composable
private fun Subtitulo(partes: List<String>, nsfw: Boolean) {
    val texto = buildAnnotatedString {
        append(partes.joinToString("  •  "))
        if (nsfw) {
            append("  •  ")
            withStyle(SpanStyle(color = Rojo)) { append("+18") }
        }
    }
    Text(
        texto, color = LectorColors.Muted, style = LectorType.Label.copy(fontSize = 13.sp),
        maxLines = 1, overflow = TextOverflow.Ellipsis
    )
}

@Composable
internal fun CampoBusqueda(valor: String, alCambiar: (String) -> Unit, pista: String) {
    BasicTextField(
        value = valor, onValueChange = alCambiar, singleLine = true,
        textStyle = LectorType.Body.copy(color = LectorColors.Text),
        cursorBrush = SolidColor(LectorColors.Text),
        decorationBox = { inner ->
            Row(
                Modifier.fillMaxWidth().clip(CircleShape).background(LectorColors.Card)
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.Search, null, tint = LectorColors.Muted, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(12.dp))
                Box(Modifier.weight(1f)) {
                    if (valor.isEmpty()) LText(pista, LectorType.Body, color = LectorColors.Muted)
                    inner()
                }
            }
        }
    )
}

@Composable
private fun Vacio(texto: String, accion: String? = null, onAccion: (() -> Unit)? = null) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 40.dp).padding(bottom = 120.dp),
        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(texto, color = LectorColors.Muted, style = LectorType.Body, textAlign = TextAlign.Center)
        if (accion != null && onAccion != null) {
            Spacer(Modifier.height(16.dp))
            PillButton(accion, Icons.Rounded.KeyboardArrowRight, onAccion)
        }
    }
}

@Composable
private fun Aviso(texto: String, accion: String, onAccion: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(24.dp)) {
        LText(texto, LectorType.Body, color = LectorColors.Muted)
        Spacer(Modifier.height(12.dp))
        PillButton(accion, Icons.Rounded.Add, onAccion)
    }
}

/** Icono de una extensión: el de la app instalada o, si no, el que publica el listado. */
@Composable
fun IconoExt(pkg: String, url: String?, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val local = remember(pkg) {
        runCatching { ctx.packageManager.getApplicationIcon(pkg).toBitmap(128, 128).asImageBitmap() }.getOrNull()
    }
    val forma = RoundedCornerShape(12.dp)
    if (local != null) Image(local, null, modifier.clip(forma), contentScale = ContentScale.Crop)
    else ImagenRemota(url, modifier.clip(forma))
}

/** Cargador de imágenes mínimo (sin librerías): descarga, reduce y guarda en memoria. */
object Imagenes {
    private val cache = LruCache<String, ImageBitmap>(40)

    suspend fun cargar(url: String, cabeceras: Map<String, String> = emptyMap(), maxLado: Int = 512): ImageBitmap? {
        cache.get(url)?.let { return it }
        return withContext(Dispatchers.IO) {
            runCatching {
                val c = URL(url).openConnection() as HttpURLConnection
                c.connectTimeout = 15_000
                c.readTimeout = 30_000
                c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) Lector/1.0")
                cabeceras.forEach { (k, v) -> c.setRequestProperty(k, v) }
                val bytes = try { c.inputStream.use { it.readBytes() } } finally { c.disconnect() }
                val medidas = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, medidas)
                var muestra = 1
                while (maxOf(medidas.outWidth, medidas.outHeight) / (muestra * 2) >= maxLado) muestra *= 2
                val opciones = BitmapFactory.Options().apply { inSampleSize = muestra }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opciones)?.asImageBitmap()
            }.getOrNull()?.also { cache.put(url, it) }
        }
    }
}

@Composable
fun ImagenRemota(url: String?, modifier: Modifier = Modifier, cabeceras: Map<String, String> = emptyMap()) {
    var img by remember(url) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(url) { if (!url.isNullOrBlank()) img = Imagenes.cargar(url, cabeceras) }
    Box(modifier.background(LectorColors.CardHigh)) {
        val i = img
        if (i != null) Image(bitmap = i, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
    }
}
