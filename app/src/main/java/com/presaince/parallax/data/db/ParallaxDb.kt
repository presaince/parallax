/*
 * Parallax — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.parallax.data.db

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Dao
interface SubscriptionDao {
    @Query("SELECT * FROM subscriptions ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<SubscriptionEntity>>

    @Query("SELECT COUNT(*) FROM subscriptions")
    suspend fun count(): Int

    @Query("SELECT * FROM subscriptions ORDER BY name COLLATE NOCASE")
    suspend fun all(): List<SubscriptionEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(items: List<SubscriptionEntity>): List<Long>

    @Query("DELETE FROM subscriptions WHERE url = :url")
    suspend fun delete(url: String)
}

@Dao
interface ProgressDao {
    @Query("SELECT * FROM progress WHERE videoId = :videoId")
    suspend fun find(videoId: String): ProgressEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(progress: ProgressEntity)

    @Query("SELECT * FROM progress ORDER BY updatedAt DESC LIMIT 50")
    fun observeRecent(): Flow<List<ProgressEntity>>

    @Query("SELECT * FROM progress")
    fun observeAll(): Flow<List<ProgressEntity>>

    @Query("SELECT * FROM progress")
    suspend fun allOnce(): List<ProgressEntity>

    @Query("SELECT videoId FROM progress")
    suspend fun videoIds(): List<String>
    @Query("DELETE FROM progress WHERE videoId = :videoId")
    suspend fun clear(videoId: String)
}

@Dao
interface HistoryDao {
    @Insert
    suspend fun add(entry: HistoryEntity): Long

    @Query("SELECT * FROM history ORDER BY openedAt DESC LIMIT 100")
    fun observeRecent(): Flow<List<HistoryEntity>>

    /** One row per video, however many times it was opened. Watches count watched plays. */
    @Query(
        "SELECT videoId, title, channelName, thumbUrl, MAX(openedAt) AS watchedAt, " +
            "SUM(CASE WHEN watchedAt IS NOT NULL THEN 1 ELSE 0 END) AS watches FROM history GROUP BY videoId ORDER BY watchedAt DESC LIMIT 200",
    )
    fun observeGrouped(): Flow<List<HistoryGroup>>

    @Query("SELECT * FROM history ORDER BY openedAt DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<HistoryEntity>

    @Query("DELETE FROM history WHERE videoId = :videoId")
    suspend fun clear(videoId: String)

    @Query("DELETE FROM history WHERE openedAt >= :since")
    suspend fun clearSince(since: Long)

    @Query("DELETE FROM history")
    suspend fun clearAll()

    @Query("UPDATE history SET watchedAt = :watchedAt WHERE id = :id")
    suspend fun markWatched(id: Long, watchedAt: Long)

    /** Every play, newest first — the History section reads this, not the grouped list. */
    @Query(
        "SELECT h.id, h.videoId, h.title, h.channelName, h.openedAt AS watchedAt, h.thumbUrl, " +
            "COALESCE(p.positionMs, 0) AS positionMs, COALESCE(p.durationMs, 0) AS durationMs " +
            "FROM history h LEFT JOIN progress p ON p.videoId = h.videoId " +
            "ORDER BY h.openedAt DESC LIMIT :limit",
    )
    suspend fun events(limit: Int): List<HistoryProgress>
}

/** One video's note count, for the card badges. */
data class NoteCount(
    val videoId: String,
    val n: Int,
)

/** A watched video with its watch count, for the history list. */
data class HistoryGroup(
    val videoId: String,
    val title: String,
    val channelName: String,
    val thumbUrl: String?,
    val watchedAt: Long,
    val watches: Int,
)

/** One watch event with the progress the video was left at. */
data class HistoryProgress(
    val id: Long,
    val videoId: String,
    val title: String,
    val channelName: String,
    val watchedAt: Long,
    val thumbUrl: String?,
    val positionMs: Long,
    val durationMs: Long,
)

