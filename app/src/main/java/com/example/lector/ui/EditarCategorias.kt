package com.example.lector.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.zIndex
import com.example.lector.Almacen
import com.example.lector.Categoria
import kotlin.math.roundToInt

private val AltoFila = 68.dp
private val SeparacionFila = 10.dp

/**
 * "Editar categorías" como en Komikku: una tarjeta por categoría con asa para arrastrar y reordenar,
 * lápiz (renombrar), ojo (ocultar/mostrar) y papelera (borrar). Botón "Añadir" abajo a la derecha.
 *
 * Se usa desde Más → Categorías y desde el diálogo "Asignar categorías" → Editar.
 */
@Composable
fun EditarCategoriasScreen(almacen: Almacen, onBack: () -> Unit) {
    val lista = remember { mutableStateListOf<Categoria>().apply { addAll(almacen.categorias()) } }
    var nueva by remember { mutableStateOf(false) }
    var renombrar by remember { mutableStateOf<Categoria?>(null) }
    var borrar by remember { mutableStateOf<Categoria?>(null) }
    var arrastrandoId by remember { mutableStateOf<String?>(null) }
    var desplazamiento by remember { mutableStateOf(0f) }

    fun refrescar() {
        lista.clear()
        lista.addAll(almacen.categorias())
    }

    val paso = with(LocalDensity.current) { (AltoFila + SeparacionFila).toPx() }

    Box(Modifier.fillMaxSize().background(LectorColors.Bg)) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BotonAtras(onBack)
                Spacer(Modifier.width(14.dp))
                LText("Editar categorías", LectorType.Display.copy(fontSize = 26.sp), maxLines = 1)
            }

            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(SeparacionFila),
            ) {
                if (lista.isEmpty()) {
                    LText(
                        "Todavía no tienes categorías. Pulsa «Añadir» para crear la primera.",
                        LectorType.Body, Modifier.padding(8.dp), color = LectorColors.Muted,
                    )
                }
                lista.toList().forEach { c ->
                    key(c.id) {
                        val moviendo = arrastrandoId == c.id
                        Row(
                            Modifier
                                .fillMaxWidth().height(AltoFila)
                                .zIndex(if (moviendo) 1f else 0f)
                                .offset { IntOffset(0, if (moviendo) desplazamiento.roundToInt() else 0) }
                                .shadow(if (moviendo) 10.dp else 0.dp, SuaveShape(20.dp))
                                .clip(SuaveShape(20.dp))
                                .background(if (moviendo) LectorColors.CardHigh else LectorColors.Card)
                                .border(1.dp, LectorColors.GlassBorderBrush, SuaveShape(20.dp))
                                .padding(start = 6.dp, end = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // Asa: mantener y arrastrar para cambiar el orden.
                            Box(
                                Modifier.size(width = 52.dp, height = AltoFila).pointerInput(c.id) {
                                    detectDragGestures(
                                        onDragStart = { arrastrandoId = c.id; desplazamiento = 0f },
                                        onDragEnd = {
                                            arrastrandoId = null; desplazamiento = 0f
                                            almacen.reordenarCategorias(lista.map { it.id })
                                        },
                                        onDragCancel = {
                                            arrastrandoId = null; desplazamiento = 0f
                                            almacen.reordenarCategorias(lista.map { it.id })
                                        },
                                        onDrag = { cambio, delta ->
                                            cambio.consume()
                                            desplazamiento += delta.y
                                            val i = lista.indexOfFirst { it.id == c.id }
                                            if (i >= 0) {
                                                if (desplazamiento > paso / 2 && i < lista.lastIndex) {
                                                    lista.add(i + 1, lista.removeAt(i)); desplazamiento -= paso
                                                } else if (desplazamiento < -paso / 2 && i > 0) {
                                                    lista.add(i - 1, lista.removeAt(i)); desplazamiento += paso
                                                }
                                            }
                                        },
                                    )
                                },
                                Alignment.Center,
                            ) { Icon(Iconos.Arrastrar, "Arrastrar para ordenar", tint = LectorColors.Muted) }

                            Column(Modifier.weight(1f).padding(horizontal = 4.dp)) {
                                LText(c.nombre, LectorType.Body.copy(fontSize = 16.sp), maxLines = 1)
                                if (c.oculta) LText("Oculta", LectorType.Label, color = LectorColors.Muted)
                            }
                            BotonIcono(Icons.Rounded.Edit, "Renombrar") { renombrar = c }
                            BotonIcono(if (c.oculta) Iconos.OjoTachado else Iconos.Ojo, if (c.oculta) "Mostrar" else "Ocultar") {
                                almacen.alternarOculta(c.id); refrescar()
                            }
                            BotonIcono(Icons.Rounded.Delete, "Borrar") { borrar = c }
                        }
                    }
                }
                Spacer(Modifier.height(120.dp)) // que el botón "Añadir" no tape la última tarjeta
            }
        }

        // Botón "Añadir" (abajo a la derecha).
        Row(
            Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(20.dp)
                .clip(SuaveShape(20.dp))
                .background(MaterialTheme.colorScheme.primary)
                .pressable { nueva = true }
                .padding(horizontal = 22.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Add, null, tint = MaterialTheme.colorScheme.onPrimary)
            Spacer(Modifier.width(10.dp))
            LText("Añadir", LectorType.Section, color = MaterialTheme.colorScheme.onPrimary)
        }
    }

    if (nueva) DialogoNombreCategoria(
        "Nueva categoría", "",
        { almacen.crearCategoria(it); refrescar(); nueva = false }, { nueva = false },
    )
    renombrar?.let { c ->
        DialogoNombreCategoria(
            "Renombrar", c.nombre,
            { almacen.renombrarCategoria(c.id, it); refrescar(); renombrar = null }, { renombrar = null },
        )
    }
    borrar?.let { c ->
        DialogoAlerta(
            onDismissRequest = { borrar = null },
            title = { Text("¿Borrar «${c.nombre}»?") },
            text = { Text("Los mangas y cómics no se borran, solo pierden esta etiqueta.") },
            confirmButton = {
                TextButton(onClick = { almacen.eliminarCategoria(c.id); refrescar(); borrar = null }) { Text("Borrar") }
            },
            dismissButton = { TextButton(onClick = { borrar = null }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun BotonIcono(icono: androidx.compose.ui.graphics.vector.ImageVector, desc: String, onClick: () -> Unit) {
    Box(Modifier.size(48.dp).pressable(onClick), Alignment.Center) {
        Icon(icono, desc, tint = LectorColors.Text, modifier = Modifier.size(24.dp))
    }
}

@Composable
private fun DialogoNombreCategoria(titulo: String, inicial: String, alAceptar: (String) -> Unit, alCancelar: () -> Unit) {
    var texto by remember { mutableStateOf(inicial) }
    DialogoAlerta(
        onDismissRequest = alCancelar,
        title = { Text(titulo) },
        text = { CampoTextoShiori(value = texto, onValueChange = { texto = it }, singleLine = true, label = { Text("Nombre") }) },
        confirmButton = { TextButton(onClick = { alAceptar(texto) }) { Text("Aceptar") } },
        dismissButton = { TextButton(onClick = alCancelar) { Text("Cancelar") } },
    )
}

/**
 * Diálogo "Asignar categorías" (casillas). Abajo: «Editar» (abre la pantalla de editar categorías),
 * «Cancelar» y «Aceptar».
 */
@Composable
fun DialogoAsignarCategorias(
    categorias: List<Categoria>,
    seleccionInicial: Set<String>,
    alEditar: () -> Unit,
    alCancelar: () -> Unit,
    alAceptar: (Set<String>) -> Unit,
) {
    var sel by remember { mutableStateOf(seleccionInicial) }
    Dialog(onDismissRequest = alCancelar) {
        Surface(shape = SuaveShape(28.dp), color = LectorColors.CardHigh) {
            Column(Modifier.padding(top = 24.dp, bottom = 12.dp)) {
                LText("Asignar categorías", LectorType.Display.copy(fontSize = 24.sp), Modifier.padding(horizontal = 24.dp))
                Spacer(Modifier.height(12.dp))
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                    if (categorias.isEmpty()) {
                        LText(
                            "Todavía no tienes categorías. Pulsa «Editar» para crearlas.",
                            LectorType.Body, Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                            color = LectorColors.Muted,
                        )
                    }
                    categorias.forEach { c ->
                        val marcada = c.id in sel
                        Row(
                            Modifier.fillMaxWidth()
                                .pressable { sel = if (marcada) sel - c.id else sel + c.id }
                                .padding(horizontal = 16.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CasillaShiori(checked = marcada, onCheckedChange = { sel = if (marcada) sel - c.id else sel + c.id })
                            Spacer(Modifier.width(12.dp))
                            LText(c.nombre, LectorType.Body.copy(fontSize = 16.sp), maxLines = 1)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = alEditar) { Text("Editar") }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = alCancelar) { Text("Cancelar") }
                    TextButton(onClick = { alAceptar(sel) }) { Text("Aceptar") }
                }
            }
        }
    }
}
