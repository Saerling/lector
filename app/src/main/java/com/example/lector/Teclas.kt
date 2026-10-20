package com.example.lector

import kotlinx.coroutines.flow.MutableSharedFlow

// Las teclas de volumen las recibe la actividad y las reenvía al visor.
// +1 = página siguiente (bajar volumen), -1 = anterior (subir volumen).
object Teclas {
    @Volatile
    var activo = false
    val eventos = MutableSharedFlow<Int>(extraBufferCapacity = 8)
}
