/*
 * GlassTube — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.glasstube.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The log window is 300 entries; every request that logs for no reason shortens the history the
 * next bug report can carry.
 */
class HttpTraceTest {

    @Test
    fun `a quick success is silent`() {
        assertNull(HttpTrace.note(code = 200, bytes = 604_703, elapsedMs = 240))
    }

    @Test
    fun `a failure always logs, with its status and how long it took`() {
        assertEquals("HTTP 403 after 180ms", HttpTrace.note(403, 512, 180))
        assertEquals("HTTP 500 after 30ms", HttpTrace.note(500, 0, 30))
    }

    @Test
    fun `a success with nothing in it is a bug, not a success`() {
        assertEquals("200 with an empty body", HttpTrace.note(200, 0, 120))
    }

    @Test
    fun `a slow success logs even though it worked`() {
        assertEquals("slow 9000ms for 6409B", HttpTrace.note(200, 6_409, 9_000))
        assertNull(HttpTrace.note(200, 6_409, 7_999))
    }
}
