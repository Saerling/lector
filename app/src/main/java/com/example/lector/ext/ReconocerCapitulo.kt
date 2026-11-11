package com.example.lector.ext

/**
 * Número de capítulo sacado de su nombre. Copia de ChapterRecognition de Mihon/Komikku (Apache-2.0), con Float.
 * La mayoría de extensiones no dan número (-1): sin esto no hay orden por número, ni «capítulos que faltan»,
 * ni progreso de seguimiento fiable.
 */
object ReconocerCapitulo {
    private const val NUMERO = """([0-9]+)(\.[0-9]+)?(\.?[a-z]+)?"""

    // «Vol.1 Ch. 4: …» → 4. Se añaden las formas en español («Capítulo 12», «Cap. 12», «Episodio 3»).
    private val basico = Regex("""(?<=ch\.|cap\.|cap[ií]tulo|episodio|ep\.) *$NUMERO""")
    private val numero = Regex(NUMERO)

    // «Prison School 12 v.1 vol004 version1243 volume64» → «Prison School 12»
    private val sobra = Regex("""\b(?:v|ver|vol|version|volume|volumen|tomo|season|temporada|s)[^a-z]?[0-9]+""")
    private val espacioSobra = Regex("""\s(?=extra|special|omake)""")

    /** [conocido]: el que da la fuente; si es válido (>-1, o -2) se respeta. Devuelve -1 si no se reconoce. */
    fun numero(tituloManga: String, nombre: String, conocido: Float = -1f): Float {
        if (conocido == -2f || conocido > -1f) return conocido
        var limpio = nombre.lowercase()
        if (tituloManga.isNotBlank()) limpio = limpio.replace(tituloManga.lowercase(), "")
        limpio = limpio.trim().replace(',', '.').replace('-', '.').replace(espacioSobra, "")

        val todos = numero.findAll(limpio).toList()
        if (todos.isEmpty()) return conocido
        if (todos.size > 1) {
            val sin = sobra.replace(limpio, "")
            basico.find(sin)?.let { return valor(it) }
            numero.find(sin)?.let { return valor(it) }
        }
        return valor(todos.first())
    }

    private fun valor(m: MatchResult): Float {
        val entero = m.groups[1]?.value?.toDoubleOrNull() ?: return -1f
        return (entero + decimal(m.groups[2]?.value, m.groups[3]?.value)).toFloat()
    }

    private fun decimal(dec: String?, alfa: String?): Double {
        if (!dec.isNullOrEmpty()) return dec.toDouble()
        if (!alfa.isNullOrEmpty()) {
            if (alfa.contains("extra")) return 0.99
            if (alfa.contains("omake")) return 0.98
            if (alfa.contains("special")) return 0.97
            val a = alfa.trimStart('.')
            if (a.length == 1) {
                val n = a[0].code - ('a'.code - 1) // x.a → x.1, x.b → x.2…
                return if (n >= 10) 0.0 else n / 10.0
            }
        }
        return 0.0
    }
}
