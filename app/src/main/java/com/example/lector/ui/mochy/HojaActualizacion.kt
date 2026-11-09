// Hoja de versión nueva al estilo de Mochy Music (github.com/realidkroo/Mochy-Music, GPL-3.0): su PopupSheet
// (ui/component/PopupSheet.kt), la cabecera de PlaylistSheetKit.kt (SheetHeader: portada redondeada + título + segunda
// línea), filas sobre MenuRowSurface y los botones en píldora de sus hojas, todo en Inter. Recibe lo mismo que la
// alerta de BitChord (ui/bitchord/UpdateAvailableDialog.kt), así DialogoActualizacion elige una u otra.
package com.example.lector.ui.mochy

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.example.lector.ui.LectorColors
import com.example.lector.ui.LogoApp
import com.example.lector.ui.Mov
import com.example.lector.ui.SuaveShape
import com.example.lector.ui.bitchord.AccionAlerta
import com.example.lector.ui.bitchord.EstadoDescarga
import com.example.lector.ui.mochy.sheet.MenuContent
import com.example.lector.ui.mochy.sheet.MenuRowSurface
import com.example.lector.ui.mochy.sheet.PopupSheet

private val EstiloTitulo = TextStyle(fontFamily = InterFontFamily, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 26.sp, letterSpacing = (-0.03).em)
private val EstiloSub = TextStyle(fontFamily = InterFontFamily, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 17.sp)
private val EstiloCuerpo = TextStyle(fontFamily = InterFontFamily, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp)
private val EstiloEtiqueta = TextStyle(fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, letterSpacing = (-0.01).em)
private val EstiloBoton = TextStyle(fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, letterSpacing = (-0.01).em, textAlign = TextAlign.Center)

/**
 * La hoja: logo y título arriba, el estado debajo, las novedades en su tarjeta (con scroll propio) y las acciones en
 * píldoras a todo lo ancho. Mientras descarga no se puede quitar (si se arrastra fuera, vuelve).
 */
@Composable
fun HojaActualizacion(
    titulo: String,
    cuerpo: String,
    detalle: String?,
    notas: String?,
    estado: EstadoDescarga,
    acciones: List<AccionAlerta>,
    onDismiss: () -> Unit,
) {
    var vuelta by remember { mutableIntStateOf(0) }
    val descargando = estado is EstadoDescarga.Descargando
    key(vuelta) {
        PopupSheet(onClosed = { if (descargando) vuelta++ else onDismiss() }) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Box(Modifier.size(60.dp).clip(SuaveShape(16.dp)).background(MenuRowSurface), Alignment.Center) {
                    LogoApp(Modifier.size(44.dp), conFondo = false)
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(titulo, style = EstiloTitulo, color = MenuContent, maxLines = 2)
                    if (!detalle.isNullOrBlank()) Text(detalle, style = EstiloSub, color = MenuContent.copy(alpha = 0.6f), modifier = Modifier.padding(top = 2.dp))
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(
                cuerpo, style = EstiloCuerpo,
                color = if (estado is EstadoDescarga.Fallo) LectorColors.Error else MenuContent.copy(alpha = 0.78f),
            )
            if (estado is EstadoDescarga.Fallo) {
                Text(estado.mensaje, style = EstiloSub, color = MenuContent.copy(alpha = 0.6f), modifier = Modifier.padding(top = 4.dp))
            }
            // El avance llena una cápsula fina, como la barra de progreso del reproductor de Mochy.
            AnimatedVisibility(
                visible = descargando || estado is EstadoDescarga.Instalando,
                enter = expandVertically(tween(220)) + fadeIn(tween(180)),
                exit = shrinkVertically(tween(200)) + fadeOut(tween(120)),
            ) {
                val objetivo = (estado as? EstadoDescarga.Descargando)?.fraccion ?: 1f
                val f by animateFloatAsState(objetivo, if (Mov.reducido) tween(0) else tween(300), label = "avanceAct")
                Box(Modifier.padding(top = 14.dp).fillMaxWidth().height(6.dp).clip(CircleShape).background(MenuRowSurface)) {
                    Box(Modifier.fillMaxWidth(f.coerceIn(0f, 1f)).fillMaxHeight().clip(CircleShape).background(MenuContent))
                }
            }
            if (!notas.isNullOrBlank()) {
                Spacer(Modifier.height(SheetHeaderGap))
                Column(
                    Modifier.fillMaxWidth().clip(SuaveShape(20.dp)).background(MenuRowSurface)
                        .heightIn(max = 240.dp).verticalScroll(rememberScrollState()).padding(16.dp),
                ) {
                    Text("Novedades", style = EstiloEtiqueta, color = MenuContent.copy(alpha = 0.6f))
                    Spacer(Modifier.height(8.dp))
                    Text(limpiarNotas(notas), style = EstiloCuerpo, color = MenuContent)
                }
            }
            Spacer(Modifier.height(SheetHeaderGap))
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                acciones.forEach { a -> BotonPildora(a) }
            }
        }
    }
}

/** Píldora de 52 dp a todo lo ancho: la destacada en el color del texto (blanca en oscuro), la otra sobre la fila. */
@Composable
private fun BotonPildora(a: AccionAlerta) {
    val src = remember { MutableInteractionSource() }
    val fondo = if (a.destacada) MenuContent else MenuRowSurface
    val tinta = if (a.destacada) (if (MenuContent.red > 0.5f) Color.Black else Color.White) else MenuContent
    Box(
        Modifier.fillMaxWidth().height(52.dp).pressBounce(src, pressedScale = 0.96f)
            .graphicsLayer { alpha = if (a.activa) 1f else 0.5f }.clip(CircleShape).background(fondo)
            .clickable(src, null, enabled = a.activa, onClick = a.alPulsar),
        Alignment.Center,
    ) { Text(a.texto, style = EstiloBoton, color = tinta) }
}

/** Las notas vienen en Markdown de GitHub: se quitan las marcas más comunes para leerlas como texto. */
private fun limpiarNotas(md: String): String = md.lines().joinToString("\n") { l ->
    l.trimEnd().replace(Regex("^#{1,6}\\s*"), "").replace(Regex("^\\s*[-*]\\s+"), "• ").replace("**", "").replace("__", "").replace("`", "")
}.trim()
