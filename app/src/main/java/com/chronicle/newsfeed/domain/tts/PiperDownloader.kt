package com.chronicle.newsfeed.domain.tts

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

data class PiperVoiceInfo(
    val id: String,
    val name: String,
    val language: String,
    val country: String,
    val flag: String,
    val onnxUrl: String,
    val tokensUrl: String,
    val configUrl: String? = null
)

val AVAILABLE_PIPER_VOICES = listOf(
    PiperVoiceInfo(
        id = "es_AR-daniela-high",
        name = "Daniela (Argentina)",
        language = "es",
        country = "AR",
        flag = "🇦🇷",
        onnxUrl = "https://huggingface.co/csukuangfj/vits-piper-es_AR-daniela-high/resolve/main/es_AR-daniela-high.onnx",
        tokensUrl = "https://huggingface.co/csukuangfj/vits-piper-es_AR-daniela-high/resolve/main/tokens.txt",
        configUrl = "https://huggingface.co/csukuangfj/vits-piper-es_AR-daniela-high/resolve/main/es_AR-daniela-high.onnx.json"
    ),
    PiperVoiceInfo(
        id = "es_ES-davefx-medium",
        name = "Dave (España)",
        language = "es",
        country = "ES",
        flag = "🇪🇸",
        onnxUrl = "https://huggingface.co/csukuangfj/vits-piper-es_ES-davefx-medium/resolve/main/es_ES-davefx-medium.onnx",
        tokensUrl = "https://huggingface.co/csukuangfj/vits-piper-es_ES-davefx-medium/resolve/main/tokens.txt",
        configUrl = "https://huggingface.co/csukuangfj/vits-piper-es_ES-davefx-medium/resolve/main/es_ES-davefx-medium.onnx.json"
    ),
    PiperVoiceInfo(
        id = "en_US-amy-medium",
        name = "Amy (EE. UU.)",
        language = "en",
        country = "US",
        flag = "🇺🇸",
        onnxUrl = "https://huggingface.co/csukuangfj/vits-piper-en_US-amy-medium/resolve/main/en_US-amy-medium.onnx",
        tokensUrl = "https://huggingface.co/csukuangfj/vits-piper-en_US-amy-medium/resolve/main/tokens.txt",
        configUrl = "https://huggingface.co/csukuangfj/vits-piper-en_US-amy-medium/resolve/main/en_US-amy-medium.onnx.json"
    ),
    PiperVoiceInfo(
        id = "en_GB-alan-medium",
        name = "Alan (Reino Unido)",
        language = "en",
        country = "GB",
        flag = "🇬🇧",
        onnxUrl = "https://huggingface.co/csukuangfj/vits-piper-en_GB-alan-medium/resolve/main/en_GB-alan-medium.onnx",
        tokensUrl = "https://huggingface.co/csukuangfj/vits-piper-en_GB-alan-medium/resolve/main/tokens.txt",
        configUrl = "https://huggingface.co/csukuangfj/vits-piper-en_GB-alan-medium/resolve/main/en_GB-alan-medium.onnx.json"
    )
)

fun getVoicesForLanguage(language: String): List<PiperVoiceInfo> {
    return AVAILABLE_PIPER_VOICES.filter { it.language.equals(language, ignoreCase = true) }
}

class PiperDownloader(private val context: Context) {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    fun getVoiceDir(voiceId: String): File {
        val dir = File(context.filesDir, "piper_voices/$voiceId")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun isVoiceDownloaded(voiceId: String): Boolean {
        val dir = getVoiceDir(voiceId)
        val onnxFile = File(dir, "$voiceId.onnx")
        val tokensFile = File(dir, "tokens.txt")
        return onnxFile.exists() && onnxFile.length() > 1000 && tokensFile.exists()
    }

    suspend fun downloadVoice(
        voice: PiperVoiceInfo,
        onProgress: (Float, String) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        val targetDir = getVoiceDir(voice.id)
        val onnxFile = File(targetDir, "${voice.id}.onnx")
        val tokensFile = File(targetDir, "tokens.txt")

        try {
            // 1. Download tokens.txt
            onProgress(0.05f, "Descargando vocabulario (tokens.txt)...")
            downloadFile(voice.tokensUrl, tokensFile) { _ -> }

            // 2. Download ONNX model file (~30-70 MB)
            onProgress(0.15f, "Descargando voz de ${voice.name}...")
            downloadFile(voice.onnxUrl, onnxFile) { progress ->
                val overall = 0.15f + (progress * 0.80f)
                onProgress(overall, "Descargando voz de ${voice.name} (${(progress * 100).toInt()}%)")
            }

            // 3. Optional config json
            if (!voice.configUrl.isNullOrBlank()) {
                val configFile = File(targetDir, "${voice.id}.onnx.json")
                try {
                    downloadFile(voice.configUrl, configFile) { _ -> }
                } catch (ignored: Exception) {
                    // Config json is optional for sherpa-onnx if tokens.txt and onnx are present
                }
            }

            onProgress(1.0f, "¡Voz instalada correctamente!")
            Result.success(targetDir)
        } catch (e: Exception) {
            Log.e("PiperDownloader", "Failed to download Piper voice: ${voice.id}", e)
            Result.failure(e)
        }
    }

    private fun downloadFile(url: String, destination: File, onProgress: (Float) -> Unit) {
        val request = Request.Builder().url(url).build()
        val response = httpClient.newCall(request).execute()

        if (!response.isSuccessful) {
            throw Exception("HTTP error ${response.code} downloading $url")
        }

        val body = response.body ?: throw Exception("Empty body downloading $url")
        val totalBytes = body.contentLength()

        body.byteStream().use { input ->
            FileOutputStream(destination).use { output ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                var totalRead = 0L

                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    totalRead += bytesRead
                    if (totalBytes > 0) {
                        onProgress(totalRead.toFloat() / totalBytes.toFloat())
                    }
                }
                output.flush()
            }
        }
    }
}
