/*
 * GlassTube — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.glasstube.feature.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.media.AudioManager
import android.view.Window
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import android.os.SystemClock
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import com.presaince.glasstube.ui.component.LevelPill
import com.presaince.glasstube.ui.component.LevelReadout
import com.presaince.glasstube.ui.component.readoutLeft
import com.presaince.glasstube.ui.component.readoutTop
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import coil3.compose.AsyncImage
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import com.presaince.glasstube.data.resync.AiChaptersEngine
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.media3.ui.PlayerView
import com.presaince.glasstube.R
import com.presaince.glasstube.core.AppLog
import com.presaince.glasstube.core.LEVEL_BAND_TOP_DP
import com.presaince.glasstube.core.SourceError
import com.presaince.glasstube.core.activeTransport
import com.presaince.glasstube.core.formatDuration
import com.presaince.glasstube.core.lowerStop
import com.presaince.glasstube.core.panelShortSidePx
import com.presaince.glasstube.core.qualityKey
import com.presaince.glasstube.core.qualityStops
import com.presaince.glasstube.core.snapToLadder
import com.presaince.glasstube.core.timeAgo
import com.presaince.glasstube.data.auth.PostingManager
import com.presaince.glasstube.data.db.GlassTubeDb
import com.presaince.glasstube.data.library.ContentRepository
import com.presaince.glasstube.data.model.VideoSummary
import com.presaince.glasstube.data.resync.ResyncEngine
import com.presaince.glasstube.data.resync.ResyncStore
import com.presaince.glasstube.data.source.Thumbnails
import com.presaince.glasstube.data.source.VideoSource
import com.presaince.glasstube.feature.comments.CommentsViewModel
import com.presaince.glasstube.feature.feed.hiddenVideo
import com.presaince.glasstube.feature.comments.commentsItems
import com.presaince.glasstube.feature.notes.NotesTab
import com.presaince.glasstube.feature.player.gesture.SeekRate
import com.presaince.glasstube.feature.player.gesture.Zone
import com.presaince.glasstube.feature.player.gesture.playbackGestures
import com.presaince.glasstube.ui.component.CardStyle
import com.presaince.glasstube.ui.component.GlassTubeMark
import com.presaince.glasstube.ui.component.LinkifiedText
import com.presaince.glasstube.ui.component.SineRiver
import com.presaince.glasstube.ui.component.compactCount
import com.presaince.glasstube.ui.component.rememberTicks
import com.presaince.glasstube.ui.theme.faded
import com.presaince.glasstube.ui.theme.glassTubeFilterChipColors
import coil3.compose.AsyncImage
import com.presaince.glasstube.playback.PlaybackController
import com.presaince.glasstube.playback.PlaybackState
import com.presaince.glasstube.playback.STALL_RETRY_LIMIT
import kotlin.math.roundToInt
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.presaince.glasstube.ui.theme.accentTextColour

private const val CONTROLS_TIMEOUT_MS = 3_000L
private const val UNDO_VISIBLE_MS = 4_500L
private const val UNDO_MIN_MS = 2_000L

/** How many suggestions Up next shows at once; a reshuffle slides this window over the pool. */
private const val RELATED_WINDOW = 10

