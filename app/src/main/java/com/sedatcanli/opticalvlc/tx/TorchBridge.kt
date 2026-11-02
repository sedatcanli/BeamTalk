package com.sedatcanli.opticalvlc.tx

import androidx.camera.core.CameraControl

/**
 * Lets the transmitter drive the torch through the CameraX camera that is
 * already open in the receiver. [android.hardware.camera2.CameraManager] refuses
 * to toggle the torch while another camera client (our own preview/analysis)
 * owns the device, so we fall back to the active camera's torch control.
 */
object TorchBridge {

    @Volatile
    private var activeControl: CameraControl? = null

    fun attach(control: CameraControl) {
        activeControl = control
    }

    fun detach(control: CameraControl?) {
        if (control == null || activeControl === control) {
            activeControl = null
        }
    }

    fun isReady(): Boolean = activeControl != null

    fun setTorch(on: Boolean): Boolean {
        val control = activeControl ?: return false
        return try {
            control.enableTorch(on)
            true
        } catch (_: Exception) {
            false
        }
    }
}
