package com.fktm.app.ui

import android.animation.ValueAnimator
import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CardElevation
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import com.fktm.app.ui.CatalogLiquidTextButton as TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow
import kotlin.math.hypot
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

internal val LocalLiquidGlassBackdrop = staticCompositionLocalOf<LayerBackdrop?> { null }
internal val LocalLiquidGlassEnabled = staticCompositionLocalOf { true }

@Composable
internal fun CumpusGlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(22.dp),
    colors: CardColors = CardDefaults.cardColors(),
    elevation: CardElevation = CardDefaults.cardElevation(),
    border: BorderStroke? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val backdrop = LocalLiquidGlassBackdrop.current
    val glassEnabled = LocalLiquidGlassEnabled.current
    val useNativeGlass = false
    val color = colors.containerColor
    val surfaceModifier = if (useNativeGlass) {
        Modifier.drawBackdrop(
            backdrop = backdrop!!,
            shape = { shape },
            effects = {},
            highlight = { Highlight.Ambient },
            shadow = {
                Shadow.Default.copy(
                    radius = 14.dp,
                    color = Color.Black.copy(alpha = 0.15f),
                )
            },
            onDrawSurface = {
                drawRect(
                    Brush.verticalGradient(
                        listOf(
                            color.copy(alpha = 0.36f),
                            Color.Transparent,
                        )
                    )
                )
            },
        )
    } else if (glassEnabled) {
        Modifier
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(
                        color.copy(alpha = 0.9f),
                        color.copy(alpha = 0.68f),
                    )
                )
            )
            .border(1.dp, Color.White.copy(alpha = 0.18f), shape)
    } else {
        Modifier.clip(shape).background(color)
    }
    Box(
        modifier
            .then(surfaceModifier)
    ) {
        border?.let {
            Box(Modifier.matchParentSize().border(it, shape))
        }
        CompositionLocalProvider(LocalContentColor provides colors.contentColor) {
            Column(Modifier.fillMaxWidth(), content = content)
        }
    }
}

@Composable
internal fun CumpusGlassAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    shape: Shape = RoundedCornerShape(28.dp),
    containerColor: Color = MaterialTheme.colorScheme.surface,
    properties: DialogProperties = DialogProperties(
        usePlatformDefaultWidth = false,
        decorFitsSystemWindows = false,
    ),
) {
    Dialog(onDismissRequest = onDismissRequest, properties = properties) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.3f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismissRequest,
                )
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 22.dp, vertical = 24.dp),
            contentAlignment = Alignment.Center,
        ) {
            CumpusGlassCard(
                modifier = modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {},
                shape = shape,
                colors = CardDefaults.cardColors(containerColor = containerColor),
            ) {
                Column(
                    Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    icon?.invoke()
                    title?.invoke()
                    text?.invoke()
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        dismissButton?.invoke()
                        confirmButton()
                    }
                }
            }
        }
    }
}

