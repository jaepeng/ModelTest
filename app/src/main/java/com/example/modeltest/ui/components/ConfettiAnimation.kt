package com.example.modeltest.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.ui.viewinterop.AndroidView
import com.github.jinatonic.confetti.ConfettiManager
import com.github.jinatonic.confetti.ConfettiSource
import com.github.jinatonic.confetti.ConfettoGenerator
import com.github.jinatonic.confetti.confetto.BitmapConfetto
import android.graphics.Color as AndroidColor

@Composable
fun ConfettiAnimation(
    trigger: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var hasTriggered by remember { mutableStateOf(false) }

    if (trigger && !hasTriggered) {
        hasTriggered = true
        Box(modifier = modifier.fillMaxSize()) {
            AndroidView(
                factory = { ctx ->
                    FrameLayout(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        val confettiSource = ConfettiSource(0, -50, width, -50)
                        val colors = listOf(
                            AndroidColor.parseColor("#FF6B6B"),
                            AndroidColor.parseColor("#4ECDC4"),
                            AndroidColor.parseColor("#45B7D1"),
                            AndroidColor.parseColor("#96CEB4"),
                            AndroidColor.parseColor("#FFEAA7"),
                            AndroidColor.parseColor("#DDA0DD")
                        )
                        ConfettiManager(ctx)
                            .setConfettiGenerator { random ->
                                BitmapConfetto.createBitmapConfetto(
                                    colors[random.nextInt(colors.size)],
                                    20f,
                                    10f,
                                    1f
                                )
                            }
                            .setVelocityX(0f, 50f)
                            .setVelocityY(500f, 200f)
                            .setRotationalVelocity(180f, 90f)
                            .setNumInitialCount(50)
                            .setTouchEnabled(false)
                            .setConfettiSource(confettiSource)
                            .setExplosion()
                            .setTTL(3000L)
                            .animate()
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }

    LaunchedEffect(trigger) {
        if (!trigger) {
            hasTriggered = false
        }
    }
}