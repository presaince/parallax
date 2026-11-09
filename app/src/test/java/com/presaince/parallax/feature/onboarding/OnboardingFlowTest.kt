/*
 * Parallax — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.parallax.feature.onboarding

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rules that decide which page is which, who sees the wizard and what the two answers on the
 * ads page mean. The screen is not tested here; the decisions are.
 */
class OnboardingFlowTest {

    @Test
    fun `the pages run in one order and stop at the ends`() {
        assertEquals(OnboardingPage.ADS, OnboardingPage.LOOK.next())
        assertEquals(OnboardingPage.START, OnboardingPage.START.next())
        assertEquals(OnboardingPage.LOOK, OnboardingPage.LOOK.previous())
        assertEquals(OnboardingPage.DEMO, OnboardingPage.START.previous())
        assertTrue(OnboardingPage.LOOK.isFirst)
        assertTrue(OnboardingPage.START.isLast)
        assertEquals(4, OnboardingPage.COUNT)
    }

    @Test
    fun `a fresh install gets the wizard`() {
        assertTrue(shouldShowOnboarding(done = false, firstInstallTime = 100L, lastUpdateTime = 100L))
    }

    @Test
    fun `an update does not get the wizard`() {
        assertFalse(
            shouldShowOnboarding(done = false, firstInstallTime = 100L, lastUpdateTime = 200L),
        )
    }

    @Test
    fun `a finished or skipped setup never runs again by itself`() {
        assertFalse(
            shouldShowOnboarding(done = true, firstInstallTime = 100L, lastUpdateTime = 100L),
        )
    }

    @Test
    fun `wanting ads turns SponsorBlock off`() {
        assertFalse(sponsorBlockAfterAdsAnswer(wantsAds = true))
        assertTrue(sponsorBlockAfterAdsAnswer(wantsAds = false))
    }
}
