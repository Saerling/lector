// Copiado de Mochy Music (github.com/realidkroo/Mochy-Music, GPL-3.0): ui/component/SubSettingsScaffold.kt
// Shiori: los colores siguen al tema (en oscuro son los de Mochy), los iconos de res/ son ImageVector, los textos
// están en español, el margen lateral del contenido es configurable (las páginas de Shiori ponen el suyo), el hueco
// de abajo es el de la barra de navegación y, con movimiento reducido, el título no se desenfoca.

package com.example.lector.ui.mochy

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.lerp as lerpDp
import androidx.compose.ui.util.lerp
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.ui.unit.Dp
import com.example.lector.ui.LectorColors
import com.example.lector.ui.Mov
import kotlin.math.PI
import kotlin.math.sin

private val PagePadding = 20.dp
private val BackButtonSize = 40.dp

private val HeaderTopPadding = 16.dp

private val BackButtonCenterY = HeaderTopPadding + BackButtonSize / 2

private val ExpandedTitleTop = HeaderTopPadding + BackButtonSize + 12.dp

private val CollapsedTitleX = BackButtonSize + 14.dp

private val ExpandedHeaderHeight = 118.dp

private const val CollapsedTitleScale = 0.62f

private const val TitleMorphMaxBlurPx = 26f

private val OverlayEasing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)

private val SearchBarHeight = 52.dp

