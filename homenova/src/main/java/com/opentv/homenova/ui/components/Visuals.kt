package com.opentv.homenova.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.opentv.homenova.ui.theme.NovaInk
import com.opentv.homenova.ui.theme.LocalNovaPalette
import kotlinx.coroutines.delay
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** Slow, GPU-light atmospheric background with two accent glows. */
@Composable
fun AmbientBackground(
    accent: Color,
    secondary: Color,
    animated: Boolean,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "ambient")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = if (animated) {
            infiniteRepeatable(
                animation = tween(durationMillis = 24_000, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            )
        } else {
            infiniteRepeatable(animation = tween(1), repeatMode = RepeatMode.Reverse)
        },
        label = "phase",
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(NovaInk, Color(0xFF10141B), NovaInk),
                start = Offset.Zero,
                end = Offset(size.width, size.height),
            ),
        )

        val drift = (phase - 0.5f) * size.width * 0.12f
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(accent.copy(alpha = 0.17f), Color.Transparent),
                center = Offset(size.width * 0.78f + drift, size.height * 0.08f),
                radius = size.minDimension * 0.75f,
            ),
            radius = size.minDimension * 0.75f,
            center = Offset(size.width * 0.78f + drift, size.height * 0.08f),
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(secondary.copy(alpha = 0.10f), Color.Transparent),
                center = Offset(size.width * 0.10f - drift, size.height * 0.92f),
                radius = size.minDimension * 0.6f,
            ),
            radius = size.minDimension * 0.6f,
            center = Offset(size.width * 0.10f - drift, size.height * 0.92f),
        )

        // Fine vertical rules give the dark surface a subtle sense of depth.
        val step = 96.dp.toPx()
        var x = 0f
        while (x < size.width) {
            drawLine(
                color = Color.White.copy(alpha = 0.018f),
                start = Offset(x, 0f),
                end = Offset(x, size.height),
                strokeWidth = 1f,
            )
            x += step
        }
    }
}

@Composable
fun NovaLogo(modifier: Modifier = Modifier, size: Dp = 34.dp) {
    val palette = LocalNovaPalette.current
    Canvas(modifier = modifier.size(size)) {
        val center = Offset(this.size.width / 2f, this.size.height / 2f)
        val outer = this.size.minDimension * 0.46f
        val inner = outer * 0.34f
        val star = Path()
        for (i in 0 until 10) {
            val radius = if (i % 2 == 0) outer else inner
            val angle = (-90f + i * 36f) * (Math.PI / 180f).toFloat()
            val point = Offset(
                x = center.x + cos(angle) * radius,
                y = center.y + sin(angle) * radius,
            )
            if (i == 0) star.moveTo(point.x, point.y) else star.lineTo(point.x, point.y)
        }
        star.close()
        drawPath(star, brush = Brush.linearGradient(listOf(palette.accent, palette.accentSecondary)))
        val play = Path().apply {
            moveTo(center.x - outer * 0.14f, center.y - outer * 0.28f)
            lineTo(center.x - outer * 0.14f, center.y + outer * 0.28f)
            lineTo(center.x + outer * 0.32f, center.y)
            close()
        }
        drawPath(play, color = NovaInk)
    }
}

@Composable
fun ClockDisplay(
    clock24h: Boolean,
    modifier: Modifier = Modifier,
    showDate: Boolean = true,
) {
    var now by remember { mutableStateOf(ZonedDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = ZonedDateTime.now()
            delay(1_000)
        }
    }
    val locale = remember { Locale.forLanguageTag("es-ES") }
    val time = remember(clock24h) {
        DateTimeFormatter.ofPattern(if (clock24h) "HH:mm" else "h:mm", locale)
    }
    val date = remember {
        DateTimeFormatter.ofPattern("EEEE d MMMM", locale)
    }
    val dark = LocalNovaPalette.current

    Column(modifier = modifier) {
        Text(
            text = now.format(time),
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.SemiBold,
                fontSize = 21.sp,
            ),
            color = dark.textPrimary,
        )
        if (showDate) {
            Text(
                text = now.format(date).replaceFirstChar { it.uppercase(locale) },
                style = MaterialTheme.typography.bodySmall,
                color = dark.textSecondary,
            )
        }
    }
}

@Composable
fun NovaButton(
    label: String,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    primary: Boolean = true,
    compact: Boolean = false,
    destructive: Boolean = false,
) {
    val palette = LocalNovaPalette.current
    val strong = primary || destructive
    FocusableCard(
        onSelect = onSelect,
        modifier = modifier,
        shape = RoundedCornerShape(50),
        focusedScale = 1.06f,
        background = when {
            destructive -> palette.danger
            primary -> palette.accent
            else -> Color.White.copy(alpha = 0.09f)
        },
    ) { focused ->
        Row(
            modifier = Modifier.padding(
                horizontal = if (compact) 16.dp else 24.dp,
                vertical = if (compact) 8.dp else 12.dp,
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (strong) NovaInk else palette.textPrimary,
                    modifier = Modifier.size(19.dp),
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = if (strong) NovaInk else palette.textPrimary,
                maxLines = 1,
            )
        }
        if (focused) {
            Spacer(Modifier.width(0.dp))
        }
    }
}

