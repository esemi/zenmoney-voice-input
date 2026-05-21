package dev.esemi.zmvoice.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = Color(0xFF7CB7FF),
    background = Color(0xFF101418),
    surface = Color(0xFF181C22),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF1F6FEB),
    background = Color(0xFFF5F7FB),
    surface = Color(0xFFFFFFFF),
)

@Composable
fun ZmVoiceTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) DarkColors else LightColors
    MaterialTheme(colorScheme = colors, content = content)
}
