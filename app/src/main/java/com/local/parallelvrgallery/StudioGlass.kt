package com.local.parallelvrgallerypro

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.PhotoAlbum
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.PhotoAlbum
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/**
 * Liquid Glass container backed by real GPU backdrop blur when inside a studioBackdropSource.
 */
@Composable
internal fun LiquidGlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(28.dp),
    darkHud: Boolean = false,
    translucentStrong: Boolean = false,
    shadowElevation: Dp = 10.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    val apple = AppleTheme.colors
    val tint = when {
        darkHud -> null
        translucentStrong -> if (apple.isDark) Color(0x881E1E24) else Color(0xA4F4F5F8)
        else -> if (apple.isDark) Color(0x741E1E24) else Color(0x8EF4F5F8)
    }
    RealLiquidGlassSurface(
        modifier = modifier,
        shape = shape,
        blurRadius = 24.dp,
        darkHud = darkHud,
        tintColor = tint,
        borderWidth = 1.dp,
        shadowElevation = shadowElevation,
        content = content,
    )
}

/** Subtle bottom scrim so floating Dock lifts cleanly off busy photography. */
@Composable
internal fun AppleBottomFadeScrim(
    modifier: Modifier = Modifier,
    darkHud: Boolean = false,
) {
    val bg = if (darkHud) Color.Black else AppleTheme.colors.groupedBackground
    val brush = remember(bg) {
        Brush.verticalGradient(
            0.0f to Color.Transparent,
            0.70f to bg.copy(alpha = 0.28f),
            1.0f to bg.copy(alpha = 0.56f),
        )
    }
    Box(modifier.background(brush))
}

/** Hairline Apple separator (0.5.dp). */
@Composable
internal fun AppleDivider(
    modifier: Modifier = Modifier,
    startIndent: Dp = 0.dp,
) {
    HorizontalDivider(
        modifier = modifier.padding(start = startIndent),
        thickness = 0.5.dp,
        color = AppleTheme.colors.separator,
    )
}

/** Authentic iOS / iPadOS toggle switch (Apple Green active track, soft-shadow white circular thumb). */
@Composable
internal fun AppleSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val apple = AppleTheme.colors
    val haptic = LocalHapticFeedback.current
    val trackColor by animateColorAsState(
        targetValue = when {
            checked -> apple.systemGreen
            apple.isDark -> Color(0xFF39393D)
            else -> Color(0xFFE5E5EA)
        },
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "appleSwitchTrack",
    )
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) 20.dp else 0.dp,
        animationSpec = spring(dampingRatio = 0.82f, stiffness = 520f),
        label = "appleSwitchThumb",
    )

    Box(
        modifier = modifier
            .size(width = 51.dp, height = 31.dp)
            .clip(CircleShape)
            .background(if (enabled) trackColor else trackColor.copy(alpha = 0.45f))
            .clickable(
                enabled = enabled,
                role = Role.Switch,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onCheckedChange(!checked)
            }
            .padding(2.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .offset(x = thumbOffset)
                .size(27.dp)
                .shadow(elevation = 2.5.dp, shape = CircleShape, ambientColor = Color(0x28000000), spotColor = Color(0x28000000))
                .clip(CircleShape)
                .background(Color.White),
        )
    }
}

/**
 * Circular Liquid Glass icon button (matches Reference Image 1 vs Image 5):
 * When [glassActive] is false (scroll = 0 on clean canvas), renders as a clean minimal icon;
 * when [glassActive] is true (scrolled over photos or menu open), blooms into a real GPU-blurred frosted circle!
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppleGlassCircleButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    darkHud: Boolean = false,
    glassActive: Boolean = true,
    tint: Color? = null,
    badgeDot: Boolean = false,
    size: Dp = 44.dp,
    iconSize: Dp = 22.dp,
) {
    val apple = AppleTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.90f else 1f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 600f),
        label = "glassCircleScale",
    )
    val glassAlpha by animateFloatAsState(
        targetValue = if (glassActive) 1f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "glassCircleBackdropAlpha",
    )
    val resolvedTint = tint ?: if (darkHud) Color.White else apple.iconPrimary

    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(contentDescription) } },
        state = rememberTooltipState(),
    ) {
        Box(
            modifier = modifier
                .size(size)
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .clip(CircleShape)
                .clickable(interactionSource = interaction, indication = null, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            if (glassAlpha > 0.02f) {
                RealLiquidGlassSurface(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = glassAlpha },
                    shape = CircleShape,
                    blurRadius = 22.dp,
                    darkHud = darkHud,
                    tintColor = when {
                        darkHud -> null
                        apple.isDark -> Color(0x80222228)
                        else -> Color(0x9EF5F6FA)
                    },
                    borderWidth = 1.dp,
                    shadowElevation = if (darkHud) 6.dp else 8.dp,
                ) {}
            }
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = resolvedTint,
                modifier = Modifier.size(iconSize),
            )
            if (badgeDot) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(9.dp)
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(apple.systemBlue),
                )
            }
        }
    }
}

/**
 * Authentic iOS UISegmentedControl:
 * Borderless flat translucent track, concentric rounded white/elevated sliding thumb,
 * subtle vertical dividers between inactive adjacent segments, and crisp compact height.
 * Preserves SemanticsProperties.Selected for UI tests and accessibility.
 */
