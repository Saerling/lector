package com.example.lector.ui

import com.example.lector.Comic

/** 0f..1f según la página en la que vas (o los capítulos leídos, si es un manga en línea). */
fun Comic.progresoLectura(): Float =
    if (enLinea) {
        if (paginas > 0) ultimaPagina.toFloat() / paginas else 0f
    } else {
        if (paginas > 1) ultimaPagina.toFloat() / (paginas - 1) else 0f
    }

fun Comic.textoPagina(): String = etiquetaProgreso()

fun Comic.enCurso(): Boolean =
    if (enLinea) ultimaPagina in 1 until paginas
    else leido > 0 && ultimaPagina > 0 && ultimaPagina < paginas - 1

fun Comic.terminado(): Boolean =
    if (enLinea) paginas > 0 && ultimaPagina >= paginas
    else leido > 0 && paginas > 0 && ultimaPagina >= paginas - 1
