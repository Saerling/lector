package com.example.lector

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class Comic(
    val id: String,
    val titulo: String,
    val paginas: Int,
    val ultimaPagina: Int,
    val leido: Long = 0L, // cuándo lo leíste por última vez (para ordenar)
)

// Una categoría para organizar la biblioteca. Si es "oculta", esconde sus cómics.
data class Categoria(val id: String, val nombre: String, val oculta: Boolean)

// A qué serie de AniList está vinculado un cómic y qué número es (volumen o capítulo).
data class Seguimiento(
    val mediaId: Int,
    val titulo: String,
    val volumenes: Boolean,
    val numero: Int,
)

// Resultado de restaurar una copia de seguridad.
data class ResultadoRestauracion(val actualizados: Int, val pendientes: Int)

// Criterios para ordenar la biblioteca.
enum class Orden { TITULO, ULTIMO_LEIDO, PROGRESO, ANADIDO }

// Cómo se lee un cómic: paginado de derecha a izquierda, de izquierda a derecha, o en tira vertical.
enum class Modo { MANGA, COMIC, VERTICAL }

// Guarda la biblioteca: copia cada .cbz a la memoria de la app y recuerda el progreso.
class Almacen(private val contexto: Context) {
    private val prefs = contexto.getSharedPreferences("lector", Context.MODE_PRIVATE)
    private val carpetaComics = File(contexto.filesDir, "comics").apply { mkdirs() }
    private val carpetaPortadas = File(contexto.filesDir, "portadas").apply { mkdirs() }

    // ---- Apariencia (son "estado" de Compose: al cambiarlos, la app se repinta sola) ----
    var paleta by mutableStateOf(
        prefs.getString("paleta", Paletas.porDefecto.nombre) ?: Paletas.porDefecto.nombre,
    )
        private set

    fun elegirPaleta(nombre: String) {
        paleta = nombre
        prefs.edit().putString("paleta", nombre).apply()
    }

    var colorPortada by mutableStateOf(prefs.getBoolean("color_portada", true))
        private set

    fun elegirColorPortada(valor: Boolean) {
        colorPortada = valor
        prefs.edit().putBoolean("color_portada", valor).apply()
    }

    // ---- Ajustes del lector ----
    var zonasToque by mutableStateOf(prefs.getBoolean("zonas_toque", true))
        private set

    fun elegirZonasToque(valor: Boolean) {
        zonasToque = valor
        prefs.edit().putBoolean("zonas_toque", valor).apply()
    }

    var teclasVolumen by mutableStateOf(prefs.getBoolean("teclas_volumen", false))
        private set

    fun elegirTeclasVolumen(valor: Boolean) {
        teclasVolumen = valor
        prefs.edit().putBoolean("teclas_volumen", valor).apply()
    }

    var mantenerEncendida by mutableStateOf(prefs.getBoolean("mantener_encendida", true))
        private set

    fun elegirMantenerEncendida(valor: Boolean) {
        mantenerEncendida = valor
        prefs.edit().putBoolean("mantener_encendida", valor).apply()
    }

    // ---- Pantalla del lector ----
    private inline fun <reified T : Enum<T>> leerEnum(clave: String, defecto: T): T =
        prefs.getString(clave, null)?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: defecto

    var fondoLector by mutableStateOf(leerEnum("fondo_lector", Fondo.NEGRO))
        private set

    fun elegirFondoLector(valor: Fondo) {
        fondoLector = valor
        prefs.edit().putString("fondo_lector", valor.name).apply()
    }

    var orientacion by mutableStateOf(leerEnum("orientacion", Orientacion.LIBRE))
        private set

    fun elegirOrientacion(valor: Orientacion) {
        orientacion = valor
        prefs.edit().putString("orientacion", valor.name).apply()
    }

    var brilloPersonalizado by mutableStateOf(prefs.getBoolean("brillo_on", false))
        private set

    fun elegirBrilloPersonalizado(valor: Boolean) {
        brilloPersonalizado = valor
        prefs.edit().putBoolean("brillo_on", valor).apply()
    }

