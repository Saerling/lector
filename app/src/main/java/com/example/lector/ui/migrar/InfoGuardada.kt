// Adaptado de Komikku (https://github.com/komikku-app/komikku, Apache-2.0/GPL-3.0):
// app/src/main/java/eu/kanade/presentation/manga/components/MangaInfoHeader.kt (MangaInfoBox, MangaContentInfo,
// ExpandableMangaDescription) y ChapterHeader/MangaChapterListItem: cómo se ve un manga cuya fuente es un «stub».
package com.example.lector.ui.migrar

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Create
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.lector.Almacen
import com.example.lector.CacheFichas
import com.example.lector.Comic
import com.example.lector.ext.CapituloFuente
import com.example.lector.ext.ExtManager
import com.example.lector.ext.FichaManga
import com.example.lector.ui.BotonAtras
import com.example.lector.ui.ComicCover
import com.example.lector.ui.ImagenRemota
import com.example.lector.ui.LText
import com.example.lector.ui.LectorColors
import com.example.lector.ui.LectorType
import com.example.lector.ui.PillButton
import com.example.lector.ui.SuaveShape
import com.example.lector.ui.pressable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * La ficha de un manga con lo que hay guardado en el móvil, sin pedir nada a su fuente (como Komikku con una fuente
 * «stub»): datos de la copia o de la última vez (CacheFichas) y, si faltan, los de la biblioteca (Almacen); capítulos
 * con sus leídos. [accion] es el botón principal (p. ej. «Migrar» o «Buscar en otras fuentes»).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InfoGuardada(
    ext: ExtManager,
    almacen: Almacen,
    fuenteId: Long,
    mangaUrl: String,
    comic: Comic?,
    tituloRespaldo: String,
    portadaRespaldo: String?,
    fuenteTexto: String,
    fuenteFalta: Boolean,
    accion: String,
    iconoAccion: ImageVector,
    onBack: () -> Unit,
    onAccion: () -> Unit,
) {
    val ctx = LocalContext.current
    // Antes se leía en el hilo principal y sin iniciar la caché: si nadie la había iniciado aún, salía «Sin capítulos».
    val carga by produceState<Pair<Boolean, FichaManga?>>(false to null, fuenteId, mangaUrl) {
        value = true to withContext(Dispatchers.IO) {
            CacheFichas.iniciar(ctx)
            runCatching { CacheFichas.leer(fuenteId, mangaUrl) }.getOrNull()
        }
    }
    val (cargada, ficha) = carga
    val d = ficha?.detalle
    val caps: List<CapituloFuente> = ficha?.capitulos.orEmpty()
    val propio = comic?.editado == true
    val titulo = (if (propio) comic?.titulo else null) ?: d?.titulo?.ifBlank { null } ?: comic?.titulo ?: tituloRespaldo
    val autor = (if (propio) comic?.autor else null) ?: d?.autor?.ifBlank { null } ?: comic?.autor?.ifBlank { null }
    val artista = d?.artista?.ifBlank { null }?.takeIf { it != autor }
    val descripcion = (if (propio) comic?.descripcion else null) ?: d?.descripcion?.ifBlank { null } ?: comic?.descripcion?.ifBlank { null }
    val generos = (if (propio) comic?.generos else null)?.takeIf { it.isNotEmpty() } ?: d?.generos?.takeIf { it.isNotEmpty() } ?: comic?.generos.orEmpty()
    val estado = d?.estado?.takeIf { it != 0 } ?: comic?.estado ?: 0
    val portadaUrl = d?.portadaUrl ?: comic?.portadaUrl ?: portadaRespaldo
    // Leídos: los de la copia/historial; si no hay lista guardada, el recuento de la biblioteca.
    val leidos = if (caps.isNotEmpty()) caps.count { ext.esLeido(fuenteId, mangaUrl, it.url) } else comic?.ultimaPagina ?: 0
    val total = if (caps.isNotEmpty()) caps.size else comic?.paginas ?: 0

    Box(Modifier.fillMaxSize().background(LectorColors.Bg)) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 120.dp)) {
            item(key = "cabecera") {
                Box(Modifier.fillMaxWidth()) {
                    // Fondo: la portada borrosa que se funde con el fondo (MangaInfoBox de Komikku).
                    val fondo = LectorColors.Bg
                    Box(
                        Modifier.matchParentSize().alpha(0.2f).blur(7.dp).drawWithContent {
                            drawContent()
                            drawRect(Brush.verticalGradient(listOf(Color.Transparent, fondo), startY = size.height / 2))
                        },
                    ) {
                        if (comic != null) ComicCover(comic, almacen, Modifier.fillMaxSize(), SuaveShape(0.dp))
                        else ImagenRemota(portadaUrl, Modifier.fillMaxSize(), fuenteId = fuenteId)
                    }
                    Column(Modifier.statusBarsPadding()) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            BotonAtras(onBack)
                        }
                        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp), verticalAlignment = Alignment.Top) {
                            val portada = Modifier.width(100.dp).aspectRatio(2f / 3f)
                            if (comic != null) ComicCover(comic, almacen, portada, SuaveShape(10.dp))
                            else ImagenRemota(portadaUrl, portada.clip(SuaveShape(10.dp)), fuenteId = fuenteId)
                            Spacer(Modifier.width(16.dp))
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                androidx.compose.foundation.text.selection.SelectionContainer {
                                    LText(titulo, LectorType.Hero, maxLines = 4)
                                }
                                Spacer(Modifier.height(2.dp))
                                LineaIcono(Icons.Rounded.Person, autor ?: "Autor desconocido")
                                artista?.let { LineaIcono(Icons.Rounded.Create, it) }
                                LineaIcono(iconoEstado(estado), textoEstado(estado))
                                LineaIcono(
                                    if (fuenteFalta) Icons.Rounded.Warning else Icons.Rounded.Info, fuenteTexto,
                                    if (fuenteFalta) LectorColors.Error else LectorColors.Muted,
                                )
                            }
                        }
                    }
                }
            }
            // En qué extensión estaba (instalada o en tus listados), con Instalar / Confiar.
            item(key = "ext") { TarjetaExtension(ext, fuenteId, Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) }
            if (descripcion != null || generos.isNotEmpty()) item(key = "desc") { Descripcion(descripcion, generos) }
            item(key = "caps_cab") {
                // ChapterHeader de Komikku: «N capítulos» y, aparte, cuántos llevas leídos.
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (!cargada) { com.example.lector.ui.IndicadorCarga(Modifier.size(20.dp), estado = com.example.lector.ui.orbes.EstadoOrbe.BREATHING); androidx.compose.foundation.layout.Spacer(Modifier.size(10.dp)) }
                    LText(
                        when {
                            !cargada -> "Cargando lo guardado…"
                            total == 0 -> "Sin capítulos guardados"
                            total == 1 -> "1 capítulo"
                            else -> "$total capítulos"
                        },
                        LectorType.Section.copy(fontWeight = FontWeight.SemiBold), Modifier.weight(1f),
                    )
                    if (total > 0) LText("$leidos leídos", LectorType.Label, color = LectorColors.Muted)
                }
                if (cargada && caps.isEmpty() && total > 0) LText(
                    "La lista de capítulos no se guardó en el móvil (solo el recuento de la biblioteca).",
                    LectorType.Label, Modifier.padding(horizontal = 16.dp, vertical = 4.dp), color = LectorColors.Muted,
                )
            }
            items(caps.size, key = { "c" + caps[it].url }) { i -> FilaCapitulo(caps[i], ext.esLeido(fuenteId, mangaUrl, caps[i].url)) }
        }
        Box(Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(20.dp)) {
            PillButton(accion, iconoAccion, onAccion)
        }
    }
}

