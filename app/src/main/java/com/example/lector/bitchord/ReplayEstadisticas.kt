// Adaptado de BitChord (https://github.com/kushagrasinghx/BitChord, GPL-3.0): app/src/main/java/com/music/bitchord/data/stats/ListeningStats.kt
// y ListeningRecorder.kt (ReplayPeriod, ReplaySummary, rankings, día más activo, hora pico). En Shiori el registro son
// capítulos abiertos (sacados del historial de lectura) en vez de canciones escuchadas; todo se queda en el teléfono.
package com.example.lector.bitchord

import android.content.Context
import androidx.compose.runtime.snapshotFlow
import com.example.lector.Almacen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Locale

/** Un capítulo abierto: lo que en BitChord es una reproducción. */
data class LecturaCap(
    val fuenteId: Long,
    val urlManga: String,
    val titulo: String,
    val portadaUrl: String?,
    val capUrl: String,
    val capNombre: String,
    val cuando: Long,
)

/**
 * Registro de lectura en el dispositivo. El historial de Shiori solo guarda el último capítulo de cada manga, así que
 * aquí se observa ([snapshotFlow]) y cada capítulo nuevo que aparece se anota con su hora. El tiempo leyendo se
 * reparte por días con las diferencias de [Almacen.tiempoLectura].
 */
object ReplayRegistro {
    private const val MAX = 20_000
    private var iniciado = false
    private var archivo: File? = null
    private val lecturas = ArrayList<LecturaCap>()
    private val claves = HashSet<String>()
    private val tiempoPorDia = HashMap<String, Long>() // "YYYY-MM-DD" -> ms
    private var tiempoVisto = -1L

    private fun clave(f: Long, m: String, c: String) = "$f|$m|$c"

    /** Empieza a observar el historial. Se puede llamar varias veces. */
    fun iniciar(ctx: Context, almacen: Almacen) {
        synchronized(this) {
            if (iniciado) return
            iniciado = true
            archivo = File(ctx.applicationContext.filesDir, "bitchord_replay.json")
            cargar()
        }
        CoroutineScope(SupervisorJob() + Dispatchers.Main).launch {
            snapshotFlow { almacen.historialLinea }.collectLatest { lista ->
                withContext(Dispatchers.IO) {
                    var cambio = false
                    synchronized(this@ReplayRegistro) {
                        lista.forEach { e ->
                            if (e.capUrl.isNotBlank() && claves.add(clave(e.fuenteId, e.urlManga, e.capUrl))) {
                                lecturas += LecturaCap(e.fuenteId, e.urlManga, e.titulo, e.portadaUrl, e.capUrl, e.capNombre, e.leido)
                                cambio = true
                            }
                        }
                    }
                    if (anotarTiempo(almacen)) cambio = true
                    if (cambio) guardar()
                }
            }
        }
    }

    /** Suma al día de hoy lo que creció el tiempo de lectura total desde la última vez. */
    fun anotarTiempo(almacen: Almacen): Boolean = synchronized(this) {
        val total = runCatching { almacen.tiempoLectura }.getOrDefault(0L)
        val antes = tiempoVisto
        tiempoVisto = total
        if (antes < 0 || total <= antes) return false
        val dia = LocalDate.now().toString()
        tiempoPorDia[dia] = (tiempoPorDia[dia] ?: 0L) + (total - antes)
        true
    }

    fun instantanea(): Pair<List<LecturaCap>, Map<String, Long>> = synchronized(this) { lecturas.toList() to tiempoPorDia.toMap() }

    private fun cargar() {
        val f = archivo ?: return
        runCatching {
            if (!f.exists()) return
            val o = JSONObject(f.readText())
            val a = o.optJSONArray("lecturas") ?: JSONArray()
            for (i in 0 until a.length()) {
                val e = a.getJSONObject(i)
                val l = LecturaCap(
                    e.getLong("f"), e.getString("m"), e.optString("t"), e.optString("p").ifBlank { null },
                    e.getString("c"), e.optString("n"), e.getLong("w"),
                )
                if (claves.add(clave(l.fuenteId, l.urlManga, l.capUrl))) lecturas += l
            }
            o.optJSONObject("tiempo")?.let { t -> t.keys().forEach { k -> tiempoPorDia[k] = t.optLong(k) } }
            tiempoVisto = o.optLong("visto", -1L)
        }
    }

