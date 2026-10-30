package com.example.lector.ui

import kotlinx.coroutines.launch
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.lector.ActualizacionCap
import com.example.lector.Actualizador
import com.example.lector.Almacen
import com.example.lector.Comic
import com.example.lector.Descargas
import com.example.lector.ext.CapituloFuente
import com.example.lector.ext.ExtManager
import java.text.DateFormat
import java.util.Calendar
import java.util.Date

private fun inicioDelDia(ms: Long): Long = Calendar.getInstance().apply {
    timeInMillis = ms
    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}.timeInMillis

fun etiquetaDia(inicio: Long): String {
    val hoy = inicioDelDia(System.currentTimeMillis())
    val dia = 24L * 60 * 60 * 1000
    return when {
        inicio >= hoy -> "Hoy"
        inicio >= hoy - dia -> "Ayer"
        inicio >= hoy - 6 * dia -> "Hace ${(hoy - inicio + dia - 1) / dia} días"
        else -> DateFormat.getDateInstance(DateFormat.LONG).format(Date(inicio))
    }
}

fun hace(ms: Long): String {
    if (ms == 0L) return "nunca"
    val min = (System.currentTimeMillis() - ms) / 60_000
    return when {
        min < 1 -> "hace un momento"
        min < 60 -> "hace $min min"
        min < 60 * 24 -> "hace ${min / 60} h"
        else -> "hace ${min / (60 * 24)} d"
    }
}

private fun clave(a: ActualizacionCap) = "${a.fuenteId}|${a.capUrl}"