    var brillo by mutableStateOf(prefs.getFloat("brillo", 0.5f))
        private set

    fun elegirBrillo(valor: Float) {
        brillo = valor
        prefs.edit().putFloat("brillo", valor).apply()
    }

    var escalaGrises by mutableStateOf(prefs.getBoolean("grises", false))
        private set

    fun elegirEscalaGrises(valor: Boolean) {
        escalaGrises = valor
        prefs.edit().putBoolean("grises", valor).apply()
    }

    var invertirColores by mutableStateOf(prefs.getBoolean("invertir", false))
        private set

    fun elegirInvertirColores(valor: Boolean) {
        invertirColores = valor
        prefs.edit().putBoolean("invertir", valor).apply()
    }

    var tinte by mutableStateOf(leerEnum("tinte", Tinte.NINGUNO))
        private set

    fun elegirTinte(valor: Tinte) {
        tinte = valor
        prefs.edit().putString("tinte", valor.name).apply()
    }

    var intensidadTinte by mutableStateOf(prefs.getFloat("tinte_intensidad", 0.25f))
        private set

    fun elegirIntensidadTinte(valor: Float) {
        intensidadTinte = valor
        prefs.edit().putFloat("tinte_intensidad", valor).apply()
    }

    var recortarBordes by mutableStateOf(prefs.getBoolean("recortar_bordes", false))
        private set

    fun elegirRecortarBordes(valor: Boolean) {
        recortarBordes = valor
        prefs.edit().putBoolean("recortar_bordes", valor).apply()
    }

    // ---- Vista de la biblioteca ----
    var vista by mutableStateOf(
        prefs.getString("vista", null)?.let { runCatching { Vista.valueOf(it) }.getOrNull() }
            ?: Vista.CUADRICULA_COMODA,
    )
        private set

    fun elegirVista(valor: Vista) {
        vista = valor
        prefs.edit().putString("vista", valor.name).apply()
    }

    // 0 = automático (según el ancho de la pantalla).
    var columnas by mutableStateOf(prefs.getInt("columnas", 0))
        private set

    fun elegirColumnas(valor: Int) {
        columnas = valor
        prefs.edit().putInt("columnas", valor).apply()
    }

    var mostrarPagina by mutableStateOf(prefs.getBoolean("mostrar_pagina", true))
        private set

    fun elegirMostrarPagina(valor: Boolean) {
        mostrarPagina = valor
        prefs.edit().putBoolean("mostrar_pagina", valor).apply()
    }

    var insigniaProgreso by mutableStateOf(prefs.getBoolean("insignia_progreso", true))
        private set

    fun elegirInsigniaProgreso(valor: Boolean) {
        insigniaProgreso = valor
        prefs.edit().putBoolean("insignia_progreso", valor).apply()
    }

    // ---- Seguimiento con AniList ----
    var anilistClientId by mutableStateOf(prefs.getString("anilist_client", "") ?: "")
        private set

    fun guardarClientId(valor: String) {
        anilistClientId = valor
        prefs.edit().putString("anilist_client", valor).apply()
    }

    var anilistToken by mutableStateOf<String?>(prefs.getString("anilist_token", null))
        private set

    var anilistUsuario by mutableStateOf<String?>(prefs.getString("anilist_usuario", null))
        private set

    fun guardarTokenAnilist(token: String) {
        anilistToken = token
        anilistUsuario = null
        prefs.edit().putString("anilist_token", token).remove("anilist_usuario").apply()
    }

    fun guardarUsuarioAnilist(nombre: String) {
        anilistUsuario = nombre
        prefs.edit().putString("anilist_usuario", nombre).apply()
    }

    fun cerrarSesionAnilist() {
        anilistToken = null
        anilistUsuario = null
        prefs.edit().remove("anilist_token").remove("anilist_usuario").apply()
    }

    fun seguimientoDe(id: String): Seguimiento? {
        val texto = prefs.getString("seg_$id", null) ?: return null
        return runCatching {
            val o = JSONObject(texto)
            Seguimiento(o.getInt("media"), o.getString("titulo"), o.getBoolean("vol"), o.getInt("numero"))
        }.getOrNull()
    }

