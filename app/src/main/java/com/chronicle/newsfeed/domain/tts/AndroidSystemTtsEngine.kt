package com.chronicle.newsfeed.domain.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale

class AndroidSystemTtsEngine(
    private val context: Context,
    private var languageCode: String = "es",
    private var regionCode: String = "AR"
) : TtsEngine, TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    override var isReady: Boolean = false
        private set

    override val name: String
        get() = if (languageCode == "es") {
            if (regionCode == "AR") "Sistema Android (Español Argentina 🇦🇷)" else "Sistema Android (Español España 🇪🇸)"
        } else {
            if (regionCode == "GB" || regionCode == "UK") "Sistema Android (English UK 🇬🇧)" else "Sistema Android (English EEUU 🇺🇸)"
        }

    private var currentListener: TtsEngineListener? = null

    init {
        tts = TextToSpeech(context.applicationContext, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            applyLanguage()

            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    currentListener?.onStart()
                }

                override fun onDone(utteranceId: String?) {
                    currentListener?.onDone()
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    currentListener?.onError("Error durante la síntesis de voz nativa")
                }

                override fun onError(utteranceId: String?, errorCode: Int) {
                    currentListener?.onError("Error de síntesis ($errorCode)")
                }
            })

            isReady = true
            Log.i("AndroidSystemTts", "Android System TTS initialized successfully with locale $languageCode-$regionCode")
        } else {
            isReady = false
            Log.e("AndroidSystemTts", "Failed to initialize Android System TTS")
        }
    }

    fun updateLocale(language: String, region: String) {
        this.languageCode = language
        this.regionCode = region
        if (isReady) {
            applyLanguage()
        }
    }

    private fun applyLanguage() {
        val locale = if (languageCode == "en") {
            if (regionCode == "GB" || regionCode == "UK") Locale.UK else Locale.US
        } else {
            if (regionCode == "AR") Locale("es", "AR") else Locale("es", "ES")
        }
        val langResult = tts?.setLanguage(locale)
        if (langResult == TextToSpeech.LANG_MISSING_DATA || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
            // Try generic Spanish if specific regional dialect is not installed
            if (languageCode == "es") {
                tts?.setLanguage(Locale("es"))
            } else {
                tts?.setLanguage(Locale.getDefault())
            }
        }
    }

    override fun speak(text: String, rate: Float, pitch: Float, listener: TtsEngineListener) {
        if (!isReady || tts == null) {
            listener.onError("El motor de voz del sistema no está listo todavía")
            return
        }

        currentListener = listener
        tts?.setSpeechRate(rate)
        tts?.setPitch(pitch)

        val utteranceId = "chronicle_tts_${System.currentTimeMillis()}"
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    override fun stop() {
        tts?.stop()
    }

    override fun isSpeaking(): Boolean {
        return tts?.isSpeaking == true
    }

    override fun release() {
        stop()
        tts?.shutdown()
        tts = null
        isReady = false
    }
}
