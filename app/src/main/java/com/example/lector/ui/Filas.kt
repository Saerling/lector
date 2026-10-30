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

/** Botón atrás redondo gris de 40 dp. */
@Composable
fun BotonAtras(onBack: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier.size(TamAtras).pressableCon(0.9f, onBack).clip(CircleShape).background(LectorColors.PillFill),
        Alignment.Center,
    ) { Icon(Icons.Rounded.KeyboardArrowLeft, "Atrás", tint = LectorColors.Text, modifier = Modifier.size(26.dp)) }
}

/**
 * Página de ajustes a pantalla completa (como las de musicfy): título enorme que, al desplazar, se encoge y se
 * desliza junto al botón atrás con un desenfoque de paso, sobre una cabecera de cristal que aparece a la vez.
 */
@Composable
fun PaginaAjustes(titulo: String, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val scroll = rememberScrollState()
    val cristal = remember { EstadoCristal() }
    val pliegue by rememberPliegue(scroll)
    val arriba = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val altoTotal = arriba + AltoCabecera
    var altoTitulo by remember { mutableIntStateOf(0) }
    val fondo = LectorColors.Bg

    Box(Modifier.fillMaxSize().background(fondo)) {
        Column(
            Modifier.fillMaxSize().raizCristal(cristal).verticalScroll(scroll).navigationBarsPadding().padding(bottom = 32.dp)
        ) {
            Spacer(Modifier.height(altoTotal))
            content()
        }
        // Cabecera de cristal: aparece al plegarse.
        Box(Modifier.fillMaxWidth().height(altoTotal).graphicsLayer { alpha = pliegue }) {
            CristalProgresivo(cristal, { 40f * pliegue }, fondo, Modifier.fillMaxSize())
            Box(
                Modifier.matchParentSize().background(
                    Brush.verticalGradient(
                        0f to fondo.copy(alpha = 0.90f), 0.20f to fondo.copy(alpha = 0.50f),
                        0.45f to fondo.copy(alpha = 0.18f), 0.75f to Color.Transparent,
                    )
                )
            )
        }
        Box(Modifier.fillMaxWidth().height(altoTotal).padding(top = arriba, start = MargenPagina, end = MargenPagina)) {
            BotonAtras(onBack, Modifier.padding(top = ArribaCabecera))
            LText(
                titulo, LectorType.Display.copy(fontSize = 32.sp, lineHeight = 40.sp), maxLines = 1,
                modifier = Modifier
                    .onSizeChanged { altoTitulo = it.height }
                    .graphicsLayer {
                        val p = pliegue
                        transformOrigin = TransformOrigin(0f, 0f)
                        val e = 1f + (EscalaPlegado - 1f) * p
                        scaleX = e; scaleY = e
                        val centroAtras = (ArribaCabecera + TamAtras / 2).toPx()
                        val arribaPlegado = centroAtras - altoTitulo * EscalaPlegado / 2f
                        translationX = TituloPlegadoX.toPx() * p
                        translationY = TituloArriba.toPx() + (arribaPlegado - TituloArriba.toPx()) * p
                        // Desenfoque de paso: máximo a mitad del pliegue.
                        val b = (sin(p.coerceIn(0f, 1f) * PI.toFloat()) * 26f)
                        renderEffect = if (hayDesenfoque && b > 0.5f) BlurEffect(b, b, TileMode.Decal) else null
                    },
            )
        }
    }
}

/** Tarjeta de una fila de ajustes (#141416, 20 dp de radio, 8 dp entre filas), como el estilo «clásico» de musicfy. */
@Composable
private fun TarjetaFila(onClick: (() -> Unit)?, onLongClick: (() -> Unit)? = null, content: @Composable RowScope.() -> Unit) {
    val forma = SuaveShape(20.dp)
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 8.dp)
            .then(if (onClick != null) Modifier.pressableCon(0.98f, onLongClick, onClick) else Modifier)
            .clip(forma).background(LectorColors.Card)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/** Icono de fila: círculo claro de 32 dp con el glifo oscuro. */
@Composable
private fun IconoFila(icon: ImageVector) {
    Box(Modifier.size(32.dp).clip(CircleShape).background(LectorColors.IconoFila), Alignment.Center) {
        Icon(icon, null, tint = LectorColors.IconoFilaGlifo, modifier = Modifier.size(18.dp))
    }
    Spacer(Modifier.width(16.dp))
}

