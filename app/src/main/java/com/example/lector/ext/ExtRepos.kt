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

    fun leerIndice(bytes: ByteArray, urlIndice: String, repo: RepoExt): List<ExtDisponible> {
        val datos = if (bytes.size > 2 && bytes[0] == 0x1f.toByte() && bytes[1] == 0x8b.toByte())
            GZIPInputStream(bytes.inputStream()).use { it.readBytes() } else bytes
        val base = base(urlIndice)
        val esJson = datos.firstOrNull { !it.toInt().toChar().isWhitespace() }?.toInt()?.toChar() == '['
        return if (esJson) leerJson(datos, base, repo) else leerProtobuf(datos, base, repo)
    }

    // ---- JSON clásico (Suwayomi y la mayoría de listados) ----
    private fun leerJson(datos: ByteArray, base: String, repo: RepoExt): List<ExtDisponible> {
        val arr = JSONArray(String(datos, Charsets.UTF_8))
        val salida = ArrayList<ExtDisponible>()
        for (i in 0 until arr.length()) {
            runCatching {
                val o = arr.getJSONObject(i)
                val pkg = o.getString("pkg")
                val langExt = o.optString("lang", "")
                val fuentes = ArrayList<FuenteInfo>()
                o.optJSONArray("sources")?.let { s ->
                    for (j in 0 until s.length()) {
                        val so = s.getJSONObject(j)
                        fuentes += FuenteInfo(
                            id = so.optString("id", "0").toLongOrNull() ?: 0L,
                            nombre = so.optString("name"),
                            lang = so.optString("lang", langExt),
                            baseUrl = so.optString("baseUrl"),
                            pkg = pkg,
                        )
                    }
                }
                salida += ExtDisponible(
                    nombre = o.getString("name").removePrefix("Tachiyomi: "),
                    pkg = pkg,
                    apkUrl = "$base/apk/${o.getString("apk")}",
                    iconoUrl = "$base/icon/$pkg.png",
                    lang = langExt,
                    versionCode = o.optLong("code"),
                    versionName = o.optString("version"),
                    nsfw = o.optInt("nsfw") == 1,
                    fuentes = fuentes,
                    repoUrl = repo.url,
                    repoSigla = repo.sigla,
                )
            }
        }
        return salida
    }

    // ---- Protobuf (index.pb de Keiyoushi) ----
    // ¡OJO! ESQUEMA INFERIDO de una descripción de terceros, NO verificado contra el .proto oficial:
    //   campo 101 (repetido) = Extensión { 1 nombre, 2 pkg, 3 {1 apkUrl, 2 iconoUrl}, 5 versionCode,
    //   6 versionName, 8 (repetido) Fuente { 1 id, 2 nombre, 3 lang, 4 baseUrl } }.
    // Si algún campo no sale bien, comprobar el esquema real y corregir aquí (ver HANDOFF.md).
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

    private fun leerProtobuf(datos: ByteArray, base: String, repo: RepoExt): List<ExtDisponible> {
        fun abs(u: String) = if (u.startsWith("http")) u else "$base/${u.trimStart('/')}"
        val salida = ArrayList<ExtDisponible>()
        campos(datos) { num, _, bytes ->
            if (num != 101 || bytes == null) return@campos
            runCatching {
                var nombre = ""
                var pkg = ""
                var apk = ""
                var icono = ""
                var vcode = 0L
                var vname = ""
                val fuentes = ArrayList<FuenteInfo>()
                campos(bytes) { n, v, d ->
                    when (n) {
                        1 -> nombre = d?.toString(Charsets.UTF_8) ?: ""
                        2 -> pkg = d?.toString(Charsets.UTF_8) ?: ""
                        3 -> if (d != null) campos(d) { n2, _, d2 ->
                            if (d2 != null && n2 == 1) apk = d2.toString(Charsets.UTF_8)
                            if (d2 != null && n2 == 2) icono = d2.toString(Charsets.UTF_8)
                        }
                        5 -> vcode = v
                        6 -> vname = d?.toString(Charsets.UTF_8) ?: ""
                        8 -> if (d != null) {
                            var id = 0L
                            var sn = ""
                            var sl = ""
                            var sb = ""
                            campos(d) { n2, v2, d2 ->
                                when (n2) {
                                    1 -> id = d2?.toString(Charsets.UTF_8)?.toLongOrNull() ?: v2
                                    2 -> sn = d2?.toString(Charsets.UTF_8) ?: ""
                                    3 -> sl = d2?.toString(Charsets.UTF_8) ?: ""
                                    4 -> sb = d2?.toString(Charsets.UTF_8) ?: ""
                                }
                            }
                            fuentes += FuenteInfo(id, sn, sl, sb, pkg)
                        }
                    }
                }
                if (pkg.isEmpty()) return@runCatching
                val idiomas = fuentes.map { it.lang }.filter { it.isNotEmpty() }.distinct()
                val lang = when {
                    idiomas.size == 1 -> idiomas[0]
                    idiomas.size > 1 -> "all"
                    else -> pkg.removePrefix("eu.kanade.tachiyomi.extension.").substringBefore('.').ifEmpty { "all" }
                }
                salida += ExtDisponible(
                    nombre = nombre.removePrefix("Tachiyomi: "),
                    pkg = pkg,
                    apkUrl = if (apk.isEmpty()) "" else abs(apk),
                    iconoUrl = if (icono.isEmpty()) "" else abs(icono),
                    lang = lang,
                    versionCode = vcode,
                    versionName = vname,
                    nsfw = false,
                    fuentes = fuentes.map { it.copy(pkg = pkg) },
                    repoUrl = repo.url,
                    repoSigla = repo.sigla,
                )
            }
        }
        return salida
    }
}
