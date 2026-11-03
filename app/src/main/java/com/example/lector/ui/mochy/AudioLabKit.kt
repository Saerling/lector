// Copiado de Mochy Music (github.com/realidkroo/Mochy-Music, GPL-3.0): ui/screens/audiolab/AudioLabKit.kt
// (LabColors, LabCard, LabToggleRow) y LabBackButton de ui/screens/audiolab/AudioLabPage.kt.
// Shiori: los colores tienen además su equivalente en tema claro (en oscuro son los de Mochy), la preferencia
// «ocultar descripciones» es MochyPreferences.hideSettingsDescriptions y R.drawable.arrow_back_ios es [ArrowBackIos].

// shared pieces of every audio lab page (cards, stage, toggle rows, sliders, chips, segments) so the hub, equalizer and audio fx read as one set

package com.example.lector.ui.mochy

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.lector.ui.TemaActual

object LabColors {
    private val o get() = TemaActual.oscuro
    val Card: Color get() = if (o) Color(0xFF1C1C1E) else Color.White
    val Stage: Color get() = if (o) Color(0xFF0E0E10) else Color(0xFFF2F2F5)
    val StageLine: Color get() = if (o) Color(0xFF2E2E32) else Color(0xFFE0E0E5)
    val Row: Color get() = if (o) Color(0xFF26262A) else Color(0xFFEDEDF1)
    val RowSelected: Color get() = if (o) Color(0xFF34343A) else Color(0xFFE2E2E8)
    val Chip: Color get() = if (o) Color(0xFF232327) else Color(0xFFEDEDF1)
    val Primary: Color get() = com.example.lector.ui.LectorColors.Text
    val Secondary: Color get() = com.example.lector.ui.LectorColors.Muted
    val Faint: Color get() = if (o) Color(0xFF5C5C63) else Color(0xFFB0B0B8)
    val Ink: Color get() = if (o) Color(0xFF8A8A92) else Color(0xFF6B6B73)
    /** el círculo gris del botón atrás (#5E5E62 en Mochy) */
    val Back: Color get() = if (o) Color(0xFF5E5E62) else Color(0x16000000)
}

/** R.drawable.arrow_back_ios de Mochy: «M15,18L9,12l6,-6», trazo de 2 con puntas redondas */
val ArrowBackIos: ImageVector by lazy {
    ImageVector.Builder("arrow_back_ios", 24.dp, 24.dp, 24f, 24f).apply {
        path(
            fill = null,
            stroke = SolidColor(Color.White),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(15f, 18f)
            lineTo(9f, 12f)
            lineToRelative(6f, -6f)
        }
    }.build()
}

@Composable
fun LabCard(
    modifier: Modifier = Modifier,
    padding: Dp = 14.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(LabColors.Card)
            .padding(padding),
        content = content,
    )
}

@Composable
fun LabToggleRow(
    title: String,
    subtitle: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingDot: Boolean = false,
) {
    val hideDescriptions = MochyPreferences.hideSettingsDescriptions
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(horizontal = 6.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingDot) {
            val dot by animateColorAsState(if (checked) LabColors.Primary else LabColors.Faint, tween(200), label = "dot")
            Box(
                Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(dot)
            )
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                fontFamily = InterFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                color = if (enabled) LabColors.Primary else LabColors.Faint,
            )
            if (subtitle != null && !hideDescriptions) {
                Text(
                    text = subtitle,
                    fontFamily = InterFontFamily,
                    fontSize = 12.sp,
                    lineHeight = 15.sp,
                    color = if (enabled) LabColors.Secondary else LabColors.Faint,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        AppSwitch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

// the round grey back button, shared with the customize page
@Composable
fun LabBackButton(onBack: () -> Unit, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(LabColors.Back)
            .clickable(onClick = onBack),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = ArrowBackIos,
            contentDescription = "Atrás",
            tint = LabColors.Primary,
            modifier = Modifier.size(15.dp).offset(x = 2.dp),
        )
    }
}
