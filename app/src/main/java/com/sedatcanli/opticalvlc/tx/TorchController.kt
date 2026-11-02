package com.sedatcanli.opticalvlc.tx

import android.content.Context
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.util.Log

/**
 * Thin wrapper around [CameraManager.setTorchMode].
 *
 * The torch camera is not always the same id as the default back camera, so
 * we scan the camera list for a device that actually reports a flash unit.
 */
class TorchController(context: Context) {

    private val cameraManager =
        context.applicationContext.getSystemService(Context.CAMERA_SERVICE) as CameraManager

    val torchCameraId: String?
        get() {
            if (!_torchInitialized) {
                _torchCameraId = findTorchCameraId()
                _torchInitialized = true
            }
            return _torchCameraId
        }

    fun refreshTorchCameraId() {
        _torchInitialized = false
        _torchCameraId = null
        _torchCameraId = findTorchCameraId()
        _torchInitialized = true
    }

    val isAvailable: Boolean get() = torchCameraId != null

    private var lastState = false
    private var _torchCameraId: String? = null
    private var _torchInitialized = false

    /** Son flaş hatası (ui'da göstermek için). */
    var lastError: String? = null
        private set

    @Synchronized
    fun setTorch(on: Boolean): Boolean {
        lastError = null
        if (TorchBridge.isReady() && TorchBridge.setTorch(on)) {
            lastState = on
            return true
        }
        val id = torchCameraId ?: return false
        try {
            cameraManager.setTorchMode(id, on)
            lastState = on
            return true
        } catch (e: CameraAccessException) {
            if (bridgeFallback(on)) return true
            lastError = "Flaş erişim hatası: ${e.message}"
            Log.w(TAG, lastError, e)
            return false
        } catch (e: IllegalArgumentException) {
            if (bridgeFallback(on)) return true
            lastError = "Flaş desteklenmiyor: ${e.message}"
            Log.w(TAG, lastError, e)
            return false
        } catch (e: SecurityException) {
            if (bridgeFallback(on)) return true
            lastError = "Kamera izni eksik: ${e.message}"
            Log.w(TAG, lastError, e)
            return false
        } catch (e: RuntimeException) {
            if (bridgeFallback(on)) return true
            lastError = "Flaş çalışma hatası: ${e.message}"
            Log.w(TAG, lastError, e)
            return false
        }
    }

    private fun bridgeFallback(on: Boolean): Boolean {
        if (!TorchBridge.setTorch(on)) return false
        lastState = on
        lastError = null
        return true
    }

    @Synchronized
    fun forceOff() {
        setTorch(false)
    }

    private fun findTorchCameraId(): String? {
        return try {
            val ids = cameraManager.cameraIdList
            var fallback: String? = null
            for (id in ids) {
                val characteristics = cameraManager.getCameraCharacteristics(id)
                val hasFlash = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) ?: false
                if (!hasFlash) continue
                val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
                if (facing == CameraCharacteristics.LENS_FACING_BACK) return id
                if (fallback == null) fallback = id
            }
            fallback
        } catch (e: CameraAccessException) {
            Log.w(TAG, "Cannot enumerate cameras", e)
            null
        } catch (e: SecurityException) {
            null
        }
    }

    private companion object {
        const val TAG = "TorchController"
    }
}
