/*
 * Parallax — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.parallax.ui.nav

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.presaince.parallax.core.BugShot
import com.presaince.parallax.data.model.VideoSummary
import com.presaince.parallax.di.AppContainer
import com.presaince.parallax.feature.channel.ChannelScreen
import com.presaince.parallax.feature.comments.CommentsSection
import com.presaince.parallax.feature.discover.DiscoverScreen
import com.presaince.parallax.feature.feed.FeedScreen
import com.presaince.parallax.feature.library.LibraryScreen
import com.presaince.parallax.feature.player.PlayerScreen
import com.presaince.parallax.feature.posting.PostingScreen
import com.presaince.parallax.feature.search.SearchScreen
import com.presaince.parallax.feature.settings.BugScreen
import com.presaince.parallax.feature.settings.LogsScreen
import com.presaince.parallax.feature.settings.SettingsSectionId
import com.presaince.parallax.feature.settings.SettingsSectionScreen
import com.presaince.parallax.feature.settings.SettingsScreen
import com.presaince.parallax.feature.settings.title
import com.presaince.parallax.ui.component.BrandWordmark
import com.presaince.parallax.ui.component.MiniPlayer
import com.presaince.parallax.ui.component.ParallaxScaffold
import com.presaince.parallax.ui.component.TabItem
import com.presaince.parallax.ui.theme.ThemeBackdrop
import com.presaince.parallax.ui.theme.ThemeController
import android.app.Activity
import android.content.pm.ActivityInfo
import android.content.Context
import android.content.ContextWrapper

/**
 * Four top-level destinations, everything else pushed on a stack. When tabs and deep links are
 * worth a library, this is the file that gains navigation-compose, not every screen.
 */
sealed interface Route {
    data object Feed : Route
    data object Search : Route
    data object Library : Route
    data object Discover : Route
    data object Settings : Route
    data class SettingsPage(val section: SettingsSectionId) : Route
    data object Posting : Route
    data object Logs : Route
    data object Bug : Route
    data class Channel(val url: String, val name: String) : Route
    data class Player(val video: VideoSummary) : Route
}

private val tabRoutes = listOf(Route.Feed, Route.Library, Route.Discover)

private val tabs = listOf(
    TabItem("Feed", Icons.Default.Home),
    TabItem("Library", Icons.AutoMirrored.Filled.List),
    TabItem("Bored", Icons.Default.Star),
)

private fun titleOf(route: Route): String = when (route) {
    Route.Feed -> "Feed"
    Route.Search -> "Search"
    Route.Library -> "Library"
    Route.Discover -> "Bored"
    is Route.Settings -> "Settings"
    is Route.SettingsPage -> route.section.title
    is Route.Channel -> route.name
    // The title is already on the page under the video; repeating it in the bar was noise.
    is Route.Player -> ""
    Route.Posting -> "Posting"
    Route.Logs -> "Logs"
    Route.Bug -> "Bug report"
}

