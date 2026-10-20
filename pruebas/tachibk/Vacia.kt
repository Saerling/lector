@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
package prueba
import kotlinx.serialization.protobuf.ProtoBuf
import java.util.zip.GZIPInputStream
fun mainVacia() {
    val b = com.example.lector.TachibkFormato.codificar(com.example.lector.TachibkFormato.Copia())
    val r = runCatching { ProtoBuf.decodeFromByteArray(Backup.serializer(), GZIPInputStream(b.inputStream()).readBytes()) }
    println("vacía: " + r.map { "ok ${it.backupManga.size}" }.getOrElse { "ERROR ${it.message}" })
}
