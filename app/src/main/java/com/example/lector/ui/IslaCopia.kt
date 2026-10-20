package com.example.lector.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.lector.TareaCopia
import kotlinx.coroutines.delay

private val Verde = Color(0xFF5DD39E)
private val Ambar = Color(0xFFF2C14E)
private val Rojo = Color(0xFFEE5A52)

/**
 * Píldora pequeña arriba (bajo la barra de estado) con el avance de una copia de seguridad: un anillo y un «líquido»
 * que llena la píldora de izquierda a derecha. No tapa nada más que a sí misma. Al tocarla se despliega y enseña el
 * paso actual. Al terminar se queda unos segundos con el resultado: tocarla abre el informe.
 */
@Composable
fun IslaCopia(modifier: Modifier = Modifier) {
    val estado = TareaCopia.estado
    val informe = TareaCopia.informe
    val mostrarFin = estado == null && informe != null && !TareaCopia.informeVisto
    var abierta by remember { mutableStateOf(false) }

    // El resultado se va solo a los 8 s (sigue disponible desde la notificación).
    LaunchedEffect(informe, mostrarFin) {
        if (mostrarFin) { delay(8000); TareaCopia.informeVisto = true }
    }
    LaunchedEffect(estado == null) { if (estado == null) abierta = false }

    Box(modifier.fillMaxWidth().statusBarsPadding().padding(top = 6.dp), Alignment.TopCenter) {
        AnimatedVisibility(
            visible = estado != null || mostrarFin,
            enter = slideInVertically(spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow)) { -it * 2 } +
                fadeIn(tween(160)) + scaleIn(spring(dampingRatio = 0.72f), initialScale = 0.6f),
            exit = slideOutVertically(tween(260)) { -it * 2 } + fadeOut(tween(200)) + scaleOut(tween(260), targetScale = 0.7f),
        ) {
            val fraccion by animateFloatAsState(estado?.fraccion ?: 1f, spring(stiffness = Spring.StiffnessLow), label = "avanceCopia")
            val forma = CircleShape
            val liquido = LectorColors.Text.copy(alpha = 0.08f)
            Column(
                Modifier
                    .shadow(12.dp, androidx.compose.foundation.shape.RoundedCornerShape(22.dp), clip = false, ambientColor = Color.Black, spotColor = Color.Black)
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(22.dp))
                    .background(LectorColors.Aviso)
                    .drawBehind {
                        // El «líquido»: rellena la píldora según el avance (solo mientras trabaja).
                        if (estado != null) drawRect(liquido, size = Size(size.width * fraccion, size.height))
                    }
                    .drawWithContent { drawContent(); lineaCristal(22.dp.toPx()) }
                    .pressableCon(0.96f) {
                        if (estado != null) abierta = !abierta
                        else { TareaCopia.informeVisto = true; TareaCopia.verInforme = true }
                    }
                    .animateContentSize(spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow))
                    .padding(start = 8.dp, end = 14.dp, top = 6.dp, bottom = 6.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AnimatedContent(estado != null, transitionSpec = { (fadeIn(tween(200)) + scaleIn(initialScale = 0.6f)).togetherWith(fadeOut(tween(120))) }, label = "iconoCopia") { trabajando ->
                        if (trabajando) Anillo(fraccion)
                        else {
                            val (color, icono) = when {
                                informe?.conErrores == true -> Rojo to Icons.Rounded.Close
                                informe?.avisos?.isNotEmpty() == true -> Ambar to Icons.Rounded.Warning
                                else -> Verde to Icons.Rounded.Check
                            }
                            Box(Modifier.size(22.dp).clip(forma).background(color), Alignment.Center) {
                                Icon(icono, null, tint = Color.Black, modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    val texto = when {
                        estado != null -> estado.titulo
                        informe?.conErrores == true -> "${informe.titulo}: con errores"
                        else -> informe?.titulo?.let { "$it: listo" }.orEmpty()
                    }
                    LText(texto, LectorType.Body.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                    Spacer(Modifier.width(8.dp))
                    if (estado != null) Odometro("${(fraccion * 100).toInt()}%", LectorType.Label.copy(fontSize = 12.sp), color = LectorColors.Muted)
                    else LText("Ver", LectorType.Label.copy(fontSize = 12.sp), color = LectorColors.Muted)
                }
                if (abierta && estado != null) {
                    LText(
                        estado.detalle, LectorType.Label, Modifier.padding(start = 30.dp, top = 2.dp, bottom = 2.dp),
                        color = LectorColors.Muted, maxLines = 2,
                    )
                }
            }
        }
    }
}

@Composable
private fun Anillo(fraccion: Float) {
    val pista = LectorColors.Text.copy(alpha = 0.15f)
    val color = LectorColors.Text
    Box(
        Modifier.size(22.dp).drawBehind {
            val g = 2.5.dp.toPx()
            val o = Offset(g / 2, g / 2)
            val t = Size(size.width - g, size.height - g)
            drawArc(pista, 0f, 360f, false, o, t, style = Stroke(g))
            drawArc(color, -90f, 360f * fraccion.coerceAtLeast(0.03f), false, o, t, style = Stroke(g, cap = StrokeCap.Round))
        },
    )
}

/** Hoja con el informe de la última copia: lo que salió bien, avisos y errores. */
@Composable
fun InformeCopia() {
    val informe = TareaCopia.informe ?: return
    if (!TareaCopia.verInforme) return
    HojaLector({ TareaCopia.verInforme = false }) {
        Column(Modifier.fillMaxWidth().heightIn(max = 560.dp).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 28.dp)) {
            LText(informe.titulo, LectorType.Hero)
            LText(
                (if (informe.conErrores) "Terminó con errores" else "Terminó bien") + " · " + TareaCopia.duracion(informe.duracionMs),
                LectorType.Label, color = LectorColors.Muted,
            )
            Spacer(Modifier.height(16.dp))
            Seccion("Correcto", informe.bien, Verde, Icons.Rounded.Check)
            Seccion("Avisos", informe.avisos, Ambar, Icons.Rounded.Warning)
            Seccion("Errores", informe.errores, Rojo, Icons.Rounded.Close)
        }
    }
}

@Composable
private fun Seccion(titulo: String, lineas: List<String>, color: Color, icono: androidx.compose.ui.graphics.vector.ImageVector) {
    if (lineas.isEmpty()) return
    LText(titulo.uppercase(), LectorType.Label, Modifier.padding(top = 8.dp, bottom = 8.dp), color = LectorColors.Muted)
    Column(Modifier.fillMaxWidth().clip(SuaveShape(20.dp)).background(LectorColors.HojaFila).padding(vertical = 6.dp)) {
        lineas.forEach { l ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.Top) {
                Box(Modifier.padding(top = 1.dp).size(20.dp).clip(CircleShape).background(color.copy(alpha = 0.2f)), Alignment.Center) {
                    Icon(icono, null, tint = color, modifier = Modifier.size(13.dp))
                }
                Spacer(Modifier.width(12.dp))
                LText(l, LectorType.Body, Modifier.weight(1f))
            }
        }
    }
    Spacer(Modifier.height(10.dp))
}
