package com.example.lector.ui.orbes

import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

/**
 * Orbe de thinking-orbs (Jakub Antalik, MIT). Como el componente web: un reloj compartido, tamaño 64 o 20 elegido por
 * el tamaño real (cada uno es un diseño propio, no una escala), monocromo con la tinta invertida en fondos oscuros, y
 * un fotograma quieto (t = 0,6) con «Quitar animaciones». Solo se repinta el lienzo: no recompone nada por fotograma.
 * [tinte]: si no es null, los puntos van del fondo a ese color en vez de en grises.
 */
@Composable
fun OrbePensante(
    estado: EstadoOrbe,
    modifier: Modifier = Modifier,
    oscuro: Boolean = true,
    tinte: Color? = null,
    velocidad: Double = 1.0,
    quieto: Boolean = false,
) {
    val reloj = remember { mutableDoubleStateOf(0.6) }
    if (!quieto) LaunchedEffect(Unit) { while (true) withFrameNanos { reloj.doubleValue = it / 1e9 } }
    Spacer(
        modifier.semantics { contentDescription = estado.etiqueta }.drawBehind {
            val lado = size.minDimension
            if (lado <= 0f) return@drawBehind
            // 20 hasta ~36 dp; a partir de ahí el diseño grande (64)
            val tam = if (lado / density < 36f) 20 else 64
            val r = MotorOrbes.resolver(estado, tam)
            val t = if (quieto) 0.6 else reloj.doubleValue * r.velocidad * velocidad
            val f = MotorOrbes.fotograma(r.modo, tam.toDouble(), t, r.opciones)
            val k = lado / tam
            val ox = (size.width - lado) / 2; val oy = (size.height - lado) / 2
            fun color(white: Double, a: Double): Color {
                val w = white.coerceIn(0.0, 1.0).toFloat()
                val v = if (oscuro) 1f - w else w
                val base = if (tinte == null) Color(v, v, v)
                else if (oscuro) lerp(Color.Black, tinte, v) else lerp(tinte, Color.White, v)
                return base.copy(alpha = a.toFloat().coerceIn(0f, 1f))
            }
            for (l in f.lineas) drawLine(
                color(l.white, l.a), Offset(ox + l.x1.toFloat() * k, oy + l.y1.toFloat() * k),
                Offset(ox + l.x2.toFloat() * k, oy + l.y2.toFloat() * k), strokeWidth = l.w.toFloat() * k,
            )
            for (p in f.puntos) drawCircle(color(p.white, p.a), p.r.toFloat() * k, Offset(ox + p.x.toFloat() * k, oy + p.y.toFloat() * k))
        },
    )
}
