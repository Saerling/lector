// Copiado de Mochy Music (github.com/realidkroo/Mochy-Music, GPL-3.0): app/src/main/kotlin/com/mochyy/mochymusic/ui/screens/artist/ArtistDesign.kt
// ArtistDesign.kt
//
// The artist page's own pieces: the banner (with Home's blur bands, riding up and zooming like a
// playlist cover), the round photo and name, the Play / like / Subscribe row, the card, and the top bar.
// En Shiori: la página de una fuente y el detalle de una extensión (el icono hace de foto y de banner).

package com.example.lector.ui.mochy.artist

import android.graphics.Shader
import android.os.Build
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.example.lector.ui.LectorColors
import com.example.lector.ui.Mov
import com.example.lector.ui.TemaActual
import com.example.lector.ui.mochy.BlurDirection
import com.example.lector.ui.mochy.BlurEffectCache
import com.example.lector.ui.mochy.BlurQuality
import com.example.lector.ui.mochy.DownscaledBlur
import com.example.lector.ui.mochy.GlassState
import com.example.lector.ui.mochy.InterFontFamily
import com.example.lector.ui.mochy.LocalBlurQuality
import com.example.lector.ui.mochy.ProgressiveGlassBackground
import com.example.lector.ui.mochy.damageBoundary
import com.example.lector.ui.mochy.glassRoot
import com.example.lector.ui.mochy.pressBounce
import com.example.lector.ui.mochy.stableStatusBars

// geometry and type from the Figma playlist frames (PlaylistDetail.kt). internal so
// the album, artist and generated pages line up on the same inset and type
internal val PlaylistInset = 36.dp
internal val PlaylistTracking = (-0.06).em
// Mochy: blanco al 64 % con el glifo #2F2F2F; en el tema claro de Shiori, al revés (casi negro con glifo blanco).
internal val PillContent: Color get() = if (TemaActual.oscuro) Color(0xFF2F2F2F) else Color.White
internal val PillFill: Color get() = if (TemaActual.oscuro) Color.White.copy(alpha = 0.64f) else Color(0xFF111114).copy(alpha = 0.86f)
internal val TrackShape = RoundedCornerShape(9.dp)

internal val PlaylistStatsStyle = TextStyle(
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

// the round photo sits this far down the banner, and the page's header starts there
internal const val ArtistAvatarAt = 0.45f
internal val ArtistAvatarSize = 96.dp

internal val ArtistNameStyle = TextStyle(
    fontFamily = InterFontFamily,
    fontWeight = FontWeight.Bold,
    fontSize = 36.sp,
    lineHeight = 40.sp,
    letterSpacing = PlaylistTracking,
)

private val ArtistBarTitleStyle = TextStyle(
    fontFamily = InterFontFamily,
    fontWeight = FontWeight.Bold,
    fontSize = 19.sp,
    lineHeight = 22.sp,
    letterSpacing = (-0.04).em,
)

internal val CardLabelStyle = TextStyle(
    fontFamily = InterFontFamily,
    fontWeight = FontWeight.SemiBold,
    fontSize = 13.sp,
    lineHeight = 16.sp,
    letterSpacing = (-0.02).em,
)

internal val AboutBodyStyle = TextStyle(
    fontFamily = InterFontFamily,
    fontWeight = FontWeight.Medium,
    fontSize = 13.sp,
    lineHeight = 18.sp,
    letterSpacing = (-0.01).em,
)

internal val ArtistCardShape = RoundedCornerShape(22.dp)

/** Mochy: playlistCoverHeight() = 60 % de la pantalla; una fuente tiene más cabecera (chips, búsqueda), así que menos. */
@Composable
internal fun artistBannerHeight(fraction: Float = 0.42f): Dp = (LocalConfiguration.current.screenHeightDp * fraction).dp

/**
 * the banner pinned behind the list. the media is recorded once and drawn again, blurred, into a
 * band at the top and one at the bottom, as Home's hero does, and the whole frame rides up, zooms
 * and softens away with the scroll like a playlist's cover.
 * En Shiori el «medio» es [media] (el icono de la extensión ampliado y desenfocado).
 */
@Composable
internal fun ArtistBanner(
    edgeBlur: Boolean,
    height: Dp,
    scrollProgress: () -> Float,
    background: State<Color>,
    media: @Composable BoxScope.() -> Unit,
) {
    val mediaLayer = rememberGraphicsLayer()
    ScrollAwayCover(
        progress = scrollProgress,
        follow = -0.8f,
        softFrom = 0.02f,
        modifier = Modifier
            .fillMaxWidth()
            .height(height),
    ) {
        // bounded, only the banner repaints
        Box(modifier = Modifier.fillMaxSize().damageBoundary()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (edgeBlur) {
                            Modifier.drawWithContent {
                                mediaLayer.record { this@drawWithContent.drawContent() }
                                drawLayer(mediaLayer)
                            }
                        } else {
                            Modifier
                        }
                    ),
                content = media,
            )
            if (edgeBlur) {
                BannerBlurBand(mediaLayer, atTop = true, heightFraction = 0.4f, radius = 22f)
                BannerBlurBand(mediaLayer, atTop = false, heightFraction = 0.55f, radius = 30f)
            }
            BannerScrims(background)
        }
    }
}

