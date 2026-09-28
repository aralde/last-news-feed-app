package com.chronicle.newsfeed.domain.tts

import android.content.Context
import android.util.Log
import com.chronicle.newsfeed.data.model.TtsEngineType
import com.chronicle.newsfeed.data.model.TtsSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

data class PlaybackState(
    val isPlaying: Boolean = false,
    val isSynthesizing: Boolean = false,
    val title: String = "",
    val text: String = "",
    val speechRate: Float = 1.0f,
    val engineName: String = "",
    val errorMessage: String? = null
)

sealed class TtsInitStatus {
    object Initializing : TtsInitStatus()
    data class Ready(val engineName: String) : TtsInitStatus()
    data class Error(val message: String) : TtsInitStatus()
}

class TtsManager(private val context: Context) {

    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private val _initStatus = MutableStateFlow<TtsInitStatus>(TtsInitStatus.Initializing)
    val initStatus: StateFlow<TtsInitStatus> = _initStatus.asStateFlow()

    val piperDownloader = PiperDownloader(context)

    private var currentEngine: TtsEngine? = null
    private var systemEngine: AndroidSystemTtsEngine? = null
    private var piperEngine: SherpaPiperTtsEngine? = null
    private var currentSettings: TtsSettings = TtsSettings()

    init {
        systemEngine = AndroidSystemTtsEngine(context, currentSettings.language, currentSettings.voiceRegion)
        currentEngine = systemEngine
        _playbackState.value = _playbackState.value.copy(
            speechRate = currentSettings.speechRate,
            engineName = currentEngine?.name ?: "TTS"
        )
        _initStatus.value = TtsInitStatus.Ready(currentEngine?.name ?: "TTS")
    }

    fun updateSettings(settings: TtsSettings) {
        currentSettings = settings
        _initStatus.value = TtsInitStatus.Initializing

        systemEngine?.updateLocale(settings.language, settings.voiceRegion)

        if (settings.engineType == TtsEngineType.PIPER_ONNX) {
            // Check downloaded folder first, then custom directory
            val downloadedDir = piperDownloader.getVoiceDir(settings.selectedVoiceId)
            val customDir = if (settings.piperModelDir.isNotBlank()) File(settings.piperModelDir) else null

            val targetDir = when {
                piperDownloader.isVoiceDownloaded(settings.selectedVoiceId) -> downloadedDir
                customDir != null && customDir.exists() -> customDir
                else -> null
            }

            if (targetDir != null) {
                val modelFile = File(targetDir, "${settings.selectedVoiceId}.onnx")
                val tokensFile = File(targetDir, "tokens.txt")
                val customDataDir = File(targetDir, "espeak-ng-data").absolutePath
                val dataDir = SherpaPiperTtsEngine.resolveEspeakNgData(context, customDataDir)

                if (modelFile.exists() && tokensFile.exists()) {
                    piperEngine?.release()
                    piperEngine = SherpaPiperTtsEngine(
                        context = context,
                        modelPath = modelFile.absolutePath,
                        tokensPath = tokensFile.absolutePath,
                        dataDir = dataDir
                    )
                    currentEngine = piperEngine
                } else {
                    Log.w("TtsManager", "Piper model or tokens not found in $targetDir, using system TTS")
                    currentEngine = systemEngine
                }
            } else {
                currentEngine = systemEngine
            }
        } else {
            currentEngine = systemEngine
        }

        val engineName = currentEngine?.name ?: "TTS"
        _playbackState.value = _playbackState.value.copy(
            speechRate = settings.speechRate,
            engineName = engineName
        )
        _initStatus.value = TtsInitStatus.Ready(engineName)
    }

    private var currentOnDone: (() -> Unit)? = null

    fun play(title: String, text: String, onDone: (() -> Unit)? = null) {
        if (text.isBlank()) return

        stop()
        currentOnDone = onDone
        val engine = currentEngine ?: systemEngine

        _playbackState.value = _playbackState.value.copy(
            isPlaying = true,
            isSynthesizing = true,
            title = title,
            text = text,
            errorMessage = null,
            engineName = engine?.name ?: "TTS"
        )

        engine?.speak(
            text = text,
            rate = currentSettings.speechRate,
            pitch = currentSettings.pitch,
            listener = object : TtsEngineListener {
                override fun onStart() {
                    _playbackState.value = _playbackState.value.copy(
                        isPlaying = true,
                        isSynthesizing = false
                    )
                }

                override fun onDone() {
                    _playbackState.value = _playbackState.value.copy(
                        isPlaying = false,
                        isSynthesizing = false,
                        text = "",
                        title = ""
                    )
                    val callback = currentOnDone
                    currentOnDone = null
                    callback?.invoke()
                }

                override fun onError(errorMessage: String) {
                    _playbackState.value = _playbackState.value.copy(
                        isPlaying = false,
                        isSynthesizing = false,
                        errorMessage = errorMessage
                    )
                    currentOnDone = null
                }
            }
        )
    }

    fun stop() {
        currentOnDone = null
        currentEngine?.stop()
        systemEngine?.stop()
        piperEngine?.stop()
        _playbackState.value = _playbackState.value.copy(
            isPlaying = false,
            isSynthesizing = false,
            text = "",
            title = ""
        )
    }

    fun togglePlayPause() {
        val current = _playbackState.value
        if (current.isPlaying) {
            stop()
        } else if (current.text.isNotBlank()) {
            play(current.title, current.text)
        }
    }

    fun cycleSpeed() {
        val rates = listOf(1.0f, 1.25f, 1.5f, 1.75f, 2.0f)
        val currentRate = _playbackState.value.speechRate
        val nextIndex = (rates.indexOfFirst { kotlin.math.abs(it - currentRate) < 0.05f } + 1) % rates.size
        val newRate = rates[nextIndex]

        currentSettings = currentSettings.copy(speechRate = newRate)
        _playbackState.value = _playbackState.value.copy(speechRate = newRate)

        if (_playbackState.value.isPlaying) {
            play(_playbackState.value.title, _playbackState.value.text)
        }
    }

    fun release() {
        stop()
        systemEngine?.release()
        piperEngine?.release()
        systemEngine = null
        piperEngine = null
        currentEngine = null
    }
}
