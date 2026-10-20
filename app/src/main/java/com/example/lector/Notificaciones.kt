package com.example.lector

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.ForegroundInfo

/** Canales, identificadores y ayudas para las notificaciones (actualizaciones, descargas, extensiones, copias). */
object Notificaciones {
    const val CANAL_PROGRESO = "progreso"
    const val CANAL_CAPITULOS = "capitulos_nuevos"
    const val CANAL_DESCARGAS = "descargas"
    const val CANAL_EXTENSIONES = "extensiones"
    const val CANAL_COPIAS = "copias"

    const val ID_DESCARGAS = 101
    const val ID_ACTUALIZANDO = 102
    const val ID_CAPITULOS = 103
    const val ID_EXTENSIONES = 104
    const val ID_COPIA = 105
    const val ID_DESCARGAS_FIN = 106

    /** Destinos que entiende MainActivity (extra "destino" del intent). */
    const val DESTINO_ACTUALIZACIONES = "actualizaciones"
    const val DESTINO_DESCARGAS = "descargas"
    const val DESTINO_EXTENSIONES = "extensiones"

    fun crearCanales(ctx: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannels(
            listOf(
                NotificationChannel(CANAL_PROGRESO, "Progreso de la biblioteca", NotificationManager.IMPORTANCE_LOW)
                    .apply { description = "Mientras se buscan capítulos nuevos"; setShowBadge(false) },
                NotificationChannel(CANAL_CAPITULOS, "Capítulos nuevos", NotificationManager.IMPORTANCE_DEFAULT)
                    .apply { description = "Cuando salen capítulos de los mangas de tu biblioteca" },
                NotificationChannel(CANAL_DESCARGAS, "Descargas", NotificationManager.IMPORTANCE_LOW)
                    .apply { description = "Progreso de las descargas de capítulos"; setShowBadge(false) },
                NotificationChannel(CANAL_EXTENSIONES, "Extensiones", NotificationManager.IMPORTANCE_DEFAULT)
                    .apply { description = "Extensiones con una versión nueva" },
                NotificationChannel(CANAL_COPIAS, "Copias de seguridad", NotificationManager.IMPORTANCE_LOW)
                    .apply { description = "Copias automáticas"; setShowBadge(false) },
            ),
        )
    }

    fun abrirApp(ctx: Context, destino: String?, codigo: Int): PendingIntent {
        val i = Intent(ctx, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        if (destino != null) i.putExtra("destino", destino)
        return PendingIntent.getActivity(ctx, codigo, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    fun base(ctx: Context, canal: String): NotificationCompat.Builder =
        NotificationCompat.Builder(ctx, canal)
            .setSmallIcon(R.drawable.ic_notificacion)
            .setColor(0xFFFA233B.toInt())

    fun progreso(ctx: Context, canal: String, titulo: String, texto: String, hechas: Int, total: Int, destino: String?, id: Int): Notification =
        base(ctx, canal)
            .setContentTitle(titulo)
            .setContentText(texto)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setProgress(total.coerceAtLeast(0), hechas.coerceIn(0, total.coerceAtLeast(0)), total <= 0)
            .setContentIntent(abrirApp(ctx, destino, id))
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .build()

    /** Para que WorkManager ejecute un trabajo largo como servicio en primer plano (Android 14: tipo dataSync). */
    fun primerPlano(id: Int, n: Notification): ForegroundInfo =
        if (Build.VERSION.SDK_INT >= 29) ForegroundInfo(id, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else ForegroundInfo(id, n)

    fun mostrar(ctx: Context, id: Int, n: Notification) {
        runCatching { NotificationManagerCompat.from(ctx).notify(id, n) }
    }

    fun quitar(ctx: Context, id: Int) {
        runCatching { NotificationManagerCompat.from(ctx).cancel(id) }
    }

    fun permitidas(ctx: Context): Boolean = NotificationManagerCompat.from(ctx).areNotificationsEnabled()
}