@Composable
fun ParallaxNav(container: AppContainer, theme: ThemeController) {
    var stack by remember { mutableStateOf(listOf<Route>(Route.Feed)) }
    val current = stack.last()
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }

    val tabIndex = tabRoutes.indexOfFirst { it == current }
    val isTab = tabIndex >= 0
    val isPlayer = current is Route.Player
    // A channel page is a library surface; it keeps the bar, with Library selected, so the app
    // does not lose its bottom navigation one level in.
    val isChannel = current is Route.Channel

    val playbackState by container.playback.state.collectAsStateWithLifecycle()
    val playingVideoId = playbackState.videoId

    // Full, swipe down to mini, swipe down again to super-min; swipe up to step back.
    // Remembered per video and cleared when the full player is opened, so a different video
    // always starts as a full card.
    var miniLevelFor by remember { mutableStateOf<Pair<String, Int>?>(null) }
    LaunchedEffect(isPlayer) {
        if (isPlayer) miniLevelFor = null
    }

    // Leaving the player restores portrait here, in the nav, not in the player's dispose: that
    // dispose runs on fullscreen toggles too, and forcing portrait there snapped the picture
    // back before the rotation could land.
    LaunchedEffect(isPlayer) {
        if (!isPlayer) {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }

    val push: (Route) -> Unit = { route -> stack = stack + route }
    val pop: () -> Unit = { if (stack.size > 1) stack = stack.dropLast(1) }

    // The video that is playing, as its full player. The mini card and the notification both use it.
    fun openPlayingVideo() {
        val playing = container.playback.state.value
        val id = playing.videoId ?: return
        if (stack.none { it is Route.Player }) {
            push(
                Route.Player(
                    VideoSummary(
                        id = id,
                        url = "https://www.youtube.com/watch?v=$id",
                        title = playing.videoTitle,
                        channelName = "",
                        durationSec = 0,
                        thumbnailUrl = null,
                    ),
                ),
            )
        }
    }

    val openRequested by container.playback.openPlayer.collectAsStateWithLifecycle()
    LaunchedEffect(openRequested) {
        if (openRequested) {
            container.playback.consumeOpenPlayer()
            openPlayingVideo()
        }
    }
    var historyRequest by remember { mutableIntStateOf(0) }
    // Tapping Feed opens the feed tab even when History is on: it switches the list, it does
    // not refresh the network. A refresh is the pull gesture's job.
    var showFeedSignal by remember { mutableIntStateOf(0) }

    BackHandler(enabled = stack.size > 1) { pop() }

    // Search belongs to the library tabs. On Bored Mode, Settings, a channel page or anywhere
    // else it was a second way to the same screen, and it read as part of that page.
    val showSearch = current == Route.Feed || current == Route.Library
    ParallaxScaffold(
        title = titleOf(current),
        showBack = !isTab,
        onBack = pop,
        tabs = if (isTab || isChannel) tabs else emptyList(),
        selectedTab = when {
            isTab -> tabIndex
            isChannel -> 1
            else -> 0
        },
        onSelectTab = { index ->
            if (index == 0) showFeedSignal += 1
            stack = listOf(tabRoutes[index])
        },
        onOpenSettings = { push(Route.Settings) },
        onOpenBug = {
            BugShot.capture(activity) { push(Route.Bug) }
        },
        onSearch = if (showSearch) ({ push(Route.Search) }) else null,
        showTopBar = !isPlayer,
        titleContent = if (current == Route.Feed) {
            { BrandWordmark() }
        } else {
            null
        },
        miniPlayer = if (playingVideoId != null && !isPlayer) {
            {
                MiniPlayer(
                    player = container.playback.player,
                    title = playbackState.videoTitle,
                    isPlaying = playbackState.isPlaying,
                    progress = if (playbackState.durationMs > 0) {
                        playbackState.positionMs.toFloat() / playbackState.durationMs
                    } else {
                        0f
                    },
                    onExpand = ::openPlayingVideo,
                    onClose = container.playback::stop,
                    onTogglePlay = container.playback::togglePlayPause,
                    // Three sizes on one gesture: full, swipe down to mini, swipe down again to
                    // super-min; swipe up to step back out.
                    level = if (miniLevelFor?.first == playingVideoId) miniLevelFor.second else 0,
                    onLevel = { next ->
                        miniLevelFor = playingVideoId to next
                    },
                )
            }
        } else {
            null
        },
    ) {
        // The pager is the floor of the app. Whatever is pushed on top (a player, a channel, a
        // settings page) is drawn over it rather than replacing it, so going back reveals a feed
        // that never left composition: no refetch, no scroll reset, no wait.
        val baseIndex = tabRoutes.indexOfFirst { it == stack.first() }
        Box(modifier = Modifier.fillMaxSize()) {
            if (baseIndex >= 0) {
                val pagerState = rememberPagerState(initialPage = baseIndex) { tabRoutes.size }

                // Only a settle decides where the tabs are. Keying this on currentPage meant it
                // fired mid-animation: tap Bored, the pager passes Library, the stack rewrote
                // itself to Library, and the animation was cancelled part-way across.
                LaunchedEffect(pagerState.settledPage) {
                    if (stack.size != 1) return@LaunchedEffect
                    val target = tabRoutes[pagerState.settledPage]
                    if (stack.first() != target) stack = listOf(target)
                }
                LaunchedEffect(baseIndex) {
                    // settledPage, not currentPage: currentPage flips before the page is actually
                    // in place, so skipping on it could leave an interrupted animation frozen
                    // part-way — the "Bored at eighty percent with Library still on the left" bug.
                    if (pagerState.settledPage != baseIndex) {
                        pagerState.animateScrollToPage(baseIndex)
                    }
                }

                // Swiping between destinations is the point; the bar stays for a reason to exist.
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    // A pushed screen covers the pager but the pager is still composed; letting a
                    // stray horizontal swipe reach it moved a page underneath and popped the
                    // screen the person was on. While anything is on top, the tabs do not swipe.
                    userScrollEnabled = isTab,
                ) { page ->
                    when (tabRoutes[page]) {
                        Route.Feed -> FeedScreen(
                            content = container.content,
                            db = container.db,
                            onOpenVideo = { push(Route.Player(it)) },
                            onOpenLibrary = { stack = listOf(Route.Library) },
                            openHistorySignal = historyRequest,
                            showFeedSignal = showFeedSignal,
                        )
                        Route.Library -> LibraryScreen(
                            db = container.db,
                            content = container.content,
                            onOpenVideo = { push(Route.Player(it)) },
                            onOpenChannel = { url, name -> push(Route.Channel(url, name)) },
                            onOpenHistory = {
                                historyRequest += 1
                                stack = listOf(Route.Feed)
                            },
                        )
                        Route.Discover -> DiscoverScreen(
                            recipes = container.recipes,
                            engine = container.discover,
                            onOpenVideo = { push(Route.Player(it)) },
                        )
                        else -> Unit
                    }
                }
            }

            if (!isTab) {
                Box(modifier = Modifier.fillMaxSize()) {
                    // The floor: a pushed screen must not let the pager underneath show through.
                    ThemeBackdrop(modifier = Modifier.fillMaxSize())
                    when (current) {
                        Route.Feed, Route.Library, Route.Discover -> Unit
                        Route.Search -> SearchScreen(
                            source = container.source,
                            db = container.db,
                            preferences = container.preferences,
                            onOpenVideo = { push(Route.Player(it)) },
                        )
                        Route.Settings -> SettingsScreen(
                            playback = container.playback,
                            resyncStore = container.resyncStore,
                            posting = container.posting,
                            theme = theme,
                            onOpenSection = { push(Route.SettingsPage(it)) },
                        )
                        is Route.SettingsPage -> SettingsSectionScreen(
                            section = current.section,
                            playback = container.playback,
                            resyncStore = container.resyncStore,
                            posting = container.posting,
                            db = container.db,
                            recipes = container.recipes,
                            theme = theme,
                            auth = container.youtubeAuth,
                            onRegionChange = { container.setContentRegions(it) },
                            onOpenLogs = { push(Route.Logs) },
                            onOpenVideo = { push(Route.Player(it)) },
                            log = container.log,
                        )
                        Route.Logs -> LogsScreen(log = container.log)
                        Route.Bug -> BugScreen(log = container.log)
                        Route.Posting -> PostingScreen(
                            manager = container.posting,
                            auth = container.youtubeAuth,
                        )
                        is Route.Channel -> ChannelScreen(
                            source = container.source,
                            channelUrl = current.url,
                            channelName = current.name,
                            onOpenVideo = { push(Route.Player(it)) },
                        )
                        is Route.Player -> PlayerScreen(
                            source = container.source,
                            content = container.content,
                            playback = container.playback,
                            db = container.db,
                            posting = container.posting,
                            resync = container.resync,
                            resyncStore = container.resyncStore,
                            aiChapters = container.aiChapters,
                            video = current.video,
                            log = container.log,
                            onBack = pop,
                            onOpenBug = { BugShot.capture(activity) { push(Route.Bug) } },
                            onOpenChannel = { url, name -> push(Route.Channel(url, name)) },
                            onOpenVideo = { push(Route.Player(it)) },
                        )
                    }
                }
            }
        }
    }
}

private fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}
