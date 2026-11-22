package com.opentv.homenova.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.SettingsInputAntenna
import androidx.compose.material.icons.rounded.SettingsInputComponent
import androidx.compose.material.icons.rounded.SettingsInputHdmi
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.opentv.homenova.core.InputEntry
import com.opentv.homenova.core.InputKind
import com.opentv.homenova.ui.components.DialogAction
import com.opentv.homenova.ui.components.FocusableCard
import com.opentv.homenova.ui.components.NovaButton
import com.opentv.homenova.ui.components.NovaDialog
import com.opentv.homenova.ui.components.Pill
import com.opentv.homenova.ui.theme.LocalNovaPalette

@Composable
fun InputsScreen(
    inputs: List<InputEntry>,
    onOpenInput: (InputEntry) -> Boolean,
    onOpenSystemPicker: () -> Boolean,
    modifier: Modifier = Modifier,
) {
    val palette = LocalNovaPalette.current
    var unavailable by remember { mutableStateOf<InputEntry?>(null) }
    var pickerFailed by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 40.dp, end = 40.dp, top = 2.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "Entradas",
                    style = MaterialTheme.typography.displayMedium,
                    color = palette.textPrimary,
                )
                Text(
                    text = "Cambia entre HDMI, sintonizador y fuentes conectadas.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.textSecondary,
                )
            }
            NovaButton(
                label = "Selector del sistema",
                icon = Icons.Rounded.OpenInNew,
                primary = false,
                compact = true,
                onSelect = { if (!onOpenSystemPicker()) pickerFailed = true },
            )
        }

        if (inputs.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "No se han detectado entradas de TV en este dispositivo.",
                    color = palette.textSecondary,
                )
            }
        } else {
            LazyColumn(
                state = rememberLazyListState(),
                contentPadding = PaddingValues(horizontal = 40.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(inputs, key = { it.id }) { input ->
                    InputRow(
                        input = input,
                        onSelect = {
                            if (!input.isLaunchable) {
                                unavailable = input
                            } else if (!onOpenInput(input)) {
                                unavailable = input
                            }
                        },
                    )
                }
                item {
                    Text(
                        text = "Las entradas se obtienen del TV Input Framework. En dispositivos sin " +
                            "servicios de TV se muestran fuentes de demostración.",
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.textMuted,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }
        }
    }

    unavailable?.let { input ->
        NovaDialog(
            title = "Entrada no disponible",
            message = "No se ha podido abrir «${input.label}». Puede que el dispositivo no exponga " +
                "esta entrada o que falte el permiso del sistema correspondiente.",
            onDismiss = { unavailable = null },
            actions = listOf(
                DialogAction(
                    label = "Entendido",
                    primary = true,
                    onClick = { unavailable = null },
                ),
            ),
        )
    }

    if (pickerFailed) {
        NovaDialog(
            title = "Selector no disponible",
            message = "Este dispositivo no ofrece el selector de entradas del sistema. " +
                "Puedes abrir las fuentes desde sus propias apps.",
            onDismiss = { pickerFailed = false },
            actions = listOf(
                DialogAction(
                    label = "Cerrar",
                    primary = true,
                    onClick = { pickerFailed = false },
                ),
            ),
        )
    }
}

@Composable
private fun InputRow(input: InputEntry, onSelect: () -> Unit) {
    val palette = LocalNovaPalette.current
    FocusableCard(
        onSelect = onSelect,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        focusedScale = 1.012f,
        background = palette.surface,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .background(Color.White.copy(alpha = 0.055f), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = input.kind.icon(),
                    contentDescription = null,
                    tint = palette.accent,
                    modifier = Modifier.size(25.dp),
                )
            }
            Column(Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = input.label,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = palette.textPrimary,
                    )
                    Pill(text = input.kind.label(), color = palette.textSecondary)
                }
                Text(
                    text = input.status,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (input.status == "Conectado") palette.success else palette.textSecondary,
                )
            }
            Icon(
                imageVector = Icons.Rounded.ArrowForward,
                contentDescription = null,
                tint = palette.textMuted,
            )
        }
    }
}

private fun InputKind.icon(): ImageVector = when (this) {
    InputKind.HDMI -> Icons.Rounded.SettingsInputHdmi
    InputKind.TUNER -> Icons.Rounded.SettingsInputAntenna
    InputKind.COMPOSITE -> Icons.Rounded.SettingsInputComponent
    InputKind.OTHER -> Icons.Rounded.Tv
}

private fun InputKind.label(): String = when (this) {
    InputKind.HDMI -> "HDMI"
    InputKind.TUNER -> "TV"
    InputKind.COMPOSITE -> "AV"
    InputKind.OTHER -> "Fuente"
}
