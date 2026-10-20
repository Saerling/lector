package com.example.lector

import java.io.File
import java.security.MessageDigest
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Datos que crecen con la biblioteca (capítulos leídos, marcados, páginas, capítulos conocidos de cada manga) en
 * archivos propios, NO en SharedPreferences: Android carga cada archivo de preferencias entero en memoria y lo reescribe
 * entero en cada cambio, y con una biblioteca grande eso eran decenas de MB y cierres por falta de memoria.
 *
 * Las escrituras van a un único hilo de fondo y se agrupan (varios cambios seguidos = una escritura), línea a línea,
 * sin montar un texto gigante en memoria. Se escribe a un .tmp y se renombra: un corte a medias no deja el archivo roto.
 */
object Persistencia {
    internal val hilo = Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "lector-guardado").apply { isDaemon = true; priority = Thread.NORM_PRIORITY - 1 } }

    /** Escribe [lineas] en [archivo] de forma segura (tmp + renombrar). */
    fun escribirLineas(archivo: File, lineas: Sequence<String>) {
        archivo.parentFile?.mkdirs()
        val tmp = File(archivo.path + ".tmp")
        tmp.bufferedWriter(Charsets.UTF_8, 64 * 1024).use { w -> lineas.forEach { w.write(it); w.write('\n'.code) } }
        if (!tmp.renameTo(archivo)) { archivo.delete(); tmp.renameTo(archivo) }
    }

    fun leerLineas(archivo: File): List<String> =
        if (!archivo.exists()) emptyList() else archivo.bufferedReader(Charsets.UTF_8).useLines { s -> s.filter { it.isNotEmpty() }.toList() }

    /** Ejecuta en el hilo de guardado. */
    fun enFondo(tarea: () -> Unit) { hilo.execute { runCatching(tarea) } }
}

/**
 * Un conjunto (o mapa) guardado en un archivo, con escrituras agrupadas: [guardar] recibe el estado actual (inmutable)
 * y, al cabo de [esperaMs], se escribe la última versión. Varios cambios en ese rato = una sola escritura.
 */
class ArchivoAgrupado(private val archivo: File, private val esperaMs: Long = 400) {
    @Volatile private var pendiente: (() -> Sequence<String>)? = null
    private val programado = AtomicBoolean(false)

    fun existe() = archivo.exists()
    fun leer(): List<String> = Persistencia.leerLineas(archivo)

    fun guardar(lineas: () -> Sequence<String>) {
        pendiente = lineas
        if (programado.compareAndSet(false, true)) {
            Persistencia.hilo.schedule({
                programado.set(false)
                val p = pendiente
                pendiente = null
                if (p != null) runCatching { Persistencia.escribirLineas(archivo, p()) }
            }, esperaMs, TimeUnit.MILLISECONDS)
        }
    }
}

/**
 * Capítulos conocidos de cada manga (para saber cuáles son nuevos al actualizar): un archivo pequeño por manga.
 * Antes iban todos juntos en las preferencias.
 */
class CapitulosConocidos(carpetaBase: File) {
    private val carpeta = File(carpetaBase, "conocidos").apply { mkdirs() }

    private fun archivo(fuenteId: Long, urlManga: String): File {
        val h = MessageDigest.getInstance("SHA-1").digest("$fuenteId|$urlManga".toByteArray()).joinToString("") { "%02x".format(it) }
        return File(carpeta, h)
    }

    /** null = nunca se registró (no es lo mismo que una lista vacía). */
    fun leer(fuenteId: Long, urlManga: String): Set<String>? {
        val f = archivo(fuenteId, urlManga)
        return if (f.exists()) Persistencia.leerLineas(f).toHashSet() else null
    }

    fun guardar(fuenteId: Long, urlManga: String, urls: Set<String>) {
        val f = archivo(fuenteId, urlManga)
        val copia = urls.toList()
        Persistencia.enFondo { Persistencia.escribirLineas(f, copia.asSequence()) }
    }

    fun olvidar(fuenteId: Long, urlManga: String) {
        val f = archivo(fuenteId, urlManga)
        Persistencia.enFondo { f.delete() }
    }

    /** Escritura inmediata (para la migración desde las preferencias). */
    fun guardarYa(fuenteId: Long, urlManga: String, urls: Collection<String>) =
        Persistencia.escribirLineas(archivo(fuenteId, urlManga), urls.asSequence())
}

/**
 * Conjunto de números de 64 bits sin objetos por elemento (direccionamiento abierto): 300.000 capítulos leídos ocupan
 * ~5 MB en vez de los ~60 MB de un Set<String>. Se modifica en sitio (antes se copiaba el conjunto entero en cada
 * capítulo leído). Seguro entre hilos (los métodos están sincronizados).
 */
