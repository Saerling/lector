// Copiado de Mochy Music (github.com/realidkroo/Mochy-Music, GPL-3.0): app/src/main/kotlin/com/mochyy/mochymusic/ui/component/AppNavigation.kt
// AppNavigation.kt
//
// Shiori: solo la barra flotante (AppNavigationBar, navHighlight, tabTapped, NavTab, TabIcons). Lo del NavController
// (navigateToTab, owningTab, TabOwnership, el rail) no aplica: Shiori no usa Navigation. Cambios mínimos:
//  - las pestañas son [NavItem] (ImageVector) en vez de Screens con iconos de recurso cargados con coil;
//  - el cristal es FondoCristal(CristalApp), que es lo que Shiori graba con raizCristal (GlassState no se alimenta);
//  - dropShadow (Compose 1.9+) → Modifier.shadow con ChromeShadow;
//  - blanco → LectorColors.Text, para que la barra también se lea en los temas claros.

package com.example.lector.ui.mochy

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.lector.ui.CristalApp
import com.example.lector.ui.FondoCristal
import com.example.lector.ui.LectorColors
import kotlinx.coroutines.launch

/** Shiori: una pestaña de la barra (lo que en Mochy es un Screens). */
@Immutable
class NavItem(
    val label: String,
    val iconActive: ImageVector,
    val iconInactive: ImageVector,
    val iconSize: Dp = 24.dp,
)

/** The bar itself, a stadium the mini player's 64dp matches. */
private val NavBarHeight = 64.dp
private val NavBarShape = RoundedCornerShape(50)

/** The lit tab's highlight sits this far in from the bar's top, bottom and both ends. */
private val NavHighlightInset = 8.dp

/** Room between a highlight and where its neighbour's would be. */
private val NavHighlightGap = 4.dp

/** How bright an unlit tab's icon is. */
private const val UnlitIconAlpha = 0.7f

/**
 * The highlight's front edge springs to the new tab faster than its back edge, so it stretches
 * toward the tab and settles into it instead of sliding over as a rigid block.
 */
private val NavHighlightLead = spring<Float>(dampingRatio = 0.82f, stiffness = 700f)
private val NavHighlightTrail = spring<Float>(dampingRatio = 0.82f, stiffness = 320f)

/**
 * Shiori: el rectángulo de la barra sin el Box exterior de Mochy (su padding 24/8 y los insets los pone ya quien
 * la coloca, junto al mini lector). [onDoubleTap] por pestaña, como el de Search en Mochy.
 */
@Composable
fun AppNavigationBar(
    navigationItems: List<NavItem>,
    selectedIndex: Int,
    onItemClick: (Int, Boolean) -> Unit,
    modifier: Modifier = Modifier,
    onDoubleTap: ((Int) -> Unit)? = null,
    /** Shiori: lo que va dentro del recorte (la luz táctil de Apariencia). */
    clipped: Modifier = Modifier,
) {
    val highlight = LectorColors.Text.copy(alpha = ChromeHighlightColor.alpha)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(NavBarHeight)
            .press3D(maxTilt = 4f, pressedScale = 0.985f)
            .shadow(ChromeShadow.radius, NavBarShape, clip = false, ambientColor = ChromeShadow.color, spotColor = ChromeShadow.color)
            .clip(NavBarShape)
            .drawWithContent {
                drawContent()
                drawChromeOutline(Offset.Zero, size, cornerRadius = size.height / 2f)
            }
            .then(clipped)
    ) {
        FondoCristal(CristalApp, Modifier.fillMaxSize())

        // Half the gap is the row's padding and half is inside each slot, so the highlight sits
        // the same distance from the ends as from the top and bottom.
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = NavHighlightInset - NavHighlightGap / 2)
                .navHighlight(selectedIndex, navigationItems.size, highlight),
            verticalAlignment = Alignment.CenterVertically
        ) {
            navigationItems.forEachIndexed { index, item ->
                NavTab(
                    item = item,
                    isSelected = index == selectedIndex,
                    onItemClick = { selected -> onItemClick(index, selected) },
                    onDoubleTap = onDoubleTap?.let { { it(index) } },
                )
            }
        }
    }
}

/**
 * One highlight behind the lit tab that slides to the next one, instead of each tab fading its own
 * in and out. Only read while drawing, so the slide never recomposes the bar.
 */
