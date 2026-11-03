// Copiado de Mochy Music (github.com/realidkroo/Mochy-Music, GPL-3.0): app/src/main/kotlin/com/mochyy/mochymusic/ui/component/HomeCards.kt
// (Shiori: canciones -> cómics/mangas; AsyncImage -> ComicCover; R.drawable -> iconos Material Rounded;
// los blancos fijos siguen a LectorColors.Text para que el tema claro también funcione; las portadas son 2:3)

package com.example.lector.ui.mochy.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListPrefetchStrategy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.example.lector.Almacen
import com.example.lector.Comic
import com.example.lector.ui.ComicCover
import com.example.lector.ui.LectorColors
import com.example.lector.ui.mochy.BoneColor
import com.example.lector.ui.mochy.HomeCardCornerRadius
import com.example.lector.ui.mochy.InterFontFamily
import com.example.lector.ui.mochy.RevealSeenState
import com.example.lector.ui.mochy.RoundedHighlight
import com.example.lector.ui.mochy.revealOnAppear
import com.example.lector.ui.origenTransicion
import com.example.lector.ui.press3D
import com.example.lector.ui.progresoLectura
import com.example.lector.ui.textoPagina
import kotlin.math.min

// spacing and type straight from the Figma home frames (13:11 / 14:212)
val HomeContentInset = 36.dp
val HomeCardSpacing = 15.dp

private val HomeTracking = (-0.06).em

val HomeLabelStyle = TextStyle(
    fontFamily = InterFontFamily,
    fontWeight = FontWeight.Bold,
    fontSize = 16.sp,
    lineHeight = 19.sp,
    letterSpacing = HomeTracking,
)

val HomeTitleStyle = TextStyle(
    fontFamily = InterFontFamily,
    fontWeight = FontWeight.Bold,
    fontSize = 24.sp,
    lineHeight = 28.sp,
    letterSpacing = HomeTracking,
)

private val HomeCardTitleStyle = TextStyle(
    fontFamily = InterFontFamily,
    fontWeight = FontWeight.SemiBold,
    fontSize = 14.sp,
    lineHeight = 17.sp,
    letterSpacing = (-0.03).em,
)

private val HomeCardSubtitleStyle = TextStyle(
    fontFamily = InterFontFamily,
    fontWeight = FontWeight.Medium,
    fontSize = 12.sp,
    lineHeight = 15.sp,
    letterSpacing = (-0.02).em,
)

// Shiori: Color.White -> LectorColors.Text (tema claro)
private val SectionLabelColor: Color get() = LectorColors.Text.copy(alpha = 0.69f)
val HomeRowHeight = 54.dp

/**
 * A song row's height: [HomeRowHeight], or taller when a big system font size makes the title and
 * artist lines need more room. A fixed 54dp cut both lines off at the top and bottom.
 */
@Composable
fun homeRowHeight(): Dp {
    val linesHeight = with(LocalDensity.current) { (HomeRowTitleLineHeight.value + HomeCardSubtitleStyle.lineHeight.value).sp.toDp() }
    return maxOf(HomeRowHeight, linesHeight + 14.dp)
}

private val HomeRowTitleLineHeight = 17.sp

// pressing anything on Home shows a soft rounded box behind it instead of a square ripple
private val SectionTapMargin = 12.dp
private val SectionTitleHighlight = RoundedHighlight(cornerRadius = 100.dp)
private val TrackRowHighlight = RoundedHighlight(cornerRadius = 22.dp, spillX = 8.dp)

/** for the cover cards: a little bigger than the card all round, cornered to match */
val HomeCardHighlight = RoundedHighlight(cornerRadius = HomeCardCornerRadius + 6.dp, spillX = 7.dp, spillY = 7.dp)

/**
 * Shiori: el ancho de una portada en Inicio. En Mochy es la tarjeta cuadrada de 137 dp de Figma; las portadas de
 * manga son 2:3, así que se queda en 120 dp de ancho (180 de alto) para que quepan tres y un trozo.
 */
fun homeCardSize(): Dp = 120.dp

/**
 * "Recently Played >" style header. the whole line is the tap target, so pressing it lights a
 * pill from one side of the screen to the other.
 * (Shiori: [expanded] gira el chevrón cuando «ver todo» despliega la sección en su sitio)
 */
