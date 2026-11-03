// Copiado de Mochy Music (github.com/realidkroo/Mochy-Music, GPL-3.0): app/src/main/kotlin/com/mochyy/mochymusic/ui/screens/search/SearchDesign.kt
// SearchDesign.kt

package com.example.lector.ui.mochy.search

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.lector.ui.LectorColors
import com.example.lector.ui.mochy.AppMotion
import com.example.lector.ui.mochy.AppTypography
import com.example.lector.ui.mochy.BlurDirection
import com.example.lector.ui.mochy.FlatBarScrimAlpha
import com.example.lector.ui.mochy.GlassState
import com.example.lector.ui.mochy.ProgressiveGlassBackground
import com.example.lector.ui.mochy.RoundedHighlight
import com.example.lector.ui.mochy.rememberProgressiveBlur
import com.example.lector.ui.mochy.stableStatusBars

val SearchHorizontalPadding = 24.dp

val SearchFieldHeight = 48.dp

val AvatarSize = 36.dp

val SearchTitleBlockHeight = 62.dp

val SearchTopClearance = 18.dp

// De library/LibraryDesign.kt: el bloque del título con una línea de subtítulo debajo.
val LibraryTitleBlockHeight: Dp = SearchTitleBlockHeight + 16.dp

// Shiori: los mismos grises de Mochy en oscuro, pero siguiendo el tema (claro/negro puro) a través de LectorColors.
object SearchColors {
    val Field get() = LectorColors.Card
    val FieldFocused get() = LectorColors.CardHigh
    val Tile get() = LectorColors.Card
    val TileHigh get() = LectorColors.CardHigh
    val Divider get() = LectorColors.Linea
    val Primary get() = LectorColors.Text
    val Secondary get() = LectorColors.Muted
    val Placeholder get() = LectorColors.Pista

    @Suppress("UNUSED_PARAMETER")
    fun page(pureBlack: Boolean): Color = LectorColors.Bg
}

val SearchCollapseEasing = CubicBezierEasing(0.57f, 0.53f, 0f, 1f)

const val SearchCollapseDurationMs = 900

/**
 * Shiori: el mismo «latch-and-settle» de rememberCollapseProgress, pero leyendo el desplazamiento por nested scroll,
 * para páginas cuyas listas viven dentro de sub-pestañas (cada una con su propio estado de lista). [clave] lo reinicia
 * (p. ej. al cambiar de sub-pestaña, que empieza arriba).
 */
class NestedCollapse internal constructor(private val enterPx: Float, private val exitPx: Float) : NestedScrollConnection {
    private var offset by mutableFloatStateOf(0f)
    private var latch = false
    val collapsed by derivedStateOf {
        val next = when {
            offset > enterPx -> true
            offset < exitPx -> false
            else -> latch
        }
        latch = next
        next
    }

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        if (consumed.y != 0f) offset = (offset - consumed.y).coerceAtLeast(0f)
        // tope de la lista: lo que sobra hacia abajo significa que ya está arriba del todo
        if (available.y > 0f && consumed.y == 0f) offset = 0f
        return Offset.Zero
    }
}

@Composable
fun rememberNestedCollapseProgress(clave: Any?): Pair<NestedCollapse, State<Float>> {
    val density = LocalDensity.current
    val enterPx = with(density) { 24.dp.toPx() }
    val exitPx = with(density) { 6.dp.toPx() }
    val conexion = remember(clave, enterPx, exitPx) { NestedCollapse(enterPx, exitPx) }
    val progreso = animateFloatAsState(
        targetValue = if (conexion.collapsed) 1f else 0f,
        animationSpec = tween(
            durationMillis = SearchCollapseDurationMs,
            easing = SearchCollapseEasing,
        ),
        label = "searchCollapse",
    )
    return conexion to progreso
}

@Composable
fun searchTopBarHeight(
    withTitle: Boolean = true,
    extra: Dp = 0.dp,
    titleBlockHeight: Dp = SearchTitleBlockHeight,
): Dp {
    val statusBar = WindowInsets.stableStatusBars.asPaddingValues().calculateTopPadding()
    return statusBar + SearchTopClearance +
        (if (withTitle) titleBlockHeight else 0.dp) +
        SearchFieldHeight + extra + 32.dp
}