/**
 * The video page. Controls live on the video and hide themselves: one tap anywhere on the picture
 * shows or hides them, nothing else is on screen. The three tabs under the video are the only
 * chrome in portrait, and fullscreen drops even those.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun PlayerScreen(
    source: VideoSource,
    content: ContentRepository,
    playback: PlaybackController,
    db: GlassTubeDb,
    posting: PostingManager,
    resync: ResyncEngine,
    resyncStore: ResyncStore,
    aiChapters: AiChaptersEngine,
    video: VideoSummary,
    log: AppLog,
    onBack: () -> Unit,
    onOpenBug: () -> Unit,
    onOpenChannel: (url: String, name: String) -> Unit = { _, _ -> },
    onOpenVideo: (com.presaince.glasstube.data.model.VideoSummary) -> Unit = {},
    clipStartMs: Long? = null,
    clipEndMs: Long? = null,
) {
    val viewModel: PlayerViewModel = viewModel(
        key = video.id,
        factory = viewModelFactory {
            initializer { PlayerViewModel(source, playback, db, video, log, aiChapters) }
        },
    )
    val commentsViewModel: CommentsViewModel = viewModel(
        key = "comments-${video.id}",
        factory = viewModelFactory {
            initializer { CommentsViewModel(content, posting, resync, resyncStore, video.id) }
        },
    )

    val loadState by viewModel.state.collectAsStateWithLifecycle()
    val playbackState by playback.state.collectAsStateWithLifecycle()
    val commentsState by commentsViewModel.state.collectAsStateWithLifecycle()
    val watches = com.presaince.glasstube.ui.component.watchCounts(db)

    // The staged load order: video to its first frame, then chapters, then comments, then
    // "Up next". Each step starts only once the previous has settled; the picture never
    // waits on the words beneath it.
    LaunchedEffect(
        playbackState.firstFrameReady,
        loadState.detailReady,
        video.id,
    ) {
        if (playbackState.firstFrameReady && loadState.detailReady) {
            commentsViewModel.preloadFirstPage()
        }
    }
    val commentsReady = commentsState.firstPageLoaded
    val stage = playerStage(
        videoReady = playbackState.firstFrameReady,
        detailReady = loadState.detailReady,
        commentsReady = commentsReady,
    )

    // Reopening a video that was stopped reuses this view model. If the controller no longer owns
    // this video, play it again instead of sitting on a loaded title over a black frame.
    LaunchedEffect(playbackState.videoId) {
        viewModel.ensurePlaying(playbackState.videoId)
    }

    // The quiet retries are the controller's, not this screen's: they have to fire with the
    // activity gone and with the mini-player showing, and their budget belongs to the video.

    val context = LocalContext.current
    val cardStyle = remember(CardStyle.revision) { CardStyle.read(context) }
    val activity = remember {
        var current: Context? = context
        while (current is ContextWrapper) {
            if (current is Activity) return@remember current
            current = current.baseContext
        }
        null
    }
    val audio = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val ticks = rememberTicks()
    var seekDeltaMs by remember { mutableLongStateOf(0L) }
    var seekFromMs by remember { mutableLongStateOf(0L) }

    var hud by remember { mutableStateOf<Hud?>(null) }
    var scrubTargetMs by remember { mutableStateOf<Long?>(null) }
    // The share moment. Seeking runs live under the finger, but the share pill must not move
    // until the finger lifts: the timestamp is locked at the start of the gesture and released
    // on the exact seek at the end, so a scrub never rewrites what a quiet tap would have shared.
    var shareLockedMs by remember { mutableStateOf<Long?>(null) }
    var descriptionExpanded by remember { mutableStateOf(false) }
    var notesExpanded by remember { mutableStateOf(false) }
    var notesCount by remember { mutableIntStateOf(0) }
    var chaptersExpanded by remember { mutableStateOf(true) }
    var sponsorExpanded by remember { mutableStateOf(false) }
    // Row, plain list, or not drawn at all. Hidden exists because the description keeps its own
    // timestamps: a reader who wants the text version of a chapter list should not have to read
    // the same list twice.
    val chaptersStyle = remember {
        ChaptersStyle.of(
            context.getSharedPreferences("glasstube", Context.MODE_PRIVATE)
                .getInt("chapters_style", ChaptersStyle.Row.stored),
        )
    }
    // The page's section order, as the reader arranged it in Settings.
    val sectionOrder = remember {
        resolveSectionOrder(
            context.getSharedPreferences("glasstube", Context.MODE_PRIVATE)
                .getString("player_sections", null)
                .orEmpty()
                .split(",")
                .filter { it.isNotBlank() },
        )
    }
    val chapterTimestamps = remember {
        context.getSharedPreferences("glasstube", Context.MODE_PRIVATE)
            .getBoolean("chapter_timestamps", true)
    }
    // Section labels: left, in line with the text they name; or right, as a margin rail.
    val labelsRight = remember {
        context.getSharedPreferences("glasstube", Context.MODE_PRIVATE)
            .getBoolean("player_labels_right", false)
    }
    var dictateSignal by remember { mutableIntStateOf(0) }
    var dragDown by remember { mutableStateOf(0f) }
    var dragUp by remember { mutableStateOf(0f) }
    var dragStartedAt by remember { mutableLongStateOf(0L) }
    var volumeSteps by remember { mutableFloatStateOf(0f) }
    var brightnessLevel by remember { mutableFloatStateOf(0.5f) }
    var tapAt by remember { mutableStateOf<Offset?>(null) }
    // Used by the permanent-failure screen: hiding a video it cannot play is a database write.
    val hideScope = rememberCoroutineScope()
    var scrubStartMs by remember { mutableLongStateOf(0L) }
    // A seek that moved more than a moment gets a way back. Accidental bar taps are the reason.
    var seekUndo by remember { mutableStateOf<SeekUndo?>(null) }
    var barStartMs by remember { mutableStateOf<Long?>(null) }
    // Every tap that chooses a position is a seek worth taking back: chapters, SponsorBlock
    // rows, description timestamps and share-panel seeks all run through here, so the undo
    // chip covers them as well as the bar and the swipe.
    fun userSeek(targetMs: Long) {
        val from = playbackState.positionMs
        playback.seekTo(targetMs)
        if (kotlin.math.abs(targetMs - from) >= UNDO_MIN_MS) {
            seekUndo = SeekUndo(from, targetMs)
        }
    }
    // A shared link can carry a clip. While focused the video starts at the clip start
    // and holds at the clip end; the chip is the way out in both directions.
    var clipFocused by remember(video.id, clipStartMs, clipEndMs) {
        mutableStateOf(clipStartMs != null || clipEndMs != null)
    }
    var clipSeen by remember(video.id) { mutableStateOf(false) }
    LaunchedEffect(video.id, clipStartMs, clipFocused) {
        if (clipFocused && clipStartMs != null && !clipSeen) {
            clipSeen = true
            playback.seekTo(clipStartMs)
        }
    }
    LaunchedEffect(video.id, clipEndMs, clipFocused, playbackState.positionMs, playbackState.isPlaying) {
        if (clipFocused && clipEndMs != null && playbackState.isPlaying &&
            playbackState.positionMs >= clipEndMs
        ) {
            playback.togglePlayPause()
        }
    }
    LaunchedEffect(video.id, clipFocused, clipStartMs, clipEndMs) {
        if (clipFocused) {
            val s = clipStartMs?.let { formatDuration(it / 1000) }
            val e = clipEndMs?.let { formatDuration(it / 1000) }
            val label = when {
                s != null && e != null -> "Clip $s–$e"
                s != null -> "From $s"
                e != null -> "To $e"
                else -> "Clip"
            }
            android.widget.Toast.makeText(context, label, android.widget.Toast.LENGTH_LONG).show()
        }
    }
    var lastScrubSeekAt by remember { mutableLongStateOf(0L) }
    // How far the finger has travelled sideways in this gesture, as a fraction of the width.
    // The seek rate rises with it, so a short swipe is precise and a long one covers a long video.
    var scrubTravel by remember { mutableFloatStateOf(0f) }
    // The rate at the start of a swipe: seconds per 1% of the screen. Chosen in Settings.
    val seekBaseSeconds = remember {
        context.getSharedPreferences("glasstube", Context.MODE_PRIVATE)
            .getInt("seek_base_seconds", 5)
            .coerceIn(1, 30)
            .toFloat()
    }

    // How many times in a row this video has failed to load. The fourth attempt changes the
    // message, because at that point the retry is the thing being wasted.
    var retryCount by remember { mutableIntStateOf(0) }
    LaunchedEffect(loadState.error) {
        if (loadState.error != null) retryCount++
        else retryCount = 0
    }
    var dragZone by remember { mutableStateOf(Zone.Centre) }
    // A video opens clean: the picture only, no controls until the first tap.
    var controlsVisible by remember { mutableStateOf(false) }
    var speedHolding by remember { mutableStateOf(false) }
    var speedPanelOpen by remember { mutableStateOf(false) }
    var speedAnchor by remember { mutableStateOf(Offset.Zero) }
    var fullscreen by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Turning the phone in is a fullscreen in, turning it back is a fullscreen out. The
    // manifest already lists orientation in configChanges, so no recreation fights the flag.
    //
    // Leaving fullscreen while the phone is still held sideways is the one case the sensor
    // cannot answer: the exit would be undone by the same landscape it did not change. The flag
    // holds that decision until the phone is upright again, which is also the moment the sensor
    // is handed back so a later turn goes fullscreen again.
    var exitHeldSideways by remember { mutableStateOf(false) }
    LaunchedEffect(landscape) {
        when {
            !landscape -> {
                fullscreen = false
                exitHeldSideways = false
                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR
            }
            !exitHeldSideways -> fullscreen = true
        }
    }

    // Fullscreen means landscape plus no system bars, and both are restored on the way out.
    // Rotating the phone is treated as fullscreen too, so a turn and the button agree.
    val effectiveFullscreen = fullscreen || landscape
    DisposableEffect(effectiveFullscreen) {
        val window = activity?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        if (effectiveFullscreen) {
            controller?.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller?.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
        onDispose {
            window?.let { WindowCompat.getInsetsController(it, it.decorView) }
                ?.show(WindowInsetsCompat.Type.systemBars())
            // Orientation is restored by the nav when the player leaves (see GlassTubeNav).
            // Doing it here, in a dispose that runs on every fullscreen toggle, fought the
            // rotation and snapped the picture back to portrait.
        }
    }

    // The brightness swipe writes the window, and a window attribute outlives the screen: the
    // same single Activity drew the feed after the player and stayed at the swiped level, which
    // the shade reports as brightness locked by the top app. Hand it back on the way out.
    DisposableEffect(Unit) {
        onDispose { restoreSystemBrightness(activity?.window) }
    }

    // Controls fade out while playing, and stay while paused or while the speed panel is open.
    LaunchedEffect(controlsVisible, playbackState.isPlaying, speedPanelOpen) {
        if (controlsVisible && playbackState.isPlaying && !speedPanelOpen) {
            delay(CONTROLS_TIMEOUT_MS)
            controlsVisible = false
        }
    }

    // The undo offer is a moment, not a control: it leaves on its own.
    LaunchedEffect(seekUndo) {
        if (seekUndo != null) {
            delay(UNDO_VISIBLE_MS)
            seekUndo = null
        }
    }

    // The media notification's microphone: open the card and start listening. The note itself
    // still belongs to this player, so the request is handed to it rather than to a service.
    val notesRequester = remember { BringIntoViewRequester() }

    val progressInFullscreen = remember {
        context.getSharedPreferences("glasstube", Context.MODE_PRIVATE)
            .getBoolean("progress_in_fullscreen", true)
    }
    // The ladder stops at what this screen can show, and the connection decides which of the two
    // stops is in force: mobile data keeps its own, so a film on Wi-Fi does not raise it.
    val panelPx = remember { panelShortSidePx(context) }
    val qualityLadder = remember(panelPx) { qualityStops(panelPx) }
    val qualityTransport = remember { activeTransport(context) }
    var qualityHeight by remember(qualityTransport, panelPx) {
        mutableIntStateOf(
            snapToLadder(
                context.getSharedPreferences("glasstube", Context.MODE_PRIVATE)
                    .getInt(qualityKey(qualityTransport), 0),
                panelPx,
            ),
        )
    }
    // A quality change loads a different file, so the picture has to build a new buffer: there is
    // nothing "already buffered" to fall back on. Saying so beats a pill that reads like a stall
    // (`111445`).
    var switchingQuality by remember { mutableStateOf(false) }
    LaunchedEffect(playbackState.firstFrameReady) {
        if (playbackState.firstFrameReady) switchingQuality = false
    }
    // `110114`: a video that has spent both quiet retries is asking for a smaller picture. One
    // step down per video, written to the ladder in force, and said out loud — a silent quality
    // change is indistinguishable from the app getting worse.
    var degradedOnce by remember(video.id) { mutableStateOf(false) }
    LaunchedEffect(playbackState.recoveryAttempts, video.id) {
        if (!degradedOnce && playbackState.recoveryAttempts >= STALL_RETRY_LIMIT) {
            val lower = lowerStop(qualityHeight, qualityLadder)
            if (lower != null) {
                degradedOnce = true
                context.getSharedPreferences("glasstube", Context.MODE_PRIVATE)
                    .edit()
                    .putInt(qualityKey(qualityTransport), lower.height)
                    .apply()
                qualityHeight = lower.height
                switchingQuality = true
                Toast.makeText(
                    context,
                    "Kept stalling, so the picture dropped to ${lower.label}",
                    Toast.LENGTH_LONG,
                ).show()
                viewModel.reloadAt(playback.state.value.positionMs)
            }
        }
    }
    val chapters = remember(loadState.description) { parseChapters(loadState.description) }
    val shownChapters = if (chapters.isNotEmpty()) chapters else loadState.aiChapters
    // The description keeps its own timestamp lines. They were stripped once the chapter parser
    // found them, on the argument that the same list twice is noise — but the description is the
    // only place the times are selectable and copyable, and the chapter row can be hidden
    // (`chapters_style`). The timestamps stay; the duplication is the reader's to switch off.
    val chaptersHidden = chaptersStyle == ChaptersStyle.Hidden
    // Only the model fills a gap: its features are off unless the user enabled the endpoint and key.
    // It also waits for the first frame: the staged order is video, then chapters, and the model
    // request must not compete with the picture.
    LaunchedEffect(
        playbackState.firstFrameReady,
        chapters.isEmpty(),
        loadState.description.isNotEmpty(),
    ) {
        if (playbackState.firstFrameReady && chapters.isEmpty() && loadState.description.isNotEmpty()) {
            viewModel.loadAiChapters()
        }
    }

    val surface: @Composable (Modifier) -> Unit = { modifier ->
        Box(modifier = modifier.background(Color.Black), contentAlignment = Alignment.Center) {
            AndroidView(
                factory = { playerContext ->
                    (android.view.LayoutInflater.from(playerContext)
                        .inflate(R.layout.view_player, null) as PlayerView)
                        .apply {
                            player = playback.player
                            useController = false
                        }
                },
                modifier = Modifier.fillMaxSize(),
            )
            if (loadState.loading) {
                LoadingPreview(videoId = video.id, thumbnailUrl = video.thumbnailUrl)
            }
            // The pill states one fact: fetching streams, waiting for the first frame, rebuffering,
            // stalled, or broken. Past the two quiet retries it becomes the way back, because a
            // stall that ends in a word nobody can press reads as a frozen app.
            val status = playerStatus(
                loading = loadState.loading,
                hasError = playbackState.playerError != null,
                buffering = playbackState.isBuffering,
                firstFrameReady = playbackState.firstFrameReady,
                stalledForMs = playbackState.stalledForMs,
                // A phone with no network cannot be repaired, and calling it a broken stream
                // sends the reader looking for a fault in the app. The system's own answer
                // outranks the load state's, because a drop mid-playback never reaches it.
                networkDown = playbackState.networkDown ||
                    loadState.errorKind == SourceError.NetworkFailure,
            )
            val tappable = playbackState.recoveryAttempts >= STALL_RETRY_LIMIT &&
                (status == PlayerStatus.Stalled || status == PlayerStatus.Broken)
            val statusLine = if (switchingQuality) "Switching quality…" else when (status) {
                PlayerStatus.Offline -> "No connection — playing will retry"
                PlayerStatus.Broken ->
                    if (tappable) "Stream broken — tap to retry" else "Stream broken — retrying"
                PlayerStatus.Stalled ->
                    if (tappable) "Stalled — tap to retry" else "Stalled — retrying"
                PlayerStatus.Fetching -> "Fetching…"
                PlayerStatus.WaitingForFirstFrame -> "Loading video…"
                PlayerStatus.Buffering -> if (playbackState.bufferedPercent > 0) {
                    "Buffering ${playbackState.bufferedPercent}%"
                } else {
                    "Buffering…"
                }
                PlayerStatus.Hidden -> null
            }
            if (statusLine != null && loadState.error == null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        // Clear of the progress bar, which owns the bottom 30dp.
                        .padding(bottom = 40.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color(0xCC000000))
                        .then(
                            if (tappable) {
                                Modifier.clickable {
                                    playback.retryNow()
                                }
                            } else {
                                Modifier
                            },
                        )
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (tappable) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = Color.White,
                            )
                        } else {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                color = Color.White,
                                strokeWidth = 2.dp,
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = statusLine,
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White,
                        )
                    }
                }
            }
            hud?.let { HudPill(it) }
        }
    }

    // Full screen is the top of the ladder and normal at the top of the page is one step down.
    // The fixed lock matches the fullscreen button so a sensor lock cannot snap back.
    fun enterFullscreen() {
        exitHeldSideways = false
        fullscreen = true
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
    }

    fun exitFullscreen() {
        fullscreen = false
        // Held sideways: hold the decision and turn the picture upright. Upright: the sensor takes
        // over again, which is what makes turning the phone go back to fullscreen.
        exitHeldSideways = landscape
        activity?.requestedOrientation = if (landscape) {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        } else {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR
        }
    }

    val overlay: @Composable () -> Unit = {
        ControlsOverlay(
            visible = controlsVisible,
            state = playbackState,
            fullscreen = fullscreen,
            onBack = onBack,
            // Starting playback hides the controls at once: the tap said "play", and the
            // overlay has no reason to stay between the picture and the person watching it.
            onTogglePlay = {
                val starting = !playback.state.value.isPlaying
                playback.togglePlayPause()
                if (starting) controlsVisible = false
            },
            onOpenSpeedPopup = { anchor -> speedAnchor = anchor; speedPanelOpen = true },
            onSpeedAdjust = { speed, _ -> playback.setSpeed(speed) },
            onSpeedEnd = { hud = null },
            onSpeedHold = { speedHolding = it },
            onToggleFullscreen = {
                if (fullscreen) {
                    exitFullscreen()
                } else {
                    enterFullscreen()
                }
            },
            // The readout owns the middle of the picture while it is up, so the play disc
            // steps aside instead of sitting on top of the number.
            showPlay = hud == null,
            playAt = tapAt,
            qualityLabel = com.presaince.glasstube.core.qualityLabel(qualityHeight, playbackState.videoHeight),
            selectedQuality = qualityHeight,
            qualityConnection = if (qualityTransport == com.presaince.glasstube.core.Transport.Cellular) {
                "Mobile data"
            } else {
                "Wi-Fi"
            },
            qualityStops = qualityLadder,
            onPickQuality = { height ->
                context.getSharedPreferences("glasstube", Context.MODE_PRIVATE)
                    .edit()
                    .putInt(qualityKey(qualityTransport), height)
                    .apply()
                qualityHeight = height
                switchingQuality = true
                // The stream is chosen when it loads, so the same video is loaded again from here.
                viewModel.reloadAt(playback.state.value.positionMs)
            },
            speedPresets = playback.speedPresets(),
        )
        if (speedPanelOpen) {
            SpeedPanel(
                speed = playbackState.speed,
                presets = playback.speedPresets(),
                onSpeed = playback::setSpeed,
                onDismiss = { speedPanelOpen = false },
                anchor = speedAnchor,
            )
        }
    }

    val gestures: Modifier = Modifier.playbackGestures(
        // A press on the speed dial, or an open speed panel, owns the gesture. Restarting the
        // picture detector disabled is what stops the same drag from also being a brightness swipe.
        enabled = !speedHolding && !speedPanelOpen,
        onVerticalStart = { zone, _ ->
            dragDown = 0f
            dragUp = 0f
            dragZone = zone
            dragStartedAt = SystemClock.uptimeMillis()
            volumeSteps = rawVolume(audio)
            brightnessLevel = brightnessFraction(activity)
            // Nothing is shown until the first move: a drag that turns out to be the ladder
            // must not flash a level readout on the way.
            hud = null
        },
        onVerticalDelta = { zone, fraction, finger, reach ->
            when (zone) {
                Zone.Left -> {
                    dragZone = Zone.Left
                    val before = volumeSteps.roundToInt()
                    val top = maxVolume(audio)
                    // The magnet: the last 12dp of the band is the end, so a finger that started
                    // high up can still finish the range instead of running out of screen.
                    val next = when {
                        reach > 0 -> top.toFloat()
                        reach < 0 -> 0f
                        else -> volumeSteps + fraction * LevelTravel * top
                    }
                    // Accumulate in steps, not in whole steps per event: rounding every delta to
                    // an Int lost most of a slow swipe, which is why the slider felt stuck.
                    volumeSteps = next.coerceIn(0f, top.toFloat())
                    val after = volumeSteps.roundToInt()
                    ticks.clamp(before.toFloat(), after.toFloat(), 0f, top.toFloat())
                    audio.setStreamVolume(
                        AudioManager.STREAM_MUSIC,
                        after,
                        0,
                    )
                    // Every move, not only a change: the readout travels with the finger, and
                    // a level pinned at an end still has a place to be.
                    hud = Hud(
                        fraction = volumeFraction(audio),
                        icon = R.drawable.ic_volume,
                        anchor = finger,
                    )
                }
                Zone.Right -> {
                    dragZone = Zone.Right
                    // A level is settable whether or not the picture is moving: a paused video
                    // still has a brightness to choose, and the readout has to answer the drag.
                    val before = brightnessLevel
                    brightnessLevel = when {
                        reach > 0 -> 1f
                        reach < 0 -> 0.01f
                        else -> brightnessLevel + fraction * LevelTravel
                    }.coerceIn(0.01f, 1f)
                    ticks.clamp(before, brightnessLevel, 0.01f, 1f)
                    if (kotlin.math.abs(brightnessLevel - before) > 0.001f) {
                        activity?.window?.let { window ->
                            window.attributes = window.attributes.apply {
                                screenBrightness = brightnessLevel
                            }
                        }
                    }
                    hud = Hud(
                        fraction = brightnessLevel,
                        icon = R.drawable.ic_sun,
                        anchor = finger,
                    )
                }
                // Centre vertical is the ladder gesture. Down follows the finger and a qualifying
                // swipe steps down one level, up steps back one level. No animation on release.
                else -> {
                    dragZone = Zone.Centre
                    // The full finger travel, clamped only at the ends. The surface translation
                    // reads this directly, so what the picture does matches what the finger did.
                    dragDown = (dragDown + -fraction).coerceIn(0f, 1f)
                    dragUp = (dragUp + fraction).coerceIn(0f, 1f)
                }
            }
        },
        onVerticalEnd = {
            hud = null
            val centre = dragZone != Zone.Left && dragZone != Zone.Right
            val elapsed = SystemClock.uptimeMillis() - dragStartedAt
            // A flick counts even when it is short; a slow drag has to travel to be meant.
            val flungDown = dragDown > 0.03f && elapsed < 260
            val flungUp = dragUp > 0.03f && elapsed < 260
            when {
                centre && (dragDown > 0.22f || flungDown) -> {
                    // One step down: full screen to normal, normal to minimized.
                    dragDown = 0f
                    dragUp = 0f
                    if (effectiveFullscreen) exitFullscreen() else onBack()
                }
                centre && !effectiveFullscreen && (dragUp > 0.22f || flungUp) -> {
                    // One step back up: normal to full screen. At full screen an up swipe stays.
                    dragDown = 0f
                    dragUp = 0f
                    enterFullscreen()
                }
                else -> {
                    dragDown = 0f
                    dragUp = 0f
                }
            }
        },
        onHorizontalStart = { yFraction ->
            shareLockedMs = playbackState.positionMs
            scrubStartMs = playbackState.positionMs
            scrubTargetMs = playbackState.positionMs
            seekDeltaMs = 0L
            seekFromMs = playbackState.positionMs
            scrubTravel = 0f
            hud = Hud(
                label = formatSeekDelta(0),
                secondary = formatDuration(playbackState.positionMs / 1000),
                progress = scrubProgress(playbackState.positionMs, playbackState.durationMs),
                anchor = Offset(0.5f, yFraction),
            )
        },
        onHorizontalDelta = { fraction, yFraction ->
            // A short swipe moves in seconds, not in percentages of the whole video: the first
            // tenth of the screen is worth the chosen base (5 s by default), and the rate climbs
            // as the finger travels, so the far end of a two-hour video is still a few swipes away.
            // Shared with the gesture lesson; the first tenth is exactly the Settings base.
            val movedMs = SeekRate.movedMs(fraction, scrubTravel, seekBaseSeconds)
            scrubTravel += kotlin.math.abs(fraction)
            val target = (scrubTargetMs ?: playbackState.positionMs) + movedMs
            scrubTargetMs = target.coerceIn(0, playbackState.durationMs)
            val settled = scrubTargetMs ?: 0L
            // The cancel snap when the displacement is back at zero, and a light tick for a
            // chapter boundary crossed on the way. Positions are carried between events, so
            // both are spaced by value, not by time.
            val delta = settled - scrubStartMs
            ticks.cancelSnap(seekDeltaMs, delta)
            ticks.milestone(seekFromMs, settled, shownChapters.map { it.atSec * 1000 })
            seekDeltaMs = delta
            seekFromMs = settled
            // Seek while the finger is still down: the picture follows the pill instead of
            // waiting for the release. Throttled so a jittery finger does not spam the player.
            val now = SystemClock.uptimeMillis()
            if (now - lastScrubSeekAt > 90) {
                playback.seekTo(settled)
                lastScrubSeekAt = now
            }
            hud = Hud(
                // The distance is the question ("how far am I going?"), the target is the answer.
                label = formatSeekDelta(settled - scrubStartMs),
                secondary = formatDuration(settled / 1000),
                progress = scrubProgress(settled, playbackState.durationMs),
                anchor = Offset(0.5f, yFraction),
            )
        },
        onHorizontalEnd = {
            // One last exact seek for where the finger actually ended up.
            val target = scrubTargetMs
            target?.let { playback.seekTo(it) }
            target?.let { after ->
                if (kotlin.math.abs(after - scrubStartMs) >= UNDO_MIN_MS) {
                    seekUndo = SeekUndo(scrubStartMs, after)
                }
            }
            shareLockedMs = target
            scrubTargetMs = null
            scrubTravel = 0f
            hud = null
        },
        onHorizontalCancel = {
            // Finger left the video: abandon the scrub, no seek, no readout.
            shareLockedMs = scrubStartMs
            scrubTargetMs = null
            scrubTravel = 0f
            hud = null
        },
        // A slide on the bar itself: the playhead goes under the finger, no pill in the way.
        onBarDrag = { fraction ->
            val duration = playbackState.durationMs
            if (duration > 0L) {
                if (barStartMs == null) {
                    barStartMs = playbackState.positionMs
                    shareLockedMs = playbackState.positionMs
                    seekDeltaMs = 0L
                    seekFromMs = playbackState.positionMs
                }
                scrubTargetMs = (fraction * duration).toLong()
                val settled = scrubTargetMs ?: 0L
                val delta = settled - (barStartMs ?: playbackState.positionMs)
                ticks.cancelSnap(seekDeltaMs, delta)
                ticks.milestone(seekFromMs, settled, shownChapters.map { it.atSec * 1000 })
                seekDeltaMs = delta
                seekFromMs = settled
                playback.seekTo(settled)
            }
        },
        onBarEnd = {
            val target = scrubTargetMs
            target?.let { playback.seekTo(it) }
            val from = barStartMs
            if (target != null && from != null &&
                kotlin.math.abs(target - from) >= UNDO_MIN_MS
            ) {
                seekUndo = SeekUndo(from, target)
            }
            shareLockedMs = target ?: shareLockedMs
            scrubTargetMs = null
            barStartMs = null
        },
        // A tap is for the controls. Play/pause has its own button, which is how every video
        // player behaves; tapping to pause also meant you could never reveal the controls.
        // Without a double tap there is nothing to wait for, so this is immediate.
        onTap = { at ->
            // A tap that hides the controls must not move the play disc on its way out: the
            // disc re-placing itself under the finger that asked for a hide reads as a second
            // button appearing. Only a tap that reveals the controls chooses where they go.
            if (controlsVisible) {
                controlsVisible = false
            } else {
                tapAt = at
                controlsVisible = true
            }
        },
    )

    val progressFraction = if (playbackState.durationMs > 0) {
        (playbackState.positionMs.toFloat() / playbackState.durationMs).coerceIn(0f, 1f)
    } else {
        0f
    }
    // The bar fades with the controls, and is never up beside the seek pill or the undo offer.
    val showProgress = progressBarVisible(
        controlsVisible = controlsVisible,
        barDragging = barStartMs != null,
        seekPillVisible = hud?.progress != null,
    )
    val progressLine: @Composable () -> Unit = {
        val duration = playbackState.durationMs
        val segments = playbackState.segments
        val primary = MaterialTheme.colorScheme.primary
        // The bar is the picture's bottom edge, with the elapsed time on its left and the length
        // on its right. `111214` asked for it back at the bottom after it spent a day at the top,
        // and `112240` asked for the bar *between* the two times rather than a "0:42 / 12:07"
        // cell with the track somewhere else. Times at 12sp in a fixed 48dp column so the track
        // does not jump as the seconds grow a digit.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(30.dp)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = formatDuration(playbackState.positionMs / 1000),
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
                textAlign = TextAlign.Start,
                modifier = Modifier.width(48.dp),
            )
            BoxWithConstraints(
                modifier = Modifier.weight(1f).fillMaxHeight(),
            ) {
                val barWidth = maxWidth
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .fillMaxWidth()
                        .height(6.dp)
                        .background(Color(0x44FFFFFF)),
                ) {
                    androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                        if (duration > 0L) {
                            // Two layers that never share pixels: sponsors on top half, playhead
                            // on bottom half. Overlap is the 50/50 split by construction.
                            val half = size.height / 2f
                            for (segment in segments) {
                                val start = (segment.startMs.toFloat() / duration).coerceIn(0f, 1f)
                                val end = (segment.endMs.toFloat() / duration).coerceIn(0f, 1f)
                                drawRect(
                                    color = segmentColor(segment.category),
                                    topLeft = androidx.compose.ui.geometry.Offset(
                                        start * size.width,
                                        0f,
                                    ),
                                    size = androidx.compose.ui.geometry.Size(
                                        (end - start) * size.width,
                                        half,
                                    ),
                                )
                            }
                            drawRect(
                                color = primary,
                                topLeft = androidx.compose.ui.geometry.Offset(0f, half),
                                size = androidx.compose.ui.geometry.Size(
                                    progressFraction * size.width,
                                    half,
                                ),
                            )
                            if (clipFocused && (clipStartMs != null || clipEndMs != null)) {
                                val cs = ((clipStartMs ?: 0L).toFloat() / duration)
                                    .coerceIn(0f, 1f)
                                val ce = ((clipEndMs ?: duration).toFloat() / duration)
                                    .coerceIn(0f, 1f)
                                val dim = Color.Black.copy(alpha = 0.45f)
                                drawRect(
                                    dim,
                                    topLeft = androidx.compose.ui.geometry.Offset(0f, 0f),
                                    size = androidx.compose.ui.geometry.Size(
                                        cs * size.width,
                                        size.height,
                                    ),
                                )
                                drawRect(
                                    dim,
                                    topLeft = androidx.compose.ui.geometry.Offset(
                                        ce * size.width,
                                        0f,
                                    ),
                                    size = androidx.compose.ui.geometry.Size(
                                        (1f - ce) * size.width,
                                        size.height,
                                    ),
                                )
                                val bar = primary.copy(alpha = 0.9f)
                                drawRect(
                                    bar,
                                    topLeft = androidx.compose.ui.geometry.Offset(
                                        cs * size.width - 1f,
                                        0f,
                                    ),
                                    size = androidx.compose.ui.geometry.Size(2f, size.height),
                                )
                                drawRect(
                                    bar,
                                    topLeft = androidx.compose.ui.geometry.Offset(
                                        ce * size.width - 1f,
                                        0f,
                                    ),
                                    size = androidx.compose.ui.geometry.Size(2f, size.height),
                                )
                            }
                        }
                    }
                }
                if (duration > 0L) {
                    // A dot, the shape every player uses for the playhead. The tube-with-a-play-
                    // mark it replaced read as decoration, and a control nobody recognises is not
                    // a control.
                    val handleSize = 14.dp
                    val dotX = (maxWidth * progressFraction - handleSize / 2)
                        .coerceIn(0.dp, (maxWidth - handleSize).coerceAtLeast(0.dp))
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .offset(x = dotX)
                            .size(handleSize)
                            .clip(CircleShape)
                            .background(primary),
                    )
                }
            }
            Text(
                text = formatDuration(duration / 1000),
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
                textAlign = TextAlign.End,
                modifier = Modifier.width(48.dp),
            )
        }
    }

    val lastSkip = playbackState.lastSkip
    val currentSegment = playbackState.segments.firstOrNull { it.contains(playbackState.positionMs) }
    LaunchedEffect(lastSkip) {
        if (lastSkip != null) {
            kotlinx.coroutines.delay(3000)
            playback.clearLastSkip()
        }
    }

    if (effectiveFullscreen) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            // The minimise drag: the surface follows the finger down and shrinks; release closes
            // it at once or leaves it where it is, with no animation either way. The detector sits
            // on the outer box, which never moves, so the finger's travel is read in screen space.
            // Transforming the node that owned the pointer warped the deltas and froze the drag.
            val travelPx = with(androidx.compose.ui.platform.LocalDensity.current) { maxHeight.toPx() }
            val followScale = (1f - 0.35f * dragDown).coerceAtLeast(0.55f)
            Box(modifier = Modifier.fillMaxSize().then(gestures)) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            translationY = travelPx * dragDown
                            scaleX = followScale
                            scaleY = followScale
                            shape = RoundedCornerShape((24 * dragDown).dp)
                            clip = true
                        },
                ) {
                    surface(Modifier.fillMaxSize())
                    overlay()
                    lastSkip?.let { seg ->
                        SponsorChip(
                            text = "Skipped ${com.presaince.glasstube.data.sponsorblock.SponsorCategories.label(seg.category)} · ${formatDuration(seg.startMs / 1000)} → ${formatDuration(seg.endMs / 1000)}",
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = 64.dp, end = 12.dp),
                        )
                    }
                    currentSegment?.let { seg ->
                        if (lastSkip == null) {
                            SponsorChip(
                                text = "${com.presaince.glasstube.data.sponsorblock.SponsorCategories.label(seg.category)} ends at ${formatDuration(seg.endMs / 1000)}",
                                onClick = { userSeek(seg.endMs) },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(top = 64.dp, end = 12.dp),
                            )
                        }
                    }
                    seekUndo?.let { undo ->
                        SeekUndoChip(
                            undo = undo,
                            onUndo = {
                                playback.seekTo(undo.beforeMs)
                                seekUndo = null
                            },
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 44.dp),
                        )
                    }
                    if (progressInFullscreen) {
                        FadingProgressBar(
                            visible = showProgress,
                            modifier = Modifier.align(Alignment.BottomCenter),
                        ) { progressLine() }
                    }
                }
            }
        }
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
            val travelPx = with(androidx.compose.ui.platform.LocalDensity.current) { maxHeight.toPx() }
            val followScale = (1f - 0.35f * dragDown).coerceAtLeast(0.55f)
            Box(modifier = Modifier.fillMaxSize().then(gestures)) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            translationY = travelPx * dragDown
                            scaleX = followScale
                            scaleY = followScale
                            shape = RoundedCornerShape((24 * dragDown).dp)
                            clip = true
                        },
                ) {
                surface(Modifier.fillMaxSize())
                overlay()
                lastSkip?.let { seg ->
                    SponsorChip(
                        text = "Skipped ${com.presaince.glasstube.data.sponsorblock.SponsorCategories.label(seg.category)} · ${formatDuration(seg.startMs / 1000)} → ${formatDuration(seg.endMs / 1000)}",
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 64.dp, end = 12.dp),
                    )
                }
                currentSegment?.let { seg ->
                    if (lastSkip == null) {
                        SponsorChip(
                            text = "${com.presaince.glasstube.data.sponsorblock.SponsorCategories.label(seg.category)} ends at ${formatDuration(seg.endMs / 1000)}",
                            onClick = { userSeek(seg.endMs) },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = 64.dp, end = 12.dp),
                        )
                    }
                }
                if (clipFocused) {
                    val s = clipStartMs?.let { formatDuration(it / 1000) }
                    val e = clipEndMs?.let { formatDuration(it / 1000) }
                    val label = when {
                        s != null && e != null -> "Clip $s–$e"
                        s != null -> "From $s"
                        e != null -> "To $e"
                        else -> "Clip"
                    }
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 64.dp)
                            .clip(RoundedCornerShape(50))
                            .background(Color(0xE6101014))
                            .padding(start = 14.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(label, style = MaterialTheme.typography.labelMedium, color = Color.White)
                        TextButton(onClick = { clipFocused = false; userSeek(0) }) {
                            Text("Before", color = accentTextColour())
                        }
                        TextButton(onClick = {
                            clipFocused = false
                            // "Keep going" means carry on watching: if the clip took the picture
                            // out of the frame and the player is paused, start it.
                            if (!playbackState.isPlaying) playback.togglePlayPause()
                        }) {
                            Text("Keep going", color = accentTextColour())
                        }
                    }
                }
                seekUndo?.let { undo ->
                    SeekUndoChip(
                        undo = undo,
                        onUndo = {
                            playback.seekTo(undo.beforeMs)
                            seekUndo = null
                        },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 44.dp),
                    )
                }
                FadingProgressBar(
                    visible = showProgress,
                    modifier = Modifier.align(Alignment.BottomCenter),
                ) { progressLine() }
                // Video failures (members-only, private, and the rest) get the whole black frame,
                // because that is what the failure is sitting on. A permanent failure says its own
                // sentence and offers no Retry: pressing it again cannot change the answer, and a
                // button that only ever fails is the thing that reads as broken.
                loadState.error?.let { message ->
                    val kind = loadState.errorKind
                    val permanent = kind?.permanent == true
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xCC000000)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(24.dp),
                        ) {
                            Text(
                                text = if (permanent) message else if (retryCount > 3) {
                                    "Still failing. Check your connection, then try again."
                                } else {
                                    message
                                },
                                style = MaterialTheme.typography.titleLarge,
                                color = Color.White,
                                textAlign = TextAlign.Center,
                            )
                            if (permanent) {
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    text = "Open another video from the feed.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White.copy(alpha = 0.75f),
                                    textAlign = TextAlign.Center,
                                )
                                Spacer(Modifier.height(8.dp))
                                // A permanent failure is the one thing this screen can act on:
                                // the feed offered something that cannot play, so stop offering
                                // it. It lands in Hidden with the reason, and can come back.
                                TextButton(onClick = {
                                    val reason = when (loadState.errorKind) {
                                        SourceError.MembersOnly -> "members only"
                                        else -> "private or removed"
                                    }
                                    hideScope.launch {
                                        runCatching {
                                            db.hidden().hide(hiddenVideo(video, reason))
                                        }
                                        onBack()
                                    }
                                }) {
                                    Text("Hide this video", color = Color.White)
                                }
                            } else {
                                Spacer(Modifier.height(16.dp))
                                TextButton(onClick = {
                                    retryCount = 0
                                    viewModel.retry()
                                }) {
                                    Text("Retry", color = Color.White)
                                }
                            }
                        }
                    }
                }
            }
            }
        }

        // End screen: related videos when the playhead reaches the end, behind a Settings
        // switch. Loads once via the source boundary; never more than one call per video.
        val endEnabled = remember {
            context.getSharedPreferences("glasstube", Context.MODE_PRIVATE)
                .getBoolean("end_related_enabled", true)
        }
        var relatedToShow by remember(video.id) {
            mutableStateOf<List<com.presaince.glasstube.data.model.VideoSummary>>(emptyList())
        }
        // What the source last answered: -1 until the first fetch, then 1. Kept as a plain flag so
        // the automatic fetch runs once per video and a manual refresh is never swallowed by it.
        var relatedAnswered by remember(video.id) { mutableIntStateOf(-1) }
        // The first stage of the load order reaches Up next once, so the list is there to read
        // whenever the reader scrolls to it, not only in the seconds after the video ends.
        LaunchedEffect(stage, endEnabled, video.id) {
            if (!endEnabled) return@LaunchedEffect
            if (stage != PlayerLoadStage.UpNext) return@LaunchedEffect
            if (relatedAnswered >= 0) return@LaunchedEffect
            when (val result = source.related(video.id)) {
                is com.presaince.glasstube.core.AppResult.Ok -> relatedToShow = result.value.take(RELATED_WINDOW)
                // A failed first fetch keeps whatever is there: an empty Up next reads as "nothing
                // follows this video", which is a worse lie than a stale row.
                is com.presaince.glasstube.core.AppResult.Err -> Unit
            }
            relatedAnswered = 1
        }

        val relatedScope = rememberCoroutineScope()
        var relatedRefreshing by remember(video.id) { mutableStateOf(false) }
        var relatedNote by remember(video.id) { mutableStateOf<String?>(null) }
        var relatedExpanded by remember(video.id) { mutableStateOf(true) }

        /**
         * The button refetches and reshuffles. The first version rotated a window over a cached
         * pool, so a small pool looped the same videos and the cache made the tap instant. Now the
         * cache is bypassed and the order is shuffled, so each tap is a real fetch and a visibly
         * different list.
         */
        fun refreshRelated() {
            if (relatedRefreshing) return
            relatedRefreshing = true
            relatedNote = null
            relatedScope.launch {
                when (val result = source.relatedFresh(video.id)) {
                    is com.presaince.glasstube.core.AppResult.Ok -> {
                        val fresh = result.value.distinctBy { it.id }
                        if (fresh.isEmpty()) {
                            relatedNote = "This video has no suggestions."
                        } else {
                            relatedToShow = fresh.shuffled().take(RELATED_WINDOW)
                            if (fresh.size <= RELATED_WINDOW) {
                                relatedNote = "Only ${fresh.size} suggestions exist, so this is all of them."
                            }
                        }
                    }
                    is com.presaince.glasstube.core.AppResult.Err ->
                        relatedNote = result.message
                }
                relatedRefreshing = false
            }
        }

        LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
            item(key = "titles") {
                // Advanced tint: the thumbnail's average colour washes behind the words when the
                // toggle is on. Sampling happens once per video on IO, never on the draw pass.
                val tintOn = remember {
                    context.getSharedPreferences("glasstube", Context.MODE_PRIVATE)
                        .getBoolean("theme_from_video", false)
                }
                var videoHue by remember(video.id) { mutableStateOf<Color?>(null) }
                LaunchedEffect(video.id, tintOn) {
                    if (!tintOn) return@LaunchedEffect
                    val url = Thumbnails.forVideo(video.id, video.thumbnailUrl)
                    val bitmap = runCatching {
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            coil3.ImageLoader(context).execute(
                                coil3.request.ImageRequest.Builder(context)
                                    .data(url)
                                    .size(48)
                                    .build(),
                            ).image
                        }
                    }.getOrNull() as? android.graphics.Bitmap
                    if (bitmap != null) {
                        videoHue = averageColor(bitmap)
                    }
                }
                val tint = videoHue?.let { it.copy(alpha = 0.12f) }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (tint != null) Modifier.background(tint, RoundedCornerShape(12.dp)) else Modifier)
                        .padding(horizontal = 16.dp)
                        .padding(top = 6.dp),
                ) {
                    if (cardStyle.title) {
                        // Two lines, and a tap opens the rest. No chevron: the ellipsis is the
                        // affordance, and a title that fits is not a target. The tap only exists
                        // when the collapsed text was actually cut off, so a short title never
                        // swallows a press that was meant for the picture above.
                        var titleOpen by remember(video.id) { mutableStateOf(false) }
                        var titleCut by remember(video.id) { mutableStateOf(false) }
                        Text(
                            text = loadState.title,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = if (titleOpen) Int.MAX_VALUE else 2,
                            overflow = TextOverflow.Ellipsis,
                            onTextLayout = { layout ->
                                if (!titleOpen) titleCut = layout.hasVisualOverflow
                            },
                            modifier = Modifier.then(
                                if (titleCut || titleOpen) {
                                    Modifier.clickable { titleOpen = !titleOpen }
                                } else {
                                    Modifier
                                },
                            ),
                        )
                    }
                    // The channel is a door, not a caption: the name opens its page. The facts
                    // sit on the right, so the row stays one line under the title.
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                    if (cardStyle.channel || buildList {
                            (loadState.views ?: video.viewCount)?.let { if (cardStyle.views) add(it) }
                            (loadState.likes ?: video.likeCount)?.let { if (cardStyle.likes) add(it) }
                            if (cardStyle.age) video.publishedAtMs?.let { add(it) }
                            if (cardStyle.watches) watches[video.id]?.let { add(it) }
                        }.isNotEmpty()) {
                        // The detail's uploader URL lands after the first load; when a listing
                        // carried none, a subscription of the same name is the door instead.
                        var subUrl by remember(video.channelName) { mutableStateOf<String?>(null) }
                        LaunchedEffect(video.channelName) {
                            if (video.channelUrl == null && loadState.channelUrl == null) {
                                subUrl = runCatching {
                                    db.subscriptions().all().firstOrNull {
                                        it.name.equals(video.channelName, ignoreCase = true)
                                    }?.url
                                }.getOrNull()
                            }
                        }
                        val channelUrl = loadState.channelUrl ?: video.channelUrl ?: subUrl
                        val channelName = loadState.channelName.ifBlank { video.channelName }
                        if (cardStyle.channel) {
                            Text(
                                text = channelName,
                                style = MaterialTheme.typography.bodySmall,
                                color = accentTextColour(),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .weight(1f)
                                    .then(
                                        if (channelUrl.isNullOrBlank()) {
                                            Modifier
                                        } else {
                                            Modifier.clickable {
                                                onOpenChannel(channelUrl, channelName)
                                            }
                                        },
                                    )
                                    .padding(vertical = 4.dp),
                            )
                        }
                        val facts = buildList {
                            if (cardStyle.views) {
                                (loadState.views ?: video.viewCount)?.let {
                                    add("${compactCount(it)}\u00A0views")
                                }
                            }
                            if (cardStyle.likes) {
                                (loadState.likes ?: video.likeCount)?.let {
                                    add("${compactCount(it)}\u00A0likes")
                                }
                            }
                            if (cardStyle.watches) watches[video.id]?.takeIf { it > 1 }?.let { add("watched\u00A0$it\u00A0times") }
                            // The age is last so it sits at the far right of the line: when a
                            // reader glances at that corner, it is always "when".
                            if (cardStyle.age) video.publishedAtMs?.let { add(timeAgo(it)) }
                            // The comment count and the length are already on the page: the
                            // comments section says how many, the picture shows how long.
                        }.joinToString("\u00A0·\u00A0")
                        if (facts.isNotBlank()) {
                            // One line, ellipsised, right-aligned: the facts never wrap, so "4h"
                            // cannot end up on one line and "ago" on the next.
                            Text(
                                text = facts,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.End,
                            )
                        }
                    }
                    }
                }
            }

            item(key = "actions") {
                val watchUrl = "https://www.youtube.com/watch?v=${video.id}"
                // The locked moment, not the live position: while a finger is seeking the
                // playhead moves but the share does not, until the lift lands the exact seek.
                val shareSeconds = ((shareLockedMs ?: playbackState.positionMs) / 1000).coerceAtLeast(0)
                var shareOpen by remember { mutableStateOf(false) }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End,
                ) {
                    // The bug report sits on the left of this row and the share controls keep the
                    // right, so a mis-aimed thumb does not file a report. It is temporary, and the
                    // picture's top-right corner it used to hold is now nobody's.
                    IconButton(onClick = onOpenBug) {
                        Icon(
                            painter = painterResource(R.drawable.ic_bug),
                            contentDescription = "Bug report",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                            .combinedClickable(
                                onClick = { shareOpen = true },
                                onLongClick = {
                                    val send = android.content.Intent(
                                        android.content.Intent.ACTION_SEND,
                                    ).apply {
                                        type = "text/plain"
                                        putExtra(android.content.Intent.EXTRA_TEXT, watchUrl)
                                    }
                                    runCatching { context.startActivity(send) }
                                },
                            )
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Share. Hold to share the plain link",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = formatDuration(shareSeconds),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = {
                        runCatching {
                            context.startActivity(
                                android.content.Intent(
                                    android.content.Intent.ACTION_VIEW,
                                    android.net.Uri.parse(watchUrl),
                                ),
                            )
                        }
                    }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_open_in_new),
                            contentDescription = "Open in the browser",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (shareOpen) {
                    ShareSheet(
                        watchUrl = watchUrl,
                        durationSec = (playbackState.durationMs / 1000).toInt(),
                        positionSec = shareSeconds.toInt(),
                        onSeek = { seconds -> userSeek(seconds * 1000) },
                        onDismiss = { shareOpen = false },
                    )
                }
            }


            // The page below the picture, in the order set in Settings → Playback. Each part is a
            // whole lazy item (My notes is its header and its body), so a reorder moves sections
            // and Compose keeps the reader's place.
            for (section in sectionOrder) when (section) {
                PlayerSection.Description -> if (loadState.description.isNotBlank()) {
                    item(key = "description") {
                        Column(modifier = Modifier.fillMaxWidth().playerCard()) {
                            DescriptionBlock(
                                description = loadState.description,
                                expanded = descriptionExpanded,
                                labelsRight = labelsRight,
                                onToggle = { descriptionExpanded = !descriptionExpanded },
                                onSeek = { seconds -> userSeek(seconds * 1000) },
                            )
                        }
                    }
                }
                PlayerSection.Chapters -> if (shownChapters.isNotEmpty() && !chaptersHidden) {
                    item(key = "chapters") {
                        Column(modifier = Modifier.fillMaxWidth().playerCard()) {
                            ChaptersBlock(
                                chapters = shownChapters,
                                videoId = video.id,
                                thumbnailUrl = video.thumbnailUrl,
                                horizontal = chaptersStyle == ChaptersStyle.Row,
                                showTimestamps = chapterTimestamps,
                                labelsRight = labelsRight,
                                expanded = chaptersExpanded,
                                // The chapter the playhead is inside. Before the first mark there
                                // is none, which is honest: an intro is not the first chapter yet.
                                activeIndex = activeChapterIndex(
                                    shownChapters,
                                    playbackState.positionMs,
                                ),
                                onToggle = { chaptersExpanded = !chaptersExpanded },
                                onSeek = { seconds -> userSeek(seconds * 1000) },
                            )
                        }
                    }
                }
                PlayerSection.Sponsor -> if (playbackState.segments.isNotEmpty()) {
                    item(key = "sponsor") {
                        Column(modifier = Modifier.fillMaxWidth().playerCard()) {
                            SponsorSegmentsBlock(
                                segments = playbackState.segments,
                                positionMs = playbackState.positionMs,
                                videoId = video.id,
                                durationMs = playbackState.durationMs,
                                previewFrames = loadState.previewFrames,
                                log = log,
                                expanded = sponsorExpanded,
                                onToggle = { sponsorExpanded = !sponsorExpanded },
                                onSeek = { ms -> userSeek(ms) },
                            )
                        }
                    }
                }
                PlayerSection.Notes -> {
                    // My notes: a thin header with the record button always visible. The whole
                    // header is the tap target, not just the word: a collapsed section that
                    // ignores a tap anywhere but the chevron is a section people think is broken.
                    item(key = "notes-header") {
                        Column(modifier = Modifier.fillMaxWidth().playerCard()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .bringIntoViewRequester(notesRequester)
                                .clickable { notesExpanded = !notesExpanded }
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                                .heightIn(min = 44.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Spacer(Modifier.weight(1f))
                            // The record button lives in the card once expanded: one microphone,
                            // not two. It sits left of the title, so the header reads the same way
                            // the comments header does, right-aligned against the chevron.
                            if (!notesExpanded) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary)
                                        .clickable {
                                            notesExpanded = true
                                            dictateSignal++
                                        },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_mic),
                                        contentDescription = "Record a note",
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                                Spacer(Modifier.width(6.dp))
                            }
                            Text(
                                text = if (notesCount > 0) "My notes ($notesCount)" else "My notes",
                                style = MaterialTheme.typography.titleSmall,
                                color = accentTextColour(),
                            )
                            if (!labelsRight) Spacer(Modifier.weight(1f))
                            Icon(
                                imageVector = if (notesExpanded) {
                                    Icons.Default.KeyboardArrowUp
                                } else {
                                    Icons.Default.KeyboardArrowDown
                                },
                                contentDescription = if (notesExpanded) {
                                    "Collapse notes"
                                } else {
                                    "Expand notes"
                                },
                                modifier = Modifier
                                    .padding(start = 6.dp)
                                    .clickable { notesExpanded = !notesExpanded },
                            )
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                        }
                    }

                    item(key = "notes") {
                        AnimatedVisibility(
                            visible = notesExpanded,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut(),
                        ) {
                            NotesTab(
                                db = db,
                                playback = playback,
                                videoId = video.id,
                                log = log,
                                dictateSignal = dictateSignal,
                                onCount = { notesCount = it },
                            )
                        }
                    }
                }
                PlayerSection.Comments -> commentsItems(
                    state = commentsState,
                    viewModel = commentsViewModel,
                    labelsRight = labelsRight,
                )

                PlayerSection.UpNext -> if (endEnabled && relatedToShow.isNotEmpty()) {
                    item(key = "related") {
                        // Same inner padding as the blocks above it (16dp sides, 4dp top and
                        // bottom): 16 all round made this one card visibly taller than its
                        // neighbours for no reason a reader could see.
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .playerCard()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { relatedExpanded = !relatedExpanded }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (labelsRight) Spacer(Modifier.weight(1f))
                                Text(
                                    text = "Up next",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = accentTextColour(),
                                    modifier = if (labelsRight) Modifier else Modifier.weight(1f),
                                )
                                if (relatedRefreshing) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(14.dp),
                                        strokeWidth = 2.dp,
                                        color = accentTextColour(),
                                    )
                                    Spacer(Modifier.width(8.dp))
                                }
                                // Another page from the same source, on demand. One related()
                                // call per video returns the same set, so the answer is often
                                // "nothing new" — and that is what the line under the header says.
                                IconButton(
                                    onClick = { refreshRelated() },
                                    enabled = !relatedRefreshing,
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "Refresh up next",
                                        tint = accentTextColour(),
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                                Icon(
                                    imageVector = if (relatedExpanded) {
                                        Icons.Filled.KeyboardArrowUp
                                    } else {
                                        Icons.Filled.KeyboardArrowDown
                                    },
                                    contentDescription = if (relatedExpanded) {
                                        "Hide up next"
                                    } else {
                                        "Show up next"
                                    },
                                    tint = accentTextColour(),
                                )
                            }
                            relatedNote?.let { note ->
                                Text(
                                    text = note,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (relatedExpanded) {
                                relatedToShow.forEach { next ->
                                    com.presaince.glasstube.ui.component.VideoRow(
                                        video = next,
                                        watches = watches[next.id],
                                        onClick = { onOpenVideo(next) },
                                        onChannel = onOpenChannel,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

}

/**
 * The description as YouTube writes it: one row per line, timestamped lines seek, links stay
 * links, blank lines keep their breathing room. Collapsed it is only the header; a folded
 * description that still shows four lines is a description that did not fold. Tapping the header
 * or the word opens the whole text.
 */
@Composable
private fun DescriptionBlock(
    description: String,
    expanded: Boolean,
    labelsRight: Boolean,
    onToggle: () -> Unit,
    onSeek: (Long) -> Unit,
) {
    val allLines = remember(description) { description.lines().map { it.trimEnd() } }
    val shown = if (expanded) allLines else emptyList()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .animateContentSize(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 40.dp)
                .clickable(onClick = onToggle),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (labelsRight) Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "Description",
                style = MaterialTheme.typography.labelMedium,
                color = accentTextColour(),
                modifier = if (labelsRight) Modifier else Modifier.weight(1f),
            )
            Icon(
                imageVector = if (expanded) {
                    Icons.Default.KeyboardArrowUp
                } else {
                    Icons.Default.KeyboardArrowDown
                },
                contentDescription = if (expanded) {
                    "Collapse description"
                } else {
                    "Expand description"
                },
                tint = accentTextColour(),
            )
        }
        if (expanded) {
            // Selectable text: a long press starts a selection. A plain tap on the open text
            // folds it again, which is what the header already does; no hidden target.
            SelectionContainer {
                Column(modifier = Modifier.fillMaxWidth()) {
                    shown.forEach { line -> DescriptionLine(line = line, onSeek = onSeek) }
                }
            }
        }
    }
}

/** One line of the description, in the shape its content asks for. */
@Composable
private fun DescriptionLine(line: String, onSeek: (Long) -> Unit) {
    when {
        line.isBlank() -> Spacer(Modifier.height(8.dp))
        timestampLine(line) != null -> {
            val (seconds, title) = timestampLine(line)!!
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSeek(seconds) }
                    .padding(vertical = 3.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    text = formatDuration(seconds),
                    style = MaterialTheme.typography.labelMedium,
                    color = accentTextColour(),
                    modifier = Modifier.width(56.dp),
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        else -> LinkifiedText(
            text = line,
            maxLines = Int.MAX_VALUE,
            modifier = Modifier.padding(vertical = 1.dp),
        )
    }
}

/** How many real lines a collapsed description shows before the chevron. */
private const val DESCRIPTION_PREVIEW_LINES = 4

/**
 * The wait, made of the video itself. The thumbnail is the animation: it breathes slowly and a
 * band of light crosses it, so what is on screen is the thing being asked for rather than a
 * spinner laid over a darkened picture. Settings offers Kinetic, Still, Random and None; Random
 * decides per video, which keeps the wait from becoming the same loop every time. The animations
 * master switch turns the movement off without removing the photograph.
 */
@Composable
private fun LoadingPreview(videoId: String, thumbnailUrl: String?) {
    val prefs = LocalContext.current.getSharedPreferences("glasstube", Context.MODE_PRIVATE)
    val stored = remember { prefs.getInt("loading_style", 0) }
    // Random (3) picks kinetic or still once per video, not once per frame.
    val style = remember(videoId, stored) {
        if (stored == 3) (if (Random.nextBoolean()) 0 else 1) else stored
    }
    val motion = remember { prefs.getBoolean("animations_enabled", true) }
    if (style == 2) return

    val url = remember(videoId, thumbnailUrl) { Thumbnails.forVideo(videoId, thumbnailUrl) }
    val kinetic = motion && style == 0

    val transition = rememberInfiniteTransition(label = "loading")
    val sweep by transition.animateFloat(
        initialValue = -0.45f,
        targetValue = 1.45f,
        animationSpec = infiniteRepeatable(
            animation = tween(1900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "sweep",
    )
    val breathe by transition.animateFloat(
        initialValue = 1.02f,
        targetValue = 1.07f,
        animationSpec = infiniteRepeatable(
            animation = tween(4200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "breathe",
    )

    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        AsyncImage(
            model = url,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    if (kinetic) {
                        scaleX = breathe
                        scaleY = breathe
                    }
                }
                .alpha(if (style == 1) 0.22f else 0.42f),
        )
        if (kinetic) {
            // One soft band rather than a spinner: the picture is the progress.
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(maxWidth * 0.45f)
                    .offset(x = maxWidth * sweep)
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                Color.Transparent,
                                Color(0x26FFFFFF),
                                Color.Transparent,
                            ),
                        ),
                    ),
            )
        }
    }
}

