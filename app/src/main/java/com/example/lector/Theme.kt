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
data class Paleta(val nombre: String, val color: Color)

object Paletas {
    // Esta paleta toma el color de tu fondo de pantalla (Android 12 o superior).
    const val SISTEMA = "Sistema"

    val fijas = listOf(
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

// Negro puro (AMOLED) y grises neutros. El acento se pone aparte.
private val EsquemaPuro = darkColorScheme(
    primary = Paletas.porDefecto.color,
    onPrimary = Color.White,
    background = Color.Black,
    onBackground = Color(0xFFE6E1E5),
    surface = Color.Black,
    onSurface = Color(0xFFE6E1E5),
    surfaceVariant = Color(0xFF1A1A1A),
    onSurfaceVariant = Color(0xFFCAC4D0),
    surfaceContainer = Color(0xFF1A1A1A),
    surfaceContainerHigh = Color(0xFF212121),
    surfaceContainerHighest = Color(0xFF2A2A2A),
    surfaceContainerLow = Color(0xFF121212),
)

// Oscuro "normal": grises en vez de negro puro.
private val EsquemaOscuro = darkColorScheme(
    primary = Paletas.porDefecto.color,
    onPrimary = Color.White,
    background = Color(0xFF111114),
    onBackground = Color(0xFFE6E1E5),
    surface = Color(0xFF111114),
    onSurface = Color(0xFFE6E1E5),
    surfaceVariant = Color(0xFF26262C),
    onSurfaceVariant = Color(0xFFCAC4D0),
    surfaceContainer = Color(0xFF1C1C21),
    surfaceContainerHigh = Color(0xFF26262C),
    surfaceContainerHighest = Color(0xFF303037),
    surfaceContainerLow = Color(0xFF17171B),
)

private val EsquemaClaro = lightColorScheme(
    primary = Paletas.porDefecto.color,
    onPrimary = Color.White,
    background = Color(0xFFF6F6F8),
    onBackground = Color(0xFF111114),
    surface = Color(0xFFF6F6F8),
    onSurface = Color(0xFF111114),
    surfaceVariant = Color(0xFFE6E6EB),
    onSurfaceVariant = Color(0xFF4A4A52),
    surfaceContainer = Color(0xFFEFEFF3),
    surfaceContainerHigh = Color(0xFFE9E9EE),
    surfaceContainerHighest = Color(0xFFE2E2E8),
    surfaceContainerLow = Color(0xFFFFFFFF),
)

private fun Typography.conFuente(f: FontFamily) = copy(
    displayLarge = displayLarge.copy(fontFamily = f),
    displayMedium = displayMedium.copy(fontFamily = f),
    displaySmall = displaySmall.copy(fontFamily = f),
    headlineLarge = headlineLarge.copy(fontFamily = f),
    headlineMedium = headlineMedium.copy(fontFamily = f),
    headlineSmall = headlineSmall.copy(fontFamily = f),
    titleLarge = titleLarge.copy(fontFamily = f),
    titleMedium = titleMedium.copy(fontFamily = f),
    titleSmall = titleSmall.copy(fontFamily = f),
    bodyLarge = bodyLarge.copy(fontFamily = f),
    bodyMedium = bodyMedium.copy(fontFamily = f),
    bodySmall = bodySmall.copy(fontFamily = f),
    labelLarge = labelLarge.copy(fontFamily = f),
    labelMedium = labelMedium.copy(fontFamily = f),
    labelSmall = labelSmall.copy(fontFamily = f),
)

@Composable
fun LectorTheme(
    acento: Color = Paletas.porDefecto.color,
    oscuro: Boolean = true,
    puro: Boolean = true,
    content: @Composable () -> Unit,
) {
    val base = if (!oscuro) EsquemaClaro else if (puro) EsquemaPuro else EsquemaOscuro
    MaterialTheme(
        colorScheme = base.copy(primary = acento, onPrimary = textoSobre(acento)),
        typography = Typography().conFuente(Inter),
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
