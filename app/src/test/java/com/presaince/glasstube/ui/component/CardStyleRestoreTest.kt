/*
 * GlassTube — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.glasstube.ui.component

import android.content.Context
import com.presaince.glasstube.data.backup.SettingsImport
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * A restore writes preferences underneath every card already composed. The revision is the only
 * reason those cards read again, so it is pinned here: without it the restored layout is in the
 * prefs and not on the screen until the app restarts.
 */
@RunWith(RobolectricTestRunner::class)
class CardStyleRestoreTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("glasstube", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun `a restore is not on screen until the card style is told to reload`() {
        CardStyle.write(context, CardStyle(layout = CardLayout.MINIMAL))
        val before = CardStyle.revision

        SettingsImport.apply(
            context,
            JSONObject().put("cards", JSONObject().put("layout", "CLASSIC")),
            apiKeyPresent = false,
        )

        assertEquals(CardLayout.CLASSIC, CardStyle.read(context).layout)
        assertEquals("nothing has repainted yet", before, CardStyle.revision)

        CardStyle.reload()

        assertEquals(before + 1, CardStyle.revision)
        assertEquals(CardLayout.CLASSIC, CardStyle.read(context).layout)
    }
}
