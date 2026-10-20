package com.example.lector.ui

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.MultiSelectListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceGroup
import androidx.preference.PreferenceManager
import androidx.preference.TwoStatePreference
import com.example.lector.ext.ExtManager
import com.example.lector.FilaOpcion
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.preferenceKey

/**
 * Ajustes propios de una fuente (dominio, inicio de sesión, calidad de imagen…).
 *
 * Komikku (SourcePreferencesScreen) los muestra con un PreferenceFragmentCompat. Aquí la extensión rellena el mismo
 * PreferenceScreen de androidx.preference (setupPreferenceScreen), guardado en el mismo archivo "source_<id>",
 * y cada preferencia se dibuja con Compose. Los cambios pasan por callChangeListener, igual que en el original.
 */
@Composable
fun AjustesFuenteScreen(ext: ExtManager, pkg: String, fuente: ConfigurableSource, titulo: String, onBack: () -> Unit) {
    val ctx = LocalContext.current
    var cambios by remember { mutableIntStateOf(0) }
    val resultado = remember(fuente) {
        runCatching {
            val pm = PreferenceManager(ctx)
            pm.sharedPreferencesName = fuente.preferenceKey()
            pm.sharedPreferencesMode = Context.MODE_PRIVATE
            val pantalla = pm.createPreferenceScreen(ctx)
            fuente.setupPreferenceScreen(pantalla)
            pantalla
        }
    }
    // Al salir tras cambiar algo, la fuente se vuelve a crear para que lea los valores nuevos (dominio, etc.).
    val volver = {
        if (cambios > 0) ext.recargarExtension(pkg)
        onBack()
    }
    BackHandler(onBack = volver)
    PaginaAjustes(titulo, volver) {
        val pantalla = resultado.getOrNull()
        if (pantalla == null) {
            LText(
                "No se pudieron cargar los ajustes de esta fuente: ${resultado.exceptionOrNull()?.message ?: "error desconocido"}",
                LectorType.Body, Modifier.padding(24.dp), color = LectorColors.Muted,
            )
            return@PaginaAjustes
        }
        if (pantalla.preferenceCount == 0) {
            LText("Esta fuente no tiene ajustes.", LectorType.Body, Modifier.padding(24.dp), color = LectorColors.Muted)
            return@PaginaAjustes
        }
        // Al cambiar un valor se recompone todo (títulos, resúmenes y dependencias entre ajustes).
        key(cambios) { FilasPreferencias(pantalla) { cambios++ } }
    }
}

@Composable
private fun FilasPreferencias(grupo: PreferenceGroup, alCambiar: () -> Unit) {
    for (i in 0 until grupo.preferenceCount) {
        val p = grupo.getPreference(i)
        if (!p.isVisible) continue
        when (p) {
            // Categorías y subpantallas: se muestran seguidas, con su título.
            is PreferenceGroup -> {
                p.title?.toString()?.takeIf { it.isNotBlank() }?.let { TituloSeccion(it) }
                FilasPreferencias(p, alCambiar)
            }
            is TwoStatePreference -> FilaInterruptorPref(p, alCambiar)
            is EditTextPreference -> FilaTextoPref(p, alCambiar)
            is ListPreference -> FilaListaPref(p, alCambiar)
            is MultiSelectListPreference -> FilaMultiplePref(p, alCambiar)
            else -> FilaPref(p, p.summary?.toString()) {
                p.onPreferenceClickListener?.onPreferenceClick(p)
                alCambiar()
            }
        }
    }
}

@Composable
private fun FilaPref(p: Preference, resumen: String?, alPulsar: () -> Unit) {
    Box(Modifier.alpha(if (p.isEnabled) 1f else 0.4f)) {
        FilaAjuste(null, p.title?.toString().orEmpty(), resumen?.takeIf { it.isNotBlank() }) {
            if (p.isEnabled) alPulsar()
        }
    }
}

