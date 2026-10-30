package com.example.lector.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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

@Composable
private fun Etiqueta(texto: String) {
    LText(texto, LectorType.Body.copy(fontSize = 15.sp), Modifier.padding(start = 24.dp, end = 24.dp, top = 10.dp), color = LectorColors.Text)
}

@Composable
private fun Deslizador(valor: Float, rango: ClosedFloatingPointRange<Float>, pasos: Int = 0, onCambio: (Float) -> Unit) {
    DeslizadorExpresivo(valor, rango, onCambio, Modifier.fillMaxWidth().padding(horizontal = 24.dp), pasos = pasos)
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
        TituloSeccion(if (paraSerie) "Modo de lectura" else "Modo de lectura por defecto")
        ModoVisor.entries.forEach { m ->
            FilaRadio(nombreModoVisor(m), modo == m) {
                if (alElegirModo != null) alElegirModo(m, soloSerie) else almacen.modoVisor = m
            }
        }
        if (paraSerie) {
            FilaSwitch("Solo para esta serie", "Si lo apagas, el modo elegido pasa a ser el de todas", soloSerie) { soloSerie = it }
        }

        Divisor()
        TituloSeccion("Navegación")
        Etiqueta("Zonas de toque")
        FilaChips(ZonasToque.entries.map { nombreZonas(it) }, almacen.zonasVisor.ordinal) { almacen.zonasVisor = ZonasToque.entries[it] }
        Etiqueta("Invertir zonas de toque")
        FilaChips(InvertirZonas.entries.map { nombreInvertir(it) }, almacen.invertirZonas.ordinal) { almacen.invertirZonas = InvertirZonas.entries[it] }
        FilaSwitch("Animar el cambio de página", null, almacen.animarPaginas) { almacen.animarPaginas = it }
        FilaSwitch("Doble toque para ampliar", null, almacen.dobleToqueZoom) { almacen.dobleToqueZoom = it }
        FilaSwitch("Teclas de volumen", "Bajar el volumen avanza y subirlo retrocede", almacen.teclasVolumen) { almacen.elegirTeclasVolumen(it) }
        if (almacen.teclasVolumen) {
            FilaSwitch("Invertir las teclas de volumen", null, almacen.invertirVolumen) { almacen.invertirVolumen = it }
        }

        Divisor()
        TituloSeccion("Modos paginados")
        Etiqueta("Escala de la página")
        FilaChips(EscalaPagina.entries.map { nombreEscala(it) }, almacen.escalaPagina.ordinal) { almacen.escalaPagina = EscalaPagina.entries[it] }
        FilaSwitch("Recortar bordes", "Quita los márgenes lisos (blancos o negros) de cada página", almacen.recortarBordes) { almacen.elegirRecortarBordes(it) }
        FilaSwitch("Dividir páginas dobles", "Las páginas apaisadas se parten en dos", almacen.dividirDobles) { almacen.dividirDobles = it }
        if (almacen.dividirDobles) {
            FilaSwitch("Invertir el orden de las mitades", null, almacen.invertirDivision) { almacen.invertirDivision = it }
        }

        Divisor()
        TituloSeccion("Webtoon y vertical continuo")
        Etiqueta("Relleno lateral: ${almacen.rellenoLateral}%")
        FilaChips(listOf("0%", "5%", "10%", "15%", "20%", "25%"), almacen.rellenoLateral / 5) { almacen.rellenoLateral = it * 5 }
        FilaSwitch("Recortar bordes", "En la tira vertical", almacen.recortarWebtoon) { almacen.recortarWebtoon = it }
        FilaSwitch("Pasar al siguiente capítulo solo", "Al llegar al final de la tira", almacen.pasarCapituloSolo) { almacen.pasarCapituloSolo = it }

        Divisor()
        TituloSeccion("General")
        FilaSwitch("Mostrar las páginas de transición", "Entre capítulos, en los modos paginados", almacen.mostrarTransiciones) { almacen.mostrarTransiciones = it }
        FilaSwitch("Número de página", "Abajo, cuando el menú está oculto", almacen.mostrarNumeroPagina) { almacen.mostrarNumeroPagina = it }
        FilaSwitch("Pantalla completa", "Oculta las barras del sistema mientras lees", almacen.pantallaCompleta) { almacen.pantallaCompleta = it }
        FilaSwitch("Mantener la pantalla encendida", null, almacen.mantenerEncendida) { almacen.elegirMantenerEncendida(it) }
        Etiqueta("Páginas que se precargan: ${almacen.precargaPaginas}")
        FilaChips(listOf("2", "4", "6", "8", "10"), (almacen.precargaPaginas / 2 - 1).coerceIn(0, 4)) { almacen.precargaPaginas = (it + 1) * 2 }
        Etiqueta("Velocidad del autodesplazamiento: ${almacen.velocidadAuto.roundToInt()}")
        Deslizador(almacen.velocidadAuto, 1f..10f, 8) { almacen.velocidadAuto = it.roundToInt().toFloat() }

        Divisor()
        TituloSeccion("Pantalla")
        Etiqueta("Color de fondo")
        FilaChips(Fondo.entries.map { nombreFondoVisor(it) }, almacen.fondoLector.ordinal) { almacen.elegirFondoLector(Fondo.entries[it]) }
        Etiqueta("Orientación")
        FilaChips(Orientacion.entries.map { nombreOrientacionVisor(it) }, almacen.orientacion.ordinal) { almacen.elegirOrientacion(Orientacion.entries[it]) }
        FilaSwitch("Brillo personalizado", "Un brillo propio solo mientras lees", almacen.brilloPersonalizado) { almacen.elegirBrilloPersonalizado(it) }
        if (almacen.brilloPersonalizado) Deslizador(almacen.brillo, 0.01f..1f) { almacen.elegirBrillo(it) }

        Divisor()
        TituloSeccion("Color")
        FilaSwitch("Escala de grises", null, almacen.escalaGrises) { almacen.elegirEscalaGrises(it) }
        FilaSwitch("Invertir colores", "Útil para leer de noche páginas con fondo blanco", almacen.invertirColores) { almacen.elegirInvertirColores(it) }
        Etiqueta("Filtro de color")
        FilaChips(Tinte.entries.map { nombreTinteVisor(it) }, almacen.tinte.ordinal) { almacen.elegirTinte(Tinte.entries[it]) }
        if (almacen.tinte != Tinte.NINGUNO) {
            Etiqueta("Intensidad")
            Deslizador(almacen.intensidadTinte, 0.05f..0.6f) { almacen.elegirIntensidadTinte(it) }
        }
    }
}

/** Ajustes → Visor (pantalla completa). */
@Composable
fun AjustesVisorScreen(almacen: Almacen, onBack: () -> Unit) {
    PaginaAjustes("Visor", onBack) { AjustesVisorContenido(almacen) }
}
