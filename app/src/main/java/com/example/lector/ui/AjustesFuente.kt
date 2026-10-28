package com.example.lector.ui

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.rounded.Check
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
        Interruptor(p.isChecked, if (activa) { nuevo ->
            if (p.callChangeListener(nuevo)) p.isChecked = nuevo
            alCambiar()
        } else null)
    }
}

@Composable
private fun FilaTextoPref(p: EditTextPreference, alCambiar: () -> Unit) {
    var abierto by remember { mutableStateOf(false) }
    val titulo = p.title?.toString().orEmpty()
    FilaPref(p, p.summary?.toString()) { abierto = true }
    if (abierto) {
        var texto by remember { mutableStateOf(p.text.orEmpty()) }
        DialogoShiori(
            p.dialogTitle?.toString()?.takeIf { it.isNotBlank() } ?: titulo, { abierto = false },
            aceptar = "Aceptar" to {
                if (p.callChangeListener(texto)) p.text = texto
                abierto = false
                alCambiar()
            },
        ) {
            p.dialogMessage?.toString()?.takeIf { it.isNotBlank() }?.let { LText(it, LectorType.Label, Modifier.padding(start = 10.dp, bottom = 8.dp), color = LectorColors.Muted) }
            Box(Modifier.padding(horizontal = 10.dp)) { CampoBusqueda(texto, { texto = it }, titulo) }
        }
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
        DialogoShiori(p.dialogTitle?.toString()?.takeIf { it.isNotBlank() } ?: titulo, { abierto = false }) {
            entradas.forEachIndexed { i, etiqueta ->
                val valor = valores.getOrNull(i) ?: return@forEachIndexed
                OpcionPildora(etiqueta, valor == p.value) {
                    Tacto.tic()
                    if (p.callChangeListener(valor)) p.value = valor
                    abierto = false
                    alCambiar()
                }
            }
        }
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
        DialogoShiori(
            p.dialogTitle?.toString()?.takeIf { it.isNotBlank() } ?: titulo, { abierto = false },
            aceptar = "Aceptar" to {
                if (p.callChangeListener(marcadas)) p.values = marcadas
                abierto = false
                alCambiar()
            },
        ) {
            entradas.forEachIndexed { i, etiqueta ->
                val valor = valores.getOrNull(i) ?: return@forEachIndexed
                OpcionPildora(etiqueta, valor in marcadas) {
                    Tacto.tic()
                    marcadas = if (valor in marcadas) marcadas - valor else marcadas + valor
                }
            }
        }
    }
}

/**
 * Diálogo con el estilo de Shiori (tarjeta de cristal redondeada, título grande, opciones en píldora y botones
 * píldora), con entrada animada: crece desde 0,94 y se funde. Sustituye a los diálogos de Android.
 */
@Composable
fun DialogoShiori(
    titulo: String,
    onCerrar: () -> Unit,
    aceptar: Pair<String, () -> Unit>? = null,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onCerrar, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        val visible = remember { androidx.compose.animation.core.MutableTransitionState(false) }.apply { targetState = true }
        androidx.compose.animation.AnimatedVisibility(
            visibleState = visible,
            enter = androidx.compose.animation.scaleIn(androidx.compose.animation.core.tween(240, easing = EnfasisEasing), initialScale = 0.94f) +
                androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(180)),
        ) {
            Column(
                Modifier.padding(20.dp).fillMaxWidth().clip(SuaveShape(30.dp)).background(LectorColors.Card)
                    .border(1.dp, LectorColors.Linea, SuaveShape(30.dp)).padding(vertical = 22.dp),
            ) {
                LText(titulo, LectorType.Section, Modifier.padding(horizontal = 24.dp))
                Spacer(Modifier.height(12.dp))
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 14.dp), content = content)
                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.pressable(onCerrar).padding(horizontal = 16.dp, vertical = 10.dp)) { LText("Cancelar", LectorType.Body, color = LectorColors.Muted) }
                    if (aceptar != null) {
                        Spacer(Modifier.width(8.dp))
                        PillButton(aceptar.first, androidx.compose.material.icons.Icons.Rounded.Check, aceptar.second)
                    }
                }
            }
        }
    }
}

/** Opción de una lista dentro de [DialogoShiori]: píldora que se ilumina con el acento si está elegida. */
@Composable
fun OpcionPildora(texto: String, elegida: Boolean, onClick: () -> Unit) {
    val acento = androidx.compose.material3.MaterialTheme.colorScheme.primary
    val fondo by androidx.compose.animation.animateColorAsState(if (elegida) acento.copy(alpha = 0.22f) else Color.Transparent, label = "op")
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp).clip(SuaveShape(22.dp)).background(fondo).pressable(onClick)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(20.dp).clip(androidx.compose.foundation.shape.CircleShape)
                .border(2.dp, if (elegida) acento else LectorColors.Muted, androidx.compose.foundation.shape.CircleShape),
            Alignment.Center,
        ) { if (elegida) Box(Modifier.size(10.dp).clip(androidx.compose.foundation.shape.CircleShape).background(acento)) }
        Spacer(Modifier.width(14.dp))
        LText(texto, LectorType.Body, color = LectorColors.Text)
    }
}
