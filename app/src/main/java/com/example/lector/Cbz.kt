package com.example.lector

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.util.zip.ZipFile

// Un archivo .cbz es un .zip con imágenes dentro. Aquí lo leemos.
object Cbz {
    private val extensiones = setOf("jpg", "jpeg", "png", "webp", "gif")

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

    private fun compararNatural(a: String, b: String): Int {
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