@Composable
fun HomeSectionTitle(
    title: String,
    modifier: Modifier = Modifier,
    label: String? = null,
    thumbnail: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    expanded: Boolean = false,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(start = SectionTapMargin, end = SectionTapMargin, bottom = 6.dp)
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = null,
                        indication = SectionTitleHighlight,
                        onClick = onClick,
                    )
                } else {
                    Modifier
                }
            )
            // the text still starts on the 36dp inset, inside the wider tap area
            .padding(
                start = HomeContentInset - SectionTapMargin,
                end = HomeContentInset - SectionTapMargin,
                top = 4.dp,
                bottom = 4.dp,
            ),
    ) {
        if (thumbnail != null) {
            thumbnail()
            Spacer(Modifier.width(10.dp))
        }
        Column(modifier = Modifier.weight(1f, fill = false)) {
            if (label != null) {
                Text(
                    text = label,
                    style = HomeCardSubtitleStyle,
                    color = LectorColors.Text.copy(alpha = 0.45f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = title,
                style = HomeLabelStyle,
                color = SectionLabelColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (onClick != null) {
            Spacer(Modifier.width(6.dp))
            SectionChevron(expanded)
        }
    }
}

@Composable
private fun SectionChevron(expanded: Boolean) {
    val turn by animateFloatAsState(if (expanded) 90f else 0f, spring(dampingRatio = 0.75f, stiffness = 520f), label = "chevron")
    Icon(
        imageVector = Icons.Rounded.KeyboardArrowRight,
        contentDescription = if (expanded) "Ver menos" else "Ver todo",
        tint = LectorColors.Text.copy(alpha = 0.55f),
        modifier = Modifier.size(18.dp).graphicsLayer { rotationZ = turn },
    )
}

/**
 * cover with two lines under it. no outline, on purpose.
 * (Shiori: la portada del cómic a 2:3, con la barra de progreso de lectura encima si ya lo empezaste)
 */
@Composable
fun HomeCoverCard(
    comic: Comic,
    almacen: Almacen,
    modifier: Modifier = Modifier,
) {
    val size = homeCardSize()
    val shape = RoundedCornerShape(HomeCardCornerRadius * 0.72f)
    val progreso = comic.progresoLectura()

    Column(modifier = modifier.width(size)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                // Shiori: la portada abre la ficha creciendo desde aquí (ContainerTransform)
                .origenTransicion()
                .press3D()
                .clip(shape)
                .background(BoneColor),
        ) {
            ComicCover(comic, almacen, Modifier.fillMaxSize(), shape)
            if (progreso > 0f) {
                Box(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(44.dp)
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f))))
                )
                Box(
                    Modifier.align(Alignment.BottomCenter).padding(horizontal = 10.dp, vertical = 10.dp)
                        .fillMaxWidth().height(3.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.28f))
                ) {
                    Box(Modifier.fillMaxWidth(progreso.coerceIn(0f, 1f)).fillMaxHeight().clip(CircleShape).background(Color.White))
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = comic.titulo,
            style = HomeCardTitleStyle,
            color = LectorColors.Text.copy(alpha = 0.92f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = comic.textoPagina(),
            style = HomeCardSubtitleStyle,
            color = LectorColors.Text.copy(alpha = 0.5f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// a row scrolling into view prefetches 2 cards by default, but 3 to 4 are showing, so the rest composed in the frame that revealed them
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun rememberCardRowState(): LazyListState =
    rememberLazyListState(prefetchStrategy = remember { LazyListPrefetchStrategy(nestedPrefetchItemCount = 4) })

/** a horizontally scrolling row of cards, lined up with the section title. cards cascade in once. */
@Composable
fun <T> HomeCardRow(
    rowKey: String,
    items: List<T>,
    revealSeen: RevealSeenState,
    key: (T) -> Any,
    modifier: Modifier = Modifier,
    content: @Composable (T) -> Unit,
) {
    LazyRow(
        modifier = modifier,
        state = rememberCardRowState(),
        contentPadding = PaddingValues(horizontal = HomeContentInset),
        horizontalArrangement = Arrangement.spacedBy(HomeCardSpacing),
    ) {
        itemsIndexed(items = items, key = { _, item -> key(item) }) { index, item ->
            Box(
                modifier = Modifier.revealOnAppear(
                    key = "$rowKey/${key(item)}",
                    seenState = revealSeen,
                    delayMillis = min(index, 4) * 60,
                )
            ) {
                content(item)
            }
        }
    }
}

/**
 * the "list" rows: 54dp cover, two lines, a small button.
 * (Shiori: el botón de «más» es el de seguir leyendo, y debajo del subtítulo va la barra de progreso)
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeTrackRow(
    comic: Comic,
    almacen: Almacen,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    onMoreClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    showProgress: Boolean = false,
    coverShape: Shape = RoundedCornerShape((HomeCardCornerRadius * 0.56f).coerceIn(0.dp, 20.dp)),
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .height(homeRowHeight())
            .combinedClickable(
                interactionSource = null,
                indication = TrackRowHighlight,
                onClick = onClick,
                onLongClick = onLongClick,
            ),
    ) {
        Box(
            modifier = Modifier
                .origenTransicion()
                .size(HomeRowHeight)
                .clip(coverShape)
                .background(BoneColor),
        ) {
            ComicCover(comic, almacen, Modifier.fillMaxSize(), coverShape)
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 13.dp),
        ) {
            Text(
                text = comic.titulo,
                style = HomeCardTitleStyle.copy(fontSize = 15.sp),
                color = LectorColors.Text.copy(alpha = 0.92f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = comic.textoPagina(),
                style = HomeCardSubtitleStyle,
                color = LectorColors.Text.copy(alpha = 0.5f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (showProgress) {
                Spacer(Modifier.height(5.dp))
                Box(Modifier.fillMaxWidth().height(3.dp).clip(CircleShape).background(LectorColors.Text.copy(alpha = 0.14f))) {
                    Box(
                        Modifier.fillMaxWidth(comic.progresoLectura().coerceIn(0f, 1f)).fillMaxHeight()
                            .clip(CircleShape).background(LectorColors.Text.copy(alpha = 0.85f))
                    )
                }
            }
        }
        if (onMoreClick != null) Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .clickable(onClick = onMoreClick),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(24.dp)
                    .background(LectorColors.Text.copy(alpha = 0.14f), CircleShape),
            ) {
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = "Leer",
                    tint = LectorColors.Text.copy(alpha = 0.85f),
                    modifier = Modifier.size(17.dp),
                )
            }
        }
    }
}
