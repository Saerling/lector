package com.example.lector.ui.orbes

// Port a Kotlin del motor de thinking-orbs (Jakub Antalik, MIT: https://github.com/Jakubantalik/thinking-orbs).
// Geometría pura: (tamaño, t, opciones) → lista de puntos ya ordenada y lista de líneas. Se usa StrictMath (el mismo
// fdlibm que usa V8) para que los números coincidan con los valores de referencia del proyecto (orbs-golden.json).

import java.lang.StrictMath as M

/** Un punto del orbe. [white]: tinta en papel (0 = tinta más oscura); en fondos oscuros se invierte. */
class Punto(val x: Double, val y: Double, val z: Double, var r: Double, val white: Double, val a: Double = 1.0)

class Linea(val x1: Double, val y1: Double, val x2: Double, val y2: Double, val white: Double, val a: Double, val w: Double)

class Fotograma(val puntos: List<Punto>, val lineas: List<Linea>)

/** Los nueve estados de thinking-orbs. [etiqueta] = lo que lee TalkBack. */
enum class EstadoOrbe(val modo: String, val etiqueta: String) {
    WORKING("orbits", "Trabajando…"),
    SEARCHING("globe", "Buscando…"),
    SOLVING("rubik", "Resolviendo…"),
    LISTENING("wave", "Escuchando…"),
    CONNECTING("web", "Conectando…"),
    WEAVING("braid", "Entrelazando…"),
    COMPOSING("ribbon", "Componiendo…"),
    BREATHING("ring", "Pensando…"),
    SHAPING("morph", "Dando forma…"),
}

typealias Opciones = Map<String, Double>

/** Un estado resuelto para un tamaño (64 o 20): su velocidad y opciones ya escaladas. */
class Resuelto(val modo: String, val velocidad: Double, val opciones: Opciones)

object MotorOrbes {
    // ---------- utilidades (core.ts) ----------
    private fun lerp(a: Double, b: Double, f: Double) = a + (b - a) * f
    private fun frac(x: Double) = x - M.floor(x)

    fun hashD(a: Double, b: Double): Double {
        val h = M.sin(a * 12.9898 + b * 78.233) * 43758.5453
        return h - M.floor(h)
    }

    private fun vnoise(x: Double, y: Double): Double {
        val xi = M.floor(x); val yi = M.floor(y)
        var fx = x - xi; var fy = y - yi
        fx = fx * fx * (3 - 2 * fx); fy = fy * fy * (3 - 2 * fy)
        val a = hashD(xi, yi); val b = hashD(xi + 1, yi); val c = hashD(xi, yi + 1); val d = hashD(xi + 1, yi + 1)
        return a + (b - a) * fx + (c - a) * fy + (a - b - c + d) * fx * fy
    }

    private val ORO = M.PI * (3 - M.sqrt(5.0))
    private fun fib(i: Int, n: Int): DoubleArray {
        val y = 1 - (2.0 * (i + 0.5)) / n
        val rad = M.sqrt(1 - y * y)
        val a = i * ORO
        return doubleArrayOf(rad * M.cos(a), y, rad * M.sin(a))
    }

    private fun anguloDelta(a: Double, b: Double) = M.atan2(M.sin(a - b), M.cos(a - b))

    /** Giro + inclinación + proyección ortográfica (makeProj). */
    private class Proy(yaw: Double, tilt: Double, val cx: Double, val cy: Double, val s: Double) {
        val st = M.sin(tilt); val ct = M.cos(tilt); val sy = M.sin(yaw); val cyw = M.cos(yaw)
        var px = 0.0; var py = 0.0; var pz = 0.0
        fun p(x: Double, y: Double, z: Double) {
            val x1 = x * cyw + z * sy
            val z1 = -x * sy + z * cyw
            val y1 = y * ct - z1 * st
            pz = y * st + z1 * ct
            px = cx + x1 * s; py = cy - y1 * s
        }
    }

    private fun rs(size: Double, pow: Double) = M.pow(size / 300, pow)

    private fun Opciones.o(k: String, def: Double) = this[k] ?: def

