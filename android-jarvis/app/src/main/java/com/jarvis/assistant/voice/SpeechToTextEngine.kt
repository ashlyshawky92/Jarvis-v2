package com.jarvis.assistant.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SttCapabilities(
    val isAvailableOnDevice: Boolean,
    val supportsOnDeviceApi: Boolean
)

class SpeechToTextEngine(private val context: Context) {

    private var recognizer: SpeechRecognizer? = null

    private val _rmsLevel = MutableStateFlow(0f)
    val rmsLevel: StateFlow<Float> = _rmsLevel.asStateFlow()

    private val _partialText = MutableStateFlow("")
    val partialText: StateFlow<String> = _partialText.asStateFlow()

    fun getCapabilities(): SttCapabilities {
        val standardAvailable = SpeechRecognizer.isRecognitionAvailable(context)
        val onDevice = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
        } else {
            false
        }
        return SttCapabilities(isAvailableOnDevice = standardAvailable, supportsOnDeviceApi = onDevice)
    }

    fun startListening(
        preferOffline: Boolean,
        onReady: () -> Unit,
        onResult: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            onError("خدمة التعرف على الكلام غير متوفرة على الجهاز حالياً. تأكد من تفعيل خدمات Google الصوتية.")
            return
        }

        stopListening()
        _partialText.value = ""

        val instance = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer = instance

        instance.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                onReady()
            }

            override fun onBeginningOfSpeech() = Unit

            override fun onRmsChanged(rmsdB: Float) {
                val normalized = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
                _rmsLevel.value = normalized
            }

            override fun onBufferReceived(buffer: ByteArray?) = Unit

            override fun onEndOfSpeech() {
                _rmsLevel.value = 0f
            }

            override fun onError(error: Int) {
                _rmsLevel.value = 0f
                val message = when (error) {
                    SpeechRecognizer.ERROR_AUDIO -> "مشكلة في تسجيل الصوت من الميكروفون."
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "صلاحية الميكروفون مش مفعلة يا فندم."
                    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                        "مفيش اتصال بالإنترنت للتعرف السحابي على الكلام."
                    SpeechRecognizer.ERROR_NO_MATCH -> "مسمعتش كلام واضح، ممكن تعيد تاني يا فندم؟"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "انتهى وقت الاستماع من غير كلام."
                    else -> "تعذر التعرف على الصوت (كود: $error)."
                }
                onError(message)
            }

            override fun onResults(results: Bundle?) {
                _rmsLevel.value = 0f
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val best = matches?.firstOrNull()?.trim().orEmpty()
                if (best.isBlank()) {
                    onError("مفهمتش الكلام، جرب تقول الأمر تاني.")
                } else {
                    onResult(best)
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
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

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ar-EG")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "ar-EG")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            if (preferOffline) {
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            }
        }

        instance.startListening(intent)
    }

    fun stopListening() {
        _rmsLevel.value = 0f
        recognizer?.run {
            try {
                stopListening()
                cancel()
                destroy()
            } catch (_: Throwable) {
            }
        }
        recognizer = null
    }
}
