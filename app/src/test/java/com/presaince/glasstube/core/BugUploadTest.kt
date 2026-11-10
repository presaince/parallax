/*
 * GlassTube — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.glasstube.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The upload contract as a pure function. A 2xx that is not our own shape is a failure, not a
 * success: something else answered instead of upload.php.
 */
@RunWith(RobolectricTestRunner::class)
class BugUploadTest {

    @Test
    fun `a well formed reply is done`() {
        val state = BugUpload.stateFor(
            code = 200,
            retryAfter = null,
            body = """{"ok":true,"name":"report.json"}""",
            contentType = "application/json",
        )
        assertTrue(state is BugUploadState.Done)
        assertEquals("report.json", (state as BugUploadState.Done).name)
    }

    @Test
    fun `a 2xx with the wrong shape is a failure`() {
        val state = BugUpload.stateFor(200, null, "<html>portal</html>", "text/html")
        assertTrue(state is BugUploadState.Failed)
        val failed = state as BugUploadState.Failed
        assertTrue(failed.reason.startsWith("unexpected reply"))
        assertEquals(null, failed.retryAtMillis)
    }

    @Test
    fun `a 2xx without a name is a failure`() {
        val state = BugUpload.stateFor(201, null, """{"ok":true}""", "application/json")
        assertTrue(state is BugUploadState.Failed)
    }

    @Test
    fun `429 carries the retry time and honours Retry-After`() {
        val now = 1_000_000L
        val state = BugUpload.stateFor(429, "10", "", null, nowMs = now)
        assertTrue(state is BugUploadState.Failed)
        val failed = state as BugUploadState.Failed
        assertEquals("HTTP 429", failed.reason)
        assertEquals(now + 10_000L, failed.retryAtMillis)
    }

    @Test
    fun `429 without a header waits thirty seconds`() {
        val now = 1_000_000L
        val state = BugUpload.stateFor(429, null, "", null, nowMs = now)
        assertEquals(now + 30_000L, (state as BugUploadState.Failed).retryAtMillis)
    }

    @Test
    fun `429 is clamped to five and three hundred seconds`() {
        val now = 1_000_000L
        val low = BugUpload.stateFor(429, "1", "", null, nowMs = now)
        assertEquals(now + 5_000L, (low as BugUploadState.Failed).retryAtMillis)
        val high = BugUpload.stateFor(429, "9999", "", null, nowMs = now)
        assertEquals(now + 300_000L, (high as BugUploadState.Failed).retryAtMillis)
    }

    @Test
    fun `other errors carry no retry time`() {
        val state = BugUpload.stateFor(500, null, "oops", null)
        assertTrue(state is BugUploadState.Failed)
        val failed = state as BugUploadState.Failed
        assertEquals("HTTP 500", failed.reason)
        assertEquals(null, failed.retryAtMillis)
    }
}
