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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.opentv.homenova.ui.theme.LocalNovaPalette

@Composable
fun NovaSwitch(checked: Boolean, accent: Color = LocalNovaPalette.current.accent) {
    val track by animateColorAsState(
        targetValue = if (checked) accent else Color.White.copy(alpha = 0.16f),
        animationSpec = tween(160),
        label = "switchTrack",
    )
    val offset by animateDpAsState(
        targetValue = if (checked) 20.dp else 0.dp,
        animationSpec = tween(160),
        label = "switchThumb",
    )
    Box(
        modifier = Modifier
            .width(46.dp)
            .height(26.dp)
            .clip(CircleShape)
            .background(track)
            .padding(3.dp),
    ) {
        Box(
            modifier = Modifier
                .offset(x = offset)
                .size(20.dp)
                .clip(CircleShape)
                .background(if (checked) Color(0xFF091014) else Color.White.copy(alpha = 0.85f)),
        )
    }
}

@Composable
fun ToggleRow(
    title: String,
    description: String?,
    checked: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalNovaPalette.current
    FocusableCard(
        onSelect = onToggle,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        focusedScale = 1.012f,
        focusedElevation = 8.dp,
        background = Color.White.copy(alpha = 0.04f),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = palette.textPrimary,
                )
                if (description != null) {
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.textSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            NovaSwitch(checked = checked)
        }
    }
}

data class Choice<T>(val value: T, val label: String)

@Composable
fun <T> ChoiceRow(
    title: String,
    description: String?,
    options: List<Choice<T>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    columns: Int = 3,
) {
    val palette = LocalNovaPalette.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.04f))
            .padding(horizontal = 20.dp, vertical = 15.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = palette.textPrimary,
        )
        if (description != null) {
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = palette.textSecondary,
            )
        }
        options.chunked(columns).forEach { rowOptions ->
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                rowOptions.forEach { option ->
                    val isSelected = option.value == selected
                    FocusableCard(
                        onSelect = { onSelect(option.value) },
                        shape = RoundedCornerShape(50),
                        selected = isSelected,
                        focusedScale = 1.05f,
                        background = if (isSelected) {
                            palette.accent.copy(alpha = 0.18f)
                        } else {
                            Color.White.copy(alpha = 0.055f)
                        },
                    ) {
                        Text(
                            text = option.label,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (isSelected) palette.accent else palette.textSecondary,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun ActionRow(
    title: String,
    description: String?,
    actionLabel: String,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    val palette = LocalNovaPalette.current
    FocusableCard(
        onSelect = onSelect,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        focusedScale = 1.012f,
        focusedElevation = 8.dp,
        background = Color.White.copy(alpha = 0.04f),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = palette.textSecondary,
                    modifier = Modifier.size(21.dp),
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = palette.textPrimary,
                )
                if (description != null) {
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.textSecondary,
                    )
                }
            }
            Text(
                text = actionLabel,
                style = MaterialTheme.typography.labelLarge,
                color = palette.accent,
            )
        }
    }
}

@Composable
fun ValueRow(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    description: String? = null,
) {
    val palette = LocalNovaPalette.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.028f))
            .padding(horizontal = 20.dp, vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = palette.textPrimary,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = palette.accent,
            )
        }
        if (description != null) {
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = palette.textSecondary,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}
