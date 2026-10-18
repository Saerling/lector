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

    private val paginasCache = ConcurrentHashMap<String, List<Page>>()

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
        paginasCache[clave]?.let { return it }
        val lista = fuenteReal(fuente).getPageList(sCapitulo(capitulo))
        paginasCache[clave] = lista
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
                for (i in 0 until total) {
                    val bytes = imagenPagina(fuente, capitulo, i)
                    val ext = when {
                        bytes.size > 4 && bytes[0] == 0x89.toByte() && bytes[1] == 'P'.code.toByte() -> "png"
                        bytes.size > 12 && bytes[0] == 'R'.code.toByte() && bytes[8] == 'W'.code.toByte() -> "webp"
                        else -> "jpg"
                    }
                    zip.putNextEntry(ZipEntry("%04d.%s".format(i + 1, ext)))
                    zip.write(bytes)
                    zip.closeEntry()
                    progreso(i + 1, total)
                }
            }
            destino.delete()
            check(tmp.renameTo(destino)) { "No se pudo guardar el archivo" }
        } finally {
            tmp.delete()
        }
    }

    override fun cabeceras(fuente: FuenteUI): Map<String, String> = runCatching {
        val h = (fuenteReal(fuente) as? HttpSource)?.headers ?: return emptyMap()
        h.names().associateWith { h[it].orEmpty() }
    }.getOrDefault(emptyMap())
}
