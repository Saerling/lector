package com.example.lector.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.lector.ext.*
import kotlinx.coroutines.launch

private fun abrirUrl(ctx: Context, url: String) {
    runCatching {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

// ───────────────────── Listados de extensiones ─────────────────────

@Composable
fun ListadosExtScreen(ext: ExtManager, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val acento = MaterialTheme.colorScheme.primary
    var anadir by remember { mutableStateOf(false) }
    var borrar by remember { mutableStateOf<RepoExt?>(null) }

    Box(Modifier.fillMaxSize()) {
        PaginaAjustes("Listados de extensiones", onBack) {
            if (ext.repos.isEmpty()) {
                LText("No hay listados. Añade uno con el botón de abajo.", LectorType.Body, Modifier.padding(24.dp), color = LectorColors.Muted)
            }
            ext.repos.forEach { r ->
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
                        .alpha(if (r.activo) 1f else 0.5f)
                        .clip(SuaveShape(24.dp)).background(LectorColors.Card).padding(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(56.dp).clip(SuaveShape(14.dp)).background(acento.copy(alpha = 0.2f)),
                            Alignment.Center
                        ) { LText(r.sigla, LectorType.Section, color = acento) }
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            LText(r.nombre, LectorType.Section.copy(fontSize = 18.sp), maxLines = 1)
                            LText(r.url, LectorType.Label, color = LectorColors.Muted, maxLines = 2)
                        }
                    }
                    Row(Modifier.fillMaxWidth(), Arrangement.End, Alignment.CenterVertically) {
                        r.web?.let { w -> AccionTexto("Web") { abrirUrl(ctx, w) } }
                        AccionTexto("Copiar") {
                            val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("Listado", r.url))
                            Avisos.mostrar("Dirección copiada")
                        }
                        AccionTexto(if (r.activo) "Ocultar" else "Mostrar") { ext.alternarRepo(r.url) }
                        IconButton(onClick = { borrar = r }) { Icon(Icons.Rounded.Delete, "Borrar", tint = LectorColors.Text) }
                    }
                }
            }
            Spacer(Modifier.height(96.dp))
        }
        Row(
            Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(20.dp)
                .pressable { anadir = true }.clip(SuaveShape(20.dp))
                .background(acento.copy(alpha = 0.28f)).padding(horizontal = 22.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Rounded.Add, null, tint = LectorColors.Text)
            Spacer(Modifier.width(10.dp))
            LText("Añadir", LectorType.Section.copy(fontSize = 17.sp))
        }
    }

    if (anadir) DialogoAnadirRepo(ext) { anadir = false }
    borrar?.let { r ->
        AlertDialog(
            onDismissRequest = { borrar = null },
            title = { Text("¿Quitar «${r.nombre}»?") },
            text = { Text("Las extensiones ya instaladas se quedan; solo desaparece este listado.") },
            confirmButton = { TextButton(onClick = { ext.eliminarRepo(r.url); borrar = null }) { Text("Quitar") } },
            dismissButton = { TextButton(onClick = { borrar = null }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun AccionTexto(texto: String, onClick: () -> Unit) {
    TextButton(onClick = onClick) { Text(texto) }
}

@Composable
private fun DialogoAnadirRepo(ext: ExtManager, cerrar: () -> Unit) {
    // Listados conocidos (direcciones publicadas por sus autores). Salen solo los que aún no tienes.
    val sugeridos = remember(ext.repos) {
        listOf(
            "Keiyoushi" to "https://raw.githubusercontent.com/keiyoushi/extensions/repo/index.min.json",
            "Suwayomi" to "https://raw.githubusercontent.com/suwayomi/tachiyomi-extension/repo/index.min.json",
            "Yūzōnō" to "https://github.com/yuzono/manga-repo/raw/repo/index.pb",
            "Cursed Yūzōnō" to "https://raw.githubusercontent.com/yuzono/cursed-manga-repo/repo/index.min.json",
            "Kavita" to "https://raw.githubusercontent.com/Kareadita/tach-extension/repo/index.min.json",
        ).filter { (_, u) -> ext.repos.none { ExtRepos.carpeta(it.url) == ExtRepos.carpeta(ExtRepos.normalizar(u) ?: u) } }
    }
    var url by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var trabajando by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { if (!trabajando) cerrar() },
        title = { Text("Añadir listado de extensiones") },
        text = {
            Column {
                OutlinedTextField(
                    value = url, onValueChange = { url = it; error = null }, singleLine = true,
                    enabled = !trabajando, isError = error != null,
                    label = { Text("Dirección URL del listado") },
                    supportingText = {
                        Text(error ?: "Acepta la dirección del índice, repo.json, un enlace de GitHub o usuario/repositorio")
                    },
                )
                if (sugeridos.isNotEmpty() && !trabajando) {
                    Spacer(Modifier.height(10.dp))
                    Text("Listados conocidos", style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.height(6.dp))
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        sugeridos.forEach { (nombre, u) -> ChipSel(nombre, url == u) { url = u; error = null } }
                    }
                }
                if (trabajando) {
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(12.dp))
                        Text("Comprobando el listado…")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = url.isNotBlank() && !trabajando,
                onClick = {
                    trabajando = true
                    ext.anadirRepo(url) { e ->
                        trabajando = false
                        if (e == null) cerrar() else error = e
                    }
                }
            ) { Text("Añadir") }
        },
        dismissButton = { TextButton(enabled = !trabajando, onClick = cerrar) { Text("Cancelar") } },
    )
}

// ───────────────────── Detalle de una extensión ─────────────────────

@Composable
fun ExtDetalleScreen(ext: ExtManager, pkg: String, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val e = ext.instaladas.firstOrNull { it.pkg == pkg }
    var ajustesDe by remember { mutableStateOf<Long?>(null) }
    val configurables = ext.configurables(pkg)
    val abierta = ajustesDe?.let { id -> configurables.firstOrNull { it.id == id } }
    if (abierta != null) {
        AjustesFuenteScreen(ext, pkg, abierta, abierta.name) { ajustesDe = null }
        return
    }
    PaginaAjustes(e?.nombre ?: "Extensión", onBack) {
        if (e == null) {
            LText("Esta extensión ya no está instalada.", LectorType.Body, Modifier.padding(24.dp), color = LectorColors.Muted)
            return@PaginaAjustes
        }
        Row(Modifier.padding(horizontal = 24.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconoExt(e.pkg, null, Modifier.size(72.dp))
            Spacer(Modifier.width(20.dp))
            Column {
                LText(e.nombre, LectorType.Hero, maxLines = 2)
                LText("${bandera(e.lang)} ${nombreIdioma(e.lang)}", LectorType.Body, color = LectorColors.Muted)
            }
        }
        Dato("Paquete", e.pkg)
        Dato("Versión", e.versionName)
        Dato("Listado", e.repoSigla?.let { "@$it" } ?: "—")
        Dato("Estado", if (e.confiable) "De confianza" else "Sin confianza")
        (e.errorCarga ?: ext.erroresCarga[e.pkg])?.let { Dato("Problema", it) }
        Divisor()
        e.actualizacion?.let { a ->
            FilaAjuste(Icons.Rounded.Refresh, "Actualizar a ${a.versionName}", null) { ext.instalar(a) }
        }
        if (!e.confiable) {
            FilaAjuste(Icons.Rounded.Check, "Confiar en esta extensión", "Permite usar sus fuentes") { ext.confiar(e) }
        } else {
            FilaAjuste(Icons.Rounded.Close, "Dejar de confiar", "Sus fuentes dejarán de aparecer") { ext.dejarDeConfiar(e) }
        }
        FilaAjuste(Icons.Rounded.Info, "Información de la app", "Permisos y ajustes de Android") {
            runCatching {
                ctx.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${e.pkg}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        }
        FilaAjuste(Icons.Rounded.Delete, "Desinstalar", null) { ext.desinstalar(e.pkg) }
        Divisor()
        val fuentes = ext.disponibles.firstOrNull { it.pkg == e.pkg }?.fuentes.orEmpty()
        TituloSeccion("Fuentes (${fuentes.size})")
        fuentes.forEach { f ->
            Column(Modifier.padding(horizontal = 24.dp, vertical = 6.dp)) {
                LText(f.nombre, LectorType.Section.copy(fontSize = 16.sp), maxLines = 1)
                LText("${nombreIdioma(f.lang)}  ·  ${f.baseUrl}", LectorType.Label, color = LectorColors.Muted, maxLines = 1)
            }
        }
        if (configurables.isNotEmpty()) {
            Divisor()
            TituloSeccion("Ajustes de las fuentes")
            configurables.forEach { c ->
                FilaAjuste(Icons.Rounded.Settings, c.name, "Dominio, inicio de sesión, calidad…") { ajustesDe = c.id }
            }
        } else {
            LText(
                if (e.confiable && e.errorCarga == null) "Esta extensión no tiene ajustes propios (o todavía se está cargando)."
                else "Los ajustes propios de cada fuente aparecen cuando la extensión es de confianza y se puede cargar.",
                LectorType.Label, Modifier.padding(24.dp), color = LectorColors.Muted
            )
        }
    }
}

@Composable
private fun Dato(etiqueta: String, valor: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 6.dp)) {
        LText(etiqueta, LectorType.Body, Modifier.width(96.dp), color = LectorColors.Muted)
        LText(valor, LectorType.Body, Modifier.weight(1f))
    }
}

// ───────────────────── Una fuente ─────────────────────

@Composable
fun FuenteDetalleScreen(ext: ExtManager, fuenteId: Long, onManga: (MangaFuente) -> Unit, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val f = ext.fuentes.firstOrNull { it.id == fuenteId }
    val motor = Motor.actual
    LaunchedEffect(fuenteId) { f?.let { ext.marcarReciente(it.id) } }

    when {
        f == null -> PaginaAjustes("Fuente", onBack) {
            LText("No se encontró la fuente. Quizá se desinstaló la extensión.", LectorType.Body, Modifier.padding(24.dp), color = LectorColors.Muted)
        }
        motor == null -> PaginaAjustes(f.nombre, onBack) {
            Dato("Idioma", "${bandera(f.lang)} ${nombreIdioma(f.lang)}")
            if (f.baseUrl.isNotBlank()) Dato("Sitio", f.baseUrl)
            Divisor()
            if (f.baseUrl.isNotBlank()) {
                FilaAjuste(Icons.Rounded.Share, "Abrir sitio web", "Se abre en tu navegador") { abrirUrl(ctx, f.baseUrl) }
            }
            LText(
                "Explorar los títulos de esta fuente dentro de la app necesita el motor de fuentes, que se conecta en el siguiente paso (mira HANDOFF.md).",
                LectorType.Label, Modifier.padding(24.dp), color = LectorColors.Muted
            )
        }
        else -> {
            val configurable = ext.configurables(f.pkg).firstOrNull { it.id == f.id }
            var ajustes by remember { mutableStateOf(false) }
            if (ajustes && configurable != null) {
                AjustesFuenteScreen(ext, f.pkg, configurable, f.nombre) { ajustes = false }
            } else {
                NavegarFuente(f, motor, onManga, onBack, if (configurable != null) ({ ajustes = true }) else null)
            }
        }
    }
}

@Composable
private fun NavegarFuente(
    f: FuenteUI, motor: MotorFuentes, onManga: (MangaFuente) -> Unit, onBack: () -> Unit,
    onAjustes: (() -> Unit)? = null,
) {
    val scope = rememberCoroutineScope()
    var modo by rememberSaveable { mutableStateOf(0) } // 0 populares · 1 recientes · 2 búsqueda
    var consulta by rememberSaveable { mutableStateOf("") }
    var enviada by rememberSaveable { mutableStateOf("") }
    val lista = remember { mutableStateListOf<MangaFuente>() }
    var pagina by remember { mutableStateOf(1) }
    var hay by remember { mutableStateOf(true) }
    var cargando by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    suspend fun pedir(p: Int): PaginaMangas = when (modo) {
        0 -> motor.populares(f, p)
        1 -> motor.recientes(f, p)
        else -> motor.buscar(f, enviada, p)
    }

    // Al cambiar de modo o de búsqueda se vuelve a empezar por la página 1.
    LaunchedEffect(modo, enviada) {
        lista.clear(); pagina = 1; hay = true; error = null; cargando = true
        runCatching { pedir(1) }
            .onSuccess { lista.addAll(it.mangas); hay = it.haySiguiente }
            .onFailure { error = it.message ?: "No se pudo cargar" }
        cargando = false
    }

    Column(Modifier.fillMaxSize().background(LectorColors.Bg).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).pressable(onBack).clip(CircleShape).background(LectorColors.PillFill), Alignment.Center) {
                Icon(Icons.Rounded.ArrowBack, "Atrás", tint = LectorColors.Text)
            }
            Spacer(Modifier.width(14.dp))
            LText(f.nombre, LectorType.Display.copy(fontSize = 26.sp), Modifier.weight(1f, fill = false), maxLines = 1)
            if (onAjustes != null) {
                Spacer(Modifier.weight(1f))
                Box(Modifier.size(44.dp).pressable(onAjustes).clip(CircleShape).background(LectorColors.PillFill), Alignment.Center) {
                    Icon(Icons.Rounded.Settings, "Ajustes de la fuente", tint = LectorColors.Text)
                }
            }
        }
        Row(Modifier.padding(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ChipSel("Populares", modo == 0) { modo = 0 }
            ChipSel("Recientes", modo == 1) { modo = 1 }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { CampoBusqueda(consulta, { consulta = it }, "Buscar en ${f.nombre}") }
            Spacer(Modifier.width(10.dp))
            PillButton("Ir", Icons.Rounded.Search, { if (consulta.isNotBlank()) { enviada = consulta.trim(); modo = 2 } })
        }
        error?.let { LText(it, LectorType.Label, Modifier.padding(horizontal = 24.dp), color = Color(0xFFE57373)) }
        LazyVerticalGrid(
            GridCells.Fixed(3),
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 40.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            itemsIndexed(lista) { i, m ->
                Column(Modifier.entrada(i % 12).press3D().pressable { onManga(m) }) {
                    ImagenRemota(
                        m.portadaUrl, Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(SuaveShape(14.dp)),
                        motor.cabeceras(f)
                    )
                    Spacer(Modifier.height(6.dp))
                    LText(m.titulo, LectorType.Label, maxLines = 2)
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), Alignment.Center) {
                    when {
                        cargando -> CircularProgressIndicator(Modifier.size(24.dp))
                        hay && lista.isNotEmpty() -> PillButton("Cargar más", Icons.Rounded.Add, {
                            scope.launch {
                                cargando = true
                                runCatching { pedir(pagina + 1) }
                                    .onSuccess { lista.addAll(it.mangas); hay = it.haySiguiente; pagina += 1 }
                                    .onFailure { error = it.message ?: "No se pudo cargar" }
                                cargando = false
                            }
                        })
                    }
                }
            }
        }
    }
}
