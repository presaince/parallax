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
class ThemeController(context: Context) {
    private val context = context.applicationContext

    var mode: ThemeMode by mutableStateOf(readThemeMode(context))
        private set

    var accent: Accent by mutableStateOf(readAccent(context))
        private set

    /** Glass only: an escape hatch for contrast or performance, remembered like the theme. */
    var solidSurfaces: Boolean by mutableStateOf(readSolidSurfaces(context))
        private set

    /** Glass only: continuous 0..1 fill, 0 clearest to 1 nearly solid. Default 0.42. */
    var glassLevel: Float by mutableStateOf(readGlassLevel(context))
        private set

    /** How hard the theme works for readable text: Off, AA or AAA. Default AA. */
    var contrast: ContrastPolicy by mutableStateOf(readContrast(context))
        private set

    /** Glass only: which way the transparency leans — the last ordinary theme that was chosen. */
    var glassBase: ThemeMode by mutableStateOf(readGlassBase(context))
        private set

    /**
     * The dial's ceiling for the current accent, mode and policy. Cached because the drag asks on
     * every move, and the search behind it is far too expensive for a per-frame question.
     *
     * Declared below [glassBase] on purpose: this initialiser reads [dark], and Kotlin runs property
     * initialisers top to bottom, so above it the delegate is still null.
     */
    private var levelCap: Float = maxGlassLevel(accent, dark, solidSurfaces, contrast)

    /** A new accent on a rhythm the reader picked: daily, hourly, or on every open. */
    var shuffle: ShuffleCadence by mutableStateOf(readShuffleCadence(context))
        private set

    /** Kept for older call sites: any cadence but off is "shuffling". */
    val accentDaily: Boolean get() = shuffle != ShuffleCadence.OFF

    /**
     * Called each time the app returns to the foreground. The first call comes from the theme
     * lifecycle wiring on cold start, later calls from each resume, so midnight and hour
     * boundaries crossed while away are caught here rather than at the next process start.
     */
    fun onForeground() {
        if (!shouldRollOnForeground(shuffle, readAccentDay(context), periodKey())) return
        rollAccent()
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
        clampGlassLevel()
    }

    fun chooseAccent(next: Accent) {
        accent = next
        writeAccent(context, next)
        // Picking a colour by hand is a decision; the shuffle starts its period from here.
        if (shuffle != ShuffleCadence.OFF) writeAccentDay(context, periodKey())
        // A different hue can carry a different amount of glass before text stops reading.
        clampGlassLevel()
    }

    fun chooseSolidSurfaces(solid: Boolean) {
        solidSurfaces = solid
        writeSolidSurfaces(context, solid)
        clampGlassLevel()
    }

    fun chooseGlassLevel(level: Float, persist: Boolean = true) {
        glassLevel = level.coerceIn(0f, levelCap)
        // The drag writes state every move but only touches prefs on release, so the
        // finger gets a live fill without a disk round trip per pixel.
        if (persist) writeGlassLevel(context, glassLevel)
    }

    /**
     * The transparent dial stops where text stops reading. The cap belongs to the accent and the
     * ratio, so it moves when either does — which is why the stored level is clamped rather than
     * the drawing being quietly adjusted: a dial that shows 1.0 while painting 0.5 is a lie.
     */
    fun chooseContrast(next: ContrastPolicy) {
        contrast = next
        writeContrast(context, next)
        clampGlassLevel()
    }

    private fun clampGlassLevel() {
        levelCap = maxGlassLevel(accent, dark, solidSurfaces, contrast)
        if (glassLevel > levelCap) {
            glassLevel = levelCap
            writeGlassLevel(context, glassLevel)
        }
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

    /**
     * Re-read every stored choice. A backup restore writes preferences directly — the file holds
     * stored tokens, not typed values — so the live theme has to be told afterwards; without this,
     * an imported accent and theme only appear on the next launch.
     */
    fun reload() {
        mode = readThemeMode(context)
        glassBase = readGlassBase(context)
        accent = readAccent(context)
        contrast = readContrast(context)
        shuffle = readShuffleCadence(context)
        solidSurfaces = readSolidSurfaces(context)
        glassLevel = readGlassLevel(context)
        clampGlassLevel()
    }

    private fun periodKey(): String =
        periodKeyFor(shuffle, LocalDate.now(), LocalDateTime.now())

    private fun rollAccent() {
        val key = periodKey()
        val pool = Accents.all.filter { candidate ->
            candidate.name != accent.name &&
                accentHolds(
                    candidate,
                    contrast,
                    glass = mode == ThemeMode.GLASS,
                    dark = dark,
                    solid = solidSurfaces,
                    level = glassLevel,
                )
        }
        // The period is marked as handled either way: retrying on every resume would re-run the
        // 18-accent check for a wheel that has not changed.
        if (pool.isEmpty()) {
            writeAccentDay(context, key)
            return
        }
        val next = pool.random()
        accent = next
        writeAccent(context, next)
        writeAccentDay(context, key)
        clampGlassLevel()
    }
}

/** The stored key for a cadence at a moment: the day for daily, the hour for hourly. */
fun periodKeyFor(cadence: ShuffleCadence, date: LocalDate, dateTime: LocalDateTime): String =
    when (cadence) {
        ShuffleCadence.OFF -> ""
        ShuffleCadence.DAILY -> date.toString()
        ShuffleCadence.HOURLY -> dateTime.format(
            java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd-HH"),
        )
        // Focus has no period; every foreground is the event, not the clock.
        ShuffleCadence.ON_OPEN -> ""
    }

/** Whether a foreground should move the accent for the picked cadence. */
fun shouldRollOnForeground(
    cadence: ShuffleCadence,
    storedKey: String?,
    periodKey: String,
): Boolean = when (cadence) {
    ShuffleCadence.OFF -> false
    ShuffleCadence.ON_OPEN -> true
    ShuffleCadence.DAILY, ShuffleCadence.HOURLY -> storedKey != periodKey
}
