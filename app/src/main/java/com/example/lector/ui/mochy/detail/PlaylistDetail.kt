// Copiado de Mochy Music (github.com/realidkroo/Mochy-Music, GPL-3.0): app/src/main/kotlin/com/mochyy/mochymusic/ui/component/detail/PlaylistDetail.kt
//
// Shiori: la página de álbum/playlist de Mochy (estilo FLOW) hecha ficha de manga. La portada a sangre detrás de la
// lista, el título que se esconde bajo la barra y la portada pequeña que sale del botón atrás son los de Mochy; las
// filas de canción son filas de capítulo. Coil → slots @Composable (el que llama pone ImagenRemota/ComicCover).

package com.example.lector.ui.mochy.detail

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.lector.ui.LectorColors
import com.example.lector.ui.Mov
import com.example.lector.ui.TemaActual
import com.example.lector.ui.mochy.Bone
import com.example.lector.ui.mochy.BoneColor
import com.example.lector.ui.mochy.BlurDirection
import com.example.lector.ui.mochy.BonesHost
import com.example.lector.ui.mochy.GlassPillBackground
import com.example.lector.ui.mochy.GlassState
import com.example.lector.ui.mochy.InterFontFamily
import com.example.lector.ui.mochy.ProgressiveGlassBackground
import com.example.lector.ui.mochy.RoundedHighlight
import com.example.lector.ui.mochy.pressBounce
import com.example.lector.ui.mochy.stableStatusBars
import kotlinx.coroutines.launch

// geometry and type from the Figma playlist frames (16:1162 open, 16:1354 scrolled). internal so
// the album, artist and generated pages line up on the same inset and type
internal val PlaylistInset = 36.dp
internal val PlaylistTracking = (-0.06).em
internal val PillContent = Color(0xFF2F2F2F)
// Shiori: en el tema claro el blanco al 64 % no se ve sobre el fondo claro; ahí es un gris suave.
internal val PillFill: Color get() = if (TemaActual.oscuro) Color.White.copy(alpha = 0.64f) else Color.Black.copy(alpha = 0.07f)
internal val TrackShape = RoundedCornerShape(9.dp)

internal val PlaylistTitleStyle = TextStyle(
    fontFamily = InterFontFamily,
    fontWeight = FontWeight.Bold,
    fontSize = 24.sp,
    lineHeight = 28.sp,
    letterSpacing = PlaylistTracking,
)
internal val PlaylistCreatorStyle = TextStyle(
    fontFamily = InterFontFamily,
    fontWeight = FontWeight.Bold,
    fontSize = 15.sp,
    lineHeight = 18.sp,
    letterSpacing = (-0.04).em,
)
val PlaylistStatsStyle = TextStyle(
    fontFamily = InterFontFamily,
    fontWeight = FontWeight.SemiBold,
    fontSize = 11.sp,
    lineHeight = 14.sp,
    letterSpacing = (-0.02).em,
)
internal val PillLabelStyle = TextStyle(
    fontFamily = InterFontFamily,
    fontWeight = FontWeight.Bold,
    fontSize = 16.sp,
    letterSpacing = PlaylistTracking,
)
val TrackTitleStyle = TextStyle(
    fontFamily = InterFontFamily,
    fontWeight = FontWeight.SemiBold,
    fontSize = 14.sp,
    lineHeight = 17.sp,
    letterSpacing = (-0.03).em,
)
val TrackSubtitleStyle = TextStyle(
    fontFamily = InterFontFamily,
    fontWeight = FontWeight.Medium,
    fontSize = 11.5.sp,
    lineHeight = 14.sp,
    letterSpacing = (-0.02).em,
)
val SectionTitleStyle = TextStyle(
    fontFamily = InterFontFamily,
    fontWeight = FontWeight.Bold,
    fontSize = 16.sp,
    letterSpacing = PlaylistTracking,
)
internal val ReceiptSmallStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.Medium,
    fontSize = 8.5.sp,
    letterSpacing = 0.04.em,
)
internal val ReceiptCenterStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.Medium,
    fontSize = 10.sp,
    letterSpacing = 0.42.em,
)

/** who made a playlist. (Shiori: autor del manga, o la fuente si no hay autor) */
data class CreatorUi(
    val name: String,
)

/** the cover band at the top of a playlist, shared by the header item and the backdrop behind it */
@Composable
fun playlistCoverHeight(): Dp = (LocalConfiguration.current.screenHeightDp * 0.6f).dp

