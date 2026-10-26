package com.example.lector

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.Settings
import java.io.File

/**
 * Carpeta de la app en el almacenamiento del móvil (como «Ubicación de almacenamiento» de Komikku): la eliges y dentro
 * se crean `descargas` (capítulos .cbz) y `copias` (copias de seguridad automáticas). Así se ven desde el gestor de
 * archivos, no se pierden al borrar los datos de la app y se pueden llevar a otro móvil.
 * Usa rutas normales (java.io.File), por eso pide «Acceso a todos los archivos» (Android 11+).
 */
object CarpetaApp {
    private const val PREF = "carpeta_app"

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("lector", Context.MODE_PRIVATE)

    fun ruta(ctx: Context): String? = prefs(ctx).getString(PREF, null)

    fun hayPermiso(ctx: Context): Boolean =
        if (Build.VERSION.SDK_INT >= 30) Environment.isExternalStorageManager()
        else ctx.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED

    /** Abre el ajuste del sistema para dar el permiso (Android 11+). */
    fun pedirPermiso(ctx: Context) {
        val i = if (Build.VERSION.SDK_INT >= 30)
            Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${ctx.packageName}"))
        else Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))
        runCatching { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    /** La carpeta elegida, si existe y se puede escribir; si no, null (se usa la interna). */
    fun raiz(ctx: Context): File? {
        val r = ruta(ctx) ?: return null
        if (!hayPermiso(ctx)) return null
        return File(r).takeIf { (it.isDirectory || it.mkdirs()) && it.canWrite() }
    }

    private fun sub(ctx: Context, nombre: String): File? = raiz(ctx)?.let { File(it, nombre) }?.takeIf { it.isDirectory || it.mkdirs() }

    fun descargas(ctx: Context): File =
        sub(ctx, "descargas") ?: File(ctx.filesDir, "descargas").apply { mkdirs() }

    fun copias(ctx: Context): File? = sub(ctx, "copias")

    /** Ruta real de una carpeta elegida con el selector del sistema (memoria interna o tarjeta SD). */
    fun rutaDeArbol(uri: Uri): String? = runCatching {
        val id = DocumentsContract.getTreeDocumentId(uri) // «primary:Documents/Shiori» o «1A2B-3C4D:Shiori»
        val vol = id.substringBefore(':')
        val resto = id.substringAfter(':', "")
        val base = if (vol.equals("primary", true)) Environment.getExternalStorageDirectory().path else "/storage/$vol"
        if (resto.isEmpty()) base else "$base/$resto"
    }.getOrNull()

    /**
     * Cambia la carpeta y mueve allí las descargas que ya había (en segundo plano). Devuelve cuántas movió.
     * Llamar fuera del hilo principal.
     */
    fun cambiar(ctx: Context, nueva: String): Int {
        val antes = descargas(ctx)
        prefs(ctx).edit().putString(PREF, nueva).commit()
        val despues = descargas(ctx)
        copias(ctx)
        if (antes.absolutePath == despues.absolutePath) return 0
        var n = 0
        antes.listFiles()?.filter { it.isFile }?.forEach { f ->
            val d = File(despues, f.name)
            if (f.renameTo(d) || runCatching { f.copyTo(d, overwrite = true); f.delete() }.getOrDefault(false)) n++
        }
        return n
    }

    /** Volver a la carpeta interna de la app. */
    fun quitar(ctx: Context) { prefs(ctx).edit().remove(PREF).apply() }
}