    private fun finalizar(puntos: MutableList<Punto>, lineas: List<Linea>, rMin: Double?): Fotograma {
        val min = rMin ?: 0.3
        val vis = ArrayList<Punto>(puntos.size)
        for (d in puntos) { if (d.a < 0.02) continue; d.r = maxOf(min, d.r); vis += d }
        vis.sortBy { it.z } // estable, como Array.sort de V8
        return Fotograma(vis, lineas.filter { it.a >= 0.02 })
    }

    // ---------- modos ----------
    fun fotograma(modo: String, size: Double, t: Double, o: Opciones): Fotograma = when (modo) {
        "orbits" -> orbitas(size, t, o)
        "globe" -> globo(size, t, o)
        "rubik" -> rubik(size, t, o)
        "wave" -> onda(size, t, o)
        "web" -> red(size, t, o)
        "braid" -> trenza(size, t, o)
        "ribbon", "ring" -> cinta(size, t, o)
        else -> morph(size, t, o)
    }

    private fun orbitas(size: Double, t: Double, o: Opciones): Fotograma {
        val c = size / 2; val R = (size / 2) * 0.82
        val pt = Proy(t * 0.12, 0.3, c, c, 1.0)
        val rs = rs(size, o.o("rsPow", 0.6))
        val dots = ArrayList<Punto>()
        val orbitN = o.o("orbitN", 12.0).toInt(); val ghostN = o.o("ghostN", 40.0).toInt(); val particles = o.o("particles", 3.0).toInt()
        for (orb in 0 until orbitN) {
            val h1 = hashD(orb.toDouble(), 1.7); val h2 = hashD(orb.toDouble(), 5.2); val h3 = hashD(orb.toDouble(), 8.9)
            val ro = R * (0.45 + 0.52 * h1)
            val th = h1 * 2 * M.PI
            val phi = M.acos(2 * h2 - 1)
            val nx = M.sin(phi) * M.cos(th); val ny = M.cos(phi); val nz = M.sin(phi) * M.sin(th)
            var ux = -ny; var uy = nx; val uz = 0.0
            val ul = maxOf(1e-6, M.sqrt(ux * ux + uy * uy))
            ux /= ul; uy /= ul
            val vx = ny * uz - nz * uy; val vy = nz * ux - nx * uz; val vz = nx * uy - ny * ux
            val speed = (0.25 + 0.55 * h3) * (if (h3 > 0.5) 1 else -1)
            for (k in 0 until ghostN) {
                val a = (k.toDouble() / ghostN) * 2 * M.PI
                pt.p((ux * M.cos(a) + vx * M.sin(a)) * ro, (uy * M.cos(a) + vy * M.sin(a)) * ro, (uz * M.cos(a) + vz * M.sin(a)) * ro)
                val depth = (pt.pz / ro + 1) / 2
                dots += Punto(pt.px, pt.py, pt.pz, o.o("ghostR", 0.9) * rs, 0.72, o.o("ghostA", 0.5) * (0.4 + 0.6 * depth))
            }
            for (m in 0 until particles) {
                val a = t * speed + (m.toDouble() / particles) * 2 * M.PI + h2 * 6
                pt.p((ux * M.cos(a) + vx * M.sin(a)) * ro, (uy * M.cos(a) + vy * M.sin(a)) * ro, (uz * M.cos(a) + vz * M.sin(a)) * ro)
                val depth = (pt.pz / ro + 1) / 2
                dots += Punto(pt.px, pt.py, pt.pz, (o.o("partR", 1.2) + o.o("partRDepth", 1.6) * depth) * rs, 0.3 - 0.22 * depth)
            }
        }
        return finalizar(dots, emptyList(), o["rMin"])
    }

    private fun lonCount(cosLat: Double, dens: Int) = maxOf(1, Math.round(M.abs(cosLat) * dens).toInt())

