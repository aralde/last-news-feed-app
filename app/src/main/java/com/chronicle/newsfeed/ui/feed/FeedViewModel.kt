package com.chronicle.newsfeed.ui.feed

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.chronicle.newsfeed.ChronicleApplication
import com.chronicle.newsfeed.data.model.Article
import com.chronicle.newsfeed.data.model.DailyDigest
import com.chronicle.newsfeed.data.model.FeedSource
import com.chronicle.newsfeed.domain.tts.PlaybackState
import com.chronicle.newsfeed.service.AudioPlaybackService
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

enum class SortOrder {
    NEWEST,
    BY_SOURCE
}

data class EngineInitState(
    val isLlmLoading: Boolean = false,
    val isTtsLoading: Boolean = false,
    val isReady: Boolean = false,
    val message: String = ""
)

@OptIn(ExperimentalCoroutinesApi::class)
class FeedViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as ChronicleApplication
    private val repo = app.feedRepository
    private val ttsManager = app.ttsManager
    private val llmManager = app.llmManager

    val playbackState: StateFlow<PlaybackState> = ttsManager.playbackState

    val appLanguage: StateFlow<String> = app.settingsRepository.llmSettingsFlow.map { it.language }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = "es"
    )

    val engineInitState: StateFlow<EngineInitState> = combine(
        llmManager.statusFlow,
        ttsManager.initStatus
    ) { llmStatus, ttsStatus ->
        val isLlmLoading = llmStatus is com.chronicle.newsfeed.domain.llm.LlmStatus.Loading
        val isTtsLoading = ttsStatus is com.chronicle.newsfeed.domain.tts.TtsInitStatus.Initializing
        val isReady = llmStatus is com.chronicle.newsfeed.domain.llm.LlmStatus.Ready && ttsStatus is com.chronicle.newsfeed.domain.tts.TtsInitStatus.Ready
        val msg = when {
            isLlmLoading && isTtsLoading -> "Iniciando motor de IA y voces…"
            isLlmLoading -> "Cargando modelo de IA Gemma…"
            isTtsLoading -> "Cargando síntesis de voz…"
            isReady -> "Motor de IA y voces listos"
            llmStatus is com.chronicle.newsfeed.domain.llm.LlmStatus.Error -> llmStatus.message
            else -> ""
        }
        EngineInitState(
            isLlmLoading = isLlmLoading,
            isTtsLoading = isTtsLoading,
            isReady = isReady,
            message = msg
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = EngineInitState(isLlmLoading = true, isTtsLoading = true, message = "Iniciando…")
    )

    val sources: StateFlow<List<FeedSource>> = repo.getSources().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private val _selectedCategory = MutableStateFlow("Todos")
    val selectedCategory = _selectedCategory.asStateFlow()

    private val _selectedSources = MutableStateFlow<Set<String>>(emptySet())
    val selectedSources = _selectedSources.asStateFlow()

    private val _showOnlyUnread = MutableStateFlow(false)
    val showOnlyUnread = _showOnlyUnread.asStateFlow()

    private val _showOnlyFavorites = MutableStateFlow(false)
    val showOnlyFavorites = _showOnlyFavorites.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _sortOrder = MutableStateFlow(SortOrder.NEWEST)
    val sortOrder = _sortOrder.asStateFlow()

    private val _limitPerSource = MutableStateFlow(10)
    val limitPerSource = _limitPerSource.asStateFlow()

    private val _blockedTags = MutableStateFlow<Set<String>>(emptySet())
    val blockedTags = _blockedTags.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing = _isSyncing.asStateFlow()

    private val _isAddingSource = MutableStateFlow(false)
    val isAddingSource = _isAddingSource.asStateFlow()

    private val _synthesizingArticleId = MutableStateFlow<String?>(null)
    val synthesizingArticleId = _synthesizingArticleId.asStateFlow()

    private val _dailyDigest = MutableStateFlow<DailyDigest?>(null)
    val dailyDigest = _dailyDigest.asStateFlow()

    private val _isGeneratingDigest = MutableStateFlow(false)
    val isGeneratingDigest = _isGeneratingDigest.asStateFlow()

    // Custom Bulletin State
    private val _isCustomBulletinMode = MutableStateFlow(false)
    val isCustomBulletinMode = _isCustomBulletinMode.asStateFlow()

    private val _selectedArticleIdsForDigest = MutableStateFlow<Set<String>>(emptySet())
    val selectedArticleIdsForDigest = _selectedArticleIdsForDigest.asStateFlow()

    private val _customDigest = MutableStateFlow<DailyDigest?>(null)
    val customDigest = _customDigest.asStateFlow()

    private val _isGeneratingCustomDigest = MutableStateFlow(false)
    val isGeneratingCustomDigest = _isGeneratingCustomDigest.asStateFlow()

    // Sequential Queue Playback State
    private var queuePlaybackJob: Job? = null
    private var prefetchJob: Job? = null
    private val summaryDeferredMap = ConcurrentHashMap<String, CompletableDeferred<String>>()
    private val _queueArticles = MutableStateFlow<List<Article>>(emptyList())
    val queueArticles = _queueArticles.asStateFlow()

    private val _currentQueueIndex = MutableStateFlow(-1)
    val currentQueueIndex = _currentQueueIndex.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage = _errorMessage.asStateFlow()

    init {
        viewModelScope.launch {
            ttsManager.playbackState.collect { playback ->
                if (playback.errorMessage != null) {
                    _errorMessage.value = playback.errorMessage
                }
            }
        }
    }

    // Base all articles stream to compute counts
    private val allBaseArticles: StateFlow<List<Article>> = repo.getArticles().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val allCount: StateFlow<Int> = allBaseArticles.map { it.size }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 0
    )

    val unreadCount: StateFlow<Int> = allBaseArticles.map { list -> list.count { !it.isRead } }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 0
    )

    val favoritesCount: StateFlow<Int> = allBaseArticles.map { list -> list.count { it.isFavorite } }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 0
    )

    private data class FilterTuple5<A, B, C, D, E>(val a: A, val b: B, val c: C, val d: D, val e: E)

    val articles: StateFlow<List<Article>> = combine(
        combine(_selectedCategory, _selectedSources, _showOnlyUnread) { cat, src, unread ->
            Triple(cat, src, unread)
        },
        combine(_showOnlyFavorites, _searchQuery, _sortOrder, _limitPerSource, _blockedTags) { fav, q, sort, lim, blk ->
            FilterTuple5(fav, q, sort, lim, blk)
        }
    ) { t1, t2 ->
        FilterState(t1.first, t1.second, t1.third, t2.a, t2.b, t2.c, t2.d, t2.e)
    }.flatMapLatest { filter ->
        repo.getArticles(
            categoryFilter = filter.category,
            showOnlyUnread = filter.unread,
            showOnlyFavorites = filter.favorites
        ).map { list ->
            var filtered = list

            // 1. Filter by specific selected sources (if any selected)
            if (filter.sourceIds.isNotEmpty()) {
                filtered = filtered.filter { filter.sourceIds.contains(it.sourceId) }
            }

            // 2. Filter out blocked hashtags/topics
            if (filter.blocked.isNotEmpty()) {
                filtered = filtered.filter { article ->
                    val combinedText = "${article.title} ${article.description} ${article.category}".lowercase()
                    filter.blocked.none { tag -> combinedText.contains(tag.lowercase()) }
                }
            }

            // 3. Search query
            if (filter.query.isNotBlank()) {
                val q = filter.query.lowercase()
                filtered = filtered.filter {
                    it.title.lowercase().contains(q) ||
                    it.description.lowercase().contains(q) ||
                    it.sourceName.lowercase().contains(q)
                }
            }

            // 4. Default Sort by Recent (Newest first) across all sources!
            if (filter.sortOrder == SortOrder.BY_SOURCE) {
                val grouped = filtered.groupBy { it.sourceId }
                val limited = mutableListOf<Article>()
                for ((_, articles) in grouped) {
                    limited.addAll(articles.take(filter.limit))
                }
                limited.sortedByDescending { it.pubDate }
            } else {
                filtered.sortedByDescending { it.pubDate }
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )


    init {
        syncFeeds()
    }

    fun syncFeeds() {
        viewModelScope.launch {
            _isSyncing.value = true
            try {
                repo.syncAllEnabledFeeds()
            } catch (e: Exception) {
                _errorMessage.value = "Error sincronizando feeds: ${e.localizedMessage}"
            } finally {
                _isSyncing.value = false
            }
        }
    }

    fun setCategory(category: String) {
        _selectedCategory.value = category
    }

    fun toggleSourceFilter(sourceId: String) {
        val current = _selectedSources.value.toMutableSet()
        if (current.contains(sourceId)) {
            current.remove(sourceId)
        } else {
            current.add(sourceId)
        }
        _selectedSources.value = current
    }

    fun clearSourceFilters() {
        _selectedSources.value = emptySet()
    }

    fun setLimitPerSource(limit: Int) {
        _limitPerSource.value = limit
    }

    fun addBlockedTag(tag: String) {
        val clean = tag.trim().removePrefix("#")
        if (clean.isNotBlank()) {
            _blockedTags.value = _blockedTags.value + clean
        }
    }

    fun removeBlockedTag(tag: String) {
        _blockedTags.value = _blockedTags.value - tag
    }

    fun resetAllFilters() {
        _selectedCategory.value = "Todos"
        _selectedSources.value = emptySet()
        _showOnlyUnread.value = false
        _showOnlyFavorites.value = false
        _searchQuery.value = ""
        _blockedTags.value = emptySet()
    }

    fun toggleUnread(show: Boolean) {
        _showOnlyUnread.value = show
    }

    fun toggleFavorites(show: Boolean) {
        _showOnlyFavorites.value = show
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun markAllRead() {
        viewModelScope.launch {
            repo.markAllAsRead()
        }
    }

    fun toggleArticleRead(article: Article) {
        viewModelScope.launch {
            repo.setReadStatus(article.id, !article.isRead)
        }
    }

    fun toggleArticleFavorite(article: Article) {
        viewModelScope.launch {
            repo.setFavoriteStatus(article.id, !article.isFavorite)
        }
    }

    fun toggleSource(sourceId: String, enabled: Boolean) {
        viewModelScope.launch {
            repo.setSourceEnabled(sourceId, enabled)
        }
    }

    fun deleteSource(sourceId: String) {
        viewModelScope.launch {
            repo.deleteSource(sourceId)
        }
    }

    fun addSource(url: String, category: String) {
        viewModelScope.launch {
            _isAddingSource.value = true
            val result = repo.addSourceFromUrl(url, category)
            _isAddingSource.value = false
            if (result.isFailure) {
                _errorMessage.value = result.exceptionOrNull()?.localizedMessage ?: "No se pudo añadir la fuente"
            }
        }
    }

    fun narrateArticle(article: Article) {
        prefetchJob?.cancel()
        summaryDeferredMap.clear()
        queuePlaybackJob?.cancel()
        _currentQueueIndex.value = -1
        _queueArticles.value = emptyList()

        viewModelScope.launch {
            _synthesizingArticleId.value = article.id
            try {
                val rawSummary = repo.getOrGenerateSummary(article)
                val localizedStory = parseLocalizedStory(rawSummary, article.title)
                val spokenText = "${localizedStory.localizedTitle}. ${localizedStory.summaryBody}"
                ttsManager.play(title = localizedStory.localizedTitle, text = spokenText)
                AudioPlaybackService.startService(app, localizedStory.localizedTitle)
                repo.setReadStatus(article.id, true)
            } catch (e: Exception) {
                _errorMessage.value = e.localizedMessage ?: "Error generando locución con Gemma"
            } finally {
                _synthesizingArticleId.value = null
            }
        }
    }

    fun generateDailyDigest() {
        viewModelScope.launch {
            _isGeneratingDigest.value = true
            try {
                val digest = repo.generateDailyDigest()
                _dailyDigest.value = digest
            } catch (e: Exception) {
                _errorMessage.value = e.localizedMessage ?: "Error generando boletín con Gemma"
            } finally {
                _isGeneratingDigest.value = false
            }
        }
    }

    fun playDailyDigest() {
        prefetchJob?.cancel()
        summaryDeferredMap.clear()
        queuePlaybackJob?.cancel()
        _currentQueueIndex.value = -1
        _queueArticles.value = emptyList()

        val digest = _dailyDigest.value ?: return
        ttsManager.play("Boletín de Noticias de Hoy", digest.text)
        AudioPlaybackService.startService(app, "Boletín de Noticias de Hoy")
    }

    fun setSortOrder(order: SortOrder) {
        _sortOrder.value = order
    }

    fun toggleCustomBulletinMode() {
        val next = !_isCustomBulletinMode.value
        _isCustomBulletinMode.value = next
        if (!next) {
            _selectedArticleIdsForDigest.value = emptySet()
        }
    }

    fun exitCustomBulletinMode() {
        _isCustomBulletinMode.value = false
        _selectedArticleIdsForDigest.value = emptySet()
    }

    fun toggleArticleForDigest(articleId: String) {
        val current = _selectedArticleIdsForDigest.value.toMutableSet()
        if (current.contains(articleId)) {
            current.remove(articleId)
        } else {
            current.add(articleId)
        }
        _selectedArticleIdsForDigest.value = current
    }

    fun selectAllForDigest(articleIds: List<String>) {
        _selectedArticleIdsForDigest.value = articleIds.take(15).toSet()
    }

    fun clearDigestSelection() {
        _selectedArticleIdsForDigest.value = emptySet()
    }

    data class LocalizedNewsStory(
        val localizedTitle: String,
        val summaryBody: String
    )

    private fun parseLocalizedStory(rawSummary: String, fallbackTitle: String): LocalizedNewsStory {
        val clean = rawSummary.trim()
        val lines = clean.lines().map { it.trim() }.filter { it.isNotBlank() }

        // 1. If Gemma separated the translated headline on Line 1 and the summary body on Line 2+
        if (lines.size >= 2) {
            val rawFirstLine = lines[0]
                .removePrefix("Titular:")
                .removePrefix("Titular traducido:")
                .removePrefix("Titular adaptado:")
                .removePrefix("Headline:")
                .removePrefix("Translated headline:")
                .trim(' ', '"', '*', ':', '—', '-')
            val rest = lines.drop(1).joinToString(" ") {
                it.removePrefix("Resumen:")
                    .removePrefix("Crónica:")
                    .removePrefix("Summary:")
                    .trim()
            }
            if (rawFirstLine.isNotBlank() && rest.isNotBlank()) {
                return LocalizedNewsStory(rawFirstLine, rest)
            }
        }

        // 2. If it's a single block of text (e.g. from previously cached summaries in Spanish):
        // Extract the introductory statement/sentence in Spanish as the interpreted headline!
        val firstSentenceEnd = clean.indexOfAny(charArrayOf('.', '!', '?'))
        if (firstSentenceEnd in 15..140) {
            val firstSentence = clean.substring(0, firstSentenceEnd).trim()
            val remaining = clean.substring(firstSentenceEnd + 1).trim()
            if (remaining.isNotBlank()) {
                return LocalizedNewsStory(firstSentence, remaining)
            }
        }

        // 3. Fallback: if summary is short or without clear sentence end, use the summary itself
        return LocalizedNewsStory(fallbackTitle, clean)
    }

    private fun formatStoryQueueIntro(index: Int, total: Int, language: String): String {
        val isEnglish = language.equals("en", ignoreCase = true)
        return if (isEnglish) {
            when (index) {
                0 -> "First story"
                1 -> "Second story"
                2 -> "Third story"
                3 -> "Fourth story"
                4 -> "Fifth story"
                5 -> "Sixth story"
                6 -> "Seventh story"
                7 -> "Eighth story"
                8 -> "Ninth story"
                9 -> "Tenth story"
                else -> "Story number ${index + 1}"
            }
        } else {
            when (index) {
                0 -> "Primera noticia"
                1 -> "Segunda noticia"
                2 -> "Tercera noticia"
                3 -> "Cuarta noticia"
                4 -> "Quinta noticia"
                5 -> "Sexta noticia"
                6 -> "Séptima noticia"
                7 -> "Octava noticia"
                8 -> "Novena noticia"
                9 -> "Décima noticia"
                else -> "Noticia número ${index + 1}"
            }
        }
    }

    fun playSelectedArticles(allArticlesList: List<Article>) {
        val selectedIds = _selectedArticleIdsForDigest.value
        val selectedArticles = allArticlesList.filter { selectedIds.contains(it.id) }
        if (selectedArticles.isEmpty()) {
            _errorMessage.value = if (appLanguage.value == "en") "Select at least one story" else "Selecciona al menos una noticia"
            return
        }

        exitCustomBulletinMode()
        _queueArticles.value = selectedArticles

        prefetchJob?.cancel()
        queuePlaybackJob?.cancel()
        summaryDeferredMap.clear()

        // Create a CompletableDeferred for each story in the queue
        selectedArticles.forEach { article ->
            summaryDeferredMap[article.id] = CompletableDeferred()
        }

        // Background worker: pipeline prefetching of all selected stories without gaps
        prefetchJob = viewModelScope.launch(Dispatchers.IO) {
            for (art in selectedArticles) {
                if (!isActive) break
                val deferred = summaryDeferredMap[art.id] ?: continue
                if (deferred.isCompleted) continue
                try {
                    val summary = repo.getOrGenerateSummary(art)
                    deferred.complete(summary)
                } catch (e: Exception) {
                    deferred.completeExceptionally(e)
                }
            }
        }

        // Start playing the first item immediately
        playQueueItem(0)
    }

    fun playQueueItem(index: Int) {
        val queue = _queueArticles.value
        if (index < 0 || index >= queue.size) {
            _currentQueueIndex.value = -1
            _queueArticles.value = emptyList()
            prefetchJob?.cancel()
            summaryDeferredMap.clear()
            return
        }

        _currentQueueIndex.value = index
        val article = queue[index]

        queuePlaybackJob?.cancel()
        queuePlaybackJob = viewModelScope.launch {
            try {
                val deferred = summaryDeferredMap[article.id]
                val summary: String = if (deferred != null) {
                    if (!deferred.isCompleted) {
                        _synthesizingArticleId.value = article.id
                    }
                    val res = deferred.await()
                    _synthesizingArticleId.value = null
                    res
                } else {
                    _synthesizingArticleId.value = article.id
                    val res = repo.getOrGenerateSummary(article)
                    _synthesizingArticleId.value = null
                    res
                }

                repo.setReadStatus(article.id, true)

                // Parse translated/interpreted headline and summary in the spoken language
                val localizedStory = parseLocalizedStory(summary, article.title)

                // Spoken intro: "Primera noticia: [Título traducido e interpretado]. [Cuerpo del resumen]"
                val intro = formatStoryQueueIntro(index, queue.size, appLanguage.value)
                val spokenText = "$intro: ${localizedStory.localizedTitle}. ${localizedStory.summaryBody}"

                ttsManager.play(
                    title = "(${index + 1}/${queue.size}) ${localizedStory.localizedTitle}",
                    text = spokenText,
                    onDone = {
                        viewModelScope.launch {
                            playQueueItem(index + 1)
                        }
                    }
                )
                AudioPlaybackService.startService(app, localizedStory.localizedTitle)
            } catch (e: Exception) {
                _synthesizingArticleId.value = null
                _errorMessage.value = e.localizedMessage ?: "Error generando locución con Gemma"
                if (index + 1 < queue.size) {
                    playQueueItem(index + 1)
                } else {
                    _currentQueueIndex.value = -1
                    _queueArticles.value = emptyList()
                    prefetchJob?.cancel()
                    summaryDeferredMap.clear()
                }
            }
        }
    }

    fun skipQueueNext() {
        val nextIndex = _currentQueueIndex.value + 1
        if (nextIndex < _queueArticles.value.size) {
            ttsManager.stop()
            playQueueItem(nextIndex)
        } else {
            stopAudio()
        }
    }

    fun stopAudio() {
        prefetchJob?.cancel()
        summaryDeferredMap.clear()
        queuePlaybackJob?.cancel()
        _currentQueueIndex.value = -1
        _queueArticles.value = emptyList()
        ttsManager.stop()
        AudioPlaybackService.stopService(app)
    }

    fun generateCustomDigest(allArticlesList: List<Article>) {
        playSelectedArticles(allArticlesList)
    }

    fun playCustomDigest() {
        // Obsolete custom digest replaced by sequential queue playback
    }

    fun clearCustomDigest() {
        _customDigest.value = null
    }

    fun clearDigest() {
        _dailyDigest.value = null
    }

    fun clearError() {
        _errorMessage.value = null
    }

    private data class FilterState(
        val category: String,
        val sourceIds: Set<String>,
        val unread: Boolean,
        val favorites: Boolean,
        val query: String,
        val sortOrder: SortOrder,
        val limit: Int,
        val blocked: Set<String>
    )
}

