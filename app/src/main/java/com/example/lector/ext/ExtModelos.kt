package com.example.lector.ext

import java.util.Locale

/** Una fuente (sitio) que ofrece una extensión. */
data class FuenteInfo(val id: Long, val nombre: String, val lang: String, val baseUrl: String, val pkg: String)

/** Lo que dice el listado (repositorio) de una extensión que se puede instalar. */
data class ExtDisponible(
    val nombre: String,
    val pkg: String,
    val apkUrl: String,
    val iconoUrl: String,
    val lang: String,
    val versionCode: Long,
    val versionName: String,
    val nsfw: Boolean,
    val fuentes: List<FuenteInfo>,
    val repoUrl: String,
    val repoSigla: String,
    // De Komikku: versión de la librería de extensiones y clave de firma del listado que la ofrece.
    val libVersion: Double = 0.0,
    val firma: String = "",
)

/** Una extensión que ya está instalada en el móvil. */
data class ExtInstalada(
    val nombre: String,
    val pkg: String,
    val versionName: String,
    val versionCode: Long,
    val lang: String,
    val nsfw: Boolean,
    val confiable: Boolean,
    val firma: String,
    val repoSigla: String?,
    val actualizacion: ExtDisponible?,
    // De Komikku (ExtensionLoader): versión de librería, todas las firmas SHA-256 y motivo por el que no se puede cargar.
    val libVersion: Double = 0.0,
    val firmas: List<String> = emptyList(),
    val errorCarga: String? = null,
)

/** Una fuente lista para usar (de una extensión instalada y de confianza). */
data class FuenteUI(
    val id: Long,
    val nombre: String,
    val lang: String,
    val nsfw: Boolean,
    val pkg: String,
    val baseUrl: String,
)

/** Un listado de extensiones que añade el usuario (Keiyoushi, Suwayomi...). */
data class RepoExt(
    val url: String,
    val nombre: String,
    val activo: Boolean = true,
    val web: String? = null,
    // De Komikku (ExtensionStore): etiqueta corta, clave de firma, formato clásico, lista aparte y contacto.
    val etiqueta: String = "",
    val claveFirma: String = "",
    val esLegacy: Boolean = false,
    val urlLista: String? = null,
    val discord: String? = null,
) {
    /** Siglas que se ven junto a la versión: "@KEI". */
    val sigla: String get() = etiqueta.ifBlank { nombre }.filter { it.isLetterOrDigit() }.take(3).uppercase()
}

enum class Fase { DESCARGANDO, INSTALANDO, ERROR }

data class Progreso(val fase: Fase, val porcentaje: Int = 0, val error: String? = null)

// ---- Idiomas ----
fun nombreIdioma(lang: String): String = when (lang) {
    "all" -> "Varios idiomas"
    "other", "" -> "Otros"
    else -> {
        val loc = Locale.forLanguageTag(lang)
        loc.getDisplayName(loc).replaceFirstChar { it.uppercase() }.ifBlank { lang }
    }
}

private val banderas = mapOf(
    "es" to "\uD83C\uDDEA\uD83C\uDDF8", "en" to "\uD83C\uDDFA\uD83C\uDDF8", "vi" to "\uD83C\uDDFB\uD83C\uDDF3",
    "pt" to "\uD83C\uDDE7\uD83C\uDDF7", "fr" to "\uD83C\uDDEB\uD83C\uDDF7", "de" to "\uD83C\uDDE9\uD83C\uDDEA",
    "it" to "\uD83C\uDDEE\uD83C\uDDF9", "ja" to "\uD83C\uDDEF\uD83C\uDDF5", "ko" to "\uD83C\uDDF0\uD83C\uDDF7",
    "zh" to "\uD83C\uDDE8\uD83C\uDDF3", "ru" to "\uD83C\uDDF7\uD83C\uDDFA", "ar" to "\uD83C\uDDF8\uD83C\uDDE6",
    "tr" to "\uD83C\uDDF9\uD83C\uDDF7", "id" to "\uD83C\uDDEE\uD83C\uDDE9", "th" to "\uD83C\uDDF9\uD83C\uDDED",
    "pl" to "\uD83C\uDDF5\uD83C\uDDF1", "all" to "\uD83C\uDDFA\uD83C\uDDF3",
)

fun bandera(lang: String): String = banderas[lang.substringBefore('-')] ?: "\uD83C\uDF10"
