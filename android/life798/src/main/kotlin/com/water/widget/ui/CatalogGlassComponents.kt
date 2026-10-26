package com.water.widget.ui

import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button as MaterialButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonElevation
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CardElevation
import androidx.compose.material3.FilledTonalButton as MaterialFilledTonalButton
import androidx.compose.material3.IconButton as MaterialIconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton as MaterialOutlinedButton
import androidx.compose.material3.RadioButton as MaterialRadioButton
import androidx.compose.material3.Switch as MaterialSwitch
import androidx.compose.material3.TextButton as MaterialTextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.tanh

internal val LocalHuishengGlassBackdrop = staticCompositionLocalOf<LayerBackdrop?> { null }
internal val LocalHuishengGlassEnabled = staticCompositionLocalOf { true }

@Composable
internal fun HuishengGlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    colors: CardColors = CardDefaults.cardColors(),
    elevation: CardElevation = CardDefaults.cardElevation(),
    border: BorderStroke? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    HuishengGlassSurfaceContainer(
        modifier = modifier,
        shape = shape,
        color = colors.containerColor,
        contentColor = colors.contentColor,
        border = border,
        elevated = true,
        onClick = null,
        enabled = true,
        suppliedInteractionSource = null,
    ) {
        Column(Modifier.fillMaxWidth(), content = content)
    }
}

@Composable
internal fun HuishengGlassCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(24.dp),
    colors: CardColors = CardDefaults.cardColors(),
    elevation: CardElevation = CardDefaults.cardElevation(),
    border: BorderStroke? = null,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    HuishengGlassSurfaceContainer(
        modifier = modifier,
        shape = shape,
        color = colors.containerColor,
        contentColor = colors.contentColor,
        border = border,
        elevated = true,
        onClick = onClick,
        enabled = enabled,
        suppliedInteractionSource = interactionSource,
    ) {
        Column(Modifier.fillMaxWidth(), content = content)
    }
}

@Composable
internal fun HuishengGlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    color: Color = MaterialTheme.colorScheme.surface,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    tonalElevation: androidx.compose.ui.unit.Dp = 0.dp,
    shadowElevation: androidx.compose.ui.unit.Dp = 0.dp,
    border: BorderStroke? = null,
    content: @Composable () -> Unit,
) {
    HuishengGlassSurfaceContainer(
        modifier = modifier,
        shape = shape,
        color = color,
        contentColor = contentColor,
        border = border,
        elevated = shadowElevation > 0.dp,
        onClick = null,
        enabled = true,
        suppliedInteractionSource = null,
        content = content,
    )
}

@Composable
internal fun HuishengGlassSurface(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(24.dp),
    color: Color = MaterialTheme.colorScheme.surface,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    tonalElevation: androidx.compose.ui.unit.Dp = 0.dp,
    shadowElevation: androidx.compose.ui.unit.Dp = 0.dp,
    border: BorderStroke? = null,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable () -> Unit,
) {
    HuishengGlassSurfaceContainer(
        modifier = modifier,
        shape = shape,
        color = color,
        contentColor = contentColor,
        border = border,
        elevated = shadowElevation > 0.dp,
        onClick = onClick,
        enabled = enabled,
        suppliedInteractionSource = interactionSource,
        content = content,
    )
}

