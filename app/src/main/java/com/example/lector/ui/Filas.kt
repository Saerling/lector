package com.example.lector.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import com.example.lector.ui.mochy.AppSwitch
import com.example.lector.ui.mochy.AppTypography
import com.example.lector.ui.mochy.SettingsGroup
import com.example.lector.ui.mochy.SettingsGroupStyle
import com.example.lector.ui.mochy.SettingsItem
import kotlinx.coroutines.withTimeoutOrNull

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
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.KeyboardArrowLeft
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.em
import kotlin.math.PI
import kotlin.math.sin
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

// ───────────── Cabecera que se pliega (las páginas de ajustes de musicfy) ─────────────

private val MargenPagina = 20.dp
private val TamAtras = 40.dp
private val ArribaCabecera = 16.dp
private val AltoCabecera = 118.dp
/** El título grande empieza bajo el botón atrás... */
private val TituloArriba = ArribaCabecera + TamAtras + 12.dp
/** ...y al plegarse se encoge al 62 % y se coloca a la derecha del botón. */
private val TituloPlegadoX = TamAtras + 14.dp
private const val EscalaPlegado = 0.62f
private val PliegueEasing = CubicBezierEasing(0.59f, 0.53f, 0f, 1f) // la de DetailCollapsingTopBar de Mochy

/**
 * Progreso del pliegue (0 abierto, 1 plegado). Cruzar el umbral cambia el destino y la animación siempre
 * termina (900 ms), aunque el dedo se suelte a medias: no queda pegada 1:1 al desplazamiento.
 */
@Composable
fun rememberPliegue(scroll: ScrollState, entra: Dp = 24.dp, sale: Dp = 6.dp): State<Float> {
    val d = LocalDensity.current
    val entraPx = with(d) { entra.toPx() }
    val salePx = with(d) { sale.toPx() }
    val cerrojo = remember(scroll) { booleanArrayOf(false) }
    val plegado by remember(scroll, entraPx, salePx) {
        derivedStateOf {
            val o = scroll.value.toFloat()
            val n = when {
                o > entraPx -> true
                o < salePx -> false
                else -> cerrojo[0]
            }
            cerrojo[0] = n
            n
        }
    }
    return animateFloatAsState(if (plegado) 1f else 0f, if (Mov.reducido) tween(120) else tween(1000, easing = PliegueEasing), label = "pliegue")
}

/** Botón atrás redondo gris de 40 dp: el de las subpáginas de ajustes de Mochy (ui/mochy/SubSettingsScaffold.kt). */
@Composable
fun BotonAtras(onBack: () -> Unit, modifier: Modifier = Modifier) {
    com.example.lector.ui.mochy.SubSettingsBackButton(onBack, modifier)
}

/**
 * Página de ajustes a pantalla completa: el SubSettingsScaffold de Mochy (título enorme que, al desplazar, se encoge
 * y se desliza junto al botón atrás con un desenfoque de paso, sobre una cabecera de cristal progresivo).
 * Las filas de las páginas de Shiori ponen su propio margen lateral, así que el contenido va sin él.
 */
@Composable
fun PaginaAjustes(titulo: String, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    com.example.lector.ui.mochy.SubSettingsScaffold(
        title = titulo,
        onBack = onBack,
        contentPadding = 0.dp,
        content = content,
    )
}

/*
 * Filas de ajustes: el SettingsGroup de Mochy (ui/mochy/SettingsGroup.kt), estilo «Classic» (tarjeta de 20 dp de
 * radio y 8 dp entre filas), una fila por grupo. El margen lateral de 16 dp es el que ya daban estas filas.
 */
@Composable
private fun TarjetaFila(item: SettingsItem) {
    Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        SettingsGroup(items = listOf(item), style = SettingsGroupStyle.Classic)
    }
}

/** Fila de ajustes: tarjeta con icono en círculo, título y detalle. */
@Composable
fun FilaAjuste(icon: ImageVector?, titulo: String, detalle: String? = null, onClick: () -> Unit) {
    TarjetaFila(
        SettingsItem(
            icon = icon?.let { rememberVectorPainter(it) },
            title = { androidx.compose.material3.Text(titulo) },
            descriptionText = detalle,
            onClick = onClick,
        )
    )
}