    private fun globo(size: Double, t: Double, o: Opciones): Fotograma {
        val spin = 0.5; val c = size / 2; val radius = (size / 2) * 0.82
        val tilt = 0.4 + 0.06 * M.sin(t * 0.35)
        val pt = Proy(t * spin, tilt, c, c, radius)
        val scan = t * (spin + (1.7 - spin) * o.o("scanMul", 1.0))
        val rs = rs(size, o.o("rsPow", 0.6)); val dimBase = o.o("dimBase", 1.0)
        val dots = ArrayList<Punto>()
        val latRings = o.o("latRings", 17.0).toInt(); val lonDensity = o.o("lonDensity", 44.0).toInt()
        for (li in 0..latRings) {
            val lat = -M.PI / 2 + (li.toDouble() / latRings) * M.PI
            val cosLat = M.cos(lat); val sinLat = M.sin(lat)
            val n = lonCount(cosLat, lonDensity)
            for (lj in 0 until n) {
                val lon = (lj.toDouble() / n) * 2 * M.PI
                pt.p(cosLat * M.cos(lon), sinLat, cosLat * M.sin(lon))
                val z = pt.pz; val depth = (z + 1) / 2
                val d = anguloDelta(lon + t * spin, scan)
                val boost = M.exp(-(d * d) / 0.18) * maxOf(0.0, z)
                dots += Punto(
                    pt.px, pt.py, z,
                    (o.o("rBase", 0.6) + o.o("rDepth", 1.7) * depth + o.o("rBoost", 1.0) * boost) * rs,
                    o.o("inkFar", 0.62) - o.o("inkSpan", 0.54) * depth,
                    dimBase + (1 - dimBase) * minOf(1.0, boost),
                )
            }
        }
        return finalizar(dots, emptyList(), o["rMin"])
    }

    private class Mov(val eje: Int, val lo: Double, val hi: Double, val ang: Double)

    private fun rubik(size: Double, t: Double, o: Opciones): Fotograma {
        val c = size / 2; val R = (size / 2) * 0.82
        val pt = Proy(t * 0.55, 0.35 + 0.1 * M.sin(t * 0.9), c, c, R)
        val rs = rs(size, o.o("rsPow", 0.6))
        val count = o.o("moveCount", 14.0).toInt()
        val moves = List(count) { i ->
            val eje = minOf(2, M.floor(hashD(i.toDouble(), 2.3) * 3).toInt())
            val lo = -1.0 + 0.5 * minOf(3, M.floor(hashD(i.toDouble(), 5.9) * 4).toInt())
            val dir = if (hashD(i.toDouble(), 7.7) < 0.5) 1 else -1
            Mov(eje, lo, lo + 0.5, (dir * M.PI) / 2)
        }
        // ciclo del solucionador: mezcla con giros rápidos y deshace en orden inverso
        val slot0 = 0.42; val rest = 1.2
        val cyc = 2 * count * slot0 + rest
        val tc = t % cyc
        val amount = DoubleArray(count)
        var active = -1
        if (tc < 2 * count * slot0) {
            val slot = M.floor(tc / slot0).toInt()
            val p = (tc - slot * slot0) / slot0
            val cl = minOf(1.0, p / 0.7)
            val ep = 1 - M.pow(1 - cl, 3.0)
            if (slot < count) { for (i in 0 until slot) amount[i] = 1.0; amount[slot] = ep; active = slot }
            else { val u = 2 * count - 1 - slot; for (i in 0 until u) amount[i] = 1.0; amount[u] = 1 - ep; active = u }
        }
        val dots = ArrayList<Punto>()
        val latRings = o.o("latRings", 15.0).toInt(); val lonDensity = o.o("lonDensity", 40.0).toInt()
        for (li in 0..latRings) {
            val lat = -M.PI / 2 + (li.toDouble() / latRings) * M.PI
            val cosLat = M.cos(lat); val sinLat = M.sin(lat)
            val n = lonCount(cosLat, lonDensity)
            for (lj in 0 until n) {
                val lon = (lj.toDouble() / n) * 2 * M.PI
                var x = cosLat * M.cos(lon); var y = sinLat; var z = cosLat * M.sin(lon)
                var enActivo = false
                for (i in moves.indices) {
                    if (amount[i] <= 0) continue
                    val mv = moves[i]
                    val coord = when (mv.eje) { 0 -> x; 1 -> y; else -> z }
                    if (coord < mv.lo || coord >= mv.hi) continue
                    if (i == active) enActivo = true
                    val a = mv.ang * amount[i]; val ca = M.cos(a); val sa = M.sin(a)
                    when (mv.eje) {
                        0 -> { val y2 = y * ca - z * sa; z = y * sa + z * ca; y = y2 }
                        1 -> { val x2 = x * ca + z * sa; z = -x * sa + z * ca; x = x2 }
                        else -> { val x2 = x * ca - y * sa; y = x * sa + y * ca; x = x2 }
                    }
                }
                pt.p(x, y, z)
                val depth = (pt.pz + 1) / 2
                dots += Punto(
                    pt.px, pt.py, pt.pz,
                    (o.o("rBase", 0.6) + o.o("rDepth", 1.7) * depth + (if (enActivo) o.o("rActive", 0.3) else 0.0)) * rs,
                    o.o("inkFar", 0.62) - o.o("inkSpan", 0.54) * depth - (if (enActivo) 0.14 else 0.0),
                )
            }
        }
        return finalizar(dots, emptyList(), o["rMin"])
    }

