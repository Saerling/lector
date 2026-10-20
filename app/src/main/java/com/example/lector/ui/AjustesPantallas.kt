package com.example.lector.ui

import com.example.lector.TareaCopia
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.text.format.Formatter
import android.webkit.CookieManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
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
import com.example.lector.CopiaSeguridad
import com.example.lector.ModoTema
import com.example.lector.Paletas
import com.example.lector.Programador
import com.example.lector.Vista
import com.example.lector.VistaExplorar
import com.example.lector.colorDePaleta
import com.example.lector.ext.ExtManager
import com.example.lector.ext.bandera
import com.example.lector.ext.nombreIdioma
import com.example.lector.nombreVista
import com.example.lector.textoSobre
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Ajustes: lista de secciones (como la de Komikku). */
@Composable
fun AjustesScreen(almacen: Almacen, onBack: () -> Unit, onRuta: (Ruta) -> Unit) {
    PaginaAjustes("Ajustes", onBack) {
        FilaAjuste(Icons.Rounded.Create, "Apariencia", "Tema claro / oscuro, color de acento y portadas") { onRuta(Ruta.Apariencia) }
        FilaAjuste(Icons.Rounded.List, "Biblioteca", "Vista, categorías y actualizaciones automáticas") { onRuta(Ruta.AjBiblioteca) }
        FilaAjuste(Icons.Rounded.PlayArrow, "Visor", "Modos de lectura, zonas de toque, zoom, filtros") { onRuta(Ruta.AjVisor) }
        FilaAjuste(Iconos.Descargar, "Descargas", "Solo Wi‑Fi, borrar al leer, descargar por adelantado") { onRuta(Ruta.AjDescargas) }
        FilaAjuste(Icons.Rounded.Refresh, "Seguimiento", "Sincroniza tu progreso con AniList") { onRuta(Ruta.AjSeguimiento) }
        FilaAjuste(Icons.Rounded.Search, "Explorar", "Contenido +18, idiomas, listados de extensiones") { onRuta(Ruta.AjExplorar) }
        FilaAjuste(Icons.Rounded.Share, "Datos y almacenamiento", "Copias de seguridad (también automáticas) y cachés") { onRuta(Ruta.Datos) }
        FilaAjuste(Icons.Rounded.Lock, "Seguridad y privacidad", "Bloqueo de la app, pantalla segura, modo incógnito") { onRuta(Ruta.AjSeguridad) }
        FilaAjuste(Icons.Rounded.Build, "Avanzado", "DNS sobre HTTPS, cookies, batería") { onRuta(Ruta.AjAvanzado) }
        FilaAjuste(Icons.Rounded.Info, "Acerca de", null) { onRuta(Ruta.AcercaDe) }
    }
}

// ───────────────────── Apariencia ─────────────────────

private fun nombreTema(m: ModoTema) = when (m) {
    ModoTema.SISTEMA -> "Según el sistema"
    ModoTema.OSCURO_PURO -> "Negro puro"
    ModoTema.OSCURO -> "Oscuro"
    ModoTema.CLARO -> "Claro"
}

@Composable
fun AparienciaScreen(almacen: Almacen, onBack: () -> Unit) {
    val opciones = Paletas.fijas.map { it.nombre to it.color } +
        if (Build.VERSION.SDK_INT >= 31) listOf(Paletas.SISTEMA to colorDePaleta(Paletas.SISTEMA)) else emptyList()
    val acento = androidx.compose.material3.MaterialTheme.colorScheme.primary
    PaginaAjustes("Apariencia", onBack) {
        TituloSeccion("Tema")
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            ModoTema.entries.forEach { m ->
                val (fondo, tarjeta, texto) = when (m) {
                    ModoTema.CLARO -> Triple(Color(0xFFF6F6F8), Color.White, Color(0xFF111114))
                    ModoTema.OSCURO -> Triple(Color(0xFF111114), Color(0xFF26262C), Color.White)
                    ModoTema.OSCURO_PURO -> Triple(Color.Black, Color(0xFF1E1E20), Color.White)
                    ModoTema.SISTEMA -> Triple(Color(0xFF6B6B73), Color(0xFFB0B0B8), Color.White)
                }
                TarjetaTema(nombreTema(m), acento, almacen.modoTema == m, fondo, tarjeta, texto) { almacen.modoTema = m }
            }
        }
        Divisor()
        TituloSeccion("Color de acento")
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            opciones.forEach { (nombre, color) ->
                TarjetaTema(nombre, color, almacen.paleta == nombre, LectorColors.Bg, LectorColors.CardHigh, LectorColors.Text) { almacen.elegirPaleta(nombre) }
            }
        }
        if (Build.VERSION.SDK_INT >= 31) {
            LText("«Sistema» usa los colores de tu fondo de pantalla.", LectorType.Label, Modifier.padding(24.dp, 12.dp), color = LectorColors.Muted)
        }
        Divisor()
        TituloSeccion("Información del cómic")
        FilaSwitch(
            "Tema según la portada", "La ficha y el lector toman el color de la portada.",
            almacen.colorPortada,
        ) { almacen.elegirColorPortada(it) }
    }
}

