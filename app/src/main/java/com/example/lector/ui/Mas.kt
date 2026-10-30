package com.example.lector.ui

import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.lector.Almacen
import com.example.lector.Comic
import com.example.lector.Descargas
import com.example.lector.ext.ExtManager

/** Páginas que se abren encima de las pestañas (con animación de deslizamiento). */
enum class Ruta {
    Categorias, Estadisticas, Datos, Ajustes, Apariencia, AjBiblioteca, AjSeguimiento,
    AjVisor, AjDescargas, AjExplorar, AjSeguridad, AjAvanzado, AcercaDe,
    Descargas, BusquedaGlobal, MigrarSerie,
    // Explorar / extensiones
    ListadosExt, ExtDetalle, FuenteDetalle,
    // Ficha de un manga de una fuente + lector online
    Ficha, LeerOnline
}

fun Ruta.padre(): Ruta? = when (this) {
    Ruta.Apariencia, Ruta.AjBiblioteca, Ruta.AjSeguimiento, Ruta.AjVisor, Ruta.AjDescargas, Ruta.AjExplorar,
    Ruta.AjSeguridad, Ruta.AjAvanzado, Ruta.AcercaDe -> Ruta.Ajustes
    Ruta.Ficha -> Ruta.FuenteDetalle
    Ruta.LeerOnline -> Ruta.Ficha
    else -> null
}

/** Pestaña "Más": todo lo que no cabe en la barra, igual que en Komikku. */
@Composable
fun MasScreen(almacen: Almacen, onRuta: (Ruta) -> Unit, onAdd: () -> Unit, onAddFolder: () -> Unit) {
    val ctx = LocalContext.current
    LaunchedEffect(Unit) { Descargas.cargar(ctx) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().padding(bottom = 110.dp)
    ) {
        Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), Alignment.Center) { LogoApp(Modifier.size(96.dp)) }
        FilaSwitch("Modo incógnito", "No se anota el historial de lectura", almacen.incognito) { almacen.incognito = it }
        Divisor()
        FilaAjuste(Icons.Rounded.Add, "Añadir cómic", "Archivo .cbz, .zip o .pdf de tu móvil", onAdd)
        FilaAjuste(Icons.Rounded.Add, "Añadir carpeta", "Una carpeta de imágenes (o con capítulos)", onAddFolder)
        Divisor()
        val pendientes = Descargas.cola.size
        FilaAjuste(
            Iconos.Descargar, "Cola de descargas",
            when {
                pendientes == 0 -> "Sin descargas pendientes"
                Descargas.pausado -> "En pausa · $pendientes pendientes"
                else -> "$pendientes pendientes"
            },
        ) { onRuta(Ruta.Descargas) }
        FilaAjuste(Icons.Rounded.Menu, "Categorías", null) { onRuta(Ruta.Categorias) }
        FilaAjuste(Icons.Rounded.Info, "Estadísticas", null) { onRuta(Ruta.Estadisticas) }
        FilaAjuste(Icons.Rounded.Share, "Datos y almacenamiento", null) { onRuta(Ruta.Datos) }
        Divisor()
        FilaAjuste(Icons.Rounded.Settings, "Ajustes", null) { onRuta(Ruta.Ajustes) }
        FilaAjuste(Icons.Rounded.Face, "Acerca de", null) { onRuta(Ruta.AcercaDe) }
    }
}

private fun duracion(ms: Long): String {
    val min = ms / 60_000
    return when {
        min < 1 -> "menos de 1 min"
        min < 60 -> "$min min"
        else -> "${min / 60} h ${min % 60} min"
    }
}

