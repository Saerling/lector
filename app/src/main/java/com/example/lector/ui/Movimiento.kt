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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
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

/** Colores que salen de una portada: un fondo muy oscuro del mismo tono y un acento claro. */
class TemaPortada(val fondo: Color, val acento: Color)

/** Saca el tono dominante de una portada. Si es casi gris (blanco y negro) devuelve null. */
fun temaDe(img: ImageBitmap): TemaPortada? = runCatching {
    val b = android.graphics.Bitmap.createScaledBitmap(img.asAndroidBitmap(), 24, 24, true)
    val hsv = FloatArray(3)
    var sx = 0.0; var sy = 0.0; var ss = 0.0; var sw = 0.0
    for (x in 0 until 24) for (y in 0 until 24) {
        android.graphics.Color.colorToHSV(b.getPixel(x, y), hsv)
        val w = (hsv[1] * hsv[2]).toDouble() + 0.001      // pesan más los colores vivos
        val ang = Math.toRadians(hsv[0].toDouble())
        sx += kotlin.math.cos(ang) * w; sy += kotlin.math.sin(ang) * w
        ss += hsv[1] * w; sw += w
    }
    val sat = (ss / sw).toFloat()
    if (sat < 0.18f) return@runCatching null
    var h = Math.toDegrees(kotlin.math.atan2(sy, sx)).toFloat()
    if (h < 0f) h += 360f
    TemaPortada(
        fondo = Color.hsv(h, sat.coerceIn(0.45f, 0.9f), 0.20f),
        acento = Color.hsv(h, (sat * 0.7f).coerceIn(0.30f, 0.65f), 1f),
    )
}.getOrNull()

/**
 * Barra superior que aparece al bajar: el fondo se tiñe y el título entra desde abajo.
 * Lee el scroll dentro de graphicsLayer, así no obliga a recomponer toda la pantalla en cada fotograma.
 */
@Composable
fun BarraSuperior(
    scroll: () -> Float, titulo: String, onBack: () -> Unit, modifier: Modifier = Modifier,
    fondo: Color = Color.Black, trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    Box(modifier.fillMaxWidth()) {
        Box(
            Modifier.matchParentSize()
                .graphicsLayer { alpha = (scroll() / 380f).coerceIn(0f, 1f) }
                .background(Brush.verticalGradient(listOf(fondo, fondo.copy(alpha = 0.94f))))
        )
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(44.dp).pressable(onBack).clip(CircleShape).background(Color(0x66000000)),
                Alignment.Center
            ) { Icon(Icons.Rounded.ArrowBack, "Atrás", tint = LectorColors.Text) }
            Spacer(Modifier.width(14.dp))
            LText(
                titulo, LectorType.Display.copy(fontSize = 24.sp, letterSpacing = (-0.5).sp),
                Modifier.weight(1f).graphicsLayer {
                    val a = ((scroll() - 320f) / 120f).coerceIn(0f, 1f)
                    alpha = a
                    translationY = (1f - a) * 14f
                },
                maxLines = 1
            )
            trailing?.invoke(this)
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
