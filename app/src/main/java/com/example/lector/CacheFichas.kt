package com.example.lector

import android.content.Context
import com.example.lector.ext.CapituloFuente
import com.example.lector.ext.DetalleManga
import com.example.lector.ext.FichaManga
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
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
        val f = runCatching {
            val o = JSONObject(GZIPInputStream(a.inputStream().buffered()).bufferedReader().use { it.readText() })
            val d = o.getJSONObject("detalle")
            val gen = d.optJSONArray("generos")
            val caps = o.getJSONArray("caps")
            FichaManga(
                detalle = DetalleManga(
                    titulo = d.optString("titulo"),
                    autor = d.txt("autor"), artista = d.txt("artista"), descripcion = d.txt("descripcion"),
                    generos = (0 until (gen?.length() ?: 0)).map { gen!!.getString(it) },
                    estado = d.optInt("estado"),
                    portadaUrl = d.txt("portada"),
                ),
                capitulos = (0 until caps.length()).map { i ->
                    val c = caps.getJSONObject(i)
                    CapituloFuente(
                        c.getString("u"), c.optString("n"), c.optLong("f"), c.txt("s"),
                        c.optDouble("x", -1.0).toFloat(), c.txt("m"),
                    )
                },
                memo = o.txt("memo"),
                guardada = o.optLong("t"),
            )
        }.getOrNull() ?: return null
        synchronized(recientes) { recientes[k] = f }
        return f
    }

    /** Guarda la ficha recién cargada de la fuente (escritura segura: .tmp y renombrar). */
    fun guardar(fuenteId: Long, url: String, f: FichaManga) {
        val a = archivo(fuenteId, url) ?: return
        val ahora = System.currentTimeMillis()
        val d = f.detalle
        val o = JSONObject()
            .put("t", ahora)
            .put("memo", f.memo)
            .put(
                "detalle",
                JSONObject().put("titulo", d.titulo).put("autor", d.autor).put("artista", d.artista)
                    .put("descripcion", d.descripcion).put("generos", JSONArray(d.generos)).put("estado", d.estado)
                    .put("portada", d.portadaUrl),
            )
            .put(
                "caps",
                JSONArray().also { arr ->
                    f.capitulos.forEach { c ->
                        arr.put(
                            JSONObject().put("u", c.url).put("n", c.nombre).put("f", c.fecha).put("s", c.scanlator)
                                .put("x", c.numero.toDouble()).put("m", c.memo),
                        )
                    }
                },
            )
        runCatching {
            val tmp = File(a.path + ".tmp")
            GZIPOutputStream(tmp.outputStream().buffered()).bufferedWriter().use { it.write(o.toString()) }
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

    private fun JSONObject.txt(k: String): String? = if (isNull(k)) null else optString(k).ifBlank { null }
}
