package com.nexus.ai.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import com.nexus.ai.voice.VoicePhase
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Animated orb. Listening: real mic level. Speaking: simulated pulse. Thinking: spinning ring. */
@Composable
fun NexusOrb(phase: VoicePhase, level: Float, modifier: Modifier = Modifier) {
    val inf = rememberInfiniteTransition(label = "orb")
    val t by inf.animateFloat(
        0f, (2 * PI).toFloat(),
        infiniteRepeatable(tween(4000, easing = LinearEasing)), label = "t"
    )
    val pulse by inf.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(700, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "p"
    )
    val lv by animateFloatAsState(level, spring(stiffness = Spring.StiffnessMedium), label = "lv")

    val (midT, edgeT) = when (phase) {
        VoicePhase.LISTENING -> Color(0xFF8FE9FF) to Color(0xFF3D7BFF)
        VoicePhase.THINKING -> Color(0xFFFFC2F0) to Color(0xFFB04DFF)
        VoicePhase.SPEAKING -> Color(0xFFD7C8FF) to Color(0xFF7B5CFF)
        VoicePhase.IDLE -> Color(0xFFC9BFFF) to Color(0xFF5B4BD6)
    }
    val mid by animateColorAsState(midT, tween(500), label = "mid")
    val edge by animateColorAsState(edgeT, tween(500), label = "edge")

    val amp = when (phase) {
        VoicePhase.LISTENING -> 0.04f + lv * 0.22f
        VoicePhase.SPEAKING -> 0.05f + lv * 0.22f + 0.02f * pulse
        VoicePhase.THINKING -> 0.03f
        VoicePhase.IDLE -> 0.02f + 0.01f * pulse
    }

    Canvas(modifier) {
        val c = center
        val base = size.minDimension * 0.26f * (1f + if (phase == VoicePhase.LISTENING || phase == VoicePhase.SPEAKING) lv * 0.15f else 0f)

        drawCircle(
            brush = Brush.radialGradient(listOf(edge.copy(alpha = 0.40f), Color.Transparent), center = c, radius = base * 2.3f),
            radius = base * 2.3f, center = c
        )

        val path = Path()
        val n = 90
        for (i in 0..n) {
            val a = i / n.toFloat() * 2f * PI.toFloat()
            val r = base * (1f + amp * sin(a * 3f + t * 2f) + amp * 0.6f * sin(a * 5f - t * 3f))
            val x = c.x + r * cos(a)
            val y = c.y + r * sin(a)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        drawPath(
            path,
            Brush.radialGradient(
                listOf(Color.White, mid, edge),
                center = Offset(c.x - base * 0.3f, c.y - base * 0.3f),
                radius = base * 1.6f
            )
        )

        if (phase == VoicePhase.THINKING) {
            val ring = base * 1.35f
            drawArc(
                color = mid, startAngle = t * 57.3f * 2f, sweepAngle = 100f, useCenter = false,
                topLeft = Offset(c.x - ring, c.y - ring), size = Size(ring * 2, ring * 2),
                style = Stroke(width = 8f, cap = StrokeCap.Round)
            )
        }
    }
}