@Composable
private fun BoxScope.BannerBlurBand(
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

/** a little dim for the text, darker under the status bar, and the foot sinking into the page */
@Composable
private fun BoxScope.BannerScrims(background: State<Color>) {
    val dark = TemaActual.oscuro
    Box(
        modifier = Modifier
            .matchParentSize()
            .drawWithCache {
                val h = size.height
                val top = Brush.verticalGradient(
                    0f to (if (dark) Color.Black else background.value).copy(alpha = 0.5f),
                    1f to Color.Transparent,
                    endY = h * 0.3f,
                )
                onDrawBehind {
                    val bg = background.value
                    if (dark) drawRect(Color.Black.copy(alpha = 0.14f))
                    drawRect(top, size = Size(size.width, h * 0.3f))
                    drawRect(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.5f to bg.copy(alpha = 0.55f),
                            0.82f to bg.copy(alpha = 0.92f),
                            1f to bg,
                            startY = h * 0.42f,
                            endY = h,
                        ),
                        topLeft = Offset(0f, h * 0.42f),
                        size = Size(size.width, h * 0.58f),
                    )
                }
            }
    )
}

/** the photo, a bone until it loads. En Shiori: el icono de la extensión, en squircle en vez de círculo. */
@Composable
internal fun ArtistAvatar(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier = modifier
            .size(ArtistAvatarSize)
            .clip(com.example.lector.ui.SuaveShape(26.dp))
            .background(com.example.lector.ui.mochy.BoneColor),
        content = content,
    )
}

