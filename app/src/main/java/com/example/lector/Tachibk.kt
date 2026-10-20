package com.example.lector

import android.content.Context
import com.example.lector.TachibkFormato.ANILIST
import com.example.lector.ext.ExtManager
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Copias de seguridad compatibles con Mihon, Komikku, TachiyomiSY, J2K y demás forks de Tachiyomi (.tachibk).
 * El formato está en [TachibkFormato]; aquí se pasa de la biblioteca de Lector a ese formato y al revés.
 *
 * Qué se pasa en cada sentido:
 *  - Mangas de fuentes (fuente + dirección): título, autor, descripción, géneros, estado, portada, fecha de añadido,
 *    categorías, capítulos leídos / marcados / página por la que vas, historial y el vínculo con AniList.
 *  - Los datos editados a mano (Komikku/SY «custom…») se importan como «Editar información» y se exportan igual.
 *  - Lo que solo está en el historial (no en la biblioteca) viaja como «no favorito», como hace Mihon.
 *  - Los cómics locales (.cbz de tu móvil) NO van en una .tachibk: en esas apps son archivos de otra carpeta.
 *  - Al importar también se añaden los listados de extensiones que traiga la copia.
 *
 * Rapidez: todo se calcula en memoria y se guarda en lote (una escritura por tipo de dato), no manga a manga.
 */
object Tachibk {

