package com.sedatcanli.opticalvlc.speech

import android.content.Context
import com.sedatcanli.opticalvlc.core.Language
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService

/**
 * 100 % on-device speech recognition using Vosk.
 *
 * No network code is involved: audio is recorded by Vosk's [SpeechService]
 * and decoded by a local Kaldi model. The model must already be present in
 * assets (bundled) or in `files/vosk-models/` (imported by the user).
 */
class VoskSttEngine(context: Context) : AutoCloseable {

    enum class Status { IDLE, LOADING, LISTENING, ERROR }

    data class State(
        val status: Status = Status.IDLE,
        val partialText: String = "",
        val error: String? = null,
        val language: Language? = null
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val modelStore = VoskModelStore(context)
    private val operationMutex = Mutex()

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private val _finalResults = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val finalResults: SharedFlow<String> = _finalResults.asSharedFlow()

    private var startJob: Job? = null
    private var speechService: SpeechService? = null
    private var recognizer: Recognizer? = null

    private val listener = object : RecognitionListener {
        override fun onPartialResult(hypothesis: String?) {
            val text = extractText(hypothesis, "partial")
            if (text.isNotEmpty()) {
                _state.value = _state.value.copy(partialText = text)
            }
        }

        override fun onResult(hypothesis: String?) {
            val text = extractText(hypothesis, "text")
            if (text.isNotEmpty()) {
                _finalResults.tryEmit(text)
            }
            _state.value = _state.value.copy(partialText = "")
        }

        override fun onFinalResult(hypothesis: String?) {
            val text = extractText(hypothesis, "text")
            if (text.isNotEmpty()) {
                _finalResults.tryEmit(text)
            }
            _state.value = _state.value.copy(partialText = "")
        }

        override fun onError(exception: Exception?) {
            _state.value = _state.value.copy(
                status = Status.ERROR,
                error = exception?.message ?: "Ses tanıma hatası"
            )
        }

        override fun onTimeout() {
            _state.value = _state.value.copy(partialText = "")
        }
    }

    fun start(language: Language) {
        if (!language.supportsStt) {
            _state.value = State(
                status = Status.ERROR,
                error = "${language.displayName} için paketlenmiş STT modeli yok",
                language = language
            )
            return
        }

        startJob?.cancel()
        startJob = scope.launch {
            operationMutex.withLock {
                releaseSession()
                _state.value = State(status = Status.LOADING, language = language)
                try {
                    val model = withContext(Dispatchers.IO) { modelStore.getModel(language) }
                    val newRecognizer = withContext(Dispatchers.IO) {
                        Recognizer(model, SAMPLE_RATE)
                    }
                    val service = withContext(Dispatchers.IO) {
                        SpeechService(newRecognizer, SAMPLE_RATE)
                    }
                    recognizer = newRecognizer
                    speechService = service
                    if (!service.startListening(listener)) {
                        throw IllegalStateException("Mikrofon dinlemesi başlatılamadı")
                    }
                    _state.value = State(
                        status = Status.LISTENING,
                        language = language
                    )
                } catch (e: CancellationException) {
                    withContext(NonCancellable + Dispatchers.IO) { releaseSession() }
                    throw e
                } catch (e: Throwable) {
                    withContext(NonCancellable + Dispatchers.IO) { releaseSession() }
                    _state.value = State(
                        status = Status.ERROR,
                        error = e.message ?: "STT başlatılamadı",
                        language = language
                    )
                }
            }
        }
    }

    fun stop() {
        startJob?.cancel()
        scope.launch {
            operationMutex.withLock {
                releaseSession()
                if (_state.value.status != Status.ERROR) {
                    _state.value = State(status = Status.IDLE)
                }
            }
        }
    }

    private suspend fun releaseSession() {
        val service = speechService
        val rec = recognizer
        speechService = null
        recognizer = null
        if (service != null) {
            withContext(Dispatchers.IO) {
                try {
                    service.stop()
                } catch (_: Throwable) {
                }
                try {
                    service.shutdown()
                } catch (_: Throwable) {
                }
            }
        }
        try {
            rec?.close()
        } catch (_: Throwable) {
        }
    }

    private fun extractText(hypothesis: String?, key: String): String {
        if (hypothesis.isNullOrBlank()) return ""
        return try {
            val json = JSONObject(hypothesis)
            json.optString(key).trim()
        } catch (_: Exception) {
            ""
        }
    }

    override fun close() {
        startJob?.cancel()
        runBlocking {
            withContext(NonCancellable) { releaseSession() }
        }
        modelStore.close()
        scope.cancel()
    }

    companion object {
        const val SAMPLE_RATE = 16000f
    }
}