    private fun onda(size: Double, t: Double, o: Opciones): Fotograma {
        val c = size / 2; val R = (size / 2) * 0.874
        val pt = Proy(t * 0.18, 0.38, c, c, 1.0)
        val rs = rs(size, o.o("rsPow", 0.6))
        val dots = ArrayList<Punto>()
        val rings = o.o("rings", 15.0).toInt(); val lonDensity = o.o("lonDensity", 40.0).toInt()
        for (ri in 0..rings) {
            val lat = -M.PI / 2 + (ri.toDouble() / rings) * M.PI
            val cosLat = M.cos(lat); val sinLat = M.sin(lat)
            val w = 0.62 * M.sin(t * 2.1 - ri * 0.52) + 0.38 * M.sin(t * 1.27 + ri * 0.83)
            val rr = R * (0.88 + 0.105 * w)
            val n = lonCount(cosLat, lonDensity)
            for (lj in 0 until n) {
                val lon = (lj.toDouble() / n) * 2 * M.PI
                pt.p(cosLat * M.cos(lon) * rr, sinLat * rr, cosLat * M.sin(lon) * rr)
                val depth = (pt.pz / R + 1) / 2
                val crest = maxOf(0.0, w)
                dots += Punto(
                    pt.px, pt.py, pt.pz,
                    (o.o("rBase", 0.6) + o.o("rDepth", 1.7) * depth) * (1 + 0.4 * crest) * rs,
                    0.66 - 0.56 * depth - 0.1 * crest,
                )
            }
        }
        return finalizar(dots, emptyList(), o["rMin"])
    }

    private fun red(size: Double, t: Double, o: Opciones): Fotograma {
        val c = size / 2; val R = (size / 2) * 0.8 * o.o("spread", 1.0)
        val pt = Proy(t * 0.12, 0.32, c, c, R)
        val rs = rs(size, o.o("rsPow", 0.6))
        val nodeN = o.o("nodeN", 30.0).toInt(); val thr = o.o("thr", 0.72)
        val nodeR = o.o("nodeR", 1.4); val nodeRDepth = o.o("nodeRDepth", 1.8)
        val nodes = Array(nodeN) { i ->
            val d = fib(i, nodeN)
            val x = d[0] + 0.3 * (vnoise(i * 0.31 + 9, t * 0.24) - 0.5) * 2
            val y = d[1] + 0.3 * (vnoise(i * 0.53 + 27, t * 0.21) - 0.5) * 2
            val z = d[2] + 0.3 * (vnoise(i * 0.77 + 55, t * 0.27) - 0.5) * 2
            val l = M.sqrt(x * x + y * y + z * z)
            doubleArrayOf(x / l, y / l, z / l)
        }
        val lines = ArrayList<Linea>(); val dots = ArrayList<Punto>()
        for (i in 0 until nodeN) for (j in i + 1 until nodeN) {
            val dx = nodes[i][0] - nodes[j][0]; val dy = nodes[i][1] - nodes[j][1]; val dz = nodes[i][2] - nodes[j][2]
            val dist = M.sqrt(dx * dx + dy * dy + dz * dz)
            if (dist >= thr) continue
            pt.p(nodes[i][0], nodes[i][1], nodes[i][2]); val x1 = pt.px; val y1 = pt.py; val z1 = pt.pz
            pt.p(nodes[j][0], nodes[j][1], nodes[j][2])
            val depth = ((z1 + pt.pz) / 2 + 1) / 2
            lines += Linea(x1, y1, pt.px, pt.py, 0.42, (1 - dist / thr) * (0.3 + 0.55 * depth), maxOf(0.6, o.o("lineW", 0.8) * rs))
        }
        for (i in 0 until nodeN) {
            pt.p(nodes[i][0], nodes[i][1], nodes[i][2])
            val depth = (pt.pz + 1) / 2
            val pulse = 1 + 0.25 * M.sin(t * 1.4 + i * 2.7)
            dots += Punto(pt.px, pt.py, pt.pz, (nodeR + nodeRDepth * depth) * pulse * rs, 0.55 - 0.45 * depth)
        }
        val signals = o.o("signals", 5.0).toInt()
        for (s in 0 until signals) {
            val seg = M.floor(t * 0.55 + s * 7.31)
            val a = M.floor(hashD(seg, s * 3.1 + 1.7) * nodeN).toInt()
            val b = M.floor(hashD(seg, s * 5.7 + 4.2) * nodeN).toInt()
            if (a == b) continue
            val f = frac(t * 0.55 + s * 7.31)
            val x = lerp(nodes[a][0], nodes[b][0], f); val y = lerp(nodes[a][1], nodes[b][1], f); val z = lerp(nodes[a][2], nodes[b][2], f)
            val l = maxOf(1e-6, M.sqrt(x * x + y * y + z * z))
            pt.p(x / l, y / l, z / l)
            val depth = (pt.pz + 1) / 2
            dots += Punto(pt.px, pt.py, pt.pz, (nodeR * 1.5 + nodeRDepth * depth) * rs, 0.05, 0.5 + 0.5 * depth)
        }
        return finalizar(dots, lines, o["rMin"])
    }

