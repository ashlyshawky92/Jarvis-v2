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
import java.util.concurrent.atomic.AtomicReference

data class JarvisVoiceProfile(
    val profileName: String = "JARVIS",
    val languageTag: String = "ar-EG",
    val selectedVoiceName: String? = null,
    val speechRate: Float = 1.0f,
    val pitch: Float = 0.92f,
    val providerId: String = "android-system-tts"
)

data class InstalledVoiceOption(
    val name: String,
    val languageTag: String,
    val requiresNetwork: Boolean
)

data class TtsStatus(
    val isInitializing: Boolean = true,
    val isReady: Boolean = false,
    val activeEngineName: String = "جاري تهيئة محرك النطق...",
    val selectedVoiceName: String? = null,
    val egyptianArabicAvailable: Boolean = false,
    val anyArabicAvailable: Boolean = false,
    val availableVoices: List<InstalledVoiceOption> = emptyList(),
    val errorNote: String? = null
)

/**
 * Abstraction allowing future cloud TTS integration without embedding secrets in the app.
 */
interface TtsProvider {
    val status: StateFlow<TtsStatus>
    val isSpeaking: StateFlow<Boolean>
    fun speak(text: String, profile: JarvisVoiceProfile, onFinished: (Result<Unit>) -> Unit)
    fun stop()
    fun shutdown()
}

class TextToSpeechEngine(context: Context) : TtsProvider {

    private val appContext = context.applicationContext
    private var tts: TextToSpeech? = null
    private val activeUtteranceId = AtomicReference<String?>(null)
    private var pendingCallback: ((Result<Unit>) -> Unit)? = null

