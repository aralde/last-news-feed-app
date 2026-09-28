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
    private var currentJob: Job? = null
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

            val optimalThreads = Runtime.getRuntime().availableProcessors().coerceIn(2, 4)
            val modelConfig = OfflineTtsModelConfig().apply {
                vits = vitsConfig
                numThreads = optimalThreads
                debug = false
                provider = "cpu"
            }

            val config = OfflineTtsConfig().apply {
                model = modelConfig
                maxNumSentences = 2
            }

            tts = OfflineTts(assetManager = null, config = config)
            isReady = true
            Log.i("SherpaPiperTts", "Sherpa-ONNX Piper initialized successfully with model: ${modelFile.name} ($optimalThreads threads) and dataDir: $resolvedDataDir")
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

        val sentences = splitIntoSentences(text)
        if (sentences.isEmpty()) {
            isPlaying = false
            listener.onDone()
            return
        }

        currentJob = scope.launch {
            try {
                withContext(Dispatchers.Main) {
                    listener.onStart()
                }

                val speed = rate.coerceIn(0.5f, 2.0f)
                // Channel to pipeline synthesized audio chunks directly to playback
                val audioChannel = kotlinx.coroutines.channels.Channel<Pair<ShortArray, Int>>(capacity = 3)

                // Synthesis Coroutine (Producer)
                val generatorJob = launch(Dispatchers.Default) {
                    try {
                        for (sentence in sentences) {
                            if (!isActive || !isPlaying) break
                            val audio = engine.generate(sentence, sid = 0, speed = speed)
                            val samples = audio.samples
                            if (samples.isNotEmpty()) {
                                val pcm = floatArrayToPcm(samples)
                                audioChannel.send(Pair(pcm, audio.sampleRate))
                            }
                        }
                    } finally {
                        audioChannel.close()
                    }
                }

                // Playback Coroutine (Consumer)
                withContext(Dispatchers.IO) {
                    var track: AudioTrack? = null
                    var totalFramesWritten = 0L

                    try {
                        for ((pcm, sampleRate) in audioChannel) {
                            if (!isActive || !isPlaying) break

                            if (track == null) {
                                val minBufferSize = AudioTrack.getMinBufferSize(
                                    sampleRate,
                                    AudioFormat.CHANNEL_OUT_MONO,
                                    AudioFormat.ENCODING_PCM_16BIT
                                )
                                val bufferSize = (minBufferSize * 4).coerceAtLeast(sampleRate * 2)

                                val newTrack = AudioTrack.Builder()
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

                                audioTrack = newTrack
                                track = newTrack
                                newTrack.play()
                            }

                            var written = 0
                            while (written < pcm.size && isPlaying && isActive) {
                                val ret = track.write(pcm, written, pcm.size - written)
                                if (ret > 0) {
                                    written += ret
                                    totalFramesWritten += ret
                                } else {
                                    break
                                }
                            }
                        }

                        // Wait for track buffer to finish playing
                        if (track != null && isPlaying && isActive) {
                            while (isPlaying && isActive) {
                                val head = (track.playbackHeadPosition.toLong() and 0xFFFFFFFFL)
                                if (head >= totalFramesWritten) break
                                delay(50)
                            }
                        }
                    } finally {
                        generatorJob.cancel()
                        audioChannel.close()
                        try {
                            track?.stop()
                            track?.release()
                        } catch (_: Exception) {}
                        if (audioTrack == track) {
                            audioTrack = null
                        }
                    }
                }

                val wasPlaying = isPlaying
                isPlaying = false

                withContext(Dispatchers.Main) {
                    if (wasPlaying) {
                        listener.onDone()
                    }
                }
            } catch (e: Exception) {
                if (e !is CancellationException) {
                    Log.e("SherpaPiperTts", "Error synthesizing speech with Piper", e)
                    withContext(Dispatchers.Main) {
                        listener.onError("Error de Piper: ${e.localizedMessage ?: e.message}")
                    }
                }
                isPlaying = false
            }
        }
    }

    private fun floatArrayToPcm(samples: FloatArray): ShortArray {
        val pcm = ShortArray(samples.size)
        for (i in samples.indices) {
            val s = (samples[i] * 32767.0f).coerceIn(-32768.0f, 32767.0f)
            pcm[i] = s.toInt().toShort()
        }
        return pcm
    }

    private fun splitIntoSentences(text: String): List<String> {
        if (text.isBlank()) return emptyList()
        val cleanedText = text.replace("\r\n", "\n").replace('\r', '\n')
        val rawChunks = cleanedText.split(Regex("(?<=[.!?\\n])\\s+"))
        val result = mutableListOf<String>()

        for (chunk in rawChunks) {
            val trimmed = chunk.trim()
            if (trimmed.isEmpty()) continue

            if (trimmed.length > 200) {
                val subChunks = trimmed.split(Regex("(?<=[,;:])\\s+"))
                for (sub in subChunks) {
                    val subTrimmed = sub.trim()
                    if (subTrimmed.isNotEmpty()) {
                        result.add(subTrimmed)
                    }
                }
            } else {
                result.add(trimmed)
            }
        }

        return if (result.isNotEmpty()) result else listOf(cleanedText.trim())
    }

    override fun stop() {
        isPlaying = false
        currentJob?.cancel()
        currentJob = null
        try {
            audioTrack?.pause()
            audioTrack?.flush()
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
