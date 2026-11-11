/*
 * GlassTube — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.glasstube.di

import android.content.Context
import androidx.room.Room
import coil3.ImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import com.presaince.glasstube.core.AppLog
import com.presaince.glasstube.core.ExitLog
import com.presaince.glasstube.data.auth.PostingManager
import com.presaince.glasstube.data.auth.PostingStore
import com.presaince.glasstube.data.auth.YouTubeAuth
import com.presaince.glasstube.data.db.GlassTubeDb
import com.presaince.glasstube.data.discover.DiscoverEngine
import com.presaince.glasstube.data.discover.RecipeRepository
import com.presaince.glasstube.data.library.ContentRepository
import com.presaince.glasstube.data.resync.LlmClient
import com.presaince.glasstube.data.resync.AiChaptersEngine
import com.presaince.glasstube.data.resync.ResyncEngine
import com.presaince.glasstube.data.resync.ResyncStore
import com.presaince.glasstube.data.secure.SecurePrefs
import com.presaince.glasstube.data.source.NewPipeSource
import com.presaince.glasstube.data.source.LlmRepairPlanner
import com.presaince.glasstube.data.source.RssChannelFeed
import com.presaince.glasstube.data.source.initContentCountry
import com.presaince.glasstube.data.source.resolvedRegions
import com.presaince.glasstube.data.source.writeContentRegions
import com.presaince.glasstube.data.source.OkHttpDownloader
import com.presaince.glasstube.data.source.VideoSource
import com.presaince.glasstube.data.sponsorblock.SponsorBlock
import com.presaince.glasstube.playback.PlaybackController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Manual injection. One container, created in Application.onCreate, no annotation processor.
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * The player's client. It shares [http]'s pool, DNS cache and threads, because a play should
     * not pay for a new handshake, but not its patience: the extractor fetches whole pages and can
     * afford to wait thirty seconds, while a stream that stopped delivering bytes has to be dropped
     * in seconds or the wait is the bug. Measured on the 2026-11-09 report: 30567 ms to the first
     * frame, which is the shared 30 s read timeout plus the retry that then succeeded silently.
     */
    private val playerHttp: OkHttpClient = http.newBuilder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    private val prefs = appContext.getSharedPreferences("glasstube", Context.MODE_PRIVATE)

    /** Exposed for screens that own a preference (search history, theme). */
    val preferences: android.content.SharedPreferences get() = prefs

    /** Thumbnails share the one OkHttp client, so they share its pool, timeouts and DNS cache. */
    val imageLoader: ImageLoader by lazy {
        ImageLoader.Builder(appContext)
            .components { add(OkHttpNetworkFetcherFactory(callFactory = { http })) }
            .build()
    }

    /** Readable from Settings; kept for 48 hours and never uploaded. */
    val log: AppLog = AppLog(appContext.filesDir)

    /** Why the previous process ended, from the OS. Read once per start, in Application.onCreate. */
    val exitLog: ExitLog by lazy { ExitLog(appContext, log, prefs) }

    init {
        // The extractor needs a downloader before any service can be used.
        initContentCountry(http, resolvedRegions(appContext).first(), log)
    }

    /**
     * The regions the app asks as. Re-initialising is the only way NewPipe changes the country,
     * so this is called when the setting changes, not on every read. The first entry is the main
     * region; search fans out over the rest.
     */
    fun setContentRegions(codes: List<String>) {
        writeContentRegions(appContext, codes)
        initContentCountry(http, resolvedRegions(appContext).first(), log)
    }

    fun setContentRegion(code: String) = setContentRegions(listOf(code))

    val source: VideoSource by lazy {
        NewPipeSource(http, appContext, log, LlmRepairPlanner(LlmClient(http), ResyncStore(prefs, SecurePrefs(appContext)), log))
    }

    val sponsorBlock: SponsorBlock by lazy { SponsorBlock(http) }

    val db: GlassTubeDb by lazy {
        Room.databaseBuilder(appContext, GlassTubeDb::class.java, "glasstube.db")
            .addMigrations(
                GlassTubeDb.MIGRATION_5_6,
                GlassTubeDb.MIGRATION_6_7,
                GlassTubeDb.MIGRATION_7_8,
                GlassTubeDb.MIGRATION_8_9,
                GlassTubeDb.MIGRATION_9_10,
                GlassTubeDb.MIGRATION_10_11,
                GlassTubeDb.MIGRATION_11_12,
                GlassTubeDb.MIGRATION_12_13,
            )
            // Real migrations only: the destructive fallback is gone. Schema v12 is exported and
            // committed; the next change needs a real migration plus a migration test.
            .build()
    }

    val content: ContentRepository by lazy {
        ContentRepository(source, db, log, playbackBusy = { playback.state.value.isBuffering })
            // Seeded from the stored setting, so the feed's first paint already obeys it.
            .also {
                it.setHideWatchedPercent(
                    prefs.getInt(ContentRepository.KEY_HIDE_WATCHED, 0),
                )
                it.setFeedLive(prefs.getBoolean("feed_live", true))
            }
    }

    val resyncStore: ResyncStore by lazy {
        ResyncStore(prefs, SecurePrefs(appContext))
    }

    val resync: ResyncEngine by lazy {
        ResyncEngine(source, db, LlmClient(http), resyncStore)
    }

    val aiChapters: AiChaptersEngine by lazy {
        AiChaptersEngine(source, db, LlmClient(http), resyncStore)
    }

    val recipes: RecipeRepository by lazy { RecipeRepository(appContext, db) }

    val discover: DiscoverEngine by lazy {
        DiscoverEngine(
            source,
            db,
            hideSubscribed = { prefs.getBoolean("bored_hide_subscribed", true) },
            // Free, keyless, no quota: the fresh page reads the channel feeds directly, and the
            // extractor is only the fallback for a handle URL the feed cannot name.
            channelFeed = RssChannelFeed(http, log),
        )
    }

    // Idle until the user turns posting on: YouTubeAuth holds an HTTP client and nothing else, so
    // no Google endpoint is contacted while the feature is off.
    val postingStore: PostingStore by lazy { PostingStore(prefs, SecurePrefs(appContext)) }

    val youtubeAuth: YouTubeAuth by lazy { YouTubeAuth(http) }

    val posting: PostingManager by lazy { PostingManager(http, postingStore, youtubeAuth) }

    val playback: PlaybackController by lazy {
        PlaybackController(
            appContext = appContext,
            sponsorBlock = sponsorBlock,
            prefs = prefs,
            db = db,
            log = log,
            // Not [http]: streaming wants a short fuse, the extractor wants a long one.
            http = playerHttp,
            // Invalidate first: a retry that reuses the cached StreamInfo hands back the same
            // rejected URL and fails for the same reason.
            refreshStreams = { videoId ->
                runCatching { source.invalidateStreams(videoId) }
                source.streams(videoId)
            },
        )
    }

    private val prefetchScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /**
     * Warms the shared StreamInfo fetch at tap time, so navigation and composition overlap the
     * request instead of waiting for them. Single-flight, so the coming PlayerViewModel load
     * joins the same job. Prefetching the whole screen would fan out per card and throttle the
     * network for the one video anyone actually taps.
     */
    fun prefetchStreams(videoId: String) {
        prefetchScope.launch {
            runCatching { source.streams(videoId) }
        }
    }
}
