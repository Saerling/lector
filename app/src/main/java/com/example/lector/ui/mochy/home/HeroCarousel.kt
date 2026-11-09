// Copiado de Mochy Music (github.com/realidkroo/Mochy-Music, GPL-3.0): app/src/main/kotlin/com/mochyy/mochymusic/ui/component/HeroCarousel.kt
// (Shiori: las tarjetas de música -> cómics de la biblioteca (seguir leyendo / en curso / recientes / sin leer);
// AsyncImage -> ComicCover; Play/Pause -> Leer/Continuar; sin gacha ni vídeo «canvas», que son solo de música;
// LocalNavAnimatedContentScope/LocalAppContentObscured -> GestoAtras.hayEncima; el auto-avance respeta Mov.reducido)

package com.example.lector.ui.mochy.home

import android.graphics.Shader
import android.os.Build
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.lector.Almacen
import com.example.lector.Comic
import com.example.lector.Portadas
import com.example.lector.ui.ComicCover
import com.example.lector.ui.GestoAtras
import com.example.lector.ui.LectorColors
import com.example.lector.ui.Mov
import com.example.lector.ui.mochy.BlurEffectCache
import com.example.lector.ui.mochy.BlurQuality
import com.example.lector.ui.mochy.DownscaledBlur
import com.example.lector.ui.mochy.LocalBlurQuality
import com.example.lector.ui.mochy.MochyPreferences
import com.example.lector.ui.mochy.PageWipe
import com.example.lector.ui.mochy.damageBoundary
import com.example.lector.ui.mochy.pageMediaDrift
import com.example.lector.ui.mochy.pageTextDrift
import com.example.lector.ui.mochy.pressBounce
import com.example.lector.ui.origenTransicion
import com.example.lector.ui.textoPagina
import kotlinx.coroutines.delay

/** the hero takes this share of the screen height (527 of 874 in the Figma frame) */
const val HeroHeightFraction = 0.6f

// the first section starts inside the hero's faded-out bottom rather than under it, which puts
// its title 61dp below the Play pill like the Figma (it was 68)
private val HeroSectionTuck = 7.dp

private val HeroSlideEasing = CubicBezierEasing(0.32f, 0f, 0.15f, 1f)
private const val HeroSlideMillis = 1150
private const val HeroAdvanceMillis = 5500L
private val HeroPillContent = Color(0xFF2F2F2F)
private val HeroPillFill = Color.White.copy(alpha = 0.64f)
private val HeroLabelColor = Color.White.copy(alpha = 0.69f)

// once the hero starts scrolling away its video holds still and the carousel stops turning, so
// the scroll's blur only ever sees a still frame
private const val HeroStillFrom = 0.08f

/** the small line above a hero title: [text], then the [subject] it's about */
@Immutable
data class HeroLabel(
    val text: String,
    val subject: String? = null,
)

/** Shiori: una tarjeta del héroe es un cómic y el motivo por el que sale (lo que en Mochy es HeroCarouselItem). */
@Immutable
data class HeroCarouselItem(val comic: Comic, val reason: String) {
    fun label(): HeroLabel = HeroLabel(reason, comic.textoPagina())
    val mainText: String get() = comic.titulo
}

