package com.example.lector.ui.home

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.lector.Almacen
import com.example.lector.Comic
import com.example.lector.Descargas
import com.example.lector.ext.ExtManager
import com.example.lector.ext.MangaFuente
import com.example.lector.ext.Motor
import com.example.lector.ui.*
import com.example.lector.ui.mochy.RevealSeenState
import com.example.lector.ui.mochy.home.HomeCardHighlight
import com.example.lector.ui.mochy.home.HomeCardRow
import com.example.lector.ui.mochy.home.HomeCoverCard
import com.example.lector.ui.mochy.sheet.MenuItemRow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/*
 * Acciones de Inicio: el menú contextual al mantener pulsado (la hoja de Mochy Music: cabecera con la portada y
 * filas-píldora de ui/component/Menu.kt, como YouTubeSongMenu/YouTubeAlbumMenu de HomeScreen.kt) y la selección
 * múltiple con barra de acciones de Komikku (ui/library y ui/updates: marcar leído / no leído, descargar,
 * categorías, borrar). «Quitar de Inicio» es como el «Remove from history» de Komikku pero sin tocar el progreso.
 */

/**
 * Cómics quitados de Inicio: id -> cuándo. Vuelven solos si los lees de nuevo después (leido > momento), así
 * «quitar» no los esconde para siempre. Se guarda en SharedPreferences propias («inicio», clave «ocultos»).
 */
class OcultosInicio(ctx: Context) {
    private val prefs = ctx.getSharedPreferences("inicio", Context.MODE_PRIVATE)
    var mapa by mutableStateOf(leer())
        private set

    private fun leer(): Map<String, Long> =
        prefs.getStringSet("ocultos", emptySet()).orEmpty().mapNotNull { e ->
            val i = e.lastIndexOf(':')
            if (i <= 0) null else e.substring(0, i) to (e.substring(i + 1).toLongOrNull() ?: 0L)
        }.toMap()

    private fun guardar(m: Map<String, Long>) {
        mapa = m
        prefs.edit().putStringSet("ocultos", m.map { "${it.key}:${it.value}" }.toSet()).apply()
    }

    fun ocultar(ids: Collection<String>) {
        val ahora = System.currentTimeMillis()
        guardar(mapa + ids.associateWith { ahora })
    }

    fun mostrar(ids: Collection<String>) = guardar(mapa - ids.toSet())
    fun mostrarTodos() = guardar(emptyMap())

    fun oculto(c: Comic): Boolean = mapa[c.id]?.let { c.leido <= it } ?: false
    fun filtrar(comics: List<Comic>): List<Comic> = if (mapa.isEmpty()) comics else comics.filterNot { oculto(it) }
}

@Composable
fun rememberOcultosInicio(): OcultosInicio {
    val ctx = LocalContext.current.applicationContext
    return remember { OcultosInicio(ctx) }
}