    private fun esferaFantasma(dots: MutableList<Punto>, pt: Proy, R: Double, n: Int, rs: Double) {
        for (i in 0 until n) {
            val d = fib(i, n)
            pt.p(d[0] * R, d[1] * R, d[2] * R)
            val depth = (pt.pz / R + 1) / 2
            dots += Punto(pt.px, pt.py, pt.pz, 0.8 * rs, 0.78, 0.1 + 0.22 * depth)
        }
    }

    private fun trenza(size: Double, t: Double, o: Opciones): Fotograma {
        val c = size / 2; val R = (size / 2) * 0.76
        val pt = Proy(t * 0.4, 0.3, c, c, 1.0)
        val rs = rs(size, o.o("rsPow", 0.6))
        val dots = ArrayList<Punto>()
        esferaFantasma(dots, pt, R, o.o("ghostN", 150.0).toInt(), rs)
        val strandN = o.o("strandN", 52.0).toInt(); val turns = o.o("turns", 3.0)
        for (s in 0 until 3) {
            val phase = (s / 3.0) * 2 * M.PI
            for (i in 0 until strandN) {
                val u = (frac(i.toDouble() / strandN + t * 0.045) * 2 - 1) * 0.96
                val surf = M.sqrt(maxOf(0.0, 1 - u * u))
                val endFade = minOf(1.0, (1 - M.abs(u)) / 0.1)
                val a = u * M.PI * turns + phase
                val weave = 1 + 0.075 * M.sin(u * M.PI * turns * 2 + phase * 2 + t * 0.8)
                val rr = surf * R * weave
                pt.p(M.cos(a) * rr, u * R * weave, M.sin(a) * rr)
                val depth = (pt.pz / R + 1) / 2
                dots += Punto(pt.px, pt.py, pt.pz, (o.o("rBase", 1.2) + o.o("rDepth", 1.8) * depth) * rs, 0.55 - 0.45 * depth, endFade * (0.45 + 0.55 * depth))
            }
        }
        return finalizar(dots, emptyList(), o["rMin"])
    }

