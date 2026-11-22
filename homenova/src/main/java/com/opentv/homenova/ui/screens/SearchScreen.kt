package com.opentv.homenova.ui.screens

import android.view.KeyEvent as AndroidKeyEvent
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Backspace
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Search
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.opentv.homenova.core.AppEntry
import com.opentv.homenova.core.MediaEntry
import com.opentv.homenova.ui.components.AppIconTile
import com.opentv.homenova.ui.components.EmptyState
import com.opentv.homenova.ui.components.FocusableCard
import com.opentv.homenova.ui.components.IconAction
import com.opentv.homenova.ui.components.MediaCard
import com.opentv.homenova.ui.components.SectionHeader
import com.opentv.homenova.ui.theme.LocalNovaPalette
import com.opentv.homenova.voice.VoiceCommandParser
import kotlinx.coroutines.delay

@Composable
fun SearchScreen(
    initialQuery: String,
    apps: List<AppEntry>,
    media: List<MediaEntry>,
    onAppClick: (AppEntry) -> Unit,
    onMediaClick: (MediaEntry) -> Unit,
    onVoice: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalNovaPalette.current
    var query by rememberSaveable { mutableStateOf(initialQuery) }
    val keyboardFocus = remember { FocusRequester() }
    val normalized = VoiceCommandParser.normalize(query)

    LaunchedEffect(initialQuery) {
        if (initialQuery.isNotBlank()) query = initialQuery
        delay(200)
        runCatching { keyboardFocus.requestFocus() }
    }

    val appResults = remember(normalized, apps) {
        if (normalized.isBlank()) apps.take(10)
        else apps.filter { VoiceCommandParser.normalize(it.label).contains(normalized) }
    }
    val mediaResults = remember(normalized, media) {
        if (normalized.isBlank()) media.take(8)
        else media.filter { entry ->
            VoiceCommandParser.normalize(entry.title).contains(normalized) ||
                VoiceCommandParser.normalize(entry.description).contains(normalized) ||
                entry.genres.any { VoiceCommandParser.normalize(it).contains(normalized) }
        }
    }
    val hasResults = appResults.isNotEmpty() || mediaResults.isNotEmpty()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(top = 2.dp)
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val native = event.nativeKeyEvent
                when (native.keyCode) {
                    AndroidKeyEvent.KEYCODE_DEL -> {
                        if (query.isNotEmpty()) query = query.dropLast(1)
                        true
                    }
                    AndroidKeyEvent.KEYCODE_SPACE -> {
                        query += " "
                        true
                    }
                    else -> {
                        val char = native.unicodeChar.toChar()
                        if (char.isLetterOrDigit() || char == 'ñ' || char == 'Ñ') {
                            query += char.lowercase()
                            true
                        } else {
                            false
                        }
                    }
                }
            },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 40.dp, end = 40.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            IconAction(
                icon = Icons.Rounded.ArrowBack,
                label = "Volver",
                onSelect = onBack,
            )
            Column(Modifier.weight(1f)) {
                Text(
                    text = "Buscar",
                    style = MaterialTheme.typography.displayMedium,
                    color = palette.textPrimary,
                )
                Text(
                    text = "Usa el teclado con el D-pad o dicta con el micrófono.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.textSecondary,
                )
            }
            IconAction(
                icon = Icons.Rounded.Mic,
                label = "Buscar por voz",
                onSelect = onVoice,
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 40.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(58.dp)
                    .background(Color.White.copy(alpha = 0.055f), RoundedCornerShape(16.dp))
                    .padding(horizontal = 18.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = query.ifBlank { "Escribe el nombre de una app o de un título…" },
                        style = MaterialTheme.typography.titleLarge,
                        color = if (query.isBlank()) palette.textMuted else palette.textPrimary,
                    )
                    if (query.isNotBlank()) {
                        Spacer(Modifier.width(2.dp))
                        Box(
                            Modifier
                                .width(2.dp)
                                .height(24.dp)
                                .background(palette.accent),
                        )
                    }
                }
            }
            if (query.isNotBlank()) {
                IconAction(
                    icon = Icons.Rounded.Backspace,
                    label = "Borrar",
                    onSelect = { if (query.isNotEmpty()) query = query.dropLast(1) },
                )
            }
        }

        Spacer(Modifier.height(18.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 40.dp, end = 40.dp),
            verticalAlignment = Alignment.Top,
        ) {
            if (!hasResults) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    EmptyState(
                        icon = Icons.Rounded.Search,
                        title = "Sin resultados para «$query»",
                        body = "Prueba con otro nombre o busca por voz. El catálogo incluye apps instaladas y contenido de demostración.",
                        actionLabel = "Buscar por voz",
                        onAction = onVoice,
                    )
                }
            } else {
                LazyColumn(
                    state = rememberLazyListState(),
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = 30.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    if (appResults.isNotEmpty()) {
                        item(key = "apps-title") {
                            SectionHeader(
                                title = if (normalized.isBlank()) "Aplicaciones" else "Apps que coinciden",
                                subtitle = "${appResults.size} resultados",
                                modifier = Modifier.padding(end = 10.dp),
                            )
                        }
                        item(key = "apps") {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(appResults, key = { it.packageName }) { app ->
                                    AppIconTile(
                                        app = app,
                                        onSelect = { onAppClick(app) },
                                        size = 86.dp,
                                    )
                                }
                            }
                        }
                    }
                    if (mediaResults.isNotEmpty()) {
                        item(key = "media-title") {
                            SectionHeader(
                                title = if (normalized.isBlank()) "Contenido" else "Títulos que coinciden",
                                subtitle = "${mediaResults.size} resultados",
                                modifier = Modifier.padding(end = 10.dp),
                            )
                        }
                        item(key = "media") {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                items(mediaResults, key = { it.id }) { entry ->
                                    MediaCard(
                                        entry = entry,
                                        width = 240.dp,
                                        onSelect = { onMediaClick(entry) },
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.width(28.dp))
            TvKeyboard(
                initialFocus = keyboardFocus,
                onLetter = { query += it },
                onBackspace = { if (query.isNotEmpty()) query = query.dropLast(1) },
                onClear = { query = "" },
                onVoice = onVoice,
            )
        }
    }
}

@Composable
private fun TvKeyboard(
    initialFocus: FocusRequester,
    onLetter: (String) -> Unit,
    onBackspace: () -> Unit,
    onClear: () -> Unit,
    onVoice: () -> Unit,
) {
    val palette = LocalNovaPalette.current
    val rows = listOf("ABCDEF", "GHIJKL", "MNOPQR", "STUVWX", "YZ0123", "456789")

    Column(
        modifier = Modifier
            .width(498.dp)
            .background(Color.White.copy(alpha = 0.035f), RoundedCornerShape(20.dp))
            // The keyboard is ~440dp tall; on 540dp TVs (and phones) it does not fit
            // under the header, so it scrolls and follows D-pad focus.
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Text(
            text = "TECLADO",
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 10.sp),
            color = palette.textMuted,
            modifier = Modifier.padding(start = 4.dp, bottom = 2.dp),
        )
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                row.forEach { char ->
                    KeyboardKey(
                        label = char.toString(),
                        onSelect = { onLetter(char.toString()) },
                        modifier = if (char == 'A') Modifier.focusRequester(initialFocus) else Modifier,
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            KeyboardKey(
                label = "ESPACIO",
                onSelect = { onLetter(" ") },
                modifier = Modifier.width(166.dp),
            )
            KeyboardKey(
                label = "BORRAR",
                onSelect = onBackspace,
                modifier = Modifier.width(150.dp),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            KeyboardKey(
                label = "LIMPIAR",
                onSelect = onClear,
                modifier = Modifier.width(150.dp),
            )
            KeyboardKey(
                label = "VOZ",
                onSelect = onVoice,
                modifier = Modifier.width(166.dp),
            )
        }
    }
}

@Composable
private fun KeyboardKey(
    label: String,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalNovaPalette.current
    FocusableCard(
        onSelect = onSelect,
        modifier = modifier.width(74.dp),
        shape = RoundedCornerShape(10.dp),
        focusedScale = 1.07f,
        focusedElevation = 7.dp,
        background = Color.White.copy(alpha = 0.07f),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(42.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = if (label.length > 1) 10.sp else 16.sp,
                ),
                color = palette.textPrimary,
            )
        }
    }
}
