package com.example.lector

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.em
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import java.io.File
import android.graphics.Color as ColorAndroid

val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
)

// Una paleta es, en el fondo, un color de acento. El fondo sigue siendo negro puro.
// La primera, «Musicfy», es monocroma (gris claro sobre negro), como musicfy: es la de por defecto.
data class Paleta(val nombre: String, val color: Color)

object Paletas {
    // Esta paleta toma el color de tu fondo de pantalla (Android 12 o superior).
    const val SISTEMA = "Sistema"

    const val MONO = "Musicfy"

    val fijas = listOf(
        Paleta(MONO, Color(0xFFE0E0E0)),
        Paleta("Rojo", Color(0xFFFA233B)),
        Paleta("Naranja", Color(0xFFFF8A3D)),
        Paleta("Dorado", Color(0xFFF2C14E)),
        Paleta("Verde", Color(0xFF3DDC84)),
        Paleta("Turquesa", Color(0xFF2EC4B6)),
        Paleta("Azul", Color(0xFF4C8DFF)),
        Paleta("Violeta", Color(0xFFA37BFF)),
        Paleta("Rosa", Color(0xFFFF5FA2)),
    )

    val porDefecto = fijas.first()
}

// Devuelve el color de acento de una paleta por su nombre.
@Composable
fun colorDePaleta(nombre: String, oscuro: Boolean = true): Color {
    if (nombre == Paletas.SISTEMA && Build.VERSION.SDK_INT >= 31) {
        val ctx = LocalContext.current
        return if (oscuro) dynamicDarkColorScheme(ctx).primary else androidx.compose.material3.dynamicLightColorScheme(ctx).primary
    }
    // El gris claro de «Musicfy» no se vería sobre fondo claro: ahí es casi negro.
    if ((nombre == Paletas.MONO || Paletas.fijas.none { it.nombre == nombre }) && !oscuro) return Color(0xFF1C1C1E)
    return (Paletas.fijas.firstOrNull { it.nombre == nombre } ?: Paletas.porDefecto).color
}

// Negro o blanco, según cuál se lea mejor encima del color dado.
fun textoSobre(color: Color): Color =
    if (color.luminance() > 0.5f) Color.Black else Color.White

// Saca el color dominante de una portada.
object ColorPortada {
    fun de(archivo: File): Color? {
        if (!archivo.exists()) return null
        val original = BitmapFactory.decodeFile(archivo.path) ?: return null
        val chico = Bitmap.createScaledBitmap(original, 24, 24, true)

        // Agrupa los píxeles por tono (12 grupos de 30°) y da más peso a los vivos y claros.
        val hsv = FloatArray(3)
        val peso = FloatArray(12)
        val rojo = FloatArray(12)
        val verde = FloatArray(12)
        val azul = FloatArray(12)
        for (y in 0 until chico.height) {
            for (x in 0 until chico.width) {
                val pixel = chico.getPixel(x, y)
                ColorAndroid.colorToHSV(pixel, hsv)
                if (hsv[1] < 0.25f || hsv[2] < 0.25f) continue // descarta grises y oscuros
                val w = hsv[1] * hsv[2]
                val grupo = (hsv[0] / 30f).toInt().coerceIn(0, 11)
                peso[grupo] += w
                rojo[grupo] += ColorAndroid.red(pixel) * w
                verde[grupo] += ColorAndroid.green(pixel) * w
                azul[grupo] += ColorAndroid.blue(pixel) * w
            }
        }

        val mejor = peso.indices.maxByOrNull { peso[it] } ?: return null
        if (peso[mejor] < 1f) return null // portada casi en blanco y negro

        val resultado = FloatArray(3)
        ColorAndroid.RGBToHSV(
            (rojo[mejor] / peso[mejor]).toInt(),
            (verde[mejor] / peso[mejor]).toInt(),
            (azul[mejor] / peso[mejor]).toInt(),
            resultado,
        )
        // Lo avivamos un poco para que destaque sobre el fondo negro.
        resultado[1] = resultado[1].coerceIn(0.55f, 0.95f)
        resultado[2] = maxOf(resultado[2], 0.85f)
        return Color(ColorAndroid.HSVToColor(resultado))
    }
}

