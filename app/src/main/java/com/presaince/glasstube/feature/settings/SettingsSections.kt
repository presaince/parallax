/*
 * GlassTube — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.glasstube.feature.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.content.Context
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import com.presaince.glasstube.ui.component.LevelPill
import com.presaince.glasstube.ui.component.levelPercent
import com.presaince.glasstube.ui.component.LevelReadout
import com.presaince.glasstube.ui.component.readoutLeft
import com.presaince.glasstube.ui.component.readoutTop
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.presaince.glasstube.R
import com.presaince.glasstube.core.AppLog
import com.presaince.glasstube.core.QUALITY_STOPS
import com.presaince.glasstube.core.timeAgo
import com.presaince.glasstube.data.backup.BackupFiles
import com.presaince.glasstube.data.backup.FoundFile
import com.presaince.glasstube.data.auth.PostingManager
import com.presaince.glasstube.data.db.GlassTubeDb
import com.presaince.glasstube.data.library.ContentRepository
import com.presaince.glasstube.data.resync.ResyncSettings
import com.presaince.glasstube.data.resync.ResyncStore
import com.presaince.glasstube.data.source.flag
import com.presaince.glasstube.data.source.readContentRegions
import com.presaince.glasstube.data.sponsorblock.SponsorCategories
import com.presaince.glasstube.feature.player.gesture.SeekRate
import com.presaince.glasstube.playback.PlaybackController
import com.presaince.glasstube.ui.theme.Accents
import com.presaince.glasstube.ui.theme.ContrastPolicy
import com.presaince.glasstube.ui.theme.LocalAccentInk
import com.presaince.glasstube.ui.theme.LocalSurfaces
import com.presaince.glasstube.ui.theme.ThemeController
import com.presaince.glasstube.ui.theme.ThemeMode
import com.presaince.glasstube.ui.theme.contrastRatio
import com.presaince.glasstube.ui.theme.faded
import com.presaince.glasstube.ui.theme.glassTubeFilterChipColors
import com.presaince.glasstube.ui.theme.toHsl
import com.presaince.glasstube.ui.theme.worstContrast
import com.presaince.glasstube.ui.component.CardStyle
import com.presaince.glasstube.ui.component.CardLayout
import com.presaince.glasstube.ui.component.SettingsTile
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.roundToInt
import com.presaince.glasstube.ui.theme.accentTextColour

/** One page of Settings. The root list decides which; nothing else is on screen. */
@Composable
fun SettingsSectionScreen(
    section: SettingsSectionId,
    playback: PlaybackController,
    resyncStore: ResyncStore,
    posting: PostingManager,
    db: GlassTubeDb,
    recipes: com.presaince.glasstube.data.discover.RecipeRepository,
    content: ContentRepository,
    theme: ThemeController,
    auth: com.presaince.glasstube.data.auth.YouTubeAuth,
    onRegionChange: (List<String>) -> Unit,
    onOpenLogs: () -> Unit,
    onOpenVideo: (com.presaince.glasstube.data.model.VideoSummary) -> Unit,
    log: AppLog? = null,
) {
    when (section) {
        SettingsSectionId.APPEARANCE -> AppearanceSection(theme)
        SettingsSectionId.PLAYBACK -> PlaybackSection(playback)
        SettingsSectionId.FEEDBACK -> FeedbackSection()
        SettingsSectionId.REGION -> RegionSection(onRegionChange)
        SettingsSectionId.SPONSOR -> SponsorSection(playback)
        SettingsSectionId.ACCOUNTS -> AccountsSection(resyncStore, posting, auth)
        SettingsSectionId.DATA -> DataSection(
            db = db,
            recipes = recipes,
            theme = theme,
            playback = playback,
            content = content,
            resyncStore = resyncStore,
            onRegionChange = onRegionChange,
        )
        SettingsSectionId.DIAGNOSTICS -> DiagnosticsSection(onOpenLogs, log)
        // One About page, not two: the section is the whole thing.
        SettingsSectionId.ABOUT -> com.presaince.glasstube.feature.about.AboutScreen()
    }
}

/** One AI & accounts page: the model key and the posting keys live together. */
@Composable
private fun AccountsSection(
    resyncStore: ResyncStore,
    posting: PostingManager,
    auth: com.presaince.glasstube.data.auth.YouTubeAuth,
) {
    Page {
        Text(
            text = "One key powers AI mode: timestamp alignment, AI chapters, summaries and " +
                "search self-repair. Posting is the separate Google sign-in below. Both are " +
                "off by default.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Caption("AI")
        ResyncBody(resyncStore)
        Caption("Google posting")
        // PostingScreen is a full scroll page of its own; a fixed window keeps the two keys on
        // one page without nested unbounded scroll containers.
        Box(modifier = Modifier.fillMaxWidth().height(700.dp)) {
            PostingSection(posting, auth)
        }
    }
}

@Composable
private fun Page(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        content()
    }
}

@Composable
private fun AppearanceSection(theme: ThemeController) {
    val context = LocalContext.current
    // One copy of the card style for the page: the switches and the preview above them must agree
    // the moment a switch moves, not after a round trip.
    var cardStyle by remember { mutableStateOf(CardStyle.read(context)) }

    Page {
        Caption("Theme")
        ThemePicker(theme)

        // Accent is the second question, not the third: it is the other half of "how does this app
        // look", and it belongs next to the theme rather than after the card preview.
        Caption("Accent")
        AccentGrid(theme)
        // One line: the master and its three rhythms. Shuffle off mutes the others rather than
        // hiding them, so the row keeps its shape and the choices stay where they were.
        ShuffleRow(theme)

        // Preview is the third section, in its own card. It used to sit under Motion at the
        // bottom of the page, where the thing being described was never on screen with the
        // controls that change it.
        Caption("Preview")
        PreviewSection(
            style = cardStyle,
            onChange = { next ->
                cardStyle = next
                CardStyle.write(context, next)
            },
        )

        // Every theme, not just Glass: Light is where a warm accent sits at 2.4:1 as text, and Dark
        // is where the reader needs to see there is nothing to fix.
        Caption("Contrast")
        ContrastCard(theme)

        Caption("Motion")
        var animations by remember {
            mutableStateOf(
                context.getSharedPreferences("glasstube", Context.MODE_PRIVATE)
                    .getBoolean("animations_enabled", true),
            )
        }
        var videoTint by remember {
            mutableStateOf(
                context.getSharedPreferences("glasstube", Context.MODE_PRIVATE)
                    .getBoolean("theme_from_video", false),
            )
        }
        SwitchRow(
            title = "Tint from video",
            summary = "Advanced: tints the player page with the video's dominant colour. Off by default.",
            checked = videoTint,
            onCheckedChange = { on ->
                videoTint = on
                context.getSharedPreferences("glasstube", Context.MODE_PRIVATE)
                    .edit().putBoolean("theme_from_video", on).apply()
            },
        )
        var loadingStyle by remember {
            mutableIntStateOf(
                context.getSharedPreferences("glasstube", Context.MODE_PRIVATE)
                    .getInt("loading_style", 0),
            )
        }
        SwitchRow(
            title = "Animations",
            summary = "The small movements: the waiting picture, the preview sweep. Off keeps " +
                "everything still.",
            checked = animations,
            onCheckedChange = { on ->
                animations = on
                context.getSharedPreferences("glasstube", Context.MODE_PRIVATE)
                    .edit().putBoolean("animations_enabled", on).apply()
            },
        )
        // Its own subsection: what the wait looks like is a second question under Motion, not a
        // row of its own inside the switch's description.
        Caption("While a video loads")
        ChoiceChips(
            options = listOf(
                0 to "Kinetic",
                1 to "Still",
                3 to "Random",
                2 to "None",
            ),
            selected = loadingStyle,
            onPick = { style ->
                loadingStyle = style
                context.getSharedPreferences("glasstube", Context.MODE_PRIVATE)
                    .edit().putInt("loading_style", style).apply()
            },
        )
    }
}

/**
 * Contrast as one number, one sentence and three buttons. It is shown in every theme because Light
 * is where a warm accent can sit at 2.4:1 as text, and Dark is where the reader needs to know
 * there is nothing to fix. In Glass the same number covers the composited card, not the ideal token.
 */
