package com.example.lector.ui

// Pantalla «Personalización», portada de la de Mochy (ui/screens/gesture: GestureScreen, CustomisationCards,
// CustomisationEffects, CustomisationPreviews): misma estructura (atrás, título, buscador, secciones separadas por una
// línea), mismas tarjetas (redondeo con la esquina de muestra y su arco, slider fino con etiquetas, carrusel de estilos
// con su vista previa asomando abajo a la derecha y el borde del elegido, tarjetas con interruptor y vista previa en
// vivo, selector de dos opciones con la píldora que se desliza) y mismas medidas. El contenido es el de Shiori.

import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.graphics.toArgb
import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.layout.layout
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.example.lector.Almacen
import com.example.lector.Paletas
import com.example.lector.colorDePaleta
import com.example.lector.ModoTema
import com.example.lector.ModoVisor
import com.example.lector.Vista
import androidx.compose.animation.Crossfade
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.ui.text.style.TextAlign
import com.example.lector.ui.mochy.ChapterSwipeAction
import com.example.lector.ui.mochy.GestosCapitulos
import com.example.lector.ui.mochy.SwipePreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

// ───────────── Medidas y estilos de Mochy (CustomisationCards.kt) ─────────────

/** Cuánto entran las tarjetas desde los lados de la pantalla. */
private val MargenPagina = 24.dp
/** A partir de aquí la página deja de ensancharse (en tablet se lee como un móvil ancho). */
private val AnchoMaxPagina = 680.dp
/** El slider del redondeo va de esquina recta a esto (dp). */
private const val RadioMaxPortada = 45f

private val FormaTarjeta = RoundedCornerShape(22.dp)
private val FormaTarjetaSlider = RoundedCornerShape(20.dp)
private val AltoTarjetaRedondeo = 186.dp
private val AltoTarjetaEstilo = 187.dp
private val SangriaTexto = 22.dp

// La esquina de portada de muestra: pegada arriba a la derecha de la tarjeta, de este tamaño.
private val AnchoMuestra = 146.dp
private val AltoMuestra = 135.dp
// El arco blanco va a esta distancia de la esquina, con tramos rectos de este largo.
private val HuecoArco = 19.dp
private val ColaArco = 12.dp

private val AnchoVista = 133.dp
private val FormaMarco = RoundedCornerShape(topStart = 15.dp, topEnd = 15.dp)
private val FormaPildora = RoundedCornerShape(14.dp)

private val EstiloTituloPagina = TextStyle(fontFamily = LectorFont, fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 38.sp, letterSpacing = (-0.04).em)
private val EstiloEtiquetaTarjeta = TextStyle(fontFamily = LectorFont, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 14.sp, letterSpacing = (-0.03).em)
private val EstiloValorTarjeta = TextStyle(fontFamily = LectorFont, fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 34.sp, letterSpacing = (-0.045).em)
private val EstiloEtiquetaPeque = TextStyle(fontFamily = LectorFont, fontWeight = FontWeight.Medium, fontSize = 9.sp, letterSpacing = (-0.01).em)
private val EstiloPie = TextStyle(fontFamily = LectorFont, fontWeight = FontWeight.Medium, fontSize = 10.sp, letterSpacing = (-0.01).em)
private val EstiloTituloSlider = TextStyle(fontFamily = LectorFont, fontWeight = FontWeight.Bold, fontSize = 13.sp, letterSpacing = (-0.03).em)
private val EstiloTextoPildora = TextStyle(fontFamily = LectorFont, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, letterSpacing = (-0.02).em)

// Colores de LabColors de Mochy, con su equivalente en tema claro.
private object ColP {
    private val o get() = TemaActual.oscuro
    val Tarjeta get() = if (o) Color(0xFF1C1C1E) else Color.White
    val Fila get() = if (o) Color(0xFF26262A) else Color(0xFFEDEDF1)
    val Marco get() = if (o) Color(0xFF2B2B2D) else Color(0xFFE6E6EA)
    val Texto get() = LectorColors.Text
    val Arte get() = if (o) Brush.verticalGradient(listOf(Color(0xFF636365), Color(0xFF3F3F41))) else Brush.verticalGradient(listOf(Color(0xFFC9C9CE), Color(0xFFAEAEB4)))
    val ArteClaro get() = if (o) Color(0xFF7C7C7E) else Color(0xFFB9B9BF)
    val Tinta get() = LectorColors.Text.copy(alpha = 0.92f)
    val Hueso get() = LectorColors.Text.copy(alpha = 0.2f)
    val HuesoTenue get() = LectorColors.Text.copy(alpha = 0.1f)
    val Muestra get() = if (o) Color(0xFFD9D9D9) else Color(0xFF8E8E96)
}

/** Las velocidades en las que se para el slider de animación, de la más lenta a la más rápida (las de Mochy). */
val VelocidadesAnimacion = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)
fun textoVelocidad(v: Float): String = (if (v % 1f == 0f) v.toInt().toString() else v.toString().trimEnd('0')) + "x"

// ───────────── Secciones ─────────────

/** Las partes de la página en orden, con las palabras con las que las encuentra el buscador. */
private enum class SeccionP(val titulo: String, val palabras: String) {
    REDONDEO("Redondeo de portadas", "redondeo portadas esquinas radio cuadradas redondas"),
    BIBLIOTECA("Vista de la biblioteca", "biblioteca vista cuadricula compacta lista portadas"),
    LECTURA("Modo de lectura", "lector modo lectura paginado manga webtoon tira vertical continuo"),
    TEMA("Tema", "tema claro oscuro negro sistema"),
    ACENTO("Color de acento", "color acento paleta sistema"),
    CRISTAL("Cristal", "cristal desenfoque blur liquid glass solido superficies barra"),
    FONDO("Fondo", "fondo aurora liso color vivo"),
    ANIMACION("Animación", "animacion animaciones velocidad movimiento rapido lento quitar"),
    INICIO("Animación de inicio", "inicio arranque agujero negro abrir"),
    RESPUESTA("Respuesta", "luz tocar tactil portada color ficha"),
    GESTOS("Gestos", "gestos deslizar izquierda derecha capitulos marcar leido descargar"),
    ESTILO("Estilo", "estilo mochy bitchord esquinas continuas menu mantener pulsado catalogo actualizacion ventana indicador descargas carga orbes thinking orbs");

    fun coincide(q: String): Boolean {
        val palabrasQ = normalizar(q).split(' ').filter { it.isNotBlank() }
        if (palabrasQ.isEmpty()) return true
        val pajar = normalizar("$palabras $titulo")
        return palabrasQ.all { it in pajar }
    }
}

private fun normalizar(t: String) = java.text.Normalizer.normalize(t.lowercase(), java.text.Normalizer.Form.NFD).replace(Regex("\\p{M}"), "")

