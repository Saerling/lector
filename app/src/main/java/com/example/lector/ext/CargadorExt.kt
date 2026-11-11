package com.example.lector.ext

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory
import eu.kanade.tachiyomi.util.system.ChildFirstPathClassLoader
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/**
 * Lectura y carga de extensiones instaladas. Port de ExtensionLoader de Komikku
 * (app/.../extension/util/ExtensionLoader.kt): mismos metadatos "tachiyomi.*" / "tachiyomix.*",
 * misma validación de la versión de la librería, mismas firmas (SHA-256 de todas las del paquete)
 * y misma forma de crear las fuentes (clase suelta o SourceFactory).
 * Lo que no se porta: las extensiones "privadas" (APK dentro de la app); aquí se instalan siempre con el instalador del sistema.
 */
object CargadorExt {
    const val CARACTERISTICA = "tachiyomi.extension"
    private const val META_CLASE = "tachiyomi.extension.class"
    private const val META_NSFW = "tachiyomi.extension.nsfw"
    private const val META_NOMBRE = "tachiyomix.name"
    private const val META_LIB = "tachiyomix.extensionLib"
    private const val META_AVISO = "tachiyomix.contentWarning"

    /** Igual que SUPPORTED_LIB_VERSIONS de Komikku. */
    // Rango, no valores sueltos: antes solo valían 1.4 y 1.6 EXACTOS y las extensiones 1.5 (muchas) no salían.
    // Como Komikku: de 1.2 a 1.6 (las que no carguen dan su error en el diagnóstico).
    val LIBS_SOPORTADAS = 1.2..1.69

