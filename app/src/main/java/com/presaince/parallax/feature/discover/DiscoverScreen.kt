/*
 * Parallax — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.parallax.feature.discover

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.presaince.parallax.R
import com.presaince.parallax.data.discover.DiscoverEngine
import com.presaince.parallax.data.discover.Recipe
import com.presaince.parallax.data.discover.RecipeRepository
import com.presaince.parallax.data.model.VideoSummary
import com.presaince.parallax.ui.component.SineRiver
import com.presaince.parallax.ui.component.ScrollToTopButton
import com.presaince.parallax.ui.component.VideoRow
import com.presaince.parallax.ui.theme.faded
import com.presaince.parallax.ui.theme.parallaxFilterChipColors
import kotlin.math.PI
import kotlin.math.sin

/**
 * Bored Mode. A recipe is a button: tap it and a page of picks arrives. The list can be refreshed
 * for another page, and every row still carries the path that produced it.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun DiscoverScreen(
    recipes: RecipeRepository,
    engine: DiscoverEngine,
    onOpenVideo: (VideoSummary) -> Unit,
) {
    val viewModel: DiscoverViewModel = viewModel(
        factory = viewModelFactory {
            initializer { DiscoverViewModel(recipes, engine) }
        },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember {
        context.getSharedPreferences("parallax", android.content.Context.MODE_PRIVATE)
    }
    // Bored's own setting: subscribed channels are the feed's job, not the walk's.
    var hideSubs by remember { mutableStateOf(prefs.getBoolean("bored_hide_subscribed", true)) }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FlowRow(
                modifier = Modifier.weight(1f).padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                state.recipes.forEach { recipe ->
                    FilterChip(
                        selected = false,
                        onClick = { viewModel.selectAndWalk(recipe.name) },
                        label = { Text(recipe.name) },
                        colors = parallaxFilterChipColors(),
                    )
                }
            }
        }

        state.message?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = viewModel::dismissMessage)
                    .padding(vertical = 6.dp),
            )
        }

        state.recipe?.let { recipe ->
            RecipeWhy(
                recipe = recipe,
                hideSubscribed = hideSubs,
                onHideSubscribed = {
                    hideSubs = it
                    prefs.edit().putBoolean("bored_hide_subscribed", it).apply()
                    viewModel.refresh()
                },
                onTune = viewModel::tune,
            )
        }

        if (state.picks.isEmpty()) {
            // The waiting screen is the river, at the size the picture would have been.
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                SineRiver(
                    modifier = Modifier.fillMaxWidth().height(180.dp),
                    interactive = true,
                )
                if (state.walking) {
                    Text(
                        text = "Finding something…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 220.dp),
                    )
                } else {
                    Text(
                        text = "Choose a mode",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 220.dp),
                    )
                }
            }
        } else {
            val listState = rememberLazyListState()
            Box(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(bottom = 84.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(state.picks, key = { it.video.id }) { step ->
                        VideoRow(
                            video = step.video,
                            watched = null,
                            meta = step.path.joinToString("\n"),
                            badge = { ScoreBadge(step.score) },
                            onClick = { onOpenVideo(step.video) },
                        )
                    }
                }
                ScrollToTopButton(
                    listState = listState,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                )
            }
        }
    }
}

/**
 * A recipe score, sitting in the picture's corner on its own opaque surface. The number used to
 * run on in the meta line and be covered by the next line of text; a score is a badge, not a fact
 * in a sentence.
 */
@Composable
private fun ScoreBadge(score: Double) {
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        contentColor = MaterialTheme.colorScheme.primary,
        shape = RoundedCornerShape(7.dp),
        shadowElevation = 2.dp,
    ) {
        Text(
            text = "%.2f".format(score),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
        )
    }
}