/** Ajustes → Personalización (la «Customisation» de Mochy). */
@Composable
fun PersonalizacionScreen(almacen: Almacen, onBack: () -> Unit) {
    var q by rememberSaveable { mutableStateOf("") }
    val arriba = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val abajo = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val vistas = SeccionP.entries.filter { it.coincide(q) }
    Box(
        Modifier.fillMaxSize().background(LectorColors.Bg).verticalScroll(rememberScrollState()),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            Modifier.widthIn(max = AnchoMaxPagina).fillMaxWidth().padding(horizontal = MargenPagina)
                .padding(top = arriba + 18.dp, bottom = abajo + 32.dp),
        ) {
            BotonAtras(onBack)
            Spacer(Modifier.height(14.dp))
            LText("Personalización", EstiloTituloPagina)
            Spacer(Modifier.height(16.dp))
            CampoBusqueda(q, { q = it }, "Buscar")
            Spacer(Modifier.height(18.dp))
            if (vistas.isEmpty()) {
                LText("Nada coincide con «$q».", LectorType.Body.copy(fontSize = 15.sp), Modifier.padding(horizontal = 4.dp, vertical = 12.dp), color = LectorColors.Muted)
            }
            vistas.forEachIndexed { i, s ->
                if (i > 0) LineaSeccion()
                Box(Modifier.entrada(i)) { ContenidoSeccion(s, almacen) }
            }
        }
    }
}

@Composable
private fun ContenidoSeccion(s: SeccionP, almacen: Almacen) {
    val sangrar = Modifier.sangrarHorizontal(MargenPagina)
    val radio = Aspecto.radioPortada
    when (s) {
        SeccionP.REDONDEO -> SeccionRedondeo(radio) { Aspecto.elegirRadioPortada(it) }
        SeccionP.BIBLIOTECA -> CarruselEstilos(
            "Vista de la biblioteca", Vista.entries, almacen.vista, { nombreCortoVista(it) }, { almacen.elegirVista(it) }, sangrar,
        ) { VistaBibliotecaPrevia(it, radio.dp) }
        SeccionP.LECTURA -> CarruselEstilos(
            "Modo de lectura", ModoVisor.entries, almacen.modoVisor, { nombreCortoModo(it) }, { almacen.modoVisor = it }, sangrar,
        ) { ModoLecturaPrevia(it) }
        SeccionP.TEMA -> CarruselEstilos(
            "Tema", ModoTema.entries, almacen.modoTema, { nombreCortoTema(it) }, { almacen.modoTema = it }, sangrar,
        ) { TemaPrevia(it) }
        SeccionP.ACENTO -> SeccionAcento(almacen)
        SeccionP.CRISTAL -> SeccionCristal()
        SeccionP.FONDO -> CarruselEstilos(
            "Fondo", FondoApp.entries, Aspecto.fondo, { it.nombre }, { Aspecto.elegir(it) }, sangrar,
        ) { f ->
            val acento = MaterialTheme.colorScheme.primary
            Box(Modifier.fillMaxSize().background(LectorColors.Bg)) {
                FondoVivo(f, Modifier.fillMaxSize(), acento = acento)
                VistaBarra(Aspecto.superficie, Modifier.align(Alignment.BottomCenter))
            }
        }
        SeccionP.ANIMACION -> SeccionAnimacion()
        SeccionP.INICIO -> Column {
            CarruselEstilos(
                "Animación de inicio", AnimArranque.entries, Aspecto.arranque, { it.nombre }, { Aspecto.elegir(it) }, sangrar,
            ) { a ->
                Box(Modifier.fillMaxSize().background(if (a == AnimArranque.NINGUNA) LectorColors.Bg else Color.Black), Alignment.Center) {
                    when (a) {
                        AnimArranque.AGUJERO -> LogoApp(Modifier.size(70.dp), conFondo = false)
                        AnimArranque.RAPIDA -> LogoApp(Modifier.size(46.dp).graphicsLayer { alpha = 0.55f }, conFondo = false)
                        AnimArranque.NINGUNA -> VistaBarra(Aspecto.superficie, Modifier.align(Alignment.BottomCenter))
                    }
                }
            }
            Spacer(Modifier.height(11.dp))
            BotonExpresivo("Ver animación", Icons.Rounded.Refresh, { ArranqueRevelado.repetir++ }, estilo = EstiloBoton.TONAL)
        }
        SeccionP.RESPUESTA -> Column {
            TarjetaEfecto("Luz al tocar", Aspecto.luzTactil, { Aspecto.elegirLuz(it) }, preview = { LuzPrevia() }) {
                LText("Un brillo suave sigue a tu dedo en botones, portadas y la barra.", EstiloPie, color = LectorColors.Muted)
            }
            Spacer(Modifier.height(11.dp))
            TarjetaEfecto("Color de la portada", almacen.colorPortada, { almacen.elegirColorPortada(it) }, preview = { PortadaColorPrevia(almacen.colorPortada) }) {
                LText("La ficha y el lector toman el color de la portada.", EstiloPie, color = LectorColors.Muted)
            }
        }
        SeccionP.GESTOS -> SeccionGestos()
        SeccionP.ESTILO -> SeccionEstilo()
    }
}

private fun nombreCortoVista(v: Vista) = when (v) {
    Vista.CUADRICULA_COMODA -> "Cómoda"
    Vista.CUADRICULA_COMPACTA -> "Compacta"
    Vista.SOLO_PORTADA -> "Portadas"
    Vista.LISTA -> "Lista"
}

private fun nombreCortoModo(m: ModoVisor) = when (m) {
    ModoVisor.DERECHA_IZQUIERDA -> "Manga"
    ModoVisor.IZQUIERDA_DERECHA -> "Cómic"
    ModoVisor.VERTICAL -> "Vertical"
    ModoVisor.WEBTOON -> "Webtoon"
    ModoVisor.CONTINUO -> "Continuo"
}

private fun nombreCortoTema(m: ModoTema) = when (m) {
    ModoTema.SISTEMA -> "Sistema"
    ModoTema.OSCURO_PURO -> "Negro puro"
    ModoTema.OSCURO -> "Oscuro"
    ModoTema.CLARO -> "Claro"
}

/** Línea fina entre secciones (SectionRule de Mochy). */
@Composable
private fun LineaSeccion() {
    Box(Modifier.padding(top = 18.dp, bottom = 20.dp).fillMaxWidth().height(1.dp).background(LectorColors.Text.copy(alpha = 0.1f)))
}

