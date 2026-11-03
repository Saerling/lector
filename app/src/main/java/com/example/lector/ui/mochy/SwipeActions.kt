// Copiado de Mochy Music (github.com/realidkroo/Mochy-Music, GPL-3.0): app/src/main/kotlin/com/mochyy/mochymusic/ui/component/SwipeActions.kt
//
// Shiori: el gesto, la física y el dibujo son los de Mochy tal cual. Cambia lo que depende de Mochy:
// los iconos son ImageVector (no @DrawableRes), las acciones de canciones viven ahora en ChapterSwipeBox.kt
// (acciones de capítulo), el háptico va por la vista (los HapticFeedbackType de Compose 1.8 no existen en 1.7)
// y los colores neutros siguen al tema claro/oscuro de Shiori.

package com.example.lector.ui.mochy

import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.example.lector.ui.LectorColors
import com.example.lector.ui.Mov
import com.example.lector.ui.TemaActual
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign

/** one side of a swipeable row: what it shows while you drag, and what it does once you let go past the line */
@Immutable
class SwipeAction(
    val icon: ImageVector,
    val color: Color,
    val label: String,
    val onTrigger: () -> Unit,
)

/** queueing a song is green, saving it or taking it out of your playlist is purple (whichever side it's on) */
val SwipeQueueColor = Color(0xFF34C759)
val SwipeSaveColor = Color(0xFF9B72F2)
// each extra gesture option gets its own colour so you can tell what you armed before letting go
val SwipeDownloadColor = Color(0xFF0A84FF)
val SwipePlayNextColor = Color(0xFFFF9F0A)
val SwipePlaylistColor = Color(0xFFFF375F)
val SwipeDetailsColor = Color(0xFF5AC8FA)

/** for a swipe that takes music out of the library for good, like removing an import */
val SwipeRemoveColor = Color(0xFFFF453A)

// Shiori: en Mochy son blanco al 12 % sobre negro; en tema claro, negro suave.
/** la losa sobre la que va la fila mientras se mueve (HighlightColor de Mochy, TouchFeedback.kt, con tema claro) */
internal val SwipeSlabColor: Color
    get() = if (TemaActual.oscuro) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.06f)
private val SwipePillIdle: Color
    get() = if (TemaActual.oscuro) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.08f)
private val SwipeIconIdle: Color
    get() = if (TemaActual.oscuro) Color.White else LectorColors.Text

// a hold has to be this long before it counts as a long press inside a swipeable row, so pressing
// down and then sliding never opens selection or the menu by accident
private const val SwipeHoldMillis = 650L

// past the line the row only follows a third of the finger, so it feels like it's straining
private const val SwipeResistance = 0.35f

// the pill lives in the gap the swipe opens: this far in from the screen edge, this far from the row
internal val PillEdgeInset = 10.dp
internal val PillGap = 8.dp
internal val SlabInset = 10.dp
private val PillPadding = 13.dp
private val PillIconSize = 18.dp
private val PillLabelSpacing = 6.dp

private val SwipeLabelStyle = TextStyle(
    fontFamily = InterFontFamily,
    fontWeight = FontWeight.SemiBold,
    fontSize = 12.5.sp,
    letterSpacing = (-0.02).em,
)

// Shiori: HapticFeedbackType.GestureThresholdActivate / SegmentTick / Confirm son de Compose 1.8;
// aquí se piden a la vista con las mismas constantes de Android (y una parecida en versiones viejas).
private object SwipeHaptic {
    val ThresholdActivate get() = if (Build.VERSION.SDK_INT >= 34) HapticFeedbackConstants.GESTURE_THRESHOLD_ACTIVATE else HapticFeedbackConstants.LONG_PRESS
    val SegmentTick get() = if (Build.VERSION.SDK_INT >= 34) HapticFeedbackConstants.SEGMENT_TICK else HapticFeedbackConstants.CLOCK_TICK
    val Confirm get() = if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY
}

/**
 * a row you can drag sideways, like Frame 117: the row slides on a dark rounded slab, and the
 * action for that side grows out of the edge into the gap it leaves, a dot first, then a circle,
 * then a pill that keeps pace with your finger. past the line it fills with the action's colour
 * (with a tick you can feel) and says what it will do; letting go there does it while the row
 * springs back.
 *
 * [start] is revealed on the left by sliding right, [end] on the right by sliding left. both are
 * only composed while their side is showing, so an action can look things up without every row
 * in a list paying for it. rows that are already a pill of their own turn the [slab] off.
 */
