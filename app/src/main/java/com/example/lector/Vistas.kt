package com.example.lector

// Cómo se muestra la biblioteca.
enum class Vista { CUADRICULA_COMODA, CUADRICULA_COMPACTA, SOLO_PORTADA, LISTA }

fun nombreVista(v: Vista): String = when (v) {
    Vista.CUADRICULA_COMODA -> "Cuadrícula cómoda (título debajo)"
    Vista.CUADRICULA_COMPACTA -> "Cuadrícula compacta (título sobre la portada)"
    Vista.SOLO_PORTADA -> "Solo portadas"
    Vista.LISTA -> "Lista"
}
