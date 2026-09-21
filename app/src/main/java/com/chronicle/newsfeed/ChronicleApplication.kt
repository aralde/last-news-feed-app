package com.chronicle.newsfeed

import android.app.Application
import com.chronicle.newsfeed.data.local.ChronicleDatabase
import com.chronicle.newsfeed.data.repository.FeedRepository
import com.chronicle.newsfeed.data.repository.SettingsRepository
import com.chronicle.newsfeed.domain.llm.LlmManager
import com.chronicle.newsfeed.domain.tts.TtsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ChronicleApplication : Application() {

    lateinit var database: ChronicleDatabase
        private set

    lateinit var settingsRepository: SettingsRepository
        private set

    lateinit var llmManager: LlmManager
        private set

    lateinit var ttsManager: TtsManager
        private set

    lateinit var feedRepository: FeedRepository
        private set

    private val appScope = CoroutineScope(Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()

        database = ChronicleDatabase.getInstance(this)
        settingsRepository = SettingsRepository(this)
        llmManager = LlmManager(this)
        ttsManager = TtsManager(this)
        feedRepository = FeedRepository(
            context = this,
            database = database,
            llmManager = llmManager,
            settingsRepository = settingsRepository
        )

        // Seed default sources and init settings in background
        appScope.launch {
            feedRepository.initializeDefaultSourcesIfNeeded()
            val ttsSettings = settingsRepository.ttsSettingsFlow.first()
            ttsManager.updateSettings(ttsSettings)

            val llmSettings = settingsRepository.llmSettingsFlow.first()
            if (llmSettings.modelPath.isNotBlank()) {
                llmManager.initialize(llmSettings)
            }
        }
    }
}
