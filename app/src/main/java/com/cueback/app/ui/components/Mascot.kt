package com.cueback.app.ui.components

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.annotation.DrawableRes
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.cueback.app.R
import com.cueback.app.ui.theme.Ember
import com.cueback.app.ui.theme.LocalReducedMotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Cue, the owl that keeps your place. Each pose is one render of the same character. */
enum class MascotPose(@DrawableRes val art: Int) {
    /** Floating, cloak flying: the portrait used for first impressions. */
    Hero(R.drawable.mascot_hero),
    Front(R.drawable.mascot_front),
    /** Three-quarter view: looking toward something. */
    Turn(R.drawable.mascot_turn),
    Side(R.drawable.mascot_side),
    Wave(R.drawable.mascot_wave),
    Think(R.drawable.mascot_think),
}

/**
 * Cue, alive: it hovers, breathes, glows, glances to the side now and then (by turning through the
 * three-quarter render), leans with the way the phone is held, and hops and waves when tapped.
 * With reduced motion on it is a still picture.
 */
@Composable
fun Mascot(
    pose: MascotPose,
    modifier: Modifier = Modifier,
    size: Dp = 160.dp,
    glow: Boolean = true,
    contentDescription: String? = null,
) {
    val reduced = LocalReducedMotion.current
    val scope = rememberCoroutineScope()
    val hop = remember { Animatable(0f) }
    var greeting by remember { mutableStateOf(false) }
    val tilt = rememberTilt(enabled = !reduced && size >= 120.dp)

    val idle = if (reduced) null else rememberInfiniteTransition(label = "mascot")
    val bob = idle?.animateFloat(-1f, 1f, infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "bob")
    val pulse = idle?.animateFloat(0f, 1f, infiniteRepeatable(tween(1900, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "pulse")
    val clock = idle?.animateFloat(0f, 1f, infiniteRepeatable(tween(9000, easing = LinearEasing), RepeatMode.Restart), label = "glance")
    val glancing by remember(clock) { derivedStateOf { (clock?.value ?: 0f) in 0.60f..0.69f } }

    val shown = when {
        greeting -> MascotPose.Wave
        glancing && pose == MascotPose.Front -> MascotPose.Turn
        glancing && pose == MascotPose.Turn -> MascotPose.Side
        else -> pose
    }

    Box(
        modifier
            .size(size)
            .drawBehind {
                if (!glow) return@drawBehind
                val r = this.size.minDimension
                val p = pulse?.value ?: 0.5f
                val lift = bob?.value ?: 0f
                // The halo slides the opposite way to the owl when the phone tilts, which reads as depth.
                val haloCenter = center - Offset(tilt.value.x * r * 0.04f, tilt.value.y * r * 0.04f)
                drawCircle(
                    Brush.radialGradient(
                        0f to Ember.Flame.copy(alpha = 0.40f + 0.16f * p),
                        0.55f to Ember.Rust.copy(alpha = 0.20f),
                        1f to Color.Transparent,
                        center = haloCenter,
                        radius = r * 0.80f,
                    ),
                    radius = r * 0.80f,
                    center = haloCenter,
                )
                // Light pooled on the ground. It tightens and dims as the owl rises.
                val floor = Offset(center.x, r * 0.99f)
                val reach = r * (0.40f - 0.05f * lift)
                scale(scaleX = 1f, scaleY = 0.20f, pivot = floor) {
                    drawCircle(
                        Brush.radialGradient(
                            listOf(Ember.Glow.copy(alpha = 0.55f - 0.14f * lift), Color.Transparent),
                            center = floor,
                            radius = reach,
                        ),
                        radius = reach,
                        center = floor,
                    )
                }
            }
            .pointerInput(reduced) {
                if (reduced) return@pointerInput
                detectTapGestures {
                    scope.launch {
                        greeting = true
                        hop.animateTo(-0.10f, tween(150, easing = FastOutSlowInEasing))
                        hop.animateTo(0f, spring(dampingRatio = 0.36f, stiffness = 420f))
                        delay(900)
                        greeting = false
                    }
                }
            },
    ) {
        Crossfade(shown, animationSpec = tween(170), label = "pose") { p ->
            Image(
                painterResource(p.art),
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    val lift = bob?.value ?: 0f
                    val breathe = 1f + 0.014f * (pulse?.value ?: 0f)
                    translationY = (lift * 0.030f + hop.value) * this.size.height
                    translationX = tilt.value.x * this.size.width * 0.04f
                    rotationZ = lift * 1.3f + tilt.value.x * 3f
                    scaleX = breathe
                    scaleY = breathe
                },
            )
        }
    }
}

/** Cue's face, still: for avatars and chat lines. */
@Composable
fun MascotAvatar(modifier: Modifier = Modifier, size: Dp = 28.dp) {
    Image(painterResource(R.drawable.mascot_head), contentDescription = null, modifier = modifier.size(size))
}

/** Cue saying one short thing, the way a coach would. */
@Composable
fun MascotSays(text: String, modifier: Modifier = Modifier, pose: MascotPose = MascotPose.Front, size: Dp = 68.dp) {
    Row(modifier, verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Mascot(pose, size = size)
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = Ember.Cream,
            modifier = Modifier
                .weight(1f, fill = false)
                .glass(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomEnd = 20.dp, bottomStart = 6.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp),
        )
    }
}

/**
 * How the phone is tilted relative to how it was held when the screen opened, smoothed, in -1..1.
 * Read only in draw and layer blocks, so it never recomposes anything. Listens only while resumed.
 */
@Composable
private fun rememberTilt(enabled: Boolean): State<Offset> {
    val state = remember { mutableStateOf(Offset.Zero) }
    if (!enabled) return state
    val ctx = LocalContext.current
    LifecycleResumeEffect(Unit) {
        val manager = ctx.getSystemService(SensorManager::class.java)
        val sensor = manager?.getDefaultSensor(Sensor.TYPE_GRAVITY) ?: manager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        var restX = Float.NaN
        var restY = Float.NaN
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                val x = e.values[0] / SensorManager.GRAVITY_EARTH
                val y = e.values[1] / SensorManager.GRAVITY_EARTH
                if (restX.isNaN()) {
                    restX = x
                    restY = y
                }
                // The resting angle follows slowly, so however you settle becomes neutral again.
                restX += (x - restX) * 0.01f
                restY += (y - restY) * 0.01f
                val targetX = (-(x - restX) * 2.4f).coerceIn(-1f, 1f)
                val targetY = ((y - restY) * 2.4f).coerceIn(-1f, 1f)
                val cur = state.value
                state.value = Offset(cur.x + (targetX - cur.x) * 0.12f, cur.y + (targetY - cur.y) * 0.12f)
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        if (sensor != null) manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        onPauseOrDispose { manager?.unregisterListener(listener) }
    }
    return state
}
