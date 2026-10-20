package com.example.lector.ui

import android.graphics.BitmapFactory
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.lector.Almacen
import com.example.lector.Comic
import com.example.lector.rememberPortada
import com.example.lector.ext.ExtManager
import com.example.lector.ext.Motor
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.List
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Search
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

@Composable
fun LText(
    text: String, style: TextStyle, modifier: Modifier = Modifier,
    color: Color = LectorColors.Text, maxLines: Int = Int.MAX_VALUE
) = Text(text, modifier, color, style = style, maxLines = maxLines, overflow = TextOverflow.Ellipsis)

/** Portada real (la que ya guarda tu Almacen); si no hay, muestra la inicial del título. */
@Composable
fun ComicCover(comic: Comic, almacen: Almacen, modifier: Modifier = Modifier, shape: Shape = SuaveShape(20.dp)) {
    val portada = rememberPortada(almacen, comic.id)
    Box(modifier.clip(shape).background(LectorColors.CardHigh), contentAlignment = Alignment.Center) {
        when {
            portada != null ->
                Image(bitmap = portada, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            // Manga en línea sin portada guardada (añadido con pulsación larga, copia restaurada…): la de la fuente.
            comic.enLinea && !comic.portadaUrl.isNullOrBlank() ->
                ImagenRemota(comic.portadaUrl, Modifier.fillMaxSize(), rememberCabeceras(comic.fuenteId))
            else -> LText(comic.titulo.take(1).uppercase(), LectorType.Display, color = LectorColors.Muted)
        }
    }
}

/** Cabeceras HTTP de una fuente (Referer…) para cargar sus imágenes; se piden en segundo plano. */
@Composable
fun rememberCabeceras(fuenteId: Long): Map<String, String> {
    val ctx = LocalContext.current
    val ext = remember { ExtManager.obtener(ctx) }
    val f = ext.fuentes.firstOrNull { it.id == fuenteId }
    val inicial = remember(fuenteId, f) { f?.let { Motor.actual?.cabeceras(it) }.orEmpty() }
    val cab by produceState(inicial, fuenteId, f) {
        if (f != null) value = Motor.actual?.cabecerasCargando(f).orEmpty()
    }
    return cab
}

@Composable
fun ProgressLine(progress: Float, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(3.dp).clip(CircleShape).background(LectorColors.PillFill)) {
        Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).fillMaxHeight().background(LectorColors.Text))
    }
}

@Composable
fun PillButton(text: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        // Botón tonal compacto de musicfy (~46 dp de alto, gris #2C2C2E).
        modifier.pressable(onClick).clip(CircleShape).background(LectorColors.PillFill)
            .heightIn(min = 46.dp).padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = LectorColors.Text, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        LText(text, LectorType.Body)
    }
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier) {
    Row(modifier.padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
        LText(title, LectorType.Section)
        Icon(Icons.Rounded.KeyboardArrowRight, null, tint = LectorColors.Muted, modifier = Modifier.size(18.dp))
    }
}

/** Carrusel con "snap"; la última portada queda cortada para sugerir scroll. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CoverCarousel(items: List<Comic>, almacen: Almacen, onClick: (Comic) -> Unit, modifier: Modifier = Modifier) {
    val state = rememberLazyListState()
    LazyRow(
        modifier, state = state,
        contentPadding = PaddingValues(horizontal = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        flingBehavior = rememberSnapFlingBehavior(state)
    ) {
        itemsIndexed(items, key = { _, c -> c.id }) { i, c ->
            Column(Modifier.width(140.dp).entrada(i).press3D().pressable { onClick(c) }) {
                ComicCover(c, almacen, Modifier.fillMaxWidth().aspectRatio(2f / 3f))
                Spacer(Modifier.height(8.dp))
                LText(c.titulo, LectorType.Body, maxLines = 1)
                LText(c.textoPagina(), LectorType.Label, color = LectorColors.Muted, maxLines = 1)
            }
        }
    }
}

enum class LectorTab(val label: String, val icon: ImageVector, val iconoApagado: ImageVector) {
    Home("Inicio", Icons.Rounded.Home, Icons.Outlined.Home),
    Search("Explorar", Icons.Rounded.Search, Icons.Outlined.Search),
    Library("Biblioteca", Icons.Rounded.List, Icons.Outlined.List),
    Updates("Actualizaciones", Icons.Rounded.Notifications, Icons.Outlined.Notifications),
    History("Historial", Icons.Rounded.DateRange, Icons.Outlined.DateRange),
    More("Más", Icons.Rounded.MoreVert, Icons.Outlined.MoreVert)
}

/** Alto de la barra y del mini lector: un «estadio» de 64 dp, como en musicfy. */
private val AltoCromo = 64.dp
/** La píldora iluminada queda a 8 dp de arriba, abajo y de los extremos de la barra. */
private val MargenLuz = 8.dp
private val HuecoLuz = 4.dp
/** El borde delantero de la píldora llega antes que el trasero: se estira hacia la pestaña y luego se asienta. */
private val LuzDelante = spring<Float>(dampingRatio = 0.82f, stiffness = 700f)
private val LuzDetras = spring<Float>(dampingRatio = 0.82f, stiffness = 320f)