/** Mini maqueta de teléfono con el color elegido, como las tarjetas de tema de Komikku. */
@Composable
private fun TarjetaTema(
    nombre: String, color: Color, seleccionada: Boolean, fondo: Color, tarjeta: Color, texto: Color, onClick: () -> Unit,
) {
    val forma = SuaveShape(24.dp)
    Column(Modifier.width(104.dp).pressable(onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.fillMaxWidth().height(150.dp).clip(forma).background(fondo)
                .border(if (seleccionada) 3.dp else 2.dp, if (seleccionada) color else LectorColors.Linea, forma),
        ) {
            Box(Modifier.padding(12.dp).size(width = 46.dp, height = 12.dp).clip(CircleShape).background(texto))
            Box(Modifier.align(Alignment.Center).size(width = 56.dp, height = 62.dp).clip(SuaveShape(10.dp)).background(tarjeta)) {
                Box(Modifier.padding(6.dp).size(width = 22.dp, height = 12.dp).clip(CircleShape).background(color))
            }
            Row(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(tarjeta).padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(14.dp).clip(CircleShape).background(color))
                Spacer(Modifier.width(6.dp))
                Box(Modifier.weight(1f).height(12.dp).clip(CircleShape).background(texto.copy(alpha = 0.5f)))
            }
            if (seleccionada) {
                Box(
                    Modifier.align(Alignment.TopEnd).padding(8.dp).size(22.dp).clip(CircleShape).background(color),
                    Alignment.Center,
                ) { Icon(Icons.Rounded.Check, null, tint = textoSobre(color), modifier = Modifier.size(14.dp)) }
            }
        }
        Spacer(Modifier.height(8.dp))
        LText(nombre, LectorType.Label, color = LectorColors.Muted, maxLines = 1)
    }
}

// ───────────────────── Biblioteca y actualizaciones ─────────────────────