/**
 * Pestaña "Actualizaciones" (como la de Komikku): capítulos nuevos de los mangas de tu biblioteca, por día y
 * agrupados por manga. Tocar abre el capítulo; mantener pulsado selecciona (marcar leído, descargar, quitar).
 * Deslizar hacia abajo busca capítulos nuevos. Si hubo mangas que no se pudieron revisar, se ven sus errores.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ActualizacionesScreen(
    almacen: Almacen,
    ext: ExtManager,
    alAbrir: (ActualizacionCap) -> Unit,
    alAbrirManga: (ActualizacionCap) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val ctx = LocalContext.current
    val lista = almacen.actualizaciones
    abrirMangaAct = alAbrirManga
    var seleccion by remember { mutableStateOf(emptySet<String>()) }
    var abiertos by remember { mutableStateOf(emptySet<String>()) }
    var menu by remember { mutableStateOf(false) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var confirmarLimpiar by remember { mutableStateOf(false) }
    var verErrores by remember { mutableStateOf(false) }

    // Sin revisión automática: al abrir la pestaña, si hace más de 6 horas, se revisa sola.
    LaunchedEffect(Unit) {
        if (almacen.intervaloActualizacion == 0 &&
            System.currentTimeMillis() - almacen.ultimaActualizacion > Actualizador.INTERVALO_MS
        ) Actualizador.iniciar(ctx, avisar = false)
    }
    BackHandler(enabled = seleccion.isNotEmpty()) { seleccion = emptySet() }

    val grupos = remember(lista) {
        lista.sortedByDescending { it.detectado }
            .groupBy { inicioDelDia(it.detectado) }
            .map { (dia, filas) -> dia to filas.groupBy { "${it.fuenteId}|${it.urlManga}" }.values.toList() }
    }
    val comics: Map<String, Comic> = remember(almacen.version) {
        almacen.cargar().filter { it.enLinea }.associateBy { "${it.fuenteId}|${it.urlManga}" }
    }
    val leidos = ext.leidos

    fun alternar(a: ActualizacionCap) {
        val k = clave(a)
        seleccion = if (k in seleccion) seleccion - k else seleccion + k
    }

    fun seleccionadas() = lista.filter { clave(it) in seleccion }

    fun recalcularProgreso(filas: List<ActualizacionCap>) {
        filas.map { it.fuenteId to it.urlManga }.distinct().forEach { (f, url) ->
            val conocidos = almacen.capitulosConocidos(f, url)
            if (conocidos.isNotEmpty()) almacen.actualizarProgresoEnLinea(f, url, conocidos.count { ext.esLeido(f, it) }, conocidos.size)
        }
    }

    Box(modifier.fillMaxSize()) {
        val acento = MaterialTheme.colorScheme.primary
        Column(Modifier.fillMaxSize()) {
            if (seleccion.isNotEmpty()) {
                CabeceraSeleccion(
                    seleccion.size, onCerrar = { seleccion = emptySet() },
                    onTodo = { seleccion = lista.map { clave(it) }.toSet() },
                    onInvertir = { seleccion = lista.map { clave(it) }.toSet() - seleccion },
                )
            } else {
                Row(
                    Modifier.fillMaxWidth().padding(start = 22.dp, end = 14.dp, top = 16.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        LText("Actualizaciones", LectorType.Display.copy(fontSize = 32.sp, lineHeight = 36.sp), maxLines = 1)
                        Spacer(Modifier.height(4.dp))
                        Row(
                            Modifier.clip(CircleShape)
                                .background(if (Actualizador.corriendo) acento else acento.copy(alpha = 0.18f))
                                .padding(horizontal = 10.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            LText(
                                if (Actualizador.corriendo) {
                                    if (Actualizador.total > 0) "Revisando ${Actualizador.hechas} de ${Actualizador.total}…" else "Preparando la revisión…"
                                } else "Última revisión: ${hace(almacen.ultimaActualizacion)}",
                                LectorType.Label, color = if (Actualizador.corriendo) MaterialTheme.colorScheme.onPrimary else acento, maxLines = 1,
                            )
                        }
                    }
                    Box(
                        Modifier.size(44.dp).pressableCon(0.88f) { if (Actualizador.corriendo) Actualizador.cancelar(ctx) else Actualizador.iniciar(ctx) }
                            .clip(CircleShape).background(acento.copy(alpha = 0.22f)),
                        Alignment.Center,
                    ) {
                        if (Actualizador.corriendo) IndicadorCarga(Modifier.size(24.dp), strokeWidth = 2.5.dp)
                        else Icon(Icons.Rounded.Refresh, "Buscar capítulos nuevos", tint = acento, modifier = Modifier.size(22.dp))
                    }
                    Spacer(Modifier.width(8.dp))
                    Box {
                        Box(
                            Modifier.size(44.dp).pressableCon(0.88f) { menu = true }.clip(CircleShape).background(LectorColors.PillFill),
                            Alignment.Center,
                        ) {
                            Icon(Icons.Rounded.MoreVert, "Más", tint = LectorColors.Text, modifier = Modifier.size(22.dp))
                        }
                        MenuShiori(expanded = menu, onDismissRequest = { menu = false }) {
                            if (almacen.erroresActualizacion.isNotEmpty()) {
                                DropdownMenuItem(
                                    text = { Text("Errores de la última revisión (${almacen.erroresActualizacion.size})") },
                                    onClick = { menu = false; verErrores = true },
                                )
                            }
                            if (lista.isNotEmpty()) {
                                DropdownMenuItem(text = { Text("Borrar la lista") }, onClick = { menu = false; confirmarLimpiar = true })
                            }
                            DropdownMenuItem(
                                text = { Text(if (Actualizador.corriendo) "Cancelar la revisión" else "Buscar capítulos nuevos") },
                                onClick = { menu = false; if (Actualizador.corriendo) Actualizador.cancelar(ctx) else Actualizador.iniciar(ctx) },
                            )
                        }
                    }
                }
                if (almacen.erroresActualizacion.isNotEmpty() && !Actualizador.corriendo) {
                    Row(
                        Modifier.padding(horizontal = 20.dp, vertical = 6.dp).clip(CircleShape)
                            .background(LectorColors.Error.copy(alpha = 0.16f)).pressable { verErrores = true }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.Warning, null, tint = LectorColors.Error, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        LText("${almacen.erroresActualizacion.size} no se pudieron revisar · ver", LectorType.Label, color = LectorColors.Error)
                    }
                }
            }

            PullToRefreshBox(
                isRefreshing = Actualizador.corriendo,
                onRefresh = { if (!Actualizador.corriendo) Actualizador.iniciar(ctx) },
                modifier = Modifier.weight(1f),
            ) {
                if (lista.isEmpty()) {
                    VacioActualizaciones(
                        "Todo al día",
                        "Aquí saldrán los capítulos nuevos de los mangas que añadas a tu biblioteca desde Explorar. " +
                            if (almacen.intervaloActualizacion > 0) "Se revisan solos cada ${almacen.intervaloActualizacion} h; desliza hacia abajo para revisar ya."
                            else "Desliza hacia abajo para revisar.",
                        enMarcha = Actualizador.corriendo,
                    ) { Actualizador.iniciar(ctx) }
                } else {
                    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 120.dp)) {
                        var base = 0
                        grupos.forEach { (dia, mangas) ->
                            val inicioDia = base
                            base += 1
                            stickyHeader(key = "d_$dia", contentType = 0) {
                                CabeceraDiaAct(etiquetaDia(dia), mangas.sumOf { it.size }, Modifier.entrada(escalonAct(inicioDia)))
                            }
                            mangas.forEach { filas ->
                                val primera = filas.first()
                                val kGrupo = "$dia|${primera.fuenteId}|${primera.urlManga}"
                                val comic = comics["${primera.fuenteId}|${primera.urlManga}"]
                                val pos = base
                                base += 1
                                if (filas.size == 1) {
                                    item(key = "c_${clave(primera)}", contentType = 1) {
                                        FilaActualizacion(
                                            primera, comic, almacen, ext, leidos, clave(primera) in seleccion, seleccion.isNotEmpty(),
                                            onClick = { if (seleccion.isNotEmpty()) alternar(primera) else alAbrir(primera) },
                                            onLargo = { alternar(primera) },
                                            modifier = Modifier.entrada(escalonAct(pos)),
                                        )
                                    }
                                } else {
                                    val abierto = kGrupo in abiertos
                                    item(key = "g_$kGrupo", contentType = 2) {
                                        CabeceraGrupo(
                                            primera, comic, almacen, filas.size, filas.count { ext.esLeido(it.fuenteId, it.capUrl) }, abierto,
                                            seleccionado = filas.all { clave(it) in seleccion },
                                            onClick = {
                                                if (seleccion.isNotEmpty()) {
                                                    val ks = filas.map { clave(it) }.toSet()
                                                    seleccion = if (ks.all { it in seleccion }) seleccion - ks else seleccion + ks
                                                } else abiertos = if (abierto) abiertos - kGrupo else abiertos + kGrupo
                                            },
                                            onLargo = { seleccion = seleccion + filas.map { clave(it) } },
                                            modifier = Modifier.entrada(escalonAct(pos)),
                                        )
                                    }
                                    if (abierto) itemsAnimados(filas, key = { "c_${clave(it)}" }, contentType = { 1 }) { a ->
                                        FilaActualizacion(
                                            a, comic, almacen, ext, leidos, clave(a) in seleccion, seleccion.isNotEmpty(),
                                            onClick = { if (seleccion.isNotEmpty()) alternar(a) else alAbrir(a) },
                                            onLargo = { alternar(a) }, sangria = true,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        BarraAcciones(
            visible = seleccion.isNotEmpty(),
            modifier = Modifier.align(Alignment.BottomCenter),
            acciones = listOf(
                AccionBarra(Icons.Rounded.Check, "Leído") {
                    val sel = seleccionadas()
                    sel.groupBy { it.fuenteId }.forEach { (f, filas) -> ext.marcarLeidos(f, filas.map { it.capUrl }) }
                    recalcularProgreso(sel)
                    seleccion = emptySet()
                },
                AccionBarra(Iconos.OjoTachado, "No leído") {
                    val sel = seleccionadas()
                    sel.groupBy { it.fuenteId }.forEach { (f, filas) -> ext.desmarcarLeidos(f, filas.map { it.capUrl }) }
                    recalcularProgreso(sel)
                    seleccion = emptySet()
                },
                AccionBarra(Iconos.Descargar, "Descargar") {
                    var n = 0
                    seleccionadas().groupBy { it.fuenteId to it.urlManga }.forEach { (k, filas) ->
                        val f = ext.fuentes.firstOrNull { it.id == k.first } ?: return@forEach
                        n += Descargas.encolar(
                            ctx, f, k.second, filas.first().titulo,
                            filas.map { com.example.lector.CacheFichas.conMemo(it.fuenteId, it.urlManga, CapituloFuente(it.capUrl, it.capNombre, it.capFecha, it.scanlator, it.capNumero)) }.asReversed(),
                            avisar = false,
                        )
                    }
                    Avisos.mostrar(if (n == 0) "Ya estaban descargados" else "$n capítulo(s) en la cola de descargas")
                    seleccion = emptySet()
                },
                AccionBarra(Icons.Rounded.Delete, "Quitar") {
                    almacen.quitarActualizaciones(seleccion)
                    seleccion = emptySet()
                },
            ),
        )
    }

    if (confirmarLimpiar) {
        DialogoAlerta(
            onDismissRequest = { confirmarLimpiar = false },
            title = { Text("¿Borrar las actualizaciones?") },
            text = { Text("Solo se vacía esta lista. Tu biblioteca y tu progreso no cambian.") },
            confirmButton = { TextButton(onClick = { almacen.limpiarActualizaciones(); confirmarLimpiar = false }) { Text("Borrar") } },
            dismissButton = { TextButton(onClick = { confirmarLimpiar = false }) { Text("Cancelar") } },
        )
    }

    if (verErrores) {
        DialogoAlerta(
            onDismissRequest = { verErrores = false },
            title = { Text("No se pudieron revisar") },
            text = {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    almacen.erroresActualizacion.groupBy { it.motivo }.forEach { (motivo, errs) ->
                        Text(motivo, color = MaterialTheme.colorScheme.error, style = LectorType.Body)
                        errs.forEach { e -> Text("• ${e.titulo} (${e.fuente})", style = LectorType.Label, modifier = Modifier.padding(start = 8.dp, top = 2.dp)) }
                        Spacer(Modifier.height(10.dp))
                    }
                }
            },
            confirmButton = { TextButton(onClick = { verErrores = false }) { Text("Cerrar") } },
            dismissButton = { TextButton(onClick = { almacen.guardarErrores(emptyList()); verErrores = false }) { Text("Olvidar") } },
        )
    }
}

private var abrirMangaAct: (ActualizacionCap) -> Unit = {}

/** Solo la primera pantalla aparece escalonada; lo que llega luego al hacer scroll aparece sin retraso. */
private fun escalonAct(i: Int): Int = if (i < 10) i else 0