@Composable
private fun ContrastCard(theme: ThemeController) {
    val scheme = MaterialTheme.colorScheme
    val surfaces = LocalSurfaces.current.ifEmpty { listOf(scheme.surface) }
    val rawInk = LocalAccentInk.current
    val ink = if (rawInk == Color.Unspecified) scheme.primary else rawInk

    val worst = minOf(
        worstContrast(scheme.onSurface, surfaces),
        worstContrast(scheme.onSurfaceVariant, surfaces),
        worstContrast(ink, surfaces),
        contrastRatio(scheme.onPrimary, scheme.primary),
    )
    val target = theme.contrast.ratio
    val holding = worst >= target
    val glass = theme.mode == ThemeMode.GLASS
    // Whether the policy actually moved anything. When it did not, the card says so in words: an
    // identical result for three settings looks broken unless the screen names the reason. The
    // threshold is a perceptual one — a 2%-lightness nudge is not a change anyone can see.
    val inkMoved = kotlin.math.abs(
        ink.toHsl().third - scheme.primary.toHsl().third,
    ) > 0.015f
    val aurora = remember(theme.accent, theme.dark, theme.solidSurfaces, theme.contrast) {
        if (glass) {
            com.presaince.glasstube.ui.theme.auroraScale(
                theme.accent,
                theme.dark,
                theme.solidSurfaces,
                theme.contrast,
            )
        } else {
            1f
        }
    }

    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
            .clip(shape)
            .border(1.dp, MaterialTheme.colorScheme.surfaceVariant, shape)
            .background(MaterialTheme.colorScheme.surfaceVariant.faded(0.35f))
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        // Number and control on one line: the reading belongs beside the thing that changes it, and
        // the card is two lines tall instead of four.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "%.1f:1".format(worst),
                style = MaterialTheme.typography.titleLarge,
                color = if (holding) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.error
                },
                modifier = Modifier.weight(1f),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ContrastPolicy.all.forEach { policy ->
                    PickChip(
                        label = policy.label,
                        selected = theme.contrast == policy,
                        onClick = { theme.chooseContrast(policy) },
                    )
                }
            }
        }

        // One line, no wrapping: every branch is written to fit beside the number's width. What it
        // says in Dark is the point of the card — that the setting is already satisfied.
        Text(
            text = when {
                !theme.contrast.enforced -> "Off — the palette as drawn."
                !holding -> "%.1f short of %.1f:1, and nothing here can reach it.".format(
                    target - worst,
                    target,
                )
                glass && aurora < 0.999f -> "Glow at %d%% keeps the page readable.".format(
                    (aurora * 100).roundToInt(),
                )
                inkMoved -> "Accent labels use a darker shade of the same hue."
                else -> "Nothing to change — %s already passes %.1f:1.".format(
                    when {
                        glass -> "glass"
                        theme.dark -> "Dark"
                        else -> "Light"
                    },
                    target,
                )
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

/** The accent master and its three rhythms, on one line. */
@Composable
private fun ShuffleRow(theme: ThemeController) {
    val cadence = theme.shuffle
    val on = cadence != com.presaince.glasstube.ui.theme.ShuffleCadence.OFF
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PickChip(
            label = "Shuffle",
            selected = on,
            onClick = {
                theme.chooseShuffle(
                    if (on) {
                        com.presaince.glasstube.ui.theme.ShuffleCadence.OFF
                    } else {
                        com.presaince.glasstube.ui.theme.ShuffleCadence.DAILY
                    },
                )
            },
        )
        PickChip(
            label = "Daily",
            selected = cadence == com.presaince.glasstube.ui.theme.ShuffleCadence.DAILY,
            muted = !on,
            onClick = { theme.chooseShuffle(com.presaince.glasstube.ui.theme.ShuffleCadence.DAILY) },
        )
        PickChip(
            label = "Hourly",
            selected = cadence == com.presaince.glasstube.ui.theme.ShuffleCadence.HOURLY,
            muted = !on,
            onClick = { theme.chooseShuffle(com.presaince.glasstube.ui.theme.ShuffleCadence.HOURLY) },
        )
        PickChip(
            label = "Focus",
            selected = cadence == com.presaince.glasstube.ui.theme.ShuffleCadence.ON_OPEN,
            muted = !on,
            onClick = { theme.chooseShuffle(com.presaince.glasstube.ui.theme.ShuffleCadence.ON_OPEN) },
        )
    }
}

/**
 * A small tap button with the selected state drawn on it. FilterChip brings its own padding and a
 * minimum width that would not fit four on one line, which is the whole point of this row.
 */
@Composable
private fun PickChip(
    label: String,
    selected: Boolean,
    muted: Boolean = false,
    onClick: () -> Unit,
) {
    val ink = LocalAccentInk.current
    val accentText = if (ink == Color.Unspecified) MaterialTheme.colorScheme.primary else ink
    val tint = when {
        selected -> accentText
        // Muted is a container difference, not a faded label: a chip that can still be tapped has
        // to be readable, and 38% alpha measured 1.9:1 on the glass card.
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(
                when {
                    selected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                    muted -> Color.Transparent
                    else -> MaterialTheme.colorScheme.surfaceVariant.faded(0.35f)
                },
            )
            .clickable(onClick = onClick)
            .heightIn(min = 40.dp)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = tint,
            maxLines = 1,
        )
    }
}


/** A labelled row of buttons. The shape for a choice of two or three, not an on/off switch. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun <T> ChoiceChips(
    options: List<Pair<T, String>>,
    selected: T,
    onPick: (T) -> Unit,
    title: String? = null,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        if (title != null) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            options.forEach { (value, label) ->
                FilterChip(
                    selected = value == selected,
                    onClick = { onPick(value) },
                    label = { Text(label) },
                    colors = glassTubeFilterChipColors(),
                )
            }
        }
    }
}

/**
 * The quality track as water instead of a line: a filled wave, small and calm at Auto, rising into
 * a parabola at AI sim. Same six stops, same snap, but the shape says how far past a screen the
 * setting is claiming to go. The whole box is the touch target, so a thumb anywhere lands on a stop.
 */
@Composable
private fun WaveQualitySlider(
    index: Int,
    count: Int,
    onIndex: (Int) -> Unit,
) {
    var widthPx by remember { mutableStateOf(1f) }
    val accent = MaterialTheme.colorScheme.primary
    val rest = MaterialTheme.colorScheme.onSurfaceVariant
    val fraction = index.toFloat() / (count - 1).coerceAtLeast(1)

    fun pick(x: Float) {
        if (widthPx <= 1f) return
        val next = ((x / widthPx) * (count - 1)).roundToInt().coerceIn(0, count - 1)
        if (next != index) onIndex(next)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .height(44.dp)
            .onSizeChanged { widthPx = it.width.toFloat().coerceAtLeast(1f) }
            .pointerInput(count, widthPx, index) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    // A drag moves by steps from where it started, not to an absolute x: with
                    // eight stops on one screen width, an absolute map makes 240 a target the
                    // thumb always overshoots. A tap still jumps to the nearest stop.
                    val startIndex = index
                    val startX = down.position.x
                    var moved = false
                    var lastX = startX
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        change.consume()
                        val dx = change.position.x - lastX
                        lastX = change.position.x
                        if (!moved && kotlin.math.abs(change.position.x - startX) > 6f) {
                            moved = true
                        }
                        if (moved) {
                            val stepPx = (widthPx / (count - 1).coerceAtLeast(1)).coerceAtLeast(48f)
                            val steps = ((lastX - startX) / stepPx).roundToInt()
                            val next = (startIndex + steps).coerceIn(0, count - 1)
                            if (next != index) onIndex(next)
                        } else {
                            pick(change.position.x)
                        }
                        if (!change.pressed) {
                            if (!moved) pick(change.position.x)
                            break
                        }
                    }
                }
            },
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val base = h * 0.70f
            val progressX = w * fraction
            val step = w / (count - 1).coerceAtLeast(1)

            fun waveY(x: Float): Float {
                val t = (x / w).coerceIn(0f, 1f)
                // A growing value, not a wave: flat at Auto, climbing by AI sim.
                val climb = kotlin.math.exp(t * 2.2f) - 1f
                return base - (h * 0.28f * climb) / (kotlin.math.exp(2.2f) - 1f)
            }

            // The whole track is water, faint past the thumb and solid before it. A line under a
            // wave looked like a slider with a decoration; a filled shape is the setting itself.
            var x = 0f
            val water = Path()
            water.moveTo(0f, base)
            x = 0f
            while (x <= w) {
                water.lineTo(x, waveY(x))
                x += 3f
            }
            water.lineTo(w, base)
            water.close()
            drawPath(water, rest.copy(alpha = 0.10f))

            // The chosen part: filled water under the wave up to the thumb.
            val filled = Path()
            filled.moveTo(0f, base)
            x = 0f
            while (x <= progressX) {
                filled.lineTo(x, waveY(x))
                x += 3f
            }
            filled.lineTo(progressX, waveY(progressX))
            filled.lineTo(progressX, base)
            filled.close()
            drawPath(
                filled,
                androidx.compose.ui.graphics.Brush.verticalGradient(
                    listOf(accent.copy(alpha = 0.55f), accent.copy(alpha = 0.12f)),
                ),
            )

            // Stops, and the thumb as a short vertical mark rather than a disc.
            for (i in 0 until count) {
                val sx = step * i
                drawCircle(
                    color = if (i <= index) accent else rest.copy(alpha = 0.45f),
                    radius = if (i == index) 4.5f else 2.5f,
                    center = Offset(sx, base + h * 0.10f),
                )
            }
            drawLine(
                color = accent,
                start = Offset(progressX, base - h * 0.34f),
                end = Offset(progressX, base + h * 0.16f),
                strokeWidth = 3f,
            )
        }
    }
}


/**
 * The preview card: the same card the lists draw, at full width. The layout buttons sit above it
 * and the card itself lives in a fixed-height box, so switching between Side, Classic and Minimal
 * cannot move the control that was just tapped out from under the finger. Anything shorter than the
 * tallest layout is simply top-aligned inside the box.
 */
@Composable
private fun PreviewSection(
    style: CardStyle,
    onChange: (CardStyle) -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.faded(0.30f),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CardLayout.entries.forEach { layout ->
                    PickChip(
                        label = layout.label,
                        selected = style.layout == layout,
                        onClick = { onChange(style.copy(layout = layout)) },
                    )
                }
            }
            Box(
                modifier = Modifier.fillMaxWidth().height(PREVIEW_HEIGHT),
                contentAlignment = Alignment.TopStart,
            ) {
                PreviewCard(style = style)
            }
            FactButtons(style = style, onChange = onChange)
        }
    }
}

