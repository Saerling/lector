// Copiado de BitChord (https://github.com/kushagrasinghx/BitChord, GPL-3.0): app/src/main/java/com/music/bitchord/data/webdav/WebDavClient.kt
// y WebDavConfig.kt (normalizeUrl, isConfigured, basicAuthHeader, hostOf). Adaptado a Shiori: cliente OkHttp propio,
// se quitan las partes de música (listado de audio, nombres de pista) y se añade GET para bajar copias.
package com.example.lector.bitchord

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import org.w3c.dom.Element
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Cliente WebDAV mínimo: PROPFIND (Depth 1), PUT, GET y MKCOL con autenticación HTTP Basic.
 * El XML se lee con el parser DOM de la plataforma.
 */
object WebDavClient {

    private val http by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    data class Entry(
        val url: String,
        val displayName: String,
        val isCollection: Boolean,
        val contentType: String? = null,
        val lastModified: Long = 0L,
        val size: Long = -1L,
    )

    private val PROPFIND_BODY = """<?xml version="1.0" encoding="utf-8"?>
<d:propfind xmlns:d="DAV:">
  <d:prop>
    <d:displayname/>
    <d:resourcetype/>
    <d:getcontenttype/>
    <d:getlastmodified/>
    <d:getcontentlength/>
  </d:prop>
</d:propfind>
""".trimIndent()

    // ── De WebDavConfig ──

