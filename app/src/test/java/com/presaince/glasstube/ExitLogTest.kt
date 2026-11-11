/*
 * GlassTube — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.glasstube

import com.presaince.glasstube.core.isNewer
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * One death, one line. The exit record is read on every start, and the OS keeps history, so
 * without this rule the same crash would be written again each time the app opened.
 */
class ExitLogTest {

    @Test
    fun an_exit_that_has_not_been_written_is_written() {
        assertTrue(isNewer(recordedAt = 1_000L, lastRecordedAt = 900L))
    }

    @Test
    fun an_exit_already_in_the_log_is_not_written_again() {
        assertFalse(isNewer(recordedAt = 1_000L, lastRecordedAt = 1_000L))
        assertFalse(isNewer(recordedAt = 900L, lastRecordedAt = 1_000L))
    }

    @Test
    fun a_first_start_has_written_nothing_yet() {
        assertTrue(isNewer(recordedAt = 1L, lastRecordedAt = 0L))
    }
}