    fun nombreArchivo(): String =
        "lector_" + SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.US).format(Date()) + ".tachibk"

    fun esTachibk(bytes: ByteArray): Boolean = TachibkFormato.esTachibk(bytes)

    fun descomprimirSiHace(bytes: ByteArray): ByteArray = TachibkFormato.descomprimirSiHace(bytes)

    // ───────────────────────────── Exportar ─────────────────────────────

    /** Crea la copia .tachibk y devuelve los bytes y el informe. */
    fun crear(ctx: Context, avance: TareaCopia.Avance? = null): Pair<ByteArray, TareaCopia.Informe> {
        val almacen = Almacen.obtener(ctx)
        val ext = ExtManager.obtener(ctx)
        val todos = almacen.cargar()
        val comics = todos.filter { it.enLinea && it.urlManga != null }
        val locales = todos.count { !it.enLinea }
        val cats = almacen.categorias()
        val asign = almacen.asignaciones()
        val historial = almacen.historialLinea
        val histPorManga = historial.groupBy { it.fuenteId to it.urlManga }
        val ordenCat = cats.withIndex().associate { (i, c) -> c.id to i.toLong() }
        var capsConEstado = 0

        // Solo los capítulos con algo que guardar (leído, marcado o página): la lista completa la pide la otra app.
        fun capitulos(fuente: Long, urls: Collection<String>, nombres: Map<String, String>) = urls.mapNotNull { u ->
            val leido = ext.esLeido(fuente, u)
            val marcado = ext.esMarcado(fuente, u)
            val pagina = ext.paginaDe(fuente, u)
            if (!leido && !marcado && pagina <= 0) null
            else TachibkFormato.Capitulo(u, nombres[u].orEmpty(), leido = leido, marcado = marcado, pagina = pagina.toLong())
        }.also { capsConEstado += it.size }

        val mangas = comics.mapIndexed { i, c ->
            if (i % 20 == 0) avance?.paso(0.05f + 0.75f * i / comics.size.coerceAtLeast(1), "Mangas: $i de ${comics.size}")
            val url = c.urlManga!!
            val hist = histPorManga[c.fuenteId to url].orEmpty()
            TachibkFormato.Manga(
                fuente = c.fuenteId, url = url, titulo = c.titulo, autor = c.autor, descripcion = c.descripcion,
                generos = c.generos, estado = c.estado, portada = c.portadaUrl, anadido = c.anadido,
                capitulos = capitulos(c.fuenteId, almacen.capitulosConocidos(c.fuenteId, url) + hist.map { it.capUrl }, hist.associate { it.capUrl to it.capNombre }),
                // Mihon referencia las categorías por su «order».
                categorias = asign[c.id].orEmpty().mapNotNull { ordenCat[it] },
                seguimiento = listOfNotNull(almacen.seguimientoDe(c.id)?.let { s ->
                    TachibkFormato.Seguimiento(ANILIST, s.mediaId.toLong(), s.titulo, s.numero.toFloat())
                }),
                favorito = true,
                historial = hist.map { TachibkFormato.Historial(it.capUrl, it.leido) },
                tituloPropio = c.titulo.takeIf { c.editado },
                autorPropio = c.autor.takeIf { c.editado },
                descripcionPropia = c.descripcion.takeIf { c.editado },
                generosPropios = if (c.editado) c.generos else emptyList(),
            )
        }
        val enBiblioteca = comics.map { it.fuenteId to it.urlManga }.toSet()
        val soloHistorial = historial.filter { (it.fuenteId to it.urlManga) !in enBiblioteca }.map { h ->
            TachibkFormato.Manga(
                fuente = h.fuenteId, url = h.urlManga, titulo = h.titulo, portada = h.portadaUrl,
                capitulos = capitulos(h.fuenteId, listOf(h.capUrl), mapOf(h.capUrl to h.capNombre)),
                favorito = false,
                historial = listOf(TachibkFormato.Historial(h.capUrl, h.leido)),
            )
        }
        avance?.paso(0.85f, "Comprimiendo…")
        val ids = (comics.map { it.fuenteId } + historial.map { it.fuenteId }).toSet()
        val fuentesPorId = ext.fuentes.associateBy { it.id }
        val bytes = TachibkFormato.codificar(
            TachibkFormato.Copia(
                mangas = mangas + soloHistorial,
                categorias = cats.mapIndexed { i, c -> TachibkFormato.Categoria(c.nombre, i.toLong(), c.oculta) },
                fuentes = ids.map { id -> TachibkFormato.Fuente(fuentesPorId[id]?.nombre ?: id.toString(), id) },
            )
        )
        val informe = TareaCopia.Informe(
            titulo = "Copia para Mihon / Komikku",
            bien = listOfNotNull(
                "${comics.size} mangas de la biblioteca",
                "$capsConEstado capítulos con progreso (leídos, marcados o a medias)".takeIf { capsConEstado > 0 },
                "${cats.size} categorías".takeIf { cats.isNotEmpty() },
                "${historial.size} entradas de historial".takeIf { historial.isNotEmpty() },
                "Tamaño: ${bytes.size / 1024 + 1} KB",
            ),
            avisos = listOfNotNull(
                "$locales cómics locales (.cbz/.pdf) no se incluyen: ese formato solo guarda mangas de fuentes".takeIf { locales > 0 },
            ),
        )
        return bytes to informe
    }

    // ───────────────────────────── Importar ─────────────────────────────

    suspend fun restaurar(ctx: Context, bytes: ByteArray, avance: TareaCopia.Avance? = null): TareaCopia.Informe {
        val almacen = Almacen.obtener(ctx)
        val ext = ExtManager.obtener(ctx)
        avance?.paso(0.03f, "Leyendo la copia…")
        // Primera pasada (rápida): categorías, fuentes y listados. Los mangas se leen después, de uno en uno.
        val cabecera = TachibkFormato.leerCabecera(bytes)
        val errores = ArrayList<String>()

        // Categorías: «order» de la copia → id en Lector (se crean las que falten, por nombre).
        var catsNuevas = 0
        val porOrden = HashMap<Long, String>()
        cabecera.categorias.forEach { c ->
            runCatching {
                val existente = almacen.categorias().firstOrNull { it.nombre.equals(c.nombre, ignoreCase = true) }
                val id = existente?.id ?: almacen.crearCategoria(c.nombre)?.also { catsNuevas++ }
                if (id != null) {
                    porOrden[c.orden] = id
                    if (existente == null && c.oculta) almacen.alternarOculta(id)
                }
            }.onFailure { errores += "Categoría «${c.nombre}»: ${it.message}" }
        }

        // Segunda pasada: los mangas de uno en uno; solo se guarda en memoria lo que hay que restaurar
        // (capítulos leídos / marcados / a medias), no la lista completa de capítulos de cada manga.
        // Los capítulos «conocidos» no se guardan: la primera actualización de cada manga los toma como base.
        // Leídos como huellas de 64 bits (no textos): cientos de miles de capítulos caben en pocos MB.
        val leidos = ConjuntoLargo(4096)
        val marcados = HashSet<String>()
        val paginas = HashMap<String, Int>()
        val nuevos = ArrayList<Almacen.NuevoEnLinea>()
        val historialNuevo = ArrayList<EntradaHistorial>()
        // De los que traen categorías, AniList o datos editados se guarda solo eso (no sus capítulos).
        class Extra(
            val fuente: Long, val url: String, val autor: String?, val descripcion: String?, val generos: List<String>, val estado: Int,
            val categorias: List<Long>, val seguimiento: List<TachibkFormato.Seguimiento>,
            val tituloPropio: String?, val autorPropio: String?, val descripcionPropia: String?, val generosPropios: List<String>, val estadoPropio: Int,
        )
        val extras = ArrayList<Extra>()
        var total = 0
        var favoritos = 0
        val porFuente = HashMap<Long, Int>()
        TachibkFormato.recorrerMangas(bytes) { m ->
            total++
            if (total % 25 == 0) avance?.paso(0.05f + 0.6f * (total.toFloat() / (total + 50)).coerceAtMost(0.99f), "Mangas leídos: $total")
            runCatching {
                val titulo = m.titulo.ifBlank { m.url }
                m.conEstado.forEach { c ->
                    if (c.leido) leidos.add(ConjuntoLargo.clave(m.fuente, c.url))
                    if (c.marcado) marcados += "${m.fuente}|${c.url}"
                    if (!c.leido && c.pagina > 0) paginas["${m.fuente}|${c.url}"] = c.pagina.toInt()
                }
                if (m.favorito) {
                    favoritos++
                    porFuente[m.fuente] = (porFuente[m.fuente] ?: 0) + 1
                    nuevos += Almacen.NuevoEnLinea(
                        m.fuente, m.url, titulo, m.portada, m.totalCapitulos, m.conEstado.count { it.leido },
                        m.autor, m.estado, m.generos, m.descripcion, m.anadido,
                    )
                    if (m.categorias.isNotEmpty() || m.seguimiento.isNotEmpty() || m.tituloPropio != null || m.autorPropio != null ||
                        m.descripcionPropia != null || m.generosPropios.isNotEmpty() || m.estadoPropio != 0
                    ) extras += Extra(
                        m.fuente, m.url, m.autor, m.descripcion, m.generos, m.estado, m.categorias, m.seguimiento,
                        m.tituloPropio, m.autorPropio, m.descripcionPropia, m.generosPropios, m.estadoPropio,
                    )
                }
                m.ultimaLectura?.takeIf { it.ultimaLectura > 0 }?.let { h ->
                    val cap = m.conEstado.firstOrNull { it.url == h.url }
                    historialNuevo += EntradaHistorial(
                        m.fuente, m.url, titulo, m.portada, h.url, cap?.nombre.orEmpty(), (cap?.pagina ?: 0L).toInt(), 0, h.ultimaLectura,
                    )
                }
            }.onFailure { errores += "«${m.titulo.ifBlank { m.url }}»: ${it.message ?: it.javaClass.simpleName}" }
        }

        // Una escritura por tipo de dato.
        avance?.paso(0.7f, "Guardando la biblioteca…")
        val (porClave, anadidos) = almacen.anadirVariosEnLinea(nuevos)
        nuevos.clear()
        avance?.paso(0.78f, "Guardando capítulos leídos…")
        ext.importarEstados(leidos, marcados, paginas)
        avance?.paso(0.84f, "Guardando categorías e historial…")
        val categoriasDe = HashMap<String, Set<String>>()
        var anilist = 0
        var editados = 0
        extras.forEach { m ->
            val comic = porClave["${m.fuente}|${m.url}"] ?: return@forEach
            runCatching {
                val ids = m.categorias.mapNotNull { porOrden[it] }.toSet()
                if (ids.isNotEmpty()) categoriasDe[comic.id] = ids
                if (m.tituloPropio != null || m.autorPropio != null || m.descripcionPropia != null || m.generosPropios.isNotEmpty() || m.estadoPropio != 0) {
                    almacen.editarInfo(
                        comic.id, m.tituloPropio ?: comic.titulo, m.autorPropio ?: m.autor, m.descripcionPropia ?: m.descripcion,
                        m.generosPropios.ifEmpty { m.generos }, if (m.estadoPropio != 0) m.estadoPropio else m.estado,
                    )
                    editados++
                }
                m.seguimiento.firstOrNull { it.servicio == ANILIST && it.mediaId > 0 }?.let { t ->
                    if (almacen.seguimientoDe(comic.id) == null) {
                        almacen.guardarSeguimiento(comic.id, Seguimiento(t.mediaId.toInt(), t.titulo.ifBlank { comic.titulo }, false, t.ultimoCapitulo.toInt()))
                        anilist++
                    }
                }
            }.onFailure { errores += "«${comic.titulo}»: ${it.message ?: it.javaClass.simpleName}" }
        }
        almacen.anadirCategoriasVarios(categoriasDe)
        almacen.anadirHistorialVarios(historialNuevo)

        // Listados de extensiones (se comprueban por internet: se espera un poco a cada uno).
        var listadosOk = 0
        cabecera.listados.forEachIndexed { i, url ->
            avance?.paso(0.9f + 0.09f * i / cabecera.listados.size, "Añadiendo listados de extensiones…")
            val listo = CompletableDeferred<String?>()
            ext.anadirRepo(url) { listo.complete(it) }
            val r = withTimeoutOrNull(20_000) { listo.await() }
            when {
                r == null && listo.isCompleted -> listadosOk++
                r == "Ese listado ya está añadido" -> Unit
                listo.isCompleted -> errores += "Listado $url: $r"
                else -> errores += "Listado $url: no respondió a tiempo"
            }
        }
        avance?.paso(1f, "Listo")

        // Fuentes que faltan por instalar.
        val instaladas = ext.fuentes.map { it.id }.toSet()
        val nombres = cabecera.fuentes.associate { it.id to it.nombre }
        val faltan = porFuente.filterKeys { it !in instaladas }.entries.sortedByDescending { it.value }
            .map { (id, n) -> "${nombres[id]?.takeIf { it.isNotBlank() } ?: "Fuente $id"} ($n)" }

        return TareaCopia.Informe(
            titulo = "Restaurar copia de Mihon / Komikku",
            bien = listOfNotNull(
                "$anadidos mangas añadidos a la biblioteca" + if (favoritos > anadidos) " (${favoritos - anadidos} ya estaban)" else "",
                "${leidos.size} capítulos leídos".takeIf { leidos.size > 0 },
                "${marcados.size} capítulos marcados".takeIf { marcados.isNotEmpty() },
                "${paginas.size} capítulos a medias (con su página)".takeIf { paginas.isNotEmpty() },
                "$catsNuevas categorías nuevas".takeIf { catsNuevas > 0 },
                "${historialNuevo.size} entradas de historial".takeIf { historialNuevo.isNotEmpty() },
                "$anilist vínculos con AniList".takeIf { anilist > 0 },
                "$editados mangas con información editada".takeIf { editados > 0 },
                "$listadosOk listados de extensiones".takeIf { listadosOk > 0 },
            ),
            avisos = listOfNotNull(
                ("Instala las extensiones de estas fuentes para leer sus mangas (Explorar → Extensiones): " +
                    faltan.take(12).joinToString(", ") + if (faltan.size > 12) "… y ${faltan.size - 12} más" else "").takeIf { faltan.isNotEmpty() },
            ),
            errores = errores.take(50) + listOfNotNull("…y ${errores.size - 50} errores más".takeIf { errores.size > 50 }),
        )
    }
}