/**
 * The SponsorBlock list. Invisible when the video has no enabled segments. One tap seeks to
 * that segment's start and plays it; the timeline bars are decoration, this list is the control.
 */
@Composable
private fun SponsorSegmentsBlock(
    segments: List<com.presaince.glasstube.data.sponsorblock.Segment>,
    positionMs: Long,
    videoId: String,
    durationMs: Long,
    previewFrames: List<com.presaince.glasstube.data.model.PreviewFrames>,
    log: AppLog? = null,
    expanded: Boolean,
    onToggle: () -> Unit,
    onSeek: (Long) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "SponsorBlock (${segments.size})",
                style = MaterialTheme.typography.labelMedium,
                color = accentTextColour(),
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = if (expanded) "Hide sponsor segments" else "Show sponsor segments",
                tint = accentTextColour(),
            )
        }
        if (expanded) {
            for (segment in segments) {
                val active = segment.contains(positionMs)
                // Frame at start + 5s so an imprecise timestamp lands on the segment's
                // likely first real picture. Short segments sample their midpoint instead.
                val frameMs = segmentFrameMs(segment)
                val frameset = previewFrames.firstOrNull()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            if (active) {
                                MaterialTheme.colorScheme.surfaceVariant.faded(0.35f)
                            } else {
                                Color.Transparent
                            },
                        )
                        .clickable { onSeek(segment.startMs) }
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (frameset != null) {
                        PreviewTile(
                            frameset = frameset,
                            frameMs = frameMs,
                            borderColor = segmentColor(segment.category),
                            fallbackUrl = segmentThumbUrl(videoId, segment, durationMs),
                            log = log,
                        )
                    } else {
                        AsyncImage(
                            model = segmentThumbUrl(videoId, segment, durationMs),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(width = 64.dp, height = 36.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .border(2.dp, segmentColor(segment.category), RoundedCornerShape(6.dp)),
                        )
                    }
                    Text(
                        text = com.presaince.glasstube.data.sponsorblock.SponsorCategories.label(segment.category),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (active) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        modifier = Modifier.padding(horizontal = 10.dp).weight(1f),
                    )
                    Text(
                        text = "${formatDuration(segment.startMs / 1000)} - ${formatDuration(segment.endMs / 1000)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** The storyboard tile at [frameMs]: the page image cropped to one cell. */
@Composable
private fun PreviewTile(
    frameset: com.presaince.glasstube.data.model.PreviewFrames,
    frameMs: Long,
    borderColor: Color,
    fallbackUrl: String,
    log: AppLog? = null,
) {
    val tile = frameset.tileAt(frameMs)
    val (page, col, row) = tile ?: Triple(0, 0, 0)
    val url = frameset.urls.getOrNull(page).orEmpty()
    var loaded by remember(url, col, row, frameMs) { androidx.compose.runtime.mutableStateOf(false) }
    var failed by remember(url, col, row, frameMs) { androidx.compose.runtime.mutableStateOf(url.isBlank()) }
    androidx.compose.runtime.LaunchedEffect(url, col, row, frameMs) {
        val hostPath = runCatching {
            val uri = java.net.URI(url)
            "${uri.host ?: "unknown"}${uri.path ?: ""}"
        }.getOrDefault("unknown")
        log?.info("segments", "tile frame=$frameMs page=$page col=$col row=$row host=$hostPath")
    }
    Box(
        modifier = Modifier
            .size(width = 64.dp, height = 36.dp)
            .clip(RoundedCornerShape(6.dp))
            .border(2.dp, borderColor, RoundedCornerShape(6.dp)),
    ) {
        // The quartile frame is the placeholder behind the tile, so a slow or failed
        // storyboard page still shows a picture instead of black.
        AsyncImage(
            model = fallbackUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        if (!failed) {
            Box(
                modifier = Modifier
                    .size(
                        width = (64 * frameset.framesPerPageX).dp,
                        height = (36 * frameset.framesPerPageY).dp,
                    )
                    .offset((-64 * col).dp, (-36 * row).dp),
            ) {
                AsyncImage(
                    model = url,
                    contentDescription = null,
                    contentScale = ContentScale.FillBounds,
                    onError = { failed = true; log?.warn("segments", "tile failed frame=$frameMs url=${runCatching { java.net.URI(url).host }.getOrNull()}") },
                    onSuccess = { loaded = true },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

/** The frame 5 seconds into the segment, clamped to stay inside it. */
private fun segmentFrameMs(segment: com.presaince.glasstube.data.sponsorblock.Segment): Long {
    val length = (segment.endMs - segment.startMs).coerceAtLeast(0L)
    return if (length > 5_000L) segment.startMs + 5_000L else segment.startMs + length / 2
}

/**
 * The nearest auto-captured frame to [segmentFrameMs]. YouTube exposes only four URL frames
 * (hqdefault + hq1/2/3 at ~25/50/75%), so the nearest quartile is the closest a URL gets to
 * the +5s picture. Coil loads it only when the row scrolls on screen.
 */
private fun segmentThumbUrl(
    videoId: String,
    segment: com.presaince.glasstube.data.sponsorblock.Segment,
    durationMs: Long,
): String {
    if (durationMs <= 0L) return "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
    val fraction = segmentFrameMs(segment).toFloat() / durationMs.toFloat()
    val name = when {
        fraction < 0.125f -> "hqdefault"
        fraction < 0.375f -> "hq1"
        fraction < 0.625f -> "hq2"
        fraction < 0.875f -> "hq3"
        else -> "hqdefault"
    }
    return "https://i.ytimg.com/vi/$videoId/$name.jpg"
}

/**
 * The chapter list: the description's timestamps, one tap per chapter. Collapsible header. The
 * body is either a row of small cards or a plain list, per the Settings toggle. Each chapter has
 * no frame of its own, so the video's thumbnail stands in for all of them.
 */
@Composable
private fun ChaptersBlock(
    chapters: List<Chapter>,
    videoId: String,
    thumbnailUrl: String?,
    horizontal: Boolean,
    showTimestamps: Boolean,
    labelsRight: Boolean,
    expanded: Boolean,
    activeIndex: Int,
    onToggle: () -> Unit,
    onSeek: (Long) -> Unit,
) {
    val thumb = Thumbnails.forVideo(videoId, thumbnailUrl)
    val listState = rememberLazyListState()
    // Keep the active chapter in view. Skipped when it is -1 (before the first mark).
    LaunchedEffect(activeIndex) {
        if (activeIndex >= 0) listState.animateScrollToItem(activeIndex)
    }
    // One canonical thumbnail for every chapter: same picture, no extra fetches.
    fun frame(index: Int): String = thumb
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (labelsRight) Spacer(Modifier.weight(1f))
            Text(
                text = "Chapters (${chapters.size})",
                style = MaterialTheme.typography.labelMedium,
                color = accentTextColour(),
                modifier = if (labelsRight) Modifier else Modifier.weight(1f),
            )
            Icon(
                imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = if (expanded) "Hide chapters" else "Show chapters",
                tint = accentTextColour(),
            )
        }
        if (expanded && horizontal) {
            LazyRow(
                state = listState,
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(top = 4.dp),
            ) {
                itemsIndexed(chapters, key = { _, chapter -> chapter.atSec }) { index, chapter ->
                    val active = index == activeIndex
                    Column(
                        modifier = Modifier
                            .width(120.dp)
                            .clickable { onSeek(chapter.atSec) },
                    ) {
                        Box {
                            AsyncImage(
                                model = frame(index),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(width = 120.dp, height = 68.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .then(
                                        if (active) {
                                            Modifier.border(
                                                2.dp,
                                                MaterialTheme.colorScheme.primary,
                                                RoundedCornerShape(6.dp),
                                            )
                                        } else {
                                            Modifier
                                        },
                                    ),
                            )
                            // The time used to sit on the frame in white, which is unreadable
                            // over half of YouTube's thumbnails. It is part of the caption now,
                            // and can be switched off entirely.
                        }
                        Text(
                            text = chapterCaption(
                                chapter,
                                showTimestamps,
                                formatDuration(chapter.atSec),
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (active) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
        } else if (expanded) {
            chapters.forEachIndexed { index, chapter ->
                val active = index == activeIndex
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            if (active) {
                                MaterialTheme.colorScheme.surfaceVariant.faded(0.35f)
                            } else {
                                Color.Transparent
                            },
                        )
                        .clickable { onSeek(chapter.atSec) }
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AsyncImage(
                        model = frame(index),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(width = 64.dp, height = 36.dp)
                            .clip(RoundedCornerShape(4.dp)),
                    )
                    if (showTimestamps) {
                        Text(
                            text = formatDuration(chapter.atSec),
                            style = MaterialTheme.typography.labelMedium,
                            color = accentTextColour(),
                            modifier = Modifier.padding(horizontal = 10.dp).width(48.dp),
                        )
                    } else {
                        Spacer(Modifier.width(10.dp))
                    }
                    Text(
                        text = chapter.title,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (active) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/**
 * The progress bar's slot: one fade for the portrait and fullscreen shells. It is its own
 * composable because a top-level `AnimatedVisibility` cannot be called from inside a `Column`:
 * `ColumnScope`'s overload shadows it and is then rejected by the layout scope marker.
 */
@Composable
private fun FadingProgressBar(
    visible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(160)),
        exit = fadeOut(tween(220)),
        modifier = modifier,
    ) { content() }
}

/**
 * How far a level travels per pixel of drag: the full range across half the picture's height.
 *
 * The rate is deliberately unchanged, because it is the part that felt right. What was missing was
 * somewhere to put the last few percent: a drag that began near the top of the picture ran out of
 * screen before it ran out of level. `core/LevelBand.kt` owns that answer — the band, and a magnet
 * at each end — and this factor is only the middle of the gesture.
 */
private const val LevelTravel = 2f

/** What the gesture HUD is saying: a time, a speed, or a level's percentage. */
private data class Hud(
    val label: String? = null,
    /** A smaller line under the primary one, such as the position a scrub is heading for. */
    val secondary: String? = null,
    /** A level, volume or brightness, drawn as a pill above the finger. */
    val fraction: Float? = null,
    /** The level's mark, so the readout says which level it is. */
    val icon: Int? = null,
    /** Scrub progress through the video, 0..1, drawn as a bar inside the pill. */
    val progress: Float? = null,
    /** Finger position as a fraction of the picture. The readout follows it. */
    val anchor: Offset? = null,
    /** Speed readout: a vertical pill on the left edge at the finger's height. */
    val leftEdge: Boolean = false,
)

/** A seek worth taking back: where the playhead was, and where it went. */
private data class SeekUndo(val beforeMs: Long, val afterMs: Long)

/** Average colour of a 48px thumbnail, on IO. The video tint only, never the theme. */
private fun averageColor(bitmap: android.graphics.Bitmap): Color {
    var r = 0L
    var g = 0L
    var b = 0L
    var n = 0L
    val w = bitmap.width
    val h = bitmap.height
    val stepX = (w / 16).coerceAtLeast(1)
    val stepY = (h / 16).coerceAtLeast(1)
    var y = 0
    while (y < h) {
        var x = 0
        while (x < w) {
            val c = bitmap.getPixel(x, y)
            r += android.graphics.Color.red(c)
            g += android.graphics.Color.green(c)
            b += android.graphics.Color.blue(c)
            n++
            x += stepX
        }
        y += stepY
    }
    if (n == 0L) return Color.Transparent
    return Color((r / n).toInt(), (g / n).toInt(), (b / n).toInt())
}

/** The way back from a seek. Sits on the picture for a few seconds, then goes. */
@Composable
private fun SeekUndoChip(undo: SeekUndo, onUndo: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(Color(0xE6101014))
            .clickable(onClick = onUndo)
            .padding(start = 14.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = com.presaince.glasstube.core.seekUndoLabel(undo.beforeMs, undo.afterMs),
            style = MaterialTheme.typography.labelMedium,
            color = Color.White,
        )
        TextButton(onClick = onUndo) {
            Text("Undo", color = accentTextColour())
        }
    }
}

/**
 * One section of the page under the picture: a rounded surface with the theme's quiet fill, so the
 * page reads as sections rather than as one run of text (`114914`). The chevrons were already
 * there; this is the box they belong to.
 */
@Composable
private fun Modifier.playerCard(): Modifier = this
    .padding(horizontal = 8.dp, vertical = 4.dp)
    .clip(RoundedCornerShape(16.dp))
    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.30f))

private fun segmentColor(category: String): Color =
    com.presaince.glasstube.data.sponsorblock.SponsorCategories.color(category)

@Composable
private fun SponsorChip(
    text: String,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(Color(0xE6101014))
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = Color.White,
        )
    }
}

/**
 * The gesture readout. Volume and brightness get one bare percentage above the finger; a scrub
 * or a skip gets a wide pill clear above the finger that asked for it, or below when the
 * finger is too near the top, carrying the whole video's progress in its bottom edge. Speed
 * sticks to the left edge at the finger's height, out of the way of the picture.
 */
@Composable
private fun HudPill(hud: Hud) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val level = hud.fraction
        val progress = hud.progress
        val anchorY = (hud.anchor?.y ?: 0.5f).coerceIn(0f, 1f)
        val anchorX = (hud.anchor?.x ?: 0.5f).coerceIn(0f, 1f)
        when {
            progress != null -> {
                val pillHeight = 48.dp
                val below = anchorY < 0.25f
                // 64dp of air, not 28: at 28 the bottom line sat half under the thumb, and the
                // pill's bottom edge carries the progress bar.
                val raw = if (below) {
                    maxHeight * anchorY + 56.dp
                } else {
                    maxHeight * anchorY - pillHeight - 64.dp
                }
                val y = raw.coerceIn(8.dp, (maxHeight - pillHeight - 8.dp).coerceAtLeast(8.dp))
                ScrubPill(
                    label = hud.label.orEmpty(),
                    secondary = hud.secondary,
                    progress = progress,
                    // The pill follows the finger instead of owning the middle of the picture
                    // (`111834`): the same placement rule the level readouts use, clamped inside.
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .offset(
                            x = readoutLeft(maxWidth * anchorX, maxWidth, 176.dp),
                            y = y,
                        ),
                )
            }
            level != null -> {
                // The seek pill's capsule, carrying the level's icon and its whole percent,
                // 64dp clear above the finger and travelling with it. Above is the safe side
                // for either hand: the finger's mass lies below the contact point, which is why
                // there is no handedness setting to consult.
                LevelReadout(
                    icon = hud.icon ?: R.drawable.ic_volume,
                    level = level,
                    modifier = Modifier.offset(
                        x = readoutLeft(maxWidth * anchorX, maxWidth),
                        y = readoutTop(
                            fingerY = maxHeight * anchorY,
                            pictureHeight = maxHeight,
                            bandTop = LEVEL_BAND_TOP_DP.dp,
                        ),
                    ),
                )
            }
            hud.leftEdge -> {
                val y = (maxHeight * anchorY - 24.dp)
                    .coerceIn(8.dp, (maxHeight - 56.dp).coerceAtLeast(8.dp))
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .offset(x = 12.dp, y = y)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xE6101014))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                ) {
                    Text(
                        text = hud.label.orEmpty(),
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                    )
                }
            }
            hud.label != null -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text(
                    text = hud.label,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    modifier = Modifier
                        .background(Color(0xCC101014), RoundedCornerShape(24.dp))
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                )
            }
        }
    }
}

/** The scrub readout: the distance as the headline, the target underneath, progress inside. */
@Composable
private fun ScrubPill(
    label: String,
    secondary: String?,
    progress: Float,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .width(176.dp)
            .height(56.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(Color(0xE6101014)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
            )
            if (secondary != null) {
                Text(
                    text = secondary,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.7f),
                )
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(5.dp)
                .background(Color(0x33FFFFFF)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress.coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun ControlsOverlay(
    visible: Boolean,
    state: PlaybackState,
    fullscreen: Boolean,
    onBack: () -> Unit,
    onTogglePlay: () -> Unit,
    onOpenSpeedPopup: (Offset) -> Unit,
    onSpeedAdjust: (Float, Float) -> Unit,
    onSpeedEnd: () -> Unit,
    onSpeedHold: (Boolean) -> Unit,
    onToggleFullscreen: () -> Unit,
    showPlay: Boolean,
    /** Where the last tap landed, as a fraction of the picture: the play disc goes there. */
    playAt: Offset?,
    /** Auto or the chosen height, shown beside the speed as a readout of the setting. */
    qualityLabel: String,
    selectedQuality: Int,
    /** Which ladder the menu is editing: the stop is stored per connection. */
    qualityConnection: String,
    /** The stops this screen can show, Auto last. */
    qualityStops: List<com.presaince.glasstube.core.QualityStop>,
    onPickQuality: (Int) -> Unit,
    speedPresets: List<Float>,
) {
    var overlayCoords by remember { mutableStateOf<androidx.compose.ui.layout.LayoutCoordinates?>(null) }
    var speedBoxCoords by remember { mutableStateOf<androidx.compose.ui.layout.LayoutCoordinates?>(null) }
    // The dial stops where the reader's presets stop (`111118`): tenths, low to high.
    val speedRange = remember(speedPresets) {
        val low = ((speedPresets.minOrNull() ?: 0.3f) * 10).roundToInt().coerceIn(3, 50)
        val high = ((speedPresets.maxOrNull() ?: 5f) * 10).roundToInt().coerceIn(low, 50)
        low to high
    }
    AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut()) {
        Box(modifier = Modifier.fillMaxSize().onGloballyPositioned { overlayCoords = it }) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0x99000000), Color(0x22000000), Color(0xAA000000)),
                        ),
                    ),
            )

            IconButton(
                onClick = onBack,
                modifier = Modifier.align(Alignment.TopStart).padding(4.dp),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White,
                )
            }

            // Pause appears where the finger last was, so the next tap lands on it rather than on
            // empty picture. A first tap in the middle behaves exactly like a centre button.
            if (showPlay) {
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val disc = 56.dp
                    val fx = (playAt?.x ?: 0.5f).coerceIn(0f, 1f)
                    val fy = (playAt?.y ?: 0.5f).coerceIn(0f, 1f)
                    val x = (maxWidth * fx - disc / 2)
                        .coerceIn(0.dp, (maxWidth - disc).coerceAtLeast(0.dp))
                    // The disc lives in the empty middle of the picture. A tap down in the
                    // control strip, or under the progress line, does not drag it down there: it
                    // gets the comfortable centre height instead.
                    val lowest = (maxHeight * 0.62f).coerceIn(0.dp, (maxHeight - disc).coerceAtLeast(0.dp))
                    // The top strip (back arrow, speed, quality, fullscreen) owns the first 56dp of
                    // the picture. A tap up there raises the controls, and the disc must not then
                    // land on top of the strip it just revealed.
                    val highest = 56.dp.coerceAtMost(lowest)
                    val y = if (fy > 0.72f) {
                        ((maxHeight - disc) / 2).coerceIn(highest, lowest)
                    } else {
                        (maxHeight * fy - disc / 2).coerceIn(highest, lowest)
                    }
                    Box(
                        modifier = Modifier
                            .offset(x = x, y = y)
                            .size(disc)
                            .clip(CircleShape)
                            .background(Color(0x66000000))
                            .border(2.dp, Color.White, CircleShape)
                            .clickable(role = Role.Button, onClick = onTogglePlay),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (state.isPlaying) {
                            Icon(
                                painter = painterResource(R.drawable.ic_pause),
                                contentDescription = "Pause",
                                tint = Color.White,
                                modifier = Modifier.size(30.dp),
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Play",
                                tint = Color.White,
                                modifier = Modifier.size(34.dp),
                            )
                        }
                    }
                }
            }

            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    // Speed, quality and fullscreen are the picture's top row now. The bar owns
                    // the bottom edge, so this strip has no business there; the back arrow keeps
                    // the top-left, which is why the strip starts after its 48dp.
                    .padding(start = 48.dp, end = 4.dp, top = 4.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // The times are on the bar's own row now, at its two ends. This strip keeps
                    // the controls: speed, quality, fullscreen.
                    Spacer(Modifier.weight(1f))
                    // The player's own position, not the last UI sample. Seeking is the slide
                    // gesture now, so the arrow buttons are gone.
                    var dial by remember { mutableStateOf<SpeedDial?>(null) }
                    var qualityMenuOpen by remember { mutableStateOf(false) }
                    val ticks = rememberTicks()
                    fun speedTick(fromTenths: Int, toTenths: Int) {
                        // The snap back at 1.0x, gated by its own setting inside TickRunner.
                        ticks.speedHome(fromTenths, toTenths)
                    }
                    Box(
                        modifier = Modifier
                            .onGloballyPositioned { speedBoxCoords = it }
                            .pointerInput(state.speed) {
                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = false)
                                    down.consume()
                                    onSpeedHold(true)
                                    try {
                                    val height = size.height.toFloat().coerceAtLeast(1f)
                                    val baseTenths = (state.speed * 10).roundToInt()
                                    var lastY = down.position.y
                                    var travelled = 0f
                                    var adjusting = false
                                    var applied = baseTenths
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        val change = event.changes.firstOrNull { it.id == down.id }
                                            ?: break
                                        change.consume()
                                        travelled += lastY - change.position.y
                                        lastY = change.position.y
                                        if (!adjusting && kotlin.math.abs(travelled) > 14f) {
                                            adjusting = true
                                        }
                                        if (adjusting) {
                                            val steps = (travelled / 24f).roundToInt()
                                            val tenths = (baseTenths + steps)
                                                .coerceIn(speedRange.first, speedRange.second)
                                            if (tenths != applied) {
                                                val previous = applied
                                                applied = tenths
                                                dial = SpeedDial(tenths / 10f, change.position.y)
                                                // The snap back at home (1.0x) when the drag
                                                // lands on it or jumps over it.
                                                speedTick(previous, tenths)
                                                onSpeedAdjust(
                                                    tenths / 10f,
                                                    (change.position.y / height)
                                                        .coerceIn(0f, 1f),
                                                )
                                            }
                                        }
                                        if (!change.pressed) {
                                            if (adjusting) {
                                                dial = null
                                                onSpeedEnd()
                                            } else {
                                                // The tap point, in the overlay's coordinates: the
                                                // panel opens on the finger that asked for it, not
                                                // on the button's corner (`213728`).
                                                val anchor = overlayCoords?.let { parent ->
                                                    speedBoxCoords?.let { speed ->
                                                        parent.localPositionOf(speed, down.position)
                                                    }
                                                } ?: Offset.Zero
                                                onOpenSpeedPopup(anchor)
                                            }
                                            break
                                        }
                                    }
                                    } finally {
                                        // Whatever interrupted the gesture (a second finger, the
                                        // panel opening, the player pausing) gives the picture back.
                                        dial = null
                                        onSpeedHold(false)
                                    }
                                }
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    ) {
                        Text(
                            text = "%.1fx".format(state.speed),
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.White,
                        )
                        dial?.let { current ->
                            Popup(popupPositionProvider = SpeedLadderPosition(current.fingerY)) {
                                SpeedLadder(presets = speedPresets, current = current.speed)
                            }
                        }
                    }
                    Box {
                        Text(
                            text = qualityLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier
                                .clickable { qualityMenuOpen = true }
                                .padding(horizontal = 6.dp, vertical = 6.dp),
                        )
                        DropdownMenu(
                            expanded = qualityMenuOpen,
                            onDismissRequest = { qualityMenuOpen = false },
                        ) {
                            Text(
                                text = qualityConnection,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            )
                            qualityStops.forEach { stop ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = stop.label,
                                            color = if (stop.height == selectedQuality) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.onSurface
                                            },
                                        )
                                    },
                                    onClick = {
                                        qualityMenuOpen = false
                                        onPickQuality(stop.height)
                                    },
                                )
                            }
                        }
                    }
                    IconButton(onClick = onToggleFullscreen) {
                        Icon(
                            painter = painterResource(R.drawable.ic_fullscreen),
                            contentDescription = if (fullscreen) {
                                "Leave fullscreen"
                            } else {
                                "Fullscreen"
                            },
                            tint = Color.White,
                        )
                    }
                }
            }
        }
    }
}

