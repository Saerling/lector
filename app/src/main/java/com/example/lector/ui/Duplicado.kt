package com.example.lector.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.example.lector.Almacen
import com.example.lector.Comic
import com.example.lector.ext.ExtManager
import com.example.lector.ext.FuenteUI
import com.example.lector.ext.MangaFuente
import com.example.lector.ext.Motor
import kotlinx.coroutines.launch

/**
 * Antes de añadir un manga a la biblioteca: si ya tienes uno con el mismo título en OTRA fuente, se pregunta qué
 * hacer (verlo, migrar tu progreso a esta fuente o añadirlo igualmente), como el aviso de duplicados de Mihon.
 */
object Duplicados {
    class Pedido(val existente: Comic, val fuente: FuenteUI, val manga: MangaFuente, val anadir: () -> Unit)

    var pedido by mutableStateOf<Pedido?>(null)

    fun comprobar(almacen: Almacen, f: FuenteUI, m: MangaFuente, titulo: String = m.titulo, anadir: () -> Unit) {
        val clave = tituloComparable(titulo)
        val ya = if (clave.length < 2) null else almacen.cargar().firstOrNull {
            it.enLinea && !(it.fuenteId == f.id && it.urlManga == m.url) && tituloComparable(it.titulo) == clave
        }
        if (ya == null) anadir() else { Tacto.tic(); pedido = Pedido(ya, f, m, anadir) }
    }
}

/**
 * La ventanita: fondo que se oscurece y una tarjeta que crece un poco al aparecer (desde 0,94, nunca desde cero) y se
 * va más rápido y con menos movimiento del que entró. Enseña las dos portadas: la que tienes y la nueva.
 */
@Composable
fun DialogoDuplicado(ext: ExtManager, almacen: Almacen, alRecargar: () -> Unit, verExistente: (Comic) -> Unit) {
    val p = Duplicados.pedido
    var ultimo by remember { mutableStateOf<Duplicados.Pedido?>(null) }
    if (p != null) ultimo = p
    var migrando by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    fun cerrar() { if (!migrando) Duplicados.pedido = null }

    AnimatedVisibility(p != null, enter = fadeIn(tween(200)), exit = fadeOut(tween(160))) {
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)).pointerInput(Unit) { detectTapGestures { cerrar() } },
            Alignment.Center,
        ) {
            val d = ultimo ?: return@Box
            val nombreViejo = ext.fuentes.firstOrNull { it.id == d.existente.fuenteId }?.nombre ?: "otra fuente"
            AnimatedVisibility(
                visible = p != null,
                enter = scaleIn(tween(260, easing = EnfasisEasing), initialScale = 0.94f) + fadeIn(tween(180)) +
                    slideInVertically(tween(260, easing = EnfasisEasing)) { it / 24 },
                exit = scaleOut(tween(140), targetScale = 0.97f) + fadeOut(tween(120)),
            ) {
                Column(
                    Modifier.padding(24.dp).widthIn(max = 420.dp).fillMaxWidth().clip(SuaveShape(30.dp))
                        .background(LectorColors.Card).pointerInput(Unit) { detectTapGestures { } }.padding(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // Las dos portadas: la que tienes → la nueva.
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            ComicCover(d.existente, almacen, Modifier.width(92.dp).aspectRatio(2f / 3f), SuaveShape(14.dp))
                            Spacer(Modifier.height(6.dp))
                            LText(nombreViejo, LectorType.Label, color = LectorColors.Muted, maxLines = 1)
                        }
                        Icon(Icons.Rounded.ArrowForward, null, tint = LectorColors.Muted, modifier = Modifier.size(22.dp))
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            ImagenRemota(
                                d.manga.portadaUrl, Modifier.width(92.dp).aspectRatio(2f / 3f).clip(SuaveShape(14.dp)),
                                Motor.actual?.cabeceras(d.fuente).orEmpty(), fuenteId = d.fuente.id,
                            )
                            Spacer(Modifier.height(6.dp))
                            LText(d.fuente.nombre, LectorType.Label, color = LectorColors.Muted, maxLines = 1)
                        }
                    }
                    Spacer(Modifier.height(18.dp))
                    LText("Ya lo tienes en tu biblioteca", LectorType.Section)
                    Spacer(Modifier.height(6.dp))
                    LText(
                        "«${d.existente.titulo}» está guardado desde $nombreViejo. ¿Qué quieres hacer?",
                        LectorType.Body, color = LectorColors.Muted,
                    )
                    Spacer(Modifier.height(18.dp))
                    PillButton("Ver el que tengo", Icons.Rounded.ArrowForward, {
                        Duplicados.pedido = null; verExistente(d.existente)
                    }, Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    if (migrando) {
                        Box(Modifier.fillMaxWidth().height(46.dp), Alignment.Center) { IndicadorCarga(Modifier.size(22.dp), strokeWidth = 2.dp) }
                    } else PillButton("Migrar aquí (con tu progreso)", Icons.Rounded.Refresh, {
                        migrando = true
                        scope.launch {
                            val r = runCatching { migrarComic(ext, almacen, d.existente, d.existente.fuenteId, null, d.fuente, d.manga) }
                            migrando = false
                            Duplicados.pedido = null
                            r.onSuccess { alRecargar(); Tacto.confirmar(); Avisos.mostrar("Migrado a ${d.fuente.nombre}") }
                                .onFailure { Avisos.mostrar("No se pudo migrar: ${it.message}") }
                        }
                    }, Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    PillButton("Añadir igualmente", Icons.Rounded.Add, {
                        Duplicados.pedido = null; d.anadir()
                    }, Modifier.fillMaxWidth())
                    Spacer(Modifier.height(4.dp))
                    Box(Modifier.fillMaxWidth().pressable { cerrar() }.padding(12.dp), Alignment.Center) {
                        LText("Cancelar", LectorType.Body, color = LectorColors.Muted)
                    }
                }
            }
        }
    }
}
