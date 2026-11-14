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
    // Portada panorámica de Komikku: las portadas apaisadas se muestran a lo ancho en Historial y Actualizaciones.
    var panoramica by mutableStateOf(false); private set
    // Thinking orbs (Jakub Antalik) como indicador de carga; apagado = el indicador clásico de radios.
    var orbes by mutableStateOf(true); private set
    var orbesAcento by mutableStateOf(false); private set
    enum class VariarOrbes(val nombre: String) { CAMBIANDO("Ir cambiando"), AZAR("Al azar"), TAREA("Según la tarea") }
    // Por defecto van pasando por los nueve tipos; «Al azar» elige uno cada vez; «Según la tarea», el de cada espera.
    var variarOrbes by mutableStateOf(VariarOrbes.CAMBIANDO); private set
    fun elegir(v: VariarOrbes) { variarOrbes = v; guardar("estilo_orbes_variar", v.name) }
    fun elegirOrbes(v: Boolean) { orbes = v; prefs?.edit()?.putBoolean("estilo_orbes", v)?.apply() }
    fun elegirOrbesAcento(v: Boolean) { orbesAcento = v; prefs?.edit()?.putBoolean("estilo_orbes_acento", v)?.apply() }
    fun elegirPanoramica(v: Boolean) { panoramica = v; prefs?.edit()?.putBoolean("estilo_panoramica", v)?.apply() }

    /** Portadas que resultaron apaisadas al cargarse (alto/ancho <= 0,75, como RatioSwitchToPanorama). */
    private val anchas = androidx.compose.runtime.mutableStateMapOf<String, Boolean>()
    fun anotarPortada(clave: String?, ancho: Int, alto: Int) {
        if (clave.isNullOrBlank() || ancho <= 0) return
        val ancha = alto.toFloat() / ancho <= 0.75f
        if (anchas[clave] != ancha && (ancha || clave in anchas)) anchas[clave] = ancha
    }
    /** Ancho de la portada de una fila: 1,6 veces más si la opción está activa y alguna de las claves es apaisada. */
    fun factorAncho(vararg claves: String?): Float = if (panoramica && claves.any { it != null && anchas[it] == true }) 1.6f else 1f

    private inline fun <reified E : Enum<E>> leer(p: android.content.SharedPreferences, clave: String, def: E): E =
        p.getString(clave, null)?.let { n -> enumValues<E>().firstOrNull { it.name == n } } ?: def

    fun iniciar(ctx: android.content.Context) {
        val p = ctx.getSharedPreferences("aspecto", android.content.Context.MODE_PRIVATE).also { prefs = it }
        esquinas = leer(p, "estilo_esquinas", Esquinas.MOCHY)
        menuMantener = leer(p, "estilo_menu", MenuMantener.MOCHY)
        ventanaActualizacion = leer(p, "estilo_actualizacion", VentanaActualizacion.MOCHY)
        verDescargas = p.getBoolean("estilo_descargas", true)
        panoramica = p.getBoolean("estilo_panoramica", false)
        orbes = p.getBoolean("estilo_orbes", true)
        orbesAcento = p.getBoolean("estilo_orbes_acento", false)
        variarOrbes = leer(p, "estilo_orbes_variar", VariarOrbes.CAMBIANDO)
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
