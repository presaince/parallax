/*
 * Parallax — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.parallax.feature.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.content.Context
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.presaince.parallax.R
import com.presaince.parallax.core.AppLog
import com.presaince.parallax.core.QUALITY_STOPS
import com.presaince.parallax.data.auth.PostingManager
import com.presaince.parallax.data.db.ParallaxDb
import com.presaince.parallax.data.resync.ResyncSettings
import com.presaince.parallax.data.resync.ResyncStore
import com.presaince.parallax.data.source.flag
import com.presaince.parallax.data.sponsorblock.SponsorCategories
import com.presaince.parallax.playback.PlaybackController
import com.presaince.parallax.ui.theme.Accents
import com.presaince.parallax.ui.theme.ThemeController
import com.presaince.parallax.ui.theme.ThemeMode
import com.presaince.parallax.ui.theme.faded
import com.presaince.parallax.ui.theme.parallaxFilterChipColors
import com.presaince.parallax.ui.component.CardStyle
import com.presaince.parallax.ui.component.CardLayout
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.roundToInt

/** One page of Settings. The root list decides which; nothing else is on screen. */
@Composable
fun SettingsSectionScreen(
    section: SettingsSectionId,
    playback: PlaybackController,
    resyncStore: ResyncStore,
    posting: PostingManager,
    db: ParallaxDb,
    recipes: com.presaince.parallax.data.discover.RecipeRepository,
    theme: ThemeController,
    auth: com.presaince.parallax.data.auth.YouTubeAuth,
    onRegionChange: (List<String>) -> Unit,
    onOpenLogs: () -> Unit,
    onOpenVideo: (com.presaince.parallax.data.model.VideoSummary) -> Unit,
    log: AppLog? = null,
) {
    when (section) {
        SettingsSectionId.APPEARANCE -> AppearanceSection(theme)
        SettingsSectionId.PLAYBACK -> PlaybackSection(playback)
        SettingsSectionId.REGION -> RegionSection(onRegionChange)
        SettingsSectionId.SPONSOR -> SponsorSection(playback)
        SettingsSectionId.ACCOUNTS -> AccountsSection(resyncStore, posting, auth)
        SettingsSectionId.AI -> AiSection(resyncStore)
        SettingsSectionId.RESYNC -> ResyncSection(resyncStore)
        SettingsSectionId.POSTING -> PostingSection(posting, auth)
        SettingsSectionId.DATA -> DataSection(db, recipes)
        SettingsSectionId.DIAGNOSTICS -> DiagnosticsSection(onOpenLogs, log)
        // One About page, not two: the section is the whole thing.
        SettingsSectionId.ABOUT -> com.presaince.parallax.feature.about.AboutScreen()
    }
}

/** One Accounts & keys page: the model key and the posting keys live together. */
@Composable
private fun AccountsSection(
    resyncStore: ResyncStore,
    posting: PostingManager,
    auth: com.presaince.parallax.data.auth.YouTubeAuth,
) {
    Page {
        Caption("Timestamp resync")
        ResyncBody(resyncStore)
        Caption("Posting")
        // PostingScreen is a full scroll page of its own; a fixed window keeps the two keys on
        // one page without nested unbounded scroll containers.
        Box(modifier = Modifier.fillMaxWidth().height(700.dp)) {
            PostingSection(posting, auth)
        }
    }
}

