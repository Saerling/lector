package com.example.lector.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.lector.Almacen
import com.example.lector.Anilist
import com.example.lector.Categoria
import com.example.lector.ColorPortada
import com.example.lector.Comic
import com.example.lector.FilaOpcion
import com.example.lector.Modo
import com.example.lector.ResultadoAnilist
import com.example.lector.Seguimiento
import com.example.lector.textoSobre
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Página de un cómic (como la de Komikku): cabecera con la portada difuminada, datos, acciones,
 * categorías como etiquetas con borde y el botón "Reanudar". Se tiñe con el color de la portada.
 */
@Composable
fun DetalleScreen(
    comic: Comic, almacen: Almacen,
    onBack: () -> Unit, onRead: () -> Unit, onChanged: () -> Unit,
) {
    val deLaPortada = remember(comic.id, almacen.colorPortada) {
        if (almacen.colorPortada) ColorPortada.de(almacen.portadaDe(comic.id)) else null
    }
    val acento = deLaPortada ?: MaterialTheme.colorScheme.primary

    var version by remember { mutableStateOf(0) }
    val propias = remember(comic.id, version) {
        val ids = almacen.asignaciones()[comic.id].orEmpty()
        almacen.categorias().filter { it.id in ids }
    }
    val vinculo = remember(comic.id, version) { almacen.seguimientoDe(comic.id) }
    var modo by remember(comic.id) { mutableStateOf(almacen.modoDe(comic.id) ?: almacen.modoPorDefecto()) }

    var dCategorias by remember { mutableStateOf(false) }
    var dSeguimiento by remember { mutableStateOf(false) }
    var dModo by remember { mutableStateOf(false) }
    var dBorrar by remember { mutableStateOf(false) }

    // Entrada suave: la cabecera sube y aparece.
    var entro by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entro = true }
    val t by animateFloatAsState(if (entro) 1f else 0f, tween(550), label = "entrada")

    val porcentaje = (comic.progresoLectura() * 100).toInt()
    val estado = when {
        comic.terminado() -> "Terminado"
        comic.enCurso() -> "En curso · $porcentaje%"
        else -> "Sin leer"
    }

    val scroll = rememberScrollState()
    Box(Modifier.fillMaxSize().background(LectorColors.Bg)) {
        Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(bottom = 130.dp)) {
            Box(Modifier.fillMaxWidth().height(400.dp)) {
                ComicCover(comic, almacen, Modifier.fillMaxSize().graphicsLayer { translationY = scroll.value * 0.5f }.blur(32.dp).alpha(0.6f), SuaveShape(0.dp))
                Box(Modifier.fillMaxSize().background(acento.copy(alpha = 0.18f)))
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.35f to Color.Transparent, 1f to Color.Black)))

                Row(
                    Modifier.align(Alignment.BottomStart).padding(24.dp)
                        .graphicsLayer { alpha = t; translationY = (1f - t) * 60f },
                    verticalAlignment = Alignment.Bottom
                ) {
                    ComicCover(comic, almacen, Modifier.width(124.dp).aspectRatio(2f / 3f), SuaveShape(20.dp))
                    Spacer(Modifier.width(18.dp))
                    Column(Modifier.weight(1f)) {
                        LText(comic.titulo, LectorType.Hero, maxLines = 3)
                        Spacer(Modifier.height(6.dp))
                        LText("${comic.paginas} páginas", LectorType.Body, color = LectorColors.Muted)
                        LText(estado, LectorType.Body, color = acento)
                        if (vinculo != null) LText("AniList: ${vinculo.titulo}", LectorType.Label, color = LectorColors.Muted, maxLines = 1)
                    }
                }
            }

            // Acciones (como "En biblioteca / Seguimiento / WebView" de Komikku)
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
                Accion(Icons.Rounded.Menu, if (propias.isEmpty()) "Categorías" else "${propias.size} categ.", propias.isNotEmpty(), acento, Modifier.weight(1f)) { dCategorias = true }
                Accion(Icons.Rounded.Refresh, if (vinculo != null) "AniList ✓" else "Seguimiento", vinculo != null, acento, Modifier.weight(1f)) { dSeguimiento = true }
                Accion(Icons.Rounded.Settings, nombreModo(modo), true, acento, Modifier.weight(1f)) { dModo = true }
                Accion(Icons.Rounded.Delete, "Eliminar", false, acento, Modifier.weight(1f)) { dBorrar = true }
            }

            Column(Modifier.padding(horizontal = 24.dp, vertical = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LText(comic.textoPagina(), LectorType.Section, Modifier.weight(1f))
                    Odometro("$porcentaje%", LectorType.Body, color = acento)
                }
                Spacer(Modifier.height(10.dp))
                ProgressLine(comic.progresoLectura())
            }

            if (propias.isNotEmpty()) {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) { propias.forEach { ChipBorde(it.nombre, acento) } }
            }
        }

        BarraSuperior({ scroll.value.toFloat() }, comic.titulo, onBack)

        // Botón "Reanudar" flotante, del color de la portada.
        // Al bajar se encoge a solo el icono; al subir vuelve a enseñar el texto (como el botón de musicfy).
        var extendido by remember { mutableStateOf(true) }
        LaunchedEffect(scroll) {
            var ancla = 0
            snapshotFlow { scroll.value }.collect { v ->
                when {
                    v < 120 -> { extendido = true; ancla = v }
                    v > ancla + 24 -> { extendido = false; ancla = v }
                    v < ancla - 24 -> { extendido = true; ancla = v }
                }
            }
        }
        val relleno by animateDpAsState(if (extendido) 26.dp else 16.dp, tween(300, easing = EnfasisEasing), label = "rellenoReanudar")
        Row(
            Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(24.dp)
                .pressable(onRead).clip(SuaveShape(22.dp)).background(acento)
                .padding(horizontal = relleno, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Rounded.PlayArrow, null, tint = textoSobre(acento), modifier = Modifier.size(24.dp))
            AnimatedVisibility(
                visible = extendido,
                enter = expandHorizontally(tween(300, easing = EnfasisEasing)) + fadeIn(tween(240)),
                exit = shrinkHorizontally(tween(260, easing = EnfasisEasing)) + fadeOut(tween(160)),
            ) {
                Row {
                    Spacer(Modifier.width(10.dp))
                    LText(
                        if (comic.enCurso()) "Reanudar" else "Leer", LectorType.Section.copy(fontSize = 18.sp),
                        color = textoSobre(acento), maxLines = 1,
                    )
                }
            }
        }
    }

    if (dCategorias) DialogoCategoriasComic(comic, almacen) { dCategorias = false; version++; onChanged() }
    if (dSeguimiento) DialogoSeguimientoComic(comic, almacen) { dSeguimiento = false; version++ }
    if (dModo) {
        AlertDialog(
            onDismissRequest = { dModo = false },
            title = { Text("Modo de lectura") },
            text = {
                Column {
                    Modo.values().forEach { m ->
                        FilaOpcion(etiqueta = nombreModo(m), marcada = modo == m) {
                            modo = m; almacen.guardarModo(comic.id, m); dModo = false
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { dModo = false }) { Text("Cerrar") } },
        )
    }
    if (dBorrar) {
        AlertDialog(
            onDismissRequest = { dBorrar = false },
            title = { Text("Eliminar") },
            text = { Text("¿Quitar «${comic.titulo}» de la biblioteca? Se borra la copia guardada en la app.") },
            confirmButton = {
                TextButton(onClick = { dBorrar = false; almacen.eliminar(comic.id); onChanged(); onBack() }) { Text("Eliminar") }
            },
            dismissButton = { TextButton(onClick = { dBorrar = false }) { Text("Cancelar") } },
        )
    }
}

private fun nombreModo(m: Modo) = when (m) {
    Modo.MANGA -> "Manga"
    Modo.COMIC -> "Cómic"
    Modo.VERTICAL -> "Vertical"
}

@Composable
private fun Accion(icon: ImageVector, texto: String, activa: Boolean, acento: Color, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.pressable(onClick).padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, tint = if (activa) acento else LectorColors.Muted, modifier = Modifier.size(26.dp))
        Spacer(Modifier.height(6.dp))
        LText(texto, LectorType.Label, color = if (activa) acento else LectorColors.Muted, maxLines = 1)
    }
}