@Dao
interface VideoDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<VideoEntity>)

    @Query(
        "SELECT * FROM videos WHERE TRIM(title) != '' " +
            "ORDER BY COALESCE(publishedAt, fetchedAt) DESC LIMIT 200",
    )
    fun observeFeed(): Flow<List<VideoEntity>>

    @Query("SELECT MAX(fetchedAt) FROM videos")
    suspend fun lastFetchedAt(): Long?

    @Query("SELECT * FROM videos ORDER BY COALESCE(publishedAt, fetchedAt) DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<VideoEntity>

    @Query("SELECT * FROM videos WHERE id = :id")
    suspend fun find(id: String): VideoEntity?

    @Query("UPDATE videos SET likeCount = :likes WHERE id = :id")
    suspend fun setLikeCount(id: String, likes: Long)

    @Query("UPDATE videos SET commentCount = :comments WHERE id = :id")
    suspend fun setCommentCount(id: String, comments: Long)

    @Query("SELECT id, liveSeenAt FROM videos WHERE liveSeenAt IS NOT NULL")
    suspend fun liveSeen(): List<VideoLiveSeen>

    @Query("SELECT id, likeCount, commentCount FROM videos WHERE likeCount IS NOT NULL OR commentCount IS NOT NULL")
    suspend fun learnedCounts(): List<VideoLearned>

    @Query(
        "SELECT * FROM videos WHERE channelId = :channelUrl AND TRIM(title) != '' " +
            "ORDER BY COALESCE(publishedAt, fetchedAt) DESC LIMIT :limit",
    )
    suspend fun forChannel(channelUrl: String, limit: Int): List<VideoEntity>

    @Query("DELETE FROM videos WHERE fetchedAt < :before AND id NOT IN (SELECT videoId FROM progress)")
    suspend fun pruneOlderThan(before: Long)
}

@Dao
interface ChannelDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<ChannelEntity>)

    @Query("SELECT * FROM channels")
    suspend fun all(): List<ChannelEntity>

    @Query("SELECT * FROM channels ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<ChannelEntity>>

    @Query("SELECT * FROM channels WHERE url = :url")
    suspend fun find(url: String): ChannelEntity?
}

@Dao
interface CommentCacheDao {    @Query("SELECT * FROM comment_cache WHERE videoId = :videoId AND pageKey = :pageKey")
    suspend fun find(videoId: String, pageKey: String): CommentCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(entry: CommentCacheEntity)

    @Query("DELETE FROM comment_cache WHERE fetchedAt < :before")
    suspend fun pruneOlderThan(before: Long)
}

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes WHERE videoId = :videoId AND kind = :kind ORDER BY atSec")
    fun observeFor(videoId: String, kind: String): Flow<List<NoteEntity>>

    /** Voice-note counts per video, for the card badges. */
    @Query(
        "SELECT videoId, COUNT(*) AS n FROM notes WHERE kind = :kind GROUP BY videoId",
    )
    fun counts(kind: String): Flow<List<NoteCount>>

    @Query("SELECT * FROM notes WHERE videoId = :videoId ORDER BY atSec")
    suspend fun forVideo(videoId: String): List<NoteEntity>

    @Insert
    suspend fun insert(note: NoteEntity): Long

    @Query("UPDATE notes SET text = :text WHERE id = :id")
    suspend fun updateText(id: Long, text: String)

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM notes WHERE videoId = :videoId AND kind = :kind")
    suspend fun deleteAll(videoId: String, kind: String)
}

@Dao
interface ResyncDao {    @Query("SELECT * FROM resync WHERE videoId = :videoId")
    suspend fun forVideo(videoId: String): List<ResyncEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveAll(items: List<ResyncEntity>)

    @Query("DELETE FROM resync WHERE videoId = :videoId")
    suspend fun clear(videoId: String)
}

@Dao
interface AiChapterDao {
    @Query("SELECT * FROM ai_chapters WHERE videoId = :videoId ORDER BY atSec")
    suspend fun forVideo(videoId: String): List<AiChapterEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveAll(items: List<AiChapterEntity>)