/**
 * Lo que en Mochy calcula PlaylistDetailScaffold: cuánto se ha subido la banda de la portada (0..1) y el muelle
 * que pliega la barra de arriba en cuanto el título pasa por debajo. Se leen al dibujar, sin recomponer.
 */
class PlaylistCollapse(val scrollProgress: State<Float>, val morph: Animatable<Float, *>)

@Composable
fun rememberPlaylistCollapse(lazyListState: LazyListState, headerHeight: Dp): PlaylistCollapse {
    val collapseHeightPx = with(LocalDensity.current) { headerHeight.toPx() }
    val scrollProgress = remember(lazyListState, collapseHeightPx) {
        derivedStateOf {
            if (lazyListState.firstVisibleItemIndex > 0) {
                1f
            } else {
                (lazyListState.firstVisibleItemScrollOffset / collapseHeightPx).coerceIn(0f, 1f)
            }
        }
    }

    // flips once the title has slid under the top bar; a soft spring with a little bounce on the
    // way in, a calmer one on the way out
    val collapsed by remember(scrollProgress) { derivedStateOf { scrollProgress.value > 0.55f } }
    val morph = remember { Animatable(0f) }
    LaunchedEffect(collapsed) {
        morph.animateTo(
            targetValue = if (collapsed) 1f else 0f,
            animationSpec = if (Mov.reducido) tween(120) else spring(dampingRatio = if (collapsed) 0.62f else 0.9f, stiffness = 360f),
        )
    }
    return remember(scrollProgress, morph) { PlaylistCollapse(scrollProgress, morph) }
}

// the backdrop is pinned behind the list, so it has to travel most of the way up on its own
private const val CoverFollow = 0.8f
private const val CoverSoftBy = 0.55f

/**
 * La portada a sangre detrás de la lista (PlaylistCoverBackdrop de Mochy). [cover] pinta la nítida; [softCover] es
 * la copia pequeña que, antes de Android 12, se ve al subir en lugar del desenfoque de todo el marco.
 */
@Composable
fun PlaylistCoverBackdrop(
    cover: @Composable (Modifier) -> Unit,
    softCover: (@Composable (Modifier) -> Unit)?,
    coverHeight: Dp,
    scrollProgress: () -> Float,
    background: State<Color>,
    modifier: Modifier = Modifier,
) {
    // before android 12 the whole frame can't blur, so the picture alone goes soft from a copy blurred once off the main thread
    val frameBlur = rememberElementBlurAvailable()
    val soft = if (frameBlur || com.example.lector.ui.mochy.MochyPreferences.disableBlur) null else softCover

    ScrollAwayCover(
        progress = scrollProgress,
        follow = -CoverFollow,
        modifier = modifier
            .fillMaxWidth()
            .height(coverHeight),
    ) {
        if (soft != null) {
            soft(
                Modifier
                    .fillMaxSize()
                    // skipped entirely until the scroll starts revealing it
                    .graphicsLayer { alpha = if (scrollProgress() > 0f) 1f else 0f },
            )
        }
        cover(
            Modifier
                .fillMaxSize()
                .then(
                    if (soft != null) {
                        Modifier.graphicsLayer {
                            alpha = 1f - smoothstep(0f, CoverSoftBy, scrollProgress())
                            compositingStrategy = CompositingStrategy.ModulateAlpha
                        }
                    } else {
                        Modifier
                    }
                ),
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .drawWithCache {
                    val bg = background.value
                    val top = Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.34f),
                        1f to Color.Transparent,
                        endY = size.height * 0.24f,
                    )
                    val bottom = Brush.verticalGradient(
                        0.56f to Color.Transparent,
                        0.84f to bg.copy(alpha = 0.78f),
                        1f to bg,
                    )
                    onDrawBehind {
                        drawRect(top, size = Size(size.width, size.height * 0.24f))
                        drawRect(bottom)
                    }
                }
        )
    }
}

/**
 * La barra de arriba de Mochy (PlaylistTopBar): atrás (con la portada pequeña al plegarse) a la izquierda y los
 * botones a la derecha; con [mode] cambia a la barra de elegir, sobre el mismo cristal.
 * Shiori: [trailing] pone los botones de la derecha (Mochy solo tiene los puntos); [BarCircleButton] es su estilo.
 */