@Composable
private fun HuishengGlassSurfaceContainer(
    modifier: Modifier,
    shape: Shape,
    color: Color,
    contentColor: Color,
    border: BorderStroke?,
    elevated: Boolean,
    onClick: (() -> Unit)?,
    enabled: Boolean,
    suppliedInteractionSource: MutableInteractionSource?,
    content: @Composable () -> Unit,
) {
    val backdrop = LocalHuishengGlassBackdrop.current
    val glassEnabled = LocalHuishengGlassEnabled.current
    val useNativeGlass = glassEnabled &&
        backdrop != null &&
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val backgroundColor = MaterialTheme.colorScheme.background
    val interaction = suppliedInteractionSource ?: remember { MutableInteractionSource() }
    val catalogInteraction = rememberCatalogGlassInteraction()
    val clickModifier = if (onClick != null) {
        Modifier
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            )
            .then(catalogInteraction.gestureModifier)
    } else {
        Modifier
    }
    val surfaceModifier = when {
        useNativeGlass -> Modifier.drawBackdrop(
            backdrop = backdrop!!,
            shape = { shape },
            effects = {
                vibrancy()
                blur(5.dp.toPx())
                lens(
                    refractionHeight = 14.dp.toPx(),
                    refractionAmount = 24.dp.toPx(),
                    depthEffect = true,
                    chromaticAberration = true,
                )
            },
            highlight = { Highlight.Ambient },
            shadow = {
                Shadow.Default.copy(
                    radius = if (elevated) 14.dp else 9.dp,
                    color = Color.Black.copy(alpha = if (elevated) 0.16f else 0.1f),
                )
            },
            layerBlock = {
                catalogPressTransform(
                    catalogInteraction,
                    1f + 2.dp.toPx() / size.height.coerceAtLeast(1f),
                )
            },
            onDrawSurface = {
                drawRect(
                    Brush.verticalGradient(
                        listOf(
                            color.copy(alpha = 0.34f),
                            lerp(color, backgroundColor, 0.4f)
                                .copy(alpha = 0.2f),
                        )
                    )
                )
                drawRect(
                    Brush.radialGradient(
                        listOf(Color.White.copy(alpha = 0.14f), Color.Transparent),
                        center = Offset(size.width * 0.16f, 0f),
                        radius = size.maxDimension * 0.82f,
                    )
                )
            },
        )

        glassEnabled -> Modifier
            .shadow(
                elevation = if (elevated) 14.dp else 8.dp,
                shape = shape,
                clip = false,
                ambientColor = Color.Black.copy(alpha = 0.14f),
                spotColor = color.copy(alpha = 0.18f),
            )
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(
                        color.copy(alpha = 0.9f),
                        lerp(color, backgroundColor, 0.34f)
                            .copy(alpha = 0.72f),
                    )
                )
            )
            .border(1.dp, Color.White.copy(alpha = 0.2f), shape)

        else -> Modifier
            .clip(shape)
            .background(color)
    }
    Box(
        modifier
            .then(surfaceModifier)
            .then(clickModifier)
            .then(catalogInteraction.clippedModifier(shape))
            .then(catalogInteraction.modifier)
    ) {
        border?.let {
            Box(
                Modifier
                    .matchParentSize()
                    .border(it, shape)
            )
        }
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            content()
        }
    }
}

/**
 * Gives standalone Huisheng activities the same backdrop used by the embedded
 * STmall surface. The ambient layer is captured once and remains static while
 * controls above it lens and blur the captured colors.
 */
@Composable
internal fun HuishengLiquidGlassHost(
    glassEnabled: Boolean,
    content: @Composable () -> Unit,
) {
    val backdrop = rememberLayerBackdrop()
    CompositionLocalProvider(
        LocalHuishengGlassBackdrop provides backdrop,
        LocalHuishengGlassEnabled provides glassEnabled,
    ) {
        Box(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .then(if (glassEnabled) Modifier.layerBackdrop(backdrop) else Modifier)
            ) {
                if (glassEnabled) {
                    val primary = MaterialTheme.colorScheme.primary
                    val secondary = MaterialTheme.colorScheme.secondary
                    val tertiary = MaterialTheme.colorScheme.tertiary
                    Canvas(Modifier.fillMaxSize()) {
                        drawCircle(
                            brush = Brush.radialGradient(
                                listOf(primary.copy(alpha = 0.18f), Color.Transparent),
                                center = Offset(size.width * 0.08f, size.height * 0.16f),
                                radius = size.width * 0.82f,
                            ),
                            center = Offset(size.width * 0.08f, size.height * 0.16f),
                            radius = size.width * 0.82f,
                        )
                        drawCircle(
                            brush = Brush.radialGradient(
                                listOf(secondary.copy(alpha = 0.12f), Color.Transparent),
                                center = Offset(size.width * 0.94f, size.height * 0.58f),
                                radius = size.width * 0.7f,
                            ),
                            center = Offset(size.width * 0.94f, size.height * 0.58f),
                            radius = size.width * 0.7f,
                        )
                        drawCircle(
                            brush = Brush.radialGradient(
                                listOf(tertiary.copy(alpha = 0.1f), Color.Transparent),
                                center = Offset(size.width * 0.24f, size.height * 0.96f),
                                radius = size.width * 0.66f,
                            ),
                            center = Offset(size.width * 0.24f, size.height * 0.96f),
                            radius = size.width * 0.66f,
                        )
                    }
                }
            }
            content()
        }
    }
}

