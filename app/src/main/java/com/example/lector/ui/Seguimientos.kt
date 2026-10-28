package com.example.lector.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.lector.Seguidores
import kotlinx.coroutines.launch

/** Cuentas de MyAnimeList, Kitsu y MangaUpdates (Ajustes → Seguimiento). */
@Composable
fun CuentasSeguimiento() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var version by remember { mutableIntStateOf(0) }
    var entrando by remember { mutableStateOf<Seguidores.Servicio?>(null) }
    @Suppress("UNUSED_VARIABLE") val v = version
    Seguidores.todos.forEach { s ->
        val con = s.conectado(ctx)
        FilaAjuste(if (con) Icons.Rounded.Close else Icons.Rounded.Add, s.nombre, if (con) "Conectado · toca para salir" else "Toca para conectar") {
            if (con) { s.salir(ctx); version++ } else entrando = s
        }
    }
    entrando?.let { s ->
        var usuario by remember(s) { mutableStateOf("") }
        var clave by remember(s) { mutableStateOf("") }
        var ocupado by remember(s) { mutableStateOf(false) }
        val esMal = s === Seguidores.mal
        DialogoShiori(
            "Conectar ${s.nombre}", { entrando = null },
            aceptar = (if (esMal) "Autorizar" else "Entrar") to {
                if (esMal) {
                    if (usuario.isBlank()) Avisos.mostrar("Pega tu Client ID de MyAnimeList")
                    else { Seguidores.mal.autorizar(ctx, usuario); entrando = null }
                } else if (!ocupado) {
                    ocupado = true
                    scope.launch {
                        runCatching {
                            if (s === Seguidores.kitsu) Seguidores.kitsu.entrar(ctx, usuario, clave) else Seguidores.mangaUpdates.entrar(ctx, usuario, clave)
                        }.onSuccess { Tacto.confirmar(); Avisos.mostrar("${s.nombre} conectado"); entrando = null; version++ }
                            .onFailure { Avisos.mostrar("No se pudo conectar: ${it.message}") }
                        ocupado = false
                    }
                }
            },
        ) {
            if (esMal) {
                LText(
                    "MyAnimeList pide que cada app tenga su Client ID. Créalo gratis en myanimelist.net/apiconfig (tipo «Android», redirección «lector://mal») y pégalo aquí.",
                    LectorType.Label, Modifier.padding(horizontal = 10.dp, vertical = 6.dp), color = LectorColors.Muted,
                )
                Box(Modifier.padding(horizontal = 10.dp)) { CampoBusqueda(usuario, { usuario = it }, "Client ID") }
            } else {
                Box(Modifier.padding(horizontal = 10.dp, vertical = 4.dp)) { CampoBusqueda(usuario, { usuario = it }, "Usuario o correo") }
                Box(Modifier.padding(horizontal = 10.dp, vertical = 4.dp)) { CampoBusqueda(clave, { clave = it }, "Contraseña") }
            }
            if (ocupado) CircularProgressIndicator(Modifier.padding(10.dp))
        }
    }
}

/** En la ficha: vincular este manga con una serie de cada servicio conectado. */
@Composable
fun DialogoServicios(comicId: String, titulo: String, alCerrar: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var version by remember { mutableIntStateOf(0) }
    var buscando by remember { mutableStateOf<Seguidores.Servicio?>(null) }
    var q by remember { mutableStateOf(titulo) }
    var resultados by remember { mutableStateOf<List<Seguidores.Resultado>?>(null) }
    @Suppress("UNUSED_VARIABLE") val v = version
    DialogoShiori("Seguimiento", alCerrar) {
        val conectados = Seguidores.todos.filter { it.conectado(ctx) }
        if (conectados.isEmpty()) LText("Conecta MyAnimeList, Kitsu o MangaUpdates en Ajustes → Seguimiento.", LectorType.Body, Modifier.padding(10.dp), color = LectorColors.Muted)
        conectados.forEach { s ->
            val vin = Seguidores.vinculo(ctx, s, comicId)
            OpcionPildora(if (vin != null) "${s.nombre}: ${vin.titulo}" else "${s.nombre}: vincular…", vin != null) {
                if (vin != null) { Seguidores.vincular(ctx, s, comicId, null); version++ }
                else { buscando = s; resultados = null }
            }
        }
        buscando?.let { s ->
            Spacer(Modifier.height(10.dp))
            Row(Modifier.padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { CampoBusqueda(q, { q = it }, "Buscar en ${s.nombre}") }
                Spacer(Modifier.width(8.dp))
                BotonRedondoTema(Icons.Rounded.Search, "Buscar", {
                    scope.launch { resultados = runCatching { s.buscar(ctx, q) }.getOrElse { Avisos.mostrar("Error: ${it.message}"); emptyList() } }
                })
            }
            resultados?.forEach { r ->
                Row(
                    Modifier.fillMaxWidth().pressable {
                        Seguidores.vincular(ctx, s, comicId, Seguidores.Vinculo(r.id, r.titulo, 0))
                        Tacto.confirmar(); buscando = null; version++
                    }.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ImagenRemota(r.portada, Modifier.width(40.dp).aspectRatio(2f / 3f).clip(SuaveShape(8.dp)))
                    Spacer(Modifier.width(12.dp))
                    LText(r.titulo, LectorType.Body, maxLines = 2)
                }
            }
        }
    }
}