/**
 * The height the preview box always keeps: the tallest layout, Classic, with three title lines and
 * one meta line. A shorter card leaves blank space rather than pulling the buttons up.
 */
private val PREVIEW_HEIGHT = 300.dp

/** One fact a card can carry. Tapping turns it on or off; it is a button, not a switch. */
private data class Fact(
    val label: String,
    val on: Boolean,
    val set: (Boolean) -> Unit,
)

@Composable
private fun FactButtons(style: CardStyle, onChange: (CardStyle) -> Unit) {
    val facts = listOf(
        Fact("Title", style.title) { onChange(style.copy(title = it)) },
        Fact("Channel", style.channel) { onChange(style.copy(channel = it)) },
        Fact("How long ago", style.age) { onChange(style.copy(age = it)) },
        Fact("Views", style.views) { onChange(style.copy(views = it)) },
        Fact("Likes", style.likes) { onChange(style.copy(likes = it)) },
        Fact("Duration", style.duration) { onChange(style.copy(duration = it)) },
        Fact("Watch count", style.watches) { onChange(style.copy(watches = it)) },
        Fact("Comments", style.comments) { onChange(style.copy(comments = it)) },
        Fact("Progress", style.progress) { onChange(style.copy(progress = it)) },
        Fact("My notes", style.notes) { onChange(style.copy(notes = it)) },    )
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        facts.chunked(2).forEach { pair ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                pair.forEach { fact ->
                    FactButton(
                        label = fact.label,
                        on = fact.on,
                        modifier = Modifier.weight(1f),
                        onClick = { fact.set(!fact.on) },
                    )
                }
                // The last row has one button; the empty half keeps the grid's rhythm.
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun FactButton(
    label: String,
    on: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(
                if (on) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                } else {
                    MaterialTheme.colorScheme.surface.copy(alpha = 0.45f)
                },
            )
            .then(
                if (on) {
                    Modifier
                } else {
                    Modifier.border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                        shape = shape,
                    )
                },
            )
            .clickable(onClick = onClick)
            .heightIn(min = 44.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (on) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            maxLines = 1,
        )
    }
}

/**
 * The card as the lists draw it, in the chosen layout. The gradient stands in for a thumbnail so
 * the preview follows the theme and the accent, and the play mark says what it is.
 */
@Composable
internal fun PreviewCard(style: CardStyle) {
    val meta = buildList {
        if (style.channel) add("Channel name")
        if (style.age) add("3h ago")
        if (style.views) add("128K views")
        if (style.likes) add("4.2K likes")
        if (style.watches) add("watched 3 times")
        if (style.comments) add("42 comments")
    }.joinToString(" · ")
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(modifier = Modifier.padding(12.dp)) {
            when (style.layout) {
                CardLayout.CLASSIC -> Column {
                    PreviewThumb(style = style, modifier = Modifier.fillMaxWidth())
                    PreviewWords(
                        title = "Video title in the current theme",
                        meta = meta,
                        showTitle = style.title,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }

                CardLayout.SIDE -> Row(verticalAlignment = Alignment.Top) {
                    PreviewThumb(style = style, modifier = Modifier.weight(0.40f))
                    PreviewWords(
                        title = "Video title in the current theme",
                        meta = meta,
                        showTitle = style.title,
                        modifier = Modifier.weight(0.60f).padding(start = 12.dp),
                    )
                }

                CardLayout.MINIMAL -> Row(verticalAlignment = Alignment.CenterVertically) {
                    PreviewThumb(style = style, modifier = Modifier.weight(0.26f))
                    PreviewWords(
                        title = "Video title in the current theme",
                        meta = meta,
                        showTitle = style.title,
                        modifier = Modifier.weight(0.74f).padding(start = 10.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun PreviewThumb(style: CardStyle, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(10.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                        MaterialTheme.colorScheme.tertiary.copy(alpha = 0.22f),
                        MaterialTheme.colorScheme.surfaceVariant,
                    ),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Default.PlayArrow,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
            modifier = Modifier.size(32.dp),
        )
        if (style.duration) {
            Surface(
                color = Color.Black.copy(alpha = 0.72f),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp),
            ) {
                Text(
                    text = "12:04",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                )
            }
        }
        if (style.notes) {
            Surface(
                color = Color(0xCC000000),
                shape = RoundedCornerShape(50),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp),
            ) {
                Text(
                    text = "3",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                )
            }
        }
        if (style.progress) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(bottom = 0.dp),
            ) {
                // The progress line: a third across, on a translucent track, over the picture.
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.35f)
                        .height(3.dp)
                        .background(MaterialTheme.colorScheme.primary),
                )
            }
        }
    }
}

@Composable
private fun PreviewWords(
    title: String,
    meta: String,
    showTitle: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        if (showTitle) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 3,
            )
        }
        if (meta.isNotEmpty()) {
            Text(
                text = meta,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                modifier = Modifier.padding(top = if (showTitle) 4.dp else 0.dp),
            )
        }
    }
}

/**
 * The Glass theme's own button: just Glass, no Solid word. The whole tile is the fill, in the
 * volume/brightness language: the tile's color rises from the bottom with the level, 0..1,
 * with the glass icon riding on it like the other theme tiles. It slides up and down anywhere on
 * the tile. Tapping turns Glass on from another theme at the remembered level; off is picking
 * another theme. Default 0.42.
 */
