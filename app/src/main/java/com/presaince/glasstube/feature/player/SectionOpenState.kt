/*
 * GlassTube — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.glasstube.feature.player

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Which of the page's sections the reader left open, kept in the app's own preferences so the
 * next video opens the way this one was left. The owner's rule: a folded section that unfolds
 * again on every open is a setting the app forgets once a minute.
 *
 * One key per section, named after the enum entry, so a section inserted later cannot read
 * another's answer. The defaults are what the page shipped with: chapters and Up next are open,
 * the rest are a header until asked.
 */
object SectionOpenState {

    /** The app's one preferences file, the same one every other setting reads. */
    const val PREFS = "glasstube"

    const val KEY_PREFIX = "player_open_"

    fun key(section: PlayerSection): String = KEY_PREFIX + section.name

    /** What the page shows before the reader has said anything about this section. */
    fun default(section: PlayerSection): Boolean = when (section) {
        PlayerSection.Description -> false
        PlayerSection.Chapters -> true
        PlayerSection.Sponsor -> false
        PlayerSection.Notes -> false
        PlayerSection.Comments -> false
        PlayerSection.UpNext -> true
    }

    fun read(prefs: SharedPreferences, section: PlayerSection): Boolean =
        prefs.getBoolean(key(section), default(section))

    fun write(prefs: SharedPreferences, section: PlayerSection, open: Boolean) {
        prefs.edit().putBoolean(key(section), open).apply()
    }
}

/**
 * The open state of one section, remembered across videos. Reading happens once per composition
 * of the page; the write happens after the frame that changed it, so a recomposition per tick
 * does not touch the disk.
 */
@Composable
fun rememberSectionOpen(section: PlayerSection): MutableState<Boolean> {
    val context = LocalContext.current
    val prefs = remember {
        context.getSharedPreferences(SectionOpenState.PREFS, Context.MODE_PRIVATE)
    }
    val state = remember(section) { mutableStateOf(SectionOpenState.read(prefs, section)) }
    LaunchedEffect(section, state.value) {
        SectionOpenState.write(prefs, section, state.value)
    }
    return state
}
