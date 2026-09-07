package com.jiaocai.download.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Green = Color(0xFF2E7D32)
private val GreenDark = Color(0xFF1B5E20)
private val Amber = Color(0xFFF9A825)

private val LightColors = lightColorScheme(
    primary = Green,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC8E6C9),
    onPrimaryContainer = GreenDark,
    secondary = Amber,
    onSecondary = Color(0xFF3E2723),
    background = Color(0xFFF7FAF7),
    surface = Color.White,
)

@Composable
fun TextbookTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColors,
        content = content,
    )
}