@Composable
private fun androidx.compose.foundation.layout.RowScope.GlassTile(theme: ThemeController) {
    val active = theme.mode == ThemeMode.GLASS
    val shape = RoundedCornerShape(20.dp)
    val level = theme.glassLevel.coerceIn(0f, 1f)
    var tileHeightPx by remember { mutableFloatStateOf(0f) }
    // The whole tile is the slider: no inner capsule, no number. The fill's height is the
    // reading, and dragging anywhere on the tile sets it. The gesture is keyless on purpose:
    // keying it on the level recreated the detector mid-drag, which is the flaky feel.
    Box(
        modifier = Modifier
            .weight(1f)
            .heightIn(min = 74.dp)
            .clip(shape)
            .border(
                width = if (active) 2.dp else 1.dp,
                color = if (active) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
                shape = shape,
            )
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
            .onSizeChanged { tileHeightPx = it.height.toFloat() }
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = { },
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        if (tileHeightPx > 1f) {
                            // Accumulate like the volume/brightness pills: the level moves
                            // with the drag, not to the finger's absolute position.
                            val delta = -dragAmount / tileHeightPx
                            theme.chooseGlassLevel(
                                (theme.glassLevel + delta).coerceIn(0f, 1f),
                                persist = false,
                            )
                        }
                    },
                    onDragEnd = { theme.chooseGlassLevel(theme.glassLevel) },
                    onDragCancel = { theme.chooseGlassLevel(theme.glassLevel) },
                )
            }
            .clickable {
                theme.chooseSolidSurfaces(false)
                theme.chooseMode(ThemeMode.GLASS)
            },
        contentAlignment = Alignment.Center,
    ) {
        // Same fill as the volume/brightness pills: LevelPill owns the fill, the tile is its
        // frame. The icon and word ride centred on the fill rather than a custom bar, with the
        // level's own percentage above them, as every level pill carries its reading at the top.
        // No fill when Glass is not selected; the gap to the border appears when it is.
        LevelPill(
            level = if (active) level else 0f,
            width = 72.dp,
            height = 58.dp,
            fillColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
            trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
            modifier = if (active) Modifier.padding(4.dp) else Modifier,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (active) {
                    Text(
                        text = levelPercent(level),
                        style = MaterialTheme.typography.labelSmall,
                        color = tileInk().copy(alpha = 0.72f),
                    )
                    Spacer(Modifier.height(2.dp))
                }
                Icon(
                    painter = painterResource(R.drawable.ic_glass),
                    contentDescription = "Glass theme",
                    modifier = Modifier.size(if (active) 20.dp else 24.dp),
                    tint = if (active) {
                        tileInk()
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                Spacer(Modifier.height(if (active) 2.dp else 6.dp))
                Text(
                    text = "Glass",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (active) {
                        tileInk()
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}

@Composable
private fun SurfaceHalf(
    label: String,
    selected: Boolean,
    active: Boolean,
    brush: Brush,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(brush)
            .then(
                if (selected) {
                    Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.20f))
                } else {
                    Modifier
                },
            )
            .clickable(onClick = onClick)
            .heightIn(min = 30.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = when {
                active && selected -> tileInk()
                selected -> MaterialTheme.colorScheme.onSurface
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
            maxLines = 1,
        )
    }
}

/** Accent text for a selected tile: the picked hue, walked until it reads on this surface. */
@Composable
private fun tileInk(): Color = com.presaince.glasstube.ui.theme.accentTextColour()

@Composable
private fun ThemePicker(theme: ThemeController) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ThemeMode.entries.forEach { mode ->
            if (mode == ThemeMode.GLASS) {
                // The Glass button is the surface control. One tile cut across: Glass on top,
                // Solid underneath, and the chosen half tinted. Choosing Glass any other way
                // would put the same question on the page twice.
                GlassTile(theme)
                return@forEach
            }
            val selected = theme.mode == mode
            val tileShape = RoundedCornerShape(20.dp)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(tileShape)
                    .border(
                        width = if (selected) 2.dp else 1.dp,
                        color = if (selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                        shape = tileShape,
                    )
                    .background(
                        if (selected) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                        } else {
                            Color.Transparent
                        },
                    )
                    .clickable { theme.chooseMode(mode) }
                    .padding(vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    painter = painterResource(
                        when (mode) {
                            ThemeMode.SYSTEM -> R.drawable.ic_auto
                            ThemeMode.DARK -> R.drawable.ic_moon
                            ThemeMode.LIGHT -> R.drawable.ic_sun
                            ThemeMode.GLASS -> R.drawable.ic_glass
                        },
                    ),
                    contentDescription = when (mode) {
                        ThemeMode.SYSTEM -> "Follow the system theme"
                        ThemeMode.DARK -> "Dark theme"
                        ThemeMode.LIGHT -> "Light theme"
                        ThemeMode.GLASS -> "Glass theme"
                    },
                    tint = if (selected) {
                        tileInk()
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = when (mode) {
                        ThemeMode.SYSTEM -> "System"
                        ThemeMode.DARK -> "Dark"
                        ThemeMode.LIGHT -> "Light"
                        ThemeMode.GLASS -> "Glass"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = if (selected) {
                        tileInk()
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}

@Composable
internal fun AccentGrid(theme: ThemeController) {
    val context = LocalContext.current
    // The phone's own colour, read once per composition: a dot of the actual primary, so the
    // choice shows what it will do rather than describing it.
    val system = remember(context) { Accents.system(context) }
    val rows = Accents.all.chunked(6)
    Column(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
        rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                row.forEach { accent ->
                    val selected = accent.name == theme.accent.name
                    val index = Accents.all.indexOf(accent) + 1
                    Box(
                        // The whole cell is the target, not the 38 dp swatch: a colour is a
                        // 48 dp touch target even when the dot inside it is smaller.
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .clickable { theme.chooseAccent(accent) }
                            .semantics {
                                contentDescription = if (selected) {
                                    "Accent $index, selected"
                                } else {
                                    "Accent $index"
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(accent.darkPrimary)
                                .border(
                                    width = if (selected) 3.dp else 1.dp,
                                    color = if (selected) {
                                        MaterialTheme.colorScheme.onBackground
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant
                                    },
                                    shape = CircleShape,
                                ),
                        )
                    }
                }
            }
        }
        // Not a nineteenth dot: the wheel is a grid of hues, and this is not one of them — it is
        // the phone's palette, which can be any hue and moves with the wallpaper. Its own row,
        // with the real primary as the dot, keeps that difference visible.
        val systemSelected = theme.accent.name == Accents.SYSTEM
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp)
                .height(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .clickable { theme.chooseAccent(system) }
                .semantics {
                    contentDescription = if (systemSelected) {
                        "System colour, selected"
                    } else {
                        "System colour"
                    }
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(system.darkPrimary)
                    .border(
                        width = if (systemSelected) 3.dp else 1.dp,
                        color = if (systemSelected) {
                            MaterialTheme.colorScheme.onBackground
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                        shape = CircleShape,
                    ),
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = "System colour",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            if (systemSelected) {
                Spacer(Modifier.weight(1f))
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = accentTextColour(),
                )
            }
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun PlaybackSection(playback: PlaybackController) {
    val context = LocalContext.current
    var hideLive by remember {
        mutableStateOf(
            context.getSharedPreferences("glasstube", android.content.Context.MODE_PRIVATE)
                .getBoolean("hide_live", false),
        )
    }
    var speedPresets by remember { mutableStateOf(playback.speedPresets()) }
    var pipOnLeave by remember {
        mutableStateOf(
            context.getSharedPreferences("glasstube", android.content.Context.MODE_PRIVATE)
                .getBoolean("pip_on_leave", true),
        )
    }
    var progressInFullscreen by remember {
        mutableStateOf(
            context.getSharedPreferences("glasstube", android.content.Context.MODE_PRIVATE)
                .getBoolean("progress_in_fullscreen", true),
        )
    }
    var notificationRestart by remember {
        mutableStateOf(
            context.getSharedPreferences("glasstube", android.content.Context.MODE_PRIVATE)
                .getBoolean("notification_restart", true),
        )
    }
    var chaptersHorizontal by remember {
        mutableStateOf(
            context.getSharedPreferences("glasstube", android.content.Context.MODE_PRIVATE)
                .getBoolean("chapters_horizontal", true),
        )
    }
    var chapterTimestamps by remember {
        mutableStateOf(
            context.getSharedPreferences("glasstube", android.content.Context.MODE_PRIVATE)
                .getBoolean("chapter_timestamps", true),
        )
    }
    var labelsRight by remember {
        mutableStateOf(
            context.getSharedPreferences("glasstube", android.content.Context.MODE_PRIVATE)
                .getBoolean("player_labels_right", false),
        )
    }
    var endRelated by remember {
        mutableStateOf(
            context.getSharedPreferences("glasstube", android.content.Context.MODE_PRIVATE)
                .getBoolean("end_related_enabled", true),
        )
    }
    var seekBase by remember {
        mutableStateOf(
            context.getSharedPreferences("glasstube", android.content.Context.MODE_PRIVATE)
                .getInt("seek_base_seconds", 5),
        )
    }
    var watchThreshold by remember { mutableStateOf(playback.watchThreshold()) }

    Page {
        Caption("Gestures")
        GestureTutorial()

        SwitchRow(
            title = "Restart button in the notification",
            summary = "The far-left media button, which jumps back to the start. Off removes it " +
                "and leaves the notification to play, pause and seek. Takes effect from the next " +
                "playback session.",
            checked = notificationRestart,
            onCheckedChange = { enabled ->
                notificationRestart = enabled
                context.getSharedPreferences("glasstube", android.content.Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean("notification_restart", enabled)
                    .apply()
            },
        )

        SwitchRow(
            title = "Progress line in fullscreen",
            summary = "The 2dp line at the bottom of the picture. Off means a completely clean " +
                "picture when the phone is turned or fullscreen is entered.",
            checked = progressInFullscreen,
            onCheckedChange = { enabled ->
                progressInFullscreen = enabled
                context.getSharedPreferences("glasstube", android.content.Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean("progress_in_fullscreen", enabled)
                    .apply()
            },
        )

        SwitchRow(
            title = "Pop out when you leave the app",
            summary = "Keeps the picture in a floating window while a video plays. Off means " +
                "leaving the app simply pauses nothing and hides the video.",
            checked = pipOnLeave,
            onCheckedChange = { enabled ->
                pipOnLeave = enabled
                context.getSharedPreferences("glasstube", android.content.Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean("pip_on_leave", enabled)
                    .apply()
            },
        )

        SwitchRow(
            title = "Related at the end",
            summary = "Shows Up next under the comments while the video plays and on the end " +
                "screen. Off means the page simply ends.",
            checked = endRelated,
            onCheckedChange = { enabled ->
                endRelated = enabled
                context.getSharedPreferences("glasstube", android.content.Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean("end_related_enabled", enabled)
                    .apply()
            },
        )

        SwitchRow(
            title = "Chapters as a scrolling row",
            summary = "Off shows the chapters as a plain list. On shows them in a row you swipe " +
                "sideways, with a small thumbnail for each.",
            checked = chaptersHorizontal,
            onCheckedChange = { enabled ->
                chaptersHorizontal = enabled
                context.getSharedPreferences("glasstube", android.content.Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean("chapters_horizontal", enabled)
                    .apply()
            },
        )

        SwitchRow(
            title = "Chapter timestamps",
            summary = "The time beside each chapter. Off leaves the titles alone, and the " +
                "timestamps in the description are untouched either way.",
            checked = chapterTimestamps,
            onCheckedChange = { enabled ->
                chapterTimestamps = enabled
                context.getSharedPreferences("glasstube", android.content.Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean("chapter_timestamps", enabled)
                    .apply()
            },
        )

        SwitchRow(
            title = "Section labels on the right",
            summary = "Description, Chapters, My notes, Comments and Up next. Off puts them on " +
                "the left, in line with the text they name, which is where the eye already is.",
            checked = labelsRight,
            onCheckedChange = { enabled ->
                labelsRight = enabled
                context.getSharedPreferences("glasstube", android.content.Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean("player_labels_right", enabled)
                    .apply()
            },
        )

        SwitchRow(
            title = "Hide live and upcoming",
            summary = "Leaves them out of the feed, search and channel pages.",
            checked = hideLive,
            onCheckedChange = { enabled ->
                hideLive = enabled
                context.getSharedPreferences("glasstube", android.content.Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean("hide_live", enabled)
                    .apply()
            },
        )

        // How far one swipe moves the picture. The rate climbs as the finger travels, so this is
        // the precision at the start, not the speed of the whole gesture.
        Caption("Swipe to seek")
        Text(
            text = "What the first tenth of a sideways swipe moves. The player speeds up as the " +
                "finger keeps going, so a long video stays reachable.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf(2 to "Gentle · 2s", 5 to "Normal · 5s", 10 to "Quick · 10s").forEach { (seconds, label) ->
                FilterChip(
                    selected = seekBase == seconds,
                    onClick = {
                        seekBase = seconds
                        context.getSharedPreferences("glasstube", android.content.Context.MODE_PRIVATE)
                            .edit()
                            .putInt("seek_base_seconds", seconds)
                            .apply()
                    },
                    label = { Text(label) },
                    colors = glassTubeFilterChipColors(),
                )
            }
        }

        // How much playing counts an open as a watch. Opens enter History at once;
        // this is only when the same open is also stamped watched.
        Caption("Count a watch after")
        Text(
            text = "History records every open at once. This only decides when an open is " +
                "also counted as a watch. 30 seconds is the usual default; shorter values " +
                "count quick opens too.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf(
                "30s" to "30s",
                "60s" to "1 min",
                "300s" to "5 min",
                "600s" to "10 min",
            ).forEach { (token, label) ->
                FilterChip(
                    selected = watchThreshold == token,
                    onClick = {
                        watchThreshold = token
                        playback.setWatchThreshold(token)
                    },
                    label = { Text(label, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
                    colors = glassTubeFilterChipColors(),
                    modifier = Modifier.width(80.dp),
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf(
                "10%" to "10%",
                "25%" to "25%",
                "50%" to "50%",
                "90%" to "90%",
            ).forEach { (token, label) ->
                FilterChip(
                    selected = watchThreshold == token,
                    onClick = {
                        watchThreshold = token
                        playback.setWatchThreshold(token)
                    },
                    label = { Text(label, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
                    colors = glassTubeFilterChipColors(),
                    modifier = Modifier.width(80.dp),
                )
            }
        }

        // "Price is right" speed editor: pick the numbers the slider offers.
        Caption("Video quality")
        Text(
            text = "The ceiling for new videos. Auto takes the best the phone can decode; the " +
                "rest is a promise the video may not keep.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val qualityState = remember {
            mutableStateOf(
                context.getSharedPreferences("glasstube", android.content.Context.MODE_PRIVATE)
                    .getInt("video_quality", 0),
            )
        }
        val qualityIndex = QUALITY_STOPS.indexOfFirst { it.height == qualityState.value }
            .coerceAtLeast(0)
        val qualityStop = QUALITY_STOPS[qualityIndex]
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                shape = RoundedCornerShape(50),
            ) {
                Text(
                    text = qualityStop.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = accentTextColour(),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                )
            }
            if (qualityStop.note != null) {
                Text(
                    text = qualityStop.note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 10.dp),
                )
            }
        }
        // One track, six stops, Auto pinned at the left end but still part of the drag. The track
        // is a filled wave rather than a line: flat at the start, and by AI sim the amplitude has
        // climbed into a parabola, because that stop is the one that stops being measurable.
        WaveQualitySlider(
            index = qualityIndex,
            count = QUALITY_STOPS.size,
            onIndex = { next ->
                if (QUALITY_STOPS[next].height != qualityState.value) {
                    qualityState.value = QUALITY_STOPS[next].height
                    context.getSharedPreferences("glasstube", android.content.Context.MODE_PRIVATE)
                        .edit()
                        .putInt("video_quality", QUALITY_STOPS[next].height)
                        .apply()
                }
            },
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            // The labels are buttons, not a legend: on a narrow row the wave is hard to hit at
            // exactly one stop, and 480 is the one people ask for.
            QUALITY_STOPS.forEachIndexed { index, stop ->
                Text(
                    text = stop.short,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (index == qualityIndex) {
                        accentTextColour()
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier
                        .clickable {
                            if (stop.height != qualityState.value) {
                                qualityState.value = stop.height
                                context.getSharedPreferences("glasstube", android.content.Context.MODE_PRIVATE)
                                    .edit()
                                    .putInt("video_quality", stop.height)
                                    .apply()
                            }
                        }
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                )
            }
        }

        Caption("Stream cache")
        Text(
            text = "The bytes you have just watched are kept on disk, so reopening a video " +
                "starts at once while fresh data loads. Off keeps nothing. Turning it on " +
                "applies to the next video; changing the size applies the next time the app " +
                "starts.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        var cacheMb by remember { mutableStateOf(playback.streamCacheMb()) }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf(
                1024 to "1GB",
                2048 to "2GB",
                4096 to "4GB",
                0 to "Off",
            ).forEach { (mb, label) ->
                FilterChip(
                    selected = cacheMb == mb,
                    onClick = {
                        cacheMb = mb
                        playback.setStreamCacheMb(mb)
                    },
                    label = { Text(label, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
                    colors = glassTubeFilterChipColors(),
                    modifier = Modifier.width(80.dp),
                )
            }
        }

        Caption("Speeds in the slider")
        Text(
            text = "Tap a speed to offer it. The slider also steps in 0.1 either way, whatever " +
                "this list says.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            SPEED_STANDARD.forEach { speed ->
                SpeedChoice(speed, speedPresets) { chosen ->
                    val next = if (chosen) speedPresets - speed else speedPresets + speed
                    if (next.isNotEmpty()) {
                        speedPresets = next.sorted()
                        playback.setSpeedPresets(next)
                    }
                }
            }
        }
        // 3x, 4x and 5x sit together on one line under the 0.1 ladder.
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            SPEED_STRETCH.forEach { speed ->
                SpeedChoice(speed, speedPresets) { chosen ->
                    val next = if (chosen) speedPresets - speed else speedPresets + speed
                    if (next.isNotEmpty()) {
                        speedPresets = next.sorted()
                        playback.setSpeedPresets(next)
                    }
                }
            }
        }
    }
}

/** One home for what the app feels like, not just what it shows. Haptics lives here and
 *  anything else the phone can sense goes here too instead of a new top-level section. */
@Composable
private fun FeedbackSection() {
    val context = LocalContext.current
    fun pref(name: String): Boolean =
        context.getSharedPreferences("glasstube", android.content.Context.MODE_PRIVATE)
            .getBoolean(name, true)
    fun setPref(name: String, value: Boolean) {
        context.getSharedPreferences("glasstube", android.content.Context.MODE_PRIVATE)
            .edit()
            .putBoolean(name, value)
            .apply()
    }
    var master by remember { mutableStateOf(pref("haptics_enabled")) }
    var seekCancel by remember { mutableStateOf(pref("haptics_seek_cancel")) }
    var speedHome by remember { mutableStateOf(pref("haptics_speed_home")) }
    var clamps by remember { mutableStateOf(pref("haptics_clamps")) }
    var milestones by remember { mutableStateOf(pref("haptics_milestones")) }
    val systemHaptics = com.presaince.glasstube.ui.component.systemHapticsOn(context)

    Page {
        SwitchRow(
            title = "Haptics",
            summary = "Follows the system haptic setting, which is currently " +
                (if (systemHaptics) "on." else "off, so nothing will be felt until it is on."),
            checked = master,
            onCheckedChange = { enabled ->
                master = enabled
                setPref("haptics_enabled", enabled)
                if (enabled) com.presaince.glasstube.ui.component.TickRunner(context).step()
            },
        )
        TextButton(
            onClick = { com.presaince.glasstube.ui.component.TickRunner(context).step() },
        ) { Text("Feel a test tick") }

        Caption("Marks")
        SwitchRow(
            title = "Seek cancel snap",
            summary = "A double-click when a seek comes back to where it started, meaning " +
                "don't seek. It is the gesture's own zero, never the start of the video.",
            checked = seekCancel,
            onCheckedChange = { enabled ->
                seekCancel = enabled
                setPref("haptics_seek_cancel", enabled)
            },
        )
        SwitchRow(
            title = "Speed home snap",
            summary = "The same snap back at 1.0×, the speed's resting value.",
            checked = speedHome,
            onCheckedChange = { enabled ->
                speedHome = enabled
                setPref("haptics_speed_home", enabled)
            },
        )
        SwitchRow(
            title = "Volume and brightness ends",
            summary = "A snap when either gesture reaches the top or the bottom of its range.",
            checked = clamps,
            onCheckedChange = { enabled ->
                clamps = enabled
                setPref("haptics_clamps", enabled)
            },
        )
        SwitchRow(
            title = "Chapter milestones",
            summary = "A lighter tick when a seek crosses a chapter boundary.",
            checked = milestones,
            onCheckedChange = { enabled ->
                milestones = enabled
                setPref("haptics_milestones", enabled)
            },
        )
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun SponsorSection(playback: PlaybackController) {
    val playbackState by playback.state.collectAsStateWithLifecycle()

    Page {
        SwitchRow(
            title = "Skip SponsorBlock",
            summary = "Segments come from the public SponsorBlock API. On by default.",
            checked = playbackState.sponsorSkip,
            onCheckedChange = playback::setSponsorSkip,
        )

        if (playbackState.sponsorSkip) {
            Caption("Categories to skip")
            Text(
                text = "Tap one to stop skipping it. The rest stay on.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 6.dp),
            )
            // Themeable toggle buttons, not checkboxes: same control as the rest of the app, and
            // ordered by label length so the rows fill evenly instead of ragging.
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                SponsorCategories.all
                    .sortedBy { SponsorCategories.label(it).length }
                    .forEach { category ->
                        // Normal chips, like every toggle in the app: the colored dot is the
                        // category's identity and stays; the fill is the theme's own.
                        val checked = category in playbackState.sponsorCategories
                        val categoryColor = SponsorCategories.color(category)
                        FilterChip(
                            selected = checked,
                            onClick = { playback.setSponsorCategory(category, !checked) },
                            label = { Text(SponsorCategories.label(category)) },
                            colors = glassTubeFilterChipColors(),
                            leadingIcon = {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(categoryColor),
                                )
                            },
                        )
                    }
            }
        }
    }
}

@Composable
private fun ResyncBody(resyncStore: ResyncStore) {
    val stored = remember { resyncStore.load() }
    var enabled by remember { mutableStateOf(stored.enabled) }
    var endpoint by remember { mutableStateOf(stored.endpoint) }
    var model by remember { mutableStateOf(stored.model) }
    var apiKey by remember { mutableStateOf(stored.apiKey) }
    var saved by remember { mutableStateOf(false) }

        SwitchRow(
            title = "AI mode",
            summary = "Off by default. Your own model endpoint and key; only the cue slice for " +
                "the comments you align is sent. Turning this on also lets the app generate AI " +
                "chapters for videos that have none.",
            checked = enabled,
            onCheckedChange = {
                enabled = it
                saved = false
            },
        )
        if (enabled) {
            OutlinedTextField(
                value = endpoint,
                onValueChange = { endpoint = it; saved = false },
                label = { Text("Endpoint") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            OutlinedTextField(
                value = model,
                onValueChange = { model = it; saved = false },
                label = { Text("Model") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it; saved = false },
                label = { Text("API key (stored encrypted)") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            TextButton(
                onClick = {
                    resyncStore.save(
                        ResyncSettings(
                            enabled = enabled,
                            endpoint = endpoint,
                            model = model,
                            apiKey = apiKey,
                        ),
                    )
                    saved = true
                },
                modifier = Modifier.padding(top = 4.dp),
            ) {
                Text(if (saved) "Saved" else "Save AI key")
            }
        }
}

@Composable
private fun PostingSection(posting: PostingManager, auth: com.presaince.glasstube.data.auth.YouTubeAuth) {
    // The setup is the section: no row that opens another screen whose only content is this.
    com.presaince.glasstube.feature.posting.PostingScreen(manager = posting, auth = auth)
}

/**
 * One export, one import, and the files this app can see. The import decides what a file is from
 * its content; the reader only has to point at something.
 */
@Composable
private fun DataSection(
    db: GlassTubeDb,
    recipes: com.presaince.glasstube.data.discover.RecipeRepository,
    theme: ThemeController,
    playback: PlaybackController,
    content: ContentRepository,
    resyncStore: ResyncStore,
    onRegionChange: (List<String>) -> Unit,
) {
    val context = LocalContext.current
    val viewModel: SettingsViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                SettingsViewModel(
                    db = db,
                    context = context.applicationContext,
                    resolver = context.contentResolver,
                    recipes = recipes,
                    requestFeedRefresh = content::requestFeedRefresh,
                )
            }
        },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val apiKeyPresent = remember { resyncStore.load().apiKey.isNotBlank() }
    var found by remember { mutableStateOf<List<FoundFile>>(emptyList()) }
    var refresh by remember { mutableIntStateOf(0) }

    val exporter = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) {
            viewModel.export(uri)
            refresh++
        }
    }

    val importer = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) viewModel.import(uri, apiKeyPresent)
    }

    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            BackupFiles.rememberFolder(context, uri)
            refresh++
        }
    }

    // A restore writes preferences underneath every screen already composed. The theme, the
    // player and the card style all hold a copy, and the extractor's country is read once at
    // startup; this is the one place that tells them all to look again.
    LaunchedEffect(state.settingsApplied) {
        if (state.settingsApplied > 0) {
            theme.reload()
            playback.reload()
            CardStyle.reload()
            onRegionChange(readContentRegions(context))
        }
    }
    // A folder listing is a disk walk, so it never runs on the main thread. The message is a
    // refresh key: an import or an export can add a file to the list without a second tap.
    LaunchedEffect(refresh, state.message) {
        found = BackupFiles.load(context)
    }

    Page {
        Caption("One file")
        Text(
            text = "A backup carries subscriptions, appearance, playback, your Bored recipes, " +
                "notes, watch history and where you stopped. Keys and tokens never leave the " +
                "device.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SettingsTile(
                icon = R.drawable.ic_upload,
                title = "Export",
                onClick = { exporter.launch("glasstube-backup.json") },
                modifier = Modifier.weight(1f),
            )
            SettingsTile(
                icon = R.drawable.ic_download,
                title = "Import",
                onClick = { importer.launch(arrayOf("*/*")) },
                modifier = Modifier.weight(1f),
            )
        }
        Text(
            text = "Import reads a GlassTube backup, a NewPipe zip or JSON, a LibreTube JSON, a " +
                "Takeout CSV, or one Bored recipe. A settings file works on its own too.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 10.dp),
        )
        if (state.busy) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            )
        }
        state.message?.let { message ->
            RestoreNote(message = message, onDismiss = viewModel::dismissMessage)
        }

        Caption("Found")
        if (found.isEmpty()) {
            Text(
                text = "No files found yet. Export a backup, or look in a folder.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            found.forEach { file ->
                FoundRow(file = file) { viewModel.import(file.uri, apiKeyPresent) }
            }
        }
        OutlinedButton(
            onClick = { folderPicker.launch(null) },
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
        ) {
            Text(
                if (BackupFiles.folder(context) == null) "Look in a folder" else "Change folder",
            )
        }
    }
}

/** One file the page can see: its name, where it came from, and one tap to import it. */
@Composable
private fun FoundRow(file: FoundFile, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_data),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
            Text(
                text = file.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (!file.inFolder) {
                    "Exported by GlassTube"
                } else if (file.modified > 0) {
                    "In the folder · ${timeAgo(file.modified)}"
                } else {
                    "In the folder"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

/** The result of a restore, in a card the reader can dismiss: it is a receipt, not a dialog. */
@Composable
private fun RestoreNote(message: String, onDismiss: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.faded(0.30f),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onDismiss),
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(14.dp),
        )
    }
}

@Composable
private fun DiagnosticsSection(
    onOpenLogs: () -> Unit,
    log: AppLog? = null,
) {
    var refresh by remember { mutableIntStateOf(0) }
    // The log sits on this page itself: the recent lines are on screen, the full 48 hours are
    // one navigation away, and a reload re-reads rather than jumping to a second screen.
    val allLines = remember(refresh, log) { log?.recent().orEmpty() }
    Page {
        Caption("Self-repair")
        Text(
            text = "When YouTube changes something, the extractor breaks in a handful of known ways. " +
                "GlassTube classifies the failure, resets the extractor on the main content region and " +
                "retries once, and writes every step to the log below. A search that still fails then " +
                "asks your Timestamp Resync model for one fallback query, but only when resync is on " +
                "and your key is present. Stream URLs are never logged.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // The log itself, inline: the last lines are the diagnosis, not a screen behind a tap.
        Caption("Log")
        if (allLines.isEmpty()) {
            Text(
                text = "No entries yet. Play a video and pull the feed, then come back.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            // The most recent hundred on this page; the full log is the Logs row below.
            val shown = allLines.takeLast(100)
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.faded(0.30f),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            ) {
                // A bounded window inside the page scroll: the lines scroll, the page does not.
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    shown.forEach { entry ->
                        Text(
                            text = "${entry.time()} · ${entry.level.name} · ${entry.tag} · ${entry.message}",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
        }
        TextButton(onClick = { refresh++ }) { Text("Reload") }
        ValueRow(
            title = "Logs",
            value = "48h",
            summary = "The full 48 hours, kept on this device. Sent only when you ask.",
            onClick = onOpenLogs,
        )
        Text(
            text = "A bug report attaches a screenshot and the last lines of this log. It stays on " +
                "the phone unless you send it, and sending it is a button on the report screen.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

@Composable
private fun Caption(text: String) {
    val ink = LocalAccentInk.current
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = if (ink == Color.Unspecified) MaterialTheme.colorScheme.primary else ink,
        modifier = Modifier.padding(top = 16.dp, bottom = 6.dp),
    )
}

@Composable
private fun ValueRow(
    title: String,
    value: String,
    summary: String?,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = 56.dp)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            summary?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.secondary,
        )
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
}

@Composable
private fun SwitchRow(
    title: String,
    summary: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .heightIn(min = 56.dp)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            if (summary.isNotBlank()) {
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

private val SPEED_STANDARD: List<Float> = (5..20).map { it / 10f }
private val SPEED_STRETCH: List<Float> = listOf(3.0f, 4.0f, 5.0f)

@Composable
private fun SpeedChoice(speed: Float, selected: Collection<Float>, onToggle: (Boolean) -> Unit) {
    val isOn = speed in selected
    FilterChip(
        selected = isOn,
        onClick = { onToggle(isOn) },
        label = { Text("%.1f".format(speed)) },
        colors = glassTubeFilterChipColors(),
    )
}

/** YouTube answers for a country. Up to three countries can be asked, one after another. */
@Composable
private fun RegionSection(onRegionChange: (List<String>) -> Unit) {
    val context = LocalContext.current
    var chosen by remember {
        mutableStateOf(com.presaince.glasstube.data.source.readContentRegions(context))
    }
    Page {
        Text(
            text = "YouTube serves search, recommendations and trends per country. Up to three can " +
                "be chosen. Each extra country is its own search pass, about five seconds, and the " +
                "results are merged. The first one you pick is the main region: the feed and " +
                "channel pages ask as that one.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        Text(
            text = when {
                com.presaince.glasstube.data.source.SYSTEM_REGION in chosen ->
                    "System. YouTube answers for the phone's own country."
                chosen.size >= com.presaince.glasstube.data.source.MAX_CONTENT_REGIONS ->
                    "Three selected. Tap one off to choose another."
                else -> "${chosen.size} selected"
            },
            style = MaterialTheme.typography.labelMedium,
            color = tileInk(),
            modifier = Modifier.padding(bottom = 6.dp),
        )
        com.presaince.glasstube.ui.component.RegionGrid(
            regions = com.presaince.glasstube.data.source.regionChoices,
            chosen = chosen,
            onToggle = { code ->
                val next = com.presaince.glasstube.data.source.toggleRegion(chosen, code)
                if (next != chosen) {
                    chosen = next
                    onRegionChange(next)
                }
            },
        )
    }
}

/**
 * The dashed target for the step in progress: a ring for the tap, a bar for the seek, the two
 * side bands for volume and brightness, and a mini-player outline for the flick. They show where
 * the finger goes; what the player draws there is a bare percent above the finger. Nothing here
 * takes pointer input.
 */
@Composable
private fun GestureStageHint(step: Int, modifier: Modifier = Modifier) {
    val ink = tileInk().copy(alpha = 0.78f)
    Box(modifier = modifier.fillMaxSize()) {
        when (step) {
            0 -> Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(58.dp)
                    .dashedCircle(ink),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = ink,
                    modifier = Modifier.size(26.dp),
                )
            }
            1 -> Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth(0.84f)
                    .height(18.dp),
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val stroke = 1.5.dp.toPx()
                    val dash = PathEffect.dashPathEffect(
                        floatArrayOf(6.dp.toPx(), 5.dp.toPx()),
                    )
                    val y = size.height / 2f
                    val inset = 16.dp.toPx()
                    drawLine(
                        color = ink,
                        start = Offset(inset, y),
                        end = Offset(size.width - inset, y),
                        strokeWidth = stroke,
                        pathEffect = dash,
                    )
                    val head = 4.5.dp.toPx()
                    drawPath(
                        Path().apply {
                            moveTo(inset, y)
                            lineTo(inset + head, y - head)
                            lineTo(inset + head, y + head)
                            close()
                        },
                        ink,
                    )
                    drawPath(
                        Path().apply {
                            moveTo(size.width - inset, y)
                            lineTo(size.width - inset - head, y - head)
                            lineTo(size.width - inset - head, y + head)
                            close()
                        },
                        ink,
                    )
                }
            }
            2 -> Row(
                modifier = Modifier.align(Alignment.Center),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                // The two controls are a pair, not two things on opposite walls.
                StageLevelHint(R.drawable.ic_volume, ink)
                StageLevelHint(R.drawable.ic_sun, ink)
            }
            3 -> Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_arrow_down),
                    contentDescription = null,
                    tint = ink,
                    modifier = Modifier.size(20.dp),
                )
                Box(
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .width(118.dp)
                        .height(36.dp)
                        .dashedRoundRect(ink, 10.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = 10.dp, bottom = 6.dp)
                            .fillMaxWidth(0.45f)
                            .height(3.dp)
                            .background(ink),
                    )
                }
            }
        }
    }
}

@Composable
private fun StageLevelHint(icon: Int, ink: Color) {
    Box(
        modifier = Modifier
            .width(34.dp)
            .height(88.dp)
            .dashedRoundRect(ink, 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = ink,
            modifier = Modifier.size(18.dp),
        )
    }
}

private fun Modifier.dashedRoundRect(color: Color, corner: Dp): Modifier = drawBehind {
    val stroke = 1.5.dp.toPx()
    drawRoundRect(
        color = color,
        topLeft = Offset(stroke / 2f, stroke / 2f),
        size = Size(size.width - stroke, size.height - stroke),
        cornerRadius = CornerRadius(corner.toPx()),
        style = Stroke(
            width = stroke,
            pathEffect = PathEffect.dashPathEffect(
                floatArrayOf(6.dp.toPx(), 5.dp.toPx()),
            ),
        ),
    )
}

private fun Modifier.dashedCircle(color: Color): Modifier = drawBehind {
    val stroke = 1.5.dp.toPx()
    drawCircle(
        color = color,
        radius = size.minDimension / 2f - stroke / 2f,
        style = Stroke(
            width = stroke,
            pathEffect = PathEffect.dashPathEffect(
                floatArrayOf(6.dp.toPx(), 5.dp.toPx()),
            ),
        ),
    )
}

/** Four columns along the bottom of the picture, filled as each gesture lands. */
@Composable
private fun LessonProgress(completed: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        repeat(4) { index ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        if (index < completed) {
                            tileInk()
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
                        },
                    ),
            )
        }
    }
}

/**
 * The way down and back up the ladder. Once the card leaves the full stage, a faint dashed
 * outline holds its place so the three stops are visible at once.
 */
@Composable
private fun GestureLadderHint(level: Int, modifier: Modifier = Modifier) {
    val ink = tileInk().copy(alpha = 0.78f)
    Box(modifier = modifier.fillMaxSize()) {
        if (level > GestureLesson.NORMAL) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .padding(1.dp)
                    .dashedRoundRect(
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.30f),
                        14.dp,
                    ),
            )
        }
        when (level) {
            GestureLesson.NORMAL -> Icon(
                painter = painterResource(R.drawable.ic_arrow_down),
                contentDescription = null,
                tint = ink,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 10.dp)
                    .size(24.dp),
            )
            GestureLesson.MINI -> Row(
                modifier = Modifier.align(Alignment.Center),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_arrow_down),
                    contentDescription = null,
                    tint = ink,
                    modifier = Modifier.size(24.dp),
                )
                Icon(
                    painter = painterResource(R.drawable.ic_arrow_up),
                    contentDescription = null,
                    tint = ink,
                    modifier = Modifier.size(24.dp),
                )
            }
            else -> Icon(
                painter = painterResource(R.drawable.ic_arrow_up),
                contentDescription = null,
                tint = ink,
                modifier = Modifier.align(Alignment.Center).size(24.dp),
            )
        }
    }
}

