package com.example.lector

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.lector.ext.CapituloFuente
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
    // Cómic local: nº de páginas. Manga en línea: nº de capítulos que tiene la fuente.
    val paginas: Int,
    // Cómic local: última página vista. Manga en línea: nº de capítulos leídos.
    val ultimaPagina: Int,
    val leido: Long = 0L, // cuándo lo leíste por última vez (para ordenar)
    // Solo para mangas añadidos desde una fuente (Explorar): no tienen archivo .cbz.
    val fuenteId: Long = 0L,
    val urlManga: String? = null,
    val portadaUrl: String? = null,
    // Datos extra (como la base de datos de Komikku). Se rellenan al abrir la ficha o al buscar actualizaciones.
    val anadido: Long = 0L,
    val autor: String? = null,
    val estado: Int = 0, // mismo código que SManga.status (1 en emisión, 2 completado…)
    val generos: List<String> = emptyList(),
    val descripcion: String? = null,
    val ultimaRevision: Long = 0L, // cuándo se buscaron capítulos nuevos por última vez
    val ultimoCapitulo: Long = 0L, // fecha del capítulo más reciente que ofrece la fuente
    // El usuario editó la información (título, autor…): las fuentes ya no la pisan.
    val editado: Boolean = false,
    // La portada guardada la eligió el usuario (no se sustituye por la de la fuente).
    val portadaPropia: Boolean = false,
) {
    val enLinea: Boolean get() = urlManga != null

    /** Capítulos sin leer (en línea) o páginas que faltan (local). */
    val sinLeer: Int get() = if (enLinea) (paginas - ultimaPagina).coerceAtLeast(0) else (paginas - 1 - ultimaPagina).coerceAtLeast(0)

    // Texto corto de progreso para tarjetas, listas e historial.
    fun etiquetaProgreso(): String =
        if (enLinea) {
            if (paginas > 0) "Leídos $ultimaPagina de $paginas caps." else "En línea"
        } else {
            "Pág. ${ultimaPagina + 1} de $paginas"
        }
}

// Un capítulo nuevo detectado al revisar la biblioteca (pestaña Actualizaciones).
data class ActualizacionCap(
    val fuenteId: Long,
    val urlManga: String,
    val titulo: String,
    val portadaUrl: String?,
    val capUrl: String,
    val capNombre: String,
    val capNumero: Float,
    val capFecha: Long,
    val scanlator: String?,
    val detectado: Long,
)

// Una entrada del historial de lectura online: el último capítulo que abriste de cada manga.
data class EntradaHistorial(
    val fuenteId: Long,
    val urlManga: String,
    val titulo: String,
    val portadaUrl: String?,
    val capUrl: String,
    val capNombre: String,
    val pagina: Int,
    val total: Int,
    val leido: Long,
)

/** Un manga que no se pudo revisar en la última actualización de la biblioteca, y por qué. */
data class ErrorActualizacion(val titulo: String, val fuente: String, val motivo: String)

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

// Criterios para ordenar la biblioteca (los cuatro primeros existían antes; se conservan sus nombres).
enum class Orden { TITULO, ULTIMO_LEIDO, PROGRESO, ANADIDO, SIN_LEER, TOTAL_CAPITULOS, ULTIMO_CAPITULO, ULTIMA_REVISION, ALEATORIO }

// Cómo se agrupa la biblioteca en pestañas (las "categorías dinámicas" de TachiyomiSY/Komikku).
enum class Agrupar { CATEGORIA, FUENTE, ESTADO, NINGUNO }

// Cómo se lee un cómic local (modo antiguo; el visor nuevo usa ModoVisor).
enum class Modo { MANGA, COMIC, VERTICAL }

/** Modos del visor (como Komikku): paginado ← / →, paginado vertical, webtoon y vertical continuo con separación. */
enum class ModoVisor { DERECHA_IZQUIERDA, IZQUIERDA_DERECHA, VERTICAL, WEBTOON, CONTINUO }

/** Zonas de toque del visor (las de Mihon/Komikku). */
enum class ZonasToque { AUTOMATICO, L, KINDLE, BORDES, IZQUIERDA_DERECHA, DESACTIVADO }

enum class InvertirZonas { NO, HORIZONTAL, VERTICAL, AMBOS }

/** Escala de las páginas en los modos paginados. */
enum class EscalaPagina { AJUSTAR, AJUSTAR_ANCHO, AJUSTAR_ALTO, ORIGINAL }

/** Tema de la app. */
enum class ModoTema { SISTEMA, OSCURO_PURO, OSCURO, CLARO }

/** Vista de las listas de una fuente (Explorar). */
enum class VistaExplorar { CUADRICULA, LISTA, COMPACTA, PORTADAS }

// Guarda la biblioteca: copia cada .cbz a la memoria de la app y recuerda el progreso.
// UNA sola instancia por proceso (Almacen.obtener): la usan la interfaz y los trabajos en segundo plano.
class Almacen private constructor(private val contexto: Context) {

    companion object {
        @Volatile private var instancia: Almacen? = null
        fun obtener(c: Context): Almacen = instancia ?: synchronized(this) {
            instancia ?: Almacen(c.applicationContext).also { instancia = it }
        }
    }

    private val prefs = contexto.getSharedPreferences("lector", Context.MODE_PRIVATE)
    private val carpetaComics = File(contexto.filesDir, "comics").apply { mkdirs() }
    private val carpetaPortadas = File(contexto.filesDir, "portadas").apply { mkdirs() }
    private val carpetaDescargas = File(contexto.filesDir, "descargas").apply { mkdirs() }
    /** Capítulos conocidos de cada manga: un archivo por manga (antes estaban en las preferencias). */
    private val conocidos = CapitulosConocidos(contexto.filesDir)

    /** Sube cada vez que cambia la biblioteca: las pantallas que lo leen se refrescan. */
    var version by mutableIntStateOf(0)
        private set

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

    var modoTema by prefEnum(prefs, "modo_tema", ModoTema.OSCURO_PURO)

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

    // Visor (Komikku): modo por defecto, zonas, escala, recortes, webtoon, autodesplazamiento…
    var modoVisor by prefEnum(prefs, "modo_visor", ModoVisor.DERECHA_IZQUIERDA)
    var zonasVisor by prefEnum(prefs, "zonas_visor", ZonasToque.AUTOMATICO)
    var invertirZonas by prefEnum(prefs, "invertir_zonas", InvertirZonas.NO)
    var escalaPagina by prefEnum(prefs, "escala_pagina", EscalaPagina.AJUSTAR)
    var recortarWebtoon by prefBool(prefs, "recortar_webtoon", false)
    var dividirDobles by prefBool(prefs, "dividir_dobles", false)
    var invertirDivision by prefBool(prefs, "invertir_division", false)
    var rellenoLateral by prefInt(prefs, "relleno_lateral", 0) // % de cada lado en webtoon
    var animarPaginas by prefBool(prefs, "animar_paginas", true)
    var mostrarNumeroPagina by prefBool(prefs, "mostrar_numero", true)
    var pantallaCompleta by prefBool(prefs, "pantalla_completa", true)
    var invertirVolumen by prefBool(prefs, "invertir_volumen", false)
    var mostrarTransiciones by prefBool(prefs, "mostrar_transiciones", true)
    var precargaPaginas by prefInt(prefs, "precarga", 4)
    var velocidadAuto by prefFloat(prefs, "velocidad_auto", 5f) // 1 (lento) … 10 (rápido)
    var dobleToqueZoom by prefBool(prefs, "doble_toque_zoom", true)
    var pasarCapituloSolo by prefBool(prefs, "pasar_capitulo_solo", true) // webtoon: al llegar al final, sigue

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

