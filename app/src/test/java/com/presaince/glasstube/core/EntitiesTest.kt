/*
 * GlassTube — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.glasstube.core

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A caption cue arrives as one line with the entities still in it, so the decoder has to be
 * right without Android: an apostrophe is `&#39;`, and everything that is not an entity has to
 * come back untouched.
 */
class EntitiesTest {

    @Test
    fun `a decimal apostrophe becomes the mark`() {
        assertEquals("It's fine", decodeEntities("It&#39;s fine"))
    }

    @Test
    fun `a hex apostrophe becomes the mark`() {
        assertEquals("It's fine", decodeEntities("It&#x27;s fine"))
    }

    @Test
    fun `named entities are decoded`() {
        assertEquals("a & b < c > d \"e\"", decodeEntities("a &amp; b &lt; c &gt; d &quot;e&quot;"))
    }

    @Test
    fun `an emoji code point survives as one character`() {
        // The camera is one code point above the basic plane, which is where a naive toChar fails.
        assertEquals("\uD83D\uDCF7", decodeEntities("&#128247;"))
    }

    @Test
    fun `text with no entity comes back unchanged`() {
        assertEquals("Tom & Jerry", decodeEntities("Tom & Jerry"))
        assertEquals("100% #1", decodeEntities("100% #1"))
    }

    @Test
    fun `an unknown name is left alone`() {
        assertEquals("&wat; is not an entity", decodeEntities("&wat; is not an entity"))
    }

    @Test
    fun `a broken number is left alone`() {
        assertEquals("&#99999999;", decodeEntities("&#99999999;"))
        assertEquals("&#xD800;", decodeEntities("&#xD800;"))
    }
}
