/*
 * GlassTube — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.glasstube.feature.player

import android.app.Activity
import android.view.WindowManager
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

/**
 * The brightness swipe writes the window, and the window outlives the player. Left set, the whole
 * app stayed at the swiped level and the shade's slider read as locked by the top app.
 */
@RunWith(RobolectricTestRunner::class)
class BrightnessHandbackTest {

    @Test
    fun `leaving the player gives brightness back to the system`() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        activity.window.attributes = activity.window.attributes.apply { screenBrightness = 0.2f }

        restoreSystemBrightness(activity.window)

        assertEquals(
            WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE,
            activity.window.attributes.screenBrightness,
        )
    }

    @Test
    fun `the rest of the window attributes survive the handback`() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        activity.window.attributes = activity.window.attributes.apply {
            screenBrightness = 0.2f
            dimAmount = 0.4f
        }

        restoreSystemBrightness(activity.window)

        assertEquals(0.4f, activity.window.attributes.dimAmount)
    }
}
