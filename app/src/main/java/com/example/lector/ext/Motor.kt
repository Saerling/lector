package com.example.lector.ext

/**
 * COSTURA con el motor de fuentes (paso siguiente).
 *
 * Hoy NO hay motor: la app instala, confía y lista extensiones, pero todavía no ejecuta su código.
 * Quien conecte el motor (cargar el APK de la extensión con el source-api de Komikku/Mihon)
 * solo tiene que crear una clase que implemente esta interfaz y asignarla a [Motor.actual].
 * Con eso se activan solas las pestañas Novedades y la pantalla de cada fuente.
 */
data class MangaFuente(val url: String, val titulo: String, val portadaUrl: String?)

data class PaginaMangas(val mangas: List<MangaFuente>, val haySiguiente: Boolean)

/** Ficha de un manga: datos + capítulos (en el orden de la fuente, normalmente el más nuevo primero). */
data class DetalleManga(
    val titulo: String,
    val autor: String?,
    val artista: String?,
    val descripcion: String?,
    val generos: List<String>,
    val estado: Int,
    val portadaUrl: String?,
)

data class CapituloFuente(
    val url: String,
    val nombre: String,
    val fecha: Long,
    val scanlator: String?,
    val numero: Float,
)

data class FichaManga(val detalle: DetalleManga, val capitulos: List<CapituloFuente>)

interface MotorFuentes {
    suspend fun populares(fuente: FuenteUI, pagina: Int): PaginaMangas
    suspend fun recientes(fuente: FuenteUI, pagina: Int): PaginaMangas
    suspend fun buscar(fuente: FuenteUI, consulta: String, pagina: Int): PaginaMangas

    /** Datos del manga + lista de capítulos. */
    suspend fun ficha(fuente: FuenteUI, manga: MangaFuente): FichaManga

    /** Cuántas páginas tiene un capítulo (también las deja listas para pedirlas con [imagenPagina]). */
    suspend fun cantidadPaginas(fuente: FuenteUI, capitulo: CapituloFuente): Int

    /** Bytes de la imagen de una página, pedidos con el cliente de la propia fuente. */
    suspend fun imagenPagina(fuente: FuenteUI, capitulo: CapituloFuente, indice: Int): ByteArray

    /** Descarga todas las páginas del capítulo y las empaqueta como .cbz en [destino]. */
    suspend fun descargarCbz(
        fuente: FuenteUI, capitulo: CapituloFuente, destino: java.io.File, progreso: (hechas: Int, total: Int) -> Unit,
    )

    /** Dirección web de un manga en el sitio de la fuente (null si no se sabe). */
    fun urlManga(fuente: FuenteUI, manga: MangaFuente): String? = null

    /** Cabeceras HTTP (Referer, User-Agent...) para cargar las portadas de esta fuente. */
    fun cabeceras(fuente: FuenteUI): Map<String, String> = emptyMap()
}

object Motor {
    @Volatile
    var actual: MotorFuentes? = null
}
