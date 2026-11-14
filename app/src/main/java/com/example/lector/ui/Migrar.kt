package com.example.lector.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.lector.Almacen
import com.example.lector.Comic
import com.example.lector.ext.CapituloFuente
import com.example.lector.ext.ExtManager
import com.example.lector.ext.FuenteUI
import com.example.lector.ext.MangaFuente
import com.example.lector.ext.Motor
import com.example.lector.ext.bandera
import com.example.lector.ext.nombreIdioma
import kotlinx.coroutines.launch

/**
 * Migración de fuente (como Komikku): se elige otra fuente, se busca el manga en ella y se pasa la entrada de la
 * biblioteca a esa fuente conservando categorías, portada y capítulos leídos (emparejados por número de capítulo).
 * Lo que no se hace: mover las descargas de la fuente vieja, ni elegir qué datos copiar.
 */
@Composable
fun MigrarScreen(
    ext: ExtManager,
    almacen: Almacen,
    comic: Comic,
    origen: FuenteUI,
    capsOrigen: List<CapituloFuente>?,
    alHecho: (destino: FuenteUI) -> Unit,
    onBack: () -> Unit,
) {
    val motor = Motor.actual
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var destino by remember { mutableStateOf<FuenteUI?>(null) }
    var consulta by remember { mutableStateOf(comic.titulo) }
    val resultados = remember { mutableStateListOf<MangaFuente>() }
    var buscando by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var elegido by remember { mutableStateOf<MangaFuente?>(null) }
    var migrando by remember { mutableStateOf(false) }

    fun buscar(f: FuenteUI, q: String) {
        if (motor == null || q.isBlank()) return
        scope.launch {
            buscando = true; error = null; resultados.clear()
            runCatching { motor.buscar(f, q.trim(), 1) }
                .onSuccess { resultados.addAll(it.mangas) }
                .onFailure { error = it.message ?: "No se pudo buscar" }
            buscando = false
        }
    }

    val f = destino
    // Paso 0 (como Komikku): elegir en qué fuentes buscar. Se recuerda para la próxima vez.
    remember { com.example.lector.ui.migrar.OpcionesMigrar.iniciar(ctx); com.example.lector.ui.migrar.HistorialMigraciones.iniciar(ctx) }
    val todasIdiomas = com.example.lector.ui.migrar.OpcionesMigrar.todasFuentes
    val todas = remember(ext.fuentes, ext.idiomas, ext.instaladas, todasIdiomas) { fuentesBuscables(ext, origen.id) }
    var elegidas by remember { mutableStateOf(elegidasGuardadas(ctx, ext, todas)) }
    var eligiendo by remember { mutableStateOf(true) }
    // Desde la pestaña Migrar (sin la ficha abierta detrás): primero se ve lo que hay guardado del manga (lo de la copia:
    // portada, datos, capítulos y leídos), como en Komikku; «Migrar» sigue con la elección de fuentes.
    var verInfo by remember { mutableStateOf(capsOrigen == null && comic.urlManga != null) }
    if (f == null && verInfo) {
        com.example.lector.ui.migrar.InfoGuardada(
            ext, almacen, origen.id, comic.urlManga.orEmpty(), comic, comic.titulo, comic.portadaUrl,
            fuenteTexto = "${origen.nombre} (${origen.lang.uppercase()})", fuenteFalta = false,
            accion = "Migrar", iconoAccion = Icons.Rounded.ArrowForward,
            onBack = onBack, onAccion = { verInfo = false },
        )
        BackHandler(onBack = onBack)
        return
    }
    if (f == null && eligiendo) {
        SeleccionFuentes(ext, todas, elegidas, { if (capsOrigen == null && comic.urlManga != null) verInfo = true else onBack() }) { elegidas = it; eligiendo = false }
        return
    }
    if (f == null) {
        // Paso 1: se busca el título en las fuentes elegidas (6 a la vez, como Komikku) y se elige el resultado.
        // Lista perezosa: antes se pintaban todas las filas a la vez y con cientos de fuentes la app se cerraba.
        var enviada by remember { mutableStateOf(comic.titulo) }
        var soloConResultados by remember { mutableStateOf(true) }
        val fuentes = remember(todas, elegidas) {
            todas.filter { it.id in elegidas }.sortedWith(compareBy({ it.id.toString() !in ext.fijadas }, { it.nombre.lowercase() }))
        }
        val globales = rememberResultadosGlobales(fuentes, enviada)
        val claves = remember(almacen.version) { almacen.clavesEnLinea() }
        BackHandler { eligiendo = true }
        Column(Modifier.fillMaxSize().background(LectorColors.Bg).statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                BotonAtras({ eligiendo = true })
                Spacer(Modifier.width(14.dp))
                LText("Migrar «${comic.titulo}»", LectorType.Section, Modifier.weight(1f), maxLines = 1)
            }
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 40.dp)) {
                item {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) { CampoBusqueda(consulta, { consulta = it }, "Título") }
                        Spacer(Modifier.width(10.dp))
                        PillButton("Buscar", Icons.Rounded.Search, { enviada = consulta.trim() })
                    }
                    Row(Modifier.padding(horizontal = 24.dp, vertical = 4.dp)) {
                        ChipSel("Solo con resultados", soloConResultados) { soloConResultados = !soloConResultados }
                        val omit = fuentes.count { it.id in FuentesOmitidas.omitidas }
                        if (omit > 0) {
                            Spacer(Modifier.width(8.dp))
                            ChipSel("Volver a incluir $omit omitidas", false) { FuentesOmitidas.restablecer(); val q = enviada; enviada = ""; scope.launch { enviada = q } }
                        }
                    }
                    val hechas = globales.values.count { it !is EstadoBusqueda.Cargando }
                    val conAlgo = globales.values.count { it is EstadoBusqueda.Hecho && it.mangas.isNotEmpty() }
                    LText(
                        when {
                            fuentes.isEmpty() -> "No hay otras fuentes instaladas y de confianza."
                            hechas < fuentes.size -> "Buscando… $hechas de ${fuentes.size} fuentes · aparece en $conAlgo"
                            else -> "Aparece en $conAlgo de ${fuentes.size} fuentes. Toca el que quieras."
                        },
                        LectorType.Label, Modifier.padding(horizontal = 24.dp, vertical = 6.dp), color = LectorColors.Muted,
                    )
                }
                items(fuentes, key = { it.id }) { o ->
                    val e = globales[o.id] ?: EstadoBusqueda.Cargando
                    if (soloConResultados && (e !is EstadoBusqueda.Hecho || e.mangas.isEmpty())) return@items
                    FilaResultados(
                        o, e, claves, destacar = comic.titulo,
                        onManga = { m ->
                            destino = o
                            resultados.clear(); (e as? EstadoBusqueda.Hecho)?.let { resultados.addAll(it.mangas) }
                            elegido = m
                        },
                        onLargo = {},
                    )
                }
            }
        }
        return
    }

    // Paso 2: buscar el manga en la fuente elegida y confirmar.
    PaginaAjustes("Migrar a ${f.nombre}", { destino = null; resultados.clear(); error = null }) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { CampoBusqueda(consulta, { consulta = it }, "Buscar en ${f.nombre}") }
            Spacer(Modifier.width(10.dp))
            PillButton("Buscar", Icons.Rounded.Search, { buscar(f, consulta) })
        }
        error?.let { LText(it, LectorType.Label, Modifier.padding(horizontal = 24.dp), color = LectorColors.Error) }
        if (buscando) {
            Box(Modifier.fillMaxWidth().padding(24.dp), Alignment.Center) { IndicadorCarga(Modifier.size(24.dp), estado = com.example.lector.ui.orbes.EstadoOrbe.SEARCHING) }
        } else if (resultados.isEmpty() && error == null) {
            LText("Sin resultados. Prueba con otro título.", LectorType.Body, Modifier.padding(24.dp), color = LectorColors.Muted)
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            resultados.forEach { m ->
                Row(
                    Modifier.fillMaxWidth().pressable { elegido = m }.padding(horizontal = 24.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ImagenRemota(
                        m.portadaUrl,
                        Modifier.width(52.dp).aspectRatio(2f / 3f).clip(SuaveShape(10.dp)),
                        motor?.cabeceras(f).orEmpty(), fuenteId = f.id,
                    )
                    Spacer(Modifier.width(16.dp))
                    LText(m.titulo, LectorType.Body, Modifier.weight(1f), maxLines = 3)
                }
            }
        }
    }

    elegido?.let { m ->
        DialogoAlerta(
            onDismissRequest = { if (!migrando) elegido = null },
            title = { Text("¿Migrar a ${f.nombre}?") },
            text = {
                Column {
                    Text("«${m.titulo}» sustituirá a «${comic.titulo}» en tu biblioteca.")
                    Spacer(Modifier.size(8.dp))
                    Text("Se conservan categorías y portada; los capítulos leídos se emparejan por número. Las descargas de la fuente anterior no se mueven.")
                    if (migrando) {
                        Spacer(Modifier.size(12.dp))
                        IndicadorCarga(Modifier.size(22.dp), strokeWidth = 2.dp, estado = com.example.lector.ui.orbes.EstadoOrbe.WEAVING)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !migrando && motor != null,
                    onClick = {
                        migrando = true
                        scope.launch {
                            val resultado = runCatching { migrarComic(ext, almacen, comic, origen.id, capsOrigen, f, m) }
                            migrando = false
                            resultado
                                .onSuccess { elegido = null; alHecho(f) }
                                .onFailure { elegido = null; Avisos.mostrar(it.message ?: "No se pudo migrar") }
                        }
                    },
                ) { Text("Migrar") }
            },
            dismissButton = { TextButton(enabled = !migrando, onClick = { elegido = null }) { Text("Cancelar") } },
        )
    }
}

