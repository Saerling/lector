package com.example.lector.ext

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.content.pm.PackageInfoCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.Locale

/**
 * Todo lo de extensiones en un solo sitio: listados, lista de disponibles, instaladas,
 * instalar / desinstalar, confianza, fuentes ancladas y filtros.
 * Es "estado de Compose": al cambiar algo, las pantallas se repintan solas.
 */
class ExtManager private constructor(private val app: Context) {

    companion object {
        @Volatile private var instancia: ExtManager? = null
        fun obtener(c: Context): ExtManager = instancia ?: synchronized(this) {
            instancia ?: ExtManager(c.applicationContext).also { instancia = it }
        }
        private const val ACCION_INSTALAR = "com.example.lector.EXT_INSTALAR"
        private const val CARACTERISTICA = "tachiyomi.extension"
    }

    private val prefs = app.getSharedPreferences("lector_ext", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val archivoCache = File(app.filesDir, "ext_disponibles.json")

    // ---- Estado visible ----
    var repos by mutableStateOf(cargarRepos()); private set
    var disponibles by mutableStateOf<List<ExtDisponible>>(emptyList()); private set
    private var instaladasRaw by mutableStateOf<List<ExtInstalada>>(emptyList())
    var cargando by mutableStateOf(false); private set
    var errores by mutableStateOf<List<String>>(emptyList()); private set
    val progreso = mutableStateMapOf<String, Progreso>()

    var mostrarNsfw by mutableStateOf(prefs.getBoolean("nsfw", true)); private set
    var idiomas by mutableStateOf(
        prefs.getStringSet("idiomas", null)?.toSet()
            ?: setOf("all", "es", Locale.getDefault().language)
    ); private set
    var fijadas by mutableStateOf(prefs.getStringSet("fijadas", emptySet())?.toSet() ?: emptySet()); private set
    var recientes by mutableStateOf(
        (prefs.getString("recientes", "") ?: "").split(',').filter { it.isNotBlank() }
    ); private set

    private val confiadas: MutableSet<String> =
        (prefs.getStringSet("confiadas", emptySet()) ?: emptySet()).toMutableSet()
    private var confiadasVersion by mutableStateOf(0) // fuerza repintar al cambiar la confianza

    /** Instaladas + si hay actualización + si son de confianza. */
    val instaladas: List<ExtInstalada>
        get() {
            @Suppress("UNUSED_VARIABLE") val v = confiadasVersion
            return instaladasRaw.map { e ->
                val disp = disponibles.firstOrNull { it.pkg == e.pkg }
                e.copy(
                    lang = disp?.lang ?: e.lang,
                    nsfw = e.nsfw || (disp?.nsfw == true),
                    confiable = "${e.pkg}:${e.firma}" in confiadas,
                    repoSigla = disp?.repoSigla,
                    actualizacion = disp?.takeIf { it.versionCode > e.versionCode },
                )
            }
        }

    /** Fuentes de las extensiones instaladas y de confianza. */
    val fuentes: List<FuenteUI>
        get() = instaladas.filter { it.confiable }.flatMap { e ->
            val lista = disponibles.firstOrNull { it.pkg == e.pkg }?.fuentes.orEmpty()
            val base = lista.ifEmpty {
                listOf(FuenteInfo(e.pkg.hashCode().toLong(), e.nombre, e.lang, "", e.pkg))
            }
            base.map { FuenteUI(it.id, it.nombre.ifBlank { e.nombre }, it.lang.ifBlank { e.lang }, e.nsfw, e.pkg, it.baseUrl) }
        }

    // ---- Receptores ----
    private val receptorPaquetes = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) { refrescarInstaladas() }
    }

    private val receptorInstalacion = object : BroadcastReceiver() {
        @Suppress("DEPRECATION")
        override fun onReceive(c: Context, i: Intent) {
            val pkg = i.getStringExtra("pkg") ?: i.getStringExtra(PackageInstaller.EXTRA_PACKAGE_NAME) ?: return
            when (i.getIntExtra(PackageInstaller.EXTRA_STATUS, -1)) {
                PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                    val confirmar: Intent? = if (Build.VERSION.SDK_INT >= 33)
                        i.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                    else i.getParcelableExtra(Intent.EXTRA_INTENT)
                    if (confirmar != null) {
                        confirmar.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        runCatching { app.startActivity(confirmar) }
                    }
                }
                PackageInstaller.STATUS_SUCCESS -> { progreso.remove(pkg); refrescarInstaladas() }
                PackageInstaller.STATUS_FAILURE_ABORTED -> progreso.remove(pkg)
                else -> progreso[pkg] = Progreso(
                    Fase.ERROR,
                    error = i.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "No se pudo instalar",
                )
            }
        }
    }

    init {
        val paquetes = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(app, receptorPaquetes, paquetes, ContextCompat.RECEIVER_EXPORTED)
        ContextCompat.registerReceiver(
            app, receptorInstalacion, IntentFilter(ACCION_INSTALAR), ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        cargarCache()
        refrescarInstaladas()
        actualizarLista()
    }

    // ---- Listados (repositorios) ----
    private fun reposPorDefecto() = listOf(
        RepoExt(
            "https://github.com/keiyoushi/extensions/raw/repo/index.pb", "Keiyoushi", true,
            "https://github.com/keiyoushi/extensions",
        ),
        RepoExt(
            "https://raw.githubusercontent.com/suwayomi/tachiyomi-extension/repo/index.min.json",
            "Suwayomi Extension Repo", true, "https://github.com/Suwayomi/tachiyomi-extension",
        ),
    )

    private fun cargarRepos(): List<RepoExt> {
        val s = prefs.getString("repos", null)
        if (s == null) return reposPorDefecto().also { guardarRepos(it) }
        return runCatching {
            val a = JSONArray(s)
            (0 until a.length()).map {
                val o = a.getJSONObject(it)
                RepoExt(o.getString("url"), o.getString("nombre"), o.optBoolean("activo", true), o.optString("web").ifBlank { null })
            }
        }.getOrDefault(emptyList())
    }

    private fun guardarRepos(lista: List<RepoExt>) {
        val a = JSONArray()
        lista.forEach {
            a.put(JSONObject().put("url", it.url).put("nombre", it.nombre).put("activo", it.activo).put("web", it.web ?: ""))
        }
        prefs.edit().putString("repos", a.toString()).apply()
    }

    /** Comprueba el listado descargándolo; si es válido lo añade. [alTerminar] recibe el error o null. */
    fun anadirRepo(entrada: String, alTerminar: (String?) -> Unit) {
        val url = ExtRepos.normalizar(entrada)
        if (url == null) { alTerminar("Escribe una dirección que empiece por https://"); return }
        if (repos.any { it.url == url }) { alTerminar("Ese listado ya está añadido"); return }
        scope.launch {
            val repo = RepoExt(url, ExtRepos.nombreDe(url), true, ExtRepos.webDe(url))
            val error = runCatching {
                val lista = ExtRepos.leerIndice(ExtRepos.descargar(url), url, repo)
                if (lista.isEmpty()) "El listado está vacío o no tiene un formato conocido" else null
            }.getOrElse { "No se pudo leer el listado: ${it.message ?: it.javaClass.simpleName}" }
            withContext(Dispatchers.Main) {
                if (error == null) {
                    repos = repos + repo
                    guardarRepos(repos)
                    actualizarLista()
                }
                alTerminar(error)
            }
        }
    }

    fun eliminarRepo(url: String) {
        repos = repos.filterNot { it.url == url }
        guardarRepos(repos)
        actualizarLista()
    }

    fun alternarRepo(url: String) {
        repos = repos.map { if (it.url == url) it.copy(activo = !it.activo) else it }
        guardarRepos(repos)
        actualizarLista()
    }

    // ---- Lista de extensiones disponibles ----
    fun actualizarLista() {
        if (cargando) return
        cargando = true
        scope.launch {
            val nuevas = ArrayList<ExtDisponible>()
            val errs = ArrayList<String>()
            repos.filter { it.activo }.forEach { r ->
                runCatching { nuevas += ExtRepos.leerIndice(ExtRepos.descargar(r.url), r.url, r) }
                    .onFailure { errs += "${r.nombre}: ${it.message ?: it.javaClass.simpleName}" }
            }
            // Si una extensión sale en varios listados, gana la versión más alta.
            val unicas = nuevas.groupBy { it.pkg }.values.map { g -> g.maxByOrNull { it.versionCode }!! }
            withContext(Dispatchers.Main) {
                if (unicas.isNotEmpty() || errs.isEmpty()) {
                    disponibles = unicas
                    guardarCache(unicas)
                }
                errores = errs
                cargando = false
            }
        }
    }

    private fun guardarCache(lista: List<ExtDisponible>) = runCatching {
        val a = JSONArray()
        lista.forEach { e ->
            a.put(
                JSONObject().put("nombre", e.nombre).put("pkg", e.pkg).put("apk", e.apkUrl).put("icono", e.iconoUrl)
                    .put("lang", e.lang).put("code", e.versionCode).put("version", e.versionName).put("nsfw", e.nsfw)
                    .put("repo", e.repoUrl).put("sigla", e.repoSigla)
                    .put(
                        "fuentes",
                        JSONArray().also { fa ->
                            e.fuentes.forEach { f ->
                                fa.put(JSONObject().put("id", f.id.toString()).put("n", f.nombre).put("l", f.lang).put("b", f.baseUrl))
                            }
                        },
                    ),
            )
        }
        archivoCache.writeText(a.toString())
    }

    private fun cargarCache() {
        runCatching {
            if (!archivoCache.exists()) return
            val a = JSONArray(archivoCache.readText())
            disponibles = (0 until a.length()).map {
                val o = a.getJSONObject(it)
                val pkg = o.getString("pkg")
                val fa = o.optJSONArray("fuentes")
                ExtDisponible(
                    o.getString("nombre"), pkg, o.optString("apk"), o.optString("icono"), o.optString("lang"),
                    o.optLong("code"), o.optString("version"), o.optBoolean("nsfw"), 
                    if (fa == null) emptyList() else (0 until fa.length()).map { j ->
                        val f = fa.getJSONObject(j)
                        FuenteInfo(f.optString("id", "0").toLongOrNull() ?: 0L, f.optString("n"), f.optString("l"), f.optString("b"), pkg)
                    },
                    o.optString("repo"), o.optString("sigla"),
                )
            }
        }
    }

    // ---- Extensiones instaladas ----
    @Suppress("DEPRECATION")
    fun refrescarInstaladas() {
        val pm = app.packageManager
        val nombres = runCatching { pm.getInstalledPackages(PackageManager.GET_CONFIGURATIONS) }
            .getOrDefault(emptyList())
            .filter { p -> p.reqFeatures?.any { it.name == CARACTERISTICA } == true }
            .map { it.packageName }
        val flags = PackageManager.GET_META_DATA or
            (if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES)
        instaladasRaw = nombres.mapNotNull { n ->
            runCatching {
                val p = pm.getPackageInfo(n, flags)
                val ai = p.applicationInfo ?: return@runCatching null
                ExtInstalada(
                    nombre = pm.getApplicationLabel(ai).toString().removePrefix("Tachiyomi: "),
                    pkg = n,
                    versionName = p.versionName ?: "",
                    versionCode = PackageInfoCompat.getLongVersionCode(p),
                    lang = n.removePrefix("eu.kanade.tachiyomi.extension.").substringBefore('.').ifEmpty { "all" },
                    nsfw = ai.metaData?.getInt("tachiyomi.extension.nsfw", 0) == 1,
                    confiable = false,
                    firma = firma(p),
                    repoSigla = null,
                    actualizacion = null,
                )
            }.getOrNull()
        }.sortedBy { it.nombre.lowercase() }
    }

    @Suppress("DEPRECATION")
    private fun firma(p: PackageInfo): String {
        val firmas = if (Build.VERSION.SDK_INT >= 28) p.signingInfo?.apkContentsSigners else p.signatures
        val bytes = firmas?.firstOrNull()?.toByteArray() ?: return ""
        return MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    }

    // ---- Confianza (como en Mihon: hay que pulsar "Confiar" antes de usar una extensión) ----
    fun confiar(e: ExtInstalada) {
        confiadas += "${e.pkg}:${e.firma}"
        prefs.edit().putStringSet("confiadas", confiadas.toSet()).apply()
        confiadasVersion++
    }

    fun dejarDeConfiar(e: ExtInstalada) {
        confiadas -= "${e.pkg}:${e.firma}"
        prefs.edit().putStringSet("confiadas", confiadas.toSet()).apply()
        confiadasVersion++
    }

    // ---- Instalar / desinstalar ----
    fun instalar(e: ExtDisponible) {
        if (e.apkUrl.isBlank()) return
        val actual = progreso[e.pkg]?.fase
        if (actual == Fase.DESCARGANDO || actual == Fase.INSTALANDO) return
        if (!app.packageManager.canRequestPackageInstalls()) {
            Toast.makeText(app, "Permite instalar apps desde Lector y vuelve a pulsar Instalar", Toast.LENGTH_LONG).show()
            runCatching {
                app.startActivity(
                    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${app.packageName}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
            return
        }
        scope.launch {
            try {
                progreso[e.pkg] = Progreso(Fase.DESCARGANDO, 0)
                val apk = File(File(app.cacheDir, "ext").apply { mkdirs() }, "${e.pkg}.apk")
                ExtRepos.descargarA(e.apkUrl, apk) { progreso[e.pkg] = Progreso(Fase.DESCARGANDO, it) }
                progreso[e.pkg] = Progreso(Fase.INSTALANDO)
                abrirSesion(e.pkg, apk)
            } catch (t: Throwable) {
                progreso[e.pkg] = Progreso(Fase.ERROR, error = t.message ?: "Error al descargar")
            }
        }
    }

    private fun abrirSesion(pkg: String, apk: File) {
        val instalador = app.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        params.setAppPackageName(pkg)
        val id = instalador.createSession(params)
        instalador.openSession(id).use { s ->
            apk.inputStream().use { ent ->
                s.openWrite("ext.apk", 0, apk.length()).use { sal ->
                    ent.copyTo(sal)
                    s.fsync(sal)
                }
            }
            val intent = Intent(ACCION_INSTALAR).setPackage(app.packageName).putExtra("pkg", pkg)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
            s.commit(PendingIntent.getBroadcast(app, id, intent, flags).intentSender)
        }
    }

    fun desinstalar(pkg: String) {
        runCatching {
            app.startActivity(
                Intent(Intent.ACTION_DELETE, Uri.parse("package:$pkg")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    fun limpiarError(pkg: String) { progreso.remove(pkg) }

    // ---- Filtros y fuentes ancladas ----
    fun alternarNsfw() {
        mostrarNsfw = !mostrarNsfw
        prefs.edit().putBoolean("nsfw", mostrarNsfw).apply()
    }

    fun alternarIdioma(lang: String) {
        idiomas = if (lang in idiomas) idiomas - lang else idiomas + lang
        prefs.edit().putStringSet("idiomas", idiomas).apply()
    }

    fun alternarFijada(id: Long) {
        val s = id.toString()
        fijadas = if (s in fijadas) fijadas - s else fijadas + s
        prefs.edit().putStringSet("fijadas", fijadas).apply()
    }

    fun marcarReciente(id: Long) {
        recientes = (listOf(id.toString()) + recientes.filter { it != id.toString() }).take(5)
        prefs.edit().putString("recientes", recientes.joinToString(",")).apply()
    }
}
