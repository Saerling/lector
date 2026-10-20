package com.example.lector

import android.content.Context
import com.example.lector.TachibkFormato.ANILIST
import com.example.lector.ext.CapituloFuente
import com.example.lector.ext.ExtManager
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
 */
object Tachibk {

    fun nombreArchivo(): String =
        "lector_" + SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.US).format(Date()) + ".tachibk"

    fun esTachibk(bytes: ByteArray): Boolean = TachibkFormato.esTachibk(bytes)

    fun descomprimirSiHace(bytes: ByteArray): ByteArray = TachibkFormato.descomprimirSiHace(bytes)

    // ───────────────────────────── Exportar ─────────────────────────────

    fun crear(ctx: Context): ByteArray {
        val almacen = Almacen.obtener(ctx)
        val ext = ExtManager.obtener(ctx)
        val comics = almacen.cargar().filter { it.enLinea && it.urlManga != null }
        val cats = almacen.categorias()
        val asign = almacen.asignaciones()
        val historial = almacen.historialLinea

        // Solo los capítulos con algo que guardar (leído, marcado o página): la lista completa la pide la otra app.
        fun capitulos(fuente: Long, urls: Collection<String>, nombres: Map<String, String>) = urls.mapNotNull { u ->
            val leido = ext.esLeido(fuente, u)
            val marcado = ext.esMarcado(fuente, u)
            val pagina = ext.paginaDe(fuente, u)
            if (!leido && !marcado && pagina <= 0) null
            else TachibkFormato.Capitulo(u, nombres[u].orEmpty(), leido = leido, marcado = marcado, pagina = pagina.toLong())
        }

        val mangas = comics.map { c ->
            val url = c.urlManga!!
            val hist = historial.filter { it.fuenteId == c.fuenteId && it.urlManga == url }
            TachibkFormato.Manga(
                fuente = c.fuenteId, url = url, titulo = c.titulo, autor = c.autor, descripcion = c.descripcion,
                generos = c.generos, estado = c.estado, portada = c.portadaUrl, anadido = c.anadido,
                capitulos = capitulos(c.fuenteId, almacen.capitulosConocidos(c.fuenteId, url) + hist.map { it.capUrl }, hist.associate { it.capUrl to it.capNombre }),
                // Mihon referencia las categorías por su «order».
                categorias = asign[c.id].orEmpty().mapNotNull { id -> cats.indexOfFirst { it.id == id }.takeIf { it >= 0 }?.toLong() },
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
        val ids = (comics.map { it.fuenteId } + historial.map { it.fuenteId }).toSet()
        return TachibkFormato.codificar(
            TachibkFormato.Copia(
                mangas = mangas + soloHistorial,
                categorias = cats.mapIndexed { i, c -> TachibkFormato.Categoria(c.nombre, i.toLong(), c.oculta) },
                fuentes = ids.map { id -> TachibkFormato.Fuente(ext.fuentes.firstOrNull { it.id == id }?.nombre ?: id.toString(), id) },
            )
        )
    }

    // ───────────────────────────── Importar ─────────────────────────────

    data class Resultado(val mangas: Int, val categorias: Int, val historial: Int, val listados: Int)

    fun restaurar(ctx: Context, bytes: ByteArray): Resultado {
        val almacen = Almacen.obtener(ctx)
        val ext = ExtManager.obtener(ctx)
        val copia = TachibkFormato.decodificar(bytes)

        // Categorías: «order» de la copia → id en Lector (se crean las que falten, por nombre).
        var catsNuevas = 0
        val porOrden = HashMap<Long, String>()
        copia.categorias.forEach { c ->
            val existente = almacen.categorias().firstOrNull { it.nombre.equals(c.nombre, ignoreCase = true) }
            val id = existente?.id ?: almacen.crearCategoria(c.nombre)?.also { catsNuevas++ }
            if (id != null) {
                porOrden[c.orden] = id
                if (existente == null && c.oculta) almacen.alternarOculta(id)
            }
        }

        var mangas = 0
        val historialNuevo = ArrayList<EntradaHistorial>()
        copia.mangas.forEach { m ->
            val caps = m.capitulos
            caps.filter { it.leido }.map { it.url }.takeIf { it.isNotEmpty() }?.let { ext.marcarLeidos(m.fuente, it) }
            caps.filter { it.marcado }.map { it.url }.takeIf { it.isNotEmpty() }?.let { ext.marcarVarios(m.fuente, it, true) }
            caps.filter { !it.leido && it.pagina > 0 }.forEach { ext.guardarPagina(m.fuente, it.url, it.pagina.toInt()) }
            val titulo = m.titulo.ifBlank { m.url }

            if (m.favorito) {
                val comic = almacen.anadirEnLinea(
                    m.fuente, m.url, titulo, m.portada, null, caps.size, caps.count { it.leido },
                    autor = m.autor, estado = m.estado, generos = m.generos, descripcion = m.descripcion,
                )
                mangas++
                if (m.anadido > 0) almacen.actualizarComic(comic.id) { it.copy(anadido = m.anadido) }
                // Los capítulos de la copia cuentan como «ya conocidos»: no salen como novedad al actualizar.
                if (caps.isNotEmpty()) almacen.registrarCapitulos(
                    m.fuente, m.url, titulo, m.portada,
                    caps.map { CapituloFuente(it.url, it.nombre, it.fechaSubida, it.scanlator, it.numero) },
                )
                val ids = m.categorias.mapNotNull { porOrden[it] }.toSet()
                if (ids.isNotEmpty()) almacen.asignarCategorias(comic.id, almacen.asignaciones()[comic.id].orEmpty() + ids)
                if (m.tituloPropio != null || m.autorPropio != null || m.descripcionPropia != null || m.generosPropios.isNotEmpty() || m.estadoPropio != 0) {
                    almacen.editarInfo(
                        comic.id, m.tituloPropio ?: titulo, m.autorPropio ?: m.autor, m.descripcionPropia ?: m.descripcion,
                        m.generosPropios.ifEmpty { m.generos }, if (m.estadoPropio != 0) m.estadoPropio else m.estado,
                    )
                }
                m.seguimiento.firstOrNull { it.servicio == ANILIST && it.mediaId > 0 }?.let { t ->
                    if (almacen.seguimientoDe(comic.id) == null) {
                        almacen.guardarSeguimiento(comic.id, Seguimiento(t.mediaId.toInt(), t.titulo.ifBlank { titulo }, false, t.ultimoCapitulo.toInt()))
                    }
                }
            }
            // Historial: la lectura más reciente de este manga.
            m.historial.maxByOrNull { it.ultimaLectura }?.takeIf { it.ultimaLectura > 0 }?.let { h ->
                val cap = caps.firstOrNull { it.url == h.url }
                historialNuevo += EntradaHistorial(
                    m.fuente, m.url, titulo, m.portada, h.url, cap?.nombre.orEmpty(), (cap?.pagina ?: 0L).toInt(), 0, h.ultimaLectura,
                )
            }
        }
        // Del más viejo al más nuevo: así el último leído queda arriba.
        historialNuevo.sortedBy { it.leido }.forEach { almacen.registrarHistorialLinea(it) }

        copia.listados.forEach { ext.anadirRepo(it) { } }
        return Resultado(mangas, catsNuevas, historialNuevo.size, copia.listados.size)
    }
}
