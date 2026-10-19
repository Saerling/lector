package com.example.lector

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import java.io.File
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import java.util.zip.ZipFile

// Un archivo .cbz es un .zip con imágenes dentro. Aquí lo leemos.
object Cbz {
    private val extensiones = setOf("jpg", "jpeg", "png", "webp", "gif")

    // ¿Este píxel se distingue del color de fondo?
    private fun distinto(pixel: Int, fondo: Int): Boolean {
        val dif = abs(Color.red(pixel) - Color.red(fondo)) +
            abs(Color.green(pixel) - Color.green(fondo)) +
            abs(Color.blue(pixel) - Color.blue(fondo))
        return dif > 60
    }

    // Quita los márgenes lisos (del color de la esquina) de una página.
    // Si no hay un margen claro o recortaría demasiado, devuelve la imagen tal cual.
    fun recortarBordes(b: Bitmap): Bitmap {
        val w = b.width
        val h = b.height
        if (w < 100 || h < 100) return b

        // Si las cuatro esquinas no se parecen, no hay un margen uniforme.
        val fondo = b.getPixel(0, 0)
        if (distinto(b.getPixel(w - 1, 0), fondo) ||
            distinto(b.getPixel(0, h - 1), fondo) ||
            distinto(b.getPixel(w - 1, h - 1), fondo)
        ) {
            return b
        }

        // Se mira un píxel de cada 4 para ir rápido.
        fun filaConContenido(y: Int): Boolean {
            var x = 0
            while (x < w) {
                if (distinto(b.getPixel(x, y), fondo)) return true
                x += 4
            }
            return false
        }

        fun columnaConContenido(x: Int): Boolean {
            var y = 0
            while (y < h) {
                if (distinto(b.getPixel(x, y), fondo)) return true
                y += 4
            }
            return false
        }

        var arriba = 0
        while (arriba < h && !filaConContenido(arriba)) arriba++
        if (arriba >= h) return b // página lisa

        var abajo = h - 1
        while (abajo > arriba && !filaConContenido(abajo)) abajo--
        var izquierda = 0
        while (izquierda < w && !columnaConContenido(izquierda)) izquierda++
        var derecha = w - 1
        while (derecha > izquierda && !columnaConContenido(derecha)) derecha--

        // Un pequeño margen de seguridad para no morder el dibujo.
        val margen = 6
        val x0 = max(0, izquierda - margen)
        val y0 = max(0, arriba - margen)
        val x1 = min(w - 1, derecha + margen)
        val y1 = min(h - 1, abajo + margen)
        val ancho = x1 - x0 + 1
        val alto = y1 - y0 + 1

        // Si quedaría menos del 40 % de la página, mejor no tocarla.
        if (ancho.toLong() * alto < w.toLong() * h * 4 / 10) return b
        if (x0 == 0 && y0 == 0 && ancho == w && alto == h) return b
        return Bitmap.createBitmap(b, x0, y0, ancho, alto)
    }

    // ¿El nombre de archivo parece una imagen que sabemos leer?
    fun esImagen(nombre: String): Boolean =
        nombre.substringAfterLast('.', "").lowercase() in extensiones

