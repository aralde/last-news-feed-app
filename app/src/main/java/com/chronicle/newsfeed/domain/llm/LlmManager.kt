package com.chronicle.newsfeed.domain.llm

import android.content.Context
import android.util.Log
import com.chronicle.newsfeed.data.model.LlmSettings
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

sealed class LlmStatus {
    object Uninitialized : LlmStatus()
    object Loading : LlmStatus()
    data class Ready(val modelName: String) : LlmStatus()
    data class Error(val message: String) : LlmStatus()
}

class LlmManager(private val context: Context) {

    private var liteRtRunner: LiteRtLmRunner? = null
    private var llmInference: LlmInference? = null
    private var currentModelPath: String = ""
    private var isInitializing = false

    var status: LlmStatus = LlmStatus.Uninitialized
        private set

    suspend fun initialize(settings: LlmSettings) = withContext(Dispatchers.IO) {
        if (settings.modelPath.isBlank()) {
            status = LlmStatus.Uninitialized
            return@withContext
        }

        val file = File(settings.modelPath)
        if (!file.exists() || !file.canRead()) {
            status = LlmStatus.Error("Archivo de modelo no encontrado en: ${settings.modelPath}")
            return@withContext
        }

        if ((liteRtRunner != null || llmInference != null) && currentModelPath == settings.modelPath) {
            return@withContext
        }

        try {
            isInitializing = true
            status = LlmStatus.Loading
            close()

            if (settings.modelPath.endsWith(".litertlm", ignoreCase = true)) {
                // Initialize using Google LiteRT-LM (reflection)
                val runner = LiteRtLmRunner(
                    modelPath = settings.modelPath,
                    maxTokens = settings.maxTokens,
                    cacheDir = context.cacheDir.absolutePath
                )
                runner.initialize()
                liteRtRunner = runner
            } else {
                // Initialize using Google MediaPipe Tasks GenAI (.bin / .task)
                val options = LlmInference.LlmInferenceOptions.builder()
                    .setModelPath(settings.modelPath)
                    .setMaxTokens(settings.maxTokens)
                    .build()

                llmInference = LlmInference.createFromOptions(context, options)
            }

            currentModelPath = settings.modelPath
            status = LlmStatus.Ready(file.name)
            Log.i("LlmManager", "Gemma model loaded successfully from ${file.name}")
        } catch (e: Exception) {
            Log.e("LlmManager", "Failed to initialize Gemma model", e)
            status = LlmStatus.Error("Error cargando Gemma: ${e.localizedMessage}")
        } finally {
            isInitializing = false
        }
    }

    private fun generateText(prompt: String): String {
        liteRtRunner?.let { runner ->
            val result = runner.generate(prompt)
            if (result.isNotBlank()) return result
        }

        llmInference?.let { inference ->
            return inference.generateResponse(prompt)
        }

        throw Exception("El motor de inferencia Gemma no está disponible.")
    }

    suspend fun summarizeArticle(
        title: String,
        content: String,
        description: String,
        settings: LlmSettings,
        promptSettings: com.chronicle.newsfeed.data.model.PromptSettings? = null
    ): String = withContext(Dispatchers.IO) {
        if ((liteRtRunner == null && llmInference == null) || status !is LlmStatus.Ready) {
            throw Exception("El modelo Gemma no está cargado. Ve a Ajustes y selecciona el archivo del modelo (.litertlm / .bin / .task).")
        }

        val lang = settings.language
        val customTemplate = if (lang == "en") promptSettings?.articleSummaryPromptEn else promptSettings?.articleSummaryPromptEs
        val prompt = NewsPrompts.getNewsSummaryPrompt(
            title = title,
            content = content.ifBlank { description },
            language = lang,
            customTemplate = customTemplate ?: ""
        )

        try {
            val rawResponse = generateText(prompt)
            val clean = cleanAiResponse(rawResponse)
            if (clean.isNotBlank()) {
                return@withContext clean
            }
            throw Exception("El modelo Gemma devolvió una respuesta vacía.")
        } catch (e: Exception) {
            val actual = if (e is java.lang.reflect.InvocationTargetException) (e.targetException ?: e) else e
            val errorMsg = actual.localizedMessage ?: actual.message ?: actual.javaClass.simpleName
            Log.e("LlmManager", "Error generating response with Gemma", actual)
            throw Exception("Error de inferencia con Gemma: $errorMsg")
        }
    }

