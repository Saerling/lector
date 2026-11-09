package com.example.lector.ui.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Si Inicio está en selección múltiple: LectorApp esconde el mini lector y la barra de acciones ocupa su sitio. */
object SeleccionInicio {
    var activa by mutableStateOf(false)
}