@Composable
fun SearchGlassTopBar(
    glassState: GlassState,
    progressProvider: () -> Float,
    pureBlack: Boolean,
    title: String?,

    blurActive: Boolean = true,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,

    /** Small line under the title, e.g. "All songs on your library listed here." */
    subtitle: String? = null,

    /**
     * Overrides what renders inside the title block (e.g. a logo mark) while keeping the same
     * collapse fade/scale/translate/blur the plain text title gets. [title] must still be
     * non-null to reserve the block's height; [subtitle] is ignored when this is set.
     */
    titleContent: (@Composable () -> Unit)? = null,

    /**
     * Height of the title block. Defaults to the Search tab's title-only height; the Library
     * screens pass a taller value because their block carries a subtitle line as well. This is the
     * distance the block travels as it collapses, so it has to match what is actually rendered or
     * the search field lands in the wrong place at rest.
     */
    titleBlockHeight: Dp = SearchTitleBlockHeight,

    below: (@Composable () -> Unit)? = null,
    field: @Composable () -> Unit,
) {
    val statusBar = WindowInsets.stableStatusBars.asPaddingValues().calculateTopPadding()
    val pageColor = SearchColors.page(pureBlack)
    val density = LocalDensity.current
    val titleTravelPx = with(density) { titleBlockHeight.toPx() }

    // Shiori: el hueco que deja el campo se mide de lo que hay en [trailing] (Mochy reservaba el de un avatar fijo;
    // aquí pueden ser varios botones). Con un solo avatar de 36 dp da lo mismo que en Mochy.
    var trailingWidthPx by remember { mutableIntStateOf(with(density) { AvatarSize.roundToPx() }) }
    val gapPx = with(density) { 12.dp.toPx() }
    val avatarDropPx = with(density) { (SearchFieldHeight / 2 - titleBlockHeight / 2).toPx() }

    val blurCache = remember { mutableMapOf<Int, androidx.compose.ui.graphics.RenderEffect>() }

    Box(modifier = modifier.fillMaxWidth()) {

        val showGlass by remember { derivedStateOf { progressProvider() > 0.01f } }
        if (showGlass && blurActive) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer { alpha = progressProvider().coerceIn(0f, 1f) }
            ) {
                ProgressiveGlassBackground(
                    state = glassState,
                    maxBlurRadius = { 42f * progressProvider().coerceIn(0f, 1f) },
                    foundationColor = pageColor,
                    direction = BlurDirection.BottomToTop,

                    steps = 2,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        // a flat bar only when there's glass under it, without blur the fade is all that keeps the title readable
        val flatBar = blurActive && !rememberProgressiveBlur()
        if (showGlass) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .drawWithCache {
                        onDrawBehind {
                            val p = progressProvider().coerceIn(0f, 1f)
                            if (flatBar) {
                                drawRect(pageColor.copy(alpha = p * FlatBarScrimAlpha))
                                return@onDrawBehind
                            }

                            val boost = if (blurActive) 1f else 1.12f
                            drawRect(
                                brush = Brush.verticalGradient(
                                    0f to pageColor.copy(alpha = (p * 0.95f * boost).coerceAtMost(1f)),
                                    0.45f to pageColor.copy(alpha = (p * 0.72f * boost).coerceAtMost(1f)),
                                    0.78f to pageColor.copy(alpha = (p * 0.34f * boost).coerceAtMost(1f)),
                                    1f to Color.Transparent,
                                )
                            )
                        }
                    }
            )
        }

        Column(modifier = Modifier.padding(top = statusBar + SearchTopClearance)) {
            if (title != null) {

                Box(
                    modifier = Modifier
                        .height(titleBlockHeight)
                        .fillMaxWidth()
                        .padding(horizontal = SearchHorizontalPadding)
                        .graphicsLayer {
                            val p = progressProvider().coerceIn(0f, 1f)
                            alpha = (1f - p * 1.6f).coerceIn(0f, 1f)
                            translationY = -p * titleTravelPx
                            val scale = 1f - p * 0.18f
                            scaleX = scale
                            scaleY = scale
                            transformOrigin = TransformOrigin(0f, 0.5f)
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                                val quantised = ((p * 14f) / 3f).toInt() * 3
                                renderEffect = if (quantised > 0 && AppMotion.elementBlur) {
                                    blurCache.getOrPut(quantised) {
                                        android.graphics.RenderEffect.createBlurEffect(
                                            quantised.toFloat(),
                                            quantised.toFloat(),
                                            android.graphics.Shader.TileMode.CLAMP,
                                        ).asComposeRenderEffect()
                                    }
                                } else {
                                    null
                                }
                            }
                        }
                        // Shiori: que el subtítulo no pase por debajo de los botones de la derecha
                        .then(
                            if (trailing != null) {
                                Modifier.padding(end = with(density) { (trailingWidthPx + gapPx).toDp() })
                            } else {
                                Modifier
                            }
                        ),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (titleContent != null) {
                        titleContent()
                    } else {
                        Column(verticalArrangement = Arrangement.Center) {
                            Text(
                                text = title,
                                style = AppTypography.displaySmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 34.sp,
                                ),
                                color = SearchColors.Primary,
                                maxLines = 1,
                            )
                            if (subtitle != null) {
                                Text(
                                    text = subtitle,
                                    style = AppTypography.bodySmall.copy(fontSize = 12.sp),
                                    color = SearchColors.Secondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        if (title != null) {
                            translationY = -progressProvider().coerceIn(0f, 1f) * titleTravelPx
                        }
                    },
            ) {
              Box(modifier = Modifier.fillMaxWidth().padding(horizontal = SearchHorizontalPadding)) {
                Box(
                    modifier = if (trailing == null) {
                        Modifier.fillMaxWidth()
                    } else {
                        Modifier.layout { measurable, constraints ->

                            val avatarReservePx = trailingWidthPx + gapPx
                            val reserve = if (title == null) {
                                avatarReservePx
                            } else {
                                avatarReservePx * progressProvider().coerceIn(0f, 1f)
                            }
                            val width = (constraints.maxWidth - reserve.toInt()).coerceAtLeast(0)
                            val placeable = measurable.measure(
                                constraints.copy(minWidth = width, maxWidth = width)
                            )
                            layout(constraints.maxWidth, placeable.height) { placeable.place(0, 0) }
                        }
                    },
                ) {
                    field()
                }
              }
              if (below != null) {
                Spacer(modifier = Modifier.height(16.dp))
                below()
              }
            }
        }

        if (trailing != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = statusBar + SearchTopClearance, end = SearchHorizontalPadding)
                    // As tall as this page's title block (taller on Library pages with a subtitle),
                    // so the drop below lands its centre on the search field's centre.
                    .height(if (title == null) SearchFieldHeight else titleBlockHeight)
                    .graphicsLayer {
                        if (title != null) {
                            translationY = progressProvider().coerceIn(0f, 1f) * avatarDropPx
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.onSizeChanged { trailingWidthPx = it.width }) {
                    trailing()
                }
            }
        }
    }
}