@Composable
private fun DialogoCategoriasComic(comic: Comic, almacen: Almacen, alCerrar: () -> Unit) {
    val todas = remember { almacen.categorias() }
    var marcadas by remember { mutableStateOf(almacen.asignaciones()[comic.id].orEmpty()) }
    AlertDialog(
        onDismissRequest = alCerrar,
        title = { Text("Categorías") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (todas.isEmpty()) Text("Crea categorías en Más → Categorías.")
                todas.forEach { c: Categoria ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = c.id in marcadas, onCheckedChange = {
                            marcadas = if (it) marcadas + c.id else marcadas - c.id
                        })
                        Text(c.nombre)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { almacen.asignarCategorias(comic.id, marcadas); alCerrar() }) { Text("Guardar") } },
        dismissButton = { TextButton(onClick = alCerrar) { Text("Cancelar") } },
    )
}

// Intenta adivinar el número de volumen o capítulo a partir del título ("Vol. 3", "Cap 12"...).
private fun numeroDeTitulo(titulo: String): Int? {
    val conPrefijo = Regex("(?i)(?:vol(?:umen|ume)?|cap(?:[ií]tulo)?|ch(?:apter)?|[vc#])[ .]*0*(\\d+)").find(titulo)
    if (conPrefijo != null) return conPrefijo.groupValues[1].toIntOrNull()
    return Regex("(\\d+)").findAll(titulo).lastOrNull()?.groupValues?.get(1)?.toIntOrNull()
}

