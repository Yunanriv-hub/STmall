package com.fktm.app.ui

import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button as MaterialButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonElevation
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.IconButton as MaterialIconButton
import androidx.compose.material3.OutlinedButton as MaterialOutlinedButton
import androidx.compose.material3.TextButton as MaterialTextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow

private val CatalogButtonShape = RoundedCornerShape(percent = 50)
private val CatalogButtonPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp)

/**
 * Catalog-style capsule buttons. The plain mode delegates to Material 3 so the
 * liquid-glass switch remains a true visual and behavioral on/off boundary.
 */
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
    val backdrop = LocalLiquidGlassBackdrop.current
    val glassEnabled = LocalLiquidGlassEnabled.current
    if (!glassEnabled || backdrop == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
        MaterialButton(
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
        shape = shape,
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
    val backdrop = LocalLiquidGlassBackdrop.current
    val glassEnabled = LocalLiquidGlassEnabled.current
    if (!glassEnabled || backdrop == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
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
        shape = shape,
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
    val backdrop = LocalLiquidGlassBackdrop.current
    val glassEnabled = LocalLiquidGlassEnabled.current
    if (!glassEnabled || backdrop == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
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
        shape = shape,
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
internal fun CatalogLiquidIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable () -> Unit,
) {
    val backdrop = LocalLiquidGlassBackdrop.current
    val glassEnabled = LocalLiquidGlassEnabled.current
    if (!glassEnabled || backdrop == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
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
                shape = { androidx.compose.foundation.shape.CircleShape },
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
            .then(catalogInteraction.modifier)
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
    val backdrop = LocalLiquidGlassBackdrop.current
    val glassEnabled = LocalLiquidGlassEnabled.current
    if (!glassEnabled || backdrop == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
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
            .then(catalogInteraction.modifier)
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
private fun CatalogGlassButtonSurface(
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    shape: Shape,
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
    val backdrop = LocalLiquidGlassBackdrop.current!!
    val view = LocalView.current
    val glassShape = RoundedCornerShape(percent = 50)
    val borderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
    val contentAlpha = if (enabled) 1f else 0.55f

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
                            androidx.compose.ui.graphics.Brush.verticalGradient(
                                listOf(
                                    Color.White.copy(alpha = 0.13f),
                                    Color.Transparent,
                                ),
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
            .then(catalogInteraction.modifier)
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
        CompositionLocalProvider(LocalContentColor provides contentColor.copy(alpha = contentAlpha)) {
            content()
        }
    }
}
