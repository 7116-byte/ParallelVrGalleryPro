package com.local.parallelvrgallerypro

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Real-time GPU backdrop capture holder using Compose 1.7 GraphicsLayer + Android 12+ RenderEffect.
 * Records the live scrollable content into a hardware RenderNode so sibling glass overlays
 * (top progressive blur, circular buttons, popovers, bottom liquid glass dock) sample and blur
 * the exact pixels directly behind them with zero CPU bitmap copies.
 */
@Stable
internal class StudioBackdropState(val layer: GraphicsLayer) {
    var originInRoot by mutableStateOf(Offset.Zero)
    var hasRecorded by mutableStateOf(false)
}

internal val LocalStudioBackdrop = compositionLocalOf<StudioBackdropState?> { null }

@Composable
internal fun rememberStudioBackdrop(): StudioBackdropState {
    val layer = rememberGraphicsLayer()
    return remember(layer) { StudioBackdropState(layer) }
}

/** Captures the live visual output of the wrapped background content into [backdrop]. */
internal fun Modifier.studioBackdropSource(backdrop: StudioBackdropState): Modifier = this
    .onGloballyPositioned { backdrop.originInRoot = it.positionInRoot() }
    .drawWithContent {
        if (size.width > 0f && size.height > 0f) {
            backdrop.layer.record {
                this@drawWithContent.drawContent()
            }
            if (!backdrop.hasRecorded) backdrop.hasRecorded = true
            drawLayer(backdrop.layer)
        } else {
            drawContent()
        }
    }

/**
 * Draws the live GPU-blurred portion of [LocalStudioBackdrop] directly behind this composable,
 * clipped cleanly to [shape]. On API 31+ this executes real hardware RenderEffect Gaussian blur.
 */
@Composable
internal fun BackdropBlurLayer(
    shape: Shape,
    blurRadius: Dp,
    modifier: Modifier = Modifier,
) {
    val backdrop = LocalStudioBackdrop.current ?: return
    if (Build.VERSION.SDK_INT < 31 || !backdrop.hasRecorded) return
    var myOrigin by remember { mutableStateOf(Offset.Zero) }
    Box(
        modifier = modifier
            .onGloballyPositioned { myOrigin = it.positionInRoot() }
            .clip(shape)
            .graphicsLayer {
                val radiusPx = blurRadius.toPx()
                if (radiusPx > 0.5f) {
                    renderEffect = BlurEffect(radiusPx, radiusPx, TileMode.Clamp)
                }
                clip = true
                this.shape = shape
            }
            .drawBehind {
                if (backdrop.layer.size.width > 0 && backdrop.layer.size.height > 0) {
                    val dx = backdrop.originInRoot.x - myOrigin.x
                    val dy = backdrop.originInRoot.y - myOrigin.y
                    translate(left = dx, top = dy) {
                        drawLayer(backdrop.layer)
                    }
                }
            },
    )
}

/**
 * Real-time Liquid Glass container:
 * GPU backdrop blur + soft frosted tint + delicate top-to-bottom specular sheen + crisp white rim.
 */
