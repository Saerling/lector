package com.example.lector.ui

import com.example.lector.Comic

/** 0f..1f según la página en la que vas. */
fun Comic.progresoLectura(): Float =
    if (paginas > 1) ultimaPagina.toFloat() / (paginas - 1) else 0f

fun Comic.textoPagina(): String = "Pág. ${ultimaPagina + 1} de $paginas"

fun Comic.enCurso(): Boolean = leido > 0 && ultimaPagina > 0 && ultimaPagina < paginas - 1
fun Comic.terminado(): Boolean = leido > 0 && paginas > 0 && ultimaPagina >= paginas - 1