@Composable
private fun TextosFila(titulo: String, detalle: String?, modifier: Modifier) {
    Column(modifier) {
        LText(titulo, LectorType.Section)
        if (detalle != null) {
            Spacer(Modifier.height(1.dp))
            LText(detalle, LectorType.Label, color = LectorColors.Muted)
        }
    }
}

/** Fila de ajustes: tarjeta con icono en círculo, título y detalle. */
@Composable
fun FilaAjuste(icon: ImageVector?, titulo: String, detalle: String? = null, onClick: () -> Unit) {
    TarjetaFila(onClick) {
        if (icon != null) IconoFila(icon)
        TextosFila(titulo, detalle, Modifier.weight(1f))
    }
}

@Composable
fun FilaSwitch(titulo: String, detalle: String? = null, activado: Boolean, onChange: (Boolean) -> Unit) {
    TarjetaFila({ onChange(!activado) }) {
        TextosFila(titulo, detalle, Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        Interruptor(activado, onChange)
    }
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

/**
 * Interruptor de musicfy: pista de 52×32 con borde gris cuando está apagado; encendido, pista gris y botón blanco.
 */
@Composable
fun Interruptor(activado: Boolean, onChange: ((Boolean) -> Unit)?, modifier: Modifier = Modifier) {
    // Interruptor Expressive: apagado, botón pequeño gris en pista con borde; encendido, pista de acento y botón
    // grande con un visto. El botón crece, se mueve y cambia de color con muelles.
    val acento = MaterialTheme.colorScheme.primary
    val gris = LectorColors.Muted
    val pista by animateColorAsState(if (activado) acento else Color.Transparent, Muelles.efectos(), label = "pista")
    val borde by animateColorAsState(if (activado) Color.Transparent else gris, Muelles.efectos(), label = "borde")
    val boton by animateColorAsState(if (activado) MaterialTheme.colorScheme.onPrimary else gris, Muelles.efectos(), label = "boton")
    val tam by animateDpAsState(if (activado) 24.dp else 16.dp, Muelles.espacialRapido(), label = "tam")
    val x by animateDpAsState(if (activado) 52.dp - 24.dp - 4.dp else 4.dp, Muelles.espacialRapido(), label = "x")
    Box(
        modifier.size(52.dp, 32.dp)
            .then(if (onChange != null) Modifier.toggleable(activado, role = Role.Switch, onValueChange = { Tacto.alternar(it); onChange(it) }) else Modifier)
            .clip(CircleShape).background(pista).border(2.dp, borde, CircleShape),
    ) {
        Box(Modifier.align(Alignment.CenterStart).offset(x = x + (24.dp - tam) / 2).size(tam).clip(CircleShape).background(boton), Alignment.Center) {
            if (activado) Icon(Icons.Rounded.Check, null, tint = acento, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
fun TituloSeccion(texto: String) {
    LText(
        texto.uppercase(), LectorType.Label.copy(letterSpacing = 0.06.em),
        Modifier.padding(start = 28.dp, end = 24.dp, top = 18.dp, bottom = 8.dp), color = LectorColors.Muted,
    )
}

@Composable
fun Divisor() {
    Spacer(Modifier.height(8.dp))
}

/**
 * Chip de selección (los FilterChip de musicfy): esquina de 8 dp que se redondea a 20 dp con un rebote al
 * elegirlo, y un visto delante cuando está elegido.
 */
@Composable
fun ChipSel(texto: String, seleccionado: Boolean, onLongClick: (() -> Unit)? = null, onClick: () -> Unit) {
    val radio by animateDpAsState(
        if (seleccionado) 20.dp else 8.dp, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium), label = "radioChip",
    )
    val forma = RoundedCornerShape(radio)
    Row(
        Modifier.pressable(onLongClick) { Tacto.tic(); onClick() }.clip(forma)
            .background(if (seleccionado) LectorColors.PillFill else LectorColors.Card)
            .animateContentSize(spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium))
            .height(36.dp).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (seleccionado) {
            Icon(Icons.Rounded.Check, null, tint = LectorColors.Text, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
        }
        LText(texto, LectorType.Body, color = LectorColors.Text, maxLines = 1)
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
