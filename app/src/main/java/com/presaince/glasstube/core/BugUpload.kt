/*
 * GlassTube — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.glasstube.core

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * What happened to one report. Sealed, so "is it working" is never a pile of booleans and the
 * screen cannot show two contradictory states at once.
 */
sealed interface BugUploadState {
    data object Idle : BugUploadState
    data object Building : BugUploadState
    data class Sending(val bytes: Long) : BugUploadState
    data class Done(val name: String) : BugUploadState

    /** [reason] is a diagnostic code, not a sentence — the screen owns the wording. [retryAtMillis]
     *  is set when the server said when to come back, so the button can stay shut until then. */
    data class Failed(val reason: String, val retryAtMillis: Long? = null) : BugUploadState
}

/**
 * The line Diagnostics keeps for a report that landed. The bundle has left the device, so this is
 * the only place the person can see what they filed — the generated file name alone says nothing
 * about it (`112509`). Flattened and capped so one entry stays one readable line.
 */
internal fun filedLine(name: String, note: String, limit: Int = 120): String {
    val flat = note.replace(Regex("\\s+"), " ").trim()
    if (flat.isEmpty()) return "filed $name"
    val kept = if (flat.length > limit) flat.take(limit - 1).trimEnd() + "…" else flat
    return "filed $name: $kept"
}

/**
 * Ships a bug report to GlassTube's own drop box, the same path Oko uses: one JSON body, one POST,
 * a filename in a header that the server sanitises rather than trusts.
 *
 * No shared secret on purpose. A token in an APK is extractable by anyone who unpacks it, so it
 * would guard nothing; the endpoint is rate-limited per IP and refuses anything that is not a
 * report bundle. See `server/upload.php`.
 */
object BugUpload {

    private const val ENDPOINT = "https://odesaplay.com.ua/other_apps/glasstube/upload.php"

    private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow<BugUploadState>(BugUploadState.Idle)
    val state: StateFlow<BugUploadState> = _state.asStateFlow()

    /** The HTTP contract as a pure function, so the 429 and bad-reply rules are testable without a socket. */
    internal fun stateFor(
        code: Int,
        retryAfter: String?,
        body: String,
        contentType: String?,
        nowMs: Long = System.currentTimeMillis(),
    ): BugUploadState {
        if (code !in 200..299) {
            if (code == 429) {
                val wait = retryAfter?.toIntOrNull()?.coerceIn(5, 300) ?: 30
                return BugUploadState.Failed("HTTP 429", retryAtMillis = nowMs + wait * 1_000L)
            }
            return BugUploadState.Failed("HTTP $code")
        }
        // A 2xx that is not our own contract means something answered instead of upload.php — an
        // error page, a captive portal, a host that returns 200 for everything. Reporting success
        // there would be a lie.
        val replied = runCatching { JSONObject(body) }.getOrNull()
            ?.takeIf { it.optBoolean("ok") }
            ?.optString("name")
            ?.takeIf { it.isNotBlank() }
        if (replied == null) {
            return BugUploadState.Failed(
                "unexpected reply (HTTP $code ${contentType ?: "?"})",
            )
        }
        return BugUploadState.Done(replied)
    }

    /** No-op while a bundle is in flight, so a double tap cannot produce two reports. */
    fun send(
        context: Context,
        log: AppLog,
        note: String,
        includeShot: Boolean,
        includeLogs: Boolean = true,
    ) {
        val current = _state.value
        if (current is BugUploadState.Building || current is BugUploadState.Sending) return
        val app = context.applicationContext
        scope.launch {
            try {
                _state.value = BugUploadState.Building
                val body = BugReport.bundle(app, log, note, includeShot, includeLogs)
                val name = BugReport.uploadName()
                _state.value = BugUploadState.Sending(body.toByteArray(Charsets.UTF_8).size.toLong())
                val settled = post(body, name)
                _state.value = settled
                // The report leaves the device, so without this line the person's own history of
                // what they filed disappears with it. Diagnostics is where they look for it, and
                // the note is what they typed — a file name says nothing (`112509`).
                if (settled is BugUploadState.Done) log.info("bug", filedLine(settled.name, note))
            } catch (error: Exception) {
                _state.value = BugUploadState.Failed(error.message ?: error.javaClass.simpleName)
            }
        }
    }

    private fun post(body: String, name: String): BugUploadState {
        return try {
            val request = Request.Builder()
                .url(ENDPOINT)
                .header("X-Log-Name", name)
                .post(body.toRequestBody(JSON_MEDIA))
                .build()
            client.newCall(request).execute().use { response ->
                stateFor(
                    code = response.code,
                    retryAfter = response.header("Retry-After"),
                    body = response.body?.string().orEmpty(),
                    contentType = response.header("Content-Type"),
                )
            }
        } catch (error: Exception) {
            BugUploadState.Failed(error.message ?: error.javaClass.simpleName)
        }
    }

    fun dismiss() {
        _state.value = BugUploadState.Idle
    }
}
