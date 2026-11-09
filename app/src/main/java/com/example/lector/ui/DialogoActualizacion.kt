package com.example.lector.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.lector.NuevaVersion
import com.example.lector.NuevaVersion.Paso
import com.example.lector.ui.bitchord.AccionAlerta
import com.example.lector.ui.bitchord.EstadoDescarga
import com.example.lector.ui.bitchord.UpdateAvailableDialog

/** Aviso de versión nueva de la app (ventana de BitChord): novedades, descarga con su avance e instalación encima de la actual. */
@Composable
fun DialogoActualizacion() {
    val v = NuevaVersion.disponible
    if (!NuevaVersion.verDialogo || v == null) return
    val ctx = LocalContext.current
    val paso = NuevaVersion.paso
    val cerrar = { if (NuevaVersion.paso != Paso.DESCARGANDO) NuevaVersion.cerrar() }
    val actualizar = { NuevaVersion.descargarEInstalar(ctx) }
    Dialog(
        onDismissRequest = cerrar,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        UpdateAvailableDialog(
            titulo = "Nueva versión disponible",
            cuerpo = when (paso) {
                Paso.DESCARGANDO -> "Descargando Shiori ${v.nombre}… ${NuevaVersion.porcentaje} %"
                Paso.INSTALANDO -> "Confirma la instalación en la ventana de Android. La app se cerrará y se abrirá ya actualizada."
                Paso.ERROR -> "No se pudo instalar Shiori ${v.nombre}."
                Paso.LISTO -> "Shiori ${v.nombre} (compilación ${v.codigo}) está lista para descargar. Tienes la ${NuevaVersion.nombreActual(ctx)}."
            },
            detalle = if (v.tamano > 0) "${"%.1f".format(v.tamano / 1048576f)} MB · se instala encima, sin perder nada" else null,
            notas = v.notas,
            estado = when (paso) {
                Paso.DESCARGANDO -> EstadoDescarga.Descargando(NuevaVersion.porcentaje / 100f)
                Paso.INSTALANDO -> EstadoDescarga.Instalando
                Paso.ERROR -> EstadoDescarga.Fallo(NuevaVersion.error ?: "No se pudo actualizar")
                Paso.LISTO -> EstadoDescarga.Disponible
            },
            acciones = when (paso) {
                Paso.DESCARGANDO -> listOf(AccionAlerta("Descargando…", destacada = false, activa = false) {})
                Paso.LISTO -> listOf(AccionAlerta("Actualizar", true, alPulsar = actualizar), AccionAlerta("Más tarde", false, alPulsar = cerrar))
                Paso.INSTALANDO, Paso.ERROR ->
                    listOf(AccionAlerta("Reintentar", true, alPulsar = actualizar), AccionAlerta("Más tarde", false, alPulsar = cerrar))
            },
            onDismiss = cerrar,
        )
    }
}
