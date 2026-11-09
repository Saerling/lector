// Copiado de BitChord (github.com/kushagrasinghx/BitChord, GPL-3.0): app/src/main/java/com/music/bitchord/ui/components/DownloadManagerSheet.kt
// Adaptado a Shiori: lee la cola de [Descargas] (cola / progreso / errores) en vez de DownloadSession, el indicador es
// una píldora flotante en lugar de un botón de la barra, y la lista va en una HojaLector con un acceso a la cola completa.
package com.example.lector.ui.bitchord

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.lector.Descargas
import com.example.lector.TareaDescarga
import com.example.lector.ui.HojaLector
import com.example.lector.ui.LectorColors
import com.example.lector.ui.LectorType

/** Las que siguen pendientes (en curso o esperando); las fallidas no cuentan para el indicador. */
private fun pendientes(): List<TareaDescarga> = Descargas.cola.filter { it.clave !in Descargas.errores }

/**
 * The download indicator — en Shiori, una píldora flotante arriba.
 *
 * Absent until something is actually downloading, and absent again once the queue is quiet.
 *
 * It draws the batch's overall progress rather than the running chapter's: from here the interesting
 * number is how much of the *batch* is left, and a ring that restarts from zero forty times says
 * nothing about that.
 */
@Composable
fun TopBarDownloadButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val activas = pendientes()
    val visible = activas.isNotEmpty()
    // Fracción del lote: los capítulos en curso aportan lo que llevan; los que esperan, nada.
    val fraction = if (activas.isEmpty()) 0f else activas.sumOf { (Descargas.progreso[it.clave] ?: 0f).toDouble() }.toFloat() / activas.size
    // Animated, because the fraction lands in steps — one chapter at a time, plus
    // whatever the running one reports — and a ring that jumps reads as a stutter.
    val progress by animateFloatAsState(fraction, tween(300), label = "downloadRingProgress")
    val tint = LectorColors.Text

    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = scaleIn(tween(260), initialScale = 0.6f) + fadeIn(tween(160, delayMillis = 60)),
        exit = fadeOut(tween(120)) + scaleOut(tween(220, delayMillis = 60), targetScale = 0.6f),
    ) {
        Row(
            Modifier
                .shadow(10.dp, CircleShape, clip = false)
                .clip(CircleShape)
                .background(LectorColors.Glass)
                .border(0.5.dp, LectorColors.GlassBorder, CircleShape)
                .clickable(onClick = onClick)
                .padding(start = 6.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    // Never quite zero: a ring pinned at nothing reads as
                    // stalled where the first sliver reads as starting.
                    progress = { progress.coerceAtLeast(0.02f) },
                    modifier = Modifier.size(RING_SIZE),
                    color = tint,
                    trackColor = tint.copy(alpha = 0.22f),
                    strokeWidth = 2.dp,
                    strokeCap = StrokeCap.Round,
                    gapSize = 0.dp,
                )
                // A bare arrow inside the ring: the ring is already saying "downloading".
                Icon(Icons.Rounded.KeyboardArrowDown, "Descargas: ${(fraction * 100).toInt()} %", tint = tint, modifier = Modifier.size(GLYPH_IN_RING))
            }
            Spacer(Modifier.width(8.dp))
            Text("${activas.size}", style = LectorType.Label, color = tint)
        }
    }
}

/**
 * The list behind that indicator: every chapter still going, and a way out of each.
 *
 * Rows carry the title and the chapter because a key is not how anybody remembers a chapter.
 * Ver la cola completa abre la pantalla de descargas de siempre (pausa, reordenar, reintentar…).
 */
@Composable
fun DownloadManagerSheet(onCerrar: () -> Unit, onVerCola: () -> Unit) {
    val ctx = LocalContext.current
    val activas = pendientes()
    val fallidas = Descargas.cola.count { it.clave in Descargas.errores }
    HojaLector(onCerrar) {
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Descargas", style = LectorType.Section, color = LectorColors.Text)
                Text(
                    summary(activas, fallidas),
                    style = LectorType.Body,
                    color = if (fallidas > 0 && activas.isEmpty()) LectorColors.Error else LectorColors.Muted,
                )
            }
            if (activas.isNotEmpty()) {
                // Only what is still going: a failed row stays for the full queue to retry.
                TextButton(onClick = { activas.forEach { Descargas.cancelar(ctx, it.clave) } }) {
                    Text("Cancelar todo", color = LectorColors.Text)
                }
            }
        }
        HorizontalDivider(thickness = 0.5.dp, color = LectorColors.Linea)
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
            items(activas, key = { it.clave }) { t ->
                DownloadManagerRow(t, Descargas.progreso[t.clave]) { Descargas.cancelar(ctx, t.clave) }
            }
        }
        HorizontalDivider(thickness = 0.5.dp, color = LectorColors.Linea)
        Row(
            Modifier.fillMaxWidth().clickable { onCerrar(); onVerCola() }.padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Ver la cola completa", style = LectorType.Body, color = LectorColors.Text)
            Icon(Icons.Rounded.KeyboardArrowRight, null, tint = LectorColors.Muted)
        }
        Spacer(Modifier.height(12.dp))
    }
}

/** One chapter: what it is, and where it has got to. */
@Composable
private fun DownloadManagerRow(item: TareaDescarga, progress: Float?, onCancel: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(item.mangaTitulo.ifBlank { "Sin título" }, style = LectorType.Body, color = LectorColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(item.capNombre, style = LectorType.Label, color = LectorColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            when {
                progress == null -> RowStatus("En cola")
                else -> {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth().height(3.dp),
                        color = LectorColors.Text,
                        trackColor = LectorColors.Text.copy(alpha = 0.18f),
                        strokeCap = StrokeCap.Round,
                        gapSize = 0.dp,
                        drawStopIndicator = {},
                    )
                    Spacer(Modifier.height(3.dp))
                    RowStatus(if (progress > 0f) "Descargando ${(progress * 100).toInt()} %" else "Empezando…")
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        RowAction(Icons.Rounded.Close, "Cancelar", onCancel)
    }
}

@Composable
private fun RowStatus(text: String, color: Color = LectorColors.Muted) {
    Text(text, style = LectorType.Label, color = color, maxLines = 2, overflow = TextOverflow.Ellipsis)
}

@Composable
private fun RowAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier.size(36.dp).clip(CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, label, tint = LectorColors.Muted, modifier = Modifier.size(20.dp))
    }
}

/** The one line under the heading — counts rather than a percentage. */
private fun summary(activas: List<TareaDescarga>, fallidas: Int): String {
    val enCurso = activas.count { it.clave in Descargas.progreso }
    val esperando = activas.size - enCurso
    val parts = listOfNotNull(
        enCurso.takeIf { it > 0 }?.let { "$it descargando" },
        esperando.takeIf { it > 0 }?.let { "$it en cola" },
        fallidas.takeIf { it > 0 }?.let { "$it con error" },
    )
    return if (parts.isEmpty()) "Nada descargándose" else parts.joinToString(" · ")
}

/** The progress ring in the pill. */
private val RING_SIZE = 26.dp

/** The glyph inside that ring, small enough to leave the stroke clear. */
private val GLYPH_IN_RING = 15.dp