@Composable
fun PlaylistTopBar(
    glassState: GlassState,
    morph: () -> Float,
    miniCover: (@Composable (Modifier) -> Unit)?,
    background: State<Color>,
    onBackClick: () -> Unit,
    onBackLongClick: () -> Unit,
    onMiniCoverClick: () -> Unit,
    mode: PlaylistModeBar? = null,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    // con un modo puesto la barra va siempre sobre cristal (en Mochy lo hace el de ordenar)
    val forced = mode != null
    val glass: () -> Float = { if (forced) 1f else morph().coerceIn(0f, 1f) }
    val showChrome by remember(forced) { derivedStateOf { glass() > 0.01f } }
    val statusBarTop = WindowInsets.stableStatusBars.asPaddingValues().calculateTopPadding()
    val barModifier = Modifier
        .fillMaxWidth()
        .windowInsetsPadding(WindowInsets.stableStatusBars)
        .padding(top = 6.dp)
        .height(47.dp)
        .padding(start = 26.dp, end = 30.dp)

    Box(modifier = Modifier.fillMaxWidth()) {
        if (showChrome) {
            // the theme colour underneath keeps the sharp rows from showing through the frost
            ProgressiveGlassBackground(
                state = glassState,
                maxBlurRadius = { 34f * glass() },
                tint = background.value.copy(alpha = 0.4f),
                foundationColor = background.value,
                direction = BlurDirection.BottomToTop,
                steps = 3,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(statusBarTop + 96.dp)
                    .graphicsLayer { alpha = glass() }
            )
        }

        // keyed on the kind of mode only: a new count or a new lambda updates the bar in place,
        // only going in or out of a mode plays the swap
        AnimatedContent(
            targetState = mode,
            contentKey = { it?.javaClass },
            transitionSpec = {
                (fadeIn(tween(180, delayMillis = 60)) + slideInVertically(spring(dampingRatio = 0.8f, stiffness = 500f)) { -it / 3 })
                    .togetherWith(fadeOut(tween(110)) + slideOutVertically(tween(160)) { -it / 4 })
                    .using(SizeTransform(clip = false))
            },
            label = "playlistTopBarMode",
        ) { shownMode ->
            if (shownMode != null) {
                PlaylistModeBarRow(mode = shownMode, modifier = barModifier)
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = barModifier,
                ) {
                    BackPill(
                        morph = morph,
                        glassState = glassState,
                        miniCover = miniCover,
                        tint = background,
                        onBackClick = onBackClick,
                        onBackLongClick = onBackLongClick,
                        onMiniCoverClick = onMiniCoverClick,
                    )
                    Spacer(Modifier.weight(1f))
                    trailing()
                }
            }
        }
    }
}

/** El botón de los puntos de la barra de Mochy (30 dp): oscuro sobre la portada, claro sobre el cristal. */
@Composable
fun BarCircleButton(
    icon: ImageVector,
    contentDescription: String,
    morph: () -> Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
) {
    // Shiori: en el tema claro, plegada la barra (cristal claro), el icono pasa a la tinta oscura.
    val overGlass by remember { derivedStateOf { morph() > 0.5f } }
    val tint = when {
        filled -> PillContent
        overGlass && !TemaActual.oscuro -> LectorColors.Text
        else -> Color.White
    }
    val fill = PillFill
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(30.dp)
            .clip(CircleShape)
            .drawBehind {
                if (filled) {
                    drawCircle(fill)
                } else {
                    val m = morph().coerceIn(0f, 1f)
                    drawCircle(Color.Black.copy(alpha = 0.28f * (1f - m)))
                    drawCircle((if (TemaActual.oscuro) Color.White else Color.Black).copy(alpha = 0.18f * m))
                }
            }
            .clickable(onClick = onClick)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(18.dp),
        )
    }
}