@Composable
private fun Modifier.navHighlight(selectedIndex: Int, tabCount: Int, color: Color): Modifier {
    val start = remember { Animatable(selectedIndex.coerceAtLeast(0).toFloat()) }
    val end = remember { Animatable(selectedIndex.coerceAtLeast(0).toFloat()) }
    val shown = remember { Animatable(if (selectedIndex >= 0) 1f else 0f) }

    LaunchedEffect(selectedIndex) {
        if (selectedIndex < 0) {
            shown.animateTo(0f, tween(200))
            return@LaunchedEffect
        }
        val target = selectedIndex.toFloat()
        // back from no lit tab, it fades in where it belongs rather than sliding over from the old one
        if (shown.value == 0f) {
            start.snapTo(target)
            end.snapTo(target)
        }
        launch { shown.animateTo(1f, tween(200)) }
        val movingRight = target > (start.value + end.value) / 2f
        launch { start.animateTo(target, if (movingRight) NavHighlightTrail else NavHighlightLead) }
        end.animateTo(target, if (movingRight) NavHighlightLead else NavHighlightTrail)
    }

    return drawBehind {
        val alpha = shown.value
        if (alpha <= 0f || tabCount == 0) return@drawBehind
        val slot = size.width / tabCount
        val halfGap = NavHighlightGap.toPx() / 2f
        val inset = NavHighlightInset.toPx()
        val left = start.value * slot + halfGap
        val right = (end.value + 1f) * slot - halfGap
        val height = size.height - inset * 2f
        drawRoundRect(
            color = color,
            topLeft = Offset(left, inset),
            size = Size(right - left, height),
            cornerRadius = CornerRadius(height / 2f),
            alpha = alpha,
        )
    }
}

/**
 * A tab's tap, or with [onDoubleTap] a second tap close behind the first: the first still goes to
 * the tab at once, the second does [onDoubleTap] instead. [lastTap] holds the time of the one before.
 */
private fun tabTapped(lastTap: LongArray, doubleTapTimeoutMillis: Long, onDoubleTap: (() -> Unit)?, onTap: () -> Unit) {
    val now = android.os.SystemClock.uptimeMillis()
    if (onDoubleTap != null && now - lastTap[0] < doubleTapTimeoutMillis) {
        lastTap[0] = 0L
        onDoubleTap()
    } else {
        lastTap[0] = now
        onTap()
    }
}

/**
 * One tab of the bar. Its icon swaps to the filled one and brightens as it lights up, and it
 * squishes under a press.
 */
@Composable
private fun RowScope.NavTab(
    item: NavItem,
    isSelected: Boolean,
    onItemClick: (Boolean) -> Unit,
    onDoubleTap: (() -> Unit)?,
) {
    val viewConfiguration = LocalViewConfiguration.current
    val interactionSource = remember { MutableInteractionSource() }
    val lastTap = remember { longArrayOf(0L) }
    val lit = animateFloatAsState(
        targetValue = if (isSelected) 1f else 0f,
        animationSpec = tween(200),
        label = "nav_tab_lit"
    )

    Box(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .pressBounce(interactionSource, pressedScale = 0.85f)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    tabTapped(lastTap, viewConfiguration.doubleTapTimeoutMillis, onDoubleTap) {
                        onItemClick(isSelected)
                    }
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        TabIcons(item) { lit.value }
    }
}

/** a tab's two icons, the filled one fading in over the outline as the tab lights up */
@Composable
private fun TabIcons(
    item: NavItem,
    lit: () -> Float,
) {
    val tint = LectorColors.Text
    Box(contentAlignment = Alignment.Center) {
        Icon(
            imageVector = item.iconInactive,
            contentDescription = item.label,
            tint = tint,
            modifier = Modifier
                .size(item.iconSize)
                .graphicsLayer {
                    alpha = UnlitIconAlpha * (1f - lit())
                    compositingStrategy = CompositingStrategy.ModulateAlpha
                }
        )
        Icon(
            imageVector = item.iconActive,
            contentDescription = null,
            tint = tint,
            modifier = Modifier
                .size(item.iconSize)
                .graphicsLayer {
                    alpha = lit()
                    compositingStrategy = CompositingStrategy.ModulateAlpha
                }
        )
    }
}
