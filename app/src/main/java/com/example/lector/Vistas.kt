package com.example.lector

import android.graphics.BitmapFactory
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

// Cómo se muestra la biblioteca.
enum class Vista { CUADRICULA_COMODA, CUADRICULA_COMPACTA, SOLO_PORTADA, LISTA }

fun nombreVista(v: Vista): String = when (v) {
    Vista.CUADRICULA_COMODA -> "Cuadrícula cómoda (título debajo)"
    Vista.CUADRICULA_COMPACTA -> "Cuadrícula compacta (título sobre la portada)"
    Vista.SOLO_PORTADA -> "Solo portadas"
    Vista.LISTA -> "Lista"
}

// Carga la portada de un cómic (null si no tiene).
@Composable
fun rememberPortada(almacen: Almacen, id: String): ImageBitmap? =
    remember(id) {
        BitmapFactory.decodeFile(almacen.portadaDe(id).path)?.asImageBitmap()
    }

// Etiqueta pequeña sobre la portada: porcentaje si está en curso, ✓ si está terminado.
@Composable
fun InsigniaProgreso(comic: Comic, modifier: Modifier = Modifier) {
    val estado = estadoDe(comic)
    val texto = when (estado) {
        Filtro.TERMINADOS -> "✓"
        Filtro.EN_CURSO -> "${(progresoDe(comic) * 100).toInt()}%"
        else -> return
    }
    Text(
        texto,
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.primary)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        color = MaterialTheme.colorScheme.onPrimary,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
    )
}

// Un cómic en la cuadrícula, en cualquiera de sus tres variantes.
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TarjetaPortada(
    comic: Comic,
    almacen: Almacen,
    vista: Vista,
    seleccionado: Boolean,
    alAbrir: () -> Unit,
    alMantener: () -> Unit,
) {
    val portada = rememberPortada(almacen, comic.id)
    Column(Modifier.combinedClickable(onClick = alAbrir, onLongClick = alMantener)) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .then(
                    if (seleccionado) {
                        Modifier.border(
                            3.dp,
                            MaterialTheme.colorScheme.primary,
                            RoundedCornerShape(12.dp),
                        )
                    } else {
                        Modifier
                    },
                ),
        ) {
            if (portada != null) {
                Image(
                    bitmap = portada,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            // Variante compacta: el título va sobre un degradado oscuro.
            if (vista == Vista.CUADRICULA_COMPACTA) {
                Column(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color(0xDD000000)),
                            ),
                        )
                        .padding(start = 8.dp, top = 24.dp, end = 8.dp, bottom = 8.dp),
                ) {
                    Text(
                        comic.titulo,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                    )
                    if (almacen.mostrarPagina) {
                        Text(
                            comic.etiquetaProgreso(),
                            color = Color(0xCCFFFFFF),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }

            if (almacen.insigniaProgreso) {
                InsigniaProgreso(
                    comic,
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp),
                )
            }

            if (seleccionado) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "✓",
                        color = MaterialTheme.colorScheme.onPrimary,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }

        // Variante cómoda: el título va debajo.
        if (vista == Vista.CUADRICULA_COMODA) {
            Spacer(Modifier.height(6.dp))
            Text(
                comic.titulo,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            if (almacen.mostrarPagina) {
                Text(
                    comic.etiquetaProgreso(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// Un cómic en la vista de lista.
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FilaLista(
    comic: Comic,
    almacen: Almacen,
    seleccionado: Boolean,
    alAbrir: () -> Unit,
    alMantener: () -> Unit,
) {
    val portada = rememberPortada(almacen, comic.id)
    Row(
        Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = alAbrir, onLongClick = alMantener)
            .background(
                if (seleccionado) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                } else {
                    Color.Transparent
                },
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .width(56.dp)
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            if (portada != null) {
                Image(
                    bitmap = portada,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            if (almacen.insigniaProgreso) {
                InsigniaProgreso(
                    comic,
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(4.dp),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                comic.titulo,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            if (almacen.mostrarPagina) {
                Text(
                    comic.etiquetaProgreso(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { progresoDe(comic) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (seleccionado) {
            Spacer(Modifier.width(12.dp))
            Text(
                "✓",
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
