// Adaptación para Shiori: lo mínimo que los archivos copiados de Mochy Music
// (github.com/realidkroo/Mochy-Music, GPL-3.0) necesitan y que en Mochy vive fuera de ui/component
// (constants/PreferenceKeys.kt, constants/Dimensions.kt, MainActivity.kt, ui/screens/search/SearchDesign.kt).
// Las preferencias de DataStore de Mochy (rememberPreference) pasan a ser estado simple.

package com.example.lector.ui.mochy

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// constants/PreferenceKeys.kt
enum class BlurQuality { LOW, HIGH }

/** Lo que en Mochy son claves de DataStore leídas con rememberPreference. */
object MochyPreferences {
    var disableBlur by mutableStateOf(false)
    var blurStrengthPx by mutableFloatStateOf(25f)
    var enableProgressiveBlur by mutableStateOf(true)
    var hideSettingsDescriptions by mutableStateOf(false)
}

// constants/Dimensions.kt
val AppBarHeight = 64.dp

// MainActivity.kt
val LocalDetailAccentColor = staticCompositionLocalOf<MutableState<Color?>> {
    mutableStateOf(null)
}

// ui/screens/search/SearchDesign.kt
// (Shiori: Field y FieldFocused siguen al tema claro/oscuro de Shiori; en oscuro son los mismos #141416 / #1E1E21)
object SearchColors {
    val Field: Color get() = com.example.lector.ui.LectorColors.Card
    val FieldFocused: Color get() = com.example.lector.ui.LectorColors.CardHigh
    val Tile = Color(0xFF121214)
    val TileHigh = Color(0xFF1C1C1F)
    val Divider = Color(0xFF232326)
    val Primary = Color.White
    val Secondary = Color(0xFF9A9AA0)
    val Placeholder = Color(0xFF77777D)

    fun page(pureBlack: Boolean): Color = Color.Black
}

/**
 * Compose 1.7 no tiene androidx.compose.ui.graphics.shadow.Shadow (llegó en 1.9): la misma forma de datos,
 * para que ChromeShadow de FrostedGlass.kt siga existiendo. Se dibuja con Modifier.shadow donde haga falta.
 */
data class Shadow(val radius: Dp, val color: Color, val spread: Dp = 0.dp)
