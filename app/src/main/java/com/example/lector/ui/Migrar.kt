package com.example.lector.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.lector.Almacen
import com.example.lector.Comic
import com.example.lector.ext.CapituloFuente
import com.example.lector.ext.ExtManager
import com.example.lector.ext.FuenteUI
import com.example.lector.ext.MangaFuente
import com.example.lector.ext.Motor
import com.example.lector.ext.bandera
import com.example.lector.ext.nombreIdioma
import kotlinx.coroutines.launch

/**
 * Migración de fuente (como Komikku): se elige otra fuente, se busca el manga en ella y se pasa la entrada de la
 * biblioteca a esa fuente conservando categorías, portada y capítulos leídos (emparejados por número de capítulo).
 * Lo que no se hace: mover las descargas de la fuente vieja, ni elegir qué datos copiar.
 */
@Composable
fun MigrarScreen(
    ext: ExtManager,
    almacen: Almacen,
    comic: Comic,
    origen: FuenteUI,
    capsOrigen: List<CapituloFuente>?,
    alHecho: (destino: FuenteUI) -> Unit,
    onBack: () -> Unit,
) {
    val motor = Motor.actual
    val scope = rememberCoroutineScope()
    var destino by remember { mutableStateOf<FuenteUI?>(null) }
    var consulta by remember { mutableStateOf(comic.titulo) }
    val resultados = remember { mutableStateListOf<MangaFuente>() }
    var buscando by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var elegido by remember { mutableStateOf<MangaFuente?>(null) }
    var migrando by remember { mutableStateOf(false) }

    fun buscar(f: FuenteUI, q: String) {
        if (motor == null || q.isBlank()) return
        scope.launch {
            buscando = true; error = null; resultados.clear()
            runCatching { motor.buscar(f, q.trim(), 1) }
                .onSuccess { resultados.addAll(it.mangas) }
                .onFailure { error = it.message ?: "No se pudo buscar" }
            buscando = false
        }
    }

    val f = destino
    if (f == null) {
        // Paso 1: elegir la fuente a la que se migra.
        PaginaAjustes("Migrar a otra fuente", onBack) {
            LText(
                "Elige la fuente a la que quieres pasar «${comic.titulo}». Se conservan tus categorías y los capítulos leídos.",
                LectorType.Body, Modifier.padding(horizontal = 24.dp, vertical = 8.dp), color = LectorColors.Muted,
            )
            val opciones = ext.fuentes.filter { it.id != origen.id }
            if (opciones.isEmpty()) {
                LText(
                    "No hay otras fuentes instaladas y de confianza.",
                    LectorType.Body, Modifier.padding(24.dp), color = LectorColors.Muted,
                )
            }
            opciones.forEach { o ->
                FilaAjuste(null, o.nombre, "${bandera(o.lang)} ${nombreIdioma(o.lang)}") {
                    destino = o
                    buscar(o, consulta)
                }
            }
        }
        return
    }

    // Paso 2: buscar el manga en la fuente elegida y confirmar.
    PaginaAjustes("Migrar a ${f.nombre}", { destino = null; resultados.clear(); error = null }) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { CampoBusqueda(consulta, { consulta = it }, "Buscar en ${f.nombre}") }
            Spacer(Modifier.width(10.dp))
            PillButton("Buscar", Icons.Rounded.Search, { buscar(f, consulta) })
        }
        error?.let { LText(it, LectorType.Label, Modifier.padding(horizontal = 24.dp), color = Color(0xFFE57373)) }
        if (buscando) {
            Box(Modifier.fillMaxWidth().padding(24.dp), Alignment.Center) { CircularProgressIndicator(Modifier.size(24.dp)) }
        } else if (resultados.isEmpty() && error == null) {
            LText("Sin resultados. Prueba con otro título.", LectorType.Body, Modifier.padding(24.dp), color = LectorColors.Muted)
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            resultados.forEach { m ->
                Row(
                    Modifier.fillMaxWidth().pressable { elegido = m }.padding(horizontal = 24.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ImagenRemota(
                        m.portadaUrl,
                        Modifier.width(52.dp).aspectRatio(2f / 3f).clip(SuaveShape(10.dp)),
                        motor?.cabeceras(f).orEmpty(), fuenteId = f.id,
                    )
                    Spacer(Modifier.width(16.dp))
                    LText(m.titulo, LectorType.Body, Modifier.weight(1f), maxLines = 3)
                }
            }
        }
    }

    elegido?.let { m ->
        AlertDialog(
            onDismissRequest = { if (!migrando) elegido = null },
            title = { Text("¿Migrar a ${f.nombre}?") },
            text = {
                Column {
                    Text("«${m.titulo}» sustituirá a «${comic.titulo}» en tu biblioteca.")
                    Spacer(Modifier.size(8.dp))
                    Text("Se conservan categorías y portada; los capítulos leídos se emparejan por número. Las descargas de la fuente anterior no se mueven.")
                    if (migrando) {
                        Spacer(Modifier.size(12.dp))
                        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !migrando && motor != null,
                    onClick = {
                        migrando = true
                        scope.launch {
                            val resultado = runCatching {
                                requireNotNull(motor) { "Motor de fuentes no disponible" }
                                check(almacen.comicEnLinea(f.id, m.url) == null) { "Ese manga ya está en tu biblioteca" }
                                val ficha = motor.ficha(f, m)
                                val leidos = ext.migrarLeidos(origen.id, capsOrigen, f.id, ficha.capitulos, comic.ultimaPagina)
                                val portada = ficha.detalle.portadaUrl ?: m.portadaUrl
                                check(almacen.migrarEnLinea(comic, f.id, m.url, portada, ficha.capitulos.size, leidos)) {
                                    "No se pudo migrar"
                                }
                                // Los capítulos que hay ahora son la base: solo lo que salga después contará como novedad.
                                almacen.registrarCapitulos(
                                    f.id, m.url, ficha.detalle.titulo.ifBlank { m.titulo }, portada, ficha.capitulos,
                                )
                            }
                            migrando = false
                            resultado
                                .onSuccess { elegido = null; alHecho(f) }
                                .onFailure { elegido = null; Avisos.mostrar(it.message ?: "No se pudo migrar") }
                        }
                    },
                ) { Text("Migrar") }
            },
            dismissButton = { TextButton(enabled = !migrando, onClick = { elegido = null }) { Text("Cancelar") } },
        )
    }
}