    @Query("DELETE FROM ai_chapters WHERE videoId = :videoId")
    suspend fun clear(videoId: String)
}

@Dao
interface RecipeDao {
    @Query("SELECT * FROM recipes ORDER BY source, name COLLATE NOCASE")
    fun observeAll(): Flow<List<RecipeEntity>>

    @Query("SELECT * FROM recipes ORDER BY source, name COLLATE NOCASE")
    suspend fun all(): List<RecipeEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(recipe: RecipeEntity)

    @Query("DELETE FROM recipes WHERE name = :name AND source = 'user'")
    suspend fun deleteUser(name: String)

    /** Built-ins removed from the assets should not linger in the table under an old name. */
    @Query("DELETE FROM recipes WHERE source = 'builtin' AND name NOT IN (:names)")
    suspend fun pruneBuiltIns(names: List<String>)
}

@Database(
    entities = [
        SubscriptionEntity::class,
        ProgressEntity::class,
        HistoryEntity::class,
        VideoEntity::class,
        ChannelEntity::class,
        CommentCacheEntity::class,
        NoteEntity::class,
        ResyncEntity::class,
        AiChapterEntity::class,
        RecipeEntity::class,
    ],
    version = 12,
    exportSchema = true,
)
abstract class ParallaxDb : RoomDatabase() {
    abstract fun subscriptions(): SubscriptionDao
    abstract fun progress(): ProgressDao
    abstract fun history(): HistoryDao
    abstract fun videos(): VideoDao
    abstract fun channels(): ChannelDao
    abstract fun commentCache(): CommentCacheDao
    abstract fun notes(): NoteDao
    abstract fun resync(): ResyncDao
    abstract fun aiChapters(): AiChapterDao
    abstract fun recipes(): RecipeDao

    companion object {
        /** v6 added thumbnails to the two playback tables so history rows are not blank. */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE progress ADD COLUMN thumbUrl TEXT")
                db.execSQL("ALTER TABLE history ADD COLUMN thumbUrl TEXT")
            }
        }

        /** v7 stores the like count a video carries once it has been extracted. */
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE videos ADD COLUMN likeCount INTEGER")
            }
        }

        /** v8 stores whether a video is live or still upcoming, so the list can mark or hide it. */
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE videos ADD COLUMN streamState TEXT NOT NULL DEFAULT 'NORMAL'",
                )
            }
        }

        /** v9 stores the comment total a video carries once its comments have loaded. */
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE videos ADD COLUMN commentCount INTEGER")
            }
        }

        /** v10 remembers when a video was live, so a later listing cannot flip its badge off. */
        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE videos ADD COLUMN liveSeenAt INTEGER")
            }
        }

        /** v11 stores AI-generated chapters so a revisit reads them back instead of asking again. */
        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `ai_chapters` (" +
                        "`videoId` TEXT NOT NULL, `atSec` INTEGER NOT NULL, `title` TEXT NOT NULL, " +
                        "`model` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`videoId`, `atSec`))",
                )
            }
        }

        /** v12 one row per open: openedAt always, watchedAt only when the threshold was met. */
        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `history_new` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`videoId` TEXT NOT NULL, `title` TEXT NOT NULL, `channelName` TEXT NOT NULL, " +
                        "`openedAt` INTEGER NOT NULL, `watchedAt` INTEGER, `thumbUrl` TEXT)",
                )
                db.execSQL(
                    "INSERT INTO `history_new` " +
                        "(`id`, `videoId`, `title`, `channelName`, `openedAt`, `watchedAt`, `thumbUrl`) " +
                        "SELECT `id`, `videoId`, `title`, `channelName`, `watchedAt`, `watchedAt`, `thumbUrl` FROM `history`",
                )
                db.execSQL("DROP TABLE `history`")
                db.execSQL("ALTER TABLE `history_new` RENAME TO `history`")
            }
        }
    }
}
