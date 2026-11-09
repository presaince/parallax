/*
 * Parallax — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.parallax.feature.onboarding

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.presaince.parallax.R
import com.presaince.parallax.core.AppResult
import com.presaince.parallax.data.importer.SubscriptionImportSummary
import com.presaince.parallax.data.importer.importSubscriptions
import com.presaince.parallax.di.AppContainer
import com.presaince.parallax.feature.settings.AccentGrid
import com.presaince.parallax.feature.settings.GestureTutorial
import com.presaince.parallax.feature.settings.PreviewCard
import com.presaince.parallax.ui.component.CardLayout
import com.presaince.parallax.ui.component.CardStyle
import com.presaince.parallax.ui.theme.ThemeBackdrop
import com.presaince.parallax.ui.theme.ThemeController
import com.presaince.parallax.ui.theme.ThemeMode
import com.presaince.parallax.ui.theme.accentTextColour
import com.presaince.parallax.ui.theme.faded
import com.presaince.parallax.ui.theme.parallaxFilterChipColors
import kotlinx.coroutines.launch

private sealed interface ImportUi {
    data object Idle : ImportUi
    data object Busy : ImportUi
    data class Done(val summary: SubscriptionImportSummary) : ImportUi
    data class Failed(val message: String) : ImportUi
}

private data class ThemeChoice(val mode: ThemeMode, val label: String, val icon: Int)

private val themeChoices = listOf(
    ThemeChoice(ThemeMode.SYSTEM, "System", R.drawable.ic_auto),
    ThemeChoice(ThemeMode.GLASS, "Glass", R.drawable.ic_glass),
    ThemeChoice(ThemeMode.DARK, "Dark", R.drawable.ic_moon),
    ThemeChoice(ThemeMode.LIGHT, "Light", R.drawable.ic_sun),
)

/**
 * The first-run setup: look, ads, the gestures, then bring your subscriptions. Four pages, one
 * question each, and nothing here can be reached without finishing or skipping.
 *
 * The theme is written as it is tapped, not on Finish: the page is showing the real app, so a
 * force-close must not leave the preview claiming a look the app does not have. SponsorBlock is
 * the same. The only thing held until the end is [markOnboardingDone].
 *
 * [rerun] is true when the wizard was opened from Settings. Then finishing returns to the page it
 * was opened from instead of revealing the feed, and system Back on the first page closes it.
 */