@Stable
private class CatalogGlassInteraction(
    private val scope: CoroutineScope,
) {
    private val pressProgressAnimation = Animatable(0f, 0.001f)
    private val pointerAnimation = Animatable(
        Offset.Zero,
        Offset.VectorConverter,
        Offset.VisibilityThreshold,
    )
    private val pressSpec = spring<Float>(0.5f, 300f, 0.001f)
    private val positionSpec = spring<Offset>(0.5f, 300f, Offset.VisibilityThreshold)
    private var start = Offset.Zero

    val pressProgress: Float get() = pressProgressAnimation.value
    val pointerOffset: Offset get() = pointerAnimation.value - start

    val modifier: Modifier = Modifier.drawWithContent {
        val progress = pressProgressAnimation.value
        if (progress > 0f) {
            val center = pointerAnimation.value
            drawRect(
                Color.White.copy(alpha = 0.075f * progress),
                blendMode = BlendMode.Plus,
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.16f * progress),
                        Color.Transparent,
                    ),
                    center = center,
                    radius = size.minDimension * 1.15f,
                ),
                radius = size.minDimension * 1.15f,
                center = center,
                blendMode = BlendMode.Plus,
            )
        }
        drawContent()
    }

    fun clippedModifier(shape: Shape): Modifier =
        Modifier.clip(shape).then(modifier)

    val gestureModifier: Modifier = Modifier.pointerInput(scope) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            start = down.position
            scope.launch {
                launch { pressProgressAnimation.animateTo(1f, pressSpec) }
                launch { pointerAnimation.snapTo(start) }
            }
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                scope.launch { pointerAnimation.snapTo(change.position) }
                if (change.changedToUpIgnoreConsumed()) break
            }
            scope.launch {
                launch { pressProgressAnimation.animateTo(0f, pressSpec) }
                launch { pointerAnimation.animateTo(start, positionSpec) }
            }
        }
    }
}

@Composable
private fun rememberCatalogGlassInteraction(): CatalogGlassInteraction {
    val scope = rememberCoroutineScope()
    return remember(scope) { CatalogGlassInteraction(scope) }
}

private fun GraphicsLayerScope.catalogPressTransform(
    interaction: CatalogGlassInteraction,
    pressedScale: Float = 1f,
) {
    val progress = interaction.pressProgress
    val scale = 1f + (pressedScale - 1f) * progress
    val maxOffset = size.minDimension
    val offset = interaction.pointerOffset
    translationX = maxOffset * tanh(0.028f * offset.x / maxOffset)
    translationY = maxOffset * tanh(0.028f * offset.y / maxOffset)
    val angle = atan2(offset.y, offset.x)
    val dragScale = abs(pressedScale - 1f)
    scaleX = scale +
        dragScale * abs(cos(angle) * offset.x / size.maxDimension) *
        (size.width / size.height).coerceAtMost(1f)
    scaleY = scale +
        dragScale * abs(sin(angle) * offset.y / size.maxDimension) *
        (size.height / size.width).coerceAtMost(1f)
}

private val CatalogButtonShape = RoundedCornerShape(percent = 50)
private val CatalogButtonPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp)

@Composable
internal fun CatalogLiquidButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = CatalogButtonShape,
    elevation: ButtonElevation? = ButtonDefaults.buttonElevation(),
    border: BorderStroke? = null,
    contentPadding: PaddingValues = CatalogButtonPadding,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
) {
    if (!glassControlsAvailable()) {
        MaterialButton(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            shape = shape,
            elevation = null,
            border = border,
            contentPadding = contentPadding,
            interactionSource = interactionSource,
            content = content,
        )
        return
    }
    CatalogGlassButtonSurface(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        outlined = false,
        flat = false,
        contentPadding = contentPadding,
        suppliedInteractionSource = interactionSource,
        content = content,
    )
}

