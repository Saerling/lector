// Copiado de Mochy Music (github.com/realidkroo/Mochy-Music, GPL-3.0): app/src/main/kotlin/com/mochyy/mochymusic/ui/component/Menu.kt
//
// The row style shared by every item menu.
//
// Despite the `Material3` names, nothing here is Material 3 any more: these rows render in the same
// language as the player's own action sheet — flat dark pills, a plain white icon, one line of text.
//
//   * `description` is accepted but never rendered. Menus are a list of verbs; a second
//     explanatory line under each one doubles the height of the sheet and slows down the scan.
//   * `cardColors` is accepted but ignored, so one menu can't quietly diverge from the rest.
//
// Shiori: [FilasMenu] da ese mismo aspecto (píldoras de 16 dp sobre MenuRowSurface, separadas 8 dp, texto
// 14 sp SemiBold) a menús escritos con DropdownMenuItem de Material, sin tocar cada pantalla.
package com.example.lector.ui.mochy.sheet

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardColors
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measured
import androidx.compose.ui.layout.VerticalAlignmentLine
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun Material3MenuGroup(
    items: List<Material3MenuItemData>
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items.forEach { item ->
            MenuItemRow(
                icon = item.icon,
                enabled = item.onClick != null,
                onClick = { item.onClick?.invoke() },
                trailing = item.trailingContent,
                title = item.title,
            )
        }
    }
}

/**
 * One row of a menu: a flat dark pill with the icon, the title, and anything [trailing].
 */
@Composable
fun MenuItemRow(
    icon: (@Composable () -> Unit)?,
    enabled: Boolean,
    onClick: () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
    title: @Composable () -> Unit,
) {
    val contentAlpha = if (enabled) 1f else 0.35f

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .clip(RoundedCornerShape(16.dp))
            .background(MenuRowSurface)
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 14.dp, vertical = 14.dp),
    ) {
        if (icon != null) {
            // Menus pass a bare Icon with no tint, which picks up LocalContentColor, so white here
            // makes every menu's icons white without each one asking.
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(SheetIconSize),
            ) {
                CompositionLocalProvider(
                    LocalContentColor provides MenuContent.copy(alpha = contentAlpha),
                ) {
                    icon()
                }
            }
            Spacer(modifier = Modifier.width(14.dp))
        }

        Box(modifier = Modifier.weight(1f)) {
            ProvideTextStyle(
                MaterialTheme.typography.bodyLarge.copy(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MenuContent.copy(alpha = contentAlpha),
                )
            ) {
                title()
            }
        }

        trailing?.let {
            Spacer(modifier = Modifier.width(8.dp))
            it()
        }
    }
}

data class Material3MenuItemData(
    val icon: (@Composable () -> Unit)? = null,
    val title: @Composable () -> Unit,
    /** Accepted for source compatibility, never rendered. See the file header. */
    val description: (@Composable () -> Unit)? = null,
    val onClick: (() -> Unit)? = null,
    /** Accepted for source compatibility, never applied. See the file header. */
    val cardColors: CardColors? = null,
    val trailingContent: (@Composable () -> Unit)? = null
)

/** ColumnScope sin efectos para el contenido de [FilasMenu] (los menús no usan weight ni align). */
private object AmbitoFilas : ColumnScope {
    override fun Modifier.weight(weight: Float, fill: Boolean): Modifier = this
    override fun Modifier.align(alignment: Alignment.Horizontal): Modifier = this
    override fun Modifier.alignBy(alignmentLine: VerticalAlignmentLine): Modifier = this
    override fun Modifier.alignBy(alignmentLineBlock: (Measured) -> Int): Modifier = this
}

/** Rectángulos de las filas medidas, para pintar sus píldoras detrás (se leen al dibujar, sin recomponer). */
private class Filas { var tops = IntArray(0); var alturas = IntArray(0); var ancho = 0 }

/**
 * Pone cada hijo (p. ej. un DropdownMenuItem) en una píldora de [MenuItemRow]: fondo MenuRowSurface con 16 dp de
 * esquina, 8 dp entre filas, texto 14 sp SemiBold en blanco y sin ripple.
 */
@Composable
fun FilasMenu(content: @Composable ColumnScope.() -> Unit) {
    val base = MaterialTheme.typography
    val estilo = base.bodyLarge.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    val color = MenuContent
    val filas = remember { Filas() }
    val superficie = MenuRowSurface
    MaterialTheme(
        colorScheme = MaterialTheme.colorScheme.copy(onSurface = color, onSurfaceVariant = color),
        typography = base.copy(labelLarge = estilo),
    ) {
        Layout(
            content = { AmbitoFilas.content() },
            modifier = Modifier.fillMaxWidth().drawBehind {
                val r = CornerRadius(16.dp.toPx())
                for (i in filas.tops.indices) {
                    drawRoundRect(superficie, Offset(0f, filas.tops[i].toFloat()), Size(filas.ancho.toFloat(), filas.alturas[i].toFloat()), r)
                }
            },
        ) { medibles, restricciones ->
            val hueco = 8.dp.roundToPx()
            val acotado = restricciones.hasBoundedWidth
            val colocables = medibles.map {
                it.measure(restricciones.copy(minWidth = if (acotado) restricciones.maxWidth else 0, minHeight = 0))
            }
            val ancho = if (acotado) restricciones.maxWidth else (colocables.maxOfOrNull { it.width } ?: 0)
            val tops = IntArray(colocables.size)
            var y = 0
            colocables.forEachIndexed { i, p -> tops[i] = y; y += p.height + if (i < colocables.lastIndex) hueco else 0 }
            filas.tops = tops; filas.alturas = IntArray(colocables.size) { colocables[it].height }; filas.ancho = ancho
            layout(ancho, y.coerceAtLeast(restricciones.minHeight)) {
                colocables.forEachIndexed { i, p -> p.place(0, tops[i]) }
            }
        }
    }
}
