package com.example.lector.ui

import kotlinx.coroutines.async
import kotlinx.coroutines.asCoroutineDispatcher
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import com.example.lector.ui.mochy.AppTypography
import com.example.lector.ui.mochy.GlassState
import com.example.lector.ui.mochy.glassRoot
import com.example.lector.ui.mochy.rememberCollapseProgress
import com.example.lector.ui.mochy.search.*
import com.example.lector.Almacen
import com.example.lector.ext.ExtManager
import com.example.lector.ext.FuenteUI
import com.example.lector.ext.MangaFuente
import com.example.lector.ext.Motor
import com.example.lector.ext.bandera
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

internal sealed interface EstadoBusqueda {
    object Cargando : EstadoBusqueda
    data class Hecho(val mangas: List<MangaFuente>) : EstadoBusqueda
    data class Fallo(val motivo: String) : EstadoBusqueda
}

/**
 * Búsqueda global (como la de Komikku): busca el mismo texto en todas tus fuentes a la vez (o solo en las ancladas)
 * y enseña una fila de resultados por fuente. Mantener pulsada una portada la añade a la biblioteca.
 */
@Composable
fun BusquedaGlobalScreen(
    ext: ExtManager,
    almacen: Almacen,
    consultaInicial: String,
    onManga: (FuenteUI, MangaFuente) -> Unit,
    onBack: () -> Unit,
) {
    val ctx = LocalContext.current
    val motor = Motor.actual
    var consulta by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(consultaInicial, TextRange(consultaInicial.length)))
    }
    var enviada by rememberSaveable { mutableStateOf(consultaInicial) }
    var soloConResultados by remember { mutableStateOf(false) }
    val fuentes = remember(ext.fuentes, almacen.globalSoloAncladas, ext.idiomas, ext.mostrarNsfw) {
        ext.fuentes.filter {
            (ext.mostrarNsfw || !it.nsfw) && it.lang in ext.idiomas &&
                (!almacen.globalSoloAncladas || it.id.toString() in ext.fijadas)
        }.sortedWith(compareBy({ it.id.toString() !in ext.fijadas }, { it.nombre.lowercase() }))
    }
    val enBiblioteca = remember(almacen.version) { almacen.clavesEnLinea() }

    val resultados = rememberResultadosGlobales(fuentes, enviada)

    // Como OnlineSearchResult de Mochy: la lista pasa por debajo de la barra de cristal y el título grande se pliega.
    val focusManager = LocalFocusManager.current
    val listState = rememberLazyListState()
    val glassState = remember { GlassState() }
    val collapse = rememberCollapseProgress(listState)
    val progressProvider = remember(collapse) { { collapse.value } }
    val enviar: (String) -> Unit = { texto -> enviada = texto.trim(); focusManager.clearFocus() }
    val alto = searchTopBarHeight(withTitle = true, extra = 50.dp, titleBlockHeight = LibraryTitleBlockHeight)
    val abajo = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Box(Modifier.fillMaxSize().background(SearchColors.page(TemaActual.puro))) {
        Box(
            Modifier.fillMaxSize()
                // recorded while scrolling too, so the bar's blur never drops out mid-scroll
                .glassRoot(glassState, isActive = { progressProvider() > 0.01f })
        ) {
            when {
                motor == null -> MensajeVacio("El motor de fuentes no está disponible.", modifier = Modifier.padding(top = alto))
                fuentes.isEmpty() -> MensajeVacio(
                    "No hay fuentes. Instala una extensión y pulsa «Confiar» (o desactiva «Solo ancladas»).",
                    modifier = Modifier.padding(top = alto),
                )
                enviada.isBlank() -> MensajeVacio("Escribe un título y pulsa buscar.", modifier = Modifier.padding(top = alto))
                else -> LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = alto, bottom = abajo + 40.dp),
                ) {
                    val terminadas = resultados.values.count { it !is EstadoBusqueda.Cargando }
                    if (terminadas < fuentes.size) item(key = "progreso", contentType = "progreso") {
                        ProgresoGlobal(terminadas, fuentes.size)
                    }
                    items(fuentes, key = { it.id }, contentType = { "fuente" }) { f ->
                        val e = resultados[f.id] ?: EstadoBusqueda.Cargando
                        if (soloConResultados && (e !is EstadoBusqueda.Hecho || e.mangas.isEmpty())) return@items
                        FilaResultados(f, e, enBiblioteca, onManga = { m -> onManga(f, m) }) { m -> alternarEnBiblioteca(ctx, almacen, f, m) }
                    }
                }
            }
        }

        SearchGlassTopBar(
            glassState = glassState,
            progressProvider = progressProvider,
            pureBlack = TemaActual.puro,
            title = "Búsqueda global",
            subtitle = "${fuentes.size} fuentes",
            titleBlockHeight = LibraryTitleBlockHeight,
            trailing = {
                SearchAvatarButton(Icons.Rounded.Search, "Buscar", onClick = { Tacto.tic(); enviar(consulta.text) })
            },
            below = {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = SearchHorizontalPadding),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SearchCategoryChip("Con resultados", soloConResultados) { Tacto.tic(); soloConResultados = !soloConResultados }
                    SearchCategoryChip(if (almacen.globalSoloAncladas) "Solo ancladas" else "Todas las fuentes", almacen.globalSoloAncladas) {
                        Tacto.tic()
                        almacen.globalSoloAncladas = !almacen.globalSoloAncladas
                    }
                }
            },
        ) {
            SearchField(
                value = consulta,
                onValueChange = { consulta = it },
                onSearch = enviar,
                placeholder = "Buscar en todas las fuentes",
                leading = { SearchFieldGlyph(Icons.Rounded.ArrowBack, "Atrás", onBack) },
                trailing = if (consulta.text.isNotEmpty()) {
                    { SearchFieldGlyph(Icons.Rounded.Close, "Borrar") { consulta = TextFieldValue("") } }
                } else {
                    null
                },
            )
        }
    }
}

