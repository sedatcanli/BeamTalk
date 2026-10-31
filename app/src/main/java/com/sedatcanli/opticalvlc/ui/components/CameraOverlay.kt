package com.sedatcanli.opticalvlc.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.sedatcanli.opticalvlc.ui.OverlayInfo
import com.sedatcanli.opticalvlc.ui.theme.NeonCyan
import com.sedatcanli.opticalvlc.ui.theme.SignalAmber
import kotlin.math.max
import kotlin.math.min

/**
 * Draws the tracked ROI and blob centroid on top of the CameraX preview.
 *
 * CameraX preview uses FILL_CENTER, so the same aspect-fill transform is
 * applied here (including sensor rotation) to keep the box glued to the
 * physical light source.
 */
@Composable
fun CameraOverlay(
    info: OverlayInfo?,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        if (info == null) return@Canvas

        val imageWidth = info.imageWidth.toFloat().coerceAtLeast(1f)
        val imageHeight = info.imageHeight.toFloat().coerceAtLeast(1f)
        val rotation = ((info.rotationDegrees % 360) + 360) % 360
        val rotatedWidth = if (rotation == 90 || rotation == 270) imageHeight else imageWidth
        val rotatedHeight = if (rotation == 90 || rotation == 270) imageWidth else imageHeight

        val scale = max(size.width / rotatedWidth, size.height / rotatedHeight)
        val offsetX = (size.width - rotatedWidth * scale) / 2f
        val offsetY = (size.height - rotatedHeight * scale) / 2f

        fun map(x: Float, y: Float): Offset {
            val rx: Float
            val ry: Float
            when (rotation) {
                90 -> {
                    rx = imageHeight - y
                    ry = x
                }
                180 -> {
                    rx = imageWidth - x
                    ry = imageHeight - y
                }
                270 -> {
                    rx = y
                    ry = imageWidth - x
                }
                else -> {
                    rx = x
                    ry = y
                }
            }
            return Offset(offsetX + rx * scale, offsetY + ry * scale)
        }

        val topLeft = map(info.roiLeft.toFloat(), info.roiTop.toFloat())
        val bottomRight = map(info.roiRight.toFloat(), info.roiBottom.toFloat())
        val left = min(topLeft.x, bottomRight.x)
        val top = min(topLeft.y, bottomRight.y)
        val width = kotlin.math.abs(bottomRight.x - topLeft.x)
        val height = kotlin.math.abs(bottomRight.y - topLeft.y)

        drawRect(
            color = NeonCyan.copy(alpha = 0.9f),
            topLeft = Offset(left, top),
            size = Size(width, height),
            style = Stroke(width = 2.dp.toPx())
        )

        val centroid = map(info.centroidX, info.centroidY)
        drawCircle(
            color = SignalAmber.copy(alpha = 0.35f),
            radius = 12.dp.toPx(),
            center = centroid
        )
        drawCircle(
            color = SignalAmber,
            radius = 3.dp.toPx(),
            center = centroid
        )
        drawLine(
            color = NeonCyan.copy(alpha = 0.5f),
            start = Offset(centroid.x - 14.dp.toPx(), centroid.y),
            end = Offset(centroid.x + 14.dp.toPx(), centroid.y),
            strokeWidth = 1.dp.toPx()
        )
        drawLine(
            color = NeonCyan.copy(alpha = 0.5f),
            start = Offset(centroid.x, centroid.y - 14.dp.toPx()),
            end = Offset(centroid.x, centroid.y + 14.dp.toPx()),
            strokeWidth = 1.dp.toPx()
        )

        // Keep the canvas background transparent.
        drawRect(color = Color.Transparent, topLeft = Offset.Zero, size = size)
    }
}

/** Rect helper kept for future overlay extensions. */
fun roiRect(info: OverlayInfo): Rect = Rect(
    left = info.roiLeft.toFloat(),
    top = info.roiTop.toFloat(),
    right = info.roiRight.toFloat(),
    bottom = info.roiBottom.toFloat()
)
