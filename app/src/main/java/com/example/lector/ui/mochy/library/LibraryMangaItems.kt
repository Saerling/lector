// Copiado de Mochy Music (github.com/realidkroo/Mochy-Music, GPL-3.0): app/src/main/kotlin/com/mochyy/mochymusic/ui/screens/library/LibraryGridScreen.kt
// (LibraryGridTile) y app/src/main/kotlin/com/mochyy/mochymusic/ui/screens/library/LibraryDesign.kt (LibraryListRow)
//
// Adaptado a Shiori: la baldosa es una portada de manga 2:3 con sus insignias, el botón de continuar, la selección
// múltiple y las cuatro vistas (cómoda, compacta, solo portada y lista). La portada abre el manga con origenTransicion().

package com.example.lector.ui.mochy.library

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.lector.Almacen
import com.example.lector.Comic
import com.example.lector.Vista
import com.example.lector.ext.bandera
import com.example.lector.progresoDe
import com.example.lector.textoSobre
import com.example.lector.ui.Aspecto
import com.example.lector.ui.ComicCover
import com.example.lector.ui.Mov
import com.example.lector.ui.Muelles
import com.example.lector.ui.SuaveShape
import com.example.lector.ui.mochy.InterFontFamily
import com.example.lector.ui.origenTransicion
import com.example.lector.ui.press3D
import com.example.lector.ui.terminado

// ---------------------------------------------------------------------------------------------
// Insignias
// ---------------------------------------------------------------------------------------------

@Composable
private fun LibraryBadges(c: Comic, almacen: Almacen, descargas: Int, modifier: Modifier) {
    val acento = MaterialTheme.colorScheme.primary
    val sobre = textoSobre(acento)
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        if (!c.enLinea && almacen.insigniaLocal) LibraryBadge("Local", Color(0xE62E7D6B), Color.White)
        if (c.enLinea && almacen.insigniaDescargas && descargas > 0) LibraryBadge("↓ $descargas", Color(0xB8000000), Color.White)
        if (c.enLinea && almacen.insigniaSinLeer && c.sinLeer > 0) LibraryBadge("${c.sinLeer}", acento, sobre)
        if (almacen.insigniaProgreso && !c.enLinea) {
            val p = (progresoDe(c) * 100).toInt()
            if (c.terminado()) LibraryBadge("✓", acento, sobre)
            else if (p in 1..99) LibraryBadge("$p%", acento, sobre)
        }
    }
}

@Composable
private fun LibraryBadge(texto: String, fondo: Color, tinta: Color) {
    Box(Modifier.clip(CircleShape).background(fondo).padding(horizontal = 7.dp, vertical = 2.dp), Alignment.Center) {
        Text(texto, color = tinta, fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.SemiBold, fontFamily = InterFontFamily, maxLines = 1)
    }
}