    // Biblioteca estilo Komikku: agrupación, filtros de tres estados, insignias y botón «continuar».
    var agrupar by prefEnum(prefs, "agrupar", Agrupar.CATEGORIA)
    var fDescargados by prefEnum(prefs, "f_descargados", Tri.IGNORAR)
    var fSinLeer by prefEnum(prefs, "f_sin_leer", Tri.IGNORAR)
    var fEmpezados by prefEnum(prefs, "f_empezados", Tri.IGNORAR)
    var fTerminados by prefEnum(prefs, "f_terminados", Tri.IGNORAR)
    var fSeguimiento by prefEnum(prefs, "f_seguimiento", Tri.IGNORAR)
    var fLocales by prefEnum(prefs, "f_locales", Tri.IGNORAR)
    var insigniaSinLeer by prefBool(prefs, "ins_sin_leer", true)
    var insigniaDescargas by prefBool(prefs, "ins_descargas", true)
    var insigniaIdioma by prefBool(prefs, "ins_idioma", false)
    var insigniaLocal by prefBool(prefs, "ins_local", true)
    var botonContinuar by prefBool(prefs, "boton_continuar", true)
    var conteoCategorias by prefBool(prefs, "conteo_categorias", true)

    // ---- Actualizaciones automáticas de la biblioteca (WorkManager) ----
    var intervaloActualizacion by prefInt(prefs, "act_intervalo", 24) // horas; 0 = solo a mano
    var actSoloWifi by prefBool(prefs, "act_wifi", true)
    var actSoloCargando by prefBool(prefs, "act_cargando", false)
    var actSaltarTerminados by prefBool(prefs, "act_saltar_terminados", true)
    var descargarNuevos by prefBool(prefs, "descargar_nuevos", false)
    var revisarExtensiones by prefBool(prefs, "revisar_ext", true)
    var ultimaRevisionExt by prefLong(prefs, "ult_revision_ext", 0L)

    // ---- Descargas ----
    var borrarAlLeer by prefBool(prefs, "borrar_al_leer", false)
    var descargarAdelante by prefInt(prefs, "descargar_adelante", 0) // capítulos siguientes al leer (0 = no)
    var descSoloWifi by prefBool(prefs, "desc_wifi", false)
    var descParalelas by prefInt(prefs, "desc_paralelas", 2)

    // ---- Explorar ----
    var ocultarEnBiblioteca by prefBool(prefs, "ocultar_en_biblioteca", false)
    var vistaExplorar by prefEnum(prefs, "vista_explorar", VistaExplorar.CUADRICULA)
    /** Catálogo de una fuente: columnas (0 = automático), filtro «en la biblioteca» y orden (0 fuente · 1 título). */
    var explorarColumnas by prefInt(prefs, "explorar_columnas", 0)
    var explorarEnBiblioteca by prefEnum(prefs, "explorar_en_biblioteca", if (prefs.getBoolean("ocultar_en_biblioteca", false)) Tri.EXCLUIR else Tri.IGNORAR)
    var explorarOrden by prefInt(prefs, "explorar_orden", 0)
    var explorarDescendente by prefBool(prefs, "explorar_desc", false)
    /** Pestaña de la biblioteca en la que estabas (se vuelve a ella al volver a la biblioteca o abrir la app). */
    var ultimaPestanaBiblioteca by prefString(prefs, "ultima_pestana_biblioteca", "")
    var globalSoloAncladas by prefBool(prefs, "global_ancladas", false)

    // ---- Seguridad y privacidad ----
    var bloqueo by prefBool(prefs, "bloqueo", false)
    var bloqueoMinutos by prefInt(prefs, "bloqueo_min", 0) // 0 = siempre al volver
    var pantallaSegura by prefBool(prefs, "pantalla_segura", false)
    var incognito by prefBool(prefs, "incognito", false)

    // ---- Avanzado ----
    var doh by prefInt(prefs, "doh", 0) // 0 ninguno · 1 Cloudflare · 2 Google · 3 AdGuard · 4 Quad9

    // ---- Copias automáticas ----
    var copiaAutoDias by prefInt(prefs, "copia_auto_dias", 0) // 0 = desactivadas
    var copiaCarpeta by prefString(prefs, "copia_carpeta", "") // URI de una carpeta elegida (SAF) o vacío
    var copiasMax by prefInt(prefs, "copias_max", 5)
    var ultimaCopia by prefLong(prefs, "ultima_copia", 0L)

    // ---- Estadísticas ----
    var tiempoLectura by prefLong(prefs, "tiempo_lectura", 0L) // milisegundos leyendo en total

    fun sumarTiempoLectura(ms: Long) {
        if (ms in 1_000L..(4L * 60 * 60 * 1000)) tiempoLectura += ms
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

    /** Cuántos cómics/mangas están vinculados a AniList (para filtros y estadísticas). */
    fun idsConSeguimiento(): Set<String> =
        prefs.all.keys.filter { it.startsWith("seg_") }.map { it.removePrefix("seg_") }.toSet()

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
        version++
    }