@Composable
private fun FilaInterruptorPref(p: TwoStatePreference, alCambiar: () -> Unit) {
    val activa = p.isEnabled
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            LText(
                p.title?.toString().orEmpty(), LectorType.Section.copy(fontSize = 17.sp),
                color = if (activa) LectorColors.Text else LectorColors.Muted,
            )
            val resumen = (if (p.isChecked) p.summaryOn else p.summaryOff) ?: p.summary
            resumen?.toString()?.takeIf { it.isNotBlank() }?.let {
                LText(it, LectorType.Label.copy(fontSize = 13.sp), color = LectorColors.Muted)
            }
        }
        Spacer(Modifier.width(8.dp))
        Switch(
            checked = p.isChecked,
            enabled = activa,
            onCheckedChange = { nuevo ->
                if (p.callChangeListener(nuevo)) p.isChecked = nuevo
                alCambiar()
            },
        )
    }
}

@Composable
private fun FilaTextoPref(p: EditTextPreference, alCambiar: () -> Unit) {
    var abierto by remember { mutableStateOf(false) }
    val titulo = p.title?.toString().orEmpty()
    FilaPref(p, p.summary?.toString()) { abierto = true }
    if (abierto) {
        var texto by remember { mutableStateOf(p.text.orEmpty()) }
        AlertDialog(
            onDismissRequest = { abierto = false },
            title = { Text(p.dialogTitle?.toString()?.takeIf { it.isNotBlank() } ?: titulo) },
            text = {
                Column {
                    p.dialogMessage?.toString()?.takeIf { it.isNotBlank() }?.let { Text(it, Modifier.padding(bottom = 8.dp)) }
                    OutlinedTextField(value = texto, onValueChange = { texto = it }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (p.callChangeListener(texto)) p.text = texto
                    abierto = false
                    alCambiar()
                }) { Text("Aceptar") }
            },
            dismissButton = { TextButton(onClick = { abierto = false }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun FilaListaPref(p: ListPreference, alCambiar: () -> Unit) {
    var abierto by remember { mutableStateOf(false) }
    val titulo = p.title?.toString().orEmpty()
    FilaPref(p, p.summary?.toString()) { abierto = true }
    if (abierto) {
        val entradas = p.entries?.map { it.toString() }.orEmpty()
        val valores = p.entryValues?.map { it.toString() }.orEmpty()
        AlertDialog(
            onDismissRequest = { abierto = false },
            title = { Text(p.dialogTitle?.toString()?.takeIf { it.isNotBlank() } ?: titulo) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    entradas.forEachIndexed { i, etiqueta ->
                        val valor = valores.getOrNull(i) ?: return@forEachIndexed
                        Box(Modifier.padding(vertical = 8.dp)) {
                            FilaOpcion(etiqueta, valor == p.value) {
                                if (p.callChangeListener(valor)) p.value = valor
                                abierto = false
                                alCambiar()
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { abierto = false }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun FilaMultiplePref(p: MultiSelectListPreference, alCambiar: () -> Unit) {
    var abierto by remember { mutableStateOf(false) }
    val titulo = p.title?.toString().orEmpty()
    FilaPref(p, p.summary?.toString()) { abierto = true }
    if (abierto) {
        val entradas = p.entries?.map { it.toString() }.orEmpty()
        val valores = p.entryValues?.map { it.toString() }.orEmpty()
        var marcadas by remember { mutableStateOf(p.values.toSet()) }
        AlertDialog(
            onDismissRequest = { abierto = false },
            title = { Text(p.dialogTitle?.toString()?.takeIf { it.isNotBlank() } ?: titulo) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    entradas.forEachIndexed { i, etiqueta ->
                        val valor = valores.getOrNull(i) ?: return@forEachIndexed
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                marcadas = if (valor in marcadas) marcadas - valor else marcadas + valor
                            }.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = valor in marcadas, onCheckedChange = null)
                            Spacer(Modifier.width(8.dp))
                            Text(etiqueta)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (p.callChangeListener(marcadas)) p.values = marcadas
                    abierto = false
                    alCambiar()
                }) { Text("Aceptar") }
            },
            dismissButton = { TextButton(onClick = { abierto = false }) { Text("Cancelar") } },
        )
    }
}
