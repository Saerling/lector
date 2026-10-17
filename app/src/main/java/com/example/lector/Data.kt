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
    val leido: Long = 0L, // cuándo lo leíste por última vez (para ordenar)
)

// Una categoría para organizar la biblioteca. Si es "oculta", esconde sus cómics.
data class Categoria(val id: String, val nombre: String, val oculta: Boolean)

// Criterios para ordenar la biblioteca.
enum class Orden { TITULO, ULTIMO_LEIDO, PROGRESO, ANADIDO }

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

    // ---- Orden de la biblioteca ----
    var orden: Orden
        get() = prefs.getString("orden", null)
            ?.let { runCatching { Orden.valueOf(it) }.getOrNull() } ?: Orden.TITULO
        set(valor) = prefs.edit().putString("orden", valor.name).apply()

    var ordenAscendente: Boolean
        get() = prefs.getBoolean("orden_asc", true)
        set(valor) = prefs.edit().putBoolean("orden_asc", valor).apply()

    // ---- Categorías ----
    fun categorias(): List<Categoria> {
        val json = JSONArray(prefs.getString("categorias", "[]"))
        return List(json.length()) { i ->
            val o = json.getJSONObject(i)
            Categoria(o.getString("id"), o.getString("nombre"), o.optBoolean("oculta", false))
        }
    }

    private fun guardarCategorias(lista: List<Categoria>) {
        val json = JSONArray()
        lista.forEach {
            json.put(
                JSONObject()
                    .put("id", it.id)
                    .put("nombre", it.nombre)
                    .put("oculta", it.oculta),
            )
        }
        prefs.edit().putString("categorias", json.toString()).apply()
    }

    fun crearCategoria(nombre: String) {
        val limpio = nombre.trim()
        if (limpio.isEmpty()) return
        guardarCategorias(categorias() + Categoria(UUID.randomUUID().toString(), limpio, false))
    }

    fun renombrarCategoria(id: String, nombre: String) {
        val limpio = nombre.trim()
        if (limpio.isEmpty()) return
        guardarCategorias(categorias().map { if (it.id == id) it.copy(nombre = limpio) else it })
    }

    fun alternarOculta(id: String) {
        guardarCategorias(categorias().map { if (it.id == id) it.copy(oculta = !it.oculta) else it })
    }

    // Borra la categoría; los cómics no se tocan, solo pierden esa etiqueta.
    fun eliminarCategoria(id: String) {
        guardarCategorias(categorias().filter { it.id != id })
        guardarAsignaciones(asignaciones().mapValues { (_, ids) -> ids - id })
    }

    // Qué categorías tiene cada cómic: id del cómic -> ids de categorías.
    fun asignaciones(): Map<String, Set<String>> {
        val objeto = JSONObject(prefs.getString("asignaciones", "{}"))
        val mapa = HashMap<String, Set<String>>()
        for (clave in objeto.keys()) {
            val arreglo = objeto.getJSONArray(clave)
            mapa[clave] = List(arreglo.length()) { arreglo.getString(it) }.toSet()
        }
        return mapa
    }

    private fun guardarAsignaciones(mapa: Map<String, Set<String>>) {
        val objeto = JSONObject()
        mapa.forEach { (comicId, ids) ->
            if (ids.isNotEmpty()) objeto.put(comicId, JSONArray(ids.toList()))
        }
        prefs.edit().putString("asignaciones", objeto.toString()).apply()
    }

    fun asignarCategorias(comicId: String, ids: Set<String>) {
        guardarAsignaciones(asignaciones() + (comicId to ids))
    }

    fun cargar(): List<Comic> {
        val json = JSONArray(prefs.getString("comics", "[]"))
        return List(json.length()) { i ->
            val o = json.getJSONObject(i)
            Comic(
                o.getString("id"),
                o.getString("titulo"),
                o.getInt("paginas"),
                o.getInt("ultima"),
                o.optLong("leido", 0L),
            )
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
                    .put("ultima", it.ultimaPagina)
                    .put("leido", it.leido),
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
        guardar(cargar().map { if (it.id == id) it.copy(ultimaPagina = pagina, leido = System.currentTimeMillis()) else it })
    }

    fun eliminar(id: String) {
        archivoDe(id).delete()
        portadaDe(id).delete()
        prefs.edit().remove("modo_$id").apply()
        if (ultimoAbierto == id) ultimoAbierto = null
        guardarAsignaciones(asignaciones() - id)
        guardar(cargar().filter { it.id != id })
    }
}
