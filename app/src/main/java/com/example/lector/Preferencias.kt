package com.example.lector

import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateOf
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/*
 * Ajustes guardados en SharedPreferences que además son "estado de Compose": al cambiarlos, las pantallas que los
 * leen se repintan solas. Se usan como `var algo by prefBool(prefs, "clave", false)`.
 * Sustituyen al patrón repetido `var x by mutableStateOf(...); fun elegirX(v) { x = v; prefs.edit()... }`.
 */

private class PrefDelegado<T>(
    inicial: T,
    private val guardar: (T) -> Unit,
) : ReadWriteProperty<Any?, T> {
    private val estado = mutableStateOf(inicial)
    override fun getValue(thisRef: Any?, property: KProperty<*>): T = estado.value
    override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
        if (estado.value == value) return
        estado.value = value
        guardar(value)
    }
}

fun prefBool(p: SharedPreferences, clave: String, defecto: Boolean): ReadWriteProperty<Any?, Boolean> =
    PrefDelegado(p.getBoolean(clave, defecto)) { p.edit().putBoolean(clave, it).apply() }

fun prefInt(p: SharedPreferences, clave: String, defecto: Int): ReadWriteProperty<Any?, Int> =
    PrefDelegado(p.getInt(clave, defecto)) { p.edit().putInt(clave, it).apply() }

fun prefLong(p: SharedPreferences, clave: String, defecto: Long): ReadWriteProperty<Any?, Long> =
    PrefDelegado(p.getLong(clave, defecto)) { p.edit().putLong(clave, it).apply() }

fun prefFloat(p: SharedPreferences, clave: String, defecto: Float): ReadWriteProperty<Any?, Float> =
    PrefDelegado(p.getFloat(clave, defecto)) { p.edit().putFloat(clave, it).apply() }

fun prefString(p: SharedPreferences, clave: String, defecto: String): ReadWriteProperty<Any?, String> =
    PrefDelegado(p.getString(clave, defecto) ?: defecto) { p.edit().putString(clave, it).apply() }

inline fun <reified T : Enum<T>> prefEnum(p: SharedPreferences, clave: String, defecto: T): ReadWriteProperty<Any?, T> {
    val leido = p.getString(clave, null)?.let { n -> enumValues<T>().firstOrNull { it.name == n } } ?: defecto
    return prefEnumCon(leido) { p.edit().putString(clave, it.name).apply() }
}

fun <T> prefEnumCon(inicial: T, guardar: (T) -> Unit): ReadWriteProperty<Any?, T> = PrefDelegado(inicial, guardar)

/** Estado de tres valores de los filtros (como las casillas de Komikku): ignorar, incluir o excluir. */
enum class Tri { IGNORAR, INCLUIR, EXCLUIR;
    fun siguiente(): Tri = when (this) { IGNORAR -> INCLUIR; INCLUIR -> EXCLUIR; EXCLUIR -> IGNORAR }
    /** ¿Pasa el filtro un elemento que cumple (o no) la condición? */
    fun deja(cumple: Boolean): Boolean = when (this) { IGNORAR -> true; INCLUIR -> cumple; EXCLUIR -> !cumple }
}