/**
 * One share panel for everything a link can carry. "This moment" puts the playhead in the URL; "A
 * clip" adds an end, which has to travel in the text because a YouTube link holds only one time.
 * The bar carries a live playhead line, both ends can be played to, and "Sync to now" pulls the
 * end (or the moment) to wherever the video is.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShareSheet(
    watchUrl: String,
    durationSec: Int,
    positionSec: Int,
    onSeek: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val total = durationSec.coerceAtLeast(1)
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
            var start by remember {
                mutableFloatStateOf(positionSec.toFloat().coerceIn(0f, (total - 1).toFloat()))
            }
            var end by remember {
                mutableFloatStateOf(
                    (positionSec + 30).coerceAtMost(total).toFloat().coerceAtLeast(start + 1f),
                )
            }
            // The video keeps playing behind the sheet, so this line moves while you look at it.
            val playhead = (positionSec.toFloat() / total).coerceIn(0f, 1f)

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Share",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                // Pulls the end of a clip to the playhead; with a single moment, moves the moment.
                TextButton(onClick = {
                    val now = positionSec.toFloat()
                    if (end - start > 1f) {
                        end = now.coerceIn(start + 1f, total.toFloat())
                    } else {
                        start = now.coerceIn(0f, (total - 1).toFloat())
                    }
                }) { Text("Sync to now") }
            }
            Text(
                text = "The link carries the start. Drag the second handle to add an end, or leave it " +
                    "where it is and only the moment is shared.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )

            val trackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.30f)
            val handleColor = MaterialTheme.colorScheme.primary
            val playheadColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.80f)
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .padding(vertical = 16.dp)
                    .pointerInput(total) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            down.consume()
                            val width = size.width.toFloat().coerceAtLeast(1f)
                            fun secondsAt(x: Float) =
                                (x / width * total).coerceIn(0f, total.toFloat())
                            var movingStart = kotlin.math.abs(
                                down.position.x - start / total * width,
                            ) <= kotlin.math.abs(down.position.x - end / total * width)
                            fun apply(x: Float) {
                                val at = secondsAt(x)
                                if (movingStart) {
                                    start = at.coerceAtMost(end - 1f).coerceAtLeast(0f)
                                } else {
                                    end = at.coerceAtLeast(start + 1f).coerceAtMost(total.toFloat())
                                }
                            }
                            apply(down.position.x)
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id }
                                    ?: break
                                change.consume()
                                apply(change.position.x)
                                if (!change.pressed) break
                            }
                        }
                    },
            ) {
                val y = size.height / 2
                val startX = start / total * size.width
                val endX = end / total * size.width
                drawLine(
                    color = trackColor,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 6f,
                )
                if (end - start > 1f) {
                    drawLine(
                        color = handleColor,
                        start = Offset(startX, y),
                        end = Offset(endX, y),
                        strokeWidth = 8f,
                    )
                    drawCircle(handleColor, radius = 13f, center = Offset(startX, y))
                    drawCircle(handleColor, radius = 13f, center = Offset(endX, y))
                } else {
                    drawCircle(handleColor, radius = 13f, center = Offset(startX, y))
                }
                // The video's own position, not a third handle: thin, dim, never grabbable.
                val px = playhead * size.width
                drawLine(
                    color = playheadColor,
                    start = Offset(px, 4f),
                    end = Offset(px, size.height - 4f),
                    strokeWidth = 3f,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (end - start > 1f) {
                        "${formatDuration(start.toLong())} – ${formatDuration(end.toLong())}"
                    } else {
                        "at ${formatDuration(start.toLong())}"
                    },
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                if (end - start > 1f) {
                    Text(
                        text = formatDuration((end - start).toLong()),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            // Play to an end to check it. "Go to end" lands on the end itself, which is what the
            // label promises; the two-second head start made the last moment of the clip
            // impossible to check.
            // The shared text is only the link. A clip adds &end, which YouTube ignores but GlassTube
            // reads, so the same link works for both.
            val shareText = if (end - start > 1f) {
                "$watchUrl&t=${start.toLong()}s&end=${end.toLong()}s"
            } else {
                "$watchUrl&t=${start.toLong()}s"
            }
            // Small seeks by name. Dragging a handle on a two-hour video cannot land on a second,
            // so the sheet carries the step a video reviewer actually uses, and each one moves the
            // picture as well as the start, because a clip boundary you cannot watch is a guess.
            fun nudge(by: Float) {
                val at = (start + by).coerceIn(0f, (total - 1).toFloat())
                start = if (end - start > 1f) at.coerceAtMost(end - 1f) else at
                onSeek(start.toLong())
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                listOf(-30f, -10f, -2f, 2f, 10f, 30f).forEach { step ->
                    TextButton(
                        onClick = { nudge(step) },
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            horizontal = 6.dp,
                            vertical = 4.dp,
                        ),
                    ) {
                        Text(
                            text = formatSeekDelta((step * 1000).toLong()),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = { onSeek(start.toLong()) }) { Text("Go to start") }
                if (end - start > 1f) {
                    TextButton(onClick = {
                        onSeek(end.toLong())
                    }) { Text("Go to end") }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(onClick = {
                    val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(android.content.Intent.EXTRA_TEXT, shareText)
                    }
                    runCatching { context.startActivity(send) }
                    onDismiss()
                }) { Text("Share") }
            }
        }
    }
}

/** Speed fill fraction: the shared LevelPill language, so speed reads like volume/brightness. */
private fun speedFill(speed: Float): Float =
    ((speed - 0.25f) / (5.0f - 0.25f)).coerceIn(0f, 1f)