    suspend fun generateDailyDigest(
        articles: List<Pair<String, String>>,
        settings: LlmSettings,
        promptSettings: com.chronicle.newsfeed.data.model.PromptSettings? = null
    ): String = withContext(Dispatchers.IO) {
        if ((liteRtRunner == null && llmInference == null) || status !is LlmStatus.Ready) {
            throw Exception("El modelo Gemma no está cargado. Ve a Ajustes y selecciona el archivo del modelo (.litertlm / .bin / .task).")
        }

        val lang = settings.language
        val customTemplate = if (lang == "en") promptSettings?.dailyDigestPromptEn else promptSettings?.dailyDigestPromptEs
        val prompt = NewsPrompts.getDailyDigestPrompt(
            articles = articles,
            language = lang,
            customTemplate = customTemplate ?: ""
        )

        try {
            val rawResponse = generateText(prompt)
            val clean = cleanAiResponse(rawResponse)
            if (clean.isNotBlank()) {
                return@withContext clean
            }
            throw Exception("El modelo Gemma no generó texto para el boletín.")
        } catch (e: Exception) {
            val actual = if (e is java.lang.reflect.InvocationTargetException) (e.targetException ?: e) else e
            val errorMsg = actual.localizedMessage ?: actual.message ?: actual.javaClass.simpleName
            Log.e("LlmManager", "Error generating daily digest with Gemma", actual)
            throw Exception("Error generando el boletín con Gemma: $errorMsg")
        }
    }

    private fun cleanAiResponse(raw: String): String {
        var clean = raw.replace(Regex("[*#`_~>]"), "").trim()
        val prefixes = listOf(
            "resumen:", "summary:", "locutor:", "locución:", "locucion:",
            "resumen :", "summary :", "locutor :", "locución :", "locucion :"
        )
        for (prefix in prefixes) {
            if (clean.startsWith(prefix, ignoreCase = true)) {
                clean = clean.substring(prefix.length).trim()
            }
        }
        return clean.replace(Regex("\\s+"), " ").trim()
    }

    fun close() {
        try {
            liteRtRunner?.close()
            liteRtRunner = null
            llmInference?.close()
            llmInference = null
            status = LlmStatus.Uninitialized
        } catch (_: Exception) {}
    }
}

