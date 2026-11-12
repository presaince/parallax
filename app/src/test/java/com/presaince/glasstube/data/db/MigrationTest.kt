/*
 * GlassTube — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.glasstube.data.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File

/**
 * The migrations are hand-written SQL, and the destructive fallback is gone, so a wrong one is a
 * dead app on the owner's phone rather than a cleared cache. These build a real version-11 file
 * from the schema Room exported at that version and open it at the current one, which is the only
 * way to see what a reader who upgrades actually gets.
 *
 * Limited to 11 → 12 → 13 on purpose: those are the only versions with an exported schema, so a
 * file older than 11 cannot be built faithfully here. Everything below 11 is a fallback path for
 * installs that no longer exist.
 *
 * No `androidx.room:room-testing`: it needs an androidTest source set and a device, and the whole
 * point of this suite is that it runs on the JVM.
 */
@RunWith(RobolectricTestRunner::class)
class MigrationTest {

    private lateinit var context: Context
    private val dbName = "migration-test.db"

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        context.deleteDatabase(dbName)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(dbName)
    }

    /** A version-11 file as Room wrote it: every table from the exported schema, nothing else. */
    private fun seedV11(): File {
        val schema = File(schemaDir(), "com.presaince.glasstube.data.db.GlassTubeDb/11.json")
        assertTrue("exported schema missing: ${schema.absolutePath}", schema.exists())
        val entities = JSONObject(schema.readText())
            .getJSONObject("database")
            .getJSONArray("entities")

        val file = context.getDatabasePath(dbName)
        file.parentFile?.mkdirs()
        val db = SQLiteDatabase.openOrCreateDatabase(file, null)
        try {
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                val table = entity.getString("tableName")
                db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                val indices = entity.optJSONArray("indices") ?: continue
                for (j in 0 until indices.length()) {
                    db.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
                }
            }
            // One video opened and watched in v11, and one subscription: both must survive, because
            // a migration that quietly dropped either is the failure this file exists to catch.
            db.execSQL(
                "INSERT INTO `history` (`videoId`, `title`, `channelName`, `watchedAt`, `thumbUrl`) " +
                    "VALUES ('v1', 'A video', 'A channel', 1700000000000, NULL)",
            )
            db.execSQL(
                "INSERT INTO `subscriptions` (`url`, `name`, `addedAt`) " +
                    "VALUES ('https://youtube.com/c/a', 'A', 1700000000000)",
            )
            db.version = 11
        } finally {
            db.close()
        }
        return file
    }

    private fun openAtCurrentVersion(): GlassTubeDb =
        Room.databaseBuilder(context, GlassTubeDb::class.java, dbName)
            .addMigrations(GlassTubeDb.MIGRATION_11_12, GlassTubeDb.MIGRATION_12_13)
            .allowMainThreadQueries()
            .build()

    /**
     * The schema lives in the module's own directory. Gradle runs a unit test with the module as
     * its working directory, but a run from the repository root is common enough to allow.
     */
    private fun schemaDir(): File {
        val candidates = listOf(File("schemas"), File("app/schemas"))
        return candidates.firstOrNull { it.isDirectory }
            ?: throw AssertionError(
                "schemas/ not found from ${File(".").absolutePath}; " +
                    "run the test from the app module or the repository root",
            )
    }

    @Test
    fun `a version 11 history row opens at 13 with its watch time kept`() = runTest {
        seedV11()

        val db = openAtCurrentVersion()
        try {
            val row = db.history().recent(10).single()

            assertEquals("v1", row.videoId)
            assertEquals("A video", row.title)
            // v11 knew only when the video was watched; v12 split the open from the watch. A row
            // that was watched must read as opened at the same moment, not at zero and not at now.
            assertEquals(1_700_000_000_000L, row.openedAt)
            assertEquals(1_700_000_000_000L, row.watchedAt)
        } finally {
            db.close()
        }
    }

    @Test
    fun `a version 11 subscription survives the upgrade`() = runTest {
        seedV11()

        val db = openAtCurrentVersion()
        try {
            val row = db.subscriptions().all().single()

            assertEquals("https://youtube.com/c/a", row.url)
            assertEquals("A", row.name)
        } finally {
            db.close()
        }
    }

    @Test
    fun `the hidden set arrives at 13 empty and writable`() = runTest {
        seedV11()

        val db = openAtCurrentVersion()
        try {
            assertEquals(emptyList<HiddenRow>(), db.hidden().all())
            // Inserting is what proves the columns match the entity: a createSql that disagrees
            // with HiddenRow fails here rather than on the feed screen.
            db.hidden().hide(
                HiddenRow(
                    key = "v1",
                    videoId = "v1",
                    channelUrl = null,
                    title = "A video",
                    thumbUrl = null,
                    liveOnly = false,
                    reason = "not interested",
                    hiddenAt = 1_700_000_000_000L,
                ),
            )

            assertEquals("v1", db.hidden().all().single().videoId)
        } finally {
            db.close()
        }
    }
}