/** The AI page: the same Resync key, cached like ai_chapters. Summary and ask arrive here. */
@Composable
private fun AiSection(resyncStore: ResyncStore) {
    Page {
        Text(
            text = "AI uses the Resync key above. A video summary, a cleaned description and ask-a-question " +
                "over the transcript are generated on demand and cached per video. Channel blurbs use the same key.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Caption("Resync key")
        ResyncBody(resyncStore)
        Caption("Coming next")
        Text(
            text = "Summary, cleaned description, ask-a-question and channel blurbs. Each answer is cached; " +
                "regeneration is explicit, never silent.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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

        Caption("Motion")
        var animations by remember {
            mutableStateOf(
                context.getSharedPreferences("parallax", Context.MODE_PRIVATE)
                    .getBoolean("animations_enabled", true),
            )
        }
        var videoTint by remember {
            mutableStateOf(
                context.getSharedPreferences("parallax", Context.MODE_PRIVATE)
                    .getBoolean("theme_from_video", false),
            )
        }
        SwitchRow(
            title = "Tint from video",
            summary = "Advanced: tints the player page with the video's dominant colour. Off by default.",
            checked = videoTint,
            onCheckedChange = { on ->
                videoTint = on
                context.getSharedPreferences("parallax", Context.MODE_PRIVATE)
                    .edit().putBoolean("theme_from_video", on).apply()
            },
        )
        var loadingStyle by remember {
            mutableIntStateOf(
                context.getSharedPreferences("parallax", Context.MODE_PRIVATE)
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
                context.getSharedPreferences("parallax", Context.MODE_PRIVATE)
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
                context.getSharedPreferences("parallax", Context.MODE_PRIVATE)
                    .edit().putInt("loading_style", style).apply()
            },
        )
    }
}

/** The accent master and its three rhythms, on one line. */
@Composable
private fun ShuffleRow(theme: ThemeController) {
    val cadence = theme.shuffle
    val on = cadence != com.presaince.parallax.ui.theme.ShuffleCadence.OFF
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
                        com.presaince.parallax.ui.theme.ShuffleCadence.OFF
                    } else {
                        com.presaince.parallax.ui.theme.ShuffleCadence.DAILY
                    },
                )
            },
        )
        PickChip(
            label = "Daily",
            selected = cadence == com.presaince.parallax.ui.theme.ShuffleCadence.DAILY,
            muted = !on,
            onClick = { theme.chooseShuffle(com.presaince.parallax.ui.theme.ShuffleCadence.DAILY) },
        )
        PickChip(
            label = "Hourly",
            selected = cadence == com.presaince.parallax.ui.theme.ShuffleCadence.HOURLY,
            muted = !on,
            onClick = { theme.chooseShuffle(com.presaince.parallax.ui.theme.ShuffleCadence.HOURLY) },
        )
        PickChip(
            label = "Focus",
            selected = cadence == com.presaince.parallax.ui.theme.ShuffleCadence.ON_OPEN,
            muted = !on,
            onClick = { theme.chooseShuffle(com.presaince.parallax.ui.theme.ShuffleCadence.ON_OPEN) },
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
    val tint = when {
        selected -> MaterialTheme.colorScheme.primary
        muted -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.faded(0.35f)
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
                    colors = parallaxFilterChipColors(),
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
    )
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
private fun PreviewCard(style: CardStyle) {
    val meta = buildList {
        if (style.channel) add("Channel name")
        if (style.age) add("3h ago")
        if (style.views) add("128K views")
        if (style.likes) add("4.2K likes")
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
 * The Glass theme's own button, and the surface switch inside it. Top half is the glass look,
 * bottom half the solid one; the half in use stays tinted, so the tile always answers "which
 * surface" even while another theme is active. Tapping either half selects Glass with that
 * surface, which is what the old separate "Surfaces" block was asking twice.
 */
@Composable
private fun androidx.compose.foundation.layout.RowScope.GlassTile(theme: ThemeController) {
    val active = theme.mode == ThemeMode.GLASS
    val glass = !theme.solidSurfaces
    val shape = RoundedCornerShape(20.dp)
    Column(
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
            ),
    ) {
        // Top half is the levels: the same shape, a slider with eight levels instead of two
        // fixed fills. Bottom half stays the solid escape hatch.
        SurfaceHalf(
            label = "Glass",
            selected = glass,
            active = active,
            brush = Brush.linearGradient(
                listOf(
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.30f),
                    MaterialTheme.colorScheme.tertiary.copy(alpha = 0.16f),
                    MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
                ),
            ),
            onClick = {
                theme.chooseSolidSurfaces(false)
                theme.chooseMode(ThemeMode.GLASS)
            },
            modifier = Modifier.weight(1f),
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f))
        // The levels: 0 is the clearest glass, 7 nearly solid. Same tile, a dial now.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 30.dp)
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "${theme.glassLevel}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.width(20.dp),
            )
            androidx.compose.material3.Slider(
                value = theme.glassLevel.toFloat(),
                onValueChange = { theme.chooseGlassLevel(it.roundToInt()) },
                valueRange = 0f..7f,
                steps = 6,
                modifier = Modifier.weight(1f),
            )
        }
        SurfaceHalf(
            label = "Solid",
            selected = !glass,
            active = active,
            brush = Brush.linearGradient(
                listOf(
                    MaterialTheme.colorScheme.surfaceVariant,
                    MaterialTheme.colorScheme.surfaceVariant,
                ),
            ),
            onClick = {
                theme.chooseSolidSurfaces(glass)
                theme.chooseMode(ThemeMode.GLASS)
            },
            modifier = Modifier.weight(1f),
        )
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
                active && selected -> MaterialTheme.colorScheme.primary
                active -> MaterialTheme.colorScheme.onSurfaceVariant
                selected -> MaterialTheme.colorScheme.onSurface
                else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
            },
            maxLines = 1,
        )
    }
}

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
                        MaterialTheme.colorScheme.primary
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
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}