/** Deja que un hijo llegue hasta los bordes de la pantalla saliéndose del margen del padre (bleedHorizontally de Mochy). */
private fun Modifier.sangrarHorizontal(margen: Dp): Modifier = this.layout { m, c ->
    val extra = margen.roundToPx() * 2
    val p = m.measure(c.copy(maxWidth = c.maxWidth + extra, minWidth = (c.minWidth + extra).coerceAtMost(c.maxWidth + extra)))
    layout(c.maxWidth, p.height) { p.place(-margen.roundToPx(), 0) }
}

// ───────────── Redondeo (CoverRoundingSection) ─────────────

@Composable
private fun SeccionRedondeo(radio: Float, alCambiar: (Float) -> Unit) {
    val tacto = LocalHapticFeedback.current
    var ultimo by remember { mutableIntStateOf((radio / 5f).roundToInt()) }
    Column(Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(AltoTarjetaRedondeo).clip(FormaTarjeta).background(ColP.Tarjeta)) {
            MuestraRedondeo(radio, Modifier.matchParentSize())
            LText("Redondeo de portadas", EstiloEtiquetaTarjeta, Modifier.padding(start = SangriaTexto, top = SangriaTexto), color = ColP.Texto.copy(alpha = 0.9f))
            LText("${radio.roundToInt()} dp", EstiloValorTarjeta, Modifier.align(Alignment.BottomStart).padding(start = SangriaTexto, bottom = 24.dp))
        }
        Spacer(Modifier.height(11.dp))
        TarjetaSlider(
            "Ajústalo", "Cuadradas", "Más redondas", radio / RadioMaxPortada,
            alCambiar = { f ->
                val v = f * RadioMaxPortada
                // Un toque cada 5: el arrastre se siente con muescas.
                val t = (v / 5f).roundToInt()
                if (t != ultimo) { ultimo = t; tacto.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
                alCambiar(v)
            },
        )
    }
}

/** La esquina de portada metida arriba a la derecha de la tarjeta, con el arco blanco abrazando su curva. */
@Composable
private fun MuestraRedondeo(radio: Float, modifier: Modifier) {
    val relleno = ColP.Muestra
    val arco = ColP.Texto
    Canvas(modifier) {
        val r = radio.coerceIn(0f, RadioMaxPortada).dp.toPx()
        val izq = size.width - AnchoMuestra.toPx()
        val fondo = AltoMuestra.toPx()
        // Solo se ve la esquina de abajo a la izquierda; las otras tres se salen de la tarjeta.
        drawRoundRect(relleno, Offset(izq, -r), Size(size.width - izq + r, fondo + r), CornerRadius(r, r))
        val g = HuecoArco.toPx(); val cola = ColaArco.toPx()
        val ra = r + g; val x = izq - g; val y = fondo + g
        val p = Path().apply {
            moveTo(x, y - ra - cola)
            lineTo(x, y - ra)
            arcTo(Rect(x, y - ra * 2f, x + ra * 2f, y), 180f, -90f, false)
            lineTo(x + ra + cola, y)
        }
        drawPath(p, arco, style = Stroke(4.5.dp.toPx(), cap = StrokeCap.Round))
    }
}

/** La tarjeta fina bajo una grande: título (y valor), una línea de slider y qué significa cada extremo. */
@Composable
private fun TarjetaSlider(
    titulo: String, inicio: String, fin: String, fraccion: Float, alCambiar: (Float) -> Unit,
    modifier: Modifier = Modifier, valor: String? = null, activo: Boolean = true,
) {
    Column(
        modifier.fillMaxWidth().clip(FormaTarjetaSlider).background(ColP.Tarjeta)
            .graphicsLayer { alpha = if (activo) 1f else 0.45f }
            .padding(start = SangriaTexto, end = SangriaTexto, top = 16.dp, bottom = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            LText(titulo, EstiloTituloSlider, Modifier.weight(1f))
            if (valor != null) LText(valor, EstiloTituloSlider, color = ColP.Texto.copy(alpha = 0.7f))
        }
        Spacer(Modifier.height(4.dp))
        SliderFino(fraccion, alCambiar, activo)
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            LText(inicio, EstiloEtiquetaPeque, color = ColP.Texto.copy(alpha = 0.5f))
            LText(fin, EstiloEtiquetaPeque, color = ColP.Texto.copy(alpha = 0.5f))
        }
    }
}

/** Una línea: la parte clara es cuánto va; se toca o se arrastra en cualquier punto (BareSlider). */
@Composable
private fun SliderFino(fraccion: Float, alCambiar: (Float) -> Unit, activo: Boolean) {
    var ancho by remember { mutableFloatStateOf(1f) }
    val visto = fraccion.coerceIn(0f, 1f)
    val vistoAhora by rememberUpdatedState(visto)
    val cambiar by rememberUpdatedState(alCambiar)
    fun desdeX(x: Float) = cambiar((x / ancho).coerceIn(0f, 1f))
    // El punto del dedo: así un slider por pasos se mueve aunque cada movimiento sea menor que un paso.
    var xArrastre by remember { mutableFloatStateOf(0f) }
    val estado = rememberDraggableState { d -> xArrastre += d; desdeX(xArrastre) }
    val claro = ColP.Texto
    Canvas(
        Modifier.fillMaxWidth().height(20.dp)
            .onSizeChanged { ancho = it.width.toFloat().coerceAtLeast(1f) }
            .pointerInput(activo) { if (activo) detectTapGestures { desdeX(it.x) } }
            .draggable(estado, Orientation.Horizontal, enabled = activo, onDragStarted = { xArrastre = vistoAhora * ancho }),
    ) {
        val y = size.height / 2f
        val g = 3.dp.toPx()
        val a = g / 2f; val b = size.width - g / 2f
        drawLine(claro.copy(alpha = 0.2f), Offset(a, y), Offset(b, y), g, StrokeCap.Round)
        drawLine(claro, Offset(a, y), Offset(a + (b - a) * visto, y), g, StrokeCap.Round)
    }
}

// ───────────── Carrusel de estilos (StylePagerSection / StyleCard / PreviewFrame) ─────────────

/** Una fila deslizable de tarjetas de estilo: deslizar solo mira; tocar una la usa y la recuadra. */
@Composable
private fun <T> CarruselEstilos(
    categoria: String, estilos: List<T>, elegido: T, nombre: (T) -> String, alElegir: (T) -> Unit,
    modifier: Modifier = Modifier, previa: @Composable BoxScope.(T) -> Unit,
) {
    val pager = rememberPagerState(initialPage = estilos.indexOf(elegido).coerceAtLeast(0)) { estilos.size }
    val tacto = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    Column(modifier.fillMaxWidth()) {
        HorizontalPager(
            state = pager,
            // La siguiente tarjeta asoma por la derecha: se ve que hay más.
            contentPadding = PaddingValues(start = MargenPagina, end = MargenPagina + 4.dp),
            pageSpacing = 16.dp,
            modifier = Modifier.fillMaxWidth(),
        ) { pagina ->
            val e = estilos[pagina]
            TarjetaEstilo(categoria, nombre(e), e == elegido, {
                if (e != elegido) { tacto.performHapticFeedback(HapticFeedbackType.TextHandleMove); alElegir(e) }
                if (pager.currentPage != pagina) scope.launch { pager.animateScrollToPage(pagina) }
            }) { previa(e) }
        }
        LText("Desliza para ver más, toca para usarlo", EstiloPie, Modifier.padding(start = MargenPagina, top = 10.dp), color = ColP.Texto.copy(alpha = 0.45f))
    }
}

