package com.fktm.app.ui

import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.rememberBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.roundToInt

@Composable
internal fun CatalogLiquidSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val backdrop = LocalLiquidGlassBackdrop.current
    val glassEnabled = LocalLiquidGlassEnabled.current
    if (!glassEnabled) {
        Switch(checked = checked, onCheckedChange = onCheckedChange, modifier = modifier)
        return
    }

    val useNativeGlass = backdrop != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
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
        animationSpec = if (dragging) {
            snap()
        } else {
            spring(dampingRatio = 0.74f, stiffness = 560f)
        },
        label = "catalog-switch-fraction",
    )
    val heldProgress by animateFloatAsState(
        targetValue = if (dragging || pressed) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.64f, stiffness = 620f),
        label = "catalog-switch-press-progress",
    )
    val pressProgress = max(heldProgress, tapPulse.value)
    val pressScale by animateFloatAsState(
        targetValue = 1f + pressProgress * 0.5f,
        animationSpec = spring(dampingRatio = 0.62f, stiffness = 450f),
        label = "catalog-switch-press-scale",
    )
    val trackBrush = lerp(
        LocalTrackColor(),
        Color(0xFF34C759),
        fraction,
    )
    val combinedBackdrop = if (useNativeGlass) {
        rememberCombinedBackdrop(
            backdrop!!,
            rememberBackdrop(trackBackdrop) { drawBackdrop ->
                scale(
                    scaleX = (2f / 3f) + (0.75f - 2f / 3f) * pressProgress,
                    scaleY = 0.75f * pressProgress,
                ) {
                    drawBackdrop()
                }
            },
        )
    } else {
        null
    }

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
                            animationSpec = spring(dampingRatio = 0.58f, stiffness = 250f),
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
                .then(if (useNativeGlass) Modifier.layerBackdrop(trackBackdrop) else Modifier)
                .clip(shape)
                .background(trackBrush)
                .size(width = 64.dp, height = 28.dp)
        )
        val thumbSurface = if (combinedBackdrop != null) {
            Modifier.drawBackdrop(
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
        } else {
            Modifier
                .graphicsLayer {
                    scaleX = pressScale
                    scaleY = pressScale
                }
                .shadow(
                    elevation = 4.dp,
                    shape = shape,
                    clip = false,
                    ambientColor = Color.Black.copy(alpha = 0.08f),
                    spotColor = Color.White.copy(alpha = 0.18f),
                )
                .clip(shape)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = 1f - pressProgress * 0.18f),
                            Color.White.copy(alpha = 0.84f),
                        )
                    )
                )
                .border(
                    width = 1.dp,
                    color = Color.White.copy(alpha = 0.64f),
                    shape = shape,
                )
        }
        Box(
            Modifier
                .offset(x = 2.dp + 20.dp * fraction, y = 2.dp)
                .then(thumbSurface)
                .size(width = 40.dp, height = 24.dp)
        )
    }
}

@Composable
private fun LocalTrackColor(): Color =
    androidx.compose.material3.MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
