/*
 * Parallax — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.parallax.data.source

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShareClipTest {
    @Test
    fun `a plain link and a single moment carry no clip`() {
        assertEquals("dQw4w9WgXcQ", videoIdFromUrl("https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
        assertEquals(ShareClip(null, null), shareClipFrom("https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
        assertEquals(ShareClip(80_000, null), shareClipFrom("https://youtu.be/dQw4w9WgXcQ?t=80s"))
    }

    @Test
    fun `start and end query params form a clip`() {
        val text = "https://www.youtube.com/watch?v=dQw4w9WgXcQ&start=80&end=125\n1:20–2:05"
        assertEquals(ShareClip(80_000, 125_000), shareClipFrom(text))
    }

    @Test
    fun `a header range line alone forms a clip`() {
        val text = "https://youtu.be/dQw4w9WgXcQ\n1:20–2:05"
        assertEquals(ShareClip(80_000, 125_000), shareClipFrom(text))
    }

    @Test
    fun `an honest rejection keeps the id without inventing times`() {
        val text = "https://www.youtube.com/watch?v=dQw4w9WgXcQ"
        assertEquals("dQw4w9WgXcQ", videoIdFromUrl(text))
        assertEquals(90_000L, parseYouTubeTime("1m30s"))
        assertEquals(5_000L, parseYouTubeTime("5s"))
    }
}
