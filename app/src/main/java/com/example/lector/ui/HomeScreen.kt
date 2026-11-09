package com.example.lector.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.example.lector.Almacen
import com.example.lector.Comic
import com.example.lector.ui.mochy.BlurEffectCache
import com.example.lector.ui.mochy.InterFontFamily
import com.example.lector.ui.mochy.RevealSeenState
import com.example.lector.ui.mochy.RoundedHighlight
import com.example.lector.ui.mochy.home.HeroCarousel
import com.example.lector.ui.mochy.home.HeroCarouselItem
import com.example.lector.ui.mochy.home.HeroHeightFraction
import com.example.lector.ui.mochy.home.HomeContentInset
import com.example.lector.ui.mochy.home.HomeSectionTitle
import com.example.lector.ui.mochy.home.HomeTitleStyle
import com.example.lector.ui.mochy.home.HomeTopGlow
import com.example.lector.ui.mochy.home.HomeTrackRow
import com.example.lector.ui.mochy.rememberRevealSeenState
import com.example.lector.ui.mochy.revealOnAppear
import com.example.lector.ui.home.CarruselInicio
import com.example.lector.ui.home.DialogoCategoriasInicio
import com.example.lector.ui.home.DialogoEditarInicio
import com.example.lector.ui.home.DialogoEliminarInicio
import com.example.lector.ui.home.HojaComicInicio
import com.example.lector.ui.home.MarcaSeleccion
import com.example.lector.ui.home.OpcionInicio
import com.example.lector.ui.home.rememberAccionesInicio
import com.example.lector.ui.home.rememberOcultosInicio
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.DropdownMenuItem

/*
 * Inicio con el diseño de Mochy Music (github.com/realidkroo/Mochy-Music, GPL-3.0: ui/screens/HomeScreen.kt):
 * el héroe a sangre que pasa solo (ui/mochy/home/HeroCarousel.kt), secciones con título «ver todo» y filas de
 * portadas que aparecen en cascada, y la barra de arriba que cambia el logo por «Inicio» al desplazar.
 * Recomendaciones / escuchado hace poco / en bucle de Mochy -> seguir leyendo / recientes / en curso / sin leer.
 */

/** a Home row that fades and slides up the first time it scrolls into view */
private fun LazyListScope.homeItem(
    key: String,
    revealSeen: RevealSeenState,
    contentType: String,
    content: @Composable () -> Unit,
) {
    item(key = key, contentType = contentType) {
        Box(modifier = Modifier.revealOnAppear(key, revealSeen, cascade = true)) {
            content()
        }
    }
}

private val HomeHeaderHighlight = RoundedHighlight(cornerRadius = 100.dp)

