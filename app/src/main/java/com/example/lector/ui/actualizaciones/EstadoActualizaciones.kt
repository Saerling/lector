package com.example.lector.ui.actualizaciones

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.time.LocalDate

/**
 * Estado de la pestaña Actualizaciones que tiene que sobrevivir a que su contenido salga de la composición (al abrir
 * un manga, la pestaña se desmonta y `remember`/`rememberSaveable` se pierden). Solo se reinicia cuando el usuario
 * cierra «Próximos».
 */
object EstadoActualizaciones {
    /** ¿Se está viendo la pantalla «Próximos»? */
    var verProximos by mutableStateOf(false)

    /** Día elegido en el calendario (null: se ven todos). */
    var diaSeleccionado by mutableStateOf<LocalDate?>(null)

    /** Página del calendario (mes) y desplazamiento de la lista de «Próximos». */
    var paginaMes: Int? = null
    var listaIndice = 0
    var listaDesplazamiento = 0

    /** Cálculo de «Próximos» guardado: se rehace solo si cambia la versión de la biblioteca. */
    var proximos by mutableStateOf<List<MangaProximo>?>(null)
    var versionProximos = Int.MIN_VALUE

    fun abrirProximos() { verProximos = true }

    fun cerrarProximos() {
        verProximos = false
        diaSeleccionado = null
        paginaMes = null
        listaIndice = 0
        listaDesplazamiento = 0
    }
}