@Composable
internal fun CatalogLiquidOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = CatalogButtonShape,
    elevation: ButtonElevation? = null,
    border: BorderStroke? = BorderStroke(
        1.dp,
        MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
    ),
    contentPadding: PaddingValues = CatalogButtonPadding,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
) {
    if (!glassControlsAvailable()) {
        MaterialOutlinedButton(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            shape = shape,
            elevation = elevation,
            border = border,
            contentPadding = contentPadding,
            interactionSource = interactionSource,
            content = content,
        )
        return
    }
    CatalogGlassButtonSurface(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.primary,
        outlined = true,
        flat = false,
        contentPadding = contentPadding,
        suppliedInteractionSource = interactionSource,
        content = content,
    )
}

@Composable
internal fun CatalogLiquidTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = CatalogButtonShape,
    elevation: ButtonElevation? = null,
    border: BorderStroke? = null,
    contentPadding: PaddingValues = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
) {
    if (!glassControlsAvailable()) {
        MaterialTextButton(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            shape = shape,
            elevation = elevation,
            border = border,
            contentPadding = contentPadding,
            interactionSource = interactionSource,
            content = content,
        )
        return
    }
    CatalogGlassButtonSurface(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.primary,
        outlined = false,
        flat = true,
        contentPadding = contentPadding,
        suppliedInteractionSource = interactionSource,
        content = content,
    )
}

@Composable
internal fun CatalogLiquidFilledTonalButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = CatalogButtonShape,
    elevation: ButtonElevation? = ButtonDefaults.filledTonalButtonElevation(),
    border: BorderStroke? = null,
    contentPadding: PaddingValues = CatalogButtonPadding,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
) {
    if (!glassControlsAvailable()) {
        MaterialFilledTonalButton(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            shape = shape,
            elevation = null,
            border = border,
            contentPadding = contentPadding,
            interactionSource = interactionSource,
            content = content,
        )
        return
    }
    CatalogGlassButtonSurface(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        outlined = false,
        flat = false,
        contentPadding = contentPadding,
        suppliedInteractionSource = interactionSource,
        content = content,
    )
}

@Composable
internal fun CatalogLiquidIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable () -> Unit,
) {
    val backdrop = LocalHuishengGlassBackdrop.current
    if (!LocalHuishengGlassEnabled.current ||
        backdrop == null ||
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S
    ) {
        MaterialIconButton(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            interactionSource = interactionSource,
            content = content,
        )
        return
    }

    val interaction = interactionSource ?: remember { MutableInteractionSource() }
    val catalogInteraction = rememberCatalogGlassInteraction()
    val view = LocalView.current
    Box(
        modifier = modifier
            .size(46.dp)
            .drawBackdrop(
                backdrop = backdrop,
                shape = { RoundedCornerShape(percent = 50) },
                effects = {
                    vibrancy()
                    blur(2.dp.toPx())
                    lens(
                        refractionHeight = 9.dp.toPx(),
                        refractionAmount = 18.dp.toPx(),
                        depthEffect = true,
                        chromaticAberration = true,
                    )
                },
                highlight = { Highlight.Ambient.copy(alpha = if (enabled) 0.9f else 0.4f) },
                shadow = {
                    Shadow(radius = 6.dp, color = Color.Black.copy(alpha = 0.1f))
                },
                layerBlock = {
                    catalogPressTransform(
                        catalogInteraction,
                        1f + 4.dp.toPx() / size.height.coerceAtLeast(1f),
                    )
                },
                onDrawSurface = {
                    drawRect(Color.White.copy(alpha = if (enabled) 0.075f else 0.025f))
                },
            )
            .then(catalogInteraction.clippedModifier(RoundedCornerShape(percent = 50)))
            .then(catalogInteraction.gestureModifier)
            .clickable(
                enabled = enabled,
                role = Role.Button,
                interactionSource = interaction,
                indication = null,
            ) {
                view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(
            LocalContentColor provides MaterialTheme.colorScheme.onSurface.copy(
                alpha = if (enabled) 1f else 0.55f
            )
        ) {
            content()
        }
    }
}

@Composable
internal fun CatalogLiquidFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    selectedContainerColor: Color? = null,
) {
    val backdrop = LocalHuishengGlassBackdrop.current
    if (!LocalHuishengGlassEnabled.current ||
        backdrop == null ||
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S
    ) {
        androidx.compose.material3.FilterChip(
            selected = selected,
            onClick = onClick,
            label = label,
            modifier = modifier,
            enabled = enabled,
            leadingIcon = leadingIcon,
            trailingIcon = trailingIcon,
        )
        return
    }

    val interaction = remember { MutableInteractionSource() }
    val catalogInteraction = rememberCatalogGlassInteraction()
    val view = LocalView.current
    val shape = RoundedCornerShape(percent = 50)
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val resolvedSelectedContainer = selectedContainerColor ?: MaterialTheme.colorScheme.primary
    val unselectedContainer = MaterialTheme.colorScheme.surface.copy(alpha = 0.12f)
    Row(
        modifier = modifier
            .heightIn(min = 40.dp)
            .drawBackdrop(
                backdrop = backdrop,
                shape = { shape },
                effects = {
                    vibrancy()
                    blur(2.dp.toPx())
                    lens(
                        refractionHeight = 8.dp.toPx(),
                        refractionAmount = 16.dp.toPx(),
                        depthEffect = true,
                        chromaticAberration = true,
                    )
                },
                highlight = { Highlight.Ambient.copy(alpha = if (enabled) 0.85f else 0.35f) },
                shadow = {
                    Shadow(radius = 5.dp, color = Color.Black.copy(alpha = 0.08f))
                },
                layerBlock = {
                    catalogPressTransform(
                        catalogInteraction,
                        1f + 4.dp.toPx() / size.height.coerceAtLeast(1f),
                    )
                },
                onDrawSurface = {
                    drawRect(
                        if (selected) {
                            resolvedSelectedContainer.copy(alpha = 0.72f)
                        } else {
                            unselectedContainer
                        }
                    )
                    drawRect(
                        Brush.verticalGradient(
                            listOf(Color.White.copy(alpha = 0.12f), Color.Transparent),
                        )
                    )
                },
            )
            .then(catalogInteraction.clippedModifier(shape))
            .then(catalogInteraction.gestureModifier)
            .selectable(
                selected = selected,
                enabled = enabled,
                role = Role.Checkbox,
                interactionSource = interaction,
                indication = null,
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                    onClick()
                },
            )
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CompositionLocalProvider(
            LocalContentColor provides contentColor.copy(alpha = if (enabled) 1f else 0.55f)
        ) {
            leadingIcon?.invoke()
            label()
            trailingIcon?.invoke()
        }
    }
}