/**
 * Pasa [comic] de la biblioteca a [m] de la fuente [f]: conserva categorías y portada y marca como leídos los
 * capítulos equivalentes. Sirve también cuando la fuente vieja ya no está instalada ([capsOrigen] = null).
 */
suspend fun migrarComic(
    ext: ExtManager, almacen: Almacen, comic: Comic, origenId: Long, capsOrigen: List<CapituloFuente>?, f: FuenteUI, m: MangaFuente,
) {
    val motor = requireNotNull(Motor.actual) { "Motor de fuentes no disponible" }
    check(almacen.comicEnLinea(f.id, m.url) == null) { "Ese manga ya está en tu biblioteca" }
    val ficha = motor.ficha(f, m)
    // Sin la lista de la fuente vieja (no instalada o no responde) se usa la guardada en el móvil (copia o última vez).
    val viejos = capsOrigen ?: comic.urlManga?.let { com.example.lector.CacheFichas.leer(origenId, it)?.capitulos }
    val leidos = ext.migrarLeidos(origenId, comic.urlManga.orEmpty(), viejos, f.id, m.url, ficha.capitulos, comic.ultimaPagina)
    val portada = ficha.detalle.portadaUrl ?: m.portadaUrl
    check(almacen.migrarEnLinea(comic, f.id, m.url, portada, ficha.capitulos.size, leidos)) { "No se pudo migrar" }
    // Los capítulos que hay ahora son la base: solo lo que salga después contará como novedad.
    almacen.registrarCapitulos(f.id, m.url, ficha.detalle.titulo.ifBlank { m.titulo }, portada, ficha.capitulos)
    // Historial de migraciones (Más → Migraciones recientes).
    runCatching {
        com.example.lector.ui.migrar.HistorialMigraciones.registrar(
            com.example.lector.ui.migrar.Migracion(
                "migrar", comic.titulo,
                origenId, com.example.lector.ui.migrar.HistorialMigraciones.nombreFuente(ext, origenId), comic.urlManga,
                f.id, "${f.nombre} (${f.lang.uppercase()})", m.url, portada, leidos,
            )
        )
    }
}

