package com.example.lector.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import com.example.lector.Almacen
import com.example.lector.Comic

@Composable
fun SearchScreen(comics: List<Comic>, almacen: Almacen, onOpen: (Comic) -> Unit) {
    var q by rememberSaveable { mutableStateOf("") }
    val res = remember(q, comics) {
        if (q.isBlank()) comics else comics.filter { it.titulo.contains(q.trim(), ignoreCase = true) }
    }
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 24.dp)) {
        Spacer(Modifier.height(16.dp))
        LText("Buscar", LectorType.Display)
        Spacer(Modifier.height(16.dp))
        BasicTextField(
            value = q, onValueChange = { q = it }, singleLine = true,
            textStyle = LectorType.Body.copy(color = LectorColors.Text),
            cursorBrush = SolidColor(LectorColors.Text),
            decorationBox = { inner ->
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(LectorColors.Card)
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    if (q.isEmpty()) LText("Buscar en tu biblioteca…", LectorType.Body, color = LectorColors.Muted)
                    inner()
                }
            }
        )
        Spacer(Modifier.height(16.dp))
        LazyVerticalGrid(
            GridCells.Fixed(3),
            contentPadding = PaddingValues(bottom = 190.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(res, key = { it.id }) { c ->
                Column(Modifier.pressable { onOpen(c) }) {
                    ComicCover(c, almacen, Modifier.fillMaxWidth().aspectRatio(2f / 3f), RoundedCornerShape(14.dp))
                    Spacer(Modifier.height(6.dp))
                    LText(c.titulo, LectorType.Label, maxLines = 2)
                }
            }
        }
    }
}
