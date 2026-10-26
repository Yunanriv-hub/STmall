package com.fktm.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tanh

/**
 * Press and pointer-highlight state adapted from Backdrop Catalog (Apache-2.0).
 */
@Stable
internal class CatalogGlassInteraction(
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
internal fun rememberCatalogGlassInteraction(): CatalogGlassInteraction {
    val scope = rememberCoroutineScope()
    return remember(scope) { CatalogGlassInteraction(scope) }
}

internal fun GraphicsLayerScope.catalogPressTransform(
    interaction: CatalogGlassInteraction,
    pressedScale: Float = 1f,
) {
    val progress = interaction.pressProgress
    val scale = 1f + (pressedScale - 1f) * progress
    val maxOffset = size.minDimension
    val offset = interaction.pointerOffset
    translationX = maxOffset * tanh(0.05f * offset.x / maxOffset)
    translationY = maxOffset * tanh(0.05f * offset.y / maxOffset)
    val angle = atan2(offset.y, offset.x)
    val dragScale = abs(pressedScale - 1f)
    scaleX = scale +
        dragScale * abs(cos(angle) * offset.x / size.maxDimension) *
        (size.width / size.height).coerceAtMost(1f)
    scaleY = scale +
        dragScale * abs(sin(angle) * offset.y / size.maxDimension) *
        (size.height / size.width).coerceAtMost(1f)
}
