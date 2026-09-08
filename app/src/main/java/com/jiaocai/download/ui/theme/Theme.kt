package com.jiaocai.download.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// 遵循 Material You 的紫罗兰基调，与应用图标配色统一，整体更细腻协调。
private val LightColors = lightColorScheme(
    primary = Color(0xFF6750A4),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE8DDF5),
    onPrimaryContainer = Color(0xFF2A1B4A),
    secondary = Color(0xFF7D5260),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF3D7E0),
    onSecondaryContainer = Color(0xFF3A1A24),
    tertiary = Color(0xFF386A20),
    onTertiary = Color.White,
    background = Color(0xFFFDF8FF),
    onBackground = Color(0xFF1D1B20),
    surface = Color(0xFFFDF8FF),
    onSurface = Color(0xFF1D1B20),
    surfaceVariant = Color(0xFFE7DDF3),
    onSurfaceVariant = Color(0xFF49454F),
    outline = Color(0xFF7A757F),
    error = Color(0xFFB3261E),
    onError = Color.White,
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
)

@Composable
fun TextbookTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColors,
        content = content,
    )
}
