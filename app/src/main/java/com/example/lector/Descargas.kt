package com.example.lector

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.lector.ext.CapituloFuente
import com.example.lector.ext.ExtManager
import com.example.lector.ext.FuenteUI
import com.example.lector.ext.Motor
import com.example.lector.ui.Avisos
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/** Un capítulo en la cola de descargas. */
data class TareaDescarga(
    val fuenteId: Long,
    val mangaUrl: String,
    val mangaTitulo: String,
    val capUrl: String,
    val capNombre: String,
    val capNumero: Float,
    val capFecha: Long,
    val scanlator: String?,
) {
    val clave: String get() = "$fuenteId|$capUrl"
    fun capitulo() = CapituloFuente(capUrl, capNombre, capFecha, scanlator, capNumero)
}

/**
 * Cola de descargas (como el gestor de descargas de Komikku):
 *  - persistente: si cierras la app (o Android la cierra), al volver sigue donde iba;
 *  - corre en un trabajo de WorkManager en primer plano con notificación de progreso;
 *  - descargas simultáneas configurables, pausa, reanudar, cancelar, reintentar y reordenar;
 *  - opción de descargar solo con Wi-Fi.
 * Las pantallas leen [cola], [progreso] y [errores] (estado de Compose).
 */
object Descargas {
    val cola = mutableStateListOf<TareaDescarga>()
    /** clave → fracción descargada (0f = preparando) de las que están en curso. */
    val progreso = mutableStateMapOf<String, Float>()
    /** clave → motivo, de las que fallaron (se quedan en la cola hasta reintentar o quitar). */
    val errores = mutableStateMapOf<String, String>()
    /** Sube cada vez que termina o se borra una descarga, para que las pantallas vuelvan a mirar el disco. */
    val version = mutableStateOf(0)
    var pausado by mutableStateOf(false)
        private set

    private const val TRABAJO = "descargas"
    private val trabajos = ConcurrentHashMap<String, Job>()
    @Volatile private var cargada = false

    fun clave(f: FuenteUI, c: CapituloFuente) = "${f.id}|${c.url}"

    fun enCola(clave: String) = cola.any { it.clave == clave }

    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences("lector_descargas", Context.MODE_PRIVATE)

    /** Lee la cola guardada (una vez por proceso). */
    fun cargar(ctx: Context) {
        if (cargada) return
        synchronized(this) {
            if (cargada) return
            val p = prefs(ctx)
            pausado = p.getBoolean("pausado", false)
            runCatching {
                val a = JSONArray(p.getString("cola", "[]"))
                val lista = (0 until a.length()).map { i ->
                    val o = a.getJSONObject(i)
                    TareaDescarga(
                        o.getLong("f"), o.getString("m"), o.optString("t"), o.getString("c"), o.optString("n"),
                        o.optDouble("num", -1.0).toFloat(), o.optLong("d"), o.optString("s").ifBlank { null },
                    )
                }
                cola.clear(); cola.addAll(lista.distinctBy { it.clave })
            }
            cargada = true
        }
    }

    private fun guardar(ctx: Context) {
        val a = JSONArray()
        cola.toList().forEach {
            a.put(
                JSONObject().put("f", it.fuenteId).put("m", it.mangaUrl).put("t", it.mangaTitulo).put("c", it.capUrl)
                    .put("n", it.capNombre).put("num", it.capNumero.toDouble()).put("d", it.capFecha).put("s", it.scanlator ?: ""),
            )
        }
        prefs(ctx).edit().putString("cola", a.toString()).putBoolean("pausado", pausado).apply()
    }

    /** Pone capítulos en la cola (salta los ya descargados o en cola) y arranca el trabajo. Devuelve cuántos. */
    fun encolar(
        ctx: Context, f: FuenteUI, mangaUrl: String, mangaTitulo: String, caps: List<CapituloFuente>, avisar: Boolean = true,
    ): Int {
        cargar(ctx)
        val alm = Almacen.obtener(ctx)
        val nuevas = caps.filter { c -> !alm.estaDescargado(f.id, c.url) && !enCola("${f.id}|${c.url}") }
            .map { TareaDescarga(f.id, mangaUrl, mangaTitulo, it.url, it.nombre, it.numero, it.fecha, it.scanlator) }
        if (nuevas.isEmpty()) {
            if (avisar) Avisos.mostrar("Ya están descargados")
            return 0
        }
        cola.addAll(nuevas)
        nuevas.forEach { errores.remove(it.clave) }
        guardar(ctx)
        if (avisar) Avisos.mostrar(if (nuevas.size == 1) "Capítulo en la cola de descargas" else "${nuevas.size} capítulos en la cola de descargas")
        arrancar(ctx)
        return nuevas.size
    }

