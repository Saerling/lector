package com.example.lector.ui

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
import com.example.lector.Almacen
import com.example.lector.BibliotecaScreen
import com.example.lector.Comic
import com.example.lector.ext.CapituloFuente
import com.example.lector.ext.MangaFuente
import com.example.lector.ext.Motor
import com.example.lector.HistorialScreen
import com.example.lector.ext.ExtManager

/**
 * Pantalla principal con el diseño nuevo.
 *  - Pestañas: Inicio, Buscar, Biblioteca, Historial, Más.
 *  - Encima de las pestañas se abren (con animación) la página de un cómic y las páginas de Más/Ajustes.
 * Tu BibliotecaScreen e HistorialScreen siguen intactos.
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
    pestanaBiblioteca: Int,
    alCambiarPestanaBiblioteca: (Int) -> Unit,
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
    var desdeNovedades by remember { mutableStateOf(false) } // la ficha se abrió desde Explorar → Novedades
    var capsLectura by remember { mutableStateOf<List<CapituloFuente>>(emptyList()) }
    var capIndice by remember { mutableStateOf(0) }
    var paginaLectura by remember { mutableStateOf(0) }
    // Capítulo y página que hay que abrir al cargar la ficha (cuando se entra desde el historial).
    var reanudar by remember { mutableStateOf<Pair<String, Int>?>(null) }
    val hayCapa = ruta != null || detalleId != null

    // Al cerrar cualquier página que estaba encima, relee la biblioteca (progreso, "último leído", etc.).
    LaunchedEffect(hayCapa) { if (!hayCapa) alRecargar() }

    // `ruta` se restaura tras morir el proceso, pero el manga y los capítulos abiertos viven solo en memoria:
    // sin esto se veía una ficha o un lector vacíos ("No se puede abrir el capítulo").
    LaunchedEffect(ruta) {
        if ((ruta == Ruta.Ficha || ruta == Ruta.LeerOnline) && mangaSel == null) ruta = null
        else if (ruta == Ruta.LeerOnline && capsLectura.isEmpty()) ruta = Ruta.Ficha
    }

    BackHandler(enabled = activo && ruta != null && detalleId == null) {
        ruta = if (ruta == Ruta.Ficha && desdeNovedades) null else ruta?.padre()
    }
    BackHandler(enabled = activo && detalleId != null) { detalleId = null }

    // Abre la ficha de un manga de una fuente (desde la biblioteca o el historial).
    val abrirEnLinea: (Long, String, String, String?, Pair<String, Int>?) -> Unit = { fId, url, titulo, portada, cap ->
        fuenteId = fId
        mangaSel = MangaFuente(url, titulo, portada)
        desdeNovedades = true // al volver se regresa a la pestaña desde la que se abrió
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

    Box(Modifier.fillMaxSize().background(LectorColors.Bg)) {
        AnimatedContent(
            targetState = tab, label = "tabs",
            transitionSpec = {
                ContentTransform(
                    fadeIn(tween(220)) + scaleIn(tween(220), initialScale = 0.97f),
                    fadeOut(tween(110)),
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
                    onManga = { f, m -> fuenteId = f.id; mangaSel = m; desdeNovedades = true; reanudar = null; ruta = Ruta.Ficha },
                )
                LectorTab.Library -> Box(Modifier.padding(bottom = 76.dp)) {
                    BibliotecaScreen(
                        comics = comics,
                        almacen = almacen,
                        alAnadir = alAnadir,
                        alAnadirCarpeta = alAnadirCarpeta,
                        alAbrir = abrirDetalle,
                        alEliminar = alEliminar,
                        alRecargar = alRecargar,
                        pestana = pestanaBiblioteca,
                        alCambiarPestana = alCambiarPestanaBiblioteca,
                        conNavegacion = false,
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
                        cabecerasDe = { id ->
                            // Ya corre en un hilo de fondo: aquí sí se puede cargar la extensión.
                            ext.fuentes.firstOrNull { it.id == id }
                                ?.let { f -> Motor.actual?.let { m -> kotlinx.coroutines.runBlocking { m.cabecerasCargando(f) } } }
                                .orEmpty()
                        },
                    )
                }
                LectorTab.History -> Box(Modifier.statusBarsPadding().padding(bottom = 76.dp)) {
                    HistorialScreen(
                        comics = visibles, almacen = almacen,
                        alAbrir = leerComic, alCambiar = alRecargar,
                        alAbrirOnline = { e ->
                            abrirEnLinea(e.fuenteId, e.urlManga, e.titulo, e.portadaUrl, e.capUrl to e.pagina)
                        },
                        cabecerasDe = { id ->
                            // Ya corre en un hilo de fondo: aquí sí se puede cargar la extensión.
                            ext.fuentes.firstOrNull { it.id == id }
                                ?.let { f -> Motor.actual?.let { m -> kotlinx.coroutines.runBlocking { m.cabecerasCargando(f) } } }
                                .orEmpty()
                        },
                    )
                }
                LectorTab.More -> MasScreen(
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
                    Ruta.Estadisticas -> EstadisticasScreen(visibles) { ruta = null }
                    Ruta.Datos -> DatosScreen(almacen, alRecargar) { ruta = null }
                    Ruta.Ajustes -> AjustesScreen(almacen, { ruta = null }) { ruta = it }
                    Ruta.Apariencia -> AparienciaScreen(almacen) { ruta = Ruta.Ajustes }
                    Ruta.AjBiblioteca -> AjBibliotecaScreen(almacen, { ruta = Ruta.Ajustes }) { ruta = it }
                    Ruta.AjSeguimiento -> AjSeguimientoScreen(almacen) { ruta = Ruta.Ajustes }
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
                    enter = slideInHorizontally(tween(330, easing = EnfasisEasing)) { it / 8 } +
                        scaleIn(tween(330, easing = EnfasisEasing), initialScale = 0.96f) +
                        fadeIn(tween(260, easing = EnfasisEasing)),
                    exit = slideOutHorizontally(tween(240, easing = EnfasisEasing)) { it / 8 } +
                        scaleOut(tween(240, easing = EnfasisEasing), targetScale = 0.97f) + fadeOut(tween(160)),
                ) {
                    // Fondo opaco y toques propios: la pantalla de debajo no se ve ni recibe toques.
                    Box(Modifier.fillMaxSize().background(LectorColors.Bg).pointerInput(Unit) { detectTapGestures { } }) {
                        when (r) {
                        Ruta.FuenteDetalle -> FuenteDetalleScreen(
                            ext, fuenteId ?: 0L,
                            onManga = { mangaSel = it; desdeNovedades = false; reanudar = null; ruta = Ruta.Ficha },
                        ) { ruta = null }
                        Ruta.Ficha -> {
                            val m = mangaSel
                            if (m == null) Box(Modifier.fillMaxSize().background(LectorColors.Bg))
                            else FichaMangaScreen(
                                ext, fuenteId ?: 0L, m, almacen, alRecargar,
                                onBack = { ruta = if (desdeNovedades) null else Ruta.FuenteDetalle },
                                onLeer = { caps, i, pagina ->
                                    capsLectura = caps; capIndice = i; paginaLectura = pagina; ruta = Ruta.LeerOnline
                                },
                                reanudarCap = reanudar,
                                alConsumirReanudar = { reanudar = null },
                            )
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

        // Mini lector + barra flotante: se esconden hacia abajo cuando hay una página encima.
        AnimatedVisibility(
            visible = !hayCapa,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(tween(240)) { it } + fadeIn(tween(240)),
            exit = slideOutVertically(tween(200)) { it } + fadeOut(tween(160)),
        ) {
            Column(
                Modifier.navigationBarsPadding().padding(horizontal = 16.dp).padding(bottom = 8.dp)
            ) {
                // El botón de reanudar abre la ficha del último manga leído (no el capítulo).
                if (tab != LectorTab.Library) {
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
                            e.titulo, e.capNombre,
                            onClick = { abrirEnLinea(e.fuenteId, e.urlManga, e.titulo, e.portadaUrl, null) },
                        ) { m -> ImagenRemota(e.portadaUrl, m.clip(SuaveShape(12.dp)), cabeceras) }
                        Spacer(Modifier.height(8.dp))
                    } else if (ultimo != null) {
                        MiniReader(ultimo, almacen, onClick = { abrirDetalle(ultimo) })
                        Spacer(Modifier.height(8.dp))
                    }
                }
                FloatingNavBar(tab, onSelect = onTab)
            }
        }

        // Avisos (en lugar de Toast): píldora que cae desde arriba.
        AvisosHost(Modifier.align(Alignment.TopCenter))
    }
}
