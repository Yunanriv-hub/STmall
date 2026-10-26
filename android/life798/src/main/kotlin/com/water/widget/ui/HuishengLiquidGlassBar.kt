package com.water.widget.ui

import android.animation.ValueAnimator
import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow
import kotlin.math.abs
import kotlin.math.roundToInt

internal data class LiquidGlassTab(
    val label: String,
    val icon: ImageVector,
)

/**
 * STmall adaptation of the AndroidLiquidGlass bottom-tab principles (Apache-2.0):
 * translucent optical layers, a spring-driven selected lens, and press deformation.
 */
@Composable
internal fun HuishengLiquidGlassBar(
    tabs: List<LiquidGlassTab>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    backdrop: LayerBackdrop? = null,
    glassEnabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    if (tabs.isEmpty()) return
    val darkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(percent = 50)
    val animationsEnabled = ValueAnimator.areAnimatorsEnabled()
    val density = LocalDensity.current
    var dragPosition by remember { mutableFloatStateOf(selectedIndex.toFloat()) }
    var isDragging by remember { mutableStateOf(false) }
    var suppressClicksUntil by remember { mutableLongStateOf(0L) }
    LaunchedEffect(selectedIndex, isDragging) {
        if (!isDragging) dragPosition = selectedIndex.toFloat()
    }
    val animatedPosition by animateFloatAsState(
        targetValue = dragPosition,
        animationSpec = if (isDragging || !animationsEnabled) {
            snap()
        } else {
            spring(stiffness = 430f, dampingRatio = 0.7f)
        },
        label = "huisheng-glass-tab-drag-position",
    )
    fun accentFor(index: Int): Color = when (index) {
        0 -> colors.primary
        1 -> if (darkTheme) Color(0xFF8AB4F8) else Color(0xFF356EA8)
        else -> if (darkTheme) Color(0xFFF0B86E) else Color(0xFF8A5A1C)
    }
    val lowerIndex = animatedPosition.toInt().coerceIn(0, tabs.lastIndex)
    val upperIndex = (lowerIndex + 1).coerceAtMost(tabs.lastIndex)
    val accentFraction = (animatedPosition - lowerIndex).coerceIn(0f, 1f)
    val activeAccent = lerp(
        accentFor(lowerIndex),
        accentFor(upperIndex),
        accentFraction,
    )
    val dragDistance = (animatedPosition - selectedIndex).coerceIn(-1f, 1f)
    val barTranslationPx = with(density) { (1.5.dp * dragDistance).toPx() }
    val barStretch = abs(dragDistance)
    val lensStretch by animateFloatAsState(
        targetValue = if (isDragging) abs(dragDistance) * 0.022f else 0f,
        animationSpec = if (animationsEnabled) {
            spring(stiffness = 620f, dampingRatio = 0.68f)
        } else {
            snap()
        },
        label = "huisheng-glass-lens-stretch",
    )
    val useNativeGlass = glassEnabled &&
        backdrop != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val tabsBackdrop = rememberLayerBackdrop()
    val selectedBackdrop = if (useNativeGlass) {
        rememberCombinedBackdrop(backdrop!!, tabsBackdrop)
    } else {
        null
    }
    val glassBrush = Brush.verticalGradient(
        listOf(
            colors.surface.copy(
                alpha = if (useNativeGlass) {
                    if (darkTheme) 0.3f else 0.34f
                } else {
                    if (darkTheme) 0.74f else 0.82f
                }
            ),
            colors.surfaceVariant.copy(
                alpha = if (useNativeGlass) {
                    if (darkTheme) 0.18f else 0.2f
                } else {
                    if (darkTheme) 0.56f else 0.64f
                }
            ),
            colors.background.copy(
                alpha = if (useNativeGlass) {
                    if (darkTheme) 0.22f else 0.22f
                } else {
                    if (darkTheme) 0.82f else 0.72f
                }
            ),
        )
    )
    val rim = Color.White.copy(alpha = if (darkTheme) 0.14f else 0.62f)
    val baseSurfaceModifier = if (useNativeGlass) {
        Modifier.drawBackdrop(
            backdrop = backdrop!!,
            shape = { shape },
            effects = {
                vibrancy()
                blur(5.dp.toPx())
                lens(
                    refractionHeight = 16.dp.toPx(),
                    refractionAmount = 26.dp.toPx(),
                    depthEffect = true,
                    chromaticAberration = true,
                )
            },
            highlight = { Highlight.Ambient },
            shadow = {
                Shadow.Default.copy(
                    radius = 18.dp,
                    color = Color.Black.copy(alpha = 0.24f),
                )
            },
            exportedBackdrop = tabsBackdrop,
            onDrawSurface = { drawRect(glassBrush) },
        )
    } else if (glassEnabled) {
        Modifier
            .shadow(
                elevation = 18.dp,
                shape = shape,
                clip = false,
                ambientColor = Color.Black.copy(alpha = 0.28f),
                spotColor = colors.primary.copy(alpha = 0.24f),
            )
            .clip(shape)
            .background(glassBrush)
            .border(1.dp, rim, shape)
    } else {
        Modifier
            .clip(shape)
            .background(colors.surface)
    }

    Box(
        modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 18.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .widthIn(max = 520.dp)
                .height(64.dp),
        ) {
            val tabWidth = maxWidth / tabs.size
            val indicatorOffset = with(density) {
                (tabWidth * animatedPosition + 6.dp).roundToPx()
            }

            Box(
                Modifier
                    .fillMaxSize()
                    .then(baseSurfaceModifier)
                    .graphicsLayer {
                        translationX = barTranslationPx
                        scaleX = 1f + barStretch * 0.008f
                        scaleY = 1f - barStretch * 0.004f
                    }
                    .pointerInput(selectedIndex, tabs.size) {
                        val tabWidthPx = (size.width.toFloat() / tabs.size).coerceAtLeast(1f)
                        val touchSlop = viewConfiguration.touchSlop
                        awaitEachGesture {
                            val down = awaitFirstDown(
                                requireUnconsumed = false,
                                pass = PointerEventPass.Initial,
                            )
                            var lastX = down.position.x
                            var totalDx = 0f
                            var draggingNow = false
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                val dx = change.position.x - lastX
                                lastX = change.position.x
                                totalDx += dx
                                if (!draggingNow && abs(totalDx) > touchSlop) {
                                    draggingNow = true
                                    isDragging = true
                                    suppressClicksUntil = Long.MAX_VALUE
                                }
                                if (draggingNow) {
                                    change.consume()
                                    dragPosition = (
                                        dragPosition + dx * 0.82f / tabWidthPx
                                    ).coerceIn(0f, tabs.lastIndex.toFloat())
                                }
                                if (change.changedToUpIgnoreConsumed()) break
                            }
                            if (draggingNow) {
                                val target = dragPosition.roundToInt().coerceIn(0, tabs.lastIndex)
                                dragPosition = target.toFloat()
                                isDragging = false
                                suppressClicksUntil = System.currentTimeMillis() + 250L
                                if (target != selectedIndex) onSelected(target)
                            }
                        }
                    },
            ) {
                if (glassEnabled && !useNativeGlass) {
                    Canvas(Modifier.fillMaxSize()) {
                        val highlightCenter = Offset(
                            x = size.width * (selectedIndex + 0.5f) / tabs.size,
                            y = size.height * 0.45f,
                        )
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    colors.primary.copy(alpha = if (darkTheme) 0.18f else 0.12f),
                                    Color.Transparent,
                                ),
                                center = highlightCenter,
                                radius = size.width / tabs.size * 1.15f,
                            ),
                            radius = size.width / tabs.size * 1.15f,
                            center = highlightCenter,
                        )
                        drawRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = if (darkTheme) 0.12f else 0.3f),
                                    Color.Transparent,
                                ),
                                startY = 0f,
                                endY = size.height * 0.58f,
                            )
                        )
                    }
                }

                if (glassEnabled) Canvas(Modifier.fillMaxSize()) {
                    val glowCenter = Offset(
                        x = size.width * (animatedPosition + 0.5f) / tabs.size,
                        y = size.height * 0.5f,
                    )
                    val glowRadius = size.width / tabs.size * 0.75f
                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(activeAccent.copy(alpha = 0.26f), Color.Transparent),
                            center = glowCenter,
                            radius = glowRadius,
                        ),
                        radius = glowRadius,
                        center = glowCenter,
                    )
                }

                val selectedBrush = Brush.horizontalGradient(
                    listOf(
                        activeAccent.copy(alpha = if (darkTheme) 0.42f else 0.3f),
                        lerp(activeAccent, colors.surface, 0.24f).copy(
                            alpha = if (darkTheme) 0.18f else 0.12f
                        ),
                    )
                )
                val indicatorSurface = if (selectedBackdrop != null) {
                    Modifier.drawBackdrop(
                        backdrop = selectedBackdrop,
                        shape = { shape },
                        effects = {
                            blur(1.5.dp.toPx())
                            lens(
                                refractionHeight = 11.dp.toPx(),
                                refractionAmount = 20.dp.toPx(),
                                depthEffect = true,
                                chromaticAberration = true,
                            )
                        },
                        highlight = { Highlight.Ambient.copy(alpha = 0.9f) },
                        shadow = null,
                        onDrawSurface = { drawRect(selectedBrush) },
                    )
                } else if (glassEnabled) {
                    Modifier
                        .clip(shape)
                        .background(selectedBrush)
                        .border(
                            1.dp,
                            Color.White.copy(alpha = if (darkTheme) 0.16f else 0.46f),
                            shape,
                        )
                } else {
                    Modifier
                        .clip(shape)
                        .background(activeAccent)
                }

                Box(
                    Modifier
                        .offset { IntOffset(indicatorOffset, 0) }
                        .width(tabWidth - 12.dp)
                        .fillMaxHeight()
                        .padding(vertical = 6.dp)
                        .graphicsLayer {
                            scaleX = 1f + lensStretch
                            scaleY = 1f - lensStretch * 0.28f
                        }
                        .then(indicatorSurface),
                ) {
                    if (glassEnabled) {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        listOf(
                                            Color.White.copy(alpha = if (darkTheme) 0.1f else 0.2f),
                                            Color.Transparent,
                                        )
                                    )
                                )
                        )
                    }
                }

                Row(
                    Modifier.fillMaxSize().selectableGroup(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    tabs.forEachIndexed { index, tab ->
                        LiquidGlassTabButton(
                            modifier = Modifier.weight(1f),
                            tab = tab,
                            selected = selectedIndex == index,
                            selectionProgress = (
                                1f - abs(animatedPosition - index.toFloat())
                            ).coerceIn(0f, 1f),
                            accentColor = accentFor(index),
                            animationsEnabled = animationsEnabled,
                            onClick = {
                                if (System.currentTimeMillis() >= suppressClicksUntil) {
                                    onSelected(index)
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.LiquidGlassTabButton(
    modifier: Modifier,
    tab: LiquidGlassTab,
    selected: Boolean,
    selectionProgress: Float,
    accentColor: Color,
    animationsEnabled: Boolean,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 1.12f else 1f,
        animationSpec = if (animationsEnabled) {
            spring(stiffness = 650f, dampingRatio = 0.66f)
        } else {
            snap()
        },
        label = "huisheng-tab-press",
    )
    val contentColor by animateColorAsState(
        targetValue = lerp(
            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.76f),
            accentColor,
            selectionProgress,
        ),
        label = "huisheng-tab-color",
    )
    val selectedScale = 0.96f + selectionProgress * 0.04f

    Row(
        modifier
            .fillMaxHeight()
            .graphicsLayer {
                scaleX = pressScale * selectedScale
                scaleY = pressScale * selectedScale
            }
            .selectable(
                selected = selected,
                interactionSource = interaction,
                indication = null,
                role = Role.Tab,
                onClick = onClick,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
    ) {
        Icon(
            imageVector = tab.icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(21.dp),
        )
        Spacer(Modifier.size(7.dp))
        Text(
            tab.label,
            color = contentColor,
            fontSize = 12.sp,
            fontWeight = if (selectionProgress >= 0.5f) FontWeight.Bold else FontWeight.Medium,
        )
    }
}