@Composable
internal fun RealLiquidGlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(28.dp),
    blurRadius: Dp = 24.dp,
    darkHud: Boolean = false,
    tintColor: Color? = null,
    borderWidth: Dp = 1.dp,
    shadowElevation: Dp = 12.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    val apple = AppleTheme.colors
    val hasHardwareBlur = Build.VERSION.SDK_INT >= 31 && LocalStudioBackdrop.current != null
    val resolvedTint = tintColor ?: when {
        darkHud -> if (hasHardwareBlur) Color(0x88141418) else Color(0xCC18181B)
        apple.isDark -> if (hasHardwareBlur) Color(0x781E1E24) else Color(0xDC1E1E22)
        else -> if (hasHardwareBlur) Color(0x94F4F5F8) else Color(0xEAF7F7FA)
    }
    val sheenTop = when {
        darkHud -> Color(0x28FFFFFF)
        apple.isDark -> Color(0x24FFFFFF)
        else -> Color(0x70FFFFFF)
    }
    val sheenBottom = when {
        darkHud -> Color(0x06FFFFFF)
        apple.isDark -> Color(0x08FFFFFF)
        else -> Color(0x18FFFFFF)
    }
    val rimTop = when {
        darkHud -> Color(0x52FFFFFF)
        apple.isDark -> Color(0x4CFFFFFF)
        else -> Color(0xF5FFFFFF)
    }
    val rimBottom = when {
        darkHud -> Color(0x18FFFFFF)
        apple.isDark -> Color(0x18FFFFFF)
        else -> Color(0x78FFFFFF)
    }
    val shadowColor = if (darkHud || apple.isDark) Color(0x7A000000) else Color(0x26000000)

    val sheenBrush = remember(sheenTop, sheenBottom) {
        Brush.verticalGradient(listOf(sheenTop, sheenBottom))
    }
    val rimBrush = remember(rimTop, rimBottom) {
        Brush.verticalGradient(listOf(rimTop, rimBottom))
    }

    Box(
        modifier = modifier
            .shadow(
                elevation = shadowElevation,
                shape = shape,
                ambientColor = shadowColor,
                spotColor = shadowColor,
                clip = false,
            )
            .clip(shape),
    ) {
        BackdropBlurLayer(
            shape = shape,
            blurRadius = blurRadius,
            modifier = Modifier.matchParentSize(),
        )
        Box(
            Modifier
                .matchParentSize()
                .background(resolvedTint)
                .background(sheenBrush)
                .border(width = borderWidth, brush = rimBrush, shape = shape),
        )
        content()
    }
}

/**
 * Smaller, clearer, MORE TRANSPARENT Liquid Glass lens pill ("小的更透明"):
 * Uses a gentler blur radius and very light optical tint so real photo colors beneath the dock
 * shine vividly through the active sliding capsule, contrasting against the milkier outer dock.
 */
@Composable
internal fun LiquidGlassClearLens(
    shape: Shape,
    modifier: Modifier = Modifier,
    blurRadius: Dp = 11.dp,
) {
    val apple = AppleTheme.colors
    val hasHardwareBlur = Build.VERSION.SDK_INT >= 31 && LocalStudioBackdrop.current != null
    val clearTint = when {
        apple.isDark -> if (hasHardwareBlur) Color(0x22FFFFFF) else Color(0x36FFFFFF)
        else -> if (hasHardwareBlur) Color(0x34FFFFFF) else Color(0x66FFFFFF)
    }
    val rimBrush = remember(apple.isDark) {
        if (apple.isDark) {
            Brush.verticalGradient(listOf(Color(0x66FFFFFF), Color(0x1CFFFFFF)))
        } else {
            Brush.verticalGradient(listOf(Color(0xEEFFFFFF), Color(0x44FFFFFF), Color(0xB8FFFFFF)))
        }
    }
    val glossBrush = remember(apple.isDark) {
        if (apple.isDark) {
            Brush.verticalGradient(listOf(Color(0x22FFFFFF), Color.Transparent))
        } else {
            Brush.verticalGradient(listOf(Color(0x48FFFFFF), Color.Transparent, Color(0x18FFFFFF)))
        }
    }

    Box(
        modifier = modifier
            .shadow(
                elevation = 4.dp,
                shape = shape,
                ambientColor = Color(0x1C000000),
                spotColor = Color(0x1C000000),
                clip = false,
            )
            .clip(shape),
    ) {
        BackdropBlurLayer(
            shape = shape,
            blurRadius = blurRadius,
            modifier = Modifier.matchParentSize(),
        )
        Box(
            Modifier
                .matchParentSize()
                .background(clearTint)
                .background(glossBrush)
                .border(width = 0.85.dp, brush = rimBrush, shape = shape),
        )
    }
}

/**
 * Progressive top blurred translucent gradient veil (matching Reference Image 5):
 * Real GPU backdrop blur masked smoothly with BlendMode.DstIn so scrolling photos
 * melt progressively from sharp -> softly blurred -> frosted translucent under the status bar.
 */