@Composable
fun OnboardingScreen(
    container: AppContainer,
    theme: ThemeController,
    rerun: Boolean,
    onDone: () -> Unit,
) {
    val context = LocalContext.current
    val prefs = container.preferences
    val scope = rememberCoroutineScope()

    var page by remember { mutableStateOf(OnboardingPage.LOOK) }
    // The page starts on the truth: SponsorBlock on means "No ads, ever" is already the answer.
    // A re-run where it was turned off opens on the other row instead of lying.
    var wantsAds by remember { mutableStateOf(!container.playback.state.value.sponsorSkip) }
    var importState by remember { mutableStateOf<ImportUi>(ImportUi.Idle) }

    val importer = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        importState = ImportUi.Busy
        scope.launch {
            importState = when (
                val result = importSubscriptions(container.db, context.contentResolver, uri)
            ) {
                is AppResult.Ok -> ImportUi.Done(result.value)
                is AppResult.Err -> ImportUi.Failed(result.message)
            }
        }
    }

    val busy = importState is ImportUi.Busy
    val notifications = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { complete(prefs, onDone) }

    fun finish() {
        val granted = context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (granted || rerun) {
            // A re-run is a settings errand, not a first launch: it does not ask for anything.
            complete(prefs, onDone)
        } else {
            // Asked once, while the wizard is still the screen, so the system dialog has context.
            notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // One handler, always on. A first run consumes Back on its first page rather than letting the
    // Activity finish into a half-set-up app that reappears next launch; a re-run uses it to close.
    BackHandler {
        when {
            !page.isFirst -> page = page.previous()
            rerun -> {
                markOnboardingDone(prefs)
                onDone()
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        ThemeBackdrop(modifier = Modifier.fillMaxSize())
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 20.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${page.ordinal + 1} / ${OnboardingPage.COUNT}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (!page.isLast) {
                    TextButton(
                        onClick = {
                            markOnboardingDone(prefs)
                            onDone()
                        },
                        enabled = !busy,
                    ) {
                        Text("Skip")
                    }
                } else {
                    // The row keeps its height when Skip leaves, so the content does not jump.
                    Spacer(Modifier.size(48.dp))
                }
            }

            AnimatedContent(
                targetState = page,
                transitionSpec = {
                    val forward = targetState.ordinal > initialState.ordinal
                    (fadeIn(tween(180)) +
                        slideInHorizontally(tween(180)) { if (forward) it / 8 else -it / 8 })
                        .togetherWith(
                            fadeOut(tween(120)) +
                                slideOutHorizontally(tween(120)) {
                                    if (forward) -it / 8 else it / 8
                                },
                        )
                },
                label = "onboarding-page",
                modifier = Modifier.weight(1f),
            ) { shown ->
                when (shown) {
                    OnboardingPage.LOOK -> LookPage(theme)
                    OnboardingPage.ADS -> AdsPage(
                        wantsAds = wantsAds,
                        onAnswer = { answer ->
                            wantsAds = answer
                            container.playback.setSponsorSkip(
                                sponsorBlockAfterAdsAnswer(answer),
                            )
                        },
                    )
                    OnboardingPage.DEMO -> DemoPage()
                    OnboardingPage.START -> StartPage(
                        importState = importState,
                        onImport = { importer.launch(arrayOf("*/*")) },
                    )
                }
            }

            ProgressBar(
                fraction = (page.ordinal + 1f) / OnboardingPage.COUNT,
                modifier = Modifier.padding(top = 12.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (!page.isFirst) {
                    IconButton(
                        onClick = { page = page.previous() },
                        enabled = !busy,
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                } else {
                    // The same slot, so the primary does not widen on the first page.
                    Spacer(Modifier.size(48.dp))
                }
                Button(
                    onClick = {
                        if (page.isLast) finish() else page = page.next()
                    },
                    enabled = !busy,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                ) {
                    Text(
                        text = when {
                            !page.isLast -> "Next"
                            importState is ImportUi.Done -> "Open feed"
                            else -> "Start with nothing"
                        },
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

/** The one write that ends the wizard, after the optional notification prompt. */
private fun complete(
    prefs: android.content.SharedPreferences,
    onDone: () -> Unit,
) {
    markOnboardingDone(prefs)
    onDone()
}

@Composable
private fun LookPage(theme: ThemeController) {
    val context = LocalContext.current
    var cardStyle by remember { mutableStateOf(CardStyle.read(context)) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 8.dp),
    ) {
        WelcomeHero()
        Spacer(Modifier.height(18.dp))
        Panel("Theme") {
            ThemeTiles(theme)
        }
        Spacer(Modifier.height(12.dp))
        Panel("Accent") {
            AccentGrid(theme)
        }
        Spacer(Modifier.height(12.dp))
        Panel("Card preview") {
            LayoutPicker(
                selected = cardStyle.layout,
                onPick = { layout ->
                    val next = cardStyle.copy(layout = layout)
                    cardStyle = next
                    CardStyle.write(context, next)
                },
            )
            Spacer(Modifier.height(10.dp))
            PreviewCard(style = cardStyle)
        }
    }
}

@Composable
private fun Panel(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.faded(0.30f),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = accentTextColour(),
            )
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun ThemeTiles(theme: ThemeController) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        themeChoices.forEach { choice ->
            val active = theme.mode == choice.mode
            Column(
                modifier = Modifier
                    .weight(1f)
                    .height(64.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (active) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.faded(0.55f)
                        },
                    )
                    .border(
                        width = if (active) 1.5.dp else 1.dp,
                        color = if (active) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outlineVariant
                        },
                        shape = RoundedCornerShape(12.dp),
                    )
                    .clickable { theme.chooseMode(choice.mode) }
                    .semantics {
                        selected = active
                        role = Role.RadioButton
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    painter = painterResource(choice.icon),
                    contentDescription = null,
                    tint = if (active) {
                        accentTextColour()
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = choice.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (active) {
                        accentTextColour()
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun LayoutPicker(selected: CardLayout, onPick: (CardLayout) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CardLayout.entries.forEach { layout ->
            FilterChip(
                selected = layout == selected,
                onClick = { onPick(layout) },
                label = { Text(layout.label) },
                colors = parallaxFilterChipColors(),
            )
        }
    }
}

@Composable
private fun AdsPage(
    wantsAds: Boolean,
    onAnswer: (Boolean) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 8.dp),
    ) {
        Text(
            text = "Do you want ads?",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(top = 4.dp),
        )
        Spacer(Modifier.height(18.dp))
        AdChoice(
            selected = !wantsAds,
            title = "No ads, ever",
            body = "Sponsor reads are skipped too.",
            onClick = { onAnswer(false) },
        )
        Spacer(Modifier.height(10.dp))
        AdChoice(
            selected = wantsAds,
            title = "I actually like ads",
            body = "There are none anyway. Sponsor reads stay.",
            onClick = { onAnswer(true) },
        )
        Text(
            text = "YouTube ads are not in the stream. SponsorBlock skips sponsor reads inside " +
                "videos.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 18.dp),
        )
    }
}

@Composable
private fun AdChoice(
    selected: Boolean,
    title: String,
    body: String,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.faded(0.30f)
                },
            )
            .border(
                width = if (selected) 1.5.dp else 1.dp,
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outlineVariant
                },
                shape = shape,
            )
            .clickable(onClick = onClick)
            .semantics {
                this.selected = selected
                role = Role.RadioButton
            },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .border(
                        width = 2.dp,
                        color = if (selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        shape = CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                    )
                }
            }
            Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (selected) {
                        accentTextColour()
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun DemoPage() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 8.dp),
    ) {
        Text(
            text = "Try the player",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
        )
        GestureTutorial()
    }
}

@Composable
private fun StartPage(
    importState: ImportUi,
    onImport: () -> Unit,
) {
    val canImport = importState !is ImportUi.Busy && importState !is ImportUi.Done
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 8.dp),
    ) {
        Text(
            text = "Bring your feed",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(top = 4.dp),
        )
        Spacer(Modifier.height(16.dp))
        Surface(
            onClick = onImport,
            enabled = canImport,
            color = MaterialTheme.colorScheme.surfaceVariant.faded(0.30f),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(
                            if (importState is ImportUi.Done) {
                                R.drawable.ic_check
                            } else {
                                R.drawable.ic_data
                            },
                        ),
                        contentDescription = null,
                        tint = accentTextColour(),
                        modifier = Modifier.size(24.dp),
                    )
                }
                Column(modifier = Modifier.weight(1f).padding(start = 14.dp)) {
                    Text(
                        text = "Import subscriptions",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    when (importState) {
                        is ImportUi.Busy -> Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 4.dp),
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                            )
                            Text(
                                text = "Reading the file",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                        is ImportUi.Done -> Text(
                            text = importState.summary.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = accentTextColour(),
                            modifier = Modifier.padding(top = 2.dp),
                        )
                        is ImportUi.Failed -> Text(
                            text = importState.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                        ImportUi.Idle -> Text(
                            text = "NewPipe, LibreTube or Google Takeout.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
                if (canImport) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 6.dp).size(20.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ProgressBar(fraction: Float, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(4.dp)
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(4.dp)
                .background(MaterialTheme.colorScheme.primary),
        )
    }
}
