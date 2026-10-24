package com.fktm.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.hypot
import kotlin.math.max

/**
 * Compatible Liquid Glass adaptation.
 *
 * The interaction layers follow the backdrop/highlight/lens composition used by
 * Kyant0/AndroidLiquidGlass (Apache-2.0), implemented with the Compose version
 * already used by this app so minSdk 26 remains supported.
 */
@Composable
internal fun LiquidGlassTopActions(
    modifier: Modifier = Modifier,
    showThemeToggle: Boolean,
    darkTheme: Boolean,
    enabled: Boolean,
    onThemeToggle: (Offset) -> Unit,
    onSettings: () -> Unit,
) {
    Row(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showThemeToggle) {
            GlassIconButton(
                glyph = if (darkTheme) "☀" else "☾",
                description = if (darkTheme) "切换到日间模式" else "切换到纯黑暗黑模式",
                enabled = enabled,
                onClick = onThemeToggle,
            )
        }
        GlassIconButton(
            glyph = "⚙",
            description = "打开界面设置",
            enabled = enabled,
            onClick = { onSettings() },
        )
    }
}

@Composable
private fun GlassIconButton(
    glyph: String,
    description: String,
    enabled: Boolean,
    onClick: (Offset) -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.9f else 1f,
        animationSpec = spring(stiffness = 650f, dampingRatio = 0.68f),
        label = "glass-button-scale",
    )
    var center by remember { mutableStateOf(Offset.Zero) }
    val topColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.68f)
    val bottomColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.34f)

    Box(
        Modifier
            .size(46.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .onGloballyPositioned { coordinates ->
                center = coordinates.positionInRoot() + Offset(
                    coordinates.size.width / 2f,
                    coordinates.size.height / 2f,
                )
            }
            .shadow(
                elevation = 10.dp,
                shape = CircleShape,
                clip = false,
                ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
            )
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(topColor, bottomColor)))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.16f),
                shape = CircleShape,
            )
            .semantics {
                contentDescription = description
                role = Role.Button
            }
            .clickable(
                enabled = enabled,
                interactionSource = interaction,
                indication = null,
            ) {
                onClick(center)
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = 0.2f), Color.Transparent),
                    )
                )
        )
        Text(
            glyph,
            fontSize = 20.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
        )
    }
}

@Composable
internal fun LiquidGlassBottomBar(
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    darkTheme: Boolean,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(percent = 50)
    val surface = MaterialTheme.colorScheme.surface
    val accent = MaterialTheme.colorScheme.primary
    val borderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f)
    val glassBrush = Brush.verticalGradient(
        colors = if (darkTheme) {
            listOf(
                Color(0xFF333A37).copy(alpha = 0.62f),
                Color(0xFF171B19).copy(alpha = 0.5f),
                Color(0xFF090B0A).copy(alpha = 0.68f),
            )
        } else {
            listOf(
                Color.White.copy(alpha = 0.78f),
                surface.copy(alpha = 0.56f),
                surface.copy(alpha = 0.72f),
            )
        }
    )

    Box(
        modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 18.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .widthIn(max = 520.dp)
                .height(64.dp),
        ) {
            BoxWithConstraints(
                Modifier
                    .fillMaxSize()
                    .shadow(
                        elevation = 18.dp,
                        shape = shape,
                        clip = false,
                        ambientColor = Color.Black.copy(alpha = 0.2f),
                        spotColor = accent.copy(alpha = 0.2f),
                    )
                    .clip(shape)
                    .background(glassBrush)
                    .border(1.dp, borderColor, shape),
            ) {
                val tabWidth = maxWidth / 2
                val indicatorOffset by animateDpAsState(
                    targetValue = if (selectedIndex == 0) 6.dp else tabWidth + 6.dp,
                    animationSpec = spring(stiffness = 420f, dampingRatio = 0.72f),
                    label = "glass-tab-position",
                )
                val selectedBrush = Brush.horizontalGradient(
                    listOf(
                        accent.copy(alpha = if (darkTheme) 0.45f else 0.3f),
                        accent.copy(alpha = if (darkTheme) 0.22f else 0.16f),
                    )
                )

                Box(
                    Modifier
                        .offset { IntOffset(indicatorOffset.roundToPx(), 0) }
                        .width(tabWidth - 12.dp)
                        .fillMaxHeight()
                        .padding(vertical = 6.dp)
                        .clip(shape)
                        .background(selectedBrush)
                        .border(
                            1.dp,
                            Color.White.copy(alpha = if (darkTheme) 0.17f else 0.5f),
                            shape,
                        ),
                ) {
                    Box(
                        Modifier
                            .matchParentSize()
                            .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.White.copy(alpha = if (darkTheme) 0.1f else 0.22f),
                                    Color.Transparent,
                                )
                            )
                        ),
                    )
                }

                Row(Modifier.fillMaxSize()) {
                    GlassTab(
                        modifier = Modifier.weight(1f),
                        selected = selectedIndex == 0,
                        label = "首页",
                        onClick = { onSelected(0) },
                    ) {
                        HomeGlyph(it)
                    }
                    GlassTab(
                        modifier = Modifier.weight(1f),
                        selected = selectedIndex == 1,
                        label = "我的",
                        onClick = { onSelected(1) },
                    ) {
                        ProfileGlyph(it)
                    }
                }
            }
        }
    }
}