@Composable
fun IconAction(
    icon: ImageVector,
    label: String,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
    badge: Int = 0,
    size: Dp = 44.dp,
) {
    val palette = LocalNovaPalette.current
    Box(modifier = modifier) {
        FocusableCard(
            onSelect = onSelect,
            shape = CircleShape,
            focusedScale = 1.1f,
            focusedElevation = 10.dp,
            background = Color.White.copy(alpha = 0.06f),
        ) {
            Box(Modifier.size(size), contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = palette.textPrimary,
                    modifier = Modifier.size(21.dp),
                )
            }
        }
        if (badge > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 3.dp, end = 3.dp)
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(palette.accent),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (badge > 9) "9+" else badge.toString(),
                    color = NovaInk,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
fun Pill(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = LocalNovaPalette.current.accent,
    filled: Boolean = false,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(if (filled) color else color.copy(alpha = 0.14f))
            .padding(horizontal = 9.dp, vertical = 4.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium.copy(
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.6.sp,
            ),
            color = if (filled) NovaInk else color,
            maxLines = 1,
        )
    }
}

/**
 * Original abstract artwork generated from a palette seed. No bundled or
 * third-party images are required, so the launcher stays lightweight and the
 * visual identity is entirely its own.
 */
@Composable
fun PosterArt(
    seed: Int,
    modifier: Modifier = Modifier,
    badge: String? = null,
    badgeColor: Color = LocalNovaPalette.current.accent,
    live: Boolean = false,
) {
    val palette = LocalNovaPalette.current
    val hue = ((seed * 43) % 360).toFloat()
    val colorA = Color.hsv(hue, 0.42f, 0.72f)
    val colorB = Color.hsv((hue + 42f) % 360f, 0.62f, 0.42f)
    val colorC = Color.hsv((hue + 305f) % 360f, 0.58f, 0.58f)

    Box(modifier = modifier.clip(RoundedCornerShape(14.dp))) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(
                brush = Brush.linearGradient(
                    colors = listOf(colorB, colorA, NovaInk),
                    start = Offset.Zero,
                    end = Offset(size.width, size.height),
                ),
            )
            drawCircle(
                color = colorC.copy(alpha = 0.32f),
                radius = size.minDimension * 0.62f,
                center = Offset(size.width * 0.78f, size.height * 0.22f),
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.07f),
                radius = size.minDimension * 0.34f,
                center = Offset(size.width * 0.28f, size.height * 0.78f),
            )
            drawCircle(
                color = NovaInk.copy(alpha = 0.18f),
                radius = size.minDimension * 0.8f,
                center = Offset(size.width * 0.05f, size.height * 1.05f),
            )
            val random = Random(seed)
            for (i in 0 until 5) {
                val y = size.height * (0.18f + random.nextFloat() * 0.7f)
                drawLine(
                    color = Color.White.copy(alpha = 0.055f),
                    start = Offset(0f, y),
                    end = Offset(size.width, y - size.height * 0.22f),
                    strokeWidth = (1 + i).toFloat(),
                    cap = StrokeCap.Round,
                )
            }
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color.Transparent, NovaInk.copy(alpha = 0.76f)),
                    startY = size.height * 0.48f,
                    endY = size.height,
                ),
            )
        }

        if (badge != null) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (live) {
                    Box(
                        Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(palette.danger)
                            .align(Alignment.CenterVertically),
                    )
                }
                Pill(text = badge, color = if (live) palette.danger else badgeColor, filled = true)
            }
        }
    }
}

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val palette = LocalNovaPalette.current
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                color = palette.textPrimary,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.textSecondary,
                )
            }
        }
        if (actionLabel != null && onAction != null) {
            NovaButton(
                label = actionLabel,
                onSelect = onAction,
                primary = false,
                compact = true,
            )
        }
    }
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val palette = LocalNovaPalette.current
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = palette.textMuted,
            modifier = Modifier.size(46.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, color = palette.textPrimary)
        Spacer(Modifier.height(6.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = palette.textSecondary,
            modifier = Modifier.width(420.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(20.dp))
            NovaButton(label = actionLabel, onSelect = onAction)
        }
    }
}

@Composable
fun ProgressLine(
    progress: Float,
    modifier: Modifier = Modifier,
    height: Dp = 4.dp,
) {
    val palette = LocalNovaPalette.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.18f)),
    ) {
        Box(
            Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .height(height)
                .background(palette.accent),
        )
    }
}
