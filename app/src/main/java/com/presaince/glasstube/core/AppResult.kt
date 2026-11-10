/*
 * GlassTube — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.glasstube.core

import kotlin.coroutines.cancellation.CancellationException

/**
 * Expected failures carry a message the UI can show. Programmer errors are left to crash:
 * catching nothing is better than catching everything and calling it resilience.
 */
sealed interface AppResult<out T> {
    data class Ok<T>(val value: T) : AppResult<T>
    data class Err(
        val message: String,
        val cause: Throwable? = null,
        val sourceError: SourceError? = null,
    ) : AppResult<Nothing>
}

/**
 * Why the YouTube source failed, in a form the UI can act on. Carried on [AppResult.Err] by the
 * source layer only. Other layers leave it null: an expected local failure is not a source error.
 */
sealed interface SourceError {
    /** One plain sentence for the screen, with no exclamation marks. */
    val userMessage: String

    /** YouTube is asking to confirm a human. Retrying without sign-in is unlikely to help. */
    data object SignInConfirmRequired : SourceError {
        override val userMessage = "YouTube is asking for a sign-in to confirm a human."
    }

    /** The video is restricted in the selected content region. */
    data object GeoBlocked : SourceError {
        override val userMessage = "This video is not available in the selected region."
    }

    /** The video is age restricted. */
    data object AgeRestricted : SourceError {
        override val userMessage = "This video is age restricted."
    }

    /** The video is private or removed. */
    data object PrivateOrRemoved : SourceError {
        override val userMessage = "This video is private or has been removed."
    }

    /**
     * Only the channel's paying members can watch it. YouTube reports this as a generic
     * unavailability, so the source layer recognises the wording; the screen needs its own state
     * because retrying can never help.
     */
    data object MembersOnly : SourceError {
        override val userMessage = "This video is only for members of the channel."
    }

    /** True when trying again cannot change the answer. The UI offers no retry for these. */
    val permanent: Boolean
        get() = this is MembersOnly || this is PrivateOrRemoved || this is AgeRestricted ||
            this is GeoBlocked || this is SignInConfirmRequired

    /** YouTube returned SABR only streaming data, with no direct stream URLs. */
    data object SabrOnly : SourceError {
        override val userMessage = "YouTube returned streaming data with no playable stream URLs."
    }

    /** A bot check blocked the request. */
    data object BotCheck : SourceError {
        override val userMessage = "YouTube blocked this request as a bot."
    }

    /** The network failed, timed out, or the downloader returned an error status. */
    data object NetworkFailure : SourceError {
        override val userMessage = "The request failed. Check your connection."
    }

    /** Parsing or extraction broke, most likely because YouTube changed something upstream. */
    data object ExtractorBroken : SourceError {
        override val userMessage = "YouTube changed something and extraction broke."
    }

    /** Anything else the source layer could not classify. Keeps the raw message for logging. */
    data class Unknown(val raw: String) : SourceError {
        override val userMessage = "YouTube did not return this video."
    }
}

suspend fun <T> attempt(block: suspend () -> T): AppResult<T> = try {
    AppResult.Ok(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    AppResult.Err(e.message ?: e.javaClass.simpleName, e)
}

inline fun <T, R> AppResult<T>.map(transform: (T) -> R): AppResult<R> = when (this) {
    is AppResult.Ok -> AppResult.Ok(transform(value))
    is AppResult.Err -> this
}
