package eu.kanade.tachiyomi.util.system

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.Toast

object WebViewUtil {
    const val MINIMUM_WEBVIEW_VERSION = 118

    fun supportsWebView(context: Context): Boolean {
        try {
            // Puede lanzar MissingWebViewPackageException si no hay WebView instalado.
            CookieManager.getInstance()
        } catch (e: Throwable) {
            Log.e("Lector", "WebView no disponible", e)
            return false
        }
        return context.packageManager.hasSystemFeature(PackageManager.FEATURE_WEBVIEW)
    }
}

fun Context.toast(texto: String, duracion: Int = Toast.LENGTH_SHORT) {
    val app = applicationContext
    Handler(Looper.getMainLooper()).post { Toast.makeText(app, texto, duracion).show() }
}

fun WebView.isOutdated(): Boolean = getWebViewMajorVersion() < WebViewUtil.MINIMUM_WEBVIEW_VERSION

@SuppressLint("SetJavaScriptEnabled")
fun WebView.setDefaultSettings() {
    with(settings) {
        javaScriptEnabled = true
        domStorageEnabled = true
        useWideViewPort = true
        loadWithOverviewMode = true
        cacheMode = WebSettings.LOAD_DEFAULT
        setSupportMultipleWindows(true)
        setSupportZoom(true)
        builtInZoomControls = true
        displayZoomControls = false
    }
    CookieManager.getInstance().acceptThirdPartyCookies(this)
}

private fun WebView.getWebViewMajorVersion(): Int {
    val m = """.*Chrome/(\d+)\..*""".toRegex().matchEntire(getDefaultUserAgentString())
    return m?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
}

// Basado en https://stackoverflow.com/a/29218966
private fun WebView.getDefaultUserAgentString(): String {
    val original: String = settings.userAgentString
    settings.userAgentString = null
    val porDefecto = settings.userAgentString
    settings.userAgentString = original
    return porDefecto
}
