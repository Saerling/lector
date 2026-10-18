package com.example.lector.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.lector.Almacen
import com.example.lector.Anilist
import com.example.lector.DialogoLector
import com.example.lector.Paletas
import com.example.lector.Vista
import com.example.lector.colorDePaleta
import com.example.lector.nombreVista
import com.example.lector.textoSobre

/** Ajustes: lista de secciones. "Visor" reutiliza tu diálogo del lector (se rehace como pantalla más adelante). */
@Composable
fun AjustesScreen(almacen: Almacen, onBack: () -> Unit, onRuta: (Ruta) -> Unit) {
    var visor by remember { mutableStateOf(false) }
    PaginaAjustes("Ajustes", onBack) {
        FilaAjuste(Icons.Rounded.Create, "Apariencia", "Color de acento y portadas") { onRuta(Ruta.Apariencia) }
        FilaAjuste(Icons.Rounded.List, "Biblioteca", "Vista, columnas, insignias y categorías") { onRuta(Ruta.AjBiblioteca) }
        FilaAjuste(Icons.Rounded.PlayArrow, "Visor", "Modos de lectura, apariencia y navegación") { visor = true }
        FilaAjuste(Icons.Rounded.Refresh, "Seguimiento", "Sincroniza tu progreso de lectura con AniList") { onRuta(Ruta.AjSeguimiento) }
        FilaAjuste(Icons.Rounded.Share, "Datos y almacenamiento", "Copias de seguridad") { onRuta(Ruta.Datos) }
    }
    if (visor) DialogoLector(almacen = almacen, alCerrar = { visor = false })
}

@Composable
fun AparienciaScreen(almacen: Almacen, onBack: () -> Unit) {
    val opciones = Paletas.fijas.map { it.nombre to it.color } +
        if (Build.VERSION.SDK_INT >= 31) listOf(Paletas.SISTEMA to colorDePaleta(Paletas.SISTEMA)) else emptyList()
    PaginaAjustes("Apariencia", onBack) {
        TituloSeccion("Color de acento")
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            opciones.forEach { (nombre, color) ->
                TarjetaTema(nombre, color, almacen.paleta == nombre) { almacen.elegirPaleta(nombre) }
            }
        }
        if (Build.VERSION.SDK_INT >= 31) {
            LText("«Sistema» usa los colores de tu fondo de pantalla.", LectorType.Label, Modifier.padding(24.dp, 12.dp), color = LectorColors.Muted)
        }
        Divisor()
        TituloSeccion("Información del cómic")
        FilaSwitch(
            "Tema según la portada", "La página del cómic y el lector toman el color de la portada.",
            almacen.colorPortada
        ) { almacen.elegirColorPortada(it) }
    }
}

/** Mini maqueta de teléfono con el color elegido, como las tarjetas de tema de Komikku. */
@Composable
private fun TarjetaTema(nombre: String, color: Color, seleccionada: Boolean, onClick: () -> Unit) {
    val forma = RoundedCornerShape(24.dp)
    Column(Modifier.width(104.dp).pressable(onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.fillMaxWidth().height(150.dp).clip(forma).background(Color.Black)
                .border(if (seleccionada) 3.dp else 2.dp, if (seleccionada) color else Color(0xFF3A3A3C), forma)
        ) {
            Box(Modifier.padding(12.dp).size(width = 46.dp, height = 12.dp).clip(CircleShape).background(Color.White))
            Box(Modifier.align(Alignment.Center).size(width = 56.dp, height = 62.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFF44464F))) {
                Box(Modifier.padding(6.dp).size(width = 22.dp, height = 12.dp).clip(CircleShape).background(color))
            }
            Row(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color(0xFF1A1B20)).padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(14.dp).clip(CircleShape).background(color))
                Spacer(Modifier.width(6.dp))
                Box(Modifier.weight(1f).height(12.dp).clip(CircleShape).background(Color(0xFF9E9E9E)))
            }
            if (seleccionada) {
                Box(
                    Modifier.align(Alignment.TopEnd).padding(8.dp).size(22.dp).clip(CircleShape).background(color),
                    Alignment.Center
                ) { Icon(Icons.Rounded.Check, null, tint = textoSobre(color), modifier = Modifier.size(14.dp)) }
            }
        }
        Spacer(Modifier.height(8.dp))
        LText(nombre, LectorType.Label, color = LectorColors.Muted, maxLines = 1)
    }
}

