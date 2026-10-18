package com.example.lector.ui

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt
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
    onManga: (FuenteUI, MangaFuente) -> Unit,
) {
    var sub by rememberSaveable { mutableStateOf(0) }
    var reordenar by rememberSaveable { mutableStateOf(false) }
    var dialogoNovedades by remember { mutableStateOf(false) }
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
            if (sub == 1) {
                if (reordenar) {
                    IconButton(onClick = { reordenar = false }) { Icon(Icons.Rounded.Close, "Terminar", tint = LectorColors.Text) }
                } else {
                    IconButton(onClick = { dialogoNovedades = true }) { Icon(Icons.Rounded.Add, "Añadir fuente", tint = LectorColors.Text) }
                    IconButton(onClick = { reordenar = true }) { Icon(Icons.Rounded.Menu, "Ordenar", tint = LectorColors.Text) }
                }
            }
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

        // Pestañas en píldora: el indicador se desliza de una a otra.
        BoxWithConstraints(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                .clip(CircleShape).background(LectorColors.Card).padding(4.dp)
        ) {
            val titulos = listOf("Fuentes", "Novedades", "Extensiones", "Migrar")
            val ancho = maxWidth / titulos.size
            val x by animateDpAsState(ancho * sub, spring(dampingRatio = 0.75f, stiffness = 400f), label = "tabExplorar")
            Box(Modifier.offset(x = x).width(ancho).height(40.dp).clip(CircleShape).background(acento.copy(alpha = 0.22f)))
            Row(Modifier.fillMaxWidth()) {
                titulos.forEachIndexed { i, titulo ->
                    val color by animateColorAsState(if (i == sub) acento else LectorColors.Text, label = "tabColor")
                    Box(
                        Modifier.weight(1f).height(40.dp).clip(CircleShape)
                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                                sub = i; consulta = ""; buscando = false; reordenar = false
                            },
                        Alignment.Center
                    ) { LText(titulo, LectorType.Body.copy(fontSize = 14.sp), color = color, maxLines = 1) }
                }
            }
        }

        Box(Modifier.weight(1f)) {
            when (sub) {
                0 -> FuentesTab(ext, consulta, { consulta = it }, onFuente) { sub = 2 }
                1 -> NovedadesTab(ext, reordenar, onManga, onFuente) { dialogoNovedades = true }
                2 -> ExtensionesTab(
                    ext, consulta, buscando, { consulta = it }, onExt,
                    onConfiar = { porConfiar = it }, onRepos = { onRuta(Ruta.ListadosExt) }
                )
                else -> MigrarTab()
            }
        }
    }

    if (dialogoNovedades) DialogoNovedades(ext) { dialogoNovedades = false }

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
private fun NovedadesTab(
    ext: ExtManager,
    reordenar: Boolean,
    onManga: (FuenteUI, MangaFuente) -> Unit,
    onFuente: (FuenteUI) -> Unit,
    onAnadir: () -> Unit,
) {
    val motor = Motor.actual
    val todas = ext.novedades.mapNotNull { id -> ext.fuentes.firstOrNull { it.id == id } }
    val lista = todas.filter { ext.mostrarNsfw || !it.nsfw }
    when {
        todas.isEmpty() -> Vacio("Aquí salen las novedades de las fuentes que elijas.", "Añadir fuentes", onAnadir)
        motor == null -> Vacio("El motor de fuentes no está disponible.")
        reordenar -> OrdenarNovedades(ext)
        else -> LazyColumn(contentPadding = PaddingValues(bottom = 190.dp)) {
            items(lista) { NovedadesFuente(it, motor, onManga, onFuente) }
        }
    }
}