/**
 * expanded it's only the back button. collapsed it's the 99x47 glass pill from the Figma, with a
 * small copy of the cover that grows out of the back button and settles beside it.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BackPill(
    morph: () -> Float,
    glassState: GlassState,
    miniCover: (@Composable (Modifier) -> Unit)?,
    tint: State<Color>,
    onBackClick: () -> Unit,
    onBackLongClick: () -> Unit,
    onMiniCoverClick: () -> Unit,
) {
    val density = LocalDensity.current
    val pillShape = RoundedCornerShape(17.dp)
    val miniShape = TrackShape

    Box(
        modifier = Modifier
            .size(width = 99.dp, height = 47.dp)
            .graphicsLayer { translationX = with(density) { 10.dp.toPx() } * morph() }
    ) {
        // the pill's size follows the spring in the layout pass, so it stretches out of the back
        // button (and overshoots a hair) without recomposing anything
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .layout { measurable, _ ->
                    val m = morph().coerceAtLeast(0f)
                    val width = lerp(30.dp, 99.dp, m).roundToPx()
                    val height = lerp(30.dp, 47.dp, m).roundToPx()
                    val placeable = measurable.measure(Constraints.fixed(width, height))
                    layout(width, height) { placeable.place(0, 0) }
                }
                .graphicsLayer { alpha = morph().coerceIn(0f, 1f) }
        ) {
            // fully clear at morph 0, so the glass isn't composed (or recomposed by the theme colour tween) until the bar collapses
            val showPill by remember { derivedStateOf { morph() > 0f } }
            if (showPill) BackPillGlass(glassState = glassState, tint = tint, shape = pillShape)
        }

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .graphicsLayer { translationX = with(density) { 11.dp.toPx() } * morph() }
                .size(30.dp)
                .clip(CircleShape)
                .drawBehind {
                    val m = morph().coerceIn(0f, 1f)
                    drawCircle(Color.Black.copy(alpha = 0.28f + 0.17f * m))
                }
                .combinedClickable(onClick = onBackClick, onLongClick = onBackLongClick)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                contentDescription = "Atrás",
                tint = Color.White,
                modifier = Modifier.size(22.dp),
            )
        }

        val showMini by remember { derivedStateOf { morph() > 0.02f } }
        if (showMini && miniCover != null) {
            val miniModifier = Modifier
                .align(Alignment.CenterStart)
                .offset(x = 56.dp)
                .graphicsLayer {
                    // starts tucked under the back button, small, and springs out to its slot;
                    // the spring overshoots a touch, so it lands with a little give
                    val m = morph()
                    translationX = with(density) { (-45).dp.toPx() } * (1f - m)
                    val s = 0.35f + 0.65f * m
                    scaleX = s
                    scaleY = s
                    alpha = m.coerceIn(0f, 1f)
                    shape = miniShape
                    clip = true
                }
                .size(37.dp)
                .clickable(onClick = onMiniCoverClick)
            miniCover(miniModifier)
        }
    }
}

/** the frosted fill and hairline of the collapsed back pill; the theme colour is read here so only this recomposes */
@Composable
private fun BoxScope.BackPillGlass(glassState: GlassState, tint: State<Color>, shape: Shape) {
    GlassPillBackground(
        state = glassState,
        blurRadius = { 28f },
        tint = Color.White.copy(alpha = 0.12f),
        foundationColor = tint.value,
        shape = shape,
        modifier = Modifier.matchParentSize()
    )
    Box(
        modifier = Modifier
            .matchParentSize()
            .border(1.dp, Color.White.copy(alpha = 0.12f), shape)
    )
}

/**
 * title, who made it, a stats line, then Play / shuffle (sf) / add to library (rn).
 *
 * with a [description] (albums) the line under the creator is the description and the stats move
 * below it. Shiori: la cabecera FLOW (el bloque al pie de la banda de la portada); [actions] son los botones
 * redondos tras la píldora de leer, y tocar la banda de la portada llama a [onCoverClick].
 */
