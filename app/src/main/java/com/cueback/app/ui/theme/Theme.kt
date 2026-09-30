package com.cueback.app.ui.theme

import android.provider.Settings
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cueback.app.R

/**
 * Ember: a near-black ground lit by orange light, the same light the owl's eyes and cloak give off.
 * CueBack is dark-only; the palette and the mascot artwork are made for a dark ground.
 */
object Ember {
    val Coal = Color(0xFF080503)
    val Soot = Color(0xFF160B06)
    val Flame = Color(0xFFFF6A1A)
    val Glow = Color(0xFFFFA24A)
    val Core = Color(0xFFFFD699)
    val Rust = Color(0xFFB8360A)
    val Lava = Color(0xFF8E1A08)
    val Cream = Color(0xFFFFF3E8)
    val Ash = Color(0xFFCDB8A9)
    val Faint = Color(0x80FFF3E8)
    /** Text and icons that sit on flame-coloured surfaces. */
    val Ink = Color(0xFF1B0900)
    /** Translucent panel fill: reads as smoked glass over both the orange and the black parts of the backdrop. */
    val Glass = Color(0x8F1A0B04)
    val GlassStrong = Color(0xC7140903)
    val GlassStrokeTop = Color(0x38FFE0C2)
    val GlassStrokeBottom = Color(0x0FFFE0C2)
}

/** The bookmark ribbon that marks "your place". */
val Ribbon = Ember.Glow

private val Scheme = darkColorScheme(
    primary = Ember.Flame,
    onPrimary = Ember.Ink,
    primaryContainer = Color(0xFF3A1706),
    onPrimaryContainer = Ember.Cream,
    secondary = Ember.Glow,
    onSecondary = Ember.Ink,
    secondaryContainer = Color(0xFF5A2408),
    onSecondaryContainer = Ember.Cream,
    tertiary = Ember.Core,
    onTertiary = Ember.Ink,
    background = Ember.Coal,
    onBackground = Ember.Cream,
    surface = Ember.Soot,
    onSurface = Ember.Cream,
    surfaceVariant = Color(0xFF2B160C),
    onSurfaceVariant = Ember.Ash,
    outline = Color(0xFF7A5642),
    outlineVariant = Color(0xFF3A2318),
    surfaceContainerLowest = Color(0xFF0B0604),
    surfaceContainerLow = Color(0xFF140A06),
    surfaceContainer = Color(0xFF1B0E08),
    surfaceContainerHigh = Color(0xFF24130B),
    surfaceContainerHighest = Color(0xFF2E190F),
    inverseSurface = Ember.Cream,
    inverseOnSurface = Ember.Ink,
    inversePrimary = Ember.Rust,
    error = Color(0xFFFF8E7A),
    onError = Ember.Ink,
    scrim = Color(0xCC000000),
)

@OptIn(ExperimentalTextApi::class)
private fun grotesk(weight: Int) = Font(
    R.font.inter_tight,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

/** Inter Tight, one variable file. Light for the large statements, medium for labels. */
val Grotesk = FontFamily(grotesk(300), grotesk(400), grotesk(500), grotesk(600))

private fun style(weight: Int, size: Int, line: Int, tracking: Double = 0.0) = TextStyle(
    fontFamily = Grotesk,
    fontWeight = FontWeight(weight),
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = tracking.sp,
)

private val Type = Typography(
    displayLarge = style(300, 46, 50, -1.2),
    displayMedium = style(300, 42, 46, -1.0),
    displaySmall = style(300, 38, 42, -0.9),
    headlineLarge = style(300, 32, 37, -0.6),
    headlineMedium = style(300, 28, 33, -0.5),
    headlineSmall = style(500, 22, 27, -0.2),
    titleLarge = style(500, 19, 24, -0.1),
    titleMedium = style(500, 16, 21),
    titleSmall = style(500, 14, 19),
    bodyLarge = style(400, 16, 23),
    bodyMedium = style(400, 14, 20),
    bodySmall = style(400, 12, 17),
    labelLarge = style(600, 15, 20),
    labelMedium = style(500, 13, 17),
    labelSmall = style(500, 11, 15, 0.1),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp),
)

val LocalReducedMotion = staticCompositionLocalOf { false }

@Composable
fun CueBackTheme(content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    val reduced = Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    CompositionLocalProvider(LocalReducedMotion provides reduced) {
        MaterialTheme(colorScheme = Scheme, typography = Type, shapes = AppShapes, content = content)
    }
}
