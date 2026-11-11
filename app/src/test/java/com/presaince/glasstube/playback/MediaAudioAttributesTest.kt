/*
 * GlassTube — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.glasstube.playback

import androidx.media3.common.C
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The soundtrack that would not stop. Opening NewPipe and playing a video left both audios running,
 * because the player was built without audio focus and so never heard the second app ask for it.
 *
 * Only half of that rule is testable here: the attributes the player announces are values, so the
 * usage is pinned. `handleAudioFocus = true` is a builder argument, and whether another app's video
 * actually stops this one can only be seen with two players and a speaker — the phone check.
 */
class MediaAudioAttributesTest {

    @Test
    fun the_player_announces_itself_as_media() {
        assertEquals(C.USAGE_MEDIA, mediaAudioAttributes().usage)
    }
}
