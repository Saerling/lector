package eu.kanade.tachiyomi.network

import android.content.Context

/**
 * Existe para que las extensiones que lo referencian carguen. QuickJS no está incluido todavía:
 * las que evalúen JavaScript fallarán con UnsupportedOperationException (ver HANDOFF).
 */
@Suppress("UNUSED", "UNUSED_PARAMETER")
class JavaScriptEngine(context: Context) {
    suspend fun <T> evaluate(script: String): T =
        throw UnsupportedOperationException("JavaScriptEngine no está disponible todavía en Lector")
}
