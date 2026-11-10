/*
 * GlassTube — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.glasstube.data.auth

import java.net.URI
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PkceTest {

    @Test
    fun `challenge is sha256 base64url without padding`() {
        // Independent check: SHA-256 of this verifier, base64url, computed outside the JVM.
        val verifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gXk5h2Vrq4"
        assertEquals("EPuk1Exa1au8AjFTIc2g8z6VjKmA7WBr_S5lw7qcfuE", Pkce.challenge(verifier))
        assertEquals(43, Pkce.challenge(verifier).length)
    }

    @Test
    fun `verifiers are url safe and long enough`() {
        val verifier = Pkce.verifier()
        assertTrue(verifier.length >= 43)
        assertTrue(verifier.all { it.isLetterOrDigit() || it == '-' || it == '_' })
        assertNotEquals(verifier, Pkce.verifier())
    }
}

class AuthorizeUrlTest {

    private val auth = YouTubeAuth(OkHttpClient())

    private fun params(url: String): Map<String, String> =
        URI(url).rawQuery.split('&').associate {
            val key = it.substringBefore('=')
            val value = it.substringAfter('=')
            key to java.net.URLDecoder.decode(value, "UTF-8")
        }

    @Test
    fun `consent url carries the pkce challenge and offline access`() {
        val verifier = "verifier-value"
        val url = auth.authorizeUrl(
            clientId = "client.apps.googleusercontent.com",
            redirectUri = "http://127.0.0.1:1234/",
            verifier = verifier,
            state = "state-1",
        )

        val query = params(url)
        assertTrue(url.startsWith("https://accounts.google.com/o/oauth2/v2/auth?"))
        assertEquals("code", query["response_type"])
        assertEquals("S256", query["code_challenge_method"])
        assertEquals(Pkce.challenge(verifier), query["code_challenge"])
        assertEquals("offline", query["access_type"])
        assertEquals("state-1", query["state"])
        assertEquals("http://127.0.0.1:1234/", query["redirect_uri"])
        assertEquals("https://www.googleapis.com/auth/youtube.force-ssl", query["scope"])
    }
}