/** Lo que hacen las acciones (las mismas que la selección de la Biblioteca, sin depender de esa pantalla). */
class AccionesInicio(
    private val ctx: Context,
    private val scope: CoroutineScope,
    val almacen: Almacen,
    val ext: ExtManager?,
    val ocultos: OcultosInicio,
) {
    fun quitarDeInicio(lista: List<Comic>) {
        val ids = lista.map { it.id }
        ocultos.ocultar(ids)
        Avisos.mostrar(if (lista.size == 1) "Quitado de Inicio" else "${lista.size} quitados de Inicio", "Deshacer") { ocultos.mostrar(ids) }
    }

    /** Borra la fecha de lectura (la página se conserva), como «quitar del historial» del Historial. */
    fun quitarDelHistorial(lista: List<Comic>) {
        lista.forEach { c ->
            almacen.quitarDelHistorial(c.id)
            if (c.enLinea) almacen.quitarHistorialLinea(c.fuenteId, c.urlManga.orEmpty())
        }
        Avisos.mostrar(if (lista.size == 1) "Quitado del historial" else "${lista.size} quitados del historial")
    }

    fun marcar(lista: List<Comic>, leido: Boolean) {
        var sinDatos = 0
        lista.forEach { c ->
            if (c.enLinea) {
                val url = c.urlManga.orEmpty()
                val conocidos = almacen.capitulosConocidos(c.fuenteId, url)
                if (conocidos.isEmpty() || ext == null) { sinDatos++; return@forEach }
                if (leido) ext.marcarLeidos(c.fuenteId, conocidos) else ext.desmarcarLeidos(c.fuenteId, conocidos)
                almacen.actualizarProgresoEnLinea(c.fuenteId, url, if (leido) conocidos.size else 0, conocidos.size)
            } else {
                almacen.actualizarComic(c.id) { it.copy(ultimaPagina = if (leido) (it.paginas - 1).coerceAtLeast(0) else 0) }
            }
        }
        if (sinDatos > 0) Avisos.mostrar("$sinDatos manga(s) aún no tienen la lista de capítulos: ábrelos o busca actualizaciones")
        else Avisos.mostrar(if (leido) "Marcado como leído" else "Marcado como no leído")
    }

    fun descargar(lista: List<Comic>, maximo: Int?) {
        val motor = Motor.actual
        val enLinea = lista.filter { it.enLinea }
        if (enLinea.isEmpty()) { Avisos.mostrar("Los cómics locales ya están en el teléfono"); return }
        if (motor == null || ext == null) { Avisos.mostrar("Las fuentes aún se están cargando"); return }
        Avisos.mostrar("Preparando ${enLinea.size} manga(s)…")
        scope.launch {
            var total = 0
            enLinea.forEach { c ->
                val f = ext.fuentes.firstOrNull { it.id == c.fuenteId } ?: return@forEach
                runCatching { motor.ficha(f, MangaFuente(c.urlManga.orEmpty(), c.titulo, c.portadaUrl)) }.getOrNull()?.let { ficha ->
                    var caps = ficha.capitulos.asReversed().filter { !ext.esLeido(f.id, it.url) }
                    if (maximo != null) caps = caps.take(maximo)
                    total += Descargas.encolar(ctx, f, c.urlManga.orEmpty(), c.titulo, caps, avisar = false)
                }
            }
            Avisos.mostrar(if (total == 0) "No había capítulos que descargar" else "$total capítulo(s) en la cola de descargas")
        }
    }

    fun urlWeb(c: Comic): String? {
        val url = c.urlManga ?: return null
        val f = ext?.fuentes?.firstOrNull { it.id == c.fuenteId }
        if (f != null) runCatching { Motor.actual?.urlManga(f, MangaFuente(url, c.titulo, c.portadaUrl)) }.getOrNull()?.let { return it }
        return when {
            url.startsWith("http") -> url
            f == null || f.baseUrl.isBlank() -> null
            else -> f.baseUrl.trimEnd('/') + "/" + url.trimStart('/')
        }
    }

    fun compartir(c: Comic) {
        val u = urlWeb(c)
        compartirTexto(ctx, if (u != null) "${c.titulo}\n$u" else c.titulo)
    }
}

@Composable
fun rememberAccionesInicio(almacen: Almacen, ext: ExtManager?, ocultos: OcultosInicio): AccionesInicio {
    val ctx = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    return remember(almacen, ext, ocultos) { AccionesInicio(ctx, scope, almacen, ext, ocultos) }
}

