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
        TituloSeccion("Biblioteca")
        Fila2(StatCardMod("${comics.size}", "En la biblioteca"), StatCardMod("$enLinea", "De fuentes en línea"))
        Fila2(StatCardMod("${comics.size - enLinea}", "Cómics locales"), StatCardMod("${almacen.categorias().size}", "Categorías"))
        Fila2(StatCardMod("$terminados", "Terminados"), StatCardMod("$enCurso", "En curso"))
        Fila2(StatCardMod("$sinEmpezar", "Sin empezar"), StatCardMod("$seguidos", "Con seguimiento"))

        TituloSeccion("Capítulos")
        Fila2(StatCardMod("${ext.leidos.size}", "Leídos"), StatCardMod("$sinLeer", "Sin leer"))
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
                    Box(Modifier.fillMaxWidth().height(6.dp).clip(SuaveShape(3.dp)).background(LectorColors.PillFill)) {
                        Box(Modifier.fillMaxWidth(n.toFloat() / max).fillMaxHeight().background(MaterialTheme.colorScheme.primary))
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
        StatCard(a.valor, a.etiqueta, Modifier.weight(1f))
        StatCard(b.valor, b.etiqueta, Modifier.weight(1f))
    }
}