@Composable
fun AjBibliotecaScreen(almacen: Almacen, onBack: () -> Unit, onRuta: (Ruta) -> Unit) {
    PaginaAjustes("Biblioteca", onBack) {
        TituloSeccion("Vista")
        Vista.entries.forEach { v -> FilaRadio(nombreVista(v), almacen.vista == v) { almacen.elegirVista(v) } }
        if (almacen.vista != Vista.LISTA) {
            TituloSeccion("Columnas")
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                listOf(0, 2, 3, 4, 5).forEach { n ->
                    ChipSel(if (n == 0) "Auto" else "$n", almacen.columnas == n) { almacen.elegirColumnas(n) }
                }
            }
        }
        Divisor()
        FilaSwitch("Mostrar la página actual", "«Pág. X de Y» junto a cada cómic.", almacen.mostrarPagina) { almacen.elegirMostrarPagina(it) }
        FilaSwitch("Insignia de progreso", "Porcentaje sobre la portada, o ✓ si está terminado.", almacen.insigniaProgreso) { almacen.elegirInsigniaProgreso(it) }
        Divisor()
        FilaAjuste(Icons.Rounded.Menu, "Categorías", "Crear, ocultar y renombrar") { onRuta(Ruta.Categorias) }
    }
}

@Composable
fun AjSeguimientoScreen(almacen: Almacen, onBack: () -> Unit) {
    val contexto = LocalContext.current
    var clientId by remember { mutableStateOf(almacen.anilistClientId) }
    PaginaAjustes("Seguimiento", onBack) {
        if (almacen.anilistToken != null) {
            FilaAjuste(Icons.Rounded.Person, "Conectado como ${almacen.anilistUsuario ?: "…"}", "AniList") {}
            LText(
                "Abre un cómic y toca «Seguimiento» para vincularlo con su serie. Al llegar a su última página, se sube el progreso.",
                LectorType.Label.copy(fontSize = 13.sp), Modifier.padding(24.dp, 8.dp), color = LectorColors.Muted
            )
            Box(Modifier.padding(24.dp, 8.dp)) { PillButton("Cerrar sesión", Icons.Rounded.Close, { almacen.cerrarSesionAnilist() }) }
        } else {
            LText(
                "1. En anilist.co → Settings → Developer, crea una aplicación con la URL de redirección  lector://anilist\n" +
                    "2. Pega aquí su Client ID (un número) y pulsa «Iniciar sesión».",
                LectorType.Label.copy(fontSize = 13.sp), Modifier.padding(24.dp, 8.dp), color = LectorColors.Muted
            )
            OutlinedTextField(
                value = clientId, onValueChange = { clientId = it.filter { c -> c.isDigit() } },
                singleLine = true, label = { Text("Client ID") },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)
            )
            Box(Modifier.padding(24.dp, 12.dp)) {
                PillButton("Iniciar sesión", Icons.Rounded.Person, {
                    if (clientId.isNotEmpty()) {
                        almacen.guardarClientId(clientId)
                        contexto.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(Anilist.urlDeLogin(clientId))))
                    }
                })
            }
        }
    }
}

@Composable
fun DatosScreen(almacen: Almacen, alRecargar: () -> Unit, onBack: () -> Unit) {
    val contexto = LocalContext.current
    var resultado by remember { mutableStateOf<String?>(null) }

    val crear = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            resultado = try {
                val salida = contexto.contentResolver.openOutputStream(uri) ?: throw IllegalStateException("Sin salida")
                salida.use { it.write(almacen.crearCopia().toByteArray(Charsets.UTF_8)) }
                "Copia guardada."
            } catch (e: Exception) {
                "No se pudo guardar la copia."
            }
        }
    }
    val restaurar = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            resultado = try {
                val entrada = contexto.contentResolver.openInputStream(uri) ?: throw IllegalStateException("Sin entrada")
                val texto = entrada.use { it.readBytes().toString(Charsets.UTF_8) }
                val r = almacen.restaurarCopia(texto)
                alRecargar()
                "Copia restaurada: ${r.actualizados} cómics actualizados" +
                    if (r.pendientes > 0) {
                        " y ${r.pendientes} pendientes. Su progreso y categorías se aplicarán cuando añadas esos archivos."
                    } else {
                        "."
                    }
            } catch (e: Exception) {
                "El archivo no es una copia válida."
            }
        }
    }

    PaginaAjustes("Datos y almacenamiento", onBack) {
        TituloSeccion("Copia de seguridad")
        LText(
            "Guarda tu progreso, categorías, modos de lectura y ajustes. No incluye los archivos de los cómics.",
            LectorType.Label.copy(fontSize = 13.sp), Modifier.padding(horizontal = 24.dp), color = LectorColors.Muted
        )
        Spacer(Modifier.height(8.dp))
        FilaAjuste(Icons.Rounded.Share, "Crear copia", "Guardar en un archivo .json") { crear.launch("lector-copia.json") }
        FilaAjuste(Icons.Rounded.Refresh, "Restaurar copia", "Elegir un archivo de copia") { restaurar.launch(arrayOf("*/*")) }
    }
    resultado?.let {
        AlertDialog(
            onDismissRequest = { resultado = null },
            title = { Text("Copia de seguridad") },
            text = { Text(it) },
            confirmButton = { TextButton(onClick = { resultado = null }) { Text("Aceptar") } },
        )
    }
}
