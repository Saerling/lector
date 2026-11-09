package com.example.lector.ui

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.derivedStateOf
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.lector.ext.*
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import kotlinx.coroutines.launch
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import com.example.lector.ui.mochy.artist.*

private fun abrirUrl(ctx: Context, url: String) {
    runCatching {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

// ───────────────────── Listados de extensiones ─────────────────────

@Composable
fun ListadosExtScreen(ext: ExtManager, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val acento = MaterialTheme.colorScheme.primary
    var anadir by remember { mutableStateOf(false) }
    var borrar by remember { mutableStateOf<RepoExt?>(null) }

    Box(Modifier.fillMaxSize()) {
        PaginaAjustes("Listados de extensiones", onBack) {
            if (ext.repos.isEmpty()) {
                LText("No hay listados. Añade uno con el botón de abajo.", LectorType.Body, Modifier.padding(24.dp), color = LectorColors.Muted)
            }
            ext.repos.forEach { r ->
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
                        .alpha(if (r.activo) 1f else 0.5f)
                        .clip(SuaveShape(24.dp)).background(LectorColors.Card).padding(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(56.dp).clip(SuaveShape(14.dp)).background(acento.copy(alpha = 0.2f)),
                            Alignment.Center
                        ) { LText(r.sigla, LectorType.Section, color = acento) }
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            LText(r.nombre, LectorType.Section.copy(fontSize = 18.sp), maxLines = 1)
                            LText(r.url, LectorType.Label, color = LectorColors.Muted, maxLines = 2)
                        }
                    }
                    Row(Modifier.fillMaxWidth(), Arrangement.End, Alignment.CenterVertically) {
                        r.web?.let { w -> AccionTexto("Web") { abrirUrl(ctx, w) } }
                        AccionTexto("Copiar") {
                            val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("Listado", r.url))
                            Avisos.mostrar("Dirección copiada")
                        }
                        AccionTexto(if (r.activo) "Ocultar" else "Mostrar") { ext.alternarRepo(r.url) }
                        IconButton(onClick = { borrar = r }) { Icon(Icons.Rounded.Delete, "Borrar", tint = LectorColors.Text) }
                    }
                }
            }
            Spacer(Modifier.height(96.dp))
        }
        Row(
            Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(20.dp)
                .pressable { anadir = true }.clip(SuaveShape(20.dp))
                .background(acento.copy(alpha = 0.28f)).padding(horizontal = 22.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Rounded.Add, null, tint = LectorColors.Text)
            Spacer(Modifier.width(10.dp))
            LText("Añadir", LectorType.Section.copy(fontSize = 17.sp))
        }
    }

    if (anadir) DialogoAnadirRepo(ext) { anadir = false }
    borrar?.let { r ->
        DialogoAlerta(
            onDismissRequest = { borrar = null },
            title = { Text("¿Quitar «${r.nombre}»?") },
            text = { Text("Las extensiones ya instaladas se quedan; solo desaparece este listado.") },
            confirmButton = { TextButton(onClick = { ext.eliminarRepo(r.url); borrar = null }) { Text("Quitar") } },
            dismissButton = { TextButton(onClick = { borrar = null }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun AccionTexto(texto: String, onClick: () -> Unit) {
    TextButton(onClick = onClick) { Text(texto) }
}

@Composable
private fun DialogoAnadirRepo(ext: ExtManager, cerrar: () -> Unit) {
    // Listados conocidos (direcciones publicadas por sus autores). Salen solo los que aún no tienes.
    val sugeridos = remember(ext.repos) {
        listOf(
            "Keiyoushi" to "https://raw.githubusercontent.com/keiyoushi/extensions/repo/index.min.json",
            "Suwayomi" to "https://raw.githubusercontent.com/suwayomi/tachiyomi-extension/repo/index.min.json",
            "Yūzōnō" to "https://github.com/yuzono/manga-repo/raw/repo/index.pb",
            "Cursed Yūzōnō" to "https://raw.githubusercontent.com/yuzono/cursed-manga-repo/repo/index.min.json",
            "Kavita" to "https://raw.githubusercontent.com/Kareadita/tach-extension/repo/index.min.json",
        ).filter { (_, u) -> ext.repos.none { ExtRepos.carpeta(it.url) == ExtRepos.carpeta(ExtRepos.normalizar(u) ?: u) } }
    }
    var url by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var trabajando by remember { mutableStateOf(false) }
    DialogoAlerta(
        onDismissRequest = { if (!trabajando) cerrar() },
        title = { Text("Añadir listado de extensiones") },
        text = {
            Column {
                CampoTextoShiori(
                    value = url, onValueChange = { url = it; error = null }, singleLine = true,
                    enabled = !trabajando, isError = error != null,
                    label = { Text("Dirección URL del listado") },
                    supportingText = {
                        Text(error ?: "Acepta la dirección del índice, repo.json, un enlace de GitHub o usuario/repositorio")
                    },
                )
                if (sugeridos.isNotEmpty() && !trabajando) {
                    Spacer(Modifier.height(10.dp))
                    Text("Listados conocidos", style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.height(6.dp))
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        sugeridos.forEach { (nombre, u) -> ChipSel(nombre, url == u) { url = u; error = null } }
                    }
                }
                if (trabajando) {
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IndicadorCarga(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(12.dp))
                        Text("Comprobando el listado…")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = url.isNotBlank() && !trabajando,
                onClick = {
                    trabajando = true
                    ext.anadirRepo(url) { e ->
                        trabajando = false
                        if (e == null) cerrar() else error = e
                    }
                }
            ) { Text("Añadir") }
        },
        dismissButton = { TextButton(enabled = !trabajando, onClick = cerrar) { Text("Cancelar") } },
    )
}

// ───────────────────── Detalle de una extensión ─────────────────────

@Composable
fun ExtDetalleScreen(ext: ExtManager, pkg: String, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val e = ext.instaladas.firstOrNull { it.pkg == pkg }
    var ajustesDe by remember { mutableStateOf<Long?>(null) }
    val configurables = ext.configurables(pkg)
    val abierta = ajustesDe?.let { id -> configurables.firstOrNull { it.id == id } }
    if (abierta != null) {
        AjustesFuenteScreen(ext, pkg, abierta, abierta.name) { ajustesDe = null }
        return
    }
    if (e == null) {
        PaginaAjustes("Extensión", onBack) {
            LText("Esta extensión ya no está instalada.", LectorType.Body, Modifier.padding(24.dp), color = LectorColors.Muted)
        }
        return
    }
    // La página de artista de Mochy (ui/mochy/artist): el icono hace de banner desenfocado y de foto.
    val abrirInfo = {
        runCatching {
            ctx.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${e.pkg}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
        Unit
    }
    val listaEstado = rememberLazyListState()
    val alturaBanner = artistBannerHeight(0.5f)
    val alturaBannerPx = with(LocalDensity.current) { alturaBanner.toPx() }
    val progreso = remember(listaEstado, alturaBannerPx) {
        derivedStateOf {
            if (listaEstado.firstVisibleItemIndex > 0) 1f
            else (listaEstado.firstVisibleItemScrollOffset / alturaBannerPx).coerceIn(0f, 1f)
        }
    }
    val fuentes = ext.disponibles.firstOrNull { it.pkg == e.pkg }?.fuentes.orEmpty()
    val problema = e.errorCarga ?: ext.erroresCarga[e.pkg]
    ArtistPageScaffold(
        title = e.nombre, bannerHeight = alturaBanner, scrollProgress = progreso, onBack = onBack,
        banner = { IconBackdrop { IconoExt(e.pkg, null, Modifier.fillMaxSize()) } },
    ) {
        LazyColumn(
            state = listaEstado,
            contentPadding = WindowInsets.navigationBars.asPaddingValues(),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "header", contentType = "header") {
                Column(
                    Modifier.fillMaxWidth()
                        .padding(top = alturaBanner * ArtistAvatarAt)
                        .padding(start = PlaylistInset - 3.dp, end = PlaylistInset)
                ) {
                    ArtistAvatar { IconoExt(e.pkg, null, Modifier.fillMaxSize()) }
                    Spacer(Modifier.height(14.dp))
                    ArtistName(e.nombre)
                    Spacer(Modifier.height(4.dp))
                    ArtistStats(
                        listOfNotNull(
                            "${bandera(e.lang)} ${nombreIdioma(e.lang)}", "v${e.versionName}",
                            if (e.confiable) "De confianza" else "Sin confianza",
                        ).joinToString(" · ")
                    )
                    Spacer(Modifier.height(14.dp))
                    ArtistActions {
                        val a = e.actualizacion
                        when {
                            a != null -> PlayPill("Actualizar a ${a.versionName}", Icons.Rounded.Refresh) { ext.instalar(a) }
                            !e.confiable -> PlayPill("Confiar", Icons.Rounded.Check) { ext.confiar(e) }
                            else -> PlayPill("Información", Icons.Rounded.Info, abrirInfo)
                        }
                        if (a != null && !e.confiable) CircleAction(Icons.Rounded.Check, "Confiar", { ext.confiar(e) })
                        if (e.confiable) CircleAction(Icons.Rounded.Close, "Dejar de confiar", { ext.dejarDeConfiar(e) })
                        if (a != null || !e.confiable) CircleAction(Icons.Rounded.Info, "Información de la app", abrirInfo)
                        CircleAction(Icons.Rounded.Delete, "Desinstalar", { ext.desinstalar(e.pkg) })
                    }
                }
            }
            if (problema != null) item(key = "problema", contentType = "card") {
                ArtistCard(Modifier.padding(start = PlaylistInset - 3.dp, end = PlaylistInset - 3.dp, top = 22.dp), tint = LectorColors.Error) {
                    Column {
                        Text("Problema", style = CardLabelStyle, color = LectorColors.Error)
                        Spacer(Modifier.height(5.dp))
                        Text(problema, style = AboutBodyStyle, color = LectorColors.Text.copy(alpha = 0.74f), maxLines = 6)
                    }
                }
            }
            item(key = "info_title", contentType = "shelf_title") {
                ArtistShelfTitle("Información", Modifier.padding(top = 30.dp, bottom = 8.dp))
            }
            item(key = "info", contentType = "card") {
                ArtistCard(Modifier.padding(horizontal = PlaylistInset - 3.dp)) {
                    Column {
                        ArtistInfoRow("Paquete", e.pkg)
                        ArtistInfoRow("Versión", e.versionName)
                        ArtistInfoRow("Listado", e.repoSigla?.let { "@$it" } ?: "—")
                        ArtistInfoRow("Estado", if (e.confiable) "De confianza" else "Sin confianza")
                    }
                }
            }
            item(key = "fuentes_title", contentType = "shelf_title") {
                ArtistShelfTitle("Fuentes", Modifier.padding(top = 30.dp, bottom = 4.dp), count = fuentes.size)
            }
            items(fuentes.size, contentType = { "fuente" }) { i ->
                val f = fuentes[i]
                // fila de pista de Mochy: 46 dp de «portada» (la bandera), título y subtítulo
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = PlaylistInset - 3.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(46.dp).clip(TrackShape).background(LectorColors.Text.copy(alpha = 0.08f)), Alignment.Center) {
                        LText(bandera(f.lang), LectorType.Section.copy(fontSize = 22.sp))
                    }
                    Column(Modifier.weight(1f).padding(horizontal = 11.dp)) {
                        Text(f.nombre, style = TrackTitleStyle.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold), color = LectorColors.Text, maxLines = 1)
                        Text("${nombreIdioma(f.lang)}  ·  ${f.baseUrl}", style = TrackSubtitleStyle, color = LectorColors.Text.copy(alpha = 0.6f), maxLines = 1)
                    }
                }
            }
            if (configurables.isNotEmpty()) {
                item(key = "ajustes_title", contentType = "shelf_title") {
                    ArtistShelfTitle("Ajustes de las fuentes", Modifier.padding(top = 30.dp, bottom = 4.dp))
                }
                items(configurables.size, contentType = { "ajuste" }) { i ->
                    val c = configurables[i]
                    FilaAjuste(Icons.Rounded.Settings, c.name, "Dominio, inicio de sesión, calidad…") { ajustesDe = c.id }
                }
            } else item(key = "sin_ajustes") {
                LText(
                    if (e.confiable && e.errorCarga == null) "Esta extensión no tiene ajustes propios (o todavía se está cargando)."
                    else "Los ajustes propios de cada fuente aparecen cuando la extensión es de confianza y se puede cargar.",
                    LectorType.Label, Modifier.padding(horizontal = PlaylistInset, vertical = 24.dp), color = LectorColors.Muted
                )
            }
            item(key = "bottom_spacer") { Spacer(Modifier.height(50.dp)) }
        }
    }
}

@Composable
private fun Dato(etiqueta: String, valor: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 7.dp)) {
        LText(etiqueta, LectorType.Label.copy(fontSize = 13.sp), Modifier.width(92.dp), color = LectorColors.Muted)
        LText(valor, LectorType.Body.copy(fontWeight = FontWeight.Bold), Modifier.weight(1f))
    }
}