@Composable
fun AjBibliotecaScreen(almacen: Almacen, onBack: () -> Unit, onRuta: (Ruta) -> Unit) {
    val ctx = LocalContext.current
    fun reprogramar() = Programador.programar(ctx)
    PaginaAjustes("Biblioteca", onBack) {
        TituloSeccion("Vista")
        Vista.entries.forEach { v -> FilaRadio(nombreVista(v), almacen.vista == v) { almacen.elegirVista(v) } }
        if (almacen.vista != Vista.LISTA) {
            TituloSeccion("Columnas")
            FilaChips(listOf("Auto", "2", "3", "4", "5", "6"), listOf(0, 2, 3, 4, 5, 6).indexOf(almacen.columnas).coerceAtLeast(0)) {
                almacen.elegirColumnas(listOf(0, 2, 3, 4, 5, 6)[it])
            }
        }
        FilaAjuste(Icons.Rounded.Menu, "Categorías", "Crear, ordenar, ocultar y renombrar") { onRuta(Ruta.Categorias) }

        Divisor()
        TituloSeccion("Buscar capítulos nuevos automáticamente")
        val horas = listOf(0, 6, 12, 24, 48, 168)
        FilaChips(listOf("A mano", "Cada 6 h", "Cada 12 h", "Diario", "Cada 2 días", "Semanal"), horas.indexOf(almacen.intervaloActualizacion).coerceAtLeast(0)) {
            almacen.intervaloActualizacion = horas[it]; reprogramar()
        }
        if (almacen.intervaloActualizacion > 0) {
            FilaSwitch("Solo con Wi‑Fi", null, almacen.actSoloWifi) { almacen.actSoloWifi = it; reprogramar() }
            FilaSwitch("Solo cargando", null, almacen.actSoloCargando) { almacen.actSoloCargando = it; reprogramar() }
        }
        FilaSwitch("Saltar los completados", "No revisa los mangas terminados y sin capítulos por leer", almacen.actSaltarTerminados) { almacen.actSaltarTerminados = it }
        FilaSwitch("Descargar los capítulos nuevos", "Los pone en la cola de descargas al encontrarlos", almacen.descargarNuevos) { almacen.descargarNuevos = it }
        LText(
            "Las revisiones automáticas avisan con una notificación de los capítulos nuevos. Android puede retrasarlas para ahorrar batería.",
            LectorType.Label.copy(fontSize = 13.sp), Modifier.padding(horizontal = 24.dp, vertical = 8.dp), color = LectorColors.Muted,
        )
    }
}

// ───────────────────── Descargas ─────────────────────

@Composable
fun AjDescargasScreen(almacen: Almacen, onBack: () -> Unit, onRuta: (Ruta) -> Unit) {
    val ctx = LocalContext.current
    var confirmar by remember { mutableStateOf(false) }
    var version by remember { mutableIntStateOf(0) }
    val espacio = remember(version, almacen.version) { Formatter.formatShortFileSize(ctx, almacen.bytesDescargas()) }
    PaginaAjustes("Descargas", onBack) {
        FilaAjuste(Iconos.Descargar, "Cola de descargas", "${com.example.lector.Descargas.cola.size} pendientes") { onRuta(Ruta.Descargas) }
        Divisor()
        FilaSwitch("Solo con Wi‑Fi", "Las descargas esperan a tener Wi‑Fi", almacen.descSoloWifi) { almacen.descSoloWifi = it }
        TituloSeccion("Descargas a la vez")
        FilaChips(listOf("1", "2", "3", "4"), (almacen.descParalelas - 1).coerceIn(0, 3)) { almacen.descParalelas = it + 1 }
        Divisor()
        TituloSeccion("Al leer")
        FilaSwitch("Borrar al terminar de leer", "Borra el capítulo descargado cuando lo terminas", almacen.borrarAlLeer) { almacen.borrarAlLeer = it }
        LText("Descargar por adelantado (mangas de la biblioteca)", LectorType.Body.copy(fontSize = 15.sp), Modifier.padding(start = 24.dp, top = 10.dp))
        val opciones = listOf(0, 1, 2, 3, 5, 10)
        FilaChips(listOf("No", "1", "2", "3", "5", "10"), opciones.indexOf(almacen.descargarAdelante).coerceAtLeast(0)) {
            almacen.descargarAdelante = opciones[it]
        }
        Divisor()
        TituloSeccion("Almacenamiento")
        FilaAjuste(Icons.Rounded.Delete, "Borrar todas las descargas", "Ocupan $espacio") { confirmar = true }
    }
    if (confirmar) {
        AlertDialog(
            onDismissRequest = { confirmar = false },
            title = { Text("¿Borrar todas las descargas?") },
            text = { Text("Se borran los capítulos descargados. Podrás seguir leyéndolos online.") },
            confirmButton = {
                TextButton(onClick = {
                    almacen.cargar().filter { it.enLinea }.forEach { almacen.borrarDescargasDe(it.fuenteId, it.urlManga.orEmpty()) }
                    File(ctx.filesDir, "descargas").listFiles()?.forEach { it.delete() }
                    com.example.lector.Descargas.version.value++
                    version++
                    confirmar = false
                }) { Text("Borrar") }
            },
            dismissButton = { TextButton(onClick = { confirmar = false }) { Text("Cancelar") } },
        )
    }
}

