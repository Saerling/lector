// Copiado de BitChord (https://github.com/kushagrasinghx/BitChord, GPL-3.0): app/src/main/java/com/music/bitchord/ui/components/UpdateAvailableDialog.kt
// Adaptado a Shiori: sin Haze ni RichText (el cristal usa el fondo opaco que BitChord pone con "reduceDynamicBlur"),
// estado y textos pasados por parámetros en vez de AppUpdateChecker/strings.xml, y animación de entrada estilo alerta iOS.
package com.example.lector.ui.bitchord

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.lector.ui.LectorColors
import com.example.lector.ui.Mov

/** UIAlertController's own metrics: fixed narrow width, 14pt corner, 44pt rows. */
internal val ALERT_WIDTH = 270.dp
internal val ALERT_CORNER = 14.dp
internal val ACTION_HEIGHT = 44.dp

/** The dim behind the alert. Flat on purpose — the card is the surface. */
internal val SCRIM_COLOR = Color.Black.copy(alpha = 0.28f)

private val DOWNLOAD_ROW_HEIGHT = 4.dp

/** How much of the card's height the release notes are allowed to fill before scrolling. */
private val NOTES_MAX_HEIGHT = 220.dp

/** Estado de la descarga, equivalente a AppUpdateChecker.DownloadState de BitChord. */
sealed interface EstadoDescarga {
    data object Disponible : EstadoDescarga
    data class Descargando(val fraccion: Float) : EstadoDescarga
    data object Instalando : EstadoDescarga
    data class Fallo(val mensaje: String) : EstadoDescarga
}

/** Una fila de acción del pie de la alerta. */
data class AccionAlerta(val texto: String, val destacada: Boolean, val activa: Boolean = true, val alPulsar: () -> Unit)

/**
 * Shaped like an iOS system alert: frosted card, hairline rules, full-width actions
 * stacked under the message rather than a Material button pair in the corner.
 * Download progress fills the hairline under the message.
 */
@Composable
fun UpdateAvailableDialog(
    titulo: String,
    cuerpo: String,
    detalle: String?,
    notas: String?,
    estado: EstadoDescarga,
    acciones: List<AccionAlerta>,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(ALERT_CORNER)
    // Entrada de alerta iOS: aparece desde un poco más grande y se asienta, mientras el velo se funde.
    val aparicion = remember { Animatable(if (Mov.reducido) 1f else 0f) }
    LaunchedEffect(Unit) { aparicion.animateTo(1f, tween(if (Mov.reducido) 0 else 220)) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer { alpha = aparicion.value }
            .background(SCRIM_COLOR)
            // Tapping the scrim reads the same as Remind Me Later.
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onDismiss,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .graphicsLayer {
                    val s = 1.12f - 0.12f * aparicion.value
                    scaleX = s; scaleY = s
                }
                .width(ALERT_WIDTH)
                .clip(shape)
                .background(LectorColors.Glass)
                // Swallows the tap before it reaches the scrim behind.
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = {},
                ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 19.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = titulo,
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp, fontWeight = FontWeight.W600),
                    color = LectorColors.Text,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = cuerpo,
                    modifier = Modifier.padding(top = 4.dp),
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 17.sp),
                    color = LectorColors.Text,
                    textAlign = TextAlign.Center,
                )
                if (!detalle.isNullOrBlank()) {
                    Text(
                        text = detalle,
                        modifier = Modifier.padding(top = 2.dp),
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = LectorColors.Muted,
                        textAlign = TextAlign.Center,
                    )
                }

                // The download's progress, drawn as a thin fill across a hairline track.
                val descargando = estado as? EstadoDescarga.Descargando
                if (descargando != null || estado is EstadoDescarga.Fallo) {
                    Box(
                        Modifier
                            .padding(top = 12.dp)
                            .fillMaxWidth()
                            .height(DOWNLOAD_ROW_HEIGHT)
                            .clip(RoundedCornerShape(DOWNLOAD_ROW_HEIGHT / 2))
                            .background(LectorColors.Text.copy(alpha = 0.14f)),
                    ) {
                        if (descargando != null && descargando.fraccion > 0f) {
                            Box(
                                Modifier
                                    .fillMaxWidth(descargando.fraccion)
                                    .height(DOWNLOAD_ROW_HEIGHT)
                                    .clip(RoundedCornerShape(DOWNLOAD_ROW_HEIGHT / 2))
                                    .background(MaterialTheme.colorScheme.primary),
                            )
                        }
                    }
                }
                if (estado is EstadoDescarga.Fallo) {
                    Text(
                        text = estado.mensaje,
                        modifier = Modifier.padding(top = 6.dp),
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = LectorColors.Error,
                        textAlign = TextAlign.Center,
                    )
                }

                if (!notas.isNullOrBlank()) {
                    AlertRule(modifier = Modifier.padding(top = 12.dp))
                    Text(
                        text = "Novedades",
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp, fontWeight = FontWeight.W600),
                        color = LectorColors.Text.copy(alpha = 0.6f),
                        textAlign = TextAlign.Start,
                    )
                    Box(
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .fillMaxWidth()
                            .heightIn(max = NOTES_MAX_HEIGHT)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        Text(
                            text = notas,
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 18.sp),
                            color = LectorColors.Text,
                        )
                    }
                }
            }

            acciones.forEach { a ->
                AlertRule()
                AlertAction(label = a.texto, emphasised = a.destacada, onClick = a.alPulsar, enabled = a.activa)
            }
        }
    }
}

/**
 * Full-bleed action row. Tinted rather than filled, so the two read as equals
 * in weight and only the font differentiates the default action.
 */
@Composable
internal fun AlertAction(
    label: String,
    emphasised: Boolean,
    onClick: () -> Unit,
    enabled: Boolean = true,
    destructive: Boolean = false,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(ACTION_HEIGHT)
            // iOS washes the whole row instead of drawing a ripple inside it.
            .background(if (pressed) LectorColors.Text.copy(alpha = 0.09f) else Color.Transparent)
            .clickable(enabled = enabled, indication = null, interactionSource = interactionSource, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontSize = 17.sp,
                fontWeight = if (emphasised) FontWeight.W600 else FontWeight.W400,
            ),
            color = (if (destructive) LectorColors.Error else MaterialTheme.colorScheme.primary)
                .copy(alpha = if (enabled) 1f else 0.4f),
        )
    }
}

/** Hairline separator — HorizontalDivider's 1dp reads as a bar at this scale. */
@Composable
internal fun AlertRule(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(0.5.dp)
            .background(LectorColors.Text.copy(alpha = 0.14f)),
    )
}
