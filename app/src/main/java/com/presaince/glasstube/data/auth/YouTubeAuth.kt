/*
 * GlassTube — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.glasstube.data.auth

import com.presaince.glasstube.core.AppResult
import com.presaince.glasstube.core.attempt
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

private const val AUTH_ENDPOINT = "https://accounts.google.com/o/oauth2/v2/auth"
private const val TOKEN_ENDPOINT = "https://oauth2.googleapis.com/token"
private const val SCOPE = "https://www.googleapis.com/auth/youtube.force-ssl"

/**
 * The authorisation-code flow for a "desktop app" OAuth client, with PKCE and a loopback redirect.
 * The Google Sign-In SDK is deliberately absent: it pulls Play Services into an app whose whole
 * selling point is that it has no account, and it initialises a client at startup, which M6's gate
 * forbids while posting is off.
 *
 * Nothing here runs until the user turns posting on and supplies a client id.
 */
class YouTubeAuth(private val http: OkHttpClient) {

    fun authorizeUrl(
        clientId: String,
        redirectUri: String,
        verifier: String,
        state: String,
    ): String {
        val params = linkedMapOf(
            "client_id" to clientId,
            "redirect_uri" to redirectUri,
            "response_type" to "code",
            "scope" to SCOPE,
            "code_challenge" to Pkce.challenge(verifier),
            "code_challenge_method" to "S256",
            "access_type" to "offline",
            "prompt" to "consent",
            "state" to state,
        )
        return AUTH_ENDPOINT + "?" + params.entries.joinToString("&") { (key, value) ->
            "$key=${encode(value)}"
        }
    }

    suspend fun exchangeCode(
        clientId: String,
        clientSecret: String,
        code: String,
        verifier: String,
        redirectUri: String,
    ): AppResult<Tokens> = withContext(Dispatchers.IO) {
        tokenCall(
            FormBody.Builder()
                .add("client_id", clientId)
                .add("client_secret", clientSecret)
                .add("code", code)
                .add("code_verifier", verifier)
                .add("grant_type", "authorization_code")
                .add("redirect_uri", redirectUri)
                .build(),
        )
    }

    suspend fun refresh(
        clientId: String,
        clientSecret: String,
        refreshToken: String,
    ): AppResult<Tokens> = withContext(Dispatchers.IO) {
        tokenCall(
            FormBody.Builder()
                .add("client_id", clientId)
                .add("client_secret", clientSecret)
                .add("refresh_token", refreshToken)
                .add("grant_type", "refresh_token")
                .build(),
        )
    }

    private fun tokenCall(body: FormBody): AppResult<Tokens> = runCatchingBlocking {
        val request = Request.Builder()
            .url(TOKEN_ENDPOINT)
            .post(body)
            .build()

        http.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IOException("token call failed: HTTP ${response.code} ${text.take(160)}")
            }
            val json = JSONObject(text)
            Tokens(
                accessToken = json.getString("access_token"),
                refreshToken = json.optString("refresh_token").takeIf { it.isNotBlank() },
                expiresInSec = json.optLong("expires_in", 3600L),
            )
        }
    }

    private fun <T> runCatchingBlocking(block: () -> T): AppResult<T> = try {
        AppResult.Ok(block())
    } catch (e: Exception) {
        AppResult.Err(e.message ?: e.javaClass.simpleName, e)
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")
}

data class Tokens(
    val accessToken: String,
    val refreshToken: String?,
    val expiresInSec: Long,
)

/**
 * A one-shot HTTP listener on 127.0.0.1. Google's desktop-client flow redirects the browser here;
 * the app reads the code off the request line and shuts the socket.
 */
class LoopbackServer {

    private val socket: ServerSocket =
        ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))

    val redirectUri: String get() = "http://127.0.0.1:${socket.localPort}/"

    suspend fun awaitCode(): AppResult<String> = withContext(Dispatchers.IO) {
        attempt {
            socket.soTimeout = TIMEOUT_MS
            socket.accept().use { client ->
                val reader = client.getInputStream().bufferedReader()
                val requestLine = reader.readLine().orEmpty()
                val body = "<html><body>GlassTube is signed in. You can close this tab.</body></html>"
                client.getOutputStream().bufferedWriter().use { writer ->
                    writer.write("HTTP/1.1 200 OK\r\nContent-Type: text/html\r\n")
                    writer.write("Content-Length: ${body.length}\r\nConnection: close\r\n\r\n")
                    writer.write(body)
                    writer.flush()
                }
                val query = requestLine.substringAfter('?', "").substringBefore(' ')
                val code = query.split('&')
                    .firstOrNull { it.startsWith("code=") }
                    ?.substringAfter('=')
                    ?.let { java.net.URLDecoder.decode(it, "UTF-8") }
                if (code.isNullOrBlank()) {
                    throw IOException("no authorisation code in the redirect")
                }
                code
            }
        }
    }

    fun close() {
        runCatching { socket.close() }
    }

    private companion object {
        const val TIMEOUT_MS = 180_000
    }
}
