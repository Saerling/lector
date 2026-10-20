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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.online.HttpSource
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

    /** Se completa cuando ya se leyeron las extensiones instaladas y sus fuentes (lo esperan los trabajos de fondo). */
    private val listo = kotlinx.coroutines.CompletableDeferred<Unit>()

    /** Espera (como mucho [ms]) a que las extensiones estén cargadas. */
    suspend fun esperarListo(ms: Long = 20_000L) {
        kotlinx.coroutines.withTimeoutOrNull(ms) { listo.await() }
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val archivoCache = File(app.filesDir, "ext_disponibles.json")

    // ---- Estado visible ----
    var repos by mutableStateOf(cargarRepos()); private set
    // Todo lo que ofrecen los listados (una entrada por paquete y firma, como Komikku) y la vista reducida
    // a una por paquete que usan las pantallas.
    private var disponiblesTodas by mutableStateOf<List<ExtDisponible>>(emptyList())
    var disponibles by mutableStateOf<List<ExtDisponible>>(emptyList()); private set
    // Fuentes reales leídas del código de cada extensión de confianza (como Komikku), y por qué no cargó alguna.
    private val fuentesCargadas = mutableStateMapOf<String, List<FuenteInfo>>()
    val erroresCarga = mutableStateMapOf<String, String>()

    private fun reducirDisponibles() {
        val firmas = instaladasRaw.associate { it.pkg to it.firmas }
        disponibles = disponiblesTodas.groupBy { it.pkg }.values.map { g ->
            val propias = firmas[g[0].pkg].orEmpty()
            val mismas = g.filter { d -> d.firma.isBlank() || propias.any { it.equals(d.firma, true) } }
            (if (propias.isNotEmpty() && mismas.isNotEmpty()) mismas else g).maxByOrNull { it.versionCode }!!
        }
    }
    private var instaladasRaw by mutableStateOf<List<ExtInstalada>>(emptyList())
    var cargando by mutableStateOf(false); private set
    var errores by mutableStateOf<List<String>>(emptyList()); private set
    val progreso = mutableStateMapOf<String, Progreso>()

    var mostrarNsfw by mutableStateOf(prefs.getBoolean("nsfw", true)); private set
    var idiomas by mutableStateOf(
        prefs.getStringSet("idiomas", null)?.toSet()
            ?: setOf("all", "es", Locale.getDefault().language)
    ); private set
    // Mientras el usuario no toque la lista de idiomas, salen TODOS (los de todas las extensiones del listado).
    private var idiomasPersonalizados = prefs.getBoolean("idiomas_pers", false)
    private fun sincronizarIdiomas() {
        if (idiomasPersonalizados) return
        idiomas = (disponibles.map { it.lang } + instaladasRaw.map { it.lang } + "all").filter { it.isNotBlank() }.toSet()
    }

    /** Vuelve a activar todos los idiomas. */
    fun activarTodosIdiomas() {
        idiomasPersonalizados = false
        prefs.edit().putBoolean("idiomas_pers", false).apply()
        sincronizarIdiomas()
    }

    var fijadas by mutableStateOf(prefs.getStringSet("fijadas", emptySet())?.toSet() ?: emptySet()); private set
    var recientes by mutableStateOf(
        (prefs.getString("recientes", "") ?: "").split(',').filter { it.isNotBlank() }
    ); private set

    // Fuentes que salen en la pestaña Novedades, en el orden que elige el usuario.
    // Si nunca se tocó, arranca con las fuentes ancladas.
    var novedades by mutableStateOf(cargarNovedades()); private set

    // Capítulos leídos online ("fuenteId|urlCapítulo") y modo del lector online (0 tira · 1 paginado → · 2 paginado ←).
    var leidos by mutableStateOf(prefs.getStringSet("leidos", emptySet())?.toSet() ?: emptySet()); private set
    var modoLector by mutableStateOf(prefs.getInt("modo_lector", 0)); private set

    // Capítulos marcados con el marcador ("fuenteId|urlCapítulo"); se alternan deslizando la fila hacia la izquierda.
    var marcados by mutableStateOf(prefs.getStringSet("marcados", emptySet())?.toSet() ?: emptySet()); private set

    // Página por la que vas en cada capítulo SIN terminar ("fuenteId|url" -> página, desde 0).
    private val paginasCap = mutableStateMapOf<String, Int>().also { m ->
        runCatching {
            val o = JSONObject(prefs.getString("paginas_cap", "{}") ?: "{}")
            o.keys().forEach { k -> m[k] = o.getInt(k) }
        }
    }

    private val confiadas: MutableSet<String> =
        (prefs.getStringSet("confiadas", emptySet()) ?: emptySet()).toMutableSet()
    private var confiadasVersion by mutableStateOf(0) // fuerza repintar al cambiar la confianza

    /**
     * Instaladas + si hay actualización + si son de confianza.
     * Como en Komikku (TrustExtension): una extensión firmada con la clave de un listado añadido es de confianza;
     * el resto necesita pulsar «Confiar». Hay actualización si el listado con LA MISMA firma ofrece una versión
     * mayor o una versión de librería mayor (ExtensionManager.updateExists).
     */
    val instaladas: List<ExtInstalada>
        get() {
            @Suppress("UNUSED_VARIABLE") val v = confiadasVersion
            val clavesListados = repos.map { it.claveFirma }.filter { it.isNotBlank() }
            return instaladasRaw.map { e ->
                val disp = disponibles.firstOrNull { it.pkg == e.pkg }
                val candidato = disponiblesTodas
                    .filter { d -> d.pkg == e.pkg && (d.firma.isBlank() || e.firmas.any { it.equals(d.firma, true) }) }
                    .maxByOrNull { it.versionCode }
                val deListado = e.firmas.any { f -> clavesListados.any { it.equals(f, true) } }
                val reales = fuentesCargadas[e.pkg]?.map { it.lang }?.toSet()
                e.copy(
                    lang = when {
                        reales != null && reales.size == 1 -> reales.first()
                        reales != null && reales.size > 1 -> "all"
                        else -> disp?.lang ?: e.lang
                    },
                    nsfw = e.nsfw || (disp?.nsfw == true),
                    confiable = deListado || "${e.pkg}:${e.firma}" in confiadas,
                    repoSigla = (candidato ?: disp)?.repoSigla,
                    actualizacion = candidato?.takeIf { it.versionCode > e.versionCode || it.libVersion > e.libVersion },
                )
            }
        }

    /** Fuentes de las extensiones instaladas y de confianza (las reales si ya se cargaron; si no, las del listado). */
    val fuentes: List<FuenteUI>
        get() = instaladas.filter { it.confiable && it.errorCarga == null }.flatMap { e ->
            val lista = fuentesCargadas[e.pkg]?.takeIf { it.isNotEmpty() }
                ?: disponibles.firstOrNull { it.pkg == e.pkg }?.fuentes.orEmpty()
            val base = lista.ifEmpty {
                listOf(FuenteInfo(e.pkg.hashCode().toLong(), e.nombre, e.lang, "", e.pkg))
            }
            base.map { FuenteUI(it.id, it.nombre.ifBlank { e.nombre }, it.lang.ifBlank { e.lang }, e.nsfw, e.pkg, it.baseUrl) }
        }

    /**
     * Fuentes de una extensión que tienen ajustes propios (ConfigurableSource). Vacío mientras la extensión
     * no esté cargada; al terminar de cargar, las pantallas que lo leen se repintan solas.
     */
    fun configurables(pkg: String): List<ConfigurableSource> {
        if (fuentesCargadas[pkg] == null) return emptyList()
        val e = instaladas.firstOrNull { it.pkg == pkg } ?: return emptyList()
        return CargadorExt.yaCargada(e)?.filterIsInstance<ConfigurableSource>().orEmpty()
    }

    /** Vuelve a crear las fuentes de una extensión para que lean sus ajustes nuevos. */
    fun recargarExtension(pkg: String) {
        CargadorExt.olvidar(pkg)
        (Motor.actual as? MotorExt)?.olvidar()
        scope.launch { cargarFuentesReales(pkg) }
    }

    /** Carga en segundo plano el código de las extensiones de confianza para conocer sus fuentes reales. */
    private suspend fun cargarFuentesReales(solo: String? = null) {
        val objetivos = instaladas.filter { it.confiable && it.errorCarga == null && (solo == null || it.pkg == solo) }
        coroutineScope {
            objetivos.map { e ->
                async(Dispatchers.Default) {
                    runCatching { CargadorExt.fuentes(app, e) }
                        .onSuccess { src ->
                            fuentesCargadas[e.pkg] = src.map {
                                FuenteInfo(it.id, it.name, it.lang, (it as? HttpSource)?.baseUrl.orEmpty(), e.pkg)
                            }
                            erroresCarga.remove(e.pkg)
                        }
                        .onFailure {
                            erroresCarga[e.pkg] = it.message ?: it.javaClass.simpleName
                            android.util.Log.e("Lector", "No se pudo cargar la extensión ${e.pkg}", it)
                        }
                }
            }.awaitAll()
        }
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
        // Motor de fuentes: ejecuta el código de las extensiones de confianza (Komikku source-api).
        // Va ANTES de la carga en segundo plano: registra lo que las extensiones piden con Injekt.
        runCatching { Motor.actual = MotorExt(app.applicationContext as android.app.Application, this) }
            .onFailure { android.util.Log.e("Lector", "No se pudo iniciar el motor de fuentes", it) }
        // Leer el JSON de ~1500 extensiones y recorrer todos los paquetes del teléfono tardaba cientos de ms
        // EN EL HILO PRINCIPAL al abrir la app. Ahora va en segundo plano; las pantallas se rellenan solas.
        scope.launch {
            try {
                cargarCache()
                leerInstaladas()
                withContext(Dispatchers.Main) { actualizarLista() }
                cargarFuentesReales()
            } finally {
                listo.complete(Unit)
            }
        }
    }

    // ---- Listados (repositorios) ----
    private fun reposPorDefecto() = listOf(
        RepoExt(
            "https://raw.githubusercontent.com/keiyoushi/extensions/repo/index.min.json", "Keiyoushi", true,
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
                RepoExt(
                    url = o.getString("url"),
                    nombre = o.getString("nombre"),
                    activo = o.optBoolean("activo", true),
                    web = o.optString("web").ifBlank { null },
                    etiqueta = o.optString("etiqueta"),
                    claveFirma = o.optString("clave"),
                    esLegacy = o.optBoolean("legacy", false),
                    urlLista = o.optString("lista").ifBlank { null },
                    discord = o.optString("discord").ifBlank { null },
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun guardarRepos(lista: List<RepoExt>) {
        val a = JSONArray()
        lista.forEach {
            a.put(
                JSONObject().put("url", it.url).put("nombre", it.nombre).put("activo", it.activo).put("web", it.web ?: "")
                    .put("etiqueta", it.etiqueta).put("clave", it.claveFirma).put("legacy", it.esLegacy)
                    .put("lista", it.urlLista ?: "").put("discord", it.discord ?: ""),
            )
        }
        prefs.edit().putString("repos", a.toString()).apply()
    }

    /**
     * Comprueba el listado descargándolo (probando las direcciones habituales); si es válido lo añade.
     * [alTerminar] recibe el error o null.
     */
    fun anadirRepo(entrada: String, alTerminar: (String?) -> Unit) {
        val candidatos = ExtRepos.candidatos(entrada)
        if (candidatos.isEmpty()) {
            alTerminar("Escribe una dirección https://… o «usuario/repositorio» de GitHub"); return
        }
        val carpeta = ExtRepos.carpeta(candidatos[0])
        if (repos.any { ExtRepos.carpeta(it.url) == carpeta }) { alTerminar("Ese listado ya está añadido"); return }
        scope.launch {
            var hallado: RepoExt? = null
            var primerError: String? = null
            for (url in candidatos) {
                // Igual que Komikku al añadir una tienda: se lee el listado y se guardan sus datos (nombre, etiqueta, clave de firma…).
                val r = runCatching {
                    val t = ExtRepos.leerTienda(url)
                    check(ExtRepos.extensiones(t).isNotEmpty()) { "El listado está vacío o no tiene un formato conocido" }
                    t.repo
                }
                if (r.isSuccess) { hallado = r.getOrNull(); break }
                if (primerError == null) primerError = r.exceptionOrNull()?.let { it.message ?: it.javaClass.simpleName }
            }
            withContext(Dispatchers.Main) {
                val repo = hallado
                if (repo != null) {
                    val repetido = repos.any {
                        it.url == repo.url || (repo.claveFirma.isNotBlank() && it.claveFirma == repo.claveFirma && it.nombre == repo.nombre)
                    }
                    if (repetido) { alTerminar("Ese listado ya está añadido"); return@withContext }
                    repos = repos + repo
                    guardarRepos(repos)
                    actualizarLista()
                    alTerminar(null)
                } else {
                    alTerminar(
                        "No se pudo leer el listado (${primerError ?: "sin respuesta"}). Prueba con la dirección " +
                            "del índice, por ejemplo https://raw.githubusercontent.com/usuario/repo/repo/index.min.json",
                    )
                }
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
        scope.launch { recargarLista() }
    }

    /** Igual que [actualizarLista] pero espera a que termine (trabajo de fondo que avisa de actualizaciones). */
    suspend fun actualizarListaAhora() {
        if (cargando) {
            kotlinx.coroutines.withTimeoutOrNull(60_000L) { while (cargando) kotlinx.coroutines.delay(250) }
            return
        }
        cargando = true
        recargarLista()
    }

    private suspend fun recargarLista() {
        try {
            recargarListaSinProteger()
        } finally {
            // Pase lo que pase (error, cancelación), la lista deja de figurar "cargando".
            if (cargando) withContext(kotlinx.coroutines.NonCancellable + Dispatchers.Main) { cargando = false }
        }
    }

    private suspend fun recargarListaSinProteger() {
        withContext(Dispatchers.IO) {
            val nuevas = ArrayList<ExtDisponible>()
            val errs = ArrayList<String>()
            val metas = HashMap<String, RepoExt>()
            repos.filter { it.activo }.forEach { r ->
                // Si el listado falla o viene vacío, se prueban los otros nombres habituales de la misma carpeta.
                val urls = listOf(r.url) + ExtRepos.candidatos(ExtRepos.base(r.url)).filter { it != r.url }
                var primerError: String? = null
                var leido = false
                for (u in urls) {
                    val res = runCatching { ExtRepos.leerTienda(u).let { t -> t.repo to ExtRepos.extensiones(t) } }
                    val par = res.getOrNull()
                    if (par != null && par.second.isNotEmpty()) {
                        nuevas += par.second; metas[r.url] = par.first; leido = true; break
                    }
                    if (primerError == null) primerError = res.exceptionOrNull()?.let { it.message ?: it.javaClass.simpleName } ?: "listado vacío"
                }
                if (!leido) errs += "${r.nombre}: ${primerError ?: "sin respuesta"}"
            }
            // Una entrada por paquete y firma (si hay copias en varios listados con la misma firma, gana la versión más alta).
            val unicas = nuevas.groupBy { it.pkg to it.firma }.values.map { g -> g.maxByOrNull { it.versionCode }!! }
            withContext(Dispatchers.Main) {
                if (unicas.isNotEmpty() || errs.isEmpty()) {
                    disponiblesTodas = unicas
                    guardarCache(unicas)
                    reducirDisponibles()
                    sincronizarIdiomas()
                }
                if (metas.isNotEmpty()) {
                    repos = repos.map { r ->
                        val m = metas[r.url] ?: return@map r
                        r.copy(
                            nombre = m.nombre, web = m.web ?: r.web, etiqueta = m.etiqueta, claveFirma = m.claveFirma,
                            esLegacy = m.esLegacy, urlLista = m.urlLista, discord = m.discord,
                        )
                    }
                    guardarRepos(repos)
                }
                errores = errs
                cargando = false
            }
            // Con las claves de firma recién conocidas, más extensiones pueden ser de confianza.
            cargarFuentesReales()
        }
    }

    /** Extensiones instaladas que tienen una versión nueva en algún listado. */
    fun conActualizacion(): List<ExtInstalada> = instaladas.filter { it.actualizacion != null }

    private fun guardarCache(lista: List<ExtDisponible>) = runCatching {
        val a = JSONArray()
        lista.forEach { e ->
            a.put(
                JSONObject().put("nombre", e.nombre).put("pkg", e.pkg).put("apk", e.apkUrl).put("icono", e.iconoUrl)
                    .put("lang", e.lang).put("code", e.versionCode).put("version", e.versionName).put("nsfw", e.nsfw)
                    .put("repo", e.repoUrl).put("sigla", e.repoSigla).put("lib", e.libVersion).put("firma", e.firma)
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
                    libVersion = o.optDouble("lib", 0.0), firma = o.optString("firma"),
                )
            }
            disponiblesTodas = disponibles
            reducirDisponibles()
            sincronizarIdiomas()
        }
    }

    // ---- Extensiones instaladas ----
    /** Vuelve a leer las extensiones instaladas (en segundo plano) y carga sus fuentes. */
    fun refrescarInstaladas() {
        scope.launch {
            leerInstaladas()
            cargarFuentesReales()
        }
    }

    @Suppress("DEPRECATION")
    private fun leerInstaladas() {
        val pm = app.packageManager
        val nombres = runCatching { pm.getInstalledPackages(PackageManager.GET_CONFIGURATIONS) }
            .getOrDefault(emptyList())
            .filter { CargadorExt.esExtension(it) }
            .map { it.packageName }
        instaladasRaw = nombres.mapNotNull { n ->
            runCatching { CargadorExt.leer(pm, pm.getPackageInfo(n, CargadorExt.FLAGS)) }.getOrNull()
        }.sortedBy { it.nombre.lowercase() }
        fuentesCargadas.keys.filter { k -> instaladasRaw.none { it.pkg == k } }.forEach { fuentesCargadas.remove(it) }
        reducirDisponibles()
        sincronizarIdiomas()
    }

    // ---- Confianza (como en Mihon: hay que pulsar "Confiar" antes de usar una extensión) ----
    fun confiar(e: ExtInstalada) {
        confiadas += "${e.pkg}:${e.firma}"
        prefs.edit().putStringSet("confiadas", confiadas.toSet()).apply()
        confiadasVersion++
        scope.launch { cargarFuentesReales(e.pkg) }
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
            com.example.lector.ui.Avisos.mostrar("Permite instalar apps desde Lector y vuelve a pulsar Instalar")
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
        if (!idiomasPersonalizados) {
            idiomasPersonalizados = true
            prefs.edit().putBoolean("idiomas_pers", true).apply()
        }
        idiomas = if (lang in idiomas) idiomas - lang else idiomas + lang
        prefs.edit().putStringSet("idiomas", idiomas).apply()
    }

    fun alternarFijada(id: Long) {
        val s = id.toString()
        fijadas = if (s in fijadas) fijadas - s else fijadas + s
        prefs.edit().putStringSet("fijadas", fijadas).apply()
    }

    private fun cargarNovedades(): List<Long> {
        val guardado = prefs.getString("novedades", null)
        return if (guardado != null) guardado.split(',').mapNotNull { it.toLongOrNull() }
        else (prefs.getStringSet("fijadas", emptySet()) ?: emptySet()).mapNotNull { it.toLongOrNull() }
    }

    fun fijarNovedades(lista: List<Long>) {
        novedades = lista.distinct()
        prefs.edit().putString("novedades", novedades.joinToString(",")).apply()
    }

    fun alternarNovedad(id: Long) = fijarNovedades(if (id in novedades) novedades - id else novedades + id)

    /** Marca varios capítulos como leídos de una vez (una sola escritura). */
    fun marcarLeidos(fuenteId: Long, urls: Collection<String>) {
        if (urls.isEmpty()) return
        val claves = urls.map { "$fuenteId|$it" }
        var cambioPaginas = false
        claves.forEach { if (paginasCap.remove(it) != null) cambioPaginas = true }
        if (cambioPaginas) guardarPaginasCap()
        leidos = leidos + claves
        prefs.edit().putStringSet("leidos", leidos).apply()
    }

    fun desmarcarLeidos(fuenteId: Long, urls: Collection<String>) {
        if (urls.isEmpty()) return
        leidos = leidos - urls.map { "$fuenteId|$it" }.toSet()
        prefs.edit().putStringSet("leidos", leidos).apply()
    }

    fun marcarVarios(fuenteId: Long, urls: Collection<String>, marcar: Boolean) {
        val claves = urls.map { "$fuenteId|$it" }.toSet()
        marcados = if (marcar) marcados + claves else marcados - claves
        prefs.edit().putStringSet("marcados", marcados).apply()
    }

    // ---- Lista de capítulos de la ficha: filtros y orden (como Komikku) ----
    var capNoLeidos by com.example.lector.prefEnum(prefs, "cap_no_leidos", com.example.lector.Tri.IGNORAR)
    var capDescargados by com.example.lector.prefEnum(prefs, "cap_descargados", com.example.lector.Tri.IGNORAR)
    var capMarcados by com.example.lector.prefEnum(prefs, "cap_marcados", com.example.lector.Tri.IGNORAR)
    var capOrden by com.example.lector.prefInt(prefs, "cap_orden", 0) // 0 fuente · 1 número · 2 fecha de subida
    var capDescendente by com.example.lector.prefBool(prefs, "cap_desc", true)
    var capVerNumero by com.example.lector.prefBool(prefs, "cap_ver_numero", false) // "Capítulo N" en vez del nombre

    // ---- Búsquedas guardadas de cada fuente (TachiyomiSY/Komikku) ----
    data class BusquedaGuardada(val nombre: String, val consulta: String, val filtros: String?)

    var busquedasVersion by mutableStateOf(0); private set

    fun busquedas(fuenteId: Long): List<BusquedaGuardada> {
        @Suppress("UNUSED_VARIABLE") val v = busquedasVersion
        return runCatching {
            val a = JSONArray(prefs.getString("busq_$fuenteId", "[]"))
            (0 until a.length()).map { i ->
                val o = a.getJSONObject(i)
                BusquedaGuardada(o.getString("n"), o.optString("q"), o.optString("f").ifBlank { null })
            }
        }.getOrDefault(emptyList())
    }

    fun guardarBusqueda(fuenteId: Long, b: BusquedaGuardada) {
        val lista = busquedas(fuenteId).filterNot { it.nombre == b.nombre } + b
        val a = JSONArray()
        lista.forEach { a.put(JSONObject().put("n", it.nombre).put("q", it.consulta).put("f", it.filtros ?: "")) }
        prefs.edit().putString("busq_$fuenteId", a.toString()).apply()
        busquedasVersion++
    }

    fun borrarBusqueda(fuenteId: Long, nombre: String) {
        val lista = busquedas(fuenteId).filterNot { it.nombre == nombre }
        val a = JSONArray()
        lista.forEach { a.put(JSONObject().put("n", it.nombre).put("q", it.consulta).put("f", it.filtros ?: "")) }
        prefs.edit().putString("busq_$fuenteId", a.toString()).apply()
        busquedasVersion++
    }

    // ---- Copia de seguridad: lo que guarda este gestor (capítulos leídos, marcas, listados…) ----
    fun exportar(): JSONObject {
        val o = JSONObject()
        o.put("leidos", JSONArray(leidos.toList()))
        o.put("marcados", JSONArray(marcados.toList()))
        o.put("paginas_cap", JSONObject().also { j -> paginasCap.forEach { (k, v) -> j.put(k, v) } })
        o.put("repos", JSONArray(prefs.getString("repos", "[]")))
        o.put("fijadas", JSONArray(fijadas.toList()))
        o.put("novedades", JSONArray(novedades))
        o.put("confiadas", JSONArray(confiadas.toList()))
        o.put("nsfw", mostrarNsfw)
        val busq = JSONObject()
        prefs.all.keys.filter { it.startsWith("busq_") }.forEach { k -> busq.put(k, JSONArray(prefs.getString(k, "[]"))) }
        o.put("busquedas", busq)
        return o
    }

    fun importar(o: JSONObject) {
        fun conjunto(a: JSONArray?): Set<String> = if (a == null) emptySet() else (0 until a.length()).map { a.getString(it) }.toSet()
        leidos = leidos + conjunto(o.optJSONArray("leidos"))
        marcados = marcados + conjunto(o.optJSONArray("marcados"))
        o.optJSONObject("paginas_cap")?.let { j -> j.keys().forEach { k -> if ("$k" !in leidos) paginasCap[k] = j.optInt(k) } }
        fijadas = fijadas + conjunto(o.optJSONArray("fijadas"))
        confiadas += conjunto(o.optJSONArray("confiadas"))
        o.optJSONArray("novedades")?.let { a -> fijarNovedades(novedades + (0 until a.length()).map { a.getLong(it) }) }
        o.optJSONArray("repos")?.let { a ->
            val actuales = repos.map { it.url }.toSet()
            val nuevos = (0 until a.length()).mapNotNull { i ->
                val r = a.getJSONObject(i)
                if (r.optString("url") in actuales) null
                else RepoExt(
                    url = r.getString("url"), nombre = r.optString("nombre"), activo = r.optBoolean("activo", true),
                    web = r.optString("web").ifBlank { null }, etiqueta = r.optString("etiqueta"),
                    claveFirma = r.optString("clave"), esLegacy = r.optBoolean("legacy", false),
                    urlLista = r.optString("lista").ifBlank { null }, discord = r.optString("discord").ifBlank { null },
                )
            }
            if (nuevos.isNotEmpty()) { repos = repos + nuevos; guardarRepos(repos) }
        }
        o.optJSONObject("busquedas")?.let { b -> val e = prefs.edit(); b.keys().forEach { k -> e.putString(k, b.getJSONArray(k).toString()) }; e.apply(); busquedasVersion++ }
        prefs.edit().putStringSet("leidos", leidos).putStringSet("marcados", marcados)
            .putStringSet("fijadas", fijadas).putStringSet("confiadas", confiadas.toSet()).apply()
        guardarPaginasCap()
        confiadasVersion++
        actualizarLista()
    }

    fun marcarLeido(fuenteId: Long, url: String) {
        val k = "$fuenteId|$url"
        if (paginasCap.remove(k) != null) guardarPaginasCap() // terminado: ya no hay "página por la que vas"
        if (k in leidos) return
        leidos = leidos + k
        prefs.edit().putStringSet("leidos", leidos).apply()
    }

    /** Página por la que ibas en un capítulo (0 si no lo empezaste o ya está leído). */
    fun paginaDe(fuenteId: Long, url: String): Int =
        if (esLeido(fuenteId, url)) 0 else paginasCap["$fuenteId|$url"] ?: 0

    fun guardarPagina(fuenteId: Long, url: String, pagina: Int) {
        val k = "$fuenteId|$url"
        if (esLeido(fuenteId, url) || pagina <= 0 || paginasCap[k] == pagina) return
        paginasCap[k] = pagina
        guardarPaginasCap()
    }

    private fun guardarPaginasCap() {
        val o = JSONObject()
        paginasCap.forEach { (k, v) -> o.put(k, v) }
        prefs.edit().putString("paginas_cap", o.toString()).apply()
    }

    fun esLeido(fuenteId: Long, url: String) = "$fuenteId|$url" in leidos

    /** Vuelve a dejar un capítulo como no leído. */
    fun desmarcarLeido(fuenteId: Long, url: String) {
        val k = "$fuenteId|$url"
        if (k !in leidos) return
        leidos = leidos - k
        prefs.edit().putStringSet("leidos", leidos).apply()
    }

    /**
     * Migración de fuente: pasa el estado «leído» de los capítulos de la fuente vieja a los de la nueva, emparejándolos
     * por número de capítulo (como Komikku). Si no se puede emparejar (la fuente vieja no responde o no hay números),
     * se marcan como leídos los [respaldo] capítulos más antiguos. Devuelve cuántos capítulos quedan leídos.
     */
    fun migrarLeidos(
        viejaFuente: Long, viejos: List<CapituloFuente>?, nuevaFuente: Long, nuevos: List<CapituloFuente>, respaldo: Int,
    ): Int {
        val numerosLeidos = viejos.orEmpty().filter { it.numero >= 0 && esLeido(viejaFuente, it.url) }.map { it.numero }.toSet()
        val aMarcar = if (numerosLeidos.isNotEmpty()) {
            nuevos.filter { it.numero >= 0 && it.numero in numerosLeidos }
        } else {
            // Las fuentes listan lo más nuevo primero: los leídos son los últimos de la lista.
            nuevos.takeLast(respaldo.coerceIn(0, nuevos.size))
        }
        val claves = aMarcar.map { "$nuevaFuente|${it.url}" }.toSet()
        if (claves.isNotEmpty()) {
            leidos = leidos + claves
            prefs.edit().putStringSet("leidos", leidos).apply()
        }
        return nuevos.count { esLeido(nuevaFuente, it.url) }
    }

    fun esMarcado(fuenteId: Long, url: String) = "$fuenteId|$url" in marcados

    fun alternarMarcado(fuenteId: Long, url: String) {
        val k = "$fuenteId|$url"
        marcados = if (k in marcados) marcados - k else marcados + k
        prefs.edit().putStringSet("marcados", marcados).apply()
    }

    fun cambiarModoLector(m: Int) {
        modoLector = m
        prefs.edit().putInt("modo_lector", m).apply()
    }

    fun marcarReciente(id: Long) {
        recientes = (listOf(id.toString()) + recientes.filter { it != id.toString() }).take(5)
        prefs.edit().putString("recientes", recientes.joinToString(",")).apply()
    }
}
