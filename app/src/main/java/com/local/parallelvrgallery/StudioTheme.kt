package com.local.parallelvrgallerypro

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import android.app.Activity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private val StudioLight = lightColorScheme(
    primary = Color(0xFF206B58), onPrimary = Color.White,
    primaryContainer = Color(0xFFD4EEE3), onPrimaryContainer = Color(0xFF103E31),
    secondary = Color(0xFF52665D), onSecondary = Color.White,
    secondaryContainer = Color(0xFFE0E9E2), onSecondaryContainer = Color(0xFF203B2B),
    background = Color(0xFFF5F7F3), onBackground = Color(0xFF19211D),
    surface = Color(0xFFFDFEFA), onSurface = Color(0xFF19211D),
    surfaceVariant = Color(0xFFE6ECE5), onSurfaceVariant = Color(0xFF4C5C52),
    surfaceContainer = Color(0xFFEEF2EB), surfaceContainerHigh = Color(0xFFE7EDE5),
    outline = Color(0xFF78877D), outlineVariant = Color(0xFFD0DAD0),
)
private val StudioDark = darkColorScheme(
    primary = Color(0xFF9CD6BF), onPrimary = Color(0xFF09382A),
    primaryContainer = Color(0xFF225444), onPrimaryContainer = Color(0xFFBEEDD9),
    secondary = Color(0xFFB7CCBD), onSecondary = Color(0xFF203B2B),
    secondaryContainer = Color(0xFF354D40), onSecondaryContainer = Color(0xFFD7EBDD),
    background = Color(0xFF101713), onBackground = Color(0xFFE1EAE1),
    surface = Color(0xFF151E18), onSurface = Color(0xFFE1EAE1),
    surfaceVariant = Color(0xFF27342B), onSurfaceVariant = Color(0xFFB5C5B8),
    surfaceContainer = Color(0xFF1C2820), surfaceContainerHigh = Color(0xFF26342B),
    outline = Color(0xFF82998A), outlineVariant = Color(0xFF3B4D40),
)

@Composable
fun StudioTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val view = LocalView.current
    SideEffect {
        (view.context as? Activity)?.window?.let { window ->
            WindowCompat.setDecorFitsSystemWindows(window, false)
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
            WindowInsetsControllerCompat(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    MaterialTheme(
        colorScheme = if (dark) StudioDark else StudioLight,
        shapes = Shapes(small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(18.dp), large = RoundedCornerShape(26.dp)),
        content = content,
    )
}

/** A bounded reading column; every control remains reachable with the IME or large fonts. */
@Composable
fun StudioPage(content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding().imePadding(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.widthIn(max = 840.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp), content = content,
        )
    }
}

@Composable
fun StudioCard(title: String, subtitle: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(22.dp), tonalElevation = 1.dp) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            content()
        }
    }
}

@Composable
fun StudioEmptyState(title: String, detail: String, modifier: Modifier = Modifier, icon: ImageVector = Icons.Default.Info, action: (@Composable () -> Unit)? = null) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(Modifier.widthIn(max = 420.dp).verticalScroll(rememberScrollState()).padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                Icon(icon, null, Modifier.padding(18.dp).size(32.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            action?.invoke()
        }
    }
}

@Composable
fun StudioSwitch(title: String, detail: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked, onChange)
    }
}
