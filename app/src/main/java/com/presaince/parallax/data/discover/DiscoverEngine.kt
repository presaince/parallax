/*
 * Parallax — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.parallax.data.discover

import com.presaince.parallax.core.AppResult
import com.presaince.parallax.core.attempt
import com.presaince.parallax.data.db.ParallaxDb
import com.presaince.parallax.data.db.SubscriptionEntity
import com.presaince.parallax.data.db.VideoEntity
import com.presaince.parallax.data.model.VideoSummary
import com.presaince.parallax.data.source.VideoSource
import kotlin.math.exp
import kotlin.math.log10
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The slice of the database the walk needs. An interface so the engine can be tested without Room:
 * every behaviour worth pinning (seen filtering, deadlines, empty picks) lives in the engine, and
 * the SQL is already covered by the smoke test.
 */
interface DiscoverStore {
    suspend fun recentHistory(limit: Int): List<com.presaince.parallax.data.db.HistoryEntity>
    suspend fun progressIds(): List<String>
    suspend fun subscriptions(): List<SubscriptionEntity>
    suspend fun findVideo(id: String): VideoEntity?
    suspend fun recentVideos(limit: Int): List<VideoEntity>
}

class RoomDiscoverStore(private val db: ParallaxDb) : DiscoverStore {
    override suspend fun recentHistory(limit: Int) = db.history().recent(limit)
    override suspend fun progressIds(): List<String> = db.progress().videoIds()
    override suspend fun subscriptions(): List<SubscriptionEntity> = db.subscriptions().all()
    override suspend fun findVideo(id: String): VideoEntity? = db.videos().find(id)
    override suspend fun recentVideos(limit: Int): List<VideoEntity> = db.videos().recent(limit)
}

data class WalkStep(
    val video: VideoSummary,
    /** Titles from the seed to this video: "you → A → B → this". */
    val path: List<String>,
    val score: Double,
)

private data class Node(val video: VideoSummary, val path: List<String>)

/**
 * A bounded random walk over the related-video graph, scored by a recipe. The path is kept because
 * explaining a pick is the one thing the incumbent feed cannot do.
 */