// ───────────────────── Seguimiento ─────────────────────

@Composable
fun AjSeguimientoScreen(almacen: Almacen, onBack: () -> Unit) {
    val contexto = LocalContext.current
    var clientId by remember { mutableStateOf(almacen.anilistClientId) }
    PaginaAjustes("Seguimiento", onBack) {
        TituloSeccion("AniList")
        if (almacen.anilistToken != null) {
            FilaAjuste(Icons.Rounded.Person, "Conectado como ${almacen.anilistUsuario ?: "…"}", "AniList") {}
            LText(
                "En la ficha de un cómic o de un manga de tu biblioteca, toca «Seguimiento» para vincularlo con su serie. " +
                    "Los cómics suben el progreso al llegar a la última página; los mangas en línea, al terminar cada capítulo.",
                LectorType.Label.copy(fontSize = 13.sp), Modifier.padding(24.dp, 8.dp), color = LectorColors.Muted,
            )
            Box(Modifier.padding(24.dp, 8.dp)) { PillButton("Cerrar sesión", Icons.Rounded.Close, { almacen.cerrarSesionAnilist() }) }
        } else {
            LText(
                "1. En anilist.co → Settings → Developer, crea una aplicación con la URL de redirección  lector://anilist\n" +
                    "2. Pega aquí su Client ID (un número) y pulsa «Iniciar sesión».",
                LectorType.Label.copy(fontSize = 13.sp), Modifier.padding(24.dp, 8.dp), color = LectorColors.Muted,
            )
            OutlinedTextField(
                value = clientId, onValueChange = { clientId = it.filter { c -> c.isDigit() } },
                singleLine = true, label = { Text("Client ID") },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            )
            Box(Modifier.padding(24.dp, 12.dp)) {
                PillButton("Iniciar sesión", Icons.Rounded.Person, {
                    if (clientId.isNotEmpty()) {
                        almacen.guardarClientId(clientId)
                        runCatching { contexto.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(Anilist.urlDeLogin(clientId)))) }
                    }
                })
            }
        }
    }
}

// ───────────────────── Explorar ─────────────────────

@Composable
fun AjExplorarScreen(almacen: Almacen, ext: ExtManager, onBack: () -> Unit, onRuta: (Ruta) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var idiomas by remember { mutableStateOf(false) }
    var revisando by remember { mutableStateOf(false) }
    PaginaAjustes("Explorar", onBack) {
        FilaAjuste(Icons.Rounded.List, "Listados de extensiones", "${ext.repos.size} listados") { onRuta(Ruta.ListadosExt) }
        FilaAjuste(Icons.Rounded.Place, "Idiomas", ext.idiomas.take(8).joinToString(" ") { bandera(it) }) { idiomas = true }
        FilaSwitch("Mostrar contenido +18", "Fuentes y extensiones para adultos", ext.mostrarNsfw) { ext.alternarNsfw() }
        Divisor()
        TituloSeccion("Navegar una fuente")
        FilaChips(listOf("Cuadrícula", "Lista", "Compacta", "Solo portadas"), almacen.vistaExplorar.ordinal) { almacen.vistaExplorar = VistaExplorar.entries[it] }
        FilaSwitch("Esconder lo que ya está en la biblioteca", null, almacen.explorarEnBiblioteca == com.example.lector.Tri.EXCLUIR) {
            almacen.explorarEnBiblioteca = if (it) com.example.lector.Tri.EXCLUIR else com.example.lector.Tri.IGNORAR
        }
        FilaSwitch("Búsqueda global solo en fuentes ancladas", "Más rápida si tienes muchas fuentes", almacen.globalSoloAncladas) { almacen.globalSoloAncladas = it }
        Divisor()
        TituloSeccion("Extensiones")
        FilaSwitch("Avisar de actualizaciones de extensiones", "Revisa cada 12 horas", almacen.revisarExtensiones) {
            almacen.revisarExtensiones = it; Programador.programar(ctx)
        }
        FilaAjuste(Icons.Rounded.Refresh, if (revisando) "Revisando…" else "Buscar actualizaciones ahora", null) {
            if (!revisando) {
                revisando = true
                scope.launch {
                    withContext(Dispatchers.IO) { runCatching { ext.actualizarListaAhora() } }
                    val n = ext.conActualizacion().size
                    Avisos.mostrar(if (n == 0) "Todas las extensiones están al día" else "$n extensión(es) con actualización")
                    revisando = false
                }
            }
        }
    }
    if (idiomas) {
        val todos = (ext.disponibles.map { it.lang } + ext.instaladas.map { it.lang } + ext.idiomas + "all")
            .filter { it.isNotBlank() }.distinct().sortedBy { nombreIdioma(it) }
        AlertDialog(
            onDismissRequest = { idiomas = false },
            title = { Text("Idiomas") },
            text = {
                Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState())) {
                    todos.forEach { l ->
                        Row(Modifier.fillMaxWidth().pressable { ext.alternarIdioma(l) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = l in ext.idiomas, onCheckedChange = null)
                            Spacer(Modifier.width(12.dp))
                            Text("${bandera(l)}  ${nombreIdioma(l)}")
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { idiomas = false }) { Text("Listo") } },
            dismissButton = { TextButton(onClick = { ext.activarTodosIdiomas() }) { Text("Todos") } },
        )
    }
}