@Composable
fun HeroCarousel(
    items: List<HeroCarouselItem>,
    almacen: Almacen,
    onOpen: (Comic) -> Unit,
    onRead: (Comic) -> Unit,
    heroScrollProgressProvider: () -> Float = { 0f },
    modifier: Modifier = Modifier,
    onLongPress: ((Comic) -> Unit)? = null,
) {
    val heroHeight = (LocalConfiguration.current.screenHeightDp * HeroHeightFraction).dp

    // scrolling away it does exactly what a playlist cover does: rides up, zooms, goes soft, fades.
    // the list already carries it up, so it only adds a little on top
    ScrollAwayCover(
        progress = heroScrollProgressProvider,
        follow = 0.2f,
        softFrom = HeroStillFrom,
        modifier = modifier
            .heroTuck()
            .fillMaxWidth()
            .height(heroHeight),
    ) {
        if (items.isNotEmpty()) {
            HeroPager(
                items = items,
                almacen = almacen,
                onOpen = onOpen,
                onRead = onRead,
                heroScrollProgressProvider = heroScrollProgressProvider,
                onLongPress = onLongPress,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HeroPager(
    items: List<HeroCarouselItem>,
    almacen: Almacen,
    onOpen: (Comic) -> Unit,
    onRead: (Comic) -> Unit,
    heroScrollProgressProvider: () -> Float,
    onLongPress: ((Comic) -> Unit)? = null,
) {
    // with one card an infinite pager wraps onto itself and auto-advances against nothing,
    // so a single card is just a static hero
    val isSingleItem = items.size == 1
    val pagerState = rememberPagerState(
        initialPage = if (isSingleItem) 0 else (Int.MAX_VALUE / 2) - ((Int.MAX_VALUE / 2) % items.size),
        pageCount = { if (isSingleItem) 1 else Int.MAX_VALUE },
    )

    // Shiori: con una página encima (ficha, lector…) o con movimiento reducido no pasa solo
    val heroHeld = remember { { GestoAtras.hayEncima || Mov.reducido } }

    if (!isSingleItem) {
        val isDragged by pagerState.interactionSource.collectIsDraggedAsState()
        LaunchedEffect(pagerState, items.size, isDragged) {
            if (isDragged) return@LaunchedEffect
            // restarts after every manual swipe, so the next auto-advance never lands right on top of it
            while (true) {
                delay(HeroAdvanceMillis)
                // scrolled away, there's nothing to show the next card to; mid-transition or under
                // the player nobody's looking either
                if (heroScrollProgressProvider() > HeroStillFrom || heroHeld()) continue
                pagerState.animateScrollToPage(
                    pagerState.currentPage + 1,
                    animationSpec = tween(HeroSlideMillis, easing = HeroSlideEasing),
                )
            }
        }

        // warm the next cover so a swipe never fades in from an empty page
        LaunchedEffect(pagerState.currentPage, items) {
            val next = items[(pagerState.currentPage + 1).mod(items.size)].comic
            if (Portadas.enMemoria(next.id) == null) Portadas.cargar(almacen.portadaDe(next.id), next.id)
        }
    }

    val edgeBlur = !MochyPreferences.disableBlur && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    // a swipe bows the wipe's edge round, an auto-advance keeps it straight. it's set as soon as a
    // finger lands and held through the fling until the pager settles, eased so grabbing a card
    // mid-advance bends the edge instead of snapping it
    var userSwipe by remember { mutableStateOf(false) }
    val dragging by pagerState.interactionSource.collectIsDraggedAsState()
    LaunchedEffect(dragging) { if (dragging) userSwipe = true }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.isScrollInProgress }.collect { if (!it) userSwipe = false }
    }
    val wipeCurve by animateFloatAsState(if (userSwipe) 1f else 0f, tween(260), label = "heroWipeCurve")

    // the hero animates nearly all the time; bounded, that only ever repaints the hero instead of
    // the whole screen and every glass surface sampling it
    Box(modifier = Modifier.fillMaxSize().damageBoundary()) {
        HorizontalPager(
            state = pagerState,
            userScrollEnabled = !isSingleItem,
            // Shiori: la clave mantiene la página al cambiar la biblioteca
            key = { page -> if (isSingleItem) items[0].comic.id else page },
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val item = items[page.mod(items.size)]
            HeroPage(
                item = item,
                almacen = almacen,
                pageOffset = { pagerState.offsetOf(page) },
                wipeCurve = { wipeCurve },
                edgeBlur = edgeBlur,
                onOpen = { onOpen(item.comic) },
                onPrimaryClick = { onRead(item.comic) },
                onLongPress = onLongPress?.let { f -> { f(item.comic) } },
            )
        }
    }
}

/** signed distance of [page] from the settled position: 0 on screen, +1 one page to the right */
private fun PagerState.offsetOf(page: Int): Float =
    (page - currentPage) - currentPageOffsetFraction

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HeroPage(
    item: HeroCarouselItem,
    almacen: Almacen,
    pageOffset: () -> Float,
    wipeCurve: () -> Float,
    edgeBlur: Boolean,
    onOpen: () -> Unit,
    onPrimaryClick: () -> Unit,
    onLongPress: (() -> Unit)? = null,
) {
    PageWipe(pageOffset = pageOffset, curve = wipeCurve, blur = edgeBlur) {
        HeroMedia(
            item = item,
            almacen = almacen,
            // Shiori: tocar la portada abre la ficha (ContainerTransform desde el héroe)
            modifier = Modifier
                .pageMediaDrift(pageOffset)
                .origenTransicion()
                .combinedClickable(interactionSource = null, indication = null, onClick = onOpen, onLongClick = onLongPress),
            edgeBlur = edgeBlur,
        )

        // per page and under the text: drawn once over the whole pager it would dim the titles too
        HeroScrims()

        Box(
            modifier = Modifier
                .matchParentSize()
                .pageTextDrift(pageOffset)
        ) {
            HeroText(
                label = item.label(),
                title = item.mainText,
                pill = if (item.comic.leido > 0) HeroPillKind.Continue else HeroPillKind.Read,
                onPillClick = onPrimaryClick,
                onViewClick = onOpen,
                modifier = Modifier.align(Alignment.BottomStart)
            )
        }
    }
}

@Composable
private fun HeroMedia(
    item: HeroCarouselItem,
    almacen: Almacen,
    modifier: Modifier = Modifier,
    edgeBlur: Boolean,
) {
    val mediaLayer = rememberGraphicsLayer()

    Box(modifier = modifier.fillMaxSize()) {
        // the page's media is recorded once and drawn again, blurred, into the edge bands below.
        // they're siblings of this box, never inside it, so the layer can't end up drawing itself.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (edgeBlur) {
                        Modifier.drawWithContent {
                            mediaLayer.record { this@drawWithContent.drawContent() }
                            drawLayer(mediaLayer)
                        }
                    } else Modifier
                )
        ) {
            ComicCover(item.comic, almacen, Modifier.fillMaxSize(), RoundedCornerShape(0.dp))
        }

        if (edgeBlur) {
            HeroBlurBand(mediaLayer, atTop = true, heightFraction = 0.46f, radius = 22f)
            HeroBlurBand(mediaLayer, atTop = false, heightFraction = 0.5f, radius = 30f)
        }
    }
}

