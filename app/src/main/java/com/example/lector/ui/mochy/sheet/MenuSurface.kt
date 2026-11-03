// Copiado de Mochy Music (github.com/realidkroo/Mochy-Music, GPL-3.0): app/src/main/kotlin/com/mochyy/mochymusic/ui/player/menu/MenuSheet.kt
package com.example.lector.ui.mochy.sheet

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.lector.ui.LectorColors
import com.example.lector.ui.TemaActual

// Mochy es solo oscuro (MenuSurface = #0B0B0C, MenuRowSurface = #161619). Shiori tiene tema claro: ahí se usan los
// grises claros equivalentes de LectorColors.
val MenuSurface: Color get() = if (TemaActual.oscuro) Color(0xFF0B0B0C) else LectorColors.Hoja
val MenuRowSurface: Color get() = if (TemaActual.oscuro) Color(0xFF161619) else LectorColors.HojaFila

/** El texto y los iconos de las filas: blanco en Mochy. */
val MenuContent: Color get() = if (TemaActual.oscuro) Color.White else LectorColors.Text

/** How big a row's icon is drawn. There's no disc behind it any more, so the glyph itself is the marker. */
internal val SheetIconSize = 20.dp
