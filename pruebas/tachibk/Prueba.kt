@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
package prueba

import com.example.lector.TachibkFormato
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.protobuf.ProtoNumber
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

// Copia fiel de los modelos de Mihon/Komikku (eu.kanade.tachiyomi.data.backup.models).
@Serializable data class Backup(
    @ProtoNumber(1) val backupManga: List<BackupManga>,
    @ProtoNumber(2) var backupCategories: List<BackupCategory> = emptyList(),
    @ProtoNumber(101) var backupSources: List<BackupSource> = emptyList(),
)
@Serializable class BackupManga(
    @ProtoNumber(1) var source: Long,
    @ProtoNumber(2) var url: String,
    @ProtoNumber(3) var title: String = "",
    @ProtoNumber(4) var artist: String? = null,
    @ProtoNumber(5) var author: String? = null,
    @ProtoNumber(6) var description: String? = null,
    @ProtoNumber(7) var genre: List<String> = emptyList(),
    @ProtoNumber(8) var status: Int = 0,
    @ProtoNumber(9) var thumbnailUrl: String? = null,
    @ProtoNumber(13) var dateAdded: Long = 0,
    @ProtoNumber(14) var viewer: Int = 0,
    @ProtoNumber(16) var chapters: List<BackupChapter> = emptyList(),
    @ProtoNumber(17) var categories: List<Long> = emptyList(),
    @ProtoNumber(18) var tracking: List<BackupTracking> = emptyList(),
    @ProtoNumber(100) var favorite: Boolean = true,
    @ProtoNumber(101) var chapterFlags: Int = 0,
    @ProtoNumber(103) var viewer_flags: Int? = null,
    @ProtoNumber(104) var history: List<BackupHistory> = emptyList(),
    @ProtoNumber(106) var lastModifiedAt: Long = 0,
    @ProtoNumber(107) var favoriteModifiedAt: Long? = null,
    @ProtoNumber(108) var excludedScanlators: List<String> = emptyList(),
    @ProtoNumber(109) var version: Long = 0,
    @ProtoNumber(110) var notes: String = "",
    @ProtoNumber(111) var initialized: Boolean = false,
    @ProtoNumber(602) var customStatus: Int = 0,
    @ProtoNumber(603) var customThumbnailUrl: String? = null,
    @ProtoNumber(800) var customTitle: String? = null,
    @ProtoNumber(801) var customArtist: String? = null,
    @ProtoNumber(802) var customAuthor: String? = null,
    @ProtoNumber(804) var customDescription: String? = null,
    @ProtoNumber(805) var customGenre: List<String>? = null,
)
@Serializable class BackupChapter(
    @ProtoNumber(1) var url: String,
    @ProtoNumber(2) var name: String,
    @ProtoNumber(3) var scanlator: String? = null,
    @ProtoNumber(4) var read: Boolean = false,
    @ProtoNumber(5) var bookmark: Boolean = false,
    @ProtoNumber(6) var lastPageRead: Long = 0,
    @ProtoNumber(7) var dateFetch: Long = 0,
    @ProtoNumber(8) var dateUpload: Long = 0,
    @ProtoNumber(9) var chapterNumber: Float = 0F,
    @ProtoNumber(10) var sourceOrder: Long = 0,
    @ProtoNumber(11) var lastModifiedAt: Long = 0,
    @ProtoNumber(12) var version: Long = 0,
)
@Serializable class BackupCategory(
    @ProtoNumber(1) var name: String,
    @ProtoNumber(2) var order: Long = 0,
    @ProtoNumber(3) var id: Long = 0,
    @ProtoNumber(100) var flags: Long = 0,
    @ProtoNumber(900) var hidden: Boolean = false,
)
@Serializable data class BackupHistory(
    @ProtoNumber(1) var url: String,
    @ProtoNumber(2) var lastRead: Long,
    @ProtoNumber(3) var readDuration: Long = 0,
)
@Serializable data class BackupSource(
    @ProtoNumber(1) var name: String = "",
    @ProtoNumber(2) var sourceId: Long,
)
@Serializable data class BackupTracking(
    @ProtoNumber(1) var syncId: Int,
    @ProtoNumber(2) var libraryId: Long,
    @ProtoNumber(3) var mediaIdInt: Int = 0,
    @ProtoNumber(4) var trackingUrl: String = "",
    @ProtoNumber(5) var title: String = "",
    @ProtoNumber(6) var lastChapterRead: Float = 0F,
    @ProtoNumber(7) var totalChapters: Int = 0,
    @ProtoNumber(8) var score: Float = 0F,
    @ProtoNumber(9) var status: Int = 0,
    @ProtoNumber(10) var startedReadingDate: Long = 0,
    @ProtoNumber(11) var finishedReadingDate: Long = 0,
    @ProtoNumber(12) var private: Boolean = false,
    @ProtoNumber(100) var mediaId: Long = 0,
)

fun check(c: Boolean, m: String) { if (!c) { println("FALLO: $m"); fallos++ } else println("ok: $m") }
var fallos = 0