@Composable
fun SubSettingsScaffold(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    searchBar: (@Composable () -> Unit)? = null,
    /** the page this one sits under, written beside the back button ("‹ Audio Lab") */
    backLabel: String? = null,
    /** Shiori: el margen lateral del contenido; 0 cuando cada fila pone el suyo */
    contentPadding: Dp = PagePadding,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scrollState = rememberScrollState()
    val glassState = remember { GlassState() }

    val headerFoundationColor = LectorColors.Bg

    // Latch-and-settle collapse, same as Library/Search: once the threshold is crossed the morph
    // always finishes smoothly, even after the user's finger lifts mid-scroll.
    val collapseProgress by rememberScrollCollapseProgress(scrollState)

    var titleHeightPx by remember { mutableIntStateOf(0) }

    val bottomInset = WindowInsets.navigationBars.asPaddingValues()

    val statusBarTop = WindowInsets.stableStatusBars.asPaddingValues().calculateTopPadding()
    val totalHeaderHeight = statusBarTop + ExpandedHeaderHeight

    var searchOverlayVisible by remember { mutableStateOf(false) }

    // The search pill is a single element that actually resizes (real layout constraints, not a
    // scaled graphicsLayer) and slides from its resting spot below the header to the icon slot at
    // top-right, so it reads as one shape warping into a circle rather than one thing fading out
    // while an unrelated icon fades in elsewhere. The two rects are plain layout constants - no
    // live position measurement feeding back into the content a RenderNode is capturing.
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val expandedBarX = PagePadding
    val expandedBarY = totalHeaderHeight
    val expandedBarWidth = screenWidth - PagePadding * 2
    val collapsedBarX = screenWidth - PagePadding - BackButtonSize
    val collapsedBarY = statusBarTop + HeaderTopPadding

    LaunchedEffect(collapseProgress) {
        if (collapseProgress < 0.5f) searchOverlayVisible = false
    }

    Box(modifier = modifier.fillMaxSize().background(LectorColors.Bg)) {
        Column(
            modifier = Modifier
                .fillMaxSize()

                .glassRoot(glassState, isActive = { !scrollState.isScrollInProgress })
                .verticalScroll(scrollState)
                .padding(horizontal = contentPadding)
        ) {
            Spacer(modifier = Modifier.height(totalHeaderHeight))

            if (searchBar != null) {
                // Reserves the pill's resting space in the normal flow; the pill itself is drawn
                // as an overlay below so it can slide out over the header instead of being
                // clipped by the scrolling column.
                Spacer(modifier = Modifier.height(SearchBarHeight + 16.dp))
            }

            content()

            Spacer(modifier = Modifier.height(bottomInset.calculateBottomPadding() + 24.dp))
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(totalHeaderHeight)
                .align(Alignment.TopCenter)
                .graphicsLayer { alpha = collapseProgress }
        ) {
            ProgressiveGlassBackground(
                state = glassState,
                maxBlurRadius = { 40f * collapseProgress },
                foundationColor = headerFoundationColor.copy(alpha = 0.55f),
                direction = BlurDirection.BottomToTop,
                steps = 2,
                modifier = Modifier.fillMaxSize()
            )

            val progressive = rememberProgressiveBlur()
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .drawWithCache {
                        val gradient = if (progressive) {
                            Brush.verticalGradient(
                                0f to headerFoundationColor.copy(alpha = 0.90f),
                                0.20f to headerFoundationColor.copy(alpha = 0.50f),
                                0.45f to headerFoundationColor.copy(alpha = 0.18f),
                                0.75f to Color.Transparent,
                            )
                        } else {
                            SolidColor(headerFoundationColor.copy(alpha = FlatBarScrimAlpha))
                        }
                        onDrawBehind { drawRect(brush = gradient) }
                    }
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(totalHeaderHeight)
                .align(Alignment.TopCenter)
                .padding(top = statusBarTop, start = PagePadding, end = PagePadding)
        ) {
            SubSettingsBackButton(onBack, Modifier.padding(top = HeaderTopPadding))

            if (backLabel != null) {
                // gives way to the title, which slides into this spot as the page collapses
                Text(
                    text = backLabel,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = LectorColors.Text,
                    maxLines = 1,
                    modifier = Modifier
                        .padding(top = HeaderTopPadding, start = BackButtonSize + 10.dp)
                        .height(BackButtonSize)
                        .wrapContentHeight(Alignment.CenterVertically)
                        .graphicsLayer { alpha = (1f - collapseProgress * 2.5f).coerceIn(0f, 1f) }
                        .clickable(onClick = onBack)
                )
            }

            Text(
                text = title,
                style = AppTypography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = LectorColors.Text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .onSizeChanged { titleHeightPx = it.height }
                    .graphicsLayer {

                        val p = collapseProgress

                        transformOrigin = TransformOrigin(0f, 0f)
                        val scale = lerp(1f, CollapsedTitleScale, p)
                        scaleX = scale
                        scaleY = scale

                        val collapsedTop =
                            BackButtonCenterY.toPx() - (titleHeightPx * CollapsedTitleScale) / 2f
                        translationX = lerp(0f, CollapsedTitleX.toPx(), p)
                        translationY = lerp(ExpandedTitleTop.toPx(), collapsedTop, p)

                        val blurPx = sin(p.coerceIn(0f, 1f) * PI.toFloat()) * TitleMorphMaxBlurPx
                        // DECAL is API 31. The argument is read before BlurEffectCache.get can
                        // return early, so naming it outside this check threw NoSuchFieldError on
                        // Android 11 and older the first time the header drew: every settings
                        // page crashed on opening. Below 31 there's no blur either way.
                        renderEffect = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S && !Mov.reducido && blurPx > 0.5f) {
                            BlurEffectCache.element(blurPx, android.graphics.Shader.TileMode.DECAL)
                        } else {
                            null
                        }
                    }
            )
        }

        if (searchBar != null) {
            val p = collapseProgress
            val pillX = lerpDp(expandedBarX, collapsedBarX, p)
            val pillY = lerpDp(expandedBarY, collapsedBarY, p)
            val pillWidth = lerpDp(expandedBarWidth, BackButtonSize, p)
            val pillHeight = lerpDp(SearchBarHeight, BackButtonSize, p)

            // One element whose actual layout size and position are animated (a true warp, not a
            // scaled copy): the real search field fades out as it shrinks, an icon fades in on top
            // of it, and because both share the same shrinking frame they read as one shape
            // deforming into a circle rather than a new element appearing elsewhere.
            Box(
                modifier = Modifier
                    .offset(x = pillX, y = pillY)
                    .size(width = pillWidth, height = pillHeight)
                    .clip(RoundedCornerShape(lerpDp(26.dp, BackButtonSize / 2, p)))
                    .background(LectorColors.PillFill)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = (1f - p / 0.6f).coerceIn(0f, 1f) }
                ) {
                    searchBar()
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = ((p - 0.45f) / 0.55f).coerceIn(0f, 1f) }
                        .clip(CircleShape)
                        .clickable(enabled = p > 0.6f) {
                            searchOverlayVisible = !searchOverlayVisible
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (searchOverlayVisible) Icons.Rounded.Close else Icons.Rounded.Search,
                        contentDescription = if (searchOverlayVisible) "Ocultar búsqueda" else "Buscar",
                        tint = LectorColors.Text,
                        modifier = Modifier.size(if (searchOverlayVisible) 18.dp else 16.dp)
                    )
                }
            }

            BackHandler(enabled = searchOverlayVisible) { searchOverlayVisible = false }

            AnimatedVisibility(
                visible = searchOverlayVisible,
                enter = fadeIn(tween(260, easing = OverlayEasing)) +
                    slideInVertically(tween(260, easing = OverlayEasing)) { -it / 2 },
                exit = fadeOut(tween(180)) + slideOutVertically(tween(180)) { -it / 2 },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(
                        top = statusBarTop + HeaderTopPadding + BackButtonSize + 10.dp,
                        start = PagePadding,
                        end = PagePadding,
                    ),
            ) {
                searchBar()
            }
        }
    }
}

@Composable
fun SubSettingsSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Buscar en ajustes",
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(26.dp))
            .background(SearchColors.Field)
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            androidx.compose.foundation.text.BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(
                    fontFamily = InterFontFamily,
                    fontSize = 16.sp,
                    color = LectorColors.Text,
                    fontWeight = FontWeight.Normal
                ),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(LectorColors.Text),
                modifier = Modifier.weight(1f),
                decorationBox = { innerTextField ->
                    if (query.isEmpty()) {
                        Text(
                            text = placeholder,
                            fontFamily = InterFontFamily,
                            fontSize = 16.sp,
                            color = LectorColors.Pista
                        )
                    }
                    innerTextField()
                }
            )

            if (query.isNotEmpty()) {
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.size(8.dp))
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Borrar búsqueda",
                    tint = LectorColors.Text.copy(alpha = 0.7f),
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .clickable { onQueryChange("") }
                )
            }
        }
    }
}

/** the 40dp round grey back button of the sub-settings header */
@Composable
fun SubSettingsBackButton(onBack: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(BackButtonSize)
            .clip(CircleShape)
            .background(LectorColors.PillFill)
            .clickable(onClick = onBack),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = ArrowBackIos,
            contentDescription = "Atrás",
            tint = LectorColors.Text,
            modifier = Modifier.size(18.dp)
        )
    }
}
