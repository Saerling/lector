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
                        "Hilo: ${hilo.name}\n\n" + traza.take(20_000)
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
