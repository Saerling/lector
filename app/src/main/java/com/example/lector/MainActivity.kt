package com.example.lector

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import android.Manifest
import android.app.KeyguardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.runtime.SideEffect
import com.example.lector.ui.TemaActual
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.graphics.graphicsLayer
import com.example.lector.ui.ArranqueOverlay
import com.example.lector.ui.Inicio
import com.example.lector.ui.LectorTab
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private lateinit var almacen: Almacen

    // Destino pedido por una notificación (lo consume la pantalla principal).
    private var destino by mutableStateOf<String?>(null)

    // Bloqueo de la app (Ajustes → Seguridad): se pide el PIN / huella del sistema al volver.
    private var bloqueada by mutableStateOf(false)
    // Detalle del último cierre inesperado (se enseña al abrir).
    private var fallo by mutableStateOf<String?>(null)
    private var salioEn = 0L

    private val desbloqueo = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == RESULT_OK) bloqueada = false
    }
    private val permisoNotificaciones = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    // «Quitar animaciones» puede cambiar con la app en segundo plano.
    override fun onResume() {
        super.onResume()
        com.example.lector.ui.MovimientoReducido.actualizar()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Fallos.instalar(applicationContext)
        com.example.lector.ui.MovimientoReducido.actualizar()
        com.example.lector.ui.Tacto.iniciar(window.decorView)
        if (savedInstanceState == null) fallo = Fallos.recoger(applicationContext)
        almacen = Almacen.obtener(applicationContext)
        aplicarBarras(oscuro = almacen.modoTema != ModoTema.CLARO)
        com.example.lector.ui.Imagenes.iniciar(applicationContext)
        Notificaciones.crearCanales(applicationContext)
        Programador.programar(applicationContext)
        if (savedInstanceState == null) {
            bloqueada = almacen.bloqueo && dispositivoSeguro()
            pedirPermisoNotificaciones()
            // App abierta de nuevo: Novedades se vuelven a pedir (dentro de la misma sesión se reutilizan).
            com.example.lector.ui.NovedadesGuardadas.olvidar()
            // ¿Hay versión nueva en GitHub? Si la hay, sale el aviso para actualizar.
            NuevaVersion.alArrancar(applicationContext)
        }
        procesarEnlace(intent)
        setContent {
            App(
                almacen,
                destino = destino, alConsumirDestino = { destino = null },
                bloqueada = bloqueada, alDesbloquear = ::pedirDesbloqueo,
                alCambiarTema = { aplicarBarras(it) },
            )
            fallo?.let { texto -> DialogoFallo(texto) { fallo = null } }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        procesarEnlace(intent)
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) salioEn = System.currentTimeMillis()
    }

    override fun onStart() {
        super.onStart()
        // Si pasó el tiempo elegido fuera de la app, vuelve a bloquear.
        if (salioEn > 0 && almacen.bloqueo && dispositivoSeguro() &&
            System.currentTimeMillis() - salioEn >= almacen.bloqueoMinutos * 60_000L
        ) bloqueada = true
        // Pantalla segura: oculta el contenido en "recientes" y bloquea las capturas.
        if (almacen.pantallaSegura) window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }

    private fun aplicarBarras(oscuro: Boolean) {
        val estilo = if (oscuro) SystemBarStyle.dark(Color.TRANSPARENT)
            else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        enableEdgeToEdge(statusBarStyle = estilo, navigationBarStyle = estilo)
    }

    private fun dispositivoSeguro(): Boolean =
        (getSystemService(KEYGUARD_SERVICE) as? KeyguardManager)?.isDeviceSecure == true

    @Suppress("DEPRECATION")
    private fun pedirDesbloqueo() {
        val km = getSystemService(KEYGUARD_SERVICE) as? KeyguardManager
        val i = km?.createConfirmDeviceCredentialIntent("Lector", "Desbloquea para entrar")
        if (i == null) bloqueada = false else runCatching { desbloqueo.launch(i) }.onFailure { bloqueada = false }
    }

    private fun pedirPermisoNotificaciones() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) permisoNotificaciones.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    // Tras iniciar sesión, AniList nos devuelve aquí: lector://anilist#access_token=...
    // Las notificaciones traen el extra "destino" (actualizaciones, descargas, extensiones).
    private fun procesarEnlace(intent: Intent?) {
        intent?.getStringExtra("destino")?.let { destino = it; intent.removeExtra("destino") }
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
fun App(
    almacen: Almacen,
    destino: String? = null,
    alConsumirDestino: () -> Unit = {},
    bloqueada: Boolean = false,
    alDesbloquear: () -> Unit = {},
    alCambiarTema: (oscuro: Boolean) -> Unit = {},
) {
    // La animación de arranque solo se ve al abrir la app (no al girar la pantalla).
    var arranque by rememberSaveable { mutableStateOf(true) }
    val sistemaOscuro = isSystemInDarkTheme()
    val oscuro = when (almacen.modoTema) {
        ModoTema.SISTEMA -> sistemaOscuro
        ModoTema.CLARO -> false
        else -> true
    }
    val puro = almacen.modoTema != ModoTema.OSCURO
    // Los colores de la app (LectorColors) se leen de aquí.
    SideEffect { TemaActual.oscuro = oscuro; TemaActual.puro = puro }
    LaunchedEffect(oscuro) { alCambiarTema(oscuro) }
    LectorTheme(acento = colorDePaleta(almacen.paleta, oscuro), oscuro = oscuro, puro = puro) {
        Box(Modifier.fillMaxSize().background(com.example.lector.ui.LectorColors.Bg)) {
            // Mientras hay una hoja inferior abierta, la app se aleja un poco y redondea las esquinas (musicfy).
            val alejar by androidx.compose.animation.core.animateFloatAsState(
                if (com.example.lector.ui.Hojas.abiertas > 0) 1f else 0f,
                androidx.compose.animation.core.spring(
                    dampingRatio = androidx.compose.animation.core.Spring.DampingRatioNoBouncy,
                    stiffness = androidx.compose.animation.core.Spring.StiffnessLow,
                    visibilityThreshold = 0.0005f,
                ),
                label = "alejar",
            )
            Box(
                Modifier.fillMaxSize().graphicsLayer {
                    if (alejar > 0f) {
                        val e = 1f - 0.08f * alejar
                        scaleX = e; scaleY = e
                        shape = androidx.compose.foundation.shape.RoundedCornerShape((28f * alejar).dp)
                        clip = true
                    }
                },
            ) { Contenido(almacen, destino, alConsumirDestino) }
            if (arranque) ArranqueOverlay { arranque = false }
            if (!bloqueada) com.example.lector.ui.DialogoActualizacion()
            if (bloqueada) PantallaBloqueo(alDesbloquear)
        }
    }
}

@Composable
private fun PantallaBloqueo(alDesbloquear: () -> Unit) {
    LaunchedEffect(Unit) { alDesbloquear() }
    BackHandler { }
    Box(
        Modifier.fillMaxSize().background(com.example.lector.ui.LectorColors.Bg).pointerInput(Unit) { detectTapGestures { } },
        Alignment.Center,
    ) {
        androidx.compose.foundation.layout.Column(horizontalAlignment = Alignment.CenterHorizontally) {
            androidx.compose.material3.Icon(
                androidx.compose.material.icons.Icons.Rounded.Lock, null,
                tint = com.example.lector.ui.LectorColors.Text, modifier = Modifier.size(48.dp),
            )
            Spacer(Modifier.size(16.dp))
            com.example.lector.ui.PillButton("Desbloquear", androidx.compose.material.icons.Icons.Rounded.Lock, alDesbloquear)
        }
    }
}

@Composable
private fun Contenido(almacen: Almacen, destino: String?, alConsumirDestino: () -> Unit) {
    val contexto = LocalContext.current
    val scope = rememberCoroutineScope()
    var comics by remember { mutableStateOf(almacen.cargar()) }
    var abierto by remember { mutableStateOf<Comic?>(null) }
    var importando by remember { mutableStateOf(false) }
    // Pestaña de la barra inferior y vista (biblioteca/historial) dentro de la pestaña Biblioteca.
    var tab by rememberSaveable { mutableStateOf(LectorTab.Home) }
    // Al terminar una copia (crear o restaurar) se relee la biblioteca.
    LaunchedEffect(TareaCopia.terminadas) { if (TareaCopia.terminadas > 0) comics = almacen.cargar() }

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
                    Toast.makeText(contexto, "No se pudo abrir ese archivo (¿PDF con contraseña o archivo dañado?)", Toast.LENGTH_SHORT).show()
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
    // Inicio se queda en composición mientras lees: así, al cerrar el capítulo, vuelves exactamente donde estabas
    // (ficha abierta, scroll, lista de la extensión...) en vez de recargar todo desde cero.
    Box(Modifier.fillMaxSize()) {
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
            destino = destino,
            alConsumirDestino = alConsumirDestino,
            activo = actual == null,
        )
        if (actual != null) {
            val cerrar = {
                abierto = null
                comics = almacen.cargar()
            }
            BackHandler { cerrar() }
            // El lector tapa todo y se queda con los toques (Inicio sigue vivo debajo).
            Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { } }) {
                LectorScreen(comic = actual, almacen = almacen, alCerrar = cerrar)
            }
        }
    }
}

/** Aviso de que la app se cerró la última vez, con el detalle para copiar y mandarlo. */
@Composable
private fun DialogoFallo(texto: String, alCerrar: () -> Unit) {
    val ctx = LocalContext.current
    AlertDialog(
        onDismissRequest = alCerrar,
        title = { Text("La app se cerró por un error") },
        text = {
            androidx.compose.foundation.layout.Column(
                Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())
            ) {
                Text("Copia este detalle y mándalo para poder arreglarlo:")
                Spacer(Modifier.size(8.dp))
                Text(texto, style = androidx.compose.ui.text.TextStyle(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, fontSize = androidx.compose.ui.unit.TextUnit(10f, androidx.compose.ui.unit.TextUnitType.Sp)))
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = {
                val cm = ctx.getSystemService(android.content.ClipboardManager::class.java)
                cm?.setPrimaryClip(android.content.ClipData.newPlainText("Error de Lector", texto))
                Toast.makeText(ctx, "Copiado", Toast.LENGTH_SHORT).show()
            }) { Text("Copiar") }
        },
        dismissButton = { androidx.compose.material3.TextButton(onClick = alCerrar) { Text("Cerrar") } },
    )
}
