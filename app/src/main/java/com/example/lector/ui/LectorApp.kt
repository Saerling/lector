package com.example.lector.ui

import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.expandVertically
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.graphicsLayer
import com.example.lector.Almacen
import com.example.lector.Comic
import com.example.lector.ext.CapituloFuente
import com.example.lector.ext.MangaFuente
import com.example.lector.ext.Motor
import com.example.lector.Notificaciones
import com.example.lector.ext.ExtManager

/**
 * Pantalla principal con el diseño nuevo.
 *  - Pestañas: Inicio, Buscar, Biblioteca, Historial, Más.
 *  - Encima de las pestañas se abren (con animación) la página de un cómic y las páginas de Más/Ajustes.
 * `destino` llega de una notificación (actualizaciones, descargas, extensiones) y se consume al abrirlo.
 */
@Composable
fun Inicio(
    tab: LectorTab,
    onTab: (LectorTab) -> Unit,
    comics: List<Comic>,
    almacen: Almacen,
    alAnadir: () -> Unit,
    alAnadirCarpeta: () -> Unit,
    alAbrir: (Comic) -> Unit,
    alEliminar: (Comic) -> Unit,
    alRecargar: () -> Unit,
    destino: String? = null,
    alConsumirDestino: () -> Unit = {},
    // false mientras se lee un cómic local encima: Inicio sigue vivo pero no debe atender el botón atrás.
    activo: Boolean = true,
) {
    // Los cómics de categorías ocultas no salen en Inicio, Buscar ni Historial.
    // Solo se ven en Biblioteca, manteniendo pulsado su título.
    val visibles = almacen.sinOcultos(comics)
    val ultimo = visibles.firstOrNull { it.id == almacen.ultimoAbierto }
    // Último manga en línea que se estaba leyendo (si no está en una categoría oculta).
    val ultimoLinea = remember(almacen.historialLinea, visibles) {
        almacen.historialLinea.firstOrNull()?.takeIf { e ->
            val c = almacen.comicEnLinea(e.fuenteId, e.urlManga)
            c == null || visibles.any { it.id == c.id }
        }
    }

    var ruta by rememberSaveable { mutableStateOf<Ruta?>(null) }
    var detalleId by rememberSaveable { mutableStateOf<String?>(null) }
    // Explorar / extensiones: gestor único + qué extensión o fuente se está viendo.
    val contexto = LocalContext.current
    val ext = remember { ExtManager.obtener(contexto) }
    var extPkg by rememberSaveable { mutableStateOf<String?>(null) }
    var fuenteId by rememberSaveable { mutableStateOf<Long?>(null) }
    // Manga abierto desde una fuente y capítulos que se están leyendo online.
    var mangaSel by remember { mutableStateOf<MangaFuente?>(null) }
    var generoBuscar by remember { mutableStateOf<String?>(null) } // género tocado en una ficha → se busca en su fuente
    var desdeNovedades by remember { mutableStateOf(false) } // la ficha se abrió desde Explorar → Novedades
    var capsLectura by remember { mutableStateOf<List<CapituloFuente>>(emptyList()) }
    var capIndice by remember { mutableStateOf(0) }
    var paginaLectura by remember { mutableStateOf(0) }
    // Capítulo y página que hay que abrir al cargar la ficha (cuando se entra desde el historial).
    var reanudar by remember { mutableStateOf<Pair<String, Int>?>(null) }
    // Pantalla a la que vuelve la ficha cuando no se abrió desde la lista de una fuente (p. ej. búsqueda global).
    var vueltaFicha by remember { mutableStateOf<Ruta?>(null) }
    // Mangas abiertos uno encima de otro desde una ficha (recomendaciones, «Fuente no disponible»): «atrás» vuelve
    // al anterior en vez de salir de la ficha. Se vacía al salir de la ficha.
    val fichasAnteriores = remember { androidx.compose.runtime.mutableStateListOf<Pair<Long, MangaFuente>>() }
    var consultaGlobal by rememberSaveable { mutableStateOf("") }
    var migrarId by rememberSaveable { mutableStateOf<String?>(null) }

    // Solo en la versión debug: «probar:<paquete>» confía en esa extensión y abre el catálogo de su primera fuente.
    // Lo usa la prueba automática en el emulador (.github/workflows/probar.yml).
    var paquetePrueba by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(paquetePrueba) {
        val pkg = paquetePrueba ?: return@LaunchedEffect
        ext.refrescarInstaladas()
        var fuente: com.example.lector.ext.FuenteUI? = null
        repeat(60) {
            if (fuente != null) return@repeat
            ext.instaladas.firstOrNull { it.pkg == pkg }?.let { e -> if (!e.confiable) ext.confiar(e) }
            fuente = ext.fuentes.filter { it.pkg == pkg }.let { l -> l.firstOrNull { it.lang == "es" } ?: l.firstOrNull { it.lang == "en" } ?: l.firstOrNull() }
            if (fuente == null) kotlinx.coroutines.delay(500)
        }
        // El id puede ser provisional hasta que la extensión termina de cargar: se espera a que no cambie.
        repeat(20) {
            kotlinx.coroutines.delay(1000)
            val delPaquete = ext.fuentes.filter { it.pkg == pkg }
            val ahora = delPaquete.firstOrNull { it.nombre == fuente?.nombre && it.lang == fuente?.lang }
                ?: delPaquete.firstOrNull { it.lang == "es" } ?: delPaquete.firstOrNull()
            if (ahora != null) fuente = ahora
        }
        android.util.Log.i("LectorPrueba", "fuente=${fuente?.nombre} id=${fuente?.id}")
        val fp = fuente
        // «|capitulo»: recorre populares → ficha → primer capítulo como lo haría el usuario, sin tocar la pantalla.
        if (fp != null && PruebaAuto.probarCapitulo) {
            PruebaAuto.probarCapitulo = false
            val motor = com.example.lector.ext.Motor.actual
            val r = runCatching {
                requireNotNull(motor)
                val m = motor.populares(fp, 1).mangas.first()
                val fi = motor.ficha(fp, m)
                val cap = fi.capitulos.firstOrNull() ?: error("«${m.titulo}» sin capítulos")
                // Como al abrirlo desde Actualizaciones o la cola de descargas: sin memo, recuperado de la ficha guardada.
                val recuperado = com.example.lector.CacheFichas.conMemo(fp.id, m.url, cap.copy(memo = null))
                val n = motor.cantidadPaginas(fp, recuperado)
                "«${m.titulo}»: ${fi.capitulos.size} caps, memo manga=${fi.memo != null}, memo cap=${cap.memo != null} " +
                    "(guardado=${recuperado.memo != null}); «${cap.nombre}»: $n páginas"
            }
            android.util.Log.i("LectorPrueba", "capitulo ${fp.nombre}: " + r.getOrElse { "ERROR ${it.message ?: it.javaClass.simpleName}" })
        }
        fuente?.let { fuenteId = it.id; desdeNovedades = false; ruta = Ruta.FuenteDetalle }
    }

    // Abrir desde una notificación.
    LaunchedEffect(destino) {
        when (destino) {
            Notificaciones.DESTINO_ACTUALIZACIONES -> { ruta = null; detalleId = null; onTab(LectorTab.Updates) }
            Notificaciones.DESTINO_DESCARGAS -> { detalleId = null; ruta = Ruta.Descargas }
            Notificaciones.DESTINO_EXTENSIONES -> { ruta = null; detalleId = null; onTab(LectorTab.Search) }
            com.example.lector.NuevaVersion.DESTINO -> com.example.lector.NuevaVersion.abrirDesdeNotificacion(contexto)
            com.example.lector.TareaCopia.DESTINO -> {
                com.example.lector.TareaCopia.informeVisto = true
                com.example.lector.TareaCopia.verInforme = com.example.lector.TareaCopia.informe != null
            }
            else -> {
                val depurable = (contexto.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
                if (destino?.startsWith("probar:") == true && depurable) {
                    paquetePrueba = destino.removePrefix("probar:").substringBefore('|')
                    if (destino.endsWith("|autorec")) PruebaAuto.abrirRecomendacion = true
                    if (destino.endsWith("|capitulo")) PruebaAuto.probarCapitulo = true
                }
                // Pruebas de las copias en el emulador: restaurar un archivo y crear una copia, midiendo el tiempo.
                if (destino?.startsWith("probar-restaurar:") == true && depurable) {
                    val ruta = destino.removePrefix("probar-restaurar:")
                    com.example.lector.TareaCopia.lanzar(contexto, "Restaurando copia") {
                        val t0 = android.os.SystemClock.elapsedRealtime()
                        val inf = com.example.lector.Tachibk.restaurar(contexto, java.io.File(ruta).readBytes(), this)
                        android.util.Log.i("LectorPrueba", "restaurar ms=${android.os.SystemClock.elapsedRealtime() - t0} bien=${inf.bien} avisos=${inf.avisos} errores=${inf.errores}")
                        inf
                    }
                }
                // «Fuente no disponible»: abre un manga de una fuente que no existe (busca su título en las demás).
                if (destino?.startsWith("probar-huerfano:") == true && depurable) {
                    fuenteId = 424242L
                    mangaSel = com.example.lector.ext.MangaFuente("/no-existe", destino.removePrefix("probar-huerfano:"), null)
                    desdeNovedades = true; vueltaFicha = null; reanudar = null
                    ruta = Ruta.Ficha
                }
                // Actualización de la app: lee la última versión de GitHub y descarga su APK (sin instalarlo).
                if (destino == "probar-version" && depurable) {
                    kotlin.concurrent.thread {
                        val r = runCatching {
                            val v = kotlinx.coroutines.runBlocking { com.example.lector.NuevaVersion.ultima(contexto) }
                            if (v == null) "sin versiones publicadas" else {
                                val f = java.io.File(contexto.cacheDir, "prueba-version.apk")
                                com.example.lector.ext.ExtRepos.descargarA(v.apkUrl, f) {}
                                "ultima=${v.nombre} codigo=${v.codigo} actual=${com.example.lector.NuevaVersion.codigoActual(contexto)} " +
                                    "descargado=${f.length()} esperado=${v.tamano} notas=${v.notas.lineSequence().firstOrNull()}"
                            }
                        }
                        android.util.Log.i("LectorPrueba", "version " + r.getOrElse { "error ${it.message ?: it.javaClass.simpleName}" })
                    }
                }
                // Direcciones de descarga de unas extensiones (de los listados), para instalarlas en la prueba.
                if (destino?.startsWith("probar-url:") == true && depurable) {
                    val pkgs = destino.removePrefix("probar-url:").split(',')
                    kotlin.concurrent.thread {
                        repeat(90) { if (ext.disponibles.isEmpty()) Thread.sleep(1000) }
                        pkgs.forEach { p ->
                            val d = ext.disponibles.firstOrNull { it.pkg.endsWith(".$p") || it.pkg == p }
                            android.util.Log.i("LectorPrueba", "url $p ${d?.pkg ?: "-"} ${d?.apkUrl ?: "-"}")
                        }
                    }
                }
                // Prueba de un listado de extensiones: lo mismo que «Añadir listado», sin guardarlo.
                if (destino?.startsWith("probar-repo:") == true && depurable) {
                    val entrada = destino.removePrefix("probar-repo:")
                    kotlin.concurrent.thread {
                        val c = com.example.lector.ext.ExtRepos.candidatos(entrada)
                        for (u in c) {
                            val r = runCatching { com.example.lector.ext.ExtRepos.extensiones(com.example.lector.ext.ExtRepos.leerTienda(u)) }
                            android.util.Log.i(
                                "LectorPrueba",
                                "repo $u -> " + r.fold({ l -> "ok ${l.size} extensiones, p. ej. ${l.firstOrNull()?.apkUrl}" }, { e -> "error ${e.message ?: e.javaClass.simpleName}" }),
                            )
                            if (r.isSuccess) break
                        }
                    }
                }
                if (destino == "probar-copia" && depurable) {
                    com.example.lector.TareaCopia.lanzar(contexto, "Creando copia .tachibk") {
                        val t0 = android.os.SystemClock.elapsedRealtime()
                        val archivo = java.io.File(contexto.filesDir, "exportada.tachibk")
                        val inf = archivo.outputStream().use { com.example.lector.Tachibk.crear(contexto, it, this) }
                        android.util.Log.i("LectorPrueba", "crear ms=${android.os.SystemClock.elapsedRealtime() - t0} bytes=${archivo.length()} bien=${inf.bien} avisos=${inf.avisos}")
                        inf
                    }
                }
            }
        }
        if (destino != null) alConsumirDestino()
    }
    val hayCapa = ruta != null || detalleId != null

    // Al cerrar cualquier página que estaba encima, relee la biblioteca (progreso, "último leído", etc.).
    LaunchedEffect(hayCapa) { if (!hayCapa) alRecargar() }

    // `ruta` se restaura tras morir el proceso, pero el manga y los capítulos abiertos viven solo en memoria:
    // sin esto se veía una ficha o un lector vacíos ("No se puede abrir el capítulo").
    LaunchedEffect(ruta) {
        if ((ruta == Ruta.Ficha || ruta == Ruta.LeerOnline) && mangaSel == null) ruta = null
        else if (ruta == Ruta.LeerOnline && capsLectura.isEmpty()) ruta = Ruta.Ficha
    }

    // Atrás desde una ficha: primero al manga anterior (si se abrió esta desde otra ficha); si no, a donde se venía.
    val volverDeFicha: () -> Unit = {
        val previa = fichasAnteriores.removeLastOrNull()
        if (previa != null) {
            fuenteId = previa.first; mangaSel = previa.second; reanudar = null
        } else ruta = if (desdeNovedades) vueltaFicha else Ruta.FuenteDetalle
    }
    LaunchedEffect(ruta) { if (ruta != Ruta.Ficha && ruta != Ruta.LeerOnline) fichasAnteriores.clear() }

    BackHandler(enabled = activo && ruta != null && detalleId == null) {
        if (ruta == Ruta.Ficha) volverDeFicha()
        else ruta = ruta?.padre()
    }
    BackHandler(enabled = activo && detalleId != null) { detalleId = null }

    // Abre la ficha de un manga de una fuente (desde la biblioteca o el historial).
    val abrirEnLinea: (Long, String, String, String?, Pair<String, Int>?) -> Unit = { fId, url, titulo, portada, cap ->
        fuenteId = fId
        mangaSel = MangaFuente(url, titulo, portada)
        desdeNovedades = true // al volver se regresa a la pestaña desde la que se abrió
        vueltaFicha = null
        reanudar = cap
        ruta = Ruta.Ficha
    }
    // Un manga en línea no tiene .cbz: se abre su ficha. Los cómics locales siguen como siempre.
    val abrirDetalle: (Comic) -> Unit = {
        if (it.enLinea) abrirEnLinea(it.fuenteId, it.urlManga.orEmpty(), it.titulo, it.portadaUrl, null)
        else detalleId = it.id
    }
    val leerComic: (Comic) -> Unit = { if (it.enLinea) abrirDetalle(it) else alAbrir(it) }
    // Fuente → ficha → lector online forman una pila: las de abajo se quedan en composición (tapadas) mientras
    // hay otra encima, así al volver no se recarga la lista de la extensión ni la ficha ni se pierde el scroll.
    val pila: List<Ruta> = when (ruta) {
        Ruta.LeerOnline -> if (desdeNovedades) listOf(Ruta.Ficha, Ruta.LeerOnline)
            else listOf(Ruta.FuenteDetalle, Ruta.Ficha, Ruta.LeerOnline)
        Ruta.Ficha -> if (desdeNovedades) listOf(Ruta.Ficha) else listOf(Ruta.FuenteDetalle, Ruta.Ficha)
        Ruta.FuenteDetalle -> listOf(Ruta.FuenteDetalle)
        else -> emptyList()
    }
    // Clave de la capa que está encima ("" = ninguna). Se usa como estado de la animación.
    // Las pantallas de la pila se dibujan aparte (más abajo), por eso aquí cuentan como "ninguna".
    val capa = when {
        detalleId != null -> "d:$detalleId"
        ruta != null && ruta !in pila -> ruta!!.name
        else -> ""
    }

    // Como en musicfy: al abrir una ficha (o un cómic) la página de debajo retrocede (se encoge y se apaga) con
    // el muelle con el que se abre; al volver, avanza con una curva suave de 560 ms.
    val abiertaEncima = detalleId != null || pila.isNotEmpty()
    val retroceso = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(abiertaEncima) {
        if (abiertaEncima) retroceso.animateTo(1f, spring(dampingRatio = 0.88f, stiffness = 150f, visibilityThreshold = 0.001f))
        else retroceso.animateTo(0f, tween(560, easing = CierreEasing))
    }

    Box(Modifier.fillMaxSize().background(LectorColors.Bg)) {
      // Todo lo que queda bajo la barra flotante: se graba para el cristal esmerilado de la barra y el mini lector.
      Box(Modifier.fillMaxSize().raizCristal(CristalApp)) {
        // Fondo vivo (Apariencia → Fondo): detrás de las pestañas, y el cristal de la barra lo desenfoca.
        FondoVivo(Aspecto.fondo, Modifier.fillMaxSize(), acento = androidx.compose.material3.MaterialTheme.colorScheme.primary)
        AnimatedContent(
            targetState = tab, label = "tabs",
            modifier = Modifier.graphicsLayer {
                val r = retroceso.value
                if (r > 0f) {
                    val e = 1f - 0.08f * r
                    scaleX = e; scaleY = e
                    alpha = 1f - 0.55f * r
                }
            },
            // Las pestañas se deslizan un octavo de pantalla hacia el lado de la pestaña nueva, con un fundido.
            transitionSpec = {
                val derecha = targetState.ordinal > initialState.ordinal
                ContentTransform(
                    slideInHorizontally(tween(260, easing = EnfasisEasing)) { if (derecha) it / 8 else -it / 8 } + fadeIn(tween(200)),
                    slideOutHorizontally(tween(260, easing = EnfasisEasing)) { if (derecha) -it / 8 else it / 8 } + fadeOut(tween(200)),
                )
            }
        ) { t ->
            when (t) {
                LectorTab.Home -> HomeScreen(visibles, almacen, onOpen = abrirDetalle, onRead = leerComic, onAdd = alAnadir)
                LectorTab.Search -> ExplorarScreen(
                    ext = ext,
                    onRuta = { ruta = it },
                    onFuente = { fuenteId = it.id; desdeNovedades = false; ruta = Ruta.FuenteDetalle },
                    onExt = { extPkg = it.pkg; ruta = Ruta.ExtDetalle },
                    onManga = { f, m -> fuenteId = f.id; mangaSel = m; desdeNovedades = true; vueltaFicha = null; reanudar = null; ruta = Ruta.Ficha },
                    almacen = almacen,
                    onMigrar = { migrarId = it.id; ruta = Ruta.MigrarSerie },
                    onBuscarGlobal = { consultaGlobal = ""; ruta = Ruta.BusquedaGlobal },
                )
                LectorTab.Library -> Box(Modifier.padding(bottom = 76.dp)) {
                    BibliotecaScreen(
                        comics = comics,
                        almacen = almacen,
                        ext = ext,
                        alAbrir = abrirDetalle,
                        alLeer = leerComic,
                        alAnadir = alAnadir,
                        alAnadirCarpeta = alAnadirCarpeta,
                        alEditarCategorias = { ruta = Ruta.Categorias },
                        alMigrar = { migrarId = it.id; ruta = Ruta.MigrarSerie },
                        alEliminar = alEliminar,
                    )
                }
                LectorTab.Updates -> Box(Modifier.statusBarsPadding().padding(bottom = 76.dp)) {
                    ActualizacionesScreen(
                        almacen = almacen, ext = ext,
                        // Abre el capítulo nuevo directamente (la ficha carga y salta al lector).
                        alAbrir = { a ->
                            abrirEnLinea(
                                a.fuenteId, a.urlManga, a.titulo, a.portadaUrl,
                                a.capUrl to ext.paginaDe(a.fuenteId, a.capUrl),
                            )
                        },
                    )
                }
                LectorTab.History -> Box(Modifier.statusBarsPadding().padding(bottom = 76.dp)) {
                    HistorialScreen(
                        comics = visibles, almacen = almacen,
                        alAbrir = leerComic,
                        alAbrirOnline = { e ->
                            abrirEnLinea(e.fuenteId, e.urlManga, e.titulo, e.portadaUrl, e.capUrl to e.pagina)
                        },
                    )
                }
                LectorTab.More -> MasScreen(
                    almacen = almacen,
                    onRuta = { ruta = it },
                    onAdd = alAnadir,
                    onAddFolder = alAnadirCarpeta,
                )
            }
        }

        // Páginas que se deslizan encima (detalle de cómic, Más → Categorías, Ajustes, etc.).
        AnimatedContent(
            targetState = capa, label = "capa",
            transitionSpec = {
                ContentTransform(
                    slideInHorizontally(tween(330, easing = EnfasisEasing)) { it / 8 } +
                        scaleIn(tween(330, easing = EnfasisEasing), initialScale = 0.96f) +
                        fadeIn(tween(260, easing = EnfasisEasing)),
                    slideOutHorizontally(tween(240, easing = EnfasisEasing)) { -it / 8 } +
                        scaleOut(tween(240, easing = EnfasisEasing), targetScale = 0.97f) + fadeOut(tween(160)),
                )
            }
        ) { clave ->
            when {
                clave.isEmpty() -> Box(Modifier.fillMaxSize())
                clave.startsWith("d:") -> {
                    val c = comics.firstOrNull { it.id == clave.drop(2) }
                    if (c != null) {
                        DetalleScreen(
                            comic = c, almacen = almacen,
                            onBack = { detalleId = null },
                            onRead = { alAbrir(c) },
                            onChanged = alRecargar,
                        )
                    } else {
                        Box(Modifier.fillMaxSize().background(LectorColors.Bg))
                    }
                }
                else -> when (Ruta.valueOf(clave)) {
                    Ruta.Categorias -> EditarCategoriasScreen(almacen) { ruta = null }
                    Ruta.Estadisticas -> EstadisticasScreen(visibles, almacen, ext) { ruta = null }
                    Ruta.Datos -> DatosScreen(almacen, alRecargar) { ruta = null }
                    Ruta.Ajustes -> AjustesScreen(almacen, { ruta = null }) { ruta = it }
                    Ruta.Apariencia -> AparienciaScreen(almacen) { ruta = Ruta.Ajustes }
                    Ruta.AjBiblioteca -> AjBibliotecaScreen(almacen, { ruta = Ruta.Ajustes }) { ruta = it }
                    Ruta.AjSeguimiento -> AjSeguimientoScreen(almacen) { ruta = Ruta.Ajustes }
                    Ruta.AjVisor -> AjustesVisorScreen(almacen) { ruta = Ruta.Ajustes }
                    Ruta.AjDescargas -> AjDescargasScreen(almacen, { ruta = Ruta.Ajustes }) { ruta = it }
                    Ruta.AjExplorar -> AjExplorarScreen(almacen, ext, { ruta = Ruta.Ajustes }) { ruta = it }
                    Ruta.AjSeguridad -> AjSeguridadScreen(almacen) { ruta = Ruta.Ajustes }
                    Ruta.AjAvanzado -> AjAvanzadoScreen(almacen) { ruta = Ruta.Ajustes }
                    Ruta.AcercaDe -> AcercaDeScreen { ruta = Ruta.Ajustes }
                    Ruta.Descargas -> ColaDescargasScreen(almacen) { ruta = null }
                    Ruta.BusquedaGlobal -> BusquedaGlobalScreen(
                        ext, almacen, consultaGlobal,
                        onManga = { f, m ->
                            fuenteId = f.id; mangaSel = m; desdeNovedades = true; reanudar = null
                            vueltaFicha = Ruta.BusquedaGlobal; ruta = Ruta.Ficha
                        },
                    ) { ruta = null }
                    Ruta.MigrarSerie -> {
                        val c = comics.firstOrNull { it.id == migrarId }
                        val origen = c?.let { x -> ext.fuentes.firstOrNull { it.id == x.fuenteId } }
                        if (c == null || origen == null) {
                            // Sin su fuente: se abre su ficha, que enseña «Fuente no disponible» con la búsqueda en las demás.
                            LaunchedEffect(Unit) {
                                if (c != null && c.enLinea) abrirEnLinea(c.fuenteId, c.urlManga.orEmpty(), c.titulo, c.portadaUrl, null)
                                else ruta = null
                            }
                            Box(Modifier.fillMaxSize().background(LectorColors.Bg))
                        } else MigrarScreen(
                            ext, almacen, c, origen, null,
                            alHecho = { d -> ruta = null; alRecargar(); Avisos.mostrar("Migrado a ${d.nombre}") },
                            onBack = { ruta = null },
                        )
                    }
                    Ruta.ListadosExt -> ListadosExtScreen(ext) { ruta = null }
                    Ruta.ExtDetalle -> ExtDetalleScreen(ext, extPkg.orEmpty()) { ruta = null }
                    Ruta.FuenteDetalle, Ruta.Ficha, Ruta.LeerOnline -> Box(Modifier.fillMaxSize())
                }
            }
        }


        // Pila fuente → ficha → lector online: cada una sigue viva mientras haya otra encima.
        listOf(Ruta.FuenteDetalle, Ruta.Ficha, Ruta.LeerOnline).forEach { r ->
            key(r) {
                AnimatedVisibility(
                    visible = r in pila && detalleId == null,
                    // La página «crece» al abrirse (muelle suave, interrumpible) y se encoge al cerrarse.
                    enter = scaleIn(spring(dampingRatio = 0.88f, stiffness = 150f), initialScale = 0.86f) +
                        fadeIn(tween(240)),
                    exit = scaleOut(tween(420, easing = CierreEasing), targetScale = 0.88f) + fadeOut(tween(300, easing = CierreEasing)),
                ) {
                    // Fondo opaco y toques propios: la pantalla de debajo no se ve ni recibe toques.
                    Box(Modifier.fillMaxSize().background(LectorColors.Bg).pointerInput(Unit) { detectTapGestures { } }) {
                        when (r) {
                        Ruta.FuenteDetalle -> FuenteDetalleScreen(
                            ext, fuenteId ?: 0L,
                            onManga = { mangaSel = it; desdeNovedades = false; reanudar = null; ruta = Ruta.Ficha },
                            generoBuscar = generoBuscar, alConsumirGenero = { generoBuscar = null },
                        ) { ruta = null }
                        Ruta.Ficha -> {
                            val m = mangaSel
                            if (m == null) Box(Modifier.fillMaxSize().background(LectorColors.Bg))
                            // Una ficha por manga: al pasar a otro (o volver al anterior) se crea de nuevo, con su estado.
                            else androidx.compose.animation.AnimatedContent(
                                targetState = (fuenteId ?: 0L) to m,
                                transitionSpec = {
                                    (fadeIn(tween(260)) + scaleIn(tween(320, easing = EnfasisEasing), initialScale = 0.94f)) togetherWith
                                        fadeOut(tween(180))
                                },
                                contentKey = { "${it.first}|${it.second.url}" },
                                label = "ficha",
                            ) { (fid, mm) -> FichaMangaScreen(
                                ext, fid, mm, almacen, alRecargar,
                                onBack = volverDeFicha,
                                onLeer = { caps, i, pagina ->
                                    capsLectura = caps; capIndice = i; paginaLectura = pagina; ruta = Ruta.LeerOnline
                                },
                                reanudarCap = reanudar,
                                alConsumirReanudar = { reanudar = null },
                                // Vuelve a la lista de la fuente (si la ficha venía de la biblioteca, se abre) y busca el género.
                                alGenero = { g -> generoBuscar = g; desdeNovedades = false; ruta = Ruta.FuenteDetalle },
                                alBuscarGlobal = { q -> consultaGlobal = q; ruta = Ruta.BusquedaGlobal },
                                alAbrirOtro = { f2, m2 ->
                                    fichasAnteriores.add(fid to mm)
                                    fuenteId = f2.id; mangaSel = m2; reanudar = null
                                },
                            ) }
                        }
                        Ruta.LeerOnline -> LeerOnlineScreen(
                            ext, fuenteId ?: 0L, mangaSel?.titulo.orEmpty(), capsLectura, capIndice,
                            almacen = almacen, manga = mangaSel, paginaInicial = paginaLectura,
                        ) { ruta = Ruta.Ficha }
                        else -> Unit
                        }
                    }
                }
            }
        }

      }

        // Mini lector + barra flotante (de cristal): se esconden hacia abajo cuando hay una página encima.
        AnimatedVisibility(
            visible = !hayCapa,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(tween(240)) { it } + fadeIn(tween(240)),
            exit = slideOutVertically(tween(200)) { it } + fadeOut(tween(160)),
        ) {
            Column(
                Modifier.navigationBarsPadding().padding(horizontal = 24.dp).padding(bottom = 8.dp)
            ) {
                // El botón de reanudar abre la ficha del último manga leído (no el capítulo).
                // Solo donde tiene sentido seguir leyendo (Inicio e Historial): en Biblioteca, Explorar, Actualizaciones y
                // Más tapaba contenido y la barra de selección. Entra y sale plegándose, sin saltos.
                AnimatedVisibility(
                    visible = tab == LectorTab.Home || tab == LectorTab.History,
                    // Living Flow: brota de la barra (crece desde abajo) y vuelve a fundirse en ella al salir.
                    enter = expandVertically(tween(300, easing = EnfasisEasing), expandFrom = Alignment.Bottom) + fadeIn(tween(220, 60)) +
                        scaleIn(tween(320, easing = EnfasisEasing), initialScale = 0.88f, transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)),
                    exit = shrinkVertically(tween(220, easing = EnfasisEasing), shrinkTowards = Alignment.Bottom) + fadeOut(tween(140)) +
                        scaleOut(tween(200), targetScale = 0.92f, transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)),
                ) { Column {
                    if (ultimoLinea != null && (ultimo == null || ultimoLinea.leido >= ultimo.leido)) {
                        val e = ultimoLinea
                        // Cargar la extensión tarda: se pide en segundo plano y, mientras, se usa lo que ya haya en caché.
                        val fuenteMini = ext.fuentes.firstOrNull { it.id == e.fuenteId }
                        val cabeceras by produceState(
                            initialValue = fuenteMini?.let { Motor.actual?.cabeceras(it) }.orEmpty(), fuenteMini?.id,
                        ) {
                            value = fuenteMini?.let { Motor.actual?.cabecerasCargando(it) }.orEmpty()
                        }
                        MiniReaderGenerico(
                            e.titulo, e.capNombre.trim().trimEnd(':').trim().ifBlank { "Seguir leyendo" },
                            onClick = { abrirEnLinea(e.fuenteId, e.urlManga, e.titulo, e.portadaUrl, null) },
                            progreso = if (e.total > 0) ((e.pagina + 1).toFloat() / e.total).coerceIn(0f, 1f) else null,
                        ) { m -> ImagenRemota(e.portadaUrl, m.clip(CircleShape), cabeceras, fuenteId = e.fuenteId) }
                        Spacer(Modifier.height(8.dp))
                    } else if (ultimo != null) {
                        MiniReader(ultimo, almacen, onClick = { abrirDetalle(ultimo) })
                        Spacer(Modifier.height(8.dp))
                    }
                } }
                FloatingNavBar(tab, onSelect = onTab)
            }
        }

        // Avance de una copia de seguridad (píldora pequeña arriba) y su informe al terminar.
        // «Ya lo tienes»: al añadir un manga que ya está en la biblioteca desde otra fuente.
        DialogoDuplicado(ext, almacen, alRecargar) { c ->
            if (ruta == Ruta.Ficha && mangaSel != null) {
                // Desde una ficha: se abre encima, y «atrás» vuelve a la que estabas mirando.
                fichasAnteriores.add((fuenteId ?: 0L) to mangaSel!!)
                fuenteId = c.fuenteId; mangaSel = MangaFuente(c.urlManga.orEmpty(), c.titulo, c.portadaUrl); reanudar = null
            } else abrirDetalle(c)
        }
        IslaCopia(Modifier.align(Alignment.TopCenter))
        InformeCopia()

        // Avisos (en lugar de Toast): píldora que cae desde arriba. Si hay una copia en marcha, debajo de su píldora.
        val bajarAvisos by androidx.compose.animation.core.animateDpAsState(
            if (com.example.lector.TareaCopia.estado != null) 46.dp else 0.dp, label = "bajarAvisos",
        )
        AvisosHost(Modifier.align(Alignment.TopCenter).padding(top = bajarAvisos))
    }
}

/** Curva de cierre de las páginas (empieza suave: un primer fotograma lento no se nota). */
val CierreEasing = CubicBezierEasing(0.4f, 0f, 0.1f, 1f)
