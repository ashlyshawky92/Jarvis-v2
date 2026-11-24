package com.jarvis.assistant.voice

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicLong

enum class SttEngineState {
    IDLE,
    INITIALIZING,
    LISTENING,
    PROCESSING,
    ERROR
}

data class SttCapabilities(
    val isStandardAvailable: Boolean,
    val isOnDeviceApiSupported: Boolean,
    val activeModeLabel: String
)

class SpeechToTextEngine(private val context: Context) {

    private val appContext = context.applicationContext
    private var recognizer: SpeechRecognizer? = null
    private val sessionCounter = AtomicLong(0L)

    private val _state = MutableStateFlow(SttEngineState.IDLE)
    val state: StateFlow<SttEngineState> = _state.asStateFlow()

    private val _rmsLevel = MutableStateFlow(0f)
    val rmsLevel: StateFlow<Float> = _rmsLevel.asStateFlow()

    private val _partialText = MutableStateFlow("")
    val partialText: StateFlow<String> = _partialText.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    fun getCapabilities(): SttCapabilities {
        val standard = SpeechRecognizer.isRecognitionAvailable(appContext)
        val onDevice = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            SpeechRecognizer.isOnDeviceRecognitionAvailable(appContext)
        } else {
            false
        }
        val mode = when {
            onDevice -> "محرك التعرف المحلي على الجهاز متاح (On-Device API)"
            standard -> "محرك التعرف القياسي متاح (يعتمد على خدمة Google الصوتية)"
            else -> "لا توجد خدمة تعرف صوتي متاحة على هذا الجهاز"
        }
        return SttCapabilities(
            isStandardAvailable = standard,
            isOnDeviceApiSupported = onDevice,
            activeModeLabel = mode
        )
    }

    fun startListening(
        languageTag: String = "ar-EG",
        preferOffline: Boolean,
        isNetworkAvailable: Boolean,
        onReady: () -> Unit,
        onResult: (transcript: String, usedOnDeviceEngine: Boolean) -> Unit,
        onError: (String) -> Unit
    ) {
        val capabilities = getCapabilities()
        if (!capabilities.isStandardAvailable && !capabilities.isOnDeviceApiSupported) {
            val msg = "خدمة التعرف على الكلام غير متوفرة على الجهاز حالياً."
            _state.value = SttEngineState.ERROR
            _lastError.value = msg
            onError(msg)
            return
        }

        if (!isNetworkAvailable && !capabilities.isOnDeviceApiSupported && !preferOffline) {
            val msg = "مفيش اتصال بالإنترنت حالياً، ونموذج التعرف المحلي على الكلام غير مؤكد توفره."
            _state.value = SttEngineState.ERROR
            _lastError.value = msg
            onError(msg)
            return
        }

        // Prevent concurrent recognition sessions and invalidate any prior callbacks.
        stopListeningInternal(resetToIdle = false)
        val activeSessionId = sessionCounter.incrementAndGet()
        _state.value = SttEngineState.INITIALIZING
        _partialText.value = ""
        _lastError.value = null

        val useOnDevice = preferOffline &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            capabilities.isOnDeviceApiSupported

        val instance = try {
            if (useOnDevice && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                SpeechRecognizer.createOnDeviceSpeechRecognizer(appContext)
            } else {
                SpeechRecognizer.createSpeechRecognizer(appContext)
            }
        } catch (t: Throwable) {
            val msg = "تعذر تشغيل الميكروفون أو محرك الصوت (${t.message ?: "خطأ غير معروف"})."
            _state.value = SttEngineState.ERROR
            _lastError.value = msg
            onError(msg)
            return
        }

        recognizer = instance
        var hasRetriedOfflineFallback = false

        fun launchIntent(offlineFlag: Boolean) {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, languageTag)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                if (offlineFlag) {
                    putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                }
            }
            instance.startListening(intent)
        }

        instance.setRecognitionListener(object : RecognitionListener {
            private fun isCurrentSession(): Boolean = sessionCounter.get() == activeSessionId

            override fun onReadyForSpeech(params: Bundle?) {
                if (!isCurrentSession()) return
                _state.value = SttEngineState.LISTENING
                onReady()
            }

            override fun onBeginningOfSpeech() = Unit

            override fun onRmsChanged(rmsdB: Float) {
                if (!isCurrentSession()) return
                _rmsLevel.value = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
            }

            override fun onBufferReceived(buffer: ByteArray?) = Unit

            override fun onEndOfSpeech() {
                if (!isCurrentSession()) return
                _rmsLevel.value = 0f
                _state.value = SttEngineState.PROCESSING
            }

            override fun onError(error: Int) {
                if (!isCurrentSession()) return
                _rmsLevel.value = 0f

                // Safe single retry when recognizer reports transient busy or client state glitch.
                if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY && !hasRetriedOfflineFallback) {
                    hasRetriedOfflineFallback = true
                    runCatching { instance.cancel() }
                    launchIntent(preferOffline)
                    return
                }

                val message = mapSpeechError(error, preferOffline, useOnDevice)
                _state.value = SttEngineState.ERROR
                _lastError.value = message
                releaseRecognizerOnly()
                onError(message)
            }

            override fun onResults(results: Bundle?) {
                if (!isCurrentSession()) return
                _rmsLevel.value = 0f
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val best = matches?.firstOrNull()?.trim().orEmpty()
                releaseRecognizerOnly()
                if (best.isBlank()) {
                    val msg = "مسمعتش كلام واضح يا فندم، ممكن تقول الأمر تاني؟"
                    _state.value = SttEngineState.ERROR
                    _lastError.value = msg
                    onError(msg)
                } else {
                    _state.value = SttEngineState.IDLE
                    onResult(best, useOnDevice)
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                if (!isCurrentSession()) return
                val partial = partialResults
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    .orEmpty()
                if (partial.isNotBlank()) {
                    _partialText.value = partial
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })

        launchIntent(preferOffline)
    }

    private fun mapSpeechError(error: Int, preferOffline: Boolean, usedOnDevice: Boolean): String {
        return when (error) {
            SpeechRecognizer.ERROR_AUDIO -> "حصلت مشكلة في التقاط الصوت من الميكروفون."
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "صلاحية الميكروفون مش مفعلة يا فندم."
            SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                if (preferOffline && !usedOnDevice) {
                    "مفيش إنترنت، وحزمة اللغة العربية بدون إنترنت مش متوفرة على محرك الجهاز."
                } else {
                    "مفيش اتصال بالإنترنت حالياً للتعرف السحابي على الكلام."
                }
            SpeechRecognizer.ERROR_NO_MATCH -> "مفهمتش الكلام، جرب تتكلم أوضح أو تقرب من الميكروفون."
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "انتهى وقت الاستماع من غير ما أسمع كلام."
            SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED,
            SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE ->
                "حزمة اللغة المطلوبة للتعرف الصوتي غير مثبتة على الجهاز."
            else -> "تعذر التعرف على الصوت (كود الخطأ: $error)."
        }
    }

    fun stopListening() {
        stopListeningInternal(resetToIdle = true)
    }

    private fun stopListeningInternal(resetToIdle: Boolean) {
        sessionCounter.incrementAndGet()
        _rmsLevel.value = 0f
        releaseRecognizerOnly()
        if (resetToIdle) {
            _state.value = SttEngineState.IDLE
        }
    }

    private fun releaseRecognizerOnly() {
        recognizer?.let { rec ->
            runCatching { rec.stopListening() }
            runCatching { rec.cancel() }
            runCatching { rec.destroy() }
        }
        recognizer = null
    }

    fun release() {
        stopListeningInternal(resetToIdle = true)
    }
}