    private fun cinta(size: Double, t: Double, o: Opciones): Fotograma {
        val c = size / 2; val R = (size / 2) * 0.78
        val spin = o.o("spin", 1.0); val camTilt = 0.3
        val pt = Proy(t * 0.1 * spin, camTilt, c, c, 1.0)
        val rs = rs(size, o.o("rsPow", 0.6))
        val dots = ArrayList<Punto>()
        esferaFantasma(dots, pt, R, o.o("ghostN", 150.0).toInt(), rs)
        val faceOn = (o["faceOn"] ?: 0.0) != 0.0
        val ya = t * 0.24 * spin
        val ta = if (faceOn) -camTilt else 0.55 + 0.3 * M.sin(t * 0.18) * spin
        val ux = M.cos(ya); val uy = 0.0; val uz = M.sin(ya)
        val vx = -uz * M.sin(ta); val vy = M.cos(ta); val vz = ux * M.sin(ta)
        val nx = uy * vz - uz * vy; val ny = uz * vx - ux * vz; val nz = ux * vy - uy * vx
        val wobMul = o.o("wobMul", 1.0)
        val wobAmp = 0.23 * wobMul
        val baseR = if (faceOn) R / (1 + 0.85 * wobAmp) else R
        val segs = o.o("segs", 88.0).toInt()
        val lanes = maxOf(1, Math.round(o.o("lanes", 5.0) * o.o("bandMul", 1.0)).toInt())
        for (w in 0 until lanes) {
            val laneOff = (w - (lanes - 1) / 2.0) * 0.075
            val edge = M.abs(w - (lanes - 1) / 2.0) / maxOf(1.0, (lanes - 1) / 2.0)
            for (k in 0 until segs) {
                val a = (k.toDouble() / segs) * 2 * M.PI
                val wob = (0.16 * M.sin(a * 3 - t * 1.7 + w * 0.22) + 0.07 * M.sin(a * 5 + t * 1.1)) * wobMul
                val radial = if (faceOn) 1 + wob else 1.0
                val off = if (faceOn) laneOff else laneOff + wob
                val x = ux * M.cos(a) + vx * M.sin(a) + nx * off
                val y = uy * M.cos(a) + vy * M.sin(a) + ny * off
                val z = uz * M.cos(a) + vz * M.sin(a) + nz * off
                val l = M.sqrt(x * x + y * y + z * z)
                val rr = baseR * radial
                pt.p((x / l) * rr, (y / l) * rr, (z / l) * rr)
                val depth = (pt.pz / R + 1) / 2
                dots += Punto(
                    pt.px, pt.py, pt.pz,
                    (o.o("rBase", 1.1) + o.o("rDepth", 1.7) * depth) * (1 - 0.25 * edge) * rs,
                    0.52 - 0.44 * depth + 0.18 * edge, 0.4 + 0.6 * depth,
                )
            }
        }
        return finalizar(dots, emptyList(), o["rMin"])
    }

    // morph: círculo → triángulo → cuadrado, con los puntos repartidos por igual a lo largo del contorno
    private fun interface Camino { fun en(f: Double): DoubleArray }

    private fun poligono(v: Array<DoubleArray>): Camino {
        val n = v.size
        val L = DoubleArray(n) { i -> val a = v[i]; val b = v[(i + 1) % n]; M.hypot(b[0] - a[0], b[1] - a[1]) }
        val total = L.sum()
        return Camino { f ->
            var target = f * total; var i = 0
            while (target > L[i] && i < n - 1) { target -= L[i]; i++ }
            val a = v[i]; val b = v[(i + 1) % n]
            val ff = if (L[i] != 0.0) minOf(1.0, target / L[i]) else 0.0
            doubleArrayOf(a[0] + (b[0] - a[0]) * ff, a[1] + (b[1] - a[1]) * ff)
        }
    }

    private val CICLO = arrayOf(
        Camino { f -> val a = -M.PI / 2 + f * 2 * M.PI; doubleArrayOf(M.cos(a) * 0.24, M.sin(a) * 0.24) },
        poligono(arrayOf(doubleArrayOf(0.0, -0.26), doubleArrayOf(0.24, 0.16), doubleArrayOf(-0.24, 0.16))),
        poligono(arrayOf(doubleArrayOf(0.0, -0.2), doubleArrayOf(0.2, -0.2), doubleArrayOf(0.2, 0.2), doubleArrayOf(-0.2, 0.2), doubleArrayOf(-0.2, -0.2))),
    )

