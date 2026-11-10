/*
 * GlassTube — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.glasstube.data.source

import com.presaince.glasstube.core.AppResult
import com.presaince.glasstube.core.SourceError
import com.presaince.glasstube.data.model.StreamSet
import com.presaince.glasstube.data.model.VideoDetail
import com.presaince.glasstube.data.model.VideoSummary
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.schabi.newpipe.extractor.exceptions.ParsingException

/**
 * The repair loop is boring on purpose: one retry after a reset, one validator that rejects a
 * bad model reply, and a canary list whose shape checks are structural rather than counts.
 */
class SelfRepairTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `known videos have two structural ids`() {
        assertEquals(2, KnownVideos.ids.size)
        assertEquals(2, KnownVideos.ids.distinct().size)
    }

    @Test
    fun `a thin video is flagged`() {
        val detail = VideoDetail(
            id = KnownVideos.ids.first(),
            url = "https://www.youtube.com/watch?v=${KnownVideos.ids.first()}",
            title = "",
            channelName = "c",
            durationSec = 0,
            description = "",
        )
        val problems = KnownVideos.checkVideo(detail)
        assertTrue(problems.contains("blank title"))
        assertTrue(problems.contains("duration 0"))
    }

    @Test
    fun `a stream set with only blanks is flagged`() {
        val problems = KnownVideos.checkStreams(StreamSet(muxedUrl = ""))
        assertTrue(problems.contains("no playable stream"))
    }

    @Test
    fun `a proposal that echoes the query is dropped`() {
        val proposal = RepairProposalParser.parse(
            """{"queryAlternative":"esp32","regionCode":"GB"}""",
            "esp32",
        )
        assertEquals("GB", proposal?.regionCode)
        assertEquals(null, proposal?.queryAlternative)
    }

    @Test
    fun `a proposal with an unknown region is dropped`() {
        val proposal = RepairProposalParser.parse(
            """{"queryAlternative":"esp32 s3","regionCode":"XX"}""",
            "esp32",
        )
        assertEquals("esp32 s3", proposal?.queryAlternative)
        assertEquals(null, proposal?.regionCode)
    }

    @Test
    fun `an unparseable reply proposes nothing`() {
        assertEquals(null, RepairProposalParser.parse("not json", "esp32"))
    }

    @Test
    fun `a successful call stays silent and never resets`() = runTest {
        val log = com.presaince.glasstube.core.AppLog(tmp.newFolder())
        var resets = 0
        val result = withSourceRepair(log, "video", { resets++ }) { 42 }
        assertTrue(result is AppResult.Ok)
        assertEquals(42, (result as AppResult.Ok).value)
        assertEquals(0, resets)
        assertTrue(log.recent().isEmpty())
    }

    @Test
    fun `the wrapper recovers after a reset`() = runTest {
        val log = com.presaince.glasstube.core.AppLog(tmp.newFolder())
        var calls = 0
        val result = withSourceRepair(log, "video", { log.info(com.presaince.glasstube.data.source.REPAIR_TAG, "reset") }) {
            calls++
            if (calls == 1) throw ParsingException("no lockup")
            42
        }
        assertTrue(result is AppResult.Ok)
        assertEquals(42, (result as AppResult.Ok).value)
        val lines = log.recent().joinToString("\n") { it.message }
        assertTrue(lines.contains("video failed"))
        assertTrue(lines.contains("video recovered"))
        assertTrue(!lines.contains("video start"))
    }

    @Test
    fun `a non-extractor failure skips the reset`() = runTest {
        val log = com.presaince.glasstube.core.AppLog(tmp.newFolder())
        var resets = 0
        val result = withSourceRepair(log, "video", { resets++ }) {
            throw IllegalStateException("no readable captions")
        }
        assertTrue(result is AppResult.Err)
        assertEquals(0, resets)
    }
}