@Composable
internal fun ProgressiveTopBlurOverlay(
    height: Dp,
    alpha: Float,
    modifier: Modifier = Modifier,
) {
    if (alpha <= 0.01f) return
    val apple = AppleTheme.colors
    val backdrop = LocalStudioBackdrop.current
    val maskBrush = remember {
        Brush.verticalGradient(
            0.0f to Color.Black,
            0.44f to Color.Black.copy(alpha = 0.92f),
            0.74f to Color.Black.copy(alpha = 0.45f),
            1.0f to Color.Transparent,
        )
    }
    val veilColor = if (apple.isDark) Color(0xFF000000) else Color(0xFFF6F7F9)
    val tintBrush = remember(veilColor, apple.isDark) {
        val topAlpha = if (Build.VERSION.SDK_INT >= 31) {
            if (apple.isDark) 0.58f else 0.62f
        } else {
            if (apple.isDark) 0.86f else 0.88f
        }
        val midAlpha = topAlpha * 0.58f
        Brush.verticalGradient(
            0.0f to veilColor.copy(alpha = topAlpha),
            0.50f to veilColor.copy(alpha = midAlpha),
            1.0f to Color.Transparent,
        )
    }

    var myOrigin by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .graphicsLayer { this.alpha = alpha.coerceIn(0f, 1f) },
    ) {
        if (backdrop != null && backdrop.hasRecorded && Build.VERSION.SDK_INT >= 31) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .drawWithContent {
                        drawContent()
                        drawRect(brush = maskBrush, blendMode = BlendMode.DstIn)
                    },
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .onGloballyPositioned { myOrigin = it.positionInRoot() }
                        .graphicsLayer {
                            val r = 20.dp.toPx()
                            renderEffect = BlurEffect(r, r, TileMode.Clamp)
                            clip = true
                        }
                        .drawBehind {
                            if (backdrop.layer.size.width > 0 && backdrop.layer.size.height > 0) {
                                val dx = backdrop.originInRoot.x - myOrigin.x
                                val dy = backdrop.originInRoot.y - myOrigin.y
                                translate(left = dx, top = dy) {
                                    drawLayer(backdrop.layer)
                                }
                            }
                        },
                )
            }
        }
        Box(Modifier.fillMaxSize().background(tintBrush))
    }
}

/**
 * In-window frosted Liquid Glass dropdown popover (matching Reference Images 2 & 3):
 * Renders inside the same window overlay so it genuinely blurs the live photo grid behind it.
 */
@Composable
internal fun AppleGlassPopoverOverlay(
    visible: Boolean,
    onDismiss: () -> Unit,
    menuAlignment: Alignment = Alignment.TopEnd,
    menuModifier: Modifier = Modifier,
    minWidth: Dp = 216.dp,
    maxWidth: Dp = 268.dp,
    transformOrigin: TransformOrigin = TransformOrigin(0.85f, 0f),
    content: @Composable ColumnScope.() -> Unit,
) {
    if (visible) {
        BackHandler(onBack = onDismiss)
    }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMedium)),
        exit = fadeOut(animationSpec = spring(stiffness = Spring.StiffnessHigh)),
        modifier = Modifier.fillMaxSize(),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
        ) {
            AnimatedVisibility(
                visible = visible,
                enter = scaleIn(
                    initialScale = 0.86f,
                    transformOrigin = transformOrigin,
                    animationSpec = spring(dampingRatio = 0.80f, stiffness = 520f),
                ) + fadeIn(),
                exit = scaleOut(
                    targetScale = 0.90f,
                    transformOrigin = transformOrigin,
                    animationSpec = spring(stiffness = 600f),
                ) + fadeOut(),
                modifier = Modifier.align(menuAlignment).then(menuModifier),
            ) {
                RealLiquidGlassSurface(
                    shape = RoundedCornerShape(26.dp),
                    blurRadius = 28.dp,
                    tintColor = if (AppleTheme.colors.isDark) Color(0xB8202024) else Color(0xC6FFFFFF),
                    borderWidth = 1.dp,
                    shadowElevation = 18.dp,
                    modifier = Modifier
                        .widthIn(min = minWidth, max = maxWidth)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {},
                        ),
                ) {
                    Column(Modifier.fillMaxWidth(), content = content)
                }
            }
        }
    }
}
