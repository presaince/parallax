/*
 * Parallax — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.parallax.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.presaince.parallax.BuildConfig
import com.presaince.parallax.data.auth.PostingManager
import com.presaince.parallax.data.resync.ResyncStore
import com.presaince.parallax.playback.PlaybackController
import com.presaince.parallax.ui.theme.ThemeController
import com.presaince.parallax.ui.theme.ThemeMode
import com.presaince.parallax.ui.theme.faded
import android.content.Context

/**
 * The root of Settings: one search field, then a page per section. Nothing lives directly on this
 * list, so every setting has a single home and the search result can take you straight to it.
 */
@Composable
fun SettingsScreen(
    playback: PlaybackController,
    resyncStore: ResyncStore,
    posting: PostingManager,
    theme: ThemeController,
    onOpenSection: (SettingsSectionId) -> Unit,
) {
    val playbackState by playback.state.collectAsStateWithLifecycle()
    val resync = remember { resyncStore.load() }
    val postingState = remember { posting.settings() }
    val keyboard = LocalSoftwareKeyboardController.current
    val context = LocalContext.current
    val hideLive = remember {
        context.getSharedPreferences("parallax", Context.MODE_PRIVATE)
            .getBoolean("hide_live", false)
    }
    val speeds = remember { playback.speedPresets() }
    val regionLabel = remember {
        val codes = com.presaince.parallax.data.source.readContentRegions(context)
        val name = com.presaince.parallax.data.source.contentRegions
            .firstOrNull { it.code == codes.first() }?.name ?: codes.first()
        if (codes.size > 1) "$name +${codes.size - 1}" else name
    }

    var query by remember { mutableStateOf("") }
    val searching = query.isNotBlank()
    val results = remember(query) { searchSettings(query) }

    val summaries = mapOf(
        SettingsSectionId.APPEARANCE to "${modeLabel(theme.mode)} · ${theme.accent.label}",
        SettingsSectionId.PLAYBACK to buildString {
            append("%.1f–%.1f×".format(speeds.first(), speeds.last()))
            append(" · ")
            append(if (hideLive) "live hidden" else "live shown")
        },
        SettingsSectionId.FEEDBACK to if (
            context.getSharedPreferences("parallax", Context.MODE_PRIVATE)
                .getBoolean("haptics_enabled", true)
        ) "Haptics on" else "Haptics off",
        SettingsSectionId.REGION to regionLabel,
        SettingsSectionId.SPONSOR to if (playbackState.sponsorSkip) {
            "On · ${playbackState.sponsorCategories.size} categories"
        } else {
            "Off"
        },
        SettingsSectionId.ACCOUNTS to when {
            resync.enabled -> "AI on"
            postingState.configured -> "Google set up"
            else -> "Off"
        },
        SettingsSectionId.DATA to "Import and export subscriptions",
        SettingsSectionId.DIAGNOSTICS to "Repair summary, logs, settings export",
        SettingsSectionId.ABOUT to "Version ${BuildConfig.VERSION_NAME}",
    )

    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            placeholder = { Text("Search settings") },
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = null)
            },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = "" }) {
                        Icon(Icons.Default.Close, contentDescription = "Clear the search")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(50),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = Color.Transparent,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.faded(0.5f),
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.faded(0.5f),
            ),
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (!searching) {
                // Three cards across, however many rows that takes: the whole of Settings visible
                // at once instead of one long column of rows.
                items(
                    SettingsSectionId.entries.chunked(3),
                    key = { it.first().name },
                ) { chunk ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        chunk.forEach { section ->
                            SectionCard(
                                section = section,
                                summary = summaries[section].orEmpty(),
                                onClick = { onOpenSection(section) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        repeat(3 - chunk.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            } else if (results.isEmpty()) {
                item {
                    Text(
                        text = "Nothing matches \u201C$query\u201D. Try a word from the row you " +
                            "remember, like sponsor, speed or theme.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 16.dp),
                    )
                }
            } else {
                items(results, key = { it.title }) { entry ->
                    ResultRow(entry = entry, onClick = { onOpenSection(entry.section) })
                }
            }
        }
    }
}

private fun modeLabel(mode: ThemeMode): String = when (mode) {
    ThemeMode.SYSTEM -> "System theme"
    ThemeMode.DARK -> "Dark"
    ThemeMode.LIGHT -> "Light"
    ThemeMode.GLASS -> "Glass"
}

@Composable
private fun SectionCard(
    section: SettingsSectionId,
    summary: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.faded(0.35f))
            .clickable(onClick = onClick)
            .heightIn(min = 108.dp)
            .padding(horizontal = 8.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(section.icon),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp),
            )
        }
        Text(
            text = section.title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
        if (summary.isNotBlank()) {
            Text(
                text = summary,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ResultRow(entry: SettingsEntry, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .heightIn(min = 60.dp)
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(entry.section.icon),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = entry.section.title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
