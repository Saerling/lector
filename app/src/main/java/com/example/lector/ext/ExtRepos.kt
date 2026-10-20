package com.example.lector.ext

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.GZIPInputStream

/** Descarga y lectura de los listados de extensiones (index.min.json / index.json / index.pb). */
object ExtRepos {
    private const val UA = "Mozilla/5.0 (Linux; Android 14) Lector/1.0"
    private val terminaciones = listOf("index.min.json", "index.json", "index.pb")

    /** Primera dirección que se probaría para lo escrito (null si no es una dirección válida). */
    fun normalizar(entrada: String): String? = candidatos(entrada).firstOrNull()

    private fun indicesEn(carpeta: String) = terminaciones.map { "$carpeta/$it" }

    // Convierte una dirección de la web de GitHub en la de raw.githubusercontent.com.
    // Devuelve la lista de carpetas posibles (si no se sabe la rama se prueban las habituales).
    private fun carpetasGitHub(u: String): List<String>? {
        val m = Regex("^https?://(?:www\\.)?github\\.com/([^/]+)/([^/]+?)(?:\\.git)?(?:/(tree|blob|raw)/([^/]+)(/.*)?)?$")
            .matchEntire(u) ?: return null
        val (owner, repo, tipo, rama, resto) = m.destructured
        if (tipo.isEmpty()) {
            return listOf("repo", "main", "master").map { "https://raw.githubusercontent.com/$owner/$repo/$it" }
        }
        val ruta = resto.trimEnd('/')
        val base = "https://raw.githubusercontent.com/$owner/$repo/$rama"
        return listOf(if (ruta.isEmpty() || tipo == "tree") base else base + ruta)
    }

    /**
     * Direcciones posibles del índice para lo que escribió el usuario, de la más probable a la menos.
     * Acepta: la dirección del índice (index.min.json / index.json / index.pb), la de repo.json,
     * la carpeta del listado, páginas de GitHub, "usuario/repositorio" y enlaces tachiyomi://add-repo?url=...
     */
    fun candidatos(entrada: String): List<String> {
        var u = entrada.trim().trim('"', '\'', '<', '>').trim()
        if (u.isEmpty()) return emptyList()
        // Enlaces de "añadir repositorio" de Mihon/Tachiyomi/Komikku.
        if (u.contains("add-repo", ignoreCase = true) && u.contains("url=")) {
            u = runCatching { java.net.URLDecoder.decode(u.substringAfter("url=").substringBefore('&'), "UTF-8") }
                .getOrDefault(u)
        }
        // Atajo estilo TachiyomiSY: usuario/repositorio
        if (!u.startsWith("http") && Regex("[\\w.-]+/[\\w.-]+").matches(u)) u = "https://github.com/$u"
        if (!u.startsWith("http://") && !u.startsWith("https://")) return emptyList()
        u = u.substringBefore('#').trimEnd('/')

        val gh = carpetasGitHub(u)
        if (gh != null) {
            // Si la página de GitHub ya apunta a un fichero (…/blob/rama/repo.json), se trabaja con su carpeta.
            val primera = gh[0]
            val esFichero = gh.size == 1 && (terminaciones + "repo.json").any { primera.endsWith("/$it") }
            return if (esFichero) candidatos(primera) else gh.flatMap { indicesEn(it) }.distinct()
        }
        if (terminaciones.any { u.endsWith("/$it") }) {
            val carpeta = u.substringBeforeLast('/')
            return (listOf(u) + indicesEn(carpeta)).distinct()
        }
        if (u.endsWith("/repo.json")) return indicesEn(u.removeSuffix("/repo.json"))
        return indicesEn(u)
    }

    /** Identifica un listado por su carpeta, para no añadirlo dos veces aunque se escriba distinto. */
    fun carpeta(url: String): String {
        val c = carpetasGitHub(url.trimEnd('/'))?.firstOrNull() ?: url
        val sin = if (c.substringAfterLast('/').contains('.')) c.substringBeforeLast('/') else c
        return sin.trimEnd('/').lowercase()
    }