@Composable
private fun AccentGrid(theme: ThemeController) {
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
                        modifier = Modifier.weight(1f).height(44.dp),
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
                                )
                                .clickable { theme.chooseAccent(accent) }
                                .semantics {
                                    contentDescription = if (selected) {
                                        "Accent $index, selected"
                                    } else {
                                        "Accent $index"
                                    }
                                },
                        )
                    }
                }
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
            context.getSharedPreferences("parallax", android.content.Context.MODE_PRIVATE)
                .getBoolean("hide_live", false),
        )
    }
    var speedPresets by remember { mutableStateOf(playback.speedPresets()) }
    var pipOnLeave by remember {
        mutableStateOf(
            context.getSharedPreferences("parallax", android.content.Context.MODE_PRIVATE)
                .getBoolean("pip_on_leave", true),
        )
    }
    var progressInFullscreen by remember {
        mutableStateOf(
            context.getSharedPreferences("parallax", android.content.Context.MODE_PRIVATE)
                .getBoolean("progress_in_fullscreen", true),
        )
    }
    var notificationRestart by remember {
        mutableStateOf(
            context.getSharedPreferences("parallax", android.content.Context.MODE_PRIVATE)
                .getBoolean("notification_restart", true),
        )
    }
    var chaptersHorizontal by remember {
        mutableStateOf(
            context.getSharedPreferences("parallax", android.content.Context.MODE_PRIVATE)
                .getBoolean("chapters_horizontal", true),
        )
    }
    var endRelated by remember {
        mutableStateOf(
            context.getSharedPreferences("parallax", android.content.Context.MODE_PRIVATE)
                .getBoolean("end_related_enabled", true),
        )
    }
    var seekBase by remember {
        mutableStateOf(
            context.getSharedPreferences("parallax", android.content.Context.MODE_PRIVATE)
                .getInt("seek_base_seconds", 5),
        )
    }

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
                context.getSharedPreferences("parallax", android.content.Context.MODE_PRIVATE)
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
                context.getSharedPreferences("parallax", android.content.Context.MODE_PRIVATE)
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
                context.getSharedPreferences("parallax", android.content.Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean("pip_on_leave", enabled)
                    .apply()
            },
        )

        SwitchRow(
            title = "Related at the end",
            summary = "Shows Up next when a video finishes. Off means the page simply ends.",
            checked = endRelated,
            onCheckedChange = { enabled ->
                endRelated = enabled
                context.getSharedPreferences("parallax", android.content.Context.MODE_PRIVATE)
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
                context.getSharedPreferences("parallax", android.content.Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean("chapters_horizontal", enabled)
                    .apply()
            },
        )

        SwitchRow(
            title = "Hide live and upcoming",
            summary = "Leaves them out of the feed, search and channel pages.",
            checked = hideLive,
            onCheckedChange = { enabled ->
                hideLive = enabled
                context.getSharedPreferences("parallax", android.content.Context.MODE_PRIVATE)
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
                        context.getSharedPreferences("parallax", android.content.Context.MODE_PRIVATE)
                            .edit()
                            .putInt("seek_base_seconds", seconds)
                            .apply()
                    },
                    label = { Text(label) },
                    colors = parallaxFilterChipColors(),
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
                context.getSharedPreferences("parallax", android.content.Context.MODE_PRIVATE)
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
                    color = MaterialTheme.colorScheme.primary,
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
                    context.getSharedPreferences("parallax", android.content.Context.MODE_PRIVATE)
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
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    },
                    modifier = Modifier
                        .clickable {
                            if (stop.height != qualityState.value) {
                                qualityState.value = stop.height
                                context.getSharedPreferences("parallax", android.content.Context.MODE_PRIVATE)
                                    .edit()
                                    .putInt("video_quality", stop.height)
                                    .apply()
                            }
                        }
                        .padding(horizontal = 6.dp, vertical = 4.dp),
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

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun SponsorSection(playback: PlaybackController) {
    val playbackState by playback.state.collectAsStateWithLifecycle()

    Page {
        SwitchRow(
            title = "Skip SponsorBlock segments",
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
                        val checked = category in playbackState.sponsorCategories
                        FilterChip(
                            selected = checked,
                            onClick = { playback.setSponsorCategory(category, !checked) },
                            label = { Text(SponsorCategories.label(category)) },
                            colors = parallaxFilterChipColors(),
                        )
                    }
            }
        }
    }
}

