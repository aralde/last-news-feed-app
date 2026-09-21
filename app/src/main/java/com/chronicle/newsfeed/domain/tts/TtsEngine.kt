package com.chronicle.newsfeed.domain.tts

interface TtsEngineListener {
    fun onStart()
    fun onDone()
    fun onError(errorMessage: String)
}

interface TtsEngine {
    val name: String
    val isReady: Boolean

    fun speak(text: String, rate: Float = 1.0f, pitch: Float = 1.0f, listener: TtsEngineListener)
    fun stop()
    fun isSpeaking(): Boolean
    fun release()
}