@Composable
fun HomeScreen(
    comics: List<Comic>,
    almacen: Almacen,
    onOpen: (Comic) -> Unit,
    onRead: (Comic) -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
    ext: com.example.lector.ext.ExtManager? = null,
    onEliminar: ((Comic) -> Unit)? = null,
) {
    if (comics.isEmpty()) {
        InicioVacio(onAdd, modifier)
        return
    }
    // «Quitar de Inicio» (persistente; vuelven al leerlos otra vez) y las acciones del menú / selección
    val ocultos = rememberOcultosInicio()
    val acciones = rememberAccionesInicio(almacen, ext, ocultos)
    val todos = comics
    val comics = remember(todos, ocultos.mapa) { ocultos.filtrar(todos).ifEmpty { todos } }
    val cuantosOcultos = todos.size - comics.size

    val featured = remember(comics) {
        comics.firstOrNull { it.id == almacen.ultimoAbierto } ?: comics.maxByOrNull { it.leido } ?: comics.first()
    }
    val recientesTodos = remember(comics) { comics.filter { it.leido > 0 }.sortedByDescending { it.leido } }
    val recientes = remember(recientesTodos) { recientesTodos.take(12) }
    val enCursoTodos = remember(comics) { comics.filter { it.enCurso() }.sortedByDescending { it.leido } }
    val enCurso = remember(enCursoTodos) { enCursoTodos.take(3) }
    val sinLeer = remember(comics) { comics.filter { it.leido == 0L } }
    // Carrusel héroe: el destacado primero y luego lo que estás leyendo (o, si no hay, lo que tienes por empezar).
    val heroe = remember(comics, featured) {
        val base = (listOf(featured) + enCursoTodos + recientes).distinctBy { it.id }
        (if (base.size >= 2) base else (base + sinLeer).distinctBy { it.id }).take(6)
            .map { c ->
                HeroCarouselItem(
                    c,
                    when {
                        c.id == featured.id && c.leido > 0 -> "Seguir leyendo"
                        c.enCurso() -> "En curso"
                        c.leido > 0 -> "Leído hace poco"
                        else -> "Para empezar"
                    },
                )
            }
    }

    // «Ver todo»: la sección se despliega en su sitio, en filas (Mochy abre section_detail; Shiori no tiene esa página)
    var verRecientes by rememberSaveable { mutableStateOf(false) }
    var verEnCurso by rememberSaveable { mutableStateOf(false) }
    var verSinLeer by rememberSaveable { mutableStateOf(false) }

    val lazylistState = rememberLazyListState()
    val revealSeen = rememberRevealSeenState()
    val heroHeightDp = (LocalConfiguration.current.screenHeightDp * HeroHeightFraction).dp
    val collapseDistancePx = with(LocalDensity.current) { heroHeightDp.toPx() }

    val firstItemScrollOffset by remember(collapseDistancePx) {
        derivedStateOf {
            if (lazylistState.firstVisibleItemIndex == 0) lazylistState.firstVisibleItemScrollOffset.toFloat()
            else collapseDistancePx
        }
    }
    val heroScrollProgress by remember(collapseDistancePx) {
        derivedStateOf { (firstItemScrollOffset / collapseDistancePx).coerceIn(0f, 1f) }
    }
    val heroScrollProgressProvider = remember { { heroScrollProgress } }
    val glowIds = remember(recientes, enCurso) { (enCurso + recientes).distinctBy { it.id }.take(2).map { it.id } }

    // ---- Selección múltiple (Komikku) y menú contextual (Mochy) ----
    var seleccion by remember { mutableStateOf(emptySet<String>()) }
    var hoja by remember { mutableStateOf<Comic?>(null) }
    var categoriasDe by remember { mutableStateOf<List<String>?>(null) }
    var editar by remember { mutableStateOf<Comic?>(null) }
    var eliminar by remember { mutableStateOf<List<Comic>?>(null) }
    var menuDescarga by remember { mutableStateOf(false) }
    val enSeleccion = seleccion.isNotEmpty()
    LaunchedEffect(comics) { seleccion = seleccion.filterTo(HashSet()) { id -> comics.any { it.id == id } } }
    BackHandler(enabled = enSeleccion) { seleccion = emptySet() }
    val seleccionados = remember(seleccion, comics) { comics.filter { it.id in seleccion } }
    fun alternar(c: Comic) { seleccion = if (c.id in seleccion) seleccion - c.id else seleccion + c.id }
    val toque: (Comic) -> Unit = { c -> if (enSeleccion) alternar(c) else onOpen(c) }
    val pulsacionLarga: (Comic) -> Unit = { c -> if (enSeleccion) alternar(c) else hoja = c }
    val leerOSel: (Comic) -> Unit = { c -> if (enSeleccion) alternar(c) else onRead(c) }

    Box(modifier.fillMaxSize().background(LectorColors.Bg)) {
        LazyColumn(state = lazylistState, contentPadding = PaddingValues(bottom = 190.dp)) {
            item(key = "hero", contentType = "hero") {
                HeroCarousel(
                    items = heroe,
                    almacen = almacen,
                    onOpen = toque,
                    onRead = leerOSel,
                    heroScrollProgressProvider = heroScrollProgressProvider,
                    onLongPress = pulsacionLarga,
                )
            }

            if (recientes.isNotEmpty()) {
                homeItem("recientes_title", revealSeen, "title") {
                    HomeSectionTitle(
                        title = "Leídos recientemente",
                        label = "${recientesTodos.size}",
                        expanded = verRecientes,
                        onClick = { verRecientes = !verRecientes },
                    )
                }
                if (verRecientes) seccionEnFilas("rec", recientesTodos, almacen, toque, leerOSel, seleccion, pulsacionLarga)
                else homeItem("recientes_list", revealSeen, "cardRow") {
                    CarruselInicio("rec", recientes, almacen, revealSeen, seleccion, toque, pulsacionLarga)
                }
                espacioSeccion("recientes")
            }

            if (enCurso.isNotEmpty()) {
                homeItem("enCurso_title", revealSeen, "title") {
                    HomeSectionTitle(
                        title = "En curso",
                        label = "${enCursoTodos.size}",
                        expanded = verEnCurso,
                        onClick = if (enCursoTodos.size > enCurso.size) ({ verEnCurso = !verEnCurso }) else null,
                    )
                }
                seccionEnFilas("ec", if (verEnCurso) enCursoTodos else enCurso, almacen, toque, leerOSel, seleccion, pulsacionLarga, progreso = true)
                espacioSeccion("enCurso")
            }

            if (sinLeer.isNotEmpty()) {
                homeItem("sinLeer_title", revealSeen, "title") {
                    HomeSectionTitle(
                        title = "Sin leer",
                        label = "${sinLeer.size}",
                        expanded = verSinLeer,
                        onClick = { verSinLeer = !verSinLeer },
                    )
                }
                if (verSinLeer) seccionEnFilas("sl", sinLeer, almacen, toque, leerOSel, seleccion, pulsacionLarga)
                else homeItem("sinLeer_list", revealSeen, "cardRow") {
                    CarruselInicio("sl", sinLeer, almacen, revealSeen, seleccion, toque, pulsacionLarga)
                }
                espacioSeccion("sinLeer")
            }

            if (cuantosOcultos > 0) item(key = "ocultos", contentType = "ocultos") {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = HomeContentInset, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LText(
                        if (cuantosOcultos == 1) "1 quitado de Inicio" else "$cuantosOcultos quitados de Inicio",
                        LectorType.Label, Modifier.weight(1f), color = LectorColors.Muted,
                    )
                    ChipSel("Mostrar", false) { ocultos.mostrarTodos() }
                }
            }

            item(key = "bottom_spacer", contentType = "spacer") { Spacer(Modifier.height(30.dp)) }
        }

        if (enSeleccion) {
            Box(Modifier.fillMaxWidth().align(Alignment.TopCenter).background(LectorColors.Bg).statusBarsPadding()) {
                CabeceraSeleccion(
                    cantidad = seleccion.size,
                    onCerrar = { seleccion = emptySet() },
                    onTodo = { seleccion = comics.mapTo(HashSet()) { it.id } },
                    onInvertir = { seleccion = comics.mapTo(HashSet()) { it.id } - seleccion },
                )
            }
        } else HomeTopBar(heroScrollProgressProvider, glowIds, almacen, onAdd)

        Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 76.dp)) {
            BarraAcciones(
                visible = enSeleccion,
                acciones = buildList {
                    fun fin(f: (List<Comic>) -> Unit) { val l = seleccionados; seleccion = emptySet(); f(l) }
                    add(AccionBarra(Iconos.OjoTachado, "Quitar de Inicio") { fin(acciones::quitarDeInicio) })
                    add(AccionBarra(Icons.Rounded.Done, "Leído") { fin { acciones.marcar(it, true) } })
                    add(AccionBarra(Icons.Rounded.Clear, "No leído") { fin { acciones.marcar(it, false) } })
                    if (seleccionados.any { it.enLinea }) add(AccionBarra(Iconos.Descargar, "Descargar") { menuDescarga = true })
                    add(AccionBarra(Icons.Rounded.Menu, "Categorías") { categoriasDe = seleccionados.map { it.id } })
                    if (seleccionados.any { it.leido > 0 }) add(AccionBarra(Icons.Rounded.Refresh, "Historial") {
                        fin { l -> acciones.quitarDelHistorial(l.filter { it.leido > 0 }) }
                    })
                    if (onEliminar != null) add(AccionBarra(Icons.Rounded.Delete, "Quitar") { eliminar = seleccionados })
                },
            )
            MenuShiori(expanded = menuDescarga, onDismissRequest = { menuDescarga = false }) {
                DropdownMenuItem(text = { Text("Siguiente capítulo sin leer") }, onClick = {
                    menuDescarga = false; val l = seleccionados; seleccion = emptySet(); acciones.descargar(l, 1)
                })
                DropdownMenuItem(text = { Text("Siguientes 5 sin leer") }, onClick = {
                    menuDescarga = false; val l = seleccionados; seleccion = emptySet(); acciones.descargar(l, 5)
                })
                DropdownMenuItem(text = { Text("Todos los no leídos") }, onClick = {
                    menuDescarga = false; val l = seleccionados; seleccion = emptySet(); acciones.descargar(l, null)
                })
            }
        }
    }

    hoja?.let { c ->
        HojaComicInicio(c, almacen, puedeEliminar = onEliminar != null, onCerrar = { hoja = null }, onElegir = { o ->
            hoja = null
            when (o) {
                OpcionInicio.LEER -> onRead(c)
                OpcionInicio.FICHA -> onOpen(c)
                OpcionInicio.SELECCIONAR -> seleccion = setOf(c.id)
                OpcionInicio.QUITAR_INICIO -> acciones.quitarDeInicio(listOf(c))
                OpcionInicio.QUITAR_HISTORIAL -> acciones.quitarDelHistorial(listOf(c))
                OpcionInicio.LEIDO -> acciones.marcar(listOf(c), true)
                OpcionInicio.NO_LEIDO -> acciones.marcar(listOf(c), false)
                OpcionInicio.DESCARGAR_1 -> acciones.descargar(listOf(c), 1)
                OpcionInicio.DESCARGAR_5 -> acciones.descargar(listOf(c), 5)
                OpcionInicio.CATEGORIAS -> categoriasDe = listOf(c.id)
                OpcionInicio.EDITAR -> editar = c
                OpcionInicio.COMPARTIR -> acciones.compartir(c)
                OpcionInicio.ELIMINAR -> eliminar = listOf(c)
            }
        })
    }
    categoriasDe?.let { ids -> DialogoCategoriasInicio(ids, almacen) { categoriasDe = null; seleccion = emptySet() } }
    editar?.let { c -> DialogoEditarInicio(c, almacen) { editar = null } }
    eliminar?.let { l ->
        DialogoEliminarInicio(l, onConfirmar = { seleccion = emptySet(); l.forEach { onEliminar?.invoke(it) } }, onCerrar = { eliminar = null })
    }
}

