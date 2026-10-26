package com.opentv.app.ui.connect

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.opentv.app.connect.CastDevice
import com.opentv.app.connect.ConnectUiState
import com.opentv.app.connect.ConnectViewModel
import com.opentv.app.connect.ConnectionMethod
import com.opentv.app.connect.MethodState
import com.opentv.app.connect.MethodStatus
import com.opentv.app.connect.PhoneGuides
import com.opentv.app.connect.TvBrand
import com.opentv.app.connect.TvTarget
import com.opentv.app.connect.UpnpDevice
import com.opentv.app.connect.Troubleshooting
import com.opentv.app.ui.components.FocusableSurface
import com.opentv.app.ui.components.SafeArea
import com.opentv.app.ui.components.StatusChip
import com.opentv.app.ui.components.TvButton
import com.opentv.app.ui.components.TvListItem
import com.opentv.app.ui.theme.OpenTvColors

/**
 * «Conectar TV»: muestra qué puede hacer REALMENTE este equipo, sin simular conexiones.
 * Las búsquedas (pantalla externa, Cast, DLNA) viven solo mientras la pantalla está visible.
 */
@Composable
fun ConnectTvScreen(
    onBack: () -> Unit,
    viewModel: ConnectViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val selectedTarget by viewModel.outputTarget.collectAsStateWithLifecycle()

    LifecycleEventEffect(Lifecycle.Event.ON_START) { viewModel.onScreenStarted() }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.onScreenStopped() }

    var brandIndex by remember { mutableIntStateOf(0) }
    val brand = TvBrand.values()[brandIndex]
    var castPickerOpen by remember { mutableStateOf(false) }
    if (castPickerOpen) {
        CastPickerDialog(onDismiss = { castPickerOpen = false }, viewModel = viewModel, manageDiscovery = false)
    }

    val firstFocus = remember { FocusRequester() }
    val hasStatuses = state.statuses.isNotEmpty()
    LaunchedEffect(hasStatuses) {
        if (hasStatuses) {
            withFrameNanos { }
            runCatching { firstFocus.requestFocus() }
        }
    }

    SafeArea {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Conectar TV",
                    style = MaterialTheme.typography.headlineMedium,
                    color = OpenTvColors.OnBackground,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CastButton(onClick = { castPickerOpen = true })
                    TvButton(text = "Volver", onClick = onBack, horizontalPadding = 20.dp)
                }
            }
            Text(
                text = "Estado real de este equipo. Cast y DLNA envían contenido al TV; no duplican " +
                    "la pantalla. Para duplicar la pantalla se usa la transmisión del sistema.",
                style = MaterialTheme.typography.bodyMedium,
                color = OpenTvColors.Muted,
            )

            state.message?.let { Banner(it) }

            if (!hasStatuses) {
                Text(
                    text = "Comprobando capacidades del equipo…",
                    style = MaterialTheme.typography.bodyLarge,
                    color = OpenTvColors.Muted,
                )
            }
            state.statuses.forEachIndexed { index, status ->
                StatusCard(
                    status = status,
                    onClick = when {
                        status.method == ConnectionMethod.MIRACAST && state.hasCastSettingsScreen ->
                            viewModel::openSystemCast
                        (status.method == ConnectionMethod.GOOGLE_CAST || status.method == ConnectionMethod.DLNA) &&
                            status.state == MethodState.AVAILABLE -> { { castPickerOpen = true } }
                        else -> null
                    },
                    modifier = if (index == 0) Modifier.focusRequester(firstFocus) else Modifier,
                )
            }

            SectionTitle("Duplicar pantalla (Miracast)")
            MiracastBlock(state = state, onOpen = viewModel::openSystemCast)

            SectionTitle("Google Cast")
            CastBlock(
                state = state,
                selectedKey = selectedTarget?.key,
                onSelect = { device -> viewModel.selectTarget(TvTarget.CastTarget(device)) },
                onStop = viewModel::clearTarget,
            )

            SectionTitle("DLNA")
            DlnaBlock(
                state = state,
                selectedKey = selectedTarget?.key,
                onScan = viewModel::scanDlna,
                onSelect = { device -> viewModel.selectTarget(TvTarget.DlnaTarget(device)) },
                onStop = viewModel::clearTarget,
            )

            SectionTitle("Guía paso a paso")
            GuideBlock(
                brand = brand,
                manufacturer = state.manufacturer,
                onNextBrand = { brandIndex = brand.next().ordinal },
            )

            SectionTitle("Solución de problemas")
            Troubleshooting.tips(
                onLocalNetwork = state.onLocalNetwork,
                dlnaScannedWithoutResults = state.dlna.scanned && state.dlna.devices.isEmpty(),
            ).forEach { tip -> TvListItem(title = tip, subtitle = null) }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        color = OpenTvColors.OnBackground,
        modifier = Modifier.padding(top = 10.dp),
    )
}

@Composable
private fun Banner(text: String) {
    TvListItem(title = text, subtitle = null)
}

private fun stateColor(state: MethodState): Color = when (state) {
    MethodState.CONNECTED -> OpenTvColors.Success
    MethodState.AVAILABLE -> OpenTvColors.Primary
    MethodState.UNVERIFIABLE, MethodState.DETECTED_ONLY -> OpenTvColors.Warning
    MethodState.UNAVAILABLE -> OpenTvColors.Muted
}

