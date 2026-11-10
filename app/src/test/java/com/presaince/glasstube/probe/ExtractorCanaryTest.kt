/*
 * GlassTube — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.glasstube.probe

import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.search.SearchInfo
import com.presaince.glasstube.data.source.KnownVideos
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/**
 * Extractor canary. Skipped unless `-Dglasstube.canary=true`, because it needs the network and a
 * YouTube that still parses. This is the test that fails first when YouTube changes something,
 * and the one M7's nightly job runs.
 */
class ExtractorCanaryTest {

    @Test
    fun `search still returns videos`() {
        assumeTrue(System.getProperty("glasstube.canary") == "true")

        val downloader = object : Downloader() {
            override fun execute(request: Request): Response {
                val connection = URL(request.url()).openConnection() as HttpURLConnection
                connection.requestMethod = request.httpMethod()
                connection.connectTimeout = 15000
                connection.readTimeout = 20000
                request.headers().forEach { (name, values) ->
                    connection.setRequestProperty(name, values.firstOrNull() ?: "")
                }
                val payload = request.dataToSend()
                if (payload != null) {
                    connection.doOutput = true
                    connection.outputStream.use { it.write(payload) }
                }
                val code = connection.responseCode
                val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                val headers = connection.headerFields
                    .filterKeys { it != null }
                    .mapValues { it.value ?: emptyList() }
                return Response(code, connection.responseMessage.orEmpty(), headers, body, request.url())
            }
        }

        NewPipe.init(downloader, Localization.fromLocale(Locale.US))

        val extractor = NewPipe.getService(0).getSearchExtractor("esp32")
        extractor.fetchPage()
        val info = SearchInfo.getInfo(extractor)

        println("canary: ${info.relatedItems.size} items")
        assertTrue("search returned nothing", info.relatedItems.isNotEmpty())
    }

    @Test
    fun `known videos still resolve`() {
        assumeTrue(System.getProperty("glasstube.canary") == "true")

        NewPipe.init(
            object : Downloader() {
                override fun execute(request: Request): Response {
                    val connection = URL(request.url()).openConnection() as HttpURLConnection
                    connection.requestMethod = request.httpMethod()
                    connection.connectTimeout = 15000
                    connection.readTimeout = 20000
                    request.headers().forEach { (name, values) ->
                        connection.setRequestProperty(name, values.firstOrNull() ?: "")
                    }
                    val payload = request.dataToSend()
                    if (payload != null) {
                        connection.doOutput = true
                        connection.outputStream.use { it.write(payload) }
                    }
                    val code = connection.responseCode
                    val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                    val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                    val headers = connection.headerFields
                        .filterKeys { it != null }
                        .mapValues { it.value ?: emptyList() }
                    return Response(code, connection.responseMessage.orEmpty(), headers, body, request.url())
                }
            },
            Localization.fromLocale(Locale.US),
        )

        val service = NewPipe.getService(0)
        val passedIds = mutableListOf<String>()
        val failed = mutableListOf<String>()
        for (id in KnownVideos.ids) {
            val problems = runCatching {
                val video = org.schabi.newpipe.extractor.stream.StreamInfo.getInfo(service, "https://www.youtube.com/watch?v=$id")
                val detail = com.presaince.glasstube.data.model.VideoDetail(
                    id = id,
                    url = "https://www.youtube.com/watch?v=$id",
                    title = video.name.orEmpty(),
                    channelName = video.uploaderName.orEmpty(),
                    durationSec = video.duration,
                    description = "",
                )
                KnownVideos.checkVideo(detail) + KnownVideos.checkStreams(
                    com.presaince.glasstube.data.model.StreamSet(muxedUrl = video.videoStreams.firstOrNull()?.content ?: video.videoOnlyStreams.firstOrNull()?.content ?: ""),
                )
            }.fold(onSuccess = { it }, onFailure = { listOf("error ${it.javaClass.simpleName}") })
            if (problems.isEmpty()) {
                passedIds += id
                println("canary known: $id ok")
            } else {
                failed += "$id ${problems.joinToString(",")}"
                println("canary known: $id failed $problems")
            }
        }
        // The two structural ids have to both resolve on a night YouTube still parses.
        assertTrue("only ${passedIds.size} of ${KnownVideos.ids.size} resolved: $failed", passedIds.size == KnownVideos.ids.size)
    }

    @Test
    fun `comments still carry a date and an author url`() {
        assumeTrue(System.getProperty("glasstube.canary") == "true")

        NewPipe.init(
            object : Downloader() {
                override fun execute(request: Request): Response {
                    val connection = URL(request.url()).openConnection() as HttpURLConnection
                    connection.requestMethod = request.httpMethod()
                    connection.connectTimeout = 15000
                    connection.readTimeout = 20000
                    request.headers().forEach { (name, values) ->
                        connection.setRequestProperty(name, values.firstOrNull() ?: "")
                    }
                    val payload = request.dataToSend()
                    if (payload != null) {
                        connection.doOutput = true
                        connection.outputStream.use { it.write(payload) }
                    }
                    val code = connection.responseCode
                    val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                    val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                    val headers = connection.headerFields
                        .filterKeys { it != null }
                        .mapValues { it.value ?: emptyList() }
                    return Response(code, connection.responseMessage.orEmpty(), headers, body, request.url())
                }
            },
            Localization.fromLocale(Locale.US),
        )

        // The comments sort by date, so a night when YouTube stops sending the date is a night the
        // sort quietly does nothing. Print the samples, then require at least one real date.
        val service = NewPipe.getService(0)
        val info = org.schabi.newpipe.extractor.comments.CommentsInfo.getInfo(
            service,
            "https://www.youtube.com/watch?v=${KnownVideos.ids.first()}",
        )
        val items = info.relatedItems.take(10)
        var dated = 0
        for (item in items) {
            val instant = item.uploadDate?.instant
            if (instant != null) dated++
            println(
                "canary comment: date=$instant approx=${item.uploadDate?.isApproximation} " +
                    "text='${item.textualUploadDate}' author=${item.uploaderUrl}",
            )
        }
        assertTrue("no comment carried a date: ${items.size} sampled", dated > 0)
    }
}
