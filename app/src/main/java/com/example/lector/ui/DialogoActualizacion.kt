package com.example.lector.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.lector.NuevaVersion
import com.example.lector.NuevaVersion.Paso

/** Aviso de versión nueva de la app: novedades, descarga con su avance e instalación encima de la actual. */
@Composable
fun DialogoActualizacion() {
    val v = NuevaVersion.disponible
    if (!NuevaVersion.verDialogo || v == null) return
    val ctx = LocalContext.current
    val paso = NuevaVersion.paso
    DialogoAlerta(
        onDismissRequest = { if (paso != Paso.DESCARGANDO) NuevaVersion.cerrar() },
        title = { Text("Nueva versión disponible") },
        text = {
            Column {
                Text("Shiori ${v.nombre} (compilación ${v.codigo}). Tienes la ${NuevaVersion.nombreActual(ctx)}.")
                if (v.tamano > 0) LText("${"%.1f".format(v.tamano / 1048576f)} MB · se instala encima, sin perder nada", LectorType.Label, color = LectorColors.Muted)
                if (v.notas.isNotBlank()) {
                    Spacer(Modifier.height(12.dp))
                    LText("Novedades", LectorType.Label, color = LectorColors.Muted)
                    Column(Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState())) {
                        LText(v.notas, LectorType.Body)
                    }
                }
                when (paso) {
                    Paso.DESCARGANDO -> {
                        Spacer(Modifier.height(14.dp))
                        LinearProgressIndicator(
                            progress = { NuevaVersion.porcentaje / 100f },
                            modifier = Modifier.fillMaxWidth().clip(SuaveShape(4.dp)),
                        )
                        LText("Descargando… ${NuevaVersion.porcentaje} %", LectorType.Label, color = LectorColors.Muted)
                    }
                    Paso.INSTALANDO -> {
                        Spacer(Modifier.height(14.dp))
                        LText("Confirma la instalación en la ventana de Android. La app se cerrará y se abrirá ya actualizada.", LectorType.Body, color = LectorColors.Muted)
                    }
                    Paso.ERROR -> {
                        Spacer(Modifier.height(14.dp))
                        LText(NuevaVersion.error ?: "No se pudo actualizar", LectorType.Body, color = LectorColors.Error)
                    }
                    Paso.LISTO -> Unit
                }
            }
        },
        confirmButton = {
            TextButton(enabled = paso != Paso.DESCARGANDO, onClick = { NuevaVersion.descargarEInstalar(ctx) }) {
                Text(
                    when (paso) {
                        Paso.LISTO -> "Actualizar"
                        Paso.DESCARGANDO -> "Descargando…"
                        Paso.INSTALANDO, Paso.ERROR -> "Reintentar"
                    },
                )
            }
        },
        dismissButton = {
            TextButton(enabled = paso != Paso.DESCARGANDO, onClick = { NuevaVersion.cerrar() }) { Text("Más tarde") }
        },
    )
}
