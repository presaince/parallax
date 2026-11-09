/*
 * Parallax — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.parallax.data.source

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChannelFeedTest {

    private val xml = """
        <?xml version="1.0" encoding="UTF-8"?>
        <feed xmlns:yt="http://www.youtube.com/xml/schemas/2015"
              xmlns:media="http://search.yahoo.com/mrss/">
          <title>A channel</title>
          <entry>
            <id>yt:video:aaaaaaaaaaa</id>
            <yt:videoId>aaaaaaaaaaa</yt:videoId>
            <yt:channelId>UC1234567890123456789012</yt:channelId>
            <title>Maps &amp; borders &#8212; a talk</title>
            <link rel="alternate" href="https://www.youtube.com/watch?v=aaaaaaaaaaa"/>
            <author><name>The Channel</name><uri>https://www.youtube.com/channel/UC1234567890123456789012</uri></author>
            <published>2026-11-08T12:00:00+00:00</published>
            <media:group>
              <media:title>Maps &amp; borders</media:title>
              <media:thumbnail url="https://i1.ytimg.com/vi/aaaaaaaaaaa/hqdefault.jpg" width="480" height="360"/>
              <media:community>
                <media:statistics views="1042"/>
              </media:community>
            </media:group>
          </entry>
          <entry>
            <id>yt:video:bbbbbbbbbbb</id>
            <yt:videoId>bbbbbbbbbbb</yt:videoId>
            <title>Second</title>
            <author><name>The Channel</name></author>
            <published>2026-11-07T12:00:00+00:00</published>
          </entry>
          <entry>
            <title>No id, no row</title>
          </entry>
        </feed>
    """.trimIndent()

    @Test
    fun `parses the fields the cards need`() {
        val videos = parseChannelFeed(xml)

        assertEquals(2, videos.size)
        val first = videos.first()
        assertEquals("aaaaaaaaaaa", first.id)
        assertEquals("Maps & borders — a talk", first.title)
        assertEquals("The Channel", first.channelName)
        assertEquals("https://www.youtube.com/watch?v=aaaaaaaaaaa", first.url)
        assertEquals("https://i1.ytimg.com/vi/aaaaaaaaaaa/hqdefault.jpg", first.thumbnailUrl)
        assertEquals("https://www.youtube.com/channel/UC1234567890123456789012", first.channelUrl)
        assertEquals(1042L, first.viewCount)
        assertEquals(1_794_139_200_000L, first.publishedAtMs)
        // The feed carries no duration; zero is the code's "unknown".
        assertEquals(0L, first.durationSec)
    }

    @Test
    fun `an entry with no video id is skipped`() {
        assertEquals(listOf("aaaaaaaaaaa", "bbbbbbbbbbb"), parseChannelFeed(xml).map { it.id })
    }

    @Test
    fun `the limit keeps the newest entries`() {
        assertEquals(listOf("aaaaaaaaaaa"), parseChannelFeed(xml, limit = 1).map { it.id })
    }

    @Test
    fun `finds the channel id in an imported url`() {
        assertEquals(
            "UC1234567890123456789012",
            channelIdFromUrl("https://www.youtube.com/channel/UC1234567890123456789012"),
        )
        assertNull(channelIdFromUrl("https://www.youtube.com/@someone"))
        assertNull(channelIdFromUrl("https://www.youtube.com/c/oldname"))
    }

    @Test
    fun `decodes named and numeric entities`() {
        assertEquals("a & b < c", unescape("a &amp; b &lt; c"))
        assertEquals("it's", unescape("it&apos;s"))
        assertEquals("—", unescape("&#8212;"))
        assertEquals("&#unknown;", unescape("&#unknown;"))
    }

    @Test
    fun `a blank feed is an empty list, not a crash`() {
        assertTrue(parseChannelFeed("").isEmpty())
        assertTrue(parseChannelFeed("<feed></feed>").isEmpty())
    }
}
