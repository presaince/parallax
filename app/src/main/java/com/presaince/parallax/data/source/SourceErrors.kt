/*
 * Parallax — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.parallax.data.source

import com.presaince.parallax.core.AppResult
import com.presaince.parallax.core.SourceError
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import org.schabi.newpipe.extractor.exceptions.AgeRestrictedContentException
import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException
import org.schabi.newpipe.extractor.exceptions.ContentNotSupportedException
import org.schabi.newpipe.extractor.exceptions.ExtractionException
import org.schabi.newpipe.extractor.exceptions.GeographicRestrictionException
import org.schabi.newpipe.extractor.exceptions.PrivateContentException
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.exceptions.SignInConfirmNotBotException

/**
 * Runs one source call and classifies an expected failure into a [SourceError]. Programmer
 * errors still throw, and cancellation still propagates. [LinkageError] is the one dependency
 * fault treated as expected: a missing class from the extractor jar is not the app's bug to
 * crash on, and the screen should say extraction broke.
 */
suspend fun <T> sourceAttempt(block: suspend () -> T): AppResult<T> = try {
    AppResult.Ok(block())
} catch (e: CancellationException) {
    throw e
} catch (e: LinkageError) {
    AppResult.Err(
        e.message ?: e.javaClass.simpleName,
        e,
        SourceError.ExtractorBroken,
    )
} catch (e: Exception) {
    AppResult.Err(
        e.message ?: e.javaClass.simpleName,
        e,
        e.toSourceError(),
    )
}

/**
 * One exception becomes one reason. Subclasses are checked before their parents: geo blocked
 * and age restricted are both [ContentNotAvailableException], so the order below decides them.
 * The SABR case is recognised by wording, because the extractor raises it as a generic
 * unsupported error when YouTube returns SABR only streaming data.
 */
fun Throwable.toSourceError(): SourceError = when (this) {
    is SignInConfirmNotBotException -> SourceError.SignInConfirmRequired
    is GeographicRestrictionException -> SourceError.GeoBlocked
    is AgeRestrictedContentException -> SourceError.AgeRestricted
    is PrivateContentException -> SourceError.PrivateOrRemoved
    is ReCaptchaException -> SourceError.BotCheck
    is ContentNotSupportedException -> if (isSabrOnly()) {
        SourceError.SabrOnly
    } else {
        SourceError.ExtractorBroken
    }
    // Members-only arrives as a plain unavailability whose only distinguishing feature is its
    // wording; there is no extractor type for it, so it is read before the generic case.
    is ContentNotAvailableException -> if (isMembersOnly()) {
        SourceError.MembersOnly
    } else {
        SourceError.PrivateOrRemoved
    }
    is ExtractionException -> SourceError.ExtractorBroken
    is IOException -> SourceError.NetworkFailure
    else -> SourceError.Unknown(message ?: javaClass.simpleName)
}

private fun Throwable.isSabrOnly(): Boolean =
    message?.contains("SABR", ignoreCase = true) == true

/** "This video is only available for members of the channel of this video", and its variants. */
internal fun Throwable.isMembersOnly(): Boolean {
    val text = message?.lowercase() ?: return false
    return "members" in text && ("channel" in text || "members-only" in text || "paid" in text)
}
