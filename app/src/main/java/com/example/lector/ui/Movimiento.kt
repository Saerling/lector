package com.example.lector.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.lector.textoSobre
import kotlinx.coroutines.delay

/**
 * Entrada escalonada (como las listas del video): cada elemento aparece con fundido, subida y un poco de escala,
 * uno tras otro. Solo ocurre la primera vez; al volver a hacer scroll ya no se repite.
 */
@Composable
fun Modifier.entrada(indice: Int, pasoMs: Int = 55): Modifier {
    var visible by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!visible) {
            delay((indice.coerceIn(0, 10) * pasoMs).toLong())
            visible = true
        }
    }
    val t by animateFloatAsState(
        if (visible) 1f else 0f,
        spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessLow), label = "entrada"
    )
    return this.graphicsLayer {
        alpha = t
        translationY = (1f - t) * 56f
        val s = 0.94f + 0.06f * t
        scaleX = s; scaleY = s
    }
}

/**
 * Barra superior que aparece al bajar: se oscurece el fondo y el título entra desde abajo.
 * Lee el scroll dentro de graphicsLayer, así no obliga a recomponer toda la pantalla en cada fotograma.
 */
@Composable
fun BarraSuperior(scroll: () -> Float, titulo: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth()) {
        Box(
            Modifier.matchParentSize()
                .graphicsLayer { alpha = (scroll() / 380f).coerceIn(0f, 1f) }
                .background(Brush.verticalGradient(listOf(Color.Black, Color.Black.copy(alpha = 0.94f))))
        )
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(44.dp).pressable(onBack).clip(CircleShape).background(LectorColors.PillFill),
                Alignment.Center
            ) { Icon(Icons.Rounded.ArrowBack, "Atrás", tint = LectorColors.Text) }
            Spacer(Modifier.width(14.dp))
            LText(
                titulo, LectorType.Section.copy(fontSize = 19.sp),
                Modifier.weight(1f).graphicsLayer {
                    val a = ((scroll() - 320f) / 120f).coerceIn(0f, 1f)
                    alpha = a
                    translationY = (1f - a) * 14f
                },
                maxLines = 1
            )
        }
    }
}

/** Botón "Reanudar / Leer" flotante, del color de acento. */
@Composable
fun BotonFlotante(texto: String, acento: Color, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.navigationBarsPadding().padding(24.dp)
            .pressable(onClick).clip(RoundedCornerShape(22.dp)).background(acento)
            .padding(horizontal = 26.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Rounded.PlayArrow, null, tint = textoSobre(acento), modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(10.dp))
        LText(texto, LectorType.Section.copy(fontSize = 18.sp), color = textoSobre(acento))
    }
}