@Composable
private fun TarjetaEstilo(categoria: String, titulo: String, elegido: Boolean, alTocar: () -> Unit, previa: @Composable BoxScope.() -> Unit) {
    val aro by animateColorAsState(if (elegido) ColP.Texto.copy(alpha = 0.9f) else Color.Transparent, tween(220), label = "aro")
    Box(
        Modifier.fillMaxWidth().height(AltoTarjetaEstilo).pressableCon(0.97f, alTocar).clip(FormaTarjeta).background(ColP.Tarjeta),
    ) {
        MarcoPrevia(Modifier.align(Alignment.BottomEnd), content = previa)
        LText(categoria, EstiloEtiquetaTarjeta, Modifier.padding(start = SangriaTexto, top = SangriaTexto), color = ColP.Texto.copy(alpha = 0.9f))
        // Un nombre largo se encoge para caber junto a la vista previa en vez de partirse.
        Box(Modifier.align(Alignment.BottomStart).padding(start = SangriaTexto, bottom = 24.dp, end = AnchoVista + 28.dp)) {
            TituloAjustable(titulo, maximo = 32.sp, minimo = 20.sp)
        }
        // Se dibuja al final para que el borde pase también por encima de la vista previa.
        Box(Modifier.matchParentSize().border(2.dp, aro, FormaTarjeta))
    }
}

/** La pantallita que asoma por abajo a la derecha de la tarjeta, hundiéndose en ella al pie. */
@Composable
private fun MarcoPrevia(modifier: Modifier = Modifier, ancho: Dp = AnchoVista, arriba: Dp = 23.dp, content: @Composable BoxScope.() -> Unit) {
    val tarjeta = ColP.Tarjeta
    Box(
        modifier.padding(top = arriba, end = 18.dp).width(ancho).fillMaxHeight().clip(FormaMarco).background(ColP.Marco)
            .drawWithCache {
                val y0 = size.height * 0.72f
                val pie = Brush.verticalGradient(0f to Color.Transparent, 1f to tarjeta, startY = y0, endY = size.height)
                onDrawWithContent { drawContent(); drawRect(pie, topLeft = Offset(0f, y0)) }
            },
        content = content,
    )
}

// ───────────── Tarjetas con interruptor (EffectCard / PillSwitchRow / MiniSegmented) ─────────────

/** Interruptor en la esquina, lo que controla a la izquierda y una vista previa en vivo asomando a la derecha. */
@Composable
private fun TarjetaEfecto(
    titulo: String, activo: Boolean, alCambiar: (Boolean) -> Unit,
    modifier: Modifier = Modifier, valor: String? = null, anchoPrevia: Dp = AnchoVista,
    preview: @Composable BoxScope.() -> Unit, content: @Composable ColumnScope.() -> Unit,
) {
    Box(Modifier.then(modifier).fillMaxWidth().height(AltoTarjetaEstilo).clip(FormaTarjeta).background(ColP.Tarjeta)) {
        MarcoPrevia(Modifier.align(Alignment.BottomEnd), ancho = anchoPrevia, arriba = 58.dp, content = preview)
        LText(titulo, EstiloEtiquetaTarjeta, Modifier.padding(start = SangriaTexto, top = SangriaTexto), color = ColP.Texto.copy(alpha = 0.9f))
        Interruptor(activo, alCambiar, Modifier.align(Alignment.TopEnd).padding(top = 16.dp, end = 18.dp))
        Column(Modifier.padding(start = SangriaTexto, top = 52.dp, end = anchoPrevia + 30.dp), content = content)
        if (valor != null) LText(valor, EstiloValorTarjeta, Modifier.align(Alignment.BottomStart).padding(start = SangriaTexto, bottom = 24.dp))
    }
}

/** Selector de dos (o más) opciones con una píldora que se desliza a la elegida. */
@Composable
private fun SelectorMini(opciones: List<String>, elegida: Int, alElegir: (Int) -> Unit, activo: Boolean, fondo: Color = ColP.Tarjeta) {
    BoxWithConstraints(Modifier.fillMaxWidth().height(24.dp).clip(RoundedCornerShape(12.dp)).background(fondo).padding(2.dp)) {
        val tramo = maxWidth / opciones.size
        val x by animateDpAsState(tramo * elegida, spring(dampingRatio = 0.82f, stiffness = 520f), label = "mini")
        Box(Modifier.offset(x = x).width(tramo).fillMaxHeight().clip(RoundedCornerShape(10.dp)).background(ColP.Texto))
        Row(Modifier.fillMaxWidth().fillMaxHeight()) {
            opciones.forEachIndexed { i, t ->
                val c by animateColorAsState(if (i == elegida) LectorColors.Bg else ColP.Texto, tween(160), label = "miniT")
                Box(
                    Modifier.weight(1f).fillMaxHeight().clickable(remember { MutableInteractionSource() }, null, enabled = activo) { alElegir(i) },
                    Alignment.Center,
                ) { LText(t, EstiloTextoPildora, color = c, maxLines = 1) }
            }
        }
    }
}

@Composable
private fun SeccionCristal() {
    val activo = Aspecto.superficie != Superficie.SOLIDO
    TarjetaEfecto(
        "Cristal", activo,
        { on -> Aspecto.elegir(if (on) Superficie.CRISTAL else Superficie.SOLIDO) },
        anchoPrevia = 104.dp,
        preview = {
            val acento = MaterialTheme.colorScheme.primary
            Box(Modifier.fillMaxSize().background(LectorColors.Bg)) {
                FondoVivo(FondoApp.COLOR, Modifier.fillMaxSize(), acento = acento)
                VistaBarra(Aspecto.superficie, Modifier.align(Alignment.BottomCenter))
            }
        },
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.graphicsLayer { alpha = if (activo) 1f else 0.45f },
        ) {
            Column(Modifier.fillMaxWidth().clip(FormaPildora).background(ColP.Fila).padding(horizontal = 12.dp, vertical = 9.dp)) {
                LText("Estilo", EstiloTextoPildora)
                Spacer(Modifier.height(7.dp))
                SelectorMini(listOf("Cristal", "Liquid"), if (Aspecto.superficie == Superficie.LIQUIDO) 1 else 0, {
                    Aspecto.elegir(if (it == 1) Superficie.LIQUIDO else Superficie.CRISTAL)
                }, activo)
            }
            LText("La barra, el mini lector y los avisos dejan ver lo de detrás.", EstiloPie, color = LectorColors.Muted)
        }
    }
}

