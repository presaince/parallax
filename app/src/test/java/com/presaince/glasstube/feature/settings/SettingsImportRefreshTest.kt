/*
 * GlassTube — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.glasstube.feature.settings

import android.content.Context
import androidx.room.Room
import com.presaince.glasstube.data.backup.Backup
import com.presaince.glasstube.data.backup.SettingsExport
import com.presaince.glasstube.data.db.GlassTubeDb
import com.presaince.glasstube.data.discover.RecipeRepository
import com.presaince.glasstube.data.importer.ImportedSubscription
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * The feed is a database view and nothing fetches on its own: a restore that adds channels has to
 * ask, or a fresh install sits on "No subscriptions to fetch" with 40 channels in the database.
 */
@RunWith(RobolectricTestRunner::class)
class SettingsImportRefreshTest {

    private lateinit var context: Context
    private lateinit var db: GlassTubeDb
    private var refreshes = 0

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences(SettingsExport.PREFS, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        db = Room.inMemoryDatabaseBuilder(context, GlassTubeDb::class.java)
            .allowMainThreadQueries()
            .build()
        refreshes = 0
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun viewModel() = SettingsViewModel(
        db = db,
        context = context,
        resolver = context.contentResolver,
        recipes = RecipeRepository(context, db),
        requestFeedRefresh = { refreshes++ },
    )

    private fun backupText(): String = Backup.json(
        settings = SettingsExport.values(context),
        subscriptions = listOf(
            ImportedSubscription(
                url = "https://www.youtube.com/channel/UCone",
                name = "One",
            ),
        ),
        recipes = emptyList(),
    )

    @Test
    fun `a backup that adds channels asks the feed to fetch once`() = runBlocking {
        viewModel().import(backupText(), apiKeyPresent = false)

        assertEquals(1, refreshes)
        assertEquals(1, db.subscriptions().count())
    }

    @Test
    fun `a settings file leaves the feed alone`() = runBlocking {
        viewModel().import(SettingsExport.json(context), apiKeyPresent = false)

        assertEquals(0, refreshes)
    }
}
