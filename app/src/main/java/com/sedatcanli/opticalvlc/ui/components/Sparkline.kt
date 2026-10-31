package com.sedatcanli.opticalvlc.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.sedatcanli.opticalvlc.ui.theme.InkRaised
import com.sedatcanli.opticalvlc.ui.theme.NeonCyan
import com.sedatcanli.opticalvlc.ui.theme.OutlineDim
import com.sedatcanli.opticalvlc.ui.theme.SignalAmber

@Composable
fun LumaSparkline(
    values: FloatArray,
    threshold: Float,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(96.dp)
            .clip(RoundedCornerShape(12.dp))
    ) {
        drawRect(color = InkRaised)

        val gridColor = OutlineDim.copy(alpha = 0.55f)
        for (i in 1..3) {
            val y = size.height * i / 4f
            drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
        }

        if (values.size >= 2) {
            val path = Path()
            val maxIndex = values.size - 1
            values.forEachIndexed { index, value ->
                val x = size.width * index / maxIndex
                val normalized = (value / 255f).coerceIn(0f, 1f)
                val y = size.height * (1f - normalized)
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(
                path = path,
                color = NeonCyan,
                style = Stroke(
                    width = 2.dp.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
        }

        if (threshold > 0f) {
            val normalized = (threshold / 255f).coerceIn(0f, 1f)
            val y = size.height * (1f - normalized)
            drawLine(
                color = SignalAmber.copy(alpha = 0.9f),
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))
            )
        }

        drawRect(
            color = Color.Transparent,
            size = size
        )
    }
}
