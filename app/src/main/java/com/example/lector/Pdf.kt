package com.example.lector

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.File
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Convierte un PDF en un .cbz: cada página se dibuja con el PdfRenderer de Android a ~1600 px de ancho
 * (nítido en un móvil sin gastar memoria de más) y se guarda como JPEG dentro del zip.
 */
object Pdf {
    private const val ANCHO = 1600

    fun aCbz(ctx: Context, uri: Uri, destino: File): Boolean {
        // PdfRenderer necesita un archivo con acceso aleatorio: se copia primero a la caché.
        val temporal = File(ctx.cacheDir, "importar_${System.nanoTime()}.pdf")
        return try {
            val copiado = ctx.contentResolver.openInputStream(uri)?.use { e -> temporal.outputStream().use { e.copyTo(it) }; true } ?: false
            if (!copiado) return false
            ParcelFileDescriptor.open(temporal, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                PdfRenderer(fd).use { pdf ->
                    if (pdf.pageCount == 0) return false
                    val digitos = pdf.pageCount.toString().length.coerceAtLeast(3)
                    ZipOutputStream(destino.outputStream().buffered()).use { zip ->
                        zip.setLevel(Deflater.NO_COMPRESSION) // los JPEG ya van comprimidos
                        for (i in 0 until pdf.pageCount) {
                            pdf.openPage(i).use { pagina ->
                                val escala = (ANCHO.toFloat() / pagina.width).coerceIn(1f, 4f)
                                val w = (pagina.width * escala).toInt().coerceAtLeast(1)
                                val h = (pagina.height * escala).toInt().coerceAtLeast(1)
                                val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                                bmp.eraseColor(Color.WHITE) // las páginas sin fondo saldrían negras
                                pagina.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                zip.putNextEntry(ZipEntry("${(i + 1).toString().padStart(digitos, '0')}.jpg"))
                                bmp.compress(Bitmap.CompressFormat.JPEG, 88, zip)
                                zip.closeEntry()
                                bmp.recycle()
                            }
                        }
                    }
                }
            }
            true
        } catch (e: Exception) {
            // PDF con contraseña o dañado.
            destino.delete()
            false
        } finally {
            temporal.delete()
        }
    }
}
