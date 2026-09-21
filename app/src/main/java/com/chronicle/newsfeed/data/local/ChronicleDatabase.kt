package com.chronicle.newsfeed.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.chronicle.newsfeed.data.local.dao.ArticleDao
import com.chronicle.newsfeed.data.local.dao.CachedSummaryDao
import com.chronicle.newsfeed.data.local.dao.FeedSourceDao
import com.chronicle.newsfeed.data.local.entity.ArticleEntity
import com.chronicle.newsfeed.data.local.entity.CachedSummaryEntity
import com.chronicle.newsfeed.data.local.entity.FeedSourceEntity

@Database(
    entities = [FeedSourceEntity::class, ArticleEntity::class, CachedSummaryEntity::class],
    version = 1,
    exportSchema = false
)
abstract class ChronicleDatabase : RoomDatabase() {
    abstract fun feedSourceDao(): FeedSourceDao
    abstract fun articleDao(): ArticleDao
    abstract fun cachedSummaryDao(): CachedSummaryDao

    companion object {
        @Volatile
        private var INSTANCE: ChronicleDatabase? = null

        fun getInstance(context: Context): ChronicleDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ChronicleDatabase::class.java,
                    "chronicle_news.db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
