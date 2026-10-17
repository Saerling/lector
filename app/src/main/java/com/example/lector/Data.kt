package com.example.lector

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.OpenableColumns
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class Comic(
    val id: String,
    val titulo: String,
    val paginas: Int,
    val ultimaPagina: Int,
)

// Cómo se lee un cómic: paginado de derecha a izquierda, de izquierda a derecha, o en tira vertical.
enum class Modo { MANGA, COMIC, VERTICAL }

// Guarda la biblioteca: copia cada .cbz a la memoria de la app y recuerda el progreso.
class Almacen(private val contexto: Context) {
    private val prefs = contexto.getSharedPreferences("lector", Context.MODE_PRIVATE)
    private val carpetaComics = File(contexto.filesDir, "comics").apply { mkdirs() }
    private val carpetaPortadas = File(contexto.filesDir, "portadas").apply { mkdirs() }

    fun archivoDe(id: String) = File(carpetaComics, "$id.cbz")
    fun portadaDe(id: String) = File(carpetaPortadas, "$id.jpg")

    var derechaAIzquierda: Boolean
        get() = prefs.getBoolean("rtl", true)
        set(valor) = prefs.edit().putBoolean("rtl", valor).apply()

    // Modo por defecto para cómics nuevos: el último modo paginado que elegiste.
    fun modoPorDefecto(): Modo = if (derechaAIzquierda) Modo.MANGA else Modo.COMIC

    // Modo elegido a mano para un cómic concreto (null si nunca lo cambiaste).
    fun modoDe(id: String): Modo? =
        prefs.getString("modo_$id", null)?.let { runCatching { Modo.valueOf(it) }.getOrNull() }

    fun guardarModo(id: String, modo: Modo) {
        prefs.edit().putString("modo_$id", modo.name).apply()
    }

    // Último cómic que abriste, para el botón "Continuar".
    var ultimoAbierto: String?
        get() = prefs.getString("ultimo", null)
        set(valor) = prefs.edit().putString("ultimo", valor).apply()

    fun cargar(): List<Comic> {
        val json = JSONArray(prefs.getString("comics", "[]"))
        return List(json.length()) { i ->
            val o = json.getJSONObject(i)
            Comic(o.getString("id"), o.getString("titulo"), o.getInt("paginas"), o.getInt("ultima"))
        }
    }

    private fun guardar(lista: List<Comic>) {
        val json = JSONArray()
        lista.forEach {
            json.put(
                JSONObject()
                    .put("id", it.id)
                    .put("titulo", it.titulo)
                    .put("paginas", it.paginas)
                    .put("ultima", it.ultimaPagina),
            )
        }
        prefs.edit().putString("comics", json.toString()).apply()
    }

    // Devuelve el cómic añadido, o null si el archivo no sirve.
    fun importar(uri: Uri): Comic? {
        val nombre = contexto.contentResolver.query(uri, null, null, null, null)?.use { c ->
            val col = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (col >= 0 && c.moveToFirst()) c.getString(col) else null
        } ?: "Sin título"

        val id = UUID.randomUUID().toString()
        val archivo = archivoDe(id)

        val copiado = contexto.contentResolver.openInputStream(uri)?.use { entrada ->
            archivo.outputStream().use { salida -> entrada.copyTo(salida) }
            true
        } ?: false
        if (!copiado) return null

        val paginas = try {
            Cbz.paginas(archivo)
        } catch (e: Exception) {
            emptyList()
        }
        if (paginas.isEmpty()) {
            archivo.delete()
            return null
        }

        Cbz.decodificar(archivo, paginas.first(), 400)?.let { portada ->
            portadaDe(id).outputStream().use { portada.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        }

        val comic = Comic(id, nombre.substringBeforeLast('.'), paginas.size, 0)
        guardar(cargar() + comic)
        return comic
    }

    fun guardarProgreso(id: String, pagina: Int) {
        ultimoAbierto = id
        guardar(cargar().map { if (it.id == id) it.copy(ultimaPagina = pagina) else it })
    }

    fun eliminar(id: String) {
        archivoDe(id).delete()
        portadaDe(id).delete()
        prefs.edit().remove("modo_$id").apply()
        if (ultimoAbierto == id) ultimoAbierto = null
        guardar(cargar().filter { it.id != id })
    }
}
