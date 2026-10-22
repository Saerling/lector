package com.example.lector

import com.example.lector.Proto.bool
import com.example.lector.Proto.float
import com.example.lector.Proto.int
import com.example.lector.Proto.long
import com.example.lector.Proto.longs
import com.example.lector.Proto.mensajes
import com.example.lector.Proto.texto
import com.example.lector.Proto.textos
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * El formato .tachibk (Mihon / Komikku / TachiyomiSY / J2K) sin nada de Android: gzip + protobuf con los mismos
 * números de campo que sus clases `Backup*`. Se prueba en el ordenador con la librería que usan esas apps
 * (kotlinx-serialization-protobuf) para asegurar que se entienden en los dos sentidos.
 */
object TachibkFormato {

    data class Copia(
        val mangas: List<Manga> = emptyList(),
        val categorias: List<Categoria> = emptyList(),
        val fuentes: List<Fuente> = emptyList(),
        val listados: List<String> = emptyList(),
    )

    data class Manga(
        val fuente: Long,
        val url: String,
        val titulo: String = "",
        val artista: String? = null,
        val autor: String? = null,
        val descripcion: String? = null,
        val generos: List<String> = emptyList(),
        val estado: Int = 0,
        val portada: String? = null,
        val anadido: Long = 0,
        val capitulos: List<Capitulo> = emptyList(),
        val categorias: List<Long> = emptyList(),
        val seguimiento: List<Seguimiento> = emptyList(),
        val favorito: Boolean = true,
        val historial: List<Historial> = emptyList(),
        // Datos editados a mano (Komikku / TachiyomiSY).
        val tituloPropio: String? = null,
        val autorPropio: String? = null,
        val descripcionPropia: String? = null,
        val generosPropios: List<String> = emptyList(),
        val estadoPropio: Int = 0,
        // Datos internos de la extensión (JSON); Mihon/Komikku los guardan en el campo 112.
        val memo: String? = null,
    )

    data class Capitulo(
        val url: String,
        val nombre: String = "",
        val scanlator: String? = null,
        val leido: Boolean = false,
        val marcado: Boolean = false,
        val pagina: Long = 0,
        val fechaSubida: Long = 0,
        val numero: Float = -1f,
        // Posición en la lista de la fuente (0 = la primera) y datos internos de la extensión (campos 10 y 13).
        val orden: Long = 0,
        val memo: String? = null,
    )

    data class Historial(val url: String, val ultimaLectura: Long)
    data class Categoria(val nombre: String, val orden: Long, val oculta: Boolean = false)
    data class Fuente(val nombre: String, val id: Long)
    data class Seguimiento(val servicio: Int, val mediaId: Long, val titulo: String = "", val ultimoCapitulo: Float = 0f)

    /** Id de AniList en los gestores de seguimiento de Mihon. */
    const val ANILIST = 2

    // ───────── Escribir ─────────

    fun codificar(c: Copia): ByteArray {
        val w = Proto.Escritor()
        c.mangas.forEach { m -> w.mensaje(1) { cuerpoManga(m) } }
        c.categorias.forEach { cat -> w.mensaje(2) { cuerpoCategoria(cat) } }
        c.fuentes.forEach { f -> w.mensaje(101) { texto(1, f.nombre); long(2, f.id) } }
        val salida = ByteArrayOutputStream()
        GZIPOutputStream(salida).use { it.write(w.aBytes()) }
        return salida.toByteArray()
    }

