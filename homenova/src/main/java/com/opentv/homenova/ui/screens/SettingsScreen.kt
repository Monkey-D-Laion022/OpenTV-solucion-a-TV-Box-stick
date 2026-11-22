package com.opentv.homenova.ui.screens

import android.content.Context
import android.os.Build
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.opentv.homenova.HOME2_VERSION
import com.opentv.homenova.core.AppEntry
import com.opentv.homenova.data.LauncherSettings
import com.opentv.homenova.ui.components.ActionRow
import com.opentv.homenova.ui.components.AppIcon
import com.opentv.homenova.ui.components.Choice
import com.opentv.homenova.ui.components.ChoiceRow
import com.opentv.homenova.ui.components.DialogAction
import com.opentv.homenova.ui.components.FocusableCard
import com.opentv.homenova.ui.components.NovaDialog
import com.opentv.homenova.ui.components.ToggleRow
import com.opentv.homenova.ui.components.ValueRow
import com.opentv.homenova.ui.theme.AccentStyle
import com.opentv.homenova.ui.theme.LocalNovaPalette
import com.opentv.homenova.ui.theme.accentStyleFor

private enum class SettingsCategory(
    val label: String,
    val subtitle: String,
    val icon: ImageVector,
) {
    HOME("Inicio", "Filas, avisos y recomendaciones", Icons.Rounded.Home),
    DISPLAY("Pantalla y sonido", "Imagen, movimiento y ambiente", Icons.Rounded.Tv),
    APPS("Aplicaciones", "Cuadrícula, favoritas y apps ocultas", Icons.Rounded.Apps),
    VOICE("Voz y asistente", "Idioma y comandos de voz", Icons.Rounded.Mic),
    SYSTEM("Sistema", "Información, ajustes y reinicio", Icons.Rounded.Settings),
}