    /** Nombre y web del listado según su repo.json (como hace Komikku). Null si no lo tiene. */
    fun meta(carpeta: String): Pair<String, String?>? = runCatching {
        val o = JSONObject(String(descargar("$carpeta/repo.json"), Charsets.UTF_8)).getJSONObject("meta")
        val nombre = o.optString("name").ifBlank { o.optString("shortName") }
        if (nombre.isBlank()) null else nombre to o.optString("website").ifBlank { null }
    }.getOrNull()

    fun base(urlIndice: String) = urlIndice.substringBeforeLast('/')

    fun nombreDe(url: String): String {
        val p = url.removePrefix("https://").removePrefix("http://").split('/')
        return if ((p[0] == "raw.githubusercontent.com" || p[0] == "github.com") && p.size > 1) p[1] else p[0]
    }

    fun webDe(url: String): String? {
        val p = url.removePrefix("https://").removePrefix("http://").split('/')
        return when {
            (p[0] == "raw.githubusercontent.com" || p[0] == "github.com") && p.size > 2 ->
                "https://github.com/${p[1]}/${p[2]}"
            else -> "https://${p[0]}"
        }
    }

    private fun abrir(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", UA)
        }

    fun descargar(url: String): ByteArray {
        val c = abrir(url)
        try {
            if (c.responseCode !in 200..299) throw IllegalStateException("HTTP ${c.responseCode}")
            return c.inputStream.use { it.readBytes() }
        } finally {
            c.disconnect()
        }
    }

    fun descargarA(url: String, destino: File, alProgreso: (Int) -> Unit) {
        val c = abrir(url)
        try {
            if (c.responseCode !in 200..299) throw IllegalStateException("HTTP ${c.responseCode}")
            val total = c.contentLengthLong
            c.inputStream.use { ent ->
                destino.outputStream().use { sal ->
                    val buf = ByteArray(32 * 1024)
                    var hecho = 0L
                    var ultimo = -1
                    while (true) {
                        val n = ent.read(buf)
                        if (n < 0) break
                        sal.write(buf, 0, n)
                        hecho += n
                        if (total > 0) {
                            val p = (hecho * 100 / total).toInt()
                            if (p != ultimo) { ultimo = p; alProgreso(p) }
                        }
                    }
                }
            }
        } finally {
            c.disconnect()
        }
    }

    // ───────────────────────────────────────────────────────────────────────────────────────────
    // Lectura de listados (tiendas) de extensiones. Port de Komikku:
    //   data/.../ExtensionStoreService.kt (fetch y getExtensions) y los modelos NetworkExtensionStore,
    //   NetworkLegacyExtension y NetworkLegacyExtensionRepo.
    // Formatos que entiende, igual que Komikku:
    //   · "[...": índice clásico index.min.json (+ repo.json con nombre, web y clave de firma)
    //   · "{...": repo.json clásico (con "index_v2" apuntando al índice nuevo) o tienda nueva en JSON
    //   · otro:   tienda nueva en protobuf (index.pb), con o sin gzip
    // ───────────────────────────────────────────────────────────────────────────────────────────

    class FuenteCruda(val id: Long, val nombre: String, val lang: String, val home: String)

    class ExtCruda(
        val nombre: String, val pkg: String, val apk: String, val icono: String, val lib: Double,
        val code: Long, val version: String, val nsfw: Boolean, val lang: String, val fuentes: List<FuenteCruda>,
    )

    /** Un listado ya leído: sus datos y, si venía en el mismo archivo, la lista de extensiones. */
    class TiendaLeida(val repo: RepoExt, val crudas: List<ExtCruda>?)

    private fun descomprimir(bytes: ByteArray): ByteArray {
        val gz = bytes.size > 2 && bytes[0] == 0x1f.toByte() && bytes[1] == 0x8b.toByte()
        return if (gz) GZIPInputStream(bytes.inputStream()).use { it.readBytes() } else bytes
    }

    private fun sinBom(b: ByteArray): ByteArray =
        if (b.size >= 3 && b[0] == 0xEF.toByte() && b[1] == 0xBB.toByte() && b[2] == 0xBF.toByte()) b.copyOfRange(3, b.size) else b

    private fun descargarLista(url: String): ByteArray = sinBom(descomprimir(descargar(url)))

    private const val CORCHETE = 0x5B.toByte() // '['
    private const val LLAVE = 0x7B.toByte() // '{'

