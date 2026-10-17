package com.saerling.lector.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun HomeScreen(
    featured: Series,
    recent: List<Series>,
    mostRead: List<Series>,
    similar: List<Series>,
    similarTo: String,
    onOpen: (Series) -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier
) {
    val list = rememberLazyListState()
    // El título "Home" aparece fijo arriba cuando el héroe ya salió de pantalla.
    val collapsed by remember { derivedStateOf { list.firstVisibleItemIndex > 0 } }

    Box(modifier.fillMaxSize()) {
        LazyColumn(state = list, contentPadding = PaddingValues(bottom = 190.dp)) {
            item { Hero(featured, onContinue) }
            item {
                Spacer(Modifier.height(24.dp))
                SectionHeader("Recently Read")
                Spacer(Modifier.height(12.dp))
                CoverCarousel(recent, onOpen)
            }
            item {
                Spacer(Modifier.height(28.dp))
                SectionHeader("Most Read")
                Spacer(Modifier.height(8.dp))
            }
            itemsIndexed(mostRead.take(3)) { i, s -> RankRow(i + 1, s, onOpen) }
            item {
                Spacer(Modifier.height(28.dp))
                SectionHeader("More like $similarTo")
                Spacer(Modifier.height(12.dp))
                CoverCarousel(similar, onOpen)
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
            ) { LText("Home", LectorType.Display) }
        }
    }
}

@Composable
private fun Hero(featured: Series, onContinue: () -> Unit) {
    Box(Modifier.fillMaxWidth().height(520.dp)) {
        // Cruce con desenfoque al cambiar de serie destacada (blur real en Android 12+).
        AnimatedContent(
            featured, label = "hero",
            transitionSpec = { fadeIn(tween(500)) togetherWith fadeOut(tween(500)) }
        ) { s ->
            val blur by transition.animateDp(label = "heroBlur") {
                if (it == EnterExitState.Visible) 0.dp else 28.dp
            }
            Box(Modifier.fillMaxSize().blur(blur)) {
                Cover(s, Modifier.fillMaxSize(), RoundedCornerShape(0.dp))
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(0.40f to Color.Transparent, 1f to Color.Black)
                    )
                )
                Column(Modifier.align(Alignment.BottomStart).padding(24.dp)) {
                    LText("Continue", LectorType.Label, color = Color(0xB3FFFFFF))
                    LText("${s.title} - ${s.subtitle}", LectorType.Hero, maxLines = 2)
                    Spacer(Modifier.height(14.dp))
                    PillButton("Read", Icons.Rounded.PlayArrow, onContinue)
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(LectorColors.PillFill), Alignment.Center) {
                LText("L", LectorType.Section)
            }
            Box(
                Modifier.size(36.dp).clip(CircleShape).background(
                    Brush.linearGradient(listOf(Color(0xFF7C4DFF), Color(0xFF2A1B5E)))
                )
            )
        }
    }
}

@Composable
private fun RankRow(rank: Int, s: Series, onOpen: (Series) -> Unit) {
    Row(
        Modifier.fillMaxWidth().pressable { onOpen(s) }.padding(horizontal = 24.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LText("$rank", LectorType.Body, Modifier.width(28.dp))
        Cover(s, Modifier.size(48.dp), RoundedCornerShape(10.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            LText(s.title, LectorType.Body, maxLines = 1)
            LText(s.subtitle, LectorType.Label, color = LectorColors.Muted, maxLines = 1)
        }
        Icon(Icons.Rounded.MoreVert, null, tint = LectorColors.Muted)
    }
}
