/*
 * Parallax — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.parallax.playback

import androidx.media3.common.Player
import androidx.media3.datasource.okhttp.OkHttpDataSource
import com.presaince.parallax.data.model.StreamSet
import okhttp3.OkHttpClient
import org.junit.Test

class PlaybackGateTest {

    @Test
    fun `sponsor seek runs only in ready state`() {
        assert(shouldSkipSegment(Player.STATE_READY))
        assert(!shouldSkipSegment(Player.STATE_BUFFERING))
        assert(!shouldSkipSegment(Player.STATE_IDLE))
        assert(!shouldSkipSegment(Player.STATE_ENDED))
    }

    @Test
    fun `fast start buffer is balanced`() {
        // 1s to the first frame, 3s after a rebuffer, 30s minimum and 60s maximum. The memory
        // stops at a minute because the disk cache already holds the bytes behind the playhead.
        assert(PlaybackController.BUFFER_FOR_PLAYBACK_MS == 1_000)
        assert(PlaybackController.BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS == 3_000)
        // Min must stay at or above after-rebuffer, and max must allow growth.
        assert(PlaybackController.MIN_BUFFER_MS >= PlaybackController.BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS)
        assert(PlaybackController.MAX_BUFFER_MS >= PlaybackController.MIN_BUFFER_MS)
        assert(PlaybackController.MIN_BUFFER_MS >= 30_000)
        assert(PlaybackController.MAX_BUFFER_MS >= 60_000)
    }

    @Test
    fun `a short seek back is served from memory`() {
        // ExoPlayer keeps nothing behind the playhead by default, so seeking back two seconds
        // left the buffer and cost a network read. The tail has to exist, and it spends the same
        // byte budget as the forward buffer, so it stays below the minimum.
        assert(PlaybackController.BACK_BUFFER_MS >= 5_000)
        assert(PlaybackController.BACK_BUFFER_MS <= PlaybackController.MIN_BUFFER_MS)
    }

    @Test
    fun `same video and same streams reuse the warm player`() {
        val streams = StreamSet(muxedUrl = "https://example/abc")
        assert(canReuseLoadedMedia("abc", "abc", streamsKey(streams), streams, true, false))
    }

    @Test
    fun `different video never reuses the warm player`() {
        val streams = StreamSet(muxedUrl = "https://example/xyz")
        assert(!canReuseLoadedMedia("abc", "xyz", streamsKey(streams), streams, true, false))
    }

    @Test
    fun `quality change with new stream urls does not reuse the warm player`() {
        val loaded = StreamSet(muxedUrl = "https://example/low")
        val picked = StreamSet(muxedUrl = "https://example/high")
        assert(!canReuseLoadedMedia("abc", "abc", streamsKey(loaded), picked, true, false))
    }

    @Test
    fun `player error never reuses the warm player`() {
        val streams = StreamSet(muxedUrl = "https://example/abc")
        assert(!canReuseLoadedMedia("abc", "abc", streamsKey(streams), streams, true, true))
    }

    @Test
    fun `no media loaded never reuses the warm player`() {
        val streams = StreamSet(muxedUrl = "https://example/abc")
        assert(!canReuseLoadedMedia("abc", "abc", streamsKey(streams), streams, false, false))
    }

    @Test
    fun `player data source is okHttp so streams share the app connection pool`() {
        val created = playerDataSourceFactory(OkHttpClient()).createDataSource()
        assert(created is OkHttpDataSource)
    }

    @Test
    fun `same video and itag share a cache key across signed urls`() {
        val old = "https://rr2.googlevideo.com/videoplayback?expire=1&itag=247&n=AAA"
        val fresh = "https://rr2.googlevideo.com/videoplayback?expire=2&itag=247&n=BBB"
        assert(mediaCacheKey("abc", old) == mediaCacheKey("abc", fresh))
    }

    @Test
    fun `different itag or video gets a different cache key`() {
        val base = "https://rr2.googlevideo.com/videoplayback?expire=1&itag=247"
        assert(mediaCacheKey("abc", base) != mediaCacheKey("abc", base.replace("247", "248")))
        assert(mediaCacheKey("abc", base) != mediaCacheKey("xyz", base))
    }

    @Test
    fun `url without itag never shares a cache key`() {
        val a = "https://rr2.googlevideo.com/videoplayback?expire=1"
        val b = "https://rr2.googlevideo.com/videoplayback?expire=2"
        assert(mediaCacheKey("abc", a) != mediaCacheKey("abc", b))
    }

    @Test
    fun `cache limit converts megabytes to bytes`() {
        assert(streamCacheLimitBytes(100) == 100L * 1024 * 1024)
        assert(streamCacheLimitBytes(0) == 0L)
    }
}
