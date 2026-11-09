/*
 * Parallax — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.parallax.data.discover

import com.presaince.parallax.data.model.VideoSummary
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecipeTest {

    private val valid = """
        {
          "version": 1,
          "name": "Test",
          "from": { "kind": "related", "seed": "topChannels", "count": 3 },
          "filters": { "minDurationSec": 600 },
          "score": { "views": 1.0, "recency": 0.3 },
          "walk": { "hops": 3, "branch": 5, "wildcard": 0.1 }
        }
    """.trimIndent()

    private fun problems(json: String): List<String> =
        (RecipeParser.parse(json) as? RecipeResult.Err)?.problems ?: emptyList()

    @Test
    fun `parses a valid recipe`() {
        val result = RecipeParser.parse(valid)
        assertTrue(result is RecipeResult.Ok)
        val recipe = (result as RecipeResult.Ok).recipe
        assertEquals("Test", recipe.name)
        assertEquals(3, recipe.seed.count)
        assertEquals(600L, recipe.filters.minDurationSec)
        assertEquals(3, recipe.walk.hops)
    }

    @Test
    fun `a description is optional and capped`() {
        val withLine = valid.replace(
            "\"name\": \"Test\",",
            "\"name\": \"Test\", \"description\": \"Long talks for a slow evening.\",",
        )
        val recipe = (RecipeParser.parse(withLine) as RecipeResult.Ok).recipe
        assertEquals("Long talks for a slow evening.", recipe.description)

        val tooLong = withLine.replace("Long talks", "x".repeat(121))
        assertTrue(problems(tooLong).any { it.startsWith("description") })
    }

    @Test
    fun `describe speaks from the filters when a recipe is silent`() {
        val recipe = (RecipeParser.parse(valid) as RecipeResult.Ok).recipe

        assertTrue(describe(recipe).startsWith("Related to 3 of your top channels"))
        assertTrue(describe(recipe).contains("at least 10 min"))
        assertEquals("A short line.", describe(recipe.copy(description = "A short line.")))
    }

    @Test
    fun `rejects unknown keys at every level`() {
        assertTrue(problems(valid.replace("\"name\": \"Test\",", "\"name\": \"Test\", \"colour\": \"red\",")).any {
            it.contains("unknown key")
        })
        assertTrue(problems(valid.replace("\"count\": 3", "\"count\": 3, \"depth\": 2")).isNotEmpty())
        assertTrue(problems(valid.replace("\"views\": 1.0", "\"views\": 1.0, \"likes\": 2.0")).isNotEmpty())
        assertTrue(problems(valid.replace("\"hops\": 3", "\"hops\": 3, \"stop\": true")).isNotEmpty())
    }

    @Test
    fun `rejects out of range numbers`() {
        assertTrue(problems(valid.replace("\"hops\": 3", "\"hops\": 99")).isNotEmpty())
        assertTrue(problems(valid.replace("\"branch\": 5", "\"branch\": 0")).isNotEmpty())
        assertTrue(problems(valid.replace("\"wildcard\": 0.1", "\"wildcard\": 2.0")).isNotEmpty())
        assertTrue(problems(valid.replace("\"recency\": 0.3", "\"recency\": -1")).isNotEmpty())
        assertTrue(problems(valid.replace("\"count\": 3", "\"count\": 500")).isNotEmpty())
    }

    @Test
    fun `rejects unknown vocabulary`() {
        assertTrue(problems(valid.replace("\"topChannels\"", "\"trending\"")).isNotEmpty())
        assertTrue(problems(valid.replace("\"related\"", "\"videos\"")).isNotEmpty())
    }

    @Test
    fun `parses a search recipe without a walk`() {
        val json = """
            {
              "version": 1,
              "name": "Talks",
              "from": { "kind": "search", "query": "podcast", "count": 30 },
              "filters": { "minDurationSec": 600 },
              "score": { "views": 1.0, "recency": 0.3 }
            }
        """.trimIndent()

        val recipe = (RecipeParser.parse(json) as RecipeResult.Ok).recipe

        assertEquals("search", recipe.seed.kind)
        assertEquals("podcast", recipe.seed.query)
        assertEquals(30, recipe.seed.count)
        assertEquals("", recipe.seed.seed)
        assertEquals(0.0, recipe.walk.wildcard, 0.0001)
    }

    @Test
    fun `a search needs a query and no seed`() {
        val search = """
            { "version": 1, "name": "S",
              "from": { "kind": "search", "count": 10 },
              "filters": {}, "score": {} }
        """.trimIndent()
        assertTrue(problems(search).any { it.startsWith("from.query: required") })

        val withSeed = search.replace(
            "\"count\": 10",
            "\"count\": 10, \"query\": \"talks\", \"seed\": \"history\"",
        )
        assertTrue(problems(withSeed).any { it.startsWith("from.seed: only") })
    }

    @Test
    fun `hops and branch belong to a related walk only`() {
        val search = """
            { "version": 1, "name": "S",
              "from": { "kind": "search", "query": "talks", "count": 10 },
              "filters": {}, "score": {}, "walk": { "hops": 3, "branch": 4, "wildcard": 0.2 } }
        """.trimIndent()

        assertTrue(problems(search).any { it.startsWith("walk.hops") })
        assertTrue(problems(search).any { it.startsWith("walk.branch") })
    }

    @Test
    fun `parses an uploads recipe`() {
        val json = """
            { "version": 1, "name": "Fresh",
              "from": { "kind": "uploads", "count": 20 },
              "filters": { "maxAgeDays": 7 }, "score": {}, "walk": { "wildcard": 0.1 } }
        """.trimIndent()

        val recipe = (RecipeParser.parse(json) as RecipeResult.Ok).recipe

        assertEquals("uploads", recipe.seed.kind)
        assertEquals(20, recipe.seed.count)
        assertEquals("", recipe.seed.query)
        assertEquals("", recipe.seed.seed)
    }

    @Test
    fun `describe names the search and the upload page`() {
        val search = Recipe(
            name = "S",
            seed = Seed(kind = "search", count = 10, query = "podcast"),
            filters = Filters(minDurationSec = 600),
            score = Score(),
            walk = Walk(),
        )
        assertEquals("Search: \"podcast\" · at least 10 min", describe(search))

        val uploads = Recipe(
            name = "F",
            seed = Seed(kind = "uploads", count = 20),
            filters = Filters(maxAgeDays = 7),
            score = Score(),
            walk = Walk(),
        )
        assertEquals("New uploads from 20 channels you follow · newer than 7 days", describe(uploads))
    }

    @Test
    fun `rejects a contradictory duration window`() {
        val bad = valid.replace(
            "\"filters\": { \"minDurationSec\": 600 }",
            "\"filters\": { \"minDurationSec\": 900, \"maxDurationSec\": 60 }",
        )
        assertTrue(problems(bad).any { it.contains("greater than") })
    }

    @Test
    fun `rejects a wrong version and a blank name`() {
        assertTrue(problems(valid.replace("\"version\": 1", "\"version\": 2")).isNotEmpty())
        assertTrue(problems(valid.replace("\"name\": \"Test\"", "\"name\": \"  \"")).isNotEmpty())
        assertTrue(problems("{ not json").isNotEmpty())
    }

    @Test
    fun `every built-in recipe parses`() {
        val files = File("src/main/assets/recipes").listFiles { f -> f.extension == "json" }
            .orEmpty()
            .sortedBy { it.name }

        assertEquals("four built-ins ship with the app", 4, files.size)
        for (file in files) {
            val result = RecipeParser.parse(file.readText())
            assertTrue(
                "${file.name}: ${(result as? RecipeResult.Err)?.problems}",
                result is RecipeResult.Ok,
            )
        }
    }

    private fun video(
        id: String = "v",
        durationSec: Long = 1200,
        publishedAtMs: Long? = 1_800_000_000_000,
        views: Long? = 10_000,
        title: String = "a talk",
        channelUrl: String? = "https://youtube.com/c/x",
    ) = VideoSummary(
        id = id,
        url = "https://youtube.com/watch?v=$id",
        title = title,
        channelName = "channel",
        durationSec = durationSec,
        thumbnailUrl = null,
        channelUrl = channelUrl,
        publishedAtMs = publishedAtMs,
        viewCount = views,
    )

    @Test
    fun `filters accept and reject on each rule`() {
        val long = Filters(minDurationSec = 900)
        assertTrue(long.accepts(video(durationSec = 1200)))
        assertFalse(long.accepts(video(durationSec = 300)))
        // Unknown length is not evidence against a video.
        assertTrue(long.accepts(video(durationSec = 0)))

        val short = Filters(maxDurationSec = 600)
        assertFalse(short.accepts(video(durationSec = 1200)))
        assertTrue(short.accepts(video(durationSec = 300)))

        val fresh = Filters(maxAgeDays = 7)
        val now = System.currentTimeMillis()
        assertTrue(fresh.accepts(video(publishedAtMs = now - 2L * 86_400_000)))
        assertFalse(fresh.accepts(video(publishedAtMs = now - 30L * 86_400_000)))
        assertFalse(fresh.accepts(video(publishedAtMs = null)))

        assertFalse(Filters(excludeChannels = listOf("https://youtube.com/c/x")).accepts(video()))
        assertTrue(Filters(excludeChannels = listOf("https://youtube.com/c/y")).accepts(video()))
        assertFalse(Filters(excludeKeywords = listOf("#shorts")).accepts(video(title = "a #shorts clip")))
        assertFalse(Filters(minViewCount = 1_000_000).accepts(video(views = 5)))
    }

    @Test
    fun `scoring rewards views and freshness`() {
        val now = 1_800_000_000_000L
        val score = Score(views = 1.0, recency = 1.0)

        val quiet = video(id = "a", views = 100, publishedAtMs = now - 60L * 86_400_000)
        val popular = video(id = "b", views = 10_000_000, publishedAtMs = now - 60L * 86_400_000)
        assertTrue(scoreOf(popular, score, now) > scoreOf(quiet, score, now))

        val fresh = video(id = "c", views = 100, publishedAtMs = now - 86_400_000)
        assertTrue(scoreOf(fresh, score, now) > scoreOf(quiet, score, now))

        val unknown = video(id = "d", views = null, publishedAtMs = null)
        assertEquals(0.0, scoreOf(unknown, score, now), 0.0001)
    }
}
