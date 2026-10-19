package com.example.lector.ext

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
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.InjektModule
import uy.kohesive.injekt.api.InjektRegistrar
import uy.kohesive.injekt.api.addSingleton
import uy.kohesive.injekt.api.addSingletonFactory
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

    // pkg:versionCode:firma -> fuentes cargadas de esa extensión
    private val cache = ConcurrentHashMap<String, List<Source>>()

    init {
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

    private fun cargarExtension(pkg: String): List<Source> {
        val pm = ctx.packageManager
        val info = pm.getPackageInfo(pkg, PackageManager.GET_META_DATA)
        val ai = info.applicationInfo ?: error("Sin información de aplicación para $pkg")
        val claseMeta = ai.metaData?.getString("tachiyomi.extension.class")
            ?: error("La extensión $pkg no declara tachiyomi.extension.class")
        val cl = ChildFirstPathClassLoader(ai.sourceDir, null, ctx.classLoader)
        return claseMeta.split(";")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { if (it.startsWith(".")) pkg + it else it }
            .flatMap { nombre ->
                when (val obj = Class.forName(nombre, false, cl).getDeclaredConstructor().newInstance()) {
                    is Source -> listOf(obj)
                    is SourceFactory -> obj.createSources()
                    else -> error("Tipo de fuente desconocido: ${obj.javaClass}")
                }
            }
    }

    private fun fuenteReal(f: FuenteUI): CatalogueSource {
        val e = ext.instaladas.firstOrNull { it.pkg == f.pkg }
            ?: error("La extensión de ${f.nombre} no está instalada")
        check(e.confiable) { "La extensión de ${f.nombre} no es de confianza" }
        val clave = "${e.pkg}:${e.versionCode}:${e.firma}"
        val lista = cache.getOrPut(clave) {
            cache.keys.filter { it.startsWith("${e.pkg}:") }.forEach { cache.remove(it) } // versiones viejas
            cargarExtension(e.pkg)
        }
        val src = lista.filterIsInstance<CatalogueSource>().let { cs ->
            cs.firstOrNull { it.id == f.id }
                ?: cs.firstOrNull { it.name == f.nombre && it.lang == f.lang }
                ?: cs.singleOrNull()
        }
        return src ?: error("No se encontró la fuente ${f.nombre} dentro de la extensión")
    }

    private fun MangasPage.aPagina() = PaginaMangas(
        mangas = mangas.map { MangaFuente(it.url, it.title, it.thumbnail_url) },
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
        withContext(Dispatchers.IO) {
            fuenteReal(fuente).getSearchManga(pagina, consulta, FilterList()).aPagina()
        }

    // ---- Ficha, capítulos y páginas ----

    // Lista de páginas por capítulo; acotada (los últimos 24 capítulos) para que no crezca sin fin.
    private val paginasCache = object : java.util.LinkedHashMap<String, List<Page>>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<Page>>?): Boolean = size > 24
    }

    private fun sManga(m: MangaFuente) = SManga.create().apply { url = m.url; title = m.titulo }

    private fun sCapitulo(c: CapituloFuente) = SChapter.create().apply {
        url = c.url; name = c.nombre; date_upload = c.fecha; chapter_number = c.numero; scanlator = c.scanlator
    }

    override suspend fun ficha(fuente: FuenteUI, manga: MangaFuente): FichaManga = withContext(Dispatchers.IO) {
        val s = fuenteReal(fuente)
        val r = s.getMangaUpdate(sManga(manga), emptyList(), fetchDetails = true, fetchChapters = true)
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
                CapituloFuente(it.url, it.name, it.date_upload, it.scanlator?.takeIf { x -> x.isNotBlank() }, it.chapter_number)
            },
        )
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
            val pagina = paginasDe(fuente, capitulo).getOrNull(indice) ?: error("Página $indice fuera de rango")
            if (pagina.imageUrl.isNullOrEmpty()) pagina.imageUrl = src.getImageUrl(pagina)
            src.getImage(pagina).use { it.body.bytes() }
        }

    override suspend fun descargarCbz(
        fuente: FuenteUI, capitulo: CapituloFuente, destino: File, progreso: (Int, Int) -> Unit,
    ) = withContext(Dispatchers.IO) {
        val total = paginasDe(fuente, capitulo).size
        require(total > 0) { "El capítulo no tiene páginas" }
        val tmp = File(destino.parentFile, destino.name + ".tmp")
        try {
            ZipOutputStream(tmp.outputStream().buffered()).use { zip ->
                zip.setLevel(java.util.zip.Deflater.NO_COMPRESSION) // las imágenes ya vienen comprimidas
                var i = 0
                while (i < total) {
                    ensureActive() // permite cancelar la descarga entre grupos de páginas
                    val grupo = (i until minOf(i + 3, total)).toList()
                    // Se piden 3 páginas a la vez (antes era una por una) y se escriben en orden.
                    val datos = coroutineScope { grupo.map { n -> async { imagenPagina(fuente, capitulo, n) } }.awaitAll() }
                    for ((k, bytes) in datos.withIndex()) {
                        val ext = when {
                            bytes.size > 4 && bytes[0] == 0x89.toByte() && bytes[1] == 'P'.code.toByte() -> "png"
                            bytes.size > 12 && bytes[0] == 'R'.code.toByte() && bytes[8] == 'W'.code.toByte() -> "webp"
                            else -> "jpg"
                        }
                        zip.putNextEntry(ZipEntry("%04d.%s".format(grupo[k] + 1, ext)))
                        zip.write(bytes)
                        zip.closeEntry()
                        progreso(grupo[k] + 1, total)
                    }
                    i += grupo.size
                }
            }
            destino.delete()
            check(tmp.renameTo(destino)) { "No se pudo guardar el archivo" }
        } finally {
            tmp.delete()
        }
    }

    override fun urlManga(fuente: FuenteUI, manga: MangaFuente): String? = runCatching {
        (fuenteReal(fuente) as? HttpSource)?.getMangaUrl(sManga(manga))?.takeIf { it.startsWith("http") }
    }.getOrNull()

    private val cabecerasCache = ConcurrentHashMap<Long, Map<String, String>>()

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
        if (!cache.containsKey("${e.pkg}:${e.versionCode}:${e.firma}")) return emptyMap()
        return calcularCabeceras(fuente).also { if (it.isNotEmpty()) cabecerasCache[fuente.id] = it }
    }

    override suspend fun cabecerasCargando(fuente: FuenteUI): Map<String, String> {
        cabecerasCache[fuente.id]?.let { return it }
        return withContext(Dispatchers.IO) {
            calcularCabeceras(fuente).also { if (it.isNotEmpty()) cabecerasCache[fuente.id] = it }
        }
    }
}
