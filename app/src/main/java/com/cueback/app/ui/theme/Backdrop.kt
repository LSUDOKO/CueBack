package com.cueback.app.ui.theme

import android.graphics.Bitmap
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import java.util.Random

/**
 * The three lighting set-ups every screen sits on.
 * Dawn: orange light pouring from the top (home). Night: black with ember light at the top and both
 * edges (the focused moments: welcome back, onboarding, paywall). Dusk: Night turned down, for
 * screens that are mostly reading (settings, library, details).
 */
enum class Backdrop { Dawn, Night, Dusk }

/** Film grain, tiled. It keeps the large gradients from banding and gives the glass something to sit on. */
private val grain: ImageBitmap by lazy {
    val side = 128
    val rnd = Random(7)
    val px = IntArray(side * side) {
        val alpha = rnd.nextInt(22)
        val v = if (rnd.nextBoolean()) 255 else 0
        (alpha shl 24) or (v shl 16) or (v shl 8) or v
    }
    Bitmap.createBitmap(px, side, side, Bitmap.Config.ARGB_8888).asImageBitmap()
}

@Composable
fun EmberBackdrop(
    variant: Backdrop,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    // The light drifts slowly. It is read only in the draw phase, so nothing recomposes.
    val drift: State<Float> = if (LocalReducedMotion.current) {
        remember { mutableFloatStateOf(0.5f) }
    } else {
        rememberInfiniteTransition(label = "backdrop").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(11_000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "drift",
        )
    }
    val grainBrush = remember { ShaderBrush(ImageShader(grain, TileMode.Repeated, TileMode.Repeated)) }
    Box(
        modifier
            .fillMaxSize()
            .background(Ember.Coal)
            .drawBehind {
                when (variant) {
                    Backdrop.Dawn -> dawn(drift.value)
                    Backdrop.Night -> night(drift.value, strength = 1f)
                    Backdrop.Dusk -> night(drift.value, strength = 0.55f)
                }
                drawRect(grainBrush)
            },
    ) {
        CompositionLocalProvider(LocalContentColor provides Ember.Cream, content = { content() })
    }
}

private fun DrawScope.dawn(d: Float) {
    val w = size.width
    val h = size.height
    drawRect(
        Brush.verticalGradient(
            0f to Color(0xFFE96F12),
            0.15f to Color(0xFFD8590C),
            0.34f to Color(0xFF9A3307),
            0.52f to Color(0xFF3D1505),
            0.70f to Ember.Coal,
            1f to Ember.Coal,
        ),
    )
    drawRect(
        Brush.radialGradient(
            listOf(Color(0x8CFFC27A), Color(0x00FFC27A)),
            center = Offset(w * (0.60f + 0.24f * d), h * (0.03f + 0.05f * d)),
            radius = w * 0.85f,
        ),
    )
    // Shade pooling from the left, so the headline sits on darker ground.
    drawRect(
        Brush.radialGradient(
            listOf(Color(0x99080503), Color(0x00080503)),
            center = Offset(w * (0.05f - 0.10f * d), h * 0.46f),
            radius = w * 0.95f,
        ),
    )
    drawRect(
        Brush.radialGradient(
            listOf(Color(0x6BB3200E), Color(0x00B3200E)),
            center = Offset(w * 0.5f, h * 1.12f),
            radius = w * 0.95f,
        ),
    )
}

private fun DrawScope.night(d: Float, strength: Float) {
    val w = size.width
    val h = size.height
    drawRect(
        Brush.radialGradient(
            0f to Color(0xFFF0780F).copy(alpha = 0.95f * strength),
            0.45f to Color(0xFFB8360A).copy(alpha = 0.62f * strength),
            1f to Color(0x00B8360A),
            center = Offset(w * (0.5f + 0.14f * (d - 0.5f)), -h * 0.07f),
            radius = w * 0.95f,
        ),
    )
    drawRect(
        Brush.radialGradient(
            listOf(Color(0xFFE8590C).copy(alpha = 0.52f * strength), Color(0x00E8590C)),
            center = Offset(-w * 0.22f, h * (0.40f + 0.07f * d)),
            radius = w * 0.75f,
        ),
    )
    drawRect(
        Brush.radialGradient(
            listOf(Color(0xFFE8590C).copy(alpha = 0.52f * strength), Color(0x00E8590C)),
            center = Offset(w * 1.22f, h * (0.52f - 0.07f * d)),
            radius = w * 0.75f,
        ),
    )
    drawRect(
        Brush.radialGradient(
            listOf(Color(0xFFB3200E).copy(alpha = 0.34f * strength), Color(0x00B3200E)),
            center = Offset(w * 0.5f, h * 1.15f),
            radius = w * 0.9f,
        ),
    )
}
