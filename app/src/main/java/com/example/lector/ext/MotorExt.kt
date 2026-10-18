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
import java.util.concurrent.ConcurrentHashMap

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

    override fun cabeceras(fuente: FuenteUI): Map<String, String> = runCatching {
        val h = (fuenteReal(fuente) as? HttpSource)?.headers ?: return emptyMap()
        h.names().associateWith { h[it].orEmpty() }
    }.getOrDefault(emptyMap())
}
