package com.example.lector.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Rect
import android.os.Build
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.example.lector.Almacen
import com.example.lector.Cbz
import com.example.lector.ext.CapituloFuente
import com.example.lector.ext.FuenteUI
import com.example.lector.ext.MotorFuentes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipFile

/** De dónde salen las páginas de un capítulo: un .cbz del disco o una fuente en línea. */
interface OrigenPaginas {
    /** Identifica el capítulo en las cachés. */
    val id: String
    suspend fun total(): Int
    suspend fun bytes(indice: Int): ByteArray
}

/** Un archivo .cbz/.zip del disco (cómic local o capítulo descargado). */
class OrigenArchivo(private val archivo: File) : OrigenPaginas {
    override val id: String = "f:" + archivo.path + ":" + archivo.length()

    override suspend fun total(): Int = withContext(Dispatchers.IO) { Cbz.paginas(archivo).size }

    override suspend fun bytes(indice: Int): ByteArray = withContext(Dispatchers.IO) {
        val nombre = Cbz.paginas(archivo).getOrNull(indice) ?: error("Página ${indice + 1} fuera de rango")
        ZipFile(archivo).use { z -> z.getInputStream(z.getEntry(nombre)).use { it.readBytes() } }
    }
}

/** Capítulo de una fuente: si está descargado se lee del archivo; si no, de la red (con el cliente de la fuente). */
class OrigenFuente(
    private val almacen: Almacen?,
    private val motor: MotorFuentes,
    private val fuente: FuenteUI,
    private val cap: CapituloFuente,
) : OrigenPaginas {
    override val id: String = "s:${fuente.id}|${cap.url}"

    private fun local(): File? = almacen?.archivoDescarga(fuente.id, cap.url)?.takeIf { it.exists() && it.length() > 0 }

    override suspend fun total(): Int {
        local()?.let { f -> runCatching { OrigenArchivo(f).total() }.getOrNull()?.takeIf { it > 0 }?.let { return it } }
        return motor.cantidadPaginas(fuente, cap)
    }

    override suspend fun bytes(indice: Int): ByteArray {
        local()?.let { f -> runCatching { OrigenArchivo(f).bytes(indice) }.getOrNull()?.let { return it } }
        // Lo ya descargado se guarda en disco: volver a una página no la vuelve a pedir a la red.
        CacheBytes.leer(id, indice)?.let { return it }
        return motor.imagenPagina(fuente, cap, indice).also { CacheBytes.guardar(id, indice, it) }
    }

    /** ¿Hay que traer las páginas de la red? (no si está descargado). */
    val enRed: Boolean get() = local() == null
}

/**
 * Bytes de las páginas leídas en línea, en la caché del móvil (Android la puede vaciar si falta espacio). Una carpeta
 * por capítulo; si pasa de 400 MB se borran los capítulos más viejos.
 */
object CacheBytes {
    private var raiz: File? = null
    private var escrituras = 0

    fun iniciar(ctx: android.content.Context) { if (raiz == null) raiz = File(ctx.cacheDir, "paginas").apply { mkdirs() } }

    private fun carpeta(id: String): File? = raiz?.let {
        val h = java.security.MessageDigest.getInstance("SHA-1").digest(id.toByteArray()).joinToString("") { b -> "%02x".format(b) }.take(20)
        File(it, h)
    }

    fun leer(id: String, i: Int): ByteArray? = carpeta(id)?.let { File(it, "$i") }?.takeIf { it.length() > 0 }?.let { f ->
        runCatching { f.readBytes() }.getOrNull()?.also { f.parentFile?.setLastModified(System.currentTimeMillis()) }
    }

    fun guardar(id: String, i: Int, b: ByteArray) {
        val c = carpeta(id) ?: return
        runCatching {
            c.mkdirs()
            val tmp = File(c, "$i.tmp"); tmp.writeBytes(b); tmp.renameTo(File(c, "$i"))
            c.setLastModified(System.currentTimeMillis())
        }
        if (++escrituras % 40 == 0) recortar()
    }

