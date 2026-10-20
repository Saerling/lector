package prueba
import com.example.lector.TachibkFormato.Manga
import com.example.lector.TachibkFormato.Capitulo
import com.example.lector.TachibkFormato.Historial
import com.example.lector.TachibkFormato.Copia
import com.example.lector.TachibkFormato.Categoria
import com.example.lector.TachibkFormato.Fuente
import com.example.lector.TachibkFormato
import java.io.File
fun generar(ruta: String) {
    val fuentes = listOf(1814471239661920836L, 7374498796507972405L, 999L)
    val mangas = (0 until 600).map { i ->
        val f = fuentes[i % 3]
        Manga(
            fuente = f, url = "/manga/prueba-$i", titulo = "Manga de prueba $i", autor = "Autor $i", descripcion = "Descripción $i",
            generos = listOf("Acción", "Drama"), estado = 1, portada = null, anadido = 1_700_000_000_000 + i,
            capitulos = (0 until 80).map { c -> Capitulo("/manga/prueba-$i/cap-$c", "Capítulo ${c + 1}", leido = c < 40, marcado = c == 5, pagina = if (c == 40) 7 else 0, numero = (c + 1).toFloat()) },
            categorias = listOf((i % 5).toLong()),
            historial = listOf(Historial("/manga/prueba-$i/cap-40", 1_700_000_000_000 + i * 1000L)),
        )
    }
    val copia = Copia(mangas, (0 until 5).map { Categoria("Categoría $it", it.toLong()) }, fuentes.map { Fuente("Fuente $it", it) })
    File(ruta).writeBytes(TachibkFormato.codificar(copia))
}
