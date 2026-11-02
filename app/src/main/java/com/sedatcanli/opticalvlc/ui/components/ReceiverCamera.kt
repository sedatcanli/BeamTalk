package com.sedatcanli.opticalvlc.ui.components

import android.hardware.camera2.CaptureRequest
import android.util.Size
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraFilter
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.sedatcanli.opticalvlc.rx.CameraSupport
import com.sedatcanli.opticalvlc.rx.LumaAnalyzer
import com.sedatcanli.opticalvlc.rx.LumaFrame
import com.sedatcanli.opticalvlc.rx.OpticalDemodulator
import com.sedatcanli.opticalvlc.tx.TorchBridge
import com.sedatcanli.opticalvlc.ui.OverlayInfo
import com.sedatcanli.opticalvlc.ui.theme.Ink
import com.sedatcanli.opticalvlc.ui.theme.NeonCyan
import com.sedatcanli.opticalvlc.ui.theme.SignalRed
import java.util.concurrent.Executors

/**
 * CameraX RX view: 640x480 YUV analysis at 30 fps with
 * `STRATEGY_KEEP_ONLY_LATEST`, optional telephoto lens, minimum exposure
 * compensation, infinity focus and optional AE lock.
 */
@Composable
fun ReceiverCameraBox(
    hasPermission: Boolean,
    telephoto: Boolean,
    minExposure: Boolean,
    aeLock: Boolean,
    minExposureCompensation: Int,
    overlayInfo: OverlayInfo?,
    phase: OpticalDemodulator.RxPhase,
    onFrame: (LumaFrame) -> Unit,
    onError: (String) -> Unit,
    modifier: Modifier = Modifier,
    restartKey: Int = 0
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    DisposableEffect(
        hasPermission,
        telephoto,
        minExposure,
        aeLock,
        minExposureCompensation,
        restartKey
    ) {
        if (!hasPermission) {
            return@DisposableEffect onDispose { }
        }

        val analysisExecutor = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "vlc-luma-analysis").apply { priority = Thread.NORM_PRIORITY }
        }
        var provider: ProcessCameraProvider? = null
        var analysis: ImageAnalysis? = null
        var boundControl: CameraControl? = null
        var disposed = false

        val providerFuture = ProcessCameraProvider.getInstance(context)
        val listener = Runnable {
            if (disposed) return@Runnable
            try {
                val cameraProvider = providerFuture.get()
                provider = cameraProvider

                val analysisBuilder = ImageAnalysis.Builder()
                    .setTargetResolution(Size(640, 480))
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)

                val extender = Camera2Interop.Extender(analysisBuilder)
                if (minExposure) {
                    extender.setCaptureRequestOption(
                        CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION,
                        minExposureCompensation
                    )
                    extender.setCaptureRequestOption(
                        CaptureRequest.CONTROL_AF_MODE,
                        CaptureRequest.CONTROL_AF_MODE_OFF
                    )
                    extender.setCaptureRequestOption(
                        CaptureRequest.LENS_FOCUS_DISTANCE,
                        0f
                    )
                    extender.setCaptureRequestOption(
                        CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE,
                        CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_OFF
                    )
                }
                if (aeLock) {
                    extender.setCaptureRequestOption(CaptureRequest.CONTROL_AE_LOCK, true)
                }

                val newAnalysis = analysisBuilder.build().also {
                    it.setAnalyzer(analysisExecutor, LumaAnalyzer(onFrame))
                }

                val preview = Preview.Builder()
                    .setTargetResolution(Size(640, 480))
                    .build()
                    .also { it.setSurfaceProvider(previewView.surfaceProvider) }

                cameraProvider.unbindAll()
                val selector = buildSelector(context, telephoto)
                val camera = try {
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        selector,
                        preview,
                        newAnalysis
                    )
                } catch (first: Exception) {
                    if (telephoto) {
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            newAnalysis
                        )
                    } else {
                        throw first
                    }
                }
                boundControl = camera.cameraControl
                TorchBridge.attach(camera.cameraControl)
                analysis = newAnalysis
            } catch (e: Exception) {
                onError(e.message ?: "Kamera başlatılamadı")
            }
        }

        providerFuture.addListener(listener, ContextCompat.getMainExecutor(context))

        onDispose {
            disposed = true
            TorchBridge.detach(boundControl)
            try {
                analysis?.clearAnalyzer()
            } catch (_: Throwable) {
            }
            try {
                provider?.unbindAll()
            } catch (_: Throwable) {
            }
            analysisExecutor.shutdown()
        }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Ink)
    ) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize()
        )
        CameraOverlay(info = overlayInfo, modifier = Modifier.fillMaxSize())

        val color = when (phase) {
            OpticalDemodulator.RxPhase.IDLE -> NeonCyan
            OpticalDemodulator.RxPhase.PREAMBLE_ACQUISITION -> com.sedatcanli.opticalvlc.ui.theme.SignalAmber
            OpticalDemodulator.RxPhase.READ_HEADER_AND_BODY -> com.sedatcanli.opticalvlc.ui.theme.SignalAmber
            OpticalDemodulator.RxPhase.CRC_CHECK -> com.sedatcanli.opticalvlc.ui.theme.SignalAmber
            OpticalDemodulator.RxPhase.DECODED -> com.sedatcanli.opticalvlc.ui.theme.SignalGreen
            OpticalDemodulator.RxPhase.ERROR -> SignalRed
        }
        StatusPill(
            text = phaseLabel(phase),
            color = color,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp)
        )
    }
}

private fun buildSelector(context: android.content.Context, telephoto: Boolean): CameraSelector {
    val builder = CameraSelector.Builder()
        .requireLensFacing(CameraSelector.LENS_FACING_BACK)
    if (!telephoto) return builder.build()
    val info = CameraSupport.probe(context)
    val telephotoId = info.telephotoCameraId ?: return builder.build()
    builder.addCameraFilter(CameraFilter { cameraInfos ->
        cameraInfos.filter {
            Camera2CameraInfo.from(it).cameraId == telephotoId
        }
    })
    return builder.build()
}

private fun phaseLabel(phase: OpticalDemodulator.RxPhase): String = when (phase) {
    OpticalDemodulator.RxPhase.IDLE -> "SİNYAL BEKLENİYOR"
    OpticalDemodulator.RxPhase.PREAMBLE_ACQUISITION -> "PREAMBLE ARANIYOR"
    OpticalDemodulator.RxPhase.READ_HEADER_AND_BODY -> "VERİ OKUNUYOR"
    OpticalDemodulator.RxPhase.CRC_CHECK -> "CRC DENETLENİYOR"
    OpticalDemodulator.RxPhase.DECODED -> "PAKET ALINDI"
    OpticalDemodulator.RxPhase.ERROR -> "PAKET HATALI"
}
