package com.example.lector.ui

import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.input.pointer.pointerInput
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
fun ComicCover(comic: Comic, almacen: Almacen, modifier: Modifier = Modifier, shape: Shape = FormaPortada) {
    val portada = rememberPortada(almacen, comic.id)
    if (portada != null) androidx.compose.runtime.SideEffect { com.example.lector.ui.mochy.EstiloApp.anotarPortada("c:" + comic.id, portada.width, portada.height) }
    Box(modifier.clip(shape).background(LectorColors.CardHigh), contentAlignment = Alignment.Center) {
        when {
            portada != null ->
                Image(bitmap = portada, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            // Manga en línea sin portada guardada (añadido con pulsación larga, copia restaurada…): la de la fuente.
            comic.enLinea && !comic.portadaUrl.isNullOrBlank() ->
                ImagenRemota(comic.portadaUrl, Modifier.fillMaxSize(), rememberCabeceras(comic.fuenteId), fuenteId = comic.fuenteId)
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
    // Botón tonal Expressive: píldora que cuadra sus esquinas al pulsar.
    BotonExpresivo(text, icon, onClick, modifier, EstiloBoton.TONAL, alto = 46.dp)
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier) {
    Row(modifier.padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
        LText(title, LectorType.Section)
        Icon(Icons.Rounded.KeyboardArrowRight, null, tint = LectorColors.Muted, modifier = Modifier.size(18.dp))
    }
}

/**
 * Fila de portadas de Inicio: la HomeCardRow de Mochy Music (ui/mochy/home/HomeCards.kt) con sus HomeCoverCard,
 * que entran en cascada la primera vez y se iluminan con el recuadro redondeado de Mochy al pulsar.
 */
@Composable
fun CoverCarousel(items: List<Comic>, almacen: Almacen, onClick: (Comic) -> Unit, modifier: Modifier = Modifier) {
    val visto = com.example.lector.ui.mochy.rememberRevealSeenState()
    com.example.lector.ui.mochy.home.HomeCardRow(
        rowKey = "portadas", items = items, revealSeen = visto, key = { it.id }, modifier = modifier,
    ) { c ->
        com.example.lector.ui.mochy.home.HomeCoverCard(
            c, almacen,
            Modifier.clickable(
                interactionSource = null,
                indication = com.example.lector.ui.mochy.home.HomeCardHighlight,
                onClick = { onClick(c) },
            ),
        )
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
/** Sombra suave bajo el cromo flotante (no se nota como halo sobre negro). */
private fun Modifier.sombraCromo(forma: Shape): Modifier =
    shadow(10.dp, forma, clip = false, ambientColor = Color.Black.copy(alpha = 0.25f), spotColor = Color.Black.copy(alpha = 0.25f))

/**
 * Barra flotante de cristal: la AppNavigationBar de Mochy Music (ui/mochy/AppNavigation.kt), solo iconos, con las
 * pestañas de Shiori. Inicio y Explorar llevan el icono a 22 dp y el resto a 24, como Home y Search en Mochy.
 */
@Composable
fun FloatingNavBar(selected: LectorTab, onSelect: (LectorTab) -> Unit, modifier: Modifier = Modifier) {
    val tabs = LectorTab.values()
    val items = remember {
        tabs.map { t ->
            com.example.lector.ui.mochy.NavItem(
                t.label, t.icon, t.iconoApagado,
                if (t == LectorTab.Home || t == LectorTab.Search) 22.dp else 24.dp,
            )
        }
    }
    com.example.lector.ui.mochy.AppNavigationBar(
        navigationItems = items,
        selectedIndex = selected.ordinal,
        onItemClick = { i, _ -> onSelect(tabs[i]) },
        modifier = modifier,
        clipped = Modifier.luzTactil(),
    )
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
    ) { m -> ComicCover(comic, almacen, m, com.example.lector.ui.mochy.SmoothCornerShape(12.dp)) }

/**
 * Mini lector: el mini reproductor de Mochy Music (player/MorphingCover.kt + BottomSheet.kt) sin el morph:
 * píldora de cristal de 64 dp con esquinas suaves de 20 dp que se inclina bajo el dedo, portada a la izquierda,
 * título y capítulo, y el botón de Mochy (rebote + resalte redondo) para seguir leyendo. El progreso es una
 * línea finísima en el borde de abajo.
 */
@Composable
fun MiniReaderGenerico(
    titulo: String, subtitulo: String, onClick: () -> Unit, modifier: Modifier = Modifier,
    progreso: Float? = null,
    portada: @Composable (Modifier) -> Unit,
) {
    val forma = com.example.lector.ui.mochy.SmoothCornerShape(MiniEsquina)
    Box(
        modifier.fillMaxWidth().height(AltoCromo)
            .press3D(4f, 0.985f)
            .sombraCromo(forma)
            .clip(forma)
            .clickable(interactionSource = null, indication = null, onClick = onClick)
            .drawWithContent { drawContent(); lineaCristal(MiniEsquina.toPx()) },
    ) {
        FondoCristal(CristalApp, Modifier.fillMaxSize())
        Row(Modifier.fillMaxSize().padding(start = 8.dp, end = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            portada(Modifier.size(48.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                LText(titulo, LectorType.Body.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                LText(subtitulo, LectorType.Label, color = LectorColors.Muted, maxLines = 1)
            }
            Spacer(Modifier.width(6.dp))
            Box(Modifier.size(36.dp)) { BotonMini(onClick) }
        }
        if (progreso != null) {
            Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(2.dp).background(LectorColors.Text.copy(alpha = 0.10f))) {
                Box(Modifier.fillMaxWidth(progreso.coerceIn(0f, 1f)).fillMaxHeight().background(LectorColors.Text.copy(alpha = 0.75f)))
            }
        }
    }
}

/** Esquinas del mini lector (las de la píldora del mini reproductor de Mochy). */
private val MiniEsquina = 20.dp

/**
 * The round highlight behind a pressed mini player button: the nav bar's highlight colour, 4dp
 * past the 36dp button. (Copiado de Mochy Music, GPL-3.0: ui/player/MorphingCover.kt, MiniButtonHighlight)
 */
private val MiniButtonHighlight = com.example.lector.ui.mochy.RoundedHighlight(
    cornerRadius = 22.dp,
    spillX = 4.dp,
    spillY = 4.dp,
    color = com.example.lector.ui.mochy.ChromeHighlightColor,
)

/**
 * One of the mini player's buttons. A round highlight comes up under the finger and the button
 * squishes with it. (Copiado de Mochy Music, GPL-3.0: ui/player/MorphingCover.kt, MiniPlayerButton)
 */
@Composable
private fun BotonMini(onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .rebote(interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = MiniButtonHighlight,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Rounded.PlayArrow, "Continuar", tint = LectorColors.Text, modifier = Modifier.size(20.dp))
    }
}

/**
 * Luz que sigue al dedo sobre una superficie de cristal (como la barra de progreso de Fluid Cloud en ColorOS 17):
 * nace al tocar, se desliza con el dedo y se apaga despacio. Solo mira el dedo, no consume el toque.
 */
@Composable
fun Modifier.luzTactil(): Modifier {
    val luz = remember { Animatable(0f) }
    var punto by remember { mutableStateOf(Offset.Zero) }
    val scope = rememberCoroutineScope()
    return this
        .pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val ev = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                    val c = ev.changes.firstOrNull() ?: continue
                    punto = c.position
                    if (c.pressed && !c.previousPressed) scope.launch { luz.animateTo(1f, tween(140, easing = EnfasisEasing)) }
                    if (!c.pressed && c.previousPressed) scope.launch { luz.animateTo(0f, tween(600, easing = EnfasisEasing)) }
                }
            }
        }
        .drawWithContent {
            drawContent()
            val l = if (Aspecto.luzTactil) luz.value else 0f
            if (l > 0f) drawCircle(
                Brush.radialGradient(
                    0f to Color.White.copy(alpha = 0.22f * l), 0.5f to Color.White.copy(alpha = 0.06f * l), 1f to Color.Transparent,
                    center = punto, radius = size.height * 1.4f,
                ),
                radius = size.height * 1.4f, center = punto,
            )
        }
}