@Composable
private fun FilaFuenteSel(f: FuenteUI, elegida: Boolean, alTocar: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(SuaveShape(16.dp))
            .background(if (elegida) LectorColors.Card else LectorColors.Card.copy(alpha = 0.5f))
            .pressable(alTocar).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconoExt(f.pkg, null, Modifier.size(40.dp))
        Spacer(Modifier.width(14.dp))
        LText(f.nombre, LectorType.Body, Modifier.weight(1f), maxLines = 1)
        LText("${bandera(f.lang)} ${f.lang.uppercase()}", LectorType.Label, color = LectorColors.Muted)
        Spacer(Modifier.width(10.dp))
        Icon(if (elegida) Icons.Rounded.CheckCircle else Icons.Rounded.AddCircle, null,
            tint = if (elegida) androidx.compose.material3.MaterialTheme.colorScheme.primary else LectorColors.Muted, modifier = Modifier.size(22.dp))
    }
}

/**
 * Fuentes donde se puede buscar un título, sin la de origen. Como Komikku (MigrationConfigScreen.initSources): todas las
 * fuentes de catálogo de las extensiones instaladas cuyo idioma tengas activado, ordenadas por «nombre (idioma)»; sin
 * quitar las +18 (ese ajuste solo oculta extensiones en la lista). Además, una fuente entra si su idioma base
 * («pt» de «pt-BR») o el de su extensión («all» / «multi») está activado: las extensiones multilingües traen fuentes
 * con idiomas que no salen en la lista de idiomas y antes se perdían. Con [todosIdiomas] («Todas las fuentes») entran
 * todas las de las extensiones instaladas y de confianza, sin mirar el idioma.
 */
internal fun fuentesBuscables(
    ext: ExtManager, sinId: Long, todosIdiomas: Boolean = com.example.lector.ui.migrar.OpcionesMigrar.todasFuentes,
): List<FuenteUI> {
    val idiomas = ext.idiomas
    val langExt = ext.instaladas.associate { it.pkg to it.lang }
    fun activo(l: String?) = !l.isNullOrBlank() && (l in idiomas || l.substringBefore('-') in idiomas || l.substringBefore('_') in idiomas)
    return ext.fuentes.asSequence()
        .filter { it.id != sinId }
        .filter { todosIdiomas || idiomas.isEmpty() || activo(it.lang) || activo(langExt[it.pkg]) || it.lang in setOf("all", "multi", "other", "") }
        .distinctBy { it.id }
        .sortedWith(compareBy({ "${it.nombre} (${it.lang})".lowercase() }, { it.id }))
        .toList()
}

