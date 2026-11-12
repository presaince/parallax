/*
 * GlassTube — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.glasstube.core

import org.junit.Test

class QualityTest {

    @Test
    fun `Auto starts at 720 on every connection`() {
        assert(firstLoadHeight(0) == 720)
    }

    @Test
    fun `a chosen height wins over the first load cap`() {
        assert(firstLoadHeight(480) == 480)
        assert(firstLoadHeight(1080) == 1080)
        assert(firstLoadHeight(2160) == 2160)
    }

    @Test
    fun `the ladder is youtube's heights with Auto last`() {
        assert(QUALITY_STOPS.last().height == 0)
        assert(
            QUALITY_STOPS.map { it.height }.toSet() ==
                setOf(144, 240, 360, 480, 720, 1080, 1440, 2160, 0),
        )
        // Nothing the phone cannot decode: no 6K, no 8K, no uncapped joke stop.
        assert(QUALITY_STOPS.none { it.height > 2160 })
    }

    @Test
    fun `the label reports the height that is actually playing`() {
        assert(qualityLabel(1080, 720) == "Full HD (720p)")
        assert(qualityLabel(1080, 1080) == "Full HD")
        assert(qualityLabel(0, 1440) == "Auto (1440p)")
        assert(qualityLabel(720, 0) == "HD")
        // A value the ladder does not carry keeps its number rather than pretending.
        assert(qualityLabel(900, 0) == "900p")
    }

    @Test
    fun `a stalling video steps down the ladder, and nothing is offered below the bottom`() {
        val phone = qualityStops(1080)
        assert(lowerStop(1080, phone)?.height == 720)
        assert(lowerStop(720, phone)?.height == 480)
        // Auto is a ceiling nobody can name, so it steps to the stop a phone can hold.
        assert(lowerStop(0, phone)?.height == 720)
        // The smallest stop has nowhere lower to go, and saying so is the caller's job.
        assert(lowerStop(144, phone) == null)
        assert(lowerStop(360, emptyList()) == null)
    }

    @Test
    fun `a 2x stream runs out of runway twice as fast`() {
        // Eight seconds of buffer is a comfortable lead at 1x and four at 2x, which is the whole
        // reason 2x buffers where 1x does not.
        assert(!choppyWater(8_000, 1f))
        assert(choppyWater(8_000, 2f))
        // The same lead in wall time is fine at either speed.
        assert(!choppyWater(12_000, 1f))
        assert(!choppyWater(12_000, 2f))
        // Nothing buffered is always choppy; the caller decides when that counts.
        assert(choppyWater(0, 1f))
    }

    @Test
    fun `one choppy moment is noise and three in half a minute is not`() {
        val now = 1_000_000L
        assert(!shouldDegrade(listOf(now), now, DEGRADE_TOLERANCE_DEFAULT))
        assert(!shouldDegrade(listOf(now - 5_000, now), now, DEGRADE_TOLERANCE_DEFAULT))
        assert(
            shouldDegrade(
                listOf(now - 20_000, now - 10_000, now),
                now,
                DEGRADE_TOLERANCE_DEFAULT,
            ),
        )
    }

    @Test
    fun `episodes older than the window stop counting`() {
        val now = 1_000_000L
        val old = listOf(now - 31_000, now - 40_000, now - 60_000)
        assert(freshEpisodes(old, now).isEmpty())
        assert(!shouldDegrade(old, now, 1))
        // A patience of zero still means one: a rule that always fires is not a setting.
        assert(shouldDegrade(listOf(now), now, 0))
    }

    @Test
    fun `the ladder stops at what the panel can show`() {
        val phone = qualityStops(1080)
        assert(phone.none { it.height > 1080 })
        assert(phone.last().height == 0)
        assert(qualityStops(1440).any { it.height == 1440 })
        // An unmeasurable panel keeps the whole ladder rather than collapsing to Auto.
        assert(qualityStops(0).size == QUALITY_STOPS.size)
    }

    @Test
    fun `a stop above the panel falls to the tallest the panel offers`() {
        assert(snapToLadder(2160, 1080) == 1080)
        assert(snapToLadder(1440, 1080) == 1080)
        assert(snapToLadder(480, 1080) == 480)
        assert(snapToLadder(0, 1080) == 0)
        // A height that is not a stop lands on the closest one below it.
        assert(snapToLadder(400, 1080) == 360)
    }

    @Test
    fun `cellular reads its own ladder and everything else reads wifi`() {
        assert(qualityFor(Transport.Cellular, wifiHeight = 1080, cellularHeight = 480) == 480)
        assert(qualityFor(Transport.Wifi, wifiHeight = 1080, cellularHeight = 480) == 1080)
        // Ethernet and an unknown network are not metered, so they use the Wi-Fi stop.
        assert(qualityFor(Transport.Ethernet, wifiHeight = 1080, cellularHeight = 480) == 1080)
        assert(qualityFor(Transport.Other, wifiHeight = 1080, cellularHeight = 480) == 1080)
    }

    @Test
    fun `each connection stores its stop under its own key`() {
        assert(qualityKey(Transport.Cellular) == "video_quality_cellular")
        assert(qualityKey(Transport.Wifi) == "video_quality")
        // A stored choice made before the split existed is the Wi-Fi one, so it is not lost.
        assert(qualityKey(Transport.Other) == "video_quality")
    }

    @Test
    fun `a session step-down outranks the stored choice`() {
        // The reader asked for Full HD; the connection could not carry it, so this session asks
        // for 480p. Their pick is still Full HD when the next video starts.
        assert(qualityInForce(storedHeight = 1080, sessionHeight = 480) == 480)
        assert(qualityInForce(storedHeight = 1080, sessionHeight = 0) == 1080)
        // Auto (0) stored, and nothing stepped down: Auto stays Auto.
        assert(qualityInForce(storedHeight = 0, sessionHeight = 0) == 0)
    }
}
