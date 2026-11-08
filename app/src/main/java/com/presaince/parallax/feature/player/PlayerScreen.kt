/*
 * Parallax — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.parallax.feature.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.media.AudioManager
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
import android.os.VibrationEffect
import android.os.Vibrator
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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import coil3.compose.AsyncImage
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import com.presaince.parallax.data.resync.AiChaptersEngine
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
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
import com.presaince.parallax.R
import com.presaince.parallax.core.AppLog
import com.presaince.parallax.core.formatDuration
import com.presaince.parallax.core.timeAgo
import com.presaince.parallax.data.auth.PostingManager
import com.presaince.parallax.data.db.ParallaxDb
import com.presaince.parallax.data.library.ContentRepository
import com.presaince.parallax.data.model.VideoSummary
import com.presaince.parallax.data.resync.ResyncEngine
import com.presaince.parallax.data.resync.ResyncStore
import com.presaince.parallax.data.source.Thumbnails
import com.presaince.parallax.data.source.VideoSource
import com.presaince.parallax.feature.comments.CommentsSection
import com.presaince.parallax.feature.comments.CommentsViewModel
import com.presaince.parallax.feature.notes.NotesTab
import com.presaince.parallax.feature.player.gesture.Zone
import com.presaince.parallax.feature.player.gesture.playbackGestures
import com.presaince.parallax.ui.component.LinkifiedText
import com.presaince.parallax.ui.component.SineRiver
import com.presaince.parallax.ui.component.compactCount
import com.presaince.parallax.ui.theme.faded
import com.presaince.parallax.ui.theme.parallaxFilterChipColors
import coil3.compose.AsyncImage
import com.presaince.parallax.playback.PlaybackController
import com.presaince.parallax.playback.PlaybackState
import kotlin.math.roundToInt
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val CONTROLS_TIMEOUT_MS = 3_000L
private const val UNDO_VISIBLE_MS = 4_500L
private const val UNDO_MIN_MS = 2_000L

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
    db: ParallaxDb,
    posting: PostingManager,
    resync: ResyncEngine,
    resyncStore: ResyncStore,
    aiChapters: AiChaptersEngine,
    video: VideoSummary,
    log: AppLog,
    onBack: () -> Unit,
    onOpenBug: () -> Unit,
    onOpenChannel: (url: String, name: String) -> Unit = { _, _ -> },
    onOpenVideo: (com.presaince.parallax.data.model.VideoSummary) -> Unit = {},
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

    // Reopening a video that was stopped reuses this view model. If the controller no longer owns
    // this video, play it again instead of sitting on a loaded title over a black frame.
    LaunchedEffect(playbackState.videoId) {
        viewModel.ensurePlaying(playbackState.videoId)
    }

    // A stream that dies mid-video gets two quiet tries at where it was before the failure is
    // left on screen. Stream URLs expire and connections drop; asking the viewer to press Retry
    // for that is exactly the "it keeps stopping" feeling.
    var autoRetries by remember { mutableIntStateOf(0) }
    LaunchedEffect(playbackState.playerError) {
        if (playbackState.playerError == null || autoRetries >= 2) return@LaunchedEffect
        autoRetries++
        delay(1_200)
        viewModel.reloadAt(playback.state.value.positionMs)
    }

    val context = LocalContext.current
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
    val chaptersHorizontal = remember {
        context.getSharedPreferences("parallax", Context.MODE_PRIVATE)
            .getBoolean("chapters_horizontal", true)
    }
    var dictateSignal by remember { mutableIntStateOf(0) }
    var dragDown by remember { mutableStateOf(0f) }
    var dragStartedAt by remember { mutableLongStateOf(0L) }
    var volumeSteps by remember { mutableFloatStateOf(0f) }
    var brightnessLevel by remember { mutableFloatStateOf(0.5f) }
    var tapAt by remember { mutableStateOf<Offset?>(null) }
    var scrubStartMs by remember { mutableLongStateOf(0L) }
    // A seek that moved more than a moment gets a way back. Accidental bar taps are the reason.
    var seekUndo by remember { mutableStateOf<SeekUndo?>(null) }
    var barStartMs by remember { mutableStateOf<Long?>(null) }
    var lastScrubSeekAt by remember { mutableLongStateOf(0L) }
    // How far the finger has travelled sideways in this gesture, as a fraction of the width.
    // The seek rate rises with it, so a short swipe is precise and a long one covers a long video.
    var scrubTravel by remember { mutableFloatStateOf(0f) }
    // The rate at the start of a swipe: seconds per 1% of the screen. Chosen in Settings.
    val seekBaseSeconds = remember {
        context.getSharedPreferences("parallax", Context.MODE_PRIVATE)
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
    var fullscreen by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

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
            // Orientation is restored by the nav when the player leaves (see ParallaxNav).
            // Doing it here, in a dispose that runs on every fullscreen toggle, fought the
            // rotation and snapped the picture back to portrait.
        }
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
        context.getSharedPreferences("parallax", Context.MODE_PRIVATE)
            .getBoolean("progress_in_fullscreen", true)
    }
    var qualityHeight by remember {
        mutableIntStateOf(
            context.getSharedPreferences("parallax", Context.MODE_PRIVATE)
                .getInt("video_quality", 0),
        )
    }
    val chapters = remember(loadState.description) { parseChapters(loadState.description) }
    val shownChapters = if (chapters.isNotEmpty()) chapters else loadState.aiChapters
    // The timestamp lines are already the chapter panel above. Leaving them in the description
    // put the same list on screen twice, one of them not tappable.
    val descriptionBody = remember(loadState.description, chapters) {
        if (chapters.isNotEmpty()) {
            loadState.description.lineSequence()
                .filter { timestampLine(it.trim()) == null }
                .joinToString("\n")
                .trim()
        } else {
            loadState.description
        }
    }

    // Only the model fills a gap: its features are off unless the user enabled the endpoint and key.
    LaunchedEffect(chapters.isEmpty(), loadState.description.isNotEmpty()) {
        if (chapters.isEmpty() && loadState.description.isNotEmpty()) {
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
            // Queueing and repair take a visible word, not just the waiting picture: a stuck
            // stream with no sentence reads as a crash. The retry counter and the extractor's own
            // repair lines are in the log; this is the short label under the picture.
            val streamBroken = playbackState.playerError != null
            val buffering = playbackState.isBuffering
            val statusLine = when {
                streamBroken -> "Stream broken — retrying"
                buffering -> "Queueing…"
                loadState.loading -> "Queueing…"
                else -> null
            }
            if (statusLine != null && loadState.error == null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 20.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color(0xCC000000))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            color = Color.White,
                            strokeWidth = 2.dp,
                        )
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
            onOpenSpeedPopup = { speedPanelOpen = true },
            onSpeedAdjust = { speed, _ -> playback.setSpeed(speed) },
            onSpeedEnd = { hud = null },
            onSpeedHold = { speedHolding = it },
            onToggleFullscreen = {
                val next = !fullscreen
                fullscreen = next
                // Fixed landscape, not a sensor orientation: a sensor lock snaps back to
                // portrait when auto-rotate is off, which is the "goes fullscreen then
                // rotates back" the button never recovered from.
                activity?.requestedOrientation = if (next) {
                    ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                } else {
                    ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                }
            },
            onOpenBug = onOpenBug,
            // The readout owns the middle of the picture while it is up, so the play disc
            // steps aside instead of sitting on top of the number.
            showPlay = hud == null,
            playAt = tapAt,
            qualityLabel = com.presaince.parallax.core.qualityLabel(qualityHeight),
            selectedQuality = qualityHeight,
            onPickQuality = { height ->
                context.getSharedPreferences("parallax", Context.MODE_PRIVATE)
                    .edit()
                    .putInt("video_quality", height)
                    .apply()
                qualityHeight = height
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
            )
        }
    }

    val gestures: Modifier = Modifier.playbackGestures(
        // A press on the speed dial, or an open speed panel, owns the gesture. Restarting the
        // picture detector disabled is what stops the same drag from also being a brightness swipe.
        enabled = !speedHolding && !speedPanelOpen,
        onVerticalStart = { zone ->
            dragDown = 0f
            dragZone = zone
            dragStartedAt = SystemClock.uptimeMillis()
            volumeSteps = rawVolume(audio)
            brightnessLevel = brightnessFraction(activity)
            // Nothing is shown until the gesture actually changes the level: a swipe with the
            // volume already at zero should not flash an empty capsule at the user.
            hud = null
        },
        onVerticalDelta = { zone, fraction ->
            when (zone) {
                Zone.Left -> {
                    val before = volumeSteps.roundToInt()
                    // Accumulate in steps, not in whole steps per event: rounding every delta to
                    // an Int lost most of a slow swipe, which is why the slider felt stuck.
                    volumeSteps = (volumeSteps + fraction * maxVolume(audio) * 2f)
                        .coerceIn(0f, maxVolume(audio))
                    audio.setStreamVolume(
                        AudioManager.STREAM_MUSIC,
                        volumeSteps.roundToInt(),
                        0,
                    )
                    if (volumeSteps.roundToInt() != before) {
                        hud = Hud(fraction = volumeFraction(audio))
                    }
                }
                Zone.Right -> if (playbackState.isPlaying) {
                    // Brightness is a playback gesture. On a paused or stopped picture the same
                    // drag is how the page below scrolls, so it must not dim the screen instead.
                    val before = brightnessLevel
                    brightnessLevel = (brightnessLevel + fraction).coerceIn(0.01f, 1f)
                    if (kotlin.math.abs(brightnessLevel - before) > 0.001f) {
                        activity?.window?.let { window ->
                            window.attributes = window.attributes.apply {
                                screenBrightness = brightnessLevel
                            }
                        }
                        hud = Hud(fraction = brightnessLevel, brightness = true)
                    }
                }
                // Centre downwards is the minimise gesture: the surface follows the finger, and
                // a qualifying flick closes it at once. No animation on release, in either
                // direction: it either leaves or snaps back with the finger.
                else -> {
                    // The full finger travel, clamped only at the ends. The surface translation
                    // reads this directly, so what the picture does matches what the finger did.
                    dragDown = (dragDown + -fraction).coerceIn(0f, 1f)
                }
            }
        },
        onVerticalEnd = {
            hud = null
            val centre = dragZone != Zone.Left && dragZone != Zone.Right
            val elapsed = SystemClock.uptimeMillis() - dragStartedAt
            // A flick counts even when it is short; a slow drag has to travel to be meant.
            val flung = dragDown > 0.03f && elapsed < 260
            if (centre && (dragDown > 0.22f || flung)) {
                // No backflip: the player closes at once, and the mini card takes over.
                dragDown = 0f
                onBack()
            } else {
                dragDown = 0f
            }
        },
        onHorizontalStart = { yFraction ->
            shareLockedMs = playbackState.positionMs
            scrubStartMs = playbackState.positionMs
            scrubTargetMs = playbackState.positionMs
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
            scrubTravel += kotlin.math.abs(fraction)
            val ramp = 1f + (scrubTravel.coerceAtMost(0.40f) / 0.40f) * 7f
            val movedMs = (fraction * 10f * seekBaseSeconds * ramp * 1000f).toLong()
            val target = (scrubTargetMs ?: playbackState.positionMs) + movedMs
            scrubTargetMs = target.coerceIn(0, playbackState.durationMs)
            val settled = scrubTargetMs ?: 0L
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
                }
                scrubTargetMs = (fraction * duration).toLong()
                playback.seekTo(scrubTargetMs ?: 0L)
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
    val progressLine: @Composable () -> Unit = {
        // The only permanent progress indicator. It lives on the bottom edge of the picture, so
        // it is there with the controls hidden and in fullscreen, and the gesture pill is left to
        // say where a scrub is going.
        // Top half is the SponsorBlock markers only, bottom half is the playhead. Drawing both
        // at full height bled the colours together; the split keeps each colour pure.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .background(Color(0x44FFFFFF)),
        ) {
            val duration = playbackState.durationMs
            val segments = playbackState.segments
            Box(modifier = Modifier.fillMaxWidth().height(3.dp)) {
                androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                    if (duration > 0L) {
                        for (segment in segments) {
                            val start = (segment.startMs.toFloat() / duration).coerceIn(0f, 1f)
                            val end = (segment.endMs.toFloat() / duration).coerceIn(0f, 1f)
                            drawRect(
                                color = segmentColor(segment.category),
                                topLeft = androidx.compose.ui.geometry.Offset(start * size.width, 0f),
                                size = androidx.compose.ui.geometry.Size(((end - start) * size.width), size.height),
                            )
                        }
                    }
                }
            }
            Box(modifier = Modifier.fillMaxWidth().height(3.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progressFraction)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.primary),
                )
            }
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
                            text = "Skipped ${com.presaince.parallax.data.sponsorblock.SponsorCategories.label(seg.category)} · ${formatDuration(seg.startMs / 1000)} → ${formatDuration(seg.endMs / 1000)}",
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = 12.dp, end = 12.dp),
                        )
                    }
                    currentSegment?.let { seg ->
                        if (lastSkip == null) {
                            SponsorChip(
                                text = "${com.presaince.parallax.data.sponsorblock.SponsorCategories.label(seg.category)} ends at ${formatDuration(seg.endMs / 1000)}",
                                onClick = { playback.seekTo(seg.endMs) },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(top = 12.dp, end = 12.dp),
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
                                .padding(bottom = 16.dp),
                        )
                    }
                    if (progressInFullscreen) {
                        Box(modifier = Modifier.align(Alignment.BottomCenter)) { progressLine() }
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
                        text = "Skipped ${com.presaince.parallax.data.sponsorblock.SponsorCategories.label(seg.category)} · ${formatDuration(seg.startMs / 1000)} → ${formatDuration(seg.endMs / 1000)}",
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 12.dp, end = 12.dp),
                    )
                }
                currentSegment?.let { seg ->
                    if (lastSkip == null) {
                        SponsorChip(
                            text = "${com.presaince.parallax.data.sponsorblock.SponsorCategories.label(seg.category)} ends at ${formatDuration(seg.endMs / 1000)}",
                            onClick = { playback.seekTo(seg.endMs) },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = 12.dp, end = 12.dp),
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
                            .padding(bottom = 16.dp),
                    )
                }
                Box(modifier = Modifier.align(Alignment.BottomCenter)) { progressLine() }
                // Video failures (members-only, private, etc.) get the whole black frame, because
                // that is what the failure is sitting on. Larger than a line, and retryable.
                loadState.error?.let { message ->
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
                                text = if (retryCount > 3) {
                                    "ya, you're wasting your time -- how about a cat video instead?"
                                } else {
                                    message
                                },
                                style = MaterialTheme.typography.titleLarge,
                                color = Color.White,
                                textAlign = TextAlign.Center,
                            )
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

        // End screen: related videos when the playhead reaches the end, behind a Settings
        // switch. Loads once via the source boundary; never more than one call per video.
        val endEnabled = remember {
            context.getSharedPreferences("parallax", Context.MODE_PRIVATE)
                .getBoolean("end_related_enabled", true)
        }
        var relatedToShow by remember(video.id) {
            mutableStateOf<List<com.presaince.parallax.data.model.VideoSummary>>(emptyList())
        }
        val atEnd = playbackState.durationMs > 0 &&
            playbackState.positionMs >= playbackState.durationMs - 500
        LaunchedEffect(atEnd, endEnabled) {
            if (endEnabled && atEnd && relatedToShow.isEmpty()) {
                when (val result = source.related(video.id)) {
                    is com.presaince.parallax.core.AppResult.Ok -> relatedToShow = result.value.take(10)
                    is com.presaince.parallax.core.AppResult.Err -> Unit
                }
            }
        }

        LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
            item(key = "titles") {
                // Advanced tint: the thumbnail's average colour washes behind the words when the
                // toggle is on. Sampling happens once per video on IO, never on the draw pass.
                val tintOn = remember {
                    context.getSharedPreferences("parallax", Context.MODE_PRIVATE)
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
                    Text(
                        text = loadState.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    // The channel is a door, not a caption: the name opens its page. The facts
                    // sit on the right, so the row stays one line under the title.
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
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
                        Text(
                            text = channelName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
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
                        val facts = buildList {
                            (loadState.views ?: video.viewCount)?.let {
                                add("${compactCount(it)} views")
                            }
                            (loadState.likes ?: video.likeCount)?.let {
                                add("${compactCount(it)} likes")
                            }
                            video.publishedAtMs?.let { add(timeAgo(it)) }
                        }.joinToString(" · ")
                        if (facts.isNotBlank()) {
                            Text(
                                text = facts,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
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
                        onSeek = { seconds -> playback.seekTo(seconds * 1000) },
                        onDismiss = { shareOpen = false },
                    )
                }
            }


            if (shownChapters.isNotEmpty()) {
                item(key = "chapters") {
                    ChaptersBlock(
                        chapters = shownChapters,
                        videoId = video.id,
                        thumbnailUrl = video.thumbnailUrl,
                        horizontal = chaptersHorizontal,
                        expanded = chaptersExpanded,
                        // The chapter the playhead is inside. Before the first mark there is
                        // none, which is honest: an intro is not the first chapter yet.
                        activeIndex = shownChapters.indexOfLast {
                            it.atSec <= playbackState.positionMs / 1000
                        },
                        onToggle = { chaptersExpanded = !chaptersExpanded },
                        onSeek = { seconds -> playback.seekTo(seconds * 1000) },
                    )
                }
            }

            if (playbackState.segments.isNotEmpty()) {
                item(key = "sponsor") {
                    SponsorSegmentsBlock(
                        segments = playbackState.segments,
                        positionMs = playbackState.positionMs,
                        videoId = video.id,
                        durationMs = playbackState.durationMs,
                        expanded = sponsorExpanded,
                        onToggle = { sponsorExpanded = !sponsorExpanded },
                        onSeek = { ms -> playback.seekTo(ms) },
                    )
                }
            }

            // The timestamp lines are already the chapter panel above. Leaving them in the
            // description put the same list on screen twice, one of them not tappable.
            if (descriptionBody.isNotBlank()) {
                item(key = "description") {
                    DescriptionBlock(
                        description = descriptionBody,
                        expanded = descriptionExpanded,
                        onToggle = { descriptionExpanded = !descriptionExpanded },
                        onSeek = { seconds -> playback.seekTo(seconds * 1000) },
                    )
                }
            }

            // My notes: a thin header with the record button always visible, above the comments.
            // The whole header is the tap target, not just the word: a collapsed section that
            // ignores a tap anywhere but the chevron is a section people think is broken.
            item(key = "notes-header") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .bringIntoViewRequester(notesRequester)
                        .clickable { notesExpanded = !notesExpanded }
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .heightIn(min = 44.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (notesCount > 0) "My notes ($notesCount)" else "My notes",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                    )
                    // The record button lives in the card once expanded: one microphone, not two.
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
                    }
                    Icon(
                        imageVector = if (notesExpanded) {
                            Icons.Default.KeyboardArrowUp
                        } else {
                            Icons.Default.KeyboardArrowDown
                        },
                        contentDescription = if (notesExpanded) "Collapse notes" else "Expand notes",
                        modifier = Modifier
                            .padding(start = 6.dp)
                            .clickable { notesExpanded = !notesExpanded },
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
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

            item(key = "comments") {
                CommentsSection(
                    viewModel = commentsViewModel,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }

            if (endEnabled && atEnd) {
                item(key = "related") {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Up next",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                        relatedToShow.forEach { next ->
                            com.presaince.parallax.ui.component.VideoRow(
                                video = next,
                                onClick = { onOpenVideo(next) },
                            )
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
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "Description",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
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
                tint = MaterialTheme.colorScheme.primary,
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
                    color = MaterialTheme.colorScheme.primary,
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
    val prefs = LocalContext.current.getSharedPreferences("parallax", Context.MODE_PRIVATE)
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
    segments: List<com.presaince.parallax.data.sponsorblock.Segment>,
    positionMs: Long,
    videoId: String,
    durationMs: Long,
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
                text = "Sponsor segments (${segments.size})",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = if (expanded) "Hide sponsor segments" else "Show sponsor segments",
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        if (expanded) {
            for (segment in segments) {
                val active = segment.contains(positionMs)
                // Frame at start + 5s so an imprecise timestamp lands on the segment's
                // likely first real picture. Short segments sample their midpoint instead.
                val thumbUrl = segmentThumbUrl(videoId, segment, durationMs)
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
                    AsyncImage(
                        model = thumbUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(width = 64.dp, height = 36.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .border(2.dp, segmentColor(segment.category), RoundedCornerShape(6.dp)),
                    )
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 8.dp)
                            .size(width = 4.dp, height = 28.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(segmentColor(segment.category)),
                    )
                    Text(
                        text = com.presaince.parallax.data.sponsorblock.SponsorCategories.label(segment.category),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (active) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        modifier = Modifier.weight(1f),
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

/** The frame 5 seconds into the segment, clamped to stay inside it. */
private fun segmentFrameMs(segment: com.presaince.parallax.data.sponsorblock.Segment): Long {
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
    segment: com.presaince.parallax.data.sponsorblock.Segment,
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
    expanded: Boolean,
    activeIndex: Int,
    onToggle: () -> Unit,
    onSeek: (Long) -> Unit,
) {
    val thumb = Thumbnails.forVideo(videoId, thumbnailUrl)
    // Real storyboard stills, one per chapter, loaded only when the row is on screen. The strip
    // is a LazyRow and the list is one Row per chapter, so Coil fetches on visibility.
    fun frame(index: Int): String = when (index % 4) {
        0 -> thumb
        1 -> "https://i.ytimg.com/vi/$videoId/mq1.jpg"
        2 -> "https://i.ytimg.com/vi/$videoId/mq2.jpg"
        else -> "https://i.ytimg.com/vi/$videoId/mq3.jpg"
    }
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Chapters (${chapters.size})",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = if (expanded) "Hide chapters" else "Show chapters",
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        if (expanded && horizontal) {
            LazyRow(
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
                            Text(
                                text = formatDuration(chapter.atSec),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(4.dp),
                            )
                        }
                        Text(
                            text = chapter.title,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (active) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            maxLines = 2,
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
                    Text(
                        text = formatDuration(chapter.atSec),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 10.dp).width(48.dp),
                    )
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

/** What the gesture HUD is saying: a time, a speed, or a level with its name on it. */
private data class Hud(
    val label: String? = null,
    /** A smaller line under the primary one, such as the position a scrub is heading for. */
    val secondary: String? = null,
    val fraction: Float? = null,
    val brightness: Boolean = false,
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
            text = "Jumped to ${formatDuration(undo.afterMs / 1000)}",
            style = MaterialTheme.typography.labelMedium,
            color = Color.White,
        )
        TextButton(onClick = onUndo) {
            Text("Undo", color = MaterialTheme.colorScheme.primary)
        }
    }
}

