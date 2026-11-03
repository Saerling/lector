package com.example.lector.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.TextField
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * Un solo lenguaje de movimiento para toda la app. Con «Quitar animaciones» (o las animaciones del sistema
 * desactivadas) todo pasa a un fundido corto, sin desplazamientos ni escalas.
 */
object Mov {
    val reducido: Boolean get() = MovimientoReducido.activo
    fun <T> entrada(ms: Int = 320): FiniteAnimationSpec<T> = if (reducido) tween(120) else tween(ms, easing = EnfasisEasing)
    fun <T> salida(ms: Int = 220): FiniteAnimationSpec<T> = if (reducido) tween(100) else tween(ms, easing = CierreEasing)

    /** Pantalla que llega encima (la misma que las capas de LectorApp). */
    fun entraCapa(): EnterTransition = if (reducido) fadeIn(tween(120)) else
        slideInHorizontally(tween(330, easing = EnfasisEasing)) { it / 8 } +
            scaleIn(tween(330, easing = EnfasisEasing), initialScale = 0.96f) + fadeIn(tween(260, easing = EnfasisEasing))

    fun saleCapa(): ExitTransition = if (reducido) fadeOut(tween(100)) else
        slideOutHorizontally(tween(240, easing = CierreEasing)) { it / 8 } +
            scaleOut(tween(240, easing = CierreEasing), targetScale = 0.97f) + fadeOut(tween(180))

    /** Ventanas (diálogos): crecen un poco desde 0,94 y suben apenas. */
    fun entraVentana(): EnterTransition = if (reducido) fadeIn(tween(120)) else
        scaleIn(tween(260, easing = EnfasisEasing), initialScale = 0.94f) + fadeIn(tween(180)) +
            slideInVertically(tween(260, easing = EnfasisEasing)) { it / 24 }
}

/** Pantalla encima de otra (ajustes de fuente, migrar, WebView…) con la misma transición que el resto de capas. */
@Composable
fun CapaEncima(visible: Boolean, content: @Composable () -> Unit) {
    AnimatedVisibility(visible, enter = Mov.entraCapa(), exit = Mov.saleCapa()) {
        Box(Modifier.fillMaxSize()) { content() }
        // Mientras se va, «atrás» no vuelve a ejecutar el cierre de la pantalla que sale.
        androidx.activity.compose.BackHandler(transition.targetState == androidx.compose.animation.EnterExitState.PostExit) {}
    }
}

/** Igual, para capas que dependen de un valor (p. ej. una URL): mientras se va, sigue pintando el último. */
@Composable
fun <T : Any> CapaEncima(valor: T?, content: @Composable (T) -> Unit) {
    var ultimo by remember { mutableStateOf(valor) }
    if (valor != null) ultimo = valor
    AnimatedVisibility(valor != null, enter = Mov.entraCapa(), exit = Mov.saleCapa()) {
        ultimo?.let { Box(Modifier.fillMaxSize()) { content(it) } }
        androidx.activity.compose.BackHandler(transition.targetState == androidx.compose.animation.EnterExitState.PostExit) {}
    }
}

/**
 * Diálogo de Shiori con la firma de AlertDialog de Material (para sustituirlo sin tocar cada llamada). Se pinta con
 * el DefaultDialog de Mochy (ui/mochy/sheet/Dialog.kt): 24 dp de margen y de relleno, título headlineSmall, texto
 * desplazable y los botones alineados a la derecha con el color primario.
 */
@Composable
fun DialogoAlerta(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    properties: DialogProperties = DialogProperties(),
) {
    com.example.lector.ui.mochy.sheet.DefaultDialog(
        onDismiss = onDismissRequest,
        modifier = modifier,
        icon = icon,
        title = title,
        horizontalAlignment = Alignment.Start,
        properties = DialogProperties(
            properties.dismissOnBackPress, properties.dismissOnClickOutside, properties.securePolicy,
            usePlatformDefaultWidth = false, decorFitsSystemWindows = properties.decorFitsSystemWindows,
        ),
        buttons = {
            dismissButton?.let { CompositionLocalProvider(LocalContentColor provides LectorColors.Muted) { it() } }
            confirmButton()
        },
    ) {
        text?.let {
            Box(Modifier.weight(1f, fill = false)) {
                CompositionLocalProvider(LocalContentColor provides LectorColors.Muted) {
                    ProvideTextStyle(MaterialTheme.typography.bodyMedium) { it() }
                }
            }
        }
    }
}