@Composable
fun PlaylistHeader(
    title: String,
    creators: List<CreatorUi>,
    stats: String,
    playLabel: String,
    playEnabled: Boolean,
    onPlayClick: () -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    onDescriptionLongClick: () -> Unit = {},
    onCoverClick: () -> Unit = {},
    onTitleClick: () -> Unit = {},
    onTitleLongClick: () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
) {
    val ink = LectorColors.Text
    val coverHeight = playlistCoverHeight()
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = coverHeight * 0.975f),
        contentAlignment = Alignment.BottomStart,
    ) {
        // la banda de la portada, encima de la que está pintada detrás: tocarla abre la portada en grande
        Box(
            Modifier
                .matchParentSize()
                .clickable(interactionSource = null, indication = null, onClick = onCoverClick)
        )
        Column(
            modifier = Modifier.padding(
                start = PlaylistInset + 1.dp,
                end = PlaylistInset,
                bottom = 14.dp,
            )
        ) {
            Text(
                text = title,
                style = PlaylistTitleStyle,
                color = ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                // Como Komikku: tocar el título lo busca en todas las fuentes; mantenerlo pulsado lo copia.
                modifier = Modifier.pointerInput(Unit) {
                    detectTapGestures(onTap = { onTitleClick() }, onLongPress = { onTitleLongClick() })
                },
            )
            if (creators.isNotEmpty()) {
                Spacer(Modifier.height(3.dp))
                CreatorRow(creators)
            }
            if (!description.isNullOrBlank()) {
                Spacer(Modifier.height(5.dp))
                PlaylistDescription(description, onLongClick = onDescriptionLongClick)
            }
            if (stats.isNotEmpty()) {
                Spacer(Modifier.height(if (description.isNullOrBlank()) 3.dp else 6.dp))
                Text(
                    text = stats,
                    style = PlaylistStatsStyle,
                    color = ink.copy(alpha = 0.62f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                PlayPill(label = playLabel, enabled = playEnabled, onClick = onPlayClick)
                Spacer(Modifier.width(9.dp))
                actions()
            }
        }
    }
}

/**
 * an album's description where the stats line used to be: two lines, and a tap opens the rest in
 * place. the height eases with the text so the rows below glide down instead of jumping.
 * (Shiori: mantener pulsado abre la sinopsis completa.)
 */
@Composable
internal fun PlaylistDescription(text: String, modifier: Modifier = Modifier, onLongClick: () -> Unit = {}) {
    val longClick by androidx.compose.runtime.rememberUpdatedState(onLongClick)
    Text(
        text = text,
        style = PlaylistStatsStyle.copy(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
        color = LectorColors.Text.copy(alpha = 0.74f),
        maxLines = 3,
        overflow = TextOverflow.Ellipsis,
        // Ya no se despliega en su sitio (crecía hacia arriba y tapaba la portada): tocar abre la información completa.
        modifier = modifier.pointerInput(Unit) { detectTapGestures(onTap = { longClick() }, onLongPress = { longClick() }) },
    )
}

/** play and pause swap with a little pop, and the pill eases to the new label's width */
@Composable
internal fun PlayPill(label: String, enabled: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val content = if (TemaActual.oscuro) PillContent else LectorColors.Text
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .pressBounce(interaction)
            .graphicsLayer { alpha = if (enabled) 1f else 0.5f }
            .height(33.dp)
            .clip(RoundedCornerShape(50))
            .background(PillFill)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick)
            .animateContentSize(spring(dampingRatio = 0.7f, stiffness = 500f))
            .padding(start = 21.dp, end = 17.dp)
    ) {
        Icon(
            imageVector = Icons.Rounded.PlayArrow,
            contentDescription = null,
            tint = content,
            modifier = Modifier.size(17.dp),
        )
        Spacer(Modifier.width(8.dp))
        AnimatedContent(
            targetState = label,
            transitionSpec = { labelSwap() },
            label = "playPillLabel",
        ) { shown ->
            Text(
                text = shown,
                style = PillLabelStyle,
                color = content,
                maxLines = 1,
            )
        }
    }
}

/** the round buttons squish when pressed; shuffle gives its arrows a spin, and a changed icon pops in */
@Composable
fun CircleAction(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    spinOnClick: Boolean = false,
    tint: Color? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val spin = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val content = tint ?: if (TemaActual.oscuro) PillContent else LectorColors.Text
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .pressBounce(interaction)
            .size(33.dp)
            .clip(CircleShape)
            .background(PillFill)
            .clickable(interactionSource = interaction, indication = null) {
                if (spinOnClick) {
                    scope.launch {
                        spin.snapTo(0f)
                        spin.animateTo(1f, tween(520, easing = FastOutSlowInEasing))
                    }
                }
                onClick()
            }
    ) {
        AnimatedContent(
            targetState = icon,
            transitionSpec = { glyphSwap() },
            label = "circleActionIcon",
        ) { res ->
            Icon(
                imageVector = res,
                contentDescription = contentDescription,
                tint = content,
                modifier = Modifier
                    .size(17.dp)
                    .graphicsLayer { rotationZ = spin.value * 360f },
            )
        }
    }
}

