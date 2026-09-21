package com.chronicle.newsfeed.domain.tts

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

class SherpaPiperTtsEngine(
    private val context: Context,
    private val modelPath: String,
    private val tokensPath: String,
    private var dataDir: String = ""
) : TtsEngine {

    override val name: String = "Piper TTS (Sherpa-ONNX VITS)"
    override var isReady: Boolean = false
        private set

    private var tts: OfflineTts? = null
    private var audioTrack: AudioTrack? = null
    private var isPlaying = false
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    init {
        initializeEngine()
    }

    private fun initializeEngine() {
        val modelFile = File(modelPath)
        val tokensFile = File(tokensPath)

        if (!modelFile.exists() || !tokensFile.exists()) {
            Log.w("SherpaPiperTts", "Piper model or tokens not found. Model: $modelPath, Tokens: $tokensPath")
            isReady = false
            return
        }

        try {
            // Ensure espeak-ng-data directory exists and has phontab
            val resolvedDataDir = resolveEspeakNgData(context, dataDir)

            val vitsConfig = OfflineTtsVitsModelConfig().apply {
                model = modelPath
                tokens = tokensPath
                this.dataDir = resolvedDataDir
                noiseScale = 0.667f
                noiseScaleW = 0.8f
                lengthScale = 1.0f
            }

            val modelConfig = OfflineTtsModelConfig().apply {
                vits = vitsConfig
                numThreads = 2
                debug = false
                provider = "cpu"
            }

            val config = OfflineTtsConfig().apply {
                model = modelConfig
                maxNumSentences = 2
            }

            tts = OfflineTts(assetManager = null, config = config)
            isReady = true
            Log.i("SherpaPiperTts", "Sherpa-ONNX Piper initialized successfully with model: ${modelFile.name} and dataDir: $resolvedDataDir")
        } catch (e: Exception) {
            Log.e("SherpaPiperTts", "Failed to initialize Sherpa-ONNX Piper", e)
            isReady = false
        }
    }

    override fun speak(text: String, rate: Float, pitch: Float, listener: TtsEngineListener) {
        val engine = tts
        if (!isReady || engine == null) {
            listener.onError("El motor Piper no se pudo inicializar. Revisa los archivos de voz en Ajustes.")
            return
        }

        stop()
        isPlaying = true

        scope.launch {
            try {
                listener.onStart()
                val speed = rate.coerceIn(0.5f, 2.0f)

                val audio = engine.generate(text, sid = 0, speed = speed)
                val samples = audio.samples
                val sampleRate = audio.sampleRate

                if (samples == null || samples.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        listener.onError("Piper no generó audio para el texto proporcionado.")
                    }
                    isPlaying = false
                    return@launch
                }

                playSamples(samples, sampleRate, listener)
            } catch (e: Exception) {
                Log.e("SherpaPiperTts", "Error synthesizing speech with Piper", e)
                withContext(Dispatchers.Main) {
                    listener.onError("Error de Piper: ${e.localizedMessage ?: e.message}")
                }
                isPlaying = false
            }
        }
    }

    private suspend fun playSamples(samples: FloatArray, sampleRate: Int, listener: TtsEngineListener) = withContext(Dispatchers.IO) {
        val pcm = ShortArray(samples.size)
        for (i in samples.indices) {
            val s = (samples[i] * 32767.0f).coerceIn(-32768.0f, 32767.0f)
            pcm[i] = s.toInt().toShort()
        }

        val bufferSize = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(pcm.size * 2)

        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        audioTrack = track
        track.play()
        track.write(pcm, 0, pcm.size)

        val durationMs = (pcm.size * 1000L) / sampleRate
        var elapsed = 0L
        while (elapsed < durationMs && isPlaying) {
            delay(100)
            elapsed += 100
        }

        try {
            track.stop()
            track.release()
        } catch (_: Exception) {}

        audioTrack = null
        val wasPlaying = isPlaying
        isPlaying = false

        withContext(Dispatchers.Main) {
            if (wasPlaying) {
                listener.onDone()
            }
        }
    }

    override fun stop() {
        isPlaying = false
        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (_: Exception) {}
        audioTrack = null
    }

    override fun isSpeaking(): Boolean = isPlaying

    override fun release() {
        stop()
        scope.cancel()
        try {
            tts?.release()
        } catch (_: Exception) {}
        tts = null
        isReady = false
    }

    companion object {
        fun resolveEspeakNgData(context: Context, preferredDataDir: String = ""): String {
            if (preferredDataDir.isNotBlank()) {
                val dir = File(preferredDataDir)
                if (File(dir, "phontab").exists()) return dir.absolutePath
            }

            val targetDir = File(context.filesDir, "espeak-ng-data")
            val phontab = File(targetDir, "phontab")
            if (phontab.exists() && phontab.length() > 0) {
                return targetDir.absolutePath
            }

            // Extract from assets
            try {
                context.assets.open("espeak-ng-data.zip").use { zipInput ->
                    ZipInputStream(zipInput).use { zis ->
                        var entry = zis.nextEntry
                        while (entry != null) {
                            val outFile = File(context.filesDir, entry.name)
                            if (entry.isDirectory) {
                                outFile.mkdirs()
                            } else {
                                outFile.parentFile?.mkdirs()
                                FileOutputStream(outFile).use { out ->
                                    zis.copyTo(out)
                                }
                            }
                            zis.closeEntry()
                            entry = zis.nextEntry
                        }
                    }
                }
                Log.i("SherpaPiperTts", "Successfully extracted espeak-ng-data to ${targetDir.absolutePath}")
            } catch (e: Exception) {
                Log.e("SherpaPiperTts", "Failed to extract espeak-ng-data from assets", e)
            }
            return targetDir.absolutePath
        }
    }
}
