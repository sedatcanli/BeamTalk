package com.sedatcanli.opticalvlc.rx

import java.nio.ByteBuffer

/**
 * Extracts the transmitter's light blob from a YUV_420_888 luma plane.
 *
 * Two passes over a downsampled grid:
 *  1. find the peak luma and the ambient mean,
 *  2. find the centroid + bounding box of the brightest pixels.
 *
 * The ROI is re-centred on the blob every frame, which keeps the signal high
 * even when the transmitter is 5-10 m away and only a few pixels wide.
 */
class BrightSpotDetector {

    data class Result(
        val roiMean: Float,
        val peakLuma: Int,
        val ambientMean: Float,
        val centroidX: Float,
        val centroidY: Float,
        val roiLeft: Int,
        val roiTop: Int,
        val roiRight: Int,
        val roiBottom: Int,
        val imageWidth: Int,
        val imageHeight: Int
    ) {
        val roiWidth: Int get() = roiRight - roiLeft
        val roiHeight: Int get() = roiBottom - roiTop
    }

    private var lastCx = -1f
    private var lastCy = -1f
    private var lastRoiW = 0
    private var lastRoiH = 0

    fun analyze(
        buffer: ByteBuffer,
        width: Int,
        height: Int,
        rowStride: Int,
        pixelStride: Int
    ): Result {
        val step = when {
            width * height <= 400_000 -> 2
            width * height <= 1_200_000 -> 3
            else -> 4
        }
        val base = buffer.position()

        var peak = 0
        var sum = 0L
        var count = 0
        var y = 0
        while (y < height) {
            var x = 0
            val rowOffset = base + y * rowStride
            while (x < width) {
                val index = rowOffset + x * pixelStride
                if (index < buffer.limit()) {
                    val value = buffer.get(index).toInt() and 0xFF
                    if (value > peak) peak = value
                    sum += value
                    count++
                }
                x += step
            }
            y += step
        }

        if (count == 0) {
            return emptyResult(width, height)
        }
        val ambient = (sum.toDouble() / count).toFloat()
        val hotThreshold = maxOf(peak * 0.80f, ambient + 25f)

        var hotCount = 0
        var cxSum = 0.0
        var cySum = 0.0
        var minX = width
        var minY = height
        var maxX = 0
        var maxY = 0

        y = 0
        while (y < height) {
            var x = 0
            val rowOffset = base + y * rowStride
            while (x < width) {
                val index = rowOffset + x * pixelStride
                if (index < buffer.limit()) {
                    val value = buffer.get(index).toInt() and 0xFF
                    if (value >= hotThreshold) {
                        hotCount++
                        cxSum += x
                        cySum += y
                        if (x < minX) minX = x
                        if (y < minY) minY = y
                        if (x > maxX) maxX = x
                        if (y > maxY) maxY = y
                    }
                }
                x += step
            }
            y += step
        }

        val blobFound = hotCount >= 2 && peak >= maxOf(60f, ambient + 18f)
        if (blobFound) {
            lastCx = (cxSum / hotCount).toFloat()
            lastCy = (cySum / hotCount).toFloat()
            lastRoiW = (maxX - minX).coerceAtLeast(width / 24)
            lastRoiH = (maxY - minY).coerceAtLeast(height / 18)
        }

        val cx = if (lastCx >= 0f) lastCx else width / 2f
        val cy = if (lastCy >= 0f) lastCy else height / 2f
        val baseW = if (lastRoiW > 0) lastRoiW else width / 10
        val baseH = if (lastRoiH > 0) lastRoiH else height / 8

        val halfW = maxOf(10, (baseW * 0.85f).toInt())
        val halfH = maxOf(8, (baseH * 0.85f).toInt())
        val left = (cx - halfW).toInt().coerceIn(0, width - 1)
        val top = (cy - halfH).toInt().coerceIn(0, height - 1)
        val right = (cx + halfW).toInt().coerceIn(left + 1, width)
        val bottom = (cy + halfH).toInt().coerceIn(top + 1, height)

        var roiSum = 0.0
        var roiCount = 0
        var hotRoiSum = 0.0
        var hotRoiCount = 0
        var ry = top
        while (ry < bottom) {
            var rx = left
            val rowOffset = base + ry * rowStride
            while (rx < right) {
                val index = rowOffset + rx * pixelStride
                if (index < buffer.limit()) {
                    val value = buffer.get(index).toInt() and 0xFF
                    roiSum += value
                    roiCount++
                    if (value >= hotThreshold) {
                        hotRoiSum += value
                        hotRoiCount++
                    }
                }
                rx += 1
            }
            ry += 1
        }
        // Average only the blob's hottest pixels, otherwise a distant (small)
        // light source would be diluted by the dark ROI background.
        val roiMean = when {
            hotRoiCount > 0 -> (hotRoiSum / hotRoiCount).toFloat()
            roiCount > 0 -> (roiSum / roiCount).toFloat()
            else -> 0f
        }

        return Result(
            roiMean = roiMean,
            peakLuma = peak,
            ambientMean = ambient,
            centroidX = cx,
            centroidY = cy,
            roiLeft = left,
            roiTop = top,
            roiRight = right,
            roiBottom = bottom,
            imageWidth = width,
            imageHeight = height
        )
    }

    fun reset() {
        lastCx = -1f
        lastCy = -1f
        lastRoiW = 0
        lastRoiH = 0
    }

    private fun emptyResult(width: Int, height: Int) = Result(
        roiMean = 0f,
        peakLuma = 0,
        ambientMean = 0f,
        centroidX = width / 2f,
        centroidY = height / 2f,
        roiLeft = width / 4,
        roiTop = height / 4,
        roiRight = width * 3 / 4,
        roiBottom = height * 3 / 4,
        imageWidth = width,
        imageHeight = height
    )
}
