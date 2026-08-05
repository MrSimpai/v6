package com.example.medtap.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas

/**
 * Bridges the shared android.graphics drawing into Compose, so the dragon on screen and
 * the dragon in the notification are the same code path and can never drift apart.
 *
 * Signature element: the rings, breathing outward while a dose is owed -- gone the
 * instant it is logged, whether by a scan or by the button.
 */
@Composable
fun Mascot(
    mood: Mood,
    modifier: Modifier = Modifier,
    worn: Set<String> = emptySet(),
    silhouette: Boolean = false
) {
    val t = rememberInfiniteTransition(label = "dragon")
    val phase by t.animateFloat(
        0f, 1f, infiniteRepeatable(tween(2600, easing = LinearEasing)), label = "bob"
    )
    val ring by t.animateFloat(
        0f, 1f, infiniteRepeatable(tween(2200, easing = LinearEasing)), label = "ring"
    )

    Canvas(modifier) {
        if (mood == Mood.Waiting || mood == Mood.Overdue) {
            tapRings(ring, if (mood == Mood.Overdue) Color(0xFFA21E50) else Color(0xFFC03765))
        }
        drawIntoCanvas { canvas ->
            val nc = canvas.nativeCanvas
            val s = size.minDimension
            nc.save()
            nc.translate((size.width - s) / 2f, (size.height - s) / 2f)
            Dragon.draw(nc, mood, s, phase, worn, silhouette)
            nc.restore()
        }
    }
}

private fun DrawScope.tapRings(phase: Float, color: Color) {
    repeat(3) { i ->
        val p = (phase + i / 3f) % 1f
        drawCircle(
            color = color.copy(alpha = (1f - p) * 0.30f),
            radius = size.minDimension * (0.30f + p * 0.34f),
            center = Offset(size.width / 2f, size.height / 2f),
            style = Stroke(width = 3f + (1f - p) * 4f)
        )
    }
}
