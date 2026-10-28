package com.example.lector.ext

import kotlinx.coroutines.sync.withPermit
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import eu.kanade.tachiyomi.network.JavaScriptEngine
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.system.ChildFirstPathClassLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.InjektModule
import uy.kohesive.injekt.api.InjektRegistrar
import uy.kohesive.injekt.api.addSingleton
import uy.kohesive.injekt.api.addSingletonFactory
import uy.kohesive.injekt.api.get
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Motor de fuentes real: carga el APK de una extensión (de confianza) con el source-api de Komikku
 * y pide a sus fuentes populares / recientes / búsqueda.
 *
 * Se activa solo: ExtManager asigna `Motor.actual = MotorExt(...)` al arrancar.
 */
class MotorExt(app: Application, private val ext: ExtManager) : MotorFuentes {

    private val ctx: Context = app

    init {
        com.example.lector.CacheFichas.iniciar(app)
        // Lo que las extensiones piden con Injekt.get<...>() / injectLazy<...>().
        Injekt.importModule(object : InjektModule {
            override fun InjektRegistrar.registerInjectables() {
                addSingleton(app)
                addSingletonFactory { NetworkHelper(app) }
                addSingletonFactory { JavaScriptEngine(app) }
                addSingletonFactory { Json { ignoreUnknownKeys = true; explicitNulls = false } }
            }
        })
    }

    private fun fuenteReal(f: FuenteUI): CatalogueSource {
        val e = ext.instaladas.firstOrNull { it.pkg == f.pkg }
            ?: error("La extensión de ${f.nombre} no está instalada")
        check(e.confiable) { "La extensión de ${f.nombre} no es de confianza" }
        e.errorCarga?.let { error("No se puede usar ${f.nombre}: $it") }
        val lista = CargadorExt.fuentes(ctx, e)
        val src = lista.filterIsInstance<CatalogueSource>().let { cs ->
            cs.firstOrNull { it.id == f.id }
                ?: cs.firstOrNull { it.name == f.nombre && it.lang == f.lang }
                ?: cs.singleOrNull()
        }
        return src ?: error("No se encontró la fuente ${f.nombre} dentro de la extensión")
    }

    private fun MangasPage.aPagina() = PaginaMangas(
        mangas = mangas.map { MangaFuente(it.url, it.title, it.thumbnail_url, it.memo.aTexto()) },
        haySiguiente = hasNextPage,
    )

    override suspend fun populares(fuente: FuenteUI, pagina: Int): PaginaMangas = withContext(Dispatchers.IO) {
        fuenteReal(fuente).getPopularManga(pagina).aPagina()
    }

    override suspend fun recientes(fuente: FuenteUI, pagina: Int): PaginaMangas = withContext(Dispatchers.IO) {
        val s = fuenteReal(fuente)
        if (!s.supportsLatest) error("${fuente.nombre} no tiene sección de recientes")
        s.getLatestUpdates(pagina).aPagina()
    }

    override suspend fun buscar(fuente: FuenteUI, consulta: String, pagina: Int): PaginaMangas =
        buscar(fuente, consulta, pagina, null)

    override suspend fun filtros(fuente: FuenteUI): FilterList? =
        withContext(Dispatchers.IO) { fuenteReal(fuente).getFilterList().takeIf { it.isNotEmpty() } }

    override suspend fun buscar(fuente: FuenteUI, consulta: String, pagina: Int, filtros: FilterList?): PaginaMangas =
        withContext(Dispatchers.IO) {
            fuenteReal(fuente).getSearchManga(pagina, consulta, filtros ?: FilterList()).aPagina()
        }

    // ---- Ficha, capítulos y páginas ----