/**
 * "Why these?" — the recipe in plain words, and the controls that change it. Tuning is a session
 * override: it applies to the next fetch and is forgotten on restart, which keeps a built-in
 * recipe a recipe rather than a settings screen in disguise.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun RecipeWhy(
    recipe: Recipe,
    hideSubscribed: Boolean,
    onHideSubscribed: (Boolean) -> Unit,
    onTune: (Recipe) -> Unit,
) {
    var open by remember(recipe.name) { mutableStateOf(false) }
    val isShortRecipe = recipe.name.equals("Short", ignoreCase = true) || recipe.name.contains("Short", ignoreCase = true)

    // One card, like every other settings block: the explanation and the controls belong to the
    // same object, so they read as one thing rather than loose rows on the page.
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.faded(0.30f),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Why these?",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = if (open) {
                        Icons.Default.KeyboardArrowUp
                    } else {
                        Icons.Default.KeyboardArrowDown
                    },
                    contentDescription = if (open) "Hide the tuning" else "Show the tuning",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp).clickable { open = !open },
                )
            }
            Text(
                text = explain(recipe),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (open) {
                SwitchSetting(
                    title = "Skip subscribed",
                    checked = hideSubscribed,
                    onChange = onHideSubscribed,
                )

                val length = recipe.filters.minDurationSec
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                ) {
                    listOf("Any" to null, "15+ min" to 900L, "42+ min" to 2520L).forEach { (label, sec) ->
                        FilterChip(
                            selected = length == sec,
                            onClick = {
                                onTune(recipe.copy(filters = recipe.filters.copy(minDurationSec = sec)))
                            },
                            label = { Text(label) },
                            colors = parallaxFilterChipColors(),
                        )
                    }
                }

                val age = recipe.filters.maxAgeDays
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                ) {
                    listOf("Any" to null, "7 days" to 7, "30 days" to 30, "90 days" to 90).forEach { (label, days) ->
                        FilterChip(
                            selected = age == days,
                            onClick = {
                                onTune(recipe.copy(filters = recipe.filters.copy(maxAgeDays = days)))
                            },
                            label = { Text(label) },
                            colors = parallaxFilterChipColors(),
                        )
                    }
                }

                val wildcard = recipe.walk.wildcard
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                ) {
                    listOf("Low" to 0.2, "Mixed" to 0.5, "High" to 0.85).forEach { (label, w) ->
                        val selected = kotlin.math.abs(wildcard - w) < 0.06
                        FilterChip(
                            selected = selected,
                            onClick = {
                                onTune(recipe.copy(walk = recipe.walk.copy(wildcard = w)))
                            },
                            label = { Text(label) },
                            colors = parallaxFilterChipColors(),
                        )
                    }
                }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                ) {
                    val noShorts = "shorts" in recipe.filters.excludeKeywords
                    val noLive = "live" in recipe.filters.excludeKeywords
                    if (!isShortRecipe) {
                        FilterChip(
                            selected = noShorts,
                            onClick = {
                                onTune(
                                    recipe.copy(
                                        filters = recipe.filters.copy(
                                            excludeKeywords = toggleKeyword(
                                                recipe.filters.excludeKeywords, "shorts", !noShorts,
                                            ),
                                        ),
                                    ),
                                )
                            },
                            label = { Text("Shorts") },
                            colors = parallaxFilterChipColors(),
                        )
                    }
                    FilterChip(
                        selected = noLive,
                        onClick = {
                            onTune(
                                recipe.copy(
                                    filters = recipe.filters.copy(
                                        excludeKeywords = toggleKeyword(
                                            recipe.filters.excludeKeywords, "live", !noLive,
                                        ),
                                    ),
                                ),
                            )
                        },
                        label = { Text("Live") },
                        colors = parallaxFilterChipColors(),
                    )
                }
            }
        }
    }
}

@Composable
private fun SwitchSetting(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

private fun explain(recipe: Recipe): String = buildString {
    append(
        when (recipe.seed.seed) {
            "history" -> "Related to ${recipe.seed.count} videos you watched"
            "topChannels" -> "Related to ${recipe.seed.count} of your top channels"
            "random" -> "Related to ${recipe.seed.count} random videos in your library"
            else -> "Related to your library"
        },
    )
    recipe.filters.minDurationSec?.let { append(" · at least ${it / 60} min") }
    recipe.filters.maxAgeDays?.let { append(" · newer than $it days") }
    if ("shorts" in recipe.filters.excludeKeywords) append(" · no Shorts")
    if ("live" in recipe.filters.excludeKeywords) append(" · no live")
}

private fun toggleKeyword(current: List<String>, keyword: String, add: Boolean): List<String> =
    if (add) (current + keyword).distinct() else current.filterNot { it == keyword }

/**
 * The same river as the notes field, at full width: three sine layers around the accent hue,
 * always flowing. It fills the empty screen without pretending to be a control.
 */
@Composable
private fun BoredRiver() {
    val transition = rememberInfiniteTransition(label = "bored")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(3200, easing = LinearEasing)),
        label = "phase",
    )
    val accent = MaterialTheme.colorScheme.primary
    val hues = listOf(accent.shiftHue(-14f), accent, accent.shiftHue(14f))

    Canvas(modifier = Modifier.fillMaxWidth().height(180.dp)) {
        val mid = size.height / 2f
        repeat(3) { layer ->
            val path = Path()
            val amplitude = size.height * (0.10f + 0.04f * layer)
            val frequency = (2.0 * PI / size.width.coerceAtLeast(1f)).toFloat() *
                (1.1f + layer * 0.45f)
            val shift = phase + layer * 1.7f
            var x = 0f
            while (x <= size.width) {
                val y = mid + (layer - 1) * size.height * 0.18f +
                    sin(x * frequency + shift) * amplitude
                if (x == 0f) path.moveTo(x, y) else path.lineTo(x, y)
                x += 3f
            }
            drawPath(
                path = path,
                color = hues[layer].copy(alpha = 0.35f * (1f - layer * 0.18f)),
                style = Stroke(width = 2.2f + layer * 0.9f),
            )
        }
    }
}

/** The same colour, a few degrees around the wheel. */
private fun Color.shiftHue(degrees: Float): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(toArgb(), hsv)
    hsv[0] = (hsv[0] + degrees + 360f) % 360f
    return Color(android.graphics.Color.HSVToColor(hsv))
}