@Composable
private fun StatusCard(status: MethodStatus, onClick: (() -> Unit)?, modifier: Modifier = Modifier) {
    FocusableSurface(onClick = onClick, modifier = modifier.fillMaxWidth(), focusedScale = 1.01f) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = status.method.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = OpenTvColors.OnBackground,
                    modifier = Modifier.weight(1f),
                )
                StatusChip(text = status.state.label, dotColor = stateColor(status.state))
            }
            Text(
                text = status.summary,
                style = MaterialTheme.typography.bodyMedium,
                color = OpenTvColors.Muted,
            )
            if (onClick != null) {
                Text(
                    text = if (status.method == ConnectionMethod.MIRACAST) {
                        "Tocá para abrir la transmisión del sistema"
                    } else {
                        "Tocá para elegir un TV y enviarle un enlace"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = OpenTvColors.Primary,
                )
            }
        }
    }
}

@Composable
private fun MiracastBlock(state: ConnectUiState, onOpen: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        val display = state.externalDisplay
        if (display != null) {
            TvListItem(
                title = "Pantalla externa: ${display.name}",
                subtitle = "Para desconectar usá el botón: Android solo permite cortar la " +
                    "transmisión desde su propia pantalla.",
            )
        }
        if (state.hasCastSettingsScreen) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TvButton(
                    text = "Abrir transmisión del sistema",
                    onClick = onOpen,
                    horizontalPadding = 24.dp,
                )
                if (display != null) {
                    TvButton(text = "Desconectar", onClick = onOpen, horizontalPadding = 24.dp)
                }
            }
        } else {
            Text(
                text = "Este equipo no expone la pantalla de transmisión. Buscá «Transmitir», " +
                    "«Smart View» o «Proyección» en los Ajustes.",
                style = MaterialTheme.typography.bodyMedium,
                color = OpenTvColors.Muted,
            )
        }
    }
}

@Composable
private fun CastBlock(
    state: ConnectUiState,
    selectedKey: String?,
    onSelect: (CastDevice) -> Unit,
    onStop: () -> Unit,
) {
    val castStatus = state.statuses.firstOrNull { it.method == ConnectionMethod.GOOGLE_CAST }
    val castError = state.castError
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when {
            castError != null -> TvListItem(title = castError, subtitle = null)
            castStatus?.state != MethodState.AVAILABLE ->
                Text(
                    text = "La búsqueda de Cast no está activa (ver estado arriba).",
                    style = MaterialTheme.typography.bodyMedium,
                    color = OpenTvColors.Muted,
                )
            state.castDevices.isEmpty() ->
                Text(
                    text = "Buscando dispositivos Cast en tu red… Todavía no apareció ninguno.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = OpenTvColors.Muted,
                )
            else -> state.castDevices.forEach { device ->
                TvListItem(
                    title = device.name,
                    subtitle = if (selectedKey == "cast:${device.id}") {
                        "Salida elegida"
                    } else {
                        "Tocá para conectar y usar este TV como salida"
                    },
                    onClick = { onSelect(device) },
                )
            }
        }
        if (castStatus?.state == MethodState.AVAILABLE) {
            TvButton(text = "Dejar de transmitir", onClick = onStop, horizontalPadding = 24.dp)
        }
    }
}

@Composable
private fun DlnaBlock(
    state: ConnectUiState,
    selectedKey: String?,
    onScan: () -> Unit,
    onSelect: (UpnpDevice) -> Unit,
    onStop: () -> Unit,
) {
    val dlna = state.dlna
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TvButton(
                text = if (dlna.scanning) "Buscando…" else "Buscar TV en la red",
                onClick = onScan,
                horizontalPadding = 24.dp,
            )
            TvButton(text = "Dejar de transmitir", onClick = onStop, horizontalPadding = 24.dp)
        }
        dlna.error?.let { TvListItem(title = it, subtitle = null) }
        if (dlna.scanned && dlna.devices.isEmpty() && dlna.error == null) {
            Text(
                text = "No se encontró ningún TV con DLNA. Mirá la solución de problemas más abajo.",
                style = MaterialTheme.typography.bodyMedium,
                color = OpenTvColors.Muted,
            )
        }
        dlna.devices.forEach { device ->
            TvListItem(
                title = device.friendlyName,
                subtitle = (listOf(device.manufacturer, device.modelName)
                    .filter { it.isNotBlank() }
                    .joinToString(" · ")
                    .ifBlank { "DLNA" }) +
                    if (selectedKey == "dlna:${device.udn}") " · salida elegida" else " · tocá para elegir",
                onClick = { onSelect(device) },
            )
        }
    }
}

@Composable
private fun GuideBlock(brand: TvBrand, manufacturer: String, onNextBrand: () -> Unit) {
    val phone = PhoneGuides.forManufacturer(manufacturer)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TvButton(
            text = "Marca del TV: ${brand.label} (tocá para cambiar)",
            onClick = onNextBrand,
            horizontalPadding = 24.dp,
        )
        Text(
            text = "En el TV",
            style = MaterialTheme.typography.titleMedium,
            color = OpenTvColors.OnBackground,
        )
        brand.steps.forEachIndexed { i, step -> TvListItem(title = "${i + 1}. $step", subtitle = null) }
        Text(
            text = "En el teléfono (${phone.featureName})",
            style = MaterialTheme.typography.titleMedium,
            color = OpenTvColors.OnBackground,
        )
        phone.steps.forEachIndexed { i, step -> TvListItem(title = "${i + 1}. $step", subtitle = null) }
    }
}
