// Cabecera copiada de Mochy Music (github.com/realidkroo/Mochy-Music, GPL-3.0):
// app/src/main/kotlin/com/mochyy/mochymusic/ui/component/PlaylistSheetKit.kt (SheetHeader, SheetHeaderGap); la hoja es
// su PopupSheet y las filas su MenuItemRow, como en ui/menu/SongMenu.kt.
// Shiori: la portada es la foto del elemento que se mantuvo pulsado (HeldItem), recortada a su parte de arriba.
package com.example.lector.ui.mochy

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.lector.ui.SuaveShape
import com.example.lector.ui.bitchord.HeldBackdrop
import com.example.lector.ui.bitchord.HeldContextMenu
import com.example.lector.ui.bitchord.HeldItem
import com.example.lector.ui.bitchord.LocalHeldMenuMochy
import com.example.lector.ui.mochy.sheet.MenuContent
import com.example.lector.ui.mochy.sheet.MenuRowSurface
import com.example.lector.ui.mochy.sheet.PopupSheet

/** El hueco entre la cabecera de una hoja y sus primeras acciones (Mochy ya no dibuja una línea ahí). */
internal val SheetHeaderGap = 20.dp

/**
 * El menú de mantener pulsado, según Personalización → Estilo: la hoja de Mochy (cabecera con la portada y el título,
 * luego las acciones en píldoras) o el menú elevado de BitChord. Misma firma que [HeldContextMenu] más el título; las
 * [actions] son [com.example.lector.ui.bitchord.HeldMenuAction] y se dibujan en el estilo que toque.
 */
@Composable
fun <T : Any> MenuMantener(
    item: T?,
    held: HeldItem?,
    hazeState: HeldBackdrop,
    onDismiss: () -> Unit,
    onPreviewClick: ((T) -> Unit)?,
    titulo: (T) -> String,
    subtitulo: (T) -> String? = { null },
    actions: @Composable (T) -> Unit,
) {
    if (EstiloApp.menuMantener == EstiloApp.MenuMantener.ELEVADO) {
        HeldContextMenu(item, held, hazeState, onDismiss, onPreviewClick, actions)
        return
    }
    if (item == null) return
    PopupSheet(onClosed = onDismiss) {
        SheetHeader(held, titulo(item), subtitulo(item), onClick = onPreviewClick?.let { { it(item) } })
        Spacer(Modifier.height(SheetHeaderGap))
        CompositionLocalProvider(LocalHeldMenuMochy provides true) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) { actions(item) }
        }
    }
}

/** SheetHeader de Mochy: portada redondeada, el título y una segunda línea. Tocarla abre lo que representa. */
@Composable
private fun SheetHeader(held: HeldItem?, title: String, subtitle: String?, onClick: (() -> Unit)?) {
    run {
        val src = remember { MutableInteractionSource() }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().pressBounce(src, pressedScale = 0.97f)
                .then(if (onClick != null) Modifier.clickable(src, null, onClick = onClick) else Modifier),
        ) {
            Box(Modifier.size(48.dp, 68.dp).clip(SuaveShape(12.dp)).background(MenuRowSurface)) {
                val img = held?.image
                if (img != null) Image(img, null, Modifier.matchParentSize(), alignment = Alignment.TopStart, contentScale = ContentScale.Crop)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    title, color = MenuContent, fontFamily = InterFontFamily, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                    maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 20.sp,
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        subtitle, color = MenuContent.copy(alpha = 0.6f), fontFamily = InterFontFamily, fontSize = 13.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }
    }
}
