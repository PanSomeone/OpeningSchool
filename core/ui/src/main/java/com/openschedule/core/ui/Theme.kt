package com.openschedule.core.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.openschedule.core.model.ThemeMode
import androidx.compose.foundation.isSystemInDarkTheme

data class CourseColors(
    val container: Color,
    val onContainer: Color,
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF1F5EFF),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDDE6FF),
    onPrimaryContainer = Color(0xFF06214F),
    secondary = Color(0xFF0E7C66),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD6F3EA),
    onSecondaryContainer = Color(0xFF06352B),
    background = Color(0xFFF5F6F8),
    onBackground = Color(0xFF14161A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF14161A),
    surfaceVariant = Color(0xFFEDF0F4),
    onSurfaceVariant = Color(0xFF56606C),
    outline = Color(0xFFD2D8E0),
    outlineVariant = Color(0xFFE4E8ED),
    error = Color(0xFFB3261E),
    onError = Color.White,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA9C2FF),
    onPrimary = Color(0xFF0B2B66),
    primaryContainer = Color(0xFF1B3C7A),
    onPrimaryContainer = Color(0xFFD9E4FF),
    secondary = Color(0xFF75DBC4),
    onSecondary = Color(0xFF00382D),
    secondaryContainer = Color(0xFF155244),
    onSecondaryContainer = Color(0xFFC2F5E8),
    background = Color(0xFF0E1013),
    onBackground = Color(0xFFE8EBF0),
    surface = Color(0xFF15181D),
    onSurface = Color(0xFFE8EBF0),
    surfaceVariant = Color(0xFF20242B),
    onSurfaceVariant = Color(0xFFA9B2BE),
    outline = Color(0xFF3A404A),
    outlineVariant = Color(0xFF282D35),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
)

private val LightCourseColors = listOf(
    CourseColors(Color(0xFFDCE7FF), Color(0xFF14306B)),
    CourseColors(Color(0xFFD8F5E5), Color(0xFF0C4A31)),
    CourseColors(Color(0xFFFFE6CC), Color(0xFF6B3A00)),
    CourseColors(Color(0xFFFBDDE8), Color(0xFF6B2143)),
    CourseColors(Color(0xFFE7DFFF), Color(0xFF3A2670)),
    CourseColors(Color(0xFFD9F2F5), Color(0xFF0E4A52)),
    CourseColors(Color(0xFFECEFCF), Color(0xFF444A0F)),
    CourseColors(Color(0xFFFFE3D9), Color(0xFF662414)),
    CourseColors(Color(0xFFE4E7EC), Color(0xFF2A2F37)),
    CourseColors(Color(0xFFF1E2FF), Color(0xFF4B2A66)),
)

private val DarkCourseColors = listOf(
    CourseColors(Color(0xFF223B69), Color(0xFFD8E4FF)),
    CourseColors(Color(0xFF173D2F), Color(0xFFB9F2D6)),
    CourseColors(Color(0xFF4A3418), Color(0xFFFFDFB0)),
    CourseColors(Color(0xFF46222F), Color(0xFFFFD3E2)),
    CourseColors(Color(0xFF342A52), Color(0xFFE3D6FF)),
    CourseColors(Color(0xFF173C42), Color(0xFFC3F1F7)),
    CourseColors(Color(0xFF353A18), Color(0xFFE7EDB4)),
    CourseColors(Color(0xFF45281F), Color(0xFFFFD8C8)),
    CourseColors(Color(0xFF2A2F37), Color(0xFFE1E5EB)),
    CourseColors(Color(0xFF3B2A4B), Color(0xFFF0D9FF)),
)

val LocalCourseColors = staticCompositionLocalOf { LightCourseColors }
val LocalIsDarkTheme = staticCompositionLocalOf { false }

private val AppTypography = Typography().let { base ->
    base.copy(
        headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.4).sp),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.Medium),
        labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
        labelSmall = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Medium),
    )
}

@Composable
fun OpenScheduleTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    CompositionLocalProvider(
        LocalCourseColors provides if (dark) DarkCourseColors else LightCourseColors,
        LocalIsDarkTheme provides dark,
    ) {
        MaterialTheme(
            colorScheme = if (dark) DarkColors else LightColors,
            typography = AppTypography,
            content = content,
        )
    }
}
