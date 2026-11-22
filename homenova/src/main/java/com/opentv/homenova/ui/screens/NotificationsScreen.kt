package com.opentv.homenova.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.opentv.homenova.core.LauncherNotification
import com.opentv.homenova.core.NotificationKind
import com.opentv.homenova.ui.components.EmptyState
import com.opentv.homenova.ui.components.FocusableCard
import com.opentv.homenova.ui.components.IconAction
import com.opentv.homenova.ui.components.NovaButton
import com.opentv.homenova.ui.components.ToggleRow
import com.opentv.homenova.ui.theme.LocalNovaPalette
import kotlinx.coroutines.delay

@Composable
fun NotificationsScreen(
    notifications: List<LauncherNotification>,
    dndEnabled: Boolean,
    onToggleDnd: () -> Unit,
    onDismiss: (Long) -> Unit,
    onMarkRead: (Long) -> Unit,
    onMarkAllRead: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalNovaPalette.current
    val firstFocus = remember { FocusRequester() }
    val unread = notifications.count { !it.read }

    BackHandler(onBack = onClose)

    LaunchedEffect(Unit) {
        delay(180)
        runCatching { firstFocus.requestFocus() }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .pointerInput(Unit) { detectTapGestures { onClose() } },
        )

        Row(Modifier.fillMaxSize()) {
            Spacer(Modifier.weight(1f))
            Column(
                modifier = Modifier
                    .width(520.dp)
                    .fillMaxHeight()
                    .background(Color(0xFA101216))
                    .padding(start = 26.dp, end = 24.dp, top = 22.dp, bottom = 22.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "Notificaciones",
                            style = MaterialTheme.typography.headlineMedium,
                            color = palette.textPrimary,
                        )
                        Text(
                            text = if (unread > 0) "$unread sin leer" else "Todo al día",
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.textSecondary,
                        )
                    }
                    IconAction(
                        icon = Icons.Rounded.Close,
                        label = "Cerrar notificaciones",
                        onSelect = onClose,
                    )
                }

                ToggleRow(
                    title = "No molestar",
                    description = "Silencia las notificaciones de la app mientras está activo.",
                    checked = dndEnabled,
                    onToggle = onToggleDnd,
                )

                if (notifications.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        EmptyState(
                            icon = Icons.Rounded.Notifications,
                            title = "Sin notificaciones",
                            body = "Aquí aparecerán recomendaciones, avisos del sistema y recordatorios.",
                        )
                    }
                } else {
                    LazyColumn(
                        state = rememberLazyListState(),
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        itemsIndexed(notifications, key = { _, item -> item.id }) { index, item ->
                            NotificationRow(
                                notification = item,
                                onSelect = { onMarkRead(item.id) },
                                onDismiss = { onDismiss(item.id) },
                                modifier = if (index == 0) {
                                    Modifier.focusRequester(firstFocus)
                                } else {
                                    Modifier
                                },
                            )
                        }
                    }
                    NovaButton(
                        label = "Marcar todas como leídas",
                        icon = Icons.Rounded.DoneAll,
                        primary = false,
                        compact = true,
                        onSelect = onMarkAllRead,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun NotificationRow(
    notification: LauncherNotification,
    onSelect: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalNovaPalette.current
    FocusableCard(
        onSelect = onSelect,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(15.dp),
        focusedScale = 1.012f,
        background = if (notification.read) {
            Color.White.copy(alpha = 0.028f)
        } else {
            palette.accent.copy(alpha = 0.075f)
        },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 10.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(palette.accent.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = notification.kind.icon(),
                    contentDescription = null,
                    tint = palette.accent,
                    modifier = Modifier.size(19.dp),
                )
            }
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = notification.title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = palette.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (!notification.read) {
                        Box(
                            Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(palette.accent),
                        )
                    }
                }
                Text(
                    text = notification.body,
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.textSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = relativeTime(notification.timestamp),
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 10.sp),
                    color = palette.textMuted,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
            Box(Modifier.padding(top = 2.dp)) {
                IconAction(
                    icon = Icons.Rounded.Close,
                    label = "Descartar",
                    onSelect = onDismiss,
                    size = 32.dp,
                )
            }
        }
    }
}

private fun NotificationKind.icon(): ImageVector = when (this) {
    NotificationKind.RECOMMENDATION -> Icons.Rounded.Movie
    NotificationKind.SYSTEM -> Icons.Rounded.Notifications
    NotificationKind.UPDATE -> Icons.Rounded.SystemUpdate
    NotificationKind.REMINDER -> Icons.Rounded.Schedule
}

private fun relativeTime(timestamp: Long): String {
    val minutes = ((System.currentTimeMillis() - timestamp) / 60_000).coerceAtLeast(0)
    return when {
        minutes < 1 -> "Ahora mismo"
        minutes < 60 -> "Hace $minutes min"
        minutes < 24 * 60 -> "Hace ${minutes / 60} h"
        minutes < 48 * 60 -> "Ayer"
        else -> "Hace ${minutes / (24 * 60)} días"
    }
}
