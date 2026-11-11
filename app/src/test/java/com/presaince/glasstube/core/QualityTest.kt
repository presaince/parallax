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
}
