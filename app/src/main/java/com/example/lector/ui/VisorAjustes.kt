package com.example.lector.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.lector.Almacen
import com.example.lector.EscalaPagina
import com.example.lector.Fondo
import com.example.lector.InvertirZonas
import com.example.lector.ModoVisor
import com.example.lector.Orientacion
import com.example.lector.Tinte
import com.example.lector.ZonasToque
import kotlin.math.roundToInt

fun nombreZonas(z: ZonasToque) = when (z) {
    ZonasToque.AUTOMATICO -> "Automáticas"
    ZonasToque.L -> "En forma de L"
    ZonasToque.KINDLE -> "Kindle"
    ZonasToque.BORDES -> "Bordes"
    ZonasToque.IZQUIERDA_DERECHA -> "Izquierda y derecha"
    ZonasToque.DESACTIVADO -> "Desactivadas"
}

private fun nombreInvertir(i: InvertirZonas) = when (i) {
    InvertirZonas.NO -> "No"
    InvertirZonas.HORIZONTAL -> "Horizontal"
    InvertirZonas.VERTICAL -> "Vertical"
    InvertirZonas.AMBOS -> "Ambas"
}

private fun nombreEscala(e: EscalaPagina) = when (e) {
    EscalaPagina.AJUSTAR -> "Ajustar a la pantalla"
    EscalaPagina.AJUSTAR_ANCHO -> "Ajustar al ancho"
    EscalaPagina.AJUSTAR_ALTO -> "Ajustar al alto"
    EscalaPagina.ORIGINAL -> "Tamaño original"
}

private fun nombreFondoVisor(f: Fondo) = when (f) {
    Fondo.NEGRO -> "Negro"
    Fondo.GRIS -> "Gris"
    Fondo.BLANCO -> "Blanco"
}

private fun nombreOrientacionVisor(o: Orientacion) = when (o) {
    Orientacion.LIBRE -> "Automática"
    Orientacion.VERTICAL -> "Vertical"
    Orientacion.HORIZONTAL -> "Horizontal"
}

private fun nombreTinteVisor(t: Tinte) = when (t) {
    Tinte.NINGUNO -> "Ninguno"
    Tinte.CALIDO -> "Cálido"
    Tinte.SEPIA -> "Sepia"
    Tinte.ROJO -> "Rojo"
    Tinte.VERDE -> "Verde"
    Tinte.AZUL -> "Azul"
}

/** Fila de píldoras con desplazamiento lateral (una opción marcada). */
@Composable
fun FilaChips(etiquetas: List<String>, elegida: Int, onElegir: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        etiquetas.forEachIndexed { i, e -> ChipSel(e, i == elegida) { onElegir(i) } }
    }
}

// ───────── Piezas de la hoja de ajustes del visor ─────────

private val FormaBloque = SuaveShape(22.dp)

/** Título de sección: icono en círculo tonal de acento + texto grande en negrita. */
@Composable
private fun SeccionVisor(texto: String, icono: ImageVector) {
    val acento = MaterialTheme.colorScheme.primary
    Row(
        Modifier.fillMaxWidth().padding(start = 22.dp, end = 24.dp, top = 22.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(30.dp).clip(CircleShape).background(acento.copy(alpha = 0.18f)), Alignment.Center) {
            Icon(icono, null, tint = acento, modifier = Modifier.size(17.dp))
        }
        Spacer(Modifier.width(12.dp))
        LText(texto, LectorType.Section.copy(fontSize = 19.sp, fontWeight = FontWeight.Bold), color = LectorColors.Text)
    }
}