    private val _status = MutableStateFlow(TtsStatus())
    override val status: StateFlow<TtsStatus> = _status.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    override val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    init {
        tts = TextToSpeech(appContext) { initCode ->
            val engine = tts
            if (initCode != TextToSpeech.SUCCESS || engine == null) {
                _status.value = TtsStatus(
                    isInitializing = false,
                    isReady = false,
                    activeEngineName = "غير متاح",
                    errorNote = "تعذر تشغيل محرك النطق على الجهاز (كود: $initCode)."
                )
                return@TextToSpeech
            }

            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    if (utteranceId != activeUtteranceId.get()) return
                    _isSpeaking.value = true
                }

                override fun onDone(utteranceId: String?) {
                    if (utteranceId != activeUtteranceId.get()) return
                    activeUtteranceId.set(null)
                    _isSpeaking.value = false
                    val cb = pendingCallback
                    pendingCallback = null
                    cb?.invoke(Result.success(Unit))
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    handleUtteranceFailure(utteranceId, -1)
                }

                override fun onError(utteranceId: String?, errorCode: Int) {
                    handleUtteranceFailure(utteranceId, errorCode)
                }

                override fun onStop(utteranceId: String?, interrupted: Boolean) {
                    if (utteranceId != activeUtteranceId.get()) return
                    activeUtteranceId.set(null)
                    _isSpeaking.value = false
                    val cb = pendingCallback
                    pendingCallback = null
                    cb?.invoke(Result.success(Unit))
                }
            })

            val egyptLocale = Locale.forLanguageTag("ar-EG")
            val egAvailable = engine.isLanguageAvailable(egyptLocale) >= TextToSpeech.LANG_AVAILABLE
            val arAvailable = engine.isLanguageAvailable(Locale.forLanguageTag("ar")) >= TextToSpeech.LANG_AVAILABLE

            val installedVoices = engine.voices
                ?.filter {
                    val lang = it.locale.language.lowercase()
                    lang == "ar" || lang == "en"
                }
                ?.sortedWith(compareBy({ !it.locale.language.equals("ar", true) }, { it.isNetworkConnectionRequired }, { it.name }))
                ?.map {
                    InstalledVoiceOption(
                        name = it.name,
                        languageTag = it.locale.toLanguageTag(),
                        requiresNetwork = it.isNetworkConnectionRequired
                    )
                }
                .orEmpty()

            val defaultVoice = selectVoice(engine.voices, preferredVoiceName = null, preferredLanguageTag = "ar-EG")
            if (defaultVoice != null) {
                engine.voice = defaultVoice
            } else {
                engine.language = if (egAvailable) egyptLocale else Locale.forLanguageTag("ar")
            }

            _status.value = TtsStatus(
                isInitializing = false,
                isReady = true,
                activeEngineName = engine.defaultEngine ?: "Android TTS",
                selectedVoiceName = defaultVoice?.name,
                egyptianArabicAvailable = egAvailable,
                anyArabicAvailable = arAvailable,
                availableVoices = installedVoices,
                errorNote = when {
                    !arAvailable -> "حزمة الصوت العربي غير مثبتة على الجهاز حالياً. يمكن تثبيتها من إعدادات Google TTS."
                    !egAvailable -> "تم اختيار صوت عربي عام لعدم توفر حزمة مصرية مخصصة على محرك الجهاز."
                    else -> null
                }
            )
        }
    }

    private fun handleUtteranceFailure(utteranceId: String?, errorCode: Int) {
        if (utteranceId != null && utteranceId != activeUtteranceId.get()) return
        activeUtteranceId.set(null)
        _isSpeaking.value = false
        val cb = pendingCallback
        pendingCallback = null
        cb?.invoke(Result.failure(IllegalStateException("فشل النطق الصوتي (كود: $errorCode)")))
    }

    private fun selectVoice(
        voices: Set<Voice>?,
        preferredVoiceName: String?,
        preferredLanguageTag: String
    ): Voice? {
        if (voices.isNullOrEmpty()) return null
        if (!preferredVoiceName.isNullOrBlank()) {
            voices.firstOrNull { it.name == preferredVoiceName }?.let { return it }
        }
        val targetLocale = Locale.forLanguageTag(preferredLanguageTag)
        val sameLang = voices.filter { it.locale.language.equals(targetLocale.language, ignoreCase = true) }
        return sameLang.firstOrNull {
            it.locale.country.equals(targetLocale.country, ignoreCase = true) && !it.isNetworkConnectionRequired
        } ?: sameLang.firstOrNull {
            it.locale.country.equals(targetLocale.country, ignoreCase = true)
        } ?: sameLang.firstOrNull()
    }

    override fun speak(
        text: String,
        profile: JarvisVoiceProfile,
        onFinished: (Result<Unit>) -> Unit
    ) {
        val engine = tts
        if (engine == null || !_status.value.isReady) {
            _isSpeaking.value = false
            onFinished(Result.failure(IllegalStateException("محرك النطق غير جاهز حالياً.")))
            return
        }
        if (text.isBlank()) {
            onFinished(Result.success(Unit))
            return
        }

        // Prevent overlapping speech sessions and stale utterance callbacks.
        stop()

        val chosenVoice = selectVoice(engine.voices, profile.selectedVoiceName, profile.languageTag)
        if (chosenVoice != null) {
            engine.voice = chosenVoice
        } else {
            val targetLocale = Locale.forLanguageTag(profile.languageTag)
            if (engine.isLanguageAvailable(targetLocale) >= TextToSpeech.LANG_AVAILABLE) {
                engine.language = targetLocale
            }
        }

        engine.setSpeechRate(profile.speechRate.coerceIn(0.6f, 1.6f))
        engine.setPitch(profile.pitch.coerceIn(0.7f, 1.3f))

        val utteranceId = UUID.randomUUID().toString()
        activeUtteranceId.set(utteranceId)
        pendingCallback = onFinished

        val result = engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        if (result == TextToSpeech.ERROR) {
            activeUtteranceId.set(null)
            pendingCallback = null
            _isSpeaking.value = false
            onFinished(Result.failure(IllegalStateException("تعذر بدء تشغيل الصوت من محرك الجهاز.")))
        }
    }

    override fun stop() {
        activeUtteranceId.set(null)
        _isSpeaking.value = false
        val cb = pendingCallback
        pendingCallback = null
        runCatching { tts?.stop() }
        cb?.invoke(Result.success(Unit))
    }

    override fun shutdown() {
        stop()
        runCatching { tts?.shutdown() }
        tts = null
        _status.value = _status.value.copy(isReady = false)
    }
}