/** Fila de portadas de Inicio que admite mantener pulsado y selección (la CoverCarousel con combinedClickable). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CarruselInicio(
    rowKey: String,
    items: List<Comic>,
    almacen: Almacen,
    revealSeen: RevealSeenState,
    seleccion: Set<String>,
    onClick: (Comic) -> Unit,
    onLongClick: (Comic) -> Unit,
) {
    val enSeleccion = seleccion.isNotEmpty()
    HomeCardRow(rowKey = rowKey, items = items, revealSeen = revealSeen, key = { it.id }) { c ->
        val sel = c.id in seleccion
        val escala by animateFloatAsState(if (sel) 0.92f else 1f, label = "selPortada")
        Box(
            Modifier.graphicsLayer { scaleX = escala; scaleY = escala }
                .combinedClickable(
                    interactionSource = null, indication = HomeCardHighlight,
                    onClick = { onClick(c) }, onLongClick = { onLongClick(c) },
                )
        ) {
            HomeCoverCard(c, almacen)
            if (enSeleccion) MarcaSeleccion(sel, Modifier.align(Alignment.TopEnd).padding(7.dp))
        }
    }
}

/** El círculo de selección: vacío con borde o relleno con el acento y una marca. */
@Composable
fun MarcaSeleccion(seleccionado: Boolean, modifier: Modifier = Modifier) {
    val acento = MaterialTheme.colorScheme.primary
    Box(
        modifier.size(24.dp).clip(CircleShape)
            .background(if (seleccionado) acento else Color.Black.copy(alpha = 0.35f))
            .border(2.dp, if (seleccionado) acento else Color.White.copy(alpha = 0.85f), CircleShape),
        Alignment.Center,
    ) {
        if (seleccionado) Icon(Icons.Rounded.Check, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(16.dp))
    }
}

/** Acciones secundarias de la selección (las que no caben en [com.example.lector.ui.BarraAcciones]); HomeScreen las ejecuta. */
enum class OpcionInicio { NO_LEIDO, QUITAR_HISTORIAL, EDITAR, COMPARTIR, FICHA, ELIMINAR, DESCARGAR_5 }

/** «⋮» de la barra de selección: tarjeta con el resto de acciones (cabecera con la portada si hay uno solo). */
@Composable
fun HojaMasInicio(lista: List<Comic>, almacen: Almacen, puedeEliminar: Boolean, onElegir: (OpcionInicio) -> Unit, onCerrar: () -> Unit) {
    val uno = lista.singleOrNull()
    HojaLector(onCerrar) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            if (uno != null) {
                val forma = RoundedCornerShape(10.dp)
                ComicCover(uno, almacen, Modifier.width(46.dp).aspectRatio(2f / 3f).clip(forma), forma)
                Spacer(Modifier.width(14.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(uno?.titulo ?: "${lista.size} seleccionados", style = LectorType.Section, maxLines = 2, overflow = TextOverflow.Ellipsis, color = LectorColors.Text)
                if (uno != null) Text(uno.textoPagina(), style = LectorType.Label.copy(fontSize = 13.sp), maxLines = 1, color = LectorColors.Muted)
            }
        }
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            @Composable
            fun op(icono: ImageVector, texto: String, o: OpcionInicio) =
                MenuItemRow(icon = { Icon(icono, null) }, enabled = true, onClick = { onElegir(o) }) { Text(texto) }

            if (lista.any { it.progresoLectura() > 0f }) op(Icons.Rounded.Clear, "Marcar como no leído", OpcionInicio.NO_LEIDO)
            if (lista.any { it.leido > 0 }) op(Icons.Rounded.Refresh, "Quitar del historial", OpcionInicio.QUITAR_HISTORIAL)
            if (lista.any { it.enLinea }) op(Iconos.Descargar, "Descargar los 5 siguientes", OpcionInicio.DESCARGAR_5)
            if (uno != null) {
                op(Icons.Rounded.Info, "Ver ficha", OpcionInicio.FICHA)
                op(Icons.Rounded.Edit, "Editar información", OpcionInicio.EDITAR)
                if (uno.enLinea) op(Icons.Rounded.Share, "Compartir", OpcionInicio.COMPARTIR)
            }
            if (puedeEliminar) op(Icons.Rounded.Delete, "Quitar de la biblioteca", OpcionInicio.ELIMINAR)
        }
    }
}