/** Cabecera de día grande y pegajosa (tapa las filas que pasan por debajo con el fondo de la pantalla). */
@Composable
private fun CabeceraDiaAct(texto: String, cuantos: Int, modifier: Modifier) {
    val acento = MaterialTheme.colorScheme.primary
    Row(
        modifier.fillMaxWidth().background(LectorColors.Bg).padding(start = 22.dp, end = 22.dp, top = 18.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LText(texto, LectorType.Hero.copy(fontSize = 22.sp, lineHeight = 26.sp), Modifier.weight(1f, fill = false), maxLines = 1)
        Spacer(Modifier.width(10.dp))
        Box(Modifier.clip(CircleShape).background(acento.copy(alpha = 0.18f)).padding(horizontal = 9.dp, vertical = 2.dp)) {
            LText(if (cuantos == 1) "1 nuevo" else "$cuantos nuevos", LectorType.Label.copy(fontWeight = FontWeight.Bold), color = acento, maxLines = 1)
        }
    }
}

@Composable
private fun VacioActualizaciones(titulo: String, texto: String, enMarcha: Boolean, onRevisar: () -> Unit) {
    val acento = MaterialTheme.colorScheme.primary
    Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 36.dp).padding(top = 72.dp, bottom = 180.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.entrada(0).size(148.dp).clip(CircleShape).background(acento.copy(alpha = 0.07f)), Alignment.Center) {
                Box(Modifier.size(108.dp).clip(CircleShape).background(acento.copy(alpha = 0.18f)), Alignment.Center) {
                    Icon(Icons.Rounded.Notifications, null, tint = acento, modifier = Modifier.size(50.dp))
                }
            }
            Spacer(Modifier.height(24.dp))
            Text(titulo, Modifier.entrada(1), color = LectorColors.Text, style = LectorType.Hero, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text(texto, Modifier.entrada(2), color = LectorColors.Muted, style = LectorType.Body, textAlign = TextAlign.Center)
            Spacer(Modifier.height(24.dp))
            BotonExpresivo(
                if (enMarcha) "Revisando…" else "Buscar ahora", Icons.Rounded.Refresh, onRevisar, Modifier.entrada(3),
                EstiloBoton.RELLENO, alto = 52.dp, habilitado = !enMarcha,
            )
        }
    }
}