@Composable
internal fun CatalogLiquidRadioButton(
    selected: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val backdrop = LocalHuishengGlassBackdrop.current
    if (!LocalHuishengGlassEnabled.current ||
        backdrop == null ||
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S
    ) {
        MaterialRadioButton(
            selected = selected,
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
        )
        return
    }

    val interaction = remember { MutableInteractionSource() }
    val catalogInteraction = rememberCatalogGlassInteraction()
    val view = LocalView.current
    val accent = MaterialTheme.colorScheme.primary
    val outline = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.34f)
    val surfaceColor = MaterialTheme.colorScheme.surface
    val clickModifier = if (onClick == null) {
        Modifier.semantics {
            role = Role.RadioButton
            this.selected = selected
        }
    } else {
        Modifier.selectable(
            selected = selected,
            enabled = enabled,
            role = Role.RadioButton,
            interactionSource = interaction,
            indication = null,
        ) {
            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            onClick()
        }
    }

    Box(
        modifier = modifier
            .size(40.dp)
            .drawBackdrop(
                backdrop = backdrop,
                shape = { RoundedCornerShape(percent = 50) },
                effects = {
                    vibrancy()
                    blur(1.5.dp.toPx())
                    lens(
                        refractionHeight = 7.dp.toPx(),
                        refractionAmount = 14.dp.toPx(),
                        depthEffect = true,
                        chromaticAberration = true,
                    )
                },
                highlight = { Highlight.Ambient.copy(alpha = if (enabled) 0.82f else 0.35f) },
                shadow = {
                    Shadow(radius = 4.dp, color = Color.Black.copy(alpha = 0.08f))
                },
                layerBlock = {
                    catalogPressTransform(
                        catalogInteraction,
                        1f + 3.dp.toPx() / size.height.coerceAtLeast(1f),
                    )
                },
                onDrawSurface = {
                    drawRect(
                        surfaceColor.copy(
                            alpha = if (enabled) 0.18f else 0.08f,
                        )
                    )
                },
            )
            .then(catalogInteraction.clippedModifier(RoundedCornerShape(percent = 50)))
            .then(catalogInteraction.gestureModifier)
            .then(clickModifier),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(20.dp)
                .clip(RoundedCornerShape(percent = 50))
                .border(
                    width = if (selected) 2.dp else 1.dp,
                    color = if (selected) accent else outline,
                    shape = RoundedCornerShape(percent = 50),
                )
        )
        if (selected) {
            Box(
                Modifier
                    .size(10.dp)
                    .clip(RoundedCornerShape(percent = 50))
                    .background(accent)
            )
        }
    }
}

