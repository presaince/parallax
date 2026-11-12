/*
 * GlassTube — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.glasstube.architecture

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The layering, checked by the test suite rather than trusted to a comment.
 *
 * `AGENTS.md` says the UI never touches the extractor, and until now nothing failed when it did.
 * These rules are the ones that hold today; each later step adds a rule only once the code obeys
 * it, so the list never describes a wish.
 */
class LayerBoundaryTest {

    private val root = File("src/main/java/com/presaince/glasstube")

    private fun sources(): List<File> =
        root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

    private fun offenders(needle: String, allowedPrefix: String): List<String> = sources()
        .filter { file ->
            val relative = file.relativeTo(root).invariantSeparatorsPath
            !relative.startsWith(allowedPrefix) &&
                file.readLines().any { it.trim().startsWith("import") && it.contains(needle) }
        }
        .map { it.relativeTo(root).invariantSeparatorsPath }
        .sorted()

    @Test
    fun `only the source adapter imports the extractor`() {
        assertTrue(
            "NewPipeExtractor reached outside data/source: ${offenders("org.schabi.newpipe", "data/source/")}",
            offenders("org.schabi.newpipe", "data/source/").isEmpty(),
        )
    }

    @Test
    fun `nothing outside data sees the videos table row`() {
        // The model pass replaced every UI use of VideoEntity with Video. If this fails, a screen
        // has started reading the schema again and the split is leaking.
        val found = offenders("com.presaince.glasstube.data.db.VideoEntity", "data/")
        assertTrue("VideoEntity reached outside data/: $found", found.isEmpty())
    }
}
