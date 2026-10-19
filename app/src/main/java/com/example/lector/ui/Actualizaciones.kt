package com.example.lector.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.lector.ActualizacionCap
import com.example.lector.Almacen
import com.example.lector.ext.CapituloFuente
import com.example.lector.ext.ExtManager
import com.example.lector.ext.MangaFuente
import com.example.lector.ext.Motor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Calendar
import java.util.Date

/**
 * Revisa los mangas en línea de la biblioteca buscando capítulos nuevos (como "Actualizar biblioteca" de Komikku).
 * Vive fuera de las pantallas: si cambias de pestaña, la revisión sigue y el progreso se ve al volver.
 */
object Actualizador {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var trabajo: Job? = null

    var corriendo by mutableStateOf(false)
        private set
    var hechas by mutableStateOf(0)
        private set
    var total by mutableStateOf(0)
        private set

    /** Cada cuánto se revisa sola al abrir la pestaña. */
    const val INTERVALO_MS = 6L * 60 * 60 * 1000

    fun iniciar(ctx: Context, almacen: Almacen, ext: ExtManager, avisar: Boolean = true) {
        if (corriendo) return
        val app = ctx.applicationContext
        val motor = Motor.actual
        if (motor == null) {
            if (avisar) Avisos.mostrar("El motor de fuentes no está listo")
            return
        }
        val lista = almacen.cargar().filter { it.enLinea }
        if (lista.isEmpty()) {
            almacen.marcarActualizado()
            if (avisar) Avisos.mostrar("Tu biblioteca no tiene mangas de fuentes en línea")
            return
        }
        corriendo = true; hechas = 0; total = lista.size
        trabajo = scope.launch {
            var nuevos = 0
            var fallos = 0
            try {
                // De tres en tres, para no saturar las fuentes.
                for (grupo in lista.chunked(3)) {
                    val r = grupo.map { c ->
                        async(Dispatchers.IO) {
                            runCatching {
                                val url = c.urlManga.orEmpty()
                                val f = ext.fuentes.firstOrNull { it.id == c.fuenteId }
                                    ?: error("fuente no disponible")
                                val ficha = motor.ficha(f, MangaFuente(url, c.titulo, c.portadaUrl))
                                val n = almacen.registrarCapitulos(
                                    c.fuenteId, url, c.titulo, ficha.detalle.portadaUrl ?: c.portadaUrl, ficha.capitulos,
                                )
                                almacen.actualizarProgresoEnLinea(
                                    c.fuenteId, url, ficha.capitulos.count { ext.esLeido(c.fuenteId, it.url) },
                                    ficha.capitulos.size,
                                )
                                n
                            }.getOrNull()
                        }
                    }.awaitAll()
                    for (n in r) {
                        if (n == null) fallos++
                        else nuevos += n
                    }
                    hechas += grupo.size
                }
            } finally {
                corriendo = false
                almacen.marcarActualizado()
            }
            if (avisar || nuevos > 0) {
                val msg = buildString {
                    append(if (nuevos == 0) "Sin capítulos nuevos" else "$nuevos capítulo(s) nuevo(s)")
                    if (fallos > 0) append(" · $fallos manga(s) no se pudieron revisar")
                }
                Avisos.mostrar(msg)
            }
        }
    }

    fun cancelar() {
        trabajo?.cancel()
    }
}

private fun inicioDelDia(ms: Long): Long = Calendar.getInstance().apply {
    timeInMillis = ms
    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}.timeInMillis

private fun etiquetaDia(inicio: Long): String {
    val hoy = inicioDelDia(System.currentTimeMillis())
    val dia = 24L * 60 * 60 * 1000
    return when (inicio) {
        hoy -> "Hoy"
        hoy - dia -> "Ayer"
        else -> DateFormat.getDateInstance(DateFormat.LONG).format(Date(inicio))
    }
}

private fun hace(ms: Long): String {
    if (ms == 0L) return "nunca"
    val min = (System.currentTimeMillis() - ms) / 60_000
    return when {
        min < 1 -> "hace un momento"
        min < 60 -> "hace $min min"
        min < 60 * 24 -> "hace ${min / 60} h"
        else -> "hace ${min / (60 * 24)} d"
    }
}

/**
 * Pestaña "Actualizaciones": los capítulos nuevos que han salido de los mangas de tu biblioteca,
 * agrupados por día. Tocar uno abre ese capítulo; el círculo de la derecha lo descarga.
 */
