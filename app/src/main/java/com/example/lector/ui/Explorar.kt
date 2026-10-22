package com.example.lector.ui

import androidx.compose.ui.focus.onFocusChanged
import android.content.Context
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
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.graphicsLayer
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
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
    almacen: com.example.lector.Almacen? = null,
    onMigrar: (com.example.lector.Comic) -> Unit = {},
    onBuscarGlobal: () -> Unit = {},
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
            if (sub == 0) IconButton(onClick = onBuscarGlobal) { Icon(Icons.Rounded.Search, "Búsqueda global", tint = LectorColors.Text) }
            if (sub == 0 || sub == 2) {
                val forma = SuaveShape(8.dp)
                val color = if (ext.mostrarNsfw) acento else LectorColors.Muted
                Box(
                    Modifier.padding(horizontal = 8.dp).size(30.dp).pressable { ext.alternarNsfw() }
                        .clip(forma).border(2.dp, color, forma),
                    Alignment.Center
                ) { LText("18", LectorType.Body.copy(fontWeight = FontWeight.Bold, fontSize = 13.sp), color = color) }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "Más", tint = LectorColors.Text) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Búsqueda global") }, onClick = { menu = false; onBuscarGlobal() })
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
                else -> MigrarTab(ext, almacen, onMigrar)
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
            dismissButton = { TextButton(onClick = { ext.activarTodosIdiomas() }) { Text("Todos") } },
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

/**
 * Lo cargado en Novedades: se pide una vez por sesión de la app y se reutiliza al cambiar de pestaña o de pantalla.
 * Vuelve a pedirse al deslizar hacia abajo ([recargar]) o al abrir la app de nuevo ([olvidar], desde MainActivity).
 */
object NovedadesGuardadas {
    // id de la fuente → (generación en la que se cargó, mangas)
    val datos = androidx.compose.runtime.mutableStateMapOf<Long, Pair<Int, List<MangaFuente>>>()
    // id de la fuente → generación en la que falló
    val fallos = androidx.compose.runtime.mutableStateMapOf<Long, Int>()
    var generacion by mutableStateOf(0); private set

    fun recargar() { generacion++ }
    fun olvidar() { datos.clear(); fallos.clear(); generacion++ }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
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
        else -> {
            var refrescando by remember { mutableStateOf(false) }
            val gen = NovedadesGuardadas.generacion
            val pendientes = lista.any { NovedadesGuardadas.datos[it.id]?.first != gen && NovedadesGuardadas.fallos[it.id] != gen }
            LaunchedEffect(pendientes) { if (!pendientes) refrescando = false }
            // Deslizar hacia abajo vuelve a pedir las novedades de todas las fuentes (lo de antes se ve mientras tanto).
            androidx.compose.material3.pulltorefresh.PullToRefreshBox(
                isRefreshing = refrescando && pendientes,
                onRefresh = { refrescando = true; NovedadesGuardadas.recargar() },
                modifier = Modifier.fillMaxSize(),
            ) {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 190.dp)) {
                    items(lista, key = { it.id }) { NovedadesFuente(it, motor, onManga, onFuente) }
                }
            }
        }
    }
}