/** Fondo de tarjeta que se tiñe de acento al seleccionarse (con muelle de color). */
@Composable
private fun fondoTarjetaAct(seleccionado: Boolean): Color {
    val acento = MaterialTheme.colorScheme.primary
    val base = LectorColors.Card
    val c by animateColorAsState(if (seleccionado) acento.copy(alpha = 0.24f).compositeOver(base) else base, Muelles.efectos(), label = "fondoAct")
    return c
}

@Composable
private fun PortadaActualizacion(a: ActualizacionCap, comic: Comic?, almacen: Almacen, modifier0: Modifier) {
    // Tocar la portada abre el manga (su ficha), no el capítulo.
    val forma = SuaveShape(14.dp)
    val modifier = modifier0.clip(forma).clickable { abrirMangaAct(a) }
    if (comic != null) ComicCover(comic, almacen, modifier, forma)
    else ImagenRemota(a.portadaUrl, modifier.clip(forma), rememberCabeceras(a.fuenteId), fuenteId = a.fuenteId)
}

@Composable
private fun CabeceraGrupo(
    a: ActualizacionCap, comic: Comic?, almacen: Almacen, cuantos: Int, leidos: Int, abierto: Boolean, seleccionado: Boolean,
    onClick: () -> Unit, onLargo: () -> Unit, modifier: Modifier = Modifier,
) {
    val acento = MaterialTheme.colorScheme.primary
    val giro by animateFloatAsState(if (abierto) 90f else 0f, if (Mov.reducido) snap() else Muelles.espacial(), label = "giroGrupo")
    val forma = SuaveShape(22.dp)
    val todoLeido = leidos == cuantos
    Row(
        modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
            .pressableCon(0.97f, onLargo, onClick)
            .clip(forma).background(fondoTarjetaAct(seleccionado))
            .then(if (seleccionado) Modifier.border(2.dp, acento, forma) else Modifier)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PortadaActualizacion(a, comic, almacen, Modifier.width(56.dp).aspectRatio(2f / 3f))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            LText(
                comic?.titulo ?: a.titulo, LectorType.Section.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold), maxLines = 2,
                color = if (todoLeido) LectorColors.Muted else LectorColors.Text,
            )
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.clip(CircleShape).background(if (todoLeido) LectorColors.PillFill else acento)
                        .padding(horizontal = 9.dp, vertical = 3.dp),
                ) {
                    LText(
                        "$cuantos capítulos", LectorType.Label.copy(fontWeight = FontWeight.Bold), maxLines = 1,
                        color = if (todoLeido) LectorColors.Muted else MaterialTheme.colorScheme.onPrimary,
                    )
                }
                if (leidos > 0) {
                    Spacer(Modifier.width(8.dp))
                    LText("$leidos leídos", LectorType.Label, color = LectorColors.Muted, maxLines = 1)
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        Box(Modifier.size(42.dp).clip(CircleShape).background(if (abierto) acento.copy(alpha = 0.22f) else LectorColors.PillFill), Alignment.Center) {
            Icon(
                Icons.Rounded.KeyboardArrowRight, if (abierto) "Plegar" else "Desplegar",
                tint = if (abierto) acento else LectorColors.Text, modifier = Modifier.graphicsLayer { rotationZ = giro },
            )
        }
    }
}

