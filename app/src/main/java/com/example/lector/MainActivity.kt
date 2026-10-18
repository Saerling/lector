package com.example.lector

import android.graphics.Color
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.lector.ui.Inicio
import com.example.lector.ui.LectorTab
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        val almacen = Almacen(applicationContext)
        setContent {
            App(almacen)
        }
    }
}

@Composable
fun App(almacen: Almacen) {
    LectorTheme(acento = colorDePaleta(almacen.paleta)) {
        Contenido(almacen)
    }
}

@Composable
private fun Contenido(almacen: Almacen) {
    val contexto = LocalContext.current
    val scope = rememberCoroutineScope()
    var comics by remember { mutableStateOf(almacen.cargar()) }
    var abierto by remember { mutableStateOf<Comic?>(null) }
    var importando by remember { mutableStateOf(false) }
    var pestana by rememberSaveable { mutableStateOf(LectorTab.Home) }

    // Añadir un archivo .cbz.
    val selector = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                importando = true
                val nuevo = withContext(Dispatchers.IO) {
                    runCatching { almacen.importar(uri) }.getOrNull()
                }
                importando = false
                if (nuevo == null) {
                    Toast.makeText(contexto, "No se pudo abrir ese archivo", Toast.LENGTH_SHORT).show()
                }
                comics = almacen.cargar()
            }
        }
    }

    // Añadir una carpeta de imágenes.
    val selectorCarpeta = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            scope.launch {
                importando = true
                val nuevo = withContext(Dispatchers.IO) {
                    runCatching { almacen.importarCarpeta(uri) }.getOrNull()
                }
                importando = false
                if (nuevo == null) {
                    Toast.makeText(
                        contexto,
                        "No se encontraron imágenes en esa carpeta",
                        Toast.LENGTH_SHORT,
                    ).show()
                }
                comics = almacen.cargar()
            }
        }
    }

    // Aviso mientras se copia o se empaqueta (puede tardar con archivos grandes).
    if (importando) {
        AlertDialog(
            onDismissRequest = {},
            confirmButton = {},
            title = { Text("Importando…") },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(24.dp))
                    Spacer(Modifier.width(16.dp))
                    Text("Esto puede tardar con archivos o carpetas grandes.")
                }
            },
        )
    }

    val actual = abierto
    if (actual == null) {
        Inicio(
            tab = pestana,
            onTab = { pestana = it },
            comics = comics,
            almacen = almacen,
            alAnadir = { selector.launch(arrayOf("*/*")) },
            alAnadirCarpeta = { selectorCarpeta.launch(null) },
            alAbrir = { abierto = it },
            alEliminar = {
                almacen.eliminar(it.id)
                comics = almacen.cargar()
            },
            alRestaurar = { comics = almacen.cargar() },
        )
    } else {
        val cerrar = {
            abierto = null
            comics = almacen.cargar()
        }
        BackHandler { cerrar() }
        LectorScreen(comic = actual, almacen = almacen, alCerrar = cerrar)
    }
}