@Composable
private fun SeccionAnimacion() {
    val tacto = LocalHapticFeedback.current
    val v = MovimientoApp.velocidad
    val i = VelocidadesAnimacion.indexOfFirst { it >= v - 0.01f }.takeIf { it >= 0 } ?: VelocidadesAnimacion.lastIndex
    Column(Modifier.fillMaxWidth()) {
        TarjetaEfecto(
            "Animación", MovimientoApp.activado, { MovimientoApp.elegirActivado(it) },
            valor = if (MovimientoApp.activado) textoVelocidad(v) else "No",
            preview = { AnimacionPrevia() },
        ) {
            LText(
                if (v == 1f) "Sigue la velocidad de animación de Android." else "Va por encima de la velocidad de Android.",
                EstiloPie, color = LectorColors.Muted,
            )
        }
        Spacer(Modifier.height(11.dp))
        TarjetaSlider(
            "Velocidad de animación", "Más lenta", "Más rápida", i / VelocidadesAnimacion.lastIndex.toFloat(),
            alCambiar = { f ->
                val n = VelocidadesAnimacion[(f * VelocidadesAnimacion.lastIndex).roundToInt()]
                if (n != MovimientoApp.velocidad) { tacto.performHapticFeedback(HapticFeedbackType.TextHandleMove); MovimientoApp.elegirVelocidad(n) }
            },
            valor = textoVelocidad(v), activo = MovimientoApp.activado,
        )
    }
}

@Composable
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
private fun SeccionAcento(almacen: Almacen) {
    val opciones = Paletas.fijas.map { it.nombre to it.color } +
        if (Build.VERSION.SDK_INT >= 31) listOf(Paletas.SISTEMA to colorDePaleta(Paletas.SISTEMA)) else emptyList()
    Column(Modifier.fillMaxWidth().clip(FormaTarjeta).background(ColP.Tarjeta).padding(SangriaTexto)) {
        LText("Color de acento", EstiloEtiquetaTarjeta, color = ColP.Texto.copy(alpha = 0.9f))
        Spacer(Modifier.height(16.dp))
        // Puntos de color en filas; el elegido crece y lleva un aro.
        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            opciones.forEach { (nombre, color) ->
                val sel = almacen.paleta == nombre
                val t by animateDpAsState(if (sel) 44.dp else 36.dp, Muelles.espacialRapido(), label = "punto")
                Box(Modifier.size(48.dp), Alignment.Center) {
                    Box(
                        Modifier.size(t).clip(CircleShape).background(color)
                            .then(if (sel) Modifier.border(3.dp, LectorColors.Bg, CircleShape) else Modifier)
                            .pressableCon(0.9f) { almacen.elegirPaleta(nombre) },
                        Alignment.Center,
                    ) { if (sel) Icon(Icons.Rounded.Check, nombre, tint = tintaSobre(color), modifier = Modifier.size(18.dp)) }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        LText(almacen.paleta, EstiloValorTarjeta.copy(fontSize = 24.sp))
        if (Build.VERSION.SDK_INT >= 31) LText("«Sistema» usa los colores de tu fondo de pantalla.", EstiloPie, color = LectorColors.Muted)
    }
}

private fun tintaSobre(c: Color): Color = if ((c.red * 0.299f + c.green * 0.587f + c.blue * 0.114f) > 0.6f) Color.Black else Color.White

// ───────────── Vistas previas (CustomisationPreviews) ─────────────

@Composable
private fun Barra(ancho: Dp, alto: Dp, color: Color = ColP.Tinta) {
    Box(Modifier.size(ancho, alto).background(color, RoundedCornerShape(50)))
}

private fun radioEscalado(r: Dp, e: Float): Dp = (r * e).coerceIn(0.dp, 14.dp)

@Composable
private fun VistaBibliotecaPrevia(v: Vista, radio: Dp) {
    val forma = RoundedCornerShape(radioEscalado(radio, 0.3f))
    val arte = ColP.Arte
    Column(Modifier.fillMaxSize().padding(start = 10.dp, end = 10.dp, top = 14.dp)) {
        Barra(48.dp, 6.dp)
        Spacer(Modifier.height(9.dp))
        when (v) {
            Vista.LISTA -> repeat(4) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(18.dp, 26.dp).clip(forma).background(arte))
                    Spacer(Modifier.width(7.dp))
                    Column { Barra(56.dp, 5.dp); Spacer(Modifier.height(4.dp)); Barra(34.dp, 4.dp, ColP.Hueso) }
                }
                Spacer(Modifier.height(7.dp))
            }
            else -> {
                val cols = if (v == Vista.CUADRICULA_COMODA) 2 else 3
                repeat(if (v == Vista.CUADRICULA_COMODA) 2 else 3) {
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        repeat(cols) {
                            Column(Modifier.weight(1f)) {
                                Box(Modifier.fillMaxWidth().height(if (cols == 2) 44.dp else 34.dp).clip(forma).background(arte)) {
                                    if (v == Vista.CUADRICULA_COMPACTA) Box(Modifier.align(Alignment.BottomStart).padding(3.dp)) { Barra(18.dp, 3.dp) }
                                }
                                if (v == Vista.CUADRICULA_COMODA) { Spacer(Modifier.height(4.dp)); Barra(30.dp, 4.dp) }
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                }
            }
        }
    }
}

/** Cada modo de lectura, moviéndose como de verdad: las páginas pasan en su sentido o la tira sube. */
@Composable
private fun ModoLecturaPrevia(m: ModoVisor) {
    val t = rememberInfiniteTransition(label = "modo")
    val f by t.animateFloat(0f, 1f, infiniteRepeatable(tween(2400, easing = LinearEasing)), label = "f")
    val p = if (Mov.reducido) 0f else f
    val arte = ColP.Arte
    Box(Modifier.fillMaxSize().clip(FormaMarco)) {
        when (m) {
            ModoVisor.WEBTOON, ModoVisor.CONTINUO -> Column(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp).graphicsLayer { translationY = -p * 70.dp.toPx() },
                verticalArrangement = Arrangement.spacedBy(if (m == ModoVisor.CONTINUO) 6.dp else 0.dp),
            ) {
                repeat(6) { k -> Box(Modifier.fillMaxWidth().height(60.dp).background(if (k % 2 == 0) arte else Brush.verticalGradient(listOf(ColP.ArteClaro, ColP.ArteClaro)))) }
            }
            else -> {
                // Una página que se va y la siguiente que entra, en el sentido del modo.
                val dx = when (m) { ModoVisor.DERECHA_IZQUIERDA -> 1f; ModoVisor.IZQUIERDA_DERECHA -> -1f; else -> 0f }
                val dy = if (m == ModoVisor.VERTICAL) -1f else 0f
                val s = ((p - 0.5f) / 0.35f).coerceIn(0f, 1f).let { it * it * (3 - 2 * it) }
                for (k in 0..1) {
                    val desplaz = (k - s)
                    Box(
                        Modifier.fillMaxSize().padding(10.dp).graphicsLayer {
                            translationX = desplaz * dx * size.width
                            translationY = desplaz * -dy * size.height
                        }.clip(RoundedCornerShape(6.dp)).background(if (k == 0) arte else Brush.verticalGradient(listOf(ColP.ArteClaro, ColP.ArteClaro))),
                    ) {
                        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Barra(40.dp, 4.dp, ColP.Hueso); Barra(56.dp, 4.dp, ColP.Hueso)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TemaPrevia(m: ModoTema) {
    val (fondo, tarjeta, texto) = when (m) {
        ModoTema.CLARO -> Triple(Color(0xFFF6F6F8), Color.White, Color(0xFF111114))
        ModoTema.OSCURO -> Triple(Color(0xFF111114), Color(0xFF26262C), Color.White)
        ModoTema.OSCURO_PURO -> Triple(Color.Black, Color(0xFF1E1E20), Color.White)
        ModoTema.SISTEMA -> Triple(Color(0xFF6B6B73), Color(0xFFB0B0B8), Color.White)
    }
    Column(Modifier.fillMaxSize().background(fondo).padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(50.dp, 7.dp).background(texto, RoundedCornerShape(50)))
        repeat(3) {
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(7.dp)).background(tarjeta).padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(14.dp).background(texto.copy(alpha = 0.35f), CircleShape))
                Spacer(Modifier.width(6.dp))
                Box(Modifier.size(54.dp, 5.dp).background(texto.copy(alpha = 0.8f), RoundedCornerShape(50)))
            }
        }
    }
}

/** Un punto que salta de lado a lado a la velocidad de la app; sin animaciones, salta de golpe (AnimationPreview). */
@Composable
private fun AnimacionPrevia() {
    val viaje = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        var destino = 1f
        while (true) {
            viaje.animateTo(destino, spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessLow))
            delay(650)
            destino = 1f - destino
        }
    }
    val punto = 14.dp
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.align(Alignment.Center).offset(y = (-14).dp).padding(horizontal = 16.dp).fillMaxWidth().height(2.dp).background(ColP.HuesoTenue, CircleShape))
        Box(
            Modifier.align(Alignment.CenterStart)
                .offset {
                    val recorrido = (AnchoVista - 20.dp - punto).toPx()
                    IntOffset((10.dp.toPx() + viaje.value * recorrido).roundToInt(), (-14).dp.roundToPx())
                }
                .size(punto).background(ColP.Texto, CircleShape),
        )
    }
}

