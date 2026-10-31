package com.sedatcanli.opticalvlc.rx

import android.os.SystemClock
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy

data class LumaFrame(
    val timeNs: Long,
    val rotationDegrees: Int,
    val spot: BrightSpotDetector.Result
)

/**
 * CameraX analyzer that reduces each YUV frame to one luma sample of the
 * tracked bright spot. It only touches the Y plane, so no YUV->RGB
 * conversion or bitmap allocation ever happens on the camera thread.
 */
class LumaAnalyzer(
    private val onFrame: (LumaFrame) -> Unit
) : ImageAnalysis.Analyzer {

    private val detector = BrightSpotDetector()

    override fun analyze(image: ImageProxy) {
        try {
            val plane = image.planes[0]
            val result = detector.analyze(
                buffer = plane.buffer,
                width = image.width,
                height = image.height,
                rowStride = plane.rowStride,
                pixelStride = plane.pixelStride
            )
            val timestamp = image.imageInfo.timestamp.takeIf { it > 0L }
                ?: SystemClock.elapsedRealtimeNanos()
            onFrame(LumaFrame(timestamp, image.imageInfo.rotationDegrees, result))
        } catch (_: Throwable) {
            // A single bad frame must never kill the analysis pipeline.
        } finally {
            image.close()
        }
    }

    fun reset() = detector.reset()
}
