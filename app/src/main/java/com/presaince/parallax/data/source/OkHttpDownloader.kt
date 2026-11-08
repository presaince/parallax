/*
 * Parallax — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.parallax.data.source

import com.presaince.parallax.core.AppLog
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response

/**
 * The extractor never makes its own requests; it calls this. One OkHttp client is shared with the
 * rest of the app so the connection pool and timeouts are the same everywhere.
 */
class OkHttpDownloader(
    private val client: OkHttpClient,
    private val log: AppLog? = null,
) : Downloader() {

    override fun execute(request: Request): Response {
        val contentType = request.headers()
            .entries
            .firstOrNull { it.key.equals("Content-Type", ignoreCase = true) }
            ?.value
            ?.firstOrNull()
            ?.toMediaTypeOrNull()

        val body = request.dataToSend()?.toRequestBody(contentType)

        val builder = okhttp3.Request.Builder()
            .url(request.url())
            .method(request.httpMethod().uppercase(), body)

        request.headers().forEach { (name, values) ->
            values.forEach { builder.addHeader(name, it) }
        }
        if (request.headers().keys.none { it.equals("Accept-Language", ignoreCase = true) }) {
            builder.header("Accept-Language", request.localization()?.languageCode ?: "en")
        }

        val method = request.httpMethod().uppercase()
        if (log != null) {
            log.info("http", "$method ${hostPath(request.url())} headers=${request.headers().size}")
        }
        client.newCall(builder.build()).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            log?.info("http", "$method ${hostPath(request.url())} -> ${response.code} ${raw.length}B")
            return Response(
                response.code,
                response.message,
                response.headers.toMultimap(),
                raw,
                request.url(),
            )
        }
    }

    /**
     * Host plus path only. The query carries signed stream parameters that expire within hours and
     * must not reach a persisted log.
     */
    private fun hostPath(raw: String): String = runCatching {
        val uri = java.net.URI(raw)
        (uri.host ?: "unknown") + (uri.path ?: "")
    }.getOrDefault("unknown")
}