@Composable
private fun FilaActualizacion(
    a: ActualizacionCap, comic: Comic?, almacen: Almacen, ext: ExtManager, leidos: Int,
    seleccionado: Boolean, enSeleccion: Boolean,
    onClick: () -> Unit, onLargo: () -> Unit, sangria: Boolean = false, modifier: Modifier = Modifier,
) {
    val ctx = LocalContext.current
    val acento = MaterialTheme.colorScheme.primary
    // [leidos] solo sirve para repintar cuando cambia algún «leído».
    val leido = leidos.let { ext.esLeido(a.fuenteId, a.capUrl) }
    val nombre = a.capNombre.trim().trimEnd(':').trim().ifBlank { "Capítulo" }
    val k = clave(a)
    val fraccion = Descargas.progreso[k] ?: if (Descargas.enCola(k)) 0f else null
    val version = Descargas.version.value
    val estado: EstadoDescarga = when {
        fraccion != null -> EstadoDescarga.Descargando(fraccion)
        version >= 0 && almacen.estaDescargado(a.fuenteId, a.capUrl) -> EstadoDescarga.Listo
        else -> EstadoDescarga.Libre
    }
    val hora = remember(a.detectado) { DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(a.detectado)) }
    val forma = SuaveShape(if (sangria) 18.dp else 22.dp)

    Row(
        modifier.fillMaxWidth().padding(start = if (sangria) 44.dp else 16.dp, end = 16.dp, top = if (sangria) 2.dp else 4.dp, bottom = if (sangria) 2.dp else 4.dp)
            .pressableCon(0.97f, onLargo, onClick)
            .clip(forma).background(fondoTarjetaAct(seleccionado))
            .then(if (seleccionado) Modifier.border(2.dp, acento, forma) else Modifier)
            .padding(start = if (sangria) 14.dp else 10.dp, end = 8.dp, top = if (sangria) 8.dp else 10.dp, bottom = if (sangria) 8.dp else 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!sangria) {
            PortadaActualizacion(a, comic, almacen, Modifier.width(56.dp).aspectRatio(2f / 3f))
            Spacer(Modifier.width(14.dp))
        } else {
            // Punto de «sin leer» de acento.
            Box(Modifier.size(8.dp).clip(CircleShape).background(if (leido) LectorColors.PillFill else acento))
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            if (!sangria) LText(
                comic?.titulo ?: a.titulo, LectorType.Section.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold), maxLines = 1,
                color = if (leido) LectorColors.Muted else LectorColors.Text,
            )
            if (!sangria) Spacer(Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!sangria && !leido) {
                    Box(Modifier.size(7.dp).clip(CircleShape).background(acento))
                    Spacer(Modifier.width(6.dp))
                }
                LText(
                    nombre, LectorType.Body, Modifier.weight(1f, fill = false), maxLines = 1,
                    color = if (leido) LectorColors.Muted.copy(alpha = 0.7f) else if (sangria) LectorColors.Text else acento,
                )
            }
            if (!sangria) {
                Spacer(Modifier.height(2.dp))
                LText(hora, LectorType.Label, color = LectorColors.Muted, maxLines = 1)
            }
        }
        Spacer(Modifier.width(6.dp))
        if (!enSeleccion) Box(Modifier.size(42.dp).clip(CircleShape).background(LectorColors.PillFill), Alignment.Center) {
            BotonDescarga(estado, onClick = {
                val f = ext.fuentes.firstOrNull { it.id == a.fuenteId }
                when {
                    estado is EstadoDescarga.Descargando -> Descargas.cancelar(ctx, k)
                    estado is EstadoDescarga.Listo -> Avisos.mostrar("Ya está descargado")
                    f == null -> Avisos.mostrar("Esa fuente no está disponible ahora")
                    else -> Descargas.encolar(ctx, f, a.urlManga, a.titulo, listOf(com.example.lector.CacheFichas.conMemo(a.fuenteId, a.urlManga, CapituloFuente(a.capUrl, a.capNombre, a.capFecha, a.scanlator, a.capNumero))))
                }
            })
        }
    }
}