/** Una píldora con la luz que la recorre, como al tocar. */
@Composable
private fun LuzPrevia() {
    val t = rememberInfiniteTransition(label = "luz")
    val x by t.animateFloat(0f, 1f, infiniteRepeatable(tween(2200, easing = LinearEasing)), label = "x")
    val encendida = Aspecto.luzTactil
    val claro = ColP.Texto
    Box(Modifier.fillMaxSize(), Alignment.TopCenter) {
        Box(
            Modifier.padding(top = 24.dp).size(96.dp, 36.dp).clip(RoundedCornerShape(50)).background(ColP.Fila)
                .drawWithCache {
                    onDrawWithContent {
                        drawContent()
                        if (encendida) {
                            val c = Offset(size.width * (if (Mov.reducido) 0.5f else x), size.height / 2f)
                            drawCircle(Brush.radialGradient(listOf(claro.copy(alpha = 0.35f), Color.Transparent), c, size.height * 1.4f), size.height * 1.4f, c)
                        }
                    }
                },
        )
    }
}

@Composable
private fun PortadaColorPrevia(activo: Boolean) {
    val acento = if (activo) Color(0xFFE0703A) else MaterialTheme.colorScheme.primary
    Column(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(acento.copy(alpha = if (activo) 0.45f else 0.08f), LectorColors.Bg))).padding(10.dp)) {
        Box(Modifier.align(Alignment.CenterHorizontally).size(34.dp, 48.dp).clip(RoundedCornerShape(5.dp)).background(Color(0xFFE0703A)))
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(50)).background(acento))
    }
}

// ───────────── Estilo: piezas de Mochy (por defecto) o de BitChord ─────────────

