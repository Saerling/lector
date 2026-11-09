package com.example.lector.ui.migrar

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.example.lector.ext.ExtInstalada
import com.example.lector.ext.ExtManager
import com.example.lector.ext.Fase
import com.example.lector.ui.Avisos
import com.example.lector.ui.IconoExt
import com.example.lector.ui.IndicadorCarga
import com.example.lector.ui.LText
import com.example.lector.ui.LectorColors
import com.example.lector.ui.LectorType
import com.example.lector.ui.PillButton
import com.example.lector.ui.SuaveShape

/** «Todas las fuentes»: buscar en todas las instaladas sin mirar los idiomas activados. Se recuerda. */
object OpcionesMigrar {
    private var iniciado = false
    var todasFuentes by mutableStateOf(false); private set

    fun iniciar(ctx: Context) {
        if (iniciado) return
        iniciado = true
        todasFuentes = ctx.getSharedPreferences("lector", Context.MODE_PRIVATE).getBoolean("migrar_todas_fuentes", false)
    }

    fun alternar(ctx: Context) {
        todasFuentes = !todasFuentes
        ctx.getSharedPreferences("lector", Context.MODE_PRIVATE).edit().putBoolean("migrar_todas_fuentes", todasFuentes).apply()
    }
}

/** Extensiones instaladas cuyas fuentes no se pueden usar (sin confianza o que no cargan). */
fun extensionesPendientes(ext: ExtManager): List<ExtInstalada> = ext.instaladas.filter { !it.confiable || it.errorCarga != null }

/**
 * En qué extensión estaba el manga ([fuenteId]): la instalada (y si hay que confiar en ella) o la que ofrecen tus
 * listados, con su botón para instalarla.
 */
@Composable
fun TarjetaExtension(ext: ExtManager, fuenteId: Long, modifier: Modifier = Modifier) {
    val (inst, disp) = ext.extensionDeFuente(fuenteId)
    val pkg = inst?.pkg ?: disp?.pkg
    val nombreFuente = disp?.fuentes?.firstOrNull { it.id == fuenteId }?.nombre
    val (titulo, estado) = when {
        inst != null && !inst.confiable -> inst.nombre to "Instalada, pero no es de confianza: sus fuentes no se usan."
        inst?.errorCarga != null -> inst.nombre to "Instalada, pero no carga: ${inst.errorCarga}"
        inst != null -> inst.nombre to "Instalada (v${inst.versionName})" + (if (ext.fuentes.none { it.id == fuenteId }) " · la fuente ya no está en ella" else "")
        disp != null -> disp.nombre to "No instalada · está en tu listado @${disp.repoSigla} (v${disp.versionName})"
        else -> "Extensión desconocida" to "Ninguno de tus listados ofrece esta fuente (id $fuenteId). Añade el listado que la tenía."
    }
    Row(
        modifier.fillMaxWidth().clip(SuaveShape(16.dp)).background(LectorColors.Card).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (pkg != null) IconoExt(pkg, disp?.iconoUrl, Modifier.size(40.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            LText(titulo + (nombreFuente?.takeIf { it != titulo }?.let { " · $it" } ?: ""), LectorType.Body, maxLines = 2)
            LText(estado, LectorType.Label, Modifier.padding(top = 2.dp), color = LectorColors.Muted, maxLines = 3)
        }
        Spacer(Modifier.width(8.dp))
        val pr = pkg?.let { ext.progreso[it] }
        when {
            pr != null && pr.fase != Fase.ERROR -> IndicadorCarga(Modifier.size(22.dp), strokeWidth = 2.dp)
            inst != null && !inst.confiable -> PillButton("Confiar", Icons.Rounded.Check, { ext.confiar(inst) })
            inst?.errorCarga != null && disp != null -> PillButton("Reinstalar", Icons.Rounded.Refresh, { ext.instalar(disp) })
            inst == null && disp != null -> PillButton("Instalar", Icons.Rounded.Add, { ext.instalar(disp) })
        }
    }
}

/** Fila atenuada de una extensión instalada que no se puede usar todavía, con «Confiar». */
@Composable
fun FilaExtPendiente(ext: ExtManager, e: ExtInstalada) {
    val fuentes = ext.disponibles.firstOrNull { it.pkg == e.pkg }?.fuentes.orEmpty()
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(SuaveShape(16.dp)).background(LectorColors.Card.copy(alpha = 0.35f))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconoExt(e.pkg, null, Modifier.size(40.dp).alpha(0.5f))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f).alpha(0.6f)) {
            LText(e.nombre, LectorType.Body, maxLines = 1)
            val por = if (!e.confiable) "Sin confianza" else "No carga: ${e.errorCarga}"
            val lista = if (fuentes.isEmpty()) "" else " · " + fuentes.take(4).joinToString { "${it.nombre} (${it.lang})" } + (if (fuentes.size > 4) " y ${fuentes.size - 4} más" else "")
            LText(por + lista, LectorType.Label, color = LectorColors.Muted, maxLines = 2)
        }
        if (!e.confiable) TextButton(onClick = { ext.confiar(e); Avisos.mostrar("${e.nombre}: de confianza") }) { Text("Confiar") }
    }
}
