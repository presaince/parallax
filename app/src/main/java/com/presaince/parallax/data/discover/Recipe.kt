/*
 * Parallax — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.parallax.data.discover

import com.presaince.parallax.data.model.VideoSummary
import org.json.JSONArray
import org.json.JSONObject
/**
 * A recipe is data, never code. Every field below is implemented by [DiscoverEngine]; a knob that
 * nothing reads is a lie, so the schema is smaller than the design sketch.
 *
 * Deliberately absent:
 * - `maxSubs`: related items carry no subscriber count, and fetching one per candidate turns a
 *   walk into a crawl.
 * - `language`: same reason, no language metadata without an extra extraction per candidate.
 * - `likePerView` / `commentPerView`: likes and comment counts only exist on a full video extract.
 */
data class Recipe(
    val name: String,
    val seed: Seed,
    val filters: Filters,
    val score: Score,
    val walk: Walk,
    /** One line in the reader's own words. Empty means "say it from the filters". */
    val description: String = "",
    val source: RecipeSource = RecipeSource.BUILTIN,
    val json: String = "",
)

enum class RecipeSource { BUILTIN, USER }

data class Seed(
    /** Only "related" exists; the format keeps room for "search" without a migration. */
    val kind: String = "related",
    /** topChannels | history | random */
    val seed: String,
    val count: Int,
)

data class Filters(
    val minDurationSec: Long? = null,
    val maxDurationSec: Long? = null,
    val maxAgeDays: Int? = null,
    val minViewCount: Long? = null,
    val excludeChannels: List<String> = emptyList(),
    val excludeKeywords: List<String> = emptyList(),
) {
    fun accepts(video: VideoSummary): Boolean {
        // A live stream reports duration 0; unknown lengths are not evidence against a video.
        if (minDurationSec != null && video.durationSec in 1 until minDurationSec) return false
        if (maxDurationSec != null && video.durationSec > maxDurationSec) return false
        if (maxAgeDays != null) {
            val published = video.publishedAtMs ?: return false
            val ageDays = (System.currentTimeMillis() - published) / 86_400_000L
            if (ageDays > maxAgeDays) return false
        }
        if (minViewCount != null && (video.viewCount ?: 0) < minViewCount) return false
        if (excludeChannels.any { it.equals(video.channelUrl, ignoreCase = true) }) return false
        if (excludeKeywords.any { keyword ->
                video.title.contains(keyword, ignoreCase = true) ||
                    video.channelName.contains(keyword, ignoreCase = true)
            }
        ) {
            return false
        }
        return true
    }
}

data class Score(
    val views: Double = 1.0,
    val recency: Double = 0.3,
)

data class Walk(
    val hops: Int,
    val branch: Int,
    val wildcard: Double,
)

sealed interface RecipeResult {
    data class Ok(val recipe: Recipe) : RecipeResult
    data class Err(val problems: List<String>) : RecipeResult
}

/**
 * Strict parsing. Unknown keys are errors rather than ignored: a typo in a recipe that silently
 * does nothing is worse than a message naming the field.
 */
object RecipeParser {

    private val SEED_KEYS =
        setOf("version", "name", "description", "from", "filters", "score", "walk")
    private val FROM_KEYS = setOf("kind", "seed", "count")
    private val FILTER_KEYS = setOf(
        "minDurationSec",
        "maxDurationSec",
        "maxAgeDays",
        "minViewCount",
        "excludeChannels",
        "excludeKeywords",
    )
    private val SCORE_KEYS = setOf("views", "recency")
    private val WALK_KEYS = setOf("hops", "branch", "wildcard")

