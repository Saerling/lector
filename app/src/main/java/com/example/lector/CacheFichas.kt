package com.example.lector

import android.content.Context
import com.example.lector.ext.CapituloFuente
import com.example.lector.ext.DetalleManga
import com.example.lector.ext.FichaManga
import android.util.JsonReader
import android.util.JsonToken
import android.util.JsonWriter
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.zip.Deflater
import java.security.MessageDigest
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * Las fichas (datos + lista de capítulos) guardadas en el móvil, como la base de datos de Mihon/Komikku: al abrir un
 * manga se enseña lo guardado al instante y solo se vuelve a pedir a la fuente al deslizar hacia abajo (o en la
 * actualización de la biblioteca). Un archivo comprimido pequeño por manga.
 *
 * También guarda el `memo` de cada manga y capítulo: datos internos que las extensiones nuevas (tachiyomix 1.6, p. ej.
 * todas las Madara) apuntan al dar la lista y piden luego para abrir un capítulo. Sin él salía «Refresh the chapter list».
 */
object CacheFichas {
    private var carpeta: File? = null

    // Las últimas fichas leídas, para no releer el archivo en cada consulta de memo.
    private val recientes = object : LinkedHashMap<String, FichaManga>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, FichaManga>?) = size > 12
    }

    fun iniciar(ctx: Context) {
        if (carpeta == null) carpeta = File(ctx.applicationContext.filesDir, "fichas").apply { mkdirs() }
    }

    private fun clave(fuenteId: Long, url: String) = "$fuenteId|$url"

    private fun archivo(fuenteId: Long, url: String): File? {
        val c = carpeta ?: return null
        val h = MessageDigest.getInstance("SHA-1").digest(clave(fuenteId, url).toByteArray()).joinToString("") { "%02x".format(it) }
        return File(c, "$h.json.gz")
    }

    /** La ficha guardada (null si nunca se cargó). Llamar fuera del hilo principal. */
    fun leer(fuenteId: Long, url: String): FichaManga? {
        val k = clave(fuenteId, url)
        synchronized(recientes) { recientes[k] }?.let { return it }
        val a = archivo(fuenteId, url)?.takeIf { it.exists() } ?: return null
        // Lectura en flujo (sin montar el JSON entero): con miles de fichas, las copias van mucho más rápido.
        val f = runCatching {
            JsonReader(InputStreamReader(GZIPInputStream(a.inputStream(), 16 * 1024), Charsets.UTF_8)).use { r ->
                var t = 0L; var memo: String? = null; var detalle: DetalleManga? = null
                val caps = ArrayList<CapituloFuente>()
                r.beginObject()
                while (r.hasNext()) when (r.nextName()) {
                    "t" -> t = r.nextLong()
                    "memo" -> memo = r.texto()
                    "detalle" -> detalle = leerDetalle(r)
                    "caps" -> { r.beginArray(); while (r.hasNext()) caps += leerCapitulo(r); r.endArray() }
                    else -> r.skipValue()
                }
                r.endObject()
                FichaManga(detalle ?: return@use null, caps, memo, t)
            }
        }.getOrNull() ?: return null
        synchronized(recientes) { recientes[k] = f }
        return f
    }

    private fun JsonReader.texto(): String? = if (peek() == JsonToken.NULL) { nextNull(); null } else nextString().ifBlank { null }

    private fun leerDetalle(r: JsonReader): DetalleManga {
        var titulo = ""; var autor: String? = null; var artista: String? = null; var desc: String? = null
        val generos = ArrayList<String>(); var estado = 0; var portada: String? = null
        r.beginObject()
        while (r.hasNext()) when (r.nextName()) {
            "titulo" -> titulo = r.texto().orEmpty()
            "autor" -> autor = r.texto()
            "artista" -> artista = r.texto()
            "descripcion" -> desc = r.texto()
            "generos" -> { r.beginArray(); while (r.hasNext()) r.texto()?.let { generos += it }; r.endArray() }
            "estado" -> estado = r.nextInt()
            "portada" -> portada = r.texto()
            else -> r.skipValue()
        }
        r.endObject()
        return DetalleManga(titulo, autor, artista, desc, generos, estado, portada)
    }

    private fun leerCapitulo(r: JsonReader): CapituloFuente {
        var u = ""; var n = ""; var f = 0L; var s: String? = null; var x = -1f; var m: String? = null
        r.beginObject()
        while (r.hasNext()) when (r.nextName()) {
            "u" -> u = r.nextString()
            "n" -> n = r.texto().orEmpty()
            "f" -> f = r.nextLong()
            "s" -> s = r.texto()
            "x" -> x = r.nextDouble().toFloat()
            "m" -> m = r.texto()
            else -> r.skipValue()
        }
        r.endObject()
        return CapituloFuente(u, n, f, s, x, m)
    }

    /** Guarda la ficha recién cargada de la fuente (escritura en flujo y segura: .tmp y renombrar). */
    fun guardar(fuenteId: Long, url: String, f: FichaManga) {
        val a = archivo(fuenteId, url) ?: return
        val ahora = System.currentTimeMillis()
        runCatching {
            val tmp = File(a.path + ".tmp")
            // Compresión rápida: el archivo queda algo mayor pero se escribe varias veces más rápido.
            val gz = object : GZIPOutputStream(tmp.outputStream(), 16 * 1024) { init { def.setLevel(Deflater.BEST_SPEED) } }
            JsonWriter(OutputStreamWriter(gz, Charsets.UTF_8).buffered(16 * 1024)).use { w ->
                val d = f.detalle
                w.beginObject()
                w.name("t").value(ahora)
                w.name("memo").value(f.memo)
                w.name("detalle").beginObject()
                    .name("titulo").value(d.titulo).name("autor").value(d.autor).name("artista").value(d.artista)
                    .name("descripcion").value(d.descripcion)
                w.name("generos").beginArray(); d.generos.forEach { w.value(it) }; w.endArray()
                w.name("estado").value(d.estado.toLong()).name("portada").value(d.portadaUrl)
                w.endObject()
                w.name("caps").beginArray()
                f.capitulos.forEach { c ->
                    w.beginObject().name("u").value(c.url).name("n").value(c.nombre).name("f").value(c.fecha)
                    if (c.scanlator != null) w.name("s").value(c.scanlator)
                    if (c.numero.isFinite()) w.name("x").value(c.numero.toDouble())
                    if (c.memo != null) w.name("m").value(c.memo)
                    w.endObject()
                }
                w.endArray()
                w.endObject()
            }
            if (!tmp.renameTo(a)) { a.delete(); tmp.renameTo(a) }
        }
        synchronized(recientes) { recientes[clave(fuenteId, url)] = f.copy(guardada = ahora) }
    }

    fun memoManga(fuenteId: Long, url: String): String? = leer(fuenteId, url)?.memo

    fun existe(fuenteId: Long, url: String): Boolean = archivo(fuenteId, url)?.exists() == true

    /** Un capítulo con su memo (si le falta y el manga está guardado, se busca en su ficha). */
    fun conMemo(fuenteId: Long, urlManga: String, c: CapituloFuente): CapituloFuente {
        if (c.memo != null) return c
        val m = leer(fuenteId, urlManga)?.capitulos?.firstOrNull { it.url == c.url }?.memo ?: return c
        return c.copy(memo = m)
    }

}