private fun segmentColor(category: String): Color = when (category) {
    com.presaince.parallax.data.sponsorblock.SponsorCategories.SPONSOR -> Color(0xFFFFC107)
    com.presaince.parallax.data.sponsorblock.SponsorCategories.SELF_PROMO -> Color(0xFF69F0AE)
    com.presaince.parallax.data.sponsorblock.SponsorCategories.INTERACTION -> Color(0xFFFF4081)
    com.presaince.parallax.data.sponsorblock.SponsorCategories.PREVIEW -> Color(0xFF4FC3F7)
    com.presaince.parallax.data.sponsorblock.SponsorCategories.MUSIC_OFFTOPIC -> Color(0xFFBA68C8)
    com.presaince.parallax.data.sponsorblock.SponsorCategories.FILLER -> Color(0xFF9575CD)
    else -> Color(0xFFBDBDBD)
}

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
 * The gesture readout. Volume and brightness get a capsule whose fill is the level, with no text.
 * A scrub or a skip gets a wide pill just above the finger that asked for it, or just below when
 * the finger is too near the top, carrying the whole video's progress in its bottom edge. Speed
 * sticks to the left edge at the finger's height, out of the way of the picture.
 */
@Composable
private fun HudPill(hud: Hud) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val level = hud.fraction
        val progress = hud.progress
        val anchorY = (hud.anchor?.y ?: 0.5f).coerceIn(0f, 1f)
        when {
            progress != null -> {
                val pillHeight = 48.dp
                val below = anchorY < 0.25f
                val raw = if (below) {
                    maxHeight * anchorY + 56.dp
                } else {
                    maxHeight * anchorY - pillHeight - 28.dp
                }
                val y = raw.coerceIn(8.dp, (maxHeight - pillHeight - 8.dp).coerceAtLeast(8.dp))
                ScrubPill(
                    label = hud.label.orEmpty(),
                    secondary = hud.secondary,
                    progress = progress,
                    modifier = Modifier.align(Alignment.TopCenter).offset(y = y),
                )
            }
            level != null -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                LevelPill(level, hud.brightness)
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

/** Volume and brightness: a vertical capsule whose fill is the level. No number, no word. */
@Composable
private fun LevelPill(level: Float, brightness: Boolean) {
    val shown by animateFloatAsState(
        targetValue = level.coerceIn(0f, 1f),
        animationSpec = tween(90),
        label = "level",
    )
    Box(
        modifier = Modifier
            .width(72.dp)
            .height(58.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xE6101014)),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(shown)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)),
        )
        Icon(
            painter = painterResource(if (brightness) R.drawable.ic_sun else R.drawable.ic_volume),
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(22.dp),
        )
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
    onOpenSpeedPopup: () -> Unit,
    onSpeedAdjust: (Float, Float) -> Unit,
    onSpeedEnd: () -> Unit,
    onSpeedHold: (Boolean) -> Unit,
    onToggleFullscreen: () -> Unit,
    onOpenBug: () -> Unit,
    showPlay: Boolean,
    /** Where the last tap landed, as a fraction of the picture: the play disc goes there. */
    playAt: Offset?,
    /** Auto or the chosen height, shown beside the speed as a readout of the setting. */
    qualityLabel: String,
    selectedQuality: Int,
    onPickQuality: (Int) -> Unit,
    speedPresets: List<Float>,
) {
    AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut()) {
        Box(modifier = Modifier.fillMaxSize()) {
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

            // The bug screen is reachable from the player too; the bar is hidden here.
            IconButton(
                onClick = onOpenBug,
                modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_bug),
                    contentDescription = "Bug report",
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
                    val y = if (fy > 0.72f) {
                        ((maxHeight - disc) / 2).coerceIn(0.dp, lowest)
                    } else {
                        (maxHeight * fy - disc / 2).coerceIn(0.dp, lowest)
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
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "${formatDuration(state.positionMs / 1000)} / " +
                            formatDuration(state.durationMs / 1000),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                        modifier = Modifier.weight(1f),
                    )
                    // The player's own position, not the last UI sample. Seeking is the slide
                    // gesture now, so the arrow buttons are gone.
                    var dial by remember { mutableStateOf<SpeedDial?>(null) }
                    var qualityMenuOpen by remember { mutableStateOf(false) }
                    val speedContext = androidx.compose.ui.platform.LocalContext.current
                    val speedView = androidx.compose.ui.platform.LocalView.current
                    fun speedTick(home: Boolean) {
                        // The constants path is silent on some builds; the vibrator path is
                        // direct, so the tick is felt: short for a step, longer at home (1.0x).
                        val vibrator = speedContext.getSystemService(android.content.Context.VIBRATOR_SERVICE) as? Vibrator
                        if (vibrator?.hasVibrator() == true) {
                            vibrator.vibrate(
                                VibrationEffect.createOneShot(
                                    if (home) 28L else 8L,
                                    if (home) 180 else 90,
                                ),
                            )
                        } else {
                            speedView.performHapticFeedback(
                                android.view.HapticFeedbackConstants.KEYBOARD_TAP,
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
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
                                            val tenths = (baseTenths + steps).coerceIn(3, 50)
                                            if (tenths != applied) {
                                                val home = tenths == 10
                                                applied = tenths
                                                dial = SpeedDial(tenths / 10f, change.position.y)
                                                // Small tick per step, heavier at home (1.0x).
                                                speedTick(home)
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
                                                onOpenSpeedPopup()
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
                            com.presaince.parallax.core.QUALITY_STOPS.forEach { stop ->
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
            // The shared text is only the link. A clip adds &end, which YouTube ignores but Parallax
            // reads, so the same link works for both.
            val shareText = if (end - start > 1f) {
                "$watchUrl&t=${start.toLong()}s&end=${end.toLong()}s"
            } else {
                "$watchUrl&t=${start.toLong()}s"
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

/**
 * Speed: a thin vertical panel that opens above the control you tapped. The track on the left is
 * the slider. Tapping a value picks it and closes at once; a drag previews a speed and only
 * commits when the finger lifts.
 */
@Composable
private fun SpeedPanel(
    speed: Float,
    presets: List<Float>,
    onSpeed: (Float) -> Unit,
    onDismiss: () -> Unit,
) {
    var preview by remember(speed) { mutableFloatStateOf(speed) }
    var dragBase by remember { mutableFloatStateOf(speed) }
    var accumulated by remember { mutableFloatStateOf(0f) }
    var lastTenths by remember { mutableIntStateOf((speed * 10).roundToInt()) }
    val panelContext = androidx.compose.ui.platform.LocalContext.current
    val panelView = androidx.compose.ui.platform.LocalView.current
    fun panelTick(home: Boolean) {
        val vibrator = panelContext.getSystemService(android.content.Context.VIBRATOR_SERVICE) as? Vibrator
        if (vibrator?.hasVibrator() == true) {
            vibrator.vibrate(
                VibrationEffect.createOneShot(
                    if (home) 28L else 8L,
                    if (home) 180 else 90,
                ),
            )
        } else {
            panelView.performHapticFeedback(
                android.view.HapticFeedbackConstants.KEYBOARD_TAP,
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0x33000000))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.CenterStart,
    ) {
        Surface(
            // The same capsule shape as the other readouts, with the level as a rising fill
            // rather than a thin track: one fill language across the whole player.
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .padding(start = 12.dp)
                .wrapContentWidth()
                // Swallow taps meant for the panel itself so the scrim does not close it.
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
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
                            val tenths = ((dragBase * 10).roundToInt() + steps).coerceIn(3, 50)
                            if (tenths != lastTenths) {
                                val home = tenths == 10
                                lastTenths = tenths
                                panelTick(home)
                            }
                            preview = tenths / 10f
                        },
                        onDragEnd = {
                            onSpeed(preview)
                            onDismiss()
                        },
                        onDragCancel = { preview = speed },
                    )
                },
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "%.1f".format(preview),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                presets.sortedDescending().forEach { preset ->
                    Text(
                        text = "%.1f".format(preset),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (kotlin.math.abs(preview - preset) < 0.05f) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier
                            .clickable {
                                // Pick it and get out of the way: a tap is a decision.
                                onSpeed(preset)
                                onDismiss()
                            }
                            .padding(vertical = 3.dp),
                    )
                }
            }
        }
    }
}

/** The finger's place on the speed pill while it is being dragged, in the pill's own pixels. */
private data class SpeedDial(val speed: Float, val fingerY: Float)

/** The speed list during a drag: thin, just the numbers, fastest at the top. */
@Composable
private fun SpeedLadder(presets: List<Float>, current: Float) {
    val nearest = presets.minByOrNull { kotlin.math.abs(it - current) }
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xD9000000))
            .padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        presets.sortedDescending().forEach { speed ->
            Text(
                text = "%.1f".format(speed),
                style = MaterialTheme.typography.labelSmall,
                color = if (speed == nearest) {
                    MaterialTheme.colorScheme.primary
                } else {
                    Color.White.copy(alpha = 0.75f)
                },
            )
        }
    }
}

/**
 * Centred on the pill, just under the finger. If the list would run off the bottom of the
 * window, it goes above the finger instead, so it never covers the finger.
 */
private class SpeedLadderPosition(private val fingerY: Float) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val x = anchorBounds.left + (anchorBounds.width - popupContentSize.width) / 2
        val gap = 12
        val below = anchorBounds.top + fingerY.roundToInt() + gap
        val y = if (below + popupContentSize.height <= windowSize.height) {
            below
        } else {
            anchorBounds.top + fingerY.roundToInt() - gap - popupContentSize.height
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

/** "-12s" / "+12s": the size of the jump is the headline, so the sign stays visible. */
private fun formatSeekDelta(deltaMs: Long): String {
    val seconds = deltaMs / 1000
    return when {
        seconds > 0 -> "+${seconds}s"
        seconds < 0 -> "\u2212${-seconds}s"
        else -> "0s"
    }
}

/** The scrub pill sits above the finger, or below it when the finger is too near the top. */
private fun scrubProgress(positionMs: Long, durationMs: Long): Float =
    if (durationMs <= 0L) 0f else (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