private class LiteRtLmRunner(
    private val modelPath: String,
    private val maxTokens: Int,
    private val cacheDir: String
) {
    private var engine: Any? = null
    private var createConversationMethod: java.lang.reflect.Method? = null
    private var sendMessageMethod: java.lang.reflect.Method? = null
    private var closeEngineMethod: java.lang.reflect.Method? = null
    private var closeConvMethod: java.lang.reflect.Method? = null

    fun initialize() {
        val backendClass = Class.forName("com.google.ai.edge.litertlm.Backend")
        val gpuClass = Class.forName("com.google.ai.edge.litertlm.Backend\$GPU")
        val cpuClass = Class.forName("com.google.ai.edge.litertlm.Backend\$CPU")

        // In MenuWhisperer, Backend.GPU() is used for Gemma-4-E2B compiled models
        val backendObj = try {
            gpuClass.getDeclaredConstructor().newInstance()
        } catch (e: Exception) {
            Log.w("LiteRtLmRunner", "Backend.GPU() failed to instantiate, falling back to CPU", e)
            cpuClass.getDeclaredConstructor().newInstance()
        }

        val configClass = Class.forName("com.google.ai.edge.litertlm.EngineConfig")
        val configConstructor = configClass.getConstructor(
            String::class.java,
            backendClass,
            backendClass,
            backendClass,
            java.lang.Integer::class.java,
            String::class.java
        )
        // Exactly as in MenuWhisperer: modelPath and Backend.GPU() with default nulls
        val config = configConstructor.newInstance(modelPath, backendObj, null, null, null, null)

        val engineClass = Class.forName("com.google.ai.edge.litertlm.Engine")
        val engineConstructor = engineClass.getConstructor(configClass)
        val eng = engineConstructor.newInstance(config)

        val initMethod = engineClass.getMethod("initialize")
        initMethod.invoke(eng)

        engine = eng
        closeEngineMethod = engineClass.getMethod("close")
        createConversationMethod = engineClass.methods.firstOrNull { it.name == "createConversation" }
    }

    fun generate(prompt: String): String {
        val eng = engine ?: throw IllegalStateException("LiteRT Engine no inicializado")

        // Match MenuWhisperer: ConversationConfig with SamplerConfig(temperature = 0.1, topK = 40, topP = 0.95)
        val convConfigClass = Class.forName("com.google.ai.edge.litertlm.ConversationConfig")
        val samplerConfigClass = Class.forName("com.google.ai.edge.litertlm.SamplerConfig")
        val samplerConfig = samplerConfigClass.getConstructor(
            Int::class.javaPrimitiveType,
            Double::class.javaPrimitiveType,
            Double::class.javaPrimitiveType,
            Int::class.javaPrimitiveType
        ).newInstance(40, 0.95, 0.1, 0)

        val convConfigConstructor = convConfigClass.getConstructor(
            Class.forName("com.google.ai.edge.litertlm.Contents"),
            List::class.java,
            List::class.java,
            samplerConfigClass
        )
        val convConfig = convConfigConstructor.newInstance(null, emptyList<Any>(), emptyList<Any>(), samplerConfig)

        val conv = createConversationMethod!!.invoke(eng, convConfig)
            ?: throw IllegalStateException("No se pudo crear conversación con LiteRT")

        try {
            val convClass = conv.javaClass
            if (closeConvMethod == null) {
                closeConvMethod = convClass.getMethod("close")
            }

            // Match MenuWhisperer: conversation.sendMessage(Contents.of(prompt))
            val contentsClass = Class.forName("com.google.ai.edge.litertlm.Contents")
            val contentsCompanion = contentsClass.getField("Companion").get(null)
            val ofMethod = contentsCompanion.javaClass.getMethod("of", String::class.java)
            val contentsObj = ofMethod.invoke(contentsCompanion, prompt)

            if (sendMessageMethod == null) {
                sendMessageMethod = convClass.methods.firstOrNull {
                    it.name == "sendMessage" && it.parameterTypes.isNotEmpty() &&
                    it.parameterTypes[0].name == "com.google.ai.edge.litertlm.Contents"
                }
            }

            val msg = if (sendMessageMethod!!.parameterCount == 1) {
                sendMessageMethod!!.invoke(conv, contentsObj)
            } else {
                sendMessageMethod!!.invoke(conv, contentsObj, emptyMap<String, Any>())
            } ?: throw IllegalStateException("LiteRT devolvió respuesta nula")

            val text = extractTextFromMessage(msg)
            if (text.isNotBlank()) return text
            return msg.toString()
        } catch (e: java.lang.reflect.InvocationTargetException) {
            val target = e.targetException ?: e
            Log.e("LiteRtLmRunner", "Inference invocation target error", target)
            throw Exception(target.localizedMessage ?: target.message ?: target.toString())
        } catch (e: Exception) {
            Log.e("LiteRtLmRunner", "Inference error", e)
            throw Exception(e.localizedMessage ?: e.message ?: e.toString())
        } finally {
            try {
                closeConvMethod?.invoke(conv)
            } catch (_: Exception) {}
        }
    }

    private fun extractTextFromMessage(msg: Any): String {
        try {
            val getContents = msg.javaClass.getMethod("getContents")
            val contents = getContents.invoke(msg) ?: return msg.toString()
            val getList = contents.javaClass.getMethod("getContents")
            val list = getList.invoke(contents) as? List<*> ?: return msg.toString()
            val texts = mutableListOf<String>()
            for (item in list) {
                if (item != null) {
                    val getTextMethod = item.javaClass.methods.firstOrNull { it.name == "getText" && it.parameterCount == 0 }
                    val text = getTextMethod?.invoke(item) as? String
                    if (!text.isNullOrBlank()) {
                        texts.add(text)
                    }
                }
            }
            if (texts.isNotEmpty()) {
                return texts.joinToString("\n")
            }
        } catch (e: Exception) {
            Log.w("LiteRtLmRunner", "Error extracting text from Message, fallback to toString()", e)
        }
        return msg.toString()
    }

    fun close() {
        try {
            closeEngineMethod?.invoke(engine)
        } catch (_: Exception) {}
        engine = null
    }
}