@Composable
private fun glassControlsAvailable(): Boolean {
    val backdrop = LocalHuishengGlassBackdrop.current
    return LocalHuishengGlassEnabled.current &&
        backdrop != null &&
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
}

@Composable
private fun CatalogGlassButtonSurface(
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    containerColor: Color,
    contentColor: Color,
    outlined: Boolean,
    flat: Boolean,
    contentPadding: PaddingValues,
    suppliedInteractionSource: MutableInteractionSource?,
    content: @Composable RowScope.() -> Unit,
) {
    val interaction = suppliedInteractionSource ?: remember { MutableInteractionSource() }
    val catalogInteraction = rememberCatalogGlassInteraction()
    val backdrop = LocalHuishengGlassBackdrop.current!!
    val view = LocalView.current
    val glassShape = RoundedCornerShape(percent = 50)
    val borderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)

    Row(
        modifier = modifier
            .defaultMinSize(minHeight = 48.dp)
            .drawBackdrop(
                backdrop = backdrop,
                shape = { glassShape },
                effects = {
                    vibrancy()
                    blur((if (flat) 1.5f else 2f).dp.toPx())
                    lens(
                        refractionHeight = (if (flat) 8f else 12f).dp.toPx(),
                        refractionAmount = (if (flat) 16f else 24f).dp.toPx(),
                        depthEffect = true,
                        chromaticAberration = true,
                    )
                },
                highlight = {
                    Highlight.Ambient.copy(alpha = if (enabled) 0.9f else 0.45f)
                },
                shadow = if (flat) {
                    null
                } else {
                    {
                        Shadow(
                            radius = 5.dp,
                            color = Color.Black.copy(alpha = 0.09f),
                        )
                    }
                },
                layerBlock = {
                    catalogPressTransform(
                        catalogInteraction,
                        1f + 4.dp.toPx() / size.height.coerceAtLeast(1f),
                    )
                },
                onDrawSurface = {
                    if (flat) {
                        drawRect(Color.White.copy(alpha = 0.055f))
                    } else {
                        drawRect(containerColor.copy(alpha = if (outlined) 0.12f else 0.86f))
                        drawRect(
                            Brush.verticalGradient(
                                listOf(Color.White.copy(alpha = 0.13f), Color.Transparent),
                            )
                        )
                        if (outlined) {
                            drawRoundRect(
                                color = borderColor,
                                style = androidx.compose.ui.graphics.drawscope.Stroke(
                                    width = 1.dp.toPx(),
                                ),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(
                                    size.height / 2f,
                                    size.height / 2f,
                                ),
                            )
                        }
                    }
                },
            )
            .then(catalogInteraction.clippedModifier(glassShape))
            .then(catalogInteraction.gestureModifier)
            .clickable(
                enabled = enabled,
                role = Role.Button,
                interactionSource = interaction,
                indication = null,
            ) {
                view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                onClick()
            }
            .padding(contentPadding),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompositionLocalProvider(
            LocalContentColor provides contentColor.copy(alpha = if (enabled) 1f else 0.55f)
        ) {
            content()
        }
    }
}