    private fun morph(size: Double, t: Double, o: Opciones): Fotograma {
        val HOLD = 1.4; val MORPH = 0.9; val SEG = HOLD + MORPH
        val K = CICLO.size
        val tc = t % (SEG * K)
        val k = M.floor(tc / SEG).toInt()
        val local = tc - k * SEG
        val m = if (local > HOLD) { val x = (local - HOLD) / MORPH; x * x * (3 - 2 * x) } else 0.0
        val sprd = o.o("spread", 1.0)
        val pA = CICLO[k]; val pB = CICLO[(k + 1) % K]
        val N = 160
        val pts = Array(N) { i ->
            val f = i.toDouble() / N
            val a = pA.en(f); val b = pB.en(f)
            doubleArrayOf((a[0] + (b[0] - a[0]) * m) * sprd, (a[1] + (b[1] - a[1]) * m) * sprd)
        }
        val L = DoubleArray(N) { i -> val a = pts[i]; val b = pts[(i + 1) % N]; M.hypot(b[0] - a[0], b[1] - a[1]) }
        var total = 0.0; for (l in L) total += l
        val n = maxOf(6, Math.round(34 * o.o("iconD", 1.0)).toInt())
        val re = o.o("rDot", 0.021) * 1.35 * sprd
        val pulse = 1 + 0.02 * M.sin(local * 3.1)
        val dots = ArrayList<Punto>()
        val c2 = size / 2
        var seg = 0; var acc = 0.0
        for (k2 in 0 until n) {
            val target = (k2.toDouble() / n) * total
            while (acc + L[seg] < target && seg < N - 1) { acc += L[seg]; seg++ }
            val a = pts[seg]; val b = pts[(seg + 1) % N]
            val f = if (L[seg] != 0.0) minOf(1.0, (target - acc) / L[seg]) else 0.0
            val x = (a[0] + (b[0] - a[0]) * f) * pulse
            val y = (a[1] + (b[1] - a[1]) * f) * pulse
            dots += Punto(c2 + x * size, c2 + y * size, 0.0, maxOf(0.35, re * size), 0.1)
        }
        return finalizar(dots, emptyList(), o["rMin"])
    }

    // ---------- perfiles y presets (profiles.ts, presets.ts) ----------
    private val PERFILES: Map<String, Opciones> = mapOf(
        "globe" to mapOf("latRings" to 17.0, "lonDensity" to 44.0, "rBase" to 0.6, "rDepth" to 1.7, "rBoost" to 1.0, "inkFar" to 0.62, "inkSpan" to 0.54, "rsPow" to 0.6, "rMin" to 0.3),
        "orbits" to mapOf("orbitN" to 12.0, "ghostN" to 40.0, "ghostR" to 0.9, "ghostA" to 0.5, "particles" to 3.0, "partR" to 1.2, "partRDepth" to 1.6, "rsPow" to 0.6, "rMin" to 0.3),
        "rubik" to mapOf("latRings" to 15.0, "lonDensity" to 40.0, "moveCount" to 14.0, "rBase" to 0.6, "rDepth" to 1.7, "rActive" to 0.3, "inkFar" to 0.62, "inkSpan" to 0.54, "rsPow" to 0.6, "rMin" to 0.3),
        "wave" to mapOf("rings" to 15.0, "lonDensity" to 40.0, "rBase" to 0.6, "rDepth" to 1.7, "rsPow" to 0.6, "rMin" to 0.3),
        "web" to mapOf("nodeN" to 30.0, "thr" to 0.72, "signals" to 5.0, "nodeR" to 1.4, "nodeRDepth" to 1.8, "lineW" to 0.8, "rsPow" to 0.6, "rMin" to 0.3),
        "braid" to mapOf("strandN" to 52.0, "turns" to 3.0, "ghostN" to 150.0, "rBase" to 1.2, "rDepth" to 1.8, "rsPow" to 0.6, "rMin" to 0.3),
        "ribbon" to mapOf("lanes" to 5.0, "segs" to 88.0, "ghostN" to 150.0, "rBase" to 1.1, "rDepth" to 1.7, "rsPow" to 0.6, "rMin" to 0.3),
        "ring" to mapOf("lanes" to 5.0, "segs" to 88.0, "ghostN" to 0.0, "faceOn" to 1.0, "rBase" to 1.1, "rDepth" to 1.7, "rsPow" to 0.6, "rMin" to 0.3),
        "morph" to mapOf("rDot" to 0.021, "iconD" to 1.0, "rMin" to 0.25),
    )