    // Lista de páginas ya leída de cada archivo. Sin esto, leer una página online descargada abría el .zip y
    // ordenaba TODOS los nombres otra vez en cada página (trabajo cuadrático en capítulos largos).
    private val listas = object : LinkedHashMap<String, List<String>>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<String>>?): Boolean = size > 12
    }

    // Devuelve los nombres de las páginas, en orden natural (1, 2, 10 y no 1, 10, 2).
    fun paginas(archivo: File): List<String> {
        val clave = "${archivo.path}|${archivo.length()}|${archivo.lastModified()}"
        synchronized(listas) { listas[clave]?.let { return it } }
        val lista = ZipFile(archivo).use { zip ->
            zip.entries().asSequence()
                .filter { !it.isDirectory }
                .map { it.name }
                .filter { !it.startsWith("__MACOSX") }
                .filter { it.substringAfterLast('.', "").lowercase() in extensiones }
                .sortedWith(Comparator { a, b -> compararNatural(a.lowercase(), b.lowercase()) })
                .toList()
        }
        synchronized(listas) { listas[clave] = lista }
        return lista
    }

    // Decodifica una página reduciendo su tamaño si es enorme (ahorra memoria).
    fun decodificar(archivo: File, nombre: String, ladoMax: Int): Bitmap? =
        ZipFile(archivo).use { zip ->
            val entrada = zip.getEntry(nombre) ?: return@use null

            val medidas = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            zip.getInputStream(entrada).use { BitmapFactory.decodeStream(it, null, medidas) }

            var reduccion = 1
            while (maxOf(medidas.outWidth, medidas.outHeight) / (reduccion * 2) >= ladoMax) {
                reduccion *= 2
            }

            val opciones = BitmapFactory.Options().apply { inSampleSize = reduccion }
            zip.getInputStream(entrada).use { BitmapFactory.decodeStream(it, null, opciones) }
        }

    // Decodifica una página limitando el ANCHO (para tiras verticales muy largas).
    // También limita los píxeles totales para no agotar la memoria.
    private fun decodificarAncho(archivo: File, nombre: String, anchoMax: Int): Bitmap? =
        ZipFile(archivo).use { zip ->
            val entrada = zip.getEntry(nombre) ?: return@use null

            val medidas = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            zip.getInputStream(entrada).use { BitmapFactory.decodeStream(it, null, medidas) }
            if (medidas.outWidth <= 0) return@use null

            var reduccion = 1
            while (
                medidas.outWidth / (reduccion * 2) >= anchoMax ||
                (medidas.outWidth / reduccion).toLong() * (medidas.outHeight / reduccion) > 16_000_000L
            ) {
                reduccion *= 2
            }

            val opciones = BitmapFactory.Options().apply {
                inSampleSize = reduccion
                inPreferredConfig = Bitmap.Config.RGB_565 // la mitad de memoria
            }
            zip.getInputStream(entrada).use { BitmapFactory.decodeStream(it, null, opciones) }
        }

    // Decodifica una página y, si es muy alta, la corta en tiras (la GPU no admite imágenes gigantes).
    fun decodificarTiras(archivo: File, nombre: String, anchoMax: Int, altoTira: Int): List<Bitmap> {
        val completo = decodificarAncho(archivo, nombre, anchoMax) ?: return emptyList()
        if (completo.height <= altoTira) return listOf(completo)
        val tiras = ArrayList<Bitmap>()
        var y = 0
        while (y < completo.height) {
            val alto = minOf(altoTira, completo.height - y)
            tiras.add(Bitmap.createBitmap(completo, 0, y, completo.width, alto))
            y += alto
        }
        completo.recycle()
        return tiras
    }

    // Detecta si es un webtoon: la mayoría de las primeras páginas son mucho más altas que anchas.
    fun esWebtoon(archivo: File, paginas: List<String>): Boolean =
        ZipFile(archivo).use { zip ->
            var total = 0
            var largas = 0
            for (nombre in paginas.take(3)) {
                val entrada = zip.getEntry(nombre) ?: continue
                val medidas = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                zip.getInputStream(entrada).use { BitmapFactory.decodeStream(it, null, medidas) }
                if (medidas.outWidth > 0) {
                    total++
                    if (medidas.outHeight.toFloat() / medidas.outWidth > 3f) largas++
                }
            }
            total > 0 && largas * 2 > total
        }

    fun compararNatural(a: String, b: String): Int {
        var i = 0
        var j = 0
        while (i < a.length && j < b.length) {
            if (a[i].isDigit() && b[j].isDigit()) {
                val inicioA = i
                val inicioB = j
                while (i < a.length && a[i].isDigit()) i++
                while (j < b.length && b[j].isDigit()) j++
                val numA = a.substring(inicioA, i).trimStart('0')
                val numB = b.substring(inicioB, j).trimStart('0')
                if (numA.length != numB.length) return numA.length - numB.length
                val c = numA.compareTo(numB)
                if (c != 0) return c
            } else {
                if (a[i] != b[j]) return a[i] - b[j]
                i++
                j++
            }
        }
        return (a.length - i) - (b.length - j)
    }
}