/** the artist's name (Shiori no tiene logotipos de fuente: solo el nombre) */
@Composable
internal fun ArtistName(name: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier.animateContentSize(spring(dampingRatio = 0.9f, stiffness = 380f))) {
        Text(
            text = name,
            style = ArtistNameStyle,
            color = LectorColors.Text,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** "Español · 120 títulos": la línea gris bajo el nombre */
@Composable
internal fun ArtistStats(text: String, modifier: Modifier = Modifier) {
    if (text.isEmpty()) return
    Text(
        text = text,
        style = PlaylistStatsStyle,
        color = LectorColors.Text.copy(alpha = 0.7f),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
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

/** the Play pill (Shiori: cualquier acción principal con etiqueta) — it eases to the new label's width */
@Composable
internal fun PlayPill(label: String, icon: ImageVector, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .pressBounce(interaction)
            .height(33.dp)
            .clip(RoundedCornerShape(50))
            .background(PillFill)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .animateContentSize(spring(dampingRatio = 0.7f, stiffness = 500f))
            .padding(start = 17.dp, end = 17.dp)
    ) {
        AnimatedContent(targetState = icon, transitionSpec = { glyphSwap() }, label = "playPillGlyph") { ic ->
            Icon(ic, contentDescription = null, tint = PillContent, modifier = Modifier.size(17.dp))
        }
        Spacer(Modifier.width(7.dp))
        AnimatedContent(targetState = label, transitionSpec = { labelSwap() }, label = "playPillLabel") { l ->
            Text(text = l, style = PillLabelStyle, color = PillContent, maxLines = 1)
        }
    }
}

/** the round buttons squish when pressed, and a changed icon pops in */
@Composable
internal fun CircleAction(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    active: Boolean = false,
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .pressBounce(interaction)
            .size(33.dp)
            .clip(CircleShape)
            .background(if (active) androidx.compose.material3.MaterialTheme.colorScheme.primary else PillFill)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
    ) {
        AnimatedContent(targetState = icon, transitionSpec = { glyphSwap() }, label = "circleActionIcon") { ic ->
            Icon(
                ic,
                contentDescription = contentDescription,
                tint = if (active) androidx.compose.material3.MaterialTheme.colorScheme.onPrimary else PillContent,
                modifier = Modifier.size(17.dp),
            )
        }
    }
}

/** la fila de acciones bajo el nombre (Play / like / Subscribe en Mochy), con los huecos de Mochy */
@Composable
internal fun ArtistActions(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(9.dp),
        modifier = modifier,
        content = content,
    )
}

/** the release card: a glassy 22dp card with a hairline. En Shiori: avisos (actualización, error, problema). */
@Composable
internal fun ArtistCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    tint: Color = LectorColors.Text,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(ArtistCardShape)
            .background(tint.copy(alpha = 0.08f))
            .border(1.dp, tint.copy(alpha = 0.13f), ArtistCardShape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(start = 16.dp, end = 14.dp, top = 12.dp, bottom = 12.dp),
        content = content,
    )
}

