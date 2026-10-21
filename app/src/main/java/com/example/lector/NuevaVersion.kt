package com.example.lector

import android.content.Context
import android.content.pm.ApplicationInfo
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.pm.PackageInfoCompat
import com.example.lector.ext.ExtManager
import com.example.lector.ext.ExtRepos
import com.example.lector.ext.Fase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Actualizaciones de la propia app desde GitHub. Cada compilación de las ramas principales se publica en «Releases»
 * (ver .github/workflows/build.yml) con la etiqueta `v<versión>-<número>`; si ese número es mayor que el de la app
 * instalada, se ofrece descargar el APK e instalarlo encima (misma firma, así que no hay que desinstalar nada).
 */
object NuevaVersion {
    private const val REPO = "Saerling/lector"
    private const val API = "https://api.github.com/repos/$REPO/releases/latest"

    data class Version(val nombre: String, val codigo: Long, val notas: String, val apkUrl: String, val tamano: Long)

    enum class Paso { LISTO, DESCARGANDO, INSTALANDO, ERROR }

    /** La versión nueva encontrada (null = no hay o no se ha mirado). */
    var disponible by mutableStateOf<Version?>(null); private set
    var verDialogo by mutableStateOf(false)
    var paso by mutableStateOf(Paso.LISTO); private set
    var porcentaje by mutableIntStateOf(0); private set
    var error by mutableStateOf<String?>(null); private set
    var comprobando by mutableStateOf(false); private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var trabajo: kotlinx.coroutines.Job? = null

    fun codigoActual(ctx: Context): Long =
        runCatching { PackageInfoCompat.getLongVersionCode(ctx.packageManager.getPackageInfo(ctx.packageName, 0)) }.getOrDefault(0)

    fun nombreActual(ctx: Context): String =
        runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "?"

    private fun esDepurable(ctx: Context) = (ctx.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

    /** Mira en GitHub la última versión publicada. Devuelve la versión si es más nueva que la instalada. */
    suspend fun comprobar(ctx: Context): Version? = ultima(ctx)?.takeIf { it.codigo > codigoActual(ctx) }

    /** La última versión publicada en GitHub (sea o no más nueva). null si aún no hay ninguna. */
    suspend fun ultima(ctx: Context): Version? = withContext(Dispatchers.IO) {
        val c = (URL(API).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000; readTimeout = 20_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "Shiori/${nombreActual(ctx)}")
        }
        try {
            when (c.responseCode) {
                404 -> return@withContext null // aún no hay ninguna versión publicada
                in 200..299 -> Unit
                403, 429 -> error("GitHub limita las consultas por ahora; prueba en un rato")
                else -> error("GitHub respondió ${c.responseCode}")
            }
            val o = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
            val etiqueta = o.optString("tag_name")
            val codigo = etiqueta.substringAfterLast('-').toLongOrNull() ?: return@withContext null
            val assets = o.optJSONArray("assets")
            val apk = (0 until (assets?.length() ?: 0)).map { assets!!.getJSONObject(it) }
                .firstOrNull { it.optString("name").endsWith(".apk") } ?: return@withContext null
            val v = Version(
                nombre = etiqueta.removePrefix("v").substringBeforeLast('-'),
                codigo = codigo,
                notas = (if (o.isNull("body")) "" else o.optString("body")).trim(),
                apkUrl = apk.optString("browser_download_url"),
                tamano = apk.optLong("size"),
            )
            v
        } finally {
            c.disconnect()
        }
    }

    /** Al abrir la app: mira si hay versión nueva y, si la hay, enseña el aviso. (No en las compilaciones de prueba.) */
    fun alArrancar(ctx: Context) {
        val app = ctx.applicationContext
        if (esDepurable(app)) return
        scope.launch {
            delay(2500) // que primero termine la animación de arranque
            val v = runCatching { comprobar(app) }.getOrNull() ?: return@launch
            disponible = v
            verDialogo = true
        }
    }

