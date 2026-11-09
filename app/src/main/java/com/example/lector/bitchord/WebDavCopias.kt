// Adaptado de BitChord (https://github.com/kushagrasinghx/BitChord, GPL-3.0): app/src/main/java/com/music/bitchord/data/webdav/WebDavRepository.kt
// y WebDavUploads.kt (subidas a una carpeta WebDAV con MKCOL + PUT). En Shiori se suben y restauran copias de seguridad.
package com.example.lector.bitchord

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.lector.Almacen
import com.example.lector.CopiaSeguridad
import com.example.lector.Tachibk
import com.example.lector.TareaCopia
import java.util.concurrent.TimeUnit

/** Ajustes del servidor WebDAV (preferencias privadas de la app; no van en las copias de seguridad). */
class WebDavAjustes(ctx: Context) {
    private val p = ctx.applicationContext.getSharedPreferences("bitchord_webdav", Context.MODE_PRIVATE)

    var url: String
        get() = p.getString("url", "").orEmpty()
        set(v) = p.edit().putString("url", v.trim()).apply()
    var usuario: String
        get() = p.getString("usuario", "").orEmpty()
        set(v) = p.edit().putString("usuario", v.trim()).apply()
    var clave: String
        get() = p.getString("clave", "").orEmpty()
        set(v) = p.edit().putString("clave", v).apply()
    var carpeta: String
        get() = p.getString("carpeta", "Shiori").orEmpty()
        set(v) = p.edit().putString("carpeta", v.trim().trim('/')).apply()
    /** Subir sola cada copia automática nueva. */
    var autoSubir: Boolean
        get() = p.getBoolean("auto", false)
        set(v) = p.edit().putBoolean("auto", v).apply()
    /** Cuándo se subió la última copia (ms). */
    var ultimaSubida: Long
        get() = p.getLong("ultima_subida", 0L)
        set(v) = p.edit().putLong("ultima_subida", v).apply()

    val configurado: Boolean get() = WebDavClient.isConfigured(url)

    /** La carpeta donde van las copias: `<url>/<carpeta>` (o la url tal cual si no hay carpeta). */
    fun directorio(): String {
        val base = WebDavClient.normalizeUrl(url)
        return if (carpeta.isBlank()) base else carpeta.split('/').filter { it.isNotBlank() }.fold(base) { a, s -> WebDavClient.joinUrl(a, s) }
    }
}

object WebDavCopias {

    private fun esCopia(nombre: String) =
        nombre.startsWith("lector-copia-") || nombre.endsWith(".tachibk") || nombre.endsWith(".proto.gz")

    /** Crea una copia (formato de Shiori, .json) y la sube. Devuelve el nombre subido. */
    suspend fun subir(ctx: Context, avance: TareaCopia.Avance? = null): String {
        val aj = WebDavAjustes(ctx)
        require(aj.configurado) { "Configura primero el servidor WebDAV" }
        avance?.paso(0.1f, "Reuniendo la biblioteca…")
        val texto = CopiaSeguridad.crear(ctx)
        val bytes = texto.toByteArray(Charsets.UTF_8)
        // Crea la carpeta (y las intermedias) si faltan.
        val base = WebDavClient.normalizeUrl(aj.url)
        var dir = base
        aj.carpeta.split('/').filter { it.isNotBlank() }.forEach { s ->
            dir = WebDavClient.joinUrl(dir, s)
            WebDavClient.ensureCollection(dir, aj.usuario, aj.clave).getOrThrow()
        }
        val nombre = CopiaSeguridad.nombreArchivo()
        avance?.paso(0.4f, "Subiendo $nombre…")
        WebDavClient.putBytes(WebDavClient.joinUrl(dir, nombre), bytes, "application/json", aj.usuario, aj.clave) { n ->
            avance?.paso(0.4f + 0.5f * n / bytes.size.coerceAtLeast(1), "Subiendo $nombre…")
        }.getOrThrow()
        aj.ultimaSubida = System.currentTimeMillis()
        // Deja en el servidor solo las más recientes (las mismas que se guardan en local).
        runCatching {
            val max = Almacen.obtener(ctx).copiasMax.coerceIn(1, 50)
            WebDavClient.list(dir, aj.usuario, aj.clave).getOrThrow()
                .filter { !it.isCollection && it.displayName.startsWith("lector-copia-") }
                .sortedByDescending { it.displayName }
                .drop(max)
                .forEach { WebDavClient.delete(it.url, aj.usuario, aj.clave) }
        }
        return nombre
    }

    /** Las copias que hay en el servidor, la más nueva primero. */
    suspend fun listar(ctx: Context): List<WebDavClient.Entry> {
        val aj = WebDavAjustes(ctx)
        require(aj.configurado) { "Configura primero el servidor WebDAV" }
        return WebDavClient.list(aj.directorio(), aj.usuario, aj.clave).getOrThrow()
            .filter { !it.isCollection && esCopia(it.displayName) }
            .sortedWith(compareByDescending<WebDavClient.Entry> { it.lastModified }.thenByDescending { it.displayName })
    }

    /** Baja y restaura [entrada] (o la más reciente). Acepta copias de Shiori (.json) y de Mihon/Komikku (.tachibk). */
    suspend fun restaurar(ctx: Context, avance: TareaCopia.Avance, entrada: WebDavClient.Entry? = null): TareaCopia.Informe {
        val aj = WebDavAjustes(ctx)
        avance.paso(0.02f, "Buscando la copia en el servidor…")
        val e = entrada ?: listar(ctx).firstOrNull() ?: error("No hay copias en ${aj.directorio()}")
        avance.paso(0.05f, "Descargando ${e.displayName}…")
        val bytes = WebDavClient.getBytes(e.url, aj.usuario, aj.clave).getOrThrow()
        return if (Tachibk.esTachibk(bytes)) {
            Tachibk.restaurar(ctx, bytes, avance)
        } else {
            val texto = Tachibk.descomprimirSiHace(bytes).toString(Charsets.UTF_8)
            runCatching { CopiaSeguridad.restaurarConInforme(ctx, texto, avance) }
                .getOrElse { throw IllegalStateException("${e.displayName} no es una copia válida (${it.message ?: it.javaClass.simpleName})") }
        }
    }

    // ── Subida automática tras las copias automáticas ──

    private const val TRABAJO = "bitchord_webdav_auto"

    /** Programa (o quita) la revisión periódica que sube las copias automáticas nuevas. */
    fun programar(ctx: Context) {
        val wm = WorkManager.getInstance(ctx)
        val aj = WebDavAjustes(ctx)
        if (aj.autoSubir && aj.configurado) {
            val req = PeriodicWorkRequestBuilder<TrabajoWebDav>(6, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            wm.enqueueUniquePeriodicWork(TRABAJO, ExistingPeriodicWorkPolicy.UPDATE, req)
        } else {
            wm.cancelUniqueWork(TRABAJO)
        }
    }

    /** Si hubo una copia automática después de la última subida, sube una copia nueva. */
    suspend fun subirSiHaceFalta(ctx: Context): Boolean {
        val aj = WebDavAjustes(ctx)
        if (!aj.autoSubir || !aj.configurado) return false
        val ultimaCopia = Almacen.obtener(ctx).ultimaCopia
        if (ultimaCopia <= 0L || ultimaCopia <= aj.ultimaSubida) return false
        subir(ctx)
        return true
    }
}

class TrabajoWebDav(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result =
        runCatching { WebDavCopias.subirSiHaceFalta(applicationContext) }
            .fold({ Result.success() }, { if (runAttemptCount < 2) Result.retry() else Result.failure() })
}
