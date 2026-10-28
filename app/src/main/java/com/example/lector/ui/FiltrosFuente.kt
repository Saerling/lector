package com.example.lector.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

/**
 * Panel de filtros de búsqueda de una fuente. Port de SourceFilterDialog de Komikku: cada filtro de la
 * extensión (Header, Separator, CheckBox, TriState, Text, Select, Sort, Group) se edita EN SITIO cambiando su
 * `state`, y la misma lista editada es la que se manda a la búsqueda. Lo que no se porta: las búsquedas guardadas.
 */
@Composable
fun PanelFiltros(filtros: FilterList, onReset: () -> Unit, onFiltrar: () -> Unit, onCerrar: () -> Unit, onMasReciente: (() -> Unit)? = null) {
    // Los filtros no son estado de Compose: este contador obliga a repintar las filas tras cada cambio.
    var tick by remember { mutableIntStateOf(0) }
    Dialog(onDismissRequest = onCerrar, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(LectorColors.Bg).statusBarsPadding().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { onReset(); tick++ }) { Text("Restablecer") }
                if (onMasReciente != null) TextButton(onClick = onMasReciente) { Text("Más reciente") }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onCerrar) { Text("Cancelar") }
                Spacer(Modifier.width(4.dp))
                Button(onClick = { onFiltrar(); onCerrar() }) { Text("Filtrar") }
            }
            Divisor()
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 24.dp)) {
                items(filtros.toList()) { filtro -> FilaFiltro(filtro, tick) { tick++ } }
            }
        }
    }
}

