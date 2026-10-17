package com.example.lector

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun LectorScreen(comic: Comic, almacen: Almacen, alCerrar: () -> Unit) {
    val archivo = remember(comic.id) { almacen.archivoDe(comic.id) }
    var paginas by remember(comic.id) { mutableStateOf<List<String>?>(null) }
    var rtl by remember { mutableStateOf(almacen.derechaAIzquierda) }

    LaunchedEffect(comic.id) {
        paginas = withContext(Dispatchers.IO) { Cbz.paginas(archivo) }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        val lista = paginas
        if (lista == null) {
            CircularProgressIndicator(Modifier.align(Alignment.Center))
        } else {
            val estado = rememberPagerState(
                initialPage = comic.ultimaPagina.coerceIn(0, lista.size - 1),
            ) { lista.size }

            LaunchedEffect(estado.currentPage) {
                almacen.guardarProgreso(comic.id, estado.currentPage)
            }

            HorizontalPager(
                state = estado,
                reverseLayout = rtl,
                modifier = Modifier.fillMaxSize(),
            ) { indice ->
                Pagina(archivo, lista[indice])
            }

            Row(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color(0xCC000000))
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "${estado.currentPage + 1} / ${lista.size}",
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.onBackground,
                )
                TextButton(onClick = {
                    rtl = !rtl
                    almacen.derechaAIzquierda = rtl
                }) { Text(if (rtl) "Manga ←" else "Cómic →") }
                TextButton(onClick = alCerrar) { Text("Cerrar") }
            }
        }
    }
}

@Composable
private fun Pagina(archivo: File, nombre: String) {
    var imagen by remember(nombre) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(nombre) {
        imagen = withContext(Dispatchers.IO) {
            Cbz.decodificar(archivo, nombre, 2000)?.asImageBitmap()
        }
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val img = imagen
        if (img == null) {
            CircularProgressIndicator()
        } else {
            Image(
                bitmap = img,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
