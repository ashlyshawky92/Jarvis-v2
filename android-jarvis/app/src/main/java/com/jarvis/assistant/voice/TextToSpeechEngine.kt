package com.jarvis.assistant.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.UUID

data class JarvisVoiceProfile(
    val profileName: String = "JARVIS",
    val languageTag: String = "ar-EG",
    val speechRate: Float = 1.0f,
    val pitch: Float = 0.92f,
    val providerId: String = "android-system-tts"
)

data class TtsStatus(
    val isReady: Boolean = false,
    val activeEngineName: String = "جاري التهيئة...",
    val selectedVoiceName: String? = null,
    val egyptianArabicSupported: Boolean = false,
    val errorNote: String? = null
)

class TextToSpeechEngine(context: Context) {

    private val appContext = context.applicationContext
    private var tts: TextToSpeech? = null

    private val _status = MutableStateFlow(TtsStatus())
    val status: StateFlow<TtsStatus> = _status.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private var onSpeakDoneCallback: (() -> Unit)? = null

    init {
        tts = TextToSpeech(appContext) { initCode ->
            val engine = tts
            if (initCode != TextToSpeech.SUCCESS || engine == null) {
                _status.value = TtsStatus(
                    isReady = false,
                    activeEngineName = "غير متاح",
                    errorNote = "تعذر تشغيل محرك النطق على الجهاز."
                )
                return@TextToSpeech
            }

            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    _isSpeaking.value = true
                }

                override fun onDone(utteranceId: String?) {
                    _isSpeaking.value = false
                    onSpeakDoneCallback?.invoke()
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    _isSpeaking.value = false
                }

                override fun onError(utteranceId: String?, errorCode: Int) {
                    _isSpeaking.value = false
                }
            })

            val egyptLocale = Locale.forLanguageTag("ar-EG")
            val egResult = engine.isLanguageAvailable(egyptLocale)
            val fallbackArabic = Locale.forLanguageTag("ar")
            val arResult = engine.isLanguageAvailable(fallbackArabic)

            val chosenLocale = when {
                egResult >= TextToSpeech.LANG_AVAILABLE -> egyptLocale
                arResult >= TextToSpeech.LANG_AVAILABLE -> fallbackArabic
                else -> Locale.getDefault()
            }
            engine.language = chosenLocale

            val bestArabicVoice = selectBestArabicVoice(engine.voices)
            if (bestArabicVoice != null) {
                engine.voice = bestArabicVoice
            }

            _status.value = TtsStatus(
                isReady = true,
                activeEngineName = engine.defaultEngine ?: "Android TTS",
                selectedVoiceName = bestArabicVoice?.name ?: chosenLocale.toLanguageTag(),
                egyptianArabicSupported = egResult >= TextToSpeech.LANG_AVAILABLE,
                errorNote = if (arResult < TextToSpeech.LANG_AVAILABLE) {
                    "حزمة الصوت العربي غير مثبتة في إعدادات محرك النطق."
                } else null
            )
        }
    }

    private fun selectBestArabicVoice(voices: Set<Voice>?): Voice? {
        if (voices.isNullOrEmpty()) return null
        val arabicVoices = voices.filter { it.locale.language.equals("ar", ignoreCase = true) }
        if (arabicVoices.isEmpty()) return null
        return arabicVoices.firstOrNull {
            it.locale.country.equals("EG", ignoreCase = true) && !it.isNetworkConnectionRequired
        } ?: arabicVoices.firstOrNull {
            it.locale.country.equals("EG", ignoreCase = true)
        } ?: arabicVoices.firstOrNull()
    }

    fun speak(
        text: String,
        profile: JarvisVoiceProfile,
        onComplete: (() -> Unit)? = null
    ) {
        val engine = tts ?: return
        if (!_status.value.isReady) return

        onSpeakDoneCallback = onComplete
        engine.setSpeechRate(profile.speechRate.coerceIn(0.6f, 1.6f))
        engine.setPitch(profile.pitch.coerceIn(0.7f, 1.3f))
        val utteranceId = UUID.randomUUID().toString()
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    fun stop() {
        _isSpeaking.value = false
        tts?.stop()
    }

    fun shutdown() {
        stop()
        tts?.shutdown()
        tts = null
    }
}
