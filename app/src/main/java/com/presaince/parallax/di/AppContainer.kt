/*
 * Parallax — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.parallax.di

import android.content.Context
import androidx.room.Room
import coil3.ImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import com.presaince.parallax.core.AppLog
import com.presaince.parallax.data.auth.PostingManager
import com.presaince.parallax.data.auth.PostingStore
import com.presaince.parallax.data.auth.YouTubeAuth
import com.presaince.parallax.data.db.ParallaxDb
import com.presaince.parallax.data.discover.DiscoverEngine
import com.presaince.parallax.data.discover.RecipeRepository
import com.presaince.parallax.data.library.ContentRepository
import com.presaince.parallax.data.resync.LlmClient
import com.presaince.parallax.data.resync.AiChaptersEngine
import com.presaince.parallax.data.resync.ResyncEngine
import com.presaince.parallax.data.resync.ResyncStore
import com.presaince.parallax.data.secure.SecurePrefs
import com.presaince.parallax.data.source.NewPipeSource
import com.presaince.parallax.data.source.LlmRepairPlanner
import com.presaince.parallax.data.source.initContentCountry
import com.presaince.parallax.data.source.readContentRegions
import com.presaince.parallax.data.source.writeContentRegions
import com.presaince.parallax.data.source.OkHttpDownloader
import com.presaince.parallax.data.source.VideoSource
import com.presaince.parallax.data.sponsorblock.SponsorBlock
import com.presaince.parallax.playback.PlaybackController
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

    private val prefs = appContext.getSharedPreferences("parallax", Context.MODE_PRIVATE)

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

    init {
        // The extractor needs a downloader before any service can be used.
        initContentCountry(http, readContentRegions(appContext).first(), log)
    }

    /**
     * The regions the app asks as. Re-initialising is the only way NewPipe changes the country,
     * so this is called when the setting changes, not on every read. The first entry is the main
     * region; search fans out over the rest.
     */
    fun setContentRegions(codes: List<String>) {
        writeContentRegions(appContext, codes)
        initContentCountry(http, readContentRegions(appContext).first(), log)
    }

    fun setContentRegion(code: String) = setContentRegions(listOf(code))

    val source: VideoSource by lazy {
        NewPipeSource(http, appContext, log, LlmRepairPlanner(LlmClient(http), ResyncStore(prefs, SecurePrefs(appContext)), log))
    }

    val sponsorBlock: SponsorBlock by lazy { SponsorBlock(http) }

    val db: ParallaxDb by lazy {
        Room.databaseBuilder(appContext, ParallaxDb::class.java, "parallax.db")
            .addMigrations(
                ParallaxDb.MIGRATION_5_6,
                ParallaxDb.MIGRATION_6_7,
                ParallaxDb.MIGRATION_7_8,
                ParallaxDb.MIGRATION_8_9,
                ParallaxDb.MIGRATION_9_10,
                ParallaxDb.MIGRATION_10_11,
            )
            // Real migrations only: the destructive fallback is gone. Schema v11 is exported and
            // committed; the next change needs a real migration plus a migration test.
            .build()
    }

    val content: ContentRepository by lazy { ContentRepository(source, db, log) }

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
        )
    }
}