    fun guardarSeguimiento(id: String, seguimiento: Seguimiento?) {
        val editor = prefs.edit()
        if (seguimiento == null) {
            editor.remove("seg_$id")
            marcarSincronizacion(id, false)
        } else {
            editor.putString(
                "seg_$id",
                JSONObject()
                    .put("media", seguimiento.mediaId)
                    .put("titulo", seguimiento.titulo)
                    .put("vol", seguimiento.volumenes)
                    .put("numero", seguimiento.numero)
                    .toString(),
            )
        }
        editor.apply()
    }

    // Cómics terminados cuyo progreso aún no se ha podido subir (por ejemplo, sin conexión).
    fun sincronizacionesPendientes(): List<String> =
        (prefs.getString("sync_pend", "") ?: "").split(",").filter { it.isNotEmpty() }

    @Synchronized
    fun marcarSincronizacion(id: String, pendiente: Boolean) {
        val actuales = sincronizacionesPendientes().toMutableList()
        actuales.remove(id)
        if (pendiente) actuales.add(id)
        prefs.edit().putString("sync_pend", actuales.joinToString(",")).apply()
    }

    // Sube el progreso de un cómic a AniList. Bloquea: llamar desde un hilo de fondo.
    // Devuelve true si quedó subido (o no hacía falta).
    fun sincronizarAnilist(id: String): Boolean {
        val seguimiento = seguimientoDe(id)
        if (seguimiento == null) {
            marcarSincronizacion(id, false)
            return true
        }
        val token = anilistToken ?: return false
        return try {
            Anilist.actualizarProgreso(token, seguimiento.mediaId, seguimiento.volumenes, seguimiento.numero)
            marcarSincronizacion(id, false)
            true
        } catch (e: Exception) {
            marcarSincronizacion(id, true)
            false
        }
    }

    fun sincronizarPendientes() {
        for (id in sincronizacionesPendientes()) sincronizarAnilist(id)
    }

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

    // Cambia las categorías de varios cómics a la vez: añade unas y quita otras.
    fun modificarCategorias(comicIds: Set<String>, agregar: Set<String>, quitar: Set<String>) {
        val mapa = asignaciones().toMutableMap()
        comicIds.forEach { id -> mapa[id] = (mapa[id].orEmpty() + agregar) - quitar }
        guardarAsignaciones(mapa)
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

        return registrar(id, archivo, nombre.substringBeforeLast('.'))
    }

