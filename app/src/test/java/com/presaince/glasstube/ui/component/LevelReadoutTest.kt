/*
 * GlassTube — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.glasstube.ui.component

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class LevelReadoutTest {

    @Test
    fun `the readout sits clear above the finger`() {
        assertEquals(304.dp, readoutTop(fingerY = 400.dp, pictureHeight = 915.dp))
    }

    @Test
    fun `a finger near the top puts the readout below it`() {
        assertEquals(132.dp, readoutTop(fingerY = 60.dp, pictureHeight = 915.dp))
    }

    @Test
    fun `the readout never leaves the picture`() {
        assertEquals(8.dp, readoutTop(fingerY = 0.dp, pictureHeight = 40.dp))
        assertEquals(851.dp, readoutTop(fingerY = 2000.dp, pictureHeight = 915.dp))
    }

    @Test
    fun `the readout is centred on the finger and held inside the width`() {
        assertEquals(144.dp, readoutLeft(fingerX = 200.dp, pictureWidth = 411.dp))
        assertEquals(0.dp, readoutLeft(fingerX = 10.dp, pictureWidth = 411.dp))
        assertEquals(299.dp, readoutLeft(fingerX = 400.dp, pictureWidth = 411.dp))
    }
}