/**
 * Menú de Shiori: en vez de un desplegable de Material sale como hoja de Mochy (PopupSheet), con cada opción en una
 * píldora de su MenuItemRow (ver [com.example.lector.ui.mochy.sheet.FilasMenu]).
 */
@Composable
fun MenuShiori(expanded: Boolean, onDismissRequest: () -> Unit, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    if (!expanded) return
    com.example.lector.ui.mochy.sheet.PopupSheet(onClosed = onDismissRequest) {
        Column(modifier.fillMaxWidth().verticalScroll(androidx.compose.foundation.rememberScrollState()).navigationBarsPadding().padding(bottom = 12.dp)) {
            com.example.lector.ui.mochy.sheet.FilasMenu(content)
        }
    }
}

/** Casilla al estilo Mochy: cuadrado de 22 dp con esquinas de 7 dp que se rellena con el primario y un muelle. */
@Composable
fun CasillaShiori(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val acento = MaterialTheme.colorScheme.primary
    val p by androidx.compose.animation.core.animateFloatAsState(
        if (checked) 1f else 0f, Muelles.espacialRapido(), label = "casilla",
    )
    val forma = SuaveShape(7.dp)
    Box(
        modifier
            .then(
                if (onCheckedChange != null) Modifier.toggleable(
                    value = checked, enabled = enabled, role = Role.Checkbox,
                    interactionSource = remember { MutableInteractionSource() }, indication = null,
                    onValueChange = { Tacto.tic(); onCheckedChange(it) },
                ) else Modifier,
            )
            .size(48.dp)
            .graphicsLayer { alpha = if (enabled) 1f else 0.38f },
        Alignment.Center,
    ) {
        Box(
            Modifier.size(22.dp).graphicsLayer { val e = 0.9f + 0.1f * p.coerceIn(0f, 1.1f); scaleX = e; scaleY = e }
                .clip(forma)
                .background(acento.copy(alpha = p.coerceIn(0f, 1f)))
                .border(2.dp, LectorColors.Muted.copy(alpha = 1f - p.coerceIn(0f, 1f)), forma),
            Alignment.Center,
        ) {
            if (p > 0.01f) Icon(
                Icons.Rounded.Check, null, tint = com.example.lector.textoSobre(acento),
                modifier = Modifier.size(16.dp).graphicsLayer { alpha = p.coerceIn(0f, 1f); val e = 0.6f + 0.4f * p; scaleX = e; scaleY = e },
            )
        }
    }
}

/**
 * Campo de texto (misma firma que OutlinedTextField). Como los de los diálogos de Mochy es un TextField con los
 * colores de OutlinedTextFieldDefaults; Shiori le pone el fondo de fila de las hojas (MenuRowSurface), las esquinas de
 * 16 dp de MenuItemRow y quita la línea inferior.
 */
@Composable
fun CampoTextoShiori(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    textStyle: TextStyle = LectorType.Body,
    label: (@Composable () -> Unit)? = null,
    placeholder: (@Composable () -> Unit)? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    supportingText: (@Composable () -> Unit)? = null,
    isError: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = 1,
) {
    val acento = MaterialTheme.colorScheme.primary
    val fondo = com.example.lector.ui.mochy.sheet.MenuRowSurface
    TextField(
        value = value, onValueChange = onValueChange, modifier = modifier, enabled = enabled,
        textStyle = textStyle.copy(color = LectorColors.Text), label = label, placeholder = placeholder,
        leadingIcon = leadingIcon, trailingIcon = trailingIcon, supportingText = supportingText, isError = isError,
        visualTransformation = visualTransformation, keyboardOptions = keyboardOptions, keyboardActions = keyboardActions,
        singleLine = singleLine, maxLines = maxLines, minLines = minLines,
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Color.Transparent, unfocusedBorderColor = Color.Transparent,
            disabledBorderColor = Color.Transparent, errorBorderColor = Color.Transparent,
            cursorColor = acento, focusedLabelColor = acento, unfocusedLabelColor = LectorColors.Muted,
            focusedPlaceholderColor = LectorColors.Pista, unfocusedPlaceholderColor = LectorColors.Pista,
            focusedContainerColor = fondo, unfocusedContainerColor = fondo, disabledContainerColor = fondo,
            errorContainerColor = fondo,
        ),
    )
}

