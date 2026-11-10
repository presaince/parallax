/*
 * GlassTube — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.glasstube.feature.onboarding

import android.content.SharedPreferences

/**
 * The wizard's rules, with no Compose in them.
 *
 * They used to be an `Int` step passed around, which is why inserting a page could silently move
 * the "last page" button to the wrong screen. Named pages and functions that take their inputs
 * make that impossible to do quietly, and [OnboardingFlowTest] fails when a rule lands on the
 * wrong page.
 */
enum class OnboardingPage(val label: String) {
    LOOK("Look"),
    ADS("Ads"),
    DEMO("Player"),
    START("Start");

    fun next(): OnboardingPage = entries[(ordinal + 1).coerceAtMost(entries.lastIndex)]
    fun previous(): OnboardingPage = entries[(ordinal - 1).coerceAtLeast(0)]
    val isFirst: Boolean get() = ordinal == 0
    val isLast: Boolean get() = ordinal == entries.lastIndex

    companion object {
        val COUNT: Int = entries.size
    }
}

/** The one preference that says the wizard has been answered, skipped or dismissed. */
const val KEY_ONBOARDING_DONE = "onboarding_done"

fun isOnboardingDone(prefs: SharedPreferences): Boolean =
    prefs.getBoolean(KEY_ONBOARDING_DONE, false)

/**
 * Skipping is an answer. A wizard that comes back on the next launch is a nag, and the way back
 * is Settings → About → Run setup again.
 */
fun markOnboardingDone(prefs: SharedPreferences) {
    prefs.edit().putBoolean(KEY_ONBOARDING_DONE, true).apply()
}

/**
 * Fresh installs only. An update must not drop an existing reader into a first-run flow that
 * rewrites their theme and region; `firstInstallTime == lastUpdateTime` is the only signal that
 * is true for a fresh install and false after an update, including `adb install -r`.
 */
fun shouldShowOnboarding(
    done: Boolean,
    firstInstallTime: Long,
    lastUpdateTime: Long,
): Boolean = !done && firstInstallTime == lastUpdateTime

/**
 * The ads page in one line: wanting no ads is what turns SponsorBlock on. Saying yes turns it
 * off, but it cannot add YouTube ads to a stream that has none — the page says so.
 */
fun sponsorBlockAfterAdsAnswer(wantsAds: Boolean): Boolean = !wantsAds
