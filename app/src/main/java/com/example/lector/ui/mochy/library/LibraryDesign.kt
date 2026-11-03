// Copiado de Mochy Music (github.com/realidkroo/Mochy-Music, GPL-3.0): app/src/main/kotlin/com/mochyy/mochymusic/ui/screens/library/LibraryDesign.kt
// (y piezas de ui/screens/library/LibraryTabs.kt, ui/screens/search/SearchDesign.kt, ui/component/HomeCards.kt,
//  ui/component/detail/PlaylistDetail.kt y ui/screens/playlist/GeneratedPlaylistKit.kt)
//
// Adaptado a Shiori: los colores siguen al tema (claro / oscuro) a través de LectorColors, las pestañas son las
// categorías de la biblioteca (desplazables y con su número) y siguen la posición del pager.

package com.example.lector.ui.mochy.library

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.example.lector.ui.LectorColors
import com.example.lector.ui.Mov
import com.example.lector.ui.Muelles
import com.example.lector.ui.TemaActual
import com.example.lector.ui.mochy.HomeCardCornerRadius
import com.example.lector.ui.mochy.InterFontFamily
import com.example.lector.ui.mochy.RoundedHighlight
import kotlin.math.roundToInt

// ---------------------------------------------------------------------------------------------
// Colores y medidas (SearchDesign.kt / HomeCards.kt)
// ---------------------------------------------------------------------------------------------

/** SearchColors de Mochy; en tema claro se usan los equivalentes de Shiori. */
object LibraryColors {
    private val oscuro get() = TemaActual.oscuro
    val Field: Color get() = if (oscuro) Color(0xFF141416) else LectorColors.Card
    val Tile: Color get() = if (oscuro) Color(0xFF121214) else LectorColors.Card
    val TileHigh: Color get() = if (oscuro) Color(0xFF1C1C1F) else LectorColors.CardHigh
    val Divider: Color get() = if (oscuro) Color(0xFF232326) else LectorColors.Linea
    val Primary: Color get() = LectorColors.Text
    val Secondary: Color get() = LectorColors.Muted
    val Placeholder: Color get() = LectorColors.Pista

    // LibraryTabs.kt
    val ChipTrack: Color get() = if (oscuro) Color(0xFF161618) else Color(0xFFE9E9EE)
    val Chip: Color get() = if (oscuro) Color(0xFF232326) else Color(0xFFDDDDE3)
    val ChipSelected: Color get() = if (oscuro) Color(0xFF3D3D41) else Color(0xFFFFFFFF)

    /** Color.White.copy(alpha = 0.92f) / 0.5f de los títulos y subtítulos de las filas. */
    val Title: Color get() = LectorColors.Text.copy(alpha = 0.92f)
    val Subtitle: Color get() = LectorColors.Text.copy(alpha = 0.5f)
}

val SearchHorizontalPadding = 24.dp
val HomeCardSpacing = 15.dp

// PlaylistDetail.kt
internal val PlaylistInset = 36.dp
internal val PlaylistTracking = (-0.06).em

internal val TrackTitleStyle = TextStyle(
    fontFamily = InterFontFamily,
    fontWeight = FontWeight.SemiBold,
    fontSize = 14.sp,
    lineHeight = 17.sp,
    letterSpacing = (-0.03).em,
)
internal val TrackSubtitleStyle = TextStyle(
    fontFamily = InterFontFamily,
    fontWeight = FontWeight.Medium,
    fontSize = 11.5.sp,
    lineHeight = 14.sp,
    letterSpacing = (-0.02).em,
)
internal val SectionTitleStyle = TextStyle(
    fontFamily = InterFontFamily,
    fontWeight = FontWeight.Bold,
    fontSize = 16.sp,
    letterSpacing = PlaylistTracking,
)

// HomeCards.kt
internal val HomeCardTitleStyle = TextStyle(
    fontFamily = InterFontFamily,
    fontWeight = FontWeight.SemiBold,
    fontSize = 14.sp,
    lineHeight = 17.sp,
    letterSpacing = (-0.03).em,
)

internal val HomeCardSubtitleStyle = TextStyle(
    fontFamily = InterFontFamily,
    fontWeight = FontWeight.Medium,
    fontSize = 12.sp,
    lineHeight = 15.sp,
    letterSpacing = (-0.02).em,
)

val HomeRowHeight = 54.dp

private val HomeRowTitleLineHeight = 17.sp

/**
 * A song row's height: [HomeRowHeight], or taller when a big system font size makes the title and
 * artist lines need more room. A fixed 54dp cut both lines off at the top and bottom.
 */
@Composable
fun homeRowHeight(): Dp {
    val linesHeight = with(LocalDensity.current) { (HomeRowTitleLineHeight.value + HomeCardSubtitleStyle.lineHeight.value).sp.toDp() }
    return maxOf(HomeRowHeight, linesHeight + 14.dp)
}

// pressing anything on Home shows a soft rounded box behind it instead of a square ripple
internal val TrackRowHighlight = RoundedHighlight(cornerRadius = 22.dp, spillX = 8.dp)

