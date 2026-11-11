/*
 * GlassTube — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.glasstube.feature.notes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Report `220547`: "recording the speaker for 1min and at the end it lost the first 95% and then
 * exited". The recogniser restates its current utterance on every partial and starts a new one
 * without the old, so replacing the note on each callback kept only the last second.
 */
class TranscriptMergeTest {

    @Test
    fun `a partial that repeats what is already there extends it`() {
        assertEquals("the middle part", mergeTranscript("the middle", "the middle part"))
    }

    @Test
    fun `a shorter partial never deletes what was said`() {
        // The recogniser sometimes revises downwards mid-sentence; the words already heard stand.
        assertEquals("the middle part", mergeTranscript("the middle part", "the middle"))
        assertEquals("the middle part", mergeTranscript("the middle part", ""))
    }

    @Test
    fun `the next utterance is added after the last one`() {
        assertEquals("one two", mergeTranscript("one", "two"))
    }

    @Test
    fun `a minute of speech keeps its first sentence, in every state the screen shows`() {
        var committed = ""
        val shown = mutableListOf<String>()
        // Three utterances, each arriving as a short partial and then its full text, the way the
        // recogniser really behaves. The screen shows committed plus the live partial. The
        // guarantee starts once the first utterance is finished: before that there is nothing
        // older to lose.
        for ((index, utterance) in listOf("first sentence", "second sentence", "third sentence")
            .withIndex()) {
            shown += mergeTranscript(committed, utterance.take(6))
            shown += mergeTranscript(committed, utterance)
            committed = mergeTranscript(committed, utterance)
            if (index == 0) shown.clear()
        }
        assertEquals("first sentence second sentence third sentence", committed)
        assertTrue(
            "the first sentence survived every state after it was said: $shown",
            shown.isNotEmpty() && shown.all { it.startsWith("first sentence") },
        )
    }

    @Test
    fun `nothing said yet is nothing written`() {
        assertEquals("", mergeTranscript("", ""))
        assertEquals("hello", mergeTranscript("", "hello"))
    }
}