// ───────────────────── Datos y almacenamiento ─────────────────────

@Composable
fun DatosScreen(almacen: Almacen, alRecargar: () -> Unit, onBack: () -> Unit) {
    val contexto = LocalContext.current
    val scope = rememberCoroutineScope()
    var resultado by remember { mutableStateOf<String?>(null) }
    var version by remember { mutableIntStateOf(0) }

    // Crear y restaurar van en segundo plano con avance (píldora arriba + notificación) e informe al terminar.
    fun ocupado() = Avisos.mostrar("Ya hay una copia en marcha: espera a que termine")
    val crear = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null && !TareaCopia.lanzar(contexto, "Creando copia") {
                val (texto, informe) = CopiaSeguridad.crearConInforme(contexto, this)
                contexto.contentResolver.openOutputStream(uri)?.use { it.write(texto.toByteArray(Charsets.UTF_8)) } ?: error("No se pudo escribir el archivo")
                informe
            }
        ) ocupado()
    }
    val restaurar = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && !TareaCopia.lanzar(contexto, "Restaurando copia") {
                paso(0.02f, "Abriendo el archivo…")
                val bytes = contexto.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("No se pudo leer el archivo")
                if (com.example.lector.Tachibk.esTachibk(bytes)) {
                    // Copia de Mihon / Komikku / TachiyomiSY / J2K (.tachibk o .proto.gz).
                    com.example.lector.Tachibk.restaurar(contexto, bytes, this)
                } else {
                    val texto = com.example.lector.Tachibk.descomprimirSiHace(bytes).toString(Charsets.UTF_8)
                    runCatching { CopiaSeguridad.restaurarConInforme(contexto, texto, this) }
                        .getOrElse { throw IllegalStateException("El archivo no es una copia válida (${it.message ?: it.javaClass.simpleName})") }
                }
            }
        ) ocupado()
    }
    // Copia en el formato de Mihon / Komikku (.tachibk): para pasar la biblioteca a esas apps.
    val crearTachibk = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null && !TareaCopia.lanzar(contexto, "Creando copia .tachibk") {
                val informe = contexto.contentResolver.openOutputStream(uri)?.use { com.example.lector.Tachibk.crear(contexto, it, this) }
                    ?: error("No se pudo escribir el archivo")
                informe.copy(bien = informe.bien + "Restáurala en Mihon, Komikku o TachiyomiSY (Ajustes → Copia de seguridad → Restaurar)")
            }
        ) ocupado()
    }
    val elegirCarpeta = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching {
                contexto.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
            almacen.copiaCarpeta = uri.toString()
        }
    }

    val tamImagenes = remember(version) { Formatter.formatShortFileSize(contexto, Imagenes.tamanoDisco()) }
    PaginaAjustes("Datos y almacenamiento", onBack) {
        TituloSeccion("Copia de seguridad")
        LText(
            "Guarda tu biblioteca (también la de Explorar), categorías, progreso, historial, capítulos leídos y marcados, " +
                "listados de extensiones, búsquedas guardadas y ajustes. No incluye los archivos de los cómics ni las descargas.",
            LectorType.Label.copy(fontSize = 13.sp), Modifier.padding(horizontal = 24.dp), color = LectorColors.Muted,
        )
        Spacer(Modifier.height(8.dp))
        FilaAjuste(Icons.Rounded.Share, "Crear copia", "Guardar en un archivo .json") { crear.launch(CopiaSeguridad.nombreArchivo()) }
        FilaAjuste(Icons.Rounded.Refresh, "Restaurar copia", "De Lector (.json) o de Mihon, Komikku, TachiyomiSY… (.tachibk)") { restaurar.launch(arrayOf("*/*")) }
        FilaAjuste(Icons.Rounded.Share, "Exportar para Mihon / Komikku", "Archivo .tachibk con tus mangas de fuentes, capítulos leídos, categorías e historial") {
            crearTachibk.launch(com.example.lector.Tachibk.nombreArchivo())
        }

        Divisor()
        TituloSeccion("Copias automáticas")
        val dias = listOf(0, 1, 2, 3, 7)
        FilaChips(listOf("No", "Diaria", "Cada 2 días", "Cada 3 días", "Semanal"), dias.indexOf(almacen.copiaAutoDias).coerceAtLeast(0)) {
            almacen.copiaAutoDias = dias[it]; Programador.programar(contexto)
        }
        if (almacen.copiaAutoDias > 0) {
            FilaAjuste(
                Icons.Rounded.Home, "Carpeta de las copias",
                if (almacen.copiaCarpeta.isBlank()) "Interna: ${CopiaSeguridad.carpetaInterna(contexto).path}" else "Una carpeta que elegiste",
            ) { elegirCarpeta.launch(null) }
            LText("Copias que se guardan", LectorType.Body.copy(fontSize = 15.sp), Modifier.padding(start = 24.dp, top = 10.dp))
            val maximos = listOf(2, 5, 10, 20)
            FilaChips(maximos.map { "$it" }, maximos.indexOf(almacen.copiasMax).let { if (it < 0) 1 else it }) { almacen.copiasMax = maximos[it] }
        }
        FilaAjuste(
            Icons.Rounded.Check, "Hacer una copia ahora",
            if (almacen.ultimaCopia > 0) "Última automática: ${hace(almacen.ultimaCopia)}" else "En la carpeta de las copias automáticas",
        ) {
            if (!TareaCopia.lanzar(contexto, "Creando copia") {
                    paso(0.2f, "Reuniendo la biblioteca…")
                    val donde = CopiaSeguridad.guardarAutomatica(contexto)
                    TareaCopia.Informe("Copia de seguridad", listOf("Copia guardada en $donde"))
                }
            ) ocupado()
        }

        Divisor()
        TituloSeccion("Almacenamiento")
        FilaAjuste(Icons.Rounded.Delete, "Vaciar la caché de imágenes", "Portadas guardadas: $tamImagenes") {
            scope.launch {
                withContext(Dispatchers.IO) { Imagenes.vaciar() }
                CacheVisor.vaciar()
                version++
                Avisos.mostrar("Caché vaciada")
            }
        }
        FilaAjuste(Icons.Rounded.Delete, "Vaciar la caché de páginas del visor", "Las páginas leídas que siguen en memoria") {
            CacheVisor.vaciar(); Avisos.mostrar("Caché del visor vaciada")
        }
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

// ───────────────────── Seguridad y privacidad ─────────────────────

@Composable
fun AjSeguridadScreen(almacen: Almacen, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val seguro = remember { (ctx.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager)?.isDeviceSecure == true }
    PaginaAjustes("Seguridad y privacidad", onBack) {
        TituloSeccion("Bloqueo")
        FilaSwitch(
            "Pedir desbloqueo",
            if (seguro) "Con el PIN, patrón, contraseña o huella del teléfono" else "Primero configura un bloqueo de pantalla en Android",
            almacen.bloqueo && seguro,
        ) { if (seguro) almacen.bloqueo = it else Avisos.mostrar("Configura un bloqueo de pantalla en los ajustes de Android") }
        if (almacen.bloqueo) {
            LText("Pedirlo al volver a la app", LectorType.Body.copy(fontSize = 15.sp), Modifier.padding(start = 24.dp, top = 10.dp))
            val minutos = listOf(0, 1, 2, 5, 10, 30)
            FilaChips(listOf("Siempre", "1 min", "2 min", "5 min", "10 min", "30 min"), minutos.indexOf(almacen.bloqueoMinutos).coerceAtLeast(0)) {
                almacen.bloqueoMinutos = minutos[it]
            }
        }
        Divisor()
        TituloSeccion("Privacidad")
        FilaSwitch("Pantalla segura", "Oculta la app en la lista de recientes e impide capturas", almacen.pantallaSegura) { almacen.pantallaSegura = it }
        FilaSwitch("Modo incógnito", "No se anota nada en el historial mientras esté activo", almacen.incognito) { almacen.incognito = it }
    }
}

// ───────────────────── Avanzado ─────────────────────

@Composable
fun AjAvanzadoScreen(almacen: Almacen, onBack: () -> Unit) {
    val ctx = LocalContext.current
    PaginaAjustes("Avanzado", onBack) {
        TituloSeccion("Red")
        LText("DNS sobre HTTPS (para las fuentes)", LectorType.Body.copy(fontSize = 15.sp), Modifier.padding(start = 24.dp, top = 4.dp))
        FilaChips(listOf("Desactivado", "Cloudflare", "Google", "AdGuard", "Quad9"), almacen.doh.coerceIn(0, 4)) {
            almacen.doh = it
            Avisos.mostrar("Se aplicará al volver a abrir la app")
        }
        FilaAjuste(Icons.Rounded.Delete, "Borrar las cookies", "Cierra las sesiones iniciadas en los sitios de las fuentes") {
            CookieManager.getInstance().removeAllCookies { Avisos.mostrar("Cookies borradas") }
        }
        FilaAjuste(Icons.Rounded.Delete, "Vaciar la caché de red", null) {
            File(ctx.cacheDir, "network_cache").deleteRecursively()
            Avisos.mostrar("Caché de red vaciada")
        }
        Divisor()
        TituloSeccion("Sistema")
        FilaAjuste(Icons.Rounded.Notifications, "Notificaciones", "Permiso y canales de la app") {
            runCatching {
                ctx.startActivity(
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        }
        FilaAjuste(Icons.Rounded.Build, "Optimización de batería", "Desactívala para que las revisiones automáticas no se retrasen") {
            runCatching {
                ctx.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
        FilaAjuste(Icons.Rounded.Info, "Información de la app", null) {
            runCatching {
                ctx.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        }
    }
}

// ───────────────────── Acerca de ─────────────────────

@Composable
fun AcercaDeScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val version = remember {
        runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "?"
    }
    PaginaAjustes("Acerca de", onBack) {
        Box(Modifier.fillMaxWidth().padding(vertical = 20.dp), Alignment.Center) { LogoApp(Modifier.size(120.dp)) }
        Dato2("Versión", version)
        LText(
            "Lector de cómics y manga para Android, inspirado en Komikku (fork de Mihon/Tachiyomi). " +
                "Usa las extensiones de Mihon/Komikku (source-api y red copiados de Komikku, licencia Apache 2.0).",
            LectorType.Body, Modifier.padding(24.dp), color = LectorColors.Muted,
        )
        FilaAjuste(Icons.Rounded.Share, "Komikku en GitHub", "github.com/komikku-app/komikku") {
            runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/komikku-app/komikku")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
        LText(
            "Fuente tipográfica: Inter, © The Inter Project Authors, licencia SIL Open Font License 1.1.",
            LectorType.Label, Modifier.padding(24.dp), color = LectorColors.Muted,
        )
    }
}

@Composable
private fun Dato2(etiqueta: String, valor: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 6.dp)) {
        LText(etiqueta, LectorType.Body, Modifier.width(110.dp), color = LectorColors.Muted)
        LText(valor, LectorType.Body, Modifier.weight(1f))
    }
}