/** Ocupa el tamaño del padre (como `Modifier.matchParentSize()` de BoxScope, que aquí no está a mano). */
@Composable
private fun Modifier.matchParentSize(): Modifier = this.then(Modifier.fillMaxWidth().height(300.dp))

@Composable
private fun LineaIcono(icono: ImageVector, texto: String, color: Color = LectorColors.Muted) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icono, null, tint = color, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        LText(texto, LectorType.Label, maxLines = 2, color = color)
    }
}

/** ExpandableMangaDescription de Komikku: 3 líneas plegadas; al tocar se despliega con los géneros en fichas. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Descripcion(texto: String?, generos: List<String>) {
    var abierta by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().animateContentSize().pressable { abierta = !abierta }.padding(horizontal = 16.dp, vertical = 6.dp)) {
        if (texto != null) {
            if (abierta) androidx.compose.foundation.text.selection.SelectionContainer { LText(texto, LectorType.Body, color = LectorColors.Muted) }
            else LText(texto, LectorType.Body, maxLines = 3, color = LectorColors.Muted)
        }
        if (generos.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp), maxLines = if (abierta) Int.MAX_VALUE else 1) {
                generos.forEach { g ->
                    LText(g, LectorType.Label, Modifier.clip(SuaveShape(8.dp)).background(LectorColors.CardHigh).padding(horizontal = 10.dp, vertical = 5.dp))
                }
            }
        }
        Icon(
            if (abierta) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown, if (abierta) "Menos" else "Más",
            tint = LectorColors.Muted, modifier = Modifier.align(Alignment.CenterHorizontally).size(22.dp),
        )
    }
}

/** MangaChapterListItem de Komikku: nombre y, debajo, fecha · scanlator; los leídos, atenuados. */
@Composable
private fun FilaCapitulo(c: CapituloFuente, leido: Boolean) {
    Row(Modifier.fillMaxWidth().alpha(if (leido) 0.38f else 1f).padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            LText(c.nombre.ifBlank { c.url }, LectorType.Body, maxLines = 1)
            val sub = listOfNotNull(
                c.fecha.takeIf { it > 0 }?.let { java.text.DateFormat.getDateInstance(java.text.DateFormat.SHORT).format(java.util.Date(it)) },
                c.scanlator?.ifBlank { null },
            ).joinToString(" • ")
            if (sub.isNotEmpty()) LText(sub, LectorType.Label, Modifier.padding(top = 2.dp), maxLines = 1, color = LectorColors.Muted)
        }
        if (leido) Icon(Icons.Rounded.Check, "Leído", tint = LectorColors.Muted, modifier = Modifier.size(18.dp))
    }
}

private fun iconoEstado(e: Int): ImageVector = when (e) {
    1 -> Icons.Rounded.DateRange
    2, 4 -> Icons.Rounded.Check
    5 -> Icons.Rounded.Close
    else -> Icons.Rounded.Info
}

private fun textoEstado(e: Int) = when (e) {
    1 -> "En emisión"
    2 -> "Completado"
    3 -> "Licenciado"
    4 -> "Publicación finalizada"
    5 -> "Cancelado"
    6 -> "En pausa"
    else -> "Estado desconocido"
}
