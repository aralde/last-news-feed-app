package com.chronicle.newsfeed.data.repository

import android.content.Context
import android.util.Log
import com.chronicle.newsfeed.R
import com.chronicle.newsfeed.data.local.ChronicleDatabase
import com.chronicle.newsfeed.data.local.entity.ArticleEntity
import com.chronicle.newsfeed.data.local.entity.CachedSummaryEntity
import com.chronicle.newsfeed.data.local.entity.FeedSourceEntity
import com.chronicle.newsfeed.data.model.Article
import com.chronicle.newsfeed.data.model.DailyDigest
import com.chronicle.newsfeed.data.model.FeedSource
import com.chronicle.newsfeed.domain.feed.FeedDiscoveryService
import com.chronicle.newsfeed.domain.feed.FeedParser
import com.chronicle.newsfeed.domain.llm.LlmManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.util.concurrent.TimeUnit

class FeedRepository(
    private val context: Context,
    private val database: ChronicleDatabase,
    private val llmManager: LlmManager,
    private val settingsRepository: SettingsRepository,
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build(),
    private val feedParser: FeedParser = FeedParser(),
    private val discoveryService: FeedDiscoveryService = FeedDiscoveryService(httpClient)
) {

    private val sourceDao = database.feedSourceDao()
    private val articleDao = database.articleDao()
    private val summaryDao = database.cachedSummaryDao()

    suspend fun initializeDefaultSourcesIfNeeded() = withContext(Dispatchers.IO) {
        val count = sourceDao.getSourceCount()
        if (count == 0) {
            try {
                val inputStream = context.resources.openRawResource(R.raw.default_sources)
                val jsonString = inputStream.bufferedReader().use { it.readText() }
                val jsonArray = JSONArray(jsonString)
                val defaultSources = mutableListOf<FeedSourceEntity>()

                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    defaultSources.add(
                        FeedSourceEntity(
                            id = obj.getString("id"),
                            name = obj.getString("name"),
                            siteUrl = obj.getString("siteUrl"),
                            feedUrl = obj.getString("feedUrl"),
                            category = obj.optString("category", "General"),
                            enabled = obj.optBoolean("enabled", true)
                        )
                    )
                }
                sourceDao.insertSources(defaultSources)
                Log.i("FeedRepo", "Initialized ${defaultSources.size} default sources from JSON")
            } catch (e: Exception) {
                Log.e("FeedRepo", "Failed to seed default sources", e)
            }
        }
    }

    fun getSources(): Flow<List<FeedSource>> {
        return sourceDao.getAllSources().map { list ->
            list.map { entity ->
                FeedSource(
                    id = entity.id,
                    name = entity.name,
                    siteUrl = entity.siteUrl,
                    feedUrl = entity.feedUrl,
                    category = entity.category,
                    enabled = entity.enabled,
                    iconUrl = entity.iconUrl
                )
            }
        }
    }

    suspend fun setSourceEnabled(id: String, enabled: Boolean) {
        sourceDao.setSourceEnabled(id, enabled)
    }

    suspend fun deleteSource(id: String) {
        sourceDao.deleteSource(id)
    }

    suspend fun addSourceFromUrl(urlInput: String, customCategory: String = "General"): Result<FeedSource> = withContext(Dispatchers.IO) {
        val discoveryResult = discoveryService.discoverFeed(urlInput)
        if (discoveryResult.isFailure) {
            return@withContext Result.failure(discoveryResult.exceptionOrNull() ?: Exception("No se pudo descubrir feed"))
        }

        val discovered = discoveryResult.getOrThrow()
        val id = "source_${System.currentTimeMillis()}"
        val entity = FeedSourceEntity(
            id = id,
            name = discovered.title,
            siteUrl = discovered.siteUrl,
            feedUrl = discovered.feedUrl,
            category = customCategory,
            enabled = true
        )

        sourceDao.insertSource(entity)

        // Sync its articles immediately
        syncFeed(entity)

        Result.success(
            FeedSource(
                id = entity.id,
                name = entity.name,
                siteUrl = entity.siteUrl,
                feedUrl = entity.feedUrl,
                category = entity.category,
                enabled = entity.enabled
            )
        )
    }

    fun getArticles(categoryFilter: String? = null, showOnlyUnread: Boolean = false, showOnlyFavorites: Boolean = false): Flow<List<Article>> {
        val rawFlow = when {
            showOnlyFavorites -> articleDao.getFavoriteArticles()
            showOnlyUnread -> articleDao.getUnreadArticles()
            !categoryFilter.isNullOrBlank() && categoryFilter != "Todos" -> articleDao.getArticlesByCategory(categoryFilter)
            else -> articleDao.getArticlesFromEnabledSources()
        }

        return rawFlow.map { list ->
            list.map { entity ->
                Article(
                    id = entity.id,
                    sourceId = entity.sourceId,
                    sourceName = entity.sourceName,
                    title = entity.title,
                    link = entity.link,
                    description = entity.description,
                    content = entity.content,
                    author = entity.author,
                    pubDate = entity.pubDate,
                    imageUrl = entity.imageUrl,
                    isRead = entity.isRead,
                    isFavorite = entity.isFavorite,
                    category = entity.category
                )
            }
        }
    }

    suspend fun getArticleById(id: String): Article? = withContext(Dispatchers.IO) {
        val entity = articleDao.getArticleById(id) ?: return@withContext null
        Article(
            id = entity.id,
            sourceId = entity.sourceId,
            sourceName = entity.sourceName,
            title = entity.title,
            link = entity.link,
            description = entity.description,
            content = entity.content,
            author = entity.author,
            pubDate = entity.pubDate,
            imageUrl = entity.imageUrl,
            isRead = entity.isRead,
            isFavorite = entity.isFavorite,
            category = entity.category
        )
    }

    suspend fun setReadStatus(articleId: String, isRead: Boolean) {
        articleDao.setReadStatus(articleId, isRead)
    }

    suspend fun setFavoriteStatus(articleId: String, isFavorite: Boolean) {
        articleDao.setFavoriteStatus(articleId, isFavorite)
    }

    suspend fun markAllAsRead() {
        articleDao.markAllAsRead()
    }

    suspend fun syncAllEnabledFeeds(): Int = withContext(Dispatchers.IO) {
        val enabledSources = sourceDao.getEnabledSourcesSync()
        var totalNewArticles = 0

        for (source in enabledSources) {
            try {
                totalNewArticles += syncFeed(source)
            } catch (e: Exception) {
                Log.w("FeedRepo", "Failed to sync feed for ${source.name}", e)
            }
        }
        totalNewArticles
    }

    private suspend fun syncFeed(source: FeedSourceEntity): Int {
        val request = Request.Builder()
            .url(source.feedUrl)
            .header("User-Agent", "ChronicleNewsReader/1.0")
            .build()

        val response = httpClient.newCall(request).execute()
        if (!response.isSuccessful) return 0

        val xmlBody = response.body?.string() ?: return 0
        val parsedArticles = feedParser.parse(
            xmlContent = xmlBody,
            sourceId = source.id,
            sourceName = source.name,
            category = source.category
        )

        if (parsedArticles.isNotEmpty()) {
            articleDao.insertArticles(parsedArticles)
        }
        return parsedArticles.size
    }

    suspend fun getOrGenerateSummary(article: Article): String = withContext(Dispatchers.IO) {
        val llmSettings = settingsRepository.getLlmSettingsSync()
        val cached = summaryDao.getSummary(article.id, llmSettings.language)
        if (!cached.isNullOrBlank()) {
            return@withContext cached
        }

        val promptSettings = settingsRepository.getPromptSettingsSync()
        val generated = llmManager.summarizeArticle(
            title = article.title,
            content = article.content,
            description = article.description,
            settings = llmSettings,
            promptSettings = promptSettings
        )

        summaryDao.insertSummary(
            CachedSummaryEntity(
                articleId = article.id,
                language = llmSettings.language,
                summaryText = generated
            )
        )

        generated
    }

    suspend fun generateDailyDigest(): DailyDigest = withContext(Dispatchers.IO) {
        val llmSettings = settingsRepository.getLlmSettingsSync()
        val promptSettings = settingsRepository.getPromptSettingsSync()
        val topArticles = articleDao.getTopArticlesSync(limit = 10)

        val pairs = topArticles.map { it.sourceName to it.title }
        val digestText = llmManager.generateDailyDigest(pairs, llmSettings, promptSettings)

        DailyDigest(
            id = "digest_${System.currentTimeMillis()}",
            text = digestText,
            articleCount = topArticles.size,
            language = llmSettings.language
        )
    }
}
