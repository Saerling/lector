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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
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
            LectorTheme {
                App(almacen)
            }
        }
    }
}

@Composable
fun App(almacen: Almacen) {
    val contexto = LocalContext.current
    val scope = rememberCoroutineScope()
    var comics by remember { mutableStateOf(almacen.cargar()) }
    var abierto by remember { mutableStateOf<Comic?>(null) }

    val selector = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val nuevo = withContext(Dispatchers.IO) { almacen.importar(uri) }
                if (nuevo == null) {
                    Toast.makeText(contexto, "No se pudo abrir ese archivo", Toast.LENGTH_SHORT).show()
                }
                comics = almacen.cargar()
            }
        }
    }

    val actual = abierto
    if (actual == null) {
        BibliotecaScreen(
            comics = comics,
            almacen = almacen,
            alAnadir = { selector.launch(arrayOf("*/*")) },
            alAbrir = { abierto = it },
            alEliminar = {
                almacen.eliminar(it.id)
                comics = almacen.cargar()
            },
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
