package com.hassan.zensposed.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

data class RingSegment(val value: Float, val color: Color, val label: String)

@Composable
fun RingChart(
    segments: List<RingSegment>,
    modifier: Modifier = Modifier,
    ringSize: Int = 220,
    strokeWidth: Float = 46f,
    center: @Composable () -> Unit = {}
) {
    val total = segments.sumOf { it.value.toDouble() }.toFloat().coerceAtLeast(0.0001f)
    val gapDegrees = if (segments.size > 1) 3f else 0f
    val available = 360f - gapDegrees * segments.size

    Box(modifier = modifier.size(ringSize.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(ringSize.dp)) {
            var startAngle = -90f
            if (segments.isEmpty()) {
                drawArc(
                    color = Color(0xFFE2E8F0),
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            } else {
                segments.forEach { seg ->
                    val sweep = (seg.value / total) * available
                    drawArc(
                        color = seg.color,
                        startAngle = startAngle,
                        sweepAngle = sweep,
                        useCenter = false,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )
                    startAngle += sweep + gapDegrees
                }
            }
        }
        center()
    }
}
