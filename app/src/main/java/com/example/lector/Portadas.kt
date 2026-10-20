package com.example.lector

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Portadas guardadas en disco (cómics locales y mangas de la biblioteca), con caché en memoria.
 * Antes cada tarjeta decodificaba su JPEG en el hilo principal en cuanto aparecía (tirones al hacer scroll en
 * bibliotecas grandes); ahora se decodifica en segundo plano, reducida, y se reutiliza.
 */
object Portadas {
    private val memoria = object : LruCache<String, ImageBitmap>(
        (Runtime.getRuntime().maxMemory() / 10L).coerceIn(12L * 1024 * 1024, 64L * 1024 * 1024).toInt(),
    ) {
        override fun sizeOf(key: String, value: ImageBitmap): Int = value.width * value.height * 4
    }

    fun enMemoria(id: String): ImageBitmap? = memoria.get(id)

    fun olvidar(id: String) {
        memoria.remove(id)
    }

    suspend fun cargar(archivo: File, id: String, ladoMax: Int = 600): ImageBitmap? {
        memoria.get(id)?.let { return it }
        return withContext(Dispatchers.IO) {
            if (!archivo.exists()) return@withContext null
            runCatching {
                val medidas = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(archivo.path, medidas)
                var muestra = 1
                while (maxOf(medidas.outWidth, medidas.outHeight) / (muestra * 2) >= ladoMax) muestra *= 2
                BitmapFactory.decodeFile(archivo.path, BitmapFactory.Options().apply { inSampleSize = muestra })
                    ?.asImageBitmap()
            }.getOrNull()?.also { memoria.put(id, it) }
        }
    }
}

/** Portada guardada de una serie (null mientras carga o si no tiene). Se refresca si cambia la biblioteca. */
@Composable
fun rememberPortada(almacen: Almacen, id: String): ImageBitmap? {
    val version = almacen.version
    var img by remember(id) { mutableStateOf(Portadas.enMemoria(id)) }
    LaunchedEffect(id, version) {
        val actual = Portadas.enMemoria(id)
        img = actual ?: Portadas.cargar(almacen.portadaDe(id), id)
    }
    return img
}
