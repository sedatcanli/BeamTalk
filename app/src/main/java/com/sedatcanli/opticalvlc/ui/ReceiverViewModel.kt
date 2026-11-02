package com.sedatcanli.opticalvlc.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sedatcanli.opticalvlc.core.FrameCodec
import com.sedatcanli.opticalvlc.core.Language
import com.sedatcanli.opticalvlc.rx.LumaFrame
import com.sedatcanli.opticalvlc.rx.LumaSample
import com.sedatcanli.opticalvlc.rx.OpticalDemodulator
import com.sedatcanli.opticalvlc.speech.OfflineTtsEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ReceivedMessage(
    val id: Long,
    val text: String,
    val languageId: Int,
    val language: Language?,
    val bitRateHz: Double,
    val contrast: Double,
    val durationMs: Long,
    val receivedAtMs: Long
)

data class OverlayInfo(
    val roiLeft: Int,
    val roiTop: Int,
    val roiRight: Int,
    val roiBottom: Int,
    val centroidX: Float,
    val centroidY: Float,
    val imageWidth: Int,
    val imageHeight: Int,
    val rotationDegrees: Int
)

class ReceiverViewModel(application: Application) : AndroidViewModel(application) {

    private val demodulator = OpticalDemodulator()
    private val tts = OfflineTtsEngine(application)

    val rxState: StateFlow<OpticalDemodulator.State> = demodulator.state
    val ttsState: StateFlow<OfflineTtsEngine.State> = tts.state

    private val _messages = MutableStateFlow<List<ReceivedMessage>>(emptyList())
    val messages: StateFlow<List<ReceivedMessage>> = _messages.asStateFlow()

    private val _lumaHistory = MutableStateFlow(FloatArray(0))
    val lumaHistory: StateFlow<FloatArray> = _lumaHistory.asStateFlow()

    private val _overlay = MutableStateFlow<OverlayInfo?>(null)
    val overlay: StateFlow<OverlayInfo?> = _overlay.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private val _cameraError = MutableStateFlow<String?>(null)
    val cameraError: StateFlow<String?> = _cameraError.asStateFlow()

    var targetLanguage by mutableStateOf(Language.TURKISH)
        private set

    var autoSpeak by mutableStateOf(true)
        private set

    var useFrameLanguage by mutableStateOf(true)
        private set

    private val history = ArrayDeque<Float>(HISTORY_SIZE)
    private var messageCounter = 0L

    init {
        tts.refreshLanguage(targetLanguage)
        viewModelScope.launch {
            demodulator.events.collect { event ->
                when (event) {
                    is OpticalDemodulator.Event.Decoded -> handleDecoded(event)
                    is OpticalDemodulator.Event.Rejected -> {
                        _statusMessage.value = "Paket reddedildi: ${event.reason}"
                    }
                }
            }
        }
    }

    fun onLumaFrame(frame: LumaFrame) {
        val sample = LumaSample(frame.timeNs, frame.spot.roiMean)
        demodulator.onSample(sample)

        synchronized(history) {
            history.addLast(sample.luma)
            while (history.size > HISTORY_SIZE) history.removeFirst()
            _lumaHistory.value = history.toFloatArray()
        }

        _overlay.value = OverlayInfo(
            roiLeft = frame.spot.roiLeft,
            roiTop = frame.spot.roiTop,
            roiRight = frame.spot.roiRight,
            roiBottom = frame.spot.roiBottom,
            centroidX = frame.spot.centroidX,
            centroidY = frame.spot.centroidY,
            imageWidth = frame.spot.imageWidth,
            imageHeight = frame.spot.imageHeight,
            rotationDegrees = frame.rotationDegrees
        )
    }

    private fun handleDecoded(event: OpticalDemodulator.Event.Decoded) {
        messageCounter++
        val parsed = event.frame
        val message = ReceivedMessage(
            id = messageCounter,
            text = parsed.payload,
            languageId = parsed.languageId,
            language = parsed.language,
            bitRateHz = event.bitRateHz,
            contrast = event.contrast,
            durationMs = event.durationMs,
            receivedAtMs = System.currentTimeMillis()
        )
        _messages.update { current -> (listOf(message) + current).take(MAX_MESSAGES) }
        _statusMessage.value = "Paket alındı (${message.text.length} karakter)"

        if (autoSpeak) {
            val speakLanguage = if (useFrameLanguage) {
                parsed.language ?: targetLanguage
            } else {
                targetLanguage
            }
            tts.speak(parsed.payload, speakLanguage)
        }
    }

    fun onTargetLanguageSelected(language: Language) {
        targetLanguage = language
        tts.refreshLanguage(language)
    }

    fun onAutoSpeakChange(value: Boolean) {
        autoSpeak = value
        if (!value) tts.stopSpeaking()
    }

    fun onUseFrameLanguageChange(value: Boolean) {
        useFrameLanguage = value
    }

    fun stopSpeaking() {
        tts.stopSpeaking()
    }

    fun speakMessage(message: ReceivedMessage) {
        val language = if (useFrameLanguage) {
            message.language ?: targetLanguage
        } else {
            targetLanguage
        }
        tts.speak(message.text, language)
    }

    fun clearMessages() {
        _messages.value = emptyList()
        _statusMessage.value = null
    }

    fun clearStatus() {
        _statusMessage.value = null
    }

    fun reportCameraError(message: String) {
        _cameraError.value = message
    }

    fun clearCameraError() {
        _cameraError.value = null
    }

    fun onCameraStopped() {
        demodulator.reset()
        _overlay.value = null
    }

    override fun onCleared() {
        tts.close()
        super.onCleared()
    }

    companion object {
        private const val HISTORY_SIZE = 150
        private const val MAX_MESSAGES = 50
    }
}

