/*
 * GlassTube — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.glasstube.core

import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Two rules the app states in prose and must not lose: "one is one" and "expected failures return
 * Err, cancellation is not an expected failure".
 */
class ContractTest {

    @Test
    fun `one is singular`() {
        assertEquals("1 like", plural(1, "like"))
        assertEquals("1 reply", plural(1, "reply", "replies"))
        assertEquals("1 note", plural(1, "note"))
    }

    @Test
    fun `zero and more are plural`() {
        assertEquals("0 likes", plural(0, "like"))
        assertEquals("2 likes", plural(2, "like"))
        assertEquals("0 replies", plural(0, "reply", "replies"))
        assertEquals("2 replies", plural(2, "reply", "replies"))
    }

    @Test
    fun `the default plural follows the three regular rules`() {
        assertEquals("3 watches", plural(3, "watch"))
        assertEquals("3 classes", plural(3, "class"))
        assertEquals("2 replies", plural(2, "reply"))
        // Vowel + y stays +s; irregulars still pass their own plural explicitly.
        assertEquals("2 days", plural(2, "day"))
        assertEquals("3 people", plural(3, "person", "people"))
    }

    @Test
    fun `attempt keeps a successful value`() = runTest {
        val result = attempt { 42 }
        assertTrue(result is AppResult.Ok)
        assertEquals(42, (result as AppResult.Ok).value)
    }

    @Test
    fun `attempt turns an expected failure into Err with its cause`() = runTest {
        val boom = IllegalStateException("no network")
        val result = attempt<Int> { throw boom }

        assertTrue(result is AppResult.Err)
        result as AppResult.Err
        assertEquals("no network", result.message)
        assertSame(boom, result.cause)
    }

    @Test
    fun `attempt falls back to the class name when the message is null`() = runTest {
        val result = attempt<Int> { throw IllegalStateException() }

        assertTrue(result is AppResult.Err)
        assertEquals("IllegalStateException", (result as AppResult.Err).message)
    }

    @Test
    fun `attempt never swallows cancellation`() = runTest {
        // Cancellation is the caller's decision, not a failure to report. Wrapping it would turn a
        // screen leaving into a permanent error message.
        try {
            attempt<Int> { throw CancellationException("left the screen") }
            fail("cancellation was caught")
        } catch (e: CancellationException) {
            assertEquals("left the screen", e.message)
        }
    }

    @Test
    fun `map transforms Ok and passes Err through untouched`() {
        val ok = AppResult.Ok(2).map { it * 2 }
        assertTrue(ok is AppResult.Ok)
        assertEquals(4, (ok as AppResult.Ok).value)

        val err = AppResult.Err("bad")
        val mapped: AppResult<Int> = err.map { 1 }
        assertSame(err, mapped)
        assertNull((mapped as AppResult.Err).cause)
    }
}
