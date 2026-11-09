// Adaptado de BitChord (https://github.com/kushagrasinghx/BitChord, GPL-3.0): ajustes de WebDAV
// (servidor, usuario, contraseña, probar conexión) de app/src/main/java/com/music/bitchord/data/webdav/*. Con los
// componentes de ajustes de Shiori.
package com.example.lector.ui.bitchord

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.lector.TareaCopia
import com.example.lector.bitchord.WebDavAjustes
import com.example.lector.bitchord.WebDavClient
import com.example.lector.bitchord.WebDavCopias
import com.example.lector.ui.Avisos
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.draw.clip
import com.example.lector.ui.DialogoAlerta
import com.example.lector.ui.Divisor
import com.example.lector.ui.FilaAjuste
import com.example.lector.ui.FilaSwitch
import com.example.lector.ui.LText
import com.example.lector.ui.LectorColors
import com.example.lector.ui.LectorType
import com.example.lector.ui.PaginaAjustes
import com.example.lector.ui.TituloSeccion
import com.example.lector.ui.hace
import kotlinx.coroutines.launch

/** Ajustes → Datos → Copias en WebDAV (Nextcloud, ownCloud, NAS…). */
@Composable
fun WebdavScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val ctx = LocalContext.current
    val aj = remember { WebDavAjustes(ctx) }
    val scope = rememberCoroutineScope()
    var url by remember { mutableStateOf(aj.url) }
    var usuario by remember { mutableStateOf(aj.usuario) }
    var clave by remember { mutableStateOf(aj.clave) }
    var carpeta by remember { mutableStateOf(aj.carpeta) }
    var auto by remember { mutableStateOf(aj.autoSubir) }
    var probando by remember { mutableStateOf(false) }
    var estado by remember { mutableStateOf<String?>(null) }
    var ultima by remember { mutableStateOf(aj.ultimaSubida) }
    var copias by remember { mutableStateOf<List<WebDavClient.Entry>?>(null) }
    var confirmar by remember { mutableStateOf<WebDavClient.Entry?>(null) }
    var pedirUltima by remember { mutableStateOf(false) }

    fun guardar() {
        aj.url = url; aj.usuario = usuario; aj.clave = clave; aj.carpeta = carpeta
        WebDavCopias.programar(ctx)
    }
    fun ocupado() = Avisos.mostrar("Ya hay una copia en marcha: espera a que termine")
    fun cargarCopias() {
        if (!aj.configurado) return
        scope.launch { copias = runCatching { WebDavCopias.listar(ctx) }.getOrNull() }
    }
    LaunchedEffect(Unit) { cargarCopias() }
    LaunchedEffect(TareaCopia.estado) { if (TareaCopia.estado == null) { ultima = aj.ultimaSubida; cargarCopias() } }

    PaginaAjustes("Copias en WebDAV", { guardar(); onBack() }) {
        LText(
            "Sube tus copias de seguridad a tu propio servidor WebDAV (Nextcloud, ownCloud, un NAS…) y restáuralas desde ahí. " +
                "La contraseña se queda solo en este teléfono y no va dentro de las copias.",
            LectorType.Label.copy(fontSize = 13.sp, lineHeight = 18.sp), Modifier.padding(horizontal = 24.dp).padding(bottom = 4.dp), color = LectorColors.Muted,
        )
        TituloSeccion("Servidor")
        // Los cuatro campos en una tarjeta de Mochy (la de SettingsGroup): etiqueta pequeña encima y el texto debajo,
        // sin contornos de Material.
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(com.example.lector.ui.SuaveShape(24.dp))
                .background(LectorColors.Card).padding(vertical = 6.dp),
        ) {
            CampoMochy("Dirección", url, { url = it; estado = null }, "https://nube.ejemplo.com/remote.php/dav/files/yo", tipo = KeyboardType.Uri)
            CampoMochy("Usuario", usuario, { usuario = it; estado = null })
            CampoMochy("Contraseña o clave de aplicación", clave, { clave = it; estado = null }, tipo = KeyboardType.Password, oculto = true)
            CampoMochy("Carpeta en el servidor", carpeta, { carpeta = it })
        }
        Spacer(Modifier.height(4.dp))
        FilaAjuste(
            Icons.Rounded.Check, if (probando) "Probando…" else "Probar conexión",
            estado ?: "Comprueba la dirección, el usuario y la contraseña",
        ) {
            if (probando) return@FilaAjuste
            guardar()
            probando = true
            scope.launch {
                val r = WebDavClient.testConnection(url, usuario, clave)
                probando = false
                estado = r.fold({ "Conexión correcta" }, { "Error: ${it.message ?: it.javaClass.simpleName}" })
                Avisos.mostrar(if (r.isSuccess) "Conexión correcta" else "No se pudo conectar")
                if (r.isSuccess) cargarCopias()
            }
        }

        Divisor()
        TituloSeccion("Copias")
        FilaAjuste(
            Icons.Rounded.Share, "Subir una copia ahora",
            if (ultima > 0) "Última subida: ${hace(ultima)}" else "Crea una copia y la sube al servidor",
        ) {
            guardar()
            if (!aj.configurado) { Avisos.mostrar("Escribe primero la dirección del servidor"); return@FilaAjuste }
            if (!TareaCopia.lanzar(ctx, "Subiendo copia a WebDAV") {
                    val nombre = WebDavCopias.subir(ctx, this)
                    TareaCopia.Informe("Copia en WebDAV", listOf("Subida como $nombre", "En ${aj.directorio()}"))
                }
            ) ocupado()
        }
        FilaAjuste(Icons.Rounded.Refresh, "Restaurar la más reciente", "Baja la última copia del servidor y la restaura") {
            guardar()
            if (!aj.configurado) Avisos.mostrar("Escribe primero la dirección del servidor") else pedirUltima = true
        }
        FilaSwitch(
            "Subir las copias automáticas",
            "Cuando se hace una copia automática (Datos → Copias automáticas) también se sube aquí",
            auto,
        ) { auto = it; aj.autoSubir = it; guardar() }

        copias?.takeIf { it.isNotEmpty() }?.let { lista ->
            Divisor()
            TituloSeccion("En el servidor")
            lista.take(20).forEach { e ->
                FilaAjuste(
                    null, e.displayName,
                    buildString {
                        if (e.lastModified > 0) append(hace(e.lastModified))
                        if (e.size > 0) { if (isNotEmpty()) append(" · "); append(android.text.format.Formatter.formatShortFileSize(ctx, e.size)) }
                        if (isNotEmpty()) append(" · ")
                        append("Toca para restaurar")
                    },
                ) { confirmar = e }
            }
        }
        Spacer(Modifier.height(24.dp))
    }

    if (pedirUltima || confirmar != null) {
        val e = confirmar
        DialogoAlerta(
            onDismissRequest = { pedirUltima = false; confirmar = null },
            title = { Text("Restaurar copia") },
            text = { Text("Se restaurará ${e?.displayName ?: "la copia más reciente del servidor"}.") },
            confirmButton = {
                TextButton(onClick = {
                    pedirUltima = false; confirmar = null
                    if (!TareaCopia.lanzar(ctx, "Restaurando desde WebDAV") { WebDavCopias.restaurar(ctx, this, e) }) ocupado()
                }) { Text("Restaurar") }
            },
            dismissButton = { TextButton(onClick = { pedirUltima = false; confirmar = null }) { Text("Cancelar") } },
        )
    }
}

/** Un campo dentro de la tarjeta: etiqueta de 12 sp atenuada y el valor en Inter 15 sp; el cursor en el acento. */
@Composable
private fun CampoMochy(
    etiqueta: String, valor: String, alCambiar: (String) -> Unit, ejemplo: String? = null,
    tipo: KeyboardType = KeyboardType.Text, oculto: Boolean = false,
) {
    val acento = androidx.compose.material3.MaterialTheme.colorScheme.primary
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp)) {
        LText(etiqueta, LectorType.Label.copy(fontSize = 12.sp), color = LectorColors.Muted)
        Spacer(Modifier.height(4.dp))
        Box {
            if (valor.isEmpty() && ejemplo != null) {
                LText(ejemplo, LectorType.Body.copy(fontSize = 15.sp), color = LectorColors.Muted.copy(alpha = 0.6f), maxLines = 1)
            }
            androidx.compose.foundation.text.BasicTextField(
                valor, alCambiar, Modifier.fillMaxWidth(), singleLine = true,
                textStyle = LectorType.Body.copy(fontSize = 15.sp, color = LectorColors.Text),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(acento),
                visualTransformation = if (oculto) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
                keyboardOptions = KeyboardOptions(keyboardType = tipo),
            )
        }
    }
}
