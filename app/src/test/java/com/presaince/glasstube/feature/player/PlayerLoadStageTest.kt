package com.presaince.glasstube.feature.player

import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerLoadStageTest {

    @Test
    fun stage_advances_in_order_video_chapters_comments_up_next() {
        assertEquals(PlayerLoadStage.Video, playerStage(false, false, false))
        assertEquals(PlayerLoadStage.Video, playerStage(false, true, true))
        assertEquals(PlayerLoadStage.Chapters, playerStage(true, false, false))
        assertEquals(PlayerLoadStage.Chapters, playerStage(true, false, true))
        assertEquals(PlayerLoadStage.Comments, playerStage(true, true, false))
        assertEquals(PlayerLoadStage.UpNext, playerStage(true, true, true))
    }
}