    /** Los campos de un BackupManga. */
    private fun Proto.Escritor.cuerpoManga(m: Manga) {
        long(1, m.fuente)
        texto(2, m.url)
        texto(3, m.titulo)
        texto(4, m.artista)
        texto(112, m.memo)
        texto(5, m.autor)
        texto(6, m.descripcion)
        m.generos.forEach { texto(7, it) }
        int(8, m.estado)
        texto(9, m.portada)
        long(13, m.anadido)
        m.capitulos.forEach { ch ->
            mensaje(16) {
                texto(1, ch.url)
                texto(2, ch.nombre)
                texto(3, ch.scanlator)
                bool(4, ch.leido)
                bool(5, ch.marcado)
                long(6, ch.pagina)
                long(8, ch.fechaSubida)
                float(9, ch.numero)
                if (ch.orden > 0) long(10, ch.orden)
                texto(13, ch.memo)
            }
        }
        m.categorias.forEach { long(17, it) }
        m.seguimiento.forEach { t ->
            mensaje(18) {
                int(1, t.servicio)
                long(2, t.mediaId)
                int(3, t.mediaId.toInt())
                texto(4, if (t.servicio == ANILIST) "https://anilist.co/manga/${t.mediaId}" else "")
                texto(5, t.titulo)
                float(6, t.ultimoCapitulo)
                long(100, t.mediaId)
            }
        }
        bool(100, m.favorito)
        m.historial.forEach { h -> mensaje(104) { texto(1, h.url); long(2, h.ultimaLectura) } }
        if (m.estadoPropio != 0) int(602, m.estadoPropio)
        texto(800, m.tituloPropio)
        texto(802, m.autorPropio)
        texto(804, m.descripcionPropia)
        m.generosPropios.forEach { texto(805, it) }
    }

    private fun Proto.Escritor.cuerpoCategoria(cat: Categoria) {
        texto(1, cat.nombre)
        long(2, cat.orden)
        long(3, cat.orden + 1)
        bool(900, cat.oculta)
    }

    /**
     * Escribe una copia .tachibk directamente en [salida], manga a manga, sin montarla entera en memoria
     * (una biblioteca de miles de mangas con cientos de miles de capítulos no cabría).
     */
    class EscritorFlujo(salida: java.io.OutputStream) : java.io.Closeable {
        private val gz = GZIPOutputStream(java.io.BufferedOutputStream(salida, 64 * 1024), 64 * 1024)

        private fun varint(v: Long) {
            var x = v
            while (true) {
                if (x and 0x7FL.inv() == 0L) { gz.write(x.toInt()); return }
                gz.write(((x and 0x7F) or 0x80).toInt())
                x = x ushr 7
            }
        }

        private fun campo(n: Int, datos: ByteArray) {
            varint(((n shl 3) or 2).toLong()); varint(datos.size.toLong()); gz.write(datos)
        }

        fun manga(m: Manga) = campo(1, Proto.Escritor().apply { cuerpoManga(m) }.aBytes())
        fun categoria(c: Categoria) = campo(2, Proto.Escritor().apply { cuerpoCategoria(c) }.aBytes())
        fun fuente(f: Fuente) = campo(101, Proto.Escritor().apply { texto(1, f.nombre); long(2, f.id) }.aBytes())

        override fun close() { gz.finish(); gz.flush(); gz.close() }
    }

    // ───────── Leer ─────────

    /** gzip → bytes (si no está comprimido, se devuelve igual). */
    fun descomprimirSiHace(bytes: ByteArray): ByteArray =
        if (bytes.size > 2 && bytes[0] == 0x1f.toByte() && bytes[1] == 0x8b.toByte()) GZIPInputStream(bytes.inputStream()).use { it.readBytes() }
        else bytes

    /** ¿Es una copia .tachibk (protobuf) y no una copia .json de Lector? */
    fun esTachibk(bytes: ByteArray): Boolean {
        val datos = runCatching { descomprimirSiHace(bytes) }.getOrNull() ?: return false
        val primero = datos.firstOrNull { !it.toInt().toChar().isWhitespace() } ?: return false
        return primero.toInt().toChar() != '{'
    }

