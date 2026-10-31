package com.sedatcanli.opticalvlc.speech

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.sedatcanli.opticalvlc.core.Language
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

/**
 * Android's native TTS engine, configured for embedded (offline) synthesis.
 *
 * The offline capability is verified through
 * [TextToSpeech.Engine.KEY_FEATURE_EMBEDDED_SYNTHESIS] on the default engine
 * plus [TextToSpeech.isLanguageAvailable] for the requested locale. If the
 * voice pack is missing the UI tells the user instead of silently falling
 * back to a network voice.
 */
class OfflineTtsEngine(context: Context) : AutoCloseable {

    data class State(
        val initialized: Boolean = false,
        val embeddedVoiceAvailable: Boolean = false,
        val languageAvailable: Boolean = false,
        val speaking: Boolean = false,
        val engineName: String? = null,
        val language: Language? = null,
        val message: String? = null
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private val utteranceCounter = AtomicLong(0L)
    private var tts: TextToSpeech? = null
    private var ready = false
    private var pendingLanguage: Language? = null

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                ready = true
                val engine = tts
                val engineName = engine?.defaultEngine
                engine?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        _state.value = _state.value.copy(speaking = true)
                    }

                    override fun onDone(utteranceId: String?) {
                        _state.value = _state.value.copy(speaking = false)
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        _state.value = _state.value.copy(
                            speaking = false,
                            message = "Seslendirme hatası"
                        )
                    }

                    override fun onError(utteranceId: String?, errorCode: Int) {
                        _state.value = _state.value.copy(
                            speaking = false,
                            message = "Seslendirme hatası ($errorCode)"
                        )
                    }
                })
                _state.value = State(
                    initialized = true,
                    embeddedVoiceAvailable = false,
                    engineName = engineName
                )
                pendingLanguage?.let { applyLanguage(it) }
            } else {
                _state.value = State(
                    initialized = false,
                    message = "TTS motoru başlatılamadı"
                )
            }
        }
    }

    fun refreshLanguage(language: Language) {
        if (!ready) {
            pendingLanguage = language
            _state.value = _state.value.copy(language = language)
            return
        }
        applyLanguage(language)
    }

    private fun applyLanguage(language: Language) {
        val engine = tts ?: return
        val locale = Locale.forLanguageTag(language.tag)
        val availability = try {
            engine.setLanguage(locale)
        } catch (_: Exception) {
            TextToSpeech.LANG_NOT_SUPPORTED
        }
        val available = availability == TextToSpeech.LANG_AVAILABLE ||
            availability == TextToSpeech.LANG_COUNTRY_AVAILABLE ||
            availability == TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE
        val features = try {
            engine.getFeatures(locale)?.toList() ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
        val embedded = features.contains(TextToSpeech.Engine.KEY_FEATURE_EMBEDDED_SYNTHESIS)
        _state.value = _state.value.copy(
            language = language,
            languageAvailable = available,
            embeddedVoiceAvailable = embedded,
            message = when {
                available && embedded -> null
                available -> "TTS motoru gömülü ses paketi bildirmiyor"
                else -> "${language.displayName} için TTS dil paketi yüklü değil"
            }
        )
    }

    fun speak(text: String, language: Language) {
        val engine = tts
        if (!ready || engine == null) {
            _state.value = _state.value.copy(message = "TTS hazır değil")
            return
        }
        if (text.isBlank()) return
        applyLanguage(language)
        val locale = Locale.forLanguageTag(language.tag)
        val availability = try {
            engine.isLanguageAvailable(locale)
        } catch (_: Exception) {
            TextToSpeech.LANG_NOT_SUPPORTED
        }
        if (availability < TextToSpeech.LANG_AVAILABLE) {
            _state.value = _state.value.copy(
                speaking = false,
                message = "${language.displayName} ses paketi bulunamadı"
            )
            return
        }
        val id = "vlc-${utteranceCounter.incrementAndGet()}"
        val result = engine.speak(text, TextToSpeech.QUEUE_ADD, null, id)
        if (result != TextToSpeech.SUCCESS) {
            _state.value = _state.value.copy(message = "Seslendirme kuyruğa alınamadı")
        }
    }

    fun stopSpeaking() {
        tts?.stop()
        _state.value = _state.value.copy(speaking = false)
    }

    override fun close() {
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (_: Throwable) {
        }
        tts = null
        ready = false
        _state.value = State()
    }
}