@Composable
private fun GlassTab(
    modifier: Modifier,
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
    icon: @Composable (Color) -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.9f else 1f,
        animationSpec = spring(stiffness = 700f, dampingRatio = 0.62f),
        label = "glass-tab-press",
    )
    val color = if (selected) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f)
    }

    Box(
        modifier
            .fillMaxHeight()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .semantics {
                contentDescription = label
                role = Role.Tab
            }
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Tab,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Box(Modifier.size(20.dp)) { icon(color) }
            Text(
                label,
                fontSize = 12.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = color,
            )
        }
    }
}

@Composable
private fun HomeGlyph(color: Color) {
    Canvas(Modifier.fillMaxSize()) {
        val stroke = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round)
        val path = Path().apply {
            moveTo(size.width * 0.12f, size.height * 0.48f)
            lineTo(size.width * 0.5f, size.height * 0.13f)
            lineTo(size.width * 0.88f, size.height * 0.48f)
            moveTo(size.width * 0.22f, size.height * 0.43f)
            lineTo(size.width * 0.22f, size.height * 0.86f)
            lineTo(size.width * 0.78f, size.height * 0.86f)
            lineTo(size.width * 0.78f, size.height * 0.43f)
        }
        drawPath(path, color, style = stroke)
    }
}

@Composable
private fun ProfileGlyph(color: Color) {
    Canvas(Modifier.fillMaxSize()) {
        val stroke = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round)
        drawCircle(
            color = color,
            radius = size.minDimension * 0.17f,
            center = Offset(size.width * 0.5f, size.height * 0.3f),
            style = stroke,
        )
        drawArc(
            color = color,
            startAngle = 205f,
            sweepAngle = 130f,
            useCenter = false,
            topLeft = Offset(size.width * 0.19f, size.height * 0.43f),
            size = Size(size.width * 0.62f, size.height * 0.5f),
            style = stroke,
        )
    }
}

internal data class ThemeRevealSpec(
    val id: Long,
    val center: Offset,
    val targetDark: Boolean,
)

@Composable
internal fun ThemeRevealOverlay(
    spec: ThemeRevealSpec,
    onCovered: () -> Unit,
    onFinished: () -> Unit,
) {
    val reveal = remember { Animatable(0f) }
    val fade = remember { Animatable(1f) }
    val targetColor = if (spec.targetDark) Color.Black else Color(0xFFF5F7F5)
    val accent = MaterialTheme.colorScheme.primary

    LaunchedEffect(spec.id) {
        reveal.animateTo(
            targetValue = 1f,
            animationSpec = tween(420, easing = FastOutSlowInEasing),
        )
        onCovered()
        fade.animateTo(
            targetValue = 0f,
            animationSpec = tween(270, easing = FastOutSlowInEasing),
        )
        onFinished()
    }

    Canvas(
        Modifier
            .fillMaxSize()
            .pointerInput(spec.id) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        event.changes.forEach { it.consume() }
                    }
                }
            }
    ) {
        val farthestX = max(spec.center.x, size.width - spec.center.x)
        val farthestY = max(spec.center.y, size.height - spec.center.y)
        val maxRadius = hypot(farthestX, farthestY)
        val radius = maxRadius * reveal.value
        drawCircle(
            color = accent.copy(alpha = 0.18f),
            radius = radius * 1.035f,
            center = spec.center,
            alpha = fade.value,
        )
        drawCircle(
            color = targetColor,
            radius = radius,
            center = spec.center,
            alpha = fade.value,
        )
    }
}
