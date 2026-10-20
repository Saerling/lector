package com.example.lector.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.lector.Tri
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

/** Una acción de la barra flotante de selección (Marcar leído, Descargar, Borrar…). */
class AccionBarra(val icono: ImageVector, val texto: String, val alPulsar: () -> Unit)

/** Barra flotante de acciones para la selección múltiple (como la de Komikku). Aparece desde abajo. */
@Composable
fun BarraAcciones(visible: Boolean, acciones: List<AccionBarra>, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = visible, modifier = modifier,
        enter = slideInVertically(tween(260, easing = EnfasisEasing)) { it } + fadeIn(tween(200)),
        exit = slideOutVertically(tween(200)) { it } + fadeOut(tween(150)),
    ) {
        Row(
            Modifier.navigationBarsPadding().padding(horizontal = 12.dp, vertical = 10.dp).fillMaxWidth()
                .clip(SuaveShape(28.dp)).background(LectorColors.Glass)
                .border(1.dp, LectorColors.GlassBorderBrush, SuaveShape(28.dp))
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 6.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            acciones.forEach { a ->
                Column(
                    Modifier.widthIn(min = 64.dp).pressable(a.alPulsar).padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(a.icono, a.texto, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(2.dp))
                    LText(a.texto, LectorType.Label.copy(fontSize = 11.sp), maxLines = 1, color = LectorColors.Text)
                }
            }
        }
    }
}

/** Cabecera durante la selección: «N seleccionados», cerrar, seleccionar todo e invertir. */
@Composable
fun CabeceraSeleccion(cantidad: Int, onCerrar: () -> Unit, onTodo: () -> Unit, onInvertir: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BotonRedondoTema(Icons.Rounded.Close, "Terminar selección", onCerrar)
        Spacer(Modifier.width(12.dp))
        Odometro("$cantidad", LectorType.Display.copy(fontSize = 26.sp))
        LText(if (cantidad == 1) " seleccionado" else " seleccionados", LectorType.Section, Modifier.weight(1f), maxLines = 1)
        ChipSel("Todo", false, onClick = onTodo)
        Spacer(Modifier.width(8.dp))
        ChipSel("Invertir", false, onClick = onInvertir)
    }
}

/** Botón redondo con el fondo de píldora del tema (cabeceras de las pantallas). */
@Composable
fun BotonRedondoTema(icono: ImageVector, desc: String, onClick: () -> Unit, modifier: Modifier = Modifier, tam: Dp = 44.dp) {
    Box(modifier.size(tam).pressable(onClick).clip(CircleShape).background(LectorColors.PillFill), Alignment.Center) {
        Icon(icono, desc, tint = LectorColors.Text)
    }
}

/** Mensaje centrado para pantallas o listas vacías, con una acción opcional. */
@Composable
fun MensajeVacio(texto: String, accion: String? = null, onAccion: (() -> Unit)? = null, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().padding(horizontal = 40.dp).padding(bottom = 120.dp),
        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(texto, color = LectorColors.Muted, style = LectorType.Body, textAlign = TextAlign.Center)
        if (accion != null && onAccion != null) {
            Spacer(Modifier.height(16.dp))
            PillButton(accion, Icons.Rounded.KeyboardArrowRight, onAccion)
        }
    }
}

/** Fila de filtro de tres estados (□ ignorar · ✓ incluir · ✕ excluir), como los filtros de Komikku. */
@Composable
fun FilaTri(titulo: String, estado: Tri, onCambio: (Tri) -> Unit) {
    val acento = MaterialTheme.colorScheme.primary
    Row(
        Modifier.fillMaxWidth().resaltable { onCambio(estado.siguiente()) }.padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(24.dp).clip(SuaveShape(7.dp))
                .background(
                    when (estado) {
                        Tri.IGNORAR -> Color.Transparent
                        Tri.INCLUIR -> acento
                        Tri.EXCLUIR -> Color(0xFFE57373)
                    },
                )
                .border(2.dp, if (estado == Tri.IGNORAR) LectorColors.Muted else Color.Transparent, SuaveShape(7.dp)),
            Alignment.Center,
        ) {
            when (estado) {
                Tri.INCLUIR -> LText("✓", LectorType.Body, color = com.example.lector.textoSobre(acento))
                Tri.EXCLUIR -> LText("✕", LectorType.Body, color = Color.White)
                Tri.IGNORAR -> {}
            }
        }
        Spacer(Modifier.width(18.dp))
        LText(titulo, LectorType.Body.copy(fontSize = 16.sp), Modifier.weight(1f))
    }
}

/** Fila de orden: marca la opción elegida con una flecha ↑/↓ según el sentido (tocar otra vez invierte). */
@Composable
fun FilaOrden(titulo: String, elegida: Boolean, ascendente: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().resaltable(onClick = onClick).padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(24.dp), Alignment.Center) {
            if (elegida) LText(if (ascendente) "↑" else "↓", LectorType.Section.copy(fontSize = 18.sp), color = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.width(18.dp))
        LText(titulo, LectorType.Body.copy(fontSize = 16.sp), Modifier.weight(1f), color = if (elegida) MaterialTheme.colorScheme.primary else LectorColors.Text)
    }
}

/** Pestañas en píldora dentro de una hoja (Filtro / Ordenar / Mostrar…). */
@Composable
fun PestanasPildora(titulos: List<String>, elegida: Int, onElegir: (Int) -> Unit, modifier: Modifier = Modifier) {
    val acento = MaterialTheme.colorScheme.primary
    Row(
        modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).clip(CircleShape)
            .background(LectorColors.CardHigh).padding(4.dp),
    ) {
        titulos.forEachIndexed { i, t ->
            Box(
                Modifier.weight(1f).height(38.dp).clip(CircleShape)
                    .background(if (i == elegida) acento.copy(alpha = 0.22f) else Color.Transparent)
                    .pressable { onElegir(i) },
                Alignment.Center,
            ) { LText(t, LectorType.Body, color = if (i == elegida) acento else LectorColors.Text, maxLines = 1) }
        }
    }
}


/** Cuántas hojas inferiores hay abiertas: mientras haya alguna, la app de detrás retrocede (ver MainActivity). */
object Hojas {
    var abiertas by mutableIntStateOf(0)
}

/**
 * Hoja inferior al estilo de musicfy: fondo #0B0B0C con esquinas de 28 dp, asa gris y la app de detrás se
 * aleja un poco (se encoge y redondea) mientras está abierta.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HojaLector(onCerrar: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val estado = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    DisposableEffect(Unit) {
        Hojas.abiertas++
        onDispose { Hojas.abiertas-- }
    }
    ModalBottomSheet(
        onDismissRequest = onCerrar, sheetState = estado,
        containerColor = LectorColors.Hoja, contentColor = LectorColors.Text,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        scrimColor = Color.Black.copy(alpha = 0.45f),
        dragHandle = {
            Box(Modifier.padding(top = 10.dp, bottom = 14.dp).size(36.dp, 4.dp).clip(CircleShape).background(LectorColors.Muted.copy(alpha = 0.5f)))
        },
        content = content,
    )
}