/** Progreso de la búsqueda: los tres puntos de Mochy, el recuento y una línea fina que avanza con muelle. */
@Composable
private fun ProgresoGlobal(terminadas: Int, total: Int) {
    val fr by androidx.compose.animation.core.animateFloatAsState(
        if (total == 0) 0f else terminadas.toFloat() / total,
        if (Mov.reducido) androidx.compose.animation.core.tween(100) else Muelles.espacial(), label = "progresoGlobal",
    )
    Column(Modifier.fillMaxWidth().padding(horizontal = SearchHorizontalPadding).padding(bottom = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SearchLoadingDots()
            Spacer(Modifier.width(12.dp))
            Text(
                "Buscando · $terminadas de $total fuentes",
                style = AppTypography.bodySmall.copy(fontSize = 12.sp),
                color = SearchColors.Secondary, maxLines = 1,
            )
        }
        Spacer(Modifier.height(10.dp))
        Box(Modifier.fillMaxWidth().height(2.dp).clip(CircleShape).background(SearchColors.Divider)) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(fr.coerceIn(0.02f, 1f)).clip(CircleShape).background(SearchColors.Primary))
        }
    }
}

/**
 * Busca [consulta] en todas las [fuentes] a la vez (5 en paralelo) y devuelve el estado de cada una por id. Vuelve a
 * buscar si cambia la consulta o la lista de fuentes.
 */
// Igual que Komikku (SearchScreenModel): un grupo fijo de 5 hilos solo para la búsqueda global.
private val hilosBusqueda = java.util.concurrent.Executors.newFixedThreadPool(5).asCoroutineDispatcher()

/**
 * Fuentes que estaban buscando cuando la app se cerró (p. ej. una extensión que agota la memoria): se anotan antes de
 * buscar y se quitan al terminar; lo que queda anotado al volver a abrir la app se omite en las búsquedas globales.
 */
internal object FuentesOmitidas {
    private var prefs: android.content.SharedPreferences? = null
    private val activas = java.util.concurrent.ConcurrentHashMap.newKeySet<Long>()
    var omitidas by mutableStateOf(emptySet<Long>()); private set

    fun iniciar(ctx: android.content.Context) {
        if (prefs != null) return
        val p = ctx.getSharedPreferences("busqueda_global", android.content.Context.MODE_PRIVATE).also { prefs = it }
        val antes = p.getStringSet("omitidas", null).orEmpty() + p.getStringSet("activas", null).orEmpty()
        omitidas = antes.mapNotNull { it.toLongOrNull() }.toSet()
        p.edit().putStringSet("omitidas", omitidas.map { "$it" }.toSet()).remove("activas").commit()
    }
    private fun guardarActivas() { prefs?.edit()?.putStringSet("activas", activas.map { "$it" }.toSet())?.commit() }
    fun empieza(id: Long) { activas.add(id); guardarActivas() }
    fun termina(id: Long) { activas.remove(id); guardarActivas() }
    fun restablecer() { omitidas = emptySet(); prefs?.edit()?.remove("omitidas")?.apply() }
}

private val ambitoBusqueda = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + hilosBusqueda)