/** Categorías de uno o varios cómics (el diálogo con casillas de tres estados de la Biblioteca / Komikku). */
@Composable
fun DialogoCategoriasInicio(ids: List<String>, almacen: Almacen, onCerrar: () -> Unit) {
    val categorias = remember { almacen.categorias() }
    val asignaciones = remember { almacen.asignaciones() }
    var estados by remember {
        mutableStateOf(categorias.associate { cat ->
            val cuantos = ids.count { cat.id in asignaciones[it].orEmpty() }
            cat.id to when {
                ids.isNotEmpty() && cuantos == ids.size -> ToggleableState.On
                cuantos == 0 -> ToggleableState.Off
                else -> ToggleableState.Indeterminate
            }
        })
    }
    DialogoAlerta(
        onDismissRequest = onCerrar,
        title = { Text("Categorías") },
        text = {
            Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
                if (categorias.isEmpty()) Text("Aún no hay categorías. Créalas desde la Biblioteca.")
                categorias.forEach { cat ->
                    val e = estados[cat.id] ?: ToggleableState.Off
                    Row(
                        Modifier.fillMaxWidth().pressable {
                            estados = estados + (cat.id to if (e == ToggleableState.On) ToggleableState.Off else ToggleableState.On)
                        }.padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TriStateCheckbox(state = e, onClick = null)
                        Spacer(Modifier.width(8.dp))
                        Text(cat.nombre + if (cat.oculta) " (oculta)" else "")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                almacen.modificarCategorias(
                    ids.toSet(),
                    estados.filter { it.value == ToggleableState.On }.keys,
                    estados.filter { it.value == ToggleableState.Off }.keys,
                )
                onCerrar()
            }) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onCerrar) { Text("Cancelar") } },
    )
}

private val EstadosEdicion = listOf(0 to "Desconocido", 1 to "En emisión", 2 to "Completado", 4 to "Publicación finalizada", 5 to "Cancelado", 6 to "En pausa")

/** «Editar información» (el diálogo de la ficha / Komikku): título, autor, géneros, descripción, estado y portada. */
@Composable
fun DialogoEditarInicio(comic: Comic, almacen: Almacen, onCerrar: () -> Unit) {
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
        onDismissRequest = onCerrar,
        title = { Text("Editar información") },
        text = {
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CampoTextoShiori(titulo, { titulo = it }, label = { Text("Título") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                CampoTextoShiori(autor, { autor = it }, label = { Text("Autor") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                CampoTextoShiori(generos, { generos = it }, label = { Text("Géneros (separados por comas)") }, modifier = Modifier.fillMaxWidth())
                CampoTextoShiori(descripcion, { descripcion = it }, label = { Text("Descripción") }, minLines = 3, modifier = Modifier.fillMaxWidth())
                Text("Estado", style = MaterialTheme.typography.labelLarge)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    EstadosEdicion.forEach { (e, t) -> ChipSel(t, estado == e) { estado = e } }
                }
                TextButton(onClick = { elegirImagen.launch("image/*") }) { Text("Cambiar la portada…") }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                almacen.editarInfo(comic.id, titulo, autor, descripcion, generos.split(',').map { it.trim() }.filter { it.isNotEmpty() }, estado)
                onCerrar()
            }) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onCerrar) { Text("Cancelar") } },
    )
}

/** Confirmación antes de quitar de la biblioteca (borra el archivo o deja de seguir el manga). */
@Composable
fun DialogoEliminarInicio(lista: List<Comic>, onConfirmar: () -> Unit, onCerrar: () -> Unit) {
    DialogoAlerta(
        onDismissRequest = onCerrar,
        title = { Text(if (lista.size == 1) "¿Quitar «${lista[0].titulo}»?" else "¿Quitar ${lista.size} de la biblioteca?") },
        text = { Text("Se borra de la biblioteca junto con su progreso. Para solo esconderlo de Inicio usa «Quitar de Inicio».") },
        confirmButton = { TextButton(onClick = { onConfirmar(); onCerrar() }) { Text("Quitar") } },
        dismissButton = { TextButton(onClick = onCerrar) { Text("Cancelar") } },
    )
}
