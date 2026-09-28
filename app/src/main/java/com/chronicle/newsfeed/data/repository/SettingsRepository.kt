package com.chronicle.newsfeed.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.chronicle.newsfeed.data.model.LlmSettings
import com.chronicle.newsfeed.data.model.PromptSettings
import com.chronicle.newsfeed.data.model.TtsEngineType
import com.chronicle.newsfeed.data.model.TtsSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "chronicle_settings")

class SettingsRepository(private val context: Context) {

    private object PreferencesKeys {
        val LLM_MODEL_PATH = stringPreferencesKey("llm_model_path")
        val LLM_TEMPERATURE = floatPreferencesKey("llm_temperature")
        val LLM_LANGUAGE = stringPreferencesKey("llm_language")
        val LLM_FETCH_FULL_ARTICLE_WEB = booleanPreferencesKey("llm_fetch_full_article_web")

        val TTS_ENGINE_TYPE = stringPreferencesKey("tts_engine_type")
        val TTS_PIPER_DIR = stringPreferencesKey("tts_piper_dir")
        val TTS_VOICE_ID = stringPreferencesKey("tts_voice_id")
        val TTS_VOICE_REGION = stringPreferencesKey("tts_voice_region")
        val TTS_SPEECH_RATE = floatPreferencesKey("tts_speech_rate")
        val TTS_PITCH = floatPreferencesKey("tts_pitch")
        val TTS_LANGUAGE = stringPreferencesKey("tts_language")

        val PROMPT_ARTICLE_SUMMARY_ES = stringPreferencesKey("prompt_article_summary_es")
        val PROMPT_ARTICLE_SUMMARY_EN = stringPreferencesKey("prompt_article_summary_en")
        val PROMPT_DAILY_DIGEST_ES = stringPreferencesKey("prompt_daily_digest_es")
        val PROMPT_DAILY_DIGEST_EN = stringPreferencesKey("prompt_daily_digest_en")
    }

    val llmSettingsFlow: Flow<LlmSettings> = context.dataStore.data.map { prefs ->
        LlmSettings(
            modelPath = prefs[PreferencesKeys.LLM_MODEL_PATH] ?: "",
            temperature = prefs[PreferencesKeys.LLM_TEMPERATURE] ?: 0.5f,
            language = prefs[PreferencesKeys.LLM_LANGUAGE] ?: "es",
            fetchFullArticleWeb = prefs[PreferencesKeys.LLM_FETCH_FULL_ARTICLE_WEB] ?: true
        )
    }

    val ttsSettingsFlow: Flow<TtsSettings> = context.dataStore.data.map { prefs ->
        val engineStr = prefs[PreferencesKeys.TTS_ENGINE_TYPE] ?: TtsEngineType.SYSTEM_TTS.name
        val engineType = try {
            TtsEngineType.valueOf(engineStr)
        } catch (_: Exception) {
            TtsEngineType.SYSTEM_TTS
        }

        TtsSettings(
            engineType = engineType,
            piperModelDir = prefs[PreferencesKeys.TTS_PIPER_DIR] ?: "",
            selectedVoiceId = prefs[PreferencesKeys.TTS_VOICE_ID] ?: "es_AR-daniela-high",
            voiceRegion = prefs[PreferencesKeys.TTS_VOICE_REGION] ?: "AR",
            speechRate = prefs[PreferencesKeys.TTS_SPEECH_RATE] ?: 1.0f,
            pitch = prefs[PreferencesKeys.TTS_PITCH] ?: 1.0f,
            language = prefs[PreferencesKeys.TTS_LANGUAGE] ?: "es"
        )
    }

    val promptSettingsFlow: Flow<PromptSettings> = context.dataStore.data.map { prefs ->
        PromptSettings(
            articleSummaryPromptEs = prefs[PreferencesKeys.PROMPT_ARTICLE_SUMMARY_ES] ?: "",
            articleSummaryPromptEn = prefs[PreferencesKeys.PROMPT_ARTICLE_SUMMARY_EN] ?: "",
            dailyDigestPromptEs = prefs[PreferencesKeys.PROMPT_DAILY_DIGEST_ES] ?: "",
            dailyDigestPromptEn = prefs[PreferencesKeys.PROMPT_DAILY_DIGEST_EN] ?: ""
        )
    }

    suspend fun getLlmSettingsSync(): LlmSettings = llmSettingsFlow.first()
    suspend fun getTtsSettingsSync(): TtsSettings = ttsSettingsFlow.first()
    suspend fun getPromptSettingsSync(): PromptSettings = promptSettingsFlow.first()

    suspend fun updateLlmSettings(settings: LlmSettings) {
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.LLM_MODEL_PATH] = settings.modelPath
            prefs[PreferencesKeys.LLM_TEMPERATURE] = settings.temperature
            prefs[PreferencesKeys.LLM_LANGUAGE] = settings.language
            prefs[PreferencesKeys.LLM_FETCH_FULL_ARTICLE_WEB] = settings.fetchFullArticleWeb
        }
    }

    suspend fun updateTtsSettings(settings: TtsSettings) {
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.TTS_ENGINE_TYPE] = settings.engineType.name
            prefs[PreferencesKeys.TTS_PIPER_DIR] = settings.piperModelDir
            prefs[PreferencesKeys.TTS_VOICE_ID] = settings.selectedVoiceId
            prefs[PreferencesKeys.TTS_VOICE_REGION] = settings.voiceRegion
            prefs[PreferencesKeys.TTS_SPEECH_RATE] = settings.speechRate
            prefs[PreferencesKeys.TTS_PITCH] = settings.pitch
            prefs[PreferencesKeys.TTS_LANGUAGE] = settings.language
        }
    }

    suspend fun updatePromptSettings(settings: PromptSettings) {
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.PROMPT_ARTICLE_SUMMARY_ES] = settings.articleSummaryPromptEs
            prefs[PreferencesKeys.PROMPT_ARTICLE_SUMMARY_EN] = settings.articleSummaryPromptEn
            prefs[PreferencesKeys.PROMPT_DAILY_DIGEST_ES] = settings.dailyDigestPromptEs
            prefs[PreferencesKeys.PROMPT_DAILY_DIGEST_EN] = settings.dailyDigestPromptEn
        }
    }

    suspend fun resetPromptSettings() {
        context.dataStore.edit { prefs ->
            prefs.remove(PreferencesKeys.PROMPT_ARTICLE_SUMMARY_ES)
            prefs.remove(PreferencesKeys.PROMPT_ARTICLE_SUMMARY_EN)
            prefs.remove(PreferencesKeys.PROMPT_DAILY_DIGEST_ES)
            prefs.remove(PreferencesKeys.PROMPT_DAILY_DIGEST_EN)
        }
    }
}