    /** Desde Ajustes → Acerca de. */
    fun buscarAhora(ctx: Context) {
        if (comprobando) return
        val app = ctx.applicationContext
        comprobando = true
        scope.launch {
            val r = runCatching { comprobar(app) }
            comprobando = false
            r.onSuccess { v ->
                if (v != null) { disponible = v; verDialogo = true }
                else withContext(Dispatchers.Main) { com.example.lector.ui.Avisos.mostrar("Ya tienes la última versión (${nombreActual(app)})") }
            }.onFailure { e ->
                withContext(Dispatchers.Main) { com.example.lector.ui.Avisos.mostrar(e.message ?: "No se pudo comprobar") }
            }
        }
    }

    /** Segundo plano (cada 12 h, con la revisión de extensiones): una notificación por cada versión nueva. */
    suspend fun revisarEnSegundoPlano(ctx: Context) {
        if (esDepurable(ctx)) return
        val v = comprobar(ctx) ?: return
        val prefs = ctx.getSharedPreferences("actualizador", Context.MODE_PRIVATE)
        if (prefs.getLong("avisada", 0) == v.codigo) return
        prefs.edit().putLong("avisada", v.codigo).apply()
        val n = Notificaciones.base(ctx, Notificaciones.CANAL_EXTENSIONES)
            .setContentTitle("Nueva versión de Shiori: ${v.nombre}")
            .setContentText("Toca para descargarla e instalarla")
            .setAutoCancel(true)
            .setContentIntent(Notificaciones.abrirApp(ctx, DESTINO, Notificaciones.ID_APP))
            .build()
        Notificaciones.mostrar(ctx, Notificaciones.ID_APP, n)
    }

    const val DESTINO = "actualizar-app"

    /** Al tocar la notificación. */
    fun abrirDesdeNotificacion(ctx: Context) {
        if (disponible != null) { verDialogo = true; return }
        buscarAhora(ctx)
    }

    /** Descarga el APK de [disponible] y lanza la instalación (Android pide confirmarla). */
    fun descargarEInstalar(ctx: Context) {
        val v = disponible ?: return
        if (paso == Paso.DESCARGANDO) return
        trabajo?.cancel()
        val app = ctx.applicationContext
        val ext = ExtManager.obtener(app)
        paso = Paso.DESCARGANDO; porcentaje = 0; error = null
        ext.limpiarError(app.packageName)
        trabajo = scope.launch {
            try {
                val carpeta = File(app.cacheDir, "actualizacion").apply { deleteRecursively(); mkdirs() }
                val apk = File(carpeta, "shiori-${v.codigo}.apk")
                ExtRepos.descargarA(v.apkUrl, apk) { porcentaje = it }
                if (v.tamano > 0 && apk.length() != v.tamano) error("La descarga quedó incompleta; inténtalo otra vez")
                paso = Paso.INSTALANDO
                withContext(Dispatchers.Main) { ext.abrirSesion(app.packageName, apk) }
                // Si Android rechaza la instalación, el motivo llega al progreso del instalador.
                repeat(600) {
                    val p = ext.progreso[app.packageName]
                    if (p?.fase == Fase.ERROR) {
                        ext.limpiarError(app.packageName)
                        throw IllegalStateException(explicar(p.error))
                    }
                    delay(500)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Throwable) {
                error = e.message ?: "No se pudo actualizar"
                paso = Paso.ERROR
            }
        }
    }

    /** Cancelar desde el diálogo (o cerrarlo tras confirmar en Android). */
    fun cerrar() {
        verDialogo = false
        if (paso != Paso.DESCARGANDO) { paso = Paso.LISTO; error = null }
    }

    private fun explicar(mensaje: String?): String {
        val m = mensaje.orEmpty()
        return when {
            m.contains("UPDATE_INCOMPATIBLE", true) || m.contains("signatures", true) || m.contains("conflict", true) ->
                "La versión instalada está firmada con otra clave (las anteriores a la 0.9). Haz una copia de seguridad, " +
                    "desinstala la app una sola vez e instala esta; desde ahora las actualizaciones se instalan encima."
            m.contains("VERSION_DOWNGRADE", true) -> "La versión instalada es más nueva que esta."
            m.contains("storage", true) || m.contains("space", true) -> "No hay espacio suficiente para instalarla."
            else -> m.ifBlank { "Android no pudo instalar la actualización" }
        }
    }
}
