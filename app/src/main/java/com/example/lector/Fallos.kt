package com.example.lector

import android.content.Context
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/**
 * Registro de cierres inesperados: si la app se cierra por un error, el detalle se guarda en un archivo y
 * se enseña al volver a abrirla (con un botón para copiarlo). Así se puede saber qué pasó sin un ordenador.
 */
/** Datos de memoria y fuentes activas; si fue por memoria, esas fuentes se omiten en las búsquedas globales. */
private fun extraFallo(app: Context, error: Throwable): String {
    val rt = Runtime.getRuntime()
    val usada = (rt.totalMemory() - rt.freeMemory()) / 1_048_576
    val activas = com.example.lector.ext.FuentesEnUso.activas.toMap()
    val sinMemoria = generateSequence(error) { it.cause }.any { it is OutOfMemoryError }
    if (sinMemoria && activas.isNotEmpty()) {
        val p = app.getSharedPreferences("busqueda_global", Context.MODE_PRIVATE)
        p.edit().putStringSet("activas", p.getStringSet("activas", null).orEmpty() + activas.keys.map { "$it" }).commit()
    }
    return "Memoria: $usada de ${rt.maxMemory() / 1_048_576} MB" +
        (if (activas.isNotEmpty()) "\nFuentes trabajando: " + activas.values.joinToString(", ") else "")
}

object Fallos {
    private fun archivo(ctx: Context) = File(ctx.filesDir, "ultimo_fallo.txt")

    fun instalar(ctx: Context) {
        val app = ctx.applicationContext
        val anterior = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { hilo, error ->
            runCatching {
                val traza = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()
                val version = runCatching { app.packageManager.getPackageInfo(app.packageName, 0).versionName }.getOrNull()
                archivo(app).writeText(
                    "Lector $version · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) · ${Build.MANUFACTURER} ${Build.MODEL}\n" +
                        "Hilo: ${hilo.name}\n" + extraFallo(app, error) + "\n" + traza.take(20_000)
                )
            }
            anterior?.uncaughtException(hilo, error)
        }
    }

    /** El último fallo guardado (y lo borra, para no enseñarlo dos veces). */
    fun recoger(ctx: Context): String? {
        val f = archivo(ctx)
        if (!f.exists()) return null
        return runCatching { f.readText() }.getOrNull().also { f.delete() }
    }
}
