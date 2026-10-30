package com.example.lector.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.lector.Almacen
import com.example.lector.Comic
import kotlinx.coroutines.delay
import kotlin.math.abs

private val BlurReal = android.os.Build.VERSION.SDK_INT >= 31

@Composable
fun HomeScreen(
    comics: List<Comic>,
    almacen: Almacen,
    onOpen: (Comic) -> Unit,
    onRead: (Comic) -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (comics.isEmpty()) {
        InicioVacio(onAdd, modifier)
        return
    }

    val featured = remember(comics) {
        comics.firstOrNull { it.id == almacen.ultimoAbierto } ?: comics.maxByOrNull { it.leido } ?: comics.first()
    }
    val recientes = remember(comics) { comics.filter { it.leido > 0 }.sortedByDescending { it.leido }.take(12) }
    val enCurso = remember(comics) { comics.filter { it.enCurso() }.sortedByDescending { it.leido }.take(3) }
    val sinLeer = remember(comics) { comics.filter { it.leido == 0L } }
    // Carrusel héroe: el destacado primero y luego lo que estás leyendo (o, si no hay, lo que tienes por empezar).
    val heroe = remember(comics, featured) {
        val base = (listOf(featured) + comics.filter { it.enCurso() }.sortedByDescending { it.leido } + recientes)
            .distinctBy { it.id }
        (if (base.size >= 2) base else (base + sinLeer).distinctBy { it.id }).take(6)
    }

    val list = rememberLazyListState()
    val altoHero = (LocalConfiguration.current.screenHeightDp * 0.56f).dp.coerceIn(420.dp, 640.dp)
    val altoPx = with(LocalDensity.current) { altoHero.toPx() }
    // Cuánto se ha desplazado la cabecera (se lee solo al dibujar, no recompone la pantalla).
    val scrollPx: () -> Float = { if (list.firstVisibleItemIndex == 0) list.firstVisibleItemScrollOffset.toFloat() else altoPx }
    val collapsed by remember { derivedStateOf { list.firstVisibleItemIndex > 0 } }

    Box(modifier.fillMaxSize().background(LectorColors.Bg)) {
        LazyColumn(state = list, contentPadding = PaddingValues(bottom = 190.dp)) {
            item(key = "heroe") { HeroCarrusel(heroe, almacen, onOpen, onRead, scrollPx, altoPx, altoHero) }
            if (recientes.isNotEmpty()) item(key = "recientes") {
                Column(Modifier.entrada(1)) {
                    Spacer(Modifier.height(20.dp))
                    TituloInicio("Leídos recientemente", recientes.size)
                    Spacer(Modifier.height(14.dp))
                    CoverCarousel(recientes, almacen, onOpen)
                }
            }
            if (enCurso.isNotEmpty()) {
                item(key = "enCursoT") {
                    Column(Modifier.entrada(2)) {
                        Spacer(Modifier.height(30.dp)); TituloInicio("En curso", enCurso.size); Spacer(Modifier.height(10.dp))
                    }
                }
                itemsIndexed(enCurso, key = { _, c -> "ec" + c.id }) { i, c -> FilaProgreso(c, almacen, onOpen, onRead, Modifier.entrada(i + 3)) }
            }
            if (sinLeer.isNotEmpty()) item(key = "sinLeer") {
                Column(Modifier.entrada(4)) {
                    Spacer(Modifier.height(30.dp))
                    TituloInicio("Sin leer", sinLeer.size)
                    Spacer(Modifier.height(14.dp))
                    CoverCarousel(sinLeer, almacen, onOpen)
                }
            }
        }

        AnimatedVisibility(
            collapsed,
            enter = fadeIn(tween(250)) + slideInVertically { -it / 2 },
            exit = fadeOut(tween(200)) + slideOutVertically { -it / 2 }
        ) {
            Box(
                Modifier.fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(LectorColors.Bg, LectorColors.Bg.copy(0.92f), Color.Transparent)))
                    .statusBarsPadding().padding(start = 76.dp, end = 24.dp, top = 12.dp, bottom = 12.dp)
            ) { LText("Inicio", LectorType.Display) }
        }

        // El icono de la app, fijo en la esquina; a la derecha, añadir (botón tonal redondo de cristal).
        LogoApp(
            Modifier.align(Alignment.TopStart).statusBarsPadding()
                .padding(start = LogoEsquinaInicio, top = LogoEsquinaArriba).size(LogoEsquina)
        )
        Box(
            Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(end = 18.dp, top = 16.dp).size(42.dp)
                .clip(CircleShape).background(LectorColors.Card.copy(alpha = 0.72f)).pressable(onAdd),
            Alignment.Center
        ) { Icon(Icons.Rounded.Add, "Añadir", tint = LectorColors.Text, modifier = Modifier.size(22.dp)) }
    }
}

