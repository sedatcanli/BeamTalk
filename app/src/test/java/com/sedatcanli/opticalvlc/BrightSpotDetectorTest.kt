package com.sedatcanli.opticalvlc

import com.sedatcanli.opticalvlc.rx.BrightSpotDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer

class BrightSpotDetectorTest {

    private fun plane(
        width: Int,
        height: Int,
        background: Int,
        blobCenterX: Int? = null,
        blobCenterY: Int? = null,
        blobSize: Int = 12,
        blobValue: Int = 250
    ): ByteBuffer {
        val buffer = ByteBuffer.allocateDirect(width * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                var value = background
                if (blobCenterX != null && blobCenterY != null) {
                    val half = blobSize / 2
                    if (x in (blobCenterX - half)..(blobCenterX + half) &&
                        y in (blobCenterY - half)..(blobCenterY + half)
                    ) {
                        value = blobValue
                    }
                }
                buffer.put(y * width + x, value.toByte())
            }
        }
        return buffer
    }

    @Test
    fun findsBrightBlobAndRoi() {
        val width = 640
        val height = 480
        val detector = BrightSpotDetector()
        val result = detector.analyze(
            plane(width, height, background = 18, blobCenterX = 420, blobCenterY = 160),
            width, height, width, 1
        )

        assertEquals(250, result.peakLuma)
        assertTrue("ROI parlaklığı düşük: ${result.roiMean}", result.roiMean > 200f)
        assertEquals(420f, result.centroidX, 6f)
        assertEquals(160f, result.centroidY, 6f)
        assertTrue(result.roiLeft < 420 && result.roiRight > 420)
        assertTrue(result.roiTop < 160 && result.roiBottom > 160)
    }

    @Test
    fun darkFrameHasLowSignal() {
        val width = 640
        val height = 480
        val detector = BrightSpotDetector()
        val result = detector.analyze(
            plane(width, height, background = 20),
            width, height, width, 1
        )

        assertTrue(result.roiMean < 60f)
        assertEquals(20, result.peakLuma)
    }

    @Test
    fun blobTurnsOffAndOnAgain() {
        val width = 320
        val height = 240
        val detector = BrightSpotDetector()
        val on = detector.analyze(
            plane(width, height, 16, blobCenterX = 200, blobCenterY = 120),
            width, height, width, 1
        )
        val off = detector.analyze(
            plane(width, height, 16),
            width, height, width, 1
        )
        val onAgain = detector.analyze(
            plane(width, height, 16, blobCenterX = 200, blobCenterY = 120),
            width, height, width, 1
        )

        assertTrue(on.roiMean > 180f)
        assertTrue(off.roiMean < 60f)
        assertTrue(onAgain.roiMean > 180f)
        assertTrue("ROI karanlıkta merkezde kalmalı", off.centroidX > 150f)
    }

    @Test
    fun supportsRowStridePadding() {
        val width = 64
        val height = 48
        val rowStride = 96
        val buffer = ByteBuffer.allocateDirect(rowStride * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                buffer.put(y * rowStride + x, 20.toByte())
            }
        }
        for (y in 20 until 30) {
            for (x in 30 until 40) {
                buffer.put(y * rowStride + x, 240.toByte())
            }
        }
        val result = BrightSpotDetector().analyze(
            buffer, width, height, rowStride, 1
        )
        assertTrue(result.roiMean > 150f)
        assertEquals(34f, result.centroidX, 5f)
        assertEquals(24f, result.centroidY, 5f)
    }
}