// ───────────────────── Una fuente ─────────────────────

@Composable
fun FuenteDetalleScreen(
    ext: ExtManager, fuenteId: Long, onManga: (MangaFuente) -> Unit,
    // Género que se acaba de tocar en una ficha: se busca nada más abrir (y se avisa al terminar).
    generoBuscar: String? = null, alConsumirGenero: () -> Unit = {},
    onBack: () -> Unit,
) {
    val ctx = LocalContext.current
    val f = ext.fuentes.firstOrNull { it.id == fuenteId }
    val motor = Motor.actual
    LaunchedEffect(fuenteId) { f?.let { ext.marcarReciente(it.id) } }

    when {
        f == null -> PaginaAjustes("Fuente", onBack) {
            LText("No se encontró la fuente. Quizá se desinstaló la extensión.", LectorType.Body, Modifier.padding(24.dp), color = LectorColors.Muted)
        }
        motor == null -> PaginaAjustes(f.nombre, onBack) {
            Dato("Idioma", "${bandera(f.lang)} ${nombreIdioma(f.lang)}")
            if (f.baseUrl.isNotBlank()) Dato("Sitio", f.baseUrl)
            Divisor()
            if (f.baseUrl.isNotBlank()) {
                FilaAjuste(Icons.Rounded.Share, "Abrir sitio web", "Se abre en tu navegador") { abrirUrl(ctx, f.baseUrl) }
            }
            LText(
                "Explorar los títulos de esta fuente dentro de la app necesita el motor de fuentes, que se conecta en el siguiente paso (mira HANDOFF.md).",
                LectorType.Label, Modifier.padding(24.dp), color = LectorColors.Muted
            )
        }
        else -> {
            val configurable = ext.configurables(f.pkg).firstOrNull { it.id == f.id }
            var ajustes by remember { mutableStateOf(false) }
            if (ajustes && configurable != null) {
                AjustesFuenteScreen(ext, f.pkg, configurable, f.nombre) { ajustes = false }
            } else {
                NavegarFuente(
                    f, motor, onManga, onBack, if (configurable != null) ({ ajustes = true }) else null,
                    generoInicial = generoBuscar, alConsumirGenero = alConsumirGenero,
                )
            }
        }
    }
}

