// Copiado de Mochy Music (github.com/realidkroo/Mochy-Music, GPL-3.0): ui/screens/search/SearchDesign.kt
// (la parte del pliegue al desplazar) y ui/utils/StableInsets.kt.
// Shiori: con «movimiento reducido» el pliegue se resuelve en 120 ms en vez de 900.

package com.example.lector.ui.mochy

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val SearchHorizontalPadding = 24.dp

val SearchFieldHeight = 48.dp

val SearchTitleBlockHeight = 62.dp

val SearchTopClearance = 18.dp

val SearchCollapseEasing = CubicBezierEasing(0.57f, 0.53f, 0f, 1f)

const val SearchCollapseDurationMs = 900

/** el título grande de las páginas de Mochy («Settings», «Search»): 34 sp, negrita */
val SearchTitleStyle = TextStyle(
    fontFamily = InterFontFamily,
    fontWeight = FontWeight.Bold,
    fontSize = 34.sp,
    lineHeight = 40.sp,
)

private fun collapseSpec() = tween<Float>(
    durationMillis = if (com.example.lector.ui.Mov.reducido) 120 else SearchCollapseDurationMs,
    easing = SearchCollapseEasing,
)

/**
 * Same latch-and-settle collapse mechanics as [rememberCollapseProgress] but for a plain
 * [androidx.compose.foundation.ScrollState] (a `Column` + `verticalScroll`, e.g. the Settings
 * screens) instead of a `LazyList`. Crossing the threshold flips a binary target that
 * [animateFloatAsState] always eases all the way to 0 or 1, so the morph finishes smoothly even
 * after the user's finger lifts and scrolling stops, instead of being pinned 1:1 to scroll offset.
 */
@Composable
fun rememberScrollCollapseProgress(
    scrollState: androidx.compose.foundation.ScrollState,
    enterDp: Dp = 24.dp,
    exitDp: Dp = 6.dp,
): State<Float> {
    val density = LocalDensity.current
    val enterPx = with(density) { enterDp.toPx() }
    val exitPx = with(density) { exitDp.toPx() }
    val latch = remember(scrollState) { booleanArrayOf(false) }
    val collapsed by remember(scrollState, enterPx, exitPx) {
        derivedStateOf {
            val offset = scrollState.value.toFloat()
            val next = when {
                offset > enterPx -> true
                offset < exitPx -> false
                else -> latch[0]
            }
            latch[0] = next
            next
        }
    }
    return animateFloatAsState(
        targetValue = if (collapsed) 1f else 0f,
        animationSpec = collapseSpec(),
        label = "scrollCollapse",
    )
}

@Composable
fun rememberCollapseProgress(listState: LazyListState): State<Float> {
    val density = LocalDensity.current
    val enterPx = with(density) { 24.dp.toPx() }
    val exitPx = with(density) { 6.dp.toPx() }
    val latch = remember(listState) { booleanArrayOf(false) }
    val collapsed by remember(listState, enterPx, exitPx) {
        derivedStateOf {
            val offset = listState.firstVisibleItemScrollOffset.toFloat()
            val next = when {
                listState.firstVisibleItemIndex > 0 -> true
                offset > enterPx -> true
                offset < exitPx -> false
                else -> latch[0]
            }
            latch[0] = next
            next
        }
    }
    return animateFloatAsState(
        targetValue = if (collapsed) 1f else 0f,
        animationSpec = collapseSpec(),
        label = "searchCollapse",
    )
}

// the status bar's height as it is when shown: the player can hide it, and pages laid out against the live one jumped as it went and came back
@OptIn(ExperimentalLayoutApi::class)
val WindowInsets.Companion.stableStatusBars: WindowInsets
    @Composable
    @NonRestartableComposable
    get() = WindowInsets.statusBarsIgnoringVisibility
