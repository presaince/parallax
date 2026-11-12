/*
 * GlassTube — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.glasstube.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SoundTest {

    @Test
    fun `every preset has one gain per band`() {
        SoundPreset.entries.forEach { preset ->
            assertEquals(
                "${preset.key} bands",
                SOUND_BANDS_HZ.size,
                preset.gainsDb.size,
            )
        }
    }

    @Test
    fun `flat is silent on every band`() {
        assertTrue(SoundPreset.FLAT.gainsDb.all { it == 0f })
    }

    @Test
    fun `voice lifts the speech band and takes the rumble out`() {
        val gains = SoundPreset.VOICE.gainsDb
        assertTrue(gains[2] > 0f)
        assertTrue(gains[0] < 0f)
    }

    @Test
    fun `bass lifts the bottom and treble lifts the top`() {
        assertTrue(SoundPreset.BASS.gainsDb[0] > 0f)
        assertTrue(SoundPreset.TREBLE.gainsDb.last() > 0f)
    }

    @Test
    fun `an unknown stored key reads as flat rather than failing`() {
        assertEquals(SoundPreset.FLAT, SoundPreset.fromKey(null))
        assertEquals(SoundPreset.FLAT, SoundPreset.fromKey("nonsense"))
        assertEquals(SoundPreset.VOICE, SoundPreset.fromKey("voice"))
    }

    @Test
    fun `the level is millibels, clamped to what the control offers`() {
        assertEquals(0, levelToMillibels(-4))
        assertEquals(600, levelToMillibels(6))
        assertEquals(SOUND_MAX_LEVEL_DB * 100, levelToMillibels(99))
    }
}
