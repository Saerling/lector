package com.example.lector.ui.migrar

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.lector.ext.ExtManager
import com.example.lector.ui.BotonAtras
import com.example.lector.ui.BotonRedondoTema
import com.example.lector.ui.DialogoAlerta
import com.example.lector.ui.ImagenRemota
import com.example.lector.ui.LText
import com.example.lector.ui.LectorColors
import com.example.lector.ui.LectorType
import com.example.lector.ui.SuaveShape
import com.example.lector.ui.pressable
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.DateFormat
import java.util.Calendar
import java.util.Date

/** Un movimiento de un manga: migración a otra fuente, unión de dos fuentes o cambio de categoría. */
data class Migracion(
    val tipo: String, // "migrar" · "unir" · "categoria"
    val titulo: String,
    val deId: Long, val deNombre: String, val deUrl: String?,
    val aId: Long, val aNombre: String, val aUrl: String?,
    val portada: String?,
    val leidos: Int,
    val fecha: Long = System.currentTimeMillis(),
)

/** Registro de las últimas migraciones (como mucho [MAX]), en un JSON dentro de la carpeta de la app. */
object HistorialMigraciones {
    private const val MAX = 500
    private var archivo: File? = null
    var lista by mutableStateOf<List<Migracion>>(emptyList()); private set

    fun iniciar(ctx: Context) {
        if (archivo != null) return
        val f = File(ctx.applicationContext.filesDir, "historial_migraciones.json")
        archivo = f
        lista = runCatching {
            val a = JSONArray(f.readText())
            (0 until a.length()).map { a.getJSONObject(it).aMigracion() }
        }.getOrDefault(emptyList())
    }

    fun registrar(m: Migracion) {
        lista = (listOf(m) + lista).take(MAX)
        guardar()
    }

    /** Nombre legible de una fuente aunque ya no esté instalada (del listado si lo ofrece). */
    fun nombreFuente(ext: ExtManager, id: Long): String =
        ext.fuentes.firstOrNull { it.id == id }?.let { "${it.nombre} (${it.lang.uppercase()})" }
            ?: ext.extensionDeFuente(id).second?.fuentes?.firstOrNull { it.id == id }?.let { "${it.nombre} (${it.lang.uppercase()})" }
            ?: "Fuente $id"

    fun borrar() { lista = emptyList(); guardar() }

    private fun guardar() {
        val f = archivo ?: return
        val a = JSONArray()
        lista.forEach { m ->
            a.put(JSONObject().apply {
                put("tipo", m.tipo); put("titulo", m.titulo)
                put("deId", m.deId); put("deNombre", m.deNombre); put("deUrl", m.deUrl ?: "")
                put("aId", m.aId); put("aNombre", m.aNombre); put("aUrl", m.aUrl ?: "")
                put("portada", m.portada ?: ""); put("leidos", m.leidos); put("fecha", m.fecha)
            })
        }
        runCatching { f.writeText(a.toString()) }
    }

    private fun JSONObject.aMigracion() = Migracion(
        optString("tipo", "migrar"), optString("titulo"),
        optLong("deId"), optString("deNombre"), optString("deUrl").ifBlank { null },
        optLong("aId"), optString("aNombre"), optString("aUrl").ifBlank { null },
        optString("portada").ifBlank { null }, optInt("leidos"), optLong("fecha"),
    )
}

/** «Hoy», «Ayer» o la fecha. */
internal fun diaDe(t: Long): String {
    val c = Calendar.getInstance().apply { timeInMillis = t }
    val hoy = Calendar.getInstance()
    fun mismoDia(a: Calendar, b: Calendar) = a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
    if (mismoDia(c, hoy)) return "Hoy"
    hoy.add(Calendar.DAY_OF_YEAR, -1)
    if (mismoDia(c, hoy)) return "Ayer"
    return DateFormat.getDateInstance(DateFormat.LONG).format(Date(t))
}

internal fun textoMigracion(m: Migracion): String {
    val hora = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(m.fecha))
    val que = when (m.tipo) {
        "unir" -> "Unido: ${m.deNombre} + ${m.aNombre}"
        "categoria" -> "Categoría: ${m.deNombre} → ${m.aNombre}"
        else -> "${m.deNombre} → ${m.aNombre}"
    }
    return if (m.tipo == "migrar") "$que · ${m.leidos} leídos · $hora" else "$que · $hora"
}

/** Fila compacta de una migración (también la usa la pestaña Explorar → Migrar). */
@Composable
fun FilaMigracion(m: Migracion, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().pressable(onClick).padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ImagenRemota(m.portada, Modifier.width(40.dp).aspectRatio(2f / 3f).clip(SuaveShape(8.dp)), fuenteId = m.aId)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            LText(m.titulo, LectorType.Body, maxLines = 1)
            LText(textoMigracion(m), LectorType.Label, Modifier.padding(top = 2.dp), color = LectorColors.Muted, maxLines = 2)
        }
        Icon(if (m.tipo == "migrar") Icons.Rounded.ArrowForward else Icons.Rounded.Refresh, null, tint = LectorColors.Muted, modifier = Modifier.size(18.dp))
    }
}

/** «Migraciones recientes»: lo movido agrupado por día; al tocar se abre la ficha nueva. */
@Composable
fun MigracionesRecientesScreen(onAbrir: (Migracion) -> Unit, onBack: () -> Unit) {
    val ctx = LocalContext.current
    remember { HistorialMigraciones.iniciar(ctx) }
    var confirmar by remember { mutableStateOf(false) }
    val lista = HistorialMigraciones.lista
    val grupos = remember(lista) { lista.groupBy { diaDe(it.fecha) }.toList() }
    Column(Modifier.fillMaxSize().background(LectorColors.Bg).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            BotonAtras(onBack)
            Spacer(Modifier.width(14.dp))
            LText("Migraciones recientes", LectorType.Section, Modifier.weight(1f), maxLines = 1)
            if (lista.isNotEmpty()) BotonRedondoTema(Icons.Rounded.Delete, "Borrar historial", { confirmar = true })
        }
        if (lista.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(40.dp), Alignment.Center) {
                LText("Aún no has migrado nada. Lo que muevas de una fuente a otra aparecerá aquí.", LectorType.Body, color = LectorColors.Muted)
            }
        } else LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 60.dp)) {
            grupos.forEach { (dia, items) ->
                item(key = "d_$dia") {
                    LText(dia, LectorType.Label, Modifier.padding(start = 20.dp, top = 16.dp, bottom = 6.dp), color = LectorColors.Muted)
                }
                items(items, key = { "${it.fecha}_${it.aId}_${it.aUrl}" }) { m ->
                    FilaMigracion(m) { if (m.aUrl != null) onAbrir(m) }
                }
            }
        }
    }
    if (confirmar) DialogoAlerta(
        onDismissRequest = { confirmar = false },
        title = { Text("¿Borrar el historial?") },
        text = { Text("Se olvidan las ${lista.size} migraciones guardadas. Tus mangas no cambian.") },
        confirmButton = { TextButton(onClick = { HistorialMigraciones.borrar(); confirmar = false }) { Text("Borrar") } },
        dismissButton = { TextButton(onClick = { confirmar = false }) { Text("Cancelar") } },
    )
    BackHandler(onBack = onBack)
}