/** for the cover cards: a little bigger than the card all round, cornered to match */
internal val HomeCardHighlight: RoundedHighlight
    get() = RoundedHighlight(cornerRadius = HomeCardCornerRadius + 6.dp, spillX = 7.dp, spillY = 7.dp)

/** Home's row cover, so the Library's rows match it exactly. */
internal val LibraryRowCoverCorner = 14.dp
internal val LibraryRowCoverSize = 54.dp

// ---------------------------------------------------------------------------------------------
// Título de pantalla (SearchGlassTopBar: 34sp en negrita y un subtítulo de 12sp)
// ---------------------------------------------------------------------------------------------

@Composable
fun LibraryTitleBlock(title: String, subtitle: String?, modifier: Modifier = Modifier, subtitleContent: (@Composable () -> Unit)? = null) {
    Column(modifier, verticalArrangement = Arrangement.Center) {
        Text(
            text = title,
            style = MaterialTheme.typography.displaySmall.copy(fontFamily = InterFontFamily, fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 40.sp),
            color = LibraryColors.Primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (subtitleContent != null) subtitleContent()
        else if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = InterFontFamily, fontSize = 12.sp),
                color = LibraryColors.Secondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Botón redondo de la cabecera, con la «more» de HomeTrackRow: un círculo suave dentro de un área de toque mayor.
 * [activo] lo tiñe de acento (búsqueda abierta, filtros puestos).
 */
@Composable
fun LibraryIconButton(icon: ImageVector, contentDescription: String, active: Boolean = false, onClick: () -> Unit) {
    val acento = MaterialTheme.colorScheme.primary
    val fondo by animateColorAsState(
        if (active) acento.copy(alpha = 0.22f) else LibraryColors.Field,
        tween(220), label = "libraryIconButton",
    )
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(fondo)
            .clickable(role = Role.Button, onClick = onClick),
    ) {
        Icon(icon, contentDescription, tint = if (active) acento else LibraryColors.Primary.copy(alpha = 0.85f), modifier = Modifier.size(21.dp))
    }
}

// ---------------------------------------------------------------------------------------------
// Pestañas (LibraryTabs.kt)
// ---------------------------------------------------------------------------------------------

/**
 * The tabs as small pills in one dark track, the picked one a step lighter.
 * En Shiori son las categorías: la fila se desplaza sola para dejar a la vista la elegida.
 */
@Composable
fun LibraryTabChips(
    labels: List<String>,
    counts: List<Int>?,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val n = labels.size
    val scroll = rememberScrollState()
    // [izquierda, ancho] de cada píldora, en px dentro de la pista.
    val bordes = remember(n) { mutableStateListOf(*Array(n * 2) { 0f }) }
    var anchoVista by remember { mutableIntStateOf(0) }
    val margen = with(LocalDensity.current) { (SearchHorizontalPadding + 3.dp).toPx() }
    val actual = selected.coerceIn(0, (n - 1).coerceAtLeast(0))

    LaunchedEffect(actual, anchoVista, bordes.getOrNull(actual * 2 + 1)) {
        val w = bordes.getOrNull(actual * 2 + 1) ?: return@LaunchedEffect
        if (w <= 0f || anchoVista == 0) return@LaunchedEffect
        val centro = margen + bordes[actual * 2] + w / 2f
        val destino = (centro - anchoVista / 2f).roundToInt().coerceIn(0, scroll.maxValue)
        runCatching { if (Mov.reducido) scroll.scrollTo(destino) else scroll.animateScrollTo(destino, Muelles.espacial()) }
    }

    Box(
        modifier
            .fillMaxWidth()
            .onSizeChanged { anchoVista = it.width }
            .horizontalScroll(scroll)
            .padding(horizontal = SearchHorizontalPadding),
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(percent = 50))
                .background(LibraryColors.ChipTrack)
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            labels.forEachIndexed { i, label ->
                val isSelected = i == actual
                val background by animateColorAsState(
                    targetValue = if (isSelected) LibraryColors.ChipSelected else LibraryColors.Chip,
                    animationSpec = tween(220),
                    label = "libraryTabChip",
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .onPlaced { c ->
                            val x = c.positionInParent().x; val w = c.size.width.toFloat()
                            if (i * 2 + 1 < bordes.size) {
                                if (bordes[i * 2] != x) bordes[i * 2] = x
                                if (bordes[i * 2 + 1] != w) bordes[i * 2 + 1] = w
                            }
                        }
                        .clip(RoundedCornerShape(percent = 50))
                        .background(background)
                        .clickable(
                            role = Role.Tab,
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { onSelect(i) },
                        )
                        .heightIn(min = 28.dp)
                        .padding(horizontal = 14.dp, vertical = 5.dp),
                ) {
                    Text(
                        text = label,
                        color = if (isSelected) LibraryColors.Primary else LibraryColors.Primary.copy(alpha = 0.72f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = InterFontFamily,
                        maxLines = 1,
                    )
                    val cuenta = counts?.getOrNull(i)
                    if (cuenta != null) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = cuenta.toString(),
                            color = LibraryColors.Secondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = InterFontFamily,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Small shared pieces
// ---------------------------------------------------------------------------------------------

@Composable
fun LibraryRule(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = SearchHorizontalPadding)
            .height(1.dp)
            .background(LibraryColors.Divider),
    )
}

@Composable
fun LibrarySectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = LibraryColors.Primary,
        fontSize = 20.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = InterFontFamily,
        modifier = modifier.padding(horizontal = SearchHorizontalPadding),
    )
}

/**
 * Centred placeholder for a screen or section with nothing in it.
 * Shiori: un título encima del texto y, si hace falta, la píldora de LibraryManagePill como acción.
 */
@Composable
fun LibraryEmptyState(
    modifier: Modifier = Modifier,
    text: String = "Aquí no hay nada",
    title: String? = null,
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = 56.dp, horizontal = SearchHorizontalPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (title != null) {
            Text(
                text = title,
                color = LibraryColors.Primary,
                style = SectionTitleStyle.copy(fontSize = 20.sp),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(6.dp))
        }
        Text(
            text = text,
            color = LibraryColors.Secondary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = InterFontFamily,
            textAlign = TextAlign.Center,
        )
        if (action != null && onAction != null) {
            Spacer(Modifier.height(18.dp))
            LibraryManagePill(action, onAction)
        }
    }
}