    private fun guardar() {
        val f = archivo ?: return
        val texto = synchronized(this) {
            if (lecturas.size > MAX) {
                lecturas.sortBy { it.cuando }
                repeat(lecturas.size - MAX) { lecturas.removeAt(0) }
            }
            val a = JSONArray()
            lecturas.forEach { l ->
                a.put(JSONObject().put("f", l.fuenteId).put("m", l.urlManga).put("t", l.titulo).put("p", l.portadaUrl ?: "")
                    .put("c", l.capUrl).put("n", l.capNombre).put("w", l.cuando))
            }
            val t = JSONObject(); tiempoPorDia.forEach { (k, v) -> t.put(k, v) }
            JSONObject().put("lecturas", a).put("tiempo", t).put("visto", tiempoVisto).toString()
        }
        runCatching {
            val tmp = File(f.parentFile, f.name + ".tmp")
            tmp.writeText(texto)
            tmp.renameTo(f)
        }
    }
}

// Copiado de BitChord: ReplayPeriod (ListeningStats.kt), con etiquetas en español.
enum class ReplayPeriodo(val chip: String) {
    SEMANA("Esta semana"),
    MES("Este mes"),
    ANO("Este año"),
    SIEMPRE("Siempre"),
    ;

    /** El rango (inclusivo) que cubre. Null para SIEMPRE. */
    fun rango(hoy: LocalDate): Pair<LocalDate, LocalDate>? = when (this) {
        SEMANA -> hoy.with(DayOfWeek.MONDAY) to hoy
        MES -> YearMonth.from(hoy).let { it.atDay(1) to it.atEndOfMonth() }
        ANO -> LocalDate.of(hoy.year, 1, 1) to LocalDate.of(hoy.year, 12, 31)
        SIEMPRE -> null
    }

    fun etiqueta(hoy: LocalDate): String = when (this) {
        SEMANA -> "Esta semana"
        MES -> YearMonth.from(hoy).month.getDisplayName(java.time.format.TextStyle.FULL, Locale("es"))
            .replaceFirstChar { it.uppercase(Locale.ROOT) } + " ${hoy.year}"
        ANO -> hoy.year.toString()
        SIEMPRE -> "Desde siempre"
    }
}

data class MangaReplay(val titulo: String, val portadaUrl: String?, val fuenteId: Long, val urlManga: String, val caps: Int)
data class NombreReplay(val nombre: String, val caps: Int)

/** Todo lo que dibuja la pantalla Replay, calculado una vez (como ReplaySummary de BitChord). */
data class ResumenReplay(
    val periodo: ReplayPeriodo,
    val etiqueta: String,
    val capitulos: Int,
    val tiempoMs: Long,
    val mangas: List<MangaReplay>,
    val generos: List<NombreReplay>,
    /** Capítulos por semana (las últimas 12, la más vieja primero), con la etiqueta del lunes. */
    val porSemana: List<Pair<String, Int>>,
    /** Capítulos por mes (los últimos 12). */
    val porMes: List<Pair<String, Int>>,
    val horas: List<Int>,
    val diaMasActivo: LocalDate?,
    val diaMasActivoCaps: Int,
    val rachaActual: Int,
    val rachaMaxima: Int,
    val diasLeyendo: Int,
    val desde: LocalDate?,
) {
    val vacio: Boolean get() = capitulos == 0
    val horaPico: Int? get() = horas.withIndex().filter { it.value > 0 }.maxByOrNull { it.value }?.index
}

object ReplayEstadisticas {