/** Port de searchGenre de Komikku: marca el filtro con el nombre del género. Falso si la fuente no tiene ese filtro. */
private fun marcarGenero(filtros: FilterList, genero: String): Boolean {
    for (filtro in filtros) {
        if (filtro is Filter.Group<*>) {
            for (hijo in filtro.state) {
                if (hijo is Filter<*> && hijo.name.equals(genero, true)) {
                    when (hijo) {
                        is Filter.TriState -> hijo.state = Filter.TriState.STATE_INCLUDE
                        is Filter.CheckBox -> hijo.state = true
                        else -> {}
                    }
                    return true
                }
            }
        } else if (filtro is Filter.Select<*>) {
            val i = filtro.values.filterIsInstance<String>().indexOfFirst { it.equals(genero, true) }
            if (i != -1) {
                filtro.state = i
                return true
            }
        }
    }
    return false
}

@Composable
private fun NavegarFuente(
    f: FuenteUI, motor: MotorFuentes, onManga: (MangaFuente) -> Unit, onBack: () -> Unit,
    onAjustes: (() -> Unit)? = null,
    generoInicial: String? = null, alConsumirGenero: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    val almacen = remember { com.example.lector.Almacen.obtener(ctx) }
    val ext = remember { ExtManager.obtener(ctx) }
    val enBiblioteca = remember(almacen.version) { almacen.clavesEnLinea() }
    val rejilla = rememberLazyGridState()
    var guardar by remember { mutableStateOf(false) }
    // WebView del sitio (como en Komikku): para pasar Cloudflare / captchas o iniciar sesión, y luego recargar.
    var webAbierta by rememberSaveable { mutableStateOf(false) }
    var modo by rememberSaveable { mutableStateOf(0) } // 0 populares · 1 recientes · 2 búsqueda
    var consulta by rememberSaveable { mutableStateOf("") }
    var enviada by rememberSaveable { mutableStateOf("") }
    val lista = remember { mutableStateListOf<MangaFuente>() }
    var pagina by remember { mutableStateOf(1) }
    var hay by remember { mutableStateOf(true) }
    var cargando by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    // Filtros de la fuente (género, estado, orden…): los que se editan en el panel y los que usa la búsqueda actual.
    var filtros by remember { mutableStateOf<FilterList?>(null) }
    var aplicados by remember { mutableStateOf<FilterList?>(null) }
    var panel by remember { mutableStateOf(false) }
    var recarga by remember { mutableIntStateOf(0) }
    LaunchedEffect(f.id) { runCatching { motor.filtros(f) }.onSuccess { if (filtros == null) filtros = it } }
    // Un género tocado en la ficha: se marca en los filtros (o, si la fuente no lo tiene, se busca como texto).
    LaunchedEffect(generoInicial) {
        val g = generoInicial ?: return@LaunchedEffect
        val nuevos = runCatching { motor.filtros(f) }.getOrNull()
        val coincide = nuevos != null && marcarGenero(nuevos, g)
        if (nuevos != null) filtros = nuevos
        aplicados = nuevos
        consulta = if (coincide) "" else g
        enviada = if (coincide) "" else g
        modo = 2
        recarga++
        alConsumirGenero()
    }

    // Repite una búsqueda guardada con sus filtros (los de esa fuente, puestos como estaban al guardarla).
    fun aplicarGuardada(b: ExtManager.BusquedaGuardada) {
        scope.launch {
            val nuevos = b.filtros?.let { t -> runCatching { motor.filtros(f) }.getOrNull()?.let { FiltrosTexto.aplicar(it, t) } }
            if (nuevos != null) filtros = nuevos
            aplicados = nuevos
            consulta = b.consulta; enviada = b.consulta; modo = 2; recarga++
        }
    }
    // «Más reciente»: el orden de la fuente puesto en fecha/actualización; si no tiene, su sección de recientes.
    fun masReciente() {
        scope.launch {
            val nuevos = runCatching { motor.filtros(f) }.getOrNull()
            if (nuevos != null && FiltrosTexto.ponerMasReciente(nuevos)) {
                filtros = nuevos; aplicados = nuevos; enviada = consulta.trim(); modo = 2; recarga++
            } else modo = 1
        }
    }
    // Abierta desde un chip de Novedades: se aplica esa búsqueda guardada.
    LaunchedEffect(f.id) { BusquedaPendiente.guardada?.let { BusquedaPendiente.guardada = null; aplicarGuardada(it) } }

    suspend fun pedir(p: Int): PaginaMangas = when (modo) {
        0 -> motor.populares(f, p)
        1 -> motor.recientes(f, p)
        else -> motor.buscar(f, enviada, p, aplicados)
    }

    // Al cambiar de modo, de búsqueda o de filtros se vuelve a empezar por la página 1.
    // Al volver de un manga (la pantalla se recrea) se recupera la lista ya cargada en vez de pedirla otra vez.
    val restaurada = remember {
        EstadoListaFuente.ultimo[f.id]?.takeIf { it.modo == modo && it.enviada == enviada }?.also { e ->
            lista.addAll(e.lista); pagina = e.pagina; hay = e.hay; filtros = e.filtros; aplicados = e.aplicados; recarga = e.recarga
        } != null
    }
    var saltar by remember { mutableStateOf(restaurada) }
    DisposableEffect(f.id) {
        onDispose { EstadoListaFuente.ultimo[f.id] = EstadoListaFuente(modo, enviada, recarga, lista.toList(), pagina, hay, filtros, aplicados) }
    }
    LaunchedEffect(modo, enviada, recarga) {
        if (saltar) { saltar = false; return@LaunchedEffect }
        lista.clear(); pagina = 1; hay = true; error = null; cargando = true
        try {
            val r = pedir(1)
            lista.addAll(r.mangas.distinctBy { m -> m.url }); hay = r.haySiguiente
            // «Páginas por carga» de esta fuente: se piden las siguientes seguidas.
            repeat(ext.paginasPorCarga(f.id) - 1) {
                if (!hay) return@repeat
                val s2 = pedir(pagina + 1)
                val ya = lista.mapTo(HashSet()) { v -> v.url }
                lista.addAll(s2.mangas.filter { n -> ya.add(n.url) }); hay = s2.haySiguiente; pagina += 1
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e // se cambió de pestaña o de búsqueda: no es un error
        } catch (e: Throwable) {
            error = e.message ?: "No se pudo cargar"
        } finally {
            cargando = false
        }
    }
    var hojaVista by remember { mutableStateOf(false) }

    if (webAbierta && f.baseUrl.isNotBlank()) {
        WebViewScreen(f.baseUrl, f.nombre, motor.cabeceras(f)) {
            // Al volver se repite la carga: ya con las cookies que dejó el captcha.
            webAbierta = false; error = null; recarga++
        }
        return
    }

    val acento = MaterialTheme.colorScheme.primary
    if (hojaVista) HojaVistaCatalogo(almacen, filtros != null, alFiltrosFuente = { hojaVista = false; panel = true }, ext = ext, fuenteId = f.id) { hojaVista = false }
    if (guardar) {
        var nombre by remember { mutableStateOf(enviada.ifBlank { "Filtros" }) }
        var enNovedades by remember { mutableStateOf(true) }
        DialogoAlerta(
            onDismissRequest = { guardar = false },
            title = { Text("Guardar búsqueda") },
            text = {
                Column {
                    CampoTextoShiori(nombre, { nombre = it }, label = { Text("Nombre") }, singleLine = true)
                    LText(if (aplicados != null) "Se guarda con los filtros puestos." else "Se guarda el texto buscado.", LectorType.Label, Modifier.padding(top = 8.dp), color = LectorColors.Muted)
                    FilaSwitch("Mostrar en Novedades", "Como una fila propia en Explorar → Novedades", enNovedades) { enNovedades = it }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (nombre.isNotBlank()) {
                        ext.guardarBusqueda(f.id, ExtManager.BusquedaGuardada(nombre.trim(), enviada, aplicados?.let { FiltrosTexto.guardar(it) }))
                        val k = "${f.id}\t${nombre.trim()}"
                        if (enNovedades != (k in ext.novedadesBusq)) ext.alternarNovedadBusqueda(f.id, nombre.trim())
                        Tacto.confirmar(); Avisos.mostrar(if (enNovedades) "Guardada y añadida a Novedades" else "Búsqueda guardada")
                    }
                    guardar = false
                }) { Text("Guardar") }
            },
            dismissButton = { TextButton(onClick = { guardar = false }) { Text("Cancelar") } },
        )
    }
    if (panel) filtros?.let { lista0 ->
        PanelFiltros(
            lista0, onMasReciente = { panel = false; masReciente() },
            onReset = { scope.launch { runCatching { motor.filtros(f) }.getOrNull()?.let { filtros = it } } },
            onFiltrar = { aplicados = lista0; enviada = consulta.trim(); modo = 2; recarga++ },
            onCerrar = { panel = false },
        )
    }
    val vista = almacen.vistaExplorar
    val cabeceras = remember(f.id, recarga) { motor.cabeceras(f) }
    val degradadoPortada = remember {
        androidx.compose.ui.graphics.Brush.verticalGradient(
            0f to Color.Transparent, 0.45f to Color(0x66000000), 1f to Color(0xF2000000),
        )
    }
    val enLista = vista == com.example.lector.VistaExplorar.LISTA
    // Filtro «en la biblioteca» y orden (sobre lo ya cargado; «orden de la fuente» = como lo da la fuente).
    val filtroBiblio = almacen.explorarEnBiblioteca
    val visibles = lista.filter { filtroBiblio.deja("${f.id}|${it.url}" in enBiblioteca) }.let { l ->
        when (almacen.explorarOrden) {
            1 -> l.sortedBy { it.titulo.lowercase() }.let { if (almacen.explorarDescendente) it.asReversed() else it }
            else -> if (almacen.explorarDescendente) l.asReversed() else l
        }
    }
    suspend fun cargarMas() {
        if (cargando || !hay || lista.isEmpty()) return
        cargando = true
        try {
            repeat(ext.paginasPorCarga(f.id)) {
                if (!hay) return@repeat
                val r = pedir(pagina + 1)
                // Las fuentes a veces repiten un manga (en la misma página o en la siguiente): se descarta,
                // porque dos elementos con la misma clave cierran la app.
                val ya = lista.mapTo(HashSet()) { v -> v.url }
                lista.addAll(r.mangas.filter { n -> ya.add(n.url) })
                hay = r.haySiguiente; pagina += 1
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e // la pantalla se cerró a mitad: no es un error que haya que enseñar
        } catch (e: Throwable) {
            error = e.message ?: "No se pudo cargar"
        } finally {
            cargando = false
        }
    }
    // Desplazamiento infinito: al acercarse al final se pide la siguiente página.
    val cercaDelFinal by remember {
        derivedStateOf {
            val info = rejilla.layoutInfo
            info.totalItemsCount > 0 && (info.visibleItemsInfo.lastOrNull()?.index ?: 0) >= info.totalItemsCount - 7
        }
    }
    // Una sola corrutina que escucha y carga de una en una. (Antes se reiniciaba al cambiar «cargando» y cancelaba la
    // carga a medias: de ahí el «The coroutine scope left the composition» en rojo.)
    LaunchedEffect(Unit) {
        snapshotFlow { cercaDelFinal && !cargando && hay && error == null && lista.isNotEmpty() }
            .collect { listo -> if (listo) cargarMas() }
    }

    // La página de artista de Mochy (ui/mochy/artist): banner con el icono desenfocado, «foto», nombre en grande,
    // estadísticas y la fila de píldoras; debajo, el catálogo como la estantería de álbumes, en rejilla.
    val alturaBanner = artistBannerHeight()
    val alturaBannerPx = with(LocalDensity.current) { alturaBanner.toPx() }
    val progreso = remember(rejilla, alturaBannerPx) {
        derivedStateOf {
            if (rejilla.firstVisibleItemIndex > 0) 1f
            else (rejilla.firstVisibleItemScrollOffset / alturaBannerPx).coerceIn(0f, 1f)
        }
    }
    val margen = 20.dp
    ArtistPageScaffold(
        title = f.nombre, bannerHeight = alturaBanner, scrollProgress = progreso, onBack = onBack,
        banner = { IconBackdrop { IconoExt(f.pkg, null, Modifier.fillMaxSize()) } },
        topActions = {
            // WebView del sitio, siempre a mano (como el icono de Komikku en la barra).
            if (f.baseUrl.isNotBlank()) CircleAction(Icons.Rounded.Share, "WebView", { webAbierta = true })
        },
    ) {
        LazyVerticalGrid(
            when {
                enLista -> GridCells.Fixed(1)
                almacen.explorarColumnas > 0 -> GridCells.Fixed(almacen.explorarColumnas)
                else -> GridCells.Adaptive(if (vista == com.example.lector.VistaExplorar.PORTADAS) 96.dp else 108.dp)
            },
            state = rejilla,
            contentPadding = PaddingValues(start = margen, end = margen, bottom = 40.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(if (enLista) 10.dp else 16.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "header", span = { GridItemSpan(maxLineSpan) }, contentType = "header") {
                Column(
                    Modifier.fillMaxWidth()
                        .padding(top = alturaBanner * ArtistAvatarAt)
                        .padding(start = PlaylistInset - 3.dp - margen, end = PlaylistInset - margen)
                ) {
                    ArtistAvatar { IconoExt(f.pkg, null, Modifier.fillMaxSize()) }
                    Spacer(Modifier.height(14.dp))
                    ArtistName(f.nombre)
                    Spacer(Modifier.height(4.dp))
                    val idioma = remember(f.lang) { "${bandera(f.lang)} ${nombreIdioma(f.lang)}" }
                    ArtistStats(if (lista.isNotEmpty()) "$idioma · ${lista.size} títulos" else idioma)
                    Spacer(Modifier.height(14.dp))
                    ArtistActions {
                        if (f.baseUrl.isNotBlank()) PlayPill("WebView", Icons.Rounded.Share) { webAbierta = true }
                        if (filtros != null) CircleAction(Icons.Rounded.List, "Filtros", { panel = true }, active = aplicados != null)
                        val activos = almacen.explorarEnBiblioteca != com.example.lector.Tri.IGNORAR || almacen.explorarOrden != 0 || almacen.explorarDescendente
                        CircleAction(Icons.Rounded.MoreVert, "Vista", { hojaVista = true }, active = activos)
                        if (onAjustes != null) CircleAction(Icons.Rounded.Settings, "Ajustes de la fuente", onAjustes)
                    }
                }
            }
            item(key = "chips", span = { GridItemSpan(maxLineSpan) }, contentType = "chips") {
                Column(Modifier.sangrar(margen).padding(top = 22.dp)) {
                    Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = PlaylistInset - 3.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ChipExplorar("Populares", modo == 0, Icons.Rounded.Favorite) { modo = 0 }
                        ChipExplorar("Recientes", modo == 1, Icons.Rounded.DateRange) { modo = 1 }
                        ChipExplorar("Más reciente", false, Icons.Rounded.KeyboardArrowUp) { masReciente() }
                        if (filtros != null) ChipExplorar("Filtros", aplicados != null, Icons.Rounded.List) { panel = true }
                        // Filtro / Ordenar / Mostrar del catálogo (como la hoja de la biblioteca).
                        val activos = almacen.explorarEnBiblioteca != com.example.lector.Tri.IGNORAR || almacen.explorarOrden != 0 || almacen.explorarDescendente
                        ChipExplorar("Vista", activos, Icons.Rounded.Settings) { hojaVista = true }
                    }
                    // Búsquedas guardadas (TachiyomiSY/Komikku): tocar = repetirla · mantener pulsado = borrarla.
                    val guardadas = ext.busquedas(f.id)
                    if (guardadas.isNotEmpty() || (modo == 2 && (enviada.isNotBlank() || aplicados != null))) {
                        Row(
                            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = PlaylistInset - 3.dp).padding(top = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            guardadas.forEach { b ->
                                ChipExplorar(b.nombre, enviada == b.consulta && modo == 2, Icons.Rounded.Star, onLongClick = {
                                    ext.borrarBusqueda(f.id, b.nombre); Avisos.mostrar("Búsqueda «${b.nombre}» borrada")
                                }) { aplicarGuardada(b) }
                            }
                            if (modo == 2 && (enviada.isNotBlank() || aplicados != null)) {
                                ChipExplorar("Guardar búsqueda", false, Icons.Rounded.Add) { guardar = true }
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth().padding(horizontal = PlaylistInset - 3.dp).padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) { CampoBusqueda(consulta, { consulta = it }, "Buscar en ${f.nombre}") }
                        Spacer(Modifier.width(8.dp))
                        BotonExpresivo(null, Icons.Rounded.Search, { if (consulta.isNotBlank()) { enviada = consulta.trim(); modo = 2 } }, estilo = EstiloBoton.RELLENO)
                    }
                }
            }
            error?.let {
                item(key = "error", span = { GridItemSpan(maxLineSpan) }, contentType = "card") {
                    ArtistCard(Modifier.padding(top = 14.dp), tint = LectorColors.Error) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.Warning, null, tint = LectorColors.Error, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(10.dp))
                                Text(it, style = AboutBodyStyle, color = LectorColors.Error, maxLines = 4, modifier = Modifier.weight(1f))
                            }
                            if (f.baseUrl.isNotBlank()) {
                                Spacer(Modifier.height(12.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                                    PlayPill("Abrir en WebView", Icons.Rounded.ArrowForward) { webAbierta = true }
                                    CircleAction(Icons.Rounded.Refresh, "Reintentar", { error = null; recarga++ })
                                }
                            }
                        }
                    }
                }
            }
            item(key = "shelf_title", span = { GridItemSpan(maxLineSpan) }, contentType = "shelf_title") {
                val titulo = when {
                    modo == 0 -> "Populares"
                    modo == 1 -> "Recientes"
                    enviada.isNotBlank() -> "«$enviada»"
                    else -> "Filtrado"
                }
                ArtistShelfTitle(titulo, Modifier.sangrar(margen).padding(top = 26.dp, bottom = 0.dp), count = visibles.size.takeIf { it > 0 })
            }
            // Mientras llega la primera página: esqueletos con la ola, que se revelan en las portadas reales.
            if (cargando && lista.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) { BonesRejilla(filas = 4) }
            itemsIndexed(visibles, key = { _, m -> m.url }) { i, m ->
                val esta = "${f.id}|${m.url}" in enBiblioteca
                // Mantener pulsado = añadir o quitar de la biblioteca (como en Komikku).
                val mod = Modifier.entrada(i % 12).origenTransicion().press3D().pressable(onLongClick = { alternarEnBiblioteca(ctx, almacen, f, m) }) { onManga(m) }
                if (enLista) Row(
                    mod.fillMaxWidth().padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // fila de pista de Mochy: portada pequeña con TrackShape, título y subtítulo
                    ImagenRemota(m.portadaUrl, Modifier.width(46.dp).aspectRatio(2f / 3f).clip(TrackShape), cabeceras, fuenteId = f.id)
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(m.titulo, style = TrackTitleStyle.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold), color = LectorColors.Text, maxLines = 2)
                        if (esta) {
                            Spacer(Modifier.height(6.dp))
                            EnBiblioteca()
                        }
                    }
                    Icon(Icons.Rounded.KeyboardArrowRight, null, tint = LectorColors.Muted)
                } else if (vista == com.example.lector.VistaExplorar.CUADRICULA) Column(mod) {
                    Box {
                        ImagenRemota(
                            m.portadaUrl, Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(FormaPortada).alpha(if (esta) 0.55f else 1f),
                            cabeceras, fuenteId = f.id
                        )
                        if (esta) EnBiblioteca(Modifier.align(Alignment.TopStart).padding(6.dp))
                    }
                    Spacer(Modifier.height(8.dp))
                    // tarjeta de portada de Home de Mochy: título en TrackTitleStyle
                    Text(m.titulo, style = TrackTitleStyle.copy(fontSize = 13.sp, lineHeight = 16.sp), color = LectorColors.Text, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                } else Box(mod.fillMaxWidth().aspectRatio(2f / 3f).clip(FormaPortada)) {
                    // Compacta (título sobre la portada) o solo portadas.
                    ImagenRemota(m.portadaUrl, Modifier.fillMaxSize().alpha(if (esta) 0.55f else 1f), cabeceras, fuenteId = f.id)
                    if (vista == com.example.lector.VistaExplorar.COMPACTA) {
                        Box(
                            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                                .background(degradadoPortada)
                                .padding(start = 10.dp, end = 10.dp, top = 44.dp, bottom = 10.dp),
                        ) {
                            Text(
                                m.titulo, style = TrackTitleStyle.copy(fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold),
                                color = Color.White, maxLines = 2,
                            )
                        }
                    }
                    if (esta) EnBiblioteca(Modifier.align(Alignment.TopStart).padding(6.dp))
                }
            }
            item(key = "footer", span = { GridItemSpan(maxLineSpan) }, contentType = "footer") {
                Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), Alignment.Center) {
                    when {
                        cargando && lista.isNotEmpty() -> IndicadorCarga(Modifier.size(32.dp))
                        cargando -> {}
                        hay && lista.isNotEmpty() -> PlayPill(
                            if (error != null) "Reintentar" else "Cargar más", if (error != null) Icons.Rounded.Refresh else Icons.Rounded.Add,
                        ) {
                            error = null
                            scope.launch { cargarMas() }
                        }
                        !cargando && lista.isEmpty() && error == null -> Column(
                            Modifier.padding(top = 40.dp), horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Box(Modifier.size(96.dp).clip(SuaveShape(34.dp)).background(acento.copy(alpha = 0.16f)), Alignment.Center) {
                                Icon(Icons.Rounded.Search, null, tint = acento, modifier = Modifier.size(44.dp))
                            }
                            Spacer(Modifier.height(16.dp))
                            Text("Sin resultados", style = TrackTitleStyle.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold), color = LectorColors.Text)
                            Text("Prueba con otra búsqueda o quita filtros", style = TrackSubtitleStyle, color = LectorColors.Muted)
                        }
                    }
                }
            }
        }
    }
}

