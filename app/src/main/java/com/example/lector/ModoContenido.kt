package com.example.lector

import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.lector.ext.ExtManager
import java.text.Normalizer

/**
 * Modo de contenido (Ajustes → Seguridad): NORMAL (todo, respetando «Mostrar contenido +18»), SFW (oculta todo lo +18)
 * o NSFW (solo lo +18). Un único valor guardado en las preferencias de extensiones ("modo_contenido").
 *
 * Una cosa es +18 si su fuente es de una extensión marcada NSFW o si sus géneros llevan etiquetas de adultos.
 * Las dos comprobaciones van en caché: el conjunto de fuentes NSFW se rehace solo cuando cambian las listas de
 * fuentes/extensiones, y cada género se analiza una sola vez.
 */
object ModoContenido {
    enum class Modo { NORMAL, SFW, NSFW }

    var modo by mutableStateOf(Modo.NORMAL); private set
    private var prefs: SharedPreferences? = null
    private var ext: ExtManager? = null

    /** Lo llama ExtManager al crearse. */
    fun iniciar(p: SharedPreferences, e: ExtManager) {
        prefs = p; ext = e
        modo = runCatching { Modo.valueOf(p.getString("modo_contenido", null) ?: "NORMAL") }.getOrDefault(Modo.NORMAL)
    }

    fun fijar(m: Modo) {
        modo = m
        prefs?.edit()?.putString("modo_contenido", m.name)?.apply()
    }

    /** ¿Se ve una extensión/fuente con esta marca NSFW? (también aplica «Mostrar contenido +18» en modo normal). */
    fun verNsfw(esNsfw: Boolean, mostrarNsfw: Boolean): Boolean = when (modo) {
        Modo.SFW -> !esNsfw
        Modo.NSFW -> esNsfw
        Modo.NORMAL -> mostrarNsfw || !esNsfw
    }

    /** ¿Se ve un manga de [fuenteId] con estos [generos]? En modo normal, siempre. */
    fun permite(fuenteId: Long, generos: Collection<String>?): Boolean {
        val m = modo
        if (m == Modo.NORMAL) return true
        val adulto = fuenteNsfw(fuenteId) || generos?.any { generoAdulto(it) } == true
        return if (m == Modo.SFW) !adulto else adulto
    }

    fun permite(c: Comic): Boolean = permite(c.fuenteId, c.generos)

    fun filtrar(lista: List<Comic>): List<Comic> = if (modo == Modo.NORMAL) lista else lista.filter { permite(it) }

    // ---- Fuentes NSFW (caché por identidad de las listas del ExtManager) ----
    private var claveFuentes: Any? = null
    private var claveDisp: Any? = null
    private var nsfwIds: Set<Long> = emptySet()

    fun fuenteNsfw(fuenteId: Long): Boolean {
        if (fuenteId == 0L) return false
        val e = ext ?: return false
        val f = e.fuentes
        val d = e.disponibles
        if (f !== claveFuentes || d !== claveDisp) {
            val s = HashSet<Long>()
            f.forEach { if (it.nsfw) s.add(it.id) }
            d.forEach { x -> if (x.nsfw) x.fuentes.forEach { s.add(it.id) } }
            nsfwIds = s; claveFuentes = f; claveDisp = d
        }
        return fuenteId in nsfwIds
    }

    // ---- Géneros de adultos ----
    private val etiquetas = setOf(
        "hentai", "adult", "adults", "adulto", "adultos", "adult content", "contenido adulto", "smut", "ecchi",
        "mature", "maduro", "erotica", "erotic", "erotico", "erotismo", "pornographic", "porn", "porno", "+18", "18+",
        "r18", "r-18", "nsfw", "sexual content", "contenido sexual", "doujinshi hentai",
    )
    private val cacheGeneros = HashMap<String, Boolean>()

    fun generoAdulto(g: String): Boolean = synchronized(cacheGeneros) {
        cacheGeneros.getOrPut(g) {
            val n = Normalizer.normalize(g.trim().lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
            n in etiquetas || n.startsWith("hentai") || n.endsWith("(18+)") || n.endsWith("(+18)")
        }
    }
}