@Composable
private fun NovedadesFuente(
    f: FuenteUI, motor: MotorFuentes,
    onManga: (FuenteUI, MangaFuente) -> Unit, onFuente: (FuenteUI) -> Unit,
) {
    var estado by remember(f.id) { mutableStateOf<List<MangaFuente>?>(null) }
    var fallo by remember(f.id) { mutableStateOf<String?>(null) }
    var intento by remember(f.id) { mutableStateOf(0) }
    LaunchedEffect(f.id, intento) {
        fallo = null
        // Si la fuente no tiene "recientes", se usan los populares.
        runCatching { runCatching { motor.recientes(f, 1) }.getOrElse { motor.populares(f, 1) }.mangas.take(15) }
            .onSuccess { estado = it }
            .onFailure { fallo = it.message ?: "No se pudieron cargar las novedades" }
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
        val lst = estado
        when {
            fallo != null -> Row(Modifier.padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                LText(fallo!!, LectorType.Label, Modifier.weight(1f), color = Rojo, maxLines = 2)
                IconButton(onClick = { intento++ }) { Icon(Icons.Rounded.Refresh, "Reintentar", tint = LectorColors.Text) }
            }
            lst == null -> Box(Modifier.fillMaxWidth().height(120.dp), Alignment.Center) { CircularProgressIndicator(Modifier.size(24.dp)) }
            else -> LazyRow(contentPadding = PaddingValues(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(lst) { m ->
                    Column(Modifier.width(124.dp).pressable { onManga(f, m) }) {
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

/** Modo "ordenar": arrastra el icono ≡ para mover una fuente, o quítala con la papelera. */
@Composable
private fun OrdenarNovedades(ext: ExtManager) {
    val orden = remember { mutableStateListOf<Long>().apply { addAll(ext.novedades) } }
    var arrastrando by remember { mutableStateOf<Long?>(null) }
    var desplaz by remember { mutableStateOf(0f) }
    val paso = with(LocalDensity.current) { (72 + 12).dp.toPx() }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp).padding(bottom = 190.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        orden.toList().forEach { id ->
            val f = ext.fuentes.firstOrNull { it.id == id }
            key(id) {
                Row(
                    Modifier.fillMaxWidth().height(72.dp)
                        .zIndex(if (arrastrando == id) 1f else 0f)
                        .offset { IntOffset(0, if (arrastrando == id) desplaz.roundToInt() else 0) }
                        .clip(RoundedCornerShape(20.dp)).background(LectorColors.CardHigh),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.padding(start = 12.dp).size(48.dp).pointerInput(id) {
                            detectDragGestures(
                                onDragStart = { arrastrando = id; desplaz = 0f },
                                onDragEnd = { arrastrando = null; desplaz = 0f; ext.fijarNovedades(orden.toList()) },
                                onDragCancel = { arrastrando = null; desplaz = 0f; ext.fijarNovedades(orden.toList()) },
                            ) { change, d ->
                                change.consume()
                                desplaz += d.y
                                val i = orden.indexOf(id)
                                if (desplaz > paso / 2 && i < orden.lastIndex) {
                                    orden.add(i + 1, orden.removeAt(i)); desplaz -= paso
                                } else if (desplaz < -paso / 2 && i > 0) {
                                    orden.add(i - 1, orden.removeAt(i)); desplaz += paso
                                }
                            }
                        },
                        Alignment.Center,
                    ) { Icon(Icons.Rounded.Menu, "Arrastrar", tint = LectorColors.Text) }
                    Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                        LText(f?.nombre ?: "Fuente no disponible", LectorType.Section.copy(fontSize = 17.sp), maxLines = 1)
                        if (f != null) LText(nombreIdioma(f.lang), LectorType.Label, color = LectorColors.Muted, maxLines = 1)
                    }
                    IconButton(onClick = { orden.remove(id); ext.fijarNovedades(orden.toList()) }) {
                        Icon(Icons.Rounded.Delete, "Quitar", tint = LectorColors.Text)
                    }
                }
            }
        }
    }
}

/** Diálogo "Novedades": marca las fuentes que quieres ver en la pestaña. */
@Composable
private fun DialogoNovedades(ext: ExtManager, onCerrar: () -> Unit) {
    var q by remember { mutableStateOf("") }
    val todas = ext.fuentes
        .filter { (ext.mostrarNsfw || !it.nsfw) && (q.isBlank() || it.nombre.contains(q.trim(), ignoreCase = true)) }
        .sortedWith(compareBy({ it.nombre.lowercase() }, { it.lang }))
    AlertDialog(
        onDismissRequest = onCerrar,
        title = { Text("Novedades") },
        text = {
            Column {
                CampoBusqueda(q, { q = it }, "Buscar…")
                Spacer(Modifier.height(8.dp))
                if (todas.isEmpty()) {
                    Text("No hay fuentes. Instala una extensión y pulsa «Confiar».")
                } else {
                    LazyColumn(Modifier.heightIn(max = 400.dp)) {
                        items(todas) { f ->
                            Row(
                                Modifier.fillMaxWidth().clickable { ext.alternarNovedad(f.id) }.padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(checked = f.id in ext.novedades, onCheckedChange = { ext.alternarNovedad(f.id) })
                                IconoExt(f.pkg, null, Modifier.size(40.dp))
                                Spacer(Modifier.width(12.dp))
                                Text("${f.nombre} (${bandera(f.lang)})", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onCerrar) { Text("Aceptar") } },
    )
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
