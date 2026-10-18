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

interface MotorFuentes {
    suspend fun populares(fuente: FuenteUI, pagina: Int): PaginaMangas
    suspend fun recientes(fuente: FuenteUI, pagina: Int): PaginaMangas
    suspend fun buscar(fuente: FuenteUI, consulta: String, pagina: Int): PaginaMangas

    /** Cabeceras HTTP (Referer, User-Agent...) para cargar las portadas de esta fuente. */
    fun cabeceras(fuente: FuenteUI): Map<String, String> = emptyMap()
}

object Motor {
    @Volatile
    var actual: MotorFuentes? = null
}