/** Título de sección de Inicio: 22 sp en negrita con el recuento en una píldora tonal. */
@Composable
private fun TituloInicio(titulo: String, cuenta: Int) {
    Row(Modifier.padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
        LText(titulo, LectorType.Section.copy(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold), Modifier.weight(1f, fill = false), maxLines = 1)
        Spacer(Modifier.width(10.dp))
        Box(
            Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f))
                .padding(horizontal = 10.dp, vertical = 3.dp)
        ) { LText("$cuenta", LectorType.Label.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.primary) }
    }
}

@Composable
private fun HeroCarrusel(
    items: List<Comic>, almacen: Almacen, onOpen: (Comic) -> Unit, onRead: (Comic) -> Unit,
    scroll: () -> Float, altoPx: Float, alto: Dp,
) {
    val pager = rememberPagerState(pageCount = { items.size })
    val arrastrando by pager.interactionSource.collectIsDraggedAsState()
    val reducido = Mov.reducido
    // Auto-avance suave cada 6 s (se pausa mientras lo arrastras y con movimiento reducido).
    val pausado = GestoAtras.hayEncima // con una página encima no tiene sentido seguir pasando (ni gastar)
    LaunchedEffect(pager.settledPage, arrastrando, items.size, reducido, pausado) {
        if (pausado || reducido || arrastrando || items.size < 2) return@LaunchedEffect
        delay(6000)
        pager.animateScrollToPage((pager.settledPage + 1) % items.size, animationSpec = spring(0.9f, 140f))
    }

    Box(
        Modifier.fillMaxWidth().height(alto).graphicsLayer {
            if (!reducido) {
                val s = scroll()
                translationY = s * 0.45f                    // parallax vertical: el héroe sube más despacio
                alpha = 1f - (s / altoPx).coerceIn(0f, 1f) * 0.85f
            }
        }
    ) {
        HorizontalPager(
            pager, Modifier.fillMaxSize(), beyondViewportPageCount = 1, key = { items[it].id }
        ) { page ->
            val c = items[page]
            PaginaHeroe(c, almacen, pager, page, reducido, onOpen, onRead)
        }
        // Indicador de puntos que se estira con muelle.
        Row(
            Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically,
        ) {
            if (items.size > 1) repeat(items.size) { i ->
                val sel = pager.currentPage == i
                val ancho by animateDpAsState(if (sel) 26.dp else 8.dp, Muelles.espacial(), label = "punto")
                val color by animateColorAsState(
                    if (sel) MaterialTheme.colorScheme.primary else LectorColors.Muted.copy(alpha = 0.45f), Muelles.efectos(), label = "puntoC"
                )
                Box(Modifier.height(8.dp).width(ancho).clip(CircleShape).background(color))
            }
        }
    }
}

