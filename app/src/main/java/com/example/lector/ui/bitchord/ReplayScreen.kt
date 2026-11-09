// Adaptado de BitChord (https://github.com/kushagrasinghx/BitChord, GPL-3.0): la página Replay
// (resumen por periodo, rankings, día más activo, hora pico) de app/src/main/java/com/music/bitchord/data/stats/ListeningStats.kt.
// Dibujada con los componentes y estilos de Shiori.
package com.example.lector.ui.bitchord

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.lector.Almacen
import com.example.lector.bitchord.ReplayEstadisticas
import com.example.lector.bitchord.ReplayPeriodo
import com.example.lector.bitchord.ReplayRegistro
import com.example.lector.bitchord.ResumenReplay
import com.example.lector.ui.FilaChips
import com.example.lector.ui.ImagenRemota
import com.example.lector.ui.LText
import com.example.lector.ui.LectorColors
import com.example.lector.ui.LectorType
import com.example.lector.ui.Mov
import com.example.lector.ui.PaginaAjustes
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import com.example.lector.ui.SuaveShape
import com.example.lector.ui.TituloSeccion
import com.example.lector.ui.entrada
import com.example.lector.ui.pressable
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Tarjeta de entrada a Replay (va en Estadísticas). */
@Composable
fun ReplayTarjeta(almacen: Almacen, onClick: () -> Unit) {
    val ctx = LocalContext.current
    LaunchedEffect(Unit) { ReplayRegistro.iniciar(ctx, almacen) }
    val r by produceState<ResumenReplay?>(null) { value = ReplayEstadisticas.resumen(almacen, ReplayPeriodo.MES) }
    val acento = MaterialTheme.colorScheme.primary
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).clip(SuaveShape(24.dp))
            .background(acento.copy(alpha = 0.16f))
            .pressable(onClick = onClick)
            .padding(20.dp),
    ) {
        LText("Replay", LectorType.Hero, color = acento)
        Spacer(Modifier.height(4.dp))
        LText(
            r?.let { if (it.vacio) "Lee unos capítulos y aquí verás tu resumen" else "${it.capitulos} capítulos este mes · racha de ${it.rachaActual} días" }
                ?: "Tu lectura en resumen",
            LectorType.Body, color = LectorColors.Muted,
        )
    }
}

