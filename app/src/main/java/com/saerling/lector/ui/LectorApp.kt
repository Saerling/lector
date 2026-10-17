package com.saerling.lector.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Punto de entrada: llámalo desde MainActivity -> setContent { LectorApp() } */
@Composable
fun LectorApp() {
    LectorTheme {
        var tab by rememberSaveable { mutableStateOf(LectorTab.Home) }
        val all = SampleData.series
        val featured = all.first()

        Box(Modifier.fillMaxSize().background(LectorColors.Bg)) {
            AnimatedContent(
                tab, label = "tabs",
                transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(200)) }
            ) { t ->
                when (t) {
                    LectorTab.Home -> HomeScreen(
                        featured = featured, recent = all, mostRead = all.reversed(),
                        similar = all.shuffled(kotlin.random.Random(7)), similarTo = "Vagabond",
                        onOpen = { /* paso 3: página de serie */ }, onContinue = { /* paso 4: lector */ }
                    )
                    else -> PlaceholderScreen(t.label)
                }
            }
            Column(
                Modifier.align(Alignment.BottomCenter).navigationBarsPadding()
                    .padding(horizontal = 16.dp).padding(bottom = 8.dp)
            ) {
                MiniReader(featured, "Cap. 12 · pág. 5/20", onClick = { /* paso 4 */ })
                Spacer(Modifier.height(8.dp))
                FloatingNavBar(tab, onSelect = { tab = it })
            }
        }
    }
}

@Composable
private fun PlaceholderScreen(title: String) {
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(24.dp)) {
        LText(title, LectorType.Display)
        LText("Se construye en el siguiente paso", LectorType.Label, color = LectorColors.Muted)
    }
}