/** Sombra suave bajo el cromo flotante (no se nota como halo sobre negro). */
private fun Modifier.sombraCromo(forma: Shape): Modifier =
    shadow(10.dp, forma, clip = false, ambientColor = Color.Black.copy(alpha = 0.25f), spotColor = Color.Black.copy(alpha = 0.25f))

/**
 * Barra flotante de cristal (musicfy): estadio de 64 dp con lo de debajo desenfocado, una píldora
 * blanca al 10 % detrás de la pestaña activa que se estira al cambiar de pestaña, y cada icono pasa
 * de contorno a relleno al encenderse. Al pulsar, la pestaña se aplasta y rebota.
 */
@Composable
fun FloatingNavBar(selected: LectorTab, onSelect: (LectorTab) -> Unit, modifier: Modifier = Modifier) {
    val forma = CircleShape
    val tabs = LectorTab.values()
    val indice = selected.ordinal
    val inicio = remember { Animatable(indice.toFloat()) }
    val fin = remember { Animatable(indice.toFloat()) }
    LaunchedEffect(indice) {
        val destino = indice.toFloat()
        val aDerecha = destino > (inicio.value + fin.value) / 2f
        launch { inicio.animateTo(destino, if (aDerecha) LuzDetras else LuzDelante) }
        fin.animateTo(destino, if (aDerecha) LuzDelante else LuzDetras)
    }
    val luz = LectorColors.Iluminada
    Box(
        modifier.fillMaxWidth().height(AltoCromo)
            .press3D(4f, 0.985f)
            .sombraCromo(forma)
            .clip(forma)
            .drawWithContent { drawContent(); lineaCristal(size.height / 2f) },
    ) {
        FondoCristal(CristalApp, Modifier.fillMaxSize())
        Row(
            Modifier.fillMaxSize()
                .padding(horizontal = MargenLuz - HuecoLuz / 2)
                .drawBehind {
                    val ranura = size.width / tabs.size
                    val medio = HuecoLuz.toPx() / 2f
                    val margen = MargenLuz.toPx()
                    val izq = inicio.value * ranura + medio
                    val der = (fin.value + 1f) * ranura - medio
                    val alto = size.height - margen * 2f
                    drawRoundRect(luz, Offset(izq, margen), Size(der - izq, alto), CornerRadius(alto / 2f))
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tabs.forEach { tab -> PestanaNav(tab, tab == selected) { onSelect(tab) } }
        }
    }
}

@Composable
private fun RowScope.PestanaNav(tab: LectorTab, encendida: Boolean, onClick: () -> Unit) {
    val fuente = remember { MutableInteractionSource() }
    val luz by animateFloatAsState(if (encendida) 1f else 0f, tween(200), label = "pestana")
    Box(
        Modifier.weight(1f).fillMaxHeight()
            .rebote(fuente, 0.85f)
            .clickable(interactionSource = fuente, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            tab.iconoApagado, tab.label, tint = LectorColors.Text,
            modifier = Modifier.size(24.dp).graphicsLayer { alpha = 0.7f * (1f - luz) },
        )
        Icon(
            tab.icon, null, tint = LectorColors.Text,
            modifier = Modifier.size(24.dp).graphicsLayer { alpha = luz },
        )
    }
}

/**
 * Se aplasta bajo el dedo y vuelve con un pequeño rebote. Un toque rápido dentro de una lista avisa de
 * pulsar y soltar a la vez: entonces al soltar primero se ve el aplastado.
 */
@Composable
fun Modifier.rebote(fuente: MutableInteractionSource, escala: Float = 0.88f): Modifier {
    val e = remember { Animatable(1f) }
    LaunchedEffect(fuente, escala) {
        var pulsado = 0
        fuente.interactions.collect { i ->
            when (i) {
                is PressInteraction.Press -> {
                    pulsado++
                    launch { e.animateTo(escala, spring(dampingRatio = 1f, stiffness = 1600f)) }
                }
                is PressInteraction.Release, is PressInteraction.Cancel -> {
                    pulsado = (pulsado - 1).coerceAtLeast(0)
                    if (pulsado == 0) launch {
                        if (e.value > escala + 0.03f) e.animateTo(escala, tween(70))
                        e.animateTo(1f, spring(dampingRatio = 0.38f, stiffness = 520f))
                    }
                }
            }
        }
    }
    return graphicsLayer { scaleX = e.value; scaleY = e.value }
}

/** "Continuar leyendo": el último cómic que abriste. */
@Composable
fun MiniReader(comic: Comic, almacen: Almacen, onClick: () -> Unit, modifier: Modifier = Modifier) =
    MiniReaderGenerico(
        comic.titulo, comic.textoPagina(), onClick, modifier,
        progreso = if (comic.paginas > 0) ((comic.ultimaPagina + if (comic.enLinea) 0 else 1).toFloat() / comic.paginas).coerceIn(0f, 1f) else null,
    ) { m -> ComicCover(comic, almacen, m, CircleShape) }

/**
 * Mini lector de cristal (el mini reproductor de musicfy): mismo estadio de 64 dp que la barra, portada redonda
 * con un anillo de progreso, título y capítulo, y un botón redondo para seguir leyendo.
 */
@Composable
fun MiniReaderGenerico(
    titulo: String, subtitulo: String, onClick: () -> Unit, modifier: Modifier = Modifier,
    progreso: Float? = null,
    portada: @Composable (Modifier) -> Unit,
) {
    val forma = CircleShape
    val anillo = LectorColors.Text
    val pista = LectorColors.Text.copy(alpha = 0.15f)
    Box(
        modifier.fillMaxWidth().height(AltoCromo)
            .press3D(4f, 0.985f)
            .sombraCromo(forma)
            .clip(forma)
            .pressable(onClick)
            .drawWithContent { drawContent(); lineaCristal(size.height / 2f) },
    ) {
        FondoCristal(CristalApp, Modifier.fillMaxSize())
        Row(Modifier.fillMaxSize().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(48.dp).drawWithContent {
                    drawContent()
                    if (progreso != null) {
                        val g = 2.5.dp.toPx()
                        drawArc(pista, 0f, 360f, false, topLeft = Offset(g / 2, g / 2), size = Size(size.width - g, size.height - g), style = Stroke(g))
                        drawArc(anillo, -90f, 360f * progreso, false, topLeft = Offset(g / 2, g / 2), size = Size(size.width - g, size.height - g), style = Stroke(g, cap = StrokeCap.Round))
                    }
                },
                Alignment.Center,
            ) { portada(Modifier.size(if (progreso != null) 40.dp else 48.dp)) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                LText(titulo, LectorType.Body.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                LText(subtitulo, LectorType.Label, color = LectorColors.Muted, maxLines = 1)
            }
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier.size(44.dp).clip(CircleShape).border(1.dp, LectorColors.Text.copy(alpha = 0.15f), CircleShape),
                Alignment.Center,
            ) { Icon(Icons.Rounded.PlayArrow, "Continuar", tint = LectorColors.Text, modifier = Modifier.size(24.dp)) }
            Spacer(Modifier.width(2.dp))
        }
    }
}