    private class Preset(val speed: Double, val count: Double, val size: Double, val extra: Opciones = emptyMap())

    private val PRESETS: Map<String, Map<Int, Preset>> = mapOf(
        "orbits" to mapOf(64 to Preset(1.885, 1.0, 1.0), 20 to Preset(3.9, 0.238, 2.4)),
        "globe" to mapOf(64 to Preset(2.015, 0.42, 1.15, mapOf("scanMul" to 4.08, "dimBase" to 0.45)), 20 to Preset(2.665, 0.105, 1.75, mapOf("scanMul" to 4.335, "dimBase" to 0.45))),
        "rubik" to mapOf(64 to Preset(1.82, 0.35, 1.05), 20 to Preset(1.95, 0.088, 1.9)),
        "wave" to mapOf(64 to Preset(4.388, 0.341, 1.0), 20 to Preset(3.998, 0.105, 1.6)),
        "web" to mapOf(64 to Preset(3.315, 1.35, 0.95), 20 to Preset(6.63, 0.25, 1.52)),
        "braid" to mapOf(64 to Preset(1.625, 0.5, 1.0), 20 to Preset(2.75, 0.1125, 1.36)),
        "ribbon" to mapOf(64 to Preset(2.34, 0.25, 0.85, mapOf("spin" to 0.0, "bandMul" to 3.9, "wobMul" to 1.0)), 20 to Preset(3.12, 0.051, 1.073, mapOf("spin" to 0.0, "bandMul" to 4.94, "wobMul" to 1.0))),
        "ring" to mapOf(64 to Preset(3.24, 0.25, 0.956, mapOf("spin" to 0.0, "bandMul" to 3.627, "wobMul" to 0.368)), 20 to Preset(3.78, 0.028, 1.622, mapOf("spin" to 0.0, "bandMul" to 3.968, "wobMul" to 0.565))),
        "morph" to mapOf(64 to Preset(2.405, 0.702, 0.395, mapOf("spread" to 1.45)), 20 to Preset(2.08, 0.53, 1.011, mapOf("spread" to 1.45))),
    )

    private fun escalarCuentas(o: Opciones, s: Double): Opciones {
        val out = o.toMutableMap(); val hecho = HashSet<String>(); val rt = M.sqrt(s)
        for ((a, b) in listOf("latRings" to "lonDensity", "rings" to "lonDensity", "lanes" to "segs")) {
            val va = out[a]; val vb = out[b]
            if (va != null && vb != null && a !in hecho && b !in hecho) {
                out[a] = maxOf(2L, Math.round(va * rt)).toDouble(); out[b] = maxOf(2L, Math.round(vb * rt)).toDouble()
                hecho += a; hecho += b
            }
        }
        for (k in listOf("orbitN", "ghostN", "nodeN", "strandN", "signals")) {
            val v = out[k]
            if (v != null && v != 0.0 && k !in hecho) out[k] = maxOf(1L, Math.round(v * s)).toDouble()
        }
        out["iconD"]?.let { out["iconD"] = maxOf(0.02, it * s) }
        return out
    }

    private fun escalarRadios(o: Opciones, s: Double): Opciones {
        val out = o.toMutableMap()
        for (k in listOf("rBase", "rDepth", "rActive", "rDot", "ghostR", "partR", "partRDepth", "nodeR", "nodeRDepth")) out[k]?.let { out[k] = it * s }
        out["rSizeMul"] = (out["rSizeMul"] ?: 1.0) * s
        return out
    }

    private val cache = HashMap<String, Resuelto>()

    /** Estado + tamaño (64 o 20) → modo, velocidad y opciones ya escaladas (resolvePreset). */
    @Synchronized
    fun resolver(estado: EstadoOrbe, tam: Int): Resuelto = cache.getOrPut("${estado.name}-$tam") {
        val modo = estado.modo
        val p = PRESETS.getValue(modo).getValue(tam)
        var o: Opciones = PERFILES.getValue(modo)
        if (p.count != 1.0) o = escalarCuentas(o, p.count)
        if (p.size != 1.0) o = escalarRadios(o, p.size)
        if (p.extra.isNotEmpty()) o = o + p.extra
        Resuelto(modo, p.speed, o)
    }
}