class ConjuntoLargo(capacidad: Int = 1024) {
    private var tabla = LongArray(potencia(capacidad * 2))
    var size = 0; private set

    private fun potencia(n: Int): Int { var c = 16; while (c < n) c = c shl 1; return c }

    /** 0 marca hueco libre: el 0 real se guarda como 1 (una colisión en 2^64, despreciable). */
    private fun norm(v: Long) = if (v == 0L) 1L else v
    private fun pos(v: Long, mask: Int) = ((v xor (v ushr 32)).toInt() * -0x61c88647) and mask

    @Synchronized fun contains(v0: Long): Boolean {
        val v = norm(v0); val mask = tabla.size - 1
        var i = pos(v, mask)
        while (true) { val x = tabla[i]; if (x == 0L) return false; if (x == v) return true; i = (i + 1) and mask }
    }

    @Synchronized fun add(v0: Long): Boolean {
        val v = norm(v0)
        if ((size + 1) * 2 > tabla.size) crecer()
        val mask = tabla.size - 1
        var i = pos(v, mask)
        while (true) {
            val x = tabla[i]
            if (x == 0L) { tabla[i] = v; size++; return true }
            if (x == v) return false
            i = (i + 1) and mask
        }
    }

    @Synchronized fun remove(v0: Long): Boolean {
        val v = norm(v0); val mask = tabla.size - 1
        var i = pos(v, mask)
        while (true) {
            val x = tabla[i]
            if (x == 0L) return false
            if (x == v) break
            i = (i + 1) and mask
        }
        // Borrado con desplazamiento hacia atrás (sin lápidas).
        tabla[i] = 0L; size--
        var j = (i + 1) and mask
        while (tabla[j] != 0L) {
            val y = tabla[j]; tabla[j] = 0L; size--
            addSinCrecer(y)
            j = (j + 1) and mask
        }
        return true
    }

    private fun addSinCrecer(v: Long) {
        val mask = tabla.size - 1
        var i = pos(v, mask)
        while (tabla[i] != 0L) i = (i + 1) and mask
        tabla[i] = v; size++
    }

    private fun crecer() {
        val vieja = tabla
        tabla = LongArray(vieja.size * 2); size = 0
        for (x in vieja) if (x != 0L) addSinCrecer(x)
    }

    @Synchronized fun addAll(otro: ConjuntoLargo) { otro.aArreglo().forEach { add(it) } }

    @Synchronized fun aArreglo(): LongArray {
        val r = LongArray(size); var k = 0
        for (x in tabla) if (x != 0L) r[k++] = x
        return r
    }

    companion object {
        /** Huella de 64 bits de un capítulo («fuente|url»): FNV-1a + mezcla final. */
        fun clave(fuenteId: Long, url: String): Long {
            var h = -0x340d631b7bdddcdbL xor (fuenteId * -0x61c8864680b583ebL)
            for (ch in url) { h = h xor ch.code.toLong(); h *= 0x100000001b3L }
            h = h xor (h ushr 33); h *= -0xae502812aa7333L; h = h xor (h ushr 33); h *= -0x3b314601e57a13adL; h = h xor (h ushr 33)
            return h
        }

        /** Lo mismo desde el texto «fuente|url» de las versiones anteriores. */
        fun claveDeTexto(k: String): Long? {
            val i = k.indexOf('|'); if (i <= 0) return null
            val f = k.substring(0, i).toLongOrNull() ?: return null
            return clave(f, k.substring(i + 1))
        }
    }
}

/** Guarda un [ConjuntoLargo] en binario (8 bytes por elemento), agrupando cambios seguidos en una escritura. */
class ArchivoConjuntoLargo(private val archivo: File, private val esperaMs: Long = 600) {
    private val programado = AtomicBoolean(false)
    fun existe() = archivo.exists()

    fun leer(): ConjuntoLargo {
        val bytes = archivo.readBytes()
        val n = bytes.size / 8
        val c = ConjuntoLargo(n)
        val bb = java.nio.ByteBuffer.wrap(bytes)
        repeat(n) { c.add(bb.long) }
        return c
    }

    fun guardar(c: ConjuntoLargo) {
        if (!programado.compareAndSet(false, true)) return
        Persistencia.hilo.schedule({
            programado.set(false)
            runCatching { escribirYa(c) }
        }, esperaMs, TimeUnit.MILLISECONDS)
    }

    fun escribirYa(c: ConjuntoLargo) {
        val datos = c.aArreglo()
        archivo.parentFile?.mkdirs()
        val tmp = File(archivo.path + ".tmp")
        java.io.DataOutputStream(tmp.outputStream().buffered(64 * 1024)).use { o -> datos.forEach { o.writeLong(it) } }
        if (!tmp.renameTo(archivo)) { archivo.delete(); tmp.renameTo(archivo) }
    }
}
