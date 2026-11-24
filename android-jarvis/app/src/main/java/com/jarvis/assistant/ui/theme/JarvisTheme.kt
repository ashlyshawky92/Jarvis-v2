package com.jarvis.assistant.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object JarvisColors {
    val VoidBlack = Color(0xFF050914)
    val DeepNavy = Color(0xFF0B1221)
    val PanelNavy = Color(0xFF101A2F)
    val HairlineBlue = Color(0xFF1E3254)
    val CyanCore = Color(0xFF00E5FF)
    val ElectricBlue = Color(0xFF2979FF)
    val TextPrimary = Color(0xFFEAF6FF)
    val TextSecondary = Color(0xFF8CA6C8)
    val StatusNominal = Color(0xFF10B981)
    val StatusWarning = Color(0xFFF59E0B)
    val StatusError = Color(0xFFEF4444)
}

private val DarkScheme = darkColorScheme(
    primary = JarvisColors.CyanCore,
    secondary = JarvisColors.ElectricBlue,
    background = JarvisColors.VoidBlack,
    surface = JarvisColors.DeepNavy,
    surfaceVariant = JarvisColors.PanelNavy,
    onPrimary = JarvisColors.VoidBlack,
    onSecondary = Color.White,
    onBackground = JarvisColors.TextPrimary,
    onSurface = JarvisColors.TextPrimary,
    onSurfaceVariant = JarvisColors.TextSecondary,
    error = JarvisColors.StatusError
)

@Composable
fun JarvisTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkScheme,
        content = content
    )
}
