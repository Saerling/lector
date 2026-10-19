package com.example.lector

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.example.lector.ui.ArranqueOverlay
import com.example.lector.ui.Inicio
import com.example.lector.ui.LectorTab
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private lateinit var almacen: Almacen

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        almacen = Almacen(applicationContext)
        procesarEnlace(intent)
        setContent {
            App(almacen)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        procesarEnlace(intent)
    }

    // Tras iniciar sesión, AniList nos devuelve aquí: lector://anilist#access_token=...
    private fun procesarEnlace(intent: Intent?) {
        val datos = intent?.data ?: return
        if (datos.scheme != "lector" || datos.host != "anilist") return
        val token = datos.fragment
            ?.split("&")
            ?.map { it.split("=", limit = 2) }
            ?.firstOrNull { it.size == 2 && it[0] == "access_token" }
            ?.get(1)
        if (!token.isNullOrEmpty()) {
            almacen.guardarTokenAnilist(token)
            Toast.makeText(this, "Sesión de AniList iniciada", Toast.LENGTH_SHORT).show()
        }
    }

    // Teclas de volumen: solo se interceptan mientras el lector las tiene activadas.
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (Teclas.activo &&
            (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP)
        ) {
            if (event?.repeatCount == 0) {
                Teclas.eventos.tryEmit(if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) 1 else -1)
            }
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (Teclas.activo &&
            (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP)
        ) {
            return true
        }
        return super.onKeyUp(keyCode, event)
    }
}

@Composable
fun App(almacen: Almacen) {
    // La animación de arranque solo se ve al abrir la app (no al girar la pantalla).
    var arranque by rememberSaveable { mutableStateOf(true) }
    LectorTheme(acento = colorDePaleta(almacen.paleta)) {
        Box(Modifier.fillMaxSize()) {
            Contenido(almacen)
            if (arranque) ArranqueOverlay { arranque = false }
        }
    }
}

@Composable
private fun Contenido(almacen: Almacen) {
    val contexto = LocalContext.current
    val scope = rememberCoroutineScope()
    var comics by remember { mutableStateOf(almacen.cargar()) }
    var abierto by remember { mutableStateOf<Comic?>(null) }
    var importando by remember { mutableStateOf(false) }
    // Pestaña de la barra inferior y vista (biblioteca/historial) dentro de la pestaña Biblioteca.
    var tab by rememberSaveable { mutableStateOf(LectorTab.Home) }
    var pestanaBiblioteca by rememberSaveable { mutableStateOf(0) }

    // Con sesión de AniList: averigua el nombre de usuario y sube lo que quedó pendiente.
    // Se repite al volver a la biblioteca.
    LaunchedEffect(almacen.anilistToken, abierto == null) {
        val token = almacen.anilistToken
        if (token != null && abierto == null) {
            withContext(Dispatchers.IO) {
                if (almacen.anilistUsuario == null) {
                    runCatching { Anilist.usuario(token) }.getOrNull()
                        ?.let { almacen.guardarUsuarioAnilist(it) }
                }
                almacen.sincronizarPendientes()
            }
        }
    }

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
            tab = tab,
            onTab = { tab = it },
            comics = comics,
            almacen = almacen,
            alAnadir = { selector.launch(arrayOf("*/*")) },
            alAnadirCarpeta = { selectorCarpeta.launch(null) },
            alAbrir = { abierto = it },
            alEliminar = {
                almacen.eliminar(it.id)
                comics = almacen.cargar()
            },
            alRecargar = { comics = almacen.cargar() },
            pestanaBiblioteca = pestanaBiblioteca,
            alCambiarPestanaBiblioteca = { pestanaBiblioteca = it },
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