/** The mini player as the lesson draws it: the same picture, smaller, with two title lines. */
@Composable
private fun MiniLesson() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Icon(
            painter = painterResource(R.drawable.ic_pause),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f),
            modifier = Modifier.size(28.dp),
        )
        Column(modifier = Modifier.align(Alignment.BottomStart).padding(10.dp)) {
            Box(
                modifier = Modifier
                    .width(64.dp)
                    .height(4.dp)
                    .background(
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                        RoundedCornerShape(2.dp),
                    ),
            )
            Box(
                modifier = Modifier
                    .padding(top = 4.dp)
                    .width(42.dp)
                    .height(3.dp)
                    .background(
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.32f),
                        RoundedCornerShape(2.dp),
                    ),
            )
        }
    }
}

/** The super-minimised bar: a thumbnail, two lines and the up arrow that brings it back. */
@Composable
private fun BarLesson() {
    Box(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.Black.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_pause),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(14.dp),
                )
            }
            Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.55f)
                        .height(4.dp)
                        .background(
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                            RoundedCornerShape(2.dp),
                        ),
                )
                Box(
                    modifier = Modifier
                        .padding(top = 3.dp)
                        .fillMaxWidth(0.35f)
                        .height(3.dp)
                        .background(
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.32f),
                            RoundedCornerShape(2.dp),
                        ),
                )
            }
            Icon(
                painter = painterResource(R.drawable.ic_arrow_up),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(16.dp),
            )
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth(0.4f)
                .height(2.dp)
                .background(MaterialTheme.colorScheme.primary),
        )
    }
}

