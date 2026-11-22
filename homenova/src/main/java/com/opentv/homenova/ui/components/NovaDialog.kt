package com.opentv.homenova.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.opentv.homenova.ui.theme.LocalNovaPalette
import kotlinx.coroutines.delay

data class DialogAction(
    val label: String,
    val onClick: () -> Unit,
    val icon: ImageVector? = null,
    val primary: Boolean = false,
    val destructive: Boolean = false,
)

/**
 * TV-friendly modal dialog. The first action receives focus explicitly so the
 * D-pad always lands inside the dialog instead of escaping to the screen below.
 */
@Composable
fun NovaDialog(
    title: String,
    onDismiss: () -> Unit,
    actions: List<DialogAction>,
    message: String? = null,
) {
    val palette = LocalNovaPalette.current
    val firstAction = androidx.compose.runtime.remember { FocusRequester() }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.62f)),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(min = 460.dp, max = 620.dp)
                    .background(palette.surface, RoundedCornerShape(24.dp))
                    .padding(28.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineMedium,
                    color = palette.textPrimary,
                )
                if (message != null) {
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.textSecondary,
                    )
                }
                Spacer(Modifier.height(2.dp))
                actions.forEachIndexed { index, action ->
                    NovaButton(
                        label = action.label,
                        icon = action.icon,
                        primary = action.primary,
                        destructive = action.destructive,
                        onSelect = {
                            action.onClick()
                        },
                        modifier = if (index == 0) {
                            Modifier
                                .fillMaxWidth()
                                .focusRequester(firstAction)
                        } else {
                            Modifier.fillMaxWidth()
                        },
                    )
                    if (action.destructive) {
                        Spacer(Modifier.height(0.dp))
                    }
                }
                Text(
                    text = "Pulsa ATRÁS para cerrar",
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.textMuted,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }

    LaunchedEffect(Unit) {
        delay(80)
        runCatching { firstAction.requestFocus() }
    }
}
