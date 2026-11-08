/*
 * Parallax — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.parallax.core

import android.content.Context
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * The bug screen can send a note on its own or a note plus logs. Logs carry the settings, and a
 * setting whose name says token or secret must never leave the phone in clear.
 */
@RunWith(RobolectricTestRunner::class)
class BugReportTest {

    private lateinit var context: Context
    private lateinit var log: AppLog

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        log = AppLog(context.filesDir)
        context.getSharedPreferences("parallax", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .putString("posting_token", "abc123")
            .putString("accent", "cyan")
            .commit()
    }

    @Test
    fun `logs off sends no entries and no settings`() {
        val json = JSONObject(BugReport.json(context, log, includeLogs = false))
        assertEquals(0, json.getJSONArray("entries").length())
        assertFalse(json.has("settings"))
    }

    @Test
    fun `logs on carries the settings with secrets redacted`() {
        val json = JSONObject(BugReport.json(context, log, includeLogs = true))
        val settings = json.getJSONObject("settings")
        assertEquals("[redacted]", settings.getString("posting_token"))
        assertEquals("cyan", settings.getString("accent"))
    }

    @Test
    fun `the bundle says whether logs were included`() {
        val off = JSONObject(BugReport.bundle(context, log, "note", includeShot = false, includeLogs = false))
        assertFalse(off.getBoolean("logsIncluded"))
        val on = JSONObject(BugReport.bundle(context, log, "note", includeShot = false, includeLogs = true))
        assertTrue(on.getBoolean("logsIncluded"))
        assertTrue(on.has("settings"))    }
}
