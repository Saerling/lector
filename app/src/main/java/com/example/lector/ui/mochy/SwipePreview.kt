// Copiado de Mochy Music (github.com/realidkroo/Mochy-Music, GPL-3.0): app/src/main/kotlin/com/mochyy/mochymusic/ui/screens/gesture/SwipePreview.kt
//
// The little demo on each Gesture card: a song row slides out and the real swipe pill grows into
// the gap, then turns the action's colour and says what it will do, then the row slides back.
// The pill is the one the lists use (SwipePill), so what you see here is what you get there.
//
// Shiori: la fila de muestra es de capítulo (punto de no leído, dos líneas, botón de descarga), y el reloj
// del laboratorio de Mochy (rememberLabTime / rememberOnScreen) es un bucle de fotogramas que solo corre
// mientras la tarjeta está compuesta. Con «Quitar animaciones» se queda quieta con la pastilla armada.

package com.example.lector.ui.mochy

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.example.lector.ui.LectorColors
import com.example.lector.ui.Mov

private val RowHeight = 60.dp

// one round of the demo, in seconds
private const val LoopSeconds = 4f
private const val RestSeconds = 0.5f
private const val PullSeconds = 0.9f
private const val HoldUntil = 3f
private const val ReturnSeconds = 0.7f

// a freshly picked action starts pulling this soon, so the change is seen right away
private const val PickLeadIn = 0.3f

/** how far out the row is, 0 at rest to 1 when the pill has fired its colour, at [seconds] into the demo */
private fun pullAt(seconds: Float): Float {
    val t = seconds.mod(LoopSeconds)
    return when {
        t < RestSeconds -> 0f
        t < RestSeconds + PullSeconds -> FastOutSlowInEasing.transform((t - RestSeconds) / PullSeconds)
        t < HoldUntil -> 1f
        t < HoldUntil + ReturnSeconds -> 1f - FastOutSlowInEasing.transform((t - HoldUntil) / ReturnSeconds)
        else -> 0f
    }
}

/**
 * Draws a chapter row sliding [toRight] or to the left, with [action]'s pill opening behind it. It
 * stops moving while it's off composition.
 */
@Composable
fun SwipePreview(action: ChapterSwipeAction, toRight: Boolean, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val reduced = Mov.reducido
    // Shiori: en lugar de rememberLabTime, segundos desde que la tarjeta apareció
    val time = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(reduced) {
        if (reduced) return@LaunchedEffect
        val start = withFrameNanos { it }
        while (true) withFrameNanos { time.floatValue = (it - start) / 1_000_000_000f }
    }
    val origin = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(action) { origin.floatValue = time.floatValue - (RestSeconds - PickLeadIn) }

    var width by remember { mutableIntStateOf(0) }
    var pillNeed by remember { mutableFloatStateOf(0f) }
    val rowHeightPx = with(density) { RowHeight.roundToPx() }
    val pillRoom = with(density) { (PillEdgeInset + PillGap - SlabInset).toPx() }
    // the same line a real swipe has: a third of the row, or further out if the label needs it
    val line = maxOf(
        (width * 0.3f).coerceIn(with(density) { 100.dp.toPx() }, with(density) { 150.dp.toPx() }),
        pillNeed + pillRoom,
    )
    val pull = { if (reduced) 1f else pullAt(time.floatValue - origin.floatValue) }
    val out = { pull() * line }
    val armed by remember(reduced) { derivedStateOf { pull() >= 1f } }

    val swipeAction = SwipeAction(action.icon, action.colorActual, action.pillLabel) {}

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(RowHeight)
            .onSizeChanged { width = it.width },
    ) {
        if (action != ChapterSwipeAction.NONE) {
            SwipePill(
                action = swipeAction,
                atStart = toRight,
                room = { (out() - pillRoom).coerceAtLeast(0f) },
                rowHeight = rowHeightPx,
                armed = armed,
                fired = false,
                onNeed = { pillNeed = it },
            )
            SampleRow(Modifier.graphicsLayer { translationX = if (toRight) out() else -out() })
        } else {
            // Shiori: «Nada» no abre hueco; la fila apenas cede, como un lado sin acción
            SampleRow(Modifier.graphicsLayer {
                val give = minOf(out() * 0.2f, 22.dp.toPx())
                translationX = if (toRight) give else -give
            })
        }
    }
}

/** a stand-in chapter row: unread dot, two lines of text and the round download button at the end, on its slab */
@Composable
private fun SampleRow(modifier: Modifier = Modifier) {
    val ink = LectorColors.Text
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxSize()
            .drawBehind {
                val inset = SlabInset.toPx()
                drawRoundRect(
                    color = SwipeSlabColor,
                    topLeft = Offset(inset, 0f),
                    size = Size(size.width - inset * 2f, size.height),
                    cornerRadius = CornerRadius(size.height / 2f),
                )
            }
            .padding(horizontal = SlabInset + 14.dp),
    ) {
        Box(Modifier.size(9.dp).clip(CircleShape).background(ink.copy(alpha = 0.5f)))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Box(Modifier.fillMaxWidth(0.55f).height(8.dp).clip(CircleShape).background(ink.copy(alpha = 0.22f)))
            Box(Modifier.fillMaxWidth(0.32f).height(8.dp).clip(CircleShape).background(ink.copy(alpha = 0.13f)))
        }
        Spacer(Modifier.width(12.dp))
        Box(Modifier.size(26.dp).clip(CircleShape).background(ink.copy(alpha = if (ink == Color.White) 0.5f else 0.35f)))
    }
}
