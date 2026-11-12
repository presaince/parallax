/*
 * GlassTube — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.glasstube.feature.player

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Where a dot on the share bar is allowed to land. The nudge row acts on whichever dot was touched
 * last, so both of them can be asked to move; the rules that keep them inside the video and apart
 * are the part worth checking without a phone.
 */
class ShareBoundaryTest {

    /** A ten-minute video unless a case says otherwise. */
    private val total = 600f

    @Test
    fun `a nudge on the start stops a second before the end`() {
        assertEquals(
            99f,
            boundaryAt(120f, ClipEdge.Start, start = 60f, end = 100f, totalSeconds = total, hasClip = true),
            0.001f,
        )
    }

    @Test
    fun `a nudge on the end stops a second after the start`() {
        assertEquals(
            61f,
            boundaryAt(0f, ClipEdge.End, start = 60f, end = 100f, totalSeconds = total, hasClip = true),
            0.001f,
        )
    }

    @Test
    fun `the end cannot leave the video`() {
        assertEquals(
            600f,
            boundaryAt(900f, ClipEdge.End, start = 60f, end = 100f, totalSeconds = total, hasClip = true),
            0.001f,
        )
    }

    @Test
    fun `a moment cannot be nudged below zero`() {
        assertEquals(
            0f,
            boundaryAt(-30f, ClipEdge.Start, start = 10f, end = 11f, totalSeconds = total, hasClip = false),
            0.001f,
        )
    }

    @Test
    fun `a moment cannot be nudged past the last second`() {
        assertEquals(
            599f,
            boundaryAt(900f, ClipEdge.Start, start = 300f, end = 301f, totalSeconds = total, hasClip = false),
            0.001f,
        )
    }

    @Test
    fun `an end with no dot on screen stays where it is`() {
        assertEquals(
            11f,
            boundaryAt(400f, ClipEdge.End, start = 10f, end = 11f, totalSeconds = total, hasClip = false),
            0.001f,
        )
    }

    @Test
    fun `the spoken nudge names the dot it moves`() {
        assertEquals("10 seconds earlier, end", formatNudgeSpeech(-10f, ClipEdge.End))
        assertEquals("30 seconds later, start", formatNudgeSpeech(30f, ClipEdge.Start))
    }
}
