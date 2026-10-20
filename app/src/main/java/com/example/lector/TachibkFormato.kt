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
        c.mangas.forEach { m ->
            w.mensaje(1) {
                long(1, m.fuente)
                texto(2, m.url)
                texto(3, m.titulo)
                texto(4, m.artista)
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
        }
        c.categorias.forEach { cat ->
            w.mensaje(2) {
                texto(1, cat.nombre)
                long(2, cat.orden)
                long(3, cat.orden + 1)
                bool(900, cat.oculta)
            }
        }
        c.fuentes.forEach { f -> w.mensaje(101) { texto(1, f.nombre); long(2, f.id) } }
        val salida = ByteArrayOutputStream()
        GZIPOutputStream(salida).use { it.write(w.aBytes()) }
        return salida.toByteArray()
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
                    )
                },
                categorias = m.longs(17),
                seguimiento = m.mensajes(18).map { t ->
                    Seguimiento(t.int(1), t.long(100).takeIf { it > 0 } ?: t.long(3), t.texto(5).orEmpty(), t.float(6))
                },
                favorito = m.bool(100, true),
                historial = m.mensajes(104).mapNotNull { h -> h.texto(1)?.let { Historial(it, h.long(2)) } },
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
}
