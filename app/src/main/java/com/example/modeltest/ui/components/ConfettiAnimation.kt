package com.example.modeltest.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private data class ConfettiParticle(
    val x: Float,
    val y: Float,
    val color: Color,
    val size: Float,
    val angle: Float,
    val speed: Float,
    val rotation: Float
)

@Composable
fun ConfettiAnimation(
    trigger: Boolean,
    modifier: Modifier = Modifier
) {
    if (!trigger) return

    val particles = remember {
        List(100) {
            ConfettiParticle(
                x = Random.nextFloat(),
                y = -Random.nextFloat() * 0.3f,
                color = listOf(
                    Color(0xFFE91E63), Color(0xFF9C27B0), Color(0xFF2196F3),
                    Color(0xFF4CAF50), Color(0xFFFFEB3B), Color(0xFFFF9800),
                    Color(0xFF00BCD4), Color(0xFFFF5722)
                ).random(),
                size = Random.nextFloat() * 12f + 6f,
                angle = Random.nextFloat() * 360f,
                speed = Random.nextFloat() * 2f + 1f,
                rotation = Random.nextFloat() * 360f
            )
        }
    }

    val progress = remember { Animatable(0f) }

    LaunchedEffect(trigger) {
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 3000, easing = LinearEasing)
        )
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        particles.forEach { p ->
            val currentProgress = progress.value
            val x = p.x * w + sin(Math.toRadians(p.angle.toDouble())).toFloat() * w * 0.1f
            val y = p.y * h + currentProgress * h * 1.2f * p.speed
            val alpha = (1f - currentProgress).coerceIn(0f, 1f)

            if (y < h) {
                drawCircle(
                    color = p.color.copy(alpha = alpha),
                    radius = p.size / 2,
                    center = Offset(x, y)
                )
            }
        }
    }
}

fun showConfetti(viewGroup: android.view.ViewGroup) {
    // No-op since we use Compose-based confetti now
}