    @Suppress("DEPRECATION")
    val FLAGS: Int = PackageManager.GET_CONFIGURATIONS or
        PackageManager.GET_META_DATA or
        PackageManager.GET_SIGNATURES or
        (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) PackageManager.GET_SIGNING_CERTIFICATES else 0)

    fun esExtension(p: PackageInfo): Boolean = p.reqFeatures.orEmpty().any { it.name == CARACTERISTICA }

    private fun sha256(b: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it) }

    /** Firmas del paquete (SHA-256 en hexadecimal), o lista vacía si no está firmado. */
    @Suppress("DEPRECATION")
    fun firmas(p: PackageInfo): List<String> {
        val firmas = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val info = p.signingInfo ?: return emptyList()
            if (info.hasMultipleSigners()) info.apkContentsSigners else info.signingCertificateHistory
        } else {
            p.signatures
        }
        return firmas?.map { sha256(it.toByteArray()) }.orEmpty()
    }

    /**
     * Lee los datos de un paquete instalado SIN cargar su código y comprueba lo que Komikku comprueba
     * antes de cargarlo (versión, versión de la librería y firma). Si algo falla, queda en errorCarga.
     */
    fun leer(pm: PackageManager, p: PackageInfo, privada: Boolean = false): ExtInstalada? {
        val ai = p.applicationInfo ?: return null
        val meta = ai.metaData
        val nombre = meta?.getString(META_NOMBRE)
            ?: pm.getApplicationLabel(ai).toString().substringAfter("Tachiyomi: ")
        val versionName = p.versionName.orEmpty()
        val versionCode = PackageInfoCompat.getLongVersionCode(p)
        val lib = meta?.getFloat(META_LIB, 0f)?.takeUnless { it == 0f }?.toString()?.toDoubleOrNull()
            ?: versionName.substringBeforeLast('.').toDoubleOrNull()
        val huellas = firmas(p)
        val motivo = when {
            versionName.isEmpty() -> "La extensión no declara su versión"
            lib == null || lib !in LIBS_SOPORTADAS ->
                "Versión de librería $lib no soportada (se admiten de 1.2 a 1.6)"
            huellas.isEmpty() -> "La extensión no está firmada"
            else -> null
        }
        val nsfw = (meta?.getInt(META_AVISO, 0) ?: 0) > 0 || meta?.getInt(META_NSFW, 0) == 1
        return ExtInstalada(
            nombre = nombre,
            pkg = p.packageName,
            versionName = versionName,
            versionCode = versionCode,
            lang = p.packageName.removePrefix("eu.kanade.tachiyomi.extension.").substringBefore('.').ifEmpty { "all" },
            nsfw = nsfw,
            confiable = false,
            firma = huellas.lastOrNull().orEmpty(),
            repoSigla = null,
            actualizacion = null,
            libVersion = lib ?: 0.0,
            firmas = huellas,
            errorCarga = motivo,
            privada = privada,
        )
    }

    // ---- Extensiones privadas (como ExtensionLoader de Komikku): el APK vive en la carpeta de la app, sin instalarse
    // en Android. No piden permiso de instalación ni confirmación y no aparecen entre las apps del sistema. ----
    fun carpetaPrivadas(ctx: Context) = java.io.File(ctx.filesDir, "exts")
    fun archivoPrivado(ctx: Context, pkg: String) = java.io.File(carpetaPrivadas(ctx), "$pkg.ext")

    private fun infoArchivo(pm: PackageManager, f: java.io.File): PackageInfo? = runCatching {
        if (f.canWrite()) f.setReadOnly() // Android 14+ exige que el código cargado sea de solo lectura
        val ruta = f.absolutePath
        pm.getPackageArchiveInfo(ruta, FLAGS)?.takeIf { esExtension(it) }?.also { p ->
            // En Android 13+ falta la ruta y no se podrían leer ni el icono ni las clases.
            p.applicationInfo?.let { ai -> if (ai.sourceDir == null) ai.sourceDir = ruta; if (ai.publicSourceDir == null) ai.publicSourceDir = ruta }
        }
    }.getOrNull()

    fun privadas(ctx: Context): List<PackageInfo> =
        carpetaPrivadas(ctx).listFiles()?.filter { it.isFile && it.extension == "ext" }?.mapNotNull { infoArchivo(ctx.packageManager, it) }.orEmpty()

    /** El paquete a usar y si es privado: instalado en Android o privado; si están los dos, el de versión mayor. */
    fun info(ctx: Context, pkg: String): Pair<PackageInfo, Boolean>? {
        val pm = ctx.packageManager
        val sistema = runCatching { pm.getPackageInfo(pkg, FLAGS) }.getOrNull()?.takeIf { esExtension(it) }
        val privada = archivoPrivado(ctx, pkg).takeIf { it.isFile }?.let { infoArchivo(pm, it) }
        return when {
            privada != null && (sistema == null || PackageInfoCompat.getLongVersionCode(privada) > PackageInfoCompat.getLongVersionCode(sistema)) -> privada to true
            sistema != null -> sistema to false
            else -> null
        }
    }

    /** Copia [apk] como extensión privada. Devuelve el paquete, o lanza con el motivo (mismas comprobaciones que Komikku). */
    fun instalarPrivada(ctx: Context, apk: java.io.File): String {
        val pm = ctx.packageManager
        val nueva = pm.getPackageArchiveInfo(apk.absolutePath, FLAGS)?.takeIf { esExtension(it) }
            ?: error("El archivo no es una extensión de Mihon/Tachiyomi")
        val firmasNueva = firmas(nueva)
        check(firmasNueva.isNotEmpty()) { "La extensión no está firmada" }
        info(ctx, nueva.packageName)?.first?.let { actual ->
            check(PackageInfoCompat.getLongVersionCode(nueva) >= PackageInfoCompat.getLongVersionCode(actual)) { "Ya tienes una versión más nueva" }
            check(firmasNueva.containsAll(firmas(actual))) { "La firma no coincide con la de la extensión que ya tienes" }
        }
        val destino = archivoPrivado(ctx, nueva.packageName)
        destino.parentFile?.mkdirs()
        val tmp = java.io.File(destino.parentFile, destino.name + ".tmp")
        try {
            tmp.delete(); apk.copyTo(tmp, overwrite = true); tmp.setReadOnly()
            destino.delete()
            check(tmp.renameTo(destino)) { "No se pudo guardar la extensión" }
        } finally { tmp.delete() }
        olvidar(nueva.packageName)
        return nueva.packageName
    }

    fun quitarPrivada(ctx: Context, pkg: String) { archivoPrivado(ctx, pkg).delete(); olvidar(pkg) }

    // pkg:versionCode:firma -> fuentes cargadas de esa extensión
    private val cache = ConcurrentHashMap<String, List<Source>>()

    private fun clave(e: ExtInstalada) = "${e.pkg}:${e.versionCode}:${e.firma}"

    /** Solo responde si la extensión ya está cargada (cargar un APK tarda cientos de ms). */
    fun yaCargada(e: ExtInstalada): List<Source>? = cache[clave(e)]

    /** Fuentes de una extensión; la carga la primera vez y la guarda hasta que cambie su versión o firma. */
    fun fuentes(ctx: Context, e: ExtInstalada): List<Source> {
        cache[clave(e)]?.let { return it }
        synchronized(this) {
            cache[clave(e)]?.let { return it }
            cache.keys.filter { it.startsWith("${e.pkg}:") }.forEach { cache.remove(it) } // versiones viejas
            val lista = cargar(ctx, e.pkg)
            cache[clave(e)] = lista
            return lista
        }
    }

    /** Descarta lo cargado de una extensión (p. ej. tras cambiar sus ajustes) para que se vuelva a crear. */
    fun olvidar(pkg: String) {
        cache.keys.filter { it.startsWith("$pkg:") }.forEach { cache.remove(it) }
    }

    /** Equivale al final de ExtensionLoader.loadExtension: crea las clases de fuente declaradas por la extensión. */
    private fun cargar(ctx: Context, pkg: String): List<Source> {
        val info = info(ctx, pkg)?.first ?: error("La extensión $pkg no está instalada")
        val ai = info.applicationInfo ?: error("Sin información de aplicación para $pkg")
        val clases = ai.metaData?.getString(META_CLASE) ?: error("La extensión $pkg no declara $META_CLASE")
        val cl = ChildFirstPathClassLoader(ai.sourceDir, null, ctx.classLoader)
        return clases.split(";")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { if (it.startsWith(".")) pkg + it else it }
            .flatMap { nombre ->
                when (val obj = Class.forName(nombre, false, cl).getDeclaredConstructor().newInstance()) {
                    is Source -> listOf(obj)
                    is SourceFactory -> obj.createSources()
                    else -> error("Tipo de fuente desconocido: ${obj.javaClass}")
                }
            }
    }
}