    fun decodificar(bytes: ByteArray): Copia {
        val raiz = Proto.leer(descomprimirSiHace(bytes))
        val mangas = raiz.mensajes(1).mapNotNull { m ->
            val url = m.texto(2) ?: return@mapNotNull null
            Manga(
                fuente = m.long(1),
                url = url,
                titulo = m.texto(3).orEmpty(),
                artista = m.texto(4),
                autor = m.texto(5),
                descripcion = m.texto(6),
                generos = m.textos(7),
                estado = m.int(8),
                portada = m.texto(9),
                anadido = m.long(13),
                capitulos = m.mensajes(16).mapNotNull { c ->
                    val u = c.texto(1)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                    Capitulo(
                        url = u, nombre = c.texto(2).orEmpty(), scanlator = c.texto(3), leido = c.bool(4), marcado = c.bool(5),
                        pagina = c.long(6), fechaSubida = c.long(8), numero = c.float(9, -1f),
                        orden = c.long(10), memo = c.texto(13)?.takeIf { it.length > 2 },
                    )
                },
                categorias = m.longs(17),
                seguimiento = m.mensajes(18).map { t ->
                    Seguimiento(t.int(1), t.long(100).takeIf { it > 0 } ?: t.long(3), t.texto(5).orEmpty(), t.float(6))
                },
                favorito = m.bool(100, true),
                historial = m.mensajes(104).mapNotNull { h -> h.texto(1)?.let { Historial(it, h.long(2)) } },
                memo = m.texto(112)?.takeIf { it.length > 2 },
                tituloPropio = m.texto(800),
                autorPropio = m.texto(802),
                descripcionPropia = m.texto(804),
                generosPropios = m.textos(805),
                estadoPropio = m.int(602),
            )
        }
        val categorias = raiz.mensajes(2).mapIndexedNotNull { i, c ->
            val n = c.texto(1)?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapIndexedNotNull null
            Categoria(n, c.long(2, i.toLong()), c.bool(900))
        }
        val fuentes = raiz.mensajes(101).map { Fuente(it.texto(1).orEmpty(), it.long(2)) }
        // Listados de extensiones (Mihon: dirección base; Komikku: dirección del índice).
        val listados = raiz.mensajes(106).mapNotNull { it.texto(1)?.trim()?.takeIf { u -> u.startsWith("http") } }
        return Copia(mangas, categorias, fuentes, listados)
    }

    // ───────── Leer por partes (para restaurar sin llenar la memoria) ─────────

    /** Un manga con lo justo para restaurar: de sus capítulos solo los que tienen algo (leído, marcado o página). */
    class MangaLigero(
        val fuente: Long, val url: String, val titulo: String, val autor: String?, val descripcion: String?,
        val generos: List<String>, val estado: Int, val portada: String?, val anadido: Long, val favorito: Boolean,
        val totalCapitulos: Int, val conEstado: List<Capitulo>, val categorias: List<Long>,
        /** Las direcciones de TODOS sus capítulos (para guardarlos como «conocidos» y poder exportarlos después). */
        val urls: List<String>,
        val seguimiento: List<Seguimiento>, val ultimaLectura: Historial?,
        val tituloPropio: String?, val autorPropio: String?, val descripcionPropia: String?,
        val generosPropios: List<String>, val estadoPropio: Int,
        /** Todos sus capítulos, en el orden de la fuente (para guardar su ficha en el móvil), y el memo del manga. */
        val todos: List<Capitulo>, val artista: String?, val memo: String?,
    )

    private fun flujo(bytes: ByteArray): java.io.InputStream =
        if (bytes.size > 2 && bytes[0] == 0x1f.toByte() && bytes[1] == 0x8b.toByte()) GZIPInputStream(bytes.inputStream(), 64 * 1024)
        else bytes.inputStream()

    /** Primera pasada: categorías, fuentes y listados (los mangas se saltan sin descodificarlos). */
    fun leerCabecera(bytes: ByteArray): Copia {
        val cats = ArrayList<Categoria>(); val fuentes = ArrayList<Fuente>(); val listados = ArrayList<String>()
        Proto.recorrerFlujo(flujo(bytes)) { num, datos ->
            when (num) {
                2 -> {
                    val c = Proto.Cursor(datos); var nombre: String? = null; var orden = cats.size.toLong(); var oculta = false
                    while (c.siguiente()) when (c.numero) { 1 -> nombre = c.texto().trim(); 2 -> orden = c.valor; 900 -> oculta = c.bool() }
                    if (!nombre.isNullOrEmpty()) cats += Categoria(nombre!!, orden, oculta)
                }
                101 -> {
                    val c = Proto.Cursor(datos); var n = ""; var id = 0L
                    while (c.siguiente()) when (c.numero) { 1 -> n = c.texto(); 2 -> id = c.valor }
                    fuentes += Fuente(n, id)
                }
                106 -> {
                    val c = Proto.Cursor(datos)
                    while (c.siguiente()) if (c.numero == 1 && c.tipo == 2) c.texto().trim().takeIf { it.startsWith("http") }?.let { listados += it }
                }
            }
        }
        return Copia(emptyList(), cats, fuentes, listados)
    }