    /** Equivale a ExtensionStoreService.fetch: lee un listado y devuelve sus datos. Lanza error si no es válido. */
    fun leerTienda(url: String): TiendaLeida {
        val datos = descargarLista(url)
        if (datos.isEmpty()) error("El listado está vacío")
        return when (datos[0]) {
            CORCHETE -> {
                // Índice clásico: los datos del listado están en repo.json, junto al índice.
                val sufijo = listOf("/index.min.json", "/index.json").firstOrNull { url.endsWith(it) }
                    ?: error("La dirección de un listado clásico debe terminar en /index.min.json")
                val repoUrl = url.removeSuffix(sufijo) + "/repo.json"
                val meta = runCatching { String(descargarLista(repoUrl), Charsets.UTF_8) }.getOrNull()
                if (meta == null) {
                    // Listado sin repo.json (los hay antiguos): se usa el propio índice, sin clave de firma.
                    val repo = RepoExt(url, nombreDe(url), true, webDe(url), esLegacy = true)
                    TiendaLeida(repo, crudasClasicas(datos, url.substringBeforeLast('/')))
                } else {
                    val t = leerRepoClasico(JSONObject(meta), repoUrl)
                    if (t.repo.url == repoUrl) TiendaLeida(t.repo, crudasClasicas(datos, repoUrl.substringBeforeLast('/'))) else t
                }
            }
            LLAVE -> {
                val o = JSONObject(String(datos, Charsets.UTF_8))
                if (o.has("meta")) leerRepoClasico(o, url) else leerTiendaJson(o, url)
            }
            else -> leerTiendaProto(datos, url)
        }
    }

    // repo.json clásico: { "meta": { name, shortName, website, signingKeyFingerprint }, "index_v2": "..." }
    private fun leerRepoClasico(o: JSONObject, url: String): TiendaLeida {
        val v2 = o.optString("index_v2").ifBlank { null }
        if (v2 != null) return leerTienda(v2)
        val m = o.getJSONObject("meta")
        val nombre = m.optString("name").ifBlank { m.optString("shortName") }.ifBlank { nombreDe(url) }
        val repo = RepoExt(
            url = url, nombre = nombre, activo = true,
            web = m.optString("website").ifBlank { webDe(url) },
            etiqueta = m.optString("shortName").ifBlank { nombre },
            claveFirma = m.optString("signingKeyFingerprint"),
            esLegacy = true,
        )
        return TiendaLeida(repo, null)
    }

    // Tienda nueva en JSON (mismos campos que el protobuf, con nombres en camelCase).
    private fun leerTiendaJson(o: JSONObject, url: String): TiendaLeida {
        val contacto = o.optJSONObject("contact")
        val nombre = o.optString("name").ifBlank { nombreDe(url) }
        val repo = RepoExt(
            url = url, nombre = nombre, activo = true,
            web = contacto?.optString("website")?.ifBlank { null } ?: webDe(url),
            etiqueta = o.optString("badgeLabel").ifBlank { nombre },
            claveFirma = o.optString("signingKey"),
            esLegacy = false,
            urlLista = o.optString("extensionListUrl").ifBlank { null },
            discord = contacto?.optString("discord")?.ifBlank { null },
        )
        return TiendaLeida(repo, o.optJSONObject("extensionList")?.optJSONArray("extensions")?.let { crudasJson(it) })
    }

    private fun avisoEsNsfw(v: Any?): Boolean = when (v) {
        is Number -> v.toInt() >= 2 // 2 = MIXED, 3 = NSFW
        is String -> v.contains("MIXED", true) || v.contains("NSFW", true)
        else -> false
    }

    private fun langDe(fuentes: List<FuenteCruda>): String {
        val l = fuentes.map { it.lang }.toSet()
        return if (l.size == 1) l.first() else "all"
    }