/** "Top songs >": the line opens the shelf's own page */
@Composable
internal fun ArtistShelfTitle(
    title: String,
    modifier: Modifier = Modifier,
    count: Int? = null,
    onClick: (() -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.padding(start = PlaylistInset - 4.dp, end = PlaylistInset),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .clickable(enabled = onClick != null) { onClick?.invoke() }
                .padding(horizontal = 4.dp, vertical = 4.dp)
        ) {
            Text(text = title, style = SectionTitleStyle, color = LectorColors.Text, maxLines = 1)
            if (count != null) {
                Spacer(Modifier.width(7.dp))
                Text(text = "$count", style = SectionTitleStyle, color = LectorColors.Text.copy(alpha = 0.45f), maxLines = 1)
            }
            if (onClick != null) {
                Spacer(Modifier.width(6.dp))
                Icon(
                    Icons.Rounded.KeyboardArrowRight,
                    contentDescription = null,
                    tint = LectorColors.Text.copy(alpha = 0.6f),
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

/** fila de dato de la tarjeta de información: etiqueta gris, valor en TrackTitleStyle */
@Composable
internal fun ArtistInfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.Top) {
        Text(label, style = TrackSubtitleStyle, color = LectorColors.Text.copy(alpha = 0.6f), modifier = Modifier.width(88.dp).padding(top = 2.dp))
        Text(value, style = TrackTitleStyle, color = LectorColors.Text, modifier = Modifier.weight(1f), maxLines = 3, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * the bar over the page. at rest it's the back button on the banner; once the name has gone
 * under it, it frosts over and the name settles in beside the back button.
 * Shiori: [actions] ocupa el sitio del avatar del perfil.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ArtistTopBar(
    glassState: GlassState,
    morph: () -> Float,
    title: String,
    background: State<Color>,
    onBackClick: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val density = LocalDensity.current
    val showChrome by remember { derivedStateOf { morph() > 0.01f } }
    val statusBarTop = WindowInsets.stableStatusBars.asPaddingValues().calculateTopPadding()

    Box(modifier = Modifier.fillMaxWidth()) {
        if (showChrome) {
            ProgressiveGlassBackground(
                state = glassState,
                maxBlurRadius = { 34f * morph().coerceIn(0f, 1f) },
                tint = background.value.copy(alpha = 0.4f),
                foundationColor = background.value,
                direction = BlurDirection.BottomToTop,
                steps = 3,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(statusBarTop + 96.dp)
                    .graphicsLayer { alpha = morph().coerceIn(0f, 1f) }
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.stableStatusBars)
                .padding(top = 6.dp)
                .height(47.dp)
                .padding(start = 26.dp, end = 28.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .drawBehind {
                        val m = morph().coerceIn(0f, 1f)
                        drawCircle(Color.Black.copy(alpha = 0.28f + 0.1f * m))
                    }
                    .combinedClickable(onClick = onBackClick)
            ) {
                Icon(
                    Icons.Rounded.KeyboardArrowLeft,
                    contentDescription = "Atrás",
                    tint = Color.White,
                    modifier = Modifier
                        .size(22.dp)
                        .offset(x = (-1).dp),
                )
            }
            Text(
                text = title,
                style = ArtistBarTitleStyle,
                color = LectorColors.Text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp, end = 12.dp)
                    .graphicsLayer {
                        val m = morph()
                        alpha = m.coerceIn(0f, 1f)
                        translationY = with(density) { 10.dp.toPx() } * (1f - m)
                    },
            )
            actions()
        }
    }
}

/**
 * El armazón de ArtistScreen: el banner fijo detrás de la lista, la lista (que pone el llamador) y la
 * barra de arriba que se escarcha con el muelle de la de la playlist cuando el nombre pasa por debajo.
 * [scrollProgress] 0..1 = cuánto del banner se ha desplazado.
 */
@Composable
internal fun ArtistPageScaffold(
    title: String,
    bannerHeight: Dp,
    scrollProgress: State<Float>,
    onBack: () -> Unit,
    banner: @Composable BoxScope.() -> Unit,
    topActions: @Composable RowScope.() -> Unit = {},
    content: @Composable BoxScope.() -> Unit,
) {
    val bg = LectorColors.Bg
    val background = remember { androidx.compose.runtime.mutableStateOf(bg) }
    background.value = bg
    // the bar frosts once the name has gone under it, with the playlist bar's spring
    val collapsed by remember(scrollProgress) { derivedStateOf { scrollProgress.value > 0.62f } }
    val morph = remember { Animatable(0f) }
    LaunchedEffect(collapsed) {
        val target = if (collapsed) 1f else 0f
        if (Mov.reducido) morph.animateTo(target, tween(120))
        else morph.animateTo(
            targetValue = target,
            animationSpec = spring(dampingRatio = if (collapsed) 0.62f else 0.9f, stiffness = 360f),
        )
    }
    val glassState = remember { GlassState() }
    val edgeBlur = rememberCoverBlurAvailable()

    Box(modifier = Modifier.fillMaxSize().background(bg)) {
        // the bar's frost samples the banner and the list; it sits beside this box, never in it
        Box(
            modifier = Modifier
                .fillMaxSize()
                .glassRoot(glassState, isActive = { morph.value > 0.01f })
        ) {
            ArtistBanner(
                edgeBlur = edgeBlur,
                height = bannerHeight,
                scrollProgress = { scrollProgress.value },
                background = background,
                media = banner,
            )
            content()
        }
        ArtistTopBar(
            glassState = glassState,
            morph = { morph.value },
            title = title,
            background = background,
            onBackClick = onBack,
            actions = topActions,
        )
    }
}

/** el banner de una extensión: su icono ampliado a todo el ancho y muy desenfocado (API 31+; antes, solo atenuado) */
@Composable
internal fun BoxScope.IconBackdrop(content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier = Modifier
            .matchParentSize()
            .graphicsLayer {
                scaleX = 1.3f
                scaleY = 1.3f
                renderEffect = BlurEffectCache.get(54f, Shader.TileMode.CLAMP)
                alpha = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) 0.9f else 0.35f
                clip = true
            },
        content = content,
    )
}