// ───── Listas con animación de entrada, salida y recolocación (Modifier.animateItem), como en Komikku ─────

@Composable
private fun Modifier.animarItem(scope: androidx.compose.foundation.lazy.LazyItemScope): Modifier = with(scope) {
    if (Mov.reducido) this@animarItem else this@animarItem.animateItem(
        fadeInSpec = tween(220, easing = EnfasisEasing),
        placementSpec = androidx.compose.animation.core.spring(
            stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow,
            visibilityThreshold = androidx.compose.ui.unit.IntOffset(1, 1),
        ),
        fadeOutSpec = tween(160),
    )
}

fun <T> androidx.compose.foundation.lazy.LazyListScope.itemsAnimados(
    lista: List<T>,
    key: ((T) -> Any)? = null,
    contentType: (T) -> Any? = { null },
    contenido: @Composable androidx.compose.foundation.lazy.LazyItemScope.(T) -> Unit,
) = items(lista.size, key?.let { k -> { i: Int -> k(lista[i]) } }, { i -> contentType(lista[i]) }) { i ->
    val ambito = this
    Box(Modifier.animarItem(ambito)) { ambito.contenido(lista[i]) }
}

fun <T> androidx.compose.foundation.lazy.LazyListScope.itemsIndexedAnimados(
    lista: List<T>,
    key: ((Int, T) -> Any)? = null,
    contentType: (Int, T) -> Any? = { _, _ -> null },
    contenido: @Composable androidx.compose.foundation.lazy.LazyItemScope.(Int, T) -> Unit,
) = items(lista.size, key?.let { k -> { i: Int -> k(i, lista[i]) } }, { i -> contentType(i, lista[i]) }) { i ->
    val ambito = this
    Box(Modifier.animarItem(ambito)) { ambito.contenido(i, lista[i]) }
}

fun <T> androidx.compose.foundation.lazy.grid.LazyGridScope.itemsAnimados(
    lista: List<T>,
    key: ((T) -> Any)? = null,
    contenido: @Composable androidx.compose.foundation.lazy.grid.LazyGridItemScope.(T) -> Unit,
) = items(lista.size, key?.let { k -> { i: Int -> k(lista[i]) } }) { i ->
    val ambito = this
    val m = if (Mov.reducido) Modifier else with(ambito) {
        Modifier.animateItem(
            fadeInSpec = tween(220, easing = EnfasisEasing),
            placementSpec = androidx.compose.animation.core.spring(
                stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow,
                visibilityThreshold = androidx.compose.ui.unit.IntOffset(1, 1),
            ),
            fadeOutSpec = tween(160),
        )
    }
    Box(m) { ambito.contenido(lista[i]) }
}


/** Estado del gesto «atrás» predictivo: 0 = sin gesto, 1 = a punto de soltar. */
object GestoAtras {
    var progreso by mutableFloatStateOf(0f)
    /** Animación de vuelta a 0 de un gesto cancelado (se para si empieza otro). */
    var vuelta: kotlinx.coroutines.Job? = null
    /** Hay una página encima de las pestañas (el carrusel de Inicio se pausa). */
    var hayEncima by mutableStateOf(false)
    var desdeIzquierda by mutableStateOf(true)
}

/**
 * La página de arriba sigue al gesto atrás (como Android 14+): se encoge hasta el 90 %, se corre un poco hacia el
 * lado contrario al borde y redondea sus esquinas. Al soltar se queda así mientras sale (no da un salto).
 */
@Composable
fun Modifier.gestoAtras(arriba: Boolean): Modifier {
    // Todo se lee al dibujar (no recompone la página en cada fotograma del gesto).
    val congelado = remember { FloatArray(1) }
    return this.graphicsLayer {
        val raw = if (arriba) GestoAtras.progreso.also { congelado[0] = it } else congelado[0]
        val p = if (Mov.reducido) 0f else androidx.compose.animation.core.FastOutSlowInEasing.transform(raw)
        if (p <= 0f) return@graphicsLayer
        val e = 1f - 0.1f * p
        scaleX = e; scaleY = e
        translationX = (if (GestoAtras.desdeIzquierda) 1f else -1f) * 24.dp.toPx() * p
        shape = SuaveShape(32.dp * p)
        clip = true
    }
}
