// Copiado de Komikku (https://github.com/komikku-app/komikku, Apache-2.0): app/src/main/java/eu/kanade/presentation/updates/UpdatesFilterDialog.kt
// y app/src/main/java/eu/kanade/tachiyomi/ui/updates/UpdatesScreenModel.kt (applyFilters), adaptado a Shiori.
package com.example.lector.ui.actualizaciones

import android.content.Context
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.lector.ActualizacionCap
import com.example.lector.Almacen
import com.example.lector.Tri
import com.example.lector.ext.ExtManager
import com.example.lector.ui.FilaSwitch
import com.example.lector.ui.FilaTri
import com.example.lector.ui.HojaLector
import com.example.lector.ui.LText
import com.example.lector.ui.LectorColors
import com.example.lector.ui.LectorType

/**
 * Filtros de la pestaña Actualizaciones (como UpdatesPreferences de Komikku): descargados, no leídos, empezados y
 * marcados en tres estados, más fuentes ocultas y agrupar por manga. Se guardan en preferencias.
 */
object FiltrosActualizaciones {
    private var prefs: android.content.SharedPreferences? = null

    var descargados by mutableStateOf(Tri.IGNORAR); private set
    var noLeidos by mutableStateOf(Tri.IGNORAR); private set
    var empezados by mutableStateOf(Tri.IGNORAR); private set
    var marcados by mutableStateOf(Tri.IGNORAR); private set
    var fuentesOcultas by mutableStateOf(emptySet<Long>()); private set
    var agrupar by mutableStateOf(true); private set

    fun iniciar(ctx: Context) {
        if (prefs != null) return
        val p = ctx.applicationContext.getSharedPreferences("actualizaciones_ui", Context.MODE_PRIVATE)
        prefs = p
        fun tri(k: String) = runCatching { Tri.valueOf(p.getString(k, null) ?: "IGNORAR") }.getOrDefault(Tri.IGNORAR)
        descargados = tri("descargados"); noLeidos = tri("no_leidos"); empezados = tri("empezados"); marcados = tri("marcados")
        fuentesOcultas = p.getStringSet("fuentes_ocultas", emptySet()).orEmpty().mapNotNull { it.toLongOrNull() }.toSet()
        agrupar = p.getBoolean("agrupar", true)
    }

    val hayFiltros: Boolean
        get() = descargados != Tri.IGNORAR || noLeidos != Tri.IGNORAR || empezados != Tri.IGNORAR ||
            marcados != Tri.IGNORAR || fuentesOcultas.isNotEmpty()

    fun fijarDescargados(t: Tri) { descargados = t; prefs?.edit()?.putString("descargados", t.name)?.apply() }
    fun fijarNoLeidos(t: Tri) { noLeidos = t; prefs?.edit()?.putString("no_leidos", t.name)?.apply() }
    fun fijarEmpezados(t: Tri) { empezados = t; prefs?.edit()?.putString("empezados", t.name)?.apply() }
    fun fijarMarcados(t: Tri) { marcados = t; prefs?.edit()?.putString("marcados", t.name)?.apply() }
    fun fijarAgrupar(v: Boolean) { agrupar = v; prefs?.edit()?.putBoolean("agrupar", v)?.apply() }
    fun alternarFuente(id: Long) {
        fuentesOcultas = if (id in fuentesOcultas) fuentesOcultas - id else fuentesOcultas + id
        prefs?.edit()?.putStringSet("fuentes_ocultas", fuentesOcultas.map { it.toString() }.toSet())?.apply()
    }

    fun limpiar() {
        fijarDescargados(Tri.IGNORAR); fijarNoLeidos(Tri.IGNORAR); fijarEmpezados(Tri.IGNORAR); fijarMarcados(Tri.IGNORAR)
        fuentesOcultas = emptySet(); prefs?.edit()?.remove("fuentes_ocultas")?.apply()
    }

    /** Como `applyFilters` de UpdatesScreenModel: cada filtro deja pasar según su estado de tres valores. */
    fun aplicar(lista: List<ActualizacionCap>, almacen: Almacen, ext: ExtManager): List<ActualizacionCap> {
        if (!hayFiltros) return lista
        return lista.filter { a ->
            a.fuenteId !in fuentesOcultas &&
                noLeidos.deja(!ext.esLeido(a.fuenteId, a.capUrl)) &&
                marcados.deja(ext.esMarcado(a.fuenteId, a.capUrl)) &&
                empezados.deja(!ext.esLeido(a.fuenteId, a.capUrl) && ext.paginaDe(a.fuenteId, a.capUrl) > 0) &&
                descargados.deja(almacen.estaDescargado(a.fuenteId, a.urlManga, a.capUrl))
        }
    }
}

/** Hoja de filtros (UpdatesFilterDialog de Komikku) con el aspecto de Shiori. */
@Composable
fun HojaFiltroActualizaciones(lista: List<ActualizacionCap>, ext: ExtManager, onCerrar: () -> Unit) {
    val f = FiltrosActualizaciones
    HojaLector(onCerrar) {
        LText("Filtrar", LectorType.Section, Modifier.padding(start = 24.dp, top = 4.dp, bottom = 8.dp))
        FilaTri("Descargados", f.descargados, f::fijarDescargados)
        FilaTri("No leídos", f.noLeidos, f::fijarNoLeidos)
        FilaTri("Empezados", f.empezados, f::fijarEmpezados)
        FilaTri("Marcados", f.marcados, f::fijarMarcados)
        Spacer(Modifier.height(8.dp))
        FilaSwitch("Agrupar por manga", "Junta los capítulos del mismo manga y día", f.agrupar, f::fijarAgrupar)
        val fuentes = lista.map { it.fuenteId }.distinct()
        if (fuentes.size > 1 || f.fuentesOcultas.isNotEmpty()) {
            LText("Fuentes", LectorType.Label, Modifier.padding(start = 24.dp, top = 14.dp, bottom = 4.dp), color = LectorColors.Muted)
            (fuentes + f.fuentesOcultas).distinct().forEach { id ->
                val nombre = ext.fuentes.firstOrNull { it.id == id }?.nombre ?: "Fuente $id"
                FilaSwitch(nombre, null, id !in f.fuentesOcultas) { f.alternarFuente(id) }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

/** Icono «filtrar» (FilterList de Material, que no viene en material-icons-core). */
val IconoFiltrar: androidx.compose.ui.graphics.vector.ImageVector by lazy {
    androidx.compose.ui.graphics.vector.ImageVector.Builder(
        name = "FilterList", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f,
    ).addPath(
        androidx.compose.ui.graphics.vector.addPathNodes("M10 18h4v-2h-4v2zM3 6v2h18V6H3zm3 7h12v-2H6v2z"),
        fill = androidx.compose.ui.graphics.SolidColor(androidx.compose.ui.graphics.Color.Black),
    ).build()
}