    fun normalizeUrl(raw: String): String {
        val trimmed = raw.trim().trimEnd('/')
        if (trimmed.isEmpty()) return ""
        return if (trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true)
        ) trimmed else "https://$trimmed"
    }

    fun isConfigured(url: String): Boolean {
        val normalized = normalizeUrl(url)
        if (!normalized.startsWith("http://", ignoreCase = true) &&
            !normalized.startsWith("https://", ignoreCase = true)
        ) return false
        return hostOf(normalized) != null
    }

    fun basicAuthHeader(username: String, password: String): String? {
        if (username.isBlank() && password.isBlank()) return null
        val encoded = java.util.Base64.getEncoder().encodeToString("$username:$password".toByteArray(Charsets.UTF_8))
        return "Basic $encoded"
    }

    fun hostOf(url: String): String? = runCatching {
        java.net.URI(normalizeUrl(url)).host?.lowercase(Locale.ROOT)
    }.getOrNull()?.takeIf { it.isNotBlank() }

    // ── Operaciones ──

    suspend fun testConnection(url: String, username: String, password: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val target = normalizeUrl(url)
                require(isConfigured(target)) { "Escribe una dirección http(s) válida" }
                http.newCall(propfindRequest(target, "0", username, password)).execute().use { response ->
                    if (response.code !in 200..299 && response.code != 207) {
                        throw WebDavException(mensajeCodigo(response.code))
                    }
                }
            }
        }

    private fun propfindRequest(url: String, depth: String, username: String, password: String): Request {
        val builder = Request.Builder()
            .url(url)
            .header("Depth", depth)
            .method("PROPFIND", PROPFIND_BODY.toRequestBody("application/xml; charset=utf-8".toMediaType()))
        basicAuthHeader(username, password)?.let { builder.header("Authorization", it) }
        return builder.build()
    }

    /** Crea la colección [dirUrl] si no existe (405 = ya estaba). */
    suspend fun ensureCollection(dirUrl: String, username: String, password: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val builder = Request.Builder().url(dirUrl).method("MKCOL", null)
                basicAuthHeader(username, password)?.let { builder.header("Authorization", it) }
                http.newCall(builder.build()).execute().use { response ->
                    if (response.code !in 200..299 && response.code != 405) {
                        throw WebDavException("No se pudo crear la carpeta (${response.code})")
                    }
                }
            }
        }

    /** Sube [bytes] a [fileUrl] (sobrescribe). */
    suspend fun putBytes(
        fileUrl: String,
        bytes: ByteArray,
        mimeType: String,
        username: String,
        password: String,
        onProgress: ((Long) -> Unit)? = null,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val body = BytesBody(bytes, mimeType.toMediaType(), onProgress)
            val builder = Request.Builder().url(fileUrl).put(body)
            basicAuthHeader(username, password)?.let { builder.header("Authorization", it) }
            http.newCall(builder.build()).execute().use { response ->
                if (response.code !in 200..299) throw WebDavException("La subida falló: ${mensajeCodigo(response.code)}")
            }
        }
    }

    suspend fun getBytes(fileUrl: String, username: String, password: String): Result<ByteArray> =
        withContext(Dispatchers.IO) {
            runCatching {
                val builder = Request.Builder().url(fileUrl).get()
                basicAuthHeader(username, password)?.let { builder.header("Authorization", it) }
                http.newCall(builder.build()).execute().use { response ->
                    if (response.code !in 200..299) throw WebDavException("La descarga falló: ${mensajeCodigo(response.code)}")
                    response.body.bytes()
                }
            }
        }

    suspend fun delete(fileUrl: String, username: String, password: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val builder = Request.Builder().url(fileUrl).delete()
                basicAuthHeader(username, password)?.let { builder.header("Authorization", it) }
                http.newCall(builder.build()).execute().use { response ->
                    if (response.code !in 200..299 && response.code != 404) throw WebDavException("No se pudo borrar (${response.code})")
                }
            }
        }

    suspend fun list(dirUrl: String, username: String, password: String): Result<List<Entry>> =
        withContext(Dispatchers.IO) { propfind(dirUrl, username, password) }

    private fun propfind(dirUrl: String, username: String, password: String): Result<List<Entry>> = runCatching {
        http.newCall(propfindRequest(dirUrl, "1", username, password)).execute().use { response ->
            if (response.code == 404) return@runCatching emptyList()
            if (response.code != 207 && response.code !in 200..299) {
                throw WebDavException("El listado falló: ${mensajeCodigo(response.code)}")
            }
            val body = response.body.string()
            if (body.isBlank()) return@runCatching emptyList()
            parseMultistatus(body, dirUrl)
        }
    }

    /** Lee una respuesta multistatus de PROPFIND; descarta la entrada del propio directorio. */
    fun parseMultistatus(xml: String, dirUrl: String): List<Entry> {
        if (xml.isBlank()) return emptyList()
        val doc = runCatching {
            val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            factory.newDocumentBuilder().parse(xml.byteInputStream(Charsets.UTF_8))
        }.getOrNull() ?: return emptyList()
        val responses = doc.getElementsByTagNameNS("*", "response")
        val out = ArrayList<Entry>(responses.length)
        for (i in 0 until responses.length) {
            val node = responses.item(i) as? Element ?: continue
            val href = node.getElementsByTagNameNS("*", "href").item(0)?.textContent?.trim().orEmpty()
            if (href.isEmpty()) continue
            val url = resolveHref(dirUrl, href)
            if (isSameUrl(url, dirUrl)) continue
            val displayName = node.getElementsByTagNameNS("*", "displayname")
                .item(0)?.textContent?.trim().orEmpty()
                .ifBlank { decodedName(url) }
            val resourceType = node.getElementsByTagNameNS("*", "resourcetype").item(0) as? Element
            val isCollection = resourceType
                ?.getElementsByTagNameNS("*", "collection")?.length?.let { it > 0 } == true
            val contentType = node.getElementsByTagNameNS("*", "getcontenttype")
                .item(0)?.textContent?.trim()?.takeIf { it.isNotBlank() }
            val modified = node.getElementsByTagNameNS("*", "getlastmodified").item(0)?.textContent?.trim()
                ?.let { s -> runCatching { java.time.ZonedDateTime.parse(s, java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() }.getOrNull() }
                ?: 0L
            val size = node.getElementsByTagNameNS("*", "getcontentlength").item(0)?.textContent?.trim()?.toLongOrNull() ?: -1L
            out.add(Entry(url, displayName, isCollection, contentType, modified, size))
        }
        return out
    }

    fun resolveHref(baseUrl: String, href: String): String {
        val trimmed = href.trim()
        if (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) {
            return trimmed
        }
        val base = normalizeUrl(baseUrl)
        val schemeEnd = base.indexOf("://")
        if (schemeEnd < 0) return trimmed
        val pathStart = base.indexOf('/', schemeEnd + 3)
        val schemeHost = if (pathStart < 0) base else base.substring(0, pathStart)
        return if (trimmed.startsWith("/")) "$schemeHost$trimmed" else "$base/$trimmed"
    }

    private fun isSameUrl(a: String, b: String): Boolean {
        fun norm(u: String) = runCatching { URLDecoder.decode(u.trim().trimEnd('/'), "UTF-8") }.getOrDefault(u).lowercase()
        return norm(a) == norm(b)
    }

    private fun decodedName(url: String): String = runCatching {
        URLDecoder.decode(url.trimEnd('/').substringAfterLast('/'), "UTF-8")
    }.getOrDefault(url.trimEnd('/').substringAfterLast('/'))

    /** `root/<segment>` con el segmento codificado (`+` → `%20`: es una ruta, no una consulta). */
    fun joinUrl(root: String, segment: String): String {
        val encoded = URLEncoder.encode(segment, "UTF-8").replace("+", "%20")
        return "${root.trimEnd('/')}/$encoded"
    }

    private fun mensajeCodigo(code: Int) = when (code) {
        401 -> "usuario o contraseña incorrectos (401)"
        403 -> "sin permiso (403)"
        404 -> "no existe esa ruta (404)"
        405 -> "el servidor no acepta WebDAV en esa ruta (405)"
        else -> "el servidor respondió $code"
    }

    class WebDavException(message: String) : Exception(message)

    private class BytesBody(
        private val bytes: ByteArray,
        private val mime: MediaType?,
        private val onProgress: ((Long) -> Unit)?,
    ) : RequestBody() {
        override fun contentType(): MediaType? = mime
        override fun contentLength(): Long = bytes.size.toLong()
        override fun writeTo(sink: BufferedSink) {
            var off = 0
            while (off < bytes.size) {
                val n = minOf(8192, bytes.size - off)
                sink.write(bytes, off, n)
                off += n
                onProgress?.invoke(off.toLong())
            }
        }
    }
}