// Paleta de Mochy (ui/theme/Theme.kt de Mochy Music): monocroma, sin color dinámico de Material You. Mochy deja
// sin fijar los surfaceContainer*, outline, tertiary… y ahí Material pone sus grises violáceos; aquí van neutros.
private val EsquemaPuro = darkColorScheme(
    primary = Color(0xFFE0E0E0),
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF333333),
    onPrimaryContainer = Color.White,
    secondary = Color(0xFFB0B0B0),
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF2C2C2C),
    onSecondaryContainer = Color.White,
    tertiary = Color(0xFFB0B0B0),
    onTertiary = Color.Black,
    tertiaryContainer = Color(0xFF2C2C2C),
    onTertiaryContainer = Color.White,
    background = Color.Black,
    onBackground = Color.White,
    surface = Color.Black,
    onSurface = Color.White,
    surfaceVariant = Color(0xFF1A1A1A),
    onSurfaceVariant = Color(0xFF9A9AA0),
    surfaceTint = Color.Black,
    surfaceBright = Color(0xFF2C2C2E),
    surfaceDim = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF0B0B0C),
    surfaceContainer = Color(0xFF141416),
    surfaceContainerHigh = Color(0xFF161619),
    surfaceContainerHighest = Color(0xFF2C2C2E),
    outline = Color(0xFF3A3A3C),
    outlineVariant = Color(0xFF232326),
    inverseSurface = Color(0xFFE0E0E0),
    inverseOnSurface = Color.Black,
    inversePrimary = Color(0xFF333333),
    error = Color(0xFFE57373),
    onError = Color.Black,
    scrim = Color.Black,
)

// Oscuro "normal": la misma paleta de Mochy con grises en vez de negro puro.
private val EsquemaOscuro = EsquemaPuro.copy(
    background = Color(0xFF0E0E10),
    surface = Color(0xFF0E0E10),
    surfaceDim = Color(0xFF0E0E10),
    surfaceContainerLowest = Color(0xFF0E0E10),
    surfaceContainerLow = Color(0xFF141416),
    surfaceContainer = Color(0xFF1A1A1D),
    surfaceContainerHigh = Color(0xFF1E1E21),
    surfaceContainerHighest = Color(0xFF2C2C2E),
    surfaceVariant = Color(0xFF1E1E21),
)

// Claro de Mochy (negro sobre blanco), con los grises de Shiori.
private val EsquemaClaro = lightColorScheme(
    primary = Color.Black,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0E0E0),
    onPrimaryContainer = Color.Black,
    secondary = Color(0xFF333333),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCCCCCC),
    onSecondaryContainer = Color.Black,
    tertiary = Color(0xFF333333),
    onTertiary = Color.White,
    background = Color(0xFFF6F6F8),
    onBackground = Color(0xFF111114),
    surface = Color(0xFFF6F6F8),
    onSurface = Color(0xFF111114),
    surfaceVariant = Color(0xFFF0F0F0),
    onSurfaceVariant = Color(0xFF4A4A52),
    surfaceTint = Color.White,
    surfaceBright = Color.White,
    surfaceDim = Color(0xFFE2E2E8),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainer = Color(0xFFEFEFF3),
    surfaceContainerHigh = Color(0xFFF2F2F5),
    surfaceContainerHighest = Color(0xFFE2E2E8),
    outline = Color(0xFFB8B8BE),
    outlineVariant = Color(0xFFDDDDE2),
    error = Color(0xFFD32F2F),
)

// Como musicfy: Inter SemiBold en todo y el interletrado un poco cerrado (-0.05 em).
private fun TextStyle.mf(f: FontFamily) = copy(fontFamily = f, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.05).em)

private fun Typography.conFuente(f: FontFamily) = copy(
    displayLarge = displayLarge.mf(f),
    displayMedium = displayMedium.mf(f),
    displaySmall = displaySmall.mf(f),
    headlineLarge = headlineLarge.mf(f),
    headlineMedium = headlineMedium.mf(f),
    headlineSmall = headlineSmall.mf(f),
    titleLarge = titleLarge.mf(f),
    titleMedium = titleMedium.mf(f),
    titleSmall = titleSmall.mf(f),
    bodyLarge = bodyLarge.mf(f),
    bodyMedium = bodyMedium.mf(f),
    bodySmall = bodySmall.mf(f),
    labelLarge = labelLarge.mf(f),
    labelMedium = labelMedium.mf(f),
    labelSmall = labelSmall.mf(f),
)

private val Tipografia = Typography().conFuente(Inter)

@Composable
fun LectorTheme(
    acento: Color = Paletas.porDefecto.color,
    oscuro: Boolean = true,
    puro: Boolean = true,
    content: @Composable () -> Unit,
) {
    val base = if (!oscuro) EsquemaClaro else if (puro) EsquemaPuro else EsquemaOscuro
    // El acento elegido (por defecto el gris claro monocromo de Mochy) sustituye al primario; el resto no cambia.
    val esquema = remember(base, acento) { base.copy(primary = acento, onPrimary = textoSobre(acento)) }
    MaterialTheme(
        colorScheme = esquema,
        typography = Tipografia,
        content = content,
    )
}

// Pinta su contenido con el color de la portada (si está activado y se puede sacar uno).
@Composable
fun TemaPortada(portada: File, activo: Boolean, content: @Composable () -> Unit) {
    val color = remember(portada.path, activo) {
        if (activo) ColorPortada.de(portada) else null
    }
    if (color == null) {
        content()
    } else {
        MaterialTheme(
            colorScheme = MaterialTheme.colorScheme.copy(
                primary = color,
                onPrimary = textoSobre(color),
            ),
            content = content,
        )
    }
}
