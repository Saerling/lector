@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
package prueba
import com.example.lector.TachibkFormato
import kotlinx.serialization.protobuf.ProtoBuf
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.GZIPOutputStream

fun gz(b: ByteArray) = ByteArrayOutputStream().also { o -> GZIPOutputStream(o).use { it.write(b) } }.toByteArray()

fun pruebaLigera(mihon: Backup) {
    val bytes = gz(ProtoBuf.encodeToByteArray(Backup.serializer(), mihon))
    val cab = TachibkFormato.leerCabecera(bytes)
    check(cab.categorias.map { it.nombre } == listOf("Leyendo", "Pendiente", "Ocultos") && cab.categorias[2].oculta, "ligero: categorías")
    check(cab.fuentes.size == 1, "ligero: fuentes")
    val ms = ArrayList<TachibkFormato.MangaLigero>()
    TachibkFormato.recorrerMangas(bytes) { ms += it }
    val m = ms[0]
    check(ms.size == 2 && m.titulo == "One Piece" && m.totalCapitulos == 2, "ligero: mangas y total de capítulos")
    check(m.conEstado.size == 2 && m.conEstado[0].leido && m.conEstado[1].marcado && m.conEstado[1].pagina == 7L, "ligero: capítulos con estado")
    check(m.categorias == listOf(0L, 2L), "ligero: categorías del manga")
    check(m.seguimiento.single().mediaId == 21L && m.ultimaLectura?.url == "/c/2", "ligero: AniList e historial")
    check(m.tituloPropio == "Mi One Piece" && m.estadoPropio == 2 && !ms[1].favorito, "ligero: editados y no favorito")
}

/** Copia enorme al estilo Mihon (con TODOS los capítulos, como hace Mihon). */
fun generarEnorme(ruta: String, mangas: Int, caps: Int) {
    val fuentes = listOf(1814471239661920836L, 7374498796507972405L, 8664735734660636295L, 1600533831542201953L, 123456789L)
    val lista = (0 until mangas).map { i ->
        val f = fuentes[i % fuentes.size]
        BackupManga(
            source = f, url = "/series/una-serie-con-nombre-largo-numero-$i/", title = "Una serie con un nombre bastante largo $i",
            author = "Autor $i", artist = "Artista $i", description = "Descripción larga ".repeat(20), genre = listOf("Acción", "Aventura", "Fantasía", "Drama"),
            status = 1, thumbnailUrl = "https://cdn.ejemplo.com/portadas/serie-$i/portada-grande.webp", dateAdded = 1_700_000_000_000 + i,
            chapters = (0 until caps).map { c ->
                BackupChapter(
                    "/series/una-serie-con-nombre-largo-numero-$i/capitulo-$c/", "Capítulo ${c + 1}: Un título de capítulo $c",
                    scanlator = "Grupo de traducción", read = c < caps * 6 / 10, bookmark = c % 50 == 0, lastPageRead = if (c == caps * 6 / 10) 12 else 0,
                    dateFetch = 1_700_000_000_000, dateUpload = 1_690_000_000_000 + c, chapterNumber = (c + 1).toFloat(), sourceOrder = c.toLong(),
                )
            },
            categories = listOf((i % 8).toLong()),
            history = listOf(BackupHistory("/series/una-serie-con-nombre-largo-numero-$i/capitulo-${caps * 6 / 10}/", 1_700_000_000_000 + i * 1000L)),
        )
    }
    val b = Backup(lista, (0 until 8).map { BackupCategory("Categoría $it", it.toLong()) }, fuentes.map { BackupSource("Fuente $it", it) })
    val crudo = ProtoBuf.encodeToByteArray(Backup.serializer(), b)
    File(ruta).writeBytes(gz(crudo))
    println("enorme: ${mangas * caps} capítulos, sin comprimir ${crudo.size / 1024 / 1024} MB, comprimido ${File(ruta).length() / 1024} KB")
}