    private fun crudasJson(arr: org.json.JSONArray): List<ExtCruda> {
        val salida = ArrayList<ExtCruda>()
        for (i in 0 until arr.length()) {
            runCatching {
                val o = arr.getJSONObject(i)
                val fa = o.optJSONArray("sources")
                val fuentes = (0 until (fa?.length() ?: 0)).map { j ->
                    val so = fa!!.getJSONObject(j)
                    FuenteCruda(so.optString("id", "0").toLongOrNull() ?: 0L, so.optString("name"), so.optString("language"), so.optString("homeUrl"))
                }
                val res = o.optJSONObject("resources")
                salida += ExtCruda(
                    nombre = o.getString("name"), pkg = o.getString("packageName"),
                    apk = res?.optString("apkUrl").orEmpty(), icono = res?.optString("iconUrl").orEmpty(),
                    lib = o.opt("extensionLib")?.toString()?.toDoubleOrNull() ?: 0.0,
                    code = o.optLong("versionCode"), version = o.optString("versionName"),
                    nsfw = avisoEsNsfw(o.opt("contentWarning")), lang = langDe(fuentes), fuentes = fuentes,
                )
            }
        }
        return salida
    }

    // index.min.json clásico (NetworkLegacyExtension): apk e icono salen de la carpeta del listado.
    private fun crudasClasicas(datos: ByteArray, base: String): List<ExtCruda> {
        val arr = JSONArray(String(datos, Charsets.UTF_8))
        val salida = ArrayList<ExtCruda>()
        for (i in 0 until arr.length()) {
            runCatching {
                val o = arr.getJSONObject(i)
                val pkg = o.getString("pkg")
                val lang = o.optString("lang", "")
                val nombre = o.getString("name").substringAfter("Tachiyomi: ")
                val sa = o.optJSONArray("sources")
                val fuentes = if (sa == null || sa.length() == 0) {
                    listOf(FuenteCruda(0L, nombre, lang, ""))
                } else (0 until sa.length()).map { j ->
                    val so = sa.getJSONObject(j)
                    FuenteCruda(so.optString("id", "0").toLongOrNull() ?: 0L, so.optString("name"), so.optString("lang", lang), so.optString("baseUrl"))
                }
                val version = o.optString("version")
                salida += ExtCruda(
                    nombre = nombre, pkg = pkg, apk = "$base/apk/${o.getString("apk")}", icono = "$base/icon/$pkg.png",
                    lib = version.substringBeforeLast('.').toDoubleOrNull() ?: 0.0,
                    code = o.optLong("code"), version = version, nsfw = o.optInt("nsfw") == 1,
                    lang = lang, fuentes = fuentes,
                )
            }
        }
        return salida
    }

    // ---- Protobuf: esquema real de NetworkExtensionStore (Komikku) ----
    //   Tienda:    1 nombre, 2 etiqueta, 3 clave de firma, 4 contacto{1 web, 2 discord},
    //              101 lista{1 (repetido) extensión}, 102 dirección de la lista aparte
    //   Extensión: 1 nombre, 2 paquete, 3 recursos{1 apk, 2 icono}, 4 extensionLib (texto), 5 versionCode,
    //              6 versionName, 7 aviso de contenido (0 sin dato, 1 seguro, 2 mixto, 3 NSFW),
    //              8 (repetido) fuente{1 id, 2 nombre, 3 idioma, 4 web, 5 espejos, 7 mensaje}
    private fun campos(b: ByteArray, cb: (num: Int, valor: Long, datos: ByteArray?) -> Unit) {
        var p = 0
        fun varint(): Long {
            var r = 0L
            var s = 0
            while (p < b.size) {
                val x = b[p++].toInt() and 0xff
                r = r or ((x and 0x7f).toLong() shl s)
                if (x and 0x80 == 0) break
                s += 7
            }
            return r
        }
        while (p < b.size) {
            val clave = varint().toInt()
            val num = clave ushr 3
            when (clave and 7) {
                0 -> cb(num, varint(), null)
                1 -> p += 8
                2 -> {
                    val n = varint().toInt()
                    if (n < 0 || p + n > b.size) return
                    cb(num, 0L, b.copyOfRange(p, p + n))
                    p += n
                }
                5 -> p += 4
                else -> return
            }
        }
    }

    private fun txt(d: ByteArray?) = d?.toString(Charsets.UTF_8).orEmpty()

    private fun protoFuente(b: ByteArray): FuenteCruda {
        var id = 0L; var n = ""; var l = ""; var h = ""
        campos(b) { num, v, d ->
            when (num) {
                1 -> id = if (d != null) txt(d).toLongOrNull() ?: 0L else v
                2 -> n = txt(d)
                3 -> l = txt(d)
                4 -> h = txt(d)
            }
        }
        return FuenteCruda(id, n, l, h)
    }