/** an icon swap: the new one springs up from small while the old one shrinks away */
private fun <S> AnimatedContentTransitionScope<S>.glyphSwap(): ContentTransform =
    (scaleIn(initialScale = 0.4f, animationSpec = spring(dampingRatio = 0.5f, stiffness = 700f)) + fadeIn(tween(120)))
        .togetherWith(scaleOut(targetScale = 0.4f, animationSpec = tween(110)) + fadeOut(tween(90)))
        .using(SizeTransform(clip = false))

/** a label swap: the new word rises in as the old one lifts out */
private fun <S> AnimatedContentTransitionScope<S>.labelSwap(): ContentTransform =
    (slideInVertically(spring(dampingRatio = 0.75f, stiffness = 600f)) { it / 2 } + fadeIn(tween(140)))
        .togetherWith(slideOutVertically(tween(120)) { -it / 2 } + fadeOut(tween(100)))
        .using(SizeTransform(clip = false))

/** creator avatars side by side, overlapping when there's more than one */
@Composable
private fun CreatorRow(creators: List<CreatorUi>) {
    val shown = creators.take(3)
    val avatar = 16.dp
    val step = 11.dp
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(width = avatar + step * (shown.size - 1), height = avatar)) {
            shown.forEachIndexed { index, creator ->
                CreatorAvatar(
                    creator = creator,
                    size = avatar,
                    modifier = Modifier
                        .offset(x = step * index)
                        .zIndex((shown.size - index).toFloat())
                )
            }
        }
        Spacer(Modifier.width(6.dp))
        Text(
            text = when (creators.size) {
                1 -> creators[0].name
                2 -> "${creators[0].name} y ${creators[1].name}"
                else -> "${creators[0].name}, ${creators[1].name} y ${creators.size - 2} más"
            },
            style = PlaylistCreatorStyle,
            color = LectorColors.Text.copy(alpha = 0.88f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** (Shiori: los autores no tienen foto; siempre la inicial) */
@Composable
private fun CreatorAvatar(creator: CreatorUi, size: Dp, modifier: Modifier = Modifier) {
    val ink = LectorColors.Text
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(ink.copy(alpha = 0.28f))
            .border(1.dp, ink.copy(alpha = 0.7f), CircleShape)
    ) {
        Text(
            text = creator.name.take(1).uppercase(),
            style = PlaylistStatsStyle.copy(fontSize = 8.sp, fontWeight = FontWeight.Bold),
            color = ink,
        )
    }
}

/** the "last added ⌄" label under the buttons (Shiori: abre la hoja de filtro y orden de los capítulos) */
@Composable
fun PlaylistSortControl(
    label: String,
    descending: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ink = LectorColors.Text.copy(alpha = 0.72f)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp, horizontal = 2.dp)
    ) {
        Text(
            text = label.lowercase(),
            style = PlaylistStatsStyle.copy(fontSize = 12.sp),
            color = ink,
            maxLines = 1,
        )
        Icon(
            imageVector = Icons.Rounded.KeyboardArrowDown,
            contentDescription = if (descending) "Descendente" else "Ascendente",
            tint = ink,
            modifier = Modifier
                .size(16.dp)
                .graphicsLayer { rotationZ = if (!descending) 180f else 0f }
        )
    }
}

// a pressed track lights a pill from one side of the screen to the other, like the swipe slab
// (Shiori: con la tinta del tema, que en el claro es oscura)
@Composable
private fun trackHighlight(): RoundedHighlight {
    val ink = LectorColors.Text
    return remember(ink) { RoundedHighlight(cornerRadius = 100.dp, spillX = (-10).dp, color = ink.copy(alpha = 0.12f)) }
}

