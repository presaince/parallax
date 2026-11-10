/*
 * GlassTube — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.glasstube.data.importer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SubscriptionImporterTest {

    @Test
    fun `reads a newpipe export`() {
        val json = """
            {
              "app_version": "0.27.0",
              "app_version_int": 1006,
              "subscriptions": [
                {"service_id": 0, "url": "https://www.youtube.com/channel/UCabc", "name": "Alpha"},
                {"service_id": 0, "url": "https://www.youtube.com/channel/UCdef", "name": "Beta"}
              ]
            }
        """.trimIndent()

        val result = SubscriptionImporter.parse(json) as ImportResult.Ok
        assertEquals(2, result.subscriptions.size)
        assertEquals("Alpha", result.subscriptions.first().name)
    }

    @Test
    fun `reads a libretube export`() {
        val json = """
            {
              "subscriptions": [
                {"id": 1, "name": "Gamma", "url": "https://www.youtube.com/channel/UCghi"}
              ]
            }
        """.trimIndent()

        val result = SubscriptionImporter.parse(json) as ImportResult.Ok
        assertEquals(1, result.subscriptions.size)
        assertEquals("Gamma", result.subscriptions.first().name)
    }

    @Test
    fun `reads a takeout csv with quoted titles`() {
        val csv = """
            Channel Id,Channel Url,Channel Title
            UCabc,https://www.youtube.com/channel/UCabc,"Delta, the channel"
            UCdef,http://www.youtube.com/channel/UCdef,Epsilon
        """.trimIndent()

        val result = SubscriptionImporter.parse(csv) as ImportResult.Ok
        assertEquals(2, result.subscriptions.size)
        assertEquals("Delta, the channel", result.subscriptions.first().name)
    }

    @Test
    fun `deduplicates by url`() {
        val json = """
            {"subscriptions": [
              {"url": "https://www.youtube.com/channel/UCabc", "name": "Alpha"},
              {"url": "https://www.youtube.com/channel/UCabc", "name": "Alpha again"}
            ]}
        """.trimIndent()

        val result = SubscriptionImporter.parse(json) as ImportResult.Ok
        assertEquals(1, result.subscriptions.size)
    }

    @Test
    fun `rejects junk`() {
        val result = SubscriptionImporter.parse("hello there")
        assertTrue(result is ImportResult.Err)
    }

    @Test
    fun `rejects json without subscriptions`() {
        val result = SubscriptionImporter.parse("""{"something": []}""")
        assertTrue(result is ImportResult.Err)
    }

    @Test
    fun `entries without a url are counted as skipped`() {
        val json = """
            {"subscriptions": [
              {"url": "https://www.youtube.com/channel/UCabc", "name": "Alpha"},
              {"name": "No url"},
              {"url": "", "name": "Blank url"}
            ]}
        """.trimIndent()

        val result = SubscriptionImporter.parse(json) as ImportResult.Ok
        org.junit.Assert.assertEquals(1, result.subscriptions.size)
        org.junit.Assert.assertEquals(2, result.skipped)
    }

    @Test
    fun `takeout rows without an http url are counted as skipped`() {
        val csv = """
            Channel Id,Channel Url,Channel Title
            UCabc,https://www.youtube.com/channel/UCabc,Alpha
            UCnope,,No url
        """.trimIndent()

        val result = SubscriptionImporter.parse(csv) as ImportResult.Ok
        org.junit.Assert.assertEquals(1, result.subscriptions.size)
        org.junit.Assert.assertEquals(1, result.skipped)
    }
}