@Composable
private fun ResyncSection(resyncStore: ResyncStore) {
    Page { ResyncBody(resyncStore) }
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
            title = "Align comment timestamps",
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
                Text(if (saved) "Saved" else "Save Resync")
            }
        }
}

@Composable
private fun PostingSection(posting: PostingManager, auth: com.presaince.parallax.data.auth.YouTubeAuth) {
    // The setup is the section: no row that opens another screen whose only content is this.
    com.presaince.parallax.feature.posting.PostingScreen(manager = posting, auth = auth)
}

@Composable
private fun DataSection(
    db: ParallaxDb,
    recipes: com.presaince.parallax.data.discover.RecipeRepository,
) {
    val context = LocalContext.current
    val viewModel: SettingsViewModel = viewModel(
        factory = viewModelFactory {
            initializer { SettingsViewModel(db, context.contentResolver) }
        },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var recipeMessage by remember { mutableStateOf<String?>(null) }
    var dataMessage by remember { mutableStateOf<String?>(null) }

    val recipeImporter = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val text = runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                java.io.InputStreamReader(stream).readText()
            }
        }.getOrNull()
        if (text.isNullOrBlank()) {
            recipeMessage = "Could not read that file"
        } else {
            scope.launch {
                recipeMessage = when (val result = recipes.import(text)) {
                    is com.presaince.parallax.data.discover.RecipeResult.Ok ->
                        "Imported ${result.recipe.name}"
                    is com.presaince.parallax.data.discover.RecipeResult.Err ->
                        result.problems.take(3).joinToString("; ")
                }
            }
        }
    }

    val settingsExporter = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val json = com.presaince.parallax.data.backup.SettingsExport.json(context)
        val result = runCatching {
            context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
        }
        dataMessage = if (result.isSuccess) "Settings exported" else "Could not write the file"
    }
    val subscriptionsImporter = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(viewModel::import) }
    val subscriptionsExporter = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val json = viewModel.exportJson()
            val result = runCatching {
                context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
            }
            dataMessage = if (result.isSuccess) "Subscriptions exported" else "Could not write the file"
        }
    }

    Page {
        Caption("Subscriptions")
        // One card for subscriptions, two buttons inside it: the card is the object, the flows
        // are the actions on it.
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.faded(0.30f),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                ValueRow(
                    title = "Import subscriptions",
                    value = "NewPipe zip or JSON",
                    summary = "Also LibreTube JSON and a Google Takeout CSV.",
                    onClick = { subscriptionsImporter.launch(arrayOf("*/*")) },
                )
                ValueRow(
                    title = "Export subscriptions",
                    value = "JSON",
                    summary = "NewPipe-style, readable by the clients this imports from.",
                    onClick = { subscriptionsExporter.launch("parallax-subscriptions.json") },
                )
            }
        }
        Caption("Settings and recipes")
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.faded(0.30f),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                ValueRow(
                    title = "Export settings",
                    value = "JSON",
                    summary = "Theme, accent, SponsorBlock, Resync endpoint and search history. " +
                        "Keys are never exported.",
                    onClick = { settingsExporter.launch("parallax-settings.json") },
                )
                ValueRow(
                    title = "Import a Bored recipe",
                    value = "JSON",
                    summary = "A recipe in Bored's own format, added to the recipe row there.",
                    onClick = { recipeImporter.launch(arrayOf("application/json", "*/*")) },
                )
            }
        }
        recipeMessage?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { recipeMessage = null }
                    .padding(vertical = 8.dp),
            )
        }
        dataMessage?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { dataMessage = null }
                    .padding(vertical = 8.dp),
            )
        }
        state.message?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = viewModel::dismissMessage)
                    .padding(vertical = 8.dp),
            )
        }
        if (state.busy) {
            CircularProgressIndicator(modifier = Modifier.padding(8.dp))
        }
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
                "Parallax classifies the failure, resets the extractor on the main content region and " +
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
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
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
        colors = parallaxFilterChipColors(),
    )
}

