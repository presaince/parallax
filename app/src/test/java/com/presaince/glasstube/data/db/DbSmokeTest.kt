/*
 * GlassTube — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.glasstube.data.db

import androidx.room.Room
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * The SQL is hand-written, so it is tested against a real database rather than a mock. These are
 * the queries whose behaviour a reader would otherwise have to trust: the unique subscription key,
 * the grouped history, and the join that puts the playhead on a history event.
 */
@RunWith(RobolectricTestRunner::class)
class DbSmokeTest {

    private lateinit var db: GlassTubeDb

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            GlassTubeDb::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun subscription(url: String, name: String = url) =
        SubscriptionEntity(url = url, name = name, addedAt = 0)

    private fun history(videoId: String, openedAt: Long, watchedAt: Long? = null, title: String = videoId) =
        HistoryEntity(videoId = videoId, title = title, channelName = "channel", openedAt = openedAt, watchedAt = watchedAt)

    @Test
    fun `a subscription url is the primary key, so an import cannot duplicate it`() = runTest {
        val dao = db.subscriptions()
        dao.insertAll(listOf(subscription("https://youtube.com/c/a", "A")))
        dao.insertAll(listOf(subscription("https://youtube.com/c/a", "A renamed")))

        assertEquals(1, dao.count())
        // IGNORE keeps the first row: a re-import must not silently rename a channel.
        assertEquals("A", dao.all().single().name)
    }

    @Test
    fun `history groups one row per video and counts the watches`() = runTest {
        val dao = db.history()
        dao.add(history("a", openedAt = 100, watchedAt = 100))
        dao.add(history("a", openedAt = 300, watchedAt = 300))
        dao.add(history("b", openedAt = 200, watchedAt = 200))

        val groups = dao.observeGrouped().first()

        assertEquals(listOf("a", "b"), groups.map { it.videoId })
        assertEquals(2, groups.first { it.videoId == "a" }.watches)
        // The group carries the most recent watch, not the first one.
        assertEquals(300, groups.first { it.videoId == "a" }.watchedAt)
    }

    @Test
    fun `a history event carries the playhead from the progress table`() = runTest {
        db.history().add(history("a", openedAt = 100))
        db.progress().save(
            ProgressEntity(
                videoId = "a",
                title = "a",
                positionMs = 42_000,
                durationMs = 300_000,
                updatedAt = 150,
            ),
        )

        val event = db.history().events(10).single()

        assertEquals("a", event.videoId)
        assertEquals(42_000, event.positionMs)
        assertEquals(300_000, event.durationMs)
    }

    @Test
    fun `an event without progress reports a zero playhead, not a missing row`() = runTest {
        db.history().add(history("a", openedAt = 100))

        val event = db.history().events(10).single()

        assertEquals(0, event.positionMs)
        assertEquals(0, event.durationMs)
    }

    @Test
    fun `the feed hides blank titles and orders newest first`() = runTest {
        val dao = db.videos()
        dao.upsertAll(
            listOf(
                video(id = "old", publishedAt = 100, fetchedAt = 100),
                video(id = "new", publishedAt = 300, fetchedAt = 300),
                video(id = "blank", title = "  ", publishedAt = 500, fetchedAt = 500),
            ),
        )

        assertEquals(listOf("new", "old"), dao.observeFeed().first().map { it.id })
    }

    @Test
    fun `a replace upsert drops the learned like count at the dao level`() = runTest {
        val dao = db.videos()
        dao.upsertAll(listOf(video(id = "a", title = "first")))
        dao.setLikeCount("a", 77)
        dao.upsertAll(listOf(video(id = "a", title = "second")))

        val stored = dao.find("a")
        assertEquals("second", stored?.title)
        // REPLACE is delete + insert, and a listing row carries no like count. The repository
        // carries the learned columns forward via learnedCounts(); this pins why that hop exists.
        assertNull(stored?.likeCount)
    }

    @Test
    fun `learned counts come back by id and only for learned videos`() = runTest {
        val dao = db.videos()
        dao.upsertAll(listOf(video(id = "a"), video(id = "b")))
        dao.setLikeCount("a", 77)
        dao.setCommentCount("b", 12)

        val learned = dao.learnedCounts()

        assertEquals(2, learned.size)
        assertEquals(77L, learned.first { it.id == "a" }.likeCount)
        assertEquals(12L, learned.first { it.id == "b" }.commentCount)
        assertEquals(null, learned.first { it.id == "a" }.commentCount)
    }

    @Test
    fun `notes are read back in time order`() = runTest {
        val dao = db.notes()
        dao.insert(NoteEntity(videoId = "a", atSec = 90, text = "second", createdAt = 1))
        dao.insert(NoteEntity(videoId = "a", atSec = 10, text = "first", createdAt = 2))

        val notes = dao.forVideo("a")
        assertEquals(listOf(10L, 90L), notes.map { it.atSec })
        assertEquals("deck", notes.first().kind)
    }

    private fun video(
        id: String,
        title: String = id,
        publishedAt: Long? = null,
        fetchedAt: Long = 0,
    ) = VideoEntity(
        id = id,
        title = title,
        channelId = "channel",
        channelName = "channel",
        durationSec = 60,
        thumbUrl = null,
        publishedAt = publishedAt,
        fetchedAt = fetchedAt,
    )
}
