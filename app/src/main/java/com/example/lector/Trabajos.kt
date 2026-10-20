package com.example.lector

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.lector.ext.ExtManager
import com.example.lector.ext.MangaFuente
import com.example.lector.ext.Motor
import com.example.lector.ui.Avisos
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** Estado de la búsqueda de capítulos nuevos (lo leen las pantallas: «Revisando 3 de 20…»). */
object Actualizador {
    var corriendo by mutableStateOf(false)
        internal set
    var hechas by mutableIntStateOf(0)
        internal set
    var total by mutableIntStateOf(0)
        internal set
    var actual by mutableStateOf<String?>(null)
        internal set

    /** Cada cuánto se revisa sola al abrir la pestaña Actualizaciones (si no hay revisión automática). */
    const val INTERVALO_MS = 6L * 60 * 60 * 1000

    private const val AHORA = "actualizar_ahora"

    /** Busca capítulos nuevos ya (en un trabajo de fondo: sigue aunque salgas de la app). [categoria] = solo esa. */
    fun iniciar(ctx: Context, categoria: String? = null, avisar: Boolean = true) {
        if (corriendo) return
        corriendo = true
        hechas = 0; total = 0; actual = null
        val req = OneTimeWorkRequestBuilder<TrabajoActualizar>()
            .setInputData(workDataOf("categoria" to categoria, "manual" to true, "avisar" to avisar))
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
        WorkManager.getInstance(ctx).enqueueUniqueWork(AHORA, ExistingWorkPolicy.KEEP, req)
    }

    fun cancelar(ctx: Context) {
        WorkManager.getInstance(ctx).cancelUniqueWork(AHORA)
        corriendo = false
    }
}

data class ResultadoRevision(val nuevos: Map<String, Int>, val fallos: Int, val revisados: Int)

/**
 * Revisa los mangas en línea de la biblioteca buscando capítulos nuevos (como LibraryUpdateJob de Komikku):
 * anota las novedades, actualiza autor/estado/géneros, «leídos / total», la fecha de revisión, guarda los errores
 * y, si está activado, pone en la cola de descargas los capítulos nuevos.
 */
object RevisorBiblioteca {
    suspend fun revisar(
        ctx: Context, categoria: String?,
        alProgreso: suspend (hechas: Int, total: Int, titulo: String?) -> Unit,
    ): ResultadoRevision = withContext(Dispatchers.IO) {
        val alm = Almacen.obtener(ctx)
        val ext = ExtManager.obtener(ctx)
        ext.esperarListo()
        val motor = Motor.actual ?: error("El motor de fuentes no está listo")
        val asign = alm.asignaciones()
        val lista = alm.cargar().filter { c ->
            c.enLinea &&
                (categoria == null || categoria in asign[c.id].orEmpty()) &&
                // Saltar los terminados (estado «Completado» y nada por leer), como la opción de Komikku.
                !(alm.actSaltarTerminados && c.estado == 2 && c.sinLeer == 0 && c.paginas > 0)
        }
        Actualizador.total = lista.size
        Actualizador.hechas = 0
        val hechas = AtomicInteger(0)
        val nuevos = java.util.concurrent.ConcurrentHashMap<String, Int>()
        val errores = java.util.Collections.synchronizedList(ArrayList<ErrorActualizacion>())
        val limite = Semaphore(4)
        val fuentes = ext.fuentes
        coroutineScope {
            lista.map { c ->
                async {
                    limite.withPermit {
                        val url = c.urlManga.orEmpty()
                        val f = fuentes.firstOrNull { it.id == c.fuenteId }
                        try {
                            if (f == null) error("La fuente no está disponible (¿extensión desinstalada o sin confianza?)")
                            Actualizador.actual = c.titulo
                            val ficha = motor.ficha(f, MangaFuente(url, c.titulo, c.portadaUrl))
                            val portada = ficha.detalle.portadaUrl ?: c.portadaUrl
                            val n = alm.registrarCapitulos(c.fuenteId, url, c.titulo, portada, ficha.capitulos)
                            alm.actualizarProgresoEnLinea(
                                c.fuenteId, url, ficha.capitulos.count { ext.esLeido(c.fuenteId, it.url) }, ficha.capitulos.size,
                            )
                            alm.actualizarInfoEnLinea(
                                c.fuenteId, url, ficha.detalle.autor, ficha.detalle.estado, ficha.detalle.generos,
                                ficha.detalle.descripcion, ficha.detalle.portadaUrl, ficha.capitulos.maxOfOrNull { it.fecha } ?: 0L,
                            )
                            alm.actualizarComic(c.id) { it.copy(ultimaRevision = System.currentTimeMillis()) }
                            if (n.isNotEmpty()) {
                                nuevos[c.titulo] = n.size
                                if (alm.descargarNuevos) withContext(Dispatchers.Main) {
                                    Descargas.encolar(ctx, f, url, c.titulo, n.asReversed(), avisar = false)
                                }
                            }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            errores += ErrorActualizacion(c.titulo, f?.nombre ?: "Fuente ${c.fuenteId}", e.message ?: e.javaClass.simpleName)
                        } finally {
                            val h = hechas.incrementAndGet()
                            Actualizador.hechas = h
                            alProgreso(h, lista.size, c.titulo)
                        }
                    }
                }
            }.awaitAll()
        }
        alm.guardarErrores(errores.toList())
        alm.marcarActualizado()
        ResultadoRevision(nuevos.toMap(), errores.size, lista.size)
    }

