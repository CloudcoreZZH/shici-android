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
    primary = Color(0xFFC84927), onPrimary = Color.White, primaryContainer = Color(0xFFFFE3D8),
    onPrimaryContainer = Color(0xFF8E2D14), secondary = Color(0xFF756562),
    secondaryContainer = Color(0xFFFFE3D8), onSecondaryContainer = Color(0xFF8E2D14),
    background = Color(0xFFFAFAF7), surface = Color(0xFFFAFAF7), surfaceContainer = Color(0xFFF1F0ED),
    surfaceContainerLow = Color(0xFFF6F5F2), onSurface = Color(0xFF191B21),
    onSurfaceVariant = Color(0xFF62636B), outlineVariant = Color(0xFFE4E2DD),
)
private val Dark = darkColorScheme(
    primary = Color(0xFFFFA185), onPrimary = Color(0xFF501907), primaryContainer = Color(0xFF633326),
    onPrimaryContainer = Color(0xFFFFDBD0), secondary = Color(0xFFD5BBB3),
    secondaryContainer = Color(0xFF633326), onSecondaryContainer = Color(0xFFFFDBD0),
    background = Color(0xFF17191D), surface = Color(0xFF17191D), surfaceContainer = Color(0xFF272A30),
    surfaceContainerLow = Color(0xFF202328), onSurface = Color(0xFFF6F3EE),
    onSurfaceVariant = Color(0xFFB9BDC7), outlineVariant = Color(0xFF45484F),
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
