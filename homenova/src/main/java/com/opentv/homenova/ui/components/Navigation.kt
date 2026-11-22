package com.opentv.homenova.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.opentv.homenova.navigation.NavItem
import com.opentv.homenova.ui.theme.LocalNovaPalette

/**
 * Left navigation rail. It stays compact while browsing and expands smoothly as
 * soon as it receives D-pad focus, which keeps the content area large while
 * still making every label discoverable.
 */
@Composable
fun SideNavRail(
    items: List<NavItem>,
    selectedRoute: String?,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onNavigate: (NavItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalNovaPalette.current
    val width by animateDpAsState(
        targetValue = if (expanded) 232.dp else 82.dp,
        animationSpec = tween(190),
        label = "railWidth",
    )

    Column(
        modifier = modifier
            .width(width)
            .fillMaxHeight()
            .background(Color(0xF20A0B0D))
            .onFocusChanged { onExpandedChange(it.hasFocus) }
            .padding(vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Logo + items scroll on short screens (phones in landscape are ~360dp tall);
        // the profile footer below stays pinned.
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = if (expanded) 20.dp else 0.dp)
                    .height(46.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = if (expanded) Arrangement.Start else Arrangement.Center,
            ) {
                NovaLogo(size = 30.dp)
                if (expanded) {
                    Spacer(Modifier.width(11.dp))
                    Text(
                        text = "OPENTV",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 3.sp,
                        ),
                        color = palette.textPrimary,
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            items.forEach { item ->
                RailItem(
                    item = item,
                    selected = selectedRoute == item.route,
                    expanded = expanded,
                    onClick = { onNavigate(item) },
                )
                Spacer(Modifier.height(4.dp))
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = if (expanded) 18.dp else 0.dp)
                .height(42.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = if (expanded) Arrangement.Start else Arrangement.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(palette.accentSecondary.copy(alpha = 0.25f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "N",
                    color = palette.textPrimary,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            if (expanded) {
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        "Perfil local",
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.textPrimary,
                    )
                    Text(
                        "Sin cuenta",
                        style = MaterialTheme.typography.labelMedium.copy(fontSize = 10.sp),
                        color = palette.textMuted,
                    )
                }
            }
        }
    }
}

@Composable
private fun RailItem(
    item: NavItem,
    selected: Boolean,
    expanded: Boolean,
    onClick: () -> Unit,
) {
    val palette = LocalNovaPalette.current
    var focused by remember { mutableStateOf(false) }
    val background by animateColorAsState(
        targetValue = when {
            focused -> palette.accent.copy(alpha = 0.16f)
            selected -> Color.White.copy(alpha = 0.06f)
            else -> Color.Transparent
        },
        animationSpec = tween(140),
        label = "railItemBg",
    )
    val iconTint by animateColorAsState(
        targetValue = when {
            focused || selected -> palette.accent
            else -> palette.textSecondary
        },
        animationSpec = tween(140),
        label = "railIcon",
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(background)
                .onFocusChanged { focused = it.hasFocus }
                .tvFocusable(onSelect = onClick)
                .padding(start = 15.dp, end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = item.label,
                    tint = iconTint,
                    modifier = Modifier.size(23.dp),
                )
            }
            if (expanded) {
                Spacer(Modifier.width(15.dp))
                Text(
                    text = item.label,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (focused || selected) palette.textPrimary else palette.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (selected) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .width(3.dp)
                    .height(24.dp)
                    .clip(RoundedCornerShape(50))
                    .background(palette.accent),
            )
        }
    }
}

@Composable
fun TopBar(
    clock24h: Boolean,
    nowPlayingTitle: String?,
    unreadNotifications: Int,
    onVoice: () -> Unit,
    onSearch: () -> Unit,
    onNotifications: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalNovaPalette.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(70.dp)
            .padding(start = 40.dp, end = 34.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ClockDisplay(clock24h = clock24h)
        Spacer(Modifier.width(20.dp))
        if (nowPlayingTitle != null) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.06f))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(palette.accent),
                )
                Text(
                    text = "Reproduciendo · $nowPlayingTitle",
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.width(260.dp),
                )
            }
        }

        Spacer(Modifier.weight(1f))

        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconAction(
                icon = Icons.Rounded.Mic,
                label = "Voz",
                onSelect = onVoice,
            )
            IconAction(
                icon = Icons.Rounded.Search,
                label = "Buscar",
                onSelect = onSearch,
            )
            IconAction(
                icon = Icons.Rounded.Notifications,
                label = "Notificaciones",
                onSelect = onNotifications,
                badge = unreadNotifications,
            )
            IconAction(
                icon = Icons.Rounded.Settings,
                label = "Ajustes",
                onSelect = onSettings,
            )
        }
    }
}
