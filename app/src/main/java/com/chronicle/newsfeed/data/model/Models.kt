package com.chronicle.newsfeed.data.model

data class FeedSource(
    val id: String,
    val name: String,
    val siteUrl: String,
    val feedUrl: String,
    val category: String = "General",
    val enabled: Boolean = true,
    val iconUrl: String? = null,
    val articleCount: Int = 0
)

data class Article(
    val id: String,
    val sourceId: String,
    val sourceName: String,
    val title: String,
    val link: String,
    val description: String = "",
    val content: String = "",
    val author: String = "",
    val pubDate: Long = System.currentTimeMillis(),
    val imageUrl: String? = null,
    val isRead: Boolean = false,
    val isFavorite: Boolean = false,
    val cachedSummary: String? = null,
    val category: String = "General"
)

data class DailyDigest(
    val id: String,
    val timestamp: Long = System.currentTimeMillis(),
    val text: String,
    val articleCount: Int,
    val language: String = "es"
)

enum class TtsEngineType {
    SYSTEM_TTS,
    PIPER_ONNX
}

data class TtsVoiceOption(
    val id: String,
    val name: String,
    val language: String,
    val region: String = "AR",
    val flag: String = "🇦🇷"
)

data class LlmSettings(
    val modelPath: String = "",
    val temperature: Float = 0.5f,
    val maxTokens: Int = 512,
    val language: String = "es"
)

data class TtsSettings(
    val engineType: TtsEngineType = TtsEngineType.SYSTEM_TTS,
    val piperModelDir: String = "",
    val selectedVoiceId: String = "es_AR-daniela-high",
    val voiceRegion: String = "AR",
    val speechRate: Float = 1.0f,
    val pitch: Float = 1.0f,
    val language: String = "es"
)

data class PromptSettings(
    val articleSummaryPromptEs: String = "",
    val articleSummaryPromptEn: String = "",
    val dailyDigestPromptEs: String = "",
    val dailyDigestPromptEn: String = ""
)