/** Bloque tipo tarjeta (como las filas de ajustes) con un título, un valor opcional en píldora y su control. */
@Composable
private fun BloqueVisor(titulo: String, valor: String? = null, content: @Composable ColumnScope.() -> Unit) {
    val acento = MaterialTheme.colorScheme.primary
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 8.dp)
            .clip(FormaBloque).background(LectorColors.Card).padding(top = 14.dp, bottom = 12.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            LText(titulo, LectorType.Section, Modifier.weight(1f), color = LectorColors.Text)
            if (valor != null) {
                Spacer(Modifier.width(8.dp))
                LText(
                    valor, LectorType.Label.copy(fontWeight = FontWeight.Bold),
                    Modifier.clip(CircleShape).background(acento.copy(alpha = 0.18f)).padding(horizontal = 10.dp, vertical = 3.dp),
                    color = acento,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        content()
    }
}

/** Píldora de opción del visor: la elegida, en acento lleno con un visto; las demás, tonales. */
@Composable
private fun ChipVisor(texto: String, elegido: Boolean, onClick: () -> Unit) {
    val acento = MaterialTheme.colorScheme.primary
    val radio by animateDpAsState(if (elegido) 18.dp else 10.dp, Muelles.espacialRapido(), label = "radioChip")
    val fondo by animateColorAsState(if (elegido) acento else LectorColors.PillFill, Muelles.efectos(), label = "fondoChip")
    val tinta by animateColorAsState(if (elegido) MaterialTheme.colorScheme.onPrimary else LectorColors.Text, Muelles.efectos(), label = "tintaChip")
    Row(
        Modifier.pressable { Tacto.tic(); onClick() }.clip(RoundedCornerShape(radio)).background(fondo)
            .height(36.dp).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (elegido) {
            Icon(Icons.Rounded.Check, null, tint = tinta, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
        }
        LText(texto, LectorType.Body, color = tinta, maxLines = 1)
    }
}

@Composable
private fun ChipsVisor(etiquetas: List<String>, elegida: Int, onElegir: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        etiquetas.forEachIndexed { i, e -> ChipVisor(e, i == elegida) { onElegir(i) } }
    }
}

@Composable
private fun GrupoVisor(opciones: List<String>, elegida: Int, onElegir: (Int) -> Unit) {
    GrupoBotones(opciones, elegida, onElegir, Modifier.padding(horizontal = 16.dp))
}

@Composable
private fun DeslizadorVisor(valor: Float, rango: ClosedFloatingPointRange<Float>, pasos: Int = 0, onCambio: (Float) -> Unit) {
    DeslizadorExpresivo(valor, rango, onCambio, Modifier.fillMaxWidth().padding(horizontal = 20.dp), pasos = pasos)
}

private fun iconoModo(m: ModoVisor): ImageVector = when (m) {
    ModoVisor.DERECHA_IZQUIERDA -> Icons.Rounded.ArrowBack
    ModoVisor.IZQUIERDA_DERECHA -> Icons.Rounded.ArrowForward
    ModoVisor.VERTICAL -> Icons.Rounded.KeyboardArrowDown
    ModoVisor.WEBTOON -> Icons.Rounded.Menu
    ModoVisor.CONTINUO -> Icons.Rounded.List
}

private fun nombreCortoModo(m: ModoVisor): Pair<String, String> = when (m) {
    ModoVisor.DERECHA_IZQUIERDA -> "Derecha a izquierda" to "Paginado · manga"
    ModoVisor.IZQUIERDA_DERECHA -> "Izquierda a derecha" to "Paginado · cómic"
    ModoVisor.VERTICAL -> "Vertical" to "Paginado hacia abajo"
    ModoVisor.WEBTOON -> "Webtoon" to "Tira continua"
    ModoVisor.CONTINUO -> "Continuo" to "Vertical con separación"
}

/** Tarjeta grande de un modo de lectura: icono, nombre y detalle; la elegida se tiñe de acento con muelle. */
@Composable
private fun TarjetaModo(m: ModoVisor, elegido: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val acento = MaterialTheme.colorScheme.primary
    val fondo by animateColorAsState(if (elegido) acento.copy(alpha = 0.16f) else LectorColors.Card, Muelles.efectos(), label = "fondoModo")
    val borde by animateColorAsState(if (elegido) acento else Color.Transparent, Muelles.efectos(), label = "bordeModo")
    val circulo by animateColorAsState(if (elegido) acento else LectorColors.PillFill, Muelles.efectos(), label = "circuloModo")
    val radio by animateDpAsState(if (elegido) 26.dp else 18.dp, Muelles.espacialRapido(), label = "radioModo")
    val forma = SuaveShape(radio)
    val (nombre, detalle) = nombreCortoModo(m)
    Column(
        modifier.pressableCon(0.97f) { Tacto.tic(); onClick() }.clip(forma).background(fondo).border(1.5.dp, borde, forma)
            .padding(horizontal = 14.dp, vertical = 14.dp),
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(circulo), Alignment.Center) {
            Icon(iconoModo(m), nombreModoVisor(m), tint = if (elegido) MaterialTheme.colorScheme.onPrimary else LectorColors.Text, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.height(12.dp))
        LText(nombre, LectorType.Body.copy(fontWeight = FontWeight.Bold), color = LectorColors.Text, maxLines = 2)
        Spacer(Modifier.height(2.dp))
        LText(detalle, LectorType.Label, color = LectorColors.Muted, maxLines = 2)
    }
}

/**
 * Todos los ajustes del visor (los mismos que Komikku agrupa en «Lectura / General / Pantalla / Color»).
 * Se usa en la hoja de ajustes del visor y en Ajustes → Visor. Si [alElegirModo] es null, el modo elegido pasa a
 * ser el modo por defecto.
 */
@Composable
fun AjustesVisorContenido(
    almacen: Almacen,
    modoActual: ModoVisor? = null,
    paraSerie: Boolean = false,
    alElegirModo: ((ModoVisor, Boolean) -> Unit)? = null,
) {
    var soloSerie by remember { mutableStateOf(paraSerie) }
    val modo = modoActual ?: almacen.modoVisor
    Column(Modifier.fillMaxWidth()) {
        SeccionVisor(if (paraSerie) "Modo de lectura" else "Modo de lectura por defecto", Icons.Rounded.Star)
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ModoVisor.entries.chunked(2).forEach { fila ->
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    fila.forEach { m ->
                        TarjetaModo(m, modo == m, {
                            if (alElegirModo != null) alElegirModo(m, soloSerie) else almacen.modoVisor = m
                        }, Modifier.weight(1f).fillMaxHeight())
                    }
                }
            }
        }
        if (paraSerie) {
            FilaSwitch("Solo para esta serie", "Si lo apagas, el modo elegido pasa a ser el de todas", soloSerie) { soloSerie = it }
        }

        SeccionVisor("Navegación", Icons.Rounded.Place)
        BloqueVisor("Zonas de toque") {
            ChipsVisor(ZonasToque.entries.map { nombreZonas(it) }, almacen.zonasVisor.ordinal) { almacen.zonasVisor = ZonasToque.entries[it] }
        }
        BloqueVisor("Invertir zonas de toque") {
            GrupoVisor(InvertirZonas.entries.map { nombreInvertir(it) }, almacen.invertirZonas.ordinal) { almacen.invertirZonas = InvertirZonas.entries[it] }
        }
        FilaSwitch("Animar el cambio de página", null, almacen.animarPaginas) { almacen.animarPaginas = it }
        FilaSwitch("Doble toque para ampliar", null, almacen.dobleToqueZoom) { almacen.dobleToqueZoom = it }
        FilaSwitch("Teclas de volumen", "Bajar el volumen avanza y subirlo retrocede", almacen.teclasVolumen) { almacen.elegirTeclasVolumen(it) }
        if (almacen.teclasVolumen) {
            FilaSwitch("Invertir las teclas de volumen", null, almacen.invertirVolumen) { almacen.invertirVolumen = it }
        }

        SeccionVisor("Modos paginados", Icons.Rounded.ArrowForward)
        BloqueVisor("Escala de la página") {
            ChipsVisor(EscalaPagina.entries.map { nombreEscala(it) }, almacen.escalaPagina.ordinal) { almacen.escalaPagina = EscalaPagina.entries[it] }
        }
        FilaSwitch("Recortar bordes", "Quita los márgenes lisos (blancos o negros) de cada página", almacen.recortarBordes) { almacen.elegirRecortarBordes(it) }
        FilaSwitch("Dividir páginas dobles", "Las páginas apaisadas se parten en dos", almacen.dividirDobles) { almacen.dividirDobles = it }
        if (almacen.dividirDobles) {
            FilaSwitch("Invertir el orden de las mitades", null, almacen.invertirDivision) { almacen.invertirDivision = it }
        }

        SeccionVisor("Webtoon y vertical continuo", Icons.Rounded.KeyboardArrowDown)
        BloqueVisor("Relleno lateral", "${almacen.rellenoLateral}%") {
            GrupoVisor(listOf("0", "5", "10", "15", "20", "25"), almacen.rellenoLateral / 5) { almacen.rellenoLateral = it * 5 }
        }
        FilaSwitch("Recortar bordes", "En la tira vertical", almacen.recortarWebtoon) { almacen.recortarWebtoon = it }
        FilaSwitch("Pasar al siguiente capítulo solo", "Al llegar al final de la tira", almacen.pasarCapituloSolo) { almacen.pasarCapituloSolo = it }

        SeccionVisor("General", Icons.Rounded.Settings)
        FilaSwitch("Mostrar las páginas de transición", "Entre capítulos, en los modos paginados", almacen.mostrarTransiciones) { almacen.mostrarTransiciones = it }
        FilaSwitch("Número de página", "Abajo, cuando el menú está oculto", almacen.mostrarNumeroPagina) { almacen.mostrarNumeroPagina = it }
        FilaSwitch("Pantalla completa", "Oculta las barras del sistema mientras lees", almacen.pantallaCompleta) { almacen.pantallaCompleta = it }
        FilaSwitch("Mantener la pantalla encendida", null, almacen.mantenerEncendida) { almacen.elegirMantenerEncendida(it) }
        BloqueVisor("Páginas que se precargan", "${almacen.precargaPaginas}") {
            GrupoVisor(listOf("2", "4", "6", "8", "10"), (almacen.precargaPaginas / 2 - 1).coerceIn(0, 4)) { almacen.precargaPaginas = (it + 1) * 2 }
        }
        BloqueVisor("Velocidad del autodesplazamiento", "${almacen.velocidadAuto.roundToInt()}") {
            DeslizadorVisor(almacen.velocidadAuto, 1f..10f, 8) { almacen.velocidadAuto = it.roundToInt().toFloat() }
        }

        SeccionVisor("Pantalla", Icons.Rounded.Face)
        BloqueVisor("Color de fondo") {
            GrupoVisor(Fondo.entries.map { nombreFondoVisor(it) }, almacen.fondoLector.ordinal) { almacen.elegirFondoLector(Fondo.entries[it]) }
        }
        BloqueVisor("Orientación") {
            GrupoVisor(Orientacion.entries.map { nombreOrientacionVisor(it) }, almacen.orientacion.ordinal) { almacen.elegirOrientacion(Orientacion.entries[it]) }
        }
        FilaSwitch("Brillo personalizado", "Un brillo propio solo mientras lees", almacen.brilloPersonalizado) { almacen.elegirBrilloPersonalizado(it) }
        if (almacen.brilloPersonalizado) BloqueVisor("Brillo", "${(almacen.brillo * 100).roundToInt()}%") {
            DeslizadorVisor(almacen.brillo, 0.01f..1f) { almacen.elegirBrillo(it) }
        }

        SeccionVisor("Color", Icons.Rounded.Create)
        FilaSwitch("Escala de grises", null, almacen.escalaGrises) { almacen.elegirEscalaGrises(it) }
        FilaSwitch("Invertir colores", "Útil para leer de noche páginas con fondo blanco", almacen.invertirColores) { almacen.elegirInvertirColores(it) }
        BloqueVisor("Filtro de color") {
            ChipsVisor(Tinte.entries.map { nombreTinteVisor(it) }, almacen.tinte.ordinal) { almacen.elegirTinte(Tinte.entries[it]) }
        }
        if (almacen.tinte != Tinte.NINGUNO) {
            BloqueVisor("Intensidad", "${(almacen.intensidadTinte * 100).roundToInt()}%") {
                DeslizadorVisor(almacen.intensidadTinte, 0.05f..0.6f) { almacen.elegirIntensidadTinte(it) }
            }
        }
    }
}

/** Ajustes → Visor (pantalla completa). */
@Composable
fun AjustesVisorScreen(almacen: Almacen, onBack: () -> Unit) {
    PaginaAjustes("Visor", onBack) { AjustesVisorContenido(almacen) }
}
