package io.github.shici.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Coral = Color(0xFFF17754)
private val Light = lightColorScheme(
    primary = Color(0xFF315D4D), onPrimary = Color.White, primaryContainer = Color(0xFFE1EDE4),
    onPrimaryContainer = Color(0xFF1E4335), secondary = Color(0xFF8A5339),
    secondaryContainer = Color(0xFFF7E5DA), onSecondaryContainer = Color(0xFF65351F),
    background = Color(0xFFF8F7F3), surface = Color(0xFFF8F7F3), surfaceContainer = Color(0xFFEDEEE7),
    surfaceContainerLow = Color(0xFFF0F1EB), onSurface = Color(0xFF202B27),
    onSurfaceVariant = Color(0xFF59665E), outlineVariant = Color(0xFFD9DFD7),
)
private val Dark = darkColorScheme(
    primary = Color(0xFFAFD6BB), onPrimary = Color(0xFF183B2B), primaryContainer = Color(0xFF2B4638),
    onPrimaryContainer = Color(0xFFDCEEDD), secondary = Color(0xFFEBBA9D),
    secondaryContainer = Color(0xFF523B2D), onSecondaryContainer = Color(0xFFF3D8C7),
    background = Color(0xFF171D1A), surface = Color(0xFF171D1A), surfaceContainer = Color(0xFF29332D),
    surfaceContainerLow = Color(0xFF202A24), onSurface = Color(0xFFF0F1E9),
    onSurfaceVariant = Color(0xFFB8C4B9), outlineVariant = Color(0xFF414E44),
)

@Composable fun isDark(appearance: Appearance) = when (appearance) {
    Appearance.SYSTEM -> isSystemInDarkTheme()
    Appearance.LIGHT -> false
    Appearance.DARK -> true
}

@Composable fun ShiciTheme(appearance: Appearance, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isDark(appearance)) Dark else Light,
        typography = Typography(
            headlineLarge = TextStyle(fontSize = 32.sp, fontWeight = FontWeight.Bold),
            titleLarge = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold),
            bodyLarge = TextStyle(fontSize = 17.sp, lineHeight = 26.sp),
            bodyMedium = TextStyle(fontSize = 15.sp, lineHeight = 23.sp),
            labelLarge = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium),
        ), content = content)
}
