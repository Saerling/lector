package com.saerling.lector.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun LText(
    text: String, style: TextStyle, modifier: Modifier = Modifier,
    color: Color = LectorColors.Text, maxLines: Int = Int.MAX_VALUE
) = Text(text, modifier, color, style = style, maxLines = maxLines, overflow = TextOverflow.Ellipsis)

/** Rebote suave al presionar (el "feel" del video). */
@Composable
fun Modifier.pressable(onClick: () -> Unit): Modifier {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) 0.96f else 1f,
        spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium), label = "press"
    )
    return this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .clickable(interactionSource = src, indication = null, onClick = onClick)
}

/** Portada. TODO: cambia el contenido por AsyncImage (Coil) con el primer cuadro del CBZ. */
@Composable
fun Cover(series: Series, modifier: Modifier = Modifier, shape: Shape = RoundedCornerShape(20.dp)) {
    Box(
        modifier.clip(shape).background(
            Brush.verticalGradient(listOf(series.color, series.color.copy(alpha = 0.35f), Color.Black))
        ),
        contentAlignment = Alignment.Center
    ) { LText(series.title.take(1), LectorType.Display.copy(fontSize = 44.sp), color = Color(0x55FFFFFF)) }
}

@Composable
fun PillButton(text: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.pressable(onClick).clip(CircleShape).background(LectorColors.PillFill)
            .padding(horizontal = 18.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = LectorColors.Text, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        LText(text, LectorType.Body)
    }
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier) {
    Row(modifier.padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
        LText(title, LectorType.Section)
        Icon(Icons.Rounded.KeyboardArrowRight, null, tint = LectorColors.Muted, modifier = Modifier.size(18.dp))
    }
}

/** Carrusel con "snap" y la última portada cortada para sugerir scroll. */
@Composable
fun CoverCarousel(items: List<Series>, onClick: (Series) -> Unit, modifier: Modifier = Modifier) {
    val state = rememberLazyListState()
    LazyRow(
        modifier, state = state,
        contentPadding = PaddingValues(horizontal = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        flingBehavior = rememberSnapFlingBehavior(state)
    ) {
        items(items, key = { it.id }) { s ->
            Column(Modifier.width(140.dp).pressable { onClick(s) }) {
                Cover(s, Modifier.fillMaxWidth().aspectRatio(2f / 3f))
                Spacer(Modifier.height(8.dp))
                LText(s.title, LectorType.Body, maxLines = 1)
                LText(s.subtitle, LectorType.Label, color = LectorColors.Muted, maxLines = 1)
            }
        }
    }
}

enum class LectorTab(val label: String, val icon: ImageVector) {
    Home("Home", Icons.Rounded.Home),
    Search("Search", Icons.Rounded.Search),
    Library("Library", Icons.Rounded.List),
    Profile("Profile", Icons.Rounded.Person)
}

/** Barra flotante en píldora; el indicador de la pestaña activa cambia con resorte. */
@Composable
fun FloatingNavBar(selected: LectorTab, onSelect: (LectorTab) -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(30.dp)
    Row(
        modifier.fillMaxWidth().height(60.dp).clip(shape).background(LectorColors.Glass)
            .border(1.dp, LectorColors.GlassBorder, shape).padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        LectorTab.entries.forEach { tab ->
            val sel = tab == selected
            val bg by animateColorAsState(
                if (sel) LectorColors.PillFill else Color.Transparent,
                spring(stiffness = Spring.StiffnessMediumLow), label = "navBg"
            )
            val tint by animateColorAsState(if (sel) LectorColors.Text else LectorColors.Muted, label = "navTint")
            Box(
                Modifier.weight(1f).fillMaxHeight().clip(CircleShape).background(bg)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onSelect(tab) },
                contentAlignment = Alignment.Center
            ) { Icon(tab.icon, tab.label, tint = tint) }
        }
    }
}

/** Equivalente al mini reproductor: "continuar leyendo". */
@Composable
fun MiniReader(series: Series, progress: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(24.dp)
    Row(
        modifier.pressable(onClick).fillMaxWidth().height(64.dp).clip(shape)
            .background(LectorColors.Glass).border(1.dp, LectorColors.GlassBorder, shape).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Cover(series, Modifier.fillMaxHeight().aspectRatio(1f), RoundedCornerShape(12.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            LText(series.title, LectorType.Body, maxLines = 1)
            LText(progress, LectorType.Label, color = LectorColors.Muted, maxLines = 1)
        }
        Icon(Icons.Rounded.PlayArrow, "Continuar", tint = LectorColors.Text, modifier = Modifier.size(28.dp))
        Spacer(Modifier.width(8.dp))
    }
}