    /** Segunda pasada: entrega los mangas de uno en uno (nunca están todos a la vez en memoria). */
    fun recorrerMangas(bytes: ByteArray, alManga: (MangaLigero) -> Unit) {
        Proto.recorrerFlujo(flujo(bytes)) { num, datos -> if (num == 1) leerMangaLigero(datos)?.let(alManga) }
    }

    private fun leerMangaLigero(datos: ByteArray): MangaLigero? {
        val c = Proto.Cursor(datos)
        var fuente = 0L; var url: String? = null; var titulo = ""; var autor: String? = null; var desc: String? = null
        val generos = ArrayList<String>(); var estado = 0; var portada: String? = null; var anadido = 0L; var favorito = true
        var total = 0; val conEstado = ArrayList<Capitulo>(); val urls = ArrayList<String>(); val cats = ArrayList<Long>(); val segs = ArrayList<Seguimiento>()
        var ultima: Historial? = null
        var tP: String? = null; var aP: String? = null; var dP: String? = null; val gP = ArrayList<String>(); var eP = 0
        val todos = ArrayList<Capitulo>(); var artista: String? = null; var memo: String? = null
        while (c.siguiente()) {
            when (c.numero) {
                1 -> fuente = c.valor
                2 -> url = c.texto()
                3 -> titulo = c.texto()
                4 -> artista = c.texto()
                5 -> autor = c.texto()
                112 -> memo = c.texto().takeIf { it.length > 2 }
                6 -> desc = c.texto()
                7 -> generos += c.texto()
                8 -> estado = c.valor.toInt()
                9 -> portada = c.texto()
                13 -> anadido = c.valor
                16 -> {
                    total++
                    leerCapitulo(c.sub())?.let { cap ->
                        urls += cap.url; todos += cap
                        if (cap.leido || cap.marcado || cap.pagina > 0) conEstado += cap
                    }
                }
                17 -> if (c.tipo == 0) cats += c.valor else { val p = c.sub(); while (p.siguienteVarintEmpaquetado()) cats += p.valor }
                18 -> {
                    val t = c.sub(); var serv = 0; var m100 = 0L; var m3 = 0L; var tt = ""; var ult = 0f
                    while (t.siguiente()) when (t.numero) { 1 -> serv = t.valor.toInt(); 3 -> m3 = t.valor; 5 -> tt = t.texto(); 6 -> ult = t.float(); 100 -> m100 = t.valor }
                    segs += Seguimiento(serv, if (m100 > 0) m100 else m3, tt, ult)
                }
                100 -> favorito = c.bool()
                104 -> {
                    val h = c.sub(); var u: String? = null; var cuando = 0L
                    while (h.siguiente()) when (h.numero) { 1 -> u = h.texto(); 2 -> cuando = h.valor }
                    if (u != null && cuando > (ultima?.ultimaLectura ?: 0L)) ultima = Historial(u!!, cuando)
                }
                602 -> eP = c.valor.toInt()
                800 -> tP = c.texto()
                802 -> aP = c.texto()
                804 -> dP = c.texto()
                805 -> gP += c.texto()
            }
        }
        val u = url ?: return null
        return MangaLigero(
            fuente, u, titulo, autor, desc, generos, estado, portada, anadido, favorito, total, conEstado, cats, urls, segs, ultima,
            tP, aP, dP, gP, eP, todos.sortedBy { it.orden }, artista, memo,
        )
    }

    /** Un capítulo completo (con lo necesario para enseñar la lista de capítulos sin pedirla a la fuente). */
    private fun leerCapitulo(c: Proto.Cursor): Capitulo? {
        var url: String? = null; var nombre = ""; var scan: String? = null
        var leido = false; var marcado = false; var pagina = 0L; var numero = -1f; var fecha = 0L; var orden = 0L; var memo: String? = null
        while (c.siguiente()) when (c.numero) {
            1 -> url = c.texto()
            2 -> nombre = c.texto()
            3 -> scan = c.texto().ifBlank { null }
            4 -> leido = c.bool()
            5 -> marcado = c.bool()
            6 -> pagina = c.valor
            8 -> fecha = c.valor
            9 -> numero = c.float()
            10 -> orden = c.valor
            13 -> memo = c.texto().takeIf { it.length > 2 } // «{}» = vacío
        }
        val u = url ?: return null
        return Capitulo(u, nombre, scan, leido, marcado, pagina, fecha, numero, orden, memo)
    }
}