@Composable
fun SwipeActionsBox(
    modifier: Modifier = Modifier,
    start: (@Composable () -> SwipeAction)? = null,
    end: (@Composable () -> SwipeAction)? = null,
    enabled: Boolean = true,
    slab: Boolean = true,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()

    var rowSize by remember { mutableStateOf(IntSize.Zero) }
    // what's drawn, and where the finger really is (they part ways past the line)
    var offset by remember { mutableFloatStateOf(0f) }
    var travel by remember { mutableFloatStateOf(0f) }
    // 1 = the start side is armed, -1 = the end side, 0 = neither
    var armed by remember { mutableIntStateOf(0) }
    var fired by remember { mutableIntStateOf(0) }
    val settle = remember { arrayOfNulls<Job>(1) }
    val startAction = remember { mutableStateOf<SwipeAction?>(null) }
    val endAction = remember { mutableStateOf<SwipeAction?>(null) }

    // how wide each side's pill is with its label showing; that side's line moves out to fit it
    var startNeed by remember { mutableFloatStateOf(0f) }
    var endNeed by remember { mutableFloatStateOf(0f) }

    val baseThreshold = with(density) { (rowSize.width * 0.3f).coerceIn(100.dp.toPx(), 150.dp.toPx()) }
    // the pill's width is the travel less this: its gap to the row and to the screen edge
    val pillRoom = with(density) { (PillEdgeInset + PillGap - SlabInset).toPx() }
    val deadSideLimit = with(density) { 22.dp.toPx() }
    val hysteresis = with(density) { 4.dp.toPx() }

    fun thresholdFor(direction: Float): Float =
        maxOf(baseThreshold, (if (direction > 0f) startNeed else endNeed) + pillRoom)

    fun resisted(raw: Float): Float {
        val allowed = if (raw > 0f) start != null else end != null
        val distance = abs(raw)
        val line = thresholdFor(raw)
        val shown = when {
            // a side with nothing on it only gives a little, so the row doesn't feel stuck
            !allowed -> minOf(distance * 0.2f, deadSideLimit)
            distance <= line -> distance
            else -> line + (distance - line) * SwipeResistance
        }
        return sign(raw) * shown
    }

    fun unresisted(shown: Float): Float {
        val distance = abs(shown)
        val line = thresholdFor(shown)
        return if (distance <= line) shown else sign(shown) * (line + (distance - line) / SwipeResistance)
    }

    LaunchedEffect(enabled) {
        if (!enabled) {
            offset = 0f
            travel = 0f
            armed = 0
        }
    }

    val dragState = rememberDraggableState { delta ->
        travel += delta
        offset = resisted(travel)
        val side = when {
            offset > 0f && start != null && offset >= thresholdFor(1f) -> 1
            offset < 0f && end != null && -offset >= thresholdFor(-1f) -> -1
            // a few dp of hysteresis, so hovering right on the line doesn't buzz (small enough
            // that the pill never gets narrower than its label while the label is out)
            armed != 0 && sign(offset) == armed.toFloat() && abs(offset) >= thresholdFor(offset) - hysteresis -> armed
            else -> 0
        }
        if (side != armed) {
            armed = side
            view.performHapticFeedback(
                if (side != 0) SwipeHaptic.ThresholdActivate else SwipeHaptic.SegmentTick
            )
        }
    }

    val baseConfiguration = LocalViewConfiguration.current
    val longHold = remember(baseConfiguration) {
        object : ViewConfiguration by baseConfiguration {
            override val longPressTimeoutMillis: Long
                get() = maxOf(baseConfiguration.longPressTimeoutMillis, SwipeHoldMillis)
        }
    }

    val showStart by remember { derivedStateOf { offset > 0.5f } }
    val showEnd by remember { derivedStateOf { offset < -0.5f } }

    // provided around the whole row, so a click handler passed in through [modifier] waits
    // the longer hold too, not just the ones inside [content]
    CompositionLocalProvider(LocalViewConfiguration provides longHold) {
        Box(
            modifier = modifier
                .onSizeChanged { rowSize = it }
                .draggable(
                    state = dragState,
                    orientation = Orientation.Horizontal,
                    enabled = enabled,
                    onDragStarted = {
                        settle[0]?.cancel()
                        fired = 0
                        travel = unresisted(offset)
                    },
                    onDragStopped = { velocity ->
                        val side = armed
                        val action = when (side) {
                            1 -> startAction.value
                            -1 -> endAction.value
                            else -> null
                        }
                        if (action != null) {
                            fired = side
                            view.performHapticFeedback(SwipeHaptic.Confirm)
                            action.onTrigger()
                        }
                        armed = 0
                        settle[0] = scope.launch {
                            animate(
                                initialValue = offset,
                                targetValue = 0f,
                                initialVelocity = velocity.coerceIn(-3000f, 3000f) * 0.35f,
                                // Shiori: con «Quitar animaciones» vuelve sin rebote
                                animationSpec = if (Mov.reducido) tween(160) else spring(dampingRatio = 0.62f, stiffness = 380f),
                            ) { value, _ ->
                                offset = value
                                travel = value
                            }
                            fired = 0
                        }
                    },
                ),
        ) {
            if (showStart && start != null) {
                val action = start()
                SideEffect { startAction.value = action }
                SwipePill(
                    action = action,
                    atStart = true,
                    room = { (offset - pillRoom).coerceAtLeast(0f) },
                    rowHeight = rowSize.height,
                    armed = armed == 1,
                    fired = fired == 1,
                    onNeed = { startNeed = it },
                )
            }
            if (showEnd && end != null) {
                val action = end()
                SideEffect { endAction.value = action }
                SwipePill(
                    action = action,
                    atStart = false,
                    room = { (-offset - pillRoom).coerceAtLeast(0f) },
                    rowHeight = rowSize.height,
                    armed = armed == -1,
                    fired = fired == -1,
                    onNeed = { endNeed = it },
                )
            }

            Box(
                modifier = Modifier
                    .graphicsLayer { translationX = offset }
                    .drawBehind {
                        // the row rides on its own rounded slab while it's moving, side to side
                        val strength = if (slab) (abs(offset) / 20.dp.toPx()).coerceIn(0f, 1f) else 0f
                        if (strength > 0f) {
                            val inset = SlabInset.toPx()
                            val highlight = SwipeSlabColor
                            drawRoundRect(
                                color = highlight.copy(alpha = highlight.alpha * strength),
                                topLeft = Offset(inset, 0f),
                                size = Size(size.width - inset * 2f, size.height),
                                cornerRadius = CornerRadius(size.height / 2f),
                            )
                        }
                    },
            ) {
                content()
            }
        }
    }
}

