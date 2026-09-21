package com.chronicle.newsfeed.ui.settings

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.chronicle.newsfeed.ChronicleApplication
import com.chronicle.newsfeed.data.model.LlmSettings
import com.chronicle.newsfeed.data.model.PromptSettings
import com.chronicle.newsfeed.data.model.TtsEngineType
import com.chronicle.newsfeed.data.model.TtsSettings
import com.chronicle.newsfeed.domain.llm.LlmStatus
import com.chronicle.newsfeed.domain.tts.AVAILABLE_PIPER_VOICES
import com.chronicle.newsfeed.domain.tts.PiperVoiceInfo
import com.chronicle.newsfeed.domain.tts.getVoicesForLanguage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as ChronicleApplication
    private val settingsRepo = app.settingsRepository
    val llmManager = app.llmManager
    val ttsManager = app.ttsManager
    val piperDownloader = ttsManager.piperDownloader

    val llmSettings: StateFlow<LlmSettings> = settingsRepo.llmSettingsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = LlmSettings()
    )

    val ttsSettings: StateFlow<TtsSettings> = settingsRepo.ttsSettingsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = TtsSettings()
    )

    val promptSettings: StateFlow<PromptSettings> = settingsRepo.promptSettingsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = PromptSettings()
    )

    private val _isImportingModel = MutableStateFlow(false)
    val isImportingModel = _isImportingModel.asStateFlow()

    private val _importProgress = MutableStateFlow(0f)
    val importProgress = _importProgress.asStateFlow()

    private val _downloadingVoiceId = MutableStateFlow<String?>(null)
    val downloadingVoiceId = _downloadingVoiceId.asStateFlow()

    private val _downloadProgress = MutableStateFlow(0f)
    val downloadProgress = _downloadProgress.asStateFlow()

    private val _downloadStatusMessage = MutableStateFlow("")
    val downloadStatusMessage = _downloadStatusMessage.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage = _statusMessage.asStateFlow()

    init {
        viewModelScope.launch {
            ttsManager.playbackState.collect { playback ->
                if (playback.errorMessage != null) {
                    _statusMessage.value = playback.errorMessage
                }
            }
        }
    }

    fun updateLlmSettings(settings: LlmSettings) {
        viewModelScope.launch {
            settingsRepo.updateLlmSettings(settings)
            llmManager.initialize(settings)
        }
    }

    fun setLanguage(language: String) {
        viewModelScope.launch {
            val updatedLlm = llmSettings.value.copy(language = language)
            settingsRepo.updateLlmSettings(updatedLlm)
            llmManager.initialize(updatedLlm)

            // Dynamically adapt TTS settings to the chosen language
            val currentTts = ttsSettings.value
            val (newRegion, newVoiceId) = if (language == "en") {
                val region = if (currentTts.voiceRegion in listOf("US", "GB")) currentTts.voiceRegion else "US"
                val voiceId = if (region == "GB") "en_GB-alan-medium" else "en_US-amy-medium"
                region to voiceId
            } else {
                val region = if (currentTts.voiceRegion in listOf("AR", "ES")) currentTts.voiceRegion else "AR"
                val voiceId = if (region == "ES") "es_ES-davefx-medium" else "es_AR-daniela-high"
                region to voiceId
            }

            val updatedTts = currentTts.copy(
                language = language,
                voiceRegion = newRegion,
                selectedVoiceId = newVoiceId
            )
            settingsRepo.updateTtsSettings(updatedTts)
            ttsManager.updateSettings(updatedTts)
        }
    }

    fun setVoiceRegion(region: String) {
        viewModelScope.launch {
            val currentTts = ttsSettings.value
            val voiceId = when (region) {
                "AR" -> "es_AR-daniela-high"
                "ES" -> "es_ES-davefx-medium"
                "US" -> "en_US-amy-medium"
                "GB" -> "en_GB-alan-medium"
                else -> currentTts.selectedVoiceId
            }
            val updatedTts = currentTts.copy(voiceRegion = region, selectedVoiceId = voiceId)
            settingsRepo.updateTtsSettings(updatedTts)
            ttsManager.updateSettings(updatedTts)
        }
    }

    fun updateTtsSettings(settings: TtsSettings) {
        viewModelScope.launch {
            settingsRepo.updateTtsSettings(settings)
            ttsManager.updateSettings(settings)
        }
    }

    fun updatePromptSettings(settings: PromptSettings) {
        viewModelScope.launch {
            settingsRepo.updatePromptSettings(settings)
            _statusMessage.value = "Prompts guardados correctamente"
        }
    }

    fun resetPromptSettings() {
        viewModelScope.launch {
            settingsRepo.resetPromptSettings()
            _statusMessage.value = "Prompts restablecidos a los valores por defecto"
        }
    }

    fun importModelFromUri(uri: Uri) {
        viewModelScope.launch {
            _isImportingModel.value = true
            _importProgress.value = 0f
            try {
                val fileName = getFileNameFromUri(uri) ?: "gemma_model_${System.currentTimeMillis()}.litertlm"
                val modelsDir = File(app.filesDir, "models")
                if (!modelsDir.exists()) modelsDir.mkdirs()

                val destinationFile = File(modelsDir, fileName)

                withContext(Dispatchers.IO) {
                    val contentResolver = app.contentResolver
                    val afd = try { contentResolver.openAssetFileDescriptor(uri, "r") } catch (_: Exception) { null }
                    val totalSize = afd?.length ?: -1L
                    afd?.close()

                    // If file already exists with same size, avoid re-copying
                    if (!destinationFile.exists() || destinationFile.length() != totalSize || totalSize <= 0) {
                        if (destinationFile.exists()) destinationFile.delete()

                        val inputStream = contentResolver.openInputStream(uri)
                            ?: throw Exception("No se pudo abrir el archivo seleccionado")

                        FileOutputStream(destinationFile).use { output ->
                            val buffer = ByteArray(512 * 1024)
                            var read: Int
                            var copied = 0L
                            var lastReportedPercent = -1

                            while (inputStream.read(buffer).also { read = it } != -1) {
                                output.write(buffer, 0, read)
                                copied += read
                                if (totalSize > 0) {
                                    val currentPercent = ((copied.toDouble() / totalSize.toDouble()) * 100).toInt()
                                    if (currentPercent != lastReportedPercent) {
                                        lastReportedPercent = currentPercent
                                        _importProgress.value = (copied.toFloat() / totalSize.toFloat()).coerceIn(0f, 1f)
                                    }
                                }
                            }
                            output.flush()
                        }
                    }
                }

                _importProgress.value = 1f
                // Update settings and initialize Gemma
                val newSettings = llmSettings.value.copy(modelPath = destinationFile.absolutePath)
                settingsRepo.updateLlmSettings(newSettings)
                llmManager.initialize(newSettings)
                _statusMessage.value = "Modelo ${destinationFile.name} importado y listo"
            } catch (e: Exception) {
                _statusMessage.value = "Error al importar el archivo: ${e.localizedMessage}"
            } finally {
                _isImportingModel.value = false
            }
        }
    }

    fun downloadPiperVoice(voice: PiperVoiceInfo) {
        viewModelScope.launch {
            _downloadingVoiceId.value = voice.id
            _downloadProgress.value = 0f
            _downloadStatusMessage.value = "Iniciando descarga de ${voice.name}..."

            val result = piperDownloader.downloadVoice(voice) { progress, message ->
                _downloadProgress.value = progress
                _downloadStatusMessage.value = message
            }

            _downloadingVoiceId.value = null
            if (result.isSuccess) {
                _statusMessage.value = "Voz de ${voice.name} descargada e instalada con éxito."
                // Automatically activate this voice
                val newTts = ttsSettings.value.copy(
                    engineType = TtsEngineType.PIPER_ONNX,
                    selectedVoiceId = voice.id,
                    piperModelDir = result.getOrThrow().absolutePath
                )
                updateTtsSettings(newTts)
            } else {
                _statusMessage.value = "Error al descargar voz: ${result.exceptionOrNull()?.localizedMessage}"
            }
        }
    }

    fun importPiperVoiceFromUri(uri: Uri) {
        viewModelScope.launch {
            try {
                val fileName = getFileNameFromUri(uri) ?: "voice_${System.currentTimeMillis()}.onnx"
                val voiceId = fileName.substringBeforeLast(".")
                val targetDir = piperDownloader.getVoiceDir(voiceId)
                val destinationFile = File(targetDir, "$voiceId.onnx")

                withContext(Dispatchers.IO) {
                    app.contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(destinationFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                }

                val newTts = ttsSettings.value.copy(
                    engineType = TtsEngineType.PIPER_ONNX,
                    selectedVoiceId = voiceId,
                    piperModelDir = targetDir.absolutePath
                )
                updateTtsSettings(newTts)
                _statusMessage.value = "Voz importada a $voiceId"
            } catch (e: Exception) {
                _statusMessage.value = "Error al importar voz: ${e.localizedMessage}"
            }
        }
    }

    private fun getFileNameFromUri(uri: Uri): String? {
        var name: String? = null
        if (uri.scheme == "content") {
            val cursor = app.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) name = it.getString(index)
                }
            }
        }
        if (name == null) {
            name = uri.path?.let {
                val cut = it.lastIndexOf('/')
                if (cut != -1) it.substring(cut + 1) else it
            }
        }
        return name
    }

    fun testTtsVoice(text: String) {
        ttsManager.play("Prueba de voz", text)
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    fun getLlmStatus(): LlmStatus = llmManager.status
}
