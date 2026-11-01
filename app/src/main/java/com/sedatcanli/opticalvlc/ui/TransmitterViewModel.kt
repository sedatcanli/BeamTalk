package com.sedatcanli.opticalvlc.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sedatcanli.opticalvlc.core.FrameCodec
import com.sedatcanli.opticalvlc.core.Language
import com.sedatcanli.opticalvlc.speech.VoskSttEngine
import com.sedatcanli.opticalvlc.tx.TorchController
import com.sedatcanli.opticalvlc.tx.TorchTransmitter
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.nio.charset.StandardCharsets

class TransmitterViewModel(application: Application) : AndroidViewModel(application) {

    private val torch = TorchController(application)
    private val transmitter = TorchTransmitter(torch)
    private val stt = VoskSttEngine(application)

    val txState: StateFlow<TorchTransmitter.State> = transmitter.state
    val sttState: StateFlow<VoskSttEngine.State> = stt.state

    var torchAvailable by mutableStateOf(torch.isAvailable)
        private set

    var torchError by mutableStateOf<String?>(null)
        private set

    fun refreshTorchAvailability() {
        torch.refreshTorchCameraId()
        torchAvailable = torch.isAvailable
        torchError = torch.lastError
    }

    var text by mutableStateOf("")
        private set

    var sourceLanguage by mutableStateOf(Language.TURKISH)
        private set

    var bitRateHz by mutableStateOf(6f)
        private set

    var repeats by mutableStateOf(1)
        private set

    var uiMessage by mutableStateOf<String?>(null)
        private set

    val payloadBytes: Int
        get() = text.trim().toByteArray(StandardCharsets.UTF_8).size

    val canTransmit: Boolean
        get() = payloadBytes in FrameCodec.MIN_PAYLOAD_BYTES..FrameCodec.MAX_PAYLOAD_BYTES

    val estimatedDurationMs: Long
        get() = TorchTransmitter.estimateDurationMs(
            payloadBytes = payloadBytes.coerceAtLeast(1),
            bitRateHz = bitRateHz.toInt(),
            repeats = repeats
        )

    init {
        viewModelScope.launch {
            stt.finalResults.collect { appendTranscription(it) }
        }
    }

    fun onTextChange(value: String) {
        text = value.take(MAX_TEXT_CHARS)
        uiMessage = null
    }

    fun clearText() {
        text = ""
        uiMessage = null
    }

    fun showMessage(message: String) {
        uiMessage = message
    }

    fun onSourceLanguageSelected(language: Language) {
        if (language.supportsStt) {
            sourceLanguage = language
            uiMessage = null
        }
    }

    fun onBitRateChange(value: Float) {
        bitRateHz = value.coerceIn(
            TorchTransmitter.MIN_BIT_RATE_HZ.toFloat(),
            TorchTransmitter.MAX_BIT_RATE_HZ.toFloat()
        )
    }

    fun onRepeatsChange(value: Int) {
        repeats = value.coerceIn(1, TorchTransmitter.MAX_REPEATS)
    }

    fun startListening() {
        if (sttState.value.status == VoskSttEngine.Status.LOADING ||
            sttState.value.status == VoskSttEngine.Status.LISTENING
        ) {
            stt.stop()
        } else {
            stt.start(sourceLanguage)
        }
    }

    fun stopListening() {
        stt.stop()
    }

    fun startTransmission(): Boolean {
        refreshTorchAvailability()
        if (!torchAvailable) {
            uiMessage = torchError ?: "Bu cihazda kullanılamabilir flaş bulunamadı"
            return false
        }
        val payload = text.trim()
        if (payload.isEmpty()) {
            uiMessage = "Önce bir mesaj yazın veya konuşun"
            return false
        }
        val bytes = payload.toByteArray(StandardCharsets.UTF_8)
        if (bytes.size > FrameCodec.MAX_PAYLOAD_BYTES) {
            uiMessage = "Mesaj ${bytes.size} bayt; en fazla ${FrameCodec.MAX_PAYLOAD_BYTES} bayt olabilir"
            return false
        }
        if (sttState.value.status == VoskSttEngine.Status.LISTENING) {
            stt.stop()
        }
        uiMessage = null
        val started = transmitter.transmit(
            text = payload,
            language = sourceLanguage,
            bitRateHz = bitRateHz.toInt(),
            repeats = repeats
        )
        if (!started) {
            uiMessage = transmitter.state.value.error ?: "İletim başlatılamadı"
        }
        return started
    }

    fun cancelTransmission() {
        transmitter.cancel()
    }

    private fun appendTranscription(result: String) {
        val clean = result.trim()
        if (clean.isEmpty()) return
        if (text.isBlank()) {
            text = clean
        } else {
            text = text.trimEnd() + " " + clean
        }
        if (text.length > MAX_TEXT_CHARS) {
            text = text.take(MAX_TEXT_CHARS)
        }
        uiMessage = null
    }

    override fun onCleared() {
        transmitter.release()
        stt.close()
        super.onCleared()
    }

    companion object {
        private const val MAX_TEXT_CHARS = 512
    }
}

