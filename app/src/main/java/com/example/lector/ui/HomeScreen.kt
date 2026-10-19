package com.example.lector.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.lector.Almacen
import com.example.lector.Comic

@Composable
fun HomeScreen(
    comics: List<Comic>,
    almacen: Almacen,
    onOpen: (Comic) -> Unit,
    onRead: (Comic) -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (comics.isEmpty()) {
        Column(
            modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally
        ) {
            LText("Tu biblioteca está vacía", LectorType.Hero)
            Spacer(Modifier.height(16.dp))
            PillButton("Añadir cómic", Icons.Rounded.Add, onAdd)
        }
        return
    }

    val featured = remember(comics) {
        comics.firstOrNull { it.id == almacen.ultimoAbierto } ?: comics.maxByOrNull { it.leido } ?: comics.first()
    }
    val recientes = remember(comics) { comics.filter { it.leido > 0 }.sortedByDescending { it.leido }.take(12) }
    val enCurso = remember(comics) { comics.filter { it.enCurso() }.sortedByDescending { it.leido }.take(3) }
    val sinLeer = remember(comics) { comics.filter { it.leido == 0L } }

    val list = rememberLazyListState()
    val altoPx = with(LocalDensity.current) { 520.dp.toPx() }
    // Cuánto se ha desplazado la cabecera (se lee solo al dibujar, no recompone la pantalla).
    val scrollPx: () -> Float = { if (list.firstVisibleItemIndex == 0) list.firstVisibleItemScrollOffset.toFloat() else altoPx }
    // El título "Inicio" aparece fijo arriba cuando la cabecera ya salió de pantalla.
    val collapsed by remember { derivedStateOf { list.firstVisibleItemIndex > 0 } }

    Box(modifier.fillMaxSize()) {
        LazyColumn(state = list, contentPadding = PaddingValues(bottom = 190.dp)) {
            item { Hero(featured, comics, almacen, onOpen, onRead, onAdd, scrollPx, altoPx) }
            if (recientes.isNotEmpty()) item {
                Spacer(Modifier.height(24.dp)); SectionHeader("Leídos recientemente")
                Spacer(Modifier.height(12.dp)); CoverCarousel(recientes, almacen, onOpen)
            }
            if (enCurso.isNotEmpty()) {
                item { Spacer(Modifier.height(28.dp)); SectionHeader("En curso"); Spacer(Modifier.height(8.dp)) }
                itemsIndexed(enCurso, key = { _, c -> c.id }) { i, c -> FilaProgreso(c, almacen, onOpen, Modifier.entrada(i)) }
            }
            if (sinLeer.isNotEmpty()) item {
                Spacer(Modifier.height(28.dp)); SectionHeader("Sin leer")
                Spacer(Modifier.height(12.dp)); CoverCarousel(sinLeer, almacen, onOpen)
            }
        }

        AnimatedVisibility(
            collapsed,
            enter = fadeIn(tween(250)) + slideInVertically { -it / 2 },
            exit = fadeOut(tween(200)) + slideOutVertically { -it / 2 }
        ) {
            Box(
                Modifier.fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Black, Color.Black.copy(0.92f), Color.Transparent)))
                    .statusBarsPadding().padding(horizontal = 24.dp, vertical = 12.dp)
            ) { LText("Inicio", LectorType.Display) }
        }
    }
}

@Composable
private fun Hero(featured: Comic, all: List<Comic>, almacen: Almacen, onOpen: (Comic) -> Unit, onRead: (Comic) -> Unit, onAdd: () -> Unit, scroll: () -> Float, altoPx: Float) {
    Box(
        Modifier.fillMaxWidth().height(520.dp).graphicsLayer {
            val p = (scroll() / altoPx).coerceIn(0f, 1f)
            translationY = scroll() * 0.45f          // parallax: la portada sube más despacio
            alpha = 1f - p * 0.9f                    // se desvanece
            val r = p * 40f                          // y se desenfoca (Android 12+)
            renderEffect = if (r > 1f && android.os.Build.VERSION.SDK_INT >= 31) BlurEffect(r, r, TileMode.Decal) else null
        }
    ) {
        // Cruce con desenfoque al cambiar de cómic (blur real en Android 12+).
        AnimatedContent(
            targetState = featured.id, label = "hero",
            transitionSpec = { ContentTransform(fadeIn(tween(500)), fadeOut(tween(500))) }
        ) { id ->
            val s = all.firstOrNull { it.id == id } ?: featured
            val blur by transition.animateDp(label = "heroBlur") {
                if (it == EnterExitState.Visible) 0.dp else 28.dp
            }
            Box(Modifier.fillMaxSize().blur(blur)) {
                ComicCover(s, almacen, Modifier.fillMaxSize(), SuaveShape(0.dp))
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(0.40f to Color.Transparent, 1f to Color.Black)
                    )
                )
                Column(Modifier.align(Alignment.BottomStart).padding(24.dp)) {
                    LText("Continuar", LectorType.Label, color = Color(0xB3FFFFFF))
                    LText(s.titulo, LectorType.Hero, maxLines = 2)
                    LText(s.textoPagina(), LectorType.Label, color = Color(0xB3FFFFFF))
                    Spacer(Modifier.height(14.dp))
                    PillButton("Leer", Icons.Rounded.PlayArrow, { onRead(s) })
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(36.dp).clip(SuaveShape(10.dp)).background(LectorColors.PillFill), Alignment.Center) {
                LText("L", LectorType.Section)
            }
            Box(
                Modifier.size(36.dp).clip(CircleShape).background(LectorColors.PillFill).pressable(onAdd),
                Alignment.Center
            ) { Icon(Icons.Rounded.Add, "Añadir", tint = LectorColors.Text, modifier = Modifier.size(20.dp)) }
        }
    }
}

@Composable
private fun FilaProgreso(c: Comic, almacen: Almacen, onOpen: (Comic) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().resaltable { onOpen(c) }.padding(horizontal = 24.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ComicCover(c, almacen, Modifier.size(width = 44.dp, height = 60.dp), SuaveShape(10.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            LText(c.titulo, LectorType.Body, maxLines = 1)
            LText(c.textoPagina(), LectorType.Label, color = LectorColors.Muted, maxLines = 1)
            Spacer(Modifier.height(8.dp))
            ProgressLine(c.progresoLectura())
        }
    }
}
