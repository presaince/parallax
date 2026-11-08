/*
 * Parallax — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.parallax.data.source

import com.presaince.parallax.core.AppLog
import com.presaince.parallax.core.AppResult
import com.presaince.parallax.core.SourceError
import com.presaince.parallax.data.model.StreamSet
import com.presaince.parallax.data.model.VideoDetail
import com.presaince.parallax.data.model.VideoSummary
import com.presaince.parallax.data.resync.LlmClient
import com.presaince.parallax.data.resync.ResyncSettings
import com.presaince.parallax.data.resync.ResyncStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Tag every self-repair line carries, so Logs teaches the loop at a glance. */
const val REPAIR_TAG = "selfrepair"

/**
 * On-device self-repair for the source layer. The extractor breaks in a handful of recognisable
 * ways; this runs the cheap healing first and records every step so a failure reads like a trace.
 */
suspend fun <T> withSourceRepair(
    log: AppLog?,
    op: String,
    reset: suspend () -> Unit,
    block: suspend () -> T,
): AppResult<T> {
    log?.info(REPAIR_TAG, "$op start")
    val first = sourceAttempt { block() }
    when (first) {
        is AppResult.Ok -> {
            log?.info(REPAIR_TAG, "$op ok")
            return first
        }
        is AppResult.Err -> {
            val kind = first.sourceError?.let { it::class.simpleName } ?: "none"
            log?.warn(REPAIR_TAG, "$op failed kind=$kind msg=${first.message.take(180)}")
            if (first.sourceError != SourceError.ExtractorBroken) {
                log?.info(REPAIR_TAG, "$op healing skipped: not extractor-broken")
                return first
            }
        }
    }
    log?.info(REPAIR_TAG, "$op healing: resetting extractor, retrying once")
    runCatching { reset() }.onFailure { log?.warn(REPAIR_TAG, "$op reset failed: ${it.message}") }
    val second = sourceAttempt { block() }
    when (second) {
        is AppResult.Ok -> log?.info(REPAIR_TAG, "$op recovered after reset")
        is AppResult.Err -> log?.error(
            REPAIR_TAG,
            "$op still failed kind=${second.sourceError?.let { it::class.simpleName }} msg=${second.message.take(180)}",
        )
    }
    return second
}

/**
 * Stable video ids used as canaries. Expectations are deliberately loose (a title, a positive
 * duration, a playable stream) so a view-count change does not fail and a renamed field does.
 * Two ids cover the common shape; more ids only add flake when one video is removed.
 */
object KnownVideos {
    val ids: List<String> = listOf(
        "kJQP7kiw5Fk",
        "jNQXAC9IVRw",
    )

    fun checkSummary(summary: VideoSummary): List<String> = buildList {
        if (summary.title.isBlank()) add("blank title")
        if (summary.id !in ids) add("unknown id ${summary.id}")
    }

    fun checkVideo(detail: VideoDetail): List<String> = buildList {
        if (detail.title.isBlank()) add("blank title")
        if (detail.durationSec <= 0) add("duration ${detail.durationSec}")
    }

    fun checkStreams(streams: StreamSet): List<String> = buildList {
        val urls = listOfNotNull(streams.muxedUrl, streams.videoUrl, streams.audioUrl)
        if (urls.all { it.isBlank() }) add("no playable stream")
    }
}

/** A fallback the model may propose for a search that YouTube answered with nothing useful. */
data class RepairProposal(
    val queryAlternative: String? = null,
    val regionCode: String? = null,
)

/** Pure JSON handling for the model's reply. Never throws; a bad reply proposes nothing. */
object RepairProposalParser {

    fun parse(reply: String, originalQuery: String): RepairProposal? {
        val json = reply.substringAfter('{', "").let { if (it.isEmpty()) return null else "{$it" }
        val trimmed = json.substring(0, json.lastIndexOf('}') + 1)
        val root = runCatching { JSONObject(trimmed) }.getOrNull() ?: return null
        val rawQuery = root.optString("queryAlternative", "").trim()
        val query = rawQuery.takeIf { candidate ->
            candidate.isNotBlank() &&
                candidate.length <= 100 &&
                candidate != originalQuery &&
                candidate.none { it.isISOControl() }
        }
        val rawRegion = root.optString("regionCode", "").trim().uppercase()
            .takeIf { code -> contentRegions.any { it.code == code } }
        return if (query != null || rawRegion != null) {
            RepairProposal(queryAlternative = query, regionCode = rawRegion)
        } else {
            null
        }
    }
}

object RepairPrompt {

    fun systemPrompt(): String = buildString {
        appendLine("You help a YouTube client recover from an empty extractor response.")
        appendLine("Reply with a single JSON object and nothing else:")
        appendLine("""{"queryAlternative":"...","regionCode":"US"}""")
        appendLine("Rules:")
        appendLine("- queryAlternative is a shorter, less filtered search query.")
        appendLine("- regionCode is the two-letter region most likely to answer.")
        appendLine("- Either may be empty. Never return executable code.")
        append("Only propose fields you are reasonably confident will answer.")
    }

    fun userPrompt(query: String, error: String): String = buildString {
        appendLine("FAILED_QUERY $query")
        appendLine("ERROR ${error.take(240)}")
        append("Propose at most one fallback.")
    }
}

/**
 * Asks the user's own model for a fallback search when extraction broke. Off by default and
 * BYOK, sharing the Timestamp Resync key: no key, no request, and every step is logged.
 */
class LlmRepairPlanner(
    private val llm: LlmClient,
    private val store: ResyncStore,
    private val log: AppLog? = null,
) {
    suspend fun plan(query: String, error: String): RepairProposal? = withContext(Dispatchers.IO) {
        val settings: ResyncSettings = store.load()
        if (!settings.ready) {
            log?.info(REPAIR_TAG, "llm tier skipped: BYOK not ready")
            return@withContext null
        }
        log?.info(REPAIR_TAG, "llm tier: asking model for a fallback query")
        val reply = when (val result = llm.complete(settings, RepairPrompt.systemPrompt(), RepairPrompt.userPrompt(query, error))) {
            is AppResult.Ok -> result.value
            is AppResult.Err -> {
                log?.warn(REPAIR_TAG, "llm tier failed: ${result.message.take(140)}")
                return@withContext null
            }
        }
        val proposal = RepairProposalParser.parse(reply, query)
        if (proposal == null) {
            log?.warn(REPAIR_TAG, "llm tier: proposal rejected by validation")
        } else {
            log?.info(REPAIR_TAG, "llm tier: proposal query=${proposal.queryAlternative} region=${proposal.regionCode}")
        }
        proposal
    }
}