/** Vincular un cómic con una serie de AniList (mismo comportamiento que el diálogo de la biblioteca). */
@Composable
fun DialogoSeguimientoComic(comic: Comic, almacen: Almacen, alCerrar: () -> Unit) {
    val ambito = rememberCoroutineScope()
    val vinculoInicial = remember(comic.id) { almacen.seguimientoDe(comic.id) }
    var elegido by remember(comic.id) { mutableStateOf(vinculoInicial?.let { it.mediaId to it.titulo }) }
    var porVolumenes by remember(comic.id) { mutableStateOf(vinculoInicial?.volumenes ?: true) }
    var numero by remember(comic.id) {
        mutableStateOf((vinculoInicial?.numero ?: numeroDeTitulo(comic.titulo))?.toString() ?: "")
    }
    var busqueda by remember(comic.id) { mutableStateOf(comic.titulo) }
    var resultados by remember(comic.id) { mutableStateOf(emptyList<ResultadoAnilist>()) }
    var cargando by remember(comic.id) { mutableStateOf(false) }
    var error by remember(comic.id) { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = alCerrar,
        title = { Text("Seguimiento") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                val serie = elegido
                if (serie != null) {
                    Text("Serie en AniList")
                    Text(serie.second)
                    TextButton(onClick = { elegido = null }) { Text("Cambiar serie") }
                    Text("Este archivo es un…")
                    FilaOpcion(etiqueta = "Volumen", marcada = porVolumenes) { porVolumenes = true }
                    FilaOpcion(etiqueta = "Capítulo", marcada = !porVolumenes) { porVolumenes = false }
                    OutlinedTextField(
                        value = numero, onValueChange = { numero = it.filter { c -> c.isDigit() }.take(5) },
                        singleLine = true, label = { Text("Número") },
                    )
                    if (almacen.anilistToken == null) {
                        Text("Aún no has iniciado sesión: Más → Ajustes → Seguimiento.", color = MaterialTheme.colorScheme.primary)
                    }
                } else {
                    OutlinedTextField(
                        value = busqueda, onValueChange = { busqueda = it },
                        singleLine = true, label = { Text("Buscar en AniList") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    TextButton(
                        enabled = busqueda.isNotBlank() && !cargando,
                        onClick = {
                            ambito.launch {
                                cargando = true; error = null
                                val r = withContext(Dispatchers.IO) { runCatching { Anilist.buscar(busqueda) } }
                                r.onSuccess { resultados = it }
                                    .onFailure { error = "No se pudo buscar. Revisa tu conexión." }
                                cargando = false
                            }
                        },
                    ) { Text(if (cargando) "Buscando…" else "Buscar") }
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    resultados.forEach { r ->
                        TextButton(onClick = { elegido = r.id to r.titulo }) {
                            Text(r.titulo + (r.formato?.let { " · $it" } ?: ""))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = elegido != null && numero.isNotEmpty(),
                onClick = {
                    val serie = elegido
                    val n = numero.toIntOrNull()
                    if (serie != null && n != null) {
                        almacen.guardarSeguimiento(comic.id, Seguimiento(serie.first, serie.second, porVolumenes, n))
                        if (comic.ultimaPagina >= comic.paginas - 1) {
                            almacen.marcarSincronizacion(comic.id, true)
                            ambito.launch { withContext(Dispatchers.IO) { almacen.sincronizarAnilist(comic.id) } }
                        }
                        alCerrar()
                    }
                },
            ) { Text("Guardar") }
        },
        dismissButton = {
            Row {
                if (vinculoInicial != null) {
                    TextButton(onClick = { almacen.guardarSeguimiento(comic.id, null); alCerrar() }) { Text("Desvincular") }
                }
                TextButton(onClick = alCerrar) { Text("Cancelar") }
            }
        },
    )
}