/**
 * Liquid Glass surface supplied by Kyant0/Backdrop (Apache-2.0).
 *
 * API 31+ samples and blurs the real page behind the surface; API 33+ also
 * applies the rounded-rectangle lens. Older devices keep the lightweight
 * optical fallback so minSdk 26 behavior remains unchanged.
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
                darkTheme = darkTheme,
                enabled = enabled,
                onClick = onThemeToggle,
            )
        }
        GlassIconButton(
            glyph = "⚙",
            description = "打开界面设置",
            darkTheme = darkTheme,
            enabled = enabled,
            onClick = { onSettings() },
        )
    }
}

@Composable
private fun GlassIconButton(
    glyph: String,
    description: String,
    darkTheme: Boolean,
    enabled: Boolean,
    onClick: (Offset) -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val catalogInteraction = rememberCatalogGlassInteraction()
    val backdrop = LocalLiquidGlassBackdrop.current
    val glassEnabled = LocalLiquidGlassEnabled.current
    val useNativeGlass = false
    var center by remember { mutableStateOf(Offset.Zero) }
    val topColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.68f)
    val bottomColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.34f)
    val surfaceModifier = if (useNativeGlass) {
        Modifier.drawBackdrop(
            backdrop = backdrop!!,
            shape = { CircleShape },
            effects = {
                vibrancy()
                blur(4.dp.toPx())
                lens(
                    refractionHeight = 9.dp.toPx(),
                    refractionAmount = 16.dp.toPx(),
                    depthEffect = true,
                    chromaticAberration = true,
                )
            },
            highlight = { Highlight.Ambient.copy(alpha = 0.82f) },
            shadow = {
                Shadow.Default.copy(
                    radius = 12.dp,
                    color = Color.Black.copy(alpha = 0.2f),
                )
            },
            layerBlock = {
                catalogPressTransform(
                    catalogInteraction,
                    1f + 4.dp.toPx() / size.height.coerceAtLeast(1f),
                )
            },
            onDrawSurface = {
                drawRect(
                    Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = if (darkTheme) 0.08f else 0.16f),
                            topColor.copy(alpha = if (darkTheme) 0.16f else 0.14f),
                            bottomColor.copy(alpha = if (darkTheme) 0.12f else 0.1f),
                        )
                    )
                )
                drawRect(
                    Brush.radialGradient(
                        listOf(Color.White.copy(alpha = 0.2f), Color.Transparent),
                        center = Offset(size.width * 0.25f, 0f),
                        radius = size.maxDimension * 0.9f,
                    )
                )
            },
        )
    } else if (glassEnabled) {
        Modifier
            .graphicsLayer {
                catalogPressTransform(
                    catalogInteraction,
                    1f + 4.dp.toPx() / size.height.coerceAtLeast(1f),
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
    } else {
        Modifier
            .graphicsLayer {
                catalogPressTransform(
                    catalogInteraction,
                    1f + 4.dp.toPx() / size.height.coerceAtLeast(1f),
                )
            }
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.16f),
                shape = CircleShape,
            )
    }

    Box(
        Modifier
            .size(46.dp)
            .then(surfaceModifier)
            .onGloballyPositioned { coordinates ->
                center = coordinates.positionInRoot() + Offset(
                    coordinates.size.width / 2f,
                    coordinates.size.height / 2f,
                )
            }
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
            }
            .then(catalogInteraction.clippedModifier(CircleShape))
            .then(catalogInteraction.gestureModifier),
        contentAlignment = Alignment.Center,
    ) {
        if (glassEnabled) {
            Box(
                Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.White.copy(alpha = 0.2f), Color.Transparent),
                        )
                    )
            )
        }
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
    val featureAccent = if (darkTheme) Color(0xFFC2A7FF) else Color(0xFF6E4AB0)
    val profileAccent = if (darkTheme) Color(0xFF8AB4F8) else Color(0xFF356EA8)
    val density = LocalDensity.current
    val animationsEnabled = ValueAnimator.areAnimatorsEnabled()
    val barInteraction = rememberCatalogGlassInteraction()
    val borderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f)
    val backdrop = LocalLiquidGlassBackdrop.current
    val glassEnabled = LocalLiquidGlassEnabled.current
    val useNativeGlass = glassEnabled &&
        backdrop != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
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
            spring(stiffness = 430f, dampingRatio = 0.72f)
        },
        label = "glass-tab-drag-position",
    )
    val lowerAccentIndex = animatedPosition.toInt().coerceIn(0, 1)
    val accentFraction = (animatedPosition - lowerAccentIndex).coerceIn(0f, 1f)
    val accentAt: (Int) -> Color = { index ->
        when (index) {
            0 -> accent
            1 -> featureAccent
            else -> profileAccent
        }
    }
    val activeAccent = lerp(
        accentAt(lowerAccentIndex),
        accentAt((lowerAccentIndex + 1).coerceAtMost(2)),
        accentFraction,
    )
    val dragDistance = (animatedPosition - selectedIndex).coerceIn(-2f, 2f)
    val barTranslationPx = with(density) { (1.5.dp * dragDistance).toPx() }
    val barStretch = abs(dragDistance) / 2f
    val lensStretch by animateFloatAsState(
        targetValue = if (isDragging) abs((animatedPosition - selectedIndex).coerceIn(-1f, 1f)) * 0.022f else 0f,
        animationSpec = if (animationsEnabled) {
            spring(stiffness = 620f, dampingRatio = 0.68f)
        } else {
            snap()
        },
        label = "glass-tab-lens-stretch",
    )
    val tabsBackdrop = rememberLayerBackdrop()
    val selectedBackdrop = if (useNativeGlass) {
        rememberCombinedBackdrop(backdrop!!, tabsBackdrop)
    } else {
        null
    }
    val glassBrush = Brush.verticalGradient(
        colors = if (darkTheme) {
            listOf(
                Color(0xFF333A37).copy(alpha = if (useNativeGlass) 0.32f else 0.5f),
                Color(0xFF171B19).copy(alpha = if (useNativeGlass) 0.2f else 0.3f),
                Color(0xFF090B0A).copy(alpha = if (useNativeGlass) 0.24f else 0.42f),
            )
        } else {
            listOf(
                Color.White.copy(alpha = if (useNativeGlass) 0.34f else 0.58f),
                surface.copy(alpha = if (useNativeGlass) 0.2f else 0.26f),
                surface.copy(alpha = if (useNativeGlass) 0.22f else 0.38f),
            )
        }
    )
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
                    color = Color.Black.copy(alpha = 0.22f),
                )
            },
            exportedBackdrop = tabsBackdrop,
            layerBlock = {
                catalogPressTransform(
                    barInteraction,
                    1f + 16.dp.toPx() / size.width.coerceAtLeast(1f),
                )
            },
            onDrawSurface = { drawRect(glassBrush) },
        )
    } else if (glassEnabled) {
        Modifier
            .shadow(
                elevation = 18.dp,
                shape = shape,
                clip = false,
                ambientColor = Color.Black.copy(alpha = 0.2f),
                spotColor = accent.copy(alpha = 0.2f),
            )
            .clip(shape)
            .background(glassBrush)
            .border(1.dp, borderColor, shape)
            .graphicsLayer {
                catalogPressTransform(
                    barInteraction,
                    1f + 16.dp.toPx() / size.width.coerceAtLeast(1f),
                )
            }
    } else {
        Modifier
            .clip(shape)
            .background(surface)
            .graphicsLayer {
                catalogPressTransform(
                    barInteraction,
                    1f + 16.dp.toPx() / size.width.coerceAtLeast(1f),
                )
            }
    }

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
                    .then(baseSurfaceModifier)
                    .graphicsLayer {
                        translationX = barTranslationPx
                        scaleX = 1f + barStretch * 0.008f
                        scaleY = 1f - barStretch * 0.004f
                    }
                    .pointerInput(selectedIndex) {
                        val tabWidthPx = (size.width / 3f).coerceAtLeast(1f)
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
                                    ).coerceIn(0f, 2f)
                                }
                                if (change.changedToUpIgnoreConsumed()) break
                            }
                            if (draggingNow) {
                                val target = dragPosition.roundToInt().coerceIn(0, 2)
                                dragPosition = target.toFloat()
                                isDragging = false
                                suppressClicksUntil = System.currentTimeMillis() + 250L
                                if (target != selectedIndex) onSelected(target)
                            }
                        }
                    }
                    .then(barInteraction.clippedModifier(shape))
                    .then(barInteraction.gestureModifier),
            ) {
                val tabWidth = maxWidth / 3
                val indicatorOffset = with(density) {
                    (tabWidth * animatedPosition + 6.dp).roundToPx()
                }
                val selectedBrush = Brush.horizontalGradient(
                    listOf(
                        activeAccent.copy(alpha = if (darkTheme) 0.4f else 0.28f),
                        lerp(activeAccent, surface, 0.24f)
                            .copy(alpha = if (darkTheme) 0.16f else 0.1f),
                    )
                )
                if (glassEnabled) {
                    Canvas(Modifier.matchParentSize()) {
                        val glowCenter = Offset(
                            x = size.width * (animatedPosition + 0.5f) / 3f,
                            y = size.height * 0.5f,
                        )
                        drawCircle(
                            brush = Brush.radialGradient(
                                listOf(activeAccent.copy(alpha = 0.24f), Color.Transparent),
                                center = glowCenter,
                                radius = size.width * 0.34f,
                            ),
                            radius = size.width * 0.34f,
                            center = glowCenter,
                        )
                    }
                }
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
                            Color.White.copy(alpha = if (darkTheme) 0.17f else 0.5f),
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
                                .matchParentSize()
                                .background(
                                    Brush.verticalGradient(
                                        listOf(
                                            Color.White.copy(alpha = if (darkTheme) 0.1f else 0.22f),
                                            Color.Transparent,
                                        )
                                    )
                                )
                        )
                    }
                }

                Row(Modifier.fillMaxSize()) {
                    GlassTab(
                        modifier = Modifier.weight(1f),
                        selected = selectedIndex == 0,
                        selectionProgress = (1f - abs(animatedPosition - 0f)).coerceIn(0f, 1f),
                        accentColor = accent,
                        label = "首页",
                        onClick = {
                            if (System.currentTimeMillis() >= suppressClicksUntil) {
                                onSelected(0)
                            }
                        },
                    ) {
                        HomeGlyph(it)
                    }
                    GlassTab(
                        modifier = Modifier.weight(1f),
                        selected = selectedIndex == 1,
                        selectionProgress = (1f - abs(animatedPosition - 1f)).coerceIn(0f, 1f),
                        accentColor = featureAccent,
                        label = "功能",
                        onClick = {
                            if (System.currentTimeMillis() >= suppressClicksUntil) {
                                onSelected(1)
                            }
                        },
                    ) {
                        FeatureGlyph(it)
                    }
                    GlassTab(
                        modifier = Modifier.weight(1f),
                        selected = selectedIndex == 2,
                        selectionProgress = (1f - abs(animatedPosition - 2f)).coerceIn(0f, 1f),
                        accentColor = profileAccent,
                        label = "我的",
                        onClick = {
                            if (System.currentTimeMillis() >= suppressClicksUntil) {
                                onSelected(2)
                            }
                        },
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
    selectionProgress: Float,
    accentColor: Color,
    label: String,
    onClick: () -> Unit,
    icon: @Composable (Color) -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 1.12f else 1f,
        animationSpec = spring(stiffness = 700f, dampingRatio = 0.62f),
        label = "glass-tab-press",
    )
    val glassEnabled = LocalLiquidGlassEnabled.current
    val selectedContentColor = if (glassEnabled) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onPrimary
    }
    val color = lerp(
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.88f),
        selectedContentColor,
        selectionProgress,
    )
    val selectedScale = 0.96f + selectionProgress * 0.04f

    Box(
        modifier
            .fillMaxHeight()
            .graphicsLayer {
                scaleX = scale * selectedScale
                scaleY = scale * selectedScale
            }
            .semantics {
                contentDescription = label
                role = Role.Tab
                this.selected = selected
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
                fontWeight = if (selectionProgress >= 0.5f) FontWeight.Bold else FontWeight.Medium,
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
private fun FeatureGlyph(color: Color) {
    Canvas(Modifier.fillMaxSize()) {
        val stroke = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round)
        val gap = size.minDimension * 0.16f
        val cell = (size.minDimension - gap) / 2f
        listOf(
            Offset(0f, 0f),
            Offset(cell + gap, 0f),
            Offset(0f, cell + gap),
            Offset(cell + gap, cell + gap),
        ).forEach { offset ->
            drawRoundRect(
                color = color,
                topLeft = offset,
                size = Size(cell, cell),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(cell * 0.28f),
                style = stroke,
            )
        }
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

@Composable
internal fun LiquidGlassPanel(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    content: @Composable BoxScope.() -> Unit,
) {
    val surface = MaterialTheme.colorScheme.surface
    val container = MaterialTheme.colorScheme.primary
    val backdrop = LocalLiquidGlassBackdrop.current
    val glassEnabled = LocalLiquidGlassEnabled.current
    val useNativeGlass = false
    val glass = Brush.verticalGradient(
        listOf(
            surface.copy(alpha = if (useNativeGlass) 0.42f else 0.82f),
            surface.copy(alpha = if (useNativeGlass) 0.2f else 0.58f),
            MaterialTheme.colorScheme.background.copy(
                alpha = if (useNativeGlass) 0.24f else 0.72f
            ),
        )
    )
    val surfaceModifier = if (useNativeGlass) {
        Modifier.drawBackdrop(
            backdrop = backdrop!!,
            shape = { shape },
            effects = {},
            highlight = { Highlight.Ambient },
            shadow = {
                Shadow.Default.copy(
                    radius = 16.dp,
                    color = Color.Black.copy(alpha = 0.18f),
                )
            },
            onDrawSurface = {
                drawRect(glass)
                drawRect(
                    Brush.radialGradient(
                        listOf(Color.White.copy(alpha = 0.16f), Color.Transparent),
                        center = Offset(size.width * 0.16f, 0f),
                        radius = size.maxDimension * 0.9f,
                    )
                )
            },
        )
    } else if (glassEnabled) {
        Modifier
            .clip(shape)
            .background(glass)
            .border(1.dp, Color.White.copy(alpha = 0.2f), shape)
    } else {
        Modifier
            .clip(shape)
            .background(surface)
    }
    Box(
        modifier
            .then(surfaceModifier)
    ) {
        if (useNativeGlass) {
            Canvas(Modifier.matchParentSize()) {
                drawRect(
                    brush = Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = 0.2f), Color.Transparent),
                        endY = size.height * 0.62f,
                    )
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(container.copy(alpha = 0.14f), Color.Transparent),
                        center = Offset(size.width * 0.18f, size.height * 0.5f),
                        radius = size.maxDimension * 0.72f,
                    ),
                    center = Offset(size.width * 0.18f, size.height * 0.5f),
                    radius = size.maxDimension * 0.72f,
                )
            }
        }
        CompositionLocalProvider(
            LocalContentColor provides MaterialTheme.colorScheme.onSurface
        ) {
            Box(Modifier.padding(contentPadding), content = content)
        }
    }
}

@Composable
internal fun LiquidGlassAmbientBackground(
    darkTheme: Boolean,
    modifier: Modifier = Modifier,
) {
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val tertiary = MaterialTheme.colorScheme.tertiary
    val darkFraction by animateFloatAsState(
        targetValue = if (darkTheme) 1f else 0f,
        animationSpec = tween(360, easing = FastOutSlowInEasing),
        label = "ambient-theme-fraction",
    )
    Canvas(modifier.fillMaxSize()) {
        val primaryAlpha = 0.24f + (0.11f - 0.24f) * darkFraction
        val secondaryAlpha = 0.18f + (0.085f - 0.18f) * darkFraction
        val tertiaryAlpha = 0.14f + (0.06f - 0.14f) * darkFraction
        drawCircle(
            brush = Brush.radialGradient(
                listOf(primary.copy(alpha = primaryAlpha), Color.Transparent),
                center = Offset(size.width * 0.12f, size.height * 0.2f),
                radius = size.width * 0.8f,
            ),
            radius = size.width * 0.8f,
            center = Offset(size.width * 0.12f, size.height * 0.2f),
        )
        drawCircle(
            brush = Brush.radialGradient(
                listOf(secondary.copy(alpha = secondaryAlpha), Color.Transparent),
                center = Offset(size.width * 0.92f, size.height * 0.58f),
                radius = size.width * 0.72f,
            ),
            radius = size.width * 0.72f,
            center = Offset(size.width * 0.92f, size.height * 0.58f),
        )
        drawCircle(
            brush = Brush.radialGradient(
                listOf(tertiary.copy(alpha = tertiaryAlpha), Color.Transparent),
                center = Offset(size.width * 0.24f, size.height * 0.96f),
                radius = size.width * 0.66f,
            ),
            radius = size.width * 0.66f,
            center = Offset(size.width * 0.24f, size.height * 0.96f),
        )

        val bubbleAlpha = 0.12f + (0.07f - 0.12f) * darkFraction
        val bubbleCenter = Offset(size.width * 0.78f, size.height * 0.42f)
        val bubbleRadius = size.width * 0.075f
        drawCircle(
            brush = Brush.radialGradient(
                listOf(
                    Color.White.copy(alpha = bubbleAlpha * 0.9f),
                    secondary.copy(alpha = bubbleAlpha),
                    Color.Transparent,
                ),
                center = bubbleCenter,
                radius = bubbleRadius,
            ),
            radius = bubbleRadius,
            center = bubbleCenter,
        )
    }
}

@Composable
internal fun LiquidGlassIconDisc(
    discSize: Dp,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val backdrop = LocalLiquidGlassBackdrop.current
    val glassEnabled = LocalLiquidGlassEnabled.current
    val useNativeGlass = glassEnabled &&
        backdrop != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val surface = MaterialTheme.colorScheme.surface
    val surfaceModifier = if (useNativeGlass) {
        Modifier.drawBackdrop(
            backdrop = backdrop!!,
            shape = { CircleShape },
            effects = {
                vibrancy()
                blur(2.5.dp.toPx())
                lens(
                    refractionHeight = 8.dp.toPx(),
                    refractionAmount = 16.dp.toPx(),
                    depthEffect = true,
                    chromaticAberration = true,
                )
            },
            highlight = { Highlight.Ambient.copy(alpha = 0.78f) },
            shadow = {
                Shadow.Default.copy(
                    radius = 8.dp,
                    color = Color.Black.copy(alpha = 0.12f),
                )
            },
            onDrawSurface = {
                drawRect(surface.copy(alpha = 0.18f))
                drawRect(
                    Brush.radialGradient(
                        listOf(Color.White.copy(alpha = 0.2f), Color.Transparent),
                        center = Offset(size.width * 0.2f, 0f),
                        radius = size.maxDimension * 0.9f,
                    )
                )
            },
        )
    } else if (glassEnabled) {
        Modifier
            .clip(CircleShape)
            .background(surface.copy(alpha = 0.78f))
            .border(
                1.dp,
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                CircleShape,
            )
    } else {
        Modifier
            .clip(CircleShape)
            .background(surface)
    }
    Box(
        modifier
            .size(discSize)
            .then(surfaceModifier),
        contentAlignment = Alignment.Center,
        content = content,
    )
}

@Composable
internal fun CumpusRoundBackButton(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val glassEnabled = LocalLiquidGlassEnabled.current
    val interaction = remember { MutableInteractionSource() }
    val surface = MaterialTheme.colorScheme.surface
    Box(
        modifier = modifier
            .size(46.dp)
            .shadow(
                elevation = 4.dp,
                shape = CircleShape,
                clip = false,
                ambientColor = Color.Black.copy(alpha = 0.08f),
                spotColor = Color.White.copy(alpha = 0.12f),
            )
            .clip(CircleShape)
            .background(
                if (glassEnabled) {
                    Brush.verticalGradient(
                        listOf(
                            surface.copy(alpha = 0.9f),
                            surface.copy(alpha = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) 0.58f else 0.7f),
                        )
                    )
                } else {
                    Brush.verticalGradient(listOf(surface, surface))
                }
            )
            .border(
                width = 1.dp,
                color = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) {
                    Color.White.copy(alpha = 0.16f)
                } else {
                    Color.White.copy(alpha = 0.62f)
                },
                shape = CircleShape,
            )
            .semantics {
                contentDescription = "返回"
                role = Role.Button
            }
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = onBack,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "←",
            fontSize = 24.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
internal fun LiquidGlassHeader(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LiquidGlassPanel(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            CumpusRoundBackButton(onBack = onBack)
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                Text(
                    subtitle,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                )
            }
        }
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
    onFinished: () -> Unit,
) {
    val reveal = remember { Animatable(0f) }
    val fade = remember { Animatable(1f) }
    val targetColor = if (spec.targetDark) Color.Black else Color(0xFFF5F7F5)
    val accent = MaterialTheme.colorScheme.primary

    LaunchedEffect(spec.id) {
        if (!ValueAnimator.areAnimatorsEnabled()) {
            onFinished()
            return@LaunchedEffect
        }
        reveal.animateTo(
            targetValue = 1f,
            animationSpec = tween(360, easing = FastOutSlowInEasing),
        )
        fade.animateTo(
            targetValue = 0f,
            animationSpec = tween(160, easing = FastOutSlowInEasing),
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
            alpha = 0.18f * fade.value,
        )
    }
}
