package com.example.lector

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

// Qué cómics mostrar según cuánto los hayas leído.
enum class Filtro { TODOS, SIN_LEER, EN_CURSO, TERMINADOS }

// Sin leer = en la primera página; terminado = en la última; el resto, en curso.
fun estadoDe(c: Comic): Filtro = when {
    // Manga en línea: ultimaPagina = capítulos leídos, paginas = capítulos totales.
    c.enLinea -> when {
        c.ultimaPagina <= 0 -> Filtro.SIN_LEER
        c.paginas > 0 && c.ultimaPagina >= c.paginas -> Filtro.TERMINADOS
        else -> Filtro.EN_CURSO
    }
    c.ultimaPagina == 0 -> Filtro.SIN_LEER
    c.ultimaPagina >= c.paginas - 1 -> Filtro.TERMINADOS
    else -> Filtro.EN_CURSO
}

fun progresoDe(c: Comic): Float =
    if (c.enLinea) {
        if (c.paginas > 0) c.ultimaPagina.toFloat() / c.paginas else 0f
    } else if (c.paginas > 1) c.ultimaPagina.toFloat() / (c.paginas - 1) else 0f

/** Estado de publicación (SManga.status) en texto. */
fun estadoPublicacion(e: Int) = when (e) {
    1 -> "En emisión"
    2 -> "Completado"
    3 -> "Licenciado"
    4 -> "Publicación finalizada"
    5 -> "Cancelado"
    6 -> "En pausa"
    else -> "Estado desconocido"
}

@Composable
fun FilaOpcion(etiqueta: String, marcada: Boolean, alElegir: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { alElegir() },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = marcada, onClick = null)
        Spacer(Modifier.width(8.dp))
        Text(etiqueta)
    }
}

@Composable
fun FilaInterruptor(titulo: String, detalle: String?, activado: Boolean, alCambiar: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(titulo)
            if (detalle != null) {
                Text(detalle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(8.dp))
        Switch(checked = activado, onCheckedChange = alCambiar)
    }
}

/** Búsqueda con operadores (biblioteca): palabras sueltas, "frases", -excluir, fuente:, autor:, genero:. */
class ConsultaBiblioteca(texto: String) {
    private data class Termino(val campo: String?, val valor: String, val excluir: Boolean)
    private val terminos: List<Termino> = Regex("(-?)(\\w+:)?(\"[^\"]*\"|\\S+)").findAll(texto.trim()).map { m ->
        val excluir = m.groupValues[1] == "-"
        val campo = m.groupValues[2].removeSuffix(":").lowercase().ifBlank { null }
        val valor = m.groupValues[3].trim('"').lowercase()
        Termino(campo, valor, excluir)
    }.filter { it.valor.isNotBlank() }.toList()

    val vacia: Boolean get() = terminos.isEmpty()

    fun coincide(c: Comic, nombreFuente: String): Boolean = terminos.all { t ->
        val hay = when (t.campo) {
            "fuente", "src", "f" -> nombreFuente.lowercase().contains(t.valor)
            "autor", "aut", "a" -> c.autor.orEmpty().lowercase().contains(t.valor)
            "genero", "género", "tag", "g" -> c.generos.any { it.lowercase().contains(t.valor) }
            else -> c.titulo.lowercase().contains(t.valor) || c.autor.orEmpty().lowercase().contains(t.valor) ||
                c.generos.any { it.lowercase() == t.valor } || nombreFuente.lowercase() == t.valor
        }
        if (t.excluir) !hay else hay
    }
}