/** El PlayPill de la página de lista hecho círculo: continuar leyendo. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LibraryContinueButton(onClick: () -> Unit, modifier: Modifier, small: Boolean = true) {
    val acento = MaterialTheme.colorScheme.primary
    Box(
        modifier
            .size(if (small) 32.dp else 30.dp)
            .clip(CircleShape)
            .background(if (small) acento else acento.copy(alpha = 0.2f))
            .combinedClickable(interactionSource = null, indication = null, onClick = onClick),
        Alignment.Center,
    ) {
        Icon(
            Icons.Rounded.PlayArrow, "Continuar leyendo",
            tint = if (small) textoSobre(acento) else acento, modifier = Modifier.size(20.dp),
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Rejilla
// ---------------------------------------------------------------------------------------------

/**
 * a tile in Home's card language: the cover's corner, the soft press highlight instead of a square ripple,
 * and the cards' type. Shiori: portada 2:3, insignias, botón de continuar y selección con muelle.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryGridTile(
    c: Comic, almacen: Almacen, vista: Vista, seleccionado: Boolean, descargas: Int, lang: String?,
    onClick: () -> Unit, onLargo: () -> Unit, onContinuar: () -> Unit,
) {
    val acento = MaterialTheme.colorScheme.primary
    val haptic = LocalHapticFeedback.current
    // Selección con muelle: la portada redondea más sus esquinas, se encoge un poco y aparece el borde de acento.
    val s by animateFloatAsState(if (seleccionado) 1f else 0f, if (Mov.reducido) snap() else Muelles.espacial(), label = "selTarjeta")
    val forma = SuaveShape(Aspecto.radioPortada.dp + 10.dp * s) // redondeo de Personalización
    val comoda = vista == Vista.CUADRICULA_COMODA
    val compacta = vista == Vista.CUADRICULA_COMPACTA
    val progreso = progresoDe(c)
    Column(
        Modifier
            .origenTransicion()
            .press3D(5f, 0.97f)
            .combinedClickable(
                interactionSource = null,
                indication = HomeCardHighlight,
                onClick = onClick,
                onLongClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onLargo() },
            )
            .graphicsLayer { val e = 1f - 0.06f * s; scaleX = e; scaleY = e },
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(forma)
                .background(LibraryColors.TileHigh)
                .then(if (s > 0.01f) Modifier.border((3f * s).dp, acento, forma) else Modifier),
        ) {
            ComicCover(c, almacen, Modifier.fillMaxSize(), forma)
            if (s > 0.01f) Box(Modifier.fillMaxSize().background(acento.copy(alpha = 0.26f * s)))
            LibraryBadges(c, almacen, descargas, Modifier.align(Alignment.TopStart).padding(7.dp))
            if (lang != null && almacen.insigniaIdioma) {
                Box(
                    Modifier.align(Alignment.TopEnd).padding(6.dp).size(24.dp).clip(CircleShape).background(Color(0x8C000000)),
                    Alignment.Center,
                ) { Text(bandera(lang), fontSize = 12.sp, maxLines = 1) }
            }
            if (compacta) {
                Column(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xE6000000))))
                        .padding(start = 10.dp, end = 10.dp, top = 34.dp, bottom = 11.dp),
                ) {
                    Text(c.titulo, style = TrackTitleStyle.copy(fontSize = 13.sp, lineHeight = 16.sp), color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (almacen.mostrarPagina) Text(
                        c.etiquetaProgreso(), style = TrackSubtitleStyle, color = Color.White.copy(alpha = 0.7f),
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (progreso > 0f && progreso < 1f) {
                Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(3.dp).background(Color(0x66000000))) {
                    Box(Modifier.fillMaxWidth(progreso.coerceIn(0f, 1f)).fillMaxHeight().background(acento))
                }
            }
            val puedeSeguir = almacen.botonContinuar && !seleccionado && !c.terminado() && (c.enLinea && c.paginas > 0 || !c.enLinea)
            if (puedeSeguir) {
                LibraryContinueButton(
                    onContinuar,
                    Modifier.align(if (compacta) Alignment.TopEnd else Alignment.BottomEnd).padding(8.dp)
                        .padding(top = if (compacta && lang != null && almacen.insigniaIdioma) 28.dp else 0.dp, bottom = if (!compacta) 3.dp else 0.dp),
                )
            }
            if (s > 0.01f) {
                Box(
                    Modifier.align(Alignment.Center).size(40.dp).graphicsLayer { scaleX = s; scaleY = s; alpha = s.coerceIn(0f, 1f) }
                        .clip(CircleShape).background(acento),
                    Alignment.Center,
                ) { Icon(Icons.Rounded.Check, null, tint = textoSobre(acento), modifier = Modifier.size(22.dp)) }
            }
        }
        if (comoda) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = c.titulo,
                style = TrackTitleStyle,
                color = LibraryColors.Title,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (almacen.mostrarPagina) {
                Text(
                    text = c.etiquetaProgreso(),
                    style = TrackSubtitleStyle,
                    color = LibraryColors.Subtitle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Lista
// ---------------------------------------------------------------------------------------------

/**
 * A row in the lists, drawn exactly like Home's track rows: the same 54dp cover and corners, the same type,
 * no pill behind it - only the soft highlight while pressed. Shiori: insignias y continuar al final.
 */
@Composable
fun LibraryListRow(
    c: Comic, almacen: Almacen, seleccionado: Boolean, descargas: Int, lang: String?,
    onClick: () -> Unit, onLargo: () -> Unit, onContinuar: () -> Unit,
) {
    val acento = MaterialTheme.colorScheme.primary
    val haptic = LocalHapticFeedback.current
    val s by animateFloatAsState(if (seleccionado) 1f else 0f, if (Mov.reducido) snap() else Muelles.espacial(), label = "selFila")
    val forma = SuaveShape(LibraryRowCoverCorner)
    val progreso = progresoDe(c)
    val linea = listOfNotNull(
        if (lang != null && almacen.insigniaIdioma) bandera(lang) else null,
        c.autor?.takeIf { it.isNotBlank() }, if (almacen.mostrarPagina) c.etiquetaProgreso() else null,
    ).joinToString(" · ")
    HomeTrackRow(
        title = c.titulo,
        subtitle = linea,
        onClick = onClick,
        onLongClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onLargo() },
        selected = s,
        modifier = Modifier.fillMaxWidth().padding(horizontal = SearchHorizontalPadding, vertical = 3.dp),
        cover = {
            Box(Modifier.origenTransicion().size(LibraryRowCoverSize).clip(forma).background(LibraryColors.TileHigh)) {
                ComicCover(c, almacen, Modifier.fillMaxSize(), forma)
                if (progreso > 0f && progreso < 1f) {
                    Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(3.dp).background(Color(0x66000000))) {
                        Box(Modifier.fillMaxWidth(progreso.coerceIn(0f, 1f)).fillMaxHeight().background(acento))
                    }
                }
                if (s > 0.01f) Box(
                    Modifier.fillMaxSize().background(acento.copy(alpha = 0.55f * s)),
                    Alignment.Center,
                ) { Icon(Icons.Rounded.Check, null, tint = textoSobre(acento), modifier = Modifier.graphicsLayer { scaleX = s; scaleY = s }) }
            }
        },
        trailing = {
            LibraryBadges(c, almacen, descargas, Modifier.padding(end = 2.dp))
            if (almacen.botonContinuar && !seleccionado && !c.terminado()) {
                Spacer(Modifier.width(8.dp))
                LibraryContinueButton(onContinuar, Modifier, small = false)
            }
        },
    )
}