private fun LazyListScope.espacioSeccion(id: String) {
    item(key = "section_spacer_$id", contentType = "spacer") { Spacer(Modifier.height(34.dp)) }
}

/** Una sección desplegada (o «En curso»): filas de Mochy, una por cómic, como items de la lista (6000+ sin problema). */
private fun LazyListScope.seccionEnFilas(
    prefijo: String, lista: List<Comic>, almacen: Almacen,
    onOpen: (Comic) -> Unit, onRead: (Comic) -> Unit,
    seleccion: Set<String>, onLong: (Comic) -> Unit, progreso: Boolean = false,
) {
    items(lista, key = { prefijo + it.id }, contentType = { "row" }) { c ->
        val enSel = seleccion.isNotEmpty()
        val sel = c.id in seleccion
        Box(
            Modifier.fillMaxWidth().padding(horizontal = HomeContentInset - 8.dp, vertical = 2.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(if (sel) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else androidx.compose.ui.graphics.Color.Transparent)
                .padding(horizontal = 8.dp, vertical = 2.dp)
        ) {
            HomeTrackRow(
                comic = c,
                almacen = almacen,
                onClick = { onOpen(c) },
                onLongClick = { onLong(c) },
                onMoreClick = if (enSel) null else ({ onRead(c) }),
                showProgress = progreso,
                modifier = Modifier.fillMaxWidth(),
            )
            if (enSel) MarcaSeleccion(sel, Modifier.align(Alignment.CenterEnd).padding(end = 6.dp))
        }
    }
}

/**
 * La barra de arriba de Mochy (HomeScreen.kt): sobre el héroe solo el logo; al desplazar, el fundido de fondo,
 * el brillo de las portadas (HomeTopGlow) y «Inicio» en negrita en lugar del logo. A la derecha, añadir.
 */
@Composable
private fun BoxScope.HomeTopBar(
    heroScrollProgressProvider: () -> Float,
    glowIds: List<String>,
    almacen: Almacen,
    onAdd: () -> Unit,
) {
    val bg = LectorColors.Bg
    Box(modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter)) {
        val showHeroOverlay by remember { derivedStateOf { heroScrollProgressProvider() > 0.01f } }
        if (showHeroOverlay) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .drawWithCache {
                        // every stop is the page colour, so the fade is built once and only its alpha follows the scroll
                        val fade = Brush.verticalGradient(
                            0f to bg.copy(alpha = 0.9f),
                            0.3f to bg.copy(alpha = 0.6f),
                            0.6f to bg.copy(alpha = 0.3f),
                            1f to bg.copy(alpha = 0f),
                        )
                        onDrawBehind { drawRect(brush = fade, alpha = heroScrollProgressProvider().coerceIn(0f, 1f)) }
                    }
            )
            HomeTopGlow(
                coverIds = glowIds,
                almacen = almacen,
                depth = 180.dp,
                visibility = heroScrollProgressProvider,
                modifier = Modifier.matchParentSize(),
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(top = 14.dp, bottom = 16.dp, start = HomeContentInset, end = 30.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val isScrolled by remember { derivedStateOf { heroScrollProgressProvider() > 0.5f } }
            val topBarProgress by animateFloatAsState(
                targetValue = if (isScrolled) 1f else 0f,
                animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
                label = "topBarProgress"
            )

            // the brand mark over the hero, swapped for a bold "Inicio" once the hero scrolls away
            Box(contentAlignment = Alignment.CenterStart) {
                LogoApp(
                    Modifier
                        .size(44.dp)
                        .graphicsLayer {
                            alpha = 1f - topBarProgress
                            val scale = 1f - topBarProgress * 0.25f
                            scaleX = scale
                            scaleY = scale
                            transformOrigin = TransformOrigin(0f, 0.5f)
                            renderEffect = BlurEffectCache.element(topBarProgress * 15f)
                        }
                )
                Text(
                    text = "Inicio",
                    style = TextStyle(
                        fontFamily = InterFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 32.sp,
                        letterSpacing = (-0.06).em,
                    ),
                    color = LectorColors.Text,
                    maxLines = 1,
                    modifier = Modifier.graphicsLayer {
                        alpha = topBarProgress
                        renderEffect = BlurEffectCache.element((1f - topBarProgress) * 15f)
                    }
                )
            }

            Box(
                modifier = Modifier
                    .graphicsLayer {
                        val scale = 1f - (topBarProgress * 0.1f)
                        scaleX = scale
                        scaleY = scale
                        transformOrigin = TransformOrigin(1f, 0.5f)
                    }
                    .height(44.dp)
                    .clip(RoundedCornerShape(50))
                    .clickable(interactionSource = null, indication = HomeHeaderHighlight, onClick = onAdd),
                contentAlignment = Alignment.CenterEnd
            ) {
                Box(
                    Modifier.padding(vertical = 4.dp).size(36.dp).clip(CircleShape).background(LectorColors.Card.copy(alpha = 0.72f)),
                    Alignment.Center,
                ) { Icon(Icons.Rounded.Add, "Añadir", tint = LectorColors.Text, modifier = Modifier.size(20.dp)) }
            }
        }
    }
}

