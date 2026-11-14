package com.example.lector.ext

import com.example.lector.ui.orbes.EstadoOrbe
import com.example.lector.ui.orbes.MotorOrbes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** El port en Kotlin debe dar los mismos puntos que thinking-orbs (spec/orbs-golden.json, tolerancia 1e-4). */
class OrbesTest {
    @Test
    fun `coincide con los valores de referencia de thinking-orbs`() {
        val lineas = javaClass.classLoader!!.getResourceAsStream("orbes-golden.txt")!!.bufferedReader().readLines()
        var casos = 0
        val fallos = ArrayList<String>()
        var i = 0
        while (i + 2 < lineas.size) {
            val (estado, tam, t, nDots, nLines) = lineas[i].split(' ')
            fun nums(s: String) = if (s.isBlank()) DoubleArray(0) else s.split(' ').map { it.toDouble() }.toDoubleArray()
            val dots = nums(lineas[i + 1]); val lines = nums(lineas[i + 2]); i += 3
            val r = MotorOrbes.resolver(EstadoOrbe.valueOf(estado.uppercase()), tam.toInt())
            val f = MotorOrbes.fotograma(r.modo, tam.toDouble(), t.toDouble(), r.opciones)
            val clave = "$estado-$tam-$t"
            casos++
            if (f.puntos.size != nDots.toInt() || f.lineas.size != nLines.toInt()) {
                fallos += "$clave: ${f.puntos.size}/${f.lineas.size} puntos/líneas, se esperaban $nDots/$nLines"; continue
            }
            var peor = 0.0
            f.puntos.forEachIndexed { k, p ->
                val e = doubleArrayOf(p.x, p.y, p.z, p.r, p.white, p.a)
                for (j in 0 until 6) peor = maxOf(peor, abs(e[j] - dots[k * 6 + j]))
            }
            f.lineas.forEachIndexed { k, l ->
                val e = doubleArrayOf(l.x1, l.y1, l.x2, l.y2, l.white, l.a, l.w)
                for (j in 0 until 7) peor = maxOf(peor, abs(e[j] - lines[k * 7 + j]))
            }
            if (peor > 1e-4) fallos += "$clave: diferencia máxima $peor"
        }
        assertEquals(72, casos)
        assertTrue(fallos.joinToString("\n"), fallos.isEmpty())
    }
}
