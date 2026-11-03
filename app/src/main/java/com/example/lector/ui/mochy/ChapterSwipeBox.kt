// Copiado de Mochy Music (github.com/realidkroo/Mochy-Music, GPL-3.0): app/src/main/kotlin/com/mochyy/mochymusic/ui/component/SongSwipeBox.kt
//
// Shiori: las acciones de canción pasan a ser de capítulo, y las claves de DataStore (SwipeRightActionKey /
// SwipeLeftActionKey + rememberEnumPreference) pasan a SharedPreferences en [GestosCapitulos].

package com.example.lector.ui.mochy

import androidx.compose.ui.graphics.toArgb
import android.content.Context
import android.content.SharedPreferences
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import com.example.lector.ui.Iconos

// what sliding a chapter row can do, picked per side in Ajustes > Personalización > Gestos
enum class ChapterSwipeAction(
    val title: String,
    val icon: ImageVector,
    val color: Color,
    val pillLabel: String,
) {

    NONE("Nada", Icons.Rounded.Close, Color(0xFF8E8E93), "Nada"),
    MARK_READ("Marcar leído / no leído", Icons.Rounded.Check, SwipeQueueColor, "Marcar leído"),
    BOOKMARK("Marcar / desmarcar", Iconos.Marca, SwipeSaveColor, "Marcar"),
    DOWNLOAD("Descargar / borrar descarga", Iconos.Descargar, SwipeDownloadColor, "Descargar"),
    MARK_PREVIOUS_READ("Marcar anteriores como leídos", Icons.Rounded.KeyboardArrowDown, SwipePlayNextColor, "Anteriores leídos");

    /** El color elegido por el usuario para esta acción (Personalización → Gestos), o el de Mochy. */
    val colorActual: Color get() = GestosCapitulos.colores[name]?.let { Color(it) } ?: color

}

val DefaultSwipeRight = ChapterSwipeAction.MARK_READ
val DefaultSwipeLeft = ChapterSwipeAction.BOOKMARK

/** Lo que cada lado hace, guardado en SharedPreferences («gestos»). */
object GestosCapitulos {
    private var prefs: SharedPreferences? = null

    /** deslizar a la derecha (se ve a la izquierda de la fila) */
    var derecha by mutableStateOf(DefaultSwipeRight); private set
    /** deslizar a la izquierda */
    var izquierda by mutableStateOf(DefaultSwipeLeft); private set

    fun iniciar(ctx: Context) {
        if (prefs != null) return
        val p = ctx.applicationContext.getSharedPreferences("gestos", Context.MODE_PRIVATE).also { prefs = it }
        derecha = leer(p, "derecha", DefaultSwipeRight)
        izquierda = leer(p, "izquierda", DefaultSwipeLeft)
        ChapterSwipeAction.entries.forEach { a -> if (p.contains("color_${a.name}")) colores[a.name] = p.getInt("color_${a.name}", 0) }
    }

    private fun leer(p: SharedPreferences, clave: String, def: ChapterSwipeAction) =
        runCatching { ChapterSwipeAction.valueOf(p.getString(clave, null)!!) }.getOrDefault(def)

    /** Colores propios por acción (ARGB); sin entrada = el de Mochy. */
    val colores = androidx.compose.runtime.mutableStateMapOf<String, Int>()
    fun elegirColor(a: ChapterSwipeAction, c: Color?) {
        if (c == null) colores.remove(a.name) else colores[a.name] = c.toArgbCompat()
        prefs?.edit()?.apply { if (c == null) remove("color_${a.name}") else putInt("color_${a.name}", c.toArgbCompat()) }?.apply()
    }
    private fun Color.toArgbCompat(): Int = toArgb()

    fun elegirDerecha(a: ChapterSwipeAction) { derecha = a; prefs?.edit()?.putString("derecha", a.name)?.apply() }
    fun elegirIzquierda(a: ChapterSwipeAction) { izquierda = a; prefs?.edit()?.putString("izquierda", a.name)?.apply() }
}

/** Cómo está el capítulo ahora: decide el icono y el texto de las acciones que alternan. */
class EstadoCapituloSwipe(
    val leido: Boolean,
    val marcado: Boolean,
    /** descargado o en cola / descargando (entonces la acción lo quita) */
    val descargado: Boolean,
    val descargando: Boolean = false,
)

@Composable
fun ChapterSwipeBox(
    estado: EstadoCapituloSwipe,
    onAction: (ChapterSwipeAction) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    slab: Boolean = true,
    content: @Composable () -> Unit,
) {
    GestosCapitulos.iniciar(LocalContext.current)
    val right = GestosCapitulos.derecha
    val left = GestosCapitulos.izquierda
    SwipeActionsBox(
        modifier = modifier,
        enabled = enabled,
        slab = slab,
        start = if (right == ChapterSwipeAction.NONE) null else {
            { chosenSwipeAction(right, estado, onAction) }
        },
        end = if (left == ChapterSwipeAction.NONE) null else {
            { chosenSwipeAction(left, estado, onAction) }
        },
        content = content,
    )
}

/** La pastilla de [chosen] para un capítulo en [estado] (las que alternan cambian de icono y texto). */
fun chapterSwipeAction(
    chosen: ChapterSwipeAction,
    estado: EstadoCapituloSwipe,
    onTrigger: () -> Unit,
): SwipeAction = when (chosen) {
    ChapterSwipeAction.MARK_READ ->
        if (estado.leido) SwipeAction(Iconos.OjoTachado, chosen.colorActual, "Marcar no leído", onTrigger)
        else SwipeAction(chosen.icon, chosen.colorActual, chosen.pillLabel, onTrigger)
    ChapterSwipeAction.BOOKMARK ->
        if (estado.marcado) SwipeAction(Iconos.MarcaBorde, chosen.colorActual, "Desmarcar", onTrigger)
        else SwipeAction(chosen.icon, chosen.colorActual, chosen.pillLabel, onTrigger)
    ChapterSwipeAction.DOWNLOAD -> when {
        estado.descargando -> SwipeAction(Icons.Rounded.Close, SwipeRemoveColor, "Cancelar descarga", onTrigger)
        estado.descargado -> SwipeAction(Icons.Rounded.Delete, SwipeRemoveColor, "Borrar descarga", onTrigger)
        else -> SwipeAction(chosen.icon, chosen.colorActual, chosen.pillLabel, onTrigger)
    }
    else -> SwipeAction(chosen.icon, chosen.colorActual, chosen.pillLabel, onTrigger)
}

@Composable
private fun chosenSwipeAction(
    chosen: ChapterSwipeAction,
    estado: EstadoCapituloSwipe,
    onAction: (ChapterSwipeAction) -> Unit,
): SwipeAction = chapterSwipeAction(chosen, estado) { onAction(chosen) }
