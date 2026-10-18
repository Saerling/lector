package com.example.lector.ui

import android.graphics.BitmapFactory
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.lector.Almacen
import com.example.lector.Comic

@Composable
fun LText(
    text: String, style: TextStyle, modifier: Modifier = Modifier,
    color: Color = LectorColors.Text, maxLines: Int = Int.MAX_VALUE
) = Text(text, modifier, color, style = style, maxLines = maxLines, overflow = TextOverflow.Ellipsis)

/** Rebote suave al presionar. */
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

/** Portada real (la que ya guarda tu Almacen); si no hay, muestra la inicial del título. */
@Composable
fun ComicCover(comic: Comic, almacen: Almacen, modifier: Modifier = Modifier, shape: Shape = RoundedCornerShape(20.dp)) {
    val portada = remember(comic.id) {
        BitmapFactory.decodeFile(almacen.portadaDe(comic.id).path)?.asImageBitmap()
    }
    Box(modifier.clip(shape).background(LectorColors.CardHigh), contentAlignment = Alignment.Center) {
        if (portada != null) {
            Image(bitmap = portada, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            LText(comic.titulo.take(1).uppercase(), LectorType.Display, color = LectorColors.Muted)
        }
    }
}

@Composable
fun ProgressLine(progress: Float, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(3.dp).clip(CircleShape).background(LectorColors.PillFill)) {
        Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).fillMaxHeight().background(LectorColors.Text))
    }
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

/** Carrusel con "snap"; la última portada queda cortada para sugerir scroll. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CoverCarousel(items: List<Comic>, almacen: Almacen, onClick: (Comic) -> Unit, modifier: Modifier = Modifier) {
    val state = rememberLazyListState()
    LazyRow(
        modifier, state = state,
        contentPadding = PaddingValues(horizontal = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        flingBehavior = rememberSnapFlingBehavior(state)
    ) {
        items(items, key = { it.id }) { c ->
            Column(Modifier.width(140.dp).pressable { onClick(c) }) {
                ComicCover(c, almacen, Modifier.fillMaxWidth().aspectRatio(2f / 3f))
                Spacer(Modifier.height(8.dp))
                LText(c.titulo, LectorType.Body, maxLines = 1)
                LText(c.textoPagina(), LectorType.Label, color = LectorColors.Muted, maxLines = 1)
            }
        }
    }
}

enum class LectorTab(val label: String, val icon: ImageVector) {
    Home("Inicio", Icons.Rounded.Home),
    Search("Explorar", Icons.Rounded.Search),
    Library("Biblioteca", Icons.Rounded.List),
    History("Historial", Icons.Rounded.DateRange),
    More("Más", Icons.Rounded.MoreVert)
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
        LectorTab.values().forEach { tab ->
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

/** "Continuar leyendo": el último cómic que abriste. */
@Composable
fun MiniReader(comic: Comic, almacen: Almacen, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(24.dp)
    Row(
        modifier.pressable(onClick).fillMaxWidth().height(64.dp).clip(shape)
            .background(LectorColors.Glass).border(1.dp, LectorColors.GlassBorder, shape).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ComicCover(comic, almacen, Modifier.fillMaxHeight().aspectRatio(1f), RoundedCornerShape(12.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            LText(comic.titulo, LectorType.Body, maxLines = 1)
            LText(comic.textoPagina(), LectorType.Label, color = LectorColors.Muted, maxLines = 1)
        }
        Icon(Icons.Rounded.PlayArrow, "Continuar", tint = LectorColors.Text, modifier = Modifier.size(28.dp))
        Spacer(Modifier.width(8.dp))
    }
}
