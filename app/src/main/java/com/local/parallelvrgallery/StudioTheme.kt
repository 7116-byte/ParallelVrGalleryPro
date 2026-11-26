package com.local.parallelvrgallerypro

import android.app.Activity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat

/** Keep gallery chrome clear of transient bars after leaving an immersive Viewer. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Modifier.studioSafeArea(): Modifier = windowInsetsPadding(
    WindowInsets.safeDrawing.union(WindowInsets.systemBarsIgnoringVisibility),
)

@Immutable
internal data class StudioAppleColors(
    val isDark: Boolean,
    val groupedBackground: Color,
    val headerBackdrop: Color,
    val cardBackground: Color,
    val elevatedSurface: Color,
    val separator: Color,
    val fillSecondary: Color,
    val fillTertiary: Color,
    val systemBlue: Color,
    val systemGreen: Color,
    val systemOrange: Color,
    val systemRed: Color,
    val labelPrimary: Color,
    val labelSecondary: Color,
    val labelTertiary: Color,
    val iconPrimary: Color,
    val glassFill: Color,
    val glassFillStrong: Color,
    val glassPillActive: Color,
    val glassBorderTop: Color,
    val glassBorderBottom: Color,
    val glassShadow: Color,
    val darkGlassFill: Color,
    val darkGlassBorderTop: Color,
    val darkGlassBorderBottom: Color,
)

private val AppleLightPalette = StudioAppleColors(
    isDark = false,
    groupedBackground = Color(0xFFF6F6F8),
    headerBackdrop = Color(0xF5F6F6F8),
    cardBackground = Color(0xFFFFFFFF),
    elevatedSurface = Color(0xFFFFFFFF),
    separator = Color(0x1E3C3C43),
    fillSecondary = Color(0x16767680),
    fillTertiary = Color(0x0E767680),
    systemBlue = Color(0xFF007AFF),
    systemGreen = Color(0xFF34C759),
    systemOrange = Color(0xFFFF9500),
    systemRed = Color(0xFFFF3B30),
    labelPrimary = Color(0xFF111113),
    labelSecondary = Color(0xFF6E6E73),
    labelTertiary = Color(0xFF8E8E93),
    iconPrimary = Color(0xFF1C1C1E),
    glassFill = Color(0xA4F4F5F8),
    glassFillStrong = Color(0xB6F5F6F9),
    glassPillActive = Color(0xFFFFFFFF),
    glassBorderTop = Color(0xF5FFFFFF),
    glassBorderBottom = Color(0x70FFFFFF),
    glassShadow = Color(0x22000000),
    darkGlassFill = Color(0xAA16161A),
    darkGlassBorderTop = Color(0x45FFFFFF),
    darkGlassBorderBottom = Color(0x16FFFFFF),
)

private val AppleDarkPalette = StudioAppleColors(
    isDark = true,
    groupedBackground = Color(0xFF000000),
    headerBackdrop = Color(0xF2000000),
    cardBackground = Color(0xFF1C1C1E),
    elevatedSurface = Color(0xFF2C2C2E),
    separator = Color(0x47545458),
    fillSecondary = Color(0x3D767680),
    fillTertiary = Color(0x29767680),
    systemBlue = Color(0xFF0A84FF),
    systemGreen = Color(0xFF30D158),
    systemOrange = Color(0xFFFF9F0A),
    systemRed = Color(0xFFFF453A),
    labelPrimary = Color(0xFFFFFFFF),
    labelSecondary = Color(0xFF98989F),
    labelTertiary = Color(0xFF636366),
    iconPrimary = Color(0xFFE5E5EA),
    glassFill = Color(0xDC1E1E22),
    glassFillStrong = Color(0xEB222226),
    glassPillActive = Color(0xFF636366),
    glassBorderTop = Color(0x33FFFFFF),
    glassBorderBottom = Color(0x10FFFFFF),
    glassShadow = Color(0x88000000),
    darkGlassFill = Color(0xCC18181B),
    darkGlassBorderTop = Color(0x38FFFFFF),
    darkGlassBorderBottom = Color(0x12FFFFFF),
)

internal val LocalAppleColors = staticCompositionLocalOf { AppleLightPalette }

internal object AppleTheme {
    val colors: StudioAppleColors
        @Composable get() = LocalAppleColors.current
}

@Immutable
internal data class StudioWindowMetrics(
    val widthDp: Int,
    val heightDp: Int,
    val isTablet: Boolean,
    val isSplitPane: Boolean,
    val isCompactPhone: Boolean,
)

@Composable
internal fun rememberStudioWindowMetrics(): StudioWindowMetrics {
    val config = LocalConfiguration.current
    val w = config.screenWidthDp
    val h = config.screenHeightDp
    return StudioWindowMetrics(
        widthDp = w,
        heightDp = h,
        isTablet = w >= 600,
        isSplitPane = w >= 840,
        isCompactPhone = w < 410,
    )
}

private val StudioLight = lightColorScheme(
    primary = Color(0xFF007AFF),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE5F1FF),
    onPrimaryContainer = Color(0xFF004099),
    secondary = Color(0xFF5856D6),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE9E9F2),
    onSecondaryContainer = Color(0xFF1C1C1E),
    tertiary = Color(0xFF34C759),
    onTertiary = Color.White,
    background = Color(0xFFF2F2F7),
    onBackground = Color(0xFF1C1C1E),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1C1C1E),
    surfaceVariant = Color(0xFFEAEAF0),
    onSurfaceVariant = Color(0xFF6E6E73),
    surfaceContainer = Color(0xFFF2F2F7),
    surfaceContainerHigh = Color(0xFFE5E5EA),
    outline = Color(0xFFC6C6C8),
    outlineVariant = Color(0xFFE5E5EA),
    error = Color(0xFFFF3B30),
    onError = Color.White,
)

private val StudioDark = darkColorScheme(
    primary = Color(0xFF0A84FF),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF0E2C52),
    onPrimaryContainer = Color(0xFF8CC5FF),
    secondary = Color(0xFF5E5CE6),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF2C2C2E),
    onSecondaryContainer = Color(0xFFFFFFFF),
    tertiary = Color(0xFF30D158),
    onTertiary = Color.Black,
    background = Color(0xFF000000),
    onBackground = Color(0xFFFFFFFF),
    surface = Color(0xFF1C1C1E),
    onSurface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFF2C2C2E),
    onSurfaceVariant = Color(0xFF98989F),
    surfaceContainer = Color(0xFF1C1C1E),
    surfaceContainerHigh = Color(0xFF2C2C2E),
    outline = Color(0xFF48484A),
    outlineVariant = Color(0xFF38383A),
    error = Color(0xFFFF453A),
    onError = Color.White,
)

private val AppleTypography = Typography(
    headlineSmall = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
        letterSpacing = (-0.4).sp,
    ),
    titleLarge = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 21.sp,
        lineHeight = 26.sp,
        letterSpacing = (-0.3).sp,
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 21.sp,
        letterSpacing = (-0.15).sp,
    ),
    titleSmall = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 19.sp,
        letterSpacing = (-0.1).sp,
    ),
    bodyLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 21.sp,
        letterSpacing = (-0.1).sp,
    ),
    bodyMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodySmall = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 17.sp,
    ),
    labelLarge = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 18.sp,
    ),
    labelMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
    labelSmall = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        lineHeight = 14.sp,
    ),
)

@Composable
internal fun studioFloatingTextStyle(style: TextStyle, foreground: Color): TextStyle {
    val shadow = if (foreground.luminance() > 0.5f) Color(0x88000000) else Color(0x88FFFFFF)
    return style.copy(shadow = Shadow(color = shadow, blurRadius = with(LocalDensity.current) { 3.dp.toPx() }))
}

@Composable
fun StudioTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val view = LocalView.current
    val systemDark = isSystemInDarkTheme()
    DisposableEffect(view, dark, systemDark) {
        onDispose {
            if (dark != systemDark) (view.context as? Activity)?.window?.let { window ->
                WindowInsetsControllerCompat(window, view).apply {
                    isAppearanceLightStatusBars = !systemDark
                    isAppearanceLightNavigationBars = !systemDark
                }
            }
        }
    }
    SideEffect {
        (view.context as? Activity)?.window?.let { window ->
            WindowCompat.setDecorFitsSystemWindows(window, false)
            @Suppress("DEPRECATION")
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            @Suppress("DEPRECATION")
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
            WindowInsetsControllerCompat(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    val appleColors = if (dark) AppleDarkPalette else AppleLightPalette
    CompositionLocalProvider(LocalAppleColors provides appleColors) {
        MaterialTheme(
            colorScheme = if (dark) StudioDark else StudioLight,
            typography = AppleTypography,
            shapes = Shapes(
                small = RoundedCornerShape(10.dp),
                medium = RoundedCornerShape(14.dp),
                large = RoundedCornerShape(22.dp),
            ),
            content = content,
        )
    }
}

/** A bounded Apple-style inset-grouped reading column; every control stays reachable with IME or large fonts. */
@Composable
fun StudioPage(header: (@Composable () -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val apple = AppleTheme.colors
    Box(
        Modifier
            .fillMaxSize()
            .background(apple.groupedBackground)
            .studioSafeArea()
            .imePadding(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(Modifier.widthIn(max = 720.dp).fillMaxWidth()) {
            header?.let {
                Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) { it() }
            }
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
                content = content,
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

/**
 * Authentic iOS InsetGrouped section:
 * Muted section label ABOVE a clean borderless rounded white (light) / charcoal (dark) card.
 */
@Composable
fun StudioCard(
    title: String,
    subtitle: String? = null,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val apple = AppleTheme.colors
    Column(Modifier.fillMaxWidth()) {
        if (title.isNotBlank() || !subtitle.isNullOrBlank()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 7.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                if (title.isNotBlank()) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelMedium,
                        color = apple.labelSecondary,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
                if (!subtitle.isNullOrBlank()) {
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = apple.labelTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        Surface(
            color = apple.cardBackground,
            shape = RoundedCornerShape(14.dp),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
        ) {
            Column(
                Modifier.fillMaxWidth().padding(contentPadding),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                content = content,
            )
        }
    }
}

/** Authentic iOS InsetGrouped list container where rows touch edges cleanly with inset hairline dividers. */
@Composable
internal fun AppleGroupedList(
    title: String,
    trailingHeader: String? = null,
    footer: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val apple = AppleTheme.colors
    Column(Modifier.fillMaxWidth()) {
        if (title.isNotBlank() || !trailingHeader.isNullOrBlank()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 7.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                if (title.isNotBlank()) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelMedium,
                        color = apple.labelSecondary,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
                if (!trailingHeader.isNullOrBlank()) {
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = trailingHeader,
                        style = MaterialTheme.typography.labelSmall,
                        color = apple.labelTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        Surface(
            color = apple.cardBackground,
            shape = RoundedCornerShape(14.dp),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
        ) {
            Column(Modifier.fillMaxWidth(), content = content)
        }
        if (footer != null) {
            Box(Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, top = 7.dp)) {
                footer()
            }
        }
    }
}

@Composable
fun StudioEmptyState(
    title: String,
    detail: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Outlined.Info,
    action: (@Composable () -> Unit)? = null,
) {
    val apple = AppleTheme.colors
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            Modifier
                .widthIn(max = 400.dp)
                .verticalScroll(rememberScrollState())
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = apple.systemBlue.copy(alpha = if (apple.isDark) 0.16f else 0.10f),
                border = BorderStroke(0.5.dp, apple.systemBlue.copy(alpha = 0.18f)),
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.padding(16.dp).size(28.dp),
                    tint = apple.systemBlue,
                )
            }
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                color = apple.labelPrimary,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            Text(
                detail,
                style = MaterialTheme.typography.bodyMedium,
                color = apple.labelSecondary,
                textAlign = TextAlign.Center,
            )
            if (action != null) {
                Spacer(Modifier.height(6.dp))
                action()
            }
        }
    }
}

@Composable
fun StudioSwitch(
    title: String,
    detail: String,
    checked: Boolean,
    inGroupedList: Boolean = false,
    onChange: (Boolean) -> Unit,
) {
    val apple = AppleTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .padding(
                horizontal = if (inGroupedList) 16.dp else 0.dp,
                vertical = if (inGroupedList) 11.dp else 2.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = apple.labelPrimary, fontWeight = FontWeight.Normal)
            if (detail.isNotBlank()) {
                Text(detail, style = MaterialTheme.typography.bodySmall, color = apple.labelSecondary)
            }
        }
        AppleSwitch(checked = checked, onCheckedChange = onChange)
    }
}