/** YouTube answers for a country. Up to three countries can be asked, one after another. */
@Composable
private fun RegionSection(onRegionChange: (List<String>) -> Unit) {
    val context = LocalContext.current
    var chosen by remember {
        mutableStateOf(com.presaince.parallax.data.source.readContentRegions(context))
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
            text = if (chosen.size >= com.presaince.parallax.data.source.MAX_CONTENT_REGIONS) {
                "Three selected. Tap one off to choose another."
            } else {
                "${chosen.size} selected"
            },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        // Three cards across: a flag stretched full-width reads as a flag stretched, and the
        // long list of countries is the shortest path to a card grid.
        com.presaince.parallax.data.source.contentRegions
            .sortedBy { it.name.lowercase() }
            .chunked(3)
            .forEach { chunk ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    chunk.forEach { region ->
                        val index = chosen.indexOf(region.code)
                        val selected = index >= 0
                        val main = index == 0
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .padding(vertical = 3.dp)
                                .height(58.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .then(
                                    if (selected) {
                                        Modifier.border(
                                            if (main) 2.dp else 1.dp,
                                            MaterialTheme.colorScheme.primary,
                                            RoundedCornerShape(10.dp),
                                        )
                                    } else {
                                        Modifier
                                    },
                                )
                                .clickable {
                                    // One tap selects, one tap removes. The last one cannot be
                                    // removed: a search with no country has no default to fall
                                    // back to.
                                    val next = when {
                                        selected -> chosen.filterNot { it == region.code }
                                        chosen.size < com.presaince.parallax.data.source.MAX_CONTENT_REGIONS ->
                                            chosen + region.code
                                        else -> chosen
                                    }
                                    if (next.isNotEmpty() && next != chosen) {
                                        chosen = next
                                        onRegionChange(next)
                                    }
                                },
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            com.presaince.parallax.ui.component.FlagBackground(
                                code = region.code,
                                modifier = Modifier.matchParentSize(),
                                alpha = if (selected) 0.42f else 0.24f,
                            )
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .background(
                                        Brush.horizontalGradient(
                                            listOf(
                                                MaterialTheme.colorScheme.surface.copy(alpha = 0.90f),
                                                MaterialTheme.colorScheme.surface.copy(alpha = 0.45f),
                                            ),
                                        ),
                                    ),
                            )
                            Text(
                                text = region.name,
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (selected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                        },
                        modifier = Modifier.padding(start = 14.dp),
                    )
                    if (main) {
                        Text(
                            text = "Main",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .padding(end = 14.dp)
                                .background(
                                    MaterialTheme.colorScheme.primary.faded(0.16f),
                                    RoundedCornerShape(50),
                                )
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                        }
                    }
                    repeat(3 - chunk.size) { Spacer(Modifier.weight(1f)) }
                }
            }
    }
}

/**
 * The gestures, learnt by doing them: a stand-in video you can tap, slide and flick. Every gesture
 * draws the same readout the player draws, so the lesson is the muscle memory, not a table of text.
 */
