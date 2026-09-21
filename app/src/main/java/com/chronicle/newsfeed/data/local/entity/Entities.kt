package com.chronicle.newsfeed.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sources")
data class FeedSourceEntity(
    @PrimaryKey val id: String,
    val name: String,
    val siteUrl: String,
    val feedUrl: String,
    val category: String = "General",
    val enabled: Boolean = true,
    val iconUrl: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "articles")
data class ArticleEntity(
    @PrimaryKey val id: String,
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
    val category: String = "General"
)

@Entity(tableName = "cached_summaries", primaryKeys = ["articleId", "language"])
data class CachedSummaryEntity(
    val articleId: String,
    val language: String,
    val summaryText: String,
    val createdAt: Long = System.currentTimeMillis()
)