/**
 * progressive blur: the page's media drawn again, blurred, and faded out toward the middle of the
 * hero, so it's strongest right behind the top bar and behind the title and button.
 */
@Composable
private fun BoxScope.HeroBlurBand(
    mediaLayer: GraphicsLayer,
    atTop: Boolean,
    heightFraction: Float,
    radius: Float,
) {
    val mask = remember(atTop) {
        if (atTop) {
            Brush.verticalGradient(0f to Color.Black, 0.35f to Color.Black.copy(alpha = 0.85f), 1f to Color.Transparent)
        } else {
            Brush.verticalGradient(0f to Color.Transparent, 0.55f to Color.Black.copy(alpha = 0.85f), 1f to Color.Black)
        }
    }
    // One layer per band, at the resolution DownscaledBlur picks for the radius: the mask runs in
    // the blur's own effect instead of an offscreen layer around it, and the media is blurred from
    // a half-size copy.
    // high quality on android 13+ grows the blur toward the edge in one pass; a faded fixed blur bloomed around bright parts
    val smooth = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        LocalBlurQuality.current == BlurQuality.HIGH
    DownscaledBlur(
        radius = { BlurEffectCache.effectiveRadius(radius) },
        tileMode = Shader.TileMode.CLAMP,
        mask = mask,
        growDown = if (smooth) !atTop else null,
        modifier = Modifier
            .align(if (atTop) Alignment.TopCenter else Alignment.BottomCenter)
            .fillMaxWidth()
            .fillMaxHeight(heightFraction),
    ) { fullSize ->
        val dy = if (atTop) 0f else fullSize.height - mediaLayer.size.height.toFloat()
        translate(top = dy) { drawLayer(mediaLayer) }
    }
}

/**
 * the Figma dim (the cover sits at 64% on black) and the top and bottom gradients, as one gradient.
 * black at a over black at b is black at 1 - (1 - a)(1 - b), and that's linear in each, so folding
 * the dim into the gradients' stops gives the same picture: one pass over the hero instead of three
 */
@Composable
private fun BoxScope.HeroScrims() {
    // Shiori: con el tema claro el fondo no es negro; el último tramo funde el negro con el fondo de la app
    val bg = LectorColors.Bg
    Box(
        modifier = Modifier
            .matchParentSize()
            .drawWithCache {
                val topEnd = 0.463f
                val bottomStart = 0.533f
                val bottomEnd = 0.989f
                val scrim = Brush.verticalGradient(
                    0f to heroScrimBlack(0.86f),
                    0.475f * topEnd to heroScrimBlack(0.59f),
                    topEnd to heroScrimBlack(0f),
                    bottomStart to heroScrimBlack(0f),
                    bottomStart + 0.457f * (bottomEnd - bottomStart) to heroScrimBlack(0.59f),
                    bottomEnd to heroScrimBlack(1f),
                    1f to heroScrimBlack(1f),
                    startY = 0f,
                    endY = size.height,
                )
                val toBg = Brush.verticalGradient(0.93f to Color.Transparent, 1f to bg, startY = 0f, endY = size.height)
                val needsBg = bg.red + bg.green + bg.blue > 0.15f
                onDrawBehind {
                    drawRect(scrim)
                    if (needsBg) drawRect(toBg)
                }
            }
    )
}