    suspend fun resumen(almacen: Almacen, periodo: ReplayPeriodo): ResumenReplay = withContext(Dispatchers.Default) {
        ReplayRegistro.anotarTiempo(almacen)
        val (todas, tiempo) = ReplayRegistro.instantanea()
        val zona = ZoneId.systemDefault()
        val hoy = LocalDate.now(zona)
        fun dia(ms: Long): LocalDate = Instant.ofEpochMilli(ms).atZone(zona).toLocalDate()
        val rango = periodo.rango(hoy)
        val enRango = if (rango == null) todas else todas.filter { dia(it.cuando).let { d -> d >= rango.first && d <= rango.second } }

        val mangas = enRango.groupBy { it.fuenteId to it.urlManga }.map { (_, l) ->
            val ultima = l.maxBy { it.cuando }
            MangaReplay(ultima.titulo, ultima.portadaUrl, ultima.fuenteId, ultima.urlManga, l.size)
        }.sortedByDescending { it.caps }

        // Géneros: de la biblioteca o, si no está, de la ficha guardada. Cada capítulo vota por los géneros de su manga.
        val biblioteca = runCatching { almacen.cargar() }.getOrDefault(emptyList())
            .filter { it.enLinea }.associateBy { it.fuenteId to it.urlManga }
        val votos = HashMap<String, Pair<String, Int>>()
        mangas.forEach { m ->
            val gen = biblioteca[m.fuenteId to m.urlManga]?.generos?.takeIf { it.isNotEmpty() }
                ?: runCatching { com.example.lector.CacheFichas.leer(m.fuenteId, m.urlManga)?.detalle?.generos }.getOrNull()
                ?: emptyList()
            gen.map { it.trim() }.filter { it.isNotBlank() }.distinctBy { it.lowercase() }.forEach { g ->
                val k = g.lowercase(Locale.ROOT)
                val (n0, c0) = votos[k] ?: (g to 0)
                votos[k] = n0 to c0 + m.caps
            }
        }
        val generos = votos.values.map { NombreReplay(it.first, it.second) }.sortedByDescending { it.caps }

        val lunes = hoy.with(DayOfWeek.MONDAY)
        val porDia = todas.groupingBy { dia(it.cuando) }.eachCount()
        val porSemana = (11 downTo 0).map { i ->
            val ini = lunes.minusWeeks(i.toLong())
            val n = (0L..6L).sumOf { porDia[ini.plusDays(it)] ?: 0 }
            "${ini.dayOfMonth}/${ini.monthValue}" to n
        }
        val mesActual = YearMonth.from(hoy)
        val porMesMapa = todas.groupingBy { YearMonth.from(dia(it.cuando)) }.eachCount()
        val porMes = (11 downTo 0).map { i ->
            val ym = mesActual.minusMonths(i.toLong())
            ym.month.getDisplayName(java.time.format.TextStyle.SHORT, Locale("es")).trimEnd('.') to (porMesMapa[ym] ?: 0)
        }

        val horas = MutableList(24) { 0 }
        enRango.forEach { horas[Instant.ofEpochMilli(it.cuando).atZone(zona).hour]++ }
        val diasRango = enRango.groupingBy { dia(it.cuando) }.eachCount()
        val masActivo = diasRango.maxByOrNull { it.value }

        // Rachas: días seguidos con al menos un capítulo (sobre todo el registro).
        val dias = porDia.keys.sorted()
        var max = 0; var run = 0; var previo: LocalDate? = null
        dias.forEach { d ->
            run = if (previo != null && ChronoUnit.DAYS.between(previo, d) == 1L) run + 1 else 1
            if (run > max) max = run
            previo = d
        }
        var actual = 0
        var d = if (porDia.containsKey(hoy)) hoy else hoy.minusDays(1)
        while (porDia.containsKey(d)) { actual++; d = d.minusDays(1) }

        val tiempoMs = tiempo.entries.sumOf { (k, v) ->
            val fecha = runCatching { LocalDate.parse(k) }.getOrNull() ?: return@sumOf 0L
            if (rango == null || (fecha >= rango.first && fecha <= rango.second)) v else 0L
        }

        ResumenReplay(
            periodo = periodo,
            etiqueta = periodo.etiqueta(hoy),
            capitulos = enRango.size,
            tiempoMs = tiempoMs,
            mangas = mangas,
            generos = generos,
            porSemana = porSemana,
            porMes = porMes,
            horas = horas,
            diaMasActivo = masActivo?.key,
            diaMasActivoCaps = masActivo?.value ?: 0,
            rachaActual = actual,
            rachaMaxima = max,
            diasLeyendo = diasRango.size,
            desde = dias.firstOrNull(),
        )
    }
}