@Composable
private fun NovedadesFuente(
    f: FuenteUI, motor: MotorFuentes,
    onManga: (FuenteUI, MangaFuente) -> Unit, onFuente: (FuenteUI) -> Unit,
) {
    val gen = NovedadesGuardadas.generacion
    val estado = NovedadesGuardadas.datos[f.id]?.second
    var fallo by remember(f.id) { mutableStateOf<String?>(null) }
    var intento by remember(f.id) { mutableStateOf(0) }
    LaunchedEffect(f.id, intento, gen) {
        // Ya cargadas en esta sesión (y sin pedir recargar): no se vuelven a pedir.
        if (NovedadesGuardadas.datos[f.id]?.first == gen) return@LaunchedEffect
        fallo = null
        // Si la fuente no tiene "recientes", se usan los populares.
        runCatching { runCatching { motor.recientes(f, 1) }.getOrElse { motor.populares(f, 1) }.mangas.distinctBy { it.url }.take(15) }
            .onSuccess { NovedadesGuardadas.datos[f.id] = gen to it; NovedadesGuardadas.fallos.remove(f.id) }
            .onFailure {
                if (it is kotlinx.coroutines.CancellationException) throw it
                NovedadesGuardadas.fallos[f.id] = gen
                // Si ya había algo cargado, se sigue enseñando; el fallo solo se ve si no hay nada.
                if (estado == null) fallo = it.message ?: "No se pudieron cargar las novedades"
                else Avisos.mostrar("${f.nombre}: no se pudieron actualizar las novedades")
            }
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
            lst == null && fallo != null -> Row(Modifier.padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                LText(fallo!!, LectorType.Label, Modifier.weight(1f), color = Rojo, maxLines = 2)
                IconButton(onClick = { intento++ }) { Icon(Icons.Rounded.Refresh, "Reintentar", tint = LectorColors.Text) }
            }
            lst == null -> BonesPortadas()
            else -> LazyRow(contentPadding = PaddingValues(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(lst, key = { it.url }) { m ->
                    Column(Modifier.width(124.dp).press3D().pressable { onManga(f, m) }) {
                        ImagenRemota(
                            m.portadaUrl,
                            Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(SuaveShape(12.dp)),
                            motor.cabeceras(f), fuenteId = f.id
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
                        .clip(SuaveShape(20.dp)).background(LectorColors.CardHigh),
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

    // «Confiar en todas»: las instaladas que aún no son de confianza (con una sola confirmación).
    val sinConfianza = ext.instaladas.filter { !it.confiable }
    var confirmarTodas by remember { mutableStateOf(false) }
    if (confirmarTodas) {
        AlertDialog(
            onDismissRequest = { confirmarTodas = false },
            title = { Text("¿Confiar en ${sinConfianza.size} extensiones?") },
            text = {
                Text(
                    "Una extensión de confianza puede ejecutar su código dentro de la app. Hazlo solo si las instalaste " +
                        "tú desde listados que conoces.\n\n" + sinConfianza.take(12).joinToString("\n") { "• " + it.nombre } +
                        if (sinConfianza.size > 12) "\n… y ${sinConfianza.size - 12} más" else ""
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    sinConfianza.forEach { ext.confiar(it) }
                    confirmarTodas = false
                    Avisos.mostrar("${sinConfianza.size} extensiones de confianza")
                }) { Text("Confiar en todas") }
            },
            dismissButton = { TextButton(onClick = { confirmarTodas = false }) { Text("Cancelar") } },
        )
    }

    Column(Modifier.fillMaxSize()) {
        if (buscando) Box(Modifier.padding(horizontal = 24.dp, vertical = 12.dp)) { CampoBusqueda(consulta, onConsulta, "Buscar extensiones") }
        if (ext.cargando) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (sinConfianza.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LText(
                    "${sinConfianza.size} sin confianza", LectorType.Body, Modifier.weight(1f), color = LectorColors.Muted, maxLines = 1,
                )
                PillButton("Confiar en todas", Icons.Rounded.Check, { confirmarTodas = true })
            }
        }
        val estadoLista = androidx.compose.foundation.lazy.rememberLazyListState()
        Box(Modifier.weight(1f)) {
        LazyColumn(state = estadoLista, contentPadding = PaddingValues(bottom = 190.dp)) {
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
            // contentType = nombre de la sección: es lo que enseña la burbuja de la barra de desplazamiento.
            if (pendientes.isNotEmpty()) {
                item(contentType = "Actualizaciones") { TituloSeccion("Actualizaciones pendientes (${pendientes.size})") }
                items(pendientes, contentType = { "Actualizaciones" }) { FilaInstalada(it, ext, onDetalle, onConfiar) }
            }
            if (alDia.isNotEmpty()) {
                item(contentType = "Instalado") { TituloSeccion("Instalado") }
                items(alDia, contentType = { "Instalado" }) { FilaInstalada(it, ext, onDetalle, onConfiar) }
            }
            disp.forEach { (lang, lista) ->
                val n = nombreIdioma(lang)
                item(contentType = n) { TituloSeccion(n) }
                items(lista, contentType = { n }) { FilaDisponible(it, ext) }
            }
        }
        BarraDesplazamiento(estadoLista)
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
private fun MigrarTab(ext: ExtManager, almacen: com.example.lector.Almacen?, onMigrar: (com.example.lector.Comic) -> Unit) {
    // Como en Komikku: tus series agrupadas por fuente; elige una para pasarla a otra fuente.
    val series = remember(almacen?.version) { almacen?.cargar().orEmpty().filter { it.enLinea } }
    if (series.isEmpty()) {
        Vacio("Aquí puedes mover tus series de una fuente a otra. Añade a la biblioteca algún manga de una fuente.")
        return
    }
    val grupos = remember(series, ext.fuentes) {
        series.groupBy { c -> ext.fuentes.firstOrNull { it.id == c.fuenteId }?.nombre ?: "Fuente no instalada" }
            .toSortedMap(compareBy { it.lowercase() })
    }
    androidx.compose.foundation.lazy.LazyColumn(contentPadding = PaddingValues(bottom = 190.dp)) {
        grupos.forEach { (nombre, lista) ->
            item(key = "g_$nombre") {
                LText("$nombre (${lista.size})", LectorType.Section.copy(fontSize = 16.sp), Modifier.padding(start = 24.dp, end = 24.dp, top = 18.dp, bottom = 6.dp), color = LectorColors.Muted)
            }
            items(lista.size, key = { lista[it].id }) { i ->
                val c = lista[i]
                Row(
                    Modifier.fillMaxWidth().pressable { onMigrar(c) }.padding(horizontal = 24.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ComicCover(c, almacen!!, Modifier.width(44.dp).aspectRatio(2f / 3f), SuaveShape(8.dp))
                    Spacer(Modifier.width(14.dp))
                    LText(c.titulo, LectorType.Body, Modifier.weight(1f), maxLines = 2)
                    Icon(Icons.Rounded.ArrowForward, "Migrar", tint = LectorColors.Muted)
                }
            }
        }
    }
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
    // Campo de búsqueda de musicfy: píldora de 48 dp, #141416 (un escalón más claro con el foco), pista gris.
    var foco by remember { mutableStateOf(false) }
    val fondo by animateColorAsState(if (foco) LectorColors.CardHigh else LectorColors.Card, tween(160), label = "campo")
    BasicTextField(
        value = valor, onValueChange = alCambiar, singleLine = true,
        textStyle = LectorType.Body.copy(color = LectorColors.Text, fontSize = 15.sp),
        cursorBrush = SolidColor(LectorColors.Text),
        modifier = Modifier.onFocusChanged { foco = it.isFocused },
        decorationBox = { inner ->
            Row(
                Modifier.fillMaxWidth().height(48.dp).clip(CircleShape).background(fondo).padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.Search, null, tint = LectorColors.Pista, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (valor.isEmpty()) LText(pista, LectorType.Body.copy(fontSize = 15.sp), color = LectorColors.Pista, maxLines = 1)
                    inner()
                }
                if (valor.isNotEmpty()) Icon(
                    Icons.Rounded.Close, "Borrar", tint = LectorColors.Pista,
                    modifier = Modifier.size(20.dp).pressable { alCambiar("") },
                )
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
    val forma = SuaveShape(12.dp)
    if (local != null) Image(local, null, modifier.clip(forma), contentScale = ContentScale.Crop)
    else ImagenRemota(url, modifier.clip(forma))
}

/**
 * Cargador de imágenes sin librerías.
 *  - Memoria: límite por BYTES (no por número de imágenes), así las cuadrículas largas no vuelven a descargar portadas.
 *  - Disco: guarda los bytes originales en cacheDir/imagenes (máx. ~120 MB); al reabrir la app las portadas salen al instante.
 *  - Una sola petición por URL aunque varias pantallas la pidan a la vez, y máximo 6 descargas simultáneas.
 */
object Imagenes {
    private val memoria = object : LruCache<String, ImageBitmap>(
        (Runtime.getRuntime().maxMemory() / 8L).coerceIn(16L * 1024 * 1024, 96L * 1024 * 1024).toInt()
    ) {
        override fun sizeOf(key: String, value: ImageBitmap): Int = value.width * value.height * 4
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val enVuelo = HashMap<String, Deferred<ImageBitmap?>>()
    private val limite = Semaphore(6)
    @Volatile private var disco: File? = null

    private const val MAX_DISCO = 120L * 1024 * 1024

    /** Prepara la caché en disco. Se puede llamar muchas veces. */
    fun iniciar(ctx: Context) {
        if (disco != null) return
        val carpeta = File(ctx.applicationContext.cacheDir, "imagenes").apply { mkdirs() }
        disco = carpeta
        scope.launch { runCatching { recortarDisco(carpeta) } }
    }

    private fun recortarDisco(carpeta: File) {
        val archivos = carpeta.listFiles()?.filter { it.isFile } ?: return
        var total = archivos.sumOf { it.length() }
        if (total <= MAX_DISCO) return
        for (a in archivos.sortedBy { it.lastModified() }) {
            if (total <= MAX_DISCO * 3 / 4) break
            total -= a.length()
            a.delete()
        }
    }

    /** Bytes originales de una imagen (de la caché en disco o de la red), para compartirla o guardarla. */
    suspend fun bytes(url: String, cabeceras: Map<String, String> = emptyMap(), fuenteId: Long? = null): ByteArray? = kotlinx.coroutines.withContext(Dispatchers.IO) {
        archivoDe(url)?.takeIf { it.exists() && it.length() > 0 }?.let { f -> return@withContext runCatching { f.readBytes() }.getOrNull() }
        descargar(url, cabeceras, fuenteId)?.let { return@withContext it }
        runCatching {
            val c = URL(url).openConnection() as HttpURLConnection
            try {
                c.connectTimeout = 15_000; c.readTimeout = 30_000
                c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) Lector/1.0")
                cabeceras.forEach { (k, v) -> c.setRequestProperty(k, v) }
                if (c.responseCode !in 200..299) null else c.inputStream.use { it.readBytes() }
            } finally { c.disconnect() }
        }.getOrNull()
    }

    /** Bytes que ocupa la caché de imágenes en disco. */
    fun tamanoDisco(): Long = disco?.listFiles()?.sumOf { it.length() } ?: 0L

    /** Vacía la caché de imágenes (memoria y disco). */
    fun vaciar() {
        memoria.evictAll()
        disco?.listFiles()?.forEach { it.delete() }
    }

    /** Devuelve la imagen si ya está en memoria (sin esperar), para pintarla en el primer fotograma. */
    fun enMemoria(url: String): ImageBitmap? = memoria.get(url)

    suspend fun cargar(url: String, cabeceras: Map<String, String> = emptyMap(), maxLado: Int = 512, fuenteId: Long? = null): ImageBitmap? {
        memoria.get(url)?.let { return it }
        val tarea = synchronized(enVuelo) {
            enVuelo.getOrPut(url) {
                scope.async {
                    try {
                        limite.withPermit { obtener(url, cabeceras, maxLado, fuenteId) }
                    } finally {
                        synchronized(enVuelo) { enVuelo.remove(url) }
                    }
                }
            }
        }
        return tarea.await()
    }

    private fun archivoDe(url: String): File? {
        val carpeta = disco ?: return null
        val h = java.security.MessageDigest.getInstance("SHA-1")
            .digest(url.toByteArray()).joinToString("") { "%02x".format(it) }
        return File(carpeta, h)
    }

    /** Bytes de una imagen de internet: cliente de la fuente → cliente general → conexión simple. */
    private suspend fun descargar(url: String, cabeceras: Map<String, String>, fuenteId: Long?): ByteArray? {
        val motor = com.example.lector.ext.Motor.actual
        if (fuenteId != null && fuenteId != 0L && motor != null) {
            runCatching { motor.bytesImagen(fuenteId, url) }.getOrNull()?.takeIf { it.isNotEmpty() }?.let { return it }
        }
        motor?.clienteGeneral()?.let { cliente ->
            runCatching {
                val b = okhttp3.Request.Builder().url(url)
                cabeceras.forEach { (k, v) -> b.header(k, v) }
                cliente.newCall(b.build()).execute().use { r -> if (r.isSuccessful) r.body.bytes() else null }
            }.getOrNull()?.takeIf { it.isNotEmpty() }?.let { return it }
        }
        return runCatching {
            val c = URL(url).openConnection() as HttpURLConnection
            try {
                c.connectTimeout = 15_000
                c.readTimeout = 30_000
                c.setRequestProperty("User-Agent", eu.kanade.tachiyomi.network.NetworkHelper.DEFAULT_USER_AGENT)
                cabeceras.forEach { (k, v) -> c.setRequestProperty(k, v) }
                if (c.responseCode !in 200..299) null else c.inputStream.use { it.readBytes() }
            } finally {
                c.disconnect()
            }
        }.getOrNull()
    }

    private suspend fun obtener(url: String, cabeceras: Map<String, String>, maxLado: Int, fuenteId: Long?): ImageBitmap? {
        val archivo = archivoDe(url)
        // 1) Disco.
        if (archivo != null && archivo.exists() && archivo.length() > 0) {
            val bytes = runCatching { archivo.readBytes() }.getOrNull()
            val img = bytes?.let { decodificar(it, maxLado) }
            if (img != null) {
                archivo.setLastModified(System.currentTimeMillis())
                memoria.put(url, img)
                return img
            }
            archivo.delete() // archivo dañado
        }
        // 2) Red: con el cliente de la fuente si se sabe cuál es; si no, con el cliente general (cookies + Cloudflare).
        val bytes = descargar(url, cabeceras, fuenteId) ?: return null
        val img = decodificar(bytes, maxLado) ?: return null
        if (archivo != null) {
            runCatching {
                val tmp = File(archivo.path + ".tmp")
                tmp.writeBytes(bytes)
                if (!tmp.renameTo(archivo)) tmp.delete()
            }
        }
        memoria.put(url, img)
        return img
    }

    private fun decodificar(bytes: ByteArray, maxLado: Int): ImageBitmap? = runCatching {
        val medidas = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, medidas)
        if (medidas.outWidth <= 0) return@runCatching null
        var muestra = 1
        while (maxOf(medidas.outWidth, medidas.outHeight) / (muestra * 2) >= maxLado) muestra *= 2
        val opciones = BitmapFactory.Options().apply { inSampleSize = muestra }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opciones)?.asImageBitmap()
    }.getOrNull()
}

@Composable
fun ImagenRemota(
    url: String?, modifier: Modifier = Modifier, cabeceras: Map<String, String> = emptyMap(),
    onCargada: ((ImageBitmap) -> Unit)? = null,
    escala: ContentScale = ContentScale.Crop,
    maxLado: Int = 512,
    // Fuente de la que viene la imagen: se descarga con su cliente (cookies, Cloudflare, Referer…).
    fuenteId: Long? = null,
) {
    val ctx = LocalContext.current
    remember { Imagenes.iniciar(ctx) }
    // Si ya estaba en memoria se pinta en el primer fotograma, sin parpadeo ni fundido al hacer scroll.
    var img by remember(url) { mutableStateOf(url?.takeIf { it.isNotBlank() }?.let { Imagenes.enMemoria(it) }) }
    LaunchedEffect(url, cabeceras, fuenteId) {
        if (!url.isNullOrBlank()) {
            val cargada = img ?: Imagenes.cargar(url, cabeceras, maxLado, fuenteId)
            img = cargada
            if (cargada != null) onCargada?.invoke(cargada)
        }
    }
    // La portada nueva aparece con un fundido; la que ya estaba en caché empieza directamente en 1f.
    val opacidad by animateFloatAsState(if (img != null) 1f else 0f, tween(300), label = "portadaFundido")
    Box(modifier.background(LectorColors.CardHigh)) {
        val i = img
        if (i != null) Image(
            bitmap = i, contentDescription = null, contentScale = escala,
            modifier = Modifier.fillMaxSize().graphicsLayer { alpha = opacidad },
        )
    }
}