    /** Notificación de «capítulos nuevos» con la lista de mangas (como la de Komikku). */
    fun notificarNuevos(ctx: Context, r: ResultadoRevision) {
        val total = r.nuevos.values.sum()
        if (total == 0) return
        val estilo = NotificationCompat.InboxStyle()
        r.nuevos.entries.sortedByDescending { it.value }.take(6).forEach { (t, n) -> estilo.addLine("$t · $n nuevo(s)") }
        if (r.nuevos.size > 6) estilo.setSummaryText("y ${r.nuevos.size - 6} más")
        Notificaciones.mostrar(
            ctx, Notificaciones.ID_CAPITULOS,
            Notificaciones.base(ctx, Notificaciones.CANAL_CAPITULOS)
                .setContentTitle(if (total == 1) "1 capítulo nuevo" else "$total capítulos nuevos")
                .setContentText(r.nuevos.keys.take(3).joinToString(", "))
                .setStyle(estilo)
                .setNumber(total)
                .setContentIntent(Notificaciones.abrirApp(ctx, Notificaciones.DESTINO_ACTUALIZACIONES, Notificaciones.ID_CAPITULOS))
                .setAutoCancel(true)
                .build(),
        )
    }
}

/** Trabajo de fondo: revisión de la biblioteca (manual o periódica). */
class TrabajoActualizar(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    private fun info(hechas: Int, total: Int, titulo: String?): ForegroundInfo =
        Notificaciones.primerPlano(
            Notificaciones.ID_ACTUALIZANDO,
            Notificaciones.progreso(
                applicationContext, Notificaciones.CANAL_PROGRESO,
                if (total > 0) "Buscando capítulos nuevos ($hechas/$total)" else "Buscando capítulos nuevos",
                titulo ?: "", hechas, total, Notificaciones.DESTINO_ACTUALIZACIONES, Notificaciones.ID_ACTUALIZANDO,
            ),
        )

    override suspend fun getForegroundInfo(): ForegroundInfo = info(0, 0, null)

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        Notificaciones.crearCanales(ctx)
        val manual = inputData.getBoolean("manual", false)
        val avisar = inputData.getBoolean("avisar", true)
        val categoria = inputData.getString("categoria")
        Actualizador.corriendo = true
        runCatching { setForeground(getForegroundInfo()) }
        return try {
            var ultimo = 0L
            val r = RevisorBiblioteca.revisar(ctx, categoria) { h, t, titulo ->
                val ahora = System.currentTimeMillis()
                if (ahora - ultimo > 700 || h == t) {
                    ultimo = ahora
                    runCatching { setForeground(info(h, t, titulo)) }
                }
            }
            RevisorBiblioteca.notificarNuevos(ctx, r)
            if (manual && avisar) {
                val total = r.nuevos.values.sum()
                Avisos.mostrar(
                    buildString {
                        append(
                            when {
                                r.revisados == 0 -> "Tu biblioteca no tiene mangas de fuentes en línea"
                                total == 0 -> "Sin capítulos nuevos"
                                else -> "$total capítulo(s) nuevo(s)"
                            },
                        )
                        if (r.fallos > 0) append(" · ${r.fallos} no se pudieron revisar")
                    },
                )
            }
            // Las revisiones automáticas también miran si hay extensiones nuevas (como mucho cada 12 h).
            if (!manual) RevisorExtensiones.revisarSiToca(ctx)
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (manual && avisar) Avisos.mostrar("No se pudo revisar: ${e.message ?: "error"}")
            Result.failure()
        } finally {
            Actualizador.corriendo = false
            Actualizador.actual = null
            Notificaciones.quitar(ctx, Notificaciones.ID_ACTUALIZANDO)
        }
    }
}