/** Shiori: el círculo de [SearchAvatar] con un icono (el hueco del avatar de Mochy, para una acción). */
@Composable
fun SearchAvatarButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = AvatarSize,
    selected: Boolean = false,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(if (selected) SearchColors.Primary else SearchColors.TileHigh)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (selected) SearchColors.page(false) else SearchColors.Secondary,
            modifier = Modifier.size(size * 0.5f),
        )
    }
}

@Composable
fun SearchField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    onSearch: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    focused: Boolean = false,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    readOnlyClick: (() -> Unit)? = null,

    focusRequester: FocusRequester? = null,
    onFocusChanged: ((Boolean) -> Unit)? = null,
) {
    // Shiori: sin el reproductor de Mochy, no hace falta soltar el foco cuando se abre su hoja.
    var fieldHasFocus by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(SearchFieldHeight)
            .clip(RoundedCornerShape(50))
            .background(if (focused || fieldHasFocus) SearchColors.FieldFocused else SearchColors.Field)
            .then(
                if (readOnlyClick != null) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = readOnlyClick,
                    )
                } else {
                    Modifier
                }
            )
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            Spacer(modifier = Modifier.width(10.dp))
        }
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.text.isEmpty()) {
                Text(
                    text = placeholder,
                    style = AppTypography.bodyMedium.copy(fontSize = 15.sp),
                    color = SearchColors.Placeholder,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (enabled) {
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    textStyle = LocalTextStyle.current.merge(
                        TextStyle(color = SearchColors.Primary, fontSize = 15.sp, fontFamily = AppTypography.bodyMedium.fontFamily)
                    ),
                    cursorBrush = SolidColor(SearchColors.Primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onSearch(value.text) }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (focusRequester != null) {
                                Modifier.focusRequester(focusRequester)
                            } else {
                                Modifier
                            }
                        )
                        .onFocusChanged {
                            fieldHasFocus = it.isFocused
                            onFocusChanged?.invoke(it.isFocused)
                        },
                )
            }
        }
        if (trailing != null) {
            Spacer(modifier = Modifier.width(10.dp))
            trailing()
        }
    }
}

/** Shiori: el icono de 20 dp de los bordes del campo (flecha atrás, borrar, lupa), tocable sin onda, como en Mochy. */
@Composable
fun SearchFieldGlyph(icon: ImageVector, contentDescription: String?, onClick: (() -> Unit)? = null) {
    Icon(
        imageVector = icon,
        contentDescription = contentDescription,
        tint = SearchColors.Secondary,
        modifier = Modifier
            .size(20.dp)
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onClick,
                    )
                } else {
                    Modifier
                }
            ),
    )
}