@Composable
fun SettingsScreen(
    settings: LauncherSettings,
    apps: List<AppEntry>,
    voiceAvailable: Boolean,
    isDefaultLauncher: Boolean,
    onUpdate: ((LauncherSettings) -> LauncherSettings) -> Unit,
    onToggleFavourite: (String) -> Unit,
    onToggleHidden: (String) -> Unit,
    onOpenApps: () -> Unit,
    onOpenHomeSettings: () -> Unit,
    onOpenSystemSettings: () -> Unit,
    onTestVoice: () -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalNovaPalette.current
    var selectedName by rememberSaveable { mutableStateOf(SettingsCategory.HOME.name) }
    val selected = SettingsCategory.valueOf(selectedName)
    val scroll = rememberScrollState()
    var confirmReset by remember { mutableStateOf(false) }

    LaunchedEffect(selectedName) {
        scroll.scrollTo(0)
    }

    Row(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .width(320.dp)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(start = 34.dp, top = 4.dp, end = 18.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Text(
                text = "Ajustes",
                style = MaterialTheme.typography.displayMedium,
                color = palette.textPrimary,
                modifier = Modifier.padding(start = 10.dp, bottom = 10.dp),
            )
            SettingsCategory.entries.forEach { category ->
                CategoryRow(
                    category = category,
                    selected = selected == category,
                    onSelect = { selectedName = category.name },
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(
                text = "OpenTV · $HOME2_VERSION",
                style = MaterialTheme.typography.bodySmall,
                color = palette.textMuted,
                modifier = Modifier.padding(start = 10.dp),
            )
        }

        Box(
            modifier = Modifier
                .width(1.dp)
                .fillMaxHeight()
                .background(Color.White.copy(alpha = 0.07f)),
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .verticalScroll(scroll)
                .padding(start = 34.dp, end = 44.dp, top = 6.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            Text(
                text = selected.label,
                style = MaterialTheme.typography.headlineMedium,
                color = palette.textPrimary,
            )
            Text(
                text = selected.subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = palette.textSecondary,
                modifier = Modifier.padding(bottom = 5.dp),
            )

            when (selected) {
                SettingsCategory.HOME -> HomeSettings(
                    settings = settings,
                    onUpdate = onUpdate,
                    onOpenApps = onOpenApps,
                )
                SettingsCategory.DISPLAY -> DisplaySettings(
                    settings = settings,
                    onUpdate = onUpdate,
                )
                SettingsCategory.APPS -> AppSettings(
                    settings = settings,
                    apps = apps,
                    isDefaultLauncher = isDefaultLauncher,
                    onUpdate = onUpdate,
                    onToggleFavourite = onToggleFavourite,
                    onToggleHidden = onToggleHidden,
                    onOpenHomeSettings = onOpenHomeSettings,
                )
                SettingsCategory.VOICE -> VoiceSettings(
                    settings = settings,
                    voiceAvailable = voiceAvailable,
                    onUpdate = onUpdate,
                    onTestVoice = onTestVoice,
                )
                SettingsCategory.SYSTEM -> SystemSettings(
                    isDefaultLauncher = isDefaultLauncher,
                    onOpenHomeSettings = onOpenHomeSettings,
                    onOpenSystemSettings = onOpenSystemSettings,
                    onReset = { confirmReset = true },
                )
            }
        }
    }

    if (confirmReset) {
        NovaDialog(
            title = "¿Restablecer los ajustes?",
            message = "Se restaurarán el tema, la cuadrícula, la voz y las preferencias de inicio. " +
                "Las apps instaladas no se verán afectadas.",
            onDismiss = { confirmReset = false },
            actions = listOf(
                DialogAction(
                    label = "Restablecer",
                    primary = true,
                    destructive = true,
                    onClick = {
                        confirmReset = false
                        onReset()
                    },
                ),
                DialogAction(
                    label = "Cancelar",
                    onClick = { confirmReset = false },
                ),
            ),
        )
    }
}

@Composable
private fun CategoryRow(
    category: SettingsCategory,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    val palette = LocalNovaPalette.current
    FocusableCard(
        onSelect = onSelect,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(13.dp),
        selected = selected,
        focusedScale = 1.02f,
        background = if (selected) {
            palette.accent.copy(alpha = 0.11f)
        } else {
            Color.White.copy(alpha = 0.025f)
        },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 13.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            Icon(
                imageVector = category.icon,
                contentDescription = null,
                tint = if (selected) palette.accent else palette.textSecondary,
                modifier = Modifier.size(19.dp),
            )
            Column(Modifier.weight(1f)) {
                Text(
                    text = category.label,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (selected) palette.textPrimary else palette.textSecondary,
                )
                Text(
                    text = category.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.textMuted,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun HomeSettings(
    settings: LauncherSettings,
    onUpdate: ((LauncherSettings) -> LauncherSettings) -> Unit,
    onOpenApps: () -> Unit,
) {
    ChoiceRow(
        title = "Pantalla de inicio",
        description = "Dónde quieres aterrizar al volver al launcher.",
        options = listOf(
            Choice("home", "Inicio"),
            Choice("apps", "Aplicaciones"),
            Choice("discover", "Descubrir"),
            Choice("live", "En vivo"),
        ),
        selected = settings.startScreen,
        onSelect = { value -> onUpdate { it.copy(startScreen = value) } },
    )
    ToggleRow(
        title = "Vistas previas de vídeo",
        description = "Reproduce avances silenciosos al mantener el foco sobre una tarjeta.",
        checked = settings.videoPreviews,
        onToggle = { onUpdate { it.copy(videoPreviews = !it.videoPreviews) } },
    )
    ToggleRow(
        title = "Vistas previas de audio",
        description = "Activa el audio de los avances. Puede subir el consumo de datos.",
        checked = settings.audioPreviews,
        onToggle = { onUpdate { it.copy(audioPreviews = !it.audioPreviews) } },
    )
    ToggleRow(
        title = "Recomendaciones personalizadas",
        description = "Muestra la fila «Elegido para ti» en Inicio y Descubrir.",
        checked = settings.showRecommendations,
        onToggle = { onUpdate { it.copy(showRecommendations = !it.showRecommendations) } },
    )
    ActionRow(
        title = "Editor de aplicaciones",
        description = "Elige favoritas y oculta apps de la cuadrícula.",
        actionLabel = "Abrir",
        onSelect = onOpenApps,
    )
    ToggleRow(
        title = "Sonidos de navegación",
        description = "Pequeños clics al mover el foco por la interfaz.",
        checked = settings.dpadSounds,
        onToggle = { onUpdate { it.copy(dpadSounds = !it.dpadSounds) } },
    )
    ToggleRow(
        title = "Notificaciones",
        description = "Muestra recomendaciones y avisos en el panel de notificaciones.",
        checked = settings.notificationsEnabled,
        onToggle = { onUpdate { it.copy(notificationsEnabled = !it.notificationsEnabled) } },
    )
}

@Composable
private fun DisplaySettings(
    settings: LauncherSettings,
    onUpdate: ((LauncherSettings) -> LauncherSettings) -> Unit,
) {
    val context = LocalContext.current
    val displayInfo = remember(context) { readDisplayInfo(context) }

    ValueRow(
        title = "Resolución actual",
        value = displayInfo.resolution,
        description = "${displayInfo.modeCount} modos disponibles en la pantalla",
    )
    ValueRow(
        title = "Frecuencia de refresco",
        value = displayInfo.refreshRate,
        description = "Se ajusta desde los ajustes de pantalla del sistema.",
    )
    ValueRow(
        title = "HDR",
        value = displayInfo.hdr,
        description = "Capacidades notificadas por el compositor de la TV.",
    )
    ChoiceRow(
        title = "Protector de pantalla",
        description = "Tiempo de inactividad antes de mostrar el modo ambiente.",
        options = listOf(
            Choice(5, "5 min"),
            Choice(10, "10 min"),
            Choice(20, "20 min"),
            Choice(30, "30 min"),
            Choice(0, "Nunca"),
        ),
        selected = settings.screensaverMinutes,
        onSelect = { value -> onUpdate { it.copy(screensaverMinutes = value) } },
        columns = 5,
    )
    ToggleRow(
        title = "Reducir movimiento",
        description = "Desactiva animaciones ambientales y transiciones largas.",
        checked = settings.reduceMotion,
        onToggle = { onUpdate { it.copy(reduceMotion = !it.reduceMotion) } },
    )
    ToggleRow(
        title = "Fondo ambiente",
        description = "Degradados suaves detrás del contenido.",
        checked = settings.ambientBackground,
        onToggle = { onUpdate { it.copy(ambientBackground = !it.ambientBackground) } },
    )
    AccentPicker(
        selectedKey = settings.accent,
        onSelect = { key -> onUpdate { it.copy(accent = key) } },
    )
}

@Composable
private fun AccentPicker(selectedKey: String, onSelect: (String) -> Unit) {
    val palette = LocalNovaPalette.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.04f), RoundedCornerShape(16.dp))
            .padding(horizontal = 20.dp, vertical = 15.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = "Color de acento",
            style = MaterialTheme.typography.titleMedium,
            color = palette.textPrimary,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AccentStyle.entries.forEach { style ->
                val selected = style.key == selectedKey
                FocusableCard(
                    onSelect = { onSelect(style.key) },
                    shape = RoundedCornerShape(12.dp),
                    selected = selected,
                    focusedScale = 1.05f,
                    background = if (selected) palette.accent.copy(alpha = 0.1f)
                    else Color.White.copy(alpha = 0.04f),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(
                            Modifier
                                .size(13.dp)
                                .background(style.primary, CircleShape),
                        )
                        Box(
                            Modifier
                                .size(13.dp)
                                .background(style.secondary, CircleShape),
                        )
                        Text(
                            text = style.label,
                            style = MaterialTheme.typography.labelLarge,
                            color = palette.textSecondary,
                        )
                        if (selected) {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = null,
                                tint = palette.accent,
                                modifier = Modifier.size(15.dp),
                            )
                        }
                    }
                }
            }
        }
        Text(
            text = "El tema se aplica al instante sobre toda la interfaz.",
            style = MaterialTheme.typography.bodySmall,
            color = palette.textMuted,
        )
    }
}

@Composable
private fun AppSettings(
    settings: LauncherSettings,
    apps: List<AppEntry>,
    isDefaultLauncher: Boolean,
    onUpdate: ((LauncherSettings) -> LauncherSettings) -> Unit,
    onToggleFavourite: (String) -> Unit,
    onToggleHidden: (String) -> Unit,
    onOpenHomeSettings: () -> Unit,
) {
    ChoiceRow(
        title = "Columnas de la cuadrícula",
        description = "Cuántas apps caben por fila en la pantalla de Aplicaciones.",
        options = (4..8).map { Choice(it, "$it") },
        selected = settings.gridColumns,
        onSelect = { value -> onUpdate { it.copy(gridColumns = value) } },
        columns = 5,
    )
    ToggleRow(
        title = "OpenTV como launcher predeterminado",
        description = if (isDefaultLauncher) {
            "Ahora mismo OpenTV abre con el botón Inicio."
        } else {
            "Pulsa para abrir los ajustes del sistema y elegir OpenTV como pantalla de inicio."
        },
        checked = isDefaultLauncher,
        onToggle = onOpenHomeSettings,
    )
    Text(
        text = "Favoritas (${settings.favourites.size})",
        style = MaterialTheme.typography.titleMedium,
        color = LocalNovaPalette.current.textPrimary,
        modifier = Modifier.padding(top = 4.dp),
    )
    apps.sortedByDescending { it.packageName in settings.favourites }
        .take(20)
        .forEach { app ->
            AppManageRow(
                app = app,
                active = app.packageName in settings.favourites,
                activeIcon = Icons.Rounded.Star,
                inactiveIcon = Icons.Rounded.StarBorder,
                onToggle = { onToggleFavourite(app.packageName) },
            )
        }
    Text(
        text = "Apps ocultas (${settings.hiddenApps.size})",
        style = MaterialTheme.typography.titleMedium,
        color = LocalNovaPalette.current.textPrimary,
        modifier = Modifier.padding(top = 8.dp),
    )
    val hidden = apps.filter { it.packageName in settings.hiddenApps }
    if (hidden.isEmpty()) {
        Text(
            text = "No has ocultado ninguna app. Mantén pulsado OK sobre una app para ocultarla.",
            style = MaterialTheme.typography.bodySmall,
            color = LocalNovaPalette.current.textMuted,
        )
    } else {
        hidden.forEach { app ->
            AppManageRow(
                app = app,
                active = true,
                activeIcon = Icons.Rounded.VisibilityOff,
                inactiveIcon = Icons.Rounded.Visibility,
                onToggle = { onToggleHidden(app.packageName) },
            )
        }
    }
}

@Composable
private fun AppManageRow(
    app: AppEntry,
    active: Boolean,
    activeIcon: ImageVector,
    inactiveIcon: ImageVector,
    onToggle: () -> Unit,
) {
    val palette = LocalNovaPalette.current
    FocusableCard(
        onSelect = onToggle,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        focusedScale = 1.012f,
        background = Color.White.copy(alpha = 0.03f),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AppIcon(app = app, size = 30.dp)
            Text(
                text = app.label,
                style = MaterialTheme.typography.bodyMedium,
                color = if (active) palette.textPrimary else palette.textSecondary,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = if (active) activeIcon else inactiveIcon,
                contentDescription = null,
                tint = if (active) palette.accent else palette.textMuted,
                modifier = Modifier.size(19.dp),
            )
        }
    }
}

@Composable
private fun VoiceSettings(
    settings: LauncherSettings,
    voiceAvailable: Boolean,
    onUpdate: ((LauncherSettings) -> LauncherSettings) -> Unit,
    onTestVoice: () -> Unit,
) {
    val palette = LocalNovaPalette.current
    ValueRow(
        title = "Reconocimiento en el dispositivo",
        value = if (voiceAvailable) "Disponible" else "No disponible",
        description = if (voiceAvailable) {
            "Se usará el servicio de reconocimiento del sistema."
        } else {
            "Instala un servicio de reconocimiento o usa el asistente del sistema."
        },
    )
    ChoiceRow(
        title = "Idioma de los comandos",
        description = "Se pasa al reconocedor y al motor de voz.",
        options = listOf(
            Choice("es-ES", "Español (ES)"),
            Choice("es-MX", "Español (MX)"),
            Choice("en-US", "English (US)"),
            Choice("pt-BR", "Português (BR)"),
        ),
        selected = settings.voiceLanguage,
        onSelect = { value -> onUpdate { it.copy(voiceLanguage = value) } },
        columns = 2,
    )
    ToggleRow(
        title = "Usar el asistente del sistema",
        description = "Delega en el servicio de voz instalado en la TV (por ejemplo, el asistente del fabricante).",
        checked = settings.useSystemAssistant,
        onToggle = { onUpdate { it.copy(useSystemAssistant = !it.useSystemAssistant) } },
    )
    ActionRow(
        title = "Probar la voz",
        description = "Lanza el reconocedor con el idioma seleccionado.",
        actionLabel = "Probar",
        icon = Icons.Rounded.Mic,
        onSelect = onTestVoice,
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.035f), RoundedCornerShape(16.dp))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = "Comandos que entiende OpenTV",
            style = MaterialTheme.typography.titleMedium,
            color = palette.textPrimary,
        )
        listOf(
            "«Abre Ajustes» · navega a una sección",
            "«Reproduce Neon Horizon» · abre el reproductor",
            "«Busca documentales» · busca en apps y catálogo",
            "«Sube el volumen» / «Silencio» · control de audio",
            "«Qué hora es» · respuesta hablada",
        ).forEach { line ->
            Text(
                text = line,
                style = MaterialTheme.typography.bodySmall,
                color = palette.textSecondary,
            )
        }
        Text(
            text = "El asistente de Google no se puede incluir en la app: es un servicio de sistema. " +
                "OpenTV puede cederle la petición cuando esté instalado.",
            style = MaterialTheme.typography.bodySmall,
            color = palette.textMuted,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun SystemSettings(
    isDefaultLauncher: Boolean,
    onOpenHomeSettings: () -> Unit,
    onOpenSystemSettings: () -> Unit,
    onReset: () -> Unit,
) {
    val context = LocalContext.current
    ValueRow("Dispositivo", Build.MANUFACTURER.replaceFirstChar { it.uppercase() } + " " + Build.MODEL)
    ValueRow("Android", "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
    ValueRow("OpenTV", HOME2_VERSION)
    ValueRow(
        "Launcher predeterminado",
        if (isDefaultLauncher) "Sí" else "No",
        description = "Android permite elegir la pantalla de inicio entre las apps instaladas.",
    )
    ActionRow(
        title = "Pantalla de inicio del sistema",
        description = "Elige qué app responde al botón Inicio.",
        actionLabel = if (isDefaultLauncher) "Revisar" else "Elegir",
        onSelect = onOpenHomeSettings,
    )
    ActionRow(
        title = "Ajustes de Android",
        description = "Red, cuentas, accesibilidad y resto de opciones del dispositivo.",
        actionLabel = "Abrir",
        onSelect = onOpenSystemSettings,
    )
    ActionRow(
        title = "Restablecer preferencias",
        description = "Vuelve a la configuración de fábrica de OpenTV.",
        actionLabel = "Restablecer",
        icon = Icons.Rounded.RestartAlt,
        onSelect = onReset,
    )
    ValueRow(
        "Créditos",
        "Hecho con Kotlin y Jetpack Compose",
        description = "Interfaz y código originales. Los vídeos de demostración son películas " +
            "Creative Commons; consulta el README del proyecto para las atribuciones.",
    )
}

private data class DisplayInfo(
    val resolution: String,
    val refreshRate: String,
    val hdr: String,
    val modeCount: Int,
)

private fun readDisplayInfo(context: Context): DisplayInfo {
    val display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        context.display
    } else {
        @Suppress("DEPRECATION")
        (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay
    }
    if (display == null) {
        return DisplayInfo("Desconocida", "Desconocida", "No disponible", 0)
    }
    val mode = display.mode
    val resolution = "${mode.physicalWidth} × ${mode.physicalHeight}"
    val refresh = String.format(java.util.Locale.US, "%.0f Hz", mode.refreshRate)
    val hdrTypes = display.hdrCapabilities?.supportedHdrTypes
    val hdr = when {
        hdrTypes == null || hdrTypes.isEmpty() -> "SDR"
        else -> hdrTypes.joinToString(" · ") { type ->
            when (type) {
                android.view.Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION -> "Dolby Vision"
                android.view.Display.HdrCapabilities.HDR_TYPE_HDR10 -> "HDR10"
                android.view.Display.HdrCapabilities.HDR_TYPE_HLG -> "HLG"
                android.view.Display.HdrCapabilities.HDR_TYPE_HDR10_PLUS -> "HDR10+"
                else -> "HDR"
            }
        }
    }
    return DisplayInfo(
        resolution = resolution,
        refreshRate = refresh,
        hdr = hdr,
        modeCount = display.supportedModes.size,
    )
}