    private fun protoExt(b: ByteArray): ExtCruda {
        var nombre = ""; var pkg = ""; var apk = ""; var icono = ""; var lib = ""
        var code = 0L; var version = ""; var aviso = 0L
        val fuentes = ArrayList<FuenteCruda>()
        campos(b) { n, v, d ->
            when (n) {
                1 -> nombre = txt(d)
                2 -> pkg = txt(d)
                3 -> if (d != null) campos(d) { n2, _, d2 ->
                    if (n2 == 1) apk = txt(d2)
                    if (n2 == 2) icono = txt(d2)
                }
                4 -> lib = txt(d)
                5 -> code = v
                6 -> version = txt(d)
                7 -> aviso = v
                8 -> if (d != null) fuentes += protoFuente(d)
            }
        }
        require(pkg.isNotEmpty()) { "Extensión sin paquete" }
        return ExtCruda(nombre, pkg, apk, icono, lib.toDoubleOrNull() ?: 0.0, code, version, aviso >= 2, langDe(fuentes), fuentes)
    }

    private fun protoLista(b: ByteArray): List<ExtCruda> {
        val r = ArrayList<ExtCruda>()
        campos(b) { n, _, d -> if (n == 1 && d != null) runCatching { r += protoExt(d) } }
        return r
    }

    private fun leerTiendaProto(datos: ByteArray, url: String): TiendaLeida {
        var nombre = ""; var etiqueta = ""; var clave = ""; var web = ""; var discord: String? = null
        var urlLista: String? = null
        var crudas: List<ExtCruda>? = null
        campos(datos) { n, _, d ->
            when (n) {
                1 -> nombre = txt(d)
                2 -> etiqueta = txt(d)
                3 -> clave = txt(d)
                4 -> if (d != null) campos(d) { n2, _, d2 ->
                    if (n2 == 1) web = txt(d2)
                    if (n2 == 2) discord = txt(d2).ifBlank { null }
                }
                101 -> if (d != null) crudas = protoLista(d)
                102 -> urlLista = txt(d).ifBlank { null }
            }
        }
        if (nombre.isEmpty() && clave.isEmpty() && crudas == null && urlLista == null) error("Formato de listado desconocido")
        val nom = nombre.ifBlank { nombreDe(url) }
        val repo = RepoExt(
            url = url, nombre = nom, activo = true, web = web.ifBlank { webDe(url) },
            etiqueta = etiqueta.ifBlank { nom }, claveFirma = clave, esLegacy = false,
            urlLista = urlLista, discord = discord,
        )
        return TiendaLeida(repo, crudas)
    }

    /** Equivale a ExtensionStoreService.getExtensions: las extensiones que ofrece un listado. */
    fun extensiones(t: TiendaLeida): List<ExtDisponible> {
        val repo = t.repo
        val base = repo.url.substringBeforeLast('/')
        fun abs(u: String) = if (u.isEmpty() || u.startsWith("http")) u else "$base/${u.trimStart('/')}"
        val crudas = t.crudas ?: run {
            when {
                repo.urlLista != null -> {
                    val d = descargarLista(repo.urlLista)
                    if (d.isNotEmpty() && d[0] == LLAVE) crudasJson(JSONObject(String(d, Charsets.UTF_8)).getJSONArray("extensions"))
                    else protoLista(d)
                }
                !repo.esLegacy -> leerTienda(repo.url).crudas ?: error("El listado no incluye extensiones")
                else -> {
                    val indice = if (repo.url.endsWith("/repo.json")) repo.url.removeSuffix("/repo.json") + "/index.min.json" else repo.url
                    crudasClasicas(descargarLista(indice), indice.substringBeforeLast('/'))
                }
            }
        }
        return crudas.map { e ->
            ExtDisponible(
                nombre = e.nombre, pkg = e.pkg, apkUrl = abs(e.apk), iconoUrl = abs(e.icono),
                lang = e.lang, versionCode = e.code, versionName = e.version, nsfw = e.nsfw,
                fuentes = e.fuentes.map { FuenteInfo(it.id, it.nombre, it.lang, it.home, e.pkg) },
                repoUrl = repo.url, repoSigla = repo.sigla, libVersion = e.lib, firma = repo.claveFirma,
            )
        }
    }
}