    /**
     * Manga en línea con seguimiento: sube el número de capítulos leídos (nunca lo baja). Bloquea: llamar desde un
     * hilo de fondo. Devuelve true si no hacía falta o se subió.
     */
    fun avanzarSeguimiento(id: String, capitulo: Int): Boolean {
        val s = seguimientoDe(id) ?: return true
        if (s.volumenes || capitulo <= s.numero) return true
        guardarSeguimiento(id, s.copy(numero = capitulo))
        marcarSincronizacion(id, true)
        return sincronizarAnilist(id)
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

    /** Modo del visor elegido para una serie (clave = id del cómic o "fuente|url" de un manga en línea). */
    fun modoVisorDe(clave: String): ModoVisor? {
        prefs.getString("visor_$clave", null)?.let { n -> ModoVisor.entries.firstOrNull { it.name == n } }?.let { return it }
        // Compatibilidad con el modo antiguo de los cómics locales.
        return when (modoDe(clave)) {
            Modo.MANGA -> ModoVisor.DERECHA_IZQUIERDA
            Modo.COMIC -> ModoVisor.IZQUIERDA_DERECHA
            Modo.VERTICAL -> ModoVisor.WEBTOON
            null -> null
        }
    }

    fun guardarModoVisor(clave: String, modo: ModoVisor?) {
        if (modo == null) prefs.edit().remove("visor_$clave").remove("modo_$clave").apply()
        else prefs.edit().putString("visor_$clave", modo.name).apply()
    }

    // Último cómic que abriste, para el botón "Continuar".
    var ultimoAbierto: String?
        get() = prefs.getString("ultimo", null)
        set(valor) = prefs.edit().putString("ultimo", valor).apply()

    // ---- Orden de la biblioteca ----
    var orden: Orden
        get() = prefs.getString("orden", null)
            ?.let { runCatching { Orden.valueOf(it) }.getOrNull() } ?: Orden.TITULO
        set(valor) { prefs.edit().putString("orden", valor.name).apply(); version++ }

    var ordenAscendente: Boolean
        get() = prefs.getBoolean("orden_asc", true)
        set(valor) { prefs.edit().putBoolean("orden_asc", valor).apply(); version++ }

    // ---- Categorías ----
    private var cacheCats: List<Categoria>? = null

    fun categorias(): List<Categoria> = synchronized(this) {
        cacheCats ?: runCatching {
            val json = JSONArray(prefs.getString("categorias", "[]"))
            List(json.length()) { i ->
                val o = json.getJSONObject(i)
                Categoria(o.getString("id"), o.getString("nombre"), o.optBoolean("oculta", false))
            }
        }.getOrDefault(emptyList()).also { cacheCats = it }
    }

    private fun guardarCategorias(lista: List<Categoria>) {
        synchronized(this) {
            cacheCats = lista.toList()
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
            version++
        }
    }

    @Synchronized
    fun crearCategoria(nombre: String): String? {
        val limpio = nombre.trim()
        if (limpio.isEmpty()) return null
        val c = Categoria(UUID.randomUUID().toString(), limpio, false)
        guardarCategorias(categorias() + c)
        return c.id
    }

    @Synchronized
    fun renombrarCategoria(id: String, nombre: String) {
        val limpio = nombre.trim()
        if (limpio.isEmpty()) return
        guardarCategorias(categorias().map { if (it.id == id) it.copy(nombre = limpio) else it })
    }

    // Guarda las categorías en el orden dado (las que falten se quedan al final).
    @Synchronized
    fun reordenarCategorias(ids: List<String>) {
        val actuales = categorias()
        val porId = actuales.associateBy { it.id }
        guardarCategorias(ids.mapNotNull { porId[it] } + actuales.filter { it.id !in ids })
    }

    @Synchronized
    fun alternarOculta(id: String) {
        guardarCategorias(categorias().map { if (it.id == id) it.copy(oculta = !it.oculta) else it })
    }

    // Borra la categoría; los cómics no se tocan, solo pierden esa etiqueta.
    @Synchronized
    fun eliminarCategoria(id: String) {
        guardarCategorias(categorias().filter { it.id != id })
        guardarAsignaciones(asignaciones().mapValues { (_, ids) -> ids - id })
    }

    // Qué categorías tiene cada cómic: id del cómic -> ids de categorías.
    private var cacheAsign: Map<String, Set<String>>? = null

    fun asignaciones(): Map<String, Set<String>> = synchronized(this) {
        cacheAsign ?: runCatching {
            val objeto = JSONObject(prefs.getString("asignaciones", "{}"))
            val mapa = HashMap<String, Set<String>>()
            for (clave in objeto.keys()) {
                val arreglo = objeto.getJSONArray(clave)
                mapa[clave] = List(arreglo.length()) { arreglo.getString(it) }.toSet()
            }
            mapa as Map<String, Set<String>>
        }.getOrDefault(emptyMap()).also { cacheAsign = it }
    }

    private fun guardarAsignaciones(mapa: Map<String, Set<String>>) {
        synchronized(this) {
            val limpio = mapa.filterValues { it.isNotEmpty() }
            cacheAsign = limpio
            val objeto = JSONObject()
            limpio.forEach { (comicId, ids) -> objeto.put(comicId, JSONArray(ids.toList())) }
            prefs.edit().putString("asignaciones", objeto.toString()).apply()
            version++
        }
    }

    // Cambia las categorías de varios cómics a la vez: añade unas y quita otras.
    @Synchronized
    fun modificarCategorias(comicIds: Set<String>, agregar: Set<String>, quitar: Set<String>) {
        val mapa = asignaciones().toMutableMap()
        comicIds.forEach { id -> mapa[id] = (mapa[id].orEmpty() + agregar) - quitar }
        guardarAsignaciones(mapa)
    }

    @Synchronized
    fun asignarCategorias(comicId: String, ids: Set<String>) {
        guardarAsignaciones(asignaciones() + (comicId to ids))
    }

    // La biblioteca se lee del JSON una sola vez y se guarda en memoria; cada cambio actualiza ambos.
    private var cacheComics: List<Comic>? = null

    fun cargar(): List<Comic> = synchronized(this) {
        cacheComics ?: leerComics().also { cacheComics = it }
    }

    private fun JSONArray?.aLista(): List<String> =
        if (this == null) emptyList() else List(length()) { optString(it) }.filter { it.isNotBlank() }

    /** La biblioteca en su propio archivo (antes era un texto dentro de las preferencias, reescrito entero en cada cambio). */
    private val archivoBiblioteca = File(contexto.filesDir, "biblioteca.json")
    private val guardadoProgramado = java.util.concurrent.atomic.AtomicBoolean(false)

    private fun textoBiblioteca(): String =
        if (archivoBiblioteca.exists()) archivoBiblioteca.readText() else prefs.getString("comics", "[]") ?: "[]"

    /** Escribe la biblioteca en segundo plano, agrupando los cambios seguidos en una sola escritura. */
    private fun programarGuardado() {
        if (!guardadoProgramado.compareAndSet(false, true)) return
        Persistencia.hilo.schedule({
            guardadoProgramado.set(false)
            runCatching { escribirBiblioteca() }
        }, 300, java.util.concurrent.TimeUnit.MILLISECONDS)
    }

    private fun escribirBiblioteca() {
        val lista = synchronized(this) { cacheComics } ?: return
        val json = JSONArray()
        lista.forEach { json.put(it.aJson()) }
        val tmp = File(archivoBiblioteca.path + ".tmp")
        tmp.writeText(json.toString())
        if (!tmp.renameTo(archivoBiblioteca)) { archivoBiblioteca.delete(); tmp.renameTo(archivoBiblioteca) }
        if (prefs.contains("comics")) prefs.edit().remove("comics").apply() // ya migrada
    }

    private fun leerComics(): List<Comic> = runCatching {
        val json = JSONArray(textoBiblioteca())
        List(json.length()) { i ->
            val o = json.getJSONObject(i)
            Comic(
                o.getString("id"),
                o.getString("titulo"),
                o.getInt("paginas"),
                o.getInt("ultima"),
                o.optLong("leido", 0L),
                fuenteId = o.optLong("fuente", 0L),
                urlManga = o.optString("url_manga").ifBlank { null },
                portadaUrl = o.optString("portada_url").ifBlank { null },
                anadido = o.optLong("anadido", 0L),
                autor = o.optString("autor").ifBlank { null },
                estado = o.optInt("estado", 0),
                generos = o.optJSONArray("generos").aLista(),
                descripcion = o.optString("descripcion").ifBlank { null },
                ultimaRevision = o.optLong("ult_revision", 0L),
                ultimoCapitulo = o.optLong("ult_cap", 0L),
                editado = o.optBoolean("editado", false),
                portadaPropia = o.optBoolean("portada_propia", false),
            )
        }
    }.getOrDefault(emptyList())

    private fun Comic.aJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("titulo", titulo)
        .put("paginas", paginas)
        .put("ultima", ultimaPagina)
        .put("leido", leido)
        .put("fuente", fuenteId)
        .put("url_manga", urlManga ?: "")
        .put("portada_url", portadaUrl ?: "")
        .put("anadido", anadido)
        .put("autor", autor ?: "")
        .put("estado", estado)
        .put("generos", JSONArray(generos))
        .put("descripcion", descripcion ?: "")
        .put("ult_revision", ultimaRevision)
        .put("ult_cap", ultimoCapitulo)
        .put("editado", editado)
        .put("portada_propia", portadaPropia)

    private fun guardar(lista: List<Comic>) {
        synchronized(this) {
            if (lista == cacheComics) return // nada cambió: no reescribe el JSON
            cacheComics = lista.toList()
            programarGuardado()
            version++
        }
    }

    // Lee, transforma y guarda como una sola operación (evita perder cambios entre hilos).
    private fun modificar(f: (List<Comic>) -> List<Comic>) {
        synchronized(this) { guardar(f(cargar())) }
    }

    fun comic(id: String): Comic? = cargar().firstOrNull { it.id == id }

    /** Cambia un cómic de la biblioteca (por id). */
    fun actualizarComic(id: String, cambio: (Comic) -> Comic) {
        modificar { l -> l.map { if (it.id == id) cambio(it) else it } }
    }

    // Devuelve el cómic añadido, o null si el archivo no sirve.
    fun importar(uri: Uri): Comic? {
        val nombre = contexto.contentResolver.query(uri, null, null, null, null)?.use { c ->
            val col = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (col >= 0 && c.moveToFirst()) c.getString(col) else null
        } ?: "Sin título"

        val id = UUID.randomUUID().toString()
        val archivo = archivoDe(id)

        // PDF: cada página se dibuja como imagen y se empaqueta como .cbz (así el visor lo abre igual).
        val mime = runCatching { contexto.contentResolver.getType(uri) }.getOrNull()
        if (mime == "application/pdf" || nombre.endsWith(".pdf", ignoreCase = true)) {
            return if (Pdf.aCbz(contexto, uri, archivo)) registrar(id, archivo, nombre.substringBeforeLast('.'))
            else { archivo.delete(); null }
        }

        val copiado = contexto.contentResolver.openInputStream(uri)?.use { entrada ->
            archivo.outputStream().use { salida -> entrada.copyTo(salida) }
            true
        } ?: false
        if (!copiado) return null

        return registrar(id, archivo, nombre.substringBeforeLast('.'))
    }

    // Añade a la biblioteca un .cbz que ya tenemos en disco.
    fun importarArchivo(origen: File, titulo: String): Comic? {
        val id = UUID.randomUUID().toString()
        val archivo = archivoDe(id)
        origen.copyTo(archivo, overwrite = true)
        return registrar(id, archivo, titulo)
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

        val comic = Comic(id, titulo, paginas.size, 0, anadido = System.currentTimeMillis())
        modificar { l -> l + comic }
        // Si venía de una copia de seguridad, recupera su progreso y categorías.
        return aplicarPendiente(comic)
    }

    /** Usa una imagen (bytes de una página, una foto…) como portada de la serie. */
    fun cambiarPortada(id: String, bytes: ByteArray): Boolean = runCatching {
        val bmp = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return false
        val escala = minOf(1f, 600f / maxOf(bmp.width, bmp.height))
        val chica = if (escala < 1f) Bitmap.createScaledBitmap(bmp, (bmp.width * escala).toInt(), (bmp.height * escala).toInt(), true) else bmp
        portadaDe(id).outputStream().use { chica.compress(Bitmap.CompressFormat.JPEG, 88, it) }
        actualizarComic(id) { it.copy(portadaPropia = true) }
        Portadas.olvidar(id)
        true
    }.getOrDefault(false)

    // ---- Copia de seguridad (parte de la biblioteca; CopiaSeguridad añade lo demás) ----
    // Guarda progreso, categorías, modos de lectura y ajustes (no los archivos de los cómics).
    // Los cómics se reconocen por título y número de páginas.
    private fun claveDe(titulo: String, paginas: Int) = titulo.trim().lowercase() + "|" + paginas

    private fun pendientes(): JSONObject = JSONObject(prefs.getString("pendientes", "{}"))

    /** Ajustes que viajan en la copia (todo menos datos privados como el token de AniList). */
    private val clavesAjustes = listOf(
        "paleta", "color_portada", "modo_tema", "zonas_toque", "teclas_volumen", "mantener_encendida", "modo_visor",
        "zonas_visor", "invertir_zonas", "escala_pagina", "recortar_webtoon", "dividir_dobles", "invertir_division",
        "relleno_lateral", "animar_paginas", "mostrar_numero", "pantalla_completa", "invertir_volumen",
        "mostrar_transiciones", "precarga", "velocidad_auto", "doble_toque_zoom", "pasar_capitulo_solo",
        "fondo_lector", "orientacion", "brillo_on", "brillo", "grises", "invertir", "tinte", "tinte_intensidad",
        "recortar_bordes", "vista", "columnas", "mostrar_pagina", "insignia_progreso", "agrupar", "f_descargados",
        "f_sin_leer", "f_empezados", "f_terminados", "f_seguimiento", "f_locales", "ins_sin_leer", "ins_descargas",
        "ins_idioma", "ins_local", "boton_continuar", "conteo_categorias", "act_intervalo", "act_wifi",
        "act_cargando", "act_saltar_terminados", "descargar_nuevos", "revisar_ext", "borrar_al_leer",
        "descargar_adelante", "desc_wifi", "desc_paralelas", "ocultar_en_biblioteca", "vista_explorar",
        "global_ancladas", "pantalla_segura", "doh", "orden", "orden_asc", "rtl", "copias_max",
    )

    private fun ajustesComoJson(): JSONObject {
        val o = JSONObject()
        val todos = prefs.all
        clavesAjustes.forEach { k ->
            when (val v = todos[k]) {
                is Boolean -> o.put(k, JSONObject().put("t", "b").put("v", v))
                is Int -> o.put(k, JSONObject().put("t", "i").put("v", v))
                is Long -> o.put(k, JSONObject().put("t", "l").put("v", v))
                is Float -> o.put(k, JSONObject().put("t", "f").put("v", v.toDouble()))
                is String -> o.put(k, JSONObject().put("t", "s").put("v", v))
            }
        }
        return o
    }

    private fun aplicarAjustesJson(o: JSONObject) {
        val e = prefs.edit()
        for (k in o.keys()) {
            if (k !in clavesAjustes) continue
            val x = o.optJSONObject(k) ?: continue
            when (x.optString("t")) {
                "b" -> e.putBoolean(k, x.optBoolean("v"))
                "i" -> e.putInt(k, x.optInt("v"))
                "l" -> e.putLong(k, x.optLong("v"))
                "f" -> e.putFloat(k, x.optDouble("v").toFloat())
                "s" -> e.putString(k, x.optString("v"))
            }
        }
        e.apply()
    }

    fun crearCopia(): JSONObject {
        val listaCategorias = categorias()
        val nombres = listaCategorias.associate { it.id to it.nombre }
        val asign = asignaciones()

        val raiz = JSONObject()
        raiz.put("version", 2)
        raiz.put("app", "Lector")
        raiz.put("fecha", System.currentTimeMillis())
        // Formato antiguo (version 1) de los ajustes, para que una copia nueva siga sirviendo en versiones viejas.
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
        raiz.put("ajustes2", ajustesComoJson())

        val cats = JSONArray()
        listaCategorias.forEach {
            cats.put(JSONObject().put("nombre", it.nombre).put("oculta", it.oculta))
        }
        raiz.put("categorias", cats)

        val lista = JSONArray()
        cargar().filter { !it.enLinea }.forEach { c ->
            val e = JSONObject()
                .put("titulo", c.titulo)
                .put("paginas", c.paginas)
                .put("ultima", c.ultimaPagina)
                .put("leido", c.leido)
                .put("anadido", c.anadido)
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

        // Mangas añadidos desde fuentes (biblioteca en línea): se guardan con su fuente, dirección y progreso.
        val enLinea = JSONArray()
        cargar().filter { it.enLinea }.forEach { c ->
            val cs = JSONArray()
            asign[c.id].orEmpty().mapNotNull { nombres[it] }.forEach { cs.put(it) }
            val e = JSONObject()
                .put("fuente", c.fuenteId)
                .put("url", c.urlManga.orEmpty())
                .put("titulo", c.titulo)
                .put("portada", c.portadaUrl.orEmpty())
                .put("caps", c.paginas)
                .put("leidos", c.ultimaPagina)
                .put("leido", c.leido)
                .put("anadido", c.anadido)
                .put("autor", c.autor.orEmpty())
                .put("estado", c.estado)
                .put("generos", JSONArray(c.generos))
                .put("descripcion", c.descripcion.orEmpty())
                .put("editado", c.editado)
                .put("categorias", cs)
            seguimientoDe(c.id)?.let {
                e.put("seguimiento", JSONObject().put("media", it.mediaId).put("titulo", it.titulo).put("vol", it.volumenes).put("numero", it.numero))
            }
            modoVisorDe(c.id)?.let { e.put("visor", it.name) }
            enLinea.put(e)
        }
        raiz.put("en_linea", enLinea)

        // Historial online.
        val h = JSONArray()
        historialLinea.forEach { h.put(entradaAJson(it)) }
        raiz.put("historial_linea", h)
        raiz.put("tiempo_lectura", tiempoLectura)
        return raiz
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

        modificar { l -> l.map { if (it.id == comic.id) nuevo else it } }
        guardarAsignaciones(mapa)
        guardarCategorias(cats)
        prefs.edit().putString("pendientes", pend.toString()).apply()
        return nuevo
    }

    // Lanza una excepción si el texto no es una copia válida.
    fun restaurarCopia(raiz: JSONObject): ResultadoRestauracion {
        val v = raiz.optInt("version", 0)
        require(v == 1 || v == 2) { "Copia no válida" }

        raiz.optJSONObject("ajustes2")?.let { aplicarAjustesJson(it) }
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
            val indice = locales.indexOfFirst { !it.enLinea && claveDe(it.titulo, it.paginas) == clave }
            if (indice >= 0) {
                locales[indice] = aplicarEntrada(locales[indice], e, cats, mapa)
                actualizados++
            } else {
                pend.put(clave, e)
                sinArchivo++
            }
        }

        // Biblioteca en línea: se recrea cada manga que aún no esté (la portada se vuelve a bajar al verla).
        // Índice por «fuente|url» para no recorrer la biblioteca entera por cada manga.
        val enLineaPorClave = HashMap<String, Comic>().also { m -> locales.forEach { c -> if (c.enLinea) m["${c.fuenteId}|${c.urlManga}"] = c } }
        raiz.optJSONArray("en_linea")?.let { arr ->
            for (i in 0 until arr.length()) {
                val e = arr.getJSONObject(i)
                val fuente = e.optLong("fuente", 0L)
                val url = e.optString("url")
                if (url.isBlank()) continue
                val existente = enLineaPorClave["$fuente|$url"]
                val comic = existente ?: Comic(
                    id = UUID.randomUUID().toString(),
                    titulo = e.optString("titulo").ifBlank { "Sin título" },
                    paginas = e.optInt("caps", 0),
                    ultimaPagina = e.optInt("leidos", 0),
                    leido = e.optLong("leido", 0L),
                    fuenteId = fuente, urlManga = url,
                    portadaUrl = e.optString("portada").ifBlank { null },
                    anadido = e.optLong("anadido", System.currentTimeMillis()),
                    autor = e.optString("autor").ifBlank { null },
                    estado = e.optInt("estado", 0),
                    generos = e.optJSONArray("generos").aLista(),
                    descripcion = e.optString("descripcion").ifBlank { null },
                    editado = e.optBoolean("editado", false),
                ).also { locales.add(it); enLineaPorClave["$fuente|$url"] = it }
                val nombresCats = e.optJSONArray("categorias")
                if (nombresCats != null && nombresCats.length() > 0) {
                    val ids = HashSet<String>()
                    for (k in 0 until nombresCats.length()) ids.add(idCategoria(nombresCats.getString(k), false, cats))
                    mapa[comic.id] = mapa[comic.id].orEmpty() + ids
                }
                e.optJSONObject("seguimiento")?.let { seg ->
                    if (seguimientoDe(comic.id) == null) runCatching {
                        guardarSeguimiento(comic.id, Seguimiento(seg.getInt("media"), seg.getString("titulo"), seg.getBoolean("vol"), seg.getInt("numero")))
                    }
                }
                e.optString("visor").takeIf { it.isNotBlank() }?.let { n ->
                    ModoVisor.entries.firstOrNull { it.name == n }?.let { guardarModoVisor(comic.id, it) }
                }
                actualizados++
            }
        }

        raiz.optJSONArray("historial_linea")?.let { arr ->
            val copia = (0 until arr.length()).mapNotNull { runCatching { jsonAEntrada(arr.getJSONObject(it)) }.getOrNull() }
            val unidas = (historialLinea + copia).sortedByDescending { it.leido }
                .distinctBy { it.fuenteId to it.urlManga }.take(300)
            historialLinea = unidas
            guardarHistorialLinea(unidas)
        }
        val tiempo = raiz.optLong("tiempo_lectura", 0L)
        if (tiempo > tiempoLectura) tiempoLectura = tiempo

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
        modificar { l -> l.map { if (it.id == id) it.copy(leido = 0L) else it } }
    }

    fun limpiarHistorial() {
        modificar { l -> l.map { it.copy(leido = 0L) } }
        historialLinea = emptyList()
        guardarHistorialLinea(emptyList())
    }

    // ---- Historial de lectura online (mangas de fuentes; estén o no en la biblioteca) ----
    var historialLinea by mutableStateOf(leerHistorialLinea())
        private set

    private fun jsonAEntrada(o: JSONObject) = EntradaHistorial(
        fuenteId = o.getLong("fuente"),
        urlManga = o.getString("manga"),
        titulo = o.getString("titulo"),
        portadaUrl = o.optString("portada").ifBlank { null },
        capUrl = o.getString("cap"),
        capNombre = o.optString("cap_nombre"),
        pagina = o.optInt("pagina", 0),
        total = o.optInt("total", 0),
        leido = o.optLong("leido", 0L),
    )

    private fun entradaAJson(it: EntradaHistorial): JSONObject = JSONObject()
        .put("fuente", it.fuenteId)
        .put("manga", it.urlManga)
        .put("titulo", it.titulo)
        .put("portada", it.portadaUrl ?: "")
        .put("cap", it.capUrl)
        .put("cap_nombre", it.capNombre)
        .put("pagina", it.pagina)
        .put("total", it.total)
        .put("leido", it.leido)

    private fun leerHistorialLinea(): List<EntradaHistorial> = runCatching {
        val a = JSONArray(prefs.getString("historial_linea", "[]"))
        List(a.length()) { i -> jsonAEntrada(a.getJSONObject(i)) }
    }.getOrDefault(emptyList())

    private fun guardarHistorialLinea(lista: List<EntradaHistorial>) {
        val a = JSONArray()
        lista.forEach { a.put(entradaAJson(it)) }
        prefs.edit().putString("historial_linea", a.toString()).apply()
    }

    // Anota (o actualiza) el capítulo que se está leyendo. Un manga ocupa una sola fila: la más reciente.
    // En modo incógnito no se anota nada (como Komikku).
    @Synchronized
    fun registrarHistorialLinea(e: EntradaHistorial) {
        if (incognito) return
        val nueva = (listOf(e) + historialLinea.filterNot { it.fuenteId == e.fuenteId && it.urlManga == e.urlManga })
            .take(300)
        historialLinea = nueva
        guardarHistorialLinea(nueva)
        // Si el manga está en la biblioteca, también sube en "último leído".
        comicEnLinea(e.fuenteId, e.urlManga)?.let { c ->
            modificar { l -> l.map { if (it.id == c.id) it.copy(leido = e.leido) else it } }
        }
    }

    @Synchronized
    fun quitarHistorialLinea(fuenteId: Long, urlManga: String) {
        val nueva = historialLinea.filterNot { it.fuenteId == fuenteId && it.urlManga == urlManga }
        historialLinea = nueva
        guardarHistorialLinea(nueva)
    }

    // ---- Mangas de fuentes dentro de la biblioteca ("En biblioteca" de la ficha) ----
    fun comicEnLinea(fuenteId: Long, urlManga: String): Comic? =
        cargar().firstOrNull { it.enLinea && it.fuenteId == fuenteId && it.urlManga == urlManga }

    /** Claves "fuente|url" de todos los mangas en línea de la biblioteca (para marcar "en biblioteca" rápido). */
    fun clavesEnLinea(): Set<String> = cargar().filter { it.enLinea }.map { "${it.fuenteId}|${it.urlManga}" }.toSet()

    // Añade el manga a la biblioteca (o devuelve el que ya estaba). La portada se guarda en disco.
    @Synchronized
    fun anadirEnLinea(
        fuenteId: Long, urlManga: String, titulo: String, portadaUrl: String?,
        portada: Bitmap?, capitulos: Int, leidos: Int,
        autor: String? = null, estado: Int = 0, generos: List<String> = emptyList(), descripcion: String? = null,
    ): Comic {
        comicEnLinea(fuenteId, urlManga)?.let { return it }
        val id = UUID.randomUUID().toString()
        if (portada != null) {
            runCatching {
                portadaDe(id).outputStream().use { portada.compress(Bitmap.CompressFormat.JPEG, 85, it) }
            }
        }
        val comic = Comic(
            id = id, titulo = titulo, paginas = capitulos, ultimaPagina = leidos,
            fuenteId = fuenteId, urlManga = urlManga, portadaUrl = portadaUrl,
            anadido = System.currentTimeMillis(), autor = autor, estado = estado, generos = generos,
            descripcion = descripcion,
        )
        modificar { l -> l + comic }
        return comic
    }

    // ---- Escrituras en lote (restaurar copias rápido: una sola escritura en vez de una por manga) ----

    /** Un manga de fuente que se va a añadir de golpe con [anadirVariosEnLinea]. */
    data class NuevoEnLinea(
        val fuenteId: Long, val url: String, val titulo: String, val portadaUrl: String?,
        val capitulos: Int, val leidos: Int, val autor: String?, val estado: Int, val generos: List<String>,
        val descripcion: String?, val anadido: Long,
    )

    /**
     * Añade muchos mangas en una sola escritura. Devuelve, para cada «fuente|url» pedido, el cómic de la biblioteca
     * (el nuevo o el que ya estaba) y cuántos eran nuevos.
     */
    @Synchronized
    fun anadirVariosEnLinea(lista: List<NuevoEnLinea>): Pair<Map<String, Comic>, Int> {
        val actuales = cargar()
        val porClave = HashMap<String, Comic>()
        actuales.forEach { if (it.enLinea) porClave["${it.fuenteId}|${it.urlManga}"] = it }
        val nuevos = ArrayList<Comic>()
        val ahora = System.currentTimeMillis()
        lista.forEach { n ->
            val k = "${n.fuenteId}|${n.url}"
            if (k in porClave) return@forEach
            val c = Comic(
                id = UUID.randomUUID().toString(), titulo = n.titulo, paginas = n.capitulos, ultimaPagina = n.leidos,
                fuenteId = n.fuenteId, urlManga = n.url, portadaUrl = n.portadaUrl,
                anadido = if (n.anadido > 0) n.anadido else ahora, autor = n.autor, estado = n.estado, generos = n.generos,
                descripcion = n.descripcion,
            )
            porClave[k] = c
            nuevos += c
        }
        if (nuevos.isNotEmpty()) guardar(actuales + nuevos)
        return porClave to nuevos.size
    }

    /** Capítulos ya conocidos de muchos mangas («fuente|url» → urls), en una sola escritura. */
    fun registrarConocidosVarios(mapa: Map<Pair<Long, String>, Collection<String>>) {
        mapa.forEach { (k, urls) ->
            if (urls.isEmpty()) return@forEach
            conocidos.guardar(k.first, k.second, conocidos.leer(k.first, k.second).orEmpty() + urls)
        }
    }

    /** Guarda ya (en este hilo) los capítulos conocidos de un manga, sumándolos a los que hubiera. */
    fun guardarConocidosYa(fuenteId: Long, urlManga: String, urls: Collection<String>) {
        if (urls.isEmpty()) return
        val ya = conocidos.leer(fuenteId, urlManga)
        conocidos.guardarYa(fuenteId, urlManga, if (ya == null) urls else ya + urls)
    }

    /**
     * Las versiones anteriores guardaban los capítulos conocidos de cada manga en las preferencias («cc|fuente|url»):
     * con una biblioteca grande eran decenas de MB cargados en memoria siempre. Se pasan a archivos y se borran de ahí.
     */
    private fun migrarConocidos() {
        val claves = prefs.all.keys.filter { it.startsWith("cc|") }
        if (claves.isEmpty()) return
        val ed = prefs.edit()
        claves.forEach { k ->
            val resto = k.removePrefix("cc|")
            val fuente = resto.substringBefore('|').toLongOrNull()
            val url = resto.substringAfter('|', "")
            val urls = runCatching { prefs.getStringSet(k, null) }.getOrNull()
            if (fuente != null && url.isNotEmpty() && urls != null) runCatching { conocidos.guardarYa(fuente, url, urls) }
            ed.remove(k)
        }
        ed.commit()
    }

    /** Añade categorías a muchos cómics de una vez (id del cómic → ids de categorías). */
    @Synchronized
    fun anadirCategoriasVarios(mapa: Map<String, Set<String>>) {
        if (mapa.isEmpty()) return
        val actual = asignaciones().toMutableMap()
        mapa.forEach { (id, cats) -> actual[id] = actual[id].orEmpty() + cats }
        guardarAsignaciones(actual)
    }

    /** Mete muchas entradas en el historial en línea de una vez (un manga = su lectura más reciente). */
    @Synchronized
    fun anadirHistorialVarios(lista: List<EntradaHistorial>) {
        if (lista.isEmpty()) return
        val nueva = (lista + historialLinea).sortedByDescending { it.leido }.distinctBy { it.fuenteId to it.urlManga }.take(300)
        historialLinea = nueva
        guardarHistorialLinea(nueva)
        val ultimo = HashMap<String, Long>()
        lista.forEach { e -> val k = "${e.fuenteId}|${e.urlManga}"; if ((ultimo[k] ?: 0L) < e.leido) ultimo[k] = e.leido }
        modificar { l -> l.map { c -> if (c.enLinea) ultimo["${c.fuenteId}|${c.urlManga}"]?.takeIf { it > c.leido }?.let { c.copy(leido = it) } ?: c else c } }
    }

    /** Guarda los datos de la ficha (autor, estado, géneros…) salvo que el usuario los haya editado. */
    fun actualizarInfoEnLinea(
        fuenteId: Long, urlManga: String, autor: String?, estado: Int, generos: List<String>, descripcion: String?,
        portadaUrl: String?, ultimoCapitulo: Long,
    ) {
        val c = comicEnLinea(fuenteId, urlManga) ?: return
        actualizarComic(c.id) {
            val base = it.copy(ultimoCapitulo = maxOf(it.ultimoCapitulo, ultimoCapitulo))
            if (it.editado) base
            else base.copy(
                autor = autor ?: it.autor, estado = if (estado != 0) estado else it.estado,
                generos = generos.ifEmpty { it.generos }, descripcion = descripcion ?: it.descripcion,
                portadaUrl = portadaUrl ?: it.portadaUrl,
            )
        }
    }

    /** Edición manual de la información (Komikku: «Editar información»). */
    fun editarInfo(id: String, titulo: String, autor: String?, descripcion: String?, generos: List<String>, estado: Int) {
        actualizarComic(id) {
            it.copy(
                titulo = titulo.ifBlank { it.titulo }, autor = autor?.ifBlank { null }, descripcion = descripcion?.ifBlank { null },
                generos = generos, estado = estado, editado = true,
            )
        }
    }

    /**
     * Migración de fuente (como Komikku): el mismo manga de la biblioteca pasa a otra fuente. Es la MISMA entrada
     * (mismo id), así que se conservan categorías, portada guardada y posición. Devuelve false si el manga destino
     * ya está en la biblioteca.
     */
    @Synchronized
    fun migrarEnLinea(
        c: Comic, nuevaFuenteId: Long, nuevaUrl: String, nuevaPortadaUrl: String?, capitulos: Int, leidos: Int,
    ): Boolean {
        if (comicEnLinea(nuevaFuenteId, nuevaUrl) != null) return false
        val viejaUrl = c.urlManga ?: return false
        modificar { l ->
            l.map {
                if (it.id == c.id) {
                    it.copy(
                        fuenteId = nuevaFuenteId, urlManga = nuevaUrl, portadaUrl = nuevaPortadaUrl ?: it.portadaUrl,
                        paginas = capitulos, ultimaPagina = leidos.coerceIn(0, maxOf(capitulos, 0)),
                    )
                } else it
            }
        }
        // Lo anotado de la fuente vieja (novedades pendientes, historial) ya no corresponde a este manga.
        olvidarCapitulos(c.fuenteId, viejaUrl)
        quitarHistorialLinea(c.fuenteId, viejaUrl)
        return true
    }

    fun quitarEnLinea(fuenteId: Long, urlManga: String) {
        comicEnLinea(fuenteId, urlManga)?.let { eliminar(it.id) }
    }

    // Mantiene al día "capítulos leídos / capítulos totales" de un manga de la biblioteca.
    @Synchronized
    fun actualizarProgresoEnLinea(fuenteId: Long, urlManga: String, leidos: Int, total: Int) {
        val c = comicEnLinea(fuenteId, urlManga) ?: return
        if (c.paginas == total && c.ultimaPagina == leidos) return
        modificar { l -> l.map { if (it.id == c.id) it.copy(paginas = total, ultimaPagina = leidos) else it } }
    }

    // ---- Actualizaciones: capítulos nuevos de los mangas en línea de la biblioteca ----
    var actualizaciones by mutableStateOf(leerActualizaciones())
        private set
    var ultimaActualizacion by mutableStateOf(prefs.getLong("ult_actualizacion", 0L))
        private set

    /** Lo que no se pudo revisar en la última actualización (pantalla «Errores de actualización»). */
    var erroresActualizacion by mutableStateOf(leerErrores())
        private set

    private fun leerErrores(): List<ErrorActualizacion> = runCatching {
        val a = JSONArray(prefs.getString("errores_act", "[]"))
        List(a.length()) { i -> a.getJSONObject(i).let { ErrorActualizacion(it.optString("t"), it.optString("f"), it.optString("m")) } }
    }.getOrDefault(emptyList())

    fun guardarErrores(lista: List<ErrorActualizacion>) {
        erroresActualizacion = lista
        val a = JSONArray()
        lista.forEach { a.put(JSONObject().put("t", it.titulo).put("f", it.fuente).put("m", it.motivo)) }
        prefs.edit().putString("errores_act", a.toString()).apply()
    }

    private fun leerActualizaciones(): List<ActualizacionCap> = runCatching {
        val a = JSONArray(prefs.getString("actualizaciones", "[]"))
        List(a.length()) { i ->
            val o = a.getJSONObject(i)
            ActualizacionCap(
                fuenteId = o.getLong("fuente"), urlManga = o.getString("manga"), titulo = o.getString("titulo"),
                portadaUrl = o.optString("portada").ifBlank { null },
                capUrl = o.getString("cap"), capNombre = o.optString("nombre"),
                capNumero = o.optDouble("numero", -1.0).toFloat(), capFecha = o.optLong("fecha", 0L),
                scanlator = o.optString("scan").ifBlank { null }, detectado = o.optLong("detectado", 0L),
            )
        }
    }.getOrDefault(emptyList())

    private fun guardarActualizaciones(lista: List<ActualizacionCap>) {
        val a = JSONArray()
        lista.forEach {
            a.put(
                JSONObject().put("fuente", it.fuenteId).put("manga", it.urlManga).put("titulo", it.titulo)
                    .put("portada", it.portadaUrl ?: "").put("cap", it.capUrl).put("nombre", it.capNombre)
                    .put("numero", it.capNumero.toDouble()).put("fecha", it.capFecha)
                    .put("scan", it.scanlator ?: "").put("detectado", it.detectado),
            )
        }
        prefs.edit().putString("actualizaciones", a.toString()).apply()
    }

    /** URLs de capítulos conocidas de un manga (las que se guardaron al revisar). Vacío si nunca se revisó. */
    fun capitulosConocidos(fuenteId: Long, urlManga: String): Set<String> =
        conocidos.leer(fuenteId, urlManga).orEmpty()

    /**
     * Compara los capítulos que ofrece la fuente con los que ya conocíamos de ese manga.
     * La primera vez solo guarda la lista (no hay "nuevos"); después, lo que falte se anota como novedad.
     * Devuelve los capítulos nuevos.
     */
    @Synchronized
    fun registrarCapitulos(
        fuenteId: Long, urlManga: String, titulo: String, portadaUrl: String?, caps: List<CapituloFuente>,
    ): List<CapituloFuente> {
        if (caps.isEmpty()) return emptyList()
        val actuales = caps.mapTo(HashSet()) { it.url }
        val ya = conocidos.leer(fuenteId, urlManga)
        if (ya == null) {
            conocidos.guardar(fuenteId, urlManga, actuales)
            return emptyList()
        }
        val nuevos = caps.filter { it.url !in ya }
        // Se guarda siempre la lista actual (así, "marcar todo como leído" conoce todos los capítulos).
        if (nuevos.isEmpty()) {
            if (!ya.containsAll(actuales)) conocidos.guardar(fuenteId, urlManga, ya + actuales)
            return emptyList()
        }
        conocidos.guardar(fuenteId, urlManga, ya + actuales)
        val ahora = System.currentTimeMillis()
        val entradas = nuevos.map {
            ActualizacionCap(fuenteId, urlManga, titulo, portadaUrl, it.url, it.nombre, it.numero, it.fecha, it.scanlator, ahora)
        }
        val lista = (entradas + actualizaciones).distinctBy { it.fuenteId to it.capUrl }.take(500)
        actualizaciones = lista
        guardarActualizaciones(lista)
        return nuevos
    }

    private fun olvidarCapitulos(fuenteId: Long, urlManga: String) {
        conocidos.olvidar(fuenteId, urlManga)
        val lista = actualizaciones.filterNot { it.fuenteId == fuenteId && it.urlManga == urlManga }
        if (lista.size != actualizaciones.size) {
            actualizaciones = lista
            guardarActualizaciones(lista)
        }
    }

    fun limpiarActualizaciones() {
        actualizaciones = emptyList()
        guardarActualizaciones(emptyList())
    }

    fun quitarActualizaciones(claves: Set<String>) {
        val lista = actualizaciones.filterNot { "${it.fuenteId}|${it.capUrl}" in claves }
        actualizaciones = lista
        guardarActualizaciones(lista)
    }

    fun marcarActualizado() {
        ultimaActualizacion = System.currentTimeMillis()
        prefs.edit().putLong("ult_actualizacion", ultimaActualizacion).apply()
    }

    // ---- Capítulos descargados ----
    // Como en Komikku, una descarga pertenece al capítulo de su manga: NO es un cómic aparte de la
    // biblioteca. Se guarda como .cbz en su propia carpeta y el lector online la usa en vez de la red.
    fun archivoDescarga(fuenteId: Long, capUrl: String): File {
        val h = java.security.MessageDigest.getInstance("SHA-256")
            .digest("$fuenteId|$capUrl".toByteArray()).joinToString("") { "%02x".format(it) }.take(32)
        return File(carpetaDescargas, "$h.cbz")
    }

    // Nombres de los archivos descargados, en memoria (la ficha pregunta por CADA capítulo en cada recomposición).
    private var descargados: MutableSet<String>? = null

    private fun nombresDescargados(): MutableSet<String> = synchronized(this) {
        descargados ?: (carpetaDescargas.list()?.filter { it.endsWith(".cbz") }?.toHashSet() ?: HashSet()).also { descargados = it }
    }

    // A qué manga pertenece cada descarga: nombre del archivo -> "fuente|urlManga" (para contar por serie).
    private var indiceDescargas: MutableMap<String, String>? = null

    private fun indice(): MutableMap<String, String> = synchronized(this) {
        indiceDescargas ?: runCatching {
            val o = JSONObject(prefs.getString("desc_idx", "{}") ?: "{}")
            val m = HashMap<String, String>()
            o.keys().forEach { k -> m[k] = o.getString(k) }
            m as MutableMap<String, String>
        }.getOrDefault(HashMap()).also { indiceDescargas = it }
    }

    private fun guardarIndice() {
        val o = JSONObject()
        synchronized(this) { indice().forEach { (k, v) -> o.put(k, v) } }
        prefs.edit().putString("desc_idx", o.toString()).apply()
    }

    fun estaDescargado(fuenteId: Long, capUrl: String): Boolean =
        archivoDescarga(fuenteId, capUrl).name in nombresDescargados()

    /** Cuántos capítulos descargados tiene un manga (insignia de la biblioteca). */
    fun descargasDe(fuenteId: Long, urlManga: String): Int {
        val k = "$fuenteId|$urlManga"
        val nombres = nombresDescargados()
        return synchronized(this) { indice().count { (n, v) -> v == k && n in nombres } }
    }

    /** Todas las cuentas de descargas por manga a la vez ("fuente|url" -> n). */
    fun conteoDescargas(): Map<String, Int> {
        val nombres = nombresDescargados()
        return synchronized(this) { indice().filterKeys { it in nombres }.values.groupingBy { it }.eachCount() }
    }

    fun totalDescargas(): Int = nombresDescargados().size

    fun bytesDescargas(): Long = carpetaDescargas.listFiles()?.sumOf { it.length() } ?: 0L

    // Mueve (o copia) el .cbz recién bajado a su sitio definitivo.
    fun guardarDescarga(origen: File, fuenteId: Long, capUrl: String, urlManga: String? = null) {
        val destino = archivoDescarga(fuenteId, capUrl)
        destino.delete()
        if (!origen.renameTo(destino)) {
            origen.copyTo(destino, overwrite = true)
            origen.delete()
        }
        synchronized(this) {
            nombresDescargados().add(destino.name)
            if (urlManga != null) indice()[destino.name] = "$fuenteId|$urlManga"
        }
        if (urlManga != null) guardarIndice()
        version++
    }

    fun borrarDescarga(fuenteId: Long, capUrl: String) {
        val archivo = archivoDescarga(fuenteId, capUrl)
        archivo.delete()
        synchronized(this) {
            nombresDescargados().remove(archivo.name)
            indice().remove(archivo.name)
        }
        guardarIndice()
        version++
    }

    /** Borra todas las descargas de un manga (las que el índice sabe que son suyas). */
    fun borrarDescargasDe(fuenteId: Long, urlManga: String): Int {
        val k = "$fuenteId|$urlManga"
        val nombres = synchronized(this) { indice().filterValues { it == k }.keys.toList() }
        nombres.forEach { n ->
            File(carpetaDescargas, n).delete()
            synchronized(this) { nombresDescargados().remove(n); indice().remove(n) }
        }
        guardarIndice()
        version++
        return nombres.size
    }

    // Versiones anteriores guardaban cada capítulo descargado como un cómic suelto de la biblioteca
    // (el "duplicado"). Aquí se convierten en descargas del capítulo y se quitan de la biblioteca.
    private fun migrarDescargasViejas() {
        val texto = prefs.getString("descargas", null) ?: return
        val viejas = JSONObject(texto)
        for (clave in viejas.keys().asSequence().toList()) {
            val id = viejas.optString(clave)
            val origen = archivoDe(id)
            val sep = clave.indexOf('|')
            val fuente = clave.substring(0, sep.coerceAtLeast(0)).toLongOrNull()
            if (id.isNotEmpty() && origen.exists() && sep > 0 && fuente != null) {
                guardarDescarga(origen, fuente, clave.substring(sep + 1))
            }
            if (id.isNotEmpty()) eliminar(id)
        }
        prefs.edit().remove("descargas").apply()
    }

    @Synchronized
    fun guardarProgreso(id: String, pagina: Int) {
        ultimoAbierto = id
        // En incógnito se guarda la página, pero no cuenta como "leído hace poco" (no sale en el historial).
        modificar { l ->
            l.map {
                if (it.id == id) it.copy(ultimaPagina = pagina, leido = if (incognito) it.leido else System.currentTimeMillis())
                else it
            }
        }
    }

    @Synchronized
    fun eliminar(id: String) {
        cargar().firstOrNull { it.id == id && it.enLinea }?.let { olvidarCapitulos(it.fuenteId, it.urlManga.orEmpty()) }
        archivoDe(id).delete()
        portadaDe(id).delete()
        Portadas.olvidar(id)
        prefs.edit().remove("modo_$id").remove("visor_$id").remove("seg_$id").apply()
        marcarSincronizacion(id, false)
        if (ultimoAbierto == id) ultimoAbierto = null
        guardarAsignaciones(asignaciones() - id)
        modificar { l -> l.filter { it.id != id } }
    }

    init {
        runCatching { migrarDescargasViejas() }
        runCatching { migrarConocidos() }
        // Los cómics de antes no tenían fecha de añadido: se usa el orden en que estaban.
        runCatching {
            val l = cargar()
            if (l.any { it.anadido == 0L }) {
                val base = System.currentTimeMillis() - l.size * 1000L
                guardar(l.mapIndexed { i, c -> if (c.anadido == 0L) c.copy(anadido = base + i * 1000L) else c })
            }
        }
    }
}
