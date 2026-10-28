package com.example.lector

import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/**
 * Servicios de seguimiento además de AniList (como Komikku): MyAnimeList, Kitsu y MangaUpdates. Cada manga de la
 * biblioteca se puede vincular a una serie de cada servicio; al terminar un capítulo se sube el progreso a todos.
 */
object Seguidores {
    data class Resultado(val id: Long, val titulo: String, val portada: String?)
    data class Vinculo(val id: Long, val titulo: String, val capitulo: Int)

    abstract class Servicio(val clave: String, val nombre: String) {
        abstract fun conectado(ctx: Context): Boolean
        abstract suspend fun buscar(ctx: Context, q: String): List<Resultado>
        abstract suspend fun subir(ctx: Context, id: Long, capitulo: Int)
        fun salir(ctx: Context) = prefs(ctx).edit().remove("tk_$clave").remove("extra_$clave").apply()
        open suspend fun entrar(ctx: Context, usuario: String, clave2: String): Unit = error("Este servicio no usa contraseña")
        open fun autorizar(ctx: Context, idCliente: String) {}
        open suspend fun completar(ctx: Context, codigo: String) {}
    }

    private val http by lazy { OkHttpClient() }
    private val JSON_TIPO = "application/json".toMediaType()
    private val API_TIPO = "application/vnd.api+json".toMediaType()

    fun prefs(ctx: Context) = ctx.getSharedPreferences("seguidores", Context.MODE_PRIVATE)
    private fun token(ctx: Context, s: String) = prefs(ctx).getString("tk_$s", null)
    private fun extra(ctx: Context, s: String) = prefs(ctx).getString("extra_$s", null)

    private fun ejecutar(r: Request): String = http.newCall(r).execute().use { res ->
        val cuerpo = res.body.string()
        if (!res.isSuccessful) error("${res.code}: ${cuerpo.take(160)}")
        cuerpo
    }

    // ───────── MangaUpdates (usuario y contraseña) ─────────
    val mangaUpdates = object : Servicio("mu", "MangaUpdates") {
        private val api = "https://api.mangaupdates.com/v1"
        override fun conectado(ctx: Context) = token(ctx, clave) != null
        override suspend fun entrar(ctx: Context, usuario: String, clave2: String): Unit = withContext(Dispatchers.IO) {
            val cuerpo = JSONObject().put("username", usuario).put("password", clave2).toString().toRequestBody(JSON_TIPO)
            val o = JSONObject(ejecutar(Request.Builder().url("$api/account/login").put(cuerpo).build()))
            val tk = o.getJSONObject("context").getString("session_token")
            prefs(ctx).edit().putString("tk_$clave", tk).apply()
        }
        private fun auth(ctx: Context, b: Request.Builder) = b.header("Authorization", "Bearer ${token(ctx, clave)}")
        override suspend fun buscar(ctx: Context, q: String) = withContext(Dispatchers.IO) {
            val cuerpo = JSONObject().put("search", q).put("perpage", 15).toString().toRequestBody(JSON_TIPO)
            val a = JSONObject(ejecutar(auth(ctx, Request.Builder().url("$api/series/search").post(cuerpo)).build())).getJSONArray("results")
            (0 until a.length()).map { i ->
                val r = a.getJSONObject(i).getJSONObject("record")
                Resultado(r.getLong("series_id"), r.optString("title"), r.optJSONObject("image")?.optJSONObject("url")?.optString("original"))
            }
        }
        override suspend fun subir(ctx: Context, id: Long, capitulo: Int) = withContext(Dispatchers.IO) {
            val entrada = JSONArray().put(JSONObject().put("series", JSONObject().put("id", id)).put("list_id", 0)
                .put("status", JSONObject().put("chapter", capitulo)))
            val cuerpo = entrada.toString().toRequestBody(JSON_TIPO)
            // Si ya está en su lista se actualiza; si no, se añade a «Leyendo».
            runCatching { ejecutar(auth(ctx, Request.Builder().url("$api/lists/series/update").post(cuerpo)).build()) }
                .getOrElse { ejecutar(auth(ctx, Request.Builder().url("$api/lists/series").post(entrada.toString().toRequestBody(JSON_TIPO))).build()) }
            Unit
        }
    }

