package com.example.lector.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Página de ajustes a pantalla completa: botón atrás en píldora + título grande + contenido con scroll. */
@Composable
fun PaginaAjustes(titulo: String, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().background(LectorColors.Bg).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).pressable(onBack).clip(CircleShape).background(LectorColors.PillFill),
                Alignment.Center
            ) { Icon(Icons.Rounded.ArrowBack, "Atrás", tint = LectorColors.Text) }
            Spacer(Modifier.width(14.dp))
            LText(titulo, LectorType.Display.copy(fontSize = 28.sp), maxLines = 1)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 32.dp)) {
            content()
        }
    }
}

/** Fila estilo Komikku: icono con el color de acento, título y detalle. */
@Composable
fun FilaAjuste(icon: ImageVector?, titulo: String, detalle: String? = null, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().resaltable(onClick = onClick).padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(26.dp))
            Spacer(Modifier.width(22.dp))
        }
        Column(Modifier.weight(1f)) {
            LText(titulo, LectorType.Section.copy(fontSize = 17.sp))
            if (detalle != null) LText(detalle, LectorType.Label.copy(fontSize = 13.sp), color = LectorColors.Muted)
        }
    }
}

@Composable
fun FilaSwitch(titulo: String, detalle: String? = null, activado: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().resaltable { onChange(!activado) }.padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            LText(titulo, LectorType.Section.copy(fontSize = 17.sp))
            if (detalle != null) LText(detalle, LectorType.Label.copy(fontSize = 13.sp), color = LectorColors.Muted)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = activado, onCheckedChange = onChange)
    }
}

@Composable
fun FilaRadio(etiqueta: String, marcada: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().resaltable(onClick = onClick).padding(horizontal = 24.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = marcada, onClick = null)
        Spacer(Modifier.width(16.dp))
        LText(etiqueta, LectorType.Body.copy(fontSize = 15.sp))
    }
}

@Composable
fun TituloSeccion(texto: String) {
    LText(texto, LectorType.Body, Modifier.padding(horizontal = 24.dp, vertical = 12.dp), color = MaterialTheme.colorScheme.primary)
}

@Composable
fun Divisor() {
    Box(Modifier.fillMaxWidth().padding(vertical = 8.dp).height(1.dp).background(LectorColors.Linea))
}

/** Píldora de selección (columnas, etc.). */
@Composable
fun ChipSel(texto: String, seleccionado: Boolean, onClick: () -> Unit) {
    val acento = MaterialTheme.colorScheme.primary
    Box(
        Modifier.pressable(onClick).clip(CircleShape)
            .background(if (seleccionado) acento.copy(alpha = 0.22f) else Color.Transparent)
            .border(1.dp, if (seleccionado) acento else LectorColors.Linea, CircleShape)
            .padding(horizontal = 18.dp, vertical = 9.dp)
    ) { LText(texto, LectorType.Body, color = if (seleccionado) acento else LectorColors.Text) }
}

/** Etiqueta con borde de color (como los géneros de Komikku). */
@Composable
fun ChipBorde(texto: String, color: Color) {
    Box(
        Modifier.clip(SuaveShape(14.dp)).border(1.5.dp, color, SuaveShape(14.dp))
            .padding(horizontal = 16.dp, vertical = 9.dp)
    ) { LText(texto, LectorType.Body, color = color) }
}

@Composable
fun StatCard(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier.clip(SuaveShape(24.dp)).background(LectorColors.Card).padding(20.dp)) {
        LText(value, LectorType.Display.copy(fontSize = if (value.length > 6) 22.sp else 36.sp), maxLines = 1)
        Spacer(Modifier.height(4.dp))
        LText(label, LectorType.Mono, color = LectorColors.Muted)
    }
}
