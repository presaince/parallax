/*
 * GlassTube — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.glasstube.data.source

import com.presaince.glasstube.core.AppResult
import com.presaince.glasstube.core.SourceError
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.schabi.newpipe.extractor.exceptions.AgeRestrictedContentException
import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException
import org.schabi.newpipe.extractor.exceptions.ParsingException
import org.schabi.newpipe.extractor.exceptions.SignInConfirmNotBotException

/**
 * The source layer classifies an extractor failure into one reason the UI can act on, and it
 * never swallows cancellation.
 */
class SourceErrorsTest {

    @Test
    fun `sign in confirm is its own reason`() {
        val error = SignInConfirmNotBotException("confirm").toSourceError()
        assertSame(SourceError.SignInConfirmRequired, error)
    }

    @Test
    fun `age restricted is not read as private`() {
        // Both are ContentNotAvailableException subclasses, so the specific check must come first.
        val error = AgeRestrictedContentException("age").toSourceError()
        assertSame(SourceError.AgeRestricted, error)
    }

    @Test
    fun `a parsing failure means extraction broke`() {
        val error = ParsingException("no lockup").toSourceError()
        assertSame(SourceError.ExtractorBroken, error)
    }

    @Test
    fun `an io failure means the network failed`() {
        val error = IOException("timed out").toSourceError()
        assertSame(SourceError.NetworkFailure, error)
    }

    @Test
    fun `anything else keeps the raw message`() {
        val error = IllegalStateException("no readable captions").toSourceError()
        assertTrue(error is SourceError.Unknown)
        assertEquals("no readable captions", (error as SourceError.Unknown).raw)
    }

    @Test
    fun `members only is its own reason, read from the wording`() {
        // The extractor raises this as a plain unavailability; the sentence is the only signal.
        val error = ContentNotAvailableException(
            "This video is only available for members of the channel of this video",
        ).toSourceError()
        assertSame(SourceError.MembersOnly, error)
    }

    @Test
    fun `a private video is still read as private`() {
        val error = ContentNotAvailableException("This video is private").toSourceError()
        assertSame(SourceError.PrivateOrRemoved, error)
    }

    @Test
    fun `a permanent failure is one that retrying cannot change`() {
        assertTrue(SourceError.MembersOnly.permanent)
        assertTrue(SourceError.PrivateOrRemoved.permanent)
        assertTrue(SourceError.AgeRestricted.permanent)
        assertFalse(SourceError.NetworkFailure.permanent)
        assertFalse(SourceError.ExtractorBroken.permanent)
    }

    @Test
    fun `sourceAttempt keeps a successful value`() = runTest {
        val result = sourceAttempt { 42 }
        assertTrue(result is AppResult.Ok)
        assertEquals(42, (result as AppResult.Ok).value)
    }

    @Test
    fun `sourceAttempt attaches the reason to Err`() = runTest {
        val result = sourceAttempt<Int> { throw SignInConfirmNotBotException("confirm") }
        assertTrue(result is AppResult.Err)
        result as AppResult.Err
        assertSame(SourceError.SignInConfirmRequired, result.sourceError)
        assertEquals("confirm", result.message)
    }
}