@Composable
private fun GestureTutorial() {
    var tap by remember { mutableStateOf<Offset?>(null) }
    var seekSeconds by remember { mutableStateOf<Int?>(null) }
    var level by remember { mutableStateOf<Float?>(null) }
    var levelIsBrightness by remember { mutableStateOf(false) }
    var minimised by remember { mutableStateOf(false) }
    var started by remember { mutableStateOf(false) }
    var hint by remember { mutableStateOf("Tap the card to start the lesson") }
    val scope = rememberCoroutineScope()

    val scale by animateFloatAsState(
        targetValue = if (minimised) 0.35f else 1f,
        animationSpec = tween(if (minimised) 300 else 180),
        label = "tutorial-minimise",
    )

    LaunchedEffect(tap) {
        if (tap != null) {
            delay(900)
            tap = null
        }
    }

    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
        val instructions = listOf(
            "Tap the box",
            "Slide left or right to seek",
            "Slide up or down on a side",
            "Flick down to minimise",
        )
        var step by remember { mutableIntStateOf(0) }
        val done = step >= instructions.size
        Text(
            text = when {
                !started -> "Tap the card to start"
                done -> "All four. Those are the real gestures in the player."
                else -> "Step ${step + 1} of ${instructions.size} · ${instructions[step]}"
            },
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        // Small on purpose: a tutorial that fills the width is a screen, not a card. The four
        // gestures are a thing to try on the way past the switch below it.
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.72f)
                    .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.faded(0.45f))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val width = size.width.toFloat().coerceAtLeast(1f)
                        val height = size.height.toFloat().coerceAtLeast(1f)
                        val leftSide = down.position.x < width / 2f
                        var last = down.position
                        var totalX = 0f
                        var totalY = 0f
                        var axis = 0
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            change.consume()
                            totalX += change.position.x - last.x
                            totalY += change.position.y - last.y
                            last = change.position
                            if (axis == 0 &&
                                (kotlin.math.abs(totalX) > 24f || kotlin.math.abs(totalY) > 24f)
                            ) {
                                axis = if (kotlin.math.abs(totalX) > kotlin.math.abs(totalY)) 1 else 2
                            }
                            when (axis) {
                                1 -> {
                                    seekSeconds = ((totalX / width) * 120f).toInt()
                                    hint = "Slide left or right to seek"
                                }
                                2 -> {
                                    level = (0.5f - totalY / height).coerceIn(0f, 1f)
                                    levelIsBrightness = !leftSide
                                    hint = if (leftSide) {
                                        "Up or down on the left is volume"
                                    } else {
                                        "Up or down on the right is brightness"
                                    }
                                }
                            }
                            if (!change.pressed) {
                                if (axis == 0) {
                                    tap = Offset(
                                        down.position.x / width,
                                        down.position.y / height,
                                    )
                                    hint = "Controls appear where you tap"
                                    if (step == 0) step = 1
                                } else if (axis == 2 && totalY > height * 0.2f) {
                                    minimised = true
                                    hint = "A downward flick goes to the mini player"
                                    if (step == 3) step = 4
                                    scope.launch {
                                        delay(900)
                                        minimised = false
                                    }
                                } else if (axis == 1 && step == 1) {
                                    step = 2
                                } else if (axis == 2 && step == 2) {
                                    step = 3
                                }
                                seekSeconds = null
                                level = null
                                break
                            }
                        }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            // The stand-in picture, which shrinks when you flick it down.
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationY = if (minimised) size.height * 0.55f else 0f
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_pause),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f),
                    modifier = Modifier.size(48.dp),
                )
            }

            seekSeconds?.let { seconds ->
                val signed = when {
                    seconds > 0 -> "+${seconds}s"
                    seconds < 0 -> "\u2212${-seconds}s"
                    else -> "0s"
                }
                // The same pill the player draws: the jump as the headline, the target underneath,
                // and the video's progress inside the bottom edge.
                val target = (210 + seconds).coerceIn(0, 720)
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 8.dp)
                        .width(150.dp)
                        .height(50.dp)
                        .clip(RoundedCornerShape(25.dp))
                        .background(Color(0xE6101014)),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = signed,
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                        )
                        Text(
                            text = "%d:%02d".format(target / 60, target % 60),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.7f),
                        )
                    }
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(4.dp)
                            .background(Color(0x33FFFFFF)),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth((target / 720f).coerceIn(0f, 1f))
                                .fillMaxHeight()
                                .background(MaterialTheme.colorScheme.primary),
                        )
                    }
                }
            }

            level?.let { value ->
                Box(
                    modifier = Modifier
                        .align(if (levelIsBrightness) Alignment.CenterEnd else Alignment.CenterStart)
                        .padding(horizontal = 10.dp)
                        .width(36.dp)
                        .height(96.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xE6101014)),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .fillMaxHeight(value)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)),
                    )
                    Icon(
                        painter = painterResource(
                            if (levelIsBrightness) R.drawable.ic_sun else R.drawable.ic_volume,
                        ),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            tap?.let { point ->
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val disc = 44.dp
                    val x = (maxWidth * point.x / 1f - disc / 2)
                        .coerceIn(0.dp, (maxWidth - disc).coerceAtLeast(0.dp))
                    val y = (maxHeight * point.y / 1f - disc / 2)
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
            if (!started) {
                // The invitation is the overlay, not another paragraph under the card. One tap
                // anywhere on the picture begins the lesson; until then the gestures do nothing.
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0x99000000))
                        .clickable {
                            started = true
                            hint = "Try it: tap, slide, or flick down"
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
                            text = "Tap to try the gestures",
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.White,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
        }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = hint,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f).padding(top = 6.dp),
            )
            if (done) {
                TextButton(
                    onClick = {
                        step = 0
                        hint = "Try it: tap, slide, or flick down"
                    },
                ) {
                    Text("Start again")
                }
            }
        }
    }
}
