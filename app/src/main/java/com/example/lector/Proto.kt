package com.example.lector

import java.io.ByteArrayOutputStream

/**
 * Lector y escritor mínimos del formato protobuf (el que usan las copias .tachibk de Mihon / Komikku / TachiyomiSY).
 * Solo lo necesario: enteros (varint), float (fixed32), double/long fijos (fixed64), textos, bytes y mensajes.
 */
object Proto {

    /** Un campo leído: número, tipo de cable y valor (Long para varint/fixed, ByteArray para delimitados). */
    class Campo(val numero: Int, val tipo: Int, val valor: Any)

    /** Lee todos los campos de un mensaje (en el orden en que vienen). */
    fun leer(b: ByteArray): List<Campo> {
        val out = ArrayList<Campo>()
        var i = 0
        fun varint(): Long {
            var r = 0L
            var s = 0
            while (true) {
                if (i >= b.size) error("Fin inesperado")
                val x = b[i++].toInt() and 0xFF
                r = r or ((x and 0x7F).toLong() shl s)
                if (x and 0x80 == 0) return r
                s += 7
                if (s > 63) error("Varint demasiado largo")
            }
        }
        while (i < b.size) {
            val tag = varint()
            val num = (tag ushr 3).toInt()
            when (val tipo = (tag and 7).toInt()) {
                0 -> out += Campo(num, tipo, varint())
                1 -> {
                    var v = 0L
                    for (k in 0 until 8) v = v or ((b[i + k].toLong() and 0xFF) shl (8 * k))
                    i += 8
                    out += Campo(num, tipo, v)
                }
                2 -> {
                    val n = varint().toInt()
                    require(n >= 0 && i + n <= b.size) { "Longitud no válida" }
                    out += Campo(num, tipo, b.copyOfRange(i, i + n))
                    i += n
                }
                5 -> {
                    var v = 0L
                    for (k in 0 until 4) v = v or ((b[i + k].toLong() and 0xFF) shl (8 * k))
                    i += 4
                    out += Campo(num, tipo, v)
                }
                else -> error("Tipo de campo protobuf no admitido: $tipo")
            }
        }
        return out
    }

    // ---- Ayudas de lectura sobre una lista de campos ----
    fun List<Campo>.long(n: Int, def: Long = 0): Long = lastOrNull { it.numero == n && (it.tipo == 0 || it.tipo == 1) }?.valor as? Long ?: def
    fun List<Campo>.int(n: Int, def: Int = 0): Int = long(n, def.toLong()).toInt()
    fun List<Campo>.bool(n: Int, def: Boolean = false): Boolean = lastOrNull { it.numero == n && it.tipo == 0 }?.let { (it.valor as Long) != 0L } ?: def
    fun List<Campo>.float(n: Int, def: Float = 0f): Float =
        lastOrNull { it.numero == n && it.tipo == 5 }?.let { java.lang.Float.intBitsToFloat((it.valor as Long).toInt()) } ?: def
    fun List<Campo>.texto(n: Int): String? = lastOrNull { it.numero == n && it.tipo == 2 }?.let { String(it.valor as ByteArray, Charsets.UTF_8) }
    fun List<Campo>.textos(n: Int): List<String> = filter { it.numero == n && it.tipo == 2 }.map { String(it.valor as ByteArray, Charsets.UTF_8) }
    fun List<Campo>.mensajes(n: Int): List<List<Campo>> = filter { it.numero == n && it.tipo == 2 }.map { leer(it.valor as ByteArray) }
    fun List<Campo>.mensaje(n: Int): List<Campo>? = lastOrNull { it.numero == n && it.tipo == 2 }?.let { leer(it.valor as ByteArray) }

    /** Enteros repetidos: admite tanto la forma «empaquetada» como un campo por valor. */
    fun List<Campo>.longs(n: Int): List<Long> = filter { it.numero == n }.flatMap { c ->
        if (c.tipo == 0) listOf(c.valor as Long)
        else if (c.tipo == 2) {
            val b = c.valor as ByteArray
            val r = ArrayList<Long>()
            var i = 0
            while (i < b.size) {
                var v = 0L; var s = 0
                while (true) {
                    val x = b[i++].toInt() and 0xFF
                    v = v or ((x and 0x7F).toLong() shl s)
                    if (x and 0x80 == 0) break
                    s += 7
                }
                r += v
            }
            r
        } else emptyList()
    }

    /** Escritor de un mensaje. */
    class Escritor {
        private val o = ByteArrayOutputStream()

        private fun varintCrudo(v: Long) {
            var x = v
            while (true) {
                if (x and 0x7FL.inv() == 0L) { o.write(x.toInt()); return }
                o.write(((x and 0x7F) or 0x80).toInt())
                x = x ushr 7
            }
        }

        private fun etiqueta(n: Int, tipo: Int) = varintCrudo(((n shl 3) or tipo).toLong())

        fun long(n: Int, v: Long) { etiqueta(n, 0); varintCrudo(v) }
        fun int(n: Int, v: Int) = long(n, v.toLong())
        fun bool(n: Int, v: Boolean) = long(n, if (v) 1 else 0)
        fun float(n: Int, v: Float) {
            etiqueta(n, 5)
            val b = java.lang.Float.floatToIntBits(v)
            for (k in 0 until 4) o.write((b ushr (8 * k)) and 0xFF)
        }
        fun bytes(n: Int, v: ByteArray) { etiqueta(n, 2); varintCrudo(v.size.toLong()); o.write(v) }
        fun texto(n: Int, v: String?) { if (v != null) bytes(n, v.toByteArray(Charsets.UTF_8)) }
        fun mensaje(n: Int, bloque: Escritor.() -> Unit) = bytes(n, Escritor().apply(bloque).aBytes())

        fun aBytes(): ByteArray = o.toByteArray()
    }
}