fun main() {
    mainVacia()
    // 1) Mihon escribe → Lector lee.
    val idNegativo = -1540562214L * 3_000_000_000L // ids de fuente pueden ser negativos y enormes
    val mihon = Backup(
        backupManga = listOf(
            BackupManga(
                source = idNegativo, url = "/manga/one-piece", title = "One Piece", author = "Oda", description = "Piratas",
                genre = listOf("Acción", "Aventura"), status = 1, thumbnailUrl = "https://x/p.jpg", dateAdded = 1_700_000_000_000,
                chapters = listOf(
                    BackupChapter("/c/1", "Capítulo 1", scanlator = "Grupo", read = true, chapterNumber = 1f, dateUpload = 123),
                    BackupChapter("/c/2", "Capítulo 2", bookmark = true, lastPageRead = 7, chapterNumber = 2.5f),
                ),
                categories = listOf(0, 2),
                tracking = listOf(BackupTracking(2, 999, mediaIdInt = 21, title = "One Piece AL", lastChapterRead = 1f, mediaId = 21)),
                history = listOf(BackupHistory("/c/2", 1_700_000_500_000)),
                customTitle = "Mi One Piece", customStatus = 2,
            ),
            BackupManga(source = 5, url = "/solo-historial", title = "Visto", favorite = false, history = listOf(BackupHistory("/h/1", 5))),
        ),
        backupCategories = listOf(BackupCategory("Leyendo", 0), BackupCategory("Pendiente", 1), BackupCategory("Ocultos", 2, hidden = true)),
        backupSources = listOf(BackupSource("Fuente X", idNegativo)),
    )
    val crudo = ProtoBuf.encodeToByteArray(Backup.serializer(), mihon)
    val gz = ByteArrayOutputStream().also { o -> GZIPOutputStream(o).use { it.write(crudo) } }.toByteArray()
    check(TachibkFormato.esTachibk(gz), "detecta .tachibk")
    check(!TachibkFormato.esTachibk("{\"a\":1}".toByteArray()), "un .json de Lector no es .tachibk")
    val leida = TachibkFormato.decodificar(gz)
    val m = leida.mangas[0]
    check(leida.mangas.size == 2, "2 mangas")
    check(m.fuente == idNegativo, "id de fuente negativo grande (${m.fuente})")
    check(m.url == "/manga/one-piece" && m.titulo == "One Piece" && m.autor == "Oda" && m.descripcion == "Piratas", "datos básicos")
    check(m.generos == listOf("Acción", "Aventura"), "géneros")
    check(m.estado == 1 && m.portada == "https://x/p.jpg" && m.anadido == 1_700_000_000_000, "estado, portada, fecha")
    check(m.capitulos.size == 2 && m.capitulos[0].leido && m.capitulos[0].nombre == "Capítulo 1" && m.capitulos[0].scanlator == "Grupo", "capítulo leído")
    check(m.capitulos[1].marcado && m.capitulos[1].pagina == 7L && m.capitulos[1].numero == 2.5f, "capítulo marcado + página + número")
    check(m.categorias == listOf(0L, 2L), "categorías del manga ${m.categorias}")
    check(m.seguimiento.single().servicio == 2 && m.seguimiento.single().mediaId == 21L, "AniList")
    check(m.historial.single().url == "/c/2" && m.historial.single().ultimaLectura == 1_700_000_500_000, "historial")
    check(m.tituloPropio == "Mi One Piece" && m.estadoPropio == 2, "datos editados de Komikku")
    check(m.favorito && !leida.mangas[1].favorito, "favorito / solo historial")
    check(leida.categorias.map { it.nombre } == listOf("Leyendo", "Pendiente", "Ocultos") && leida.categorias[2].oculta, "categorías")

    // 2) Lector escribe → Mihon lee (con su misma librería).
    val nuestra = TachibkFormato.codificar(leida)
    val bytes = GZIPInputStream(nuestra.inputStream()).use { it.readBytes() }
    val vuelta = ProtoBuf.decodeFromByteArray(Backup.serializer(), bytes)
    val v = vuelta.backupManga[0]
    check(vuelta.backupManga.size == 2, "Mihon lee 2 mangas")
    check(v.source == idNegativo && v.url == "/manga/one-piece" && v.title == "One Piece", "Mihon: fuente, url, título")
    check(v.author == "Oda" && v.genre == listOf("Acción", "Aventura") && v.status == 1 && v.dateAdded == 1_700_000_000_000, "Mihon: datos")
    check(v.chapters.size == 2 && v.chapters[0].read && v.chapters[1].bookmark && v.chapters[1].lastPageRead == 7L, "Mihon: capítulos")
    check(v.chapters[1].chapterNumber == 2.5f && v.chapters[0].scanlator == "Grupo", "Mihon: número y scanlator")
    check(v.categories == listOf(0L, 2L), "Mihon: categorías del manga")
    check(v.tracking.single().syncId == 2 && v.tracking.single().mediaId == 21L, "Mihon: AniList")
    check(v.history.single().lastRead == 1_700_000_500_000, "Mihon: historial")
    check(v.customTitle == "Mi One Piece" && v.customStatus == 2, "Komikku: datos editados")
    check(!vuelta.backupManga[1].favorite, "Mihon: no favorito")
    check(vuelta.backupCategories.map { it.name to it.order } == listOf("Leyendo" to 0L, "Pendiente" to 1L, "Ocultos" to 2L) && vuelta.backupCategories[2].hidden, "Mihon: categorías")
    println(if (fallos == 0) "TODO OK" else "FALLOS: $fallos")
}