    @Synchronized private fun recortar(max: Long = 400L * 1024 * 1024) {
        val dirs = raiz?.listFiles()?.filter { it.isDirectory }?.sortedBy { it.lastModified() } ?: return
        var total = dirs.sumOf { d -> d.listFiles()?.sumOf { it.length() } ?: 0L }
        for (d in dirs) {
            if (total <= max) break
            total -= d.listFiles()?.sumOf { it.length() } ?: 0L
            d.deleteRecursively()
        }
    }
}

/** Una página ya decodificada. Las muy altas (webtoon) vienen partidas en tiras que la GPU sí puede dibujar. */
class PaginaLista(val partes: List<ImageBitmap>, val anchoOriginal: Int, val altoOriginal: Int) {
    val ancha: Boolean get() = anchoOriginal > altoOriginal * 1.1f
    val proporcion: Float get() = if (anchoOriginal > 0) altoOriginal.toFloat() / anchoOriginal else 1.45f
    val bytesMemoria: Int get() = partes.sumOf { it.width * it.height * 4 }
}

private const val ALTO_TIRA = 4096

/**
 * Caché de páginas decodificadas (por bytes) con una sola decodificación por página aunque la pidan a la vez la
 * pantalla y la precarga, y como mucho 3 descargas/decodificaciones simultáneas.
 */
object CacheVisor {
    private val cache = object : LruCache<String, PaginaLista>(
        (Runtime.getRuntime().maxMemory() / 4L).coerceIn(32L * 1024 * 1024, 160L * 1024 * 1024).toInt(),
    ) {
        override fun sizeOf(key: String, value: PaginaLista): Int = value.bytesMemoria.coerceAtLeast(1)
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val enVuelo = HashMap<String, Deferred<PaginaLista>>()
    private val limite = Semaphore(3)

    /** Proporción alto/ancho de la última página: reserva el hueco con la altura probable (la tira no "salta"). */
    @Volatile var proporcion = 1.45f

    fun clave(origen: OrigenPaginas, indice: Int, mitad: Int, ancho: Int, recortar: Boolean) =
        "${origen.id}|$indice|$mitad|$ancho|$recortar"

    fun enMemoria(clave: String): PaginaLista? = cache.get(clave)

    suspend fun obtener(
        origen: OrigenPaginas, indice: Int, mitad: Int, anchoMax: Int, recortar: Boolean,
        permitirHardware: Boolean = true,
    ): PaginaLista {
        val k = clave(origen, indice, mitad, anchoMax, recortar)
        cache.get(k)?.let { return it }
        return tareas.esperar(k) {
            val r = limite.withPermit { decodificar(origen.bytes(indice), anchoMax, recortar, mitad, permitirHardware) }
            cache.put(k, r)
            if (mitad == 0) proporcion = r.proporcion.coerceIn(0.5f, 3f)
            r
        }
    }
    private val tareas by lazy { TareasCompartidas<PaginaLista>(scope) }

    fun vaciar() = cache.evictAll()
}

/**
 * Decodifica una página:
 *  - reduce con inSampleSize hasta [anchoMax] de ancho (y como mucho ~24 Mpx);
 *  - [mitad] 1 = mitad izquierda, 2 = mitad derecha (dividir páginas dobles);
 *  - si es muy alta la corta en tiras de 4096 px con BitmapRegionDecoder (sin decodificar la imagen gigante entera);
 *  - con [recortar] quita los márgenes lisos;
 *  - las páginas normales usan bitmaps de hardware (Android 8+): no ocupan memoria de la app y se dibujan más rápido.
 */
suspend fun decodificar(
    bytes: ByteArray, anchoMax: Int, recortar: Boolean, mitad: Int, permitirHardware: Boolean = true,
): PaginaLista = withContext(Dispatchers.Default) {
    val medidas = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, medidas)
    val w = medidas.outWidth
    val h = medidas.outHeight
    if (w <= 0 || h <= 0) error("Imagen no válida")