@Composable
fun ReplayScreen(almacen: Almacen, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val ctx = LocalContext.current
    LaunchedEffect(Unit) { ReplayRegistro.iniciar(ctx, almacen) }
    var periodo by rememberSaveable { mutableIntStateOf(1) }
    val per = ReplayPeriodo.entries[periodo]
    val r by produceState<ResumenReplay?>(null, per, almacen.historialLinea) { value = ReplayEstadisticas.resumen(almacen, per) }

    // Con las piezas de Mochy: tarjetas sobre LectorColors.Card con la superelipse, números en Inter con el
    // cuentakilómetros (OdometerNumber) y las secciones con el título de SettingsGroup.
    PaginaAjustes("Replay", onBack) {
        FilaChips(ReplayPeriodo.entries.map { it.chip }, periodo) { periodo = it }
        val s = r ?: return@PaginaAjustes
        val acento = MaterialTheme.colorScheme.primary
        TarjetaReplay(Modifier.padding(top = 8.dp)) {
            Column(Modifier.padding(horizontal = 22.dp, vertical = 20.dp)) {
                LText(s.etiqueta, EstiloEtiqueta, color = LectorColors.Muted)
                Spacer(Modifier.height(6.dp))
                com.example.lector.ui.mochy.OdometerNumber("${s.capitulos}", fontSize = 56.sp, color = acento)
                LText(if (s.capitulos == 1) "capítulo leído" else "capítulos leídos", EstiloTituloFila.copy(fontSize = 17.sp))
                if (s.tiempoMs > 60_000) LText("${duracionReplay(s.tiempoMs)} leyendo", EstiloDetalle, Modifier.padding(top = 2.dp), color = LectorColors.Muted)
            }
        }
        if (s.vacio) {
            LText(
                "Todavía no hay lecturas en este periodo. Shiori anota cada capítulo que abres desde ahora (y los del historial); " +
                    "todo se queda en el teléfono.",
                EstiloDetalle, Modifier.padding(horizontal = 24.dp, vertical = 14.dp), color = LectorColors.Muted,
            )
        }

        Spacer(Modifier.height(8.dp))
        Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DatoReplay("${s.rachaActual}", "Racha actual (días)", Modifier.weight(1f).entrada(1))
            DatoReplay("${s.rachaMaxima}", "Mejor racha", Modifier.weight(1f).entrada(2))
        }
        Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DatoReplay("${s.mangas.size}", "Mangas distintos", Modifier.weight(1f).entrada(3))
            DatoReplay("${s.diasLeyendo}", "Días leyendo", Modifier.weight(1f).entrada(4))
        }

        if (s.mangas.isNotEmpty()) {
            TituloSeccion("Lo que más leíste")
            TarjetaReplay {
                Column(Modifier.padding(vertical = 8.dp)) {
                    s.mangas.take(10).forEachIndexed { i, m ->
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).entrada(i),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            LText("${i + 1}", EstiloTituloFila, Modifier.width(28.dp), color = if (i < 3) acento else LectorColors.Muted)
                            ImagenRemota(m.portadaUrl, Modifier.size(44.dp, 62.dp).clip(SuaveShape(10.dp)).background(LectorColors.CardHigh))
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                LText(m.titulo, EstiloTituloFila, maxLines = 2)
                                LText(if (m.caps == 1) "1 capítulo" else "${m.caps} capítulos", EstiloDetalle, color = LectorColors.Muted)
                            }
                        }
                    }
                }
            }
        }

        TituloSeccion("Capítulos por semana")
        TarjetaReplay { Barras(s.porSemana) }
        TituloSeccion("Capítulos por mes")
        TarjetaReplay { Barras(s.porMes) }

        if (s.generos.isNotEmpty()) {
            TituloSeccion("Géneros favoritos")
            val max = s.generos.first().caps.coerceAtLeast(1)
            TarjetaReplay { Column(Modifier.padding(vertical = 10.dp)) { s.generos.take(8).forEachIndexed { i, g -> BarraHorizontal(g.nombre, g.caps, max, i) } } }
        }

        if (!s.vacio) {
            TituloSeccion("Curiosidades")
            TarjetaReplay {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    s.horaPico?.let { h -> LText("Lees sobre todo ${franja(h)} (hacia las ${h}:00)", EstiloTituloFila.copy(fontWeight = FontWeight.Medium)) }
                    s.diaMasActivo?.let { d ->
                        val f = d.format(DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", Locale("es")))
                        LText("Tu día más lector: $f (${s.diaMasActivoCaps} capítulos)", EstiloTituloFila.copy(fontWeight = FontWeight.Medium))
                    }
                    s.desde?.let { d ->
                        LText("Registro desde el ${d.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale("es")))}", EstiloDetalle, color = LectorColors.Muted)
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

private val EstiloEtiqueta = androidx.compose.ui.text.TextStyle(fontFamily = com.example.lector.ui.LectorFont, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
private val EstiloTituloFila = androidx.compose.ui.text.TextStyle(fontFamily = com.example.lector.ui.LectorFont, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 19.sp, letterSpacing = (-0.01).em)
private val EstiloDetalle = androidx.compose.ui.text.TextStyle(fontFamily = com.example.lector.ui.LectorFont, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp)

/** Tarjeta de Mochy (la de SettingsGroup «Classic»): fondo de tarjeta, superelipse de 24 dp, 16 dp de margen. */
@Composable
private fun TarjetaReplay(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(SuaveShape(24.dp)).background(LectorColors.Card)) { content() }
}

/** Un dato suelto: número grande en Inter y su etiqueta debajo. */
@Composable
private fun DatoReplay(valor: String, etiqueta: String, modifier: Modifier = Modifier) {
    Column(modifier.clip(SuaveShape(22.dp)).background(LectorColors.Card).padding(horizontal = 18.dp, vertical = 16.dp)) {
        com.example.lector.ui.mochy.OdometerNumber(valor, fontSize = 30.sp, color = LectorColors.Text, letterSpacing = (-1).sp)
        Spacer(Modifier.height(2.dp))
        LText(etiqueta, EstiloDetalle, color = LectorColors.Muted, maxLines = 1)
    }
}

/** Columnas verticales que crecen al entrar (como las barras de Estadísticas). */
@Composable
private fun Barras(datos: List<Pair<String, Int>>) {
    val max = datos.maxOfOrNull { it.second }?.coerceAtLeast(1) ?: 1
    val acento = MaterialTheme.colorScheme.primary
    Row(
        Modifier.fillMaxWidth().height(156.dp).padding(horizontal = 18.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        datos.forEachIndexed { i, (etq, n) ->
            var lleno by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) { kotlinx.coroutines.delay(40L * i); lleno = true }
            val f by animateFloatAsState(if (lleno) n.toFloat() / max else 0f, if (Mov.reducido) snap() else spring(0.8f, 120f), label = "col")
            Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                if (n > 0) LText("$n", LectorType.Label.copy(fontSize = 10.sp), color = LectorColors.Muted, maxLines = 1)
                Box(
                    Modifier.fillMaxWidth().fillMaxHeight(0.75f * f + 0.01f).clip(SuaveShape(6.dp))
                        .background(if (i == datos.lastIndex) acento else acento.copy(alpha = 0.45f)),
                )
                Spacer(Modifier.height(4.dp))
                LText(etq, LectorType.Label.copy(fontSize = 9.sp), color = LectorColors.Muted, maxLines = 1)
            }
        }
    }
}

@Composable
private fun BarraHorizontal(nombre: String, n: Int, max: Int, i: Int) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp)) {
        Row {
            LText(nombre, EstiloTituloFila, Modifier.weight(1f), maxLines = 1)
            LText("$n", EstiloTituloFila, color = LectorColors.Muted)
        }
        Spacer(Modifier.height(6.dp))
        var lleno by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { kotlinx.coroutines.delay(120L * i); lleno = true }
        val f by animateFloatAsState(if (lleno) n.toFloat() / max else 0f, if (Mov.reducido) snap() else spring(0.8f, 120f), label = "barra")
        Box(Modifier.fillMaxWidth().height(8.dp).clip(SuaveShape(4.dp)).background(LectorColors.PillFill)) {
            Box(Modifier.fillMaxWidth(f).fillMaxHeight().clip(SuaveShape(4.dp)).background(MaterialTheme.colorScheme.primary))
        }
    }
}

private fun franja(h: Int) = when (h) {
    in 5..11 -> "por la mañana"
    in 12..19 -> "por la tarde"
    in 20..23 -> "por la noche"
    else -> "de madrugada"
}

private fun duracionReplay(ms: Long): String {
    val min = ms / 60_000
    val h = min / 60
    return if (h > 0) "${h} h ${min % 60} min" else "$min min"
}
