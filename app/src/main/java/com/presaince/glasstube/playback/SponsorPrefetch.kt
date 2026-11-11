/*
 * GlassTube — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.glasstube.playback

import android.content.Context
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MediaSource
import com.presaince.glasstube.core.AppLog
import com.presaince.glasstube.data.model.StreamSet
import com.presaince.glasstube.data.sponsorblock.Segment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** How far ahead a block is noticed: a couple of minutes is enough to fetch its far side. */
internal const val SPONSOR_LEAD_MS = 120_000L

/** Shorter than a full forward buffer is not worth a second connection. */
internal const val SPONSOR_MIN_BLOCK_MS = 30_000L

/** The window fetched past the block. Enough for the first frame and the first seconds of play. */
internal const val SPONSOR_PREFETCH_MS = 15_000L

/** A prefetch that has not filled its window by now is on a bad connection; let it go. */
private const val SPONSOR_PREFETCH_CEILING_MS = 60_000L

/** A ceiling in bytes as well as in time, so a 4K stream cannot fill memory to warm 15 s. */
private const val SPONSOR_PREFETCH_BYTES = 12 * 1024 * 1024

private const val SPONSOR_PREFETCH_POLL_MS = 250L

/**
 * The far side of the next long block, if it is worth warming: still ahead, longer than the
 * player's own buffer holds, outside that buffer, inside the lead window, and not already done.
 * Pure, so the rule has a test without a player.
 */
internal fun sponsorPrefetchTarget(
    positionMs: Long,
    bufferedMs: Long,
    segments: List<Segment>,
    prefetchedEndMs: Long?,
): Long? = segments
    .asSequence()
    .filter { segment ->
        segment.startMs > positionMs &&
            segment.endMs - segment.startMs >= SPONSOR_MIN_BLOCK_MS &&
            segment.endMs > bufferedMs &&
            segment.endMs != prefetchedEndMs &&
            segment.startMs - positionMs <= SPONSOR_LEAD_MS
    }
    .minByOrNull { it.startMs }
    ?.endMs

/**
 * Warms the cache at the far side of a long SponsorBlock block before the skip happens.
 *
 * The skip is a seek, and a seek past the ~60 s the player holds is a network read the viewer
 * sees as a rebuffer — a five-minute block always lands outside it. This runs a throwaway player
 * on the same media source, which goes through the same [androidx.media3.datasource.cache.SimpleCache]
 * under the same key, with playback never started. It loads only the first seconds after the
 * block, then releases; the real seek then reads bytes already on disk.
 *
 * It is deliberately a second player rather than a [androidx.media3.datasource.cache.CacheWriter]:
 * the cache key and the muxed-or-split shape are the ones [PlaybackController.play] already built,
 * and a prefetch that has to re-derive the media's byte offsets would be a second, faster-drifting
 * source of truth.
 */
internal class SponsorPrefetch(
    private val context: Context,
    private val scope: CoroutineScope,
    private val log: AppLog,
    private val mediaSource: (String, StreamSet, Boolean) -> MediaSource,
) {

    private var job: Job? = null
    private var player: ExoPlayer? = null

    /** Starts once for one destination; a second call replaces whatever was warming. */
    fun start(videoId: String, streams: StreamSet, targetMs: Long, audioOnly: Boolean = false) {
        cancel()
        val prefetcher = ExoPlayer.Builder(context)
            .setLoadControl(
                DefaultLoadControl.Builder()
                    .setBufferDurationsMs(
                        2_000,
                        (SPONSOR_PREFETCH_MS + 10_000).toInt(),
                        1_000,
                        2_000,
                    )
                    .setTargetBufferBytes(SPONSOR_PREFETCH_BYTES)
                    // The time window is the one that matters; the byte ceiling is the guard.
                    .setPrioritizeTimeOverSizeThresholds(true)
                    .build(),
            )
            .build()
        player = prefetcher
        log.info("player", "sponsor prefetch start ${targetMs}ms for $videoId")
        job = scope.launch {
            try {
                prefetcher.setMediaSource(mediaSource(videoId, streams, audioOnly))
                prefetcher.playWhenReady = false
                // Seek before prepare: the load starts at the far side, not at zero.
                prefetcher.seekTo(targetMs)
                prefetcher.prepare()
                val deadline = System.currentTimeMillis() + SPONSOR_PREFETCH_CEILING_MS
                while (isActive) {
                    if (prefetcher.bufferedPosition >= targetMs + SPONSOR_PREFETCH_MS) break
                    if (prefetcher.playerError != null) break
                    if (System.currentTimeMillis() > deadline) break
                    delay(SPONSOR_PREFETCH_POLL_MS)
                }
                log.info(
                    "player",
                    "sponsor prefetch ${if (prefetcher.playerError == null) "done" else "failed"} " +
                        "at ${prefetcher.bufferedPosition}ms",
                )
            } finally {
                release()
            }
        }
    }

    /** The block was skipped, the video changed, or playback closed: nothing left to warm. */
    fun cancel() {
        job?.cancel()
        job = null
        release()
    }

    private fun release() {
        player?.release()
        player = null
    }
}
