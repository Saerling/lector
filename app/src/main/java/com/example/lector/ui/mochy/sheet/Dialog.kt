// Copiado de Mochy Music (github.com/realidkroo/Mochy-Music, GPL-3.0): app/src/main/kotlin/com/mochyy/mochymusic/ui/component/Dialog.kt
package com.example.lector.ui.mochy.sheet

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * Mochy's DefaultDialog. Shiori adds [properties] (DialogoAlerta keeps AlertDialog's signature) and caps the width
 * on tablets (Mochy lets it stretch).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DefaultDialog(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    icon: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    buttons: (@Composable RowScope.() -> Unit)? = null,
    horizontalAlignment: Alignment.Horizontal = Alignment.CenterHorizontally,
    properties: DialogProperties = DialogProperties(usePlatformDefaultWidth = false),
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = properties,
    ) {
        Surface(
            modifier = Modifier.padding(24.dp).widthIn(max = 560.dp),
            shape = AlertDialogDefaults.shape,
            color = AlertDialogDefaults.containerColor,
            tonalElevation = AlertDialogDefaults.TonalElevation
        ) {
            Column(
                horizontalAlignment = horizontalAlignment,
                modifier = modifier
                    .padding(24.dp)
            ) {
                if (icon != null) {
                    CompositionLocalProvider(LocalContentColor provides AlertDialogDefaults.iconContentColor) {
                        Box(
                            Modifier.align(Alignment.CenterHorizontally)
                        ) {
                            icon()
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                }
                if (title != null) {
                    CompositionLocalProvider(LocalContentColor provides AlertDialogDefaults.titleContentColor) {
                        ProvideTextStyle(MaterialTheme.typography.headlineSmall) {
                            Box(

                                Modifier.align(if (icon == null) Alignment.Start else Alignment.CenterHorizontally)
                            ) {
                                title()
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                }

                content()

                if (buttons != null) {
                    Spacer(Modifier.height(24.dp))

                    FlowRow(
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.primary) {
                            ProvideTextStyle(
                                value = MaterialTheme.typography.labelLarge
                            ) {
                                // FlowRow's scope is a RowScope in Compose 1.7 too.
                                buttons()
                            }
                        }
                    }
                }
            }
        }
    }
}