    // ───────── Kitsu (usuario y contraseña; credenciales públicas de su API) ─────────
    val kitsu = object : Servicio("kitsu", "Kitsu") {
        private val api = "https://kitsu.io/api/edge"
        private val idCliente = "dd031b32d2f56c990b1425efe6c42ad847e7fe3ab46bf1299f05ecd856bdb7dd"
        private val secreto = "54d7307928f63414defd96399fc31ba847961ceaecef3a5fd93144e960c0e151"
        override fun conectado(ctx: Context) = token(ctx, clave) != null
        override suspend fun entrar(ctx: Context, usuario: String, clave2: String): Unit = withContext(Dispatchers.IO) {
            val form = FormBody.Builder().add("grant_type", "password").add("username", usuario).add("password", clave2)
                .add("client_id", idCliente).add("client_secret", secreto).build()
            val tk = JSONObject(ejecutar(Request.Builder().url("https://kitsu.io/api/oauth/token").post(form).build())).getString("access_token")
            val yo = JSONObject(ejecutar(Request.Builder().url("$api/users?filter[self]=true").header("Authorization", "Bearer $tk").build()))
                .getJSONArray("data").getJSONObject(0).getString("id")
            prefs(ctx).edit().putString("tk_$clave", tk).putString("extra_$clave", yo).apply()
        }
        private fun auth(ctx: Context, b: Request.Builder) = b.header("Authorization", "Bearer ${token(ctx, clave)}").header("Accept", "application/vnd.api+json")
        override suspend fun buscar(ctx: Context, q: String) = withContext(Dispatchers.IO) {
            val a = JSONObject(ejecutar(auth(ctx, Request.Builder().url("$api/manga?page[limit]=15&filter[text]=" + Uri.encode(q))).build())).getJSONArray("data")
            (0 until a.length()).map { i ->
                val d = a.getJSONObject(i); val at = d.getJSONObject("attributes")
                Resultado(d.getString("id").toLong(), at.optString("canonicalTitle"), at.optJSONObject("posterImage")?.optString("small"))
            }
        }
        override suspend fun subir(ctx: Context, id: Long, capitulo: Int) = withContext(Dispatchers.IO) {
            val yo = extra(ctx, clave) ?: error("Vuelve a conectar Kitsu")
            val existentes = JSONObject(ejecutar(auth(ctx, Request.Builder().url("$api/library-entries?filter[userId]=$yo&filter[mangaId]=$id")).build())).getJSONArray("data")
            if (existentes.length() > 0) {
                val eid = existentes.getJSONObject(0).getString("id")
                val cuerpo = JSONObject().put("data", JSONObject().put("type", "libraryEntries").put("id", eid)
                    .put("attributes", JSONObject().put("progress", capitulo))).toString().toRequestBody(API_TIPO)
                ejecutar(auth(ctx, Request.Builder().url("$api/library-entries/$eid").patch(cuerpo)).build())
            } else {
                val cuerpo = JSONObject().put("data", JSONObject().put("type", "libraryEntries")
                    .put("attributes", JSONObject().put("status", "current").put("progress", capitulo))
                    .put("relationships", JSONObject()
                        .put("user", JSONObject().put("data", JSONObject().put("type", "users").put("id", yo)))
                        .put("media", JSONObject().put("data", JSONObject().put("type", "manga").put("id", id.toString())))))
                    .toString().toRequestBody(API_TIPO)
                ejecutar(auth(ctx, Request.Builder().url("$api/library-entries").post(cuerpo)).build())
            }
            Unit
        }
    }