    val region = when (mitad) {
        1 -> Rect(0, 0, w / 2, h)
        2 -> Rect(w / 2, 0, w, h)
        else -> Rect(0, 0, w, h)
    }
    val rw = region.width()
    val rh = region.height()
    var muestra = 1
    while (rw / (muestra * 2) >= anchoMax || (rw / muestra).toLong() * (rh / muestra) > 24_000_000L) muestra *= 2

    val altoFinal = rh / muestra
    val alta = altoFinal > ALTO_TIRA

    // Página normal y entera: decodificación directa (de hardware si se puede).
    if (!alta && mitad == 0) {
        val usarHardware = permitirHardware && !recortar && Build.VERSION.SDK_INT >= 26
        val op = BitmapFactory.Options().apply {
            inSampleSize = muestra
            if (usarHardware) inPreferredConfig = Bitmap.Config.HARDWARE
        }
        var bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, op)
            ?: BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = muestra })
            ?: error("Imagen no válida")
        if (recortar) bmp = runCatching { Cbz.recortarBordes(bmp) }.getOrDefault(bmp)
        return@withContext PaginaLista(listOf(bmp.asImageBitmap()), w, h)
    }

    // Mitades y tiras: con BitmapRegionDecoder (no admite GIF; en ese caso se decodifica entera y se corta).
    val decoder = runCatching {
        if (Build.VERSION.SDK_INT >= 31) BitmapRegionDecoder.newInstance(bytes, 0, bytes.size)
        else @Suppress("DEPRECATION") BitmapRegionDecoder.newInstance(bytes, 0, bytes.size, false)
    }.getOrNull()

    val partes = ArrayList<ImageBitmap>()
    if (decoder != null) {
        try {
            val op = BitmapFactory.Options().apply { inSampleSize = muestra }
            val paso = ALTO_TIRA * muestra
            var y = region.top
            while (y < region.bottom) {
                val fin = minOf(y + paso, region.bottom)
                val b = decoder.decodeRegion(Rect(region.left, y, region.right, fin), op) ?: break
                val final = if (recortar && !alta) runCatching { Cbz.recortarBordes(b) }.getOrDefault(b) else b
                partes += final.asImageBitmap()
                y = fin
            }
        } finally {
            decoder.recycle()
        }
    }
    if (partes.isEmpty()) {
        val completo = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = muestra })
            ?: error("Imagen no válida")
        val r = Rect(region.left / muestra, 0, (region.right / muestra).coerceAtMost(completo.width), completo.height)
        var y = 0
        while (y < r.bottom) {
            val alto = minOf(ALTO_TIRA, r.bottom - y)
            partes += Bitmap.createBitmap(completo, r.left, y, r.width(), alto).asImageBitmap()
            y += alto
        }
    }
    PaginaLista(partes, if (mitad == 0) w else rw, h)
}

/**
 * Cargas compartidas que se cancelan solas cuando ya nadie las espera (como Coil/Komikku al salir una página de la
 * pantalla): tras un desplazamiento rápido, las páginas que pasaste sueltan su turno y la visible carga antes.
 */
internal class TareasCompartidas<T>(private val scope: CoroutineScope) {
    private class Entrada<T>(var esperando: Int) { lateinit var tarea: Deferred<T> }
    private val mapa = HashMap<String, Entrada<T>>()

    suspend fun esperar(k: String, bloque: suspend () -> T): T {
        val e = synchronized(mapa) {
            mapa.getOrPut(k) {
                Entrada<T>(0).also { n ->
                    n.tarea = scope.async(start = kotlinx.coroutines.CoroutineStart.LAZY) {
                        try { bloque() } finally { synchronized(mapa) { if (mapa[k] === n) mapa.remove(k) } }
                    }
                }
            }.also { it.esperando++ }
        }
        e.tarea.start()
        try {
            return e.tarea.await()
        } finally {
            // La decisión y la retirada van juntas: nadie puede engancharse a una tarea que se va a cancelar.
            val cancelar = synchronized(mapa) {
                e.esperando--
                (e.esperando <= 0 && !e.tarea.isCompleted).also { if (it && mapa[k] === e) mapa.remove(k) }
            }
            if (cancelar) e.tarea.cancel()
        }
    }
}