private const val HeroDim = 0.36f

/** black at [gradient], over the 36% dim */
private fun heroScrimBlack(gradient: Float): Color = Color.Black.copy(alpha = 1f - (1f - HeroDim) * (1f - gradient))

/** label, title and pill, sitting on the hero's bottom left like the Figma */
@Composable
private fun HeroText(
    label: HeroLabel,
    title: String,
    pill: HeroPillKind,
    onPillClick: () -> Unit,
    onViewClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(start = HomeContentInset, end = HomeContentInset, bottom = 64.dp)
    ) {
        HeroLabelLine(label)
        AnimatedContent(
            targetState = title,
            transitionSpec = { heroLabelSwap() },
            label = "heroTitle",
        ) { text ->
            Text(
                text = text,
                style = HomeTitleStyle,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(11.dp))
        Row {
            HeroPill(kind = pill, onClick = onPillClick)
            Spacer(Modifier.width(8.dp))
            // Shiori: además de leer, la ficha (en Mochy «View» es la píldora de álbumes y listas)
            HeroPill(kind = HeroPillKind.View, onClick = onViewClick)
        }
    }
}

/** "Seguir leyendo  Cap. 12 de 40": the reason, then what it's about */
@Composable
private fun HeroLabelLine(label: HeroLabel) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label.text,
            style = HomeLabelStyle,
            color = HeroLabelColor,
            maxLines = 1,
        )
        if (label.subject != null) {
            Spacer(Modifier.width(6.dp))
            Text(
                text = label.subject,
                style = HomeLabelStyle,
                color = Color.White.copy(alpha = 0.88f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
        }
    }
}

private enum class HeroPillKind { Read, Continue, View }

/**
 * the 33dp light pill from the Figma: dark glyph and label on white at 64%. it squishes under the
 * finger, and when it changes the new content pops in while the pill eases to its new width.
 */
@Composable
private fun HeroPill(
    kind: HeroPillKind,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .pressBounce(interaction)
            .height(33.dp)
            .clip(RoundedCornerShape(50))
            .background(HeroPillFill)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick)
    ) {
        AnimatedContent(
            targetState = kind,
            transitionSpec = { heroGlyphSwap() },
            label = "heroPill",
        ) { current ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .height(33.dp)
                    .padding(
                        start = when (current) {
                            HeroPillKind.Read, HeroPillKind.Continue -> 21.dp
                            HeroPillKind.View -> 17.dp
                        },
                        end = 17.dp,
                    )
            ) {
                when (current) {
                    HeroPillKind.Read, HeroPillKind.Continue -> {
                        Icon(
                            imageVector = Icons.Rounded.PlayArrow,
                            contentDescription = null,
                            tint = HeroPillContent,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(Modifier.width(9.dp))
                    }
                    HeroPillKind.View -> Unit
                }
                Text(
                    text = when (current) {
                        HeroPillKind.Read -> "Leer"
                        HeroPillKind.Continue -> "Continuar"
                        HeroPillKind.View -> "Ver"
                    },
                    style = HomeLabelStyle,
                    color = HeroPillContent,
                )
                if (current == HeroPillKind.View) {
                    Spacer(Modifier.width(5.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                        contentDescription = null,
                        tint = HeroPillContent,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

private fun <S> AnimatedContentTransitionScope<S>.heroGlyphSwap(): ContentTransform =
    (scaleIn(initialScale = 0.6f, animationSpec = spring(dampingRatio = 0.55f, stiffness = 650f)) + fadeIn(tween(130)))
        .togetherWith(scaleOut(targetScale = 0.6f, animationSpec = tween(110)) + fadeOut(tween(90)))
        .using(SizeTransform(clip = false) { _, _ -> spring(dampingRatio = 0.75f, stiffness = 520f) })

private fun <S> AnimatedContentTransitionScope<S>.heroLabelSwap(): ContentTransform =
    (slideInVertically(spring(dampingRatio = 0.8f, stiffness = 420f)) { it / 3 } + fadeIn(tween(220)))
        .togetherWith(slideOutVertically(tween(160)) { -it / 3 } + fadeOut(tween(140)))
        .using(SizeTransform(clip = false))

/** drawn full height, but the list only makes room for all but the tuck */
private fun Modifier.heroTuck(): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    val tuck = HeroSectionTuck.roundToPx()
    layout(placeable.width, (placeable.height - tuck).coerceAtLeast(0)) {
        placeable.place(0, 0)
    }
}