@Composable
fun FilaSwitch(titulo: String, detalle: String? = null, activado: Boolean, onChange: (Boolean) -> Unit) {
    TarjetaFila(
        SettingsItem(
            title = { androidx.compose.material3.Text(titulo) },
            descriptionText = detalle,
            trailingContent = { Interruptor(activado, onChange) },
            onClick = { onChange(!activado) },
        )
    )
}

@Composable
fun FilaRadio(etiqueta: String, marcada: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().resaltable(onClick = onClick).padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Marca(marcada)
        Spacer(Modifier.width(16.dp))
        LText(etiqueta, LectorType.Body.copy(fontSize = 15.sp))
    }
}

/** Botón de opción redondo: anillo gris; marcado, círculo blanco con un punto oscuro. */
@Composable
fun Marca(marcada: Boolean) {
    val relleno by animateColorAsState(if (marcada) LectorColors.Text else Color.Transparent, tween(150), label = "marca")
    Box(
        Modifier.size(22.dp).clip(CircleShape).background(relleno)
            .border(1.5.dp, if (marcada) Color.Transparent else LectorColors.Muted, CircleShape),
        Alignment.Center,
    ) { if (marcada) Box(Modifier.size(8.dp).clip(CircleShape).background(LectorColors.Bg)) }
}

/** Interruptor: el AppSwitch de Mochy (ui/mochy/AppSwitch.kt), con el toque háptico de Shiori. */
@Composable
fun Interruptor(activado: Boolean, onChange: ((Boolean) -> Unit)?, modifier: Modifier = Modifier) {
    AppSwitch(
        checked = activado,
        onCheckedChange = onChange?.let { cambiar -> { v: Boolean -> Tacto.alternar(v); cambiar(v) } },
        modifier = modifier,
    )
}

/** Título de un grupo de ajustes, como el de SettingsGroup de Mochy (labelLarge en el color de acento). */
@Composable
fun TituloSeccion(texto: String) {
    androidx.compose.material3.Text(
        text = texto,
        style = AppTypography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp).padding(start = 4.dp, bottom = 8.dp, top = 8.dp + 10.dp),
    )
}

@Composable
fun Divisor() {
    Spacer(Modifier.height(8.dp))
}

/**
 * Chip de selección: el FilterChip de ChipsRow de Mochy (ui/mochy/ChipsRow.kt), esquina de 8 dp que se redondea a
 * 20 dp con un rebote al elegirlo y un visto delante. Mantener pulsado llama a [onLongClick].
 */
@Composable
fun ChipSel(texto: String, seleccionado: Boolean, onLongClick: (() -> Unit)? = null, onClick: () -> Unit) {
    val isSelected = seleccionado
    val cornerRadius by animateDpAsState(
        targetValue = if (isSelected) 20.dp else 8.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "corner_radius"
    )
    val largo by rememberUpdatedState(onLongClick)
    FilterChip(
        label = { androidx.compose.material3.Text(texto) },
        selected = isSelected,
        colors = FilterChipDefaults.filterChipColors(
            containerColor = LectorColors.Card,
            labelColor = LectorColors.Text,
            selectedContainerColor = LectorColors.PillFill,
            selectedLabelColor = LectorColors.Text,
            selectedLeadingIconColor = LectorColors.Text,
        ),
        onClick = { Tacto.tic(); onClick() },
        leadingIcon = if (isSelected) {
            {
                Icon(
                    imageVector = Icons.Filled.Done,
                    contentDescription = null,
                    modifier = Modifier.size(FilterChipDefaults.IconSize),
                )
            }
        } else {
            null
        },
        shape = RoundedCornerShape(cornerRadius),
        border = null,
        modifier = Modifier
            .then(if (onLongClick != null) Modifier.pulsacionLarga { largo?.invoke() } else Modifier)
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            )
    )
}

/**
 * Mantener pulsado sin quitarle el toque al FilterChip: se mira en la pasada inicial y, si se cumple el tiempo,
 * se consume el resto del gesto para que el chip no lo cuente además como un toque.
 */
private fun Modifier.pulsacionLarga(alMantener: () -> Unit): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        val suelta = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
            waitForUpOrCancellation(PointerEventPass.Initial)
        }
        if (suelta == null) {
            Tacto.tic()
            alMantener()
            while (true) {
                val ev = awaitPointerEvent(PointerEventPass.Initial)
                ev.changes.forEach { it.consume() }
                if (ev.changes.none { it.pressed }) break
            }
        }
    }
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
        LText(label, LectorType.Label, color = LectorColors.Muted, maxLines = 2)
    }
}