/**
 * Speed: a level-fill pill that opens just above the control you tapped, under the finger, so
 * the slide starts where the hand already is. Only the current speed is shown; a drag previews
 * a speed and only commits when the finger lifts.
 */
@Composable
private fun SpeedPanel(
    speed: Float,
    presets: List<Float>,
    onSpeed: (Float) -> Unit,
    onDismiss: () -> Unit,
    anchor: Offset = Offset.Zero,
) {
    var preview by remember(speed) { mutableFloatStateOf(speed) }
    var dragBase by remember { mutableFloatStateOf(speed) }
    var accumulated by remember { mutableFloatStateOf(0f) }
    var lastTenths by remember { mutableIntStateOf((speed * 10).roundToInt()) }
    // The reader's presets are the range: a panel that offered 5.0x when the list stops at 2.0x is
    // a control advertising speeds they have switched off (`111118`).
    val lowestTenths = ((presets.minOrNull() ?: 0.3f) * 10).roundToInt().coerceIn(3, 50)
    val highestTenths = ((presets.maxOrNull() ?: 5f) * 10).roundToInt()
        .coerceIn(lowestTenths, 50)
    val ticks = rememberTicks()
    fun panelTick(fromTenths: Int, toTenths: Int) {
        ticks.speedHome(fromTenths, toTenths)
    }

    // Anchor is the tap point in the overlay's coordinates. The panel (84dp wide, 96dp tall) is
    // placed by the same rules as the level readouts: centred on the finger, clear above it, and
    // held inside the picture. The old placement centred it on the speed button's corner, so the
    // pill appeared beside the finger rather than over it (`213728`).
    val density = androidx.compose.ui.platform.LocalDensity.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0x33000000))
            .clickable(onClick = onDismiss),
    ) {
        val panelW = 84.dp
        val panelH = 96.dp
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val offset = if (anchor != Offset.Zero) {
                with(density) { readoutLeft(anchor.x.toDp(), maxWidth, panelW) }
            } else {
                12.dp
            }
            val offsetY = if (anchor != Offset.Zero) {
                with(density) { readoutTop(anchor.y.toDp(), maxHeight, panelH) }
            } else {
                64.dp
            }
            Surface(
                // Same capsule shape as the readouts, but the fill is the level: one language.
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                modifier = Modifier
                    // Centred on the finger and clear of the thumb, so sliding continues
                    // without reaching across the screen and the reset hint is never covered.
                    .offset(x = offset, y = offsetY)
                    .wrapContentWidth()
                // Swallow taps meant for the panel itself. A tap on the pill is the way home: it
                // resets to 1.0x and closes, which is the one speed everybody wants back and the
                // only one a drag makes awkward to reach.
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {
                        onSpeed(1f)
                        onDismiss()
                    },
                )
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragStart = {
                            dragBase = preview
                            accumulated = 0f
                        },
                        onVerticalDrag = { _, delta ->
                            accumulated += -delta
                            val steps = (accumulated / 24f).roundToInt()
                            val tenths = ((dragBase * 10).roundToInt() + steps)
                                .coerceIn(lowestTenths, highestTenths)
                            if (tenths != lastTenths) {
                                val previous = lastTenths
                                lastTenths = tenths
                                panelTick(previous, tenths)
                            }
                            preview = tenths / 10f
                        },
                        onDragEnd = {
                            onSpeed(preview)
                            onDismiss()
                        },
                        onDragCancel = { preview = speed; onDismiss() },
                    )
                },
        ) {
            // Only the current speed, filled like volume/brightness. The preset numbers are
            // gone; the fill is the reading. The way back sits at the top, clear of the thumb
            // that is on the panel.
            LevelPill(
                level = speedFill(preview),
                width = 84.dp,
                height = 96.dp,
                fillColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
            ) {
                // Only while there is something to reset: a hint under 1.0x would be a
                // control that does nothing.
                if (kotlin.math.abs(preview - 1f) > 0.001f) {
                    Text(
                        text = "tap for 1.0x",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.72f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 8.dp, start = 2.dp, end = 2.dp),
                    )
                }
                Text(
                    text = "%.1fx".format(preview),
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White,
                    modifier = Modifier.align(Alignment.Center),
                )
                }
            }
        }
    }
}