/**
 * the action's pill. it only ever fills the gap the swipe has opened ([room]), so it never slides
 * under the row: a dot at first, a circle once there's room for one, then it stretches with the
 * finger through a stiff spring until it's as wide as its label needs. armed, it takes the
 * action's colour and the label slides out beside the icon.
 */
@Composable
internal fun BoxScope.SwipePill(
    action: SwipeAction,
    atStart: Boolean,
    room: () -> Float,
    rowHeight: Int,
    armed: Boolean,
    fired: Boolean,
    onNeed: (Float) -> Unit,
) {
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val pillHeight = with(density) { (rowHeight.toDp() * 0.6f).coerceIn(32.dp, 38.dp).toPx() }
    val labelWidth = remember(action.label, density) {
        measurer.measure(action.label, SwipeLabelStyle).size.width.toFloat()
    }
    val need = with(density) { (PillPadding * 2 + PillIconSize + PillLabelSpacing).toPx() } + labelWidth
    SideEffect { onNeed(need) }

    val width by animateFloatAsState(
        targetValue = room().coerceIn(0f, need),
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 1100f),
        label = "swipePillWidth",
    )
    val fill by animateColorAsState(
        targetValue = if (armed || fired) action.color else SwipePillIdle,
        animationSpec = spring(stiffness = 700f),
        label = "swipePillFill",
    )
    // Shiori: el icono va blanco sobre el color; en reposo sigue al tema
    val tint by animateColorAsState(
        targetValue = if (armed || fired) Color.White else SwipeIconIdle,
        animationSpec = spring(stiffness = 700f),
        label = "swipePillTint",
    )
    val bump = remember { Animatable(1f) }
    LaunchedEffect(armed, fired) {
        if ((armed || fired) && !Mov.reducido) {
            bump.animateTo(if (fired) 1.3f else 1.2f, tween(85))
            bump.animateTo(1f, spring(dampingRatio = 0.38f, stiffness = 560f))
        }
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .align(if (atStart) Alignment.CenterStart else Alignment.CenterEnd)
            .padding(horizontal = PillEdgeInset)
            .layout { measurable, _ ->
                // the spring may lag or overshoot, but the pill is never wider than the gap
                // that's actually open right now, so it can't overlap the row
                val w = minOf(width, room()).coerceAtLeast(0f)
                val h = minOf(w, pillHeight)
                val placeable = measurable.measure(Constraints.fixed(w.roundToInt(), h.roundToInt()))
                layout(placeable.width, placeable.height) { placeable.place(0, 0) }
            }
            .clip(RoundedCornerShape(50))
            .background(fill),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.wrapContentWidth(unbounded = true),
        ) {
            Icon(
                imageVector = action.icon,
                contentDescription = action.label,
                tint = tint,
                modifier = Modifier
                    .size(PillIconSize)
                    .graphicsLayer {
                        // grows with the pill: tiny in the dot, full size once it's a circle
                        val grown = (minOf(width, room()) / pillHeight).coerceIn(0f, 1f)
                        val scale = (0.35f + 0.65f * grown) * bump.value
                        scaleX = scale
                        scaleY = scale
                        alpha = grown
                    },
            )
            AnimatedVisibility(
                visible = armed,
                enter = if (Mov.reducido) fadeIn(tween(120)) else
                    expandHorizontally(spring(dampingRatio = 0.8f, stiffness = 700f), expandFrom = Alignment.Start) + fadeIn(tween(160)),
                exit = if (Mov.reducido) fadeOut(snap()) else
                    shrinkHorizontally(tween(140), shrinkTowards = Alignment.Start) + fadeOut(tween(100)),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Spacer(Modifier.width(PillLabelSpacing))
                    Text(
                        text = action.label,
                        style = SwipeLabelStyle,
                        color = Color.White,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
        }
    }
}
