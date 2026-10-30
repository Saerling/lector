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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    var consulta by rememberSaveable { mutableStateOf(consultaInicial) }
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

    Column(Modifier.fillMaxSize().background(LectorColors.Bg).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            BotonRedondoTema(Icons.Rounded.ArrowBack, "Atrás", onBack)
            Spacer(Modifier.width(12.dp))
            Box(Modifier.weight(1f)) { CampoBusqueda(consulta, { consulta = it }, "Buscar en todas las fuentes") }
            Spacer(Modifier.width(8.dp))
            BotonRedondoTema(Icons.Rounded.Search, "Buscar", { enviada = consulta.trim() })
        }
        Row(Modifier.padding(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChipSel("Con resultados", soloConResultados) { soloConResultados = !soloConResultados }
            ChipSel(if (almacen.globalSoloAncladas) "Solo ancladas" else "Todas las fuentes", almacen.globalSoloAncladas) {
                almacen.globalSoloAncladas = !almacen.globalSoloAncladas
            }
        }
        val terminadas = resultados.values.count { it !is EstadoBusqueda.Cargando }
        if (enviada.isNotBlank() && terminadas < fuentes.size) {
            LText("Buscando… $terminadas de ${fuentes.size}", LectorType.Label, Modifier.padding(horizontal = 24.dp, vertical = 6.dp), color = LectorColors.Muted)
        }
        when {
            motor == null -> MensajeVacio("El motor de fuentes no está disponible.")
            fuentes.isEmpty() -> MensajeVacio("No hay fuentes. Instala una extensión y pulsa «Confiar» (o desactiva «Solo ancladas»).")
            enviada.isBlank() -> MensajeVacio("Escribe un título y pulsa buscar.")
            else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 40.dp)) {
                items(fuentes, key = { it.id }) { f ->
                    val e = resultados[f.id] ?: EstadoBusqueda.Cargando
                    if (soloConResultados && (e !is EstadoBusqueda.Hecho || e.mangas.isEmpty())) return@items
                    FilaResultados(f, e, enBiblioteca, onManga = { m -> onManga(f, m) }) { m -> alternarEnBiblioteca(ctx, almacen, f, m) }
                }
            }
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
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(Modifier.padding(horizontal = 24.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            LText(f.nombre, LectorType.Section.copy(fontSize = 17.sp), Modifier.weight(1f), maxLines = 1)
            LText(bandera(f.lang), LectorType.Body)
        }
        when (e) {
            EstadoBusqueda.Cargando -> Box(Modifier.fillMaxWidth().height(60.dp), Alignment.Center) { CircularProgressIndicator(Modifier.size(22.dp)) }
            is EstadoBusqueda.Fallo -> LText(e.motivo, LectorType.Label, Modifier.padding(horizontal = 24.dp), color = LectorColors.Error, maxLines = 2)
            is EstadoBusqueda.Hecho -> if (e.mangas.isEmpty()) {
                LText("Sin resultados", LectorType.Label, Modifier.padding(horizontal = 24.dp), color = LectorColors.Muted)
            } else {
                val lista = if (clave == null) e.mangas else e.mangas.sortedByDescending { tituloComparable(it.titulo) == clave }
                LazyRow(contentPadding = PaddingValues(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(lista, key = { it.url }) { m ->
                        val esta = "${f.id}|${m.url}" in enBiblioteca
                        val igual = clave != null && tituloComparable(m.titulo) == clave
                        Column(Modifier.width(112.dp).press3D().pressable(onLongClick = { onLargo(m) }) { onManga(m) }) {
                            Box {
                                ImagenRemota(m.portadaUrl, Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(SuaveShape(12.dp)), motor?.cabeceras(f).orEmpty(), fuenteId = f.id)
                                if (esta) EnBiblioteca(Modifier.align(Alignment.TopStart).padding(6.dp))
                                else if (igual) Insignia("Mismo título", Modifier.align(Alignment.TopStart).padding(6.dp))
                            }
                            Spacer(Modifier.height(6.dp))
                            LText(m.titulo, LectorType.Label, maxLines = 2)
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
    Box(modifier.clip(SuaveShape(8.dp)).background(LectorColors.Text).padding(horizontal = 6.dp, vertical = 2.dp)) {
        LText(texto, LectorType.Label.copy(fontSize = 10.sp), color = LectorColors.Bg, maxLines = 1)
    }
}

/** Insignia «En biblioteca» sobre una portada de Explorar. */
@Composable
fun EnBiblioteca(modifier: Modifier = Modifier) {
    Box(
        modifier.clip(SuaveShape(8.dp)).background(androidx.compose.material3.MaterialTheme.colorScheme.primary)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        LText("En biblioteca", LectorType.Label.copy(fontSize = 10.sp), color = com.example.lector.textoSobre(androidx.compose.material3.MaterialTheme.colorScheme.primary), maxLines = 1)
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