/** The small centred pill at the bottom of a tab that opens its Manage sheet. */
@Composable
fun LibraryManagePill(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .clip(RoundedCornerShape(percent = 50))
                .background(if (TemaActual.oscuro) Color(0xFF2E2E31) else LectorColors.CardHigh)
                .clickable(role = Role.Button, enabled = enabled, onClick = onClick)
                .heightIn(min = 30.dp)
                .padding(horizontal = 18.dp, vertical = 6.dp),
        ) {
            Text(
                text = text,
                color = LibraryColors.Primary.copy(alpha = if (enabled) 1f else 0.5f),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = InterFontFamily,
            )
        }
    }
}

/**
 * GeneratedGroupHeader (GeneratedPlaylistKit.kt): la cabecera de cada día del historial.
 * Shiori añade el número de filas del día, en gris, y un fondo para que tape lo que pasa por debajo al ir pegada.
 */
@Composable
fun GeneratedGroupHeader(text: String, modifier: Modifier = Modifier, count: String? = null) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(LectorColors.Bg)
            .padding(start = SearchHorizontalPadding, end = SearchHorizontalPadding, top = 22.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = SectionTitleStyle,
            color = LibraryColors.Primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (count != null) {
            Spacer(Modifier.width(8.dp))
            Text(text = count, style = TrackSubtitleStyle.copy(fontSize = 12.sp), color = LibraryColors.Secondary, maxLines = 1)
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Filas (HomeTrackRow de HomeCards.kt)
// ---------------------------------------------------------------------------------------------

/**
 * the "list" rows: 54dp cover, two lines, no button. Shiori pone en [cover] su portada y en [trailing] lo que cada
 * pantalla necesite (continuar, descargar, quitar…). [selected] tiñe la fila como la luz de pulsación, pero fija.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeTrackRow(
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    selected: Float = 0f,
    dimmed: Boolean = false,
    titleMaxLines: Int = 1,
    subtitleColor: Color? = null,
    cover: @Composable () -> Unit,
    extra: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val acento = MaterialTheme.colorScheme.primary
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .heightIn(min = homeRowHeight())
            .then(
                if (selected > 0.01f) Modifier.background(acento.copy(alpha = 0.16f * selected), RoundedCornerShape(22.dp))
                else Modifier,
            )
            .combinedClickable(
                interactionSource = null,
                indication = TrackRowHighlight,
                onClick = onClick,
                onLongClick = onLongClick,
            ),
    ) {
        cover()
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 13.dp),
        ) {
            Text(
                text = title,
                style = HomeCardTitleStyle.copy(fontSize = 15.sp),
                color = if (dimmed) LibraryColors.Subtitle else LibraryColors.Title,
                maxLines = titleMaxLines,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrEmpty()) {
                Text(
                    text = subtitle,
                    style = HomeCardSubtitleStyle,
                    color = subtitleColor ?: LibraryColors.Subtitle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            extra?.invoke()
        }
        trailing?.invoke()
    }
}

/** El botón «more» de HomeTrackRow (un círculo de 24dp dentro de 38dp) con el icono que se le pase. */
@Composable
fun RowSmallAction(icon: ImageVector, contentDescription: String, onClick: () -> Unit, tint: Color? = null, fill: Color? = null) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(28.dp)
                .background(fill ?: LibraryColors.Primary.copy(alpha = 0.14f), CircleShape),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = tint ?: LibraryColors.Primary.copy(alpha = 0.85f),
                modifier = Modifier.size(17.dp),
            )
        }
    }
}