@Composable
internal fun AppleSegmentedControl(
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    translucent: Boolean = false,
) {
    if (options.isEmpty()) return
    val apple = AppleTheme.colors
    val haptic = LocalHapticFeedback.current
    val selectedIndex = options.indexOfFirst { it.first == selected }.coerceAtLeast(0)
    val trackShape = RoundedCornerShape(10.dp)
    val thumbShape = RoundedCornerShape(8.dp)
    val density = LocalDensity.current
    var rowHeightPx by remember { mutableIntStateOf(0) }
    val indicatorHeight = if (rowHeightPx > 0) with(density) { rowHeightPx.toDp() } else 32.dp

    val trackColor = when {
        translucent && apple.isDark -> Color(0xB82C2C2E)
        translucent -> Color(0xC4E5E5EA)
        apple.isDark -> Color(0xFF2C2C2E)
        else -> Color(0xFFE3E3E8)
    }

    BoxWithConstraints(
        modifier = modifier
            .clip(trackShape)
            .background(trackColor)
            .padding(2.5.dp),
    ) {
        val segmentWidth = maxWidth / options.size
        val indicatorOffset by animateDpAsState(
            targetValue = segmentWidth * selectedIndex,
            animationSpec = spring(dampingRatio = 0.86f, stiffness = 520f),
            label = "appleSegmentOffset",
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(indicatorHeight),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            options.forEachIndexed { idx, _ ->
                Spacer(Modifier.weight(1f))
                if (idx < options.lastIndex) {
                    val hideDivider = idx == selectedIndex || idx + 1 == selectedIndex
                    Box(
                        Modifier
                            .width(0.6.dp)
                            .height(14.dp)
                            .background(if (hideDivider) Color.Transparent else apple.separator),
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .offset(x = indicatorOffset)
                .width(segmentWidth)
                .height(indicatorHeight)
                .shadow(
                    elevation = if (apple.isDark) 2.dp else 2.5.dp,
                    shape = thumbShape,
                    ambientColor = Color(0x1A000000),
                    spotColor = Color(0x1A000000),
                )
                .clip(thumbShape)
                .background(apple.glassPillActive),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .onSizeChanged { rowHeightPx = it.height },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            options.forEach { (key, label) ->
                val active = key == selected
                val textColor by animateColorAsState(
                    targetValue = if (active) apple.labelPrimary else apple.labelSecondary,
                    label = "appleSegmentText",
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 32.dp)
                        .clip(thumbShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {
                            if (!active) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onSelect(key)
                        }
                        .semantics { this[SemanticsProperties.Selected] = active }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label,
                        color = textColor,
                        fontSize = 13.sp,
                        lineHeight = 16.sp,
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

private data class DockTabSpec(
    val key: String,
    val label: String,
    val activeIcon: ImageVector,
    val inactiveIcon: ImageVector,
)

/**
 * Two-Layer Liquid Glass Bottom Navigation Dock (matching Reference Image 4):
 * - Outer capsule ("大圆框"): milky frosted GPU-blurred glass capsule with crisp white rim.
 * - Inner sliding pill ("小圆框，小的更透明"): clearer, MORE TRANSPARENT optical glass lens
 *   that glides smoothly on tap OR horizontal finger drag, letting real photo colors glow through!
 */
@Composable
internal fun AppleFloatingNavDock(
    current: String,
    lang: AppLanguage,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val apple = AppleTheme.colors
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val tabs = listOf(
        DockTabSpec("all", lang.t("全部", "All"), Icons.Rounded.GridView, Icons.Outlined.GridView),
        DockTabSpec("albums", lang.t("相册", "Albums"), Icons.Rounded.PhotoAlbum, Icons.Outlined.PhotoAlbum),
        DockTabSpec("generated", lang.t("生成", "Generated"), Icons.Rounded.AutoAwesome, Icons.Outlined.AutoAwesome),
    )
    val activeIndex = tabs.indexOfFirst { it.key == current }.coerceAtLeast(0)
    var dragging by remember { mutableStateOf(false) }
    var dragFraction by remember(activeIndex) { mutableFloatStateOf(activeIndex.toFloat()) }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .padding(4.dp)
            .pointerInput(tabs.size, current) {
                val tabWidthPx = (size.width.toFloat() / tabs.size).coerceAtLeast(1f)
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        dragging = true
                        dragFraction = ((offset.x / tabWidthPx) - 0.5f).coerceIn(0f, (tabs.size - 1).toFloat())
                    },
                    onDragCancel = {
                        dragging = false
                        dragFraction = activeIndex.toFloat()
                    },
                    onDragEnd = {
                        val target = dragFraction.roundToInt().coerceIn(0, tabs.lastIndex)
                        dragging = false
                        if (target != activeIndex) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onSelect(tabs[target].key)
                        }
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        dragFraction = (dragFraction + dragAmount / tabWidthPx).coerceIn(0f, (tabs.size - 1).toFloat())
                    },
                )
            },
    ) {
        val tabWidth = maxWidth / tabs.size
        val targetOffset = if (dragging) tabWidth * dragFraction else tabWidth * activeIndex
        val indicatorOffset by animateDpAsState(
            targetValue = targetOffset,
            animationSpec = if (dragging) spring(stiffness = Spring.StiffnessHigh) else spring(dampingRatio = 0.78f, stiffness = 420f),
            label = "appleDockIndicator",
        )
        val lensScale by animateFloatAsState(
            targetValue = if (dragging) 1.05f else 1f,
            animationSpec = spring(dampingRatio = 0.72f, stiffness = 460f),
            label = "appleDockLensScale",
        )

        // Smaller, MORE TRANSPARENT Liquid Glass optical lens pill ("小的更透明")
        LiquidGlassClearLens(
            shape = CircleShape,
            blurRadius = if (dragging) 8.dp else 11.dp,
            modifier = Modifier
                .offset(x = indicatorOffset)
                .width(tabWidth)
                .height(54.dp)
                .graphicsLayer {
                    scaleX = lensScale
                    scaleY = lensScale
                },
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val highlightedIndex = if (dragging) dragFraction.roundToInt().coerceIn(0, tabs.lastIndex) else activeIndex
            tabs.forEachIndexed { idx, tab ->
                val selected = idx == highlightedIndex
                val contentColor by animateColorAsState(
                    targetValue = when {
                        selected -> apple.labelPrimary
                        apple.isDark -> Color(0xB8FFFFFF)
                        else -> Color(0xFF45454A)
                    },
                    label = "appleDockColor",
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .height(54.dp)
                        .clip(CircleShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {
                            if (tab.key != current) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onSelect(tab.key)
                        }
                        .semantics { this[SemanticsProperties.Selected] = tab.key == current },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        imageVector = if (selected) tab.activeIcon else tab.inactiveIcon,
                        contentDescription = null,
                        tint = contentColor,
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = tab.label,
                        color = contentColor,
                        fontSize = 11.sp,
                        lineHeight = 13.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** Authentic Apple Photos blue circular checkmark selection overlay. */
@Composable
internal fun AppleSelectionOverlay(
    modifier: Modifier = Modifier,
    alignment: Alignment = Alignment.BottomEnd,
) {
    val apple = AppleTheme.colors
    Box(
        modifier
            .fillMaxSize()
            .background(apple.systemBlue.copy(alpha = 0.20f)),
    ) {
        Box(
            modifier = Modifier
                .align(alignment)
                .padding(6.dp)
                .size(23.dp)
                .shadow(2.dp, CircleShape)
                .clip(CircleShape)
                .background(apple.systemBlue)
                .border(1.5.dp, Color.White, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

/** Crisp Apple pill button for Viewer HUD and primary/secondary actions. */
@Composable
internal fun AppleCapsuleButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    primary: Boolean = true,
    darkHud: Boolean = false,
    destructive: Boolean = false,
    leadingIcon: ImageVector? = null,
) {
    val apple = AppleTheme.colors
    val container = when {
        !enabled -> if (darkHud) Color(0x2EFFFFFF) else apple.fillSecondary
        destructive -> apple.systemRed
        primary -> apple.systemBlue
        darkHud -> Color(0x30FFFFFF)
        else -> apple.systemBlue.copy(alpha = if (apple.isDark) 0.18f else 0.10f)
    }
    val content = when {
        !enabled -> if (darkHud) Color(0x88FFFFFF) else apple.labelTertiary
        destructive || primary -> Color.White
        darkHud -> Color.White
        else -> apple.systemBlue
    }

    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 34.dp),
        shape = CircleShape,
        color = container,
        contentColor = content,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            leadingIcon?.let {
                Icon(it, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(5.dp))
            }
            Text(
                text = text,
                fontSize = 13.5.sp,
                lineHeight = 17.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