    // Lista de páginas por capítulo; acotada (los últimos 24 capítulos) para que no crezca sin fin.
    private val paginasCache = object : java.util.LinkedHashMap<String, List<Page>>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<Page>>?): Boolean = size > 24
    }

    // El memo va y vuelve tal cual: si el manga no lo trae, el de su ficha guardada.
    private fun sManga(fuente: FuenteUI, m: MangaFuente) = SManga.create().apply {
        url = m.url; title = m.titulo
        (m.memo ?: com.example.lector.CacheFichas.memoManga(fuente.id, m.url))?.aMemo()?.let { memo = it }
    }

    private fun sCapitulo(c: CapituloFuente) = SChapter.create().apply {
        url = c.url; name = c.nombre; date_upload = c.fecha; chapter_number = c.numero; scanlator = c.scanlator
        c.memo?.aMemo()?.let { memo = it }
    }

    private fun kotlinx.serialization.json.JsonObject.aTexto(): String? = if (isEmpty()) null else toString()
    private fun String.aMemo(): kotlinx.serialization.json.JsonObject? =
        runCatching { kotlinx.serialization.json.Json.parseToJsonElement(this) as? kotlinx.serialization.json.JsonObject }.getOrNull()

    override suspend fun ficha(fuente: FuenteUI, manga: MangaFuente): FichaManga = withContext(Dispatchers.IO) {
        val s = fuenteReal(fuente)
        val entrada = sManga(fuente, manga)
        val r = s.getMangaUpdate(entrada, emptyList(), fetchDetails = true, fetchChapters = true)
        val m = r.manga
        FichaManga(
            detalle = DetalleManga(
                titulo = m.title.ifBlank { manga.titulo },
                autor = m.author?.takeIf { it.isNotBlank() },
                artista = m.artist?.takeIf { it.isNotBlank() },
                descripcion = m.description?.takeIf { it.isNotBlank() },
                generos = m.getGenres().orEmpty(),
                estado = m.status,
                portadaUrl = m.thumbnail_url ?: manga.portadaUrl,
            ),
            capitulos = r.chapters.map {
                CapituloFuente(it.url, it.name, it.date_upload, it.scanlator?.takeIf { x -> x.isNotBlank() }, it.chapter_number, it.memo.aTexto())
            },
            memo = m.memo.aTexto() ?: entrada.memo.aTexto(),
        ).also { com.example.lector.CacheFichas.guardar(fuente.id, manga.url, it) } // queda guardada en el móvil
    }

    private suspend fun paginasDe(fuente: FuenteUI, capitulo: CapituloFuente): List<Page> {
        val clave = "${fuente.id}|${capitulo.url}"
        synchronized(paginasCache) { paginasCache[clave] }?.let { return it }
        val lista = fuenteReal(fuente).getPageList(sCapitulo(capitulo))
        synchronized(paginasCache) { paginasCache[clave] = lista }
        return lista
    }

    override suspend fun cantidadPaginas(fuente: FuenteUI, capitulo: CapituloFuente): Int =
        withContext(Dispatchers.IO) { paginasDe(fuente, capitulo).size }

    override suspend fun imagenPagina(fuente: FuenteUI, capitulo: CapituloFuente, indice: Int): ByteArray =
        withContext(Dispatchers.IO) {
            val src = fuenteReal(fuente) as? HttpSource ?: error("${fuente.nombre} no es una fuente web")
            val pagina = conReintentos { paginasDe(fuente, capitulo) }.getOrNull(indice) ?: error("Página $indice fuera de rango")
            // Como mucho 2 peticiones a la vez a la misma web (descargas + lectura): así no salta el «429».
            semaforo(fuente.id).withPermit {
                conReintentos {
                    if (pagina.imageUrl.isNullOrEmpty()) pagina.imageUrl = src.getImageUrl(pagina)
                    ahorroDatos(pagina.imageUrl) ?: src.getImage(pagina).use { it.body.bytes() }
                }
            }
        }

    /**
     * Ahorro de datos (como el «Data saver» de Komikku con wsrv.nl): la imagen se pide recomprimida en WebP de 1080 px.
     * Si el servicio no puede traerla (webs que exigen sus cabeceras), null y se pide normal.
     */
    private fun ahorroDatos(url: String?): ByteArray? {
        if (url.isNullOrBlank() || !url.startsWith("http")) return null
        if (!ctx.getSharedPreferences("lector", Context.MODE_PRIVATE).getBoolean("ahorro_datos", false)) return null
        val cli = clienteGeneral() ?: return null
        val pedido = okhttp3.Request.Builder()
            .url("https://wsrv.nl/?url=" + java.net.URLEncoder.encode(url, "UTF-8") + "&w=1080&q=65&output=webp&we").build()
        return runCatching { cli.newCall(pedido).execute().use { r -> if (r.isSuccessful) r.body.bytes().takeIf { it.size > 512 } else null } }.getOrNull()
    }

    private val semaforos = ConcurrentHashMap<Long, kotlinx.coroutines.sync.Semaphore>()
    private fun semaforo(id: Long) = semaforos.getOrPut(id) { kotlinx.coroutines.sync.Semaphore(2) }

    /**
     * «HTTP error 429» (demasiadas peticiones) o 503: la web pide esperar. Se reintenta solo con esperas crecientes
     * (2, 4, 8, 16, 30 s, con algo de azar) en vez de dar el capítulo por fallido.
     */
    private suspend fun <T> conReintentos(bloque: suspend () -> T): T {
        var espera = 2000L
        repeat(5) {
            try { return bloque() } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) {
                val m = e.message.orEmpty()
                if (!m.contains("429") && !m.contains("503")) throw e
                kotlinx.coroutines.delay(espera + (0..800).random())
                espera = (espera * 2).coerceAtMost(30_000)
            }
        }
        return bloque()
    }

    /**
     * Descarga como Komikku: cada página se guarda en su archivo al llegar (carpeta «.partes» junto al destino), 3 a la
     * vez; una página que falla no detiene a las demás, y si alguna falla la carpeta se conserva, así al reintentar
     * solo se bajan las que faltan. Las páginas ya cargadas al leer se copian de la caché, sin red. Cada página se
     * reintenta 3 veces (2, 4 y 8 s), y más si la web responde «429». Al final se empaqueta el .cbz.
     */
    override suspend fun descargarCbz(
        fuente: FuenteUI, capitulo: CapituloFuente, destino: File, progreso: (Int, Int) -> Unit,
    ): Unit = withContext(Dispatchers.IO) {
        val total = conReintentos { paginasDe(fuente, capitulo) }.size
        require(total > 0) { "El capítulo no tiene páginas" }
        val partes = File(destino.parentFile, destino.name + ".partes").apply { mkdirs() }
        fun hecha(n: Int) = partes.listFiles()?.any { it.name.startsWith("%04d.".format(n + 1)) && !it.name.endsWith(".tmp") } == true
        val idCap = "s:${fuente.id}|${capitulo.url}"
        val hechas = java.util.concurrent.atomic.AtomicInteger((0 until total).count { hecha(it) })
        val fallos = java.util.concurrent.atomic.AtomicInteger(0)
        var ultimoError: String? = null
        progreso(hechas.get(), total)
        val cupo = kotlinx.coroutines.sync.Semaphore(3)
        coroutineScope {
            for (n in 0 until total) {
                if (hecha(n)) continue
                launch {
                    cupo.withPermit {
                        val r = runCatching {
                            com.example.lector.ui.CacheBytes.leer(idCap, n) ?: reintentar3 { imagenPagina(fuente, capitulo, n) }
                        }
                        val bytes = r.getOrNull()
                        if (bytes == null) {
                            val e = r.exceptionOrNull()
                            if (e is kotlinx.coroutines.CancellationException) throw e
                            fallos.incrementAndGet(); ultimoError = e?.message
                            return@withPermit
                        }
                        val ext = when {
                            bytes.size > 4 && bytes[0] == 0x89.toByte() && bytes[1] == 'P'.code.toByte() -> "png"
                            bytes.size > 12 && bytes[0] == 'R'.code.toByte() && bytes[8] == 'W'.code.toByte() -> "webp"
                            else -> "jpg"
                        }
                        val f = File(partes, "%04d.%s".format(n + 1, ext))
                        val tmp = File(partes, f.name + ".tmp")
                        tmp.writeBytes(bytes)
                        tmp.renameTo(f)
                        progreso(hechas.incrementAndGet(), total)
                    }
                }
            }
        }
        if (fallos.get() > 0) {
            error("Fallaron ${fallos.get()} de $total páginas (${ultimoError ?: "error"}). Al reintentar solo se bajan esas.")
        }
        val tmpZip = File(destino.parentFile, destino.name + ".tmp")
        try {
            ZipOutputStream(tmpZip.outputStream().buffered()).use { zip ->
                zip.setLevel(java.util.zip.Deflater.NO_COMPRESSION) // las imágenes ya vienen comprimidas
                partes.listFiles()?.filter { !it.name.endsWith(".tmp") }?.sortedBy { it.name }?.forEach { f ->
                    zip.putNextEntry(ZipEntry(f.name)); f.inputStream().use { it.copyTo(zip) }; zip.closeEntry()
                }
            }
            destino.delete()
            check(tmpZip.renameTo(destino)) { "No se pudo guardar el archivo" }
            partes.deleteRecursively()
        } finally {
            tmpZip.delete()
        }
        Unit
    }

    /** Como Komikku: 3 reintentos con esperas de 2, 4 y 8 s para cualquier error de red. */
    private suspend fun <T> reintentar3(bloque: suspend () -> T): T {
        repeat(3) { intento ->
            try { return bloque() } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) {
                kotlinx.coroutines.delay((2L shl intento) * 1000)
            }
        }
        return bloque()
    }

    override fun urlManga(fuente: FuenteUI, manga: MangaFuente): String? = runCatching {
        (fuenteReal(fuente) as? HttpSource)?.getMangaUrl(sManga(fuente, manga))?.takeIf { it.startsWith("http") }
    }.getOrNull()

    private val cabecerasCache = ConcurrentHashMap<Long, Map<String, String>>()

    /** Tras cambiar los ajustes de una fuente (dominio…): descarta cabeceras y listas de páginas guardadas. */
    fun olvidar() {
        cabecerasCache.clear()
        synchronized(paginasCache) { paginasCache.clear() }
    }

    override suspend fun bytesImagen(fuenteId: Long, url: String): ByteArray? = withContext(Dispatchers.IO) {
        val f = ext.fuentes.firstOrNull { it.id == fuenteId } ?: return@withContext null
        val src = runCatching { fuenteReal(f) as? HttpSource }.getOrNull() ?: return@withContext null
        val absoluta = if (url.startsWith("http")) url else src.baseUrl.trimEnd('/') + "/" + url.trimStart('/')
        val pedido = okhttp3.Request.Builder().url(absoluta).headers(src.headers).build()
        src.client.newCall(pedido).execute().use { r -> if (r.isSuccessful) r.body.bytes() else null }
    }

    override suspend fun relacionados(fuente: FuenteUI, manga: MangaFuente): List<MangaFuente> = withContext(Dispatchers.IO) {
        val s = fuenteReal(fuente)
        val res = java.util.Collections.synchronizedList(ArrayList<MangaFuente>())
        // Como Komikku: lo que dé la extensión (y su búsqueda por palabras clave), como mucho 15 s.
        kotlinx.coroutines.withTimeoutOrNull(15_000) {
            runCatching {
                s.getRelatedMangaList(sManga(fuente, manga), {}) { (_, lista), _ ->
                    lista.forEach { res += MangaFuente(it.url, it.title, it.thumbnail_url, it.memo.aTexto()) }
                }
            }
        }
        res.toList().filter { it.url != manga.url }.distinctBy { it.url }.take(20)
    }

    override fun clienteGeneral(): okhttp3.OkHttpClient? =
        runCatching { uy.kohesive.injekt.Injekt.get<NetworkHelper>().client }.getOrNull()

    private fun calcularCabeceras(fuente: FuenteUI): Map<String, String> = runCatching {
        val h = (fuenteReal(fuente) as? HttpSource)?.headers ?: return@runCatching emptyMap<String, String>()
        h.names().associateWith { h[it].orEmpty() }
    }.getOrDefault(emptyMap())

    /**
     * Versión rápida, segura para el hilo de la interfaz: solo responde si la extensión ya está cargada
     * (cargar un APK tarda cientos de ms). Si no, devuelve vacío; usa [cabecerasCargando] fuera del hilo principal.
     */
    override fun cabeceras(fuente: FuenteUI): Map<String, String> {
        cabecerasCache[fuente.id]?.let { return it }
        val e = ext.instaladas.firstOrNull { it.pkg == fuente.pkg } ?: return emptyMap()
        if (CargadorExt.yaCargada(e) == null) return emptyMap()
        return calcularCabeceras(fuente).also { if (it.isNotEmpty()) cabecerasCache[fuente.id] = it }
    }

    override suspend fun cabecerasCargando(fuente: FuenteUI): Map<String, String> {
        cabecerasCache[fuente.id]?.let { return it }
        return withContext(Dispatchers.IO) {
            calcularCabeceras(fuente).also { if (it.isNotEmpty()) cabecerasCache[fuente.id] = it }
        }
    }
}
