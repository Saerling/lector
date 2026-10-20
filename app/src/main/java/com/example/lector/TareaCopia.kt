package com.example.lector

import android.content.Context
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Crear o restaurar una copia de seguridad en segundo plano, con su avance visible en la app (la píldora de arriba)
 * y en una notificación, y un informe al terminar (lo que salió bien, avisos y errores).
 * Sigue aunque cambies de pantalla. Solo puede haber una a la vez.
 */
object TareaCopia {

    data class Estado(val titulo: String, val fraccion: Float, val detalle: String)

    data class Informe(
        val titulo: String,
        val bien: List<String>,
        val avisos: List<String> = emptyList(),
        val errores: List<String> = emptyList(),
        val duracionMs: Long = 0,
    ) {
        val conErrores get() = errores.isNotEmpty()
    }

    /** Avance actual (null = no hay ninguna en marcha). */
    var estado by mutableStateOf<Estado?>(null); private set

    /** Último informe (se enseña en la píldora hasta que se mira o pasa un rato). */
    var informe by mutableStateOf<Informe?>(null); private set
    var informeVisto by mutableStateOf(true)
    var verInforme by mutableStateOf(false)

    /** Sube cada vez que termina una: las pantallas releen la biblioteca. */
    var terminadas by mutableIntStateOf(0); private set

    val ocupada: Boolean get() = estado != null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Lo que recibe el trabajo para contar su avance (las actualizaciones se espacian solas). */
    class Avance internal constructor(private val ctx: Context, private val titulo: String) {
        private var ultimaUi = 0L
        private var ultimaNotif = 0L
        private var ultimaFraccion = -1f

        fun paso(fraccion: Float, detalle: String) {
            val f = fraccion.coerceIn(0f, 1f)
            val ahora = SystemClock.elapsedRealtime()
            if (f < 1f && f - ultimaFraccion < 0.01f && ahora - ultimaUi < 120) return
            ultimaFraccion = f
            ultimaUi = ahora
            TareaCopia.estado = Estado(titulo, f, detalle)
            // Android limita las notificaciones por segundo: se actualiza como mucho ~3 veces por segundo.
            if (ahora - ultimaNotif > 350 || f >= 1f) {
                ultimaNotif = ahora
                Notificaciones.mostrar(
                    ctx, Notificaciones.ID_COPIA,
                    Notificaciones.progreso(ctx, Notificaciones.CANAL_COPIAS, titulo, detalle, (f * 1000).toInt(), 1000, DESTINO, Notificaciones.ID_COPIA),
                )
            }
        }
    }

    const val DESTINO = "copia"

    /** Lanza el trabajo si no hay otro en marcha. Devuelve false si ya había uno. */
    fun lanzar(ctx: Context, titulo: String, trabajo: suspend Avance.() -> Informe): Boolean {
        if (ocupada) return false
        val app = ctx.applicationContext
        estado = Estado(titulo, 0f, "Preparando…")
        scope.launch {
            val inicio = SystemClock.elapsedRealtime()
            val r = try {
                Avance(app, titulo).trabajo()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Informe(titulo, emptyList(), errores = listOf(e.message ?: e.javaClass.simpleName))
            }.copy(duracionMs = SystemClock.elapsedRealtime() - inicio)
            informe = r
            informeVisto = false
            estado = null
            terminadas++
            notificarFin(app, r)
        }
        return true
    }

    private fun notificarFin(ctx: Context, r: Informe) {
        val resumen = buildString {
            r.bien.take(4).forEach { appendLine("✓ $it") }
            r.avisos.take(3).forEach { appendLine("! $it") }
            r.errores.take(3).forEach { appendLine("✕ $it") }
        }.trim()
        val titulo = if (r.conErrores) "${r.titulo}: con errores" else "${r.titulo}: listo"
        val n = Notificaciones.base(ctx, Notificaciones.CANAL_COPIAS)
            .setContentTitle(titulo)
            .setContentText(resumen.lineSequence().firstOrNull().orEmpty())
            .setStyle(NotificationCompat.BigTextStyle().bigText(resumen))
            .setAutoCancel(true)
            .setContentIntent(Notificaciones.abrirApp(ctx, DESTINO, Notificaciones.ID_COPIA))
            .build()
        Notificaciones.mostrar(ctx, Notificaciones.ID_COPIA, n)
    }

    /** Duración legible («3,2 s», «1 min 5 s»). */
    fun duracion(ms: Long): String =
        if (ms < 60_000) String.format(java.util.Locale.getDefault(), "%.1f s", ms / 1000f)
        else "${ms / 60_000} min ${(ms % 60_000) / 1000} s"
}