/** The finger's place on the speed pill while it is being dragged, in the pill's own pixels. */
private data class SpeedDial(val speed: Float, val fingerY: Float)

/** The speed readout during a drag: only the current speed, filled like the other pills. */
@Composable
private fun SpeedLadder(presets: List<Float>, current: Float) {
    LevelPill(
        level = speedFill(current),
        width = 72.dp,
        height = 64.dp,
        trackColor = Color(0xD9000000),
    ) {
        Text(
            text = "%.1fx".format(current),
            style = MaterialTheme.typography.labelLarge,
            color = Color.White,
        )
    }
}

/**
 * Above the finger by default, so the readout is never under the thumb; below only when the
 * finger is too near the top of the window for that.
 */
private class SpeedLadderPosition(private val fingerY: Float) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val x = anchorBounds.left + (anchorBounds.width - popupContentSize.width) / 2
        val gap = 48
        val above = anchorBounds.top + fingerY.roundToInt() - gap - popupContentSize.height
        val y = if (above >= 0) {
            above
        } else {
            anchorBounds.top + fingerY.roundToInt() + gap
        }
        return IntOffset(
            x.coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0)),
            y.coerceIn(0, (windowSize.height - popupContentSize.height).coerceAtLeast(0)),
        )
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

/** Volume changes in steps of about 1/20 of the stream, so a swipe is felt but not a jump. */
private fun rawVolume(audio: AudioManager): Float =
    audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()

private fun maxVolume(audio: AudioManager): Float =
    audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1).toFloat()

private fun volumeFraction(audio: AudioManager): Float =
    (rawVolume(audio) / maxVolume(audio)).coerceIn(0f, 1f)

/** Window brightness is unset while the system value is in charge; 50% is the honest guess. */
private fun brightnessFraction(activity: Activity?): Float {
    val value = activity?.window?.attributes?.screenBrightness ?: -1f
    return if (value < 0f) 0.5f else value
}

/** Give brightness back to the system, so the window stops overriding the shade's slider. */
internal fun restoreSystemBrightness(window: Window?) {
    if (window == null) return
    window.attributes = window.attributes.apply {
        screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
    }
}

/** "-12s" / "+12s": the size of the jump is the headline, so the sign stays visible. */
private fun formatSeekDelta(deltaMs: Long): String =
    com.presaince.glasstube.core.seekDelta(deltaMs / 1000)

/** The scrub pill sits above the finger, or below it when the finger is too near the top. */
private fun scrubProgress(positionMs: Long, durationMs: Long): Float =
    if (durationMs <= 0L) 0f else (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
