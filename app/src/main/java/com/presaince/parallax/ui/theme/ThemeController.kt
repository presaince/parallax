/*
 * Parallax — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.parallax.ui.theme

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Theme and accent as live state. Changing either must not recreate the activity: that would throw
 * away the screen you changed it from, which is exactly where you want to see the result.
 */
class ThemeController(private val context: Context) {

    var mode: ThemeMode by mutableStateOf(readThemeMode(context))
        private set

    var accent: Accent by mutableStateOf(readAccent(context))
        private set

    /** Glass only: an escape hatch for contrast or performance, remembered like the theme. */
    var solidSurfaces: Boolean by mutableStateOf(readSolidSurfaces(context))
        private set

    /** Glass only: ten levels, 1 clearest to 10 nearly solid. */
    var glassLevel: Int by mutableStateOf(readGlassLevel(context))
        private set

    /** Glass only: which way the transparency leans — the last ordinary theme that was chosen. */
    var glassBase: ThemeMode by mutableStateOf(readGlassBase(context))
        private set

    /** A new accent on a rhythm the reader picked: daily, hourly, or on every open. */
    var shuffle: ShuffleCadence by mutableStateOf(readShuffleCadence(context))
        private set

    /** Kept for older call sites: any cadence but off is "shuffling". */
    val accentDaily: Boolean get() = shuffle != ShuffleCadence.OFF

    init {
        // The roll happens once, on the first read of the period. A process that lives through
        // midnight keeps yesterday's colour until it is next opened, which is fine: nobody is
        // sitting in the app waiting for a colour change at 00:00.
        when (shuffle) {
            ShuffleCadence.OFF -> Unit
            // "On open" has no key to compare against, so this process always rolls once.
            ShuffleCadence.ON_OPEN -> rollAccent(force = true)
            else -> rollAccent(force = false)
        }
    }

    val dark: Boolean get() = isDarkTheme(mode, context, glassDark)

    /** What the Glass theme should do about light and dark, from the base it remembers. */
    val glassDark: Boolean? get() = glassDarkFor(glassBase)

    fun chooseMode(next: ThemeMode) {
        // Glass takes its light or dark from the last ordinary theme: tap Dark, then Glass, and
        // the glass is dark. That is why the picker has no second row of chips for it.
        if (next != ThemeMode.GLASS) {
            glassBase = next
            writeGlassBase(context, next)
        }
        mode = next
        writeThemeMode(context, next)
    }

    fun chooseAccent(next: Accent) {
        accent = next
        writeAccent(context, next)
        // Picking a colour by hand is a decision; the shuffle starts its period from here.
        if (shuffle != ShuffleCadence.OFF) writeAccentDay(context, periodKey())
    }

    fun chooseSolidSurfaces(solid: Boolean) {
        solidSurfaces = solid
        writeSolidSurfaces(context, solid)
    }

    fun chooseGlassLevel(level: Int) {
        glassLevel = level.coerceIn(1, 10)
        writeGlassLevel(context, glassLevel)
    }

    fun chooseShuffle(cadence: ShuffleCadence) {
        shuffle = cadence
        writeShuffleCadence(context, cadence)
        // Choosing a rhythm records the rhythm, nothing else. It used to roll the accent on the
        // spot, so tapping a pill repainted the whole app and made the tap look like a colour
        // change. The colour moves at the period boundary, where the label says it will.
    }

    fun chooseAccentDaily(daily: Boolean) {
        chooseShuffle(if (daily) ShuffleCadence.DAILY else ShuffleCadence.OFF)
    }

    private fun periodKey(): String = when (shuffle) {
        ShuffleCadence.OFF -> ""
        ShuffleCadence.DAILY -> LocalDate.now().toString()
        ShuffleCadence.HOURLY -> LocalDateTime.now()
            .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd-HH"))
        ShuffleCadence.ON_OPEN -> "open-${System.currentTimeMillis()}"
    }

    private fun rollAccent(force: Boolean) {
        if (shuffle == ShuffleCadence.OFF) return
        val key = periodKey()
        if (!force && readAccentDay(context) == key) return
        val next = Accents.all.filter { it.name != accent.name }.random()
        accent = next
        writeAccent(context, next)
        writeAccentDay(context, key)
    }
}