@Composable
private fun SeccionEstilo() {
    val e = com.example.lector.ui.mochy.EstiloApp
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(11.dp)) {
        TarjetaOpciones(
            "Esquinas",
            if (e.esquinas == com.example.lector.ui.mochy.EstiloApp.Esquinas.MOCHY) "La superelipse de Mochy en tarjetas, botones y portadas."
            else "La esquina continua de BitChord, como la de iOS.",
            com.example.lector.ui.mochy.EstiloApp.Esquinas.entries, e.esquinas, { it.nombre }, { e.elegir(it) },
        ) {
            // A 26 dp de radio sobre 64 dp se ve bien la diferencia entre las dos curvas.
            Box(Modifier.size(64.dp).clip(SuaveShape(26.dp, estilo = e.esquinas)).background(ColP.Arte))
        }
        TarjetaOpciones(
            "Mantener pulsado en catálogos",
            when (e.menuMantener) {
                com.example.lector.ui.mochy.EstiloApp.MenuMantener.MOCHY -> "Abre una hoja con la portada y sus acciones."
                com.example.lector.ui.mochy.EstiloApp.MenuMantener.ELEVADO -> "La portada se levanta y el fondo se desenfoca (BitChord)."
                com.example.lector.ui.mochy.EstiloApp.MenuMantener.DIRECTO -> "Añade o quita de la biblioteca al momento, sin menú."
            },
            com.example.lector.ui.mochy.EstiloApp.MenuMantener.entries, e.menuMantener, { it.nombre }, { e.elegir(it) },
        )
        TarjetaOpciones(
            "Ventana de actualización",
            if (e.ventanaActualizacion == com.example.lector.ui.mochy.EstiloApp.VentanaActualizacion.MOCHY) "Una hoja desde abajo con las novedades y botones en píldora."
            else "Una alerta centrada al estilo de iOS.",
            com.example.lector.ui.mochy.EstiloApp.VentanaActualizacion.entries, e.ventanaActualizacion, { it.nombre }, { e.elegir(it) },
        )
        Column(Modifier.fillMaxWidth().clip(FormaTarjeta).background(ColP.Tarjeta).padding(SangriaTexto)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LText("Indicador de descargas", EstiloEtiquetaTarjeta, Modifier.weight(1f), color = ColP.Texto.copy(alpha = 0.9f))
                Interruptor(e.verDescargas, { e.elegirVerDescargas(it) })
            }
            Spacer(Modifier.height(6.dp))
            LText("Una píldora encima de la barra mientras bajan capítulos; tócala para ver la cola.", EstiloPie, color = LectorColors.Muted)
            Spacer(Modifier.height(12.dp))
            Box(Modifier.graphicsLayer { alpha = if (e.verDescargas) 1f else 0.45f }) {
                SelectorMini(
                    com.example.lector.ui.mochy.EstiloApp.LadoDescargas.entries.map { it.nombre }, e.ladoDescargas.ordinal,
                    { e.elegir(com.example.lector.ui.mochy.EstiloApp.LadoDescargas.entries[it]) }, e.verDescargas, fondo = ColP.Fila,
                )
            }
        }
        Column(Modifier.fillMaxWidth().clip(FormaTarjeta).background(ColP.Tarjeta).padding(SangriaTexto)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LText("Portada panorámica", EstiloEtiquetaTarjeta, Modifier.weight(1f), color = ColP.Texto.copy(alpha = 0.9f))
                Interruptor(e.panoramica, { e.elegirPanoramica(it) })
            }
            Spacer(Modifier.height(6.dp))
            LText("Las portadas apaisadas se muestran a lo ancho en Historial y Actualizaciones, como en Komikku.", EstiloPie, color = LectorColors.Muted)
        }
        Column(Modifier.fillMaxWidth().clip(FormaTarjeta).background(ColP.Tarjeta).padding(SangriaTexto)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LText("Thinking orbs", EstiloEtiquetaTarjeta, Modifier.weight(1f), color = ColP.Texto.copy(alpha = 0.9f))
                Interruptor(e.orbes, { e.elegirOrbes(it) })
            }
            Spacer(Modifier.height(6.dp))
            LText(
                "Los orbes de puntos de Jakub Antalik en todas las cargas: indicadores, tirar para recargar y esqueletos. Apagado, el indicador clásico.",
                EstiloPie, color = LectorColors.Muted,
            )
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).graphicsLayer { alpha = if (e.orbes) 1f else 0.45f },
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                com.example.lector.ui.orbes.EstadoOrbe.entries.forEach { st ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        com.example.lector.ui.orbes.OrbePensante(
                            st, Modifier.size(52.dp), oscuro = TemaActual.oscuro,
                            tinte = if (e.orbesAcento) MaterialTheme.colorScheme.primary else null, quieto = !e.orbes || Mov.reducido,
                        )
                        Spacer(Modifier.height(4.dp))
                        LText(st.etiqueta.removeSuffix("…"), EstiloPie, color = LectorColors.Muted)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Box(Modifier.graphicsLayer { alpha = if (e.orbes) 1f else 0.45f }) {
                SelectorMini(
                    com.example.lector.ui.mochy.EstiloApp.VariarOrbes.entries.map { it.nombre }, e.variarOrbes.ordinal,
                    { e.elegir(com.example.lector.ui.mochy.EstiloApp.VariarOrbes.entries[it]) }, e.orbes, fondo = ColP.Fila,
                )
            }
            Spacer(Modifier.height(6.dp))
            LText(
                when (e.variarOrbes) {
                    com.example.lector.ui.mochy.EstiloApp.VariarOrbes.CAMBIANDO -> "Cada indicador va pasando por los nueve tipos."
                    com.example.lector.ui.mochy.EstiloApp.VariarOrbes.AZAR -> "Cada vez que aparece una carga sale un tipo distinto."
                    com.example.lector.ui.mochy.EstiloApp.VariarOrbes.TAREA -> "Cada espera usa el suyo: buscar, conectar, revisar, migrar…"
                },
                EstiloPie, color = LectorColors.Muted,
            )
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.graphicsLayer { alpha = if (e.orbes) 1f else 0.45f }) {
                LText("Con el color de acento", EstiloPie, Modifier.weight(1f), color = ColP.Texto.copy(alpha = 0.9f))
                Interruptor(e.orbesAcento, { if (e.orbes) e.elegirOrbesAcento(it) })
            }
        }
    }
}

/** Tarjeta con un título, una línea que explica la opción elegida y el selector de píldora; [previa] va a la derecha. */
@Composable
private fun <T> TarjetaOpciones(
    titulo: String, explicacion: String, opciones: List<T>, elegida: T, nombre: (T) -> String, alElegir: (T) -> Unit,
    previa: (@Composable () -> Unit)? = null,
) {
    Column(Modifier.fillMaxWidth().clip(FormaTarjeta).background(ColP.Tarjeta).padding(SangriaTexto)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                LText(titulo, EstiloEtiquetaTarjeta, color = ColP.Texto.copy(alpha = 0.9f))
                Spacer(Modifier.height(6.dp))
                androidx.compose.animation.AnimatedContent(
                    explicacion, label = "explica",
                    transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
                ) { LText(it, EstiloPie, color = LectorColors.Muted) }
            }
            if (previa != null) { Spacer(Modifier.width(14.dp)); previa() }
        }
        Spacer(Modifier.height(12.dp))
        SelectorMini(opciones.map(nombre), opciones.indexOf(elegida).coerceAtLeast(0), { alElegir(opciones[it]) }, true, fondo = ColP.Fila)
    }
}

/** Velocidad global de las animaciones (AppMotion de Mochy): se entrega al Recomposer de la ventana. */
object MovimientoApp {
    private var prefs: android.content.SharedPreferences? = null
    var activado by mutableStateOf(true); private set
    var velocidad by androidx.compose.runtime.mutableFloatStateOf(1f); private set
    private var escalaSistema by androidx.compose.runtime.mutableFloatStateOf(1f)

    /** La lee cada animación de Compose en cada fotograma. */
    val escala: androidx.compose.ui.MotionDurationScale = object : androidx.compose.ui.MotionDurationScale {
        override val scaleFactor: Float get() = if (!activado) 0f else escalaSistema / velocidad.coerceAtLeast(0.1f)
    }