    // ───────── MyAnimeList (OAuth con el Client ID propio del usuario; vuelve a lector://mal) ─────────
    val mal = object : Servicio("mal", "MyAnimeList") {
        private val api = "https://api.myanimelist.net/v2"
        override fun conectado(ctx: Context) = token(ctx, clave) != null
        /** Abre el navegador para autorizar. El Client ID se crea gratis en myanimelist.net/apiconfig (redirección: lector://mal). */
        override fun autorizar(ctx: Context, idCliente: String) {
            val verificador = (1..64).map { "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-._~".random() }.joinToString("")
            prefs(ctx).edit().putString("mal_cliente", idCliente.trim()).putString("mal_verif", verificador).apply()
            val url = "https://myanimelist.net/v1/oauth2/authorize?response_type=code&client_id=${idCliente.trim()}" +
                "&code_challenge=$verificador&code_challenge_method=plain&redirect_uri=" + Uri.encode("lector://mal")
            runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
        override suspend fun completar(ctx: Context, codigo: String): Unit = withContext(Dispatchers.IO) {
            val p = prefs(ctx)
            val form = FormBody.Builder().add("client_id", p.getString("mal_cliente", "")!!).add("code", codigo)
                .add("code_verifier", p.getString("mal_verif", "")!!).add("grant_type", "authorization_code")
                .add("redirect_uri", "lector://mal").build()
            val tk = JSONObject(ejecutar(Request.Builder().url("https://myanimelist.net/v1/oauth2/token").post(form).build())).getString("access_token")
            p.edit().putString("tk_$clave", tk).apply()
        }
        private fun auth(ctx: Context, b: Request.Builder) = b.header("Authorization", "Bearer ${token(ctx, clave)}")
        override suspend fun buscar(ctx: Context, q: String) = withContext(Dispatchers.IO) {
            val a = JSONObject(ejecutar(auth(ctx, Request.Builder().url("$api/manga?limit=15&q=" + Uri.encode(q.take(60)))).build())).getJSONArray("data")
            (0 until a.length()).map { i ->
                val n = a.getJSONObject(i).getJSONObject("node")
                Resultado(n.getLong("id"), n.optString("title"), n.optJSONObject("main_picture")?.optString("medium"))
            }
        }
        override suspend fun subir(ctx: Context, id: Long, capitulo: Int) = withContext(Dispatchers.IO) {
            val form = FormBody.Builder().add("num_chapters_read", "$capitulo").add("status", "reading").build()
            ejecutar(auth(ctx, Request.Builder().url("$api/manga/$id/my_list_status").patch(form)).build())
            Unit
        }
    }

    val todos: List<Servicio> get() = listOf(mal, kitsu, mangaUpdates)

    // ───────── Vínculos por manga ─────────
    fun vinculo(ctx: Context, s: Servicio, comicId: String): Vinculo? = prefs(ctx).getString("v_${s.clave}_$comicId", null)?.let {
        runCatching { JSONObject(it).let { o -> Vinculo(o.getLong("id"), o.getString("t"), o.optInt("c")) } }.getOrNull()
    }

    fun vincular(ctx: Context, s: Servicio, comicId: String, v: Vinculo?) {
        val e = prefs(ctx).edit()
        if (v == null) e.remove("v_${s.clave}_$comicId")
        else e.putString("v_${s.clave}_$comicId", JSONObject().put("id", v.id).put("t", v.titulo).put("c", v.capitulo).toString())
        e.apply()
    }

    /** Al terminar un capítulo: sube el progreso a cada servicio vinculado (sin bajarlo nunca). */
    suspend fun avanzar(ctx: Context, comicId: String, capitulo: Int) {
        for (s in todos) {
            val v = vinculo(ctx, s, comicId) ?: continue
            if (!s.conectado(ctx) || capitulo <= v.capitulo) continue
            runCatching { s.subir(ctx, v.id, capitulo) }.onSuccess { vincular(ctx, s, comicId, v.copy(capitulo = capitulo)) }
        }
    }
}
