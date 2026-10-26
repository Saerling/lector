package com.example.lector

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.example.lector.ext.ExtManager
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Copia de seguridad completa (versión 2): biblioteca, categorías, progreso, historial, ajustes y lo de Explorar
 * (capítulos leídos, marcas, página por la que vas, listados, fuentes ancladas, búsquedas guardadas).
 * Las copias de la versión 1 se siguen pudiendo restaurar.
 */
object CopiaSeguridad {

    fun crear(ctx: Context): String {
        val raiz = Almacen.obtener(ctx).crearCopia()
        raiz.put("ext", ExtManager.obtener(ctx).exportar())
        return raiz.toString(2)
    }

    /** Lanza una excepción si el texto no es una copia válida. */
    fun restaurar(ctx: Context, texto: String): ResultadoRestauracion {
        val raiz = JSONObject(texto)
        val r = Almacen.obtener(ctx).restaurarCopia(raiz)
        raiz.optJSONObject("ext")?.let { ExtManager.obtener(ctx).importar(it) }
        return r
    }

    /** Crea la copia .json y su informe (para la tarea con avance). */
    fun crearConInforme(ctx: Context, avance: TareaCopia.Avance?): Pair<String, TareaCopia.Informe> {
        avance?.paso(0.1f, "Reuniendo la biblioteca…")
        val a = Almacen.obtener(ctx)
        val comics = a.cargar()
        val texto = crear(ctx)
        avance?.paso(0.9f, "Guardando…")
        val enLinea = comics.count { it.enLinea }
        return texto to TareaCopia.Informe(
            titulo = "Copia de seguridad",
            bien = listOfNotNull(
                "${comics.size - enLinea} cómics locales (progreso y categorías)".takeIf { comics.size > enLinea },
                "$enLinea mangas de fuentes".takeIf { enLinea > 0 },
                "${a.categorias().size} categorías".takeIf { a.categorias().isNotEmpty() },
                "${a.historialLinea.size} entradas de historial".takeIf { a.historialLinea.isNotEmpty() },
                "Ajustes, capítulos leídos, listados y fuentes ancladas",
                "Tamaño: ${texto.length / 1024 + 1} KB",
            ),
            avisos = listOfNotNull(
                "Los archivos .cbz/.pdf no van dentro de la copia: guarda tú esos archivos".takeIf { comics.size > enLinea },
            ),
        )
    }

    /** Restaura una copia .json con su informe. */
    fun restaurarConInforme(ctx: Context, texto: String, avance: TareaCopia.Avance?): TareaCopia.Informe {
        avance?.paso(0.1f, "Leyendo la copia…")
        val antes = Almacen.obtener(ctx).cargar().size
        val r = restaurar(ctx, texto)
        avance?.paso(1f, "Listo")
        val despues = Almacen.obtener(ctx).cargar().size
        return TareaCopia.Informe(
            titulo = "Restaurar copia",
            bien = listOfNotNull(
                "${r.actualizados} entradas restauradas",
                "${despues - antes} mangas nuevos en la biblioteca".takeIf { despues > antes },
                "Ajustes, categorías, historial y capítulos leídos",
            ),
            avisos = listOfNotNull(
                "${r.pendientes} cómics locales pendientes: su progreso y categorías se aplicarán cuando vuelvas a añadir esos archivos".takeIf { r.pendientes > 0 },
            ),
        )
    }

    fun nombreArchivo(): String =
        "lector-copia-" + SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.US).format(Date()) + ".json"

    /** Carpeta por defecto de las copias automáticas (Android/data/<app>/files/copias). */
    fun carpetaInterna(ctx: Context): File =
        CarpetaApp.copias(ctx) ?: File(ctx.getExternalFilesDir(null) ?: ctx.filesDir, "copias").apply { mkdirs() }

    /**
     * Copia automática: la guarda en la carpeta elegida (si hay) o en la interna, y borra las más viejas para
     * quedarse con [Almacen.copiasMax]. Devuelve dónde se guardó (texto para el usuario) o lanza el error.
     */
    fun guardarAutomatica(ctx: Context): String {
        val a = Almacen.obtener(ctx)
        val texto = crear(ctx)
        val nombre = nombreArchivo()
        val max = a.copiasMax.coerceIn(1, 50)
        val carpeta = a.copiaCarpeta.takeIf { it.isNotBlank() }?.let { Uri.parse(it) }
        val dondeQuedo = if (carpeta != null) {
            runCatching { guardarEnArbol(ctx, carpeta, nombre, texto, max) }.getOrNull()
        } else null
        val resultado = dondeQuedo ?: run {
            val dir = carpetaInterna(ctx)
            File(dir, nombre).writeText(texto)
            dir.listFiles { f -> f.name.startsWith("lector-copia-") }?.sortedByDescending { it.lastModified() }
                ?.drop(max)?.forEach { it.delete() }
            dir.path
        }
        a.ultimaCopia = System.currentTimeMillis()
        return resultado
    }

    private fun guardarEnArbol(ctx: Context, arbol: Uri, nombre: String, texto: String, max: Int): String {
        val cr = ctx.contentResolver
        val raiz = DocumentsContract.getTreeDocumentId(arbol)
        val padre = DocumentsContract.buildDocumentUriUsingTree(arbol, raiz)
        val doc = DocumentsContract.createDocument(cr, padre, "application/json", nombre) ?: error("No se pudo crear el archivo")
        cr.openOutputStream(doc)?.use { it.write(texto.toByteArray(Charsets.UTF_8)) } ?: error("Sin permiso de escritura")
        // Borra las copias más viejas.
        val hijos = DocumentsContract.buildChildDocumentsUriUsingTree(arbol, raiz)
        val copias = ArrayList<Pair<String, Long>>()
        cr.query(
            hijos,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            ),
            null, null, null,
        )?.use { c ->
            while (c.moveToNext()) {
                val n = c.getString(1) ?: continue
                if (n.startsWith("lector-copia-")) copias += c.getString(0) to c.getLong(2)
            }
        }
        copias.sortedByDescending { it.second }.drop(max).forEach { (id, _) ->
            runCatching { DocumentsContract.deleteDocument(cr, DocumentsContract.buildDocumentUriUsingTree(arbol, id)) }
        }
        return "la carpeta elegida"
    }
}