class DiscoverEngine(
    private val source: VideoSource,
    private val store: DiscoverStore,
    private val random: Random = Random.Default,
    /** Bored's own switch: subscribed channels belong to the feed, not to the walk. */
    private val hideSubscribed: () -> Boolean = { false },
) {

    /** Test-friendly constructor: the Room adapter is the only production implementation. */
    constructor(
        source: VideoSource,
        db: ParallaxDb,
        random: Random = Random.Default,
        hideSubscribed: () -> Boolean = { false },
    ) : this(source, RoomDiscoverStore(db), random, hideSubscribed)

    /**
     * One level, not a four-hop walk: the list is what people use, and a deep walk spent ten
     * seconds computing steps nobody would reach. Seeds in, top picks out, refreshable.
     */
    suspend fun picks(recipe: Recipe, limit: Int): AppResult<List<WalkStep>> =
        withContext(Dispatchers.IO) {
            attempt {
                val seeds = seedVideos(recipe.seed)
                check(seeds.isNotEmpty()) {
                    "Nothing to walk from — refresh the feed or watch something"
                }

                val visited = seeds.map { it.id }.toMutableSet()
                val seen = (
                    store.recentHistory(SEEN_LIMIT).map { it.videoId } +
                        store.progressIds()
                    ).toSet()
                val subscribed = subscribedChannels()
                val now = System.currentTimeMillis()
                val candidates = mutableListOf<Node>()

                for (node in seeds.take(recipe.walk.branch)) {
                    val related = when (val result = source.related(node.id)) {
                        is AppResult.Ok -> result.value
                        is AppResult.Err -> emptyList()
                    }
                    related.take(RELATED_PER_NODE).forEach { video ->
                        if (video.id !in visited) {
                            candidates += Node(video, listOf(node.title, video.title))
                        }
                    }
                }

                val scored = candidates
                    .filter { it.video.id !in seen }
                    .filter { it.video.channelUrl?.lowercase() !in subscribed }
                    .filter { recipe.filters.accepts(it.video) }
                    .distinctBy { it.video.id }
                    .map { it to scoreOf(it.video, recipe.score, now) }
                    .sortedByDescending { it.second }
                    .take(limit * 2)

                // An empty pick set has three different causes and three different fixes. Naming
                // which one applies is what makes the empty state diagnosable instead of silent:
                // no related videos at all (extractor or network), everything seen (clear history
                // or pick a tamer seed), or the recipe's own filters (loosen them).
                check(scored.isNotEmpty()) {
                    when {
                        candidates.isEmpty() -> "No related videos found for ${seeds.size} seed(s)"
                        candidates.all { it.video.id in seen } ->
                            "Every related video was already seen (${candidates.size} found)"
                        candidates.all { it.video.channelUrl?.lowercase() in subscribed } ->
                            "Every related video is from a subscribed channel (${candidates.size} found)"
                        else -> "No video passed this recipe's filters (${candidates.size} found)"
                    }
                }

                // Surprise is the recipe's wildcard: at zero the page is simply the top of the
                // score, at one it is a shuffle of the same pool.
                val picked = if (recipe.walk.wildcard <= 0.0) {
                    scored.take(limit)
                } else {
                    scored.shuffled(random).take(limit)
                }

                picked.map { WalkStep(video = it.first.video, path = it.first.path, score = it.second) }
            }
        }

    suspend fun walk(recipe: Recipe): AppResult<List<WalkStep>> = withContext(Dispatchers.IO) {
        attempt {
            val seeds = seedVideos(recipe.seed)
            check(seeds.isNotEmpty()) { "Nothing to walk from — refresh the feed or watch something" }

            val visited = seeds.map { it.id }.toMutableSet()
            // A walk that recommends what you watched last night is just a history list.
            val seen = (
                store.recentHistory(SEEN_LIMIT).map { it.videoId } +
                    store.progressIds()
                ).toSet()
            val steps = mutableListOf<WalkStep>()
            val subscribed = subscribedChannels()
            var frontier = seeds.map { Node(it, listOf(it.title)) }
            val now = System.currentTimeMillis()

            repeat(recipe.walk.hops) {
                val candidates = mutableListOf<Node>()
                for (node in frontier.take(recipe.walk.branch)) {
                    val related = when (val result = source.related(node.video.id)) {
                        is AppResult.Ok -> result.value
                        // One dead branch should not end the walk.
                        is AppResult.Err -> emptyList()
                    }
                    related.take(RELATED_PER_NODE).forEach { video ->
                        if (video.id !in visited) {
                            candidates += Node(video, node.path + video.title)
                        }
                    }
                }

                val scored = candidates
                    .filter { it.video.id !in seen }
                    .filter { it.video.channelUrl?.lowercase() !in subscribed }
                    .filter { recipe.filters.accepts(it.video) }
                    .distinctBy { it.video.id }
                    .map { it to scoreOf(it.video, recipe.score, now) }
                    .sortedByDescending { it.second }
                    .take(recipe.walk.branch)

                if (scored.isEmpty()) return@repeat

                val chosen = if (scored.size > 1 && random.nextDouble() < recipe.walk.wildcard) {
                    scored.random(random)
                } else {
                    scored.first()
                }

                visited += chosen.first.video.id
                steps += WalkStep(
                    video = chosen.first.video,
                    path = chosen.first.path,
                    score = chosen.second,
                )
                frontier = scored.map { it.first }
            }

            steps
        }
    }

    /** The channels this Bored page refuses, read at walk time so a toggle takes effect at once. */
    private suspend fun subscribedChannels(): Set<String> =
        if (!hideSubscribed()) {
            emptySet()
        } else {
            store.subscriptions().map { it.url.lowercase() }.toSet()
        }

    private suspend fun seedVideos(seed: Seed): List<VideoSummary> = when (seed.seed) {
        "history" -> store.recentHistory(seed.count * 5)
            .map { entry ->
                store.findVideo(entry.videoId)?.toSummary()
                    ?: VideoSummary(
                        id = entry.videoId,
                        url = "https://www.youtube.com/watch?v=${entry.videoId}",
                        title = entry.title,
                        channelName = entry.channelName,
                        durationSec = 0,
                        thumbnailUrl = null,
                    )
            }
            .distinctBy { it.id }
            .take(seed.count)

        "random" -> store.recentVideos(POOL)
            .shuffled(random)
            .take(seed.count)
            .map { it.toSummary() }

        else -> store.recentVideos(seed.count).map { it.toSummary() }
    }

    private fun VideoEntity.toSummary() = VideoSummary(
        id = id,
        url = "https://www.youtube.com/watch?v=$id",
        title = title,
        channelName = channelName,
        durationSec = durationSec,
        thumbnailUrl = thumbUrl,
        channelUrl = channelId,
        publishedAtMs = publishedAt,
        viewCount = viewCount,
        likeCount = likeCount,
        commentCount = commentCount,
    )

    private companion object {
        const val RELATED_PER_NODE = 12
        const val POOL = 200
        const val SEEN_LIMIT = 500
    }
}

/**
 * Views on a log scale (six orders of magnitude is the whole range) plus an exponential recency
 * term. Pure so the scoring is testable without a network.
 */
internal fun scoreOf(video: VideoSummary, score: Score, now: Long): Double {
    val views = video.viewCount
        ?.takeIf { it > 0 }
        ?.let { log10(it.toDouble() + 1) / 6.0 * score.views }
        ?: 0.0
    val recency = video.publishedAtMs
        ?.let { published ->
            val days = ((now - published).coerceAtLeast(0)) / 86_400_000.0
            score.recency * exp(-days / 30.0)
        }
        ?: 0.0
    return views + recency
}
