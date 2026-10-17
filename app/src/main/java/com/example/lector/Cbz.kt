package com.example.lector

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.util.zip.ZipFile

// Un archivo .cbz es un .zip con imágenes dentro. Aquí lo leemos.
object Cbz {
    private val extensiones = setOf("jpg", "jpeg", "png", "webp", "gif")

    // ¿El nombre de archivo parece una imagen que sabemos leer?
    fun esImagen(nombre: String): Boolean =
        nombre.substringAfterLast('.', "").lowercase() in extensiones

    // Devuelve los nombres de las páginas, en orden natural (1, 2, 10 y no 1, 10, 2).
    fun paginas(archivo: File): List<String> =
        ZipFile(archivo).use { zip ->
            zip.entries().asSequence()
                .filter { !it.isDirectory }
                .map { it.name }
                .filter { !it.startsWith("__MACOSX") }
                .filter { it.substringAfterLast('.', "").lowercase() in extensiones }
                .sortedWith(Comparator { a, b -> compararNatural(a.lowercase(), b.lowercase()) })
                .toList()
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