@Composable
internal fun CatalogLiquidSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val backdrop = LocalHuishengGlassBackdrop.current
    if (!LocalHuishengGlassEnabled.current ||
        backdrop == null ||
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S
    ) {
        MaterialSwitch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = modifier,
        )
        return
    }

    val shape = RoundedCornerShape(percent = 50)
    val trackBackdrop = rememberLayerBackdrop()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val tapPulse = remember { Animatable(0f) }
    var targetFraction by remember { mutableFloatStateOf(if (checked) 1f else 0f) }
    var dragging by remember { mutableStateOf(false) }
    var suppressClicksUntil by remember { mutableLongStateOf(0L) }
    LaunchedEffect(checked, dragging) {
        if (!dragging) targetFraction = if (checked) 1f else 0f
    }
    val fraction by animateFloatAsState(
        targetValue = targetFraction,
        animationSpec = if (dragging) snap() else spring(0.74f, 560f),
        label = "catalog-switch-fraction",
    )
    val heldProgress by animateFloatAsState(
        targetValue = if (dragging || pressed) 1f else 0f,
        animationSpec = spring(0.64f, 620f),
        label = "catalog-switch-press-progress",
    )
    val pressProgress = max(heldProgress, tapPulse.value)
    val pressScale by animateFloatAsState(
        targetValue = 1f + pressProgress * 0.5f,
        animationSpec = spring(0.62f, 450f),
        label = "catalog-switch-press-scale",
    )
    val trackColor = lerp(
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
        Color(0xFF34C759),
        fraction,
    )
    val combinedBackdrop = rememberCombinedBackdrop(
        backdrop,
        rememberBackdrop(trackBackdrop) { drawBackdrop ->
            scale(
                scaleX = (2f / 3f) + (0.75f - 2f / 3f) * pressProgress,
                scaleY = 0.75f * pressProgress,
            ) {
                drawBackdrop()
            }
        },
    )

    Box(
        modifier
            .size(width = 64.dp, height = 28.dp)
            .semantics { role = Role.Switch }
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Switch,
            ) {
                if (System.currentTimeMillis() >= suppressClicksUntil) {
                    view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                    onCheckedChange(!checked)
                    scope.launch {
                        tapPulse.snapTo(1f)
                        tapPulse.animateTo(
                            targetValue = 0f,
                            animationSpec = spring(0.58f, 250f),
                        )
                    }
                }
            }
            .pointerInput(checked) {
                val dragWidth = 20.dp.toPx()
                var crossedMidpoint = false
                detectHorizontalDragGestures(
                    onDragStart = {
                        dragging = true
                        crossedMidpoint = false
                        suppressClicksUntil = Long.MAX_VALUE
                    },
                    onHorizontalDrag = { change, amount ->
                        change.consume()
                        targetFraction = (targetFraction + amount / dragWidth).coerceIn(0f, 1f)
                        val crossed = targetFraction >= 0.5f
                        if (crossed != crossedMidpoint) {
                            crossedMidpoint = crossed
                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        }
                    },
                    onDragCancel = {
                        targetFraction = if (checked) 1f else 0f
                        dragging = false
                        suppressClicksUntil = System.currentTimeMillis() + 250L
                    },
                    onDragEnd = {
                        val next = targetFraction.roundToInt().coerceIn(0, 1) == 1
                        targetFraction = if (next) 1f else 0f
                        dragging = false
                        suppressClicksUntil = System.currentTimeMillis() + 250L
                        if (next != checked) {
                            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                            onCheckedChange(next)
                        }
                    },
                )
            },
    ) {
        Box(
            Modifier
                .layerBackdrop(trackBackdrop)
                .clip(shape)
                .background(trackColor)
                .size(width = 64.dp, height = 28.dp)
        )
        Box(
            Modifier
                .offset(x = 2.dp + 20.dp * fraction, y = 2.dp)
                .drawBackdrop(
                    backdrop = combinedBackdrop,
                    shape = { shape },
                    effects = {
                        blur(8.dp.toPx() * (1f - pressProgress))
                        lens(
                            5.dp.toPx() * pressProgress,
                            10.dp.toPx() * pressProgress,
                            chromaticAberration = true,
                        )
                    },
                    highlight = {
                        Highlight.Ambient.copy(
                            width = Highlight.Ambient.width / 1.5f,
                            blurRadius = Highlight.Ambient.blurRadius / 1.5f,
                            alpha = pressProgress,
                        )
                    },
                    shadow = {
                        Shadow(radius = 4.dp, color = Color.Black.copy(alpha = 0.05f))
                    },
                    innerShadow = {
                        InnerShadow(radius = 4.dp * pressProgress, alpha = pressProgress)
                    },
                    layerBlock = {
                        scaleX = pressScale
                        scaleY = pressScale
                    },
                    onDrawSurface = {
                        drawRect(Color.White.copy(alpha = 1f - pressProgress))
                    },
                )
                .size(width = 40.dp, height = 24.dp)
        )
    }
}