    fun parse(json: String, source: RecipeSource = RecipeSource.BUILTIN): RecipeResult {
        val root = runCatching { JSONObject(json) }.getOrElse {
            return RecipeResult.Err(listOf("not a JSON object"))
        }
        val problems = mutableListOf<String>()

        unknownKeys(root, SEED_KEYS, "recipe")?.let(problems::add)

        if (root.optInt("version", -1) != 1) problems += "version: must be 1"

        val name = root.optString("name").trim()
        if (name.isEmpty()) problems += "name: required"
        if (name.length > 80) problems += "name: at most 80 characters"

        val description = root.optString("description").trim()
        if (description.length > 120) problems += "description: at most 120 characters"

        val from = root.optJSONObject("from")
        val seed = if (from == null) {
            problems += "from: required"
            null
        } else {
            unknownKeys(from, FROM_KEYS, "from")?.let(problems::add)
            val kind = from.optString("kind", "related")
            if (kind != "related") problems += "from.kind: only \"related\" is supported"
            val seedValue = from.optString("seed")
            if (seedValue !in setOf("topChannels", "history", "random")) {
                problems += "from.seed: topChannels, history or random"
            }
            val count = from.optInt("count", -1)
            if (count !in 1..20) problems += "from.count: 1 to 20"
            Seed(kind = kind, seed = seedValue, count = count.coerceIn(1, 20))
        }

        val filtersJson = root.optJSONObject("filters")
        val filters = if (filtersJson == null) {
            problems += "filters: required"
            null
        } else {
            unknownKeys(filtersJson, FILTER_KEYS, "filters")?.let(problems::add)
            Filters(
                minDurationSec = duration(filtersJson, "minDurationSec", problems),
                maxDurationSec = duration(filtersJson, "maxDurationSec", problems),
                maxAgeDays = intIn(filtersJson, "maxAgeDays", 1, 3650, problems),
                minViewCount = longIn(filtersJson, "minViewCount", 0, 10_000_000_000, problems),
                excludeChannels = strings(filtersJson, "excludeChannels", problems),
                excludeKeywords = strings(filtersJson, "excludeKeywords", problems),
            )
        }

        val scoreJson = root.optJSONObject("score")
        val score = if (scoreJson == null) {
            problems += "score: required"
            null
        } else {
            unknownKeys(scoreJson, SCORE_KEYS, "score")?.let(problems::add)
            Score(
                views = doubleIn(scoreJson, "views", 0.0, 10.0, problems) ?: 1.0,
                recency = doubleIn(scoreJson, "recency", 0.0, 1.0, problems) ?: 0.3,
            )
        }

        val walkJson = root.optJSONObject("walk")
        val walk = if (walkJson == null) {
            problems += "walk: required"
            null
        } else {
            unknownKeys(walkJson, WALK_KEYS, "walk")?.let(problems::add)
            Walk(
                hops = intIn(walkJson, "hops", 1, 6, problems) ?: 3,
                branch = intIn(walkJson, "branch", 1, 10, problems) ?: 5,
                wildcard = doubleIn(walkJson, "wildcard", 0.0, 1.0, problems) ?: 0.0,
            )
        }

        if (filters != null && filters.minDurationSec != null && filters.maxDurationSec != null &&
            filters.minDurationSec > filters.maxDurationSec
        ) {
            problems += "filters: minDurationSec is greater than maxDurationSec"
        }

        if (problems.isNotEmpty()) return RecipeResult.Err(problems)

        return RecipeResult.Ok(
            Recipe(
                name = name,
                seed = requireNotNull(seed),
                filters = requireNotNull(filters),
                score = requireNotNull(score),
                walk = requireNotNull(walk),
                description = description,
                source = source,
                json = json,
            ),
        )
    }

    private fun unknownKeys(obj: JSONObject, allowed: Set<String>, path: String): String? {
        val unknown = obj.keys().asSequence().filter { it !in allowed }.toList()
        return if (unknown.isEmpty()) null else "$path: unknown key ${unknown.joinToString()}"
    }

    private fun duration(obj: JSONObject, key: String, problems: MutableList<String>): Long? =
        longIn(obj, key, 1, 86_400, problems)

    private fun intIn(
        obj: JSONObject,
        key: String,
        min: Int,
        max: Int,
        problems: MutableList<String>,
    ): Int? {
        if (!obj.has(key)) return null
        val value = obj.optInt(key, Int.MIN_VALUE)
        if (value !in min..max) {
            problems += "$key: must be $min to $max"
            return null
        }
        return value
    }

    private fun longIn(
        obj: JSONObject,
        key: String,
        min: Long,
        max: Long,
        problems: MutableList<String>,
    ): Long? {
        if (!obj.has(key)) return null
        val value = obj.optLong(key, Long.MIN_VALUE)
        if (value !in min..max) {
            problems += "$key: must be $min to $max"
            return null
        }
        return value
    }

    private fun doubleIn(
        obj: JSONObject,
        key: String,
        min: Double,
        max: Double,
        problems: MutableList<String>,
    ): Double? {
        if (!obj.has(key)) return null
        val value = obj.optDouble(key, Double.NaN)
        if (!value.isFinite() || value < min || value > max) {
            problems += "$key: must be $min to $max"
            return null
        }
        return value
    }

    private fun strings(
        obj: JSONObject,
        key: String,
        problems: MutableList<String>,
    ): List<String> {
        val array: JSONArray = obj.optJSONArray(key) ?: return emptyList()
        if (array.length() > 50) {
            problems += "$key: at most 50 entries"
            return emptyList()
        }
        return buildList {
            for (i in 0 until array.length()) {
                val value = array.optString(i).trim()
                if (value.isEmpty() || value.length > 200) {
                    problems += "$key: entry ${i + 1} must be 1 to 200 characters"
                    continue
                }
                add(value)
            }
        }
    }
}

/**
 * What a recipe does, in one sentence, for the row that lists it. A recipe that wrote its own
 * `description` speaks for itself; otherwise the sentence is assembled from the filters, so a
 * one-line explanation can never drift from what the walk actually does.
 */
fun describe(recipe: Recipe): String = recipe.description.ifBlank {
    buildString {
        append(
            when (recipe.seed.seed) {
                "history" -> "Related to ${recipe.seed.count} videos you watched"
                "topChannels" -> "Related to ${recipe.seed.count} of your top channels"
                "random" -> "Related to ${recipe.seed.count} random videos in your library"
                else -> "Related to your library"
            },
        )
        recipe.filters.minDurationSec?.let { append(" · at least ${it / 60} min") }
        recipe.filters.maxAgeDays?.let { append(" · newer than $it days") }
        if ("shorts" in recipe.filters.excludeKeywords) append(" · no Shorts")
        if ("live" in recipe.filters.excludeKeywords) append(" · no live")
    }
}
