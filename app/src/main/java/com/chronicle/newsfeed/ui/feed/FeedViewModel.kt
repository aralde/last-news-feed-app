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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class FeedViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as ChronicleApplication
    private val repo = app.feedRepository
    private val ttsManager = app.ttsManager

    val playbackState: StateFlow<PlaybackState> = ttsManager.playbackState

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

    private data class FilterTuple4<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)

    val articles: StateFlow<List<Article>> = combine(
        combine(_selectedCategory, _selectedSources, _showOnlyUnread) { cat, src, unread ->
            Triple(cat, src, unread)
        },
        combine(_showOnlyFavorites, _searchQuery, _limitPerSource, _blockedTags) { fav, q, lim, blk ->
            FilterTuple4(fav, q, lim, blk)
        }
    ) { t1, t2 ->
        FilterState(t1.first, t1.second, t1.third, t2.a, t2.b, t2.c, t2.d)
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

            // 4. Group by source and apply limit per source
            val grouped = filtered.groupBy { it.sourceId }
            val limited = mutableListOf<Article>()
            for ((_, articles) in grouped) {
                limited.addAll(articles.take(filter.limit))
            }
            limited.sortedByDescending { it.pubDate }
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
        viewModelScope.launch {
            _synthesizingArticleId.value = article.id
            try {
                val summary = repo.getOrGenerateSummary(article)
                ttsManager.play(title = article.title, text = summary)
                AudioPlaybackService.startService(app, article.title)
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
        val digest = _dailyDigest.value ?: return
        ttsManager.play("Boletín de Noticias de Hoy", digest.text)
        AudioPlaybackService.startService(app, "Boletín de Noticias de Hoy")
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
        val limit: Int,
        val blocked: Set<String>
    )
}