    fun iniciar(ctx: android.content.Context) {
        val p = ctx.getSharedPreferences("aspecto", android.content.Context.MODE_PRIVATE).also { prefs = it }
        activado = p.getBoolean("animaciones", true)
        velocidad = p.getFloat("velocidad_anim", 1f)
        com.example.lector.ui.mochy.EstiloApp.iniciar(ctx)
        val res = ctx.contentResolver
        fun leer() = android.provider.Settings.Global.getFloat(res, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        escalaSistema = leer()
        res.registerContentObserver(
            android.provider.Settings.Global.getUriFor(android.provider.Settings.Global.ANIMATOR_DURATION_SCALE), false,
            object : android.database.ContentObserver(android.os.Handler(android.os.Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) { escalaSistema = leer(); MovimientoReducido.actualizar() }
            },
        )
    }

    fun elegirActivado(v: Boolean) { activado = v; prefs?.edit()?.putBoolean("animaciones", v)?.apply(); MovimientoReducido.actualizar() }
    fun elegirVelocidad(v: Float) { velocidad = v; prefs?.edit()?.putFloat("velocidad_anim", v)?.apply() }
}

// ───────────── Gestos (GestureSection / SwipeCard / ActionDropdown / ActionPicker de Mochy, GestureScreen.kt) ─────────────

private val RellenoTarjetaGesto = 20.dp

@Composable
private fun SeccionGestos() {
    GestosCapitulos.iniciar(androidx.compose.ui.platform.LocalContext.current)
    Column {
        LText(
            "Gestos",
            TextStyle(fontFamily = LectorFont, fontWeight = FontWeight.Bold, fontSize = 24.sp, letterSpacing = (-0.05).em),
        )
        Spacer(Modifier.height(14.dp))
        TarjetaDeslizar(
            titulo = "Deslizar a la derecha",
            subtitulo = "Lo que hace deslizar un capítulo hacia la derecha.",
            haciaDerecha = true,
            accion = GestosCapitulos.derecha,
            alElegir = { GestosCapitulos.elegirDerecha(it) },
        )
        Spacer(Modifier.height(14.dp))
        TarjetaDeslizar(
            titulo = "Deslizar a la izquierda",
            subtitulo = "Lo que hace deslizar un capítulo hacia la izquierda.",
            haciaDerecha = false,
            accion = GestosCapitulos.izquierda,
            alElegir = { GestosCapitulos.elegirIzquierda(it) },
        )
    }
}

/** SwipeCard de Mochy: título, la demo en vivo y el selector. */
@Composable
private fun TarjetaDeslizar(
    titulo: String,
    subtitulo: String,
    haciaDerecha: Boolean,
    accion: ChapterSwipeAction,
    alElegir: (ChapterSwipeAction) -> Unit,
) {
    var hoja by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().clip(FormaTarjeta).background(ColP.Tarjeta).padding(RellenoTarjetaGesto)) {
        LText(
            titulo,
            TextStyle(fontFamily = LectorFont, fontWeight = FontWeight.Bold, fontSize = 20.sp, letterSpacing = (-0.02).em),
        )
        LText(
            subtitulo,
            TextStyle(fontFamily = LectorFont, fontWeight = FontWeight.Medium, fontSize = 13.sp),
            Modifier.padding(top = 2.dp), color = LectorColors.Muted,
        )
        Spacer(Modifier.height(20.dp))
        // the demo runs edge to edge, like the mock: the row slides off the card's side
        SwipePreview(accion, haciaDerecha, Modifier.sangrarHorizontal(RellenoTarjetaGesto))
        Spacer(Modifier.height(20.dp))
        DesplegableAccion(accion) { hoja = true }
        if (accion != ChapterSwipeAction.NONE) {
            Spacer(Modifier.height(14.dp))
            SelectorColorGesto(accion)
        }
    }
    if (hoja) {
        HojaLector(onCerrar = { hoja = false }) {
            SelectorAccion(seleccionada = accion) { elegida ->
                alElegir(elegida)
                hoja = false
            }
        }
    }
}

private val ColoresGesto = listOf(
    Color(0xFF34C759), Color(0xFF0A84FF), Color(0xFF9B72F2), Color(0xFFFF9F0A),
    Color(0xFFFF375F), Color(0xFF5AC8FA), Color(0xFFFFD60A), Color(0xFF8E8E93),
)

/** Muestras de color para la acción: la primera vuelve al color de Mochy. */
@Composable
private fun SelectorColorGesto(accion: ChapterSwipeAction) {
    val actual = accion.colorActual
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        (listOf<Color?>(null) + ColoresGesto).forEach { c ->
            val muestra = c ?: accion.color
            val elegido = if (c == null) GestosCapitulos.colores[accion.name] == null else GestosCapitulos.colores[accion.name] == c.toArgb()
            Box(
                Modifier.size(30.dp).clip(CircleShape).background(muestra)
                    .then(if (elegido) Modifier.border(2.5.dp, LectorColors.Text, CircleShape) else Modifier)
                    .clickable { GestosCapitulos.elegirColor(accion, c) },
                Alignment.Center,
            ) { if (c == null) LText("A", TextStyle(fontFamily = LectorFont, fontWeight = FontWeight.Bold, fontSize = 12.sp), color = Color.White) }
        }
    }
}

/** ActionDropdown de Mochy. */
@Composable
private fun DesplegableAccion(accion: ChapterSwipeAction, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(ColP.Fila)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
    ) {
        InsigniaAccion(accion)
        Crossfade(targetState = accion, animationSpec = tween(if (Mov.reducido) 0 else 180), modifier = Modifier.weight(1f), label = "swipeAction") { mostrada ->
            LText(
                mostrada.title,
                TextStyle(fontFamily = LectorFont, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, letterSpacing = (-0.01).em, textAlign = TextAlign.Center),
                Modifier.fillMaxWidth(), maxLines = 1,
            )
        }
        Icon(Icons.Rounded.KeyboardArrowDown, null, tint = LectorColors.Muted, modifier = Modifier.size(20.dp))
    }
}

/** ActionPicker de Mochy (en la hoja de Shiori). */
@Composable
private fun SelectorAccion(seleccionada: ChapterSwipeAction, alElegir: (ChapterSwipeAction) -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
            .padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()),
    ) {
        ChapterSwipeAction.entries.forEach { opcion ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn56()
                    .clip(RoundedCornerShape(18.dp))
                    .background(ColP.Fila)
                    .clickable { alElegir(opcion) }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                InsigniaAccion(opcion)
                Spacer(Modifier.width(12.dp))
                LText(
                    opcion.title,
                    TextStyle(fontFamily = LectorFont, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, letterSpacing = (-0.01).em),
                    Modifier.weight(1f), maxLines = 1,
                )
                if (opcion == seleccionada) Icon(Icons.Rounded.Check, null, tint = LectorColors.Text, modifier = Modifier.size(18.dp))
            }
        }
    }
}

private fun Modifier.heightIn56() = this.then(Modifier.height(56.dp))

// colored circle badge so each gesture option shows its pill color in the selector and sheet
@Composable
private fun InsigniaAccion(accion: ChapterSwipeAction) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(accion.colorActual.copy(alpha = 0.22f)),
    ) {
        Icon(accion.icon, null, tint = accion.colorActual, modifier = Modifier.size(16.dp))
    }
}