@Composable
internal fun rememberResultadosGlobales(fuentes: List<FuenteUI>, consulta: String): Map<Long, EstadoBusqueda> {
    val motor = Motor.actual
    val ctx = androidx.compose.ui.platform.LocalContext.current
    remember { FuentesOmitidas.iniciar(ctx.applicationContext) }
    val resultados = remember { mutableStateMapOf<Long, EstadoBusqueda>() }
    LaunchedEffect(consulta, fuentes) {
        resultados.clear()
        if (consulta.isBlank() || motor == null) return@LaunchedEffect
        val q = consulta.trim()
        // 12 a la vez (son webs distintas), 12 s como mucho por fuente (las lentas o con Cloudflare no frenan a las
        // demás) y lo ya buscado sale al instante de la memoria.
        val limite = Semaphore(5)
        val omitidas = if (fuentes.size > 1) FuentesOmitidas.omitidas else emptySet()
        fuentes.forEach { f ->
            resultados[f.id] = CacheBusquedas.leer(f.id, q)?.let { EstadoBusqueda.Hecho(it) }
                ?: if (f.id in omitidas) EstadoBusqueda.Fallo("Omitida: cerró la app en una búsqueda anterior") else EstadoBusqueda.Cargando
        }
        fuentes.filter { resultados[it.id] is EstadoBusqueda.Cargando }.forEach { f ->
            launch {
                limite.withPermit {
                    resultados[f.id] = try {
                        // En su propio grupo de hilos: una fuente colgada no deja sin hilos al resto de la app.
                        // La búsqueda va aparte: si se cuelga, se deja de esperar a los 12 s y no retiene el turno.
                        val tarea = ambitoBusqueda.async {
                            FuentesOmitidas.empieza(f.id)
                            try { motor.buscar(f, q, 1).mangas.distinctBy { it.url }.take(20) } finally { FuentesOmitidas.termina(f.id) }
                        }
                        val r = kotlinx.coroutines.withTimeout(12_000) { tarea.await() }
                        CacheBusquedas.guardar(f.id, q, r)
                        EstadoBusqueda.Hecho(r)
                    } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                        com.example.lector.ext.FuentesEnUso.abortar(f.id) // que no siga trabajando a escondidas
                        EstadoBusqueda.Fallo("Tardó demasiado")
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Throwable) {
                        // También los Error de extensiones rotas (NoClassDefFoundError, StackOverflow…): antes cerraban la app.
                        EstadoBusqueda.Fallo(e.message ?: e.javaClass.simpleName)
                    }
                }
            }
        }
    }
    return resultados
}

/** Título reducido a letras y números en minúsculas, para comparar títulos de fuentes distintas. */
internal fun tituloComparable(t: String): String = t.lowercase().filter { it.isLetterOrDigit() }

/**
 * Una fila de la búsqueda global: nombre de la fuente y sus resultados en horizontal (como en Novedades). Con
 * [destacar], los resultados con ese mismo título van primero y llevan la insignia «Mismo título».
 */