@Composable
fun ActualizacionesScreen(
    almacen: Almacen,
    ext: ExtManager,
    alAbrir: (ActualizacionCap) -> Unit,
    cabecerasDe: (Long) -> Map<String, String>,
    modifier: Modifier = Modifier,
) {
    val ctx = LocalContext.current
    val lista = almacen.actualizaciones
    val grupos = remember(lista) {
        lista.sortedByDescending { it.detectado }.groupBy { inicioDelDia(it.detectado) }
    }
    val formatoHora = remember { DateFormat.getTimeInstance(DateFormat.SHORT) }
    var confirmarLimpiar by remember { mutableStateOf(false) }

    // Al abrir la pestaña, si hace más de 6 horas que no se revisa, se revisa sola.
    LaunchedEffect(Unit) {
        if (System.currentTimeMillis() - almacen.ultimaActualizacion > Actualizador.INTERVALO_MS) {
            Actualizador.iniciar(ctx, almacen, ext, avisar = false)
        }
    }

    Column(modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp, top = 12.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                LText("Actualizaciones", LectorType.Display.copy(fontSize = 30.sp, letterSpacing = (-1).sp), maxLines = 1)
                LText(
                    if (Actualizador.corriendo) "Revisando ${Actualizador.hechas} de ${Actualizador.total}…"
                    else "Última revisión: ${hace(almacen.ultimaActualizacion)}",
                    LectorType.Label, color = LectorColors.Muted, maxLines = 1,
                )
            }
            if (lista.isNotEmpty() && !Actualizador.corriendo) {
                TextButton(onClick = { confirmarLimpiar = true }) { Text("Borrar") }
            }
            Box(
                Modifier.size(48.dp).clip(CircleShape)
                    .pressable { if (Actualizador.corriendo) Actualizador.cancelar() else Actualizador.iniciar(ctx, almacen, ext) },
                Alignment.Center,
            ) {
                if (Actualizador.corriendo) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.5.dp)
                } else {
                    Icon(Icons.Rounded.Refresh, "Buscar capítulos nuevos", tint = LectorColors.Text)
                }
            }
        }

        if (lista.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(32.dp), Alignment.Center) {
                LText(
                    "Aquí saldrán los capítulos nuevos de los mangas que añadas a tu biblioteca desde Explorar. " +
                        "Se revisan al abrir esta pestaña (cada 6 horas) o con el botón de actualizar.",
                    LectorType.Body, color = LectorColors.Muted,
                )
            }
        } else {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                grupos.forEach { (dia, filas) ->
                    item(key = "d_$dia") {
                        LText(
                            etiquetaDia(dia), LectorType.Section.copy(fontSize = 16.sp),
                            Modifier.padding(start = 24.dp, end = 24.dp, top = 18.dp, bottom = 6.dp),
                            color = LectorColors.Muted, maxLines = 1,
                        )
                    }
                    items(filas, key = { "${it.fuenteId}|${it.capUrl}" }) { a ->
                        FilaActualizacion(a, almacen, ext, formatoHora.format(Date(a.detectado)), cabecerasDe, alAbrir)
                    }
                }
            }
        }
    }

    if (confirmarLimpiar) {
        AlertDialog(
            onDismissRequest = { confirmarLimpiar = false },
            title = { Text("¿Borrar las actualizaciones?") },
            text = { Text("Solo se vacía esta lista. Tu biblioteca y tu progreso no cambian.") },
            confirmButton = {
                TextButton(onClick = { almacen.limpiarActualizaciones(); confirmarLimpiar = false }) { Text("Borrar") }
            },
            dismissButton = { TextButton(onClick = { confirmarLimpiar = false }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun FilaActualizacion(
    a: ActualizacionCap,
    almacen: Almacen,
    ext: ExtManager,
    hora: String,
    cabecerasDe: (Long) -> Map<String, String>,
    alAbrir: (ActualizacionCap) -> Unit,
) {
    val ctx = LocalContext.current
    // Las cabeceras se piden fuera del hilo principal (la primera vez puede cargar la extensión).
    val cabeceras by produceState<Map<String, String>?>(null, a.fuenteId) {
        value = withContext(Dispatchers.IO) { runCatching { cabecerasDe(a.fuenteId) }.getOrDefault(emptyMap()) }
    }
    val leido = ext.esLeido(a.fuenteId, a.capUrl)
    val nombre = a.capNombre.trim().trimEnd(':').trim().ifBlank { "Capítulo" }
    val cap = CapituloFuente(a.capUrl, a.capNombre, a.capFecha, a.scanlator, a.capNumero)
    val clave = "${a.fuenteId}|${a.capUrl}"
    val fraccion = GestorDescargas.progreso[clave]
    val version = GestorDescargas.version.value // al cambiar, se vuelve a mirar el disco
    val estado: EstadoDescarga = when {
        fraccion != null -> EstadoDescarga.Descargando(fraccion)
        version >= 0 && almacen.estaDescargado(a.fuenteId, a.capUrl) -> EstadoDescarga.Listo
        else -> EstadoDescarga.Libre
    }

    Row(
        Modifier.fillMaxWidth().resaltable { alAbrir(a) }.padding(start = 24.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(48.dp).aspectRatio(2f / 3f).clip(SuaveShape(8.dp)).background(LectorColors.CardHigh)) {
            val c = cabeceras
            if (c != null) ImagenRemota(a.portadaUrl, Modifier.fillMaxSize(), c)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            LText(
                a.titulo, LectorType.Section.copy(fontSize = 16.sp), maxLines = 1,
                color = if (leido) LectorColors.Muted else LectorColors.Text,
            )
            LText(
                "$nombre · $hora", LectorType.Label, maxLines = 1,
                color = if (leido) LectorColors.Muted.copy(alpha = 0.7f) else LectorColors.Muted,
            )
        }
        Spacer(Modifier.width(6.dp))
        BotonDescarga(estado, onClick = {
            val f = ext.fuentes.firstOrNull { it.id == a.fuenteId }
            val motor = Motor.actual
            when {
                estado is EstadoDescarga.Descargando -> GestorDescargas.cancelar(ctx, clave)
                estado is EstadoDescarga.Listo -> Avisos.mostrar("Ya está descargado")
                f == null || motor == null ->
                    Avisos.mostrar("Esa fuente no está disponible ahora")
                else -> GestorDescargas.iniciar(ctx, almacen, motor, f, cap, a.titulo) {}
            }
        })
    }
}