/** Avisa de extensiones con versión nueva (como el aviso de Komikku). */
object RevisorExtensiones {
    suspend fun revisarSiToca(ctx: Context) {
        val alm = Almacen.obtener(ctx)
        if (!alm.revisarExtensiones) return
        if (System.currentTimeMillis() - alm.ultimaRevisionExt < 12L * 60 * 60 * 1000) return
        revisar(ctx)
    }

    suspend fun revisar(ctx: Context) {
        val alm = Almacen.obtener(ctx)
        val ext = ExtManager.obtener(ctx)
        ext.esperarListo()
        runCatching { ext.actualizarListaAhora() }
        alm.ultimaRevisionExt = System.currentTimeMillis()
        val con = ext.conActualizacion()
        if (con.isEmpty()) {
            Notificaciones.quitar(ctx, Notificaciones.ID_EXTENSIONES)
            return
        }
        Notificaciones.mostrar(
            ctx, Notificaciones.ID_EXTENSIONES,
            Notificaciones.base(ctx, Notificaciones.CANAL_EXTENSIONES)
                .setContentTitle(if (con.size == 1) "1 extensión con actualización" else "${con.size} extensiones con actualización")
                .setContentText(con.take(4).joinToString(", ") { it.nombre })
                .setContentIntent(Notificaciones.abrirApp(ctx, Notificaciones.DESTINO_EXTENSIONES, Notificaciones.ID_EXTENSIONES))
                .setAutoCancel(true)
                .build(),
        )
    }
}

class TrabajoExtensiones(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        Notificaciones.crearCanales(applicationContext)
        runCatching { RevisorExtensiones.revisarSiToca(applicationContext) }
        return Result.success()
    }
}

/** Copia de seguridad automática (Ajustes → Datos). */
class TrabajoCopia(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        runCatching { CopiaSeguridad.guardarAutomatica(applicationContext) }
            .fold({ Result.success() }, { if (runAttemptCount < 2) Result.retry() else Result.failure() })
    }
}

/** Programa (o quita) los trabajos periódicos según los ajustes. Se llama al abrir la app y al cambiar un ajuste. */
object Programador {
    private const val PERIODICO = "actualizar_periodico"
    private const val EXTENSIONES = "revisar_extensiones"
    private const val COPIA = "copia_automatica"

    fun programar(ctx: Context) {
        val alm = Almacen.obtener(ctx)
        val wm = WorkManager.getInstance(ctx)

        val horas = alm.intervaloActualizacion
        if (horas > 0) {
            val req = PeriodicWorkRequestBuilder<TrabajoActualizar>(horas.toLong(), TimeUnit.HOURS, 30, TimeUnit.MINUTES)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(if (alm.actSoloWifi) NetworkType.UNMETERED else NetworkType.CONNECTED)
                        .setRequiresCharging(alm.actSoloCargando)
                        .setRequiresBatteryNotLow(true)
                        .build(),
                )
                .setInputData(workDataOf("manual" to false))
                .setInitialDelay(horas.toLong(), TimeUnit.HOURS)
                .build()
            wm.enqueueUniquePeriodicWork(PERIODICO, ExistingPeriodicWorkPolicy.UPDATE, req)
        } else {
            wm.cancelUniqueWork(PERIODICO)
        }

        if (alm.revisarExtensiones) {
            val req = PeriodicWorkRequestBuilder<TrabajoExtensiones>(12, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            wm.enqueueUniquePeriodicWork(EXTENSIONES, ExistingPeriodicWorkPolicy.KEEP, req)
        } else {
            wm.cancelUniqueWork(EXTENSIONES)
        }

        val dias = alm.copiaAutoDias
        if (dias > 0) {
            val req = PeriodicWorkRequestBuilder<TrabajoCopia>(dias.toLong(), TimeUnit.DAYS)
                .setConstraints(Constraints.Builder().setRequiresStorageNotLow(true).build())
                .build()
            wm.enqueueUniquePeriodicWork(COPIA, ExistingPeriodicWorkPolicy.UPDATE, req)
        } else {
            wm.cancelUniqueWork(COPIA)
        }

        // Descargas que quedaron pendientes (app cerrada a mitad).
        Descargas.arrancar(ctx)
    }
}
