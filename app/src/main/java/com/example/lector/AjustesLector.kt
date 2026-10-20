package com.example.lector

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

// Color detrás de la página.
enum class Fondo { NEGRO, GRIS, BLANCO }

// Bloqueo de orientación mientras lees.
enum class Orientacion { LIBRE, VERTICAL, HORIZONTAL }

// Capa de color sobre la página (por ejemplo, un tono cálido para leer de noche).
enum class Tinte { NINGUNO, CALIDO, SEPIA, ROJO, VERDE, AZUL }

fun colorFondo(f: Fondo): Color = when (f) {
    Fondo.NEGRO -> Color.Black
    Fondo.GRIS -> Color(0xFF2B2B2B)
    Fondo.BLANCO -> Color.White
}

fun colorTinte(t: Tinte): Color = when (t) {
    Tinte.NINGUNO -> Color.Transparent
    Tinte.CALIDO -> Color(0xFFFF9A3C)
    Tinte.SEPIA -> Color(0xFF8B5A2B)
    Tinte.ROJO -> Color(0xFFFF2A2A)
    Tinte.VERDE -> Color(0xFF3DDC84)
    Tinte.AZUL -> Color(0xFF4C8DFF)
}

private fun nombreFondo(f: Fondo) = when (f) {
    Fondo.NEGRO -> "Negro"
    Fondo.GRIS -> "Gris"
    Fondo.BLANCO -> "Blanco"
}

private fun nombreOrientacion(o: Orientacion) = when (o) {
    Orientacion.LIBRE -> "Automática"
    Orientacion.VERTICAL -> "Vertical"
    Orientacion.HORIZONTAL -> "Horizontal"
}

private fun nombreTinte(t: Tinte) = when (t) {
    Tinte.NINGUNO -> "Ninguno"
    Tinte.CALIDO -> "Cálido"
    Tinte.SEPIA -> "Sepia"
    Tinte.ROJO -> "Rojo"
    Tinte.VERDE -> "Verde"
    Tinte.AZUL -> "Azul"
}

@Composable
private fun TituloSeccion(texto: String) {
    Text(
        texto,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
    )
}

// Ajustes del lector. Se usa desde el menú de la biblioteca y desde la barra del propio lector,
// donde los cambios se ven al momento.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DialogoLector(almacen: Almacen, alCerrar: () -> Unit) {
    AlertDialog(
        onDismissRequest = alCerrar,
        title = { Text("Lector") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                TituloSeccion("Navegación")
                Spacer(Modifier.height(8.dp))
                FilaInterruptor(
                    titulo = "Tocar los bordes para pasar de página",
                    detalle = "Izquierda y derecha cambian de página; el centro muestra " +
                        "u oculta la barra.",
                    activado = almacen.zonasToque,
                    alCambiar = { almacen.elegirZonasToque(it) },
                )
                Spacer(Modifier.height(12.dp))
                FilaInterruptor(
                    titulo = "Teclas de volumen",
                    detalle = "Bajar el volumen avanza y subirlo retrocede.",
                    activado = almacen.teclasVolumen,
                    alCambiar = { almacen.elegirTeclasVolumen(it) },
                )
                Spacer(Modifier.height(12.dp))
                FilaInterruptor(
                    titulo = "Mantener la pantalla encendida",
                    detalle = null,
                    activado = almacen.mantenerEncendida,
                    alCambiar = { almacen.elegirMantenerEncendida(it) },
                )

                Spacer(Modifier.height(20.dp))
                AjustesPantallaImagen(almacen)
            }
        },
        confirmButton = {
            TextButton(onClick = alCerrar) { Text("Listo") }
        },
    )
}

// Secciones «Pantalla» e «Imagen»: las comparten el lector local y el online.
@Composable
fun AjustesPantallaImagen(almacen: Almacen) {
    Column {
        TituloSeccion("Pantalla")
        Spacer(Modifier.height(8.dp))
        Text("Color de fondo")
        Fondo.entries.forEach { f ->
            FilaOpcion(etiqueta = nombreFondo(f), marcada = almacen.fondoLector == f) {
                almacen.elegirFondoLector(f)
            }
        }
        Spacer(Modifier.height(12.dp))
        Text("Orientación")
        Orientacion.entries.forEach { o ->
            FilaOpcion(etiqueta = nombreOrientacion(o), marcada = almacen.orientacion == o) {
                almacen.elegirOrientacion(o)
            }
        }
        Spacer(Modifier.height(12.dp))
        FilaInterruptor(
            titulo = "Brillo personalizado",
            detalle = "Usa un brillo propio solo mientras lees.",
            activado = almacen.brilloPersonalizado,
            alCambiar = { almacen.elegirBrilloPersonalizado(it) },
        )
        if (almacen.brilloPersonalizado) {
            Slider(
                value = almacen.brillo,
                onValueChange = { almacen.elegirBrillo(it) },
                valueRange = 0.01f..1f,
            )
        }

        Spacer(Modifier.height(20.dp))
        TituloSeccion("Imagen")
        Spacer(Modifier.height(8.dp))
        FilaInterruptor(
            titulo = "Escala de grises",
            detalle = null,
            activado = almacen.escalaGrises,
            alCambiar = { almacen.elegirEscalaGrises(it) },
        )
        Spacer(Modifier.height(12.dp))
        FilaInterruptor(
            titulo = "Invertir colores",
            detalle = "Útil para leer de noche páginas con fondo blanco.",
            activado = almacen.invertirColores,
            alCambiar = { almacen.elegirInvertirColores(it) },
        )
        Spacer(Modifier.height(12.dp))
        FilaInterruptor(
            titulo = "Recortar bordes",
            detalle = "Quita los márgenes lisos (blancos o negros) de cada página. " +
                "Solo en los modos paginados.",
            activado = almacen.recortarBordes,
            alCambiar = { almacen.elegirRecortarBordes(it) },
        )
        Spacer(Modifier.height(12.dp))
        Text("Filtro de color")
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Tinte.entries.forEach { t ->
                FilterChip(
                    selected = almacen.tinte == t,
                    onClick = { almacen.elegirTinte(t) },
                    label = { Text(nombreTinte(t)) },
                )
            }
        }
        if (almacen.tinte != Tinte.NINGUNO) {
            Text(
                "Intensidad",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Slider(
                value = almacen.intensidadTinte,
                onValueChange = { almacen.elegirIntensidadTinte(it) },
                valueRange = 0.05f..0.6f,
            )
        }
    }
}

// Solo pantalla e imagen (sin las opciones de navegación del lector local).
@Composable
fun DialogoPantallaLector(almacen: Almacen, alCerrar: () -> Unit) {
    AlertDialog(
        onDismissRequest = alCerrar,
        title = { Text("Pantalla y color") },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) { AjustesPantallaImagen(almacen) } },
        confirmButton = { TextButton(onClick = alCerrar) { Text("Listo") } },
    )
}
