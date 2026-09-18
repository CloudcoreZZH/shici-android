package io.github.shici.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp

val BookSerif = FontFamily.Serif
private val Light = lightColorScheme(
    primary = Color(0xFFAF493A), onPrimary = Color.White, primaryContainer = Color(0xFFF0DCD4),
    onPrimaryContainer = Color(0xFF652D24), secondary = Color(0xFF626C54),
    secondaryContainer = Color(0xFFE0E4D6), onSecondaryContainer = Color(0xFF343E2A),
    background = Color(0xFFF7F3EA), surface = Color(0xFFF7F3EA), surfaceContainer = Color(0xFFEAE5DA),
    surfaceContainerLow = Color(0xFFF0EBE1), onSurface = Color(0xFF232524),
    onSurfaceVariant = Color(0xFF6D6961), outline = Color(0xFF8C867C), outlineVariant = Color(0xFFDCD5C9),
    errorContainer = Color(0xFFF0DCD5), onErrorContainer = Color(0xFF702F27),
)
private val Dark = darkColorScheme(
    primary = Color(0xFFEBA18D), onPrimary = Color(0xFF492019), primaryContainer = Color(0xFF53342C),
    onPrimaryContainer = Color(0xFFF4D4C8), secondary = Color(0xFFBAC4A7),
    secondaryContainer = Color(0xFF383E31), onSecondaryContainer = Color(0xFFE0E7D2),
    background = Color(0xFF201F1C), surface = Color(0xFF201F1C), surfaceContainer = Color(0xFF34312C),
    surfaceContainerLow = Color(0xFF292722), onSurface = Color(0xFFF1EADD),
    onSurfaceVariant = Color(0xFFC2BAAD), outline = Color(0xFF938C80), outlineVariant = Color(0xFF484339),
    errorContainer = Color(0xFF53342F), onErrorContainer = Color(0xFFF0C4B9),
)

@Composable fun isDark(appearance: Appearance) = when (appearance) {
    Appearance.SYSTEM -> isSystemInDarkTheme()
    Appearance.LIGHT -> false
    Appearance.DARK -> true
}

@Composable fun ShiciTheme(appearance: Appearance, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isDark(appearance)) Dark else Light,
        shapes = Shapes(extraSmall = RoundedCornerShape(4.dp), small = RoundedCornerShape(6.dp),
            medium = RoundedCornerShape(8.dp), large = RoundedCornerShape(12.dp), extraLarge = RoundedCornerShape(16.dp)),
        typography = Typography(
            headlineLarge = TextStyle(fontSize = 34.sp, lineHeight = 44.sp),
            titleLarge = TextStyle(fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.Medium),
            bodyLarge = TextStyle(fontSize = 18.sp, lineHeight = 29.sp),
            bodyMedium = TextStyle(fontSize = 15.sp, lineHeight = 24.sp),
            bodySmall = TextStyle(fontSize = 13.sp, lineHeight = 20.sp),
            labelLarge = TextStyle(fontSize = 17.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium),
        ), content = content)
}