@Composable
private fun PaginaHeroe(
    c: Comic, almacen: Almacen, pager: PagerState, page: Int, reducido: Boolean,
    onOpen: (Comic) -> Unit, onRead: (Comic) -> Unit,
) {
    val desfase: () -> Float = { (pager.currentPage - page) + pager.currentPageOffsetFraction }
    Box(Modifier.fillMaxSize().clip(SuaveShape(0.dp))) {
        // Fondo: la portada a sangre, desenfocada (Android 12+) y con parallax horizontal al pasar de página.
        Box(Modifier.fillMaxSize().graphicsLayer {
            if (!reducido) translationX = desfase() * size.width * 0.45f
            scaleX = 1.15f; scaleY = 1.15f
        }) {
            ComicCover(
                c, almacen,
                Modifier.fillMaxSize().then(if (BlurReal) Modifier.blur(36.dp) else Modifier).graphicsLayer { alpha = if (BlurReal) 0.9f else 0.55f },
                SuaveShape(0.dp)
            )
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to LectorColors.Bg.copy(alpha = if (BlurReal) 0.25f else 0.55f),
                    0.45f to LectorColors.Bg.copy(alpha = if (BlurReal) 0.15f else 0.5f),
                    0.78f to LectorColors.Bg.copy(alpha = 0.85f),
                    1f to LectorColors.Bg,
                )
            )
        )
        // Primer plano: portada nítida con sombra + título + botón Continuar. Se mueven un poco más rápido (profundidad).
        Column(
            Modifier.fillMaxSize().statusBarsPadding().padding(top = 64.dp, bottom = 34.dp)
                .graphicsLayer {
                    if (!reducido) {
                        val d = desfase()
                        translationX = -d * size.width * 0.12f
                        alpha = 1f - abs(d).coerceIn(0f, 1f) * 0.6f
                    }
                },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Bottom,
        ) {
            val forma = SuaveShape(18.dp)
            Box(
                Modifier.weight(1f, fill = false).heightIn(max = 260.dp).aspectRatio(2f / 3f, matchHeightConstraintsFirst = true)
                    .shadow(28.dp, forma, clip = false, ambientColor = Color.Black.copy(alpha = 0.5f), spotColor = Color.Black.copy(alpha = 0.6f))
                    .origenTransicion().press3D().pressable { onOpen(c) }
            ) { ComicCover(c, almacen, Modifier.fillMaxSize(), forma) }
            Spacer(Modifier.height(20.dp))
            LText(
                if (c.leido > 0) "SEGUIR LEYENDO" else "PARA EMPEZAR",
                LectorType.Label.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp),
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(4.dp))
            LText(
                c.titulo,
                LectorType.Display.copy(fontSize = 28.sp, lineHeight = 32.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center),
                Modifier.padding(horizontal = 32.dp), maxLines = 2,
            )
            Spacer(Modifier.height(4.dp))
            LText(c.textoPagina(), LectorType.Label, color = LectorColors.Muted, maxLines = 1)
            Spacer(Modifier.height(16.dp))
            BotonExpresivo(
                if (c.leido > 0) "Continuar" else "Leer", Icons.Rounded.PlayArrow, { onRead(c) },
                Modifier.widthIn(min = 168.dp), EstiloBoton.RELLENO, alto = 54.dp,
            )
        }
    }
}

/** Fila «En curso» como tarjeta: portada, título, progreso de acento y botón redondo para seguir. */
@Composable
private fun FilaProgreso(c: Comic, almacen: Almacen, onOpen: (Comic) -> Unit, onRead: (Comic) -> Unit, modifier: Modifier = Modifier) {
    val acento = MaterialTheme.colorScheme.primary
    Row(
        modifier.padding(horizontal = 20.dp, vertical = 5.dp).fillMaxWidth().clip(SuaveShape(24.dp))
            .background(LectorColors.Card).pressableCon(0.97f) { onOpen(c) }.padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ComicCover(c, almacen, Modifier.size(width = 52.dp, height = 74.dp).origenTransicion(), SuaveShape(14.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            LText(c.titulo, LectorType.Body.copy(fontSize = 16.sp, fontWeight = FontWeight.SemiBold), maxLines = 1)
            Spacer(Modifier.height(2.dp))
            LText(c.textoPagina(), LectorType.Label, color = LectorColors.Muted, maxLines = 1)
            Spacer(Modifier.height(10.dp))
            Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(LectorColors.PillFill)) {
                Box(Modifier.fillMaxWidth(c.progresoLectura().coerceIn(0f, 1f)).fillMaxHeight().clip(CircleShape).background(acento))
            }
        }
        Spacer(Modifier.width(12.dp))
        Box(
            Modifier.size(44.dp).clip(CircleShape).background(acento.copy(alpha = 0.18f)).pressable { onRead(c) },
            Alignment.Center
        ) { Icon(Icons.Rounded.PlayArrow, "Leer", tint = acento, modifier = Modifier.size(22.dp)) }
    }
}

/** Estado vacío: icono grande en círculo tonal, texto con jerarquía y botón de acento. */
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
            LText("Tu estantería espera", LectorType.Display.copy(fontSize = 30.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center), Modifier.entrada(1))
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
