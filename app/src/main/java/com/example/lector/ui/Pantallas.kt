package com.example.lector.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

@Composable
fun ProfileScreen(comics: List<Comic>, onAdd: () -> Unit, onAddFolder: () -> Unit) {
    val paginasLeidas = comics.filter { it.leido > 0 }.sumOf { it.ultimaPagina + 1 }
    val terminados = comics.count { it.terminado() }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding()
            .padding(horizontal = 24.dp).padding(bottom = 190.dp)
    ) {
        Spacer(Modifier.height(16.dp))
        LText("Perfil", LectorType.Display)
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard("${comics.size}", "Cómics", Modifier.weight(1f))
            StatCard("$terminados", "Terminados", Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
        StatCard("$paginasLeidas", "Páginas leídas", Modifier.fillMaxWidth())
        Spacer(Modifier.height(24.dp))
        LText("Biblioteca", LectorType.Section)
        Spacer(Modifier.height(12.dp))
        AccionCard("Añadir cómic (.cbz)", onAdd)
        Spacer(Modifier.height(10.dp))
        AccionCard("Añadir carpeta de imágenes", onAddFolder)
    }
}

@Composable
private fun StatCard(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier.clip(RoundedCornerShape(24.dp)).background(LectorColors.Card).padding(20.dp)) {
        LText(value, LectorType.Display.copy(fontSize = 40.sp))
        Spacer(Modifier.height(4.dp))
        LText(label, LectorType.Mono, color = LectorColors.Muted)
    }
}

@Composable
private fun AccionCard(texto: String, onClick: () -> Unit) {
    androidx.compose.foundation.layout.Row(
        Modifier.fillMaxWidth().pressable(onClick).clip(RoundedCornerShape(20.dp))
            .background(LectorColors.Card).padding(18.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        androidx.compose.material3.Icon(Icons.Rounded.Add, null, tint = LectorColors.Text)
        Spacer(Modifier.width(12.dp))
        LText(texto, LectorType.Body)
    }
}