    /** Arranca (o encadena) el trabajo de fondo que procesa la cola. */
    fun arrancar(ctx: Context) {
        cargar(ctx)
        if (pausado || cola.none { it.clave !in errores }) return
        val alm = Almacen.obtener(ctx)
        val req = OneTimeWorkRequestBuilder<TrabajoDescargas>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(if (alm.descSoloWifi) NetworkType.UNMETERED else NetworkType.CONNECTED)
                    .build(),
            )
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
        // APPEND_OR_REPLACE: si ya hay uno trabajando, este va detrás (así nada se queda sin procesar).
        WorkManager.getInstance(ctx).enqueueUniqueWork(TRABAJO, ExistingWorkPolicy.APPEND_OR_REPLACE, req)
    }

    fun cancelar(ctx: Context, clave: String) {
        trabajos.remove(clave)?.cancel()
        progreso.remove(clave)
        errores.remove(clave)
        cola.removeAll { it.clave == clave }
        guardar(ctx)
        version.value++
    }

    fun cancelarTodo(ctx: Context) {
        trabajos.values.forEach { it.cancel() }
        trabajos.clear()
        progreso.clear()
        errores.clear()
        cola.clear()
        guardar(ctx)
        WorkManager.getInstance(ctx).cancelUniqueWork(TRABAJO)
        Notificaciones.quitar(ctx, Notificaciones.ID_DESCARGAS)
        version.value++
    }

    fun pausar(ctx: Context) {
        pausado = true
        trabajos.values.forEach { it.cancel() }
        guardar(ctx)
    }

    fun reanudar(ctx: Context) {
        pausado = false
        guardar(ctx)
        arrancar(ctx)
    }

    fun reintentar(ctx: Context, clave: String? = null) {
        if (clave == null) errores.clear() else errores.remove(clave)
        arrancar(ctx)
    }

    /** Mueve una descarga al principio o al final de la cola. */
    fun mover(ctx: Context, clave: String, alPrincipio: Boolean) {
        val i = cola.indexOfFirst { it.clave == clave }
        if (i < 0) return
        val t = cola.removeAt(i)
        if (alPrincipio) cola.add(0, t) else cola.add(t)
        guardar(ctx)
    }

    /** El bucle que descarga. Lo ejecuta [TrabajoDescargas]. */
    internal suspend fun procesar(ctx: Context, notificar: suspend (titulo: String, texto: String, hechas: Int, total: Int) -> Unit) {
        cargar(ctx)
        val alm = Almacen.obtener(ctx)
        val ext = ExtManager.obtener(ctx)
        ext.esperarListo()
        var hechas = 0
        coroutineScope {
            while (true) {
                if (pausado) break
                val motor = Motor.actual ?: break
                val maximo = alm.descParalelas.coerceIn(1, 4)
                val siguientes = cola.toList().filter { !trabajos.containsKey(it.clave) && it.clave !in errores }.take((maximo - trabajos.size).coerceAtLeast(0))
                if (siguientes.isEmpty() && trabajos.isEmpty()) break
                for (t in siguientes) {
                    progreso[t.clave] = 0f
                    // LAZY: el trabajo se registra ANTES de empezar (si no, uno muy rápido podría terminar antes y
                    // quedarse "registrado" para siempre).
                    val job = launch(start = kotlinx.coroutines.CoroutineStart.LAZY) {
                        val tmp = File(ctx.cacheDir, "cap_${System.nanoTime()}.cbz")
                        try {
                            val f = ext.fuentes.firstOrNull { it.id == t.fuenteId } ?: error("La fuente ya no está disponible")
                            motor.descargarCbz(f, t.capitulo(), tmp) { h, tot -> if (tot > 0) progreso[t.clave] = h.toFloat() / tot }
                            alm.guardarDescarga(tmp, t.fuenteId, t.capUrl, t.mangaUrl)
                            cola.removeAll { it.clave == t.clave }
                            guardar(ctx)
                            hechas++
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            errores[t.clave] = e.message ?: e.javaClass.simpleName
                        } finally {
                            tmp.delete()
                            progreso.remove(t.clave)
                            trabajos.remove(t.clave)
                            version.value++
                        }
                    }
                    trabajos[t.clave] = job
                    job.start()
                }
                val enCurso = cola.firstOrNull { trabajos.containsKey(it.clave) }
                val pendientes = cola.count { it.clave !in errores }
                notificar(
                    "Descargando ($pendientes en cola)",
                    enCurso?.let { "${it.mangaTitulo} · ${it.capNombre}" } ?: "Preparando…",
                    hechas, hechas + pendientes,
                )
                delay(400)
            }
        }
        if (hechas > 0) {
            val fallidas = errores.size
            Notificaciones.mostrar(
                ctx, Notificaciones.ID_DESCARGAS_FIN,
                Notificaciones.base(ctx, Notificaciones.CANAL_DESCARGAS)
                    .setContentTitle("Descargas terminadas")
                    .setContentText("$hechas capítulo(s) descargado(s)" + if (fallidas > 0) " · $fallidas con error" else "")
                    .setContentIntent(Notificaciones.abrirApp(ctx, Notificaciones.DESTINO_DESCARGAS, Notificaciones.ID_DESCARGAS_FIN))
                    .setAutoCancel(true)
                    .build(),
            )
        }
    }
}

/** Trabajo de WorkManager que vacía la cola de descargas (en primer plano, con notificación). */
class TrabajoDescargas(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    private fun info(titulo: String, texto: String, hechas: Int, total: Int): ForegroundInfo =
        Notificaciones.primerPlano(
            Notificaciones.ID_DESCARGAS,
            Notificaciones.progreso(
                applicationContext, Notificaciones.CANAL_DESCARGAS, titulo, texto, hechas, total,
                Notificaciones.DESTINO_DESCARGAS, Notificaciones.ID_DESCARGAS,
            ),
        )

    override suspend fun getForegroundInfo(): ForegroundInfo = info("Descargando capítulos", "Preparando…", 0, 0)

    override suspend fun doWork(): Result {
        Notificaciones.crearCanales(applicationContext)
        runCatching { setForeground(getForegroundInfo()) }
        var ultimo = 0L
        Descargas.procesar(applicationContext) { titulo, texto, hechas, total ->
            val ahora = System.currentTimeMillis()
            if (ahora - ultimo > 900) {
                ultimo = ahora
                runCatching { setForeground(info(titulo, texto, hechas, total)) }
            }
        }
        Notificaciones.quitar(applicationContext, Notificaciones.ID_DESCARGAS)
        return Result.success()
    }
}