/** Estado vacío: el «QuietHero» de Mochy (nada aún) con el botón para añadir. */
@Composable
private fun InicioVacio(onAdd: () -> Unit, modifier: Modifier) {
    val acento = MaterialTheme.colorScheme.primary
    Box(
        modifier.fillMaxSize().background(
            Brush.verticalGradient(0f to acento.copy(alpha = 0.14f), 0.55f to LectorColors.Bg, 1f to LectorColors.Bg)
        )
    ) {
        Column(
            Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(Modifier.entrada(0).size(132.dp).clip(SuaveShape(48.dp)).background(acento.copy(alpha = 0.18f)), Alignment.Center) {
                Box(Modifier.size(76.dp).clip(CircleShape).background(acento.copy(alpha = 0.22f)), Alignment.Center) {
                    Icon(Icons.Rounded.Star, null, tint = acento, modifier = Modifier.size(40.dp))
                }
            }
            Spacer(Modifier.height(28.dp))
            LText(
                "Tu estantería espera",
                HomeTitleStyle.copy(fontSize = 30.sp, lineHeight = 34.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center),
                Modifier.entrada(1),
            )
            Spacer(Modifier.height(8.dp))
            LText(
                "Añade un cómic o manga (.cbz, .zip, .pdf) y aparecerá aquí para seguir leyendo.",
                LectorType.Body.copy(textAlign = androidx.compose.ui.text.style.TextAlign.Center), Modifier.entrada(2),
                color = LectorColors.Muted,
            )
            Spacer(Modifier.height(24.dp))
            BotonExpresivo("Añadir cómic", Icons.Rounded.Add, onAdd, Modifier.entrada(3), EstiloBoton.RELLENO, alto = 54.dp)
        }
        LogoApp(
            Modifier.align(Alignment.TopStart).statusBarsPadding()
                .padding(start = LogoEsquinaInicio, top = LogoEsquinaArriba).size(LogoEsquina)
        )
    }
}