/** Ensancha un elemento de ancho completo de la rejilla por encima de su margen lateral (para filas que se desplazan de borde a borde). */
private fun Modifier.sangrar(margen: androidx.compose.ui.unit.Dp): Modifier = layout { medible, restricciones ->
    val px = margen.roundToPx()
    val ancho = restricciones.maxWidth + 2 * px
    val p = medible.measure(restricciones.copy(minWidth = ancho, maxWidth = ancho))
    layout(restricciones.maxWidth, p.height) { p.place(-px, 0) }
}

/** Hoja Filtro / Ordenar / Mostrar del catálogo de una fuente (los ajustes se guardan para todas las fuentes). */
@Composable
private fun HojaVistaCatalogo(almacen: com.example.lector.Almacen, hayFiltrosFuente: Boolean, alFiltrosFuente: () -> Unit, ext: ExtManager? = null, fuenteId: Long = 0, alCerrar: () -> Unit) {
    var pestana by remember { mutableIntStateOf(0) }
    HojaLector(alCerrar) {
        PestanasPildora(listOf("Filtro", "Ordenar", "Mostrar"), pestana, { pestana = it })
        Column(Modifier.fillMaxWidth().heightIn(max = 520.dp).verticalScroll(rememberScrollState()).padding(bottom = 28.dp)) {
            when (pestana) {
                0 -> {
                    FilaTri("En la biblioteca", almacen.explorarEnBiblioteca) { almacen.explorarEnBiblioteca = it }
                    if (hayFiltrosFuente) {
                        Spacer(Modifier.height(8.dp))
                        FilaAjuste(Icons.Rounded.Search, "Filtros de la fuente", "Género, estado, orden… (los de la extensión)", alFiltrosFuente)
                    }
                }
                1 -> {
                    listOf("Orden de la fuente", "Título").forEachIndexed { i, n ->
                        FilaOrden(n, almacen.explorarOrden == i, !almacen.explorarDescendente) {
                            if (almacen.explorarOrden == i) almacen.explorarDescendente = !almacen.explorarDescendente
                            else { almacen.explorarOrden = i; almacen.explorarDescendente = false }
                        }
                    }
                    LText(
                        "Se ordena lo que ya se cargó. Para otro orden de la web usa «Filtros de la fuente».",
                        LectorType.Label, Modifier.padding(horizontal = 24.dp, vertical = 10.dp), color = LectorColors.Muted,
                    )
                }
                else -> {
                    TituloSeccion("Vista")
                    listOf(
                        com.example.lector.VistaExplorar.CUADRICULA to "Cuadrícula cómoda (título debajo)",
                        com.example.lector.VistaExplorar.COMPACTA to "Cuadrícula compacta (título sobre la portada)",
                        com.example.lector.VistaExplorar.PORTADAS to "Solo portadas",
                        com.example.lector.VistaExplorar.LISTA to "Lista",
                    ).forEach { (v, n) -> FilaRadio(n, almacen.vistaExplorar == v) { almacen.vistaExplorar = v } }
                    if (almacen.vistaExplorar != com.example.lector.VistaExplorar.LISTA) {
                        TituloSeccion("Columnas")
                        val cols = listOf(0, 2, 3, 4, 5, 6)
                        FilaChips(listOf("Auto", "2", "3", "4", "5", "6"), cols.indexOf(almacen.explorarColumnas).coerceAtLeast(0)) {
                            almacen.explorarColumnas = cols[it]
                        }
                    }
                    if (ext != null) {
                        TituloSeccion("Páginas por carga (esta fuente)")
                        val ops = listOf(1, 2, 3, 5, 10)
                        FilaChips(ops.map { "$it" }, ops.indexOf(ext.paginasPorCarga(fuenteId)).coerceAtLeast(0)) { ext.fijarPaginasPorCarga(fuenteId, ops[it]) }
                        LText("Cuántas páginas de la web se cargan de una vez. Más = más mangas por carga, pero más lento y más datos.", LectorType.Label, Modifier.padding(horizontal = 24.dp), color = LectorColors.Muted)
                    }
                }
            }
        }
    }
}


/** Lo último que mostraba la lista de cada fuente (en memoria): se recupera al volver de una ficha. */
private class EstadoListaFuente(
    val modo: Int, val enviada: String, val recarga: Int, val lista: List<MangaFuente>, val pagina: Int, val hay: Boolean,
    val filtros: FilterList?, val aplicados: FilterList?,
) {
    companion object { val ultimo = HashMap<Long, EstadoListaFuente>() }
}