/**
 * a song row as drawn in the Figma playlist: 39dp cover, two lines, the dots on the right.
 * Shiori: fila de capítulo. El cuadrado de 39 dp lleva el número; el punto de acento a la izquierda = sin leer;
 * leído = texto apagado. [trailing] es el botón de descarga o, eligiendo, el visto redondo.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlaylistTrackRow(
    badge: String,
    title: String,
    subtitle: String,
    read: Boolean,
    accent: Color,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
    /** delante del título (marcador) */
    leading: (@Composable () -> Unit)? = null,
    /** la página por la que vas, en acento, antes del subtítulo */
    progressLabel: String? = null,
    /** picked while selecting: the row sits on a soft band */
    selected: Boolean = false,
) {
    val band by animateFloatAsState(if (selected) 1f else 0f, tween(180), label = "selectedBand")
    val ink = LectorColors.Text
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .selectedRowBand(ink) { band }
            .combinedClickable(
                interactionSource = null,
                indication = trackHighlight(),
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .padding(start = PlaylistInset - 17.dp, end = 18.dp, top = 9.dp, bottom = 9.dp)
    ) {
        Box(Modifier.width(17.dp), contentAlignment = Alignment.CenterStart) {
            if (!read) Box(Modifier.padding(start = 3.dp).size(7.dp).clip(CircleShape).background(accent))
        }
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(39.dp)
                .clip(TrackShape)
                .background(BoneColor)
        ) {
            Text(
                text = badge,
                style = TrackSubtitleStyle.copy(fontWeight = FontWeight.Bold, textAlign = TextAlign.Center),
                color = ink.copy(alpha = if (read) 0.45f else 0.85f),
                maxLines = 1,
                overflow = TextOverflow.Clip,
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 11.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (leading != null) {
                    leading()
                    Spacer(Modifier.width(5.dp))
                }
                Text(
                    text = title,
                    style = TrackTitleStyle,
                    color = ink.copy(alpha = if (read && !selected) 0.45f else 0.94f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (progressLabel != null) {
                    Text(
                        text = progressLabel,
                        style = TrackSubtitleStyle.copy(fontWeight = FontWeight.Bold),
                        color = accent,
                        maxLines = 1,
                    )
                    if (subtitle.isNotEmpty()) Spacer(Modifier.width(6.dp))
                }
                Text(
                    text = subtitle,
                    style = TrackSubtitleStyle,
                    color = ink.copy(alpha = if (read) 0.4f else 0.55f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        trailing?.invoke()
    }
}

/** stand-in rows while the songs are still loading */
@Composable
fun PlaylistTrackBones(rows: Int = 7) {
    BonesHost(modifier = Modifier.fillMaxWidth()) {
        repeat(rows) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = PlaylistInset, top = 9.dp, bottom = 9.dp)
            ) {
                Bone(width = 39.dp, height = 39.dp, shape = TrackShape)
                Spacer(Modifier.width(11.dp))
                Column {
                    Bone(width = 78.dp, height = 13.dp)
                    Spacer(Modifier.height(4.dp))
                    Bone(width = 45.dp, height = 9.dp)
                }
            }
        }
    }
}

/** the receipt-style footer: N SONGS · E N D  O F  L I N E · N MINUTES between two rules */
@Composable
fun PlaylistEndOfLine(
    start: String,
    end: String,
    modifier: Modifier = Modifier,
    center: String = "FIN",
) {
    val ink = LectorColors.Text
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = PlaylistInset)
            .padding(top = 18.dp, bottom = 6.dp)
    ) {
        ReceiptRule()
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .height(25.dp)
        ) {
            Text(
                text = start,
                style = ReceiptSmallStyle,
                color = ink.copy(alpha = 0.6f),
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = center,
                style = ReceiptCenterStyle,
                color = ink.copy(alpha = 0.78f),
                maxLines = 1,
                textAlign = TextAlign.Center,
            )
            Text(
                text = end,
                style = ReceiptSmallStyle,
                color = ink.copy(alpha = 0.6f),
                maxLines = 1,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f),
            )
        }
        ReceiptRule()
    }
}

@Composable
private fun ReceiptRule() {
    val ink = LectorColors.Text
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .drawBehind {
                drawLine(
                    color = ink.copy(alpha = 0.24f),
                    start = Offset(0f, size.height / 2f),
                    end = Offset(size.width, size.height / 2f),
                    strokeWidth = 0.75.dp.toPx(),
                )
            }
    )
}

/** header for the sections after the list, like "Featured Artist >" */
@Composable
fun PlaylistSectionTitle(
    title: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    showChevron: Boolean = onClick != null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    val ink = LectorColors.Text
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(start = PlaylistInset - 4.dp, end = PlaylistInset - 12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .clickable(enabled = onClick != null) { onClick?.invoke() }
                .padding(horizontal = 4.dp, vertical = 4.dp)
        ) {
            Text(text = title, style = SectionTitleStyle, color = ink, maxLines = 1)
            if (showChevron) {
                Spacer(Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowRight,
                    contentDescription = null,
                    tint = ink.copy(alpha = 0.6f),
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        if (trailing != null) {
            Spacer(Modifier.weight(1f))
            trailing()
        }
    }
}