@Composable
fun SearchSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    large: Boolean = true,
    ruleAbove: Boolean = true,
    ruleBelow: Boolean = false,
    // Shiori: recuento o acción al final de la cabecera
    trailing: (@Composable () -> Unit)? = null,
) {
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = SearchHorizontalPadding)) {
        if (ruleAbove) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(SearchColors.Divider)
            )
            Spacer(modifier = Modifier.height(18.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                style = AppTypography.titleLarge.copy(
                    fontSize = if (large) 22.sp else 18.sp,
                    fontWeight = FontWeight.Bold,
                ),
                color = SearchColors.Primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (trailing != null) {
                Spacer(modifier = Modifier.width(10.dp))
                trailing()
            }
        }
        Spacer(modifier = Modifier.height(if (ruleBelow) 12.dp else 16.dp))
        if (ruleBelow) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(SearchColors.Divider)
            )
            Spacer(modifier = Modifier.height(14.dp))
        }
    }
}

/** Shiori: el recuento gris que va junto a una [SearchSectionHeader]. */
@Composable
fun SearchCount(count: Int) {
    Text(
        text = "$count",
        style = AppTypography.titleLarge.copy(fontSize = 18.sp, fontWeight = FontWeight.Medium),
        color = SearchColors.Secondary,
        maxLines = 1,
    )
}

@Composable
fun SearchCategoryRow(
    categories: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = SearchHorizontalPadding),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(categories) { index, label ->
            SearchCategoryChip(label, index == selectedIndex) { onSelect(index) }
        }
    }
}

/** Una píldora de [SearchCategoryRow]; Shiori la usa también suelta para filtros que se encienden y apagan. */
@Composable
fun SearchCategoryChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .height(34.dp)
            .clip(RoundedCornerShape(50))
            .background(if (selected) SearchColors.Primary else SearchColors.Tile)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = AppTypography.labelLarge.copy(
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            ),
            color = if (selected) SearchColors.page(false) else SearchColors.Primary,
            maxLines = 1,
        )
    }
}

@Composable
fun SearchSimpleRow(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    pill: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = SearchHorizontalPadding, vertical = 4.dp)
            .clip(RoundedCornerShape(if (pill) 14.dp else 0.dp))
            .then(if (pill) Modifier.background(SearchColors.Tile) else Modifier)
            .clickable(onClick = onClick)
            .padding(horizontal = if (pill) 16.dp else 0.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingIcon != null) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                tint = SearchColors.Secondary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(14.dp))
        }
        Text(
            text = text,
            style = AppTypography.bodyLarge.copy(fontSize = 15.sp),
            color = SearchColors.Primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (trailing != null) {
            Spacer(modifier = Modifier.width(12.dp))
            trailing()
        }
    }
}

@Composable
fun SearchRule(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = SearchHorizontalPadding)
            .height(1.dp)
            .background(SearchColors.Divider)
    )
}

@Composable
fun SearchLoadingDots(modifier: Modifier = Modifier, color: Color = SearchColors.Primary) {
    val transition = androidx.compose.animation.core.rememberInfiniteTransition(label = "searchLoading")
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(3) { index ->
            val phase by transition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                    animation = androidx.compose.animation.core.tween(
                        durationMillis = 900,
                        delayMillis = index * 150,
                        easing = androidx.compose.animation.core.CubicBezierEasing(0.4f, 0f, 0.2f, 1f),
                    ),
                    repeatMode = androidx.compose.animation.core.RepeatMode.Reverse,
                ),
                label = "searchLoadingDot$index",
            )
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .graphicsLayer {
                        alpha = 0.3f + 0.7f * phase
                        val s = 0.75f + 0.25f * phase
                        scaleX = s
                        scaleY = s
                    }
                    .clip(CircleShape)
                    .background(color)
            )
        }
    }
}

fun Modifier.searchCardBorder(radius: Dp = 16.dp): Modifier = this.border(
    width = androidx.compose.ui.unit.Dp.Hairline,
    color = SearchColors.Primary.copy(alpha = 0.05f),
    shape = RoundedCornerShape(radius),
)

// De OnlineSearchResult.kt: pressing a result lights a pill from one side of the screen to the other
val ResultRowHighlight: RoundedHighlight
    @Composable get() {
        val color = LectorColors.Resalte
        return remember(color) { RoundedHighlight(cornerRadius = 100.dp, spillX = (-10).dp, color = color) }
    }