/**
 * The gestures, learnt by doing them: a stand-in video you can tap, slide and flick. Every gesture
 * draws the same readout the player draws, so the lesson is the muscle memory, not a table of text.
 *
 * The fourth gesture is the real ladder: normal, mini, super-mini, and the way back up. The step
 * completes only after the bar was reached and the card returned to normal.
 */
@Composable
internal fun GestureTutorial() {
    val context = LocalContext.current
    val seekBaseSeconds = context.getSharedPreferences(
        "glasstube",
        android.content.Context.MODE_PRIVATE,
    ).getInt("seek_base_seconds", 5).coerceIn(1, 30).toFloat()
    var tap by remember { mutableStateOf<Offset?>(null) }
    var seekSeconds by remember { mutableStateOf<Int?>(null) }
    var seekTravel by remember { mutableFloatStateOf(0f) }
    var seekDeltaMs by remember { mutableLongStateOf(0L) }
    var volumeLevel by remember { mutableFloatStateOf(0.5f) }
    var brightnessLevel by remember { mutableFloatStateOf(0.5f) }
    var sideActive by remember { mutableStateOf(false) }
    var activeBrightness by remember { mutableStateOf(false) }
    var levelAnchor by remember { mutableStateOf(Offset(0.5f, 0.5f)) }
    var ladderLevel by remember { mutableIntStateOf(GestureLesson.NORMAL) }
    var reachedBar by remember { mutableStateOf(false) }
    var started by remember { mutableStateOf(false) }
    var hint by remember { mutableStateOf("") }
    val instructions = listOf(
        "Tap the box",
        "Slide left or right to seek",
        "Slide up or down anywhere: left volume, right brightness",
        "Flick down twice, then up twice",
    )
    var step by remember { mutableIntStateOf(0) }
    val done = step >= instructions.size
    val seekHint = "Base ${seekBaseSeconds.toInt()} s per tenth. Longer slides move faster; " +
        "change it in Settings \u2192 Playback."

    LaunchedEffect(tap) {
        if (tap != null) {
            delay(900)
            tap = null
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp),
    ) {
        if (started) {
            Text(
                text = if (done) {
                    "All four. Those are the real gestures."
                } else {
                    instructions[step]
                },
                style = MaterialTheme.typography.titleMedium,
                color = tileInk(),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp),
            )
        }

        // The stage keeps its 16:9 size while the card inside it walks the ladder. The pointer
        // input is on the stage, not the shrinking card, so a swipe still lands when the card is
        // a bar at the bottom.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .pointerInput(seekBaseSeconds) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val width = size.width.toFloat().coerceAtLeast(1f)
                        val height = size.height.toFloat().coerceAtLeast(1f)
                        val onSide = down.position.x < width * 0.28f ||
                            down.position.x > width * 0.72f
                        var last = down.position
                        var totalX = 0f
                        var totalY = 0f
                        var axis = 0
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            change.consume()
                            val dx = change.position.x - last.x
                            val dy = change.position.y - last.y
                            totalX += dx
                            totalY += dy
                            last = change.position
                            if (axis == 0 &&
                                (kotlin.math.abs(totalX) > 24f ||
                                    kotlin.math.abs(totalY) > 24f)
                            ) {
                                axis = if (
                                    kotlin.math.abs(totalX) > kotlin.math.abs(totalY)
                                ) {
                                    1
                                } else {
                                    2
                                }
                                if (axis == 1) {
                                    seekTravel = 0f
                                    seekDeltaMs = 0L
                                    seekSeconds = 0
                                }
                            }
                            when (axis) {
                                1 -> if (ladderLevel == GestureLesson.NORMAL) {
                                    val fraction = dx / width
                                    seekDeltaMs += SeekRate.movedMs(
                                        fraction,
                                        seekTravel,
                                        seekBaseSeconds,
                                    )
                                    seekTravel += kotlin.math.abs(fraction)
                                    seekSeconds = (seekDeltaMs / 1000L).toInt()
                                    hint = "Longer slides move faster."
                                }
                                2 -> when {
                                    onSide && ladderLevel == GestureLesson.NORMAL -> {
                                        val left = change.position.x < width / 2f
                                        val next = (0.5f - totalY / height).coerceIn(0f, 1f)
                                        activeBrightness = !left
                                        sideActive = true
                                        // The readout travels with the finger, so the demo
                                        // shows the same tracking the player does.
                                        levelAnchor = Offset(
                                            change.position.x / width,
                                            change.position.y / height,
                                        )
                                        if (left) volumeLevel = next else brightnessLevel = next
                                        hint = "Slide across to the other control while your " +
                                            "finger is down."
                                    }
                                    !onSide -> hint = if (totalY > 0) {
                                        "One step down the ladder"
                                    } else {
                                        "One step up the ladder"
                                    }
                                }
                            }
                            if (!change.pressed) {
                                when (axis) {
                                    0 -> if (
                                        started && ladderLevel == GestureLesson.NORMAL
                                    ) {
                                        tap = Offset(
                                            down.position.x / width,
                                            down.position.y / height,
                                        )
                                        if (step == 0) step = 1
                                        hint = "Controls appear where you tap"
                                    }
                                    1 -> if (
                                        ladderLevel == GestureLesson.NORMAL &&
                                        step == 1
                                    ) {
                                        step = 2
                                        hint = "Keep your finger down and slide across to use " +
                                            "the other side."
                                    }
                                    2 -> if (
                                        onSide && ladderLevel == GestureLesson.NORMAL
                                    ) {
                                        if (step == 2) {
                                            step = 3
                                            hint = "Flick down twice, then up twice."
                                            if (
                                                GestureLesson.isComplete(
                                                    step,
                                                    ladderLevel,
                                                    reachedBar,
                                                )
                                            ) {
                                                step = 4
                                            }
                                        }
                                    } else if (!onSide) {
                                        val before = ladderLevel
                                        ladderLevel = GestureLesson.nextLevel(
                                            ladderLevel,
                                            totalY,
                                            height * 0.12f,
                                        )
                                        if (ladderLevel == GestureLesson.BAR) reachedBar = true
                                        if (ladderLevel != before) {
                                            hint = when (ladderLevel) {
                                                GestureLesson.NORMAL -> "Back to the player"
                                                GestureLesson.MINI ->
                                                    "One step down: the mini player"
                                                else -> "The bar. Swipe up twice to return."
                                            }
                                        }
                                        if (
                                            step == 3 &&
                                            GestureLesson.isComplete(
                                                step,
                                                ladderLevel,
                                                reachedBar,
                                            )
                                        ) {
                                            step = 4
                                            hint = "All four. Those are the real gestures."
                                        }
                                    }
                                }
                                seekSeconds = null
                                sideActive = false
                                break
                            }
                        }
                    }
                },
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(
                        if (ladderLevel == GestureLesson.MINI) 0.62f else 1f,
                    )
                    .then(
                        if (ladderLevel == GestureLesson.BAR) {
                            Modifier.height(44.dp)
                        } else {
                            Modifier.aspectRatio(16f / 9f)
                        },
                    )
                    .animateContentSize()
                    .clip(
                        RoundedCornerShape(
                            if (ladderLevel == GestureLesson.BAR) 10.dp else 14.dp,
                        ),
                    )
                    .background(MaterialTheme.colorScheme.surfaceVariant.faded(0.45f))
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant,
                        RoundedCornerShape(
                            if (ladderLevel == GestureLesson.BAR) 10.dp else 14.dp,
                        ),
                    ),
            ) {
                Crossfade(
                    targetState = ladderLevel,
                    modifier = Modifier.fillMaxSize(),
                    label = "lesson-level",
                ) { stage ->
                    when (stage) {
                        GestureLesson.NORMAL -> Box(modifier = Modifier.fillMaxSize()) {
                            // The stand-in picture. It no longer scales; the card itself moves
                            // down the ladder.
                            Box(
                                modifier = Modifier.matchParentSize(),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_pause),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme
                                        .onSurfaceVariant.copy(alpha = 0.25f),
                                    modifier = Modifier.size(48.dp),
                                )
                            }

                            if (started && !done && step < 3) {
                                GestureStageHint(
                                    step = step,
                                    modifier = Modifier.matchParentSize(),
                                )
                            }

                            seekSeconds?.let { seconds ->
                                val signed = when {
                                    seconds > 0 -> "+${seconds}s"
                                    seconds < 0 -> "\u2212${-seconds}s"
                                    else -> "0s"
                                }
                                // The lesson has no video to name a target for, so it shows the
                                // distance the finger moved, not a fake position.
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopCenter)
                                        .padding(top = 8.dp)
                                        .width(130.dp)
                                        .height(40.dp)
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(Color(0xE6101014)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = signed,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = Color.White,
                                    )
                                }
                            }

                            if (sideActive) {
                                val activeLevel = if (activeBrightness) {
                                    brightnessLevel
                                } else {
                                    volumeLevel
                                }
                                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                                    // The seek pill's capsule with the level's own icon, above
                                    // the finger and travelling with it: the demo cannot
                                    // promise a readout the real gesture lacks.
                                    LevelReadout(
                                        icon = if (activeBrightness) {
                                            R.drawable.ic_sun
                                        } else {
                                            R.drawable.ic_volume
                                        },
                                        level = activeLevel,
                                        modifier = Modifier.offset(
                                            x = readoutLeft(
                                                maxWidth * levelAnchor.x,
                                                maxWidth,
                                            ),
                                            y = readoutTop(
                                                maxHeight * levelAnchor.y,
                                                maxHeight,
                                            ),
                                        ),
                                    )
                                }
                            }

                            tap?.let { point ->
                                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                                    val disc = 44.dp
                                    val x = (maxWidth * point.x - disc / 2)
                                        .coerceIn(0.dp, (maxWidth - disc).coerceAtLeast(0.dp))
                                    val y = (maxHeight * point.y - disc / 2)
                                        .coerceIn(0.dp, (maxHeight - disc).coerceAtLeast(0.dp))
                                    Box(
                                        modifier = Modifier
                                            .offset(x = x, y = y)
                                            .size(disc)
                                            .clip(CircleShape)
                                            .background(Color(0x66000000))
                                            .border(2.dp, Color.White, CircleShape),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PlayArrow,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(26.dp),
                                        )
                                    }
                                }
                            }
                        }

                        GestureLesson.MINI -> MiniLesson()
                        else -> BarLesson()
                    }
                }
            }

            if (!started) {
                // The invitation lives on the picture, where the finger is going.
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0x99000000))
                        .clickable {
                            started = true
                            hint = ""
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(30.dp),
                        )
                        Text(
                            text = "Gesture tutorial",
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.White.copy(alpha = 0.75f),
                            modifier = Modifier.padding(top = 6.dp),
                        )
                        Text(
                            text = "Touch me",
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
            }

            if (started && !done && step == 3) {
                GestureLadderHint(
                    level = ladderLevel,
                    modifier = Modifier.matchParentSize(),
                )
            }

            // The lesson's own progress, on the picture's bottom edge: four columns, filled as
            // each gesture lands. It replaces the row of dots that used to sit above the card.
            LessonProgress(
                completed = if (done) 4 else step,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(start = 12.dp, end = 12.dp, bottom = 8.dp),
            )
        }

        if (done) {
            TextButton(
                onClick = {
                    step = 0
                    ladderLevel = GestureLesson.NORMAL
                    reachedBar = false
                    tap = null
                    volumeLevel = 0.5f
                    brightnessLevel = 0.5f
                    sideActive = false
                    activeBrightness = false
                    seekSeconds = null
                    seekTravel = 0f
                    seekDeltaMs = 0L
                    levelAnchor = Offset(0.5f, 0.5f)
                    hint = ""
                },
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) {
                Text("Restart tutorial")
            }
        } else if (started) {
            Text(
                text = if (step == 1) seekHint else hint,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
            )
        }
    }
}
