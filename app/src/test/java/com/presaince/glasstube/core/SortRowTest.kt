/*
 * GlassTube — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.glasstube.core

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.presaince.glasstube.ui.theme.GlassTubeTheme
import com.presaince.glasstube.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The one Compose test in the suite: the sort row shows the options the lists offer and reports
 * the pick. It exists to prove the Robolectric + Compose path works and to pin the labels, which
 * the pure comparator test cannot see.
 */
@RunWith(RobolectricTestRunner::class)
class SortRowTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `tapping a chip selects it`() {
        val state = mutableStateOf(SortState(SortOption.DATE))
        compose.setContent {
            GlassTubeTheme(mode = ThemeMode.DARK) {
                SortRow(state = state.value, onState = { state.value = it })
            }
        }

        compose.onNodeWithText("Likes").performClick()

        assertEquals(SortOption.LIKES, state.value.option)
        compose.onNodeWithText("Likes").assertIsSelected()
    }

    @Test
    fun `tapping the selected chip flips the direction`() {
        val state = mutableStateOf(SortState(SortOption.DATE))
        compose.setContent {
            GlassTubeTheme(mode = ThemeMode.DARK) {
                SortRow(state = state.value, onState = { state.value = it })
            }
        }

        compose.onNodeWithText("Date").performClick()

        assertEquals(SortOption.DATE, state.value.option)
        assertEquals(true, state.value.ascending)
    }
}