    // Convierte una carpeta de imágenes (elegida con el selector de carpetas) en un cómic.
    // Por dentro la empaqueta como .cbz, así el resto de la app funciona igual.
    fun importarCarpeta(arbol: Uri): Comic? {
        val raizId = DocumentsContract.getTreeDocumentId(arbol)
        val titulo = nombreDe(DocumentsContract.buildDocumentUriUsingTree(arbol, raizId))
            ?: "Sin título"

        val imagenes = ArrayList<Pair<String, Uri>>()
        listarImagenes(arbol, raizId, "", imagenes)
        if (imagenes.isEmpty()) return null

        val id = UUID.randomUUID().toString()
        val archivo = archivoDe(id)
        try {
            ZipOutputStream(archivo.outputStream().buffered()).use { zip ->
                zip.setLevel(Deflater.NO_COMPRESSION) // las imágenes ya vienen comprimidas
                for ((ruta, uri) in imagenes) {
                    zip.putNextEntry(ZipEntry(ruta))
                    contexto.contentResolver.openInputStream(uri)?.use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
        } catch (e: Exception) {
            archivo.delete()
            return null
        }
        return registrar(id, archivo, titulo)
    }

    private fun nombreDe(uri: Uri): String? =
        contexto.contentResolver
            .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }

    // Busca imágenes dentro de la carpeta y sus subcarpetas (por ejemplo, una por capítulo).
    private fun listarImagenes(
        arbol: Uri,
        carpetaId: String,
        prefijo: String,
        destino: MutableList<Pair<String, Uri>>,
    ) {
        val hijos = DocumentsContract.buildChildDocumentsUriUsingTree(arbol, carpetaId)
        val columnas = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
        )
        contexto.contentResolver.query(hijos, columnas, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                val docId = c.getString(0)
                val nombre = c.getString(1) ?: continue
                val mime = c.getString(2)
                if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                    listarImagenes(arbol, docId, "$prefijo$nombre/", destino)
                } else if (Cbz.esImagen(nombre)) {
                    destino.add(
                        "$prefijo$nombre" to
                            DocumentsContract.buildDocumentUriUsingTree(arbol, docId),
                    )
                }
            }
        }
    }

    // Comprueba el archivo, le saca la portada y lo añade a la biblioteca.
    private fun registrar(id: String, archivo: File, titulo: String): Comic? {
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

        val comic = Comic(id, titulo, paginas.size, 0)
        guardar(cargar() + comic)
        // Si venía de una copia de seguridad, recupera su progreso y categorías.
        return aplicarPendiente(comic)
    }

    // ---- Copia de seguridad ----
    // Guarda progreso, categorías, modos de lectura y ajustes (no los archivos de los cómics).
    // Los cómics se reconocen por título y número de páginas.
    private fun claveDe(titulo: String, paginas: Int) = titulo.trim().lowercase() + "|" + paginas

    private fun pendientes(): JSONObject = JSONObject(prefs.getString("pendientes", "{}"))

    fun crearCopia(): String {
        val listaCategorias = categorias()
        val nombres = listaCategorias.associate { it.id to it.nombre }
        val asign = asignaciones()

        val raiz = JSONObject()
        raiz.put("version", 1)
        raiz.put("app", "Lector")
        raiz.put(
            "ajustes",
            JSONObject()
                .put("orden", orden.name)
                .put("orden_asc", ordenAscendente)
                .put("rtl", derechaAIzquierda)
                .put("paleta", paleta)
                .put("color_portada", colorPortada)
                .put("zonas_toque", zonasToque)
                .put("teclas_volumen", teclasVolumen)
                .put("mantener_encendida", mantenerEncendida)
                .put("vista", vista.name)
                .put("columnas", columnas)
                .put("mostrar_pagina", mostrarPagina)
                .put("insignia_progreso", insigniaProgreso)
                .put("fondo_lector", fondoLector.name)
                .put("orientacion", orientacion.name)
                .put("brillo_on", brilloPersonalizado)
                .put("brillo", brillo.toDouble())
                .put("grises", escalaGrises)
                .put("invertir", invertirColores)
                .put("tinte", tinte.name)
                .put("tinte_intensidad", intensidadTinte.toDouble())
                .put("recortar_bordes", recortarBordes),
        )

        val cats = JSONArray()
        listaCategorias.forEach {
            cats.put(JSONObject().put("nombre", it.nombre).put("oculta", it.oculta))
        }
        raiz.put("categorias", cats)

        val lista = JSONArray()
        cargar().forEach { c ->
            val e = JSONObject()
                .put("titulo", c.titulo)
                .put("paginas", c.paginas)
                .put("ultima", c.ultimaPagina)
                .put("leido", c.leido)
            modoDe(c.id)?.let { e.put("modo", it.name) }
            seguimientoDe(c.id)?.let {
                e.put(
                    "seguimiento",
                    JSONObject()
                        .put("media", it.mediaId)
                        .put("titulo", it.titulo)
                        .put("vol", it.volumenes)
                        .put("numero", it.numero),
                )
            }
            val nombresDelComic = JSONArray()
            asign[c.id].orEmpty().mapNotNull { nombres[it] }.forEach { nombresDelComic.put(it) }
            e.put("categorias", nombresDelComic)
            lista.put(e)
        }
        // También los que aún esperan a que añadas su archivo.
        val pend = pendientes()
        for (clave in pend.keys()) lista.put(pend.getJSONObject(clave))
        raiz.put("comics", lista)

        return raiz.toString(2)
    }

    // Busca una categoría por nombre o la crea.
    private fun idCategoria(nombre: String, oculta: Boolean, cats: MutableList<Categoria>): String {
        val existente = cats.firstOrNull { it.nombre.equals(nombre, ignoreCase = true) }
        if (existente != null) return existente.id
        val nueva = Categoria(UUID.randomUUID().toString(), nombre, oculta)
        cats.add(nueva)
        return nueva.id
    }

    // Aplica los datos de una copia a un cómic que ya está en la biblioteca.
    private fun aplicarEntrada(
        local: Comic,
        e: JSONObject,
        cats: MutableList<Categoria>,
        mapa: MutableMap<String, Set<String>>,
    ): Comic {
        // El progreso solo se pisa si la copia es más reciente que lo que ya tienes.
        val leidoCopia = e.optLong("leido", 0L)
        val resultado = if (leidoCopia > local.leido) {
            local.copy(
                ultimaPagina = e.optInt("ultima", 0).coerceIn(0, maxOf(0, local.paginas - 1)),
                leido = leidoCopia,
            )
        } else {
            local
        }

        val modo = e.optString("modo")
        if (modo.isNotEmpty() && modoDe(local.id) == null) {
            runCatching { Modo.valueOf(modo) }.getOrNull()?.let { guardarModo(local.id, it) }
        }

        val seg = e.optJSONObject("seguimiento")
        if (seg != null && seguimientoDe(local.id) == null) {
            runCatching {
                Seguimiento(seg.getInt("media"), seg.getString("titulo"), seg.getBoolean("vol"), seg.getInt("numero"))
            }.getOrNull()?.let { guardarSeguimiento(local.id, it) }
        }

        val arreglo = e.optJSONArray("categorias")
        if (arreglo != null && arreglo.length() > 0) {
            val ids = HashSet<String>()
            for (i in 0 until arreglo.length()) {
                ids.add(idCategoria(arreglo.getString(i), false, cats))
            }
            mapa[local.id] = mapa[local.id].orEmpty() + ids
        }
        return resultado
    }

    // Cuando añades un cómic que estaba en una copia, recupera sus datos.
    private fun aplicarPendiente(comic: Comic): Comic {
        val pend = pendientes()
        val clave = claveDe(comic.titulo, comic.paginas)
        val e = pend.optJSONObject(clave) ?: return comic

        val cats = categorias().toMutableList()
        val mapa = asignaciones().toMutableMap()
        val nuevo = aplicarEntrada(comic, e, cats, mapa)
        pend.remove(clave)

        guardar(cargar().map { if (it.id == comic.id) nuevo else it })
        guardarAsignaciones(mapa)
        guardarCategorias(cats)
        prefs.edit().putString("pendientes", pend.toString()).apply()
        return nuevo
    }

    // Lanza una excepción si el texto no es una copia válida.
    fun restaurarCopia(texto: String): ResultadoRestauracion {
        val raiz = JSONObject(texto)
        require(raiz.optInt("version", 0) == 1) { "Copia no válida" }

        raiz.optJSONObject("ajustes")?.let { a ->
            val nombreOrden = a.optString("orden")
            if (nombreOrden.isNotEmpty()) {
                runCatching { Orden.valueOf(nombreOrden) }.getOrNull()?.let { orden = it }
            }
            if (a.has("orden_asc")) ordenAscendente = a.getBoolean("orden_asc")
            if (a.has("rtl")) derechaAIzquierda = a.getBoolean("rtl")
            val nombrePaleta = a.optString("paleta")
            if (nombrePaleta.isNotEmpty()) elegirPaleta(nombrePaleta)
            if (a.has("color_portada")) elegirColorPortada(a.getBoolean("color_portada"))
            if (a.has("zonas_toque")) elegirZonasToque(a.getBoolean("zonas_toque"))
            if (a.has("teclas_volumen")) elegirTeclasVolumen(a.getBoolean("teclas_volumen"))
            if (a.has("mantener_encendida")) elegirMantenerEncendida(a.getBoolean("mantener_encendida"))
            val nombreVista = a.optString("vista")
            if (nombreVista.isNotEmpty()) {
                runCatching { Vista.valueOf(nombreVista) }.getOrNull()?.let { elegirVista(it) }
            }
            if (a.has("columnas")) elegirColumnas(a.getInt("columnas"))
            if (a.has("mostrar_pagina")) elegirMostrarPagina(a.getBoolean("mostrar_pagina"))
            if (a.has("insignia_progreso")) elegirInsigniaProgreso(a.getBoolean("insignia_progreso"))
            runCatching { Fondo.valueOf(a.optString("fondo_lector")) }.getOrNull()
                ?.let { elegirFondoLector(it) }
            runCatching { Orientacion.valueOf(a.optString("orientacion")) }.getOrNull()
                ?.let { elegirOrientacion(it) }
            if (a.has("brillo_on")) elegirBrilloPersonalizado(a.getBoolean("brillo_on"))
            if (a.has("brillo")) elegirBrillo(a.getDouble("brillo").toFloat())
            if (a.has("grises")) elegirEscalaGrises(a.getBoolean("grises"))
            if (a.has("invertir")) elegirInvertirColores(a.getBoolean("invertir"))
            runCatching { Tinte.valueOf(a.optString("tinte")) }.getOrNull()
                ?.let { elegirTinte(it) }
            if (a.has("tinte_intensidad")) {
                elegirIntensidadTinte(a.getDouble("tinte_intensidad").toFloat())
            }
            if (a.has("recortar_bordes")) elegirRecortarBordes(a.getBoolean("recortar_bordes"))
        }

        val cats = categorias().toMutableList()
        raiz.optJSONArray("categorias")?.let { arreglo ->
            for (i in 0 until arreglo.length()) {
                val o = arreglo.getJSONObject(i)
                idCategoria(o.getString("nombre"), o.optBoolean("oculta", false), cats)
            }
        }

        val locales = cargar().toMutableList()
        val mapa = asignaciones().toMutableMap()
        val pend = pendientes()
        var actualizados = 0
        var sinArchivo = 0

        val entradas = raiz.optJSONArray("comics") ?: JSONArray()
        for (i in 0 until entradas.length()) {
            val e = entradas.getJSONObject(i)
            val clave = claveDe(e.getString("titulo"), e.getInt("paginas"))
            val indice = locales.indexOfFirst { claveDe(it.titulo, it.paginas) == clave }
            if (indice >= 0) {
                locales[indice] = aplicarEntrada(locales[indice], e, cats, mapa)
                actualizados++
            } else {
                pend.put(clave, e)
                sinArchivo++
            }
        }

        guardar(locales)
        guardarAsignaciones(mapa)
        guardarCategorias(cats)
        prefs.edit().putString("pendientes", pend.toString()).apply()
        return ResultadoRestauracion(actualizados, sinArchivo)
    }

    // Quita los cómics que están en alguna categoría oculta (para Inicio, Buscar y Perfil).
    fun sinOcultos(comics: List<Comic>): List<Comic> {
        val ocultas = categorias().filter { it.oculta }.map { it.id }.toSet()
        if (ocultas.isEmpty()) return comics
        val asign = asignaciones()
        return comics.filter { c -> asign[c.id].orEmpty().none { it in ocultas } }
    }

    // ---- Historial ----
    // Quitar un cómic del historial borra solo la fecha de lectura; la página se conserva.
    fun quitarDelHistorial(id: String) {
        guardar(cargar().map { if (it.id == id) it.copy(leido = 0L) else it })
    }

    fun limpiarHistorial() {
        guardar(cargar().map { it.copy(leido = 0L) })
    }

    fun guardarProgreso(id: String, pagina: Int) {
        ultimoAbierto = id
        guardar(cargar().map { if (it.id == id) it.copy(ultimaPagina = pagina, leido = System.currentTimeMillis()) else it })
    }

    fun eliminar(id: String) {
        archivoDe(id).delete()
        portadaDe(id).delete()
        prefs.edit().remove("modo_$id").remove("seg_$id").apply()
        marcarSincronizacion(id, false)
        if (ultimoAbierto == id) ultimoAbierto = null
        guardarAsignaciones(asignaciones() - id)
        guardar(cargar().filter { it.id != id })
    }
}