/** Las elegidas la última vez; si no hay, las ancladas; si tampoco, todas. */
internal fun elegidasGuardadas(ctx: android.content.Context, ext: ExtManager, todas: List<FuenteUI>): Set<Long> {
    val guardadas = ctx.getSharedPreferences("lector", android.content.Context.MODE_PRIVATE)
        .getStringSet("migrar_fuentes", null)?.mapNotNull { it.toLongOrNull() }?.toSet().orEmpty()
    val ids = todas.map { it.id }.toSet()
    return (guardadas intersect ids).ifEmpty { todas.filter { it.id.toString() in ext.fijadas }.map { it.id }.toSet() }.ifEmpty { ids }
}

/** «Seleccionar fuentes» (como Komikku): Elegidas / Disponibles, buscar, ancladas, todas o ninguna. */
@Composable
internal fun SeleccionFuentes(ext: ExtManager, todas: List<FuenteUI>, inicial: Set<Long>, onBack: () -> Unit, onContinuar: (Set<Long>) -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var elegidas by remember { mutableStateOf(inicial) }
    var filtro by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().background(LectorColors.Bg).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            BotonAtras(onBack)
            Spacer(Modifier.width(14.dp))
            LText("Seleccionar fuentes", LectorType.Section, Modifier.weight(1f), maxLines = 1)
            BotonRedondoTema(Icons.Rounded.Star, "Solo las ancladas", {
                elegidas = todas.filter { it.id.toString() in ext.fijadas }.map { it.id }.toSet()
                if (elegidas.isEmpty()) Avisos.mostrar("No tienes fuentes ancladas")
            })
            Spacer(Modifier.width(6.dp))
            BotonRedondoTema(Icons.Rounded.Check, "Todas", { elegidas = todas.map { it.id }.toSet() })
            Spacer(Modifier.width(6.dp))
            BotonRedondoTema(Icons.Rounded.Clear, "Ninguna", { elegidas = emptySet() })
        }
        Box(Modifier.padding(horizontal = 16.dp)) { CampoBusqueda(filtro, { filtro = it }, "Buscar fuentes") }
        Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            // Sin filtro de idioma: todas las fuentes de todas las extensiones instaladas (también las multilingües).
            ChipSel("Todas las fuentes (todos los idiomas)", com.example.lector.ui.migrar.OpcionesMigrar.todasFuentes) {
                com.example.lector.ui.migrar.OpcionesMigrar.alternar(ctx)
            }
            Spacer(Modifier.width(8.dp))
            LText("${todas.size}", LectorType.Label, color = LectorColors.Muted)
        }
        val pendientes = com.example.lector.ui.migrar.extensionesPendientes(ext)
        val visibles = todas.filter { filtro.isBlank() || it.nombre.contains(filtro.trim(), true) }
        val (si, no) = visibles.partition { it.id in elegidas }
        Box(Modifier.weight(1f)) {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 110.dp)) {
                if (si.isNotEmpty()) item { LText("Elegidas (${elegidas.size})", LectorType.Label, Modifier.padding(8.dp), color = LectorColors.Muted) }
                items(si, key = { "s${it.id}" }) { o -> FilaFuenteSel(o, true) { elegidas = elegidas - o.id } }
                if (no.isNotEmpty()) item { LText("Disponibles", LectorType.Label, Modifier.padding(start = 8.dp, top = 16.dp, bottom = 8.dp), color = LectorColors.Muted) }
                items(no, key = { "n${it.id}" }) { o -> FilaFuenteSel(o, false) { elegidas = elegidas + o.id } }
                if (pendientes.isNotEmpty()) item {
                    LText(
                        "Instaladas pero sin usar (confía en ellas para buscar ahí)", LectorType.Label,
                        Modifier.padding(start = 8.dp, top = 16.dp, bottom = 8.dp), color = LectorColors.Muted,
                    )
                }
                items(pendientes, key = { "p${it.pkg}" }) { e -> com.example.lector.ui.migrar.FilaExtPendiente(ext, e) }
            }
            Box(Modifier.align(Alignment.BottomEnd).padding(20.dp).navigationBarsPadding()) {
                PillButton("Continuar (${elegidas.size})", Icons.Rounded.ArrowForward, {
                    if (elegidas.isEmpty()) Avisos.mostrar("Elige al menos una fuente")
                    else {
                        ctx.getSharedPreferences("lector", android.content.Context.MODE_PRIVATE).edit()
                            .putStringSet("migrar_fuentes", elegidas.map { it.toString() }.toSet()).apply()
                        onContinuar(elegidas)
                    }
                })
            }
        }
    }
    BackHandler(onBack = onBack)
}