@Composable
internal fun FilaResultados(
    f: FuenteUI,
    e: EstadoBusqueda,
    enBiblioteca: Set<String>,
    destacar: String? = null,
    onManga: (MangaFuente) -> Unit,
    onLargo: (MangaFuente) -> Unit,
) {
    val motor = Motor.actual
    val clave = destacar?.let { tituloComparable(it) }
    val cabeceras = remember(f.id) { motor?.cabeceras(f).orEmpty() }
    // Cada fuente es una sección como las de OnlineSearchResult de Mochy: regla, cabecera grande y el carrusel debajo.
    Column(Modifier.fillMaxWidth().padding(bottom = 22.dp)) {
        SearchRule()
        Spacer(Modifier.height(18.dp))
        Row(Modifier.padding(horizontal = SearchHorizontalPadding), verticalAlignment = Alignment.CenterVertically) {
            IconoExt(f.pkg, null, Modifier.size(36.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    f.nombre, style = AppTypography.titleLarge.copy(fontSize = 18.sp, fontWeight = FontWeight.Bold),
                    color = SearchColors.Primary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    bandera(f.lang), style = AppTypography.bodySmall.copy(fontSize = 12.sp),
                    color = SearchColors.Secondary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            when (e) {
                EstadoBusqueda.Cargando -> SearchLoadingDots()
                is EstadoBusqueda.Hecho -> if (e.mangas.isNotEmpty()) SearchCount(e.mangas.size)
                is EstadoBusqueda.Fallo -> Icon(Icons.Rounded.Warning, null, tint = LectorColors.Error, modifier = Modifier.size(20.dp))
            }
        }
        when (e) {
            EstadoBusqueda.Cargando -> {
                Spacer(Modifier.height(16.dp))
                BonesCarrusel(104.dp)
            }
            is EstadoBusqueda.Fallo -> Text(
                e.motivo, style = AppTypography.bodySmall.copy(fontSize = 12.sp),
                modifier = Modifier.padding(start = SearchHorizontalPadding, end = SearchHorizontalPadding, top = 10.dp),
                color = LectorColors.Error, maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            is EstadoBusqueda.Hecho -> if (e.mangas.isEmpty()) {
                Text(
                    "Sin resultados", style = AppTypography.bodySmall.copy(fontSize = 12.sp),
                    modifier = Modifier.padding(start = SearchHorizontalPadding, end = SearchHorizontalPadding, top = 8.dp),
                    color = SearchColors.Secondary,
                )
            } else {
                val lista = remember(e.mangas, clave) { if (clave == null) e.mangas else e.mangas.sortedByDescending { tituloComparable(it.titulo) == clave } }
                Spacer(Modifier.height(16.dp))
                LazyRow(contentPadding = PaddingValues(horizontal = SearchHorizontalPadding), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(lista, key = { it.url }) { m ->
                        val esta = "${f.id}|${m.url}" in enBiblioteca
                        val igual = clave != null && tituloComparable(m.titulo) == clave
                        Column(Modifier.width(112.dp).origenTransicion().press3D().pressable(onLongClick = { onLargo(m) }) { onManga(m) }) {
                            Box {
                                ImagenRemota(
                                    m.portadaUrl,
                                    Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(FormaPortada).background(SearchColors.TileHigh),
                                    cabeceras, fuenteId = f.id,
                                )
                                if (esta) EnBiblioteca(Modifier.align(Alignment.TopStart).padding(6.dp))
                                else if (igual) Insignia("Mismo título", Modifier.align(Alignment.TopStart).padding(6.dp))
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(
                                m.titulo, style = AppTypography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 17.sp, fontWeight = FontWeight.Medium),
                                color = SearchColors.Primary, maxLines = 2, overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Insignia pequeña sobre una portada. */
@Composable
internal fun Insignia(texto: String, modifier: Modifier = Modifier) {
    Box(modifier.clip(androidx.compose.foundation.shape.CircleShape).background(Color(0xE6000000)).padding(horizontal = 8.dp, vertical = 3.dp)) {
        LText(texto, LectorType.Label.copy(fontSize = 10.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold), color = Color.White, maxLines = 1)
    }
}

/** Insignia «En biblioteca» sobre una portada de Explorar: píldora de acento con un visto. */
@Composable
fun EnBiblioteca(modifier: Modifier = Modifier) {
    val acento = androidx.compose.material3.MaterialTheme.colorScheme.primary
    val tinta = com.example.lector.textoSobre(acento)
    Row(
        modifier.clip(androidx.compose.foundation.shape.CircleShape).background(acento)
            .padding(start = 5.dp, end = 8.dp, top = 3.dp, bottom = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Check, null, tint = tinta, modifier = Modifier.size(12.dp))
        Spacer(Modifier.width(3.dp))
        LText("En biblioteca", LectorType.Label.copy(fontSize = 10.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold), color = tinta, maxLines = 1)
    }
}

/** Mantener pulsada una portada en Explorar: la añade a la biblioteca (o la quita si ya estaba), como en Komikku. */
fun alternarEnBiblioteca(ctx: android.content.Context, almacen: Almacen, f: FuenteUI, m: MangaFuente) {
    val ya = almacen.comicEnLinea(f.id, m.url)
    if (ya != null) {
        Tacto.confirmar()
        almacen.eliminar(ya.id)
        Avisos.mostrar("Quitado de la biblioteca")
    } else Duplicados.comprobar(almacen, f, m) {
        Tacto.confirmar()
        almacen.anadirEnLinea(f.id, m.url, m.titulo, m.portadaUrl, null, 0, 0)
        Avisos.mostrar("Añadido a la biblioteca")
    }
}

/** Resultados de búsqueda recientes en memoria (fuente + texto), 10 minutos: repetir una búsqueda es instantáneo. */
internal object CacheBusquedas {
    private class Entrada(val cuando: Long, val mangas: List<MangaFuente>)
    private val datos = object : LinkedHashMap<String, Entrada>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entrada>?) = size > 400
    }

    fun leer(fuente: Long, q: String): List<MangaFuente>? = synchronized(datos) {
        datos["$fuente|${q.lowercase()}"]?.takeIf { System.currentTimeMillis() - it.cuando < 10 * 60_000 }?.mangas
    }

    fun guardar(fuente: Long, q: String, m: List<MangaFuente>) = synchronized(datos) {
        datos["$fuente|${q.lowercase()}"] = Entrada(System.currentTimeMillis(), m)
    }
}
