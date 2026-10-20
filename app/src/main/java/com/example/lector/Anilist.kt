package com.example.lector

import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

// Una serie encontrada en AniList.
data class ResultadoAnilist(
    val id: Int,
    val titulo: String,
    val formato: String?,
    val portada: String? = null,
)

// Cliente mínimo de la API de AniList (GraphQL). Todas las funciones bloquean:
// llámalas desde un hilo de fondo (Dispatchers.IO) y captura las excepciones.
object Anilist {
    private const val API = "https://graphql.anilist.co"

    // Dirección a la que se envía al usuario para que autorice la app.
    // AniList devolverá el token a lector://anilist#access_token=...
    fun urlDeLogin(clientId: String) =
        "https://anilist.co/api/v2/oauth/authorize?client_id=$clientId&response_type=token"

    private fun consulta(token: String?, query: String, variables: JSONObject): JSONObject {
        val conexion = URL(API).openConnection() as HttpURLConnection
        try {
            conexion.requestMethod = "POST"
            conexion.connectTimeout = 15000
            conexion.readTimeout = 15000
            conexion.doOutput = true
            conexion.setRequestProperty("Content-Type", "application/json")
            conexion.setRequestProperty("Accept", "application/json")
            if (token != null) conexion.setRequestProperty("Authorization", "Bearer $token")

            val cuerpo = JSONObject().put("query", query).put("variables", variables).toString()
            conexion.outputStream.use { it.write(cuerpo.toByteArray(Charsets.UTF_8)) }

            val codigo = conexion.responseCode
            val flujo = if (codigo in 200..299) conexion.inputStream else conexion.errorStream
            val texto = flujo?.bufferedReader()?.use { it.readText() } ?: ""
            if (codigo !in 200..299) throw IOException("HTTP $codigo")

            val json = JSONObject(texto)
            if (json.has("errors")) {
                val mensaje = json.getJSONArray("errors").getJSONObject(0).optString("message", "Error")
                throw IOException(mensaje)
            }
            return json.getJSONObject("data")
        } finally {
            conexion.disconnect()
        }
    }

    // Nombre del usuario al que pertenece el token.
    fun usuario(token: String): String =
        consulta(token, "query { Viewer { name } }", JSONObject())
            .getJSONObject("Viewer")
            .getString("name")

    fun buscar(texto: String): List<ResultadoAnilist> {
        val datos = consulta(
            null,
            "query(\$s: String) { Page(perPage: 10) { media(search: \$s, type: MANGA) " +
                "{ id title { romaji english } format } } }",
            JSONObject().put("s", texto),
        )
        val medios = datos.getJSONObject("Page").getJSONArray("media")
        return List(medios.length()) { i ->
            val m = medios.getJSONObject(i)
            val t = m.getJSONObject("title")
            val ingles = if (t.isNull("english")) null else t.getString("english")
            val romaji = if (t.isNull("romaji")) null else t.getString("romaji")
            ResultadoAnilist(
                id = m.getInt("id"),
                titulo = ingles ?: romaji ?: "Sin título",
                formato = if (m.isNull("format")) null else m.getString("format"),
            )
        }
    }

    /**
     * Recomendaciones de AniList para un título (las que usa la ficha, como las «Sugerencias» de Komikku).
     * No necesita sesión. Lista vacía si AniList no conoce el título.
     */
    fun recomendaciones(titulo: String): List<ResultadoAnilist> {
        val datos = consulta(
            null,
            "query(\$s: String) { Media(search: \$s, type: MANGA) { recommendations(perPage: 15, sort: RATING_DESC) " +
                "{ nodes { mediaRecommendation { id title { romaji english } format coverImage { medium } } } } } }",
            JSONObject().put("s", titulo),
        )
        val nodos = datos.optJSONObject("Media")?.optJSONObject("recommendations")?.optJSONArray("nodes") ?: return emptyList()
        return (0 until nodos.length()).mapNotNull { i ->
            val m = nodos.getJSONObject(i).optJSONObject("mediaRecommendation") ?: return@mapNotNull null
            val t = m.getJSONObject("title")
            val ingles = if (t.isNull("english")) null else t.getString("english")
            val romaji = if (t.isNull("romaji")) null else t.getString("romaji")
            ResultadoAnilist(
                id = m.getInt("id"),
                titulo = ingles ?: romaji ?: return@mapNotNull null,
                formato = if (m.isNull("format")) null else m.getString("format"),
                portada = m.optJSONObject("coverImage")?.optString("medium")?.ifBlank { null },
            )
        }
    }

    // Sube el progreso a AniList sin bajarlo nunca.
    // volumenes = true: cuenta volúmenes leídos; false: capítulos leídos.
    fun actualizarProgreso(token: String, mediaId: Int, volumenes: Boolean, numero: Int) {
        val datos = consulta(
            token,
            "query(\$id: Int) { Media(id: \$id) { mediaListEntry { progress progressVolumes status } } }",
            JSONObject().put("id", mediaId),
        )
        val entrada = datos.getJSONObject("Media").optJSONObject("mediaListEntry")
        val actual = entrada?.optInt(if (volumenes) "progressVolumes" else "progress", 0) ?: 0
        if (numero <= actual) return

        // Si no estaba en tu lista o era "Planeado", pasa a "Leyendo". El resto se respeta.
        val estadoActual =
            if (entrada == null || entrada.isNull("status")) null else entrada.getString("status")
        val estadoNuevo =
            if (estadoActual == null || estadoActual == "PLANNING") "CURRENT" else estadoActual

        val campo = if (volumenes) "progressVolumes" else "progress"
        consulta(
            token,
            "mutation(\$id: Int, \$n: Int, \$st: MediaListStatus) " +
                "{ SaveMediaListEntry(mediaId: \$id, $campo: \$n, status: \$st) { id } }",
            JSONObject().put("id", mediaId).put("n", numero).put("st", estadoNuevo),
        )
    }
}
