package com.example.lector.ui.mochy

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Personalización → Estilo: qué piezas de diseño usa Shiori. Por defecto, todo como en Mochy Music; las piezas que
 * vinieron de BitChord quedan como alternativa. Se lee barato (estado de Compose en un objeto) y se guarda en las
 * mismas preferencias «aspecto» que el resto de Personalización. [iniciar] lo llama MovimientoApp.iniciar.
 */
object EstiloApp {
    enum class Esquinas(val nombre: String) { MOCHY("Mochy"), CONTINUAS("Continuas") }
    enum class MenuMantener(val nombre: String) { MOCHY("Mochy"), ELEVADO("Elevado"), DIRECTO("Directo") }
    enum class VentanaActualizacion(val nombre: String) { MOCHY("Mochy"), BITCHORD("BitChord") }
    enum class LadoDescargas(val nombre: String) { IZQUIERDA("Izquierda"), CENTRO("Centro"), DERECHA("Derecha") }

    private var prefs: android.content.SharedPreferences? = null

    var esquinas by mutableStateOf(Esquinas.MOCHY); private set
    var menuMantener by mutableStateOf(MenuMantener.MOCHY); private set
    var ventanaActualizacion by mutableStateOf(VentanaActualizacion.MOCHY); private set
    var verDescargas by mutableStateOf(true); private set
    var ladoDescargas by mutableStateOf(LadoDescargas.IZQUIERDA); private set

    private inline fun <reified E : Enum<E>> leer(p: android.content.SharedPreferences, clave: String, def: E): E =
        p.getString(clave, null)?.let { n -> enumValues<E>().firstOrNull { it.name == n } } ?: def

    fun iniciar(ctx: android.content.Context) {
        val p = ctx.getSharedPreferences("aspecto", android.content.Context.MODE_PRIVATE).also { prefs = it }
        esquinas = leer(p, "estilo_esquinas", Esquinas.MOCHY)
        menuMantener = leer(p, "estilo_menu", MenuMantener.MOCHY)
        ventanaActualizacion = leer(p, "estilo_actualizacion", VentanaActualizacion.MOCHY)
        verDescargas = p.getBoolean("estilo_descargas", true)
        ladoDescargas = leer(p, "estilo_descargas_lado", LadoDescargas.IZQUIERDA)
    }

    private fun guardar(clave: String, v: String) { prefs?.edit()?.putString(clave, v)?.apply() }
    fun elegir(v: Esquinas) { esquinas = v; guardar("estilo_esquinas", v.name) }
    fun elegir(v: MenuMantener) { menuMantener = v; guardar("estilo_menu", v.name) }
    fun elegir(v: VentanaActualizacion) { ventanaActualizacion = v; guardar("estilo_actualizacion", v.name) }
    fun elegir(v: LadoDescargas) { ladoDescargas = v; guardar("estilo_descargas_lado", v.name) }
    fun elegirVerDescargas(v: Boolean) { verDescargas = v; prefs?.edit()?.putBoolean("estilo_descargas", v)?.apply() }

    /** Mantener pulsado hace la acción directa (añadir o quitar de la biblioteca) en vez de abrir un menú. */
    val accionDirecta: Boolean get() = menuMantener == MenuMantener.DIRECTO
}
