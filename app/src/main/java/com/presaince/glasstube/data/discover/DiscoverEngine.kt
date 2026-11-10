/*
 * GlassTube — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.glasstube.data.discover

import com.presaince.glasstube.core.AppResult
import com.presaince.glasstube.core.attempt
import com.presaince.glasstube.data.db.GlassTubeDb
import com.presaince.glasstube.data.db.SubscriptionEntity
import com.presaince.glasstube.data.db.VideoEntity
import com.presaince.glasstube.data.model.VideoSummary
import com.presaince.glasstube.data.source.ChannelFeed
import com.presaince.glasstube.data.source.VideoSource
import kotlin.math.exp
import kotlin.math.log10
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

/**
 * The slice of the database the walk needs. An interface so the engine can be tested without Room:
 * every behaviour worth pinning (seen filtering, deadlines, empty picks) lives in the engine, and
 * the SQL is already covered by the smoke test.
 */
interface DiscoverStore {
    suspend fun recentHistory(limit: Int): List<com.presaince.glasstube.data.db.HistoryEntity>
    suspend fun progressIds(): List<String>
    suspend fun subscriptions(): List<SubscriptionEntity>
    suspend fun findVideo(id: String): VideoEntity?
    suspend fun recentVideos(limit: Int): List<VideoEntity>
}

class RoomDiscoverStore(private val db: GlassTubeDb) : DiscoverStore {
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
    /** The free per-channel feed; without it an uploads recipe asks the extractor instead. */
    private val channelFeed: ChannelFeed? = null,
) {

    /** Test-friendly constructor: the Room adapter is the only production implementation. */
    constructor(
        source: VideoSource,
        db: GlassTubeDb,
        random: Random = Random.Default,
        hideSubscribed: () -> Boolean = { false },
        channelFeed: ChannelFeed? = null,
    ) : this(source, RoomDiscoverStore(db), random, hideSubscribed, channelFeed)

    /** A page of candidates and the sentence to show when it produced none. */
    private class Pool(val nodes: List<Node>, val empty: String)

    /**
     * One page, not a four-hop walk: the list is what people use, and a deep walk spent ten
     * seconds computing steps nobody would reach. The source depends on the recipe: one search,
     * one round of channel feeds, or one level of related videos.
     */
    suspend fun picks(recipe: Recipe, limit: Int): AppResult<List<WalkStep>> =
        withContext(Dispatchers.IO) {
            attempt {
                val seen = (
                    store.recentHistory(SEEN_LIMIT).map { it.videoId } +
                        store.progressIds()
                    ).toSet()
                // Fresh is explicitly "the channels you follow", so the skip-subscribed rule
                // cannot apply to it; everywhere else that switch is the page's own opinion.
                val subscribed = if (recipe.seed.kind == "uploads") {
                    emptySet()
                } else {
                    subscribedChannels()
                }
                val now = System.currentTimeMillis()
                val pool = when (recipe.seed.kind) {
                    "search" -> searchCandidates(recipe.seed)
                    "uploads" -> uploadCandidates(recipe.seed)
                    else -> relatedCandidates(recipe)
                }
                val candidates = pool.nodes

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
                // no candidates at all (source, network, or an empty library), everything seen
                // (clear history or pick a tamer source), or the recipe's own filters (loosen
                // them).
                check(scored.isNotEmpty()) {
                    when {
                        candidates.isEmpty() -> pool.empty
                        candidates.all { it.video.id in seen } ->
                            "Every candidate was already seen (${candidates.size} found)"
                        candidates.all { it.video.channelUrl?.lowercase() in subscribed } ->
                            "Every candidate is from a subscribed channel (${candidates.size} found)"
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

    /** Related only: the seeds are the reader's own videos, and the candidates are one hop out. */
    private suspend fun relatedCandidates(recipe: Recipe): Pool {
        val seeds = seedVideos(recipe.seed)
        check(seeds.isNotEmpty()) {
            "Nothing to walk from — refresh the feed or watch something"
        }
        val visited = seeds.map { it.id }.toMutableSet()
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
        return Pool(candidates, "No related videos found for ${seeds.size} seed(s)")
    }

    /**
     * One search for the recipe's own query. A failed search is not the end of the page: the
     * library is already on disk, so the same words are matched against titles and channel names
     * before the empty state is shown.
     */
    private suspend fun searchCandidates(seed: Seed): Pool {
        val found = when (val result = source.search(seed.query)) {
            is AppResult.Ok -> result.value.items
            is AppResult.Err -> emptyList()
        }
        val remote = found.distinctBy { it.id }.take(seed.count)
        if (remote.isNotEmpty()) {
            return Pool(
                remote.map { Node(it, listOf("Search: \"${seed.query}\"")) },
                "No results for \"${seed.query}\"",
            )
        }
        val words = seed.query.lowercase().split(Regex("\\s+")).filter { it.length > 2 }
        val local = store.recentVideos(POOL)
        val matched = local.filter { row ->
            words.any {
                row.title.contains(it, ignoreCase = true) ||
                    row.channelName.contains(it, ignoreCase = true)
            }
        }.ifEmpty { local }
        return Pool(
            matched.take(seed.count * 2).map { Node(it.toSummary(), listOf("From your library")) },
            "Nothing found for \"${seed.query}\" — the search failed and your library had no match",
        )
    }

    /**
     * The channels the reader follows, read from the free feed when one is available and from the
     * extractor otherwise. If the network is gone entirely, the feed already in the database is
     * the page.
     */
    private suspend fun uploadCandidates(seed: Seed): Pool {
        val channels = store.subscriptions().shuffled(random).take(seed.count)
        val fetched = if (channels.isEmpty()) {
            emptyList()
        } else {
            coroutineScope {
                channels.map { channel ->
                    async {
                        when (val fresh = channelFeed?.latest(channel.url, RSS_PER_CHANNEL)) {
                            is AppResult.Ok -> fresh.value
                            else -> when (val uploads = source.channelUploads(channel.url)) {
                                is AppResult.Ok -> uploads.value.items.take(RSS_PER_CHANNEL)
                                is AppResult.Err -> emptyList()
                            }
                        }
                    }
                }.awaitAll()
            }
        }
        val remote = fetched.flatten().distinctBy { it.id }
        if (remote.isNotEmpty()) {
            return Pool(
                remote.map { Node(it, listOf("New from ${it.channelName}")) },
                "No uploads from the channels you follow",
            )
        }
        val local = store.recentVideos(POOL).take(seed.count * RSS_PER_CHANNEL)
        return Pool(
            local.map { Node(it.toSummary(), listOf("From your library")) },
            "No uploads from the channels you follow, and your library is empty",
        )
    }

    suspend fun walk(recipe: Recipe): AppResult<List<WalkStep>> = withContext(Dispatchers.IO) {
        // A search or upload page has no related graph to hop; the one-page answer is the walk.
        if (recipe.seed.kind != "related") return@withContext picks(recipe, 12)
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
        /** The free channel feed publishes the latest fifteen, which is a page on its own. */
        const val RSS_PER_CHANNEL = 15
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
