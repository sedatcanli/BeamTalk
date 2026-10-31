package com.sedatcanli.opticalvlc.rx

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.util.Log

/**
 * Queries the camera stack for the options the receiver UI exposes:
 * a possible telephoto lens and the supported exposure compensation range.
 */
object CameraSupport {

    data class Info(
        val telephotoCameraId: String? = null,
        val minExposureCompensation: Int = 0,
        val maxExposureCompensation: Int = 0,
        val backCameraCount: Int = 0
    ) {
        val hasTelephoto: Boolean get() = telephotoCameraId != null
    }

    fun probe(context: Context): Info {
        val manager = context.applicationContext
            .getSystemService(Context.CAMERA_SERVICE) as? CameraManager
            ?: return Info()
        return try {
            val ids = manager.cameraIdList
            var defaultBackId: String? = null
            var defaultBackFocal = 0f
            var maxFocalId: String? = null
            var maxFocal = 0f
            var backCount = 0

            for (id in ids) {
                val characteristics = manager.getCameraCharacteristics(id)
                val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
                if (facing != CameraCharacteristics.LENS_FACING_BACK) continue
                backCount++
                val focalLengths =
                    characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
                        ?: continue
                val primaryFocal = focalLengths.minOrNull() ?: continue
                if (defaultBackId == null) {
                    defaultBackId = id
                    defaultBackFocal = primaryFocal
                }
                if (primaryFocal > maxFocal) {
                    maxFocal = primaryFocal
                    maxFocalId = id
                }
            }

            val telephotoId = if (
                maxFocalId != null &&
                defaultBackId != null &&
                maxFocalId != defaultBackId &&
                defaultBackFocal > 0f &&
                maxFocal > defaultBackFocal * 1.35f
            ) {
                maxFocalId
            } else {
                null
            }

            var minExposure = 0
            var maxExposure = 0
            if (defaultBackId != null) {
                val characteristics = manager.getCameraCharacteristics(defaultBackId)
                val range = characteristics.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE)
                if (range != null) {
                    minExposure = range.lower
                    maxExposure = range.upper
                }
            }

            Info(
                telephotoCameraId = telephotoId,
                minExposureCompensation = minExposure,
                maxExposureCompensation = maxExposure,
                backCameraCount = backCount
            )
        } catch (e: Exception) {
            Log.w("CameraSupport", "Camera probe failed", e)
            Info()
        }
    }
}
