package com.cueback.app.ui.theme

import android.provider.Settings
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** The amber bookmark ribbon: used only to mark "your place". */
val Ribbon = Color(0xFFE9A93A)

private val Light = lightColorScheme(
    primary = Color(0xFF0F5C63),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD3E6E6),
    onPrimaryContainer = Color(0xFF06363A),
    secondary = Color(0xFF4A5A6A),
    background = Color(0xFFEEF1F4),
    onBackground = Color(0xFF18212B),
    surface = Color(0xFFF9FAFB),
    onSurface = Color(0xFF18212B),
    surfaceVariant = Color(0xFFE1E6EB),
    onSurfaceVariant = Color(0xFF4F5B67),
    outline = Color(0xFFB7C0C9),
    outlineVariant = Color(0xFFD5DBE1),
    error = Color(0xFFB3261E),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF7FC4C6),
    onPrimary = Color(0xFF00363A),
    primaryContainer = Color(0xFF1E4A4E),
    onPrimaryContainer = Color(0xFFCDEBEC),
    secondary = Color(0xFFB0BDCA),
    background = Color(0xFF12171D),
    onBackground = Color(0xFFE6EBF0),
    surface = Color(0xFF1A222B),
    onSurface = Color(0xFFE6EBF0),
    surfaceVariant = Color(0xFF252F3A),
    onSurfaceVariant = Color(0xFFA9B5C1),
    outline = Color(0xFF4A5663),
    outlineVariant = Color(0xFF2E3844),
    error = Color(0xFFF2B8B5),
)

/** Serif is reserved for the sentences CueBack says back to you. */
val NoteSerif = FontFamily.Serif

private val Type = Typography(
    displaySmall = TextStyle(fontFamily = NoteSerif, fontWeight = FontWeight.Normal, fontSize = 32.sp, lineHeight = 40.sp),
    headlineMedium = TextStyle(fontFamily = NoteSerif, fontWeight = FontWeight.Normal, fontSize = 26.sp, lineHeight = 34.sp),
    headlineSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 18.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 15.sp),
)

private val AppShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(22.dp),
)

val LocalReducedMotion = staticCompositionLocalOf { false }

@Composable
fun CueBackTheme(content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    val reduced = Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    CompositionLocalProvider(LocalReducedMotion provides reduced) {
        MaterialTheme(
            colorScheme = if (isSystemInDarkTheme()) Dark else Light,
            typography = Type,
            shapes = AppShapes,
            content = content,
        )
    }
}