/** Estadísticas (como la pantalla de Komikku): biblioteca, capítulos, tiempo de lectura, seguimiento y fuentes. */
@Composable
fun EstadisticasScreen(comics: List<Comic>, almacen: Almacen, ext: ExtManager, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val enLinea = comics.count { it.enLinea }
    val terminados = comics.count { it.terminado() }
    val enCurso = comics.count { it.enCurso() }
    val sinEmpezar = comics.count { it.ultimaPagina == 0 && it.leido == 0L }
    val capsTotales = comics.filter { it.enLinea }.sumOf { it.paginas }
    val sinLeer = comics.filter { it.enLinea }.sumOf { it.sinLeer }
    val paginasLocales = comics.filter { it.leido > 0 && !it.enLinea }.sumOf { it.ultimaPagina + 1 }
    val seguidos = remember(almacen.version) { almacen.idsConSeguimiento().count { id -> comics.any { it.id == id } } }
    val descargas = remember(almacen.version) { almacen.totalDescargas() to almacen.bytesDescargas() }
    val porFuente = remember(comics, ext.fuentes) {
        comics.filter { it.enLinea }.groupingBy { it.fuenteId }.eachCount().entries.sortedByDescending { it.value }.take(6)
            .map { (id, n) -> (ext.fuentes.firstOrNull { it.id == id }?.nombre ?: "Fuente $id") to n }
    }

    PaginaAjustes("Estadísticas", onBack) {
        // Anillo de la biblioteca (terminados / en curso / sin empezar) que se dibuja al entrar.
        AnilloBiblioteca(terminados, enCurso, sinEmpezar, comics.size)
        TituloSeccion("Biblioteca")
        Fila2(StatCardMod("${comics.size}", "En la biblioteca"), StatCardMod("$enLinea", "De fuentes en línea"))
        Fila2(StatCardMod("${comics.size - enLinea}", "Cómics locales"), StatCardMod("${almacen.categorias().size}", "Categorías"))
        Fila2(StatCardMod("$terminados", "Terminados"), StatCardMod("$enCurso", "En curso"))
        Fila2(StatCardMod("$sinEmpezar", "Sin empezar"), StatCardMod("$seguidos", "Con seguimiento"))

        TituloSeccion("Capítulos")
        Fila2(StatCardMod("${ext.totalLeidos}", "Leídos"), StatCardMod("$sinLeer", "Sin leer"))
        Fila2(StatCardMod("$capsTotales", "En total"), StatCardMod("${descargas.first}", "Descargados"))
        LText(
            "Las descargas ocupan ${Formatter.formatShortFileSize(ctx, descargas.second)}",
            LectorType.Label, Modifier.padding(horizontal = 24.dp, vertical = 4.dp), color = LectorColors.Muted,
        )

        TituloSeccion("Lectura")
        Fila2(StatCardMod(duracion(almacen.tiempoLectura), "Tiempo leyendo"), StatCardMod("$paginasLocales", "Páginas (cómics locales)"))

        if (porFuente.isNotEmpty()) {
            TituloSeccion("Fuentes más usadas")
            val max = porFuente.first().second.coerceAtLeast(1)
            porFuente.forEach { (nombre, n) ->
                Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 6.dp)) {
                    Row {
                        LText(nombre, LectorType.Body, Modifier.weight(1f), maxLines = 1)
                        LText("$n", LectorType.Body, color = LectorColors.Muted)
                    }
                    Spacer(Modifier.height(6.dp))
                    // La barra crece desde cero, una tras otra.
                    val i = porFuente.indexOfFirst { it.first == nombre }
                    var lleno by remember { mutableStateOf(false) }
                    LaunchedEffect(Unit) { kotlinx.coroutines.delay(120L * i); lleno = true }
                    val f by androidx.compose.animation.core.animateFloatAsState(
                        if (lleno) n.toFloat() / max else 0f,
                        if (Mov.reducido) androidx.compose.animation.core.snap() else androidx.compose.animation.core.spring(0.8f, 120f), label = "barra",
                    )
                    Box(Modifier.fillMaxWidth().height(8.dp).clip(SuaveShape(4.dp)).background(LectorColors.PillFill)) {
                        Box(Modifier.fillMaxWidth(f).fillMaxHeight().clip(SuaveShape(4.dp)).background(MaterialTheme.colorScheme.primary))
                    }
                }
            }
        }
    }
}

/** Anillo con tres arcos que se dibujan al entrar (muelle) y el total contando en el centro. */
@Composable
private fun AnilloBiblioteca(terminados: Int, enCurso: Int, sinEmpezar: Int, total: Int) {
    val acento = MaterialTheme.colorScheme.primary
    val colores = listOf(acento, acento.copy(alpha = 0.55f), LectorColors.Muted.copy(alpha = 0.45f))
    val valores = listOf(terminados, enCurso, sinEmpezar)
    val suma = valores.sum().coerceAtLeast(1)
    var listo by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { listo = true }
    val p by androidx.compose.animation.core.animateFloatAsState(
        if (listo) 1f else 0f,
        if (Mov.reducido) androidx.compose.animation.core.snap() else androidx.compose.animation.core.spring(0.85f, 60f), label = "anillo",
    )
    Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp).entrada(0), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(150.dp), Alignment.Center) {
            androidx.compose.foundation.Canvas(Modifier.fillMaxSize().then(Modifier.rotate(-90f + (1f - p) * -40f))) {
                val grosor = 16.dp.toPx()
                val hueco = 6f
                var inicio = 0f
                drawArc(LectorColors.PillFill, 0f, 360f, false, style = androidx.compose.ui.graphics.drawscope.Stroke(grosor),
                    topLeft = androidx.compose.ui.geometry.Offset(grosor / 2, grosor / 2),
                    size = androidx.compose.ui.geometry.Size(size.width - grosor, size.height - grosor))
                valores.forEachIndexed { i, v ->
                    val barrido = (v.toFloat() / suma * 360f - hueco).coerceAtLeast(0f) * p
                    if (barrido > 0f) drawArc(
                        colores[i], inicio, barrido, false,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(grosor, cap = androidx.compose.ui.graphics.StrokeCap.Round),
                        topLeft = androidx.compose.ui.geometry.Offset(grosor / 2, grosor / 2),
                        size = androidx.compose.ui.geometry.Size(size.width - grosor, size.height - grosor),
                    )
                    inicio += v.toFloat() / suma * 360f * p
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                LText("${(total * p).toInt()}", LectorType.Display.copy(fontSize = 32.sp))
                LText("mangas", LectorType.Label, color = LectorColors.Muted)
            }
        }
        Spacer(Modifier.width(20.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf("Terminados", "En curso", "Sin empezar").forEachIndexed { i, t ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.entrada(i + 1)) {
                    Box(Modifier.size(12.dp).clip(SuaveShape(4.dp)).background(colores[i]))
                    Spacer(Modifier.width(10.dp))
                    Column {
                        LText(t, LectorType.Label, color = LectorColors.Muted)
                        LText("${valores[i]}", LectorType.Section.copy(fontSize = 18.sp))
                    }
                }
            }
        }
    }
}

private class DatoStat(val valor: String, val etiqueta: String)

private fun StatCardMod(valor: String, etiqueta: String) = DatoStat(valor, etiqueta)

@Composable
private fun Fila2(a: DatoStat, b: DatoStat) {
    Row(Modifier.padding(horizontal = 24.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        StatCard(a.valor, a.etiqueta, Modifier.weight(1f).entrada(1))
        StatCard(b.valor, b.etiqueta, Modifier.weight(1f).entrada(2))
    }
}
