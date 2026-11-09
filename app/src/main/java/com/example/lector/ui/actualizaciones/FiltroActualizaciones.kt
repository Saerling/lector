// Copiado de Komikku (https://github.com/komikku-app/komikku, Apache-2.0): app/src/main/java/eu/kanade/presentation/updates/UpdatesFilterDialog.kt
// y app/src/main/java/eu/kanade/tachiyomi/ui/updates/UpdatesScreenModel.kt (applyFilters), adaptado a Shiori.
package com.example.lector.ui.actualizaciones

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.lector.ActualizacionCap
import com.example.lector.Almacen
import com.example.lector.Tri
import com.example.lector.ext.ExtManager
import com.example.lector.ui.HojaLector
import com.example.lector.ui.Interruptor
import com.example.lector.ui.Tacto
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
                noLeidos.deja(!ext.esLeido(a.fuenteId, a.urlManga, a.capUrl)) &&
                marcados.deja(ext.esMarcado(a.fuenteId, a.urlManga, a.capUrl)) &&
                empezados.deja(!ext.esLeido(a.fuenteId, a.urlManga, a.capUrl) && ext.paginaDe(a.fuenteId, a.urlManga, a.capUrl) > 0) &&
                descargados.deja(almacen.estaDescargado(a.fuenteId, a.urlManga, a.capUrl))
        }
    }
}

/** Hoja de filtros (UpdatesFilterDialog de Komikku) con el aspecto de Mochy: secciones con título y tarjetas agrupadas. */
@Composable
fun HojaFiltroActualizaciones(lista: List<ActualizacionCap>, ext: ExtManager, onCerrar: () -> Unit) {
    val f = FiltrosActualizaciones
    val fuentes = remember(lista, f.fuentesOcultas) { (lista.map { it.fuenteId }.distinct() + f.fuentesOcultas).distinct() }
    val nombres = remember(fuentes, ext.fuentes) {
        fuentes.associateWith { id -> ext.fuentes.firstOrNull { it.id == id }?.nombre ?: "Fuente $id" }
    }
    HojaLector(onCerrar) {
        // La hoja limita el alto al 85 % de la pantalla: el contenido se desplaza dentro (y el arrastre de la hoja
        // solo actúa cuando la lista ya está arriba, gracias al nestedScroll de ModalBottomSheet).
        Column(
            Modifier.fillMaxWidth().weight(1f, fill = false).verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp).padding(bottom = 24.dp),
        ) {
            Row(Modifier.fillMaxWidth().padding(start = 8.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    LText("Filtrar", LectorType.Hero)
                    LText(
                        if (f.hayFiltros) "Hay filtros activos" else "Se muestran todos los capítulos",
                        LectorType.Body, color = LectorColors.Muted,
                    )
                }
                AnimatedVisibility(f.hayFiltros, enter = fadeIn() + scaleIn(), exit = fadeOut() + scaleOut()) {
                    Box(
                        Modifier.clip(CircleShape).background(LectorColors.CardHigh).clickable { f.limpiar() }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    ) { LText("Restablecer", LectorType.Label.copy(fontWeight = FontWeight.SemiBold)) }
                }
            }

            TituloSeccion("Capítulos")
            Tarjeta {
                FilaFiltroTri("Descargados", f.descargados, f::fijarDescargados)
                Separador()
                FilaFiltroTri("No leídos", f.noLeidos, f::fijarNoLeidos)
                Separador()
                FilaFiltroTri("Empezados", f.empezados, f::fijarEmpezados)
                Separador()
                FilaFiltroTri("Marcados", f.marcados, f::fijarMarcados)
            }

            TituloSeccion("Vista")
            Tarjeta {
                FilaInterruptor("Agrupar por manga", "Junta los capítulos del mismo manga y día", f.agrupar, f::fijarAgrupar)
            }

            if (fuentes.size > 1 || f.fuentesOcultas.isNotEmpty()) {
                TituloSeccion("Fuentes")
                Tarjeta {
                    fuentes.forEachIndexed { i, id ->
                        if (i > 0) Separador()
                        FilaInterruptor(nombres[id].orEmpty(), null, id !in f.fuentesOcultas) { f.alternarFuente(id) }
                    }
                }
            }
        }
    }
}

@Composable
private fun TituloSeccion(texto: String) {
    LText(
        texto.uppercase(), LectorType.Label.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 1.2.sp),
        Modifier.padding(start = 12.dp, top = 18.dp, bottom = 8.dp), color = LectorColors.Muted,
    )
}

@Composable
private fun Tarjeta(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(LectorColors.Card).padding(vertical = 4.dp), content = content)
}

@Composable
private fun Separador() {
    Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(1.dp).background(LectorColors.Linea))
}

@Composable
private fun FilaInterruptor(titulo: String, detalle: String?, activado: Boolean, onCambio: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onCambio(!activado) }.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            LText(titulo, LectorType.Body.copy(fontSize = 16.sp, fontWeight = FontWeight.Medium), maxLines = 1)
            if (detalle != null) LText(detalle, LectorType.Body.copy(fontSize = 13.sp), color = LectorColors.Muted)
        }
        Interruptor(activado, onCambio)
    }
}

/** Fila con un selector segmentado de tres estados (Todos · Solo · Sin), más claro que la casilla de tres estados. */
@Composable
private fun FilaFiltroTri(titulo: String, estado: Tri, onCambio: (Tri) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LText(titulo, LectorType.Body.copy(fontSize = 16.sp, fontWeight = FontWeight.Medium), Modifier.weight(1f), maxLines = 1)
        SegmentoTri(estado, onCambio)
    }
}

@Composable
private fun SegmentoTri(estado: Tri, onCambio: (Tri) -> Unit) {
    val opciones = listOf(Tri.IGNORAR to "Todos", Tri.INCLUIR to "Solo", Tri.EXCLUIR to "Sin")
    val acento = MaterialTheme.colorScheme.primary
    Row(Modifier.clip(CircleShape).background(LectorColors.CardHigh).padding(3.dp)) {
        opciones.forEach { (valor, etiqueta) ->
            val elegido = valor == estado
            val fondo by animateColorAsState(
                when {
                    !elegido -> Color.Transparent
                    valor == Tri.EXCLUIR -> LectorColors.Error
                    valor == Tri.INCLUIR -> acento
                    else -> LectorColors.Card
                },
                spring(stiffness = Spring.StiffnessMediumLow), label = "fondoTri",
            )
            val texto by animateColorAsState(
                when {
                    !elegido -> LectorColors.Muted
                    valor == Tri.EXCLUIR -> Color.White
                    valor == Tri.INCLUIR -> com.example.lector.textoSobre(acento)
                    else -> LectorColors.Text
                },
                label = "textoTri",
            )
            Box(
                Modifier.clip(CircleShape).background(fondo).clickable { if (!elegido) { Tacto.alternar(true); onCambio(valor) } }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) { LText(etiqueta, LectorType.Label.copy(fontWeight = FontWeight.SemiBold), color = texto) }
        }
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
