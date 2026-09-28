package com.chronicle.newsfeed.data.local.dao

import androidx.room.*
import com.chronicle.newsfeed.data.local.entity.ArticleEntity
import com.chronicle.newsfeed.data.local.entity.CachedSummaryEntity
import com.chronicle.newsfeed.data.local.entity.FeedSourceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FeedSourceDao {
    @Query("SELECT * FROM sources ORDER BY name ASC")
    fun getAllSources(): Flow<List<FeedSourceEntity>>

    @Query("SELECT * FROM sources WHERE enabled = 1 ORDER BY name ASC")
    suspend fun getEnabledSourcesSync(): List<FeedSourceEntity>

    @Query("SELECT * FROM sources WHERE id = :id LIMIT 1")
    suspend fun getSourceById(id: String): FeedSourceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSources(sources: List<FeedSourceEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSource(source: FeedSourceEntity)

    @Update
    suspend fun updateSource(source: FeedSourceEntity)

    @Query("UPDATE sources SET enabled = :enabled WHERE id = :id")
    suspend fun setSourceEnabled(id: String, enabled: Boolean)

    @Query("DELETE FROM sources WHERE id = :id")
    suspend fun deleteSource(id: String)

    @Query("SELECT COUNT(*) FROM sources")
    suspend fun getSourceCount(): Int
}

@Dao
interface ArticleDao {
    @Query("SELECT * FROM articles ORDER BY pubDate DESC")
    fun getAllArticles(): Flow<List<ArticleEntity>>

    @Query("SELECT * FROM articles WHERE sourceId IN (SELECT id FROM sources WHERE enabled = 1) ORDER BY pubDate DESC")
    fun getArticlesFromEnabledSources(): Flow<List<ArticleEntity>>

    @Query("SELECT * FROM articles WHERE isFavorite = 1 ORDER BY pubDate DESC")
    fun getFavoriteArticles(): Flow<List<ArticleEntity>>

    @Query("SELECT * FROM articles WHERE isRead = 0 AND sourceId IN (SELECT id FROM sources WHERE enabled = 1) ORDER BY pubDate DESC")
    fun getUnreadArticles(): Flow<List<ArticleEntity>>

    @Query("SELECT * FROM articles WHERE category = :category AND sourceId IN (SELECT id FROM sources WHERE enabled = 1) ORDER BY pubDate DESC")
    fun getArticlesByCategory(category: String): Flow<List<ArticleEntity>>

    @Query("SELECT * FROM articles WHERE id = :id LIMIT 1")
    suspend fun getArticleById(id: String): ArticleEntity?

    @Query("SELECT * FROM articles WHERE sourceId IN (SELECT id FROM sources WHERE enabled = 1) ORDER BY pubDate DESC LIMIT :limit")
    suspend fun getTopArticlesSync(limit: Int = 10): List<ArticleEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertArticles(articles: List<ArticleEntity>)

    @Query("UPDATE articles SET isRead = :isRead WHERE id = :id")
    suspend fun setReadStatus(id: String, isRead: Boolean)

    @Query("UPDATE articles SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun setFavoriteStatus(id: String, isFavorite: Boolean)

    @Query("UPDATE articles SET content = :content WHERE id = :id")
    suspend fun updateArticleContent(id: String, content: String)

    @Query("UPDATE articles SET isRead = 1 WHERE sourceId = :sourceId")
    suspend fun markAllAsReadForSource(sourceId: String)

    @Query("UPDATE articles SET isRead = 1")
    suspend fun markAllAsRead()

    @Query("DELETE FROM articles WHERE isFavorite = 0 AND pubDate < :cutoffTimestamp")
    suspend fun deleteOldArticles(cutoffTimestamp: Long)
}

@Dao
interface CachedSummaryDao {
    @Query("SELECT summaryText FROM cached_summaries WHERE articleId = :articleId AND language = :language LIMIT 1")
    suspend fun getSummary(articleId: String, language: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSummary(summary: CachedSummaryEntity)
}
