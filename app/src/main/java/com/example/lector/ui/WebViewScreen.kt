package com.example.lector.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

/**
 * WebView dentro de la app (como el de Komikku): sirve para ver el sitio de la fuente y resolver
 * Cloudflare / captchas o iniciar sesión. Las cookies se comparten con las peticiones de la fuente.
 *
 * Importante: usa el MISMO User-Agent que las peticiones de la extensión ([cabeceras]). Cloudflare ata su cookie
 * de paso (cf_clearance) al navegador que resolvió el reto: con otro User-Agent la cookie no valdría.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebViewScreen(url: String, tituloInicial: String, cabeceras: Map<String, String> = emptyMap(), alVolver: () -> Unit) {
    val ctx = LocalContext.current
    var web by remember { mutableStateOf<WebView?>(null) }
    var progreso by remember { mutableStateOf(0f) }
    var titulo by remember { mutableStateOf(tituloInicial) }
    var actual by remember { mutableStateOf(url) }

    BackHandler { if (web?.canGoBack() == true) web?.goBack() else alVolver() }
    // Al cerrar se guardan las cookies en disco, para que las use en seguida la siguiente petición de la fuente.
    DisposableEffect(Unit) { onDispose { CookieManager.getInstance().flush(); web?.apply { stopLoading(); destroy() } } }
    val agente = remember(cabeceras) {
        cabeceras.entries.firstOrNull { it.key.equals("User-Agent", ignoreCase = true) }?.value
            ?: eu.kanade.tachiyomi.network.NetworkHelper.DEFAULT_USER_AGENT
    }
    val extra = remember(cabeceras) { cabeceras.filterKeys { !it.equals("User-Agent", ignoreCase = true) } }

    Column(Modifier.fillMaxSize().background(LectorColors.Bg)) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(44.dp).pressable { alVolver() }.clip(CircleShape).background(LectorColors.PillFill),
                Alignment.Center,
            ) { Icon(Icons.Rounded.ArrowBack, "Cerrar", tint = LectorColors.Text) }
            Spacer(Modifier.width(14.dp))
            LText(titulo, LectorType.Section.copy(fontSize = 18.sp), Modifier.weight(1f), maxLines = 1)
            Box(
                Modifier.size(44.dp).pressable {
                    runCatching {
                        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(actual)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                }.clip(CircleShape).background(LectorColors.PillFill),
                Alignment.Center,
            ) { Icon(Icons.Rounded.Share, "Abrir en el navegador", tint = LectorColors.Text) }
        }
        Box(Modifier.fillMaxWidth().height(2.dp)) {
            if (progreso in 0.01f..0.99f) {
                Box(Modifier.fillMaxWidth(progreso).fillMaxHeight().background(MaterialTheme.colorScheme.primary))
            }
        }
        AndroidView(
            modifier = Modifier.fillMaxWidth().weight(1f),
            factory = { c ->
                WebView(c).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.loadWithOverviewMode = true
                    settings.useWideViewPort = true
                    settings.userAgentString = agente
                    CookieManager.getInstance().setAcceptCookie(true)
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(v: WebView, u: String, favicon: Bitmap?) { actual = u }
                    }
                    webChromeClient = object : WebChromeClient() {
                        override fun onProgressChanged(v: WebView, p: Int) { progreso = p / 100f }
                        override fun onReceivedTitle(v: WebView, t: String?) { if (!t.isNullOrBlank()) titulo = t }
                    }
                    loadUrl(url, extra)
                    web = this
                }
            },
        )
    }
}