@Composable
private fun FilaFiltro(filtro: Filter<*>, tick: Int, alCambiar: () -> Unit) {
    when (filtro) {
        is Filter.Header -> TituloSeccion(filtro.name)
        is Filter.Separator -> Divisor()
        is Filter.CheckBox -> FilaCasilla(filtro.name, if (filtro.state) ToggleableState.On else ToggleableState.Off) {
            filtro.state = !filtro.state
            alCambiar()
        }
        is Filter.TriState -> FilaCasilla(
            filtro.name,
            when (filtro.state) {
                Filter.TriState.STATE_INCLUDE -> ToggleableState.On
                Filter.TriState.STATE_EXCLUDE -> ToggleableState.Indeterminate
                else -> ToggleableState.Off
            },
        ) {
            // Ignorar → incluir → excluir → ignorar (como en Komikku).
            filtro.state = when (filtro.state) {
                Filter.TriState.STATE_IGNORE -> Filter.TriState.STATE_INCLUDE
                Filter.TriState.STATE_INCLUDE -> Filter.TriState.STATE_EXCLUDE
                else -> Filter.TriState.STATE_IGNORE
            }
            alCambiar()
        }
        is Filter.Text -> {
            var texto by remember(filtro) { mutableStateOf(filtro.state) }
            OutlinedTextField(
                value = texto,
                onValueChange = { texto = it; filtro.state = it },
                label = { Text(filtro.name) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 6.dp),
            )
        }
        is Filter.Select<*> -> FilaSeleccion(filtro, alCambiar)
        is Filter.Sort -> Plegable(filtro.name) {
            filtro.values.forEachIndexed { i, nombre ->
                val actual = filtro.state?.takeIf { it.index == i }
                Row(
                    Modifier.fillMaxWidth().clickable {
                        val ascendente = if (actual != null) !actual.ascending else (filtro.state?.ascending ?: true)
                        filtro.state = Filter.Sort.Selection(i, ascendente)
                        alCambiar()
                    }.padding(horizontal = 24.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LText(
                        nombre, LectorType.Body, Modifier.weight(1f),
                        color = if (actual != null) MaterialTheme.colorScheme.primary else LectorColors.Text,
                    )
                    if (actual != null) Text(if (actual.ascending) "↑" else "↓", color = MaterialTheme.colorScheme.primary)
                }
            }
        }
        is Filter.Group<*> -> Plegable(filtro.name) {
            Column {
                filtro.state.filterIsInstance<Filter<*>>().forEach { hijo -> FilaFiltro(hijo, tick, alCambiar) }
            }
        }
        // Etiquetas con autocompletado (solo extensiones de TachiyomiSY): se escriben separadas por comas.
        is Filter.AutoComplete -> {
            var texto by remember(filtro) { mutableStateOf(filtro.state.joinToString(", ")) }
            OutlinedTextField(
                value = texto,
                onValueChange = { nuevo ->
                    texto = nuevo
                    filtro.state = nuevo.split(',').map { it.trim() }.filter { it.isNotEmpty() }
                },
                label = { Text(filtro.name) },
                placeholder = { Text(filtro.hint) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun FilaCasilla(etiqueta: String, estado: ToggleableState, alPulsar: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = alPulsar).padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TriStateCheckbox(state = estado, onClick = null)
        Spacer(Modifier.width(12.dp))
        LText(etiqueta, LectorType.Body)
    }
}

@Composable
private fun FilaSeleccion(filtro: Filter.Select<*>, alCambiar: () -> Unit) {
    var abierto by remember { mutableStateOf(false) }
    val opciones = filtro.values.map { it.toString() }
    Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        LText(filtro.name, LectorType.Body, Modifier.weight(1f))
        Box {
            TextButton(onClick = { abierto = true }) { Text(opciones.getOrElse(filtro.state) { "" } + " ▾") }
            DropdownMenu(expanded = abierto, onDismissRequest = { abierto = false }) {
                opciones.forEachIndexed { i, opcion ->
                    DropdownMenuItem(
                        text = { Text(opcion) },
                        onClick = { filtro.state = i; abierto = false; alCambiar() },
                    )
                }
            }
        }
    }
}

/** Grupo que se abre y se cierra pulsando su título (como CollapsibleBox de Komikku, cerrado al empezar). */
@Composable
private fun Plegable(titulo: String, contenido: @Composable () -> Unit) {
    var abierto by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().clickable { abierto = !abierto }.padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LText(titulo, LectorType.Section.copy(fontSize = 17.sp), Modifier.weight(1f))
        Text(if (abierto) "▲" else "▼")
    }
    if (abierto) contenido()
}

/**
 * Filtros de una fuente ↔ texto (para las búsquedas guardadas, como Komikku): se guarda el estado de cada filtro por
 * su posición y se vuelve a poner sobre una lista nueva de la misma fuente (si cambió, lo que no encaje se ignora).
 */
object FiltrosTexto {
    fun guardar(l: FilterList): String = org.json.JSONArray().also { a -> l.forEach { a.put(estado(it)) } }.toString()

    private fun estado(f: Filter<*>): Any = when (f) {
        is Filter.Select<*> -> f.state
        is Filter.Text -> f.state
        is Filter.CheckBox -> f.state
        is Filter.TriState -> f.state
        is Filter.Sort -> f.state?.let { org.json.JSONArray().put(it.index).put(it.ascending) } ?: org.json.JSONObject.NULL
        is Filter.Group<*> -> org.json.JSONArray().also { a -> f.state.forEach { h -> a.put((h as? Filter<*>)?.let { estado(it) } ?: org.json.JSONObject.NULL) } }
        else -> org.json.JSONObject.NULL
    }

    fun aplicar(l: FilterList, texto: String): FilterList {
        val a = runCatching { org.json.JSONArray(texto) }.getOrNull() ?: return l
        l.forEachIndexed { i, f -> if (i < a.length()) poner(f, a.opt(i)) }
        return l
    }

    @Suppress("UNCHECKED_CAST")
    private fun poner(f: Filter<*>, v: Any?) {
        runCatching {
            when (f) {
                is Filter.Select<*> -> (v as? Int)?.takeIf { it in f.values.indices }?.let { f.state = it }
                is Filter.Text -> (v as? String)?.let { f.state = it }
                is Filter.CheckBox -> (v as? Boolean)?.let { f.state = it }
                is Filter.TriState -> (v as? Int)?.let { f.state = it }
                is Filter.Sort -> (v as? org.json.JSONArray)?.let { f.state = Filter.Sort.Selection(it.getInt(0), it.getBoolean(1)) }
                is Filter.Group<*> -> (v as? org.json.JSONArray)?.let { a ->
                    f.state.forEachIndexed { i, h -> if (i < a.length()) (h as? Filter<*>)?.let { poner(it, a.opt(i)) } }
                }
                else -> Unit
            }
        }
    }

    /**
     * «Más reciente»: pone el orden de la fuente en su opción de fecha/actualización (descendente). false si la
     * fuente no tiene un orden así (entonces se usa su sección de recientes).
     */
    fun ponerMasReciente(l: FilterList): Boolean {
        val clave = Regex("(?i)recien|reciente|latest|update|actualiz|new|nuev|fecha|date|añadid|added|last")
        for (f in l) {
            if (f is Filter.Sort) {
                val i = f.values.indexOfFirst { clave.containsMatchIn(it) }
                if (i >= 0) { f.state = Filter.Sort.Selection(i, false); return true }
            }
            if (f is Filter.Select<*> && Regex("(?i)orden|sort|order").containsMatchIn(f.name)) {
                val i = f.values.indexOfFirst { clave.containsMatchIn(it.toString()) }
                if (i >= 0) { f.state = i; return true }
            }
        }
        return false
    }
}
